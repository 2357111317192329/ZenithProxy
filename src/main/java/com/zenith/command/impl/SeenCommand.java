package com.zenith.command.impl;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.zenith.Proxy;
import com.zenith.command.api.Command;
import com.zenith.command.api.CommandCategory;
import com.zenith.command.api.CommandContext;
import com.zenith.command.api.CommandUsage;
import com.zenith.feature.api.vcapi.VcApi;
import net.dv8tion.jda.api.utils.TimeFormat;
import org.jspecify.annotations.Nullable;

import java.time.OffsetDateTime;

import static com.zenith.command.brigadier.CustomStringArgumentType.getString;
import static com.zenith.command.brigadier.CustomStringArgumentType.wordWithChars;
import static com.zenith.discord.DiscordBot.escape;

public class SeenCommand extends Command {

    @Override
    public CommandUsage commandUsage() {
        return CommandUsage.builder()
            .name("seen")
            .category(CommandCategory.INFO)
            .description("""
            Gets the first and last times a player was seen on 2b2t using https://api.2b2t.vc
            """)
            .usageLines(
                "<playerName>"
            )
            .aliases(
                "firstseen",
                "lastseen"
            )
            .build();
    }

    @Override
    public LiteralArgumentBuilder<CommandContext> register() {
        return command("seen")
            .then(argument("playerName", wordWithChars()).executes(c -> {
                final String playerName = getString(c, "playerName");
                var seenResponse = VcApi.INSTANCE.getSeen(playerName);
                if (seenResponse.isEmpty()) {
                    c.getSource().getEmbed()
                        .title(escape(playerName) + " not found");
                    return ERROR;
                }
                c.getSource().getEmbed()
                    .title("Seen")
                    .primaryColor();
                seenResponse.ifPresent((response) -> c.getSource().getEmbed()
                    .addField("Player", playerName, true)
                    .addField("\u200B", "\u200B", true)
                    .addField("\u200B", "\u200B", true)
                    .addField("First Seen", getSeenString(response.firstSeen()), false)
                    .addField("Last Seen", getSeenString(response.lastSeen()), false)
                    .thumbnail(Proxy.getInstance().getPlayerHeadURL(playerName).toString()));
                return OK;
            }));
    }

    private String getSeenString(@Nullable final OffsetDateTime time) {
        if (time == null) return "Never";
        return TimeFormat.DATE_TIME_SHORT.format(time);
    }
}
