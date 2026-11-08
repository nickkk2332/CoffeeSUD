# Prayers, part 4: Empower Unholy Armor to Hunt Law

> Part of the CoffeeMud NEWOVERVIEW. Bucket: `b070` (Abilities/Prayers [part 4/8: Prayer_EmpowerUnholyArmor .. Prayer_HuntLaw]) · 49 files · 8681 lines of Java.

All paths are relative to the CoffeeMUD folder. Java lives under `com/planet_ink/coffee_mud/`, shortened to `pkg/` below (so `pkg/Abilities/Prayers/Prayer.java` is the shared base). Line numbers were checked against the source. Anything I did not confirm by reading the code path is marked "unverified".

## Summary

These 49 prayers are the `Prayer` subclasses from `Prayer_EmpowerUnholyArmor` to `Prayer_HuntLaw`. They cover healing and harm, buffs and curses, a stored-prayer trap, trackers and searches, summoned and spawned creatures, item and relic enchantment, and a few hooks into the legal and faith systems. Every one extends `pkg/Abilities/Prayers/Prayer.java`, which adds an alignment gate: the prayer's holy or unholy flag decides how far the caster's faction standing may be from the pure end of that alignment before the cast can succeed. The rest is built from the generic `StdAbility` engine (tick-down durations, the two-phase message veto, proficiency rolls, fizzle messages), so each prayer is a few dozen lines of effect code. For a single-player text game the most reusable parts are the trap that stores and replays another prayer, the tracker that turns a search into step-by-step directions, the area-wide Holy Day, and the healing and harm dice table. The deity, faction, loyalty and legal parts assume other people and need rethinking.

## Key files and types

| File | Role | Lines |
|---|---|---|
| `pkg/Abilities/Prayers/Prayer.java` | Shared base: alignment gate, `invoke` chain, relic and infusion helpers | 432 |
| `pkg/Abilities/Prayers/Prayer_GreaterWardingGlyph.java` | Room trap that stores another prayer and casts it on the first victim who enters | 382 |
| `pkg/Abilities/Prayers/Prayer_FindSacredItem.java` | Radiant search for a deity-infused item, reported as spoken directions | 240 |
| `pkg/Abilities/Prayers/Prayer_HuntEvil.java` | Mob-level tracker; base class for `Prayer_HuntChaos`, `Prayer_HuntGood`, `Prayer_HuntLaw` | 242 |
| `pkg/Abilities/Prayers/Prayer_HolyDay.java` | Area-wide sabbath: blocks work, commerce and conquest until hour 15 | 223 |
| `pkg/Abilities/Prayers/Prayer_Haunted.java` | Room effect that turns corpses into ghosts on each tick | 190 |
| `pkg/Abilities/Prayers/Prayer_FaithfulHellhound.java` | Summons a dog-race combat pet that follows the caster | 203 |
| `pkg/Abilities/Prayers/Prayer_Gateway.java` | Creates a temporary GATE exit between two rooms | 183 |
| `pkg/Abilities/Prayers/Prayer_HolyWord.java` | Room-wide good/evil split of stats; clears cursed items from targets | 218 |
| `pkg/Abilities/Prayers/Prayer_HolyWind.java` | Knocks back and sometimes knocks down every room inhabitant | 211 |
| `pkg/Abilities/Prayers/Prayer_EnchantRelic.java` | Stores a prayer inside a Wand (a "relic") with five extra charges | 220 |
| `pkg/Abilities/Prayers/Prayer_EternalItem.java` | Item protection: renames the item and vetoes malicious actions on it | 224 |
| `pkg/Abilities/Prayers/Prayer_FreezeMetal.java` | Tick damage to loose metal items carried by the victim | 226 |
| `pkg/Abilities/Prayers/Prayer_Fluency.java` | Per-language cache that lets the bearer speak and understand natural languages | 217 |
| `pkg/Abilities/Prayers/Prayer_Forgive.java`, `Prayer_Heresy.java`, `Prayer_Faithless.java` | Hooks into the legal warrant system and the faith system | 170 / 154 / 151 |
| `pkg/Abilities/StdAbility.java` | Engine used by every prayer: `beneficialAffect`, `maliciousAffect`, tick-down, costs, `proficiencyCheck`, `copyOf`, fizzle helpers | 3018 (helpers skimmed) |
| `pkg/Libraries/Factions.java` | `getAlignPurity`, the distance used by the alignment gate | 2109 (helpers skimmed) |

## How it works

### 1. The shared base and the alignment gate

**Data model.** `Prayer.java` supplies the trigger words `PRAY` and `PR` (`Prayer.java:77`) and `classificationCode()` returning `Ability.ACODE_PRAYER` (`Prayer.java:86-88`). Each subclass adds its domain (for example `DOMAIN_BLESSING`, `DOMAIN_CURSING`, the constants at `pkg/Abilities/interfaces/Ability.java:760-786`) and its target codes. Alignment lives in the flags: `FLAG_HOLY` is 512, `FLAG_UNHOLY` is 1024, and `FLAG_NEUTRAL` is defined as both bits together (`pkg/Abilities/interfaces/Ability.java:964-968`). Because of that definition, a prayer flagged neutral is also flagged holy and unholy, and the gate treats it as neutral (see section 1, and the Problems list).

**The gate.** `Prayer.invoke` (`Prayer.java:423-430`) calls `StdAbility.invoke` and then `prayerAlignmentCheck` (`Prayer.java:372-418`). The check only applies when all of these hold (`Prayer.java:373-379`): the cast is not automatic, the caster is a player and not a monster, the prayer is "mine" to the caster, the prayer does not report itself as appropriate to the caster's factions, and the flags are non-zero.

- The threshold `hq` starts at 500 (neutral). `FLAG_HOLY` without `FLAG_UNHOLY` gives 1000 (good), and `FLAG_UNHOLY` without `FLAG_HOLY` gives 0 (evil) (`Prayer.java:381-390`).
- The basis is the caster's faction distance from the pure end of the required range (`Prayer.java:392-400`). For neutral the basis is reduced by 10 (`Prayer.java:400`).
- `Factions.getAlignPurity` returns a distance, not a purity: for good it is `|pct - top|` and for evil `|bottom - pct|` (`pkg/Libraries/Factions.java:873-910`). A caster at the pure end has basis 0.
- The cast passes when `rollPercentage() > basis` (`Prayer.java:403-404`). So a pure caster always passes, and the chance falls as the caster drifts toward the middle of the range.
- Failure messages are "The evil nature of X disrupts your prayer.", "The goodness of X disrupts your prayer." or the "non-good / non-evil nature ... disrupts your thought" variants (`Prayer.java:406-415`).

Two prayers in this bucket override `appropriateToMyFactions` to return true and so skip the gate: `Prayer_EnchantRelic.java:85-90` and `Prayer_EternalItem.java:89-94`.

**Proficiency.** Each cast calls `StdAbility.proficiencyCheck` (`pkg/Abilities/StdAbility.java:1441-1485`). The chance starts at the proficiency plus `2 * XLEVEL`, is clamped to 5..95 unless the DIS955RULE security flag is set, gets `XLEVEL/2` added, and gets a non-negative adjustment added. A negative adjustment does not reduce the chance. It is an early-out: the cast fails at once if `roll > 100 + adjustment`, and otherwise succeeds if `roll < pctChance` (`StdAbility.java:1480-1485`). The Gateway and Faithless penalties depend on this.

**Infusion and relic helpers** (shared with prayers in other buckets). `checkInfusionMismatch` (`Prayer.java:185-194`) and `checkRequiredInfusion` (`Prayer.java:196-206`) compare the deity that infused an object with the caster's worship. `infusePhysicalByAlignment` (`Prayer.java:208-255`) adds an infusion effect chosen by the caster's alignment, and `infuseMobAlignment` (`Prayer.java:257-292`) adds or extends a `Prop_WearZapper` mask such as `+FACTION -EVIL -NEUTRAL`. Relic lookups are `getRelicPrayers` (`Prayer.java:151-184`), `getDischargeableRelic` (`Prayer.java:307-318`) and `getRelicCharges` / `isARelic` (`Prayer.java:356-370`).

### 2. Durations and the tick model

- **Beneficial effects.** `StdAbility.beneficialAffect` (`StdAbility.java:2341-2349`) calls `setInvoker(mob)`, copies the ability, sets `canBeUninvoked = true`, and starts the tick-down. A positive duration argument is taken as literal ticks. Zero means the standard value `(casterLevel * 4) + 40` (`StdAbility.java:2312-2334`, formula at `:2325`).
- **Malicious effects.** `StdAbility.maliciousAffect` (`StdAbility.java:2185-2208`) uses a standard base of `adjustedLevel * 1.1 + 25` ticks (`StdAbility.java:2178`), then `adjustMaliciousTickdownTime` (`StdAbility.java:2140-2171`). That scales the time by the target's level relative to the caster (bounded by `EXPRATE`), caps it at a third of a mud hour for non-room targets when no level is forced, caps it at a mud day, and floors it at 2 ticks.
- **Permanent-ish effects.** `Ability.TICKS_ALMOST_FOREVER` is `Integer.MAX_VALUE/2` (`pkg/Abilities/interfaces/Ability.java:48`), used by Fidelity (`Prayer_Fidelity.java:135`). Prayers flagged uninvokable (`addNonUninvokableEffect`) persist until removed: Fertilize, Haunted and Guardian Hearth use this for owned property (`Prayer_Fertilize.java:197-204`, `Prayer_Haunted.java:175-179`, `Prayer_GuardianHearth.java:159-167`).
- **Tick targets.** Creature effects check `Tickable.TICKID_MOB` (HealingAura `Prayer_HealingAura.java:124`, FreezeMetal `Prayer_FreezeMetal.java:130`, HuntEvil `Prayer_HuntEvil.java:101`). Room and area effects run on every tick they receive: Haunted has no tick-ID filter (`Prayer_Haunted.java:111-147`).
- **Wake-up tick.** Fertilize on a permanent room that has not yet ticked starts a 3-tick countdown when someone enters (`Prayer_Fertilize.java:100-117`).
- **Time of day.** Holy Day ends when the area's hour reads 15 (`Prayer_HolyDay.java:166-167`).
- **Cast timing.** Heal and Heal Mount have a minimum cast wait of half a tick (`Prayer_Heal.java:70-73`, `Prayer_HealMount.java:70-73`).

### 3. Healing and harm: one dice table, split by undead status

The healing prayers all use `CMLib.dice().roll(5, adjustedLevel, 10)`, which is five dice of size L plus 10 (`Dice.java:398-405`, `Prayer_Heal.java:128`). The formulas below are the code as written.

| Prayer | Applies to | Effect and formula | Where |
|---|---|---|---|
| Heal | any creature | heals `5dL+10` through `postHealing` | `Prayer_Heal.java:124-130` |
| Heal Mount | the ridden mount (riders > 0) | same healing | `Prayer_HealMount.java:79-80,125-128` |
| Heal Undead | undead | adds `5dL+10` to HP directly | `Prayer_HealUndead.java:116-121` |
| Heal Undead | living | damage of `(5dL+10)/3`, type `TYP_UNDEAD`, burning | `Prayer_HealUndead.java:122-126` |
| Harm | any | damage `1d(L+24)+8`, type `TYP_UNDEAD`, bursting | `Prayer_Harm.java:122-123` |
| Godstrike | evil targets only | `3d(L+8)+L`; halved if either message is resisted; doubled vs undead; `TYP_JUSTICE` | `Prayer_Godstrike.java:109-115` |
| Hellfire | good targets only | `2dL + L/2` (integer); halved vs undead; `TYP_FIRE` | `Prayer_Hellfire.java:109-113` |
| Enervate | any | movement loss `10dL+50`; if fatigue is at or below the fatigued threshold, sets it just past that threshold (unless the race is infatigueable) | `Prayer_Enervate.java:91-98` |
| Freeze Metal | metal items carried by the victim, each tick | each item takes `1d(3+XLEVEL+2*X1)+1` damage | `Prayer_FreezeMetal.java:139-153` |
| Fire Healing | self, when fire damage hits | cancels the damage and heals `round(damage/2)` | `Prayer_FireHealing.java:113-120` |
| Fortress | self, when damaged | damage times 0.75, rounded; if unchanged and above 1, one less | `Prayer_Fortress.java:113-120` |

Harm and Heal both have an `executeMsg` hook: when the object they are on is eaten or drunk, they cast on the eater with auto set (`Prayer_Harm.java:84-92`, `Prayer_Heal.java:99-107`). A prayer can therefore be put on food or drink.

Harm is marked "beneficial for undead" in `castingQuality` (`Prayer_Harm.java:70-81`) but always posts damage in `invoke` (`Prayer_Harm.java:122-123`). The sign for undead must be decided by the damage code, which was not traced.

**Healing Aura** (`Prayer_HealingAura.java:89-99`) is auto-invoked and cannot be uninvoked. Three countdowns (5, 10 and 20 ticks) run in `tick` (`:101-103`). On each MOB tick they are decremented with `&&` short-circuiting (`:127`), so a timer reaching zero stops the later ones counting that tick. When a timer reaches zero, the aura casts Cure Light, Remove Poison or Cure Disease on each member of the healing set (`:146-184`). The set is the caster's group plus room inhabitants who are not undead, or who the caster may fight (`:130-145`). By reading, the cadence is therefore not exactly 5, 10 and 20 ticks.

### 4. Stat-changing buffs and curses

All of these change stats in `affectPhyStats` or `affectCharStats`, and `recoverPhyStats` reapplies them from base values.

- **Holy Word** (`Prayer_HolyWord.java:98-119`): a good creature gets armor `-(15+6X)` and attack `+(20+4X)`. An evil creature gets armor `+(15+6X)` and attack `-(20+4X)`, where X is the invoker's XLEVEL (`:107, 110-111, 116-117`).
- **Holy Aura** (`Prayer_HolyAura.java:98-109`): armor `-(10+4X)` and attack `+(10+2X)` (`:107-108`).
- **Great Curse** (`Prayer_GreatCurse.java:90-101`): armor `+(10+4X)` and attack `-(10+2X)` (`:99-100`).
- **Holy Shield** (`Prayer_HolyShield.java:89-97`): sets `STAT_SAVE_UNDEAD` to 100 (`:96`).
- **Feign Life** (`Prayer_FeignLife.java:89-96`): reports the race name as "Human" (`:95`).
- **Gills** (`Prayer_Gills.java:93-111`): adds a gill body part and adds saltwater and freshwater to the breathable set (`:96-110`).
- **God Light** (`Prayer_GodLight.java:78-90`): adds light-source and glowing, removes dark, and for creatures sets `CAN_NOT_SEE`.
- **Etherealness** (`Prayer_Etherealness.java:105-110`): weight 0 and height -1.
- **Hold Undead** (`Prayer_HoldUndead.java:98-105`): `CAN_NOT_MOVE`.
- **Holy Wind** (`Prayer_HolyWind.java:98-118`): the victim is sitting while the effect lasts, and standing is vetoed (`:115-116`).

The sign convention is confirmed by `pkg/Libraries/CoffeeLevels.java:147`, which uses `(103 - armor) / 3` as a power term. So a lower armor value is better, and the holy buffs lower it.

**Cleansing.** Holy Word and Holy Aura remove cursed items from the target. They loop over `Prayer_Bless.getSomething(target, true)` (which returns a random item the target carries that is cursed, `Prayer_Bless.java:123-143`). Each item is dropped with the message "<S-NAME> release(s) <T-NAME>." (`Prayer_HolyWord.java:182-184`). They then strip lower blessings and curses (`Prayer_HolyWord.java:185-191`).

**The caster's cost.** Great Curse first removes lower blessings from one of the caster's own blessed items (`Prayer_GreatCurse.java:137-142`). The helper `Prayer_Curse.getSomething(mob, true)` returns a random blessed item and prefers items that are worn or held over carried ones (`Prayer_Curse.java:122-141`).

### 5. Vetoes, redirects and rewrites

The message system is two-phase: `okMessage` can veto, and `executeMsg` runs afterwards (see the brief). The prayers here use the veto as their main mechanic.

- **Holy Shield** vetoes any message with `TYP_UNDEAD` as source or target code that is aimed at the bearer, with the text "holy shield block(s) the unholy magic" (`Prayer_HolyShield.java:100-110`). Harm and Heal Undead are the messages it blocks (`Prayer_Harm.java:113`, `Prayer_HealUndead.java:124`).
- **Etherealness** (`Prayer_Etherealness.java:113-165`): when the bearer acts, a closed door is refused while the bearer carries items (`:121-130`). Handling items (get, put, drop, wear, wield, lock and similar) is refused with "You fail to manipulate matter in this form." (`:132-149`). Opening, closing, pushing and pulling are refused (`:150-156`). Throwing and weapon attacks are refused and the bearer is made peaceful (`:157-161`).
- **Fidelity** (`Prayer_Fidelity.java:100-112`): while the bearer is married to their liege, two intimacy social commands (names matched at `:107-108`) are refused toward anyone else, with "You fidelity geas prevents you from doing that." (`:110`). The social names are not reproduced here.
- **Eternal Item** (`Prayer_EternalItem.java:131-151`): any malicious message aimed at the item is refused with "@x1 is eternally protected!" (`:143`), except teaching. Wear-and-tear items get their uses reset to 100 (`:146-150`).
- **Holy Day** (`Prayer_HolyDay.java:118-158`): see section 9.
- **Guardian Hearth** appears to be a veto but is not one; see Problems.

### 6. Traps and wards: Greater Warding Glyph

This is the only trap in the bucket. Its mechanism is a stored command replayed later by a dummy caster.

- **Setting it** (`Prayer_GreaterWardingGlyph.java:328-381`). The caster names a prayer and its arguments. The command list is prefixed with "PRAY" (`:338`) and resolved with `CMLib.english().getSkillToInvoke` (`:339`). The result must be a prayer (`:341`). The copy is kept in `shooter`, and the arguments in `parameters` (`:340, 348`). The effect is attached to the room for 9999 ticks (`:371`). The caster's name is stored as the effect's text (`:370`), so `theInvoker()` can find the caster by name later (`:106-113`).
- **Triggering** (`Prayer_GreaterWardingGlyph.java:276-299`). When a creature that is not the invoker enters the room, a trailer runs `spring` after the move.
- **Springing** (`Prayer_GreaterWardingGlyph.java:231-273`). If the creature cannot be targeted (`canInvokeTrapOn`, `:218-228`) or a d100 roll falls below its trap-save stat (`:242-243`), it avoids the trap with "<S-NAME> avoid(s) a mystical ward trap." (`:244`). Otherwise a `StdFactoryMOB` named "the thin air" is placed in the room at level `invoker + XLEVEL`, or 10 with no invoker (`:247-255`). The stored prayer is invoked on the victim with auto set (`:262`). The glyph then uninvokes and sets `sprung` (`:271-272`).
- **Exemptions** (`isLocalExempt`, `:197-216`). These only apply to permanent wards. A landholder with privileges is exempt, and so is a creature whose start room is in the same area.
- **One shot.** `disabled()` returns `sprung` (`:127-129`), and `canReSetTrap` is false (`:162-165`).

### 7. Trackers and searches

**Hunt Evil and its three subclasses** (`Prayer_HuntEvil.java:174-241`; each subclass is 97 lines).

- `invoke` removes any other tracking effects on the caster (`:181-182`). It refuses if an evil creature is already in the room, saying "Try 'look'." (`:190-194`). It then searches outward with `CMLib.tracking().getRadiantRoomsToTarget` over a range of `50 + XLEVEL + 2*XMAX` (`:198-212`, range at `:201`). It keeps the last room found and builds a trail with `findTrailToAnyRoom` (`:213-215`).
- The trail is held by a copy of the prayer added to the caster as an effect (`:228-233`).
- Each LOOK in the room (`executeMsg`, `:145-151`) recalculates `nextDirection` with `trackNextDirectionFromHere`.
- Each MOB tick (`tick`, `:97-135`) reports "The hunt seems to continue <dir>." (`:129`), "The hunt dries up here." (`:122`), or "The hunt seems to pause here." (`:115`). The sentinel values are -2 (unset), -1 (dried up), -999 (finished) and 999 (pause). 999 is tested but never set in this file.
- `affectPhyStats` marks the hunter `CAN_NOT_TRACK` (`:154-157`), the same flag used for untrackable creatures in `pkg/Races/Slime.java:132`.
- The three subclasses change only the search predicate and the flags. HuntChaos uses `isChaotic` and HuntGood uses `isGood` (`Prayer_HuntChaos.java:84-95`, `Prayer_HuntGood.java:84-95`). HuntLaw uses `isLawful` (`Prayer_HuntLaw.java:84-95`).

**Find Sacred Item** (`Prayer_FindSacredItem.java:154-239`).

- The caster must worship a deity (`:166-171`). The text typed is the search term (`:165`).
- `getHere` (`:71-102`) checks the room, the inhabitants' carried items, and shop stock for an item infused by the caster's deity (`:104-111`, using `CMLib.law().getClericInfused`).
- The search is radiant with range `5 + adjustedLevel/10 + XLEVEL + 10*XMAX` (`:129`).
- The trail is built even when the prayer fails (`:178-179`).
- On success the route is narrated from five random verb phrasings (`:206-222`), for example "First, thou shalt go <dir>, and then goest thou <dir>...". If the trail breaks, it says ", but then everything goes dark" (`:203`).
- Messages: "Thou feelst thou shouldst look around." when the item is in this room (`:188`), and "Your mind remains dark." when there is no direction (`:230`).

### 8. Summons, spawns and created objects

- **Faithful Hellhound** (`Prayer_FaithfulHellhound.java:110-202`). The caster must be in combat (`:112-116`) and may have only one hellhound at a time (`:117-126`). The creature is a `GenMOB` (`:139`) with hit points of `CMProps.getMobHPBase() + 4*XLEVEL` (`:140`), race Dog (`:144`), fire and acid saves 50 (`:147-148`), and combat stats from the leveler (`:151-154`). It is aligned evil (`:166`) and tattooed "SUMMONED_BY:" plus the caster's name (`:160-161`). It gets a Loyalty effect for the caster with `TELEPORT=TRUE` (`:168-173`), a CombatAbilities behavior with `MINTICKS=1 MAXTICKS=4 CHANCE=99` (`:174-179`), and Firebreath at proficiency 100 (`:180-185`). It then follows the caster (`:193`). `unInvoke` destroys it (`:97-107`). If the caster or its follower quits, the effect unwinds (`:83-94`).
- **Haunted** (`Prayer_Haunted.java:111-147`). Each tick, the room's first non-player corpse with a name is taken (`:118-131`). `Prayer_AnimateGhost.makeUndeadFrom` makes a ghost from it (`:134-135`; that class is in another bucket). The ghost is made invisible and given a `Thiefness` behavior (`:137-140`). The corpse is destroyed, `level` rises by 5, and `numDone` increases (`:141-143`). The cap is `numMax = (caster level + 2*XLEVEL) / 8` (`:174`). On owned land the effect is saved as a permanent room effect (`:175-179`). Otherwise it lasts `TICKSPERMUDMONTH` (`:181`). Its end message is "The haunted aura fades." (`:107`).
- **Fountain of Life** (`Prayer_FountainLife.java:110-147`). It costs all mana (`:85-88`). It places a misc-magic item named `LifeFountain` in the room (`:124-137`), and the effect destroys it on uninvoke with "The fountain of life dries up." (`:98-106`). The healing the fountain gives is in its item file, which was not traced.
- **Flesh Rock** (`Prayer_FleshRock.java:105-285`). The target becomes a rock statue: a `GenItem` named "a rocky statue of X" made of granite and weighing 2000 (`:250-262`). The target is made peaceful each tick and is pulled back to the room if it wanders (`:81-103`). It cannot move, hear, smell, speak or taste (`:170-175`) and breathes nothing (`:185`). The previous state is kept as `prevState` (`:275`) and restored on uninvoke (`:199-205`). Duration is `level + 2X` ticks (`:264`).
- **Gateway** is in section 10.

### 9. Holy Day: an area-wide rule

`Prayer_HolyDay.java:184-222`. The effect is attached to the area, not a room. Casting it again while it is active ends it (`:186-193`). The duration is `TICKSPERMUDDAY` (`:214`). Every room of the area is told "A holy day of <god> has begun!" (`:212`). The god is the caster's worship, or "the gods" (`:209-211`).

While active, `okMessage` refuses:
- common skills used by anyone other than the invoker, with "You are not allowed to work on the holy day of X." (`:120-128`);
- buying, selling, bidding, withdrawing, borrowing and depositing by anyone other than the invoker (`:130-140`);
- conquest, when the source belongs to a conquerable clan and the room's legal behaviour has control points (`:142-156`).

Ending: the area's hour reaching 15 ends it (`:166-167`), and each room gets "The holy day has ended." (`:112`).

### 10. Gateway, and other room-level effects

**Gateway** (`Prayer_Gateway.java:100-182`).
- With no argument (when automatic or cast by a monster) it adds the display text of a random room as the search name (`:102-103`). It then finds rooms by name with `CMLib.hunt().findRooms` (`:120`) and picks one of the matches at random (`:122`).
- The fizzle "A gateway cannot be created here." appears if a GATE exit already exists (`:109-114`).
- The proficiency penalty is the sum over the target room's inhabitants of their level minus `caster + 2X`, tripled for monsters, plus 20 per item (`:134-146`), passed as `proficiencyCheck(mob, -profNeg, auto)` (`:151`).
- On success it creates a `GenExit` named "a divine gateway" (`:163-168`) and installs it in both rooms on the `Directions.GATE` direction (`:169-172`). The effect lasts 15 ticks (`:174`). The uninvoke closes both sides with "The divine gateway closes." (`:85`, `:91`).

**Guardian Hearth** (`Prayer_GuardianHearth.java:97-133`) is a room effect that watches for trap and saving-throw type messages aimed at members of a protected group (see Problems). **God Light** (section 4) is also a room-level effect.

### 11. Legal and faith levers

- **Forgive** (`Prayer_Forgive.java:82-169`). It strips `$` from the name (`:88-92`) and looks up criminals and warrants in the room's legal behaviour (`:100-107`). On success each warrant gets crime "pardoned" and offenses 0 (`:149-154`). Prisoner, Shaming and Banishment effects are removed from the criminal (`:126-138, :155-160`). With nothing to forgive it fizzles "for no reason at all" (`:139-142`).
- **Heresy** (`Prayer_Heresy.java:88-153`). It needs a legal behaviour in the room (`:90-92, :104`) and costs 100 mana by override (`:81-85`). It files a warrant against the target (`:140`). The god is the caster's worship, or the first deity in the map if there is none (`:113-129`). The crime is "heresy against <target>", or "heresy against the gods" with no deity (`:115, :125`). The sentence index is `(caster class level - lowest level) / 5`, clamped to the punishment range (`:132-139`). The flags are `!witness` (`:131`). The caster's victim is restored afterwards (`:147-149`).
- **Faithless** (`Prayer_Faithless.java:95-150`). It sends a `MSG_REBUKE` from the target to the target's deity (`:133`), with "<S-NAME> LOSE(S) FAITH!!!" (`:133`). The faith loss itself happens in whatever handles that message, which was not traced. The proficiency penalty is 25 per level above `caster + 2X` (`:116-119`). It costs 100 mana by override (`:70-73`).
- **Feed The Dead** (`Prayer_FeedTheDead.java:82-166`). The caster gives experience (100 by default, or the number at the end of the command) to an undead creature that follows them (`:84-120`). The amount the target actually gains is used as a percent chance to make it loyal (`:141-145`). The loyal creature receives a Loyalty effect naming the caster (`:153-156`), but only if it has Prop_ModExperience and follows the caster (`:146-151`).
- **Freedom** (`Prayer_Freedom.java:69-161`). `returnOffensiveAffects` (`:82-114`) lists effects that bind or immobilise. A spell, chant, prayer or song effect is spared when its invoker's level is at or below `caster + 1 + 2X` (`:101-105`). Effects with no invoker are always offensive. The mending check uses `LASTPLAYERLEVEL` as the caster's level (`:74-76`).

### 12. Language and perception: Fluency

`Prayer_Fluency.java:89-216`. The effect keeps a cache of about 10 language copies (`:89, :110-111`; the cache is cleared once it exceeds 10), each set to 100 percent proficiency and to be spoken by the bearer (`:113-115`). When the bearer speaks a natural language to a non-animal creature, the cached copy processes the speech (`:127-135`). The prayer also records the last natural language and speaker it heard from others (`:152-162`), so the bearer can follow that language even if they do not know it. The effect is moved to the front of the effect list with `addPriorityEffect` (`:204-205`), to beat other languages.

### 13. Messages shown to players

Quoted short messages from this bucket, with their sources:

- "Your mind remains dark." (`Prayer_FindSacredItem.java:230`) and "Thou feelst thou shouldst look around." (`Prayer_FindSacredItem.java:188`)
- "The hunt dries up here." (`Prayer_HuntEvil.java:122`) and "The hunt seems to continue <dir>." (`Prayer_HuntEvil.java:129`)
- "Try 'look'." (`Prayer_HuntEvil.java:192`)
- "A holy day of <god> has begun!" (`Prayer_HolyDay.java:212`)
- "There can be no conquest on the holy day of <god>." (`Prayer_HolyDay.java:153`)
- "A blinding, divine gateway appears here." (`Prayer_Gateway.java:158`)
- "The fountain of life dries up." (`Prayer_FountainLife.java:98`)
- "The flame attack heals <S-NAME> N points." (`Prayer_FireHealing.java:118`)
- "X is too cold!" / "X is frozen stuck!", where X is the victim's name (`Prayer_FreezeMetal.java:117-119`)
- "<S-NAME> can't move!" (`Prayer_HoldUndead.java:168`)
- "A horrendous wind gust blows through here." (`Prayer_HolyWind.java:163`) and "<S-NAME> fall(s) down!" (`Prayer_HolyWind.java:192`)
- "You feel less constricted!" (`Prayer_Freedom.java:154`)
- "<T-NAME> is now loyal to you." (`Prayer_FeedTheDead.java:157`)
- "You lose N experience points for the effort." (`Prayer_EnchantRelic.java:183`)

## Content worth mining

### Catalogue of the 49 prayers

All files are in `pkg/Abilities/Prayers/`. "Flags" are the `flags()` values; "Target" is the target kind. Lines refer to the file of the same name.

| Prayer | Domain | Flags | Target | What it does (key numbers) | Lines |
|---|---|---|---|---|---|
| Empower Unholy Armor | BLESSING | UNHOLY | worn armor | +1 ability, +3 level, drops GOOD, adds BONUS and EVIL; zapper mask restricts to the deity; 50 XP; cap at ability 4 | 89-186 |
| Empower Unholy Weapon | BLESSING | UNHOLY | weapon | same as armor, for weapons | 89-176 |
| Enchant Relic | BLESSING | NOORDERING, NEUTRAL | wand | copies a known prayer into the wand; +5 charges; XP cost 10 × lowest level | 93-219 |
| Eternal Item | HOLYPROTECTION | NOORDERING, NEUTRAL | infused item | renames "X of <deity>", vetoes malicious actions, keeps uses at 100; needs 100 mana | 36-224 |
| Etherealness | NEUTRALIZATION | NEUTRAL | self | 3 ticks; weight 0; cannot handle items, use doors with items, or fight | 35-213 |
| Extinguish | NEUTRALIZATION | HOLY | creature, item, room, exit | puts out fire via the utensils library | 35-115 |
| Enervate | CORRUPTION | UNHOLY | creature | movement loss 10dL+50; can set fatigue | 36-110 |
| Faithful Hellhound | COMMUNING | none declared | self (summon) | combat pet, one per caster, Loyalty to caster | 35-203 |
| Faithless | EVANGELISM | UNHOLY | creature | target's deity receives a faith-loss rebuke; -25 per level | 35-151 |
| Feed The Dead | DEATHLORE | UNHOLY, NOORDERING | undead creature | experience transfer; loyalty by percent chance | 35-167 |
| Feign Life | DEATHLORE | UNHOLY | creature | race reported as Human | 35-160 |
| Fertilize | CREATION | NEUTRAL | room | vegetation farming yield +1 via ability code; duration class level × mud day | 35-221 |
| Fidelity | WARDING | HOLY | creature | marriage geas refusing two intimacy socials toward others | 35-144 |
| Find Sacred Item | COMMUNING | NEUTRAL | item by name | radiant search for deity-infused items; narrated route | 35-240 |
| Fire Healing | HEALING | UNHOLY, HEALINGMAGIC | self | absorbs fire damage and heals half of it | 35-161 |
| Flame Weapon | CREATION | UNHOLY, HEATING, FIREBASED | weapon | adds fire damage on hits, 0-6 × (X + X1) | 35-242 |
| Flesh Rock | CORRUPTION | NEUTRAL, POLYMORPHING | creature | turns creature into a rock statue item | 35-286 |
| Fluency | COMMUNING | HOLY | creature | language fluency with cache and overheard memory | 35-217 |
| Fortress | HOLYPROTECTION | UNHOLY | creature | damage × 0.75 | 35-156 |
| Forgive | EVANGELISM | HOLY | creature by name | pardons warrants; removes Prisoner, Shaming, Banishment | 35-170 |
| Fortify Food | BLESSING | NEUTRAL | food | +1000 nourishment if not already above 1000 | 35-121 |
| Fountain of Life | CREATION | HOLY | item (cast on) | spawns a LifeFountain item; costs all mana | 35-148 |
| Freedom | RESTORATION | HOLY | creature | removes binding and offensive effects | 35-162 |
| Freeze Metal | CURSING | HOLY, WATERBASED | creature and items | tick damage to loose metal; blocks removing items | 35-226 |
| Gateway | CREATION | NEUTRAL, TRANSPORTING | room | 15-tick GATE exit to another room | 35-183 |
| Gills | HOLYPROTECTION | HOLY | creature | gill body part; breathes salt and fresh water | 35-144 |
| God Light | CREATION | HOLY | room or creature | room light; creature blinded (CAN_NOT_SEE) | 35-189 |
| Greater Warding Glyph | WARDING | NEUTRAL | room | stored-prayer trap, one shot | 35-382 |
| Guardian Hearth | WARDING | NEUTRAL | room | "protects" group members; message only (see Problems) | 35-177 |
| Great Curse | CURSING | UNHOLY | creature | armor +(10+4X), attack -(10+2X); costs one caster blessing | 35-154 |
| Haunted | DEATHLORE | UNHOLY | room | corpses become ghosts each tick; cap (level+2X)/8 | 35-190 |
| Harm | VEXING | UNHOLY | creature | 1d(L+24)+8 TYP_UNDEAD; eat/drink hook | 35-136 |
| Heal | HEALING | HOLY, HEALINGMAGIC | creature | 5dL+10; eat/drink hook; half-tick cast wait | 35-142 |
| Healing Aura | HEALING | NEUTRAL | self (auto) | permanent aura; cures on 5/10/20-tick countdowns | 35-187 |
| Heal Mount | HEALING | HEALINGMAGIC | ridden mount | as Heal, for mounts with riders | 35-139 |
| Heal Undead | HEALING | UNHOLY, HEALINGMAGIC | undead or living | heals undead; damages living at one third | 35-136 |
| Heresy | EVANGELISM | UNHOLY | creature | files a legal warrant; 100 mana | 35-154 |
| Hellfire | VEXING | UNHOLY | good creature | 2dL + L/2 fire | 35-123 |
| Godstrike | VEXING | HOLY | evil creature | 3d(L+8)+L justice damage | 35-124 |
| Hold Undead | NEUTRALIZATION | NEUTRAL | undead creatures | cannot move; 2 to 8 ticks | 35-182 |
| Holy Aura | BLESSING | HOLY | creature | removes cursed items; armor -(10+4X), attack +(10+2X) | 35-175 |
| Holy Day | EVANGELISM | NEUTRAL | area | blocks work, commerce, conquest until hour 15 | 35-223 |
| Holy Shield | HOLYPROTECTION | HOLY | creature | save vs undead 100; vetoes TYP_UNDEAD | 35-158 |
| Holy Wind | CREATION | MOVING, NEUTRAL | room creatures | knockback; knockdown on d100 vs 2×DEX + level - 5×duration | 35-211 |
| Holy Word | BLESSING | HOLY | room creatures | good and evil stat split; removes cursed items | 35-218 |
| Hunt Chaos | NEUTRALIZATION | LAW, TRACKING, DIVINING | self | tracks chaotic creatures | 35-97 |
| Hunt Evil | NEUTRALIZATION | HOLY, TRACKING, DIVINING | self | tracks evil creatures | 35-242 |
| Hunt Good | NEUTRALIZATION | UNHOLY, TRACKING, DIVINING | self | tracks good creatures | 35-97 |
| Hunt Law | NEUTRALIZATION | CHAOS, TRACKING, DIVINING | self | tracks lawful creatures | 35-97 |

### Reusable content

- **Trail narration** (`Prayer_FindSacredItem.java:196-225`): five phrasings for each step and a failure line, which can be reused for any route-giving NPC or item.
- **Message bank**: short messages above are a ready source of flavour for a text game. Several are plain, for example "The divine gateway closes." and "The haunted aura fades."
- **Numbers**: the healing and harm dice table in section 3 and the stat splits in section 4 are small, easy to tune tables.
- **Named effects**: Fountain of Life, Flesh Rock (statue), Holy Day, Haunted and Hellfire are distinctive in-world ideas with ready hooks.

## Notable design ideas

1. **A stored prayer as a trap** (`Prayer_GreaterWardingGlyph.java:247-270`). The trap keeps a command, not an effect, and replays it through a dummy caster. Any ability can be turned into a trap this way.
2. **Alignment as a probability** (`Prayer.java:381-404`). Alignment is a graded distance from the pure end of a range, rolled against a d100. It rewards a committed character and gives others a chance to slip.
3. **A calendar sabbath** (`Prayer_HolyDay.java:118-223`). An area-wide rule ends at a fixed hour and blocks work, trade and conquest. This fits a living world.
4. **Relics with payloads** (`Prayer_EnchantRelic.java:189-210`). A wand can hold a copy of another prayer with its own charges, and the enchantment raises its level.
5. **Items that record their owner by renaming** (`Prayer_EternalItem.java:116-119`). The deity's name is appended to the item name, so the item carries its history in the text the player sees.
6. **Symmetric good and evil stat splits** (`Prayer_HolyWord.java:107-117`). One effect helps creatures of one alignment and hurts the other, with the same formula and opposite signs.
7. **Escalating undead** (`Prayer_Haunted.java:141-143, 172-174`). Each raised ghost is stronger by 5 levels, and the number is capped by caster level.
8. **Faith as a reporting mechanism** (`Prayer_Heresy.java:112-140`). A priest's accusation becomes a formal warrant with a sentence chosen from the caster's class level.
9. **Veto-based protection** (`Prayer_HolyShield.java:100-110`, `Prayer_EternalItem.java:138-145`). Protection is written as a refused message rather than as a damage reduction, so it covers every action type.

## Problems and cruft

1. **Dead cleanup loop in Freeze Metal.** `affectedItems` is declared, cleared in `setMiscText` and iterated in `unInvoke`, but it is never filled (`Prayer_FreezeMetal.java:89, 95, 179-188`).
2. **Freeze Metal goes silent without an invoker.** `tick` returns at once when the `invoker` field is null (`Prayer_FreezeMetal.java:134`). `invoker()` returns only that field (`pkg/Abilities/StdAbility.java:1563-1566`). `pkg/Libraries/CoffeeMaker.java` has no reference to `invoker`, so the field does not appear to be saved (unverified: the loader was not traced).
3. **Glyph "already set" test is wrong for explicit targets.** The condition at `Prayer_GreaterWardingGlyph.java:350` includes `givenTarget != null`. Any cast with an explicit target reports "A ward trap has already been set" (`:352`) and does nothing.
4. **Glyph state does not survive a reload, by reading.** `shooter` and `parameters` are plain fields, and the effect's text is the caster's name (`:370`). A restored ward has `shooter == null`, so `spring` returns at once (`:237-238`) and the ward stays inert (unverified: persistence path).
5. **Find Sacred Item builds the trail on failure too** (`Prayer_FindSacredItem.java:178-179`). The radiant search runs even when the cast fails.
6. **Guardian Hearth does not protect.** `okMessage` shows "The guardian hearth protect(s) <S-NAME>!" (`Prayer_GuardianHearth.java:127`) and then returns `super.okMessage` (`:132`). `StdAbility.okMessage` always returns true (`pkg/Abilities/StdAbility.java:2837-2840`). The message is never blocked or reduced. The only reference to the class in the tree is the Shaman mapping (`pkg/CharClasses/Shaman.java:163`).
7. **God Light's dark-vision bit is probably lost.** `CAN_SEE_DARK` is added to the caster's phyStats (`Prayer_GodLight.java:165`), and then `recoverPhyStats()` runs (`:171`). `StdMOB.recoverPhyStats` copies base stats over phyStats first (`pkg/MOBS/StdMOB.java:798-800`), so the bit appears to be dropped (by reading).
8. **Duplicate call in God Light.** `recoverRoomStats()` is called twice (`Prayer_GodLight.java:178-179`).
9. **Healing Aura cadence and set.** The short-circuit countdown (`Prayer_HealingAura.java:127`) makes the cadence irregular. The healing set includes undead the caster may fight (`:141`), which looks inverted (intent unverified).
10. **Haunted's settings are not saved.** `numMax`, `level` and `numDone` are not part of the effect's text (`Prayer_Haunted.java:89-91`, set at `:172-174`). A reloaded land-owned haunting would fall back to unlimited ghosts from level 14 (by reading).
11. **Haunted scans every tick.** It scans the room's items on every tick with no tick-ID check (`Prayer_Haunted.java:111-131`).
12. **Holy Day exemption depends on the invoker** (`Prayer_HolyDay.java:124, 136`). With no invoker set, the caster is not exempt from their own restrictions.
13. **Hellhound level bonus is overwritten.** The summon's level gets `XLEVEL` added at `Prayer_FaithfulHellhound.java:141` and is reset to the caster's level at `:155`. Only hit points use the bonus.
14. **Enchant Relic costs and no-ops.** Experience is deducted before the proficiency roll and is not refunded on failure (`Prayer_EnchantRelic.java:181-190`). The pre-check uses the unadjusted cost (`:170`) while the deduction uses the adjusted cost (`:180`). `wand.text();` (`:208`) does nothing.
15. **Duplicate code in the empower prayers.** `Prayer_EmpowerUnholyArmor.java:89-186` and `Prayer_EmpowerUnholyWeapon.java:89-176` differ only in their target checks. Both contain a no-op `target.text();` (`Armor:177`, `Weapon:167`).
16. **Fluency fails with the wrong fizzle.** A beneficial prayer fails with `maliciousFizzle` (`Prayer_Fluency.java:212`).
17. **Hold Undead can report failure after success.** `success` is overwritten for each undead target (`Prayer_HoldUndead.java:167`), so a later failed target makes the whole cast fail even if earlier targets were held.
18. **Harm and Heal Undead tension.** Harm is "beneficial for undead" in `castingQuality` (`Prayer_Harm.java:70-81`) but always posts damage (`:122-123`). The sign is decided elsewhere (unverified).
19. **Fertilize never resets the farming bonus.** `setAbilityCode(1)` raises the yield multiplier (`Prayer_Fertilize.java:149`; `pkg/Abilities/Common/Farming.java:206` uses it). Nothing in this file resets it. The restore on uninvoke (`:86-90`) is effectively a no-op because the room resource is never changed (`:210` is commented out). Whether it is reset elsewhere is unverified.
20. **Heresy's crime text may not expand.** The crime string contains `<T-NAME>` literally (`Prayer_Heresy.java:115`). Whether the law system expands it is unverified.
21. **Gateway state is in fields only.** `newRoom` and `oldRoom` (`Prayer_Gateway.java:75-76`) are not in the effect's text, so a reloaded gate may not close its far side (unverified).
22. **Eternal Item name cache.** `newName` is cached and only reset by `setMiscText` (`Prayer_EternalItem.java:96-124`), so a later rename of the item may be overwritten (unverified).
23. **Eternal Item mana.** The prayer costs all mana (`:82-86`, and `StdAbility.java:1614-1618`) and then also subtracts 100 on success (`:211`). How the two interact was not traced.
24. **Alignment flags for Law and Chaos.** HuntChaos is flagged LAW and HuntLaw is flagged CHAOS (`Prayer_HuntChaos.java:54`, `Prayer_HuntLaw.java:54`). The alignment gate reads only the holy and unholy bits (`Prayer.java:381-390`), so both use the neutral gate.
25. **Neutral is a combination of holy and unholy.** `FLAG_NEUTRAL` is `FLAG_HOLY | FLAG_UNHOLY` (`pkg/Abilities/interfaces/Ability.java:968`). It works, but any holy-only or unholy-only test silently ignores neutral prayers.
26. **Negative proficiency adjustments are early-outs.** A large penalty does not lower the success chance (see section 1). This is easy to misread when tuning the Gateway and Faithless penalties.
27. **Multiplayer assumptions.** Forgive, Heresy and Freedom depend on the legal warrant system and on player names (`Prayer_Forgive.java:122`, `Prayer_Freedom.java:74-76`). Faithful Hellhound depends on followers and Loyalty. Holy Day restricts commerce and conquest. Faithless relies on deity worship and rebuke messages handled elsewhere.

## Takeaways for a Python SUD

1. **Represent prayers as data, not classes.** One table row per prayer: `id`, `domain`, `alignment` (holy, unholy or neutral), `target` (self, creature, item, room or area), `mana` (number or "all"), `xp_cost`, `duration_ticks` (0 for the level-based default), and an `effect` handler name. Most of the 49 files are the same shape, so one dispatch table with a few handlers covers them.

   ```python
   @dataclass(frozen=True)
   class PrayerSpec:
       id: str
       domain: str
       alignment: str        # "holy" | "unholy" | "neutral"
       target: str           # "self" | "creature" | "item" | "room" | "area"
       mana: int | str       # int, or "all"
       duration: int         # ticks; 0 = level-based default
       effect: str           # handler name
   ```

2. **Dice as text.** Keep formulas as strings such as `"5d{lvl}+10"` and parse them once. The source's `roll(n, sides, modifier)` (`Dice.java:398-405`) is a one-line Python helper: `sum(random.randint(1, sides) for _ in range(n)) + modifier`.

3. **Alignment gate as a roll.** Keep the probability idea but simplify the input. A single "piety" or "faction distance" number per character, and `random.randint(1, 100) > distance` (`Prayer.java:403`), gives the same behaviour. Drop the zapper-mask language (`MUDZapper`), which is far more general than a single player needs.

4. **Two-phase veto for effects.** A small event loop with `ok(event) -> bool` and `apply(event)` lets an effect such as Holy Shield, Etherealness or an eternal item refuse an action. This is worth borrowing even in a single-player game, because it lets many effects interact without special cases.

5. **Durations in ticks, one clock.** Use one tick counter for real-time and turn-based play. Keep the tick-down and expiry logic in one `Effect` base class, and make per-creature and per-room hooks explicit.

6. **Stat modifiers recomputed from base.** Store base values and a list of effect modifiers, and compute the effective value on demand (the `recoverPhyStats` pattern). This avoids the stale-bit problem noted in Problems item 7.

7. **Trap as stored command.** Save `(prayer_id, args)` on a room. When the player enters, roll a save, and on failure call the dispatcher with a dummy caster. Drop the faction and ward-exemption logic and keep the replay.

8. **Tracker as a path.** Use a shortest path (breadth-first is enough) to the nearest matching creature, store it on the player, and on each LOOK return the next direction. Narrate with several phrasings, as in `Prayer_FindSacredItem.java:196-225`.

9. **Holy Day as a world calendar rule.** Attach a set of forbidden actions to an area, end it at a fixed in-game hour, and check it in the action layer. Keep "no commerce on the holy day" and "no conquest" if the game has trade and territory.

10. **Remove or rethink.** Drop the loyalty-to-caster effects, the group-based protections and the legal warrant hooks (Forgive, Heresy, Faithful Hellhound's loyalty) unless the SUD has a crime system. Replace deity worship with one patron value per player if the game wants a faith system. The Guardian Hearth idea of protecting a group can be rebuilt on a single "guardian" creature.

11. **Single-player changes.** Replace group and follower checks with "the player" and "companions". Replace the "other player's level" caps in Freedom and the hellhound's Loyalty with NPC-only rules.

## Coverage

- **Read in full (49 files):** every `Prayer_*.java` in this bucket (listed in the catalogue), plus the base `pkg/Abilities/Prayers/Prayer.java` (432 lines).
- **Skimmed:** `pkg/Abilities/StdAbility.java` (`invoker` and `okMessage`, `beneficialAffect`, `maliciousAffect`, `adjustMaliciousTickdownTime`, `getBeneficialTickdownTime`, `proficiencyCheck`, `adjustedLevel`, `overrideMana`, `copyOf`, the cost block); `pkg/Libraries/Factions.java` (`getAlignPurity` and `getInclinationPurity`); `pkg/Libraries/Dice.java` (`roll`, `rollPercentage`); `pkg/Prayer_Bless.java` and `pkg/Prayer_Curse.java` (the `getSomething` helpers); `pkg/Libraries/CoffeeLevels.java:147`; `pkg/Abilities/Common/Farming.java:206`; `pkg/Common/interfaces/PhyStats.java` constants; `pkg/Libraries/CoffeeUtensils.java` ruin and wish checks (`:1200`, `:1361`, `:1433`).
- **Grepped only:** `pkg/Libraries/MUDZapper.java` (the `DEITY` describer at `:2622-2633`); `pkg/CharClasses/Shaman.java:163` (the Guardian Hearth mapping); the search for references to `Prayer_GuardianHearth`; the search for `invoker` in `pkg/Libraries/CoffeeMaker.java`.
- **Skipped:** the other prayer buckets, including `Prayer_AnimateGhost` (used by Haunted), `Prayer_Bless` and `Prayer_Curse` apart from the helpers, `Prop_WearZapper`, the legal behaviour, the tracking library, and the combat library's damage and healing posting.
