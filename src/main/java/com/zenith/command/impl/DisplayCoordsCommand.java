package com.zenith.command.impl;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.zenith.command.api.Command;
import com.zenith.command.api.CommandCategory;
import com.zenith.command.api.CommandContext;
import com.zenith.command.api.CommandUsage;

import static com.zenith.Globals.CONFIG;
import static com.zenith.command.brigadier.ToggleArgumentType.getToggle;
import static com.zenith.command.brigadier.ToggleArgumentType.toggle;

public class DisplayCoordsCommand extends Command {
    @Override
    public CommandUsage commandUsage() {
        return CommandUsage.builder()
            .name("displayCoords")
            .category(CommandCategory.MANAGE)
            .description("""
            Configures whether the discord bot's messages should display coordinates. Only usable by account owner(s).
            """)
            .usageLines("on/off")
            .aliases("coords")
            .build();
    }

    @Override
    public LiteralArgumentBuilder<CommandContext> register() {
        return command("displayCoords").requires(Command::validateAccountOwner)
            .then(argument("toggle", toggle()).executes(c -> {
                com.zenith.util.config.ConfigManager.modify(cfg -> cfg.discord.reportCoords = getToggle(c, "toggle"));
                c.getSource().getEmbed()
                    .title("Coordinates " + toggleStrCaps(CONFIG.discord.reportCoords))
                    .primaryColor();
                return OK;
            }));
    }
}
