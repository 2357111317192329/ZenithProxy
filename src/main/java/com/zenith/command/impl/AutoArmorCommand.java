package com.zenith.command.impl;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.zenith.command.api.Command;
import com.zenith.command.api.CommandCategory;
import com.zenith.command.api.CommandContext;
import com.zenith.command.api.CommandUsage;
import com.zenith.module.impl.AutoArmor;

import static com.zenith.Globals.CONFIG;
import static com.zenith.Globals.MODULE;
import static com.zenith.command.brigadier.ToggleArgumentType.getToggle;
import static com.zenith.command.brigadier.ToggleArgumentType.toggle;

public class AutoArmorCommand extends Command {
    @Override
    public CommandUsage commandUsage() {
        return CommandUsage.builder()
            .name("autoArmor")
            .category(CommandCategory.MODULE)
            .description("""
            Automatically equips the best armor in your inventory
            """)
            .usageLines(
                "on/off"
            )
            .build();
    }

    @Override
    public LiteralArgumentBuilder<CommandContext> register() {
        return command("autoArmor")
            .then(argument("toggle", toggle()).executes(c -> {
                com.zenith.util.config.ConfigManager.modify(cfg -> cfg.client.extra.autoArmor.enabled = getToggle(c, "toggle"));
                MODULE.get(AutoArmor.class).syncEnabledFromConfig();
                c.getSource().getEmbed()
                    .title("AutoArmor " + toggleStrCaps(CONFIG.client.extra.autoArmor.enabled))
                    .primaryColor();
                return OK;
            }));
    }
}
