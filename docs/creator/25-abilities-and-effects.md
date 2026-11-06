# Abilities and effects

Almost everything that does something to a character, a room or an item in
CoffeeSUD is an *ability*: spells, prayers, chants, songs, thief and fighter
skills, crafting and gathering skills, languages, diseases, poisons, traps and
the "properties" that builders attach to rooms, exits, items and mobs. When an
ability is on something and running it is also an *effect* (the engine's word is
"affect"). This chapter is for world creators and scripters, with a section
for engine developers at the end of each major part. It explains how abilities
are classified and costed, how characters and mobs obtain them, how timed
effects behave and are saved, how to build your own without Java (`GenAbility`,
`GenTweakAbility`, `GenPoison`, `GenDisease`, `GenTrap`), how to find the stock
abilities in game, and ends with worked examples.

The properties themselves (the `Prop_*` family) are catalogued in
[23-properties-reference.md](23-properties-reference.md). Class and race
qualification, levels, training and `GenCharClass` are in
[03-characters.md](03-characters.md). The optional composed-skill system is in
[15-ability-composition.md](15-ability-composition.md). Script syntax is in the
MOBPROG chapters ([04](04-mobprog.md), [26](26-mobprog-triggers.md),
[27](27-mobprog-commands.md), [28](28-mobprog-functions.md)).

## In this chapter

- [What an ability is](#what-an-ability-is)
- [Families of abilities](#families-of-abilities)
- [Classification: type and domain](#classification-type-and-domain)
- [Quality, targets and range](#quality-targets-and-range)
- [Costs, cooldowns and action time](#costs-cooldowns-and-action-time)
- [Proficiency, levels and expertise](#proficiency-levels-and-expertise)
- [Ability flags](#ability-flags)
- [Timed effects](#timed-effects)
- [Saving throws](#saving-throws)
- [How characters get abilities](#how-characters-get-abilities)
- [How mobs, items and rooms get abilities](#how-mobs-items-and-rooms-get-abilities)
- [Putting effects on things](#putting-effects-on-things)
- [Finding abilities in game](#finding-abilities-in-game)
- [GenAbility: custom abilities](#genability-custom-abilities)
- [GenTweakAbility: renamed and adjusted Java abilities](#gentweakability-renamed-and-adjusted-java-abilities)
- [Poisons](#poisons)
- [Diseases](#diseases)
- [Traps](#traps)
- [Other generic ability types](#other-generic-ability-types)
- [The stock ability catalogue](#the-stock-ability-catalogue)
- [Worked examples](#worked-examples)
- [Persistence](#persistence)
- [Troubleshooting and gotchas](#troubleshooting-and-gotchas)
- [See also](#see-also)

## What an ability is

An ability is a Java object implementing
[Ability](../../CoffeeMud/com/planet_ink/coffee_mud/Abilities/interfaces/Ability.java),
usually by extending
[StdAbility](../../CoffeeMud/com/planet_ink/coffee_mud/Abilities/StdAbility.java).
Every ability has an **ID** (the Java class name, such as `Spell_MagicMissile`),
a display **name** ("Magic Missile"), and an **invoke** method that does the
work when somebody uses it. The same object plays two roles:

1. **A known ability** (a skill, spell, prayer ...). It lives in a mob's
   ability list (`MOB.abilities`), carries the character's *proficiency*
   (0 to 100) and is used by typing its command: `CAST MAGIC MISSILE ORC`,
   `PRAY BLESS`, `BACKSTAB ORC`. The list is what `ABILITIES`, `SPELLS`,
   `SKILLS` and similar commands show.
2. **An effect** (an "affect"). When the ability does something lasting, it
   puts a *copy of itself* on the target's effects list. The copy ticks, adjusts
   stats while it is there, reacts to messages, and eventually ends
   (*uninvokes*). Rooms, exits, items, areas and mobs all have effect lists.
   A *property* (`Prop_*`) is an ability whose only job is to sit on something
   as a permanent effect.

Several things follow from this that builders rely on:

- Any ability ID can be put on a room, exit, item or mob in the builder's
  *Effects* field, with a parameter string (its `text()`), whether or not it is
  a "spell" ([Putting effects on things](#putting-effects-on-things)).
- Every ability can be granted to a mob by ID, from an editor, a class, a race,
  an item or a script.
- Custom abilities made as data (`GenAbility` and the other `Gen*` classes) are
  real abilities: they appear in `LIST ABILITIES`, can be given to classes, and
  can be used anywhere a Java ability ID can.

Most abilities take their numbers (damage, duration, cost) from the caster's
level and the target's level. Wherever this chapter says "level" for a spell it
means the *adjusted caster level*: the level of the class that gave the
ability, counted from the level at which the ability is learned, plus any
`XLEVEL` expertise (see [Proficiency, levels and expertise](#proficiency-levels-and-expertise)).
A mob that is not a player casts at its own level.

## Families of abilities

The first five bits of `classificationCode()` select the *type* of an ability
(mask `ALL_ACODES` = 31). The type decides which base class is used, how the
ability is invoked, what its cast message is, which saving throw protects
against it, and how the in-game lists group it.

| Type (ACODE) | Value | Invoke words | Typical base class | Source folder under `Abilities/` |
| --- | --- | --- | --- | --- |
| SKILL | 0 | the skill's own words (`BASH`, `RECALL`) | `StdSkill`, `FighterSkill`, `PaladinSkill`, `ArchonSkill` | `Skills`, `Fighter`, `Paladin`, `Ranger`, `Specializations`, `Archon`, `Misc` |
| SPELL | 1 | `CAST`, `CA`, `C` plus the spell name | `Spell` | `Spells` |
| PRAYER | 2 | `PRAY`, `PR` plus the prayer name | `Prayer` | `Prayers` |
| SONG | 3 | `SING`/`SI` (songs), `PLAY`/`PL`/`PLA` (instruments), `DANCE`/`DA` (dances) | `Song`, `Play`, `Dance` | `Songs` |
| TRAP | 4 | `SPRING` (not for players; traps spring on events) | `StdTrap`, `StdBomb` | `Traps` |
| PROPERTY | 5 | none | `Property` (a light `ThinAbility`) | `Properties` |
| THIEF SKILL | 6 | the skill's own words (`STEAL`, `SNEAK`) | `ThiefSkill` | `Thief` |
| LANGUAGE | 7 | `SPEAK` plus the language name | `StdLanguage` (`Language` interface) | `Languages` |
| CHANT | 8 | `CHANT`, `CH` plus the chant name | `Chant` | `Druid` |
| COMMON SKILL | 9 | the skill's own words (`MINE`, `COOK`) | `CommonSkill` and its crafting/gathering subclasses | `Common` |
| DISEASE | 10 | none for players (`DISEASE` internally) | `Disease` | `Diseases` |
| POISON | 11 | none for players (`POISONSTING` internally) | `Poison` | `Poisons` |
| SUPERPOWER | 12 | the power's own words | `SuperPower` | `SuperPowers` |
| TECH | 13 | the skill's own words | `TechSkill` | `Tech` |

Notes:

- A spell's command is the *trigger string* (`CAST`) followed by the spell's
  name, which may be abbreviated: `cast magic missile orc`, `c magic orc`. The
  engine finds the ability the player knows whose trigger string equals the
  first word and whose name (or name with spaces removed) starts with the second.
  Other types use one command word chosen by the ability itself (its
  `triggerStrings()`; for a `GenAbility`, the `TRIGSTR` field).
- Archon-only skills are not a separate type: they are `SKILL` with the `ARCHON`
  domain (`Archon_Hush`, `Archon_Wrath`). The editors refuse to hand them to a
  builder who does not already know them, or who is not a sysop.
- Class-specific skills (`Fighter_*`, `Paladin_*`, `Ranger_*`) are ordinary
  `SKILL` abilities; the folder just groups them. `Thief_*` skills are the
  separate `THIEF SKILL` type, and `Skill_*` abilities are generic skills.
- The type also picks the message type a `GenAbility` sends when cast: spells,
  prayers and superpowers are *somatic* casts, chants are *verbal* casts, thief
  skills are thief actions, languages and properties are visual, and the rest
  are noisy movement. Other effects that watch for such messages (silence,
  bindings) therefore treat them differently (see
  [What happens when it is cast](#what-happens-when-it-is-cast)).
- Roughly 1,100 abilities ship: about 420 spells, 365 prayers, 315 chants, 115
  songs, 190 `Skill_*`, 195 thief skills, 160 fighter skills, 170 common-skill
  classes, 60 languages, 70 diseases, 25 poisons, 80 traps and bombs and 240
  properties. See [The stock ability catalogue](#the-stock-ability-catalogue)
  for how to browse them rather than memorising a list.

## Classification: type and domain

`classificationCode()` is one integer: `type | domain`, where the type is
the value from the table above and the domain is `(index << 5)`. Domains are
sub-categories used by the in-game lists (`SPELLS CONJURATION`), by
`QUALIFY`, by the `LIST ABILITIES` filters and by many property and mask
checks (`-SKILLFLAG`, ability adjustments such as `PROF+HEALING`).

In a `GenAbility` the field `CLASSIFICATION` takes `TYPE,DOMAIN` as words, for
example `SPELL,EVOCATION` or `SKILL,MARTIAL_LORE`, or the raw number. Names are
matched ignoring case, and a unique start or end of the name is accepted, so
`SPELL,EVOCATION` matches the domain `INVOCATION/EVOCATION`. The valid words:

- Types: `SKILL`, `SPELL`, `PRAYER`, `SONG`, `TRAP`, `PROPERTY`, `THIEF SKILL`,
  `LANGUAGE`, `CHANT`, `COMMON SKILL`, `DISEASE`, `POISON`, `SUPERPOWER`.
  (`LIST ABILITYCODES` prints them with underscores.)
- Domains, by index:

| Domain names (as `LIST ABILITYDOMAINS` shows them) | Usually seen with |
| --- | --- |
| `DIVINATION`, `ABJURATION`, `ILLUSION`, `INVOCATION/EVOCATION`, `ALTERATION`, `TRANSMUTATION`, `ENCHANTMENT/CHARM`, `CONJURATION` | spells (the eight schools) |
| `ARCHON` | archon skills |
| `SINGING`, `DANCING`, `PLAYING` | songs |
| `DECEPTIVE`, `FIND/REMOVE_TRAPS`, `BINDING`, `STEALING`, `STEALTHY`, `TRAPPING`, `ALERT` | thief skills |
| `HOLY_PROTECTION`, `HEALING`, `VEXING`, `BLESSING`, `CURSING`, `EVANGELISM`, `WARDING`, `DEATH_LORE`, `CORRUPTION`, `RESTORATION`, `NEUTRALIZATION`, `CREATION`, `COMMUNING` | prayers |
| `MOON_SUMMONING`, `MOON_ALTERING`, `PLANT_GROWTH`, `PLANT_CONTROL`, `SHAPE_SHIFTING`, `WEATHER_MASTERY`, `PRESERVING`, `ENDURING`, `ANIMAL_AFFINITY`, `DEEP_MAGIC`, `BREEDING`, `ROCK_CONTROL`, `WATER_CONTROL` | chants |
| `GATHERING`, `CRAFTING`, `BUILDING`, `EPICUREAN`, `ARTISTIC`, `CALLIGRAPHY` | common skills |
| `WEAPON_USE`, `KICKING`, `PUNCHING`, `GRAPPLING`, `MARTIAL_LORE`, `COMBAT_LORE`, `COMBAT_FLUIDITY`, `EVASIVE`, `DIRTY_FIGHTING`, `ARMOR_USE`, `SHIELD_USE` | fighting skills |
| `FOOLISHNESS`, `ACROBATIC`, `INFLUENTIAL`, `STREET_SMARTS`, `NATURE_LORE`, `ARCANE_LORE`, `EDUCATION_LORE`, `ANATOMY`, `CRIMINAL`, `LEGAL`, `FITNESS`, `THEATRE`, `POISONING`, `RACIAL_ABILITY`, `COSMOLOGY`, `WATER_LORE`, `SEA_TRAVEL`, `ROPE_USE`, `TRAVEL` | general skills |

`NOTHING` (index 0) means no domain. A domain does not change what an ability
does; the sources for abilities of one family may mix domains (a chant can sit
in `ANIMAL_AFFINITY`; prayers also use `COSMOLOGY`).

**Developer note.** In `Ability.ACODE.DESCS` the array has 15 entries but
`ACODE_TECH` is 13, so the word at index 13 reads `ARCHON SKILL` and `TECH
SKILL` is 14. Do not use `TECH SKILL` or `ARCHON SKILL` in `CLASSIFICATION`;
use the numbers (`13`) if you really need the tech type.

## Quality, targets and range

### Quality

`QUALITY` (`abstractQuality()`) tells the engine and the NPC AI what an ability
means for its target.

| Quality | Meaning |
| --- | --- |
| `MALICIOUS` | Hostile. Its cast message is flagged malicious (it starts a fight and is refused against friends), duration uses the hostile formula, it can fizzle visibly with a hostile message, and it uses the *combat* action cost even outside a fight. |
| `INDIFFERENT` | Neither helps nor harms (utility). |
| `OK_SELF` | Harmless; targets the caster only. |
| `OK_OTHERS` | Harmless; targets the caster or others. |
| `BENEFICIAL_SELF` | Helpful; always targets the caster. |
| `BENEFICIAL_OTHERS` | Helpful; targets the caster or others. |

For a `GenAbility`, `BENEFICIAL_SELF` and `OK_SELF` skip target lookup and always
use the caster (a mob given as the target of an *automatic* cast is honoured).
Every other quality looks up a target according to `CANTARGETMASK`. Mobs
decide what to cast on whom by calling `castingQuality`, which downgrades
`BENEFICIAL_SELF` to `INDIFFERENT` for a target other than the caster, and
everything to `INDIFFERENT` if the target already has the effect.

### What it can affect and target

Two masks pick the kinds of object: `CANAFFECTMASK` (what the ability can sit
on as an effect) and `CANTARGETMASK` (what the caster may aim it at). Each is a
comma list of `MOBS`, `ITEMS`, `AREAS`, `ROOMS`, `EXITS` (values 1, 2, 4, 8,
16), or `0` for none.

- `CANAFFECTMASK=0` makes an *instant* ability: nothing is left on the target.
  Use it for plain attacks and heals.
- A non-zero `CANAFFECTMASK` plus a target makes a *lasting* effect with a
  duration. Hostile ones are applied only if the target does not save.
- In a `GenAbility`, `CANTARGETMASK` is read as a whole value: exactly `MOBS`
  looks for a mob, `ITEMS` for an item, `ROOMS` and `EXITS` as described under
  [What happens when it is cast](#what-happens-when-it-is-cast), `0` for none,
  and any other combination (such as `MOBS,ITEMS`) looks for either a mob or an
  item.

### Range

`MINRANGE` and `MAXRANGE` use the words `MELEE`, `CLOSE`, `SHORT`, `MEDIUM`,
`LONG`, `LONGER`, `LONGERSTILL`, `VERYLONG`, `EXTREMELYLONG`, `INFINITE`
(stored as 0 to 9). They matter only in combat distance: before a *hostile*
ability is paid for, the engine refuses it when the caster and the victim are
too close (`You are too close to X`) or too far apart. Ranges are widened by
the caster's `XMAXRANGE` expertise. Outside combat, range has no effect.

## Costs, cooldowns and action time

### Resource cost

`USAGEMASK` picks what the ability costs: any of `MANA`, `MOVEMENT`,
`HITPOINTS` (a comma list; the cost is divided evenly among those chosen). The
amount comes from, in order of precedence:

1. a per-class cost override in the class's ability mapping (if the class
   defines one for that ability; see [03-characters.md](03-characters.md));
2. a `MANACOST` exception for that ability ID in `coffeemud.ini`
   (`MANACOST=-1, Skill_Recall 25`: a number, a negative "50 plus N times the
   ability's lowest qualifying level", or a formula with `@x1` lowest level,
   `@x2` the mob's level, `@x3` the minimum cost, `@x4` the adjusted level and
   `@x5` the number of levels above qualification);
3. the ability's own override (`OVERRIDEMANA` on a `GenAbility`, a Java
   `overrideMana()`), used only when the ini has no exception for that ID:
   `-1` for the normal formula, a fixed amount, or a share of the caster's
   pool. For a percentage the value is `2147483647` minus the percent (25
   percent is `2147483622`); `2147483647` alone means "all of it". The editor's
   prompt shows the same four choices as `-1`, `x`, `2147483647` and
   `2147483647-(1 to 100)`;
4. the world default `MANACOST` (`-1`), reduced by about 1/30 for each level
   the caster has above the qualifying level, never below `MANAMINCOST`
   (`coffeemud.ini`: `MANAMINCOST=-1, 10`). The minimum is applied before the
   overrides above, so an override may go below it.

`XLOWCOST` and `XLOWFREECOST` expertise reduce it. `MANACOMPOUND` in
`coffeemud.ini` can make repeating the same ability within a few ticks cost more
or be refused outright.

### Cooldown

`TICKSBETWEENCASTS` (Java `getTicksBetweenCasts`) sets a per-ability cooldown in
ticks (about 4 seconds each at the default tick). A caster who tries early is
told how long to wait. `XLEVEL` and `XTIME` expertise shorten it. A cooldown is
kept on the ability object the caster holds; see
[Persistence](#persistence).

### Action time

How many "actions" the use takes comes from `DEFABLETIME` (out of combat,
default `0.10`) and `DEFCOMABLETIME` (in combat, default `1.0`; `1.0` is one
full round) in `coffeemud.ini`. Each takes a comma list of ID exceptions:
`DEFABLETIME=0.10, Skill_Recall 2.0`. A hostile ability always uses the
combat cost. A `GenAbility` overrides both in its own record with
`ACTIONCOST` and `COMBATACTIONCOST` (`-1` for the default). Combat timing is
covered in [29-combat-and-encounters.md](29-combat-and-encounters.md).

### Components

Optional spell components (a pinch of salt, a wand held) are defined in
`resources/skills/components.txt`, one line per ability ID, or in the
ability's own `COMPONENTS` field. Archons, mobs and holders of the
`COMPONENTS` security flag are exempt. The line format is documented at the top
of the file and summarised under
[Costs and components](#costs-and-components-in-a-genability). Use
`LIST COMPONENTS`, `CREATE COMPONENT`, `MODIFY COMPONENT` and `DESTROY
COMPONENT` to manage the file in game.

## Proficiency, levels and expertise

**Proficiency** is a character's skill with one ability, 0 to 100. When an
ability is used by a player (not automatically), the engine rolls against its
proficiency: the chance is the proficiency, plus twice the `XLEVEL` expertise
level, kept between 5 and 95 percent (unless the `DIS955RULE` disable flag is
set), plus half the expertise again, and plus or minus `PROF` ability
adjustments from items and properties. A miss fizzles the ability with its
failure message and runs any `SKILL_PROG FAIL` script ([26](26-mobprog-triggers.md)).
Holders of the `SUPERSKILL` security flag never fail.

Proficiency rises three ways:

- **Use.** Each use has a small chance to add 1 point, at most once every five
  minutes per ability, and only if the character's `INTELLIGENCE`-based roll
  beats their current proficiency. It stops at the class's maximum.
  `AUTOIMPROVE` (a player setting) announces it.
- **Practice.** `PRACTICE [SKILL] [TEACHER]` with a teacher who knows it
  better spends practice points and adds about `25 * (teacher WIS + student
  INT) / 36` points, never beyond `PRACMAXPCT` percent (default 75) of the
  class maximum, and never beyond the teacher's own proficiency.
- **Scripts and properties.** `MPENABLE`, `Prop_HaveEnabler` and the other
  enablers set it directly ([How mobs, items and rooms get abilities](#how-mobs-items-and-rooms-get-abilities)).

Scripts see `SKILL_PROG IMPROVE`, `MASTER` and `FAIL` events as they happen
([26](26-mobprog-triggers.md)).

**Levels.** Each ability has a *qualifying level* for each class that can have
it. The adjusted level at which it is cast is the lowest qualifying level plus
how far above that level the caster's class level is, plus `XLEVEL` and any
`LEVEL+` adjustment. Duration, damage, saves and many effects scale with that
number, and a spell read from a scroll is cast at the scroll's level.

**Expertise** is the system of purchasable boosts defined in
`resources/skills/expertises.txt`. Expertise types an ability reads are
`XLEVEL`, `X1` to `X5` (extra values only some abilities use), `XLOWCOST`,
`XLOWFREECOST`, `XMAXRANGE`, `XTIME` and `XPCOST`. In `GenAbility` formulas they
appear as `@x3` to `@x8`. Expertise is covered in
[03-characters.md](03-characters.md).

## Ability flags

`FLAGS` (the Java `flags()` bit mask) describes what an ability *is*, for
other abilities and scripts to query: a protection spell checks for
`PARALYZING`, a cure checks for `POTENTIALLY DEADLY`, `LIST ABILITIES
[FLAG]` filters by them. In a `GenAbility` you may give a comma list of the
names below, a number, or a start of a name. `LIST ABILITYFLAGS` prints the
exact words your build accepts.

| Flag word | Bit | Meaning |
| --- | --- | --- |
| `BINDING` | 1 | binds the target |
| `MOVING` | 2 | moves the target between rooms |
| `TRANSPORTING` | 4 | transports a group |
| `WEATHERAFFECTING` | 8 | changes the weather |
| `SUMMONING` | 16 | summons something |
| `CHARMING` | 32 | charms the target |
| `TRACKING` | 64 | causes tracking |
| `HEATING` | 128 | heats |
| `BURNING` | 256 | fire based (Java `FLAG_FIREBASED`) |
| `HOLY` | 512 | holy |
| `UNHOLY` | 1024 | unholy (`HOLY,UNHOLY` together is the neutral flag) |
| `PARALYZING` | 2048 | paralyses |
| `NOORDERING` | 4096 | cannot be ordered by a leader |
| `CLANMAGIC` | 8192 | clan magic |
| `HEALING` | 16384 | healing magic |
| `FREEZING` | 32768 | water/cold based (`FLAG_WATERBASED`) |
| `GASSING` | 65536 | air/gas based (`FLAG_AIRBASED`) |
| `ACIDIZING` | 131072 | earth/acid based (`FLAG_EARTHBASED`) |
| `INTOXICATING` | 262144 | intoxicates |
| `ADJUSTER` | 524288 | adjusts stats |
| `RESISTER` | 1048576 | resists or saves |
| `IMMUNER` | 2097152 | blocks certain negative effects |
| `ZAPPER` | 4194304 | blocks getting and other mundane actions |
| `CASTER` | 8388608 | casts another ability |
| `ENABLER` | 16777216 | grants another ability |
| `UNCRAFTABLE` | 33554432 | cannot be learned as a recipe |
| `DEADLY` | 67108864 | may kill (`FLAG_POTENTIALLY_DEADLY`) |
| `AGGROING` | 134217728 | aggravates (`FLAG_AGGROFYING`) |
| `TIDEALTERING` | 268435456 | alters the tides |
| `SUNSUMMONING` | 536870912 | summons a sun |
| `NOUNINVOKING` | 1073741824 | cannot be auto-uninvoked |
| `MINDALTERING` | 2147483648 | alters the mind |
| `TORTURING` | 4294967296 | tortures |
| `LAW` | 8589934592 | a law effect |
| `CHAOS` | 17179869184 | a chaos effect (`LAW,CHAOS` together is "moderate") |
| `ELECTROCUTING` | 34359738368 | electric based (`FLAG_ELECTRICBASED`) |
| `NONENCHANTMENT` | 68719476736 | not an enchantment |
| `NODISENCHANT` | 137438953472 | cannot be disenchanted |
| `STEALING` | 274877906944 | a stealing effect |
| `FEARING` | 549755813888 | causes fear |
| `DIVINING` | 1099511627776 | divination |
| `POLYMORPHING` | 2199023255552 | changes the target's shape |

A word that is the start of exactly one name works (`HEAL` becomes
`HEALING`); give full names in files you maintain.
