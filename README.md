# DiscSwordPlugin

**DiscSwordPlugin** turns every Minecraft music disc into a **custom weapon with unique abilities**, complete with crafting recipes, model data support, a GUI selector, and a fully-featured **Guardian Wolf companion system**.
Designed for SMPs, RPG servers, PvP, and custom content-creator worlds.

---

## Features

* Custom **Disc Swords** for all vanilla music discs
* Right-click **abilities** (movement, combat, utility, effects)
* **Custom model data** per disc sword (config-driven)
* **Survival-friendly crafting recipes**
* GUI **Disc Sword Selector** menu
* **Guardian Wolf** system: summon, modes, stats, recall, auto-teleport
* Automatic **config patching** to prevent missing sections
* Player data saved in `players.yml`
* Admin commands, debug info, and cooldown bypass permissions

---

## Abilities

Each disc sword has its own power, such as:

* Lightning Dash
* Wind Dash
* Barrier Shield
* Freeze Pulse
* Void Rift Implosion
* Nether Flames
* Damage Immunity
* Sonic Boom
* Lifesteal
* Teleport to mobs
* Launch enemies
* Invert breathing
* Water Empowered Strike
* Guardian Wolf summon
* Many more depending on disc type

All abilities are implemented internally and work out of the box.

---

## Crafting

Every Disc Sword can be crafted in survival using:

```
 D 
NJN
 S 
```

**D** = music disc
**J** = jukebox
**N** = netherite ingot
**S** = diamond sword

No mods or external items required.

---

## Commands

### Player

* `/discsword wolf` – Wolf menu
* `/discsword wolf mode <agg|def>` – Change wolf behavior
* `/discsword wolf recall` – Teleport wolf
* `/discsword autotpswitch` – Toggle wolf auto-teleport

### Admin

* `/discsword help`
* `/discsword list`
* `/discsword give <player> <disc>`
* `/discsword getall`
* `/discsword debug [player]`
* `/discsword reload`
* `/discsword menu` – Open selector GUI

---

## Permissions

```
discsword.player
discsword.admin
discsword.cooldown.bypass
```

Admins automatically bypass ability cooldowns.

---

## Configuration

* All disc swords load **custom model data** from `config.yml`
* Plugin patches missing sections automatically
* Player/wolf data stored in `players.yml`

---

## Ideal For

* SMP progression
* RPG servers
* PvP ability combat
* Creator SMPs
* Servers with custom resource packs
