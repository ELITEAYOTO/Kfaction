# Kfaction V2 — Integration Guide

## Principe

Une intégration externe doit dépendre de la frontière publique:

```text
Bukkit ServicesManager
        ↓
KfactionApiV2
        ↓
snapshots / ApiResult
```

Elle ne doit pas lire directement les managers/services internes.

## Détection Kfaction

```java
KfactionApiV2 api = KfactionApis.get();

if (api == null) {
    // Kfaction absent, non activé ou service non enregistré.
    return;
}
```

Optionnellement:

```java
if (!KfactionApis.isAvailable()) {
    return;
}
```

## Vérifier la version

```java
if (api.getApiMajor() != 2) {
    return;
}

String version = api.getApiVersion();
```

Baseline du freeze:

```text
2.2.0
```

## Lecture faction

```java
FactionView faction =
        api.findFaction("Volkaria");

if (faction == null) {
    return;
}

String name = faction.getName();
int members = faction.getMemberCount();
int claims = faction.getClaimCount();
long bankMinor = faction.getBankMinor();
```

## Lecture joueur

```java
PlayerView profile =
        api.getPlayer(
                player.getUniqueId()
        );

if (profile == null) {
    return;
}
```

Pour un profil historique non chargé, une lecture async ne doit pas supposer qu'un lazy-load SQLite sera fait. Utiliser le main thread pour une résolution storage-backed.

## Territoire

```java
TerritoryView territory =
        api.getTerritory(
                new FLocation(
                        player.getLocation()
                ),
                player.getUniqueId()
        );
```

Types possibles:

```text
WILDERNESS
FACTION
SAFEZONE
WARZONE
```

## Permission

```java
PermissionView permission =
        api.checkTerritory(
                player,
                block.getLocation(),
                TerritoryAction.BLOCK_BREAK
        );

if (!permission.isAllowed()) {
    return;
}
```

## Mutation depuis une GUI

Toujours sur Bukkit main thread:

```java
OperationContext context =
        OperationContext.actor(
                player.getUniqueId(),
                player.getName(),
                OperationSource.GUI
        );

ApiResult<ClaimResultView> result =
        api.claimSingle(
                player,
                faction.getId(),
                new FLocation(
                        player.getLocation()
                ),
                context
        );
```

Ne jamais contourner `ApiResult`.

## Kgui V2

### Dynamic content

IDs actuellement fournis:

```text
kfaction_quests
kfaction_rewards
```

`kfaction_quests` produit des éléments depuis `QuestView`.

`kfaction_rewards` produit des éléments depuis `RewardLevelView`.

Pour la réécriture Kgui V2, ces IDs doivent être conservés ou migrés explicitement avec compatibilité.

### Dépendances autorisées

Kgui V2 peut importer:

```text
me.krunsh.kfaction.api.v2.*
me.krunsh.kfaction.core.operation.OperationContext
me.krunsh.kfaction.core.operation.OperationSource
me.krunsh.kfaction.data.FLocation
me.krunsh.kfaction.data.FactionRole
me.krunsh.kfaction.permissions.TerritoryAction
me.krunsh.kfaction.zones.GlobalZoneType
```

Kgui V2 ne doit pas importer:

```text
me.krunsh.kfaction.managers.*
me.krunsh.kfaction.services.*
me.krunsh.kfaction.data.Faction
me.krunsh.kfaction.data.FPlayer
```

## PlaceholderAPI

Expansion:

```text
kfaction
```

Exemple:

```text
%kfaction_faction_name%
%kfaction_player_role%
%kfaction_location_faction%
%kfaction_faction_progress_percent%
```

Permissions dynamiques:

```text
%kfaction_perm_officer_claim%
%kfaction_perm_ally_container%
```

La résolution exacte dépend des clés ACL reconnues par l'API.

## LuckPerms

Contextes:

```text
kfaction:has-faction
kfaction:faction
kfaction:faction-tag
kfaction:role
```

Exemple conceptuel:

```text
permission.some.feature
context:
  kfaction:role=officer
```

Les contextes sont rafraîchis lors des transitions membership/role et au join.

## Kchat

Les informations faction/role/relation sont lues via API 2.2.

L'intégration reflection restante concerne la frontière externe Kchat, pas le domaine Kfaction.

## Kcore

SafeZone, WarZone, PvP et zone name passent par l'API publique.

## Kclassement

Le calcul V2 principal consomme `FactionView`.

La compatibilité historique avec `Faction` reste dépréciée.

## Diagnostics intégrations

Commande:

```text
/kf doctor integrations
```

États:

```text
ACTIVE
FAILED
STARTING
MISSING
DISABLED
```

Un plugin externe ne doit pas convertir automatiquement `MISSING` en erreur core.
