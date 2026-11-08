# Physical, character and state stats

> Part of the CoffeeMud NEWOVERVIEW. Bucket: `b009` (Physical, character and state stats) · 9 files · 5221 lines of Java.

## Summary

Every Physical (mob, item, exit, room) carries a physical-stat block (`PhyStats`: level, weight, armor, damage, speed, sense and disposition flags, free-text ambiance tags). A character adds a character-stat block (`CharStats`: six base attributes with maximum adjustments, 26 save codes, age, gender, class list, race, body-part counts, deity, proficiency filters) and a vitals block (`CharState`: hit points, mana, movement, hunger, thirst, fatigue). Each block is kept as a base copy and a working copy that is rebuilt from the base plus every active effect on each recalculation (vitals add a max copy), so effects are layered on top of the base rather than written into it. Saves are the main combat hook: one percentage is both the chance of a reduction and its size. Hunger, thirst and fatigue turn elapsed ticks and body weight into slow pressure on the character, and tattoos are the engine's generic named, expiring flags, reused for achievements, location markers and scars.

## Key files and types

Paths are relative to the CoffeeMUD folder. The table gives the full path, and prose repeats the same path, for example `com/planet_ink/coffee_mud/Libraries/MUDFight.java:3426`.

| File | Role | Lines |
|---|---|---|
| `com/planet_ink/coffee_mud/Common/interfaces/PhyStats.java` | Interface for physical stats; `STAT_`, `CAN_`, `SENSE_` and `IS_` constants; `Ambiance` enum | 694 |
| `com/planet_ink/coffee_mud/Common/DefaultPhyStats.java` | Default `PhyStats`: int array of ten stats, plus speed, replacement name and a sorted ambiance array | 516 |
| `com/planet_ink/coffee_mud/Common/interfaces/CharState.java` | Interface for vitals, plus the timing constants (fatigue, death, annoyance) | 266 |
| `com/planet_ink/coffee_mud/Common/DefaultCharState.java` | Default vitals: bounded adjusters, weight-scaled maxima, fractional hunger and thirst carry | 482 |
| `com/planet_ink/coffee_mud/Common/interfaces/CharStats.java` | Interface; 48 stat codes; `DefCharStat` table; `CODES` registry that boot-time ini rows can extend | 1540 |
| `com/planet_ink/coffee_mud/Common/DefaultCharStats.java` | Default `CharStats`: `short[]` stats, class list, race, body parts, gender, deity, saves, age, proficiency filters | 1299 |
| `com/planet_ink/coffee_mud/Common/interfaces/Tattoo.java` | Named flag with a tick-down | 73 |
| `com/planet_ink/coffee_mud/Common/DefaultTattoo.java` | Tattoo with an absolute expiry timestamp and an `N NAME` string form | 193 |
| `com/planet_ink/coffee_mud/Common/DefaultTattooable.java` | Tattoo container; not referenced by any other file (see Problems) | 158 |

Consumers outside the bucket, cited where used: `com/planet_ink/coffee_mud/MOBS/StdMOB.java` (recalculation, tick, tattoo methods), `com/planet_ink/coffee_mud/Libraries/MUDFight.java` (hunger and thirst drain, saving throws, fatigue messages), `com/planet_ink/coffee_mud/Races/StdRace.java` (aging), `com/planet_ink/coffee_mud/Locales/StdRoom.java` (thirst per round), `com/planet_ink/coffee_mud/Libraries/Sense.java` (ambiance text).

## How it works

### 1. Physical stats (PhyStats)

**Data model.** `DefaultPhyStats` stores ten integers indexed by the `STAT_` constants (`com/planet_ink/coffee_mud/Common/interfaces/PhyStats.java:359-377`): LEVEL 0, SENSES 1, ARMOR 2, DAMAGE 3, ATTACK 4, DISPOSITION 5, REJUV 6, WEIGHT 7, ABILITY 8, HEIGHT 9. The defaults are `{0,0,100,0,0,0,0,0,0,0}` (`com/planet_ink/coffee_mud/Common/DefaultPhyStats.java:52`), so an unarmored default has armor 100. Speed is a separate `double` that defaults to 1.0 (`:63`). The ambiance list is a sorted `String[]` that is replaced rather than mutated on every change (`addAmbiance` `:262-282`, `delAmbiance` `:284-304`), which lets copies share the array safely. Weight is in pounds (`com/planet_ink/coffee_mud/Common/interfaces/PhyStats.java:136-137`). Pull and push cost is `1 + weight/3` (`com/planet_ink/coffee_mud/Common/DefaultPhyStats.java:324-333`), read by `com/planet_ink/coffee_mud/MOBS/StdMOB.java:3254,3272` and `com/planet_ink/coffee_mud/Commands/Push.java:319,377`.

**Flags.** Three bitmasks share the integer storage. `CAN_*` (`com/planet_ink/coffee_mud/Common/interfaces/PhyStats.java:397-442`) say what a mob may sense or do, for example `CAN_NOT_MOVE` (1024) or `CAN_NOT_THINK` (131072). `SENSE_*` (`:475-521`) are runtime flags for items and rooms, for example `SENSE_ITEMNODROP` (32) or `SENSE_ROOMNOMOVEMENT` (1024). `IS_*` (`:553-597`) are dispositions: `IS_SITTING` 1024, `IS_SLEEPING` 512, `IS_SWIMMING` 4096, `IS_CLIMBING` 16384, `IS_FALLING` 32768. Parallel name tables (`CAN_SEE_CODES` `:447-471`, `SENSE_CODES` `:524-546`, `IS_CODES` `:610-635`) map bits to names for the flag library.

**Ambiance tags.** The `Ambiance` enum (`com/planet_ink/coffee_mud/Common/interfaces/PhyStats.java:645-693`) fixes three prefix conventions, which the code relies on by string matching:
- `@` marks a fact about the object or room: `@CHAOS`, `@LAW`, `@FLOATING`, `@TAMED`.
- `#` marks what a viewer perceives or an official status: `#GOOD`, `#EVIL`, `#LAW`, `#OFFICER`, `#JUDGE`.
- `-` suppresses a display or behaviour: `-MOOD`, `-ROBBERY`, `-GLOWING`, `-ALL`.

Code also adds untyped tags, such as `(Stance: X)` (`com/planet_ink/coffee_mud/Abilities/Fighter/Fighter_Stance.java:209`). A property switches itself off on an object by testing for a `"-"` tag: `Prop_CommonTwister.java:215` tests `"-"+ID()`, and `Prop_AbsorbDamage.java:261-262` tests `"-"+this`, which depends on the object's string form (not traced).

**Recalculation.** `StdMOB.recoverPhyStats` (`com/planet_ink/coffee_mud/MOBS/StdMOB.java:798`) copies base into working (`:800`), scales a speed above 1.0 by `speedAdj`, adds the character `STAT_WEIGHTADJ` to weight, and then lets the room and carried money modify the result.

**Text rendering.** `com/planet_ink/coffee_mud/Libraries/Sense.java:2591-2640` (`getDispositionBlurbs`) turns flags into bracketed blurbs such as `(glowing ^rred^?)`, `(^yinvisible^?)`, `(^yhidden^?)`, `(^rheat aura^?)`, `(^wmagical aura^?)` and `(^gbusy^?)`. Each is gated by the viewer having the matching sense and by the absence of its `SUPPRESS_*` tag.

### 2. Character attributes (CharStats)

**Data model.** There are 48 fixed codes (`com/planet_ink/coffee_mud/Common/interfaces/CharStats.java:44-144`): six base attributes STRENGTH 0 to WISDOM 5, GENDER 6, save codes 7 to 39 and 47 (`SAVE_POLYMORPH`), six maximum adjustments 22 to 27, AGE 28, WEIGHTADJ 32, four critical-hit percentages 40 to 43, FAITH 44, RECOVERRATE5_ADJ 45 and XP_ADJ_PCT 46. The base attributes default to 10 (`com/planet_ink/coffee_mud/Common/interfaces/CharStats.java:45`; `reset` at `com/planet_ink/coffee_mud/Common/DefaultCharStats.java:139`). Storage is `short[]` (`com/planet_ink/coffee_mud/Common/DefaultCharStats.java:76`), and writes are cast to `short` (`:1154`, `:1162`), so large values wrap silently.

**Maximum.** The effective maximum of a base attribute is `BASEMAXSTAT + adjustment`, where BASEMAXSTAT defaults to 18 (`com/planet_ink/coffee_mud/core/CMProps.java:3094`) and `getMaxStat` is at `com/planet_ink/coffee_mud/Common/DefaultCharStats.java:1091-1095`. `setPermanentStat` writes the base and sets the adjustment to `value - BASEMAXSTAT` (`:1083-1088`). `adjStat` moves base and adjustment together, floors the base at 1 and lets the adjustment go negative as a delta (`:1158-1172`). Character-creation minimum is BASEMINSTAT = 3 (`com/planet_ink/coffee_mud/core/CMProps.java:3095`).

**Classes and level.** The class list runs from oldest to current (`com/planet_ink/coffee_mud/Common/interfaces/CharStats.java:301-308`), and `getCurrentClass` is the last entry (`com/planet_ink/coffee_mud/Common/DefaultCharStats.java:653-656`). The serialised class string is capped at 250 characters: adding a class first removes zero-level classes until the string fits (`:607-625`). `StdMOB.recoverCharStats` recomputes the current class level each time as total level minus the other classes (`com/planet_ink/coffee_mud/MOBS/StdMOB.java:928`).

**Body parts.** The count for each part is the race `bodyMask()` plus `bodyAlterations`, clamped at 0 (`com/planet_ink/coffee_mud/Common/DefaultCharStats.java:698-707`). Alterations serialise as a semicolon list (`:710-732`).

**Gender.** The gender stat is an ASCII letter, `'M'` by default (`:140`). Pronoun sets come from the `GENDERS` list (`resources/lists.ini:896-899`) through `CMProps.getGenderDef` (`com/planet_ink/coffee_mud/core/CMProps.java:2368-2380`). An apparent gender name set with `setGenderName` overrides the lookup by its first letter (`com/planet_ink/coffee_mud/Common/DefaultCharStats.java:920-927`).

**Deity.** `worshipCharID` is the worship link. `deityName` overrides the name shown, and falls back to the worship ID (`:295-318`).

**Proficiency filters.** `getItemProficiencies()` returns an array of `DoubleFilterer<Item>` (`:1255-1264`). `com/planet_ink/coffee_mud/Abilities/Specializations/Proficiency_Weapon.java:124-152` appends one filter per weapon proficiency and compares the array identity with `lastFilter` to avoid appending twice. `com/planet_ink/coffee_mud/CharClasses/StdCharClass.java:906-911` (weapons) and `com/planet_ink/coffee_mud/Libraries/CoffeeUtensils.java:1069-1075` (armour) walk the array and take the first answer that is not NOTAPPLICABLE.

**Recalculation order.** `StdMOB.recoverCharStats` (`com/planet_ink/coffee_mud/MOBS/StdMOB.java:926-956`) sets the class level (`:928`), copies base into working (`:929`), and then applies, in order: riding, deity, each class, race, age effects computed from the *base* age (`:942`), effects, carried items, room, factions, and finally a Charisma penalty from hygiene (`:949-955`). The hygiene penalty subtracts `hygiene / HYGIENE_DELIMIT` from Charisma, or halves Charisma when that penalty is at least half of its current value.

**Base versus maximum adjustments.** `setRacialStat` (`com/planet_ink/coffee_mud/Common/DefaultCharStats.java:1108-1146`) is how race effects set attributes, for example `com/planet_ink/coffee_mud/Races/Unicorn.java:160-162` sets STR 18, DEX 6 and INT 1. When the current base is the default 10 it calls `setPermanentStat(racialMax)` (`:1110`). Otherwise it caps the current value at 7 times the current maximum (`:1119-1139`, logging each distinct message once to `SYSTEM_DEFCHARSTATS_ERRORS`) and computes `pctOfMax = cur/currMax * racialMax` plus `stdMaxAdj = (currMax-10)/currMax * racialMax` (`:1140-1142`). The result is written and the adjustment becomes `racialMax - BASEMAXSTAT` (`:1143-1144`). See Problems for a case where the result exceeds the new maximum.

**Saves.** `getSave` (`com/planet_ink/coffee_mud/Common/DefaultCharStats.java:780-838`) returns a save's raw value plus one or two attributes:

| Save code | Adds to the raw value | Lines |
|---|---|---|
| PARALYSIS | round((CON+STR)/2) | 785 |
| FIRE, COLD, WATER, ACID, ELECTRIC | round((CON+DEX)/2) | 787-803 |
| GAS | round((CON+STR)/2) | 793 |
| MIND | round((WIS+INT+CHA)/3) | 795 |
| GENERAL, POISON, DISEASE | CON | 797, 805, 809 |
| JUSTICE | CHA | 799 |
| UNDEAD | WIS + DOUBT | 807 |
| MAGIC | INT | 811 |
| TRAPS | DEX | 813 |
| DETECTION, OVERLOOKING, DOUBT, BLUNT, PIERCE, SLASH, SPELLS, PRAYERS, SONGS, CHANTS | nothing (raw value) | 815-833 |
| POLYMORPH | PARALYSIS formula (see Problems) | 834-835 |

Saves are used in combat by `com/planet_ink/coffee_mud/Libraries/MUDFight.java:2348-2409` (`checkDamageSaves`):
- A weapon hit uses BLUNT, PIERCE or SLASH by damage type. Any other damage type skips the check (`:2351-2368`).
- An ability hit uses the save for its family (chant, prayer, spell or song) plus MAGIC (`:2370-2395`). The save for the message's minor type is added through `CharStats.CODES.RVSCMMSGMAP` (`:2393-2395`).
- The total is clamped to plus or minus 100. With probability |total|% the damage value becomes `value * (100 - total) / 100` (`:2400-2406`). A save of 30 is a 30% chance to take 30% less damage, and a negative total is a weakness that can increase damage. The variable is named `chanceToFail`, which is misleading.

Other callers include `com/planet_ink/coffee_mud/Behaviors/WeatherAffects.java:855,919` (cold, water and fire saves against exposure), `com/planet_ink/coffee_mud/Libraries/Sense.java:1453,1473` (detection and overlooking) and `com/planet_ink/coffee_mud/Commands/DieRoll.java:140`. There are about 115 `getSave(` call sites across the tree.

**Age.** `ageCategory` and `ageName` (`com/planet_ink/coffee_mud/Common/DefaultCharStats.java:751-777`) place the AGE stat on the race's aging chart (`com/planet_ink/coffee_mud/Races/StdRace.java:59`, `{0,1,3,15,35,53,70,74,78}`; the unit of the age stat is not defined in this bucket). `StdRace.agingAffects` (`com/planet_ink/coffee_mud/Races/StdRace.java:386-460`) applies per-category modifiers, but only when AGE is above 0, the mob is not an immortal and ageing is not disabled (`:388-391`). Values are in Content worth mining.

### 3. Vitals (CharState)

**Data model.** `DefaultCharState` keeps `int[7]` (`states`): HITPOINTS 0, MANA 1, MOVE 2, HUNGER 3, THIRST 4, TICKSHUNGRY 5, TICKSTHIRSTY 6 (`com/planet_ink/coffee_mud/Common/interfaces/CharState.java:41-55`). Fatigue is a separate `long` in milliseconds (`com/planet_ink/coffee_mud/Common/DefaultCharState.java:52`). Defaults are `{10,100,50,500,1000,0,0}` (`:48`), and hunger and thirst are then overwritten from config (`:56-57`). The config keys are `HUNGER=500,100,100` and `THIRST=1000,100,100`, meaning full value, gain percent and loss percent (`com/planet_ink/coffee_mud/core/CMProps.java:2988-2995`).

**Three layers.** Each mob has base, max and current states. `StdMOB.recoverMaxState` (`com/planet_ink/coffee_mud/MOBS/StdMOB.java:1047-1066`) copies base into max and then applies race, riding, classes, effects, items, factions and room. `resetToMaxState` (`:1039-1043`) copies max into current.

**Bounded adjusters.** `adjHitPoints`, `adjMana` and `adjMovement` clamp to `[0, max]` and return false when a bound is hit (`com/planet_ink/coffee_mud/Common/DefaultCharState.java:121-135`, `:310-324`, `:339-353`). `adjFatigue` clamps only at 0 and ignores its `max` argument (`:150-159`).

**Hunger and thirst.** `maxHunger(w)` returns `max(1, w/250) * getHunger()` (`:215-224`), so the ceiling doubles at 500 pounds. It is called on `maxState()`, whose hunger is the full value (`com/planet_ink/coffee_mud/Abilities/Skills/Skill_DevourCorpse.java:179`, `com/planet_ink/coffee_mud/Abilities/Properties/Prop_UseAdjuster.java:129`). `adjHunger` (`:185-212`) multiplies a change by the gain or loss percent, obtained from `CMProps.getIntVarAsPct` (`com/planet_ink/coffee_mud/core/CMProps.java:1532-1546`, which divides by 100). It keeps the fractional part in `hoverflow` (`:194-197`) and resets TICKSHUNGRY once hunger is above zero (`:204-205`). `adjThirst` mirrors it (`:250-277`).

**The per-tick drain.** `MUDFight.expendEnergy` (`com/planet_ink/coffee_mud/Libraries/MUDFight.java:3426-3500`) runs every tick for player characters (`com/planet_ink/coffee_mud/MOBS/StdMOB.java:4203`) and, with `expendMovement` true, from movement commands (`com/planet_ink/coffee_mud/Commands/Push.java:326`, `com/planet_ink/coffee_mud/Commands/Pull.java:320`). Movement cost is `-room.pointsPerMove()`, increased by `10 * (weight - maxCarry) / maxCarry` when the character is overweight (`:3428-3441`). When the mob is awake and not an immortal (`:3444`), it computes `factor = baseWeight/500`, with a minimum of 0.001 (`:3447-3449`), and then:
- thirst drops by `room.thirstPerRound() * factor` (`:3452`);
- hunger drops by `factor` (`:3455`).

`thirstPerRound` (`com/planet_ink/coffee_mud/Locales/StdRoom.java:2871-2886`) is a room base value, plus 1 for hot, minus 1 for wet, plus 1 for dry and plus 1 for windy, then passed through the area climate's `adjustWaterConsumption`. It returns 0 when the area has no climate object.

**Death, warnings and messages.** While hunger or thirst is at or below zero, the matching TICKS counter increments each tick (`com/planet_ink/coffee_mud/Libraries/MUDFight.java:3459-3462`). Death follows when that counter exceeds `DEATH_THIRST_TICKS` (5400) or `DEATH_HUNGER_TICKS` (10800) (`com/planet_ink/coffee_mud/Common/interfaces/CharState.java:67-69`; `com/planet_ink/coffee_mud/Libraries/MUDFight.java:3464-3470`). Messages:
- At death: "YOU ARE DYING OF THIRST!" and "YOU ARE DYING OF HUNGER!" (`:3466-3468`).
- 30 ticks before death: "You are dehydrated, and near death.  DRINK SOMETHING!" and "You are starved, and near death.  EAT SOMETHING!" (`:3474-3478`).
- Every `ANNOYANCE_DEFAULT_TICKS` (60) ticks, a tier chosen by how far the counter is through its limit (`:3481-3497`). Thirst: "You are thirsty.", then "You are parched! Drink something!", then "You are dehydrated! Drink something!". Hunger: "You are hungry.", then "You are famished! Eat something!", then "You are starved! Eat something!".

**Fatigue.** The block at `com/planet_ink/coffee_mud/MOBS/StdMOB.java:4236-4276` runs for non-monsters whose race is not infatigueable and whose fatigue is not disabled:
- While sleeping, fatigue drops by `REST_PER_SLEEP`, which is 200 ticks of time per tick (`:4242`; `com/planet_ink/coffee_mud/Common/interfaces/CharState.java:71`).
- While awake, fatigue rises by one tick length, `CMProps.getTickMillis()` (`:4247`).
- Above `FATIGUED_MILLIS` (3000 ticks), each tick has a 1% chance of the yawning disease (`:4248`, `:4264`). Above three times that, each tick has a 1% chance of a SILLY mood, if hit points are full (`:4251-4262`).
- Above `FATIGUED_EXHAUSTED_MILLIS` (ten times that), each tick has a 1% chance that the character falls asleep: "<S-NAME> fall(s) asleep from exhaustion!!" (`:4268-4273`).
- `REST_PER_SIT` (`com/planet_ink/coffee_mud/Common/interfaces/CharState.java:73`) is defined but nothing reads it.

**Timing.** The default tick is 4000 ms (`com/planet_ink/coffee_mud/core/CMProps.java:681`; a `TICKTIME` below 500 is reset to 4000 at `:2720-2722`), which gives 15 ticks per real minute (`:683`). At that tick length:

| Constant | Ticks | Time at the default tick |
|---|---|---|
| `ANNOYANCE_DEFAULT_TICKS` (`com/planet_ink/coffee_mud/Common/interfaces/CharState.java:63`) | 60 | 4 min |
| `DEATH_THIRST_TICKS` (`:67`) | 5400 | 6 h |
| `DEATH_HUNGER_TICKS` (`:69`) | 10800 | 12 h |
| `FATIGUED_MILLIS` (`:75`) | 3000 awake ticks | about 3.3 h awake |
| `FATIGUED_EXHAUSTED_MILLIS` (`:77`) | 30000 awake ticks | about 33 h awake |
| Sleep from fatigued to zero (`REST_PER_SLEEP`) | 15 sleeping ticks | 1 min |

Worked from these constants, not measured in a running server: a 150-pound character has a hunger factor of 0.3, so it loses about one hunger point every 3.3 ticks. Going from full (500) to zero takes about 1,667 ticks, roughly 1.9 hours at 4 seconds, with no food.

### 4. Tattoos

**Data model.** `DefaultTattoo` (`com/planet_ink/coffee_mud/Common/DefaultTattoo.java:36-193`) holds an uppercased name and an absolute expiry timestamp in milliseconds (`:44`). A tick-down of n sets the expiry to now plus n ticks (`:61-69`, `:75-81`). A tick-down of 0 means permanent (`:64-65`, `:77-78`). `getTickDown` returns the remaining ticks rounded up, and returns 1 once the tattoo has expired but has not yet been removed (`:87-95`). `toString` gives `N NAME` (`:107-110`) and `parse` reads the same form (`:165-186`), so the stored form carries remaining ticks, not a deadline. Lookups are case-insensitive, because the container sorts with `compareToIgnoreCase` (`com/planet_ink/coffee_mud/core/collections/CMUniqNameSortSVec.java:65-81`).

**Container and sweep.** Tattoos are held by the mob in `StdMOB.tattoos` (`com/planet_ink/coffee_mud/MOBS/StdMOB.java:137`). `addTattoo(Tattoo)` ignores a name already present (`:5725-5733`), so re-applying a tattoo does not refresh its timer. `StdMOB.tick` removes any timed tattoo whose expiry is before the current wall-clock time, which is refreshed at the start of that tick (`:4398-4406`).

**Uses.**
- Location markers: a tattoo named `LOCNAME:text` is listed against that body location (`com/planet_ink/coffee_mud/Commands/Equipment.java:86-100`).
- Achievements: a tattoo whose name matches an achievement ID is an award on the mob and on the account (`com/planet_ink/coffee_mud/Commands/Remort.java:501`, `com/planet_ink/coffee_mud/Commands/Achievements.java:113-132`).
- Scars: `com/planet_ink/coffee_mud/Abilities/Misc/Scarring.java:133-136` refreshes an existing scar with `setTickDown`, or adds it with a timer if absent.
- Scripts: the `ISTATTOOTIME` test reads `getTickDown` (`com/planet_ink/coffee_mud/Common/DefaultScriptingEngine.java:5543`).

## Content worth mining

### Attribute catalogue

From the `DefCharStat` table (`com/planet_ink/coffee_mud/Common/interfaces/CharStats.java:851-900`). Each code has an abbreviation and an attribution word, the adjective used to describe someone with a high value.

| Code | Abbr | Attribution word |
|---|---|---|
| STRENGTH | S | STRONG |
| INTELLIGENCE | I | INTELLIGENT |
| DEXTERITY | D | DEXTEROUS |
| CONSTITUTION | C | HEALTHY |
| CHARISMA | CH | CHARISMATIC |
| WISDOM | W | WISE |
| AGE | A | OLD |
| FAITH | F | FAITHFUL |
| RECOVERRATE5_ADJ | RR | REJUVENATINGLY DIFFERENT |
| XP_ADJ_PCT | XP | EXPERIENTIAL |
| SAVE_DETECTION | vH | CONCEALED |
| SAVE_OVERLOOKING | vO | WATCHFUL |
| SAVE_DOUBT | vB | DOUBTFUL |
| SAVE_POLYMORPH | vY | ACTUALIZED |

Most saves use "RESISTANT TO ..." (for example FIRE, COLD, MIND ("RESISTANT TO MIND ATTACKS"), JUSTICE ("RESISTANT TO UNDIGNIFIED ATTACKS"), TRAPS). Several are bound to a message type, which gives a ready-made damage-type-to-save table: FIRE to `TYP_FIRE` (`com/planet_ink/coffee_mud/Common/interfaces/CMMsg.java:1052`), MAGIC to `TYP_CAST_SPELL` (`:1028`), POLYMORPH to `TYP_POLYMORPH` (`:1272`).

### Gender and pronouns

`resources/lists.ini:896-899` holds one row per gender code, with a noun, pronouns and kin words:

| Code | Noun | Pronouns (object, possessive, subject) | Kin words (man / son / boy) |
|---|---|---|---|
| M | male | him, his, he | man, son, boy |
| F | female | her, her, she | woman, daughter, girl |
| N | neuter | it, its, it | person, child, child |

Titles follow the same rows (sir/madam, mr./ms., mister/madam).

### Aging

Age categories are INFANT 0 to ANCIENT 8 (`com/planet_ink/coffee_mud/Races/interfaces/Race.java:486-502`). The default aging chart is `{0, 1, 3, 15, 35, 53, 70, 74, 78}` (`com/planet_ink/coffee_mud/Races/StdRace.java:59`). `StdRace.agingAffects` (`:386-460`) applies these modifiers to the working stats when AGE is above 0:

| Category | Save MIND | Save UNDEAD | MAX STR | MAX CON | MAX DEX | MAX INT | MAX WIS | MAX CHA |
|---|---|---|---|---|---|---|---|---|
| Infant, toddler | -10 | | -2 | | | -4 | -4 | |
| Child | -5 | | -1 | | | -2 | -2 | |
| Young adult, mature | | | | | | | | |
| Middle-aged | +5 | | -1 | -1 | -1 | +1 | +1 | +1 |
| Old | +10 | -5 | -2 | -2 | -2 | +2 | +2 | +2 |
| Venerable | +15 | -25 | -3 | -3 | -3 | +3 | +3 | +3 |
| Ancient | +20+5n | -35+5n | -(3+n) | -(3+n) | -(3+n) | +(3+n) | +(3+n) | +(3+n) |

For Ancient, `n` is the band (see Problems for its off-by-one). Race and class save modifiers are plain numbers, for example Elf MAGIC +10 (`com/planet_ink/coffee_mud/Races/Elf.java:154`), Drow MAGIC +20 (`com/planet_ink/coffee_mud/Races/Drow.java:226`), Mindflayer MAGIC +50 (`com/planet_ink/coffee_mud/Races/Mindflayer.java:209`), Beholder MAGIC set to 75 (`com/planet_ink/coffee_mud/Races/Beholder.java:200`), Halfling POLYMORPH +10 (`com/planet_ink/coffee_mud/Races/Halfling.java:149`) and Tumbleweed POLYMORPH +100 (`com/planet_ink/coffee_mud/Races/Tumbleweed.java:190`).

### Messages

The hunger, thirst and fatigue lines are listed under How it works. They are short and tiered, and make a good template for a single-player status line.

## Notable design ideas

- **One number, two jobs.** A save is both the probability of a reduction and its size, `value * (100 - save)/100`, clamped to plus or minus 100 (`com/planet_ink/coffee_mud/Libraries/MUDFight.java:2400-2406`). A negative save becomes a vulnerability for free.
- **Potential versus current.** Each base attribute has a current value and a separate maximum adjustment. Base values are what the character is; the maximum is a separate value that `setPermanentStat` and `adjStat` move together with the base (`com/planet_ink/coffee_mud/Common/DefaultCharStats.java:1083-1095`, `:1158-1172`).
- **Recompute from base, in layers.** Every recalculation copies base into working and reapplies classes, race, age, effects, items, room and factions in a fixed order (`com/planet_ink/coffee_mud/MOBS/StdMOB.java:926-956`). Nothing needs to be undone when an effect ends.
- **Hunger and thirst scale with weight and place.** The drain is per tick, proportional to body weight over 500 (`com/planet_ink/coffee_mud/Libraries/MUDFight.java:3447-3455`). Thirst also depends on the room's climate (`com/planet_ink/coffee_mud/Locales/StdRoom.java:2871-2886`). Food capacity also scales with weight (`com/planet_ink/coffee_mud/Common/DefaultCharState.java:215-224`).
- **Fractional carry.** Hunger and thirst keep the fraction left after each integer update (`com/planet_ink/coffee_mud/Common/DefaultCharState.java:194-197`, `:259-262`), so slow drains do not vanish from rounding.
- **Fatigue asymmetry.** Awake fatigue accrues one tick per tick, and sleep removes 200 ticks per tick (`com/planet_ink/coffee_mud/MOBS/StdMOB.java:4242-4247`). Rest clears fatigue quickly, and the fatigue thresholds gate small events rather than hard penalties.
- **Tattoos as universal flags.** One named, expiring flag type serves achievements, body markers and scars (`com/planet_ink/coffee_mud/Commands/Equipment.java:86-100`, `com/planet_ink/coffee_mud/Abilities/Misc/Scarring.java:126-140`, `com/planet_ink/coffee_mud/Commands/Achievements.java:113-132`). A flag needs no new data type.
- **Ambiance prefixes.** Typed prefixes (`@` fact, `#` perceived, `-` suppress) let one string list carry world facts, perceptions and switches for displays and properties (`com/planet_ink/coffee_mud/Common/interfaces/PhyStats.java:645-693`). A property can be switched off on an object with a `-` tag, as `Prop_CommonTwister.java:215` does (`"-"+ID()`).
- **Proficiency as an ordered filter chain.** Each proficiency adds a filter; the first filter with an opinion wins (`com/planet_ink/coffee_mud/CharClasses/StdCharClass.java:906-911`, `com/planet_ink/coffee_mud/Libraries/CoffeeUtensils.java:1069-1075`). An identity check stops the chain growing on every recalculation (`com/planet_ink/coffee_mud/Abilities/Specializations/Proficiency_Weapon.java:137-151`).
- **Data-driven stat table.** New or replacement stat codes come from ini rows at boot, with a type, abbreviation, description and attribution word (`com/planet_ink/coffee_mud/Common/interfaces/CharStats.java:981-1045`).

## Problems and cruft

**Wrong as written (verified by reading the code; in-game effect not run)**

1. **POLYMORPH save uses the PARALYSIS formula.** `com/planet_ink/coffee_mud/Common/DefaultCharStats.java:834-835` reads `getStat(STAT_SAVE_PARALYSIS)` in the POLYMORPH case, so the polymorph stat itself is ignored. An ability whose message type is `TYP_POLYMORPH` (`com/planet_ink/coffee_mud/Common/interfaces/CMMsg.java:1272`) is checked through this path (`com/planet_ink/coffee_mud/Libraries/MUDFight.java:2393-2395`), and the polymorph bonuses in races (for example `com/planet_ink/coffee_mud/Races/Tumbleweed.java:190`, `+100`) never reach it.
2. **Posture sentence test mixes a bitmask with subtraction.** `com/planet_ink/coffee_mud/MOBS/StdMOB.java:1866` tests `((unnormalMask-visiblyActiveMask)>0)`, where both values are disposition bitmasks. The numeric difference is not a bit test. Example: base disposition SWIMMING (4096), current SLEEPING (512) gives 512 - 4096 = -3584, so this clause is false and the "<name> is ... here" sentence (`:1882-1887`) is skipped unless another clause applies. The intent is probably `(unnormalMask & ~visiblyActiveMask) != 0`.
3. **Racial stat can exceed the new maximum.** `com/planet_ink/coffee_mud/Common/DefaultCharStats.java:1140-1144` has no clamp. Take base STR 14 and a race that sets STR 18 with BASEMAXSTAT 18 (`com/planet_ink/coffee_mud/Races/Unicorn.java:160`). Then pct = 14, stdMax = 8, the result is 22, and the new maximum is 18. The default-10 case is special-cased (`:1110`) and gives exactly the racial value, so behaviour differs by base value.
4. **Long-form display class name prints a field, which can be null.** `com/planet_ink/coffee_mud/Common/DefaultCharStats.java:408` concatenates the raw field `displayClassName`, not the method `displayClassName()` used on line 407. If only the level is set, the text reads "level 12 null". `com/planet_ink/coffee_mud/Abilities/Songs/Skill_Disguise.java:170-173` sets the level and the class name independently, so this is reachable.
5. **`reset()` does not restore factory defaults for most stats.** `com/planet_ink/coffee_mud/Common/DefaultCharStats.java:137-159` resets the six base attributes and gender only. Saves, maximum adjustments, age, weight adjustment and the other non-base codes keep their values, although `com/planet_ink/coffee_mud/Common/interfaces/CharStats.java:578-581` promises factory defaults. `com/planet_ink/coffee_mud/MOBS/StdFactoryMOB.java:123-129` calls `reset` on objects that appear to be reused, so previous values can persist.
6. **Age bands are off by one.** `com/planet_ink/coffee_mud/Races/StdRace.java:446` computes `num = floor((age - chart[ANCIENT]) / diff) - 1`, and only a result of 0 is forced to 1. Ages 78 to 81 therefore give -1, ages 82 to 85 and 86 to 89 both give 1, and so on. The display (`com/planet_ink/coffee_mud/Common/DefaultCharStats.java:773-776`) numbers 82 to 85 as "I" and 86 to 89 as "II". The effect and the label disagree.
7. **Fatigue has no ceiling and ignores its bound.** `com/planet_ink/coffee_mud/Common/DefaultCharState.java:150-159` takes a `max` argument and never uses it. The "respecting boundaries" wording in `com/planet_ink/coffee_mud/Common/interfaces/CharState.java:96-99` is therefore false for fatigue.
8. **First `DefaultCharState` instance keeps literal hunger and thirst.** `com/planet_ink/coffee_mud/Common/DefaultCharState.java:49` clones `DEFAULT_STATES` as a field initialiser, which runs before the constructor body. The constructor (`:56-57`) then writes the configured values into that shared static array. The first instance keeps 500 and 1000, and later instances and `reset` see the configured values. This matters only if the config differs from those literals.
9. **Copy does not carry the deity name.** `com/planet_ink/coffee_mud/Common/DefaultCharStats.java:842-869` (`copyOf`) omits `deityName`, while `copyInto` keeps it (`:195`). `com/planet_ink/coffee_mud/MOBS/StdMOB.java:643-644` copies base stats through `copyOf`. Impact is small, because the masking that sets `deityName` (`com/planet_ink/coffee_mud/Abilities/Thief/Thief_MaskFaith.java:107-109`) writes to the working copy, which is rebuilt on each recalculation.

**Latent or cruft**

10. **Ambiance order mismatch.** `com/planet_ink/coffee_mud/Common/DefaultPhyStats.java:472` sorts with natural (case-sensitive) order when loading an `AMBIANCES` value. `addAmbiance` (`:278`) and `isAmbiance` (`:311`) use case-insensitive order. Mixed-case tags loaded this way can be stored in an order the binary search does not expect. Lowercase tags are produced at `com/planet_ink/coffee_mud/Abilities/Fighter/FighterGrappleSkill.java:329`. Unverified whether loaded data reaches this path.
11. **Tattoo normalisation is inconsistent.** `DefaultTattoo.parse` (`:170`) keeps the name as written, without trimming or uppercasing, while `set` uppercases (`:56`, `:63`). `equals` (`:159-160`) is case-sensitive, while the container's lookups are case-insensitive (`com/planet_ink/coffee_mud/core/collections/CMUniqNameSortSVec.java:65-81`).
12. **`addTattoo` never refreshes.** `com/planet_ink/coffee_mud/MOBS/StdMOB.java:5725-5733` ignores a duplicate name. Callers that want a refresh must call `setTickDown` first, as `com/planet_ink/coffee_mud/Abilities/Misc/Scarring.java:133-134` does.
13. **`DefaultTattooable` is dead code.** `com/planet_ink/coffee_mud/Common/DefaultTattooable.java` is not referenced by any other file (a text search finds only itself). `com/planet_ink/coffee_mud/MOBS/StdMOB.java:137` and `:5699-5751` implement the same container inline. Its `copyOf` (`:53-65`) copies references with `addAll`, so the copy shares `Tattoo` objects.
14. **Unused and duplicate code.** `com/planet_ink/coffee_mud/Common/DefaultCharStats.java:369-379` (`combinedLevels`) duplicates `combinedSubLevels` (`:356-367`) and has no caller. `addItemProficiency` and `addItemDeficiency` (`:1267-1298`) have no callers outside `com/planet_ink/coffee_mud/Common/`. `REST_PER_SIT` (`com/planet_ink/coffee_mud/Common/interfaces/CharState.java:73`) is unused.
15. **Flag tables overlap or are wrong.** `IS_UNNORMAL` and `IS_VISIBLYACTIVE` each list `IS_CLIMBING` twice (`com/planet_ink/coffee_mud/Common/interfaces/PhyStats.java:601,603`), and `IS_VISIBLYACTIVE` omits `IS_FLYING`, which may be intended. Several `SENSE_` values are reused across item and room kinds: 256 (`:491,493`), 512 (`:495,497`) and 1024 (`:499,501`). That is safe only if an object never carries both kinds of flag. `SENSE_CODES` names index 12 `ROOMGRIDSYNC` (`:537`) for `SENSE_ROOMSYNC` (`:505`).
16. **Reverse message map returns a surprising stat for -1.** Every stat registered with mapping -1 overwrites key -1 in `rvsStatCMMsgMapping` (`com/planet_ink/coffee_mud/Common/interfaces/CharStats.java:1537`), so `RVSCMMSGMAP(-1)` returns STAT_XP_ADJ_PCT (46). It is not reachable today, because CMMsg minor codes are masked to 0 to 2047 (`com/planet_ink/coffee_mud/Common/interfaces/CMMsg.java:938`) and default to 0 (`com/planet_ink/coffee_mud/Common/DefaultMessage.java:124`).
17. **Registry keyed by one character.** `CharStats.CODES` has 256 slots, one per possible first character of the thread-group name (`com/planet_ink/coffee_mud/Common/interfaces/CharStats.java:954`, `:1048-1068`). `CMLib` uses the same pattern (`com/planet_ink/coffee_mud/core/CMLib.java:63-74`), so two groups with the same initial share one registry. The static accessors such as `BASECODES()` (`:1076-1079`) dereference the instance without a null check (unverified whether it can be null).
18. **`copyOf` is heavy.** `com/planet_ink/coffee_mud/Common/DefaultCharStats.java:842-845` builds a new object through the constructor, which calls `reset()` and two `CMClass` lookups (`:98-101`, `:137-158`), then overwrites the fields.
19. **Silent truncation.** Stats are `short` (`:76`); `setStat` (`:1154`) and `adjStat` (`:1162`) cast without a range check. The comment at `:1151-1152` says this is deliberate, because the object doubles as a work container.
20. **Inconsistent lookups.** `getStat(String)` returns null for unknown names (`com/planet_ink/coffee_mud/Common/DefaultCharStats.java:1199-1212`), while `DefaultCharState` (`:440-458`) and `DefaultPhyStats` (`:480-509`) return "". `setStat(String,String)` matches by case-sensitive prefix (`com/planet_ink/coffee_mud/Common/DefaultCharStats.java:1244-1250`). `CharStats.CODES.find` needs lowercase input for its prefix branch (`com/planet_ink/coffee_mud/Common/interfaces/CharStats.java:1444-1448`).
21. **Easy-to-misuse helper.** `maxHunger(w)` multiplies the receiver's current hunger (`com/planet_ink/coffee_mud/Common/DefaultCharState.java:215-224`). Calling it on the wrong object silently gives a different ceiling.
22. **Copy-paste comments.** `com/planet_ink/coffee_mud/Common/interfaces/CharState.java:50-53` documents TICKSHUNGRY and TICKSTHIRSTY as "thirst".
23. **Partial copies.** `DefaultCharState.copyInto` drops the fractional carries (`:95-106`). `DefaultPhyStats.copyInto` (non-default branch, `:354-367`) and `sameAs` (`:420-428`) skip speed and replacement name.

## Takeaways for a Python SUD

- **Keep the base and maximum split, with a dict or dataclass.** `base[attr]` and `max_adj[attr]`, with `maximum = BASE_MAX + max_adj[attr]`. Single-player training then cannot exceed the ceiling, and effects change only the derived view.
- **Use one save number for both chance and size.** `if rng.randint(1, 100) <= abs(save): dmg = dmg * (100 - save) / 100`, clamped to plus or minus 100. Give it a small table of damage types (blunt, pierce, slash, fire, cold, poison, mind, magic), not 26 codes.
- **Recompute, do not undo.** Each turn, copy base and apply effects in a fixed order. An effect that ends simply stops applying, so no undo step is needed.
- **Store hunger and thirst as floats.** Python needs no overflow carry, so drop `hoverflow` and `toverflow`. Keep the weight factor, `rate = weight / 500 * room_rate`, and the max as `max(1, weight // 250) * FULL`.
- **Time in game turns, not milliseconds or wall-clock.** A turn-based or real-time single-player game should express fatigue, tattoo timers and death counters in turns. Persist remaining turns, as the `N NAME` form already does, so pauses and quits do not age timers.
- **Keep the fatigue asymmetry, drop the milliseconds.** One unit per awake turn and a large recovery per sleeping turn, with two thresholds, is enough. Drop the 1% random triggers unless they add flavour.
- **Replace tattoos with a typed flag.** `Flag(name, expires_turn | None)` in a dict on the character, reused for achievements, markers and scars. Drop the account-level variant and the case-insensitive sorted container.
- **Ambiance becomes a typed set.** A `frozenset` of enum tags, each with a category (fact, perceived, suppress), instead of prefixed strings. Keep the idea of a suppression tag that disables a named effect.
- **Make ages and genders data.** Put the aging chart and the per-category modifiers in YAML, and the pronoun table in a dict. Apply aging when stats are read.
- **Drop or simplify.** The thread-group registry, bit-flag overlap, `short` truncation, racial re-scaling (use explicit racial modifiers instead), proficiency closures (use a list of allowed item tags) and the hygiene Charisma penalty.
- **Multiplayer assumptions to rethink.** Hunger, thirst and fatigue are driven only for non-monster characters (`com/planet_ink/coffee_mud/MOBS/StdMOB.java:4203`, `:4236`), and the hunger and thirst drain is skipped for immortals and sleepers (`com/planet_ink/coffee_mud/Libraries/MUDFight.java:3444`). In a single-player game, decide whether NPCs share the same rules. Deity masking, soulmates and account-level achievements have no single-player counterpart.

## Coverage

- **Read in full (9 of 9 manifest files):** `com/planet_ink/coffee_mud/Common/interfaces/PhyStats.java`, `com/planet_ink/coffee_mud/Common/DefaultPhyStats.java`, `com/planet_ink/coffee_mud/Common/interfaces/CharState.java`, `com/planet_ink/coffee_mud/Common/DefaultCharState.java`, `com/planet_ink/coffee_mud/Common/interfaces/CharStats.java`, `com/planet_ink/coffee_mud/Common/DefaultCharStats.java`, `com/planet_ink/coffee_mud/Common/interfaces/Tattoo.java`, `com/planet_ink/coffee_mud/Common/DefaultTattoo.java`, `com/planet_ink/coffee_mud/Common/DefaultTattooable.java`.
- **Read in part, for the interactions cited:** `com/planet_ink/coffee_mud/MOBS/StdMOB.java` (recalculation, tick, tattoo methods, reset and copy), `com/planet_ink/coffee_mud/Libraries/MUDFight.java` (`expendEnergy`, `checkDamageSaves`, fatigue checks), `com/planet_ink/coffee_mud/Races/StdRace.java` (`agingAffects`), `com/planet_ink/coffee_mud/Locales/StdRoom.java` (`thirstPerRound`), `com/planet_ink/coffee_mud/Libraries/Sense.java` (`getDispositionBlurbs`), `com/planet_ink/coffee_mud/core/CMProps.java` (config keys, tick, percent helper), `com/planet_ink/coffee_mud/Abilities/Specializations/Proficiency_Weapon.java`, `com/planet_ink/coffee_mud/CharClasses/StdCharClass.java` (`weaponCheck`), `com/planet_ink/coffee_mud/Libraries/CoffeeUtensils.java`, `com/planet_ink/coffee_mud/Commands/Equipment.java`, `com/planet_ink/coffee_mud/Commands/Achievements.java`, `com/planet_ink/coffee_mud/Commands/Remort.java`, `com/planet_ink/coffee_mud/Abilities/Misc/Scarring.java`, `com/planet_ink/coffee_mud/Abilities/Songs/Skill_Disguise.java`, `com/planet_ink/coffee_mud/Common/interfaces/CMMsg.java` (type constants), `com/planet_ink/coffee_mud/Common/DefaultMessage.java`.
- **Sampled by search only:** the call sites of `getSave`, `adjHunger`, `maxHunger`, `setRacialStat` and `adjStat`; race and class stat modifiers (a few examples only); `com/planet_ink/coffee_mud/Races/Undead.java`, `com/planet_ink/coffee_mud/Races/Unicorn.java`.
- **Skipped:** the body of `com/planet_ink/coffee_mud/Races/interfaces/Race.java` and the `PlayerStats` hygiene value; the ini text beyond the `GENDERS` list; the individual ability and disease files that consume these stats.
