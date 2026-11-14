package com.zenith.feature.passwordlogin;

/**
 * A temporary password that can be used to log in for a limited validity window.
 * Only the SHA-256 hash is ever stored - the plaintext is shown once to the console operator.
 */
public class TemporaryPassword {
    public String id;
    public String passwordHash;
    public long expiresAtEpochMs;

    public TemporaryPassword() {}

    public TemporaryPassword(final String id, final String passwordHash, final long expiresAtEpochMs) {
        this.id = id;
        this.passwordHash = passwordHash;
        this.expiresAtEpochMs = expiresAtEpochMs;
    }
}