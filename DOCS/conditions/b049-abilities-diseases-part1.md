# Diseases, part 1: the disease framework and 35 disease types

> Part of the CoffeeMud NEWOVERVIEW. Bucket: `b049` (Abilities/Diseases [part 1/2: Disease .. Disease_Leeches]) · 36 files · 6749 lines of Java.

## Summary

A CoffeeMud disease is an ordinary `Ability` attached to a creature or an item as a malicious, timed effect. The base class `Disease` supplies the whole lifecycle: a save-based infection roll, spread through a bitmask of routes, a countdown in ticks, a cure message and a temporary per-disease immunity. Each subclass overrides only hooks: duration, cycle, stat and state penalties, action vetoes, speech rewrites and messages. Most of the 36 files are a handful of numbers and strings, but about a dozen carry real mechanics: escalation chains (flu to fever, cold to pneumonia, infection to gangrene), a permanent mind-altering condition that vetoes spells, a hostility mask that blocks trade and grouping, and chronic diseases that run a constitution or HP clock down to death. For a single-player SUD this shows how a health condition can be written as data plus a few hooks into one action pipeline.

## Key files and types

Paths are relative to the CoffeeMUD folder. Files in the Diseases folder are written as `Disease*.java` with the folder shown once in the table heading.

| File or class | Role | Lines |
|---|---|---|
| `com/planet_ink/coffee_mud/Abilities/Diseases/Disease.java` | Base class: infection roll, spread routes, countdown, immunity, cure | 410 |
| `com/planet_ink/coffee_mud/Abilities/Diseases/Disease_Hatred.java` | Hostility mask: trade and group vetoes, mood-rewritten speech, hostile socials | 372 |
| `com/planet_ink/coffee_mud/Abilities/Diseases/Disease_FootFungus.java` | Feet restriction, limp through Gait, running veto | 231 |
| `com/planet_ink/coffee_mud/Abilities/Diseases/Disease_Apathy.java` | Apathetic speech, veto of beneficial spells | 216 |
| `com/planet_ink/coffee_mud/Abilities/Diseases/Disease_Depression.java` | Attack and justice-save penalties, forced sleep and forced peace | 214 |
| `com/planet_ink/coffee_mud/Abilities/Diseases/Disease_Alzheimers.java` | INT and WIS halved, forgotten abilities, random wandering | 212 |
| `com/planet_ink/coffee_mud/Abilities/Diseases/Disease_HeatExhaustion.java` | Look and examine redirected to another room, weather cure | 212 |
| `com/planet_ink/coffee_mud/Abilities/Diseases/Disease_Gangrene.java` | Chronic HP erosion per mud day, death through postDeath | 207 |
| `com/planet_ink/coffee_mud/Abilities/Diseases/Disease_Cannibalism.java` | Eating veto with a race-based food gate | 197 |
| `com/planet_ink/coffee_mud/Abilities/Diseases/Disease_Leeches.java` | Poison removal, HP ceiling ratchet, max-HP drain | 196 |
| `com/planet_ink/coffee_mud/Abilities/Diseases/Disease_Flu.java` | Stat and state penalties, cycle damage, chance to become fever | 179 |
| `com/planet_ink/coffee_mud/Abilities/Diseases/Disease_Cold.java` | Final-tick escalation to pneumonia | 177 |
| `com/planet_ink/coffee_mud/Abilities/Diseases/Disease_Infection.java` | Final-tick escalation to gangrene | 178 |
| `com/planet_ink/coffee_mud/Abilities/Diseases/Disease_Anthrax.java` | Damage cycle, CON loss that grows, death through postDeath | 183 |
| `com/planet_ink/coffee_mud/Abilities/Diseases/Disease_Cancer.java` | Growing CON loss, resources divided by a counter, death | 177 |
| `com/planet_ink/coffee_mud/Abilities/Diseases/Disease_Gonorrhea.java` | Escalation to arthritis, sexual socials vetoed | 183 |
| `com/planet_ink/coffee_mud/Abilities/Diseases/Disease_Blahs.java` | Speech rewritten through the Blah language | 192 |
| `com/planet_ink/coffee_mud/Abilities/Diseases/Disease_Diabetes.java` | Obesity after eating when full, nausea chance per day | 177 |
| `com/planet_ink/coffee_mud/Abilities/Diseases/Disease_ColorBlindness.java` | Session output filter that strips colour codes | 173 |
| `com/planet_ink/coffee_mud/Abilities/interfaces/DiseaseAffect.java` (outside bucket) | Interface: SPREAD_ bit constants, difficultyLevel, spreadBitmap, isMalicious | 96 |
| `com/planet_ink/coffee_mud/Abilities/StdAbility.java` (outside bucket) | maliciousAffect, spreadImmunity, tick countdown, duration rule | 3018 |
| `com/planet_ink/coffee_mud/Abilities/Misc/TemporaryImmunity.java` (outside bucket) | Per-creature list of immune disease IDs with timestamps | 225 |
| `com/planet_ink/coffee_mud/Abilities/Misc/Mood.java` (outside bucket) | Speech mood; MoodType enum at line 96 | 1673 |
| `com/planet_ink/coffee_mud/Abilities/Languages/Blah.java` (outside bucket) | Language whose translate() renders each word as "blah" | 80 |
| `com/planet_ink/coffee_mud/Races/StdRace.java`, `Abilities/Common/Butchering.java`, `Abilities/Misc/Scavenge.java`, `Abilities/Misc/Pregnancy.java` (outside bucket) | Route consumers: corpse, butchered meat, scavenged items, birth | 2204 / 238 / 198 / 813 |

## How it works

### Data model

Hook defaults in `Disease.java`:

- `DISEASE_TICKS()` returns 48 (lines 110-113), the duration in ticks.
- `DISEASE_DELAY()` returns 5 (115-118), the cycle length in ticks.
- `DISEASE_START()`, `DISEASE_DONE()` and `DISEASE_AFFECT()` return the messages (120-133).
- `DISEASE_REQSEE()` returns false (135-138). Only `Disease_Yawning`, outside this bucket, overrides it.
- `spreadBitmap()` returns 0 (140-144). `difficultyLevel()` returns 0 (152-156). `isMalicious()` returns true (99-102).

Instance state: `diseaseTick` (160) is the cycle counter, `processing` (158) guards item re-entry, `invoker` (inherited) is the creature that infected this one, and `tickDown` (StdAbility.java:58) is the remaining duration.

Scope: `canAffectCode()` is CAN_MOBS (61-64). `canTargetCode()` is CAN_MOBS|CAN_ITEMS (66-70), so a disease can be put on food and drink as well as on creatures. Classification is ACODE_DISEASE (93-96), abstract quality is QUALITY_MALICIOUS (81-84), and `putInCommandlist()` is false (87-90), so diseases never appear as typed commands.

Route bits (`Abilities/interfaces/DiseaseAffect.java:42-56`): STD=1, CONTACT=2, PROXIMITY=4, CONSUMPTION=8, DAMAGE=16, GET=32, HEARING=64, INHERETED=128. Readable names are at lines 61-70.

### Infection: invoke and catchIt

There are two infection paths.

1. Deliberate `invoke` (`Disease.java:338-409`, the spell and item path). The skill check is bypassed when `auto` is set: `proficiencyCheck` returns true and sets proficiency to 100 for creatures that are not the caster's own (`StdAbility.java:1443-1448`). The disease is offered to the room as a `CMMsg` with `MASK_HANDS|MASK_MALICIOUS|TYP_DISEASE` (360-362). If the message value is 0 or less, the target sees DISEASE_START (367), the effect is attached with `maliciousAffect(mob, target, asLevel, DISEASE_TICKS(), -1)` (368), and the new instance receives the caster's misc text (371). Otherwise the target gets `spreadImmunity` (374). Non-malicious diseases (Giggles, Apathy) then restore the combat victims, so they never start a fight (376-398).

2. Spread through `catchIt(mob, target)` (`Disease.java:168-205`). It does nothing if the target already has this disease (`fetchEffect(ID())==null`, line 180), or if the disease needs sight and the carrier cannot be seen (181). For a creature, the infection succeeds when `rollPercentage() > save(STAT_SAVE_DISEASE)` (186), which is a (100 - save)% chance because the roll is 1-100. A resisted exposure grants immunity (196). For an item the disease is attached directly (200). `catchIt(mob)` (207-215) picks a random inhabitant of the room and calls the two-argument form. This is the only proximity mechanism; see Spread routes.

Recovery: `unInvoke()` (217-236) removes the effect through `StdAbility.unInvoke`, grants immunity to a living creature (230), and prints DISEASE_DONE (231).

### Countdown, cycles and duration

- `StdAbility.tick` (`StdAbility.java:2843-2862`) decrements `tickDown` only on the creature tick, `tickID==Tickable.TICKID_MOB` (2849), and calls `unInvoke()` at zero.
- Each member's `tick()` calls `super.tick()` first, then on each cycle runs `if((--diseaseTick)<=0){ diseaseTick=DISEASE_DELAY(); ... }`. The typical example is `Disease_Flu.java:126-155`.
- Durations are fixed. `getMaliciousTickdownTime` returns any positive tick adjustment unchanged (`StdAbility.java:2176-2177`), so DISEASE_TICKS is an absolute count of ticks. It does not scale with caster level or the experience rate, which only the level-based branch does.

Time base. `TICKSPERMUDDAY` is set to `MILLISPERMUDHOUR * globalClock hours per day / tick length` (`Common/DefaultTimeClock.java:313`, also `application/MUD.java:1620`). The shipped defaults are `MILLISPERMUDHOUR=600000` (`coffeemud.ini:1099`), `HOURSINDAY=6` (`coffeemud.ini:1104`) and `TICKTIME=4000` (`coffeemud.ini:2151`). That gives 150 ticks per mud hour and 900 ticks per mud day, so one mud day is one real hour at defaults. `Disease.getTicksPerDay()` (`Disease.java:162-166`) does the same per clock. In this chapter "day" means a mud day, 900 ticks at defaults. Note that `homeClock(affected)` falls back to the global clock when the disease is not yet attached (`affected` is null), as in `Libraries/CoffeeTime.java:1149-1170`.

### Save and difficulty

- Only `STAT_SAVE_DISEASE` gates infection. The damage route needs `rollPercentage() > save + 70` (`Disease.java:256`), which is about (30 - save)% when save is below 30.
- `difficultyLevel()` (0-9) is a cure gate. `Prayer_CureDisease.java:142` and `Prayer_MassCureDisease.java:155` compare `difficultyLevel()*10` with the caster's adjusted level plus the prayer's ability code. The cure branch itself was not read. `Items/Software/HealthScanProgram.java:95` shows the value to the player.

### Spread routes

For a creature carrier, `Disease.executeMsg` (`Disease.java:238-336`, lines 241-282) checks:

- DAMAGE (248-257): the carrier hits a creature with a natural weapon while bare-handed, and `roll > save + 70`.
- CONTACT (259-266): the carrier moves or uses hands on a creature, or touches it with a natural tool.
- STD (268-274): the MATE or SEX social between carrier and target.
- HEARING (276-281): the carrier speaks to a creature that can hear it.

For an infected item (283-334), drinking or eating (TYP_DRINK, TYP_EAT, lines 296-313) spreads CONSUMPTION or CONTACT to the holder. Get, push and pull of items that are neither food nor drink spread CONTACT or GET (318-325).

Periodic exposure. Flu (`Disease_Flu.java:145`), Cold (`Disease_Cold.java:159`), Fleas (`Disease_Fleas.java:140`) and Giggles (`Disease_Giggles.java:157`) call `catchIt(mob)` on every cycle. Those calls do not consult the route bits.

Consequences:

- SPREAD_PROXIMITY is never tested in `Disease.java`. It has effect only through the explicit periodic calls above.
- SPREAD_INHERETED is read only in `Abilities/Misc/Pregnancy.java:634`. At birth, every disease effect on the mother with that bit is copied to the child (`copyOf`, 634-640).
- CONSUMPTION does not act on a living carrier directly. When a creature dies, each disease with CONSUMPTION or CONTACT is copied onto its corpse (`Races/StdRace.java:1008-1010`). Butchering copies them onto the food and drink it produces (`Abilities/Common/Butchering.java:127`), and scavenging does the same (`Abilities/Misc/Scavenge.java:171`). Consumption diseases therefore spread through meat, not from person to person.

### Immunity

- `spreadImmunity(mob)` (`StdAbility.java:2351-2365`) returns early if the creature already has this disease. Otherwise it adds a `TemporaryImmunity` effect that is not savable and is long-lasting, and records `"+" + ID`.
- It is called after a resisted exposure (`Disease.java:196`), after a blocked invoke (374), and after a cure (230).
- `Abilities/Misc/TemporaryImmunity.java`: `IMMUNITY_TIME = 36000000` ms, which is 10 real hours (line 94). The list of (ID, timestamp) pairs is pruned every 10 ticks (`tick`, 114-135). Re-recording an ID refreshes its timestamp (`setMiscText`, 181 onward).
- An immune creature is protected in two ways. `okMessage` (208-225) vetoes any message whose tool is a listed ability and shows "X seem(s) immune to Y." The veto reaches the disease through the room's `okMessage`, which is how `Disease.invoke` checks the target. The propagation through the room was not traced. As a second line, `tick` (151-158) removes any active effect on the list whenever the creature's effect count changes.
- Immunity expires on real time. At default settings 10 real hours is 10 mud days of game time, but the expiry does not follow the game clock.

### Stat and state effects

Penalties come from `affectCharStats`, `affectCharState` and `affectPhyStats`. The engine recomputes them from base values on each recovery call (`recoverCharStats` and similar). Examples:

- Flu: CON -4 and STR -5, both floored at 1 (`Disease_Flu.java:158-168`). Movement is halved, mana loses a third, and HP loses the creature's level on each recalculation (171-178).
- Cold: CON -2 and STR -3, floored at 1 (`Disease_Cold.java:166` onward).
- Alzheimers: INT halved (floor 1), WIS halved (floor 1), CHA +2 (`Disease_Alzheimers.java:200-211`).
- Gangrene: CHA -4, floored at 0 (`Disease_Gangrene.java:203`). Each recalculation removes `daysSick * (HP/10)` from HP (184).
- Cancer and Anthrax: CON is reduced by a counter that grows over time (`Disease_Cancer.java:155`, `Disease_Anthrax.java:171`).
- Blindness, Deafness and Anosmia set `CAN_NOT_SEE`, `CAN_NOT_HEAR` or `CAN_NOT_SMELL` in the sense mask (`affectPhyStats`).

### Behaviour: the action pipeline

Mind and behaviour diseases work through `okMessage`, which runs before an action. Returning false vetoes the action and typically tells the actor why. Speech is rewritten by lending the creature a `Mood` ability (`Disease_Apathy.java:145-158` and `173-182`, `Disease_Hatred.java:270-281`).

- Apathy: a beneficial cast is vetoed when `rollPercentage() > MIND save + 10` (`Disease_Apathy.java:184-199`, roll at 188). The message is "You don't really feel like doing X!"
- Amnesia: any ability the creature knows is vetoed when `rollPercentage() > MIND save + 10` (`Disease_Amnesia.java:145`). The message is "You can't remember X!"
- Alzheimers: the same test with no +10 (`Disease_Alzheimers.java:147`), so it blocks more often. Its tick adds a wandering behaviour (156-197).
- Hatred: the shop commands BUY, BID, SELL, LIST, VALUE, DEPOSIT, WITHDRAW, BORROW and VIEW, and the FOLLOW command, are vetoed when the other party matches the hate masks. Group members count too (`Disease_Hatred.java:232-256` and `288-314`). A room counts as hated only when every occupant outside the group is hated (`doIHate(srcM, R)`, 192-211).
- Blahs: a speaking creature's message is run through the Blah language at proficiency 100 (`Disease_Blahs.java:151-158`).
- Depression: MATE and SEX socials are vetoed (`Disease_Depression.java:144-151`).
- Gonorrhea and Chlamydia: the same sexual-social veto (`Disease_Gonorrhea.java:165` onward; `Disease_Chlamydia.java:153` onward).
- FootFungus: AUTORUN is vetoed on room enter and leave with "You can not run in your condition" (`Disease_FootFungus.java:183-191`).
- HeatExhaustion: a look or examine of the current room is run in a random room of the same area instead (`Disease_HeatExhaustion.java:141-161`; the room choice is at 128-138).
- Cannibalism: eating is vetoed unless the food passes a race test (see Problems), and the Spell_Hungerless tool is blocked (`Disease_Cannibalism.java:174` and `186-190`).

### Cycles, escalation and the final tick

Random escalation during a cycle:

- Flu, Gangrene and Infection each roll `rollPercentage()==1` on a cycle and add Fever on a hit (`Disease_Flu.java:146-150`, `Disease_Gangrene.java:164-168`, `Disease_Infection.java:167-170`). That is a 1% chance per cycle.
- Gonorrhea rolls 1% per tick, and only if `roll > COLD save` and `roll < 25 - CON` also hold, adds arthritis (`Disease_Gonorrhea.java:136-146`).
- Diabetes adds obesity with roll(1,100) < 3, a 2% chance, after eating while full (`Disease_Diabetes.java:144-150`). Once per mud day it adds nausea with a 1% roll (166-170).

Final tick. `getTickDownRemaining()==1` (`StdAbility.java:2406-2409`) is the last tick. Cold (`Disease_Cold.java:139-147`) and Infection (`Disease_Infection.java:151-158`) then remove themselves and invoke a worse disease if `roll > save` and `roll < 25 - CON`. For CON 10 that second roll is 14%. Cold's escalation applies only to player characters (`!mob.isMonster()`, line 143). The escalation is silent: `delEffect` runs without a message.

### Chronic clocks

- Gangrene: `tickUpToDay` counts ticks, and `daysSick` rises once per mud day (`Disease_Gangrene.java:140-145`). HP loss grows with `daysSick` (184). The disease ends when HP reaches max (147-151). Its duration is 100 mud days (`DISEASE_TICKS` = 100 * TICKSPERMUDDAY, line 92).
- Anthrax: `conTickDown` counts damage cycles, and `conDown` rises by one every 60 cycles (`Disease_Anthrax.java:152-156`). CON loss equals `conDown` (171). At CON 0 or below, `postDeath` runs (172-180).
- Cancer: `conDown` starts at 1 and rises once per mud day through the cycle (`Disease_Cancer.java:127-146`). CON loss equals `conDown` (155). Movement, mana and HP are divided by `conDown` (173-175). At CON 0 or below, `postDeath` runs (157-165).

### Messages

Start and done messages come in pairs, for example "X come(s) down with a flu." and "Your flu clears up." Random pools:

- Fever: ten hallucination lines, one every three ticks while the creature is awake and can see (`Disease_Fever.java`, tick from line 126). Examples: "Everything looks upside-down." and "Hundreds of little rainbow bees buzz around your head."
- FilthFever: five lines about smell (`Disease_FilthFever.java:119-137`), sent to nearby animals every 20 ticks.
- Diarrhea: six lines about stomach discomfort (`Disease_Diarrhea.java`).
- Hatred: twenty-three socials chosen at random for hostile acts (`Disease_Hatred.java:61-65`).
- Veto messages: "Your hatred of X prevents you from dealing with Y." (`Disease_Hatred.java:235-237`), "You can't remember X!" (Amnesia) and "X seem(s) immune to Y." (`TemporaryImmunity.java:208-225`).

### Interactions with other systems

- StdAbility: `tick`, `unInvoke`, `maliciousAffect`, `spreadImmunity`, `proficiencyCheck`.
- Combat: `CombatLibrary.postDamage` (implemented at `Libraries/MUDFight.java:819`) carries all periodic damage. The attacker is the infecting creature, so kills are credited to the carrier. `postDeath` is used by Gangrene, Anthrax and Cancer.
- Mood (`Abilities/Misc/Mood.java`, MoodType enum at line 96): each mood carries an adjective and an adverb, such as ANGRY with "angrily" and APATHETIC with "apathetically".
- Blah (`Abilities/Languages/Blah.java:78`): `translate()` returns "blah", with the case of the original word preserved.
- Gait (`Abilities/Misc/Gait.java`): FootFungus sets its STATE to "limping" (`Disease_FootFungus.java:149-161`).
- Amputation (`Abilities/Misc/Amputation.java`): FrostBite invokes it on expiry (`Disease_FrostBite.java:129-148`).
- Soiled (`Abilities/Misc/Soiled.java`): applied by Diarrhea (`Disease_Diarrhea.java:174-186`).
- Spell_Hungerless (`Abilities/Spells/Spell_Hungerless.java`): blocked by Cannibalism.
- Session output filter (`Common/interfaces/Session.java:48`, `SessionFilter`): ColorBlindness strips colour codes from text sent to the player.
- Clock and properties: `Common/DefaultTimeClock.java:313` and `core/CMProps.java:3539-3541`.
- Dice (`Libraries/Dice.java:398-411`): `roll(n,d,m)` is n dice of d sides plus m. `rollPercentage()` is 1-100.
- Route consumers: Pregnancy, StdRace, Butchering and Scavenge, as above.

## Content worth mining

Routes: CONS = CONSUMPTION, CONT = CONTACT, PROX = PROXIMITY, STD = sexual social, HEAR = HEARING, INH = INHERETED. "Ticks" is DISEASE_TICKS. Times are at default settings, 4 seconds per tick and 900 ticks per mud day. Files are in `com/planet_ink/coffee_mud/Abilities/Diseases/`.

| Disease | Class file | Duration (ticks, about real time) | Cycle | Routes | Diff | Effect in brief |
|---|---|---|---|---|---|---|
| Base | Disease.java | 48 (3.2 min) | none | none | 0 | Lifecycle only |
| Hatred | Disease_Hatred.java | 98 (6.5 min) | 11-20 ticks | none | 1 | Hostility mask; trade and group vetoes; mood speech; hostile social |
| Foot fungus | Disease_FootFungus.java | 5-7 days (5-7 h) | 75 | none | 4 | Feet restricted; limp; movement halved; no running |
| Apathy | Disease_Apathy.java | 34 (2.3 min) | none | CONS/HEAR | 3 | Not malicious; apathetic speech; beneficial casts vetoed |
| Depression | Disease_Depression.java | 900 (1 h) | per-tick rolls | none | 4 | Attack -10, justice save -20; forced sleep when idle; sexual socials vetoed |
| Alzheimers | Disease_Alzheimers.java | 34 (2.3 min) | 1/150 per tick starts 1-20 steps | none | 9 | INT and WIS halved, CHA +2; forgets abilities; wanders |
| Heat exhaustion | Disease_HeatExhaustion.java | 300 (20 min) | room re-picked every 30 | none | 1 | Look shows another room; cured underwater or in bad weather |
| Gangrene | Disease_Gangrene.java | 100 days (100 h) | 5 | none | 4 | CHA -4; HP loss grows daily; healing halved; 1 dmg per cycle |
| Filth fever | Disease_FilthFever.java | 3d4 days (3-12 h) | 20 | CONS | 2 | Justice save -25; CHA halved; CON -3; animals react |
| Cannibalism | Disease_Cannibalism.java | 999999 (about 46 days) | none | CONS | 6 | Eating vetoed for non-race food; Spell_Hungerless blocked |
| Leeches | Disease_Leeches.java | 35 (2.3 min) | 7 | STD | 0 | CHA -4; poisons removed each cycle; HP cannot rise above low point |
| Diarrhea | Disease_Diarrhea.java | 180 (12 min) | 2% per tick | CONS | 2 | Justice save -50; soiling effect |
| Blahs | Disease_Blahs.java | 99999 (about 4.6 days) | none | CONS | 4 | Always fatigued; speech becomes "blah" |
| Anthrax | Disease_Anthrax.java | 10 days (10 h) | 15 | CONS/CONT | 2 | 1d6 per cycle; CON loss grows; death at CON 0 |
| Fever | Disease_Fever.java | 15 (1 min) | 3 (awake, sighted) | none | 1 | Re-picks combat victim each tick; hallucinations |
| Gonorrhea | Disease_Gonorrhea.java | 99999 (about 4.6 days) | 1 day | STD | 1 | Attack -5; sexual socials vetoed; 1% per tick to arthritis |
| Frost bite | Disease_FrostBite.java | 450 (30 min) | none | none | 1 | Random extremity chosen; amputated if it runs out |
| Flu | Disease_Flu.java | 30 (2 min) | 5 | CONS/PROX/CONT/STD | 1 | CON -4, STR -5; movement halved; mana -1/3; 2d(lvl+1)+1 per cycle; 1% fever |
| Infection | Disease_Infection.java | 34 (2.3 min) | 5 | none | 0 | Healing halved; 1 dmg per cycle; final tick may become gangrene |
| Cancer | Disease_Cancer.java | 99999 (about 4.6 days) | 1 day | none | 5 | CON loss grows daily; resources divided by it; death at CON 0 |
| Cold | Disease_Cold.java | 24 (1.6 min) | 5 | CONS/PROX/CONT/STD | 0 | CON -2, STR -3; damage while HP is high; final tick may become pneumonia (PCs) |
| Diabetes | Disease_Diabetes.java | 999999 (about 46 days) | 1 day | CONS | 8 | 2% obesity after eating when full; 1% per day nausea |
| Colour blindness | Disease_ColorBlindness.java | 99999 | none | none | 10 | Strips colour codes from player output |
| Chlamydia | Disease_Chlamydia.java | 10 days (10 h) | 1 day | STD | 5 | Movement halved; sexual socials vetoed |
| Amnesia | Disease_Amnesia.java | 34 (2.3 min) | none | none | 3 | Forgets known abilities (MIND save +10) |
| Giggles | Disease_Giggles.java | 15 (1 min) | 3 | PROX | 3 | Not malicious; noise and spread to a random inhabitant |
| Asthma | Disease_Asthma.java | 99999 (about 4.6 days) | 5 | none | 2 | 1% per cycle attack for half current HP; movement a quarter |
| Eczema | Disease_Eczema.java | 1 day (1 h) | 6-10 ticks | none | 2 | DEX -3, CHA -3; periodic message |
| Fleas | Disease_Fleas.java | 35 (2.3 min) | 5 | CONT/STD/PROX | 1 | DEX -4; spreads each cycle |
| Anemia | Disease_Anemia.java | 4-7 days (4-7 h) | none | CONS | 2 | DEX -3, STR -3 |
| Arthritis | Disease_Arthritis.java | 999999 (about 46 days) | none | none | 4 | DEX -3; reached from gonorrhea |
| Anosmia | Disease_Anosmia.java | 99999 (about 4.6 days) | none | none | 10 | Cannot smell |
| Blindness | Disease_Blindness.java | 99999 (about 4.6 days) | none | none | 10 | Cannot see |
| Deafness | Disease_Deafness.java | 99999 (about 4.6 days) | none | none | 10 | Cannot hear |
| AIDS | Disease_Aids.java | 999999 (about 46 days) | none | STD/INH | 9 | Save -200, so every exposure takes; passed to children |
| Carrier | Disease_Carrier.java | 999999 (about 46 days) | none | none | 0 | No effects; a tag. Help text says it "carries" others, which the code does not do |

Sample mechanics worth porting:

- The escalation table: Flu to Fever (1%/cycle), Gangrene and Infection to Fever (1%/cycle), Cold to Pneumonia (final tick, PCs), Infection to Gangrene (final tick), Gonorrhea to Arthritis (1%/tick), Diabetes to Obesity (2% after eating when full) and Nausea (1%/day).
- The veto list for mind effects: spells known (Apathy, Amnesia, Alzheimers), trade commands (Hatred), sexual socials (Depression, Gonorrhea, Chlamydia), running (FootFungus), eating (Cannibalism).
- Message pools: the ten fever hallucinations, the smell lines, the Hatred social list (`Disease_Hatred.java:61-65`).

## Notable design ideas

- Diseases are abilities. They reuse the spell machinery (`copyOf`, `startTickDown`, `unInvoke`, messages), so they stack, expire and clean up the same way spells do.
- The final tick is a moment of truth. Cold and Infection test their last tick, and an untreated patient may silently get something worse.
- Mind effects are vetoes and rewrites inside `okMessage`, not separate code paths. Apathy, Amnesia, Alzheimers, Hatred, Blahs and Depression all use this, so any new action inherits them.
- The hate list reuses the zapper mask language (`CMLib.masking().getPreCompiledMask`, `Disease_Hatred.java:116`), so "hates elves above level 10" is plain text in the disease's misc text.
- Chronic diseases turn a stat into a death timer (CON for Anthrax and Cancer, HP for Gangrene). The death is then a normal `postDeath`.
- Infection moves through the economy. Carcasses and butchered food carry CONSUMPTION and CONTACT diseases (`StdRace.java:1008`, `Butchering.java:127`), so infected meat is a real risk.
- Immunity follows resistance as well as cure. A resisted exposure gives the same lasting protection as recovery.
- Perception diseases are sense-mask bits (`PhyStats.CAN_NOT_SEE` and friends), not special cases in the look or hear code.
- HeatExhaustion's look redirect is a small, elegant perception effect that needs no new output path.
- Carrier is a tag. Quest data lists it among dispel targets (`resources/quests/random/normal_dispel1.xml:220`), so a disease can exist purely as a classification.

## Problems and cruft

- Cannibalism's eat gate cannot pass for standard races. `food.name().toLowerCase().indexOf(mob.charStats().raceName()) < 0` (`Disease_Cannibalism.java:174`) compares a lower-cased name with the race name. Race names are capitalised (`Races/Human.java:40` returns "Human", and `Common/DefaultCharStats.java:476-483` returns the race name). For any race whose name contains a capital letter, which includes Human and Elf, every food item is therefore refused. This follows from reading the code and was not run.
- Apathy's mood suppression is checked only once. In `getMood()` (`Disease_Apathy.java:145-158`), `mood` is assigned before the suppression check returns null. Later calls return the cached mood even when the creature's ambiance suppresses moods.
- Hatred's additive edits are lost on infection. `setMiscText` with a leading "+" or "<" does not call `super.setMiscText` (`Disease_Hatred.java:97-106`), so `text()` does not reflect them. `Disease.java:371` copies `text()` into each new infection, so additions disappear. `whatDesc` is also left stale when every entry is removed (118-119).
- Death happens inside stat recalculation. Gangrene (`Disease_Gangrene.java:185-193`), Anthrax (`Disease_Anthrax.java:174-180`) and Cancer (`Disease_Cancer.java:157-165`) call `postDeath` from `affectCharState` or `affectCharStats`. Those run on every recalculation, and the `norecurse` flag stops only recursion, not repetition. Death belongs in `tick`.
- Cancer's resource division is unguarded. `conDown` becomes -1 after death (`Disease_Cancer.java:158`), but `affectCharState` divides by it (173-175) with no check for `conDown < 0`. Resources would flip sign until the effect is removed. The gap was not measured.
- Several escalations do not check for a null ability. Gonorrhea, Cold and Infection call `CMClass.getAbility(...)` and invoke the result (`Disease_Gonorrhea.java:144-145`, `Disease_Cold.java:146-147`, `Disease_Infection.java:157-158`). Flu and Gangrene check first.
- HP bookkeeping is stale. Gangrene and Infection update `lastHP` only on non-cycle ticks, because the cycle branch returns first (`Disease_Gangrene.java:158-172`, `Disease_Infection.java:~151-175`). The "halve the healing" rule therefore compares against an old value. Anthrax writes `lastHP` (`Disease_Anthrax.java:159`) and never reads it.
- Leeches edit state from inside `tick`. It sets max HP to current minus one (`Disease_Leeches.java:164`). Its HP ceiling (`affectCharState`, 183-195) holds HP at the lowest value seen while infected. The persistence of the max-HP change after cure was not traced.
- Many declared cycles are dead. These classes never read `diseaseTick`: Cannibalism (100), FrostBite (50), Arthritis (50), AIDS (50), Carrier (50), Diarrhea (20), Blahs (20), Depression (20), Anemia (20), Amnesia (5), Apathy (5), Alzheimers (5, which uses `everyTick`), HeatExhaustion (3, which uses `changeDown`), and the sense-mask and colour diseases (`TICKSPERMUDDAY`). The constants are misleading.
- SPREAD_PROXIMITY is not implemented in `Disease.executeMsg`. The periodic `catchIt(mob)` calls ignore the route bits (`Disease_Flu.java:145`). The bitmap is partly descriptive.
- Item diseases may never time out. `StdAbility.tick` decrements only for `TICKID_MOB` (`StdAbility.java:2849`). Whether item effects are ticked with that ID was not traced, so an item disease may persist until removed.
- Fever's victim choice has no hostility check. It calls `fetchRandomInhabitant()` (`Disease_Fever.java:136-138`) and re-picks on every combat tick, so a friendly creature can become a target.
- Durations ignore caster level by design (`StdAbility.java:2176-2177`). That is consistent but means a level 1 and a level 50 caster produce the same disease.
- Help text and code disagree in several places (`guides/refs/affbehav.html`, entries around lines 1894-2064):
  - Apathy says it is "spread by being spoken to by someone else with it". The code spreads from the infected speaker to the creature spoken to (`Disease.java:276-281`).
  - Anemia says "not contagious", but its CONSUMPTION route can carry it through meat (`StdRace.java:1008`).
  - Anthrax says "spread by contact". The code is CONSUMPTION|CONTACT (`Disease_Anthrax.java:120-122`).
  - Gangrene says it "depletes maximum hit points". The code lowers current HP per day (`Disease_Gangrene.java:184`).
  - AIDS does not mention inheritance, which `Pregnancy.java:634` implements.
  - Alzheimers does not mention its CHA +2 (`Disease_Alzheimers.java:208`).
  - Blahs is said to come from excessive idle time. No idle trigger was found in this bucket. The source was not searched.
- Multiplayer assumptions: Depression forces sleep when `mob.session().getIdleMillis() > 10000` (`Disease_Depression.java:189-192`). ColorBlindness uses a network session filter. Hatred's groups and the random-inhabitant targeting assume other people are present.
- Boilerplate: nearly every class repeats the same ID, name, display-text and canAffect or canTarget methods, which could be one table.

## Takeaways for a Python SUD

1. Keep three layers: a data record per disease, a small runtime object per infection, and hooks into one action pipeline. A sketch in Python:

```python
from dataclasses import dataclass, field
from enum import IntFlag
import random

class Route(IntFlag):
    CONTACT = 1      # touch, hands, natural attacks
    FOOD = 2         # eating or drinking a contaminated item or carcass
    ROOM = 4         # periodic exposure to others in the same room
    SPEECH = 8       # speaking to someone who can hear
    INHERIT = 16     # passed to a child at birth

@dataclass(frozen=True)
class DiseaseSpec:
    key: str
    name: str
    duration: int                 # game ticks
    cycle: int = 0                # ticks between periodic effects; 0 = none
    difficulty: int = 0           # 0-9, compared with cure strength
    routes: Route = Route(0)
    stat_mods: dict[str, int] = field(default_factory=dict)   # {"CON": -4}

FLU = DiseaseSpec("flu", "Flu", duration=30, cycle=5, difficulty=1,
                  routes=Route.FOOD | Route.ROOM | Route.CONTACT,
                  stat_mods={"CON": -4, "STR": -5})

# Escalations as data: (from, when, chance, to). "final" means the last tick.
ESCALATIONS = [
    ("flu", "cycle", 0.01, "fever"),
    ("infection", "final", None, "gangrene"),   # chance from save and CON, computed in code
]

def roll_catch(rng: random.Random, char, spec: DiseaseSpec) -> bool:
    if spec.key in char.conditions:
        return False
    if char.immune_until.get(spec.key, 0) > char.game_tick:   # game time, not wall clock
        return False
    return rng.randint(1, 100) > char.save("disease")
```

2. Borrow the table, not the classes. Most of the 36 diseases are one `DiseaseSpec` line. Write hooks only for the mechanics that need them: the hostility mask (Hatred), wandering (Alzheimers), action vetoes (Apathy, Amnesia), the look redirect (HeatExhaustion), the chronic clocks (Gangrene, Anthrax, Cancer) and the poison and HP rules (Leeches).
3. Borrow the escalation chains as data. The "final tick" rule (cold to pneumonia, infection to gangrene) is a strong, simple piece of game design.
4. Borrow immunity after both cure and a resisted exposure. Use game time, not real time. CoffeeMud's ten real hours is ten mud days at its defaults.
5. Use one `can_act(char, action)` veto chain and one `speak(char, text)` rewrite instead of a message bus. Mind diseases then reduce to a few short functions.
6. Simplify: drop the sexual-transmission routes, or fold them into a generic "close contact" route. Drop the network session filter; keep colour stripping only as a display option. Keep the speech route (HEARING) and the carcass route, which give a text game a good link to food and crafting.
7. Fix in the port: compute death in one `update()` step, never inside stat recalculation. Null-check every escalation lookup. Make every route check explicit, with no inert bits. Make every duration game time.
8. Single-player: remove the "random inhabitant" targeting and the idle-session sleep. Hatred's group rules become rules for NPC companions. Proximity spread becomes "same location, once per turn".

## Coverage

- Read in full (10 files): `Disease.java` (410), `Disease_Flu.java` (179), `Disease_Hatred.java` (372), `Disease_FootFungus.java` (231), `Disease_Apathy.java` (216), `Disease_Depression.java` (214), `Disease_Alzheimers.java` (212), `Disease_HeatExhaustion.java` (212), `Disease_Gangrene.java` (207), `Disease_FilthFever.java` (198).
- Read with trivial accessors filtered out (26 files): `Disease_Cannibalism`, `Disease_Leeches`, `Disease_Diarrhea`, `Disease_Blahs`, `Disease_Anthrax`, `Disease_Fever`, `Disease_Gonorrhea`, `Disease_FrostBite`, `Disease_Infection`, `Disease_Cancer`, `Disease_Cold`, `Disease_Diabetes`, `Disease_ColorBlindness`, `Disease_Chlamydia`, `Disease_Amnesia`, `Disease_Giggles`, `Disease_Asthma`, `Disease_Eczema`, `Disease_Fleas`, `Disease_Anemia`, `Disease_Arthritis`, `Disease_Anosmia`, `Disease_Blindness`, `Disease_Deafness`, `Disease_Aids`, `Disease_Carrier`. The filter removed only ID, name, displayText, canAffectCode, canTargetCode, abstractQuality, putInCommandlist and the localised-name fields. Method bodies were kept. The catalogue was built from this same reading.
- Outside the bucket, read in part: `Abilities/interfaces/DiseaseAffect.java` (in full), `Abilities/StdAbility.java` (the malicious-affect, immunity, tick, unInvoke and invoke methods), `Abilities/Misc/TemporaryImmunity.java` (most), `Abilities/Languages/Blah.java` (in full), `Abilities/Misc/Mood.java` (the MoodType enum only), excerpts of `Races/StdRace.java`, `Abilities/Common/Butchering.java`, `Abilities/Misc/Scavenge.java` and `Abilities/Misc/Pregnancy.java`, excerpts of `Libraries/Dice.java`, `Libraries/MUDFight.java`, `Common/DefaultCharStats.java`, `Libraries/CoffeeTime.java`, `Common/DefaultTimeClock.java`, `coffeemud.ini`, `core/CMProps.java` and the help entries in `guides/refs/affbehav.html`.
- Skipped: the other 34 files in `Abilities/Diseases/` (part 2 of this family), `Abilities/Misc/Gait.java`, `Abilities/Misc/Amputation.java`, `Abilities/Misc/Soiled.java`, `Abilities/Spells/Spell_Hungerless.java`, `Common/interfaces/Session.java` (only the SessionFilter interface name was checked), and the cure branch of `Abilities/Prayers/Prayer_CureDisease.java` (only line 142 was read).
