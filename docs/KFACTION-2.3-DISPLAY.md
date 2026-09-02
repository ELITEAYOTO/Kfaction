# KFaction 2.3 — affichage, configuration et migration

## Commandes

- `/f` affiche toujours l'aide des commandes joueur.
- `/f f` affiche sa propre faction.
- `/f show [faction]` affiche sa faction ou celle indiquée.

La fiche présente le chef, le nombre connecté/total, la capacité maximale,
les sections connectés et déconnectés, le power, les claims, la banque et les
relations. Le tri est : rang faction décroissant, puis pseudo alphabétique.

Chaque pseudo dispose d'un survol lisible par tous : rang faction, rang
serveur si Vault Chat est disponible, et argent. Les détails Vault sont mis en
cache afin qu'une consultation par plusieurs membres ne répète pas les mêmes
lectures économie.

## Configuration

```yaml
faction-show:
  names-per-line: 8
  member-details-cache-seconds: 15
  server-rank:
    enabled: true
```

`names-per-line` est borné entre 1 et 15. Le cache est borné entre 1 et
300 secondes dans le code. Tous les textes se trouvent sous
`show.display` dans `messages.yml`, y compris le contenu du survol.

Le rendu 2.3 utilise `show.display` comme source unique. Un ancien bloc
`show.format` éventuellement présent sur disque est ignoré et peut être retiré.

## Territoires

Valeurs par défaut :

- claim personnel : vert clair (`&a`) ;
- claim d'une autre faction : rouge clair (`&c`) ;
- Wilderness : gris (`&7`) ;
- WarZone : rouge foncé (`&4`) ;
- SafeZone : vert foncé (`&2`).

`territory.use-titles: false` envoie le changement dans le chat. Passer cette
option à `true` réactive les titres centraux.

## Carte des territoires

Le mode uniforme est actif par défaut : toutes les cases non-joueur utilisent
le même glyphe et seule leur couleur change. Cela évite les colonnes décalées
par la police proportionnelle de Minecraft 1.8, tout en conservant la flèche
d'orientation du joueur.

```yaml
map:
  claimed-symbol-mode: "FIXED"
  uniform-cells:
    enabled: true
    symbol: "■"
```

Le glyphe uniforme et les huit flèches sont limités au premier caractère
visible. Mettre `uniform-cells.enabled: false` réactive les symboles propres aux
zones et les modes `TAG_INITIAL` / `NAME_INITIAL`, sans garantie d'alignement
si les glyphes choisis n'ont pas la même largeur visuelle.

## Migration non destructive

Au chargement et au reload, Kfaction attache désormais les ressources YAML
embarquées comme valeurs par défaut. Les nouvelles clés fonctionnent donc sur
une ancienne installation sans écraser ni réordonner ses fichiers.

- une clé déjà présente sur disque garde toujours sa valeur ;
- une clé absente utilise le défaut embarqué ;
- pour rendre le choix explicite, recopier les sections ci-dessus dans les
  fichiers serveur puis exécuter le reload Kfaction.

## PlaceholderAPI / KTab

```text
%kfaction_online_member_1%
%kfaction_online_member_1_name%
%kfaction_online_member_1_role%
```

L'index commence à 1. Un slot vide renvoie une chaîne vide. Les membres sont
triés par rang puis par pseudo. Les appels consécutifs partagent pendant
100 ms les snapshots joueur/faction et la liste déjà triée : un affichage TAB
ne reconstruit pas la faction pour chaque ligne.

## ShopGUIPlus

Le bridge écoute `ShopPostTransactionEvent` à `MONITOR`. Son contrat est
résolu à l'exécution ; Kfaction ne compile et ne redistribue aucun JAR
ShopGUIPlus. Si une future version casse ce contrat, le bridge est désactivé
avec un avertissement unique et les quêtes de vente restent fail-closed.

## Validation restante

Le rendu final doit encore être contrôlé en jeu avec le pack de ressources du
serveur, car OptiFine peut remplacer la police utilisée par le client.
