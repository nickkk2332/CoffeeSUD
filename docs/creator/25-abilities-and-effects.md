# Abilities and effects

Almost everything that does something to a character, a room or an item in
CoffeeSUD is an *ability*: spells, prayers, chants, songs, thief and fighter
skills, crafting and gathering skills, languages, diseases, poisons, traps and
the "properties" that builders attach to rooms, exits, items and mobs. When an
ability is on something and running it is also an *effect* (the engine's word is
"affect"). This chapter is for world creators and scripters, with developer
notes where they matter and a short section for engine developers. It explains how abilities
are classified and costed, how characters and mobs obtain them, how timed
effects behave and are saved, how to build your own without Java (`GenAbility`,
`GenTweakAbility`, `GenPoison`, `GenDisease`, `GenTrap`), how to find the stock
abilities in game, and gives worked examples, persistence rules and a
troubleshooting list.

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
- [For engine developers](#for-engine-developers)
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
matched ignoring case. A type word must be given in full, with its space
(`THIEF SKILL`, `COMMON SKILL`); a domain word may also be the start or the end
of the name (the first domain that fits is used), so `SPELL,EVOCATION` matches
the domain `INVOCATION/EVOCATION`. A word that matches nothing is ignored
without an error. The valid words:

- Types: `SKILL`, `SPELL`, `PRAYER`, `SONG`, `TRAP`, `PROPERTY`, `THIEF SKILL`,
  `LANGUAGE`, `CHANT`, `COMMON SKILL`, `DISEASE`, `POISON`, `SUPERPOWER`.
  (`LIST ABILITYCODES` prints them with underscores, `THIEF_SKILL`, but a
  `CLASSIFICATION` needs the space; the underscore form is ignored and leaves
  the type at `SKILL`.)
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
use the numbers (`13`) if you really need the tech type. The word `ARCHON` as
a domain cannot be typed either: it is taken for the start of `ARCHON SKILL`
and dropped, so use the number (an archon skill is `288`, which is `SKILL` with
domain 9 shifted left five bits).

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
One more consequence (from reading `castingQuality`): against the caster's
current victim, a hostile ability reports itself as `INDIFFERENT` when the
fight range is greater than its `MAXRANGE`, and then the cast is not flagged
malicious (the cast message itself then starts no fight and is not saved
against). A hostile `GenAbility` should
therefore set `MAXRANGE` as far as it is meant to reach; the default `MELEE`
only suits melee fights.

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
   (`coffeemud.ini`: `MANAMINCOST=-1, 10`; the `-1` means the ability's lowest
   qualifying level, and the `10` is the floor under that). The minimum is
   applied before the overrides above, so an override may go below it.

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
`DEFABLETIME=Skill_Recall 2.0, 0.10`. A hostile ability always uses the
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
proficiency: the chance is the proficiency, plus or minus any `PROF` ability
adjustments from items and properties, plus twice the `XLEVEL` expertise
level, kept between 5 and 95 percent (unless the `DIS955RULE` disable flag is
set), plus half the expertise level again. A miss fizzles the ability with its
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
  `Prayer_CureDisease` refuse a disease that someone else caused and whose
  difficulty is above the healer's level;
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
appended. `AFFECTS [TYPE] [NAME|TYPE|DOMAIN|REMAIN]` filters by ability type and sorts
(a domain word filters too: `AFFECTS ABJURATION` lists only the effects of that
domain);
`AFFECTS spell`, `AFFECTS spell remain`, `AFFECTS common_skill name` (join the words of a
type or domain with an underscore). A character with the `CMDMOBS` flag can say `AFFECT`
followed by `ROOM` (or `HERE`), `AREA` (or `ZONE`) or a target's name to see the effects on that
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
   The kind-of-harm saves are not bare numbers: each is the stored save plus a
   bonus from the target's stats (for example `SAVE_FIRE` adds half of
   constitution plus dexterity, `SAVE_MIND` a third of wisdom, intelligence
   and charisma).
4. Clamp the result to 5 to 95 percent and roll. A roll below it means the
   target *resists*: the engine sends a resistance message ("X shake(s) off the
   mental attack"), the effect is not applied, and damage from the ability is
   halved.

The cast message of a spell, prayer, chant or superpower is itself a magic
cast, so it is checked with the family save plus the generic magic save
(`CAST_SPELL`). A `GenAbility` with an `ATTACKCODE` then sends a second message,
which is checked with the family save plus the save for that kind of harm. Each
message gets its own roll, and if either resists, the effect is not applied and
damage is halved.

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

In `MODIFY MOB` the *Abilities* field takes an ability ID to add or remove it
and `ID(parameters)` to add one with a parameter string. A new
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
| `MPAFFECT [ABILITY] [TARGET] [N]TICKS [PARMS]` | Puts the ability on the target as an automatic cast (no cost, never fizzles). The duration word is the number run straight into `TICKS`, as `120TICKS` (a space between them is not recognised). For a property it is added permanently, or timed through a `TemporaryAffects` effect when the duration is given. For other abilities it sets how long the effect lasts. `property:[ID]` forces the permanent-property form for any ability. |
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

## GenAbility: custom abilities

`GenAbility` is a data-driven ability: a spell, skill, prayer, song or property
described by fields instead of Java. One `GenAbility` definition is registered
under its own ID and then behaves like any stock ability: it can be taught,
cast, given to classes, put on rooms, items and mobs, and appear in lists and
help. Source:
[GenAbility.java](../../CoffeeMud/com/planet_ink/coffee_mud/Abilities/GenAbility.java).

Use it when an ability is one of: a single-target or area hit or heal with a
formula; a buff or debuff that changes stats for a while; a spell that
combines existing abilities (adds effects, casts others); a passive property
whose behaviour is a script; a summon of catalog mobs. Reach for Java (see
[32-engine-programming.md](32-engine-programming.md)) when you need behaviour
the fields cannot express, such as moving the caster, changing exits, or
interacting with many objects. For small changes to a stock ability, a
[tweak](#gentweakability-renamed-and-adjusted-java-abilities) is simpler.

### Creating, modifying and destroying

You need the `CMDABILITIES` security flag (archons, and builders whose security
group includes it). In a normal game the commands are in the builder tools;
builder mode and `DEV` mode are described in
[18-installing-and-running.md](18-installing-and-running.md).

```text
CREATE ABILITY [ID]
MODIFY ABILITY [ID]
DESTROY ABILITY [ID]
```

- `[ID]` is the new ability's Java-style ID, with no spaces (`Spell_Cinderlash`,
  `Prop_Haunted`). The ID is permanent: to rename, destroy and create again.
  Conventions: `Spell_`, `Prayer_`, `Chant_`, `Skill_`, `Prop_` prefixes
  match the stock IDs and make `LIST ABILITIES` easy to read.
- `CREATE ABILITY` opens the editor. With `EDITORTYPE=MENU` in
  `coffeemud.ini` the editor lists every field with a number and its current
  value; type a number to change it, a blank line or `0` to finish. With
  `EDITORTYPE=WIZARD` it steps through the fields in order. Each prompt shows
  the allowed words; typing `?` often prints help.
- `MODIFY ABILITY` edits an existing generic ability. The change applies at once
  to every copy in the world (the message reads "X's everywhere shake under
  the transforming power!"). Other kinds have their own verbs, and `MODIFY
  ABILITY` names the right one if you use the wrong one: `MODIFY TRAP`, `MODIFY
  POISON`, `MODIFY DISEASE`, `MODIFY TWEAK`, `MODIFY LANGUAGE`, `MODIFY
  CRAFTSKILL`, `MODIFY WRIGHTSKILL`, `MODIFY GATHERSKILL`, `MODIFY BUILDSKILL`.
- `DESTROY ABILITY [ID]` removes a generic ability of any of those kinds
  (`DESTROY TRAP`, `DESTROY POISON` ... all reach the same code). Stock Java
  abilities cannot be destroyed or modified. Copies already on mobs and things
  stay until their records are reloaded; remove the ID from classes, races and
  item properties yourself.
- `CREATE ABILITY` on the ID of a stock Java ability does not make a tweak: it
  *replaces* that ID with a generic ability seeded from the old one's
  classification, trigger words, range, flags, cost type, masks and quality
  (and with `MOCKABILITY` and `POSTCASTABILITY` set to the old ID). Clear those
  two fields before you rely on the new one, or better, make a
  [tweak](#gentweakability-renamed-and-adjusted-java-abilities) under a new ID.

In the `MENU` editor the `GenAbility` fields are numbered: 1 name, 2 type and
domain, 3 command words, 4 minimum range, 5 maximum range, 6 ticks between casts,
7 duration override, 8 affect string, 9 auto-invoking, 10 flags, 11 override cost,
12 cost type, 13 number of arguments, 14 can affect, 15 tick/periodic affects,
16 can target, 17 quality, 18 affect adjustments, 19 caster mask, 20 caster
mask failure message, 21 target mode, 22 most targets, 23 area message, 24 to 27
the summon fields, 28 action cost, 29 combat action cost, 30 components, 31
script, 32 target mask, 33 fizzle message, 34 auto-cast message, 35 normal-cast
message, 36 post-cast message, 37 uninvoke message, 38 attack type, 39 quiet
effect ID, 40 public effects, 41 extra castings, 42 damage/healing formula, 43
help text, 44 windup ticks, 45 windup warning.

The new ability is registered as soon as you set its ID, and written to the
content database when you leave the editor. In builder mode `SAVE` (and a
clean shutdown) exports it to
`world/custom/abilities/[id].cmare` ([The saved record](#the-saved-record)).
Add it to a class or to `allqualifylist.txt` so characters can learn it
([How characters get abilities](#how-characters-get-abilities)) or hand it
to a mob directly.

### GenAbility fields

The fields, with their editor prompts (the menu numbers are listed above)
and the names that appear in the saved record, in `GMODIFY` and in JS (`ability.setStat("NAME", ...)`). Fields with no
editor prompt are marked; set them by editing the record (see
[The saved record](#the-saved-record)).

| Field | Editor prompt | Default | What it does |
| --- | --- | --- | --- |
| `CLASS` | (set by `CREATE`) | `GenAbility` | The ability's ID. |
| `TEXT` | (none) | empty | The *instance* text of an effect or property: the parameter string given where it is attached. Not part of the definition. Read by `EFFECTPARM(KEY)` and `$PARMS` in the script. |
| `NAME` | Ability/Skill name | `an ability` | The name players see and type (`cast cinder` matches `Cinderlash`). |
| `CLASSIFICATION` | Type, Domain | `SPELL,ABJURATION` | [Type and domain](#classification-type-and-domain): `SPELL,EVOCATION`, `SKILL,MARTIAL_LORE`, `PROPERTY`. A number is also accepted. |
| `TRIGSTR` | Command Words | `CAST,CA,C` | Comma list of the words that invoke it, stored in upper case. For a spell, prayer or chant keep the family's words (`CAST,CA,C`); for a skill use its own command (`MOONBLADE`). |
| `MINRANGE`, `MAXRANGE` | Minimum Range, Maximum Range | `MELEE` | [Combat range](#range) of a hostile use. |
| `TICKSBETWEENCASTS` | Ticks Between Casts | `0` | Cooldown, in ticks. |
| `TICKSOVERRIDE` | Duration Override (0=NO) | `0` | Formula: exact ticks if positive, standard if `0`, percent of standard if negative. Variables below. |
| `DISPLAY` | Affect String | `(An Affect)` | The text `AFFECTS` shows for the effect. Blank hides it; wrap in `( )` to have the time left shown. |
| `AUTOINVOKE` | Is Auto-invoking | `false` | A known ability is turned into a permanent automatic effect on its owner (a passive skill). |
| `FLAGS` | Skill Flags | none | [Flag words](#ability-flags). |
| `OVERRIDEMANA` | Override Cost | `-1` | `-1` normal cost formula; a number; or `2147483647` minus a percent. |
| `USAGEMASK` | Cost Type | `MANA` | `MANA`, `MOVEMENT`, `HITPOINTS`, comma list. |
| `NUMARGS` | Num. Arguments | `0` | With 2 or more, the first word typed is the target and the rest (merged into the last argument) are free text: available as `$0`.. in script hooks, and as `@x1`... in `MOCKABLETEXT` and the text part of `POSTCASTAFFECT`. |
| `CANAFFECTMASK` | Can Affect | `MOBS` | What a lasting effect can sit on; `0` makes it instant. |
| `TICKAFFECTS` | Tick/Periodic Affects | `false` | Repeats the post-cast message each tick of the effect (see [gotchas](#troubleshooting-and-gotchas)). |
| `CANTARGETMASK` | Can Target | `MOBS` | What the caster can aim it at. |
| `QUALITY` | Quality Code | `BENEFICIAL_OTHERS` | [Quality](#quality). |
| `HERESTATS` | Affect Adjustments | none | While the effect is on something, stat changes in `Prop_Adjuster` syntax: `dex+2 savefir+20`, optional `MASK=`. Variables `@x1`..`@x9` are replaced first. See [`Prop_HereAdjuster`](23-properties-reference.md#prop_hereadjuster). |
| `CASTMASK` | Caster Mask | none | A [zapper mask](24-behaviors-and-masks.md) the caster must pass; failing says `You do not meet the requirements: ...` or `CASTFAILMSG`. `-IF "..."` conditions see the target as `$t`. Not checked for automatic casts. |
| `CASTFAILMSG` | Caster Mask failure message | none | Replaces the standard refusal (`<T-NAME>` is the target). |
| `TARGETMODE` | Target mode | `SINGLE` | [Area abilities](#area-abilities). |
| `MAXTARGETS` | Most targets | none | Formula, `@x1` caster level, `@x2` number of candidates. |
| `AOEMSG` | Area message, once | none | Shown once before the per-target messages. |
| `SUMMON` | Summon | none | A catalog mob name or mob class brought when the cast works. [Summons](#summons). |
| `SUMMONCOUNT` | Summon count | `1` | Formula, capped at 20. |
| `SUMMONLEVEL` | Summon level | mob's own | Formula. |
| `SUMMONFLAGS` | Summon flags | none | `FOLLOW ASSIST NOSAVE DISMISSONEXPIRE BIND`. |
| `ACTIONCOST` | Action cost | `-1` | Actions used out of combat; `-1` the ini default. |
| `COMBATACTIONCOST` | Combat action cost | `-1` | Actions used in combat or when malicious. |
| `COMPONENTS` | Components | none | A `components.txt` line, [see below](#costs-and-components-in-a-genability). |
| `SCRIPT` | Scriptable Parm | none | The ability's script (MOBPROG text or `LOAD=file`). [Script hooks](#script-hooks). |
| `TARGETMASK` | Target Mask | none | A zapper mask the target must pass; failing says `The target is invalid: ...` or `TARGETFAILMSG`. |
| `TARGETFAILMSG` | (none) | none | The refusal for `TARGETMASK`. |
| `FIZZLEMSG` | Fizzle Message | `<S-NAME> attempts to use this ability against <T-NAME>, and fails` | When proficiency fails. |
| `AUTOCASTMSG` | Auto-Cast Message | `An amazing thing happens to <T-NAME>!` | Cast message when cast automatically (item, script, trap). |
| `CASTMSG` | Normal-Cast Message | `<S-NAME> uses an ability against <T-NAME>` | Cast message when a character casts it. |
| `POSTCASTMSG` | Post-Cast Message | `<T-NAME> is <DAMAGE> by an ability from <S-NAME>!` | Shown when the effect lands (with the damage verb filled in for `<DAMAGE>`). Blank shows nothing. |
| `UNINVOKEMSG` | Uninvoke Message | none | Told to the host when the effect ends. |
| `ATTACKCODE` | Attack-Type | `NOTHING` | A second message of this kind aimed at the target so its matching [saving throw](#saving-throws) applies: `FIRE`, `COLD`, `WATER`, `GAS`, `MIND`, `GENERAL`, `ACID`, `ELECTRIC`, `POISON`, `UNDEAD`, `PARALYZE`, `DISEASE`, ... (any message type word). Also names the damage's message type. |
| `MOCKABILITY` | Quiet effect ID | none | The ID of another ability held silently as a second effect while this one lasts (it adds to this one's stat changes and message handling). |
| `MOCKABLETEXT` | (none) | none | Parameter text for the quiet effect; `@x1`.. are the words the caster typed. |
| `POSTCASTAFFECT` | Public effects | none | A [`Prop_SpellAdder`](23-properties-reference.md#prop_spelladder) list of abilities put as effects on the target when the cast lands, lasting as long as the main effect: `Spell_Slow;Prop_Adjuster(/con-2)`. |
| `POSTCASTABILITY` | Extra castings | none | A `Prop_SpellAdder` list cast *at* the target afterward (with chance prefixes): `20%;Skill_Trip`. |
| `POSTCASTDAMAGE` | Damage/Healing Formula | `0` | Positive damages the target, negative heals it. Variables below. |
| `HELP` | Help Text | `<ABILITY>This ability is not yet documented.` | The page `HELP [NAME]` shows. Starting with `<ABILITY>` puts the generated header (domain, classes, cost, range) above the text. |
| `CHANNELING` | (none) | `false` | A channeled effect ends when its invoker speaks, moves or uses their hands. It also keeps the invoker from auto-attacking. |
| `WINDUP` | Windup ticks, for monsters (0=NO) | `0` | A monster that casts it first warns for that many ticks ([29-combat-and-encounters.md](29-combat-and-encounters.md)). |
| `WINDUPMSG` | Windup warning | blank | The warning text; blank uses `<S-NAME> begin(s) to gather power!`. |

Some further notes about the table:

- `FLAGS`, `CANAFFECTMASK`, `CANTARGETMASK` and `USAGEMASK` take comma lists of
  words, a number, or a unique start of a word. `QUALITY` and the range fields
  take one word (note that a start such as `BENEFICIAL` matches the first
  choice that starts with it, `BENEFICIAL_SELF`).
- Message fields are raw text: they may use `<S-NAME>`, `<T-NAME>`,
  `<T-NAMESELF>`, `<S-HIS-HER>` style tokens and the colour codes `^S ... ^?`
  (spell colour; `^?` restores). See
  [11-presentation-language.md](11-presentation-language.md).
- `MOCKABILITY`, `MOCKABLETEXT`, `TARGETFAILMSG` and `CHANNELING` have no
  editor prompt in this build.

### Formulas and variables

Numeric fields that accept a formula (`TICKSOVERRIDE`, `POSTCASTDAMAGE`,
`SUMMONCOUNT`, `SUMMONLEVEL`, `MAXTARGETS`, `OVERRIDEMANA`) use the engine's
formula language ([`AHELP MATH_FORMULA`](tool-reference.md)):
numbers, `+ - * /` and `\` (both divide), `%` (remainder), `^` (power), parentheses, `?` (a random number
between its two sides, so `2?6` is a number from 2 to 6), `>` and `<`
(the larger or smaller of its two sides) and `=` and `!` (1 if equal or
different). **Evaluation is strictly left to right with no operator
precedence**: `2?6+@x1/3` means `((2?6)+@x1)/3`. Use parentheses.

Inside a cast, these placeholders are replaced:

| Variable | Value |
| --- | --- |
| `@x1` | the caster's level |
| `@x2` | the target's level (the caster's, with no target) |
| `@x3` | the caster's `XLEVEL` expertise level |
| `@x4` to `@x8` | the caster's `X1` to `X5` expertise levels |
| `@x9` | the adjusted caster level (what duration and strength scale with) |

`MAXTARGETS` is different: `@x1` is the adjusted caster level and `@x2` is the
number of candidate targets. `HERESTATS` takes the same variables as text
substitution (`hitpoints+@x9`). When a `GenAbility` is added as a property
rather than cast, `@x1`, `@x2` and `@x9` are the level of the thing it is on
(at least 1) and `@x3` to `@x8` are 0.

### What happens when it is cast

For developers and scripters who need to know the order. A player's
`CAST CINDERLASH ORC` (or the skill's own command) runs `invoke`:

1. **Target.** `BENEFICIAL_SELF` and `OK_SELF` use the caster (an automatic
   cast to another mob honours that mob) and refuse if the caster already has the
   effect. Other qualities look for a target by `CANTARGETMASK`: `MOBS` a mob
   named in the arguments or in combat, `ITEMS` an item, `ROOMS` the caster
   (the effect then lands on the caster unless a script or item casts it
   automatically at a room), `EXITS` only checks that the named door or
   direction exists, `0` no target, and other combinations a mob or an item.
   No target means a refusal.
2. **Checks that cost nothing.** `CASTMASK` (message: `CASTFAILMSG` or the
   standard one), then the script's `CANCAST` hook, then `TARGETMASK`
   (`TARGETFAILMSG`). All three are skipped for automatic casts.
3. **Windup**, for a monster with `WINDUP`: it warns and the cast comes later.
4. **Armor.** For spells (cloth) and chants and thief skills (leather), a
   player who does not qualify for the ability by level and wears heavy armor
   has a 50 percent chance to fumble.
5. **Area abilities** divert here ([Area abilities](#area-abilities)).
6. **Payment.** Cooldown, bindings (movement costs), mana/moves/hit points,
   components, the compounding rule (`MANACOMPOUND`), and the pre-invoke
   message. Failing any of these is a refusal with no effect.
7. **Proficiency roll.** On failure: `FIZZLEMSG`, shown as a hostile fizzle for
   `MALICIOUS` quality, otherwise as a visual one. Nothing else happens.
8. **Cast message.** `CASTMSG` (or `AUTOCASTMSG` when automatic) is sent as a
   message whose type comes from the ability type (spells, prayers and
   superpowers are somatic casts; chants verbal; thief skills thief actions;
   languages and properties visual; the rest noisy movement), flagged malicious
   for hostile qualities. If `ATTACKCODE` is set, a second message of that kind
   follows so the matching save applies. The room, the target and every effect
   in sight may veto them (`okMessage`).
9. **Result.** If the target resisted (`value` above zero) no effect is applied
   and any damage is halved. Otherwise, if `CANAFFECTMASK` is not `0` and there
   is a target, the effect is put on it with `maliciousAffect` or
   `beneficialAffect` (duration as in [Duration](#duration)); `HERESTATS` and
   `MOCKABILITY` are set up on it; the cooldown starts; `SUMMON` runs; and
   `POSTCASTAFFECT` effects are added with the same duration.
10. **Aftermath.** `POSTCASTMSG` and the `POSTCASTDAMAGE` formula (damage as
    a burst, or healing), then the script gets its cast event and `ONCAST`,
    then `POSTCASTABILITY` is cast at the target. If damage or the aftermath
    killed the target, any effect just applied is removed.

When the effect later ends, the host is told `UNINVOKEMSG`, the script's
`ONEXPIRE` runs and any `POSTCASTAFFECT` effects it added are removed.

### Using a GenAbility as a property

Put a `GenAbility` on a room, exit, item or mob like any property
([Putting effects on things](#putting-effects-on-things)). It then works as a
permanent effect with no casting:

- its `HERESTATS` apply at once, at the level of the thing it is on;
- its `MOCKABILITY` effect is created with `MOCKABLETEXT`, silent and permanent;
- its `SCRIPT` runs as that thing's own script: ticks, messages and triggers
  (`RAND_PROG`, `GREET_PROG`, ...) reach it.

The text typed where it is attached (`LOUD=yes MSG=moan`) reaches the script as
`$PARMS` (all of it) and `EFFECTPARM(KEY)` (a `KEY=VALUE` pair, case
insensitive; `EFFECTPARM(*)` is all of it). One definition therefore serves any
number of settings. As with all script functions, put the comparison inside
the brackets: `effectparm(LOUD == yes)`. Worked example in
[Worked examples](#worked-examples) (a haunted room).

### Script hooks

The ability's `SCRIPT` is ordinary MOBPROG text ([04](04-mobprog.md)). On top of
the normal triggers, the engine calls named functions, each written as a
`FUNCTION_PROG`:

| Hook | When it runs | `$n` | `$t` | `$0`... |
| --- | --- | --- | --- | --- |
| `CANCAST` | After `CASTMASK`, before any cost. `RETURN false` (or `0` or `CANCEL`) refuses the cast. Not run for automatic casts or for the later targets of an area cast. | the caster | the target | the words typed |
| `ONCAST` | After a cast that worked, once per target | the caster | the target | the words typed |
| `ONTICK` | Each tick the effect (or property) is on something | the invoker | the host | none |
| `ONEXPIRE` | When the effect ends | the invoker | the host | none |
| `ONLEARN` | When a mob gains the ability | the learner | the learner | none |

```text
FUNCTION_PROG CANCAST
if !istime(night)
 mpecho The moon is hidden.
 return false
endif
~
FUNCTION_PROG ONEXPIRE
mpecho The moonlight fades.
~
```

Older routes still work: the script receives the cast as a message whose text is
the ability ID, so `IMASK_PROG p [ID]` can match it, and on expiry the text is
`UNINVOKE-[ID]`. `SKILL_PROG` triggers (`LEARN`, `IMPROVE`, `MASTER`, `FAIL`)
fire for every ability, generic or not
([26-mobprog-triggers.md](26-mobprog-triggers.md)). A `GenTrap` has
`ONTRIGGER` and `ONDISARM` instead ([Traps](#traps)).

### Area abilities

`TARGETMODE` turns a single-target cast into an area one:

| Mode | Targets |
| --- | --- |
| `SINGLE` | One target (default). |
| `ALLENEMIES` | Everything the caster would fight in the room (for a `MALICIOUS` ability: the combat-legal targets; otherwise every other mob not in the caster's group). The caster is never one. |
| `ALLALLIES` | The caster and the caster's group members in the room. |
| `ALLINROOM` | Everyone in the room; the caster too unless the ability is `MALICIOUS`. |
| `GROUPANYWHERE` | The caster and the whole group wherever they are. |

The caster's mask, `CANCAST`, windup and the resource cost happen **once**; then
each target gets its own cast: target mask (a target it turns away is skipped),
cast message, saving throw, damage or healing, `HERESTATS`, effects, extra
castings and `ONCAST`. `MAXTARGETS` keeps only that many of the candidates;
`AOEMSG` is shown once before them. With no candidates the caster is told
`There is no one here for [NAME].` before anything is paid.

### Summons

`SUMMON` names a **catalog mob** (`CATALOG` entry by name) or a mob class; each
successful cast brings `SUMMONCOUNT` copies (1 when blank, at most 20) into the
caster's room at `SUMMONLEVEL` (their own level when blank). Each carries
[`Prop_Summoned`](23-properties-reference.md#prop_summoned), gives no experience,
and is not rejuvenated. `SUMMONFLAGS`:

| Flag | Effect |
| --- | --- |
| `FOLLOW` | Follows the caster (so it takes part in party turns and goes along). |
| `ASSIST` | Fights the caster's current victim; without it a follower does not assist on its own. |
| `NOSAVE` | Left out of campaign saves. |
| `DISMISSONEXPIRE` | Departs when the ability's effect ends, or after its duration if it leaves no effect. |
| `BIND` | Departs when its summoner dies or leaves the game. |

### Costs and components in a GenAbility

`ACTIONCOST`, `COMBATACTIONCOST`, `OVERRIDEMANA`, `USAGEMASK` and
`TICKSBETWEENCASTS` travel with the ability's record, so a custom spell needs no
entries in `coffeemud.ini`. `COMPONENTS` is one line of `resources/skills/components.txt`
without the leading `ID=`:

```text
(inventory:consumed:1:a pinch of salt:)
(held:kept:1:*wand*:) && (inventory:consumed:1:SAND:-class +mage)
```

Each part is `([DISPOSITION]:[FATE]:[AMOUNT]:[COMPONENT]:[MASK])`:
disposition `inventory`, `have`, `held`, `worn`, `nearby` or `onground`;
fate `consumed`, `kept` or `wear(N)` (CoffeeSUD: kept, losing `N` uses of
condition each time, breaking at none); the component a material, a resource,
an item name (with `*` wildcards) or `TAG(name)`; and an optional zapper mask
naming who needs it. `&&` and `||` combine parts. The field replaces any
`components.txt` line for that ID. A caster without the components is refused
(`The requirements to use this spell are: ...`, with the ability's type in place
of "spell") before the cost is taken. Holders of the `COMPONENTS` security flag
are exempt (the shipped help also names archons and mobs).

### The saved record

Every `Gen*` ability definition is stored as one element, kept in the content
database and exported by the builder tools to a file per ability:

```text
world/custom/abilities/spell-cinderlash.cmare
world/custom/abilities/spell-cinderlash.script   (only when the SCRIPT has several lines)
```

The file name is the lower-cased ID with other characters turned to dashes.
Its content is the ability's type and every field except `TEXT`:

```xml
<ABILITY ID="Spell_Cinderlash" TYPE="GenAbility">
 <CLASS>Spell_Cinderlash</CLASS>
 <NAME>Cinderlash</NAME>
 <DISPLAY>(An Affect)</DISPLAY>
 <TRIGSTR>CAST,CA,C</TRIGSTR>
 <MAXRANGE>LONG</MAXRANGE>
 <CLASSIFICATION>SPELL,INVOCATION/EVOCATION</CLASSIFICATION>
 <QUALITY>MALICIOUS</QUALITY>
 <CANAFFECTMASK>0</CANAFFECTMASK>
 <CANTARGETMASK>MOBS</CANTARGETMASK>
 <ATTACKCODE>FIRE</ATTACKCODE>
 <POSTCASTDAMAGE>(2?6)+(@x1/2)</POSTCASTDAMAGE>
 <SCRIPT>FILE:spell-cinderlash.script</SCRIPT>
 ...every other field...
</ABILITY>
```

(Abbreviated: the real file lists all the fields in the order of the table
above.) A multi-line script lives in the `.script` file next to it and the
element names it with `FILE:`. The files are the content: at the next start,
if they differ from the content database's checksum, the engine rebuilds the
database from them, so a hand edit to a `.cmare` or `.script` file is picked up
on restart. See [02-world-workflow.md](02-world-workflow.md) for the folder,
`WORLD STATUS`/`DIFF`, and what builder mode writes when, and
[state-and-persistence.md](state-and-persistence.md).

A record written by hand must contain every tag. A tag that is missing is
read as an empty value, which blanks that field (message fields, the
classification and the quality included) instead of leaving its default. Start
from an exported file, or create the ability in the editor and copy the file the
tools write.

An older single file, `world/custom.cmare`, is still read when it exists; the
exporter no longer writes it.

For developers: all `Gen*` abilities keep their per-ID definitions in a static
map shared by every copy, so editing one definition changes all of them at
once. The instance-specific part is only `text()` (and the live effect state).
Round trips use `getStat("ALLXML")` and `setStat("ALLXML", xml)`.

## GenTweakAbility: renamed and adjusted Java abilities

A tweak is a stock Java ability under a *new ID* with some settings changed.
It keeps all the original's behaviour: cast, it casts the original at the
caster's proficiency, through a proxy that substitutes your overrides. Source:
[GenTweakAbility.java](../../CoffeeMud/com/planet_ink/coffee_mud/Abilities/GenTweakAbility.java).

```text
CREATE TWEAK [NEW ID]
MODIFY TWEAK [ID]
DESTROY TWEAK [ID]
```

An ID that already exists is refused (use a new one). The editor offers every
non-property, non-language, non-trap, non-common-skill ability as the base.
Fields (stat codes are those of the saved record):

| Field | Editor prompt | Meaning |
| --- | --- | --- |
| `ORIGINAL` | Base Ability/Skill ID | The Java ability it wraps. |
| `NAME`, `DISPLAY`, `TRIGSTR`, `MAXRANGE`, `MINRANGE`, `CLASSIFICATION`, `FLAGS`, `OVERRIDEMANA`, `USAGEMASK`, `QUALITY`, `TICKSBETWEENCASTS`, `HELP` | as for [GenAbility](#genability-fields) | Blank keeps the original's value. |
| `AUTOINVOKE`, `MAYENCHANT` | Is Auto-invoking, Is enchantable | Whether it is a passive ability; whether it may be put into scrolls and wands. |
| `STRING` | Strings | A table of text replacements: a message string of the original (the English text its code passes to its translation function) mapped to new text. In the editor, type a string to add it (it then asks for the replacement) and type an existing string to remove it. |
| `ACTIONCOST`, `COMBATACTIONCOST`, `COMPONENTS` | as for GenAbility | `-1` keeps the original's. |
| `CASTMASK`, `TARGETMASK` | Cast mask, Target mask | Zapper masks checked before delegating. |
| `POSTCASTABILITY`, `POSTCASTAFFECT` | Post-cast ability, Post-cast affect | `Prop_SpellAdder` lists cast on the target or put on it after a successful cast. |
| `SCRIPT` | Script | Functions `CANCAST` (return `false` or `CANCEL` to stop it, `$t` the target), `ONCAST` (after a cast that worked) and `ONEXPIRE` (when the effect it left wears off). |
| `DAMAGEMULT`, `DURATIONMULT` | Damage multiplier, Duration multiplier | Multiply the damage of its casts and effects, and how long its effects last (`1` for none). |

Players, classes and scrolls refer to the tweak by its new ID; the original
remains available under its own. Example: *Stormcall*, a tweak of
`Spell_Lightning` with `POSTCASTABILITY` `20%;Skill_Trip` and
`DAMAGEMULT` `1.25`.

Note that the `-LOCALE +OUTDOORS` sample in the shipped `GENTWEAK` help is not a
valid zapper mask in this build; use the keys in
[24-behaviors-and-masks.md](24-behaviors-and-masks.md) (for example
`-DOMAIN +WOODS` for "only in woods").

## Poisons

A poison is an ability of type POISON that sits on a mob (poisoning it over
time) or on an item (food, drink or a weapon) that gives it to whoever eats,
drinks or is hit. Stock poisons are the `Poison_*` classes (`Poison_Venom`,
`Poison_Sting`, `Poison_Hallucinogen`, `Poison_Slumberall`, ...) in the folder
`Abilities/Poisons`; `LIST POISONS` lists them and `HELP [NAME]` describes each.
Source: [Poison.java](../../CoffeeMud/com/planet_ink/coffee_mud/Abilities/Poisons/Poison.java),
[GenPoison.java](../../CoffeeMud/com/planet_ink/coffee_mud/Abilities/Poisons/GenPoison.java).

### How a poison works

- **On a mob** it is an ordinary timed effect. Every *delay* ticks it shows its
  affect message and does its damage (poison-type damage). A poisoned mob that
  is not fighting will attack the poisoner if it can see them.
- **On a food or drink item** it poisons whoever eats or drinks; pouring a
  poisoned drink into another container spreads it.
- **On a weapon** it tries to poison each mob the weapon damages.
- **In a trap, bomb or script** it is applied by ID: `Trap_Needle` takes the
  poison's ID as its text; `MPAFFECT Poison_Venom $n`.
- The victim gets a poison save before the poison takes hold. A save
  always keeps at least 5 percent chance either way (the clamp in
  [Saving throws](#saving-throws)).
- Parameter text on the effect: `RANK=[N]` (a strength number that scales
  generic poisons, default 1), `ITEMAMBIANCE="[TEXT]"` and
  `MOBAMBIANCE="[TEXT]"` (ambience text added to the poisoned item or mob).
- Poisons are cured by `Prayer_RemovePoison`, antidotes and similar.

### GenPoison fields

`CREATE POISON [ID]`, `MODIFY POISON [ID]`, `DESTROY POISON [ID]`
(security flag `CMDABILITIES`). `MODIFY POISON` refuses
`UniPoison`, a `GenPoison` variant that keeps its definition per instance
instead of per ID.

| Field | Editor prompt | Default | Meaning |
| --- | --- | --- | --- |
| `CLASS` | (set by `CREATE`) | | The ID. |
| `NAME` | Poison name | `Poison` | Name shown in lists and healing messages. |
| `TRIGSTR` | Command Words | `GPOISON` | Words for the (rarely used) command. |
| `TICKS` | Duration Formula | `0` | Ticks it lasts. `0` means the standard hostile duration for the poisoner's level. |
| `DELAY` | Delay Formula | `3` | Ticks between its effects (messages and damage). |
| `STARTMSG` | Start Msg | `^G<S-NAME> turn(s) green.^?` | Shown to the room when it takes hold. `<S-NAME>` is the victim. |
| `TARGTELLMSG` | Target Tell | none | If set, told to the victim alone instead of the start message. |
| `AFFECTMSG` | Affect Msg | `<S-NAME> cringe(s) as the poison courses through <S-HIS-HER> blood.` | Shown each delay. |
| `AFFECTTARG` | Affect Target | `true` | Whether it puts a lasting effect on the victim; `false` gives only the messages. |
| `MAKEPEACE` | Force Peace | `false` | After poisoning, ends a fight that the poisoning itself started. Good for poisoned food. |
| `DAMAGE` | Damage Formula | `@x4 + 1?@x1` | Damage each delay; `0` for none. Read left to right: a random number between `@x4 + 1` and `@x1`. |
| `MOOD` | Mood | none | A `Mood` name held while poisoned (`AHELP MOOD`). |
| `EFFECTS` | (a list editor) | none | Coded abilities (`Prop_SpellAdder` syntax) put on the victim along with the poison. See the note below. |
| `DONEMSG` | Done Msg | `The poison runs its course.` | Told to the victim when it ends. |
| `ADDCHANCE` | Addiction (0-100) Formula | `0` | Chance that a drink, food or magic dust poison also makes the victim addicted (adds an `Addictions` effect). |
| `CASTMSG` | Cast Msg | `^F^<FIGHT^><S-NAME> attempt(s) to poison <T-NAMESELF>!^</FIGHT^>^?` | When it is applied by a character. |
| `FAILMSG` | Cast Fail Msg | `<S-NAME> attempt(s) to poison <T-NAMESELF>, but fail(s).` | When applying it fails. |
| `ADJUSTMENTS` | Adjustments | none | Stat changes while poisoned, in `Prop_Adjuster` syntax: `dex-1 savefir-10`. The variables below work in it. |
| `SCRIPT` | Script | none | Functions `ONAFFECT` (it took hold), `ONTICK` (each delay) and `ONCURE` (cured or ran its course). `$n`, `$t` and `$i` are the poisoned mob. |
| `HELP` | Help Text | `<ABILITY>This poison is not yet documented.` | The help page. |

Formula variables for `TICKS`, `DELAY`, `DAMAGE`, `ADDCHANCE` and `ADJUSTMENTS`:
`@x1` the poisoner's level, `@x2` the poisoner's `XLEVEL` expertise, `@x3` the
victim's level, `@x4` the `RANK`. A poison that was not applied by anybody has
all of these at 0 except what the host supplies.

Note: the `EFFECTS` listed in a `GenPoison` are put on the victim along with the
poison, and stay while it lasts. `ADJUSTMENTS` and `MOOD` are still the simpler
way to change stats or mood.

For the poison to appear in play, put it where it is delivered:

```text
MODIFY ITEM a rusty dagger      then edit the Effects field and add:  Poison_Emberbite
```

A poison placed on an item through the editor is permanent: a weapon so
poisoned does not run dry.

## Diseases

A disease is a malicious effect of type DISEASE. It lasts a long time, shows a
periodic message, and may spread to others by contact, nearness, food or other
routes; a save decides whether it takes hold, and a cure removes it. Stock
diseases are the `Disease_*` classes (`Disease_Cold`, `Disease_Flu`,
`Disease_Plague`, `Disease_Lycanthropy`, `Disease_Cancer` ...). `LIST DISEASES`
lists them. Source:
[Disease.java](../../CoffeeMud/com/planet_ink/coffee_mud/Abilities/Diseases/Disease.java),
[GenDisease.java](../../CoffeeMud/com/planet_ink/coffee_mud/Abilities/Diseases/GenDisease.java)
(item P20).

### How a disease spreads and is cured

The *spread* of a disease is a set of ways it passes on (`spreadBitmap`). A
route only runs when the sufferer or the carrying item is involved in it:

| Spread word | Route |
| --- | --- |
| `STD` | Mating socials (`MATE`, `SEX`) between the sufferer and someone. |
| `CONTACT` | Touching: hands or movement actions toward another mob (not by weapons), and handling an infected item or food. |
| `PROXIMITY` | At each of its delays the sufferer may infect a random mob in the room. |
| `CONSUMPTION` | Eating or drinking an infected food or drink. |
| `DAMAGE` | The sufferer hurting someone bare-handed with a natural attack (a bite or claw), now and then. |
| `GET` | Picking up, pushing or pulling an infected non-food item. |
| `HEARING` | The sufferer speaking to someone who can hear them. |
| `INHERITED` | Reserved for inherited conditions. The stock spread code does not use it; scripts and checks may. |

Every catch attempt first rolls against the target's `SAVE_DISEASE`: the chance
to catch is 100 minus the save. A target who saves is immune for a while
(`TemporaryImmunity`). When a disease runs its course the sufferer gains the
same short immunity.

A disease has a *difficulty* from 0 to 9. A cure such as `Prayer_CureDisease` or
`Prayer_MassCureDisease` fails to remove a disease caused by someone else when
`difficulty * 10` is higher than the healer's adjusted level, so difficulty 2
needs a level 20 healer. Diseases also respond to `HELP [NAME]` for their text
and to `INFECT [MOB] [DISEASE]`, the archon skill (an archon must know
`Archon_Infect`).

### GenDisease fields

`CREATE DISEASE [ID]`, `MODIFY DISEASE [ID]`, `DESTROY DISEASE [ID]`.
`CREATE DISEASE` on the ID of a stock disease starts a generic one in its place
from the stock disease's spread, difficulty and malice, and `MODIFY DISEASE`
works only on generic ones. A generic disease counts everywhere a stock one
does: cures, immunities and checks see its type.

| Field | Editor prompt | Default | Meaning |
| --- | --- | --- | --- |
| `CLASS` | (set by `CREATE`) | | The ID. |
| `NAME` | Disease name | `Disease` | |
| `DISPLAY` | Display text | `(Diseased)` | What `AFFECTS` shows. |
| `HELP` | Help Text | `<ABILITY>This disease is not yet documented.` | |
| `TRIGSTR` | (none) | none | Words (unused for players). |
| `TICKS` | Duration Formula (ticks) | `48` | How long it lasts. |
| `DELAY` | Delay Formula (ticks between effects) | `5` | At least 1. |
| `SPREAD` | Spread | none | Comma list of the spread words above. |
| `CATCHCHANCE` | Catch chance (0-100) Formula | `100` | The chance a spread attempt goes ahead at all, before the save. |
| `DIFFICULTY` | Difficulty | `0` | Cure difficulty, 0 to 9 (see above). |
| `STARTMSG` | Start Msg | `^G<S-NAME> come(s) down with a disease.^?` | Shown to the room when caught. |
| `AFFECTMSG` | Affect Msg (each delay) | `<S-NAME> ache(s) and groan(s).` | |
| `DAMAGE` | Damage Formula (each delay) | `0` | Disease damage each delay. |
| `DONEMSG` | Done Msg | `Your disease has run its course.` | |
| `ADJUSTMENTS` | Adjustments | none | `Prop_Adjuster` syntax while it lasts: `con-2`. |
| `EFFECTS` | (a list editor) | none | Coded abilities (`Prop_SpellAdder` syntax) held while it lasts. |
| `IMMUNEMASK` | Immune mask | none | A zapper mask; a mob that *passes* it never catches the disease. `-RACE +Dwarf` passes only dwarves, so only dwarves are immune. |
| `MALICIOUS` | Malicious | `true` | A malicious disease counts as a hostile act when given; a non-malicious one does not. |
| `SCRIPT` | Script | none | Functions `ONAFFECT` (caught: `$n` the giver, `$t` the sufferer), `ONTICK` (each delay: `$n` the giver, `$t` the sufferer) and `ONEXPIRE` (ran its course or was cured: both the sufferer). |

Formula variables for `TICKS`, `DELAY`, `CATCHCHANCE`, `DAMAGE` and
`ADJUSTMENTS`: `@x1` the giver's level, `@x2` the giver's `XLEVEL` expertise,
`@x3` the sufferer's level.

## Traps

A trap is an ability of type TRAP that sits on a room, an exit or door, a
container or an item and *springs* when someone does the thing it watches for:
enters a room, opens or unlocks a door or container, or picks up an item. It
may do damage, cast a spell, run a script or all three. Source:
[StdTrap.java](../../CoffeeMud/com/planet_ink/coffee_mud/Abilities/Traps/StdTrap.java),
[GenTrap.java](../../CoffeeMud/com/planet_ink/coffee_mud/Abilities/Traps/GenTrap.java).

### How traps behave

- **Springing.** A trap that is not yet sprung springs on its trigger. A room
  trap springs when somebody enters (or, for "travel through" traps such as
  pits, when they leave the wrong way); an exit or container trap on `OPEN`, or
  on `UNLOCK` if it is locked; an item trap on `GET`, `PUSH` or `PULL` by
  someone who does not already carry it.
- **Who it hits.** The person who set it, their group, and anyone the setter may
  not fight (the player-kill rules) are spared. A *permanent* trap (one a
  builder put there) also spares mobs whose home is in the same area and
  whoever has property rights in the room. The target then gets a `SAVE_TRAPS`
  save, with the difference between target and trapper level added; a saved
  trap shows its avoid message and does nothing.
- **Damage.** Stock traps compute damage from the trap level, the ability code
  ("bonus"), and the victim, and often an effect or a changed room (a pit
  exit, a cave-in). A `GenTrap` uses its formula, damage type and message type.
- **Reset.** After springing, or after being disarmed, a trap is *disabled*
  and re-arms after its reset time (`PERMRESET` in a `GenTrap`; default 60
  ticks, about four minutes). Bombs are single-use.
- **Temporary traps** (laid by a character) have a lifetime of
  `level * 30` ticks, are not savable, and vanish when they run out.
- **Parameter text.** An effect's text for a trap has the form
  ``` `invoker` "spring message" "damage message" "avoid message" :LEVEL/CODE: extra ```
  where only the parts you need are given. `:12/3:` sets trap level 12 and
  ability code (bonus) 3; `:3:` sets only the code. Quoted strings override the
  three messages in order. For some traps the remaining text carries a spell or
  poison ID (`Trap_EnterSpell` casts `Spell_Fireball` when given it,
  `Trap_Needle` uses a poison's ID).

### Stock trap families

`LIST TRAPS` lists the classes and `HELP [TRAP ID]` explains each. In groups:

| Group | Examples (IDs) | Goes on |
| --- | --- | --- |
| Pits (room) | `Trap_RoomPit`, `Trap_SpikePit`, `Trap_AcidPit`, `Trap_DeepPit`, `Trap_SnakePit`, `Trap_RatPit` and the "Thru" forms | rooms |
| Entry traps | `Trap_EnterBlade`, `Trap_EnterGas`, `Trap_EnterNeedle`, `Trap_EnterPit`, `Trap_EnterSpell`, `Trap_Enter` (random) | rooms, exits |
| Room hazards | `Trap_Avalanche` (mountains), `Trap_Boulders`, `Trap_CaveIn`, `Trap_CrushingRoom`, `Trap_FloodRoom`, `Trap_Darkfall`, `Trap_BearTrap`, `Trap_Snare`, `Trap_Tripline`, `Trap_MonsterCage` | rooms |
| Open / unlock traps | `Trap_OpenBlade`, `Trap_OpenGas`, `Trap_OpenNeedle`, `Trap_OpenSpell`, `Trap_Open` and the `Trap_Unlock*` forms | containers, doors |
| Get traps | `Trap_GetBlade`, `Trap_GetGas`, `Trap_GetNeedle`, `Trap_GetSpell`, `Trap_Get`, `Trap_Gluey`, `Trap_Greasy`, `Trap_Ignition`, `Trap_Infected`, `Trap_Vanishing` | items |
| Door and chest specials | `Trap_Needle`, `Trap_AcidSpray`, `Trap_ElectricShock`, `Trap_Launcher`, `Trap_Noise`, `Trap_PoisonGas`, `Trap_SporeTrap` | containers, doors |
| Bombs | `Bomb_Explosive`, `Bomb_FlameBurst`, `Bomb_Heat`, `Bomb_Smoke`, `Bomb_Sonic`, `Bomb_Poison`, `Bomb_SpellBlast`, ... | an item the maker holds |

`Trap_Trap` is a legacy base; do not place it.

### Placing traps

**As a builder.** Add the trap ID in the *Effects* field of the room, exit,
container or item, with parameters if wanted (`:15/0:`):

```text
MODIFY ROOM              Effects: Trap_RoomPit      parameters: :5/0:
MODIFY ITEM an oak chest Effects: Trap_Needle       parameters: :8/0: Poison_Venom
MODIFY ROOM              Effects: Trap_EnterSpell   parameters: Spell_Fireball
```

The first is a level 5 pit; the second a needle in a chest lid that delivers
the named poison (`Trap_Needle` reads a poison ID; other needle and gas traps
use the plain `Poison`); the third casts a spell, written `SPELL ID` or
`SPELL ID;words for it` for the traps whose names end in `Spell`.

It is a permanent trap: it re-arms after its reset, does not time out and is
written with the object. Traps whose `canSetTrapOn` rules say a thing is
unsuitable (a pit in a non-room, a door trap on an item with no lid) are
refused when a character lays them; the builder's editor does not check, so
keep to the "Goes on" column.

**As a thief.** `TRAP [ITEM|DIRECTION|ROOM] [TRAP NAME]` (`TRAP LIST` lists
what the character can lay), `DETRAP [TARGET]` and `CHECK [TARGET]` to find
or disarm, `BOMB [ITEM] [BOMB NAME]` to make a bomb. The traps a thief may lay
come from the recipe file `resources/skills/traps.txt` (tab separated: name,
level, a blank, trap ID, an extra text for the trap, and optionally the three
messages), plus `mtraps.txt` for the minor-trapping skill. Adding a line
there with the ID of a `GenTrap` lets thieves lay your custom trap.
`MTRAP` lays minor traps. `Ranger_SetSnare` (`SETSNARE`), `Thief_Caltrops`, wards (`Spell_WardArea`,
`Prayer_*WardingGlyph`) and plant traps (`Chant_PlantTrap`) are other setters.

### GenTrap fields

`CREATE TRAP [ID]`, `MODIFY TRAP [ID]`, `DESTROY TRAP [ID]`. (The shipped
`GENTRAP` help mentions `CREATE ABILITY GenTrap`; the commands above are the
ones that exist.)

| Field | Editor prompt | Default | Meaning |
| --- | --- | --- | --- |
| `CLASS` | (set by `CREATE`) | | The ID. |
| `NAME` | Trap name | `an ability` | |
| `BASELEVEL` | Level | `0` | The trap level of every new copy. Characters need at least this level to lay it, and damage formulas read it as `@x1`. |
| `LEVEL` | (none) | the base level | The level of one copy; `:LEVEL/CODE:` in a copy's parameter text sets it. |
| `ACODE` | (none) | `0` | The default ability code (bonus). |
| `ISBOMB` | Is Bomb | `false` | When `true` the trap is a bomb (`isABomb()`), as the stock bombs are. |
| `CANAFFECTMASK` | Can Affect | `ITEMS` | What it can sit on: `ITEMS`, `EXITS`, `ROOMS`. A container needs `ITEMS`. |
| `CANTARGETMASK` | Can Target | `0` | |
| `PERMRESET` | Reset Ticks | `60` | Ticks until a sprung or disarmed trap re-arms. |
| `TRIGGERS` | Triggers | none | What springs it instead of the class's own triggers (below). |
| `DISARM` | Disarm | none | A typed phrase that disarms it: `verb:cut red wire`. |
| `ONCE` | Once | `false` | After springing once, it is removed for good. |
| `ACOMP` | Components | `(inventory:consumed:1:METAL:)` | What a thief needs to lay it (components syntax). |
| `AVOIDMSG` | Avoid Msg | `<S-NAME> avoid(s) setting off a trap!` | When the target saves. |
| `TRIGMSG` | Spring Msg | `<S-NAME> set(s) off a trap!` | When it springs. |
| `DAMMSG` | Damage Msg | `The trap <DAMAGE> <T-NAME>!` | The damage message. |
| `DMGF` | Damage Formula | `((@x1+@x2)*(1?6))+1` | Blank for no damage. `@x1` trap level, `@x2` ability code, `@x3` the setter's level, `@x4` the victim's level. |
| `DMGT` | Damage Type | `BURSTING` | A weapon damage type: `NATURAL SLASHING PIERCING BASHING BURNING BURSTING SHOOTING FROSTING GASSING MELTING STRIKING LASERING SONICING DISRUPTING STULTIFYING CORRUPTING SCRAPING`. |
| `DMGM` | Dmg Msg Type | `FIRE` | The message kind of the damage (a [saving throw](#saving-throws) kind: `FIRE`, `ACID`, `GAS` ...). |
| `ABILITY` | Ability ID | none | An ability invoked on the victim when it springs. |
| `ABILTXT` | Ability Parms | none | Words given to it (`words/text`). |
| `ABILTIK` | A.Tick Override | `0` | Caps the ticks of the effect it leaves. |
| `SCRIPT` | Scriptable Parm | none | `ACT_PROG [TRAP ID]` runs when it springs (`$t` the one who sprang it). Functions `ONTRIGGER` (`$n` the victim) and `ONDISARM` (`$n` whoever disarmed it, if known). |
| `HELP` | Help Text | `<ABILITY>This trap is not yet documented.` | |

`TRIGGERS` is a comma list of message types: `SIT`, `READ`, `LOOK`, `EXAMINE`,
`OPEN`, `CLOSE`, `WEAR`, `HOLD`, `PUT`, `DROP`, `GET` ... (any message type
word). On an item it springs when somebody does that to the item; on a room
the types `ENTER` and `LEAVE:[DIRECTION]` and others apply to anyone in it.
`ONCE` and `DISARM` work with or without damage, so a `GenTrap` with an empty
`DMGF` and an empty `ABILITY` is a scripted tripwire.

## Other generic ability types

These have their own editors and chapters; they are listed here so you know
what the `CREATE`/`MODIFY`/`DESTROY` words are.

| Verb | Makes | Where it is explained |
| --- | --- | --- |
| `LANGUAGE` | a `GenLanguage` (words, hashed words, natural flag, interpreted languages) | `AHELP LANGUAGE_AUTHORING`, [11-presentation-language.md](11-presentation-language.md) |
| `CRAFTSKILL`, `WRIGHTSKILL` | crafting skills (a recipe file, materials, verbs, stations) | [07-items-crafting.md](07-items-crafting.md), `AHELP GENCRAFTSKILL` |
| `GATHERSKILL` | a gathering skill (mining, fishing, hunting) | [07-items-crafting.md](07-items-crafting.md), `AHELP GENGATHERSKILL` |
| `BUILDSKILL` | a building skill (`AHELP GENBUILDSKILL`) | [07-items-crafting.md](07-items-crafting.md) |
| `COMPONENT` | a line of `components.txt` | `AHELP COMPONENTS` |
| `EXPERTISE` | a line of `expertises.txt` | `AHELP EXPERTISES`, [03-characters.md](03-characters.md) |
| `ALLQUALIFY` | a shared qualification entry | [How characters get abilities](#class-race-and-the-shared-pools) |

Crafting, gathering and tweak records also accept `ACTIONCOST`,
`COMBATACTIONCOST` and `COMPONENTS`; crafting, gathering and poison records
accept a `SCRIPT` with the hooks listed under `AHELP SKILLSCRIPTS`
(`ONCRAFTED`, `ONFAILED`, `ONFOUND`).

*Planar abilities* (`StdPlanarAbility`) define planes of existence and are
covered in [30-travel-vehicles-instances.md](30-travel-vehicles-instances.md).
*Composed skills* are a separate optional system in
[15-ability-composition.md](15-ability-composition.md).

**Developer note.** `ThinAbility` is the light base class for properties
(no cost, cooldown or casting); `ExtAbility` (`ExtendableAbility`) is a
shell whose name, stat adjustments, message listener and tick are supplied as
Java callbacks, used by the engine for short-lived effects (a mob eating
another, a craft broker). Writing a new Java ability is covered in
[32-engine-programming.md](32-engine-programming.md).

## The stock ability catalogue

CoffeeSUD ships about 1,100 abilities, inherited from CoffeeMud. This
handbook does not list them; the game does, and the lists always match the
loaded classes.

| Family | Folder | Count (Java classes, about) | How to list | Help file |
| --- | --- | --- | --- | --- |
| Spells | `Spells` | 420 | `LIST SPELLS` | `spell_help.ini` |
| Prayers | `Prayers` | 365 | `LIST PRAYERS` | `prayer_help.ini` |
| Chants (druid) | `Druid` | 315 | `LIST CHANTS` | `chant_help.ini` |
| Songs, plays, dances | `Songs` | 115 | `LIST SONGS` | `songs_help.ini` |
| Skills (generic) | `Skills` | 190 | `LIST SKILLS` | `skill_help.ini` |
| Fighter, paladin, ranger skills | `Fighter`, `Paladin`, `Ranger` | 205 | `LIST SKILLS` | `skill_help.ini` |
| Weapon and armor training | `Specializations` | 46 | `LIST SKILLS` | `skill_help.ini` |
| Archon skills | `Archon` | 18 | `LIST SKILLS ARCHON` | `arc_misc.ini` |
| Thief skills | `Thief` | 195 | `LIST THIEFSKILLS` | `skill_help.ini` |
| Common skills (crafting, gathering, services) | `Common` | 170 | `LIST COMMON` | `common_help.ini` |
| Languages | `Languages` | 59 | `LIST LANGUAGES` | built in |
| Diseases | `Diseases` | 70 | `LIST DISEASES` | `diseases.ini` |
| Poisons | `Poisons` | 25 | `LIST POISONS` | `poisons.ini` |
| Traps and bombs | `Traps` | 80 | `LIST TRAPS` | `traps.ini` |
| Properties | `Properties` | 240 | `LIST PROPERTIES` | `arc_properties.ini`; reference: [23-properties-reference.md](23-properties-reference.md) |
| Super powers | `SuperPowers` | 5 | `LIST SUPERPOWERS` | `skill_help.ini` |
| Racial and condition effects | `Misc` | 80 | `LIST ABILITIES` | various |

Practical ways to find what you need:

- `LIST ABILITIES [DOMAIN|FLAG]` with a domain word narrows by what it does:
  `LIST ABILITIES healing`, `LIST ABILITIES warding`, `LIST ABILITIES moving`
  (flag), `LIST ABILITIES summoning`.
- `HELP [NAME]` prints the usage, example and who gets it at what level; the
  help text of the flags like `ADJUSTER` (the stat changers) helps when
  looking for a building block.
- Look for property-like building blocks by IDs: `Prop_` for properties;
  `Spell_Fly`, `Spell_Invisibility`, `Spell_Haste`, `Prayer_Bless`,
  `Prayer_Sanctuary`, `Chant_Barkskin`, `Spell_Slow` and `Spell_Deafness` are
  commonly reused as `POSTCASTAFFECT` and `POSTCASTABILITY` parts.
- To see how a stock ability does something, read its Java: the folder names above
  are under `CoffeeMud/com/planet_ink/coffee_mud/Abilities/`. The ones named
  `Std*` are the base classes.

Examples worth knowing by ID: `Spell_MagicMissile`, `Spell_Fireball`,
`Spell_Lightning`, `Spell_Sleep`, `Spell_Shield`, `Spell_MageArmor`,
`Spell_Light`, `Spell_SummonMonster`, `Spell_ReadMagic`; `Prayer_Bless`,
`Prayer_CureLight`, `Prayer_CureDisease`, `Prayer_RemovePoison`; `Chant_Barkskin`;
`Song_Detection`, `Play_Battlehymn`, `Dance_Ballet`; `Skill_Recall`,
`Skill_Dodge`, `Skill_Bash`, `Skill_Trip`, `Skill_Write`, `Skill_Climb`,
`Skill_Swim`; `Thief_Steal`, `Thief_Sneak`, `Thief_Hide`, `Thief_Trap`,
`Thief_Poison`; `Fighter_Kick`, `Fighter_Berzerk`; `Disease_Cold`,
`Disease_Flu`, `Disease_Plague`; `Poison_Venom`, `Poison_Sting`;
`Trap_BearTrap`, `Trap_RoomPit`, `Bomb_Explosive`.

## For engine developers

The contract is the
[Ability](../../CoffeeMud/com/planet_ink/coffee_mud/Abilities/interfaces/Ability.java)
interface; [StdAbility](../../CoffeeMud/com/planet_ink/coffee_mud/Abilities/StdAbility.java)
implements most of it. The methods that matter when writing a new Java ability
(full treatment in [32-engine-programming.md](32-engine-programming.md)):

| Method | Purpose |
| --- | --- |
| `ID()`, `name()`, `displayText()`, `triggerStrings()` | Identity and invoke words. `ID()` must equal the class name. |
| `classificationCode()` | `ACODE_* \| DOMAIN_*`. |
| `abstractQuality()`, `enchantQuality()`, `castingQuality(mob, target)` | What the ability is, and what it is *now* against this target (used by NPC AI). |
| `canAffectCode()`, `canTargetCode()` (protected) | The `CAN_*` masks. |
| `usageType()`, `overrideMana()`, `usageCost(mob, ignoreClassOverride)` | What it costs. |
| `castingTime`, `combatCastingTime`, `checkedCastingCost` | Action time. |
| `getTicksBetweenCasts()` | Cooldown. |
| `flags()`, `minRange()`, `maxRange()` | Flags and combat range. |
| `invoke(mob, commands, target, auto, asLevel)` | The act. Call `super.invoke` first for costs. `auto` skips costs and proficiency. `asLevel > 0` forces the cast level. |
| `preInvoke(...)` | For abilities that take time (common skills): a chance to veto before each action. |
| `startTickDown(invoker, affected, ticks)` | Put the copy on `affected` for `ticks` (called by `beneficialAffect` and `maliciousAffect`, which also set the invoker and pick the standard duration). |
| `tick(ticking, tickID)`, `unInvoke()`, `canBeUninvoked()`, `makeNonUninvokable()`, `makeLongLasting()` | Effect life cycle. |
| `affectPhyStats`, `affectCharStats`, `affectCharState` | Stat changes, recalculated whenever the host's stats are. |
| `okMessage`, `executeMsg` | Message flow ([32-engine-programming.md](32-engine-programming.md)). |
| `autoInvocation(mob, force)`, `isAutoInvoked()`, `isNowAnAutoEffect()` | Passive abilities. |
| `canBeTaughtBy`, `canBePracticedBy`, `canBeLearnedBy`, `teach`, `practice`, `unlearn`, `requirements` | Teaching rules. |
| `proficiencyCheck(mob, adjustment, auto)`, `helpProficiency(mob, adjustment)` | The roll and the improvement chance. |
| `adjustedLevel(mob, asLevel)` | The level to scale by. |
| `getStatCodes()`, `getStat`, `setStat`, `isGeneric()` | Data-driven abilities; `getSaveStatIndex()` says where extra saved stats start. |
| `catchUp(host, ticks)` | Optional: what to do at once when the host's area wakes after sleeping (item N8). |

Rules of thumb: set the invoker before an effect can do damage; use
`getTarget` and `getAnyTarget` to resolve targets (they print the standard
refusals); do not keep per-instance data in static fields unless it is
keyed like the `Gen*` classes do; put saved state in `text()` or in
declared stats so the campaign save (below) can restore it. A new class under
`Abilities/` is picked up by the class loader like any other.

## Worked examples

The examples are complete in the sense that each can be typed in as written.
They assume a builder or archon with the `CMDABILITIES` flag. Adapt the IDs.

### A custom attack spell: Cinderlash

A hostile fire spell, instant (no lingering effect), with a fire save, damage
that scales with the caster, a small chance to slow the victim, and a
cooldown.

```text
CREATE ABILITY Spell_Cinderlash
```

Set the fields (menu number in brackets):

| Prompt (number) | Value |
| --- | --- |
| Ability/Skill name (1) | `Cinderlash` |
| Type, Domain (2) | `SPELL,EVOCATION` |
| Command Words (3) | `CAST,CA,C` |
| Maximum Range (5) | `LONG` |
| Ticks Between Casts (6) | `2` |
| Skill Flags (10) | `BURNING` |
| Override Cost (11) | `30` |
| Cost Type (12) | `MANA` |
| Can Affect (14) | `0` |
| Can Target (16) | `MOBS` |
| Quality Code (17) | `MALICIOUS` |
| Fizzle Message (33) | `^S<S-NAME> flick(s) a whip of cinders at <T-NAMESELF>, but it falls to ash.^?` |
| Normal-Cast Message (35) | `^S<S-NAME> lash(es) <T-NAMESELF> with a whip of glowing cinders!^?` |
| Post-Cast Message (36) | `The cinders <DAMAGE> <T-NAME>!` |
| Attack-Type (38) | `FIRE` |
| Extra castings (41) | `25%;Spell_Slow` |
| Damage/Healing Formula (42) | `(2?6)+(@x9/2)` |
| Help Text (43) | `<ABILITY>Usage   : CAST CINDERLASH [TARGET]` |

How it plays: the caster pays 30 mana and waits two ticks before another
cast; a proficiency roll may fail; on success the target rolls two saves (one against the cast itself, one against
the fire message, each with the spell save and the level difference,
[Saving throws](#saving-throws)).
A target that resists either one takes half damage and is not slowed; one that
resists neither takes it all and has a one in four chance of being slowed by
`Spell_Slow` (which makes its own roll).
The damage formula is a random 2 to 6 plus half the adjusted caster level,
with the parentheses doing the work because evaluation is left to right.

Let characters learn it. For a quick test give it to every class from level 1
(`CREATE ALLQUALIFY EACH Spell_Cinderlash`, answer the prompts for level 1,
not auto-gained, default proficiency 25), set a teacher in a test room with the
`MOBTeacher` behavior and the parameter `Spell_Cinderlash`, and from the
character:

```text
say teach me cinderlash
practice cinderlash [TEACHER]
cast cinderlash goblin
```

For the real game, instead list `Spell_Cinderlash` in the class's ability map
([03-characters.md](03-characters.md)). Check the help page with `HELP
CINDERLASH`; it shows the generated header.

### A timed buff handed out by a script: Emberward

A protective buff that an NPC priest grants to anyone who asks, lasting 120
ticks (about eight minutes).

```text
CREATE ABILITY Spell_Emberward
```

| Prompt | Value |
| --- | --- |
| Ability/Skill name | `Emberward` |
| Type, Domain | `SPELL,ABJURATION` |
| Affect String | `(Emberward)` |
| Skill Flags | `RESISTER` |
| Can Affect / Can Target | `MOBS` / `MOBS` |
| Quality Code | `BENEFICIAL_OTHERS` |
| Duration Override | `120` |
| Affect Adjustments | `savefir+30 saveaci+15` |
| Auto-Cast Message | `^S<T-NAME> <T-IS-ARE> wreathed in a faint, warm glow.^?` |
| Uninvoke Message | `The warm glow around you fades.` |

Attach this to the priest (`MODIFY MOB`, Behaviors, `Scriptable`, parameter
text; or `LOAD=` a file, see [04-mobprog.md](04-mobprog.md)):

```text
SPEECH_PROG p ember ward
if affected($n Spell_Emberward)
 say You already bear the ward, $n.
else
 say Stand still, $n.
 mpaffect Spell_Emberward $n 120TICKS
endif
~
```

`mpaffect` casts it as an automatic cast, so the priest needs no mana and the
roll cannot fail; `120TICKS` sets how long it lasts (the number and the word are written together) (the 120 in the
ability's own `Duration Override` is the default when a caster uses it). A
player who says "ember ward" gets the glow, sees `(Emberward)` with its time left
in `AFFECTS`, and takes the extra fire and acid resistance until it fades. To
let players cast it themselves later, list it in a class; the same definition
works. If you need the buff to end early, `MPUNAFFECT $n Spell_Emberward`.

To react to the end of the buff, add to the ability's own script:

```text
FUNCTION_PROG ONEXPIRE
mpecho The ember glow around $t gutters out.
~
```

### A custom poison: Emberbite

```text
CREATE POISON Poison_Emberbite
```

| Prompt | Value |
| --- | --- |
| Poison name | `Emberbite` |
| Duration Formula | `30` |
| Delay Formula | `2` |
| Start Msg | `^G<S-NAME> wince(s) as embers seem to crawl under <S-HIS-HER> skin!^?` |
| Affect Msg | `<S-NAME> flinch(es) as the embers burn.` |
| Force Peace | `true` |
| Damage Formula | `(@x1/6)+(1?4)` |
| Done Msg | `The burning in your veins fades.` |
| Adjustments | `dex-1 savefir-10` |
| Script | `FUNCTION_PROG ONCURE` / `mpecho The embers in $i's blood die down.` / `~` |

(`@x1` is the poisoner's level.) Put it on a weapon or a drink:

```text
MODIFY ITEM rusty dagger     Effects: Poison_Emberbite       parameters: RANK=2
MODIFY ITEM flask of cider   Effects: Poison_Emberbite
```

Hitting a mob with the dagger, or drinking the flask, now rolls a poison save; on
failure the victim burns for 30 ticks, taking damage every second tick, with
lowered dexterity and fire resistance, until it runs out, `Prayer_RemovePoison`
cures it or `MPUNAFFECT $n Poison_Emberbite` ends it. With `Force Peace` set
the poisoned flask does not start a fight with the drinker. Use `MPAFFECT
Poison_Emberbite $n` from a script to poison directly.

### A spreading disease: Ash Lung

```text
CREATE DISEASE Disease_AshLung
```

| Prompt | Value |
| --- | --- |
| Disease name | `Ash Lung` |
| Display text | `(Ash Lung)` |
| Duration Formula (ticks) | `100` |
| Delay Formula | `5` |
| Spread | `PROXIMITY, CONTACT` |
| Catch chance | `100` |
| Difficulty | `2` |
| Affect Msg | `<S-NAME> cough(s) up grey ash.` |
| Damage Formula | `1?4` |
| Adjustments | `con-2` |
| Immune mask | `-RACE +Dwarf` |
| Script | `FUNCTION_PROG ONEXPIRE` / `mpecho The rattle leaves $t's chest.` / `~` |

A mob that catches it loses 2 constitution, coughs ash every fifth tick for 1 to 4
damage, and, because of `PROXIMITY`, may pass it to someone in the room at each
cough (after their disease save). Dwarves never catch it. A priest needs a
level of at least 20 to cure it (difficulty 2 times 10). Give it to something to
carry: a monster that exhales it,

```text
FIGHT_PROG 15
mpecho $i exhales a cloud of grey ash over $n!
mpcast Disease_AshLung $n
~
```

or put `Disease_AshLung` in the Effects of a food item to make it a plague
meal (add `CONSUMPTION` to the `Spread` first).

### A scripted tripwire: Trap_AshChest

```text
CREATE TRAP Trap_AshChest
```

| Prompt | Value |
| --- | --- |
| Trap name | `ash-chest trap` |
| Level | `3` |
| Can Affect | `ITEMS` |
| Reset Ticks | `120` |
| Triggers | `OPEN` |
| Disarm | `verb:cut red wire` |
| Spring Msg | `<S-NAME> open(s) the lid and set(s) off a trap!` |
| Damage Msg | `A gout of cinders <DAMAGE> <T-NAME>!` |
| Damage Formula | `(@x1*4)+(1?6)` |
| Damage Type | `BURNING` |
| Dmg Msg Type | `FIRE` |
| Scriptable Parm | see below |

```text
FUNCTION_PROG ONTRIGGER
mpecho Cinders roar out of the chest at $n!
~
FUNCTION_PROG ONDISARM
mpecho The red wire parts with a soft click.
~
```

Place it: `MODIFY ITEM the oak chest` (it must be a container with a lid), add
`Trap_AshChest` to its Effects. Opening the chest springs it (a `SAVE_TRAPS`
save can avoid it, and builders' own area mobs are exempt); typing `cut red
wire` first in the same room disarms it and the script reports it. After the reset
ticks it re-arms. Add a line for it in `resources/skills/traps.txt` if thieves
should be able to lay it themselves.

### An area buff for a party: Marching Cadence

A `GenAbility` of type SONG (command words `PLAY`, quality `BENEFICIAL_OTHERS`)
with `Target mode` `ALLALLIES`, `Can Affect` and `Can Target` `MOBS`, `Affect
Adjustments` `movement+50`, `Duration Override` `40` and `Area message`
`<S-NAME> strike(s) up a marching cadence!`. The caster pays once; every member of
the group in the room gets the effect with their own cast message. Compare
`ALLENEMIES` for Thunderclap (damage `(2?6)+(@x9/2)`, `Public effects`
`Spell_Deafness`) and a summon for "Call the Pack": `Summon` `a grey wolf` (a
catalog mob), `Summon count` `1+(@x1/10)`, `Duration Override` `20`, `Summon
flags` `FOLLOW ASSIST DISMISSONEXPIRE`.

### A reusable haunting property: Prop_Haunted

A property with a script, set per room by text.

```text
CREATE ABILITY Prop_Haunted
```

Set `Type, Domain` to `PROPERTY`, `Can Affect` to `ROOMS`, and the script:

```text
RAND_PROG 10
if effectparm(LOUD == yes)
 mpecho Something moans behind the walls!
else
 mpecho A cold draft slips through the room.
endif
~
```

Then in each room's Effects add `Prop_Haunted` with parameters `LOUD=yes`, or
`LOUD=no`. One definition, many settings. Test with the plain attachment (no
casting), then save and restart ([Persistence](#persistence)).

## Persistence

| What | Where it is kept | On reload or restart |
| --- | --- | --- |
| A custom ability's **definition** (`GenAbility`, `GenPoison`, `GenDisease`, `GenTrap`, `GenTweakAbility`, ...) | The content database, and in builder mode the files `world/custom/abilities/*.cmare` (+ `.script`) | Loaded with the engine's classes at boot, before areas. Hand edits to the files are rebuilt at the next start. In a normal game the files are the content and the database is a cache of them ([02-world-workflow.md](02-world-workflow.md)). |
| **Known abilities and proficiency** of a character | The character's record in the campaign | Restored with the character. An ability whose definition no longer exists cannot be restored. |
| A **permanent effect** on a room, exit, item or mob (properties, traps, permanent spells) | In the thing's record: ID plus parameter text (`<AFF><ACLASS>..</ACLASS><ATEXT>..</ATEXT></AFF>`) | Re-created from its ID and text. Any state not in `text()` (a trap's sprung state, a GenAbility's cooldown) is lost. |
| A **timed effect** (a cast spell, a poison, a disease, a laid trap, a crop in progress) | Not in the world files. The *campaign save* records every effect with ticks left on the player, mobs, items, rooms, exits and areas: its ID, text, ticks left, proficiency, who caused it, and the activity of a common skill | Put back with its remaining ticks by the save's loader; the cause is the character if they made it, else a nearby mob of that name, else the host. Effects that do not keep their state in `text()` come back with defaults. |
| **Cooldowns** | On the ability object the character holds, by game time | Not guaranteed across a restart for custom abilities; do not rely on them. |
| **Summons** with `NOSAVE` | Not saved | Gone after a save and restore; others follow with the party. |

Developers: effects decide their own persistence through `isSavable()`;
`startTickDown` turns it off for cast effects and `makeNonUninvokable` turns it
on. The campaign save also stores effects with `TICKDOWN > 0` that
`canBeUninvoked()` and are not `isAutoInvoked()` and not item tickers (see
[33-engine-internals.md](33-engine-internals.md), `WorldState`).
Round-trip anything an effect needs through `text()` or `setStat`/`getStat`.

## Troubleshooting and gotchas

- **"A generic ability with the ID 'X' already exists!"** `CREATE` refuses an ID
  that is already a generic of any kind. `MODIFY` it, or destroy it first.
- **"'X' is an invalid id, because it contains a space."** IDs have no spaces.
- **The wrong `MODIFY` verb.** `MODIFY ABILITY` tells you the right one:
  `MODIFY TRAP`, `POISON`, `DISEASE`, `TWEAK`, `LANGUAGE`, `CRAFTSKILL`,
  `GATHERSKILL`, `BUILDSKILL`.
- **A custom spell is not castable by anyone.** Creating the ability does not
  qualify any class. Add it to a class's ability map, `allqualifylist.txt`, a
  teacher's `MOBTeacher` list, or hand it out with `MPENABLE`. Check `QUALIFY`
  and `SKILLTREE`. If the level of the character is below the ability's
  qualifying level it is not learnable.
- **`cast xyz` says nothing.** The trigger words and name must match what the
  character types; `NAME` is what follows `CAST`, and `TRIGSTR` for a spell must
  contain `CAST`. Another ability with the same first letters may be chosen
  first; type more of the name.
- **Casting an instant attack leaves a lasting effect or none.** `Can Affect`
  `0` makes it instant; any other value puts a copy on the target for the
  duration. Do not give an instant attack a non-zero `Can Affect` just to get a
  message.
- **`TICKAFFECTS` and damage.** In a `GenAbility` that leaves an effect
  (`Can Affect` not `0`), the effect re-runs its post-cast step on every tick,
  so a damage or healing formula is applied each tick for the length of the
  effect, and its script's `ONCAST` is called again. `TICKAFFECTS` controls
  whether the post-cast *message* repeats too. Read the code path before
  relying on the exact repetition rules for a damage-over-time ability, and
  test it with `AFFECTS`; use `Can Affect` `0` and a separate effect through
  `Public effects` when you want exactly one hit plus a timed penalty.
- **`ROOMS` and `EXITS` targets.** With `Can Target` `ROOMS` the effect of an
  ordinary cast lands on the caster, because the target is the caster; it
  lands on a room only when a script, item or trap casts it automatically at
  the room. `EXITS` only checks that the named exit exists. For room effects
  prefer a property (a `GenAbility` of type `PROPERTY`) put on the room.
- **Messages blank or odd after hand-editing a record.** A missing tag reads as
  an empty value ([The saved record](#the-saved-record)).
- **Percent signs in `MOBPROG`.** Anything inside a script ability is subject to
  the same quoting rules as any script ([27-mobprog-commands.md](27-mobprog-commands.md)).
- **A formula gives an odd number.** There is no operator precedence; add
  parentheses. `?` is a random number between its two sides, not a dice roll
  (use `(1?6)+(1?6)` for 2d6).
- **Poison or disease does nothing.** Check the save (a strong target or a
  racial immunity), the disease's `IMMUNEMASK`, `TICKS` and `DELAY` (0 delay is
  forced to 1), and that the effect is on the target (`AFFECT [TARGET]` as an
  archon). `Poison` `Affect Target` must be `true` for a lasting effect.
- **A trap that never triggers.** `Can Affect` must include the kind of host
  (`ITEMS` for a chest as well as a lid; `ROOMS` for a room), `TRIGGERS` must
  name the message kind, and mobs whose home is in the same area are exempt
  from a *permanent* trap, so test with a player or a mob from another area.
- **A trap or a poison on an item never runs out.** Builder-placed effects are
  permanent. Use a script and `MPAFFECT [ID] [TARGET] [N]TICKS` for a temporary one.
- **A summon is left behind.** Use `DISMISSONEXPIRE` and `BIND`, give the
  ability a duration (`TICKSOVERRIDE`), and test death of the summoner.
- **Changing a definition changes every copy.** `MODIFY ABILITY` edits the one
  shared definition. Effects already on things pick up the new fields at once
  (stats are recalculated); text already told to players is not changed.
- **Stock `Gen*` help shows an old file name.** The shipped `GENABILITY` help
  still says custom abilities live in `world/custom.cmare`; they are now one
  file each under `world/custom/abilities/`.

## See also

- [03-characters.md](03-characters.md): classes, races, ability maps,
  expertise, training and respec.
- [04-mobprog.md](04-mobprog.md), [26-mobprog-triggers.md](26-mobprog-triggers.md),
  [27-mobprog-commands.md](27-mobprog-commands.md) and
  [28-mobprog-functions.md](28-mobprog-functions.md): scripts, including the
  `SKILL_PROG` trigger and `MPAFFECT`, `MPCAST`, `MPENABLE`.
- [05-javascript.md](05-javascript.md): JS-defined classes and hooks.
- [15-ability-composition.md](15-ability-composition.md): the optional composed
  skills.
- [16-combat-profiles.md](16-combat-profiles.md) and
  [29-combat-and-encounters.md](29-combat-and-encounters.md): damage, saves,
  action time and encounters.
- [21-mobs.md](21-mobs.md), [22-items.md](22-items.md),
  [20-areas-rooms-exits.md](20-areas-rooms-exits.md): giving mobs, items and
  rooms abilities and effects.
- [23-properties-reference.md](23-properties-reference.md): every property.
- [24-behaviors-and-masks.md](24-behaviors-and-masks.md): `MOBTeacher` and the
  zapper mask language.
- [02-world-workflow.md](02-world-workflow.md),
  [state-and-persistence.md](state-and-persistence.md): where definitions and
  saves live.
- [tool-reference.md](tool-reference.md): generated help, including `GENABILITY`,
  `GENDISEASE`, `GENTWEAK`, `GENTRAP`, `SKILLSCRIPTS` and the commands above.
- [32-engine-programming.md](32-engine-programming.md): writing Java abilities.
