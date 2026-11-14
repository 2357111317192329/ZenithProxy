package com.zenith.network.server.handler.shared.incoming;

import com.zenith.feature.passwordlogin.PasswordLoginHelper;
import com.zenith.network.codec.PacketHandler;
import com.zenith.network.server.ServerSession;
import org.geysermc.mcprotocollib.protocol.packet.common.serverbound.ServerboundCustomClickActionPacket;

import static com.zenith.Globals.CONFIG;
import static com.zenith.Globals.SERVER_LOG;

/**
 * Handles the client's response to the password login dialog (1.21.6+).
 */
public class SLoginCustomClickActionHandler implements PacketHandler<ServerboundCustomClickActionPacket, ServerSession> {
    @Override
    public ServerboundCustomClickActionPacket apply(final ServerboundCustomClickActionPacket packet, final ServerSession session) {
        if (!com.zenith.feature.passwordlogin.PasswordLoginHelper.passwordLoginActive()) return packet;
        if (session.isPasswordAuthenticated()) return packet;
        final var id = packet.getId();
        if (id == null) return null;
        if (id.asString().equals(PasswordLoginHelper.DIALOG_ACTION_ID)) {
            PasswordLoginHelper.INSTANCE.handleDialogAction(session, packet.getPayload());
        } else if (id.asString().equals(PasswordLoginHelper.DIALOG_CANCEL_ID)) {
            SERVER_LOG.info("{} cancelled the password login dialog", session.getName());
            PasswordLoginHelper.disconnectLocalized(session, "Login cancelled.", "已取消登入。", "已取消登录。");
        }
        return null;
    }
}