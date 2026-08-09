package me.krunsh.kfaction.api.v2;

import java.util.List;

/**
 * Capacité de lecture additive 2.3. Le contrat {@link KfactionApiV2} 2.2 reste
 * inchangé pour les consommateurs déjà compilés.
 */
public interface KfactionApiV23 extends KfactionApiV2 {

    String API_VERSION = "2.3.0";
    int API_MINOR = 3;

    PageView<ClaimView> getFactionClaims(String factionId, PageRequest request);

    List<WarpView> getFactionWarps(String factionId);

    PageView<FactionLogView> getFactionLogs(String factionId, FactionLogQuery query);

    List<FactionInviteView> getFactionInvites(String factionId);

    List<RelationRequestView> getRelationRequests(String factionId);

    List<RelationView> getRelations(String factionId);

    FactionAclView getFactionAcl(String factionId);

    FactionSettingsView getFactionSettings(String factionId);
}
