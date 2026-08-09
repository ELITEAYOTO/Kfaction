# Kfaction V2 — Operations & Diagnostics

## Commandes principales staff

```text
/kf doctor
/kf doctor full

/kf doctor runtime
/kf doctor storage
/kf doctor audit
/kf doctor integrations
/kf doctor indexes
/kf doctor progression

/kf doctor indexes full
/kf doctor progression full

/kf version

/kf audit help
/kf audit status
/kf audit recent [limit]
/kf audit search ...
```

Le doctor est diagnostic/read-only.

Il ne doit pas:

- créer de FPlayer;
- mark dirty;
- sauvegarder;
- rebuild automatiquement;
- reload automatiquement;
- rejouer une reward progression.

## Storage health

Normal attendu après activité faible:

```text
writer queue faible
rejected=0
pending deletes=0
```

Lot 23 expose:

- queue size;
- queue capacity;
- accepted tasks;
- rejected tasks;
- pending faction deletes;
- pending player deletes.

Config:

```yaml
storage:
  writer:
    queue-capacity: 256
    shutdown-enqueue-timeout-seconds: 10
```

Une saturation ne déclenche jamais une écriture SQLite via CallerRuns sur Bukkit main thread.

## Audit health

Audit entries:

```text
ArrayBlockingQueue bornée
```

Audit queries:

```yaml
audit:
  query-queue-capacity: 64
```

`/kf doctor audit` doit normalement indiquer:

```text
dropped entries = 0
rejected queries = 0
failed queries = 0
```

## SQLite

Fichiers:

```text
plugins/Kfaction/kfaction.db
plugins/Kfaction/audit.db
```

Le main DB et l'audit DB sont séparés volontairement.

## Shutdown

Le storage final suit:

```text
saveAllSync
→ flush pending deletes
→ shutdown writer
→ await
→ close storage
```

Une incapacité à confirmer le snapshot final ou les deletes produit un log `SEVERE`.

## Disband

Le lifecycle V2 nettoie:

- membership;
- claims/index;
- coffre;
- relations;
- requêtes;
- index faction;
- storage;
- caches runtime progression.

L'historique `audit.db` reste conservé.

## Incident storage

Si `/kf doctor storage` montre:

```text
queue proche de 100%
rejected > 0
pending deletes persistants
```

vérifier prioritairement:

- latence disque;
- espace disque;
- permissions du dossier plugin;
- verrouillage externe de SQLite;
- erreurs SQLite console.

Ne pas augmenter aveuglément la capacité de queue pour masquer un stockage trop lent.

## Incident audit

Si:

```text
audit dropped > 0
```

le writer audit n'arrive pas à suivre.

Si:

```text
query rejected > 0
```

trop de recherches sont demandées par rapport au débit de lecture.

Les deux phénomènes sont distincts.

## Validation release

Avant release:

```bash
mvn clean package
```

Puis:

```text
/kf version
/kf doctor full
/kf doctor indexes full
/kf doctor progression full
```

Attendu:

```text
0 ERROR critique
storage connected
schema payload = 9
API = 2.2.0
```
