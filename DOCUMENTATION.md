# Kfaction V2.2 — Documentation Index

> Documentation canonique après le Lot25D. Les anciens guides V1 ont été remplacés ou marqués historiques afin de ne plus contredire le runtime V2.

## Contrats principaux

```text
Minecraft / Spigot: 1.8.8
Java: 8
API publique: KfactionApiV2 2.2.0 / major 2
Storage payload: schema 9
Global Zones payload: schema 2
Backend recommandé: SQLite
Progression: progression.yml uniquement
Rôles: RECRUIT, MEMBER, OFFICER, MODERATOR, COLEADER, LEADER
```

## Documentation à lire

- `docs/ARCHITECTURE.md` — architecture et invariants V2.2.
- `docs/API-V2.md` — contrat API public frozen 2.2.0.
- `docs/API-FREEZE-2.2.0.txt` — résumé du freeze API.
- `docs/PERMISSIONS.md` — permissions Bukkit, FactionCapabilities et TerritoryActions.
- `docs/DYNAMIC-ZONES.md` — Global Zones configurables.
- `docs/RESOURCES-V2.md` — fichiers YAML réellement utilisés.
- `LEVEL_SYSTEM.md` — progression `progression.yml` actuelle.
- `docs/INTEGRATIONS.md` — hooks optionnels.
- `docs/COMPATIBILITY.md` — matrice Java/Minecraft/API/intégrations.
- `docs/CONSOLE-STARTUP.md` — logs compact/debug.
- `docs/OPERATIONS.md` — opérations/admin/diagnostics.
- `docs/RELEASE-CHECKLIST.md` — validation avant release.
- `docs/KGUI-V2-HANDOFF.md` — frontière à utiliser pour Kgui V2.
- `docs/FINAL-AUDIT-25D.md` — anomalies trouvées et corrections du dernier audit.

## Resources runtime

```text
src/main/resources/
├── config.yml
├── messages.yml
├── plugin.yml
└── progression.yml
```

`levels.yml`, `quests.yml` et `progression.example.yml` ne sont plus des configs runtime.

## Commandes importantes de validation

```text
/kf version
/kf doctor full
/kf doctor indexes full
/kf doctor progression full
/kf doctor zones full
/kf audit status
```

## Kgui

Le nouveau Kgui doit passer par:

```text
KfactionApis.get()
→ KfactionApiV2 2.2
→ snapshots / ApiResult
```

et ne pas lire directement les managers/domain live.
