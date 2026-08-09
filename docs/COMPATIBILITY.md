# Kfaction V2.2 — Compatibility Matrix

| Élément | Contrat / état |
|---|---|
| Minecraft | 1.8.8 |
| Bukkit/Spigot/PandaSpigot | API 1.8.8 |
| Java | 8 |
| API publique Kfaction | 2.2.0 / major 2 |
| Storage payload | schema 9 |
| Global Zones payload | schema 2 |
| Backend recommandé | SQLite |
| MySQL | non implémenté |
| Progression runtime | `progression.yml` uniquement |
| Rôles | 6 fixes |

## Storage

SQLite est la source de vérité V2 single-server.

Le runtime conserve l'état en RAM et persiste des snapshots immuables via un writer borné.

`FLATFILE` reste uniquement un backend legacy/rollback explicite.

Il n'existe aucun fallback silencieux SQLite -> JSON et aucun backend MySQL V2 actif.

## Gson / Minecraft 1.8.8

Le code runtime utilise la forme compatible ancien Gson:

```java
new JsonParser().parse(...)
```

et ne doit pas réintroduire `JsonParser.parseString(...)`, absent des vieilles versions Gson chargées par certains serveurs 1.8.8.

## Intégrations optionnelles

Déclarées via `softdepend` lorsque pertinent:

```text
Vault
PlaceholderAPI
LuckPerms
Kcore
Kchat
Kgui
Kclassement
WorldGuard
CombatTagPlus
Multiverse-Core
ShopGUIPlus
Kcraft
KSpawner
Kgenerator
Kminerai
```

L'absence d'une intégration optionnelle ne doit pas empêcher le démarrage du core.

### LuckPerms

Contextes:

```text
kfaction:has-faction
kfaction:faction
kfaction:faction-tag
kfaction:role
```

Le hook conserve le hotfix Java 8 utilisant l'interface publique `ContextConsumer`, et non la classe lambda interne de LuckPerms.

### PlaceholderAPI

L'expansion Kfaction est possédée par `HookManager -> PlaceholderAPIHook`; le bootstrap principal ne doit pas enregistrer une seconde expansion.

### Kchat / Kcore / Kclassement / Kgui

Les bridges de lecture doivent préférer les snapshots API V2.2.

Kgui V2 doit utiliser `KfactionApis.get()` et `ZoneView`, sans accès direct aux managers.

## Dynamic Zones

Les IDs SafeZone/WarZone restent compatibles:

```text
safezone
warzone
```

Une zone custom utilise `zoneId` et n'est jamais transformée en `Faction` synthétique.

## API compatibility

Les overloads legacy `GlobalZoneType` restent présents en 2.2.

Les ajouts dynamiques utilisent les méthodes `*ById` afin de ne pas créer d'ambiguïté Java avec un ancien appel passant `null` à l'overload `GlobalZoneType`.

## Progression compatibility

`levels.yml`, `quests.yml` et `progression.example.yml` ne sont plus des ressources runtime.

Les façades/méthodes deprecated peuvent rester pour compatibilité binaire tant qu'elles délèguent au moteur `ProgressionService` et ne réactivent pas l'ancien système.
