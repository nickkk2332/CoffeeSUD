# Abilities: base classes and their siblings

> Part of the CoffeeMud NEWOVERVIEW. Bucket: `b058` (Abilities (base classes)) · 19 files · 10871 lines of Java.

## Summary

Every skill, spell, chant, prayer, song, trap, language, disease and property in CoffeeMud is an `Ability`, and `StdAbility` (3,018 lines) gives them one shared lifecycle: a legality check (alive and awake, cooldown, bound, compounding rule), a price in mana, movement or hit points, a proficiency roll, and a timed effect object that is attached to the target, ticked and removed. The same base class defines the tuning vocabulary the rest of the game relies on: expertise levels (`LEVEL`, `TIME`, `LOWCOST`, `X1`–`X5` and others), a classification word (type plus domain), quality codes (malicious or beneficial), and key-based adjustments such as `PROF+<DOMAIN>`. Sibling classes add a data-driven ability built from stored fields (`GenAbility`), runtime composition (`ExtAbility`), a proxy that overrides an existing ability (`GenTweakAbility`), a stub (`ThinAbility`), and a planar-travel family (`StdPlanarAbility`) that builds instance areas from a 37-plane table. For a single-player text game the template-versus-effect split, the fixed order of checks in `invoke`, and the proficiency model are worth borrowing; the other-player hooks can go.

## Key files and types

All paths are relative to the CoffeeMUD folder. Nothing was executed; every behaviour below comes from reading the code.

| File or class | Role | Lines |
|---|---|---|
| `com/planet_ink/coffee_mud/Abilities/StdAbility.java` | Base of every ability: legality checks, costs, proficiency, targeting, effect timers, uninvoke, teaching and practice | 3018 |
| `com/planet_ink/coffee_mud/Abilities/interfaces/Ability.java` | The `Ability` contract and its constants (`ACODE_*`, `DOMAIN_*`, `QUALITY_*`, `CAN_*`, `FLAG_*`, `USAGE_*`, `COST_*`, `USAGEINDEX_*`) | 1109 |
| `com/planet_ink/coffee_mud/Abilities/StdPlanarAbility.java` | Planar-shift base: plane table, instance areas, planar tick, outcome tiers, repeat-visit escalation | 2235 |
| `com/planet_ink/coffee_mud/Abilities/GenAbility.java` | Data-driven ability: 37 stat codes per ability ID, XML save, script and message hooks, damage and heal expressions | 1516 |
| `com/planet_ink/coffee_mud/Abilities/GenTweakAbility.java` | `java.lang.reflect.Proxy` handler that overrides methods of an existing ability ID | 667 |
| `com/planet_ink/coffee_mud/Abilities/ExtAbility.java` | Composable ability: pluggable stats affector, message listener, tickable and invoke callbacks | 202 |
| `com/planet_ink/coffee_mud/Abilities/ThinAbility.java` | Minimal `Ability` (not a `StdAbility`): always on, zero cost, cannot be uninvoked | 665 |
| `com/planet_ink/coffee_mud/Abilities/interfaces/PlanarAbility.java` | Planar contract, `PlanarVar` enum (36 keys), `PlanarSpecFlag` enum (3 values) | 341 |
| `com/planet_ink/coffee_mud/Abilities/interfaces/ItemCraftor.java`, `CraftorAbility.java` | Crafting-capable abilities: recipe lookup, craftable level range, craft calls | 189, 86 |
| `com/planet_ink/coffee_mud/Abilities/interfaces/Trap.java` | Trap contract: set, spring, reset, disable, bomb | 184 |
| `com/planet_ink/coffee_mud/Abilities/interfaces/Language.java` | Language contract: translation tables, who is speaking it | 139 |
| `com/planet_ink/coffee_mud/Abilities/interfaces/DiseaseAffect.java`, `HealthCondition.java`, `LimbDamage.java`, `MendingSkill.java` | Condition, spread and injury contracts | 95, 47, 86, 48 |
| `com/planet_ink/coffee_mud/Abilities/interfaces/ExtendableAbility.java`, `ItemTicker.java`, `TriggeredAffect.java` | Hooks for `ExtAbility`; effects held by items; trigger masks | 84, 80, 80 |

## How it works

### 1. Object model: template and effect

`StdAbility` keeps this per-instance state (`com/planet_ink/coffee_mud/Abilities/StdAbility.java:50-60`):

- `proficiency`: 0 to 100; `setProficiency` caps at 100 (`StdAbility.java:632-638`).
- `miscText`: a free-form parameter string. Planar abilities store the plane name here.
- `invoker` (the MOB that started it) and `affected` (the `Physical` it sits on).
- `canBeUninvoked`, `unInvoked`, `tickDown` (`-1` means no timer; `makeLongLasting()` sets `Integer.MAX_VALUE`, `StdAbility.java:2825-2828`).
- `isAnAutoEffect`, `savable`, and `lastCastHelp` (time of the last proficiency gain, used as a throttle).

The design is two objects per cast. The ability in a MOB's list is the *template*. A cast that persists calls `copyOf()` (a shallow `clone()`, `StdAbility.java:1423-1438`) and the copy becomes the *effect*, carrying its own timer and target. `beneficialAffect` and `maliciousAffect` both do this: copy, set `canBeUninvoked=true`, then `startTickDown` (`StdAbility.java:2185-2208` and `2341-2349`). `startTickDown` sets `savable=false` so effect copies are never written to the save file (`StdAbility.java:703`). Templates therefore hold no per-target state. Subclasses that add fields must fix references in `cloneFix` (`StdAbility.java:1419-1421`).

### 2. Encodings used everywhere

- **Classification** (`classificationCode()`): the low 5 bits are the type (`ACODE_SKILL=0` through `ACODE_TECH=13`, mask `ALL_ACODES=31`). Bits from 5 upward hold the domain: `DOMAIN_* = n<<5` for n = 1..82, mask `255<<5` (`Abilities/interfaces/Ability.java:670-701`, `721-888`).
- **Quality** (`abstractQuality()`): `QUALITY_MALICIOUS=0`, `INDIFFERENT=1`, `OK_SELF=2`, `OK_OTHERS=3`, `BENEFICIAL_SELF=4`, `BENEFICIAL_OTHERS=5` (`Ability.java:1083-1093`).
- **Targeting masks**: `CAN_MOBS=1`, `CAN_ITEMS=2`, `CAN_AREAS=4`, `CAN_ROOMS=8`, `CAN_EXITS=16` (`Ability.java:1100-1108`). The default `canTargetCode()` and `canAffectCode()` are all five (`StdAbility.java:498-520`). The `Spell` base narrows `canTarget` to `CAN_MOBS` and `canAffect` to 0 (`Abilities/Spells/Spell.java:60-69`).
- **Usage**: `USAGE_MANA=1`, `USAGE_MOVEMENT=2`, `USAGE_HITPOINTS=4` (`Ability.java:620-626`). The default is mana (`StdAbility.java:299-302`).
- **Cost value** (`consumed`): a plain number is absolute. `COST_ALL` (`Integer.MAX_VALUE`) means all of the pool. `COST_PCT` (`Integer.MAX_VALUE-100`) and above encode a percentage as `COST_ALL - n`. `COST_NORMAL` (`-1`) defers to configuration (`Ability.java:651-655`, `StdAbility.java:1614-1620`). `StdPlanarAbility.overrideMana()` returns `COST_ALL - 90`, meaning 90% of the pool (`StdPlanarAbility.java:69-72`).
- **Flags**: a 64-bit mask, `FLAG_BINDING=1` up to `FLAG_POLYMORPHING=2^41` (`Ability.java:946-1032`), with names in `FLAG_DESCS` (`Ability.java:1036-1080`). Tags include `FLAG_TORTURING` and `FLAG_MINDALTERING`. Their use was not traced.

### 3. Expertise levels and key-based adjustments

**Expertise levels.** `expertise()` asks `CMLib.expertises().getExpertiseLevelCached(...)` (implemented in `com/planet_ink/coffee_mud/Libraries/ColumbiaUniv.java:387`), then adds circumstantial character-stat adjustments named `X<TYPE>+<KEY>` (`StdAbility.java:400-417`). The `XType` enum (`Libraries/interfaces/ExpertiseLibrary.java:421`) has twelve values: LEVEL, TIME, LOWCOST, LOWFREECOST, MAXRANGE, XPCOST and X1–X6. StdAbility reads X1–X5 and has no accessor for X6. Each type has a fixed meaning in StdAbility:

| Type | Effect | Where |
|---|---|---|
| `LEVEL` | +1 power level per point (`adjustedLevel`); proficiency check +2 per point plus half a point; cooldown multiplier −5% per point | `StdAbility.java:920`, `1467-1478`, `278-281` |
| `TIME` | Durations +20% of base per point (`addedTickTime`); cooldown −10% per point | `StdAbility.java:640-643`, `709`, `281` |
| `LOWCOST` | Cost −5% of raw cost per point, capped at half the raw cost | `StdAbility.java:1621-1631` |
| `LOWFREECOST` | Flat cost reduction of half a point per point (integer division) | `StdAbility.java:1632-1634` |
| `MAXRANGE` | Max range +20% of base per point, rounded up | `StdAbility.java:482-490` |
| `XPCOST` | Experience loss −5% per point | `StdAbility.java:474-480` |
| `X1`–`X5` | Free for ability code. GenAbility uses them in its damage and affect expressions | `GenAbility.java:625-636`, `729-745` |

**Key-based adjustments.** Character-stat adjustments are looked up by a key and summed, not overridden. For proficiency the terms are `PROF+<ID>`, `PROF+<ACODE>`, `PROF+<DOMAIN>` and `PROF+*` (`StdAbility.java:1457-1464`). Duration, level, cost and expertise use the same scheme (`StdAbility.java:746-752`, `892-895`, `1644-1673`, `410-413`). So a single "+3 to Abjuration" effect is one adjustment on `PROF+ABJURATION`.

### 4. Invocation: from command to effect

**Step 1, queueing (StdMOB).** A typed skill enters the MOB's command queue. `StdMOB.calculateActionCost` asks the ability for `checkedCastingCost` (`MOBS/StdMOB.java:2291-2312`). That returns `castingTime` out of combat, or `combatCastingTime` when in combat or when the ability is malicious (`StdAbility.java:153-175`). Config defaults are `DEFABLETIME=0.10` and `DEFCOMABLETIME=1.0` (`coffeemud.ini:951-952`; parsed at `core/CMProps.java:3101-3102` as hundredths). While queued, `StdMOB` calls `EnglishParser.preInvokeSkill` (`MOBS/StdMOB.java:2223-2225`), which calls `Ability.preInvoke` (`Libraries/EnglishParser.java:1324`). `preInvoke` cancels the skill with "You cancel X." when more than one command is queued (`StdAbility.java:1905-1913`). When actions are available, `StdMOB` calls `EnglishParser.invokeSkill` (`MOBS/StdMOB.java:2267-2268`, `Libraries/EnglishParser.java:1328-1344`), which calls `Ability.invoke(mob, commands, null, false, 0)`.

**Step 2, the checks in `StdAbility.invoke` (non-auto path, `StdAbility.java:1964-2086`).** Each check runs in this order and returns with a message on failure:

1. Set the invoker if the MAXRANGE expertise is positive (`1967-1968`).
2. The caster must be alive, awake and mobile: `CMLib.flags().isAliveAwakeMobile` (`1974`).
3. Cooldown: if `getTicksBetweenCasts()>0` and `getTimeOfNextCast()>0` and the time has not passed, fail with "You must wait @x1 before you can do that again." (`1978-1989`). Unless the caster has the ALLSKILLS security flag.
4. A movement-cost ability fails while the caster is bound: "You are bound!" (`1991-1996`).
5. Compute cost (`usageCost`, `1998`), then apply the compounding rule (`2001-2048`). Inside the window, a negative amount forbids the skill: "You can't do that again just yet." (`2021-2024`). Otherwise each unit of cost is raised by the penalty for every earlier repeat (section 5).
6. `testUsageCost` compares current mana, movement and hit points with the cost (`1925-1961`). It says "You must be at full mana to do that." when the cost equals the maximum, otherwise "You don't have enough mana to do that." (and the equivalents for movement and hit points).
7. Minimum recovery time between casts, if `minCastWaitTime()>0` (default 0): "You need a second to recover before doing that again." (up to 1 s), "a few seconds" (up to 5 s), or "awhile" (`2053-2066`).
8. Components, if the caster lacks the `COMPONENTS` security flag and the ability has components (`2088-2111`). Missing components produce "The requirements to use this @x1 are: @x2."
9. Timestamp the cast for compounding (`2069-2073`), then **spend the cost**: `adjMana`, `adjMovement`, `adjHitPoints` (`2074-2076`).
10. `helpProficiency(mob, 0)`: the attempt itself can raise proficiency (`2077`; section 7).
11. Send a `MSG_PREINVOKE` message through `mob.okMessage`, then `executeMsg`. Any listener can veto it (`2078-2081`).
12. Return true. The subclass body then runs: target selection, `proficiencyCheck`, the effect, and fizzle handling.

If `auto` is set, the base code instead marks `isAnAutoEffect=true` and returns true (`2083-2085`). Auto-invoked abilities skip every check above.

**Step 3, the subclass body.** `Spells/Spell_AcidFog.java:141-185` is a typical body. It finds proper targets, calls `super.invoke` (which spends the cost), calls `proficiencyCheck(mob,0,auto)` (`:153`), and on success sends the cast message and calls `maliciousAffect` (`:174`). On failure it calls `maliciousFizzle`. `Spells/Spell.java:154-161` adds a last gate after the base call: `spellArmorCheck` (cloth armor, concentration), which refuses the cast after the cost is spent (see Problems).

### 5. Costs

`usageCost` (`StdAbility.java:1696-1807`) builds the cost and caches it per MOB and per ability ID (`mob.getAbilityUsageCache`, `1708-1720`).

- **Class surplus.** `diff` is the sum over the caster's classes of (class level − qualifying level) for classes that qualify (`1727-1738`).
- **Floor.** `MANAMINCOST`, shipped as `-1, 10` (`coffeemud.ini:903`). `-1` means the lowest qualifying level times 1, floored at the `_DEFAULT` exception (`1750-1762`). With the shipped config the floor is 10 (the ini comment says 5; see Problems).
- **Base.** `MANACOST`, shipped as `-1, Skill_Recall 25` (`coffeemud.ini:877`). A plain number is used as is. A compiled formula gets the variables {lowest, caster level, minimum, adjusted level, diff} (`1763-1781`). The default `-1` becomes `50 + lowest × 1`. A level-1 spell costs 51 and a level-20 spell 70.
- **Surplus discount.** For non-formula costs, `consumed -= (consumed/30) × diff` (`1782-1784`). The division is integer.
- **Overrides.** `overrideMana()` if it is not `-1` (`1787-1788`), then per-class overrides from the ability mapper (`1789-1794`).
- **Split and discount.** `buildCostArray` (`1574-1681`) divides the cost evenly across the usage types the ability uses (`1580`), applies LOWCOST and LOWFREECOST, and applies `HPCOST+`, `MNCOST+` and `MVCOST+` adjustments (`1644-1673`).

**Compounding** (`MANACOMPOUND`, `coffeemud.ini:880-893`; parsed at `Libraries/CMAbleMap.java:2800-2880`). A rule reads `ticks amount [MOBMASK=...] [ABILITYMASK=...]`. Within the window, each earlier repeat applies `newAmt += amtPenalty; newAmt += newAmt × pct` (`StdAbility.java:2032-2041`). The config example `MANACOMPOUND=3 25%` gives 70 → 88 after one repeat and 110 after two. The count resets when the window passes (`2014-2015`).

### 6. Casting quality and targets

`castingQuality` (`StdAbility.java:355-392`) refines the abstract quality for one call:

- INDIFFERENT if the target already has this effect (`357-358`), if the ability is auto-invoked (`359-360`), if a min or max range check fails against the victim (`363-370`), or if the room already has it (`371-375`).
- Otherwise it switches on the abstract quality. `BENEFICIAL_OTHERS` becomes `BENEFICIAL_SELF` when target is the caster. `BENEFICIAL_SELF` becomes INDIFFERENT when target is another MOB. `OK_SELF`, `OK_OTHERS` and INDIFFERENT all fall into `default`, which returns INDIFFERENT.

`getTarget` (`StdAbility.java:1034-1116`) with no argument chooses in this order: the victim if the cast is malicious and in combat; self if beneficial to self; the victim if the ability is malicious and in combat; self if the ability is not malicious. Otherwise it fails with "You need to specify a target." With a name it uses `getVisibleRoomTarget`. Range checks are in `checkTargetRange` (`989-1016`): "You are too close to @x1 to do that." and "You are too far away from @x1 to do that."

### 7. Proficiency: the check, the gain, and teaching

**The check** (`proficiencyCheck`, `StdAbility.java:1440-1486`):

1. With `auto` set, mark the instance auto and return true. If the instance is not owned by the MOB (`mob.isMine`, which checks the MOB's ability list and effects, `MOBS/StdMOB.java:6105-6127`), force proficiency to 100 (`1443-1448`).
2. Start from `proficiency`. The `SUPERSKILL` security flag always passes (`1454-1456`).
3. Add the `PROF+` adjustments (`1458-1464`).
4. Add `2 × LEVEL` (`1467-1468`).
5. Clamp to 5..95 unless the `DIS955RULE` security disable is set (`1470-1476`; `core/CMSecurity.java:3038`, described as "no 5% failure chance").
6. Add `LEVEL / 2` (`1478`).
7. If the caller's adjustment is non-negative, add it (`1480-1481`). If negative, roll once and fail when the roll is above `100 + adjustment` (`1483-1484`). This is a separate failure gate; it does not lower the chance.
8. Succeed when a roll is below the result (`1485`).

Because the clamp is applied before the bonuses, a proficiency of 95 with two LEVEL points gives 99, clamped to 95, plus 1, so 96%. A positive adjustment can exceed 100%.

**The gain** (`helpProficiency`, `StdAbility.java:1810-1871`). It runs on every attempt (`2077`) and is called from about 160 files across the code base, several with their own adjustments.

- It stops if the record is not savable (`1824`), or if the last gain was under 300000 ms ago (5 minutes; `1827`, stamped at `1857`), or if the ability is inappropriate to the MOB's factions (`1830`).
- The maximum comes from `CMLib.ableMapper().getMaxProficiency`, which defaults to 100 (`Libraries/CMAbleMap.java:2364-2368`).
- Gate: the MOB passes if `round(sqrt(INT) × 34 × random) >= proficiency + adjustment` (`1837`). The random ceiling is about 108 at INT 10, 144 at INT 18 and 170 at INT 25. The gate passes about 54%, 66% and 71% of the time at proficiency 50 for those three INT values.
- Gain roll: `getProfGainChance` (`1839-1840`). The formula is `FORMULA_PROFGAIN = (100 - (@x3 * 50)) * ( (@x1 + 1 - @x2) / ( (@x1 * 2) + (10 * @x2) ) )` (`coffeemud.ini:2526`). Here @x1 is `LASTPLAYERLEVEL` (91 in the shipped config, `coffeemud.ini:515`), @x2 is the qualifying level and @x3 is 1 when fatigued (`Libraries/CMAbleMap.java:2885-2903`; the fatigue threshold is `CharState.java:75`, 3000 ticks). Computed with @x1 = 91: qualifying level 1 gives 47% (24% when fatigued), level 10 gives 29%, level 20 gives 19%, level 50 gives 6%, and level 91 gives 0%. A non-qualifying skill gives 100.1 (`CMAbleMap.java:2889-2890`).
- On a gain: +1 on the instance, copied to the template and any live effect (`1843-1849`), and "You become better at @x1." if `AUTOIMPROVE` is set (`1855-1856`). At maximum, proficient players get a channel notice (`1860-1867`).

**Teaching and practice** (`StdAbility.java:2751-2822`).

- `teach`: the student's new record starts at teacher proficiency × (teacher WIS + student INT) / 100 (`2762`), capped at 75% of max (`PRACMAXPCT=75`, `coffeemud.ini:907`; `2760-2764`) and raised to the class default if that is higher (`2765-2767`).
- `practice`: spends `practicesToPractice` (default 1) and gains `round(25 × (teacher WIS + student INT) / 36)` per session (`2811`). It is capped at 75% of max and at the teacher's proficiency (`2812-2816`).
- Gates: the teacher needs at least 25% of max proficiency to teach (`canBeTaughtBy`, `2446-2452`). A practice session needs the student below the teacher and below 75% of max minus 1 (`canBePracticedBy`, `2709-2724`).
- `canBeLearnedBy` (`2467-2602`) checks training cost, the student's class qualification, level, mask and prerequisites. It also refuses a student with INT under 2 (`2504-2510`), and one whose INT is more than 6 below a normalised requirement: `ceil(18 × qualifying level / highest level)` (`2521-2526`).
- Training cost: `getTrainingCost` (`StdAbility.java:226-258`) evaluates `SKILLCOST` (`coffeemud.ini:921`, shipped as `1 TRAIN`, with overrides such as `Skill_WildernessLore 1 PRACTICE`) with @x1 = qualifying level and @x2 = player level. `LANGCOST=3 PRACTICE` is at `coffeemud.ini:922`.

### 8. Affecting a target: durations

**Tick unit.** `CMProps.getTickMillis()` is `TIME_TICK = 4000` ms (`core/CMProps.java:681`; `TICKTIME=4000` at `coffeemud.ini:2151`). Durations are counted in ticks. The mud day is `TICKSPERMUDDAY = MILLISPERMUDHOUR × HOURSINDAY / TICKTIME`, which is 600000 × 6 / 4000 = 900 ticks (`application/MUD.java:1620`, `Common/DefaultTimeClock.java:313`; `coffeemud.ini:1099` and `1104`). Ticks per real hour are 900 (`core/CMProps.java:683-684`, `3557-3560`).

**Beneficial** (`getBeneficialTickdownTime`, `StdAbility.java:2312-2331`). Ticks = `casterLevel × 4 + 40`. A caster who is a possible abuser of level (for example a player, `aPossibleAbuserOfCasterLevel` at `2284-2310`) is capped at the target's level plus `EXPRATE` when the target is another MOB (`2316-2324`). The result is clamped to [5, 900] (`2273-2282`). Level 10 gives 80 ticks (5 min 20 s); level 30 gives 160 ticks; 215 and above reach the 900-tick cap.

**Malicious** (`getMaliciousTickdownTime`, `StdAbility.java:2174-2183`; adjustment at `2140-2172`). Base ticks = `round(1.1 × adjusted level) + 25`. For a non-room target with no explicit level, the difference `target level − adjusted level` is clamped to ±`EXPRATE` (default 6, `coffeemud.ini:406`). The ticks are then reduced by `round(ticks × diff / (EXPRATE+1))`. A target 6 levels above the caster loses six-sevenths of the duration; 6 levels below gains about 13/7 of it. The result is capped at one third of the ticks in a real hour (`CMProps.getTicksPerHour()/3` = 300 ticks, 20 real minutes; `StdAbility.java:2160-2162`), also at 900, and floored at 2. Worked example at adjusted level 20: base 47; target level 26 gives 7 ticks; target level 14 gives 87 ticks.

**Explicit override.** A positive `tickAdjustmentFromStandard` is used directly (`2176-2177`, `2314-2315`). A negative one is a percentage of the standard (`2181-2182`, `2329-2330`). Spell_AcidFog passes `(level + XLEVEL + 2 × X1) × 10` ticks (`Spells/Spell_AcidFog.java:174`). GenAbility passes an expression (`GenAbility.java:636`).

**Attaching.** `beneficialAffect` and `maliciousAffect` (section 1) call `startTickDown` (`StdAbility.java:697-780`). That raises the duration by the TIME factor (`709`) and by class modifiers (`710-714`, `765-767`). It applies the `TICK+` adjustments (`746-752`). It also adds the effect to the target with `addEffect` (`685-690`). `EFFECTCAP` (default 0, `coffeemud.ini:862`) limits effects per target. Above the cap, a random removable effect is uninvoked (`725-743`).

**Non-MOB targets.** Items, rooms and areas get a tick client from the global service, `startTickDown(this, TICKID_MOB, 1)` (`StdAbility.java:685-690`). That schedules the effect every thread-group interval, which is 4 s by default (`core/threads/ServiceEngine.java:349-352`; `core/threads/StdTickClient.java:35-40`, `153-160`).

### 9. Ticking, expiry and uninvoking

`StdAbility.tick` (`StdAbility.java:2843-2862`) returns false if the effect is already uninvoked. For `TICKID_MOB` only, when the timer is running and not long-lasting, it decrements `tickDown`. At zero it sets `tickDown=-1`, calls `unInvoke()` and returns false. A MOB drives its effects from its own tick: `if(!A.tick(...)) A.unInvoke();` (`MOBS/StdMOB.java:4318-4327`).

`unInvoke` (`StdAbility.java:1501-1539`) sets `unInvoked`. If there is an affected object and `canBeUninvoked()`, it removes the effect from the target:

- Room: remove, then `recoverRoomStats`.
- MOB: fire the GMCP removal notice, remove, then `recoverRoomStats` if the MOB is in a room, otherwise `recoverPhyStats`, `recoverCharStats` and `recoverMaxState`.
- Anything else: remove, then `recoverPhyStats`.

Stat effects are applied by overriding `affectPhyStats`, `affectCharStats` and `affectCharState`. The base versions are empty hooks (`StdAbility.java:1548-1560`). Owners recompute stats from base values plus all effects.

`makeNonUninvokable` (`StdAbility.java:2393-2398`) makes an effect permanent, like a racial trait. `makeLongLasting` (`2825-2828`) makes it last effectively forever until removed.

### 10. Auto-invocation

`autoInvocation` (`StdAbility.java:2368-2390`) adds a copy as an effect when a MOB gains or logs in with an auto-invoked ability. It skips the copy if the player's auto-invoke list excludes the ability (`2375-2377`) or if the effect is already present. The copy is marked unsavable, with the MOB as invoker and `isAnAutoEffect=true`. `isAutoInvoked()` is false by default (`StdAbility.java:184-187`).

### 11. Messages players see

Fixed strings from the base class, all in `StdAbility.java`:

- "You cancel @x1." (`1909`)
- "You must wait @x1 before you can do that again." (`1987`)
- "You are bound!" (`1994`)
- "You can't do that again just yet." (`2023`)
- "You must be at full mana to do that." and "You don't have enough mana to do that." (`1932`, `1934`); movement reads "You don't have enough movement to do that.  You are too tired." (`1945`)
- "You need to specify a target." (`1062`)
- "You don't see them here." and "You don't see anyone called '@x1' here." (`1093`, `1095`)
- "You are already affected by @x1." (`1105`)

Fizzles and failures go out as `MSG_SKILLFAIL` messages through `failureTell` (`StdAbility.java:1873-1902`). Casts go out as `CMMsg` objects with source, target, tool and separate source, target and other texts. Those texts use tokens such as `<S-NAME>` and `<T-NAME>`. Every message is offered to `okMessage` before `executeMsg`.

### 12. Sibling classes

**GenAbility: data-driven abilities.** Each ability ID owns one 35-slot record in a static table, `vars`, keyed by ID (`GenAbility.java:50`, accessors `V` and `SV` at `130-149`). Every instance with that ID shares the record. Defaults are in `makeEmpty` (`89-128`). The default classification is `SPELL | ABJURATION`, the default usage is mana, the default targets are mobs, and the default quality is `BENEFICIAL_OTHERS`. The 37 stat codes (`GenAbility.java:1064-1101`) are the editable fields and the XML save format: name, display, trigger strings, range, auto-invoke, flags, classification, cost override, usage, targets, quality, here-stats, casting mask, script, target mask, and about 20 message and tuning fields.

`invoke` (`GenAbility.java:409-852`) runs as follows:

1. Check the casting mask (`413-419`).
2. Select the target by `canTargetCode` (`421-491`) and check the target mask (`492-502`).
3. Armor fumble check for chants, spells and thief skills (`504-550`). It runs before `super.invoke`, so it costs nothing.
4. Call `super.invoke` (`551`), which spends the cost.
5. `proficiencyCheck` (`554`).
6. On success, choose a cast code from the classification (chant → verbal, spell → somatic, thief → thief action, and so on; `559-600`). Add the malicious flag if the cast is malicious.
7. Send a cast message and an optional second, malicious-others message (`606-624`).
8. If neither is blocked, evaluate `TICKSOVERRIDE` with @x1 = caster level, @x2 = target level, then X-levels and the adjusted level (`625-636`). Then apply `maliciousAffect` or `beneficialAffect` (`644-647`).
9. Optionally attach a quiet "mock" ability (`654-669`) and a post-cast effect through `Prop_SpellAdder` (`675-718`).
10. Run the damage or heal step (`724-829`). The damage expression (`POSTCASTDAMAGE`, `GenAbility.java:729-746`) is evaluated with the same variables, and the text shown is `POSTCASTMSG`.
11. If the effect is still running, attach the same step as a per-tick effect (`830-842`, `1018-1019`). This makes damage over time.

The fizzle path uses `maliciousFizzle` or `beneficialVisualFizzle` (`845-849`). Scripts run through `DefaultScriptingEngine` (`170-188`). Hooks in `okMessage`, `executeMsg` and `tick` (`861-889`, `927-942`, `1001-1021`). A channelling ability breaks when the invoker performs a noisy or moving action (`870-887`).

**GenTweakAbility: override by proxy.** `createProxy(originalID, newID)` builds a `java.lang.reflect.Proxy` whose handler is a `GenTweakAbility` (`GenTweakAbility.java:122-127`). Calls first check a static override map keyed by the new ID (`47`, `103-115`). A method name present in the map returns the stored value. `maxRange` is wrapped with the MAXRANGE expertise (`111-112`). Otherwise the call goes to the original ability, loaded with `CMClass.getAbility` (`70-87`, `118`). `L()` can replace translated strings from a `STRINGS` map (`130-145`).

**ExtAbility: composition by callback.** An `ExtAbility` can set its own ID, stats affector, message listener, tickable and invoke callbacks at run time (`ExtAbility.java:75-110`). It runs its callbacks before the base `invoke` (`113-135`). That means before costs and cooldown are checked. Its message methods pass through to the listener (`162-177`), and its tick calls the tickable (`193-201`).

**ThinAbility: stub.** It implements `Ability` directly. It cannot be uninvoked and is always an auto effect (`257-272`). Its class is PROPERTY (`403-406`) and its cost is all zeros (`646-652`). It is the placeholder for properties that need no logic.

**StdPlanarAbility: planar shift.** The plane table is `resources/skills/planesofexistence.txt` (lines 54–90 hold 37 planes). `setMiscText` loads a plane's variables (`469-510`), chooses a prefix at random from a comma list (`511-515`), compiles the level formula (`553-559`) and builds the enable list (`570-...`). The default level formula is `(@x3 - (@x1 - @x2) + 0) > 1` (`553-555`). An unknown plane name throws `IllegalArgumentException` (`483-489`).

Casting (`invoke`, `1870-2234`):

- Prime Material returns the caster to the material world. It removes the current planar effect (`1931-1956`).
- Casting again within 10 minutes of the last shift invokes `Disease_PlanarInstability` first (`1958-1965`).
- Proficiency decides the outcome in tiers (`2018-2086`). On a failed check (95% of cases) the cast fizzles. On the other 5%, the result depends on proficiency: under 50 gives a random plane and area; 50 to 74 gives a random transitional plane and area; 75 to 99 gives a random transitional plane; 100 gives a random area only. On a successful check, a second roll with adjustment −95 (passes about 5% of the time) keeps the planned destination (the code's comment reads "kaplah!"). If that roll fails, a roll with −50 gives a transitional plane, plus a random area below proficiency 75. If that also fails, proficiency sets the result: below 75 gives a transitional plane and a random area, 75 to 99 gives a random area, and 100 gives a transitional plane.
- A random area is a random room with more than two identified rooms (`2105-2121`).
- The new plane is a `SubThinInstance` area named `<n>_<area>`, with one room, "Between The Planes of Existence" (`2149-2166`). The caster and followers move into it with `MSG_ENTER` and `MSG_LEAVE` (`2203-2229`).
- A repeat visit to the same plane and area within an hour of the previous visit (`hardBumpTimeout`, `StdPlanarAbility.java:107`) doubles a hard-bump level: 1, 2, 4 and so on (`2135-2148`). In `fixRoom` the bump raises mob levels (`1104`) and adds 5% resistance per level (`1174-1175`).

The planar tick (`1391-1425`) runs on the plane's area tick (`TICKID_AREA=9`, `core/interfaces/Tickable.java:100`). It gives players in the plane a recovery pass every `RECOVERRATE × 5` area ticks, where 5 is `CharState.REAL_TICK_ADJUST_FACTOR` (`Common/interfaces/CharState.java:65`). Hunger and thirst are also affected by the plane's `FATIGUERATE`. `unInvoke` destroys the plane (`1858-1865`).

### 13. Interfaces

`Ability.java` (1109 lines) is the contract: classification, quality, costs, targeting, invoke, tick, uninvoke, teaching and practice, proficiency, and the constant tables. Sub-interfaces add family behaviour. `PlanarAbility` has planar accessors and two enums (`PlanarVar`, 36 keys; `PlanarSpecFlag`: NOINFRAVISION, BADMUNDANEARMOR, ALLBREATHE). `Trap` covers set, spring, reset, disable and bomb. `Language` covers translation tables and whether a language is being spoken. `CraftorAbility` and `ItemCraftor` give recipe and craft-item access. `DiseaseAffect` and `HealthCondition` describe conditions and their spread (`SPREAD_*` bits). `LimbDamage` lists damaged limbs. `MendingSkill` says whether an item can be mended. `ExtendableAbility` exposes `setAbilityID` and the hook setters. `ItemTicker` manages effects that live on items. `TriggeredAffect` defines trigger masks (`TRIGGER_*`).

## Content worth mining

**Ability types** (`classificationCode()` low bits, `Ability.java:671-718`). SKILL, SPELL, PRAYER, SONG, TRAP, PROPERTY, THIEF SKILL, LANGUAGE, CHANT, COMMON SKILL, DISEASE, POISON, SUPERPOWER, ARCHON SKILL (`ACODE_TECH=13`). A string "TECH SKILL" at index 14 has no constant (see Problems).

**Domains** (`DOMAIN.DESCS`, `Ability.java:893-915`, 83 entries). The names are: NOTHING, DIVINATION, ABJURATION, ILLUSION, INVOCATION/EVOCATION, ALTERATION, TRANSMUTATION, ENCHANTMENT/CHARM, CONJURATION, ARCHON, SINGING, DANCING, PLAYING, DECEPTIVE, FIND/REMOVE_TRAPS, BINDING, STEALING, STEALTHY, TRAPPING, ALERT, HOLY_PROTECTION, HEALING, VEXING, BLESSING, CURSING, EVANGELISM, MOON_SUMMONING, MOON_ALTERING, GATHERING, CRAFTING, PLANT_GROWTH, SHAPE_SHIFTING, FOOLISHNESS, WARDING, DEATH_LORE, WEATHER_MASTERY, CORRUPTION, RESTORATION, NEUTRALIZATION, CREATION, COMMUNING, PRESERVING, ENDURING, PLANT_CONTROL, ANIMAL_AFFINITY, DEEP_MAGIC, BREEDING, WEAPON_USE, ROCK_CONTROL, KICKING, PUNCHING, GRAPPLING, CALLIGRAPHY, POISONING, ARCANE_LORE, ACROBATIC, INFLUENTIAL, STREET_SMARTS, NATURE_LORE, DIRTY_FIGHTING, COMBAT_LORE, COMBAT_FLUIDITY, EVASIVE, MARTIAL_LORE, RACIAL_ABILITY, ARTISTIC, ANATOMY, ARMOR_USE, SHIELD_USE, CRIMINAL, LEGAL, FITNESS, THEATRE, BUILDING, WATER_LORE, WATER_CONTROL, SEA_TRAVEL, EDUCATION_LORE, EPICUREAN, COSMOLOGY, ROPE_USE, TRAVEL, ECONOMIC. Each has a verb form in `VERBS` (`Ability.java:917-939`), for example "Divining", "Abjuring" and "Stealthing".

**Planes** (`resources/skills/planesofexistence.txt`, 37 entries, one per line). Grouped by the category field:

| Group | Planes | Notes |
|---|---|---|
| Inner, elite | Earth, Air, Fire, Water, Positive, Negative | Fire is opposed by Water |
| Inner, quasi | Magma, Ooze, Minerals, Dust, Smoke, Ice, Lightning, Vacuum, Radiance, Ash, Steam, Salt | |
| Transitional | Astral, Ethereal | Opposed to each other; `transitional=true` |
| Outer, higher | Arcadia, Seven Heavens, Twin Paradises, Elysium, Happy Hunting Grounds, Olympus | Olympus is opposed by Nine Hells; alignment 10000 |
| Outer, central | Nirvana, Gladsheim, Limbo, Pandemonium, Concordant Opposition | |
| Outer, lower | Abyss, Tarterus, Hades, Gehenna, Nine Hells, Acheron | Nine Hells alignment −10000 |

Each entry sets these keys: `CATEGORY`, `OPPOSED`, `PROMOTIONS` (rank words with a percentage threshold; a roll at or below the number picks that rank, and the rarest matching rank wins, `StdPlanarAbility.java:898-911`), `ELITE`, `PREFIX`, `LEVELADJ`, `MOBRESIST`, `ENABLE` (grants skills per level, `number(x/y)`), `BEHAVE` (behaviours), and others. The header comment lists the keys (`planesofexistence.txt`, lines 1-52).

**Standard messages** (short, as quoted above). `GenAbility.makeEmpty` defaults (`GenAbility.java:109-117`): "<S-NAME> attempts to use this ability against <T-NAME>, and fails", "An amazing thing happens to <T-NAME>!", "This ability is not yet documented." These are a usable template set for a text game.

**Quality and range vocabulary.** The six quality codes and the range names in `RANGE_CHOICES` (`Ability.java:943`: MELEE, CLOSE, SHORT, MEDIUM, LONG, LONGER, LONGERSTILL, VERYLONG, EXTREMELYLONG, INFINITE) are a ready-made distance scale.

## Notable design ideas

- **Template and effect split.** A cast that persists copies the template (`StdAbility.java:2185-2208`, `2341-2349`), so the template holds no per-target state.
- **Hierarchical adjustment keys.** `PROF+<ID>`, `PROF+<ACODE>`, `PROF+<DOMAIN>` and `PROF+*` are summed (`StdAbility.java:1457-1464`). One key set gives every ability a tuning hook by name, type, domain or wildcard.
- **A fixed vocabulary of expertise dials.** Twelve `XType` values cover cost, time, range, level, proficiency and XP (`ExpertiseLibrary.java:421`; accessors at `StdAbility.java:419-472`), so balance can be tuned in one place.
- **Compounding rules as data.** Repeat-use penalties are config lines with optional masks (`coffeemud.ini:880-893`; `CMAbleMap.java:2800-2880`).
- **Formulas as configuration.** Costs, gain chance and level adjustment are compiled formulas over named variables (`StdAbility.java:1763-1781`; `CMAbleMap.java:2885-2903`; `coffeemud.ini:2526`).
- **Proficiency gate scaled by intelligence.** The random ceiling is `sqrt(INT) × 34` (`StdAbility.java:1837`). Smarter characters reach high proficiency more easily.
- **Outcome tiers as a mishap table.** Planar shifts fizzle, drift to a related plane, or land exactly, depending on a proficiency tier (`StdPlanarAbility.java:2018-2086`).
- **Escalation on repeat visits.** Visiting the same plane and area within an hour raises mob levels and resistances (`StdPlanarAbility.java:2135-2148`, `1104`, `1174-1175`).
- **Timers without per-object loops.** Items, rooms and areas get a tick client from the global service (`StdAbility.java:685-690`; `ServiceEngine.java:349-352`).
- **Composition over subclassing.** `ExtAbility` callbacks and `GenTweakAbility` proxies let an ability be built or altered from data (`ExtAbility.java:75-135`; `GenTweakAbility.java:103-127`).
- **Save round-trip for data abilities.** `GenAbility` writes every stat code as an XML element and reads them back (`GenAbility.java:1485-1516`).

## Problems and cruft

1. **Double uninvoke on natural expiry, for MOB effects (fragile, not a visible bug in the usual pattern).** `StdAbility.tick` calls `unInvoke()` and returns false (`StdAbility.java:2854-2858`). `StdMOB` then calls `unInvoke()` again on the false return (`MOBS/StdMOB.java:4326-4327`). `unInvoke` does not check `unInvoked` (`StdAbility.java:1501-1539`), but the first call's `delEffect` clears `affected` (`MOBS/StdMOB.java`, `delEffect` calls `setAffectedOne(null)`), so the second call returns at `being==null`. A subclass that guards with `if(!(affected instanceof MOB)) return;`, such as `Spells/Spell_AcidFog.java:125-138`, therefore does not repeat its message. Only a subclass that does work before checking `affected` would run it twice. The original reading of this chapter claimed a repeated message; that was corrected against the source.
2. **Cost before a late gate.** `Spell.invoke` runs `spellArmorCheck` after `super.invoke` (`Spells/Spell.java:154-161`), and `super.invoke` has already spent the mana (`StdAbility.java:2074-2076`). A cloth-armor or concentration failure therefore still costs mana. `GenAbility` runs its armor check before the cost (`GenAbility.java:539-551`). The two conventions disagree.
3. **Doc and code disagree on negative proficiency adjustments.** The `Ability.proficiencyCheck` javadoc says the adjustment is applied "to their base proficiency" (`Ability.java:593-604`). The code does not reduce the chance. It adds a separate roll that fails above `100 + adjustment` (`StdAbility.java:1483-1484`).
4. **Clamp before bonuses.** The 5..95 clamp (`StdAbility.java:1470-1476`) comes before the LEVEL half-point and the caller's adjustment (`1478-1481`). Bonuses can therefore exceed 95, and a positive adjustment can exceed 100. Whether that is intended is not stated.
5. **Expiry units are inconsistent.** `expirationDate()` returns a relative duration (`tickDown × tickMillis`, `StdAbility.java:529-531`). `setExpirationDate` treats any time not in the future as a duration in milliseconds (`534-541`).
6. **Global shared state keyed by ID.** `GenAbility.vars` (`GenAbility.java:50`) and the `GenTweakAbility` override map (`GenTweakAbility.java:47`) are static. Every instance with an ID shares one record. The mobless cost cache `SYSTEM_ABLEUSAGE_HARD_OVERRIDE_CACHE` ignores the caster (`StdAbility.java:1683-1705`, `1804-1805`).
7. **Overrides collide on overloads.** `GenTweakAbility.overrideMethod` stores one value per method name (`GenTweakAbility.java:89-94`, `108-109`). `canTarget(int)` and `canTarget(Physical)` share one slot.
8. **Comment and default disagree on the mana floor.** `coffeemud.ini:895-903` says a value of −1 uses "the value of 5, whichever is greater", and then says "The default is -1, 10". The code uses the `_DEFAULT` exception (10 when configured) and falls back to 5 only when none is set (`StdAbility.java:1750-1762`).
9. **Cost field is a tagged union.** One integer means absolute, percentage, all, normal or "−N = 50 + N × level", chosen by value ranges (`StdAbility.java:1763-1781`; `Ability.java:651-655`). It works but is hard to read or validate.
10. **Name collision: RECOVERRATE.** In planes it is an amount per cycle (`StdPlanarAbility.java:508`, `planesofexistence.txt` header). In the global config it is an interval in 5-tick units (`coffeemud.ini:689-691`), read at `StdPlanarAbility.java:1399`.
11. **Level-formula docs.** The `LEVELADJ` header defines @x2 twice (`planesofexistence.txt`, lines 12-14). The default formula uses @x3, which the header does not define (`StdPlanarAbility.java:555`).
12. **Wrong log tag.** `Log.errOut("Spell_Planeshift", ...)` in a planar base class (`StdPlanarAbility.java:532`).
13. **Setter can throw on load.** `setMiscText` throws for an unknown plane name (`StdPlanarAbility.java:483-489`). It is also the path that restores a saved ability. Whether the load catches the exception was not checked.
14. **Possible empty list.** `getTransitionPlaneKeys()` can be empty. Then `CMLib.dice().roll(1, 0, -1)` gives an index of −1 (`StdPlanarAbility.java:2124`, `2130`). Unverified, as the dice behaviour was not traced.
15. **Callbacks run before every check.** `ExtAbility.invoke` runs its callbacks before the base checks, and before costs (`ExtAbility.java:113-134`). A callback that has side effects will fire even if the cast is then refused.
16. **Constant comments disagree.** `ACODE_TECH=13` is documented as "Archon ONly Skill" (`Ability.java:696-697`), and the matching `DESCS` entry at index 13 is "ARCHON SKILL". "TECH SKILL" sits at index 14 with no constant. `ALL_ACODES=31`, a mask, is documented as "Tech-Only Skill" (`Ability.java:700-701`).
17. **Duplicate trigger bits.** `TRIGGER_DROP_PUTIN` equals `TRIGGER_PUT` (both 64), and `TRIGGER_HITTING_WITH` equals `TRIGGER_MOUNT` (both 128) (`Abilities/interfaces/TriggeredAffect.java:52-58`).
18. **Typos in display text.** Some domain verbs are odd or misspelled: "ArChreating" (`Ability.java:920`) and "Matrial Loring" (`Ability.java:917-939`). They are visible to players.
19. **Misc.** `StdPlanarAbility` uses `recoverTick` both as a countdown and as a multiplier (`StdPlanarAbility.java:1399-1420`). `GenAbility` has a "lots of work to be done here" comment on its persistence code (`GenAbility.java:1057`). `ThinAbility.adjustedLevel` returns −1 (`ThinAbility.java:129-133`), and `canAffect(Physical)` returns true while `canAffect(int)` returns false (`ThinAbility.java:88-92`, `591-595`).

## Takeaways for a Python SUD

**Borrow.**

- **Template and effect.** Keep ability definitions as shared, read-only data. Store per-character proficiency separately. Create an `Effect` object per active instance, holding its source, target, remaining turns and its own undo state.

  ```python
  @dataclass(frozen=True)
  class AbilityDef:
      id: str
      kind: str                     # skill, spell, prayer, chant, song, trap, property
      domains: frozenset[str]      # used for bonus keys such as "prof:abjuration"
      quality: str                  # malicious, beneficial_self, beneficial_other, neutral
      cost: "CostRule"
      cooldown_turns: int = 0

  @dataclass
  class Effect:
      ability_id: str
      source: str
      target_id: str
      turns_left: int | None        # None = until removed
      removed: bool = False         # guard against double removal (Problems 1)
  ```

- **Ordered checks, one place.** Run the checks in the order of `StdAbility.invoke`: alive and awake, cooldown, bound, cost, compounding, components. Spend the cost, record the attempt, then roll. Decide explicitly whether a failed roll costs the resource. The original does spend it.
- **Proficiency model.** Use the 5..95 clamp, but apply the clamp after all bonuses, or document that it is not a hard floor. Use an intelligence-scaled gain gate and a per-skill gain formula. Plain Python lambdas are simpler than a formula parser. Throttle gains by game time, not wall-clock time.
- **Key-based bonuses.** Store bonuses in a dict keyed by `"prof:<id>"`, `"prof:<domain>"` and `"prof:*"`, and sum them. This is much easier to tune than per-skill code.
- **Outcome tiers.** Use the planar mishap table as a template for "magic goes wrong" results: fizzle, drift, or exact success, selected by a tier table.
- **Repeat escalation.** Track repeat visits per location with a game-turn window and an escalation counter.

**Simplify.**

- Twelve expertise types become three or four knobs: level, duration, cost discount and range.
- Three usage types become one resource (for a single player, mana or stamina).
- Replace the mask language (`MOBMASK`, `ABILITYMASK`) with a predicate function until real rules need a mini-language.
- Drop the tagged-union cost encoding. Use separate fields for absolute, percentage and per-level costs.
- Replace the 1,516-line `GenAbility` with a dataclass and a YAML or TOML record. Keep the message templates, the expression hooks and the XML round-trip idea.

**Drop, or rethink for single-player.**

- `aPossibleAbuserOfCasterLevel` and the caster-level cap are about players casting on other players. In a single-player game there is no such case.
- Group following during planar travel (`StdPlanarAbility.java:2203-2229`), faction reactions, and channel announcements of proficiency are multiplayer features.
- The `SUPERSKILL` and `DIS955RULE` flags are admin switches. Make such bypasses explicit test hooks, not runtime flags.
- Real-time windows (5 minutes, 10 minutes, one hour) should become game-time windows. If the game is turn-based, count turns. If real-time, keep the 4-second tick as a starting point.
- Use a seeded `random.Random` for combat and proficiency, so tests are reproducible. This is a suggestion; the original uses the global dice library.

## Coverage

**Read in full.**

- `com/planet_ink/coffee_mud/Abilities/StdAbility.java` (1–3018).
- `com/planet_ink/coffee_mud/Abilities/interfaces/Ability.java` (1–1110).
- `com/planet_ink/coffee_mud/Abilities/ThinAbility.java` (1–665).
- `com/planet_ink/coffee_mud/Abilities/ExtAbility.java` (1–202).

**Read in part.**

- `GenAbility.java`: lines 38–1135 and 1485–1516. Lines 1136–1484 (getStat, setStat and the converters) were skimmed by signature only.
- `GenTweakAbility.java`: lines 36–170. The rest was skimmed by signature.
- `StdPlanarAbility.java`: lines 40–129, 466–615, 1391–1470 and 1858–2235. `fixRoom` (951–1340) and the plane-file parser (1528–1660) were skimmed by signature.
- `PlanarAbility.java`: declarations and enums (lines 288–341) via grep.

**Skimmed by declarations.** The eleven small interfaces (`ItemCraftor`, `Trap`, `Language`, `DiseaseAffect`, `CraftorAbility`, `LimbDamage`, `ExtendableAbility`, `ItemTicker`, `TriggeredAffect`, `MendingSkill`, `HealthCondition`).

**Outside the bucket, read for context.** `Abilities/Spells/Spell.java` (56–162), `Abilities/Spells/Spell_AcidFog.java` (104–186), `Libraries/EnglishParser.java` (1290–1345), `MOBS/StdMOB.java` (2219–2312, 4318–4330, 6105–6127, plus grep of `tick`), `Libraries/CMAbleMap.java` (2332–2380, 2498–2530, 2800–2925), `Libraries/ColumbiaUniv.java` (387–403), `core/threads/ServiceEngine.java` (349–410), `core/threads/StdTickClient.java` (35–50, 135–175), `core/CMProps.java` (targeted ranges and greps), `core/CMSecurity.java:3038`, `core/interfaces/Tickable.java:82-100`, `Common/interfaces/CharState.java:65,75`, `application/MUD.java:1620`, `Common/DefaultTimeClock.java:313`, `coffeemud.ini` (targeted keys).

**Skipped.** The rest of the ability families (Spells, Prayers, Chants and others) beyond the examples above. `StdPlanarAbility.fixRoom`. Nothing in this bucket was executed.
