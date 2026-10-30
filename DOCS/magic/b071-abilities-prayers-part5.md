# Prayers, part 5: wards, infusions, imbuing, mass prayers and the Maligned Portal

> Part of the CoffeeMud NEWOVERVIEW. Bucket: `b071` (Abilities/Prayers [part 5/8: Prayer_IceHealing .. Prayer_Mercy]) · 40 files · 8658 lines of Java.

Citations are relative to the Java root `com/planet_ink/coffee_mud/` inside the CoffeeMud folder. So `Abilities/Prayers/X.java` means `com/planet_ink/coffee_mud/Abilities/Prayers/X.java`. Every file in this bucket sits in `Abilities/Prayers/`.

## Summary

This slice is the fifth of eight covering CoffeeMud's divine magic: 40 classes, each a `Prayer` (triggered by `PRAY` or `PR`, `Prayer.java:77`). The prayers form a catalogue of game effects: traps that fire a stored prayer (glyphs), a planar portal with an attackable gate, a burial shelter, a geas that forces a victim to perform social acts, mass curses and mass healing, a marriage ceremony, and a family of "infusions" that let a clergy mark rooms, items, exits and mobs with its deity's alignment. Almost every prayer is a small class with the same skeleton (`ID`, `name`, `classificationCode`, `flags`, `abstractQuality`, `canTargetCode`, `invoke`), so the bucket is best understood as one pattern repeated about forty times with a few real mechanisms worth studying. The most reusable parts are the data-driven alignment and quality flags, the tick-duration formulas, the veto-then-apply event pipeline, and the trap and geas patterns. The infusion and marriage systems depend on other players and need rethinking for a single-player game.

## Key files and types

| File (under `com/planet_ink/coffee_mud/Abilities/Prayers/`) | Role | Lines |
|---|---|---|
| `Prayer_MalignedPortal.java` | Opens a planar portal whose gate can be attacked as a fake mob | 628 |
| `Prayer_ImprovedWardingGlyph.java` | Trap ward on a door (exit); fires a stored prayer | 406 |
| `Prayer_LesserWardingGlyph.java` | Trap ward on a container; fires a stored prayer | 393 |
| `Prayer_InfuseUnholiness.java` | Evil infusion; the template for the Infuse family | 346 |
| `Prayer_InfuseHoliness.java` | Good infusion (same code, different flags and ambiance) | 344 |
| `Prayer_InfuseDiscipline.java` | Lawful infusion | 342 |
| `Prayer_InfuseImpunity.java` | Chaotic infusion | 342 |
| `Prayer_InfuseBalance.java` | Neutral infusion | 335 |
| `Prayer_InfuseModeration.java` | Moderate infusion | 333 |
| `Prayer_MassGrave.java` | Buries the caster and grouped followers in a shelter room | 309 |
| `Prayer_ImbueShield.java` | Base class: imbue a stored prayer into a shield or weapon | 287 |
| `Prayer_Judgement.java` | Geas: victim must perform a social on 10 creatures or die | 263 |
| `Prayer_MassTongues.java` | Curses all hostiles in an area with a random language | 260 |
| `Prayer_JoyousRapture.java` | Forces mood to happy or normal and keeps the mob peaceful | 190 |
| `Prayer_MassCureDisease.java` | Cures disease effects on everyone in a wide radius | 183 |
| `Prayer_LinkedHealth.java` | Splits damage 50/50 between two grouped mobs | 181 |
| `Prayer_Marry.java` | Marries two players (liege IDs, wedding bands, witness) | 181 |
| `Prayer_MassMobility.java` | Aura on each room occupant that blocks hostile immobilising prayers | 179 |
| `Prayer_MassBlindness.java` | Mass curse: CAN_NOT_SEE | 168 |
| `Prayer_MassFreedom.java` | Removes offensive effects from everyone in the room, subject to a caster-level rule | 168 |
| `Prayer_Mercy.java` | Room blessing that zeroes all damage | 166 |
| `Prayer_IceHealing.java` | Converts cold and water damage into healing | 162 |
| `Prayer_MassDeafness.java` | Mass curse: CAN_NOT_HEAR | 158 |
| `Prayer_LowerLaw.java` | Reveals the crimes of the local law | 157 |
| `Prayer_LightHammer.java` | Holy damage against evil targets | 154 |
| `Prayer_IlluminateScroll.java` | Adds uses to a prayer scroll | 151 |
| `Prayer_MassParalyze.java` | Mass curse: CAN_NOT_MOVE for a level-scaled duration | 148 |
| `Prayer_InciteDead.java` | Undead attack a random bystander | 147 |
| `Prayer_Maladiction.java` | Halves XP gains and doubles XP losses | 146 |
| `Prayer_MassForgive.java` | Pardons all outstanding warrants in the area | 145 |
| `Prayer_MassHarm.java` | Damage to every hostile in range, divided among them | 133 |
| `Prayer_ImbueFoulWeapon.java` | Imbue for chaotic weapons | 129 |
| `Prayer_ImbueHolyWeapon.java` | Imbue for holy weapons | 129 |
| `Prayer_ImbueJustWeapon.java` | Imbue for lawful weapons | 129 |
| `Prayer_ImbueModestWeapon.java` | Imbue for moderate weapons | 129 |
| `Prayer_ImbueSacredWeapon.java` | Imbue for neutral weapons | 129 |
| `Prayer_ImbueUnholyWeapon.java` | Imbue for unholy weapons | 129 |
| `Prayer_Invigorate.java` | Restores movement and clears fatigue | 127 |
| `Prayer_MassHeal.java` | Heals every group member in the room | 127 |
| `Prayer_MajorInfusion.java` | Restores mana | 125 |

Base classes outside this bucket that these files depend on: `Abilities/Prayers/Prayer.java` (432 lines), `Abilities/StdAbility.java` (tick and duration helpers, `copyOf`, `unInvoke`), `Libraries/MUDLaw.java` (`getClericInfusion`, `getClericInfused`), `Libraries/MUDFight.java` (`properTargets`), `Libraries/Dice.java` (`roll`), `MOBS/StdDeity.java` (worship services).

## How it works

### Shared base: `Prayer`

`Prayer` (`Abilities/Prayers/Prayer.java:37`) is a `StdAbility` with defaults `canTargetCode` = `CAN_MOBS` (`Prayer.java:66-69`), `abstractQuality` = `QUALITY_INDIFFERENT` (`:71-75`), `classificationCode` = `ACODE_PRAYER` (`:85-88`) and triggers `PRAY`, `PR` (`:77`). Subclasses set a domain (for example `DOMAIN_CURSING`) and one or more alignment flags in `flags()`.

**Alignment gate.** `Prayer.invoke` runs the normal `StdAbility.invoke` and then `prayerAlignmentCheck` (`Prayer.java:422-430`). The check applies only to a non-auto cast by a player who owns the prayer, when the prayer is not appropriate to the caster's factions and has flags (`:374-379`). It maps the flags to a bucket: `FLAG_HOLY` (not unholy) gives 1000, `FLAG_UNHOLY` gives 0, anything else gives 500 (`:381-389`). It then takes the caster's purity toward the matching alignment as the basis. Purity toward evil for unholy, toward good for holy, and neutral purity minus 10 for the rest (`:391-401`). The cast succeeds if `rollPercentage() > basis` (`:403-404`). Failure messages are "The evil nature of ... disrupts your prayer." and "The goodness of ... disrupts your prayer." (`:407-410`), with variants for neutral casters (`:412-416`). Each cast is a purity roll, so the failure chance falls as the caster's purity toward the prayer's alignment rises.

**Infusion helpers.** `Prayer.java:185-206` provides `checkInfusionMismatch` (a mob may not touch an item infused by a different deity) and `checkRequiredInfusion` (the item must carry the caster's own deity's infusion). `infusePhysicalByAlignment` (`:208-255`) chooses one infuser on the good/evil/neutral axis and one on the lawful/chaotic/moderate axis from the caster's alignment, and attaches it only if no infusion exists and the caster has a worship ID. Infuse prayers are called from elsewhere (for example `Prayer_EmpowerRelic`, `Prayer_EnchantRelic`, `Prayer_ProtectSacredItem`, `Prayer_StorePrayer` and `Prayer_EmpowerShield`; these are outside this bucket). `infuseMobAlignment` (`:257-292`) adds a `Prop_WearZapper` mask such as `+FACTION -EVIL -NEUTRAL` to the target. `clearInfusions` (`:294-305`) removes all infusions, and the relic helpers (`:307-370`) read or clear the charges of a "relic" that holds a prayer.

**Durations.** The tick is `TIME_TICK = 4000` ms (`core/CMProps.java:681`). Two formulas in `StdAbility` set most ability durations, in ticks:

- Beneficial standard: `casterLevel*4 + 40` (`Abilities/StdAbility.java:2312-2330`). When a caster who may abuse the system buffs another mob, the level is capped at the target's level plus `EXPRATE`.
- Malicious standard: `round(adjustedLevel*1.1) + 25` (`Abilities/StdAbility.java:2174-2183`).

The adjustment argument behaves the same way for both: a positive value is used as the absolute tick count, zero means the standard, and a negative value is a percentage of the standard (`:2176-2181` and `:2329`). Many prayers pass a bare constant (for example `9999`, `999999`, or `TICKSPERMUDMONTH`) to use an absolute duration.

**Event pipeline.** Prayers act through `okMessage` (veto) and `executeMsg` (effect), as the brief describes. Examples are given in each section below.

### Wards: Lesser and Improved Warding Glyph

Both glyphs are `Trap` implementations that store a prayer and fire it when triggered.

- **Casting.** The caster names a target and then a prayer with its parameters. `Prayer_LesserWardingGlyph.java:332-336` requires at least two tokens. The target must be a container with capacity (`:342-347`). The prayer is resolved with `CMLib.english().getSkillToInvoke` after `PRAY` is inserted at the front of the command list (`:349-358`). The Improved glyph (`Prayer_ImprovedWardingGlyph.java:341-360`) instead targets an exit that has a door.
- **Trigger.** The Lesser glyph springs on `OPEN`, `UNLOCK` or `GET` by a non-invoker (`Prayer_LesserWardingGlyph.java:197-204` and `:296-299`). The Improved glyph springs on the same three in `okMessage` (`Prayer_ImprovedWardingGlyph.java:205-217`) and additionally on `LEAVE` in `executeMsg` (`:304-309`).
- **Effect.** `spring` makes a save roll against `STAT_SAVE_TRAPS` (`Prayer_LesserWardingGlyph.java:254-255`). On failure it builds a factory caster named "the thin air" at the invoker's level plus `XLEVEL`, or level 10 with no invoker (`:258-266`). It then calls `shooter.invoke(newCaster, parameters, mob, true, 0)` (`:273`) and sets `sprung` and unInvokes (`:282-283`).
- **Duration.** `beneficialAffect(mob, target, asLevel, 9999)` (`Prayer_LesserWardingGlyph.java:382`, and `:395` in the Improved glyph). That is 9999 ticks, about 11 hours at 4 seconds per tick, unless the glyph springs first.
- **Owner.** `theInvoker` recovers the caster from the effect's misc text, which holds the caster's name after a successful cast (`:381`, `:99-102`). The invoker is recovered from that name when the live reference is missing.
- **Exemption.** `isLocalExempt` (`Prayer_LesserWardingGlyph.java:209-228`) exempts landowners and same-area monsters, but only when `!canBeUninvoked()`. A normal glyph is created by `beneficialAffect`, which sets `canBeUninvoked = true` (`Abilities/StdAbility.java:2345`), so the exemption does not apply to a cast glyph.

### Infusions: the Infuse family

Six prayers share one design. `Prayer_InfuseUnholiness` (`Prayer_InfuseUnholiness.java:35`) implements `Deity.DeityWorshipper`, so the infusion is an effect whose `getWorshipCharID()` is the deity name stored in its misc text (`:109-135`). Each of the six differs from the template only in flags, ambiance and messages, plus the missing combat override in Balance and Moderation.

| Prayer | Flag (`flags()` line) | `affectPhyStats` (line) |
|---|---|---|
| InfuseHoliness | `FLAG_HOLY` (68) | adds `IS_GOOD` and `SEEMS_GOOD`, removes `IS_EVIL` (185-188) |
| InfuseUnholiness | `FLAG_UNHOLY` (68) | adds `IS_EVIL` and `SEEMS_EVIL`, removes `IS_GOOD` (186-189) |
| InfuseDiscipline | `FLAG_LAW` (68) | adds `SEEMS_LAWFUL` (185) |
| InfuseImpunity | `FLAG_CHAOS` (68) | adds `SEEMS_CHAOTIC` (185) |
| InfuseBalance | `FLAG_NEUTRAL` (68) | removes `IS_GOOD` and `IS_EVIL`, adds `SEEMS_GOOD` and `SEEMS_EVIL` (185-190) |
| InfuseModeration | `FLAG_MODERATE` (68) | removes `SEEMS_LAWFUL` and `SEEMS_CHAOTIC`, adds `SEEMS_MODERATE` (185-187) |

All six have domain `DOMAIN_EVANGELISM`, quality `QUALITY_INDIFFERENT`, and targets mobs, items, rooms and exits (`CAN_MOBS|CAN_ITEMS|CAN_ROOMS|CAN_EXITS`, Unholiness lines 78-87).

- **Cast.** The word `room` or `here` targets the room (`Prayer_InfuseUnholiness.java:275-277`). A target that already has an infusion is refused (`:284-294`). A faithless caster with no worship ID cannot infuse a room (`:296-302`).
- **Persistence.** For a room the caster owns (`CMLib.law().doesOwnThisLand`), the infusion is added as a non-uninvokable effect and the room is saved with `DBUpdateRoom` (`:330-334`). Otherwise it is a standard beneficial effect (`:337`), which expires.
- **Holy places.** `setAffectedOne` and `setMiscText` register or deregister the infused place with the city library (`:155-179`). `finalize` deregisters it as well (`:145-153`).
- **Service lock.** `serviceRunning` is set by the deity during a worship service (`MOBS/StdDeity.java:882` calls `setAbilityCode(1)`). While it is nonzero, `okMessage` refuses malicious messages between two members of the same deity (`:213-223`) and blocks monsters from leaving the room (`:224-228`).
- **Devotion bonus.** When a worshipper of the infuser's deity casts a prayer on a mob in the infused room, and the mob does not already hold that prayer, the new effect's remaining time is scheduled to grow by 50% one second later (`:236-257`, the formula is at `:254`). That is a quiet reward for devotion.
- **Combat.** `castingQuality` returns `QUALITY_INDIFFERENT` for a caster in combat (`:261-268`). Balance and Moderation do not override it, so they can be cast in combat, which is inconsistent with the other four.

The alignment of the caster chooses the infuser: good chooses Holiness on the good axis and Discipline on the lawful axis. Evil chooses Unholiness and Impunity. Neutral chooses Balance and Moderation (see `Prayer.java:208-255`).

### Imbuing: `Prayer_ImbueShield` and the six weapon prayers

`Prayer_ImbueShield` (`Prayer_ImbueShield.java:37`) imbues a stored prayer into an item. Its checks and costs in `invoke` (`:129-285`) are:

- The target must be an item the caster can see (`:136-141`). The caster must worship a deity (`:142-147`). The item must be an appropriate type (`:149-153`).
- The item must not be repulsive by infusion mismatch, and it must carry the caster's infusion (`:154-164`).
- If the item has a zapper infusion, the caster must pass its mask (`:166-175`).
- The item must have a nonzero `phyStats().ability()` (`:180-184`).
- The prayer is found among the caster's known prayers (`:189-208`), must pass `isOkPrayer` (`:210-214`), and its neutral and moderate bits must match the imbuer's bits unless the target is a shield (`:215-223`). A sacred weapon therefore takes only prayers carrying `FLAG_NEUTRAL`, a modest weapon only prayers carrying `FLAG_MODERATE`, and a holy weapon only prayers carrying neither bit.
- The prayer must allow enchantment, the item must be generic, and it must not already be "sufficiently magical" (`:225-240`).
- The caster loses experience of `1000 + 100 * lowestQualifyingLevel(prayer)` (`:243`, `getXPCost` at `:106-111`). The loss is charged on both success and failure (`:258-261` and `:278-281`).
- On success, `setMiscText(prayer.ID())` is kept "for reliquist discharge" (`:267`). `doImbue` runs (`:272`).

`doImbue` in the base class (`:84-93`) adds `IS_BONUS`, raises the item level by `lowestQualifyingLevel / 2`, and attaches `Prop_WearSpellCast` with `LAYERED;<prayer>;` (`:86-91`). Weapons override it and attach `Prop_FightSpellCast` with `25%;MAXTICKS=12;<prayer>;` instead, and they set `IS_BONUS` only inside an unbraced `if (flags() & FLAG_UNHOLY)` (see Problems).

Each weapon prayer overrides `isOkPrayer`, which refuses `Spell_Stoneskin`, `Spell_MirrorImage`, summoning prayers, non-malicious prayers, and prayers that cannot target mobs (`Prayer_ImbueHolyWeapon.java:77-89`, the same rule in the other five). They also override `checkAlignment` to refuse a target of the opposite alignment:

| Prayer | Flag | Refuses target that is | Message on success |
|---|---|---|---|
| Imbue Holy Weapon | `FLAG_HOLY` | evil (`:121`) | "glow(s) divinely!" |
| Imbue Unholy Weapon | `FLAG_UNHOLY` | good | "glow(s) wickedly!" |
| Imbue Foul Weapon | `FLAG_CHAOS` | lawful | "glow(s) chaotically bright!" |
| Imbue Just Weapon | `FLAG_LAW` | chaotic | "glow(s) justly bright!" |
| Imbue Modest Weapon | `FLAG_MODERATE` | lawful or chaotic | "glow(s) modestly bright!" |
| Imbue Sacred Weapon | `FLAG_NEUTRAL` | evil or good | "glow(s) with a sacred light!" |

The result is that a holy weapon carries a malicious prayer (`isOkPrayer` requires `QUALITY_MALICIOUS`). The weapon receives a `Prop_FightSpellCast` with the parameters `25%;MAXTICKS=12;<prayer>;`. What those parameters do in the effect class is not verified here.

### Maligned Portal (planar gate)

`Prayer_MalignedPortal` (`Prayer_MalignedPortal.java:37`) is the most involved prayer in the bucket. It is `FLAG_UNHOLY` with `DOMAIN_COSMOLOGY` and override mana `COST_ALL-90` (`:63-77`).

- **Casting time.** `castingTime` and `combatCastingTime` return `CMProps.getSkillCombatActionCost(ID(), 3000.0) / 100`, which is 30 by default (`:358-367`). The unit is not verified.
- **Preconditions.** The caster must not be in combat (`:378-385`), must be standing and free (`:386-402`), and must be in the same room on subsequent seconds (`:386-387`). Each second the caster is still casting, the portal's "continue" message is sent every eight seconds (`:440-443`).
- **Plane selection.** The planes come from `StdPlanarAbility`. The caster's category, `getCategory()` returning `"lower"` (`:352-355`), must appear in the plane's category list. Planes with an alignment below -7500 are preferred (`:419-425`, `:479-481`). The inclination faction then narrows the list to planes whose faction range matches the caster's (`:493-534`). The choice is random (`:536`). Casting an unholy prayer shifts the caster's alignment faction by -100, and a holy one by +100 (`:457-463`).
- **Generated room.** `planeAble.invoke` creates the plane (`:558`) with level `min(area MIN_LEVEL, caster level + XLEVEL)` (`:551-553`). The portal is two `GenPortal` exits, one in each room, with `Prop_RoomView` pointing at the other (`:578-600`). The exits are placed in the two rooms (`:599-600`).
- **Gate.** The portal effect (a `Prayer_MalignedPortal`) is attached to the origin exit `e` and holds a factory mob, `fakeTarget`, that stands in as the gate (`:601-618`). Its hit points are the caster's maximum plus `10 * XLEVEL` (`:606-610`), and it has `Prop_WeaponImmunity` with `+ALL -MAGIC` (`:611-615`).
- **Siege.** `okMessage` intercepts `COMMANDFAIL` messages whose text is `ATTACK` or `KILL` followed by the portal's name (`:137-151`). The attacker needs a wielded weapon (`:165-168`), must not be in combat, and must have at least 5 movement points (`:158-162`). Fewer than one action only prints "You can't do that yet." (`:156-157`) and does not stop the attack. The fake mob is added to the room, the attack is posted, one action is spent (`:182-183`), and the portal closes when the fake mob dies (`:184-186`).
- **Monsters.** `tick` (`:242-347`) makes plane-native monsters in the plane room `ENTER` the portal (`:265-276`). Each gets a `WanderHomeLater` effect with a window equal to the portal's remaining ticks (`:288-292`), and `fixPlanarMob` gives it an `Aggressive` or `MobileAggressive` behaviour that is aggressive to everyone except the invoker (`:212-232`, `:293`). It may also befriend the invoker through `Skill_Track` (`:284-287`). On later ticks up to four plane monsters are walked around the zone (`:322-346`).
- **Close.** `unInvoke` destroys the room items, the fake mob and the plane, and posts "The swirling portal closes." (`:94-130`).

### Mass Grave (burial shelter)

`Prayer_MassGrave` (`Prayer_MassGrave.java:40`) buries the caster and grouped followers in a shelter room (`properTargets` gives group members in the room for a non-malicious prayer, `Libraries/MUDFight.java:163-170`). It is unholy and indoor or natural-terrain only (`:247-262`).

- **Shelter.** `buildShelter` makes a `CaveRoom` with the text "An underground grave" and the atmosphere `RESOURCE_DIRT` (`:126-153`). It carries `Prop_PeaceMaker`, `Prop_NoRecall`, `Prop_NoSummon`, `Prop_NoTeleport` and `Prop_NoTeleportOut` (`:135-149`).
- **Toggle.** Casting the prayer again while buried unInvokes the effect and fails (`:241-245`).
- **Duration.** The caster gets `beneficialAffect(..., 999999)` (`:298-299`), so the grave lasts until it is removed (about 46 days if left alone).
- **Safety.** `okMessage` returns everyone to the surface on `QUIT`, `SHUTDOWN`, `DEATH`, `ROOMRESET` or the shelter expiring (`:216-231`).
- **Exit.** `unInvoke` moves the inhabitants back to their previous room and moves the loose items out (`:156-211`). The previous room is stored in misc text (`:279`), and `getPreviousLocation` falls back to a random accessible room if it is gone (`:97-115`).

### Judgement (geas)

`Prayer_Judgement` (`Prayer_Judgement.java`) is a malicious curse with flag `FLAG_HOLY` (`:71-75`) and `COST_ALL` mana (`:89-93`). A good target is immune (`:218`). On a successful, unresisted cast, the victim gets `maliciousAffect(..., 0, -1)` (`:226`). The geas is written as misc text `NUM=<n> SOCIAL="..."` (`:247`). The victim is told to do the social on at least `n` unique creatures or die (`:248`). `n` is `10 + X1 + XLEVEL` (`:229`). The social is picked at random from 47 friendly socials in `FRIENDLY_SOCIALS` (`:100-104`).

- **Monsters.** `tick` makes a monster victim panic when in combat (`:147-148`), or otherwise moves it about. When it is not in combat and can move, it performs the social on an unmet creature in its room (`:153-180`). It uses the fixed social `"PRAY <T-NAME>"` (`:95`, `:175`).
- **Players.** `executeMsg` counts a social only if its name equals the fixed `"PRAY <T-NAME>"` and the target is new (`:191-203`). It then reports progress with a trailer, "That`s n/10" (`:202`).
- **Result.** `unInvoke` prints "survived the judgement" if the count was reached (`:117-118`). Otherwise, when `tickDown < 2`, it prints "consumed by ... judgement" and posts a death (`:119-124`).

### Mass Tongues (language curse)

`Prayer_MassTongues` (`Prayer_MassTongues.java:40`) is a malicious unholy curse on every hostile creature in a radius of `1 + XLEVEL/2` area rooms (`:179-183`). The enemy set comes from `allPossibleCombatants` (`:184-207`). The caster temporarily moves into each room to make the calculation work (`:188-199`).

- **Language choice.** `getLang` picks a random language ability from every `Language` ability, excluding `Std*` and `GenL*` (`:109-124`). It creates the language at proficiency 100 and marks it as spoken (`:131-133`).
- **Effect.** The language's `okMessage` and `executeMsg` run on every message the cursed mob sources (`:139-165`). What the language classes do with the message is outside this bucket. The duration is the standard malicious duration times 50 (`:239`), and the effect is put at the front of the effect list so it beats other languages (`:245`).

### Curses and debuffs

- **Maladiction** (`Prayer_Maladiction.java:103-116`). Any `EXPCHANGE` or `RPXPCHANGE` from the target is doubled if negative and halved if positive. The duration is `CMProps.Int.TICKSPERMUDMONTH` ticks (`:137`). The value is not verified.
- **Mass Paralyze** (`Prayer_MassParalyze.java:77-87`, `:103-147`). Sets `CAN_NOT_MOVE`, with `FLAG_PARALYZING` set. The duration is `8 - levelDiff` ticks, where `levelDiff` is the target's level minus `caster level + 2*XLEVEL`, clamped to 0 to 6 (`:125-134`). The result is 2 to 8 ticks.
- **Mass Blindness** (`Prayer_MassBlindness.java:86`, `:104-120`, `:144-154`). Sets `CAN_NOT_SEE`. Applies only to targets with eyes (`BODY_EYE`, `:144`) and uses the standard malicious duration. `castingQuality` returns `QUALITY_INDIFFERENT` in combat when the room is dark, the target has no eyes, or the target cannot see (`:104-120`).
- **Mass Deafness** (`Prayer_MassDeafness.java:86`, `:90-100`, `:144-145`). Sets `CAN_NOT_HEAR`, with the standard malicious duration.
- **Incite Dead** (`Prayer_InciteDead.java:83-146`). Only undead targets are considered (`:90-101`). For each, it finds a random inhabitant who is not the caster, the target or a group friend, and sets the undead's victim to that inhabitant (`:117-137`). It tries 20 times per target.
- **Joyous Rapture** (`Prayer_JoyousRapture.java:109-142`). Each tick it forces the mood effect: `NORMAL` removes it, and any other value sets it. It also calls `makePeace(true)` (`:136-139`). The mood map (`:89-91`) sends `SAD`, `ANGRY`, `RUDE`, `MEAN` and `GRUMPY` to `NORMAL`. It sends `FORMAL`, `POLITE`, `HAPPY`, `EXCITED`, `SCARED` and `LONELY` to `HAPPY`, and an unknown mood defaults to `HAPPY`. The cast uses the standard beneficial duration and picks its mood from the target's current one (`:165-179`).
- **Linked Health** (`Prayer_LinkedHealth.java:112-132`). In `okMessage`, a damage message to the holder is halved: the holder keeps the half, and the linked buddy is damaged by the same half with `postDamage` (`:126-128`). Each side holds the other's effect through the `buddy` field, which is set before each `beneficialAffect` (`:169-172`). The prayer requires group membership (`:151-155`).

### Mass curses and damage

- **Mass Harm** (`Prayer_MassHarm.java:84-132`). Targets are `properTargets` (malicious, so all hostile combatants). Each takes `1d((adjustedLevel + 24) / N) + 8` damage, where `N` is the number of targets (`:117`). Any resistance in either message stops the damage (`:115`). Undead are not flagged malicious and take `TYP_UNDEAD` damage messages (`:105-110`).
- **Light Hammer** (`Prayer_LightHammer.java:108-153`). Only evil targets are affected (`:121-122`). Damage is `2d(L+5) + L` (`:135`), halved if either message resists (`:136-137`), doubled against undead (`:138-139`). The weapon name in the message is taken from a random weapon of the caster's deity (`:89-106`), falling back to "a hammer".
- **Mass Paralyze, Blindness and Deafness** each attach an effect to every hostile in range, as described above.

### Healing and restoration

- **Ice Healing** (`Prayer_IceHealing.java:103-124`). A cold or water damage message to the bearer is cancelled and replaced by healing of half the value, with the message "The icy attack heals <S-NAME> @x1 points." (`:113-122`). It takes no target word (`canTargetCode` is 0, `:65-69`). The effect is standard beneficial (`:153`).
- **Mass Heal** (`Prayer_MassHeal.java:92-126`). Targets are `properTargets` (non-malicious, so group members in the room, `Libraries/MUDFight.java:163-170`). Each heals `L d5 + L` (`:115`). Undead targets get the message flagged malicious, but the healing still posts (`:109-116`).
- **Invigorate** (`Prayer_Invigorate.java:97-126`). Sets fatigue to zero and adds `10 d L + 50` movement (`:115-117`). Its minimum cast wait is half a tick (`:70-73`).
- **Major Infusion** (`Prayer_MajorInfusion.java:96-124`). The same shape for mana: `10 d L + 50` (`:114-115`).
- **Mass Cure Disease** (`Prayer_MassCureDisease.java:116-182`). Walks passable rooms within `100 + XLEVEL + 2*XMAXRANGE` (`:133`), which is a large radius. Every disease affect is removed unless it is a `DiseaseAffect` invoked by someone else with `difficultyLevel*10 > adjustedLevel + abilityCode` (`:154-156`). `abilityCode` is a bonus set by `setAbilityCode` (`:69-76`). The only caller in this tree, `MOBS/StdDeity.java:882`, sets it on a room's infusion during a worship service, not on this prayer, so here the bonus is normally 0 (other paths unverified).
- **Mass Freedom** (`Prayer_MassFreedom.java:82-167`). For each creature in the room, an effect is a candidate for removal when it would stop a dummy mob from acting, has `FLAG_BINDING`, or fails a sit probe (`:98-100`). It is removed only if its invoker is missing, or is not a spell, chant, prayer or song, or the invoker's level is at most `caster level + 1 + 2*XLEVEL` (`:101-105`). Effects from higher-level casters survive.
- **Mass Forgive** (`Prayer_MassForgive.java:87-145`). Pardons every warrant that is still a crime, sets its crime to `pardoned` and its offences to 0 (`:118-133`), and reports the names.
- **Mercy** (`Prayer_Mercy.java:104-126`). A room effect that sets every positive damage message in the room to zero, in both `okMessage` and `executeMsg` (`:109-113`, `:121-125`). It is permanent and saved for owned land (`:151-155`).
- **Illuminate Scroll** (`Prayer_IlluminateScroll.java:90-150`). Refuses a scroll with no prayer spells (`:110-125`) and refuses a scroll whose uses already exceed its spell count (`:104-108`). It adds `5 + XLEVEL` uses (`:141`) and skips the usual alignment check (`:82-88`).
- **Lower Law** (`Prayer_LowerLaw.java:91-156`). Reads the local law and reports the crime names that apply, from fixed codes (`TRESPASSING`, `ASSAULT` and so on, `:118-145`).

### Marriage

`Prayer_Marry` (`Prayer_Marry.java:69-180`) needs two named people in the room, both visible (`:79-90`). It swaps them if the "wife" has male reproductive code `M` (`:91-96`). Neither may already be married or lieged (`:97-116`). Both must be player characters (`:117-126`). Both must wear an item named "wedding band" (`:128-139`), and a third person must be present as a witness (`:140-154`). On success it sets each one's liege ID to the other's name (`:167-168`), posts a "You may kiss your bride!" say (`:170`), and announces the marriage on the marriages channel (`:171-173`).

## Content worth mining

The catalogue of all 40 prayers, with the core of each mechanic. Quality names drop the `QUALITY_` prefix. Flags are the alignment and behaviour bits from `flags()`.

| Prayer | Domain | Flags | Quality | Target | Effect and key numbers |
|---|---|---|---|---|---|
| Ice Healing | HEALING | HOLY, HEALINGMAGIC | BENEFICIAL_SELF | self | Cold or water damage heals half its value instead |
| Illuminate Scroll | BLESSING | NEUTRAL | INDIFFERENT | scroll | +5 + XLEVEL uses; 50 mana |
| Lesser Warding Glyph | WARDING | NEUTRAL | MALICIOUS | container | Fires a stored prayer on open, unlock or get; 9999 ticks |
| Improved Warding Glyph | WARDING | NEUTRAL | MALICIOUS | door | As Lesser, plus fires on leave |
| Maligned Portal | COSMOLOGY | UNHOLY | INDIFFERENT | room | Generated planar room pair; attackable gate |
| Infuse Holiness | EVANGELISM | HOLY | INDIFFERENT | any | Good infusion |
| Infuse Unholiness | EVANGELISM | UNHOLY | INDIFFERENT | any | Evil infusion; service lock; devotion bonus |
| Infuse Discipline | EVANGELISM | LAW | INDIFFERENT | any | Lawful infusion |
| Infuse Impunity | EVANGELISM | CHAOS | INDIFFERENT | any | Chaotic infusion |
| Infuse Balance | EVANGELISM | NEUTRAL | INDIFFERENT | any | Neutral infusion |
| Infuse Moderation | EVANGELISM | MODERATE | INDIFFERENT | any | Moderate infusion |
| Imbue Shield | BLESSING | NOORDERING, NEUTRAL | INDIFFERENT | shield | XP cost 1000 + 100 x level; wear-cast prayer |
| Imbue Holy Weapon | BLESSING | NOORDERING, HOLY | INDIFFERENT | weapon | Refuses evil; fight-cast effect with `25%;MAXTICKS=12` |
| Imbue Unholy Weapon | BLESSING | NOORDERING, UNHOLY | INDIFFERENT | weapon | Refuses good |
| Imbue Foul Weapon | BLESSING | NOORDERING, CHAOS | INDIFFERENT | weapon | Refuses lawful |
| Imbue Just Weapon | BLESSING | NOORDERING, LAW | INDIFFERENT | weapon | Refuses chaotic |
| Imbue Modest Weapon | BLESSING | NOORDERING, MODERATE | INDIFFERENT | weapon | Refuses lawful or chaotic |
| Imbue Sacred Weapon | BLESSING | NOORDERING, NEUTRAL | INDIFFERENT | weapon | Refuses evil or good |
| Incite Dead | DEATHLORE | UNHOLY | MALICIOUS | undead | Undead attack a random bystander |
| Invigorate | RESTORATION | HOLY | BENEFICIAL_OTHERS | mob | 10dL+50 movement; clears fatigue |
| Judgement | CURSING | HOLY | MALICIOUS | non-good mob | Geas: 10 unique socials or death |
| Joyous Rapture | CURSING | HOLY | OK_OTHERS | mob | Mood forced to HAPPY or NORMAL; peaceful |
| Light Hammer | VEXING | HOLY | MALICIOUS | evil mob | 2d(L+5)+L; x2 vs undead |
| Linked Health | HEALING | NEUTRAL | OK_OTHERS | group member | Damage split 50/50 |
| Lower Law | COMMUNING | HOLY, DIVINING | INDIFFERENT | room | Lists the local crimes |
| Maladiction | CURSING | UNHOLY | MALICIOUS | mob | XP gains halved, losses doubled; TICKSPERMUDMONTH |
| Marry | BLESSING | HOLY | OK_OTHERS | two players | Liege-ID marriage; witness; wedding bands |
| Mercy | WARDING | HOLY | INDIFFERENT | room | Zeroes damage in the room |
| Major Infusion | HEALING | NEUTRAL | BENEFICIAL_OTHERS | mob | 10dL+50 mana |
| Mass Blindness | CORRUPTION | UNHOLY | MALICIOUS | hostile in range | CAN_NOT_SEE; standard duration |
| Mass Deafness | CORRUPTION | UNHOLY | MALICIOUS | hostile in range | CAN_NOT_HEAR; standard duration |
| Mass Paralyze | CORRUPTION | UNHOLY, PARALYZING | MALICIOUS | hostile in range | CAN_NOT_MOVE; 2 to 8 ticks |
| Mass Cure Disease | RESTORATION | HOLY | OK_OTHERS | everyone in range | Removes disease affects; radius 100+ rooms |
| Mass Freedom | RESTORATION | HOLY | OK_OTHERS | room | Removes offensive effects, unless the invoker is a higher-level spell, chant, prayer or song caster |
| Mass Forgive | EVANGELISM | HOLY | OK_OTHERS | area | Pardons all warrants |
| Mass Grave | DEATHLORE | UNHOLY | INDIFFERENT | room | Burial shelter with no-recall and no-teleport |
| Mass Harm | VEXING | UNHOLY | MALICIOUS | hostile | 1d((L+24)/N)+8 each |
| Mass Heal | HEALING | HOLY, HEALINGMAGIC | BENEFICIAL_OTHERS | group | L d5 + L healing |
| Mass Mobility | RESTORATION | NEUTRAL | BENEFICIAL_OTHERS | room occupants | Blocks hostile immobilising prayers; +100 paralysis save |
| Mass Tongues | CURSING | UNHOLY | MALICIOUS | hostile in range | Random language on each; 50 x standard duration |

Concrete content for a text game to reuse:

- **Judgement socials.** The 47 friendly social names in `FRIENDLY_SOCIALS` (`Prayer_Judgement.java:100-104`) are a ready-made list of harmless gestures for a geas. Examples are `HUG`, `BOW`, `PAT`, `SMILE`, `THANK`, `TOAST` and `CHEER`.
- **Shelter and portal rooms.** `Prayer_MassGrave.java:126-153` and `Prayer_MalignedPortal.java:578-600` show how to build temporary rooms and exits in code, with restriction properties.
- **Messages.** Short and reusable: "The swirling portal closes." (`Prayer_MalignedPortal.java:119`), "The blessing of mercy fades." (`Prayer_Mercy.java:99`), "Your health is no longer linked with @x1." (`Prayer_LinkedHealth.java:104`), "Your joyous rapture is lifted." (`Prayer_JoyousRapture.java:99`), and "The paralysis eases out of your muscles." (`Prayer_MassParalyze.java:100`).
- **Glyph words.** "A ward trap has already been set on @x1!" and "A divine ward" (`Prayer_LesserWardingGlyph.java:363`, `:377`).

## Notable design ideas

- **Alignment as data.** Each prayer carries a holy or unholy flag and an alignment bucket, and the caster's purity decides success through one roll (`Prayer.java:372-420`). A conflicted priest fails more often, which gives moral texture without any separate morality system.
- **Six-axis alignment.** Good, evil and neutral combine with lawful, chaotic and moderate into six infusions and six imbue prayers (`Prayer.java:208-255`). Each infusion is the same code with different flags and ambiance (`SEEMS_GOOD`, `SEEMS_LAWFUL` and so on).
- **Infusion as a durable mark.** An infusion is an effect on a room, item, exit or mob, carrying a deity name. It registers holy places and can be saved to a room the caster owns (`Prayer_InfuseUnholiness.java:145-179`, `:330-334`).
- **Stored prayer as a trap.** A glyph stores the whole command line and casts it through a factory caster when triggered (`Prayer_LesserWardingGlyph.java:258-273`). The trap is a prayer, so its strength follows the caster's level.
- **Geas with a countdown.** Judgement forces the victim to act out a social, which is tracked by counting unique targets (`Prayer_Judgement.java:199-204`). Monsters perform it on their own (`:153-180`).
- **Language curse.** Attaching a random `Language` to a target's messages changes what they say through hooks the engine already has, with no new code paths (`Prayer_MassTongues.java:109-165`).
- **Devotion bonus.** A prayer cast by a worshipper of the room's deity on a mob in the infused room lasts 50% longer, provided the mob does not already hold it (`Prayer_InfuseUnholiness.java:236-257`).
- **Planar gate with a fake mob.** The portal effect holds a fake mob with hit points, and the gate is attacked as if it were a monster. Destroying it closes the portal (`Prayer_MalignedPortal.java:137-196`).
- **Aura that tests spells.** Mass Mobility checks an incoming hostile spell by applying its `affectPhyStats` and `okMessage` to a dummy mob, and it blocks spells that would immobilise (`Prayer_MassMobility.java:77-112`). This is an elegant use of the event pipeline.

## Problems and cruft

Bugs confirmed by reading. Each is a direct reading of the code and was not run.

1. **The room-infusion conflict check never fires.** `Prayer_InfuseUnholiness.java:304-314` loops over every room in the metro map but tests `getClericInfused(target)` instead of `R`. The earlier check (`:284-294`) has already returned when the target has an infusion, so the test compares against null. The same loop appears in the other five infusers (`Prayer_InfuseHoliness.java:307`, `Prayer_InfuseDiscipline.java:305`, `Prayer_InfuseImpunity.java:305`, `Prayer_InfuseBalance.java:298`, `Prayer_InfuseModeration.java:296`). The intended message "There is already an unholy place of ... in this area" can never print.
2. **Judgement counts only the prayer social, and the victim is told another.** The victim is told to perform the random `socialBaseName` (`Prayer_Judgement.java:230-248`), but `executeMsg` and `tick` use the fixed `"PRAY <T-NAME>"` (`:95`, `:175`, `:194`). Only a PRAY social counts, and the random social stored in misc text is never read. The victim may be told "cuddle" while the counter ignores it.
3. **The Judgement count ignores the level.** The message shows `num = 10 + X1 + XLEVEL` (`:229`, `:248`), but the logic compares to the fixed `numToDo = 10` (`:96`, `:117`, `:199`). Casting at higher level does not raise the requirement.
4. **Lower Law calls `super.invoke` twice.** `Prayer_LowerLaw.java:94` and `:97` are identical. Whatever `StdAbility.invoke` does (mana, checks, messages) runs twice. The effect on mana is not verified.
5. **Marriage counts a birth on every attempt.** `Prayer_Marry.java:127` bumps `STAT_BIRTHS` before any validation, while `STAT_MARRIAGES` is bumped only on success (`:169`). This looks like a misplaced statistic.
6. **Unbraced `if` in weapon imbuing.** In `Prayer_ImbueHolyWeapon.java:93-94`, `if(CMath.bset(flags(), FLAG_UNHOLY))` guards only the following line. The same unbraced `if` is at `Prayer_ImbueFoulWeapon.java:87`, `Prayer_ImbueModestWeapon.java:81`, and line 93 of the other three weapon files. The `IS_BONUS` disposition is set only on the unholy weapon, and the level and effect lines run regardless. The intent is unverified.
7. **Dead cap.** `maxPrayerLevel()` is overridden in every weapon prayer and defined in `Prayer_ImbueShield.java:123-126`, but no call site exists in the tree (grep for `maxPrayerLevel` finds only definitions). The 15 and 20 caps are never enforced.
8. **Typos in messages.** "A moeration aura appears" (`Prayer_InfuseModeration.java:311`) and "A holy balanced aura" (`Prayer_InfuseBalance.java:313`) are wording errors. The Balance wording looks copied from the holy version.
9. **Inconsistent combat casting.** Infuse Balance and Infuse Moderation have no `castingQuality` override (the other four return `QUALITY_INDIFFERENT` in combat, `Prayer_InfuseUnholiness.java:261-268`).
10. **Possible null dereference.** In `Prayer_MalignedPortal.java:569-573`, if `getPlaneOfExistence(invoker)` is null, `newRoom` stays null, and `newRoom.okMessage` at `:573` throws. Whether `planeAble.invoke` can leave the plane missing is unverified.
11. **Unbounded loop.** `Prayer_MassGrave.java:111-112` picks random rooms until one passes `canAccess(mob, ...)`. With no accessible room, or with a null mob, this never ends. Whether that case occurs is unverified.
12. **Odd multiplier.** `Prayer_MassTongues.java:239` multiplies the standard tick count by 50. The duration is then 50 times as long as the malicious standard, which for a level-10 caster is about 1800 ticks. Intent unverified.
13. **Side effects in `okMessage`.** Several prayers do real work inside a veto hook: Ice Healing posts healing (`Prayer_IceHealing.java:120`), Linked Health posts damage (`Prayer_LinkedHealth.java:128`), and the portal runs a whole combat (`Prayer_MalignedPortal.java:181-186`). If a later listener vetoes the message, the healing, damage or combat has already happened. Mercy zeroes values in both phases.
14. **Hacked enumeration.** `Prayer_MassTongues.java:188-199` moves the caster between rooms to make `allPossibleCombatants` work. It restores the location in a `finally`, but it changes state other code may observe.
15. **Finalizer.** `Prayer_InfuseUnholiness.java:145-153` uses `finalize()` to deregister holy places. Java finalization is deprecated and not timely.
16. **Silent catch.** `Prayer_MassMobility.java:106-108` swallows exceptions from the dummy-mob probe.
17. **Duplicate guards.** `Prayer_LightHammer.java:140` repeats the `isEvil` test already made at `:121`. `Prayer_Mercy.java` zeroes damage twice, once in each event phase.
18. **Shadowing and no-ops.** `Prayer_LowerLaw.java:146-147` declares a local `L` that hides the `L(...)` helper for readers. `Prayer_JoyousRapture.java:103-107` overrides `setMiscText` with only a super call.

## Takeaways for a Python SUD

- **Use a declarative prayer table.** A single class (or a row in a dictionary) per effect, with fields for `domain`, `alignment` (a set such as `{"HOLY"}`), `quality` (`beneficial`, `malicious`, `neutral`), `scope` (`self`, `one`, `room`, `group`, `hostile_in_range`) and a reference to an effect function. Most of the forty classes differ only in those fields and one function.
- **Borrow the duration formulas, not the numbers.** `beneficial_ticks = level*4 + 40` and `malicious_ticks = round(level*1.1) + 25` (from `StdAbility.java:2174-2183` and `:2312-2330`) give a good curve for a single player. Measure durations in game time or turns, not in seconds.
- **Keep the veto-then-apply pipeline.** Give each effect `ok(event)` and `apply(event)` hooks. Run every `ok` first, then every `apply`. Do no side effects inside `ok`, which is the main lesson from Problems item 13.
- **Port the purity roll.** A one-line check, `if roll() > basis: allowed`, with a basis taken from one stored standing value toward the prayer's alignment, gives an alignment system with stakes and very little bookkeeping.
- **Collapse the duplicates.** The six Infuse prayers become one `Infusion(alignment_axis, value)` class. The six Imbue weapon prayers become one `imbue(alignment_mask, refuses)` table. The Mass Blindness, Deafness and Paralyze family becomes one `mass_debuff(stat, duration_fn)`. Each collapse removes the copied flag, ambiance and message lines from every prayer.
- **Keep the glyph and geas patterns.** A stored spell fired on trigger gives traps for free, and a counted social requirement gives quests. In Python: `Trap(spell_name, args, trigger=...)` and `Geas(required=10, action=...)`.
- **Keep the language curse as a cheap trick.** A random `Language` attached to a monster's output is a small, funny effect that needs no new output path.
- **Drop or rethink the multiplayer parts.** Marriage needs two player characters, a witness and a channel, so a single-player game would turn it into a companion or relationship state. Infusions depend on a worshipping community and on owned land, so a single player could keep a simple alignment field on a shrine object instead. Linked Health needs a group, so it would become a bond with an NPC companion. Judgement's monster branch is the part a single-player game can use directly, with an NPC performing the required social; its player branch needs a player to act.
- **Watch for the planar gate.** The Maligned Portal is a clever design: a generated room pair, an attackable fake mob as the door, and a closing timer. It needs a generated area system, so a Python SUD could represent it as a locked door with hit points and a timer that spawns a side area on demand.

## Coverage

- **Read in full (28 files).** `Prayer_MalignedPortal.java`, `Prayer_LesserWardingGlyph.java`, `Prayer_ImprovedWardingGlyph.java`, `Prayer_InfuseUnholiness.java`, `Prayer_ImbueShield.java`, `Prayer_ImbueHolyWeapon.java`, `Prayer_MassGrave.java`, `Prayer_Judgement.java`, `Prayer_MassTongues.java`, `Prayer_Marry.java`, `Prayer_LinkedHealth.java`, `Prayer_JoyousRapture.java`, `Prayer_Mercy.java`, `Prayer_IceHealing.java`, `Prayer_LightHammer.java`, `Prayer_MassHarm.java`, `Prayer_MassHeal.java`, `Prayer_MassMobility.java`, `Prayer_MassParalyze.java`, `Prayer_InciteDead.java`, `Prayer_MassCureDisease.java`, `Prayer_LowerLaw.java`, `Prayer_Maladiction.java`, `Prayer_IlluminateScroll.java`, `Prayer_Invigorate.java`, `Prayer_MajorInfusion.java`, `Prayer_MassForgive.java`, `Prayer_MassFreedom.java`. The base `Prayer.java` (outside this bucket) was also read in full, and so were the duration and copy helpers in `StdAbility.java`.
- **Read by diff against a sibling and grep (12 files).** `Prayer_InfuseHoliness.java`, `Prayer_InfuseDiscipline.java`, `Prayer_InfuseImpunity.java` (partly, through grep for its key lines), `Prayer_InfuseBalance.java`, `Prayer_InfuseModeration.java`, `Prayer_ImbueFoulWeapon.java`, `Prayer_ImbueJustWeapon.java`, `Prayer_ImbueModestWeapon.java`, `Prayer_ImbueSacredWeapon.java`, `Prayer_ImbueUnholyWeapon.java`, `Prayer_MassBlindness.java`, `Prayer_MassDeafness.java`.
- **Skipped.** None of the 40 files in the manifest. Outside the bucket, `Prayer_EmpowerRelic.java`, `Prayer_EnchantRelic.java`, `Prayer_ProtectSacredItem.java`, `Prayer_StorePrayer.java`, `Prayer_EmpowerShield.java` and `Prayer_DepleteRelic.java` were only checked for their calls into `Prayer.java`. `MUDFight.properTargets`, `MUDLaw`, `StdDeity` and `CMProps` were read only at the lines cited.
- **Unverified values.** `CMProps.Int.TICKSPERMUDMONTH` (`core/CMProps.java:345`), the castingTime unit in `Prayer_MalignedPortal`, and the source of `phyStats().ability()` used by the imbue check.
