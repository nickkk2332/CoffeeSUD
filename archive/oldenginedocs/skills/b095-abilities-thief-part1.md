# Abilities/Thief, part 1: ThiefSkill to Thief_DeepCut

> Part of the CoffeeMud NEWOVERVIEW. Bucket: `b095` (Abilities/Thief [part 1/5: ThiefSkill .. Thief_DeepCut]) · 41 files · 9128 lines of Java.

## Summary

The 41 classes in this bucket are the first half of CoffeeMud's thief skill family: the base class `ThiefSkill` and 40 concrete skills in `Abilities/Thief/`. They cover trap-making (caltrops, death traps), stalking and contract killing, restraint and injury, hiding exits and items, persuading or paying NPCs, borrowing a cleric's temporary blessing, recruiting pirate crews, and a large set of passive effects that watch the message stream (auto-sneak, auto-detect traps, dagger deflection, counter-tracking). Every skill subclasses the engine's `StdAbility`, so the proficiency roll, costs, expertise levels, tick-down durations and message/tick system come from shared code, and the thief files add only the rules that make each skill feel like theft or sabotage. For a single-player Python game this is a worked model of "skill = data, one roll, one effect that hooks the event stream", with a few clear candidates to keep (passive hooks, a per-NPC notice chance, traps that spring on entry) and several bugs to avoid copying.

## Key files and types

Paths are relative to `com/planet_ink/coffee_mud/` (the Java package root under the CoffeeMud folder). Line counts are from the bucket manifest. Shared engine files are outside this bucket and are not counted.

| File or class | Role | Lines |
|---|---|---|
| `Abilities/Thief/ThiefSkill.java` | Family base class. Sets classification, quality and target type; applies the leather-armor fumble rule; provides `getHighestLevelMOB()` and `getOpenable()` helpers. | 187 |
| `Abilities/StdAbility.java` (shared, not in bucket) | Engine base: `invoke()` pipeline (1964-2090), `proficiencyCheck()` (1441-1486), `helpProficiency()` (1810-1846), tick-down formulas (2140-2335), fizzle and affect helpers (2185-2268), expertise accessors (400-470), `adjustedLevel()` (899-925). | not counted |
| `Abilities/interfaces/Ability.java` (shared) | Constants: `ACODE_THIEF_SKILL=6` (683), `QUALITY_*` (1083-1091), `USAGE_MANA`/`USAGE_MOVEMENT` (622-624), `FLAG_BINDING` (946), `FLAG_TRACKING` (958), `FLAG_STEALING` (1026), `CAN_MOBS`/`CAN_ROOMS` (1100, 1106), `TICKS_FOREVER` (46). | not counted |
| `Abilities/Thief/Thief_Articles.java` | Recruits a monster into a pirate ship crew; quotas come from the ship's room layout. | 455 |
| `Abilities/Thief/Thief_Assassinate.java` | Tracking effect: finds a target, follows its trail room by room, attacks on arrival. | 441 |
| `Abilities/Thief/Thief_BorrowBoon.java` | Copies a temporary prayer effect off an NPC, using a held holy item as catalyst. | 426 |
| `Abilities/Thief/Thief_DeathTrap.java` | Room trap built from 100 units of metal; springs when a hostile enters and can kill. | 377 |
| `Abilities/Thief/Thief_Bind.java` | Sets `IS_BOUND` on a target, which must struggle through a resistance budget. | 338 |
| `Abilities/Thief/Thief_CaseJoint.java` | Hidden multi-tick survey of a room; once "cased", the thief gets combat and skill bonuses inside it. | 325 |
| `Abilities/Thief/Thief_ContractHit.java` | Pays for a hit; spawns 4-6 "Assassin" mobs that hunt the target. | 319 |
| `Abilities/Thief/Thief_DeepCut.java` | Melee strike at a chosen limb; a qualifying hit adds extra damage and an "Injury" effect. | 304 |
| `Abilities/Thief/Thief_Caltrops.java` | Room trap of caltrops (piercing damage on entry). Base class of `Thief_DazzlingCaltrops`. | 298 |
| `Abilities/Thief/Thief_CutThroat.java`, `Thief_BackStab.java` | Sneak attacks with a temporary damage multiplier and attack bonus. | 259, 246 |
| `Abilities/Thief/Thief_Con.java`, `Thief_Bribe.java` | Make an NPC perform a command, by talk (roll) or by paying (wealth-based price). | 246, 196 |
| `Abilities/Thief/Thief_ConcealPathway.java`, `Thief_ConcealWalkway.java`, `Thief_ConcealDoor.java`, `Thief_ConcealItem.java`, `Thief_Belay.java` | Hide an exit or item with `IS_HIDDEN`, for a tick count or permanently on owned land. | 243, 221, 237, 178, 202 |
| `Abilities/Thief/Thief_CarefulStep.java` | One guarded step in a direction, with a temporary +100 DEX. | 173 |
| `Abilities/Thief/Thief_AutoSwipe.java`, `Thief_Autosneak.java`, `Thief_AutoDetectTraps.java`, `Thief_AutoMarkTraps.java`, `Thief_Autocaltrops.java` | Passive effects that react to "entered room" messages. | 207, 174, 191, 186, 157 |
| `Abilities/Thief/Thief_DaggerDefense.java`, `Thief_Countertracking.java`, `Thief_AvoidTraps.java`, `Thief_AnalyzeMark.java`, `Thief_BlendIn.java`, `Thief_Comprehension.java`, `Thief_Alertness.java`, `Thief_DampenAuras.java`, `Thief_BuriedTreasure.java` | Passive defense, perception and information effects. | 142, 113, 112, 137, 199, 204, 180, 184, 147 |
| `Abilities/Thief/Thief_Ambush.java`, `Thief_CallUrchins.java`, `Thief_Arsonry.java`, `Thief_Appraise.java`, `Thief_CondemnMark.java`, `Thief_Astrology.java`, `Thief_DazzlingCaltrops.java` | Group hiding, summoning, arson, appraisal, religious accusation, divination, trap variant. | 159, 185, 177, 209, 162, 153, 79 |

Not in bucket but read for mechanics: `Libraries/Sense.java:1399-1404` (`isBound`), `Libraries/CoffeeUtensils.java:1061-1075` and `1151-1175` (armor check), `Libraries/Dice.java:398-412`, `CharClasses/interfaces/CharClass.java:658-662`, `Libraries/interfaces/CMFlagLibrary.java:396` (`canBeSeenBy(seen, seer)` signature).

## How it works

Citations give the path after `com/planet_ink/coffee_mud/` and the line range. Quoted messages are the in-game text.

### 1. ThiefSkill: the family base

- Fixed properties (`Abilities/Thief/ThiefSkill.java`): `ID()` is `ThiefSkill` (39-42). `abstractQuality()` returns `QUALITY_INDIFFERENT` (53-56). `classificationCode()` returns `ACODE_THIEF_SKILL` (65-68). `canAffectCode()` is 0 and `canTargetCode()` is `CAN_MOBS` (71-80). Concrete skills override these, most often adding a `DOMAIN_*` to the classification.
- No fields of its own. All state lives in each subclass and in the engine.
- Armor rule (`ThiefSkill.java:83-100`). After `super.invoke`, a non-auto cast by a player who is not `disregardsArmorCheck` fumbles with probability 50% (`rollPercentage()<50`, line 94) when `CMLib.utensils().armorCheck(mob, CharClass.ARMOR_LEATHER)` fails, meaning worn armor is heavier than leather (`CharClass.java:662`). The message is "<S-NAME> fumble(s) @x1 due to <S-HIS-HER> clumsy armor!" (line 96). The engine default (`StdAbility.java:877-885`) skips the check for NPCs and for anyone who qualifies for the skill by level, so the rule bites only on skills a character got without the class.
- Overrides that opt out: `Thief_Con.java:78-81`, `Thief_Bribe.java:77-80`, `Thief_Appraise.java:77-80`, `Thief_Comprehension.java:79-82`, `Thief_Alertness.java:85-88`, `Thief_ContractHit.java:77-80`, `Thief_AnalyzeMark.java:88-91`.
- Cost ordering. The armor check runs after `super.invoke`, which has already charged mana, movement and hit points (`StdAbility.java:2074-2076`). A fumble therefore still costs the usage.
- Helpers: `getMOBLevel` (102-107); `getHighestLevelMOB` (109-137), which scans the room for the highest-level awake, unhidden, non-group, non-sysop NPC; `getOpenable` (139-186), which resolves a direction, an exit, a room item or a target and prints "is open!", "is closed!" or "You can't do that to <T-NAME>.".

### 2. Engine rules every thief skill inherits

These are in `StdAbility.java` and are the reason the thief files are short.

**Invocation pipeline** (`invoke(mob, commands, target, auto, asLevel)`, 1964-2090):
1. Non-auto casts need an alive, awake caster (1974) and must respect the cool-down (1978-1989).
2. A movement-type skill (`USAGE_MOVEMENT`) fails with "You are bound!" when `CMLib.flags().isBound(mob)` (1991-1996). `isBound` tests the `IS_BOUND` disposition (`Libraries/Sense.java:1399-1404`), which `Thief_Bind` sets (`Thief_Bind.java:131`). So a bound character cannot use movement-type skills.
3. Usage cost and component tests (2050-2073), then the deduction of mana, movement and hit points (2074-2076), then `helpProficiency(mob, 0)` on every attempt (2077). The auto path skips all of this (`isAnAutoEffect=true`, 2084).
4. The subclass body runs and calls `proficiencyCheck` itself. Subclasses call `super.invoke` first, so every subclass inherits the armor rule above.

**Proficiency roll** (`proficiencyCheck`, `StdAbility.java:1441-1486`):
- Start with `proficiency()` (0-100). Add `PROF` ability adjustments from `charStats` (1452-1464).
- Add `2 × XLEVEL` (1467-1468). Clamp to 5..95 unless `DIS955RULE` is set (1470-1476). Then add `XLEVEL / 2` (1478).
- If `adjustment >= 0`, add it to the chance (1480-1481). It is not clamped afterwards, so a large bonus makes success certain.
- If `adjustment < 0`, the attempt fails outright when `rollPercentage() > 100 + adjustment` (1482-1484). The negative adjustment is a separate gate, not a subtraction from the chance.
- Then succeed when `rollPercentage() < chance` (1485).
- `auto` always succeeds (1443-1448).

Thief code uses the level gap as the adjustment. `Thief_Articles.java:395-396` passes `-adjustment`, where the adjustment is target level minus `(caster level + XLEVEL)/2`. `Thief_BorrowBoon.java:330-334` sets the adjustment to 100 for an unconscious target, which guarantees success.

**Proficiency gain** (`helpProficiency`, 1810-1846): each attempt calls it. A gain needs a random draw of `round(sqrt(INT) × 34 × rand)` to be at least the current proficiency, then a percentage roll against `getProfGainChance` (`Libraries/CMAbleMap.java:2885`). A given ability instance can gain at most once every 5 minutes (1827, `300000` ms).

**Expertise dimensions** (`StdAbility.java:400-470`): `XLEVEL`, `X1`, `XTIME` and `XMAXRANGE` are per-skill expertise levels, read through `ExpertiseLibrary`. For a null caster they return 0 (400-405). How the thief files use them:
- `XLEVEL`: the level bonus in almost every skill (for example `Thief_Articles.java:339`, `Thief_CutThroat.java:118`).
- `X1`: discovery or potency (`Thief_BorrowBoon.java:315`, `Thief_DazzlingCaltrops.java:74`).
- `XTIME`: duration of a survey (`Thief_CaseJoint.java:262`).
- `XMAXRANGE`: tracking range (`Thief_Assassinate.java:365`).

**Effective level** (`adjustedLevel`, 899-925): the lowest qualifying level, adjusted by the caster's class level, plus `XLEVEL`, with a floor of 1. A null caster gives 1 (901-902). Thief durations and gates use it. A few examples: `Thief_Articles.java:339` (crew bonus), `Thief_Caltrops.java:227` (damage `1d6 + level`), and concealment durations of `15 × adjustedLevel` ticks (`Thief_ConcealItem.java:168-169`).

**Durations** (ticks; the default tick is 4000 ms, `core/CMProps.java:681`, and the mob tick id is 0, `core/interfaces/Tickable.java:82`):
- Beneficial effects: `casterLevel × 4 + 40` ticks (`StdAbility.java:2325`). A level-10 caster gets 80 ticks.
- Malicious effects: `round(adjustedLevel × 1.1) + 25` ticks (`StdAbility.java:2174-2182`), then adjusted by `adjustMaliciousTickdownTime` (2140).
- Explicit tick counts win. A positive `tickAdjustmentFromStandard` is used directly, for example `Thief_ContractHit.java:314` uses target level + 10 ticks.
- `maliciousAffect` (2185-2208) and `beneficialAffect` (2341-2384) both make a copy, set `canBeUninvoked`, and start the tick-down.

**Failure helpers** (all return false): `beneficialWordsFizzle` (2210, speech to the room), `beneficialVisualFizzle` (2222, visible or hand message), `maliciousFizzle` (2253, visible malicious message), and `failureTell` (1873, private).

**Message bus.** Actions are `CMMsg` objects. `okMessage` lets an effect veto by returning false. `executeMsg` lets it react. Thief actions carry `CMMsg.MSG_THIEF_ACT` (`Common/interfaces/CMMsg.java:1441`), which is a composite code of hand, movement, delicate and justice flags. Effects are attached to MOBs, rooms, exits and items with `addEffect` or, for persistent effects, `addNonUninvokableEffect` (for example `Thief_ConcealPathway.java:231`).

### 3. Mechanic families

#### 3.1 Traps

**Thief_Caltrops** (`Thief_Caltrops.java`) is a room effect. It is placed by `setTrap` (169-174) or by `invoke`, which calls `maliciousAffect` (289) unless the owner holds the land, in which case it attaches a permanent copy (285-287). Because the tick-down argument is zero, a non-owner trap gets the malicious default, about `round(1.1 × level) + 25` ticks before `adjustMaliciousTickdownTime` (`StdAbility.java:2140`) adjusts it.
- `okMessage` (247-266) springs the trap when someone other than the invoker enters, leaves, flees or advances through the room (259-263). It always returns true.
- `spring` (215-231): the victim is spared if `canInvokeTrapOn` fails (the invoker cannot fight them, they are in the invoker's group, or they are locally exempt; 205-212, 219), or if `rollPercentage() < SAVE_TRAPS` (220). Otherwise the victim takes `roll(1, 6, adjustedLevel(invoker))` piercing damage with the message "The @x1caltrops on the ground <DAMAGE> <T-NAME>." (227-228). The trap never sets `sprung` (230), so it stays in place.

**Thief_DeathTrap** (`Thief_DeathTrap.java`) is a lethal, resettable trap.
- Build cost: at least 100 units of metal (iron, or mithril as a fallback) in the room (321-331), consumed on the attempt (343).
- Attempts: `attempts = (RawMaterial.HARDNESS(material) + XLEVEL) × 2` (349). Each spring decrements the count; the trap marks itself sprung once the count goes below zero (236-238).
- Lethality: `postDeath` when `rollPercentage() + 2 × XLEVEL(invoker) > SAVE_TRAPS` (232-233). A higher save means fewer deaths.
- Failure on set: roll above `50 + 5 × XLEVEL` sets the trap off on the builder, "trigger(s) the trap on accident!" (363-368).
- Triggering (`executeMsg`, 267-280): a MOB entering the room, not the builder, not the trap's own name, which the builder may fight, not in the builder's group, and not exempt (`isLocalExempt`, 193-212).
- Reset: `TICKS PER MUDDAY` (`CMProps.Int.TICKSPERMUDDAY`, 134) through `TICKID_TRAP_RESET` (287-292). Destruction for non-owned traps: `TICKSPERMUDDAY + 2 × XLEVEL` (184).
- The builder's name is stored in `miscText`. `invoker()` reloads that player from storage if they are offline (250-264).

**Thief_DazzlingCaltrops** (`Thief_DazzlingCaltrops.java`) extends `Thief_Caltrops`. Its `spring` (64-78) drains mana instead of hitting: `roll(3 + X1, 8, 20)` points from a victim with more than 6 mana (72-74).

**Thief_Autocaltrops** (`Thief_Autocaltrops.java`) is the passive form. On entering a room it casts `Thief_Caltrops` there (94-101, 104-117). It is toggled, and its effect lasts `5 + 3 × XLEVEL` ticks (150).

**Thief_AvoidTraps** (`Thief_AvoidTraps.java`) is passive. It adds `proficiency()/2 + 5 × XLEVEL` to `STAT_SAVE_TRAPS` (97), which feeds the save checks above. It gains proficiency when a trap hits the bearer on a roll above 90 (107-109).

#### 3.2 Detecting and marking traps

- **Thief_AutoDetectTraps** (`Thief_AutoDetectTraps.java`): on entering a room, `executeMsg` records it (94-103). `tick` then runs detection on the room, its exits and reverse exits, adjacent rooms and loose items (106-139). `dropem` uses `Thief_IdentifyTraps`, else `Thief_DetectTraps` (141-149), and restores the caller's mana, hit points and movement afterwards (151-155), so the scan is free.
- **Thief_AutoMarkTraps** (`Thief_AutoMarkTraps.java`): the same scan, but synchronous inside `executeMsg` (88-123), and it also invokes `Thief_MarkTrapped` when detection succeeds (125-143). Its `dropem` restores the caller's state the same way (144-146).

#### 3.3 Tracking, assassination and contracts

**Thief_Assassinate** (`Thief_Assassinate.java`) is the tracking skill. The flag `FLAG_TRACKING` (95-98) is what other effects test (see `Thief_Countertracking` below). The tracker's own sense mask gains `CAN_NOT_TRACK` (238-242), the same flag `Skill_Track` sets on its user (`Skill_Track.java:140`). `PhyStats.java:433` describes it as "unable to engage in long tasks".
- Setup (`invoke`, 301-440): with a `Thief_Mark`, the mark is the target (326-336). Otherwise the target is found by name, using `getRadiantRoomsToTarget` with range `50 + XLEVEL + 5 × XMAXRANGE` (365) and a `FilterMOBName` filter (275-298). A trail is then built with `findTrailToAnyRoom` (408). If the target is already in the room, the caster attacks instead (355-357).
- Tracking tick (`tick`, 122-235): if the target is in the same room, a hidden tracker runs `Thief_BackStab` with ability code 5 (149-156), otherwise it calls `postAttack` (158). Otherwise it checks the six directions for an open exit leading to the target (164-175) and reports "The trail seems to continue @x1." (193). An NPC tracker walks with `Thief_Sneak` when it has it (201-208), otherwise with `CMLib.tracking().walk` (214). A broken trail prints "The trail seems to pause here." (179) or "The trail dries up here." (186).

**Thief_ContractHit** (`Thief_ContractHit.java`) hires a killer.
- Price: `100 × min(target level, hirer level + 2 × XLEVEL)` in the local currency (286-296). Needs a city room (228, 248) and a fight-able target (280-284).
- On success, a malicious effect with a duration of target level + 10 ticks (301-314).
- The effect's `tick` sets `readyToHit` when its tick-down reaches 1 (117-121). In a city room it then spawns `roll(1, 3, 3)`, that is 4-6, "Assassin" MOBs (129-166). Each is leveled to the target's level, capped at the hirer's level plus `2 × XLEVEL` (130-132), and given the `Thiefness` behavior (148-150), `Thief_Hide` (160-162) and `Thief_BackStab` on the target (163-165). Each then tracks the target with `Thief_Assassinate` at proficiency 100 (183-190).
- `unInvoke` destroys the assassins (214-218). If the target died, it prints "Someone steps out of the shadows and whispers something to @x1." and "'It is done.'" (210-211).

**Thief_Countertracking** (`Thief_Countertracking.java`) is passive. It vetoes any tracking-flagged ability aimed at its bearer (100-110) and prints "You can't get a bead on him." (107), using the bearer's proficiency check at 0 (103).

**Thief_AnalyzeMark** (`Thief_AnalyzeMark.java`) is passive. When the bearer looks at or examines the mark after more than 15 ticks of observation (125), they receive the target's score sheet (130-132), and gain proficiency on a 50% roll (128).

#### 3.4 Restraint and injury

**Thief_Bind** (`Thief_Bind.java`) binds a target.
- Effect: `IS_BOUND` disposition (131), plus `IS_SITTING` if forced (132-133).
- While bound, `okMessage` vetoes the victim's hand and movement messages (152-171). Each struggle subtracts `STR + level` from `amountRemaining` (162) and releases the victim below zero (163-164). Without `allowBreak` the message is "You are constricted by @x1 and can't move!" (169).
- An NPC is released automatically when it is not in combat, not following, and no PC is in the room (173-184).
- Strength of the bind: non-auto `adjustedLevel × 25` (313). Auto, `(STR + level + 2 × XLEVEL) × round(5 + Specialization_Ranged proficiency/10)` (307-310). Both are multiplied by the target's base speed (314).
- Parameters from `miscText`: `ROPENAME`, `AMOUNT` (default 500), `FORCESIT`, `ALLOWBREAK` (205-208).
- "UNTIE" removes a binding (255-273).

**Thief_DeepCut** (`Thief_DeepCut.java`) cuts a limb.
- Prerequisites: a wielded weapon whose damage type is slashing or piercing (154-178). Melee range only when in combat (156-159).
- The limb comes from `Amputation.unaffectedLimbSet()` (233). NPCs pick a random limb (241). Players name one by prefix; "head" uses `Race.BODY_HEAD` with `hpReq = 3` (255-260).
- Threshold: `hpReq = 20` at invoke (198). A damage message that deals at least `baseHP / (hpReq − XLEVEL)` (139-141) gets extra damage of that amount (142-143) and an "Injury" effect on the limb (98-125, damage value `maxHP / (20 − XLEVEL)`, line 108). Otherwise: "You failed to injure <T-YOUPOSS> '@x1'." (148).
- The effect lasts two ticks (`beneficialAffect(mob, mob, asLevel, 2)`, 294) and is consumed by the next damage message (149).

**Thief_CutThroat** (`Thief_CutThroat.java`) and **Thief_BackStab** (`Thief_BackStab.java`) share a skeleton.
- Both need the target to be unable to see the caster (`canBeSeenBy(mob, target)` at CutThroat 178, BackStab 167) and the caster to be standing (183, 172).
- CutThroat requires `Weapon.CLASS_DAGGER` (160, 215) and applies bleeding when the target is at least `INJBLEEDMINLEVEL` (executeMsg 128-142). BackStab excludes blunt, hammer, flail, ranged, thrown and staff weapons (199-208).
- Damage multiplier: `round(adjustedLevel/6) + 2 + abilityCode` (CutThroat 116-117, BackStab 117). Attack bonus: `+100 + 10 × XLEVEL` (CutThroat 118, BackStab 119).
- Stealth: unless the target is asleep, the swing is announced as "spot(s)" when the roll fails, the caster is seen, or the message value is above zero (CutThroat 232-233). Otherwise a temporary copy carries the multiplier for that swing only (CutThroat 236-241, removed at 248-250).
- The same victim cannot be struck twice in a row (`lastMOB`, CutThroat 188-192, BackStab 177-181).
- Messages: "<S-NAME> stab(s) <T-NAME> in the back with <O-NAME>!" (BackStab 133); "<S-NAME> cut(s) <T-YOUPOSS> throat with <O-NAME>! Blood starts running..." (CutThroat 139-140).

#### 3.5 Concealment

- **Thief_ConcealPathway** (`Thief_ConcealPathway.java`) hides an exit between a moderate road and a wilderness room. A road is `DOMAIN_OUTDOORS_PLAINS` with a base weight of 1 (150-158). Wilderness means not a road, not a city room and not indoors (160-171). The exit's level must be at most `2 × adjustedLevel` (207). Effect: `IS_HIDDEN` and `level + code` on the exit, where `code = 2 × adjustedLevel − exit level` (226, 109-112). Duration: `15 × adjustedLevel` ticks (235), or permanent on owned land (228-233).
- **Thief_ConcealWalkway** (`Thief_ConcealWalkway.java`) is the same, but one of the two rooms must be indoors (177-183).
- **Thief_ConcealDoor** (`Thief_ConcealDoor.java`) hides a closed door (`IS_HIDDEN` only while closed, 109-111). Opening it removes the effect (123-132). The level bump is commented out (112-114), so the door's level is not raised.
- **Thief_ConcealItem** (`Thief_ConcealItem.java`) hides an item (always `IS_HIDDEN`, 107). Taking, pushing or pulling it removes the effect (114-121). Weight limit: `2 × adjustedLevel` (136-140).
- **Thief_Belay** (`Thief_Belay.java`) is for ship decks. The item gets `SENSE_ITEMNOTGET` and the ambience "belayed" (113-115). It is removed by a get, push or pull (123-130). It is only allowed on a deck of a `Boardable` area that is not indoors (145-146).

#### 3.6 Persuasion, bribery and stealing

**Thief_Con** (`Thief_Con.java`) orders an NPC.
- The target must be a monster with INT at least 3, not in combat, and not following (139-169).
- The command must be a `Command` that can be ordered and passes the security check, and is not "Sleep" (171-179), or an ability without `FLAG_NOORDERING` (182-191).
- Preparation: `castingTime = DEFABLETIME / 20` (94). Each 4 seconds, "continue(s) conning" is shown (111-112).
- Roll: `proficiencyCheck(mob, CHA × 2 + levelDiff)`, where `levelDiff = min(0, ((caster level + 2 × XLEVEL) − target level) × 10)` (214-217).
- On success the NPC is ordered with forced priority (`enqueCommand`, 235-236). Repeating on the same target rolls back the proficiency gain (240-241).

**Thief_Bribe** (`Thief_Bribe.java`) is the same with payment.
- Price: the target's total wealth plus `(100 − (CHA + 2 × XLEVEL) × 2) × target level` (154-155). There is no clamp, so a high CHA can make the surcharge negative.
- The payment is subtracted (176), the order is given (185), and the same amount is added back (187). See the problems section.

**Thief_BorrowBoon** (`Thief_BorrowBoon.java`) takes a prayer.
- Catalyst (`fetchCatalyst`, 176-239): a held wand or scroll with a prayer spell, a spell holder with a prayer, or an item with an `AbilityContainer` holding a prayer. Wands need charges (187-190), scrolls too (207).
- Target: must not see the caster (`castingQuality` 254-255), must be fight-able, and must be no more than 15 levels above (293-299).
- Discovery chance (312-324): `DEX × 3 − WIS(target) × 5 − levelDiff × 5 + X1 × 5`, minus 20 for each repeat past the fifth pick (318), plus 50 if the caster is unseen (319-320), clamped to 5..95.
- Success copies a random non-savable prayer effect from the target (`findBoon`, 129-174) and starts it on the caster (405-408). The catalyst loses a charge with probability `12 − XLEVEL` percent (411-420).
- A failed discovery roll sends "You fumble the attempt to 'borrow'; <T-NAME> spots you!" to the room (345). A monster target with no current victim then picks a fight with the thief (342-343).

**Thief_AutoSwipe** (`Thief_AutoSwipe.java`) is passive pickpocketing.
- On entering a room with more than one inhabitant, eligible victims are those for which `Thief_Swipe.getAllowedSwipe` holds (`Abilities/Thief/Thief_Swipe.java:160-166`) and whose `getDiscoverChance` is at least `risk` (default 90, line 89; removal at 127-132). A random eligible victim is swiped from a delayed runnable (134-154).
- `risk` is the "safety factor" the bearer sets as a percentage (164-172).
- `Thief_Swipe.getDiscoverChance` (`Thief_Swipe.java:168-181`) is the same shape as the BorrowBoon chance: higher is better for the thief.

#### 3.7 Ship crews and ship decks

**Thief_Articles** (`Thief_Articles.java`) signs an NPC onto a crew.
- The caster must be on a `Boardable` area (250) and have privileges there (266-271). The NPC must be a bound, held or grouped monster (277-306).
- Quotas. Let `numDecks` be the indoor rooms of the ship, `numRooms` the others, and `bonus = level / 10`, which adds `bonus` to rooms and `bonus / 2` to decks (339-345). Then: `maxGunners = numDecks`, `maxBoarders = max(1, (numRooms − numDecks)/2)`, `maxDefenders = max(1, numRooms − numDecks − maxBoarders)` (348-354). Full crew is refused (356-360).
- Role: a random roll of 0-2 maps to GUNNER, DEFENDER or BOARDER (362-390). A full role is re-rolled. The NPC's `Sailor` behavior is set with the role: `FIGHTTECH=true` for gunners; `DEFENDER=true` or `BOARDER=true` for the others (`getSailor`, 159-187; `Behaviors/Sailor.java`).
- Success: `proficiencyCheck(mob, −adjustment)` with `adjustment = target level − (caster level + XLEVEL)/2` (395-396). Message: "<S-NAME> sign(s) the articles of piracy and is now a member of the crew of @x1." (406). Failure: "... but <T-HIM-HER> isn't convinced." (450).
- A crew member is stored as a copy of the effect with `miscText = "<ship>;<ROLE>"` (411). An NPC that is away from its start room is copied, marked with the tattoo `SYSTEM_SUMMONED` and `SUMMONED_BY:<name>`, and given `Prop_ModExperience` at zero (413-444).

**Thief_Belay**: see 3.5.

**Thief_CallUrchins** (`Thief_CallUrchins.java`) summons an NPC network.
- Range: `3 + adjustedLevel/5 + XLEVEL` radiant rooms (92-96), restricted to the area and passable rooms (93-95). Urchins are listed by `Thief_MyUrchins.getMyUrchins` (98), and those out of range are dropped (99-104).
- "Better look around first." if every urchin is in the room (127-131). "Either they are all en route, or you have not heard from your urchins." if none can be reached (133-137).
- Success: "^S<S-NAME> call(s) on <S-HIS-HER> local urchins to gather for a meeting!^?" (146-147). Each reachable urchin that is not already tracking gets `Skill_Track` toward the caster's room (162-171). The loop returns after the first one (172). See the problems section.

#### 3.8 Passive perception, protection and effects

- **Thief_Alertness** (`Thief_Alertness.java`): on entering a new room it describes up to `XLEVEL` random visible items that have no custom display text, as "name: description" (109-137). Count is at least 1 (127-129).
- **Thief_BlendIn** (`Thief_BlendIn.java`): veto. When a malicious action targets the bearer from an NPC who is not in combat with them, in the same room, and from the same area as its start room, the attack is refused if `rollPercentage() > (attacker level − (target level + 2 × XLEVEL)) × 10` (134). The message is "Awe, leave that local citizen @x1 alone." (137). The room counts as local if it is a city room, or holds a shopkeeper from the same area, searched three rooms deep (92-117). If the attacker's level is no more than the target's level plus `2 × XLEVEL`, the veto always fires; with a margin of 10 levels or more it never does.
- **Thief_Comprehension** (`Thief_Comprehension.java`): queues speech in languages the listener lacks (121-167) and delivers the translations on tick, one per successful proficiency check (105-119). Each is marked "(translated from <language>)".
- **Thief_DaggerDefense** (`Thief_DaggerDefense.java`): auto. Once per round (`doneThisRound`, 96-101), a melee weapon attack on the bearer is cancelled if the bearer wields a dagger, the attacker wields a weapon, the attacker can be seen, and `proficiencyCheck(null, DEX − 90 + XLEVEL)` passes (120-135). Message: "<S-NAME> deflect(s) @x1 attack from <T-NAME>!" (128). The gate with DEX 18 and XLEVEL 0 is `roll ≤ 28` (adjustment −72, line 129).
- **Thief_Autosneak** (`Thief_Autosneak.java`): when the bearer leaves a room, `Thief_Sneak` (or `Ranger_Sneak`) is cast in that direction instead (106-128), and half of its costs are charged (130-136). A guard flag (`noRepeat`) prevents recursion (98, 127, 140).
- **Thief_DampenAuras** (`Thief_DampenAuras.java`): adds `Ambiance SUPPRESS_MOST` to the bearer (106) and to each carried item (164-176). The message lists the items (177-178).
- **Thief_BuriedTreasure** (`Thief_BuriedTreasure.java`): auto. Putting an item into a "HoleInTheGround" container buries it (96-112). When someone other than the burier digs at that spot, the dig is made noisy when the burier's roll is below `50 + adjustedLevel + 5 × XLEVEL` (133-141). A burier who no longer has the skill gets noisy digs every time (133-135). Message: "You have buried some treasure!" (114-115).

#### 3.9 Movement, group and recon

- **Thief_CarefulStep** (`Thief_CarefulStep.java`): `preInvoke` takes the direction from the last word (106-108), refuses in combat (115-119), and needs an exit (121-125). The difficulty is `levelDiff = caster level + 2 × XLEVEL − highest nearby level` (145-146). Penalty multiplier 8 when negative, 10 when positive (156-159). On success the caster gains +100 DEX for the walk (163). The original DEX is restored after `CMLib.tracking().walk` (165-167).
- **Thief_Ambush** (`Thief_Ambush.java`): needs a group member in the room (112-126), and no visible non-group NPC or PC ("<S-NAME> is watching you too closely.", 127-135). Success gives `Thief_Hide` to every group member (147-152).
- **Thief_CaseJoint** (`Thief_CaseJoint.java`): the caster must be hidden and indoors (290-300). Each tick while still hidden in the room adds one tick of observation, up to `adjustedLevel / 5 + XTIME` (262-267). Speaking, casting, loud noise, or touching other people's things ends the survey (146-178). Once cased, the bonuses apply inside the room (affectPhyStats 193-198): attack `+round(0.25 × level) + 2 + 2 × XLEVEL`, armor `−(round(0.12 × level) + 1 + XLEVEL)`. It adds `3 × ticks` to the thief-skill proficiency bonus (`PROF+THIEF SKILL`, 211). Ticks above 9 add `ticks/10` to the expertise bonus (`XLEVEL+THIEF SKILL`, 212-213). The comment at line 210 says the theoretical maximum is 30 ticks.

#### 3.10 Information, arson and influence

- **Thief_Appraise** (`Thief_Appraise.java`): value, material and weight. A failed appraisal multiplies the value by `(roll(1,100) + 50) / 100`, so 0.51 to 1.50 (115-121), and picks a random material (117). The level penalty is `5 × max(0, item level − (caster level + ability code + 2 × XLEVEL))` (193-197). For the auto form (`asLevel == -1`, commands "MSG" or "WORTH"), the text is written into the command list (154-175); the caller is not examined.
- **Thief_Arsonry** (`Thief_Arsonry.java`): sets fire to a burnable, unburned, gettable item in an adjacent room through an open exit (94-126), or to an item in the same room (127-135). The caster must carry a lit item (136-149). The `Burning` ability is then invoked (167-169).
- **Thief_CondemnMark** (`Thief_CondemnMark.java`): needs a `Thief_Mark` effect (not read; `getMark` returns its `mark` field, 88-94) whose target has a deity (122-127) and has been observed for at least `30 − adjustedLevel/5 − XLEVEL` ticks (129-133). The message is "<S-NAME> condemn(s) <T-NAMESELF> before @x1." (149). It also sends a `HOLYEVENT` `CURSING` to the deity (153-157).
- **Thief_Astrology** (`Thief_Astrology.java`) extends `Thief_Runecasting` (not read). It changes the reading phrases (start 76-85, failure 88-98) and filters players to those whose zap mask uses birthday criteria (113-151).

### 4. Messages players see

Short examples from this bucket (all in the source): "You will now automatically sneak around while you move." (`Thief_Autosneak.java:164`); "The trail seems to continue @x1." (`Thief_Assassinate.java:193`); "You can't get a bead on him." (`Thief_Countertracking.java:107`); "You have buried some treasure!" (`Thief_BuriedTreasure.java:115`); "Awe, leave that local citizen @x1 alone." (`Thief_BlendIn.java:137`); "You will now automatically detect traps when you enter a room." (`Thief_AutoDetectTraps.java:180`). Failure text is usually tied to the rule that blocked the action, so "You need to stand up!" and "Not while you are fighting!" appear as separate checks.

### 5. Interactions with other systems

- `Thief_Mark` (not in this bucket and not read; the bucket uses its `mark` and `ticks` fields) is used by `Thief_Assassinate.java:326-336`, `Thief_CondemnMark.java:88-102` and `Thief_AnalyzeMark.java:99-113`.
- `Thief_ContractHit` gives its killers the `Thiefness` behavior (`Behaviors/Thiefness.java`, read for name only) and invokes `Thief_BackStab` and `Thief_Assassinate` on them (`Thief_ContractHit.java:148-165, 183-189`).
- `MOBS/Assassin.java:60-84` gives the Assassin mob `Thief_Hide` (60), `Thief_Sneak` (66), `Thief_BackStab` (72) and `Thief_Assassinate` (78), each at proficiency 100. `CharClasses/Assassin.java:144` maps `Thief_Assassinate` to class level 14. `Behaviors/MOBHunter.java:147` uses `Thief_Assassinate` for NPC hunting, and `Abilities/Songs/Play_Instrument.java:103` also instantiates it (not examined further).
- `Thief_Countertracking` vetoes any ability with `FLAG_TRACKING` aimed at its bearer (`Thief_Countertracking.java:103-105`), so it also blocks `Thief_Assassinate`.
- Land and ownership: `CMLib.law().doesOwnThisLand` (`Libraries/interfaces/LegalLibrary.java:443`) decides whether traps and concealment last forever (`Thief_DeathTrap.java:186`, `Thief_ConcealPathway.java:228`).
- `Thief_Articles` depends on the `Sailor` behavior (`Behaviors/Sailor.java`, name only) and on the `Boardable` area interface.

## Content worth mining

| Class | Name in game | Family | Key mechanic and numbers |
|---|---|---|---|
| `ThiefSkill` | "a Thief Skill" | base | Leather-armor fumble at 50% (`ThiefSkill.java:94`). |
| `Thief_Articles` | Articles | ship crew | Gunner, defender or boarder; quotas from deck and room counts (339-354). |
| `Thief_Assassinate` | Assassinate | tracking | Range `50 + XLEVEL + 5 × XMAXRANGE` (365); hidden trackers strike with BackStab code 5 (149-156). |
| `Thief_BorrowBoon` | Borrow Boon | theft | Copies a prayer effect; catalyst loses a charge with `12 − XLEVEL`% (411). |
| `Thief_DeathTrap` | Death Trap | trap | 100 metal; attempts `(hardness + XLEVEL) × 2` (349); lethal when `roll + 2 × XLEVEL > save` (232). |
| `Thief_Bind` | Bind | restraint | `IS_BOUND`; amount `level × 25 × speed` (313-314); struggle costs `STR + level` (162). |
| `Thief_CaseJoint` | Case Joint | recon | Survey up to `level/5 + XTIME` ticks (262); bonuses at 193-213. |
| `Thief_ContractHit` | Contract Hit | hitmen | Cost `100 × level` (289); 4-6 assassins from `1d3 + 3` (129). |
| `Thief_DeepCut` | Deep Cut | injury | Threshold 1/20 of base HP (hpReq 20, line 198); head 1/3 (line 259). |
| `Thief_Caltrops` | Caltrops | trap | `1d6 + level` piercing on entry (227); avoid on `roll < SAVE_TRAPS` (220). |
| `Thief_CutThroat` | Cut Throat | sneak attack | Dagger only; damage `× (round(level/6) + 2 + code)` (116-117). |
| `Thief_BackStab` | Back Stab | sneak attack | Excludes blunt, hammer, flail, ranged, thrown and staff (199-204); same multiplier. |
| `Thief_Con` | Con | influence | Forced order; gate `CHA × 2 + min(0, 10 × (caster level + 2X − target level))` (217). |
| `Thief_Bribe` | Bribe | influence | Price `wealth + (100 − (CHA + 2X) × 2) × level` (154-155). |
| `Thief_ConcealPathway` | Conceal Pathway | concealment | Hidden exit with `level + code`; 15 × level ticks (235). |
| `Thief_ConcealWalkway` | Conceal Walkway | concealment | One side indoors (177-183). |
| `Thief_ConcealDoor` | Conceal Door | concealment | Hidden only while closed (109-111). |
| `Thief_ConcealItem` | Conceal Item | concealment | Hidden until taken, pushed or pulled (114-121). |
| `Thief_Belay` | Belay | ship deck | Item cannot be taken (`SENSE_ITEMNOTGET`, 114). |
| `Thief_Appraise` | Appraise | information | Failed appraisal scales value by 0.51-1.50 (115-121). |
| `Thief_AutoSwipe` | AutoSwipe | passive theft | Random eligible victim with discovery ≥ risk (default 90) (127-132). |
| `Thief_Comprehension` | Linguistic Comprehension | passive | Translates unknown languages with a queue (138-163). |
| `Thief_BlendIn` | Blend In | passive protection | Local NPCs veto attacks: roll `> (level gap) × 10` (134). |
| `Thief_AutoDetectTraps` | AutoDetect Traps | passive detection | Deferred scan of room, exits and items on entry (106-139). |
| `Thief_AutoMarkTraps` | AutoMark Traps | passive detection | Synchronous scan, then marks traps (88-143). |
| `Thief_CallUrchins` | Call Urchins | summons | Range `3 + level/5 + XLEVEL` rooms (92); one urchin per cast (172). |
| `Thief_DampenAuras` | Dampen Auras | aura | `SUPPRESS_MOST` on self and carried items (106, 164-176). |
| `Thief_Alertness` | Alertness | perception | Describes `XLEVEL` items on room entry (127-136). |
| `Thief_Arsonry` | Arsonry | arson | Sets a burnable item alight via `Burning` (167-169). |
| `Thief_Autosneak` | AutoSneak | passive | Replaces a move with `Thief_Sneak`; costs halved (130-136). |
| `Thief_CarefulStep` | Careful Step | movement | +100 DEX during the step (163); penalty ×8, bonus ×10 (156-159). |
| `Thief_CondemnMark` | Condemn Mark | religion | Needs a deity and mark ticks ≥ `30 − level/5 − XLEVEL` (129); `CURSING` event (156). |
| `Thief_Ambush` | Ambush | group | Hides every group member (147-152). |
| `Thief_Autocaltrops` | AutoCaltrops | passive trap | Drops caltrops on entry (101); mana bug (115-116). |
| `Thief_Astrology` | Astrology | divination | Subclass of `Thief_Runecasting`; birthday-based filter (113-151). |
| `Thief_BuriedTreasure` | Buried Treasure | auto | Noisy digs by others (133-141). |
| `Thief_DaggerDefense` | Dagger Defense | passive defense | Gate `roll ≤ 100 + DEX − 90 + XLEVEL` (129). |
| `Thief_AnalyzeMark` | Analyze Mark | passive intel | Mark's score after 15 ticks (125-131). |
| `Thief_Countertracking` | Counter-Tracking | passive defense | Vetoes tracking abilities (100-110). |
| `Thief_AvoidTraps` | Avoid Traps | passive defense | `SAVE_TRAPS += proficiency/2 + 5 × XLEVEL` (97). |
| `Thief_DazzlingCaltrops` | Dazzling Caltrops | trap | Drains `roll(3 + X1, 8, 20)` mana from a victim above 6 (72-74). |

Other reusable content:
- Astrology reading phrases (`Thief_Astrology.java:80-82, 92-95`). Start: "I see your stars are affected by...", "I see the alignments will affect...", "I see the calestial motions will cause...". Failure: "Astral clouds are blocking your aura.", "Your future is unbound. Tread carefully.", "Your path is clear.", "The fates' gaze is elsewhere."
- Assassin mob template (`MOBS/Assassin.java:35-100`): human, DEX and WIS 18, sense dark, leather armor, longsword, dagger, and four thief skills at proficiency 100.
- Crew roles and the Sailor parameters (`Thief_Articles.java:173-181`), which describe three shipboard NPC archetypes in a few lines.

## Notable design ideas

- **Skill as one roll and one effect.** Nearly every class has the same shape: check prerequisites, pay, roll once, then either apply a copy of itself as a timed effect or emit a message. The thief files show how far that goes with very little code.
- **Three hook styles for passives.** `okMessage` vetoes (Countertracking, BlendIn, DaggerDefense), `executeMsg` reacts (AutoMarkTraps), and `tick` defers work to a later beat (AutoDetectTraps, Comprehension queue).
- **Stealth as a transient effect.** CutThroat and BackStab add their damage multiplier as a temporary copy that exists only for the swing, then remove it (CutThroat 236-250). The effect is the stealth.
- **One notice formula for theft.** BorrowBoon (312-324) uses `DEX × 3 − target WIS × 5 − level gap × 5 + X1 × 5`, and Swipe (`Thief_Swipe.java:173-176`) uses the same shape with a `× 3` level-gap term. The result is the chance of being noticed, and the code compares it against a percentage roll.
- **Level gap as a proficiency gate.** `proficiencyCheck` takes one signed number. A positive value is a bonus, and a negative value is a gate that fails outright. Thief code reuses this for most level-gap rules (Con, Bribe, Appraise, Arsonry, Articles, BorrowBoon).
- **Traps with budgets.** DeathTrap gives each trap `attempts = (hardness + XLEVEL) × 2` (349) and resets on the day cycle (134). A trap is a finite resource, not a permanent switch.
- **Ships as data.** Articles derives crew quotas from the number of deck and interior rooms (339-354), so crew composition follows the ship's layout.
- **Bind as a resistance budget.** `amountRemaining` drops by strength plus level per struggle (`Thief_Bind.java:162`). Escape is a count, not a roll, so it is easy to explain to a player.
- **Message queue for translation.** Comprehension queues each overheard line and delivers it at a proficiency-gated rate (`Thief_Comprehension.java:105-119`).
- **Calendar hook.** Astrology's filter selects players whose zap mask uses birthday criteria (113-151). A single-player game could key divination to the in-game calendar.

## Problems and cruft

Each item cites the code as read. Items marked "unverified" depend on code outside this bucket that was not traced.

1. **Precedence bug in Condemn Mark.** `Thief_CondemnMark.java:138` computes `target level − (caster level + 2 × XLEVEL) × 15`. The ×15 applies to the caster side, so the penalty is effectively always zero. The intended form is probably `(target − (caster + 2X)) × 15`, as in `Thief_Con.java:214`.
2. **Dead cooldown.** `Thief_CondemnMark.java:104` declares `recentCondemnations` and line 116 checks it, but nothing adds to it (grep finds no other use in the file). The "too soon" refusal never fires.
3. **Mana and movement swapped on restore.** `Thief_Autocaltrops.java:115-116` restores mana, then sets mana again to the saved movement value. Movement is never restored, and mana ends up holding the old movement number.
4. **One urchin per call.** `Thief_CallUrchins.java:172` returns from inside the loop, so only the first reachable urchin gets `Skill_Track`. Whether this is intended is not stated in the code.
5. **Bribes look free.** `Thief_Bribe.java:176` subtracts the price from the caster and `:187` adds it back after the order is given. The net transfer is zero. The target receives nothing.
6. **Bribe price is unclamped.** `Thief_Bribe.java:155` can become zero or negative when `CHA + 2 × XLEVEL >= 50`.
7. **Deep Cut field default.** `Thief_DeepCut.java:96` sets `hpReq = 8`, but `invoke` sets 20 (198), so the field default is never used. The comment "ends up around 5%" matches 20.
8. **Possible division by zero (unverified).** `Thief_DeepCut.java:139-140` divides by `hpReq − XLEVEL`, and line 108 divides by `20 − XLEVEL`. A head cut with XLEVEL 3, or any cut with XLEVEL 20, would divide by zero. Nothing clamps XLEVEL here, and whether expertise can reach these values was not checked.
9. **Unguarded invoker.** `Thief_DazzlingCaltrops.java:67` calls `invoker().mayIFight` with no null check. The parent handles a null invoker by falling back to the deity (`Thief_Caltrops.java:218`). Line 74 uses the victim's `X1`, not the trap's invoker.
10. **Missing invoker on the stealth copy (unverified effect).** `Thief_BackStab.java:224` does not call `setInvoker(mob)`, unlike `Thief_CutThroat.java:236`. The copy's `affectPhyStats` reads `getXLEVELLevel(invoker())` (line 119). `StdAbility.java:1967-1968` sets the invoker only when `XMAXRANGE > 0`, so the expertise bonus may be lost. Not traced further.
11. **Caster and target confused in toggles.** `target.delEffect(mob.fetchEffect(ID()))` appears in `Thief_AutoSwipe.java:179`, `Thief_AutoDetectTraps.java:165`, `Thief_AutoMarkTraps.java:156` and `Thief_Autocaltrops.java:134`. It removes the caster's effect from the target. It is harmless only when target and caster are the same.
12. **Duplicated calls.** `item.recoverPhyStats()` runs twice in `Thief_ConcealItem.java:170-171` and `Thief_Belay.java:194-195`.
13. **Unused state.** `Thief_ConcealDoor.java` computes `abilityCode` but its level change is commented out (112-114). `Thief_ConcealItem.java:89-101` stores a code that `affectPhyStats` never reads. `Thief_Autocaltrops.java:88` declares `noRepeat`, which is never set to true, so its guard is dead.
14. **Fragile assumption about the Assassin mob.** `Thief_ContractHit.java:187-189` assumes `fetchAbility("Thief_Assassinate")` is non-null. It is safe today only because `MOBS/Assassin.java:78-84` grants the skill.
15. **Two trap effects on owned land (unverified).** `Thief_DeathTrap.java:183` adds the trap `T` to the room, and line 187 also adds a copy of the skill when the room is owned or permanent. Both would spring on entry (each checks its own `sprung`). Whether that doubles the attack was not traced.
16. **Shared effect instance (unverified).** `Thief_DampenAuras.java:172-173` attaches the same effect object to several items and resets its affected object to the mob each time.
17. **Side channel in Appraise.** `Thief_Appraise.java:154-175` writes text into the caller's command list for the auto form. The caller is not identified.
18. **Multiplayer assumptions** (relevant to SUD, see takeaways): ship crews and "owned land" (`Thief_Articles.java:266`, `Thief_DeathTrap.java:186`), group exemptions from traps (`Thief_Caltrops.java:206-207`, `Thief_DeathTrap.java:277`), "is watching you too closely" checks against other PCs (`Thief_Ambush.java:127-135`), and `Thief_DeathTrap.invoker()` reloading offline players (`Thief_DeathTrap.java:250-264`).
19. **Repetition.** Almost every class repeats the same `invoke` skeleton (caster checks, target lookup, `super.invoke`, roll, message, effect). A single template would remove most of the copy-paste.

## Takeaways for a Python SUD

- **Data-first skill record.** Describe each skill as a small record: id, name, family, cost (`mana`, `stamina`), a quality tag (malicious, neutral, beneficial), flags (tracking, stealing, binding, summoning), and the numbers it needs. A sketch:

  ```python
  @dataclass(frozen=True)
  class Skill:
      id: str
      name: str
      family: str                      # "trap", "tracking", "passive", ...
      cost: dict[str, int]             # {"stamina": 2}
      quality: Quality                 # MALICIOUS / NEUTRAL / BENEFICIAL
      flags: frozenset[str] = frozenset()
  ```

- **One proficiency function.** Port `proficiencyCheck` (StdAbility.java:1441-1486) once and reuse it. Negative modifiers are gates; positive ones are bonuses, and the base chance is clamped before bonuses.

  ```python
  def proficiency_check(prof: int, expertise: int, adjustment: int, rng: random.Random) -> bool:
      chance = max(5, min(95, prof + 2 * expertise)) + expertise // 2
      if adjustment >= 0:
          chance += adjustment
      elif rng.randint(1, 100) > 100 + adjustment:
          return False
      return rng.randint(1, 100) < chance
  ```

- **Effects as objects with hooks.** Replace the Java event bus with a list of effects per entity, each optionally implementing `ok_message(event) -> bool` (veto), `on_event(event)` (react), and `tick() -> bool` (False means expired). Passives such as AutoDetect and Countertracking become small classes.
- **Stealth = a notice roll.** Keep one notice formula modelled on BorrowBoon: `DEX × 3 − target WIS × 5 − level gap × 5 + expertise × 5`, clamped to 5..95 (`Thief_BorrowBoon.java:312-324`). Use it for theft, pickpocketing and whether a bound or tracked NPC notices.
- **Traps as budgeted room effects.** A trap has `attempts` (from material hardness and expertise), a `spring(entity)` method called on the enter event, and a reset time in game days. Caltrops (non-destructive) and DeathTrap (budgeted, lethal) show two distinct patterns.
- **Bind as a counter.** Escape is a count of struggles against a strength-based budget. It is easier to explain than a roll per struggle.
- **Keep:** the passive-effect pattern, the per-attack stealth copy (CutThroat/BackStab), the translation queue (Comprehension), Articles' quota-from-layout idea (to a single-player "crew" or "guard" roster), and Alertness's item-hint on room entry.
- **Simplify:** merge the "Con" and "Bribe" pair into one command with a method (talk or pay). Merge the four conceal skills into one "hide target" effect parameterized by target kind. Drop the group, ownership and "watching you" checks, which assume other humans. Replace them with NPC awareness (the notice roll) and a per-building "owner" flag.
- **Drop:** the Articles/Sailor machinery unless the SUD has ships, the offline-player reload in DeathTrap, and the multiplayer summon tattoos.
- **Single-player note.** The "owned land" permanence maps well to a player-owned home. The "Thief_Countertracking" veto is a useful single-player idea too: an NPC with a counter-skill can block a tracking attempt.

## Coverage

- **Read in full (all 41 files, body from after the license header through the final brace):** `Abilities/Thief/ThiefSkill.java`, `Thief_Articles.java`, `Thief_Assassinate.java`, `Thief_BorrowBoon.java`, `Thief_DeathTrap.java`, `Thief_Bind.java`, `Thief_CaseJoint.java`, `Thief_ContractHit.java`, `Thief_DeepCut.java`, `Thief_Caltrops.java`, `Thief_CutThroat.java`, `Thief_BackStab.java`, `Thief_Con.java`, `Thief_ConcealPathway.java`, `Thief_ConcealDoor.java`, `Thief_ConcealWalkway.java`, `Thief_Appraise.java`, `Thief_AutoSwipe.java`, `Thief_Comprehension.java`, `Thief_Belay.java`, `Thief_BlendIn.java`, `Thief_Bribe.java`, `Thief_AutoDetectTraps.java`, `Thief_AutoMarkTraps.java`, `Thief_CallUrchins.java`, `Thief_DampenAuras.java`, `Thief_Alertness.java`, `Thief_ConcealItem.java`, `Thief_Arsonry.java`, `Thief_Autosneak.java`, `Thief_CarefulStep.java`, `Thief_CondemnMark.java`, `Thief_Ambush.java`, `Thief_Autocaltrops.java`, `Thief_Astrology.java`, `Thief_BuriedTreasure.java`, `Thief_DaggerDefense.java`, `Thief_AnalyzeMark.java`, `Thief_Countertracking.java`, `Thief_AvoidTraps.java`, `Thief_DazzlingCaltrops.java`.
- **Skimmed:** none. Every bucket file was read in full.
- **Skipped:** the copyright and license headers and the import blocks (boilerplate).
- **Outside the bucket, read for mechanics:** `Abilities/StdAbility.java` (lines 400-470, 877-925, 1441-1486, 1810-1846, 1964-2090, 2140-2384, partly); `Abilities/interfaces/Ability.java` (constants, grep only); `Libraries/Sense.java:1399-1404`; `Libraries/CoffeeUtensils.java:1061-1075, 1151-1175`; `Libraries/Dice.java:398-412`; `CharClasses/interfaces/CharClass.java:658-662`; `Libraries/interfaces/CMFlagLibrary.java:396` (signature only); `Libraries/interfaces/LegalLibrary.java:443` (signature only); `Libraries/CMAbleMap.java:2885` (signature only); `Abilities/Thief/Thief_Swipe.java:160-181` (for AutoSwipe); `MOBS/Assassin.java:35-100`; `CharClasses/Assassin.java:144`; `Behaviors/MOBHunter.java:147`.
- **Not read (names only):** `Abilities/Thief/Thief_Mark.java`, `Thief_Sneak.java`, `Thief_Hide.java`, `Thief_MarkTrapped.java`, `Thief_IdentifyTraps.java`, `Thief_DetectTraps.java`, `Thief_MyUrchins.java`, `Thief_Runecasting.java` (superclass of Astrology), `Behaviors/Sailor.java`, `Behaviors/Thiefness.java`, and the `Skill_Track`, `Burning` and `Deity` holy-event code. Statements about them are limited to what the bucket files show.
