# Druid chants, part 3: Find Gem to Manic Moon

> Part of the CoffeeMud NEWOVERVIEW. Bucket: `b053` (Abilities/Druid [part 3/7: Chant_FindGem .. Chant_ManicMoon]) · 49 files · 9409 lines of Java.

## Summary

These 49 classes are the middle slice of CoffeeMud's druid spell list: every chant from `Chant_FindGem` to `Chant_ManicMoon`, plus the shared `Chant` base they extend. Each class is one chant (a spell-like ability), and the set covers tracking (finding resources, plants, animals and mates), the moon family (room-wide "fields" that depend on the sky), water and flooding, body changes (legs, flippers, fur coats), growth that creates items and rooms, direct mob control and disease, healing and food, and a few mating-related effects. Most of the mechanics are one class per chant over a common base, so the set is a good model of how one spell family is built from data, rules and small overrides. For a single-player Python game the most useful parts are the room-level "field" effects, the expertise-tiered reveal chants, the tracking trail, and the shared proficiency and duration formulas, which can be ported with the multiplayer and clan details removed.

## Key files and types

| File or class | Role | Lines |
|---|---|---|
| `com/planet_ink/coffee_mud/Abilities/Druid/Chant.java` (base, outside bucket, read in full) | Shared chant rules: mana usage, alignment and armor gate for player casters, mundane rendering | 201 |
| `com/planet_ink/coffee_mud/Abilities/Druid/Chant_FindPlant.java` | Tracking base: trail to a resource, step hints on LOOK, range formula | 348 |
| `com/planet_ink/coffee_mud/Abilities/Druid/Chant_FindGem.java` | Subclass of FindPlant with gem materials | 91 |
| `com/planet_ink/coffee_mud/Abilities/Druid/Chant_FindOre.java` | Subclass of FindPlant with rock materials | 94 |
| `com/planet_ink/coffee_mud/Abilities/Druid/Chant_LocatePlants.java` | Tracking to plant-rich rooms, separate range | 247 |
| `com/planet_ink/coffee_mud/Abilities/Druid/Chant_LocateAnimals.java` | Tracking to a room with an animal | 246 |
| `com/planet_ink/coffee_mud/Abilities/Druid/Chant_FindMate.java` | Tracking toward a compatible partner; forces a mating command on arrival | 311 |
| `com/planet_ink/coffee_mud/Abilities/Druid/Chant_HowlersMoon.java` | Room effect that spawns and manages wolves | 341 |
| `com/planet_ink/coffee_mud/Abilities/Druid/Chant_ManicMoon.java` | Room effect that reshuffles combat targets | 232 |
| `com/planet_ink/coffee_mud/Abilities/Druid/Chant_LoveMoon.java` | Room effect that copies itself to occupants; pairing prompts | 244 |
| `com/planet_ink/coffee_mud/Abilities/Druid/Chant_HealingMoon.java` | Room effect that heals occupants each tick | 201 |
| `com/planet_ink/coffee_mud/Abilities/Druid/Chant_HoneyMoon.java` | Room effect that feeds and waters occupants | 176 |
| `com/planet_ink/coffee_mud/Abilities/Druid/Chant_Flood.java` | Room effect that swaps the atmosphere to fresh water | 298 |
| `com/planet_ink/coffee_mud/Abilities/Druid/Chant_HighTide.java` | Knocks down legged targets in a room | 285 |
| `com/planet_ink/coffee_mud/Abilities/Druid/Chant_Hippieness.java` | Mind-altering debuff with clan removal and forced item habits | 266 |
| `com/planet_ink/coffee_mud/Abilities/Druid/Chant_Hibernation.java` | Sleep-like regeneration state with a lock-in period | 264 |
| `com/planet_ink/coffee_mud/Abilities/Druid/Chant_IllusionaryForest.java` | Room that redirects LOOK to a generated forest description | 220 |
| `com/planet_ink/coffee_mud/Abilities/Druid/Chant_Labyrinth.java` | Copies a room into a generated maze, dissolved by caster | 219 |
| `com/planet_ink/coffee_mud/Abilities/Druid/Chant_MagneticEarth.java` | Room field that strips metal items from occupants | 214 |
| `com/planet_ink/coffee_mud/Abilities/Druid/Chant_MagneticField.java` | Mob effect that holds metal-armored targets in place | 195 |
| `com/planet_ink/coffee_mud/Abilities/Druid/Chant_FungusFeet.java` | Progressive foot disease ending in amputation | 231 |
| `com/planet_ink/coffee_mud/Abilities/Druid/Chant_KnowAnimal.java` | Expertise-tiered report on an animal | 294 |
| `com/planet_ink/coffee_mud/Abilities/Druid/Chant_GrowOak.java` | Planted oak with a hit-point pool; extends Chant_SummonPlants (outside bucket, not read) | 187 |
| `com/planet_ink/coffee_mud/Abilities/Druid/Chant_GrowItem.java` | Creates a random wooden item through the Carpentry craft | 143 |
| `com/planet_ink/coffee_mud/Abilities/Druid/Chant_Grapevine.java` | Relays speech near the caster's plants to the caster | 198 |
| `com/planet_ink/coffee_mud/Abilities/Druid/Chant_FishyFecundity.java` | Social-triggered pregnancy effect for water-breathing races, with age and clothing gates | 171 |
| `com/planet_ink/coffee_mud/Abilities/Druid/Chant_KillerVine.java` | Summons a vine monster; extends Chant_SummonVine (outside bucket, not read) | 124 |

The remaining classes (about 25 short chants) are catalogued in the Content section below.

## How it works

Citations are relative to `ref/mudlibraries/CoffeeMUD/`.

### The shared chant contract

- **Identity and class.** Each chant overrides `ID()`, `name()`, `classificationCode()` and `abstractQuality()`. Chant classification is `Ability.ACODE_CHANT` (value 8, `com/planet_ink/coffee_mud/Abilities/interfaces/Ability.java:687`) OR'ed with a domain such as `DOMAIN_MOONALTERING` (`Ability.java:774`). Domains are stored shifted by 5 bits (`27<<5`), so the domain is read with `classificationCode() & Ability.ALL_DOMAINS`.
- **Quality.** `abstractQuality()` is a tag used by the AI and by targeting: `QUALITY_MALICIOUS=0`, `QUALITY_INDIFFERENT=1`, `QUALITY_OK_SELF=2`, `QUALITY_OK_OTHERS=3`, `QUALITY_BENEFICIAL_SELF=4`, `QUALITY_BENEFICIAL_OTHERS=5` (`Ability.java:1083-1093`). Each chant's `castingQuality()` can downgrade itself to `QUALITY_INDIFFERENT` when it would be pointless (for example `Chant_FungusFeet.java:177-188` refuses a target with no feet).
- **Targets.** `canAffectCode()` and `canTargetCode()` choose mobs, items, rooms or exits (`CAN_MOBS=1`, `CAN_ITEMS=2`, `CAN_ROOMS=8`, `CAN_EXITS=16`, `Ability.java:1100-1108`). Room-level chants usually set `canTargetCode()` to 0 and act on `mob.location()`.
- **Triggers.** `Chant.java:84` sets the trigger words `CHANT` and `CH`. `Chant.java:111-117` honours a misc-text `render mundane`, which turns the chant into a quiet skill-like act (`verbalCastCode` at `Chant.java:69-82`).
- **Player-caster gate.** `Chant.invoke()` (`Chant.java:191-200`) calls `StdAbility.invoke()` first, then `chantAlignmentCheck()` (`Chant.java:167-189`). For a non-monster caster who owns the ability, not in auto mode, with a 50% roll (`rollPercentage()<50`), the chant fails with "Extreme emotions disrupt your chant." when the caster is not appropriate to their faction, or with "armor absorb" when `CMLib.utensils().armorCheck(mob, ARMOR_NONMETAL)` fails. This happens after mana is spent.

### Costs, success and duration (shared with all families)

- **Mana.** `usageType()` defaults to `USAGE_MANA` (`com/planet_ink/coffee_mud/Abilities/StdAbility.java:299-302`). `overrideMana()` defaults to `COST_NORMAL` (`StdAbility.java:309-312`). The costs are charged in `StdAbility.invoke()` at `StdAbility.java:2074-2076`, and `helpProficiency()` is called at `StdAbility.java:2077`, so a chant that fails validation after `super.invoke()` still spends its mana. Overrides in this bucket: `Chant_GrowItem.java:76-78` returns a fixed 50, and `Chant_GrowOak.java:76-78` returns `COST_ALL`.
- **Success roll.** `proficiencyCheck()` (`StdAbility.java:1441-1486`) computes `chance = proficiency + 2*expertise`, clamps it to 5..95 unless the `DIS955RULE` security flag is set (`StdAbility.java:1470-1476`), then adds `expertise/2`, then applies `adjustment` (a negative adjustment fails when `rollPercentage() > 100 + adjustment`, `StdAbility.java:1480-1484`), and finally succeeds when `rollPercentage() < chance`. Here `expertise` is `getXLEVELLevel(mob)`, and `rollPercentage()` returns 1..100 (`com/planet_ink/coffee_mud/Libraries/Dice.java:409-412`).
- **Hostile duration.** `maliciousAffect()` (`StdAbility.java:2185-2208`) copies the ability and starts a tick-down. `getMaliciousTickdownTime()` (`StdAbility.java:2174-2184`) returns a positive argument as an absolute tick count, returns the default when the argument is 0, and treats a negative argument as a percentage of the default. The default is `round(1.1 * adjustedLevel) + 25` ticks (`StdAbility.java:2178`), adjusted by the target's level gap for mobs (`StdAbility.java:2140-2172`), with a minimum of 2.
- **Beneficial duration.** `getBeneficialTickdownTime()` (`StdAbility.java:2312-2330`) uses `casterLevel*4 + 40` ticks as the default, with the same positive, zero and negative conventions.
- **Tick length.** The default tick is 4000 ms (`com/planet_ink/coffee_mud/core/CMProps.java:681`, `TIME_TICK = 4000`), so 20 room ticks is about 80 seconds and 240 ticks is about 16 minutes.
- **Instances.** Each chant class is a template. `copyOf()` clones the object and calls `cloneFix()` (`StdAbility.java:1419-1438`), and `beneficialAffect()` and `maliciousAffect()` create the effect instance that actually sits on the target. Fields such as `theTrail` are copied in `cloneFix()` (`Chant_FindPlant.java:132-142`).
- **Removal.** `unInvoke()` on the base (`StdAbility.java:1501-1539`) removes the effect from rooms or mobs and recomputes stats. Subclasses add messages and cleanup, and check `canBeUninvoked()` first.

### Tracking family (FindPlant, FindGem, FindOre, LocatePlants, LocateAnimals, FindMate)

**Data model.** `Chant_FindPlant.java:78-82` holds `lookingFor` (noun for messages), `displayText`, `theTrail` (a `List<Room>` from the caster to the goal), `nextDirection` (a code, see below) and `whatImLookingFor` (a resource code). `okMaterials()` and `okResources()` define what can be sought (`Chant_FindPlant.java:84-98`: vegetation and wood, plus cotton and hemp). `allOkResources()` expands these by iterating `RawMaterial.CODES.ALL()` (`Chant_FindPlant.java:101-130`). Gems use precious and glass materials (`Chant_FindGem.java:69-70`), and ore uses rock materials with mithril excluded (`Chant_FindOre.java:69`).

**Control flow.**
1. `invoke()` (`Chant_FindPlant.java:279-347`) first unInvokes every other tracking effect on the caster (`CMLib.flags().flaggedAffects(mob, FLAG_TRACKING)`, `Chant_FindPlant.java:290-292`). Only one tracker runs at a time.
2. `LIST` prints the valid resources. Any other argument goes through `findWhatImLookingFor()` (`Chant_FindPlant.java:212-233`), which sets `displayText` to "(Finding X)" on success.
3. If the resource is already in the current room, the chant says where it is and returns true (`Chant_FindPlant.java:318-323`). Mana has already been spent at this point.
4. `makeTheTrail()` (`Chant_FindPlant.java:253-276`) runs a radiant search with `CMLib.tracking().getRadiantRoomsToTarget` and a filter that accepts rooms holding the resource. It keeps the last element of the returned list (`Chant_FindPlant.java:271`; the list order is in `TrackingLibrary`, not read), then calls `findTrailToAnyRoom` with grid flags `NOEMPTYGRIDS`, `NOAIR`, `PASSABLE` and `NOWATER` (`Chant_FindPlant.java:235-244`).
5. On success the chant copies itself onto the target, sets `nextDirection` from the trail, and calls `recoverPhyStats()` (`Chant_FindPlant.java:336-340`).

**Step hints.** The tracker works through the LOOK command rather than a map. `executeMsg()` (`Chant_FindPlant.java:187-200`) recomputes `nextDirection` when the affected mob looks at the room. `tick()` (`Chant_FindPlant.java:145-184`) then reports it through the mob tick (`TICKID_MOB`):
- `-2` means idle;
- `-1` means the trail has fizzled out here ("The trail fizzles out here.") and the effect ends;
- `-999` means finished;
- `999` means arrived, and the mob is told what is here before the effect ends;
- `0` or more means the mob is told "You sense X <direction>.".

**Range formulas.** These are the main differences between the members of the family:
- FindPlant: `50 + 2*X + 10*M` (`Chant_FindPlant.java:258`), where X is `getXLEVELLevel` and M is `getXMAXRANGELevel`.
- FindMate: `50 + 10*M + X` (`Chant_FindMate.java:260`).
- LocatePlants: `50 + X + 2*M` (`Chant_LocatePlants.java:206`).
- LocateAnimals: `20 + X + 2*M` (`Chant_LocateAnimals.java:199`).

**Stealth.** `affectPhyStats()` sets `PhyStats.CAN_NOT_TRACK` on the target (`Chant_FindPlant.java:247-251`, `Chant_FindMate.java:188-192`), so the tracked mob cannot be tracked in turn.

**Mate tracking (FindMate).** The target must be M or F reproductive code (`Chant_FindMate.java:231-240`). `isSuitableMate()` (`Chant_FindMate.java:210-229`) rejects the same reproductive code, requires M or F, requires a race that is Human or can breed with the mate's race, and requires no worn waist or leg items and visibility. The effect's `tick()` (`Chant_FindMate.java:104-143`) checks for a suitable partner in the current room. When one is found it says "You peer longingly at X.", removes any worn waist or leg items, issues a forced mating command toward that partner, and expires. Otherwise it walks the trail (`Chant_FindMate.java:166-181`). The route is computed in `invoke()` from the caster's location to a room that holds a suitable partner for the target (`Chant_FindMate.java:260-276`).

### The moon family (HowlersMoon, ManicMoon, LoveMoon, HealingMoon, HoneyMoon)

**The sky gate.** Every moon chant checks `Climate.canSeeTheMoon(room, butNotA)` (`com/planet_ink/coffee_mud/Common/DefaultClimate.java:228-239`). The room sees the moon if `canSeeTheStars()` is true, or if a `DOMAIN_MOONSUMMONING` effect is present on the room (`Ability.java:772`). `canSeeTheStars()` (`DefaultClimate.java:242-256`) needs night or dusk, a sky, and weather that is not one of the blocking types (blizzard, hail, sleet, snow, rain, thunderstorm, cloudy, fog, dust storm and others; the full list was not read). The moon chant is therefore a time-and-weather-dependent field.

**Exclusivity.** A room can hold one `DOMAIN_MOONALTERING` effect. Each invoke checks for an existing one and refuses with "The moon is already under X, and can not be changed until this magic is gone." (for example `Chant_LoveMoon.java:210-219`). The same check drives `castingQuality()` (`Chant_ManicMoon.java:170-176`), so the AI will not waste a moon on a room that already has one.

**Room effect, mob effect.** The chant is cast on the room. Its `tick()` runs every room tick and either acts on the room's occupants or, in LoveMoon's case, copies itself onto each occupant (`Chant_LoveMoon.java:170-182`). The occupant copy then ends itself when the room's copy is gone (`Chant_LoveMoon.java:113-114`).

**HowlersMoon: summons wolves.** Per room tick:
- The room effect unInvokes when the moon is hidden (`Chant_HowlersMoon.java:127-128`). The method continues after that call (no return), so a spawn can still happen in the same tick.
- After 20 ticks (`Chant_HowlersMoon.java:130`), it counts the wolves carrying its effect. It stops spawning if there are more than 5, or more than `(caster level + 2*X)/10` (`Chant_HowlersMoon.java:141`).
- The spawn level is `lowestQualifyingLevel(ID)+5`, or `caster level + 5 + 2*X` when there is a caster (`Chant_HowlersMoon.java:153-155`).
- The wolf appears at a random open exit that leads to an outdoor, non-air room with open exits both ways (`fillChoices`, `Chant_HowlersMoon.java:192-208`), walks into the room, and then has a 33% roll to follow the caster and a roll above 66 to attack the caster (`Chant_HowlersMoon.java:168-178`). Wolves get the effect with `TICKS_ALMOST_FOREVER` (`Chant_HowlersMoon.java:179`).
- The wolf template (`determineMonster()`, `Chant_HowlersMoon.java:301-340`) is a `GenMob` of race Wolf, weight 350, neutral alignment, and the `CorpseEater` and `Emoter` behaviours. The emoter's text is "howls at the moon" (`Chant_HowlersMoon.java:330`).
- Unverified: nothing in this file removes the wolves when the moon sets. The mob-side tick has no branch for mobs (`Chant_HowlersMoon.java:118-190`), so the wolves appear to persist until killed. The "The howlers moon sets." message is the only thing shown at room end (`Chant_HowlersMoon.java:98-100`).

**ManicMoon: disorder.** Each room tick (`Chant_ManicMoon.java:94-153`):
- Every occupant other than the caster is checked. If it is in combat, a 20% roll drops its target and otherwise picks a random inhabitant as the new target. If it is not in combat, a 20% roll picks a random new target (`Chant_ManicMoon.java:106-129`).
- A random occupant emotes with a 50% roll and a 1d5 table: "howl(s) at the moon!", "wig(s) out!", "get(s) confused!", "sing(s) randomly!", "go(es) nuts!" (`Chant_ManicMoon.java:131-150`).
- The room effect is ended when the moon is hidden (`Chant_ManicMoon.java:103-104`).

**LoveMoon: pairing and charm.**
- Occupants gain the room effect as a copy, and each copy gives +6 Charisma (`Chant_LoveMoon.java:188-192`).
- Each mob tick the mob picks a random occupant from a candidate set. Candidates must be visible, of the opposite gender, both must have a reproductive code other than N, and the candidate's Charisma save must be above 14 (`Chant_LoveMoon.java:117-129`).
- A 1% roll removes waist and leg items and issues a forced mating command toward the candidate. Otherwise, on rolls above 10, the mob gets one of five "feel..." messages about the candidate (`Chant_LoveMoon.java:132-161`).
- The set of conditions has no age test, unlike FishyFecundity (see Problems).

**HealingMoon and HoneyMoon.** HealingMoon calls `CMLib.combat().postHealing(M, M, this, CON, ...)` for every occupant each room tick (`Chant_HealingMoon.java:116-118`). HoneyMoon adds `+10` hunger and `+10` thirst through `adjHunger` and `adjThirst` (`Chant_HoneyMoon.java:116-120`), bounded by the maximum hunger and thirst for the occupant's weight.

### Water and flood (Flood, HighTide, Flippers, LandLegs)

- **Flood** (`Chant_Flood.java`). Indoors only (`Chant_Flood.java:205-209`). `castingQuality()` rates the chant low unless the caster can breathe fresh water and a water room is adjacent (`Chant_Flood.java:111-123`). `invoke()` refuses when no water room is nearby ("There is no water nearby to call in a flood from.", `Chant_Flood.java:240-244`). The chant saves the room's old atmosphere, sets the atmosphere to fresh water (`Chant_Flood.java:211`, `:286-288`), and adds `Flooded!` ambiance with the swimming disposition (`Chant_Flood.java:192-197`). Each tick it asks non-breathing occupants to revenge attack the caster (`Chant_Flood.java:126-144`). When the effect ends it restores the saved atmosphere from the misc text (`Chant_Flood.java:100-104`). Its range is `adjustedMaxInvokerRange(10)` (`Chant_Flood.java:70-72`).
- **HighTide** (`Chant_HighTide.java`). Every mob in the room that is not flying is a target (`Chant_HighTide.java:232-237`). Each target that has legs and does not already have the effect is set to sitting for 3 ticks (`Chant_HighTide.java:263-265`) and takes 20 damage (`:269`). A target that tries to stand is always blocked (`okMessage`, `Chant_HighTide.java:110-127`), and the "attempt(s) to stand up, and slips!" message is shown at most once per tick (`oncePerRd`, `Chant_HighTide.java:89-96`). The sitting disposition is applied in `affectPhyStats()` (`Chant_HighTide.java:99-107`).
- **Flippers** (`Chant_Flippers.java`). Adds a tail body part (`:85-89`) and makes a swimming mob's move into a water room carry on through up to `adjustedLevel/10` further water rooms that have no room id (`:113-146`). It uses an `AtomicBoolean` guard to stop recursion.
- **LandLegs** (`Chant_LandLegs.java`). Adds legs and feet when there are none (`:98-101`), removes the tail (`:103-104`), swaps saltwater and freshwater breathing for air (`:106-134`), and clears the feet and legs wearability bits (`:135`). It returns BENEFICIAL_SELF for an underwater caster who cannot breathe the room (`:66-74`).

### Mob control, combat disruption and debuffs

- **Hippieness** (`Chant_Hippieness.java`). `affectCharStats()` lowers Wisdom by 2 with a floor of 1 (`:89-91`) and clears clans (`:92-94`). `okMessage()` blocks crafting, building and common-skill actions except teaching and FoodPrep or Cooking (`:107-118`). Each tick it removes clans again (`:130-132`), grabs a random room item that fits the mouth (`:134-141`), cancels Fighter Bezerk and Song of Rage (`:143-148`), and forces a light item worn at the mouth. An empty smokable container gets a hemp-based "smoke" resource (`:159-181`). The unInvoke restores the saved clans (`:201-202`).
- **Hibernation** (`Chant_Hibernation.java`). The caster must not be in combat (`:234`) and must be sitting (`:239`). On success it applies a permanent-until-ended effect (`:254`). Each mob tick (`:184-229`) runs a proficiency check and, if the mob is not in combat and is asleep, adds:
  - mana of `round(0.1*(INT+WIS) + (level + 2*X)/2)` (`:200-202`);
  - movement of `round(0.1*STR + (level + 2*X)/2)` (`:205-207`);
  - hit points of `round(0.1*CON + (level + 2*X)/2)` through `postHealing` (`:210-216`), or it ends if healing fails.

  Leaving is blocked for the first 10 rounds, "You can't withdraw from hibernation just yet." (`:172-176`). The display text counts down the seconds left (`:57-65`). The DEEP and FULLMANAREVOKE misc-text flags are parsed in `setMiscText()` (`:98-103`).
- **Magnetic Field** (`Chant_MagneticField.java`). While the target wears metal armour (a non-held metal item that is not inventory, held or wielded), `okMessage()` blocks leaving, entering, advancing and retreating with "Your metal armor is holding you in place!" (`:104-116`). It also blocks using or targeting the metal items the target owns (`:119-136`). The invoke computes `levelDiff = target level - (caster level + 2*X)`, clamped at 0 (`:165-167`). The proficiency check uses `-2*levelDiff` as its adjustment (`:171`). The effect duration uses `-levelDiff` as a percentage (`:182`).
- **Magnetic Earth** (`Chant_MagneticEarth.java`). A room effect that, each tick, drops metal or mithril items that are carried, held, wielded, worn on eyes or mouth, from every occupant who is not the caster or a group member (`:85-115`). It starts ticking on the first entry (`:132-143`). It can only be cast in cave, mountain, rock or city rooms (`:166-178`, `:146-164`).
- **Fodder Signal** (`Chant_FodderSignal.java`). The target gets glowing and `+10 + X` armour (`:98-103`). Each tick, every monster in the room that is not in combat, not in the caster's group and can see the target howls at it and attacks (`:113-137`).
- **Hold Animal** (`Chant_HoldAnimal.java`). Only animals (`:133-135`). It computes a level gap (`:142-143`), and `maliciousAffect(..., 8, ...)` gives an 8-tick hold (`Chant_HoldAnimal.java:156`). The hold sets `CAN_NOT_MOVE` in the senses mask (`:93`).
- **Fungus Feet** (`Chant_FungusFeet.java`). A progressive disease with two counters. `plagueDown` counts 8 ticks first, then 10 per cycle (`:94`, `:111-114`), and `drawups` starts at 1.0 and rises by 0.1 each cycle (`:95`, `:117`). Each cycle deals 1 point of disease damage (`:138-142`). When `drawups` reaches 3.1, the feet "rot off", the `Amputation` ability is invoked on the feet (up to 99 times, `:118-137`), and the effect ends. Movement is divided by `drawups + 0.1*X1` (`:148-154`). The effect is applied with `TICKS_ALMOST_FOREVER` (`:218`), and a resisted cast gives the target `spreadImmunity` (`:222`). Cure on removal gives temporary immunity to this chant (`:170`; `StdAbility.java:2351-2365`).
- **Feel Hunger** (`Chant_Hunger.java`). An instant effect with no lasting state. It lowers hunger by `150 + 5*(level + 2*X)` through `adjHunger` with a negative value (`Chant_Hunger.java:104`). `adjHunger` scales negative changes by `HUNGER_LOSS_PCT` and floors at zero (`com/planet_ink/coffee_mud/Common/DefaultCharState.java:185-200`).

### Room-scale chants (Labyrinth, IllusionaryForest, GroveWalk, GrowForest)

- **Labyrinth** (`Chant_Labyrinth.java`). The room must have a non-empty room id (`:141-145`). The chant builds a new `CaveMaze` locale that is 10 by 10 (`:161-163`), with a "The Labyrinth" title, `Prop_NoTeleportOut` (`:165`), and the original room's real exits copied to the matching sides (`:173-182`). Everyone in the room is moved to a random grid cell, and anyone fighting is made peaceful first (`:185-210`). The maze lasts `beneficialAffect` duration (`:184`). It is dissolved on unInvoke (`:85-107`). When any occupant quits, shuts down, expires or resets while inside, `okMessage()` moves that mob back to the original room (`:110-125`), and when the caster is the one leaving, the maze is dissolved (`:121-122`).
- **IllusionaryForest** (`Chant_IllusionaryForest.java`). Creates a `Woods` locale with one of ten descriptions by `d10` (`:137-185`). The room effect intercepts LOOK and EXAMINE (`:116-134`) and sends them to the decoy room, blocking the original. If the caster owns the land (`CMLib.law().doesOwnThisLand`), the effect is non-uninvokable and the room is saved (`:205-209`). Otherwise it is temporary (`:211`).
- **Grove Walk** (`Chant_GroveWalk.java`). Requires a `DruidicMonument` item in the current room (`:90-110`) and a named destination grove that also has one. Followers emerge there with an enter message (`:131-149`).
- **Grow Forest** (`Chant_GrowForest.java`). Sets the room's resource to a chosen tree material (`:118-130`). It refuses indoor rooms (`:79-85`).

### Plant and growth chants

- **Grow Oak** (`Chant_GrowOak.java`). Extends `Chant_SummonPlants` (outside bucket, not read). It plants a GenItem oak tree, not gettable, weight 10000, level `10 + X1` (`:89-101`). The tree's hit-point pool is `100*(level + 2*X + 10*X1)` (`:108`) and it lasts `adjustedLevel*240 + 450` ticks (`:110`). A caster can have one oak at a time (`:165-185`). The tree's tick (`:116-161`) makes the caster's HP loss be absorbed by the oak by healing the caster back and reducing the pool (`:126-140`). Others chopping the oak (a `Chopping` effect) deal 50 to 100 each tick to the pool and 25 to 50 to the caster (`:142-151`). When the pool is exhausted by absorbed damage the oak is unInvoked (`:137-138`). When it is exhausted by chopping, the code calls `CMLib.combat().postDeath(invoker, ...)` on the caster before unInvoking (`:152-156`), so chopping the oak down kills its druid.
- **Grow Item** (`Chant_GrowItem.java`). Works only in wood, jungle or wooden-resource rooms (`:82-86`). It makes a wooden item through `Carpentry.craftAnyItem` (`:119-122`), dropped as an `Expire.Resource` item (`:130-132`) at a fixed cost of 50 mana.
- **Grow Club** (`Chant_GrowClub.java`). A blunt wooden club, weight 10, level-scaled damage and attack adjustment (`:135-155`). Works in woods or jungle (`:97-99`).
- **Grow Food** (`Chant_GrowFood.java`). Outdoor rooms only (`:79-81`), not city rooms (`:84-89`). Makes "a pound of X" with nourishment `150 + 10*X1`, or 1 for herbs (`:146-150`). A `list` argument prints the food types.
- **Goodberry** (`Chant_Goodberry.java`). Converts berry food items that are not pills into a `GenPill` with nourishment 0, glowing and spell list `;Prayer_CureLight;` (`:84-101`). It converts up to `roll(1, level/7, 1)` items (`:137`; `Dice.java:400-401` returns the modifier when the die size is zero, so low levels convert one). It scans the owner's inventory or the room (`:143-165`).
- **Fungal Bloom** (`Chant_FungalBloom.java`). Requires a mushroom in the room (`getShroomHere`, `:77-87`) and refuses air rooms (`:98-103`, `:112-116`).
- **Free Vine** (`Chant_FreeVine.java`). Only targets a mob whose race ID is "Vine" (`:103-105`). It frees the target, which makes the target pull its roots up (`:121`).
- **Killer Vine** (`Chant_KillerVine.java`). Summons a vine monster; extends `Chant_SummonVine` (outside bucket). The monster's level is the larger of the adjusted caster level and `caster level - 5 + X/2` (`:76-79`). Its damage is `30 + 9*(level/5)`, attack adjustment `10 + level`, armour `100 - (30 + level/2)`, and it gets the sense of seeing in the dark (`:94-99`).

### Shape, protection and senses

- **Fur Coat** (`Chant_FurCoat.java`). Refuses if the target is shape-shifted (`Druid_ShapeShift.isShapeShifted`, `:159-166`, `:185-199`) or already wears something on the torso (`:201-205`). Otherwise it creates a fur coat item with armour `2 * class level` (`:221-225`) and wears it on the torso.
- **Harden Skin** (`Chant_HardenSkin.java`). Armour lowered by `10 + 2*X` (`:75`), and saves raised: cold by `10 + level + 2X` (`:83`), fire by `level + 2X` (`:84`), water by `25 + level + 2X` (`:85`).
- **Hawkeye** (`Chant_Hawkeye.java`). Raises the overlooking save to `level + 2X + 100` plus the existing value (`:81`) and adds CAN_SEE_HIDDEN (`:88`).
- **Fire, Gas and Lightning Ward** (`Chant_FireWard.java:86-89`, `Chant_GasWard.java:86-89`, `Chant_LightningWard.java:86-89`). The same code with different saves: +100 to `SAVE_FIRE`, `SAVE_GAS` and `SAVE_ELECTRIC`. The effect is BENEFICIAL_SELF with the standard beneficial duration.

### Knowledge and inspection (tiered reveal)

- **Know Animal** (`Chant_KnowAnimal.java:96-291`). The target must be an animal (`:109-112`). The report grows with the caster's expertise `X`:
  - X > 0: stats from the Score command (`:136-146`).
  - X > 1: armour, combat and damage prowess (`:147-159`).
  - X > 2: senses (`:160-167`).
  - X > 3: behaviours and property effects (`:168-190`).
  - X > 4: racial and cultural abilities (`:201-231`).
  - X > 5: alignment and inclination factions (`:233-249`).
  - X > 6: the start room (`:250-258`).
  - X > 7: every ability the target has (`:192-200`, `:259-266`).
  - X > 8: dispositions (`:267-277`).
  - X > 9: the parent named by the PARENT tattoo (`:278-286`).

  The summoned-by tattoo is read at `:127-134`.
- **Inspect Shard** (`Chant_InspectShard.java:77-132`). Works on a Wand whose enchant type is the chant type (`:83-94`). The report reads the spell name and magic word (`:109-113`), charges above X 2 (`:114-115`), and max charges above X 5 (`:116-117`), with a further tier above X 8 (`:118`).

### Breeding and bonding (plain description)

- **Fishy Fecundity** (`Chant_FishyFecundity.java`). A mob effect. Its `executeMsg()` (`:86-135`) watches for the MATE or SEX social aimed at a partner. Both partners must be of different reproductive codes, M or F, able to breed with each other, in the same room, and water breathers. Neither may wear waist or leg items. Both must be past the child age category and before the old category, or have age 0 (`:94-120`, with the age tests at `:114` and `:117`). Then the female partner gets the Pregnancy ability through `A.invoke(male, female, true, 0)` (`:127-132`), and the effect ends. The invoke applies it with `TICKS_ALMOST_FOREVER` (`:162`).
- **Give Life** (`Chant_GiveLife.java`). Only non-player animals in the caster's group (`:106`). The caster pays experience (`:130`). If the caster outranks an animal, the amount is increased by `(caster level - target level) * (caster level / 10)` (`:131-134`). The target receives the experience (`:136`). A loyalty effect is added if a `d100` roll is below the amount, and the target has the BestowName effect and follows the caster, and the caster is a player (`:137-146`).
- **Homeopathy** (`Chant_Homeopathy.java:100-123`). Finds the target's disease affect (the last one in the effect list, `:100-105`). A roll above 66 or no disease gives "condition is unchanged." A roll above 33 gives "glow(s) a bit." Otherwise the disease's `tick()` is called up to 1000 times, stopping as soon as it returns false, which fast-forwards it to the end.
- **Fortify Food** (`Chant_FortifyFood.java`). Refuses nourishment above 1000 ("already well fortified"). Otherwise it adds 1000 nourishment (`:111`) and sets the bite size from the number of bites.

### Fire and force

- **Magma Cannon** (`Chant_MagmaCannon.java`). Range 1 to 5 (`:52-60`), and the room must be a cave, rock, mountain or desert domain by the check at `:88-96`. Damage is `roll(numDice, 9, 10)` with `numDice = round((level + 2*X1)/2)` (`:117-118`), typed as fire and burning (`:121`).

### Summary of the interactions with other systems

- **Climate and time.** `DefaultClimate.canSeeTheMoon` and `canSeeTheStars` (`com/planet_ink/coffee_mud/Common/DefaultClimate.java:228-256`) for every moon chant. `Room.INDOORS` and `domainType()` for location tests.
- **Tracking.** `CMLib.tracking()` provides `getRadiantRoomsToTarget`, `findTrailToAnyRoom`, `trackNextDirectionFromHere`, `walk` and `wanderAway` (not read).
- **Combat and healing.** `CMLib.combat().postDamage`, `postHealing`, `postRevengeAttack` and `properTargets`.
- **Commands.** `CMLib.commands().postRemove`, `postStand`, `postLook`, `postFollow`, `postSay`, plus `mob.doCommand(..., METAFLAG_FORCED)` for forced mating and wearing (`Chant_HowlersMoon.java:171`, `Chant_Hippieness.java:180`, `Chant_LoveMoon.java:141`, `Chant_FindMate.java:142`).
- **Other abilities.** `Amputation`, `TemporaryImmunity` (via `spreadImmunity`), `Loyalty`, `Pregnancy`, `Prop_NoTeleportOut`, `Prop_ModExperience`, `Prayer_CureLight` (through pill spell lists), `Carpentry` (`ItemCraftor`), `Fighter_Bezerk`, `Song_Rage`, `DiseaseAffect`.
- **Plants.** `Druid_MyPlants.addNewPlant`, `myPlantRooms`, `myPlant` track the caster's plants (`Chant_GrowOak.java:103`, `Chant_Grapevine.java:140-146`).
- **Shape-shifting.** `Druid_ShapeShift.isShapeShifted` (`Chant_FurCoat.java:163`).
- **Chant_Uplift.** Drives Hibernation in its DEEP form with `setMiscText("DEEP=true FULLMANAREVOKE=true")` (`com/planet_ink/coffee_mud/Abilities/Druid/Chant_Uplift.java:396-405`). Druid and Beastmaster classes grant Hibernation at level 18 (`com/planet_ink/coffee_mud/CharClasses/Druid.java:241`, `Beastmaster.java:225`).

## Content worth mining

### Catalogue of the 49 chants

Each row's class is in `com/planet_ink/coffee_mud/Abilities/Druid/<Class>.java`. Domain and quality come from each class's `classificationCode()` and `abstractQuality()` overrides. Numbers are as coded.

| Class | Display name | Domain | Quality | Target | What it does | Key numbers |
|---|---|---|---|---|---|---|
| Chant_FindPlant | Find Plant | PLANTCONTROL | OK_SELF | self (track) | Trail to nearest room with a chosen plant resource | range 50+2X+10M |
| Chant_FindGem | Find Gem | ROCKCONTROL | OK_SELF | self (track) | Trail to precious or glass materials | inherits FindPlant |
| Chant_FindOre | Find Ore | ROCKCONTROL | OK_SELF | self (track) | Trail to rock-type ore | inherits FindPlant |
| Chant_LocatePlants | Locate Plants | PLANTCONTROL | OK_SELF | self (track) | Trail to rooms with plant life | range 50+X+2M |
| Chant_LocateAnimals | Locate Animals | ANIMALAFFINITY | OK_SELF | self (track) | Trail to a room holding an animal | range 20+X+2M |
| Chant_FindMate | Find Mate | BREEDING | OK_OTHERS | another mob | Trail to a compatible partner; forced mating on arrival | radius 50+10M+X |
| Chant_HowlersMoon | Howlers Moon | MOONALTERING | MALICIOUS | room | Spawns wolves at outdoor exits while the moon is up | every 20 room ticks; cap 5 or (L+2X)/10 |
| Chant_ManicMoon | Manic Moon | MOONALTERING | MALICIOUS | room | Reshuffles combat targets and emotes | 20% and 50% rolls |
| Chant_LoveMoon | Love Moon | MOONALTERING | INDIFFERENT | room and occupants | Copies to occupants; +6 CHA; pairing prompts | candidates need CHA save above 14 |
| Chant_HealingMoon | Healing Moon | MOONALTERING | INDIFFERENT | room | Heals occupants each tick | heal = CON |
| Chant_HoneyMoon | Honey Moon | MOONALTERING | INDIFFERENT | room | Feeds and waters occupants | +10 hunger, +10 thirst per tick |
| Chant_Flood | Flood | WATERCONTROL | MALICIOUS | indoor room | Fresh water atmosphere, swimming, revenge on non-breathers | maxRange 10 |
| Chant_HighTide | High Tide | WATERCONTROL | MALICIOUS | room mobs | Knocks legged targets down | sit 3 ticks; 20 damage |
| Chant_KnowAnimal | Know Animal | NATURELORE | INDIFFERENT | animal | Expertise-tiered report | tiers X 0 to 9 |
| Chant_InspectShard | Inspect Shard | NATURELORE | INDIFFERENT | Wand shard | Spell, magic word, charges | tiers X 0, 2, 5, 8 |
| Chant_FurCoat | Fur Coat | SHAPE_SHIFTING | BENEFICIAL_SELF | self | Conjures a fur coat on the torso | armour 2 x class level |
| Chant_Hawkeye | Hawkeye | SHAPE_SHIFTING | BENEFICIAL_SELF | mob | Overlooking save; see hidden | save level+2X+100 |
| Chant_HardenSkin | Harden Skin | SHAPE_SHIFTING | BENEFICIAL_SELF | mob | Armour and elemental saves | armour -(10+2X) |
| Chant_LandLegs | Land Legs | SHAPE_SHIFTING | OK_SELF | mob | Legs and feet, breathes air | removes tail |
| Chant_Flippers | Flippers | SHAPE_SHIFTING | OK_OTHERS | mob | Tail; swimmer dashes through water rooms | rooms = level/10 |
| Chant_Hippieness | Hippieness | ENDURING | MALICIOUS | mob | WIS -2, clans gone, no crafting, forced habit | default duration |
| Chant_Hibernation | Hibernation | ENDURING | OK_SELF | self | Sleep-like regeneration | 10-round lock; regen per tick |
| Chant_IllusionaryForest | Illusionary Forest | ENDURING | MALICIOUS | room | LOOK shows a generated forest | d10 descriptions |
| Chant_Labyrinth | Labyrinth | DEEPMAGIC | MALICIOUS | room | Copies room into a 10x10 maze | duration = beneficial default |
| Chant_MagneticEarth | Magnetic Earth | DEEPMAGIC | MALICIOUS | room | Strips metal from occupants | cave, mountain, rock or city |
| Chant_MagneticField | Magnetic Field | DEEPMAGIC | MALICIOUS | mob | Metal armour holds target in place | -2*levelDiff adjustment |
| Chant_Grapevine | Grapevine | PLANTCONTROL | INDIFFERENT | mob and plants | Relays speech near caster's plants | effect copied to each plant |
| Chant_Goodberry | Goodberry | PLANTCONTROL | INDIFFERENT | berry items | Berries become Cure Light pills | up to level/7 items |
| Chant_GrowOak | Grow Oak | PLANTGROWTH | INDIFFERENT | room | Oak with HP pool that absorbs caster damage | pool 100*(L+2X+10X1); COST_ALL |
| Chant_GrowItem | Grow Item | PLANTGROWTH | INDIFFERENT | woods room | Random wooden item via Carpentry | 50 mana |
| Chant_GrowClub | Grow Club | PLANTGROWTH | OK_SELF | woods room | Blunt wood club | weight 10 |
| Chant_GrowFood | Grow Food | PLANTGROWTH | INDIFFERENT | outdoor room | Food item by material | nourishment 150+10X1 |
| Chant_GrowForest | Grow Forest | PLANTGROWTH | INDIFFERENT | room | Sets room resource to a tree | not indoors |
| Chant_FreeVine | Free Vine | PLANTCONTROL | OK_OTHERS | Vine race | Uproots a vine | none |
| Chant_FungalBloom | Fungal Bloom | PLANTCONTROL | MALICIOUS | items | Needs a mushroom in the room | not in air rooms |
| Chant_GroveWalk | Grove Walk | PLANTCONTROL | INDIFFERENT | caster and followers | Walk to another grove with a monument | needs DruidicMonument |
| Chant_FungusFeet | Fungus Feet | PLANTGROWTH | MALICIOUS | mob with feet | Progressive foot disease ending in amputation | about 21 cycles of 10 ticks |
| Chant_Hunger | Feel Hunger | ENDURING | MALICIOUS | mob | Instant hunger loss | 150+5(L+2X) |
| Chant_FodderSignal | Fodder Signal | ENDURING | MALICIOUS | mob | Monsters in room attack the target | armour +10+X |
| Chant_HoldAnimal | Hold Animal | ANIMALAFFINITY | MALICIOUS | animal | Cannot move | 8 ticks |
| Chant_GiveLife | Give Life | ANIMALAFFINITY | OK_OTHERS | group animal | Transfers experience; may win loyalty | loyalty chance = amount % |
| Chant_Homeopathy | Homeopathy | PRESERVING | OK_OTHERS | mob | Rolls against a disease | fast-forward up to 1000 ticks |
| Chant_FortifyFood | Fortify Food | ENDURING | INDIFFERENT | food item | More nourishment | +1000 |
| Chant_FishyFecundity | Fishy Fecundity | BREEDING | OK_SELF | mob | Social-triggered pregnancy for water breathers | age and clothing gates |
| Chant_MagmaCannon | Magma Cannon | DEEPMAGIC | MALICIOUS | mob | Fire damage | roll(numDice,9,10), range 1 to 5 |
| Chant_GasWard | Gas Ward | PRESERVING | BENEFICIAL_SELF | self | Gas save | +100 |
| Chant_FireWard | Fire Ward | PRESERVING | BENEFICIAL_SELF | self | Fire save | +100 |
| Chant_LightningWard | Lightning Ward | PRESERVING | BENEFICIAL_SELF | self | Electric save | +100 |
| Chant_KillerVine | Killer Vine | PLANTCONTROL | BENEFICIAL_SELF | self | Summons a vine monster | damage 30+9(L/5) |

Notes on the table: L is the adjusted caster level, X is `getXLEVELLevel`, X1 is `getX1Level` and M is `getXMAXRANGELevel`. The base-class summoners for Grow Oak and Killer Vine are outside this bucket.

### Tiered reveals (ready-made for a text game)

- **Know Animal** tiers (`Chant_KnowAnimal.java:136-286`): stats, prowess, senses, behaviours and properties, racial and cultural abilities, factions, start room, all abilities, dispositions, parent. Each tier adds one line.
- **Inspect Shard** tiers (`Chant_InspectShard.java:109-118`): mundane or spell name and magic word; charges at level 3; max charges at level 6; a further detail at level 9.

### Messages worth reusing (short samples)

| Source | Text (paraphrased or quoted briefly) |
|---|---|
| FindPlant | "There seems to be X around here." and "You sense X <direction>." (`Chant_FindPlant.java:208`, `:178`) |
| FindPlant | "The trail fizzles out here." (`Chant_FindPlant.java:170`) |
| HowlersMoon | "The Howler's Moon Rises!" and "The howlers moon sets." (`Chant_HowlersMoon.java:289`, `:99`) |
| ManicMoon | "howl(s) at the moon!", "go(es) nuts!" (`Chant_ManicMoon.java:136`, `:148`) |
| HighTide | "attempt(s) to stand up, and slips!" (`Chant_HighTide.java:123`) |
| HighTide | "The water under your feet recedes." (`Chant_HighTide.java:151`) |
| Hippieness | "No, man... work is so bourgeois..." (`Chant_Hippieness.java:116`) |
| Hippieness | "You don't feel quite so groovy." (`Chant_Hippieness.java:203`) |
| Hibernation | "You can't withdraw from hibernation just yet." (`Chant_Hibernation.java:175`) |
| FungusFeet | "<S-YOU-POSS> feet rot off!" (`Chant_FungusFeet.java:122`) |
| Labyrinth | "You are lost in dark twisting caverns. Every turn looks the same." (`Chant_Labyrinth.java:167`) |
| MagneticField | "Your metal armor is holding you in place!" (`Chant_MagneticField.java:115`) |
| Grapevine | "You can no longer hear the whispers from your plants." (`Chant_Grapevine.java:127`) |
| Goodberry | "<T-NAME> begin to glow!" (`Chant_Goodberry.java:142`) |
| Flood | "Finally, the flood waters recede." (`Chant_Flood.java:103`) |

### Settings and tables

- **Illusionary Forest descriptions** (`Chant_IllusionaryForest.java:141-184`): ten d10 entries with titles Forest glade, Dark Forest (four entries), Light Forest, Forest by the stream, End of the path, Forest, and Druidic Forest.
- **Tracking ranges** (listed above).
- **Mob templates.** The wolf in `Chant_HowlersMoon.java:301-340`, the vine in `Chant_KillerVine.java:76-99`, and the GenItem oak in `Chant_GrowOak.java:89-108`.

## Notable design ideas

- **Fields as room effects.** The moon chants put one effect on a room, tick it per room tick, and let it act on occupants. Exclusivity is per room (one moon-altering effect), so casters contend for the same room. It is a simple world-state contest with a clear message when it fails (`Chant_LoveMoon.java:210-219`).
- **Sky-dependent magic.** Moon chants depend on time of day and weather through `canSeeTheMoon` (`DefaultClimate.java:228-256`). A magic that needs the sky is a cheap way to make the world matter.
- **Disclosure ladders.** Know Animal and Inspect Shard give more detail as the caster gets better at the spell (`Chant_KnowAnimal.java:136-286`). That turns expertise into information rather than just a number.
- **Tracking through LOOK.** The tracker updates its hint when the player looks at the room (`Chant_FindPlant.java:187-200`), so information comes from an action the player already takes.
- **A decoy room.** Illusionary Forest intercepts LOOK and EXAMINE and sends them to a second room (`Chant_IllusionaryForest.java:116-134`). It is a perception layer built from the existing message system.
- **Procedural rooms.** Labyrinth builds a 10 by 10 maze and copies the real exits into it (`Chant_Labyrinth.java:161-182`). The room is dissolved by the caster.
- **Ownership-sensitive persistence.** An owned forest is saved to the database, and an unowned one is temporary (`Chant_IllusionaryForest.java:205-211`). Ownership changes the lifetime of a change.
- **Plant as a hit-point pool.** Grow Oak turns damage into a shared resource: the oak absorbs the caster's losses and can be chopped down (`Chant_GrowOak.java:116-161`).
- **Lock-in rest.** Hibernation blocks leaving for 10 rounds and shows a countdown (`Chant_Hibernation.java:57-65`, `:172-176`). That makes a quick rest feel like a commitment.
- **Chained messages.** `okMessage()` vetoes an action and `executeMsg()` applies the result on each listener, so one chant can refuse a movement or hide a verb without knowing the rest of the game.

## Problems and cruft

Each item is a fact read from the code unless marked unverified.

1. **Doubled words in a message.** `Chant_HighTide.java:248` reads "as the tide rushes in tide rushes in from". The fix is a one-word edit.
2. **Return value overwritten.** `Chant_HighTide.java:242` sets `success` from the proficiency check. `Chant_HighTide.java:265` then sets it to `maliciousAffect(...)!=null` for each leg-bearing target. A successful chant can return false if the last target resists.
3. **Hibernation's food and drink restore is a no-op.** `Chant_Hibernation.java:253` stores `oldState = mob.curState()`, and `com/planet_ink/coffee_mud/MOBS/StdMOB.java:1000-1003` returns the live object. So `Chant_Hibernation.java:203-204` sets hunger and thirst to their own values. The reassignments at `StdMOB.java:646` and `:1162` are on copy and reset paths only. Hibernation does not freeze hunger or thirst in normal play.
4. **Plain Hibernation appears to end at once.** `Chant_Hibernation.java:127-135` sets IS_SLEEPING only for DEEP casts, but `tick()` at `:196-197` keeps the regen running only while `CMLib.flags().isSleeping(mob)` is true (`com/planet_ink/coffee_mud/Libraries/Sense.java:1071-1074`). A plain cast needs a sitting caster and a sleeping one. The DEEP path that Chant_Uplift uses (`Chant_Uplift.java:396-405`) is the one that clearly works. Unverified in play.
5. **Affect writes to base stats.** `Chant_Hibernation.java:133` writes IS_SLEEPING into `affected.basePhyStats()` from inside `affectPhyStats()`. Affect methods should not change base values. Whether `postStand` clears it is unverified.
6. **Non-monotonic duration for Magnetic Field.** `Chant_MagneticField.java:182` passes `-levelDiff` as the tick adjustment. `StdAbility.java:2181-2182` then returns `levelDiff/100` of the default. A gap of 0 gives the full default, a gap of 1 gives about 1%, and a gap of 100 gives the full default again. This looks unintended.
7. **Magnetic Field cannot succeed against strong targets.** The proficiency adjustment is `-2*levelDiff` (`Chant_MagneticField.java:171`). `StdAbility.java:1483-1484` fails when `rollPercentage() > 100 + adjustment`. With `levelDiff >= 50` the threshold is 0 or lower, and `rollPercentage()` is at least 1 (`Dice.java:409-412`), so the chant always fails.
8. **Wrong recipient for a message.** `Chant_FindMate.java:247` tells `target` "You stop tracking.", not the caster. The method then returns true without casting (`:244-249`).
9. **Duplicated race test.** `Chant_FindMate.java:221-222` repeat the `Human` check.
10. **Dead loop.** `Chant_LocateAnimals.java:212` adds at most one room, so `while(rooms.size()>10)` at `:213-215` can never run.
11. **Mana spent before validation.** `Chant_Goodberry.java:123` runs `super.invoke()`, which charges mana, and only then checks at `:128-132` that the target is a berry. A wrong target costs mana.
12. **Mana spent before a no-op.** `Chant_FindPlant.java:315-323` charges mana and then returns true when the resource is already in the room.
13. **Wolves not removed on moon set (unverified).** See the moon section. `Chant_HowlersMoon.java:127-130` continues after `unInvoke()` with no return.
14. **Typo in a material message.** `Chant_Hippieness.java:169` reads "Looks liefy and green."
15. **Fragile null field.** `Chant_Hippieness.java:93`, `:103` and `:131` call `oldClans.add`, but `oldClans` is null until `invoke()` sets it at `:251`. It is not copied by `cloneFix()`, but `clone()` copies the reference. Any instance made without `invoke()` would throw when the mob has clans. Low risk in practice.
16. **Shadowed display text.** `Chant_FindPlant.java:58-60` returns the field `displayText`, which has the same name as the method. It works but is hard to read.
17. **Unverified side effects.** `Chant_Flood.java:102-104` passes -1 to `setAtmosphere` if the saved text has no ATMOSPHERE value. `Chant_Labyrinth.java:106` destroys the room on unInvoke, and no code in this file moves other occupants out (only the quitting mob is moved, `:110-125`).
18. **Missing age test.** `Chant_FindMate.java:210-229` (`isSuitableMate`) and the LoveMoon candidate test (`Chant_LoveMoon.java:117-129`) check reproductive code, race, worn items and, for LoveMoon, a Charisma save, but not age. `Chant_FishyFecundity.java:114` and `:117` do test the age category. Any Python port should apply an adult-age test in every pairing rule.
19. **Multiplayer assumptions.** Several chants assume other players. Give Life's loyalty requires a player caster (`Chant_GiveLife.java:137-146`). Flood and HighTide use revenge and combat targeting that are PvP-shaped (`Chant_Flood.java:139-140`). Magnetic Earth uses `getGroupMembers` and `mayIFight` (`Chant_MagneticEarth.java:85-91`), and Fodder Signal excludes the caster's group (`Chant_FodderSignal.java:113-137`). Labyrinth handles other occupants and quitting players (`Chant_Labyrinth.java:185-210`).
20. **Forced commands as the engine API.** Several chants drive behaviour by building command strings and calling `mob.doCommand(..., METAFLAG_FORCED)` (`Chant_LoveMoon.java:141`, `Chant_FindMate.java:142`, `Chant_Hippieness.java:180`). This couples the spell to the parser and to the exact wording of commands.

## Takeaways for a Python SUD

**Keep**

- **A chant as data plus a few hooks.** One record per chant, with an id, name, domain, quality, target kind and cost, and a few hook functions for invoke, tick and the two message phases. The CoffeeMud pattern is a template class plus `copyOf()`, which is what a Python dataclass with a `make_effect()` method gives you.

  ```python
  @dataclass(frozen=True)
  class ChantSpec:
      id: str
      name: str
      domain: Domain          # moon, water, plant, shape, ...
      quality: Quality        # MALICIOUS, INDIFFERENT, OK_SELF, ...
      target: TargetKind      # MOB, ITEM, ROOM, SELF
      cost: int | None = None # None = use the default mana rule
  ```

- **Room fields with an exclusivity key.** A room holds at most one effect per exclusive domain, with a clear refusal message. Keep the moon rule and reuse it for other sky or ground fields.
- **The moon gate.** `can_see_moon(room)` is true at night or dusk with a sky and a clear enough sky, or when a moon-summoning effect is present. This is a small, testable function, and it makes a time-of-day system matter.
- **Tiered reveal.** Know Animal's ten tiers are a list of `(min_expertise, describe)` functions. Use them for any probe, scan or inspect command.
- **Tracking as a trail with an LOOK hint.** A `Tracker` effect holds `trail: list[room_id]` and `next_step`. The `look` command updates `next_step`, and a `track` command prints the hint. That keeps the single-player interface simple.
- **Procedural rooms for rare magic.** Build a small maze from a template when Labyrinth-style magic is cast, and remove it on expiry or when the caster leaves.
- **Proficiency and duration formulas.** Keep the shape, but apply the clamp before the expertise bonus:

  ```python
  def succeeds(prof: int, expertise: int, adjustment: int, roll: int) -> bool:
      chance = prof + 2 * expertise          # StdAbility.java:1467-1468
      chance = min(95, max(5, chance))       # StdAbility.java:1470-1476
      chance += expertise // 2               # StdAbility.java:1478
      if adjustment >= 0:
          chance += adjustment               # StdAbility.java:1480-1481
      elif roll > 100 + adjustment:
          return False                       # StdAbility.java:1482-1484
      return roll < chance                   # StdAbility.java:1485
  ```

  Hostile duration default, before the level-gap adjustment: `max(2, round(1.1 * level) + 25)` ticks. Beneficial default: `level*4 + 40` ticks.

**Change**

- **Make duration a number, not a sign convention.** Store `duration_ticks` and `duration_percent` as separate fields, and make the level-gap adjustment monotonic. This removes the Magnetic Field discontinuity (Problems 6 and 7).
- **Model stat changes as modifier lists.** Give each entity `base_stats` and a list of active `Modifier` objects, then compute `stat = base + sum(mods)` on read. This replaces `affectCharStats` and `recoverPhyStats` and makes stacking clear.
- **Fix Hibernation.** Store the food and drink state explicitly, and have the regen tick check the caster's own sleep state (or make the chant put the caster to sleep). Make the sleep flag a field, not a write to base stats.
- **Use a single message pipeline.** Keep the two phases (a veto pass, then apply) but make them plain function calls on an `Event` object. CoffeeMud's `okMessage` and `executeMsg` split is worth keeping because it lets one effect refuse an action without knowing about the others.
- **Replace command strings with actions.** Instead of `doCommand("MATE ...")`, call the game's action function directly, so the chant does not depend on parser wording.

**Drop (single player)**

- Other players' combat rules, revenge attacks, group checks and clan membership. Keep factions as a simple reputation number if you need it.
- The labyrinth's move-back logic for quitting players, and the multi-occupant handling. A single-player maze only needs the caster.
- The loyalty and experience-transfer logic for player-to-player effects. Keep it only if animal companions are a feature.

**Single-player caveats**

- Pairing and mating effects need NPC partners only, and they must carry an explicit adult-age test in every rule. CoffeeMud's FishyFecundity has such a test (Problem 18), and the others do not.
- The fungus and holding effects are good single-player material, since they are timers on one target and need no other people.
- Tracking and moon fields rely on time and weather. A Python SUD with a time-of-day clock can reuse them directly.

## Coverage

**Read in full** (the whole body, aside from licence headers and imports):
- Base: `com/planet_ink/coffee_mud/Abilities/Druid/Chant.java` (outside the bucket, read as a dependency).
- `Chant_FindGem.java`, `Chant_FindPlant.java`, `Chant_HowlersMoon.java`, `Chant_Flood.java`, `Chant_HighTide.java`, `Chant_Hippieness.java`, `Chant_Hibernation.java`, `Chant_ManicMoon.java`, `Chant_LoveMoon.java`, `Chant_FungusFeet.java`, `Chant_Labyrinth.java`, `Chant_FindMate.java` (compact view with every statement), `Chant_FireWard.java`, `Chant_Hunger.java`.

**Read in logic-only condensed form** (boilerplate getters removed, every statement kept): `Chant_KnowAnimal.java`, `Chant_GrowItem.java`, `Chant_GrowOak.java`, `Chant_MagneticEarth.java`, `Chant_MagneticField.java`, `Chant_Goodberry.java`, `Chant_Grapevine.java`, `Chant_IllusionaryForest.java`, `Chant_HealingMoon.java`, `Chant_HoneyMoon.java`, `Chant_LandLegs.java`, `Chant_Flippers.java`, `Chant_HardenSkin.java`, `Chant_GiveLife.java`, `Chant_HoldAnimal.java`, `Chant_FodderSignal.java`, `Chant_Homeopathy.java`, `Chant_FortifyFood.java`, `Chant_FishyFecundity.java`, `Chant_MagmaCannon.java`, `Chant_GrowClub.java`, `Chant_GrowFood.java`, `Chant_GrowForest.java`, `Chant_GroveWalk.java`, `Chant_FreeVine.java`, `Chant_InspectShard.java`, `Chant_FungalBloom.java`.

**Skimmed by grep and targeted reads:** `Chant_Hawkeye.java`, `Chant_FurCoat.java`, `Chant_LocateAnimals.java` (room selection and tick), `Chant_KillerVine.java`, `Chant_LocatePlants.java` (range and domain filter), `Chant_FindOre.java` (materials), `Chant_GasWard.java` and `Chant_LightningWard.java` (same shape as FireWard; stat names checked by grep).

**Skipped:** none of the 49 bucket files were left unseen, but the ward and FindOre bodies were checked by grep rather than read end to end.

**Outside the bucket, followed for interactions only:** `com/planet_ink/coffee_mud/Abilities/StdAbility.java` (lines 299-312, 1419-1539, 1964-2080, 2140-2208, 2312-2365), `com/planet_ink/coffee_mud/Abilities/interfaces/Ability.java` (constants), `com/planet_ink/coffee_mud/Common/DefaultClimate.java` (228-256), `com/planet_ink/coffee_mud/Common/DefaultCharState.java` (185-200), `com/planet_ink/coffee_mud/MOBS/StdMOB.java` (110, 646, 1000-1003, 1162), `com/planet_ink/coffee_mud/Libraries/Dice.java` (398-412), `com/planet_ink/coffee_mud/Libraries/Sense.java` (1071-1074), `com/planet_ink/coffee_mud/core/CMProps.java` (681), `com/planet_ink/coffee_mud/Abilities/Druid/Chant_Uplift.java` (392-410), `com/planet_ink/coffee_mud/CharClasses/Druid.java` (241) and `Beastmaster.java` (225). Not read: `Chant_SummonPlants.java`, `Chant_SummonVine.java`, `Druid_MyPlants.java`, `Druid_ShapeShift.java`, `TrackingLibrary`, `Climate` weather cases after line 258.
