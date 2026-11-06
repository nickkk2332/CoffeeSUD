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

## Timed effects

When an ability produces a lasting result it puts a copy of itself on its
target. That copy is the *effect*. Everything below applies to every kind of
ability, including the ones you build as data.

### The three kinds of effect

| Kind | How it gets there | Ends | Saved with the world or character? |
| --- | --- | --- | --- |
| **Timed** | Cast, thrown or applied by an ability (`beneficialAffect`, `maliciousAffect`, `startTickDown`) | When its tick counter runs out, when dispelled or cured, or when the ability itself ends it | Not as part of a room, mob or item record (the `savable` flag is switched off). The *campaign save* keeps its remaining ticks, see [Persistence](#persistence) |
| **Permanent** (non-uninvokable) | Put there by a builder (the Effects field of an editor, an `effects` list in a world file) or by `addNonUninvokableEffect` | Never on its own; dispel does nothing. Remove it with the editor | Yes: its ID and parameter text are written with the room, exit, item or mob |
| **Automatic** (auto-invoked) | A *known* ability that `isAutoInvoked()` is copied onto its owner when learned, on login and when `AUTOINVOKE` turns it on (passive skills such as Dodge, or a `GenAbility` with `AUTOINVOKE=true`) | When the owner forgets the ability or switches it off with `AUTOINVOKE` | No. It is recreated from the known ability each time |

The engine's words for the distinction: `canBeUninvoked()` is true for timed
and automatic effects and false for permanent ones; `isSavable()` is true for
permanent effects; `isNowAnAutoEffect()` is true for automatic ones and for
anything cast with `auto=true` (a cast that skips cost, proficiency and
messages, which is how scripts, items and traps cast things).

A *Property* is just a permanent effect with no known-ability role, which is why
`Prop_*` classes extend a lighter base class and have no cost, cooldown or
casting.

### Duration

An effect's counter, `tickDown`, drops by one on each tick of its host. At zero
the engine calls `unInvoke()`, the effect removes itself, recalculates the
host's stats and tells the host the ability's end message (`UNINVOKEMSG` in a
`GenAbility`). One tick is about 4 seconds of game time at the default `TICKTIME`
(see [10-world-simulation.md](10-world-simulation.md) for game time).

If the caster does not give an exact duration, the base length is:

- **Beneficial** effects: `adjusted caster level * 4 + 40` ticks, never more
  than one game day (`TICKSPERMUDDAY`), never fewer than 5. If the caster is a
  player (or leads one) and helps somebody else, the caster level used is capped
  at the target's level plus `EXPRATE` (default 6), so a high-level character
  cannot make a low-level one's buffs extremely long.
- **Hostile** effects: `adjusted caster level * 1.1 + 25` ticks, made shorter if
  the target is higher level than the caster and longer if lower (the
  difference limited to `EXPRATE` and applied as a fraction `diff / (EXPRATE+1)`),
  capped at one third of a game hour and at one game day, never fewer than 2.
  Effects on rooms are not level-adjusted.

Callers can change this. In a `GenAbility`, `TICKSOVERRIDE` is a formula: a
positive value is the exact number of ticks (and is not capped), `0` means
"standard", and a negative value means that many percent of the standard
duration (`-50` is half). Other adjustments are applied afterward:

- `XTIME` expertise adds 20 percent of the base length per level (not to the
  crafting, gathering, epicurean and building common skills);
- each of the caster's classes may adjust duration (`classDurationModifier`);
- stat adjustments of the form `TICK+[ID/TYPE/DOMAIN/*]` from items, races and
  properties (the `ableticks` parameter of `Prop_Adjuster` and relatives) shift
  it by a fixed number of ticks;
- `EFFECTCAP` in `coffeemud.ini` (default `0`, off) limits how many
  dispellable effects a mob can carry: a new one over the cap randomly ends
  an existing one.

An effect can also last "forever": `makeLongLasting()` sets the counter to the
maximum so it never counts down (used for properties and quiet effects),
and `TICKS_ALMOST_FOREVER` is the value the display treats as no time limit.

### What an effect can do

An effect is a live participant in the game's message flow. A Java effect can:

- adjust the host's stats whenever they are recalculated
  (`affectPhyStats`, `affectCharStats`, `affectCharState`): this is how a spell
  raises armor or lowers strength;
- veto or react to anything done in its host's presence (`okMessage` and
  `executeMsg`), which is how a bless reacts to damage, a sleep prevents
  movement, and a trap springs;
- do something every tick (`tick`);
- clean up (`unInvoke`).

A `GenAbility` exposes the first as `HERESTATS`, the second and third through
its script, and the last as `UNINVOKEMSG` and `ONEXPIRE`
([Script hooks](#script-hooks)). Everything a `Prop_*` property does is done
through the same hooks.

### Invoker, savable and uninvokable

- **Invoker.** Every effect remembers who made it (`invoker()`). Hostile
  damage over time credits the invoker with kills and experience; a trap
  without an invoker invents a stand-in at the host's level; cures such as
  `Prayer_CureDisease` refuse to cure a disease caused by a stronger source;
  and `MPAFFECT` makes the script's host mob the invoker.
- **Savable.** `isSavable()` decides whether the effect is written into the
  record of the room, exit, item or mob it sits on. Casting turns it off, so a
  spell buff is not part of the *world*; permanent effects have it on. The
  campaign save separately stores timed effects with ticks left
  ([Persistence](#persistence)).
- **Uninvokable.** `makeNonUninvokable()` makes an effect permanent and
  savable; `canBeUninvoked()` tells dispel and cure abilities whether they may
  remove it. The flag `NOUNINVOKING` is a separate hint for effects that
  should not be removed even by automatic clean-up.

### How effects show up

The `AFFECTS` command (`AFFECT`, `AFF`, `AF`) lists a character's effects by
their `displayText()`: the *Affect String* of a `GenAbility`
(`DISPLAY`). Effects with an empty display text are hidden. A display text
wrapped in parentheses, such as `(Emberward)`, has the time remaining
appended. `AFFECTS [TYPE or DOMAIN] [NAME|TYPE|DOMAIN|REMAIN]` filters and sorts;
`AFFECTS spell`, `AFFECTS name "common skill"`. An archon can say `AFFECT`
followed by `ROOM`, `AREA` or a target's name to see the effects on that
thing (the output includes who caused each one).

A character with the `SYSOPMSGS` attribute set also sees effects that have no
display text, written `ID:ticks` for a timed effect and `ID+proficiency` for a
permanent or automatic one. (The `Effects` field of an editor lists only
permanent, savable effects.)

## Saving throws

Hostile abilities give the target a chance to resist before any effect is
applied. A save is a percentage stored in the target's character stats; the
engine computes it when the hostile message reaches the target:

1. Start with the save for the *ability family*: `SAVE_SPELLS` for a spell,
   `SAVE_PRAYERS`, `SAVE_CHANTS` or `SAVE_SONGS` for those types (none for
   other types).
2. Add the save for the *kind of harm*, taken from the message type of the
   attack: `FIRE`, `COLD`, `WATER`, `GAS`, `MIND`, `GENERAL`, `JUSTICE`, `ACID`,
   `ELECTRIC`, `POISON`, `UNDEAD`, `DISEASE`, `PARALYZE`, `POLYMORPH` and
   `CAST_SPELL` (the generic magic save). In a `GenAbility` this is the
   `ATTACKCODE` field.
3. Add or subtract the squared level difference between target and attacker
   (a target 5 levels higher gains 25 percentage points; one 5 lower loses 25).
4. Clamp the result to 5 to 95 percent and roll. A roll below it means the
   target *resists*: the engine sends a resistance message ("X shake(s) off the
   mental attack"), the effect is not applied, and damage from the ability is
   halved.

Other saves that sit outside this: `SAVE_TRAPS` for traps (compared with the
target-minus-trapper level difference), `SAVE_DISEASE` for catching a disease
(the chance to catch is 100 minus the save; a successful save leaves a short
`TemporaryImmunity` effect), and `SAVE_POISON` for poison applications.
Saves are normal stats, so races, items and properties raise them with
`Prop_Adjuster`-style parameters (`savefir`, `savemag`, `savespells`, ...).
A world can add its own stat and effects of stats; see `STATEFFECT_*` and
`AHELP STATEFFECTS`, and [world-settings.md](world-settings.md).

## How characters get abilities

A character can know an ability in four ways: its class (or race) grants it,
it is learned from a teacher, a script or item grants it, or it comes from a
reward. All four end up in the same list.

### Class, race and the shared pools

Each character class has an ability map: for every ability it can have, the
level at which it qualifies, a default proficiency, whether it is gained
automatically at that level (*autogain*), its visibility (`PUBLIC`; `SECRET`, never listed until
known; or `MASKED`, secret until the mask is met), an optional default parameter string, required skills
(`Skill_Write Skill_Trip Skill_Dirt(25)`: other abilities, optionally with a
proficiency, that must be known first), a qualifying zapper mask and optional
cost overrides. Races can grant abilities the same way. The class and race
editors, `GenCharClass` and `GenRace`, are covered in
[03-characters.md](03-characters.md); `CREATE ABILITY` makes the ability
exist, the class's map is what lets characters have it.

A second map, `resources/skills/allqualifylist.txt`, holds abilities every
class can use without being listed in each class:

```text
[EACH]
1 Bandaging
3 FireBuilding AUTOGAIN

[ALL]
1 Digging
1 Fishing MASK=+WIS 8
```

Each line is `[QUAL_LEVEL] [ABILITY_ID] (AUTOGAIN) (PROF=[AMT])
(REQUIRES=[ID(PROF)]...) (MASK=[ZAPPER MASK])`. `[EACH]` skills belong to every
class individually; `[ALL]` skills form a shared pool subject to the
`MAXCOMMONSKILLS`, `MAXCRAFTINGSKILLS`, `MAXNONCRAFTINGSKILLS` and
`MAXLANGUAGES` limits in `coffeemud.ini`. Edit the file or use `CREATE
ALLQUALIFY EACH|ALL [ID]`, `MODIFY ALLQUALIFY EACH|ALL [ID]`, `DESTROY
ALLQUALIFY EACH|ALL [ID]` (security flag `CMDABILITIES`); `LIST ALLQUALIFYS`
shows them. The editor asks for qualifying level, auto-gain, default
proficiency, visibility, qualifying mask and required skills.

`QUALIFY` shows the abilities the player can learn now (`QUALIFY SPELLS`,
`QUALIFY UNIQUE POISONING`). `SKILLTREE [CLASS or SKILL]` draws the
prerequisite chains with learned, learnable and locked marks
(`resources/skills/skillhints.ini` holds where-taught hints).

### Learning and improving: TRAIN, LEARN, GAIN, PRACTICE, TEACH

| Command | What it does |
| --- | --- |
| `TEACH [STUDENT] [SKILL]` | A character who knows the ability at 25 percent or more of the class maximum offers to teach it to a student in the same room. When the teacher is a character, the student is asked `Is this Ok (y/N)?`; the student pays the learning cost. An NPC teacher needs no confirmation. |
| `LEARN [SKILL] ([FROM] [TEACHER])` | Says the right words to a teacher and then trains; `LEARN FROM [RECIPE BOOK]` studies a recipe book instead. |
| `GAIN [SKILL]` | Says `GAIN ...` to the room; a teacher NPC that hears it teaches (see below). `GAIN` alone asks you to use `QUALIFY`. |
| `TRAIN [STAT/CLASS] ([TEACHER])` | Spends a training session on a stat or a new class. |
| `PRACTICE [SKILL] ([TEACHER])` | Spends a practice point to raise proficiency of a known skill, with a teacher who knows it better. |
| `TEACHLIST ([TEACHER])` | Shows what a teacher here offers *you*, with prices, and what they refuse. |
| `NOTEACH` | Toggles refusing to teach or be taught. |
| `RESPEC ([THING])` | Where a retraining place exists (`Prop_Respec`), undoes purchases and refunds them. |
| `SKILLS`, `SPELLS`, `PRAYERS`, `CHANTS`, `SONGS`, `POWERS`, `ABILITIES` | List what you know and your proficiency in it; each accepts a domain, a name and a level. |
| `AUTOINVOKE` | Toggles which passive abilities are active. |

The conditions for learning (what `canBeLearnedBy` checks), all of which a
refused student is told:

1. The student can pay the learning cost (see below).
2. The class (or the shared pools) lists the ability, the student is high
   enough *level*, and meets the mapping's mask and required skills.
3. The student's intelligence is at least 2, and not so low that a skill at
   that point in the class's list is beyond them (`INT + 6` must reach
   `base max stat * skill level / highest level in the class list`).
4. The student does not already know it.
5. A teacher, if there is one, knows it at 25 percent of the maximum or more.
6. Neither is fighting, sleeping or sitting, and neither has `NOTEACH` set
   (NPCs have it set unless they are teachers).

The proficiency a newly learned ability starts at is the class mapping's
default proficiency (zero if none).

### Costs of learning

`coffeemud.ini` sets the price in `SKILLCOST` (default one training session),
`COMMONCOST` (default one train) and `LANGCOST` (three practices): a number
and a word `TRAIN`, `PRACTICE`, `XP`, `GOLD` or a currency name, with `@x1` the
ability's qualifying level and `@x2` the player's level in formulas, and
exceptions by ID:

```ini
SKILLCOST=1 TRAIN, Skill_WildernessLore 1 PRACTICE, Spell_Sleep 50*@x1 GOLD
```

A class's mapping may override the price per ability. `PRACMAXPCT` caps how
far practice alone can take a skill. Per-level practice and train grants are
set by `FORMULA_PRACTICESPERLEVEL`, `FORMULA_TRAINSPERLEVEL` and
`TRAINSATLEVELS`; see [world-settings.md](world-settings.md) and
[03-characters.md](03-characters.md).

### Teacher NPCs

NPCs refuse to teach unless they carry a teaching marker, so a trainer must be
set up deliberately:

- [`MOBTeacher`](24-behaviors-and-masks.md#mobteacher) (a behavior) makes the
  mob teach skills. Its parameters list class names, ability names, expertise
  names, percentages and flags (`NOCOMMON`, `NOEXPS`, `NOTEACH`); with none
  it teaches everything basic. The mob is given the abilities it teaches.
- [`Prop_Trainer`](23-properties-reference.md#prop_trainer) makes a mob a
  suitable target for `TRAIN` (stats, classes, expertises) and, unless
  `NOTEACH` is in its parameters, a teacher too.
  [`Prop_StatTrainer`](23-properties-reference.md#prop_stattrainer) sets the
  mob's six stats (default 25) so it can train stats, and likewise accepts
  `NOTEACH`.

A player teaches by saying `teach me [SKILL]` or `gain [SKILL]` to the NPC in
the room, or by typing `LEARN [SKILL] FROM [TEACHER]` or `PRACTICE [SKILL]
[TEACHER]`. CoffeeSUD (item P16) adds, to both `MOBTeacher` and `Prop_Trainer`,
the following parameters, written among the others in any order:

| Parameter | Meaning |
| --- | --- |
| `STUDENTMASK="[ZAPPER MASK]"` | Whom it teaches at all. |
| `REFUSEMSG="[TEXT]"` | What it says to anyone the mask turns away. |
| `SKILLMASK.[ABILITY ID]="[ZAPPER MASK]"` | Whom it teaches that one skill. |
| `PRICE="[N] per level"` or `PRICE=[N]` | Money it takes per lesson, on top of the normal cost (`per level` multiplies by the student's level). |
| `HIDDEN=[ID],[ID]` | Skills it teaches but leaves off `TEACHLIST`. |

These gate `TEACH ME`, `GAIN`, `PRACTICE` and `TRAIN` with that NPC, and `TRAIN`
with no trainer named skips a trainer who refuses. Example:

```text
Prayer_Bless Prayer_Sanctuary STUDENTMASK="-CLANRANK '+Temple of Arden>=ACOLYTE'" SKILLMASK.Prayer_Sanctuary="-CLANRANK '+Temple of Arden>=PRIEST'" PRICE="5 per level"
```

## How mobs, items and rooms get abilities

### The mob editor

In `MODIFY MOB` the *Abilities* field takes `[ABILITY ID]` to add or remove an
ability and `[ABILITY ID](parameters)` to add one with a parameter string. A new
entry starts at proficiency 75 and is switched on at once if it is automatic.
Archon-domain abilities can be added only by a sysop. A mob casts and uses what it
has, at its own level, as its combat AI decides (hostile abilities against the
victim, beneficial ones on itself or allies); see
[21-mobs.md](21-mobs.md) and [29-combat-and-encounters.md](29-combat-and-encounters.md).

### Scripts

| Command | Effect |
| --- | --- |
| `MPENABLE [MOB] [ABILITY OR EXPERTISE] [PROF] [PARMS]` | Gives the ability at that proficiency, or changes it if known. `PROF` may be `++N` or `--N` to adjust the current value. `PARMS` becomes the ability's text. A name that is not an ability but is an expertise ID adds the expertise. A language the target does not know is learned through the language system. |
| `MPDISABLE [MOB] [ABILITY OR EXPERTISE]` | Removes the ability (or expertise). |
| `MPCAST [ABILITY] [TARGET]` | The script's mob uses the ability at 100 percent proficiency on the target as a normal cast: costs, cooldown, masks and fizzling all apply. When the host is not a mob, its stand-in mob is restored to full strength first. |
| `MPCASTEXT [ABILITY] [TARGET] [ARGS]` | As `MPCAST`, with the rest of the line given to the ability the way a player would type it. |
| `MPAFFECT [ABILITY] [TARGET] [N TICKS] [PARMS]` | Puts the ability on the target as an automatic cast (no cost, never fizzles). For a property it is added permanently, or timed through a `TemporaryAffects` effect when `N TICKS` is given. For other abilities `N TICKS` sets how long the effect lasts. `property:[ID]` forces the permanent-property form for any ability. |
| `MPUNAFFECT [TARGET] [ABILITY ID or ALL]` | Ends the named effect, or every uninvokable effect. |
| `MPLEARNLANG`, `MPLEARNRECIPE`, `MPTRAINS`, `MPPRACS`, `MPRESPEC`, `MPPERK` | Related progression commands. |

Full syntax is in [27-mobprog-commands.md](27-mobprog-commands.md)
([MPENABLE](27-mobprog-commands.md#mpenable),
[MPDISABLE](27-mobprog-commands.md#mpdisable),
[MPCAST](27-mobprog-commands.md#mpcast),
[MPAFFECT](27-mobprog-commands.md#mpaffect),
[MPUNAFFECT](27-mobprog-commands.md#mpunaffect)). A script can also react to
learning: a `GenAbility`'s `ONLEARN` function runs when a mob gains it, and
`SKILL_PROG LEARN` fires for any ability.

### Items that grant or cast

These properties make an item grant abilities while owned, worn, ridden or in a
room, or cast spells on its owner. Their parameters are a list of ability IDs
separated by semicolons, each optionally with a percentage (proficiency or
chance) and `(parameters)`, finishing with an optional `MASK=` zapper mask. Full
syntax is in [23-properties-reference.md](23-properties-reference.md).

| Property | Gives the ability or casts the spell |
| --- | --- |
| [`Prop_HaveEnabler`](23-properties-reference.md#prop_haveenabler) | to the owner, while owned |
| [`Prop_WearEnabler`](23-properties-reference.md#prop_wearenabler) | to the owner, while worn, wielded or held |
| [`Prop_RideEnabler`](23-properties-reference.md#prop_rideenabler) | to the rider, while ridden |
| [`Prop_HereEnabler`](23-properties-reference.md#prop_hereenabler) | to everyone in the room or area |
| [`Prop_HaveSpellCast`](23-properties-reference.md#prop_havespellcast), [`Prop_WearSpellCast`](23-properties-reference.md#prop_wearspellcast), [`Prop_RideSpellCast`](23-properties-reference.md#prop_ridespellcast), [`Prop_HereSpellCast`](23-properties-reference.md#prop_herespellcast) | puts the spell on the owner, wearer, rider or occupants, kept up as long as the condition holds |
| [`Prop_UseSpellCast`](23-properties-reference.md#prop_usespellcast), [`Prop_UseSpellCast2`](23-properties-reference.md#prop_usespellcast2) | on use, filling, eating or touching |
| [`Prop_FightSpellCast`](23-properties-reference.md#prop_fightspellcast) | on a weapon: a spell on the target struck, with a chance |
| [`Prop_SpellAdder`](23-properties-reference.md#prop_spelladder) | the same list syntax, used for rooms, items and mobs, and by `GenAbility`'s `POSTCAST*` fields |
| [`Prop_AbilityImmunity`](23-properties-reference.md#prop_abilityimmunity), [`Prop_RestrictSpells`](23-properties-reference.md#prop_restrictspells), [`Prop_RestrictSkills`](23-properties-reference.md#prop_restrictskills), [`Prop_MagicFreedom`](23-properties-reference.md#prop_magicfreedom) | the opposite: immunity to, or a ban on, listed abilities, or on all magic |

*Scrolls, wands, potions and similar items* hold spells instead of granting
them. Reading a scroll casts one of its spells if the reader knows a
"read magic" skill that matches (`Spell_ReadMagic`, `Prayer_ReadPrayer`,
`Chant_ReadRunes`, `Song_ReadMusic`), at the scroll's level, for a small mana
cost (none if the reader's class level is already above the spell's qualifying
level, 5 if not yet, 25 if the reader's class cannot have the spell at all).
The scroll loses a use. Item types are covered in
[22-items.md](22-items.md).

### Rewards

Achievement rewards, `MPREWARD` and quest rewards can give an effect:
`1 EFFECT(ID=Prop_Adjuster PARMS="con+1" TICKS=0)` keeps it for good with
`TICKS=0`, else it lasts that many ticks. `AHELP ACHIEVEMENTREWARDS` lists all
reward kinds ([08-quests.md](08-quests.md)).

## Putting effects on things

Every room, exit, item, mob and area has an effects list that the builder edits.

### The Effects field

In the editor for any such object (`MODIFY ROOM`, `MODIFY MOB`, `MODIFY ITEM` ...)
choose the number of *Effects* and enter at the prompt:

```text
Enter an effect to add, -ID to remove, =ID or its number to edit, ?P for the properties that fit (?)
:
```

- An effect ID adds it; the next prompt asks for its parameters (type `?` there
  for the ability's help). Blank parameters keep the default text.
- `-ID` removes it; `=ID` or its list number edits the parameters; `?` lists
  every ability; `?P` lists the properties that suit this kind of object.
- Only *savable* (permanent) effects are listed. Timed effects cast during play
  are not part of the thing's definition.
- Archon-domain abilities can be added only by someone who already knows them.

An effect added here is permanent and non-uninvokable, and is written with the
object. In a world file it appears as an affect element inside the object's
extra data:

```xml
<AFFECS><AFF><ACLASS>Prop_Smell</ACLASS><ATEXT>stinky</ATEXT></AFF></AFFECS>
```

Many abilities that are not properties also work as permanent effects: a trap
(`Trap_RoomPit`), a ward spell, `Spell_Light` on a lamp, a disease on a
corpse, a poison on a drink. They use the ability's `text()` for their
parameters, and a permanent spell effect never times out.

### Commands that add timed effects

For a timed effect, use the ability itself (`CAST`, `PRAY`, `MPCAST`,
`MPAFFECT`), or an archon wand/item. `MPAFFECT` is the scripter's tool; the
GOD cheat commands are in [19-configuration-and-administration.md](19-configuration-and-administration.md).

## Finding abilities in game

The shipped help files describe every stock ability (`spell_help.ini`,
`prayer_help.ini`, `chant_help.ini`, `songs_help.ini`, `skill_help.ini`,
`common_help.ini`, `diseases.ini`, `poisons.ini`, `traps.ini` under
`resources/help/`). Each ability's entry begins with a generated header, built
from the live ability: its name, domain, which classes get it at what level,
cost, quality, targets and range.

| Command | Use |
| --- | --- |
| `HELP [NAME or ID]` | The ability's help page (any player). A `GenAbility`'s page is its `HELP` field. |
| `AHELP [TOPIC]` | Administrative topics: properties (`AHELP PROP_SPELLADDER`), behaviors, `AHELP GENABILITY`, `AHELP GENPOISON` ..., `AHELP SKILLSCRIPTS`, `AHELP COMPONENTS`, `AHELP STATEFFECTS`. |
| `LIST ABILITIES ([TYPE|DOMAIN|FLAG])` | Every ability class loaded, including generics. Add a domain or flag word: `LIST ABILITIES healing`, `LIST ABILITIES moving`, `LIST ABILITIES enchantment/charm`. |
| `LIST SPELLS`, `PRAYERS`, `CHANTS`, `SONGS`, `SKILLS`, `THIEFSKILLS`, `COMMON`, `LANGUAGES`, `DISEASES`, `POISONS`, `TRAPS`, `PROPERTIES`, `SUPERPOWERS` (or `POWERS`), `TECHSKILLS` | The same list for one type, each taking an optional domain or flag. |
| `LIST ABILITYDOMAINS`, `LIST ABILITYCODES`, `LIST ABILITYFLAGS` | The exact words for classification and flags. |
| `LIST ALLQUALIFYS`, `LIST COMPONENTS`, `LIST EXPERTISES` | The shared qualification map, components and expertises. |
| `SKILLS`, `SPELLS`, ... `ABILITIES [NAME]` | What the current character knows; naming one shows its level and proficiency. |
| `QUALIFY` | What the current character may learn now. |
| `SKILLTREE` | Prerequisite chains for the class. |

`LIST` commands need the matching security flags (`CMDABILITIES` or
`LISTADMIN`); see [19-configuration-and-administration.md](19-configuration-and-administration.md).
The generated [tool-reference.md](tool-reference.md) mirrors the help text of
all of these commands.
