# Kfaction V2.2 — Dynamic Global Zones

## Principe

Depuis API 2.2, une Global Zone est identifiée par un `zoneId` stable:

```text
FLocation -> zoneId
```

Les définitions viennent de:

```text
config.yml
zones.<zoneId>
```

Ajouter une section valide crée donc un nouveau type de zone sans modifier Java.

Exemple:

```yaml
zones:
  avant_post:
    display-name: "Avant-Post"
    color: "&6"
    map-symbol: "A"

    title: "&6Avant-Post"
    subtitle: "&eZone PvP spéciale"
    enter-message: "&6~ Avant-Post &e(PvP)"

    pvp: true
    default-policy: DENY

    allowed-actions:
      - ENTER
      - SWITCH
      - CONTAINER_OPEN
      - CONTAINER_DEPOSIT
      - CONTAINER_WITHDRAW

    denied-actions:
      - SET_HOME
```

Après sauvegarde:

```text
/kf zone reload
```

puis:

```text
/kf zone claim avant_post
```

ou:

```text
/kf zone auto avant_post
```

## IDs

Format:

```text
^[a-z0-9_-]{1,32}$
```

Valides:

```text
avant_post
event-pvp
boss1
spawn
```

Invalides:

```text
Avant Post
event!
zone.fr
```

## Commandes

```text
/kf zone claim <zone>
/kf zone unclaim [zone]

/kf zone auto <zone|off>
/kf zone autounclaim <zone|off>

/kf zone info
/kf zone list
/kf zone list <zone>

/kf zone reload
```

L'auto-zone ne s'exécute que lors d'un changement de chunk.

## Règles

Chaque zone possède:

```yaml
pvp: true|false
default-policy: ALLOW|DENY
allowed-actions: []
denied-actions: []
```

Priorité:

```text
denied-actions
    ↓
allowed-actions
    ↓
default-policy
```

Donc si une action apparaît dans ALLOW et DENY, **DENY gagne**.

## Actions territoire

```text
ENTER

BLOCK_PLACE
BLOCK_BREAK

PISTON
FLUID_FLOW
FIRE_SPREAD
EXPLOSION_BLOCK_DAMAGE
ENTITY_GRIEF
WITHER_SPAWN

SWITCH
REDSTONE

CONTAINER_OPEN
CONTAINER_DEPOSIT
CONTAINER_WITHDRAW

HOPPER
FURNACE
BREWING
ANVIL
ENCHANT

ITEM_FRAME
ARMOR_STAND

SPAWNER_PLACE
SPAWNER_BREAK
SPAWNER_INTERACT

TNT_PLACE
TNT_IGNITE

BUCKET_EMPTY
BUCKET_FILL
FLINT_AND_STEEL

ENDER_PEARL
VEHICLE
FLY
TELEPORT_IN
SET_HOME
COMMAND_USE
```

Les clés minuscules (`block_break`) sont également acceptées.

## SET_HOME

`SET_HOME` contrôle notamment les commandes externes de type Essentials `/sethome`.

Exemple:

```yaml
denied-actions:
  - SET_HOME
```

empêche la définition d'un home externe dans cette Global Zone.

Le home/warp **de faction** conserve en plus sa règle métier: il doit appartenir au territoire de la faction.

## SafeZone / WarZone

Les IDs historiques:

```text
safezone
warzone
```

sont maintenant des définitions normales fournies par défaut.

Les anciennes façades Java:

```text
GlobalZoneType.SAFEZONE
GlobalZoneType.WARZONE
```

restent compatibles pour l'API 2.1 / anciennes intégrations.

Une zone custom n'est jamais représentée par une fausse `Faction`.

## Persistance

Payload Global Zones:

```text
schema 2
```

Ancien schema:

```json
{
  "location": "world:10:12",
  "type": "SAFEZONE"
}
```

Nouveau schema:

```json
{
  "location": "world:10:12",
  "zone": "safezone"
}
```

Migration:

```text
SAFEZONE -> safezone
WARZONE  -> warzone
```

automatique au chargement.

Le `StorageSnapshot` global reste schema 9: seul le payload interne de l'entité GLOBAL_ZONES passe à schema 2.

## Zone orpheline

Si tu as:

```text
chunk -> avant_post
```

puis que tu supprimes:

```yaml
zones:
  avant_post:
```

le chunk n'est **pas silencieusement transformé en Wilderness**.

Kfaction crée une vue runtime fail-closed:

```text
ID conservé
PvP OFF
ENTER autorisé
autres actions refusées
configured=false
```

Diagnostic:

```text
/kf doctor zones
/kf doctor zones full
```

Pour corriger:

- restaurer `zones.avant_post`; ou
- retirer explicitement les chunks avec `/kf zone unclaim`.

## Map

`/f map` utilise directement:

```yaml
display-name:
color:
map-symbol:
```

de la définition de zone.

Exemple:

```text
A Avant-Post
S SafeZone
W WarZone
```

Le comportement Lot18 reste inchangé:

```text
aucun scheduler
aucun refresh yaw
refresh auto uniquement au changement de chunk
```

## Titres

À l'entrée:

```yaml
title:
subtitle:
```

sont utilisés.

Si:

```yaml
territory:
  use-titles: false
```

le plugin utilise:

```yaml
enter-message:
```

## API

API:

```text
2.2.0
```

Nouveau snapshot:

```text
ZoneView
```

Nouvelles lectures:

```java
ZoneView getGlobalZoneAt(FLocation location);
List<ZoneView> getGlobalZones();
```

Nouvelles mutations:

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

Les overloads `GlobalZoneType` sont conservés.

## Kgui V2

Kgui doit utiliser:

```text
TerritoryView.Type.GLOBAL_ZONE
TerritoryView.getZoneId()
TerritoryView.getZoneDisplayName()
TerritoryView.getZoneColor()
TerritoryView.getZoneMapSymbol()

KfactionApiV2.getGlobalZoneAt()
KfactionApiV2.getGlobalZones()
```

et ne jamais lire directement `ZoneService`.

## PlaceholderAPI

Ajouts 2.2:

```text
%kfaction_location_zone_id%
%kfaction_location_zone_name%
```

Hors Global Zone, ces deux placeholders renvoient une chaîne vide.

### Nettoyer une zone orpheline en masse

Une zone orpheline ne peut plus être claimée, mais elle peut volontairement être retirée en auto-unclaim:

```text
/kf zone autounclaim ancien_event
```

Puis marcher dans les chunks concernés et terminer avec:

```text
/kf zone autounclaim off
```

### Reload sans reload global

```text
/kf zone reload
```

relit directement `zones.*` depuis `plugins/Kfaction/config.yml` sur disque. Il ne recharge pas les autres managers, ce qui évite un reload partiel du reste du plugin.
