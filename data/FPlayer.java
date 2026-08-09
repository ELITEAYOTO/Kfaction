package me.krunsh.kfaction.data;

import java.util.Objects;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

/**
 * Représente un joueur dans le système de faction
 * Stocke les données persistantes du joueur
 */
public class FPlayer {
    
    // === Identifiants ===
    private final UUID uuid;
    private String lastKnownName;
    
    // === Faction ===
    private String factionId;
    private FactionRole role;
    
    // === Power ===
    private double power;
    private double maxPower;
    private long lastPowerUpdate;
    
    // === État ===
    private boolean autoClaimEnabled;
    private boolean mapAutoUpdateEnabled;
    private boolean seeingChunks;
    private boolean flyEnabled;
    private boolean spying; // Admin spy mode for faction chat (legacy - deprecated)
    private String spyingFactionId; // Faction ID being spied (null = not spying)
    private ChatMode chatMode;
    
    // === Métadonnées ===
    private long firstJoin;
    private long lastSeen;
    private int kills;
    private int deaths;
    
    // === Invitations ===
    private String pendingInvite;
    private long inviteTimestamp;
    
    /**
     * Mode de chat du joueur
     */
    public enum ChatMode {
        PUBLIC,      // Chat global normal
        FACTION,     // Chat faction seulement
        ALLY,        // Chat allié
        TRUCE        // Chat trêve
    }
    
    /**
     * Crée un nouveau FPlayer
     * @param uuid UUID du joueur Minecraft
     */
    public FPlayer(UUID uuid) {
        this.uuid = uuid;
        this.lastKnownName = "";
        this.factionId = null;
        this.role = null;
        this.power = 10.0; // Valeur par défaut, sera configurée
        this.maxPower = 10.0;
        this.lastPowerUpdate = System.currentTimeMillis();
        this.autoClaimEnabled = false;
        this.mapAutoUpdateEnabled = false;
        this.seeingChunks = false;
        this.flyEnabled = false;
        this.spying = false;
        this.spyingFactionId = null;
        this.chatMode = ChatMode.PUBLIC;
        this.firstJoin = System.currentTimeMillis();
        this.lastSeen = System.currentTimeMillis();
        this.kills = 0;
        this.deaths = 0;
        this.pendingInvite = null;
        this.inviteTimestamp = 0;
    }
    
    /**
     * Crée un FPlayer à partir d'un joueur en ligne
     * @param player Le joueur Bukkit
     */
    public FPlayer(Player player) {
        this(player.getUniqueId());
        this.lastKnownName = player.getName();
    }
    
    // === Getters & Setters de base ===
    
    public UUID getUuid() {
        return uuid;
    }
    
    public String getLastKnownName() {
        return lastKnownName;
    }
    
    public void setLastKnownName(String name) {
        this.lastKnownName = name;
    }
    
    /**
     * @return Le joueur Bukkit s'il est en ligne, sinon null
     */
    public Player getPlayer() {
        return Bukkit.getPlayer(uuid);
    }
    
    /**
     * @return true si le joueur est en ligne
     */
    public boolean isOnline() {
        Player player = getPlayer();
        return player != null && player.isOnline();
    }
    
    /**
     * Met à jour le nom depuis le joueur en ligne
     */
    public void updateName() {
        Player player = getPlayer();
        if (player != null) {
            this.lastKnownName = player.getName();
        }
    }
    
    // === Faction ===
    
    public String getFactionId() {
        return factionId;
    }
    
    public void setFactionId(String factionId) {
        this.factionId = factionId;
    }
    
    public boolean hasFaction() {
        return factionId != null && !factionId.isEmpty();
    }
    
    public FactionRole getRole() {
        return role;
    }
    
    public void setRole(FactionRole role) {
        this.role = role;
    }
    
    /**
     * Vérifie si le joueur a au moins le rang spécifié
     * @param minRole Rang minimum requis
     * @return true si le joueur a le rang ou supérieur
     */
    public boolean hasMinRole(FactionRole minRole) {
        if (role == null) return false;
        return role.isAtLeast(minRole);
    }
    
    /**
     * @return true si le joueur est le leader de sa faction
     */
    public boolean isLeader() {
        return role == FactionRole.LEADER;
    }
    
    /**
     * @return true si le joueur est au moins modérateur
     */
    public boolean isModerator() {
        return hasMinRole(FactionRole.MODERATOR);
    }
    
    /**
     * Retire le joueur de sa faction actuelle
     */
    public void leaveFaction() {
        this.factionId = null;
        this.role = null;
    }
    
    /**
     * Rejoint une faction avec un rôle
     * @param factionId ID de la faction
     * @param role Rôle dans la faction
     */
    public void joinFaction(String factionId, FactionRole role) {
        this.factionId = factionId;
        this.role = role;
    }
    
    // === Power ===
    
    public double getPower() {
        return power;
    }
    
    /**
     * Setter legacy de stockage.
     *
     * Le minimum métier appartient à PowerManager (power.min peut être
     * négatif). Cette classe ne doit donc plus écraser un power -5 en 0 lors
     * d'une restauration SQLite/JSON.
     */
    public void setPower(double power) {
        double safePower =
                Double.isNaN(power)
                        ? 0.0D
                        : power;

        this.power =
                Math.min(
                        safePower,
                        maxPower
                );

        this.lastPowerUpdate =
                System.currentTimeMillis();
    }

    /**
     * Mutation bornée par un maximum EFFECTIF calculé par PowerManager.
     *
     * Le champ historique maxPower reste le maximum de base configuré.
     * Cette méthode permet aux bonus de permission d'augmenter réellement
     * le plafond runtime sans réécrire le maxPower persistant à chaque lecture.
     */
    public void setPowerWithEffectiveMax(
            double power,
            double minimum,
            double effectiveMaximum
    ) {
        double safeMin =
                Double.isNaN(minimum)
                        ? 0.0D
                        : minimum;

        double safeMax =
                Double.isNaN(effectiveMaximum)
                        ? maxPower
                        : Math.max(
                                safeMin,
                                effectiveMaximum
                        );

        double safePower =
                Double.isNaN(power)
                        ? safeMin
                        : power;

        this.power =
                Math.max(
                        safeMin,
                        Math.min(
                                safePower,
                                safeMax
                        )
                );

        this.lastPowerUpdate =
                System.currentTimeMillis();
    }
    
    public double getMaxPower() {
        return maxPower;
    }
    
    public void setMaxPower(double maxPower) {
        this.maxPower = maxPower;
        // Ajuster le power actuel si nécessaire
        if (this.power > maxPower) {
            this.power = maxPower;
        }
    }
    
    /**
     * Ajoute du power au joueur (respecte le max)
     * @param amount Quantité à ajouter
     */
    public void addPower(double amount) {
        setPower(this.power + amount);
    }
    
    /**
     * Retire du power au joueur
     * @param amount Quantité à retirer
     */
    public void removePower(double amount) {
        setPower(this.power - amount);
    }
    
    /**
     * @return Le power arrondi pour l'affichage
     */
    public int getPowerRounded() {
        return (int) Math.round(power);
    }
    
    /**
     * @return Le max power arrondi pour l'affichage
     */
    public int getMaxPowerRounded() {
        return (int) Math.round(maxPower);
    }
    
    public long getLastPowerUpdate() {
        return lastPowerUpdate;
    }
    
    public void setLastPowerUpdate(long timestamp) {
        this.lastPowerUpdate = timestamp;
    }
    
    /**
     * @return Le pourcentage de power actuel (0-100)
     */
    public double getPowerPercentage() {
        if (maxPower <= 0) return 0;
        return (power / maxPower) * 100;
    }
    
    /**
     * @return true si le power est au maximum
     */
    public boolean isAtMaxPower() {
        return power >= maxPower;
    }
    
    // === État (toggles) ===
    
    private boolean bypassing = false;
    
    /**
     * @return true si le joueur est en mode bypass admin
     */
    public boolean isBypassing() {
        return bypassing;
    }
    
    /**
     * Définit le mode bypass admin
     * @param bypassing true pour activer le bypass
     */
    public void setBypassing(boolean bypassing) {
        this.bypassing = bypassing;
    }
    
    /**
     * Bascule le mode bypass admin
     */
    public void toggleBypassing() {
        this.bypassing = !this.bypassing;
    }
    
    public boolean isAutoClaimEnabled() {
        return autoClaimEnabled;
    }
    
    public void setAutoClaimEnabled(boolean enabled) {
        this.autoClaimEnabled = enabled;
    }
    
    public void toggleAutoClaim() {
        this.autoClaimEnabled = !this.autoClaimEnabled;
    }
    
    public boolean isMapAutoUpdateEnabled() {
        return mapAutoUpdateEnabled;
    }
    
    public void setMapAutoUpdateEnabled(boolean enabled) {
        this.mapAutoUpdateEnabled = enabled;
    }
    
    public void toggleMapAutoUpdate() {
        this.mapAutoUpdateEnabled = !this.mapAutoUpdateEnabled;
    }
    
    public boolean isSeeingChunks() {
        return seeingChunks;
    }
    
    public void setSeeingChunks(boolean seeing) {
        this.seeingChunks = seeing;
    }
    
    public void toggleSeeingChunks() {
        this.seeingChunks = !this.seeingChunks;
    }
    
    public boolean isFlyEnabled() {
        return flyEnabled;
    }
    
    public void setFlyEnabled(boolean enabled) {
        this.flyEnabled = enabled;
    }
    
    public void toggleFly() {
        this.flyEnabled = !this.flyEnabled;
    }
    
    public boolean isSpying() {
        return spying || spyingFactionId != null;
    }
    
    public void setSpying(boolean spying) {
        this.spying = spying;
        if (!spying) {
            this.spyingFactionId = null;
        }
    }
    
    public void toggleSpying() {
        this.spying = !this.spying;
        if (!this.spying) {
            this.spyingFactionId = null;
        }
    }
    
    // === Spy Faction Spécifique ===
    
    /**
     * @return L'ID de la faction actuellement espionnée, ou null
     */
    public String getSpyingFactionId() {
        return spyingFactionId;
    }
    
    /**
     * Définit la faction à espionner
     * @param factionId ID de la faction (null pour arrêter)
     */
    public void setSpyingFactionId(String factionId) {
        this.spyingFactionId = factionId;
        this.spying = (factionId != null);
    }
    
    /**
     * Vérifie si le joueur espionne une faction spécifique
     * @param factionId ID de la faction à vérifier
     * @return true si le joueur espionne cette faction
     */
    public boolean isSpyingFaction(String factionId) {
        if (factionId == null) return false;
        return factionId.equals(this.spyingFactionId);
    }
    
    /**
     * Arrête d'espionner
     */
    public void stopSpying() {
        this.spyingFactionId = null;
        this.spying = false;
    }
    
    /**
     * @return true si le joueur espionne une faction
     */
    public boolean isSpyingAnyFaction() {
        return spyingFactionId != null;
    }
    
    public ChatMode getChatMode() {
        return chatMode;
    }
    
    public void setChatMode(ChatMode mode) {
        this.chatMode = mode;
    }
    
    /**
     * Cycle entre les modes de chat
     */
    public void cycleChatMode() {
        ChatMode[] modes = ChatMode.values();
        int currentIndex = chatMode.ordinal();
        int nextIndex = (currentIndex + 1) % modes.length;
        this.chatMode = modes[nextIndex];
    }
    
    // === Métadonnées ===
    
    public long getFirstJoin() {
        return firstJoin;
    }
    
    public void setFirstJoin(long timestamp) {
        this.firstJoin = timestamp;
    }
    
    public long getLastSeen() {
        return lastSeen;
    }
    
    public void setLastSeen(long timestamp) {
        this.lastSeen = timestamp;
    }
    
    /**
     * Met à jour la date de dernière connexion
     */
    public void updateLastSeen() {
        this.lastSeen = System.currentTimeMillis();
    }
    
    /**
     * @return Temps d'inactivité en millisecondes
     */
    public long getInactivityTime() {
        return System.currentTimeMillis() - lastSeen;
    }
    
    /**
     * @return Temps d'inactivité en jours
     */
    public int getInactivityDays() {
        return (int) (getInactivityTime() / (1000L * 60 * 60 * 24));
    }
    
    public int getKills() {
        return kills;
    }
    
    public void setKills(int kills) {
        this.kills = kills;
    }
    
    public void addKill() {
        this.kills++;
    }
    
    public int getDeaths() {
        return deaths;
    }
    
    public void setDeaths(int deaths) {
        this.deaths = deaths;
    }
    
    public void addDeath() {
        this.deaths++;
    }
    
    /**
     * @return Ratio K/D (0 si aucune mort)
     */
    public double getKDRatio() {
        if (deaths == 0) return kills;
        return (double) kills / deaths;
    }
    
    /**
     * @return Ratio K/D formaté
     */
    public String getKDRatioFormatted() {
        return String.format("%.2f", getKDRatio());
    }
    
    // === Invitations ===
    
    public String getPendingInvite() {
        return pendingInvite;
    }
    
    public void setPendingInvite(String factionId) {
        this.pendingInvite = factionId;
        this.inviteTimestamp = System.currentTimeMillis();
    }
    
    public void clearPendingInvite() {
        this.pendingInvite = null;
        this.inviteTimestamp = 0;
    }
    
    /**
     * Vérifie si une invitation est en attente et valide
     * @param expirationMs Temps d'expiration en millisecondes
     * @return true si une invitation valide existe
     */
    public boolean hasPendingInvite(long expirationMs) {
        if (pendingInvite == null) return false;
        if (System.currentTimeMillis() - inviteTimestamp > expirationMs) {
            clearPendingInvite();
            return false;
        }
        return true;
    }
    
    /**
     * Vérifie si l'invitation est pour une faction spécifique
     * @param factionId ID de la faction
     * @param expirationMs Temps d'expiration
     * @return true si l'invitation correspond
     */
    public boolean hasInviteFrom(String factionId, long expirationMs) {
        if (!hasPendingInvite(expirationMs)) return false;
        return factionId.equals(pendingInvite);
    }
    
    public long getInviteTimestamp() {
        return inviteTimestamp;
    }
    
    // === Helpers ===
    
    /**
     * Envoie un message au joueur s'il est en ligne
     * @param message Le message à envoyer
     */
    public void sendMessage(String message) {
        Player player = getPlayer();
        if (player != null) {
            player.sendMessage(message);
        }
    }
    
    /**
     * @return Nom d'affichage avec préfixe de rôle si applicable
     */
    public String getDisplayName() {
        if (role == null) return lastKnownName;
        return role.getPrefix() + lastKnownName;
    }
    
    /**
     * Réinitialise toutes les données de faction
     */
    public void resetFactionData() {
        this.factionId = null;
        this.role = null;
        this.autoClaimEnabled = false;
        this.chatMode = ChatMode.PUBLIC;
    }
    
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        FPlayer fPlayer = (FPlayer) o;
        return uuid.equals(fPlayer.uuid);
    }
    
    @Override
    public int hashCode() {
        return Objects.hash(uuid);
    }
    
    @Override
    public String toString() {
        return "FPlayer{" +
                "uuid=" + uuid +
                ", name='" + lastKnownName + '\'' +
                ", faction='" + factionId + '\'' +
                ", role=" + role +
                ", power=" + power + "/" + maxPower +
                '}';
    }
}
