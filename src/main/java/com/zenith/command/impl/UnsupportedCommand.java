package com.zenith.command.impl;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.zenith.Proxy;
import com.zenith.command.api.*;
import com.zenith.discord.Embed;
import com.zenith.network.client.Authenticator;

import static com.zenith.Globals.CONFIG;
import static com.zenith.command.brigadier.CustomStringArgumentType.getString;
import static com.zenith.command.brigadier.CustomStringArgumentType.wordWithChars;
import static com.zenith.command.brigadier.ToggleArgumentType.getToggle;
import static com.zenith.command.brigadier.ToggleArgumentType.toggle;
import static com.zenith.discord.DiscordBot.escape;
import static com.zenith.util.config.Config.Authentication.AccountType.OFFLINE;

public class UnsupportedCommand extends Command {
    @Override
    public CommandUsage commandUsage() {
        return CommandUsage.builder()
            .name("unsupported")
            .category(CommandCategory.MANAGE)
            .description("""
            Unsupported settings that cause critical security issues.
            
            Do not use edit these unless you absolutely understand what you are doing.
            
            No user support will be provided if you modify any of these settings.
            
            All subcommands are only usable from the terminal.
            """)
            .usageLines(
                "spectatorWhitelist on/off",
                "allowOfflinePlayers on/off",
                "auth type offline",
                "auth offlineUsername <username>"
            )
            .build();
    }

    @Override
    public LiteralArgumentBuilder<CommandContext> register() {
        return command("unsupported")
            .requires(c -> Command.validateCommandSource(c, CommandSources.TERMINAL))
            .then(literal("spectatorWhitelist").then(argument("toggle", toggle()).executes(c -> {
                com.zenith.util.config.ConfigManager.modify(cfg -> cfg.server.spectator.whitelistEnabled = getToggle(c, "toggle"));
                c.getSource().getEmbed()
                    .title("Spectator Whitelist " + toggleStrCaps(CONFIG.server.spectator.whitelistEnabled));
                return OK;
            })))
            .then(literal("allowOfflinePlayers").then(argument("toggle", toggle()).executes(c -> {
                com.zenith.util.config.ConfigManager.modify(cfg -> cfg.server.verifyUsers = !getToggle(c, "toggle"));
                c.getSource().getEmbed()
                    .title("Allow Offline Players " + toggleStrCaps(!CONFIG.server.verifyUsers));
                return OK;
            })))
            .then(literal("auth")
                .then(literal("type").then(literal("offline").executes(c -> {
                    com.zenith.util.config.ConfigManager.modify(cfg -> cfg.authentication.accountType = OFFLINE);
                    c.getSource().getEmbed()
                        .title("Authentication Type Set");
                    Proxy.getInstance().cancelLogin();
                    Authenticator.INSTANCE.clearAuthCache();
                })))
                .then(literal("offlineUsername").then(argument("username", wordWithChars()).executes(c -> {
                    com.zenith.util.config.ConfigManager.modify(cfg -> cfg.authentication.username = getString(c, "username"));
                    c.getSource().getEmbed()
                        .title("Offline Username Set");
                    Proxy.getInstance().cancelLogin();
                    Authenticator.INSTANCE.clearAuthCache();
                    return OK;
                }))));
    }

    @Override
    public void defaultEmbed(Embed builder) {
        builder
            .addField("Spectator Whitelist", toggleStr(CONFIG.server.spectator.whitelistEnabled))
            .addField("Allow Offline Players", toggleStr(!CONFIG.server.verifyUsers))
            .addField("Offline Authentication", toggleStr(CONFIG.authentication.accountType == OFFLINE))
            .addField("Offline Username", escape(CONFIG.authentication.username))
            .primaryColor();
    }
}
