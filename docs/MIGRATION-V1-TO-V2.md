# Kfaction — Migration V1 vers V2

## Objectif

La V2 remplace progressivement les écritures directes et les doubles sources de vérité par des services applicatifs, snapshots immuables et invariants explicites.

Cette migration n'essaie pas de conserver chaque détail interne V1 comme contrat public.

## Rôles

V2 fixe exactement six rôles:

```text
RECRUIT
MEMBER
OFFICER
MODERATOR
COLEADER
LEADER
```

Le passage `COLEADER -> LEADER` n'est pas une promotion classique; il passe par un transfert de leadership.

## FPlayer

Ancienne ambiguïté:

```text
getFPlayer(...)
```

pouvait impliquer get-or-create.

V2 distingue:

```text
findLoaded(UUID)
find(UUID)
getOrCreate(UUID)
```

Lot 23 ajoute:

- `find(UUID)` cache-only hors main thread si non chargé;
- `getOrCreate(UUID)` ne crée rien hors main thread.

Le nouveau code ne doit pas utiliser l'ancien alias ambigu.

## Membership / rôles

Toutes les mutations passent par:

```text
MembershipService
RoleService
FactionLifecycleService
```

Objectifs:

- Faction/FPlayer synchronisés;
- index synchronisés;
- dirty tracking;
- audit/log;
- LuckPerms context refresh.

## Claims

V2 utilise des plans avant mutation.

Claim:

```text
validate
→ plan immutable
→ PRE events
→ revalidation
→ commit
```

Unclaim:

```text
snapshot exact
→ PRE events
→ revalidation
→ commit
→ cleanup home/warps
→ rollback si exception
```

Les opérations radius/fill sont bornées.

## Claim Groups

Un chunk possède zéro ou un Claim Group.

Le groupe ne possède pas le claim: il porte des overrides ACL.

Règles:

```text
ALLOW
DENY
INHERIT
```

## Global Zones

SafeZone/WarZone ne sont plus des claims normaux détenus par des factions système dans le runtime canonique.

Source runtime:

```text
FLocation -> GlobalZoneType
```

Les anciennes claims système peuvent être importées/migrées.

## Grace Period

État global persistant.

Les timestamps sont absolus: un arrêt serveur ne prolonge pas artificiellement la grace.

Protection possible:

- raids;
- actions territoire;
- dégâts blocs d'explosion;
- PvP enemy selon config.

## Home / warps

V2 persiste `StoredLocation` indépendamment du chargement Bukkit d'un monde.

Un home/warp n'est donc plus perdu au decode simplement parce que le monde n'est pas encore chargé.

Les mots de passe warps:

```text
PBKDF2
salt aléatoire
hash uniquement
jamais plaintext persistant
```

## Économie

Ancien:

```text
double bank
```

Nouveau canonique:

```text
long bankMinor
```

Scale:

```text
2 décimales
100 minor units = 1 unité
```

La migration d'une ancienne valeur double se fait à la frontière de compatibilité.

## Progression

L'état progression V2 est durable et versionné.

Les protections de reward pending empêchent un replay silencieux.

Le doctor ne déclenche pas de migration/replay.

## Storage

Backend recommandé/par défaut:

```text
SQLite
```

Legacy explicite:

```text
FlatFile / JSON
```

Pas de fallback silencieux de SQLite vers JSON.

Pourquoi:

```text
SQLite cassé
+ fallback JSON ancien
=
deux sources de vérité
+ risque de réapparition de données
```

Le payload logique final V2 est:

```text
schema 9
```

## Audit

Le vieux log JSON reste disponible pour compatibilité de commandes/Kgui historiques.

Audit durable V2:

```text
audit.db
```

La suppression d'une faction ne purge pas automatiquement son historique Audit V2.

## Map

La carte V2 ne repose pas sur un refresh tick/yaw.

Auto-map:

```text
changement de chunk
→ 1 rendu
```

Rester immobile et tourner la caméra ne doit pas spammer la map.

## API

Ancienne API:

```text
KfactionAPI
```

conservée pour compatibilité.

Nouvelle frontière:

```text
KfactionApiV2 2.2.0
```

Le nouveau code ne doit pas conserver des références `Faction`/`FPlayer` live.

## Après migration

Exécuter:

```text
/kf doctor
/kf doctor indexes full
/kf doctor progression full
/kf version
```

Puis vérifier:

```text
kfaction.db
audit.db
```

et tester:

- membership/rôles;
- claims/unclaims;
- claim groups;
- zones;
- grace;
- home/warps;
- économie;
- progression;
- map;
- intégrations.
