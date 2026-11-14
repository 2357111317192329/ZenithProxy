package com.zenith.command.impl;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.zenith.command.api.Command;
import com.zenith.command.api.CommandCategory;
import com.zenith.command.api.CommandContext;
import com.zenith.command.api.CommandUsage;
import com.zenith.discord.Embed;
import com.zenith.module.impl.ChatHistory;

import static com.mojang.brigadier.arguments.IntegerArgumentType.getInteger;
import static com.mojang.brigadier.arguments.IntegerArgumentType.integer;
import static com.zenith.Globals.CONFIG;
import static com.zenith.Globals.MODULE;
import static com.zenith.command.brigadier.ToggleArgumentType.getToggle;
import static com.zenith.command.brigadier.ToggleArgumentType.toggle;

public class ChatHistoryCommand extends Command {
    @Override
    public CommandUsage commandUsage() {
        return CommandUsage.builder()
            .name("chatHistory")
            .category(CommandCategory.MODULE)
            .description("""
            Caches and sends recent chat history to players and spectators who connect to the proxy.
            Includes whispers, chat, and system messages.
            """)
            .usageLines(
                "on/off",
                "seconds <seconds>",
                "maxCount <maxCount>",
                "spectators on/off"
            )
            .build();
    }

    @Override
    public LiteralArgumentBuilder<CommandContext> register() {
        return command("chatHistory")
            .then(argument("toggle", toggle()).executes(c -> {
                com.zenith.util.config.ConfigManager.modify(cfg -> cfg.server.extra.chatHistory.enable = getToggle(c, "toggle"));
                MODULE.get(ChatHistory.class).syncEnabledFromConfig();
                c.getSource().getEmbed()
                    .title("Chat History " + toggleStrCaps(CONFIG.server.extra.chatHistory.enable));
                return OK;
            }))
            .then(literal("seconds")
                      .then(argument("seconds", integer(5, 300)).executes(c -> {
                          com.zenith.util.config.ConfigManager.modify(cfg -> cfg.server.extra.chatHistory.seconds = getInteger(c, "seconds"));
                          c.getSource().getEmbed()
                              .title("Chat History Seconds Set");
                          return OK;
                      })))
            .then(literal("maxCount")
                      .then(argument("maxCount", integer(1, 50)).executes(c -> {
                          com.zenith.util.config.ConfigManager.modify(cfg -> cfg.server.extra.chatHistory.maxCount = getInteger(c, "maxCount"));
                          MODULE.get(ChatHistory.class).syncMaxCountFromConfig();
                          c.getSource().getEmbed()
                              .title("Chat History Max Count Set");
                          return OK;
                      })))
            .then(literal("spectators")
                      .then(argument("toggle", toggle()).executes(c -> {
                          com.zenith.util.config.ConfigManager.modify(cfg -> cfg.server.extra.chatHistory.spectators = getToggle(c, "toggle"));
                          c.getSource().getEmbed()
                              .title("Chat History Spectators " + toggleStrCaps(CONFIG.server.extra.chatHistory.spectators));
                          return OK;
                      })));
    }

    @Override
    public void defaultEmbed(final Embed embed) {
        embed
            .addField("Chat History", toggleStr(CONFIG.server.extra.chatHistory.enable), false)
            .addField("Seconds", CONFIG.server.extra.chatHistory.seconds, false)
            .addField("Max Count", CONFIG.server.extra.chatHistory.maxCount, false)
            .addField("Spectators", toggleStr(CONFIG.server.extra.chatHistory.spectators), false)
            .primaryColor();
    }
}
