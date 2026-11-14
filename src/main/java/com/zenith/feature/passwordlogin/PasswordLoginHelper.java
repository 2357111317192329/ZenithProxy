package com.zenith.feature.passwordlogin;

import com.viaversion.nbt.io.MNBTIO;
import com.viaversion.nbt.mini.MNBT;
import com.viaversion.nbt.stringified.SNBT;
import com.viaversion.nbt.tag.CompoundTag;
import com.viaversion.nbt.tag.StringTag;
import com.viaversion.nbt.tag.Tag;
import com.viaversion.viaversion.api.protocol.version.ProtocolVersion;
import com.zenith.Proxy;
import com.zenith.mc.block.BlockRegistry;
import com.zenith.network.server.ServerSession;
import com.zenith.util.config.Config;
import net.kyori.adventure.key.Key;
import org.geysermc.mcprotocollib.protocol.data.game.RegistryEntry;
import org.geysermc.mcprotocollib.protocol.data.game.chunk.ChunkSection;
import org.geysermc.mcprotocollib.protocol.data.game.chunk.DataPalette;
import org.geysermc.mcprotocollib.protocol.data.game.chunk.PalettedWorldState;
import org.geysermc.mcprotocollib.protocol.data.game.entity.metadata.GlobalPos;
import org.geysermc.mcprotocollib.protocol.data.game.entity.player.GameMode;
import org.geysermc.mcprotocollib.protocol.data.game.entity.player.PlayerSpawnInfo;
import org.geysermc.mcprotocollib.protocol.data.game.level.LightUpdateData;
import org.geysermc.mcprotocollib.protocol.data.game.level.notify.GameEvent;
import org.geysermc.mcprotocollib.protocol.packet.common.clientbound.ClientboundClearDialogPacket;
import org.geysermc.mcprotocollib.protocol.packet.common.clientbound.ClientboundCustomPayloadPacket;
import org.geysermc.mcprotocollib.protocol.packet.configuration.clientbound.ClientboundFinishConfigurationPacket;
import org.geysermc.mcprotocollib.protocol.packet.configuration.clientbound.ClientboundShowDialogConfigurationPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.ClientboundLoginPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.entity.player.ClientboundPlayerPositionPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.level.ClientboundGameEventPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.level.ClientboundLevelChunkWithLightPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.level.ClientboundSetChunkCacheCenterPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.level.ClientboundSetChunkCacheRadiusPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.level.ClientboundSetDefaultSpawnPositionPacket;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import static com.zenith.Globals.*;

/**
 * Client -> proxy password login.
 *
 * When enabled (and the whitelist is disabled), clients must supply the configured password
 * before any real game data is sent to them.
 * - 1.21.6+ clients: a native dialog is shown during the configuration phase (before entering the game).
 * - older clients: they are put into a blank "login lobby" and must type {@code /login <password>}.
 *
 * Wrong or missing password -> immediate disconnect. A timeout also disconnects.
 */
public class PasswordLoginHelper {
    public static final PasswordLoginHelper INSTANCE = new PasswordLoginHelper();

    public static final String DIALOG_ACTION_ID = "zenith:login";
    public static final String DIALOG_CANCEL_ID = "zenith:cancel";

    /**
     * The password login gate only applies when the whitelist is disabled.
     * When the whitelist is enabled it IS the authentication layer and the
     * extra password prompt should not run.
     */
    public static boolean passwordLoginActive() {
        return CONFIG.server.passwordLogin.enabled && !CONFIG.server.extra.whitelist.enable;
    }

    /**
     * Name protection (connecting username must match the account username) also only applies
     * when the whitelist is disabled - the whitelist is the authentication layer when enabled.
     */
    public static boolean nameProtectionActive() {
        return CONFIG.server.nameProtection.enable && !CONFIG.server.extra.whitelist.enable;
    }

    /**
     * Used by the console commands when a protection setting is tightened while players are connected.
     * Only actually kicks when that protection is currently the active authentication layer
     * (i.e. when the whitelist is disabled - otherwise whitelist is in charge and these protections are inert).
     * Players are already in-game here, so their locale has been recorded - localize per player.
     */
    public static void kickAllConnectedPlayers(final String en, final String zhTw, final String zhCn) {
        final var connections = Proxy.getInstance().getActiveConnections().getArray();
        for (int i = 0; i < connections.length; i++) {
            final var connection = connections[i];
            if (!connection.isDisconnected()) {
                connection.disconnect(localize(connection, en, zhTw, zhCn));
            }
        }
        SERVER_LOG.info("Kicked all connected players");
    }

    public static void kickNameMismatchedPlayers() {
        final UUID expectedUuid = expectedAccountUuid();
        final var connections = Proxy.getInstance().getActiveConnections().getArray();
        for (int i = 0; i < connections.length; i++) {
            final var connection = connections[i];
            if (connection.isDisconnected()) continue;
            final var profile = connection.getProfileCache().getProfile();
            if (profile == null || profile.getId() == null) continue;
            // compare by UUID, not displayed name: offline servers (e.g. 3C3U) may rename the in-game
            // display name (prefix/suffix) while the connecting identity (and its UUID) stays the same.
            // offline UUIDs are derived from the username (matching the server's validation rule).
            if (!expectedUuid.equals(profile.getId())) {
                connection.disconnect(localize(connection,
                    "Name protection enabled: username does not match the proxy account.",
                    "已啟用名稱保護：使用者名稱與 Proxy 帳號不符。",
                    "已启用名称保护：用户名与 Proxy 账号不符。"));
            }
        }
    }

    /**
     * The UUID the connecting client must have for the proxy account under the current auth type.
     * - offline accounts: the offline UUID derived from the configured username
     * - premium accounts: the UUID of the currently logged-in proxy profile (falls back to the
     *   offline derivation if the profile cache is not populated yet)
     */
    private static UUID expectedAccountUuid() {
        if (CONFIG.authentication.accountType != Config.Authentication.AccountType.OFFLINE) {
            final var proxyProfile = CACHE.getProfileCache().getProfile();
            if (proxyProfile != null && proxyProfile.getId() != null) return proxyProfile.getId();
        }
        return Config.Authentication.generateOfflineUUID(CONFIG.authentication.username);
    }

    private enum Lang {
        TRADITIONAL,
        SIMPLIFIED,
        ENGLISH
    }

    // ================================================================
    // Entry: called by LoginAckHandler once the client is in configuration
    // ================================================================

    public void onEnteredConfiguration(final ServerSession session) {
        if (CONFIG.server.passwordLogin.passwordHash.isEmpty()) {
            SERVER_LOG.error("Password login is enabled but no password has been set. Use `clientConnection passwordLogin set <password>`.");
            // <1.20.2 clients have no configuration phase: their locale will never arrive, use all languages.
            // otherwise wait a beat so the ClientInformation (locale) arrives first and localize properly
            if (session.getProtocolVersion().olderThan(ProtocolVersion.v1_20_2)) {
                session.disconnect("Password login enabled but no password has been configured.\n"
                    + "已啟用密碼登入，但尚未設定密碼。\n"
                    + "已启用密码登录，但尚未设置密码。");
            } else {
                scheduleLocalizedDisconnect(session, () -> localize(session,
                    "Password login enabled but no password has been configured.",
                    "已啟用密碼登入，但尚未設定密碼。",
                    "已启用密码登录，但尚未设置密码。"));
            }
            return;
        }
        if (isIpInLockout(session)) {
            // brute-force cooldown active: reject the connection outright
            disconnectWithLockoutMessage(session);
            return;
        }
        scheduleTimeout(session);
        if (supportsDialog(session)) {
            // wait a beat so the client's ClientInformation (locale) arrives before rendering the dialog text
            EXECUTOR.schedule(() -> {
                if (!session.isPasswordAuthenticated() && session.isConnected()) {
                    showLoginDialog(session);
                }
            }, 200L, TimeUnit.MILLISECONDS);
        } else if (supportsLobby(session)) {
            enterLoginLobby(session);
        } else {
            // <1.20.2 clients have no configuration phase: their locale will never arrive, use all languages.
            // otherwise wait a beat so the ClientInformation (locale) arrives first and localize properly
            if (session.getProtocolVersion().olderThan(ProtocolVersion.v1_20_2)) {
                session.disconnect(unsupportedVersionMessageMulti());
            } else {
                scheduleLocalizedDisconnect(session, () -> localizedUnsupportedVersionMessage(session));
            }
        }
    }

    /** Disconnects after a short delay so the client's locale has arrived for proper message localization. */
    private void scheduleLocalizedDisconnect(final ServerSession session, final java.util.function.Supplier<String> messageSupplier) {
        EXECUTOR.schedule(() -> {
            if (session.isConnected()) session.disconnect(messageSupplier.get());
        }, 200L, TimeUnit.MILLISECONDS);
    }

    /** 1.21.6+: native login dialog during configuration (encrypted). */
    private boolean supportsDialog(final ServerSession session) {
        return session.getProtocolVersion().newerThanOrEqualTo(ProtocolVersion.v1_21_6);
    }

    /**
     * 1.20.2 - 1.21.5: login lobby.
     * 1.20.5+ has the pure-encryption handshake; 1.20.2-1.20.4 send the password in plaintext
     * and therefore require CONFIG.server.passwordLogin.allowUnencryptedLegacy.
     * Below 1.20.2 there is no configuration/re-configuration mechanism to promote the lobby
     * to the real game, so those versions are unsupported.
     */
    private boolean supportsLobby(final ServerSession session) {
        final var pv = session.getProtocolVersion();
        if (pv.olderThan(ProtocolVersion.v1_20_2)) return false;
        if (pv.newerThanOrEqualTo(ProtocolVersion.v1_20_5)) return true;
        return CONFIG.server.passwordLogin.allowUnencryptedLegacy;
    }

    private boolean isUnencryptedLobby(final ServerSession session) {
        final var pv = session.getProtocolVersion();
        return pv.newerThanOrEqualTo(ProtocolVersion.v1_20_2) && pv.olderThan(ProtocolVersion.v1_20_5);
    }

    private String unsupportedVersionMessageMulti() {
        // for <1.20.2 clients (no configuration phase, locale never arrives): show every supported language
        final String required = requiredMinimumVersion();
        return "Unsupported client version for password login. Requires " + required + " or newer.\n"
            + "不支援此客戶端版本使用密碼登入。需要 " + required + " 以上的版本。\n"
            + "不支持此客户端版本使用密码登录。需要 " + required + " 以上的版本。";
    }

    /** Localized variant for clients that do have a configuration phase (locale is available). */
    private String localizedUnsupportedVersionMessage(final ServerSession session) {
        final String required = requiredMinimumVersion();
        return localize(session,
            "Unsupported client version for password login. Requires " + required + " or newer.",
            "不支援此客戶端版本使用密碼登入。需要 " + required + " 以上的版本。",
            "不支持此客户端版本使用密码登录。需要 " + required + " 以上的版本。");
    }

    private String requiredMinimumVersion() {
        // allowUnencrypted=true: 1.20.2-1.20.4 can use the (plaintext) lobby -> minimum 1.20.2
        // allowUnencrypted=false: only 1.20.5+ (encrypted) lobby -> minimum 1.20.5
        return CONFIG.server.passwordLogin.allowUnencryptedLegacy ? "1.20.2" : "1.20.5";
    }

    private void scheduleTimeout(final ServerSession session) {
        if (session.getPasswordLoginTimeoutTask() != null) return;
        final int timeoutSeconds = CONFIG.server.passwordLogin.timeoutSeconds;
        session.setPasswordLoginTimeoutTask(EXECUTOR.schedule(() -> {
            if (!session.isPasswordAuthenticated() && session.isConnected()) {
                SERVER_LOG.info("Disconnecting {} due to password login timeout", session.getName());
                session.disconnect(localize(session, "Login timed out.", "登入逾時", "登录超时"));
            }
        }, timeoutSeconds, TimeUnit.SECONDS));
    }

    private void cancelTimeout(final ServerSession session) {
        final var task = session.getPasswordLoginTimeoutTask();
        if (task != null) {
            task.cancel(false);
            session.setPasswordLoginTimeoutTask(null);
        }
        final var hintTask = session.getPasswordLoginHintTask();
        if (hintTask != null) {
            hintTask.cancel(false);
            session.setPasswordLoginHintTask(null);
        }
        final var warningTask = session.getPasswordLoginWarningTask();
        if (warningTask != null) {
            warningTask.cancel(false);
            session.setPasswordLoginWarningTask(null);
        }
    }

    // ================================================================
    // Dialog path (1.21.6+): native login dialog during configuration
    // ================================================================

    private void showLoginDialog(final ServerSession session) {
        try {
            final String snbt = buildDialogSnbt(session);
            final CompoundTag tag = SNBT.deserializeCompoundTag(snbt);
            // false = unnamed root tag, matching vanilla Network NBT (writeAnyTag)
            final MNBT mnbt = MNBTIO.write(tag, false);
            session.sendAsync(new ClientboundShowDialogConfigurationPacket(mnbt));
        } catch (final Exception e) {
            SERVER_LOG.error("Failed to send login dialog to {}", session.getName(), e);
            session.disconnect("Login failed.");
        }
    }

    private String buildDialogSnbt(final ServerSession session) {
        final String title = localize(session, "ZenithProxy Login", "ZenithProxy 登入", "ZenithProxy 登录");
        final String prompt = localize(session, "Password", "密碼", "密码");
        final String yes = localize(session, "Login", "登入", "登录");
        final String no = localize(session, "Cancel", "取消", "取消");
        // note: CommonDialogData, CommonButtonData and InputControl codecs are inlined
        // (no `common`/`button`/`control` wrapper keys; `type` sits next to `key`)
        return "{type:\"minecraft:confirmation\","
            + "title:{text:\"" + jsonEscape(title) + "\"},"
            + "pause:0b,"
            + "can_close_with_escape:0b,"
            + "after_action:\"close\","
            + "body:[],"
            + "inputs:[{key:\"password\",type:\"minecraft:text\",width:200,label:{text:\"" + jsonEscape(prompt) + "\"},label_visible:1b,initial:\"\",max_length:64}],"
            + "yes:{label:{text:\"" + jsonEscape(yes) + "\"},action:{type:\"minecraft:dynamic/custom\",id:\"" + DIALOG_ACTION_ID + "\"}},"
            + "no:{label:{text:\"" + jsonEscape(no) + "\"},action:{type:\"minecraft:dynamic/custom\",id:\"" + DIALOG_CANCEL_ID + "\"}}"
            + "}";
    }

    private static String jsonEscape(final String s) {
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    // ================================================================
    // Lobby path (< 1.21.6): blank world + /login <password>
    // ================================================================

    private void enterLoginLobby(final ServerSession session) {
        session.setInLoginLobby(true);
        sendLobbyRegistries(session);
        session.sendAsync(new ClientboundFinishConfigurationPacket());
        // FinishConfigurationHandler routes to finishLobbyLogin()
    }

    /** Called by FinishConfigurationHandler once the lobby client finishes configuration. */
    public void finishLobbyLogin(final ServerSession session) {
        session.sendAsync(new ClientboundLoginPacket(
            0,
            false,
            new Key[]{Key.key("minecraft", "overworld")},
            20,
            2, // view distance: keep small so "Downloading terrain" expects few chunks
            2, // simulation distance
            false,
            false,
            false,
            new PlayerSpawnInfo(
                0,
                Key.key("minecraft", "overworld"),
                0L,
                GameMode.ADVENTURE,
                GameMode.ADVENTURE,
                false,
                true,
                null,
                0,
                63
            ),
            false));
        session.sendAsync(new ClientboundSetDefaultSpawnPositionPacket(
            new GlobalPos(Key.key("minecraft", "overworld"), 0, 500, 0),
            0.0f, 0.0f));
        // signal the client that chunk loading has begun (LevelLoadTracker: WaitingForServer -> WaitingForPlayerChunk)
        session.sendAsync(new ClientboundGameEventPacket(GameEvent.LEVEL_CHUNKS_LOAD_START, null));
        // shrink the client's expected chunk radius to 1 (3x3 = 9 chunks) so that
        // "Downloading terrain" completes as soon as those 9 chunks arrive (no 30s wait)
        session.sendAsync(new ClientboundSetChunkCacheCenterPacket(0, 0));
        session.sendAsync(new ClientboundSetChunkCacheRadiusPacket(1));
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                sendVoidChunk(session, dx, dz);
            }
        }
        session.sendAsync(new ClientboundPlayerPositionPacket(0, 0.0d, 500.0d, 0.0d, 0.0d, 0.0d, 0.0d, 0.0f, 0.0f));
        // prompt; repeat every few seconds so it is visible without opening the chat
        schedulePromptTask(session);
        if (isUnencryptedLobby(session)) {
            scheduleUnencryptedWarningTask(session);
        }
        // keep the lobby ticking so the client doesn't think we are dead
        session.getEventLoop().scheduleAtFixedRate(new com.zenith.network.SKeepAliveTask(session), 0, 2, TimeUnit.SECONDS);
    }

    private void schedulePromptTask(final ServerSession session) {
        session.setPasswordLoginHintTask(EXECUTOR.scheduleAtFixedRate(() -> {
            if (!session.isInLoginLobby() || session.isPasswordAuthenticated() || !session.isConnected()) {
                final var task = session.getPasswordLoginHintTask();
                if (task != null) task.cancel(false);
                return;
            }
            sendPromptMessage(session);
        }, 0, 4, TimeUnit.SECONDS));
    }

    private void scheduleUnencryptedWarningTask(final ServerSession session) {
        session.setPasswordLoginWarningTask(EXECUTOR.scheduleAtFixedRate(() -> {
            if (!session.isInLoginLobby() || session.isPasswordAuthenticated() || !session.isConnected()) {
                final var task = session.getPasswordLoginWarningTask();
                if (task != null) task.cancel(false);
                return;
            }
            sendUnencryptedWarningMessage(session);
        }, 0, 8, TimeUnit.SECONDS));
    }

    private void sendPromptMessage(final ServerSession session) {
        // overlay=true renders on the action bar, always visible without opening chat
        session.sendAsync(new org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.ClientboundSystemChatPacket(
            net.kyori.adventure.text.Component.text(localize(session, "Please type /login <password> to log in.", "請輸入 /login <密碼> 以完成登入", "请输入 /login <密码> 以完成登录")), true));
    }

    private void sendUnencryptedWarningMessage(final ServerSession session) {
        session.sendAsync(new org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.ClientboundSystemChatPacket(
            net.kyori.adventure.text.Component.text(localize(session,
                "WARNING: this client version cannot use encrypted login; your password will be sent in plaintext and may be intercepted.",
                "警告：此客戶端版本無法使用加密登入，密碼將以明文傳輸，可能被攔截。",
                "警告：此客户端版本无法使用加密登录，密码将以明文传输，可能被拦截。")), true));
    }

    /**
     * Sends a single all-air chunk at the given chunk position so the client finishes its
     * "Downloading terrain" phase immediately. The block and biome palettes are explicitly
     * populated with entry 0 (air / plains) - an empty palette makes the vanilla client crash.
     */
    private void sendVoidChunk(final ServerSession session, final int x, final int z) {
        try {
            final int sectionCount = 24; // 384 / 16
            final PalettedWorldState palettedWorldState = new PalettedWorldState(
                sectionCount,
                BLOCK_DATA.blockStateRegistrySize(),
                BlockRegistry.AIR.minStateId(),
                4, // small indirect biome palette size (>=1 so bits per entry > 0)
                0, // default biome id = plains (the only biome in the lobby registry)
                BLOCK_DATA.fluidStates()
            );
            final ChunkSection[] sections = new ChunkSection[sectionCount];
            for (int i = 0; i < sectionCount; i++) {
                final ChunkSection section = new ChunkSection(0, 0,
                    DataPalette.createForChunk(palettedWorldState),
                    DataPalette.createForBiome(palettedWorldState));
                // ensure each palette has at least one entry: air block id 0, plains biome id 0
                section.getChunkData().set(0, 0, 0, 0);
                section.getBiomeData().set(0, 0, 0, 0);
                sections[i] = section;
            }
            final long[] empty = new long[0];
            final int lightSections = sectionCount + 2;
            final BitSet skylightMask = new BitSet(lightSections);
            skylightMask.set(0, lightSections);
            final List<byte[]> skyUpdates = new ArrayList<>(lightSections);
            for (int i = 0; i < lightSections; i++) skyUpdates.add(fullBrightSkyLightData());
            final LightUpdateData light = new LightUpdateData(
                skylightMask.toLongArray(),
                empty,
                empty,
                empty,
                skyUpdates,
                List.of()
            );
            session.sendAsync(new ClientboundLevelChunkWithLightPacket(x, z, sections, java.util.Map.of(), new org.geysermc.mcprotocollib.protocol.data.game.level.block.BlockEntityInfo[0], light));
        } catch (final Throwable e) {
            SERVER_LOG.error("Failed sending lobby void chunk to {}", session.getName(), e);
        }
    }

    private static byte[] fullBrightSkyLightData() {
        final byte[] data = new byte[2048];
        java.util.Arrays.fill(data, (byte) 0b11111111);
        return data;
    }

    /** Minimal vanilla registries so pre-1.21.6 clients can finish configuration into the empty lobby world. */
    private void sendLobbyRegistries(final ServerSession session) {
        // dimension type: overworld
        final String dimensionType = "{min_y:-64,height:384,logical_height:384,"
            + "infiniburn:\"#minecraft:infiniburn_overworld\",effects:\"minecraft:overworld\","
            + "has_skylight:1b,has_ceiling:0b,ultrawarm:0b,natural:1b,coordinate_scale:1.0,"
            + "bed_works:1b,respawn_anchor_works:1b,ambient_light:0.0,piglin_safe:1b,"
            + "monster_spawn_light_level:0,monster_spawn_block_light_limit:0}";
        // biome: plains (minimal fields)
        final String biome = "{has_precipitation:1b,temperature:0.8,downfall:0.4,"
            + "effects:{sky_color:7907327,water_color:4159204,water_fog_color:329011,"
            + "grass_color:8978276,fog_color:12638463}}";
        // painting variant (registry must be non-empty)
        final String paintingVariant = "{asset_id:\"minecraft:kebab\",height:1,width:1}";
        // wolf variant (registry must be non-empty). 1.21.5+ uses the assets/baby_assets format
        // (NETWORK_CODEC); viabackwards needs the "assets" key when translating to 1.21.4 and below.
        final String wolfAssets = "{\"wild\":\"minecraft:entity/wolf/wolf\","
            + "\"tame\":\"minecraft:entity/wolf/wolf_tame\","
            + "\"angry\":\"minecraft:entity/wolf/wolf_angry\"}";
        final String wolfVariant = "{assets:" + wolfAssets + ",baby_assets:" + wolfAssets + "}";
        // the vanilla 1.21.x damage_type registry (client's DamageSources requires nearly all of these)
        final String[] damageTypes = {
            "arrow:{exhaustion:0.1,message_id:\"arrow\",scaling:\"when_caused_by_living_non_player\"}",
            "bad_respawn_point:{death_message_type:\"intentional_game_design\",exhaustion:0.1,message_id:\"badRespawnPoint\",scaling:\"always\"}",
            "cactus:{exhaustion:0.1,message_id:\"cactus\",scaling:\"when_caused_by_living_non_player\"}",
            "campfire:{effects:\"burning\",exhaustion:0.1,message_id:\"inFire\",scaling:\"when_caused_by_living_non_player\"}",
            "cramming:{exhaustion:0.0,message_id:\"cramming\",scaling:\"when_caused_by_living_non_player\"}",
            "dragon_breath:{exhaustion:0.0,message_id:\"dragonBreath\",scaling:\"when_caused_by_living_non_player\"}",
            "drown:{effects:\"drowning\",exhaustion:0.0,message_id:\"drown\",scaling:\"when_caused_by_living_non_player\"}",
            "dry_out:{exhaustion:0.1,message_id:\"dryout\",scaling:\"when_caused_by_living_non_player\"}",
            "ender_pearl:{death_message_type:\"fall_variants\",exhaustion:0.0,message_id:\"fall\",scaling:\"when_caused_by_living_non_player\"}",
            "explosion:{exhaustion:0.1,message_id:\"explosion\",scaling:\"always\"}",
            "fall:{death_message_type:\"fall_variants\",exhaustion:0.0,message_id:\"fall\",scaling:\"when_caused_by_living_non_player\"}",
            "falling_anvil:{exhaustion:0.1,message_id:\"anvil\",scaling:\"when_caused_by_living_non_player\"}",
            "falling_block:{exhaustion:0.1,message_id:\"fallingBlock\",scaling:\"when_caused_by_living_non_player\"}",
            "falling_stalactite:{exhaustion:0.1,message_id:\"fallingStalactite\",scaling:\"when_caused_by_living_non_player\"}",
            "fireball:{effects:\"burning\",exhaustion:0.1,message_id:\"fireball\",scaling:\"when_caused_by_living_non_player\"}",
            "fireworks:{exhaustion:0.1,message_id:\"fireworks\",scaling:\"when_caused_by_living_non_player\"}",
            "fly_into_wall:{exhaustion:0.0,message_id:\"flyIntoWall\",scaling:\"when_caused_by_living_non_player\"}",
            "freeze:{effects:\"freezing\",exhaustion:0.0,message_id:\"freeze\",scaling:\"when_caused_by_living_non_player\"}",
            "generic:{exhaustion:0.0,message_id:\"generic\",scaling:\"when_caused_by_living_non_player\"}",
            "generic_kill:{exhaustion:0.0,message_id:\"genericKill\",scaling:\"when_caused_by_living_non_player\"}",
            "hot_floor:{effects:\"burning\",exhaustion:0.1,message_id:\"hotFloor\",scaling:\"when_caused_by_living_non_player\"}",
            "in_fire:{effects:\"burning\",exhaustion:0.1,message_id:\"inFire\",scaling:\"when_caused_by_living_non_player\"}",
            "in_wall:{exhaustion:0.0,message_id:\"inWall\",scaling:\"when_caused_by_living_non_player\"}",
            "indirect_magic:{exhaustion:0.0,message_id:\"indirectMagic\",scaling:\"when_caused_by_living_non_player\"}",
            "lava:{effects:\"burning\",exhaustion:0.1,message_id:\"lava\",scaling:\"when_caused_by_living_non_player\"}",
            "lightning_bolt:{exhaustion:0.1,message_id:\"lightningBolt\",scaling:\"when_caused_by_living_non_player\"}",
            "mace_smash:{exhaustion:0.1,message_id:\"mace_smash\",scaling:\"when_caused_by_living_non_player\"}",
            "magic:{exhaustion:0.0,message_id:\"magic\",scaling:\"when_caused_by_living_non_player\"}",
            "mob_attack:{exhaustion:0.1,message_id:\"mob\",scaling:\"when_caused_by_living_non_player\"}",
            "mob_attack_no_aggro:{exhaustion:0.1,message_id:\"mob\",scaling:\"when_caused_by_living_non_player\"}",
            "mob_projectile:{exhaustion:0.1,message_id:\"mob\",scaling:\"when_caused_by_living_non_player\"}",
            "on_fire:{effects:\"burning\",exhaustion:0.0,message_id:\"onFire\",scaling:\"when_caused_by_living_non_player\"}",
            "out_of_world:{exhaustion:0.0,message_id:\"outOfWorld\",scaling:\"when_caused_by_living_non_player\"}",
            "outside_border:{exhaustion:0.0,message_id:\"outsideBorder\",scaling:\"when_caused_by_living_non_player\"}",
            "player_attack:{exhaustion:0.1,message_id:\"player\",scaling:\"when_caused_by_living_non_player\"}",
            "player_explosion:{exhaustion:0.1,message_id:\"explosion.player\",scaling:\"always\"}",
            "sonic_boom:{exhaustion:0.0,message_id:\"sonic_boom\",scaling:\"always\"}",
            "spit:{exhaustion:0.1,message_id:\"mob\",scaling:\"when_caused_by_living_non_player\"}",
            "stalagmite:{exhaustion:0.0,message_id:\"stalagmite\",scaling:\"when_caused_by_living_non_player\"}",
            "starve:{exhaustion:0.0,message_id:\"starve\",scaling:\"when_caused_by_living_non_player\"}",
            "sting:{exhaustion:0.1,message_id:\"sting\",scaling:\"when_caused_by_living_non_player\"}",
            "sweet_berry_bush:{effects:\"poking\",exhaustion:0.1,message_id:\"sweetBerryBush\",scaling:\"when_caused_by_living_non_player\"}",
            "thorns:{effects:\"thorns\",exhaustion:0.1,message_id:\"thorns\",scaling:\"when_caused_by_living_non_player\"}",
            "thrown:{exhaustion:0.1,message_id:\"thrown\",scaling:\"when_caused_by_living_non_player\"}",
            "trident:{exhaustion:0.1,message_id:\"trident\",scaling:\"when_caused_by_living_non_player\"}",
            "unattributed_fireball:{effects:\"burning\",exhaustion:0.1,message_id:\"onFire\",scaling:\"when_caused_by_living_non_player\"}",
            "wind_charge:{exhaustion:0.1,message_id:\"mob\",scaling:\"when_caused_by_living_non_player\"}",
            "wither:{exhaustion:0.0,message_id:\"wither\",scaling:\"when_caused_by_living_non_player\"}",
            "wither_skull:{exhaustion:0.1,message_id:\"witherSkull\",scaling:\"when_caused_by_living_non_player\"}"
        };
        try {
            session.sendAsync(new org.geysermc.mcprotocollib.protocol.packet.configuration.clientbound.ClientboundRegistryDataPacket(
                "minecraft:dimension_type",
                List.of(new RegistryEntry("minecraft:overworld", MNBTIO.write(SNBT.deserializeCompoundTag(dimensionType), false)))));
            session.sendAsync(new org.geysermc.mcprotocollib.protocol.packet.configuration.clientbound.ClientboundRegistryDataPacket(
                "minecraft:worldgen/biome",
                List.of(new RegistryEntry("minecraft:plains", MNBTIO.write(SNBT.deserializeCompoundTag(biome), false)))));
            session.sendAsync(new org.geysermc.mcprotocollib.protocol.packet.configuration.clientbound.ClientboundRegistryDataPacket(
                "minecraft:painting_variant",
                List.of(new RegistryEntry("minecraft:kebab", MNBTIO.write(SNBT.deserializeCompoundTag(paintingVariant), false)))));
            session.sendAsync(new org.geysermc.mcprotocollib.protocol.packet.configuration.clientbound.ClientboundRegistryDataPacket(
                "minecraft:wolf_variant",
                List.of(new RegistryEntry("minecraft:classic", MNBTIO.write(SNBT.deserializeCompoundTag(wolfVariant), false)))));
            final var damageEntries = new java.util.ArrayList<RegistryEntry>();
            for (final String entry : damageTypes) {
                final int sep = entry.indexOf(':');
                final String id = entry.substring(0, sep);
                final String snbt = entry.substring(sep + 1);
                damageEntries.add(new RegistryEntry("minecraft:" + id, MNBTIO.write(SNBT.deserializeCompoundTag(snbt), false)));
            }
            session.sendAsync(new org.geysermc.mcprotocollib.protocol.packet.configuration.clientbound.ClientboundRegistryDataPacket(
                "minecraft:damage_type", damageEntries));
            // 1.21.5+ requires additional animal variant registries to be non-empty
            if (session.getProtocolVersion().newerThanOrEqualTo(ProtocolVersion.v1_21_5)) {
                sendSimpleAssetVariantRegistry(session, "minecraft:cat_variant", "minecraft:black", "minecraft:entity/cat/black");
                sendSimpleAssetVariantRegistry(session, "minecraft:chicken_variant", "minecraft:temperate", "minecraft:entity/chicken/temperate_chicken");
                sendSimpleAssetVariantRegistry(session, "minecraft:cow_variant", "minecraft:temperate", "minecraft:entity/cow/temperate_cow");
                sendSimpleAssetVariantRegistry(session, "minecraft:frog_variant", "minecraft:temperate", "minecraft:entity/frog/temperate_frog");
                sendSimpleAssetVariantRegistry(session, "minecraft:pig_variant", "minecraft:temperate", "minecraft:entity/pig/temperate_pig");
                // wolf_sound_variant: 26.1 format is {adult_sounds, baby_sounds} (viabackwards 26.1->1.21.11
                // flattens adult_sounds to the top level and expects that compound to exist)
                final String wolfSoundSet = "{ambient_sound:\"minecraft:entity.wolf.ambient\",death_sound:\"minecraft:entity.wolf.death\",growl_sound:\"minecraft:entity.wolf.growl\",hurt_sound:\"minecraft:entity.wolf.hurt\",pant_sound:\"minecraft:entity.wolf.pant\",whine_sound:\"minecraft:entity.wolf.whine\",step_sound:\"minecraft:entity.wolf.step\"}";
                session.sendAsync(new org.geysermc.mcprotocollib.protocol.packet.configuration.clientbound.ClientboundRegistryDataPacket(
                    "minecraft:wolf_sound_variant",
                    List.of(new RegistryEntry("minecraft:classic", MNBTIO.write(SNBT.deserializeCompoundTag(
                        "{adult_sounds:" + wolfSoundSet + ",baby_sounds:" + wolfSoundSet + "}"), false)))));
            }
        } catch (final Exception e) {
            SERVER_LOG.error("Failed sending lobby registries to {}", session.getName(), e);
            session.disconnect("Login failed.");
        }
    }

    /** Sends a registry with a single entry of the form {asset_id: ..., spawn_conditions:[{priority:0}]}. */
    private void sendSimpleAssetVariantRegistry(final ServerSession session,
                                                final String registry,
                                                final String id,
                                                final String assetId) throws Exception {
        session.sendAsync(new org.geysermc.mcprotocollib.protocol.packet.configuration.clientbound.ClientboundRegistryDataPacket(
            registry,
            List.of(new RegistryEntry(id, MNBTIO.write(SNBT.deserializeCompoundTag(
                "{asset_id:\"" + assetId + "\",spawn_conditions:[{priority:0}]}"), false)))));
    }

    // ================================================================
    // Password verification
    // ================================================================

    /**
     * Called from the dialog response handler (1.21.6+) and the /login chat handler (lobby).
     */
    public void handlePassword(final ServerSession session, final String password) {
        final String pwd = password == null ? "" : password;
        final String tempId = verifyPassword(pwd);
        if (tempId != null) {
            if (tempId.isEmpty()) {
                // main password correct (not a temporary password): reset this IP's failed attempts
                clearFailedAttempts(session);
            }
            onPasswordVerified(session, tempId);
        } else {
            SERVER_LOG.info("Incorrect password from {} [{}]", session.getName(), session.getRemoteAddress());
            recordFailedAttempt(session);
            if (isIpInLockout(session)) {
                disconnectWithLockoutMessage(session);
            } else {
                session.disconnect(localize(session, "Incorrect password.", "密碼錯誤", "密码错误"));
            }
        }
    }

    public void handleDialogAction(final ServerSession session, final MNBT payload) {
        String password = null;
        if (payload != null) {
            try {
                final Tag tag = MNBTIO.read(payload);
                if (tag instanceof CompoundTag compound) {
                    final StringTag st = compound.getStringTag("password");
                    if (st != null) password = st.getValue();
                }
            } catch (final Exception e) {
                SERVER_LOG.debug("Failed parsing login dialog payload", e);
            }
        }
        handlePassword(session, password);
    }

    /**
     * @return the id of the matching temporary password, or {@code ""} if the main password matched,
     *         or {@code null} on failure
     */
    private String verifyPassword(final String password) {
        final String stored = CONFIG.server.passwordLogin.passwordHash;
        if (stored != null && !stored.isEmpty()) {
            final String digest = sha256Hex(password);
            if (MessageDigest.isEqual(digest.getBytes(StandardCharsets.UTF_8), stored.getBytes(StandardCharsets.UTF_8))) {
                return "";
            }
        }
        // temporary passwords
        final long now = System.currentTimeMillis();
        final var tempPasswords = CONFIG.server.passwordLogin.tempPasswords;
        for (int i = 0; i < tempPasswords.size(); i++) {
            final var temp = tempPasswords.get(i);
            if (temp.expiresAtEpochMs > now && temp.passwordHash != null && temp.passwordHash.equals(sha256Hex(password))) {
                return temp.id;
            }
        }
        return null;
    }

    public static String sha256Hex(final String input) {
        try {
            final MessageDigest md = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(md.digest(input.getBytes(StandardCharsets.UTF_8)));
        } catch (final NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
    }

    // ================================================================
    // Temporary passwords
    // ================================================================

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();
    private static final char[] TEMP_PASSWORD_CHARS = "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz23456789".toCharArray(); // no confusable I/l/O/0
    private static final DateTimeFormatter EXPIRY_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    /** Generates a new temporary password valid for the given minutes. Returns the plaintext password (shown once). */
    public static String generateTempPassword(final int validMinutes) {
        final StringBuilder plain = new StringBuilder(16);
        for (int i = 0; i < 16; i++) plain.append(TEMP_PASSWORD_CHARS[SECURE_RANDOM.nextInt(TEMP_PASSWORD_CHARS.length)]);
        final String id = Integer.toHexString(SECURE_RANDOM.nextInt() & 0x7fffffff);
        CONFIG.server.passwordLogin.tempPasswords.add(new TemporaryPassword(
            id,
            sha256Hex(plain.toString()),
            System.currentTimeMillis() + validMinutes * 60_000L));
        saveConfigAsync();
        return plain.toString();
    }

    /** Removes a temporary password by id (matching prefix). Kicks players bound to it. Returns true if removed. */
    public static boolean removeTempPassword(final String idOrPrefix) {
        final var tempPasswords = CONFIG.server.passwordLogin.tempPasswords;
        for (int i = 0; i < tempPasswords.size(); i++) {
            if (tempPasswords.get(i).id.equals(idOrPrefix) || tempPasswords.get(i).id.startsWith(idOrPrefix)) {
                final String removedId = tempPasswords.get(i).id;
                tempPasswords.remove(i);
                saveConfigAsync();
                disconnectPlayersBoundToTempPassword(removedId,
                    "Your temporary password has been revoked. Please reconnect with a valid password.",
                    "您的臨時密碼已被撤銷，請使用有效密碼重新連線。",
                    "您的临时密码已被撤销，请使用有效密码重新连接。");
                return true;
            }
        }
        return false;
    }

    /** Lists non-expired temporary passwords (id + expiry), oldest first. */
    public static List<String> listTempPasswords() {
        final long now = System.currentTimeMillis();
        final var tempPasswords = CONFIG.server.passwordLogin.tempPasswords;
        final List<String> result = new ArrayList<>();
        for (int i = 0; i < tempPasswords.size(); i++) {
            final var temp = tempPasswords.get(i);
            if (temp.expiresAtEpochMs <= now) continue;
            result.add("`" + temp.id + "` - expires " + Instant.ofEpochMilli(temp.expiresAtEpochMs).atZone(ZoneId.systemDefault()).format(EXPIRY_FORMATTER));
        }
        return result;
    }

    /**
     * Sweeps expired temporary passwords: removes them and force-disconnects any players
     * currently bound to them (those who logged in with that temporary password).
     */
    public static void sweepTempPasswords() {
        final long now = System.currentTimeMillis();
        final var tempPasswords = CONFIG.server.passwordLogin.tempPasswords;
        for (int i = tempPasswords.size() - 1; i >= 0; i--) {
            final var temp = tempPasswords.get(i);
            if (temp.expiresAtEpochMs <= now) {
                tempPasswords.remove(i);
                saveConfigAsync();
                disconnectPlayersBoundToTempPassword(temp.id,
                    "Your temporary password has expired.",
                    "您的臨時密碼已過期。",
                    "您的临时密码已过期。");
            }
        }
    }

    private static void disconnectPlayersBoundToTempPassword(final String tempPasswordId, final String en, final String zhTw, final String zhCn) {
        final var connections = Proxy.getInstance().getActiveConnections().getArray();
        for (int i = 0; i < connections.length; i++) {
            final var connection = connections[i];
            if (connection.isDisconnected()) continue;
            if (tempPasswordId.equals(connection.getTempPasswordId())) {
                connection.disconnect(localize(connection, en, zhTw, zhCn));
            }
        }
    }

    // ================================================================
    // Post-verification: promote the session to the real game
    // ================================================================

    private void onPasswordVerified(final ServerSession session, final String tempPasswordId) {
        session.setPasswordAuthenticated(true);
        if (tempPasswordId != null && !tempPasswordId.isEmpty()) {
            // logged in via a temporary password: mark + bind to that temp password id (removed on disconnect)
            session.setTempPasswordAuthenticated(true);
            session.setTempPasswordId(tempPasswordId);
            SERVER_LOG.info("{} [{}] authenticated via temporary password id: {}", session.getName(), session.getRemoteAddress(), tempPasswordId);
        }
        session.setInLoginLobby(false);
        cancelTimeout(session);
        if (!Proxy.getInstance().isConnected()) {
            // nothing to hand over to yet; tell the client to reconnect later
            session.disconnect(localize(session,
                "Proxy is not connected to a server.",
                "Proxy 尚未連線到伺服器。",
                "Proxy 尚未连接到服务器。"));
            return;
        }
        // register as the controlling player
        if (!Proxy.getInstance().getCurrentPlayer().compareAndSet(null, session)) {
            session.disconnect(localize(session, "Someone is already controlling the player.", "已有玩家在控制", "已有玩家在控制"));
            return;
        }
        session.setWhitelistChecked(true);
        SERVER_LOG.info("{} [{}] authenticated via password login", session.getName(), session.getRemoteAddress());
        if (session.getPacketProtocol().getOutboundState() == org.geysermc.mcprotocollib.protocol.data.ProtocolState.CONFIGURATION) {
            // dialog path: still in configuration -> send real registry data + finish
            session.sendAsync(new ClientboundClearDialogPacket());
            sendRealConfiguration(session);
        } else {
            // lobby path: currently in game (the fake lobby world) -> bounce back through configuration
            session.setPasswordLoginUpgrading(true);
            session.sendAsync(new org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.ClientboundStartConfigurationPacket());
        }
    }

    /** Sends the real registry/config data and finishes configuration (same as a normal join sequence). */
    public void sendRealConfiguration(final ServerSession session) {
        CACHE.getRegistriesCache().getRegistryPackets(session::sendAsync, session);
        CACHE.getConfigurationCache().getConfigurationPackets(session::sendAsync, session);
        session.sendAsync(new ClientboundCustomPayloadPacket(Key.key("minecraft:brand"), CACHE.getChunkCache().getServerBrand()));
        session.sendAsync(new ClientboundFinishConfigurationPacket());
    }

    // ================================================================
    // Localization
    // ================================================================

    private static Lang resolveLang(final ServerSession session) {
        final String locale = session.getClientInfoCache().getLocale();
        return switch (locale == null ? "" : locale) {
            case "zh_hk", "zh_tw", "lzh" -> Lang.TRADITIONAL;
            case "zh_cn" -> Lang.SIMPLIFIED;
            default -> Lang.ENGLISH;
        };
    }

    public static String localize(final ServerSession session, final String en, final String traditional, final String simplified) {
        return switch (resolveLang(session)) {
            case TRADITIONAL -> traditional;
            case SIMPLIFIED -> simplified;
            default -> en;
        };
    }

    /** Disconnects a session with a locale-appropriate reason. */
    public static void disconnectLocalized(final ServerSession session, final String en, final String zhTw, final String zhCn) {
        if (session.isConnected()) session.disconnect(localize(session, en, zhTw, zhCn));
    }

    // ================================================================
    // Temporary password command restrictions
    // ================================================================

    /**
     * Whether the given root command (name or alias) is entirely forbidden for temporary-password accounts.
     * Matching is case-insensitive against the configured denied-commands list.
     */
    public static boolean isTempPasswordCommandDenied(final String rootCommand) {
        final var denied = CONFIG.server.passwordLogin.tempPasswordDeniedCommands;
        for (int i = 0; i < denied.size(); i++) {
            if (denied.get(i).equalsIgnoreCase(rootCommand)) return true;
        }
        return false;
    }

    /** Adds a command to the denied list. Returns false if already present. */
    public static boolean addDeniedTempCommand(final String command) {
        if (CONFIG.server.passwordLogin.tempPasswordDeniedCommands.contains(command)) return false;
        CONFIG.server.passwordLogin.tempPasswordDeniedCommands.add(command);
        saveConfigAsync();
        return true;
    }

    /** Removes a command from the denied list. Returns false if not present. */
    public static boolean removeDeniedTempCommand(final String command) {
        if (!CONFIG.server.passwordLogin.tempPasswordDeniedCommands.remove(command)) return false;
        saveConfigAsync();
        return true;
    }

    /** Returns a copy of the denied commands list. */
    public static List<String> listDeniedTempCommands() {
        return new ArrayList<>(CONFIG.server.passwordLogin.tempPasswordDeniedCommands);
    }

    // ================================================================
    // Brute-force protection (per-IP wrong-password lockout)
    // ================================================================

    /** Extracts the client IP (without port) for a session. */
    public static String getClientIp(final ServerSession session) {
        final Object remote = session.getRemoteAddress();
        if (remote instanceof InetSocketAddress isa) {
            final var address = isa.getAddress();
            if (address != null) return address.getHostAddress();
            return isa.getHostString();
        }
        return remote == null ? "unknown" : remote.toString();
    }

    /**
     * Records a wrong-password attempt from the session's IP and escalates the cooldown.
     * The 1st..threshold failures are uncooldowned; from failure (threshold+1) on, each failure
     * sets a cooldown of baseDelay * multiplier^(n), capped at maxDelay.
     */
    public static void recordFailedAttempt(final ServerSession session) {
        final var pwl = CONFIG.server.passwordLogin;
        if (!pwl.lockoutEnabled) return;
        final LoginAttemptRecord record = pwl.failedLoginAttempts.computeIfAbsent(getClientIp(session), k -> new LoginAttemptRecord());
        record.failCount++;
        if (record.failCount > pwl.lockoutThreshold) {
            final long exponent = record.failCount - pwl.lockoutThreshold - 1; // (threshold+1)-th failure -> 0
            final double multiplier = Math.pow(pwl.lockoutMultiplier, exponent);
            long delaySeconds = (long) (pwl.lockoutBaseDelaySeconds * multiplier);
            delaySeconds = Math.min(delaySeconds, pwl.lockoutMaxDelaySeconds);
            record.cooldownUntilEpochMs = System.currentTimeMillis() + delaySeconds * 1000L;
        }
        saveConfigAsync();
    }

    /** Clears the failed-attempt record for a session's IP (called when the main password is entered correctly). */
    public static void clearFailedAttempts(final ServerSession session) {
        if (CONFIG.server.passwordLogin.failedLoginAttempts.remove(getClientIp(session)) != null) {
            saveConfigAsync();
        }
    }

    /** Clears all per-IP failed-attempt records (called when the password is changed). */
    public static void clearAllFailedAttempts() {
        if (!CONFIG.server.passwordLogin.failedLoginAttempts.isEmpty()) {
            CONFIG.server.passwordLogin.failedLoginAttempts.clear();
            saveConfigAsync();
        }
    }

    /** Whether the session's IP is currently inside a lockout cooldown. */
    public static boolean isIpInLockout(final ServerSession session) {
        final var pwl = CONFIG.server.passwordLogin;
        if (!pwl.lockoutEnabled) return false;
        final LoginAttemptRecord record = pwl.failedLoginAttempts.get(getClientIp(session));
        return record != null && record.cooldownUntilEpochMs > System.currentTimeMillis();
    }

    /** Disconnects the session with a localized lockout message. */
    public static void disconnectWithLockoutMessage(final ServerSession session) {
        final LoginAttemptRecord record = CONFIG.server.passwordLogin.failedLoginAttempts.get(getClientIp(session));
        final long remainingMs = record != null ? Math.max(0, record.cooldownUntilEpochMs - System.currentTimeMillis()) : 0;
        final long remainingSec = (remainingMs + 999L) / 1000L;
        // <1.20.2 clients (no configuration phase) have no locale -> show all languages
        if (session.getProtocolVersion().olderThan(ProtocolVersion.v1_20_2)) {
            session.disconnect("Too many failed attempts. Try again in " + formatLockoutDuration(remainingSec) + ".\n"
                + "密碼錯誤次數過多，請在 " + formatLockoutDuration(remainingSec) + " 後重試。\n"
                + "密码错误次数过多，请在 " + formatLockoutDuration(remainingSec) + " 后重试。");
        } else {
            session.disconnect(localize(session,
                "Too many failed attempts. Try again in " + formatLockoutDuration(remainingSec) + ".",
                "密碼錯誤次數過多，請在 " + formatLockoutDuration(remainingSec) + " 後重試。",
                "密码错误次数过多，请在 " + formatLockoutDuration(remainingSec) + " 后重试。"));
        }
    }

    private static String formatLockoutDuration(final long seconds) {
        if (seconds >= 3600) {
            final long hours = seconds / 3600;
            final long minutes = (seconds % 3600) / 60;
            return minutes > 0 ? hours + "h " + minutes + "m" : hours + "h";
        } else if (seconds >= 60) {
            final long minutes = seconds / 60;
            final long secs = seconds % 60;
            return secs > 0 ? minutes + "m " + secs + "s" : minutes + "m";
        }
        return seconds + "s";
    }

    // ================================================================
    // Config modification guard (snapshot / rollback)
    // ================================================================

    /** Serializes the current config so modifications can be detected and rolled back. */
    public static String snapshotConfigJson() {
        return GSON.toJson(CONFIG);
    }

    public static boolean configChangedSince(final String beforeJson) {
        return beforeJson == null || !GSON.toJson(CONFIG).equals(beforeJson);
    }

    /**
     * Restores the live config instance to the given serialized snapshot.
     * List/Map/Set containers are cleared and refilled in place (preserving object identity so
     * existing references held elsewhere stay consistent); scalar fields are copied by value.
     */
    public static void restoreConfig(final String beforeJson) {
        try {
            final Config fresh = GSON.fromJson(beforeJson, Config.class);
            copyConfigFields(fresh, CONFIG);
            saveConfigAsync();
        } catch (final Exception e) {
            SERVER_LOG.error("Failed restoring config after temporary password command", e);
        }
    }

    private static void copyConfigFields(final Object src, final Object dst) throws IllegalAccessException {
        for (final var field : src.getClass().getDeclaredFields()) {
            if (java.lang.reflect.Modifier.isStatic(field.getModifiers())) continue;
            field.setAccessible(true);
            final Object srcValue = field.get(src);
            if (java.lang.reflect.Modifier.isFinal(field.getModifiers())) {
                final Object dstValue = field.get(dst);
                if (srcValue != null && dstValue != null
                    && srcValue.getClass().getName().startsWith("com.zenith.util.config.Config")) {
                    copyConfigFields(srcValue, dstValue); // nested config container
                }
            } else {
                final Object dstValue = field.get(dst);
                if (srcValue instanceof java.util.List && dstValue instanceof java.util.List) {
                    ((java.util.List<Object>) dstValue).clear();
                    ((java.util.List<Object>) dstValue).addAll((java.util.List<Object>) srcValue);
                } else if (srcValue instanceof java.util.Map && dstValue instanceof java.util.Map) {
                    ((java.util.Map<Object, Object>) dstValue).clear();
                    ((java.util.Map<Object, Object>) dstValue).putAll((java.util.Map<Object, Object>) srcValue);
                } else if (srcValue instanceof java.util.Set && dstValue instanceof java.util.Set) {
                    ((java.util.Set<Object>) dstValue).clear();
                    ((java.util.Set<Object>) dstValue).addAll((java.util.Set<Object>) srcValue);
                } else if (srcValue instanceof java.util.Optional && dstValue instanceof java.util.Optional) {
                    // optionals are mostly immutable wrappers; skip to avoid identity issues
                } else {
                    field.set(dst, srcValue);
                }
            }
        }
    }
}