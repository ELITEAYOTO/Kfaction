# Kfaction V2.2 — Release / Freeze Checklist

## 1. Build

```bash
mvn clean package
```

Attendu:

```text
BUILD SUCCESS
Failures: 0
Errors: 0
```

Tous les tests `src/test/java` doivent passer, notamment les contrats API/storage/relations/power/progression.

## 2. Freeze API

```text
KfactionApiV2.API_VERSION = 2.2.0
KfactionApiV2.API_MAJOR = 2
```

`KfactionApiV2ContractTest` doit passer.

## 3. Storage

```text
StorageSnapshot.CURRENT_SCHEMA_VERSION = 9
Global Zones payload = schema 2
```

SQLite doit démarrer connecté avec writer borné et zéro delete pending après un shutdown propre.

## 4. Resources

Le JAR doit embarquer:

```text
config.yml
messages.yml
plugin.yml
progression.yml
```

Il ne doit plus embarquer comme runtime progression:

```text
levels.yml
quests.yml
progression.example.yml
```

Au second boot, aucun faux WARN `Could not save ... already exists` ne doit apparaître.

## 5. Membership

Tester avec:

```yaml
factions:
  members:
    max-per-faction: 50
```

Vérifier:

- join jusqu'à la limite effective;
- rewards `members_limit_increase` prises en compte;
- `/f show` affiche la même limite que MembershipService/API;
- aucun fallback historique à 15.

## 6. Relations

Tester:

```text
/f ally
/f truce
/f enemy
/f neutral
```

Cas obligatoires:

- ALLY et TRUCE ont des demandes distinctes;
- une demande expirée ne peut plus être acceptée;
- limite ALLY contrôlée sur les deux factions;
- limite TRUCE contrôlée sur les deux factions;
- `require-mutual=false` produit quand même une relation symétrique immédiate;
- ENEMY reste volontairement unilatéral;
- NEUTRAL nettoie les deux directions;
- restart conserve relations et demandes valides.

## 7. Power

Tester:

- `power.min` négatif réellement atteint;
- perte à la mort;
- gain au kill;
- regen une fois/minute;
- `offline-regen` true/false;
- bonus LuckPerms VIP/MVP/LEGEND augmente réellement le plafond;
- `/kf reload` recharge `power.start`/`power.max` pour les profils nouveaux et chargés;
- aucune mutation FPlayer async.

## 8. Claims / Dynamic Zones

Claims:

- single;
- radius;
- fill;
- unclaim single/radius/all;
- Claim Groups;
- overclaim si applicable.

Zones:

```text
/kf zone list
/kf zone claim <zone>
/kf zone unclaim
/kf zone auto <zone>
/kf zone auto off
/kf zone autounclaim <zone>
/kf zone reload
```

Créer une zone custom, par exemple `avant_post`, et tester:

- build/break;
- containers;
- PvP;
- SET_HOME;
- Ender Pearl;
- pistons;
- fluid flow;
- fire spread;
- explosion block damage;
- Enderman/entity grief;
- Wither spawn;
- `/f map` symbole/couleur/titre;
- restart/persistence.

Supprimer temporairement sa section config et vérifier l'état orphelin fail-closed avec:

```text
/kf doctor zones full
```

## 9. Grace

- start;
- extend;
- stop;
- expiration;
- raid block;
- explosion protection;
- restart.

## 10. Home / Warps

- sethome/home;
- setwarp/warp;
- password correct/incorrect;
- timeout/cancel du prompt;
- warmup déplacement;
- restart avec monde temporairement non chargé.

## 11. Economy

- coût `/f create` à 0;
- coût > 0 avec Vault;
- coût > 0 sans Vault => création refusée;
- retrait échoué => aucune faction créée;
- échec de création après débit => remboursement;
- deposit/withdraw banque.

## 12. Progression

- `progression.yml` chargé au premier boot;
- quest progress;
- level transition;
- rewards;
- pending reward safety;
- restart;
- API `getProgression/getProgressionQuests` ne change pas `lockedTier` et n'écrit rien.

## 13. Map

- `/f map`;
- `/f auto claim`, `/f autoclaim`, `/f ac`;
- map auto activée;
- tourner dans le même chunk => aucun refresh;
- changer de chunk => un refresh;
- aucune charge de chunk forcée.

## 14. Diagnostics

```text
/kf version
/kf doctor full
/kf doctor indexes full
/kf doctor progression full
/kf doctor zones full
/kf audit status
```

Attendu:

```text
API 2.2.0
payload schema 9
storage connected
0 index mismatch
0 zone orphan non voulu
0 pending progression reward incohérente
writer/audit queues normales
```

## 15. Integrations

Tester selon disponibilité:

```text
Vault
PlaceholderAPI
LuckPerms
Kchat
Kcore
Kclassement
Kgui legacy
WorldGuard
CombatTagPlus
ShopGUIPlus
Kcraft
KSpawner
Kminerai/Kgenerator
```

Une dépendance optionnelle absente ne doit pas casser le core.

## 16. Restart / shutdown

Après scénario complet:

1. stop propre;
2. vérifier absence d'erreur de flush;
3. redémarrer;
4. `/kf doctor full`;
5. vérifier `kfaction.db`;
6. vérifier `audit.db`;
7. vérifier coffres/home/warps/zones/progression.

## GO / NO-GO

GO pour freeze Kfaction si:

```text
Maven green
tests green
boot debug=false green
boot debug=true green
doctor full green
doctor zones green
restart green
API 2.2 contract green
schema contract green
```

Après GO:

```text
KFACTION V2.2 = FEATURE COMPLETE / API FROZEN
```

et les nouvelles fonctions UI doivent partir dans Kgui V2 plutôt que réouvrir l'architecture Kfaction sans bug concret.
