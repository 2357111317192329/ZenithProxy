package com.zenith.network.server.handler.shared.incoming;

import com.zenith.network.codec.PacketHandler;
import com.zenith.network.server.ServerSession;
import org.geysermc.mcprotocollib.protocol.packet.ingame.serverbound.ServerboundChatPacket;

/**
 * Swallows plain chat messages while a client is in the pre-1.21.6 "login lobby".
 */
public class SLoginLobbyChatIgnoreHandler implements PacketHandler<ServerboundChatPacket, ServerSession> {
    @Override
    public ServerboundChatPacket apply(final ServerboundChatPacket packet, final ServerSession session) {
        return null;
    }
}