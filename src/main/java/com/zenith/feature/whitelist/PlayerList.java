package com.zenith.feature.whitelist;

import com.zenith.cache.data.entity.EntityPlayer;
import org.geysermc.mcprotocollib.auth.GameProfile;
import org.geysermc.mcprotocollib.protocol.data.game.PlayerListEntry;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import static com.zenith.Globals.CACHE;
import static com.zenith.feature.whitelist.PlayerListsManager.createPlayerListEntry;

public record PlayerList(
    String name,
    ArrayList<PlayerEntry> entries // reference to list in Config
) {
    public synchronized Optional<PlayerEntry> add(final String username) {
        final Optional<PlayerEntry> playerListEntryOptional = createPlayerListEntry(username);
        if (playerListEntryOptional.isPresent()) {
            var playerListEntry = playerListEntryOptional.get();
            if (!contains(playerListEntry.getUsername(), playerListEntry.getUuid())) {
                entries.add(playerListEntry);
            }
        }
        return playerListEntryOptional;
    }

    // returns true if the account was added. false if the account was already present
    public synchronized boolean add(final String username, final UUID uuid) {
        var entry = new PlayerEntry(username, uuid, Instant.now().getEpochSecond());
        if (!contains(username, uuid)) {
            entries.add(entry);
            return true;
        }
        return false;
    }

    public synchronized void remove(final String username) {
        if (username == null) return;
        this.entries.removeIf(entry -> entry.getUsername() != null && entry.getUsername().equalsIgnoreCase(username));
    }

    public synchronized void remove(final UUID uuid) {
        if (uuid == null) return;
        this.entries.removeIf(entry -> Objects.equals(entry.getUuid(), uuid));
    }

    public synchronized void remove(final String username, final UUID uuid) {
        if (username != null && !username.isBlank()) {
            remove(username);
        } else if (uuid != null) {
            remove(uuid);
        }
    }

    public synchronized void clear() {
        entries.clear();
    }

    // todo: these lookups could be sped up with secondary hashmaps
    //  we'd have to be very careful to keep those in sync
    //  as is, this shouldn't be too impactful for the extra complexity. O(n) loops are generally pretty fast at small sizes

    public synchronized boolean contains(final String name, final UUID uuid) {
        if (name != null && !name.isBlank()) {
            return contains(name);
        }
        if (uuid != null) {
            return contains(uuid);
        }
        return false;
    }

    public synchronized boolean contains(final GameProfile clientGameProfile) {
        if (clientGameProfile == null) return false;
        final String name = clientGameProfile.getName();
        final UUID uuid = clientGameProfile.getId();

        if (name != null && !name.isBlank()) {
            for (int i = 0; i < entries.size(); i++) {
                final PlayerEntry entry = entries.get(i);
                if (entry.getUsername() != null && name.equalsIgnoreCase(entry.getUsername())) {
                    entry.setLastRefreshed(Instant.now().getEpochSecond());
                    if (uuid != null) {
                        entry.setUuid(uuid);
                    }
                    return true;
                }
            }
            return false;
        } else if (uuid != null) {
            for (int i = 0; i < entries.size(); i++) {
                final PlayerEntry entry = entries.get(i);
                if (Objects.equals(entry.getUuid(), uuid)) {
                    entry.setLastRefreshed(Instant.now().getEpochSecond());
                    return true;
                }
            }
            return false;
        }
        return false;
    }

    public synchronized boolean contains(final PlayerListEntry playerListEntry) {
        if (playerListEntry == null) return false;
        return contains(playerListEntry.getName(), playerListEntry.getProfileId());
    }

    public synchronized boolean contains(final EntityPlayer entityPlayer) {
        if (entityPlayer == null) return false;
        if (entityPlayer.getUuid() == null) return false;
        var tablistEntry = CACHE.getTabListCache().get(entityPlayer.getUuid());
        if (tablistEntry.isPresent()) {
            return contains(tablistEntry.get().getName(), entityPlayer.getUuid());
        }
        return contains(null, entityPlayer.getUuid());
    }

    public synchronized boolean contains(final UUID uuid) {
        if (uuid == null) return false;
        for (int i = 0; i < entries.size(); i++) {
            final PlayerEntry entry = entries.get(i);
            if (Objects.equals(entry.getUuid(), uuid))
                return true;
        }
        return false;
    }

    public synchronized boolean contains(final String name) {
        if (name == null || name.isBlank()) return false;
        for (int i = 0; i < entries.size(); i++) {
            final PlayerEntry entry = entries.get(i);
            if (entry.getUsername() != null && name.equalsIgnoreCase(entry.getUsername()))
                return true;
        }
        return false;
    }
}
