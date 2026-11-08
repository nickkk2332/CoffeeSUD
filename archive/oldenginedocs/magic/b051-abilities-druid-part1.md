# Druid chants, part 1: the Chant base class and its members (Acid Rain to Control Plant)

> Part of the CoffeeMud NEWOVERVIEW. Bucket: `b051` (Abilities/Druid [part 1/7: Chant .. Chant_ControlPlant]) · 51 files · 9533 lines of Java.

Paths in this chapter are relative to the CoffeeMud folder. In the catalogue, a bare `Chant_X.java:N` means `com/planet_ink/coffee_mud/Abilities/Druid/Chant_X.java` line N.

## Summary

Druid chants are CoffeeMud's nature-magic family. Each is a class in `com/planet_ink/coffee_mud/Abilities/Druid/` with a `Chant_` ID that extends `Chant`, which extends `StdAbility`. The base class adds only a few shared rules: chant-style casting messages, a helper that says which effects the local weather allows, and a 50% "extreme emotions" roll for player chants. The 50 members here are short classes (92 to 325 lines) that follow one pattern: check the target, let `StdAbility.invoke` take mana, roll `proficiencyCheck`, then attach a timed effect whose tick, stat and message hooks do the work. The most interesting ones change the world rather than a creature: weather (CalmWind, CalmWeather, CalmSeas), the moon (BlueMoon, ColdMoon, ClearMoon), the clock (AlterTime), room hazards (AcidRain, Blight, CaveIn) and plant ownership (ControlPlant). For a single-player Python game this is a useful model of a data-driven spell list whose effects touch the world.

## Key files and types

| File or class | Role | Lines |
|---|---|---|
| `com/planet_ink/coffee_mud/Abilities/Druid/Chant.java` | Family base: casting codes, weather helper, disruption roll | 201 |
| `com/planet_ink/coffee_mud/Abilities/StdAbility.java` | Base of all abilities: `invoke` flow, mana cost, proficiency, durations, affect helpers (read in part) | 3018 |
| `com/planet_ink/coffee_mud/Abilities/interfaces/Ability.java` | Constants: `ACODE_CHANT=8` (687), `DOMAIN_*` (722-884), `QUALITY_*` (1083-1093), `CAN_*` (1100-1108), `FLAG_*` (946-1032), `COST_*` (651-655) | interface |
| `com/planet_ink/coffee_mud/Abilities/Druid/Chant_Barkskin.java` | Simplest self buff: armour reduction | 126 |
| `com/planet_ink/coffee_mud/Abilities/Druid/Chant_CharmAnimal.java` | Mind-affecting charm of an animal, follow maintenance | 283 |
| `com/planet_ink/coffee_mud/Abilities/Druid/Chant_AnimalCompanion.java` | Oath that rebinds a following animal (`Loyalty` effect) | 254 |
| `com/planet_ink/coffee_mud/Abilities/Druid/Chant_CalmWeather.java` | Area weather stepped toward calm; XP; static `xpWorthyChange` helper | 203 |
| `com/planet_ink/coffee_mud/Abilities/Druid/Chant_CalmWind.java` | Area wind and storm control; room "still air" effect | 263 |
| `com/planet_ink/coffee_mud/Abilities/Druid/Chant_CalmSeas.java` | Area sea-calm effect that refuses rival water chants | 229 |
| `com/planet_ink/coffee_mud/Abilities/Druid/Chant_Capsize.java` | Malicious chant on a boat that dumps its cargo | 325 |
| `com/planet_ink/coffee_mud/Abilities/Druid/Chant_AstralProjection.java` | Splits the caster into a spirit MOB and moves the session | 217 |
| `com/planet_ink/coffee_mud/Abilities/Druid/Chant_Burrowspeak.java` | Speak with burrowing creatures (extends `Chant_SpeakWithAnimals`, outside this bucket) | 92 |
| `com/planet_ink/coffee_mud/CharClasses/Druid.java` | Learn levels (`addCharAbilityMapping`, e.g. Barkskin at line 198) and the 50% disruption check in `okMessage` (448-471) | class |
| `com/planet_ink/coffee_mud/CharClasses/StdCharClass.java` | `isQualifyingAuthority` (865-881) | class |
| `com/planet_ink/coffee_mud/Libraries/Dice.java` | `roll(n, die, mod)` (398-405) and `rollPercentage()` (1-100) | library |
| `coffeemud.ini` | `MANACOST=-1` (877), `MANAMINCOST=-1, 10` (903), `TICKTIME=4000` (2151) | config |

## How it works

### 1. Shared rules in Chant.java

- **Casting codes.** `verbalCastCode` (Chant.java:69-82) sends `MSG_NOISE|MASK_MOUTH` for a "rendered mundane" chant (line 74). Otherwise it defers to `StdAbility.verbalCastCode` (StdAbility.java:2629-2637), which sends `MSG_CAST_VERBAL_SPELL`, or `MSG_CAST_ATTACK_VERBAL_SPELL` when `castingQuality` is malicious. Line 73 assigns `MSG_CAST_VERBAL_SPELL` and line 74 overwrites it (dead store).
- **Identity.** Trigger words are `CHANT` and `CH` (Chant.java:84). The class code is `ACODE_CHANT` (Chant.java:120-123). `setMiscText("render mundane")` switches it to `ACODE_SKILL` and sets `renderedMundane` (Chant.java:111-117). The only caller is `Behaviors/WeatherAffects.java:1058`, which casts `Chant_SummonLightning` with that text so that weather can reuse the code without it being a chant (no disruption roll, no chant wording).
- **Targeting defaults.** `abstractQuality()` is `QUALITY_OK_SELF` (Chant.java:87-90). `canAffectCode()` and `canTargetCode()` are both `CAN_MOBS` (Chant.java:99-108). Members that do not override these inherit that default.
- **Mana.** No member overrides `usageType()` (so `USAGE_MANA`, StdAbility.java:299-302), `castingTime()`, `getTicksBetweenCasts()` or `minCastWaitTime()` (checked by grep over the bucket).
- **Weather helper.** `weatherQue(Room)` (Chant.java:126-165) returns a bitmask of `WEATHERQUE_WIND=1`, `RAIN=2`, `HOT=4`, `COLD=8`, `CALM=16` (Chant.java:60-66). It returns 0 indoors and for blizzard, thunderstorm and heat wave. Clear skies allow wind, rain, hot and cold (line 141); fog allows rain and calm (line 143); the default is calm (line 163). Callers are `Chant_CalmWind.java:104`, `Chant_CalmWeather.java:115` and the summon chants in other buckets.
- **Disruption roll.** `chantAlignmentCheck` (Chant.java:167-189) runs in `Chant.invoke` after the base invoke (Chant.java:192-199). It applies only to a player's chant that is not auto-invoked, not rendered mundane, and owned by that mob. It then rolls `rollPercentage()<50` (line 174). On a fail it says either "Extreme emotions disrupt your chant." (line 178, when `appropriateToMyFactions` is false) or "<S-NAME> watch(es) <S-HIS-HER> armor absorb <S-HIS-HER> magical energy!" (line 184, when `armorCheck(mob, CharClass.ARMOR_NONMETAL)` fails). Either way `invoke` returns false.
  - The mana is already gone by then: `StdAbility.invoke` deducts it at StdAbility.java:2074-2076, before `chantAlignmentCheck` runs. No refund is given.
  - `CharClasses/Druid.java:448-471` has a second 50% roll with the same message. It runs on the cast message (`TYP_CAST_SPELL`) for a chant whose qualifying class is Druid (`isQualifyingAuthority`, StdCharClass.java:865-881). Whether both fire on one cast was not verified.

### 2. Invocation flow (StdAbility.invoke, StdAbility.java:1964-2086)

Every member's `invoke` follows the same order:

1. Pre-checks that cost nothing (target present, not already affected, room type). Examples: Barkskin returns "already like bark" before `super.invoke` (Chant_Barkskin.java:98-101).
2. `super.invoke(...)`, which for a live, non-auto cast does, in order: awake-and-alive check (1974); cast-wait check `getTimeOfNextCast` (1978-1989); bound check (1991); `usageCost` with compounding penalties (1998-2048); `testUsageCost` for mana, movement and hit points (1925-1962, messages such as "You don't have enough mana to do that."); minimum-cast-wait (2053-2066); components (2067); deduct mana (2074); `helpProficiency` (2077); PREINVOKE message (2078-2081).
3. Chant's disruption roll (Chant.java:197).
4. `proficiencyCheck(mob, 0, auto)` (see section 3).
5. On success, build a `CMMsg`, ask the room `okMessage`, then `send`, then attach the effect (`beneficialAffect` or `maliciousAffect`). On failure, call a fizzle helper (`beneficialWordsFizzle`, `beneficialVisualFizzle` or `maliciousFizzle`), with text such as "<S-NAME> chant(s) to <T-NAMESELF>, but nothing happens."

Mana is therefore spent even when the proficiency roll fails. Only the pre-checks are free.

### 3. Costs, proficiency and effective level

**Default mana cost.** `usageCost` (StdAbility.java:1696-1807) reads `MANACOST`. The shipped value is `-1` (coffeemud.ini:877, "Default is: -1, Skill_Recall 25"), which means the cost is `50 + lowest * 1`, where `lowest` is the lowest qualifying level of any class (StdAbility.java:1763-1781). The cost falls by `consumed/30` per class level the caster has above the qualifying level (1782-1784). The minimum is `MANAMINCOST=-1, 10` (coffeemud.ini:903): the lowest qualifying level, floored at 10 (StdAbility.java:1750-1762).

**Overrides in this bucket** (`overrideMana()`, applied at StdAbility.java:1787-1788 unless the ini has an exception):

- `Chant_Capsize`: `COST_PCT + 50`, which is 50% of max mana (Chant_Capsize.java:60-63; conversion in `buildCostArray`, StdAbility.java:1614-1619).
- `Chant_AnimalCompanion`: `COST_PCT + 10`, 10% of max mana (Chant_AnimalCompanion.java:84-87).
- `Chant_AlterTime`: a flat 100 mana (Chant_AlterTime.java:58-61).

**Proficiency** (`proficiencyCheck`, StdAbility.java:1441-1487):

1. `pct = proficiency + PROF adjustments` (1455-1460).
2. `pct += 2 * XLEVEL` (1462). The XLEVEL is an expertise value from `getXLEVELLevel` (StdAbility.java:444-447).
3. Clamp `pct` to 5..95 unless `DIS955RULE` is set (1465-1472).
4. `pct += XLEVEL/2` (1475). This comes after the clamp, so XLEVEL can push the result above 95.
5. The `adjustment` argument: a non-negative value is added to `pct`. A negative value is a separate gate first: the chant fails if `d100 > 100 + adjustment`, and then it must pass `d100 < pct`. So a negative adjustment multiplies the odds rather than just subtracting from them.

The area-scaled chants (CalmWind, CalmSeas, CalmWeather) pass a negative adjustment, so they get this gate.

**Effective level.** `adjustedLevel(caster, asLevel)` (StdAbility.java:899-926) is the lowest qualifying level, plus `(class level - qualifying level)`, plus personal LEVEL adjustments, floored at the lowest qualifying level, plus XLEVEL, then the class duration modifier. Most formulas below use it.

**Durations** (StdAbility.java:2140-2349):

- Beneficial: `beneficialTicks = adjustedLevel * 4 + 40` (2312-2331). The result is clamped to `TICKSPERMUDDAY` and at least 5 (2273-2281). When a player casts on another mob, the level is capped at the target's level plus `EXPRATE` (2318-2324).
- Malicious: `round(1.1 * adjustedLevel) + 25` ticks (2174-2183). This is then adjusted by the level gap between target and caster, with the gap capped at `EXPRATE` (2140-2170), clamped to at most one third of an hour and at least 2 ticks.
- An explicit positive `tickAdjustmentFromStandard` overrides all of this and is used as the tick count (StdAbility.java:2178-2179, 2316-2317). Examples: `Chant_CalmAnimal` passes 3 (Chant_CalmAnimal.java:104), so the effect lasts 3 ticks. `Chant_AntTrain` passes `(level + 2X) * 10` (Chant_AntTrain.java:185). `Chant_CaveIn` passes 10 for a mob (Chant_CaveIn.java:197).
- A tick is `TICKTIME=4000` ms by default (coffeemud.ini:2151; CMProps.java:681).

### 4. Effect patterns in the members

The 50 members use about six patterns. Each is a short class; the work is in the effect object.

- **Self or other buff.** `beneficialAffect` plus `affectCharStats` or `affectPhyStats`. Examples: BullStrength (STR +6+X, Chant_BullStrength.java:81), CatsGrace (DEX +6+X, Chant_CatsGrace.java:81), Barkskin (armour −10−2X, Chant_Barkskin.java:75), ColdWard (cold save +100, Chant_ColdWard.java:89), AcidWard (acid save +100, Chant_AcidWard.java:89), AnimalGrowth (height ×2, STR +5, DEX −3, weight +3×base, name "An ENORMOUS …", Chant_AnimalGrowth.java:98-126).
- **Malicious effect on a creature or item.** `maliciousAffect` with a resistance message. In `StdAbility.maliciousAffect` (2185-2208), a non-negative `additionAffectCheckCode` sends a `CMMsg` to the room, and a `msg.value()>0` result means the effect was resisted. Examples: CharmAnimal (mind-affecting code, Chant_CharmAnimal.java:264), CaveIn (rubble, Chant_CaveIn.java:197), ChargeMetal (Chant_ChargeMetal.java:237, with explicit duration `-1` code).
- **Room or area world effect.** `affected` is a `Room` or an `Area`. The effect re-runs each tick via `tick(ticking, tickID)` and changes shared state. Examples: AcidRain (hits occupants each tick while raining, Chant_AcidRain.java:91-117), Blight (converts a room's resource and destroys blightable items, Chant_Blight.java:110-134), CharmArea (Chant_CharmArea.java:76-101), ColdMoon and BlueMoon (moon checks each tick, Chant_ColdMoon.java:105-135, Chant_BlueMoon.java:104-116).
- **Weather and time control.** These write to the area's `Climate` (`setNextWeatherType`, `forceWeatherTick`) or `TimeClock` (`tickTock`). CalmWind (Chant_CalmWind.java:219-257), CalmWeather (Chant_CalmWeather.java:156-199), CalmSeas (Chant_CalmSeas.java:101-157), AlterTime (Chant_AlterTime.java:99-107, `tickTock(x)` with x in hours).
- **Summon or pet.** Create a monster and attach a follow-and-die effect. Examples: BrownMold (summons a monster in combat, Chant_BrownMold.java:166-195, ends when combat ends, tick at 90-106), CallMate (Chant_CallMate.java:171-240), CallCompanion (Chant_CallCompanion.java:76-149), AntTrain (floats an item, Chant_AntTrain.java:127-196).
- **Message interception.** `okMessage` can veto a message and `executeMsg` can add trailer messages. Examples: ChantWard blocks 35% of malicious chants aimed at the target (`rollPercentage()<35`, Chant_ChantWard.java:107-125); CharmArea stops occupants from leaving unless they follow the caster (Chant_CharmArea.java:76-101); AnimalFriendship refuses an animal's malicious act against the target unless the target is in combat (Chant_AnimalFriendship.java:72-93); BlueMoon halves damage to good mobs and adds 50% to damage against evil ones (Chant_BlueMoon.java:120-137); ChargeMetal redirects electrical damage to the wearer of metal (Chant_ChargeMetal.java:120-156).

### 5. Mechanics with formulas

| Chant | Formula or rule (as coded) | Ref |
|---|---|---|
| Barkskin | Armour value `− 10 − 2 * XLEVEL`. Lower armour values are better in CoffeeMud (not checked in this bucket). | Chant_Barkskin.java:75 |
| BullStrength | STR `+ 6 + XLEVEL` | Chant_BullStrength.java:81 |
| CatsGrace | DEX `+ 6 + XLEVEL` | Chant_CatsGrace.java:81 |
| ColdMoon | Each occupant's disease save `− (50 + 5 * XLEVEL)` | Chant_ColdMoon.java:142 |
| AcidRain | Per tick, an occupant takes damage if `d100 > save(ACID)`. Damage `roll(1, level + 2*XLEVEL, 1)`, so 1 to `level + 2X + 1`. | Chant_AcidRain.java:110-111 |
| Blight | Vegetation-race mobs in the room take damage `value + round(value/2)` | Chant_Blight.java:143-148 |
| BloodyWater | Trackers reach animals in radius `limit = 5 + 2 * (XMAXRANGE + XLEVEL)` | Chant_BloodyWater.java:155 |
| Boulderbash | `maxDie = (adjLevel + 2*X1) / 2`; damage `roll(maxDie, 6, 5 + maxDie/6)`; `/1.5` on save | Chant_Boulderbash.java:133-136 |
| CaveIn | `maxDie = (adjLevel + 2*X1) / 2`; damage `roll(maxDie, 3, min(adjLevel, 10))`; `/1.5` on save | Chant_CaveIn.java:188-191 |
| ControlFire | `numDice = round((adjLevel + 2*X1) / 2.0) + 1`; damage `roll(numDice, 6, 20)`; `/2` on save | Chant_ControlFire.java:149-152 |
| CalmWind, CalmSeas, CalmWeather | Area size penalty `size = numberOfProperIDedRooms() / (level + 2*XLEVEL)`, then `proficiencyCheck(mob, -size, auto)` | Chant_CalmWind.java:212-216; Chant_CalmSeas.java:201-205; Chant_CalmWeather.java:149-153 |
| CalmWind (still wind) | Room effect duration `4 + level/30 + XTIME` ticks | Chant_CalmWind.java:245-248 |
| CalmSeas | Initial `eachDispelCost=10`; on start the cost is set to `tickDown / max(1, adjLevel/6)`; each refused rival chant does `tickDown -= eachDispelCost` (minimum 1) | Chant_CalmSeas.java:88, 201-220, 90-97 |
| CalmWeather | Winds and storms step to calm; XP +25 when the weather moves from unsettled to clear or cloudy (no existing `Chant_ControlWeather` on the area) | Chant_CalmWeather.java:162-194, 81-101 |
| CallCompanion | `adjustment = (target level - (caster level + 2*XLEVEL)) * 3`; `proficiencyCheck(mob, -adjustment, auto)` | Chant_CallCompanion.java:124-125 |
| AnimalCompanion | Refused when `numLoyal > XLEVEL` (already-loyal animal followers) | Chant_AnimalCompanion.java:133-136 |
| BountifulWomb | Chance `50 + 5 * adjLevel + 10 * XLEVEL` (for a target with reproductive code `F`) | Chant_BountifulWomb.java:150 |
| Bury | Neutral casters regain `3*L + 3*L*XLEVEL` mana, where L is the corpse level | Chant_Bury.java:152 |
| AlterTime | `x = misc text`, or if 0 then random `roll(1,3,-2)` (−1 or +1) until non-zero; clock moves x hours | Chant_AlterTime.java:100-107 |
| ChantWard | 35% block of malicious chants | Chant_ChantWard.java:120 |
| Capsize | Blocked if `d100 < clamp(save(JUSTICE) + sign(d) * d*d, 5, 95)` where `d` = occupant level minus caster level | Chant_Capsize.java:275-289 |
| AntTrain | Floating item duration `(level + 2*XLEVEL) * 10` ticks; item weight 0 while floating | Chant_AntTrain.java:115, 185 |
| CheetahBurst | Counter cycles 3, 2, 1, 0; speed bonus `CMProps.getSpeedAdjustment()*3.0 + 0.1*XLEVEL` only when the counter is 1; movement `STR/5` every tick | Chant_CheetahBurst.java:86-123 |
| Chlorophyll | In daylight (DAY/DAWN), outdoors, in clear, drought, windy, winter-cold or heat-wave weather: hunger `+2` per tick | Chant_Chlorophyll.java:103-111 |

### 6. Notes on specific behaviours

- **Weather steps.** CalmWeather maps: fog, windy and duststorm → clear; thunderstorm → rain; blizzard → snow; snow, hail, sleet and rain → cloudy (Chant_CalmWeather.java:164-190). CalmSeas uses similar steps but forces each tick (Chant_CalmSeas.java:113-150). The step is then applied with `forceWeatherTick` (Chant_CalmWeather.java:194).
- **Moon stacking.** ClearMoon, ColdMoon and BlueMoon all test `classificationCode() & ALL_DOMAINS == DOMAIN_MOONALTERING` (Chant_ClearMoon.java:75-86; Chant_ColdMoon.java:183-190; Chant_BlueMoon.java:185-192). A room can carry one moon effect. ClearMoon also clears `DOMAIN_MOONSUMMONING`.
- **Charm and loyalty.** A charmed animal (CharmAnimal) follows the caster, and when the charm ends it "returns" to its natural will. For a monster, it wanders away if `d100 > 50`, or if it sits in a different area from its start room, can move, and either is aggressive or the caster is no longer in the room (Chant_CharmAnimal.java:205-212). Otherwise, if the caster is still around, the animal is set to fight the caster. AnimalCompanion makes the oath: it removes charm effects from the target, sets the target to follow the caster and adds `Loyalty` with `NAME=<caster>` (Chant_AnimalCompanion.java:150-243).
- **Astral projection.** The body gets sleep, can-not-hear, can-not-see and similar senses masks (Chant_AstralProjection.java:147-153). A spirit `StdMOB` with race "Spirit" takes the session (lines 193-208). A second cast returns the caster (lines 162-169). The body is sent `postDeath` when the effect timer reaches 1 (lines 109-113).
- **Animal spy.** LOOK and EXAMINE results seen by the spy are re-run for the caster (Chant_AnimalSpy.java:121-130). The caster's speech is parsed and enqueued as a command for the spy (lines 144-153).
- **Breath colour.** `getBreathColor` builds a phrase from the level gap (`+25`, `+15`, `+5`, `-5`, `-15` bands) and special traits (undead, golems, hidden, invisible, alignment) (Chant_BreathColor.java:88-275).

## Content worth mining

All 50 members plus the base. `Druid lvl` is the learn level in `CharClasses/Druid.java` (`addCharAbilityMapping`); `—` means not mapped there. Some chants are learned only by other classes (Mer, Beastmaster, Ranger, SkyWatcher, Gaian, Delver, Gypsy, Archon). Quality is the `abstractQuality()` value. `X` is `XLEVEL`.

| Chant | Quality | Domain | Target | Druid lvl | What it does (as coded) | invoke |
|---|---|---|---|---|---|---|
| AcidRain | Malicious | Weather | room | — | Only while raining, sleeting or thundering. Room effect hurts non-group occupants each tick on a failed acid save. | 132 |
| AcidWard | Beneficial self | Preserving | mob | — | Acid save +100 | 93 |
| AirWall | OK self | Weather | none | — | In ranged combat (range 1 to adjusted 10), casts `Spell_WallOfAir` in the room with shoot-back off | 102 |
| AiryAura | OK others | Water | mob | — | Group aura copied to members each tick; adds fresh and salt water to breathables | 205 |
| AlterTime | Indifferent | Moon summoning | none | — | Shifts the area clock by x hours; fixed 100 mana | 88 |
| AnimalCompanion | OK others | Animal | mob | — | Oath to a following animal; breaks its charms, sets it following, adds `Loyalty`; 10% mana | 90 |
| AnimalFriendship | OK self | Animal | mob | 10 | Animals' malicious acts against the target are refused unless the target is in combat | 110 |
| AnimalGrowth | Beneficial others | Animal | mob | — | Animal grows: height ×2, STR +5, DEX −3, heavier, named "An ENORMOUS …" | 144 |
| AnimalSpy | OK others | Animal | mob | 21 | Caster sees and hears through a following animal; speech is relayed as commands | 168 |
| AntTrain | OK self | Animal | item | — | Unworn gettable item floats beside the caster, weight 0, for `(level+2X)*10` ticks | 127 |
| AstralProjection | OK self | Shape shifting | mob | — | Body sleeps and goes limp; session moves to an astral spirit | 157 |
| BestowName | OK others | Breeding | mob | 1 | Names a group animal (non-player, in the group); name applied in `affectPhyStats`; removed if it stops following | 113 |
| Blight | Malicious | Plant control | room | — | Room resource becomes sand; one blightable item withers per tick; Vegetation mobs take +50% damage | 166 |
| Bloodhound | Beneficial self | Shape shifting | mob | — | Smell lets the caster see in darkness | 133 |
| BloodyWater | Indifferent | Animal | room | — | Underwater room: fish-race animals in radius are led to the room by tracking | 235 |
| BlueMoon | Malicious | Moon altering | room | — | Visible moon, good caster only: good mobs take half damage, evil take +50% | 170 |
| BountifulWomb | OK others | Breeding | mob | — | Adds to a target's pregnancy effect (offspring count), chance `50 + 5*adjLevel + 10X` | 125 |
| Boulderbash | Malicious | Rock control | mob | — | Caves, mountains, rocky rooms only; ranged rock hit | 102 |
| BreatheAir | OK others | Shape shifting | mob | — | Adds air to breathables (not underwater) | 124 |
| BreatheWater | OK self | Shape shifting | mob | 12 | Gill body part; fresh and salt water breathable | 107 |
| BreathColor | OK self | Weather | mob | — | Look and examine show another mob's "breath colour" | 292 |
| Brittle | Malicious | Enduring | item | 10 | Item is destroyed on its next wear-and-tear use | 152 |
| BrownMold | Beneficial self | Plant growth | mob | — | In combat, summons a brown-mold ally made of hemp; ends when combat ends | 166 |
| Bury | Indifferent | Deep magic | item | 14 | Buries a corpse in an outdoor hole (`HoleInTheGround`) | 91 |
| BullStrength | Beneficial self | Shape shifting | mob | — | STR +6+X | 98 |
| CalmAnimal | OK others | Animal | mob | 6 | Target must be in combat; 3-tick effect; anyone fighting the target is made peaceful | 76 |
| CalmSeas | Indifferent | Weather | area | — | Sea is forced calm each tick; rival water chants are refused and shorten it | 183 |
| CalmWeather | Indifferent | Weather | area | 15 | Steps area weather toward calm; XP when balance is restored | 123 |
| CalmWind | Indifferent | Weather | room | 9 | Clears wind and storms in the area; otherwise a still-air room effect blocks water-borne boats | 170 |
| CallCompanion | Indifferent | Animal | none | 9 | Sends the first eligible group animal to the caster's room by `Skill_Track` | 76 |
| CallMate | Indifferent | Animal | mob | — | In animal form, in the wild, with no group: summons a same-race companion | 171 |
| Camelback | Beneficial self | Shape shifting | mob | — | Thirst maximum set to 999999 | 119 |
| CatsGrace | Beneficial self | Shape shifting | mob | — | DEX +6+X | 98 |
| CaveFishing | Indifferent | Animal | room | — | Cave water source becomes a random fish resource; reverted on end | 89 |
| CaveIn | Malicious | Rock control | mob or exit | — | Rubble blocks an exit or pins a creature; damage on mobs | 142 |
| Barkskin | Beneficial self | Shape shifting | mob | 9 | Armour value −10−2X | 92 |
| ChantWard | Beneficial self | Preserving | mob | — | 35% of malicious chants aimed at the target are blocked | 129 |
| ChargeMetal | Malicious | Enduring | item or mob | — | Metal worn by the target draws electrical damage to the wearer | 204 |
| CharmAnimal | Malicious | Animal | mob | 15 | Charms an animal (mind-affecting); it follows the caster; on end it returns to its natural will | 235 |
| CharmArea | Malicious | Enduring | room | 24 | Room where no malicious act is allowed; leaving refused unless following the caster | 140 |
| CheetahBurst | Beneficial self | Shape shifting | mob | — | Speed bonus on one tick in three; movement regained each tick | 127 |
| Chlorophyll | Beneficial others | Shape shifting | mob | — | Daylight outdoors: hunger satisfied (+2 per tick) | 130 |
| ClearMoon | Indifferent | Moon altering | room | — | Removes moon-altering and moon-summoning effects from room, occupants and items | 89 |
| CloudWalk | Indifferent | Enduring | mob | — | Flying; group members receive it each tick | 146 |
| ColdMoon | Malicious | Moon altering | room | — | Room effect: occupants' disease save reduced by `50 + 5X` | 168 |
| ColdWard | Beneficial self | Preserving | mob | 14 | Cold save +100 | 93 |
| ControlFire | Malicious | Enduring | mob | 8 | A fire source on the target flares and attacks; range 0 to adjusted 5 | 126 |
| ControlPlant | Indifferent | Plant control | item | — | Takes control of a plant item (one with a `Chant_SummonPlants` effect) from its controller | 93 |
| Capsize | Malicious | Water control | item (boat) | — | On the water surface, tips a boat: non-fixed cargo falls overboard; blocked by the highest-level occupant's justice save | 208 |
| Burrowspeak | (inherited) | Animal | none | — | Speak with rodents, ophidians, mustelids and worms; extends `Chant_SpeakWithAnimals` (Chant_Burrowspeak.java:35), rules at lines 60-72 | none |

Sample player-facing lines in the bucket (short, quoted as written):

- "Extreme emotions disrupt your chant." (Chant.java:178)
- "The water is magically calm here, and will not heed your call." (Chant_CalmSeas.java:172)
- "Acid rain starts pouring from the sky!" (Chant_AcidRain.java:166)
- "The soil is blighted!" and "The blight is ended." (Chant_Blight.java:190, 91)
- "The Blue Moon Rises!" and "The Cold Moon Rises!" (Chant_BlueMoon.java:209, Chant_ColdMoon.java:207)
- "Time moves forwards!" and "Time moves backwards!" (Chant_AlterTime.java:104-106)
- "The sea changes color as it comes under control!" (Chant_CalmSeas.java:208)
- "This area is under an enchantment of climactic balance, which can not be calmed for @x1." (Chant_CalmSeas.java:198)

## Notable design ideas

- **Tags that carry meaning.** `classificationCode()` packs an ACODE and a DOMAIN into one int (Ability.java:671-888). Systems then ask "is this a moon effect?" with a mask, and no registry is needed. That is how moon chants stop each other (section 6).
- **Effects that edit the message stream.** Several effects work through `okMessage` and `executeMsg` rather than through stats. ChargeMetal turns an electrical hit into a hit on the wearer. CharmArea removes malicious flags from messages. ChantWard vetoes 35% of incoming chants. These are compact, and a single-player game can reuse the pattern.
- **Weather as a state machine.** The chants change the area's `Climate` state (`setNextWeatherType`, `forceWeatherTick`) and do not invent their own weather. `weatherQue()` says what each state allows (Chant.java:126-165).
- **Scale with area size.** CalmWind, CalmSeas and CalmWeather subtract `numberOfProperIDedRooms / (level + 2X)` from the proficiency roll (section 5). Controlling a large area is harder, which is a plain and sensible rule.
- **Descriptive adjectives instead of numbers.** BreathColor expresses a relative level as words ("faded", "bright", "brilliant", "dazzling") and gives traits like "stripes" or "swirl" (BreathColor.java:88-275). A player learns something about another creature without seeing its stats.
- **Cheap periodic effects.** CheetahBurst uses a three-tick counter; Chlorophyll satisfies hunger during the day (section 5). Both are small and readable.

## Problems and cruft

- **Mana spent before the disruption roll.** `Chant.invoke` calls `super.invoke` (which deducts mana, StdAbility.java:2074-2076) and only then `chantAlignmentCheck` (Chant.java:194-198). A disrupted chant costs mana and gives nothing back.
- **Two 50% disruption rolls.** `Chant.chantAlignmentCheck` (Chant.java:169-175) and `CharClasses/Druid.java:460-463` both roll for a Druid's chant with the same message. Whether both fire on one cast was not verified.
- **Description and code disagree.** `Druid.getOtherLimitsDesc` says the Druid "Must remain Neutral to avoid skill and chant failure chances" (Druid.java:437). The code checks factions (`appropriateToMyFactions`, StdAbility.java:2865-2888), not neutrality. How faction data maps to neutrality was not checked.
- **Unreachable branch in BreathColor.** `sourceCode` starts at -1 (Chant_BreathColor.java:93) and is only set to 0 (lines 153-157). The `case 1` ("swirl") branch can never run as written.
- **Always-true guard in Capsize.** `if(chanceToFail > Integer.MIN_VALUE)` (Chant_Capsize.java:276) can never be false for an `int`.
- **Non-short-circuit `&`.** `&(CMLib.flags().canTrack(M))` (Chant_BloodyWater.java:202) uses a bitwise `&` on booleans. It works, but it evaluates both sides and is probably a typo for `&&`.
- **Empty catch.** AnimalCompanion catches `Throwable` around the effect copy and does nothing (Chant_AnimalCompanion.java:192-195).
- **Duplicated lines.** ColdWard's `invoke` sets `target` from `givenTarget` twice (Chant_ColdWard.java:95-97 and 104-105).
- **Leftover name.** AiryAura reads `paladinsGroup` for a druid's group (Chant_AiryAura.java:137). Its `affectCharStats` adds fresh and salt water to breathables (lines 187-201), not air.
- **Abilities used as data slots.** CalmSeas stores its per-refusal cost in `abilityCode()` through `setAbilityCode` (Chant_CalmSeas.java:70-80). The generic code slot holds effect state.
- **Ignored flag.** AnimalSpy sets `AUTOEXITS` on the spy (Chant_AnimalSpy.java:210). This file never clears it (only one occurrence, checked by grep).
- **Astral death on timeout.** `tick` posts death to `affected` (the body) when the timer reaches 1 (Chant_AstralProjection.java:109-113). The code does not check whether the spirit is still out, so a timeout while the spirit is away kills the body. Not run; read from the code.
- **Only one companion per cast.** `CallCompanion` breaks out of its loop at the first eligible follower (Chant_CallCompanion.java:103-105) and returns after one `Skill_Track` (line 141). The name suggests all companions.
- **Duplicated weather code.** CalmWind (Chant_CalmWind.java:190-242) and CalmWeather (Chant_CalmWeather.java:130-190) have near-identical weather switches and the same size formula.
- **Overlapping thresholds.** CallMate refuses to start with more than one group member (Chant_CallMate.java:207-210) but ends the effect at more than two (tick, line 108). The two limits are not the same, and no comment explains it.

## Takeaways for a Python SUD

- **Spell list as data.** Use one table of records: id, name, domain (enum), quality (enum), target kind (self, mob, item, room, area, none), lowest level, mana (default `50 + level`, or fixed, or percent of max), and an effect class. The catalogue above maps directly onto that. Keep the formulas (`4L+40` beneficial, `1.1L+25` malicious, `50+L` mana) as named tunables, not literals.
- **Effects with three hooks.** A small effect class with `modify_stats(stats)` (for `affectPhyStats`/`affectCharStats`), `tick(world)` and `expire()`, plus `on_action(event) -> bool` for the veto and rewrite role of `okMessage`. A few dozen lines covers most of this file. Use a dataclass plus `copy.copy` in place of `copyOf()`.
- **Weather as a table.** Model each region's weather as a state with a transition table. Calm-the-weather chants are then rows: `{WINDY: CLEAR, THUNDERSTORM: RAIN, BLIZZARD: SNOW, DUST: CLEAR, FOG: CLEAR, ...}`, plus a `weather_allows(spell)` set in place of the `weatherQue` bitmask.
- **Domain tags for stacking.** Give each effect a domain tag (moon, weather, animal, rock). A room may hold one effect per domain. This replaces the scan of effects in `ClearMoon` and `ColdMoon`.
- **Check before paying.** Run the disruption roll (if you keep one) before deducting mana. The CoffeeMud order (pay, then roll) is worse for the player.
- **Turn-based timing.** Durations here are in 4-second ticks. A turn-based SUD can count game turns or in-world hours. `AlterTime` shows the idea: an hour shift is a world-clock operation, not an effect timer.
- **Single-player simplifications.** Drop the group-size checks, the abuser level cap (`aPossibleAbuserOfCasterLevel`, StdAbility.java:2284-2310), session swapping (AstralProjection) and the plant-ownership handover (ControlPlant, which loads other players). A companion list of one to three animals replaces the group logic. Keep the notion of area-wide effects, because a single player still lives in a shared world.
- **Keep the flavour generators.** `getBreathColor` and the fizzle messages (for example "<S-NAME> chant(s) to <T-NAMESELF>, but nothing happens.") are cheap to port and give the game its voice.

## Coverage

- **Read in full:** `Chant.java` (all 201 lines); `Chant_Barkskin.java` (all 126 lines); `Chant_Burrowspeak.java` (all 92 lines).
- **Read by extraction (method bodies with comments and blanks removed):** every `invoke()` of the 50 members, and their effect hooks where present (`tick`, `unInvoke`, `affectPhyStats`, `affectCharStats`, `affectCharState`, `okMessage`, `executeMsg`, `castingQuality`, and the small helpers such as `getCharmer`, `isRaining`, `isBlightable`, `getBreathColor`, `getBody`, `getAllTheStuff`, `checkBritality`, `peaceAt`). `Chant_Capsize.getHighestLevel` was read directly (lines 125-172).
- **Skimmed:** the ID, quality, classification, `canAffect`/`canTarget`, `overrideMana`, `maxRange`/`minRange` of every member (grep). Constructors, field declarations and helpers not named above (for example `Chant_ChargeMetal.wieldingMetal`, `Chant_Brittle.getItem`, `Chant_ControlFire.getFireSource`) were not read.
- **Outside this bucket, read only where a member calls them:** `StdAbility.java` (invoke flow, usage cost, proficiency, durations, affect helpers, verbal codes, adjusted level, faction check), `Ability.java` constants, `CharClasses/Druid.java` (learn levels and `okMessage`), `CharClasses/StdCharClass.java` (`isQualifyingAuthority`), `Libraries/Dice.java` (`roll`), `coffeemud.ini` (cost and tick settings), `Behaviors/WeatherAffects.java` (one render-mundane caller). Not read: `Chant_SpeakWithAnimals`, `Chant_ControlWeather`, `Chant_SummonPlants`, `Chant_Crossbreed`, `Druid_MyPlants`, `Chant_WindColor`, `Spell_WallOfAir`, `Skill_Track`, `Climate` and `TimeClock` implementations.
