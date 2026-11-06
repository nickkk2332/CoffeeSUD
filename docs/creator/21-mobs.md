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
work, but `warnAboutBuilding` tells you the mobs you make stay in that game and
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
| Misc Text | A raw text field that most standard classes ignore. |

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

1. Create or fetch the item, putting it in the mob's inventory with the `@`
   form of `CREATE ITEM`:

   ```text
   CREATE ITEM Longsword@town guard
   CREATE ITEM the iron breastplate@town guard
   ```

   or `GIVE` an item you carry to the mob.

2. Make the mob wear or wield it with `DRESS` (a builder power; the target must
   be a monster in the room):

   ```text
   DRESS guard longsword
   ```

   `DRESS [mob] [item]` takes the mob first and a *single word* for the item last.
   The mob puts the item on in the first slot that fits, or holds it if nothing
   fits.

3. Save the room (`SAVE ROOM`, see [How mobs are saved](#how-mobs-are-saved)).

Edit a carried item with `MODIFY ITEM sword@guard`. The item types and their
fields are in [22-items.md](22-items.md).

### Variable equipment (the item's Rejuv/Pct)

An item in a mob's inventory has a Rejuv/Pct field with a different meaning
from an item lying in a room. In a mob's pack:

| Value | Meaning |
| --- | --- |
| `0` (or anything out of range) | Always there. |
| `1` to `99` | N% chance, each time the mob is created or rejuvenates, that the item is *absent*. |
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
