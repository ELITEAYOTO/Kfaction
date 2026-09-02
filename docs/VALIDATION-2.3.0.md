# Validation Kfaction 2.3.0

Date : 2 septembre 2026.

## Validation automatisée

- build complet : `mvn clean verify` ;
- Java cible : 8 ;
- tests : 110, 0 échec, 0 erreur, 0 ignoré ;
- ressources YAML vérifiées, dont le mode uniforme de `/f map` ;
- API V2.2 conservée et API V2.3 exposée séparément.
- JAR Java 8 (major 52), 14 761 744 octets ;
- SHA-256 :
  `78a06f454da488a2ddc09131a1f5d81d5b63644d76d049ac37c048efe0228183`.

## Démarrage isolé

Le JAR a été démarré deux fois avec KHopeSpigot/PandaSpigot `e9f9c73`, Java
8 et SQLite, sans aucun autre plugin :

- premier démarrage Kfaction : 209 ms ;
- second démarrage Kfaction : 235 ms ;
- `/kf version` : plugin/API 2.3.0, storage connecté, payload 9 ;
- `/kf doctor full` après chaque démarrage : 29 OK, 8 informations,
  0 avertissement, 0 erreur ;
- `/f` depuis la console affiche bien l'aide joueur ;
- reload : 22 ms, sans erreur ;
- deux arrêts propres : 19 ms chacun.

Les dépendances optionnelles absentes sont signalées comme informations et ne
bloquent pas le cœur du plugin.

## Validation encore nécessaire en jeu

- contrôler visuellement `/f map` avec le pack OptiFine final ;
- vérifier le survol des membres sur un vrai client 1.8.9 ;
- tester les décisions `SPAWNER_PLACE`, `SPAWNER_BREAK` et
  `SPAWNER_INTERACT` avec KSpawner 2.0.0 et plusieurs rangs de faction ;
- rejouer les scénarios de claim/raid avec plusieurs joueurs.

Ces points ne sont pas déclarés validés par le seul test console.
