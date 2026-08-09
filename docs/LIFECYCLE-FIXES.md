# Lot25C — Lifecycle fixes récupérés depuis le Kfaction.java transmis

Le fichier transmis compilait et démarrait, mais son bootstrap principal avait
perdu plusieurs appels introduits pendant la migration V2.

## Initialisations restaurées

```java
relationManager.initialize();
permissionManager.initialize();
economyManager.initialize();
questManager.initialize();
rewardManager.initialize();
factionChestManager.initialize();
```

`LevelManager` utilise:

```java
levelManager.initialize();
```

au lieu d'appeler son implémentation de config directement.

## Autosave dupliqué supprimé

Supprimé de Kfaction.java:

```java
storageManager.saveAsync();
```

dans un scheduler propre au plugin principal.

`StorageManager` possède déjà son autosave unique.

## PlaceholderAPI dupliqué supprimé

Supprimé:

```java
new KfactionExpansion(this).register();
```

depuis le bootstrap principal.

`PlaceholderAPIHook` est propriétaire de ce lifecycle.

## Shutdown corrigé

Avant, le storage était arrêté avant que les coffres soient capturés.

Maintenant:

```text
FactionChestManager.saveAll
→ StorageManager.shutdown
→ clear runtime domain
```

## Shutdowns ajoutés

Le bootstrap appelle maintenant les shutdowns disponibles:

```text
HookManager
QuestManager
MapManager
PermissionManager
ClaimManager
FactionManager
FPlayerManager
EconomyManager
LogManager
PlacedBlockTracker
PowerManager
```

Les exceptions d'un composant ne bloquent pas le nettoyage des suivants.

## Bukkit ServicesManager

À la désactivation:

```java
Bukkit.getServicesManager().unregisterAll(plugin);
```

évite de conserver une registration API lors d'un lifecycle anormal/reload.
