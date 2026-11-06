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

Short forms also work: `CREATE GenWeapon` (without `ITEM`) is rerouted to
`CREATE ITEM GenWeapon` when the word is a template or the other CREATE words
do not match.

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

