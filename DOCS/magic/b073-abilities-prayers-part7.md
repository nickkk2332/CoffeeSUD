# Prayers, part 7: Read Language to Sense Invisible

> Part of the CoffeeMud NEWOVERVIEW. Bucket: `b073` (Abilities/Prayers [part 7/8: Prayer_ReadLanguage .. Prayer_SenseInvisible]) · 46 files · 8550 lines of Java.

Citations are relative to the CoffeeMUD folder. In this chapter the Java root `com/planet_ink/coffee_mud/` is dropped from every path, so `Abilities/Prayers/Prayer_Rot.java:211` means `com/planet_ink/coffee_mud/Abilities/Prayers/Prayer_Rot.java:211`. Other roots are written out in full.

## Summary

This slice of the cleric prayer list holds 46 `Prayer` subclasses, all built on the shared base class `Abilities/Prayers/Prayer.java:37`. The base adds one gate, an alignment roll for holy and unholy prayers (`Prayer.java:372-420`), plus relic helpers. Most prayers are small: detection buffs and room scanners ("sense" prayers), cures (Restoration, Regrowth, Remove Poison, Remove Curse), and protective effects (Sanctuary, Rockskin, Reflect Prayer, Sanctum). A few carry real mechanics: a group ritual that converts unbelievers (Revival), a prayer that generates a quest to imbue an item (Sacred Imbuing Quest), a material-based food decay table (Rot), and a passive snapshot of ridden mounts (Resurrect Mount). The bucket shows how one base class with veto, effect and tick hooks can host dozens of effect types, and how religion and alignment become mechanical gates. Several prayers assume multiplayer, and several contain defects, listed under "Problems and cruft".

## Key files and types

| File or class | Role | Lines |
|---|---|---|
| `Abilities/Prayers/Prayer.java` (outside bucket, read in full) | Base class: trigger words, `ACODE_PRAYER`, alignment gate `prayerAlignmentCheck` (372-420), relic and infusion helpers (151-370) | 432 |
| `Abilities/StdAbility.java` (outside, partial) | `beneficialAffect` (2341-2348), duration rule `getBeneficialTickdownTime` (2312-2331), `properTargets` (2113-2125), `getXPCOSTAdjustment` (474-480) | (large) |
| `Libraries/Factions.java` (outside, partial) | `getAlignPurity` (873-905), the purity distance used by the gate | (large) |
| `Abilities/Prayers/Prayer_SacredImbuingQuest.java` | Generates a quest; winning it imbues a worn item with another prayer | 585 |
| `Abilities/Prayers/Prayer_Restoration.java` | Full heal plus sixteen removal blocks; `supportsMending` gate | 330 |
| `Abilities/Prayers/Prayer_Rot.java` | Material-based decay timer for food and drink | 323 |
| `Abilities/Prayers/Prayer_ReflectPrayer.java` | Held shield reflects one hostile prayer, then the shield wears down | 307 |
| `Abilities/Prayers/Prayer_Resurrect.java` | Corpse resurrection; sysop path over stored HEAVEN records | 304 |
| `Abilities/Prayers/Prayer_Revival.java` | Group conversion ritual with a tick-driven sermon | 257 |
| `Abilities/Prayers/Prayer_SenseBombs.java`, `Prayer_SenseDisease.java`, `Prayer_SenseInjury.java` | Room scanners that report matches by direction | 254, 254, 237 |
| `Abilities/Prayers/Prayer_ReligiousDoubt.java` | Doubt debuff; clears worship while active | 225 |
| `Abilities/Prayers/Prayer_Refuge.java` | Item-anchored return point; "REFUGE" speech moves followers | 211 |
| `Abilities/Prayers/Prayer_Sanctum.java`, `Prayer_SanctifyRoom.java` | Room wards with privilege and clan checks | 208, 202 |
| `Abilities/Prayers/Prayer_SeekersPrayer.java` | Reveals a random selection of running quests and their NPCs | 189 |
| `Abilities/Prayers/Prayer_RechargeRelic.java` | Adds 5 charges to a wand | 160 |
| `Abilities/Prayers/Prayer_ResurrectMount.java` | Passive effect that keeps copies of recently ridden mounts | 160 |
| `Abilities/Prayers/Prayer_RemoveDeathMark.java` | Strips assassination marks, including other players' marks | 170 |
| `Abilities/Prayers/Prayer_Retribution.java` | Subclass of `Prayer_BladeBarrier` (outside bucket) | 82 |

## How it works

### 1. The shared gate and base hooks

- Every prayer is classified `Ability.ACODE_PRAYER` (`Prayer.java:86-89`). Trigger words are `PRAY` and `PR` (`Prayer.java:77`). Targets default to mobs (`Prayer.java:66-69`). Subclasses add a domain, for example `ACODE_PRAYER|DOMAIN_HEALING` (`Prayer_Restoration.java:58-61`).
- `Prayer.invoke` runs `StdAbility.invoke`, then `prayerAlignmentCheck` (`Prayer.java:423-430`).
- `prayerAlignmentCheck` (`Prayer.java:372-420`) is skipped for automatic casts, monster casters, abilities the caster does not own (`isMine`), prayers whose `appropriateToMyFactions` is true, and prayers with `flags()==0` (`Prayer.java:374-379`). Otherwise:
  - FLAG_HOLY alone sets `hq=1000` and tests the GOOD purity. FLAG_UNHOLY alone sets `hq=0` and tests EVIL. Anything else tests NEUTRAL minus 10 (`Prayer.java:381-400`).
  - The cast passes when `rollPercentage() > basis` (`Prayer.java:403-404`). A caster at the pure end of the range passes almost always.
  - Failure messages depend on the case, e.g. "The goodness of @x1 disrupts your prayer." (`Prayer.java:410`) or "The non-evil nature of @x1 disrupts your thought." (`Prayer.java:416`).
- `getAlignPurity` (`Libraries/Factions.java:873-905`) returns the distance of the caster's faction percent from the pure end of the alignment range: `abs(pct - top)` for GOOD, `abs(bottom - pct)` for EVIL. Lower is purer.
- Gate exemptions: `appropriateToMyFactions` returns true in Read Language (`Prayer_ReadLanguage.java:84-90`), Read Prayer (`Prayer_ReadPrayer.java:91-95`) and Recharge Relic (`Prayer_RechargeRelic.java:85-89`). `flags()` returns 0 in Reflect Prayer (`Prayer_ReflectPrayer.java:103-106`), Religious Doubt (`Prayer_ReligiousDoubt.java:71-74`) and Resurrect Mount (`Prayer_ResurrectMount.java:64-68`).

### 2. Duration, cost and XP rules

- `beneficialAffect(mob, target, asLevel, adj)` (`StdAbility.java:2341-2348`) passes `adj` to `getBeneficialTickdownTime` (`StdAbility.java:2312-2331`), which reads it three ways:
  - `adj > 0` is an absolute tick count. Revival passes 10 (`Prayer_Revival.java:249`), Reflect Prayer 15 (`Prayer_ReflectPrayer.java:292`), and Sacred Imbuing Quest `CMProps.getTicksPerHour()*2` (`Prayer_SacredImbuingQuest.java:561`).
  - `adj == 0` is the standard duration, `casterLevel*4 + 40` ticks (`StdAbility.java:2325`). When a non-owner casts on another player, the caster level is capped at the target's level plus `EXPRATE` (`StdAbility.java:2317-2324`). Most prayers here pass 0.
  - `adj < 0` is a percentage of the standard duration (`StdAbility.java:2330`).
  - Refuge passes `Ability.TICKS_ALMOST_FOREVER` (`Prayer_Refuge.java:199`), which is `Integer.MAX_VALUE/2` (`Abilities/interfaces/Ability.java:48`). The effect is therefore effectively permanent, and it ends early on sale (`Prayer_Refuge.java:92-97`) or after one use (`:163`).
- The default tick is 4000 ms (`core/CMProps.java:681`).
- Mana: `overrideMana()` returns `Ability.COST_ALL` in Restoration (`Prayer_Restoration.java:69-73`), Regrowth (`Prayer_Regrowth.java:73-76`) and Sacred Imbuing Quest (`Prayer_SacredImbuingQuest.java:72-76`). Refuge returns `COST_PCT + 25` (`Prayer_Refuge.java:75-79`), and Recharge Relic returns the literal 100 (`Prayer_RechargeRelic.java:61-63`). Other prayers keep the class default, which this bucket does not override.
- XP: `getXPCOSTAdjustment` reduces an XP loss by 5% per point of XP-cost skill level (`StdAbility.java:474-480`).

### 3. Detection family

Sense Evil, Good, Chaos, Invisible and Hidden are buffs. Each adds a sense through `affectPhyStats`:

| Prayer | Effect | Flags |
|---|---|---|
| Sense Evil | `CAN_SEE_EVIL` sense mask (`Prayer_SenseEvil.java:102`) | HOLY (`:92`) |
| Sense Good | `CAN_SEE_GOOD` sense mask (`Prayer_SenseGood.java:102`) | UNHOLY (`:92`) |
| Sense Chaos | `addAmbiance(CAN_SEE_CHAOS)` (`Prayer_SenseChaos.java:102`) | LAW (`:92`) |
| Sense Invisible | `CAN_SEE_INVISIBLE` sense mask (`Prayer_SenseInvisible.java:102`) | NEUTRAL, DIVINING |
| Sense Hidden | `CAN_SEE_HIDDEN` (`Prayer_SenseHidden.java:101`) and `STAT_SAVE_OVERLOOKING += level + 2*XLEVEL + 100` (`:108`) | NEUTRAL, DIVINING |

- Each `affectPhyStats` returns early when `invoker==null` (e.g. `Prayer_SenseEvil.java:99-100`), so the sense applies only while the buff knows its caster.
- `castingQuality` for Hidden and Invisible is INDIFFERENT unless another hidden or invisible MOB is in the room (`Prayer_SenseHidden.java:126-151`, `Prayer_SenseInvisible.java:120-145`).

Sense Bombs, Sense Disease and Sense Injury share one pattern. On a MOB tick, after the caster has moved to a new room, the effect calls `messageTo` (e.g. `Prayer_SenseDisease.java:204-218`). `messageTo` lists matches in the room and in adjacent rooms:

- Directions are scanned in reverse order over `NUM_DIRECTIONS()` and only rooms with an exit count (`Prayer_SenseDisease.java:161-171`, `Prayer_SenseBombs.java:187-199`). Output joins names with commas and "and": "You sense disease coming from @x1, and @x2." (`Prayer_SenseDisease.java:201`), "You sense a bomb to @x1, and @x2." (`Prayer_SenseBombs.java:204`), "You sense injuries @x1, and @x2." (`Prayer_SenseInjury.java:184`).
- Sense Bombs: `trapCheck` calls `CMLib.utensils().fetchMyTrap(P)` and `isABomb()` (`Prayer_SenseBombs.java:103-112`). `trapHere` walks room items, inhabitants, container contents, mob inventories and shop stock (`Prayer_SenseBombs.java:114-176`), with messages such as "has a bomb in stock." (`:170`).
- Sense Disease: `diseased()` returns MOBs and loose items holding an effect of classification `ACODE_DISEASE` (`Prayer_SenseDisease.java:113-155`).
- Sense Injury: `getInjured` returns MOBs below max HP or carrying a "BrokenLimbs", "Bleeding", "Amputation" or "Injuries" effect (`Prayer_SenseInjury.java:114-138`). The last name is a defect; see below.

### 4. Identity and information prayers

- Sense Faithful requires a deity (`Prayer_SenseFaithful.java:85-88`). It then runs the internal "Who" command with a filter for the caster's deity name (`:102-118`) and prints the result to the caster's session (`:107-109`).
- Sense Devotion refuses self-targeting (`Prayer_SenseDevotion.java:81-84`) and sets a proficiency penalty of `-(levelDiff*5)`, where levelDiff is the target's level minus (caster level + 2*XLEVEL), floored at 0 (`:87-94`). It reads `msg.value()==0` after sending the message (`:102`), then reports one of four cases (`:104-119`): no worship and no deity; worship with no deity; a deity name that differs from the worship ID ("seem(s) to worship"); otherwise "worship(s) @x1".
- Sense Alignment reports `getAlignmentName` with a "chaotic" or "lawful" prefix (`Prayer_SenseAlignment.java:98-106`). Defect at line 103.
- Sense Allergies reads the target's "Allergies" effect text (`Prayer_SenseAllergies.java:94`), uppercases it, and matches each token against `RawMaterial.CODES.NAMES()` and `CMClass.getRace` (`:101-110`). Defect at line 113.
- Sense Chants (`Prayer_SenseChants.java:52-60`) overrides `senseWhat()` to `ACODE_CHANT` and the noun to "chants". It inherits `Prayer_SenseProfessions.java:86-126`, which lists each matching ability as `name (NN%)` (`:114`).

### 5. Cures and restorations

- **Restoration** (`Prayer_Restoration.java:124-329`) heals to full through `CMLib.combat().postHealing` (`:142-145`). It then runs sixteen removal blocks in order: Bleeding (`:152`), Amputation (`:165`), Scarring (`:177`), BrokenLimbs (`:185`), Injury (`:198`), Fighter_AtemiStrike (`:211`), three Undead drain or chill effects (`:221-241`), smell, voice, poison, freedom, disease, blindness and deafness (`:251-311`). Each removal calls `recoverCharStats`, `recoverPhyStats` and `recoverMaxState`. `supportsMending` (`:76-108`) gates the cast on the same list.
- **Regrowth** (`Prayer_Regrowth.java:122-172`) needs an Amputation effect (`:105`). It regrows one limb, chosen by a fixed priority list `EYE, LEG, FOOT, ARM, HAND, EAR, NOSE, TAIL, WING, ANTENNA` (`:87-96`), through `restoreLimb` (`:160`).
- **Refresh** (`Prayer_Refresh.java:101-132`) sets mana, HP and movement to full, fatigue to 0, and hunger and thirst to full (`:118-124`). Its minimum cast wait is half a tick (`:70-73`).
- **Remove Poison** (`Prayer_RemovePoison.java:105-159`) removes every `ACODE_POISON` effect (`:78-89`, `:134-135`). A poisoned drink becomes fresh water (`:136-140`). The message "You feel much better!" follows when no poison remains (`:145`).
- **Restore Smell** and **Restore Voice** (`Prayer_RestoreSmell.java:82-103`, `Prayer_RestoreVoice.java:82-103`) apply each effect's `affectPhyStats` to a factory MOB and remove the effect if that MOB cannot smell or speak. An effect is removable only when its invoker's level is at most the caster's level + 1 + 2*XLEVEL for smell (`Prayer_RestoreSmell.java:97`), or + 10 + 2*XLEVEL for voice (`Prayer_RestoreVoice.java:97`).
- **Remove Paralysis** (`Prayer_RemoveParalysis.java:102`) unInvokes effects flagged PARALYZING or UNHOLY.
- **Rock Flesh** (`Prayer_RockFlesh.java:91-124`) dispels the first uninvokable effect whose ID is `Spell_FleshStone` or `Prayer_FleshRock`.
- **Remove Curse** (`Prayer_RemoveCurse.java:103-165`) loops over `Prayer_Bless.getSomething(target,true)` (`:123`, `:154`) and calls `Prayer_Bless.endLowerCurses` (`Prayer_Bless.java:173`). On the first pass an item becomes removable and droppable (`:147-148`). If the same item comes back, the caster forces a drop with "<S-NAME> release(s) <T-NAME>." (`:129-139`, message at `:135`). A final `endLowerCurses` covers the target (`:156`).
- **Remove Death Mark** (`Prayer_RemoveDeathMark.java:114-169`) removes `Thief_Mark`, `Fighter_UnwaveringMark` and `Thief_ContractHit` (`:81-85`) from the target. It also removes them from every player whose mark text starts with the target's name and a slash (`:132-144`). That loop covers all players, so it matters only with several humans.
- **Resurrect** (`Prayer_Resurrect.java:100-275`) resurrects player corpses through `CMLib.utensils().resurrect(...)` with the XP-cost level (`:218-219`). Non-player bodies go through an Age check. An Age effect whose text exceeds `Short.MAX_VALUE` (`:173`) must match a HEAVEN record of a nearby non-monster player, keyed as `<name>/HEAVEN/<age>` (`:181`). Otherwise the caster gets "You can't seem to focus on @x1's spirit. Perhaps if loved ones were here?" (`:191`). A sysop with no target gets a listing of stored HEAVEN records and can resurrect one by name (`:103-158`). `parseHeavenlyData` rebuilds a saved object from `classID/ability/data` text (`:277-303`).
- **Resurrect Mount** (`Prayer_ResurrectMount.java`) extends Resurrect and sets `canResurrectNormalMobs` true (`:88-92`), so it can also revive non-player corpses.

### 6. Protective effects

- **Sanctuary** (`Prayer_Sanctuary.java:105-123`) multiplies incoming `TYP_DAMAGE` by 0.75 (`:117`). If rounding leaves the value unchanged, it removes one more point (`:118-119`). Casting it removes any Prayer_Fortress (`:145-147`).
- **Rockskin** (`Prayer_Rockskin.java:87-163`) lowers armor by `15 + 2*XLEVEL` (`:91`). It absorbs `3 + level/10` hits (`:155`). Each damaging Weapon hit is nullified (`:118-126`), and the last one ends the spell (`:127-128`).
- **Sanctimonious** (`Prayer_Sanctimonious.java:89-108`) applies only in combat (`:96`). The opponent must be good, or neutral with the caster's own alignment faction above zero (`:100`), and the caster's alignment faction must exceed the opponent's (`:101`). Then armor drops by `10 + 2*XLEVEL`, attack rises by `5 + XLEVEL/2`, and damage rises by `2 + XLEVEL/3` (`:103-105`). Like Rockskin, it lowers armor for a bonus; the scale of `armor()` was not verified.
- **Reflect Prayer** (`Prayer_ReflectPrayer.java:158-223`) works while a non-enchanted Shield is held (`:225-235`). It intercepts the next hostile prayer aimed at the owner (`:181-188`). Each reflection costs `max(1, prayerLevel - 2*XLEVEL)` shield uses (`:193-197`), and at zero the shield is destroyed (`:202-215`). The intercepted prayer is re-invoked on the attacker with `auto=true` (`:217`), and the effect ends (`:218`). It can be cast every 15 ticks (`:74-77`) and lasts 15 ticks (`:292`).
- **Sanctum** (`Prayer_Sanctum.java:93-157`) is a room effect (`:74`). Non-privileged entrants are refused with "You feel your muscles unwilling to cooperate." (`:99-111`). Hostile messages in the room are cancelled, the attacker is told to stop, and both sides are made peaceful (`:112-153`). The say-line comes at random from a semicolon-separated list in the effect text, with defaults "No fighting!" (`:127`) and "You feel too peaceful here." (`:140`). The owner's name, or the owning clan, is stored as the effect text (`:182-190`).
- **Sanctify Room** (`Prayer_SanctifyRoom.java:97-142`) blocks GET, PULL, PUSH and CAST_SPELL messages against items the actor does not own (`:130-140`). The exception is a room whose owning clan is at war with a rival of the actor (`:107-113`). Casting it in combat is INDIFFERENT (`:149-150`).
- **Retribution** (`Prayer_Retribution.java:72-81`) inherits the aura in `Prayer_BladeBarrier.java:117-150`. When an adjacent attacker hits the protected MOB with hands or movement, and a percentage roll exceeds `60 + DEX` (`Prayer_BladeBarrier.java:124`), the attacker takes one die of `round(level/5)` sides plus 1 (`:131`). That damage is applied once per tick (`:139`). The base also gives armor `-1 - level/10` (`:150`). Retribution changes only the damage type and text, to electrical striking (`Prayer_Retribution.java:78-81`).
- **Religious Doubt** (`Prayer_ReligiousDoubt.java:95-225`) sets SAVE_DOUBT to -100 while active and +100 after it flips (`:100-103`). It also clears the worship ID (`:104`). After half the duration the flag flips (`:115`). While active it blocks Skill_Convert from others (`:137-144`) and deity spells cast at the victim (`:145-154`). It rolls at `-(levelDiff*15)` (`:196-200`), and casting it on someone else costs -25 inclination faction (`:209-213`).
- **Refuge** (`Prayer_Refuge.java:87-210`) is an item-anchored return point. The caster's room ID is stored as the effect text (`:199-201`). When the owner, or the room, speaks a line containing "REFUGE" to the item, a trailer runnable moves each MOB in `properTargets` to the stored room, with puff-of-smoke messages (`:123-127`, `:137-162`, moves at `:154-156`). The effect ends after one use (`:163`). Rooms in instance-child areas are refused (`:177-184`).

### 7. Seekers Prayer

`Prayer_SeekersPrayer.java:82-188`. The number of quests revealed is `XLEVEL + 2`, plus `adjustedLevel / qualifyingLevel` when the qualifying level exceeds 1 (`:109-111`). It collects running quests whose quest mob is in the game and accessible to the caster (`:112-126`), skipping the quest named "holidays" (`:117`). It trims to the count at random (`:131-132`) and opens with one of ten lead-ins (`:134-166`). Each entry names the quest mob and its area (`:177`). With no quests it says "You receive no visions of seekers." (`:128`).

### 8. Group and quest mechanics

**Revival** (`Prayer_Revival.java:90-219` tick, `:222-256` invoke).

- Each cleric in the room who has the Revival effect and the caster's worship ID adds `level + 2*XLEVEL` to a pool (`:104-117`).
- Each tick, each non-worshipper has a 10% chance of being picked as a candidate (`:119-120`).
- With at least three clerics and a candidate present (`:179`), the pool is averaged and increased by 5 for each cleric beyond three (`:181-182`).
- If that value reaches the candidate's level plus bonus (`:184`), the candidate is converted. A candidate who worships something gets `Prayer_Faithless` (`:189-193`). One who does not gets `Prayer_UndeniableFaith` (`:194-199`), and then the other clerics gain 25 XP (`:203-208`).
- Each tick there is a 50% chance of a sermon line when a deity exists (`:125`), chosen from 13 variants (`:127-177`), such as "Do you BELIEVE?!? I BELIEVE!!!" (`:163`).
- Casting needs a real deity (`:227-228`) and no running Revival (`:233`). It lasts 10 ticks (`:249`).

**Sacred Imbuing Quest** (`Prayer_SacredImbuingQuest.java:398-576` invoke, `:132-209` tick).

- Invoke requirements: a player target who is not in `lastUsed` (a one-day, 1000-entry static set, `:123`, `:425`), a worn item (`:430`), the same deity as the target (`:437-447`), and an item that is generic and not already enchanted (`:506-515`).
- The imbued prayer must be one of the caster's prayers that passes `isOkPrayer` (`:364-374`, excluding Stoneskin, Mirror Image, summoning, room prayers, malicious prayers and prayers that cannot affect mobs) and `mayBeEnchanted` (`:501`).
- The caster must have at least `1000 + 100*lowestQualifyingLevel` XP (`:517-523`).
- On success, `deviseAndStartQuest` (`:279-362`) loads `randareas/example.xml` (`:289`). The quest criteria are "-NAME +<target> -NPC" (`:297`), with AGGRESSION YES (`:542`), and duration and expiry of one hour in ticks (`:298-299`). It then starts the quest (`:320-346`).
- The quest state is stored as JSON on the effect (`:556-563`). The item must stay worn for the tick to continue (`:155`).
- When the target wins, the caster pays `1000 + 100*lowestQualifyingLevel` XP (`:187-192`). `completeImbuing` then raises the item's level by `lowestQualifyingLevel/2` (`:215`), sets IS_BONUS (`:214`), and adds a spell-cast property (`:217-249`): `Prop_WearSpellCast` for armor and other worn items, `Prop_FightSpellCast` with "25%;MAXTICKS=12;" for weapons (`:226-228`), and `Prop_UseSpellCast2` for food and drink (`:234-236`).
- Failure costs 50 XP (`:568-571`).

**Rot** (`Prayer_Rot.java:89-322`).

- `setRot` (`:127-239`) starts a decay timer the first time an owned item is checked (`:129-131`). The timer is in MUD days, `TICKSPERMUDDAY` times the tick length (`:138-141`):
  - flesh: 1 day (`:136-143`); vegetation: 5 days (`:145-151`)
  - drink blood: 1 day; drink milk: 5 days (`:155-173`); material blood and milk repeat the same values (`:176-188`)
  - herbs, wax, coffee beans, seaweed, sugar, cocoa, mushrooms, fungus, vine, flowers, nuts, cracker, pipeweed, garlic, soap and ash: never decay (`:189-205`)
- Decay (`:211-237`) is skipped for bonus items (`:213`). It adds `Poison_Rotten` (`:228-230`), strips poison effects from poison-material items (`:218-227`), zeroes food nourishment (`:232-233`) or drink quenching (`:234-236`), and clears the timer (`:237`).
- `executeMsg` (`:89-125`) clears the timer and `Poison_Rotten` when a shopkeeper takes the item or a room resets, including the contents of containers (`:93-123`).
- `okMessage` runs this check at most once every 60 s (`:241-261`, throttle at `:244-246`).
- `invoke` (`:263-322`) works only on Food and Drink (`:270-275`). A liquid becomes salt water (`:292-296`), and pill or potion spell lists become `Prayer_DrunkenStupor` (`:300-311`). The item then "appears rotted!" (`:313`).

**Recharge Relic** (`Prayer_RechargeRelic.java:93-158`) works only on wands (`:99-102`). The wand's enchant type must be -1 or `ACODE_PRAYER` (`:105-109`). Each cast adds `RECHARGE_AMT = 5` charges (`:46`, `:140`), capped at the maximum, with "begins to hum. It clearly cannot hold more magic." when capped (`:133-148`). A wand already at maximum is destroyed: "glow(s) divinely then disintegrates!" (`:125-128`).

**Resurrect Mount** (`Prayer_ResurrectMount.java:122-158` tick). This is a permanent passive. It is auto-invoked (`:76-80`) and cannot be removed (`:82-86`). When the affected MOB begins riding another MOB, the mount is copied and stored under its hash (`:130-151`). The list keeps the last five entries (`:152-153`).

**Read Language** (`Prayer_ReadLanguage.java:95-172`) strips a leading "all", "new" or number from the command list (`:101-108`). The target must be readable (`:112-116`). The caster temporarily gets the target's natural-language effect at 100% proficiency (`:127-155`), then `postRead` runs (`:156`). The old proficiency is restored in a `finally` block (`:158-165`).

**Read Prayer** (`Prayer_ReadPrayer.java:99-129`) adds a copy of itself to the caster for the duration of `postRead` (`:112-122`), so a scroll can be read through the prayer. `getReadMagicType` returns `ACODE_PRAYER` (`:67-70`).

**Release Prayer** (`Prayer_ReleasePrayer.java:82-128`) gathers the caster's non-savable prayers, optionally filtered by name (`:84-93`). It sends a `TYP_CAST_SPELL` message for each one to the room (`:116-120`). The effect of that message is left to listeners, which were not checked.

**Sacrifice** (`Prayer_Sacrifice.java:75-146`). With no argument it picks the first non-player corpse in the room (`:75-94`). It refuses corpses flagged "FAKE" in their secret identity (`:100-105`) and other players' corpses (`:107-113`). Good or lawful casters gain 5 XP plus the XP-cost level, unless the level gap exceeds `EXPRATE` (`:128-136`). The body is destroyed (`:138`).

**Remove Inhibitions** (`Prayer_RemoveInhibitions.java:70-112`) raises a hostile flag when the target is neither the caster nor a group member (`:80-85`). If both messages are non-positive, it shifts the target's inclination by `-roll(10, level, 10*XLEVEL)` (`:97-98`), with "Chaotic, disorderly thoughts fill your head." (`:96`).

**Righteous Indignation** (`Prayer_RighteousIndignation.java:103-117`). When the bearer is the source of an XP-change message aimed at an evil MOB, a loss shrinks to `value - value/4` and a gain grows to `value + value/2` (`:111-114`).

**Regeneration** (`Prayer_Regeneration.java:103-129`). It grants +100 saves against poison and disease (`:107-108`). On MOB ticks, when the caster is sitting or sleeping and not in combat, it passes a proficiency check (`:119-121`) and then runs `recoverTick` three times (`:122-127`).

### 9. Interactions with other systems

- **Factions and flags:** `CMLib.factions()` (alignment and inclination), `CMLib.flags()` (isGood, isEvil, isLawful, canBeSeenBy, canSmell, canSpeak), `CMLib.law()` (privileges and property owners), `CMLib.clans()` (rival and war relations).
- **World and content:** `CMLib.quests()` and `CMLib.percolator()` (generated quest scripts), `CMLib.database()` (HEAVEN records and room saves), `CMLib.utensils()` (corpse resurrection, trap lookup).
- **Combat and time:** `CMLib.combat()` (postHealing, postDamage, recoverTick), `CMLib.leveler()` (postExperience), `CMLib.commands()` (postRead, postSay, postFlee, postLook). Sense Faithful gets its "Who" command from `CMClass.getCommand("Who")` (`Prayer_SenseFaithful.java:102`).
- **Two-phase messages:** Sanctuary edits a value during `okMessage` (`Prayer_Sanctuary.java:117`). Sanctum rewrites a message to NO_EFFECT (`Prayer_Sanctum.java:152`). Reflect Prayer adds a trailer message (`Prayer_ReflectPrayer.java:205`). Religious Doubt vetoes a message (`Prayer_ReligiousDoubt.java:142`).
- **Ticks:** scanners key on `Tickable.TICKID_MOB` and on room changes (`Prayer_SenseDisease.java:209-216`).

## Content worth mining

Catalogue of the 46 prayers. Each row is one class in `Abilities/Prayers/<Class>.java`. Domains are written without the `DOMAIN_` prefix. "Default" means the class does not override the mana cost.

| Prayer (class) | Domain | Mana | What it does |
|---|---|---|---|
| Sacred Imbuing Quest | BLESSING | all | Quest that, if won, imbues a worn item with a prayer (SacredImbuingQuest) |
| Restoration | HEALING | all | Full heal and removal of 16 kinds of affliction |
| Rot | CORRUPTION | default | Food and drink rot on a material timer |
| Reflect Prayer | SHIELDUSE | default | Held shield reflects the next hostile prayer |
| Resurrect | DEATHLORE | default | Resurrects player corpses; sysop HEAVEN listing |
| Revival | EVANGELISM | default | Three or more clerics convert unbelievers |
| Sense Bombs | COMMUNING | default | Reports bombs (traps) in and beside the room |
| Sense Disease | COMMUNING | default | Reports diseased MOBs and items nearby |
| Sense Injury | COMMUNING | default | Reports injured MOBs nearby |
| Religious Doubt | EVANGELISM | default | Clears worship; blocks conversion and deity spells |
| Refuge | RESTORATION | COST_PCT+25 | Item return point; "REFUGE" speech moves followers |
| Sanctum | WARDING | default | Room that keeps out non-privileged people and stops violence |
| Sanctify Room | WARDING | default | Room that blocks taking or casting on others' items |
| Seekers Prayer | COMMUNING | default | Reveals up to N running quests and their NPCs |
| Sense Hidden | COMMUNING | default | Sees hidden MOBs; saves against overlooking |
| Sense Invisible | COMMUNING | default | Sees invisible MOBs |
| Read Language | COMMUNING | default | Reads a natural-language text at 100% proficiency |
| Regrowth | HEALING | all | Regrows one missing limb per cast |
| Sanctimonious | HOLYPROTECTION | default | Combat bonus against good or neutral opponents |
| Remove Death Mark | NEUTRALIZATION | default | Removes assassination marks, including other players' |
| Regeneration | RESTORATION | default | Poison and disease saves; triple regen while resting |
| Remove Curse | NEUTRALIZATION | default | Releases curses on worn items |
| Rockskin | HOLYPROTECTION | default | Armor bonus; absorbs a few weapon hits |
| Recharge Relic | BLESSING | 100 | Adds 5 charges to a wand |
| Remove Poison | RESTORATION | default | Removes poison effects |
| Resurrect Mount | DEATHLORE | default | Passive mount snapshot; revives non-player corpses |
| Righteous Indignation | COMMUNING | default | Bends XP messages against evil targets |
| Sanctuary | HOLYPROTECTION | default | 25% damage reduction |
| Sense Chaos | COMMUNING | default | Sees chaos (ambiance) |
| Sense Evil | COMMUNING | default | Sees evil |
| Sense Good | COMMUNING | default | Sees good |
| Restore Smell | RESTORATION | default | Removes smell-blocking effects from weaker casters |
| Restore Voice | RESTORATION | default | Removes mute effects from weaker casters |
| Sacrifice | DEATHLORE | default | Destroys a body; good or lawful casters gain a little XP |
| Refresh | RESTORATION | default | Full HP, mana, moves, fatigue, hunger and thirst |
| Sense Faithful | COMMUNING | default | Internal "Who" of same-deity worshippers |
| Rock Flesh | RESTORATION | default | Dispels flesh-to-stone effects |
| Sense Devotion | COMMUNING | default | Reveals another MOB's worship and deity |
| Read Prayer | COMMUNING | default | Lets a scroll be read through the prayer |
| Release Prayer | ARCANELORE | default | Releases the caster's non-savable prayers |
| Sense Allergies | COMMUNING | default | Reports a target's allergies |
| Remove Paralysis | RESTORATION | default | Removes paralysis and unholy effects |
| Sense Alignment | COMMUNING | default | Reports a target's alignment (lawful branch broken) |
| Remove Inhibitions | EVANGELISM | default | Shifts a target's inclination toward chaos |
| Retribution | HOLYPROTECTION | default | Electrical counter-strike aura (BladeBarrier subclass) |
| Sense Chants | COMMUNING | default | Lists a target's chants with proficiency |

Sample messages worth reusing: "Your reflection faith fades." (`Prayer_ReflectPrayer.java:122`), "The rock skin around <S-NAME> absorbs the attack from <T-NAME>." (`Prayer_Rockskin.java:126`), "You feel your muscles unwilling to cooperate." (`Prayer_Sanctum.java:109`), "You receive no visions of seekers." (`Prayer_SeekersPrayer.java:128`).

## Notable design ideas

- **Three duration regimes** in one parameter: an absolute tick count, the caster's level (standard), or a percentage of standard (`StdAbility.java:2312-2331`).
- **Senses are stats.** Sense Evil, Good and Chaos are sense or ambiance bits on `PhyStats`, so the rest of the engine handles them with no special code. The holy and unholy flags give the alignment gate its meaning (`Prayer.java:381-389`).
- **Message-driven scanning.** Detection runs when the caster enters a new room, not on every tick (`Prayer_SenseBombs.java:213-220`).
- **Religion as a temporary state.** Religious Doubt clears the worship ID while it runs (`Prayer_ReligiousDoubt.java:104`), and Revival needs a real deity (`Prayer_Revival.java:227-228`).
- **Group arithmetic.** Revival's pool is a level average with a bonus for each extra cleric beyond three (`Prayer_Revival.java:181-182`).
- **Decay as a table.** Rot maps each material to a shelf life, with a "never" entry (`Prayer_Rot.java:127-207`).
- **Wards as text.** Sanctum stores its owner, and a list of say-lines, in one text field (`Prayer_Sanctum.java:127-140`).
- **Quest-backed items.** Sacred Imbuing Quest generates a quest at cast time and pays XP scaled by the imbued prayer's level (`Prayer_SacredImbuingQuest.java:187-192`).

## Problems and cruft

1. **Lawful is never reported.** `Prayer_SenseAlignment.java:103` tests `isChaotic` a second time, so the "lawful" branch at `:104` is dead. The correct test is `isLawful`, which exists in `Libraries/interfaces/CMFlagLibrary.java:647`.
2. **Allergy normalization is discarded.** `Prayer_SenseAllergies.java:100-110` builds `allergies`, but `:113` prints `V`, the raw uppercased tokens. The result is never used.
3. **Wrong effect name.** `Prayer_SenseInjury.java:128` tests the effect "Injuries". The injury effect used elsewhere is "Injury" (`Abilities/Misc/Injury.java`; `Prayer_Restoration.java:198`; `Abilities/Common/Bandaging.java:91`). A target whose only sign is that effect is not reported.
4. **Probable off-by-one.** `Prayer_ResurrectMount.java:141` reads `if(x>0)`, probably meant as `x>=0`. When the mount is already at index 0, a second copy is stored instead of reusing the first. Not checked in play.
5. **Container traps missed.** `Prayer_SenseBombs.java:138-148` checks only the deep contents of a container, and the Item branch at `:150-151` is never reached for containers. A bomb on the container itself is not reported. `Items/Basic/StdContainer.java:729` shows that `getDeepContents` excludes the container.
6. **Fragile cast.** `Prayer_Rot.java:211` casts to `Decayable` without a check. It is safe in practice, since Rot is attached only to Food and Drink, and both extend `Decayable` (`Items/interfaces/Food.java:38`, `core/interfaces/Drink.java:38`).
7. **Full wands are destroyed.** `Prayer_RechargeRelic.java:125-128` destroys a fully charged wand instead of refusing. The flavour text says so, but it is an easy trap for players.
8. **Ward marked malicious.** `Prayer_SanctifyRoom.java:68` returns `QUALITY_MALICIOUS` for a protective room. That may skew the AI's choice of when to cast it.
9. **Shared and unused state.** `Prayer_SacredImbuingQuest.java:123` makes `lastUsed` a static set keyed by player name, shared across all casters. `maxPrayerLevel()` (`:393-396`) has no caller in the Prayers folder.
10. **Small cruft.** `Prayer_Restoration.java:276` has the typo "afflication". `Prayer_Refresh.java:118` and `:120` set mana twice. `Prayer_Rockskin.java:84` declares `oldHP` and never uses it. `Prayer_ReadLanguage.java:92` declares `readWhatP`, which is unused.
11. **Allocation on query.** `Prayer_Restoration.java:83-100` builds several throwaway ability objects and a factory MOB every time `supportsMending` is called.
12. **Possibly unreachable branch.** `Prayer_Sanctum.java:121-137` handles an effect that is attached to a MOB. `invoke` (`:159-207`) always targets the room, so this branch is probably unreachable from this class.
13. **Unverified membership.** `Prayer_Refuge.java:132` takes `properTargets(msg.source(),null,false)`. That set comes from `CMLib.combat().properTargets` (`StdAbility.java:2113-2125`). Whether it contains only party members was not verified, and it may move other MOBs too.
14. **Mana on failed alignment.** `Prayer.java:423-430` runs the alignment check after `StdAbility.invoke`. Whether mana is already spent on that failure was not verified.
15. **Multiplayer assumptions.** Remove Death Mark scans all players (`Prayer_RemoveDeathMark.java:132-144`). Resurrect's sysop path reads other players' HEAVEN records (`Prayer_Resurrect.java:103-158`). The Revival ritual relies on several clerics of one deity being in the room.

## Takeaways for a Python SUD

- **Prayer as a record.** Model each prayer as data: id, domain, alignment flags (holy, unholy, neutral), mana cost, valid target kinds (mob, item, room) and a duration rule (`Ticks(10)`, `Standard`, `Percent(-50)`). Pure stat buffs need no code. Detection, cures and rituals each get a small hook.
- **One detector class.** Replace the nine near-identical Sense prayers with a `Detector(label, test, scope)` that runs when the player enters a room and reports matches by direction. Sense Bombs and Sense Disease differ only in their predicate.
- **Effect hooks.** Use a base `Effect` class with `on_apply`, `on_unapply`, `on_tick`, `modify_stats(stats)`, `before(event) -> bool` (veto) and `after(event)`. Sanctuary becomes a `before` hook that scales damage, and Reflect Prayer a `before` hook that intercepts and recasts.
- **Alignment roll.** Keep the single line `roll(1,100) > distance_from_pure` for holy and unholy spells. It is tunable and easy to test.
- **Rot as a table.** `SHELF_DAYS = {"flesh": 1, "vegetation": 5, "milk": 5, "blood": 1, "herbs": None, ...}`, with `None` meaning no decay. This fits a hunger or spoilage system for a living world, checked once per in-game day rather than every 60 s.
- **Relics.** Wands with `charges`, `max_charges` and a recharge of +5, refusing a full wand instead of destroying it.
- **Rumour instead of Seekers.** Seekers Prayer maps to a rumour system: reveal a few NPCs with open tasks in areas the player can reach.
- **Return point.** Refuge maps to a waypoint stored on an item, which suits a single player who needs an escape route.
- **Drop for single player.** HEAVEN records and sysop listings, the cross-player mark scan, and the static `lastUsed` set. Keep Revival as a function over the NPC companions present, not a multi-player ritual.
- **Simplify quests.** Sacred Imbuing Quest's runtime XML percolator is heavy. A small JSON template for "fetch or kill in an area, with an expiry" would give the same shape.
- **Wards.** Sanctum and Sanctify Room become one `Ward` with an owner (a name or a faction) and an allowed-actions list. Clan war logic becomes faction standing.

## Coverage

- **Read in full (46 of 46):** every file in the bucket. Line numbers are preserved. Blank lines were removed in the display, and for the shorter files lone brace lines were removed too. `Prayer_SacredImbuingQuest.java` and `Prayer_Restoration.java` were read unfiltered.
- **Outside the bucket, read in full:** `Abilities/Prayers/Prayer.java`, `Abilities/Prayers/Prayer_SenseProfessions.java`.
- **Outside the bucket, partial:** `Abilities/Prayers/Prayer_BladeBarrier.java` (lines 91-96, 117-150, 174-178); `Abilities/StdAbility.java` (211, 276, 474-480, 2113-2125, 2312-2375); `Libraries/Factions.java` (873-905); `Abilities/Prayers/Prayer_Bless.java` (signatures at 123 and 173); `Libraries/interfaces/CMFlagLibrary.java` (647); `Items/Basic/StdContainer.java` (729); `Items/interfaces/Food.java` (38); `core/interfaces/Drink.java` (38); `Abilities/interfaces/Ability.java` (48, 651, 653); `core/CMProps.java` (681); `Libraries/MUDFight.java` (355-372).
- **Skipped:** the other prayers the bucket calls (Prayer_Faithless, Prayer_UndeniableFaith, Prayer_Fortress, the cure prayers Prayer_CureDisease, Prayer_CureBlindness, Prayer_CureDeafness and Prayer_Freedom, and Prayer_Infuse*), and the implementations behind `CMLib.utensils().resurrect`, `CMLib.combat().postHealing` and `CMLib.dice()`. These belong to other buckets or lie beyond the scope of this chapter.
