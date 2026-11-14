package com.zenith.command.impl;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.zenith.command.api.Command;
import com.zenith.command.api.CommandCategory;
import com.zenith.command.api.CommandContext;
import com.zenith.command.api.CommandUsage;
import com.zenith.discord.Embed;
import com.zenith.module.impl.AutoMend;

import static com.zenith.Globals.CONFIG;
import static com.zenith.Globals.MODULE;
import static com.zenith.command.brigadier.ToggleArgumentType.getToggle;
import static com.zenith.command.brigadier.ToggleArgumentType.toggle;

public class AutoMendCommand extends Command {
    @Override
    public CommandUsage commandUsage() {
        return CommandUsage.builder()
            .name("autoMend")
            .category(CommandCategory.MODULE)
            .description("""
            Equips items that are both damaged and have the mending enchantment to the offhand.
            
            Can be enabled while at an XP farm to repair items in your inventory.
            """)
            .usageLines(
                "on/off"
            )
            .build();
    }

    @Override
    public LiteralArgumentBuilder<CommandContext> register() {
        return command("autoMend")
            .then(argument("toggle", toggle()).executes(c -> {
                com.zenith.util.config.ConfigManager.modify(cfg -> cfg.client.extra.autoMend.enabled = getToggle(c, "toggle"));
                MODULE.get(AutoMend.class).syncEnabledFromConfig();
                c.getSource().getEmbed()
                    .title("AutoMend " + toggleStrCaps(CONFIG.client.extra.autoMend.enabled));
                return OK;
            }));
    }

    @Override
    public void defaultEmbed(Embed embed) {
        embed
            .addField("Auto Mend", toggleStr(CONFIG.client.extra.autoMend.enabled), false)
            .primaryColor();
    }
}
