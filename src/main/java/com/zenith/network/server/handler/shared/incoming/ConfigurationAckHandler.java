package com.zenith.network.server.handler.shared.incoming;

import com.zenith.network.codec.PacketHandler;
import com.zenith.network.server.ServerSession;
import org.geysermc.mcprotocollib.protocol.data.ProtocolState;
import org.geysermc.mcprotocollib.protocol.packet.ingame.serverbound.ServerboundConfigurationAcknowledgedPacket;

import static com.zenith.Globals.CONFIG;

public class ConfigurationAckHandler implements PacketHandler<ServerboundConfigurationAcknowledgedPacket, ServerSession> {
    @Override
    public ServerboundConfigurationAcknowledgedPacket apply(final ServerboundConfigurationAcknowledgedPacket packet, final ServerSession session) {
        session.switchInboundState(ProtocolState.CONFIGURATION);
        session.setClientLoaded(false);
        if (com.zenith.feature.passwordlogin.PasswordLoginHelper.passwordLoginActive() && session.isPasswordLoginUpgrading()) {
            // lobby client bouncing back into configuration to join the real game after a successful password login
            session.setPasswordLoginUpgrading(false);
            com.zenith.feature.passwordlogin.PasswordLoginHelper.INSTANCE.sendRealConfiguration(session);
            return null;
        }
        return packet;
    }
}
