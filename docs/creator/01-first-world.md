# Your first world

[Handbook](README.md) · Next: [World files and editing](02-world-workflow.md)

## Start the editor

Install a JDK, version 9 or newer; this checkout is developed with JDK 21.
Python 3 is needed for automated playtests (3.8 or newer for the example pack).
Start from the repository root:

```sh
CoffeeMud/sud.sh BUILDER=1 TIMEMODE=TURNS
```

On Windows use `CoffeeMud\sud.bat BUILDER=1 TIMEMODE=TURNS`. The launcher
compiles before starting. Follow character creation; the builder receives
Archon powers. Enter `AHELP BUILDING`. Keep the world under version control
before experimenting. The shipped world is a two-room starting point.

| Launch mode | Edits affect | What SAVE means |
| --- | --- | --- |
| `BUILDER=1` | Authored world and its cache | Export world content |
| Normal game | A working copy of the world | Save campaign state to a slot |
| `DEV=1` | A working copy with developer tools and dev areas | Save campaign state to a slot |

`DEV=1` is useful for testing a real character. It is not the publishing mode.
`PLAYTEST ON` temporarily suspends powers in the current builder world and
keeps its changes. Use `PLAYTEST GAME` for a disposable campaign snapshot.

## Make a small place

Begin with three rooms: a safe arrival, an NPC, and a locked or dangerous
destination. Choose one goal, such as learning a password and opening a gate.
Do not begin by generating a continent.

1. Inspect the starting room with `LOOK` and `STAT ROOM`. Record its room ID.
2. Enter `WORLD PATH e,n TYPE=StdRoom NAME="An unfinished passage"`. This
   creates or follows a path without moving you. Alternatively use `CREATE ROOM`
   and its prompts. `WORLD DIG ON` creates rooms as you walk into empty
   directions; turn it off with `WORLD DIG OFF` when finished.
3. Visit each room. Use `MODIFY ROOM` for name, description, locale, resources,
   properties and behaviors. `EDIT DESC` opens the multiline description editor.
   Newly dug rooms have a TODO tag until given their own description.
4. Use `MODIFY EXIT east` to edit the east exit where one exists. A return exit
   is a separate object: check both sides of doors and locks. Room links and
   exit objects are related but distinct; a destination alone is not a door.
5. Enter `WORLD MAP AREA IDS FLAGS` to inspect connectivity. A room can have
   non-geographic links, so the map can list overlaps or vertical rooms beside
   the drawing.

## Add an object and a character

Use `CREATE ITEM GenItem`, then `MODIFY ITEM` and the item's name. Set its name,
display text and description. A display text says what appears in the room;
the description is what examining it reveals. Use an appropriate subclass:
`GenWeapon` for a weapon, a generic container for storage, or a book class for
pages. Editing a generic item's name does not change its underlying interface.

Use `CREATE MOB GenMob`, then `MODIFY MOB` and the mob's name. Set race, class,
level, description, inventory, abilities and behaviors. Set its home/start room
deliberately. When saving moved creatures, `SAVE ROOM HOME` saves them into
their own start rooms rather than treating every visitor as a local resident.

Keep reusable designs in the `CATALOG`. A catalog definition and a placed copy
are different things. Catalog updates and placement rules can affect many copies;
test these deliberately. `CATALOG LIST` and `SANDBOX PREVIEW name` (a catalog name) let you
inspect designs without adding permanent residents.

## Give it one interaction

Attach `Scriptable` to your NPC through the behavior editor. Set its parameters
to `LOAD=progs/guide.script`. Put this UTF-8 file in
`CoffeeMud/world/resources/progs/guide.script`:

```text
SPEECH_PROG p gate
mpechoat $n The guide says, 'The lever opens the gate.'
~
```

Say `gate` near the NPC. `p` selects a phrase pattern; `$n` is the actor whose
speech triggered the event. End every program with `~`. The script belongs to
the host and does not need a server or a logged-in account. The [MOBPROG
chapter](04-mobprog.md) explains triggers, conditions, state and delayed actions.

For a complete working interaction, use the [museum example](examples/README.md)
or [dialogue and switch recipes](cookbook.md). Examples label the exact host,
file format and commands needed; do not paste MOBPROG into a JavaScript file.

## Check, save, and test

```text
SCRIPTCHECK guide
PROPCHECK ROOM
WORLD CHECK
LIST UNSAVED
SAVE CHANGED
WORLD STATUS
PLAYTEST GAME
```

Use `AHELP SCRIPTCHECK` to choose the host or file form for your script.
Warnings can require judgment; errors deserve investigation. `SAVE CHANGED`
publishes changed builder content. It does not commit Git changes. Inspect the
world diff before committing. A file edited externally is protected: a
conflicting export goes to a `.builder` sibling rather than overwriting it.

In the disposable campaign, test the interaction as an ordinary character,
including a wrong answer, walking away, party use, and saving halfway through.
Close it when finished. In a headless environment `PLAYTEST GAME` prints a
launch command and temporary directory instead of opening a window.

## Make a reliable opening

Set `START` in `world/world.ini` to a real room ID and restart. Pick a sensible
starting level and equipment; provide light, exits and a goal the player can
understand. Use an origin or creation hook when the start depends on a choice.
Use `Prop_FirstEntry` or narration for the introduction, with a stable ID and a
clear once-per-character/profile policy. Do not put essential instructions only
in a random ambient line. [Characters](03-characters.md) covers creation and
[presentation](11-presentation-language.md) covers opening text.
