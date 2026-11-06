# Areas, rooms and exits

[Handbook](README.md) · [World files and editing](02-world-workflow.md) · [Your first world](01-first-world.md)

This chapter is the geography manual. It explains how CoffeeSUD arranges its world into areas, rooms and exits, and gives every command and every editor field you need to build, link, change, copy, check and delete that geography, in the game and in the `.cmare` world files. It is written for world creators first (the commands and editors), and for scripters and engine developers where the file formats and class names matter. Everything here was checked against the Java under `CoffeeMud/com/planet_ink/coffee_mud/`, the shipped help (`CoffeeMud/resources/help/arc_help.ini`) and the passing fixtures `b7-effects.js`, `b8-gmodify.js`, `b9-dig.js` and `b10-map.js`.

Mobs, items, properties and behaviors placed in rooms have their own chapters: [Mobs](21-mobs.md), [Items](22-items.md), the [properties reference](23-properties-reference.md) and the [behaviors reference](24-behaviors-and-masks.md). Random generation of whole areas is in [Generation](12-generation.md), and the full build workflow (save variants, reload, undo, backups) is in [World files and editing](02-world-workflow.md).

## In this chapter

- [Concepts](#concepts)
- [Orientation and inspection commands](#orientation-and-inspection-commands)
- [Building rooms](#building-rooms)
- [Editing a room](#editing-a-room)
- [Linking rooms and working with exits](#linking-rooms-and-working-with-exits)
- [Doors, locks and keys](#doors-locks-and-keys)
- [Areas](#areas)
- [Copying, moving and destroying](#copying-moving-and-destroying)
- [Bulk tools](#bulk-tools)
- [Checking your geography](#checking-your-geography)
- [Reference: directions](#reference-directions)
- [Reference: locale classes](#reference-locale-classes)
- [Reference: exit classes](#reference-exit-classes)
- [Reference: room editor fields](#reference-room-editor-fields)
- [Reference: exit editor fields](#reference-exit-editor-fields)
- [Reference: area editor fields](#reference-area-editor-fields)
- [Reference: area classes](#reference-area-classes)
- [Reference: command syntax](#reference-command-syntax)
- [How geography is saved](#how-geography-is-saved)
- [Worked examples](#worked-examples)
- [Persistence notes](#persistence-notes)
- [Troubleshooting and gotchas](#troubleshooting-and-gotchas)
- [See also](#see-also)

## Concepts

### The three layers

The world is built from three kinds of object.

- An **area** groups rooms. It carries the rules shared by its rooms: climate, atmosphere, theme (what magic, powers and technology work there), calendar, currency and shop pricing, staff, behaviors and effects. Every room belongs to exactly one area.
- A **room** is one place. Its **locale class** (`StdRoom`, `Woods`, `UnderWater`, a grid...) decides whether it is indoors or outdoors, its terrain domain, how much it costs to cross, what weather reaches it and what resources it can hold.
- An **exit** is the way through a link between two rooms. A room has up to one link and one exit object for each direction.

A link and an exit are separate objects. A room holds, for each direction, a *destination room* (the "door" slot of `rawDoors()`) and an *exit object* (the "exit" slot of `getRawExit()`). The destination says *where* you arrive; the exit says *how* you get there: open walkway, door, locked gate, climb, jump, hidden passage. Creating and destroying rooms and links answers "is there a way, and to where"; creating and destroying exits answers "what does the way look like".

Links are one-way records. A link from room A east to room B does not give B a link west to A. The tools below create both ends when that is the usual case (`CREATE ROOM`, `LINK`, `WORLD DIG`) and leave one-way links when you ask for them.

### Room IDs

Every room in the world has a unique **room ID** of the form `Area Name#number`, for example `New Area#0` or `Midgaard#3001`. The text before the `#` is the area's name, so renaming an area renumbers its rooms (see [Renaming an area](#renaming-an-area)).

- **How numbers are assigned.** `CREATE ROOM` and the area-creating commands ask the area for the next free number. The engine ([StdArea.getNewRoomID](../../CoffeeMud/com/planet_ink/coffee_mud/Areas/StdArea.java)) scans the numbers already used by that area's name, then starts at the lowest existing number and returns the first one that is unused, searching up to 1000 above the highest. In practice new rooms fill any gap left by a deleted room, and a fresh area starts at `#0`. If the search somehow fails it falls back to a random number.
- **Grid areas** (`StdGridArea`) instead encode the grid position into the number, so a room's ID depends on its coordinates and direction of creation.
- **Grid children.** Rooms inside a grid or maze locale have no ID of their own (their `roomID()` is empty). They are addressed as `ParentID#(x,y)`, for example `Coffee Grounds#19#(0,2)`. You cannot `MODIFY` a grid child; modify the grid's parent room instead.
- **Typing IDs.** Commands that take a room ID accept the full ID, case-insensitive. `MODIFY ROOM ID` also accepts just the number (`12`), `#12`, or `Area Name#12`. Many commands (`GOTO`, `AT`, `LINK`) also accept room text, such as part of a title, and find the best match.
- **Changing an ID.** `MODIFY ROOM ID 25` renumbers the room you are in. The new ID must start with the room's own area name and must be unused. See [Room commands](#room-commands-in-detail).

### Directions

The default direction set is north, south, east, west, up, down and the special "gate" direction (`V`, shown as "there"). Setting `DIRECTIONS=11` adds northeast, northwest, southeast and southwest. See [Reference: directions](#reference-directions).

### Where your edits live

How a geography command behaves depends on how the game was started. Details are in [World files and editing](02-world-workflow.md) and [Persistence notes](#persistence-notes) below; the short version:

| Launch mode | Where geography edits go |
| --- | --- |
| `BUILDER=1` | Into the live world and the content database, immediately. `SAVE` (or quitting) then rewrites the world files, `world/areas/NAME.cmare`. This is how you author a world. |
| Normal game, or `DEV=1` | Into this game's working copy of the world and its saves only. Other games, and the world files, do not see them. The game prints a one-time warning ("This game plays on a copy of the world") when you use a building command. |

All the commands below need the builder security flags (`CMDROOMS`, `CMDEXITS`, `CMDAREAS`, `GMODIFY`, `COPYROOMS`, `GOTO`, `AT`, see [Security flags](#security-flags)). A character in builder mode is an Archon and has them all.

## Orientation and inspection commands

Before you build, you need to move around and see what is there.

```text
GOTO New Area#0
GOTO the greasy pub
GOTO Midgaard
GOTO PREVIOUS
GOTO PARENT
AT New Area#4 LOOK
AT PLAYERS SAY hello
STAT ROOM New Area#4
STAT AREA New Area
EXITS
SYSMSGS
```

| Command | What it does |
| --- | --- |
| `GOTO [room ID / room text / mob or player name / area name]` | Moves you there. An area name takes you to a random room of that area. `GOTO AREA [text]` searches only the area you are in. `GOTO PARENT` leaves a grid child for its grid's parent room. `GOTO PREVIOUS` goes back along your last ten GOTO locations. Needs the `GOTO` flag. Source: [Goto](../../CoffeeMud/com/planet_ink/coffee_mud/Commands/Goto.java). |
| `AT [room ID / room text / PLAYERS] [command...]` | Runs one command as if you stood in that room, then returns you. `AT PLAYERS` runs it in every other player's room (there are no other players in CoffeeSUDs single-player game, so it is mostly for old scripts). Needs the `AT` flag. |
| `STAT ROOM [name or ID]` | Shows the room's whole editor listing (every field, no prompts). Searches your area first, then the world. |
| `STAT AREA [name]` | The same for an area. |
| `SYSMSGS` | Toggles sysop messages. With it on, `LOOK` shows the room ID and locale (`Area :(...)`, `Locale:(...)`, and the ID before the title), and the exit list shows the exit class. Turn it on while building. |
| `EXITS` (or `EX`) | Shows the exits of the current room. `EXITS SHORT` is brief. |
| `AUTOEXITS [ON/OFF]` | Toggles showing the exit list when you look. This is a player setting. |
| `AREAS` | The player-facing list of areas, with `SORT=` options. |
| `TRAILTO [flags] [area name / room ID / EVERYROOM / EVERYAREA]` | Prints the directions from where you stand to a room or area. A quick way to test that something is reachable. Flags include `OPENONLY`, `UNLOCKEDONLY`, `AREAONLY`, `NOWATER`, `NOAIR`, `OUTDOORONLY` (see `AHELP TRAILTO`). |

The `LIST` command has several geography reports (builder only):

| Command | Shows |
| --- | --- |
| `LIST ROOMS (text)` | The room IDs and titles of your area and its child areas; text filters by title or ID. |
| `LIST AREA` | Each room ID of your area with its locale class. |
| `LIST AREAS` | Every area in the world. |
| `LIST LOCALES (text)` | Every locale class you can use, plus the domain names. |
| `LIST EXITS (text)` | Every exit class you can use. |
| `LIST LINKAGES (WORLD) (SUBSTATS)` | For your area (or the whole world), the groups of rooms that connect to each other. More than one group in an area means part of it cannot be reached. |
| `LIST ONEWAYDOORS (AREA) (LOG)` | Every link with no link back, as `room: direction to room`. |
| `LIST ORPHANS (AREA) (LOG)` | Rooms nothing links into. |
| `LIST UNLINKEDEXITS (AREA) (LOG)` | Exits that lead nowhere (an exit object with no destination room), showing the room ID the exit remembers if it had one. |
| `LIST UNSAVED` | Rooms with building changes not yet saved (see [World files and editing](02-world-workflow.md)). |

`AREA` limits the three link reports to your own area; `LOG` also writes the report to the log.

## Building rooms

### Making your first room in a new area

Rooms are created next to an existing room, so an empty world needs one starting room. `CREATE AREA` makes an area and its first room, `Area Name#0`, and moves you into it. The shipped starter world has one area, `New Area`, with two rooms (`New Area#0` and `New Area#1`); see [How geography is saved](#how-geography-is-saved).

```text
CREATE AREA Emberfall
```

The engine asks for an area type (default `StdArea`, press Enter) and creates `Emberfall#0` as a plain `StdRoom`, titled `StdRoom-Emberfall#0` with an empty description. You are standing in it. See [Creating an area](#creating-an-area).

### CREATE ROOM

```text
CREATE ROOM [direction] [locale class or template]
CREATE ROOM [locale class or template] [direction]
CREATE [locale class] [direction]
```

You stand in an existing room and name the direction of the new room and what kind it should be. The engine creates the room in the same area as the room you stand in, links you to it, and links it back (when the far side has no link there yet). Both ends share the same exit, `StdOpenDoorway` unless the direction already had an exit, in which case the new room gets that exit too.

```text
CREATE ROOM north StoneRoom
create woods east
create ROOM s Plains
```

- The direction may come first or last. `create stoneroom north` is a shorthand for `CREATE ROOM north StoneRoom`; it works for any locale class or exit class name followed by a direction.
- The new room's title is `ClassName-roomID` and its description is empty. Write them with `MODIFY ROOM` (the `WORLD CHECK` TEXT report lists rooms that still have the default text).
- The locale may also be the ID of a **builder template** (see [Templates](#templates)). A template room is copied whole: title, description, effects, behaviors, tags and contents, but it gets a new ID and your current area.
- The command is refused inside a grid child ("This command is invalid from within a GridLocaleChild room") and refused when the direction is not valid for the room ("Room was created without that possible direction" for grids that lack it, or "A room may not be created in that direction" at the edge of a grid area).
- Making a room where one already exists does not stop you. `create StoneRoom north` twice leaves the first new room in place with a link from it back to you; you now have a one-way exit to the old room, and a new room to the north. Use `LINK` and `UNLINK` to tidy.
- Needs `CMDROOMS`.

The usage message of `CREATE ROOM` mentions `LINK [ROOM ID]` as an alternative to a locale. That is a leftover from older versions: `CREATE ROOM` does not accept it. Use the separate `LINK` command.

### Templates

A template is a favourite room, exit, mob or item kept for quick duplication.

```text
TEMPLATE LIST (MOBS/ITEMS/EXITS/ROOMS) (mask)
TEMPLATE ADD [new ID] [here / room ID / exit direction / mob or item name]
TEMPLATE DELETE [ID]
TEMPLATE TOGGLE [ID]
```

`TEMPLATE ADD forestpath here` stores the room you stand in under the ID `FORESTPATH`. After that `CREATE ROOM east forestpath` (or `WORLD DIG ON TYPE=forestpath`) makes copies. `TEMPLATE ADD myDoor east` stores the exit to the east, and `CREATE EXIT north myDoor` uses it. `TEMPLATE TOGGLE` shares or unshares an entry. IDs are upper-cased. (The shipped help for `TEMPLATE` shows `TEMPLATE TEMPLATE ADD`; that is a typo, the word is `TEMPLATE ADD`.) Needs one of `CMDITEMS`, `CMDROOMS`, `CMDEXITS`, `CMDMOBS`.

### COPY a room

`COPY` clones rooms with their contents instead of building from a locale.

```text
COPY [direction]
COPY [number] [direction]
COPY [room ID] [direction]
COPY ROOM [direction1] [direction2]
COPY [area name]
```

| Form | Result |
| --- | --- |
| `COPY east` | Copies the room you are in into the room to the east (a new room, linked both ways). |
| `COPY 5 east` | Does that five times in a row, each copy east of the last, giving a straight chain of five new linked rooms. |
| `COPY Midgaard#12 north` | Copies any room by ID. |
| `COPY ROOM south east` | Copies the room that lies to the south of you into the east. The word before the directions must be `ROOM` or `EXIT`. |
| `COPY Emberfall` | Copies a whole area as a new area named `Copy of Emberfall` (and `Copy of Copy of Emberfall`... if taken). Every room is renumbered into the new area, and its mobs and items come with it. Needs both `CMDAREAS` and `COPYROOMS`. |

A copied room keeps its title, description, effects, behaviors and the mobs and items in it, but loses its exits; only the link back to you is made. It is refused if a room already exists that way. Needs `COPYROOMS`. The same command copies items and mobs (see their chapters).

### Dig mode and paths (WORLD DIG, WORLD PATH)

For laying out many rooms quickly, builder mode has two commands. Each new room is made the way `CREATE ROOM` makes it, saved, and tagged `TODO` so the world check lists it until you write its description.

```text
WORLD DIG ON (TYPE=[locale or template]) (LINK=TWO-WAY/ONE-WAY)
WORLD DIG OFF
WORLD PATH [directions] (TYPE=[locale or template]) (NAME="title") (DESC="description") (LINK=TWO-WAY/ONE-WAY)
```

- `WORLD DIG ON` switches dig mode on for you. From then on, walking in a direction where the room has no link and no exit makes a new room there first, then walks you into it. `TYPE=` picks a locale class or template; leave it out to copy the class of the room you are leaving. `LINK=ONE-WAY` makes the new room without a link back (default is two-way). `WORLD DIG` alone tells you whether it is on. `WORLD DIG OFF` stops it.
- `WORLD PATH e,e,n,ne` lays a chain of rooms, one for each direction, starting from the room you are in, without moving you. Where a link already exists in that direction it is followed instead of making a new room. `NAME=` and `DESC=` give every new room that title and description (quote values that contain spaces). The directions may be separated by commas or spaces. It stops with a message if a direction is invalid, has an exit but no room, or cannot make a room. It reports `WORLD PATH made N rooms and followed M exits, to Area#ID`.
- Both commands work only in builder mode ("WORLD DIG and WORLD PATH make rooms, so they are for builder mode"). Plain `DIG` is the player's digging command and is unrelated.
- The `TODO` tag is cleared when you give the room a description with `EDIT DESC` (see below).

```text
WORLD PATH e,e,n,n TYPE=forestpath NAME="A forest trail" LINK=ONE-WAY
```

(Directions `ne` etc. need `DIRECTIONS=11`.)

## Editing a room

### MODIFY ROOM

```text
MODIFY ROOM
MODIFY ROOM [field] [value]
MODIFY ROOM [room ID]
```

With no field, `MODIFY ROOM` opens the **room editor** for the room you stand in: a numbered list of fields, then `Edit which?`. Type a number to edit that field, press Enter to leave. Each field is described in [Reference: room editor fields](#reference-room-editor-fields). With `MODIFY ROOM [room ID]` you edit another room without going there. In a grid, edit the parent room (see [Grid rooms](#grid-rooms-and-mazes)).

The one-line form changes one aspect directly:

| Form | Effect |
| --- | --- |
| `MODIFY ROOM NAME [title]` | Sets the title (the line shown in the room header). |
| `MODIFY ROOM DESCRIPTION [text]` | Sets the description. |
| `MODIFY ROOM CLASS [locale]` | Changes the locale class (see below). |
| `MODIFY ROOM ID [number or ID]` | Renumbers the room. |
| `MODIFY ROOM AREA [area name] ([area class])` | Moves the room into another area, or creates a new area for it. |
| `MODIFY ROOM XGRID [n]` / `YGRID [n]` | The grid dimensions, for a grid or maze room. |
| `MODIFY ROOM AFFECTS` / `BEHAVIORS` | Opens the effects or behaviors editor. |
| `MODIFY ROOM [stat] [value]` | Any room stat code: `CLASS`, `DISPLAY`, `DESCRIPTION`, `TEXT`, `AFFBEHAV`, `IMAGE`, `CLIMATE`, `ATMOSPHERE`, `ROOMID`, `TAGS`. |

A wrong field name prints the list of valid ones: `NAME,AREA,DESCRIPTION,AFFECTS,BEHAVIORS,CLASS,XGRID,YGRID,[ROOM ID]` plus the stat codes. The one-line form has no `ID` in that message but does accept it. Needs `CMDROOMS`. Save to the database is immediate.

**Stat code values.** `CLIMATE` takes a number or the names `NORMAL`, `WET`, `COLD`, `WINDY`, `HOT`, `DRY`, `VOID` combined with `|` (for example `WET|HOT`); `-1` means inherit from the area. `ATMOSPHERE` takes a raw material name such as `AIR` or `FRESHWATER` (a number works as well), or `-1` to inherit. `TAGS` takes a comma-separated list and *replaces* all tags. `TEXT` is the room's raw property text.

### Changing a room's locale class

`MODIFY ROOM CLASS [locale]` and the editor's field 1 ("Type") swap the room for a new one of the other class, keeping its ID, title, description, exits, mobs and items. The editor asks you to confirm, and warns that it permanently saves any mobs and items in the room. Use `LIST LOCALES` for the names.

### Titles and descriptions

A room has a **title** (display text, the short header, limited to 253 characters by the editor) and a **description**. Both support colour codes and `<VARIES>` text that changes by weather, season, time of day, who is looking and more; see [Presentation and language](11-presentation-language.md). In the editor, the description prompt accepts `+` to open the multi-line editor, and a description that already has line breaks opens it automatically. `EDIT DESC HERE` opens the multi-line editor for the room you are in; the new text is saved and the `TODO` tag a dug room carries is cleared.

```text
EDIT DESC HERE
EDIT DESC [mob or item here or carried]
EDIT DESC AREA
```

### Grid rooms and mazes

A **grid locale** is one room that virtually contains a rectangle of child rooms of one locale class. The children exist only inside it; they link to each other and to the exits of the parent. Mazes are grids whose children are linked as a random maze. **Thin** grids create their children on demand as people walk in, and let them expire, so they cost almost nothing; use them for big wilderness.

- Set the size with `MODIFY ROOM XGRID [n]` and `MODIFY ROOM YGRID [n]`, or fields "Size (X)" and "Size (Y)" in the editor. The default is 5 by 5.
- All children are the same locale class: `PlainsGrid` fills with `Plains`, `StoneMaze` with `StoneRoom`, and so on. [Reference: locale classes](#reference-locale-classes) shows which child each grid uses.
- The parent room's title and description are used for the children. Put `<P>` between alternatives to give each child a randomly chosen one: `A dusty corridor<P>A narrow corridor<P>A cobwebbed corridor` as the title, and likewise for the description. If the number of titles and descriptions is the same they are matched up.
- Behaviors and effects added to the parent room apply to the whole grid.
- You cannot modify, link or destroy a child directly ("invalid from within a GridLocaleChild room"): use `GOTO PARENT` first. Use `RESET ROOM` after changing the parent's text so the children are rebuilt.
- To connect a grid to the rest of the world, give the parent room exits as normal; the engine joins the right edge rooms to them, and `LINK` records the grid-side cross exits for you. You can link to a specific child with `LINK ParentID#(x,y) direction`.
- Grids hold the sky or water above and below an outdoor room too: see [SKYSIZE](#skysize-and-the-sky-and-water-below).
- `DISABLE=FATGRIDS` in `coffeemud.ini` makes the engine use the thin version of any grid class that has one.

### SKYSIZE and the sky and water below

Outdoor rooms (not indoors, not underwater, not in the air) get an automatic grid of sky above them, and water-surface locales get a grid of underwater rooms below. The size comes from `SKYSIZE` in `coffeemud.ini` (default `3`, a 3x3 grid; `0` turns the sky off; a leading `-` hides the exit into the grid). This is why `CREATE ROOM up` and `down` are rarely needed for open country. See [World simulation](10-world-simulation.md).

## Linking rooms and working with exits

### LINK and UNLINK

```text
LINK [room ID or room text] [direction]
UNLINK [direction]
```

`LINK midgaard#3001 north` makes the room you stand in lead north into that room. The new link uses a `StdOpenDoorway` exit unless the direction already has an exit. If the target room has nothing yet in the opposite direction, the engine also links it back, using the same exit object; if the opposite slot is occupied, the link you made is one-way, and the command says "Opposite room already exists and heads this way. One-way link created." when it points back at you already. A bad ID gives `Room "x" is unknown. Try again.` In an outdoors-only ship area, `LINK outside [direction]` links to the ship's own location. You can link to a grid child (`Area#12#(3,2)`). Needs `CMDEXITS`.

`UNLINK north` removes the link and exit to the north *from this room only*. The room to the north keeps its link back, so you may need to go there and `UNLINK south` too. It is the same command as `DESTROY ROOM north`. Needs `CMDEXITS`.

### CREATE EXIT

```text
CREATE EXIT [direction] [exit class or template]
CREATE [exit class] [direction]
```

Replaces the exit in that direction with a new one. The rooms must already be linked; this changes how you get through, not where it goes.

```text
CREATE EXIT north Door
create LockedGate west
CREATE EXIT east GenDoor
```

- If the room beyond links back and has an exit there too, that far-side exit is replaced with a copy of the new one. If the far side has no exit, or no link back, only your side changes. (For separate treatment of each side, `DESTROY EXIT` one side first.)
- If you create a generic exit (`GenDoor`, `GenExit`) over an existing generic exit of the same class on both sides, the engine copies the old far-side exit and puts you straight into the exit editor on the copy, so you can adjust it.
- Creating a *generic* exit class does not prompt you; follow it with `MODIFY EXIT [direction]` to name it, set its locks and text.
- An exit template (`TEMPLATE ADD [id] [direction]`) can be used instead of a class.
- Needs `CMDEXITS`. Refused in a grid child.

### MODIFY EXIT

```text
MODIFY EXIT [direction]
MODIFY EXIT [direction] TEXT [value]
MODIFY EXIT [direction] [field] [value]
```

For a **generic** exit (`GenExit`, `GenDoor`, `GenCurtain`, `GenAirLock`, `Impassable`), `MODIFY EXIT north` opens the exit editor ([Reference: exit editor fields](#reference-exit-editor-fields)). For a **standard** exit class (`Door`, `LockedDoor`, `Open`...) there is nothing to edit except its text, so you must give a field: `MODIFY EXIT north TEXT mykey` sets the exit's text, which is the key code for locked standard doors and the name or phrase for `OpenNameable`, `OpenDescriptable` and `OpenPrepositional`. For a generic exit the one-line form also takes any stat code (`NAME`, `DESCRIPTION`, `DOOR`, `HASADOOR`, `DEFLOCKED`, `KEYNAME`...). Changing an exit updates *every* room that uses the same exit object, then saves them. Needs `CMDEXITS`.

To turn an ordinary exit into an editable one: `CREATE EXIT north GenDoor`.

### DESTROY EXIT

```text
DESTROY EXIT [direction]
```

Removes the exit object in that direction (this side only). The *link* to the room stays. In the editor the direction then shows no exit; a character cannot pass it, though a builder using `GOTO` still can. A removed exit on one side does not touch the other side. Use `CREATE EXIT [direction] [class]` afterwards to fit a different exit. A link with no exit is reported by `WORLD CHECK`; an exit with no link is an "exit to nowhere" (errors in the check).

### One-way links and exits that lead nowhere

- A **one-way link**: link A to B and have B's opposite slot filled (or unlinked). `LIST ONEWAYDOORS` finds them. Use them for chutes, one-way doors or trapdoors. `WORLD CHECK EXITS` warns only when a *door* has no way back; an open one-way walkway is not a warning.
- Use `LINK` to point a direction at a room in **another area** (cross-area links are ordinary). `WORLD MAP FLAGS` marks them `@`.
- An exit with a missing destination ("exit to nowhere") shows in `LIST UNLINKEDEXITS` and as an `EXITS` error in `WORLD CHECK`. When a room is destroyed, the exits that led to it keep its ID in their key name as `{#Area#12#}` so you can see where they used to go.

### Hidden, unseen and special exits

The exit's **disposition** flags control who sees and uses it. `HiddenWalkway` and `HiddenClosedDoorway` are hidden: a character who cannot see hidden things neither sees the exit nor, as a wandering NPC, uses it. `UnseenWalkway` is "not seen" (it does not appear in the exit list). Add the same flags to a generic exit in the editor under Dispositions: `invisible`, `hide`, `unseen`, `magical`, `glowing`, `lightsrc`, `fly`, `climb`, `sleep`, `sit`, `sneak`, `swimmer`, `evil`, `good`, `unattackable`. `ClimbableExit` carries the `Prop_Climbable` property, `FlyingExit` the flying flag, `GapExit` a jump that can fail with a fall, `PitOpen` a pit trap, `TrappedDoor` and friends a trap. Full list: [Reference: exit classes](#reference-exit-classes). Properties like `Prop_ReqEntry` (see the [properties reference](23-properties-reference.md#prop_reqentry)) add entry requirements to any exit or room.

## Doors, locks and keys

A generic exit has these door settings (the editor field "Has a door"):

| Setting | Meaning |
| --- | --- |
| Has a door | The exit can be opened and closed. |
| Defaults closed | The door starts closed (and returns to closed). |
| Has a lock | The door can be locked and unlocked with a key. Needs a door. |
| Defaults locked | The door starts locked (and returns to locked). |
| Reset delay (ticks) | How long after someone opens a door that defaults closed or locked it takes to close again and, if it defaults locked, lock again. Default 45 ticks. |

Standard classes bundle these: `Door` and `StdClosedDoorway` (door, closed), `LockedDoor` and `StdLockedDoorway` (door, lock, locked), `Gate`, `LockedGate`, `Grate`, `AjarDoor` (a door that starts open). See [Reference: exit classes](#reference-exit-classes).

### Making a locked door

```text
CREATE EXIT north GenDoor
MODIFY EXIT north
```

In the exit editor set: Has a door `Y`, Defaults closed `Y`, Has a lock `Y`, Defaults locked `Y`, then the key name, for example `guild.gate`. Both sides: the engine's `CREATE EXIT` already copied the exit to the far side; if you modified only one side, run `MODIFY EXIT south` from the other room too, or recreate the pair. For standard classes, `CREATE EXIT north LockedDoor` then `MODIFY EXIT north TEXT guild.gate`.

### Keys

A key is an item of class `StdKey` (or `GenKey`, `StdInnKey`) whose key code (its readable text, in the item editor "Key code") matches the lock's key name. Create it with `CREATE ITEM StdKey`, then set the code. The rules ([Keys](../../CoffeeMud/com/planet_ink/coffee_mud/core/Keys.java)):

- Codes compare ignoring case.
- A key's code may be a **list** separated by semicolons (`crypt;vault`): it opens any lock named one of them.
- A code ending in `*` is a **master key** for every lock whose name begins with the rest: `jail.*` opens `jail.cell1`.
- A lock with the `Prop_LockRules` property and a `KEYS=a;b` parameter also accepts keys coded `a` or `b`.
- A key with the `Prop_KeyRules` property and a `MASK=` zapper mask works only for characters passing the mask.
- A key may sit loose in the inventory or in a keyring (a container that holds keys), however deep.
- A lock with an **empty** key name cannot be opened by any key.
- The unlock command finds the right key for you (`UNLOCK north`); you do not need to name it.

Item details, including keyrings, are in [Items](22-items.md). `WORLD CHECK` (the `KEYS` part) reports a locked door or container whose key exists nowhere in the world (rooms, inventories, shops, catalog).

### Behaviour of doors in play

- Opening, closing, locking or unlocking a door from one side also acts on the paired exit on the far side (the engine looks it up with `getPairedExit`), so a door stays consistent from both rooms.
- After a door that defaults closed or defaults locked is opened, it resets after the delay ticks.
- A character moving along a route can have the door opened for them: wandering NPCs open doors and unlock them if they carry the key.
- `GOD NOCLIP` (and a builder's `GOTO`) walks through closed and locked doors; see [Configuration and administration](19-configuration-and-administration.md).

### Readable exits and exit messages

A generic exit is also a thing that can be looked at, and the editor lets you set the words people see:

| Field | When it shows |
| --- | --- |
| Display | What `LOOK` / the exit list shows for the open exit. Empty means the exit is "see-through" and the exit list shows the destination room's title. |
| Description | What `EXAMINE north` / `LOOK north` shows. |
| Closed Text | The exit-list text while a door is closed (`a closed door`). |
| Door Name | What the door is called in messages: "The *door* is already open." |
| Open Word | The verb for opening (`open`, or `remove` for a grate). |
| Close Word | The verb for closing. |
| Key name (Assigned Key Item) | The lock's key code. |
| Readable text | If the exit is readable (a door with no lock): the text `READ` shows. |

## Areas

### Creating an area

```text
CREATE AREA [area name]
CREATE AREA [area name] @ [x,y,z or distance FROM space object]
MODIFY ROOM AREA [new area name]
```

`CREATE AREA Emberfall` fails if an area (or ship) of that name exists. It then asks `Enter an area type to create (default=StdArea)`: press Enter for `StdArea`, or give a class from [Reference: area classes](#reference-area-classes); a bad answer lists the valid ones. The new area gets one room, `Emberfall#0` (a `StdRoom`, or a `SpacePort` for space areas) with the default title `StdRoom-Emberfall#0`, and you are moved into it. The area's automatic properties from `coffeemud.ini` are added (see [Area properties and behaviors](#area-properties-and-behaviors)). Needs `CMDAREAS`.

Space areas (a class that is also a space object: `StdPlanet`, `StdThinPlanet`, `Planet`, `Moon`) can be placed with `@`: three comma-separated distances from the core of the galaxy, or `[distance] FROM [space object]`. If you leave it out, the area is given random coordinates. See [Travel, vehicles and instances](30-travel-vehicles-instances.md).

`MODIFY ROOM AREA [name]` is the other way in. It moves the room you stand in to the named area, or, if there is no such area, offers to create one ("This command will create a BRAND NEW AREA ... Are you SURE?"), asks for its type, and renumbers the room for its new area. If you add the area class as the last word it skips the questions: `MODIFY ROOM AREA Emberfall StdArea`. Rooms you create afterwards from that room belong to the same area.

### MODIFY AREA

```text
MODIFY AREA
MODIFY AREA [area name]
MODIFY AREA [field] [value]
MODIFY AREA [area name] [field] [value]
```

With no field, `MODIFY AREA` opens the **area editor** for your area ([Reference: area editor fields](#reference-area-editor-fields)); with an area name it edits that area. The one-line forms:

| Form | Effect |
| --- | --- |
| `MODIFY AREA NAME [name]` | Renames (see [Renaming an area](#renaming-an-area)). |
| `MODIFY AREA DESC [text]` | Description. |
| `MODIFY AREA CLIMATE [letters]` | Sets the climate from letters: `R` rainy/wet, `H` hot, `C` cold, `W` windy, `D` dry, `N` normal. Combine them (`RC`). See below for a bug. |
| `MODIFY AREA FILE [path]` | The area's archive path (used as the file name by `EXPORT`). |
| `MODIFY AREA PASSIVE / ACTIVE / FROZEN / STOPPED` | Sets the area's running state. |
| `MODIFY AREA XGRID [n]` / `YGRID [n]` | Grid dimensions for a grid area. |
| `MODIFY AREA ADDSUB [name]` / `DELSUB [name]` | Adds or removes area staff (needs an existing user name). |
| `MODIFY AREA AFFECTS` / `BEHAVIORS` | The effects or behaviors editor. |
| `MODIFY AREA CALENDAR [KEY=VALUE ...]` | Gives the area its own calendar. See [Calendars](#calendars-and-climate-profiles). |
| `MODIFY AREA WEATHERPROFILE [name / NONE]` | Gives the area a weather profile file. |
| `MODIFY AREA [stat] [value]` | Any area stat code: `CLIMATE`, `DESCRIPTION`, `TEXT`, `THEME`, `BLURBS`, `PREJUDICE`, `BUDGET`, `DEVALRATE`, `INVRESETRATE`, `IGNOREMASK`, `PRICEMASKS`, `ATMOSPHERE`, `AUTHOR`, `NAME`, `PLAYERLEVEL`, `PASSIVEMINS`, `CURRENCY`, `AFFBEHAV`, `TAGS`, `REPUTATION`. |

A wrong field prints the valid ones. Needs `CMDAREAS`. The area is saved to the database straight away.

Notes on the stat-code values (all verified in [StdArea.setStat](../../CoffeeMud/com/planet_ink/coffee_mud/Areas/StdArea.java)):

- `THEME` takes names from `FANTASY`, `TECH`, `HEROIC`, `SKILLONLY` combined with `|` (or a number).
- `BLURBS`: `+FLAG text` adds a blurb flag, `-FLAG` removes one, anything else replaces them all.
- `CLIMATE` and `ATMOSPHERE` are as for rooms.
- `PASSIVEMINS` is the number of minutes without a character in the area before it goes passive.
- `PRICEMASKS` is a comma list of the item pricing adjustments.
- `INVRESETRATE` takes an integer expression.
- `TAGS` replaces all tags.

**Known problems with `MODIFY AREA CLIMATE`.** The `D` (dry) letter in the one-line command sets the *windy* bit, not dry (`Modify.java`); and the editor's climate screen lists `V` (void) but does not accept it as a choice. Use the stat-code form for exactness: `MODIFY AREA CLIMATE DRY|HOT`.

### Renaming an area

Changing an area's name (field 1, or `MODIFY AREA NAME`) asks "Is changing the name of this area really necessary (y/N)?". Answer `Y` and the area and *all of its rooms* are renumbered (`OldName#5` becomes `NewName#5`) wherever the new ID is free; other areas' links are repaired. Answer `N` and the old name is kept. Because room IDs appear in start rooms (`START=`), scripts, quests, tattoos and JavaScript, rename early or search for the old name afterwards. Renaming also renames the world file the next `SAVE` writes.

### Area properties and behaviors

Areas hold **effects** (properties) and **behaviors**, exactly as rooms do: choose field *Behaviors* or *Effects* in the editor, or `MODIFY AREA BEHAVIORS`. At the effect prompt: an ID adds an effect, `-ID` removes it, `=ID` (or its number) edits its parameters in place with the current text shown, `?` lists everything, `?P` lists only the properties and effects that fit. A second copy of an effect already present is offered `Prop_MultiEffects`. Behaviors use the same prompt without `=`.

Typical area additions: weather behaviors, law and conquest behaviors (`Arrest`), `Prop_ReqCapacity`, theme-like properties. All are listed in the [properties reference](23-properties-reference.md) and [behaviors reference](24-behaviors-and-masks.md). Two `coffeemud.ini` keys add some to every new `StdArea` automatically:

| Key | Effect |
| --- | --- |
| `AUTOWEATHERPARMS` | If not `NO`, the `WeatherAffects` behavior is added (not saved to the file) to every `StdArea`, using this value as its parameters. Default empty (added with default parameters). |
| `AUTOAREAPROPS` | A semicolon list of properties and behaviors, each `ID` or `ID(parameters)`, added to every new `StdArea`, e.g. `Prop_ReqCapacity(weight=1000);Arrest(custom)`. Default empty. |

### Parent and child areas

An area can have **parents** and **children**, forming a tree (no cycles; a ship cannot be one). A child inherits from its parents anything it leaves as *Inherited*: climate, atmosphere, theme, currency, prejudice, pricing and the calendar (see below), and behaviors and effects on the parent affect the child's messages. Messages go up the tree, not down. In the area editor, the *Parent Areas* and *Children areas* fields take an area name to add it or remove it (typing a name already related removes it); `*clear*` removes all; the engine refuses a circular reference. `DEFAULTPARENTAREA=` in `coffeemud.ini` gives every area that uses the default clock a common parent, a convenient way to apply a property to a whole world.

An `StdTimeZone` or `StdPlanet` parent also gives its children its own calendar.

### Theme, atmosphere and climate

- **Theme** (field "Theme setting") says which of three rule-sets are allowed: Fantasy (magic), Technical, Heroic (superpowers). Values: Inherited, Fantasy, Technical, Fantasy & Technical, Heroic, Heroic & Fantasy, Heroic & Technical, All Allowed. In an area whose theme has no Fantasy bit, magic fails ("Magic doesn't seem to work here"); with no Heroic bit, superpowers fail ("Powers don't seem to work here"); with Heroic but no Technical bit, technology devices refuse to work. Inherited takes the parent's theme, or at the top `MUDTHEME` (shipped `1`, Fantasy).
- **Climate** is a set of bits: wet, cold, windy, hot, dry, void, or Normal, or Inherited. The default for a new area is Normal. It shifts the weather the [simulation chapter](10-world-simulation.md) generates and the movement cost (rooms add their own bits: a `Jungle` is wet and hot regardless of the area).
- **Atmosphere** is the raw material the area breathes ("AIR", "FRESHWATER", "NOTHING"...). Inherited by default. A character who cannot breathe it suffers.

### Calendars and climate profiles

`MODIFY AREA CALENDAR HOURS=10 DAYS=30 MONTHS="Leafwake,Bloomtide" DAWNHR=0 DAYHR=1 DUSKHR=8 NIGHTHR=9 START=812/2/3/1` gives an area (and its children) its own calendar, stored in the area's data. `MODIFY AREA CALENDAR` alone shows it, `NONE` removes it. The editor's calendar screen offers the same settings by number (hours per day, dawn, day, dusk and night hours, days per month, weekdays, months, year titles, or `global` to go back to the world clock). `MODIFY AREA WEATHERPROFILE [name]` assigns `world/climates/[name].ini`. Both are specified in [World simulation](10-world-simulation.md).

### Area state

An area is `ACTIVE` (mobs move, threads run), `PASSIVE` (running but its mobs do not move; a lone area drops to this after the `PASSIVEMINS` period, 30 minutes by default, with no character in it, and becomes active again when one enters), `FROZEN` (ticking but nothing happens; movement into and out of the area is refused) or `STOPPED`. Builders rarely need to change it. The `MODIFY AREA PASSIVE/ACTIVE/FROZEN/STOPPED` forms exist for testing.

### Staff

Area staff names (`MODIFY AREA ADDSUB name`) give a user area-level privileges where their security flags say so. In CoffeeSUD's single-player game this is rarely used; the name must be an existing user.

### Economy fields

The tail of the area editor holds the shop economy: currency, prejudice, item pricing factors, budget, devaluation rate, inventory reset rate and ignore mask. Their effects are explained in [Society](09-society.md).

## Copying, moving and destroying

### DESTROY ROOM

```text
DESTROY ROOM [room ID] (CONFIRMED)
DESTROY ROOM [direction]
```

With a room ID, destroys that room completely and permanently, including mobs and items stored in it, from the database too. You must not be standing in it. Without `CONFIRMED` it asks `Are you ABSOLUTELY SURE (y/N)`. Every link *into* it from other rooms is cut (their destination is cleared; a generic exit there remembers the dead room's ID). The command needs `CMDROOMS` for that room. With a direction instead of an ID it only unlinks, as `UNLINK` does (needs `CMDEXITS`).

`JUNK` and `TEAR` are aliases of `DESTROY`.

### DESTROY AREA

```text
DESTROY AREA [area name] (CONFIRMED)
DESTROY AREA ALL EXCEPT [area] [area]...
```

Obliterates an area and all its rooms (and their mobs and items). You cannot destroy the area you are standing in ("You dip! You are IN that area!"). Without `CONFIRMED` it asks `OBLITERATE IT???`. `DESTROY AREA ALL EXCEPT ...` destroys every area not listed. In builder mode the area's `.cmare` file is removed by the next `SAVE`; `WORLD BACKUPS` keeps the previous copy. Use `UNDO` right away if it was a mistake and it is still in the undo log.

### Moving things

- **A room to another area:** `MODIFY ROOM AREA [name]` (renumbers it, keeps its links).
- **A room's contents to another room:** `TRANSFER` moves mobs and items; see [Mobs](21-mobs.md) and [Items](22-items.md).
- **A mob's or item's home:** save it into the right room (`SAVE ROOM`).
- To move a whole region, copy the area (`COPY [area]`), or write new IDs with `GMODIFY` or by editing the `.cmare` file.

### UNDO and RESET

`UNDO` and `UNDO LIST (n)` revert the last fifty building commands, including `CREATE`, `MODIFY`, `DESTROY`, `COPY`, `GMODIFY`: each affected room is rebuilt as it was and saved. `RESET ROOM` and `RESET AREA` reload the room or area from the database, throwing away unsaved live changes (and rebuilding a grid after you change its text). See [World files and editing](02-world-workflow.md).

## Bulk tools

### GMODIFY

`GMODIFY` searches objects and optionally changes them: rooms, exits, areas, mobs, items.

```text
GMODIFY (AREA / ROOM / WORLD / #TAG) [FIELD=VALUE](&&/|| [FIELD=VALUE]...) (CHANGE=[FIELD=VALUE](&&[...]))
GMODIFY ?
```

- **Scope.** The word after `GMODIFY` picks where to look. `ROOM` is the room you stand in; `AREA` is your whole area; `WORLD` (or no scope word) is the whole world; `#TAG` searches objects carrying that tag. `WORLD` needs `GMODIFY` everywhere.
- **Criteria (`WHEN=`).** Fields joined by `&&` (and) or `||` (or). Operators: `=`, `!=`, `>`, `<`, `>=`, `<=` and `$` (regular expression; the pattern must match the whole value unless you add `[ss]`). The value may begin with `[ss]` (match a *substring*) and/or `[cs]` (case sensitive; the default is case-insensitive).
- **Changes (`CHANGE=`).** Fields joined by `&&`. Each is set to the value on every object that matched. `DESTROY=true` obliterates the matched object. If a field appears in both the criteria and the change and both values start with `[ss]`, only the matched part of the text is replaced.
- **No `CHANGE=`** means search only: it prints `Matched on [name] from [room ID].` for each hit and changes nothing. Do this first, always.
- `GMODIFY ?` lists every valid field name.

What objects does it look at? For each room in scope, in order: the **area** of the first room it meets in that area (once per area), the **room**, its **items**, its **mobs** (with their inventories and shop stock), and its **exits**. A criterion on a field an object does not have simply fails to match (for instance `KEYNAME` matches only exits). Use `CLASS=` to restrict by type: `class=GenDoor`, `class=GenMob`.

**Exit and area fields are searched and changed too**, and exits are saved with their room (`DBUpdateExits`):

| Object | Typical fields |
| --- | --- |
| Exit (generic) | `CLASS`, `NAME`, `DISPLAY`, `DESCRIPTION`, `DOOR`, `LEVEL`, `ABILITY`, `ISREADABLE`, `DISPOSITION`, `READABLETEXT`, `HASADOOR`, `DEFCLOSED`, `HASALOCK`, `DEFLOCKED`, `KEYNAME`, `RESETTIME`, `CLOSEWORD`, `OPENWORD`, `CLOSEDTEXT`, `TAGS`, `ADDBEHAVIOR`, `ADDAFFECT`... |
| Room | `CLASS` (changes the locale), `DISPLAY`, `DESCRIPTION`, `CLIMATE`, `ATMOSPHERE`, `ROOMID`, `TAGS`, `IMAGE`, `TEXT`, `AFFBEHAV` |
| Area | `NAME`, `DESCRIPTION`, `CLIMATE`, `THEME`, `AUTHOR`, `PLAYERLEVEL`, `CURRENCY`, `BUDGET`, `PREJUDICE`, `TAGS`, `REPUTATION`... |
| Everything | `DESTROY`, `ADDBEHAVIOR`, `DELBEHAVIOR`, `ADDAFFECT`, `DELAFFECT`, PhyStats fields (`LEVEL`, `ABILITY`, `WEIGHT`...) |

`ADDAFFECT=Prop_ReqEntry(MASK=...)` and `ADDBEHAVIOR=Scriptable(...)` take `ID(parameters)`.

Examples:

```text
gmodify area class=GenDoor change=hasalock=true
gmodify area class=GenDoor&&name=[ss]wooden door change=name=[ss]stone door
gmodify room name=[ss]door change=closedtext=a heavy door stands shut
gmodify area class=Woods change=class=Jungle
gmodify world tags$.*TODO.*
gmodify area desc=[ss]Lord Varn change=desc=[ss]Lady Varn
```

(The fourth and fifth lines assume the field names `class`, `tags` and `desc`/`description` as the rooms' stat codes spell them. Run the search form first and read the hits.)

**Cautions.**

- With `CHANGE=` but no criteria at all, *every* object in scope with that field matches. GMODIFY does not stop you. Always supply a `class=` or `name=` filter.
- A `CHANGE=` pass resets each room it visits (`RESET ROOM`) before working, which drops mobs and items you have not saved. In builder mode it asks first if there are unsaved changes (`LIST UNSAVED`); answer `N` and `SAVE CHANGED`.
- Changes are applied to the live objects *and saved*. There is `UNDO`; also commit your world first.
- When it finishes it sets every area it touched back to `ACTIVE`, even one you had made passive or frozen.
- Regular expressions (`$`) must match the whole field unless you add `[ss]`. The help example `gmodify area name$wooden` therefore matches only an area named exactly `wooden`.
- Selecting `players` is for characters, not geography.

### MQL (SELECT / UPDATE / DELETE)

`LIST AREA SELECT: ...`, `MODIFY AREA UPDATE: ...` and `DESTROY AREA DELETE: ...` run MQL queries over areas, rooms, exits and so on. They are for experts. Exit and area changes made by `UPDATE:` are saved with their room and area. See `AHELP MQL`.

### WORLD MAP

```text
WORLD MAP (RADIUS [n]) (AREA) (IDS) (FLAGS) (HTML=[file])
```

Draws a map. `WORLD MAP` alone draws the rooms within 5 steps of you (north is up). `RADIUS 8` (or a bare number) changes the reach. `AREA` draws the whole area. `IDS` labels each room with the number from its ID (or a sequence number if it has more than three digits) and lists them under the map with their titles. `FLAGS` marks problems. `HTML=name` instead writes the area's map as a page, `world/maps/name.html` (a table with each room's title, ID and exits as links to the neighbouring rooms).

The symbols:

| Mark | Meaning |
| --- | --- |
| `*` | The room you are standing in |
| `o` | Another room (or its number, with `IDS`) |
| `-` and `\|` | An exit east-west or north-south |
| `/` and `\` | A diagonal exit |
| `>` `<` `v` `^` | (`FLAGS`) A one-way exit that does not lead back (east, west, south, north) |
| `+` | (`FLAGS`) An exit with a lock |
| `@` | (`FLAGS`) An exit to another area |
| `s` | (`FLAGS`) A room that has a script |

Below the map it lists rooms it could not draw (up, down, or where another room is), and, with `FLAGS`, `! Not reached from here at all:` followed by the area's rooms that nothing leads to from where you stand. The layout comes from walking exits from your room, so a map from a different room of a disconnected area can look different. Available in builder mode, in `DEV=1` games, and to anyone with `CMDROOMS`. Source: [AreaMap](../../CoffeeMud/com/planet_ink/coffee_mud/core/AreaMap.java), [DigMode](../../CoffeeMud/com/planet_ink/coffee_mud/core/DigMode.java), [WorldCmd](../../CoffeeMud/com/planet_ink/coffee_mud/Commands/WorldCmd.java).

### Other bulk and cross-checking commands

| Command | Use |
| --- | --- |
| `WORLD CHECK (AREA [name]) (LOG) (ONLY=part,part)` | The geography lint, below. |
| `WORLD DEV [area] ON / OFF` | Mark an area as dev-only so it is written to `world/dev` and not loaded in a normal game. |
| `WORLD RELOAD [area]` | Load an area from its file again. |
| `GENERATE ...` | Random areas ([Generation](12-generation.md)). |
| `TRAILTO` | Path checking. |
| `LIST LINKAGES`, `ONEWAYDOORS`, `ORPHANS`, `UNLINKEDEXITS` | Connectivity reports (see above). |
| `EXPORT AREA/ROOM ...` and `IMPORT` | Write or read `.cmare` text for an area or room to a file under the install folder (legacy; builder mode's `SAVE` already writes the world files). |

## Checking your geography

`WORLD CHECK` (builder mode, `DEV=1`, or anyone with `CMDROOMS`) lints the world and prints errors then warnings, each tagged with its part. The parts that concern geography:

| Part | Finds |
| --- | --- |
| `EXITS` | Error: an exit with no destination ("an exit to nowhere"). Warning: an exit from a shipped area into a dev area; a door whose far side does not lead back. |
| `REACH` | Error: a start room (`START=` or per-race/class start) that does not exist. Warning: rooms nothing leads to from any start room, reported per area. |
| `KEYS` | Error: a locked door or container whose key is nowhere in the world. |
| `TEXT` | Warning: rooms still with the new-room description, a class's default title or description, or tagged `TODO`. Also mobs and items with default text. |
| `IDS` | Stable-identity problems (see [Testing and release](13-testing-release.md)). |

```text
WORLD CHECK
WORLD CHECK AREA Emberfall ONLY=EXITS,REACH
WORLD CHECK ONLY=KEYS,TEXT LOG
```

The other parts (`SHOPS`, `DATA`, `SCRIPTS`, `PROPS`, `JS`, `VOCAB`, `LORE`, `TEMPLATES`, `ROUTES`, `MODULES`, `COMPOSITIONS`, `COMBAT`, `ITEMMODS`) belong to other chapters. `sud.sh CHECK=1` runs the check on a scratch copy and exits with status 1 on errors, suitable for a commit hook ([Testing and release](13-testing-release.md)).
