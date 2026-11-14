package com.zenith.command.impl;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.zenith.command.api.Command;
import com.zenith.command.api.CommandCategory;
import com.zenith.command.api.CommandContext;
import com.zenith.command.api.CommandUsage;

import java.util.stream.Collectors;

import static com.zenith.Globals.CONFIG;
import static com.zenith.command.brigadier.CustomStringArgumentType.wordWithChars;
import static com.zenith.command.brigadier.ToggleArgumentType.getToggle;
import static com.zenith.command.brigadier.ToggleArgumentType.toggle;

public class PacketLogCommand extends Command {
    @Override
    public CommandUsage commandUsage() {
        return CommandUsage.builder()
            .name("packetLog")
            .category(CommandCategory.INFO)
            .description("""
                Logs packets that are sent/received. Requires account owner permissions.

                `client` -> packets between Zenith and destination MC server
                `server` -> packets between players and Zenith

                If a filter is set, only packets matching the filter will be logged.
                Filters are always lowercase, but matched case-insensitive.

                `logLevelDebug` -> toggles the logger level between INFO and DEBUG

                To enable the debug log: `debug debugLogs on`
                And to enable debug log in the terminal: `debug terminalDebugLogs on`
                """)
            .usageLines(
                "on/off",
                "client on/off",
                "server on/off",
                "filter add/del <string>",
                "filter list",
                "filter clear",
                "logLevelDebug on/off"
            )
            .build();
    }

    @Override
    public LiteralArgumentBuilder<CommandContext> register() {
        return command("packetLog").requires(Command::validateAccountOwner)
            .then(argument("toggle", toggle()).executes(c -> {
                com.zenith.util.config.ConfigManager.modify(cfg -> cfg.debug.packetLog.enabled = getToggle(c, "toggle"));
                c.getSource().getEmbed()
                    .title("Packet Log " + toggleStrCaps(CONFIG.debug.packetLog.enabled));
            }))
            .then(literal("client")
                .then(argument("toggle", toggle()).executes(c -> {
                    var toggle = getToggle(c, "toggle");
                    if (toggle) {
                        com.zenith.util.config.ConfigManager.modify(cfg -> cfg.debug.packetLog.clientPacketLog.received = true);
                        com.zenith.util.config.ConfigManager.modify(cfg -> cfg.debug.packetLog.clientPacketLog.receivedBody = true);
                        com.zenith.util.config.ConfigManager.modify(cfg -> cfg.debug.packetLog.clientPacketLog.postSent = true);
                        com.zenith.util.config.ConfigManager.modify(cfg -> cfg.debug.packetLog.clientPacketLog.postSentBody = true);
                    } else {
                        com.zenith.util.config.ConfigManager.modify(cfg -> cfg.debug.packetLog.clientPacketLog.received = false);
                        com.zenith.util.config.ConfigManager.modify(cfg -> cfg.debug.packetLog.clientPacketLog.postSent = false);
                        com.zenith.util.config.ConfigManager.modify(cfg -> cfg.debug.packetLog.clientPacketLog.preSent = false);
                    }
                    c.getSource().getEmbed()
                        .title("Client Packet Log " + toggleStrCaps(toggle));
                })))
            .then(literal("server")
                .then(argument("toggle", toggle()).executes(c -> {
                    var toggle = getToggle(c, "toggle");
                    if (toggle) {
                        com.zenith.util.config.ConfigManager.modify(cfg -> cfg.debug.packetLog.serverPacketLog.received = true);
                        com.zenith.util.config.ConfigManager.modify(cfg -> cfg.debug.packetLog.serverPacketLog.receivedBody = true);
                        com.zenith.util.config.ConfigManager.modify(cfg -> cfg.debug.packetLog.serverPacketLog.postSent = true);
                        com.zenith.util.config.ConfigManager.modify(cfg -> cfg.debug.packetLog.serverPacketLog.postSentBody = true);
                    } else {
                        com.zenith.util.config.ConfigManager.modify(cfg -> cfg.debug.packetLog.serverPacketLog.received = false);
                        com.zenith.util.config.ConfigManager.modify(cfg -> cfg.debug.packetLog.serverPacketLog.postSent = false);
                        com.zenith.util.config.ConfigManager.modify(cfg -> cfg.debug.packetLog.serverPacketLog.preSent = false);
                    }
                    c.getSource().getEmbed()
                        .title("Server Packet Log " + toggleStrCaps(toggle));
                })))
            .then(literal("filter")
                .then(literal("add").then(argument("filter", wordWithChars()).executes(c -> {
                    com.zenith.util.config.ConfigManager.modify(cfg -> cfg.debug.packetLog.packetFilterList.add(c.getArgument("filter", String.class).toLowerCase()));
                    c.getSource().getEmbed()
                        .title("Packet Log Filter Added");
                })))
                .then(literal("del").then(argument("filter", wordWithChars()).executes(c -> {
                    com.zenith.util.config.ConfigManager.modify(cfg -> cfg.debug.packetLog.packetFilterList.remove(c.getArgument("filter", String.class).toLowerCase()));
                    c.getSource().getEmbed()
                        .title("Packet Log Filter Removed");
                })))
                .then(literal("list").executes(c -> {
                    c.getSource().getEmbed()
                        .title("Packet Log Filter List");
                }))
                .then(literal("clear").executes(c -> {
                    com.zenith.util.config.ConfigManager.modify(cfg -> cfg.debug.packetLog.packetFilterList.clear());
                    c.getSource().getEmbed()
                        .title("Packet Log Filter Cleared");
                })))
            .then(literal("logLevelDebug").then(argument("toggle", toggle()).executes(c -> {
                com.zenith.util.config.ConfigManager.modify(cfg -> cfg.debug.packetLog.logLevelDebug = getToggle(c, "toggle"));
                c.getSource().getEmbed()
                    .title("Log Level Debug " + toggleStrCaps(CONFIG.debug.packetLog.logLevelDebug));
            })));
    }

    @Override
    public void defaultHandler(CommandContext ctx) {
        ctx.getEmbed()
            .description(packetLogFilterList())
            .primaryColor();
    }

    private String packetLogFilterList() {
        String result = "**Filter List:**\n";
        if (CONFIG.debug.packetLog.packetFilterList.isEmpty()) {
            return result + "None";
        }
        var listStr = CONFIG.debug.packetLog.packetFilterList.stream()
            .map(s -> String.format("`%s`", s))
            .collect(Collectors.joining("\n"));
        return result + listStr;
    }
}
