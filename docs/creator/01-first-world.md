# Your first world

[Handbook](README.md) · Next: [World files and editing](02-world-workflow.md)

This is the beginner tutorial. It takes you from a freshly built CoffeeSUD
([installing and running](18-installing-and-running.md)) to a small, complete,
tested and saved world of your own: a separate world folder, an area of five
rooms, a locked door and its key, a shopkeeper and a guard, a weapon, armor, a
chest and a hammer, a catalog entry, a greeting and a hint written in MOBPROG,
a small quest, a conditional description, saved world files, checks, a
scripted playtest, and a normal game that you save and restore. Every command
is shown exactly as you type it, with what you should see. The same room IDs
and names are used from start to finish, so you can follow it line by line.

It is for people who have never built in CoffeeSUD. You need to know what a MUD
is; you do not need to know any Java.

## In this chapter

- [What you will build](#what-you-will-build)
- [Ground rules for the tutorial](#ground-rules-for-the-tutorial)
- [Start the editor](#start-the-editor)
- [Make a small place](#make-a-small-place)
- [Add an object and a character](#add-an-object-and-a-character)
- [Give it one interaction](#give-it-one-interaction)
- [Save the world files](#save-the-world-files)
- [Add a quest](#add-a-quest)
- [Add a conditional description](#add-a-conditional-description)
- [Check, save, and test](#check-save-and-test)
- [Make a reliable opening](#make-a-reliable-opening)
- [Start a normal game, save and restore](#start-a-normal-game-save-and-restore)
- [Worked variations](#worked-variations)
- [Command summary](#command-summary)
- [Persistence notes](#persistence-notes)
- [Troubleshooting](#troubleshooting)
- [Next steps](#next-steps)
- [See also](#see-also)

## What you will build

A village called Brindlewick, in one area of five rooms. North is up:

```text
                 [#4 Harl's storeroom]
                          ||  (locked door)
                 [#1 Harl's smithy]
                          |
                 [#0 The Brindlewick green]----[#2 The village gate]
                          |
                 [#3 The well house]
```

The exits are:

| Room ID | Title | Locale class | Exits |
| --- | --- | --- | --- |
| `Brindlewick#0` | The Brindlewick green | `CityStreet` | north to the smithy, east to the gate, south to the well house |
| `Brindlewick#1` | Harl's smithy | `StoneRoom` | south to the green, north to the storeroom (locked door) |
| `Brindlewick#2` | The village gate | `CityStreet` | west to the green |
| `Brindlewick#3` | The well house | `WoodRoom` | north to the green |
| `Brindlewick#4` | Harl's storeroom | `StoneRoom` | south to the smithy (locked door) |

The cast and the props:

| Thing | Class | Where | Job |
| --- | --- | --- | --- |
| Harl the smith | `GenShopkeeper` | smithy | Sells the sword |
| the gate guard | `GenMob` with `Guard` and `Scriptable` | gate | Greets, gives a hint, gives the quest |
| a notched shortsword | `GenWeapon` | in Harl's stock | The weapon |
| a padded leather jerkin | `GenArmor` | storeroom | The armor |
| an oak chest | `GenContainer` | well house | The container |
| a brass storeroom key | `GenKey` (code `harl.store`) | inside the chest | Opens the storeroom |
| an old forge hammer | `GenItem` | storeroom | The quest object, and the catalog entry |

The story: Harl has lost his old forge hammer inside his own locked storeroom
and lost the key too. The guard knows the key is in the oak chest in the well
house, and will pay a reward if you bring him the hammer.

## Ground rules for the tutorial

Four ideas explain almost everything that follows. They are covered fully in
[installing and running](18-installing-and-running.md) and
[World files and editing](02-world-workflow.md).

- **The world is a folder of text files.** A new game starts from them. You
  edit them by building in the game (builder mode) or with a text editor.
- **Builder mode edits the live world and a cache of it.** Rooms, exits and
  areas are written to the cache as you change them. Mobs and items you
  `CREATE` are only in the live room until you `SAVE ROOM` (or `SAVE CHANGED`).
  The world files are written only when you `SAVE` or quit.
- **A normal game never writes the world files.** It plays on a copy and saves
  the campaign (the characters and the live state) into a slot.
- **Keep your world away from the shipped one.** You build in a separate
  folder that you name with `WORLDDIR`, with its own cache (`DBSERVICE`) and
  saves folder (`SAVEDIR`). Nothing in the repository changes.

| Launch mode | Edits affect | What `SAVE` means |
| --- | --- | --- |
| `BUILDER=1` | The world and its cache | Write the world files |
| Normal game | A working copy of the world | Save the campaign to a slot |
| `DEV=1` | A working copy, with developer tools | Save the campaign to a slot |
| `CHECK=1` | A scratch copy, no player | Nothing: it prints a report and exits |

Typing conventions: commands in `text` blocks are typed in the game and are not
case sensitive (this chapter writes the command words in capitals and the rest
as you would type it). Blocks marked `sh` are typed in a terminal at the
repository root. A line that starts with `(` in a `text` block is a note about
what you see or do, not something to type.

Two small rules for the commands in this chapter. In the one-line editing
commands (`MODIFY ROOM DESCRIPTION ...`, `MODIFY ITEM ...`) the text after the
field name is taken as typed: you do not need quotes, but a word that starts
with a double quote begins a quoted group, and a backslash is dropped, so keep
double quotes and backslashes out of the text. Apostrophes are fine.

## Start the editor

### Step 1: make a separate world folder

You need a JDK (version 9 or newer; the project is developed with 21) and a
built CoffeeSUD; `CoffeeMud/sud.sh` builds on the first launch, which takes
about a minute. Python 3 is needed later for the automated test.

Choose a folder for the deployment and copy the shipped starter world into it.
The shipped `world/` is the starting point: one area with two rooms, one quest
file and one test.

```sh
export BRIND="$HOME/brindlewick"
mkdir -p "$BRIND/db" "$BRIND/saves"
cp CoffeeMud/resources/fakedb/fakedb.schema "$BRIND/db/"
cp -r CoffeeMud/world "$BRIND/world"
ls "$BRIND" "$BRIND/world"
```

You should see `db`, `saves` and `world` in the first listing, and `areas`,
`quests` and `tests` in the second. Each of the three folders has a job:

| Folder | What it is | Who writes it |
| --- | --- | --- |
| `$BRIND/world` | The world files: this is what you are making and what you will version | The builder, and you |
| `$BRIND/db` | The cache: a database copy of the world. It needs `fakedb.schema` to start, and is rebuilt from the world files whenever they change | The engine |
| `$BRIND/saves` | Saved games and their working databases | The engine, for normal games |

The `fakedb.schema` copy is not optional: the database folder must contain it.
If you ever wreck the cache, delete everything in `db` except
`fakedb.schema`; the next start rebuilds it from the world files (builder
characters are kept in the cache, so they are forgotten with it).

For the rest of the tutorial, every launch uses the same three settings. To
save typing, make a small launcher script outside the repository:

```sh
cat > "$BRIND/launch.sh" <<'EOF'
#!/bin/sh
# Usage: launch.sh [extra NAME=VALUE settings]  (run from anywhere)
BRIND="$(cd "$(dirname "$0")" && pwd)"
REPO="${SUD_REPO:-$HOME/CoffeeSUD}"
exec "$REPO/CoffeeMud/sud.sh" \
  WORLDDIR="$BRIND/world" \
  DBSERVICE="jdbc:fakedb:$BRIND/db" \
  SAVEDIR="$BRIND/saves" "$@"
EOF
chmod +x "$BRIND/launch.sh"
export SUD_REPO="$PWD"
```

Run that from the repository root, so that `SUD_REPO` is the repository. (If
you prefer not to use a script, type the full line from the next step each
time.) Paths must be absolute because the launcher changes into `CoffeeMud/`
before it starts the game. On Windows use `CoffeeMud\sud.bat`, absolute paths
without spaces, and the same `NAME=VALUE` settings.

### Step 2: launch in builder mode and create your builder

```sh
"$BRIND/launch.sh" BUILDER=1
```

This is the same as:

```sh
CoffeeMud/sud.sh BUILDER=1 WORLDDIR="$BRIND/world" DBSERVICE="jdbc:fakedb:$BRIND/db" SAVEDIR="$BRIND/saves"
```

The first start builds the Java classes if needed and then prints
`Loading the world...`. Because the cache folder is empty, the engine builds it
from the world files (a line says that the world files are being loaded; this
is slower than later starts). With no saved games in builder mode you go
straight to character creation.

Builder mode has no accounts, only a local profile that holds your characters.
Create the character you will build with. The questions come in this order
(the menus are abridged here; type the answer on the right):

| The game asks | You type |
| --- | --- |
| the character's name | `Curator` |
| Create a new character called 'Curator' (y/N)? | `Y` |
| Do you want ANSI colors (Y/n)? | `N` (or `Y` if your terminal shows colour) |
| a race | `Human`, then `Y` to confirm |
| your gender | `M` |
| Would you like to re-roll (y/N)? | `N` |
| a class | `Apprentice`, then `Y` to confirm |
| an alignment | `good` |
| (a final screen) | press Enter |

(The exact wording differs a little with the world's creation settings. The
automated tools use this same sequence: `Curator`, `Y`, `N`, `Human`, `Y`,
`M`, `N`, `Apprentice`, `Y`, `good`, Enter.)

In builder mode your character is made an Archon when it first logs in: it
reads the Archon's manual that lies in the starter room, and you are told
`Builder mode makes you an Archon, with the in-game editors.` After that you
see a short summary: whether the world files loaded whole, how many files were
changed outside the game, how many rooms have unsaved mobs or items, the result
of the quest check, and a pointer to `AHELP BUILDING`. You are standing in the
starter room, `New Area#0`.

Read the one-page workflow, then look around:

```text
AHELP BUILDING
LOOK
WORLD
```

- `AHELP BUILDING` (alias `AHELP BUILDERMODE`) prints the builder's workflow.
  It is a good page to know; it states the first trap (below) in its first
  numbered point.
- `LOOK` shows `New Room`, its description (`Brand new database room! ...`)
  and `Obvious exits`. The starter area, `New Area`, has two rooms.
- `WORLD` lists the world settings that are in effect and where each came from.
  Nothing from `world.ini` yet: the shipped world has no `world.ini`.

`AHELP` is the builder's help (it covers every building command and every
behavior and property); `HELP` is the player's.

**The first trap, stated once.** A mob or item you `CREATE` is in the live room
only. It is lost when the room resets and is not in the world files until you
save that room's contents with `SAVE ROOM` or `SAVE CHANGED`. Rooms, exits,
areas and the text you change with `MODIFY ROOM` are saved as you make them.
`LIST UNSAVED` lists the rooms that have unsaved mobs or items at any time.

If anything goes wrong, `UNDO` puts back the rooms your last building command
changed (`UNDO LIST` shows the log), and `SAVE` keeps backups of the files it
writes over (`WORLD BACKUPS`). Both are covered in
[World files and editing](02-world-workflow.md).

## Make a small place

### Step 3: create the area

Rooms are made next to an existing room, and an area starts with one room.
`CREATE AREA` makes both and moves you in.

```text
CREATE AREA Brindlewick
```

The game asks for an area type:

```text
Enter an area type to create (default=StdArea):
```

Press Enter to accept `StdArea`, a plain area. You are told `The size of the
world just increased!` and `You are now at Brindlewick#0.`, and the new room is
shown. It is a plain `StdRoom` titled `StdRoom-Brindlewick#0` with no
description. Room IDs are the area name, `#`, and a number; numbers are given
out in the order rooms are made.

Check that the area exists:

```text
LOOK
LIST AREAS
```

`LIST AREAS` includes `Brindlewick` beside `New Area`.

### Step 4: make the other four rooms

`CREATE ROOM` takes a direction and a locale class, makes the room in the same
area, and links both ways with an open doorway. You stay where you are.

Make the smithy, the gate and the well house around the green:

```text
CREATE ROOM north StoneRoom
CREATE ROOM east CityStreet
CREATE ROOM south WoodRoom
```

Each command answers with a line about a block of earth falling from the sky.
Now walk into the smithy and make the storeroom behind it:

```text
NORTH
CREATE ROOM north StoneRoom
```

The five rooms now exist as `Brindlewick#0` (the green), `#1` (smithy), `#2`
(gate), `#3` (well house) and `#4` (storeroom), in the order you made them.
Confirm the IDs and the shape with the map:

```text
WORLD MAP AREA IDS
```

`WORLD MAP` draws the rooms around you (with `AREA` all the rooms of the area,
with `IDS` each one's ID). You should see the green in the middle, the smithy
above it with the storeroom above that, and the gate and the well house beside
the green. If an ID is not what this chapter says, use the ID that the map
shows wherever the chapter writes `Brindlewick#n`.

You can change a room's locale class later. The green was made by
`CREATE AREA` as a `StdRoom`; give it a street's feel now. Go back to it first:

```text
SOUTH
MODIFY ROOM CLASS CityStreet
```

### Step 5: give every room a title and a description

A room has a title (its display text) and a description. Both are set with
one-line commands, so you can paste them. You must be standing in the room.
The green first:

```text
MODIFY ROOM NAME The Brindlewick green
MODIFY ROOM DESCRIPTION Trodden grass lies between three low stone buildings. A smithy squats to the north, the village gate stands to the east and a well house with a sagging roof sits to the south.
```

`MODIFY ROOM NAME` answers `There is something different about this place...`
and `MODIFY ROOM DESCRIPTION` answers `The very nature of reality changes.`
`LOOK` shows the new title, the description and the exits.

The gate, then the well house, then the smithy and the storeroom:

```text
GOTO Brindlewick#2
MODIFY ROOM NAME The village gate
MODIFY ROOM DESCRIPTION The gate is a gap in a low palisade, with a trampled road running east into the hills. A guard post of piled sandbags stands beside the arch.
GOTO Brindlewick#3
MODIFY ROOM NAME The well house
MODIFY ROOM DESCRIPTION A squat well house keeps the rain off a mossy well. The beams creak whenever the wind blows. An oak chest stands against one wall.
GOTO Brindlewick#1
MODIFY ROOM NAME Harl's smithy
MODIFY ROOM DESCRIPTION Heat shimmers over the forge and the walls ring with old hammer blows. Racks of tongs and half-finished hinges line the walls. A narrow door in the north wall leads to the storeroom.
GOTO Brindlewick#4
MODIFY ROOM NAME Harl's storeroom
MODIFY ROOM DESCRIPTION Shelves of ingots, spare handles and rusting tools crowd the storeroom. The air smells of oil and cold iron.
```

`GOTO room-id` takes an Archon straight to a room. The descriptions are plain
text now; [a later step](#add-a-conditional-description) makes two of them
change with the time of day and the quest. A longer description is easier to
write in the multi-line editor: `EDIT DESC HERE` opens it for the room you are
in (type your lines, press Enter on an empty line, then `S` and `Y` to save).
The same editor is described under [Give it one interaction](#give-it-one-interaction).
The `MODIFY ROOM` menu (`MODIFY ROOM` with nothing after it) shows every field
of the room: [Areas, rooms and exits](20-areas-rooms-exits.md).

### Step 6: name the game and set the start room (world.ini)

`world.ini` holds the settings that make a world the game it is. In builder
mode `WORLD SET KEY=VALUE` writes it (creating the file if the world has none),
and the setting takes effect the next time the game starts. Only keys on the
[world settings](world-settings.md) list are accepted.

```text
WORLD SET GAMENAME=Brindlewick Tales
WORLD SET START=Brindlewick#0
WORLD
```

The first two each answer `KEY=value is set in world.ini.  It takes effect the
next time the game starts.` `WORLD` lists the settings in effect and where each
comes from. `GAMENAME` and `START` show as set in the world file; they take
effect after the restart that is coming. `START` is the room where a new
character begins, so a normal game of this world opens at the green.

The file you have written is `$BRIND/world/world.ini`:

```ini
# The settings that make this world the game it is.  See LIST WORLDINI
# for the keys a world may set, and coffeemud.ini for what each does.
GAMENAME=Brindlewick Tales
START=Brindlewick#0
```

`GAMENAME` is shown on the title screen (see
[Make a reliable opening](#make-a-reliable-opening)). `START` must be a real
room ID; `WORLD CHECK` reports a start room that does not exist as an error.

### Step 7: make the doors: a locked door between smithy and storeroom

The open doorways `CREATE ROOM` made are exits too. An exit is a separate
object in each room; the link (where it leads) and the exit (what you pass
through) are different things. Replace the doorway between the smithy and the
storeroom with a locked door. Stand in the smithy:

```text
GOTO Brindlewick#1
CREATE EXIT north LockedDoor
```

`CREATE EXIT [direction] [exit class]` replaces the exit in that direction; if
the room beyond links back with an exit, that exit is replaced too, by a copy.
You are told `Suddenly a portal opens up to the north.` Now say which key fits.
For a standard door class the key code is the exit's text, and it is set on one
side at a time:

```text
MODIFY EXIT north TEXT harl.store
GOTO Brindlewick#4
MODIFY EXIT south TEXT harl.store
```

Each answers that the exit shakes under the transforming power. Check both
sides:

```text
LOOK
GOTO Brindlewick#1
LOOK
```

In each room the exit list shows a closed, locked door. A door that you can
close and lock, the key code `harl.store`, and the key that matches it, are
the three pieces; the key comes in [the next section](#add-an-object-and-a-character).
A lock with an empty key name can never be opened, so do not skip the
`MODIFY EXIT` on either side. (`WORLD CHECK` reports a locked door whose key
exists nowhere in the world.)

Other ways to link rooms: `LINK [room ID] [direction]` points an exit at an
existing room, `UNLINK [direction]` removes the link, `WORLD PATH` and
`WORLD DIG` make chains of rooms quickly (see
[Worked variations](#worked-variations)), and `CREATE EXIT [direction]
GenDoor` followed by `MODIFY EXIT` gives you a door you can name, describe and
lock in the exit editor. All of these are in
[Areas, rooms and exits](20-areas-rooms-exits.md).

## Add an object and a character

Every item and mob editing command below follows one pattern. `CREATE ITEM
[class]` makes the thing in your room. Generic classes (names starting `Gen`)
open the full editor at once; with the default `EDITORTYPE=MENU` that editor
lists numbered fields and then asks:

```text
Edit which?
```

Press Enter to leave the editor. Then fill the fields with one-line commands:
`MODIFY ITEM [word in its name] [FIELD] [value]`, and for mobs `MODIFY MOB
[word in its name] [FIELD] [value]`. The word that finds the thing is one
word; a brand-new generic thing is found by the word `generic`, so rename it
before you create another. The fields used here:

| Field | On | Meaning |
| --- | --- | --- |
| `NAME`, `DISPLAY`, `DESCRIPTION` | items and mobs | Name; the line shown in a room; the text shown by `LOOK AT` |
| `LEVEL` | items and mobs | Level (for a mob, also refills its combat numbers) |
| `RACE` | mobs | A race ID such as `Human` |
| `DAMAGE`, `WEAPONTYPE`, `WEAPONCLASS` | weapons | Damage; how it hurts (`SLASHING`); what kind (`SWORD`) |
| `ARMOR`, `PROPERWORN` | armor | Armor value; where it is worn (`TORSO`) |
| `HASLID`, `DEFCLOSED`, `CAPACITY` | containers | Has a lid; starts closed; how much it holds |
| `READABLETEXT` | keys | The key code |
| `BASEGOLD`, `WEIGHT` | items | Base value; weight |

`MODIFY ITEM x ?` (any unknown word) prints every field an item accepts, and
the same works for mobs. The full lists are in [Items](22-items.md) and
[Mobs](21-mobs.md).

### Step 8: the armor and the hammer (storeroom)

Go to the storeroom. Make and finish one item before the next, because both
start with the word `generic`.

```text
GOTO Brindlewick#4
CREATE ITEM GenArmor
```

(The editor lists the fields and asks `Edit which?`. Press Enter.) You see
`Suddenly, some generic armor drops from the sky.` Now describe it:

```text
MODIFY ITEM generic NAME a padded leather jerkin
MODIFY ITEM jerkin DISPLAY A padded leather jerkin lies folded on a shelf.
MODIFY ITEM jerkin DESCRIPTION Layers of quilted cloth over stiff leather. It smells of the forge.
MODIFY ITEM jerkin LEVEL 1
MODIFY ITEM jerkin PROPERWORN TORSO
MODIFY ITEM jerkin ARMOR 6
```

Each one answers that the item shakes under the transforming power. The default
`GenArmor` is leather and is worn nowhere until you give it a location; that is
what `PROPERWORN TORSO` is for. Now the hammer, a plain `GenItem`:

```text
CREATE ITEM GenItem
(Enter at "Edit which?")
MODIFY ITEM generic NAME an old forge hammer
MODIFY ITEM hammer DISPLAY An old forge hammer lies in the dust.
MODIFY ITEM hammer DESCRIPTION A heavy hammer with a scorched ash handle and a worn face. A letter H is burned into the haft.
MODIFY ITEM hammer WEIGHT 8
MODIFY ITEM hammer BASEGOLD 10
```

`LOOK` in the storeroom now shows both items. Use `LOOK AT hammer` to read its
description, and `STAT hammer` to see everything about it.

### Step 9: the catalog entry

The catalog is a world-wide list of finished mobs and items, by name. A copy
placed from the catalog stays tied to its entry, so one change can reach every
copy. The hammer is your first entry; the quest will refer to it by name. The
hammer is lying in the room, so name it:

```text
CATALOG hammer
CATALOG LIST ITEMS
```

The first answers that you catalog the old forge hammer. `CATALOG LIST ITEMS`
shows the entry `an old forge hammer` with a count of 1. From now on
`CREATE ITEM an old forge hammer` makes another copy, tied to the entry
without opening the editor. Do not rename a cataloged item: renaming detaches
it. The catalog is saved in `catalog.cmare`. Full details, including limits
and auto-spawning, are in [Items](22-items.md) and [Mobs](21-mobs.md).

### Step 10: the sword for Harl's shop

Go to the smithy. First make the sword, then the shopkeeper who sells it.

```text
GOTO Brindlewick#1
CREATE ITEM GenWeapon
(Enter at "Edit which?")
MODIFY ITEM generic NAME a notched shortsword
MODIFY ITEM notched DISPLAY A notched shortsword leans against the anvil.
MODIFY ITEM notched DESCRIPTION A plain blade with a nick near the point. It is balanced well enough.
MODIFY ITEM notched LEVEL 2
MODIFY ITEM notched DAMAGE 6
MODIFY ITEM notched WEAPONTYPE SLASHING
MODIFY ITEM notched WEAPONCLASS SWORD
MODIFY ITEM notched BASEGOLD 30
```

`WEAPONTYPE` and `WEAPONCLASS` take a name from the weapon lists. (`MODIFY ITEM
notched ?` prints all the fields; the type and class lists are in
[Items](22-items.md).) A `GenWeapon` is made of steel and can be wielded.

Do not call the word you search with `shortsword`: `Shortsword` is also the
name of a ready-made item class, and the shop's stocking prompt would pick the
class instead of your item. A word from your own name, `notched`, is safe.

### Step 11: Harl, the shopkeeper

```text
CREATE MOB GenShopkeeper
```

The mob editor opens: a numbered menu of the fields, then `Edit which?`.
Press Enter to leave it, then fill in the basics with one-line commands:

```text
MODIFY MOB generic NAME Harl the smith
MODIFY MOB harl DISPLAY Harl the smith pounds a glowing bar on his anvil.
MODIFY MOB harl DESCRIPTION A broad, soot-streaked man with forearms like fence posts. He has the look of someone who has lost something and will not say what.
MODIFY MOB harl RACE Human
MODIFY MOB harl LEVEL 10
```

A new generic mob is a blank "a generic shopkeeper"; always change at least
name, display text, description, race and level. `MODIFY MOB ... LEVEL` also
refills the mob's combat numbers for that level.

Now stock his shop. The shop's goods are a list in the mob's editor, and the
only way to stock a generic item is to name one that is in the room (or in
your inventory). Open the editor on Harl:

```text
MODIFY harl
```

(`MODIFY MOB harl` with no field is an error; `MODIFY` followed only by the
name opens the editor for a mob or an item.) Read the menu for the line that
starts `Inventory:`; on a stock build it is number 25 (the numbers shift if
your world adds factions). Type its number at `Edit which?`, then:

| The game asks | You type |
| --- | --- |
| `Edit which?` | the number of the `Inventory` line (25 on a stock build) |
| `Enter something to add/remove (?)` | `notched` |
| `How many? :` | `3` |
| `At what price? :` | `-1` |
| `Enter something to add/remove (?)` | press Enter (the game says `(no change)`) |
| `Edit which?` | press Enter |

You see `a notched shortsword added.` (it names the item's class ID) and, on
the menu, the stock as `Inventory: 'a notched shortsword (3)'`. A price of
`-1` lets the engine price it from the item's value; a number 0 or more sets a
fixed price. A shop defaults to the type "anything", so it deals in weapons.
Typing a name that is already in stock removes it.

The sword on the floor was only the model, so remove it:

```text
DESTROY ITEM notched
LOOK
```

Harl stands in the room, and the shop sells the sword. The shopkeeper chapter
of [Mobs](21-mobs.md) covers shop types, prices, budgets and the rest.

### Step 12: the oak chest and the key

Now the container and the key. Go to the well house. The chest comes first,
because the key is created inside it by name.

```text
GOTO Brindlewick#3
CREATE ITEM GenContainer
(Enter at "Edit which?")
MODIFY ITEM generic NAME an oak chest
MODIFY ITEM chest DISPLAY An oak chest, bound with iron, stands against the wall.
MODIFY ITEM chest DESCRIPTION A sturdy chest with a plain lid. The iron bands are rusting at the corners.
MODIFY ITEM chest HASLID true
MODIFY ITEM chest DEFCLOSED true
MODIFY ITEM chest CAPACITY 60
```

Set `HASLID` before `DEFCLOSED`: a closed-by-default chest needs a lid. Now
create the key inside the chest with `@`, which means "put it in":

```text
CREATE ITEM GenKey@chest
(Enter at "Edit which?")
MODIFY ITEM key@chest NAME a brass storeroom key
MODIFY ITEM key@chest DISPLAY A small brass key lies here.
MODIFY ITEM key@chest DESCRIPTION A small brass key on a loop of cord, with a tag that reads STORE.
MODIFY ITEM key@chest READABLETEXT harl.store
```

`CREATE ITEM GenKey@chest` answers `Suddenly, a key drops from the sky.` and the
key lands inside the chest. The `@chest` on the `MODIFY` lines tells
the command to look inside the chest. The key code is the item's readable text:
`harl.store`, exactly the text on the door. Codes compare without regard to
case. Check:

```text
LOOK IN chest
```

(or `OPEN chest` first if the chest shows as closed.) The key should be
listed. `DESTROY ITEM`, `COPY` and `STAT` all accept the `@container` form.

### Step 13: the gate guard

```text
GOTO Brindlewick#2
CREATE MOB GenMob
(Enter at "Edit which?")
MODIFY MOB generic NAME the gate guard
MODIFY MOB guard DISPLAY A gate guard leans on a long spear here.
MODIFY MOB guard DESCRIPTION A young guard in a patched tabard. He watches the road and the gossip with equal attention.
MODIFY MOB guard RACE Human
MODIFY MOB guard LEVEL 6
```

The guard needs two behaviors: `Guard` (he comes to the aid of anyone attacked
in his room) and `Scriptable` (his scripts, in the next section). Behaviors are
added in the mob editor:

```text
MODIFY guard
```

Find the line that starts `Behaviors:` (number 21 on a stock build) and type
its number at `Edit which?`. Then:

| The game asks | You type | The game says |
| --- | --- | --- |
| `Enter a behavior to add/remove (?)` | `Guard` | |
| `Enter any behavior parameters (?)` | press Enter (none) | `Guard added.` |
| `Enter a behavior to add/remove (?)` | press Enter | `(no change)` |
| `Edit which?` | press Enter | |

Leave the `Scriptable` behavior for the next step, where you write its script
at the same time. `STAT guard` shows the mob as it is; `AHELP BEHAVIOR_GUARD`
documents the behavior. Behaviors and their parameters are in
[Behaviors and masks](24-behaviors-and-masks.md).

## Give it one interaction

A MOBPROG script is a list of small programs. Each starts with a trigger line
and ends with a line holding only `~`. Anything that is not a script command
is run as an ordinary game command by the host, so `say` makes the guard speak.
You attach scripts with the `Scriptable` behavior. This step gives the guard
two programs:

- `GREET_PROG 100` runs whenever someone enters his room (always: 100 percent).
- `SPEECH_PROG key storeroom` runs when someone says a line containing the
  word `key` or `storeroom`: the hint.

### Step 14: write the greeting and the hint

```text
MODIFY guard
```

Choose the `Behaviors:` line again (21) and add `Scriptable`:

| The game asks | You type |
| --- | --- |
| `Edit which?` | the number of `Behaviors:` |
| `Enter a behavior to add/remove (?)` | `Scriptable` |

Because `Scriptable` holds a script, the game opens its line editor. As the
script is empty, it starts in Add Text mode (`You are now in Add Text mode.
Enter an empty line to exit.`). Type these lines, one per line, each followed
by Enter; the last empty line leaves Add Text mode:

```text
GREET_PROG 100
if ispc($n)
say Welcome to Brindlewick, $n. Harl the smith has lost his hammer. Say accept if you will help find it.
endif
~
SPEECH_PROG key storeroom
say The storeroom key? Harl keeps it in the oak chest in the well house, south of the green.
~

```

(The final empty line is the empty line that leaves Add Text mode.) The editor
then shows its menu prompt, `Menu (?/A/D/L/I/E/R/S/Q)`. Use it if you want to
check what you typed:

| At the Menu prompt | Does |
| --- | --- |
| `L` | Lists the lines with their numbers |
| `E` | Edits one line: asks for the number, then the new text |
| `I` | Inserts a line before a number |
| `D` | Deletes a line |
| `A` | Back to Add Text mode (adds lines at the end) |
| `R` | Search and replace over all lines |
| `S` | Save and exit: asks `Save and exit, are you sure (N/y)?` |
| `Q` | Quit without saving (asks to confirm) |
| `?` | Shows the help |

Type `S` and then `Y`. The editor checks the script as `SCRIPTCHECK` does, and
only if it finds a problem does it list it and ask whether to edit it again,
keep it, or cancel. When it saves, the game says `Scriptable added.`, asks for
another behavior (press Enter, `(no change)`), and asks `Edit which?` (Enter).

What the lines mean:

- `GREET_PROG 100` is the trigger: [GREET_PROG](26-mobprog-triggers.md#greet_prog).
  The `100` is a percent chance. `$n` is the one who entered.
- `if ispc($n)` ... `endif` runs the lines between only for a player's
  character, not for a wandering mob. `ISPC` is a condition function; see
  [chapter 28](28-mobprog-functions.md).
- `say ...` is not a script word, so it is run as the guard's `SAY` command.
- `SPEECH_PROG key storeroom` is [SPEECH_PROG](26-mobprog-triggers.md#speech_prog).
  The words are alternatives: the program runs when the line said contains any
  of them as a whole word. Punctuation counts as part of a word, so `key?` does
  not match `key`; list both spellings if you want both.
- `~` ends a program. The inline script is stored on the behavior as one line
  with the statements joined by `;`, which is why a statement must not itself
  contain a semicolon.
- No semicolons appear in the text of the lines above; keep it that way.

Check the script and read it back:

```text
SCRIPTCHECK guard
EDIT SCRIPT guard
```

`SCRIPTCHECK guard` prints `SCRIPTCHECK guard: no problems found.` `EDIT
SCRIPT guard` reopens the same line editor on the script; use `Q` and then `Y` to
leave it without changes.
Everything about the language is in the [MOBPROG guide](04-mobprog.md), its
[triggers](26-mobprog-triggers.md), [commands](27-mobprog-commands.md) and
[functions](28-mobprog-functions.md).

**Optional: move the script into a file.** A long script is easier to maintain
in its own file, which several mobs may share. `EXTRACT SCRIPT guard` writes
the script to `world/resources/progs/the-gate-guard.script` (a name made from
the mob's name) and replaces the inline script with
`LOAD=progs/the-gate-guard.script`. It tells you the file and that it is
`LOAD=progs/the-gate-guard.script` now. Run it only if you want that; the rest
of the tutorial works either way. Do not edit the file's lines by hand without
`SCRIPTCHECK`: a script file needs each command on its own line.

### Step 15: save the mobs and items

You have created mobs and items in four rooms. List what is unsaved and save it:

```text
LIST UNSAVED
SAVE CHANGED
```

`LIST UNSAVED` prints `Rooms with unsaved mobs or items (SAVE CHANGED saves
them):` and one line for each of `Brindlewick#1` to `Brindlewick#4`, with the
building commands that changed them and how many mobs and items. `SAVE
CHANGED` saves each of those rooms, then writes the world files, and says
`Saved the mobs and items of 4 rooms.` followed by the world-files report. Run
`LIST UNSAVED` again: it says there are none.

`SAVE CHANGED` saves each mob into its own start room (the same as `SAVE ROOM
HOME`). A mob you walk to another room is saved where it belongs, and a visitor
in a room is not saved there.

## Save the world files

`SAVE` in builder mode writes the world files. The report line tells you what
it did:

```text
SAVE
WORLD STATUS
```

You should see `The world files are up to date.` (or `are written: N changed.`
if anything was still pending) and, from `WORLD STATUS`, `No world file was
changed outside the game since it was loaded or last written.` and `SAVE would
change no world file.` The report may also list unsaved rooms, files held back
(`WORLD PROBLEMS` says why) or `SCRIPTCHECK ALL finds N problems`; none of
those should appear now.

Look at what you made. From another terminal:

```sh
ls "$BRIND/world" "$BRIND/world/areas"
cat "$BRIND/world/world.ini"
grep -n 'ROOMID' "$BRIND/world/areas/brindlewick.cmare"
```

The area is `areas/brindlewick.cmare`, one XML file for the whole area (its
name is made from the area's name). The `grep` should print five `ROOMID`
lines, `Brindlewick#0` to `Brindlewick#4`. `catalog.cmare` holds the hammer. The
mobs, items and exits are inside each room's `AROOM` element: for example the
locked door appears as an `REXIT` with `<EXID>LockedDoor</EXID>` and
`<EXDAT>harl.store</EXDAT>`. You can read and diff these files; do not hand-edit
them while the game runs unless you know the rules in
[World files and editing](02-world-workflow.md).

**Optional: put the world under version control.** The `world/` folder is the
product; commit it.

```sh
git -C "$BRIND/world" init
git -C "$BRIND/world" add -A
git -C "$BRIND/world" commit -m "Brindlewick: five rooms, Harl and the guard"
```

`tools/worlddiff.py OLD_WORLD NEW_WORLD` compares two worlds object by object.

### Tidy: remove the starter area (optional)

The shipped `New Area` is still in your world, along with a test that looks for
it. A clean world check is easier without them. Skip this if you prefer: the
only effect is a few warnings from `WORLD CHECK` about the starter text and
about rooms nothing leads to.

```text
GOTO Brindlewick#0
DESTROY AREA New Area
```

The game asks `Area: "New Area", OBLITERATE IT???`. Type `Y`. (You cannot
destroy the area you are standing in; you are in Brindlewick.) `WORLD STATUS`
now says that `SAVE would delete: areas/new-area.cmare`; the next `SAVE` (or
the quit below) does it. Your builder character was made in `New Area#0`; if
you would rather not find out how your character copes with that room going
away (this chapter's author did not exercise it), skip this step and accept the
warnings. Also remove the old test outside the game (step 17).

### Leave the game

You must restart anyway: the new `GAMENAME` and `START` take effect at the
next start, and the next two steps add files from outside the game.

```text
GOTO Brindlewick#0
QUIT
```

Answer `Y` at the questions (`Quit -- are you sure (y/N)?`). If any room still
had unsaved mobs or items the game would ask whether to save them first; the
world files are written when you quit.

## Add a quest

A quest is a small script of its own, kept one per file in `world/quests/`. The
quest here gives the gate guard the job: a script on the guard offers the
quest when the player says `accept`, and takes the hammer back when the player
gives it to him. The format and every command are in
[Quests](08-quests.md); this is the smallest complete shape of a fetch quest.

Quests are loaded when the game starts, so write the file while the game is not
running. A quest file holds the quest's settings first, then, after the line
`<?xml version="1.0"?>`, the MOBPROG scripts it hands out, each in a
`<FILE>` section.

### Step 16: write the quest file

Run this at the repository root. The quoted `'EOF'` keeps the `$n` codes
exactly as written.

```sh
cat > "$BRIND/world/quests/harls_hammer.quest" <<'EOF'
set name harls_hammer
set display Harl's Hammer
set category side
set instructions Harl the smith lost his forge hammer in his locked storeroom.  Find the key, fetch the hammer and give it to the gate guard.
set rewards "50 XP 2 QP"
set repeatable no
set autostart boot

# the giver: the gate guard, who already stands in the world
set area "Brindlewick"
set room
set mobgroup
set mob reselect "the gate guard"
give script LOAD=hammer_guard.script

set duration 0

<?xml version="1.0"?>
<FILE><NAME>hammer_guard.script</NAME>
<DATA>
FUNCTION_PROG CAN_ACCEPT
	if ISPC($n) and !QUESTSCRIPTED($n *)
		RETURN TRUE
	endif
	RETURN CANCEL
~

FUNCTION_PROG DO_ACCEPT
	mpscript $n INDIVIDUAL SAVABLE STATIC=LOAD=hammer_track.script
	mpqobjective $n * ADD hammer 1 Bring Harl's forge hammer to the gate guard
	mpqjournal $n * Harl lost his forge hammer in his locked storeroom.  The gate guard will pay for it.
	mpqset * STATISTICS ACCEPTED
~

SPEECH_PROG accept
	if CALLFUNC(CAN_ACCEPT)
		sayto "$n" Good.  The key is in the oak chest at the well house.  Bring me the hammer from the storeroom.
		MPCALLFUNC DO_ACCEPT
	endif
~

GIVE_PROG all
	if ISPC($n) and QUESTSCRIPTED($n *)
		if NAME($o == 'an old forge hammer')
			mpjunk $o
			mpqobjective $n * DONE hammer
			mpqjournal $n * I gave Harl's hammer to the gate guard.
			sayto "$n" Harl will be glad of it.  Here is your reward.
			mpquestwin $n * REWARD
			mpendquest $n
			mpqset * STATISTICS SUCCESS
		endif
	endif
~
</DATA></FILE>
<FILE><NAME>hammer_track.script</NAME>
<DATA>
ONCE_PROG 100
	mpsetvar $i INSTRUCTIONS Fetch the forge hammer from Harl's storeroom and give it to the gate guard.
~
</DATA></FILE>
EOF
```

How it works, line by line:

- The `set` lines name the quest (`harls_hammer`: keep the name stable, because
  saved characters refer to it), give it a display name, a category and the
  instructions players read in `QUESTS`. `set rewards "50 XP 2 QP"` is what
  winning pays. `set repeatable no` allows one win. `set autostart boot` starts
  the quest by itself once, as the game begins. `set duration 0` means it has
  no time limit. Every quest needs a `set name` and a `set duration`.
- `set area ... set mob reselect "the gate guard"` borrows the guard who
  already stands in Brindlewick as the quest's giver. `give script
  LOAD=hammer_guard.script` adds the first embedded script to him for as long as
  the quest runs; the guard's own greeting and hint stay as they are.
- `hammer_guard.script` is the quest's accept script. The engine looks for the
  pair `FUNCTION_PROG CAN_ACCEPT` and `DO_ACCEPT`: the first says whether the
  character may take the quest, the second takes it (gives the character a
  tracking script, an objective and a journal entry). `SPEECH_PROG accept` calls
  them when the player says `accept`.
- `GIVE_PROG all` runs when anyone gives the guard anything. If the giver is on
  the quest and the item is the hammer, the guard destroys it (`mpjunk`),
  marks the objective done, pays (`mpquestwin $n * REWARD` records the win and
  pays the rewards in one message) and ends the character's quest script. Never
  pay twice: with `REWARD` do not also give experience by hand.
- `hammer_track.script` is the tracking script the character carries while on
  the quest. Being scripted by it is what `QUESTSCRIPTED($n *)` tests; the
  `*` means this script's own quest.

You did not need the hammer as a quest-loaded item: it is already in the
world, in the storeroom, and the quest only recognizes it by name. A quest can
also load things for itself (see [Quests](08-quests.md)).

### Step 17: remove the old test and relaunch

If you destroyed `New Area`, the shipped test that expects it would fail. Remove
it, then start the builder again. (If you kept `New Area`, leave the test.)

```sh
rm "$BRIND/world/tests/start.play"
"$BRIND/launch.sh" BUILDER=1
```

This second launch is quicker: the world files differ from what the cache was
built from (the quest file is new), so you see that the world files have
changed and are loaded again; your builder character is kept in the cache. In
builder mode the second launch goes to the characters menu instead of creating
a character. Type your character's name:

```text
Curator
```

At login you see the same short summary as before. The quest line now reads
`QUESTCHECK: 0 problems in world/quests.` Confirm the quest loaded and check
the file:

```text
LIST QUESTS
QUESTCHECK harls_hammer
```

`LIST QUESTS` shows `harls_hammer`, as `loaded` and a moment later `running`
(it starts a tick after the game begins, because of `set autostart boot`).
`QUESTCHECK harls_hammer` reads the file and prints `QUESTCHECK
harls_hammer.quest: 0 problems.` If the quest stayed `loaded`, start it by hand
with `MODIFY QUEST harls_hammer START`; `QUESTINFO harls_hammer` shows the
whole state: step, the guard it chose, the scripts it gave, and its last
errors.

`WORLD STATUS` now shows that the world files match what was loaded. In a
running game, a quest file you add or edit outside the game is not read until
you restart (for an existing quest, `WORLD RELOAD QUEST harls_hammer` reads it
again).

## Add a conditional description

A description that starts with the literal text `<VARIES>` can change by the
time of day, weather, season, who is looking and the state of quests and
variables. It is plain text with tags; nothing runs. Two descriptions in
Brindlewick will change.

The green gets a day and night version. `<NIGHT>` is true at night and `<ELSE>`
is true when the block before it was false:

```text
GOTO Brindlewick#0
MODIFY ROOM DESCRIPTION <VARIES><NIGHT>Lantern light spills from the smithy door and the gate across the trodden grass.</NIGHT><ELSE>Sun lies warm on the trodden grass.</ELSE></VARIES> Three low stone buildings face the green: a smithy to the north, the village gate to the east and a well house with a sagging roof to the south.
```

The smithy changes when the viewer has won Harl's quest. `<QUESTWON name>` is
true if the character looking has won that quest:

```text
GOTO Brindlewick#1
MODIFY ROOM DESCRIPTION <VARIES><QUESTWON harls_hammer>Harl's old forge hammer hangs on its hook again, and the smithy rings with work.</QUESTWON><ELSE>An empty hook hangs above the anvil, where a hammer should be.</ELSE></VARIES> Heat shimmers over the forge. Racks of tongs and half-finished hinges line the walls, and a narrow door in the north wall leads to the storeroom.
```

The text must start with `<VARIES>` exactly: no leading space. Write the tags
in capitals, and close each. Check them without playing:

```text
PREVIEW night
PREVIEW day
CHECKTEXT area
```

`PREVIEW` shows this room's title and description as they read under the
conditions you name (here the green, if you did it from the green: `GOTO
Brindlewick#0` first); it changes nothing. `CHECKTEXT area` lists tags that
would print as text, mis-spelt tags and tags left unclosed; it should report
nothing wrong. Other tags (`DAWN`, `DUSK`, seasons, weather, `IFVAR`,
`FIRSTVISIT`, `MASK1`...) and the rules for combining them are in
[Presentation and language](11-presentation-language.md).

`LOOK` in the green now shows the day text or the night text depending on the
area's clock. (The clock runs in real time unless you start a game with
`TIMEMODE=TURNS`.) The smithy shows the empty hook until a character has
completed the quest.

## Check, save, and test

### Step 18: the checks

The world has several linters, each looking at something different. Run them
in builder mode now:

```text
WORLD CHECK
SCRIPTCHECK ALL
PROPCHECK ALL
QUESTCHECK ALL
LIST UNSAVED
```

`WORLD CHECK` prints `WORLD CHECK: n errors, m warnings.` and then each
finding as `[part] where: what`. With the starter area removed it should report
`0 errors, 0 warnings`. If you kept `New Area` expect warnings that name it:
its starter text, and rooms that nothing leads to from the start room. To
look only at your area:

```text
WORLD CHECK AREA Brindlewick
```

The parts of `WORLD CHECK` that matter for this world:

| Part | What it would say about this world |
| --- | --- |
| `EXITS` | An exit to nowhere (error); a door with no way back (warning) |
| `REACH` | A start room that does not exist (error); rooms nothing leads to from the start (warning). It reads `START`, so it needs the restart you have done |
| `KEYS` | The storeroom door is locked with `harl.store`: an error if no such key exists anywhere. It exists, inside the chest |
| `TEXT` | A room still on its class's default text, a generic mob or item still on `a generic ...` |
| `SHOPS` | A shopkeeper with nothing to sell (Harl has the sword) |
| `SCRIPTS`, `PROPS` | `SCRIPTCHECK` and `PROPCHECK` for the whole world |

`SCRIPTCHECK ALL` and `PROPCHECK ALL` each print `... no problems found.` for a
clean world. `ONLY=part,part` limits `WORLD CHECK` to some parts, for example
`WORLD CHECK ONLY=KEYS,EXITS`. `LIST UNSAVED` should say nothing is unsaved.
Warnings need judgment; errors deserve investigation. The checkers are listed in
[Testing and release](13-testing-release.md).

Save the descriptions you just changed and look at the plan:

```text
SAVE
WORLD STATUS
```

`SAVE` writes `areas/brindlewick.cmare` again (`The world files are written: 1
changed.`), and `WORLD STATUS` then says nothing is pending. `SAVE` does not
commit anything in Git; do that yourself.

### Step 19: play it as a player, inside the builder (PLAYTEST)

An Archon walks through locked doors and cannot be hurt. `PLAYTEST ON` makes
the world treat you as an ordinary mortal for a while, in place, so a quick check
of one room is easy:

```text
GOTO Brindlewick#0
PLAYTEST ON
EAST
SAY where is the key
PLAYTEST OFF
```

`PLAYTEST ON` tells you `Playtest: the world now treats you as a mortal.  A
death brings you back here.` and the prompt says `(playtest)`. Walking east you
should see the guard greet you with `Welcome to Brindlewick, Curator. ...`, and
after `SAY where is the key` he answers with the hint about the oak chest.
`PLAYTEST OFF` gives your powers back. While playtesting, a building command is
refused; `BUILD [command]` runs one command with your powers.

`PLAYTEST ON` keeps what you do: if you had picked up the key it would still be in
your inventory afterwards, and a finished quest stays finished. For a clean
test use the next two tools.

```text
PLAYTEST GAME
```

`PLAYTEST GAME` copies your world, including changes you have not saved, into a
temporary folder and starts a second copy of the game there, as an ordinary
character, in a window titled `Isolated playtest - close to discard`. Closing
the window deletes it all. On a machine with no display it opens no window: it
prints the exact command to run in another terminal and the temporary folder to
delete afterwards. See
[Testing and release](13-testing-release.md) for what the isolated playtest
does and does not isolate.

### Step 20: the test file

Playing by hand finds problems once. A playtest file replays the same play
every time, as a new character in a disposable game, and checks what you
expect. Write one for the whole story. A `.play` file has one command a line;
`#expect TEXT` says the text appeared in what the commands since the last
check printed, `#reject TEXT` that it did not, `#assert` asks the game
(`HERE room`, `HAS item`, `VAR`, `QUEST`), and `#wait N` lets N ticks pass.

```sh
cat > "$BRIND/world/tests/brindlewick.play" <<'EOF'
# The whole Brindlewick tour as an ordinary new character.
#char Wanderer,Human,Apprentice
look
#expect The Brindlewick green
#assert HERE Brindlewick#0

# the gate: the greeting, the hint and the quest
east
#wait 1
#expect Welcome to Brindlewick
#assert HERE Brindlewick#2
say where is the key
#expect oak chest in the well house
#wait 2
say accept
#expect Bring me the hammer

# the well house: the chest and the key
west
south
#assert HERE Brindlewick#3
open chest
get key from chest
inventory
#expect brass storeroom key

# the smithy: the shop, and a door that holds until it is unlocked
north
north
#assert HERE Brindlewick#1
list
#expect notched shortsword
look
#expect An empty hook hangs above the anvil
north
#assert HERE Brindlewick#1
unlock north
open north
north
#assert HERE Brindlewick#4

# the storeroom: the hammer and the armor
look
#expect old forge hammer
get hammer
get jerkin
#assert HAS hammer

# back to the guard with the hammer
south
south
east
give hammer to guard
#expect Harl will be glad of it

# the smithy remembers who finished the job
west
north
look
#expect hangs on its hook again
#reject An empty hook
EOF
```

Notes on the file: the first `north` in the smithy runs into the closed locked
door, so the `#assert HERE Brindlewick#1` after it proves the door held; then
`unlock north` and `open north` let the player through. `get key from chest`
needs `open chest` first because the chest starts closed. The text after each
`#expect` is what your own descriptions and scripts print, so if you changed
the wording earlier, change the expectation to match. The `#char` line is the
character the disposable game creates (name, race, class). A test runs as a
`DEV=1` game, which is why `ASSERT` works, and with fixed dice. A new game
starts in the `START` room.

Save the world (the test is read from the world folder, not from the live
game), then run it from the repository root:

```sh
WORLD="$BRIND/world" python3 tools/playtest.py "$BRIND/world/tests/brindlewick.play"
```

`WORLD` names the world folder the test plays on; the runner makes a scratch
copy of it, its own database and its own saves, so nothing in `$BRIND` changes.
A passing run prints one line:

```text
PASS  /home/you/brindlewick/world/tests/brindlewick.play (N checks)
```

and exits 0. A failing run prints `FAIL`, each failing check with its line
number in the file and what it expected, and the path of a `transcript.txt` of
the whole session to read. Use `KEEP=1` before the command to keep the scratch
folders of passing runs too. Give the runner a folder
(`... tools/playtest.py "$BRIND/world/tests"`) and it runs every `.play`,
`.test` and `.js` file in it. The same files can be run from inside a builder
game, in a disposable campaign, with results reported when it finishes:

```text
WORLD TEST brindlewick.play
```

(`PLAYTEST RUN brindlewick.play` is the same command.) It reads the files from
the world folder, so `SAVE` first. The format of `.play` files, `ASSERT`, the
JavaScript tests and the CI uses are in
[Testing and release](13-testing-release.md).

You can also ask the running game the same questions directly. In builder
mode:

```text
ASSERT HERE Brindlewick#1
```

answers `ASSERT ok: HERE Brindlewick#1` or `ASSERT FAIL:` and why.

### Step 21: the headless check

When the builder is closed, the same lint runs from a shell without a player,
and its exit status says whether the world has errors (it is how you would
guard a release). Quit the builder first (`GOTO Brindlewick#0`, `QUIT`, `Y`),
then:

```sh
"$BRIND/launch.sh" CHECK=1
echo $?
```

It loads the world as a new game would, prints the same report as `WORLD
CHECK` (`WORLD CHECK: 0 errors, 0 warnings.` when the starter area is gone and
all is well), and exits. The status is `0` when there are no errors and `1` when
there is at least one. `CHECK=1` shows no title screen and no menu.

## Make a reliable opening

A new character should begin somewhere that explains itself. Three tools do it.

**1. The start room.** `START` in `world.ini` (done in step 6) is where a new
character begins. `START_<race>` and similar keys give different starts for
different characters; see [world settings](world-settings.md). The start room
must have light, exits and something to do. The green has all three.

**2. A title screen and a prologue.** The launcher shows `world/text/title.txt`
when a game starts, with `@MudInfo?NAME@` replaced by your `GAMENAME`, and
`world/text/prologue.txt` once before a new game, split into pages by a line of
three or more dashes. Both accept colour codes such as `^c` (cyan) and `^N`
(back to normal). Add them with a text editor:

```sh
mkdir -p "$BRIND/world/text"
cat > "$BRIND/world/text/title.txt" <<'EOF'
^c@MudInfo?NAME@^N
^wA small village, a lost hammer, a locked door.^N
EOF
cat > "$BRIND/world/text/prologue.txt" <<'EOF'
Brindlewick has one smith, one gate guard and one problem.
---
Talk to the guard at the east gate. Say what you are looking for.
EOF
```

The next launch (of any mode except `CHECK=1`) prints the title with the game
name `Brindlewick Tales`; a new normal game then shows the two prologue pages,
waiting for Enter between them.

**3. A first-entry message in the world.** For text that belongs to the game
and must appear once per character, use the `Prop_FirstEntry` property on a
room, not a line in the prologue. In builder mode, on the green:

```text
GOTO Brindlewick#0
MODIFY ROOM AFFECTS
```

At `Enter an effect to add, -ID to remove, ...` type `Prop_FirstEntry`; at
`Enter any effect parameters (?)` type:

```text
ID=brindlewick-arrival TEXT="Woodsmoke and the ring of a hammer drift across the green." ONCE=CHARACTER
```

It answers `Prop_FirstEntry added.`; press Enter at the next prompt to finish.
`ID` is a stable name for the once-marker, so the message does not repeat for a
character who has seen it even if you later rename or move the room.
`ONCE=PROFILE` would share it across all the characters of one profile. Do not
put essential instructions only in a random ambient line. Choose the opening
by what the player must be told: [Characters](03-characters.md) covers
character creation, origins and kits, and
[Presentation and language](11-presentation-language.md) covers opening text.
The property is documented under
[Prop_FirstEntry](23-properties-reference.md#prop_firstentry).

Run `SAVE` and `PROPCHECK ROOM` afterwards (`PROPCHECK this room: no problems
found.`).
