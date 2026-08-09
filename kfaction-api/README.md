# Kfaction API 2.3

`kfaction-api` contient le contrat public stable. Le contrat 2.2 a conserve ses
noms de packages et signatures ; `KfactionApiV23` est une extension additive.
Le plugin final embarque une seule copie de ces classes.

## Services Bukkit publies

```java
KfactionApiV2 reads = KfactionApis.get();
KfactionApiCompatibility state = KfactionApiCompatibility.evaluate(reads);

KfactionApiV23 reads23 = KfactionApis.getV23();
KfactionPlayerActions actions = KfactionApis.getPlayerActions();
```

Etats possibles : `ABSENT`, `INCOMPATIBLE`, `READY_2_2`, `READY_2_3`.
Une integration optionnelle doit desactiver seulement ses fonctions Kfaction
quand le service manque ou que le major est incompatible.

## Lectures 2.3

Les claims, warps, logs, invitations, demandes de relation, relations, ACL et
parametres sont exposes sous forme de snapshots immuables. Les lectures de
collections utilisent `PageRequest` : limite 45 par defaut, plafond absolu 100.
`FactionView.getRevision()` permet de detecter une vue obsolete.

## Actions joueur

`KfactionPlayerActions` applique les parcours joueur complets sur le thread
principal : permissions, hierarchie, couts, economie, limites, stockage et
evenements. Un clic GUI fournit un `OperationContext` dont la source est
`OperationSource.GUI`. Il ne doit jamais executer une commande `/f` ni appeler
une mutation trusted pour contourner une regle joueur.

Les resultats utilisent `ApiResult<T>` avec statut, `messageKey` et detail.
Les deux parcours de progression actuellement automatiques repondent
explicitement `UNAVAILABLE` au lieu de simuler une mutation non sure.

## Evenements publics 2.3

- `FactionSnapshotChangedEvent` : faction, revision, champs et joueurs affectes ;
- `PlayerFactionChangedEvent` : changement d'appartenance ;
- `FactionField` : granularite d'invalidation pour les consommateurs.

Les commandes existantes et la facade publique s'appuient sur les memes
services metier (`MembershipService`, `RoleService`, `FactionLifecycleService`,
`HomeWarpService`, economie et services de claim/relation).
