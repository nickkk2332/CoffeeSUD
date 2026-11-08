# Prayers, part 2: blessings, curses, healing and harm, corpses and auras

> Part of the CoffeeMud NEWOVERVIEW. Bucket: `b068` (Abilities/Prayers [part 2/8: Prayer_Bless .. Prayer_DarkeningAura]) · 53 files · 8696 lines of Java.

Citation convention: paths are relative to the CoffeeMUD folder, from the package root `com/planet_ink/coffee_mud/`. So `Abilities/Prayers/Prayer_Bless.java:154` means `com/planet_ink/coffee_mud/Abilities/Prayers/Prayer_Bless.java` line 154. Files outside this bucket are marked "(outside bucket)". Numbers were verified by reading the code, not by running the game; worked examples are computed from the formulas.

## Summary

The 53 prayers in this bucket are `StdAbility` subclasses that extend the shared base `Prayer` (`Abilities/Prayers/Prayer.java:37`). Each declares a class code (`ACODE_PRAYER` plus a `DOMAIN_*` such as BLESSING, CURSING, HEALING or VEXING), a quality (from malicious to beneficial) and a faith flag (holy, unholy or neutral). Together they cover sticky blessings and curses, a ladder of cure and cause wounds spells, fatigue and exhaustion, lasting afflictions (disease, hunger, blindness, mind curses), room wards and a moon that reanimates corpses, corpse and undead logistics, and utilities such as food, water and cleansing. For a text game the individual spells matter less than the engine they share: a target check, a proficiency roll, a caster level taken from data, tick-counted durations, and a message pipeline in which listeners can veto or reduce an effect. Most of the reusable design is in that engine.

## Key files and types

| File | Role | Lines |
|---|---|---|
| `Abilities/Prayers/Prayer.java` | Base class: faith gate on every prayer, relic and infusion helpers | 432 |
| `Abilities/Prayers/Prayer_Bless.java` | Blessing domain: stat buff, curse removal, overwrite rules, shared helpers `getSomething`, `endLowerCurses` | 258 |
| `Abilities/Prayers/Prayer_Curse.java` | Curse counterpart; strips blessings | 211 |
| `Abilities/Prayers/Prayer_CurseItem.java` | Sticky curse on items (`okMessage` vetoes removal, drop and throw) | 227 |
| `Abilities/Prayers/Prayer_BlessItem.java` | Item blessing; armor on mobs scales with level | 190 |
| `Abilities/Prayers/Prayer_Cannibalism.java` | Hunger and thirst affliction; NPC behaviour in `tick` | 245 |
| `Abilities/Prayers/Prayer_Contagion.java` | Proximity disease that copies the carrier's malicious effects | 204 |
| `Abilities/Prayers/Prayer_Bloatbomb.java` | Kills a target and leaves the corpse as an acid trap (`Trap`) | 281 |
| `Abilities/Prayers/Prayer_BoneMoon.java` | Room effect that reanimates a corpse every tick | 212 |
| `Abilities/Prayers/Prayer_BloodMoon.java` | Victim takes extra damage | 155 |
| `Abilities/Prayers/Prayer_BlessedHearth.java`, `Prayer_BloodHearth.java`, `Prayer_ConsecrateLand.java` | Room wards that edit or block messages | 182, 174, 145 |
| `Abilities/Prayers/Prayer_CureLight.java` to `Prayer_CureCritical.java`; `Prayer_CauseLight.java` to `Prayer_CauseCritical.java` | The healing and harm ladder | 124 to 142 each |
| `Abilities/Prayers/Prayer_CurseMetal.java`, `Prayer_CurseFlames.java`, `Prayer_ChainStrike.java`, `Prayer_DarkHammer.java` | Area and damage-over-time spells | 233, 173, 159, 154 |
| `Abilities/Prayers/Prayer_CurseMind.java`, `Prayer_CurseMinds.java`, `Prayer_CurseLuck.java`, `Prayer_BolsterPoison.java` | Lasting debuffs | 156, 157, 182, 160 |
| `Abilities/Prayers/Prayer_Bury.java`, `Prayer_CorpseWalk.java`, `Prayer_CallUndead.java`, `Prayer_CreateIdol.java` | Corpse and undead logistics; the idol item | 155, 189, 151, 214 |
| `Abilities/Prayers/Prayer_CureBlindness.java`, `Prayer_CureDeafness.java` | Cure by probing the effect list | 152, 148 |
| `Abilities/Prayers/Prayer_Condemnation.java` | Raises legal warrant punishment | 122 |
| `Abilities/Prayers/Prayer_DailyBread.java`, `Prayer_Christen.java`, `Prayer_Calm.java` | Food taken from others; naming a caged creature; peace | 162, 187, 128 |
| `Abilities/StdAbility.java` (outside bucket) | Proficiency roll, caster level, tick durations, `maliciousAffect`, usage cost | 899-930, 1441-1490, 1916-2110, 2140-2208, 2273-2331, 2837-2860 |
| `Libraries/Dice.java` (outside bucket) | `roll(n, die, mod)` = n dice of `die` plus `mod` | 398-406 |
| `Libraries/CMAbleMap.java` (outside bucket) | Data-driven qualifying levels for abilities | 1139-1160 |
| `Libraries/Factions.java` (outside bucket) | `getAlignPurity`, distance from an alignment band | 873-905 |
| `Races/Undead.java` (outside bucket) | Turns unholy damage into healing for undead | 270-290, 340-355 |
| `Abilities/interfaces/Ability.java` (outside bucket) | Class code, domain, flag and quality constants | 675; 722-884; 950-1020; 1083-1093 |

## How it works

### 1. The base class and the invoke template

- `Prayer` returns `ACODE_PRAYER` from `classificationCode()` (`Prayer.java:86-89`, constant `Ability.java:675`). Trigger words are `PRAY` and `PR` (`Prayer.java:77`). Its defaults are `canTargetCode()` = `CAN_MOBS` (`Prayer.java:65-69`), `canAffectCode()` = 0 (`Prayer.java:59-63`) and `abstractQuality()` = `QUALITY_INDIFFERENT` (`Prayer.java:71-75`). Subclasses override some or all of these.
- Nearly every `invoke()` in the bucket follows one template: resolve the target with `getTarget`; call `super.invoke(...)`; roll `proficiencyCheck(mob, adj, auto)`; build a `CMMsg` with `CMClass.getMsg(...)` and send it through `mob.location().okMessage` and then `send`; apply the effect; on failure call `beneficialWordsFizzle` or `maliciousFizzle` with a line such as "<S-NAME> pray(s) for ..., but nothing happens."
- `StdAbility.invoke` (`StdAbility.java:1964` onward) runs first. A minimum cast wait is checked (`StdAbility.java:2053-2061`): when `minCastWaitTime()` is positive and the last cast was too recent, the cast is refused with "You need a second..." (under 1 s), "You need a few seconds..." (under 5 s) or "You need awhile..." text. Cure Light, Cure Serious, Cure Fatigue and Cure Exhaustion return `CMProps.getTickMillis()/2` (for example `Prayer_CureLight.java:70-73`), about 2 s at the default tick. Cure Critical has no wait (`Prayer_CureCritical.java`).
- Usage costs are then tested (`StdAbility.java:2050-2051`) and, for non-automatic casts, paid: mana, movement and hit points are reduced and a `MSG_PREINVOKE` message is sent (`StdAbility.java:2076-2082`). Prayers that set `overrideMana()` use fixed or percentage costs, for example `COST_PCT+50` for Bloat Bomb and Corpse Walk (`Prayer_Bloatbomb.java:90-93`, `Prayer_CorpseWalk.java:82-85`), `COST_ALL` for the two hearths (`Prayer_BlessedHearth.java:84-87`, `Prayer_BloodHearth.java:84-87`), and a flat 100 for Daily Bread (`Prayer_DailyBread.java:82-85`).

### 2. Success roll and caster level

- `proficiencyCheck` (`StdAbility.java:1441-1490`):
  1. Start from `proficiency()`, add `PROF` ability adjustments.
  2. Add `2 * XLEVEL` (`getXLEVELLevel`, `StdAbility.java:444-446`, via `expertise(...)`).
  3. Clamp to 5..95 unless `DIS955RULE` is disabled.
  4. Add `XLEVEL / 2`.
  5. If the adjustment argument is 0 or more, add it. If it is negative, fail outright when `d100 > 100 + adjustment`, before the normal test.
  6. Succeed when `d100 < chance`.
- Auto (scripted or item) casts succeed; for non-owners proficiency is set to 100 (`StdAbility.java:1441-1449`).
- The adjustment argument is how the bucket encodes difficulty:
  - Cannibalism and Blindness: `-(target WIS * 2)` (`Prayer_Cannibalism.java:221`, `Prayer_Blindness.java:147`). A high-wisdom target is harder to affect.
  - Curse Luck and Curse Mind: `-(target.level - (caster.level + XLEVEL))` (`Prayer_CurseLuck.java:160-161`, `Prayer_CurseMind.java:134-135`). A stronger target is harder to affect.
  - Daily Bread: `-25 * levelGap`, with the gap floored at 0 (`Prayer_DailyBread.java:97-100`).
  - Call Undead: the same level gap times 3 (`Prayer_CallUndead.java:124`).
- `adjustedLevel(caster, asLevel)` (`StdAbility.java:899-930`) is the caster level used for effect size and duration:
  1. Start from `CMLib.ableMapper().lowestQualifyingLevel(ID())` (the data-driven level for this ability).
  2. If `asLevel <= 0`: for monsters, or for player casters who qualify, add `qualifyingClassLevel - qualifyingLevel`; otherwise use `caster.level - lowest - 25`.
  3. Otherwise (`asLevel > 0`, from a scroll, item or trap) use `asLevel`.
  4. Clamp to at least the lowest level, add personal level adjustments, and return at least 1.
  5. Add `XLEVEL`, then apply each class's `classDurationModifier`.

### 3. Durations and the tick clock

- The default tick is 4000 ms (`core/CMProps.java:681`, `TIME_TICK = 4000`), overridable through `TICKTIME` (`CMProps.java:2720`). Each mob tick decrements `tickDown` and calls `unInvoke()` at zero (`StdAbility.java:2843-2860`).
- Beneficial duration, `getBeneficialTickdownTime` (`StdAbility.java:2312-2331`): `casterLevel * 4 + 40` ticks. If the caster is a possible abuser and the target is another mob, the caster level is first capped at the target level plus `EXPRATE`. `adjustBeneficialTickdownTime` (`StdAbility.java:2273-2281`) caps at `TICKSPERMUDDAY` and floors at 5.
- Malicious duration, `getMaliciousTickdownTime` (`StdAbility.java:2174-2183`): `round(casterLevel * 1.1) + 25` ticks. `adjustMaliciousTickdownTime` (`StdAbility.java:2140`) then scales the value by the level gap between target and caster against `EXPRATE`. The details past its first branch are unverified.
- A positive explicit `tickAdjustmentFromStandard` replaces the formula. Curse Mind and Curse Minds pass 15, so their effects last 15 ticks (`Prayer_CurseMind.java:147`, `Prayer_CurseMinds.java:143`), which is 60 s at the default tick.
- Worked examples (computed, not run): a level-10 Bless lasts 80 ticks (320 s); a level-10 Curse lasts 36 ticks (144 s); a level-10 Cure Light rolls 2d10+4 for 6 to 24 hit points.

### 4. Message pipeline: veto, resistance and effect

- Each effect is a `CMMsg` offered to the room's `okMessage` before `send`. A listener can veto (return false) or raise the message's `value()`, which here means "resisted or absorbed":
  - `maliciousAffect` sends a pre-check message and returns null when a listener set `value() > 0` (`StdAbility.java:2185-2208`).
  - Most malicious prayers apply their effect only when `msg.value() <= 0`, for example `Prayer_Curse.java:191`, `Prayer_Blindness.java:154`, `Prayer_Cannibalism.java:228`.
  - Damage is halved when resisted: `Prayer_CurseFlames.java:159-160`, `Prayer_ChainStrike.java:134-135`, `Prayer_DarkHammer.java:136-137`.
- Pure vetoes are `okMessage` overrides on the effect itself: CurseItem (`Prayer_CurseItem.java:123-164`), CreateIdol (`Prayer_CreateIdol.java:132-145`), CurseMetal (`Prayer_CurseMetal.java:110-127`), ConsecrateLand (`Prayer_ConsecrateLand.java:90-106`).
- The base `StdAbility.okMessage` always returns true (`StdAbility.java:2837-2839`).

### 5. The faith gate

- `Prayer.invoke` (`Prayer.java:422-430`) calls `super.invoke` and then `prayerAlignmentCheck` (`Prayer.java:372-420`).
- The gate applies only when all of these hold: the cast is not automatic; the caster is a player; the ability is owned by the caster; the ability is not appropriate to the caster's faction; and `flags() != 0`.
- Which band applies comes from the flags: holy only uses GOOD, unholy only uses EVIL, and neutral (both bits set) or no faith flag uses the midpoint (`Prayer.java:381-401`).
- `CMLib.factions().getAlignPurity` returns a distance from the band edge (`Libraries/Factions.java:873-905`). For GOOD it is `|pct - pct(top)|`, for EVIL `|pct(bottom) - pct|`, and for NEUTRAL the distance from the midpoint minus 10 (`Prayer.java:391-400`).
- The prayer passes if `d100 > basis` (`Prayer.java:403-404`). So a pure caster always passes, and the chance of disruption equals the caster's distance from the band.
- Messages: "The goodness of @x1 disrupts your prayer." (`Prayer.java:410`); "The evil nature of @x1 disrupts your prayer." (`Prayer.java:407`); neutral variants use "non-good" or "non-evil nature ... disrupts your thought." (`Prayer.java:412-416`).
- Order matters: `StdAbility.invoke` has already charged the usage cost (`StdAbility.java:2076-2078`) before `Prayer.invoke` runs this check (`Prayer.java:427`). A disrupted prayer still costs its mana.

### 6. Stat effects and who carries them

- Prayer effects override `affectPhyStats`, `affectCharStats` and `affectCharState`. For a mob, `recoverCharStats` applies the mob's own effects through `eachEffect` and every carried item through `eachItem` (`MOBS/StdMOB.java:943-944`; `eachItem` loops over `inventory`, `StdMOB.java:4579-4590`). An item's effect therefore applies to whoever carries it, not only to whoever wears it.
- Effects are applied in list order, from index 0 upward (`StdMOB.java:5282-5293`), so later effects win on conflicting writes.
- Stat numbers, with lines:

| Effect | Change to the affected mob | Source |
|---|---|---|
| Bless | armor `-(5 + 2*XL)`; disposition adds GOOD and BONUS; on items ability `+1` | `Prayer_Bless.java:95-101` |
| Bless Item | armor `-(5 + level/10 + 2*XL)` on mobs; ability `+1` on items | `Prayer_BlessItem.java:95-101` |
| Curse | armor `+(5 + 2*XL)`; disposition adds EVIL; on items ability `-1` | `Prayer_Curse.java:95-101` |
| Curse Item | armor `+(10 + 2*XL)`; disposition adds EVIL; on items ability `-1` | `Prayer_CurseItem.java:95-101` |
| Create Idol | armor `+(20 + 4*XL)`; attack `-(10 + 2*XL)`; STR capped at 3, DEX at 2, CON at 1; fatigued; movement set to 20 | `Prayer_CreateIdol.java:95-127` |
| Conviction (same god only) | attack `+ attack/(8 - min(XL,7))`; damage `+3 + min(XL,7)`; armor `-(20 + 3*min(XL,7))` | `Prayer_Conviction.java:104-109` |
| Curse Luck | every saving throw `-(100 + 10*XL)` | `Prayer_CurseLuck.java:112-117` |
| Curse Mind, Curse Minds | `SAVE_MIND` `-50` | `Prayer_CurseMind.java:118-122`, `Prayer_CurseMinds.java:112-116` |
| Blindness | `sensesMask` gains `CAN_NOT_SEE` | `Prayer_Blindness.java:90-100` |
| Blindsight | `sensesMask` gains `CAN_SEE_DARK`, loses `CAN_NOT_SEE` | `Prayer_Blindsight.java:104-108` |
| Dark Senses | `sensesMask` gains `CAN_SEE_DARK` | `Prayer_DarkSenses.java:84-89` |

### 7. Blessings and curses: overwrite rules and sticky items

- Blessing and curse are separate domains, so `CMLib.flags().domainAffects(target, DOMAIN_BLESSING)` lists and clears every active blessing (`Prayer_Bless.java:150`; `Ability.java:766`, `768`).
- A new blessing replaces older ones by the rules in `Prayer_Bless.endAllOtherBlessings` (`Prayer_Bless.java:148-160`). It removes any blessing whose lowest qualifying level is below the new one (`<`), any blessing placed by the same caster, any blessing cast on the caster's own self, and any blessing whose invoker is the target. Then `endLowerCurses` (`Prayer_Bless.java:173-182`) removes curses at or below the level, and `beneficialAffect` applies the new blessing (`Prayer_Bless.java:246-248`).
- Curse does the mirror: `endLowerBlessings` removes blessings at or below the level (`Prayer_Curse.java:146-155`), and `endLowerCurses` removes curses strictly below (`Prayer_Curse.java:162-171`). See section 14 for the asymmetry.
- The "level" is `CMLib.ableMapper().lowestQualifyingLevel(ID())` (`Prayer_Bless.java:154`, `246-247`). That reads the ability map (`Libraries/CMAbleMap.java:1139-1160`), so the balance between blessing tiers lives in data.
- Choosing an item, `Prayer_Bless.getSomething` (`Prayer_Bless.java:123-146`): carried items go into one list and worn, wielded or held items into another. The worn or held list is preferred, and `cursedOnly` filters by `isCursed`.
- Blessing a target (`Prayer_Bless.java:230-245`): for each cursed item, it sends a release message ("<S-NAME> release(s) <T-NAME>."), clears the item's curses and recovers stats. An `alreadyDone` set stops the loop when an item stays cursed.
- An item is cursed (`Prayer_Bless.java:192-202`) when it is not removable or not droppable (through `CMLib.flags()`), or when it carries a curse effect.
- CurseItem makes an item sticky (`Prayer_CurseItem.java:123-164`):
  - removing a worn or held item is refused ("You can't seem to let go of @x1." or "You can't seem to remove @x1.");
  - dropping and throwing it are refused ("You can't seem to get rid of @x1.").
  - CreateIdol uses the same idea by setting `setRemovable(false)` and `setDroppable(false)` (`Prayer_CreateIdol.java:200-201`).
- Targeting order, which mirrors each other: Curse Item tries a blessed item on the target first, then any item (`Prayer_CurseItem.java:186-193`). Bless Item tries a cursed item first, then any item (`Prayer_BlessItem.java:149-156`). Both fall back to the given target's own items.

### 8. The healing and harm ladder

Dice are `CMLib.dice().roll(n, die, mod)` (`Libraries/Dice.java:398-406`): n dice of `die` sides plus `mod`. `L` is `adjustedLevel`. Each healing call goes through `CMLib.combat().postHealing` (`Libraries/MUDFight.java:711-730`), which posts a `MSG_HEALING` message with the amount. Each harm call goes through `postDamage` with `TYP_UNDEAD` (`Prayer_CauseLight.java:121`).

| Prayer | Amount | Source | Range at L=10 | Mean at L=10 |
|---|---|---|---|---|
| Cure Light Wounds | `2d(L) + 4` HP | `Prayer_CureLight.java:128` | 6-24 | 15 |
| Cure Serious Wounds | `3d(L) + 5` HP | `Prayer_CureSerious.java:128` | 8-35 | 21.5 |
| Cure Critical Wounds | `4d(L) + 6` HP | `Prayer_CureCritical.java:111` | 10-46 | 28 |
| Cause Light Wounds | `1d(L+3) + 3` damage | `Prayer_CauseLight.java:120` | 4-16 | 10 |
| Cause Serious Wounds | `1d(L+6) + 4` damage | `Prayer_CauseSerious.java:120` | 5-20 | 12.5 |
| Cause Critical Wounds | `1d(L+12) + 5` damage | `Prayer_CauseCritical.java:110` | 6-27 | 16.5 |

- Light cure messages: "A faint white glow surrounds <T-NAME>." (auto) or "delivering a light healing touch"; the target gets "You feel a little better!" (`Prayer_CureLight.java:90-98`). Serious and Critical differ only in numbers and wording (diffed against Light).
- `CMLib.combat().postHealing(...)` is always posted; the target gets "You feel a little better!" only if HP rose (`Prayer_CureLight.java:129-132`).

### 9. Undead inversion

- Cure spells cast on undead set `MASK_MALICIOUS` on the cast message (`Prayer_CureLight.java`, `(!undead?0:CMMsg.MASK_MALICIOUS)`). Cause spells use a `TYP_UNDEAD` message (`Prayer_CauseLight.java:108-121`, `Prayer_CauseCritical.java:97-111`). Undead handling is in the race: `Races/Undead.java:270-290` converts unholy or `TYP_UNDEAD` damage into healing, with the text "The harming magic heals <T-NAME>.", and adds a retaliation trailer. So a Cause spell heals an undead target.
- What the malicious flag on an undead cure does downstream is outside this bucket and unverified.
- Cause Critical's undead branch only chooses a fizzle line (`Prayer_CauseCritical.java:116-120`).

### 10. Fatigue and exhaustion (movement, not HP)

- Cure Fatigue: heals movement by `3d(L) + 10` and halves current fatigue (`Prayer_CureFatigue.java:115-118`). Message: "You feel slightly more invigorated!"
- Cure Exhaustion: heals movement by `5d(L) + 20` and sets fatigue to 0 (`Prayer_CureExhaustion.java:115-117`).
- Cause Fatigue: removes `3d(L) + 10` movement and adds half the current fatigue, unless the race is `infatigueable` or `FATIGUE` is disabled (`Prayer_CauseFatigue.java:91-98`).
- Cause Exhaustion: removes `5d(L) + 20` movement and, if fatigue is at or below `FATIGUED_MILLIS`, sets it to `FATIGUED_MILLIS + 1` (`Prayer_CauseExhaustion.java:91-99`).

### 11. Area and damage-over-time spells

- Curse Metal (`Prayer_CurseMetal.java:130-168`). Each mob tick, for each carried item that is worn, wielded or held and is metal (`CMLib.flags().isMetal`): fire damage `1d(6 + XL + 2*X1) + 1` (`:153`). Then a percentage roll below STR drops the item (`:155-157`). If the mob no longer owns it, the effect is copied onto the item and the loop stops (`:158-164`). The copied effect's `okMessage` refuses any hands-level action on the item with "@x1 is filled with unholy heat!" (`:110-127`). The effect's own `tick` does nothing for non-mob hosts (`:136`). The curse therefore hops to the dropped object and makes it unholdable. An idle monster in the same room then attacks the invoker (`:166-168`). `unInvoke` removes up to three copies of the effect per affected item (`:186-196`). Duration comes from `adjustMaliciousTickdownTime(mob, target, adjustedLevel, asLevel)` (`:222`).
- Curse Flames (`Prayer_CurseFlames.java:82-171`). Needs a fire source: an item where `CMLib.flags().isOnFire` holds and which is not in a container, carried first, then in the room (`:107-131`). Range is 5 (`:82-85`). The fire source attacks the target for `1d(2*L) + (2*X1 + 1)` fire damage (`:158`), halved if resisted. The fire source is then destroyed (`:165`).
- Chain Strike (`Prayer_ChainStrike.java:76-157`). Lightning jumps across `properTargets` within range 2 (`:66`). Each hit deals the same starting damage `1d(L/2) + 1 + 2*X1` (`:90`) and the next hit deals half (`:139`). It stops when the damage drops below 2 (`:140-143`). Dead or departed targets are skipped (`:105-120`).
- Hammer of Darkness (`Prayer_DarkHammer.java:109-152`). Only good-aligned targets are harmed (`:121-122`); otherwise the cast fizzles. Damage is `2d(L+5) + L` (`:135`), halved if resisted (`:136-137`) and doubled if the target is undead (`:114`, `:138-139`). The message names a random weapon of the caster's deity (`getWeaponName`, `:89-107`).
- Bloat Bomb (`Prayer_Bloatbomb.java:244-278`, trap `:96-241`). The target dies outright if `target.level < (caster.level + XL) / 2` (`:255`, `:261`). The body gets this effect (`:272`). When the body is opened, got, given, pushed, pulled, locked, unlocked, or acted on generally, `spring()` runs (`:113-125`). Every occupant outside the caster's group takes `4d(5 + invoker level)` acid damage (`:230-231`) and the body is destroyed (`:236-239`). The death message is "explodes, spraying clumps of stomach acid everywhere!" (`:223`). If no body is found, "The death did not appear to create a body!" (`:270`).

### 12. Lasting afflictions

- Contagion (`Prayer_Contagion.java:129-170`). A proximity disease (`DiseaseAffect.SPREAD_PROXIMITY`, `Abilities/interfaces/DiseaseAffect.java:46`). Each tick, if the room has another inhabitant (`:139`), the carrier picks a random inhabitant (`:156`) and a random malicious spell or prayer it carries (`:142-157`). The victim is infected if `d100 > disease save` (`:160`), receiving a copy of Contagion and of the chosen effect (`:162-164`). Otherwise the victim gets `spreadImmunity`, a `TemporaryImmunity` keyed to the prayer ID (`:166-167`, `StdAbility.java:2351-2365`).
- Cannibalism (`Prayer_Cannibalism.java:106-244`). For a player:
  - eating same-race flesh food raises hunger by its nourishment and thirst by twice that (`:114-118`);
  - eating other food lowers hunger by its nourishment (`:121`), so ordinary food makes the affliction worse;
  - drinking lowers thirst (`:127`).
  - Each awake tick costs 2 hunger and twice the room's thirst rate (`:156-157`).
  - For NPCs with hunger or thirst at or below zero (`:158-161`): eat a matching flesh food if there is one (`:174-190`); otherwise butcher a same-race corpse through the "Butchering" ability (`:193-198`); otherwise 10% chance to set a same-race inhabitant as victim (`:199-205`).
  - On invoke, hunger and thirst are set to 0 (`:232-234`) and the effect is applied (`:235`).
- Blindness (`Prayer_Blindness.java:90-165`): sets `CAN_NOT_SEE` (`:98`). It refuses targets with no eyes (`:138`). The message on the victim is "<S-NAME> go(es) blind!" (`:156`). Unwinding gives "Your vision returns." (`:112`).
- Curse Mind (`Prayer_CurseMind.java:86-154`) and Curse Minds (`Prayer_CurseMinds.java:80-155`):
  - SAVE_MIND `-50` for 15 ticks (see section 3).
  - While the victim is in combat, each tick it is switched to a random room inhabitant (`Prayer_CurseMind.java:93-99`, `Prayer_CurseMinds.java:85-94`), so the victim fights whoever is nearby.
  - Curse Minds hits every proper target except the caster (`Prayer_CurseMinds.java:124-146`), with "look(s) confused!" on success (`:144`).
- Curse Luck (`Prayer_CurseLuck.java:112-180`). `okMessage` does not veto anything. Each saving-throw or trap message aimed at the victim spends one of four `prots`, each tool counted once through `permProts` (`:119-148`). With the message "<S-YOUPOSS> cursed luck trips!" (`:142`). At zero the curse ends: "Your cursed luck fades." (`:108`). The curse is therefore a count of triggers, with a timer as backup.
- Bolster Poison (`Prayer_BolsterPoison.java:89-158`). Each mob tick adds 1 to the remaining `TICKDOWN` of every poison effect on the target, recursing through spell holders to depth 3 (`:89-107`). `StdAbility.tick` decrements `tickDown` once per tick (`StdAbility.java:2843-2860`), so the net effect is roughly zero per tick, depending on ordering (unverified).
- Blood Curse (`Prayer_BloodMoon.java:105-153`). On a `TYP_DAMAGE` aimed at the victim, the damage value increases by `max(1, round(value/10))` (`:117-120`). This is a roughly 10% damage amplifier; the variable name `recovery` is misleading.
- Darkening Aura (`Prayer_DarkeningAura.java:104-166`) and Brightening Aura (`Prayer_BrighteningAura.java:104-160`). Both multiply the holder's own qualifying prayer output by 1.5:
  - Darkening: `TYP_DAMAGE`, tool is an unholy prayer that is not holy, and the ability map lists the tool (`:112-120`).
  - Brightening: `TYP_HEALING`, tool is a prayer, and the ability map lists the tool (`:111-119`).
  - Both apply only to non-monsters and only to tools the holder owns.
  - The ability map check is `CMLib.ableMapper().getQualifyingLevel(ID(), true, toolID) > 0` (`Libraries/CMAbleMap.java:1139-1160`), so the set of boosted abilities is data.

### 13. Cures and dispels

- Cure Disease (`Prayer_CureDisease.java:87-164`). `returnOffensiveAffects` lists every `DiseaseAffect` on the target (`:87-101`). Each is removed unless its `difficultyLevel() * 10 > adjustedLevel + abilityCode`, and unless the caster invoked it (`:141-148`). `abilityCode` is set per instance (`:72-75`). A failed removal produces "had diseases too powerful for <S-YOUPOSS> magic" (`:151-152`). Contagion's difficulty is 0 (`Prayer_Contagion.java:90-93`), so it can always be cured.
- Cure Vampirism (`Prayer_CureVampirism.java:92-121`) removes `Prayer_Vampirism` and `Disease_Vampirism` by name. Cure Cannibalism (`Prayer_CureCannibalism.java:92-118`) removes `Prayer_Cannibalism` by name.
- Cure Blindness (`Prayer_CureBlindness.java:95-101`) and Cure Deafness (`Prayer_CureDeafness.java:93-98`) probe rather than list. For each effect on the target, they:
  1. create a factory mob and let the effect's `affectPhyStats` run on it;
  2. treat the effect as offensive if the probe cannot see (or hear), or if the effect's `okMessage` rejects a LOOK message;
  3. count it only if the invoker's level is at most caster level + 1 + 2*XL.
  Any new effect that blocks sight or hearing is cured without a list.
- Supports-mending probes (`supportsMending`) build the factory caster at the game's last player level (`CMProps.Int.LASTPLAYERLEVEL`).

### 14. Rooms: moons, wards and the consecrated floor

- Bone Moon (`Prayer_BoneMoon.java:89-210`):
  - A `level` field starts at 1 (`:89`, reset at cast `:196`).
  - Each tick (`:107-139`), it finds the first non-player corpse in the room, reanimates it through `Prayer_AnimateSkeleton.makeUndeadFrom(room, body, race, invoker, level)` (`:128-131`), destroys the body (`:133`) and raises `level += 3` (`:134`). Each corpse produces a stronger undead than the last.
  - Refused if the room already has this moon ("This place is already under a bone moon." `:168-171`) or any moon-domain effect ("The moon is already under @x1, and can not be changed until this magic is gone." `:173-181`).
  - Owned land makes the effect permanent through `addNonUninvokableEffect` and `DBUpdateRoom` (`:197-200`); otherwise it is temporary (`:202`).
  - Cast message: "The Bone Moon rises over <S-NAME>." (`:195`).
- Blessed Hearth (`Prayer_BlessedHearth.java:96-180`). Stores the owner or clan name as misc text (`:163-169`). Its `okMessage`:
  - blocks `TYP_UNDEAD` messages aimed at privileged group members (`:102-119`): "The blessed powers block the unholy magic from <S-NAMESELF>." (`:114`);
  - divides damage to privileged group members by 10 (`:120-133`).
  - "Privileged" means `CMLib.law().doesHavePriviledgesHere`, or a name or clan role match to the stored text (`:109-112`, `:127-130`). Owned land is saved to the room (`:170-171`).
- Blood Hearth (`Prayer_BloodHearth.java:96-172`). Multiplies by 1.5 the damage dealt by privileged group members in the room (`:103-119`, `:114`). Castable only on owned land (`:123-133`).
- Consecrate Land (`Prayer_ConsecrateLand.java:90-143`). Blocks `CAST_SPELL` messages whose tool is a prayer with the UNHOLY flag and without the HOLY flag (`:95-100`), with "This place is blocking unholy magic!" (`:101`). The check reads the tool's flags, not its name.

### 15. Corpses, undead and idols

- Bury (`Prayer_Bury.java:77-153`):
  - The target must be an unworn `DeadBody`, not a fake, and not another player's corpse (`:87-97`). "You may only bury the dead." (`:90`).
  - A `HoleInTheGround` in the room that carries a thief's `Thief_BuriedTreasure` effect refuses the burial (`:98-107`).
  - Digs the hole (`MSG_DIG`, `:133-137`), hides it (`:139`), moves the corpse into it (`:141-143`) and tags the corpse with this effect (`:144`).
  - Neutral casters get `round(5) + XPCOST` XP once per corpse, unless they are more than `EXPRATE` levels above the corpse (`:125-131`).
- Corpse Walk (`Prayer_CorpseWalk.java:113-188`):
  - The caster names a corpse. Search runs over the area first, then the whole map (`:122`, `:129`), each with `timePct` 5 (`Libraries/interfaces/WorldHuntLibrary.java:170-177`). Player corpses are preferred (`:87-110`).
  - The caster and its followers, in `properTargetList` order with the caster first, are moved into the corpse's room (`:152-180`). Each one leaves with "sucked into the ground" and arrives with "emerge(s) from ..." (`:161-162`). A follower who cannot move is skipped; the caster failing aborts the rest (`:177-178`).
  - Costs `COST_PCT + 50` (`:82-85`).
- Call Undead (`Prayer_CallUndead.java:76-149`). Collects group members that are undead (`:80-86`), refuses if none ("You don't have any controlled undead!" `:89`) or all are in the room ("Better look around first." `:111`). Otherwise it calls the first undead found elsewhere to the caster with `Skill_Track` (`:134-140`). The adjustment is the level gap times 3 (`:124`).
- Create Idol (`Prayer_CreateIdol.java:147-212`):
  - Requires a worshipped god that exists (`:149-152`).
  - The material is a rock, mithril or metal resource from the room (`:157-182`), trying up to ten times the resource count from `resourceChoices`.
  - The item is a `GenItem` with base value 1 and weight 10, flagged IS_EVIL, not removable and not droppable, and carrying a copy of this prayer as a non-uninvokable effect (`:191-202`). It is placed with `Expire.Resource` (`:203`).
  - Effects are in section 6. The idol cannot be given to a mob that does not follow the giver (`:134-141`).

### 16. Law, food, water and social utilities

- Condemnation (`Prayer_Condemnation.java:82-120`). Looks up the room's `LegalBehavior` (`:84-86`) and the target's warrants (`:91-93`). On success and no veto, each warrant's punishment rises one step (`:107-111`) up to EXECUTE. The ladder is in `Common/interfaces/Law.java:615-637`: WARN (0), THREATEN (1), PAROLE1-4 (2-5), JAIL1-4 (6-9), EXECUTE (10, `PUNISHMENT_HIGHEST`).
- Daily Bread (`Prayer_DailyBread.java:88-160`):
  - Loose food on the target is taken first; food in a container is taken out with `postGet` (`:103-122`).
  - If the target has no food and is a shopkeeper, the first stored food is copied into the target's inventory (`:123-139`).
  - The target then gives the food to the caster with `MSG_GIVE` (`:150`), if neither message is vetoed (`:148`).
  - Pre-roll penalty `-25 * levelGap` (`:97-100`); costs 100 mana (`:82-85`).
- Christen (`Prayer_Christen.java:89-185`):
  - Takes the last command word as the new name (`:101`).
  - Target must be a `CagedAnimal` that is generic and has a positive ability value, which is a caged young creature (`:107-111`).
  - Refused for creatures with a `PARENTAGE:NPC` tattoo (`:115-124`), where the creature is destroyed and the cast returns success (`:118-122`).
  - Edits the cage template: name, display text and a "CHRISTENED" tattoo (`:166-175`).
  - Posts to the CHRISTENINGS channels (`:176-178`) and gives 5 XP (`:179`).
- Calm (`Prayer_Calm.java:76-126`). `castingQuality` rates it low for evil casters and, for neutral casters, half the time (`roll(1,2,0) == 1`, `:78-85`). This is a quality hint only; `invoke` has no such roll. If anyone in the room is fighting, every combatant gets "You feel at peace." and `makePeace(true)` (`:105-120`).
- Cleanliness (`Prayer_Cleanliness.java:78-191`). Removes the Soiled and Dusty effects from the room, from items on the floor, or from a mob, and sets hygiene to 0 for players (`:112-185`). The effect names are defined elsewhere.
- Create Food (`Prayer_CreateFood.java:82-106`). Drops a `StdFood` in the room: "Suddenly, @x1 drops from the sky." (`:95-98`).
- Create Water (`Prayer_CreateWater.java:82-128`). Fills a drink container with fresh water; refuses other liquids or a full container (`:87-101`); "@x1 fills up with water!" (`:119`).

## Content worth mining

### Catalogue (53 prayers, grouped by family)

Columns: prayer ID and display name; domain; quality; faith flag; target scope; lines. Target `mob` means `CAN_MOBS`; `none` means `canTargetCode` 0 (the spell works on the room, the caster or a named thing).

**Blessings and curses**

| ID | Display name | Domain | Quality | Faith | Target | Lines | What it does |
|---|---|---|---|---|---|---|---|
| Prayer_Bless | Bless | BLESSING | beneficial | holy | mob | 258 | Armor and GOOD/BONUS on mob; strips curses from cursed items |
| Prayer_BlessItem | Bless Item | BLESSING | beneficial | holy | mob or item | 190 | Blesses a carried or target item; level-scaled armor |
| Prayer_Curse | Curse | CURSING | malicious | unholy | mob | 211 | Armor and EVIL on mob; strips blessings |
| Prayer_CurseItem | Curse Item | CURSING | malicious | unholy | mob or item | 227 | Makes an item sticky |
| Prayer_CreateIdol | Create Idol | CURSING | indifferent | unholy | item created | 214 | Creates a sticky, stat-capping idol; needs a deity |
| Prayer_CurseLuck | Curse Luck | CURSING | malicious | unholy | mob | 182 | Saves down by 100+10*XL; ends after four triggers |
| Prayer_CurseMind | Curse Mind | CURSING | malicious | unholy | mob | 156 | SAVE_MIND -50; victim picks random targets in combat |
| Prayer_CurseMinds | Curse Minds | CURSING | malicious | unholy | room | 157 | Same, for every room target |
| Prayer_BloodMoon | Blood Curse | CURSING | malicious | unholy | mob | 155 | Victim takes about 10% more damage |
| Prayer_CurseMetal | Curse Metal | CURSING | malicious | unholy, heating | item or mob | 233 | Held metal burns, drops, and the curse hops to the dropped item |
| Prayer_CurseFlames | Curse Flames | CURSING | malicious | unholy | mob | 173 | Uses a fire source in the room or pack to strike |
| Prayer_Condemnation | Condemnation | EVANGELISM | ok others | unholy | mob | 122 | Raises warrant punishment one step |

**Healing, harm and fatigue**

| ID | Display name | Domain | Quality | Faith | Target | Lines | What it does |
|---|---|---|---|---|---|---|---|
| Prayer_CureLight | Cure Light Wounds | HEALING | beneficial | holy, healing | mob | 142 | 2d(L)+4 HP; half-tick cast wait |
| Prayer_CureSerious | Cure Serious Wounds | HEALING | beneficial | holy, healing | mob | 142 | 3d(L)+5 HP; half-tick cast wait |
| Prayer_CureCritical | Cure Critical Wounds | HEALING | beneficial | holy, healing | mob | 124 | 4d(L)+6 HP |
| Prayer_CauseLight | Cause Light Wounds | VEXING | malicious | unholy | mob | 134 | 1d(L+3)+3 damage |
| Prayer_CauseSerious | Cause Serious Wounds | VEXING | malicious | unholy | mob | 134 | 1d(L+6)+4 damage |
| Prayer_CauseCritical | Cause Critical Wounds | VEXING | malicious | unholy | mob | 124 | 1d(L+12)+5 damage |
| Prayer_CureFatigue | Cure Fatigue | RESTORATION | beneficial | holy | mob | 128 | 3d(L)+10 movement; halves fatigue; half-tick wait |
| Prayer_CureExhaustion | Cure Exhaustion | RESTORATION | beneficial | holy | mob | 127 | 5d(L)+20 movement; fatigue to 0; half-tick wait |
| Prayer_CauseFatigue | Cause Fatigue | VEXING | malicious | unholy | mob | 109 | 3d(L)+10 movement loss; fatigue up by half |
| Prayer_CauseExhaustion | Cause Exhaustion | VEXING | malicious | unholy | mob | 110 | 5d(L)+20 movement loss; sets fatigue |
| Prayer_DarkHammer | Hammer of Darkness | VEXING | malicious | unholy | mob (good only) | 154 | 2d(L+5)+L against good; doubled vs undead |
| Prayer_ChainStrike | Chain Strike | CREATION | malicious | neutral, electric | room | 159 | Chain lightning, halving per hop |
| Prayer_Bloatbomb | Bloat Bomb | VEXING | indifferent | unholy | mob (kills; body is trap) | 281 | Kills target; corpse bursts in acid when touched |

**Afflictions and auras**

| ID | Display name | Domain | Quality | Faith | Target | Lines | What it does |
|---|---|---|---|---|---|---|---|
| Prayer_Contagion | Contagion | CORRUPTION | malicious | neutral | mob | 204 | Proximity disease copying the carrier's malicious effects |
| Prayer_Cannibalism | Inflict Cannibalism | CURSING | malicious | unholy | mob | 245 | Hunger and thirst only same-race flesh satisfies |
| Prayer_Blindness | Blindness | CORRUPTION | malicious | unholy | mob | 167 | Sets CAN_NOT_SEE; WIS-based pre-roll |
| Prayer_Blindsight | Blindsight | COMMUNING | beneficial self | neutral | self or mob | 180 | Sight in darkness; overrides blindness |
| Prayer_DarkSenses | Dark Senses | COMMUNING | ok self | unholy | mob | 144 | CAN_SEE_DARK |
| Prayer_DarkeningAura | Darkening Aura | CORRUPTION | malicious | unholy | mob | 168 | 1.5x damage from owned unholy prayers |
| Prayer_BrighteningAura | Brightening Aura | HEALING | beneficial | holy | mob | 162 | 1.5x healing from owned prayers |
| Prayer_BolsterPoison | Bolster Poison | CORRUPTION | malicious | unholy | mob | 160 | Adds ticks to poison effects each tick |
| Prayer_Corruption | Corruption | EVANGELISM | ok others | unholy | mob | 114 | Shifts target's alignment down by 10d(L)+10*XL |
| Prayer_CureDisease | Cure Disease | RESTORATION | ok others | holy | mob | 166 | Removes diseases, unless too difficult |
| Prayer_CureBlindness | Cure Blindness | RESTORATION | ok others | holy | mob | 152 | Removes any sight-blocking effect (probe) |
| Prayer_CureDeafness | Cure Deafness | RESTORATION | ok others | holy | mob | 148 | Removes any hearing-blocking effect (probe) |
| Prayer_CureVampirism | Cure Vampirism | RESTORATION | ok others | holy | mob | 123 | Removes Vampirism effects by name |
| Prayer_CureCannibalism | Cure Cannibalism | RESTORATION | ok others | holy | mob | 120 | Removes Cannibalism by name |

**Rooms and wards**

| ID | Display name | Domain | Quality | Faith | Target | Lines | What it does |
|---|---|---|---|---|---|---|---|
| Prayer_BoneMoon | Bone Moon | MOONALTERING | malicious | unholy | room | 212 | Reanimates each corpse; +3 level each time |
| Prayer_BloodHearth | Blood Hearth | CORRUPTION | malicious | unholy | room | 174 | Privileged damage +50% |
| Prayer_BlessedHearth | Blessed Hearth | WARDING | indifferent | holy | room | 182 | Privileged damage /10; blocks undead effects |
| Prayer_ConsecrateLand | Consecrate Land | WARDING | indifferent | holy | room | 145 | Blocks unholy-only prayers in the room |
| Prayer_Calm | Calm | NEUTRALIZATION | beneficial | holy | room | 128 | Peace for all combatants if anyone is fighting |
| Prayer_Cleanliness | Cleanliness | NEUTRALIZATION | ok others | neutral | room, item or mob | 193 | Removes Soiled and Dusty |

**Corpses, undead and creation**

| ID | Display name | Domain | Quality | Faith | Target | Lines | What it does |
|---|---|---|---|---|---|---|---|
| Prayer_Bury | Bury | DEATHLORE | indifferent | neutral | item (corpse) | 155 | Buries a corpse in a hidden hole; XP once |
| Prayer_CorpseWalk | Corpse Walk | DEATHLORE | indifferent | unholy, transporting | none (named corpse) | 189 | Moves caster and followers to a corpse |
| Prayer_CallUndead | Call Undead | DEATHLORE | indifferent | unholy | none (group undead) | 151 | Calls a controlled undead to the caster |
| Prayer_Christen | Christen | EVANGELISM | beneficial | holy | caged young creature | 187 | Names it; channel announcement; 5 XP |
| Prayer_CreateFood | Create Food | CREATION | indifferent | neutral | none | 108 | Drops food in the room |
| Prayer_CreateWater | Create Water | CREATION | indifferent | neutral | drink container | 130 | Fills a container with fresh water |
| Prayer_DailyBread | Daily Bread | EVANGELISM | ok others | unholy | mob | 162 | Gets food from target or shop stock |

Domains and faith bits used here: BLESSING (`Ability.java:766`), CURSING (`:768`), HEALING (`:762`), VEXING (`:764`), EVANGELISM (`:770`), MOONALTERING (`:774`), WARDING (`:786`), DEATHLORE (`:788`), CORRUPTION (`:792`), RESTORATION (`:794`), NEUTRALIZATION (`:796`), CREATION (`:798`), COMMUNING (`:800`). Faith: HOLY `Ability.java:964`, UNHOLY `:966`, NEUTRAL = HOLY|UNHOLY `:968`. Quality: `Ability.java:1083-1093`.

### Short messages worth reusing

These are paraphrased or quoted briefly from the code:

- "Your aura of blessing fades." (`Prayer_Bless.java:119`); "The curse is lifted." (`Prayer_Curse.java:118`)
- "The Bone Moon rises over <S-NAME>." (`Prayer_BoneMoon.java:195`); "The bone moon fades." (`:101`)
- "The blessed powers block the unholy magic from <S-NAMESELF>." (`Prayer_BlessedHearth.java:114`)
- "This place is blocking unholy magic!" (`Prayer_ConsecrateLand.java:101`)
- "@x1 is filled with unholy heat!" (`Prayer_CurseMetal.java:123`)
- "You can't seem to get rid of @x1." (`Prayer_CurseItem.java:160`)
- "Your cursed luck fades." (`Prayer_CurseLuck.java:108`); "<S-YOUPOSS> cursed luck trips!" (`:142`)
- "Evil, vile thoughts fill your head." (`Prayer_Corruption.java:96`)
- "Suddenly, @x1 drops from the sky." (`Prayer_CreateFood.java:98`)
- "@x1 fills up with water!" (`Prayer_CreateWater.java:119`)
- "You feel at peace." (`Prayer_Calm.java:116`)
- "Your vision returns." (`Prayer_Blindness.java:112`); "Your natural vision returns." (`Prayer_Blindsight.java:100`)
- "The contagion fades." (`Prayer_Contagion.java:117`)
- "Your cannibalistic hunger fades." (`Prayer_Cannibalism.java:102`)
- "This prayer will not work on this previously used burial ground." (`Prayer_Bury.java:104`)
- "You may only christen a child." (`Prayer_Christen.java:111`)
- "You feel slightly more fatigued!" (`Prayer_CauseFatigue.java`)

### Law ladder

The punishment ladder in `Common/interfaces/Law.java:615-637` is a ready-made escalation scale: WARN, THREATEN, PAROLE1 to PAROLE4, JAIL1 to JAIL4, EXECUTE. Condemnation moves a warrant one rung per cast up to EXECUTE (`Prayer_Condemnation.java:107-111`).

## Notable design ideas

1. **One engine, many spells.** Most prayers are a few parameters (domain, quality, faith, dice, duration) plus one or two overrides (`invoke`, `affectPhyStats`, `okMessage`, `tick`). The engine handles targeting, cost, the success roll, the caster level and the tick timer.
2. **Resistance as a number on the message.** `msg.value()` carries "absorbed" from listeners, and the caster's effect checks `value() <= 0` (section 4). The same channel halves damage and blocks an effect, with no separate code path.
3. **Cure by probing.** Cure Blindness and Cure Deafness do not list the blinding effects. They run each effect's own stat modifier on a dummy mob and see whether it still can see or hear (section 13). New blinding effects are cured automatically.
4. **Faith as a distance.** Alignment is a continuous distance from a band, and a disruption chance equals that distance (section 5). This gives a smooth penalty for a near-miss rather than a switch.
5. **Wards as message filters.** The hearths and Consecrate Land change or block messages in a room, not a person (section 14). Any action in the room passes through the ward.
6. **Escalation in the Bone Moon.** Each reanimated corpse raises the level of the next (`Prayer_BoneMoon.java:134`), which gives the moon a natural, bounded escalation.
7. **A corpse as a trap.** Bloat Bomb attaches to a body and fires on eight kinds of interaction (`Prayer_Bloatbomb.java:115-125`), so the trap is a body property, not a room trigger.
8. **A curse that hops.** Curse Metal copies itself onto a dropped item, which then refuses to be held (`Prayer_CurseMetal.java:155-163`, `110-127`). The spread happens through the victim's own behaviour.
9. **Contagion copies behaviour.** The disease passes along a random malicious effect the carrier has (`Prayer_Contagion.java:142-164`), so one disease can carry many curses.
10. **Countdown by triggers.** Curse Luck ends after four saving-throw or trap events (`Prayer_CurseLuck.java:141-145`), not only by time.
11. **Self-positioning effects.** Blindsight re-inserts itself at the end of its owner's effect list on each tick when blindness applies (`Prayer_Blindsight.java:111-123`). Because effects apply in list order (`StdMOB.java:5282-5293`), it then wins over blindness.
12. **Data-driven levels.** Qualifying levels come from `CMAbleMap` (`Libraries/CMAbleMap.java:1139-1160`), so tuning blessing tiers or which prayers Darkening Aura boosts is a data change.
13. **Religion linked to law.** Condemnation reads the room's legal behaviour and warrants (`Prayer_Condemnation.java:84-111`), so a prayer can change a legal record.

## Problems and cruft

1. **Possible wrong target in Curse.** `Prayer_Curse.java:193-197` strips a blessing from an item the caster carries (`getSomething(mob, true)`), not from the target. It looks like a typo for `target`, though it may be intended. Unverified.
2. **Misleading variable in Blood Curse.** `Prayer_BloodMoon.java:117-120` names the damage increase `recovery`. The mechanic amplifies damage, so the name misleads.
3. **Blindsight placement conflict.** `invoke` inserts at the front of the list with `addPriorityEffect` (`Prayer_Blindsight.java:166-172`, `StdMOB.java:5256-5259`), so for one tick it applies before Blindness. The tick then moves it to the end (`Prayer_Blindsight.java:119-120`). The error corrects itself within one mob tick.
4. **Inverted dead branch in the auras.** `Prayer_DarkeningAura.java:106-107` and `Prayer_BrighteningAura.java:106-107` read `if(!super.okMessage(...)) return true;`. The base never returns false (`StdAbility.java:2837-2839`), so the branch is dead, and the pattern reads as if it lets a veto through.
5. **Dead save and restore.** `Prayer_ChainStrike.java:123-126` saves `auto`, builds two messages, and restores `auto` with nothing changed in between.
6. **Copy-count patch.** `Prayer_CurseMetal.java:186-196` removes up to three copies of its own effect per item, which suggests spreading can stack copies and the code guards against it with a fixed count.
7. **Costs paid before the faith gate.** The usage cost is paid in `StdAbility.invoke` (`StdAbility.java:2076-2078`), before `Prayer.invoke` disrupts the prayer (`Prayer.java:427`). A disrupted prayer still costs mana.
8. **Asymmetric level tests.** Blessings are replaced with `<` in `Prayer_Bless.java:154` and `:168`, and curses are removed with `<=` in `:179`. Curse uses `<=` for blessings (`Prayer_Curse.java:152`) and `<` for curses (`:168`). An equal-level blessing is replaced by Curse but not by Bless. Probably intended, unverified.
9. **Duplicated code.** `getSomething`, `endLower*` and privilege checks are repeated across Bless, Curse, the two hearths and the item variants. The three Cure ladder files differ only in numbers and text.
10. **Probe cost.** Cure Blindness and Cure Deafness allocate a factory mob for each check (`Prayer_CureBlindness.java:95`, `Prayer_CureDeafness.java:93`). It is correct but heavy.
11. **Cannibalism quirks.** Non-matching food lowers hunger (`Prayer_Cannibalism.java:121`) and flesh raises thirst at twice its nourishment (`:118`). These look deliberate, and the NPC branch depends on a "Butchering" ability found by name (`:195`).
12. **Refusal returns success.** `Prayer_Christen.java:118-122` returns true after refusing a creature, so the caster pays and is told it succeeded.
13. **Multiplayer assumptions.** Daily Bread takes food from any target, with only the target's own veto (`Prayer_DailyBread.java:150-151`). Christen announces on a channel. The hearths depend on land owner, clan and role (`Prayer_BlessedHearth.java:163-169`). Corpse Walk drags followers. Call Undead assumes a player group. Bury pays neutral casters for burying others' dead.
14. **Cross-system coupling.** Bury depends on a thief's hole marker (`Prayer_Bury.java:101`). Call Undead depends on `Skill_Track` (`Prayer_CallUndead.java:136`). Bone Moon depends on `Prayer_AnimateSkeleton` (`Prayer_BoneMoon.java:131`).

## Takeaways for a Python SUD

**Borrow**

- The effect shape: a timed object with `on_tick`, optional stat modifiers, and `ok_message` and `on_message` hooks. This maps to the `okMessage`/`executeMsg` pattern with a veto and a resistance number (section 4).
- The duration formulas, expressed as ticks: beneficial `level*4 + 40`, malicious `round(level*1.1) + 25`. These are in the same units the SUD will use for turns or seconds.
- The dice helper, mirroring `Dice.roll(n, die, mod)` (`Libraries/Dice.java:398-406`), and the cure/cause ladder as data (section 8).
- Probing cures (section 13): find any effect that blocks a sense by asking the effect, not by naming it.
- Room wards as message filters (section 14), and the corpse trap (section 15).
- A data table for the ability map: qualifying levels by ability ID, read at run time (`CMAbleMap`).

**Simplify**

- Collapse the 53 prayers into about ten effect kinds: `heal(dice)`, `harm(dice)`, `timed_stat(stat, delta)`, `timed_flag(flag)`, `sticky_item`, `aura(multiplier, filter)`, `room_ward(filter, action)`, `corpse_op(op)`, `reanimate`, and `cleanse(filter)`. Each prayer is then one data row: name, domain, quality, faith, target, dice, duration and effect kind.
- Keep the success roll as one function (section 2) and drop the per-prayer proficiency code.

**Drop**

- Gen objects, zapper masks, database writes, channel announcements, and factory-mob probes (write the probe as a pure function over the effect's stat output).
- Clan, owner and role privilege. A single-player game can use "owns this room" as a single flag, or drop it.

**Rethink for single player**

- Faith becomes one player alignment or per-patron faction. The distance-based disruption (section 5) still works and gives a useful near-miss penalty.
- "Group" becomes companions. "Other players" become NPCs, which matters for Daily Bread, Corpse Walk and Call Undead.
- The channel and XP events become journal or log entries.

A sketch of the core, not tested:

```python
import random
from dataclasses import dataclass

def roll(n, die, mod=0):                 # mirrors Libraries/Dice.java:398-406
    return sum(random.randint(1, die) for _ in range(n)) + mod

def beneficial_ticks(caster_level):      # StdAbility.java:2312-2331
    return caster_level * 4 + 40

def malicious_ticks(caster_level):       # StdAbility.java:2174-2183
    return round(caster_level * 1.1) + 25

def cast_succeeds(prof, xl, adjust=0):   # StdAbility.java:1441-1490
    chance = max(5, min(95, prof + 2 * xl)) + xl // 2
    if adjust < 0 and random.randint(1, 100) > 100 + adjust:
        return False
    chance += max(adjust, 0)
    return random.randint(1, 100) < chance

@dataclass
class Prayer:
    name: str
    domain: str                # "healing", "cursing", ...
    quality: str               # "beneficial" or "malicious"
    faith: str                 # "holy", "unholy", "neutral"
    effect: str                # "heal", "harm", "timed_stat", ...
    dice: tuple = (2, 0, 4)    # (n, die-base, mod); die-base scaled by caster level

def apply_message(listeners, msg):      # okMessage veto, then executeMsg
    if not all(l.ok_message(msg) for l in listeners):
        return None
    for l in listeners:
        l.on_message(msg)
    return msg
```

## Coverage

- **Read in full (after the license block), or in every method that matters, compacted:** `Prayer.java`; `Prayer_Bless`, `Prayer_BlessItem`, `Prayer_Curse`, `Prayer_CurseItem`, `Prayer_CurseMetal`, `Prayer_CurseFlames`, `Prayer_CurseLuck`, `Prayer_CurseMind`, `Prayer_CurseMinds`, `Prayer_Cannibalism`, `Prayer_Contagion`, `Prayer_Corruption`, `Prayer_Conviction`, `Prayer_Bloatbomb`, `Prayer_BoneMoon`, `Prayer_BloodMoon`, `Prayer_BolsterPoison`, `Prayer_Blindness`, `Prayer_Blindsight`, `Prayer_DarkSenses`, `Prayer_DarkeningAura`, `Prayer_BrighteningAura`, `Prayer_BlessedHearth`, `Prayer_BloodHearth`, `Prayer_ConsecrateLand`, `Prayer_CorpseWalk`, `Prayer_Bury`, `Prayer_Christen`, `Prayer_CallUndead`, `Prayer_CreateIdol`, `Prayer_Condemnation`, `Prayer_DailyBread`, `Prayer_DarkHammer`, `Prayer_ChainStrike`, `Prayer_Calm`, `Prayer_Cleanliness`, `Prayer_CreateFood`, `Prayer_CreateWater`, `Prayer_CureLight`, `Prayer_CureDisease`, `Prayer_CureVampirism`, `Prayer_CureCannibalism`, `Prayer_CauseFatigue`, `Prayer_CauseExhaustion`.
- **Read in their key methods only (invoke, tick, probe):** `Prayer_CureSerious` and `Prayer_CureCritical` (diffed against Cure Light), `Prayer_CureBlindness`, `Prayer_CureDeafness`, `Prayer_CureFatigue`, `Prayer_CureExhaustion`, `Prayer_CauseLight`, `Prayer_CauseSerious`, `Prayer_CauseCritical`.
- **Grepped for constants, flags, dice and effect lines, and the localized name:** all 53 files, to build the catalogue.
- **Outside the bucket, read for the mechanics used:** `Abilities/Prayers/Prayer.java` (already listed); `Abilities/StdAbility.java` (`adjustedLevel`, `proficiencyCheck`, `invoke`, `beneficialAffect`, `maliciousAffect`, tick durations, `tick`, `okMessage`, `startTickDown`, `properTargets`, `spreadImmunity`); `Libraries/Dice.java:398-406`; `Libraries/MUDFight.java:711-730` (`postHealing`); `Libraries/Factions.java:873-905`; `Libraries/CMAbleMap.java:1139-1160`; `MOBS/StdMOB.java:943-944, 4579-4590, 5256-5293`; `Races/Undead.java:270-290`; `Common/interfaces/Law.java:615-639`; `Libraries/interfaces/WorldHuntLibrary.java:170-177`; `Abilities/interfaces/Ability.java` constants; `Abilities/interfaces/DiseaseAffect.java:42-46`; `core/CMProps.java:681, 2720`.
- **Skipped:** the cross-referenced `Prayer_AnimateSkeleton`, `Skill_Track`, `Prayer_Sacrifice`, `Prayer_Vampirism`, `Disease_Vampirism`, `Butchering` and the protection effects (all outside this bucket).
