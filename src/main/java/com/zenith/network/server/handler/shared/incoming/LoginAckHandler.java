package com.zenith.network.server.handler.shared.incoming;

import com.zenith.Proxy;
import com.zenith.event.player.PlayerConfigurationEvent;
import com.zenith.feature.passwordlogin.PasswordLoginHelper;
import com.zenith.network.codec.PacketHandler;
import com.zenith.network.server.ServerSession;
import net.kyori.adventure.key.Key;
import org.geysermc.mcprotocollib.protocol.data.ProtocolState;
import org.geysermc.mcprotocollib.protocol.packet.common.clientbound.ClientboundCustomPayloadPacket;
import org.geysermc.mcprotocollib.protocol.packet.configuration.clientbound.ClientboundFinishConfigurationPacket;
import org.geysermc.mcprotocollib.protocol.packet.login.serverbound.ServerboundLoginAcknowledgedPacket;

import static com.zenith.Globals.CACHE;
import static com.zenith.Globals.CONFIG;
import static com.zenith.Globals.EVENT_BUS;

public class LoginAckHandler implements PacketHandler<ServerboundLoginAcknowledgedPacket, ServerSession> {
    @Override
    public ServerboundLoginAcknowledgedPacket apply(final ServerboundLoginAcknowledgedPacket packet, final ServerSession session) {
        session.switchInboundState(ProtocolState.CONFIGURATION);
        session.setClientLoaded(false);
        final boolean passwordLoginPending = com.zenith.feature.passwordlogin.PasswordLoginHelper.passwordLoginActive() && !session.isPasswordAuthenticated();
        // when password login is pending, our outbound state is still LOGIN (the LoginFinished for the
        // client's own profile was passed through without claiming the controller). the outbound state
        // is switched to CONFIGURATION here, once the client has acknowledged the login phase.
        if (!passwordLoginPending && session.getPacketProtocol().getOutboundState() != ProtocolState.CONFIGURATION) {
            session.disconnect("Attempted to enter configuration too early");
            return null;
        }
        if (!passwordLoginPending && !session.isWhitelistChecked()) {
            session.disconnect("Attempted to enter configuration too early");
            return null;
        }
        if (!passwordLoginPending) {
            // todo: handle this more gracefully, connect and wait until we have configuration set (assuming session is auth'd)
            if (!Proxy.getInstance().isConnected()) {
                session.disconnect("Proxy is not connected to a server.");
                return null;
            }
        }
        if (passwordLoginPending) {
            session.switchOutboundState(ProtocolState.CONFIGURATION);
            PasswordLoginHelper.INSTANCE.onEnteredConfiguration(session);
            return null;
        }
        EVENT_BUS.post(new PlayerConfigurationEvent.Entered(session));
        CACHE.getRegistriesCache().getRegistryPackets(session::sendAsync, session);
        CACHE.getConfigurationCache().getConfigurationPackets(session::sendAsync, session);
        session.sendAsync(new ClientboundCustomPayloadPacket(Key.key("minecraft:brand"), CACHE.getChunkCache().getServerBrand()));
        EVENT_BUS.post(new PlayerConfigurationEvent.Exiting(session));
        session.sendAsync(new ClientboundFinishConfigurationPacket());
        return null;
    }
}
