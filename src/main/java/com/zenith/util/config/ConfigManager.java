package com.zenith.util.config;

import com.zenith.network.server.ServerSession;

import java.util.function.Consumer;

import static com.zenith.Globals.CONFIG;
import static com.zenith.Globals.saveConfigAsync;

/**
 * Unified entry point for modifying persistent configuration.
 *
 * All handlers that change settings should go through {@link #modify(Consumer)} so that the
 * modification can be intercepted at write time - e.g. players authenticated with a temporary
 * password are not allowed to change any settings: the change is refused, the player is notified,
 * and a {@link ConfigChangeBlockedException} is thrown so the rest of the handler (including any
 * "change succeeded" output) does not run.
 *
 * The write is performed as a plain statement (no return value required from the caller), so both
 * void and int command handler lambdas can use it unchanged.
 */
public final class ConfigManager {
    private static final ThreadLocal<ServerSession> TEMP_ACCOUNT_CONTEXT = new ThreadLocal<>();

    private ConfigManager() {}

    /**
     * Sets (or clears, with {@code null}) the session whose in-game command execution is currently
     * running. While set, {@link #modify(Consumer)} will refuse all config changes.
     */
    public static void setTempAccountContext(final ServerSession session) {
        if (session == null) TEMP_ACCOUNT_CONTEXT.remove();
        else TEMP_ACCOUNT_CONTEXT.set(session);
    }

    public static boolean isTempAccountExecuting() {
        return TEMP_ACCOUNT_CONTEXT.get() != null;
    }

    /**
     * Performs a config change unless a temporary-password account is currently executing a command.
     *
     * @param change the modification to apply (e.g. {@code cfg -> cfg.client.extra.antiafk.enabled = true})
     */
    public static void modify(final Consumer<Config> change) {
        final ServerSession tempAccount = TEMP_ACCOUNT_CONTEXT.get();
        if (tempAccount != null) {
            tempAccount.sendAsyncAlert(com.zenith.feature.passwordlogin.PasswordLoginHelper.localize(tempAccount,
                "<red>Temporary password accounts cannot modify settings.",
                "<red>臨時密碼帳號無法修改設定。",
                "<red>临时密码账号无法修改设置。"));
            throw new ConfigChangeBlockedException();
        }
        change.accept(CONFIG);
        saveConfigAsync();
    }

    /**
     * Thrown when a temporary-password account attempts a config modification.
     * Deliberately unchecked so it propagates straight through brigadier to the executor,
     * aborting the rest of the command handler.
     */
    public static final class ConfigChangeBlockedException extends RuntimeException {
        public ConfigChangeBlockedException() {
            super("Config change blocked for temporary password account");
        }
    }
}