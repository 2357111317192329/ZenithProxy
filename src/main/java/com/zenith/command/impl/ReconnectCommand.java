package com.zenith.command.impl;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.zenith.Proxy;
import com.zenith.command.api.Command;
import com.zenith.command.api.CommandCategory;
import com.zenith.command.api.CommandContext;
import com.zenith.command.api.CommandUsage;
import com.zenith.module.impl.AutoReconnect;

import static com.zenith.Globals.EXECUTOR;
import static com.zenith.Globals.MODULE;
import static com.zenith.util.DisconnectMessages.SYSTEM_DISCONNECT;

public class ReconnectCommand extends Command {

    @Override
    public CommandUsage commandUsage() {
        return CommandUsage.builder()
            .name("reconnect")
            .category(CommandCategory.MANAGE)
            .description("""
            Disconnect and reconnects from the destination MC server.
            
            Can be used to perform a reconnect "queue skip" on 2b2t
            """)
            .build();
    }

    @Override
    public LiteralArgumentBuilder<CommandContext> register() {
        return command("reconnect").executes(c -> {
            EXECUTOR.execute(() -> {
                Proxy.getInstance().disconnect(SYSTEM_DISCONNECT);
                MODULE.get(AutoReconnect.class).cancelAutoReconnect();
                MODULE.get(AutoReconnect.class).scheduleAutoReconnect(2);
            });
        });
    }
}
