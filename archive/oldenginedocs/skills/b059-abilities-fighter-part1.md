# Fighter abilities, part 1: grapples, called strikes, passives, mounts and tactics

> Part of the CoffeeMud NEWOVERVIEW. Bucket: `b059` (Abilities/Fighter [part 1/3: FighterGrappleSkill .. Fighter_DesperateMoves]) · 52 files · 9567 lines of Java.

**Citation convention.** Paths are relative to the CoffeeMUD folder, with the common prefix `com/planet_ink/coffee_mud/` dropped. So `Abilities/Fighter/Fighter_Behead.java:181` means `com/planet_ink/coffee_mud/Abilities/Fighter/Fighter_Behead.java` line 181. Where only a file name is given (for example `Fighter_Behead.java:181`), it is in `Abilities/Fighter/`. "Outside" means a file that is not in this bucket and was read only to explain an interaction.

## Summary

This bucket is the combat-manoeuvre half of the Fighter ability folder: grapples and holds, called and finishing strikes, knockdowns and movement, reactive passives (parries, catches, counters), toggled "auto" skills, terrain and vanity bonuses, and mounts and companions. Almost all of them are `FighterSkill` subclasses that fire from a trigger word (for example `BULLRUSH`), make one proficiency roll, and then either apply a tick-counted effect or send a single message. The passives are the most interesting part: they hook `okMessage` and `executeMsg` to veto or amend other people's attacks, and they add reactions as trailing messages. The grapple family is a good model for a contest of opposed stats that is resolved a little at a time. The mount and companion skills turn animals into persistent followers. Several skills contain clear bugs, which are listed under "Problems and cruft".

## Key files and types

| File or class | Role | Lines |
|---|---|---|
| `Abilities/Fighter/FighterSkill.java` | Base for the family: skill classification, movement usage, lookup of the grapple effect on a mob | 100 |
| `Abilities/Fighter/FighterGrappleSkill.java` | Base for holds: paired effects, per-tick upkeep, escape contest, restrictions on the held party | 502 |
| `Abilities/Fighter/Fighter_ClinchHold.java`, `Fighter_ArmHold.java`, `Fighter_ChokeHold.java`, `Fighter_BearHug.java`, `Fighter_AnkleLock.java` | Concrete holds, each with its own restriction or bonus | 188, 170, 148, 115, 214 |
| `Abilities/Fighter/Fighter_Bulldog.java`, `Fighter_Breakout.java`, `Fighter_BodyShield.java` | Skills that use an existing hold: slam from a head hold, struggle free, use a victim as a shield | 199, 209, 140 |
| `Abilities/Fighter/Fighter_CalledStrike.java`, `Fighter_CalledShot.java` | Limb amputation by a called blow (melee and ranged) | 303, 84 |
| `Abilities/Fighter/Fighter_Behead.java`, `Fighter_CoupDeGrace.java`, `Fighter_AtemiStrike.java` | Finishing blows: legal execution, coup-de-grace on a helpless foe, delayed lethal strike | 242, 211, 228 |
| `Abilities/Fighter/Fighter_Charge.java`, `Fighter_BullRush.java`, `Fighter_CircleTrip.java`, `Fighter_BodyFlip.java`, `Fighter_BodyToss.java`, `Fighter_Cartwheel.java` | Movement, range and knockdown | 242, 170, 263, 240, 176, 135 |
| `Abilities/Fighter/Fighter_Cleave.java`, `Fighter_CounterAttack.java`, `Fighter_CatchProjectile.java`, `Fighter_DeflectProjectile.java`, `Fighter_CircleParry.java`, `Fighter_CoverDefence.java` | Reactive passives that veto or amend another party's attack | 182, 123, 163, 149, 137, 124 |
| `Abilities/Fighter/Fighter_CriticalShot.java`, `Fighter_CritStrike.java`, `Fighter_DesperateMoves.java`, `Fighter_BlindFighting.java`, `Fighter_BackHand.java`, `Fighter_CrescentKick.java`, `Fighter_AweMounts.java` | Damage and defence passives and a few extra attacks | 122, 121, 112, 129, 167, 139, 132 |
| `Abilities/Fighter/Fighter_AutoBash.java`, `Fighter_AutoHammerRing.java`, `Fighter_AutoPistolwhip.java` | Toggled "auto" skills that fire a second skill in melee | 189, 193, 164 |
| `Abilities/Fighter/Fighter_ConfidentVanity.java`, `Fighter_ArmoredVanity.java`, `Fighter_ArmorTweaking.java`, `Fighter_CosmicAdaptation.java` | Bonuses keyed to expensive armour, tailored armour, planes of existence | 196, 197, 226, 226 |
| `Abilities/Fighter/Fighter_FieldTactics.java` (outside bucket, base of the next three) | Terrain-keyed passive bonus, plus hiding while sitting | 231 (outside) |
| `Abilities/Fighter/Fighter_CaravanTactics.java`, `Fighter_CaveTactics.java`, `Fighter_DesertTactics.java` | Terrain variants of FieldTactics | 73, 57, 57 |
| `Abilities/Fighter/Fighter_CaravanCommander.java` | Area-wide bonuses while riding a land or wagon caravan | 216 |
| `Abilities/Fighter/Fighter_CallSteed.java` | Summons a race-matched steed, remembers the last one in its own text | 618 |
| `Abilities/Fighter/Fighter_CommandMount.java`, `Fighter_CompanionMount.java` | Ordering a favoured animal; turning a ridden animal into a permanent companion | 156, 269 |
| `Abilities/Fighter/Fighter_Berzerk.java`, `Fighter_Battlecry.java`, `Fighter_BloodBrother.java` | Morale and social skills: rage, group attack bonus, blood-brother bond between two players | 187, 162, 157 |
| `Abilities/Fighter/MonkSkill.java` (outside) | Base of Atemi, BackHand, BodyToss and CircleParry; adds `anyWeapons()` | 57 (outside) |
| `Abilities/StdAbility.java` (outside) | Proficiency check, tick-down durations, `usageCost()` | read by line range |
| `Libraries/MUDFight.java` (outside) | Hit roll and armour adjustment | read by line range |

## How it works

### 0. Rules shared by the whole bucket

**The proficiency roll.** Almost every skill ends in `proficiencyCheck(mob, adjustment, auto)`. The core is at `StdAbility.java:1441-1485`:

- With `auto` set, the check returns true at once (`StdAbility.java:1443-1449`).
- Otherwise the chance starts from the ability's proficiency, plus any `PROF` stat adjustments, plus `2 × XLEVEL` (`StdAbility.java:1452-1468`).
- That chance is clamped to 5 to 95, unless the DIS955RULE setting is on (`StdAbility.java:1470-1477`). Then `XLEVEL / 2` is added (`StdAbility.java:1478`).
- A non-negative `adjustment` is added to the chance (`StdAbility.java:1480-1481`).
- A negative `adjustment` is a pre-gate, not a subtraction. The check fails when `d100 > 100 + adjustment` (`StdAbility.java:1482-1484`). So a modifier of -30 fails 30% of the time before the normal roll is made.
- The final test is `d100 < chance` (`StdAbility.java:1485`).

Auto-passives call the check with a null mob (for example `CircleParry`, `CounterAttack`, `CriticalShot`), which skips the stat and `XLEVEL` terms. A grapple also adds `proficiencyDiff`, set by the free attack at the start of the hold (`FighterGrappleSkill.java:314-319`, `474-499`).

**The hit roll.** `CMLib.combat().rollToHit(attacker, defender)` builds the attack and defence numbers (`Libraries/MUDFight.java:394-406`). The defence term is `adjustedArmor`, which runs the armour stat through `FORMULA_ARMORADJUSTMENT`. The default formula subtracts 100 at the end (`core/CMProps.java:3203`). `rollToHit(int,int,int)` then negates the defence to get `myArmor` and rolls under `round(50 × attack / myArmor) + adjustment` (`Libraries/MUDFight.java:409-424`).

**Which way armour counts.** Worn items reduce the wearer's armour stat (`Items/Armor/StdArmor.java:639`: `armor() - phyStats().armor()`), so a lower armour stat is better defence. This matters for reading the buffs. `FieldTactics` (`Fighter_FieldTactics.java:223`), `ArmoredVanity` (`Fighter_ArmoredVanity.java:129`), `BearHug` (`Fighter_BearHug.java:75`) and `ArmorTweaking` (which raises the item's own armour value) all lower the stat or raise the item's value to help the wearer. `Berzerk` (`Fighter_Berzerk.java:104`) and `Charge` (`Fighter_Charge.java:154`) raise the wearer's stat to hurt their defence on purpose. The `CaravanCommander` group bonus, which raises the stat, looks like the same sign used the wrong way (see Problems).

**Durations.** The count of ticks is set by `StdAbility.java:2312-2349` and `2174-2185`.

- A beneficial effect with an argument of 0 uses the standard duration of `caster level × 4 + 40` ticks (`StdAbility.java:2325`).
- A malicious effect with an argument of 0 uses `round(adjusted level × 1.1) + 25` ticks (`StdAbility.java:2178`).
- A positive argument is an absolute tick count (`StdAbility.java:2176-2177`, `2314-2315`). Several skills pass 2 for "two ticks" (for example `Fighter_CircleTrip.java:251`).
- `makeLongLasting()` sets the count to `Integer.MAX_VALUE` (`StdAbility.java:2825-2828`).
- A tick is 4000 ms by default (`core/CMProps.java:681`).

**Effects and their tick.** `StdMOB` calls `A.tick()` for each effect and calls `A.unInvoke()` when the result is false (`MOBS/StdMOB.java:4326-4328`). `StdAbility.unInvoke()` sets its `unInvoked` flag first, and only removes the effect if `canBeUninvoked()` (`Abilities/StdAbility.java:1501-1509`). Several skills return false from `tick()` to stop their own work. Because `canBeUninvoked()` is false for the mount and passive effects, that return only sets a flag and does not remove the effect. `Fighter_CompanionMount.java:127-146` relies on this.

**Costs.** Holds charge upkeep each tick through `usageCost(mob, false)`, which returns `{mana, moves, hit points}` (`FighterGrappleSkill.java:144-149`). Most of the bucket uses `USAGE_MOVEMENT` (`Abilities/interfaces/Ability.java:624`; `FighterSkill.java:59-62`).

**Two-phase messages.** Nearly every passive in this bucket works through `okMessage`, which may veto (return false), and `executeMsg`, which runs afterwards. A passive can also add work to the message being processed, through `addTrailerMsg` (`Fighter_CounterAttack.java:111-112`, `Fighter_BodyShield.java:124-126`) or `addTrailerRunnable` (`Fighter_ConfidentVanity.java:190-191`).

### 1. The family base: `FighterSkill`

`FighterSkill.java:36-100` is a `StdAbility` with:

- `classificationCode()` set to `ACODE_SKILL` (`FighterSkill.java:53-56`), with a domain added by each subclass.
- `usageType()` set to `USAGE_MOVEMENT` (`FighterSkill.java:59-62`).
- `canAffectCode()` set to 0 and `canTargetCode()` set to `CAN_MOBS` (`FighterSkill.java:65-74`).
- `getGrappleA(mob)` returns the first `FighterGrappleSkill` effect on a mob (`FighterSkill.java:76-87`). `ungrapple(mob)` unInvokes every such effect (`FighterSkill.java:89-99`).

`MonkSkill.java:36-57` (outside) extends `FighterSkill` and adds `anyWeapons(mob)`, which is true if the mob has a wielded or held item (`MonkSkill.java:52-55`). The Monk-family skills use it as a gate.

### 2. Grapples: `FighterGrappleSkill` and its holds

#### 2.1 Data model and pairing

- `pairedWith` (the other party), `tickUp` (escape progress), `broken` and `proficiencyDiff` are the fields (`FighterGrappleSkill.java:70-73`).
- `flags()` returns `FLAG_BINDING` (`FighterGrappleSkill.java:58-61`). `Fighter_Breakout` finds bindings by this flag (`Fighter_Breakout.java:101`).
- `classificationCode()` is `ACODE_SKILL | DOMAIN_GRAPPLING` (`FighterGrappleSkill.java:52-55`). `abstractQuality()` is `QUALITY_MALICIOUS` (`FighterGrappleSkill.java:87-91`).

**Pairing.** `finishGrapple(mob, baseTicks, target, asLevel)` (`FighterGrappleSkill.java:332-357`) does the following:

- Sets `tickUp = 0`.
- Sets `duration = baseTicks + XLEVEL / 2` (`FighterGrappleSkill.java:335`).
- Creates one effect on the target and one on the mob, each via `maliciousAffect(..., duration, -1)` (`FighterGrappleSkill.java:336-337`).
- If either side fails, unInvokes the other (`FighterGrappleSkill.java:339-349`).
- Otherwise sets each side's `pairedWith` to the other party (`FighterGrappleSkill.java:352-353`).

Callers use `baseTicks = 4` (`Fighter_ClinchHold.java:177`, `Fighter_ArmHold.java:159`, `Fighter_ChokeHold.java:137`, `Fighter_BearHug.java:104`, `Fighter_AnkleLock.java:203`). So a hold lasts 4 + XLEVEL/2 ticks (4 seconds per tick at the default setting) unless it is broken first.

**Upkeep and the holder's side of tick.** `tick()` (`FighterGrappleSkill.java:125-175`) first checks that the pair still exists in the same room (`FighterGrappleSkill.java:133-139`). If the holder is ticking (`invoker() == mob`, lines 141-161), it does the following:

- Spends `usageCost` on mana, moves and hit points (`FighterGrappleSkill.java:144-149`). If it cannot afford this, the tick returns false (`FighterGrappleSkill.java:145-146`).
- Shows the struggle to the room.
- Rolls `CMLib.combat().rollToHit(mob, pairedWith)` each tick. A miss sets `broken` and unInvokes (`FighterGrappleSkill.java:152-158`).

**The held side's escape.** When the held party ticks (`invoker() == pairedWith`, lines 163-171), it increments `tickUp` and calls `makeBreakAttempt()` (`FighterGrappleSkill.java:168-170`).

`makeBreakAttempt()` (`FighterGrappleSkill.java:103-123`) is a contest of stats:

- Holder score: `wbest = max(DEX, STR) + XLEVEL(holder)` (`FighterGrappleSkill.java:109-111`).
- Held score: `mbest = min(DEX, STR) + XLEVEL(held) + 2 × tickUp` (`FighterGrappleSkill.java:112-115`).
- If `mbest >= wbest`, the held party breaks free (`FighterGrappleSkill.java:116-121`).

The holder's own score does not change during the contest. The held party's score grows by 2 for each attempt.

**Restrictions on the held party.** `okMessage()` (`FighterGrappleSkill.java:208-293`) does the following:

- Ends the hold when the paired party dies (`FighterGrappleSkill.java:215-221`).
- Lets the holder type a release command (`TYP_HUH` branch, `FighterGrappleSkill.java:225-243`). The command is `RELEASE`, matched by prefix.
- For hand and move actions by the bound party, first makes an escape attempt if the actor is not the holder (`FighterGrappleSkill.java:253-257`). Then it checks `isHandsFree()` and `isImmobilizing()`.
- Vetoes the action with "You are holding a X!" or "You are in a X!" (`FighterGrappleSkill.java:266-282`). It allows actions whose tool is an ability in the exempt domains (`GRAPPLING` and `DIRTYFIGHTING`, `FighterGrappleSkill.java:177-184`).
- If the holder itself uses a non-weapon ability from an exempt domain, the hold is released (`FighterGrappleSkill.java:285-287`).

**Entry conditions.** `invoke()` (`FighterGrappleSkill.java:418-501`) does the following:

- Refuses to start while in combat at range (`FighterGrappleSkill.java:433-437`).
- Refuses a non-automatic invocation when the target's base weight is more than three times the caster's (`FighterGrappleSkill.java:439-445`). The code writes `mob.baseWeight() < target.baseWeight() - mob.baseWeight()*2`, which is the same as `target > 3 × mob`.
- Requires the caster to be able to move (`FighterGrappleSkill.java:447-451`), to share the target's riding state (`FighterGrappleSkill.java:453-460`), and to be standing unless the hold is immobilising (`FighterGrappleSkill.java:462-468`).

**Free attack and penalty.** If the target is in combat, or the target is fighting the caster, and the hold is not already in place, the victim gets a free attack (`FighterGrappleSkill.java:474-483`). The penalty for damage taken in that attack is computed as follows:

- `xdmg = XLEVEL × 5` (`FighterGrappleSkill.java:488`).
- `pct = (hp lost / max hp) × 100 - xdmg` (`FighterGrappleSkill.java:489`).
- If `pct > 0`, `proficiencyDiff = -round(pct)` (`FighterGrappleSkill.java:490-491`).
- A further penalty of `10 × (target level - (caster level + 2 × XLEVEL))` applies when that is positive (`FighterGrappleSkill.java:494-499`).

**End of hold.** `unInvoke()` (`FighterGrappleSkill.java:359-410`) writes one of several messages: broke out, released, or released from the hold. It then calls `postStand` (`FighterGrappleSkill.java:407`).

#### 2.2 The holds

Each hold is a `FighterGrappleSkill` subclass with the same invoke shape. It checks the target, calls `super.invoke`, rolls `rollToHit` (or a grapple already in place counts as a hit), applies `proficiencyCheck`, sends a malicious move message, and then calls `finishGrapple(mob, 4, target, asLevel)` on success. The per-hold differences are these:

| Hold (`Fighter_X.java`) | Requirement | Effect on the held party | Effect on the holder | Lines |
|---|---|---|---|---|
| `ClinchHold` | none beyond the base | Only natural or no-weapon attacks are allowed (`ClinchHold.java:77-83`). Removing its own weapon is allowed (`ClinchHold.java:85-90`). Casting spells is blocked (`ClinchHold.java:103-111`). An NPC held this way is disarmed every tick (`ClinchHold.java:132-141`), and the weapon is re-wielded on end (`ClinchHold.java:119-125`). | none | 72-114, 128-143 |
| `ArmHold` | target has an arm (`ArmHold.java:134-138`) | Speed halved, or reduced by 1 if at least 2 (`ArmHold.java:71-74`). Hands are not free unless the held party has more than one arm (`ArmHold.java:78-84`). Hand actions on its own weapon are blocked (`ArmHold.java:94-100`). | none | 68-84, 87-125 |
| `ChokeHold` | target has a neck (`ChokeHold.java:114-118`) | Cannot breathe, through the `CAN_NOT_BREATHE` sense flag (`ChokeHold.java:71-73`). Cannot speak, by vetoing mouth messages (`ChokeHold.java:89-96`). | none | 68-100, 102-147 |
| `BearHug` | none beyond the base | none | Armour stat lowered by the held party's level and `5 × XLEVEL` (`BearHug.java:71-76`). That is a defensive gain for the hugger. | 71-114 |
| `AnkleLock` | target has a foot (`AnkleLock.java:167-171`); target must be sitting unless it is the caster (`AnkleLock.java:173-177`) | Forced to sit unless asleep (`AnkleLock.java:68-73`). Each tick the held party's wielded item is removed (`AnkleLock.java:114-115`). After 3 ticks, a roll under `chanceOfStrength` breaks the foot through `BrokenLimbs` (`AnkleLock.java:98-113`). | none | 68-119, 161-213 |

`AnkleLock.chanceOfStrength` is `10 × (caster STR - target STR)`, with a minimum of 10 and a fallback of 50 when there is no caster (`AnkleLock.java:75-86`).

#### 2.3 Skills that use an existing hold

- **Bulldog** (`Fighter_Bulldog.java:108-198`). Needs the target to be in a head hold (`Fighter_ChokeHold` or `Fighter_Headlock`) from someone other than the target (`Bulldog.java:135-142`). It makes that hold non-uninvokable for the duration of the move (`Bulldog.java:151-159`), then restores its uninvokable and savable flags in `finally` (`Bulldog.java:184-193`). It sends a natural "bulldog slam" weapon attack (`Bulldog.java:168-175`) and marks the target sitting (`Bulldog.java:178-181`).
- **Breakout** (`Fighter_Breakout.java:91-126`). A struggle against any binding effect. Each tick, when the caster is still bound, it sets `strBonus = XLEVEL + 2 × STR`, which is added to both STR and DEX while the struggle runs (`Breakout.java:108`, `147-151`). It then sends a hand message through every binding's `okMessage` and `executeMsg` (`Breakout.java:112-117`). For a grapple, that message reaches `makeBreakAttempt`, so the struggle is an escape attempt in its own right. The bonus is reset in `finally` (`Breakout.java:119-123`).
- **BodyShield** (`Fighter_BodyShield.java:96-131`). Works for the grappler, not the held party. It applies when the mob is the invoker of a hold of its own (`getGrappleA(mob)` with `invoker()==mob`) and its current victim is also in a hold (`BodyShield.java:105-119`). Then, when someone other than the victim damages the mob with a weapon, a fraction equal to the grappler's proficiency is redirected: `regain = round(damage × proficiency / 100)` (`BodyShield.java:122`). The grappler takes the rest, and the held victim takes `regain` through a trailer damage message (`BodyShield.java:123-126`). It works once per round (`BodyShield.java:115`, `121`, reset at `134-139`). The victim's weight must be at least half the grappler's (`BodyShield.java:116`).

### 3. Called and finishing strikes

**CalledStrike** (`Fighter_CalledStrike.java:35-303`). The caller names a body part, and a hit that is strong enough removes it.

- Trigger `CALLEDSTRIKE` (`Fighter_CalledStrike.java:51`).
- While the effect is active the caller has `-100` attack adjustment (`Fighter_CalledStrike.java:98-102`). The effect lasts 2 ticks (`Fighter_CalledStrike.java:290`), or until the next damage message.
- The prerequisites are that the caster is in range while fighting, and has a wielded slashing weapon (`Fighter_CalledStrike.java:150-174`).
- A monster picks a random remaining limb (`Fighter_CalledStrike.java:236-237`). A player must name one, and a list is shown otherwise (`Fighter_CalledStrike.java:239-247`).
- Naming "head" sets the divisor to 3 (`Fighter_CalledStrike.java:254-255`). Any other limb uses 9.
- On the next damage message from the caller to the target, the threshold is `base HP / (divisor + XLEVEL)` (`Fighter_CalledStrike.java:136-137`). If the hit is at least the threshold, the damage is increased by the threshold (`Fighter_CalledStrike.java:138-141`) and the limb is amputated through the `Amputation` effect (`Fighter_CalledStrike.java:104-122`, outside `Misc/Amputation.java`). Otherwise the message is "You failed to cut off ...". Either way the effect ends (`Fighter_CalledStrike.java:146`).
- The injury effect, if present, is given the caller's name and the limb (`Fighter_CalledStrike.java:291-293`).

**CalledShot** (`Fighter_CalledShot.java:35-84`) extends CalledStrike. It replaces the prerequisites so that the caller must be at range and holding a ranged or thrown weapon (`Fighter_CalledShot.java:59-82`). Trigger `CALLEDSHOT`.

**Behead** (`Fighter_Behead.java:36-242`). A legal execution.

- Refused unless the target has a head (`Behead.java:131-135`) and the local law has a warrant for the target, or the caster has ABOVELAW security (`Behead.java:139-149`). The same warrant check gates the casting quality (`Behead.java:103-121`).
- The weapon must be slashing and the target must be bound or held (`Behead.java:151-176`) unless the caster is a system operator.
- On success the target's HP is set to 1 (`Behead.java:194`), a damage message of `Integer.MAX_VALUE / 2` is posted (`Behead.java:198`), and a "head" item (`GenLimb`, weight 5, level 1, named after the target) is dropped in the room (`Behead.java:200-223`). Its material comes from the race's first flesh resource, or the last resource (`Behead.java:207-219`).
- Each warrant is then marked "pardoned" with zero offences (`Behead.java:224-229`).
- Trigger `BEHEAD` (`Behead.java:52`). The grant is secret (see Content).

**CoupDeGrace** (`Fighter_CoupDeGrace.java:35-211`). A finishing blow on a sleeping target.

- Requires combat at melee range (`CoupDeGrace.java:128-137`), standing, and a slashing or piercing weapon (`CoupDeGrace.java:143-158`).
- Requires 150 movement (`CoupDeGrace.java:159-163`). `overrideMana()` returns 150 (`CoupDeGrace.java:94-98`), but the code checks it against movement.
- Requires the victim to be sleeping (`CoupDeGrace.java:164-171`).
- Chance: `proficiencyCheck(mob, -3 × levelgap - 2 × CON, auto)` with a hit roll on top (`CoupDeGrace.java:179-186`).
- Success only when the victim has under 50 HP or under a quarter of its maximum (`CoupDeGrace.java:187-188`). The victim is then set to 1 HP and the damage is applied as the HP it had (`CoupDeGrace.java:196-197`).

**AtemiStrike** (`Fighter_AtemiStrike.java:36-228`, a `MonkSkill`). A delayed lethal strike.

- Needs no weapons and hands (`AtemiStrike.java:175-191`). Golems are immune (`AtemiStrike.java:181-185`). Mana override is 100 (`AtemiStrike.java:79-83`).
- Chance: `proficiencyCheck(mob, -20 × levelgap - (target STR - caster STR), auto)` and a hit (`AtemiStrike.java:196-203`).
- On success the target gets a malicious effect for `20 - XLEVEL / 2` ticks (`AtemiStrike.java:215-216`). Its `tickTarget` is half that (`AtemiStrike.java:218`).
- The effect counts its ticks in `tickUp` (`AtemiStrike.java:127-134`). When it ends, it kills the target if `tickTarget <= 0`, or `tickUp == 0`, or `tickUp >= tickTarget` (`AtemiStrike.java:116-124`). The victim is told "You feel your heart give out." (`AtemiStrike.java:121`).
- So the death comes at the end of the timer, unless the effect is removed during its first half. A cure that uses `delEffect` does not call `unInvoke` (`StdMOB.java:5275-5279`), so the restoration prayer heals the effect with no death (`Abilities/Prayers/Prayer_Restoration.java:211-216`).

**AxKick** (`Fighter_AxKick.java:36-145`). A kick that needs legs. Damage is `1d(level + 10)` bashing (`AxKick.java:128-129`) and is halved if the kick is blocked (`AxKick.java:134-135`). The proficiency adjustment is `2 × (caster DEX - target DEX)` (`AxKick.java:124`).

### 4. Movement, knockdown and reach

- **Charge** (`Fighter_Charge.java:35-242`). Range from 1 up to `5 + XLEVEL` (`Charge.java:98-107`). On arrival at melee, the caster gets a 2-tick buff (`Charge.java:219`):
  - attack `+ 2 × (level + XLEVEL)` (`Charge.java:152`);
  - damage `+ level + abilityCode() + XLEVEL` (`Charge.java:153`);
  - armour `+ 2 × (XLEVEL + level)` (`Charge.java:154`), a defensive cost.

  The buff ends at the next tick after the first weapon attack (`Charge.java:126-145`). If the caster was not already in combat, the charge makes its attack at once (`Charge.java:221-225`).
- **BullRush** (`Fighter_BullRush.java:35-170`). Trigger `BULLRUSH`. Needs a target and a direction, given as the last argument (`BullRush.java:109-127`). Both parties are pushed one room if the check passes (`BullRush.java:152-153`), with the target moving only if it can be heard moving (`BullRush.java:155-160`). The chance is `proficiencyCheck(-5 × levelgap)` (`BullRush.java:136-138`). A monster that moved away returns home after one mud hour (`BullRush.java:161-164`). The check is run before the move, so a failed push produces no failure message of its own (`BullRush.java:140-153`).
- **CircleTrip** (`Fighter_CircleTrip.java:35-263`). Trigger `CIRCLETRIP` or `CTRIP`. The target is knocked sitting for 2 ticks (`CircleTrip.java:251`). The chance adjustment is `-5 × levelgap - (35 + round((DEX - 9) × 3))` (`CircleTrip.java:230-236`). It is refused if the target is already on the floor, riding a mount the caster is not riding, or flying (`CircleTrip.java:212-228`). The sitting state is applied through `affectPhyStats` until the effect ends, and `doneTicking` lets the caster stand again (`CircleTrip.java:98-104`, `121-144`).
- **BodyFlip** (`Fighter_BodyFlip.java:35-240`). Trigger `BODYFLIP`. The same family as CircleTrip, with the chance adjustment `-5 × levelgap - round((STR - 9) × 3)` (`BodyFlip.java:215-221`) and a 2-tick knockdown (`BodyFlip.java:232`).
- **BodyToss** (`Fighter_BodyToss.java:35-176`, a `MonkSkill`). Needs a victim and no weapons (`BodyToss.java:112-122`). The target can weigh at most twice the caster (`BodyToss.java:138-142`). On success the two are separated by `2 + XLEVEL` ranges, capped by the room's maximum range (`BodyToss.java:158-162`), and the target takes 1d12 bashing damage (`BodyToss.java:163`).
- **Cartwheel** (`Fighter_Cartwheel.java:35-135`). Trigger `CARTWHEEL`. Needs combat, arms and legs, and room to back off. It moves the caster one step away and then up to two more, until the maximum range is reached (`Cartwheel.java:116-126`).

### 5. Reactive and passive skills

These are all `auto` abilities. They never fire on their own; they examine every message that reaches the caster.

- **Cleave** (`Fighter_Cleave.java:35-182`). In `okMessage`, if the caster's melee damage exceeds the victim's current HP, the weapon is a slashing non-natural one, the caster is awake, and the skill check passes, the code looks for a second inhabitant who is fighting the caster and is at melee range (`Cleave.java:153-174`). That second foe is attacked in the next tick (`Cleave.java:97-128`).
- **CounterAttack** (`Fighter_CounterAttack.java:35-123`). When the caster uses a parry (`Skill_Parry`), a trailing "position(s) for a counterattack" message is added (`CounterAttack.java:109-113`). When that trailer comes back through this effect with its own ID, the caster attacks the parried foe (`CounterAttack.java:114-119`). So a parry produces a counter as its trailer.
- **CatchProjectile** (`Fighter_CatchProjectile.java:35-163`). When a ranged or thrown weapon is aimed at the caster from range (`CatchProjectile.java:105-117`), with a free hand, the caster may catch it. The check is `proficiencyCheck(null, -85 + DEX + 3 × XLEVEL)` (`CatchProjectile.java:116`). A thrown weapon is taken and dropped (`CatchProjectile.java:119-126`). For ammunition, a fresh ammunition item is made and taken (`CatchProjectile.java:128-140`). The attack is vetoed. The caster can do it once per round (`CatchProjectile.java:106`, `147`, reset in `CatchProjectile.java:156-162`).
- **DeflectProjectile** (`Fighter_DeflectProjectile.java:35-149`). Almost the same, but it knocks the projectile away (a `GET` message) rather than catching it, and it needs an arm instead of a hand. The check is `-85 + DEX + 2 × XLEVEL` (`DeflectProjectile.java:117`). It yields to CatchProjectile (`DeflectProjectile.java:114`); CatchProjectile does not check for DeflectProjectile in return.
- **CircleParry** (`Fighter_CircleParry.java:35-137`, a `MonkSkill`). Parries a melee weapon attack when the caster has no weapon and a free hand. Thrown, ranged, flail and natural weapons are not parried (`CircleParry.java:111-119`). The check is `DEX - 90 + 2 × XLEVEL` (`CircleParry.java:122`). It cannot parry two attacks in a row: `lastTime` alternates the parries (`CircleParry.java:93`, `122-131`).
- **CoverDefence** (`Fighter_CoverDefence.java:35-124`). Ranged or thrown attacks from range, by someone whose victim is the caster, are turned into "take cover", with the same check `DEX - 90 + 2 × XLEVEL` (`CoverDefence.java:103-112`). The caster must be under 84 in height (`CoverDefence.java:107`).
- **CriticalShot** (`Fighter_CriticalShot.java:35-122`) and **CritStrike** (`Fighter_CritStrike.java:35-121`). When the caster's own damage hits its current victim, a random share of the damage is added, up to the caster's proficiency (`CriticalShot.java:115-116`, `CritStrike.java:114-116`). CriticalShot takes a ranged or thrown hit (`CriticalShot.java:109-111`) with the check `-75 + DEX + 2 × XLEVEL` (`CriticalShot.java:113`). CritStrike takes a melee hit that is not ranged or thrown (`CritStrike.java:109-111`) with `-90 + STR + 2 × XLEVEL` (`CritStrike.java:112`).
- **DesperateMoves** (`Fighter_DesperateMoves.java:35-112`). At 10% of maximum HP or less, incoming damage is divided by `1 + (proficiency + 10 × XLEVEL) / 100` and rounded up (`DesperateMoves.java:105-107`). The label appears only under 10% (`DesperateMoves.java:54`); see Problems.
- **BlindFighting** (`Fighter_BlindFighting.java:36-129`). If the victim cannot be seen but can be heard moving, the caster gets the `CAN_SEE_VICTIM` sense for the coming tick (`BlindFighting.java:119-126`, `104-105`). The skill can improve on a 10% roll (`BlindFighting.java:124-125`).
- **BackHand** (`Fighter_BackHand.java:36-167`, a `MonkSkill`). Needs two hands and no weapons (`BackHand.java:108-110`). The caster then attacks, with a `GenWeapon` called "a back hand", bone material, damage 5, bashing, and 1000 uses (`BackHand.java:127-137`). That attack is made against a foe who is attacking the caster (`BackHand.java:112-124`). Its damage is increased by `5 + XLEVEL` (`BackHand.java:160-164`).
- **CrescentKick** (`Fighter_CrescentKick.java:37-139`, a `StdAbility`). In melee with no weapon, each mob tick makes a kick attack on the victim with a new bashing `GenWeapon`, which is destroyed afterwards (`CrescentKick.java:109-121`). It has no leg check.
- **AweMounts** (`Fighter_AweMounts.java:38-132`). When a favoured mount (`isAMount`, `AweMounts.java:96-100`) would attack its rider or a member of the rider's group who is not in combat, the mount is overwhelmed if a d100 roll exceeds `10 × (mount level - (rider level + 2 × XLEVEL))` (`AweMounts.java:113-118`). The attack is vetoed with a message to the mount (`AweMounts.java:120-126`).

### 6. Toggled "auto" skills

`AutoBash` (`Fighter_AutoBash.java:36-189`), `AutoHammerRing` (`Fighter_AutoHammerRing.java:36-193`) and `AutoPistolwhip` (`Fighter_AutoPistolwhip.java:36-164`) share one shape:

- **Toggle.** Re-invoking the skill removes the effect and returns false, with no cost (`AutoBash.java:159-164`, `AutoHammerRing.java:155-160`, `AutoPistolwhip.java:135-140`). Otherwise the skill sets a long-lasting self effect (`beneficialAffect` then `makeLongLasting`, `AutoBash.java:173-176`).
- **Per-tick fire.** In melee, the effect invokes a second skill. `AutoBash` runs `Skill_Bash` once per worn shield per tick, with a `proficiencyCheck(null)` gate, and restores the usage counter afterwards (`AutoBash.java:116-133`). `AutoHammerRing` fires `Fighter_HammerRing` every third tick while wielding a hammer (`AutoHammerRing.java:101-119`). The counter is reset on success (`AutoHammerRing.java:112`).
- **Reaction.** `AutoPistolwhip` acts on a reload message. If a pistol with no ammunition is reloaded and the caster carries no matching ammunition, it vetoes the reload, pistol-whips the victim, and does it again with a chance equal to `XLEVEL` in percent (`AutoPistolwhip.java:109-125`). The pistol test is `Fighter_Pistolwhip.isPistolWeapon` (`Fighter_Pistolwhip.java:89-101`, outside).

### 7. Vanity, tweaking, planes and terrain

**ConfidentVanity** (`Fighter_ConfidentVanity.java:36-196`) and **ArmoredVanity** (`Fighter_ArmoredVanity.java:36-197`) reward showy kit. An armour item counts only if it is worn properly and its gold value is more than three times its computed base value (`ConfidentVanity.java:106-110`, `ArmoredVanity.java:106-111`). The count is capped at `1 + XLEVEL` items (`ConfidentVanity.java:102`, `110`).

- ConfidentVanity adds 0.1 per item to a multiplier on attack (`ConfidentVanity.java:111`, `128`).
- ArmoredVanity adds half the item's armour value per item to the armour bonus (`ArmoredVanity.java:112`), applied as `armor - bonus` (`ArmoredVanity.java:129`).
- Both are scaled by proficiency below 100 (`ConfidentVanity.java:113-116`, `ArmoredVanity.java:114-117`). They are active only in combat, while the victim can see the caster (`ConfidentVanity.java:139-166`). Their cache is cleared by a trailer runnable after wear, wield, hold or remove messages (`ConfidentVanity.java:178-195`, `run()` at `171-175`).

**ArmorTweaking** (`Fighter_ArmorTweaking.java:35-226`) raises an item's own armour value. The bonus is `round((0.10 + 0.10 × XLEVEL) × item armour)`, and a bonus under 1 is refused (`ArmorTweaking.java:183-188`). A second tweak must be larger than the first to count (`ArmorTweaking.java:189-193`). The bonus is stored as the effect's misc text and applied in `affectPhyStats` to the item, which the wearer then counts (`ArmorTweaking.java:132-139`). The effect ends when the item leaves the wearer (`ArmorTweaking.java:96-106`).

**The tweak compounds.** The bonus is computed from `armor.phyStats().armor()` (`ArmorTweaking.java:183`), which already includes the existing tweak. Each repeat is therefore a percentage of a larger number. By arithmetic alone, repeats converge to `factor × base / (1 - factor)`. At XLEVEL 0 that is about 1.11 times the base armour, and at XLEVEL 5 about 1.5 times the base. This is unverified at runtime.

**CosmicAdaptation** (`Fighter_CosmicAdaptation.java:36-226`, auto) adds the atmosphere of the plane the caster is in to its breathable set. Each tick a proficiency check decides whether it is active (`CosmicAdaptation.java:122-170`). The atmosphere comes from `StdPlanarAbility` (`CosmicAdaptation.java:172-195`, outside, not read).

**FieldTactics** (`Fighter_FieldTactics.java:35-231`, outside, base of the next three). It is an auto effect that applies while the caster is in a room whose domain is in `landClasses()` (`FieldTactics.java:93-98`, `196-202`). When it applies:

- attack is raised by `round((15 + XLEVEL) × proficiency / 100)` (`FieldTactics.java:222`);
- armour is lowered by the same amount (`FieldTactics.java:223`), which is a defensive gain;
- a damage bonus is applied to the caster's weapon hits (`FieldTactics.java:152-157`);
- sitting for two real minutes makes the caster hidden (`FieldTactics.java:113-119`, `218`).

The three children differ only in where they apply:

- `CaveTactics` uses `DOMAIN_INDOORS_CAVE` (`Fighter_CaveTactics.java:51-56`).
- `DesertTactics` uses `DOMAIN_OUTDOORS_DESERT` (`Fighter_DesertTactics.java:51-56`).
- `CaravanTactics` overrides `appliesHere()` and applies in an area that is a land or wagon boardable (`Fighter_CaravanTactics.java:57-72`).

**CaravanCommander** (`Fighter_CaravanCommander.java:36-216`, auto). When the caster enters or sits in a land or wagon boardable, the area itself gets a copy of the effect (`CaravanCommander.java:139-161`, `112-133`). The area then has the bonuses:

- Group members in combat get attack `+ 20 × proficiency` (`CaravanCommander.java:182`) and armour `+ ((level / 5) + XLEVEL) × proficiency` (`CaravanCommander.java:183-184`).
- Siege damage from a caravan weapon is multiplied (`CaravanCommander.java:202-209`). See Problems for the discontinuity at proficiency 100.

### 8. Mounts and companions

**CallSteed** (`Fighter_CallSteed.java:38-618`, a `StdAbility`, trigger `CALLSTEED`, grant Cavalier@3). It summons a steed and keeps track of it.

- The steed's race comes from a list of favoured mounts (`CoffeeUtensils.java:3459-3487`, with the mount skills in `CoffeeUtensils.java:3445` and after). Each entry is a "category,race" pair in the effect text (`Fighter_CallSteed.java:207-221`). The default is a horse (`CallSteed.java:205`).
- `invoke` with no arguments uses the default choice (`CallSteed.java:390-400`). With arguments it matches by substring (`CallSteed.java:451-478`). `LIST` and `DISMISS` are subcommands (`CallSteed.java:421-448`).
- The steed appears in a random open exit from the caster's room (`CallSteed.java:497-517`). The room must suit the mount's breathables. Indoor air rooms are refused, and outdoor-air rooms are refused for races that are not avian (`CallSteed.java:287-300`, `483-495`). It then walks toward the caster (`CallSteed.java:519`, `561`).
- The new steed is a `GenRideable` with weight 500, rider capacity 3, and level from the leveler (`CallSteed.java:581-617`). It is tagged `SYSTEM_SUMMONED` and `SUMMONED_BY:<caster>` (`CallSteed.java:608-609`). It carries a `Prop_ModExperience` of 0, so it gains no experience (`CallSteed.java:607`).
- Its state lasts across saves. `text()` writes the last steed as XML with a `LAST` marker (`CallSteed.java:308-338`), and `getNames()` restores it (`CallSteed.java:248-285`).
- It is dismissed when the caster quits or leaves the room, or when the caster rides another mob (`CallSteed.java:146-158`, `120-144`). A `Chant_AnimalCompanion` chant by the steed's follower reassigns it after 800 ms (`CallSteed.java:162-199`).

**CommandMount** (`Fighter_CommandMount.java:36-156`, a `StdAbility`, trigger `COMMANDMOUNT`, grant Cavalier@10). It orders a favoured animal (`CommandMount.java:109-116`). A monster's order with no words defaults to `FLEE` (`CommandMount.java:97-100`). A player must give words (`CommandMount.java:101-107`). Three messages are offered in turn: the spoken command, a mind message that is malicious unless the mount is a group member, and an order message (`CommandMount.java:125-133`). If none is vetoed and the malicious flag is zero, the mount performs the command at forced and ordered priority (`CommandMount.java:135-145`).

**CompanionMount** (`Fighter_CompanionMount.java:35-269`, auto, cannot be removed, grant Cavalier@19). The caster earns a loyal companion from a favoured animal.

- When the caster starts riding a favoured animal that has no `Loyalty` effect, the countdown is set (`CompanionMount.java:243-261`). The countdown is one in-game month of riding, that is, days in month × hours in day × ticks per mud hour, less `XTIME` hours (`CompanionMount.java:253-257`). It is at least 10 ticks.
- While riding that animal, each tick the countdown drops by one, but only when a proficiency check passes (`CompanionMount.java:122-124`). A failed check skips the tick.
- At zero, if the caster already has more than `XLEVEL` loyal followers, the caster is told it lacks the expertise (`CompanionMount.java:140-145`). The new animal is not yet counted, so by the code the cap is `XLEVEL + 1`.
- Otherwise the animal's personal effects are stripped and copied, and any charm effects are unInvoked and removed (`CompanionMount.java:146-178`). The animal is then set to follow the caster. A copy is kept in case the original is destroyed or dead (`CompanionMount.java:191-206`). The animal becomes a follower, and a `Loyalty` effect with `NAME=<caster>` is attached (`CompanionMount.java:225-234`). The caster is told "<target> is now a loyal companion to you."

The countdown is not saved. `CompanionMount` does not override `text()` (see Problems).

### 9. Morale and social

- **Berzerk** (`Fighter_Berzerk.java:36-187`, trigger `BERZERK`, grant Barbarian@8, Beastmaster@21, Fighter@22; the Wolverine race also grants it). It is refused when the caster is already in a Paladin chaos rage (`Berzerk.java:155`) and needs combat unless it is automatic (`Berzerk.java:161-164`). On success it adds `maxHP / 5` to the maximum and to the current HP (`Berzerk.java:177-180`). While active it raises damage and attack by `x / (6 - 0.2 × XLEVEL)` (`Berzerk.java:102-103`) and adds `20 + 2 × XLEVEL` to the armour stat (`Berzerk.java:104`). When it ends, it removes the HP bonus but never below 1 HP (`Berzerk.java:127-130`).
- **Battlecry** (`Fighter_Battlecry.java:36-162`, trigger `BATTLECRY`, grant Barbarian@6). Affects each target that `properTargets` returns (`Battlecry.java:131-153`; `properTargets` is in `StdAbility` and was not read). Each gets attack `+ 1 + round(attack / (6 - XLEVEL / 3))` (`Battlecry.java:99`). The effect lasts the standard duration, but ends at the sixth out-of-combat tick (`Battlecry.java:103-110`).
- **BloodBrother** (`Fighter_BloodBrother.java:36-157`, triggers `BLOODBROTHER` and `BROTHER`, grant Barbarian@8). Two players only (`BloodBrother.java:103-107`). The target must not be in combat unless the caller is automatic (`BloodBrother.java:93-97`). The target's session is asked for consent with a 5-second prompt (`BloodBrother.java:124-136`). On success both get a `BROTHER:<name>` tattoo and a line in their description (`BloodBrother.java:145-148`).

## Content worth mining

### Class grants

These are the skill grants of this bucket's abilities by class and level, as recorded in `CharClasses/*.java` through `CMLib.ableMapper().addCharAbilityMapping`. The levels are given as `class@level`. "auto" means the skill is granted with `isAutoInvoked` or an equivalent flag. Some grants reach past this bucket, and those are omitted.

| Ability | Granted to |
|---|---|
| CallSteed | Cavalier@3 |
| CommandMount | Cavalier@10 |
| CompanionMount | Cavalier@19 |
| CalledStrike | Fighter@24 |
| CalledShot | Cavalier@25, Ranger@25 |
| CaravanCommander | Cavalier@25 |
| CaravanTactics | Cavalier@21 |
| Charge | Barbarian@1 (also via racial lists for GiantBoar and Buffalo; the Play_Charge song) |
| ArmorTweaking | Fighter@2, Barbarian@9, Cavalier@4, Monk@5, Paladin@3, Ranger@4 |
| Cleave | Fighter@5, Barbarian@6, Beastmaster@6, Cavalier@10, Ranger@10, Paladin@15, Assassin@23 |
| ClinchHold | Fighter@13, Barbarian@13, Monk@13, Paladin@13, Ranger@13 |
| Breakout | Jester@10, Prancer@10, Sailor@10, Barbarian@14 |
| BullRush | Barbarian@23 |
| BearHug | Barbarian@4, Beastmaster@23 |
| Battlecry | Barbarian@6 |
| Berzerk | Barbarian@8, Beastmaster@21, Fighter@22 (also the Wolverine race) |
| BloodBrother | Barbarian@8 |
| BlindFighting | Fighter@10, Monk@11, Paladin@12, Beastmaster@15, Burglar@17, Jester@23, Prancer@23, SkyWatcher@20 (as `Fighter_Blindfighting`) |
| CriticalShot | Ranger@15, Fighter@16, Jester@14, Cavalier@15, Assassin@17 |
| CritStrike | Fighter@9, Jester@20, Assassin@11 |
| ChokeHold | Fighter@16, Assassin@16 |
| CoupDeGrace | Fighter@30 |
| CounterAttack | Fighter@24 |
| CoverDefence | Fighter@23, Artisan@1 (with Proficiency_Ranged 75) |
| DesperateMoves | Fighter@17 |
| AutoBash | Fighter@20 (with Skill_Bash) |
| Behead | Fighter@25 (secret), Gaoler@20 |
| ArmHold | Monk@9, Gaoler@14, Cavalier@14 |
| BodyToss | Monk@9 |
| BackHand | Monk@9 |
| BodyFlip | Monk@10, Prancer@10 |
| BodyShield | Monk@16 |
| CatchProjectile | Monk@12 |
| DeflectProjectile | Monk@7 |
| CircleParry | Monk@17 |
| CircleTrip | Monk@22, Prancer@25 |
| AnkleLock | Monk@23 |
| AtemiStrike | Monk@30 |
| AxKick | Monk@8 (with Fighter_Kick) |
| CosmicAdaptation | Monk@35 |
| Cartwheel | Monk@15, Jester@24, Prancer@19 |
| AutoHammerRing | Reliquist@22 (with Fighter_HammerRing) |
| AutoPistolwhip | Ranger@17 |
| ArmoredVanity | Cavalier@17 |
| ConfidentVanity | Cavalier@14 |
| AweMounts | Cavalier@4 |
| Bulldog | Archon@30 |
| CaveTactics | none found |
| CrescentKick | none found |

### Full catalogue

Every ability in the bucket. The trigger is the first word a player types, and `auto` means the skill has no trigger.

| ID (file) | Base | Quality | Trigger | Key numbers (file:line) |
|---|---|---|---|---|
| FighterSkill | StdAbility | none | none | Base for the family. `FighterSkill.java:53-62` |
| FighterGrappleSkill | FighterSkill | MALICIOUS | none | Hold base (see section 2) |
| Fighter_ClinchHold | FighterGrappleSkill | MALICIOUS | CLINCHHOLD | Base 4 ticks; NPC disarmed each tick (`ClinchHold.java:132-141`) |
| Fighter_ArmHold | FighterGrappleSkill | MALICIOUS | ARMHOLD | Speed halved or -1 (`ArmHold.java:71-74`) |
| Fighter_ChokeHold | FighterGrappleSkill | MALICIOUS | CHOKEHOLD | Cannot breathe, cannot speak (`ChokeHold.java:71-96`) |
| Fighter_BearHug | FighterGrappleSkill | MALICIOUS | BEARHUG | Hugger armour `- (level + 5 × XLEVEL)` (`BearHug.java:75`) |
| Fighter_AnkleLock | FighterGrappleSkill | MALICIOUS | ANKLELOCK | Foot break after 3 ticks, chance `10 × STR diff` (`AnkleLock.java:98-113`) |
| Fighter_Bulldog | FighterSkill | MALICIOUS | BULLDOG | Needs head hold; slam and knockdown (`Bulldog.java:135-181`) |
| Fighter_Breakout | FighterSkill | INDIFFERENT | BREAKOUT | STR and DEX `+ XLEVEL + 2 × STR` during struggle (`Breakout.java:108`) |
| Fighter_BodyShield | FighterSkill | OK_SELF | none | Redirects `prof%` of incoming damage to held victim (`BodyShield.java:122`) |
| Fighter_CalledStrike | FighterSkill | MALICIOUS | CALLEDSTRIKE | Sever threshold `HP / (9 + XLEVEL)`; head uses 3 (`CalledStrike.java:136-141`, `254-255`) |
| Fighter_CalledShot | Fighter_CalledStrike | MALICIOUS | CALLEDSHOT | Ranged or thrown, not adjacent (`CalledShot.java:59-82`) |
| Fighter_Behead | FighterSkill | MALICIOUS | BEHEAD | Needs warrant or ABOVELAW; target HP to 1 (`Behead.java:139-149`, `194`) |
| Fighter_CoupDeGrace | FighterSkill | MALICIOUS | COUP, COUPDEGRACE | Target sleeping; HP under 50 or under max/4; 150 movement (`CoupDeGrace.java:164-188`) |
| Fighter_AtemiStrike | MonkSkill | MALICIOUS | ATEMI | Delayed lethal effect, `20 - XLEVEL/2` ticks (`AtemiStrike.java:215-218`) |
| Fighter_AxKick | FighterSkill | MALICIOUS | AXKICK | `1d(level+10)` bashing, halved on block (`AxKick.java:128-135`) |
| Fighter_Charge | FighterSkill | MALICIOUS | CHARGE | Range 1 to `5 + XLEVEL`; buff `2(level+XLEVEL)` attack (`Charge.java:98-107`, `152`) |
| Fighter_BullRush | FighterSkill | INDIFFERENT | BULLRUSH | Pushes both one room; chance `-5 × gap` (`BullRush.java:136-153`) |
| Fighter_CircleTrip | FighterSkill | MALICIOUS | CIRCLETRIP, CTRIP | 2 ticks sitting; chance `-5 × gap - (35 + 3(DEX-9))` (`CircleTrip.java:230-251`) |
| Fighter_BodyFlip | FighterSkill | MALICIOUS | BODYFLIP | 2 ticks sitting; chance `-5 × gap - 3(STR-9)` (`BodyFlip.java:215-232`) |
| Fighter_BodyToss | MonkSkill | MALICIOUS | BODYTOSS | Range `2 + XLEVEL`; 1d12 bashing; target at most 2× caster weight (`BodyToss.java:138-163`) |
| Fighter_Cartwheel | FighterSkill | OK_SELF | CARTWHEEL | Retreats one step plus up to two (`Cartwheel.java:116-126`) |
| Fighter_Cleave | FighterSkill | BENEFICIAL_SELF | none | Second strike on adjacent foe after a killing blow (`Cleave.java:153-174`) |
| Fighter_CounterAttack | FighterSkill | BENEFICIAL_SELF | none | Parry adds a trailing counter (`CounterAttack.java:109-119`) |
| Fighter_CatchProjectile | FighterSkill | OK_SELF | none | Chance `-85 + DEX + 3 × XLEVEL` (`CatchProjectile.java:116`) |
| Fighter_DeflectProjectile | FighterSkill | OK_SELF | none | Chance `-85 + DEX + 2 × XLEVEL` (`DeflectProjectile.java:117`) |
| Fighter_CircleParry | MonkSkill | BENEFICIAL_SELF | none | Chance `DEX - 90 + 2 × XLEVEL`; parries alternate (`CircleParry.java:122-131`) |
| Fighter_CoverDefence | FighterSkill | BENEFICIAL_SELF | none | Ranged attacks aimed at you; chance `DEX - 90 + 2 × XLEVEL` (`CoverDefence.java:111`) |
| Fighter_CriticalShot | FighterSkill | BENEFICIAL_SELF | none | Ranged or thrown bonus up to `prof%`; chance `-75 + DEX + 2 × XLEVEL` (`CriticalShot.java:113-116`) |
| Fighter_CritStrike | FighterSkill | BENEFICIAL_SELF | none | Melee bonus up to `prof%`; chance `-90 + STR + 2 × XLEVEL` (`CritStrike.java:112-116`) |
| Fighter_DesperateMoves | FighterSkill | BENEFICIAL_SELF | none | Damage `÷ (1 + (prof + 10 × XLEVEL) / 100)` below 10% HP (`DesperateMoves.java:105-107`) |
| Fighter_BlindFighting | FighterSkill | BENEFICIAL_SELF | none | Sees an unseen but audible victim (`BlindFighting.java:119-126`) |
| Fighter_BackHand | MonkSkill | MALICIOUS | none | Extra attack, damage `5 + XLEVEL` (`BackHand.java:127-137`, `164`) |
| Fighter_CrescentKick | StdAbility | MALICIOUS | none | Kick each melee mob tick with no weapon (`CrescentKick.java:109-121`) |
| Fighter_AweMounts | FighterSkill | BENEFICIAL_SELF | none | Mount refuses to attack its rider when a d100 exceeds `10 × (mount level - (rider + 2 × XLEVEL))` (`AweMounts.java:113-118`) |
| Fighter_AutoBash | FighterSkill | BENEFICIAL_SELF | AUTOBASH | Toggle; one `Skill_Bash` per shield per tick (`AutoBash.java:116-133`) |
| Fighter_AutoHammerRing | FighterSkill | BENEFICIAL_SELF | AUTOHAMMERRING | Toggle; `Fighter_HammerRing` every third tick (`AutoHammerRing.java:101-119`) |
| Fighter_AutoPistolwhip | FighterSkill | BENEFICIAL_SELF | AUTOPISTOLWHIP | Toggle; pistol-whip instead of an empty reload (`AutoPistolwhip.java:109-125`) |
| Fighter_ConfidentVanity | FighterSkill | BENEFICIAL_SELF | none | +0.1 attack multiplier per overpriced item (`ConfidentVanity.java:111`) |
| Fighter_ArmoredVanity | FighterSkill | BENEFICIAL_SELF | none | Armour `- sum(item armour / 2)` (`ArmoredVanity.java:112`, `129`) |
| Fighter_ArmorTweaking | FighterSkill | BENEFICIAL_OTHERS | ARMORTWEAK, TWEAK | Bonus `(0.10 + 0.10 × XLEVEL) × item armour` (`ArmorTweaking.java:183`) |
| Fighter_CosmicAdaptation | FighterSkill | BENEFICIAL_SELF | none | Plane atmosphere added to breathables (`CosmicAdaptation.java:172-195`) |
| Fighter_CaravanCommander | FighterSkill | BENEFICIAL_SELF | none | Caravan area bonuses (`CaravanCommander.java:182-209`) |
| Fighter_CaravanTactics | Fighter_FieldTactics | BENEFICIAL_SELF | none | Applies in land or wagon areas (`CaravanTactics.java:57-72`) |
| Fighter_CaveTactics | Fighter_FieldTactics | BENEFICIAL_SELF | none | Indoor cave domain (`CaveTactics.java:51-56`) |
| Fighter_DesertTactics | Fighter_FieldTactics | BENEFICIAL_SELF | none | Outdoor desert domain (`DesertTactics.java:51-56`) |
| Fighter_CallSteed | StdAbility | OK_SELF | CALLSTEED | Summons a steed one room away (`CallSteed.java:497-561`) |
| Fighter_CommandMount | StdAbility | INDIFFERENT | COMMANDMOUNT | Orders a favoured mount (`CommandMount.java:125-145`) |
| Fighter_CompanionMount | FighterSkill | INDIFFERENT | none | One in-game month riding makes a companion (`CompanionMount.java:253-257`) |
| Fighter_Berzerk | FighterSkill | BENEFICIAL_SELF | BERZERK | HP `+ maxHP/5`; armour `+ 20 + 2 × XLEVEL` (`Berzerk.java:102-104`, `177`) |
| Fighter_Battlecry | FighterSkill | BENEFICIAL_OTHERS | BATTLECRY | Attack `+ 1 + attack / (6 - XLEVEL/3)`; ends at the sixth out-of-combat tick (`Battlecry.java:99`, `109-110`) |
| Fighter_BloodBrother | FighterSkill | INDIFFERENT | BLOODBROTHER, BROTHER | Two players, 5-second consent (`BloodBrother.java:124-136`) |
| FighterGrappleSkill | FighterSkill | MALICIOUS | none | Hold base; see section 2 |

### Messages

Paraphrased; short examples only.

- Hold start: "You are holding a X!" and "You are in a X!" (`FighterGrappleSkill.java:266-282`). The X is the skill's name.
- Hold end: the holder "releases" the target, or the target "breaks out" (`FighterGrappleSkill.java:382-405`).
- Atemi's delayed death: "You feel your heart give out." (`Fighter_AtemiStrike.java:121`).
- Berzerk end: "You feel calmer." (`Fighter_Berzerk.java:131`).
- Battlecry end: "You calm down a bit." (`Fighter_Battlecry.java:125`).
- Companion countdown: "You lack the expertise to gain another companion." (`Fighter_CompanionMount.java:142`).
- Companion gained: "<target> is now a loyal companion to you." (`Fighter_CompanionMount.java:232`).
- Cover and parry messages: "take cover" (`Fighter_CoverDefence.java:114`), "circle-parries" (`Fighter_CircleParry.java:121`).
- Called strike parts list: "Parts include ..." (`Fighter_CalledStrike.java:242-245`).

## Notable design ideas

- **A contest of opposed stats, resolved one tick at a time.** The grapple escape is `min(DEX, STR) + XLEVEL + 2 × attempts` against `max(DEX, STR) + XLEVEL` (`FighterGrappleSkill.java:103-121`). The held party's chance rises each round, and the holder's upkeep is a cost in mana, moves and hit points each round. A struggle is an ordinary action that boosts the struggler's stats (`Fighter_Breakout.java:108`).
- **Two linked effects make a hold.** Each side carries its own effect pointing at the other (`FighterGrappleSkill.java:332-357`). Either side can end the hold. The other side notices on its next tick, when `pairedWith.fetchEffect(ID())` is null (`FighterGrappleSkill.java:133-139`). That keeps the state symmetric without a central object.
- **Passives as message filters.** Parry, catch, deflect, cover and body shield all sit in `okMessage` and either veto the attack or add a trailing message (`Fighter_CounterAttack.java:109-113`, `Fighter_BodyShield.java:124-126`). The parry-then-counter is a neat use of trailers.
- **Called shots with thresholds.** Severing a limb needs a single blow of at least `HP / (9 + XLEVEL)`, which makes the skill rarely useful against healthy foes and very useful against wounded ones (`Fighter_CalledStrike.java:136-141`).
- **A delayed kill as a status effect.** Atemi strikes a timer and kills at its end unless cured early. A cure through `delEffect` does not kill (`StdMOB.java:5275-5279`). This is a good dramatic device.
- **Terrain as a passive filter.** Tactics apply by domain of the room (`Fighter_FieldTactics.java:93-98`). That is a simple way to make skills depend on where the character stands.
- **Showy kit as a combat bonus.** Items that cost more than three times their base value count as vanity items (`Fighter_ConfidentVanity.java:109-110`). The game economy and combat stats are linked by one comparison.
- **A companion earned by riding.** One in-game month of riding a favoured animal turns it into a permanent `Loyalty` companion (`Fighter_CompanionMount.java:253-257`, `225-234`). Capacity grows with rank (`Fighter_CompanionMount.java:140`).

## Problems and cruft

Items are listed roughly in order of how much they matter. Each is from reading the code, and none was run.

1. **Dead stand-up check in ChokeHold** (`Fighter_ChokeHold.java:108-112`). The condition is `!isStanding(mob) && isStanding(mob)`, so the "You need to stand up first!" branch can never run.
2. **CoupDeGrace automatic path always fails** (`Fighter_CoupDeGrace.java:143-152`, `176-177`). `ww` is set only when the call is not automatic. The check `ww == null` then returns false after `super.invoke` has already run, so any cost has been paid.
3. **CoupDeGrace reports success without effect** (`Fighter_CoupDeGrace.java:187-208`). If the proficiency check passes but the target has too much HP, the else branch shows the failure message and then returns `success`, which is true.
4. **CoupDeGrace message and check disagree** (`Fighter_CoupDeGrace.java:164-171`). The check uses `isSleeping`, while the text says "not prone".
5. **Behead computes a value it never uses** (`Fighter_Behead.java:181-185`). `levelDiff` is tripled and then dropped.
6. **Blood-brother check reads a tattoo the skill never writes** (`Fighter_BloodBrother.java:108-111`, `145-146`). It looks for `BLOODBROTHER:`, but writes `BROTHER:`. `findTattoo` matches exactly unless the name ends in `*` (`MOBS/StdMOB.java:5757-5764`), so the "already has a blood brother" test cannot fire for this skill's tattoos. `Libraries/Clans.java:476` lists both prefixes. The skill also appends to the descriptions every time it runs (`BloodBrother.java:147-148`).
7. **Battle cry timer is cumulative, not consecutive** (`Fighter_Battlecry.java:109-110`). The counter is only reset when the skill is cast (`Battlecry.java:151`), so separate out-of-combat spells add up over a long fight.
8. **Caravan group bonus has the armour sign reversed.** `Fighter_CaravanCommander.java:183-184` adds to the armour stat of group members in combat. By the rule in section 0, a higher stat is worse defence. This looks like a sign error, not a design choice.
9. **Caravan siege multiplier is discontinuous.** Below 100 proficiency the multiplier is `1 + (0.2 + 0.02 × XLEVEL) + prof/100` (`Fighter_CaravanCommander.java:205-209`), about 2.19 at XLEVEL 0 and 99%. At 100 it drops to `1.2 + 0.02 × XLEVEL` (`Fighter_CaravanCommander.java:202-203`). The fraction was probably meant to be multiplied, not added.
10. **FieldTactics divides by zero at XLEVEL 20.** The damage term is `prof / (400 - 20 × XLEVEL)` (`Fighter_FieldTactics.java:156`). The denominator is zero at XLEVEL 20 and negative above it. The effect of that on the damage is unverified, but the arithmetic is clearly wrong.
11. **Uneven use of trigger checks in DesperateMoves.** The label uses `<` (`Fighter_DesperateMoves.java:54`), but the damage reduction uses `<=` (`Fighter_DesperateMoves.java:105`). At exactly 10% the reduction applies but the label does not show.
12. **Math.random in two skills** (`Fighter_CriticalShot.java:115`, `Fighter_CritStrike.java:114`). The rest of the bucket uses `CMLib.dice()`, so these cannot be controlled the same way.
13. **Asymmetric exclusion between the two projectile skills.** DeflectProjectile checks for CatchProjectile (`Fighter_DeflectProjectile.java:114`). CatchProjectile does not check for DeflectProjectile (`Fighter_CatchProjectile.java:105-117`). Which fires first depends on listener order.
14. **No-op victim resets.** `if (x.getVictim()==null) x.setVictim(null)` appears in Charge and BodyToss with the comment "correct range" (`Fighter_Charge.java:228-231`, `Fighter_BodyToss.java:164-167`). `setVictim(null)` returns at once if the victim is already null (`MOBS/StdMOB.java:1630-1642`), so these lines do nothing.
15. **Copy-paste disposition in BodyFlip and Bulldog** (`Fighter_BodyFlip.java:180-181`, `Fighter_Bulldog.java:180-181`). The attacker's disposition is set from the target's, which puts the attacker on the floor too. That may be intended, but the code does not say so.
16. **Atemi's zero-tick case** (`Fighter_AtemiStrike.java:119`). A removal before the first tick counts as a death (`tickUp == 0`). Whether this is intended is unclear. The duration also goes to zero or below at XLEVEL 40, where the standard duration takes over (`StdAbility.java:2176-2177`).
17. **CompanionMount countdown is not saved.** It overrides no `text()`, so `mountTicks` is lost on a reload. The Loyalty effect is saved separately. The countdown also returns false on a failed check, which `StdMOB` turns into `unInvoke` (`MOBS/StdMOB.java:4326-4328`). Because the effect cannot be removed, that only sets a flag, so the countdown survives.
18. **Silent empty catch.** `Fighter_CompanionMount.java:187-189` swallows any throwable while it copies the animal's personal effects.
19. **Dead or unused code.** `FighterGrappleSkill.isMonkish()` returns false before commented-out code (`FighterGrappleSkill.java:186-201`). `CoverDefence.hits` is never read (`Fighter_CoverDefence.java:37`). `ArmoredVanity` recalculates whenever its cache is 0 or below (`ArmoredVanity.java:126-127`), while `ConfidentVanity` recalculates only when it is below 0 (`ConfidentVanity.java:125-127`).
20. **Method names misleading.** `CoupDeGrace.overrideMana()` returns 150, a movement cost (`Fighter_CoupDeGrace.java:94-98`).
21. **Side effects inside `affectPhyStats`.** `FieldTactics` sets `activated` and `hidden` during the stat recalculation (`Fighter_FieldTactics.java:216-229`). That state can drift if the stat is recalculated without the effect being present.
22. **Multiplayer assumptions.** BloodBrother needs two sessions and a consent prompt (`BloodBrother.java:124-136`). CaravanCommander and the caravan tactics assume a boardable land or wagon area, a multi-rider concept (`CaravanCommander.java:112-122`). Battlecry and the group part of CaravanCommander assume a party.
23. **Grant name mismatch.** SkyWatcher grants `Fighter_Blindfighting` (`CharClasses/SkyWatcher.java:237`), while the class is `Fighter_BlindFighting`. It is not verified whether lookup is case-insensitive.

## Takeaways for a Python SUD

**Borrow: one skill-check function.** The whole bucket runs on the rule in section 0. A Python version can be small, and it keeps the negative-modifier pre-gate, which makes penalties and bonuses behave differently.

```python
import random

def skill_check(prof: int, rank: int = 0, mod: int = 0, rng=random) -> bool:
    """Mirror of StdAbility.proficiencyCheck (StdAbility.java:1441-1485)."""
    chance = min(95, max(5, prof + 2 * rank)) + rank // 2
    if mod >= 0:
        chance += mod
    elif rng.randint(1, 100) > 100 + mod:   # negative mod is a pre-gate
        return False
    return rng.randint(1, 100) < chance
```

**Borrow: a hold is a pair of linked objects with an escape meter.** The grapple's escape contest maps directly onto turn-based play. A held character's escape score rises by 2 each attempt, and a hold costs stamina each turn.

```python
from dataclasses import dataclass

@dataclass
class Hold:
    holder: "Fighter"
    held: "Fighter"
    kind: str                  # "clinch", "arm", "choke", ...
    escape: int = 0            # CoffeeMud tickUp

    def held_struggles(self) -> bool:
        """True if the held character breaks free (FighterGrappleSkill.java:103-121)."""
        self.escape += 1
        held_best = min(self.held.dex, self.held.str) + self.held.rank + 2 * self.escape
        holder_best = max(self.holder.dex, self.holder.str) + self.holder.rank
        return held_best >= holder_best
```

**Borrow: passives as hooks, not separate loops.** In a single-player game, reactive skills (parry, catch, deflect, cover, body shield) can be one list of hooks on the attack pipeline: `on_incoming_attack(ctx) -> ctx`, where a hook can cancel the attack or append follow-up events. The parry-then-counter pattern (`Fighter_CounterAttack.java:109-119`) is the model to copy.

**Borrow: a stance system instead of auto-toggles.** AutoBash, AutoHammerRing, AutoPistolwhip and CrescentKick are the same idea with different triggers. One `stance` set on the player, checked by the attack hook, replaces four skill classes.

**Borrow: called strikes as a command.** `aim arm` can compute `threshold = target.hp // (9 + rank)` and sever on a hit at or above it. `aim head` uses `// (3 + rank)`. This gives a cheap, readable wound mechanic.

**Borrow: delayed lethal effect as a scheduled event.** Store `death_turn` on the target, and let a cure cancel the event without triggering death (the `delEffect` behaviour, `StdMOB.java:5275-5279`). Decide explicitly whether a cure in the first half should prevent death; CoffeeMud's `tickUp == 0` case is an accident.

**Borrow: companions from riding.** Track `ride_turns` for a favoured animal and convert it to a companion after one in-game month, with a capacity of `rank` companions. The copy-and-restore of charm effects is a model for handling creatures that change owner.

**Borrow: terrain-keyed passives.** A table `{"cave": {...}, "desert": {...}}` on the player or on the room is simpler than a subclass per terrain.

**Borrow: class grants as data.** The table in "Content worth mining" maps straight to a dictionary keyed by class and level, for example `{"Cavalier": {3: ["call_steed"], 10: ["command_mount"], 19: ["companion_mount"]}}`.

**Simplify.** Armour in a Python version should be a positive protection value, with higher meaning better. That avoids the sign confusion behind the CaravanCommander bug above. Also drop the hit-roll arithmetic in favour of one clear formula.

**Drop or redesign for single player.** BloodBrother needs a second human and a consent prompt; drop it or make it a bond with an NPC. CaravanCommander and the caravan tactics need other riders; reimagine them as a party vehicle with NPC crew. Battlecry and the group bonuses become buffs on companions rather than on other players. Behead's legal check depends on a law system; a Python SUD can use a simple "wanted" flag instead.

## Coverage

- **Read in full (bodies, from the class declaration down):** all 52 files of the bucket. Each file's package line, import block and licence header (roughly lines 1 to 34) were not read, because they are boilerplate. The files read from line 1 were `FighterSkill.java`, `FighterGrappleSkill.java`, `Fighter_CallSteed.java`, `Fighter_CalledStrike.java`, `Fighter_ClinchHold.java`, `Fighter_CircleTrip.java` and `Fighter_Behead.java`.
- **Skimmed:** none.
- **Skipped:** none within the bucket.
- **Outside the bucket, read only for the interaction described:** `Fighter_FieldTactics.java` (full), `MonkSkill.java` (full), and `Fighter_Pistolwhip.java`, `Abilities/StdAbility.java`, `MOBS/StdMOB.java`, `Libraries/MUDFight.java`, `Items/Armor/StdArmor.java`, `core/CMProps.java`, `Libraries/CoffeeUtensils.java`, `Libraries/Clans.java`, `Abilities/Prayers/Prayer_Restoration.java`, `Abilities/Songs/Play_Charge.java`, `Races/Wolverine.java`, `Races/GiantBoar.java`, `Races/Buffalo.java` and `CharClasses/*.java` (by grep only). `Misc/Amputation.java`, `Spells/Spell_Choke.java`, `Thief/Thief_Garrote.java` and `Druid/Chant_Drown.java` were located, not read.
- **Not verified at runtime.** Every claim here comes from reading the code. Numbers computed by hand (for example the tweak convergence and the siege discontinuity) are marked as arithmetic.
