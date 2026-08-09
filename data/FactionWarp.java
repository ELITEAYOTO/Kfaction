package me.krunsh.kfaction.data;

import java.util.Locale;

import org.bukkit.Location;

/**
 * Warp de faction V2.
 *
 * Le mot de passe n'est jamais conservé en clair. passwordHash contient
 * uniquement le format produit par WarpPasswordHasher.
 */
public final class FactionWarp {

    private final String name;
    private final StoredLocation location;
    private final String passwordHash;
    private final long createdAt;
    private final String createdBy;
    private final long updatedAt;

    public FactionWarp(
            String name,
            StoredLocation location,
            String passwordHash,
            long createdAt,
            String createdBy,
            long updatedAt
    ) {
        String normalized =
                normalizeName(name);

        if (normalized == null) {
            throw new IllegalArgumentException(
                    "warp name cannot be empty"
            );
        }

        if (location == null) {
            throw new IllegalArgumentException(
                    "warp location cannot be null"
            );
        }

        long now =
                System.currentTimeMillis();

        this.name = normalized;
        this.location = location;
        this.passwordHash =
                normalizeSecretHash(passwordHash);
        this.createdAt =
                createdAt > 0L
                        ? createdAt
                        : now;
        this.createdBy =
                normalizeText(createdBy);
        this.updatedAt =
                updatedAt > 0L
                        ? updatedAt
                        : this.createdAt;
    }

    public static FactionWarp legacy(
            String name,
            StoredLocation location
    ) {
        return new FactionWarp(
                name,
                location,
                null,
                System.currentTimeMillis(),
                null,
                System.currentTimeMillis()
        );
    }

    public String getName() {
        return name;
    }

    public StoredLocation getStoredLocation() {
        return location;
    }

    public Location getLocation() {
        return location.toBukkitLocation();
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public boolean isPasswordProtected() {
        return passwordHash != null;
    }

    public long getCreatedAt() {
        return createdAt;
    }

    public String getCreatedBy() {
        return createdBy;
    }

    public long getUpdatedAt() {
        return updatedAt;
    }

    public FactionWarp withLocation(
            StoredLocation newLocation,
            String actorName
    ) {
        return new FactionWarp(
                name,
                newLocation,
                passwordHash,
                createdAt,
                createdBy != null
                        ? createdBy
                        : actorName,
                System.currentTimeMillis()
        );
    }

    public FactionWarp withPasswordHash(
            String newPasswordHash
    ) {
        return new FactionWarp(
                name,
                location,
                newPasswordHash,
                createdAt,
                createdBy,
                System.currentTimeMillis()
        );
    }

    public boolean sameSecurityAndDestination(
            FactionWarp other
    ) {
        if (other == null) {
            return false;
        }

        if (!name.equals(other.name)
                || !location.samePosition(
                        other.location
                )) {
            return false;
        }

        return passwordHash == null
                ? other.passwordHash == null
                : passwordHash.equals(
                        other.passwordHash
                );
    }

    private static String normalizeName(
            String value
    ) {
        if (value == null) {
            return null;
        }

        String normalized =
                value.trim()
                        .toLowerCase(
                                Locale.ROOT
                        );

        return normalized.isEmpty()
                ? null
                : normalized;
    }

    private static String normalizeText(
            String value
    ) {
        if (value == null) {
            return null;
        }

        String normalized =
                value.trim();

        return normalized.isEmpty()
                ? null
                : normalized;
    }

    private static String normalizeSecretHash(
            String value
    ) {
        return normalizeText(value);
    }

    @Override
    public String toString() {
        return "FactionWarp{" +
                "name='" + name + '\'' +
                ", world='" + location.getWorldName() + '\'' +
                ", protected=" + isPasswordProtected() +
                '}';
    }
}
