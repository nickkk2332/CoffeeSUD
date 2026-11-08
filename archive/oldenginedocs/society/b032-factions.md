# Factions: numeric standings, change triggers and their effects

> Part of the CoffeeMud NEWOVERVIEW. Bucket: `b032` (Factions) · 4 files · 8199 lines of Java.

All Java paths are relative to `com/planet_ink/coffee_mud/`. `resources/` paths and `coffeemud.ini` are relative to the CoffeeMUD folder. Line numbers were read from the source. Nothing was run, so every behaviour claim is "by reading" unless marked otherwise.

## Summary

A faction is one named integer axis (alignment runs from -10000 to 10000, for example) on which any mob may hold a value. Named bands along the axis give the value a word, such as PUREEVIL or JUSTGOOD, and the same band lookup then gates abilities, selects NPC reactions and decides which automatic reaction factions apply. Values change only through change events: game messages (kills, deaths, speech, socials, ability use, entering areas, bribes) and a periodic tick are matched against each faction's rule table, and a match moves the value by a base of 100 points times a per-rule factor, optionally scaled by level, limited by chance, cooldown and time windows, and passed to related factions. The system is data-driven from `.ini` files and plugs into the normal message bus, so abilities, behaviours and properties can veto or rescale changes without special-case code in the mob. For a single-player SUD the scalar-plus-bands-plus-rules shape is the part worth borrowing; the multiplayer assumptions and several of the implementation details are not.

## Key files and types

| File or class | Role | Lines |
|---|---|---|
| `Common/interfaces/Faction.java` | Public contract. `Faction` (50); nested `FactionChangeEvent` (951), `FRange` (1332), `FData` (1437), `FAbilityUsage` (1550), `FZapFactor` (1678), `FReactionItem` (1748); enums `MiscTrigger` (1314), `Align` (1841), `FacTag` (1869); direction constants (1289-1308); flag keys (1310-1312) | 1903 |
| `Libraries/interfaces/FactionManager.java` | Manager contract behind `CMLib.factions()`; `FAbilityMaskType` (622) | 630 |
| `Common/DefaultFaction.java` | The faction itself: fields (93-132), ini loader (529-641), ranges (643-671, 1116-1133), usage gates (1199-1253), change dispatch (1281-1746), change execution (1785-2005), event parser (2174-2611), ranges (2632-2744), holder state `DefaultFactionData` (2768-3182), reaction items (3248-3350), ability usage (3352-3517), zapper factors (3184-3246) | 3557 |
| `Libraries/Factions.java` | Library and registry (`Factions`, 41): ini cache (158-298), auto defaults (739-770), reaction factions (569-736), timed-event tick (807-870), alignment helpers (873-989), direct change posting (533-566), archon editor (1032-1940), resave (2038-2083) | 2109 |

Related code outside this bucket that the faction system depends on:

- `MOBS/StdMOB.java`: holder storage (`factions`, 144; `addFaction` 5776, `adjustFaction` 5797, `fetchFaction` 5825, `fetchFactionData` 5834); message routing (2506-2509, 3577-3588, 3973-3976); periodic update (4363-4386).
- `Locales/StdRoom.java`: `updatePlayerFactions` on area change (781-786); `send` (1734-1739).
- `Abilities/StdAbility.java`: `getInappropriateFaction` (2872-2886) and `appropriateToMyFactions` (2865-2870).
- `Abilities/Misc/PresenceReaction.java`: the carrier for NPC reactions.
- `CharClasses/Paladin.java` and `Cleric.java`: prayer gates that consult faction usage.
- `application/MUD.java:1672`: registers `Factions.tick`. `core/threads/ServiceEngine.java:373` and `StdTickClient.java:35-40, 134-160` set the cadence.
- Data: `resources/factions/alignment.ini`, `inclination.ini`, `triumph.ini`; `resources/examples/*reaction.ini` and `reputation.ini`; `coffeemud.ini` (EXPRATE 406, AUTOREACTION 444, FACTIONS 502, TICKTIME 2151).

## How it works

### 1. Data model

**Faction definition.** `DefaultFaction` holds the minimum, middle, maximum and difference (98-101), the range table `ranges` keyed by upper-case code name (117), a value-to-range cache `rangeRangeMap` (118), the auto, default and choice lists (115-116, 130), `factors` (125), `relations` (126), `changes` (121), `affBehavs` (119), `abilityUsages` (127), and `reactions` with `reactionHash` (131-132). Flags are `internalFlagBitmap` (IFLAG_IGNOREAUTO=1, NEVERSAVE=2, CUSTOMTICK=4, `Faction.java:1834-1838`), plus `showInScore`, `showInFacCommand`, `showInSpecialReport`, `showInEditor`, `isInherited` and `useLightReactions` (106-112). A faction built in code via `initializeFaction(String)` gets minimum 0, middle 50, maximum 100, experience EXTREME and a single "Sample Range" (513-527).

**Ranges.** A range is written `low;high;name;codeName;alignFlag` and stored as `DefaultFactionRange` (2701-2714). The faction's extremes are recomputed from its ranges (`recalc`, 643-671), and `middle = minimum + round((maximum - minimum) / 2)` (667). `fetchRange(int)` returns the first range with `low <= value <= high` (1117-1131). Both ends are inclusive, and the search runs in the order of the `ranges` hash table, not file order.

Example, `resources/factions/alignment.ini:100-106`: seven bands from PUREEVIL (-10000..-7500) through PNEUTRAL (-1499..1500) to PUREGOOD (7501..10000). Each band also has an alignment flag (EVIL, NEUTRAL, GOOD) used by the legacy helpers.

**Holder state.** Each mob keeps a `FData` per faction in `StdMOB.factions` (`MOBS/StdMOB.java:144`). `DefaultFactionData` (2768) keeps the value, the current `FRange`, the reaction sets for that range (2895-2922), the event timers and counters (2785-2786), and the next-allowed time for each event (2787). A mob that lacks the faction gets `Integer.MAX_VALUE` from `fetchFaction` (`StdMOB.java:5829`; interface note at `Faction.java:123-127`). That sentinel is the "absent" marker everywhere.

**Setting values.** `StdMOB.addFaction` clamps the value to the faction's minimum and maximum (`StdMOB.java:5782-5785`) and then calls `setValue` (5793). `setValue` looks up the range again only when the value leaves the current one (`DefaultFaction.java:2882-2886`). `adjustFaction` adds a delta to the current value (`StdMOB.java:5797-5816`).

**Legacy alignment helpers.** `getAlignMedianFacValue` returns the top of the GOOD or LAWFUL bands, the bottom of EVIL or CHAOTIC, and the middle of NEUTRAL or MODERATE (`Factions.java:952-989`). `getAlignPurity` returns the percent distance from that ideal (873-910). `setAlignmentOldRange` maps the old 0-1000 scale: at least 650 is GOOD, at least 350 NEUTRAL, at least 0 EVIL (516-530). The alignment faction is `alignment.ini` (`getAlignmentID`, 497); the lawful-chaotic axis is `inclination.ini` (503). For `alignment.ini` the computed ideals are EVIL -10000, GOOD 10000 and NEUTRAL 1 (worked by hand from RANGE3-RANGE5; not run).

### 2. Who holds a faction and how it is granted

- **Ini keys.** The loader reads keys by prefix from a `CMProps` object, which is a `Properties` subclass (`core/CMProps.java:48`): `RANGE*`, `CHANGE*`, `AFFBEHAV*`, `FACTOR*`, `RELATION*`, `ABILITY*` and `REACTION*` (`DefaultFaction.java:590-640`). Scalar keys are `NAME`, `MINIMUM`, `MAXIMUM`, `EXPERIENCE` (blank becomes NONE, 564-566), `INHERITED` (blank becomes true, 572-574), `RATEMODIFIER`, `DEFAULT`, `AUTODEFAULTS`, `AUTOCHOICES`, `CHOICEINTRO` and `USELIGHTREACTIONS`. A minimum above the maximum is swapped (556-562).
- **DEFAULT** is the value used by ADD events and by manual adds. `findDefault` returns the first entry whose zapper mask matches (1165-1176).
- **AUTODEFAULTS** is applied when a mob comes to life or enters a new area. `updatePlayerFactions` (`Factions.java:739-770`) adds every loaded faction whose `findAutoDefault` is not the sentinel, unless the faction has IFLAG_IGNOREAUTO. Call sites: `StdRoom.java:781-786` (area change) and `StdMOB.java:1334-1336` (come to life, unless the mob carries a SYSTEM_SUMMONED tattoo).
- **Choices** at character creation are the `AUTOCHOICES` entries that pass the mask (`findChoices`, `DefaultFaction.java:934-945`). The intro text comes from `CHOICEINTRO`.
- **Reaction factions** (`Factions.java:569-736`). `AUTOREACTION` (`coffeemud.ini:444`, default PLANAR) can list AREA, NAME, RACE, PLANAR and DEITY. For AREA, NAME, RACE and DEITY the library builds a faction on first use with `makeReactionFaction`, from a template in `resources/examples/` (areareaction, namereaction, racereaction, deityreaction). It substitutes `<CODE>`, `<NAME>`, `<FACTIONID>` and `<CLASSID>`, and sets IFLAG_IGNOREAUTO, NEVERSAVE and CUSTOMTICK (589-593). PLANAR only finds factions that already exist as `PLANE_*` (688-701) and never generates them.
- **Monster-only effects.** `updateFactionData` attaches the `AFFBEHAV` abilities and behaviours, but only to mobs with `isMonster()` (`DefaultFaction.java:830`), and each one is gated by its own zapper mask (833-852). Players never receive them (`Faction.java:732-733`).

### 3. Changing values: the event pipeline

**Entry.** Every mob runs its factions on every message it receives. `StdMOB.executeMsg` calls `fD.getFaction().executeMsg(this, msg)` and `fD.executeMsg(this, msg)` (`MOBS/StdMOB.java:3973-3976`), and `okMessage` is routed the same way (2506-2509). `Room.send` passes a message to the source and then to the room's occupants (`Locales/StdRoom.java:1734-1738`; `sendAndExec` not read). Most branches of `DefaultFaction.executeMsg` (1281-1746) guard with `msg.source() == myHost` or a similar holder test, so each rule is normally applied by the holder it belongs to.

**Triggers.** The trigger is the first field of a `CHANGE` line.

| Trigger | Fires when | Code |
|---|---|---|
| `MURDER` | the holder dies; applies to the killers who are combat beneficiaries | `DefaultFaction.java:1323-1346` |
| `KILL` | the holder kills someone; applies to the killer | 1330-1346 |
| `DYING` | the holder dies, applied to itself | 1423-1437 |
| `AREAKILL` (PCT) | a monster dies in the holder's area; each player beneficiary of the kill gets a per-player counter over area population | 1386-1421 |
| `AREAASS` (PCT) | the area's maximum-level monster dies; each player beneficiary gets a counter over MAX_LEVEL_MOBS | 1350-1385 |
| `AREAEXPLORE` (PCT) | the holder enters a room it has not visited; PCT=0 means first room in the area | 1290-1320 |
| `SUNK` / `SINK` | a ship sinks with the killer in its area (`SUNK`), or the holder is the killer (`SINK`) | 1440-1463 |
| `BRIBE` (AMOUNT, PCT) | the holder gives coins to a non-shopkeeper MOB | 1464-1491 |
| `TALK` (REGEX) | the holder says something matching REGEX; addressed MOB or NPCs who can hear | 1496-1553 |
| `MUDCHAT` | an NPC with a chatty behaviour answers the holder | 1554-1608 |
| `ARRESTED` | a law officer NPC handcuffs the holder; the change is applied to the holder with the officer as source | 1667-1690 |
| social name, `NAME *`, or `SOCIAL` with ID | the holder performs a social at a MOB | 1710-1743 |
| `ADDOUTSIDER`, `TIME` | periodic, from `Factions.tick` | `Factions.java:772-861` |
| `MSG_*` (any CMMsg type) | any message of that type | 1611-1664 |
| ability ID, ACODE class, DOMAIN, or FLAG | the holder uses an ability | 1665-1708 |

**Gating.** A rule is checked in this order.

1. The event's target zapper mask (the last field of the rule) and its source mask (the `MASK` trigger parameter) (`applies`, `DefaultFaction.java:2540-2546`).
2. `WITHIN=n`: the rule is valid only within n ticks after the holder's last reset of that event (2549-2568). The window is measured in ticks, converted at `CMProps.getTickMillis()`.
3. `CHANCE=p`: a percentage roll (2586; 1805-1808).
4. `WAIT=n`: a per-holder cooldown of n ticks after the rule fires (2587; `checkApplyEventWait` 1266-1278).

Flags come from the trigger's flag list: `OUTSIDER` lets the rule fire when the target lacks the faction, `SELFOK` allows source equal to target, `JUST100` skips level scaling, `XP=` and `RPXP=` grant bonus experience, `RESTIME=` resets other events' timers, and `ANNOUNCE=` gives a message (`Faction.java:1310-1312`; parsed at 2521-2530). A `TIME` event is exempt from the SELFOK rule (1789-1792).

**Amount.** `executeChange` (`DefaultFaction.java:1785-2005`) computes the change in these steps.

1. Base amount 100 (1810).
2. Level scaling, unless source equals target or JUST100 is set (1811-1827). Let `levelDiff = target.level - source.level` and `L` be EXPRATE, which defaults to 6 (`coffeemud.ini:406`). The base is 0 when `levelDiff < -L`. Otherwise the base is `100 + 100 * (levelDiff / L)`, subject to the cap discussed in Problems.
3. Direction (1888-1943):
   - `UP` +1 and `DOWN` -1, each times the base.
   - `MAXIMUM` and `MINIMUM` set the value to the end of the range (`maximum - value`).
   - `OPPOSITE` moves against the target's side of the middle (1904-1917). Its base becomes `base/2 + round(base/2 * |source - target| / difference)`.
   - `TOWARD` and `AWAY` take their sign from comparing the target's value with the source's (1921-1931).
   - `REMOVE` deletes the faction (`Integer.MAX_VALUE`).
   - `ADD` gives the DEFAULT value if the holder lacks the faction (1936-1942).
4. For `UP`, `DOWN`, `OPPOSITE`, `TOWARD` and `AWAY`, when the target has the faction or OUTSIDER is set (1950-1953): `amount = sign * round(rateModifier * base)`, then `amount = (int)(amount * factor)`, then `amount = round(amount * findFactor(source, gain))` (1954-1955; `findFactor` at 1256-1264 returns the first matching zapper factor's gain or loss multiplier, default 1.0). `MAXIMUM`, `MINIMUM`, `REMOVE` and `ADD` skip this step, so their factor and zapper multipliers never apply.
5. A zero amount with a non-zero factor returns early (1958-1959).
6. The change is sent through the room as `CMMsg.TYP_FACTIONCHANGE`. The ANNOUNCE text is the source message and the faction ID is the others message (`CMClass.getMsg`, `CMClass.java:3847-3850`; 1973-1974, 1978-1981). `StdMOB` applies it to the source (`StdMOB.java:3577-3584`). Any listener in the room can veto or rescale it in `okMessage`.
7. Relations: after a successful first message, every related faction receives `round(amount * relationFactor)` through the same route (1986-1996). Those messages do not fire events themselves, so there is no cascade.
8. With no room, the change is applied directly (`adjustFaction` or `removeFaction`, 2000-2004), and steps 6 and 7 are skipped.

Examples of magnitudes: with the default base of 100, a factor of `1%` moves the value by 1, `10%` by 10, `25%` by 25 and `1000%` by 1000. The `triumph.ini` rules use `1%` and the inclination rules use `5%` to `1000%`.

**Timed events.** `Factions.tick` (807-870) is registered by `MUD.java:1672` on `TICKID_MOB` with a repeat of 10 ticks, so with `TICKTIME=4000` (`coffeemud.ini:2151`) it runs every 40 seconds (`ServiceEngine.java:373`; `StdTickClient.java:35-38, 148-150`). It returns at once when no session is connected (809). For each faction without CUSTOMTICK it collects ADDOUTSIDER and TIME rules (772-804). A TIME rule with `ROUNDS=n` decrements a counter that is stored on the rule object (790-800), so the countdown is shared by all players, not per player. A TIME rule with no ROUNDS fires on every run. Each run then applies outsider rules to online players who lack the faction and timed rules to those who have it (848-861). Reaction factions with CUSTOMTICK are handled per session (830-847). Any exception ends the whole run, which is caught and logged (865-868).

**Reset chains.** `RESTIME=EVENT` on a rule resets the timers of another event for the same holders (1833-1886). A leading `-` is meant to block the other event by setting its next-allowed time to `Long.MAX_VALUE` (1874, 1881). See Problems for a bug in that test.

**Experience coupling.** `okMessage` (1749-1782) scales an experience message when a holder with an experience flag other than NONE kills someone. The value becomes `round(0.75 * xp + 0.25 * xp * ratio)`, so it ranges from 75% to 100% of the original. The ratio is a distance over the range width (1762-1776):

- EXTREME: `|victim - killer|`
- HIGHER: `|killer - minimum|`; LOWER: `|maximum - killer|`
- FOLLOWHIGHER: `|victim - minimum|`; FOLLOWLOWER: `|maximum - victim|`

`alignment.ini` uses EXTREME (`alignment.ini:118`); inclination uses NONE.

**Direct posting.** `Factions.postSkillFactionChange` (551-566) sends a change without the event table, level scaling, factors, relations or announcement. It is used by druid chants (`Abilities/Druid/Chant_NaturalBalance.java:163-165`), `Paladin_RighteousDefense.java:120-125`, `Prayers/Prayer_MalignedPortal.java:460-462` (alignment -100 and +100), `Prayers/Prayer_ReligiousDoubt.java:212` (inclination -25) and `Spells/Spell_Delude.java` (sets alignment toward a value).

### 4. Effects on abilities

**Usage gates.** A usage is written `masks;low;high`. A `!` before `low` means the band is negated (`DefaultFactionAbilityUsage`, 3445-3458). The masks are ACODE names (such as `CHANT`), DOMAIN names, FLAG names (with `!` meaning must-not) or an exact ability ID (3467-3516). All flags in a mask must be present, because `CMath.bset` requires every bit (`core/CMath.java:734-737`).

- `getAbilityUsage` returns the first usage that matches an ability and caches the result (1199-1226).
- `canUse` denies when the holder's value is outside `[low, high]`; for a negated band it denies when the value is inside (1235-1253).
- `StdAbility.getInappropriateFaction` (2872-2886) walks the holder's factions and returns the first one whose usage denies the ability. `appropriateToMyFactions` (2865-2870) wraps it.
- `usageFactorRangeDescription` (2008-2047) lists the bands that an ability needs. It feeds help text (`Libraries/MUDHelp.java:791`, `WebMacros/AbilityData.java:1492`).

Prayer gating is the main consumer. `CharClasses/Paladin.java:447-456` fails a prayer that is not appropriate to a paladin's factions, or is not holy enough, with a probability tied to wisdom; the failure message is about the god denying the prayer. `CharClasses/Cleric.java:450-470` returns true at once if the prayer is appropriate, and otherwise applies a quality check with messages such as "Your immoral strife disrupts the prayer." (461).

The `alignment.ini` usages (247-268) show the pattern. `HOLY UNHOLY;-4499;4500` needs both flags, which marks neutral-only prayers. `HOLY !UNHOLY;4501;10000` is holy-only for good holders. `UNHOLY !HOLY;-10000;-4500` is unholy-only for evil holders. `PALADIN_UNHOLYSTRIKE;-10000;-4500` gates one ability by ID.

**Listeners that alter faction changes.** These sit in ability `okMessage` or `executeMsg`, so they see the same `TYP_FACTIONCHANGE` message as any other listener.

- `Paladin/Paladin_Purity.java:66-88` vetoes an alignment or inclination loss for a holder whose faction is appropriate (returns false at 86), with a message about purity protecting the holder.
- `Paladin/Paladin_Corruption.java:66-88` is meant to amplify losses. It sets `msg.setValue(msg.value()*2)` (86) and then returns false (87), which vetoes the message. See Problems.
- `Prayers/Prayer_TaintOfEvil.java:104-120` halves positive alignment gains in `executeMsg` (118). See Problems on ordering.
- `Thief/Thief_LetterOfMarque.java:91-106` flips the sign of inclination losses when there is no legal authority or the holder is outside its metro area (102-104).
- `Properties/Prop_ModFaction.java:168-200` applies a math formula to the amount (200) for mobs, items, rooms and areas. It can be limited to gains, to losses, to one faction, or to factions that carry reactions.
- Other listeners that match `TYP_FACTIONCHANGE` were found by grep but not read: `Abilities/Misc/BadReputation.java:129`, `Abilities/Skills/Skill_CulturalAdaptation.java:126`, `Skill_Diary.java:350`, `Skill_CombatLog.java:335`, `Archon/Archon_CRecord.java:346`, `Misc/InstanceArea.java:1540`.

### 5. Effects on reactions (NPC attitudes)

- **Reaction items** are `REACTION=RANGECODE;MOBMASK;ABILITYID;PARMS` (`DefaultFactionReactionItem`, 3331-3347). `<TARGET>` in the parameters is replaced with the holder's name (3310-3312; applied at 2987).
- **Grouping by band.** When the value enters a new band, `setValue` rebuilds the reaction sets for that band only, grouped by identical mask (2895-2922). The active reactions are therefore a function of (faction, band).
- **Trigger.** When the holder enters a room (`TYP_ENTER`, 3016-3045), each NPC in the room that passes the mask (`maskCheck(…, M, true)`) gets the current band's reactions (`setPresenceReaction`, 2969-3007). When the holder leaves (`TYP_LEAVE`, 3063-3082), they are removed. An NPC who enters a room with the holder gets them too (3046-3061).
- **Two carriers.** In full mode (`USELIGHTREACTIONS=false`) each NPC gets a `PresenceReaction` effect (2994-3004), which shuts itself down on a tick once the holder is no longer in the same room (`Abilities/Misc/PresenceReaction.java:304-332`). In light mode (`true`) a copy is kept in the holder's data (`lightPresenceAbilities`, 2992-2998) and removed on leave (3064-3082). Light mode is cheaper but depends on the holder's data object.
- **PresenceReaction** (`Abilities/Misc/PresenceReaction.java`). Each entry is `ID=parms` (84-133). Abilities get their misc text set and become non-uninvokable. Behaviours are started on the NPC. A `Mood` ability has its previous mood saved and restored on shutdown (226-240, 189-224). Unknown IDs are dropped (84-120).
- **Example.** `resources/examples/areareaction.ini:274-285` gives NPCs in the `_HATED` band a mood of "mean" and, under a chance term in the mask (`-CHANCE 95`), an aggressive behaviour. Lines 282-285 give `_LOVED` NPCs a greeting, a MudChat behaviour and a happy mood, and line 285 gives `_HERO` NPCs `Spell_AweOther`. `areareaction.ini:260-261` add a `Spell_WellDressed` effect with parameter 4 or 10 to NPCs whose mask matches the area and the band.

### 6. Relations

`addRelation(id, factor)` and `getRelation(id)` (490-505) store a double multiplier per related faction. The ini form is `RELATION=OTHERID;PCT` (618-632). The relation is applied once, after the first change succeeds, as described in step 7 of the amount section (1986-1996).

## Content worth mining

| File | What it models | Bands | Rules (summary) |
|---|---|---|---|
| `resources/factions/alignment.ini` | Good-evil axis, -10000 to 10000 | Seven named bands (lines 100-106) | MURDER with OPPOSITE 100% (line 232); two paladin gain and loss rules (233-234); about 31 ability usage gates (247-277): holy and unholy flag combinations and paladin skills by ID; AUTODEFAULTS gives -10000 to a mask of demon and devil races and 0 to everyone else (line 69) |
| `resources/factions/inclination.ini` | Lawful-chaotic axis, -10000 to 10000 | Seven bands, PURECHAOTIC to PURELAWFUL, with MODERATE in the middle | 54 CHANGE lines: ARRESTED DOWN 1000%; MURDER OPPOSITE; a TIME drift UP 10% every 150 rounds (line 233), which is about 100 minutes at default settings; domain and flag triggers for crafts, crimes and fighting styles; 27 social triggers at 5% (CHANGE25-51, lines 255-281), 11 raising and 16 lowering; about 17 ability-ID triggers (CHANGE10-23 at lines 240-253 and CHANGE52-54 at lines 282-284), which do not match (see Problems); ability usage gates at lines 297-327 |
| `resources/factions/triumph.ini` | Per-instance achievement score, 0 to 999999 | Five bands from "Struggling to Triumph" through "Triumphant I!" to "Triumphant IV!" (lines 105-109) | Area kills at PCT 50, 80 and 100 (lines 235, 237, 238); assassination at PCT 100 (line 236); exploration at PCT 80 (line 239) and PCT 0 (line 250); timed windows WITHIN 750, 600, 450, 300 and 150 ticks, each on a kill rule and an exploration rule with its own ANNOUNCE line (lines 240-249); DYING DOWN 1% (line 234); WAIT=9999 on most rules, about 11 hours at the default tick, which makes them effectively once; USELIGHTREACTIONS=true (line 293). Line 241 is CHANGE8, which looks malformed (see Problems) |
| `resources/examples/reputation.ini` | Fame, 0 to 200000 | Nine named bands from "unknown" to "legend" (lines 101-109) | MURDER UP 100% (line 234); TIME DOWN 1% (line 235), a steady decay of 1 point every 40 seconds; RANGE6 appears three times (lines 106-108), which is a bug (see Problems) |
| `resources/examples/areareaction.ini` | Attitude of NPCs in one area toward a player | Hated, Villain, Neutral, Disliked, Liked, Loved, Hero (lines 105-111) | Six CHANGE rules (lines 234-239): TIME decay, KILL, MUDCHAT, ARRESTED, BRIBE, ADDOUTSIDER; 27 REACTION lines (274 onward); two AFFBEHAV lines (260-261) |
| `resources/examples/namereaction.ini`, `racereaction.ini`, `planarreaction.ini` | The same template for individual NPC names, races and planes | Same shape as areareaction | Same shape; grep only for these three, not read |
| `resources/examples/deityreaction.ini` | Deity reaction faction using message-type triggers | Not read in detail | CHANGE1-2 use MSG_HOLYEVENT with TGT=ME and CHANGE3 uses MSG_REBUKE with a target-name filter (lines 234-236); this is the intended way to scope a message rule to its holder |

The social triggers in `inclination.ini` (CHANGE25-51, lines 255-281) are a neat way to make ordinary emotes into small moral signals. Note that all of the rules on the socials are in the same file, so a Python port can keep social-to-amount tables as plain data.

## Notable design ideas

- **One scalar, many consumers.** The band name is the public word for a standing. The same band lookup gates abilities (`canUse`), selects reactions (`setValue`) and chooses automatic reaction factions, so a single number drives three systems.
- **Rules as data.** Each rule is a trigger, a direction, a factor, a target mask and a few flags. The direction set is what makes this expressive: OPPOSITE turns a kill of a good mob into a push towards evil, and TOWARD and AWAY express "become more like" and "become less like" without extra code.
- **Recency as state.** `WITHIN` measures how recently a prerequisite event happened, and `RESTIME` chains events so that one event opens a short window for another. The triumph file uses this for "Blindingly fast kills", which is time-pressure achievement with no separate timer system.
- **Thresholds of coverage.** `AREAKILL` and `AREAEXPLORE` count per player against area population or room count, so "kill 80% of the area" or "explore 80% of it" is one rule.
- **Templated reaction factions.** Area, name, race, deity and plane factions are generated from a template with `<CODE>` and `<NAME>` substitution, so NPC attitudes toward places and people come from data alone.
- **Two reaction carriers.** Full and light modes trade permanence for cost. The choice is one flag per faction.
- **One-hop relations.** Relations propagate one level only, which avoids cycles and makes the result easy to predict.
- **Message-bus integration.** The faction is itself a listener, so abilities, properties and behaviours can veto or rescale changes (Paladin_Purity, Prop_ModFaction) without any code in the faction.
- **Gating by flags and band together.** Usage masks combine ability flags (HOLY, UNHOLY, LAW, CHAOS) with a band. One table then states "paladins may use this only when good".

## Problems and cruft

Each item is a defect found by reading. None was reproduced.

**Defects with a clear mechanical effect**

1. **Ability-ID triggers never match.** `findAbilityChangeEvents` looks up `changes` by the ability's exact ID (`DefaultFaction.java:1057-1058`, `1081`). Ability IDs are mixed case, for example `Prayer_Bury` (`Abilities/Prayers/Prayer_Bury.java:41`) and `Skill_Arrest` (`Abilities/Skills/Skill_Arrest.java:40`). Change keys are stored upper-case (`DefaultFaction.java:2097-2102`), and `setEventID` keeps the file's spelling through its case-insensitive `CMClass.getAbility` lookup (2442-2444). The event is therefore stored under `PRAYER_BURY` and never matched by `Prayer_Bury`. This affects about 17 ID-style rules in `inclination.ini` and two in `alignment.ini` (`PALADIN_RIGHTEOUSDEFENSE` and `PALADIN_EXPLOITINGAURA`). Class, domain and flag triggers still work, because they use integer filters. Usage gates still work, because they compare with `equalsIgnoreCase` (`DefaultFaction.java:1211`).
2. **Domain usage gates never match.** `setAbilityFlag` stores the DOMAIN index unshifted (`DefaultFaction.java:3489`). `getAbilityUsage` compares it with `classificationCode() & ALL_DOMAINS` (1218), and that value holds `index << 5` (`Abilities/interfaces/Ability.java:722-740`). The event filter does shift (1020, 2428), so only the ability-usage path is wrong.
3. **Negation-only masks never apply.** A usage such as `!HOLY` alone leaves `flagsBitmap` at -1 and sets only `notFlagsBitmap`. `possibleAbilityID` is then set (3513-3514), so `getAbilityUsage` treats the mask as an ability ID and skips every ability (1210-1212).
4. **Usage caches go stale.** `addAbilityUsage` and `delAbilityUsage` clear `abilityUseCache` (2754, 2764) but not `abilityUseMisses` (1205, 1224). A usage added at run time therefore does not apply to abilities that were already recorded as misses, until the faction is reloaded. Editing an existing usage with `setAbilityFlag` (3467) leaves both caches stale.
5. **Corruption vetoes instead of amplifying.** `Paladin_Corruption.java:86-87` doubles the loss and then returns false. The message says the corruption "enhances" the influence, but the change is blocked. Compare `Paladin_Purity.java:86`, which vetoes on purpose.
6. **Default-path rules are not scoped to the holder.** The default branch (`DefaultFaction.java:1611-1664`) has no holder test, unlike the death, ability and social branches (1324, 1691, 1712). Every holder in the room that receives the message applies the change again, unless the rule carries `TGT=ME` or `SRC=ME` (1640-1648). `Room.send` delivers to all occupants (`StdRoom.java:1734-1738`). The shipped deity template mostly relies on `TGT=ME` (`resources/examples/deityreaction.ini:234-235`); line 236 filters by target name instead. The problem is latent for `MSG_*` rules that omit a holder filter.
7. **Level-scaling cap uses the wrong constant.** `levelFactor` is capped at `levelLimit` (EXPRATE, default 6) rather than at 1 (`DefaultFaction.java:1822-1824`). The evident intent is a base of 200 at a level gap of 6 and above. With this code the base rises linearly to 700 at a gap of 36 and stays there, so a target 36 or more levels above the source gives 3.5 times the intended change. With EXPRATE=0 the `levelLimit>0` branch is skipped, so any lower-level target gives 0 (1817-1826). `coffeemud.ini:403-406` says 0 means no bonus or penalty.
8. **Timer-reset sign test is applied to the whole list.** The loop iterates over the items of `RESTIME` (1836), but the leading `-` is tested on the entire string (1849). For `A,-B` the `-` is ignored and `-B` is looked up as is. For `-A,B` the second item is looked up with its first letter removed (`B` becomes an empty string, and `BAR` becomes `AR`).
9. **Window timestamps move for other events.** `resetEventTimers` sets `birthTime` on every call (`DefaultFaction.java:2829`), and `getEventTime` returns `birthTime` for events that have never been reset (2821-2822). Resetting one event therefore moves the `WITHIN` window of every other event that has never been reset. `Faction.java:1462-1468` documents the timestamp as the last reset of that event.
10. **Duplicate ini keys are dropped silently.** The loader uses `Properties.put` (`DefaultFaction.java:547-549`). `examples/reputation.ini:106-108` has three `RANGE6` lines, so only "world hero" survives. The bands for 50000 to 99999 and for 140000 to 149999 are then undefined, and `setValue` logs "does not define a range" (2896).
11. **File order is discarded.** Keys are enumerated from a `Properties` object (590), so `FACTOR`, `ABILITY`, `REACTION` and `CHANGE` lines are applied in hash order. `findFactor` (1258-1263) and `getAbilityUsage` (1208-1223) return the first match, so overlapping entries depend on hash order.
12. **Boundary values belong to two bands.** Ranges are inclusive at both ends. In `areareaction.ini:105-111`, the values -8000, -1500, -500, 500, 1500 and 8000 each fall in two bands. `fetchRange` (1117-1131) returns whichever band is found first.
13. **Random values never give the minimum.** `randomFaction` (1161-1162) and `FRange.random` (2725-2726) compute `max - nextInt(max - min)`, which yields min+1 through max. With min equal to max, `nextInt(0)` throws.
14. **The "all factions" posting is dead and broken.** `postChangeAllFactions` passes `"" + quiet` as the faction ID (`Factions.java:537`). `StdMOB` then calls `adjustFaction` with that string (`StdMOB.java:3578-3584`), which finds no faction and does nothing. Nothing calls the method. It also returns true with no room, without doing anything (545-547). `postSkillFactionChange` has the same no-room return (564-565).
15. **Rescaling after the change on TaintOfEvil.** `Prayer_TaintOfEvil.executeMsg` rescales the value (`Prayers/Prayer_TaintOfEvil.java:118`). `StdMOB` applies the faction change at `StdMOB.java:3584`, and effect listeners run later at `StdMOB.java:3963`, inside the same method. The halving therefore probably has no effect on the applied amount. This is unverified at run time.

**Contract mismatches and smaller issues**

- `findDefault` returns 0 when nothing matches (`DefaultFaction.java:1175`). The interface says `Integer.MAX_VALUE` (`Faction.java:354-355`). ADD then adds 0, which is harmless but breaks the contract.
- `WITHIN=0` means no limit in code (`DefaultFaction.java:2549`). The interface says 0 means never (`Faction.java:990-994`).
- `asPercentFromAvg` returns a positive value when the faction is below the midpoint (`DefaultFaction.java:1155`). The doc is silent on the sign.
- The `CMMSG` trigger is listed (`Faction.java:1316-1318`) and accepted by `setEventID` (`DefaultFaction.java:2406-2410`), but nothing ever fires it.
- Announcements are posted to channels before the veto check (`DefaultFaction.java:1961-1972` against 1978). A vetoed change is still announced.
- With no room the change skips the veto check and relations (2000-2004).
- The archon relation editor writes "no change" and then replaces the relation with the parsed invalid input, which is 0 (`Factions.java:1349-1353`).
- `makeReactionFaction` removes the template from the cache (`Factions.java:582`) with no visible reason. Generated factions are never removed (589-593, 603-607, 659-661, 679-681, 709-711), so every distinct name, race or deity adds one faction for the life of the process.
- `OPPOSITE`, `TOWARD` and `AWAY` compare against `Integer.MAX_VALUE` when the source lacks the faction (`DefaultFaction.java:1787`, 1915-1916; `StdMOB.java:5829`). The subtraction at 1915-1916 can overflow. Not run.
- `AREAKILL` and `AREAASS` call `data.getCounter` without a null check (`DefaultFaction.java:1373-1375`, 1407-1409). `fetchFactionData` returns null for absent factions (`StdMOB.java:5834-5837`), and `applies` does not require the faction. A beneficiary who lacks the faction would raise a NullPointerException. Not run.
- `Factions.tick` catches all exceptions for the whole run (`Factions.java:865-868`), so one bad rule stops every timed rule for that run.
- `triumph.ini:241` (CHANGE8) closes its MASK string with `\" )` where its siblings close with `\" ")`. The effect is unverified, but it looks like a malformed quote.
- `triumph.ini:73` hard-codes a single mob name in an AUTODEFAULTS mask. This is data, not logic, but it is an unusual pattern to ship.
- The helper `parseChoifaults` (`DefaultFaction.java:395`) has a misspelled name.

## Takeaways for a Python SUD

**What to borrow**

1. **One scalar with named bands.** Store one integer per faction, with ordered band definitions. Validate at load time that bands cover the range with no gaps and no overlaps, then look up with `bisect`. Store the current band name, and emit a `BandChanged` event when it changes. That event is the hook for reactions and for "you have become X" messages.
2. **Rules as data.** Keep rules as small immutable records, in an ordered list, not a hash table. A rule is a trigger, a direction, a factor, an optional predicate over the NPC or player, a window, a cooldown, a chance and an announce string.
3. **A small direction set.** UP, DOWN, OPPOSITE and TOWARD cover most of the shipped data. MAX and MIN are useful for "maxed out" events. Drop REMOVE and ADD unless the SUD has a clear use.
4. **Band-keyed gates and reactions.** Gate each action with `(lo, hi, negate, required_tags, forbidden_tags)`. Key reactions by band code. Keep one matching function and normalise IDs to lower case at load time.
5. **Windows and cooldowns in game time.** `WITHIN`, `WAIT` and TIME-style timers are the mechanism that makes this feel alive. In Python, store timestamps per holder and per rule in the holder's state.

**Sketch**

```python
import bisect
from dataclasses import dataclass
from enum import Enum

class Dir(Enum):
    UP = "up"; DOWN = "down"; OPPOSITE = "opposite"; TOWARD = "toward"
    AWAY = "away"; MAX = "max"; MIN = "min"

@dataclass(frozen=True)
class Band:
    low: int; high: int; name: str; code: str

@dataclass(frozen=True)
class Rule:
    trigger: str                     # normalised lower-case key
    direction: Dir
    factor: float = 1.0              # 1.0 == BASE points
    within: int | None = None        # recency window, in game turns
    cooldown: int = 0                # turns between firings per holder
    chance: float = 1.0
    announce: str | None = None

class Faction:
    BASE = 100
    def __init__(self, fid, lo, hi, bands, rules):
        self.fid, self.lo, self.hi = fid, lo, hi
        self.bands = sorted(bands, key=lambda b: b.low)
        self._lows = [b.low for b in self.bands]
        self.rules = list(rules)                       # ordered, not hashed
        self._validate()                               # check no gaps or overlaps (not shown)

    def band_for(self, value: int) -> Band:
        return self.bands[bisect.bisect_right(self._lows, value) - 1]

    def delta(self, rule: Rule, value: int, other: int | None = None) -> int:
        if rule.direction is Dir.MAX: return self.hi - value
        if rule.direction is Dir.MIN: return self.lo - value
        sign = 1 if rule.direction is Dir.UP else -1   # other directions omitted
        return round(sign * self.BASE * rule.factor)
```

The `delta` method omits OPPOSITE, TOWARD and AWAY for brevity; those need the other party's value. The point is that every rule reduces to one integer delta, then one clamp, then one announce.

**What to simplify**

- Drop the OUTSIDER, SELFOK and beneficiary machinery. In a single-player game there is one actor who changes things, and the NPCs are the other side. Keep only "subject" and "object" in each rule.
- Drop the XP coupling unless the SUD has experience. Keep level scaling as an optional difficulty gap, implemented with a real cap.
- Drop per-mob zapper masks. Use Python predicates over the NPC or player object, or small tag sets.
- Drop relations at first. Add them later as a one-hop table if needed.
- Keep AFFBEHAV only if NPCs need buffs tied to standing; it is monster-only in the source.
- Keep the reaction carriers as one mechanism. Light and full modes are an optimisation the SUD may not need.

**What to drop**

- The `Integer.MAX_VALUE` sentinel. Use an explicit "has faction" set or `None`.
- The message-bus plumbing as such. The SUD can call explicit methods on a small event bus (for example `KillEvent`, `EnterRoom`, `Say`, `Social`, `AbilityUsed`, `Bribe`), which avoids the holder-scoping bugs in Problems.
- Ability-ID triggers as strings. Use typed references to the ability objects, or canonical lower-case IDs validated at load time.

**Multiplayer assumptions to rethink**

- "OUTSIDER" (a target who lacks the faction) and the target-holder dance assume many actors. In the SUD, decide per rule whether the NPC or the player holds the standing.
- Group kills and "combat beneficiaries" assume players who fight together. A single-player SUD needs one subject per kill.
- Area population counters assume many players share the population. For a single player, the counters can be per area instance and per player, which is simpler.

**Time base**

The time base is undecided. In a turn-based SUD, `WITHIN`, `WAIT` and TIME become turn counts. In a real-time SUD they become seconds. Either way, choose one clock, then express every rule window in it. Do not reuse the 4-second tick, and do not count runs of a periodic tick the way `ROUNDS` does.

**Content to reuse**

- The band shape of alignment (seven bands, symmetric around zero) and the rule ideas behind it: a kill moves the killer away from the victim's side, and a loss of standing can be amplified or blocked by a listener.
- The triumph design: per-area scoreboards with time-limited windows and "achieved" messages. This maps well onto a living-world SUD with area-based goals.
- The reputation decay (TIME DOWN) and the area attitude template (Hated to Hero) as a model for NPC attitudes toward a player, by place.

## Coverage

**Read in full:** `Common/interfaces/Faction.java` (1-1903), `Libraries/interfaces/FactionManager.java` (1-630), `Common/DefaultFaction.java` (1-3557), `Libraries/Factions.java` (1-2109). The archon editor (`Factions.java:1032-1940`) was read for structure but is not described in detail.

**Read in part:**

- `MOBS/StdMOB.java`: lines 144, 824-834, 1056-1068, 1328-1340, 2500-2515, 3412-3530, 3577-3600, 3963-3980, 4360-4390, 5770-5865.
- `Abilities/Misc/PresenceReaction.java`: lines 36-135, 189-260, 304-380.
- `Abilities/StdAbility.java`: 2865-2890.
- `CharClasses/Paladin.java` 440-460 and `Cleric.java` 440-475.
- `Abilities/Paladin/Paladin_Corruption.java`, `Paladin_Purity.java`, `Prayers/Prayer_TaintOfEvil.java`, `Thief/Thief_LetterOfMarque.java` and `Properties/Prop_ModFaction.java`: the listener methods only.
- `Locales/StdRoom.java` 776-788 and 1734-1739; `application/MUD.java` 1664-1676; `core/threads/ServiceEngine.java` 365-402; `StdTickClient.java` 35-40 and 134-160; `core/CMClass.java` 2040-2085 and 3847-3850; `core/CMMsg.java` 1150; `core/CMath.java` 734-737; `Abilities/interfaces/Ability.java` (flags, ACODE, DOMAIN: 671-740, 893-935, 1036-1075).
- Configuration: `coffeemud.ini` (EXPRATE, AUTOREACTION, FACTIONS, TICKTIME).
- Data: `resources/factions/alignment.ini`, `inclination.ini` and `triumph.ini` (non-comment lines); `resources/examples/reputation.ini` and `areareaction.ini` (ranges, changes, reactions and affects). `namereaction.ini`, `racereaction.ini`, `planarreaction.ini` and `deityreaction.ini` were checked by grep only. `factiontemplate.ini` was not read.

**Found by grep only, not read:** the other `TYP_FACTIONCHANGE` listeners (`BadReputation`, `Skill_CulturalAdaptation`, `Skill_Diary`, `Skill_CombatLog`, `Archon_CRecord`, `InstanceArea`), the consumers in `Commands/FactionList.java`, `WebMacros/FactionData.java`, `WebMacros/grinder/GrinderFactions.java`, `Libraries/CMGenEditor.java`, and `CharCreation.java`'s choice flow.

**Skipped:** the `Prop_ModFaction` editor rules, the `Score` display, and the `resources/help` entries for factions. Nothing was run.
