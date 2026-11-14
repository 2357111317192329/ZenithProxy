package com.zenith.command.impl;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.zenith.command.api.Command;
import com.zenith.command.api.CommandCategory;
import com.zenith.command.api.CommandContext;
import com.zenith.command.api.CommandUsage;
import com.zenith.discord.Embed;
import com.zenith.module.impl.DualAccountTp;
import com.zenith.util.config.Config.Client.Extra.DualAccountTp.Mode;
import com.zenith.util.config.Config.Client.Extra.DualAccountTp.MovementMode;
import com.zenith.util.config.Config.Client.Extra.DualAccountTp.TargetState;

import java.util.Locale;

import static com.mojang.brigadier.arguments.IntegerArgumentType.getInteger;
import static com.mojang.brigadier.arguments.IntegerArgumentType.integer;
import static com.zenith.Globals.CONFIG;
import static com.zenith.Globals.MODULE;
import static com.zenith.command.brigadier.CustomStringArgumentType.getString;
import static com.zenith.command.brigadier.CustomStringArgumentType.wordWithChars;
import static com.zenith.command.brigadier.EnumStringArgumentType.enumStrings;
import static com.zenith.command.brigadier.ToggleArgumentType.getToggle;
import static com.zenith.command.brigadier.ToggleArgumentType.toggle;

public class DualAccountTpCommand extends Command {
    @Override
    public CommandUsage commandUsage() {
        return CommandUsage.builder()
            .name("dualAccountTp")
            .category(CommandCategory.MODULE)
            .description("""
            Dual-account ender pearl teleport helper.

            CLIENT mode sends `/msg <peer> <triggerPrefix> <secretKey>` when an emergency trigger fires.
            SERVER mode reads signs near the bot and pulls the trapdoor belonging to the requesting player.
            The sign must stand on top of a block, and the player's trapdoor must be adjacent to that block.

            Both instances must use the same triggerPrefix and secretKey.
            Requests from players without a matching sign are ignored.
            """)
            .usageLines(
                "on/off",
                "mode <client/server>",
                "peer <serverAccountName>",
                "key <secret>",
                "keyClear",
                "trigger",
                "test <playerName>",
                "chambers",
                "rescan",
                "cooldown <seconds>",
                "movement <stand/walk>",
                "targetState <toggle/close/open>",
                "retries <1-10>",
                "retryDelay <1-50>",
                "verifyDelay <1-20>",
                "clickPriority <1-1000000>",
                "scanRadiusChunks <radius>",
                "indexTtl <seconds>",
                "health on/off",
                "health threshold <0-20>",
                "thunder on/off",
                "unknownPlayer on/off",
                "unknownPlayer enemyListMode on/off",
                "unknownPlayer disablein3C3Ulobby on/off",
                "totemPop on/off",
                "totemPop minTotemsRemaining <count>",
                "lowY on/off",
                "lowY threshold <y>",
                "lowY endOnly on/off",
                "whilePlayerConnected on/off",
                "sendAck on/off",
                "replyOnFailure on/off"
            )
            .aliases("dualTp")
            .build();
    }

    @Override
    public LiteralArgumentBuilder<CommandContext> register() {
        return command("dualAccountTp")
            .executes(c -> OK)
            .then(argument("toggle", toggle()).executes(c -> {
                com.zenith.util.config.ConfigManager.modify(cfg -> cfg.client.extra.dualAccountTp.enabled = getToggle(c, "toggle"));
                MODULE.get(DualAccountTp.class).syncEnabledFromConfig();
                c.getSource().getEmbed()
                    .title("DualAccountTp " + toggleStrCaps(CONFIG.client.extra.dualAccountTp.enabled));
                return OK;
            }))
            .then(literal("mode").then(argument("mode", enumStrings(Mode.values())).executes(c -> {
                var mode = Mode.valueOf(getString(c, "mode").toUpperCase(Locale.ROOT));
                com.zenith.util.config.ConfigManager.modify(cfg -> cfg.client.extra.dualAccountTp.mode = mode);
                c.getSource().getEmbed()
                    .title("DualAccountTp Mode Set")
                    .addField("Mode", mode.name().toLowerCase(Locale.ROOT));
                return OK;
            })))
            .then(literal("peer").then(argument("name", wordWithChars()).executes(c -> {
                com.zenith.util.config.ConfigManager.modify(cfg -> cfg.client.extra.dualAccountTp.peerName = getString(c, "name"));
                c.getSource().getEmbed()
                    .title("DualAccountTp Peer Set")
                    .addField("Peer", CONFIG.client.extra.dualAccountTp.peerName);
                return OK;
            })))
            .then(literal("key").then(argument("secret", wordWithChars()).executes(c -> {
                com.zenith.util.config.ConfigManager.modify(cfg -> cfg.client.extra.dualAccountTp.secretKey = getString(c, "secret"));
                c.getSource().getEmbed()
                    .title("DualAccountTp Secret Key Set")
                    .description("The key is not displayed.");
                return OK;
            })))
            .then(literal("keyClear").executes(c -> {
                com.zenith.util.config.ConfigManager.modify(cfg -> cfg.client.extra.dualAccountTp.secretKey = "");
                c.getSource().getEmbed()
                    .title("DualAccountTp Secret Key Cleared");
                return OK;
            }))
            .then(literal("trigger").executes(c -> {
                var cfg = CONFIG.client.extra.dualAccountTp;
                if (cfg.mode == Mode.CLIENT) {
                    boolean sent = MODULE.get(DualAccountTp.class).triggerNow();
                    if (sent) {
                        c.getSource().getEmbed()
                            .title("TP Request Sent")
                            .successColor();
                    } else {
                        c.getSource().getEmbed()
                            .title("TP Request Not Sent")
                            .description("Check mode/peerName/secretKey/online/cooldown; see module log.")
                            .errorColor();
                    }
                } else {
                    c.getSource().getEmbed()
                        .title("Server Mode")
                        .description("Use `dualAccountTp test <playerName>` to test a chamber.");
                }
                return OK;
            }))
            .then(literal("test").then(argument("player", wordWithChars()).executes(c -> {
                var playerName = getString(c, "player");
                MODULE.get(DualAccountTp.class).testChamber(playerName);
                c.getSource().getEmbed()
                    .title("Testing Chamber")
                    .addField("Player", playerName);
                return OK;
            })))
            .then(literal("chambers").executes(c -> {
                var chambers = MODULE.get(DualAccountTp.class).chamberSummary();
                c.getSource().getEmbed()
                    .title("Pearl Chambers")
                    .description(chambers.isEmpty() ? "None found" : String.join("\n", chambers))
                    .primaryColor();
                return OK;
            }))
            .then(literal("rescan").executes(c -> {
                MODULE.get(DualAccountTp.class).resetChambers();
                c.getSource().getEmbed()
                    .title("Chamber Index Cleared")
                    .successColor();
                return OK;
            }))
            .then(literal("cooldown").then(argument("seconds", integer(0, 3600)).executes(c -> {
                com.zenith.util.config.ConfigManager.modify(cfg -> cfg.client.extra.dualAccountTp.cooldownSeconds = getInteger(c, "seconds"));
                c.getSource().getEmbed()
                    .title("Cooldown Set")
                    .addField("Seconds", CONFIG.client.extra.dualAccountTp.cooldownSeconds);
                return OK;
            })))
            .then(literal("movement").then(argument("mode", enumStrings(MovementMode.values())).executes(c -> {
                var mode = MovementMode.valueOf(getString(c, "mode").toUpperCase(Locale.ROOT));
                com.zenith.util.config.ConfigManager.modify(cfg -> cfg.client.extra.dualAccountTp.movementMode = mode);
                c.getSource().getEmbed()
                    .title("Movement Mode Set")
                    .addField("Mode", mode.name().toLowerCase(Locale.ROOT));
                return OK;
            })))
            .then(literal("targetState").then(argument("state", enumStrings(TargetState.values())).executes(c -> {
                var state = TargetState.valueOf(getString(c, "state").toUpperCase(Locale.ROOT));
                com.zenith.util.config.ConfigManager.modify(cfg -> cfg.client.extra.dualAccountTp.targetState = state);
                c.getSource().getEmbed()
                    .title("Target State Set")
                    .addField("State", state.name().toLowerCase(Locale.ROOT));
                return OK;
            })))
            .then(literal("retries").then(argument("count", integer(1, 10)).executes(c -> {
                com.zenith.util.config.ConfigManager.modify(cfg -> cfg.client.extra.dualAccountTp.clickRetries = getInteger(c, "count"));
                c.getSource().getEmbed()
                    .title("Click Retries Set")
                    .addField("Retries", CONFIG.client.extra.dualAccountTp.clickRetries);
                return OK;
            })))
            .then(literal("retryDelay").then(argument("ticks", integer(1, 50)).executes(c -> {
                com.zenith.util.config.ConfigManager.modify(cfg -> cfg.client.extra.dualAccountTp.clickRetryDelayTicks = getInteger(c, "ticks"));
                c.getSource().getEmbed()
                    .title("Click Retry Delay Set")
                    .addField("Ticks", CONFIG.client.extra.dualAccountTp.clickRetryDelayTicks);
                return OK;
            })))
            .then(literal("verifyDelay").then(argument("ticks", integer(1, 20)).executes(c -> {
                com.zenith.util.config.ConfigManager.modify(cfg -> cfg.client.extra.dualAccountTp.clickVerifyDelayTicks = getInteger(c, "ticks"));
                c.getSource().getEmbed()
                    .title("Click Verify Delay Set")
                    .addField("Ticks", CONFIG.client.extra.dualAccountTp.clickVerifyDelayTicks);
                return OK;
            })))
            .then(literal("clickPriority").then(argument("priority", integer(1, 1000000)).executes(c -> {
                com.zenith.util.config.ConfigManager.modify(cfg -> cfg.client.extra.dualAccountTp.clickPriority = getInteger(c, "priority"));
                c.getSource().getEmbed()
                    .title("Click Priority Set")
                    .addField("Priority", CONFIG.client.extra.dualAccountTp.clickPriority);
                return OK;
            })))
            .then(literal("scanRadiusChunks").then(argument("radius", integer(0, 8)).executes(c -> {
                com.zenith.util.config.ConfigManager.modify(cfg -> cfg.client.extra.dualAccountTp.chamberScanChunkRadius = getInteger(c, "radius"));
                MODULE.get(DualAccountTp.class).resetChambers();
                c.getSource().getEmbed()
                    .title("Scan Radius Set")
                    .addField("Chunks", CONFIG.client.extra.dualAccountTp.chamberScanChunkRadius);
                return OK;
            })))
            .then(literal("indexTtl").then(argument("seconds", integer(0, 3600)).executes(c -> {
                com.zenith.util.config.ConfigManager.modify(cfg -> cfg.client.extra.dualAccountTp.chamberIndexTtlSeconds = getInteger(c, "seconds"));
                MODULE.get(DualAccountTp.class).resetChambers();
                c.getSource().getEmbed()
                    .title("Chamber Index TTL Set")
                    .addField("Seconds", CONFIG.client.extra.dualAccountTp.chamberIndexTtlSeconds);
                return OK;
            })))
            .then(literal("health")
                .then(argument("toggle", toggle()).executes(c -> {
                    com.zenith.util.config.ConfigManager.modify(cfg -> cfg.client.extra.dualAccountTp.healthTrigger = getToggle(c, "toggle"));
                    c.getSource().getEmbed()
                        .title("Health Trigger " + toggleStrCaps(CONFIG.client.extra.dualAccountTp.healthTrigger));
                    return OK;
                }))
                .then(literal("threshold").then(argument("level", integer(0, 20)).executes(c -> {
                    com.zenith.util.config.ConfigManager.modify(cfg -> cfg.client.extra.dualAccountTp.healthThreshold = getInteger(c, "level"));
                    c.getSource().getEmbed()
                        .title("Health Threshold Set")
                        .addField("Level", CONFIG.client.extra.dualAccountTp.healthThreshold);
                    return OK;
                }))))
            .then(literal("thunder").then(argument("toggle", toggle()).executes(c -> {
                com.zenith.util.config.ConfigManager.modify(cfg -> cfg.client.extra.dualAccountTp.thunderTrigger = getToggle(c, "toggle"));
                c.getSource().getEmbed()
                    .title("Thunder Trigger " + toggleStrCaps(CONFIG.client.extra.dualAccountTp.thunderTrigger));
                return OK;
            })))
            .then(literal("unknownPlayer")
                .then(argument("toggle", toggle()).executes(c -> {
                    com.zenith.util.config.ConfigManager.modify(cfg -> cfg.client.extra.dualAccountTp.onUnknownPlayerInVisualRange = getToggle(c, "toggle"));
                    c.getSource().getEmbed()
                        .title("Unknown Player Trigger " + toggleStrCaps(CONFIG.client.extra.dualAccountTp.onUnknownPlayerInVisualRange));
                    return OK;
                }))
                .then(literal("enemyListMode").then(argument("toggle", toggle()).executes(c -> {
                    com.zenith.util.config.ConfigManager.modify(cfg -> cfg.client.extra.dualAccountTp.enemyListMode = getToggle(c, "toggle"));
                    c.getSource().getEmbed()
                        .title("Unknown Player Enemy List Mode " + toggleStrCaps(CONFIG.client.extra.dualAccountTp.enemyListMode));
                    return OK;
                })))
                .then(literal("disablein3C3Ulobby").then(argument("toggle", toggle()).executes(c -> {
                    com.zenith.util.config.ConfigManager.modify(cfg -> cfg.client.extra.dualAccountTp.disablein3C3Ulobby = getToggle(c, "toggle"));
                    c.getSource().getEmbed()
                        .title("Disable in 3C3U Lobby " + toggleStrCaps(CONFIG.client.extra.dualAccountTp.disablein3C3Ulobby));
                    return OK;
                }))))
            .then(literal("totemPop")
                .then(argument("toggle", toggle()).executes(c -> {
                    com.zenith.util.config.ConfigManager.modify(cfg -> cfg.client.extra.dualAccountTp.onTotemPop = getToggle(c, "toggle"));
                    c.getSource().getEmbed()
                        .title("Totem Pop Trigger " + toggleStrCaps(CONFIG.client.extra.dualAccountTp.onTotemPop));
                    return OK;
                }))
                .then(literal("minTotemsRemaining").then(argument("count", integer(1)).executes(c -> {
                    com.zenith.util.config.ConfigManager.modify(cfg -> cfg.client.extra.dualAccountTp.minTotemsRemaining = getInteger(c, "count"));
                    c.getSource().getEmbed()
                        .title("Min Totems Remaining Set")
                        .addField("Count", CONFIG.client.extra.dualAccountTp.minTotemsRemaining);
                    return OK;
                }))))
            .then(literal("lowY")
                .then(argument("toggle", toggle()).executes(c -> {
                    com.zenith.util.config.ConfigManager.modify(cfg -> cfg.client.extra.dualAccountTp.lowYTrigger = getToggle(c, "toggle"));
                    c.getSource().getEmbed()
                        .title("Low Y Trigger " + toggleStrCaps(CONFIG.client.extra.dualAccountTp.lowYTrigger));
                    return OK;
                }))
                .then(literal("threshold").then(argument("y", integer(-128, 320)).executes(c -> {
                    com.zenith.util.config.ConfigManager.modify(cfg -> cfg.client.extra.dualAccountTp.lowYThreshold = getInteger(c, "y"));
                    c.getSource().getEmbed()
                        .title("Low Y Threshold Set")
                        .addField("Y", CONFIG.client.extra.dualAccountTp.lowYThreshold);
                    return OK;
                })))
                .then(literal("endOnly").then(argument("toggle", toggle()).executes(c -> {
                    com.zenith.util.config.ConfigManager.modify(cfg -> cfg.client.extra.dualAccountTp.lowYEndOnly = getToggle(c, "toggle"));
                    c.getSource().getEmbed()
                        .title("Low Y End Only " + toggleStrCaps(CONFIG.client.extra.dualAccountTp.lowYEndOnly));
                    return OK;
                }))))
            .then(literal("whilePlayerConnected").then(argument("toggle", toggle()).executes(c -> {
                com.zenith.util.config.ConfigManager.modify(cfg -> cfg.client.extra.dualAccountTp.whilePlayerConnected = getToggle(c, "toggle"));
                c.getSource().getEmbed()
                    .title("While Player Connected " + toggleStrCaps(CONFIG.client.extra.dualAccountTp.whilePlayerConnected));
                return OK;
            })))
            .then(literal("sendAck").then(argument("toggle", toggle()).executes(c -> {
                com.zenith.util.config.ConfigManager.modify(cfg -> cfg.client.extra.dualAccountTp.sendAck = getToggle(c, "toggle"));
                c.getSource().getEmbed()
                    .title("Send Ack " + toggleStrCaps(CONFIG.client.extra.dualAccountTp.sendAck));
                return OK;
            })))
            .then(literal("replyOnFailure").then(argument("toggle", toggle()).executes(c -> {
                com.zenith.util.config.ConfigManager.modify(cfg -> cfg.client.extra.dualAccountTp.replyOnFailure = getToggle(c, "toggle"));
                c.getSource().getEmbed()
                    .title("Reply On Failure " + toggleStrCaps(CONFIG.client.extra.dualAccountTp.replyOnFailure));
                return OK;
            })));
    }

    @Override
    public void defaultEmbed(final Embed embed) {
        // Commands like `chambers` and `test` already produced their own output.
        if (embed.isTitlePresent() || embed.isDescriptionPresent()) return;

        var cfg = CONFIG.client.extra.dualAccountTp;
        embed
            .title("DualAccountTp")
            .addField("Enabled", toggleStr(cfg.enabled), true)
            .addField("Mode", cfg.mode.name().toLowerCase(Locale.ROOT), true)
            .addField("Secret Key", cfg.secretKey.isBlank() ? "Not Set" : "Set", true)
            .addField("Cooldown", cfg.cooldownSeconds + "s", true);

        if (cfg.mode == Mode.CLIENT) {
            embed
                .addField("Peer", cfg.peerName.isBlank() ? "Not Set" : cfg.peerName, true)
                .addField("Health", toggleStr(cfg.healthTrigger) + " (" + cfg.healthThreshold + ")", true)
                .addField("Low Y", toggleStr(cfg.lowYTrigger) + " (" + cfg.lowYThreshold + ")", true)
                .addField("Totem Pop", toggleStr(cfg.onTotemPop) + " (" + cfg.minTotemsRemaining + ")", true)
                .addField("Unknown Player", toggleStr(cfg.onUnknownPlayerInVisualRange), true)
                .addField("While Player Connected", toggleStr(cfg.whilePlayerConnected), true);
        } else {
            embed
                .addField("Movement", cfg.movementMode.name().toLowerCase(Locale.ROOT), true)
                .addField("Target State", cfg.targetState.name().toLowerCase(Locale.ROOT), true)
                .addField("Scan Radius", cfg.chamberScanChunkRadius + " chunks", true)
                .addField("Index TTL", cfg.chamberIndexTtlSeconds + "s", true)
                .addField("Click Retries", cfg.clickRetries, true)
                .addField("Retry Delay", cfg.clickRetryDelayTicks + " ticks", true)
                .addField("Verify Delay", cfg.clickVerifyDelayTicks + " ticks", true)
                .addField("Send Ack", toggleStr(cfg.sendAck), true)
                .addField("Reply On Failure", toggleStr(cfg.replyOnFailure), true);
        }
        embed.primaryColor();
    }
}
