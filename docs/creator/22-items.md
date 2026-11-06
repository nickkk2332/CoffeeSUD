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
- [Instruments](#instruments)
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
security flag; `CATALOG` needs `CATALOG`; `COPY` needs `COPYITEMS`; `RESET` needs `RESET`. See
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
| `CREATE ITEM 100 gold coins` | Money: a number plus the name of a denomination (here the default currency's gold coin). For a specific currency or denomination use `GenCoins` and its editor |

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
MODIFY ITEM [ITEM NAME]
MODIFY ITEM [ITEM NAME]@room
MODIFY ITEM "[ITEM NAME]@[mob name or container name]"
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
| `DISPOSITION` | Disposition bitmap, or a list of names (`ISGLOWING ISINVISIBLE`; spaces or commas) |
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
| `RESET ITEMSTATS ROOM/AREA/WORLD/CATALOG` | Re-normalizes damage/armor/value of every item to its level (sysop only) |
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
| a non-generic (standard) item | Not part of this list. `MODIFY ITEM name` does not open an editor for it; `MODIFY name` (without the word `ITEM`) opens a short editor: Level, Magical Ability, Rejuv, Uses Remaining, Misc Text |

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
| Effects | Add (`ID`, then it asks `Enter any effect parameters (?)`; `?` there shows the property's help), remove (`-ID`), edit (`=ID` or its number); `?` lists abilities, `?P` lists the properties that fit this item. A second copy of an effect the item already has would be ignored, so you are offered `Prop_MultiEffects` to hold both | (`AFFBEHAV`) | See [23-properties-reference.md](23-properties-reference.md) |
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

On the command line, `MODIFY ITEM x DISPOSITION ISGLOWING ISINVISIBLE` takes
the names in `PhyStats.IS_CODES` (`ISUNSEEN`, `ISHIDDEN`, `ISINVISIBLE`,
`ISEVIL`, `ISGOOD`, `ISSNEAKING`, `ISBONUS`, `ISDARK`, `ISGOLEM`,
`ISSLEEPING`, `ISSITTING`, `ISFLYING`, `ISSWIMMING`, `ISGLOWING`,
`ISCLIMBING`, `ISFALLING`, `ISLIGHT`, `ISBOUND`, `ISCLOAKED`, `ISUNSAVABLE`,
`ISCATALOGED`, `ISUNATTACKABLE`, `ISCUSTOM`, `ISUNHELPFUL`), or the numeric
bitmap. Note that the command replaces the whole set.

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

## Item classes

`LIST ITEMS`, `LIST ARMOR`, `LIST WEAPONS`, `LIST MAGIC`, `LIST TECH` and `LIST CLANITEMS`
print what the running engine can create. This section describes the
generic (`Gen*`) classes in
[Items/](../../CoffeeMud/com/planet_ink/coffee_mud/Items/), what each one
adds over its parent, and the ready-made standard items. When a class's
default is mentioned, it is what the constructor sets before you edit it.

The inheritance matters because the editor that opens, the stat codes and the
behaviour all follow it: for example every armor is a container, every drink is
a container, and every rideable is a container.

### Plain items

| Class | Based on | What it is / adds |
| --- | --- | --- |
| `GenItem` | `StdItem` | The universal item: name, description, weight 2, value 5, level 1, oak. Use it for anything with no special behaviour: treasure, tools, quest objects, scenery |
| `GenReadable` | `GenItem` | An item that is always readable; its readable text is what `READ` shows. A text starting `FILE=` reads a file instead (below) |
| `GenWallpaper` | `Item` | A very light item for scenery: just a name, description and readable text (no stats, not gettable). Hundreds of these cost little memory |
| `GenFatWallpaper` | `GenWallpaper` | Wallpaper that also has display text |
| `GenFixture` | `GenItem` | A fixed object (not gettable, weight 20, value 10), e.g. an anvil or an altar |
| `GenMirror` | `GenItem` | A mirror: looking at it shows the viewer to themselves |
| `GenKey` | `GenItem` | A key. Its readable text is the key code. See [keys](#keys) |
| `GenResource` | `GenItem` | A raw resource item (a pile of iron, a log of oak). Adds the editor fields *Rsc Sub-Type*, and stat codes `DOMAINSRC` and `RSUBTYPE` |
| `GenFoodResource`, `GenLiquidResource` | `GenFood`, `GenDrink` | Raw resources that can be eaten or drunk |
| `GenRuinedItem` | `GenItem` | The shell of an item that wore out or was ruined; cannot be worn or wielded |
| `GenAmmunition` | `StdItem` | A stack of ammunition (arrows, bolts). Its *readable text* is the ammunition type, and *Uses Remaining* is the count. Weapons that require ammunition match this type |
| `GenCaged` | `GenItem` | A caged creature: a mob frozen into an item (a pet in a cage), which is released as a mob |
| `GenPackagedItems`, `GenPackagedStack`, `GenPackagedVariety` | `GenItem` | Package items made by the `PACKAGE` command; hold copies of one or several kinds of item. Field: *Number of items in the package* |
| `GenPerfume` | `StdPerfume` | A perfume bottle (a drink container); adds *Smells list* |
| `GenCigar`, `GenPipe` | `StdSmokable` | Smokeable items; wearing in the mouth lights them. Adds a burn duration (default 200) |
| `GenLimb` | `StdLimb` | A false limb; field *Race ID* (the race whose limb it replaces) |
| `GenGrapples` | `StdGrapples` | A set of ship grapples that can be thrown to lash vessels together |
| `GenTitle` | `StdTitle` | A land title: *Owner*, *Price* and *Land plot ID* (a room) |
| `GenMagicInstrument`, `GenInstrument` | | see [instruments](#instruments) |

### Readables, books and boards

| Class | Based on | What it is / adds |
| --- | --- | --- |
| `GenPaper` | `StdPaper` | A sheet or book of writable paper, with *Max Pages* and *Chars/Page* |
| `GenBook` | `StdBook` | A book readers `READ [n] BOOK` and writers `WRITE` chapters into. Chapters are kept as journal entries named after the book. Adds *Max Pages*, *Chars/Page* |
| `GenJournal` | `StdJournal` | A journal/board with read and write permissions as masks |
| `GenPlayerBook` | `StdPlayerBook` | A book that players write and read |
| `GenTome` | `GenReadable` | A static paged book (`READ 3 TOME`, `READ NEXT`, `READ PREV`, `TEAR PAGE 2 FROM TOME`). Pages live in the item text, so they are in world files |
| `GenMap` | `StdMap` | A map of one or more areas (readable text: area names separated by `;`) |
| `GenRecipe` | `GenReadable` | A recipe book that teaches or lists crafting recipes for one skill; stat codes `SKILLID`, `RECIPES`, `NUMRECIPES` |
| `GenDissertation` | `StdDissertation` | A scroll (a `Scroll`) that teaches: the abilities in its text are offered to the reader to learn from it |
| `GenLawBook` | `StdLawBook` | A book of entries read with `READ [page] "law book"` and added to with `WRITE`; see [09-society.md](09-society.md) for the law system |
| `GenQuestBoard` | `StdQuestBoard` | A notice board that lists open quests; `READ` shows them |

Detail for books follows in [Readable items](#readable-items-books-journals-maps-scrolls).

### Weapons

| Class | Based on | What it is / adds |
| --- | --- | --- |
| `GenWeapon` | `StdWeapon` | The general weapon. Wield or hold, steel, damage 5, level 5. Adds attack type, classification, ranges, ammunition |
| `GenBow` | `StdBow` | Ranged weapon with ammunition (arrows), ranges 1-3, two-handed, 20 shots |
| `GenHandBow` | `StdBow` | A hand crossbow: ammunition type `bolts` |
| `GenSling` | `StdSling` | Ranged weapon with ammunition type `bullets`, ranges 1-2 |
| `GenSpear` | `StdSpear` | A spear that can be thrown: ranges 0-3 |
| `GenBoffWeapon` | `StdBoffWeapon` | A harmless "boff" weapon that deals soft hits ("puff", "boff") for practice and play |
| `GenLasso`, `GenNet` | `StdLasso`, `StdNet` | Entangling tools used as weapons |
| `GenStaff` | `GenWeapon` + `Wand` | A staff that is both a weapon and a wand (spell, charges, enchant type) |
| `GenSiegeWeapon` | `StdSiegeWeapon` | A rideable siege engine with ammunition and ranges (default max range 10); see [30-travel-vehicles-instances.md](30-travel-vehicles-instances.md) |
| `GenShield` | `GenArmor` + `Shield` | Armor held in the hand |

`Natural` is the weapon type used for creatures' natural attacks; you do not
create it. See [Weapons](#weapons).

### Armor

| Class | Based on | What it is / adds |
| --- | --- | --- |
| `GenArmor` | `StdArmor` | General armor. Weight 25, armor 10, leather, level 1. A container with capacity 0, subject to wear and tear, sized by height |
| `GenThinArmor` | `StdThinArmor` | Clothing-grade armor: not a container, no wear and tear, ignores size |
| `GenShirt`, `GenPants`, `GenShoes`, `GenDress`, `GenBelt` | `GenArmor` | Pre-set wear location and look for clothes |
| `GenCloak` | `GenArmor` | A cloak that replaces the wearer's visible name with its *Cloak Name* while worn |
| `GenGangline` | `GenArmor` | Leather straps that bind a team of animals together |
| `GenEarring` | `GenThinArmor` | Jewelry worn on a pierced body part (ears), multi-wear at layer -10 |
| `GenShield` | `GenArmor` + `Shield` | A shield held in a hand |

### Containers, drink and food

| Class | Based on | What it is / adds |
| --- | --- | --- |
| `GenContainer` | `StdContainer` | General container: capacity 50, optional lid and lock, types allowed |
| `GenGiftContainer` | `GenContainer` | A gift-wrapped container ("Open it and find out what's in it!") |
| `GenBagOfHolding` | `BagOfHolding` | Container whose contents weigh less than normal |
| `GenBagOfEndlessness` | `BagOfEndlessness` | A bag that refills. Archon only in its plain form |
| `GenDrink` | `StdDrink` | A drinking container (a drink is a container): capacity 5, fresh water, leather, quenches per sip |
| `GenEmptyDrink` | `GenDrink` | A drink container that starts empty |
| `GenWater`, `GenFountain` | `GenDrink`/`GenWater` | An unlimited source of liquid (puddle, fountain) that fills drink containers |
| `GenTub` | `StdTub` | A bath tub that can hold riders and liquid |
| `GenLantern` | `GenLightSource` + `LiquidHolder` | A lantern fuelled by liquid (lamp oil); default duration 800 |
| `GenStove` | `GenContainer` + `Light` + `FuelConsumer` | A stove or cooker that burns fuel (`CONSUMEDTYPES`) |
| `GenBurnOven` | `GenContainer` | A wood-burning oven |
| `GenFood` | `StdFood` | Food. Adds *Nourishment* and *Bite size* |
| `GenPill` | `StdPill` | A pill that casts spells when eaten (spell list in readable text) |
| `GenSuperPill` | `GenPill` | Archon pill: stat/training boosts. Archon only |
| `GenPotion` | `StdPotion` | A potion that casts spells on drinking |
| `GenMultiPotion` | `GenDrink` + `Potion` | A flask with several doses |
| `GenPowder` | `StdPowder` | Magic dust (`MagicDust`): thrown or dusted on a target, it casts its spells on them |
| `GenLightSource` | `LightSource` | A light that burns for a duration (readable text) and may be destroyed when it runs out |

### Magic items

| Class | Based on | What it is / adds |
| --- | --- | --- |
| `GenWand` | `StdWand` | A wand: one spell, charges, enchant type. Words activate it |
| `GenStaff` | `GenWeapon` + `Wand` | A staff that is also a weapon |
| `GenScroll` | `StdScroll` | A scroll of one or more spells, read off to cast them |
| `GenPotion`, `GenPill`, `GenPowder`, `GenMultiPotion` | | see above |
| `GenMagicInstrument` | `GenItem` + `MusicalInstrument` + `Wand` | An instrument that casts a spell |
| `GenDice`, `GenPlayingCard` | `StdDice`, `StdPlayingCard` | Dice and cards (`DeckOfCards`, `HandOfCards`) |

### Furniture, rideables and boardables

| Class | Based on | What it is / adds |
| --- | --- | --- |
| `GenRideable` | `StdRideable` | The base for anything creatures can sit on, sleep on or ride. Default is a generic boat |
| `GenFurniture` | `GenRideable` | Furniture (sit, 2 riders, not gettable, oak, weight 40) |
| `GenChair`, `GenBed`, `GenTable`, `GenTent` | `GenRideable` | Seat, bed, table and tent with the right rider basis |
| `GenScale` | `GenRideable` | A set of scales: things placed on it (capacity 10000) or sat on it are weighed |
| `GenBoat` | `GenRideable` + `SiegableItem` | A simple water-based boat |
| `GenCage`, `GenCageRideable` | | A cage holding a caged animal; a cage wagon |
| `GenPortal` | `StdPortal` | An item that is also an exit: its readable text lists room IDs |
| `GenMobilePortal` | `GenPortal` | A portal that is also a `Rideable` |
| `GenBoardable` | `StdBoardable` | An item you can enter, with its own area and rooms (a house-like vessel) |
| `GenNavigableBoardable`, `GenSailingShip`, `GenCaravan`, `GenAirShip`, `GenBalloon` | | Boardables that move; `GenSiegableBoardable`, `GenCastle` add siege |
| `GenSpaceBody`, `GenWormhole` | | Bodies and wormholes in space |

Boardables, ships and vehicles are covered in
[30-travel-vehicles-instances.md](30-travel-vehicles-instances.md).

### Standard (ready-made) items

These classes have fixed stats and are created with `CREATE ITEM [ID]`. They
are mainly filler and examples; for your own world, copy a generic class.

| Folder | Examples |
| --- | --- |
| `Items/Weapons` | `Dagger`, `Dirk`, `Stiletto`, `Sword`, `Shortsword`, `Longsword`, `Claymore`, `Katana`, `Rapier`, `Sabre`, `Scimitar`, `TwoHandedSword`, `BattleAxe`, `Halberd`, `Glaive`, `Spear`, `Javelin`, `Mace`, `SmallMace`, `WarHammer`, `SmallHammer`, `Quarterstaff`, `Staff`, `Whip`, `Sickle`, `Trident`, `Cestus`, `Arquebus`, `Broom`, `Ruler`, `ThrowingStone`, `ShieldWeapon` and the themed `Drow*`, `FlamingSword`, `HolyAvenger`, `DaggerOfVenom`, `DragonClaw`, `EternityQuarterstaff` |
| `Items/Armor` | `LeatherArmor`, `StuddedLeatherArmor`, `PaddedArmor`, `HideArmor`, `ChainMailArmor`, `ChainMailVest`, `RingMail`, `ScaleMail`, `SplintMail`, `BandedArmor`, `FieldPlate`, `FullPlate`, `Helmet`, `GreatHelm`, `LeatherCap`, `Hat`, `Hood`, `Robes`, `ThreadbareRobes`, `Shirt`, `Pants`, `Dress`, `Shoes`, `WalkingBoots`, `LeatherBoots`, `LeatherBracers`, `MetalBracers`, `LeatherGauntlets`, `SteelGauntlets`, `StdShield`, plus themed `Eternity*`, `MichaelsMithrilChain`, `GlovesSpeed`, `Hat_Intelligence`, `GlowingMageArmor` |
| `Items/Basic` | `Apple`, `Ration`, `Waterskin`, `Wineskin`, `OilFlask`, `Torch`, `Lantern`, `Backpack`, `SmallSack`, `LargeSack`, `SmallChest`, `LargeChest`, `NeedleChest`, `TrappedChest`, `Bed`, `Chair`, `Boat`, `HorseCart`, `Ring`, `Ring_Ornamental`, `Gem`, `Parchment`, `Pot`, `Pan`, `StdKey`, `StdInnKey`, `Spring`, `HoleInTheGround`, `Corpse`, `QuestPoint`, `BardMap`, `SeaMap`, `LockableContainer`, `CloseableContainer`, `Star`, `Moonlet`, `DwarfStar`, `GasGiant`, `Asteroid`, `MassiveStar` |
| `Items/MiscMagic` | `BagOfHolding`, `BagOfEndlessness`, `CubicGate`, `PortableHole`, `LifeFountain`, `DruidicMonument`, `Ring_Protection`, `RingOfFortitude`, `RingOfMagicResistance`, `RingOfColdProtection`, `RingOfElectricity`, `RingOfJustice`, `Wand_Fire`, `Wand_Nourishment`, `Wand_Advancement`, `PracticePoint`, `TrainingSession`, `Ingredients`, `HerbChest`, `ScrollSpell`, and the archon-only `WandArchon`, `ManualArchon`, `ManualClasses`, `ManualAdvancement`, `GoodyBag`, `ArchonJournal`, `ArchonStaff` |

Names in `Items/` that end in `Wrapper` (`StdItemWrapper` and so on) are
engine helpers for proxying objects, not items to create.

## Weapons

Weapon classes implement `Weapon` (and `AmmunitionWeapon` for ranged ones).
They are wielded or held, add their damage and attack to the wielder's attack,
and are what `Natural` creature attacks mimic.

### Fields that matter

| Field | Meaning |
| --- | --- |
| Damage | Base damage per hit. Magical Ability adds to it, and each point of ability adds 10 to the attack adjustment |
| Attack Adjustment | Added to the wielder's attack. Only applies while the weapon is wielded (not merely held) |
| Level | Wielders below this level are told the weapon "looks too advanced for you". Editing it re-balances damage, attack and value with `balanceItemByLevel` |
| Weapon Attack Type | The damage type, below |
| Weapon Classification | The weapon skill category |
| Minimum / Maximum Ranges | Reach in rooms. `0/0` is melee. A bow is `1/3`, a sling `1/2`, a thrown spear `0/3` |
| Ammunition | Whether the weapon needs ammunition, its type (a word, such as `arrows`) and capacity. When on, the weapon's *Uses Remaining* is the rounds loaded |
| Condition | For weapons with no ammunition: 100 is perfect. Below 100 the damage scales down by that percent |
| Two-handed | Toggle G in the *Is Gettable* prompt: the weapon needs both hands (`WORNAND` true). Without it the weapon is one-handed and may also be held |

A new `GenWeapon` is wield-or-hold (`WORN_WIELD | WORN_HELD`), steel, weight 2,
damage 5, level 5, value 5, condition 100.

### Weapon attack types

`Weapon.TYPE_DESCS`. The editor offers the letter-coded first seven; use
`MODIFY ITEM x WEAPONTYPE [name]` for all of them. The message type is the
kind of damage the engine reports for resistances and saves.

| Index | Name | Editor letter | Damage message type |
| --- | --- | --- | --- |
| 0 | NATURAL | N | physical |
| 1 | SLASHING | S | physical |
| 2 | PIERCING | P | physical |
| 3 | BASHING | B | physical |
| 4 | BURNING | F | fire |
| 5 | BURSTING | M | physical |
| 6 | SHOOTING | R | physical |
| 7 | FROSTING | | cold |
| 8 | GASSING | | gas |
| 9 | MELTING | | acid |
| 10 | STRIKING | | electric |
| 11 | LASERING | | laser |
| 12 | SONICING | | sonic |
| 13 | DISRUPTING | | poison |
| 14 | STULTIFYING | | paralysis |
| 15 | CORRUPTING | | undead/unholy |
| 16 | SCRAPING | | water |

### Weapon classifications

`Weapon.CLASS_DESCS`; the word is also what class weapon restrictions name
(see [03-characters.md](03-characters.md)).

| Index | Name | Editor letter |
| --- | --- | --- |
| 0 | AXE | A |
| 1 | BLUNT | B |
| 2 | EDGED | E |
| 3 | FLAILED | F |
| 4 | HAMMER | H |
| 5 | KARATE (natural attacks) | K |
| 6 | POLEARM | P |
| 7 | RANGED | R |
| 8 | SWORD | S |
| 9 | DAGGER | D |
| 10 | STAFF | T |
| 11 | THROWN | N |

### Ammunition

To make a ranged weapon that needs ammunition:

1. Create the weapon (`GenBow`, `GenSling`, `GenHandBow` or `GenWeapon`) and, at
   *Does this weapon require ammunition*, answer `Y`. Enter the ammunition
   type and the capacity (how many it holds).
2. Create a `GenAmmunition` item. Its **readable text** is the ammunition type
   (it must equal the weapon's `AMMOTYPE`), and its **Uses Remaining** is how
   many rounds the stack holds.
3. Players `LOAD` or the weapon reloads from carried ammunition of the type.

(The editor's reminder text says to put the type in the "secret identity" of
the ammunition; the code reads the readable text. See the audit notes.)

### Ranged and special weapons

`StdBow`, `StdSling`, `StdSpear`, `StdLasso`, `StdNet`, `StdBoffWeapon` and
`StdSiegeWeapon` are the base classes behind the `Gen*` variants above. A
lasso and a net entangle; a boff weapon deals only play damage; a siege weapon
is rideable and has ranges up to 10.

## Armor and wear locations

### How armor works

`GenArmor` classes add their **Armor** value to the wearer's armor, which is
"higher is better" on an item and subtracted from the wearer's armor class.
Magical Ability on armor adds more: x5 at the torso, x2 on the head or in the
hand, x1 elsewhere, unless the piece is flagged multi-wear.

The material gives the wearer a small saving-throw adjustment while worn:
metal -10 to the electric save, leather +10 to acid, mithril +10 magic, cloth
and paper -10 fire, glass/rock/precious/vegetation/flesh/synthetic +10 fire,
energy +10 paralysis.

Armor is subject to wear and tear while its condition (Uses Remaining) is
between 0 and 1000: it loses condition when the wearer is struck, shows
messages as it degrades, and its value falls with condition. To make a piece
that never wears, give it more than 1000 uses, or add the `Prop_Durability`
property with `UNBREAKABLE`. Thin armor never wears. See coffeemud.ini
`ITEMWEAR` (percent of the usual wear, 100 by default, 0 for none) and
`ITEMBREAK` (`DESTROY` by default, or `BREAK`, which leaves a broken item that
can be mended); both are described in
[23-properties-reference.md](23-properties-reference.md#prop_durability).

Note: `Integer.MAX_VALUE` (the usual "no limit") is silently converted to 100
when set on armor.

### Wear locations

An item says where it can be worn in two fields: the **wear location bitmap**
(`PROPERWORN`) and **wornAnd** (`WORNAND`). With *wornAnd* false the item fits
on **any one** of the ticked locations; with it true the item occupies **all**
of them (a robe on torso, arms and legs; a two-handed weapon on wield and
held). A new `GenArmor` has no location (it cannot be worn until you give
it one). In the editor, `0: Done`, `1:` toggles any/all, and the locations
are numbered from 2: type a number (or the first letters of the name) to
toggle one.

| Name | Bit value | Code constant | Usual item name | Armor strength | Notes |
| --- | --- | --- | --- | --- | --- |
| head | 1 | `WORN_HEAD` | hat | 1.0 | |
| neck | 2 | `WORN_NECK` | necklace | 0.5 | |
| torso | 4 | `WORN_TORSO` | shirt | 3.0 | |
| arms | 8 | `WORN_ARMS` | sleeves | 1.0 | |
| left wrist | 16 | `WORN_LEFT_WRIST` | bracelet | 0.5 | |
| right wrist | 32 | `WORN_RIGHT_WRIST` | bracelet | 0.5 | |
| left finger | 64 | `WORN_LEFT_FINGER` | ring | 0.25 | |
| right finger | 128 | `WORN_RIGHT_FINGER` | ring | 0.25 | |
| feet | 256 | `WORN_FEET` | shoe | 0.5 | |
| held | 512 | `WORN_HELD` | gizmo | 2.0 | Hand-held; a shield counts here |
| wield | 1024 | `WORN_WIELD` | weapon | 1.0 | Weapon hand |
| hands | 2048 | `WORN_HANDS` | gloves | 0.5 | |
| floating nearby | 4096 | `WORN_FLOATING_NEARBY` | floaty | 0.01 | Orbits the wearer |
| waist | 8192 | `WORN_WAIST` | belt | 0.5 | |
| legs | 16384 | `WORN_LEGS` | pants | 2.0 | |
| eyes | 32768 | `WORN_EYES` | eyeglasses | 0.25 | |
| ears | 65536 | `WORN_EARS` | earmuffs | 0.25 | |
| body | 131072 | `WORN_ABOUT_BODY` | cloak | 1.5 | Worn "about the body" (cloaks) |
| mouth | 262144 | `WORN_MOUTH` | mouthpiece | 0.01 | |
| back | 524288 | `WORN_BACK` | cape | 1.0 | |

Inventory (carried, not worn) is bit 0. Source:
[Wearable.java](../../CoffeeMud/com/planet_ink/coffee_mud/Items/interfaces/Wearable.java).
The "armor strength" is the location's share of a full suit of armor
(`DEFAULT_WORN_WEIGHTS`); the engine's item-balancing code uses it with a
per-material weight (cloth, leather, metal) to compute the armor value and
weight of a piece for its level, which is what `balanceItemByLevel` and
`RESET ITEMSTATS` apply.

A race can lack some locations (no hands, no ears); items for those locations
cannot be worn by that race. A character's free slots at a location are also
bounded by the layers below.

Extra locations are defined in `coffeemud.ini`:

```ini
# ADDWEARLOC_[NAME]=dependencies|...,armorStrength,order,clothWeight,leatherWeight,metalWeight
ADDWEARLOC_FACE=eyes|mouth,0.2,16,0.5,0.7,1.0
REPLACEWEARLOC_EYES=,0.1,7,0.1,0.2,0.3
```

The six fields, in order: wear locations that depend on this one separated
by `|`, the armor score (0.0-2.0), the displayed order (0-n), and the weight
scores for cloth, leather and metal (0.0-10.0). Exactly six fields are
required; a row with any other count is logged as an error and skipped. An
added location is appended after the built-in ones and appears in the editor's
location list; `REPLACEWEARLOC_` takes the name of a built-in location. These
are engine-wide settings, so make them once, before building. They are also
documented in `coffeemud.ini`.

### Layers

Layers let a character wear several pieces on one location (a shirt, a tunic
and a coat on the torso). Each piece has a **layer** number and two flags.

| Setting | Meaning |
| --- | --- |
| Layer | `0` is the baseline. Positive layers are worn over it (coats, shawls); negative layers are worn under it (underwear, hose). Default for most armor is 0. `GenEarring` is -10 |
| See-through | `LAYERMASK_SEETHROUGH` (1). A piece on the top layer that is see-through lets the pieces below show |
| Multi-wear | `LAYERMASK_MULTIWEAR` (2). Many such pieces may share the same layer (rings, earrings). Magical Ability does not stack armor for these |

In the editor: `Enter a new layer`, then `Is see-through (Y/N)?` and
`Is multi-wear (Y/N)?`. Stat codes: `LAYER`, `LAYERATTRIB` (the sum of the
flag values). A character cannot wear two pieces on the same location at the
same layer unless one is multi-wear. The `LAYERED` option of
`Prop_WearAdjuster`, `Prop_WearSpellCast` and `Prop_WearResister` makes only
the top layer's bonus count.

### Size

Armor is cut for a height (the **Size** prompt, stat `HEIGHT`). The check
runs only when both the armor's size and the wearer's height are above 0, so
size 0 fits anyone. If the wearer's height differs from the armor's by more
than the allowance, the wear is refused with "it's too big" or "it's too
small". The allowance depends on the item's locations: 20 if it uses the
torso, legs, waist, arms, hands or feet; 1000 for eyes or ears, 5000 for the
neck; no check for floating or mouth items; 200 otherwise. The check is
skipped when `EQUIPSIZE` is in the `DISABLE` list of `coffeemud.ini`
(`CMSecurity.DisFlag.EQUIPSIZE`).

### Shields and other armor kinds

A `GenShield` is armor that is held in a hand (`WORN_HELD`). Only a `Shield`
can be held and still count as armor. `GenThinArmor` is for clothing and
jewelry. `GenCloak` hides the wearer's name. See [Item classes](#armor).

## Containers, lids, locks and keys

Containers implement `Container`. Anything that can hold other things is one:
`GenContainer`, every armor, every drink, every rideable and portal. The
fields are the same everywhere (stat codes in parentheses).

| Field | Meaning |
| --- | --- |
| Capacity (`CAPACITY`) | Maximum total weight of the contents (weight stands in for volume). 0 means nothing fits |
| Can contain (`CONTAINTYPES`) | The kinds of item allowed. In the editor you enter a type name to toggle it; `ANYTHING` (the default) clears the list. Types are listed below |
| Has a lid (`HASLID`) | Whether it opens and closes. Without a lid, contents are always reachable |
| Defaults closed (`DEFCLOSED`) | The lid shuts again when the item resets |
| Has a lock (`HASLOCK`) | Whether it locks (needs a lid) |
| Defaults locked (`DEFLOCKED`) | The lock re-engages when the item resets (needs a lock and default-closed) |
| Reset Delay (`RESETTIME`) | Ticks after it is opened or unlocked before it returns to its default state (default 30). It does not reset while a key that fits is inside it |
| Key Code (`READABLETEXT`) | The code a key must have to fit. Shown only when it has a lid and a lock |

The editor asks them as one question series: `Has a lid` (Y/n), `Defaults
closed`, `Has a lock`, `Defaults locked`, then the delay.

Container type bits (`Container.CONTAIN_*`, the `CONTAINTYPES` value is their
sum; 0 means anything):

| Name in the editor | Bit | Allows |
| --- | --- | --- |
| ANYTHING | 0 | everything (clears the list) |
| LIQUID | 1 | liquids (a drink container) |
| COINS | 2 | money |
| SWORDS | 4 | swords |
| DAGGERS | 8 | daggers |
| OTHER WEAPONS | 16 | weapons that are not swords or daggers |
| ONE-HANDED WEAPONS | 32 | one-handed weapons |
| BODIES | 64 | corpses |
| READABLES | 128 | readable items |
| SCROLLS | 256 | scrolls |
| CAGED ANIMALS | 512 | caged creatures |
| KEYS | 1024 | keys (a key ring) |
| DRINKABLES | 2048 | drinkable items |
| CLOTHES | 4096 | clothes |
| SMOKEABLES | 8192 | pipes, cigars and smokeable herbs |
| SS COMPONENTS | 16384 | spell and skill components |
| FOOTWEAR | 32768 | shoes and boots |
| RAWMATERIALS | 65536 | raw resources |
| EATABLES | 131072 | food |
| SHIELDS | 262144 | shields |

### Keys

A key is a `GenKey` (or `StdKey`/`StdInnKey`). Its key code is the item's
readable text (editor line *Assigned Key Code*). A lock opens if a key the
character carries has a matching code. Matching rules (`Keys.fits`):

- A code matches a lock name exactly, ignoring case.
- A code may be a list separated by semicolons: `crypt;vault` fits locks named
  `crypt` or `vault`.
- A code ending in `*` is a master key for every lock whose name begins with
  the text before it: `jail.*` fits `jail.1`, `jail.2`...
- A key inside a key ring (a container whose types are `KEYS`) counts, however
  deeply nested.
- A lock with `Prop_LockRules KEYS=a;b` also takes those codes.

Locks on exits use the same codes (see [20-areas-rooms-exits.md](20-areas-rooms-exits.md)).
To make keys used up, personal, or locks unpickable, attach `Prop_KeyRules`
(`CONSUME`, `BREAK`, `RETURN`, `MASK`, `MSG`) to the key and `Prop_LockRules`
(`PICK=NEVER|LEVEL:n`, `KEYS`, `RELOCK`, `MSG`) to the lock; see
[23-properties-reference.md](23-properties-reference.md#prop_keyrules) and
[23-properties-reference.md](23-properties-reference.md#prop_lockrules).
Keys that are no use unless a puzzle is solved (combinations, receptacles) are
in [07-items-crafting.md](07-items-crafting.md).

## Food, drink and light

### Food

`GenFood` has two numbers: **Nourishment** (hunger restored by the whole
item) and **Bite** (restored per bite; 0 eats it all). A pill is food that
casts the spells in its readable text. Food is edible resources too:
`GenFoodResource` is a resource item (a loaf of bread) of a material from the
food families.

### Drink

`GenDrink` is a drink container: **Quenched/Drink** is the thirst each drink
restores, **Amount of Drink Held** is how many drinks it holds when full (it is
full after editing), and **liquid type** is a resource from the liquid family
(`?` lists them: water, salt water, liquid, milk, coffee, blood,
lamp oil, poison, liquor, honey, sap, perfume, dragon blood, slime...). It is
also a container, with a lid if you give it one (a flask with a stopper).
`GenWater` and `GenFountain` are unlimited sources, `GenEmptyDrink` starts
empty, and `GenPerfume` adds a list of smells (`;`-separated) seen when
sniffed.

### Light sources

`GenLightSource` burns for a **duration in ticks** (its readable text; default
200) and then goes out. The *Is destroyed after burnout* toggle chooses
between being used up and being empty/refillable. `GenLantern` is a light
fuelled from a liquid (lamp oil) and holds liquid; the standard `Torch`,
`Lantern` and `OilFlask` classes are ready-made examples. A lit light source
lights the room; an item that should shine without burning out can instead be
given the `lightsrc` or `glowing` disposition.

## Readable items: books, journals, maps, scrolls

### The readable flag and readable text

Every item has **readable text**. The *Is this item readable* question sets
the `ISREADABLE` flag, which makes `READ` show that text. The editor turns the
flag on by itself for classes whose ID ends in `Readable`, recipe books and
maps, and forces it off for wands, scrolls, lights, containers, ammunition and
keys. The *readable text* line is relabelled per class:

| Class kind | Readable text means |
| --- | --- |
| generic readable | The text read |
| Wand | Assigned Spell Name (one ability ID) |
| Spell holder (scroll, potion, pill, powder) | Assigned Spell(s), `;`-separated, each optionally `Spell_Name(parameters)` |
| Super pill | Assigned Spell or Parameters |
| Ammunition | Ammunition type |
| Key | Assigned Key Code |
| Map | Assigned Map Area(s), `;`-separated area names |
| Light | Light duration in ticks |
| Land title | Assigned Property ID |
| Exit-like item (portal) | Assigned Room IDs |

`READ item` behaves as follows (`CommonMsgs.handleBeingRead`):

- If the readable text begins with `FILE=`, the rest is a path of a text file
  read at the time (a `world/...` path comes from the world folder). A missing
  or empty file reads "There is nothing written on...".
- If the text is empty, the item's description is read when the item has no
  display text or cannot be picked up (a sign on the wall).
- The text goes through the VARIES / story-text renderer, so season and
  state variants work; see [11-presentation-language.md](11-presentation-language.md).
- `WRITE` on a readable item appends to its readable text, except on `FILE=`
  items ("There's no more room to write on...").

### GenTome: paged books in the world file

`GenTome` stores its pages in the item itself, so they travel in world files
and appear in every new game. The readable text holds pages as blocks:

```text
<PAGE TITLE="The Founding">Ost was founded by the river.</PAGE><PAGE TITLE="Elder Words" LANG=Elvish>Mae govannen, travellers.</PAGE><PAGE TITLE="The Fall" LORE=ost.fall>The tower fell in the seventh year.</PAGE>
```

or `FILE=world/texts/ost.txt`, whose sections each start with a line
`=== title ===` (optionally `=== title | LANG=Elvish ===`).

| Page attribute | Effect |
| --- | --- |
| `TITLE` | Name shown in the contents and usable to turn to the page |
| `LANG` | The reader must know the language, or the page stays closed |
| `LORE` | Reading the page gives the reader the tattoo `LORE:key` |

Commands: `READ book` (contents), `READ 3 book`, `READ NEXT book`,
`READ PREV book`, `READ [title] book`, and `TEAR PAGE 3 FROM book`, which
makes a `GenReadable` page and tags the book `TORN:3`. A `READ_PROG PAGE n`
trigger fires when page `n` is read ([26-mobprog-triggers.md](26-mobprog-triggers.md)).
Source: [GenTome.java](../../CoffeeMud/com/planet_ink/coffee_mud/Items/Basic/GenTome.java);
fixture `tools/fixtures/fixes/i3-tome.js`.

### GenBook, GenJournal, GenPaper

`GenBook` keeps its chapters as journal entries named after the book, so
writing in a book is campaign state, not world data. To give a new book its
first chapters from the world files, put them in `world/journals/NAME.txt`
(NAME is the book's name in lower case with spaces as `_`): each entry begins
with a line `=== subject ===`, and an `AUTHOR: name` line before the first
sets the author. An empty book or board is filled from it when first read
(`JournalSeeds`). `GenJournal` is a board with read and write access set as
masks.

### Maps

`GenMap` shows a drawn map of the areas named in its readable text. Setting
the text renames the item "a map of ...". Rooms flagged unmappable are left
out. `GenRecipe` is a recipe book (stat codes `SKILLID`, `RECIPES`); see
[07-items-crafting.md](07-items-crafting.md).

## Magic items: wands, staffs, scrolls, potions, pills

Source: [Items/MiscMagic/](../../CoffeeMud/com/planet_ink/coffee_mud/Items/MiscMagic/).

### Wands and staffs

`GenWand` (and `GenStaff`, which is also a weapon) holds **one spell**. Fields:

| Field | Meaning | Stat code |
| --- | --- | --- |
| Assigned Spell Name | The ability ID that is cast | `SPELL` |
| Enchant Type | `ANY`, `SPELL`, `PRAYER`, `CHANT` or `SKILL`: restricts which kind of ability the wand may hold and which wand skill works it | `ENCHTYPE` |
| Uses Remaining | Current charges | `USES` |
| Maximum Uses | Maximum charges after recharging | `MAXUSES` |
| Level | Cast level (plus ability, up to +10) | `LEVEL` |
| Magical Ability | Plus to the cast level | `ABILITY` |
| Secret Identity | Extra text under the generated "A wand of ..." text | `SECRET` |

To use a wand the character must hold or wield it (not just carry it) and say
its **magic word** near the target. The word is not chosen by the builder: it
is taken from the spell ID by a hash into the `MAGIC_WORDS` list in
`resources/lists.ini`, and identify magic reveals it. The character also needs
a wand-using skill matching the enchant type: `Skill_WandUse` for spell wands,
`Skill_RelicUse` for prayers, `Skill_ShardUse` for chants (instrument wands use
`PlayInstrument`). Each use costs some mana, burns one charge, and runs the
spell at the wand's level. A character with no suitable wand skill, or who
fails its proficiency check, sees the wand "glow faintly for a moment, then
fade"; a spent wand "seems spent". A wand's value is 0 when empty.

### Scrolls

`GenScroll` holds one or more spells in its readable text, separated by `;`,
each optionally with parameters in parentheses (for example
`Spell_Portal(Somewhere else);Spell_Sleep`). The reader needs the matching
read-magic skill (`Spell_ReadMagic`, `Prayer_ReadPrayer`, `Chant_ReadRunes`,
`Song_ReadMusic`) for the kind of spell, and each reading uses one charge: *Uses
Remaining* is how many times it may be read before "the markings have been read
off the parchment". A new scroll has 0 uses, so set it to at least 1.

### Potions and pills

`GenPotion` is a drink container whose readable text holds spells; drinking
the potion casts them on the drinker and the potion is marked empty (`;DRUNK`
is appended to its spell list, and the flags prompt of the editor clears that
mark). `GenMultiPotion` is a flask with several doses. `GenPill` is food: eating it casts its
spells. `GenPowder` is dusted or thrown onto a target and casts its spells on
them; `GenSuperPill` (Archon only) sets stats directly.

A good rule: every magic item that has spells in it should also have a
sensible **level** (the spell is cast at least at the spell's lowest class
level) and a **value** consistent with the spells (the engine adds 100 per
spell level for dissertations and scrolls it builds itself).

### Other magic items

`GenBagOfHolding`, `GenBagOfEndlessness` and the standard rings and wands
under `Items/MiscMagic` are examples to copy. For effects that are not a
single spell, give an ordinary generic item properties instead
(see [Enchantments and item properties](#enchantments-and-item-properties)).

## Money

`GenCoins` is a stack of coins. The editor line is `Money data`, with three
prompts: **currency code** (blank or `Default` is the engine's default
currency; `?` lists the defined ones), **denomination** (a number or a
denomination name valid for that currency; `?` lists them) and **stack
size**. Stat codes: `NUMCOINS`, `CURRENCY`, `DENOM`. The currency of an area
and the default currency (`DEFAULTCURRENCY`, `CURRENCY_[CODE]` and
`resources/currencies.ini`) are described in [09-society.md](09-society.md).
`CREATE ITEM 100 gold coins` is the quick way to drop money.

## Furniture, rideables and boardables

Anything creatures can sit on, lie on, ride or enter is a `Rideable`. Its
fields (shown by the editors of rideable classes, and `GenFurniture`):

| Field | Meaning | Stat code |
| --- | --- | --- |
| Rideable Type | How riders use it: `LAND_BASED` (L), `WATER_BASED` (W), `AIR_FLYING` (A), `FURNITURE_SIT` (C), `FURNITURE_SLEEP` (B), `FURNITURE_TABLE` (T), `ENTER_IN` (E), `LADDER` (D), `WAGON` (G), `FURNITURE_HOOK` (H) | `RIDEBASIS` |
| Rider capacity | How many may ride at once | `MOBSHELD` |
| Mount Strings | The 'put', 'mount' and 'dismount' strings, such as "<S-NAME> sit(s) down on <T-NAME>" | `PUTSTR`, `MOUNTSTR`, `DISMOUNTSTR` |
| State Strings | The 'state' ("sitting on"), 'state subject' and 'ride verb' words | `STATESTR`, `STATESUBJSTR`, `RIDERSTR` |

Rider types sort furniture: a chair is `FURNITURE_SIT`, a bed
`FURNITURE_SLEEP`, a table `FURNITURE_TABLE` (things placed on it, people sat
at it), a boat `WATER_BASED`, a wagon `WAGON`, a tent `ENTER_IN` (riders are
inside it). The editor asks them with a single letter; `?` lists the
letters. `GenFurniture` is a rideable sitting furniture item that cannot be
picked up by default (weight 40, 2 riders).

`GenPortal`/`GenMobilePortal` are items that are also exits (the readable text
is the list of destination room IDs). `GenBoardable` and the navigable
boardables (`GenSailingShip`, `GenCaravan`, `GenAirShip`, `GenBalloon`,
`GenCastle`, and the spacefaring `GenSpaceShip`) are items with an interior
area. They have extra fields (`AREA`, `OWNER`, `PRICE`, `TICKSPERMOVE`,
`TICKSPERTURN`, `WEATHERSPEED`, `FIXEDINTERIOR`, and *Moves per Tick* in place of
magical ability). Their design, interiors, owner and travel are in
[30-travel-vehicles-instances.md](30-travel-vehicles-instances.md).

## Technology and clan items

### Technology items

`Items/BasicTech`, `Items/CompTech` and `Items/Software` hold electronics for
space, ship and technological settings: power sources and generators, weapons
(`GenLaserGun`, `GenPhaser`, `GenBlaster`, `GenCannon`, `GenTorpedo`...),
personal and ship shields, panels and computers (`GenComputerConsole`,
`GenElecPanel`), sensors, engines, thrusters, ship weapons and software
(`GenSoftware`, `GenShipProgram` and the nav, sensor and tactical programs).
Their extra editor fields:

| Field | Applies to | Meaning |
| --- | --- | --- |
| Manufacturer | any technical item | The `Manufacturer` that built it (create with `CREATE MANUFACTURER [name]`) |
| Pow Capacity / Pow Remaining / Activated | electronics | Battery size, charge, and whether it is switched on |
| Panel type | electronic panels | What kind of panel |
| Installed Factor, Pwr Recharge Rate | tech components | Installation efficiency, recharge rate |
| Min / Max thrust, Reaction engine, Fuel Spec Impulse %, Avail. ports | ship engines | Engine performance and which sides they can fire from |
| Max Ports, Avail. ports | directional components | Which directions the component can face |
| Message types, Damage | ship weapons | What messages fire it and its damage |
| Gen Amt/Tick, Consumed materials | generators | Power per tick and fuel resources used |
| Internal Name, Parent Menu | software | The program name and its menu |
| Technical Level | (the Magical Ability line of electronics) | Tech level |

They are covered with ships in
[30-travel-vehicles-instances.md](30-travel-vehicles-instances.md) and
[33-engine-internals.md](33-engine-internals.md). They are rarely needed for a
fantasy world.

### Clan items

`Items/ClanItems` holds `GenClan*` classes (flags, banners, tabbards, aprons,
containers, pamphlets, castles, donation lists...). A clan item adds the
fields **Clan** (the clan ID) and **Type**: one of FLAG, BANNER, GAVEL,
PROPAGANDA, GATHERITEM, CRAFTITEM, SPECIALSCALES, SPECIALSCAVENGER,
SPECIALOTHER, SPECIALTAXER, DONATIONJOURNAL, ANTI_PROPAGANDA, SPECIALAPRON,
LEGALBADGE, TABBARD, SAILORSCAP. They matter only if your world uses clans; see
[09-society.md](09-society.md). `LIST CLANITEMS` lists them.

## Instruments

`GenInstrument` has a single extra field, **Instrument Type**, one of
CLARINETS, CYMBALS, DRUMS, FLUTES, GUITARS, HARMONICAS, HARPS, HORNS, OBOES,
ORGANS, PIANOS, TROMBONES, TRUMPETS, TUBAS, VIOLINS, WOODS, XYLOPHONES or
OTHER_INSTRUMENT_TYPE (stat code `INSTTYPE`). Songs and play skills check the
type. It is worn in a wear location (usually held). `GenPiano` is a rideable
piano, and `GenMagicInstrument` also holds a spell like a wand.

## Materials

Every item has a **material**, which is one *resource* from a fixed table.
The resource belongs to a *family* (cloth, metal, wood...). Source:
[RawMaterial.java](../../CoffeeMud/com/planet_ink/coffee_mud/Items/interfaces/RawMaterial.java).

### What the material does

- The **family** is the high byte of the number (`MATERIAL_MASK`, 255 << 8).
  Crafting skills select ingredients by family (Blacksmithing works any
  metal), armor gets a small saving-throw adjustment from it (see
  [Armor and wear locations](#armor-and-wear-locations)), classes can restrict
  the weapon materials they may use, and `balanceItemByLevel` picks armor
  points and weight from the family's tables (cloth, leather, metal).
- The **resource** (the low byte, `RESOURCE_MASK`) sets the base value per
  unit used to price crafted items, the hardness, and how it floats.
- Raw resources (`GenResource`, ore, logs, hides, food ingredients) are items
  whose material is the thing they are.
- `LIST MATERIALS` lists the table the running game has; the item editor's
  `?` at *Material Type* lists the names.

### Setting a material

In the editor: `Material Type: 'STEEL'`, then enter a name (case-insensitive;
`?` lists them). From the command line:

```text
MODIFY ITEM sword MATERIAL STEEL
MODIFY ITEM sword MATERIAL 811
```

A resource number is `family + index`: STEEL is metal (768) + 43 = 811. In
saved items the number is stored as `<MTRAL>`; resources added by a world carry
their name as `<MTRALN>` too, so they are found again if the numbers move.

### Families

| Family | Number | Used for |
| --- | --- | --- |
| UNKNOWN | 0 | `NOTHING` |
| CLOTH | 256 | fur, hide, wool, cotton, silk... |
| LEATHER | 512 | scales, leather |
| METAL | 768 | iron, steel, gold, silver... |
| MITHRIL | 1024 | mithril, adamantite, atlantite (magic metals) |
| WOODEN | 1280 | woods |
| GLASS | 1536 | glass, clay, china, crystal |
| VEGETATION | 1792 | crops, fruit, herbs, flowers |
| FLESH | 2048 | meat, fish, eggs, wax |
| PAPER | 2304 | paper |
| ROCK | 2560 | stone, sand, bone, coal, salt |
| LIQUID | 2816 | water, milk, blood, oil, liquor |
| PRECIOUS | 3072 | gems, pearls, jade, diamonds |
| ENERGY | 3328 | energy, plasma, electricity |
| SYNTHETIC | 3584 | plastic, rubber, fibreglass |
| GAS | 3840 | air, oxygen, hydrogen... |

### The built-in resources

Resource names are the names typed in the editor and in `MODIFY ITEM x MATERIAL`
(case-insensitive, with spaces where the Java constant has underscores). Four
differ from their `RawMaterial.RESOURCE_` constant: `WATER` is
`RESOURCE_FRESHWATER`, `SALT WATER` is `RESOURCE_SALTWATER`, `LIQUID` is
`RESOURCE_DRINKABLE` and `LAMP OIL` is `RESOURCE_LAMPOIL`.

Columns: **Code** is the number to store; **Value** is the base worth per unit;
**Rarity** is the weight (lower is rarer) when the engine picks resources at
random; **Hardness** runs from 0 to 11; **Buoyancy** is a density-like number
(higher sinks); **Gathered as** says whether a gathering skill treats the
resource as FISH, BERRY or WOODY.

#### UNKNOWN (0)

| Resource | Code | Value | Rarity | Hardness | Buoyancy | Gathered as |
| --- | --- | --- | --- | --- | --- | --- |
| NOTHING | 0 | 0 | 0 | 0 | 0 |  |

#### CLOTH (256)

| Resource | Code | Value | Rarity | Hardness | Buoyancy | Gathered as |
| --- | --- | --- | --- | --- | --- | --- |
| FUR | 309 | 20 | 20 | 2 | 890 |  |
| HIDE | 311 | 4 | 20 | 2 | 920 |  |
| WOOL | 312 | 10 | 20 | 1 | 1310 |  |
| FEATHERS | 313 | 10 | 20 | 0 | 20 |  |
| COTTON | 314 | 5 | 20 | 1 | 590 |  |
| HEMP | 315 | 4 | 10 | 1 | 720 |  |
| SILK | 341 | 200 | 5 | 3 | 1100 |  |
| SPIDERSTEEL | 414 | 150 | 0 | 4 | 630 |  |
| POLYESTER | 455 | 15 | 0 | 1 | 545 |  |
| DENIM | 456 | 15 | 0 | 2 | 745 |  |

#### LEATHER (512)

| Resource | Code | Value | Rarity | Hardness | Buoyancy | Gathered as |
| --- | --- | --- | --- | --- | --- | --- |
| SCALES | 564 | 10 | 20 | 4 | 1800 |  |
| LEATHER | 566 | 10 | 20 | 2 | 945 |  |

#### METAL (768)

| Resource | Code | Value | Rarity | Hardness | Buoyancy | Gathered as |
| --- | --- | --- | --- | --- | --- | --- |
| IRON | 801 | 20 | 10 | 6 | 7900 |  |
| LEAD | 802 | 10 | 10 | 3 | 11300 |  |
| BRONZE | 803 | 10 | 10 | 5 | 8100 |  |
| SILVER | 804 | 30 | 2 | 4 | 10500 |  |
| GOLD | 805 | 150 | 1 | 4 | 19320 |  |
| ZINC | 806 | 10 | 5 | 4 | 7100 |  |
| COPPER | 807 | 10 | 10 | 5 | 8900 |  |
| TIN | 808 | 10 | 10 | 3 | 7300 |  |
| STEEL | 811 | 75 | 0 | 8 | 7840 |  |
| BRASS | 812 | 18 | 0 | 6 | 5500 |  |
| PLATINUM | 839 | 175 | 1 | 6 | 21450 |  |
| CHROMIUM | 942 | 80 | 1 | 6 | 1550 |  |
| WHITE GOLD | 943 | 150 | 1 | 5 | 1450 |  |
| DRAGONSCALES | 944 | 50 | 1 | 6 | 800 |  |
| ALUMINUM | 955 | 205 | 0 | 4 | 7300 |  |
| TITANIUM | 956 | 75 | 0 | 8 | 784 |  |
| ULTRANIUM | 958 | 175 | 0 | 11 | 900 |  |
| DURANIUM | 959 | 159 | 0 | 10 | 300 |  |
| PALLADIUM | 975 | 25 | 1 | 7 | 12020 |  |
| NICKEL | 976 | 10 | 2 | 5 | 8900 |  |

#### MITHRIL (1024)

| Resource | Code | Value | Rarity | Hardness | Buoyancy | Gathered as |
| --- | --- | --- | --- | --- | --- | --- |
| MITHRIL | 1065 | 100 | 1 | 9 | 3990 |  |
| ADAMANTITE | 1066 | 175 | 1 | 10 | 4500 |  |
| ATLANTITE | 1185 | 200 | 1 | 6 | 850 |  |

#### WOODEN (1280)

| Resource | Code | Value | Rarity | Hardness | Buoyancy | Gathered as |
| --- | --- | --- | --- | --- | --- | --- |
| WOOD | 1325 | 2 | 10 | 3 | 920 |  |
| PINE | 1326 | 4 | 10 | 3 | 650 |  |
| BALSA | 1327 | 1 | 5 | 2 | 130 |  |
| OAK | 1328 | 5 | 10 | 3 | 720 |  |
| MAPLE | 1329 | 10 | 5 | 3 | 689 |  |
| REDWOOD | 1330 | 20 | 2 | 3 | 450 |  |
| HICKORY | 1331 | 5 | 5 | 3 | 830 |  |
| IRONWOOD | 1364 | 25 | 5 | 4 | 990 |  |
| YEW | 1388 | 15 | 2 | 5 | 850 |  |
| TEAK | 1416 | 20 | 2 | 3 | 1000 |  |
| CEDAR | 1417 | 15 | 2 | 3 | 900 |  |
| ELM | 1418 | 15 | 2 | 3 | 1100 |  |
| CHERRYWOOD | 1419 | 17 | 2 | 3 | 900 |  |
| BEECHWOOD | 1420 | 12 | 2 | 3 | 975 |  |
| WILLOW | 1421 | 12 | 2 | 1 | 1000 |  |
| SYCAMORE | 1422 | 11 | 2 | 2 | 1000 |  |
| SPRUCE | 1423 | 12 | 2 | 3 | 990 |  |
| MESQUITE | 1424 | 9 | 2 | 3 | 1150 |  |
| BAMBOO | 1436 | 15 | 10 | 4 | 120 |  |
| REED | 1452 | 1 | 50 | 3 | 830 |  |

#### GLASS (1536)

| Resource | Code | Value | Rarity | Hardness | Buoyancy | Gathered as |
| --- | --- | --- | --- | --- | --- | --- |
| GLASS | 1599 | 10 | 0 | 3 | 2800 |  |
| CLAY | 1601 | 1 | 50 | 1 | 1750 |  |
| CHINA | 1602 | 30 | 0 | 3 | 2400 |  |
| CRYSTAL | 1604 | 10 | 5 | 5 | 2200 |  |

#### VEGETATION (1792)

| Resource | Code | Value | Rarity | Hardness | Buoyancy | Gathered as |
| --- | --- | --- | --- | --- | --- | --- |
| WHEAT | 1799 | 1 | 20 | 1 | 770 |  |
| CORN | 1800 | 1 | 20 | 1 | 720 |  |
| RICE | 1801 | 1 | 20 | 1 | 750 |  |
| CARROTS | 1802 | 1 | 5 | 1 | 720 |  |
| TOMATOES | 1803 | 1 | 5 | 1 | 640 |  |
| PEPPERS | 1804 | 1 | 5 | 1 | 640 |  |
| GREENS | 1805 | 1 | 5 | 1 | 540 |  |
| FRUIT | 1806 | 2 | 10 | 1 | 720 |  |
| APPLES | 1807 | 2 | 10 | 1 | 640 | woody |
| BERRIES | 1808 | 2 | 15 | 1 | 720 | berry |
| ORANGES | 1809 | 2 | 10 | 1 | 640 | woody |
| LEMONS | 1810 | 2 | 10 | 1 | 480 | woody |
| GRAPES | 1811 | 3 | 5 | 1 | 680 |  |
| OLIVES | 1812 | 2 | 5 | 1 | 640 |  |
| POTATOES | 1813 | 1 | 5 | 1 | 770 |  |
| CACTUS | 1814 | 2 | 5 | 1 | 680 |  |
| DATES | 1815 | 2 | 2 | 1 | 720 |  |
| SEAWEED | 1816 | 1 | 50 | 1 | 540 |  |
| HOPS | 1866 | 2 | 20 | 1 | 340 |  |
| COFFEEBEANS | 1867 | 2 | 10 | 1 | 560 |  |
| COCOA | 1878 | 4 | 5 | 0 | 590 | woody |
| SUGAR | 1885 | 1 | 50 | 1 | 1600 |  |
| BARLEY | 1887 | 1 | 20 | 1 | 610 |  |
| MUSHROOMS | 1888 | 1 | 20 | 1 | 500 |  |
| HERBS | 1889 | 1 | 10 | 1 | 770 |  |
| VINE | 1890 | 1 | 10 | 1 | 880 |  |
| FLOWERS | 1891 | 1 | 10 | 1 | 720 |  |
| NUTS | 1897 | 0 | 20 | 0 | 640 | woody |
| BREAD | 1898 | 3 | 0 | 0 | 660 |  |
| CRACKER | 1899 | 2 | 0 | 0 | 200 |  |
| PIPEWEED | 1902 | 3 | 10 | 1 | 320 |  |
| STRAWBERRIES | 1904 | 10 | 1 | 1 | 750 | berry |
| BLUEBERRIES | 1905 | 10 | 1 | 1 | 750 | berry |
| RASPBERRIES | 1906 | 10 | 1 | 1 | 750 | berry |
| BOYSENBERRIES | 1907 | 10 | 1 | 1 | 750 | berry |
| BLACKBERRIES | 1908 | 10 | 1 | 1 | 750 | berry |
| SMURFBERRIES | 1909 | 10 | 1 | 1 | 750 | berry |
| PEACHES | 1910 | 10 | 1 | 1 | 700 | woody |
| PLUMS | 1911 | 10 | 1 | 1 | 710 | woody |
| ONIONS | 1912 | 10 | 1 | 1 | 760 |  |
| CHERRIES | 1913 | 10 | 1 | 1 | 810 | woody |
| GARLIC | 1914 | 10 | 1 | 1 | 815 |  |
| PINEAPPLES | 1915 | 10 | 1 | 1 | 500 |  |
| COCONUTS | 1916 | 10 | 1 | 2 | 250 | woody |
| BANANAS | 1917 | 10 | 1 | 1 | 790 | woody |
| LIMES | 1918 | 10 | 1 | 1 | 690 | woody |
| SOAP | 1949 | 1 | 0 | 1 | 430 |  |
| ASH | 1951 | 1 | 0 | 0 | 50 |  |
| CHEESE | 1954 | 25 | 0 | 1 | 640 |  |
| BEANS | 1955 | 1 | 15 | 1 | 750 |  |
| CRANBERRIES | 1956 | 10 | 1 | 1 | 750 | berry |
| SPICE | 1996 | 100 | 5 | 1 | 750 |  |
| FUNGUS | 1998 | 1 | 3 | 1 | 750 |  |
| VEGETABLE | 2001 | 1 | 5 | 1 | 540 |  |

#### FLESH (2048)

| Resource | Code | Value | Rarity | Hardness | Buoyancy | Gathered as |
| --- | --- | --- | --- | --- | --- | --- |
| MEAT | 2049 | 4 | 20 | 1 | 3000 |  |
| BEEF | 2050 | 6 | 20 | 1 | 3000 |  |
| PORK | 2051 | 8 | 20 | 1 | 2500 |  |
| POULTRY | 2052 | 3 | 20 | 1 | 2000 |  |
| MUTTON | 2053 | 4 | 20 | 1 | 2800 |  |
| FISH | 2054 | 5 | 100 | 1 | 590 | fish |
| EGGS | 2121 | 2 | 10 | 0 | 1120 |  |
| WAX | 2152 | 1 | 0 | 0 | 900 |  |
| SALMON | 2198 | 6 | 18 | 1 | 1000 | fish |
| CARP | 2199 | 6 | 1 | 10 | 1000 | fish |
| TROUT | 2200 | 6 | 10 | 1 | 1000 | fish |
| SHRIMP | 2201 | 6 | 15 | 1 | 1000 | fish |
| TUNA | 2202 | 6 | 10 | 1 | 1000 | fish |
| CATFISH | 2203 | 6 | 10 | 1 | 1000 | fish |
| DRAGONMEAT | 2214 | 40 | 20 | 1 | 3000 |  |

#### PAPER (2304)

| Resource | Code | Value | Rarity | Hardness | Buoyancy | Gathered as |
| --- | --- | --- | --- | --- | --- | --- |
| PAPER | 2368 | 10 | 0 | 0 | 920 |  |

#### ROCK (2560)

| Resource | Code | Value | Rarity | Hardness | Buoyancy | Gathered as |
| --- | --- | --- | --- | --- | --- | --- |
| STONE | 2585 | 1 | 80 | 5 | 2500 |  |
| LIMESTONE | 2586 | 1 | 20 | 4 | 1550 |  |
| FLINT | 2587 | 1 | 10 | 4 | 2600 |  |
| GRANITE | 2588 | 2 | 10 | 6 | 2690 |  |
| OBSIDIAN | 2589 | 10 | 5 | 6 | 2650 |  |
| MARBLE | 2590 | 20 | 5 | 5 | 2560 |  |
| SAND | 2591 | 1 | 50 | 1 | 1600 |  |
| BONE | 2648 | 1 | 100 | 5 | 1600 |  |
| COAL | 2649 | 1 | 50 | 1 | 1800 |  |
| EBONY | 2662 | 5 | 5 | 5 | 2910 |  |
| IVORY | 2663 | 5 | 5 | 3 | 1840 |  |
| DUST | 2669 | 0 | 20 | 0 | 1120 |  |
| BASALT | 2705 | 10 | 2 | 4 | 3300 |  |
| SHALE | 2706 | 5 | 2 | 2 | 1200 |  |
| PUMICE | 2707 | 5 | 2 | 4 | 600 |  |
| SANDSTONE | 2708 | 10 | 2 | 2 | 3500 |  |
| SOAPSTONE | 2709 | 60 | 2 | 5 | 3600 |  |
| ALABASTER | 2733 | 5 | 10 | 4 | 1550 |  |
| URANIUM | 2752 | 100 | 1 | 0 | 2000 |  |
| SILICON | 2758 | 10 | 10 | 6 | 500 |  |
| SALT | 2763 | 10 | 20 | 5 | 750 |  |
| DIRT | 2765 | 1 | 50 | 1 | 1600 |  |

#### LIQUID (2816)

| Resource | Code | Value | Rarity | Hardness | Buoyancy | Gathered as |
| --- | --- | --- | --- | --- | --- | --- |
| WATER | 2876 | 0 | 10 | 0 | 1000 |  |
| SALT WATER | 2877 | 0 | 100 | 0 | 1030 |  |
| LIQUID | 2878 | 0 | 1 | 0 | 1000 |  |
| MILK | 2888 | 2 | 10 | 0 | 1020 |  |
| COFFEE | 2892 | 0 | 10 | 0 | 430 |  |
| BLOOD | 2903 | 1 | 100 | 0 | 1025 |  |
| LAMP OIL | 2906 | 1 | 10 | 1 | 880 |  |
| POISON | 2907 | 1 | 1 | 1 | 1000 |  |
| LIQUOR | 2908 | 10 | 1 | 1 | 790 |  |
| HONEY | 2910 | 1 | 50 | 1 | 1600 |  |
| SAP | 2943 | 10 | 1 | 1 | 1600 | woody |
| PERFUME | 2976 | 1 | 1 | 1 | 1000 |  |
| DRAGONBLOOD | 2981 | 10 | 100 | 0 | 1025 |  |
| SLIME | 2996 | 5 | 10 | 1 | 750 |  |
| PETROLEUM | 3011 | 50 | 5 | 1 | 880 |  |
| GASOLINE | 3012 | 80 | 0 | 0 | 880 |  |
| DIESEL | 3013 | 70 | 0 | 0 | 880 |  |
| TAR | 3018 | 10 | 15 | 2 | 1280 |  |

#### PRECIOUS (3072)

| Resource | Code | Value | Rarity | Hardness | Buoyancy | Gathered as |
| --- | --- | --- | --- | --- | --- | --- |
| JADE | 3104 | 50 | 2 | 5 | 3800 |  |
| DIAMOND | 3139 | 500 | 1 | 9 | 3510 |  |
| GEM | 3141 | 100 | 1 | 5 | 3500 |  |
| PEARL | 3142 | 380 | 1 | 5 | 2000 |  |
| OPAL | 3149 | 80 | 2 | 5 | 2250 |  |
| TOPAZ | 3150 | 200 | 2 | 5 | 3570 |  |
| AMETHYST | 3151 | 300 | 2 | 5 | 2651 |  |
| GARNET | 3152 | 70 | 2 | 5 | 3870 |  |
| AMBER | 3153 | 80 | 5 | 5 | 2500 |  |
| AQUAMARINE | 3154 | 50 | 2 | 5 | 2800 |  |
| CHRYSOBERYL | 3155 | 50 | 2 | 5 | 2800 |  |
| ONYX | 3200 | 70 | 1 | 8 | 3300 |  |
| TURQUOISE | 3201 | 70 | 1 | 8 | 3300 |  |
| PERIDOT | 3202 | 65 | 1 | 6 | 3300 |  |
| QUARTZ | 3203 | 25 | 1 | 5 | 3300 |  |
| LAPIS | 3204 | 70 | 1 | 6 | 3300 |  |
| BLOODSTONE | 3205 | 85 | 1 | 8 | 3300 |  |
| MOONSTONE | 3206 | 90 | 1 | 8 | 3300 |  |
| ALEXANDRITE | 3207 | 95 | 1 | 9 | 3300 |  |
| RUBY | 3239 | 300 | 1 | 3 | 3500 |  |
| EMERALD | 3240 | 200 | 1 | 3 | 3500 |  |
| SAPPHIRE | 3241 | 180 | 1 | 3 | 3500 |  |
| AGATE | 3242 | 100 | 1 | 3 | 3500 |  |
| CITRINE | 3243 | 100 | 1 | 3 | 3500 |  |
| DIAMELS | 3266 | 5 | 20 | 9 | 3510 |  |
| DILITHIUM | 3273 | 5000 | 0 | 7 | 5010 |  |

#### ENERGY (3328)

| Resource | Code | Value | Rarity | Hardness | Buoyancy | Gathered as |
| --- | --- | --- | --- | --- | --- | --- |
| ENERGY | 3439 | 30 | 0 | 4 | 0 |  |
| PLASMA | 3505 | 100 | 0 | 0 | 0 |  |
| ELECTRICITY | 3506 | 100 | 0 | 0 | 0 |  |
| ANTIMATTER | 3521 | 500 | 0 | 0 | 0 |  |

#### SYNTHETIC (3584)

| Resource | Code | Value | Rarity | Hardness | Buoyancy | Gathered as |
| --- | --- | --- | --- | --- | --- | --- |
| PLASTIC | 3684 | 25 | 0 | 4 | 950 |  |
| RUBBER | 3685 | 25 | 0 | 1 | 1506 |  |
| DEUTERIUM | 3763 | 100 | 0 | 0 | 0 |  |
| FIBREGLASS | 3773 | 10 | 0 | 3 | 0 |  |

#### GAS (3840)

| Resource | Code | Value | Rarity | Hardness | Buoyancy | Gathered as |
| --- | --- | --- | --- | --- | --- | --- |
| AIR | 4021 | 0 | 100 | 0 | 0 |  |
| OXYGEN | 4022 | 10 | 100 | 0 | 0 |  |
| HYDROGEN | 4023 | 20 | 100 | 0 | 0 |  |
| FLOURINE | 4024 | 60 | 40 | 0 | 0 |  |
| NITROGEN | 4025 | 10 | 100 | 0 | 0 |  |
| CHLORINE | 4026 | 10 | 100 | 0 | 0 |  |

### Adding or changing resources

There are three ways to define your own resources; each new resource gets a
code after the built-in ones (210 and up; the table holds 256 in all).

1. **`world/materials.txt`** (preferred; CoffeeSUD). One resource per line,
   fields separated by tabs: `NAME, FAMILY, VALUE, FREQUENCY, HARDNESS,
   BUOYANCY, CATEGORY, SMELL, EFFECTS, DESCRIPTION, CODE`. A row named like a
   built-in resource changes it; others are added, at their `CODE` if given,
   the rest sorted by name. `AHELP WORLDMATERIALS` has the details and an
   example. It is read at start-up, before the ini entries.
2. **`coffeemud.ini`** `ADDMATERIAL_[NAME]` and `REPLACEMATERIAL_[NAME]`, each
   eight comma-separated fields: material family, smell, value, rarity
   weight (1-100, lower is rarer), hardness (0-6), buoyancy (0-30000),
   gathering category (`FISH`, `BERRY`, `WOODY` or empty) and an optional
   ability ID applied to the raw form when eaten:

   ```ini
   ADDMATERIAL_GRUG=flesh,strong meaty,3,15,1,2000,FISH,
   REPLACEMATERIAL_COTTON=cloth,,0,100,0,10,,
   ```

   A name already defined in `world/materials.txt` is ignored here.
3. **`world/resources.txt`** (`RESOURCEVARIANTS`). Named *variants* of an
   existing resource, such as a moonpetal herb, set as a sub-type; see
   `AHELP RESOURCEVARIANTS`. A variant is a `Rsc Sub-Type` on a `GenResource`
   (its stat code is `RSUBTYPE`), not a new resource number.

Crafting, gathering and trading of materials is in
[07-items-crafting.md](07-items-crafting.md).

## Enchantments and item properties

An item becomes magical in four ways, which combine.

**1. The Magical Ability number.** Any non-zero value makes a weapon or
armor magical (it gets the `magical` disposition and shows to *detect
magic*). On weapons it adds to damage (and 10 times to attack); on armor it
adds to the armor value by location. It also adds 10 gold per point to the
item's value. Wands and scrolls use it as extra caster level (up to +10 for
wands).

**2. Properties (effects).** Properties are abilities that sit on the item.
Add them in the editor's *Effects* line: enter the property's ID (`?P` lists
the properties that fit the item), then give its parameters when asked.
`-ID` removes one, `=ID` or its number edits one. The most used are below;
every one is documented, with its parameters, in
[23-properties-reference.md](23-properties-reference.md).

| Property | Effect on an item |
| --- | --- |
| [Prop_WearAdjuster](23-properties-reference.md#prop_wearadjuster) | While worn or wielded, adjusts stats (`str+2 dex-1 hitpoints+20`), with optional `LAYERED`, `MASK=` and `ALLSET=` |
| [Prop_HaveAdjuster](23-properties-reference.md#prop_haveadjuster) | The same, for the item held anywhere in inventory |
| [Prop_WearSpellCast](23-properties-reference.md#prop_wearspellcast) | While worn, the wearer is under a list of spells: `Spell_MageArmor;Spell_Shield` |
| [Prop_HaveSpellCast](23-properties-reference.md#prop_havespellcast) | The same while carried |
| [Prop_WearResister](23-properties-reference.md#prop_wearresister) | Resistances while worn (`fire 40% magic 20%`) |
| [Prop_HaveResister](23-properties-reference.md#prop_haveresister) | Resistances while carried |
| [Prop_WearEnabler](23-properties-reference.md#prop_wearenabler), [Prop_HaveEnabler](23-properties-reference.md#prop_haveenabler) | Grant abilities while worn or held |
| [Prop_WearZapper](23-properties-reference.md#prop_wearzapper), [Prop_HaveZapper](23-properties-reference.md#prop_havezapper) | Only some characters can wear or hold the item (mask) |
| [Prop_AddDamage](23-properties-reference.md#prop_adddamage) | Extra damage on a weapon |
| [Prop_Invokable](23-properties-reference.md#prop_invokable) | Powers used on demand, with charges that recharge |
| [Prop_Attunement](23-properties-reference.md#prop_attunement) | Powers that wake only after the item is attuned |
| [Prop_Cursed](23-properties-reference.md#prop_cursed) | A hidden curse that Remove Curse ends cleanly |
| [Prop_ItemSet](23-properties-reference.md#prop_itemset) | Tiered bonuses for wearing several pieces of a named set |
| [Prop_ItemBinder](23-properties-reference.md#prop_itembinder) | Binds the item to whoever first picks it up or equips it |
| [Prop_Undroppable](23-properties-reference.md#prop_undroppable) | The item cannot be dropped |
| [Prop_Durability](23-properties-reference.md#prop_durability) | Wear-and-tear rules for this item: `MULT=`, `UNBREAKABLE`, `BREAK=true`, `NOREPAIR` |
| [Prop_KeyRules](23-properties-reference.md#prop_keyrules), [Prop_LockRules](23-properties-reference.md#prop_lockrules) | Key and lock rules |
| [Prop_Transform](23-properties-reference.md#prop_transform) | The item changes form over time or on a condition |
| [Prop_ItemGrowth](23-properties-reference.md#prop_itemgrowth) | The item grows with use |
| [Prop_RejuvWhen](23-properties-reference.md#prop_rejuvwhen) | The item returns only when its mask allows (see [Item rejuvenation](#item-rejuvenation)) |
| [Prop_NoPurge](23-properties-reference.md#prop_nopurge) | The item survives `PURGE` |
| [Prop_QuestItem](23-properties-reference.md#prop_questitem) | Marks an item as a quest object |
| [Prop_ItemModifiers](23-properties-reference.md#prop_itemmodifiers) | Tracked, selective modifiers; see [17-item-modifiers-crafting.md](17-item-modifiers-crafting.md) |

Puzzle properties (`Prop_Switch`, `Prop_ComboLock`, `Prop_PressurePlate`,
`Prop_Detector`, `Prop_Discoverable`, receptacles and blockages) are in
[07-items-crafting.md](07-items-crafting.md).

**3. Behaviors and scripts.** Behaviors on an item run while it exists. A
MOBPROG script on the item reacts to events such as `GET_PROG`, `WEAR_PROG`,
`REMOVE_PROG` or `READ_PROG`; see [04-mobprog.md](04-mobprog.md). Add them in
the *Behaviors* line, and see [24-behaviors-and-masks.md](24-behaviors-and-masks.md).

**4. Generic classes that do the work.** A wand, staff, scroll, potion, pill
or powder holds spells in the item itself; a bag of holding changes weight; a
wand of nourishment is a specific class. See
[Magic items](#magic-items-wands-staffs-scrolls-potions-pills).

The same enchantments exist as in-game player abilities: the spells
`Spell_EnchantWeapon`, `Spell_EnchantArmor`, `Spell_EnchantWand`,
`Spell_MagicItem`, `Spell_Disenchant` and `Spell_IdentifyObject`, and the
prayers `Prayer_BlessItem`, `Prayer_CurseItem` and `Prayer_ProtectItem`;
wands and scrolls are made by the `WandMaking` and `ScrollScribing` skills.
See [25-abilities-and-effects.md](25-abilities-and-effects.md). Random magic
items and affixes generated at loot time are in
[12-generation.md](12-generation.md). Tracked modifiers that survive crafting
and saves (affixes with versions and revisions) are in
[17-item-modifiers-crafting.md](17-item-modifiers-crafting.md).

**Identification.** The *Secret Identity* text is what identify magic adds.
Leave it blank for most items. Wands, scrolls, potions and pills generate their
own "A wand of ..." line from their spells and put your secret identity after
it.

## Item rejuvenation

"Rejuvenation" is what brings an item back after it was removed. The **Rejuv**
field means different things depending on where the item is.

### Items in rooms: ticks

For an item lying in a room, **Rejuv** is the number of ticks between the item
going missing from the room and a fresh copy appearing. A tick is `TICKTIME`
milliseconds of `coffeemud.ini` (4000, four seconds, by default). `0` (or the
"never" value) means it never comes back: once picked up and carried out, it is
gone. Each top-level item with a positive value carries an `ItemRejuv` ability
that watches the item and its contents, and puts a copy of the original (with
contents) back in the room when it is missing.

Rejuvenation starts when the room's items are loaded or saved (`SAVE ROOM`
and `SAVE ITEMS` start it). After changing `REJUV` on a live item, save the
room in builder mode, or `RESET ROOM`, for it to take effect.

`RESET REJUV ROOM ITEMS` (or `AREA`) restores any resettable item that is
missing. `RESET ROOM` also returns items and mobs that do not belong.

A capped catalog item (see [Unique and rare items](#unique-and-rare-items))
waits instead of returning while the cap is reached. `Prop_RejuvWhen` can make
an item return only when a mask is true, or never, or be replaced by another
item (see its entry in [23-properties-reference.md](23-properties-reference.md#prop_rejuvwhen)).

### Items on a mob: percent chance

For an item in a mob's inventory (worn, held or carried), **Rejuv** is a
**percent chance that the item is left out** each time the mob is created or
reset. This makes "variable equipment".

| Value | Effect |
| --- | --- |
| 0 | Always present (the normal case) |
| 1-99 | That percent chance the item is missing when the mob is created or comes back |
| -1 | The item exists only when the mob is first created (at boot), never after a rejuvenation |

If several items with a chance occupy the same wear location, exactly one is
chosen at random, weighted by their values, and the rest are removed. Items
carried loose are rolled independently. Variable items are resolved when the
mob is placed or comes back; the roll is skipped while a builder (a player
allowed `CMDMOBS`) is in the room. Do not copy a variable item from a mob to a
room: the same number means ticks there. `RESET ROOM` before saving a room
whose mobs have variable equipment, or the saved copies lose the items that
were rolled away.

The editor prompt names the two meanings: `Rejuv/Pct: ... (0=normal, -1=one
time)` for a mob's inventory, and `Rejuv/Pct: ... (0=never)` in rooms.

The command form `MODIFY ITEM x REJUV n` sets any positive `n`; any other
value sets "never rejuvenate". Use the full editor to enter -1.

Shopkeepers' stock follows the same rule for stocked items, and the mob's own
respawn time (its Rejuv Ticks) is in [21-mobs.md](21-mobs.md).

## The catalog

The catalog is a global set of named mob and item templates. Source:
[Commands/Catalog.java](../../CoffeeMud/com/planet_ink/coffee_mud/Commands/Catalog.java),
[Libraries/CMCatalog.java](../../CoffeeMud/com/planet_ink/coffee_mud/Libraries/CMCatalog.java).
Use it when the same item appears in many places, or when you want one change
to reach every copy, or when you want the engine to hand the item out by rule
(drops, room spawns, chests).

How it behaves:

- A catalog entry is keyed by the item's **name**. Do not rename a cataloged
  item; renaming detaches it from the catalog.
- `CREATE ITEM [catalog name]` (or `CREATE [catalog name]`) makes a copy tied to
  the entry: a copy carries the "cataloged" flag, and `MODIFY` tells you
  `*** This object is Cataloged **`.
- If you change a cataloged item, finishing the editor shows what differs
  (`CATALOG:` and `YOURS  :` lines per changed stat) and asks `Enter U to
  update the cataloged version, or D to detach this object from the catalog, or
  C to Cancel (u/d/C)?`. `U` rewrites the catalog entry, so every tied copy
  follows when it is next created; `D` makes this copy independent; `C` throws
  your change away.
- An object that cannot be saved to the database is refused ("can not be
  cataloged"), and an object's class must match the existing entry of that
  name.
- Scripts load catalog items with `MPOLOAD CATALOG [name]` (also
  `MPOLOADROOM`, `MPOLOADSHOP`, `CATALOG:[name]`); copies are counted and none
  is made past the cap unless `FORCE` is given ([27-mobprog-commands.md](27-mobprog-commands.md)).
  A crafting recipe's result may be `CATALOG:name`; see [07-items-crafting.md](07-items-crafting.md).
- Permission: the `CATALOG` security flag (`AHELP CATALOG`).

### CATALOG commands

Source help: `AHELP CATALOG`.

| Command | Does |
| --- | --- |
| `CATALOG [name]` | Catalogs the item or mob found in your room or inventory by that name (`SELF` or `ME` for yourself). A new entry goes into your current category. If an entry of that name exists, you are shown the differences and asked to confirm updating it |
| `CATALOG LIST` | Lists the catalog: mobs, then items. Columns are Name, the number of copies in the game (`#`), the auto-spawn kind (first letter, or `N/A`), the rate and the mask |
| `CATALOG LIST MOBS [mask]`, `CATALOG LIST ITEMS [mask]` | Lists one kind, filtered by name |
| `CATALOG LIST CATEGORIES` | Lists the category names |
| `CATALOG CATEGORY [name]` | Sets the category your next additions go into. A new name asks for confirmation. `NONE` returns to the global category; `LIST` lists them. (`GLOBAL`, `NONE` and `UNCATEGORIZED` cannot be created) |
| `CATALOG DELETE [ITEMS or MOBS] [name]` | Deletes an entry after a confirmation (a delete of more than 10 entries at once is not confirmed). Copies already placed are detached, not destroyed. `everydamnitem`, `everydamnmob` and `everydamnthing` delete in bulk |
| `CATALOG EDIT [ITEMS or MOBS] [name]` | Edits spawn rules, cap, uniqueness and rules; see below |
| `CATALOG ROOM\|AREA\|WORLD [MOBS\|ITEMS]` | Catalogs every mob or item there. It resets and saves the rooms, so it asks first: once for `ROOM`, twice for `AREA` or `WORLD` |
| `CATALOG SCAN\|DBSCAN ROOM\|AREA\|WORLD [MOBS\|ITEMS]` | Reports cataloged objects, and which are falsely cataloged (tied to a missing entry). `DBSCAN` checks the saved rooms |
| `CATALOG OVERLOOK\|DBOVERLOOK ROOM\|AREA\|WORLD [MOBS\|ITEMS]` | Reports objects that are not cataloged, or that should be tied to the catalog |
| `CATALOG CLEAN ROOM\|AREA\|WORLD [MOBS\|ITEMS]` | Detaches objects tied to entries that no longer exist, and saves (the code accepts this subcommand; `AHELP CATALOG` does not list it) |

`MOBS` and `ITEMS` may be shortened (`M`, `IT`). When omitted, both kinds are used.

Coins are never cataloged.

### CATALOG EDIT: spawn rules for items

`CATALOG EDIT [item]` asks, in order:

1. It prints `Now:` and the item's uniqueness and rules (see the next section).
2. `Auto-Spawn: N)o, L)ive mobs, D)ead mobs, R)oom, C)ontainer`. The item's own
   rule. The kinds:

   | Kind | Letter | When the item appears |
   | --- | --- | --- |
   | NONE | N | Never, by this rule (the item is only a template) |
   | LIVE | L | In the inventory of a mob that matches the mask when it comes to life |
   | DROP | D | On the corpse of a mob that matches the mask when it dies |
   | ROOM | R | In a random room matching the mask, on the catalog's timer |
   | CONTAINER | C | In a container item matching the mask; see below |

3. If the kind is not N: `Enter a new Max number in world` (the **cap**: a
   positive number), and `Enter a new Drop Rate` (a percentage above 0, such as
   `10%`). If the kind is N, only the cap is asked (0 for no cap).
4. For CONTAINER: `Roll on O)pening, T)imer, or B)oth`, then the container mask.
   For ROOM: a room mask. For LIVE or DROP: a mob mask. `?` at a mask prompt
   prints the zapper mask help; `NULL` or empty clears the mask (for a
   container rule, no mask means any container).
5. If the cap is above 0: `Unique: T)at a time, O)nce ever, W)once for a while`.
   For W: `Game days before a lost one may appear again`.
6. The extra rules: each existing rule is offered for change (`Rule 1 (text),
   or DELETE:`; Enter keeps it), then `Add a rule, or Enter for none:` repeats
   (up to 20) for new rules.

Then the entry is saved. The mask is a zapper mask (`-NAMES`, `-SUBNAMES`,
`-AREA`...); see [24-behaviors-and-masks.md](24-behaviors-and-masks.md). The
**cap** is the most copies in the world; the **rate** is the chance on each
chance to appear; for ROOM, CONTAINER-on-timer and mob spawns the "chance"
comes each time the catalog's housekeeping runs, which happens about every 60
minutes (`MudHost.TIME_SAVETHREAD_SLEEP`) and can be turned off with
`CATALOGTHREAD` in `DISABLE`.

Mobs in the catalog take only N and R spawning (`CATALOG EDIT MOBS`).

## Unique and rare items

CoffeeSUD item 1 (theme I; fixture `tools/fixtures/fixes/item1-uniques.js`)
makes catalog items unique or rare. `AHELP UNIQUES` is the shipped summary;
source: [core/Uniques.java](../../CoffeeMud/com/planet_ink/coffee_mud/core/Uniques.java).

### Uniqueness kinds

A catalog item has a **cap** (the most copies in the whole game, all rules
together) and one of three kinds of uniqueness:

| Kind | Editor letter | Rule |
| --- | --- | --- |
| TIME (at a time) | T | At most `cap` exist at once. One lost, destroyed or picked off can appear again at once. The default and the old behaviour |
| ONCE (once ever) | O | Once `cap` copies have appeared, no more ever appear, even if they are all destroyed |
| WHILE (once for a while) | W | As ONCE, but a lost copy may appear again after the entry's number of game days (`DAYS`) |

Every copy counts as it comes into the game: made by a rule, a script
(`MPOLOAD CATALOG`), a generator or a quest, loaded with the world or a save,
or carried in by a character. A mob that comes back to life with a capped item,
or an item put back in its room (rejuvenation), brings none past the cap: it is
held back. `CATALOG LIST` shows the number in the game, and `CATALOG EDIT`
shows "Once ever: 1 may appear in all; 0 have, 0 now" and for WHILE how many
game days until the next may appear. A save keeps how many have ever appeared
and when each missing copy was lost, so a restored game does not reroll.

### Rules

An entry has its own rule (the *Auto-Spawn* answer) and any number of **extra
rules**, all sharing the same cap and uniqueness. An extra rule is one line:

```text
KIND RATE [WHEN] [MASK]
```

| Part | Values |
| --- | --- |
| KIND | `LIVE`, `DROP`, `ROOM`, `CONTAINER`; a unique prefix is enough (`CONT`, `DR`) |
| RATE | A percentage: `5%`, `100%`. (A bare number is read as a percent as well; write the `%`) |
| WHEN | Only for CONTAINER: `OPEN` (default), `TIMER` or `BOTH`, spelled out in a rule line |
| MASK | A zapper mask. LIVE/DROP: tested on the mob. ROOM: tested on the room. CONTAINER: tested on the container item. Empty means any |

How each kind is evaluated:

- **LIVE** and **DROP**: when a mob comes to life, or dies, each rule of that
  kind rolls its rate; the first one that hits for a mob matching its mask
  gives a copy, if the cap and uniqueness allow it, in the mob's inventory or
  on its corpse.
- **ROOM**: on the catalog's timer, into a random room that matches the mask.
- **CONTAINER** with `OPEN`: the first time a player's character opens a
  matching container (or looks into it while open), it rolls. A rolled
  container stays rolled until its contents are put back (its room is reset or
  its items come back).
- **CONTAINER** with `TIMER`: like ROOM, but into a random matching container
  lying in a room, to be found. `BOTH` does both.

Examples of extra rules:

```text
CONTAINER 5% OPEN -SUBNAMES +*chest*
DROP 100% -NAMES "+the goblin chieftain"
ROOM 1% -AREA "+New Area"
CONTAINER 50% TIMER -NAMES "+an iron chest"
```

Quote a name of several words in a mask with its `+` inside the quotes, as
`-NAMES "+an iron chest"`.

### Making a unique item, step by step

1. Create and finish the item (for example a `GenWeapon` named
   `the Sunblade`).
2. Catalog it: `CATALOG the Sunblade`.
3. `CATALOG EDIT the Sunblade`.
4. Auto-Spawn `C` (container), cap `1`, rate `100%`, roll on `O`, mask
   `-NAMES "+an iron chest"`.
5. Unique: `O` (once ever).
6. No extra rules (press Enter), or add `DROP 100% -NAMES "+the goblin
   chieftain"`.
7. Place the iron chest in a room (`CREATE ITEM GenContainer`, name it
   `an iron chest`, give it a lid) and save. The first character to open an iron
   chest gets the Sunblade, once; after that no iron chest ever holds it.

### In the world file

A catalog entry in `world/catalog.cmare` carries its rules in its
`CATALOGDATA`. Editing by hand is supported. Tags:

| Tag | Meaning |
| --- | --- |
| `RATE` | The entry's own rule rate (`100%`) |
| `LMASK` | Its mask |
| `SPAWN` | `NONE`, `LIVE`, `DROP`, `ROOM` or `CONTAINER` |
| `CAP` | The cap |
| `UNIQUE` | `ONCE` or `WHILE` (absent means TIME) |
| `DAYS` | Game days, for WHILE |
| `WHEN` | `TIMER` or `BOTH` for the entry's own CONTAINER rule (absent means OPEN) |
| `RULES` | A list of `<RULE>` lines, each as written above |

```xml
<CATALOGDATA NAME="the Sunblade" CATAGORY="">
 <RATE>100%</RATE>
 <LMASK>-NAMES "+an iron chest"</LMASK>
 <SPAWN>CONTAINER</SPAWN>
 <CAP>1</CAP>
 <UNIQUE>ONCE</UNIQUE>
 <RULES><RULE>DROP 100% -NAMES +chieftain</RULE></RULES>
</CATALOGDATA>
```

(`CATAGORY` is spelled that way in the file.) A full example is
`tools/fixtures/smoke/catalog.cmare`. `WORLD RELOAD CATALOG` reloads a changed
file into a running builder game.

## How items are saved

Where an item lives decides where it is saved.

| The item is... | Saved in | Written when |
| --- | --- | --- |
| In a room, or in a container in a room | The room's `<ROOMITEMS>` in `world/areas/NAME.cmare` | Builder mode `SAVE`, `SAVE ROOM`, `SAVE ITEMS`, `SAVE CHANGED`, and at shutdown |
| In a mob's inventory, worn, or in its pack | The mob's `<ROOMMOBS>` entry with the mob (worn code `IWORN`, container link `ILOC`) | `SAVE`, `SAVE ROOM`/`SAVE MOBS` |
| In a shopkeeper's stock | The shopkeeper's entry, with count and price | With the shopkeeper |
| In the catalog | `world/catalog.cmare` | `SAVE`; `CATALOG EDIT` also updates the cached catalog at once |
| In space (planets, ships...) | `world/space.cmare` | `SAVE` |
| In a normal game | The campaign save (slots), not the world files | `SAVE slot`; see [31-saves-and-party.md](31-saves-and-party.md) |

In a normal game, items you create or change stay in that game's saves but are
never written back to the world; builder mode (`BUILDER=1`) writes them to the
world files. A world file edited outside the game is protected from being
overwritten (`WORLD STATUS`, `SAVE FORCE`); see
[02-world-workflow.md](02-world-workflow.md).

### Item XML

Each room item is stored as an `<RITEM>` element. Identical entries are
collapsed to one with a `COUNT` attribute when a room has more than 20 items.

```xml
<RITEM>
 <ICLAS>GenWeapon</ICLAS>
 <ILOCA />
 <IREJV>0</IREJV>
 <IUSES>100</IUSES>
 <ILEVL>5</ILEVL>
 <IABLE>0</IABLE>
 <ITEXT xml="1">...the item's own text...</ITEXT>
</RITEM>
```

| Tag | Meaning |
| --- | --- |
| `ICLAS` | Class ID |
| `IIDEN` | Present on containers and rideables with riders: an identity string other items point to |
| `ILOCA` | Empty, or the `IIDEN` of the container this item is inside |
| `IRIDE` | The rideable this item is riding (if it is in the room too) |
| `IREJV` | Rejuv |
| `IUSES` | Uses remaining / condition |
| `ILEVL` | Level |
| `IABLE` | Magical ability |
| `ITEXT` | The item's text: for generic items, the tags below; for standard items, its misc text |

The `ITEXT` of a generic item holds (from
[CoffeeMaker.java](../../CoffeeMud/com/planet_ink/coffee_mud/Libraries/CoffeeMaker.java)):

| Tag | Meaning |
| --- | --- |
| `NAME`, `DESC`, `DISP` | Name, description, display text |
| `PROP` | Base stats: `ABILITY`, `ARMOR`, `ATTACK`, `DAMAGE`, `DISPOSITION`, `LEVEL`, `REJUV`, `SPEED`, `WEIGHT`, `HEIGHT`, `SENSES` as attributes (older files use `\|`-separated numbers) |
| `IMG` | MXP image |
| `BEHAVES`, `AFFECS`, `SCRPTS`, `TAGS` | Behaviors (`BHAVE`: `BCLASS`, `BPARMS`), effects (`AFF`: `ACLASS`, `ATEXT`), scripts, tags |
| `FLAG` | Bits: 1 droppable, 2 gettable, 4 readable, 8 removable; for lidded things also 16 (new style), 32 lid, 64 lock, 128 defaults closed, 256 defaults locked |
| `IDENT` | Secret identity |
| `VALUE` | Base value |
| `MTRAL` / `MTRALN` | Material number, and its name for world-defined materials |
| `WORNL` | Wear locations are "all" (`true`) or "any one" |
| `WORNB` | Wear location bitmap |
| `READ` | Readable text |
| `CAPA`, `CONT`, `OPENTK` | Containers: capacity, content types, reset ticks |
| class-specific tags | One short tag per class field: for weapons `TYPE`, `CLASS`, `MINR`, `MAXR` and `ACAPA`; for containers `CAPA`, `CONT` and `OPENTK`; and so on. They differ from the stat code names, so export a sample to see them |

Do not hand-write `ITEXT` for a new item unless you copy an exported one. Let
the editor create it, `SAVE`, then edit the exported text if you must; a file
changed outside is guarded by `WORLD STATUS`. For bulk work, build items from
a script instead ([Worked examples](#worked-examples)) or export and merge
(`EXPORT`/`MERGE`/`IMPORT`, `AHELP MERGE`).

## Worked examples

These use builder mode (`BUILDER=1`) so that `SAVE` publishes the result.
Commands are entered one per line; after `CREATE ITEM` of a generic class the
editor opens, and pressing Enter at `Edit which?` leaves it.

### Example 1: a named, magical sword

```text
CREATE ITEM GenWeapon
```

Leave the editor with Enter, then set the fields with `MODIFY ITEM`. The new
item is still called "a generic weapon", so the first command finds it as
`generic`:

```text
MODIFY ITEM generic NAME the Emberfang
MODIFY ITEM emberfang DISPLAY the Emberfang lies here, still warm.
MODIFY ITEM emberfang DESCRIPTION A slim blade of dark steel that glows faintly red along its edge.
MODIFY ITEM emberfang LEVEL 12
MODIFY ITEM emberfang MATERIAL STEEL
MODIFY ITEM emberfang WEAPONTYPE SLASHING
MODIFY ITEM emberfang WEAPONCLASS SWORD
MODIFY ITEM emberfang ABILITY 2
MODIFY ITEM emberfang DISPOSITION ISGLOWING ISBONUS
```

Notes: the command-line `LEVEL` only sets the number. If you want the engine's
balanced damage, attack and value for the level, change the Level inside the
editor (`MODIFY ITEM emberfang`, pick the Level line) instead; it re-balances
on the spot. The `ABILITY` of 2 adds 2 to its damage and 20 to its attack.
Check the result with `STAT item emberfang`. To make it fire-flavoured, add a
property in the editor: `MODIFY ITEM emberfang`, pick the Effects line, enter
`Prop_AddDamage`, and at `Enter any effect parameters (?)` give `5 BURNING FIRE`
(type `?` there for the property's help). Save with `SAVE ROOM`.

### Example 2: a locked chest and its key

```text
CREATE ITEM GenContainer
```

In the editor set the name (`an iron-bound chest`), the capacity (`200`) and
then the lid/lock line. Answer: Has a lid `Y`, Defaults closed `Y`, Has a lock
`Y`, Defaults locked `Y`, reset delay `30`. Enter a **Key Code**, such as
`emberchest`. Leave the editor, then make the key:

```text
CREATE ITEM GenKey
MODIFY ITEM key NAME a smoky iron key
MODIFY ITEM smoky READABLETEXT emberchest
```

(`GenKey`'s readable text is the key code. You may also give the code a list,
`emberchest;vault`, or end it with `*` for a master key, `ember.*`, which fits
every lock whose code begins `ember.`) Then:

Put the key in the chest's room, or into a mob's pack with
`CREATE ITEM GenKey@a guard` when you create it. Then save:

```text
SAVE ROOM
```

Gotcha: setting the lid and lock with the stat codes `HASLID`, `HASLOCK`,
`DEFCLOSED` and `DEFLOCKED` changes only the *defaults*; the chest keeps its
current open/unlocked state until it next resets. The editor sets both, so use
the editor for locks.

To use up the key when it opens the chest, add `Prop_KeyRules` with
`CONSUME=true` to the key (see [Prop_KeyRules](23-properties-reference.md#prop_keyrules)).

### Example 3: a magic ring

A ring is thin armor (it has no size or wear and tear).

```text
CREATE ITEM GenThinArmor
```

In the editor: set the name `a copper ring of vigor`, display and description;
**Wear locations**: at the `Select an option number above to TOGGLE` prompt
type `left finger`, then `right finger` (each toggles), then `0` to finish
(leave the setting on "any ONE of these locations" so it fits either hand);
**Material** `COPPER`; **Layer**: `0`, not see-through, multi-wear `Y` (so a
character can wear two rings); **Armor** `0`. Then, at the **Effects** line,
enter the property ID, and give its parameters at the next prompt:

```text
Prop_WearAdjuster
str+1 hitpoints+10
```

Add a spell cast on the wearer with a second effect, `Prop_WearSpellCast`
with the parameters `Spell_Shield`. To make the ring show to detect-magic,
toggle the `magical` disposition. `?P` at the Effects prompt lists every
property that fits an item.

### Example 4: a wand

```text
CREATE ITEM GenWand
MODIFY ITEM wand NAME a rowan wand
MODIFY ITEM rowan SPELL Spell_MagicMissile
MODIFY ITEM rowan ENCHTYPE SPELL
MODIFY ITEM rowan MAXUSES 8
MODIFY ITEM rowan USES 8
MODIFY ITEM rowan LEVEL 10
```

A character who holds the wand and has `Skill_WandUse` says its magic word to
the target (`SAY orc "word"`). The word is not set by you: it comes from the
spell through the `MAGIC_WORDS` list in `resources/lists.ini`, and identify
magic (`Spell_IdentifyObject`) tells the holder "Say the magic word".

### Example 5: a book of lore

A static, paged book whose pages are in the world files:

```text
CREATE ITEM GenTome
MODIFY ITEM tome NAME the Annals of Ost
MODIFY ITEM annals
```

then, in the editor, pick the **Assigned Read Text** line and enter the pages.
(Use the editor or a script for this text; on the one-line command form the
quote marks in `TITLE="..."` can be lost to the command parser.)

```text
<PAGE TITLE="The Founding">Ost was founded by the river.</PAGE><PAGE TITLE="The Fall" LORE=ost.fall>The tower fell in the seventh year.</PAGE>
```

`READ annals` lists the contents, `READ 2 annals` reads page 2 (and gives the
reader the `LORE:ost.fall` tattoo), and `TEAR PAGE 2 FROM annals` makes a
single page item. For longer text, put `FILE=world/texts/ost.txt` in the
readable text, and in that file start each page with a line
`=== The Founding ===`.

### Example 6: a unique treasure, found once

Follow [Making a unique item, step by step](#making-a-unique-item-step-by-step).
The result is a catalog entry with `CAP 1`, `UNIQUE ONCE` and a container rule;
the world file holds it in `catalog.cmare`.

### Example 7: build items from a script

For many items, or to repeat a build, a JavaScript builder recipe is easier to
keep in version control than a sequence of editor sessions. This one is adapted
from the shipped Ember Trial recipe
([build.js](examples/ember-trial/world/resources/creator/build.js)); run it with
`JRUN /resources/creator/build.js` in a builder game, then `SAVE`.

```js
var CM = Packages.com.planet_ink.coffee_mud;
var CC = CM.core.CMClass, W = CM.Items.interfaces.Wearable;
var room = mob().location();

function mantle(name, slot, adjust) {
    var item = CC.getItem('GenArmor');
    item.setName(name);
    item.setDisplayText(name + ' lies on a rack.');
    item.setDescription('A hand-made piece of armor.');
    item.setRawProperLocationBitmap(slot);          // where it is worn
    item.setMaterial(CM.Items.interfaces.RawMaterial.RESOURCE_COTTON);
    item.basePhyStats().setArmor(0);
    item.basePhyStats().setWeight(1);
    var prop = CC.getAbility('Prop_WearAdjuster');   // a property on the item
    prop.setMiscText(adjust);
    item.addNonUninvokableEffect(prop);
    item.recoverPhyStats();                          // always, after changes
    room.addItem(item);
    return item;
}
mantle('an ember mantle', W.WORN_ABOUT_BODY, 'str+1 hitpoints+20');
mantle('a signet ring', W.WORN_RIGHT_FINGER, 'dex+1');
```

The calls mirror the editor: `setName`, `setDisplayText`, `setDescription`,
`setRawProperLocationBitmap`, `setMaterial`, `basePhyStats().setArmor/Damage/
Level/Weight/Ability`, `setBaseValue`, `setUsesRemaining`, `setReadableText`,
`setCapacity`, `setDoorsNLocks(hasLid, open, defaultsClosed, hasLock, locked,
defaultsLocked)`, `addNonUninvokableEffect`, `addBehavior`. See
[05-javascript.md](05-javascript.md) and
[32-engine-programming.md](32-engine-programming.md) for the object model.

### Example 8: an item the engine hands out

Create `a goblin chieftain's torque` as a `GenThinArmor` with
`Prop_WearAdjuster` `cha+1`, catalog it (`CATALOG a goblin chieftain's
torque`), then `CATALOG EDIT` it: Auto-Spawn `D` (dead mobs), cap `3`, rate
`25%`, mask `-NAMES "+the goblin chieftain"`, uniqueness `T`. Each time that
chieftain dies, there is a one in four chance a copy is on his corpse, and never
more than three in the game.

## Troubleshooting

| Problem | Cause and fix |
| --- | --- |
| "You can't wear that" for a new `GenArmor` | A new armor has no wear location. Set the location in the editor (and toggle any/all) |
| "looks too advanced for you" | The item's Level is above the character's. Lower it, or the character gains levels |
| A piece of armor "doesn't fit -- it's too big/small" | Armor size is checked against height. Set **Size** to 0 to fit anyone, or disable `EQUIPSIZE` |
| A weapon does less damage than set | Condition is below 100 (wear and tear) or Level was re-balanced. Check `USES`, and note that changing Level in the editor rewrites damage |
| Damage changes when you change Level | By design: the editor re-balances weapons and armor to the engine's norms for the level. Change Level first, then the rest |
| A scroll "has been read off" or reads blank | A new scroll has 0 uses. Set Uses Remaining to 1 or more |
| A wand "glows faintly then fades" | The character has no wand skill of the right kind, or failed its proficiency check |
| An ammunition weapon says "is out of arrows" | The weapon needs a `GenAmmunition` whose readable text equals the weapon's ammo type, and reloading |
| The chest is open at the start though "defaults closed" | The default and the current state differ. Use the editor's lid/lock prompts, which set both |
| A key does not fit | The key's code must equal the lock name (case-insensitive), be in a list with it, or be a `*` master key. Check both `READABLETEXT` values |
| A removed room item does not return | Rejuv is 0, or the room has not been saved since you set Rejuv. `SAVE ROOM`, or `RESET ROOM` |
| A mob's loot is missing | Its Rejuv is a percent chance to be left out. Set it to 0 to always carry it |
| `CREATE ITEM x` says "no such thing" | The class does not exist or the name is a typo. `LIST ITEMS`; for a catalog item, `CATALOG LIST ITEMS` |
| `MODIFY ITEM long name` fails | The item name is one parameter. Quote it: `MODIFY ITEM "iron sword@the guard"` |
| Changing a cataloged item asks to update, detach or cancel | It is tied to the catalog. `U` updates the entry for everyone, `D` detaches this copy |
| A catalog item does not spawn | The cap is reached, it is `ONCE` and has already appeared, the mask does not match, or the catalog thread is disabled (`CATALOGTHREAD`). `CATALOG EDIT` shows how many have appeared and how many are in the game |
| Edited the item in a normal game and the world did not change | A normal game works on a copy. Use `BUILDER=1` and `SAVE` |
| `MODIFY ITEM x DISPOSITION` removed other dispositions | The command replaces the whole set; use the editor to toggle one |
| A hand-edited item in a world file is skipped | World files are checked at load (`WORLD PROBLEMS` reports skipped definitions) and guarded against overwriting (`WORLD STATUS`). Export from the editor and edit that copy |

## See also

- [07-items-crafting.md](07-items-crafting.md): crafting, gathering, recipes, interactive items and puzzles.
- [23-properties-reference.md](23-properties-reference.md): every property you can put on an item.
- [24-behaviors-and-masks.md](24-behaviors-and-masks.md): behaviors and the zapper mask language used by catalog rules.
- [12-generation.md](12-generation.md): random items, loot tables and affixes.
- [17-item-modifiers-crafting.md](17-item-modifiers-crafting.md): tracked item modifiers.
- [21-mobs.md](21-mobs.md): putting items on mobs, shopkeepers' stock, equipment.
- [20-areas-rooms-exits.md](20-areas-rooms-exits.md): doors, locks and keys on exits.
- [30-travel-vehicles-instances.md](30-travel-vehicles-instances.md): boardables, ships and vehicles.
- [09-society.md](09-society.md): currencies, shops, clan items, the law.
- [02-world-workflow.md](02-world-workflow.md): world files, `SAVE` and reload.
- [31-saves-and-party.md](31-saves-and-party.md): what a campaign save keeps of items.
- [11-presentation-language.md](11-presentation-language.md): descriptions, VARIES, readable text and lore.
- [32-engine-programming.md](32-engine-programming.md): writing a new item class in Java or JS.
- [tool-reference.md](tool-reference.md): the generated command help (`CREATE`, `MODIFY`, `CATALOG`, `COPY`, `RESET`, `LIST`).
