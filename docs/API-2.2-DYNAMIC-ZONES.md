# Kfaction API 2.2 — Dynamic Zones

## Version

```text
API_VERSION = 2.2.0
API_MAJOR = 2
```

C'est une évolution **additive** de 2.1.0.

Aucune méthode 2.1 n'est supprimée.

## Ajouts

```java
ZoneView getGlobalZoneAt(FLocation location);

List<ZoneView> getGlobalZones();

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

`TerritoryView.Type` ajoute:

```text
GLOBAL_ZONE
```

`TerritoryView` ajoute:

```text
getZoneId()
getZoneDisplayName()
getZoneColor()
getZoneMapSymbol()
isGlobalZone()
```

## Compatibilité

Restent disponibles:

```java
setGlobalZone(FLocation, GlobalZoneType, OperationContext)
clearGlobalZone(FLocation, GlobalZoneType, OperationContext)
```

SafeZone/WarZone continuent à produire:

```text
TerritoryView.Type.SAFEZONE
TerritoryView.Type.WARZONE
```

Une autre zone produit:

```text
TerritoryView.Type.GLOBAL_ZONE
```

## ZoneView

Snapshot immutable:

```text
id
displayName
color
mapSymbol
title
subtitle
enterMessage
pvpAllowed
defaultPolicy
allowedActions
deniedActions
chunkCount
configured
```

`configured=false` indique notamment une définition orpheline récupérée fail-closed.

## Threading

Même contrat que 2.1:

- mutations: Bukkit main thread;
- lectures runtime: main thread recommandé;
- aucune intégration ne doit contourner l'API pour modifier ZoneService.
