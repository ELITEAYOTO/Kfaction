# Kfaction V2.2 — Final Audit Lot25D

## Base auditée

Le Lot25D a été construit à partir du **ZIP complet fourni par l'utilisateur**,
pas à partir du GitHub V1 ni d'un ancien Lot isolé.

Projet audité:

```text
src/main/java      ~226 classes Java production
src/test/java      tests contract/hardening
src/main/resources config.yml, messages.yml, plugin.yml, progression.yml
pom.xml             Maven / Java 8
```

Objectif: fermer les incohérences fonctionnelles restantes avant le freeze API
et le passage à Kgui V2.

---

## Correctifs fonctionnels majeurs

### 1. Relations V2

`RelationManager` utilisait encore plusieurs anciennes clés et la logique de
requête n'était pas totalement cohérente.

Corrigé:

```text
relations.request-expiration-seconds
relations.ally.enabled
relations.ally.max-per-faction
relations.ally.require-mutual
relations.truce.enabled
relations.truce.max-per-faction
relations.truce.require-mutual
relations.enemy.enabled
relations.enemy.max-per-faction
```

Les requêtes ALLY/TRUCE sont désormais typées, expirables et vérifiées des deux
côtés au commit. Une demande ALLY ne peut plus servir accidentellement à
accepter une TRUCE.

ALLY/TRUCE sont mutuelles; ENEMY reste volontairement unilatéral; NEUTRAL
nettoie les deux directions et les demandes pendantes.

### 2. Limite de membres

Le runtime de join lisait encore l'ancien:

```text
factions.max-members
```

avec fallback 15 alors que la configuration V2 indique:

```text
factions.members.max-per-faction: 50
```

`MembershipService`, `/f show` et l'API utilisent maintenant la même source de
vérité, avec prise en compte de `Faction.extraMembers` provenant de la
progression.

### 3. Power / threading

La régénération Power mutait encore des objets Bukkit/domain depuis un scheduler
async.

Corrigé:

```text
regen Power -> Bukkit main thread
power.min réellement respecté
power.max + bonus permissions réellement utilisables
power.per-kill
power.loss-per-death
power.offline-regen
```

`FPlayer` accepte maintenant les valeurs négatives configurées et possède un
setter prenant en compte le maximum effectif avec bonus.

`/kf reload` recharge également le cache start/max de `FPlayerManager` et
réapplique proprement les nouvelles bornes aux profils chargés.

### 4. Création faction / économie

`CreateCommand` utilisait une ancienne clé et pouvait créer gratuitement une
faction lorsque le coût était positif mais Vault indisponible.

Corrigé:

```text
economy.faction-create-cost
```

Le débit est vérifié. Si la création échoue après débit, Kfaction tente un
remboursement et journalise une erreur sévère si la compensation échoue.

### 5. Dynamic Global Zones et protections environnementales

Les zones custom pouvaient être vues comme Wilderness par certaines protections
historiques.

Ajouts `TerritoryAction`:

```text
PISTON
FLUID_FLOW
FIRE_SPREAD
EXPLOSION_BLOCK_DAMAGE
ENTITY_GRIEF
WITHER_SPAWN
```

`ExploitProtectionListener` consulte maintenant `ZoneService` avant la logique
de claim faction pour les interactions environnementales, hopper, pearl,
explosions et Wither.

### 6. Progression API read-only

Les lectures API progression pouvaient appeler un chemin qui réconciliait ou
verrouillait de l'état.

Nouveau chemin:

```text
ProgressionPolicy.resolveLockedTierReadOnly
ProgressionPolicy.viewsReadOnly
ProgressionService.peekStatus
ProgressionService.peekQuestViews
QuestManager.peekStatus
QuestManager.peekQuestViews
```

`KfactionApiProvider` utilise ces méthodes pour les lectures API V2.2.

### 7. Rôles / anti-inside

`/f promote` et `/f demote` mutaient encore directement `Faction` et
synchronisaient manuellement `FPlayer`.

Ils passent maintenant par:

```text
FactionCapability
RoleService
OperationContext COMMAND
```

et exigent que le rôle de l'acteur soit strictement supérieur à celui de la
cible.

`/f mod` est également corrigé:

```text
cible < MODERATOR -> nécessite PROMOTE
cible > MODERATOR -> nécessite DEMOTE
```

avec hiérarchie strictement supérieure. Les casts dangereux
`(Player) OfflinePlayer` ont été remplacés par `target.getPlayer()`.

### 8. ApiResult

`ApiResult.failure(SUCCESS, ...)` et `failure(NO_CHANGE, ...)` sont désormais
refusés par `IllegalArgumentException`.

---

## Nettoyage configuration / resources

Resources runtime finales:

```text
config.yml
messages.yml
plugin.yml
progression.yml
```

Absents:

```text
levels.yml
quests.yml
progression.example.yml
```

Les anciennes clés sans lecteur runtime ont été retirées de `config.yml` afin
que la documentation ne promette pas des fonctions non utilisées.

Audit littéral final des appels `ConfigManager`:

```text
0 clé principale référencée mais absente
```

Audit messages ciblé:

```text
0 clé MessageManager référencée mais absente
```

Audit permissions Bukkit littérales:

```text
0 permission utilisée mais non déclarée dans plugin.yml
```

---

## Logging / legacy

Supprimé des sources production:

```text
System.out
printStackTrace()
TODO
FIXME
JsonParser.parseString
me.krunsh.kfaction.util.KfactionLogger
```

Package logger canonique:

```text
me.krunsh.kfaction.utils.KfactionLogger
```

Les toggles `logs.types.*` contrôlent maintenant réellement le cache legacy
`/f logs`, tandis que `audit.db` reçoit toujours les événements structurés.

---

## Tests ajoutés / renforcés

```text
ApiResultHardeningTest
FactionRelationRequestContractTest
FPlayerPowerContractTest
ProgressionPolicyTest (read-only)
BundledProgressionExampleTest réaligné sur progression.yml
```

Les anciens tests contradictoires sur `progression.example.yml` ont été
réalignés avec la ressource V2 active `progression.yml`.

---

## Compatibilité volontairement conservée

Quelques façades legacy restent présentes intentionnellement:

- `GlobalZoneType.SAFEZONE/WARZONE` pour API 2.x historique;
- `FactionManager` wrappers qui délèguent aux services V2;
- méthodes `QuestManager` deprecated qui délèguent à `ProgressionService`;
- `AdminCommand` ancien comme delegate de `/kf`, tandis que les mutations staff
  critiques sont interceptées par `KfactionAdminCommand` et passent par les
  services V2;
- `getFPlayer()` pour compatibilité V1 sur certains chemins de mutation sync.

Ces éléments ne sont pas la source de vérité du nouveau code.

---

## Validation locale effectuée

Smokes Java 8 ciblés réussis pendant le Lot25D:

```text
LOT25D_PURE_CORE_JAVA8_COMPILE_OK
LOT25D_DOMAIN_JAVA8_COMPILE_OK
LOT25D_RELATION_MANAGER_JAVA8_COMPILE_OK
LOT25D_POWER_MANAGER_JAVA8_COMPILE_OK
LOT25D_FPLAYER_MANAGER_JAVA8_COMPILE_OK
LOT25D_ROLE_COMMANDS_FINAL_JAVA8_COMPILE_OK
```

YAML:

```text
config.yml       OK
messages.yml     OK
plugin.yml       OK
progression.yml  OK
```

Scan Java 8 évident:

```text
List.of / Set.of / Map.of : 0
record                    : 0
var local                 : 0
String.isBlank/strip      : 0
switch ->                 : 0
```

### Limite de validation

L'environnement de travail ne possède pas l'exécutable Maven (`mvn`) ni de
wrapper Maven dans le projet. Le build d'intégration complet n'est donc **pas
revendiqué comme exécuté localement**.

Source de vérité finale:

```bash
mvn clean package
```

---

## GO freeze

Si le build utilisateur est vert et le smoke runtime du RELEASE-CHECKLIST est
vert, le statut recommandé devient:

```text
Kfaction V2.2
FEATURE COMPLETE
API 2.2.0 FROZEN
READY FOR KGUI V2
```
