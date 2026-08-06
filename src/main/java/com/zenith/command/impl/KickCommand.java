package com.zenith.command.impl;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.zenith.Proxy;
import com.zenith.command.api.*;
import com.zenith.discord.DiscordBot;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import static com.mojang.brigadier.arguments.StringArgumentType.string;
import static com.zenith.Globals.CONFIG;
import static com.zenith.command.brigadier.CustomStringArgumentType.wordWithChars;
import static com.zenith.discord.DiscordBot.escape;

public class KickCommand extends Command {
    @Override
    public CommandUsage commandUsage() {
        return CommandUsage.builder()
            .name("kick")
            .category(CommandCategory.MANAGE)
            .description("""
            Kicks all players or a specific player. Only usable by account owners.
            """)
            .usageLines(
                "",
                "<player>"
            )
            .build();
    }

    @Override
    public LiteralArgumentBuilder<CommandContext> register() {
        return command("kick").requires(Command::validateAccountOwner)
            .executes(c -> {
                final boolean kickCurrentPlayer = c.getSource().getSource() != CommandSources.PLAYER;
                final List<String> kickedPlayers = new ArrayList<>();
                var connections = Proxy.getInstance().getActiveConnections().getArray();
                for (int i = 0; i < connections.length; i++) {
                    var connection = connections[i];
                    if (connection.equals(Proxy.getInstance().getCurrentPlayer().get()) && !kickCurrentPlayer) continue;
                    kickedPlayers.add(connection.getName());
                    connection.disconnect(CONFIG.server.extra.whitelist.kickmsg);
                }
                c.getSource().getEmbed()
                    .title("Kicked " + kickedPlayers.size() + " players")
                    .addField("Players", kickedPlayers.stream().map(DiscordBot::escape).collect(Collectors.joining(", ")), false);
                return OK;
            })
            .then(argument("player", wordWithChars()).executes(c -> {
                final String playerName = StringArgumentType.getString(c, "player");
                var connections = Proxy.getInstance().getActiveConnections().getArray();
                for (int i = 0; i < connections.length; i++) {
                    var connection = connections[i];
                    if (connection.getName().equalsIgnoreCase(playerName)) {
                        connection.disconnect(CONFIG.server.extra.whitelist.kickmsg);
                        c.getSource().getEmbed()
                            .title("Kicked " + escape(playerName))
                            .primaryColor();
                        return OK;
                    }
                }
                c.getSource().getEmbed()
                    .title("Unable to kick " + escape(playerName))
                    .errorColor()
                    .addField("Reason", "Player is not connected", false);
                return OK;
            }));
    }
}
