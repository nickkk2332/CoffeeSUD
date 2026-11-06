# Items: classes, fields, materials, catalog and unique items

This chapter is the reference and how-to for building items in CoffeeSUD: every
buildable item class and what it adds, the `CREATE ITEM` and `MODIFY ITEM`
commands, every field of the generic item editor, materials, wear locations and
layers, containers and keys, magic items, how items rejuvenate, the item
catalog (including unique and rare items), and how items are saved. It serves
world creators building in the game or in world files, and engine developers
who need to know which Java class an item type maps to. Crafting, puzzles and
interactive items are in [07-items-crafting.md](07-items-crafting.md); the
properties you attach to items are in
[23-properties-reference.md](23-properties-reference.md).

## In this chapter

- [Concepts](#concepts)
- [Creating and editing items](#creating-and-editing-items)
- [The item editor, field by field](#the-item-editor-field-by-field)
- [Item classes](#item-classes)
- [Weapons](#weapons)
- [Armor and wear locations](#armor-and-wear-locations)
- [Containers, lids, locks and keys](#containers-lids-locks-and-keys)
- [Food, drink and light](#food-drink-and-light)
- [Readable items: books, journals, maps, scrolls](#readable-items-books-journals-maps-scrolls)
- [Magic items: wands, staffs, scrolls, potions, pills](#magic-items-wands-staffs-scrolls-potions-pills)
- [Money](#money)
- [Furniture, rideables and boardables](#furniture-rideables-and-boardables)
- [Technology and clan items](#technology-and-clan-items)
- [Materials](#materials)
- [Enchantments and item properties](#enchantments-and-item-properties)
- [Item rejuvenation](#item-rejuvenation)
- [The catalog](#the-catalog)
- [Unique and rare items](#unique-and-rare-items)
- [How items are saved](#how-items-are-saved)
- [Worked examples](#worked-examples)
- [Troubleshooting](#troubleshooting)
- [See also](#see-also)

## Concepts

**An item** is anything in the world that is not a creature, room, exit or area:
a sword, a coin, a chest, a chair, a ship. Items sit in a room, in a container,
in a creature's inventory (worn, wielded, held or carried), in a shopkeeper's
stock, in the catalog, or (for planets, ships and the like) in space.

**Class ID.** Every item is an instance of a Java class (or a JS/world-owned
class, see [32-engine-programming.md](32-engine-programming.md)). The class
name is the item's ID: `GenWeapon`, `Longsword`, `StdKey`. `CREATE ITEM` takes a
class ID. Source: [Items/](../../CoffeeMud/com/planet_ink/coffee_mud/Items/),
with the interfaces in
[Items/interfaces/](../../CoffeeMud/com/planet_ink/coffee_mud/Items/interfaces/).

**Generic and standard items.** There are two kinds of item class.

| Kind | Examples | What it stores | What you can edit |
| --- | --- | --- | --- |
| Generic (`isGeneric()`, class names starting `Gen`) | `GenItem`, `GenWeapon`, `GenArmor`, `GenContainer`, `GenWand` | Every field, as XML text (name, display text, description, stats, material, wear locations, effects, behaviors, scripts...) | Everything, in the full item editor |
| Standard (fixed Java class) | `Longsword`, `Dagger`, `Backpack`, `StdKey`, `LeatherBoots` | Only level, ability, rejuv, uses and one free-text field (`miscText`). The class supplies its name, look and stats | `LEVEL`, `ABILITY`, `HEIGHT`, `REJUV`, `USES`, `SAVEABLE`, `MISC`, and the stat codes the class exposes |

Build with generic classes when you want your own name, description, stats and
properties. A standard class is a ready-made item: `CREATE ITEM Longsword`
gives a longsword with the engine's stats, and you can only nudge it. Most
creators use generic classes for everything they write and standard classes for
filler.

**Base and current stats.** An item has base stats (`basePhyStats()`, what the
editor changes) and current stats (`phyStats()`, base plus anything that
affects it: its own magical ability, spells, wear and tear). After any change
the engine calls `recoverPhyStats()`. The editor edits base stats only.

**Item vs. mob.** Items do not have a race or class, but they have a *level*
(it is the minimum level a character needs to use the item; a lower-level
character who tries to wear or wield it is told it "looks too advanced for
you"), a *material*, a *weight* and a *base value*.

**Where an item's text comes from.** For a generic item, `text()` is an XML
fragment (`<NAME>`, `<DESC>`, `<DISP>`, `<PROP>`, ...). You never write it by
hand; the editor, `MODIFY ITEM`, the catalog and the world files produce it.
See [How items are saved](#how-items-are-saved) for the tags.

**Permissions.** `CREATE ITEM`, `MODIFY ITEM`, `DESTROY ITEM` need the `CMDITEMS`
security flag; `CATALOG` needs `CATALOG`; `COPY` needs `COPYITEMS`. See
[19-configuration-and-administration.md](19-configuration-and-administration.md).

**Builder mode vs. a normal game.** In a normal single-player game, items you
create stay in that game's saves but not in the world other games start from.
Start with `BUILDER=1` to change the world itself, and publish with `SAVE`.
See [02-world-workflow.md](02-world-workflow.md).

## Creating and editing items

### CREATE ITEM

```text
CREATE ITEM [CLASS ID or CATALOG NAME or TEMPLATE ID]
CREATE ITEM [CLASS ID]@room
CREATE ITEM [CLASS ID]@[mob name]
CREATE ITEM [CLASS ID]@[container name]
```

Source: [Commands/Create.java](../../CoffeeMud/com/planet_ink/coffee_mud/Commands/Create.java)
(`items()`). Everything after `@` says where the new item goes.

| Form | Result |
| --- | --- |
| `CREATE ITEM GenWeapon` | New item dropped in your room ("Suddenly, ... drops from the sky"). If the class is generic, the full item editor opens at once |
| `CREATE ITEM GenWeapon@room` | Same, explicitly in the room |
| `CREATE ITEM GenArmor@a guard` | New item placed in the inventory of a mob in this room |
| `CREATE ITEM GenKey@a chest` | New item placed inside a container item in this room (looked up with `findItem`; it must be a container) |
| `CREATE ITEM Longsword` | A standard item, placed without an editor |
| `CREATE ITEM the Sunblade` | A copy of the catalog item of that name (see [The catalog](#the-catalog)); no editor, the copy is tied to the catalog |
| `CREATE ITEM mysword` | A copy of one of your builder templates (see `TEMPLATE` in `AHELP TEMPLATE`) |
| `CREATE ITEM 100 gold coins` | Money: a number plus a denomination of a currency in the room's currency set (the amounts accepted are those `CMLib.english().parseNumPossibleGold` understands). For a specific currency or denomination use `GenCoins` and its editor |

Order of lookup: class ID, then a money amount, then the catalog, then your
templates. A bad name reports "There's no such thing as a '...'". Some
classes (the archon items) are `ArchonOnly`: only a sysop may create them.
Spatial items (planets, stars, ships) accept a coordinate form,
`CREATE ITEM [ID] @ [COORDS]/[DISTANCE] FROM [SPACE OBJ]`; see
[30-travel-vehicles-instances.md](30-travel-vehicles-instances.md).

Short forms also work. `ITEM` may be left out when the word is not another
kind of thing: `CREATE GenWeapon`, `CREATE Longsword`, `CREATE the Sunblade`
(a catalog item) and a builder template ID are all rerouted to `CREATE ITEM`.
A leading number repeats the command (`CREATE 3 Longsword` makes three) and a
number before a material name makes raw resources: `CREATE 20 iron` drops 20
iron resource items on the floor, rebundled into piles of up to 10.

New items that suffer wear and tear (armor) start at 100 condition
(`usesRemaining` = 100).

### MODIFY ITEM

```text
MODIFY ITEM [ITEM NAME](@room | @[mob name] | @[container name])
MODIFY ITEM [ITEM NAME] [FIELD] [VALUE]
```

Source: [Commands/Modify.java](../../CoffeeMud/com/planet_ink/coffee_mud/Commands/Modify.java)
(`items()`). The item name is one parameter: quote anything with spaces, as
`MODIFY ITEM "iron sword@the orc"`. The item is found in your inventory and the
room first, then in a named container or mob, then in space, then in the stock
of a shopkeeper standing here.

- With no field, a generic item opens the full editor. A standard item prints
  the list of fields it accepts.
- With a field and a value, the field is set and the command ends. These
  forms work for any item:

| Field | Meaning |
| --- | --- |
| `LEVEL n` | Base level (0 or more) |
| `ABILITY n` | Magical ability (the "plus") |
| `HEIGHT n` | Height / size |
| `REJUV n` | Rejuvenation; any value 0 or less means "never" (see [Item rejuvenation](#item-rejuvenation)) |
| `USES n` | Uses remaining / condition / charges (0 or more) |
| `SAVEABLE true/false` | Whether the item is saved with its room |
| `MISC text` | For a standard item, its free-text field. For a generic item this opens the full editor |
| any stat code | `CLASS`'s generic stats and the class's own stat codes (below) |

The stat-code form is the most useful for scripts, `RUNFILE` macros and bulk
fixes. Every generic item accepts the codes of the `GenItemCode` list, and each
class adds its own:

| Code | Meaning |
| --- | --- |
| `CLASS` | Class ID (read only) |
| `USES` | Uses remaining |
| `LEVEL` | Level |
| `ABILITY` | Magical ability |
| `NAME`, `DISPLAY`, `DESCRIPTION` | Name, display text, description |
| `SECRET` | Secret identity (shown by identify magic) |
| `PROPERWORN` | Where it can be worn: a number (the bitmap) or a comma-separated list of location names (`HEAD, NECK`) |
| `WORNAND` | `true` if it needs *all* its locations at once |
| `BASEGOLD` | Base value |
| `ISREADABLE`, `ISGETTABLE`, `ISDROPPABLE`, `ISREMOVABLE` | True/false flags |
| `MATERIAL` | A resource number or name (`STEEL`) |
| `AFFBEHAV` | Effects and behaviors, as XML |
| `DISPOSITION` | Disposition bitmap, or a list of names (`GLOWING INVISIBLE`) |
| `WEIGHT`, `ARMOR`, `DAMAGE`, `ATTACK` | Weight, armor value, damage, attack adjustment |
| `READABLETEXT` | The item's readable text (book text, spell list, key code, duration...) |
| `IMG` | MXP image file name |
| `TAGS` | Comma-separated tags |

Numeric values accept simple integer expressions. A class's own codes are
listed in [Stat codes by class](#stat-codes-by-class). `MODIFY ITEM x ?` (an
unknown word) prints every code that item accepts.

```text
MODIFY ITEM sword DAMAGE 9
MODIFY ITEM sword WEAPONTYPE FROSTING
MODIFY ITEM "sword@a guard" REJUV 50
MODIFY ITEM ring PROPERWORN LEFT FINGER, RIGHT FINGER
```

(`PROPERWORN` takes names matching `Wearable` location names, case-insensitive.)

`GMODIFY` applies the same fields to many objects at once (`AHELP GMODIFY`);
`RESTRING [ITEM NAME]` edits just the name, display text and description.

### Other builder commands for items

| Command | What it does for items |
| --- | --- |
| `COPY [item]` | Duplicates an item (`COPY 3 sword`, `COPY sword@an orc`). Needs `COPYITEMS` |
| `DESTROY ITEM [item]` | Removes an item (`ALL`, a leading count, `@mob` and `@room` work). Needs `CMDITEMS` |
| `STAT [item]` / `STAT ITEM [name]` | Shows an item's live stats |
| `LIST ITEMS`, `LIST ARMOR`, `LIST WEAPONS`, `LIST MAGIC`, `LIST TECH`, `LIST CLANITEMS` | Lists the item classes you can create |
| `LIST MATERIALS` | Lists the resources/materials |
| `LIST UNSAVED`, `SAVE`, `SAVE ROOM`, `SAVE CHANGED` | See [How items are saved](#how-items-are-saved) |
| `RESET ITEMSTATS ROOM/AREA/WORLD/CATALOG` | Re-normalizes damage/armor/value of every item to its level |
| `RESET REJUV ROOM/AREA ITEMS` | Brings back missing resettable items |
| `DEVIATIONS ITEMS ROOM/AREA` | Shows items that deviate from the norm for their type and level |
| `TEMPLATE` | Personal and shared templates of items (and rooms, mobs, exits) |
| `CATALOG` | Global item (and mob) templates, see [The catalog](#the-catalog) |
| `ITEMMODS`, `MODCRAFT` | Tracked item modifiers, [17-item-modifiers-crafting.md](17-item-modifiers-crafting.md) |

Full help for each is in [tool-reference.md](tool-reference.md).

### The editor conversation

The full editor lists numbered fields and then asks `Edit which?`. Enter a
number to change that field, or press Enter to finish. `coffeemud.ini`
`EDITORTYPE` chooses the style: `MENU` (default) shows the list and lets you
pick fields; `WIZARD` walks through every field in order.

Prompts follow a few conventions:

- A string field says `Enter a new value`; Enter keeps the old value; `NULL`
  blanks fields that allow an empty value; `?` shows help or a list where the
  prompt says `(?)`.
- Long descriptions open the multi-line text editor (type `+` at the
  Description prompt, or edit an already multi-line description).
- Yes/no prompts show `(Y/n)` or `(y/N)` with the default capitalized.
- If the item is tied to the catalog, changing its name asks whether to detach
  it, and any other change asks whether to Update the catalog, Detach, or
  Cancel when you finish. See [The catalog](#the-catalog).
- Changing a weapon's or armor's **Level** automatically re-balances its damage,
  attack, armor, weight and value to the engine's norms for that level
  (`balanceItemByLevel`). Set Level first, then adjust the other fields.

## The item editor, field by field

Source: [Libraries/CMGenEditor.java](../../CoffeeMud/com/planet_ink/coffee_mud/Libraries/CMGenEditor.java).
Which editor opens depends on the first match, in this order, of the item's
interfaces (`genMiscSet`):

| If the item is a... | Editor |
| --- | --- |
| room map (`RoomMap`: `GenMap`) | Map editor |
| `Armor` (all `GenArmor` classes, `GenShield`, `GenCloak`, `GenEarring`...) | Armor editor |
| `MusicalInstrument` (`GenInstrument`, `GenPiano`, `GenMagicInstrument`) | Instrument editor |
| `Food` (`GenFood`, `GenPill`, `GenFoodResource`...) | Food editor |
| item that is also a `Drink` (`GenDrink`, `GenPotion`, `GenWater`, `GenFountain`, `GenPerfume`, `GenLiquidResource`...) | Drink editor |
| `Weapon` (`GenWeapon`, `GenStaff`, `GenBow`, `GenSling`, `GenSiegeWeapon`, `GenBoffWeapon`...) | Weapon editor |
| `Container` (`GenContainer`, `GenRideable` and furniture, `GenBoardable`, `GenPortal`...) | Container editor |
| class ID `GenWallpaper` | Wallpaper editor (name, description, readable text) |
| anything else generic (`GenItem`, `GenKey`, `GenCoins`, `GenReadable`, `GenWand`, `GenScroll`, `GenLightSource`, `GenBook`...) | General item editor |
| a non-generic (standard) item | Short editor: Level, Magical Ability, Rejuv, Uses Remaining, Misc Text |

Numbers in the editor are not fixed: a field that does not apply to the class
is skipped and later numbers shift. Edit by number, or use the name forms of
`MODIFY ITEM x FIELD value` where the field has a stat code.

### Fields every generic item has

| Field (prompt text) | What it is | Stat code | Notes |
| --- | --- | --- | --- |
| Name | The item's name, which players type: "a rusty sword" | `NAME` | If the display text started with the old name, it is rewritten with the new name |
| Display | Text shown in the room listing ("a rusty sword lies here."). Enter `null` to blank it. An item with no display text is "blended": it gets no line of its own in the room listing, and the room description is expected to mention it | `DISPLAY` | A blended item can still be looked at and picked up by name |
| Description | What `LOOK AT` shows. `+` opens the multi-line editor | `DESCRIPTION` | Supports VARIES/story text, see [11-presentation-language.md](11-presentation-language.md) |
| Level | Level of the item. Wearers below this level cannot wear, wield or hold it | `LEVEL` | Default 1 for most generics, 5 for `GenWeapon`, 0 for `GenStaff`. Re-balances weapons and armor |
| Material Type | The resource the item is made of, which gives it its family (metal, cloth...), weight/hardness effects and its value basis. `?` lists names | `MATERIAL` | See [Materials](#materials) |
| Secret Identity | Text shown by identify magic. Blank to use the class default. For raw resources this slot is replaced by *Rsc Sub-Type* | `SECRET` | |
| Rejuv/Pct | Item rejuvenation. Meaning depends on where the item is | `REJUV` (via `MODIFY ITEM x REJUV`) | See [Item rejuvenation](#item-rejuvenation) |
| Magical Ability | The item's "plus". Weapons add it to damage (and 10x to attack); armor adds to the armor value by location; any non-zero value also marks the item magical. Shown as *Technical Level* on electronics | `ABILITY` | |
| Uses Remaining | Charges, ammunition, or condition. For armor and weapons that suffer wear and tear it is the *Condition* (100 = perfect) and the prompt is relabeled. For ammunition it is *Ammo Remaining* | `USES` | |
| Base Value | The item's base gold value, before shopkeeper pricing | `BASEGOLD` | Real value = base value + 10 x magical ability; armor value falls with condition |
| Weight | Weight in the engine's units. Containers weigh their contents too | `WEIGHT` | |
| Dispositions | Toggles listed below | `DISPOSITION` | |
| Behaviors | Add or remove behaviors by ID; `?` lists them | (`AFFBEHAV`) | See [24-behaviors-and-masks.md](24-behaviors-and-masks.md) |
| Effects | Add (`ID`), remove (`-ID`), edit (`=ID` or its number); `?` lists abilities, `?P` lists the properties that fit this item | (`AFFBEHAV`) | See [23-properties-reference.md](23-properties-reference.md) |
| MXP Image | Image file name for MXP-aware displays | `IMG` | |
| Tags | Free tags to add or remove (entering an existing tag removes it) | `TAGS` | Used by scripts and filters |
| Scripts | Edit MOBPROG scripts on the item. Only shown when the item already has scripts | | Add scripts with the Effects/Behaviors editors (`Scriptable`) or script commands |
| *extras* | Any `EXTVAR_[ClassID]` fields from `coffeemud.ini` | | See below |

Class-specific stat codes (`getStatCodes()`) beyond these appear as extra
numbered lines at the end of the list only when they are *extension* fields.
Extension fields come from the ini: `EXTVAR_GenWeapon=SHININESS,COLOR` adds two
free-text variables to every GenWeapon (and subclasses of the named class),
saved with the item.

#### Gettable / droppable flags

One numbered prompt shows these letters; type a letter to toggle, Enter when
done. They set sense flags on the item.

| Letter | Flag | Meaning when true |
| --- | --- | --- |
| A | Is Gettable | `GET` works. If false, the item is fixed in place |
| B | Is Droppable | It can be dropped |
| C | Is Removable | It can be taken off once worn |
| D | Non-Locatable | Locate magic cannot find it |
| E | Blend Display | Always listed in the compact item summary (by name) instead of by its display text |
| F | Semi-Hidden | Hidden in plain sight (found with a search) |
| G | Contents Access (containers) | Contents can be seen/reached from outside |
| G | Is Two-Handed (weapons) | The weapon needs both hands (`WORNAND`) |

A, B, C are stored as the "deprecated" `FLAG` bits 2, 1 and 8 in item XML
(plus 4 for readable); that is why a fresh generic item has `FLAG` 11
(gettable, droppable, removable).

#### Dispositions

Toggled by letter. These are `PhyStats` disposition bits:

| Disposition | Meaning on an item |
| --- | --- |
| invisible | Cannot be seen without see-invisible |
| hide | Hidden |
| unseen | `IS_NOT_SEEN`: not seen at all |
| magical | `IS_BONUS`: shows as magical (detect magic) |
| glowing | Glows (item light via `IS_GLOWING`) |
| lightsrc | Gives light (`IS_LIGHTSOURCE`) |
| fly, climb, sleep, sit, sneak, swimmer | Gives that disposition to a mobile item, relevant for rideables and similar |
| evil, good | Alignment aura (detect evil/good) |
| unattackable | Cannot be attacked |

On the command line, `MODIFY ITEM x DISPOSITION GLOWING INVISIBLE` takes the
names `PhyStats.IS_CODES` holds, or the numeric bitmap.

### Armor editor extras

| Field | Meaning | Stat code |
| --- | --- | --- |
| Wear locations | Which body locations it fits and whether it needs *all* or *any one* of them | `PROPERWORN`, `WORNAND` |
| Layer | Layer number, plus see-through and multi-wear flags (see [Armor and wear locations](#armor-and-wear-locations)) | `LAYER`, `LAYERATTRIB` |
| Armor (higher-better) | The armor value this item adds to the wearer | `ARMOR` |
| Condition | Wear-and-tear score (100 is new), if the item suffers wear | `USES` |
| Capacity, Can contain, Lid/Lock | Every `GenArmor` class is also a container (`StdArmor` extends `StdContainer`) with capacity 0, so nothing fits until you raise it. Not shown for electronics, and `GenThinArmor` classes are not containers | `CAPACITY`, `CONTAINTYPES`... |
| Cloak Name | For `GenCloak` only (replaces the lid/lock prompts): the name wearers appear under while it is worn | `READABLETEXT` |
| Size | Shown for every `GenArmor` class, not `GenThinArmor`: the height the armor is cut for; it must fit the wearer's height to within a tolerance (see [Armor and wear locations](#armor-and-wear-locations)) | `HEIGHT` via `MODIFY ITEM x HEIGHT n` |

### Weapon editor extras

| Field | Meaning | Stat code |
| --- | --- | --- |
| Attack Adjustment | Added to the wielder's attack roll | `ATTACK` |
| Damage | Base damage | `DAMAGE` |
| Weapon Attack Type | How it hurts: see [Weapons](#weapons) | `WEAPONTYPE` |
| Weapon Classification | Weapon skill category | `WEAPONCLASS` |
| Minimum/Maximum Ranges | Distance in rooms at which it works; 0/0 is melee | `MINRANGE`, `MAXRANGE` |
| Ammunition | Whether it needs ammunition, the ammunition type, capacity | `AMMOTYPE`, `AMMOCAPACITY` |
| Condition | Wear-and-tear (skipped for ammo weapons and wands) | `USES` |
| Rideable fields | For rideable weapons (siege engines) | see [Furniture](#furniture-rideables-and-boardables) |
| Wand fields | For `GenStaff`: readable/spell text, uses, maximum uses, enchant type | `SPELL`, `MAXUSES`, `ENCHTYPE` |

### Container editor extras

| Field | Meaning | Stat code |
| --- | --- | --- |
| Capacity | The maximum weight that fits inside (weight is used as volume) | `CAPACITY` |
| Can contain | Types of item allowed; none selected means anything | `CONTAINTYPES` |
| Has a lid / lock | See [Containers, lids, locks and keys](#containers-lids-locks-and-keys) | `HASLID`, `HASLOCK`, `DEFCLOSED`, `DEFLOCKED`, `RESETTIME` |
| Key Code | Shown when it has a lid and a lock and is not readable. A key whose code fits this name unlocks it (matching is case-insensitive) | `READABLETEXT` (a generic container keeps its key name there) |

### Food editor extras

| Field | Meaning | Stat code |
| --- | --- | --- |
| Nourishment/Eat | Hunger restored by eating the whole item | `NOURISHMENT` |
| Bite/Eat (0=all) | Nourishment per bite; 0 eats it all at once | `BITE` |

### Drink editor extras

| Field | Meaning | Stat code |
| --- | --- | --- |
| Quenched/Drink | Thirst quenched per drink | `QUENCHED` |
| Amount of Drink Held | Number of drinks it holds when full (it is set full when edited) | `LIQUIDHELD` |
| Current liquid type | A liquid resource (`?` lists them) | `LIQUIDTYPE` |
| Capacity, Can contain, Lid | If the drink is also a container (wineskin, flask) | `CAPACITY`... |
| Smells list | For perfumes: smells separated by `;` | `SMELLLST` |

### Instrument editor extras

| Field | Meaning | Stat code |
| --- | --- | --- |
| Instrument Type | One of: CLARINETS, CYMBALS, DRUMS, FLUTES, GUITARS, HARMONICAS, HARPS, HORNS, OBOES, ORGANS, PIANOS, TROMBONES, TRUMPETS, TUBAS, VIOLINS, WOODS, XYLOPHONES, OTHER_INSTRUMENT_TYPE | `INSTTYPE` |

The type decides which Play skills can use the instrument (see
[25-abilities-and-effects.md](25-abilities-and-effects.md)).

### Other conditional fields in the general editor

| Applies to | Field | Meaning |
| --- | --- | --- |
| Books (`Book`) | Max Pages, Chars/Page | 0 means unlimited |
| Recipe books (`RecipesBook`) | Recipes | The skill and the recipe lines the book teaches (see `GenRecipe`) |
| Lights (`Light`) | Is destroyed after burnout | Toggle; the burn duration is the *readable text* |
| Coins | Money data | Currency, denomination, stack size |
| Wands | Maximum Uses, Enchant Type | See [Magic items](#magic-items-wands-staffs-scrolls-potions-pills) |
| Resources (`RawMaterial`) | Rsc Sub-Type | `BOLT` or `SEED`, or a world resource variant name |
| Packages | Number of items in the package | |
| Private property (titles, ships) | Owner, Price | |
| Land titles | Land plot ID | A room ID |
| Boardables | `TICKSPERMOVE`, `TICKSPERTURN`, `WEATHERSPEED`, `FIXEDINTERIOR` | See [30-travel-vehicles-instances.md](30-travel-vehicles-instances.md) |
| Electronics/tech | Manufacturer, power capacity/remaining, Activated, installed factor, recharge rate, thrust fields, ports | See [Technology and clan items](#technology-and-clan-items) |
| Clan items | Clan id and item type | |

### Stat codes by class

`MODIFY ITEM x CODE value` accepts the generic item codes above plus these,
per class (from each class's `getStatCodes()`):

| Class | Extra stat codes |
| --- | --- |
| `GenWeapon`, `GenBow`... | `MINRANGE`, `MAXRANGE`, `WEAPONTYPE`, `WEAPONCLASS`, `AMMOTYPE`, `AMMOCAPACITY` |
| `GenStaff`, `GenWand` | `ENCHTYPE`, `SPELL`, `MAXUSES` |
| `GenMagicInstrument` | `ENCHTYPE`, `SPELL`, `INSTTYPE`, `MAXUSES` |
| `GenArmor` and subclasses | `HASLOCK`, `HASLID`, `CAPACITY`, `CONTAINTYPES`, `RESETTIME`, `LAYER`, `LAYERATTRIB`, `DEFCLOSED`, `DEFLOCKED` |
| `GenContainer`, `GenBagOfHolding`, `GenBagOfEndlessness` | `CLASS`, `HASLOCK`, `HASLID`, `CAPACITY`, `CONTAINTYPES`, `RESETTIME`, `DEFCLOSED`, `DEFLOCKED` |
| `GenFood` | `NOURISHMENT`, `BITE` |
| `GenPill` | `NOURISHMENT` |
| `GenDrink`, `GenPotion` | `HASLOCK`, `HASLID`, `CAPACITY`, `CONTAINTYPES`, `RESETTIME`, `QUENCHED`, `LIQUIDHELD`, `LIQUIDTYPE`, `DEFCLOSED`, `DEFLOCKED` |
| `GenPerfume` | the drink codes plus `SMELLLST` |
| `GenLantern` | `LIQUIDHELD`, `LIQUIDTYPE` |
| `GenResource` | `DOMAINSRC`, `RSUBTYPE` |
| `GenBook`, `GenPaper` | `MAXPAGES`, `MAXCHARSPAGE` |
| `GenCoins` | `NUMCOINS`, `CURRENCY`, `DENOM` |
| `GenInstrument` | `INSTTYPE` |
| `GenRecipe` | `SKILLID`, `RECIPES`, `NUMRECIPES` |
| `GenRideable`, furniture | lock/lid/capacity codes plus `RIDEBASIS`, `MOBSHELD`, `PUTSTR`, `MOUNTSTR`, `DISMOUNTSTR`, `STATESTR`, `STATESUBJSTR`, `RIDERSTR` |
| `GenTub` | the rideable codes plus `QUENCHED`, `LIQUIDHELD`, `LIQUIDTYPE` |
| `GenPortal` | lock codes plus `RIDEBASIS`, `MOBSHELD`, `EXITNAME`, `CLOSEDTEXT`, `CLOSEWORD`, `OPENWORD` |
| `GenBoardable` | lock codes plus `RIDEBASIS`, `MOBSHELD`, `AREA`, `OWNER`, `PRICE`, `EXITNAME`, the rideable strings, `FIXEDINTERIOR` |
| `GenNavigableBoardable` and ships | the boardable codes plus `TICKSPERMOVE`, `TICKSPERTURN`, `WEATHERSPEED` |
| `GenTitle` | `OWNER`, `PRICE` |
| `GenStove` | `CONSUMEDTYPES` |

Classes not in this table (for example `GenItem`, `GenKey`, `GenReadable`,
`GenScroll`, `GenLightSource`, `GenJournal`, `GenMap`) have only the generic
codes. Their special value lives in `READABLETEXT`.

