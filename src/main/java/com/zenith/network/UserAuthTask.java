package com.zenith.network;

import com.zenith.feature.api.sessionserver.SessionServerApi;
import com.zenith.network.server.ServerSession;
import org.geysermc.mcprotocollib.auth.GameProfile;
import org.geysermc.mcprotocollib.protocol.packet.login.clientbound.ClientboundLoginCompressionPacket;
import org.geysermc.mcprotocollib.protocol.packet.login.clientbound.ClientboundLoginFinishedPacket;
import com.zenith.util.config.Config;

import javax.crypto.SecretKey;
import java.util.Optional;
import java.util.UUID;

import static com.zenith.Globals.CONFIG;

public class UserAuthTask implements Runnable {
    private final ServerSession session;
    private final SecretKey key;

    public UserAuthTask(ServerSession session, SecretKey key) {
        this.key = key;
        this.session = session;
    }

    @Override
    public void run() {
        GameProfile profile;
        String expectedUsername = CONFIG.authentication.username;
        if (CONFIG.authentication.accountType == Config.Authentication.AccountType.OFFLINE) {
            if (!session.getUsername().equals(expectedUsername)) {
                this.session.disconnect("Failed to verify username.");
                System.out.println("[ZenithProxy] 登入失敗：名稱不匹配！預期: " + expectedUsername + "，收到: " + session.getUsername());
                return;
            }

            UUID offlineUUID = Config.Authentication.generateOfflineUUID(session.getUsername());
            String displayName = session.getUsername();

            profile = new GameProfile(offlineUUID, displayName);

            System.out.println("[ZenithProxy] 離線模式登入成功 - Username: " + displayName);
        }  
        else if (this.key != null) {
            final Optional<GameProfile> response = SessionServerApi.INSTANCE.hasJoined(
                session.getUsername(),
                SessionServerApi.INSTANCE.getSharedSecret(
                    session.getServerId(),
                    session.getKeyPair().getPublic(),
                    this.key));
            if (response.isEmpty()) {
                this.session.disconnect("Failed to verify username.");
                return;
            }
            profile = response.get();
        } 
        else {
            if (CONFIG.server.verifyUsers) {
                this.session.disconnect("No encryption key!");
                return;
            }

            final var uuid = session.getLoginProfileUUID() == session.getDefaultUUID()
                ? UUID.nameUUIDFromBytes(("OfflinePlayer:" + session.getUsername()).getBytes())
                : session.getLoginProfileUUID();

            profile = new GameProfile(uuid, session.getUsername());
        }
        session.getProfileCache().setProfile(profile);

        final var threshold = CONFIG.server.compressionThreshold;
        if (threshold >= 0) {
            this.session.send(new ClientboundLoginCompressionPacket(threshold));
        } else {
            session.setCompressionThreshold(threshold, CONFIG.server.compressionLevel, true);
            session.send(new ClientboundLoginFinishedPacket(profile));
        }
    }
}
