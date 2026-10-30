# Ability mapping, qualification and expertise

> Part of the CoffeeMud NEWOVERVIEW. Bucket: `b028` (Ability mapping, qualification and expertise) · 4 files · 6469 lines of Java.

## Summary

CoffeeMud's ability-mapping layer decides which classes, races, clans and individual players may learn which skills, at what level, for what cost, with what starting and maximum proficiency, and under which prerequisite skills and zapper masks. The expertise layer adds purchasable, multi-stage upgrades ("expertises") that attach to groups of skills and modify them through one of twelve numeric modifier types (`XType`) that skill code reads. Both systems are data-driven: class and race code calls `addCharAbilityMapping`, while `skills/allqualifylist.txt` and `skills/expertises.txt` define the shared skill pool and the expertise families. For a single-player Python SUD this is the most reusable part of CoffeeMud for answering "what can the player learn next, and why not yet", but the multi-class, clan, archon and common-skill pool machinery should be dropped.

## Key files and types

Paths are relative to the CoffeeMUD folder. In the text below, `Libraries/CMAbleMap.java` is shorthand for `com/planet_ink/coffee_mud/Libraries/CMAbleMap.java`, and the same applies to every other Java path.

| File or class | Role | Lines |
|---|---|---|
| `com/planet_ink/coffee_mud/Libraries/interfaces/AbilityMapper.java` | Interface for the mapping library. Defines the `AbilityMapping` record and builder, `SecretFlag`, `AbilCostType`, `CompoundingRule` and `QualifyingID`, plus the query and update API. | 1500 |
| `Libraries/CMAbleMap.java` | `CMAbleMap extends StdLibrary implements AbilityMapper`. Storage, qualification, prerequisite resolution, the ALL/EACH list loader, mana-compounding rules, and the proficiency-gain formula. | 2912 |
| `Libraries/interfaces/ExpertiseLibrary.java` | Interface for expertises. Defines the `XType` enum, `ExpertiseDefinition`, and the query, teach and cost API. | 619 |
| `Libraries/ColumbiaUniv.java` | `ColumbiaUniv extends StdLibrary implements ExpertiseLibrary`. Parses `skills/expertises.txt`, generates stage definitions, builds the skill-to-expertise usage map, checks ownership, and handles teaching. | 1438 |

Related files outside this bucket, cited only for their interaction with it:

| File | What it contributes |
|---|---|
| `Abilities/StdAbility.java` | Training cost (215-258), expertise accessors (400-472), proficiency gain (1810-1850), teach, learn and practice gates (2423-2823), mana compounding (2001-2040). |
| `Abilities/Common/CommonSkill.java`, `Abilities/Languages/StdLanguage.java` | Separate cost keys (CommonSkill 107-109, StdLanguage 99-101), common-skill pool check (CommonSkill 751-800). |
| `CharClasses/StdCharClass.java` | `startCharacter` (1326-1333), `grantAbilities` (970-1040), `addedExpertise` hook (959-961), `showThinQualifyList` (200-203). |
| `Libraries/CoffeeLevels.java` | Level-up calls `grantAbilities` (1280), announces expertise gains (1335), and forgets class-only skills on de-levelling (470). |
| `MOBS/StdMOB.java` | Expertise storage (5537-5581). Teach message checks in `okMessage` (3236) and `executeMsg` (3473). |
| `Libraries/MUDZapper.java` | Mask scanner that also yields prerequisite and expertise IDs (`getAbilityEduReqs`, 3830-3905) and the mask description text (1673-1700). |
| `Libraries/CMAbleComps.java` | Common-skill pool remainders (`getSpecialSkillRemainder`, 1312-1333). |
| `core/CMProps.java`, `coffeemud.ini` | Cost keys and formula defaults (cited in place). |
| `resources/skills/allqualifylist.txt`, `resources/skills/expertises.txt` | The shared skill list and the expertise families (data). |
| `Commands/Qualify.java`, `Commands/Practice.java`, `Commands/Teach.java`, `Commands/ListCmd.java` | Player-facing callers. |
| `Abilities/Common/CraftingSkill.java`, `Abilities/Common/EnhancedCraftingSkill.java` | Crafting enhancement stages. |

## How it works

### 1. Data model

The unit of data is an `AbilityMapping` (`AbilityMapper.java:1149-1426`). One mapping says: holder X (a class, race, clan government, or `All`) gets skill Y at level L. Defaults come from `newAbilityMapping` (`Libraries/CMAbleMap.java:522-538`).

| Field | Meaning | Default |
|---|---|---|
| `ID` | Holder: class ID, race ID, clan government ID, or `All` | `""` |
| `abilityID` | Skill or spell ID | `""` |
| `qualLevel` | Level at which the holder qualifies | -1 |
| `autoGain` | Granted automatically at qualification, rather than trained | false |
| `defaultProficiency` | Starting proficiency when granted | 0 |
| `maxProficiency` | Cap for this holder | 100 |
| `defaultParm` | Misc text given to the ability on grant | `""` |
| `secretFlag` | `SECRET`, `PUBLIC` or `MASKED` (`AbilityMapper.java:1434-1447`) | PUBLIC |
| `isAllQualified` | Mapping came from the shared ALL/EACH list | false |
| `skillPreReqs` | `DVector` of (ability ID or OR-list, minimum proficiency) | empty |
| `extraMask` | Zapper mask the learner must pass | `""` |
| `costOverrides` | `Integer[4]` indexed by `AbilCostType` PRAC, TRAIN, MANA, PRACPRAC (`AbilityMapper.java:1134-1140`) | null |
| `allQualifyFlag` | Provenance flag, used when the shared list is reloaded | false |

Storage in `CMAbleMap`:

- `completeAbleMap`: holder ID to (ability ID to mapping), `Libraries/CMAbleMap.java:51-52`. The pseudo-holder `All` holds the shared pool. Race and clan-government holders sit in the same map.
- `reverseAbilityMap`: ability ID to (holder ID to mapping), `53-54`. Used for "who qualifies for this skill" (`getQualifyingEntities`, 959-964).
- `lowestQualifyingLevelMap` and `maxProficiencyMap`: per-skill caches, filled only for playable-class holders and `All` (`mapAbilityFinal`, 831-852). Read by `lowestQualifyingLevel` (984-990) and `getMaxProficiency(String)` (2364-2369), which defaults to 100.
- `allows`: a reverse dependency index from prerequisite to dependents (`63`, filled in `addPreRequisites`, 344-401).
- `compounders` and `compoundingRules` (57-60) for mana compounding.

### 2. Where mappings come from

- **Code.** Classes declare their mappings in `initializeClass()`. In `CharClasses/Pirate.java` a level-12 skill is mapped with a prerequisite list (line 151), `Song_Nothing` gets a one-practice cost override through a `costOverrides` array (161-163), and `Thief_PetSpy` requires `Thief_PirateFamiliar` (173). Races add mappings for cultural abilities (`Races/StdRace.java:757`) and dynamic ones (1905). Clan governments use `addDynaAbilityMapping` (`Common/DefaultClanGovernment.java:1321`). `CharClasses/GenCharClass.java:867` does the same for data-built classes.
- **Shared list.** `resources/skills/allqualifylist.txt` is read by `getAllQualifiesMap` (`Libraries/CMAbleMap.java:2561-2614`) into two maps. `[EACH]` entries are copied into every class holder (`handleEachAndClassAbility`, 882-919), so each class owns its own copy. `[ALL]` entries are stored once under `All` and form a shared pool. The line format is given in the file header: `[QUAL_LVL] [ABIL_ID] (AUTOGAIN) (PROF=n) (REQUIRES=...) (MASK=...)`. `makeAllQualifyMapping` (2389-2482) parses it.
- **Per player.** Achievements can grant a personal mapping or expertise (`Libraries/Achievements.java:8546` puts into `getExtraQualifiedSkills`, `8610` into `getExtraQualifiedExpertises`). The personal entry is checked first in almost every qualification query (`getPersonalMapping`, `Libraries/CMAbleMap.java:1556-1572`).
- **Creation path.** The 11-argument `addCharAbilityMapping` (`Libraries/CMAbleMap.java:490-517`) deletes any previous mapping, returns null for disabled IDs (504), creates the holder map on first use and copies the EACH list into it (511), builds the object through `makeAbilityMapping` (765-810), and calls `mapAbilityFinal` (824-880). That last step updates the caches and the reverse map, and for archon-only classes it also adds the skill to Archon at the required level (855-868). The interface declares eleven overloads of `addCharAbilityMapping` (`AbilityMapper.java:59-252`), and `CMAbleMap` adds several more. Each overload takes a different subset of the optional arguments (default proficiency, parm, secret flag, prerequisites, mask, cost overrides).

### 3. Qualification: who may learn a skill at a level

Three levels of query exist:

- **One holder.** `getQualifyingLevel(holder, checkAll, abilityID)` (`Libraries/CMAbleMap.java:1139-1160`) returns the level for one class, race or clan, falling back to `All` when `checkAll` is set. It returns -1 when the class level cap is below the required level (1154-1155).
- **A whole character.** `qualifyingLevel(MOB, Ability)` (1670-1728) looks at every class, the race, every clan and the personal entry. The winning source is the one with the largest margin, meaning current level minus required level (1682-1715). If no source is currently at or above its level, it returns any positive qualifying level (1716-1726), which lets callers report a future level.
- **Boolean forms.** `qualifiesByLevel` (1958-1985) is true when any source has reached its level. `qualifiesByTrajectory` (1988-2013) is true when any source will ever qualify, ignoring the current level. Clan sources also need a clan-benefit authority other than `CAN_NOT_DO` (1710, 1775-1776, 1906).

Example. A character is level 22 in a class that needs level 15 for a skill, and a race needs level 10 for it. The margins are 7 for the class and 12 for the race, so `qualifyingLevel` reports 10 and the race is the source. The race is therefore the qualifying ID in that case.

Callers include the `Qualify` list (`Commands/Qualify.java:128, 137, 158, 177`), `canBeLearnedBy` (`Abilities/StdAbility.java:2468-2597`), NPC teachers (`Behaviors/MOBTeacher.java:121, 451`), `Abilities/Properties/Prop_Trainer.java:172`, and many spells that gate enchanting on `qualifiesByLevel`.

### 4. Automatic grants

- **Creation.** `startCharacter` calls `grantAbilities` (`CharClasses/StdCharClass.java:1326-1333`).
- **Level-up.** `CoffeeLevels.java:1280` calls `curClass.grantAbilities(mob,false)` and then the race's grant.
- **`grantAbilities`** (`StdCharClass.java:970-1040`) has two paths.
  - With the `ALLSKILLS` security flag (974-1011), the mob receives every ability that has a qualifying level, at maximum proficiency, plus every expertise (1009-1010).
  - Otherwise (1017-1039), it collects mappings where `0 < qualLevel <= class level`, `autoGain` is true, `allowedToAutoGain` passes and the extra mask passes (1021-1028). Each is given with its default proficiency and parm (1039).
  - The ordinary path does not check prerequisites or the common-skill pool. Those checks live only in `canBeLearnedBy`.
- **Expertise on level-up.** "You have learned <name>." is shown when an expertise level increases (`CoffeeLevels.java:1335`).
- **De-levelling.** When a class level is lost, a skill is forgotten if its qualifying level equals the lost level, it is auto-gained by default, and `classOnly(mob, class, skill)` holds (`CoffeeLevels.java:466-471`). See problem 4 in the problems section.

### 5. Paying to learn: `canBeLearnedBy`

`StdAbility.canBeLearnedBy` (`Abilities/StdAbility.java:2468-2597`) runs these checks in order. The student-facing message is quoted.

| # | Check | Refusal message (student) |
|---|---|---|
| 1 | Training cost payable (`getTrainingCost`) | "You do not have enough @x1." (2476) |
| 2 | Student not refusing training | "You are refusing training at this time." |
| 3 | Some class qualifies (`qualifyingLevel` not below 0) | "You are not the right class to learn '@x1'." |
| 4 | Level reached, unless the class is leveless or levels are disabled | "You are not high enough level to learn '@x1'." |
| 5 | Intelligence at least 2 | "You are too stupid to learn new skills." |
| 6 | Intelligence against skill tier (below) | "You are not of high enough intelligence to learn level @x1 skills." |
| 7 | Not already known | "You already know '@x1'." |
| 8 | Teacher knows it and has at least 25% of the student's maximum (the message shown to the teacher; the student is told "@x1 isn't proficient enough to teach you '@x2'.") | "You aren't proficient enough to teach '@x1'." (2553) |
| 9 | Not in combat, not sleeping or sitting | "Not while you are fighting!" |
| 10 | Extra mask passes (`getApplicableMask`, 2581) | "You may not learn '@x1' at this time due to the @x2" |
| 11 | Prerequisites met (`getUnmetPreRequisites`, 2591) | "You must learn @x1 before you can gain @x2." |

`CommonSkill.canBeLearnedBy` (`Abilities/Common/CommonSkill.java:751-800`) adds the pool checks. "You have learned the maximum @x1 common skills, and may not learn any more." is shown when the remainder is zero (766-767). The remainder comes from `CMAbleComps.getSpecialSkillRemainder` (`Libraries/CMAbleComps.java:1312-1333`).

**Cost** (`getTrainingCost`, `StdAbility.java:226-258`):

1. A mapping override wins. A TRAIN override is used first, then a PRAC override, which is charged as practice points (232-240).
2. Otherwise a formula from config is evaluated with `@x1` = qualifying level (minimum 1) and `@x2` = player level (248-257).
3. The config key depends on the kind of ability. `CommonSkill` uses `COMMONCOST` (`CommonSkill.java:107-109`), `StdLanguage` uses `LANGCOST` (`StdLanguage.java:99-101`), and everything else uses `SKILLCOST` (`StdAbility.java:215-217`). Per-skill overrides are comma-separated inside the key. The fallback when a key is unset is 1 TRAIN (`core/CMProps.java:2085-2088`).
4. Defaults in `coffeemud.ini`: `COMMONCOST=1 TRAIN` (line 920), `SKILLCOST=1 TRAIN, Skill_WildernessLore 1 PRACTICE, PlayInstrument 3 PRACTICE` (921), `LANGCOST=3 PRACTICE` (922).

**Intelligence gate for higher tiers** (`StdAbility.java:2523-2526`). Let `q` be the qualifying level of the skill and `h` the highest qualifying level of the class's mappings. The student needs `INT + 6 >= ceil(BASEMAXSTAT * q / h)`, with `BASEMAXSTAT=18` (`coffeemud.ini:586`). Worked from that formula:

| Skill level `q` | Highest class level `h` | Minimum INT |
|---|---|---|
| 10 | 20 | 3 |
| 40 | 50 | 9 |
| 50 | 50 | 12 |

**Pool limits** (`coffeemud.ini:936-939`): `MAXCOMMONSKILLS=0` (no limit), `MAXCRAFTINGSKILLS=2`, `MAXNONCRAFTINGSKILLS=5`, `MAXLANGUAGES=3`. Classes change these: `CharClasses/Gypsy.java:92-95` multiplies languages by 5, and `CharClasses/Scholar.java:82-85` adds 3.

### 6. Prerequisites

**Data.** Each REQUIRES entry is an ability ID, optionally followed by a minimum proficiency in parentheses (`Skill_Write(75)`), an OR-group of comma-separated IDs, or a wildcard (`Specialization_*`). `makeAbilityMapping` stores them as a `DVector` (`Libraries/CMAbleMap.java:794-808`). Resolution is lazy. `fillPreRequisites` (1275-1311) calls `getOrSet` (1214-1273), which expands wildcards over every ability ID (1222-1241), splits OR-groups (1218), and maps expertise names to their IDs (1256-1262).

**Reverse index.** `addPreRequisites` (344-401) records prerequisite-to-dependent edges in `allows`. Expertise masks add edges too (`Libraries/ColumbiaUniv.java:1278, 1289, 1341`). `getClassAllowsList` (404-452) and `getAbilityAllowsList` (455-488) read this index. It drives the "what does this unlock" displays, including `Commands/ListCmd.java:6082`.

**Enforcement.** `getUnmetPreRequisites` (`Libraries/CMAbleMap.java:1383-1451`):

1. Choose the requirement set for this student (`getRawPreRequisites(MOB, Ability)`, 1609-1627). The personal entry comes first, then each current class, race and clan that has a mapping, then the current class.
2. Resolve names (`fillPreRequisites`).
3. A requirement is satisfied if the student holds the skill at the required proficiency (`fetchAbility`), or holds the expertise at the required stage (`fetchExpertise`).
4. Otherwise it is dropped, meaning it does not block, when the student does not qualify for it by level, the prerequisite is not All-qualified, and the target is not All-qualified (1404-1409 for single requirements, 1436-1441 for alternatives in an OR-group).
5. An OR-group that is emptied, either because an alternative was satisfied or because every alternative was dropped, is removed as satisfied (1446-1447).

In practice, for a class-specific skill, a prerequisite the student has not yet reached by level does not block learning. See problems, item 5.

**Messages.** `formatPreRequisites` (1471-1554) renders the list, for example `'Cooking'`, `(One of: 'A', or 'B')` and `'Blacksmithing' at 75%`. The Qualify list hides entries with unmet prerequisites only when the class sets `showThinQualifyList` (`StdCharClass.java:200-203` default false; `Commands/Qualify.java:128, 177`).

### 7. Proficiency: the number and how it rises

Every ability the mob holds has a proficiency from 0 to 100. The cap comes from `getMaxProficiency` (`Libraries/CMAbleMap.java:2332-2343`, which uses the personal entry, then the current class, then `All`).

**Use gain** (`Abilities/StdAbility.java:1810-1850`, `helpProficiency`):

- Only abilities the mob already has are considered (1814-1816).
- A random gate (1837): `round(sqrt(INT) * 34 * U) >= current proficiency + adjustment`. High proficiency therefore needs high intelligence.
- A chance roll (1839-1840): `getProfGainChance` (`Libraries/CMAbleMap.java:2885-2902`) evaluates `FORMULA_PROFGAIN`. The default is `(100 - (@x3 * 50)) * ( (@x1 + 1 - @x2) / ( (@x1 * 2) + (10 * @x2) ) )` (`coffeemud.ini:2520-2526`), where `@x1` = `LASTPLAYERLEVEL` (91, line 515), `@x2` = the required level, and `@x3` = 1 when fatigued, 0 otherwise. The roll succeeds when `rollPercentage() < chance`.
- If the mob does not qualify for the skill at all, the chance is 100.1 (`CMAbleMap.java:2889-2890`), so only the intelligence gate applies.

Chance of gaining one point, computed from the formula with `LASTPLAYERLEVEL=91` (worked from the code, not run in game):

| Required level | Rested | Fatigued |
|---|---|---|
| 1 | 47% | 24% |
| 10 | 29% | 15% |
| 20 | 19% | 9% |
| 30 | 13% | 6% |
| 50 | 6% | 3% |
| 70 | 2% | 1% |
| 91 | 0% | 0% |

### 8. Teaching and practice

**Teaching is a message.** The teacher's action creates a `CMMsg` of type `MSG_TEACH` (`Libraries/ColumbiaUniv.java:1182-1195`). Its text carries a `^<LEARN NAME="ID" />^>` tag, which `parseLearnID` reads back (922-946). The message is processed in two phases, as in the brief's flow:

- `okMessage` on the student (`MOBS/StdMOB.java:3236`) calls `canBeTaught` and can veto.
- `executeMsg` (`StdMOB.java:3473`) calls `handleBeingTaught`.

Player-to-player offers show "X wants to teach you Y. Is this Ok (y/N)?" with a 10-second timeout (`ColumbiaUniv.java:1126, 1138`). NPC teachers skip the prompt when the teacher's group leader is not a player (1095-1099).

**Ability teaching:**

- `canBeTaughtBy` (`StdAbility.java:2423-2466`): the teacher must know the skill and have at least 25% of the student's maximum (2446-2452).
- `canBeLearnedBy` (the student side) is covered in section 5.
- `teach` (`StdAbility.java:2751-2777`): the student pays the cost. The new ability's proficiency is the teacher's proficiency times (teacher WIS + student INT) / 100 (2762), capped at `PRACMAXPCT` (default 75) percent of the student's maximum (2760-2764). It is then raised to the mapping's default proficiency if that is higher.
- `handleBeingTaught` (`ColumbiaUniv.java:1036-1072`): the teacher's WIS and INT are each raised by 5 for the duration of the call (1045-1046), then stats are recomputed (1051). After teaching, the message value `add` is added to the new proficiency and clamped to the range 0 to 75 (1058-1071). The teacher earns 100 XP when both are players (1057).

**Practice** (`StdAbility.java:2670-2700` and `2795-2823`):

- Practice points are needed (`practicesToPractice`, 260-267, default 1, overridable).
- The student's proficiency must be below 75% of maximum (2717-2722), and the teacher must be at least 25% (2727).
- `practice` spends the points. Gain is `round(25 * (teacher WIS + student INT) / 36)` (2809-2811), capped at `PRACMAXPCT` of maximum and at the teacher's own proficiency (2812-2816).

Worked figures from these formulas: a teacher at proficiency 60 with WIS 16, and a student with INT 14, gives a starting proficiency of 18. A teacher and student with WIS 12 and INT 12 get 17 proficiency per practice point. A teacher at proficiency 20 cannot teach a student whose maximum is 100, because 20 is below the 25 threshold.

### 9. Mana compounding

`MANACOMPOUND` (`coffeemud.ini:879-893`, empty by default) is a comma-separated list of rules: `<ticks> <percent>%` or `<ticks> <amount>`, with optional `MOBMASK=` and `ABILITYMASK=` zapper masks. The examples in the file are `5 5%` with an ability mask on Alchemy, and `3 25%`.

- Parsing: `loadCompoundingRules` (`Libraries/CMAbleMap.java:2787-2882`).
- Selection: `getCompoundingRule` (2498-2557). An ability-masked rule that matches wins. Otherwise the unmasked rules apply. Among those, a mob-masked rule wins, and the unmasked one is the fallback.
- Use (`Abilities/StdAbility.java:2001-2040`): if the skill was last used within `ticks`, each earlier use in the window adds the flat amount and then the percentage of the running cost. A negative amount refuses the use with "You can't do that again just yet."

### 10. Secrecy, masks and availability

- `SecretFlag` (`AbilityMapper.java:1434-1447`): `SECRET` is never shown. `PUBLIC` is always shown. `MASKED` stays hidden until the mask passes.
- `getSecretSkill(MOB, String)` (`Libraries/CMAbleMap.java:2143-2176`): a personal mapping decides first. Otherwise any `PUBLIC` mapping wins, and a `MASKED` result is revealed when `CMLib.masking().maskCheck` passes (2171-2172).
- `getApplicableMask` (1648-1667) picks the first mask from the personal entry, then the qualifying holders, then the current class. `getCommonExtraMask` (1353-1380) gives the general mask.
- `availableToTheme` (1035-1051) checks whether some class holder with the skill belongs to a theme, or whether the skill is in `All`.
- `getAvailabilityList` (2732-2779) builds the "which classes" display. It collapses to "Numerous Classes" or "+Other Classes" when more than `abbreviateAt` classes qualify.

### 11. Expertises: definition format and generation

The format is given in the header of `resources/skills/expertises.txt` (lines 1-11):

`CODE@x1=Name @x2,LEVELS,FLAGS,SKILL MASK,EXTRA LIST MASK,TRAIN MASK,PRACS,TRAINS,QPS,EXP,TIME`

`HELP_` and `DATA_` lines attach to the preceding family. The parser is `confirmExpertiseLine` (`Libraries/ColumbiaUniv.java:642-844`):

- The row must have exactly 11 comma-separated fields (752).
- `FLAGS` must name at least one `XType` (761, 777-782). The flags are `LEVEL`, `TIME`, `MAXRANGE`, `LOWCOST`, `XPCOST`, `LOWFREECOST` and `X1` to `X6` (`ExpertiseLibrary.java:421-435`).
- `MUDZapper.getAbilityEduReqs` (`Libraries/MUDZapper.java:3830-3905`) reads the skill mask and returns the skills the family touches (766).
- The list mask is the skill mask plus the extra list mask (772). The final mask is the train mask (773).
- Stage generation (793-819): for each level `l` from 1 to `LEVELS`, `@X1` in the code becomes the arabic number and `@X2` becomes a Roman numeral. `@x1` and `@x2` are substituted in the name and both masks. For `l > 1`, the list mask gets `-EXPERTISE "+<previous stage>"` prepended, unless it already mentions `-EXPERT` (803-808). Arithmetic inside braces is evaluated by `expertMath` (474-486, 809-810).
- Costs: fields 6 to 10 fill `costs[0..4]` (775). `addDefinition` uses only practices, trains, QPs and XP (75-87). The TIME field is read but dropped.
- Usage maps: for each flag and each skill the family touches, `completeUsageMap` records the base family, and `completeUsageMaps` keeps the list of families (825-840).

`createNewExpertiseDefinition` (1197-1437) builds the definition object. It holds the stage name, base name, stage names from `DATA_`, the list mask (who can see the expertise in training lists), the final mask (who may buy it), the cost managers, and the set of `XType` flags. Compiling a mask also registers its skill requirements in `allows` (1278, 1289, 1341). Stage numbers are recovered from the ID suffix by `getStageNumber` (415-426), which understands both arabic and Roman forms.

### 12. Expertises at runtime

**Ownership.** `StdMOB.addExpertise` keeps the highest stage per base family (`MOBS/StdMOB.java:5537-5548`). `fetchExpertise` returns (base, stored level) when the requested stage is at or below the stored level (5568-5581). Adding or removing an expertise clears the mob's ability usage cache.

**Lists and qualification.**

- `myListableExpertises` (`ColumbiaUniv.java:357-384`) checks list masks.
- `myQualifiedExpertises` (327-354) checks both masks. Per-player extras (from achievements) override the catalogue entry.

**Teaching an expertise.**

- `canBeTaught` (949-1033) refuses if the student already has it (995), is not fully qualified (1004), or cannot pay (1019).
- `handleBeingTaught` (1074-1080) spends the costs, adds the expertise, and gives the teacher 100 XP when both are players.

**Effect on skills.**

- Skill code reads its X-levels through `StdAbility.expertise` (`Abilities/StdAbility.java:400-416`). That method returns 0 unless the ability is an auto-effect, can be uninvoked, or is auto-invoked (402-405).
- It then adds circumstantial adjustments keyed `X<TYPE>+<skill ID | ACODE | DOMAIN | *>` through `charStats.getAbilityAdjustment` (408-413).
- The accessors `getX1Level` through `getXPCOSTLevel` (420-472) each map a name to one `XType`.
- `getExpertiseLevelCalced` (`ColumbiaUniv.java:618-639`) returns the stored level of the first applicable family the mob owns. `getExpertiseLevelCached` (387-413) caches the result per mob and per ability, and adds class bonuses through `CharClass.addedExpertise` (`ColumbiaUniv.java:406-407`).
- The default class hook returns 0 (`StdCharClass.java:959-961`). `CharClasses/Scholar.java:261-265` returns +15 XPCOST for `ScrollScribing`.

**Consumers found** (by grep and reading, not exhaustive):

- `Abilities/Common/Studying.java:408`: each LEVEL stage adds 5 to the taught proficiency.
- `Abilities/Prayers/Prayer_AnimateZombie.java:125-146`: `getUndeadLevel` uses LEVEL and X1 from `getHighestListableStageBySkill` to scale the raised corpse's level. The other `Prayer_Animate*` classes use the same pattern.
- `CharClasses/Gaoler.java:462`: the LEVEL expertise of one skill adds to an XP award.
- Crafting. `Abilities/Common/CraftingSkill.java:121-141` maps stage-key prefixes (ADVN, LITE, DURA, QUAL, LTHL, CNTR, FORT, IMBU, VIGO, RUSH) to `XType`s. `EnhancedCraftingSkill.java:283-294` matches expertise IDs by prefix. The enhancement list and the craft menu are built at 373-410 and 465-520.

### 13. Messages the player sees

| Source | Message |
|---|---|
| `Abilities/StdAbility.java` (learn) | "You do not have enough @x1." / "You are not the right class to learn '@x1'." / "You are not high enough level to learn '@x1'." / "You are too stupid to learn new skills." / "You already know '@x1'." / "You must learn @x1 before you can gain @x2." |
| `Abilities/StdAbility.java` (teach) | "You aren't proficient enough to teach '@x1'." / "You are not proficient enough to teach '@x1'" / "You can't learn any more about '@x1' except through diligence." |
| `Libraries/ColumbiaUniv.java` (teach) | "@x1 wants to teach you @x2. Is this Ok (y/N)?" / "You failed to understand @x1." / "Training for that expertise requires @x1." / "@x1 does not yet fully qualify for the expertise '@x2'." |
| `Libraries/CoffeeLevels.java` | "You have learned <expertise>." (1335) |

## Content worth mining

### Expertise families (`resources/skills/expertises.txt`)

The file has 205 family lines, plus 262 `HELP_` and 23 `DATA_` lines. 185 families have 10 levels and 20 have 3 levels. Each family costs 1 train per stage in 185 cases, 2 trains in 16, and 20 trains in 4. Flag usage per line, where a line may carry several flags: LEVEL 91, LOWCOST 42, X1 28, TIME 20, X4 14, MAXRANGE 13, X2 12, X3 11, XPCOST 3, LOWFREECOST 1, X5 1, X6 1.

Samples, all read directly from the file. Gates are the class level in the list mask and the stat in the final mask, shown as they are written, where n is the stage number:

| Family | Name pattern | Levels | Flag | Skills touched (skill mask) | Extra list gate (beyond the skill mask) | Final (train) mask |
|---|---|---|---|---|---|---|
| `CAUTIOUS` | Cautiously N | 10 | X1 | Thief_Peek, Thief_Robbery, Thief_SlipItem, QuickSwipe, Thief_Steal, Thief_Swipe | class level 19+5n | WIS 12+n |
| `ARMOROPT` | Armor Optimizing N | 10 | LEVEL | Fighter_ArmorTweaking, ARMOR_USE flag | class level 28+5n | CON 12+n |
| `SHIELDUSE` | Shield Using N | 10 | LEVEL | SHIELD_USE flag | class level 26+5n | STR 12+n |
| `ALERT` | Vigilantly N | 10 | LEVEL | ALERT flag | class level 15+5n | WIS 12+n |
| `STEALTH` | Stealthy N | 10 | LEVEL | STEALTHY flag | class level 10+5n | DEX 12+n |
| `POISONING` | Poisoning N | 10 | LEVEL | Apothecary, POISONING flag | class level 10+5n | INT 12+n |
| `ARMORFOCUS` | Armor Focus N | 10 | LEVEL | Specialization_Armor | class level 24+5n | WIS 10+n |
| `HOMECOOKING` | Home Cooking N | 3 | X1 | Cooking, Baking, Distilling, FoodPrep | class level 10+5n | WIS 12+n |
| `EXPCRAFT` | Wise Crafting N | 10 | XPCOST | Alchemy, Herbalism, ScrollScribing and others | class level 26+5n | CHA 12+n |
| `RUSHCRAFT` | Rushed Crafting N | 3 | X6 | crafting-domain common skills (via a skill-flag mask) | none | DEX 5+4n |

Notes on the table:

- The `Specialization_*` family is large. Fifty-nine skill-mask references point at `Specialization_*` skills (for example `ARMORFOCUS`, `SHIELDFOCUS`, `SHIELDSTRIKE`, `AXESTRIKE`).
- `RUSHCRAFT` has no extra list gate, so listing depends only on its skill-flag mask. `LITECRAFT` (X1, 3 levels, 20 trains per stage) has the list-mask fragment `-CLASS +Archon`. Read literally, that restricts the family to the Archon class. This may be deliberate, but it is worth checking.

### The shared skill list (`resources/skills/allqualifylist.txt`)

The file is short and read in full. `[EACH]` has 8 entries: `Bandaging`, `CaravanBuilding` (requires `Wainwrighting(100)` and `Construction(100)`), `Common` (`PROF=100 AUTOGAIN`), `PlayInstrument` (mask `+WIS 5`), `SmokeRings` (mask `+CHA 5`), `UnderCommon`, `FireBuilding` (level 3, autogain), and `Searching` (level 4).

`[ALL]` has 76 entries, by qualifying level: 1: 20, 2: 7, 3: 4, 4: 7, 5: 10, 6: 2, 7: 1, 8: 2, 10: 12, 12: 2, 15: 4, 18: 2, 20: 1, 29: 2.

Prerequisite chains that make a usable tech tree:

- Cooking leads to Baking, FoodPrep and Distilling.
- Blacksmithing leads to Armorsmithing (`Blacksmithing(75)`), JewelMaking (`Blacksmithing Pottery`), LockSmith, and Weaponsmithing (`Blacksmithing(75) Specialization_*`).
- Carpentry leads to Boatwright, Construction, Siegecraft and Wainwrighting. Fletching requires `Specialization_Ranged` and Carpentry.
- Sculpting leads to Masonry and ScrimShaw. Farming leads to Composting and Landscaping.
- `MasterFloristry` requires `Floristry(100)`, and `MasterHerbology` requires `Herbology(100)`.
- Attribute masks gate many crafts: Blacksmithing `+STR 10`, Carpentry `+CON 10`, Tailoring `+DEX 10`, Weaving `+WIS 10`, Taxidermy `+INT 12`, Fishing and Hunting `+WIS 8`.

### Class-side mapping example

`CharClasses/Pirate.java:151-173` is a compact example of a class's ladder, with levels from 12 to 17, two prerequisite-bearing skills, and one cost override. It is a good template for the content format of a Python SUD.

### Config examples

`coffeemud.ini:891-892` gives `MANACOMPOUND` examples: `5 5%` with an Alchemy ability mask, and `3 25%`. `coffeemud.ini:917` gives the cost-override syntax: `Spell_Sleep 50*@x1 GOLD`.

## Notable design ideas

- **One qualification query across several sources.** A skill can come from a class, a race, a clan government or a personal grant. The reported source is the one with the largest margin above its level (`CMAbleMap.java:1682-1715`). This means the player's "why can I learn this" answer comes from the best source, which is a neat rule.
- **Two pool tiers.** `[EACH]` gives each class its own copy. `[ALL]` is a shared pool, subject to per-category caps (`coffeemud.ini:936-939`). The split is data, so a designer can move a skill between them.
- **Separate visibility from acquisition.** Expertises have a list mask (may see it) and a final mask (may buy it). A player can see a training option they cannot yet afford or qualify for, and that is a good discoverability mechanic.
- **Expertise families from one template line.** `@X1` and `@x1` generate ten stages, stage chaining is automatic, and the name, masks and costs all scale from the same line.
- **A fixed set of modifier dimensions.** `XType` gives twelve numbers that skill code interprets for itself. The data header documents what each means for each skill, so one family can mean different things in different skills.
- **Teacher-capped learning.** The starting proficiency is the teacher's proficiency scaled by (teacher WIS + student INT) / 100. A teacher must be at least 25% proficient to teach. This makes NPC and player teachers matter.
- **Practice scaled by stats.** Each practice point gives `25 * (WIS + INT) / 36`, so high-stat pairs learn faster.
- **Compounding as anti-spam.** Repeated use within a tick window costs more, and a negative amount forbids it. This is a rotation mechanic without a hard cooldown.
- **Formula-driven gain.** The proficiency-gain chance is a config string, compiled once (`CMAbleMap.java:72`), with the level and fatigue as variables.

## Problems and cruft

Items are ordered by how much they matter. "Confirmed" means the code was read and the flaw is clear. "Probable" means the behaviour depends on runtime conditions that were not run.

1. **Confirmed. Complex prerequisite keys overwrite each other** (`Libraries/CMAbleMap.java:354-359`). The uniqueness loop calls `allows.containsValue("*"+ID2)`, which looks at values, but the keys are `"*"+ID2`. The loop therefore almost never runs, and a second complex requirement for the same ability replaces the first in `allows`. The effect is limited to the reverse listings, because the enforcement path reads the mapping's own list.
2. **Confirmed. Wrong index in OR-group resolution** (`Libraries/CMAbleMap.java:1258, 1262`). The expertise branches call `orset.set(0, ...)` instead of `orset.set(o, ...)`. An expertise alternative in any position other than 0 is left unresolved, and the entry at position 0 is overwritten.
3. **Probable. Map mutation during iteration** (`Libraries/CMAbleMap.java:459-479`). `getAbilityAllowsList` iterates `allows.keySet()` and calls `addPreRequisites` inside the loop (472). That can add new keys. `allows` is an `SHashtable` whose `keySet` is the key set of a `java.util.Hashtable` (`core/collections/SHashtable.java:218-220`), which is fail-fast. A `ConcurrentModificationException` is likely the first time wildcard requirements are resolved. This was not run.
4. **Confirmed. `classOnly(MOB, ...)` ignores the class in its loop** (`Libraries/CMAbleMap.java:1023-1030`). The condition in the loop tests `!C.ID().equals(classID)`, but the other clauses do not depend on `C`. The method therefore returns false whenever the mob has any class other than `classID` and `classID` has the skill. The only caller is de-levelling (`Libraries/CoffeeLevels.java:470`), so a multi-class character who loses a level may keep a skill that only the lost class granted.
5. **Confirmed. Prerequisites are waived below level** (`Libraries/CMAbleMap.java:1404-1409, 1436-1441, 1446-1447`). A class-specific skill ignores a prerequisite the student has not yet reached by level, and an OR-group whose alternatives are all dropped counts as satisfied. The code comment "why are you even trying?" suggests this is deliberate, but it means a level-10 student can learn a skill that requires a level-15 skill, which may be unintended.
6. **Confirmed. Two prerequisite lookups disagree** (`Libraries/CMAbleMap.java:1316-1324` and `1385`). `getCommonPreRequisites` falls back to the `All` list when the mob's own mapping has no prerequisites. `getUnmetPreRequisites` uses `getRawPreRequisites(MOB, A)`, which returns the first non-null list (1617-1624). An empty class list is non-null, so it hides the `All` list's requirements on the enforcement path.
7. **Confirmed. Unqualified skills always pass the chance roll** (`Libraries/CMAbleMap.java:2889-2890`). A skill the mob is not qualified for gets a 100.1% chance. Only the intelligence gate applies (`Abilities/StdAbility.java:1837`).
8. **Confirmed. Hard-coded cap that ignores the config** (`Libraries/ColumbiaUniv.java:1063-1064`). Teaching clamps the added proficiency to 75. `teach` uses `PRACMAXPCT` (`StdAbility.java:2760`), which defaults to 75 but can be changed.
9. **Confirmed. Temporary stat hack** (`Libraries/ColumbiaUniv.java:1045-1046, 1051`). The teacher's WIS and INT are raised by 5 for the duration of the teach call, then recomputed by `recoverCharStats`. This works, but it is a side effect buried in a library method.
10. **Probable. Bonus keyed to the wrong ability** (`Abilities/StdAbility.java:408-413`). `expertise()` looks up the expertise and the circumstantial adjustments using `A` for the expertise but `this` (`ID()` and `classificationCode()`) for the adjustments. `Abilities/Common/Studying.java:408` passes a different ability (`mTeachingA`), so the adjustments would use the Studying ability's ID. This needs a runtime check.
11. **Confirmed. Dead code with an ordering trap.** `getConfirmedDefinition`, `getConfirmedExpertiseLevel` and the singular `getApplicableExpertise` have no live callers. The only reference is a commented call in `Commands/Expertises.java:58`. The index arithmetic in `getConfirmedExpertiseLevel` (`ColumbiaUniv.java:603`) assumes stage codes are in numeric order, but `getStageCodes` returns a `STreeMap` in natural string order (`ColumbiaUniv.java:48, 489-517`). So `EXPERTISE10` would sort before `EXPERTISE2`.
12. **Confirmed. TIME cost read but ignored** (`ColumbiaUniv.java:75-79, 775`). The file documents a `#TIME` field, and `addDefinition` drops it.
13. **Confirmed. Redundant call** (`ColumbiaUniv.java:766-771`). The error branch calls `getAbilityEduReqs` a second time and then discards the result.
14. **Confirmed. `getParent()` is always null** (`ColumbiaUniv.java:1211`), though the interface describes it as the base definition.
15. **Confirmed. Reloading on each new holder** (`CMAbleMap.java:511`). `getAllQualifiesMap(null)` is evaluated, which reads the file again, before `handleEachAndClassAbility` decides whether the cached EACH list is needed (884).
16. **Confirmed. Unguarded last-file access** (`CMAbleMap.java:2701`). `saveAllQualifysFile` takes the last file in the list with a TODO and no length check.
17. **Confirmed. Unused enum field** (`Abilities/Common/CraftingSkill.java:124-128, 139, 141`). `EnhancedExpertise.flag` is not read anywhere in `Abilities/Common`. Crafting matches expertises by prefix alone (`EnhancedCraftingSkill.java:283-294`), so the `XType` mapping there is decorative.
18. **Confirmed. Doc mismatch in the interface.** `AbilityMapper.java:926-930` describes `costOverrides` as indexed by "the Cost enum". It is `AbilCostType`.

## Takeaways for a Python SUD

**Borrow:**

- **A single requirement object.** Combine level, prerequisites (with minimum proficiency and OR-groups) and an optional predicate in one record per holder-skill pair. The record should carry the `secret` state too. Keep the field set from `AbilityMapping` but drop the holder dimension for a single player (see below).
- **Two-list visibility.** Keep separate predicates for "shows as an option" and "can buy it". The game can then show a locked option with its unmet requirement.
- **Teacher-capped learning.** Starting proficiency = teacher proficiency x (teacher WIS + learner INT) / 100, capped at 75% of maximum, with a 25% teacher threshold. NPC teachers become a real progression route.
- **Expertise families from one template.** A family is a name pattern, a stage count, a flag set and a list of skills. Generate stages from it, and chain each stage on the one before.
- **Formula-driven chance.** Keep the gain chance as a configurable expression of level, required level and state.

A minimal sketch of the data shape, to show the intent (this is a suggestion, not CoffeeMud code):

```python
from dataclasses import dataclass, field
from typing import Callable, Literal

Prereq = tuple[tuple[str, ...], int]        # (alternatives, minimum proficiency)

@dataclass(frozen=True)
class SkillGate:
    skill: str
    level: int                              # character level at which it unlocks
    auto_gain: bool = False
    start_prof: int = 0
    max_prof: int = 100
    prereqs: tuple[Prereq, ...] = ()
    allowed: Callable[["Player"], bool] = lambda p: True
    visibility: Literal["public", "secret", "until_allowed"] = "public"

def expertise_stages(code: str, name: str, levels: int, gate) -> list[dict]:
    stages = []
    for n in range(1, levels + 1):
        stages.append({
            "id": f"{code}{n}",
            "name": f"{name} {n}",
            "requires": [f"{code}{n - 1}"] if n > 1 else [],
            "gate": gate(n),                # e.g. lambda n: {"class_level": 10 + 5 * n}
        })
    return stages
```

**Simplify:**

- Drop the holder dimension. A single player has one class at a time, and the race and clan sources add little. Keep one list of gates for the current character.
- Drop archon handling, clan-benefit checks, theme availability and the mana-compounding config language. Keep compounding only if rotation matters for the design, and then implement it as a plain cooldown-with-penalty function.
- Replace the zapper mask language with Python predicates. The masks only need to express level, stat and class-level tests, so a dictionary or a lambda is enough.
- Keep the expertise file's format idea but load it from YAML or TOML, with the stage generator written in Python.

**Drop:**

- Common-skill pool caps and the EACH/ALL split. They exist to balance many players sharing a class list, and a single-player game has no such pressure.
- The silent prerequisite waiver. For a single player, a prerequisite the character cannot reach should block with a message such as "You need level 15 before you can learn this."

**Single-player rethink:**

- Teaching XP rewards players only. In the SUD, reward the player for finding and using a teacher, not for teaching someone else.
- Keep the message builders. `formatPreRequisites` and the refusal messages are a good model for telling the player exactly what is missing, and they are the most reusable text in this bucket.

## Coverage

**Read in full (4 of 4 assigned Java files):**

- `com/planet_ink/coffee_mud/Libraries/interfaces/AbilityMapper.java` (1-1501)
- `com/planet_ink/coffee_mud/Libraries/CMAbleMap.java` (1-2912)
- `com/planet_ink/coffee_mud/Libraries/ColumbiaUniv.java` (1-1438)
- `com/planet_ink/coffee_mud/Libraries/interfaces/ExpertiseLibrary.java` (1-619)

**Data read:**

- `resources/skills/allqualifylist.txt`, read in full.
- `resources/skills/expertises.txt`: the header, a sample of families read directly, and all 205 definition lines analysed by script for field counts, flags, levels and costs.

**Read in excerpts (cross-bucket, for interaction only):**

- `Abilities/StdAbility.java` (215-300, 398-475, 1737-1860, 1985-2050, 2423-2600, 2670-2823)
- `CharClasses/StdCharClass.java` (200-228, 959-1040, 1322-1336, 1442-1446)
- `MOBS/StdMOB.java` (2459-2470, 3236, 3412-3480, 5537-5581)
- `Libraries/CoffeeLevels.java` (460-480, 1183-1200, 1270-1350)
- `Libraries/MUDZapper.java` (1673-1700, 3830-3905, 4960-4990)
- `Libraries/CMAbleComps.java` (1312-1350)
- `Abilities/Common/CommonSkill.java` (105-110, 751-800), `Abilities/Common/CraftingSkill.java` (112-145), `Abilities/Common/EnhancedCraftingSkill.java` (283-294, 373-420, 465-520), `Abilities/Common/Studying.java` (400-415), `Abilities/Prayers/Prayer_AnimateZombie.java` (124-146), `CharClasses/Pirate.java` (101-175), `CharClasses/Scholar.java` (255-266), `CharClasses/Gaoler.java` (455-470), `Commands/Qualify.java` (125-177), `Commands/Practice.java` (128-130), `core/CMProps.java` (2073-2092, 2980-2986, 3108), `coffeemud.ini` (514-515, 586, 814-823, 879-939, 2512-2530).
- Found by grep only: `Libraries/Achievements.java` (8546, 8610), `Races/StdRace.java` (694, 757, 1905), `Common/DefaultClanGovernment.java` (1321), `CharClasses/GenCharClass.java` (867), `Behaviors/MOBTeacher.java` (121, 451), `Abilities/Properties/Prop_Trainer.java` (172), `Commands/ListCmd.java` (6082), `Commands/Teach.java` (119-123), `CharClasses/Gypsy.java` (92-95).

**Skipped:** the remaining callers of the mapping API (about 230 files reference `CMLib.ableMapper()`), the spell and skill bodies beyond the call sites cited, and the mask scanner's full body.
