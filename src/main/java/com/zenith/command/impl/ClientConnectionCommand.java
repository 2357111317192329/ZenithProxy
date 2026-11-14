package com.zenith.command.impl;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.zenith.Proxy;
import com.zenith.command.api.Command;
import com.zenith.command.api.CommandCategory;
import com.zenith.command.api.CommandContext;
import com.zenith.command.api.CommandSources;
import com.zenith.command.api.CommandUsage;
import com.zenith.discord.Embed;
import com.zenith.util.config.Config.Client.KeepAliveHandling.KeepAliveMode;
import org.geysermc.mcprotocollib.network.ProxyInfo;

import java.util.Arrays;
import java.util.regex.Pattern;

import static com.mojang.brigadier.arguments.DoubleArgumentType.doubleArg;
import static com.mojang.brigadier.arguments.DoubleArgumentType.getDouble;
import static com.mojang.brigadier.arguments.IntegerArgumentType.getInteger;
import static com.mojang.brigadier.arguments.IntegerArgumentType.integer;
import static com.zenith.Globals.CONFIG;
import static com.zenith.command.brigadier.CustomStringArgumentType.getString;
import static com.zenith.command.brigadier.CustomStringArgumentType.wordWithChars;
import static com.zenith.command.brigadier.ToggleArgumentType.getToggle;
import static com.zenith.command.brigadier.ToggleArgumentType.toggle;

public class ClientConnectionCommand extends Command {
    private static final Pattern bindAddressPattern = Pattern.compile("^(?:[0-9]{1,3}\\.){3}[0-9]{1,3}$");
    @Override
    public CommandUsage commandUsage() {
        return CommandUsage.builder()
            .name("clientConnection")
            .category(CommandCategory.MANAGE)
            .description("""
            Manages the connection configuration from ZenithProxy to the destination MC server.
            """)
            .usageLines(
                "autoConnect on/off",
                "hideusername on/off",
                "proxy on/off",
                "proxy type <type>",
                "proxy host <host>",
                "proxy port <port>",
                "proxy user <user>",
                "proxy password <password>",
                "proxy auth clear",
                "bindAddress <address>",
                "timeout on/off",
                "timeout <seconds>",
                "ping packetInterval <seconds>",
                "keepAlive mode <passthrough/independent>",
                "keepAlive queueTimeout <ms>",
                "passwordLogin on/off",
                "passwordLogin set <password>",
                "passwordLogin allowUnencrypted on/off",
                "passwordLogin status",
                "passwordLogin lockout on/off",
                "passwordLogin lockout threshold <count>",
                "passwordLogin lockout baseDelay <seconds>",
                "passwordLogin lockout multiplier <factor>",
                "passwordLogin lockout maxDelay <seconds>",
                "passwordLogin lockout status",
                "passwordLogin lockout clear",
                "tempPassword add <minutes>",
                "tempPassword list",
                "tempPassword remove <id>",
                "tempPassword denied add <command>",
                "tempPassword denied remove <command>",
                "tempPassword denied list",
                "nameProtection on/off"
            )
            .build();
    }

    @Override
    public LiteralArgumentBuilder<CommandContext> register() {
        return command("clientConnection").requires(Command::validateAccountOwner)
            .then(literal("autoConnect")
                .then(argument("toggle", toggle()).executes(c -> {
                    com.zenith.util.config.ConfigManager.modify(cfg -> cfg.client.autoConnect = getToggle(c, "toggle"));
                    c.getSource().getEmbed()
                        .title("Auto Connect " + toggleStrCaps(CONFIG.client.autoConnect));
                    return OK;
                })))
            .then(literal("hideusername")
                .then(argument("toggle", toggle()).executes(c -> {
                    com.zenith.util.config.ConfigManager.modify(cfg -> cfg.client.hideusername = getToggle(c, "toggle"));
                    c.getSource().getEmbed()
                        .title("Hide Username " + toggleStrCaps(CONFIG.client.hideusername));
                    return OK;
                })))
            .then(literal("proxy")
                .then(argument("toggle", toggle()).executes(c -> {
                    com.zenith.util.config.ConfigManager.modify(cfg -> cfg.client.connectionProxy.enabled = getToggle(c, "toggle"));
                    c.getSource().getEmbed()
                        .title("Client Connection Proxy " + toggleStrCaps(CONFIG.client.connectionProxy.enabled));
                    return OK;
                }))
                .then(literal("type")
                    .then(argument("type", enumStrings(ProxyInfo.Type.values())).executes(c -> {
                        try {
                            com.zenith.util.config.ConfigManager.modify(cfg -> cfg.client.connectionProxy.type = ProxyInfo.Type.valueOf(getString(c, "type").toUpperCase()));
                            c.getSource().getEmbed()
                                .title("Proxy Type Set");
                            return OK;
                        } catch (final Exception e) {
                            c.getSource().getEmbed()
                                .title("Invalid Proxy Type")
                                .addField("Valid Types", Arrays.toString(ProxyInfo.Type.values()), false);
                            return 0;
                        }
                    })))
                .then(literal("host")
                    .then(argument("host", wordWithChars()).executes(c -> {
                        com.zenith.util.config.ConfigManager.modify(cfg -> cfg.client.connectionProxy.host = getString(c, "host"));
                        c.getSource().getEmbed()
                            .title("Proxy Host Set");
                        return OK;
                    })))
                .then(literal("port")
                    .then(argument("port", integer(1, 65535)).executes(c -> {
                        com.zenith.util.config.ConfigManager.modify(cfg -> cfg.client.connectionProxy.port = getInteger(c, "port"));
                        c.getSource().getEmbed()
                            .title("Proxy Port Set");
                        return OK;
                    })))
                .then(literal("auth").then(literal("clear").executes(c -> {
                    com.zenith.util.config.ConfigManager.modify(cfg -> cfg.client.connectionProxy.user = "");
                    com.zenith.util.config.ConfigManager.modify(cfg -> cfg.client.connectionProxy.password = "");
                    c.getSource().getEmbed()
                        .title("Proxy User and Password Cleared");
                    return OK;
                })))
                .then(literal("user")
                    .then(argument("user", wordWithChars()).executes(c -> {
                        c.getSource().setSensitiveInput(true);
                        com.zenith.util.config.ConfigManager.modify(cfg -> cfg.client.connectionProxy.user = getString(c, "user"));
                        c.getSource().getEmbed()
                            .title("Proxy Username Set");
                        return OK;
                    })))
                .then(literal("password")
                    .then(argument("password", wordWithChars()).executes(c -> {
                        c.getSource().setSensitiveInput(true);
                        com.zenith.util.config.ConfigManager.modify(cfg -> cfg.client.connectionProxy.password = getString(c, "password"));
                        c.getSource().getEmbed()
                            .title("Proxy Password Set");
                        return OK;
                    }))))
            .then(literal("bindAddress")
                .then(argument("address", wordWithChars()).executes(c -> {
                    var address = getString(c, "address");
                    if (!bindAddressPattern.matcher(address).matches()) {
                        c.getSource().getEmbed()
                            .title("Invalid Bind Address")
                            .addField("Valid Format", "Must be formatted like an IP address, e.g. '0.0.0.0'", false);
                        return 0;
                    }
                    com.zenith.util.config.ConfigManager.modify(cfg -> cfg.client.bindAddress = address);
                    c.getSource().getEmbed()
                        .title("Bind Address Set");
                    return OK;
                })))
            .then(literal("timeout")
                .then(argument("toggle", toggle()).executes(c -> {
                    com.zenith.util.config.ConfigManager.modify(cfg -> cfg.client.timeout.enable = getToggle(c, "toggle"));
                    syncTimeout();
                    c.getSource().getEmbed()
                        .title("Client Connection Timeout " + toggleStrCaps(CONFIG.client.timeout.enable));
                    return OK;
                }))
                .then(argument("seconds", integer(10, 120)).executes(c -> {
                    com.zenith.util.config.ConfigManager.modify(cfg -> cfg.client.timeout.seconds = getInteger(c, "seconds"));
                    syncTimeout();
                    c.getSource().getEmbed()
                        .title("Timeout Set");
                    return OK;
                })))
            .then(literal("ping")
                .then(literal("packetInterval").then(argument("seconds", integer(1)).executes(c -> {
                    com.zenith.util.config.ConfigManager.modify(cfg -> cfg.client.ping.pingIntervalSeconds = getInteger(c, "seconds"));
                    c.getSource().getEmbed()
                        .title("Ping Packet Interval Set");
                    return OK;
                }))))
            .then(literal("reconfiguringNotification").then(argument("toggle", toggle()).executes(c -> {
                com.zenith.util.config.ConfigManager.modify(cfg -> cfg.client.extra.reconfiguringNotification = getToggle(c, "toggle"));
                c.getSource().getEmbed()
                    .title("Reconfiguring Notification " + toggleStrCaps(CONFIG.client.extra.reconfiguringNotification));
            })))
            .then(literal("keepAlive")
                .then(literal("mode").then(argument("keepAliveMode", enumStrings(KeepAliveMode.values())).executes(c -> {
                    com.zenith.util.config.ConfigManager.modify(cfg -> cfg.client.keepAliveHandling.keepAliveMode = KeepAliveMode.valueOf(getString(c, "keepAliveMode").toUpperCase()));
                    c.getSource().getEmbed()
                        .title("Keep Alive Mode Set");
                })))
                .then(literal("queueTimeout").then(argument("ms", integer(1)).executes(c -> {
                    com.zenith.util.config.ConfigManager.modify(cfg -> cfg.client.keepAliveHandling.keepAliveQueueTimeoutMs = getInteger(c, "ms"));
                    c.getSource().getEmbed()
                        .title("Keep Alive Queue Timeout Set");
                }))))
            .then(literal("passwordLogin")
                .requires(c -> Command.validateCommandSource(c, CommandSources.TERMINAL))
                .then(argument("toggle", toggle()).executes(c -> {
                    final boolean wasEnabled = CONFIG.server.passwordLogin.enabled;
                    final boolean enabled = getToggle(c, "toggle");
                    com.zenith.util.config.ConfigManager.modify(cfg -> cfg.server.passwordLogin.enabled = enabled);
                    final Embed embed = c.getSource().getEmbed()
                        .title("Password Login " + toggleStrCaps(enabled))
                        .primaryColor();
                    // only kick when the setting actually changes from off to on (re-running `on` is a no-op)
                    if (enabled && !wasEnabled) {
                        if (CONFIG.server.passwordLogin.passwordHash.isEmpty()) {
                            embed.description("Note: no password is set yet. All clients will be rejected until you run `passwordLogin set <password>`.");
                        } else if (com.zenith.feature.passwordlogin.PasswordLoginHelper.passwordLoginActive()) {
                            // tightening: kick connected players so they re-authenticate with the password
                            com.zenith.feature.passwordlogin.PasswordLoginHelper.kickAllConnectedPlayers(
                                "Password login enabled. Please reconnect and log in with the password.",
                                "已啟用密碼登入，請重新連線並輸入密碼。",
                                "已启用密码登录，请重新连接并输入密码。");
                        }
                    } else if (enabled && CONFIG.server.passwordLogin.passwordHash.isEmpty()) {
                        embed.description("Note: no password is set yet. All clients will be rejected until you run `passwordLogin set <password>`.");
                    }
                    return OK;
                }))
                .then(literal("set").then(argument("password", wordWithChars()).executes(c -> {
                    final String password = getString(c, "password");
                    if (password.length() < 4) {
                        c.getSource().getEmbed()
                            .title("Password too short")
                            .description("Password must be at least 4 characters long.")
                            .errorColor();
                        return ERROR;
                    }
                    c.getSource().setSensitiveInput(true);
                    final String previousHash = CONFIG.server.passwordLogin.passwordHash;
                    // only the hash is stored, never the plaintext password
                    com.zenith.util.config.ConfigManager.modify(cfg -> cfg.server.passwordLogin.passwordHash = com.zenith.feature.passwordlogin.PasswordLoginHelper.sha256Hex(password));
                    if (!CONFIG.server.passwordLogin.passwordHash.equals(previousHash)) {
                        // password changed: reset all per-IP wrong-password counters
                        com.zenith.feature.passwordlogin.PasswordLoginHelper.clearAllFailedAttempts();
                    }
                    final Embed embed = c.getSource().getEmbed()
                        .title("Password Set")
                        .description("Only a SHA-256 hash of the password is stored.")
                        .primaryColor();
                    // only kick when the password actually changed (re-entering the same password is a no-op)
                    if (!CONFIG.server.passwordLogin.passwordHash.equals(previousHash)
                        && com.zenith.feature.passwordlogin.PasswordLoginHelper.passwordLoginActive()) {
                        // password changed: kick connected players so they re-authenticate with the new password
                        com.zenith.feature.passwordlogin.PasswordLoginHelper.kickAllConnectedPlayers(
                            "Password changed. Please reconnect and log in with the new password.",
                            "密碼已變更，請重新連線並輸入新密碼。",
                            "密码已变更，请重新连接并输入新密码。");
                    }
                    return OK;
                })))
                .then(literal("allowUnencrypted").then(argument("toggle", toggle()).executes(c -> {
                    com.zenith.util.config.ConfigManager.modify(cfg -> cfg.server.passwordLogin.allowUnencryptedLegacy = getToggle(c, "toggle"));
                    final Embed embed = c.getSource().getEmbed();
                    if (CONFIG.server.passwordLogin.allowUnencryptedLegacy) {
                        embed.title("Allow Unencrypted Legacy Login On")
                            .description("WARNING: clients 1.20.2-1.20.4 do not support the encrypted handshake and their password will be sent in plaintext, vulnerable to man-in-the-middle interception.")
                            .errorColor();
                    } else {
                        embed.title("Allow Unencrypted Legacy Login Off")
                            .primaryColor();
                    }
                    return OK;
                })))
                .then(literal("status").executes(c -> {
                    final boolean pwSet = !CONFIG.server.passwordLogin.passwordHash.isEmpty();
                    final Embed embed = c.getSource().getEmbed()
                        .title("Password Login")
                        .addField("Enabled", toggleStr(CONFIG.server.passwordLogin.enabled))
                        .addField("Password Set", toggleStr(pwSet))
                        .addField("Timeout (s)", CONFIG.server.passwordLogin.timeoutSeconds)
                        .addField("Allow Unencrypted Legacy (1.20.2-1.20.4)", toggleStr(CONFIG.server.passwordLogin.allowUnencryptedLegacy))
                        .primaryColor();
                    if (CONFIG.server.passwordLogin.enabled && !pwSet) {
                        embed.description("WARNING: no password set - all clients will be rejected. Run `passwordLogin set <password>`.");
                    }
                    return OK;
                }))
                .then(literal("lockout")
                    .then(argument("toggle", toggle()).executes(c -> {
                        final boolean enabled = getToggle(c, "toggle");
                        com.zenith.util.config.ConfigManager.modify(cfg -> cfg.server.passwordLogin.lockoutEnabled = enabled);
                        c.getSource().getEmbed()
                            .title("Password Lockout " + toggleStrCaps(enabled))
                            .description("Wrong-password brute-force protection.")
                            .primaryColor();
                        return OK;
                    }))
                    .then(literal("threshold").then(argument("count", integer(1)).executes(c -> {
                        com.zenith.util.config.ConfigManager.modify(cfg -> cfg.server.passwordLogin.lockoutThreshold = getInteger(c, "count"));
                        c.getSource().getEmbed()
                            .title("Lockout Threshold Set")
                            .description("Cooldown starts from wrong-password attempt #" + (CONFIG.server.passwordLogin.lockoutThreshold + 1) + ".")
                            .primaryColor();
                        return OK;
                    })))
                    .then(literal("baseDelay").then(argument("seconds", integer(1, 86400)).executes(c -> {
                        com.zenith.util.config.ConfigManager.modify(cfg -> cfg.server.passwordLogin.lockoutBaseDelaySeconds = getInteger(c, "seconds"));
                        c.getSource().getEmbed()
                            .title("Lockout Base Delay Set")
                            .description("First cooldown is " + CONFIG.server.passwordLogin.lockoutBaseDelaySeconds + "s.")
                            .primaryColor();
                        return OK;
                    })))
                    .then(literal("multiplier").then(argument("factor", doubleArg(1.0)).executes(c -> {
                        com.zenith.util.config.ConfigManager.modify(cfg -> cfg.server.passwordLogin.lockoutMultiplier = getDouble(c, "factor"));
                        c.getSource().getEmbed()
                            .title("Lockout Multiplier Set")
                            .description("Cooldown is multiplied by " + CONFIG.server.passwordLogin.lockoutMultiplier + " per further failure.")
                            .primaryColor();
                        return OK;
                    })))
                    .then(literal("maxDelay").then(argument("seconds", integer(60, 172800)).executes(c -> {
                        com.zenith.util.config.ConfigManager.modify(cfg -> cfg.server.passwordLogin.lockoutMaxDelaySeconds = getInteger(c, "seconds"));
                        c.getSource().getEmbed()
                            .title("Lockout Max Delay Set")
                            .description("Cooldown is capped at " + CONFIG.server.passwordLogin.lockoutMaxDelaySeconds + "s (" + (CONFIG.server.passwordLogin.lockoutMaxDelaySeconds / 3600.0) + "h).")
                            .primaryColor();
                        return OK;
                    })))
                    .then(literal("status").executes(c -> {
                        final var pwl = CONFIG.server.passwordLogin;
                        c.getSource().getEmbed()
                            .title("Password Lockout")
                            .addField("Enabled", toggleStr(pwl.lockoutEnabled))
                            .addField("Threshold", pwl.lockoutThreshold)
                            .addField("Base Delay (s)", pwl.lockoutBaseDelaySeconds)
                            .addField("Multiplier", pwl.lockoutMultiplier)
                            .addField("Max Delay (s)", pwl.lockoutMaxDelaySeconds)
                            .addField("Tracked IPs", pwl.failedLoginAttempts.size())
                            .primaryColor();
                        return OK;
                    }))
                    .then(literal("clear").executes(c -> {
                        com.zenith.feature.passwordlogin.PasswordLoginHelper.clearAllFailedAttempts();
                        c.getSource().getEmbed()
                            .title("Lockout Records Cleared")
                            .primaryColor();
                        return OK;
                    }))))
            .then(literal("nameProtection")
                .requires(c -> Command.validateCommandSource(c, CommandSources.TERMINAL))
                .then(argument("toggle", toggle()).executes(c -> {
                    final boolean wasEnabled = CONFIG.server.nameProtection.enable;
                    final boolean enabled = getToggle(c, "toggle");
                    com.zenith.util.config.ConfigManager.modify(cfg -> cfg.server.nameProtection.enable = enabled);
                    final Embed embed = c.getSource().getEmbed()
                        .title("Name Protection " + toggleStrCaps(enabled))
                        .description("Connecting clients must use a username matching the proxy account username.")
                        .primaryColor();
                    // only kick when the setting actually changes from off to on (re-running `on` is a no-op)
                    if (enabled && !wasEnabled && com.zenith.feature.passwordlogin.PasswordLoginHelper.nameProtectionActive()) {
                        // tightening: kick connected players whose identity does not match
                        com.zenith.feature.passwordlogin.PasswordLoginHelper.kickNameMismatchedPlayers();
                    }
                    return OK;
                })))
            .then(literal("tempPassword")
                .requires(c -> Command.validateCommandSource(c, CommandSources.TERMINAL))
                .then(literal("add").then(argument("minutes", integer(1)).executes(c -> {
                    final int minutes = getInteger(c, "minutes");
                    final String password = com.zenith.feature.passwordlogin.PasswordLoginHelper.generateTempPassword(minutes);
                    c.getSource().setSensitiveInput(true);
                    c.getSource().getEmbed()
                        .title("Temporary Password Generated")
                        .addField("Password", password)
                        .addField("Valid For", minutes + " minutes")
                        .primaryColor();
                    return OK;
                })))
                .then(literal("list").executes(c -> {
                    final var tempPasswords = com.zenith.feature.passwordlogin.PasswordLoginHelper.listTempPasswords();
                    final Embed embed = c.getSource().getEmbed()
                        .title("Temporary Passwords")
                        .primaryColor();
                    if (tempPasswords.isEmpty()) embed.description("No active temporary passwords.");
                    else embed.description(String.join("\n", tempPasswords));
                    return OK;
                }))
                .then(literal("remove").then(argument("id", wordWithChars()).executes(c -> {
                    final String id = getString(c, "id");
                    if (com.zenith.feature.passwordlogin.PasswordLoginHelper.removeTempPassword(id)) {
                        c.getSource().getEmbed()
                            .title("Temporary Password Removed")
                            .primaryColor();
                    } else {
                        c.getSource().getEmbed()
                            .title("Temporary Password Not Found")
                            .errorColor();
                    }
                    return OK;
                })))
                .then(literal("denied")
                    .then(literal("add").then(argument("command", wordWithChars()).executes(c -> {
                        final String command = getString(c, "command").toLowerCase();
                        if (com.zenith.feature.passwordlogin.PasswordLoginHelper.addDeniedTempCommand(command)) {
                            c.getSource().getEmbed()
                                .title("Command Denied For Temporary Passwords")
                                .addField("Command", command)
                                .primaryColor();
                        } else {
                            c.getSource().getEmbed()
                                .title("Command Already Denied")
                                .errorColor();
                        }
                        return OK;
                    })))
                    .then(literal("remove").then(argument("command", wordWithChars()).executes(c -> {
                        final String command = getString(c, "command").toLowerCase();
                        if (com.zenith.feature.passwordlogin.PasswordLoginHelper.removeDeniedTempCommand(command)) {
                            c.getSource().getEmbed()
                                .title("Command Allowed For Temporary Passwords")
                                .addField("Command", command)
                                .primaryColor();
                        } else {
                            c.getSource().getEmbed()
                                .title("Command Not In Denied List")
                                .errorColor();
                        }
                        return OK;
                    })))
                    .then(literal("list").executes(c -> {
                        final var denied = com.zenith.feature.passwordlogin.PasswordLoginHelper.listDeniedTempCommands();
                        final Embed embed = c.getSource().getEmbed()
                            .title("Commands Denied For Temporary Passwords")
                            .primaryColor();
                        if (denied.isEmpty()) embed.description("None.");
                        else embed.description(String.join(", ", denied));
                        return OK;
                    }))));
    }

    private void syncTimeout() {
        int t = CONFIG.client.timeout.enable ? CONFIG.client.timeout.seconds : 0;
        var client = Proxy.getInstance().getClient();
        if (client == null) return;
        client.setReadTimeout(t);
    }

    @Override
    public void defaultEmbed(final Embed embed) {
        embed
            .primaryColor()
            .addField("Auto Connect", toggleStr(CONFIG.client.autoConnect))
            .addField("Hide Username", toggleStr(CONFIG.client.hideusername))
            .addField("Proxy", toggleStr(CONFIG.client.connectionProxy.enabled))
            .addField("Proxy Type", CONFIG.client.connectionProxy.type.toString())
            .addField("Proxy Host", CONFIG.client.connectionProxy.host)
            .addField("Proxy Port", String.valueOf(CONFIG.client.connectionProxy.port))
            .addField("Authentication", CONFIG.client.connectionProxy.password.isEmpty() && CONFIG.client.connectionProxy.user.isEmpty()
                ? "Off" : "On")
            .addField("Bind Address", CONFIG.client.bindAddress)
            .addField("Timeout", CONFIG.client.timeout.enable ? CONFIG.client.timeout.seconds : toggleStr(false))
            .addField("Ping Packet Interval", CONFIG.client.ping.pingIntervalSeconds + "s")
            .addField("KeepAlive Mode", CONFIG.client.keepAliveHandling.keepAliveMode.name().toLowerCase())
            .addField("KeepAlive Queue Timeout", CONFIG.client.keepAliveHandling.keepAliveQueueTimeoutMs + "ms")
            .addField("Password Login", toggleStr(CONFIG.server.passwordLogin.enabled))
            .addField("Password Login Allow Unencrypted", toggleStr(CONFIG.server.passwordLogin.allowUnencryptedLegacy))
            .addField("Name Protection", toggleStr(CONFIG.server.nameProtection.enable));
    }
}
