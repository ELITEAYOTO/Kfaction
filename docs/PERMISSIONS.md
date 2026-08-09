# Kfaction V2.2 — Permissions

Kfaction possède trois systèmes distincts.

## 1. Permissions Bukkit / LuckPerms

Elles contrôlent l'accès aux commandes/fonctions du plugin.

### Global Zones

```text
kfaction.admin.zone
```

Groupe parent:

```text
kfaction.admin.zone.claim
kfaction.admin.zone.unclaim
kfaction.admin.zone.auto
kfaction.admin.zone.info
kfaction.admin.zone.list
kfaction.admin.zone.reload
```

Exemples LuckPerms:

```text
/lp group admin permission set kfaction.admin.zone true
```

ou plus limité:

```text
/lp group modo permission set kfaction.admin.zone.info true
/lp group modo permission set kfaction.admin.zone.list true
```

`kfaction.admin` contient automatiquement `kfaction.admin.zone`.

## 2. FactionCapabilities

Droits internes d'un rôle de faction.

Rôles:

```text
RECRUIT
MEMBER
OFFICER
MODERATOR
COLEADER
LEADER
```

Capabilities principales:

```text
INVITE
KICK
PROMOTE
DEMOTE
BAN
UNBAN

CLAIM
UNCLAIM
AUTO_CLAIM

SET_HOME
USE_HOME

SET_WARP
DELETE_WARP
USE_WARP

DEPOSIT_MONEY
WITHDRAW_MONEY
PAY_FACTION

RELATION_ALLY
RELATION_ENEMY
RELATION_NEUTRAL
RELATION_TRUCE

RENAME
EDIT_DESCRIPTION
EDIT_TAG
EDIT_PERMISSIONS
MANAGE_CLAIM_GROUPS
VIEW_LOGS

DISBAND
TRANSFER_LEADERSHIP

FLY
FACTION_CHEST

TNT_DEPOSIT
TNT_WITHDRAW
TNT_FILL
```

Ces droits ne remplacent pas les permissions Bukkit.

## 3. TerritoryActions

Elles contrôlent ce qui est permis dans un territoire/Claim Group/Global Zone.

Liste:

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

Exemple:

```yaml
zones:
  avant_post:
    default-policy: DENY
    allowed-actions:
      - ENTER
      - SWITCH
      - CONTAINER_OPEN
    denied-actions:
      - SET_HOME
```

## Priorité Global Zone

Quand un chunk est une Global Zone:

```text
Global Zone rule
    ↓
fin de décision territoire
```

Elle est évaluée avant le moteur de faction/raid/Claim Group.

Pour une zone:

```text
DENY explicite
→ priorité maximum

ALLOW explicite
→ autorisé

sinon
→ default-policy
```

## Claim Groups

Pour les claims faction normaux:

```text
ALLOW
DENY
INHERIT
```

`INHERIT` retourne aux ACL faction/relation.

## LuckPerms contexts

Toujours disponibles:

```text
kfaction:has-faction
kfaction:faction
kfaction:faction-tag
kfaction:role
```

Ils sont indépendants des Global Zones.
## Hiérarchie anti-inside

Une `FactionCapability` ne permet jamais de contourner la hiérarchie des rôles.

Pour les mutations sensibles joueur -> joueur (`KICK`, `PROMOTE`, `DEMOTE`,
`/f mod`), l'acteur doit avoir un rôle **strictement supérieur** à la cible.

Exemples:

```text
COLEADER -> MODERATOR   autorisé si capability correspondante
COLEADER -> COLEADER    refusé
MODERATOR -> COLEADER   refusé
LEADER -> COLEADER      autorisé
```

`/f mod` choisit désormais `PROMOTE` ou `DEMOTE` selon le sens réel du
changement de rang. Les opérations staff/API explicitement trusted passent,
elles, par `RoleService` avec leur `OperationContext`.

