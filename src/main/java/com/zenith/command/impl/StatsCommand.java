package com.zenith.command.impl;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.zenith.Proxy;
import com.zenith.command.api.Command;
import com.zenith.command.api.CommandCategory;
import com.zenith.command.api.CommandContext;
import com.zenith.command.api.CommandUsage;
import com.zenith.feature.api.vcapi.VcApi;
import com.zenith.feature.api.vcapi.model.StatsResponse;
import net.dv8tion.jda.api.utils.TimeFormat;

import java.time.Duration;
import java.util.Optional;

import static com.zenith.command.brigadier.CustomStringArgumentType.wordWithChars;
import static com.zenith.util.math.MathHelper.formatDurationLong;

public class StatsCommand extends Command {

    @Override
    public CommandUsage commandUsage() {
        return CommandUsage.builder()
            .name("stats")
            .category(CommandCategory.INFO)
            .description("Gets the 2b2t stats of a player using https://api.2b2t.vc")
            .usageLines(
                "<playerName>"
            )
            .build();
    }

    @Override
    public LiteralArgumentBuilder<CommandContext> register() {
        return command("stats")
            .then(argument("playerName", wordWithChars()).executes(c -> {
                final String playerName = c.getArgument("playerName", String.class);
                final Optional<StatsResponse> statsResponse = VcApi.INSTANCE.getStats(playerName);
                if (statsResponse.isEmpty()) {
                    c.getSource().getEmbed()
                        .title(playerName + " not found");
                    return ERROR;
                }
                final StatsResponse playerStats = statsResponse.get();
                c.getSource().getEmbed()
                    .title("Player Stats")
                    .primaryColor()
                    .addField("Player", playerName, true)
                    .addField("\u200B", "\u200B", true)
                    .addField("\u200B", "\u200B", true)
                    .addField("Joins", playerStats.joinCount(), true)
                    .addField("Leaves", playerStats.leaveCount(), true)
                    .addField("\u200B", "\u200B", true)
                    .addField("First Seen", TimeFormat.DATE_TIME_SHORT.format(playerStats.firstSeen().toInstant()), true)
                    .addField("Last Seen", TimeFormat.DATE_TIME_SHORT.format(playerStats.lastSeen().toInstant()), true)
                    .addField("\u200B", "\u200B", true)
                    .addField("Playtime", formatDurationLong(Duration.ofSeconds(playerStats.playtimeSeconds())), true)
                    .addField("Playtime (Last 30 Days)", formatDurationLong(Duration.ofSeconds(playerStats.playtimeSecondsMonth())), true)
                    .addField("\u200B", "\u200B", true)
                    .addField("Deaths", playerStats.deathCount(), true)
                    .addField("Kills", playerStats.killCount(), true)
                    .addField("\u200B", "\u200B", true)
                    .addField("Chats", playerStats.chatsCount(), true)
                    .addField("Priority Queue", playerStats.prio() ? "Yes (probably)" : "No (probably not)", true)
                    .addField("\u200B", "\u200B", true)
                    .thumbnail(Proxy.getInstance().getPlayerHeadURL(playerName).toString());
                return OK;
            }));
    }
}
