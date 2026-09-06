# Hygiène de l'historique

Le 6 septembre 2026, l'historique de KfactionV2.2 a été réécrit pour retirer
14 chemins de JAR tiers. Les branches `main` et `refactor/kfaction-2.3`, ainsi
que les tags `archive/kfaction-v1`, `v1.0.0` et `v2.3.0`, ont changé de SHA.

Les dépendances nécessaires au build doivent être obtenues auprès de leurs
sources autorisées et conservées localement, hors Git. La purge a préservé les
fichiers de travail locaux et n'a remplacé aucun JAR de release ni déployé le
plugin. Elle ne constitue pas une vérification complète des licences.

Pour reprendre le travail, un clone neuf est recommandé. Conserver séparément
les modifications non publiées d'un ancien clone, puis reporter uniquement les
changements source vérifiés. Ne pas fusionner ni pousser ses anciennes branches
ou ses anciens tags : ils peuvent réintroduire les dépendances retirées.

Ne jamais ajouter de secrets, configurations privées ou sauvegardes au dépôt.
Des copies de l'ancien historique peuvent subsister dans les clones, forks,
caches ou références de pull requests de GitHub.


## Contrôle de non-réintroduction

Le workflow `Repository hygiene` récupère les branches et tags et vérifie les
chemins de JAR dans tous leurs historiques accessibles, pas seulement au dernier
commit. Il inspecte les arbres Git sans lire les blobs ni exécuter les dépendances.
Un historique incomplet ou une erreur Git fait échouer le contrôle.

Ce contrôle est distinct du build Java, des tests fonctionnels et d'un audit de
secrets/licences. Il détecte une réintroduction après un push ; rendre son succès
obligatoire avant fusion nécessite une règle de protection de branche distincte.
Le `.gitignore` limite les ajouts accidentels, mais un ajout forcé reste possible.
