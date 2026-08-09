# Kfaction V2 — Console logging

Lot25A introduit:

```text
me.krunsh.kfaction.util.KfactionLogger
```

Palette:

```text
or       identité / banner
vert     succès
blanc    info
jaune    warning réel
rouge    erreur
violet   reload
gris     debug
cyan     sections debug
```

Symboles optionnels:

```text
✔
⚠
✖
↻
```

Config:

```yaml
console:
  colors:
    enabled: true
  symbols:
    enabled: true
  startup:
    compact: true
    show-timings-in-debug: true
```

Règle:

```text
état normal
→ INFO/SUCCESS ou aucune ligne

dépendance optionnelle absente
→ DEBUG sauf si explicitement requise

erreur récupérable
→ WARN

erreur empêchant une fonction obligatoire
→ ERROR
```

Lot25A utilise déjà ce logger pour le chargement progression et les nouveaux
réglages déplacés.

La réorganisation complète de `Kfaction#onEnable()` sera appliquée dans un lot
bootstrap dédié afin de ne pas remplacer une version locale de Kfaction.java
avec une base plus ancienne.
