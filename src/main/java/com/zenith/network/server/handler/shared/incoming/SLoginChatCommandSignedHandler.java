package com.zenith.network.server.handler.shared.incoming;

import com.zenith.feature.passwordlogin.PasswordLoginHelper;
import com.zenith.network.codec.PacketHandler;
import com.zenith.network.server.ServerSession;
import org.geysermc.mcprotocollib.protocol.packet.ingame.serverbound.ServerboundChatCommandSignedPacket;

/**
 * Same as {@link SLoginChatCommandHandler} but for signed chat commands.
 */
public class SLoginChatCommandSignedHandler implements PacketHandler<ServerboundChatCommandSignedPacket, ServerSession> {
    @Override
    public ServerboundChatCommandSignedPacket apply(final ServerboundChatCommandSignedPacket packet, final ServerSession session) {
        final String command = packet.getCommand();
        if (command != null && (command.equalsIgnoreCase("login") || command.toLowerCase().startsWith("login "))) {
            final String[] parts = command.split(" ", 2);
            final String password = parts.length > 1 ? parts[1] : "";
            PasswordLoginHelper.INSTANCE.handlePassword(session, password);
            return null;
        }
        return null;
    }
}