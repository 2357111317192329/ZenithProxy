package com.zenith.command.impl;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.zenith.Proxy;
import com.zenith.command.api.Command;
import com.zenith.command.api.CommandCategory;
import com.zenith.command.api.CommandContext;
import com.zenith.command.api.CommandUsage;
import com.zenith.module.impl.Requeue;

import static com.zenith.Globals.MODULE;

public class RequeueCommand extends Command {
    @Override
    public CommandUsage commandUsage() {
        return CommandUsage.builder()
            .name("requeue")
            .category(CommandCategory.MODULE)
            .description("""
                 Cancels KeepAlive packets until the client is kicked to 2b2t's queue.
                 
                 This should kick you to the start of the queue, similar to a reconnect queue skip.
                 
                 Can be useful for resetting player state without having to do a full reconnect
                 """)
            .usageLines("")
            .build();
    }

    @Override
    public LiteralArgumentBuilder<CommandContext> register() {
        return command("requeue")
            .executes(c -> {
                if (!Proxy.getInstance().isConnected()) {
                    c.getSource().getEmbed()
                        .title("Not Connected")
                        .errorColor();
                    return;
                }
                MODULE.get(Requeue.class).enable();
                c.getSource().getEmbed()
                    .title("Requeueing...")
                    .primaryColor();
            });
    }
}
