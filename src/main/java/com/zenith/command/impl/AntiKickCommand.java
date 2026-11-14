package com.zenith.command.impl;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.zenith.command.api.Command;
import com.zenith.command.api.CommandCategory;
import com.zenith.command.api.CommandContext;
import com.zenith.command.api.CommandUsage;
import com.zenith.discord.Embed;
import com.zenith.module.impl.AntiKick;

import static com.mojang.brigadier.arguments.IntegerArgumentType.getInteger;
import static com.mojang.brigadier.arguments.IntegerArgumentType.integer;
import static com.zenith.Globals.CONFIG;
import static com.zenith.Globals.MODULE;
import static com.zenith.command.brigadier.ToggleArgumentType.getToggle;
import static com.zenith.command.brigadier.ToggleArgumentType.toggle;

public class AntiKickCommand extends Command {
    @Override
    public CommandUsage commandUsage() {
        return CommandUsage.builder()
            .name("antiKick")
            .category(CommandCategory.MODULE)
            .description("""
            AntiKick kicks players controlling the proxy client if they are inactive for a set amount of time.
            
            Inactivity is defined as not moving, fishing, or swinging - which are what prevents 2b2t from kicking players.
            """)
            .usageLines(
                "on/off",
                "playerInactivityKickMins <minutes>",
                "minWalkDistance <blocks>"
            )
            .build();
    }

    @Override
    public LiteralArgumentBuilder<CommandContext> register() {
        return command("antiKick")
            .then(argument("toggle", toggle()).executes(c -> {
                com.zenith.util.config.ConfigManager.modify(cfg -> cfg.client.extra.antiKick.enabled = getToggle(c, "toggle"));
                MODULE.get(AntiKick.class).syncEnabledFromConfig();
                c.getSource().getEmbed()
                    .title("AntiKick " + toggleStrCaps(CONFIG.client.extra.antiKick.enabled));
                return OK;
            }))
            .then(literal("playerInactivityKickMins")
                      .then(argument("minutes", integer(1, 30)).executes(c -> {
                          com.zenith.util.config.ConfigManager.modify(cfg -> cfg.client.extra.antiKick.playerInactivityKickMins = getInteger(c, "minutes"));
                          c.getSource().getEmbed()
                              .title("Inactivity Time Set!");
                          return OK;
                      })))
            .then(literal("minWalkDistance")
                      .then(argument("blocks", integer(1, 100)).executes(c -> {
                          com.zenith.util.config.ConfigManager.modify(cfg -> cfg.client.extra.antiKick.minWalkDistance = getInteger(c, "blocks"));
                          c.getSource().getEmbed()
                              .title("Min Walk Distance Set!");
                          return OK;
                      })));
    }

    @Override
    public void defaultEmbed(final Embed builder) {
        builder
            .primaryColor()
            .addField("Enabled", toggleStrCaps(CONFIG.client.extra.antiKick.enabled), false)
            .addField("Player Inactivity Minutes", CONFIG.client.extra.antiKick.playerInactivityKickMins, false)
            .addField("Min Walk Distance", CONFIG.client.extra.antiKick.minWalkDistance, false);
    }
}
