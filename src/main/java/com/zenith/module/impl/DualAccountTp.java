package com.zenith.module.impl;

import com.github.rfresh2.EventConsumer;
import com.viaversion.nbt.io.MNBTIO;
import com.viaversion.nbt.tag.CompoundTag;
import com.viaversion.nbt.tag.StringTag;
import com.zenith.Proxy;
import com.zenith.cache.data.inventory.Container;
import com.zenith.event.chat.WhisperChatEvent;
import com.zenith.event.client.ClientDisconnectEvent;
import com.zenith.event.client.ClientOnlineEvent;
import com.zenith.event.client.ClientTickEvent;
import com.zenith.event.module.PlayerHealthChangedEvent;
import com.zenith.event.module.ServerPlayerInVisualRangeEvent;
import com.zenith.event.module.TotemPopEvent;
import com.zenith.event.module.WeatherChangeEvent;
import com.zenith.feature.player.ClickResult;
import com.zenith.feature.player.ClickTarget;
import com.zenith.feature.player.Input;
import com.zenith.feature.player.InputRequest;
import com.zenith.feature.player.RotationHelper;
import com.zenith.feature.player.World;
import com.zenith.feature.player.raycast.RaycastHelper;
import com.zenith.mc.block.BlockPos;
import com.zenith.mc.block.BlockTags;
import com.zenith.mc.block.properties.api.BlockStateProperties;
import com.zenith.mc.dimension.DimensionRegistry;
import com.zenith.mc.item.ItemRegistry;
import com.zenith.module.api.Module;
import com.zenith.util.ChatUtil;
import com.zenith.util.ComponentSerializer;
import com.zenith.util.config.Config.Client.Extra.DualAccountTp.Mode;
import com.zenith.util.config.Config.Client.Extra.DualAccountTp.MovementMode;
import com.zenith.util.config.Config.Client.Extra.DualAccountTp.TargetState;
import com.zenith.util.math.MathHelper;
import org.cloudburstmc.math.vector.Vector2f;
import org.geysermc.mcprotocollib.protocol.data.game.PlayerListEntry;
import org.geysermc.mcprotocollib.protocol.data.game.entity.player.GameMode;
import org.geysermc.mcprotocollib.protocol.data.game.entity.player.Hand;
import org.geysermc.mcprotocollib.protocol.data.game.item.ItemStack;
import org.geysermc.mcprotocollib.protocol.data.game.level.block.BlockEntityInfo;
import org.geysermc.mcprotocollib.protocol.data.game.level.block.BlockEntityType;
import org.jspecify.annotations.Nullable;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

import static com.github.rfresh2.EventConsumer.of;
import static com.zenith.Globals.*;

/**
 * Dual-account pearl teleport helper.
 *
 * <p>CLIENT mode sends a whisper containing a shared secret when an emergency trigger fires.
 *
 * <p>SERVER mode watches for those whispers and pulls the trapdoor belonging to the requesting
 * player. Chambers are discovered by reading signs around the bot: the block below a sign has an
 * adjacent trapdoor, and the sign text is the player name.
 */
public class DualAccountTp extends Module {
    private static final int DEFAULT_PRIORITY = 7500;

    private volatile Instant lastClientTrigger = Instant.EPOCH;
    private volatile List<ChamberInfo> chambersCache = List.of();
    private volatile long chambersBuiltAtMs = 0L;
    private final ConcurrentHashMap<String, Instant> lastServerRequest = new ConcurrentHashMap<>();

    public DualAccountTp() {
        super();
    }

    @Override
    public List<EventConsumer<?>> registerEvents() {
        return List.of(
            of(ClientTickEvent.class, this::handleClientTick),
            of(ClientOnlineEvent.class, this::handleOnline),
            of(ClientDisconnectEvent.class, this::handleDisconnect),
            of(PlayerHealthChangedEvent.class, this::handleHealth),
            of(WeatherChangeEvent.class, this::handleWeather),
            of(ServerPlayerInVisualRangeEvent.class, this::handleVisualRange),
            of(TotemPopEvent.class, this::handleTotemPop),
            of(WhisperChatEvent.class, this::handleWhisper)
        );
    }

    @Override
    public boolean enabledSetting() {
        return CONFIG.client.extra.dualAccountTp.enabled;
    }

    @Override
    public void onEnable() {
        resetState();
        var cfg = CONFIG.client.extra.dualAccountTp;
        if (cfg.mode == Mode.CLIENT) {
            if (cfg.peerName.isBlank()) warn("peerName is not set, TP requests cannot be sent");
            if (cfg.requireKey && cfg.secretKey.isBlank()) warn("secretKey is not set, TP requests will be rejected");
        }
        info("Enabled in {} mode", cfg.mode);
    }

    @Override
    public void onDisable() {
        resetState();
    }

    public int getPriority() {
        return Objects.requireNonNullElse(CONFIG.client.extra.dualAccountTp.priority, DEFAULT_PRIORITY);
    }

    private void resetState() {
        lastClientTrigger = Instant.EPOCH;
        chambersCache = List.of();
        chambersBuiltAtMs = 0L;
        lastServerRequest.clear();
    }

    private void handleOnline(ClientOnlineEvent event) {
        resetState();
    }

    private void handleDisconnect(ClientDisconnectEvent event) {
        resetState();
    }

    // ═══════════════════════════════════════════════════════════
    //  CLIENT MODE
    // ═══════════════════════════════════════════════════════════

    private void handleClientTick(ClientTickEvent event) {
        var cfg = CONFIG.client.extra.dualAccountTp;
        if (cfg.mode != Mode.CLIENT || !cfg.lowYTrigger) return;
        if (cfg.lowYEndOnly && World.getCurrentDimension() != DimensionRegistry.THE_END.get()) return;

        var playerCache = CACHE.getPlayerCache();
        if (playerCache.getX() == 0.0d && playerCache.getY() == 0.0d && playerCache.getZ() == 0.0d) return;
        if (playerCache.getY() <= cfg.lowYThreshold) {
            requestTp("low Y " + playerCache.getY());
        }
    }

    private void handleHealth(PlayerHealthChangedEvent event) {
        var cfg = CONFIG.client.extra.dualAccountTp;
        if (cfg.mode != Mode.CLIENT || !cfg.healthTrigger) return;
        if (event.newHealth() > 0 && event.newHealth() <= cfg.healthThreshold) {
            requestTp("health " + event.newHealth());
        }
    }

    private void handleWeather(WeatherChangeEvent event) {
        var cfg = CONFIG.client.extra.dualAccountTp;
        if (cfg.mode != Mode.CLIENT || !cfg.thunderTrigger) return;
        if (CACHE.getChunkCache().isRaining() && CACHE.getChunkCache().getThunderStrength() > 0.0f) {
            requestTp("thunder");
        }
    }

    private void handleVisualRange(ServerPlayerInVisualRangeEvent event) {
        var cfg = CONFIG.client.extra.dualAccountTp;
        if (cfg.mode != Mode.CLIENT || !cfg.onUnknownPlayerInVisualRange) return;
        if (cfg.disablein3C3Ulobby && CACHE.getPlayerCache().getGameMode() != GameMode.SURVIVAL) return;

        if (cfg.enemyListMode) {
            if (!PLAYER_LISTS.getEnemyList().contains(event.playerEntry())) return;
        } else {
            if (PLAYER_LISTS.getFriendsList().contains(event.playerEntry())
                || PLAYER_LISTS.getWhitelist().contains(event.playerEntry())
                || PLAYER_LISTS.getSpectatorWhitelist().contains(event.playerEntry())) return;
        }
        requestTp("player in visual range: " + event.playerEntry().getName());
    }

    private void handleTotemPop(TotemPopEvent event) {
        var cfg = CONFIG.client.extra.dualAccountTp;
        if (cfg.mode != Mode.CLIENT || !cfg.onTotemPop) return;
        if (event.entityId() != CACHE.getPlayerCache().getEntityId()) return;

        // give the inventory a moment to update
        EXECUTOR.schedule(() -> {
            int remaining = countTotems();
            if (remaining < cfg.minTotemsRemaining) {
                requestTp("totem pop - " + remaining + " remaining");
            }
        }, 1, TimeUnit.SECONDS);
    }

    private void requestTp(String reason) {
        requestTp(reason, false);
    }

    private void requestTp(String reason, boolean force) {
        var cfg = CONFIG.client.extra.dualAccountTp;
        if (!force && !isEnabled()) return;
        if (cfg.mode != Mode.CLIENT || !online() || onClientCooldown()) return;
        if (!cfg.whilePlayerConnected && Proxy.getInstance().hasActivePlayer()) return;
        if (cfg.peerName.isBlank()) return;
        if (cfg.requireKey && cfg.secretKey.isBlank()) return;

        lastClientTrigger = Instant.now();
        var message = cfg.triggerPrefix + (cfg.requireKey ? " " + cfg.secretKey : "");
        sendClientPacketAsync(ChatUtil.getWhisperChatPacket(cfg.peerName, message));
        info("Sent TP request to {} ({})", cfg.peerName, reason);
    }

    private boolean onClientCooldown() {
        return Instant.now().isBefore(
            lastClientTrigger.plusSeconds(CONFIG.client.extra.dualAccountTp.cooldownSeconds));
    }

    // ═══════════════════════════════════════════════════════════
    //  SERVER MODE
    // ═══════════════════════════════════════════════════════════

    private void handleWhisper(WhisperChatEvent event) {
        var cfg = CONFIG.client.extra.dualAccountTp;
        if (event.outgoing()) return;

        if (cfg.mode == Mode.CLIENT) {
            if (!cfg.peerName.isBlank()
                && event.sender().getName().equalsIgnoreCase(cfg.peerName)
                && event.message().startsWith(cfg.ackPrefix)) {
                info("TP request acknowledged: {}", event.message());
            }
            return;
        }

        if (cfg.mode != Mode.SERVER || !isValidRequest(event.message())) return;

        var playerName = event.sender().getName();
        var sender = event.sender();
        var client = Proxy.getInstance().getClient();
        if (client != null) {
            // WhisperChatEvent is posted async; world/cache access must run on the client event loop
            client.executeInEventLoop(() -> executeRequest(playerName, sender));
        }
    }

    private boolean isValidRequest(String message) {
        var cfg = CONFIG.client.extra.dualAccountTp;
        var trimmed = message.trim();
        if (!trimmed.startsWith(cfg.triggerPrefix)) return false;
        if (trimmed.length() > cfg.triggerPrefix.length()
            && !Character.isWhitespace(trimmed.charAt(cfg.triggerPrefix.length()))) return false;
        if (!cfg.requireKey) return true;
        if (cfg.secretKey.isEmpty()) return false;

        var key = trimmed.substring(cfg.triggerPrefix.length()).trim();
        return MessageDigest.isEqual(
            key.getBytes(StandardCharsets.UTF_8),
            cfg.secretKey.getBytes(StandardCharsets.UTF_8));
    }

    private void executeRequest(String playerName, @Nullable PlayerListEntry sender) {
        executeRequest(playerName, sender, false);
    }

    private void executeRequest(String playerName, @Nullable PlayerListEntry sender, boolean force) {
        var cfg = CONFIG.client.extra.dualAccountTp;
        if (!force && !isEnabled()) return;
        if (!online()) {
            debug("Ignoring request from {}: not online", playerName);
            return;
        }
        if (Proxy.getInstance().hasActivePlayer()) {
            warn("Cannot pull trapdoor while a player is controlling this account");
            replyFailure(sender, "account-controlled");
            return;
        }

        var now = Instant.now();
        var last = lastServerRequest.get(playerName);
        if (last != null && now.isBefore(last.plusSeconds(cfg.cooldownSeconds))) {
            debug("Ignoring duplicate request from {}", playerName);
            return;
        }
        lastServerRequest.put(playerName, now);

        var chambers = chambers().stream()
            .filter(c -> c.playerName().equalsIgnoreCase(playerName))
            .toList();
        if (chambers.isEmpty()) {
            info("No sign/chamber found for {}", playerName);
            replyFailure(sender, "no-chamber");
            return;
        }

        var chamber = chambers.stream()
            .min(Comparator.comparingDouble(c -> distanceSqToBot(c.trapdoorPos())))
            .orElseThrow();
        var trapdoor = chamber.trapdoorPos();

        if (!shouldInteract(trapdoor)) {
            info("Trapdoor for {} is already in the requested state: {}", playerName, trapdoor);
            replyFailure(sender, "wrong-state");
            return;
        }

        if (cfg.movementMode == MovementMode.WALK) {
            BARITONE.rightClickBlock(trapdoor.x(), trapdoor.y(), trapdoor.z())
                .addExecutedListener(f -> {
                    if (f.getNow()) {
                        info("Pulled trapdoor for {} at {}", playerName, trapdoor);
                        replySuccess(sender);
                    } else {
                        warn("Failed to walk to/pull trapdoor for {} at {}", playerName, trapdoor);
                        replyFailure(sender, "walk-failed");
                    }
                });
            info("Walking to trapdoor for {} at {}", playerName, trapdoor);
            return;
        }

        if (!interactDirect(trapdoor)) {
            warn("Trapdoor for {} at {} is out of reach or not visible", playerName, trapdoor);
            replyFailure(sender, "out-of-reach");
            return;
        }

        info("Pulling trapdoor for {} at {}", playerName, trapdoor);
        replySuccess(sender);
    }

    private boolean shouldInteract(BlockPos pos) {
        var cfg = CONFIG.client.extra.dualAccountTp;
        if (cfg.targetState == TargetState.TOGGLE) return true;

        Boolean open = World.getBlockStateProperty(World.getBlockStateId(pos), BlockStateProperties.OPEN);
        if (open == null) return false;
        return cfg.targetState == TargetState.CLOSE ? open : !open;
    }

    private boolean interactDirect(BlockPos pos) {
        var center = World.blockInteractionCenter(pos.x(), pos.y(), pos.z());
        Vector2f rotation = RotationHelper.rotationTo(center.x(), center.y(), center.z());

        var ray = RaycastHelper.playerEyeRaycastThroughToBlockTarget(
            pos.x(), pos.y(), pos.z(),
            rotation.getX(), rotation.getY(), BOT.getBlockReachDistance());
        if (!ray.hit() || ray.x() != pos.x() || ray.y() != pos.y() || ray.z() != pos.z()) {
            return false;
        }

        var future = INPUTS.submit(InputRequest.builder()
                .owner(this)
                .input(Input.builder()
                    .rightClick(true)
                    .hand(Hand.MAIN_HAND)
                    .clickTarget(new ClickTarget.BlockPosition(pos.x(), pos.y(), pos.z()))
                    .clickRequiresRotation(true)
                    .build())
                .yaw(rotation.getX())
                .pitch(rotation.getY())
                .priority(getPriority())
                .build());
        if (future.isDone() && !future.getNow()) {
            warn("Trapdoor click at {} was rejected", pos);
            return false;
        }
        future.addInputExecutedListener(f -> {
            if (f.getNow()
                && f.getClickResult() instanceof ClickResult.RightClickResult result
                && result.getType() == ClickResult.RightClickResult.RightClickType.USE_ITEM_ON_BLOCK) {
                debug("Right clicked trapdoor at {}", pos);
            } else {
                warn("Trapdoor click at {} was not executed", pos);
            }
        });
        return true;
    }

    // ═══════════════════════════════════════════════════════════
    //  SIGN / CHAMBER DISCOVERY
    // ═══════════════════════════════════════════════════════════

    private synchronized List<ChamberInfo> chambers() {
        var cfg = CONFIG.client.extra.dualAccountTp;
        long now = System.currentTimeMillis();
        if (chambersBuiltAtMs != 0L && now - chambersBuiltAtMs < cfg.chamberIndexTtlSeconds * 1000L) {
            return chambersCache;
        }
        chambersCache = List.copyOf(scanChambers());
        chambersBuiltAtMs = now;
        return chambersCache;
    }

    private List<ChamberInfo> scanChambers() {
        var cfg = CONFIG.client.extra.dualAccountTp;
        var playerCache = CACHE.getPlayerCache();
        int centerChunkX = MathHelper.floorI(playerCache.getX()) >> 4;
        int centerChunkZ = MathHelper.floorI(playerCache.getZ()) >> 4;
        int radius = cfg.chamberScanChunkRadius;
        var result = new ArrayList<ChamberInfo>();

        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                var chunk = CACHE.getChunkCache().get(centerChunkX + dx, centerChunkZ + dz);
                if (chunk == null) continue;

                List<BlockEntityInfo> blockEntities;
                synchronized (chunk.getBlockEntities()) {
                    blockEntities = new ArrayList<>(chunk.getBlockEntities());
                }

                for (var blockEntity : blockEntities) {
                    int signX = (chunk.getX() << 4) + blockEntity.getX();
                    int signY = blockEntity.getY();
                    int signZ = (chunk.getZ() << 4) + blockEntity.getZ();

                    if (blockEntity.getNbt() == null) continue;
                    boolean signType = blockEntity.getType() == BlockEntityType.SIGN
                        || blockEntity.getType() == BlockEntityType.HANGING_SIGN;
                    if (!signType && !World.getBlock(signX, signY, signZ).name().contains("sign")) continue;

                    CompoundTag tag;
                    try {
                        tag = (CompoundTag) MNBTIO.read(blockEntity.getNbt());
                    } catch (Throwable t) {
                        debug("Failed to read block entity NBT at [{}, {}, {}]: {}",
                              signX, signY, signZ, t.getMessage());
                        continue;
                    }

                    var lines = readSignLines(tag);
                    if (lines.isEmpty()) continue;

                    var trapdoor = findAdjacentTrapdoor(signX, signY, signZ);
                    if (trapdoor == null) continue;

                    for (var line : lines) {
                        var playerName = line == null ? "" : line.trim();
                        if (playerName.isEmpty()) continue;
                        result.add(new ChamberInfo(
                            playerName,
                            signX, signY, signZ,
                            trapdoor.x(), trapdoor.y(), trapdoor.z()));
                    }
                }
            }
        }
        return result;
    }

    private List<String> readSignLines(CompoundTag tag) {
        var lines = new ArrayList<String>(4);
        try {
            if (tag.contains("front_text")) appendSignLines(tag.getCompoundTag("front_text"), lines);
            if (tag.contains("back_text")) appendSignLines(tag.getCompoundTag("back_text"), lines);
            if (lines.isEmpty()) {
                for (int i = 1; i <= 4; i++) {
                    var key = "Text" + i;
                    if (tag.contains(key)) lines.add(toPlain(tag.getString(key)));
                }
            }
        } catch (Throwable t) {
            debug("Failed to parse sign text: {}", t.getMessage());
        }
        return lines;
    }

    private void appendSignLines(CompoundTag text, List<String> lines) {
        if (text == null || !text.contains("messages")) return;
        var messages = text.getListTag("messages", StringTag.class);
        if (messages == null) return;
        messages.stream().map(StringTag::getValue).forEach(value -> lines.add(toPlain(value)));
    }

    private String toPlain(String raw) {
        if (raw == null) return "";
        try {
            return ComponentSerializer.serializePlain(ComponentSerializer.deserialize(raw)).trim();
        } catch (Throwable t) {
            return raw.replaceAll("§.", "").trim();
        }
    }

    private @Nullable BlockPos findAdjacentTrapdoor(int signX, int signY, int signZ) {
        // The sign stands on top of a block. The player's trapdoor is adjacent to that block.
        var base = new BlockPos(signX, signY - 1, signZ);
        var candidates = new ArrayList<BlockPos>(6);
        addIfTrapdoor(candidates, base.add(1, 0, 0));
        addIfTrapdoor(candidates, base.add(-1, 0, 0));
        addIfTrapdoor(candidates, base.add(0, 0, 1));
        addIfTrapdoor(candidates, base.add(0, 0, -1));
        addIfTrapdoor(candidates, base.add(0, 1, 0));
        addIfTrapdoor(candidates, base.add(0, -1, 0));
        // fallback: the sign is directly attached to the trapdoor
        if (candidates.isEmpty() && isTrapdoor(base)) candidates.add(base);

        if (candidates.isEmpty()) return null;
        if (candidates.size() == 1) return candidates.get(0);

        BlockPos best = candidates.get(0);
        double bestDistance = distanceSqToBot(best);
        for (int i = 1; i < candidates.size(); i++) {
            double distance = distanceSqToBot(candidates.get(i));
            if (distance < bestDistance) {
                bestDistance = distance;
                best = candidates.get(i);
            }
        }
        debug("Multiple trapdoors next to sign at [{}, {}, {}], using nearest: {}",
              signX, signY, signZ, best);
        return best;
    }

    private void addIfTrapdoor(List<BlockPos> candidates, BlockPos pos) {
        if (isTrapdoor(pos)) candidates.add(pos);
    }

    private boolean isTrapdoor(BlockPos pos) {
        return World.isChunkLoadedBlockPos(pos.x(), pos.z())
            && World.getBlock(pos).blockTags().contains(BlockTags.TRAPDOORS);
    }

    private double distanceSqToBot(BlockPos pos) {
        var playerCache = CACHE.getPlayerCache();
        return MathHelper.distanceSq3d(
            playerCache.getX(), playerCache.getY(), playerCache.getZ(),
            pos.x() + 0.5, pos.y() + 0.5, pos.z() + 0.5);
    }

    // ═══════════════════════════════════════════════════════════
    //  COMMAND SUPPORT
    // ═══════════════════════════════════════════════════════════

    public void triggerNow() {
        var cfg = CONFIG.client.extra.dualAccountTp;
        if (cfg.mode == Mode.CLIENT) {
            requestTp("manual command", true);
        } else {
            info("Server mode: use dualAccountTp test <playerName>");
        }
    }

    public void testChamber(String playerName) {
        var client = Proxy.getInstance().getClient();
        if (client == null) return;
        lastServerRequest.remove(playerName); // allow repeated tests
        client.executeInEventLoop(() -> executeRequest(playerName, null, true));
    }

    public List<String> chamberSummary() {
        var chambers = chambers();
        var result = new ArrayList<String>(chambers.size());
        for (var chamber : chambers) {
            if (CONFIG.discord.reportCoords) {
                result.add(chamber.playerName()
                               + " | sign [" + chamber.signX() + ", " + chamber.signY() + ", " + chamber.signZ()
                               + "] -> trapdoor [" + chamber.trapdoorX() + ", " + chamber.trapdoorY() + ", " + chamber.trapdoorZ() + "]");
            } else {
                result.add(chamber.playerName());
            }
        }
        return result;
    }

    public void resetChambers() {
        chambersCache = List.of();
        chambersBuiltAtMs = 0L;
        lastServerRequest.clear();
    }

    // ═══════════════════════════════════════════════════════════
    //  HELPERS
    // ═══════════════════════════════════════════════════════════

    private boolean online() {
        var proxy = Proxy.getInstance();
        return proxy.isConnected() && !proxy.isInQueue();
    }

    private int countTotems() {
        int count = 0;
        for (ItemStack item : CACHE.getPlayerCache().getPlayerInventory()) {
            if (item != Container.EMPTY_STACK && item.getId() == ItemRegistry.TOTEM_OF_UNDYING.id()) {
                count++;
            }
        }
        return count;
    }

    private void replySuccess(@Nullable PlayerListEntry sender) {
        var cfg = CONFIG.client.extra.dualAccountTp;
        if (!cfg.sendAck || sender == null) return;
        maybeReply(sender.getName(), cfg.ackPrefix);
    }

    private void replyFailure(@Nullable PlayerListEntry sender, String reason) {
        var cfg = CONFIG.client.extra.dualAccountTp;
        if (!cfg.replyOnFailure || sender == null) return;
        maybeReply(sender.getName(), cfg.failPrefix + " " + reason);
    }

    private void maybeReply(String playerName, String message) {
        if (playerName == null || playerName.isBlank()) return;
        sendClientPacketAsync(ChatUtil.getWhisperChatPacket(playerName, message));
    }

    public record ChamberInfo(
        String playerName,
        int signX,
        int signY,
        int signZ,
        int trapdoorX,
        int trapdoorY,
        int trapdoorZ
    ) {
        public BlockPos trapdoorPos() {
            return new BlockPos(trapdoorX, trapdoorY, trapdoorZ);
        }
    }
}
