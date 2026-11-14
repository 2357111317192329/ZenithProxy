package com.zenith.feature.passwordlogin;

/**
 * Brute-force protection state for a single client IP.
 * Only the fail count and the cooldown window are tracked - the IP is the map key.
 */
public class LoginAttemptRecord {
    // total wrong-password attempts from this IP (login aborts / cancels do not count)
    public int failCount = 0;
    // epoch ms until which login attempts from this IP are immediately rejected
    public long cooldownUntilEpochMs = 0;

    public LoginAttemptRecord() {}
}