# Kfaction — Système de Niveaux, Quêtes & Récompenses

> **ARCHIVE DU MODÈLE LEGACY.** Ce document décrit l'ancien système
> `quests.yml` + `levels.yml` fondé sur trois quêtes aléatoires, l'XP, les
> rerolls et l'enum `QuestCategory`. Il ne décrit pas la progression v2.
>
> La source de vérité v2 est `progression.yml` : quêtes fixes simultanément
> obligatoires, tranches de membres configurables, progression brute conservée,
> level-up transactionnel et catégories YAML libres. Le fichier
> `src/main/resources/progression.example.yml` documente le schéma, mais ses
> valeurs sont uniquement techniques et ne constituent pas l'équilibrage
> Volkaria. Tant qu'un `progression.yml` réel n'est pas activé, la progression
> de faction reste désactivée et l'ancien modèle ne reprend pas.

> Guide complet pour configurer les niveaux de faction, créer des quêtes, gérer les catégories et définir les récompenses.

---

## Table des Matières

1. [Vue d'ensemble](#1-vue-densemble)
2. [Architecture du système](#2-architecture-du-système)
3. [Configuration des quêtes — quests.yml](#3-configuration-des-quêtes--questsyml)
4. [Configuration des niveaux — levels.yml](#4-configuration-des-niveaux--levelsyml)
5. [Créer une nouvelle quête](#5-créer-une-nouvelle-quête)
6. [Créer une nouvelle catégorie](#6-créer-une-nouvelle-catégorie)
7. [Ajouter un nouveau niveau](#7-ajouter-un-nouveau-niveau)
8. [Types de récompenses](#8-types-de-récompenses)
9. [Commandes du système](#9-commandes-du-système)
10. [Placeholders PlaceholderAPI](#10-placeholders-placeholderapi)
11. [Intégration GUI (Kgui)](#11-intégration-gui-kgui)
12. [Fonctionnement interne](#12-fonctionnement-interne)
13. [FAQ](#13-faq)

---

## 1. Vue d'ensemble

Le système de niveaux de faction ajoute une progression à long terme pour les factions :

- **Quêtes** : Missions actives que les membres accomplissent (miner, farmer, tuer)
- **XP** : Chaque quête complétée donne de l'XP à la faction
- **Niveaux** : L'XP accumulée fait monter le niveau de la faction (1-10)
- **Récompenses** : Chaque niveau débloque des avantages (F-Chest, F-Fly, Anti-Sethome, etc.)
- **Catégories** : 3 types de quêtes au choix (mineur, farmer, chasseur)

### Flux de jeu

```
Joueur accomplit une action (mine, farm, kill)
    ↓
QuestListener détecte l'action et avance la progression
    ↓
Quête complétée → XP ajoutée à la faction
    ↓
XP suffisante → Level up automatique
    ↓
Récompenses appliquées (coffre, fly, power, etc.)
    ↓
3 nouvelles quêtes tirées aléatoirement
```

---

## 2. Architecture du système

### Fichiers de configuration

| Fichier | Rôle |
|---------|------|
| `quests.yml` | Pool de quêtes par catégorie |
| `levels.yml` | Seuils XP et récompenses par niveau |
| `messages.yml` | Messages du système (sections `level`, `quest`, `chest`, `fly`) |
| `config.yml` | Activation/désactivation globale |

### Classes Java

| Classe | Rôle |
|--------|------|
| `LevelManager` | Gestion XP, level up, lecture config |
| `QuestManager` | Pool de quêtes, sélection, progression, reroll |
| `RewardManager` | Application des récompenses automatiques |
| `FactionChestManager` | Coffre virtuel partagé |
| `QuestListener` | Écoute des events Bukkit (BlockBreak, EntityDeath, FurnaceExtract) |
| `FlyListener` | Gestion du fly en territoire |
| `AntiSethomeListener` | Blocage /sethome ennemi |

### Données persistées (dans Faction.java → FlatFileStorage)

```json
{
  "factionLevel": 3,
  "factionXp": 2500,
  "questCategory": "MINEUR",
  "activeQuests": [...],
  "hasChest": true,
  "hasFly": false,
  "hasAntiSethome": true,
  "chestSize": 54,
  "chestContents": {...},
  "flyEnabled": false,
  "bonusWarps": 2,
  "bonusMembers": 0,
  "bonusPower": 0.0
}
```

---

## 3. Configuration des quêtes — quests.yml

### Structure générale

```yaml
# Nombre de quêtes actives simultanément par faction
active-quests: 3

# Catégories (chaque catégorie est une section racine)
mineur:
  quests:
    <quest_id>:
      type: <type>
      target: <material_or_entity>
      sparrowmc-item: <cit_exact_optionnel>
      display: "<nom affiché>"
      amounts: [<quantité1>, <quantité2>, <quantité3>]
      xp: [<xp1>, <xp2>, <xp3>]
```

### Propriétés d'une quête

| Propriété | Type | Description |
|-----------|------|-------------|
| `type` | String | Type d'action : `block_break`, `entity_kill`, `item_smelt`, `item_sell` |
| `target` | String | Matériau Bukkit 1.8.8 (ex: `DIAMOND_ORE`) ou EntityType (ex: `ZOMBIE`) |
| `sparrowmc-item` | String | _(Optionnel, `item_sell`)_ CIT exact à vendre en plus du matériau. Le tag `level` est ignoré |
| `display` | String | Nom affiché au joueur dans les messages et GUIs |
| `amounts` | List<int> | Quantités possibles (choix aléatoire lors du tirage) |
| `xp` | List<int> | XP correspondante à chaque quantité |
| `wither-skeleton` | boolean | _(Optionnel)_ `true` si la cible est un Wither Skeleton (même EntityType que SKELETON en 1.8) |

### Types de quêtes supportés

| Type | Event Bukkit | Target | Exemple |
|------|-------------|--------|---------|
| `block_break` | `BlockBreakEvent` | Material Bukkit | `DIAMOND_ORE`, `CROPS`, `STONE` |
| `entity_kill` | `EntityDeathEvent` | EntityType Bukkit | `ZOMBIE`, `SKELETON`, `PLAYER` |
| `item_smelt` | `FurnaceExtractEvent` | Material résultant | `IRON_INGOT`, `GOLD_INGOT` |
| `item_sell` | `ShopPostTransactionEvent` | Material, avec CIT exact optionnel | `WHEAT`, `DIAMOND_CHESTPLATE` + `azurite_chestplate` |

`item_sell` requiert ShopGUIPlus. Seules les transactions finales `SUCCESS`
de type `SELL` ou `SELL_ALL` progressent, avec la quantité réellement vendue.
Une cible sans `sparrowmc-item` accepte tous les articles vendus de ce
matériau. Avec ce champ, le matériau et le tag exact doivent correspondre :

```yaml
sell_azurite_chestplate:
  type: item_sell
  target: DIAMOND_CHESTPLATE
  sparrowmc-item: azurite_chestplate
  display: "Vendre des plastrons en Azurite"
  amounts: [1, 2, 4]
  xp: [20, 40, 80]
```

Le NBT `level` ne participe volontairement pas à l'identité, conformément à
la règle ShopGUIPlus-NBT de Volkaria.

### Relation amounts ↔ xp

Les listes `amounts` et `xp` sont **liées par index**. Quand une quête est tirée :
1. Un index aléatoire `i` est choisi
2. La quantité requise = `amounts[i]`
3. L'XP gagnée = `xp[i]`

```yaml
mine_diamond_ore:
  amounts: [50, 100, 200]   # Index 0, 1, 2
  xp:     [30,  60, 120]    # Index 0, 1, 2
  # Si index 1 est tiré : "Miner 100 diamants → 60 XP"
```

### Quêtes par catégorie existantes

| Catégorie | Quêtes | Types |
|-----------|--------|-------|
| `mineur` | 12 quêtes | 10 block_break + 2 item_smelt |
| `farmer` | 9 quêtes | 9 block_break (récoltes) |
| `chasseur` | 11 quêtes | 11 entity_kill (mobs + joueurs) |

---

## 4. Configuration des niveaux — levels.yml

### Structure générale

```yaml
settings:
  enabled: true
  max-display-level: 10
  broadcast-levelup: true
  progressbar-length: 20
  progressbar-filled: "▌"
  progressbar-empty: "▌"
  progressbar-color-filled: "&a"
  progressbar-color-empty: "&7"

levels:
  <n>:
    xp-required: <xp_totale>
    rewards:
      - type: <reward_type>
        value: <valeur>
        description: "<texte>"
```

### Paramètres globaux (settings)

| Paramètre | Type | Description |
|-----------|------|-------------|
| `enabled` | boolean | Activer/désactiver tout le système |
| `max-display-level` | int | Niveau max affiché (10 par défaut) |
| `broadcast-levelup` | boolean | Annoncer les level up au serveur |
| `progressbar-length` | int | Nombre de caractères dans la barre |
| `progressbar-filled` | char | Caractère pour la partie remplie |
| `progressbar-empty` | char | Caractère pour la partie vide |
| `progressbar-color-filled` | String | Couleur de la partie remplie |
| `progressbar-color-empty` | String | Couleur de la partie vide |

### Table des niveaux par défaut

| Niveau | XP Requise | XP Cumulée | Récompenses |
|--------|-----------|------------|-------------|
| 1 | 500 | 500 | F-Chest (36 slots) + 1 Warp |
| 2 | 1 500 | 1 500 | Anti-Sethome |
| 3 | 3 000 | 3 000 | F-Chest → 54 slots |
| 4 | 5 000 | 5 000 | +1 Warp + F-Fly |
| 5 | 8 000 | 8 000 | +2 Membres + 100 PP |
| 6 | 12 000 | 12 000 | 100 PP + 5 Power Boost |
| 7 | 17 000 | 17 000 | 100 PP + 1 Membre |
| 8 | 23 000 | 23 000 | 150 PP + 1 Warp |
| 9 | 30 000 | 30 000 | 150 PP |
| 10 | 40 000 | 40 000 | 200 PP + 10 Power + 2 Membres |

> **Note :** `xp-required` est l'XP **totale** nécessaire (pas incrémentale).

---

## 5. Créer une nouvelle quête

### Étape 1 : Choisir la catégorie

Ajoutez la quête dans la section de la catégorie appropriée dans `quests.yml` :
- `mineur` → minage, fonderie
- `farmer` → récoltes, agriculture
- `chasseur` → combat, mobs

### Étape 2 : Définir la quête

```yaml
mineur:
  quests:
    # ... quêtes existantes ...
    
    # NOUVELLE QUÊTE :
    mine_glowstone:
      type: block_break
      target: GLOWSTONE         # Material Bukkit 1.8.8
      display: "Miner de la Glowstone"
      amounts: [64, 128, 256]   # 3 paliers de difficulté
      xp: [25, 50, 100]         # XP correspondante
```

### Étape 3 : Vérifier le target

Pour `block_break` et `item_smelt`, utilisez les noms de **Material** Bukkit 1.8.8 :
```
DIAMOND_ORE, GOLD_ORE, IRON_ORE, COAL_ORE, EMERALD_ORE, LAPIS_ORE,
REDSTONE_ORE, QUARTZ_ORE, STONE, OBSIDIAN, GLOWSTONE, NETHERRACK,
CROPS, CARROT, POTATO, SUGAR_CANE_BLOCK, MELON_BLOCK, PUMPKIN, 
CACTUS, NETHER_WARTS, COCOA, ...
```

Pour `entity_kill`, utilisez les noms d'**EntityType** Bukkit 1.8.8 :
```
ZOMBIE, SKELETON, SPIDER, CREEPER, ENDERMAN, BLAZE, PIG_ZOMBIE,
WITCH, GUARDIAN, GHAST, CAVE_SPIDER, SILVERFISH, SLIME, PLAYER, ...
```

### Étape 4 : Recharger

Après modification, exécutez `/kf reload` pour recharger la configuration.

> **Important :** Les quêtes déjà actives ne seront pas modifiées. Le changement s'appliquera au prochain reroll de quêtes.

---

## 6. Créer une nouvelle catégorie

### Étape 1 : Ajouter la catégorie dans quests.yml

```yaml
# Nouvelle catégorie : PÊCHEUR
pecheur:
  quests:
    fish_raw:
      type: entity_kill     # Les poissons sont des "kills" en 1.8
      target: SQUID
      display: "Pêcher des Calmars"
      amounts: [25, 50, 100]
      xp: [30, 60, 120]
```

### Étape 2 : Ajouter l'enum dans QuestCategory.java

```java
// me/krunsh/kfaction/levels/QuestCategory.java
public enum QuestCategory {
    MINEUR("⛏", "Mineur"),
    FARMER("🌾", "Farmer"),
    CHASSEUR("⚔", "Chasseur"),
    PECHEUR("🎣", "Pêcheur");  // NOUVELLE CATÉGORIE

    // ... constructeur et méthodes existantes
}
```

### Étape 3 : Mettre à jour QuestListener.java

Si la nouvelle catégorie utilise un **type de quête existant** (ex: `entity_kill`), aucun code supplémentaire n'est nécessaire — le listener gère déjà tous les types.

Si vous ajoutez un **nouveau type de quête** (ex: `item_fish`), ajoutez un nouvel EventHandler dans `QuestListener.java` :

```java
@EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
public void onPlayerFish(PlayerFishEvent event) {
    if (event.getState() != PlayerFishEvent.State.CAUGHT_FISH) return;
    Player player = event.getPlayer();
    // ... même logique que les autres handlers
    questManager.advanceQuest(factionId, "item_fish", "RAW_FISH", 1);
}
```

### Étape 4 : Mettre à jour le menu catégorie dans Kgui

Ajoutez un slot dans `Kgui/src/main/resources/menus/faction_quest_category.yml` :

```yaml
  pecheur:
    slots: [31]
    material: FISHING_ROD
    name: "&b🎣 Pêcheur"
    lore:
      - "&7Pêche et exploration aquatique"
      - ""
      - "&eCliquez pour choisir cette catégorie!"
    click_actions:
      - "[player] f quest pecheur"
      - "[close]"
    click_sound: NOTE_PLING:1:1.5
```

### Étape 5 : Mettre à jour KguiContentProviders.java

Ajoutez le matériau pour la nouvelle catégorie dans le provider `kfaction_quests` :

```java
if (category.name().equals("MINEUR")) material = "DIAMOND_PICKAXE";
else if (category.name().equals("FARMER")) material = "DIAMOND_HOE";
else if (category.name().equals("PECHEUR")) material = "FISHING_ROD";  // NOUVEAU
else material = "DIAMOND_SWORD";
```

---

## 7. Ajouter un nouveau niveau

### Dans levels.yml

```yaml
levels:
  # ... niveaux 1-10 existants ...
  
  11:
    xp-required: 55000    # XP totale pour atteindre le niveau 11
    rewards:
      - type: playerpoints_leader
        value: 300
        description: "&a+300 PlayerPoints pour le chef!"
      - type: faction_power_increase
        value: 15.0
        description: "&a+15 Power Boost"
```

### Mettre à jour settings

```yaml
settings:
  max-display-level: 11  # Augmenter le max si nécessaire
```

> **Note :** Le `max-display-level` contrôle l'affichage. Au-delà de ce niveau, seuls les `playerpoints_leader` sont donnés.

---

## 8. Types de récompenses

| Type | Valeur | Description | Appliqué par |
|------|--------|-------------|-------------|
| `faction_chest_unlock` | int (taille) | Débloque le F-Chest avec N slots | `FactionChestManager` |
| `faction_chest_resize` | int (taille) | Agrandit le F-Chest à N slots | `FactionChestManager` |
| `faction_fly` | boolean | Débloque `/f fly` dans le territoire | `FlyListener` |
| `anti_sethome` | boolean | Active le blocage /sethome ennemi | `AntiSethomeListener` |
| `warps_increase` | int | +N warps maximum pour la faction | `Faction.bonusWarps` |
| `members_limit_increase` | int | +N places de membres | `Faction.bonusMembers` |
| `faction_power_increase` | double | +N power bonus | `Faction.bonusPower` |
| `playerpoints_leader` | int | Donne N PlayerPoints au leader | Via API PlayerPoints (reflection) |

### Ajouter un nouveau type de récompense

1. Ajoutez le type dans `RewardType.java` :
```java
public enum RewardType {
    // ... existants ...
    CUSTOM_REWARD;
}
```

2. Gérez-le dans `RewardManager.java` :
```java
case CUSTOM_REWARD:
    int value = reward.getInt("value", 0);
    // Appliquer la récompense...
    break;
```

3. Utilisez-le dans `levels.yml` :
```yaml
- type: custom_reward
  value: 42
  description: "&aMa récompense custom!"
```

---

## 9. Commandes du système

| Commande | Permission | Rôle requis | Description |
|----------|-----------|-------------|-------------|
| `/f level` | `kfaction.player.level` | Membre | Voir le niveau et l'XP |
| `/f quest [catégorie]` | `kfaction.player.quest` | Moderator+ | Voir/changer les quêtes |
| `/f chest` | `kfaction.player.chest` | Membre | Ouvrir le coffre faction |
| `/f fly` | `kfaction.player.fly` | Membre | Activer/désactiver le vol |

### Détails des commandes

**`/f level`** — Affiche le niveau, XP, quêtes actives et progression

**`/f quest`** (sans argument) — Affiche la catégorie et les quêtes actives avec progression

**`/f quest <catégorie>`** — Change la catégorie et tire 3 nouvelles quêtes
- Catégories : `mineur`, `farmer`, `chasseur`
- Rerolls toutes les quêtes actives
- Seuls Moderator+ peuvent changer la catégorie

**`/f chest`** — Ouvre le coffre virtuel
- Requiert que le F-Chest soit débloqué (niveau 1+)
- Coffre partagé entre tous les membres
- Sauvegardé à chaque fermeture

**`/f fly`** — Toggle le vol dans le territoire
- Requiert que le F-Fly soit débloqué (niveau 4)
- Se désactive automatiquement en combat ou hors territoire
- Protection anti-chute pendant 3 secondes

---

## 10. Placeholders PlaceholderAPI

Format : `%kfaction_<nom>%`

### Placeholders niveau/quêtes

| Placeholder | Description | Exemple |
|-------------|-------------|---------|
| `faction_level` | Niveau faction | `5` |
| `faction_xp` | XP actuelle | `6200` |
| `faction_required_xp` | XP pour prochain niveau | `8000` |
| `faction_progressbar` | Barre de progression | `§a▌▌▌▌▌▌▌▌▌▌▌▌▌▌▌§7▌▌▌▌▌` |
| `faction_category` | Catégorie active | `Mineur` |
| `faction_quests_remaining` | Quêtes non complétées | `2` |

### Placeholders statut (pour GUIs)

| Placeholder | Résultat |
|-------------|----------|
| `faction_has_chest` | `true` / `false` |
| `faction_has_fly` | `true` / `false` |
| `faction_has_antisethome` | `true` / `false` |
| `faction_has_chest_display` | `§a✔ Débloqué` / `§c✖ Verrouillé` |
| `faction_has_fly_display` | `§a✔ Débloqué` / `§c✖ Verrouillé` |
| `faction_has_antisethome_display` | `§a✔ Actif` / `§c✖ Verrouillé` |
| `faction_role` | `Leader`, `CoLeader`, `Moderator`, etc. |

### Test en jeu

```
/papi parse me %kfaction_faction_level%
/papi parse me %kfaction_faction_progressbar%
/papi parse me %kfaction_faction_has_fly_display%
```

---

## 11. Intégration GUI (Kgui)

Le système s'intègre avec Kgui via 4 menus et 2 content providers.

### Menus créés

| Fichier | Accès | Description |
|---------|-------|-------------|
| `faction_level.yml` | Tous | Vue d'ensemble du niveau |
| `faction_quests.yml` | Tous | Quêtes actives (paginé) |
| `faction_quest_category.yml` | Moderator+ | Choix de catégorie |
| `faction_rewards.yml` | Tous | Arbre de récompenses (paginé) |

### Content Providers

| Provider ID | Description |
|------------|-------------|
| `kfaction_quests` | Items dynamiques des quêtes actives |
| `kfaction_rewards` | Items dynamiques des niveaux/récompenses |

Ces providers sont enregistrés automatiquement au démarrage si Kgui est présent.
Voir `LEVEL_SYSTEM_GUI.md` dans Kgui pour les détails complets.

### Accès via menu

```
/f upgrade → faction_upgrades.yml
  → Slot 12 : Quêtes → faction_quests.yml
  → Slot 13 : Catégories → faction_quest_category.yml  
  → Slot 21 : Récompenses → faction_rewards.yml
  → Slot 22 : Niveau → faction_level.yml
```

---

## 12. Fonctionnement interne

### Cycle de vie d'une quête

```
1. Faction choisit catégorie (ou garde la par défaut)
     ↓
2. QuestManager tire 3 quêtes aléatoires du pool
     ↓
3. FactionQuest créé avec (questId, target, amount, xpReward, progress=0)
     ↓
4. QuestListener écoute les events Bukkit
     ↓
5. À chaque action valide : progress++
     ↓
6. progress >= amount → quête complétée
     ↓
7. XP ajoutée → LevelManager.addXp(faction, xp)
     ↓
8. Si XP >= seuil → LevelManager.levelUp(faction)
     ↓
9. RewardManager.applyRewards(faction, newLevel)
     ↓
10. Quête retirée, nouvelle quête tirée
```

### Auto-reroll

Quand une faction change de catégorie (`/f quest <cat>`) :
1. Toutes les quêtes actives sont supprimées (y compris la progression)
2. 3 nouvelles quêtes sont tirées de la nouvelle catégorie
3. Un message confirme le changement

### Sauvegarde des données

Les données du système de niveaux sont persistées dans les fichiers JSON de chaque faction via `FlatFileStorage` :
- Sauvegarde automatique toutes les 5 minutes
- Sauvegarde à chaque fermeture du coffre (`save-on-close: true`)
- Sauvegarde synchrone au `onDisable()`

---

## 13. FAQ

### Q: Combien de quêtes peut-on avoir simultanément ?
**R:** 3 par défaut. Configurable via `active-quests` dans `quests.yml`.

### Q: Que se passe-t-il quand toutes les quêtes sont complétées ?
**R:** De nouvelles quêtes sont tirées automatiquement du pool de la catégorie active.

### Q: Un joueur peut-il contribuer aux quêtes d'une autre faction ?
**R:** Non. Le listener vérifie que le joueur est dans une faction et avance uniquement les quêtes de SA faction.

### Q: Le coffre est-il conservé si la faction est dissoute ?
**R:** Non. Le contenu du coffre est perdu lors du disband. Prévoir une sauvegarde si nécessaire.

### Q: Le fly est-il désactivé en PvP ?
**R:** Oui. `FlyListener` détecte les combats et désactive le fly automatiquement. Protection anti-chute pendant 3 secondes (60 ticks configurable).

### Q: Peut-on avoir plus de 10 niveaux ?
**R:** Oui. Ajoutez simplement des entrées dans `levels.yml` et augmentez `max-display-level` dans settings.

### Q: Comment reset le niveau d'une faction ?
**R:** Via le code admin (pas de commande actuellement). Le système de prestige (`F-Prestige`) est prévu pour ça dans le futur.

### Q: Les quêtes "tuer des joueurs" comptent-elles le même joueur plusieurs fois ?
**R:** Oui. Il n'y a pas de restriction anti-farming actuellement. Un cooldown par cible peut être ajouté dans `QuestListener`.

---

*Dernière mise à jour: Session 4 — Implémentation complète du système de niveaux*
