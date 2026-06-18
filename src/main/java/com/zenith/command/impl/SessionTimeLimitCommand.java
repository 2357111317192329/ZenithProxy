package com.zenith.command.impl;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.zenith.command.api.Command;
import com.zenith.command.api.CommandCategory;
import com.zenith.command.api.CommandContext;
import com.zenith.command.api.CommandUsage;
import com.zenith.discord.Embed;
import com.zenith.module.impl.SessionTimeLimit;

import java.util.stream.Collectors;

import static com.mojang.brigadier.arguments.IntegerArgumentType.getInteger;
import static com.mojang.brigadier.arguments.IntegerArgumentType.integer;
import static com.zenith.Globals.CONFIG;
import static com.zenith.Globals.MODULE;
import static com.zenith.command.brigadier.ToggleArgumentType.getToggle;
import static com.zenith.command.brigadier.ToggleArgumentType.toggle;
import static com.zenith.util.math.MathHelper.formatDuration;

public class SessionTimeLimitCommand extends Command {
    @Override
    public CommandUsage commandUsage() {
        return CommandUsage.builder()
            .name("sessionTimeLimit")
            .category(CommandCategory.MODULE)
            .description("""
            Sends an in-game warning before you are kicked for reaching the 2b2t session time limit.
            """)
            .usageLines(
                "on/off",
                "refresh",
                "dynamic2b2tSessionTimeLimit on/off",
                "ingame list",
                "ingame add <minutes>",
                "ingame del <minutes>",
                "ingame clear",
                "discord list",
                "discord add <minutes>",
                "discord add <minutes> mention",
                "discord del <minutes>",
                "discord clear"
            )
            .build();
    }

    @Override
    public LiteralArgumentBuilder<CommandContext> register() {
        return command("sessionTimeLimit")
            .then(argument("toggle", toggle()).executes(c -> {
                CONFIG.client.extra.sessionTimeLimit.enabled = getToggle(c, "toggle");
                MODULE.get(SessionTimeLimit.class).syncEnabledFromConfig();
                c.getSource().getEmbed()
                    .title("Session Time Limit " + toggleStrCaps(CONFIG.client.extra.sessionTimeLimit.enabled));
                return OK;
            }))
            .then(literal("refresh").executes(c -> {
                MODULE.get(SessionTimeLimit.class).refreshNow();
                c.getSource().getEmbed()
                    .title("Session Time Limit Refreshed");
                return OK;
            }))
            .then(literal("dynamic2b2tSessionTimeLimit")
                      .then(argument("toggle", toggle()).executes(c -> {
                          CONFIG.client.extra.sessionTimeLimit.dynamic2b2tSessionTimeLimit = getToggle(c, "toggle");
                          c.getSource().getEmbed()
                              .title("dynamic2b2tSessionTimeLimit " + toggleStrCaps(CONFIG.client.extra.sessionTimeLimit.dynamic2b2tSessionTimeLimit));
                          return OK;
                      })))
            .then(literal("ingame")
                .then(literal("list").executes(c -> {
                    c.getSource().getEmbed()
                        .title("Session Time Limit List");
                }))
                .then(literal("add").then(argument("minutes", integer(1)).executes(c -> {
                    var minutes = getInteger(c, "minutes");
                    CONFIG.client.extra.sessionTimeLimit.ingameNotificationPositions.add(minutes);
                    c.getSource().getEmbed()
                        .title("In Game Time Added");
                })))
                .then(literal("del").then(argument("minutes", integer(1)).executes(c -> {
                    var minutes = getInteger(c, "minutes");
                    CONFIG.client.extra.sessionTimeLimit.ingameNotificationPositions.remove(minutes);
                    c.getSource().getEmbed()
                        .title("In Game Time Removed");
                })))
                .then(literal("clear").executes(c -> {
                    CONFIG.client.extra.sessionTimeLimit.ingameNotificationPositions.clear();
                    c.getSource().getEmbed()
                        .title("In Game Times Cleared");
                    return OK;
                })))
            .then(literal("discord")
                .then(literal("list").executes(c -> {
                    c.getSource().getEmbed()
                        .title("Session Time Limit List");
                }))
                .then(literal("add").then(argument("minutes", integer(1)).executes(c -> {
                    var minutes = getInteger(c, "minutes");
                    CONFIG.client.extra.sessionTimeLimit.discordNotificationPositions.add(minutes);
                    CONFIG.client.extra.sessionTimeLimit.discordMentionPositions.remove(minutes);
                    c.getSource().getEmbed()
                        .title("Discord Time Added");
                })
                    .then(literal("mention").executes(c -> {
                        var minutes = getInteger(c, "minutes");
                        CONFIG.client.extra.sessionTimeLimit.discordNotificationPositions.add(minutes);
                        CONFIG.client.extra.sessionTimeLimit.discordMentionPositions.add(minutes);
                        c.getSource().getEmbed()
                            .title("Discord Time Added");
                    }))))
                .then(literal("del").then(argument("minutes", integer(1)).executes(c -> {
                    var minutes = getInteger(c, "minutes");
                    CONFIG.client.extra.sessionTimeLimit.discordNotificationPositions.remove(minutes);
                    CONFIG.client.extra.sessionTimeLimit.discordMentionPositions.remove(minutes);
                    c.getSource().getEmbed()
                        .title("Discord Time Removed");
                })))
                .then(literal("clear").executes(c -> {
                    CONFIG.client.extra.sessionTimeLimit.discordNotificationPositions.clear();
                    CONFIG.client.extra.sessionTimeLimit.discordMentionPositions.clear();
                    c.getSource().getEmbed()
                        .title("Discord Times Cleared");
                    return OK;
                })));
    }

    @Override
    public void defaultEmbed(Embed embed) {
        embed
            .addField("Session Time Limit", toggleStr(CONFIG.client.extra.sessionTimeLimit.enabled))
            .addField("dynamic2b2tSessionTimeLimit", toggleStr(CONFIG.client.extra.sessionTimeLimit.dynamic2b2tSessionTimeLimit))
            .addField("Limit", formatDuration(MODULE.get(SessionTimeLimit.class).getSessionTimeLimit()))
            .addField("In Game Notifications",
                CONFIG.client.extra.sessionTimeLimit.ingameNotificationPositions.isEmpty()
                    ? "Empty"
                    : CONFIG.client.extra.sessionTimeLimit.ingameNotificationPositions.intStream()
                        .sorted()
                        .mapToObj(String::valueOf)
                        .collect(Collectors.joining("\n")))
            .addField("Discord Notifications",
                CONFIG.client.extra.sessionTimeLimit.discordNotificationPositions.isEmpty()
                    ? "Empty"
                    : CONFIG.client.extra.sessionTimeLimit.discordNotificationPositions.intStream()
                        .sorted()
                        .mapToObj(pos -> pos + (CONFIG.client.extra.sessionTimeLimit.discordMentionPositions.contains(pos) ? " (m)" : ""))
                        .collect(Collectors.joining("\n")))
            .primaryColor();
    }
}
