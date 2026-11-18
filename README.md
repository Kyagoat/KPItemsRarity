# Items Rarity Mod

A **NeoForge 1.21.1** mod that gives equipment Dofus-style stats: fixed lines with rolls, rarities, tiers and sets,
built for an RPG / economy modpack. The original Forge 1.20.1 version lives on the `main` branch.

## How it works (Dofus-style)

Every piece of equipment has **fixed stat lines**, each one with a **roll** inside a range. Weapons also roll
their **base damage** around the vanilla value (an iron sword does 5 to 7 instead of 6).

```
Iron Sword
Common · Tier 2
6 Damage (5 - 7)
+4 % Strength (3 - 5)
+2 % Critical (1 - 2)
Iron Guard Set 2/5 · [Left Alt]
```

- The **tier** sets the power and never changes. The **rarity** comes from the rolls: under 30 % of the possible
  rolls Common, then Uncommon (30 %), Rare (60 %) and Epic (85 %, an almost perfect roll). Two forge steps then
  give **Legendary** and **Mythic**: every line goes to the max of its range, then the range moves up by 10 %.
  The `rarity` field of a definition is only a minimum (a boss item can be "at least Rare").
  The grey range is NOT stored on the item: rebalancing the datapack updates tooltips, and items whose lines
  changed are re-rolled automatically (keeping exos and rarity steps).
- All items are balanced with a **budget** per slot, tier and rarity (see `tools/check_balance.py` and the game design plan).
- A stat whose attribute comes from a missing mod is shown struck through.

### RPG tooltip

Equipment gets its own tooltip: big item preview, name in the rarity colour, base damage (or armor) in large
numbers, stat lines with their grey range, enchantments, set, durability. The frame takes the rarity colour and
pulses for Legendary and Mythic. It replaces Obscure Tooltips for equipment only. Can be turned off in
`config/itemsrarity-client.toml` (`rpgTooltips = false`).

### Lore (texts to edit)

Every lore text lives in the language files `src/main/resources/assets/itemsrarity/lang/fr_fr.json` / `en_us.json`:

- item: `lore.<mod>.<item>` (e.g. `lore.minecraft.iron_sword`), shown at the bottom of the tooltip;
- set: `lore.set.<mod>.<set>` (e.g. `lore.set.itemsrarity.iron_guard`), shown at the bottom of the set card.

A `"lore"` field in a `kp_items` or `kp_sets` JSON overrides it (translation key or plain text). No key = no lore.
All the logic is in `client/LoreText.java`.

### Item descriptions (every item)

Every item gets a short description in its tooltip header (`client/ItemDescriptions.java`), in this order:
1. `desc.<mod>.<item>` written by hand (e.g. `desc.minecraft.totem_of_undying`);
2. otherwise its lore (`lore.<mod>.<item>`);
3. otherwise an automatic one by category: `desc.itemsrarity.auto.<category>` (food, potion, horse_armor, ore, log, block...).

Texts can use variables read from the item and highlighted, so they follow datapack or mod changes:
`{effects}` (potion, tipped arrow, stew, food, totem, with durations), `{nutrition}`, `{saturation}`, `{armor}`,
`{durability}`, `{mob}` (spawn egg), `{song}` (music disc), `{fluid}` (bucket), `{fuel}` (furnace seconds), `{name}`.
Example: `"desc.minecraft.totem_of_undying": "Prevents its holder's death and gives {effects}."`

- An item with both a `desc` and a `lore` key shows both: the explanation, then the lore in italics.
- Automatic descriptions never repeat what the stats already show (food, armor, damage): they are short flavor lines.
- A text can hold several variants separated by `|`; each item always gets the same one (two fish do not
  necessarily share the same line).

### Sets

Hold **Left Alt** (rebindable) on a set piece to replace its tooltip with a **set card**: pieces worn, every
threshold, and the active bonus. The card stays in place while Alt is held, so you can hover the piece icons
to see their names. Thresholds are **totals** (with 4 pieces you get the "4" bonus).

| Set | Pieces | Tier | Playstyle |
|---|---|---|---|
| Prowler (`rodeur`) | leather + bow | 1 | mobile archer |
| Scout (`eclaireur`) | chainmail + crossbow | 2 | critical hits, mobility |
| Gold Panner (`orpailleur`) | gold + golden pickaxe | 2 | luck, wisdom (XP), harvesting |
| Iron Guard (`iron_guard`) | iron + shield | 2 | health, guard and stamina (KPBlockStamina) |
| Champion (`champion`) | diamond + diamond sword | 3 | strength, health, critical hits |
| Nether Lord (`nether_lord`) | netherite + netherite sword | 4 | strength, life steal, fire resistance |

### Character sheet

A small button at the top of the player frame in the inventory (E) opens the **character sheet** on the right: health, armor
and damage at the top, then every characteristic of the player from all sources, by family. Scroll with the
mouse wheel. Textures can be replaced by a resource pack:
`assets/itemsrarity/textures/gui/sprites/stats/panel.png` (nine-slice, see the `.mcmeta`),
`button_small.png` and `button_small_highlighted.png` (10x10).

### Sharing items

Press **N** (rebindable) to share an item in the chat with its full tooltip on hover: the hovered item in any
inventory (creative included), or the held item in game. Solo and servers, 3 s cooldown.

### Forgemagie

The Enhanced Anvil is now the **Forgemagic Table**: the item with stats goes at the top, a rune, a rarity stone or a
resource on the left. Rules (`forge/ForgeMagic`):

- **Runes** (one item, `itemsrarity:rune` component = stat + size): base, **Pa** (x3) and **Ra** (x10). A base rune is
  worth about 1 point of weight (`weight` in kp_stats).
- **Forge**: 90 % chance at the bottom of the roll range, 35 % at the top, 12 % above it (overmax, shown in gold, capped
  at +10 % over the max). Critical success: the line goes up. Neutral success: it goes up, other lines lose a little.
  Failure: no gain, other lines lose weight, and everything lost goes into the item's **well**. A well at least as
  heavy as the rune makes it succeed for sure (the well pays). A malus line forges the same way, up to 0.
- **Exo**: a rune of a stat the item does not have (and marked `"exo": true`) tries to add one exotic line (3 %, more
  with the well, capped at one Pa rune). Shown in pink, announced anonymously to the server.
- **Break**: an item with stats gives runes from its rolls (40 to 80 % of their weight). A resource listed in
  `data/<mod>/kp_runes/*.json` gives its runes (`{"items": [...], "runes": [{"stat", "power", "chance", "count"}]}`).
- **Rarity stones**: flourite 35 %, sulfur 60 %, bismuth 90 % to raise the item one step (Legendary, then Mythic).
- The emerald hammer (right click on the table) forges like the button.

## Datapack Configuration

Everything is data-driven, reloads with `/reload` and is synced to clients (works on dedicated servers):

```
data/<namespace>/kp_stats/<id>.json    one stat (name: translation key stat.<namespace>.<id>)
data/<namespace>/kp_items/<name>.json  stat lines of one or more items
data/<namespace>/kp_sets/<name>.json   one set (name: translation key set.<namespace>.<name>)
```

Stat:

```json
{
  "attribute": "minecraft:generic.attack_damage",
  "operation": "add_multiplied_base",
  "scale": 0.01,
  "format": "percent",
  "color": "#C8873A",
  "category": "combat",
  "order": 10,
  "rune_power": 1,
  "exo": false,
  "weight": 1.0
}
```

A roll is stored in points; the attribute gets `points x scale` (11 points of Strength = +0.11, i.e. +11 %).
`format`: `int`, `percent` or `tenths`. `category`: `combat`, `defense`, `mobility`, `harvest`, `magic`, `utility`
(groups in the character sheet). `rune_power` and `exo` are used by the forgemagie, `weight` by the balance check.
A stat with `"base": true` (Damage) replaces the item's vanilla value instead of adding to it (`offset` is added).

Item:

```json
{
  "items": ["minecraft:iron_sword"],
  "tier": 2,
  "rarity": "common",
  "set": "itemsrarity:iron_guard",
  "stats": { "strength": [6, 10], "critical": [1, 2] }
}
```

Set:

```json
{
  "bonuses": {
    "2": { "vitality": 2 },
    "5": { "vitality": 6, "guard": 10 }
  }
}
```

The set's pieces are all the items whose definition points to it. Other mods can add their own stats the same
way (KPBlockStamina adds `guard`, `parry`, `endurance` and `stamina_regen`).

## Commands (operator level 2)

All commands act on the held item.

- `/rarity roll` - new rolls within the ranges
- `/rarity max` - perfect rolls
- `/rarity upgrade` - raise the rarity by one step (max 2)
- `/rarity set <stat> <value>` - force a line (even above the max, to test overmax)
- `/rarity exo <stat> <value>` - add an exo line (`0` removes it)
- `/rarity remove` - remove rolls and rarity (they come back at the next scan)
- `/rarity info` - rolls, ranges and roll quality

## Balance check

`python tools/check_balance.py src/main/resources ../KPBlockStamina/src/main/resources` lists every item and set
with its weighted cost against its budget, and fails if one is outside 75 % - 115 %.

## Mod Config (`config/itemsrarity-common.toml`)

- `disableVanillaCrits` (default `true`): vanilla jump crits are disabled; crits come from the `crit_chance` attribute.
- `inventoryScanInterval` (default `20` ticks): how often inventories are checked for gear without rolls.

## Dependencies

- Required: NeoForge 21.1+
- No dependency: critical chance (5 % base), critical damage (150 %), life steal, dodge, armor pierce, arrow damage, draw speed and wisdom are the mod's own attributes (`itemsrarity:*`). Apothic Attributes is no longer needed.
- Optional: **KPBlockStamina** (guard, parry, endurance stats), **Obscure Tooltips** (animated tooltips per rarity)

## Building from Source

1. Clone this repository
2. Run `./gradlew build` (`./gradlew runClient` starts a dev client)
3. Find the JAR in `build/libs/`

## Credits

Ore textures are taken from [malcolmriley/unused-textures](https://github.com/malcolmriley/unused-textures),
used under the terms of that repository's license.

## License

All Rights Reserved

## Rareté des objets sans jets (`kp_rarity`)

Les matériaux et objets des autres mods peuvent recevoir une rareté (couleur du nom et cadre de l'infobulle)
via des fichiers côté client `assets/<mod>/kp_rarity/*.json` :

```json
{ "rarity": "epic", "items": ["minecraft:elytra", "#tide:legendary_fish"] }
```

Raretés : `common`, `uncommon`, `rare`, `epic`, `legendary`, `mythic`. Un resource pack peut ajouter ou
remplacer ces fichiers. Les objets d'un mod absent sont ignorés. Les objets à jets (kp_items) gardent leur
rareté calculée.

## Accessoires Curios

Une définition `kp_items` avec `"slot": "amulet"`, `"ring"` ou `"relic"` est un accessoire : ses jets ne donnent
rien en main ni en armure, seulement portés dans une case Curios (`compat/CuriosCompat`). Curios n'est pas une
dépendance : sans lui, les accessoires n'ont simplement pas d'effet.

## Lore des autres mods

Le lore (`lore.<mod>.<objet>`) des objets de Create, Farmer's Delight, Mowzie's Mobs et quelques autres est dans
les fichiers de langue d'Items Rarity. Pour un objet qui n'a que du lore, la ligne utile automatique (effets de
nourriture, durée de combustion, disque...) est quand même affichée au-dessus.
