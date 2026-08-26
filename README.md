# Kfaction

Kfaction est le plugin de factions de Volkaria pour Minecraft 1.8.8. La
branche `refactor/kfaction-2.3` contient l'architecture Maven multimodule
canonique : une API publique stable et le plugin serveur.

## État de la branche 2.3

- `/f` ouvre l'aide ; `/f f` et `/f show` affichent une faction.
- la fiche faction sépare membres connectés et déconnectés ;
- rang faction, rang serveur optionnel et argent sont visibles au survol ;
- annonces de territoire dans le chat par défaut avec couleurs cohérentes ;
- placeholders de membres connectés triés par rôle puis par nom ;
- intégration ShopGUIPlus optionnelle et fail-closed, sans JAR privé au build ;
- compilation reproductible sous Java 8, sans dépendance `systemPath`.

La refonte d'alignement de `/f map` reste un lot distinct et n'est pas annoncée
comme terminée dans cette branche.

## Compiler

Prérequis : JDK 8 et Maven 3.9.x.

```powershell
$env:JAVA_HOME = "C:\Program Files\Eclipse Adoptium\jdk-8.0.482.8-hotspot"
$env:Path = "$env:JAVA_HOME\bin;$env:Path"
mvn --batch-mode --no-transfer-progress clean verify
```

Artefact serveur :
`kfaction-plugin/target/Kfaction-2.3.0.jar`.

## Dépendances runtime

Spigot/PandaSpigot 1.8.8 est requis. Vault, PlaceholderAPI, LuckPerms,
WorldGuard, ShopGUIPlus et les plugins Volkaria associés sont optionnels ;
chaque bridge absent reste désactivé proprement.

## Documentation

- [Affichage et migration 2.3](docs/KFACTION-2.3-DISPLAY.md)
- [API V2](docs/API-V2.md)
- [Intégrations](docs/INTEGRATIONS.md)
- [Opérations](docs/OPERATIONS.md)
- [Checklist de release](docs/RELEASE-CHECKLIST.md)

## Licence

Code propriétaire Volkaria, visible publiquement pour audit et collaboration.
Voir [LICENSE](LICENSE). Les dépendances tierces ne sont pas redistribuées.
