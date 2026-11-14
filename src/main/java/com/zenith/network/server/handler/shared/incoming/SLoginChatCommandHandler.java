package com.zenith.network.server.handler.shared.incoming;

import com.zenith.feature.passwordlogin.PasswordLoginHelper;
import com.zenith.network.codec.PacketHandler;
import com.zenith.network.server.ServerSession;
import org.geysermc.mcprotocollib.protocol.packet.ingame.serverbound.ServerboundChatCommandPacket;

/**
 * Intercepts chat commands while a client is sitting in the pre-1.21.6 "login lobby".
 * Only {@code /login <password>} is honored; everything else is swallowed (never forwarded to the real server).
 */
public class SLoginChatCommandHandler implements PacketHandler<ServerboundChatCommandPacket, ServerSession> {
    @Override
    public ServerboundChatCommandPacket apply(final ServerboundChatCommandPacket packet, final ServerSession session) {
        final String command = packet.getCommand();
        if (command != null && (command.equalsIgnoreCase("login") || command.toLowerCase().startsWith("login "))) {
            final String[] parts = command.split(" ", 2);
            final String password = parts.length > 1 ? parts[1] : "";
            PasswordLoginHelper.INSTANCE.handlePassword(session, password);
            return null;
        }
        // any other command while in the lobby: swallow
        return null;
    }
}