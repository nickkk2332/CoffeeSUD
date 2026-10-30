# Abilities: Fighter skill family (part 2 of 3)

> Part of the CoffeeMud NEWOVERVIEW. Bucket: `b060` (Abilities/Fighter [part 2/3: Fighter_DismountingBlow .. Fighter_PressurePoints]) · 56 files · 9449 lines of Java.

## Summary

This bucket holds 56 combat and field skills from the `Fighter` family: mounted combat and favoured mounts, terrain and plane "tactics", grapples and their finishing strikes, unarmed monk-style strikes, weapon tricks with hammers, polearms and crossbows, and passive parries, blocks and saves. Almost every skill is a small `FighterSkill` subclass that reacts to combat messages (`okMessage`, `executeMsg`) or to ticks, and most use one shared pattern: roll a proficiency check, send a message, and if it lands, attach a timed status effect (dazed, blinded, pinned, dismounted) to the target. Two base classes carry most of the shared logic: `FighterGrappleSkill` (a linked pair of effects for holds) and `StdAbility.proficiencyCheck`, which turns skill level, expertise and a stat-based adjustment into a success roll. The design is mostly transferable to a single-player game because the mechanics are one-on-one, but a few skills (Intimidate, Lucky Vanity, Faithful Mount, Mounted Retreat) assume other people or groups are present.

## Key files and types

Paths are relative to the CoffeeMUD folder. The classes in this bucket live in `com/planet_ink/coffee_mud/Abilities/Fighter/`, one class per file. In the catalogue below, a name such as `Fighter_Knee` is the class ID, and its file is that ID with `.java`. Base classes outside this bucket are listed because the bucket depends on them.

| File or class | Role | Lines |
|---|---|---|
| `com/planet_ink/coffee_mud/Abilities/Fighter/FighterSkill.java` (outside bucket) | Base of every fighter skill: `ACODE_SKILL`, `USAGE_MOVEMENT`, `CAN_MOBS` targeting; finds an active grapple | 100 |
| `com/planet_ink/coffee_mud/Abilities/Fighter/FighterGrappleSkill.java` (outside bucket) | Base of all holds: paired effects, struggle counter, break-free contest, release, hold upkeep | 502 |
| `com/planet_ink/coffee_mud/Abilities/Fighter/MonkSkill.java` (outside bucket) | Base of unarmed "monk" skills; `anyWeapons()` tests wielded or held item | 57 |
| `com/planet_ink/coffee_mud/Abilities/Fighter/Fighter_FieldTactics.java` | Base of the terrain tactics: attack and armour bonus, damage bonus, hiding after sitting | 231 |
| `com/planet_ink/coffee_mud/Abilities/Fighter/Fighter_FavoredMount.java` | Chooses a favoured mount race for the rider; dismount speed burst; item hand-off to the mount | 349 |
| `com/planet_ink/coffee_mud/Abilities/Fighter/Fighter_PlanarVeteran.java` | Remembers planes the character has visited (timed) and grants stat bonuses by plane category | 346 |
| `com/planet_ink/coffee_mud/Abilities/Fighter/Fighter_PlanarMount.java` | Breaks a favoured animal in a plane and turns it into a permanent rideable | 316 |
| `com/planet_ink/coffee_mud/Abilities/Fighter/Fighter_Jousting.java` | Mounted charge: triple, then double, damage on the first hits; chains into Ridethrough | 296 |
| `com/planet_ink/coffee_mud/Abilities/Fighter/Fighter_DismountingBlow.java` | Knocks a rider off with a polearm; defines `getPolearm()`; leaves the target dazed on the ground | 287 |
| `com/planet_ink/coffee_mud/Abilities/Fighter/Fighter_HammerRing.java` | Hammer blow to the head that dazes the target (`HealthCondition`) | 279 |
| `com/planet_ink/coffee_mud/Abilities/Fighter/Fighter_MonkeyGrip.java` | One-hands a two-handed weapon by flipping its worn-location flag, at a damage penalty | 279 |
| `com/planet_ink/coffee_mud/Abilities/Fighter/Fighter_LightningStrike.java` | Unarmed flurry, then the attacker collapses from exhaustion | 243 |
| `com/planet_ink/coffee_mud/Abilities/Fighter/Fighter_Gouge.java` | Blinds a target and leaves an eye injury | 242 |
| `com/planet_ink/coffee_mud/Abilities/Fighter/Fighter_FingerLock.java` | Finger hold; breaks the victim's finger after repeated struggles | 239 |
| `com/planet_ink/coffee_mud/Abilities/Fighter/Fighter_ElbowLock.java` | Arm hold; breaks an arm after more struggles | 235 |
| `com/planet_ink/coffee_mud/Abilities/Fighter/Fighter_HoldTheLine.java` | Polearm stance that stops enemies closing and limits the holder to one weapon attack per tick | 228 |
| `com/planet_ink/coffee_mud/Abilities/Fighter/Fighter_LuckyVanity.java` | Passive saving-throw bonus from over-priced armour, only when others are in the room | 218 |
| `com/planet_ink/coffee_mud/Abilities/Fighter/Fighter_FaithfulMount.java` | On the rider's death, the favoured mount carries the corpse back after a delay | 213 |
| `com/planet_ink/coffee_mud/Abilities/Fighter/Fighter_Knee.java`, `com/planet_ink/coffee_mud/Abilities/Fighter/Fighter_Gutbuster.java`, `com/planet_ink/coffee_mud/Abilities/Fighter/Fighter_Earbox.java`, `com/planet_ink/coffee_mud/Abilities/Fighter/Fighter_ElbowJab.java` | Grapple finishers: a natural-weapon strike with a critical-damage bonus and a follow-up effect | 207, 193, 195, 186 |
| `com/planet_ink/coffee_mud/Abilities/Fighter/Fighter_MountedRetreat.java` | Flees from combat while mounted; success depends on mounted and reach-armed enemies | 195 |
| `com/planet_ink/coffee_mud/Abilities/Fighter/Fighter_Intimidate.java` | Passive: stops weaker hostile characters from approaching while the holder is not fighting | 138 |
| `com/planet_ink/coffee_mud/Abilities/Fighter/Fighter_DualParry.java`, `com/planet_ink/coffee_mud/Abilities/Fighter/Fighter_ForearmBlock.java`, `com/planet_ink/coffee_mud/Abilities/Fighter/Fighter_ImprovedShieldDefence.java` | Passive melee defences | 143, 134, 153 |
| `com/planet_ink/coffee_mud/Abilities/Fighter/Fighter_MonkeyPunch.java`, `com/planet_ink/coffee_mud/Abilities/Fighter/Fighter_KnifeHand.java` | Automatic unarmed attacks each tick while in melee | 147, 146 |
| `com/planet_ink/coffee_mud/Abilities/Fighter/Fighter_FlyingKick.java`, `com/planet_ink/coffee_mud/Abilities/Fighter/Fighter_Kick.java`, `com/planet_ink/coffee_mud/Abilities/Fighter/Fighter_KiStrike.java` | Kick and "ki" strike | 162, 145, 146 |
| `com/planet_ink/coffee_mud/Abilities/Fighter/Fighter_FastSlinging.java`, `com/planet_ink/coffee_mud/Abilities/Fighter/Fighter_FarShot.java`, `com/planet_ink/coffee_mud/Abilities/Fighter/Fighter_PointBlank.java`, `com/planet_ink/coffee_mud/Abilities/Fighter/Fighter_ImprovedThrowing.java`, `com/planet_ink/coffee_mud/Abilities/Fighter/Fighter_Fragmentation.java` | Ranged and thrown weapon modifiers | 121, 217, 217, 123, 132 |
| `com/planet_ink/coffee_mud/Abilities/Fighter/Fighter_Endurance.java`, `com/planet_ink/coffee_mud/Abilities/Fighter/Fighter_Hardiness.java`, `com/planet_ink/coffee_mud/Abilities/Fighter/Fighter_Heroism.java`, `com/planet_ink/coffee_mud/Abilities/Fighter/Fighter_PressurePoints.java` | Recovery, movement pool, justice save, and unarmed damage | 121, 119, 136, 121 |
| `com/planet_ink/coffee_mud/Abilities/Fighter/Fighter_Pin.java`, `com/planet_ink/coffee_mud/Abilities/Fighter/Fighter_LegHold.java`, `com/planet_ink/coffee_mud/Abilities/Fighter/Fighter_DoubleArmHold.java`, `com/planet_ink/coffee_mud/Abilities/Fighter/Fighter_Headlock.java` | Remaining grapple variants | 152, 159, 120, 114 |
| `com/planet_ink/coffee_mud/Abilities/Fighter/Fighter_Pistolwhip.java`, `com/planet_ink/coffee_mud/Abilities/Fighter/Fighter_Forceback.java` | Crossbow butt strike; polearm push-back | 172, 171 |
| `com/planet_ink/coffee_mud/Abilities/Fighter/Fighter_GracefulDismount.java`, `com/planet_ink/coffee_mud/Abilities/Fighter/Fighter_FierceMount.java` | Clears forced-sitting after a dismount; mount combat buff | 198, 165 |
| `com/planet_ink/coffee_mud/Abilities/Fighter/Fighter_MountedTactics.java`, `com/planet_ink/coffee_mud/Abilities/Fighter/Fighter_PlanarTactics.java` and the five terrain tactics | Terrain or plane variants of the tactics base | 75, 81, 57 each |
| `com/planet_ink/coffee_mud/Abilities/Fighter/Fighter_FavoredMount2.java` to `com/planet_ink/coffee_mud/Abilities/Fighter/Fighter_FavoredMount4.java` | Extra favoured-mount slots (subclasses of `Fighter_FavoredMount`) | 53 each |
| `com/planet_ink/coffee_mud/Libraries/CoffeeUtensils.java` (outside bucket) | `getFavoredMounts()` reads the favoured-mount effects and defaults to Horse | see How it works |
| `resources/skills/racialmounts.txt` (outside bucket) | Race to mount table used by `Fighter_RacialMount` and the favoured-mount picker | data |

## How it works

### Shared rule: the proficiency check

Almost every skill in the bucket starts with `proficiencyCheck(mob, adjustment, auto)`. The code in `com/planet_ink/coffee_mud/Abilities/StdAbility.java:1441-1485` works like this:

- The base chance is the skill's proficiency, plus `2 * xlevel` (expertise), clamped to 5 to 95 (unless the `DIS955RULE` flag is set), plus `xlevel / 2` (integer division). Admin superskill short-circuits to success at line 1455.
- If `adjustment >= 0`, it is added to the chance and the roll is `d100 < chance`.
- If `adjustment < 0`, it is **not** subtracted from the chance. It works as a separate gate: the check fails outright when `d100 > 100 + adjustment`, and otherwise the second roll `d100 < chance` decides. A negative adjustment of -40 therefore leaves a 60% gate and then the normal roll.
- `auto` returns success, and sets proficiency to 100 when the mob does not own the ability instance (`com/planet_ink/coffee_mud/Abilities/StdAbility.java:1443-1448`).

Learning is by use: most skills call `helpProficiency()` on a percentile roll with different thresholds, for example 20% in `com/planet_ink/coffee_mud/Abilities/Fighter/Fighter_HoldTheLine.java:109-110`, 1% in `com/planet_ink/coffee_mud/Abilities/Fighter/Fighter_LuckyVanity.java:174-175` and 25% in `com/planet_ink/coffee_mud/Abilities/Fighter/Fighter_Fragmentation.java:121-122`, or after a successful use in `com/planet_ink/coffee_mud/Abilities/Fighter/Fighter_PressurePoints.java:117`.

The `d100` roll is `com/planet_ink/coffee_mud/Libraries/Dice.java:409-412`, which returns 1 to 100.

### Shared rule: strikes that apply a status

Most "malicious" skills follow one template:

1. Check target and self requirements (hands, legs, arms, head, eyes, weapon class, range, standing, riding). Failures tell the player why and return false.
2. Call `super.invoke()`, the base-class entry point (its usage and timing code is not traced here).
3. Roll `proficiencyCheck` with a modifier. On failure call `maliciousFizzle()`, which prints "attempt(s)... but fail(s)" to the room (`com/planet_ink/coffee_mud/Abilities/StdAbility.java:2253`).
4. On success build a `CMMsg` with `MSK_MALICIOUS_MOVE|TYP_JUSTICE`, send it through the room's `okMessage`, and if `msg.value() <= 0` the blow lands. A positive value means something else blocked it, and the skill calls `maliciousFizzle` again ("fights off", "block(s)").
5. On landing, apply a timed effect with `maliciousAffect(mob, target, asLevel, ticks, -1)` (`com/planet_ink/coffee_mud/Abilities/StdAbility.java:2185-2201`). A positive tick count is used as is. Zero gives a level-based count, `round(adjustedLevel * 1.1) + 25` ticks, and a negative value is a percentage of that count (`getMaliciousTickdownTime()`, `com/planet_ink/coffee_mud/Abilities/StdAbility.java:2174-2183`).

Examples of the lengths used, with the default four-second tick (`com/planet_ink/coffee_mud/core/CMProps.java:681`, overridable with `TICKTIME` at line 2720):

| Skill | Effect | Ticks | Source |
|---|---|---|---|
| Hammer Ring | dazed (cannot move; cannot use eyes, hands, mouth or move) | 2 | `com/planet_ink/coffee_mud/Abilities/Fighter/Fighter_HammerRing.java:263`, `:113-123` |
| Dismounting Blow | target sits and cannot stand until the effect ends | 2 | `com/planet_ink/coffee_mud/Abilities/Fighter/Fighter_DismountingBlow.java:277`, `:120-138` |
| Gouge | blind (`CAN_NOT_SEE`) | 5 | `com/planet_ink/coffee_mud/Abilities/Fighter/Fighter_Gouge.java:202`, `:108-109` |
| Lightning Strike | attacker exhausted and asleep | 9 | `com/planet_ink/coffee_mud/Abilities/Fighter/Fighter_LightningStrike.java:233`, `:130` |
| Earbox | target deafened | 2 | `com/planet_ink/coffee_mud/Abilities/Fighter/Fighter_Earbox.java:180-182` |
| Hold The Line | stance: enemy advances refused, one weapon attack per tick | 2 + xlevel/3 | `com/planet_ink/coffee_mud/Abilities/Fighter/Fighter_HoldTheLine.java:219` |
| Forceback | no timed effect; target is sent a retreat message | none | `com/planet_ink/coffee_mud/Abilities/Fighter/Fighter_Forceback.java:160-162` |

Many strikes also use a "natural weapon": a `StdWeapon` created on the fly named after the body part (for example "a knee", "an elbow"), set to bashing damage and natural class, and passed to `CMLib.combat().postWeaponAttackResult()`. The skill attaches itself to the caster with `mob.addEffect(this)` for the duration of that one attack, so its `affectCharStats` bonus applies (`com/planet_ink/coffee_mud/Abilities/Fighter/Fighter_Knee.java:181-199`, `com/planet_ink/coffee_mud/Abilities/Fighter/Fighter_Earbox.java:160-178`, `com/planet_ink/coffee_mud/Abilities/Fighter/Fighter_Gutbuster.java:165-183`, `com/planet_ink/coffee_mud/Abilities/Fighter/Fighter_ElbowJab.java:160-178`).

### Shared rule: grapples (FighterGrappleSkill)

A hold creates two linked effects, one on each combatant, through `finishGrapple()` (`com/planet_ink/coffee_mud/Abilities/Fighter/FighterGrappleSkill.java:332-357`). Each effect is a copy of the skill with `pairedWith` set to the other combatant. The duration is `baseTicks + xlevel/2` (line 335); the standard holds pass 4 as `baseTicks` (for example `com/planet_ink/coffee_mud/Abilities/Fighter/Fighter_FingerLock.java:228`).

- **Entry checks** (`com/planet_ink/coffee_mud/Abilities/Fighter/FighterGrappleSkill.java:417-501`): the grappler must not be too far away while in combat (433-437); the target must not be more than three times the grappler's base weight (439-445, `hasWeightLimit()`); the grappler must be able to move; both must be on the same mount; the grappler must be standing unless the hold allows otherwise (462-468).
- **Free attack** (`com/planet_ink/coffee_mud/Abilities/Fighter/FighterGrappleSkill.java:473-493`): when a combatant moves in on a target that is already fighting, the target gets a weapon attack first. The HP lost in that exchange, minus `5 * xlevel` percent, becomes a negative proficiency adjustment (`proficiencyDiff`, lines 486-492). A level gap of the target over the grappler gives a further `-10` per level (lines 494-499).
- **Upkeep** (`com/planet_ink/coffee_mud/Abilities/Fighter/FighterGrappleSkill.java:143-161`): on each tick the holder pays the usage cost (mana, movement and hit points) and must win `rollToHit` against the victim. A failed roll breaks the hold.
- **Struggle** (`com/planet_ink/coffee_mud/Abilities/Fighter/FighterGrappleSkill.java:164-172`): each victim tick increments `tickUp` and calls `makeBreakAttempt()`. The victim breaks free when `min(DEX, STR) + xlevel + 2 * tickUp` is at least `max(DEX, STR) + xlevel` of the holder (`com/planet_ink/coffee_mud/Abilities/Fighter/FighterGrappleSkill.java:103-123`). Every struggle therefore makes escape easier by two points.
- **Restrictions** (`com/planet_ink/coffee_mud/Abilities/Fighter/FighterGrappleSkill.java:208-293`): the held character cannot use hand or move actions unless `isHandsFree()` says otherwise. Immobilising holds (`isImmobilizing()`, used by Pin) block nearly everything with "You are holding a..." or "You are in a...". Other holds allow abilities from the grappling and dirty-fighting domains (`exemptDomains`, lines 177-184).
- **Release** (`com/planet_ink/coffee_mud/Abilities/Fighter/FighterGrappleSkill.java:237-241`): typing a prefix of RELEASE ends the hold. Breaking free prints "break(s) out of", a voluntary release prints "release(s)".

Per-hold specifics:

- **Finger Lock** (`com/planet_ink/coffee_mud/Abilities/Fighter/Fighter_FingerLock.java`): `chanceOfStrength()` (lines 73-84) is `10 * (holder STR - victim STR)`, minimum 10. After three struggles (`tickUp >= 3`, line 96) a roll may apply `BrokenLimbs` to the finger (lines 99-108). Each tick the victim's wielded weapon is removed (`postRemove`, lines 112-113).
- **Elbow Lock** (`com/planet_ink/coffee_mud/Abilities/Fighter/Fighter_ElbowLock.java`): same contest, but five struggles are needed (`tickUp >= 5`, line 96) and the limb is an arm (line 104).
- **Leg Hold** (`com/planet_ink/coffee_mud/Abilities/Fighter/Fighter_LegHold.java`): `affectPhyStats` forces the victim to `IS_SITTING` unless asleep (lines 71-76). `requiresStanding()` is false (lines 106-109), so the hold can be started from the ground.
- **Pin** (`com/planet_ink/coffee_mud/Abilities/Fighter/Fighter_Pin.java`): `isImmobilizing()` is true (lines 88-91), so the victim gets `CAN_NOT_MOVE` from the base class and is also blocked from mouth and eye actions (lines 101-109).
- **Double Arm Hold** (`com/planet_ink/coffee_mud/Abilities/Fighter/Fighter_DoubleArmHold.java:71-74`): halves speed for both combatants (speed of 2 or more loses 1 point instead).
- **Headlock** (`com/planet_ink/coffee_mud/Abilities/Fighter/Fighter_Headlock.java:78-82`): needs a head. It adds nothing to the base hold.

### Mounts and riding

**Favoured mounts.** `com/planet_ink/coffee_mud/Abilities/Fighter/Fighter_FavoredMount.java` is an automatic effect whose `miscText` holds `Category,Race`, for example `Equine,Horse` (`pickMount()`, lines 132-159; the stored text at lines 170-181). `pickMount()` takes a random race from the racial-mount table minus the ones already chosen (`com/planet_ink/coffee_mud/Abilities/Fighter/Fighter_FavoredMount.java:143-158`). The table is `resources/skills/racialmounts.txt` (see Content worth mining). `CoffeeUtensils.getFavoredMounts()` (`com/planet_ink/coffee_mud/Libraries/CoffeeUtensils.java:3459-3491`) collects the effects named in `steedSkills` (lines 3444-3455) and falls back to a Horse when there are none. The rider's "is this my mount" check is `isMount()` (`com/planet_ink/coffee_mud/Abilities/Fighter/Fighter_FavoredMount.java:196-200`), which compares the mount's racial category to the stored category.

Two side effects:

- **Dismount burst.** A `TYP_LEAVE` to a room while a `Go` command is queued, with a successful check, records `lastLeave`. For 250 ms afterwards `affectPhyStats` multiplies speed by four (`com/planet_ink/coffee_mud/Abilities/Fighter/Fighter_FavoredMount.java:204-211`, `:230-253`, `:339-346`).
- **Item hand-off.** Give and get commands between the rider and the mount are allowed past the normal reach checks (`com/planet_ink/coffee_mud/Abilities/Fighter/Fighter_FavoredMount.java:255-328`). Re-entry guards (`norecurse1..3`) stop the recursion.

**Mounted charge (Jousting).** `com/planet_ink/coffee_mud/Abilities/Fighter/Fighter_Jousting.java` triggers on JOUST and JOUSTING. It needs a mobile mount, a polearm with range, and a target farther away than the polearm's range (lines 205-235; the polearm check uses `Fighter_DismountingBlow.getPolearm()`, line 222). Success starts a two-tick window (`beneficialAffect(mob,mob,asLevel,2)`, line 275). During the window:

- The first weapon attack sets `done` (line 112). The first damage message with value above zero is multiplied: `msg.setValue(value * (3 - hits))` for the first two hits (lines 140-142), so 3x then 2x.
- A dismount roll is made on the first hit: `25 + 5 * xlevel` percent (line 122). On success a trailer runnable casts Dismounting Blow at proficiency 100 (lines 124-137).
- On the next mob tick the effect ends and invokes `Fighter_Ridethrough` against the victim (lines 148-165).

**Dismounting Blow.** `com/planet_ink/coffee_mud/Abilities/Fighter/Fighter_DismountingBlow.java` (trigger FORCEDISMOUNT, line 78) needs a wielded polearm unless the call is automatic (lines 221-226). Its range is the polearm's maximum range plus expertise (`maxRange()`, lines 207-212). The check is built from level and dexterity:

- `levelDiff = 5 * (target level - (mob level + 2 * xlevel))` when positive, else 0 (lines 245-249). It is then reduced by `abilityCode() * mob DEX` (line 250).
- `adjustment = -levelDiff - (35 + round(3 * (target DEX - 9)))` (line 251). A DEX 12 target gives -44, so the gate passes 56% of the time before the proficiency roll.

If the blow is not blocked, the dismount is sent; once the target is off the mount, it gets the dazed effect and the message "You hit the ground!" (lines 268-280). The message on success is "dismount(s) <target> with <polearm>".

**Planar mount ("buck" and keep).** `com/planet_ink/coffee_mud/Abilities/Fighter/Fighter_PlanarMount.java` mounts a favoured animal in a plane (trigger: `TYP_MOUNT`, lines 219-227). The sequence:

1. The animal is cloned into a `GenRideable` by copying all generic stats from the animal (`CMLib.coffeeMaker().getAllGenStats`, lines 245-251), with intelligence set to 1 (lines 248-251).
2. The animal is taken off the map: its ticking is suspended and it is removed from the room (lines 268-269).
3. The clone is given the `Skill_Buck` behaviour at proficiency 100 (lines 254-262) and mounted with the normal Mount command (lines 271-276).
4. A `Triad` of (animal, clone, counters) is kept in `mount` (line 267). If the rider is not on the clone for more than 10 ticks, the mount is undone: the animal is returned and the clone destroyed (lines 202-207 and `undoMount()`, lines 143-164).
5. When the clone gains `NO_AUTO_BUCK`, which `Skill_Buck` sets when the mount is bought or tamed (`com/planet_ink/coffee_mud/Abilities/Skills/Skill_Buck.java:162`, `:175-178`), `makeMountPermanent()` runs (line 190). The original is destroyed, or, if it has rejuvenation, returned to the room and killed so it respawns (lines 118-134). The clone stays as the permanent mount. The message is "You have broken <mount>."

The name `makeMountPermanent` covers the clone. It does not say that the animal it came from is destroyed or killed.

**Faithful mount.** `com/planet_ink/coffee_mud/Abilities/Fighter/Fighter_FaithfulMount.java` triggers on the rider's own death (lines 93-98). It looks for a group member or rideable that is an animal, is awake and not bound, is in the same room, and is a favoured mount (lines 99-125). A trailer runnable then (lines 130-208):

- picks up the corpse from the room into the mount's inventory (lines 146-158),
- moves the mount into a temporary "The trail" room (lines 162-167),
- schedules a return after `60000 - 2000 * xlevel - 4000 * xtime` ms, with a minimum of one second (lines 168-172, 203),
- on return, drops the body (lines 187-196).

**Graceful Dismount.** `com/planet_ink/coffee_mud/Abilities/Fighter/Fighter_GracefulDismount.java` remembers the level of the mount it left (lines 104-117). After a dismount it gives the rider a proficiency check with a penalty of `5 * (rider level - mount level)` when the mount was higher level (lines 122-124). If the check passes, it finds effects that would keep the rider on the ground by applying them to a factory mob and checking `isStanding` (lines 133-153). It removes those effects and stands the rider (lines 155-169). If the rider is already down, the removal runs at once and is retried after half a second if the rider still cannot stand; otherwise it runs after half a second (lines 172-179).

**Mounted Retreat.** `com/planet_ink/coffee_mud/Abilities/Fighter/Fighter_MountedRetreat.java` (triggers MFLEE, MRETREAT, MOUNTEDRETREAT, line 75; classed as a thief skill, line 91) needs combat and a mobile mount. It picks a random open, non-indoor exit or uses the given direction (lines 113-136). The adjustment is -25 for each enemy who is mounted and attacking the group, and -5 for each such enemy holding a weapon with reach, meaning maximum range above zero (lines 158-178). The retreat fails with "Your attempt at a dignified mounted retreat is thwarted!" (line 183).

**Fierce Mount.** `com/planet_ink/coffee_mud/Abilities/Fighter/Fighter_FierceMount.java` is an automatic skill on the rider. While the rider is in combat it copies a combat buff onto the mount each tick when the mount lacks it (lines 136-145). The level is `2 + adjustedLevel(invoker)/10`, and the buff adds `prof/100 * level` to damage and `prof/100 * 2 * level` to attack (lines 105-115). The buff ends when the rider leaves or is out of combat (lines 153-160).

### Terrain and plane tactics

`com/planet_ink/coffee_mud/Abilities/Fighter/Fighter_FieldTactics.java` is an automatic skill with no caster-side actions. Its `appliesHere()` checks the room's domain against `landClasses()` (lines 185-202). The five terrain subclasses differ only in name and domain (diffed against Forest, identical apart from those lines):

| Class | `Room` domain constant | Value | Source |
|---|---|---|---|
| `Fighter_ForestTactics` | `DOMAIN_OUTDOORS_WOODS` | 1 | `com/planet_ink/coffee_mud/Abilities/Fighter/Fighter_ForestTactics.java:51`, `com/planet_ink/coffee_mud/Locales/interfaces/Room.java:83` |
| `Fighter_PlainsTactics` | `DOMAIN_OUTDOORS_PLAINS` | 3 | `com/planet_ink/coffee_mud/Locales/interfaces/Room.java:87` |
| `Fighter_JungleTactics` | `DOMAIN_OUTDOORS_JUNGLE` | 7 | `com/planet_ink/coffee_mud/Locales/interfaces/Room.java:95` |
| `Fighter_HillsTactics` | `DOMAIN_OUTDOORS_HILLS` | 10 | `com/planet_ink/coffee_mud/Locales/interfaces/Room.java:101` |
| `Fighter_MountainTactics` | `DOMAIN_OUTDOORS_MOUNTAINS` | 11 | `com/planet_ink/coffee_mud/Locales/interfaces/Room.java:103` |

The effects, when the mob is in one of those rooms:

- **Attack and armour** (`com/planet_ink/coffee_mud/Abilities/Fighter/Fighter_FieldTactics.java:221-223`): attack adjustment `+ round((15 + xlvl) * prof/100)` and armour `- round((15 + xlvl) * prof/100)`. In this game a lower armour value is better. `com/planet_ink/coffee_mud/Libraries/MUDFight.java:409-421` negates the defence value before the roll, so more negative armour means fewer hits.
- **Damage** (`com/planet_ink/coffee_mud/Abilities/Fighter/Fighter_FieldTactics.java:150-156`): on a weapon damage message with value above zero, the value becomes `value + round(value * prof / (400 - 20 * xlvl))`. At proficiency 100 with no expertise that is +25%.
- **Hiding** (`com/planet_ink/coffee_mud/Abilities/Fighter/Fighter_FieldTactics.java:109-127`, `:218-219`): while the character sits with no mount, `tick()` sets `hidden`, and `affectPhyStats()` sets `activated` for the stat bonuses (line 220). After two minutes (`60*2*1000` ms) hidden characters get `IS_HIDDEN`. Sounds, speech, entering, leaving and recall reveal them (`:166-181`).

`com/planet_ink/coffee_mud/Abilities/Fighter/Fighter_MountedTactics.java:54-74` applies when the character is riding a MOB whose race is in the favoured-mount list (by race object or racial category). `com/planet_ink/coffee_mud/Abilities/Fighter/Fighter_PlanarTactics.java:61-80` applies when the room's area is in a plane of existence (`CMLib.flags().getPlaneOfExistence`), with a cache of the last area checked.

**Planar veteran.** `com/planet_ink/coffee_mud/Abilities/Fighter/Fighter_PlanarVeteran.java` is an automatic skill with its own memory. When the character enters an area on a plane it has not visited, `addVisitation()` records the plane (lines 213-228) with an expiry of `60000 * level * (60 + 30 * timeXlevel)` milliseconds of wall-clock time (lines 220-221). At level 10 with no time expertise that is 600 minutes. Expired visits are dropped in `checkExpiredVisits()` (lines 254-279). The active visits feed `rebuildBonuses()` (lines 119-206), which reads each plane's variables and adds:

| Plane category | Bonus | Source |
|---|---|---|
| `inner` | armour -2 | `com/planet_ink/coffee_mud/Abilities/Fighter/Fighter_PlanarVeteran.java:134-135` |
| `transitional` | speed +0.1 times `CMProps.getSpeedAdjustment()` (`BONUSACT`, default 1.0) | `:136-137`, `CMProps` line 2997 |
| `outer`, by alignment | GOOD moves +5; NEUTRAL HP +5; EVIL damage +1; LAWFUL mind save +3; MODERATE armour -2; CHAOTIC attack +1 | `:139-201` |

Alignment for `outer` comes from the plane's alignment value and from a list of faction ranges (`:143-175`).

### Unarmed and monk strikes

`MonkSkill.anyWeapons()` (`com/planet_ink/coffee_mud/Abilities/Fighter/MonkSkill.java:33-36`) is true if a weapon is wielded or an item is held. The monk strikes are gated on it.

- **Monkey Punch** and **Knife Hand** (`com/planet_ink/coffee_mud/Abilities/Fighter/Fighter_MonkeyPunch.java:96-127`, `com/planet_ink/coffee_mud/Abilities/Fighter/Fighter_KnifeHand.java:96-127`) attack on every mob tick while the character is in melee, unarmed, with more than one hand and awake (conditions at lines 104-108). They use a `GenWeapon` built once: "a monkey punch" with bashing damage 5 (`:115-121`), or "a knife hand" with piercing damage 7. Each tick the attack is made by posting an attack with that weapon. The bonus is `base damage + xlevel` on the damage message (Monkey Punch `:144`, Knife Hand `:143`).
- **Kick** (`com/planet_ink/coffee_mud/Abilities/Fighter/Fighter_Kick.java:123-128`): proficiency adjustment `(STR - target STR) * 2`. Damage is `roll(1, adjustedLevel/2, 2)`, which is one die of that many sides plus 2. Half damage if the message value is above zero (lines 134-135).
- **Flying Kick** (`com/planet_ink/coffee_mud/Abilities/Fighter/Fighter_FlyingKick.java:134-150`): adjustment `(DEX - target DEX) * 2`. Damage is `roll(1, adjustedLevel + 20, 0)`, halved if blocked. A success sets both to melee range (lines 149-150).
- **Ki Strike** (`com/planet_ink/coffee_mud/Abilities/Fighter/Fighter_KiStrike.java:91-111`, `:137`): a one-shot buff. The next damage message the holder makes with a weapon or with no tool gains `adjustedLevel` damage and triggers a yell, "yell(s) 'KIA'!" (lines 104-106). The buff lasts two ticks in combat and four out of combat.
- **Lightning Strike** (`com/planet_ink/coffee_mud/Abilities/Fighter/Fighter_LightningStrike.java:174-242`): needs DEX at least `BASEMAXSTAT` (default 18, `com/planet_ink/coffee_mud/core/CMProps.java:3094`; checks at lines 163 and 185), two hands and no weapon. Its level gap is `5 * (target level - (mob level + 2 * xlevel))` as a penalty (lines 205-209). It makes `xlevel + qualifyingClassLevel` postAttacks while the target stays in the room and the holder stays unarmed (lines 223-229). If it finishes unarmed and unblocked, the holder collapses for nine ticks (lines 230-234).
- **Gouge** (`com/planet_ink/coffee_mud/Abilities/Fighter/Fighter_Gouge.java:162-241`): needs hands, eyes on the target and no weapon (lines 168-187). On success the target is blinded for five ticks (line 202). An `Injury` effect is applied to the first intact eye, with the attacker's name and the eye limb as its text (lines 203-235). The damage is `maxHP / (20 - xlevel)` (line 226).
- **Pressure Points** (`com/planet_ink/coffee_mud/Abilities/Fighter/Fighter_PressurePoints.java:103-116`): passive. On a natural-weapon hit at range 0 on the current victim, made while the holder carries no weapon, the gate is `-90 + STR + 2 * xlevel`, which passes `(10 + STR + 2 * xlevel)%`. If it passes, the damage message grows by `value * prof/100 * random`.

### Weapon-use skills

- **Hammer Ring** (`com/planet_ink/coffee_mud/Abilities/Fighter/Fighter_HammerRing.java`, triggers HAMMERRING and HAMMERING, line 59). Needs a hammer-class weapon (lines 225-231). Always fails against undead, golems and characters with no head (lines 245-248). Weight limit: target more than 250 heavier than the attacker (lines 219-223). The roll is `(-levelDiff) - (target CON - mob STR)` with `levelDiff` as described under Problems (lines 237-244). On a non-blocked hit, the target is dazed for two ticks and combat stops on both sides (lines 252-268). The effect is `HealthCondition` with the description "Dazed" (lines 97-100).
- **Hold The Line** (`com/planet_ink/coffee_mud/Abilities/Fighter/Fighter_HoldTheLine.java:170-227`): needs an in-combat, non-mounted holder with a polearm that is two-handed (`rawLogicalAnd()` true), at least range 1, worn properly and not monkey-gripped (lines 191-198), and an enemy at least the weapon's range away (lines 200-204). On success it lasts `2 + xlevel/3` ticks (line 219). While it lasts, an enemy's advance on the holder's victim is refused and reported as "hold(s) the line against" (lines 105-113), with a 20% helpProficiency chance (line 109). The holder gets one weapon attack per tick (lines 116-122). The holder's own advance ends the stance (lines 95-101).
- **Forceback** (`com/planet_ink/coffee_mud/Abilities/Fighter/Fighter_Forceback.java:116-170`): the same polearm check. It requires a target no more than the weapon's range plus one (lines 141-145). On success the target gets a `MSG_RETREAT` (lines 160-162). The skill has a 60-second minimum cast wait (`minCastWaitTime()`, lines 84-87).
- **Pistolwhip** (`com/planet_ink/coffee_mud/Abilities/Fighter/Fighter_Pistolwhip.java:89-171`): needs a wielded ammunition weapon of ranged class with bullet or bolt ammunition, or a technical weapon (`isPistolWeapon()`, lines 89-102). It makes a `ShieldWeapon` named "the butt of <weapon>" with the weapon's damage plus `xlevel` (lines 153-163) and posts an attack with it.
- **Monkey Grip** (`com/planet_ink/coffee_mud/Abilities/Fighter/Fighter_MonkeyGrip.java:222-278`): only a weapon whose `rawLogicalAnd()` is true can be gripped (lines 228-233). That flag says whether a worn item needs all of its locations or any one (`com/planet_ink/coffee_mud/Items/interfaces/Wearable.java:229-231`). Gripping sets it to false (line 250), so the weapon can be held in one hand. The effect on the weapon reduces damage and positive attack adjustment by `pctLoss = 0.75 - (0.50 + 0.05 * xlevel) * prof/100` (lines 176-179). At full proficiency and no expertise that is 25%; at zero it is 75%. The holder also takes an attack penalty of `sum(item levels) - 5 * sum(xlevels)` for each gripped item (lines 186-216). The effect is cleaned off the weapon when it is no longer owned by the holder, when a removal leaves it in inventory, and on quit or shutdown (lines 112-125, 168-173, 151-156).
- **Far Shot** and **Point Blank** (`com/planet_ink/coffee_mud/Abilities/Fighter/Fighter_FarShot.java:162-170`, `com/planet_ink/coffee_mud/Abilities/Fighter/Fighter_PointBlank.java:160-169`) are automatic. They attach a copy of the skill to ranged ammunition weapons the holder wields or wears, and write the weapon's range into its `armor()` field. Far Shot sets max range to 100 (`(armor & MASK_MAXRANGEZERO) + MASK_MAXRANGEFLAG + 100`, lines 167-168). Point Blank sets min range to 0 (lines 166-167). The masks are in `com/planet_ink/coffee_mud/Items/interfaces/Weapon.java:183-197`. Far Shot duplicates the mechanism of `com/planet_ink/coffee_mud/Abilities/Skills/Skill_LongReach.java:173-174`.
- **Improved Throwing** (`com/planet_ink/coffee_mud/Abilities/Fighter/Fighter_ImprovedThrowing.java:94-121`): damage on thrown weapons gains `value * prof / (100 - 10 * xlevel)` (line 105). Attack adjustment `+ (15 + xlevel) * prof/100` when a thrown weapon is wielded (line 119).
- **Fragmentation** (`com/planet_ink/coffee_mud/Abilities/Fighter/Fighter_Fragmentation.java:110-129`): thrown weapons of glass, metal, rock or wood that hit for damage gain `3 * value * prof / (100 - 10 * xlevel)` (line 126). The shattered weapon is destroyed when the ability is used as the tool (lines 96-98). At full proficiency and no expertise the damage is four times the base.
- **Fast Slinging** (`com/planet_ink/coffee_mud/Abilities/Fighter/Fighter_FastSlinging.java:102-117`): in combat, with a ranged ammunition weapon whose name contains "sling", it fires `1 + floor(adjustedLevel/16)` extra postAttacks each tick (lines 109-116).

### Passive defence and social

- **Dual Parry** (`com/planet_ink/coffee_mud/Abilities/Fighter/Fighter_DualParry.java:96-142`): when a weapon attack at range 0 lands on the holder, and both the attacking weapon and the holder's held weapon are neither flailed, natural, ranged nor thrown (lines 112-125), the held weapon must also have `rawLogicalAnd()` false, which marks it one-hand capable (line 116). The holder then parries with a gate of `DEX - 90 + 2 * xlevel` (line 128). That passes `(10 + DEX + 2 * xlevel)%`. The parry cannot happen twice in a row (`lastTime`, lines 129, 132, 137).
- **Forearm Block** (`com/planet_ink/coffee_mud/Abilities/Fighter/Fighter_ForearmBlock.java:104-133`): blocks unarmed or natural melee attacks made at range 0 against the holder, while the holder has arms and is not bound or held. The attempts per mob tick are limited to `speed + xlevel/3` (lines 123, reset at 98-99). The gate is `DEX - 93 + xlevel` (line 124), passing `(7 + DEX + xlevel)%`. At the default phys-stat speed of 1.0 (`com/planet_ink/coffee_mud/Common/DefaultPhyStats.java:63`) with no expertise the first attempt fails the comparison `1 < 1.0`.
- **Improved Shield Defence** (`com/planet_ink/coffee_mud/Abilities/Fighter/Fighter_ImprovedShieldDefence.java:96-152`): the armour of each shield worn or held outside a container is summed on the first mob tick (lines 133-141). The holder gets an armour reduction of `sum * (prof + 5 * xlevel) / 100` (line 100).
- **Intimidation** (`com/planet_ink/coffee_mud/Abilities/Fighter/Fighter_Intimidate.java:96-135`): when a malicious message aimed at the holder comes from a character whose level is below the holder's level plus twice its expertise (`levelDiff = 10 * (attacker level - (holder level + 2 * xlevel))`, line 105), and the holder is not in combat, not the attacker's victim, and in the same room, the attacker gets "You are too intimidated by <holder>." (line 121). The gate is `-(100 + levelDiff) + 2 * CHA` (line 112) and the holder's proficiency check follows. Officers of the local legal behaviour are exempt (lines 114-120).
- **Lucky Vanity** (`com/planet_ink/coffee_mud/Abilities/Fighter/Fighter_LuckyVanity.java:97-177`): adds +5 to six saving throws (mind, poison, paralysis, polymorph, disease and traps, lines 157-162) for each worn armour piece whose computed base value times three is less than its listed gold value (lines 135-141). Up to `1 + xlevel` pieces count. The bonus needs at least one other visible character in the room, and three if the holder is mounted (lines 103-125). The bonus is scaled by proficiency below 100 (lines 143-146), but see Problems for a caching bug.
- **Heroism** (`com/planet_ink/coffee_mud/Abilities/Fighter/Fighter_Heroism.java:127-135`): adds `CHA/4 + STR/4 + adjustedLevel/2` to the justice save (integer division on the stats).
- **Hardiness** (`com/planet_ink/coffee_mud/Abilities/Fighter/Fighter_Hardiness.java:94-98`): adds `30 + round(2 * adjustedLevel * prof/100)` to the movement pool.
- **Endurance** (`com/planet_ink/coffee_mud/Abilities/Fighter/Fighter_Endurance.java:94-120`): while sitting or sleeping and not in combat, each mob tick runs `xlevel/3 + 1` recovery ticks (lines 106-108).

### Messages shown to players

The bucket's messages are short and use the usual `<S-NAME>` and `<T-NAME>` tokens. A few that show the mechanics:

- "You are way too drowsy." when a dazed or exhausted character tries to act (`com/planet_ink/coffee_mud/Abilities/Fighter/Fighter_HammerRing.java:121`, `com/planet_ink/coffee_mud/Abilities/Fighter/Fighter_LightningStrike.java:115`).
- "<S-NAME> seem(s) less dazed." on Hammer Ring ending (`com/planet_ink/coffee_mud/Abilities/Fighter/Fighter_HammerRing.java:171`).
- "<S-NAME> collapse(s) in exhaustion." (`com/planet_ink/coffee_mud/Abilities/Fighter/Fighter_LightningStrike.java:232`).
- "Your eyes feel better." when blinding ends (`com/planet_ink/coffee_mud/Abilities/Fighter/Fighter_Gouge.java:135`).
- "Your ability to hold the line wavers." (`com/planet_ink/coffee_mud/Abilities/Fighter/Fighter_HoldTheLine.java:141`).
- "You hit the ground!" on a successful dismount (`com/planet_ink/coffee_mud/Abilities/Fighter/Fighter_DismountingBlow.java:278`).
- "Your faithful mount is on the way with your corpse!" (`com/planet_ink/coffee_mud/Abilities/Fighter/Fighter_FaithfulMount.java:173`).
- "You have broken <mount>." and "You have failed to break <target>." (`com/planet_ink/coffee_mud/Abilities/Fighter/Fighter_PlanarMount.java:138`, `:161`).

## Content worth mining

### Skill catalogue

Each row is a class `Fighter_<Name>` in `com/planet_ink/coffee_mud/Abilities/Fighter/`, with the trigger words that the command parser maps to it (where there are any), its base class, and the core of what it does. "auto" means the skill is automatic and passive, with no command. Line counts are from the manifest.

| ID | Lines | Base | Triggers | What it does |
|---|---|---|---|---|
| Fighter_FavoredMount | 349 | StdAbility | (auto) | Picks a favoured mount race; dismount speed burst; item hand-off to mount |
| Fighter_FavoredMount2, 3, 4 | 53 each | FavoredMount | (auto) | Further favoured-mount slots |
| Fighter_PlanarMount | 316 | FighterSkill | (auto) | Breaks a favoured animal in a plane into a permanent rideable |
| Fighter_FaithfulMount | 213 | FighterSkill | (auto) | Mount recovers the rider's corpse after death |
| Fighter_FierceMount | 165 | FighterSkill | (auto) | Combat buff copied onto the mount |
| Fighter_GracefulDismount | 198 | FighterSkill | (auto) | Stands the rider after a dismount |
| Fighter_MountedRetreat | 195 | FighterSkill | MFLEE, MRETREAT, MOUNTEDRETREAT | Flee from combat on a mount |
| Fighter_MountedTactics | 75 | FieldTactics | (auto) | Bonuses when riding a favoured mount race |
| Fighter_Jousting | 296 | FighterSkill | JOUST, JOUSTING | Mounted charge, triple then double first hits, chains into Ridethrough |
| Fighter_DismountingBlow | 287 | FighterSkill | FORCEDISMOUNT | Polearm dismount; target dazed on the ground |
| Fighter_FieldTactics | 231 | FighterSkill | (auto) | Base of terrain tactics |
| Fighter_ForestTactics | 57 | FieldTactics | (auto) | Woods bonuses |
| Fighter_HillsTactics | 57 | FieldTactics | (auto) | Hills bonuses |
| Fighter_JungleTactics | 57 | FieldTactics | (auto) | Jungle bonuses |
| Fighter_MountainTactics | 57 | FieldTactics | (auto) | Mountain bonuses |
| Fighter_PlainsTactics | 57 | FieldTactics | (auto) | Plains bonuses |
| Fighter_PlanarTactics | 81 | FieldTactics | (auto) | Bonuses while in a plane of existence |
| Fighter_PlanarVeteran | 346 | FighterSkill | (auto) | Timed memory of visited planes, stat bonuses by category |
| Fighter_FingerLock | 239 | FighterGrappleSkill | FINGERLOCK | Hold; breaks a finger after struggles |
| Fighter_ElbowLock | 235 | FighterGrappleSkill | ELBOWLOCK | Hold; breaks an arm after more struggles |
| Fighter_LegHold | 159 | FighterGrappleSkill | LEGHOLD | Hold that sits the victim down |
| Fighter_Pin | 152 | FighterGrappleSkill | PIN | Immobilising hold |
| Fighter_DoubleArmHold | 120 | FighterGrappleSkill | ARMHOLD | Hold that halves speed for both |
| Fighter_Headlock | 114 | FighterGrappleSkill | HEADLOCK | Plain hold; needs a head |
| Fighter_Knee | 207 | FighterSkill | KNEE | Grapple finisher: knee strike with crit bonus |
| Fighter_Gutbuster | 193 | FighterSkill | GUTBUSTER | Knee strike on a target on the ground; attacker kneels |
| Fighter_Earbox | 195 | FighterSkill | EARBOX | Grapple finisher: head strike, two ticks of deafness, small migraine chance |
| Fighter_ElbowJab | 186 | FighterSkill | ELBOWJAB | Grapple finisher: elbow strike |
| Fighter_MonkeyPunch | 147 | MonkSkill | (auto) | Automatic unarmed punch each tick in melee |
| Fighter_KnifeHand | 146 | MonkSkill | (auto) | Automatic knife-hand strike each tick in melee |
| Fighter_KiStrike | 146 | FighterSkill | KISTRIKE, KI | Next unarmed or weapon damage gets a bonus |
| Fighter_Kick | 145 | FighterSkill | KICK | Kick with a damage roll and a strength-based adjustment |
| Fighter_FlyingKick | 162 | FighterSkill | FLYINGKICK, FLYKICK | Closes distance and kicks |
| Fighter_LightningStrike | 243 | MonkSkill | LIGHTNINGSTRIKE, LSTRIKE | Unarmed flurry; attacker exhausted afterwards |
| Fighter_Gouge | 242 | MonkSkill | GOUGE | Blinds and injures an eye |
| Fighter_PressurePoints | 121 | MonkSkill | (auto) | Random bonus damage on unarmed hits |
| Fighter_HammerRing | 279 | FighterSkill | HAMMERRING, HAMMERING | Hammer blow to the head; dazes the target |
| Fighter_HoldTheLine | 228 | FighterSkill | HOLDTHELINE | Polearm stance; blocks enemy advance |
| Fighter_Forceback | 171 | FighterSkill | FORCEBACK | Polearm push-back |
| Fighter_Pistolwhip | 172 | FighterSkill | PISTOLWHIP | Crossbow butt strike |
| Fighter_MonkeyGrip | 279 | FighterSkill | MONKEYGRIP, MGRIP | One-hand a two-handed weapon at a damage penalty |
| Fighter_FarShot | 217 | FighterSkill | (auto) | Ranged weapons get maximum range 100 |
| Fighter_PointBlank | 217 | FighterSkill | (auto) | Ranged weapons get minimum range 0 |
| Fighter_ImprovedThrowing | 123 | FighterSkill | (auto) | Thrown weapon damage and attack bonus |
| Fighter_Fragmentation | 132 | FighterSkill | (auto) | Glass, metal, rock or wood thrown weapons shatter for extra damage |
| Fighter_FastSlinging | 121 | FighterSkill | (auto) | Extra volleys with a sling |
| Fighter_DualParry | 143 | FighterSkill | (auto) | Parry with a held one-handed weapon |
| Fighter_ForearmBlock | 134 | FighterSkill | (auto) | Blocks unarmed or natural melee attacks |
| Fighter_ImprovedShieldDefence | 153 | FighterSkill | (auto) | Shield armour bonus |
| Fighter_Intimidate | 138 | FighterSkill | (auto) | Weaker hostile characters may not approach |
| Fighter_LuckyVanity | 218 | FighterSkill | (auto) | Saves bonus from over-priced worn armour when others are present |
| Fighter_Heroism | 136 | FighterSkill | (auto) | Justice save from CHA, STR and level |
| Fighter_Hardiness | 119 | FighterSkill | (auto) | Movement pool |
| Fighter_Endurance | 121 | FighterSkill | (auto) | Extra recovery while sitting or sleeping |

### Racial mounts

`resources/skills/racialmounts.txt` maps each race to a mount category and a default mount race (the first column is the race ID, `*` is the fallback). Used by `Fighter_RacialMount` and the favoured-mount picker. Selected rows:

| Race | Mount category | Mount race |
|---|---|---|
| `*` (default) | Equine | Horse |
| Human | Equine | Horse |
| Beastkin | Canine | DireWolf |
| Drow | Arachnid | GiantSpider |
| Dwarf | Ursine | DireBear |
| Elf | Feline | DireCat |
| Gnome | Mustelid | DireOtter |
| Goblin | Rodent | DireRat |
| LizardMan | Reptile | GiantTurtle |
| Merfolk | Fish | GiantSeahorse |
| Ogre | Pachyderm | Elephant |
| Pixie | Feline | GreatCat |
| Svirfneblin | Elemental | EarthElemental |

The table has 20 rows in all. The category is what `isMount()` and the mounted tactics compare (`com/planet_ink/coffee_mud/Abilities/Fighter/Fighter_FavoredMount.java:198-199`, `com/planet_ink/coffee_mud/Abilities/Fighter/Fighter_MountedTactics.java:65-67`), so any animal in the same category counts, not only the exact race. The default for a race comes from this table, and the favoured-mount picker chooses at random from all rows not already taken (`com/planet_ink/coffee_mud/Abilities/Fighter/Fighter_FavoredMount.java:143-158`).

### Terrain and plane tactics

The five terrain classes use the room domains from the table in How it works. A plane is one of the planes of existence named in area data. Planar Veteran's categories (inner, transitional, outer) and alignment mapping are data-driven through `PlanarVar` values (`com/planet_ink/coffee_mud/Abilities/Fighter/Fighter_PlanarVeteran.java:130-175`).

### Sample messages

Short phrases worth keeping: "You are way too drowsy.", "<S-NAME> seem(s) less dazed.", "<S-NAME> manage(s) a graceful dismount.", "You are too intimidated by <T-NAME>.", "Your attempt at a dignified mounted retreat is thwarted!", "You are on your way from where you were, to where you need to be." (the trail room description, `com/planet_ink/coffee_mud/Abilities/Fighter/Fighter_FaithfulMount.java:165`).

## Notable design ideas

- **A two-stage proficiency gate.** A negative modifier is not a penalty on the chance but a separate gate (`com/planet_ink/coffee_mud/Abilities/StdAbility.java:1480-1484`). The overall chance is the product of the two rolls. Skills therefore read as "you must first be fast enough to get a foothold, then skilled enough to finish it". This is used across the bucket: Forearm Block `(7 + DEX + x)%` then the skill roll, Dual Parry `(10 + DEX + 2x)%` then the skill roll.
- **Holds as two linked effects.** A hold is two copies of the same effect, each pointing at the other (`com/planet_ink/coffee_mud/Abilities/Fighter/FighterGrappleSkill.java:352-353`). Each side runs the same `tick()`; the holder's copy spends upkeep and rolls to maintain, and the victim's copy counts struggles. Breaking free grows easier with every struggle (the `2 * tickUp` term at line 115).
- **Status effects as the ability itself.** The skill object that landed a blow is copied onto the target as the status (`maliciousAffect` calls `copyOf()` at `com/planet_ink/coffee_mud/Abilities/StdAbility.java:2203`). The same class supplies the display text, the restrictions and the cleanup. Hammer Ring also implements `HealthCondition` so the dazed state appears in the health display (`com/planet_ink/coffee_mud/Abilities/Fighter/Fighter_HammerRing.java:97-100`).
- **Weapon grip as a data flag.** The one-handed versus two-handed distinction is the worn-location AND/OR flag. Monkey Grip flips it and gets one-handed use of a two-hander with a damage cost, and Dual Parry and Hold The Line test the same flag (`com/planet_ink/coffee_mud/Abilities/Fighter/Fighter_DualParry.java:116`, `com/planet_ink/coffee_mud/Abilities/Fighter/Fighter_HoldTheLine.java:191-194`). One boolean carries three rules.
- **Range stored in the armour field.** Far Shot and Point Blank set weapon range by writing into the bits of `armor()` (`com/planet_ink/coffee_mud/Items/interfaces/Weapon.java:183-197`). This is a clever reuse of an existing stat field, but it is a hidden coupling, discussed under Problems.
- **Mount as a converted animal.** Planar Mount copies every generic stat of a captured animal into a new `GenRideable`, parks the original off the map, and gives the copy the riding behaviour (`com/planet_ink/coffee_mud/Abilities/Fighter/Fighter_PlanarMount.java:245-270`). A creature becomes a vehicle without a new type.
- **Memory with real-time expiry.** Planar Veteran records each plane's expiry time and recomputes bonuses from what remains (`com/planet_ink/coffee_mud/Abilities/Fighter/Fighter_PlanarVeteran.java:213-279`). The memory is data on the ability, persisted through its `miscText`.
- **Expertise as a second axis.** `expertise(mob, ability, XType)` gives each skill its own levels for power (XLEVEL), duration (XTIME), range (XMAXRANGE, +20% per level, `com/planet_ink/coffee_mud/Abilities/StdAbility.java:482-490`) and cost. Each skill chooses which to use (`getXTIMELevel` at `com/planet_ink/coffee_mud/Abilities/Fighter/Fighter_FaithfulMount.java:169-170`, for example).
- **Skill composition by trailer.** Jousting schedules Dismounting Blow and Ridethrough as trailer runnables or follow-up invocations (`com/planet_ink/coffee_mud/Abilities/Fighter/Fighter_Jousting.java:124-137`, `:157-161`). One skill calls another without knowing its internals.
- **Corpse recovery by an animal.** Faithful Mount returns the body by walking the mount through a temporary room on a timer that shortens with expertise (`com/planet_ink/coffee_mud/Abilities/Fighter/Fighter_FaithfulMount.java:162-172`). It is a small, self-contained event chain that a single-player game could reuse for a companion.

## Problems and cruft

Found by reading. Lines are from the file cited. Items marked "unverified" depend on code outside this bucket that I did not trace.

1. **Level-gap sign looks inverted in Hammer Ring.** `com/planet_ink/coffee_mud/Abilities/Fighter/Fighter_HammerRing.java:237-241` multiplies by 10 only when the target is weaker (`levelDiff < 0`) and sets it to zero otherwise. The result is a bonus for hitting weaker targets and no penalty for stronger ones. Dismounting Blow (`com/planet_ink/coffee_mud/Abilities/Fighter/Fighter_DismountingBlow.java:246-249`), Lightning Strike (`com/planet_ink/coffee_mud/Abilities/Fighter/Fighter_LightningStrike.java:206-209`) and Grapple (`com/planet_ink/coffee_mud/Abilities/Fighter/FighterGrappleSkill.java:494-498`) all penalise when the target is stronger (`> 0`).
2. **Faithful Mount retry schedules null.** `com/planet_ink/coffee_mud/Abilities/Fighter/Fighter_FaithfulMount.java:182` captures `reEnter[0]` inside its own initialiser, while the array element is still null. The retry at line 200 then schedules `meRun`, which is null. Only the first attempt (line 203) is valid. Whether `scheduleRunnable(null, ...)` fails silently is unverified.
3. **Operator precedence in Gouge's invoke.** `com/planet_ink/coffee_mud/Abilities/Fighter/Fighter_Gouge.java:168-171` reads `(!auto) && (hands <= 0) || (no hand body mask && no feet)`. The second clause is not guarded by `!auto`, so an automatic gouge from a race with no hands is refused with "You need hands to gouge."
4. **Dead blind-cancel path in Gouge.** `doneTicking` (`com/planet_ink/coffee_mud/Abilities/Fighter/Fighter_Gouge.java:37`) is never assigned true. Lines 108 and 120 test it, so the early-end branch cannot run. The blindness lasts for the full five ticks.
5. **Possible division by zero in Gouge.** `com/planet_ink/coffee_mud/Abilities/Fighter/Fighter_Gouge.java:226` divides an integer by `(20 - xlevel)`. At expertise 20 that is an `ArithmeticException` (unverified whether expertise can reach 20).
6. **Proficiency scaling is lost on cache hits in Lucky Vanity.** The cached branch (`com/planet_ink/coffee_mud/Abilities/Fighter/Fighter_LuckyVanity.java:126-130`) returns `gearBonus` before the scaling at lines 143-146, and `gearBonus` is stored unscaled (line 147). Scaling applies only on the first calculation after a gear change.
7. **Heroism's active flag is never read.** `com/planet_ink/coffee_mud/Abilities/Fighter/Fighter_Heroism.java:93-99` sets `activated`, and nothing reads it. `affectCharStats` (lines 127-135) always adds the bonus, so the in-combat gating in `tick()` (lines 112-122) does nothing to the stats.
8. **Forearm Block may never trigger at default speed.** `com/planet_ink/coffee_mud/Abilities/Fighter/Fighter_ForearmBlock.java:123` compares `++triesThisRound < speed + xlevel/3`. The first try is 1, and `com/planet_ink/coffee_mud/Common/DefaultPhyStats.java:63` sets speed to 1.0, so `1 < 1.0` fails until expertise or a race speed raises it. How mobs get their speed in practice is unverified.
9. **Division by a value that reaches zero.** `com/planet_ink/coffee_mud/Abilities/Fighter/Fighter_Fragmentation.java:126` and `com/planet_ink/coffee_mud/Abilities/Fighter/Fighter_ImprovedThrowing.java:105` divide by `100 - 10 * xlevel`, with no guard. At expertise 10 the denominator is zero (`CMath.div` is plain `a/b`, `com/planet_ink/coffee_mud/core/CMath.java:433-435`), and above 10 it is negative.
10. **Fast Slinging matches weapon names.** `com/planet_ink/coffee_mud/Abilities/Fighter/Fighter_FastSlinging.java:109` finds "sling" as a substring of the weapon's name. It also has no `TICKID` filter, unlike `com/planet_ink/coffee_mud/Abilities/Fighter/Fighter_Endurance.java:104` and `com/planet_ink/coffee_mud/Abilities/Fighter/Fighter_FierceMount.java:125`, so it may run on every tick type (unverified).
11. **Dead exemption in the grapple base.** `FighterGrappleSkill.isMonkish()` always returns false (`com/planet_ink/coffee_mud/Abilities/Fighter/FighterGrappleSkill.java:186-201`). The body that tested strength, dexterity and cloth armour is commented out, but the check still runs at line 475.
12. **Stale and dangling skill references.** `com/planet_ink/coffee_mud/Libraries/CoffeeUtensils.java:3444-3455` lists `Fighter_FavoredMount1`, `5`, `6` and `7`. No such classes exist in the tree, so those entries never match anything. The working slots are `Fighter_FavoredMount` and `2` to `4`.
13. **Empty override.** `com/planet_ink/coffee_mud/Abilities/Fighter/Fighter_FavoredMount.java:122-128` overrides `text()` with two branches that both return `miscText`.
14. **Parameter ignored.** `com/planet_ink/coffee_mud/Abilities/Fighter/Fighter_PlanarVeteran.java:230-236`: `parseVisits(text)` reads `text()` instead of its argument. It works only because every caller passes `text()`.
15. **Copy-paste comments and duplicated code.** Hammer Ring's comment about "consistently prevent the mob ... except sleep" (`com/planet_ink/coffee_mud/Abilities/Fighter/Fighter_HammerRing.java:110-112`) is repeated in Lightning Strike (`com/planet_ink/coffee_mud/Abilities/Fighter/Fighter_LightningStrike.java:104-106`). Finger Lock and Elbow Lock are near copies. Knee and Gutbuster both name their strike "a knee" (`com/planet_ink/coffee_mud/Abilities/Fighter/Fighter_Knee.java:182`, `com/planet_ink/coffee_mud/Abilities/Fighter/Fighter_Gutbuster.java:166`). Monkey Punch and Knife Hand differ only in name, damage and damage type. Far Shot and Point Blank are the same class with the range mask changed. Skill_LongReach does the same job.
16. **Gutbuster copies the target's stance onto the attacker.** `com/planet_ink/coffee_mud/Abilities/Fighter/Fighter_Gutbuster.java:185-186` ORs the target's whole disposition word into the attacker's. A target who is asleep would make the attacker asleep too. The intent looks like "kneel", which is only `IS_SITTING`.
17. **Misleading message.** `com/planet_ink/coffee_mud/Abilities/Fighter/Fighter_Pistolwhip.java:123` says "bowwhip" for a crossbow skill.
18. **Unused helper in Jousting.** `com/planet_ink/coffee_mud/Abilities/Fighter/Fighter_Jousting.java:280-282` calls `setVictim(null)` with a comment saying "correct range". It has no effect of its own, and the mechanism it refers to is not shown.
19. **Mount naming hides the cost.** `makeMountPermanent()` names the clone, which is what remains, but not that the original animal is destroyed or killed (lines 118-136). The "buck" check (`:190`) depends on `Skill_Buck` setting `NO_AUTO_BUCK` at purchase (`com/planet_ink/coffee_mud/Abilities/Skills/Skill_Buck.java:162`), which is outside this bucket.
20. **Possible null in mounted tactics.** `com/planet_ink/coffee_mud/Abilities/Fighter/Fighter_MountedTactics.java:64-67` calls `R.racialCategory()` on the rider's race without a null check. Whether the race can be null is unverified.
21. **Heavy dependence on other characters.** Lucky Vanity needs visible inhabitants (`com/planet_ink/coffee_mud/Abilities/Fighter/Fighter_LuckyVanity.java:103-107`); Intimidate needs an attacker in the room (`com/planet_ink/coffee_mud/Abilities/Fighter/Fighter_Intimidate.java:111`); Faithful Mount needs group members (`com/planet_ink/coffee_mud/Abilities/Fighter/Fighter_FaithfulMount.java:99-125`); Mounted Retreat checks enemies (`com/planet_ink/coffee_mud/Abilities/Fighter/Fighter_MountedRetreat.java:158-178`). In a single-player game these are inert unless NPC companions exist.

## Takeaways for a Python SUD

**Proficiency check as one function.** The two-stage gate is simple to port and worth keeping:

```python
def proficiency_check(skill, level, adj=0, rng=random):
    chance = clamp(skill.prof + 2 * level, 5, 95) + level // 2
    if adj >= 0:
        chance += adj
    elif rng.randint(1, 100) > 100 + adj:  # gate for negative modifiers
        return False
    return rng.randint(1, 100) < chance
```

Use the same signature for every skill, and put the modifiers (DEX, STR, level gap) in the calling code, so each skill reads as one line.

**One maneuver table.** About two dozen of the 56 skills are strike or hold skills that differ mainly in data. Put them in a single table (YAML or Python dicts) with these fields: trigger words, requirements (hands, legs, arms, head, eyes, weapon class, range, standing), modifier formula, strike type (natural, weapon, unarmed), crit bonus, damage formula, and status (kind and duration in turns). Dazed, blinded, pinned, dismounted and exhausted then become status names rather than classes. Keep the classes only where behaviour changes: the grapple pair, the mount, the flurry, and the passives.

**Grapples as a pair of objects.** A `Hold` object on each combatant, pointing at the other, with a shared struggle counter and the break contest from `com/planet_ink/coffee_mud/Abilities/Fighter/FighterGrappleSkill.java:103-123`. Single-player: the NPC is the victim, so the struggle runs on the NPC's turn. The victim's break formula is worth keeping as written.

**Mounts as state.** A `Mount` object with a rider, a mount, a favoured-race list and a dismount state. The planar trick of cloning an animal's generic stats into a vehicle is a good way to make tameable companions out of ordinary creature data.

**Plane or place memory with game-time expiry.** Store visited places as a dict of name to game-time expiry, rebuild bonuses when it changes, and expire them in the world tick. Use game time, not wall-clock time (see Problems and the `PlanarVeteran` expiry at `com/planet_ink/coffee_mud/Abilities/Fighter/Fighter_PlanarVeteran.java:220-221`).

**Terrain data.** Use a dict from terrain name to bonuses, not five classes. The five terrain subclasses in this bucket are the same class with a different domain constant.

**Racial mounts as data.** The 20-row table is ready-made content: a TOML or CSV file with race, mount category and mount race. The favoured-mount choice is then a random pick that excludes ones already chosen.

**Expertise.** Use one integer per skill for expertise, with the three uses kept separate (power, duration, range). Range at 20% per level is a single formula.

**Weapon grip.** Model it as `grip: one_hand | two_hand` on the weapon, with monkey grip changing the grip and applying the penalty `0.75 - (0.5 + 0.05 * xl) * prof`.

**Drop or rethink for a single-player game.**
- Intimidate, Lucky Vanity and Faithful Mount depend on other characters. Rethink them around companions or drop them.
- Mounted Retreat and Forceback are useful as flee and push actions. Keep them in simplified form.
- Far Shot and Point Blank should become data (`min_range`, `max_range` on the weapon), not hidden bits in the armour value.
- Do not copy the `armor()` bit-packing, the duplicated strike classes, or the dead flags listed under Problems.

## Coverage

**Read in full (class bodies, license headers skipped), all 56 bucket files:**

- The two in-bucket base classes, `com/planet_ink/coffee_mud/Abilities/Fighter/Fighter_FieldTactics.java` and `com/planet_ink/coffee_mud/Abilities/Fighter/Fighter_FavoredMount.java`, in full.
- Every other bucket file, from the class declaration to the end.
- `com/planet_ink/coffee_mud/Abilities/Fighter/Fighter_FavoredMount2.java`, `3` and `4` (53 lines each, read in full).
- The terrain tactics files `com/planet_ink/coffee_mud/Abilities/Fighter/Fighter_HillsTactics.java`, `com/planet_ink/coffee_mud/Abilities/Fighter/Fighter_JungleTactics.java`, `com/planet_ink/coffee_mud/Abilities/Fighter/Fighter_MountainTactics.java` and `com/planet_ink/coffee_mud/Abilities/Fighter/Fighter_PlainsTactics.java` were checked by diff against `com/planet_ink/coffee_mud/Abilities/Fighter/Fighter_ForestTactics.java`. The diffs showed only the name and the domain constant differ.

**Read from outside the bucket, for the mechanics it depends on:**

- `com/planet_ink/coffee_mud/Abilities/Fighter/FighterSkill.java` (100 lines, full).
- `com/planet_ink/coffee_mud/Abilities/Fighter/FighterGrappleSkill.java` (502 lines, full).
- `com/planet_ink/coffee_mud/Abilities/Fighter/MonkSkill.java` (57 lines, full).
- `com/planet_ink/coffee_mud/Abilities/StdAbility.java`: `proficiencyCheck` (1441-1485), `getXLEVELLevel` and related expertise getters (444-466), `adjustedMaxInvokerRange` (482-490), `getMaliciousTickdownTime` (2174-2183), `maliciousAffect` (2185-2201), `maliciousFizzle` (2253).
- `com/planet_ink/coffee_mud/Libraries/CoffeeUtensils.java`: `steedSkills` (3444-3455), `getFavoredMounts` (3459-3491).
- `com/planet_ink/coffee_mud/Abilities/Fighter/Fighter_RacialMount.java`: `getRacialMountChoices` and `getMountInfo` (lines 143-208, partly).
- `com/planet_ink/coffee_mud/Abilities/Skills/Skill_Buck.java`: `NO_AUTO_BUCK` assignment (162, 175-178), partly.
- `com/planet_ink/coffee_mud/Libraries/Dice.java` (`roll` 398-407, `rollPercentage` 409-412), `com/planet_ink/coffee_mud/Libraries/MUDFight.java` (`rollToHit` 409-421), `com/planet_ink/coffee_mud/Items/interfaces/Weapon.java` (range masks 183-197), `com/planet_ink/coffee_mud/Items/interfaces/Wearable.java` (229-231), `com/planet_ink/coffee_mud/Items/Weapons/StdWeapon.java` (maxRange, 397-401), `com/planet_ink/coffee_mud/Common/DefaultPhyStats.java` (speed default, line 63), `com/planet_ink/coffee_mud/core/CMProps.java` (681, 2720, 2997, 3094), `com/planet_ink/coffee_mud/Locales/interfaces/Room.java` (domain constants, 83-103), `resources/skills/racialmounts.txt` (full table).

**Checked by existence only (not read):** `com/planet_ink/coffee_mud/Abilities/Fighter/Fighter_Ridethrough.java`, `com/planet_ink/coffee_mud/Abilities/Spells/Spell_Deafness.java`, `com/planet_ink/coffee_mud/Abilities/Diseases/Disease_Migraines.java`, `com/planet_ink/coffee_mud/Items/Weapons/ShieldWeapon.java`, `com/planet_ink/coffee_mud/Abilities/Misc/BrokenLimbs.java`, `com/planet_ink/coffee_mud/Abilities/Misc/Injury.java`, `com/planet_ink/coffee_mud/Abilities/Misc/Amputation.java`, `com/planet_ink/coffee_mud/Abilities/Skills/Skill_LongReach.java` (grep only for the range mask).

**Skipped:** the license headers and import blocks of every file. The `CMLib` service classes behind `CMLib.combat().postAttack`, `postWeaponAttackResult`, `CMLib.tracking().wanderAway` and `CMLib.threads().scheduleRunnable` were not traced; their behaviour is described only as far as the call sites show.
