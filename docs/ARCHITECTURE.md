# Kfaction V2.2 — Architecture Freeze

## Vue générale

```text
Kfaction
├─ Bootstrap/lifecycle
│  └─ KfactionLogger + startup compact/debug
├─ Application services
│  ├─ MembershipService
│  ├─ RoleService
│  ├─ FactionLifecycleService
│  ├─ ClaimService / UnclaimService
│  ├─ PermissionService
│  ├─ HomeWarpService
│  ├─ EconomyService
│  ├─ GraceService
│  ├─ ProgressionService
│  ├─ ClaimGroupService
│  └─ ZoneService
├─ Domain
│  ├─ Faction
│  ├─ FPlayer
│  ├─ FactionRole (6 fixes)
│  ├─ ClaimGroup
│  ├─ StoredLocation / FactionWarp
│  └─ ZoneDefinition
├─ Persistence
│  ├─ StorageManager
│  ├─ SQLiteStorage
│  ├─ StorageSnapshot schema 9
│  └─ Global Zones payload schema 2
├─ Audit
│  └─ audit.db
├─ Bukkit adapters/listeners
├─ Integrations
└─ Public API
   └─ KfactionApiV2 2.2.0
```

## Mutation rule

Chemin cible:

```text
validate
→ PRE event
→ mutation domaine
→ indexes
→ dirty/snapshot
→ logs/audit
→ POST/notification
```

Les mutations domaine doivent rester sur le Bukkit main thread.

Les écritures disque asynchrones ne doivent manipuler que des données capturées/sûres pour l'I/O.

## Membership

Source canonique:

```text
Faction.members: Map<UUID, FactionRole>
```

`FPlayer.factionId/role` reste un miroir de transition/compatibilité synchronisé par les services.

La limite de base vient exclusivement de:

```text
factions.members.max-per-faction
```

plus `FactionProgressState.extraMembers`.

## Relations

ALLY/TRUCE:

```text
symétriques
requêtes typées
expiration configurable
limites vérifiées sur les deux factions
```

ENEMY:

```text
unilatéral par design
```

Le moteur effectif considère ENEMY si l'un des deux côtés l'a déclaré.

NEUTRAL nettoie les deux directions et les demandes pending entre les factions.

## Power

`power.min` peut être négatif.

`FPlayer.maxPower` est le maximum de base persistant; les bonus permission sont un plafond effectif runtime calculé par `PowerManager`.

Régénération et mutations power sont main-thread.

## Claims / Global Zones

Claims joueur et Global Zones sont distincts.

Runtime zones:

```text
FLocation -> zoneId -> ZoneDefinition
```

Une zone globale prend priorité sur un claim joueur.

Les zones custom contrôlent également les protections environnementales:

```text
PISTON
FLUID_FLOW
FIRE_SPREAD
EXPLOSION_BLOCK_DAMAGE
ENTITY_GRIEF
WITHER_SPAWN
```

## Permission engine

Trois niveaux distincts:

```text
Bukkit permissions
FactionCapability
TerritoryAction
```

Claim Groups fournissent des overrides ALLOW/DENY/INHERIT.

Global Zones appliquent DENY explicite > ALLOW explicite > default-policy.

## Progression

Source de vérité:

```text
plugins/Kfaction/progression.yml
```

Pas de runtime `levels.yml` / `quests.yml`.

Le chemin métier peut migrer/reconcilier; le chemin API `peek*` est strictement read-only.

Les rewards pending ne sont jamais rejouées aveuglément.

## API

Kfaction API 2.2 ne retourne que des snapshots/`ApiResult`.

Kgui et les nouveaux addons ne doivent pas accéder directement aux managers/domain live.

## Hot-path rules

À préserver:

```text
pas d'I/O DB dans protection/map
pas de chunk load forcé par /f map
map auto seulement changement de chunk
pas de mutation Bukkit/domain depuis async
queues I/O bornées lorsque la charge peut être externe/non bornée
```
