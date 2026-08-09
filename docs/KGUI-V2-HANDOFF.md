# Handoff officiel vers Kgui V2

## État Kfaction après Lot25D

Kfaction est considéré **feature-complete / API frozen** dès que le build Maven et le smoke serveur du Lot25D sont verts.

Contrats:

```text
API publique: KfactionApiV2 2.2.0
API major: 2
Storage payload: schema 9
Global Zones payload: schema 2
Rôles: 6 fixes
Audit: audit.db
Map auto: chunk-change-only
Progression runtime: progression.yml uniquement
```

## Architecture obligatoire Kgui

```text
Kgui
  ↓
KfactionApis.get()
  ↓
KfactionApiV2
  ↓
snapshots / ApiResult
```

Kgui V2 ne doit pas contourner l'API avec:

```text
plugin.getFactionManager()
plugin.getFPlayerManager()
plugin.getClaimManager()
plugin.getQuestManager()
new MembershipService(...)
new RoleService(...)
```

## Snapshots à utiliser

Faction:

```text
FactionView
MemberView
```

Joueur:

```text
PlayerView
```

Territoire:

```text
TerritoryView
PermissionView
ChunkView
```

Zones dynamiques:

```text
ZoneView
KfactionApiV2.getGlobalZoneAt(...)
KfactionApiV2.getGlobalZones()
```

Une zone custom renvoie:

```text
TerritoryView.Type.GLOBAL_ZONE
zoneId
zoneDisplayName
zoneColor
zoneMapSymbol
```

Progression:

```text
ProgressionView
QuestView
RewardLevelView
```

Les lectures progression API 2.2 sont read-only et ne doivent pas provoquer de reconciliation cachée.

Grace:

```text
GraceView
```

## Actions GUI

Toute action doit:

1. être exécutée sur le Bukkit main thread;
2. construire un `OperationContext` avec `OperationSource.GUI`;
3. appeler la mutation `KfactionApiV2` adaptée;
4. traiter `ApiResult.Status` au lieu d'inférer le résultat depuis un objet live;
5. relire un snapshot après succès si l'écran doit être rafraîchi.

## Dynamic Zones dans Kgui

Ne coder aucune liste fixe `SAFEZONE/WARZONE`.

La liste doit venir de:

```java
api.getGlobalZones();
```

Ainsi une config serveur peut ajouter:

```text
avant_post
boss_zone
event_pvp
mine_event
```

sans mise à jour de Kgui.

## Threading

Une GUI Bukkit travaille normalement sur le main thread.

Ne pas lancer une mutation Kfaction depuis un executor async.

Pour les providers de contenu asynchrones, n'utiliser que des snapshots déjà accessibles par le contrat API; ne jamais forcer la création/lecture storage d'un `FPlayer` depuis async.

## Compatibilité

Kgui doit vérifier:

```java
KfactionApiV2 api = KfactionApis.get();

if (api == null || api.getApiMajor() != 2) {
    // désactiver proprement l'intégration
}
```

Kfaction reste autonome si Kgui est absent.
