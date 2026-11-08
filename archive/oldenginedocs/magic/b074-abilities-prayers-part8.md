# Prayers, part 8: sensing, curses, boons and word magic

> Part of the CoffeeMud NEWOVERVIEW. Bucket: `b074` (Abilities/Prayers [part 8/8: Prayer_SenseLaw .. Prayer_WordOfLaw]) · 46 files · 8672 lines of Java.

Citation convention: a bare `File.java:NNN` is in `com/planet_ink/coffee_mud/Abilities/Prayers/`. Files outside that folder are given with their path from the CoffeeMUD folder. Line numbers were checked against the source.

## Summary

This bucket is the last eighth of CoffeeMud's clerical prayer family: 46 subclasses of `Prayer` (itself a `StdAbility`), running alphabetically from `Prayer_SenseLaw` to `Prayer_WordOfLaw`. They fall into a few groups: detection prayers that reveal things (life, traps, undead, a deity's followers, a target's skills, saves or score, a corpse's last words); alignment-keyed words that shift armour and attack values by the target's law, chaos, good or evil standing; curses and taints that silence, taint, petrify, tax or topple a target; prayers that live in objects or create creatures (a stored prayer, a snake staff, unholy armament, an elemental); and environmental acts (weather, tremor, a wave, a thunderbolt). Two mechanisms recur and matter beyond this code. `StdAbility` reads one integer duration as literal ticks, a level-based standard, or a percentage depending on its sign, and a two-phase message hook (`okMessage`, then `executeMsg`) lets one effect veto, zero or rewrite another's events. For a single-player SUD the detection family and the "alignment as a stat modifier" idea transfer most cleanly.

## Key files and types

| File or class | Role | Lines |
|---|---|---|
| `Prayer.java` (bucket base, read in full) | Alignment gate, relic and infusion helpers, shared `invoke` | 432 |
| `Prayer_StorePrayer.java` | Stores a second prayer in a held item; a spoken phrase re-casts it, charge-limited | 483 |
| `Prayer_SnakeStaff.java` | Holy staff becomes a summoned snake pack that follows the caster | 299 |
| `Prayer_UnholyArmament.java` | Materialises a full set of temporary unholy gear in the room | 299 |
| `Prayer_WordOfLaw.java` | Room-wide word: lawful targets buffed, chaotic items and protections stripped | 298 |
| `Prayer_WaterToWine.java` | Freshwater becomes wine through a `Poison_Alcohol` effect; reverts on expiry | 289 |
| `Prayer_Vampirism.java` | Hunger, thirst, darkness vision, no swimming; monsters hunt blood | 286 |
| `Prayer_SummonElemental.java` | One elemental (earth, fire, air or water) that follows the caster | 275 |
| `Prayer_WordOfChaos.java` | Mirror of Word of Law for chaos | 257 |
| `Prayer_Weather.java` | Advances area weather through a transition table | 230 |
| `Prayer_Sermon.java` | Room-wide charm: listeners follow the caster and cannot leave | 226 |
| `Prayer_Tremor.java` | Sits down enemies in range; legged targets fall | 217 |
| `Prayer_Stoning.java` | Clears legal warrants and hires a hostile citizen crowd | 215 |
| `Prayer_Tongues.java` | Makes a target speak random languages through a language proxy | 215 |
| `Prayer_UndeniableFaith.java` | Converts a target's worship to the caster's deity | 206 |
| `Prayer_UnholyWord.java` | Room-wide word: evil buffed, good debuffed, blessings stripped | 190 |
| `Prayer_SunCurse.java` | Petrifies the target the first time they stand in sunlight | 189 |
| `Prayer_TrueResurrection.java` | Raises a corpse named by the caster; cooldown per corpse | 176 |
| `Prayer_Wave.java` | Pushes enemies out through an exit; damage split among them | 170 |
| `Prayer_SenseLife.java` | Reports life in adjacent and current rooms on room change | 210 |
| `Prayer_SenseTraps.java` | Reports traps in adjacent and current rooms on room change | 260 |
| `Prayer_SenseParish.java` | Counts a named deity's followers in radiating rooms | 189 |
| `Prayer_SenseProfessions.java` | Lists a target's abilities of one class code; base of four siblings | 128 |
| `Prayer_SenseResistances.java` | Lists saving throws above zero and resister effects | 131 |
| `Prayer_SpeakWithDead.java` | Asks a corpse about its death | 145 |
| `Prayer_SoulPeering.java` | Prints another character's score to the caster | 99 |
| `Prayer_ShareBoon.java`, `Prayer_TransferBoon.java`, `Prayer_TransferBane.java` | Copy or move the caster's blessings and curses | 155, 154, 159 |
| Other files (Tithe, UndeathGuard, Sober, Wings, Stasis, Silence, UnholyPortent, TaintOfChaos, TaintOfEvil, UndeadInvisibility, Thunderbolt, SenseLaw, SenseMagic, SenseUndead, SensePrayers, SenseSkills, SenseSongs, SenseSpells) | Smaller status, curse, detection and damage prayers; full table in "Content worth mining" | 195, 194, 144, 144, 174, 162, 162, 167, 167, 195, 107, 154, 152, 157, 62, 62, 62, 62 |
| `com/planet_ink/coffee_mud/Abilities/StdAbility.java` (excerpts, outside bucket) | Affect helpers, tick-down, expiry, `properTargets` | 3018 |
| `com/planet_ink/coffee_mud/Libraries/MUDFight.java` (excerpts, outside bucket) | `properTargets`, armour formula, `rollToHit` | 3817 |
| `com/planet_ink/coffee_mud/Libraries/Factions.java` (excerpt, outside bucket) | `getAlignPurity`, the alignment distance used by the gate | 2109 |
| `com/planet_ink/coffee_mud/Libraries/Dice.java` (excerpt, outside bucket) | `roll(number, die, modifier)` and `rollPercentage` | 414 |
| `com/planet_ink/coffee_mud/Abilities/Properties/Prop_ScrapExplode.java` (outside bucket, `executeMsg` read) | Explosion when the item is scrapped | 93 |
| `com/planet_ink/coffee_mud/Abilities/Prayers/Prayer_Harm.java` (outside bucket, excerpt) | Harm-family damage that Undeath Guard fires | 136 |
| `com/planet_ink/coffee_mud/Races/StdRace.java` (excerpt, outside bucket) | `canBreedWith`, used as the "is alive" test | 2204 |
| `com/planet_ink/coffee_mud/Libraries/Sense.java` (excerpt, outside bucket) | `isUndead` by racial category | 3140 |

## How it works

### 1. The base class and the alignment gate

- **Identity.** `Prayer` (`Prayer.java:37`) is a `StdAbility` with classification `ACODE_PRAYER` (`Prayer.java:88`), trigger words `PRAY` and `PR` (`Prayer.java:77`), and no default affect or target code (`Prayer.java:60-69`). Subclasses choose their own targeting.
- **Deity-aware wording.** `prayWord`, `prayForWord`, `prayingWord`, `ofDiety` and `hisHerDiety` return the caster's deity name when there is one, and a generic form otherwise (`Prayer.java:99-142`). Example output: "pray(s) to <deity>" or "pray(s)".
- **Relics.** `getRelicPrayers` and `getDischargeableRelic` (`Prayer.java:151-183`, `307-328`) treat an item as a relic when it holds a `Dischargeable` ability container, or when it is a `Wand` whose spell is a prayer. `setRelicCharges`, `clearRelicMagic` and `getRelicCharges` (`Prayer.java:330-365`) wrap the charge count.
- **Infusions.** `infusePhysicalByAlignment` (`Prayer.java:208-255`) attaches a non-removable infusion effect to an item, chosen by the caster's alignment. On the goodness axis the choice is `Prayer_InfuseHoliness`, `Prayer_InfuseUnholiness` or `Prayer_InfuseBalance`. On the law axis it is `Prayer_InfuseDiscipline`, `Prayer_InfuseImpunity` or `Prayer_InfuseModeration`. The infusion records the caster's worship ID as its misc text (`Prayer.java:250-251`). It is written only when no deity infusion already exists (`Prayer.java:224-227`). `checkInfusionMismatch` and `checkRequiredInfusion` (`Prayer.java:185-206`) compare the caster's god with the item's infusion, and `clearInfusions` (`Prayer.java:294-305`) removes them.
- **Mob alignment mask.** `infuseMobAlignment` (`Prayer.java:257-292`) adds a `Prop_WearZapper` whose mask reads, for example, `+FACTION -EVIL -NEUTRAL`, so the item works only for that alignment.
- **The gate.** `Prayer.invoke` (`Prayer.java:422-430`) runs `StdAbility.invoke` first, then `prayerAlignmentCheck` (`Prayer.java:372-420`). The check runs only for a non-auto cast by a player, on a prayer the caster holds (`mob.isMine`), where the prayer is not already appropriate to the caster's factions, and where its flags are non-zero (`Prayer.java:374-379`). It also skips abilities that disregard the armour check (`Prayer.java:376`).
- **Band.** Holy-only flags give band 1000, unholy-only give band 0, and anything else (neutral, law, chaos) gives 500 (`Prayer.java:381-389`).
- **Basis.** The basis is `Factions.getAlignPurity` for the relevant pole (`Prayer.java:391-401`). Neutral subtracts 10 (`Prayer.java:400`). `getAlignPurity` returns a distance from the pure end of the alignment range, not a purity score (`Libraries/Factions.java:873-905`). A pure caster has a basis near 0.
- **Roll.** The gate passes when `CMLib.dice().rollPercentage() > basis` (`Prayer.java:403`). `rollPercentage` returns 1 to 100 (`Libraries/Dice.java:409-411`), so an impure caster fails with a chance equal to their basis in percent.
- **Messages.** The failure line is one of "The goodness of @x1 disrupts your prayer." (`Prayer.java:410`), "The evil nature of @x1 disrupts your prayer." (`Prayer.java:407`), or a neutral "disrupts your thought" line (`Prayer.java:413-416`).
- **Unverified.** Whether mana is charged before this gate fails was not traced. `StdAbility.invoke` (`Abilities/StdAbility.java:1964` onward) does the cost and compounding checks, and the deduction point was not followed.

### 2. How an effect lands: the message pattern

- **Standard shape.** Most invocations do the same five steps: build a `CMMsg` with the source, target, this ability and a verbal or visual code, ask the room `okMessage`, call `room.send`, then branch on `msg.value()<=0`. A listener can set the value to refuse or reduce the event. Examples: `Prayer_WordOfLaw.java:245-249`, `Prayer_Sermon.java:202-207`, `Prayer_Silence.java:145-151`.
- **Veto and rewrite.** `Prayer_Stasis.java:132-133` sets a damage message's value to zero inside `okMessage`, so the damage does nothing. The same file blocks any malicious message aimed at the protected mob and replies that the stasis field protects them (`Prayer_Stasis.java:135-139`). `Prayer_Tongues.java:140-165` passes every message the cursed mob sends to the current language ability object, which may rewrite it.
- **Resistance hook.** `maliciousAffect` takes a fifth argument, `additionAffectCheckCode`. When it is 0 or more, `StdAbility.maliciousAffect` sends an extra check message through the room. If a listener sets its value above zero, the effect is not applied and the call returns null (`Abilities/StdAbility.java:2185-2199`). `Prayer_Sermon.java:206` passes a malicious-verbal mind code; `Prayer_Stoning.java:197` passes a malicious-justice code. Most others pass -1, which skips the check.
- **Fizzles.** Failure paths call `beneficialWordsFizzle` (`Abilities/StdAbility.java:2210`), `beneficialVisualFizzle` (`:2222`) or `maliciousFizzle` (`:2253`). These broadcast the failed attempt, so onlookers see it.

### 3. Duration model

`StdAbility` converts one integer into a tick count, and the sign of the integer picks the rule.

| Call | Rule | Source |
|---|---|---|
| Beneficial, n > 0 | Exactly n ticks | `Abilities/StdAbility.java:2314-2315` |
| Beneficial, n = 0 | `(casterLevel*4)+40` ticks, with the caster level capped at target level plus EXPRATE when an abuser casts on another | `Abilities/StdAbility.java:2318-2330` |
| Beneficial, n < 0 | |n|% of the standard value | `Abilities/StdAbility.java:2329-2330` |
| Malicious, n > 0 | Exactly n ticks | `Abilities/StdAbility.java:2176` |
| Malicious, n = 0 | `round(adjustedLevel*1.1)+25`, then the level fudge below | `Abilities/StdAbility.java:2178` |
| Malicious, n < 0 | |n|% of the standard value | `Abilities/StdAbility.java:2180-2181` |

- **Level fudge.** For a malicious effect on a non-room target, `adjustMaliciousTickdownTime` (`Abilities/StdAbility.java:2140-2170`) takes the level difference between target and caster, clamps it to plus or minus EXPRATE, and removes `tickDown * diff/(EXPRATE+1)` ticks. A higher-level target therefore gets a shorter effect. Inside that branch the value is capped at a third of an hour of ticks (`Abilities/StdAbility.java:2158-2159`). Outside it, the value is capped at `TICKSPERMUDDAY` and floored at 2 (`Abilities/StdAbility.java:2162-2166`).
- **Start.** `startTickDown` (`Abilities/StdAbility.java:698-712`) adds `addedTickTime` (`:709`) and each class's duration modifier (`:711-713`) before the effect is attached, so a printed base value is not the final length.
- **Clock.** Ticks are 4000 ms by default (`com/planet_ink/coffee_mud/core/CMProps.java:681`), settable by `TICKTIME` (`core/CMProps.java:2720`).
- **Expiry.** `expirationDate()` returns remaining ticks times tick length (`Abilities/StdAbility.java:529-532`). `setExpirationDate` accepts either an absolute epoch time or a duration, depending on whether the value is above the current time (`Abilities/StdAbility.java:535-542`). `Prayer_TransferBoon.java:139` passes a duration; `Prayer_ShareBoon.java:143` adds the current time to a duration. Both give the same remaining ticks.
- **Silence.** `Prayer_Silence.java:151` passes `-levelDiff`, with `levelDiff` clamped at 0 (`Prayer_Silence.java:135-137`). Through the rule in the table, a target at or below the caster's level gets the full standard value (the zero case). A target one level above gets 1% of it, and a target 50 levels above gets 50%. The curve is discontinuous and rises with the level gap, which looks inverted (see "Problems and cruft").
- **Tongues.** `Prayer_Tongues.java:197` multiplies the standard malicious value by 50, then passes the product as a literal tick count (`Prayer_Tongues.java:198`). Using the formula alone, a level-20 caster gets a standard of 47 ticks, so roughly 2,350 ticks (about 2.6 hours at 4 s per tick) before the target-level fudge. The factor of 50 is not explained in the code.

### 4. The Word family: alignment as a stat modifier

Three prayers share one shape. They walk the room's inhabitants, send one speech message to each, and strip or buff based on alignment.

- **Flags and targets.** Word of Law carries `FLAG_LAW` (`Prayer_WordOfLaw.java:86`), Word of Chaos carries `FLAG_CHAOS` (`Prayer_WordOfChaos.java:86`), and Unholy Word carries `FLAG_UNHOLY` (`Prayer_UnholyWord.java:86`). All three target room mobs (`CAN_MOBS`).
- **Per-listener flag.** A target of the opposing alignment makes the speech malicious: a chaotic target for Word of Law (`Prayer_WordOfLaw.java:240-241`), a lawful target for Word of Chaos (`Prayer_WordOfChaos.java:212-213`), a good target for Unholy Word (`Prayer_UnholyWord.java:146-147`).
- **Hearing.** Each target must be able to hear the caster (`canBeHeardSpeakingBy`). Otherwise the target gets "did not hear the word of law!" (`Prayer_WordOfLaw.java:251-279`), or the equivalent message for the other two.
- **Stat effect.** The affect is `beneficialAffect(..., 0)` (`Prayer_WordOfLaw.java:272`), which is the standard value from the table. Its stat effect, `affectPhyStats`, uses x = `getXLEVELLevel(invoker)`:

| Word | Buffed | Debuffed |
|---|---|---|
| Word of Law (`Prayer_WordOfLaw.java:189-199`) | Lawful: armour -(15+6x), attack +(20+4x) | Chaotic: armour +(15+6x), attack -(20+4x) |
| Word of Chaos (`Prayer_WordOfChaos.java:103-113`) | Chaotic: armour -(15+6x), attack +(20+4x) | Lawful: armour +(15+6x), attack -(20+4x) |
| Unholy Word (`Prayer_UnholyWord.java:100-110`) | Evil: armour -(15+6x), attack +(20+4x) | Good: armour +(15+6x), attack -(20+4x) |

- **Why the signs work.** Lower armour is better. `MUDFight.adjustedArmor` evaluates the armour formula from `coffeemud.ini`, which subtracts 100 from the armour stat (`Libraries/MUDFight.java:348-373`; default at `coffeemud.ini:2352`). `rollToHit` negates that value, so a negative defence gives a small hit score and a positive one nearly always hits. A larger attack value raises the hit score (`Libraries/MUDFight.java:409-425`).
- **Invoker exemption.** Word of Law and Word of Chaos skip the stat effect on their caster (`Prayer_WordOfLaw.java:186-187`; `Prayer_WordOfChaos.java:99-100`). Unholy Word has no such check (`Prayer_UnholyWord.java:90-111`), so an evil caster buffs themselves.
- **Stripping.** Word of Law removes chaos from each listener. It repeatedly takes a random chaos-flagged item from the target's inventory, has the target release it with a drop message, and removes lower chaos effects from it (`Prayer_WordOfLaw.java:252-272`). It then removes other holy protections and lower chaos from the target (`Prayer_WordOfLaw.java:270-271`). Word of Chaos and Unholy Word instead take one blessed item from the caster's own inventory (`Prayer_WordOfChaos.java:225-230`; `Prayer_UnholyWord.java:159-163`), and then strip the target's protections.
- **Helpers.** `endAllOtherLegalProtections`, `endLowerProtections` and `endLowerChaos` (`Prayer_WordOfLaw.java:114-161`) and `endLowerLegalProtections` and `endAllOtherChaosProtections` (`Prayer_WordOfChaos.java:154-191`) call `unInvoke` on protections whose `lowestQualifyingLevel` is below a threshold (at or below it, in some helpers). `Prayer_UnholyWord.java:159-165` uses `Prayer_Curse.getSomething` and `endLowerBlessings` in the same way.
- **Mending.** Word of Law implements `MendingSkill` (`Prayer_WordOfLaw.java:35`). `supportsMending` returns true when the target holds chaos items or chaos effects (`Prayer_WordOfLaw.java:169-174`), so cleansing tools can use it.
- **Messages.** Word of Law says "The word of law is spoken." on auto-cast and "speak(s) the word of law" otherwise (`Prayer_WordOfLaw.java:225-226`). It also sends a bless sound (`:227`). Its aura ends with "blinding lawful aura fades" (`:214`). Word of Chaos ends with "The word of chaos has been spoken." (`Prayer_WordOfChaos.java:127`).

### 5. Prayers that live in objects, and prayers that make things

**Store Prayer** (`Prayer_StorePrayer.java`) is a prayer that carries another prayer.

- **Carrier rules.** The item must be `WORN_HELD` and not food, drink, instrument or readable (`Prayer_StorePrayer.java:389-397`). It must not already hold a different prayer (`:430-435`).
- **Data model.** The effect's misc text is `PRAYERID/charges` (`:109-143`). `getCharges` parses the second field, and `setCharges` removes the effect when charges run out (`:134-143`).
- **Storing.** The caster names the stored prayer by its prefix. The prayer must be savable or qualify by level (`:407-418`) and be enchantable (`:425`). Mana is the sum of this ability's cost and the stored prayer's cost, capped at max mana (`:447-450`). Each store adds one charge (`:472`).
- **Triggering.** Speech that contains the item's name or deity triggers `waveIfAble` (`:176-225`). The text after the name is passed as the argument, and the stored prayer is cast with `A.invoke(mob, V, target, true, me.phyStats().level())` (`:219`). The speech path works through a trailer message of type `TYP_WAND_USE` (`:227-273`). Messages: "glows brightly" (`:218`), "seems spent" (`:202`, `:221`), and a deity-rejection line (`:208`).

**Snake Staff** (`Prayer_SnakeStaff.java`) turns a holy staff into a snake pack.

- **Eligible staff.** The wielded item must be a `Weapon` whose class is in `getWeaponClasses`: staff always, plus whatever the caster's deity wields, holds or has as items (`:178-191`, `:210-215`).
- **Summon.** The staff is removed from the caster (`:239-240`) and `determineMonster` creates a `GenMob` called "a bunch of snakes" (`:253-297`). Its level is the caster's level plus (XLEVEL + X1)/2 (`:229`). It gets a poison ability at proficiency 100 (`:277-281`), a `CombatAbilities` behaviour (`:276`), and a race of "Snake" (`:261`). It is set to follow the caster (`:235`), and the caster's combat is cleared (`:233-234`).
- **Duration.** `beneficialAffect(mob, monster, asLevel, 10)` (`:230`) gives a base of 10 ticks before the start-up modifiers. The effect also ends early if the snakes are not in combat and are not following (`:137-156`). It also ends on quit (`:159-170`).
- **Return.** On uninvoke the staff reappears in the caster's hand with "reform(s) into ... in <your> hands" (`:114`). The snakes are destroyed (`:127-133`).

**Unholy Armament** (`Prayer_UnholyArmament.java`) creates gear.

- **Not in combat.** The prayer refuses while `mob.isInCombat()` (`:105-109`).
- **Slots.** It walks `checkOrder`, a list of 12 wear positions (`:87-100`). For each free slot it builds an item: a `GenWeapon` sword (`:133-147`) for the wield slot, a `GenShield` (`:152-165`) for held, and a `GenArmor` for the rest (`:168-242`). The cape is cotton and the rest mithril (`:170-174`). Stats come from `CMLib.itemBuilder().timsItemAdjustments` at level + 2 x XLEVEL (`:144`, `:161`, `:176`).
- **Binding.** Each item gets `Prop_HaveZapper` with mask `ACTUAL -GOOD -NEUTRAL -NAMES "+<caster name>"` (`:243-247`), `Prop_ScrapExplode` (`:249-251`), and a copy of this prayer that records the caster's name (`:252-254`).
- **Limit.** The count of matching items in the caster and room is checked against one per slot, or two for the wrists (`:205-212`, `:256-262`).
- **Placement.** The item is added to the room with `Expire.Monster_EQ` (`:289-290`), so it expires with monster equipment.
- **Scrapping.** `Prop_ScrapExplode` (`Abilities/Properties/Prop_ScrapExplode.java:75-88`) deals fire damage of 2 x (3 x weight) to whoever scraps the item, and 3 x weight to the caster's group members.

### 6. Summons and the elemental

- **Single follower.** `castingQuality` drops to INDIFFERENT if the caster already controls an elemental (`Prayer_SummonElemental.java:138-146`). `invoke` refuses a second one (`:151-155`).
- **Type.** The type comes from the ability's text (`EARTH`, `FIRE`, `AIR` or `WATER`) or a random roll of 1 to 4 (`:206-213`). Earth and water riders carry two passengers; fire and air carry none (`:221`, `:228-242`).
- **Level.** `level = adjustedLevel(caster, asLevel)`, lifted to `caster level - 5 + XLEVEL/2` if higher, then reduced by 5 when above 5 (`:186-194`). The elemental gets a base HP of `getMobHPBase()+2` (`:186`), speed 1 (`:201`), weight 850 (`:196`), and strength, dexterity and constitution of 25 (`:202-204`).
- **Duration.** `beneficialAffect(mob, myMonster, asLevel, 0)` (`:170`) gives the standard value (`casterLevel*4+40` ticks). The tick destroys the elemental when the caster is no longer its leader (`:96-108`, `:125-135`).
- **Messages.** "appears!" on creation (`:259`), "seems unwilling to follow you" if the follow fails (`:265`), and "start(s) attacking" if the caster had a victim (`:271`).

### 7. Weather, tremor, wave and bolt

**Weather** (`Prayer_Weather.java`):

- **Area penalty.** `size = numberOfProperIDedRooms - (casterLevel + 2*XLEVEL)*20`, floored at 0 (`:87-90`). The proficiency check is `proficiencyCheck(mob, -size, auto)` (`:91`). So the allowance is `(casterLevel + 2*XLEVEL) * 20` rooms, and each room beyond it costs one point of the proficiency roll. A level-20 caster with no XLEVEL can work in 400 rooms without penalty.
- **Step.** `switcher = roll(1,3,0)` (`:97`) picks one of three successors uniformly. The table is in "Content worth mining". It is applied to the caster's current weather (`:99`), then `forceWeatherTick` runs (`:222`).
- **Bias.** Thunderstorm, sleet and blizzard keep themselves two times in three (`:192-199`, `:174-181`, `:102-108`). Rain moves to thunderstorm two times in three (`:165-172`). So repeated casts tend to lock a storm in.
- **Messages.** "The sky changes color!" on auto-cast, "for a change in weather" otherwise (`:94`). On failure: "nothing happens" (`:226`).

**Tremor** (`Prayer_Tremor.java`):

- **Targets.** `properTargets` (`:164`), with a maximum range of `adjustedMaxInvokerRange(3)` (`:72-75`).
- **Effect.** A flying target is unaffected (`:187-188`). A target with no leg body parts is unaffected (`:195`, `:205`). Others get `maliciousAffect(mob, target, asLevel, 2, -1)` (`:197`), which is a base of 2 ticks, then 10 damage if they remain in the room (`:201`).
- **Lock.** `affectPhyStats` adds `IS_SITTING` (`:112`). `okMessage` blocks standing and, once per round, prints "attempt(s) to stand up, and falls back down!" (`:122-131`).
- **Messages.** "thunderously" for the caster (`:179-180`), "The ground underneath <T-NAME> shakes as <T-NAME> fall(s) to the ground!!" (`:201`), and "regain(s) ... feet as the ground stops shaking" on end (`:149`).

**Wave** (`Prayer_Wave.java`):

- **Direction.** The caster names a direction. Monsters pick one by a loop that is nearly always satisfied (`:98-108`). The exit must lead somewhere and must not be up (`:119-125`).
- **Damage.** It is shared by splitting the die: `roll(1, adjustedLevel/numEnemies, numEnemies)` (`:150`), using type `TYP_WATER`, `TYPE_BURSTING` (`:151`).
- **Push.** `chanceToStay = 10 + (STR - (level + 2*XLEVEL)*4)` (`:152`). A target is pushed when the percentage roll is above that and is not 1 (`:153-156`).

**Thunderbolt** (`Prayer_Thunderbolt.java`): one target in the room. Damage is `roll(1, L, L)`, which is between L+1 and 2L for adjusted level L (`:96`, `Libraries/Dice.java:398-405`). It is pure electric damage with no lasting effect.

### 8. Detection: the Sense family

Most detection prayers follow one of two triggers.

- **On room change.** `Prayer_SenseLife`, `Prayer_SenseTraps` and `Prayer_SenseUndead` store `lastRoom` and fire their message only when the affected mob enters a new room (`Prayer_SenseLife.java:161-174`; `Prayer_SenseTraps.java:215-228`; `Prayer_SenseUndead.java:104-122`).
- **On cast.** The others report once: `Prayer_SenseParish`, `Prayer_SenseProfessions` (and its four subclasses), `Prayer_SenseResistances`, `Prayer_SoulPeering` and `Prayer_SpeakWithDead`. `Prayer_SenseMagic` and `Prayer_SenseLaw` instead grant a sense bit for as long as the effect lasts.

Details:

- **Life.** `inhabitated` counts a mob as life if it is not a golem, its race is fertile (`StdRace.canBreedWith(self, false)`, which returns `fertile()`; `Races/StdRace.java:327-330`), and it is not the caster (`Prayer_SenseLife.java:113-127`). `messageTo` builds the answer from adjacent rooms, then the current room (`Prayer_SenseLife.java:129-158`). Messages: "You do not sense any life beyond your own.", "You sense a life force coming from <dirs>, and here."
- **Traps.** `trapCheck` looks for a `Trap` on an object (`Prayer_SenseTraps.java:103-112`). `trapHere` scans the room's exits, items, inhabitants, containers, worn items and shop stock (`:114-184`). Shop stock is checked through `getStoreInventory` (`:171-181`). `messageTo` reports the current room if it has a trap, and otherwise lists neighbouring directions (`:186-212`). Messages: "@x1 is trapped.", "contains something trapped.", "is carrying something trapped.", "has something trapped in stock.", "You sense a trap to <dir>, and <dir>."
- **Undead.** Undead inhabitants of a new room produce "@x1 gives off a cold dark vibe." (`Prayer_SenseUndead.java:113-121`). Undead is a racial category list (`Libraries/Sense.java:550-553`).
- **Parish.** The deity name is resolved through `CMLib.map().getDeity`. The radius is `adjustedLevel/2 + 2*XLEVEL + 10*XMAXRANGE` (`Prayer_SenseParish.java:116-117`). The prayer scans radiant rooms (`:129`) and reports by XLEVEL: below 3 it gives room counts only; above 2 it names each follower; above 4 it adds the inclination range; above 6 it adds the alignment range; above 8 it adds level (`:141-175`).
- **Skill lists.** `Prayer_SenseProfessions` refuses self (`:91-95`) and lists abilities of one class code with proficiency (`:108-119`). `Prayer_SensePrayers` uses `ACODE_PRAYER` (`Prayer_SensePrayers.java:51-55`); `Prayer_SenseSkills`, `Prayer_SenseSongs` and `Prayer_SenseSpells` use the skill, song and spell codes in the same way.
- **Resistances.** `Prayer_SenseResistances` lists saves above zero (`:102-108`) and effects with `FLAG_RESISTER` (`:109-115`). Stasis's +100 saves therefore show up here.
- **Score.** `Prayer_SoulPeering` sends the target's score to the caster's session when the caster is not a monster (`:89-91`).
- **Corpse.** `Prayer_SpeakWithDead` needs a `DeadBody`, a hearing caster, and a body whose creature could speak (`:96-112`). It applies "Being Spoken To" to the corpse and then reports its saved name, time of death, killer and killer's tool (or "a weapon") (`:129-135`).
- **Sense Law and Sense Magic.** Both set a bit on the affected mob, only while `invoker` is set: `CAN_SEE_LAW` ambiance (`Prayer_SenseLaw.java:96-103`) and `CAN_SEE_BONUS` sense mask (`Prayer_SenseMagic.java:96-103`). The unhook messages are "The glowing white fades from <your> eyes." and "The sparkles fade from <your> eyes" (`Prayer_SenseLaw.java:118`; `Prayer_SenseMagic.java:117`).

### 9. Curses, taints and conditions

- **Vampirism** (`Prayer_Vampirism.java`). Its effects are these.
  - **Senses.** Not monsters: in a dark room the victim gains `CAN_SEE_DARK`, and in light they gain `CAN_NOT_SEE` (`:111-118`).
  - **Swimming.** Swim is refused: "You can't swim!" (`:123-137`).
  - **Charisma.** CHA +1 (`:145`).
  - **Food and drink.** Eating food calls `adjHunger(-nourishment)` (`:152-155`), and `adjHunger` with a negative value lowers the hunger value (`Common/DefaultCharState.java:185-198`), so food counts against the vampire. Non-blood liquid lowers thirst (`:165`). Blood, or an empty drink, raises hunger by five times the thirst quench (`:167`).
  - **Tick.** Each tick removes twice the room's thirst per round and 2 hunger (`:197-198`).
  - **Monster hunting.** A monster vampire with thirst or hunger at 0 looks for a dead body of a blood race, or a blood drink, to consume (`:199-239`). Otherwise there is a 10% chance per tick to target a random blood-race inhabitant (`:241-245`).
  - **Invoke.** The proficiency penalty is `-(WIS*2)` (`:262`). On success the target's hunger and thirst are set to 0 (`:272-275`), and the effect is applied (`:276`). Message: "inflicted with vampiric hunger!" (`:271`).
- **Silence** (`Prayer_Silence.java`). `CAN_NOT_SPEAK` (`:97`). The proficiency penalty is `-(WIS*2 + levelDiff*5)` (`:142`). The target is "silenced" (`:152`). Its duration is the level-difference rule in section 3.
- **Sun Curse** (`Prayer_SunCurse.java`). It sets the same dark and light sense bits as Vampirism (`:96-100`). Each tick it checks whether the target is in sunlight (`:111-112`). If so, it prints "THE SUN IS BEATING ONTO YOUR SKIN", casts `Spell_FleshStone` on the target (`:116-118`), and removes itself (`:119-120`). The curse cannot be cast while the sun shines on the target (`:157-163`).
- **Taint of Chaos and Taint of Evil** (`Prayer_TaintOfChaos.java:104-121`; `Prayer_TaintOfEvil.java:104-121`). The effect halves positive faction changes for the inclination or alignment faction. The multiplier is `0.5 - 0.05 x prof`, where prof is the invoker's XLEVEL, or 0 for self-cast (`:113-118`). The faction is identified by a string in `othersMessage` (`:107-109`).
- **Tongues** (`Prayer_Tongues.java`). The effect holds a per-instance map from language ID to a `Language` ability object (`:89`). A random Language ability is chosen from all classes with `ACODE_LANGUAGE`, excluding `Std*` and `GenL*` (`:111-124`). The cache is cleared past 20 entries (`:128-129`). Each language is set to 100 proficiency (`:132`). The effect is placed at the front of the target's effect list (`:203`), so it beats other languages. Message on removal: "The tongues curse is lifted." (`:172`).
- **Undeath Guard** (`Prayer_UndeathGuard.java`). Against a non-undead target the cast is malicious (`:90-96`, `:98-110`). Each tick, if the target is below about 130% of their wimp threshold or at 30% of max HP or less (`:125-126`), the cleric's harm-family prayer fires: `Prayer_Harm`, then `CauseCritical`, `CauseSerious` and `CauseLight` as fallbacks (`:130-136`). The usage counter is saved and restored around the cast (`:139-146`), so passive use does not count toward the cleric's skill.
- **Tithe** (`Prayer_Tithe.java`). The affected mob pays 10% of any coin it gets to the caster, as `num - num/10` (`:115-127`). If the affected mob is a shopkeeper, a buy pays the caster a tenth of the gold price (`:128-147`). The shopkeeper gets +2 CHA (`:105-110`). Message: "<S-NAME> tithes." (`:123`).
- **Stoning** (`Prayer_Stoning.java`). It requires either a legal warrant against the target or the ABOVELAW security flag (`:166-176`). The target must be bound or held unless the cast is automatic or the caster is a sysop (`:178-182`). Then `adjustment = targetLevel - (casterLevel + XLEVEL)/2`, used as the proficiency penalty (`:187-188`). On success the warrants are pardoned (`:197-206`). The cleric also hires up to 10 "AngryCitizen" mobs (`:141-155`). Each tick, a citizen that is no longer in the caster's room is removed (`:113-121`). On a percentage roll of 50 or more a citizen strikes for maxHP/20 damage, minimum 1 (`:124-135`), and otherwise it jeers (`:137-138`).
- **Stasis** (`Prayer_Stasis.java`). +100 to every saving throw (`:90-95`), `CAN_NOT_MOVE` (`:105`), zero damage (`:132-133`), and malicious messages blocked (`:135-139`). Base duration 10 ticks (`:163`).
- **Invisibility to Undead** (`Prayer_UndeadInvisibility.java`). While the protected mob fights an undead victim, armour drops by 20 + 2x (`:99-106`). Undead who are not in combat with the mob and try a malicious act get "You don't see <name>" (`:113-131`). If that undead is the mob's victim, the mob is made peaceful (`:124-128`).
- **Undeniable Faith** (`Prayer_UndeniableFaith.java`). The caster's god is set on the target through `affectCharStats` (`:100-105`). The caster must worship a god (`:128-134`). The target must not already worship one, and must not be an animal or a golem (`:136-148`). A per-target lockout of five MUD hours applies (`:150-162`). The penalty is `-(levelDiff*25)` (`:167-170`). The effect lasts `TICKSPERMUDMONTH` (`:195`), and the caster gets 25 experience (`:193`).

### 10. Boons, banes and the resurrection lock

- **Transfer Bane** (`Prayer_TransferBane.java`). `getBanes` scans the caster's own effects (`:78`) for prayers that are removable and whose `castingQuality` against the target is malicious (`:81-83`). A random bane is chosen (`:116`). The original curser casts it on the target if they can fight the target, and the caster otherwise (`:131-137`). The new effect takes the old invoker and expiry (`:143-144`), and the caster's copy is removed (`:146`). Message: "You lack any banes to transfer." (`:113`).
- **Transfer Boon** (`Prayer_TransferBoon.java`). The mirror image, for beneficial prayers (`:75-88`). It moves the chosen blessing from the caster to the target (`:130-142`).
- **Share Boon** (`Prayer_ShareBoon.java`). Chooses a removable, non-savable blessing with a trigger, a display text and a known invoker (`:75-96`). It applies that blessing to each beneficial target in the group who does not already have it (`:127-146`). It needs at least two targets (`:103-107`).
- **True Resurrection** (`Prayer_TrueResurrection.java`). The corpse is found by name, first in the area and then in the whole map (`:125-136`). A cooldown map keyed by corpse name blocks a second cast within one MUD day (`:146-166`). The record is written only when the corpse is consumed (`:172-173`). Message: "That spirit can not handle another True Resurrection right now." (`:164`).

## Content worth mining

### Catalogue of the 46 prayers

Domain, flags, quality and target are read from each class's `classificationCode`, `flags`, `abstractQuality` and `canTargetCode`. Line references point at the effect code.

| Prayer | Domain | Flags | Quality | Target | What it does (lines) |
|---|---|---|---|---|---|
| Share Boon | Blessing | Holy | Indifferent | Group in room | Copies one removable blessing to group members; needs 2+ (`Prayer_ShareBoon.java:103-146`) |
| Transfer Boon | Blessing | Holy | Beneficial-other | Mob | Moves a blessing from caster to target (`Prayer_TransferBoon.java:130-142`) |
| Transfer Bane | Cursing | Unholy | Malicious | Mob | Moves a malicious prayer from caster to target (`Prayer_TransferBane.java:104-147`) |
| Sense Law | Communing | Chaos, Divining | OK-self | Mob | CAN_SEE_LAW ambiance while invoker set (`Prayer_SenseLaw.java:96-103`) |
| Sense Magic | Communing | Neutral, Divining | OK-self | Mob | CAN_SEE_BONUS sense bit (`Prayer_SenseMagic.java:96-103`) |
| Speak with Dead | Communing | Holy | Indifferent | Corpse | Reports a corpse's death details (`Prayer_SpeakWithDead.java:129-135`) |
| Sober | Neutralization | Neutral | OK-other | Item or mob | Removes intoxicating effects whose invoker is at or below caster level + 20 + 2 x XLEVEL, or with no invoker (`Prayer_Sober.java:82-99`, `134-135`) |
| Wings | Creation | Neutral | Beneficial-other | Self or mob | Flying disposition (`Prayer_Wings.java:104-109`) |
| Sense Resistances | Communing | Holy, Divining | Indifferent | Mob | Lists saves above 0 and resisters (`Prayer_SenseResistances.java:102-119`) |
| Sense Professions | Communing | Holy, Divining | Indifferent | Other mob | Lists common skills with proficiency (`Prayer_SenseProfessions.java:108-119`) |
| Sense Prayers | Communing | Holy, Divining | Indifferent | Other mob | Lists prayers (`Prayer_SensePrayers.java:51-61`) |
| Sense Skills | Communing | Holy, Divining | Indifferent | Other mob | Lists skills (`Prayer_SenseSkills.java:51-61`) |
| Sense Songs | Communing | Holy, Divining | Indifferent | Other mob | Lists songs (`Prayer_SenseSongs.java:51-61`) |
| Sense Spells | Communing | Holy, Divining | Indifferent | Other mob | Lists spells (`Prayer_SenseSpells.java:51-61`) |
| Thunderbolt | Creation | Neutral | Malicious | Mob | Electric damage 1d(L)+L (`Prayer_Thunderbolt.java:96-97`) |
| Soul Peering | Communing | Holy | Indifferent | Other mob | Prints score to caster (`Prayer_SoulPeering.java:89-91`) |
| Sense Undead | Communing | Holy, Divining | OK-self | Self | Undead warning on room change (`Prayer_SenseUndead.java:113-121`) |
| Silence | Corruption | Unholy | Malicious | Mob | CAN_NOT_SPEAK; duration rule issue (`Prayer_Silence.java:97`, `151`) |
| Unholy Portent | Cursing | Unholy | Malicious | Mob | Extra 1d(3+L/10) on the target's attacks vs evil (`Prayer_UnholyPortent.java:124-126`) |
| Sun Curse | Cursing | Unholy | Malicious | Mob | Flesh-to-stone in sunlight (`Prayer_SunCurse.java:111-120`) |
| Undeath Guard | Healing | Unholy | Beneficial-other | Mob | Harm-family prayer on low HP (`Prayer_UndeathGuard.java:117-146`) |
| Undeniable Faith | Evangelism | Neutral, Charming, Mind-altering | OK-other | Mob | Converts worship (`Prayer_UndeniableFaith.java:123-196`) |
| Tithe | Evangelism | Unholy | Malicious | Mob | Taxes coin and shop sales (`Prayer_Tithe.java:113-147`) |
| Stoning | Evangelism | Unholy | OK-other | Mob | Warrant-based stoning and citizen crowd (`Prayer_Stoning.java:161-214`) |
| Tongues | Cursing | Unholy | Malicious | Mob | Random language speech (`Prayer_Tongues.java:104-165`) |
| Taint of Chaos | Cursing | Chaos | Malicious | Mob | Halves positive chaos gains (`Prayer_TaintOfChaos.java:104-121`) |
| Taint of Evil | Cursing | Unholy | Malicious | Mob | Halves positive alignment gains (`Prayer_TaintOfEvil.java:104-121`) |
| Stasis | Neutralization | Neutral | OK-other | Mob | +100 saves, cannot move, damage zeroed (`Prayer_Stasis.java:90-139`) |
| Wave | Creation | Holy, Moving | Malicious | Exit and enemies | Pushes enemies out, shared damage (`Prayer_Wave.java:150-156`) |
| Tremor | Creation | Neutral | Malicious | Enemies in range | Sits down legged targets (`Prayer_Tremor.java:195-201`) |
| Vampirism | Cursing | Unholy | Malicious | Mob | Hunger, thirst, sun, no swimming (`Prayer_Vampirism.java:105-285`) |
| Invisibility to Undead | Deathlore | Unholy | OK-self | Mob | Armour bonus vs undead victim; blocks undead malice (`Prayer_UndeadInvisibility.java:99-131`) |
| Weather | Creation | Neutral | Indifferent | Area | Weather transition (`Prayer_Weather.java:87-222`) |
| Summon Elemental | Creation | Neutral | Beneficial-self | Summon | One follower elemental (`Prayer_SummonElemental.java:149-177`) |
| Water to Wine | Communing | Neutral, Intoxicating | Indifferent | Freshwater item | Wine via Poison_Alcohol (`Prayer_WaterToWine.java:179-288`) |
| Store Prayer | Blessing | Neutral | Indifferent | Held item | Stores a charged prayer (`Prayer_StorePrayer.java:369-481`) |
| Snake Staff | Holy Protection | Neutral | OK-self | Wielded weapon | Snake pack ally (`Prayer_SnakeStaff.java:194-251`) |
| Unholy Armament | Holy Protection | Unholy | OK-self | Self | Unholy gear set (`Prayer_UnholyArmament.java:103-298`) |
| Word of Law | Blessing | Law | Indifferent | Room mobs | Lawful buff, strips chaos (`Prayer_WordOfLaw.java:218-296`) |
| Word of Chaos | Cursing | Chaos | Indifferent | Room mobs | Chaotic buff, strips law (`Prayer_WordOfChaos.java:194-255`) |
| Unholy Word | Cursing | Unholy | Indifferent | Room mobs | Evil buff, good debuff; strips blessings from target and one of the caster's (`Prayer_UnholyWord.java:128-188`) |
| Sense Traps | Communing | Neutral, Divining | OK-self | Mob | Trap sensing (`Prayer_SenseTraps.java:215-258`) |
| Sense Parish | Communing | Neutral, Divining | Indifferent | Named deity | Counts followers (`Prayer_SenseParish.java:89-187`) |
| Sense Life | Communing | Neutral, Divining | OK-self | Mob | Life sensing (`Prayer_SenseLife.java:161-208`) |
| Sermon | Evangelism | Charming, Neutral, Mind-altering | OK-other | Room | Charms listeners to follow (`Prayer_Sermon.java:170-224`) |
| True Resurrection | Deathlore | Holy | Indifferent | Corpse by name | Raises a corpse; cooldown (`Prayer_TrueResurrection.java:113-174`) |

Quality values come from each class's `abstractQuality`. Abbreviations: "Beneficial-self" is `QUALITY_BENEFICIAL_SELF`, and "OK-other" is `QUALITY_OK_OTHERS`.

### Weather transition table

From `Prayer_Weather.java:99-221`. Each cell is the successor for roll 1, roll 2 or roll 3 (equal chance).

| Current weather | Roll 1 | Roll 2 | Roll 3 |
|---|---|---|---|
| Clear | Windy | Rain | Cloudy |
| Fog or Cloudy | Windy | Rain | Clear |
| Blizzard | Blizzard | Blizzard | Snow |
| Snow | Blizzard | Sleet | Cloudy |
| Sleet | Sleet | Sleet | Cloudy |
| Hail | Hail | Sleet | Cloudy |
| Rain | Thunderstorm | Thunderstorm | Cloudy |
| Thunderstorm | Thunderstorm | Thunderstorm | Rain |
| Windy | Windy | Thunderstorm | Clear |
| Winter cold | Windy | Snow | Clear |
| Drought | Duststorm | Heat wave | Clear |
| Duststorm | Duststorm | Cloudy | Clear |
| Heat wave | Duststorm | Rain | Clear |

Any other weather value falls to `default` and does not change.

### Duration and difficulty constants

| Constant | Value in this bucket | Where |
|---|---|---|
| Beneficial standard | `casterLevel*4+40` ticks | `Abilities/StdAbility.java:2325` |
| Malicious standard | `round(adjustedLevel*1.1)+25` ticks | `Abilities/StdAbility.java:2178` |
| Tithe share | 10% | `Prayer_Tithe.java:120` |
| Tremor base | 2 ticks | `Prayer_Tremor.java:197` |
| Stasis base | 10 ticks | `Prayer_Stasis.java:163` |
| Undeniable Faith lockout | 5 MUD hours | `Prayer_UndeniableFaith.java:154` |
| True Resurrection cooldown | one MUD day | `Prayer_TrueResurrection.java:150-151` |
| Undeniable Faith duration | `TICKSPERMUDMONTH` | `Prayer_UndeniableFaith.java:195` |

## Notable design ideas

- **A prayer can be an item's spell.** `Prayer_StorePrayer` keeps a prayer's ID and charges as text on an item (`Prayer_StorePrayer.java:109-143`), and casts it when the item's name appears in speech (`:176-225`). The speech hook is a trailer message of type `TYP_WAND_USE` (`:241-265`). It is a clean way to attach a spell to an object without a new item type.
- **Alignment as a stat modifier, with a grace for the caster.** The Word family adds fixed armour and attack deltas, scaled by XLEVEL, and skips the caster (`Prayer_WordOfLaw.java:186-199`). It is one short rule that makes a room of alignments tilt in a visible way.
- **One integer with three meanings.** Sign and magnitude choose literal ticks, a standard, or a percentage (`Abilities/StdAbility.java:2174-2182`). It is compact, but see the Silence problem below.
- **Effects that move.** `Prayer_TransferBane` and `Prayer_TransferBoon` detach a live effect from one mob, reattach it to another, and keep the invoker and remaining time (`Prayer_TransferBane.java:140-146`; `Prayer_TransferBoon.java:135-141`).
- **A dormant effect that watches the world.** `Prayer_SunCurse` checks the climate each tick, casts a different spell when the target is in sunlight, and removes itself (`Prayer_SunCurse.java:111-120`).
- **A passive response that does not cost skill.** `Prayer_UndeathGuard` calls the cleric's harm prayer and restores the usage counter (`:139-146`), so a passive trigger does not train the skill.
- **Speech rewritten by a language proxy.** `Prayer_Tongues` routes the target's own messages through a random `Language` object, so the curse is a language swap rather than a text hack (`:155-165`).
- **A weather state machine.** The transition table (above) is a finite state machine with three equally likely successors per state (`Prayer_Weather.java:99-221`). It is small enough to copy unchanged.
- **Taxes as an effect.** `Prayer_Tithe` makes a shopkeeper pay part of its income to the caster (`:113-147`). It shows that an economic mechanic can ride on the effect system without new code paths.
- **Legal consequence as an effect.** `Prayer_Stoning` clears warrants by setting their crime to "pardoned" and offenses to zero (`:198-206`). The justice system is a data structure the prayer edits.

## Problems and cruft

- **Silence's duration curve (likely inverted).** `Prayer_Silence.java:135-137` clamps `levelDiff` at 0 and passes `-levelDiff` (`:151`). Through `Abilities/StdAbility.java:2174-2182`, a target at or below the caster's level gets 100% of the standard, a target one level above gets 1%, and a target 50 levels above gets 50%. The curve jumps and rises with the level gap. The intent was probably the reverse. This was not checked in play.
- **Sermon overwrites its success flag.** Inside the listener loop, `success=maliciousAffect(...)!=null` (`Prayer_Sermon.java:206`) replaces the same variable that is returned at `:224`. The return value depends on the last listener processed. The loop runs over a `Hashtable` (`:173`, `:196`), so that order is undefined.
- **Unholy Word buffs its own caster.** `Prayer_UnholyWord.java:90-111` has no `mob==invoker` check. Word of Law (`:186`) and Word of Chaos (`:99`) both have one. An evil caster buffs their own aura. This looks like an oversight.
- **Asymmetric stripping.** Word of Law strips each listener's chaos items (`Prayer_WordOfLaw.java:252-272`). Word of Chaos strips one item from the caster (`Prayer_WordOfChaos.java:225`). Unholy Word does the same as Word of Chaos (`Prayer_UnholyWord.java:159`). The intent is not documented.
- **Tongues' factor of 50.** `Prayer_Tongues.java:197` multiplies the standard by 50 and passes it as ticks. This is about 2,350 ticks for a level-20 caster before fudge. The reason is not stated.
- **Tithe null dereference.** `Prayer_Tithe.java:132-133` calls `SK.getShop()` without checking that `SK` is non-null. This is reached only if a buy message targets a non-shopkeeper. The risk is latent.
- **Sense Traps checks one exit.** The room scan in `Prayer_SenseTraps.java:123-132` breaks after the first exit it finds, so the room's other exits are never inspected. `messageTo` applies the same single-exit scan to each neighbour (`:195-210`).
- **Unreachable branch.** `Prayer_UnholyArmament.java:122-129` tests `pos<0`. Every entry in `checkOrder` is a positive wear bit (`Items/interfaces/Wearable.java:257-287`), so the "already completely armed" message in that branch cannot fire.
- **Undeniable Faith's global list grows.** `convertStack` is `static` (`Prayer_UndeniableFaith.java:88`). Entries are removed only when the same target is checked again (`:151-156`), so one-off targets stay forever.
- **True Resurrection's cooldown is per object.** `lastCasts` is an instance field (`Prayer_TrueResurrection.java:104`). The cooldown therefore lasts only as long as that ability object. Whether two casters share an object was not traced (unverified).
- **Cross-bucket note on the hit roll.** `MUDFight.rollToHit` replaces a negative attack with `-1/myArmor` rather than something derived from the attack (`Libraries/MUDFight.java:421-422`). The Word family's debuffs (`attack -(20+4x)`) can push attack below zero, so this branch is reachable from these prayers. Its effect was not traced.
- **Typos in player-facing text.** "fatih" (`Prayer_StorePrayer.java:208`), "statis" (`Prayer_Stasis.java:137`), "code of air" for an air elemental (`Prayer_SummonElemental.java:234`), "couldn`t" with backticks in several armament descriptions (`Prayer_UnholyArmament.java:138`, `:155`), and "You already know your own @x1!." (`Prayer_SenseProfessions.java:93`).
- **Dead code.** Empty `if(target instanceof MOB){}` branches (`Prayer_Tithe.java:158-160`; `Prayer_TaintOfChaos.java:130-132`; `Prayer_TaintOfEvil.java:130-132`). Stoning calls `super.tick` twice (`Prayer_Stoning.java:105`, `:108`).
- **Stoning's temporary room.** To create its citizens, Stoning builds a throwaway `StdRoom`, brings the mob to life there, then destroys the room (`Prayer_Stoning.java:149-155`). It is a workaround for needing a room to create a mob.
- **Multiplayer assumptions.** Several prayers assume other people. Tithe pays a second character (`Prayer_Tithe.java:121-126`). Sermon makes other mobs follow the caster (`Prayer_Sermon.java:209-212`). Snake Staff and Summon Elemental reset player stats on quit (`Prayer_SnakeStaff.java:159-170`; `Prayer_SummonElemental.java:110-122`). Unholy Armament keys its zapper to the caster's name (`Prayer_UnholyArmament.java:246`). These are reasonable in an MMO and should be simplified in a single-player game.
- **Race as life.** Sense Life treats a race as alive when it is fertile (`Prayer_SenseLife.java:121-123`; `Races/StdRace.java:327-330`). This works, but it is indirect.

## Takeaways for a Python SUD

- **Effect objects with hooks.** Model an effect as a small class: `ticks`, `source`, `target`, a dict of stat deltas, and optional `ok_message`, `on_event` and `on_expire` hooks. Use one class for every timed effect, as `StdAbility` does. Keep `ok_message` (veto or set a value) separate from `on_event` (react), as Stasis and Tongues show.
- **Explicit durations.** Avoid the sign-overloaded integer. Use two fields, `ticks` for a literal count and `percent_of_standard` for a scaled one. Keep the standards as functions: `beneficial = 4*level + 40`, `malicious = round(1.1*level) + 25`. Keep the level-gap fudge but apply it once, with the sign of the effect obvious.
- **Alignment as a stat rule.** A single function covers the Word family:

  ```python
  def alignment_word_delta(target_align: str, caster_bonus: int) -> dict:
      x = caster_bonus
      if target_align == "lawful":
          return {"armour": -15 - 6 * x, "attack": 20 + 4 * x}
      if target_align == "chaotic":
          return {"armour": 15 + 6 * x, "attack": -20 - 4 * x}
      return {}
  ```

  Lower armour is better (see `Libraries/MUDFight.java:348-425`). Skip the caster, as the source does.
- **Detection as pure functions.** Write each Sense prayer as a function over world state: `sense_life(room, adjacent_rooms) -> str`. Call it on room entry, and store `last_room` on the effect so it fires once per room, as `Prayer_SenseLife.java:161-174` does.
- **Prayers as items.** Store `{"spell": "heal", "charges": 3}` on an item. A spoken phrase such as "<item name> <words>" parses the spell and charges. Model the speech hook as a parser step, not a message hook.
- **Weather as a table.** Copy the transition table above as a `dict[str, list[str]]` and choose with `random.choice`. Keep the rain and thunderstorm bias if you want storms to persist.
- **Cooldowns as game time.** Store `last_cast[(caster_id, corpse_id)] = game_time` in one dict per character, with a single-player expiry. Do not use static lists (see `Prayer_UndeniableFaith.java:88`).
- **Drop or simplify.** Drop the group and quit handling, the zapper mask language and the name-keyed items; replace masks with predicates. Keep the Sense family, Word family, Stasis, Silence, Tremor, Store Prayer and the weather table. Keep Sermon's charm only if the SUD has companions.

## Coverage

- **Read in full (class bodies; package and import headers skipped):** all 46 files in the bucket. `Prayer_SenseSkills`, `Prayer_SenseSongs` and `Prayer_SenseSpells` were read from the class declaration, since their bodies are short and identical in shape.
- **Base and helper classes read:** `Prayer.java` in full. `Prop_ScrapExplode.java` for its header, `flags` and `executeMsg`.
- **Excerpts read from outside the bucket** (paths as in the Key files table):
  - `StdAbility.java`: lines 529-545, 698-740, 1964-2030 (preamble only), 2113-2125, 2140-2199, 2210, 2222, 2312-2349; 2253 located by grep. Line 56 (`canBeUninvoked` field) was located by grep.
  - `MUDFight.java`: lines 163-205, 348-373, 394-425.
  - `Factions.java`: lines 873-905.
  - `Dice.java`: lines 398-411.
  - `Prayer_Harm.java`: lines 70-135.
  - `DefaultCharState.java`: lines 185-198.
  - `StdFood.java`: line 159.
  - `StdRace.java`: lines 327-340.
  - `Sense.java`: lines 543-553.
  - `coffeemud.ini`: line 2352.
  - `CMProps.java`: lines 681 and 2720.
- **Existence only:** `Prayer_Curse.java` (`getSomething`, `endLowerBlessings`), `Prayer_Resurrect.java` (`canResurrectNormalMobs`), `Spell_FleshStone.java` (display text and `prevState` use at `:281`), and `Wearable.java` (wear constants at `:257-287`).
- **Not decoded:** the zapper keyword semantics in `com/planet_ink/coffee_mud/Libraries/MUDZapper.java` (masks are quoted as written).
- **Skipped:** none of the bucket's files. Within `StdAbility.invoke`, the body past the cost and compounding preamble was not traced.
