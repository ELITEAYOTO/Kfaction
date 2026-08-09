package me.krunsh.kfaction.hooks;

import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

import me.krunsh.kfaction.Kfaction;
import me.krunsh.kfaction.api.v2.FactionView;
import me.krunsh.kfaction.api.v2.KfactionApiV2;
import me.krunsh.kfaction.api.v2.KfactionApis;
import me.krunsh.kfaction.data.Faction;

/**
 * Adaptateur classement V2.
 *
 * Le calcul principal travaille uniquement sur FactionView immutable.
 * Les méthodes retournant Faction live restent en compatibilité V1.
 */
public final class KclassementHook {

    private final Kfaction plugin;

    private volatile KfactionApiV2 cachedApi;

    public KclassementHook(
            Kfaction plugin
    ) {
        this.plugin = plugin;
    }

    public void initialize() {
        /*
         * Le plugin Kclassement historique n'exposait pas encore de contrat
         * provider stable dans ce repo. La façade reste disponible et le
         * futur Kclassement peut consommer directement KfactionApiV2.
         */
    }

    public double calculateFactionValue(
            FactionView faction
    ) {
        if (faction == null) {
            return 0.0D;
        }

        double value =
                faction.getBankBalance();

        value +=
                faction.getPower()
                        * plugin.getConfigManager()
                                .getDouble(
                                        "ftop.power-multiplier",
                                        100.0D
                                );

        value +=
                faction.getClaimCount()
                        * plugin.getConfigManager()
                                .getDouble(
                                        "ftop.claim-value",
                                        500.0D
                                );

        value +=
                faction.getMemberCount()
                        * plugin.getConfigManager()
                                .getDouble(
                                        "ftop.member-value",
                                        1000.0D
                                );

        return value;
    }

    public List<FactionScore> getTopFactionViews(
            int limit
    ) {
        KfactionApiV2 api =
                api();

        if (api == null) {
            return Collections.emptyList();
        }

        int safeLimit =
                Math.max(
                        0,
                        Math.min(
                                1000,
                                limit
                        )
                );

        List<FactionScore> result =
                new ArrayList<FactionScore>();

        for (FactionView faction
                : api.getFactions()) {
            result.add(
                    new FactionScore(
                            faction,
                            calculateFactionValue(
                                    faction
                            )
                    )
            );
        }

        Collections.sort(
                result,
                new Comparator<FactionScore>() {
                    @Override
                    public int compare(
                            FactionScore first,
                            FactionScore second
                    ) {
                        int score =
                                Double.compare(
                                        second.getValue(),
                                        first.getValue()
                                );

                        if (score != 0) {
                            return score;
                        }

                        return safeName(
                                first.getFaction()
                        ).compareToIgnoreCase(
                                safeName(
                                        second.getFaction()
                                )
                        );
                    }
                }
        );

        if (safeLimit == 0) {
            return Collections.emptyList();
        }

        if (result.size() > safeLimit) {
            result =
                    new ArrayList<FactionScore>(
                            result.subList(
                                    0,
                                    safeLimit
                            )
                    );
        }

        return Collections.unmodifiableList(
                result
        );
    }

    public int getFactionRank(
            FactionView faction
    ) {
        if (faction == null) {
            return -1;
        }

        KfactionApiV2 api =
                api();

        if (api == null) {
            return -1;
        }

        double target =
                calculateFactionValue(
                        faction
                );

        int rank = 1;

        for (FactionView other
                : api.getFactions()) {
            if (other == null
                    || faction.getId()
                            .equals(
                                    other.getId()
                            )) {
                continue;
            }

            if (calculateFactionValue(other)
                    > target) {
                rank++;
            }
        }

        return rank;
    }

    // ============================================================
    // Compatibilité V1
    // ============================================================

    @Deprecated
    public double calculateFactionValue(
            Faction faction
    ) {
        if (faction == null
                || faction.isSystemFaction()) {
            return 0.0D;
        }

        KfactionApiV2 api =
                api();

        return api != null
                ? calculateFactionValue(
                        api.getFaction(
                                faction.getId()
                        )
                )
                : 0.0D;
    }

    @Deprecated
    public List<Map.Entry<Faction, Double>>
            getTopFactions(
                    int limit
            ) {
        List<Map.Entry<Faction, Double>> result =
                new ArrayList<Map.Entry<Faction, Double>>();

        for (FactionScore score
                : getTopFactionViews(limit)) {
            Faction live =
                    plugin.getFactionManager()
                            .getFaction(
                                    score.getFaction()
                                            .getId()
                            );

            if (live != null) {
                result.add(
                        new AbstractMap.SimpleImmutableEntry<Faction, Double>(
                                live,
                                Double.valueOf(
                                        score.getValue()
                                )
                        )
                );
            }
        }

        return result;
    }

    @Deprecated
    public int getFactionRank(
            Faction faction
    ) {
        if (faction == null
                || faction.isSystemFaction()) {
            return -1;
        }

        KfactionApiV2 api =
                api();

        return api != null
                ? getFactionRank(
                        api.getFaction(
                                faction.getId()
                        )
                )
                : -1;
    }

    private KfactionApiV2 api() {
        KfactionApiV2 current = cachedApi;

        if (current == null) {
            current = KfactionApis.get();

            if (current != null) {
                cachedApi = current;
            }
        }

        return current;
    }

    private static String safeName(
            FactionView faction
    ) {
        if (faction == null) {
            return "";
        }

        if (faction.getName() != null) {
            return faction.getName();
        }

        return faction.getId() != null
                ? faction.getId()
                : "";
    }

    public static final class FactionScore {

        private final FactionView faction;
        private final double value;

        public FactionScore(
                FactionView faction,
                double value
        ) {
            this.faction = faction;
            this.value = value;
        }

        public FactionView getFaction() {
            return faction;
        }

        public double getValue() {
            return value;
        }
    }
}
