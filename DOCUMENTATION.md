# Kfaction - Documentation Complete

> Plugin Factions moderne pour Minecraft 1.8.8 - Ecosysteme SparrowMC

---

## Informations Generales

| Propriete | Valeur |
|-----------|--------|
| **Version** | 1.0.0 |
| **API Minecraft** | 1.8.8 (PandaSpigot-195) |
| **Java** | 1.8 |
| **Auteur** | Krunsh |
| **Package** | `me.krunsh.kfaction` |
| **Stockage** | FlatFile (JSON) |
| **Langue** | 100% Francais |

---

## Table des Matieres

1. [Dependencies](#dependencies)
2. [Commandes](#commandes)
3. [Systeme de Factions](#systeme-de-factions)
4. [Systeme de Power](#systeme-de-power)
5. [Systeme de Claims](#systeme-de-claims)
6. [Relations](#relations)
7. [Roles et Permissions](#roles-et-permissions)
8. [Economie](#economie)
9. [Chat Faction](#chat-faction)
10. [Carte et Visualisation](#carte-et-visualisation)
11. [Home et Warps](#home-et-warps)
12. [Protection des Claims](#protection-des-claims)
13. [Placeholders](#placeholders)
14. [Configuration](#configuration)
15. [Architecture Technique](#architecture-technique)
16. [Etat Actuel et TODO](#etat-actuel-et-todo)

---

## Dependencies

### Dependencies Obligatoires (Hard Depend)

Aucune. Kfaction fonctionne de manière autonome.

### Dependencies Optionnelles (Soft Depend)

| Plugin | Version | Utilisation |
|--------|---------|-------------|
| **Vault** | 1.7+ | Economie (banque de faction) |
| **Kcore** | 1.0.0+ | Systeme de base, utils, config |
| **Kchat** | 1.0.0+ | Format de chat avec prefixe faction |
| **Kgui** | 1.0.0+ | Menus GUI (/f menu) |
| **Kclassement** | 1.0.0+ | Classements faction (power, claims) |
| **PlaceholderAPI** | 2.10+ | Placeholders dynamiques |
| **WorldGuard** | - | Integration regions |
| **CombatTagPlus** | - | Anti-TP en combat |
| **Multiverse-Core** | - | Support multi-mondes |

### Note sur les integrations

Kfaction fonctionne de manière autonome mais s'integre avec l'ecosysteme K de SparrowMC
pour des fonctionnalites etendues (GUI, chat, classements).
Toutes les dependencies sont soft-depend : le plugin fonctionne sans elles.

---

## Commandes

### Commande Principale

```
/f [sous-commande]
```

**Alias disponibles :** `/faction`, `/kf`, `/fac`, `/kfaction`

### Commandes de Base

| Commande | Alias | Description | Permission |
|----------|-------|-------------|------------|
| `/f help` | `?`, `aide` | Affiche l'aide | `kfaction.player` |
| `/f create <nom>` | `creer`, `new` | Creer une faction | `kfaction.create` |
| `/f disband` | `dissoudre`, `delete` | Dissoudre sa faction | Leader uniquement |
| `/f join <faction>` | `rejoindre` | Rejoindre une faction | `kfaction.join` |
| `/f leave` | `quitter` | Quitter sa faction | `kfaction.leave` |
| `/f show [faction]` | `info`, `who` | Voir les infos d'une faction | `kfaction.show` |
| `/f f` | - | Raccourci pour `/f show` (sa faction) | `kfaction.show` |
| `/f list` | `liste` | Lister toutes les factions | `kfaction.list` |
| `/f top` | `ftop` | Classement des factions | `kfaction.list` |
| `/f menu` | `gui` | Ouvrir le menu de faction | `kfaction.menu` |

### Gestion des Membres

| Commande | Alias | Description | Permission Interne |
|----------|-------|-------------|-------------------|
| `/f invite <joueur>` | `inviter` | Inviter un joueur | `INVITE` |
| `/f kick <joueur>` | `exclure` | Exclure un membre | `KICK` |
| `/f promote <joueur>` | `promouvoir` | Promouvoir un membre | `PROMOTE` |
| `/f demote <joueur>` | `retrograder` | Retrograder un membre | `DEMOTE` |
| `/f leader <joueur>` | `chef` | Transferer le leadership | Leader uniquement |
| `/f mod <joueur>` | - | Definir comme moderateur | `PROMOTE` |
| `/f coleader <joueur>` | - | Definir comme coleader | `PROMOTE` |

### Gestion du Territoire

| Commande | Alias | Description | Permission Interne |
|----------|-------|-------------|-------------------|
| `/f claim` | - | Claim le chunk actuel | `CLAIM` |
| `/f unclaim` | - | Unclaim le chunk actuel | `UNCLAIM` |
| `/f autoclaim` | `ac` | Toggle auto-claim en marchant | `CLAIM` |
| `/f unclaimall [confirm]` | `unclaima` | Libere TOUS les claims | `UNCLAIM` |
| `/f map` | `carte` | Affiche la carte des claims | `kfaction.map` |
| `/f map on` | - | Toggle auto-map en marchant | `kfaction.map` |
| `/f coords` | - | Affiche les coordonnees de tous les claims | `kfaction.coords` |

### Home & Warps

| Commande | Alias | Description | Permission Interne |
|----------|-------|-------------|-------------------|
| `/f home` | `hq` | Teleportation au home faction | `HOME` |
| `/f sethome` | `sethq` | Definir le home faction | `SETHOME` |
| `/f warp <nom>` | `w` | Teleportation a un warp | `WARP` |
| `/f warp list` | - | Liste des warps | `WARP` |
| `/f warp delete <nom>` | - | Supprimer un warp | `SETHOME` |
| `/f setwarp <nom>` | `sw` | Creer un warp | `SETHOME` |

### Relations

| Commande | Alias | Description |
|----------|-------|-------------|
| `/f ally <faction>` | `allier` | Proposer/accepter une alliance |
| `/f enemy <faction>` | `ennemi` | Declarer une faction ennemie |
| `/f neutral <faction>` | `neutre` | Retour a relation neutre |
| `/f truce <faction>` | `treve` | Proposer/accepter une treve |

### Chat

| Commande | Alias | Description |
|----------|-------|-------------|
| `/f chat [mode]` | `c` | Changer de mode de chat |
| `/f c f` | - | Chat faction |
| `/f c a` | - | Chat allie |
| `/f c t` | - | Chat treve |
| `/f c p` | - | Chat public |

### Economie

| Commande | Alias | Description | Permission Interne |
|----------|-------|-------------|-------------------|
| `/f deposit <montant>` | `dep` | Deposer dans la banque | `DEPOSIT` |
| `/f withdraw <montant>` | `wit` | Retirer de la banque | `WITHDRAW` |
| `/f power [joueur]` | `p` | Voir le power | `kfaction.power` |

### Administration

| Commande | Description | Permission |
|----------|-------------|------------|
| `/f admin bypass` | Toggle bypass protection | `kfaction.admin.bypass` |
| `/f admin reload` | Recharger la config | `kfaction.admin.reload` |
| `/f admin forcejoin <j> <f>` | Forcer un joueur a rejoindre | `kfaction.admin.forcejoin` |
| `/f admin forceleave <j>` | Forcer un joueur a quitter | `kfaction.admin.forceleave` |
| `/f admin forceleader <f> <j>` | Forcer le leadership | `kfaction.admin.forceleader` |
| `/f admin disband <faction>` | Dissoudre de force | `kfaction.admin.disband` |
| `/f admin setpower <j> <val>` | Modifier le power | `kfaction.admin.setpower` |
| `/f admin rename <f> <nom>` | Renommer une faction | `kfaction.admin.rename` |
| `/f admin inspect <faction>` | Inspecter une faction | `kfaction.admin.inspect` |
| `/f spy` | Toggle espionnage chat faction | `kfaction.admin.spy` |

---

## Systeme de Factions

### Creation

- Nom : 3-16 caracteres alphanumeriques
- Mots interdits configurables (admin, staff, etc.)
- Cout de creation : configurable (0 par defaut)
- Le createur devient automatiquement **Leader**

### Dissolution

- Seul le **Leader** peut dissoudre
- Demande de confirmation requise (`/f disband confirm`)
- Libere automatiquement tous les claims
- Notification broadcast au serveur

### Limites

| Parametre | Valeur Par Defaut | Configurable |
|-----------|-------------------|--------------|
| Membres max | 50 | Oui |
| Nom min | 3 caracteres | Oui |
| Nom max | 16 caracteres | Oui |

---

## Systeme de Power

Le **power** determine la capacite d'une faction a maintenir ses territoires.

### Formule de Claims

```
Claims maximum = Somme du power de tous les membres
```

### Configuration Par Defaut

| Parametre | Valeur |
|-----------|--------|
| Power de depart | 10.0 |
| Power minimum | -10.0 |
| Power maximum | 10.0 |
| Perte par mort | 2.0 |
| Regeneration/minute | 0.1 |
| Multiplicateur en territoire | x1.5 |
| Perte par jour d'inactivite | 1.0 |

### Bonus de Power (Permissions)

Les bonus de rang peuvent être activés ou désactivés globalement avec
`power.bonus.enabled` dans `config.yml`. La désactivation ne modifie ni le
power de base par joueur, ni les boosts/récompenses de faction.

| Permission | Bonus |
|------------|-------|
| `kfaction.power.bonus.vip` | +2.0 |
| `kfaction.power.bonus.mvp` | +5.0 |
| `kfaction.power.bonus.legend` | +10.0 |

---

## Systeme de Claims

### Fonctionnement

- Un **claim** = un **chunk** (16x16 blocs)
- Protection complete des blocs, coffres, interactions
- Visualisation via la carte ASCII (`/f map`)

### Mondes

```yaml
# Mondes autorises (vide = tous)
allowed-worlds: []

# Mondes interdits
denied-worlds:
  - world_nether
  - world_the_end
```

### Zones Speciales

| Zone | Description | Commande Admin |
|------|-------------|----------------|
| **Wilderness** | Zone sauvage (aucune protection) | - |
| **SafeZone** | Zone protegee (pas de PvP, pas de degats) | `/f claim safezone` |
| **WarZone** | Zone de guerre (PvP active, pas de claim) | `/f claim warzone` |

### Surclaim (Overclaim)

**Regle :** Une faction peut se faire surclaim si `claims > power`

- **Deficit** = claims - power
- Les ennemis peuvent surclaim un nombre de chunks = deficit
- Si `power >= claims` : surclaim impossible (territoire securise)
- Le calcul est dynamique (si power remonte, surclaim bloque)

**Exemple :**
- 70 claims / 69 power = deficit 1 = 1 chunk surclaimable
- 70 claims / 60 power = deficit 10 = 10 chunks surclaimables

---

## Relations

### Types de Relations

| Relation | Couleur | Friendly Fire | Description |
|----------|---------|---------------|-------------|
| **MEMBER** | Vert | Non | Meme faction |
| **ALLY** | Violet | Non (configurable) | Alliance mutuelle |
| **TRUCE** | Jaune | Oui (reduit) | Treve temporaire |
| **NEUTRAL** | Blanc | Oui | Par defaut |
| **ENEMY** | Rouge | Oui | Hostilite declaree |

### Limites Par Defaut

| Type | Maximum |
|------|---------|
| Allies | 3 par faction |
| Treves | 5 par faction |
| Ennemis | Illimite |

### Activation/Desactivation

Chaque type de relation peut etre active/desactive dans `config.yml`.

---

## Roles et Permissions

### Hierarchie des Roles

| Role | Prefixe | Priorite | Description |
|------|---------|----------|-------------|
| **LEADER** | ** | 400 | Chef de faction |
| **COLEADER** | *+ | 300 | Co-leader |
| **MODERATOR** | + | 200 | Moderateur |
| **MEMBER** | - | 100 | Membre standard |
| **RECRUIT** | ~ | 0 | Recrue (defaut) |

### Actions Configurables Par Role

| Action | Description |
|--------|-------------|
| `BUILD` | Construire dans le territoire |
| `DESTROY` | Detruire des blocs |
| `CONTAINER` | Ouvrir coffres/fours |
| `BUTTON` | Utiliser boutons/leviers |
| `DOOR` | Ouvrir portes |
| `CLAIM` | Claim des territoires |
| `UNCLAIM` | Unclaim des territoires |
| `INVITE` | Inviter des joueurs |
| `KICK` | Exclure des membres |
| `PROMOTE` | Promouvoir des membres |
| `DEMOTE` | Retrograder des membres |
| `HOME` | Utiliser /f home |
| `SETHOME` | Definir le home |
| `WARP` | Utiliser les warps |
| `DEPOSIT` | Deposer dans la banque |
| `WITHDRAW` | Retirer de la banque |
| `PERMS` | Modifier les permissions |
| `DISBAND` | Dissoudre la faction |
| `RELATION` | Gerer les relations |

---

## Economie

### Banque de Faction

| Commande | Description | Status |
|----------|-------------|--------|
| `/f deposit <montant>` | Deposer dans la banque | ✅ Fonctionnel |
| `/f withdraw <montant>` | Retirer de la banque | ✅ Fonctionnel |
| `/f bank` | Voir le solde | ❌ **NON IMPLEMENTE** |

**Note :** Le solde est visible via `/f show` et les placeholders `%kfaction_faction_bank%`

### Couts (Tous configurables, 0 par defaut)

| Action | Cout Par Defaut |
|--------|-----------------|
| Creation de faction | 0 |
| Claim d'un chunk | 0 |
| Entretien auto | Desactive |

---

## Chat Faction

### ⚠️ ETAT ACTUEL : PARTIELLEMENT IMPLEMENTE

Le systeme de chat est **incomplet** :
- ✅ Changement de mode fonctionne (`/f c f`, `/f c p`, etc.)
- ❌ **ChatListener ABSENT** - Les messages ne sont PAS filtres/colores
- ❌ Le chat faction/ally affiche comme le chat public actuellement
- ❌ `/f spy` existe mais inoperant sans ChatListener

### Modes de Chat (Commandes)

| Mode | Commandes | Description |
|------|-----------|-------------|
| Public | `/f c p`, `/f c pub`, `/f c public` | Chat global normal |
| Faction | `/f c f`, `/f c fac`, `/f c faction` | Membres uniquement (a implementer) |
| Ally | `/f c a`, `/f c ally`, `/f c allie` | Faction + allies (a implementer) |
| Truce | `/f c t`, `/f c truce`, `/f c treve` | Faction + treves (a implementer) |

### Format Prevu (TODO)

| Mode | Couleur prevue |
|------|----------------|
| Faction | Vert clair (`&a`) |
| Ally | Violet (`&5`) |
| Truce | Jaune (`&e`) |

### Chat Spy (Admin) - INOPERANT

- Commande : `/f spy` (existe)
- Permission : `kfaction.admin.spy`
- **Status :** Inoperant - attend ChatListener

---

## Carte et Visualisation

### Commande Map

```
/f map      # Affiche la carte une fois
/f map on   # Toggle auto-map en marchant
```

### Symboles de la Carte

| Symbole | Signification |
|---------|---------------|
| `+` | Votre position |
| `/` | Votre faction |
| `\` | Factions alliees (vert) |
| `-` | Factions ennemies (rouge) |
| `~` | Factions en treve (jaune) |
| `.` | Wilderness |

La carte affiche egalement :
- Legende des factions presentes
- Direction cardinale (N/S/E/W)
- Coordonnees actuelles

---

## Home et Warps

### Home de Faction

- Un seul home par faction
- Delai de teleportation configurable
- Annule si mouvement/degats pendant le delai

### Warps

- Nombre de warps : configurable (progression future liee aux upgrades)
- Memes regles que le home pour la teleportation

---

## Protection des Claims

### Comportement

Quand un joueur ennemi essaie d'interagir dans un claim protege :
- **Miner/Casser :** Cancel silencieux (comme de la bedrock)
- **Poser des blocs :** Cancel silencieux
- **Ouvrir coffres :** Cancel silencieux
- **Boutons/Leviers :** Cancel silencieux
- **Portes :** Cancel silencieux

**Important :** Pas de message, pas de particules, juste l'action est annulee.
Cela evite le spam et les exploits (crash-co, lag, etc.).

### Exceptions

- Les oeufs de creeper fonctionnent dans les claims ennemis
- Les explosions suivent la config (actives/desactivees)

### Bypass Admin

- Permission : `kfaction.admin.bypass`
- Toggle : `/f admin bypass`
- Permet de contourner toutes les protections

---

## Placeholders

### PlaceholderAPI (Optionnel)

Si PlaceholderAPI est installe, les placeholders suivants sont disponibles :

| Placeholder | Description |
|-------------|-------------|
| `%kfaction_has_faction%` | true/false |
| `%kfaction_faction_name%` | Nom de la faction |
| `%kfaction_faction_tag%` | Tag de la faction |
| `%kfaction_faction_description%` | Description |
| `%kfaction_faction_leader%` | Nom du leader |
| `%kfaction_faction_online%` | Membres en ligne |
| `%kfaction_faction_members%` | Nombre de membres |
| `%kfaction_faction_maxmembers%` | Max de membres |
| `%kfaction_faction_power%` | Power de la faction |
| `%kfaction_faction_maxpower%` | Power max |
| `%kfaction_faction_claims%` | Nombre de claims |
| `%kfaction_faction_maxclaims%` | Max de claims |
| `%kfaction_faction_bank%` | Solde banque |
| `%kfaction_faction_level%` | Niveau faction (future) |
| `%kfaction_faction_allies%` | Nombre d'allies |
| `%kfaction_faction_enemies%` | Nombre d'ennemis |
| `%kfaction_faction_role%` | Role du joueur |
| `%kfaction_player_power%` | Power du joueur |
| `%kfaction_player_maxpower%` | Power max du joueur |
| `%kfaction_player_role%` | Role du joueur |
| `%kfaction_player_role_prefix%` | Prefixe du role |
| `%kfaction_location_faction%` | Faction du chunk actuel |
| `%kfaction_location_relation%` | Relation avec le chunk |

---

## Configuration

### Fichiers de Configuration

| Fichier | Description |
|---------|-------------|
| `config.yml` | Configuration principale |
| `messages.yml` | Messages personnalisables |

### Stockage des Donnees

```
plugins/Kfaction/data/
├── factions/
│   └── <faction-id>.json
└── players/
    └── <uuid>.json
```

Format : **FlatFile (JSON)**

---

## Architecture Technique

### Structure du Projet

```
me.krunsh.kfaction/
├── Kfaction.java              # Classe principale
├── api/                       # API publique
├── commands/                  # 39 commandes
├── data/                      # Modeles de donnees
│   ├── Faction.java
│   ├── FPlayer.java
│   ├── FLocation.java
│   ├── FactionRole.java
│   ├── Relation.java
│   └── PermissionAction.java
├── hooks/                     # Integrations
│   ├── HookManager.java
│   ├── VaultHook.java
│   ├── PlaceholderAPIHook.java
│   ├── KcoreHook.java
│   ├── KchatHook.java
│   ├── KguiHook.java
│   └── KclassementHook.java
├── listeners/                 # Evenements
│   ├── PlayerConnectionListener.java
│   ├── ProtectionListener.java
│   ├── TerritoryListener.java
│   └── CombatListener.java
├── managers/                  # Logique metier
│   ├── FactionManager.java
│   ├── FPlayerManager.java
│   ├── ClaimManager.java
│   ├── PowerManager.java
│   ├── RelationManager.java
│   ├── MapManager.java
│   ├── TerritoryManager.java
│   ├── PermissionManager.java
│   ├── EconomyManager.java
│   ├── ConfigManager.java
│   ├── MessageManager.java
│   └── StorageManager.java
├── placeholders/              # PlaceholderAPI expansion
├── storage/                   # Persistance
│   ├── Storage.java
│   └── FlatFileStorage.java
├── tasks/                     # Taches planifiees
│   └── PowerRegenTask.java
└── utils/                     # Utilitaires
```

### Compilation

```bash
mvn clean package
```

Le JAR final : `target/Kfaction-1.0.0.jar`

---

## Etat Actuel et TODO

### Resume de l'Etat

| Module | Status | Notes |
|--------|--------|-------|
| Factions (create/disband/join/leave) | ✅ 100% | Fonctionnel |
| Membres (invite/kick/promote/demote) | ✅ 100% | Fonctionnel |
| Territory (claim/unclaim/map) | ✅ 100% | Fonctionnel |
| Power system | ✅ 100% | Fonctionnel |
| Relations (ally/enemy/truce) | ✅ 100% | Fonctionnel |
| Home/Warps | ✅ 100% | Fonctionnel |
| Protection claims | ✅ 100% | Cancel silencieux |
| Banque factions | ✅ 100% | deposit/withdraw/bank OK |
| Chat privé (faction/ally/truce) | ✅ 100% | 4 modes: faction, ally, truce, public |
| Logs d'actions | ✅ 100% | LogManager + menus GUI (membres, territoire, eco, tp) |
| GUI Menus | ✅ 100% | Via Kgui (17 menus faction) |
| Placeholders | ✅ 100% | Via PAPI |
| TNT Bank | ✅ 100% | Configurable (désactivé par défaut) |
| Spy Chat | ✅ 100% | /f spy <faction> |
| Système d'inactivité | ✅ 100% | Transfert leadership auto |
| Admin commands | ✅ 100% | 15+ sous-commandes admin |

### TODO Haute Priorite

| Feature | Description | Effort |
|---------|-------------|--------|
| **F-Chest** | Coffre virtuel partage de faction | Moyen |
| **F-Skills** | Capacites/boosts pour la faction | Moyen |
| **F-Missions** | Quetes pour gagner XP faction | Moyen |

### TODO Futur

| Feature | Description | Notes |
|---------|-------------|-------|
| **F-Skills** | Capacites/boosts pour la faction | Design a definir |
| **F-Missions** | Quetes pour gagner XP faction | Design a definir |
| **F-Level** | Systeme de niveau de faction | Placeholder existe deja |
| **F-Prestige** | Reset niveau pour bonus permanents | Design a definir |
| **F-Upgrades** | Ameliorations progressives | Lie aux skills/missions |

### Code Existant

| Feature | Status | Notes |
|---------|--------|-------|
| **TNT Bank** | Complet, configurable | Config `tnt.bank.enabled: false` |
| **Spy Chat** | Fonctionnel | `/f spy <faction>` |
| **Inactivity** | Fonctionnel | Transfert auto du leadership |

### Abandonne

| Feature | Raison |
|---------|--------|
| **Support MySQL** | FlatFile suffit |
| **Integrations externes** | Focus sur plugins K uniquement |

---

*Documentation Kfaction v1.0.0 - Ecosysteme SparrowMC*
*Derniere mise a jour : Juillet 2025*

---

*Documentation Kfaction v1.0.0 - Ecosysteme SparrowMC*
*Derniere mise a jour : Juillet 2025*
