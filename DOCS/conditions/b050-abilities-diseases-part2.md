# Diseases, part 2: Leprosy to Zika

> Part of the CoffeeMud NEWOVERVIEW. Bucket: `b050` (Abilities/Diseases [part 2/2: Disease_Lepresy .. Disease_Zika]) · 34 files · 6898 lines of Java.

**Conventions.** Citations are relative to `com/planet_ink/coffee_mud/`, which is dropped from the path, so `Abilities/Diseases/Disease.java:168` means `com/planet_ink/coffee_mud/Abilities/Diseases/Disease.java:168`. Bare names that are not in `Abilities/Diseases/` use these folders: `StdAbility.java` = `Abilities/StdAbility.java`; `DiseaseAffect.java` = `Abilities/interfaces/DiseaseAffect.java`; `StdMOB.java` = `MOBS/StdMOB.java`; `Prop_CombatAdjuster.java` = `Abilities/Properties/Prop_CombatAdjuster.java`; `PhyStats.java` = `Common/interfaces/PhyStats.java`; `CharState.java` = `Common/interfaces/CharState.java`; `CMProps.java` = `core/CMProps.java`; `CMParms.java` = `core/CMParms.java`; `Dice.java` = `Libraries/Dice.java`; `CoffeeTime.java` = `Libraries/CoffeeTime.java`; `RawCMaterial.java` = `Libraries/RawCMaterial.java`; `RawMaterial.java` = `Items/interfaces/RawMaterial.java`; `DefaultTimeClock.java` = `Common/DefaultTimeClock.java`; `MUD.java` = `application/MUD.java`; `MUDFight.java` = `Libraries/MUDFight.java`; `coffeemud.ini` is at the CoffeeMud root. Tick arithmetic uses the shipped defaults: `TICKTIME=4000` (coffeemud.ini:2151), `MILLISPERMUDHOUR=600000` (coffeemud.ini:1099), `HOURSINDAY=6` (coffeemud.ini:1104), `DAYSINMONTH=24` (coffeemud.ini:1109). So one tick is 4 real seconds, one mud hour is 150 ticks (10 real minutes), one mud day is 900 ticks (60 real minutes), and one mud month is 21,600 ticks. `TICKSPERMUDDAY` is derived from these at startup (`DefaultTimeClock.java:313`). "Mud day" below means 900 ticks.

## Summary

These 34 classes are the second half of CoffeeMud's disease catalogue, all subclasses of the `Disease` base (part 1, bucket b049). Each is a `StdAbility` attached to a mob (Radiation Sickness can also ride on items) as a malicious effect that runs its own `tick()` on a countdown and hooks stat, state and message calls. The set covers stat-drain plagues (Malaria, Tetanus, Syphilis, Radiation Sickness, Mummy Rot), movement and balance (Vertigo, Royal Rot, Sea Sickness), speech and senses (Lockjaw, Muteness, Tinnitus), memory (Migraines, Lyme, Writers Block), compulsions (Sleepwalking, Narcolepsy, Tourettes, Yawning), and time-of-day or moon effects (Vampirism, Lycanthropy). The data-driven core (duration, period, spread channels, stat modifiers, cure rule, cascade) maps well onto Python data, but most spread logic assumes other people share the room, which a single-player game would have to replace.

## Key files and types

Base and helpers referenced (outside this bucket): `Abilities/Diseases/Disease.java` (410 lines, part 1), `Abilities/interfaces/DiseaseAffect.java` (spread bits, lines 42-56), `Abilities/StdAbility.java` (`maliciousAffect` 2185-2208, `getMaliciousTickdownTime` 2174-2183, `spreadImmunity` 2351-2365, empty `executeMsg` 2831-2834, `okMessage` 2837-2840), `core/CMProps.java` (tick helpers 3503-3542), `Common/DefaultTimeClock.java` (`TICKSPERMUDDAY`, line 313), `Libraries/CoffeeTime.java` (`homeClock` 1149-1168), `Libraries/Dice.java` (`roll` 398-406, `rollPercentage` 409-411).

| File (under `Abilities/Diseases/`) | Role | Lines |
|---|---|---|
| `Disease_Vampirism.java` | Light sensitivity, blood feeding, CHA modifier | 377 |
| `Disease_Lycanthropy.java` | Full-moon WereWolf transformation, hunting AI | 354 |
| `Disease_Tourettes.java` | Random yells every 8 ticks | 288 |
| `Disease_Obesity.java` | Fat counter driving weight, movement and display | 285 |
| `Disease_Nausea.java` | Vomiting: slime puddle, bystander infection | 279 |
| `Disease_PlanarInstability.java` | Stackable level: save loss, fizzles, XP changes | 267 |
| `Disease_RoyaltyRot.java` | Strength loss, fainting on exits, bleeding trigger | 260 |
| `Disease_MummyRot.java` | Melee-range touch, extends `StdAbility` directly, CON decay | 226 |
| `Disease_Vertigo.java` | Random falls, misdirected exit moves | 222 |
| `Disease_RadiationSickness.java` | Cumulative CON loss, lead shielding, item-carried | 213 |
| `Disease_PlanarPlague.java` | Level-ratcheting plague with stat and max-state penalties | 212 |
| `Disease_Malaria.java` | Daily CON loss, damage, fever cascade | 202 |
| `Disease_Lyme.java` | Ability forgetting, end-of-duration cascade | 199 |
| `Disease_Syphilis.java` | Stacking CON loss, divisor on max state | 193 |
| `Disease_SeaSickness.java` | Only on water, thirst, puking | 186 |
| `Disease_Migraines.java` | Per-ability memory loss, cached per instance | 182 |
| `Disease_Sleepwalking.java` | Walks to a random exit while asleep | 182 |
| `Disease_Narcolepsy.java` | Periodic fatigue, forced Sleep command | 181 |
| `Disease_Pneumonia.java` | Damage, always seeds Cold, STR and CON loss | 180 |
| `Disease_Lockjaw.java` | No eating or drinking, cannot speak | 178 |
| `Disease_Yawning.java` | Renewing yawns, not malicious | 176 |
| `Disease_SARS.java` | Damage with rare large spike, CON and STR loss | 175 |
| `Disease_Scurvy.java` | Halves CHA, cured by citrus or pepper food | 175 |
| `Disease_Smiles.java` | Contagious smiling, cascades to Giggles | 171 |
| `Disease_Magepox.java` | Mana drained by missing HP and MOVE | 165 |
| `Disease_Tetnus.java` | Cumulative DEX loss, death at DEX 0 | 163 |
| `Disease_Lepresy.java` | Leprosy: rewrites incoming damage text only | 162 |
| `Disease_Plague.java` | Damage, CON and DEX forced to 3 | 162 |
| `Disease_Tinnitus.java` | Per-tick chance of deafness | 158 |
| `Disease_WritersBlock.java` | Blocks writing; duration up to one mud month | 158 |
| `Disease_PoisonIvy.java` | CHA and DEX penalty, cosmetic | 157 |
| `Disease_Scabies.java` | Cosmetic, 3-4 mud days, sexual-contact spread | 144 |
| `Disease_Muteness.java` | Cannot speak, no cure roll | 134 |
| `Disease_Zika.java` | Non-insect max-CON adjustment (contains a bug) | 132 |

## How it works

### Lifecycle and the base class

- **Infection gate.** `Disease.invoke` runs `proficiencyCheck`, then sends a `CMMsg` with `TYP_DISEASE` through the room's `okMessage` (`Disease.java:347-368`). A `msg.value()<=0` result means the infection takes; otherwise `spreadImmunity` is applied instead.
- **Duration.** `DISEASE_TICKS()` is passed to `maliciousAffect` as the tick count. A positive value is used directly (`StdAbility.java:2174-2177`). A value of 0 falls back to a level-based formula, `round(adjustedLevel*1.1)+25` (`StdAbility.java:2178`). `Disease_MummyRot` passes 0 and so gets the level formula.
- **Period.** Each subclass keeps `diseaseTick` (`Disease.java:160`), decrements it in `tick()`, and resets it to `DISEASE_DELAY()` when it fires. The base class has no tick of its own.
- **Immunity after cure.** `unInvoke` calls `spreadImmunity` (`Disease.java:228-231`), which adds a `TemporaryImmunity` effect whose text is `+<disease ID>` (`StdAbility.java:2351-2365`). Its duration was not checked.
- **Per-target save.** `catchIt(mob,target)` (`Disease.java:168-205`) skips targets that already carry the effect, respects `DISEASE_REQSEE`, and infects only when `rollPercentage() > getSave(STAT_SAVE_DISEASE)` (line 186). Higher save means more resistance.
- **Spread by message kind.** `executeMsg` on a mob (`Disease.java:241-282`) checks, in order:
  - natural-weapon damage (`SPREAD_DAMAGE`, lines 248-257): a roll above save+70 infects, so roughly `30 - save` percent of unarmed hits;
  - contact moves and hand actions (`SPREAD_CONTACT`, lines 259-266);
  - the socials `MATE <T-NAME>` or `SEX <T-NAME>` (`SPREAD_STD`, lines 268-274);
  - speech within hearing range (`SPREAD_HEARING`, lines 276-281).
  Items use `SPREAD_CONSUMPTION` for drink and food (lines 296-313) and `SPREAD_GET` for pickup (lines 315-326).
- **Room spread from ticks.** Most tick-driven diseases call `catchIt(mob)` (`Disease.java:207-215`), which picks a random room inhabitant. This happens regardless of the bitmap. `SPREAD_PROXIMITY` (`DiseaseAffect.java:46`) is never tested by any code, so it has no effect.
- **Cascades at expiry.** Several diseases hand off to another on the last tick (see the catalogue). Examples: Smiles to Giggles (`Disease_Smiles.java:148-157`); Lyme to Fever, Amnesia or Arthritis at about 62.5, 25 and 12.5 percent (`Disease_Lyme.java:161-176`); Syphilis to Cold or Fever (`Disease_Syphilis.java:145-154`); Malaria, Pneumonia and SARS roll 1 percent for Fever (`Disease_Malaria.java:161-166`, `Disease_Pneumonia.java:147-152`) or a spike (`Disease_SARS.java:144-145`).

### Time model (what "ticks" means here)

Durations are written in ticks, so the same class behaves differently under different clock settings. The catalogue gives both. Key values with the defaults: 150 ticks per mud hour (`CMProps.java:3539-3542`), 900 ticks per mud day, 21,600 per mud month. Diseases that use `getTicksPerDay()` (`Disease.java:162-166`) read the mob's home clock, and `homeClock(null)` falls back to the global clock (`CoffeeTime.java:1167`), so the prototype calls made during `invoke` are safe.

### Stat hooks

- `affectCharStats` modifies the stats used for combat and checks (STR, CON, DEX, CHA, saves, weight adjustment).
- `affectPhyStats` sets senses (`CAN_NOT_SPEAK` 8192, `CAN_NOT_HEAR` 512, `CAN_SEE_DARK` 128; `Common/interfaces/PhyStats.java:411-423`), attack adjustment, armour, disposition.
- `affectCharState` modifies the **max** state (HP, MANA, MOVE), since `StdMOB.recoverMaxState` calls it with `maxState()` (`MOBS/StdMOB.java:1047-1063`).
- Armour convention (inferred from `Abilities/Properties/Prop_CombatAdjuster.java:83`, where 100 is the neutral point): lower is better, so `armor()+50` makes a target easier to hit. The combat formula (`Libraries/MUDFight.java:348-420`) was not fully traced.

### Per-disease mechanics

**Vampirism** (`Disease_Vampirism.java`). The disease is light-sensitive. In a lit room (`isInDark` false), attack adjustment drops by 50 and armour rises by 50 (lines 155-160). In a dark room the mob gains `CAN_SEE_DARK`. Examining things, casting at others and some other actions are vetoed in lit rooms unless the acting mob has `Spell_DarkSensitivity` (lines 267-295), with messages such as "You can't seem to make it out that well in this bright light." Drinking another mob's blood (lines 207-262) needs the target asleep, bound or charmed; otherwise the victim says no ("won't let you", line 223). A feed makes `1d(level)+4` units of blood (`roll(1,level,4)`, line 228). With `BLOODHEAL`, a healing message carries that amount (lines 346-354). The feed also causes justice-type natural damage of `1d(min(16,L)) + min(16,L)/4` (line 250), and the room's victims' targets are restored afterwards so the feed does not start a fight (lines 243-257). Nourishment is odd: blood food adds twice the amount eaten to thirst (line 341) and blood drink adds `prevEat/2` to hunger (line 344). Normal food and drink subtract the same amount that the item's own consumption adds (lines 361, 364), so they roughly cancel. Whether that cross-wiring is intended is unverified. The CHA modifier is read from misc text with default -1 (`setMiscText`, line 139), and the guard at line 373 only skips 0, so the default -1 is applied.

**Lycanthropy** (`Disease_Lycanthropy.java`). `DISEASE_TICKS` is 9,999,999, so the disease is effectively permanent. The shape-change happens when the area is at dusk or night and the moon is full (lines 322-323). It swaps the race to `WereWolf` (lines 182-186), changes the display name to "a <race> called <name>" or "<name> the <race>" (lines 168-171), and reverts when the condition ends (lines 338-349). When not in combat, each tick it can howl (15 percent, indoors, lines 247-249). It attacks a non-evil target that is `mayIFight` and whose level is below its own plus `EXPRATE` (`victimHere`, lines 194-211). `EXPRATE` is the experience-rate setting (`coffeemud.ini:406`, value 6), so this reuses an XP config as a level window. If no victim is in the room, it builds a trail back to one within depth 5 (`findVictim`, lines 213-236) using `TrackingLibrary.findTrailToAnyRoom` with limit 50 (line 272) and walks one step per tick.

**Radiation sickness** (`Disease_RadiationSickness.java`). Starts at CON -2 (`conDown=2`, line 127). Every 150 mud hours (`conTickBase`, 22,500 ticks, line 125) it adds 2 more, so the loss is cumulative (lines 139-148). At CON 0 or below the mob dies (lines 149-153). Each 8-tick period has a 5 percent chance to add Migraines, or Diarrhea if Migraines is already present (lines 157-174). The effect can ride on an item. The Uranium entry in the resource table names this disease (`Items/interfaces/RawMaterial.java:803`), and `RawCMaterial.addEffectsToResource` (lines 543-555) attaches it to uranium items. When an item is picked up, the item-side `SPREAD_GET` path infects the taker, but `invoke` first blocks the transfer if the item, any container holding it, or the taker's worn hand items are made of lead (lines 192-209).

**Planar instability** (`Disease_PlanarInstability.java`). Re-invoking the disease increments a level from 1 to 10 instead of stacking a second instance (`invoke`, lines 247-256). Effects by level: saves drop by `10*level` (line 166); a `PlanarAbility` cast fizzles when `rollPercentage() > 10*level` (line 205); experience gained is divided by level, and experience lost is multiplied by `1+0.1*level` (lines 230-233). The disease lasts 150 ticks, or 10 real minutes (`DISEASE_TICKS`, line 92).

**Nausea** (`Disease_Nausea.java`). Every 3d4+1 ticks (line 106) it vomits. It creates a slime puddle item, "a puddle of vomit", with a smell property (lines 195-212), moves followers and contained items out of the mob (lines 214-231, 159-183), and then, if `abilityCode` is 0, rolls each visible awake bystander at 20 percent (line 243). Targets that fail their save get a copy of the disease set to a 15-tick countdown (lines 245-251). Eating while full has a 2 percent chance (`roll(1,100)<3`, line 148) of starting Obesity.

**Obesity** (`Disease_Obesity.java`). The fat amount, from 0 up, is stored as misc text (`setMiscText`, lines 151-159). Weight adjustment is `baseWeight * fat%` (lines 170-172), and movement is cut by `fat%` with a floor of 20 when it was above 20 (lines 185-188). Display names are "Chubby" below 20, "Fat" below 60, "Obese" below 120, else "Morbid obesity" (lines 55-64). Eating while full adds `1d5` (lines 234-242). Fat falls by `1d10` when movement is under a tenth of max, and by `1d3` when the hunger tick counter reaches a mud hour. Both checks are throttled by wall-clock time of 10 seconds (`System.currentTimeMillis`, lines 197-212). At 0 fat the effect removes itself (line 226).

**Vertigo** (`Disease_Vertigo.java`). Each tick there is a 5 percent chance (`roll<6`, line 200) to fall for 3 ticks (line 211), sitting, which blocks standing (lines 146-148). On an exit move, a 7 percent chance (`roll<8`, line 151) redirects the move to a random other open exit, by changing the message target and tool (lines 161-174).

**Royal Rot** (`Disease_RoyaltyRot.java`). STR -5 (line 147). When moving through an exit, the faint chance is `pointsPerMove/5`, doubled when autorunning, percent (lines 168-169; `Math.random` is used here, not `Dice`). A faint sets `fallDown=3` (line 191). Damage at or above `INJBLEEDPCTHP/2` percent of max HP invokes `Bleeding` (lines 214-218). The default `INJBLEEDPCTHP` is 20 (`core/CMProps.java:3168`), so 10 percent triggers it. Bleeding's `TICKDOWN` is doubled once (line 243).

**Malaria** (`Disease_Malaria.java`). Each mud day adds one to `conDown` (lines 143-146). A cure check runs in the same tick: `roll<20` and `roll<save` (lines 147-152). Every 5 ticks it deals `2d(level+1)+1` (line 158), spreads (line 160) and rolls 1 percent for Fever (line 161). CON falls by `5+conDown` and the mob dies at 0 (`affectCharStats`, lines 180-190). Max movement is halved and max mana loses a third, and max HP drops by the mob's level (`affectCharState`, lines 198-200).

**Syphilis** (`Disease_Syphilis.java`). Each mud day has a 50 percent chance of adding to `conDown` (lines 143-144) and a 10 percent chance of seeding Cold or Fever (lines 145-154). CON drops by `conDown` and the mob dies at 0 (lines 168-178). Max movement, mana and HP are each divided by `max(2, conDown)` (lines 186-191). There is no self-cure roll. The duration is 99,999 ticks (about 111 mud days) unless something removes it earlier.

**Tetanus** (`Disease_Tetnus.java`). DEX falls by `dexDown`, which rises by 1 each mud day (line 142). At DEX 0 it sets `dexDown=-1` and calls `postDeath` from inside `affectCharStats` (lines 156-161).

**Mummy rot** (`Disease_MummyRot.java`). Not a `Disease` subclass; it extends `StdAbility` directly. It needs melee range (lines 189-192). A successful touch sets `conDown=1` and applies the effect with a level-based duration (lines 213-215). Each 10 ticks, `conDown` rises by 1 (lines 141-146). CON drops by `conDown` and the mob is killed at 0 (lines 178-183). Curing it grants the same immunity as other diseases (line 165).

**Lyme** (`Disease_Lyme.java`). After the first day, each time the mob uses a known ability the use is vetoed with probability `75 - mindSave` percent, with "Your headaches make you forget <ability>!" (lines 137-145). The cure roll runs each mud day (lines 190-193).

**Migraines** (`Disease_Migraines.java`). The first use of each known ability rolls `> mindSave+35` to forget it (about `65 - mindSave` percent). The result is cached per instance in `forgotten` or `remember` sets (lines 151-168). Remembered abilities are never rolled again.

**Writers block** (`Disease_WritersBlock.java`). Vetoes `TYP_WRITE` and `TYP_REWRITE` (lines 149-155). The duration is a random 1 to one mud month (lines 86-89).

**Sleepwalking** (`Disease_Sleepwalking.java`). The countdown runs whether or not the mob is asleep, but the walk happens only when asleep (line 158). The walk picks a random open exit and calls `CMLib.tracking().walk` (lines 160-172). It suppresses the enter and leave messages while asleep (lines 136-143).

**Narcolepsy** (`Disease_Narcolepsy.java`). Every mud hour (150 ticks, line 93) the fatigue is set to current plus `FATIGUED_MILLIS` (`Common/interfaces/CharState.java:75`, 3000 ticks' worth), unless the race is infatigueable or fatigue is disabled (lines 149-154). If the mob is awake it forces the `Sleep` command (lines 156-166). STR is halved (line 175).

**Lockjaw** (`Disease_Lockjaw.java`). Eating and drinking are vetoed unless the action is forced with `MASK_ALWAYS` (lines 160-166). It sets `CAN_NOT_SPEAK` (line 176). Each mud day has a save-based cure (lines 139-143).

**Muteness** (`Disease_Muteness.java`). `CAN_NOT_SPEAK` for the whole duration (lines 130-133). It has no cure roll.

**Yawning** (`Disease_Yawning.java`). Not malicious, and needs the target to be seen (`DISEASE_REQSEE`, line 115). Every 3 ticks while awake it yawns, plays an MSP sound tag (line 168), and tries to spread to a random inhabitant (line 171). On its final tick, if the mob is awake and fails the save, it removes itself and re-invokes Yawning (lines 153-161). This is a self-renewing loop that ends only when a save succeeds or the mob sleeps.

**Tinnitus** (`Disease_Tinnitus.java`). Every tick, the mob is deafened (`CAN_NOT_HEAR`) if `roll > save` (lines 141-144, 156). It has no cure roll and ends when its 100-tick duration runs out.

**Tourettes** (`Disease_Tourettes.java`). Every 8 ticks (line 98) the mob yells a line chosen by `1d30` (lines 127-251) with `forceStandardCommand(... "Yell" ...)` (line 283). About 30 lines are crude insults, nonsense words or scatological jokes. Several change when the target is in view and has INT above 5, and name the target's race, class or sex. The rolls are paraphrased, not quoted.

**Scurvy** (`Disease_Scurvy.java`). CHA is halved (line 151). Every 30 ticks the mob spits out a tooth (cosmetic). Eating orange, pepper, lime or lemon material cures it (lines 161-168).

**Smiles** (`Disease_Smiles.java`). Not malicious, PROX-spread. Every 2 ticks it spreads, and on its final tick a failed save converts it to Giggles (lines 148-157).

**Magepox** (`Disease_Magepox.java`). Every 15 ticks it spreads by proximity. Each tick, mana loses `(maxHP-curHP) + (maxMOVE-curMOVE)` (lines 151-158).

**Plague** (`Disease_Plague.java`). Every 4 ticks it deals `level/2` (minimum 1) and spreads (lines 143-147). CON and DEX are set to 3 (lines 159-160).

**Pneumonia** (`Disease_Pneumonia.java`). Every 3 ticks it deals `4d(level+1)+1` (line 143), calls `Disease_Cold.catchIt` on the mob (lines 145-146), and rolls 1 percent for Fever (line 147). It sets max MOVE to 10 (line 176) and applies CON -8 and STR -10 (lines 163-164).

**SARS** (`Disease_SARS.java`). Every 5 ticks it deals `2d(level+1)+1`, and on a 1 percent roll adds `10d10+1` more (lines 143-145). CON -4, STR -5 (lines 158-159). Max MOVE halves, mana loses a third and max HP drops by level (lines 171-173).

**Planar plague** (`Disease_PlanarPlague.java`). Every 4 ticks it deals `level/6` (minimum 1) (lines 160-161). Its severity `abilityCode()` ratchets up to the host's base level and never falls (lines 127-134). Penalties scale with severity: CON and DEX drop by `ceil(ac/10)` (lines 177-184), attack by `ac` (line 194), max HP and max MOVE by `2*ac` each (lines 205-208). It also sets `IS_GLOWING` (line 195).

**Sea sickness** (`Disease_SeaSickness.java`). It is cured as soon as the mob is off water (lines 143-161). While at sea, each 5 ticks it costs 1 thirst, and 1 percent of the time it vomits for -100 thirst (lines 166-171). Max MOVE and MANA are halved (lines 183-184).

**Zika** (`Disease_Zika.java`). Spreads by natural-weapon damage (`SPREAD_DAMAGE`). For non-insects it sets max CON adjustment from the max INT adjustment minus 1 (line 130, see Problems).

**Leprosy** (`Disease_Lepresy.java`). The file has no tick and no stat effect. Its only effect is `okMessage` rewriting incoming damage text: if the message has a `<DAMAGE>` or `<DAMAGES>` tag it is replaced with the combat damage tag, if the tool is a weapon, its hit string is substituted instead (lines 138-158).

## Content worth mining

### Catalogue

Durations are the `DISEASE_TICKS` value (ticks, with mud-day equivalents where useful). "Period" is `DISEASE_DELAY` (ticks). Spread codes: CONS = consumption, PROX = proximity, CONTACT, STD = sexual contact via socials, DAMAGE = natural-weapon damage, GET = item pickup.

| Class | Name | Duration | Period | Spread | Core effect (line refs in the sections above) |
|---|---|---|---|---|---|
| Lepresy | Leprosy | 999,999 | 10 (unused) | CONS | Rewrites damage text only |
| Plague | The Plague | 48 | 4 | CONS PROX CONTACT STD | Damage level/2; CON and DEX set to 3 |
| Tinnitus | Tinnitus | 100 | 1 | none | Per-tick deafness chance |
| WritersBlock | Writers Block | 1 to 21,600 (random) | 100 (unused) | none | Vetoes writing |
| PoisonIvy | Poison Ivy | 35 | 5 | CONTACT STD | CHA -2, DEX -2 |
| Scabies | Scabies | 3 to 4 mud days | 8 | STD | Cosmetic |
| Muteness | Muteness | 99,999 | 900 (unused) | none | Cannot speak |
| Zika | Zika | 150 mud days | 99 (unused) | DAMAGE | Max CON adjustment (buggy) |
| Scurvy | Scurvy | 15 mud days | 30 | none | CHA halved; cured by citrus or pepper |
| Smiles | Contagious Smiles | 10 | 2 | PROX | Cascades to Giggles |
| Magepox | Magepox | 1 mud day | 15 | PROX | Mana drained by missing HP and MOVE |
| Tetnus | Tetanus | 6 mud days | 1 mud day | CONS | DEX -1 per day, death at 0 |
| Lockjaw | Lockjaw | 9,999,999 | 1 mud day | CONS | No eat or drink, cannot speak, daily save cure |
| Pneumonia | Pneumonia | 38 | 3 | CONS PROX CONTACT STD | Damage; seeds Cold; CON -8, STR -10 |
| SARS | SARS | 30 | 5 | CONS PROX CONTACT STD | Damage with 1% spike; CON -4, STR -5 |
| Yawning | Yawning | 30 | 3 | PROX | Renews itself unless saved |
| Sleepwalking | Sleepwalking | 10 | 2 | none | Walks while asleep |
| Narcolepsy | Narcolepsy | 99,999 | 150 (1 mud hour) | none | Fatigue, forced Sleep, STR halved |
| Migraines | Migraine Headaches | 99,999 | 50 (unused) | none | Per-ability forgetting, cached |
| SeaSickness | Sea Sickness | 40 | 5 | none | Only at sea; thirst, puking, MOVE and MANA halved |
| Lyme | Lyme Disease | 8,100 (9 mud days) | 900 (1 mud day) | CONS DAMAGE | Ability forgetting; end cascade |
| Malaria | Malaria | 8,100 (9 mud days) | 5 | CONS PROX CONTACT STD | Damage; daily CON loss; 1% Fever |
| Syphilis | Syphilis | 99,999 | 900 (1 mud day) | STD | Stacking CON loss; divides max state |
| Vertigo | Vertigo | 8 to 40 ticks (4d9+4) | 1 | none | 5% falls; 7% misdirected moves |
| RadiationSickness | Radiation Sickness | 999,999 | 8 | GET | CON loss growing; lead blocks transfer |
| PlanarPlague | Planar Plague | 48 | 4 | CONS PROX CONTACT STD | Level-ratcheted penalties |
| RoyaltyRot | Royal Rot | 8 to 40 mud days (900 x 4d9+4) | 7 | none | STR -5; faints; bleeding trigger |
| MummyRot | Mummy Rot | level-based (tick adj 0) | 10 (hard-coded) | none | Melee touch; CON decay to death |
| Vampirism | Vampirism | 5,400 (6 mud days) | 900 (1 mud day) | CONS (inert for mobs) | Light penalty; blood feeding; CHA -1 default |
| Lycanthropy | Lycanthropy | 9,999,999 | 50 | CONS DAMAGE | Full-moon WereWolf; hunting |
| Tourettes | Tourettes | 999,999 | 8 | none | Random yells |
| Obesity | Obesity | 999,999 | 50 (unused) | none | Fat counter; weight, MOVE, display |
| Nausea | Nausea | 300 (2 mud hours) | 3d4+1 (rolled) | special | Vomit puddle; bystander infection |
| PlanarInstability | Planar Instability | 150 (10 real minutes) | 999,999 (unused) | none | Stackable level 1 to 10 |

Note on Vampirism's CONS bit: it matters only for item-affected instances, which Vampirism never has (`canAffectCode` is CAN_MOBS, line 62).

### Sample messages (short, for tone)

- Narcolepsy: "<S-NAME> <S-IS-ARE> getting sleepy..." (`Narcolepsy.java:111`).
- Lycanthropy: "<S-NAME> turn(s) into a @x1!" (`Lycanthropy.java:326`).
- Lyme and Migraines: "Your headaches make you forget @x1!" (`Lyme.java:143`, `Migraines.java:155`).
- Vampirism: "You can't swim!" (`Vampirism.java:198`), "@x1 won't let you." (`Vampirism.java:223`).
- Vertigo: "<S-NAME> fall(s) down!" (`Vertigo.java:210`).
- Nausea: "<S-NAME> throw(s) up." (`Nausea.java:193`), with the puddle named "a puddle of vomit" (line 204).
- Mummy rot: "<S-NAME> turn(s) grey!" (`MummyRot.java:213`).

### Useful patterns

- Tick-based cure rolls: Lyme, Lockjaw, Malaria (save-based cure per period).
- Cascade table: Smiles to Giggles; Lyme to Fever, Amnesia or Arthritis; Syphilis to Cold or Fever; Malaria, Pneumonia to Fever (1%); Radiation to Migraines or Diarrhea (5% per 8 ticks).
- Item-borne effect for the Uranium resource (`RawMaterial.java:803`), which is the only item source in this bucket.

## Notable design ideas

- **Messages are the infection channels.** Spread is driven by message types (damage with natural weapons, contact moves, the MATE/SEX socials, speech within hearing range) in `Disease.executeMsg`. A new kind of contact only needs a new message check.
- **Light and moon as state.** Vampirism reads the room's darkness on every stat recalculation (`Vampirism.java:155-160`). Lycanthropy reads the area's moon phase and time of day (`Lycanthropy.java:322-323`). Environment changes the body without a separate timer.
- **Stacking by level.** Planar Instability stacks by raising a level 1 to 10 (`PlanarInstability.java:247-256`) and scales several penalties with it. Re-catching the disease makes it worse by degrees, not by duplication.
- **Severity ratchet.** Planar Plague's `abilityCode()` keeps the highest host level seen (`PlanarPlague.java:127-134`), so the disease becomes worse the longer it is carried by stronger hosts.
- **Cumulative decay.** Radiation, Malaria, Syphilis, Tetanus and Mummy rot each lower one stat by a counter that grows per period and ends in death. Each is a small, readable pattern.
- **Misdirection.** Vertigo rewrites a move's target and exit in `okMessage` (`Vertigo.java:161-174`), so the player's command is redirected rather than refused.
- **Forgetting cached per ability.** Migraines rolls the forget chance once per known ability and remembers the answer (`Migraines.java:151-168`). Lyme vetoes at each use instead (`Lyme.java:137-145`).
- **Renewing loop.** Yawning re-invokes itself at expiry unless the save succeeds or the mob sleeps (`Yawning.java:153-161`), a contagion that persists without a new source.
- **Scurvy's food cure.** The cure is a list of food materials (`Scurvy.java:161-168`), an item-tag check rather than a spell.
- **Items carry disease.** Uranium has a disease in its resource record (`RawMaterial.java:803`), so radioactive items are a data edit, and lead shields by material.

## Problems and cruft

- **Planar Instability fizzle rule runs backwards.** `rollPercentage() > 10*level` fizzles casts with probability 90 percent at level 1 and never at level 10 (`PlanarInstability.java:205`). This contradicts the "instability" theme. Intent unverified.
- **Planar Plague penalty rounding is a no-op.** `Math.ceil(ac/10)` (`PlanarPlague.java:178`, `:182`) divides integers before the ceiling, so the ceiling does nothing and penalties are `floor(ac/10)`.
- **Planar Plague movement clamp checks the wrong field.** Lines 209-210 repeat the hit-point check after the movement change, so max MOVE can go negative (`PlanarPlague.java:208-210`).
- **Zika stat bug.** `affectCharStats` reads `STAT_MAX_INTELLIGENCE_ADJ` and writes `STAT_MAX_CONSTITUTION_ADJ` as an absolute value (`Zika.java:130`), so a non-insect's max CON adjustment becomes the max INT adjustment minus 1. This looks like a copy-paste error.
- **Vampirism executeMsg.** It calls `super.okMessage` where `super.executeMsg` was probably meant (`Vampirism.java:324`). In this chain both are no-ops (`StdAbility.java:2831-2834`, `2837-2840`), so the call is harmless, but it also skips `Disease.executeMsg`, which does nothing for this class anyway.
- **Vampirism CHA default.** The misc-text default -1 (line 139) is applied because the guard checks only for 0 (line 373). Every instance is -1 CHA unless the text sets it.
- **Lycanthropy precedence bug.** `(nextRoom!=null && sameArea) || !isMonster` (`Lycanthropy.java:287-288`) lets player characters skip the area and null checks. The trail itself is still area-limited (lines 265-271), so the practical effect is limited.
- **Side effects inside stat recalculation.** `postDeath` runs inside `affectCharStats` in Mummy rot (`MummyRot.java:182`) and Tetanus (`Tetnus.java:160`). Malaria and Syphilis guard against recursion with `norecurse` (`Malaria.java:181-190`, `Syphilis.java:169-178`). Mummy rot and Tetanus rely on sentinel values (`conDown=-1`, `dexDown=-1`).
- **Wall-clock time in game logic.** Obesity throttles losses with `System.currentTimeMillis()` (`Obesity.java:197-212`), so pausing, speeding or replaying the game changes the result.
- **Unguarded location.** Several ticks call `mob.location().show(...)` with no null check, e.g. `Malaria.java:157`, `Syphilis.java:142`, `Pneumonia.java:142`, `MummyRot.java:144`.
- **Possible NPE.** `Nausea.java:179` calls `I.owner().isContent(...)` whenever the item has a container; owner is not checked. Reachability unverified.
- **Dead or unused members.** `PlanarInstability.MISC_SAVES` (`PlanarInstability.java:142-155`) is never read. `Lycanthropy.DISEASE_STD` (`Lycanthropy.java:113-116`) is never called. `Lepresy.lastHP` (line 95) is unused. The period hook is unused in seven classes: Lepresy, Muteness, Migraines, WritersBlock, Zika, Obesity and PlanarInstability.
- **Bitmap bits that do nothing.** `SPREAD_PROXIMITY` is returned by eight classes here and tested nowhere. `SPREAD_GET` only matters for item-carried effects.
- **Random source mix.** `RoyaltyRot` uses `Math.random` (`RoyaltyRot.java:169`) while everything else uses `CMLib.dice()`.
- **Repurposed config.** Lycanthropy's level window uses `EXPRATE` (`Lycanthropy.java:207`).
- **Naming and typos.** Class and ID spellings "Lepresy" and "Tetnus" are kept as data keys, while the display names say Leprosy and Tetanus. Messages include "Your tetnus clears up!" (`Tetnus.java:98`) and "It`s really gross." (`Nausea.java:206`).
- **Long diseases with no cure roll.** Syphilis (99,999 ticks), Muteness, Migraines, Narcolepsy, Tourettes, Radiation Sickness and Leprosy have no per-period cure roll, so they end by duration or external removal. Whether players cure them in play is unverified.

## Takeaways for a Python SUD

- **Model a disease as data plus a few hooks.** A dataclass with `id`, `name`, `duration_ticks` (or hours), `period_ticks`, `spread` as a set of channel names, `stat_mods` (`{"con": -2, "cha": -2}`), `state_mods` applied to max values, `senses` (`{"speak": False}`), a `cure` rule (per-period save or item tags), and `on_expire` cascades with probabilities. Most of the 34 fit this shape. Mummy rot, Lycanthropy, Vampirism and Planar Instability need small custom hooks.

  ```python
  @dataclass
  class Disease:
      id: str
      duration_hours: float          # game time, not wall clock
      period_hours: float
      spread: frozenset[str]         # {"contact", "damage", "std", "item_get"}
      con: int = 0                   # stat deltas, recomputed from base each time
      cure_items: frozenset[str] = frozenset()
      on_expire: tuple[tuple[float, str], ...] = ()
  ```
- **Recompute stats from base, never mutate.** The `affectCharStats` pattern (base, then every active effect) is clean. Keep `postDeath`-style side effects out of recomputation and run them from the tick.
- **Game time, not wall clock.** Use the tick or hour counter everywhere. Drop Obesity's 10-second throttle and the MSP audio tag (`Yawning.java:168`), which is client-specific.
- **Replace room spread with environmental exposure.** Single-player has no other humans, so `catchIt(mob)` (random inhabitant) becomes exposure from places (a sick room or sea), contaminated items (the Uranium pattern, with lead as a material shield), or NPC carriers. Keep the per-target save roll.
- **Keep the item-carried mechanic.** Items with a disease, transferred on pickup and blocked by shielding material, fit a trade or loot game and need only a material table.
- **Keep the cure-by-item idea.** Scurvy's citrus cure is a tag check and suits crafted remedies or foraging.
- **Simplify the catalogue to archetypes.** Periodic damage (Plague, Pneumonia, SARS, Malaria, Planar Plague); stat drain (Tetanus, Syphilis, Radiation, Mummy rot); sense or speech block (Lockjaw, Muteness, Tinnitus); movement and balance (Vertigo, Royal Rot, Sea sickness); memory (Migraines, Lyme, Writers block); compulsion (Sleepwalking, Narcolepsy, Tourettes, Yawning); environment-driven (Vampirism, Lycanthropy). Drop the duplicates (Smiles and Giggles, Plague and Planar Plague) or merge them with a severity parameter.
- **Drop or redesign.** Drop the Zika bug and the Vampirism cross-wired nourishment unless a deliberate design is confirmed. Drop Leprosy's text-only effect unless it becomes a visible-injury status. Rethink Vampirism's feeding (it needs another living target) as a single-player hunt against a chosen NPC.

## Coverage

- **Read in full (34 of 34 files).** Every file in the manifest was read. Vampirism and Lycanthropy were read from line 1. The other 32 were read from line 34 onward, so the package, imports and licence header (lines 1-33) were not read. Muteness's class line (33) was confirmed by grep.
- **Read for context.** `Disease.java` (410 lines, part 1) read in full. `StdAbility.java` read at the lines cited, `DiseaseAffect.java` at lines 42-56, `Dice.java` at 398-411, `CoffeeTime.java` at 1149-1168, `RawMaterial.java` at 803 and 838-849, `RawCMaterial.java` at 543-555, `StdMOB.java` at 1047-1063 and 240, `Prop_CombatAdjuster.java` at 83, `CharState.java` at 75, `CMProps.java` at 3168 and 3503-3542, `coffeemud.ini` at the lines cited, `DefaultTimeClock.java` at 93 and 313, `CMParms.java` at 2952-2955, and `MUD.java` at 1620.
- **Skimmed.** The referenced sibling diseases (Fever, Cold, Giggles, Amnesia, Arthritis, Diarrhea, Bleeding, TemporaryImmunity) were checked only for existence and cascade targets. They are covered in other buckets.
- **Skipped.** Nothing in this bucket was skipped. Behaviour that depends on the `Bleeding` and `TemporaryImmunity` effects was not read and is marked unverified where it matters.
