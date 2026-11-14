package com.zenith.network.server.handler.shared.incoming;

import com.zenith.network.UserAuthTask;
import com.zenith.network.codec.PacketHandler;
import com.zenith.network.server.ServerSession;
import com.zenith.util.ChatUtil;
import com.zenith.feature.passwordlogin.PasswordLoginHelper;
import org.geysermc.mcprotocollib.protocol.packet.login.clientbound.ClientboundHelloPacket;
import org.geysermc.mcprotocollib.protocol.packet.login.serverbound.ServerboundHelloPacket;
import org.jspecify.annotations.NonNull;

import static com.zenith.Globals.CONFIG;
import static com.zenith.Globals.EXECUTOR;

public class SHelloHandler implements PacketHandler<ServerboundHelloPacket, ServerSession> {
    @Override
    public ServerboundHelloPacket apply(@NonNull ServerboundHelloPacket packet, @NonNull ServerSession session) {
        if (!ChatUtil.isValidPlayerName(packet.getUsername())) {
            session.disconnect("Invalid username.");
            return null;
        }
        session.setUsername(packet.getUsername());
        session.setLoginProfileUUID(packet.getProfileId());
        if (session.isTransferring())
            // TODO: see how viaversion interacts with this sequence
            //  it seems to be legal for clients to not send a response to the cookie request, at which point we stall
            //  in this login sequence forever
            session.getCookieCache().getPackets(session::sendAsync, session);
        else {
            if (PasswordLoginHelper.passwordLoginActive()) {
                // password login (whitelist disabled): always do a pure-encryption handshake (no Mojang session check)
                // so cracked/offline clients can complete it; the password itself is verified later
                session.sendAsync(new ClientboundHelloPacket(session.getServerId(), session.getKeyPair().getPublic(), session.getChallenge(), false));
            } else if (CONFIG.server.verifyUsers)
                session.sendAsync(new ClientboundHelloPacket(session.getServerId(), session.getKeyPair().getPublic(), session.getChallenge(), true));
            else
                EXECUTOR.execute(new UserAuthTask(session, null));
        }
        return null;
    }
}
