# Mobs: creating and editing creatures

A mob (the engine's word for any non-player creature) is a person, animal,
monster, shopkeeper, banker, mount or god that lives in a room. This chapter is
for world creators and builders: it covers every mob class you can create, the
`CREATE MOB` and `MODIFY` commands, every field of the generic mob editor and of
the shopkeeper editor, equipment, money, start rooms and rejuvenation,
spawning, the mob catalog, how mobs are stored in area files, and recipes for
common mobs. Engine developers will find the class and stat-code tables useful.
Behaviour detail is deliberately not repeated here: see
[24-behaviors-and-masks.md](24-behaviors-and-masks.md),
[23-properties-reference.md](23-properties-reference.md),
[06-npcs-dialogue.md](06-npcs-dialogue.md) and
[29-combat-and-encounters.md](29-combat-and-encounters.md).

## In this chapter

- [Concepts](#concepts)
- [Mob classes](#mob-classes)
- [Creating a mob](#creating-a-mob)
- [Editing a mob](#editing-a-mob)
- [The generic mob editor: field reference](#the-generic-mob-editor-field-reference)
- [Stat codes for MODIFY MOB and GMODIFY](#stat-codes-for-modify-mob-and-gmodify)
- [Combat numbers, hit points and levels](#combat-numbers-hit-points-and-levels)
- [Race, class, gender and factions](#race-class-gender-and-factions)
- [Abilities, behaviors, effects and scripts](#abilities-behaviors-effects-and-scripts)
- [Money](#money)
- [Equipment and inventory](#equipment-and-inventory)
- [Shopkeepers and other service mobs](#shopkeepers-and-other-service-mobs)
- [Rideable mobs and mounts](#rideable-mobs-and-mounts)
- [Deities](#deities)
- [Start rooms, rejuvenation and death](#start-rooms-rejuvenation-and-death)
- [Spawning](#spawning)
- [The mob catalog](#the-mob-catalog)
- [Copying, destroying and bulk editing](#copying-destroying-and-bulk-editing)
- [How mobs are saved](#how-mobs-are-saved)
- [Recipes](#recipes)
- [Worked examples](#worked-examples)
- [Troubleshooting and gotchas](#troubleshooting-and-gotchas)
- [See also](#see-also)

## Concepts

**Generic and standard mobs.** Mob classes come in two families.

- A *generic* mob (`GenMob`, `GenShopkeeper`, `GenBanker`, ...) keeps all its
  data in its own text: name, description, race, stats, abilities, behaviors,
  effects, scripts, inventory and so on. Creating one opens the full editor.
  Only generic mobs can carry equipment, behaviors, scripts or custom
  abilities into a saved world. Build your own creatures from these.
- A *standard* mob (`Dog`, `Skeleton`, `RedDragon`, `StdMOB`, ...) is a Java
  class with everything fixed in its constructor. A world file stores only its
  class, level, "magical ability" number and rejuvenation time. The editor for a
  standard mob offers just those three fields plus a Misc Text field that most
  classes ignore. They are handy as quick fodder, not as authored characters.

The tables in [Mob classes](#mob-classes) list every class.

**Mobs live in rooms and have a home.** A saved mob belongs to the room it was
saved in, its *start room*. The mob may wander, but when it dies it comes back
in its start room after its *rejuvenation* time (see
[Start rooms, rejuvenation and death](#start-rooms-rejuvenation-and-death)).

**Builder mode versus a normal game.** CoffeeSUD is a single-player engine. The
world folder (`world/`) is the publishable definition of a game, and the
database is a working cache. Build in *builder mode*, launched with `BUILDER=1`
(see [18-installing-and-running.md](18-installing-and-running.md)), and `SAVE`
writes your mobs back into the world files. In a normal game the same commands
work, but the game warns you that the mobs you make stay in that game and
its saves only, not in the world other games start from. See
[02-world-workflow.md](02-world-workflow.md) for the world files and
[31-saves-and-party.md](31-saves-and-party.md) for campaign saves.

**Permissions.** `CREATE MOB`, `MODIFY MOB` and editing a mob by name need the
`CMDMOBS` security flag; `CATALOG` needs `CATALOG`; `COPY` of a mob needs
`COPYMOBS`. In builder mode your character is an archon and has all of them
(see [19-configuration-and-administration.md](19-configuration-and-administration.md)).

**Editor styles.** The mob editor has two styles, chosen by `EDITORTYPE` in
`coffeemud.ini`: `MENU` (the default) shows every field with a number and asks
"Edit which?"; `WIZARD` walks through every field in order, prompting for each.
In either style, pressing ENTER at a prompt keeps the current value.

## Mob classes

Source: [`MOBS/`](../../CoffeeMud/com/planet_ink/coffee_mud/MOBS). Every class is
available to `CREATE MOB` by its ID.

### Generic classes (full editor)

| Class | Extends | Adds |
| --- | --- | --- |
| `GenMob` | StdMOB | The base generic creature. Name "a generic mob". |
| `GenUndead` | GenMob | A generic mob whose race starts as the Undead race, so clerics can `TURN` it. Edited exactly like GenMob. |
| `GenRideable` | StdRideable | A generic mob others can ride: rideable type, rider capacity and the mount/dismount texts. Named "a generic horse". |
| `GenHorse` | GenRideable | A `GenRideable` pre-set as a horse ("a horse stands here"). |
| `GenRideableUndead` | GenRideable | An undead mount, for example a skeletal steed. |
| `GenCow` | GenRideable (and Drink) | A cow that supplies a drink (`FILL` from it). Adds Quenched/Drink, Amount of Drink Held and Liquid Type. Its race starts as Cow and its rider capacity as 0, so it is not a mount unless you raise the capacity. |
| `GenShopkeeper` | StdShopKeeper | A merchant, trainer, caster, innkeeper or seller, depending on its type. Adds the full shop editor. |
| `GenBanker` | StdBanker | Holds players' money and items. Adds interest rates and a bank chain. |
| `GenPostman` | StdPostman | A post office clerk. Adds the postal chain and fee fields. |
| `GenAuctioneer` | StdAuctioneer | Sells players' items by timed auction. Adds auction fee fields. |
| `GenCraftBroker` | StdCraftBroker | Brokers requests for crafted goods. Adds chain, listing limits and commission. |
| `GenLibrarian` | StdLibrarian | Loans items out instead of selling them. Adds overdue charges and a library chain. |
| `GenDeity` | StdDeity | A god that clerics serve. Adds requirements, rituals, blessings, curses and powers. |

`GenMob`, `GenRideable`, `GenShopkeeper` and the others share one set of
base fields (see the field reference below); each subclass appends its own.

### Standard service and special classes (fixed, minimal editor)

| Class | What it is |
| --- | --- |
| `StdMOB` | The base class, a bare level 1 creature. |
| `StdShopKeeper`, `StdBanker`, `StdPostman`, `StdAuctioneer`, `StdLibrarian`, `StdCraftBroker` | Non-generic service mobs. Their stock and settings are not saved with the world, so a `CREATE MOB StdShopKeeper` is only a stub; use the `Gen` versions. `resetStock` only works on generic shopkeepers. |
| `StdDeity` | A non-generic deity. |
| `StdRideable` | The base class for non-generic mounts. |
| `Citizen`, `AngryCitizen` | A townsperson (level 1, human, good) and an angry one (evil, with a throwing stone). |
| `Teacher` | "Cornelius, Knower of All Things": a level 25 trainer with the `MOBTeacher`, `MudChat` and `CombatAbilities` behaviors. |
| `CombatDummy` | A level 4 wooden-golem dummy with 999 hit-point modifier that cannot move, speak or think and deals no damage. |
| `Doppleganger` | A wandering, chatty shape-changing mimic (level 6, speed 2). It copies the shape of others and reverts to its natural form after a long time or when it dies. |
| `HeavenlyServent` | An "archon servant" that wanders and chats. |
| `InvisibleStalker` | An invisible, aggressive, wandering stalker. |
| `Assassin` | A GenMob subclass pre-built with Hide, Sneak, BackStab and Assassinate, a longsword and leather armor. |
| `StoneGolem` | A level 25 stone golem. |
| `Undead`, `Skeleton`, `Zombie` | The undead base class and two undead types (Skeleton wields a longsword). `Undead` mobs can be turned by clerics. |
| `Dragon` | The dragon base class. Created by itself it rolls a random colour and age; the colour classes below fix the colour. |
| `BlackDragon`, `BlueDragon`, `BrassDragon`, `BronzeDragon`, `CopperDragon`, `GoldDragon`, `GreenDragon`, `RedDragon`, `SilverDragon`, `WhiteDragon` | The dragon colours. On a dragon the "Magical Ability" number is the colour (0 white, 1 black, 2 blue, 3 green, 4 red, 5 brass, 6 copper, 7 bronze, 8 silver, 9 gold) and the age is the level divided by ten. |
| `Horse`, `Pegasus`, `PegasusGreater` | Non-generic mounts (`StdRideable` subclasses). |

### Standard animals and monsters

These are all fixed classes with an animal or monster race. Place them for
quick wildlife; use a `GenMob` with the matching race when you need
equipment, behaviors, a custom description, or a changed name.

| Group | Classes |
| --- | --- |
| Mammals | `Ape`, `Beaver`, `BlackBear`, `BrownBear`, `WhiteBear`, `Buck`, `Doe`, `Deer`, `Buffalo`, `Bull`, `Cat`, `Kitten`, `Cheetah`, `Chimp`, `Gorilla`, `Monkey`, `Cougar`, `MountainLion`, `Lion`, `Jaguar`, `Panther`, `Tiger`, `Cow`, `Coyote`, `Wolf`, `DireWolf`, `Wolverine`, `Dog`, `Puppy`, `Elephant`, `Fox`, `Goat`, `GoatDoe`, `Mouse`, `Rat`, `Pig`, `Rabbit`, `Sheep`, `Squirrel`, `Walrus` |
| Birds | `Chicken`, `Rooster`, `Duck`, `Falcon`, `Hawk`, `WildEagle`, `Owl`, `Parakeet`, `Penguin`, `Raven`, `Vulture` |
| Reptiles, amphibians | `Alligator`, `Crocodile`, `Frog`, `Toad`, `Lizard`, `Turtle`, `Cobra`, `BrownSnake`, `GardenSnake`, `Python`, `Rattlesnake`, `Snake` |
| Sea creatures | `Dolphin`, `Seal`, `Shark`, `Swordfish`, `Whale` |
| Insects, arachnids, others | `Bee`, `Hornet`, `FlyingInsect`, `Centipede`, `Scorpion`, `GiantScorpion`, `Spider`, `Worm`, `CommonBat`, `GiantBat`, `LargeBat` |
| Humanoids and monsters | `Bugbear`, `Centaur`, `DrowElf`, `DrowPriestess`, `DrowWarrior`, `DrowWizard`, `FireGiant`, `FrostGiant`, `HillGiant`, `StoneGiant`, `Gnoll`, `Goblin`, `LizardMan`, `LizardManShaman`, `Minotaur`, `Naga`, `Ogre`, `Orc`, `Troll`, `UmberHulk`, `Wyvern` |

Class IDs are exact (they are Java class names) and `CREATE MOB` matches them
case-insensitively. To list everything the game knows, use the editor's `?`
prompts or `LIST` (see `AHELP LIST`).

Three internal classes appear in the `MOBS` folder but are not for building:
`StdFactoryMOB` (a throwaway mob the engine uses to send messages),
`StdMobWrapper` and `StdRideableWrapper` (wrappers that stand in for another
object).

### Which class to use

| You want | Use |
| --- | --- |
| Any authored NPC: guard, villager, boss, quest giver | `GenMob` |
| A creature clerics can turn | `GenUndead` (or a GenMob with the Undead race) |
| A horse, ox, cart-puller, giant eagle | `GenRideable` or `GenHorse`; see [Rideable mobs](#rideable-mobs-and-mounts) |
| A milk cow | `GenCow` |
| A shop, trainer, caster, inn, pet or land seller | `GenShopkeeper` |
| Bank, post office, auction house, library, craft broker | `GenBanker`, `GenPostman`, `GenAuctioneer`, `GenLibrarian`, `GenCraftBroker` |
| A god for clerics | `GenDeity` |
| Cheap wildlife, no customization | a standard animal class |

## Creating a mob

Source: [`Commands/Create.java`](../../CoffeeMud/com/planet_ink/coffee_mud/Commands/Create.java)
(`mobs`).

```text
CREATE MOB [CLASS ID | CATALOG NAME | RACE/CLASS/LEVEL words]
```

The new mob appears in your room, with that room as its start room. You are told
"Suddenly, ... instantiates from the Java Plane."

What the argument can be, tried in this order:

1. **A mob class ID**, such as `GenMob`, `GenShopkeeper` or `Orc`.
2. **A catalog mob name** (`CREATE MOB the town guard`). A copy of the cataloged
   mob is placed and counted as a user of the catalog entry. The editor does not
   open, because the mob is already finished. See [The mob catalog](#the-mob-catalog).
3. **A race, a class, a level, or up to three of those in any order**, with an
   optional leading "a" or "an". A `GenMob` is made (a `GenRideable` if the race
   is a mount race). Examples:

```text
CREATE MOB a Dwarf Fighter 5
CREATE MOB Elf
CREATE MOB 12
```

   The race sets the race; the level sets the level; a character class
   (for example `Fighter`) names the mob "a Dwarf Fighter" and adds a
   `Prop_Trainer` effect built from that class (`SKILLS Fighter NOTEACH
   BASEVALUE=10` plus the class's stat minimums), making it a target for the
   `TRAIN` command in that class; see `AHELP Prop_Trainer`. With only a level
   the mob is named "a level 12er".
4. **A builder template** you saved with `TEMPLATE ADD` (`AHELP TEMPLATE`): your
   personal (optionally shared) favourites, looked up by their short ID.

If nothing matches you get "There's no such thing as a '...'".

After the mob is placed:

- Its rejuvenation is set to 2.5 minutes' worth of ticks (about 37 at the
  default 4-second tick; the cap is 20 minutes). For *non-generic* classes, the combat numbers are filled
  from the mob's level (`fillOutMOB`, below).
- If the mob is generic and was *not* taken from the catalog, the generic editor
  opens at once so you can set everything.
- The creation is logged ("created mob").

Because a new generic mob is a blank "a generic mob", always change at least its
Name, Display, Description, Level and Race.

### To create, test and save a basic mob

1. Start in builder mode and `GOTO` the room it belongs in.
2. `CREATE MOB GenMob`. In the editor set Name, Display, Description, Level and
   Race; set Rejuv Ticks (for example `150`); add behaviors; press ENTER to leave.
3. Equip it if needed ([Equipment and inventory](#equipment-and-inventory)).
4. Look at it: `LOOK [name]`, `STAT [name]` and `CONSIDER [name]`.
5. `SAVE ROOM`, and later `SAVE` to write the world files.
6. Test as an ordinary character (`PLAYTEST ON`, see
   [13-testing-release.md](13-testing-release.md)): fight it, kill it, wait out the
   rejuvenation time, leave and return.

## Editing a mob

Source: [`Commands/Modify.java`](../../CoffeeMud/com/planet_ink/coffee_mud/Commands/Modify.java)
(`mobs` and the default branch), `CMGenEditor.modifyGenMOB`,
`modifyGenShopkeeper`, `modifyStdMob`.

The mob must be in your room.

```text
MODIFY [mob name]
MODIFY MOB [mob name] MISC
MODIFY MOB [mob name] LEVEL [n]
MODIFY MOB [mob name] ABILITY [n]
MODIFY MOB [mob name] REJUV [ticks]
MODIFY MOB [mob name] [STAT CODE] [value]
```

- **`MODIFY orc`** (no type word) finds the thing named; for a mob it opens the
  editor.
- **`MODIFY MOB orc MISC`** opens the generic editor for a generic mob and sets
  the Misc Text for a standard one. Wrap multi-word names in quotes:
  `MODIFY MOB "the town guard" MISC`.
- **`LEVEL n`**, **`ABILITY n`** and **`REJUV n`** change those values.
  `REJUV 0` (or less) means the mob never comes back.
- **A stat code**, such as `NAME`, `DISPLAY`, `RACE`, `MONEY`, `STRENGTH`,
  `SPEED`, sets that stat directly (see
  [Stat codes](#stat-codes-for-modify-mob-and-gmodify)). Any other word lists
  the valid aspects: "...but failed to specify an aspect. Try one of: ...".
- `MODIFY MOB x LEVEL n` re-derives the mob's armor, attack, damage, speed, hit
  points, mana, movement and money from the new level (as `fillOutMOB` does,
  see below), keeping its rejuvenation time. The command checks the builder
  rather than the mob for generic-ness, so this happens for generic mobs too.

Players are refused ("is a player! Try MODIFY USER!"). Editing reports "<mob>
shake(s) under the transforming power" and is logged.

### The menu editor

In `MENU` style the editor prints every field with its number. Type a number to
edit that field; ENTER alone leaves the editor. In `WIZARD` style it walks
all fields. Notes common to all prompts:

- ENTER keeps the old value.
- Fields that may be empty accept `NULL` to clear them.
- A `?` answer shows help or the legal choices on fields that have them.
- Lists (abilities, behaviors, effects, tattoos, expertises, tags, shop
  inventory) work as toggles: type an ID to add it; type it again to remove it.
  The prompt repeats until you press ENTER on an empty line.
- On leaving, the mob is recomputed: character stats, maximum hit points,
  mana and movement, and it is brought to full health.
- If the text of the mob exceeds the engine's size limit, you are warned.

### Editing a cataloged mob

A mob created from the catalog is *tied* to its catalog entry. The editor
announces "*** This object is Cataloged ***". When you finish, if you changed
anything, you are asked to **U**pdate the cataloged version (changes every
copy), **D**etach this copy from the catalog, or **C**ancel (revert your edit).
Changing the Name alone asks for confirmation that it will detach the mob from
the catalog. See [The mob catalog](#the-mob-catalog).

### Editing a standard mob

`MODIFY orc` on a non-generic mob shows only:

| Field | Meaning |
| --- | --- |
| Level | The mob's level. |
| Magical Ability | A number whose meaning depends on the class. For most it is the hit point modifier (default 11). On dragons it is the colour code (see the dragon row above); the level sets the age (level / 10). The effect may only show after the mob reloads. |
| Rejuv Ticks | Ticks before it reappears after being killed; `0` means never. Takes effect when the room is saved. |
| Misc Text | A raw text field that most standard classes ignore. (On `StdShopKeeper` and its subclasses it is the Prejudice factors string.) |

It cannot be given equipment, behaviors, effects or scripts that survive a save.
To add those, use a `GenMob` with the same race.

## The generic mob editor: field reference

Source: `CMGenEditor.modifyGenMOB` (GenMob, GenUndead, GenRideable, GenHorse,
GenCow, GenDeity, and the other mob types without shops) and
`modifyGenShopkeeper` (every shopkeeper type). The first line printed is
`*. Class: <ID>`; it is informational.

Field numbers in the menu are assigned in the order below, but they shift with
what is installed (one line per faction that has `EDITALONE=TRUE`, a Scripts
line only when the mob has scripts, extra classes' fields). **Find a field by
its label, not its number.** In the default install the factions with their own
line are `alignment` and `inclination` (`triumph.ini` has `EDITALONE=FALSE`).

### Common fields (all generic mobs)

| Label | Stores | Notes |
| --- | --- | --- |
| Name | the mob's name | Used in messages and for targeting (`kill guard`). If the Display text starts with the old name, the new name replaces that start. If the mob is cataloged, changing the name asks to detach it from the catalog. |
| Display | the room line | Shown in the room listing, for example "A town guard stands here." `NULL` clears it. |
| Description | the `LOOK AT` text | Type `+` to open the multi-line editor. A description that already contains line breaks opens in the editor directly. |
| Level | the mob's level | Sets hit points, XP value and what the combat formulas see. If the old level was below 2 and the new one is 2 or more, the combat numbers are re-derived (see [Combat numbers](#combat-numbers-hit-points-and-levels)) and "Combat stats rescored" is printed. |
| Rejuv Ticks | ticks until it returns after death | `0` means it never rejuvenates ("will now never rejuvenate"). Also accepts values above `0`. See [Start rooms, rejuvenation and death](#start-rooms-rejuvenation-and-death). |
| Race | the race ID | Enter `?` to list races. Choosing a race applies the race's setup to the mob (`startRacing`) and resets its height and weight from the race (see [03-characters.md](03-characters.md)). |
| *(each faction with EDITALONE)* | a faction value | One line per such faction, for example `alignment: pure neutral (0)`. Enter a number, or the name of a range (for example `evil`) to put the value in that range. |
| Gender | `M`, `F` or `N` | The letters are the first letter of each entry of `GENDERS` in `resources/lists.ini`. |
| Height | height | Set from the race when the race is chosen; edit afterwards if needed. |
| Weight | weight | As Height. |
| Clan(s) | clan memberships | Enter a clan name to add the mob with a role you choose, enter a clan the mob already belongs to to remove it. For NPC organisations see [09-society.md](09-society.md). |
| Actions/Attacks per tick | speed | A decimal, 1.0 is one attack a round; 2.0 attacks twice. |
| Attack Adjustment | attack bonus | Higher is better at hitting. |
| Damage | base damage | The maximum damage of an unarmed or natural attack (a wielded weapon adds its own). |
| Armor (lower-better) | armor | Lower armor numbers mean harder to hit. Negative values are allowed. |
| Hit Points Bonus Modifier | "ability" | Not hit points: a modifier fed to the hit point formula (default 11). See [Combat numbers](#combat-numbers-hit-points-and-levels). |
| Money | coins carried | In multiples of the base currency unit (the local currency). |
| Money Variation | random extra money on death | The corpse also gets a random amount from 0 to this value. |
| Abilities | the abilities the mob knows | See [Abilities, behaviors, effects and scripts](#abilities-behaviors-effects-and-scripts). |
| Behaviors | attached behaviors with parameters | Same section. |
| Effects | permanent effects and properties | Same section. |
| Dispositions | the "is ..." flags | A letter list (see below). |
| Senses | the "can see ..." flags | A letter list (see below). |
| Factions | all faction values | Add or remove a faction by name, with a value. |
| Tattoos | tattoo names | Add or remove, one at a time. Tattoos are tags used by masks, quests and shops. |
| Expertises | learned expertises | Add or remove a lesson ID. |
| Scripts | list of MOBPROG scripts | Shown only when the mob has scripts; enter a script number to remove it. Scripts are added as a `Scriptable` behavior (see below). |
| MXP Image filename | image path | For clients that can display an image. May be empty. |
| Tags | tags | Free-form tags used for grouping and searching (`GMODIFY #tag`, masks, scripts). Add or remove one at a time. |
| *(extra variables)* | EXTVAR_ fields | Any `EXTVAR_GenMob=...` entries in `coffeemud.ini` appear here as extra prompted fields. |

**Dispositions** are toggled by letter:

| Letter | Flag | Letter | Flag |
| --- | --- | --- | --- |
| A | invisible | I | sleeping |
| B | hiding | J | sitting |
| C | unseen | K | sneaking |
| D | magical (bonus) | L | swimming |
| E | glowing | M | evil |
| F | a light source | N | good |
| G | flying | O | unattackable |
| H | climbing | | |

**Senses** are toggled by letter:

| Letter | Flag | Letter | Flag |
| --- | --- | --- | --- |
| A | darkvision | G | sees evil |
| B | sees hidden | H | sees magic |
| C | sees invisible | I | mute |
| D | sees sneakers | J | deaf |
| E | infrared | K | blind |
| F | sees good | L | sees hidden items |

(The on-screen list shows the same flags with their descriptive names.
`unattackable` makes a mob immune to being attacked; `blind`, `mute` and `deaf`
are handicaps, not abilities.)

### Fields added by subclass

These appear after Senses (rideables, drinks, deities) and, for shop types,
inside the shop block described in
[Shopkeepers and other service mobs](#shopkeepers-and-other-service-mobs).

| Class | Label | Notes |
| --- | --- | --- |
| `GenRideable` and subclasses | Rideable Type | One letter: `L` land, `W` water, `A` air (flying), `C` chair (furniture-sit), `B` bed (furniture-sleep), `T` table, `E` enter-in, `D` ladder, `G` wagon, `H` hook. See [Rideable mobs and mounts](#rideable-mobs-and-mounts). |
| | Rider capacity | How many can ride at once. |
| | Mount Strings | Three texts: "put", "mount" and "dismount" strings. |
| | State Strings | Three texts: "state", "state subject" and "ride verb" strings. |
| `GenCow` | Quenched/Drink | How much thirst a drink satisfies. |
| | Amount of Drink Held | The amount the mob holds; it refills itself. |
| | Liquid Type | The liquid, for example fresh water or milk. |
| `GenDeity` | Cleric Requirements, Cleric Ritual, Worshiper Requirements, Worshiper Ritual, Blessings, Cleric Sin, Worshiper Sin, Curses, Cleric Power Ritual, Powers, Service Ritual | See [Deities](#deities). |

## Stat codes for MODIFY MOB and GMODIFY

`MODIFY MOB [name] [STAT] [value]` and `GMODIFY` read and write stats by name.
For a generic mob, the following codes exist on every mob; they are the same
names used in `GMODIFY ... FIELD=VALUE` and by the scripting engine's
`getStat`/`setStat`.

| Code | Meaning |
| --- | --- |
| `CLASS` | The Java class ID (read-only in effect). |
| `RACE` | The race ID. |
| `LEVEL` | Level. |
| `ABILITY` | The hit point modifier ("Magical Ability"). |
| `NAME`, `DISPLAY`, `DESCRIPTION` | The three text fields. |
| `MONEY` | Coins carried; `VARMONEY` is the money variation. |
| `ALIGNMENT` | The alignment faction value. |
| `DISPOSITION`, `SENSES` | The numeric flag masks. |
| `ARMOR`, `DAMAGE`, `ATTACK`, `SPEED` | Combat numbers. |
| `AFFBEHAV` | Effects and behaviors as XML (read for copying). |
| `ABLES` | Abilities as XML. |
| `INVENTORY` | Inventory as XML. |
| `TATTS` | Tattoos, semicolon-separated. |
| `EXPS` | Expertises. |
| `IMG` | Image path. |
| `FACTIONS` | All factions as XML. |
| `TAGS` | Tags. |

The same command also accepts stats of the mob's physical stats, character stats
(`STRENGTH`, `INTELLIGENCE`, `DEXTERITY`, `CONSTITUTION`, `CHARISMA`, `WISDOM`,
the saving throws, and so on) and its base state (`HITS`, `MANA`, `MOVE`,
`HUNGER`, `THIRST`, `FATIGUE`). A wrong word makes `MODIFY MOB` print the full
list of valid names for that mob. `BASE_` or `CURRENT_` prefixes select the
base or current value.

Subclasses add:

| Class | Extra codes |
| --- | --- |
| `GenShopkeeper` | `WHATISELL` (a number or a type name), `PREJUDICE`, `BUDGET`, `DEVALRATE`, `INVRESETRATE`, `IGNOREMASK`, `PRICEMASKS`, `ITEMMASK`, `SIVIEWTYPES` |
| `GenBanker` | `WHATISELL`, `PREJUDICE`, `BANKCHAIN`, `COININT`, `ITEMINT`, `LOANINT`, `IGNOREMASK`, `PRICEMASKS`, `ITEMMASK`, `SIVIEWTYPES` |
| `GenPostman` | `WHATISELL`, `PREJUDICE`, `POSTCHAIN`, `POSTMIN`, `POSTLBS`, `POSTHOLD`, `POSTNEW`, `POSTHELD`, `IGNOREMASK`, `PRICEMASKS`, `ITEMMASK`, `SIVIEWTYPES` |
| `GenAuctioneer` | `WHATISELL`, `PREJUDICE`, `AUCHOUSE`, `LIVEPRICE`, `TIMEPRICE`, `TIMEPCT`, `LIVECUT`, `TIMECUT`, `MAXADAYS`, `MINADAYS`, `IGNOREMASK`, `PRICEMASKS`, `ITEMMASK`, `SIVIEWTYPES` |
| `GenCraftBroker` | `WHATISELL`, `PREJUDICE`, `BROKERCHAIN`, `MAXLISTINGS`, `MAXDAYS`, `IGNOREMASK`, `PRICEMASKS`, `ITEMMASK`, `SIVIEWTYPES`, `BUDGET`, `COMPCT` |
| `GenLibrarian` | `WHATISELL`, `PREJUDICE`, `LIBRCHAIN`, `LIBROVERCHG`, `LIBRDAYCHG`, `LIBROVERPCT`, `LIBDAYPCT`, `LIBMINDAYS`, `LIBMAXDAYS`, `LIBMAXBORROW`, `LIBCMASK`, `IGNOREMASK`, `PRICEMASKS`, `ITEMMASK`, `SIVIEWTYPES` |
| `GenRideable` and subclasses | `RIDEBASIS`, `MOBSHELD` (rider capacity), `PUTSTR`, `MOUNTSTR`, `DISMOUNTSTR`, `STATESTR`, `STATESUBJSTR`, `RIDERSTR` |
| `GenCow` | `QUENCHED`, `LIQUIDHELD`, `LIQUIDTYPE` |
| `GenDeity` | `CLERREQ`, `CLERRIT`, `WORREQ`, `WORRIT`, `SVCRIT` |

Example, from the repository's `GMODIFY` help:

```text
GMODIFY AREA CLASS=GenMob&&NAME=the cityguard CHANGE=NAME=the guard
GMODIFY #mytag CHANGE=ABILITY=12
```

The librarian's contributor mask (who may donate books) is `LIBCMASK`, and is
not shown in the menu editor; set it with
`MODIFY MOB [librarian] LIBCMASK -RACE +Elf`.

## Combat numbers, hit points and levels

A generic mob's fighting strength is *authored*: whatever is stored in its
Attack Adjustment, Damage, Armor and Actions per tick is what it fights with.
Setting a Level does not by itself change them, with two exceptions:

1. In the editor, when the level goes from below 2 to 2 or more, the combat
   numbers are re-derived and "Combat stats rescored" is shown.
2. `MODIFY MOB [name] LEVEL [n]` always re-derives them.

The re-derivation is `CMLib.leveler().fillOutMOB`. It sets, from the new
level:

| Number | Source |
| --- | --- |
| Rejuvenation | `FORMULA_NPCREJUV` game minutes (default `(2+(@x1/2))<20`: 2 plus half the level, at most 20) times ticks per minute. |
| Speed | `FORMULA_NPCSPEED` (default one attack, plus one more each 30 levels). |
| Armor | `FORMULA_NPCARMOR`. |
| Damage | `FORMULA_NPCDAMAGE` (default the level). |
| Attack | `FORMULA_NPCATTACK`. |
| Money | `FORMULA_NPCMONEY`. |
| Hit points, mana, movement | from the level, class and the "ability" modifier. |
| Experience | from the level. |

The `FORMULA_NPC*` formulas live in `coffeemud.ini` (a world can override them
in `world.ini`); `@x1` is the level, `@x2` the last player level, `@x3` the
mob's ability modifier, `@x5` and `@x6` rolls for money. See
[world-settings.md](world-settings.md) and
[29-combat-and-encounters.md](29-combat-and-encounters.md).

**Hit points are not stored.** Whenever a generic mob is loaded or reset, its
maximum hit points are rolled from its level and the Hit Points Bonus Modifier
using `FORMULA_NPCHITPOINTS` (default
`3 + @x1 + (@x1 * ((@x2/2) + (1?(@x2/2))))`, with `@x1` the level and `@x2` the
modifier, 11 by default; `1?n` is a random roll from 1 to n). Raise the modifier
for tougher monsters (12-20 is "really nasty"). Editing `HITS` directly or the
`baseState` does not persist for generic mobs; use the modifier, or add a
`Prop_Adjuster` effect such as `hitpoints+50` (the Ember Trial sample does this;
see its README). The difficulty setting (`DIFFICULTY`) scales NPC hit points in
play without changing the stored numbers.

To re-normalize many mobs at once use `RESET MOBCOMBATSTATS` (see
[Copying, destroying and bulk editing](#copying-destroying-and-bulk-editing)).
`Prop_MobRank` (`RANK=MINION|NORMAL|ELITE|BOSS`) adds a rank's multipliers on
top; see [23-properties-reference.md](23-properties-reference.md#prop_mobrank).

## Race, class, gender and factions

**Race.** The Race field sets the body: size, senses, breathing, natural
weapons, corpse behaviour and which wear locations exist. A `GenMob` with the
Human race wields and wears like a person; with the Dog race it has a dog's
body. Display text alone grants nothing, so choose the race first, then add
only what the race lacks. The race of a `GenUndead` starts as Undead.
Custom races are in [03-characters.md](03-characters.md).

**Class.** The generic mob editor has *no* character-class field. A mob's
fighting style comes from its abilities and from combat behaviors
(`Fighterness`, `Mageness`, `Clericness`, `Thiefness`, `CombatAbilities`,
...; see [Abilities, behaviors, effects and scripts](#abilities-behaviors-effects-and-scripts)),
not from a class. The only places a class name enters are `CREATE MOB
[race] [class] [level]`, which attaches a `Prop_Trainer` for that class, and
`Prop_Trainer` / `MOBTeacher` for mobs that train or teach players.

**Gender.** `M`, `F` or `N` (from the `GENDERS` list in `lists.ini`).

**Factions and alignment.** Factions are numeric standings with ranges
(`alignment` runs from -10000 to 10000 and has ranges from "pure evil" to "pure
goodness"). A mob's alignment decides, with its level, how much experience a
character gets for killing it, and which creatures are "good" or "evil" to
spells and masks. Set `alignment` to reflect the creature: children and
innkeepers are not evil. To use another faction on a mob, add it in the
Factions field with a value, or give the mob a script that sets it. Faction
definitions live in `resources/factions/*.ini` and are covered in
[09-society.md](09-society.md).

## Abilities, behaviors, effects and scripts

Four separate lists in the editor hold things a mob does. They are easy to
confuse.

| List | What it holds | Who uses it |
| --- | --- | --- |
| Abilities | Skills, spells, prayers, songs and other abilities the mob *knows* | The mob can invoke them; `CombatAbilities`-family behaviors choose when. |
| Behaviors | Behavior objects (AI) with parameters | See [24-behaviors-and-masks.md](24-behaviors-and-masks.md). |
| Effects | Properties and spell-like effects that are *always on* | See [23-properties-reference.md](23-properties-reference.md). |
| Scripts | MOBPROG script objects attached to the mob | See [04-mobprog.md](04-mobprog.md). |

### Abilities

The prompt is "Enter an ability to add/remove (?)". Type an ability's ID to add
it; type the ID again to remove it (this also removes the same effect if the
ability put one on the mob). `?` lists abilities. To give arguments, add them in
brackets at once: `Spell_Fireball(arg text)`; otherwise you are asked "Enter any
arguments". An ability added in the editor has 75% proficiency. Abilities of the
Archon domain can only be added by a sysop.

Giving a mob abilities is not enough: it must also have a combat behavior
(`CombatAbilities`, or one of its subclasses `Fighterness`, `Mageness`,
`Clericness`, `Thiefness`, ...) to *use* them in a fight. A subclass already
comes loaded with the abilities of its type; do not stack two of them. Put
properties (`Prop_*`) in Effects, not in Abilities.

### Behaviors

Type a behavior's ID to add it; you are asked "Enter any behavior parameters
(?)" and `?` there prints that behavior's help. Type the ID again to remove it.
`?` at the main prompt lists behaviors, highlighting those that can improve this
kind of host. A behavior can appear only once on a mob.

A `Scriptable` behavior (MOBPROG) opens the multi-line text editor for its
parameters. A script is either written inline or loaded from a file:

```text
LOAD=progs/museum-guide.script
```

Files named like this are looked up in `resources/progs/` (a world's own
`world/resources/progs/` takes precedence). See
[04-mobprog.md](04-mobprog.md) for the language.

The most used behaviors on mobs, with the chapter that documents each:

| Behavior | Gives the mob |
| --- | --- |
| `Mobile` | Random wandering. `min=10 max=20 chance=75 wander` moves between rooms; `leash=3` keeps it near home; `opendoors` opens doors. Mobs will not walk out of a room with a visible builder (CMDMOBS) in it. |
| `Aggressive`, `MobileAggressive` | Attacks characters on sight (masks restrict whom). |
| `CombatAbilities`, `Fighterness`, `Mageness`, `Clericness`, `Thiefness` | Use of known abilities in fights. |
| `MudChat` | Chat from `resources/chat.dat` groups. See [06-npcs-dialogue.md](06-npcs-dialogue.md). |
| `Guard`, `GoodGuardian`, `GateGuard`, `DoorwayGuardian`, `Sentry` | Defending others, doors and gates. |
| `MOBTeacher`, `Hireling`, `Escortee`, `Patroller`, `Schedule` | Teaching, hire, escort, patrol, daily routine. See [06-npcs-dialogue.md](06-npcs-dialogue.md). |
| `RandomMonsters`, `WaveSpawner`, `RandomEncounters` | Spawners (host is a room or area; see [Spawning](#spawning)). |

All behaviors accept a `WHEN=` condition (see
[24-behaviors-and-masks.md](24-behaviors-and-masks.md)).

### Effects

Effects are permanent properties, such as `Prop_MobRank` or
`Prop_Resistance`, or permanent spell effects such as `Spell_Levitate` (flying).
The prompt is "Enter an effect to add, -ID to remove, =ID or its number to edit,
?P for the properties that fit (?)":

| Entry | Does |
| --- | --- |
| `ID` | Adds the effect and asks for its parameters (`?` shows its help; `NULL` empties them). |
| `-ID` | Removes it. |
| `=ID` or its list number | Edits its parameters in place, showing the current ones; ENTER keeps them. |
| `?` | Lists all abilities; those that can affect this mob are marked. |
| `?P` | Lists only properties and effects that fit this mob, each with a line of its help. |

Typing an ID the mob already has offers `Prop_MultiEffects`, which can hold
both copies; a second copy on its own would be ignored.

Properties that matter on mobs include `Prop_MobRank`, `Prop_Essential`,
`Prop_PartyScaler`, `Prop_Resistance`, `Prop_Adjuster`, `Prop_RejuvWhen`,
`Prop_Trainer`, `Prop_QuestGiver`, `Prop_QuestMarker`, `Prop_NPCLines` and
`Prop_Affinity`; all are in
[23-properties-reference.md](23-properties-reference.md).

### Scripts

The Scripts line appears when the mob has script objects attached (for example
by a quest). Enter a script's number to remove it. Author new scripts as a
`Scriptable` behavior as above.

## Money

The Money field is the amount of base currency the mob carries (a "20" is 20
gold in the default currency, whatever coins that is made of in the area's
currency). Money Variation adds a random 0..N more to the *corpse* when the mob
dies. A thief can take it first.

Inside the file, coins are folded into `<MONEY>`; carried coin objects are not
stored one by one. When you edit the Money field, loose coins in the inventory
are first merged into it.

Shopkeepers and bankers use their own currency setting; see
[Shopkeepers](#shopkeepers-and-other-service-mobs) and
[09-society.md](09-society.md).

## Equipment and inventory

Only a *generic* mob keeps equipment when the world is saved. (A standard mob
such as `Skeleton` creates its gear in its constructor each time.) Any item,
generic or not, can be carried or worn.

To equip a mob in builder mode:

1. Get the item into **your own inventory**. Create it and pick it up:

   ```text
   CREATE ITEM Longsword
   GET longsword
   ```

2. Make the mob wear or wield it with `DRESS` (a builder power; the target must
   be a monster in the room). `DRESS` takes the item from your inventory, gives
   it to the mob and puts it on in one step:

   ```text
   DRESS town guard longsword
   ```

   `DRESS [mob] [item]` takes the mob first and a *single word* for the item
   last. The mob puts the item on in the first slot that fits, or holds it if
   nothing fits. (Everything before the last word is the mob's name, so
   `DRESS town guard longsword` means the mob `town guard` and the item
   `longsword`.)

3. Save the room (`SAVE ROOM`, see [How mobs are saved](#how-mobs-are-saved)).

To give a mob something to *carry* (loot, a key, goods), without wearing it,
create the item straight into its pack with the `@` form, or `GIVE` it:

```text
CREATE ITEM StdKey@town guard
```

Carried or worn, the item is saved with the mob.

Edit a carried item with `MODIFY ITEM sword@guard`. The item types and their
fields are in [22-items.md](22-items.md).

### Variable equipment (the item's Rejuv/Pct)

An item in a mob's inventory has a Rejuv/Pct field with a different meaning
from an item lying in a room. In a mob's pack:

| Value | Meaning |
| --- | --- |
| `0` | Always there. |
| `1` to `99` | N% chance, each time the mob is created or rejuvenates, that the item is *absent* (the engine removes it when a 1-100 roll is below N). |
| `100` or more | Absent almost every time; not a useful setting. |
| `-1` | Present only the first time (at boot or creation); gone after the mob rejuvenates. |

If several items on the same mob have a value and share a wear location, exactly
one of them is kept; the chance of being kept is weighted by the values.

Edit it with `MODIFY ITEM sword@guard` and choose the Rejuv/Pct field.

Cautions:

- The variable-equipment rules do **not** run while a builder (a player with
  `CMDMOBS`) stands in the room with the mob. If you finish editing and save
  while a mob has variable gear, the gear stays as it is. Use `RESET ROOM`
  before editing again so the mob regains items you want to edit.
- Do not copy a mob's variable item into a room with its Rejuv/Pct set: in a
  room, that field means something else (the ticks until the item reappears).

For gear that depends on the story, consider scripts or a catalog item with
auto-drop rules ([The mob catalog](#the-mob-catalog)); for loot tables see
[12-generation.md](12-generation.md).

## Shopkeepers and other service mobs

Source: `CMGenEditor.modifyGenShopkeeper`,
[`StdShopKeeper`](../../CoffeeMud/com/planet_ink/coffee_mud/MOBS/StdShopKeeper.java),
[`ShopKeeper`](../../CoffeeMud/com/planet_ink/coffee_mud/core/interfaces/ShopKeeper.java),
`Libraries/CoffeeShops.java`. Economic theory (currencies, taxes, area
defaults) is in [09-society.md](09-society.md); this section is the mob
side.

Every `Gen*` service mob uses the shop editor. It has the common mob fields
(see above, up to Effects; here Height and Weight come right after Race and
before the faction lines, Gender and Clan(s)) followed by the shop block, then
Dispositions, Senses, Factions, Tattoos, Expertises, Scripts, Image and Tags as
usual. Players deal with them using `LIST`, `BUY`, `SELL`, `VALUE`, `VIEW` and
`INFO`, plus type-specific verbs below.

### The shop block

| Label | Applies to | Meaning |
| --- | --- | --- |
| Shopkeeper type | all but the auctioneer | What the mob buys and sells (a set of toggles; see below). |
| Item Buy Mask (?) | all but the auctioneer | A zapper mask that further narrows the items the shop deals in. `?` shows the mask syntax ([24-behaviors-and-masks.md](24-behaviors-and-masks.md)). Empty means no extra filter. |
| Inventory | all but the auctioneer | The stock. See below. |
| View flags/types | all but the auctioneer | What `VIEW` reveals: `BASIC`, `IDENTIFY` or `FALSE` (toggle each). |
| Prejudice | all but the auctioneer | Price adjustments by customer attributes. |
| Inventory reset rate [ticks] | all but the auctioneer | How often the stock returns to the saved stock. |
| Currency | all | The currency the shop deals in. |
| Ignore Mask | all | A zapper mask of customers the shopkeeper rudely ignores. |
| Item Pricing Factors | plain shopkeepers (and trainers, casters, ...) | Price multipliers by item mask. |
| Budget | plain shopkeepers | How much the shop spends buying from you per time period. |
| Devaluation rate(s) | plain shopkeepers | How far its buying price drops per copy already held. |

Banker, postman, librarian, auctioneer and craft broker replace the last three
(Item Pricing Factors, Budget, Devaluation) with their own fields, below.

Anything left empty or at 0 in Prejudice, Item Pricing Factors, Ignore Mask,
Budget, Devaluation and Inventory reset rate is inherited: first from the
shopkeeper's start area (and its parent areas), then from `coffeemud.ini` (the
defaults in `PREJUDICE`, `PRICEFACTORS`, `IGNOREMASK`, `BUDGET`, `DEVALUERATE`
and `INVRESETRATE`; an area sets them with its own economy fields).
`INVRESETRATE` defaults to 64800 ticks. For `Currency`, `DEFAULT` means the
area's currency.

### Shopkeeper type

The type prompt lists letters; choosing a letter toggles that type. A shop may
have several types at once, except for conflicting pairs (post office with clan
post office, bank with clan bank, the four land and ship sellers, trainer with
caster). Choosing `0` clears everything and makes the shop deal in anything.
Removing a type also removes stock it no longer covers.

| Code | Type | The shop buys and sells |
| --- | --- | --- |
| `0` | ANYTHING | Anything except land titles and children. |
| `1` | GENERAL | Ordinary items: not armor, magic, clan items, weapons, ammunition, raw resources, land titles, boats, caged animals or abilities. |
| `2` | ARMOR | Armor. |
| `3` | MAGIC | Miscellaneous magic items (wands, scrolls, potions, ...). |
| `4` | WEAPONS | Weapons and ammunition. |
| `5` | PETS | Animals (mobs or caged animals of animal intelligence). |
| `6` | LEATHER | Leather items (not raw resources). |
| `7` | INVENTORY ONLY | Only what is already in its stock; it buys back what it sells, nothing else. |
| `8` | TRAINER | Teaches the abilities stocked as inventory (a skill/spell/prayer/song teacher). Does not buy. |
| `9` | CASTER | Casts the stocked abilities on the buyer for a price. Does not buy. |
| `A` | JEWELRY | Glass or precious items, and items worn on ears, neck or fingers. |
| `B` | POTIONS | Potions. |
| `C` | BANKER | (Bankers only.) |
| `D` | LAND | Land titles for sale (rooms in the shop's area that carry sale properties; see `Prop_RoomForSale`, `Prop_RoomsForSale`, `Prop_LotForSale`, `Prop_LotsForSale`). |
| `E` | ANY TECHNOLOGY | Electronics. |
| `F` | CLAN LAND | Land titles for clan members only. |
| `G` | FOODS | Food and drink (not raw resources). |
| `H` | MEATS | Raw flesh resources. |
| `I` | VEGETABLES | Raw plant resources. |
| `J` | HIDES | Hide, feathers, leather, scales, wool and fur resources. |
| `K` | LUMBER | Raw wood. |
| `L` | METALS | Raw metals and mithril. |
| `M` | ROCKS | Raw stone and precious materials. |
| `N` | CLAN BANKER | (Bankers only.) |
| `O` | INN KEEPER | Inn keys (`StdInnKey`). See [Innkeepers](#innkeepers). |
| `P` | SHIP SELLER | Ships and ship titles. |
| `Q` | CLAN SHIP SELLER | Ships, for clan members only. |
| `R` | SLAVES | Mobs or caged mobs that are slaves. |
| `S` | POSTMAN | (Post offices only.) |
| `T` | CLAN POSTMAN | (Post offices only.) |
| `U` | AUCTIONEER | (Auctioneers only.) |
| `V` | INSTRUMENTS | Musical instruments. |
| `W` | BOOKS | Readable items whose class ID ends in `Book`. |
| `X` | READABLES | Any readable item. |
| `Y` | CLOTHS | Raw cloth resources. |
| `Z` | CHILDREN | Children (aged mobs). |

Bankers are offered only types `C` and `N`; post offices `S` and `T`; the
auctioneer has no type prompt. All other shop mobs (including librarians and
craft brokers) are offered every type except `C`, `N`, `S` and `T`.
The `WHATISELL` stat code accepts a type name, a single type number (0 to 35, the
code's position in this table) or the full stored bit mask.

### Inventory (stock)

The Inventory prompt is "Enter something to add/remove (?)". Type a name that
is already in stock to remove it (every copy goes). Otherwise type one of:

- the **class ID** of a standard item, mob or ability (`Longsword`, `Horse`,
  `Spell_Sleep`); `?` lists the possibilities;
- the **name of an item or inhabitant in the room, or an item you carry** (a
  copy of it is stocked). This is the *only* way to stock a generic item or
  generic mob, so build the item first.

If the shop type does not deal in that thing, you are asked to confirm. You are
then asked "How many?" (not for abilities) and "At what price?":

| Price | Meaning |
| --- | --- |
| `0` or more | A fixed price in multiples of the base currency unit, before charisma, prejudice and the other adjustments. |
| `-1` | Let the engine price it from the item's value (recommended). |
| `-2` to `-99` | Treated like `-1`. |
| `-100` to `-999` | Sold for quest points: the price is the absolute value minus 100 (`-135` is 35 quest points). |
| `-1000` or lower | Sold for experience points: the absolute value minus 1000 (`-1500` is 500 XP). |

A shop that sells for quest points or experience points buys nothing back
of those items.

Stock for *trainers* and *casters* is abilities, stocked by ID, with no count. A
trainer teaches them for the listed price. The saved stock of a generic
shopkeeper is put back by the periodic inventory reset (Inventory reset rate)
and when the mob is reset; goods the shop bought from players are removed then.

### Prejudice, price factors, budget and devaluation

**Prejudice** adjusts prices by who the customer is. It is a list of rules
separated by semicolons; each rule is `BUY=` (when the customer buys from
the shop) or `SELL=` (when the customer sells to the shop) followed by a
number and, optionally, words that name which customers it applies to:

```text
BUY=5.5 Elf Dwarf ; SELL=0.4 Elf Dwarf ; BUY=1.0 ; SELL=1.0
```

Rules are read left to right and the first matching one wins, so list
specific ones first and a bare `BUY=`/`SELL=` last. The words can be a race
or racial category, an alignment (`good`, `neutral`, `evil`), a gender, a
class name, a faction range name, or a tattoo. `CLAN:name` matches members of a
clan (underscores stand for spaces), and `MASK(...)` takes a zapper mask:

```text
BUY=0.8 MASK(-CLASS +Fighter)
```

A bare number (no `=`) is one factor for everyone. Values must be above 0 (use
`0.000000001` to all but forbid trade). Two extra settings may appear in the
same field: `LIMIT=n` (the most rows a `LIST` shows) and `RANGE=n` (the level
range, around the median level of the shop's area, of items it will buy).

**Item Pricing Factors** multiply the price by item type. Each is a number and a
zapper mask, for example `0.5 -MATERIAL +WOODEN` halves wooden items; the editor
asks for the multiplier and then the mask (`?` explains the syntax), and removes
a factor by its number. In `coffeemud.ini` and the `PRICEMASKS` stat code
several are written separated by semicolons.

**Budget** is the most gold the shop spends buying from the player per period:
an amount followed by a period (`HOUR`, `DAY`, `WEEK`, `MONTH`, `YEAR`), for
example `5000 DAY`. Periods are game-time periods. The default is `500000 MONTH`.

**Devaluation rate(s)** is the percent drop of its buying price for each copy
of an item it already holds: `20 0.2` means 20% per finished item, 0.2% per raw
resource. One number applies to both. The default is `20 0.2`; an empty value
inherits from the area.

The customer's charisma against the keeper's wisdom also adjusts every price
(roughly twice the price at charisma 0, the base price at 35, golems excepted).
`PRICECHECK [item] (AT [shopkeeper]) (FOR [character]) (SELL)` shows where a
price came from, step by step (builder mode, `DEV=1`, or `CMDMOBS`): see
`AHELP PRICECHECK`.

### Bankers

`GenBanker` has types `C` (Banker) or `N` (Clan banker). Players use `DEPOSIT`,
`WITHDRAW`, `BORROW` and `LIST`. Extra fields:

| Label | Meaning |
| --- | --- |
| Coin Interest [% per real day] | Interest paid (negative: charged) on money left in an account. |
| Item Interest [% per real day] | Interest on items left in the account. Making it above 0.5 disables holding items. |
| Bank Chain | Name tying banker mobs together: all bankers with the same chain share accounts. |
| Loan Interest [% per mud month] | Interest charged on loans. Making it below -0.5 disables loans. |

### Post office clerks

`GenPostman` has types `S` (Postman) or `T` (Clan postman). Players use `GIVE`,
`WITHDRAW`, `LIST` and `SAY`. Extra fields:

| Label | Meaning |
| --- | --- |
| Postal chain | Mail travels only among postmen of one chain. |
| Fee to open a new box | Cost to open a box with this clerk. |
| Minimum postage cost | Minimum charge to deliver. |
| Postage cost per pound after 1st pound | Per-pound charge. |
| Holding fee per pound per month | Charged each mud month a parcel sits idle (first month free). |
| Maximum number of months held | After this the parcel is sold to recoup costs. |

### Auctioneers

`GenAuctioneer` takes only the auction type; no stock. Players use `BID`,
`SELL`, `INFO` and `LIST`. Extra fields:

| Label | Meaning |
| --- | --- |
| Auction house | The chain name; all auctioneers with the same name list the same items. |
| Flat fee per auction | Fee for each listing, in base gold. |
| Listing Cut/%Pct per day | Percent of the base value, per day, charged at listing. Keep it small (0.5%). |
| Cut/%Pct of final price | Percent of the winning bid kept by the house. |
| Maximum number of auction mud-days | Longest auction. |
| Minimum number of auction mud-days | Shortest auction. |

### Craft brokers

`GenCraftBroker` brokers requests: a player lists what they want with `REQUEST`;
others `SELL` matching items to the broker. Extra fields:

| Label | Meaning |
| --- | --- |
| Broker Chain | Name linking brokers' listings. |
| Maximum number of listing mud-days | Longest a listing stays; 0 is forever. |
| Maximum number of listings | Per player; 0 is unlimited. |
| Commission Pct% | Percent of the final price charged to the buyer. |

### Librarians

`GenLibrarian` loans items instead of selling. Players use `BORROW`, `GIVE`,
`LIST`, `SELL` and `VIEW`. Extra fields:

| Label | Meaning |
| --- | --- |
| Library name | Chain name; librarians sharing it share check-out records. |
| Max borrowed | Most distinct items a character may hold at once. |
| Overdue mud-days | Days after check-out before charges begin. |
| Reclaim mud-days | Days after which the item returns on its own (charges stay). |
| Overdue charge, Overdue charge pct | Flat amount and percent of the item's value when overdue. |
| Daily overdue charge, Daily overdue charge pct | Flat amount and percent for each further day. |
| (not in the menu) `LIBCMASK` | Who may contribute items. Set with `MODIFY MOB [name] LIBCMASK [mask]`. |

### Innkeepers

An inn is a `GenShopkeeper` with type `O` (INN KEEPER) stocking `StdInnKey`
items, one per room to let. Each inn room needs a locked door whose key code is
`INN1`, `INN2`, ... A key added to the keeper's stock is renamed "key to room
N" and given the code `INN<N>` in the order it is stocked. Players buy keys; a
key returns to the shop's stock after one mud day, and works only in the
keeper's area. After stocking the keys run `RESET ROOM` on the keeper's room
and save it.

### Land and ship sellers

Type `D` (or `F` for clan members) sells the rooms of the shopkeeper's area
that have `Prop_RoomForSale`, `Prop_RoomsForSale`, `Prop_LotForSale` or
`Prop_LotsForSale`; `P` and `Q` sell ships. Housing is covered in
[07-items-crafting.md](07-items-crafting.md).

## Rideable mobs and mounts

A rideable mob is a creature other mobs and characters can mount: `GenRideable`,
`GenHorse`, `GenRideableUndead` or the non-generic `Horse`, `Pegasus`,
`PegasusGreater` and `StdRideable`. `CREATE MOB` with a race flagged for riding
(Equine, Pachyderm, Centaur, Dragon, Drider, GiantSeaHorse, Mustelid,
RustMonster, or a custom race with `CANRIDE`) makes a `GenRideable` rather than
a `GenMob`.

Players ride with `MOUNT [name]` (also `BOARD`, `RIDE`, `M`) and leave with
`DISMOUNT`. Skills such as `Fighter_CallSteed`, `Skill_StableMount` and
`Skill_MountedCombat` work on these classes; stables and travel are in
[30-travel-vehicles-instances.md](30-travel-vehicles-instances.md).

To make a horse:

```text
CREATE MOB GenHorse
```

and in the editor set Name ("a chestnut mare"), Display, Description, Level,
Race `Horse`, Rideable Type `L` and Rider capacity (1 or 2). The default
`GenRideable` is a land mount with capacity 2.

| Rideable Type | Letter | Used for |
| --- | --- | --- |
| land-based | `L` | Horses, camels, wagons' pullers. |
| water-based | `W` | Swimming mounts. |
| air-flying | `A` | Griffons, pegasi, dragons. Gives the rider flight while mounted. |
| furniture-sit | `C` | A chair-like mob or object. |
| furniture-sleep | `B` | A bed. |
| furniture-table | `T` | A table. |
| enter-in | `E` | Something you enter (a wagon cab). |
| ladder | `D` | Something you climb. |
| wagon | `G` | A cart or wagon. |
| furniture-hook | `H` | A hook or perch. |

The **Mount Strings** are the words in the messages: "put" (default "on"),
"mount" (default "mount(s)") and "dismount" (default "dismount(s)"). The **State
Strings** are "state" ("being ridden by" by default), "state subject" ("riding
on") and the "ride verb" for how the rider describes it. Leaving one empty
(or typing the default) uses the engine default.

A rideable can also be a normal fighting mob: give it behaviors, abilities and
a level as for a `GenMob`. A rideable that wanders will carry its riders; give
it `Prop_Resistance`, `Skill_Dodge` or armor as needed. A mount that should
not wander off needs no `Mobile` behavior.

## Deities

A `GenDeity` is a god that clerics work for and characters worship. Place it
in a protected room (give the room `Prop_NoSummon` and `Prop_NoTeleport`, so
nobody drags the god away or kills it) and give it weapons if you want its
worshippers' divine magic to follow its weapon type. In addition to the common
fields it has:

| Label | Meaning |
| --- | --- |
| Cleric Requirements | A mask (the `Prop_WearZapper` syntax) for who may serve as its cleric. |
| Cleric Ritual | The actions a cleric performs to receive all the god's blessings. |
| Worshiper Requirements | A mask for who may worship it. |
| Worshiper Ritual | The actions a worshiper performs to receive one random blessing. |
| Blessings | Abilities it grants (each flagged for clerics only or for everyone). |
| Cleric Sin, Worshiper Sin | The actions that bring its curses on a cleric (all) or worshiper (one random). |
| Curses | Abilities inflicted as punishment. |
| Cleric Power Ritual | The ritual that earns a cleric one of its Powers; the power is taken away if the cleric rebukes the deity. |
| Powers | Abilities it may give its clerics. |
| Service Ritual | The steps of a religious service held in a room infused with the deity. |

Ritual fields share one step syntax, shown with `?` at the prompt and in
`AHELP` for the property of the same family. The stat codes are `CLERREQ`,
`CLERRIT`, `WORREQ`, `WORRIT` and `SVCRIT`. Characters join a faith in play with
the commands described in [03-characters.md](03-characters.md).

## Start rooms, rejuvenation and death

**Start room.** Every placed mob has a start room, its home. `CREATE MOB` and
`COPY` set it to the room you stand in. `SAVE ROOM` writes each mob into its own
start room (see [How mobs are saved](#how-mobs-are-saved)). `RESET ROOM` sends
strays that wandered into the room home and brings back mobs that belong here.

**Rejuvenation** is a countdown after death. The Rejuv Ticks field is the
number of mob ticks (the engine's 4-second tick; about 15 per minute) a dead mob
waits before it returns to its start room at full health ("appears!"). Rules:

- `0` (stored as "no rejuvenation") means it never returns in this run of the
  world. It is gone until the world is loaded again.
- A value above 0 is the delay in ticks. `CREATE MOB` uses about 37 ticks (2.5
  minutes); `fillOutMOB` uses `FORMULA_NPCREJUV` game minutes (2 plus half the
  level, at most 20 by default).
- If the start room is gone, the mob is destroyed instead.
- If the mob or its start room is flagged "can't be camped" and a player is in
  the start room, it waits (tries again next tick) unless an admin is present.
- A mob's variable equipment is re-rolled on each rejuvenation.

**`Prop_RejuvWhen`** makes the return conditional on the story. Add it as an
effect with:

```text
WHEN="[ZAPPER MASK]" ELSE=WAIT|NEVER|REPLACE:[NAME]
```

The mask is tested on the mob as if it were back in its start room (its hour,
season, room text and `-IF`/`+IF` script conditions). If it fails, `WAIT` (the
default) tries again at the next rejuvenation, `NEVER` leaves the mob gone, and
`REPLACE:name` puts the catalog mob (or class) called `name` there for good.
Examples from the help:

```text
WHEN="-IF !VAR(* OLDMILL == CLEARED)" ELSE="REPLACE:a settler"
WHEN="-HOUR +20 +21 +22 +23 +0 +1 +2 +3 +4" ELSE=WAIT
```

**What happens on death.** `MOBDEATH` in `coffeemud.ini` is `DEATH` (the mob
dies, leaves a corpse, then rejuvenates) or `OUT <expression>` (the mob is
knocked out for a number of ticks instead). Story-driven alternatives, where a
mob yields or is knocked out rather than killed, are `Prop_Essential` (`MODE=YIELD`
or `MODE=KNOCKOUT`), and area or room rules are `Prop_DeathRules`; see
[29-combat-and-encounters.md](29-combat-and-encounters.md).

**Not a respawn.** A mob that is killed or sent away by a script and is not
brought back by rejuvenation returns only when its room is reset or the world is
loaded. If you want a population that stays fluid, use a spawner instead
([Spawning](#spawning)). Do not combine a rejuvenating mob and a spawner that
fills the same slot, or you will get duplicates.

## Spawning

A placed, saved mob is *fixed* population. Mobs that appear on their own are
*spawned*. CoffeeSUD gives you several ways; each is documented in depth
elsewhere, and this is the map.

| Mechanism | Where | What it does |
| --- | --- | --- |
| `RandomMonsters` behavior | on a room or area | Keeps a number of monsters alive, replacing dead ones. Monsters come from a `.cmare` file, pasted XML, or the catalog. |
| `WaveSpawner` | on a room | Wave and arena fights: successive groups (`WAVE1="catalog:goblin*3" WAVE2="..."`), finished when cleared. |
| `RandomEncounters`, `world/encounters/*.enc` | on a room or area | Random encounters on entering, from encounter tables, with level or budget. |
| `EncounterReset` | on a mob or room | Not a spawner: a boss that the party walks away from resets (heals, goes home, drops its summoned adds). |
| Catalog auto-spawn | the catalog | A cataloged mob spawns by itself in matching rooms up to a cap. See [The mob catalog](#the-mob-catalog). |
| `MPMLOAD` and scripts | MOBPROG | `MPMLOAD` brings a mob into the room: by class, or `MPMLOAD CATALOG name (count)` from the catalog. |
| Quest `LOAD MOB` | quests | A quest creates its mobs; see [08-quests.md](08-quests.md). |
| `Prop_RejuvWhen REPLACE:` | the mob | Replaces a mob by another once the story moves on. |

The `RandomMonsters` parameters, from its help:

```text
[min/max ticks] [% chance] [min/max monsters] [locale mask]; [xml/filename]
min=10 max=20 chance=75 minmonsters=1 maxmonsters=10;monsters.cmare
minmonsters=2 maxmonsters=4;catalog:a goblin,a goblin shaman
maxmonsters=6 CATALOG="grey wolf=4,dire wolf=1" WHEN="-SEASON +WINTER" DESPAWN=DAWN NEAR=PARTY:3 LEVEL=PARTY+-2
```

Before the semicolon you set the rate, the chance, how many to keep alive and
which kinds of room (`-ALL +CITY` and similar). After it you name the source.
CoffeeSUD adds `CATALOG="name=weight,..."` (weighted picks, counted against
each entry's cap), `WHEN="[mask]"` (tested on the room a spawn would go to),
`DESPAWN=WHENFALSE|DAWN|TICKS=n` (spawns that leave unseen), `NEAR=PARTY:n`
(only within n rooms of the player's side), `MAXAREA=n` (spawn nothing while the
area holds n spawns) and `LEVEL=PARTY|PARTY+n|PARTY-n|PARTY+-n` (match the
party's highest level). Spawned mobs carry the tag `SPAWNED`. See
[24-behaviors-and-masks.md](24-behaviors-and-masks.md#randommonsters) and
[29-combat-and-encounters.md](29-combat-and-encounters.md).

Fixed or spawned? Decide per area. A town is fixed (saved and rejuvenating); a
forest is spawned (`RandomMonsters` on the area, catalog weights). Quest actors
are loaded by the quest. Observe a room while the despawn condition changes,
leave and return, then save and restore, to check the numbers hold.

## The mob catalog

Source: [`Commands/Catalog.java`](../../CoffeeMud/com/planet_ink/coffee_mud/Commands/Catalog.java),
`Libraries/CMCatalog.java`. Needs the `CATALOG` security flag.

The catalog is a name-keyed list of finished (usually generic) mobs and items,
sorted into categories. Once a mob is in the catalog, every placed copy that is
*tied* to it stays identical: change one and (after you confirm) all change.
Think of it as a class for your own creatures.

Rules:

- Only **one mob per name** in the whole catalog, whatever its category.
- A mob in the catalog must keep its Java class; cataloging a different class
  under an existing name is refused.
- The catalog is stored in the world as `catalog.cmare` (mobs as `CATMOB`
  entries, with their category and spawn data), and in builder mode `SAVE`
  writes it.
- A mob placed from the catalog is tied to its entry. Changing its name or
  choosing **D** at the update prompt detaches it.

### CATALOG commands

| Command | Does |
| --- | --- |
| `CATALOG [mob name]` | Adds the mob in your room (or `ME`) to the catalog in your current category, or updates the entry. If a different version is already there, it lists the differences and asks. A `CATALOG` of an item works the same way. |
| `CATALOG LIST (MOBS\|ITEMS\|CATEGORIES) [name mask]` | Lists entries: name, number of copies in use (`#`), spawn kind and rate and mask. `MOBS` and `ITEMS` may be shortened (`M`, `I`). |
| `CATALOG DELETE [MOBS\|ITEMS] [name]` | Removes an entry after asking. `everydamnmob`, `everydamnitem`, `everydamnthing` delete all of a kind. |
| `CATALOG EDIT [mob name]` | Edits the spawn rules (below). |
| `CATALOG CATEGORY LIST` | Lists categories. |
| `CATALOG CATEGORY [name]` | Sets the category new entries go in (you are asked before a new category is created). `NONE` returns to the global category. The words `GLOBAL`, `NONE` and `UNCATEGORIZED` cannot be new categories. |
| `CATALOG (ROOM\|AREA\|WORLD) (MOBS\|ITEMS)` | Catalogs everything of that kind in the room, area or world, then resets and saves those rooms (after confirmations; AREA and WORLD ask twice). |
| `CATALOG SCAN (ROOM\|AREA\|WORLD) (MOBS\|ITEMS)` | Reports tied objects and whether their catalog entry exists. `DBSCAN` reads from the saved database copy instead. |
| `CATALOG OVERLOOK (ROOM\|AREA\|WORLD) (MOBS\|ITEMS)` | Reports objects that are *not* tied but have a catalog entry of their name ("should be tied"), or none. `DBOVERLOOK` as above. |
| `CATALOG CLEAN (ROOM\|AREA\|WORLD) (MOBS\|ITEMS)` | Detaches objects tied to an entry that no longer exists. |

### Creating from the catalog

`CREATE MOB [catalog name]` (the exact name) places a tied copy and skips the
editor. In scripts, `MPMLOAD CATALOG name` and `RandomMonsters catalog:` do the
same and respect each entry's cap.

### Cap, auto-spawn and the CATALOG EDIT prompts

For a mob, `CATALOG EDIT` asks:

| Prompt | Meaning |
| --- | --- |
| Auto-Spawn: N)o, R)oom | `N` is none. `R` lets the engine place copies of the mob by itself. |
| Enter a new Max number in world | The cap (must be above 0 when auto-spawn is on). With `N`, the cap is cleared to 0 (no limit) for mobs. |
| Enter a new Drop Rate | A percentage chance, per catalog pass, that a missing copy is made. |
| Enter new Room selection mask | A zapper mask for rooms (for example `-LOCATION +ROOMSTR(moonlit)`, `-AREA +Greenwood`). Leaving it empty removes the rule. `?` explains masks. |

The catalog's support thread (about once an hour of real time; it can be turned
off with the `CATALOGTHREAD` disable flag) makes new copies, only while fewer
than the cap exist. Copies made this way are marked not savable, so they are not
written with the world, and they count against the cap.

The spawn room is picked from the rooms that match your mask, skipping rooms
flagged hidden. With an empty mask the engine instead accepts only randomly
chosen rooms that *are* flagged hidden, so in practice always give a mask.

### Reasons to catalog a mob

- Many copies of the same guard, merchant or monster throughout the world.
- Quest actors loaded by scripts (`MPMLOAD CATALOG`) and encounter tables.
- A once-ever or capped population (`CATALOG EDIT`'s cap).
- `Prop_RejuvWhen ELSE="REPLACE:name"` targets.
- Player templates: catalog mobs in the category `PLAYERTEMPLATE` with a
  `Prop_PlayerTemplate` effect are offered as ready-made characters at creation
  (`CREATIONTEMPLATES` in `coffeemud.ini`); see
  [03-characters.md](03-characters.md).

A catalog mob is still a normal object when placed: its per-instance state (who
it likes, what it carries, variables) is its own. The catalog entry only
decides its definition.

## Copying, destroying and bulk editing

| Command | Does |
| --- | --- |
| `COPY ([number]) [mob name]` | Makes copies of a mob (found in the room, or the nearest anywhere in the world). Each copy is placed in your room and that room becomes its start room. Needs `COPYMOBS`. `COPY Longsword@orc` puts a copy of an item into a mob's inventory. |
| `DESTROY MOB [name]` | Removes a mob in the room (a player is refused). `DESTROY MOB ALL [name]`, `ALL.[name]` and `[name].ALL` remove every match. If the mob was saved with the room, `SAVE ROOM` afterwards makes it permanent; until then it returns on reset. |
| `GMODIFY (AREA\|ROOM\|WORLD\|#TAG) [FIELD=VALUE] (&& or \|\| more FIELD=VALUE tests) (CHANGE=[FIELD=VALUE])` | Selects mobs (and items) by field values and optionally changes them. `GMODIFY ?` lists the fields. Preview your selection first and keep version control: this edits everything that matches. |
| `RESET ROOM`, `RESET AREA` | Revives mobs and returns strays to their start rooms; unsaved changes to those rooms are lost. `RESET REJUV ROOM` revives dead mobs only. |
| `RESET MOBCOMBATSTATS (ROOM\|AREA\|WORLD\|CATALOG) (NOSAVE) ([AREA NAME])` | Re-normalizes the combat numbers of mobs from their level. `NOSAVE` previews. |
| `DEVIATIONS MOBS (ROOM\|AREA\|METRO) (SORTBY NAME\|LEVEL\|PLEVEL\|TYPE)` | Shows how mobs deviate from the norms for their level. |
| `STAT [mob]` | Shows a mob's stats (`STAT BEHAVIORS [mob]` its behaviors' state). |
| `CONSIDER [mob]` | The player view of the odds against it; `CONSIDER ALL` for a whole fight. |
| `TEMPLATE ADD [id] [mob]` | Saves a mob to your personal builder templates; `CREATE MOB [id]` makes a copy. |

Name multiple-word mobs in quotes in commands that take a token: `MODIFY MOB
"the town guard" MISC`.

Changes from `MODIFY MOB` and `GMODIFY` apply to the live mobs first; in builder
mode they reach the world files when the rooms are saved. See `AHELP GMODIFY`.

## How mobs are saved

### In an area file

A room's mobs are stored inside the area's `world/areas/NAME.cmare` file, in
the room's `ROOMMOBS`:

```xml
<ROOMCONTENT>
 <ROOMMOBS>
  <RMOB>
   <MCLAS>GenMob</MCLAS>
   <MLEVL>8</MLEVL>
   <MABLE>11</MABLE>
   <MREJV>90</MREJV>
   <MTEXT>... the mob, XML-escaped ...</MTEXT>
   <MRIDE />
  </RMOB>
 </ROOMMOBS>
 <ROOMITEMS />
</ROOMCONTENT>
```

| Tag | Content |
| --- | --- |
| `MCLAS` | The Java class ID (`GenMob`, `GenShopkeeper`, `Orc`...). |
| `MIDEN` | Present only when the mob has riders or followers; a handle other mobs refer to. |
| `MLEVL`, `MABLE`, `MREJV` | Level, "ability" (hit point modifier) and rejuvenation ticks. For a standard mob these three (plus `MTEXT`) are everything. |
| `MTEXT` | For generic mobs, everything else, as XML text (escaped: `&lt;NAME&gt;...`). |
| `MRIDE` | The `MIDEN` of what the mob rides or follows, if anything. |

Mobs that follow a *player* are not saved (`mob.amFollowing()` must be empty or
a monster).

Unescaped, the `MTEXT` of a generic mob contains, in this order (empty tags
may be written `<TAG />`):

| Tag | Content |
| --- | --- |
| `NAME`, `DESC`, `DISP` | Name, description, display text. |
| `PROP` | The base stats as one bar-separated list: ability, armor, attack, damage, disposition, level, rejuv, speed, weight, height, senses (for example `11\|76\|1\|1\|0\|1\|0\|1.0\|19\|23\|0\|`). |
| `IMG` | Image path. |
| `PREJFC`, `IGNMSK`, `BUDGET`, `DEVALR`, `INVRER`, `CURRENCY`, `IPRICS` | Economy settings (shopkeepers). |
| `BEHAVES` | `<BHAVE><BCLASS>id</BCLASS><BPARMS>parms</BPARMS></BHAVE>` per behavior. |
| `AFFECS` | `<AFF><ACLASS>id</ACLASS><ATEXT>parms</ATEXT></AFF>` per effect. |
| `SCRPTS`, `TAGS` | Script objects and tags, when present. |
| `FLAG` | A legacy flag number. |
| `MONEY`, `VARMONEY` | Money and money variation. |
| `CLAN` | `<CLAN ROLE=n>clanid</CLAN>` per membership. |
| `GENDER`, `MRACE` | Gender letter and race ID. |
| `FACTIONS` | `<FCTN ID="ALIGNMENT.INI">value</FCTN>` per faction. |
| `INVEN` | `<ITEM><ICLASS>..</ICLASS><IDATA>..</IDATA></ITEM>` per carried or worn item (wear state and rejuv included). |
| `ABLTYS` | `<ABLTY><ACLASS>id</ACLASS><APROF>75</APROF><ADATA>..</ADATA></ABLTY>` per ability. |
| `TATTS`, `EDUS` | Tattoos and expertises, semicolon-separated. |
| shop tags | `SELLCD` (what is sold), `SELLIMSK` (item mask), `STORE` of `SHITEM` (`SICLASS`, `SITYPE`, `SISTOCK`, `SIPRICE`, `SIDATA`) and `SIVTYP` (view flags); banker (`BANK`, `COININT`, `ITEMINT`, `LOANINT`), postman (`POSTCHAIN`, `POSTMIN`, `POSTLBS`, `POSTHOLD`, `POSTNEW`, `POSTHELD`), librarian (`LIBRCHAIN`, `LIBROVERCHG`, `LIBRDAYCHG`, `LIBROVERPCT`, `LIBDAYPCT`, `LIBMINDAYS`, `LIBMAXDAYS`, `LIBMAXBORROW`, `POSTCMASK`), auctioneer (`AUCHOUSE`, `TIMEPRICE`, `TIMEPCT`, `TIMECUT`, `MAXADAYS`, `MINADAYS`), craft broker (`BROCHAIN`, `MAXCDAYS`, `MAXLISTINGS`, `COMMPCT`), deity (`CLEREQ`, `WORREQ`, `CLERIT`, `WORRIT`, `CLERSIT`, `WORRSIT`, `CLERPOW`, `SVCRIT`, `BLESSINGS`, `CURSES`, `POWERS`). |

A catalog mob appears in `world/catalog.cmare` as `<CATMOB>` with the same
`<MOB>` XML plus its `CATEGORY` and `CATADATA` (spawn kind, rate, cap, mask).
You normally never hand-edit these; let the game export them and edit with
`MODIFY`. If you must edit XML, change one thing, `WORLD DIFF` it
([02-world-workflow.md](02-world-workflow.md)), and reload.

The shipped examples are `resources/examples/monsters.cmare` (a file of
`GenMob` definitions, the kind a `RandomMonsters` behavior reads) and
`resources/examples/deities.cmare`.

### Saving

- **Builder mode:** `SAVE ROOM` (the same as `SAVE ROOM HOME`) saves each mob into
  its own *start room*, wherever it now stands; mobs standing in the room whose
  home is elsewhere are listed as skipped, and this room's mobs that have
  wandered off are kept. `SAVE ROOM ALL` saves every mob standing here. `SAVE
  CHANGED` saves every room a building command changed, and `SAVE` writes all
  world files. `LIST UNSAVED` shows what is pending. A world file edited outside
  the game is not overwritten without `SAVE FORCE`. See `AHELP SAVE` and
  [02-world-workflow.md](02-world-workflow.md).
- **Normal game:** the mobs you create or change are part of that game's
  campaign save (see [31-saves-and-party.md](31-saves-and-party.md)); the world
  folder is not touched.
- **Edits are live first.** `CREATE`, `MODIFY` and `DESTROY` change the running
  game; nothing reaches the world files until you save. `QUIT` asks whether to
  save pending rooms.
- **Hit points are re-rolled** on every load and reset (see above), and
  **variable equipment is re-rolled** on creation and rejuvenation.

### From a script

Mobs can also be built in JavaScript; see [05-javascript.md](05-javascript.md)
and the example `docs/creator/examples/ember-trial/world/resources/creator/build.js`,
which creates `GenMob`s with `CC.getMOB('GenMob')`, sets their name, display,
level, rejuv, tattoos, properties and behaviors, calls `setStartRoom`, and
`bringToLife(room, true)`.

## Recipes

Each recipe lists the answers to give in the editor. Create the mob in the room
where it will live; set fields by their labels.

### A weapon and armor shopkeeper

```text
CREATE MOB GenShopkeeper
```

Answers:

| Field | Value |
| --- | --- |
| Name / Display | `Garrick the smith` / `Garrick the smith hammers at an anvil.` |
| Description | `A broad-shouldered man with burn scars up both forearms.` |
| Level | `8` (above 1, so the combat numbers are rescored) |
| Rejuv Ticks | `300` |
| Race / Gender | `Human` / `M` |
| Behaviors | `MudChat` (default group chosen from the name; see [06-npcs-dialogue.md](06-npcs-dialogue.md)) |
| Shopkeeper type | `4` (WEAPONS) and `2` (ARMOR) |
| Inventory | `Longsword`, then `3` and `-1`; `Dagger`, then `5` and `-1` |
| Prejudice | `BUY=0.9 Dwarf ; BUY=1.0 ; SELL=1.0` |
| Budget | `2000 DAY` |

Then `SAVE ROOM`. Check the prices with `PRICECHECK longsword`. A dwarf
customer pays 10% less. To make him a trainer or caster too, add type `8`
(TRAINER) or `9` (CASTER) and stock ability IDs (a shop may not be both). To
stock a generic item, build it first, leave it on the floor of the shop room,
and type its name at the Inventory prompt.

### A town guard

```text
CREATE MOB GenMob
```

| Field | Value |
| --- | --- |
| Name / Display | `a town guard` / `A town guard watches the street.` |
| Level / Race | `10` / `Human` |
| alignment | `good` |
| Hit Points Bonus Modifier | `13` |
| Rejuv Ticks | `150` |
| Behaviors | `Fighterness` with parameter `Fighter`; `Guard`; `Mobile` with `min=10 max=20 chance=50 leash=3 opendoors` |

Dress him:

```text
CREATE ITEM Longsword
GET longsword
DRESS town guard longsword
CREATE ITEM LeatherArmor
GET armor
DRESS town guard armor
SAVE ROOM
```

`Guard` makes him come to the aid of anyone attacked in his room.
`Fighterness` makes him fight with fighter abilities. For gates, doors and
sentries see `GateGuard`, `DoorwayGuardian` and `Sentry`
([24-behaviors-and-masks.md](24-behaviors-and-masks.md)).

### A quest giver

```text
CREATE MOB GenMob
```

| Field | Value |
| --- | --- |
| Name / Display | `Elder Marta` / `Elder Marta leans on a stick by the well.` |
| Level / Race | `3` / `Human` |
| Behaviors | `MudChat` |
| Effects | `Prop_QuestGiver` with `QUEST="rat_hunt" TRIGGER=SPEAK`; `Prop_QuestMarker` with `QUEST=*` |
| Disposition | `O` (unattackable) so the story cannot be broken |

`Prop_QuestGiver` offers the named quest when a character speaks to the mob. The
quest must be loaded and running and have an accept script; its default trigger
on a mob is the mob's death. `Prop_QuestMarker` shows a `(!)` after her name while
a quest is available and a `(?)` once it is active. The quest itself is written
as in [08-quests.md](08-quests.md); the mob's conversation as in
[06-npcs-dialogue.md](06-npcs-dialogue.md).

### A mount you can buy

First build the horse, in the stable room:

```text
CREATE MOB GenHorse
```

| Field | Value |
| --- | --- |
| Name / Display | `a chestnut mare` / `A chestnut mare stands saddled here.` |
| Level / Race | `3` / `Horse` |
| Rideable Type / Rider capacity | `L` / `1` |
| Rejuv Ticks | `0` |
| Behaviors | none (it should not wander) |

Then make a `GenShopkeeper` ("the stablemaster"), choose type `5` (PETS) and, at
the Inventory prompt, type `chestnut mare` (the mob standing in the room is
copied into the shop) with a count of `1` and a price such as `120`. Destroy the
original horse so only the shop copy remains. A buyer gets the animal and
can `MOUNT` it. See
[30-travel-vehicles-instances.md](30-travel-vehicles-instances.md) for stables.

### A boss template

```text
CREATE MOB GenMob
```

| Field | Value |
| --- | --- |
| Name / Display | `Gruk the Ember Warden` / `Gruk the Ember Warden blocks the furnace door.` |
| Level / Race | `20` / `Ogre` |
| Hit Points Bonus Modifier | `14` |
| Rejuv Ticks | `0` (a one-time encounter) |
| Behaviors | `Fighterness`; `BossPhases` with `ONENGAGE: SAY "You should have left."; AT=50%: HEAL 10%, SAY "Still standing!"; RESET=FULLHEAL`; `EncounterReset` with `RADIUS=1 IDLE=5 HEAL=100` |
| Effects | `Prop_MobRank` with `RANK=BOSS`; `Prop_Essential` with `MODE=YIELD AT=10% TICKS=20`; optionally `Prop_PartyScaler` with `BASE=3 HP=+25%/MEMBER` |

Add it to the catalog (`CATALOG Gruk`) so encounters, `WaveSpawner`
(`WAVE3="catalog:Gruk the Ember Warden"`) and `MPMLOAD CATALOG` can reuse it.
After building, test it with `CONSIDER`, `COMBATDEBUG` and an ordinary
character (see [29-combat-and-encounters.md](29-combat-and-encounters.md) and
[13-testing-release.md](13-testing-release.md)): an invulnerable archon gives
misleading results.

### A banker

`CREATE MOB GenBanker`; set Name and Display; type `C`; Bank Chain `Town`
(all bankers with that chain share accounts); Coin Interest `1` (percent per real
day), Item Interest `0`, Loan Interest `5`. Leave Prejudice and Currency empty
to inherit the area's.

## Worked examples

### Example 1: a village with a fixed population and a wild forest

1. In builder mode, stand in the village square and create the people:
   `CREATE MOB GenMob` (the herald), a guard as above, and the smith. After each,
   `SAVE ROOM`. The mobs are now in `world/areas/<village>.cmare` on `SAVE`.
2. Catalog the guard: `CATALOG guard`. Catalog the wolf you built in the
   forest: `CATALOG a grey wolf` (set `CATALOG CATEGORY wildlife` first to file
   it).
3. Put `RandomMonsters` on the forest *area* with `CATALOG` weights so the
   forest has wolves that vanish at dawn:

   ```text
   min=20 max=40 chance=60 minmonsters=2 maxmonsters=6 CATALOG="a grey wolf=4,a dire wolf=1" DESPAWN=DAWN
   ```

4. `SAVE`. Start a normal game and walk the village: the people are always there
   and return after death; the wolves come and go.
5. Later you improve the wolf: `MODIFY a grey wolf`; at the end the editor says
   it is cataloged; choose **U** to update, and every tied copy, in rooms or in
   the spawner's pool, now matches.

### Example 2: a boss that follows the story

1. Build `Gruk` as above and place him in the furnace room with `Rejuv Ticks` set
   to `500`.
2. Add `Prop_RejuvWhen` with `WHEN="-IF !VAR(* FURNACE == COLD)" ELSE="REPLACE:a cold slag heap"` (a script sets the
   variable the mask tests; see the `VAR` condition in
   [28-mobprog-functions.md](28-mobprog-functions.md)).
3. Catalog "a cold slag heap" as a harmless `GenMob` named so.
4. Kill Gruk. While the furnace is lit he comes back after 500 ticks; once the
   variable is `COLD`, the next rejuvenation puts the slag heap there for good.
5. Test both paths: set the variable by script, kill him, wait, save and
   restore, and check the heap is still there.

## Troubleshooting and gotchas

- **Created in the wrong room / "I don't see ..."** Editing commands act on the
  mob in your room. Wrap multi-word names in quotes.
- **A mob's stats look random after reload.** Hit points are re-rolled from level
  and modifier on every load; they are never stored. Use the Hit Points Bonus
  Modifier, `Prop_Adjuster`, or `Prop_MobRank`.
- **Changing the level did nothing to its attack.** Only the 1 to 2+ transition
  in the editor and `MODIFY MOB x LEVEL n` re-derive combat numbers. Otherwise
  set Attack, Damage and Armor yourself, or run `RESET MOBCOMBATSTATS ROOM`.
- **The mob's abilities are never used.** A combat behavior is needed
  (`CombatAbilities`, `Fighterness`, `Mageness`...). Do not stack two of them.
- **Equipment is gone after a reload.** Only generic mobs save equipment; standard
  mobs rebuild theirs. For variable gear, remember the Rejuv/Pct rules and use
  `RESET ROOM` before editing.
- **Duplicate mobs.** Do not both rejuvenate a mob and spawn into the same slot.
  `SAVE ROOM` lists strays it skipped; save the room each mob is homed in.
- **A mob never comes back.** Rejuv Ticks is 0, its start room was destroyed,
  `Prop_RejuvWhen` said `WAIT` or `NEVER`, or a player is camping its start room
  and the mob or room is flagged can't-be-camped.
- **The catalog prompts "Update/Detach/Cancel" every time.** The placed copy
  differs from the catalog entry. Choose **U** to make the entry match, **D** to
  stop tying it, or **C** to discard your change.
- **A shopkeeper has no stock after a long time.** Generic shopkeepers restore
  their saved stock at each inventory reset (default every 64800 ticks);
  anything the player sold is cleared then. A *standard* shopkeeper has no saved
  stock at all.
- **Buying a trainer's skill costs nothing / wrong price.** Check the price you
  gave the stock item: `-1` is automatic; a negative number below `-99` is quest
  points or experience.
- **`MODIFY MOB` says "failed to specify an aspect".** The word is not a valid
  stat code for that mob; the message prints the list. `LIBCMASK` and the other
  subclass codes only exist on that subclass.
- **A mob is "a player".** Players and copies of players are edited with
  `MODIFY USER`; `COPY` of a player makes a `GenMob` clone.
- **Mobs avoid or ignore you.** Wandering and aggressive mobs will not act in a
  room that has a visible builder with `CMDMOBS` in it.
- **Editor shows only four fields.** The mob is not generic; recreate it from a
  `Gen*` class.

## See also

- [06-npcs-dialogue.md](06-npcs-dialogue.md): schedules, sentries, memory, hirelings, escorts, MudChat, TALK, barks.
- [24-behaviors-and-masks.md](24-behaviors-and-masks.md): every behavior and the zapper mask language used in shop and spawn masks.
- [23-properties-reference.md](23-properties-reference.md): every `Prop_*` that goes in a mob's Effects.
- [29-combat-and-encounters.md](29-combat-and-encounters.md): the combat formulas, rank, party scaling, bosses, spawners, death rules.
- [03-characters.md](03-characters.md): races, classes, stats and templates.
- [22-items.md](22-items.md): the items that mobs carry, wear and sell.
- [20-areas-rooms-exits.md](20-areas-rooms-exits.md): the rooms mobs live in, area economy fields.
- [04-mobprog.md](04-mobprog.md), [05-javascript.md](05-javascript.md): scripting mobs.
- [08-quests.md](08-quests.md): quest givers and quest mobs.
- [09-society.md](09-society.md): clans, factions, law, currencies and shop economics.
- [30-travel-vehicles-instances.md](30-travel-vehicles-instances.md): mounts, stables and travel.
- [02-world-workflow.md](02-world-workflow.md): world files, SAVE, WORLD DIFF, catalog files.
- [31-saves-and-party.md](31-saves-and-party.md): campaign saves and the party.
- [tool-reference.md](tool-reference.md): the generated command reference.
