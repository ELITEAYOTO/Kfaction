# Kfaction V2 — Resources après Lot25A

## Ressources actives

Le projet doit maintenant contenir:

```text
src/main/resources/
├── plugin.yml
├── config.yml
├── messages.yml
└── progression.yml
```

D'autres ressources non liées à la progression peuvent évidemment rester si
le projet en possède.

## À supprimer

```text
src/main/resources/levels.yml
src/main/resources/quests.yml
src/main/resources/progression.example.yml
```

Ces trois fichiers ne doivent plus servir de configuration runtime.

## progression.yml

`progression.yml` est embarqué directement dans le JAR.

Au premier lancement:

```text
plugins/Kfaction/progression.yml absent
→ saveResource("progression.yml", false)
→ chargement du fichier
```

Aux lancements suivants:

```text
fichier présent
→ aucun saveResource
→ aucun faux WARN "already exists"
→ fichier serveur conservé
```

Kfaction ne doit jamais écraser automatiquement une progression personnalisée.

## levels.yml

Les dernières responsabilités ont été déplacées:

```text
progress bar
→ config.yml / progression-ui.progress-bar

faction chest save-on-close
→ config.yml / faction-chest.save-on-close

fly
→ config.yml / fly

anti-sethome
→ config.yml / anti-sethome
```

`LevelManager#getLevelsConfig()` reste uniquement comme frontière binaire
legacy et renvoie `null`.

## quests.yml

La logique Progression V2 ne dépend plus de `quests.yml`.

Les listeners et bridges alimentent `ProgressionService`, dont la source de
vérité est `progression.yml`.
