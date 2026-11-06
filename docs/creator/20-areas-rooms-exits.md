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
- **Changing an ID.** `MODIFY ROOM ID 25` renumbers the room you are in. The new ID must start with the room's own area name and must be unused. See [MODIFY ROOM](#modify-room).

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
GOTO New Area
GOTO PREVIOUS
GOTO PARENT
AT New Area#1 LOOK
STAT ROOM New Area#1
STAT AREA New Area
EXITS
SYSMSGS
```

| Command | What it does |
| --- | --- |
| `GOTO [room ID / room text / mob or player name / area name]` | Moves you there. An area name takes you to a random room of that area. `GOTO AREA [text]` searches only the area you are in. `GOTO PARENT` leaves a grid child for its grid's parent room. `GOTO PREVIOUS` goes back along your last ten GOTO locations. Needs the `GOTO` flag. Source: [Goto](../../CoffeeMud/com/planet_ink/coffee_mud/Commands/Goto.java). |
| `AT [room ID / room text / PLAYERS] [command...]` | Runs one command as if you stood in that room, then returns you. `AT PLAYERS` runs it in every other player's room; CoffeeSUD is a single-player game, so it rarely finds one. Needs the `AT` flag. |
| `STAT ROOM [name or ID]` | Shows the room's whole editor listing (every field, no prompts). Searches your area first, then the world. |
| `STAT AREA [name]` | The same for an area. |
| `SYSMSGS` | Toggles sysop messages. With it on, `LOOK` first prints `Area  :` (the area name), `RoomID:` (the room ID, and `#(x,y)` inside a grid) and a line with the locale class, its domain, climate words, resource and movement cost, e.g. `Plains: PLAINS wet <> 2mv`. The exit list shows each exit's class, and `LOOK` at an exit shows its `Type` and `Misc` text. Turn it on while building. It needs the `SYSMSGS` flag, and the shipped help warns that a wandering mob cannot enter or leave a room while a character with SYSMSGS on is in it, so turn it off when you test mob movement. |
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
- The locale may also be the ID of a **builder template** (see [Templates](#templates)). A template room is copied whole: title, description, effects, behaviors, tags and the mobs and items in it, but not its exits; it gets a new ID and your current area.
- The command is refused inside a grid child ("This command is invalid from within a GridLocaleChild room") and refused when the direction is not valid for the room ("Room was created without that possible direction" for grids that lack it, or "A room may not be created in that direction" at the edge of a grid area).
- Creating a room in a direction that already has one does not stop you, but the result depends on the old link. If the room there links back to you, the engine still creates and saves the new room but cannot join it ("Opposite room already exists and heads this way."), so the new room is an orphan (`LIST ORPHANS` shows it): link to it with `LINK` from a free direction, or `DESTROY ROOM [room ID]`. If the old room did not link back, your link is simply moved to the new room and the old room is left behind.
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
| `COPY New Area#1 north` | Copies any room by ID. |
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

A wrong field name prints the list of valid ones: `NAME,AREA,DESCRIPTION,AFFECTS,BEHAVIORS,CLASS,XGRID,YGRID,[ROOM ID]` plus the stat codes. (`ID` works although that message leaves it out.) Needs `CMDROOMS`. Save to the database is immediate.

**Stat code values.** `CLIMATE` takes a number or the names `NORMAL`, `WET`, `COLD`, `WINDY`, `HOT`, `DRY`, `VOID` combined with `|` (for example `WET|HOT`); `-1` means inherit from the area. `ATMOSPHERE` takes a raw material name such as `AIR` or `FRESHWATER` (a number works as well), or `-1` to inherit. `TAGS` takes a comma-separated list and *replaces* all tags. `TEXT` is the room's raw property text.

### Changing a room's locale class

`MODIFY ROOM CLASS [locale]` and the editor's field 1 ("Type") swap the room for a new one of the other class, keeping its ID, links, exits, title, description, mobs, items, behaviors and permanent effects. The editor field asks you to confirm and warns that it permanently saves the mobs and items in the room; the one-line command does not ask. Use `LIST LOCALES` for the names.

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

`LINK Emberfall#3 north` makes the room you stand in lead north into that room. The new link uses a `StdOpenDoorway` exit unless the direction already has an exit. If the target room has nothing yet in the opposite direction, the engine also links it back, using the same exit object; if the opposite slot is occupied, the link you made is one-way. If the direction you are linking already led to a room that linked back to you, that room keeps its link back and the command says "Opposite room already exists and heads this way. One-way link created." A bad ID gives `Room "x" is unknown. Try again.` In a ship or other boardable area, `LINK outside [direction]` links to the room the ship sits in. You can link to a grid child (`Area#12#(3,2)`). Needs `CMDEXITS`.

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
- If this room already has an exit and the far room's exit back is a generic exit of the *same class* as the one you ask for, the engine copies the far-side exit, opens the exit editor on the copy, and then puts the edited result on both ends. Running `CREATE EXIT north GenDoor` twice is therefore the way to edit a door on both sides at once.
- Creating a *generic* exit class does not normally prompt you (the same-class case above is the exception); follow it with `MODIFY EXIT [direction]` to name it, set its locks and text.
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

Removes the exit object in that direction (this side only). The *link* to the room stays. Ordinary characters cannot pass a direction that has a link but no exit (the original Archon guide notes that Archons still can). A removed exit on one side does not touch the other side. Use `CREATE EXIT [direction] [class]` afterwards to fit a different exit. A link with no exit is reported by `WORLD CHECK`; an exit with no link is an "exit to nowhere" (errors in the check).

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

In the exit editor set: Has a door `Y`, Defaults closed `Y`, Has a lock `Y`, Defaults locked `Y`, then the key name, for example `guild.gate`. A door has two ends, one exit object in each room, and `MODIFY EXIT` changes the one in the room you stand in (and any other room still sharing that very object, as the two ends of a freshly made room link do until the world is reloaded). There are two dependable ways to get both ends right:

1. Edit once, then repeat the creation: after the first `CREATE EXIT north GenDoor`, run `CREATE EXIT north GenDoor` *again*. Both ends are now a `GenDoor`, so the engine opens the exit editor on a copy of the far end, and when you leave it puts the result on *both* ends.
2. Or run `MODIFY EXIT` in each room, one for each end.

For standard classes, `CREATE EXIT north LockedDoor` replaces both ends (when both had exits), and `MODIFY EXIT north TEXT guild.gate` sets the key code on your end only; go to the other room and run `MODIFY EXIT south TEXT guild.gate` there.

### Keys

A key is an item of class `StdKey` (or `GenKey`, `StdInnKey`) whose key code matches the lock's key name. A `GenKey` has the field *Assigned Key Code* in its editor; a `StdKey` keeps the code in *Misc Text*. Create one with `CREATE ITEM GenKey` and set the code with `MODIFY ITEM` ([Items](22-items.md)). The rules ([Keys](../../CoffeeMud/com/planet_ink/coffee_mud/core/Keys.java)):

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

Notes on the stat-code values (from [StdArea.setStat](../../CoffeeMud/com/planet_ink/coffee_mud/Areas/StdArea.java)):

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

Areas hold **effects** (properties) and **behaviors**, exactly as rooms do: choose field *Behaviors* or *Effects* in the editor, or `MODIFY AREA BEHAVIORS`. At the effect prompt: an ID adds an effect, `-ID` removes it, `=ID` (or its number) edits its parameters in place with the current text shown, `?` lists everything, `?P` lists only the properties and effects that fit. A second copy of an effect already present is offered `Prop_MultiEffects`. The behaviors prompt takes an ID to add a behavior, and an ID already present to remove it; `?` lists them.

Typical area additions: weather behaviors, law and conquest behaviors (`Arrest`), `Prop_ReqCapacity`, theme-like properties. All are listed in the [properties reference](23-properties-reference.md) and [behaviors reference](24-behaviors-and-masks.md). Two `coffeemud.ini` keys add some to every area of class `StdArea` (when it is created or loaded) automatically:

| Key | Effect |
| --- | --- |
| `AUTOWEATHERPARMS` | If not `NO`, the `WeatherAffects` behavior is added (not saved to the file) to every `StdArea`, using this value as its parameters. Default empty (added with default parameters). |
| `AUTOAREAPROPS` | A semicolon list of properties and behaviors, each `ID` or `ID(parameters)`, added to every `StdArea`, e.g. `Prop_ReqCapacity(weight=1000);Arrest(custom)`. Default empty. |

### Parent and child areas

An area can have **parents** and **children**, forming a tree (no cycles; a ship cannot be one). A child inherits from its parents anything it leaves as *Inherited*: climate, atmosphere, theme, currency, prejudice, pricing and the calendar (see below). Behaviors and effects on a parent see, and can veto, events that happen in its child areas, because messages are passed up the tree (never down). In the area editor, the *Parent Areas* and *Children areas* fields take an area name to add it or remove it (typing a name already related removes it); `*clear*` removes all; the engine refuses a circular reference. `DEFAULTPARENTAREA=` in `coffeemud.ini` gives every area that uses the default clock a common parent, a convenient way to apply a property to a whole world.

An `StdTimeZone` or `StdPlanet` parent also gives its children its own calendar.

### Theme, atmosphere and climate

- **Theme** (field "Theme setting") says which of three rule-sets are allowed: Fantasy (magic), Technical, Heroic (superpowers). Values: Inherited, Fantasy, Technical, Fantasy & Technical, Heroic, Heroic & Fantasy, Heroic & Technical, All Allowed. In an area whose theme has no Fantasy bit, magic fails ("Magic doesn't seem to work here"); with no Heroic bit, superpowers fail ("Powers don't seem to work here"). A third gate refuses technology ("Technology doesn't seem to work here"), but the code only reaches it when the theme has the Heroic bit and lacks the Technical bit (see [Troubleshooting and gotchas](#troubleshooting-and-gotchas)), so a plain Fantasy area does not block technology. Inherited takes the parent's theme, or at the top `MUDTHEME` (shipped `1`, Fantasy).
- **Climate** is a set of bits: wet, cold, windy, hot, dry, void, or Normal, or Inherited. The default for a new area is Normal. It shifts the weather the [simulation chapter](10-world-simulation.md) generates and the movement cost (rooms add their own bits: a `Jungle` is wet and hot regardless of the area).
- **Atmosphere** is the raw material the area breathes ("AIR", "FRESHWATER", "NOTHING"...). Inherited by default. It decides what can breathe there; races list what they breathe (see [Characters](03-characters.md)).

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
gmodify room class=GenDoor change=closedtext=a heavy door stands shut
gmodify area class=Woods change=class=Jungle
gmodify #TODO
gmodify area description=[ss]Lord Varn change=description=[ss]Lady Varn
```

In order: the first example gives every generic door in the area a lock; the second renames just the matched words of doors called "wooden door"; the third sets the closed text of every generic door in your room; the fourth changes every `Woods` room in the area to a `Jungle`; the fifth lists every object tagged `TODO` (what `WORLD DIG` and `WORLD PATH` left for you to describe); the sixth rewrites a name inside the descriptions of the area's rooms, mobs and items. Run each without the `change=` part first and read the hits.

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

## Reference: directions

| Code | Name | Letters | Opposite | Needs |
| --- | --- | --- | --- | --- |
| 0 | North | `N` | South | |
| 1 | South | `S` | North | |
| 2 | East | `E` | West | |
| 3 | West | `W` | East | |
| 4 | Up | `U` | Down | |
| 5 | Down | `D` | Up | |
| 6 | "There" (the gate direction) | `V` | itself | |
| 7 | Northeast | `NE` | Southwest | `DIRECTIONS=11` |
| 8 | Northwest | `NW` | Southeast | `DIRECTIONS=11` |
| 9 | Southeast | `SE` | Northwest | `DIRECTIONS=11` |
| 10 | Southwest | `SW` | Northeast | `DIRECTIONS=11` |

`DIRECTIONS` is `7` in the shipped `coffeemud.ini`; `11` adds the four diagonals. It is a world setting (`WORLD SET DIRECTIONS=11` in builder mode, effective at the next start; see [world settings](world-settings.md)). Commands take the full name or the letters, in any case. Ships show the names fore/aft/starboard/port and caravans forward/back/right/left, but the commands still use the compass words. The `V` direction is for portals and special links with no compass sense.

## Reference: locale classes

A **locale** is a room's Java class. `LIST LOCALES` shows what is installed. Each locale fixes five things.

1. **Domain.** Indoors or outdoors, and the kind of terrain.
2. **Climate bits** it adds on top of its area's (`wet`, `hot`...); "inherits" means it adds none.
3. **Movement cost.** The base movement points one pays to enter (weather then adjusts it).
4. **Resources.** Outdoor and cave locales hold a list of resources the room may randomly carry (for gathering skills such as mining, foraging and fishing; see `LIST ENVRESOURCES`).
5. **Room size.** The room's "height" for size checks: 1 for indoor rooms and 10 for outdoor rooms unless the class says otherwise.

### Domains

| Outdoor domain (number) | Notes |
| --- | --- |
| CITY (0) | The default. |
| WOODS (1), PLAINS (3), JUNGLE (7), SWAMP (8), DESERT (9), HILLS (10) | Diggable terrain (the `DIG` command works here). |
| ROCKY (2), MOUNTAINS (11) | Rock and mountain terrain; not diggable. |
| UNDERWATER (4) | No sky. Wandering NPCs enter only if they can swim. Diggable only if there is a room below. |
| AIR (5) | Wandering NPCs enter only if they fly. (`InTheAir`-class rooms also drop non-flyers to the room below.) |
| WATERSURFACE (6) | Wandering NPCs enter only if water-worthy. Diggable only if there is a room below. |
| SPACEPORT (12), SEAPORT (13) | Ports for ships and spacecraft ([travel chapter](30-travel-vehicles-instances.md)). |

Indoor domains (the same numbers plus 128): STONE, WOODEN, CAVE, MAGIC (a pocket), UNDERWATER (`IN_UNDERWATER`), AIR (`GAP`), WATERSURFACE (`CAVELAKESURFACE`), METAL, and the two seaport domains (`CAVE_SEAPORT`, `SEAPORT`). The names in parentheses are what `LIST LOCALES` and sysop `LOOK` print. Only outdoor rooms that are not underwater get a sky (`hasASky`), and weather reaches only rooms with a sky.

### Single rooms

Move is the movement cost. "in" is indoors, "out" outdoors. Rooms marked "size 5" and so on have a larger room size.

| Class | Domain | Climate | Move | Notes |
| --- | --- | --- | --- | --- |
| `CaveRoom` | in cave | normal | 2 | A cave (size 5; resources are stone and ores). |
| `CaveSeaPort` | in cave_seaport | wet | 2 | A cave sea port. |
| `CaveSurface` | in cave | normal | 4 | A climbable cave wall, indoors. |
| `CityStreet` | out city | inherits | 1 | A street. Movement cost 1. |
| `ClimbableLedge` | out rocks | inherits | 4 | A climbable ledge; same behavior as ClimbableSurface. |
| `ClimbableSurface` | out rocks | inherits | 4 | A surface that must be climbed (carries Prop_Climbable). Name "the surface". |
| `DarkStoneRoom` | in stone | normal | 1 | A StoneRoom that is dark: it needs a light source. |
| `Desert` | out desert | hot,dry | 2 |  |
| `FrozenMountains` | out mountains | cold | 5 | Cold mountains. |
| `FrozenPlains` | out plains | cold | 2 | Cold plains (resources include fur). |
| `HideoutShelter` | in wood | normal | 0 | A wooden "Secret Hideout" with the same protections as MagicShelter; cost 0. |
| `Hills` | out hills | inherits | 3 |  |
| `HotRoom` | in stone | hot | 1 | An indoor stone room whose climate is hot. |
| `IcePlains` | out plains | cold,wet | 2 | Cold and wet plains. |
| `IceRoom` | in stone | cold | 1 | An indoor stone room whose climate is cold. |
| `InTheAir` | out air | inherits | 1 | Open air. A character not flying falls (a Falling effect) and may take damage; the game builds a fall to the room below. |
| `IndoorInTheAir` | in air | inherits | 1 | Air inside a cave or building (domain "GAP" in the indoors list). |
| `IndoorShallowWater` | in watersurface | wet | 2 | Shallow water inside. |
| `IndoorUnderWater` | in underwater | inherits | 3 | Underwater inside a cave or building (size 5). |
| `IndoorWaterSurface` | in watersurface | wet | 2 | A water surface inside a cave or building (size 5). |
| `IndoorWaterSurfaceColumn` | in watersurface | wet | 2 | Indoor surface with a column grid below. |
| `IndoorWaterThinSurface` | in watersurface | wet | 2 | Indoor surface; see the audit note about its missing thin grid class. |
| `Jungle` | out jungle | wet,hot | 3 |  |
| `LargeCaveRoom` | in cave | normal | 4 | A big cave (size 8, cost 4). |
| `LargeStoneRoom` | in stone | normal | 3 | A big stone room (size 5, cost 3). |
| `LargeWoodRoom` | in wood | normal | 3 | A big wooden room (size 5, cost 3). |
| `LongRoad` | out plains | inherits | 5 | A road that costs 5 to cross. |
| `LongerRoad` | out plains | inherits | 10 | A road that costs 10 to cross. |
| `LongestRoad` | out plains | inherits | 20 | A road that costs 20 to cross. |
| `MagicFreeRoom` | in stone | normal | 1 | A stone room with Prop_MagicFreedom (magic fails here). |
| `MagicShelter` | in magic | normal | 0 | A safe pocket: Prop_PeaceMaker, NoRecall, NoSummon, NoTeleport, NoTeleportOut; cost 0. |
| `MetalRoom` | in metal | normal | 1 | A metal-walled room (ships, stations). |
| `MountainSurface` | out mountains | inherits | 6 | A climbable mountain face, cost 6. |
| `Mountains` | out mountains | inherits | 5 |  |
| `OverTheLedge` | out air | inherits | 1 | Air beyond a ledge (an InTheAir room). |
| `Plains` | out plains | inherits | 2 | Open country. |
| `Road` | out plains | inherits | 1 | A road. Movement cost 1. |
| `SaltWaterSurface` | out watersurface | wet | 2 | Sea surface (salt water; not drinkable); builds a salt underwater grid. |
| `SaltWaterSurfaceColumn` | out watersurface | wet | 2 | Salt surface with a column grid below. |
| `SaltWaterThinSurface` | out watersurface | wet | 2 | Salt surface with a thin grid below. |
| `SeaPort` | out seaport | wet | 2 | A shore room that is a sea port (domain SEAPORT), for ships; see the travel chapter. |
| `SewerRoom` | in cave | wet | 2 | A sewer; wet; domain cave. |
| `ShallowWater` | out watersurface | wet | 2 | Wading water, outdoors (drinkable fresh water). |
| `ShipDeck` | out city | inherits | 1 | A WoodenDeck with `Prop_ReqCapacity` `items=20 weight=300 siegeweapons=0`. |
| `ShipHeavyGunDeck` | out city | inherits | 1 | A deck with `Prop_ReqCapacity` `items=20 weight=1000 siegeweapons=3`. |
| `ShipHold` | in wood | normal | 1 | A wooden room with `Prop_ReqCapacity` `items=100 weight=10000`. |
| `ShipLightGunDeck` | out city | inherits | 1 | A deck with `Prop_ReqCapacity` `items=20 weight=400 siegeweapons=1`. |
| `ShipMagazine` | in wood | normal | 1 | A wooden room with `Prop_ReqCapacity` `items=200 weight=1000`. |
| `ShipMedGunDeck` | out city | inherits | 1 | A deck with `Prop_ReqCapacity` `items=20 weight=600 siegeweapons=2`. |
| `ShipQuarter` | in wood | normal | 1 | A wooden room with `Prop_ReqCapacity` `items=100 weight=1000`. |
| `Shore` | out desert | wet | 2 | The shore; wet. Name "the shore". |
| `SpacePort` | out spaceport | inherits | 1 | A spaceport: outdoors SPACEPORT domain, a LocationRoom (has a direction from the planet core). |
| `StdRoom` | out city | inherits | 2 | The default locale. Cheap to create; behaves as a generic outdoor place. |
| `StoneRoom` | in stone | normal | 1 | A plain stone room. The usual dungeon or building room. |
| `Swamp` | out swamp | wet | 3 |  |
| `ThinRoom` | out city | inherits |  | Internal placeholder used by thin areas; do not create it. |
| `TreeSurface` | out woods | inherits | 4 | A climbable tree, cost 4. |
| `UnderSaltWater` | out underwater | wet | 3 | Underwater, salt water. |
| `UnderWater` | out underwater | wet | 3 | Underwater, fresh water. Needs swimming or water breathing; items sink. |
| `UndergroundCityStreet` | in stone | normal | 2 | An underground street (a LargeStoneRoom, cost 2). |
| `UnderwaterCityStreet` | out underwater | wet | 3 | An underwater street (size 5, cost 3). |
| `Void` | out air | void | 1 | Out of the world: air domain, atmosphere NOTHING, climate VOID (no weather). |
| `WaterSurface` | out watersurface | wet | 2 | Fresh water surface, swimmable and drinkable; makes an underwater grid below it. |
| `WaterSurfaceColumn` | out watersurface | wet | 2 | WaterSurface that builds a column-shaped underwater grid below. |
| `WaterThinSurface` | out watersurface | wet | 2 | WaterSurface with a thin underwater grid below. |
| `WetCaveRoom` | in cave | wet | 2 | A wet cave. |
| `WoodRoom` | in wood | normal | 1 | A wooden building room. |
| `WoodSeaPort` | in seaport | wet | 2 | A wooden-building sea port. |
| `WoodenDeck` | out city | inherits | 1 | A ship deck (outdoors). |
| `Woods` | out woods | inherits | 3 | Forest. |

### Grids, thin grids and mazes

A grid locale is one room that contains a rectangle of child rooms of one class (see [Grid rooms and mazes](#grid-rooms-and-mazes)). Each family below fills with the child locale shown. A **grid** builds every child at once; a **thin grid** builds them as they are visited; a **maze** links the children as a random maze. Sizes default to 5 by 5. The climate, domain and cost are the child's. Odd ones: `WetCaveGrid` is a maze (it extends `StdMaze`), `EndlessOcean`, `EndlessThinOcean`, `EndlessSky` and `EndlessThinSky` wrap their edges back to the middle so they never end, `Whirlpool` (movement cost 30) links its water-surface children its own way and gives them a `WaterCurrents` behavior (read the class for the exact shape), and `AutoGrid` builds its children from a random-area XML file (fields `AutoGen Xml File Path` and `AutoGen Variables`; see [Generation](12-generation.md)).

| Child locale | Grid | Thin grid | Maze |
| --- | --- | --- | --- |
| `CaveRoom` | `CaveGrid` |  | `CaveMaze` |
| `Desert` | `DesertGrid` | `DesertThinGrid` | `DesertMaze` |
| `Hills` | `HillsGrid` | `HillsThinGrid` |  |
| `InTheAir` | `EndlessSky` | `EndlessThinSky` |  |
| `IndoorUnderWater` | `IndoorUnderWaterColumnGrid`, `IndoorUnderWaterGrid` | `IndoorUnderWaterThinGrid` |  |
| `Jungle` | `JungleGrid` | `JungleThinGrid` |  |
| `MetalRoom` |  |  | `MetalRoomMaze` |
| `MountainSurface` | `MountainSurfaceGrid` | `MountainSurfaceThinGrid` |  |
| `Mountains` | `MountainsGrid` | `MountainsThinGrid` | `MountainsMaze` |
| `Plains` | `PlainsGrid` | `PlainsThinGrid` |  |
| `Road` | `RoadGrid` | `RoadThinGrid` |  |
| `SaltWaterSurface` | `EndlessOcean`, `OceanGrid` | `OceanThinGrid` |  |
| `SaltWaterThinSurface` |  | `EndlessThinOcean` |  |
| `SewerRoom` |  |  | `SewerMaze` |
| `Shore` | `ShoreGrid` |  |  |
| `StdRoom` | `AutoGrid`, `StdGrid` | `StdThinGrid` | `StdMaze` |
| `StoneRoom` | `StoneGrid` | `StoneThinGrid` | `StoneMaze` |
| `Swamp` | `SwampGrid` | `SwampThinGrid` |  |
| `UnderSaltWater` | `UnderSaltWaterColumnGrid`, `UnderSaltWaterGrid` | `UnderSaltWaterThinGrid` | `UnderSaltWaterMaze` |
| `UnderWater` | `UnderWaterColumnGrid`, `UnderWaterGrid` | `UnderWaterThinGrid` | `UnderWaterMaze` |
| `WaterSurface` | `GreatLake`, `Whirlpool` | `GreatThinLake` |  |
| `WetCaveRoom` |  |  | `WetCaveGrid`, `WetCaveMaze` |
| `WoodRoom` | `WoodRoomGrid` | `WoodRoomThinGrid` | `WoodRoomMaze` |
| `Woods` | `WoodsGrid` | `WoodsThinGrid` | `WoodsMaze` |


Two things to know about the table.

- `ThinRoom` is an internal placeholder class; do not create it by hand.
- Rooms have no generic class (nothing like `GenMob` or `GenDoor`): every locale is a Java class, and `MODIFY ROOM` changes only the fields in [Reference: room editor fields](#reference-room-editor-fields). To add a locale class of your own, see [Engine programming](32-engine-programming.md).

## Reference: exit classes

Create these with `CREATE EXIT [direction] [class]` (the shorthand `CREATE [class] [direction]` also works). `LIST EXITS` lists what is installed. "Door" is the class's built-in door, "Lock" its lock, "Starts" its state. Generic classes (`Gen...`) are fully editable with `MODIFY EXIT`.

| Class | Door | Lock | Starts | Notes |
| --- | --- | --- | --- | --- |
| `StdExit` | no | no | open | The base class. Name "a walkway". |
| `StdOpenDoorway` | no | no | open | An open doorway. What `CREATE ROOM` and `LINK` use. Name "a walkway". |
| `Open` | no | no | open | The same as `StdOpenDoorway`. Older guides call it "the Open exit". |
| `StdClosedDoorway` | yes | no | closed | A wooden door: "a door", closed text "a closed door", door name "door". |
| `Door` | yes | no | closed | The same as `StdClosedDoorway`. |
| `StdLockedDoorway` | yes | yes | locked | A locked door; closed text "a closed, locked door". Its key code is the exit's text (`MODIFY EXIT [dir] TEXT [code]`). |
| `LockedDoor` | yes | yes | locked | The same as `StdLockedDoorway`. |
| `AjarDoor` | yes | no | open | A door that starts open and can be closed. |
| `Gate` | yes | no | closed | "a gate": door name "gate", closed text "a closed gate". |
| `LockedGate` | yes | yes | locked | A `Gate` with a lock; closed text "a closed, locked gate". |
| `Grate` | yes | no | closed | "a barred grate"; open word "remove". |
| `HiddenClosedDoorway` | yes | no | closed | A closed door that is hidden ("a cleverly concealed door"). |
| `HiddenWalkway` | no | no | open | An open exit that is hidden until found. |
| `UnseenWalkway` | no | no | open | An open exit that is "not seen": it does not show in the exit list. |
| `Impassable` | generic | generic | n/a | A generic exit, "a blocked way", that refuses all movement ("You can't go that way") and is flagged unhelpful. |
| `ClimbableExit` | no | no | open | "a sheer surface"; carries `Prop_Climbable`, so it must be climbed. |
| `FlyingExit` | no | no | open | "the open air"; carries the flying flag. |
| `GapExit` | no | no | open | "a crevasse". Anyone not flying or falling must jump it; a chance based on carried weight against strength and carrying capacity makes them miss and die in the fall. Use with care. |
| `PitOpen` | no | no | open | "a pit"; carries the trap `Trap_EnterPit`. |
| `Skyway` | no | no | open | "the sky"; looking at it describes the sky (weather, time) when the room has one. |
| `NeedleDoor` | yes | no | closed | A door trapped with `Trap_OpenNeedle`. |
| `TrappedDoor` | yes | no | closed | A door trapped with `Trap_Open`. |
| `TrappedLockedDoor` | yes | yes | locked | A locked door trapped with `Trap_Trap`. |
| `OpenNameable` | no | no | open | An open exit whose name, display text and description are all its text. `MODIFY EXIT [dir] TEXT through the archway`. |
| `OpenDescriptable` | no | no | open | Named "the ground"; display text and description are its text. |
| `OpenPrepositional` | no | no | open | Like `OpenNameable`, and the enter and exit messages read "through [text]". |
| `NamedRedirectable` | no | no | open | An open exit with its own name, display and description (set in code or by its generic fields) that remembers the first room it was used from and sends anyone entering it back to that room. Rare; used by portal-style content. |
| `GenExit` | optional | optional | by setting | The generic exit: name "a walkway", no door until you turn one on. |
| `GenDoor` | yes | optional | closed | A generic door: "a door", description "An ordinary wooden door with hinges and a latch.", closed text "a closed door". |
| `GenCurtain` | yes | no | closed | A generic curtain: it hides what is beyond while closed but anyone can walk straight through it. "a curtain". |
| `GenAirLock` | yes | no | closed | A generic air lock door: "an air lock door", closed text "a closed air lock door". |

Standard (non-generic) classes cannot be edited beyond their text. If you want a different name or message on a door, create a `GenDoor`.

## Reference: room editor fields

`MODIFY ROOM` (and `STAT ROOM`, read only) lists these in this order. A field that does not apply to the room's class is left out, so the numbers shift; type the number shown.

| Field | Applies to | Meaning and values |
| --- | --- | --- |
| Type | all | The locale class. `?` lists the choices. Changing it asks you to confirm (see [Changing a room's locale class](#changing-a-rooms-locale-class)). |
| Display | all | The title shown above the description (253 characters at most). `null` makes it empty. In a grid parent, `<P>` separates alternative titles. |
| Description | all | The long text. `+` opens the multi-line editor. Supports colour codes and `<VARIES>`. In a grid parent, `<P>` separates alternatives. |
| Size (X), Size (Y) | grid and maze locales | Dimensions of the grid. |
| Horiz. Dir From Core, Vert. Dir From Core | `SpacePort` (location rooms) | Degrees 0-360 from the planet's core, to place the port. |
| AutoGen Xml File Path, AutoGen Variables (VAR=VAL format) | `AutoGrid` | The random-area XML file and its variables. |
| Tags | all | Add or remove a tag by typing it (a tag that exists is removed). Tags are free labels used by `GMODIFY #tag`, masks and scripts. `TODO` marks a room that still needs a description. |
| Behaviors | all | The behaviors editor ([behaviors reference](24-behaviors-and-masks.md)). |
| Effects | all | The effects editor: an ID adds, `-ID` removes, `=ID` or a number edits, `?` lists all, `?P` lists the fitting ones ([properties reference](23-properties-reference.md)). |
| further fields | engine modules | An optional module can add stat codes, and each gets its own numbered field after Effects. |

Not in the numbered editor, but settable with `MODIFY ROOM [stat] [value]`: `IMAGE` (an MXP image name), `CLIMATE`, `ATMOSPHERE`, `ROOMID`, `TEXT`, `AFFBEHAV`. A room's climate bits are normally left to its locale and area.

## Reference: exit editor fields

`MODIFY EXIT [direction]` on a generic exit lists these in order.

| Field | Meaning and values |
| --- | --- |
| Name | What the exit is called ("a door"). |
| Display | The line shown in the room's exit list when the exit is open. Empty (`null`) = see-through: the list shows the room beyond. |
| Description | What `LOOK [direction]` and `EXAMINE` show. |
| Level | The exit's level number. |
| Has a door / Defaults closed / Has a lock / Defaults locked / Reset delay | The door settings from [Doors, locks and keys](#doors-locks-and-keys), asked one after the other. Turning "Has a door" off turns off the rest. The reset delay is in ticks. |
| Closed Text | (if a door) Shown in the exit list while closed. `null` blanks it. |
| Door Name | (if a door) What the door is called in messages. |
| Open Word | (if a door) The opening verb, shown in messages and used for the "already open" message. |
| Close Word | (if a door) The closing verb. |
| Assigned Key Item | (if a lock) The key code that fits. `null` clears it. Without a lock, this field instead asks "Is this door readable" and the text `READ` shows. |
| Dispositions | A toggled list: invisible, hide, unseen, magical, glowing, lightsrc, fly, climb, sleep, sit, sneak, swimmer, evil, good, unattackable. |
| Behaviors, Effects | As for rooms (an exit can hold both). |
| MXP Image filename | The image name for MXP clients. |
| Tags | As for rooms. |
| Scripts | Shown only if the exit has scripts. |

### Exit stat codes

For `MODIFY EXIT [dir] [code] [value]`, `GMODIFY`, and scripts. The generic exit's codes, in [GenExit](../../CoffeeMud/com/planet_ink/coffee_mud/Exits/GenExit.java):

| Code | Meaning |
| --- | --- |
| `CLASS` | The exit's class (read only here). |
| `NAME` | Name. |
| `DISPLAY` | Display text. |
| `DESCRIPTION` | Description. |
| `DOOR` | Door name. |
| `LEVEL`, `ABILITY` | Level and ability numbers (math expressions allowed). |
| `ISREADABLE` | `true` if readable. |
| `READABLETEXT` | The text shown when read. |
| `AFFBEHAV` | Effects and behaviors as XML. |
| `DISPOSITION` | A number, or a comma list of `ISUNSEEN`, `ISHIDDEN`, `ISINVISIBLE`, `ISEVIL`, `ISGOOD`, `ISSNEAKING`, `ISBONUS`, `ISDARK`, `ISGOLEM`, `ISSLEEPING`, `ISSITTING`, `ISFLYING`, `ISSWIMMING`, `ISGLOWING`, `ISCLIMBING`, `ISFALLING`, `ISLIGHT`, `ISBOUND`, `ISCLOAKED`, `ISUNSAVABLE`, `ISCATALOGED`, `ISUNATTACKABLE`, `ISCUSTOM`, `ISUNHELPFUL`. |
| `HASADOOR`, `DEFCLOSED`, `HASALOCK`, `DEFLOCKED` | `true` or `false`. |
| `KEYNAME` | The key code. |
| `RESETTIME` | The reset delay in ticks. |
| `CLOSEWORD`, `OPENWORD`, `CLOSEDTEXT` | The words and the closed text. |
| `TAGS` | Comma list; replaces all tags. |

## Reference: area editor fields

`MODIFY AREA` (and `STAT AREA`, read only) lists these in order; fields that do not apply to the area's class are left out.

| Field | Applies to | Meaning and values |
| --- | --- | --- |
| Name | all | The area's name, which is also the prefix of its room IDs. Changing it offers to renumber the rooms ([Renaming an area](#renaming-an-area)). |
| Display | ships (boardable areas) | The display text of the ship area. |
| Description | all | Shown in the area's help entry (`HELP [area name]`) and the area lists. Multi-line editor with `+`. |
| Author | all | The creator's name, free text. `AREAS SORT=AUTHOR` can sort by it. |
| Theme setting | all | Inherited, Fantasy, Technical, Fantasy & Technical, Heroic, Heroic & Fantasy, Heroic & Technical, All Allowed. `?` lists them. |
| Climate | all | A screen toggling I (inherited), R (wet), H (hot), C (cold), W (windy), D (dry); the engine also has a void bit not reachable from this screen. |
| Atmosphere | all | A raw material name (`AIR`, `FRESHWATER`, `SALTWATER`, `NOTHING`...) or `Inherited`. `?` lists them. |
| Calendar | all | The clock settings screen: shows `Default` for an area on the world clock and `Inherited` for one on its parent's; offers hours per day, dawn, day, dusk and night hours, days per month, weekdays, months and year titles, or `global` to return to the world clock. |
| Player Level | all | `0` means use the median level of the area's mobs when judging suggested levels; any other number is used instead. |
| Parent Areas | all | Area names to add or remove; `*clear*` removes all. |
| Children areas | all | The same for children. |
| Area staff names | all | A user name to add or remove. |
| Area Blurb Flags | all | Flags with optional text, shown in the area's help. Enter a flag to remove it, `A` to add one. |
| Reputation template | all | A reputation template name; blank or `NONE` disables it. |
| Size (X), Size (Y) | grid areas | The dimensions; the room maximum is X times Y. |
| AutoGen Xml File Path, AutoGen Variables | `StdAutoGenInstance` | The random-area XML and its `VAR=VALUE` variables. |
| Radius, Coordinates in Space, and movement | space-object areas | Planet and moon size and position. Distances take units (`LIST` shows them in the error message). |
| Behaviors, Effects | all | As for rooms. |
| MXP Image filename | all | |
| Tags | all | As for rooms. |
| Currency | all | `DEFAULT`, a currency code (such as `GOLD`) or a full definition with `=`. |
| Prejudice | all | Shopkeeper prejudice factors ([Society](09-society.md)). |
| Item Pricing Factors | all | `A` adds a price multiplier for items matching a mask; a number removes one. |
| Budget | all | Shopkeepers' budget. |
| Devaluation rate(s) | all | |
| Inventory reset rate [ticks] | all | |
| Ignore Mask | all | Items shopkeepers here ignore. |

### Area stat codes

`CLASS`, `CLIMATE`, `DESCRIPTION`, `TEXT`, `THEME`, `BLURBS`, `PREJUDICE`, `BUDGET`, `DEVALRATE`, `INVRESETRATE`, `IGNOREMASK`, `PRICEMASKS`, `ATMOSPHERE`, `AUTHOR`, `NAME`, `PLAYERLEVEL`, `PASSIVEMINS`, `CURRENCY`, `AFFBEHAV`, `TAGS`, `REPUTATION`, plus any an optional module adds. See [MODIFY AREA](#modify-area) for value formats.

## Reference: area classes

`CREATE AREA` asks for one of these. `Area` classes are under [Areas/](../../CoffeeMud/com/planet_ink/coffee_mud/Areas/).

| Class | What it is |
| --- | --- |
| `StdArea` | The standard area. All its rooms are loaded at start. It gets the automatic weather behavior and `AUTOAREAPROPS`. Best for areas under about 700 rooms and rich in features. With `DISABLE=FATAREAS`, a new `StdArea` is made as a `StdThinArea`. |
| `StdThinArea` | Rooms are loaded from the database only when something needs them, and are dropped again after about 40 minutes unused. For huge, quiet areas. Law and conquest systems work badly in it because officers and judges may not be loaded. |
| `StdGridArea` | A standard area whose rooms sit on a fixed grid. XGRID and YGRID default to 100 by 100; room IDs come from grid positions. Useful with a grid-based map. |
| `StdThinGridArea` | The thin version of `StdGridArea`. |
| `StdTimeZone` | A standard area with its own clock; its child areas use that clock. |
| `StdPlanet` | A space object and time zone: has coordinates and a radius (random unless placed with `@`). Its first room is a `SpacePort`. |
| `StdThinPlanet`, `Planet`, `Moon` | Thin planets; `Planet` and `Moon` differ in radius range and names. |
| `StdThinInstance` | A thin area that makes its own copy for each party that enters, so each sees it fresh. Copies are discarded when empty. See [Travel, vehicles and instances](30-travel-vehicles-instances.md). |
| `SubThinInstance` | An instance whose name is `Something_Original` or `Something Original`, a thin copy of the existing instance-parent area `Original`. |
| `StdAutoGenInstance` | An instance generated on entry from a random-area XML file (default `randareas/example.xml`) with variables; see [Generation](12-generation.md). |
| `StdBoardableShip`, `StdSpaceShip` | The interior of a ship item. They are created with the ship, not by hand, though `CREATE AREA` lists them. |

## Reference: command syntax

| Command | Syntax | Notes |
| --- | --- | --- |
| `CREATE ROOM` | `CREATE ROOM [dir] [locale/template]` | Shorthand `CREATE [locale] [dir]`. |
| `CREATE EXIT` | `CREATE EXIT [dir] [exit class/template]` | Shorthand `CREATE [exit class] [dir]`. |
| `CREATE AREA` | `CREATE AREA [name]` (`@ [coords or distance FROM object]` for space areas) | Prompts for the area type. |
| `MODIFY ROOM` | `MODIFY ROOM` / `MODIFY ROOM [field] [value]` / `MODIFY ROOM [room ID]` | Fields: NAME, DESCRIPTION, CLASS, ID, AREA, XGRID, YGRID, AFFECTS, BEHAVIORS, stat codes. |
| `MODIFY EXIT` | `MODIFY EXIT [dir]` / `MODIFY EXIT [dir] TEXT [value]` / `MODIFY EXIT [dir] [stat] [value]` | |
| `MODIFY AREA` | `MODIFY AREA` / `MODIFY AREA [name]` / `MODIFY AREA ([name]) [field] [value]` | Fields: NAME, DESC, CLIMATE, FILE, PASSIVE, ACTIVE, FROZEN, STOPPED, XGRID, YGRID, ADDSUB, DELSUB, AFFECTS, BEHAVIORS, CALENDAR, WEATHERPROFILE, stat codes. |
| `DESTROY ROOM` | `DESTROY ROOM [room ID] (CONFIRMED)` / `DESTROY ROOM [dir]` | |
| `DESTROY EXIT` | `DESTROY EXIT [dir]` | |
| `DESTROY AREA` | `DESTROY AREA [name] (CONFIRMED)` / `DESTROY AREA ALL EXCEPT [name]...` | |
| `LINK` | `LINK [room ID or text] [dir]` | |
| `UNLINK` | `UNLINK [dir]` | |
| `COPY` | `COPY ([n]) [dir]` / `COPY [room ID] [dir]` / `COPY ROOM [dir] [dir]` / `COPY EXIT [dir] [dir]` / `COPY [area]` | |
| `TEMPLATE` | `TEMPLATE LIST/ADD/DELETE/TOGGLE ...` | |
| `GMODIFY` | `GMODIFY (AREA/ROOM/WORLD/#TAG) [criteria] (CHANGE=[changes])` | |
| `WORLD DIG` | `WORLD DIG ON (TYPE=) (LINK=)` / `WORLD DIG OFF` | Builder mode only. |
| `WORLD PATH` | `WORLD PATH [dirs] (TYPE=) (NAME=) (DESC=) (LINK=)` | Builder mode only. |
| `WORLD MAP` | `WORLD MAP (RADIUS n) (AREA) (IDS) (FLAGS) (HTML=file)` | |
| `WORLD CHECK` | `WORLD CHECK (AREA name) (LOG) (ONLY=parts)` | |
| `EDIT DESC` | `EDIT DESC [HERE/AREA/target]` | Multi-line description editor. |
| `GOTO`, `AT`, `STAT`, `EXITS`, `LIST ...` | see [Orientation and inspection commands](#orientation-and-inspection-commands) | |
| `UNDO` | `UNDO ([n])` / `UNDO LIST ([n])` | |
| `RESET` | `RESET ROOM` / `RESET AREA` | |
| `SAVE` | `SAVE` / `SAVE ROOM (HOME/ALL)` / `SAVE CHANGED` / `SAVE FORCE` | See [World files and editing](02-world-workflow.md). |

### Security flags

| Flag | Gates |
| --- | --- |
| `CMDROOMS` | `CREATE ROOM`, `MODIFY ROOM`, `DESTROY ROOM [id]`, `WORLD MAP`, `WORLD CHECK`, `LIST` room reports |
| `CMDEXITS` | `CREATE`, `MODIFY` and `DESTROY EXIT`, `LINK`, `UNLINK`, `DESTROY ROOM [dir]` |
| `CMDAREAS` | `CREATE`, `MODIFY`, `DESTROY AREA`, copying an area |
| `COPYROOMS` | `COPY` of rooms, exits, areas |
| `GMODIFY` | `GMODIFY` (the `WORLD` scope needs it everywhere) |
| `GOTO`, `AT`, `STAT`, `SYSMSGS` | The same-named commands |

Flags can be granted for the whole world or for rooms in an area (for an area's staff). Builder mode makes the character an Archon with all of them. The full list and how to grant them are in [Configuration and administration](19-configuration-and-administration.md) and the generated [permissions table](tool-permissions.md).

## How geography is saved

World geography lives in one file per area, `world/areas/NAME.cmare` (areas marked as dev areas go to `world/dev/`). Builder-mode `SAVE`, and quitting, write these files from the live world; the whole format, the readable layout, protection against overwriting hand edits and the content-database cache are in [World files and editing](02-world-workflow.md). This section lists only what is specific to areas, rooms and exits.

**File name.** The name is the area's name lower-cased, with every run of other characters made one `-`, trimmed, and numbered `-2`, `-3` if two areas would collide: `New Area` is `new-area.cmare`, `Hollowmere (West)` is `hollowmere-west.cmare`. Renaming an area therefore renames its file at the next save.

**Area element.**

| Element | Holds |
| --- | --- |
| `ACLAS` | The area class (`StdArea`). |
| `ANAME`, `ADESC` | Name and description. |
| `ACLIM` | The climate code (the bits: wet 1, cold 2, windy 4, hot 8, dry 16, void 32; `0` normal; `-1` inherited). |
| `ASUBS` | Area staff. |
| `ATECH` | The theme code (Fantasy 1, Technical 2, Heroic 4; `0` inherited). |
| `ADATA` | The area's other data: `REPUTATION`, `ARCHP` (the FILE path), `PARENTS` (`PARENT/PARENTNAMED`; the default parent is left out), `CHILDREN` (`CHILD/CHILDNAMED`), `PREJFC`, `IGNMSK`, `BUDGET`, `DEVALR`, `INVRER`, `CURRENCY`, `IPRICS`, `IMG`, `BEHAVES`, `AFFECS`, `TAGS`, `AUTHOR`, `BLURBS`, `AATMO`, and for grid areas `XGRID` and `YGRID`, for auto-generated ones `AGXMLPATH` and `AGAUTOVAR`. An area's `CALENDAR` and `WEATHERPROFILE` settings are stored as blurb flags in `BLURBS`. |
| `AROOMS` | The rooms, each an `AROOM`. |

**Room element.**

| Element | Holds |
| --- | --- |
| `ROOMID`, `RAREA`, `RCLAS` | The room ID, its area's name and the locale class. |
| `RDISP`, `RDESC` | Title and description. |
| `RTEXT` | The room's own data: `RCLIM` (climate code) and `RATMO` (atmosphere code, `-1` inherit), `XGRID`/`YGRID` for grids, `IMG`, `BEHAVES`, `AFFECS`, `TAGS`, scripts. Written escaped on one line in older files and as indented XML marked `xml="1"` by the current export; both read. |
| `ROOMEXITS` | One `REXIT` for each direction that has a link or an exit. |
| `ROOMCONTENT` | The mobs (`ROOMMOBS`) and items (`ROOMITEMS`) placed in the room. |

**Exit element.** Each `REXIT` holds `XDIRE` (the direction number, see [Reference: directions](#reference-directions)), `XDOOR` (the destination room ID, empty for an exit that leads nowhere), and `XEXIT`, which is either `<XEXIT />` (a link with no exit) or `<EXID>class</EXID>` with `<EXDAT>` (the exit's own text; empty for the standard classes). A direction with neither a link nor an exit is simply absent. Grid parents also write `REXIT`s numbered from 256 for their cross exits.

A generic exit's `EXDAT` holds the fields of the exit editor. For a closed, locked door:

```xml
<REXIT>
 <XDIRE>1</XDIRE>
 <XDOOR>Hollowmere#2</XDOOR>
 <XEXIT>
  <EXID>GenDoor</EXID>
  <EXDAT xml="1">
   <NAME>a heavy cellar door</NAME>
   <DESC>Oak planks bound with iron, set into a stone step.</DESC>
   <DISP />
   <PROP>0|100|0|0|0|0|0|1.0|0|0|0|</PROP>
   <IMG />
   <BEHAVES />
   <AFFECS />
   <FLAG>496</FLAG>
   <CLOSTX>a heavy cellar door</CLOSTX>
   <DOORNM>door</DOORNM>
   <OPENNM>open</OPENNM>
   <CLOSNM>close</CLOSNM>
   <KEYNM>hollowmere.cellar</KEYNM>
   <OPENTK>45</OPENTK>
  </EXDAT>
 </XEXIT>
</REXIT>
```

`NAME`, `DESC`, `DISP` are the name, description and display text; `PROP` is the base physical stats (ability, armor, attack, damage, disposition, level, rejuv, speed, weight, height, senses, in that order; the export may write them as named attributes); `FLAG` is a bit field (4 readable, 16 always set for doors, 32 has a door, 64 has a lock, 128 defaults closed, 256 defaults locked; the example is 16+32+64+128+256 = 496, a door with a lock that starts closed and locked); `CLOSTX`, `DOORNM`, `OPENNM`, `CLOSNM` are the closed text, door name, open word and close word; `KEYNM` is the key code (or the readable text of a readable exit); `OPENTK` is the reset delay in ticks. The `xml="1"` mark on `EXDAT` is what the current export writes (older files hold the same tags escaped on one line); a hand-written element must carry the mark if its tags are nested like this.

**Editing by hand.** You can edit a `.cmare` in a text editor (change a title, retarget `XDOOR`, add a `REXIT`), but two links must agree: every `XDOOR` has to name a room that exists, and a two-way passage needs a `REXIT` in each room. Run `WORLD CHECK` afterwards; `WORLD RELOAD [area]` loads the file into a running builder game. A file edited while a builder game runs is protected from being overwritten by the next `SAVE`; see [World files and editing](02-world-workflow.md).

**Dev areas.** `WORLD DEV [area] ON` tags an area `DEV`, so that it is written to `world/dev/` and loaded only in builder mode and `DEV=1` games. Exits from shipped areas into a dev area lead nowhere in a normal game, and `WORLD CHECK EXITS` warns about them.

**Starting room.** Where new characters begin is a world setting, `START=Area#number` in `world.ini` (`WORLD SET START=Hollowmere#0`), not a property of the area. See [world settings](world-settings.md).

## Worked examples

### Example 1: a village gate, a lane and a locked cellar

Start the game in builder mode (`CoffeeMud/sud.sh BUILDER=1`), and turn on `SYSMSGS` so `LOOK` shows room IDs.

```text
CREATE AREA Hollowmere
```

Press Enter at the area type question. You are now in `Hollowmere#0`, a `StdRoom` with the default title.

```text
MODIFY ROOM CLASS CityStreet
MODIFY ROOM NAME The Gate of Hollowmere
MODIFY ROOM DESCRIPTION A timber gate stands open in an earth bank. A muddy lane runs east into the village.
CREATE ROOM east CityStreet
east
MODIFY ROOM NAME The Muddy Lane
MODIFY ROOM DESCRIPTION Cottages crowd the lane. A heavy cellar door is set into a stone step to the south.
CREATE ROOM south StoneRoom
south
MODIFY ROOM NAME A Root Cellar
MODIFY ROOM DESCRIPTION Sacks and barrels crowd the cool dark.
north
```

You are back in the lane (`Hollowmere#1`; the cellar is `#2`). Make the door. The first command puts a default `GenDoor` on both ends; the second opens the exit editor on a copy of it and then writes the result to both ends:

```text
CREATE EXIT south GenDoor
CREATE EXIT south GenDoor
```

In the editor, give it a name (`a heavy cellar door`), a description, and in the door field answer Has a door `Y`, Defaults closed `Y`, Has a lock `Y`, Defaults locked `Y`; set Closed Text to `a heavy cellar door`, and the key name to `hollowmere.cellar`. Press Enter at `Edit which?` to finish. Check both ends: `LOOK south`, then `south` and `LOOK north`.

Make a key and hide it where the player can find it:

```text
CREATE ITEM GenKey
MODIFY ITEM key
```

Set its *Assigned Key Code* to `hollowmere.cellar`, then `GET key`, go west, `DROP key` and `SAVE ROOM` (items are only saved by `SAVE ROOM`; see [World files and editing](02-world-workflow.md)). Join the village to the starter area. In the shipped world the starter room `New Area#1` has free east and west exits:

```text
GOTO New Area#1
LINK Hollowmere#0 east
GOTO Hollowmere#1
WORLD CHECK AREA Hollowmere
WORLD MAP AREA IDS FLAGS
SAVE
```

`LINK` also links `Hollowmere#0` back west because that side was free. The check should report nothing for Hollowmere (a `TEXT` warning would mean a room still has default text). `WORLD MAP` draws the area you are standing in, so run it from inside Hollowmere; it shows the locked door as `+`. `SAVE` writes `world/areas/hollowmere.cmare`.

### Example 2: laying a trail, then fixing every door at once

A wilderness trail of ten rooms, mostly woods:

```text
GOTO Hollowmere#0
WORLD PATH n,n,n,e,e,e TYPE=Woods NAME="A forest trail" DESC="Roots cross a narrow trail between tall pines."
WORLD MAP RADIUS 8 IDS
```

(North of the gate was free; a direction that already has a link would be followed, not built. Diagonals such as `ne` need `DIRECTIONS=11`.) Every new room carries the `TODO` tag until you give it its own description, and `WORLD CHECK ONLY=TEXT` lists them. Give one a description with `EDIT DESC HERE`.

Later you decide every door in the area should read "oak door" and have a new closed text. Always search first:

```text
GMODIFY AREA class=GenDoor
GMODIFY AREA class=GenDoor&&name=a door CHANGE=name=an oak door
GMODIFY AREA class=GenDoor CHANGE=closedtext=a closed oak door
LIST UNSAVED
SAVE
```

The first line only prints `Matched on ... from room` lines. The second renames every generic door whose whole name is exactly `a door` (without `[ss]` the value must equal the whole field; with `[ss]` on both sides only the matched part is replaced). The third sets the closed text of every generic door in the area, whatever its name. Exits are saved with their room automatically; mobs and items need `SAVE ROOM` as always, and `GMODIFY CHANGE=` asks before it drops unsaved ones.

### Example 3: a hidden passage and a one-way chute

In the lane (`Hollowmere#1`), build a store room to the north and hide its door:

```text
CREATE ROOM north StoneRoom
CREATE EXIT north HiddenClosedDoorway
```

`CREATE EXIT` replaces the exit on your side and, because the store room had an exit back, on the far side too. The door is now a closed, hidden door from both rooms. It is left out of the exit list for a character who cannot see hidden things. Give the store room its description, then make the door one-way, a trapdoor that only opens inward:

```text
north
UNLINK south
```

`UNLINK south` removes the store room's link and exit back to the lane, so you can enter but there is no way out except the builder's `GOTO`. Check it:

```text
LIST ONEWAYDOORS AREA
WORLD MAP AREA FLAGS
WORLD CHECK ONLY=EXITS
```

`LIST ONEWAYDOORS` names the lane's north link to the store room (`Hollowmere#9` if you built the trail of Example 2 first, `#3` otherwise), the map draws a `^` on the lane's north exit, and `WORLD CHECK` warns "a door into Hollowmere#9, which has no way back" because the exit is a door. Run `LINK Hollowmere#1 south` from the store room to give it a way out again; then the warning goes away.

## Persistence notes

| What | Where it lives | When it is written |
| --- | --- | --- |
| Rooms, exits, areas you create or change | The live world and the content database at once | The moment a `CREATE`/`MODIFY`/`DESTROY`/`LINK`/`UNLINK`/`COPY`/`GMODIFY` command runs |
| `world/areas/NAME.cmare` | Disk, the publishable definition | Builder-mode `SAVE` or quit; never in a normal game |
| Mobs and items in rooms | The database, then the `.cmare` | `SAVE ROOM`, `SAVE CHANGED`, plain `SAVE` ([World files and editing](02-world-workflow.md)) |
| Builder templates | The builder's template store (`world/templates.cmare` in builder mode) | When you `TEMPLATE ADD`, then at export |
| Area calendars and weather profiles | The area's blurb flags (`BLURBS`), so the `.cmare` | With the area |
| A normal game's geography changes | That game's working copy and its save slots | `SAVE [slot]` ([Saves and party](31-saves-and-party.md)) |

- A reload (`WORLD RELOAD [area]`) rebuilds an area from its file: unsaved room edits are lost, and the command refuses while the area has unsaved mobs or items unless `FORCE`.
- Thin areas and thin grids load rooms on demand and drop them after about forty minutes; edits made to a loaded room are written through to the database immediately, so nothing is lost.
- `DESTROY ROOM` and `DESTROY AREA` delete from the database at once, but the `.cmare` still has the old text until the next `SAVE`; `WORLD BACKUPS` keeps what `SAVE` replaced.
- Renaming an area changes room IDs; any file or script that mentions the old IDs (`START=`, quests, scripts, JavaScript, `WORLD PATH` macros) needs updating.

## Troubleshooting and gotchas

- **"This command is invalid from within a GridLocaleChild room."** You are in a grid or maze child. `GOTO PARENT`, edit the parent, then `RESET ROOM`.
- **"A room may not be created in that direction."** `CREATE ROOM` could not get a room ID there, typically at the edge of a `StdGridArea`.
- **A new room's exit goes only one way.** `LINK` and `CREATE ROOM` link back only when the opposite direction of the far room is free. Check with `LIST ONEWAYDOORS AREA`.
- **`CREATE ROOM` into a direction that is already taken.** The new room may be created but left unlinked (an orphan). `LIST ORPHANS AREA` finds it; `LINK` it from a free direction or `DESTROY ROOM [id]` it.
- **A door works from one side only.** You edited only one end. `CREATE EXIT [dir] GenDoor` twice, or `MODIFY EXIT` in each room.
- **"You don't seem to have the key."** The key code does not equal the lock's key name (compare `STAT` output, remember case is ignored but spaces count), or the lock's name is empty, or a `Prop_KeyRules` mask excludes you. `WORLD CHECK ONLY=KEYS` finds locks no key exists for.
- **A door will not stay open or unlocked.** A door that defaults closed or locked resets after its delay ticks (default 45).
- **A destroyed exit still has its link.** `DESTROY EXIT` leaves the link, so the room still counts as connected for the reports, while ordinary characters cannot pass. `WORLD CHECK` does not flag a link with no exit; `LIST UNLINKEDEXITS` flags the reverse (an exit with no room).
- **Exits to nowhere after destroying a room.** `DESTROY ROOM` cuts links into the room, but a generic exit there keeps the dead room's ID in its key name and stays in the exit list. `DESTROY EXIT` them or relink.
- **`MODIFY AREA CLIMATE` letters.** `D` sets windy rather than dry in the one-line command (see [MODIFY AREA](#modify-area)); use the stat form `MODIFY AREA CLIMATE DRY` or the editor.
- **Technology gate only with the Heroic bit.** The theme check that stops technology (`Technology doesn't seem to work here`) is attached to the Heroic test in the engine: it runs only when the theme has Heroic and lacks Technical. A Fantasy-only area does not block technology items.
- **`GMODIFY CHANGE=` with no criteria changes everything in scope that has the field.** Search first, add `class=`.
- **`GMODIFY` regex without `[ss]`.** `name$wooden` must match the whole name.
- **A copied exit is shared.** `COPY EXIT [dir] [dir]` puts the same exit object in the second place, not a copy; editing one edits both until the world is reloaded.
- **Areas will not rename cleanly.** If a room of the new name already exists, that room keeps its old ID. Check `LIST ROOMS` afterwards.
- **Mobs will not walk while SYSMSGS is on** in your room (shipped help for `SYSMSGS`).
- **Dug rooms keep the `TODO` tag** until `EDIT DESC` writes their description. `MODIFY ROOM DESCRIPTION` does not clear it; remove it in the Tags field, or use `EDIT DESC HERE`.
- **Wrong direction names.** With `DIRECTIONS=7` the diagonal names do not exist. Set `DIRECTIONS=11` in `world.ini` first if you plan to use them.
- **Normal-game edits vanish.** Outside builder mode they stay in that game's copy only. Use `BUILDER=1` to change the world itself.

## See also

- [World files and editing](02-world-workflow.md): builder mode, `SAVE`, reload, undo, the file formats in full.
- [Your first world](01-first-world.md): a beginner walk-through that uses these commands.
- [Mobs](21-mobs.md) and [Items](22-items.md): what goes into the rooms you build.
- [Properties reference](23-properties-reference.md) and [Behaviors and masks](24-behaviors-and-masks.md): the effects and behaviors you can give rooms, exits and areas (`Prop_ReqEntry`, `Prop_Climbable`, `Prop_LockRules`, `Prop_KeyRules`, `Prop_ReqCapacity`, ...).
- [World simulation](10-world-simulation.md): weather, climate, time and calendars.
- [Presentation and language](11-presentation-language.md): descriptions, `<VARIES>` text and colours.
- [Generation](12-generation.md): random areas and the `AutoGrid`/`StdAutoGenInstance` classes.
- [Travel, vehicles and instances](30-travel-vehicles-instances.md): ships, boardable areas, planets, instances.
- [Configuration and administration](19-configuration-and-administration.md): `coffeemud.ini` keys and security flags; [World settings](world-settings.md).
- [Testing and release](13-testing-release.md): `WORLD CHECK`, playtests, release checks.
- [Engine programming](32-engine-programming.md): writing new locale, exit and area classes.
