# Kfaction V2 — Console / Startup Lot25C

## Objectif

Le démarrage normal doit être utile sans afficher chaque détail interne.

Avec:

```yaml
debug: false

console:
  colors:
    enabled: true
  symbols:
    enabled: true
  startup:
    compact: true
    show-timings-in-debug: true
```

la console affiche essentiellement:

```text
━━━━━━━━━━ Kfaction vX.Y.Z ━━━━━━━━━━
✔ Core prêt — API 2.2.0 | storage=SQLite | payload=9
Données — 12 factions | 386 claims | 48 chunks de zone | 3 définitions
Progression — 50 niveaux | ... quêtes | validation OK
Intégrations — Vault · PlaceholderAPI · LuckPerms · Kchat
✔ Démarrage terminé en ... ms
```

Les valeurs sont calculées réellement au démarrage.

## Warnings / erreurs

Le mode compact ne masque jamais:

```text
WARNING
SEVERE
```

Il masque seulement les anciens `INFO` produits par les managers pendant
`onEnable()` et `onDisable()`.

Le filtre est ensuite retiré.

Donc un problème SQLite, une configuration de zone invalide, une intégration
présente mais cassée ou une erreur progression reste visible.

## Debug

Avec:

```yaml
debug: true
```

aucun filtre compact n'est installé.

Les INFO historiques restent visibles et Kfaction ajoute:

```text
── Startup timings ──
[DEBUG] Configuration........ 3 ms
[DEBUG] Hooks................ 12 ms
[DEBUG] Storage.............. 24 ms
[DEBUG] Managers............. 31 ms
...
[DEBUG] TOTAL................ 96 ms

── Storage ──
[DEBUG] backend=SQLite, connected=true, writer=0/256, rejected=0...

── Dynamic Zones ──
[DEBUG] definitions=3, chunks=48, configIssues=0, orphans=0

── Progression ──
[DEBUG] enabled=true, activeQuests=..., validationIssues=0

── Integrations ──
[DEBUG] Vault............. ACTIVE
[DEBUG] PlaceholderAPI.... STARTING/ACTIVE
...
```

## Couleurs

Le rendu passe par `KfactionLogger` du Lot25A:

```text
OR       identité/banner
VERT     succès
BLANC    information
JAUNE    warning
ROUGE    erreur
VIOLET   reload
GRIS     debug
CYAN     sections debug
```

Les couleurs utilisent ANSI, comme le principe observé dans KjobsUltimate.

## PlaceholderAPI

`Kfaction.java` n'enregistre plus directement une deuxième
`KfactionExpansion`.

La propriété de l'expansion est maintenant uniquement:

```text
HookManager
  -> PlaceholderAPIHook
```

Le hook attend le tick suivant afin que l'API V2 soit déjà disponible.

## Autosave

L'ancien code:

```java
Bukkit.getScheduler().runTaskTimerAsynchronously(
    ... storageManager.saveAsync()
);
```

est supprimé de `Kfaction.java`.

La source unique est:

```text
StorageManager.reloadSettings()
  -> restartAutoSave()
  -> main-thread immutable capture
  -> bounded writer
```

Cela évite deux autosaves concurrents et protège le contrat de capture
main-thread établi par les Lots storage/hardening.

## Lifecycle init

Ordre important restauré:

```text
LogManager

FactionManager
FPlayerManager
ClaimManager

PowerManager
RelationManager
PermissionManager
TerritoryManager

EconomyManager
MapManager

LevelManager
QuestManager / ProgressionService
RewardManager
FactionChestManager

PlacedBlockTracker
```

`RelationManager.initialize()` et `PermissionManager.initialize()` passent
avant `TerritoryManager.initialize()`.

## Lifecycle shutdown

Ordre principal:

```text
stop fly/tasks
  ↓
capture coffre faction
  ↓
stop caches non-persistants
  ↓
HookManager shutdown
  ↓
StorageManager.shutdown()
  ├ saveAllSync
  ├ flush deletes
  ├ stop writer
  └ close backend
  ↓
clear Grace/claims/zones/factions/FPlayers
  ↓
Economy
  ↓
ServicesManager unregisterAll
  ↓
LogManager / Audit final
```

Le point critique est:

```text
FactionChestManager.saveAll()
AVANT
StorageManager.shutdown()
```

et:

```text
StorageManager.shutdown()
AVANT
ClaimManager/FactionManager/FPlayerManager.shutdown()
```

afin que le snapshot final voie encore tout le domaine RAM.

## Reload

`Kfaction.reload()` recharge maintenant de façon cohérente:

```text
config
messages
relations
permissions/grace
territory
storage settings
progression UI
progression.yml
map
TerritoryListener
Fly
Dynamic Zones
```

Puis affiche:

```text
↻ Configuration rechargée en X ms
```
