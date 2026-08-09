# Kfaction API V2 — Freeze 2.2.0

## Statut

La surface publique `me.krunsh.kfaction.api.v2.KfactionApiV2` est figée en:

```text
KfactionApiV2.API_VERSION = "2.2.0"
KfactionApiV2.API_MAJOR   = 2
```

Le contrat exact est protégé par `KfactionApiV2ContractTest`.

À partir du freeze 25D:

- aucune méthode 2.2 existante ne doit être supprimée ou renommée;
- aucun paramètre/type de retour ne doit changer de façon incompatible;
- une rupture de contrat doit utiliser une nouvelle API majeure, par exemple `KfactionApiV3`;
- les évolutions 2.x futures doivent rester additives et compatibles.

## Récupération

Chemin recommandé:

```java
KfactionApiV2 api = KfactionApis.get();

if (api == null) {
    return;
}
```

`KfactionApis.get()` utilise le Bukkit `ServicesManager`.

L'ancienne `KfactionAPI` reste disponible uniquement comme façade de compatibilité et expose `v2()` / `getV2()`.

## Contrat de lecture

Les lectures API V2 renvoient des **snapshots** et jamais les objets métier live `Faction`, `FPlayer`, Managers ou Services.

DTO principaux:

```text
FactionView
PlayerView
MemberView
TerritoryView
ZoneView
ProgressionView
QuestView
RewardLevelView
GraceView
PermissionView
ChunkView
```

Les collections exposées par les DTO sont des copies immuables.

### Progression: lecture strictement read-only

Lot25D sépare explicitement les chemins de lecture API des chemins de reconciliation métier:

```text
KfactionApiProvider
  -> QuestManager.peekStatus / peekQuestViews
  -> ProgressionService.peekStatus / peekQuestViews
  -> ProgressionPolicy.viewsReadOnly
```

Ces lectures ne doivent pas:

```text
migrer un état
modifier lockedTier
rejouer une reward pending
markDirty
émettre un event
écrire en storage
```

Les diagnostics et intégrations doivent préférer ces lectures pures lorsqu'ils n'ont pas l'intention de modifier le domaine.

### Lecture joueur hors main thread

`FPlayerManager.find(UUID)` a une sémantique volontairement sûre:

```text
profil déjà chargé -> lecture cache possible
profil non chargé + main thread -> chargement storage possible
profil non chargé + async -> null / cache-only
```

Une intégration async ne doit jamais forcer un chargement/création de `FPlayer`.

## Dynamic Global Zones 2.2

API 2.2 ajoute les zones dynamiques sans casser SafeZone/WarZone legacy.

Lecture:

```java
ZoneView getGlobalZoneAt(FLocation location);
List<ZoneView> getGlobalZones();
```

Mutation par ID dynamique:

```java
ApiResult<String> setGlobalZoneById(
        FLocation location,
        String zoneId,
        OperationContext context
);

ApiResult<String> clearGlobalZoneById(
        FLocation location,
        String expectedZoneId,
        OperationContext context
);
```

Compatibilité conservée:

```java
setGlobalZone(FLocation, GlobalZoneType, OperationContext)
clearGlobalZone(FLocation, GlobalZoneType, OperationContext)
```

`TerritoryView.Type`:

```text
WILDERNESS
FACTION
SAFEZONE
WARZONE
GLOBAL_ZONE
```

Pour une zone custom, utiliser:

```text
TerritoryView.getZoneId()
TerritoryView.getZoneDisplayName()
TerritoryView.getZoneColor()
TerritoryView.getZoneMapSymbol()
TerritoryView.isGlobalZone()
```

## Trusted mutation API

Les mutations publiques sont **main-thread only**.

Si elles sont appelées hors du Bukkit primary thread, le provider retourne normalement:

```text
ApiResult.Status.UNAVAILABLE
```

Les mutations passent par les services/invariants Kfaction; une intégration ne doit pas modifier directement `Faction`, `FPlayer`, `ClaimManager` ou `ZoneService`.

## ApiResult

Statuts:

```text
SUCCESS
NO_CHANGE
CANCELLED
INVALID_INPUT
NOT_FOUND
FORBIDDEN
CONFLICT
LIMIT_REACHED
UNAVAILABLE
FAILED
```

Contrat:

```text
isSuccess()     -> SUCCESS uniquement
isSuccessful()  -> SUCCESS ou NO_CHANGE
```

Lot25D interdit désormais:

```java
ApiResult.failure(Status.SUCCESS, ...)
ApiResult.failure(Status.NO_CHANGE, ...)
```

Ces appels lancent `IllegalArgumentException`.

## OperationContext

Toute mutation trusted doit fournir un contexte utile:

```java
OperationContext.actor(uuid, name, OperationSource.API)
OperationContext.actor(uuid, name, OperationSource.GUI)
OperationContext.admin(...)
OperationContext.system()
```

Le `correlationId` sert à relier logs/audit/opérations.

## Versioning

`API_MAJOR = 2` reste stable tant que le contrat est compatible.

Kgui V2 doit tester au minimum:

```java
api != null
api.getApiMajor() == 2
```

et peut vérifier `getApiVersion()` pour les fonctionnalités ajoutées en 2.2.
