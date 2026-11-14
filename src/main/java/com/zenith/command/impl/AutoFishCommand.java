package com.zenith.command.impl;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.zenith.command.api.Command;
import com.zenith.command.api.CommandCategory;
import com.zenith.command.api.CommandContext;
import com.zenith.command.api.CommandUsage;
import com.zenith.discord.Embed;
import com.zenith.module.impl.AutoFish;
import com.zenith.util.math.MathHelper;
import org.cloudburstmc.math.vector.Vector2d;

import static com.zenith.Globals.*;
import static com.zenith.command.brigadier.RotationArgument.getRotation;
import static com.zenith.command.brigadier.RotationArgument.rotation;
import static com.zenith.command.brigadier.ToggleArgumentType.getToggle;
import static com.zenith.command.brigadier.ToggleArgumentType.toggle;

public class AutoFishCommand extends Command {
    @Override
    public CommandUsage commandUsage() {
        return CommandUsage.builder()
            .name("autoFish")
            .category(CommandCategory.MODULE)
            .description("""
             Automatically fishes, both casting and reeling.
             
             AutoFishing will prevent you from being AFK kicked. It's recommended to disable AntiAFK.
             """)
            .usageLines(
                "on/off",
                "rotation <yaw> <pitch>",
                "rotation sync"
            )
            .build();
    }

    @Override
    public LiteralArgumentBuilder<CommandContext> register() {
        return command("autoFish")
            .then(argument("toggle", toggle()).executes(c -> {
                com.zenith.util.config.ConfigManager.modify(cfg -> cfg.client.extra.autoFish.enabled = getToggle(c, "toggle"));
                MODULE.get(AutoFish.class).syncEnabledFromConfig();
                c.getSource().getEmbed()
                    .title("AutoFish " + toggleStr(CONFIG.client.extra.autoFish.enabled));
                return OK;
            }))
            .then(literal("rotation")
                .then(literal("sync").executes(c -> {
                    // normalize yaw and pitch to -180 to 180 and -90 to 90
                    com.zenith.util.config.ConfigManager.modify(cfg -> cfg.client.extra.autoFish.yaw = MathHelper.wrapYaw(CACHE.getPlayerCache().getYaw()));
                    com.zenith.util.config.ConfigManager.modify(cfg -> cfg.client.extra.autoFish.pitch = MathHelper.wrapPitch(CACHE.getPlayerCache().getPitch()));
                    c.getSource().getEmbed()
                        .title("Rotation synced to player!");
                }))
                .then(argument("rot", rotation()).executes(c -> {
                    Vector2d rot = getRotation(c, "rot");
                    com.zenith.util.config.ConfigManager.modify(cfg -> cfg.client.extra.autoFish.yaw = (float) rot.getX());
                    com.zenith.util.config.ConfigManager.modify(cfg -> cfg.client.extra.autoFish.pitch = (float) rot.getY());
                    c.getSource().getEmbed()
                        .title("Rotation set to " + CONFIG.client.extra.autoFish.yaw + " " + CONFIG.client.extra.autoFish.pitch);
                })));
    }

    @Override
    public void defaultEmbed(final Embed builder) {
        builder
            .addField("AutoFish", toggleStr(CONFIG.client.extra.autoFish.enabled), false)
            .addField("Yaw", CONFIG.client.extra.autoFish.yaw, false)
            .addField("Pitch", CONFIG.client.extra.autoFish.pitch, false)
            .primaryColor();
    }
}
