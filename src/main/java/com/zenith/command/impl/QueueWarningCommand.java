package com.zenith.command.impl;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.zenith.command.api.Command;
import com.zenith.command.api.CommandCategory;
import com.zenith.command.api.CommandContext;
import com.zenith.command.api.CommandUsage;
import com.zenith.discord.Embed;
import com.zenith.module.impl.QueueWarning;

import java.util.stream.Collectors;

import static com.mojang.brigadier.arguments.IntegerArgumentType.getInteger;
import static com.mojang.brigadier.arguments.IntegerArgumentType.integer;
import static com.zenith.Globals.CONFIG;
import static com.zenith.Globals.MODULE;
import static com.zenith.command.brigadier.ToggleArgumentType.getToggle;
import static com.zenith.command.brigadier.ToggleArgumentType.toggle;

public class QueueWarningCommand extends Command {
    @Override
    public CommandUsage commandUsage() {
        return CommandUsage.builder()
            .name("queueWarning")
            .category(CommandCategory.INFO)
            .description("""
            Configure warnings sent when 2b2t queue positions are reached.
            
            The list of queue positions to send the warnings can be configured, each with an optional mention.
            """)
            .usageLines(
                "on/off",
                "list",
                "clear",
                "add <position>",
                "add <position> mention",
                "del <position>"
            )
            .build();
    }

    @Override
    public LiteralArgumentBuilder<CommandContext> register() {
        return command("queueWarning")
            .then(argument("toggle", toggle()).executes(c -> {
                com.zenith.util.config.ConfigManager.modify(cfg -> cfg.client.extra.queueWarning.enabled = getToggle(c, "toggle"));
                c.getSource().getEmbed()
                    .title("Queue Warning " + toggleStrCaps(CONFIG.client.extra.queueWarning.enabled));
                MODULE.get(QueueWarning.class).syncEnabledFromConfig();
                return OK;
            }))
            .then(literal("list").executes(c -> {
                c.getSource().getEmbed()
                    .title("Queue Warning List");
                return OK;
            }))
            .then(literal("clear").executes(c -> {
                com.zenith.util.config.ConfigManager.modify(cfg -> cfg.client.extra.queueWarning.warningPositions.clear());
                com.zenith.util.config.ConfigManager.modify(cfg -> cfg.client.extra.queueWarning.mentionPositions.clear());
                c.getSource().getEmbed().title("Cleared All Positions");
                return OK;
            }))
            .then(literal("add").then(argument("pos", integer(1, 1000)).executes(c -> {
                int pos = getInteger(c, "pos");
                com.zenith.util.config.ConfigManager.modify(cfg -> cfg.client.extra.queueWarning.warningPositions.add(pos));
                com.zenith.util.config.ConfigManager.modify(cfg -> cfg.client.extra.queueWarning.mentionPositions.remove(pos));
                c.getSource().getEmbed().title("Added " + pos);
                return OK;
            })
                .then(literal("mention").executes(c -> {
                    int pos = getInteger(c, "pos");
                    com.zenith.util.config.ConfigManager.modify(cfg -> cfg.client.extra.queueWarning.warningPositions.add(pos));
                    com.zenith.util.config.ConfigManager.modify(cfg -> cfg.client.extra.queueWarning.mentionPositions.add(pos));
                    c.getSource().getEmbed().title("Added " + pos);
                    return OK;
                }))))
            .then(literal("del").then(argument("pos", integer(1, 1000)).executes(c -> {
                int pos = getInteger(c, "pos");
                com.zenith.util.config.ConfigManager.modify(cfg -> cfg.client.extra.queueWarning.warningPositions.remove(pos));
                com.zenith.util.config.ConfigManager.modify(cfg -> cfg.client.extra.queueWarning.mentionPositions.remove(pos));
                c.getSource().getEmbed().title("Deleted " + pos);
                return OK;
            })));
    }

    @Override
    public void defaultEmbed(final Embed builder) {
        builder
            .addField("Queue Warning", toggleStr(CONFIG.client.extra.queueWarning.enabled), false)
            .description("**Warn Positions**\n" + getWarnListStr())
            .primaryColor();
    }

    private String getWarnListStr() {
        return CONFIG.client.extra.queueWarning.warningPositions.isEmpty()
            ? "Empty"
            : CONFIG.client.extra.queueWarning.warningPositions.intStream()
                .sorted()
                .mapToObj(pos -> pos + (CONFIG.client.extra.queueWarning.mentionPositions.contains(pos) ? " (m)" : ""))
                .collect(Collectors.joining("\n"));
    }
}
