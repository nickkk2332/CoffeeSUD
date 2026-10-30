# Prayers, part 6: Minor Infusion to Purify

> Part of the CoffeeMud NEWOVERVIEW. Bucket: `b072` (Abilities/Prayers [part 6/8: Prayer_MinorInfusion .. Prayer_Purify]) · 42 files · 8637 lines of Java.

Citation convention: a bare `File.java:NNN` is in `com/planet_ink/coffee_mud/Abilities/Prayers/`. Files outside that folder are given with their full path from the CoffeeMud folder. Line numbers were checked against the source. "XLEVEL" below means the expertise bonus read through `StdAbility.getXLEVELLevel` (`com/planet_ink/coffee_mud/Abilities/StdAbility.java:444-447`).

## Summary

This bucket holds 42 prayers, the cleric-side spells of CoffeeMud. Almost every one extends `Prayer` (`Prayer.java:37`), which adds an alignment gate to casting; Planar Travel extends `StdPlanarAbility` instead. Twenty are protections: mostly flat resistance bonuses plus a 50% "repel" roll, and alignment auras that damage or armour the bearer depending on which alignment it faces. Nine are offensive or control prayers (paralysis, plague, poison, a curse, a planar plague, a pacify, a room-wide dispel, a clan peace ritual and a land dispel). The other ten are utilities: mana healing, purifying food and drink, preserving a corpse, a radius "look", a compass trail, a wisdom buff, alignment nudging, weapon reshaping and planar travel. Three larger systems stand out: Planar Pilgrimage generates a multi-step quest from XML templates, weighted by plane properties; Prophecy reads the live quest list and schedules delayed scripted predictions; and Monolith creates terrain-like walls with their own combat rules. For a single-player Python game the most reusable parts are the veto-based effect hooks, the data-driven protection table, the level-gated dispel rule and the quest-template weighting.

## Key files and types

| File or class | Role | Lines |
|---|---|---|
| `Prayer_PlanarPilgrimage.java` | Commissions a planar quest built from `randareas/example.xml` templates; awards a deity favour on completion | 1137 |
| `Prayer_Prophecy.java` | Trance; when it ends unbroken it yells live quest timers and/or schedules a delayed prediction | 428 |
| `Prayer_Monolith.java` | Creates an ice, fire, earth or air wall item in the room, each with its own combat rule | 397 |
| `Prayer_MorphUnholyWeapon.java` | Reshapes an owned unholy-armament weapon into one of 14 weapon forms | 267 |
| `Prayer_PeaceRitual.java` | Five-minute clan peace; ending it downgrades war to hostile | 245 |
| `Prayer_PietyCurse.java` | Curse: crude speech, socials and weapon attacks damage the cursed mob | 220 |
| `Prayer_ProtectSacredItem.java` | Permanent item protection for deity-infused items; costs 500 XP | 215 |
| `Prayer_ProtLaw.java` | Lawful/chaotic aura: blocks law spells, damages lawful bearers, armour bonus vs lawful | 213 |
| `Prayer_ProtChaos.java` | Mirror of ProtLaw for chaos | 212 |
| `Prayer_PreserveKnowledge.java` | Absorbs experience loss from a budget; leaves a "vulnerable mind"; survives death | 211 |
| `Prayer_ProtGood.java` | Protection from good: blocks holy spells, damages good bearers, armour bonus vs good | 210 |
| `Prayer_ProtEvil.java` | Mirror of ProtGood for evil | 209 |
| `Prayer_ProtectedMount.java` | Keeps a rideable animal mount alive (flees at 1 HP); +10 undead save | 202 |
| `Prayer_Position.java` | Reports a compass trail from the caster's last position to here | 191 |
| `Prayer_ProtUndead.java` | Aura vs undead: undead bearers damaged each tick; large armour bonus vs undead | 190 |
| `Prayer_Pacify.java` | Ends a fight and makes the target unable to act hostilely for one MUD month | 188 |
| `Prayer_ProtectItem.java` | Timed item protection (no infusion requirement, no XP cost) | 181 |
| `Prayer_Purify.java` | Cleans food/drink and pills/potions; un-befouls a room shrine | 181 |
| `Prayer_Nullification.java` | Room dispel: removes one eligible effect per inhabitant; curses exempt | 172 |
| `Prayer_Paralyze.java` | Unholy paralysis, 4 to 10 ticks | 170 |
| `Prayer_ProtectHealth.java` | +50 poison save; 50% repel of disease tools | 169 |
| `Prayer_ProtDeath.java` | 30 + XLEVEL percent chance to cheat a death: full heal, return to start room | 168 |
| `Prayer_ProtDisease.java` | +100 disease save; 50% repel of disease messages | 168 |
| `Prayer_ProtBless.java` | Repels blessing-domain effects aimed at the bearer | 167 |
| `Prayer_ProtParalyzation.java` | +100 paralysis save; 50% repel of paralysis messages | 167 |
| `Prayer_ProtPoison.java` | +100 poison save; 50% repel of poison messages | 167 |
| `Prayer_ProtCurses.java` | +10 undead save; 50% repel of hostile curse spells | 166 |
| `Prayer_ProtOutsiders.java` | Armour bonus vs outsider foes | 165 |
| `Prayer_NeutralizeLand.java` | Room dispel of one effect; no curse exemption | 162 |
| `Prayer_Philosophy.java` | Wisdom boost by base class plus an XLEVEL term | 150 |
| `Prayer_ProtectElements.java` | +50 to five elemental saves | 150 |
| `Prayer_ProtCold.java` | +100 cold save | 143 |
| `Prayer_ProtFire.java` | +100 fire save | 140 |
| `Prayer_PlanarPlague.java` | Disease, only against a target on a different plane | 136 |
| `Prayer_Poison.java` | Applies the generic `Poison` effect | 133 |
| `Prayer_Plague.java` | Applies `Disease_Plague` | 128 |
| `Prayer_MinorInfusion.java` | Restores mana: 2d(level)+10 | 125 |
| `Prayer_ModerateInfusion.java` | Restores mana: 5d(level)+20 (same structure as Minor) | 125 |
| `Prayer_PlanarTravel.java` | Planar travel; delegates to `com/planet_ink/coffee_mud/Abilities/StdPlanarAbility.java` | 121 |
| `Prayer_MoralBalance.java` | Moves a target's alignment faction 1/8 of the way to its midpoint | 120 |
| `Prayer_PreserveBody.java` | Stops a dead body decaying | 115 |
| `Prayer_Omnipresence.java` | Radial "remote look" over every accessible room in range | 113 |
| `Prayer.java` (base, read for context) | Alignment gate, relic and infusion helpers | 432 |

## How it works

### Shared base: `Prayer.java`

- The base sets `classificationCode()` to `ACODE_PRAYER` (`Prayer.java:86-88`), trigger words `PRAY` and `PR` (`:77`), and a default target of mobs (`:66-68`).
- **Alignment gate.** `Prayer.invoke` runs `prayerAlignmentCheck` after the normal invoke (`Prayer.java:422-430`). The roll applies only to a player's non-automatic cast, only when the prayer has any flag set (`Prayer.java:379`), and only when the caster's factions do not already consider the prayer appropriate (`:374-379`). The purity basis is the caster's alignment-faction purity against GOOD for HOLY-only prayers (`hq=1000`), against EVIL for UNHOLY-only prayers (`hq=0`), and against NEUTRAL minus 10 otherwise (`:382-401`). The prayer succeeds if `d100 > basis` (`:403`). Failure prints "The goodness of X disrupts your prayer" or the matching evil or neutral text (`:407-416`).
- **Relic and infusion helpers.** Items can carry a `Dischargeable` container of prayers (`getRelicPrayers`, `:151-183`; `getRelicCharges` / `setRelicCharges`, `:330-365`). `checkRequiredInfusion` demands that the item's cleric infusion matches the caster's deity (`:196-206`). `infusePhysicalByAlignment` adds alignment infusions (`Prayer_InfuseHoliness`, `Prayer_InfuseUnholiness`, `Prayer_InfuseBalance`, and the law-axis `Prayer_InfuseDiscipline`, `Prayer_InfuseImpunity`, `Prayer_InfuseModeration`), but only if the item has no deity infusion (`:208-255`, deity-null checks at `:224` and `:246`).
- **Durations.** Prayers call `beneficialAffect` or `maliciousAffect` (`com/planet_ink/coffee_mud/Abilities/StdAbility.java:2341`, `:2185`). A positive tick argument is used as an absolute number of ticks. Zero selects the standard formula: for beneficial effects `casterLevel*4+40`, capped at the target's level plus `EXPRATE` when the target is a mob (`com/planet_ink/coffee_mud/Abilities/StdAbility.java:2312-2332`); for malicious effects `round(1.1 * adjustedLevel) + 25` (`com/planet_ink/coffee_mud/Abilities/StdAbility.java:2174-2178`). A tick is 4000 ms by default (`com/planet_ink/coffee_mud/core/CMProps.java:681`).

### Planar Pilgrimage (`Prayer_PlanarPilgrimage.java`)

This is the largest file and the most system-like prayer in the bucket. A priest who worships a deity sends a player who worships the same deity on a quest that is generated, not written.

**Data model.** `quest1` (the generated `Quest`), `planeName`, `deityName` and `template` are per-cast fields (`:126-129`). A static `lastUsed` set holds player names for one day, capped at 1000 entries (`:124`; `LimitedTreeSet` constructor `com/planet_ink/coffee_mud/core/collections/LimitedTreeSet.java:66-107`). The cast result is stored in the effect's misc text as `PLANE`, `DEITY` and `TEMPLATECODE` (`:1112`, parsed at `:263-273`).

**Cast (`invoke`, `:817-1128`).** The checks are: target must be a player (`:829`), not in `lastUsed` (`:834`), the caster must worship a deity (`:840-845`), and the target must worship the same one (`:846-850`). The prayer then sorts every plane (from `StdPlanarAbility.getAllPlaneKeys`) into four alignment buckets by comparing the plane's alignment range with the deity's and the target's alignment and inclination factions (`:852-1004`): same, similar, opposed, dissimilar.

**Template weighting (`:1006-1096`).** Each `QuestTemplate` has requirements (`QuestReq`, `:789-793`). A requirement passes or fails per plane, and each pass adds a weight. The weights are SAME +4, OPPOSED +4, SIMILAR +3, DISSIMILAR +3, INNER +2 and OUTER +2 (`:1024-1074`). A template's total weight for a plane is the sum of its passed weights, or zero if any requirement fails (`times=0; break`). The plane and template pair is then picked uniformly from a list that repeats each pair by its weight (`:1085-1096`). INNER and OUTER come from the plane's `CATEGORY` variable (`:1034`, `:1052`).

The active templates (`:795-815`) are:

| Template | Requirements (weight) | Quest in the cross-player branch |
|---|---|---|
| PEACEFUL_CAPTURE | SIMILAR (3) | capture a non-aggressive missionary of the deity |
| DEFEAT_CAPTURE | DISSIMILAR (3) | capture a denizen for interrogation |
| COLLECT_GROUND | OUTER (2) | gather scattered fragments from the ground |
| COLLECT_MOBS | OUTER (2) + DISSIMILAR (3) | collect fragments from denizens |
| DELIVERY | INNER (2) | deliver a divine decree |
| DISPEL | OUTER (2) + SIMILAR (3) | aid the pious denizens |
| KILL_ELITE | OUTER (2) + DISSIMILAR (3) | kill an elite denizen |
| KILL_OFFICER | OUTER (2) + DISSIMILAR (3) | thin the ranks of enemy officers |

COLLECT_RESOURCES, ESCORT and TRAVEL are commented out (`:801`, `:804`, `:807`; their code at `:381-406`, `:430-439`, `:466-489`). The self-targeted branch (`:562-768`) builds `auto_*` templates instead of `normal_*` ones.

**Generation (`deviseAndStartQuest`, `:310-780`).** The prayer loads `randareas/example.xml` (`:320`), fills a table of defined ids (plane area, faction `DEITY_<NAME>` at `:340-351`, target level from the area's median level, `DURATION` and `EXPIRATION` set to the prayer's remaining ticks at `:336-337`), then calls the quest-script percolator (`CMLib.percolator().buildDefinedIDSet`, `checkRequirements`, `buildQuestScript`, `postProcess`, `:497-518`). It retries up to five times (`:313`). The finished quest is started, marked as a copy (`:542`), and its summary is shown to the target (`:544-556`).

**Timing.** The cast lasts two hours (`:1109`). `tick` (`:151-254`) waits until the target is on the chosen plane (`:165-166`), then generates the quest. If the target never gets there, the effect expires and `unInvoke` reports failure (`:105-122`). Completion is detected with `quest1.wasWinner` (`:179`). The award is a `FavoredOf<Deity>` effect on the pilgrim, lasting `ticksPerDay` (`:197-244`). It adds +2 to every base stat (`:230-234`) and 10 per level to max HP, 5 per level to max mana and 5 per level to max movement (`:237-242`). The caster's cooldown is `getTicksBetweenCasts`, one day (`:140-143`), and the pilgrim is recorded in `lastUsed` (`:195`).

**Cost.** `overrideMana` returns `COST_ALL` (`:73-77`). `getXPCost` (`:782-787`) is defined here but not called in this file.

### Prophecy (`Prayer_Prophecy.java`)

Prophecy is a short trance whose payoff is delivered at the end. It is the bucket's only mechanic that reads world state.

**Trance (`invoke`, `:393-427`).** The prayer cannot start in combat (`:395-399`). It lasts 3 ticks (`:420`). `executeMsg` (`:339-390`) ends the trance early if the caster moves, leaves, flees or recalls (`:349-358`), or, while the prophecy has not yet been delivered (`abilityCode()==0`), if anyone else speaks, casts, opens, closes, locks, unlocks, pushes or pulls, or makes a sound (`:360-388`).

**Delivery (`unInvoke`, `:200-337`).** The prophecy is delivered only if the trance ran out naturally: `tickDown` is zero, the caster is alive and not in combat (`:209`). The number of prophecies is `XLEVEL + 2`, plus `adjustedLevel / qualifyingLevel` when the qualifying level exceeds 1 (`:218-220`). The prayer scans all quests and keeps the N with the nearest end, using remaining ticks for waiting or running quests (`:222-262`). Copies, quests with duration zero, and "holidays" are skipped (`:225`).

If the roll `1d11 < XLEVEL` (`:264`) succeeds, or no quests are known, and another player is in the room, the prayer tries a prediction about a random such player (`:268-280`, `isPlayer` at `:274`) through `generateEventPrediction` (`:281`). If that yields text it is yelled instead (`:282-288`). Otherwise the quest prophecy is yelled to the room (`:292-329`), built as one of 10 starter phrases (`:95-132`), then each quest name with "will end in" or "will begin in" and a clock-formatted time. Names use the quest's `DISPLAY` stat, with underscores replaced and purely numeric words removed (`:303-313`). If nothing is known, the caster gets "You receive no prophetic visions" (`:331`).

**Predictions (`generateEventPrediction`, `:134-198`).** The prayer loads `skills/predictions.xml` once and caches it as a resource (`:139-147`). It picks a `RANDOM_PREDICTION` piece, runs its `Prediction` function in a scripting engine with a `MPContext` (`:163-168`), and retries up to 10 times for a non-empty summary (`:151-172`). The delivery time is built from the home clock: a `1d5` roll gives no extra delay for 1, `1d10+5` weeks for 2 to 4, `1d10+3` months for 5, then `1d10+3` days and `1d10+3` hours (`:174-188`). The script is queued with the `ScriptLater` effect (`:189-190`) to run on the target later.

### Monolith (`Prayer_Monolith.java`)

Monolith creates a `GenItem` wall in the room (`:346-375`, not gettable at `:383`). The wall's effect runs on the item (`:387`) and uses the standard beneficial duration (`:387` passes 0, see the durations note above). `unInvoke` destroys the wall with a message (`:221-253`).

**Type selection.** Four types, `TYP_ICE=0`, `TYP_FIRE=1`, `TYP_EARTH=2`, `TYP_AIR=3` (`:101-104`). With no keyword the type is `roll(1,4,-1)` (`:328`). Otherwise the cast text is matched as substrings, in this order: STONE or EARTH, then ICE or WATER, then AIR, then FIRE (`:329-340`).

**Casting rules.** The caster must be in ranged combat (`:303-304`). Only one monolith may be in a room (`:308-316`). Range is 1 up to 10, adjusted by expertise (`:59-69`).

| Wall | Pool | Rule (source) |
|---|---|---|
| ice | 20 hack points (`:353`) | A melee attacker in combat with the caster hacks with `TYP_ADVANCE` (`:125`) and depletes the pool by its damage (`:138`). On break, every attacker of the caster 1 to 2 ranges away takes `roll(level/2, 4, 0)` cold piercing (`:141-153`). |
| earth | caster's base HP / 6 (`:345`) | Same hacking rule, no splash (`:190-215`). |
| fire | none | Each mob tick, every melee attacker of the caster in the room takes `roll(round((adjLevel + 2*X1)/4), 6, 1)` burn (`:271-293`). The wall is removed when the caster leaves (`:277-278`). It emits light (`:373`). |
| air | none | A ranged weapon fired at the caster is absorbed: a factory mob "The monolith of air" fires the same weapon back at the shooter through `postWeaponAttackResult` at the wall's level (`:164-189`). The original attack is vetoed (`:187`). The ranged weapon must be wielded, not just carried (`:172`). |

### Protection auras and resistances

Most protections use `beneficialAffect(...,0)`, so they run on the standard duration. They use three mechanisms.

**Stat bonuses.** The save stat is raised, typically by +100 (`Prayer_ProtPoison.java:107`, `Prayer_ProtParalyzation.java:107`, `Prayer_ProtDisease.java:107`, `Prayer_ProtCold.java:107`, `Prayer_ProtFire.java:107`). `ProtectElements` adds +50 to five saves (`:109-113`). `ProtectHealth` adds +50 to the poison save (`:107`). `ProtCurses` adds +10 to the undead save (`:107`). `ProtectedMount` adds +10 to the undead save (`Prayer_ProtectedMount.java:96`).

**Repel rolls.** Each of these vetoes a matching incoming message with a 50% chance (`rollPercentage()>50`): disease messages (`Prayer_ProtDisease.java:123-127`), poison (`Prayer_ProtPoison.java:123-127`), paralysis (`Prayer_ProtParalyzation.java:123-127`), and `DiseaseAffect` tools (`Prayer_ProtectHealth.java:120-124`). `ProtCurses` vetoes hostile curse-domain spells whose `castingQuality` is MALICIOUS (`Prayer_ProtCurses.java:121-127`). `ProtBless` vetoes blessing-domain effects with no roll (`Prayer_ProtBless.java:111-127`). Its `affectCharStats` body is commented out (`:104-107`).

**Alignment auras.** `ProtGood` (UNHOLY, `Prayer_ProtGood.java:74`), `ProtEvil` (HOLY, `Prayer_ProtEvil.java:74`), `ProtLaw` (flagged LAW, `Prayer_ProtLaw.java:74`), `ProtChaos` (LAW, `Prayer_ProtChaos.java:74`), `ProtUndead` (NEUTRAL, `Prayer_ProtUndead.java:74`) and `ProtOutsiders` (HOLY, `Prayer_ProtOutsiders.java:74`) each do three things:

1. Veto hostile spells whose flags mark the opposing alignment, with no roll (for example `ProtGood` at `Prayer_ProtGood.java:118-128`, `ProtEvil` at `Prayer_ProtEvil.java:120-125`). The veto messages name the protecting field: ProtGood's is "The unholy field around X protect(s) Y from the goodly magic attack" (`Prayer_ProtGood.java:126`), ProtEvil's is the holy-field version (`Prayer_ProtEvil.java:125`), and ProtLaw's is "The chaotic field around X protect(s) Y from the lawful magic attack" (`Prayer_ProtLaw.java:127`).
2. Damage the bearer each tick if the bearer is of the opposing alignment. The formula is `round((level + 2*XLEVEL) / 3)` (`Prayer_ProtGood.java:101`, same in ProtEvil `:101`, ProtLaw `:102`, ProtChaos `:101`, ProtUndead `:102`). The damage message type differs: `CMMsg.TYP_UNDEAD` for ProtGood (`:103`), ProtLaw (`:104`) and ProtChaos (`:103`); `TYP_JUSTICE` for ProtEvil (`:102`); `TYP_CAST_SPELL` for ProtUndead (`:104`).
3. Lower `phyStats().armor()` while the bearer fights an opposing victim: `-(10 + 2*XLEVEL)` vs good or lawful (`Prayer_ProtGood.java:148`, `Prayer_ProtLaw.java:149`), `-(10 + 2*XLEVEL)` vs evil (`Prayer_ProtEvil.java:146-147`), `-(10 + 2*XLEVEL)` vs chaotic (`Prayer_ProtChaos.java:147-148`), `-(50 + 5*XLEVEL)` vs undead (`Prayer_ProtUndead.java:125`), `-(20 + 3*XLEVEL)` vs outsiders (`Prayer_ProtOutsiders.java:103`). Whether a lower armour value helps the bearer depends on `com/planet_ink/coffee_mud/Libraries/MUDFight.java:348-420` (adjusted armour and `rollToHit`), which this reading did not trace.

### Death, experience and item protection

**Protection Death (`Prayer_ProtDeath.java:90-124`).** When the bearer's death message is about to be shown, the prayer rolls `rollPercentage() < 30 + XLEVEL` (`:102-103`). On success it resets the bearer to max state, shows "protected from death", moves the bearer to its start room, unInvokes itself and unInvokes every effect the bearer has (`:104-118`). On failure it unInvokes and the death goes ahead (`:121`). Players with `Dueling` bypass it (`:100-101`).

**Protected Mount (`Prayer_ProtectedMount.java:99-122`).** A rideable animal with a rider capacity, flagged as an animal and of a favoured mount race or category, can be targeted (`:124-137`, `getFavoredMounts` at `:131`). Its death is vetoed unless it has `Dueling` (`:110`). It is set to 1 HP if below (`:113-114`), says "wisely panic(s)!" (`:115`) and is forced to `FLEE` (`:117`).

**Preserve Knowledge (`Prayer_PreserveKnowledge.java:121-171`).** The prayer lasts the standard duration. On a death by the bearer it sets `canBeUninvoked=false` until a trailer runnable resets it, so the effect survives death (`:127-142`). On an experience-change message with a loss of more than 1 (`:144-148`):

- While the budget covers half the loss, the budget falls by half the loss and the loss is halved (`:149-152`).
- Otherwise the budget is spent in full, the loss is reduced by the budget, the state becomes vulnerable, and the bearer's tick-down grows by one MUD day in ticks (`:156-165`), with the message "Extreme experience loss leaves your mind traumatized!!" (`:166`).

The starting budget is `1100 + 100*XLEVEL` (`:201`). While vulnerable it cannot be removed until fewer than 5 ticks remain (`:92-101`).

**Protect Sacred Item (`Prayer_ProtectSacredItem.java:146-214`).** Targets an unworn item (`:154`). The item must be infused with the caster's deity (`Prayer.checkRequiredInfusion`, `:163-172`). It costs 500 XP (`:174`, adjusted by the XP-cost expertise, `com/planet_ink/coffee_mud/Abilities/StdAbility.java:474-480`), and the XP is lost before the proficiency roll (`:185` before `:194`). On success it adds a permanent non-uninvokable copy of itself (`:205-206`). The copy sets the `SENSE_ITEMNORUIN` bit (`:105`) and keeps the item's uses remaining at least at its best observed value (`:97-111`). Any malicious message or ability aimed at the item is vetoed with "X is unbreakable!" (`:130-144`).

**Protect Item (`Prayer_ProtectItem.java:143-181`).** The timed version. It needs no infusion and no XP. Its duration is `ticksPerMudHour*6*(1+XLEVEL) + ticksPerMudHour*adjustedLevel` (`:171`). It has the same wear-ratchet (`:93-125`) and "unbreakable" veto (`:127-141`).

### Offensive and control prayers

**Piety Curse (`Prayer_PietyCurse.java:188-219`).** Targets a mob that is not good (`:199`). Its curse lasts the standard malicious duration (`:207`, `com/planet_ink/coffee_mud/Abilities/StdAbility.java:2174-2178`). While cursed:

- Speech containing one of the 83 names in `UNPIOUS_SOCIALS` (`:89-98`), or a word starting with the first three characters of the game's word-filter constant (`:143`, `com/planet_ink/coffee_mud/core/CMProps.java:58`), damages the cursed mob. The damage is `round(1 + (5 + invokerLevel/20)) + sourceLevel` (`:151`, `:159`). Integer division applies to `invokerLevel/20`.
- A social with a listed base name does the same (`:168-170`).
- A weapon attack on another mob damages the attacker by `roll(1, round(1 + (5 + invokerLevel/20) / targetSpeed), 0)` (`:172-174`).
- Each damage hit also triggers a revenge attack (`:181-182`).
- Each tick, a monster in combat is panicked (`:127-131`). On removal, a monster is made to stand, wander away, and attack its invoker if they share a room (`:111-117`).

**Paralyze (`Prayer_Paralyze.java:129-169`).** The level difference is `targetLevel - (casterLevel + 2*XLEVEL)`, clamped to 0 to 6 (`:135-139`). The proficiency roll is adjusted by `XLEVEL - (target WIS*2 + levelDiff*5)` (`:144`). It sends a second message of type `TYP_PARALYZE` (`:148`), which `ProtParalyzation` can veto (`Prayer_ProtParalyzation.java:124`). The duration is `10 - levelDiff`, minimum 4 (`:156-158`), passed as an absolute tick count. The effect sets `CAN_NOT_MOVE` (`:97`, `com/planet_ink/coffee_mud/Common/interfaces/PhyStats.java:417`).

**Plague (`Prayer_Plague.java:110-116`) and Poison (`Prayer_Poison.java:116-122`).** Each sends a malicious cast message and a disease or poison message, then applies `Disease_Plague` or `Poison`. Their gates differ (see Problems). The message type `TYP_DISEASE` or `TYP_POISON` is what the protections above check.

**Planar Plague (`Prayer_PlanarPlague.java:105-120`).** Succeeds only if the target's plane differs from the caster's (`:105-107`). It then applies `Disease_PlanarPlague` and sets its level to the caster's adjusted level (`:116-120`).

### Dispel and peace

**Nullification (`Prayer_Nullification.java:113-171`).** A room-wide dispel. Each inhabitant is considered in turn (`:124-167`). An effect is eligible if it can be uninvoked, is not auto-invoked, is not curse-domain, and is a spell, prayer, chant or song (`:140-145`). Its invoker's level must be at most the caster's level plus `2*XLEVEL` (`:148`). The last eligible effect in the list is removed (`:161-162`). If effects exist but none qualify, the message is "The magic on X appears too powerful to be nullified" (`:153-156`). A failed roll prints the fizzle message once for each inhabitant (`:165-166`).

**Neutralize Land (`Prayer_NeutralizeLand.java`).** Room-level version (`:78` targets rooms). Its eligibility is wider: it also accepts effects the caster invoked, effects whose text is the caster's name, and clan-owned effects (`:125`). It has no curse-domain exemption. Removed room effects that are not uninvokable are written to the database (`:150-152`).

**Pacify (`Prayer_Pacify.java:147-187`).** Works only on a target in combat (`:167`). It makes the target at peace and makes every creature fighting the target stop (`:168-176`). It then applies a malicious effect for one MUD month (`:177`, `CMProps.Int.TICKSPERMUDMONTH`). While pacified (`:118-145`):

- The pacified mob's own malicious actions are vetoed with "You feel too passive to do that" (`:128-141`), and its victim is cleared.
- Being targeted by a malicious action removes the pacification (`:125-126`).

**Peace Ritual (`Prayer_PeaceRitual.java:94-244`).** Needs a clan (`:190`) at war with the target clan (`:203-208`) and at least one member of the target clan present (`:210-223`). The ritual lasts five minutes (`:236`). Each tick it posts to clan-info channels (`:111-113`). It is disrupted by any hostile malicious message aimed at the caster (`:127-135`) or the caster's room movement (`:137-144`). When it ends unbroken, the relations are changed from war to hostile in both directions (`:162-171`), with "There is now peace between X and Y" (`:172-174`).

### Utility prayers

**Minor and Moderate Infusion (`Prayer_MinorInfusion.java:76-117`, `Prayer_ModerateInfusion.java:114-115`).** Both implement `MendingSkill` and restore mana only, not HP. `supportsMending` is true only when the target's mana is below max (`:76-79`), and `castingQuality` is indifferent otherwise. Minor restores `roll(2, adjustedLevel, 10)` mana and Moderate restores `roll(5, adjustedLevel, 20)` (`:114-115`). They are referenced by the `DRAGONBLOOD` material (`com/planet_ink/coffee_mud/Items/interfaces/RawMaterial.java:778`, `Prop_UseSpellCast2(Prayer_MinorInfusion)`).

**Purify (`Prayer_Purify.java:95-180`).** A target must be Food or Drink (`:124-129`). A non-freshwater drink is converted to fresh water (`:146-152`). All effects on the item are deleted (`:153-157`). Pills and potions get the `Prayer_Sober` spell list (`:158-169`). Food stops decaying while the effect is present (`:90-91`). In a room, Purify removes a `Skill_BefoulShrine` effect (`:105-118`). That branch skips the proficiency roll and always returns true (`:117`).

**Preserve Body (`Prayer_PreserveBody.java:72-106`).** Targets a dead body (a `DeadBody` check in `invoke`). It sets the expiration date to 0 and removes the decay tick (`:105-106`).

**Omnipresence (`Prayer_Omnipresence.java:86-99`).** Computes layers as `XLEVEL + 1 + 2*XMAXRANGE + adjustedLevel/qualifyingLevel` (`:86-88`). It lists rooms within that radius, passable and not water or air (`:99`, tracking flags). For each accessible room it sends a `MSG_LOOK` (the caster sees the room). No target. On failure it shows a visual fizzle message.

**Position (`Prayer_Position.java:81-190`).** Stores the caster's last position (`:167-170`, `:183`). `trailTo` runs a radiant search from the old room (`:88`), walks back, and emits a run-length encoded direction string such as "3n" or "e" (`:131-161`). The letter for each step is the first character of the direction name (`:129`). Unreachable rooms return "You can't get to 'ROOMID' from here" (`:100`).

**Philosophy (`Prayer_Philosophy.java:82-106`).** Adds to WISDOM by base class: Fighter 1, Mage 2, Thief 1, Bard 1, Cleric 3, Druid 3, default 1 (`:87-104`). It adds `(XLEVEL+2)/3` (`:105`).

**Moral Balance (`Prayer_MoralBalance.java:82-112`).** Moves the target's alignment faction 1/8 of the way from its current value to the midpoint of the faction range (`:98-101`), using `postSkillFactionChange` (`:103`). It is a malicious message unless the target is the caster or a group member (`:82`).

**Morph Unholy Weapon (`Prayer_MorphUnholyWeapon.java:52-262`).** Targets a weapon that carries the caster's `Prayer_UnholyArmament` effect (`:118-128`). The forms are shown below. Two-handed forms get range 0 to 1 (`:253-254`), one-handed forms get 0 to 0 (`:255-256`), and crossbows get range 0 to 5 with one bolt of ammunition (`:245-251`). The weapon's wear is set to held or wielded (`:243`).

| Form | Name given | Damage type | Weapon class | Hands |
|---|---|---|---|---|
| sword / sword2h | unholy sword / two-handed sword | slashing | SWORD | 1 / 2 |
| axe / axe2h | unholy axe / two-handed axe | slashing | AXE | 1 / 2 |
| mace / mace2h | unholy mace / two-handed mace | bashing | BLUNT | 1 / 2 |
| flail | unholy flail | bashing | FLAILED | 1 |
| hammer / hammer2h | unholy hammer / two-handed hammer | bashing | HAMMER | 1 / 2 |
| staff | unholy staff | bashing | STAFF | 2 |
| polearm | unholy polearm | piercing | POLEARM | 2 |
| javelin | unholy javelin | piercing | THROWN | 1 |
| dagger | unholy dagger | piercing | DAGGER | 1 |
| crossbow | unholy crossbow | piercing | RANGED | 2 |

When cast automatically the form is random (`:92`); a player must name one (`:93-107`).

### Cross-system references

- Class mappings place these prayers in several classes: Templar gets `Prayer_UnholyArmament` at level 4 and `Prayer_MorphUnholyWeapon` at level 7 (`com/planet_ink/coffee_mud/CharClasses/Templar.java:127`, `:143`). Purist gets Purify at 2, Monolith at 11 with text ICE and PietyCurse at 17 (`com/planet_ink/coffee_mud/CharClasses/Purist.java:113`, `:148`, `:172`). Missionary gets Monolith at 20 with text AIR (`com/planet_ink/coffee_mud/CharClasses/Missionary.java:168`). Oracle gets Omnipresence at 11 and Prophecy at 16 (`com/planet_ink/coffee_mud/CharClasses/Oracle.java:146`, `:165`). Reliquist gets Planar Pilgrimage at 35 (`com/planet_ink/coffee_mud/CharClasses/Reliquist.java:289`). Cleric gets Planar Travel at 35 (`com/planet_ink/coffee_mud/CharClasses/Cleric.java:315`). Shaman, Oracle and Missionary get Minor Infusion (`com/planet_ink/coffee_mud/CharClasses/Shaman.java:114`, `com/planet_ink/coffee_mud/CharClasses/Oracle.java:134`, `com/planet_ink/coffee_mud/CharClasses/Missionary.java:137`).
- `com/planet_ink/coffee_mud/Abilities/Common/MeatCuring.java:99-105` and `:151` check and apply `Prayer_Purify` to food.
- Engine services used: `CMLib.combat().postDamage` (Monolith, the aura ticks, PietyCurse), `CMLib.quests()` and `CMLib.percolator()` (Pilgrimage, Prophecy's quest scan), `CMLib.commands().forceStandardCommand("Yell")` (Prophecy), `CMLib.factions()` (alignment gate, MoralBalance), `CMLib.leveler().postExperience` (Protect Sacred Item), `CMLib.threads().deleteTick` (Preserve Body), `CMLib.tracking().getRadiantRooms` (Position, Omnipresence), `CMLib.clans()` and channels (Peace Ritual), and `CMLib.database().DBUpdateRoom` (Neutralize Land).

## Content worth mining

**Protection catalogue.** Each row is one prayer. Flags are the alignment flags (`com/planet_ink/coffee_mud/Abilities/interfaces/Ability.java:1014-1030` for LAW, CHAOS, DIVINING; `:950-972` for other flags).

| Prayer | Flags | Effect | Source |
|---|---|---|---|
| Protection Good | UNHOLY | veto holy spells; damage a good bearer; armour bonus vs good | `Prayer_ProtGood.java:74`, `:99-103`, `:120-127`, `:148` |
| Protection Evil | HOLY | veto unholy spells; damage an evil bearer; armour bonus vs evil | `Prayer_ProtEvil.java:74`, `:99-102`, `:120-125`, `:146-147` |
| Protection Law | LAW | veto lawful spells; damage a lawful bearer; armour bonus vs lawful | `Prayer_ProtLaw.java:74`, `:99-104`, `:122-127`, `:148-149` |
| Protection Chaos | LAW | veto chaos spells; damage a chaotic bearer; armour bonus vs chaotic | `Prayer_ProtChaos.java:74`, `:99-103`, `:121-126`, `:147-148` |
| Protection Undead | NEUTRAL | damage undead bearer; armour bonus vs undead | `Prayer_ProtUndead.java:74`, `:100-104`, `:122-125` |
| Protection Outsiders | HOLY | armour bonus vs outsiders | `Prayer_ProtOutsiders.java:74`, `:103` |
| Protection Bless | UNHOLY | repel blessing-domain effects | `Prayer_ProtBless.java:54-66`, `:111-127` |
| Protection Curses | NEUTRAL | +10 undead save; 50% repel curse spells | `Prayer_ProtCurses.java:74`, `:107`, `:121-127` |
| Protection Disease | NEUTRAL | +100 disease save; 50% repel | `Prayer_ProtDisease.java:66`, `:107`, `:123-127` |
| Protection Poison | NEUTRAL | +100 poison save; 50% repel | `Prayer_ProtPoison.java:74`, `:107`, `:123-127` |
| Protection Paralyzation | NEUTRAL | +100 paralysis save; 50% repel | `Prayer_ProtParalyzation.java:74`, `:107`, `:123-127` |
| Protect Health | NEUTRAL | +50 poison save; 50% repel disease tools | `Prayer_ProtectHealth.java:66`, `:107`, `:120-124` |
| Protection Cold | HOLY + HEATING | +100 cold save | `Prayer_ProtCold.java:66-68`, `:107` |
| Protection Fire | UNHOLY | +100 fire save | `Prayer_ProtFire.java:66-68`, `:107` |
| Protect Elements | NEUTRAL | +50 to acid, cold, electric, fire and gas saves | `Prayer_ProtectElements.java:109-113` |
| Protection Death | HOLY | 30 + XLEVEL percent to survive one death | `Prayer_ProtDeath.java:74`, `:102-103` |
| Protected Mount | none | +10 undead save; mount survives at 1 HP | `Prayer_ProtectedMount.java:96`, `:110-118` |
| Preserve Knowledge | HOLY | XP-loss budget; survives death | `Prayer_PreserveKnowledge.java:121-171` |

**Dispel and control.** The Nullification and Neutralize Land rules are compact and clear. Pacify (one MUD month of non-hostility) and Peace Ritual (clan war to hostile) are useful templates for non-combat outcomes.

**Monolith wall table.** See the Monolith section above. Four walls, each a different rule, from one spell.

**Planar templates.** The eight active templates and their weights are listed in the Planar Pilgrimage section. They describe a whole family of procedural tasks (capture, deliver, collect, kill) tuned to a region's character.

**Prophecy starters.** Ten openings, chosen at random: "The visions say that", "I see that", "I feel that", "A voice tells me that", "Someone whispers that", "It is revealed to me that", "In my visions, I see that", "In my mind I hear that", "The spirits tell me that", and "I prophesy that" (`Prayer_Prophecy.java:98-130`).

**Infusion values.** Minor restores `2d(L)+10` mana; Moderate restores `5d(L)+20` (`Prayer_MinorInfusion.java:114`, `Prayer_ModerateInfusion.java:114`). Both restore mana, not hit points.

**Morph table.** The 14 forms in the table above map cleanly to a weapon-data table.

## Notable design ideas

- **Two-phase veto on messages.** Prayers implement `okMessage` to veto and `executeMsg` to react (`Prayer_ProtGood.java:108-132`, `Prayer_PreserveKnowledge.java:121-171`). A protection is therefore a filter on the event stream, not a special case in each spell.
- **Protections are stat deltas plus filters.** Most of the protection family is a save bonus and a message filter. This makes the family cheap to define in data.
- **Alignment as an axis of damage.** The alignment auras damage the bearer in proportion to level, but only if the bearer is on the opposing side. The protection is therefore harmful to the wrong bearer, which makes it a real choice.
- **Level-gated dispel.** `invokerLevel <= casterLevel + 2*XLEVEL` (Nullification `:148`) lets higher-expertise casters remove stronger effects. Curses are exempt from Nullification but not from Neutralize Land.
- **Penalty absorption.** Preserve Knowledge turns an XP loss into a finite budget, followed by a day of vulnerability. This is a concrete "soft death" mechanic.
- **Prophecy is state-aware.** It reads the quest list at the moment the trance ends, its separate prediction path schedules a delayed script through `ScriptLater`.
- **Quest generation from templates.** Planar Pilgrimage picks a task type by weighted, multi-requirement matching against planar metadata. A quest is built from the template's script and its defined ids, then started and shown to the target.
- **Walls as combat terrain.** Monolith's wall items have a hit pool and a per-attack rule. Each variant is a different tactical choice.

## Problems and cruft

1. **Compass trail letters collide.** `Prayer_Position.java:129` writes the first character of the direction name. The default compass list is `North,South,East,West,Up,Down,There,Northeast,Northwest,Southeast,Southwest` (`com/planet_ink/coffee_mud/core/Directions.java:128`), so Northeast and Northwest both encode as "n", and Southeast and Southwest both as "s". The trail cannot tell diagonals apart.
2. **ProtLaw's flag disagrees with its message.** `Prayer_ProtLaw.java:74` returns `FLAG_LAW`, the same as `Prayer_ProtChaos.java:74`. ProtLaw's own veto message describes "the chaotic field around" the bearer (`:127`), and its tick damages lawful creatures, which fits a chaotic aura. The flag looks like a copy-paste error, probably meant to be `FLAG_CHAOS`. The alignment gate treats any flag other than HOLY or UNHOLY as neutral (`Prayer.java:382-401`), so the gate itself is unaffected. Other readers of the flag were not checked.
3. **Two different gates on the same two-message pattern.** `Prayer_Plague.java:110` uses `okMessage(msg) || okMessage(msg2)`, as does `Prayer_PlanarPlague.java:113`. `Prayer_Poison.java:116` uses `&&`. Both messages are then sent regardless of which one was vetoed (`Prayer_Plague.java:111-112`), and the disease is applied when both values are not positive (`:114-116`). With `||`, a vetoed cast message can still deliver the effect if the second message passes.
4. **Unguarded field use in Piety Curse.** `Prayer_PietyCurse.java:151`, `:159`, `:170` and `:174` use the `invoker` field directly, while `:179` uses `invoker()` with a null check. Whether the field can be null in these paths was not traced.
5. **Protect Sacred Item loses XP on fizzle.** The XP is deducted at `Prayer_ProtectSacredItem.java:185`, before the proficiency roll at `:194`. A failed prayer still costs the 500 XP.
6. **A dead call in Protect Sacred Item.** `Prayer_ProtectSacredItem.java:204` calls `Prayer.infusePhysicalByAlignment`, which only adds alignment infusions when the item has no deity infusion (`Prayer.java:224`, `:246`). `checkRequiredInfusion` (`:168`) has already required a deity infusion, so the call does nothing.
7. **Stat hooks with side effects.** Both Protect Sacred Item (`:97-112`) and Protect Item (`:93-108`) change the item's `usesRemaining` inside `affectPhyStats`. The `maintainCondition` starts at 100 (`Prayer_ProtectSacredItem.java:97`) and only rises, so, for a wear-and-tear item, any value below 100 is raised to 100 on the first stat recalculation.
8. **Stale comment.** `Prayer_ProtectItem.java:157` says "lose all the mana!" but the prayer does not override mana cost and charges no XP.
9. **Process-wide cooldown.** Planar Pilgrimage's `lastUsed` is a static set (`:124`) keyed by name, not by character. Setting the `NEXTCAST` stat clears it for every caster (`:1131-1134`). Whether it persists across restarts was not traced.
10. **Substring keyword matching.** Monolith's `indexOf("AIR")` also matches "FAIR" or "CHAIR", and `indexOf("ICE")` matches words such as "PRICE" (`Prayer_Monolith.java:330-340`).
11. **Repeated fizzle.** Nullification's fizzle message (`:165-166`) is attached to the per-inhabitant branch, so a failed cast in a busy room prints once per inhabitant.
12. **Blanket unInvoke on death.** `Prayer_ProtDeath.java:110-115` calls `unInvoke` on every effect the mob has after a save. Its comment says "personal effects". Whether `unInvoke` removes the effect from the list was not traced, so the reach of this loop is unverified.
13. **Plane check on the wrong room.** `Prayer_PlanarPlague.java:107` compares the plane of `target.getStartRoom()`, not the target's current location. The intent is unverified.
14. **Channel spam by design.** `Prayer_PeaceRitual.java:111-113` posts to the clan channels on every call to `tick`, without checking `tickID`.
15. **Room branch of Purify.** The room branch of `Prayer_Purify.java:105-118` runs before the proficiency roll, and `:117` returns true even if the room message was vetoed.
16. **Internal ids in player output.** `Prayer_Position.java:100` returns "You can't get to 'ROOMID'" as the trail, which exposes internal room ids. Lines `:182-183` then still update the last position.
17. **Commented-out logic.** `Prayer_ProtBless.java:104-107` has a commented-out stat change, so the prayer only repels effects.
18. **Typos in player text.** "pilgrimmage" (`Prayer_PlanarPilgrimage.java:1105`), "to not, actually, exist" (`:1092`), "An curse" (`Prayer_ProtCurses.java:127`), "An paralyzing" (`Prayer_ProtParalyzation.java:126`).
19. **Multiplayer assumptions** (see the takeaways for the single-player view): Peace Ritual needs clan members, Planar Pilgrimage needs a second player, Prophecy's prediction targets another player, Moral Balance checks group membership, Piety Curse's social and attack triggers need other mobs present.

## Takeaways for a Python SUD

1. **Veto hooks on events.** Give each effect `ok(event) -> bool` and `on_event(event)`. Events are plain dataclasses with a source, target, kind and tags. A protection that repels disease is then one object with a filter, not a spell rewrite. A single room loop can call `ok` on each effect before applying an event.
2. **Protections as data.** Use a table: name, alignment tag, `stat_bonus` dict, `repel` (kinds, chance), `aura` (opposing tag, damage formula, armour delta). The 19 `Prot*` classes collapse to about 20 table rows and a few hooks.
3. **Derived stats.** Compute a character's stats from base plus all active effects, on demand. CoffeeMud's `affectCharStats` pattern is directly portable, and at single-player scale recomputing on demand is cheap.
4. **One duration function.** Keep both kinds from `StdAbility`: an absolute tick count, and a standard duration `level*4+40` (beneficial) or `round(1.1*level)+25` (malicious). Store durations in game-time units, not ticks, if the SUD is turn-based.
5. **Dispel by level.** Port `invokerLevel <= casterLevel + 2*skill` and the curse exemption. This is a clean rule for any spell system.
6. **Penalty budgets.** Port Preserve Knowledge as "soft death": an XP or item-loss budget, then a vulnerable state for a fixed number of in-game hours. It fits a single-player game well.
7. **Prophecy as a scheduled oracle.** At the end of an undisturbed trance, list the next N scheduled world events (weather, caravans, quest deadlines) sorted by time, with the N formula from XLEVEL. Predictions become future events in a `heapq` queue, the Python version of `ScriptLater`.
8. **Quest templates with weighted matching.** Port the weighting method directly: each template lists requirement tags with weights, a candidate region gets the sum of passed weights or zero, and `random.choices` picks the template. The planes become regions tagged with `CATEGORY` and an alignment band.
9. **Terrain features.** Model Monolith's wall as a room feature with a hit pool and an `on_attack` hook. The four variants are four small classes or one class with an enum.
10. **Drop the multiplayer machinery.** Remove Peace Ritual's clan diplomacy, Planar Pilgrimage's player-to-player generation, Moral Balance's group check, Prophecy's cross-player prediction, and Protected Mount's Dueling exemption. Replace the alignment-faction ranges with a single float per creature.
11. **Keep the alignment gate.** `prayerAlignmentCheck` is a simple probability: succeed if `d100 > basis`, where the basis is the alignment purity (minus 10 for neutral). Porting it as a single function would give the game its alignment flavour without the faction system.
12. **Fix the known bugs when porting.** Use unique direction codes in the trail (full names or distinct letters), use `&&` for two-message gates, and remove the side effect from stat hooks.
13. **Drop from the port.** The XML quest-script percolator, the MPContext scripting for predictions, the morph-to-weapon factory mob, and the planar framework unless the SUD has planes.

## Coverage

- **Read in full** (code bodies read from the first line of code after the licence header, with boilerplate and blank lines removed where noted): `Prayer_PlanarPilgrimage.java`, `Prayer_Prophecy.java`, `Prayer_Monolith.java`, `Prayer_MorphUnholyWeapon.java`, `Prayer_PeaceRitual.java`, `Prayer_PietyCurse.java`, `Prayer_ProtectSacredItem.java`, `Prayer_PreserveKnowledge.java`, `Prayer_ProtGood.java`, `Prayer_ProtectedMount.java`, `Prayer_Position.java`, `Prayer_Pacify.java`, `Prayer_Purify.java`, `Prayer_Nullification.java`, `Prayer_Paralyze.java`. Read with boilerplate removed: `Prayer_Plague.java`, `Prayer_Poison.java`, `Prayer_PlanarPlague.java`, `Prayer_MinorInfusion.java`, `Prayer_ModerateInfusion.java` (also diffed against Minor), `Prayer_Omnipresence.java`, `Prayer_MoralBalance.java`, `Prayer_PreserveBody.java`, `Prayer_Philosophy.java`, `Prayer_NeutralizeLand.java`, `Prayer_PlanarTravel.java`.
- **Read in part** (the mechanic blocks plus grep for the rest): `Prayer_ProtectItem.java` (affect, tick, invoke; its veto checked by grep), `Prayer_ProtDeath.java`, `Prayer_ProtectHealth.java`, `Prayer_ProtDisease.java`, `Prayer_ProtParalyzation.java`, `Prayer_ProtPoison.java`, `Prayer_ProtBless.java`, `Prayer_ProtCurses.java`, `Prayer_ProtOutsiders.java`, `Prayer_ProtUndead.java`, `Prayer_ProtLaw.java`, `Prayer_ProtChaos.java`, `Prayer_ProtEvil.java`.
- **Skimmed by grep** (class, flags, classification, stat or effect, message): `Prayer_ProtectElements.java`, `Prayer_ProtCold.java`, `Prayer_ProtFire.java`.
- **Base class and dependencies read for context:** `Prayer.java` (in full), `com/planet_ink/coffee_mud/Abilities/StdAbility.java` (the duration, expertise and cost helpers, and the beneficial and malicious affect entry points), `com/planet_ink/coffee_mud/Abilities/StdPlanarAbility.java` (named, not read), `com/planet_ink/coffee_mud/Libraries/MUDFight.java` (armour adjustment, `rollToHit`, read only to note that the direction was not traced), `com/planet_ink/coffee_mud/core/Directions.java` (compass names), `com/planet_ink/coffee_mud/Common/interfaces/CMMsg.java` (type codes), `com/planet_ink/coffee_mud/Abilities/interfaces/Ability.java` and `com/planet_ink/coffee_mud/Common/interfaces/PhyStats.java` (flag and stat constants), `com/planet_ink/coffee_mud/Libraries/CMAbleMap.java` (class-mapping signature), `com/planet_ink/coffee_mud/Libraries/CMPlayers.java` (`players()`), `com/planet_ink/coffee_mud/core/collections/LimitedTreeSet.java` (constructor), `com/planet_ink/coffee_mud/Common/DefaultFaction.java` (`fetchRange`, canonical ranges).
- **Skipped:** none of the 42 files was skipped entirely.
