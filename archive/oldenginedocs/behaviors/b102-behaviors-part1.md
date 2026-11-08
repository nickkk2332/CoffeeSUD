# Behaviors, part 1: the legal engine, combat AI and small NPC routines

> Part of the CoffeeMud NEWOVERVIEW. Bucket: `b102` (Behaviors [part 1/5: Behavior .. CombatAbilities]) · 16 files · 7908 lines of Java.

Citations are relative to `com/planet_ink/coffee_mud/`, which is dropped from the path. Paths under `resources/`, `coffeemud.ini` and `NEWOVERVIEW/` are relative to the CoffeeMud folder. Everything here comes from reading source. Nothing was run, so anything inferred from a reading is marked as such.

## Summary

This bucket holds CoffeeMud's largest behaviour, `Arrest` (4,148 lines). It is a complete law-enforcement simulation attached to an Area: it watches game messages for crimes, files warrants against mobs and players, and walks officers through a 15-state pipeline covering arrest, escort, trial, jail, parole, fines, shaming, banishment, execution and release. `CombatAbilities` (1,066 lines) is the NPC fighting brain. It chooses skills by a combat-mode probability table, scores aggro, switches targets, and wields weapons and wands. The other fourteen files are small, composable NPC routines: aggression with bribes, joining fights by alignment, clan or brotherhood, a toll-gate guard, a beggar, a vagrant-shooer, and two caster presets. Most of the legal rules are data in `resources/laws.ini`, and the small behaviours take their settings as parameter strings, which is what makes them reusable.

## Key files and types

| File | Role | Lines |
|---|---|---|
| `Behaviors/Arrest.java` | Legal engine on an Area: crime detection, warrants, sentencing, enforcement state machine | 4148 |
| `Behaviors/CombatAbilities.java` | NPC combat brain: skill choice by mode, aggro, weapon and wand handling, precast | 1066 |
| `Behaviors/BribeGateGuard.java` | Toll-gate guard with a per-player balance ("tab") | 609 |
| `Behaviors/interfaces/LegalBehavior.java` | Legal API (warrants, officers, judges, fines) and timing constants | 370 |
| `Behaviors/Aggressive.java` | Attacks eligible inhabitants; owns the static `startFight` used by the helpers; bribe-to-be-left-alone | 353 |
| `Behaviors/interfaces/Behavior.java` | Behaviour contract, `CAN_*` and `FLAG_*` masks | 196 |
| `Behaviors/ClanHelper.java` | Joins fights where the target belongs to a rivalrous clan | 183 |
| `Behaviors/Beggar.java` | Asks visible players for money; thanks donors | 169 |
| `Behaviors/ActiveTicker.java` | Random-interval scheduler base class (`min`, `max`, `chance`, `mingrp`) | 166 |
| `Behaviors/AntiVagrant.java` | Wakes sleepers and stands sitters in city rooms; can kick them out | 162 |
| `Behaviors/BrotherHelper.java` | Joins fights against an attacker of a "brother" | 137 |
| `Behaviors/AlignHelper.java` | Joins fights on the side of a same-alignment victim | 115 |
| `Behaviors/Clericness.java` | CombatAbilities preset: cleric class, armour and mana trade-off | 68 |
| `Behaviors/Bardness.java` | CombatAbilities preset: bard class, armour and HP trade-off | 66 |
| `Behaviors/interfaces/ChattyBehavior.java` | Interface: last thing said, last MOB responded to | 54 |
| `Behaviors/interfaces/MobileBehavior.java` | Interface: suspend mobility for N ticks | 46 |
| `Behaviors/StdBehavior.java` (outside bucket, base class) | No-op base implementation | 410 |

Other classes that Arrest and its neighbours depend on, named here and not read in full: the `Law` and `LegalWarrant` interfaces and their defaults (`Common/`), the `LegalLibrary` behind `CMLib.law()`, and effect abilities `Skill_HandCuff`, `Prisoner`, `Banishment`, `Shaming`, `Skill_Track`, `Skill_ArrestingSap`, `Skill_Trip`, `Fighter_Behead`, `Prayer_Stoning`, `Fighter_Whomp`, `Skill_WandUse` and `Thief_BackStab`. Stat adjusters are `Prop_CombatAdjuster` and `Prop_StatAdjuster` (`Abilities/Properties/`).

## How it works

### 1. The behaviour contract

- A behaviour is attached to a host (MOB, Item, Room, Area) and does two jobs. It acts in `tick(Tickable, int)`, and it listens to messages through `okMessage()` (which may veto) and `executeMsg()` (which reacts afterwards). `Behaviors/interfaces/Behavior.java:44`, doc `:35-43`. In this bucket only `Aggressive` and `BribeGateGuard` veto, at `Aggressive.java:68-87` and `BribeGateGuard.java:285-403`.
- Flags declare traits to the rest of the engine: `FLAG_MOBILITY` 1, `FLAG_TROUBLEMAKING` 2, `FLAG_POTENTIALLYAGGRESSIVE` 4, `FLAG_LEGALBEHAVIOR` 8, `FLAG_POTENTIALLYAUTODEATHING` 16 (`Behavior.java:187-195`). `Aggressive` returns `POTENTIALLYAGGRESSIVE | TROUBLEMAKING` (`Aggressive.java:45-48`). `Arrest` returns `LEGALBEHAVIOR` (`Arrest.java:49-53`). Being a troublemaker has legal consequences (see 2.3 and 2.4).
- `StdBehavior` is the no-op base. Its `tick` returns true unless the host is destroyed (`StdBehavior.java:297-302`). `parms` is a raw string (`:89`, `:220-223`). `canImprove` tests `CAN_*` masks (`:256-279`).
- `ActiveTicker` (`ActiveTicker.java:49-166`) turns a behaviour into a random-interval actor. Defaults are `minTicks=10` and `maxTicks=30` (`:49-50`). Parameters `min=`, `max=`, `chance=` and `mingrp=` are read in `setParms` (`:74-82`). `canAct()` decrements `tickDown` on area, MOB, item-behaviour and room-behaviour ticks (`:136-165`). On expiry it re-rolls in `tickReset()` (`:57-71`) and then rolls the chance percentage (`canChance`, `:128-134`).
- Arrest runs only on the area tick (`Arrest.java:2763-2766`). `CombatAbilities` runs on the MOB tick.

### 2. Arrest: the legal engine

#### 2.1 Data model

- Arrest implements `LegalBehavior` (`Behaviors/interfaces/LegalBehavior.java:47`) and attaches to an Area (`CAN_AREAS`, `Arrest.java:56-59`). Its own state is small: caches, a banned-item cache, a tick set and the per-area law object (`Arrest.java:61-73`).
- The rules live in `resources/laws.ini` by default (`Arrest.java:69`). The behaviour's parameter string is `lawName;KEY=value ...`. Text before the `;` names the rule file, and the extra pairs override it (`Arrest.java:620-641`, merged at `:772`). A name of `custom` stores the rule text per area in the database, seeded from `Law.defaultLaw` (`Arrest.java:701-706`, `:733-752`, default at `:745`).
- `DefaultLawSet` reads scalar keys in `resetLaw` (`Common/DefaultLawSet.java:770-800`). They include OFFICERS, JUDGE, JAIL, RELEASEROOM, the message keys, PROTECTED and TRESPASSERS masks, ARRESTMOBS (`:782`) and ACTIVATED (`:774`). Crime keys are sorted in `initialize` (`:873-923`):
  - Nine fixed basic crimes: MURDER, RESISTINGARREST, PRISONBREAK, NUDITY, ASSAULT, ARMED, TRESPASSING, PROPERTYROB and TAXEVASION (`:827` for the tax one, `:841-862` for the rest).
  - `CRIMEn` keys: crimes spotted in the text of an emote or social (`:877-879`, matched in `Arrest.java:2699-2718`).
  - `BANNEDn` keys: banned items, matched by item name or material (`:896-906`, `Arrest.java:2284-2326`).
  - Any other key that names an ability ID, an ability type or a domain is an ability crime (`:915-922`). Lookup tries the ability ID, then its type, then its domain (`DefaultLawSet.java:317-330`). This is why `resources/laws.ini` keys such as `THIEF_SWIPE` or `PRAYER_MALIGNEDPORTAL` work with no code change.
- Each crime value is `locations;flags;name;sentence;warning`. The format is documented at `resources/laws.ini:127-160`. The sentence is a base word (WARNING, THREAT, PAROLE1 to PAROLE4, JAIL1 to JAIL4, DEATH) followed by optional modifiers: SEPARATE, SKIPTRIAL, DETAIN=, FINE=, NORELEASE, PUNISHCAP=, SHAME=, BANISH= (`Common/interfaces/Law.java:642-692`).
- A warrant is a `LegalWarrant` (`Common/interfaces/LegalWarrant.java:56-383`). It holds the criminal, victim, witness, crime text, arresting officer, state, punishment code and per-punishment parameters, jail room and jail time, release room, offense count, last-offence time, travel timer and ignore-until time. The default class is `Common/DefaultArrestWarrant.java`, where `offenses` starts at 0 (`:101`).
- The punishment code is a base value plus flag bits. Base values are WARN 0, THREATEN 1, PAROLE1-4 2-5, JAIL1-4 6-9 and EXECUTE 10 (`Law.java:615-637`). Flag bits are SEPARATE 256, SKIPTRIAL 512, DETAIN 1024, FINE 2048, NORELEASE 4096, PUNISHCAP 8192, SHAME 16384 and BANISH 32768 (`Law.java:657-671`). The sentence is parsed in `Arrest.java:2211-2250`.
- Warrant states are integers 0 to 14 (`Law.java:696-724`), listed in 2.5.

#### 2.2 Noticing crimes

`executeMsg` (`Arrest.java:2473-2720`) does nothing unless the area's law is enabled and activated (`:2478-2484`). It then checks the message in this order:

1. A death caused by a MOB is murder, unless the dead party is a monster, a troublemaker and not an officer (`:2490-2497`). If a monster killed an officer, its non-monster group leader is also charged (`:2499-2504`).
2. An officer or judge entering a room triggers `testEntryLaw` on every other occupant (`:2508-2528`). Officers and judges are otherwise exempt, and the method returns (`:2528`).
3. Monster sources are ignored unless `ARRESTMOBS` is true (`:2530`; laws.ini `:77`).
4. A skill whose ability ID, type or domain is a crime key is a crime (`:2541-2555`). Teaching is excluded (`:2544`).
5. A criminal who quits while an officer holds their warrant gets RESISTINGARREST (`:2562-2589`).
6. Malicious actions against a MOB (`:2591-2686`):
   - Against a judge: nearby officers say "Ack! Treason! Die!" and turn on the attacker (`:2600-2620`).
   - Against an officer by a suspect whose arresting officer is in the room: RESISTINGARREST (`:2654-2669`).
   - Otherwise assault, unless the target is a troublemaker and the source is a player, or the act is a counter-attack on the accuser (`:2671-2684`, `turnAbout` at `:2644-2651`).
7. Others-messages (`:2688-2719`): a banned item taken (`:2691-2694`), an entry check (`:2696-2697`), and `CRIMEn` phrase matches (`:2699-2718`).

`testEntryLaw` (`:2328-2447`) runs on entry and during play:
- NUDITY (`:2330-2353`): a non-monster with wear slots for legs, waist and body, and nothing worn on them.
- ARMED (`:2356-2384`): for a player, or a monster from outside its area, a wielded weapon that is not natural, hammer or staff, seen, while peace time exceeds twice `END_SHEATH_TIME`. Nothing may have been fought in the last 30 000 ms, checked against the session's NPC and PK fight times (`:2370-2372`).
- TRESPASSING (`:2386-2408`): the TRESPASSERS mask (laws.ini `:83`, shipped as `-RACE +Undead`), or a possibly aggressive monster from another area that is not following a monster, and is not following a player who is in the same room. Protected victims are excluded. The room-based clause is commented out as a "useless feature" (`:2388`).
- Banned items (`:2409-2446`). Each mob name is checked once (`bannedMOBCheck`, `:2411-2422`). The check is skipped 99% of the time (`rollPercentage() > 1`, `:2413-2419`) only for a non-player who starts in the same area; everyone else is checked. The check looks at `numItems/5` random items, with a floor of 10 once the mob has 10 or more (`:2423-2424`). Containers carried by the mob's group and rideables are searched too (`:2432-2443`).

Monsters' affect effects are checked at the tick. `tickMobsSet` collects MOBs that acted (`:2557-2560`), and `findAffectCrime` runs on their effects (`:2780-2811`).

#### 2.3 Filing a warrant

`fillOutWarrant` (`Arrest.java:1952-2269`) is the one gate every crime passes through. Each rejection returns false and writes a debug line under `DbgFlag.ARREST`.

1. Dead, not in the world, or outside the area (`:1962-1979`).
2. Officers, judges and staff with the ABOVELAW security flag are immune (`:1981-1989`).
3. Witness search (`:1992`, `getWitness` at `:818-839`). NPCs in the room, then in each adjacent room, with INT above 3, pass when `rollPercentage() >= threshold`. The threshold is 25 for chaotic accused (about 76%), 50 for neutral (51%) and 95 for lawful (6%) (`:811-812`). Officers always witness (`:812`).
4. Victim equals criminal: rejected (`:1999-2004`).
5. Crime flags (`:2007-2094`):
   - `WITNESS` requires the witness in the same room. `!WITNESS` drops the requirement (`:2013-2024`, `:2095-2100`).
   - `IGNOREn` ignores the crime with probability n% each time it is noticed (`:2028-2034`). `!IGNOREn` uses 100-n% (`:2037-2045`).
   - `COMBAT` and `!COMBAT` (`:2048-2068`).
   - `RECENTLY` and `!RECENTLY`: the previous offence of the same crime was under 600 000 ms, which is 10 real minutes, ago (`:2070-2091`, constant at `:2074`).
6. Location flags (`:2103-2173`): `INDOORS`, `HOME` (via `CMLib.law().doesHavePriviledgesHere`), and room-keyword matches against the display text, with `!` to forbid.
7. Protected victim mask (`:2176-2184`). The shipped mask, `-RACECAT +Humanoid +Elf +Dwarf +Halfling +Gnome "+Giant-kin"` (laws.ini `:74`), means a victim must match the listed races and categories to count.
8. A warrant for the same criminal, victim and crime already exists: rejected (`:2187-2198`).
9. The previous warrant for this crime is pulled from the old list so that its `offenses` count carries over (`:2199`). Otherwise a new one is made (`:2200-2201`).
10. Fields are filled, `lastOffense` is set to now (`:2204-2210`), and the sentence is parsed (`:2211-2250`). An unknown sentence word logs an error and rejects the warrant (`:2245-2248`).
11. A troublemaker victim halves the base sentence (`:2252-2255`; see Problems).
12. The warrant is added if it is still a crime and the witness can see the criminal (`:2257-2264`).

`addWarrant` (`:364-402`) sends a MSG_LEGALWARRANT to the criminal's room (`:380-386`), bumps the player statistic `STAT_WARRANTS` (`:390-391`), and tells WARRANTS-channel subscribers "@x1 has been accused of @x2." (`:395-398`).

#### 2.4 Deciding the sentence

- `isStillACrime` (`:974-1013`) returns false when the witness is dead (`:979`), when the witness or the victim is in the criminal's group (`:993-1004`), or when the offence is older than `EXPIRATION_MILLIS` (`:1006`). That constant is 7 real days (`Behaviors/interfaces/LegalBehavior.java:52`).
- `highestCrimeAction` (`:1191-1236`) computes the sentence actually handed down:
  1. Start from the warrant's own code. A SEPARATE warrant returns it unchanged (`:1193-1195`).
  2. Build the relevant list: the warrant itself, first, then the criminal's other warrants (`getRelevantWarrants`, `:1015-1031`). For each entry, if `base + offenses` beats the current base, set the sentence to `base + min(offenses, 3)` (`:1199-1205`).
  3. For each non-separate entry, add one step while the sentence is below `base + 4` (`:1206-1212`). The warrant itself is in the list, so this adds a step even for a first offence (see Problems).
  4. Clamp to EXECUTE (`:1213-1214`). Apply the PUNISHCAP parameter, which takes a number or a word (`:1215-1230`). Lower the result by one step for a good-aligned criminal (`:1233-1234`).
- `judgeMe` (`:1681-1949`) acts on the result:
  - WARN and THREATEN: a spoken message that lists the charges, witnesses and warnings. The case is closed (`:1691-1743`).
  - PAROLE1-4 and JAIL1-4: the matching laws.ini message is said, `jailTime` is set from `PAROLEnTIME` or `JAILnTIME`, and the state becomes PAROLING or JAILING (`:1745-1840`).
  - EXECUTE: the criminal stops following anyone, and the state becomes EXECUTING (`:1841-1852`).
  - Fines: `getFine` (`:1033-1056`) and "You are hereby fined @x1, payable to the local tax assessor." (`:1862`). The total is recorded in `finesAssessed` (`:1863-1868`).
  - Banishment: the `Banishment` effect for `getBanishmentTicks` (`:1058-1092`), announced as "You are hereby banished for @x1 days." (`:1894`).
  - Shaming: the `Shaming` effect for `getShameTicks` (`:1094-1124`), announced at `:1928`.
  - DETAIN: the state becomes DETAINING, and `judgeMe` returns false so the case continues (`:1938-1947`).
- Durations are days converted with `days * ticksPerMudHour * hoursInDay` (`:1091`, `:1123`). `ticksPerMudHour` is `MILLIS_PER_MUDHOUR / TIME_TICK` (`core/CMProps.java:3539-3541`), which is 600000 / 4000 = 150 with the shipped `MILLISPERMUDHOUR=600000` (`coffeemud.ini:1099`) and `TICKTIME=4000` (`coffeemud.ini:2151`). `HOURSINDAY` is 6 (`coffeemud.ini:1104`). A mud day is therefore 900 ticks, which is one real hour at defaults. A total of 255 days or more becomes permanent, `Integer.MAX_VALUE` (`:1088-1089`, `:1120-1121`), and the effect is made long-lasting (`:1890`, `:1924`).
- Jail and parole durations are plain ticks in laws.ini: JAIL1TIME to JAIL4TIME are 20, 40, 80 and 160; PAROLE1TIME to PAROLE4TIME are 40, 80, 160 and 320 (`resources/laws.ini:92-101`). At 4 seconds per tick, jail runs from 80 s to 640 s, and parole from 160 s to 1280 s.

#### 2.5 Enforcement pipeline

Arrest's area tick (`Arrest.java:2759-2870`) does four things:
1. Checks MOB effects collected since the last tick (`:2780-2811`).
2. Runs `propertyTaxTick` (`:2813`).
3. Walks the warrants. A warrant whose criminal is missing or not located is skipped (`:2818-2824`). A warrant that is no longer a crime is closed and moves to the old list with `offenses + 1` (`:2826-2843`). If the criminal has no other warrants, the criminal is uncuffed and any arresting officer is dismissed (`:2828-2836`).
4. Handles each criminal once per tick: by name for non-separate warrants, by name and crime for separate ones (`:2845-2856`). Skips warrants whose ignore-until time is in the future (`:2860-2865`), and calls `processWarrant` (`:2867`).

`processWarrant` (`:2897-4105`) is a switch on the state. Each step first pauses the officer's mobile behaviours for one tick (`:2899-2908`). The states, with main branches:

| Value | State | Acting NPC | Main branches (lines in Arrest.java) | Next |
|---|---|---|---|---|
| 0 | SEEKING (`:2911-2998`) | officer | Find an eligible officer in the room or walk one in from an adjacent room (`:2919`). A warning or threat is given on the spot (`judgeMe` with no judge, `:2947`). COP-KILLER or resisting cases go to SUBDUEING (`:2960-2977`). Otherwise "You are under arrest for ..." (`:2979`) | ARRESTING, SUBDUEING, closed |
| 1 | ARRESTING (`:2999-3055`) | officer | If the officer is fighting the suspect, go to SUBDUEING (`:3010-3016`). A sitting or sleeping suspect goes to SUBDUEING with "Good. Now hold still." (`:3033-3037`). After 1.5 ticks in the state, "I said SIT DOWN! NOW!" goes to SUBDUEING (`:3040-3044`). A lost officer returns to SEEKING | SUBDUEING, SEEKING |
| 2 | SUBDUEING (`:3056-3182`) | officer | A standing suspect gets Skill_ArrestingSap (code 10), then Skill_Trip (code 30), then a plain attack (`:3080-3094`). A sitting, sleeping or cuffed suspect is cuffed (`:3099-3125`), the judge is located (`findTheJudge`, `:3108`), and the suspect is walked there (MOVING) or goes straight to REPORTING if already in the judge's room (`:3140-3144`). With no judge the suspect is released (`:3151-3156`). Otherwise "Hmph." and back to SEEKING (`:3161-3166`) | MOVING, REPORTING, SEEKING |
| 3 | MOVING (`:3183-3232`) | officer | Walk to the judge with Skill_Track. Movement is raised to at least 50 for both (`:3193-3196`). A lost trail gives "Now where was that court?" and SEEKING (`:3207-3211`). Chit-chat on a 25% roll (`:3215`) | REPORTING, SEEKING |
| 4 | REPORTING (`:3233-3307`) | officer | If no judge is here, set the judge's room as destination and go to MOVING (`:3245-3248`). Otherwise announce the case and go to WAITING (`:3267`, `:3275`). EXECUTE sentences are announced on the EXECUTIONS channel (`:3276-3282`) | WAITING, MOVING |
| 5 | WAITING (`:3308-3369`) | officer and judge | `judgeMe` runs with the judge present. On completion the warrant is filed and the officer dismissed (`:3335-3343`) | outcome of judgement |
| 6 | PAROLING (`:3370-3423`) | judge | Warrants filed. The `Prisoner` effect runs for `jailTime` ticks (`:3390-3391`). "<S-NAME> put(s) <T-NAME> on parole!" (`:3387`). Officer dismissed, and a monster suspect wanders away (`:3398-3399`) | closed |
| 7 | JAILING (`:3424-3496`) | officer | Find a jail room (`findTheJail`, `:1531-1540`). Remove any `Prisoner`, cuff, and track to the jail with ability code 1 so the path is cached (`:3459`) | MOVING2, closed |
| 8 | EXECUTING (`:3577-3658`) | judge | Judge applies `Fighter_Behead` or `Prayer_Stoning` (`:3604-3611`). The suspect is held by `Prisoner` for 100 ticks (`:3603`). If neither applies, the judge attacks (`:3618-3619`). With the BANISH bit, go to BANISHING (`:3621-3624`) | BANISHING, closed |
| 9 | MOVING2 (`:3659-3730`) | officer | To the jail. On arrival, `Prisoner` for `jailTime + 5` ticks (`:3683`). If the effect did not take, go to RELEASE (`:3687-3694`) | RELEASE, closed |
| 10 | RELEASE (`:3899-4100`) | officer | Wait until `Prisoner` expires or the crime is pardoned. NORELEASE closes the case (`:3910-3916`). An officer is brought in from the jail area if needed (`getAnyEligibleOfficer`, `:3926-3928`). Walk to the release room (`getReleaseRoom`, `:1380-1398`). The BANISH bit goes to BANISHING (`:3970-3972`). A PRISONBREAK warrant is filed if the criminal left jail without release (`:4064-4098`) | closed, BANISHING, SEEKING |
| 11 | MOVING3 (`:3731-3824`) | officer | To the detention centre. On arrival, `Prisoner` for `jailTime + 5` ticks (`:3763-3765`) | RELEASE |
| 12 | DETAINING (`:3497-3576`) | officer | Find the detention room (`getDetainRoom`). Set the sitting disposition on the suspect (`:3524-3525`). Go to MOVING3 | MOVING3, closed |
| 13 | BANISHING (`:3825-3898`) | officer | Find a banishing point (`findTheBanishingPoint`, `:1553-1679`). Cuff, then walk to it | MOVING4, closed |
| 14 | MOVING4 (same case as 11, `:3731-3824`) | officer | Walk the suspect to the exit. On arrival the warrant is filed and the suspect is free | closed |

Two more pieces of the pipeline:
- `release()` (`:4107-4147`) lets outside code end a warrant. A SEEKING warrant gets a 5-tick ignore window (`:4110-4114`). Arrest-stage states uncuff the suspect and clear the officer (`:4116-4142`). RELEASE is left alone (`:4117-4119`).
- Every state change goes through `DefaultArrestWarrant.setState` (`Common/DefaultArrestWarrant.java:277-297`). It sends MSG_LEGALSTATE, with the new state as the value, to the criminal's room and to the global channel. Other objects can see the case move.

#### 2.6 Officers, judges and timers

- An officer (`isAnyKindOfOfficer`, `:841-861`) must be a mobile monster, and must have the IS_OFFICER ambiance or match an OFFICERS keyword in its name or display text. A judge (`isTheJudge`, `:1298-1319`) must be non-mobile. A judge may match the JUDGE keyword or have IS_JUDGE.
- An officer is eligible when not busy with another case, alive, awake and not in combat (`isEligibleOfficer`, `:863-877`; `isBusyWithJustice`, `:1238-1252`).
- Officers search the room, then every adjacent room, and walk the eligible officer into the criminal's room (`getEligibleOfficer`, `:926-955`, with the walk call at `:948`).
- Timers, all in real time or ticks as noted:
  - A travel attempt gives up after 5 real minutes (`5*60*1000`, `:3190`, `:3665`, `:3738`, `:3992`).
  - The ARRESTING dwell is 1.5 ticks (`:3040`).
  - Warrant release and the SEEKING reset use 5 ticks (`:4110`, `:4114`).
  - Recently-offended window is 10 real minutes (`:2074`).
  - ARMED grace is 30 real seconds since the last NPC or PK fight (`:2371-2372`).

### 3. CombatAbilities

#### 3.1 Parameters and attachment

- Only MOBs can carry it. Any other host logs "does not want to fight" and removes the behaviour (`:716-722`).
- `startBehavior` reads its parameters (`:461-508`):
  - `proficient` sets every skill to proficiency 100 (`:476-477`, `:664-665`).
  - `nostat` and `nocombatstat` (plural forms too) turn off the stat adjusters (`:481-486`).
  - `-Ability` makes a skill never used. `+Ability` makes it always used (`:488-501`).
  - Combat-mode words are matched by prefix in `getParmsMinusCombatMode` (`:159-175`).
- `setParms` (`:93-105`) reads `bsize=`, the repertoire bucket size (`:99`). It forces `minTicks`, `maxTicks` and `chance` to 1, 1 and 100 (`:96-98`), so the behaviour fires on every MOB tick.
- Only one CombatAbilities per MOB is active. `isRightCombatAbilities` (`:510-524`) is checked in the tick (`:739`, `:760`).

#### 3.2 Tick flow (`tick`, `:710-954`)

1. Out of combat, it clears aggro (`:725-733`). If `PRECAST` is set, it counts down and casts an idle skill when the counter reaches zero (`:734-748`).
2. In combat it checks the victim, `canActAtAll` and `canAct` (`:752-763`).
3. It makes sure Skill_WandUse is at proficiency 100 (`:770-781`).
4. It rebuilds the weapon and wand set when the wielded weapon is missing or out of range, or a wand is in the wrong place (`:783-797`). Preference order: a wielded weapon in range, then a held one. If none, it picks a gettable room weapon whose level is no higher than the mob's, does `GET` and then `WIELD` (`:827-854`). The room search happens every fifth tick while unarmed (`chkDown`, `:827`, `:856`).
5. Aggro retarget (`:861-894`).
6. `useSkill` (`:898-908`).
7. If no skill was used, the wand fallback runs. It holds an off-hand wand, or casts the wand's spell with `sayto <target> <magic word>` (`:911-952`).

#### 3.3 Skill choice by combat mode

`useSkill` (`:526-708`) tries up to 99 times (`:537-539`). Each try draws a skill from one of three sources:
- The repertoire bucket, once it is full (`bsize`, `:541-545`). Skills join the bucket when chosen (`:633-636`).
- `skillsAlways`, in ONLYALWAYS mode (`:546-547`).
- Otherwise `fetchRandomAbility` (`:549`).

Skills are rejected if they are auto-invoked, have no trigger words, or fail the always and never lists (`:551-557`). The casting quality for the foe (`castingQuality(mob, victim)`) and for self and for the leader (`:559-561`) then selects the skill through the mode table (`:563-614`):

| Mode (value) | Malicious skill at foe | Beneficial skill on self or ally |
|---|---|---|
| RANDOM (0) | always | always |
| DEFENSIVE (1) | 5% | always |
| OFFENSIVE (2) | always | 5% |
| MIXEDOFFENSIVE (3) | 75% | 25% |
| MIXEDDEFENSIVE (4) | 25% | 75% |
| ONLYALWAYS (5) | always, from the `+` list only | always, from the `+` list only |

The percentages come from `rollPercentage() <= N` (`:572`, `:579`, `:584`, `:602`, `:606`, `:610`). Mode names are at `:71-78`.

- Targets (`:615-620`): beneficial to self, the mob. Beneficial to others, the leader, or the mob if they are in different rooms. Otherwise the victim.
- A skill already active on the chosen target is dropped (`:621-626`).
- Resource gates (`:629-662`): mana and movement are checked as `Math.random() > pool/max`. A short mana pool can still let a cast through with 30% chance (see Problems). Hit points are checked the same way, without a spending step.
- Proficiency (`:664-678`): proficient flag, 100. No qualifying level, 75. Otherwise `50 + (mob level - qualifying level)`, with the difference clamped (see Problems).
- `invoke` is called on the chosen target (`:681`). ONLYALWAYS retries up to 10 times if the invoke fails (`:682-687`). Action cost comes from `CMProps.getSkillCombatActionCost` (`:630`, `:690-691`).
- Turn-based combat: a weapon attack spends one action when the combat system is turn-based (`:380-388`).

#### 3.4 Aggro

- `adjustAggro` (`:336-375`): each attacker has a running score. If the attacker's score is over 1.5 times the current victim's score (`:355`), over one tenth of the host's max HP (`:356`), and the attacker is in combat and visible, the host switches to the attacker. Group members or unseen attackers reset the score to 0 (`:360-362`). Otherwise the host switches, and every other score is halved (`:364-370`).
- Points (`executeMsg`, `:393-459`):
  - Damage the host takes from an attacker: value x 2, credited to that attacker (`:405-414`).
  - Damage that the victim or its group deals to someone else: value x 1, credited to that attacker (`:417-419`).
  - Healing the victim, or healing done by the victim's group: value x 3, credited to the healer (`:423-429`).
  - Aggressive abilities aimed at the victim, not taught: adjusted level x 2 (`:432-437`).
  - Non-song spells cast by the victim or its group while in combat: the qualifying level (`:439-455`).
- Tick retarget (`:861-894`): the winner must exceed one tenth of max HP and `vicAmt + vicAmt/2` (`:868-883`), and must not be in the host's group (`:887`).

#### 3.5 Class and stat presets

- `makeClass` (`:107-157`) sets the class and level from the parameter list. Level is divided among the listed classes (`:146`). With no list, it uses the default class name passed by the subclass.
- `newCharacter` (`:207-251`) grants the class's starting skills. It removes any skill that does not qualify at the mob's level, or that is in SKILLSNEVER (`:228-234`). New skills get their maximum proficiency (`:237-247`). Old skills are raised to half their max (`:215-217`).
- `setCombatStats` (`:253-291`) adds a `Prop_CombatAdjuster` effect with text such as ` ARMOR+15 DAMAGE-10 MANA+10 MOVE-10 SPEED=1` (text built at `:263-277`).
- `setCharStats` (`:293-334`) adds a `Prop_StatAdjuster`. Points per stat are `level` for levels up to 5, and `5 + (level-5)/8` above that (`:313-315`), split across the class's adjustable stats (`:316`).
- Presets:
  - Clericness (`Clericness.java:52-67`): default class Cleric, combat mode RANDOM (`:58`), then `setCombatStats(mob,0,15,-10,0,10,-10,true)`. That is armour +15, damage -10, mana +10, move -10, speed 1 (`:64`).
  - Bardness (`Bardness.java:50-65`): default class Bard, then `setCombatStats(mob,0,-25,0,-10,-10,-10,true)`. That is armour -25, HP -10, mana -10, move -10, speed 1 (`:62`).
  - Both apply the stat adjusters only if `preCastSet` is unset or non-positive (`Clericness.java:62`, `Bardness.java:60`).

### 4. The small behaviours

#### 4.1 Aggressive and the bribe

- `tick` (`Aggressive.java:334-352`) calls `pickAFight` every `delay` ticks (`tickWait`, `:159`, `:339-349`).
- `pickAFight` (`:287-318`) only rescans when the room or its inhabitant count has changed (`:296-299`). It skips group members and mobs the mask does not match (`:308`). It applies `checklevel` (`:306`), `mobkill` (`:307`) and `nogang` (`:310`). `preFightCheck` runs for each candidate (`:311`).
- `startFight` (`:187-222`) requires the mob to be visible, activity to be allowed, and the monster to be free to behave normally (or `misbehave`). It also refuses targets holding the ORDER or CMDROOMS security flags (`:189-202`). A hidden monster first tries `Thief_BackStab` with proficiency `1d50 + 15 x level` (`:205-212`), then SAY the attack message (`:214-215`), then KILL (`:217`).
- Bribes (`BRIBE=amount,peace_ticks,pay_ticks`, parsed at `:161-173`):
  - Per player, the amount paid so far accumulates (`:105-115`).
  - Each payment computes `peace_ticks x (total paid so far / amount)` ticks (`:115-117`). That is added to the safe-until time if it has not lapsed, or counted from now if it has (`:118-121`). Because the total so far is used, payments in instalments earn more time than one payment (see Problems).
  - Once the total reaches the amount, the mob says "Ok then, you are safe for @x1." (`:122-134`).
  - Before a fight, `preFightCheck` (`:224-285`) asks for payment, "Pay me @x1 or I will kill you." by default (`:245-246`), and opens a window of `pay_ticks` (`:252`).
- Coins of the wrong currency are refused with "I only accept @x1." (`:72-84`).

#### 4.2 Helpers that join fights

All three helpers call the static `Aggressive.startFight` (`Aggressive.java:187`).
- AlignHelper (`AlignHelper.java:75-114`). On any malicious act against someone else, it joins if the target and the observer are both good, both neutral, or both evil (`:93-95`). An optional maximum (`num`) caps how many are already fighting the attacker (`:101-111`). Default shout: "<ALIGNMENT> PEOPLE UNITE! CHARGE!" (`:111`).
- BrotherHelper (`BrotherHelper.java:73-135`). A "brother" is a MOB with the same start room, or the same ID and name (`:79-86`). It joins against an attacker of a brother (`:101-111`). If the observer is a legal officer here, it joins only against officials, or against someone the attacker is aggressive towards (`:121-129`). Default shout: "DON'T HURT MY FRIEND!" (`:132`).
- ClanHelper (`ClanHelper.java:126-182`). For each rivalrous clan shared by observer and target (`CMLib.clans().findCommonRivalrousClans`, `:147`), it joins unless the attacker holds a role in that clan (`:166-173`). Shout: "<CLAN NAME>S UNITE! CHARGE!" (`:174-176`). Default: "WE ARE UNDER ATTACK!! CHARGE!!" (`:174`).

#### 4.3 BribeGateGuard

- `findGate` (`:96-114`) takes the first direction with an exit that has a door. `getMyKeyTo` (`:116-136`) gives the guard a key for that exit.
- Balances are per player and per gate system. They live in an in-memory map keyed by `ID()+gates` (`notTheJournal`, `:52`; `:194-207`). The database journal path needs `surviveReboot`, which is a constant `false` (`:51`), so that code never runs.
- Defaults: price 5 (`:41`, `:91`), gates "General" (`:42`, `:92`).
- Veto (`okMessage`, `:340-366`): a player who has not paid cannot pass. The guard quotes the fee and refuses. Closing is always allowed (`:340`).
- Payment (`:456-511`): coins given add to the balance (`payment`, `:138-147`). "Thank you very much." (`:462`). A surplus is kept as credit (`:463-471`). Exact or over-payment opens the gate and the guard bows (`:477-499`). Under-payment gives "I'm afraid that this is insufficient." and charges the coins back (`:501-510`, see Problems).
- Leaving through the gate charges the price (`:429-449`).
- tick (`:514-608`): greets each unpaid player with the fee, and reminds paid players of their balance (`:536-570`). The gate is locked at night and unlocked by day (`:571-597`). Every third tick it closes an open gate if no paid player is waiting to pass (`:598-606`).

#### 4.4 Beggar, AntiVagrant and ActiveTicker users

- Beggar (`Beggar.java:93-168`). Every fifth MOB tick (`:103-106`) it picks the first visible player it has not asked before (`:111-115`) and says one of ten lines (`:121-162`, see Content). A donation is removed from the beggar by a trailer runnable after the GIVE (`:77-86`). The donor gets a CHARITY achievement bump when they are a player (`:75-76`).
- AntiVagrant (`AntiVagrant.java:75-137`). It acts only in city rooms (`:79-80`), or anywhere with ANYWHERE. A sleeper is shaken awake (`:82-98`). A sitter is told to get up (`:99-111`). KICK sends the target away with `beMobile` (`:92-96`, `:108-109`). Speech containing "SIT" sets a 3-tick pause (`:133-136`, `:148-152`). The comments there say "this is for arrest behavior" (`:131`, `:147`).
- Its constructor sets min 2, max 3 and chance 99 (`:57`).

### 5. Messages shown to players

Short lines, paraphrased where long. Each shows where the text is generated.

| Context | Line (quoted or paraphrased) | Source |
|---|---|---|
| Arrest begins | "You are under arrest for @x1! Sit down on the ground immediately!" | `Arrest.java:140`, `:2979` |
| Officer loses patience | "I said SIT DOWN! NOW!" | `resources/laws.ini:64` |
| Suspect goes quiet | "Good. Now hold still." | `resources/laws.ini:65` |
| Suspect fights | "Resisting arrest?! How DARE you!" | `resources/laws.ini:66` |
| Suspect has killed an officer | "COP-KILLER!!!!! AAARRRGGGHHHH!!!!!" | `resources/laws.ini:67` |
| Subdue fails (suspect still standing) | "Hmph." | `Arrest.java:3161` |
| Officer loses the suspect in transit | "Drat! Lost another one!" | `Arrest.java:3223` |
| Attack on judge | "Ack! Treason! Die!" | `Arrest.java:2615` |
| Accusation | "@x1 has been accused of @x2." (WARRANTS channel) | `Arrest.java:397` |
| Fine | "You are hereby fined @x1, payable to the local tax assessor." | `Arrest.java:1862` |
| Banishment | "You are hereby banished for @x1 days." | `Arrest.java:1894` |
| Execution | "You are hereby sentenced to a brutal death. Sentence to be carried out IMMEDIATELY!" | `resources/laws.ini:42` |
| Gate fee | "I'll let you through here if you pay the fee of @x1." | `BribeGateGuard.java:359`, `:563` |
| Gate under-payment | "I'm afraid that this is insufficient." | `BribeGateGuard.java:505` |
| Aggressive bribe | "Pay me @x1 or I will kill you." (default) | `Aggressive.java:245-246` |
| Bribe paid | "Ok then, you are safe for @x1." (default) | `Aggressive.java:126` |
| AntiVagrant | "Damn lazy good for nothing!", "Get up and move along!" | `AntiVagrant.java:85`, `:102` |

## Content worth mining

### Crime catalogue (`resources/laws.ini`)

Basic crimes, shipped (`resources/laws.ini:163-171`):

| Crime | Where | Flags | Sentence |
|---|---|---|---|
| Resisting arrest | anywhere | none | jail1 |
| Trespassing | not at home, not indoors | not recently | jail3 |
| Indecent exposure (nudity) | not at home, not indoors | witnessed, not recently | warning, capped at parole1 |
| Violating the armed citizen law | not at home, not in a giant's room | not recently, not in combat, witnessed, 90% ignore | warning, capped at parole1 |
| Assault | anywhere | none | jail1 |
| Murder | anywhere | none | jail3 |
| Robbing property | anywhere | not witnessed | jail3 |
| Tax evasion | anywhere | not witnessed | jail1 |
| Prison break | anywhere | not witnessed | jail1 |

Ability crimes (`:180-205`), keyed by ability ID, type or domain:
- Thief skills: swiping jail2, stealing jail3, arsonry jail2, setting traps outside homes jail3, bribing jail2, conning jail2, embezzling jail4, taking a contract on someone's life death, setting a death trap death, forgery jail2, racketeering jail3, robbery jail3, kidnapping jail3.
- PIMPING (prostitution law, `:192`): witnessed, 50% ignore, jail1 capped at jail4.
- SKILL_ENSLAVE (enslavement, `:193`): jail3.
- Intoxication, the INEBRIATION key and the three POISON_ALCOHOL, POISON_FIREBREATHER and POISON_LIQUOR variants (`:194-197`): public intoxication, parole1 capped at jail4. Indoors, at home and in drinking places are exempt.
- The three PRAYER_*PORTAL keys (`:198-200`): invoking dangerous magic, jail4.

Emote crimes (`:213-223`): spitting, creating a disturbance by barking, throttling, an obscene gesture, intimate-contact emotes (indoors only), public fighting, swearing, nudity emotes, vomiting in the street (parole1), and a voodoo doll (jail2). Each matches phrases in the emote text. The phrases are not reproduced here.

Banned substances (`:231-234`): four item names or materials, each drug possession at parole3.

### Punishment ladder

| Code | Name in laws.ini | Effect in code | Lines |
|---|---|---|---|
| 0 | WARNING | spoken warning, case closed | `Law.java:615`, `Arrest.java:1691` |
| 1 | THREAT | spoken threat, case closed | `Law.java:617`, `Arrest.java:1718` |
| 2-5 | PAROLE1-4 | judge's parole, jail time from PAROLEnTIME | `Law.java:619-625`, `Arrest.java:1745-1781` |
| 6-9 | JAIL1-4 | jail, jail time from JAILnTIME | `Law.java:627-633`, `Arrest.java:1793-1840` |
| 10 | DEATH (EXECUTE) | judge's execution | `Law.java:635`, `Arrest.java:1841` |
| flag | SEPARATE | no recidivism step, no shared warrant pool | `Law.java:657`, `Arrest.java:1193` |
| flag | FINE=amount | fine, sum across warrants | `Arrest.java:1854-1870` |
| flag | DETAIN=room,ticks | held at a detention centre | `Arrest.java:1938-1947` |
| flag | BANISH=days[,room] | exile for days, 255 or more is permanent | `Arrest.java:1872-1904` |
| flag | SHAME=days | public shaming | `Arrest.java:1906-1936` |
| flag | NORELEASE | not walked back to a release room | `Arrest.java:3910` |
| flag | PUNISHCAP=sentence | upper limit on escalation | `Arrest.java:1215-1230` |
| flag | SKIPTRIAL | judged by the officer, no judge | `Arrest.java:1684-1685` |

### Combat presets and parameters

| Preset | Default class | Armour | Damage | HP | Mana | Move | Speed | Source |
|---|---|---|---|---|---|---|---|---|
| Clericness | Cleric | +15 | -10 | 0 | +10 | -10 | 1 | `Clericness.java:58-64` |
| Bardness | Bard | -25 | 0 | -10 | -10 | -10 | 1 | `Bardness.java:56-62` |

Attack adjustment is 0 in both. Combat-mode words, the mode table and the aggro points are in 3.3 and 3.4.

### Beggar lines (`Beggar.java:121-162`)

Ten lines, one picked at random per approach. In outline: a small donation for a veteran; a request for one coin of the local currency, addressed with the player's honorific; a plain "Spare some change?"; a plea for help for someone down on their luck; a joke about a lost "Will Work For Food" sign; a request to spread a little joy to a poor fella or gal; a one-word "Change?"; "Can you spare a little change?"; a request for one coin; and a "poor soul" plea using the currency name. The currency name is pulled from `nameCurrencyLong` with its leading digits stripped (`:117-120`).

### Ready-made data hooks

- The law file is a template: a new town rewrites `OFFICERS`, `JUDGE`, `JAIL`, `RELEASEROOM`, messages, times and crime lines, and needs no code change (`resources/laws.ini:1-124`).
- Parameter strings for the small behaviours (`MESSAGE=`, `BRIBE=`, `delay=`, `checklevel`, `mingrp=`) are documented in `resources/help/arc_behaviors.ini` (`BEHAVIOR_AGGRESSIVE`, `BEHAVIOR_ALIGNHELPER`, `BEHAVIOR_BRIBEGATEGUARD`, `BEHAVIOR_BROTHERHELPER`, around lines 60-190).

## Notable design ideas

- **Rules as a table, with crime keys that are also game IDs.** Ability crime keys are matched against ability IDs, types and domains (`DefaultLawSet.java:915-922`, `:317-330`). Every new spell can become a crime without code.
- **Crimes from prose.** Emote and social text is matched against phrase lists (`Arrest.java:2699-2718`), so a witnessed emote can be a crime.
- **Witnesses by temperament.** The chance that an NPC sees a crime depends on the accused's alignment: about 76% for chaotic, 51% for neutral, 6% for lawful (`Arrest.java:811-812`). It is a cheap, believable knob.
- **Warrants as first-class objects that broadcast state.** `setState` tells the room about each change (`Common/DefaultArrestWarrant.java:277-297`), so the world can react to the case.
- **Consequences as effects.** Cuffs, prisoner status, banishment and shaming are effects with tick-downs (`Arrest.java:3390-3391`, `:1883-1898`, `:3099-3125`). Jail is therefore an ordinary condition on the character.
- **Officers navigate with a skill.** Officers track with Skill_Track and cache the path (`Arrest.java:1365-1378`, `:3703`), so the NPC's pathfinding is a player-usable ability.
- **Executioners use the same abilities as players.** The judge applies `Fighter_Behead` or `Prayer_Stoning` (`Arrest.java:3604-3606`), and its own quality check returns indifferent unless the target holds a warrant (`Abilities/Fighter/Fighter_Behead.java:98-118`).
- **Mode-weighted skill choice.** Six modes give a single probability table that decides between offence and support (`CombatAbilities.java:563-614`).
- **A repertoire bucket.** `bsize` fixes an NPC's skill set after N uses, so a monster has a recognisable style (`CombatAbilities.java:541-545`, `:633-636`).
- **Aggro with hysteresis.** Switching targets needs a 1.5x lead, and the others' scores are halved on a switch (`CombatAbilities.java:355-370`, `:882-883`).
- **Bribes as time.** Payments buy safe time proportional to the amount paid (`Aggressive.java:116-121`).
- **Brotherhood by spawn room.** Mobs spawned in the same start room count as kin (`BrotherHelper.java:79-83`).
- **One rule for joining fights.** AlignHelper, BrotherHelper and ClanHelper all hand off to `Aggressive.startFight`, and differ only in the predicate that decides who is on whose side.

## Problems and cruft

Read from source. None of these were run; each is marked by how sure the reading is.

1. **First offences are escalated.** `getRelevantWarrants` puts the warrant itself first (`Arrest.java:1017-1019`). The loop at `:1206-1212` then adds one step for each non-separate warrant that is below `base + 4`. The warrant is in its own list, so every non-separate warrant goes up at least one step. A WARNING becomes a THREAT, and JAIL1 becomes JAIL2. laws.ini says `separate` is the way to avoid a recidivism increase (`resources/laws.ini:154`), so this looks unintended. Reading is clear; behaviour unverified in play.
2. **Four aggregates read the wrong warrant.** In `getFine` (`:1050`), `getBanishmentTicks` (`:1080`), `getShameTicks` (`:1114`) and `getDetainParm` (`:1144`), the loop variable is `W2` but the parameter is read from `W`. Every relevant warrant therefore contributes the same parameter. In `getDetainParm` the loop only looks at `W`, which the method has already found empty (`:1135-1136`), so detention never comes from another warrant.
3. **The troublemaker halving drops the flags.** `Arrest.java:2255` replaces the whole code with the halved base. SEPARATE, FINE, DETAIN, BANISH, SHAME and NORELEASE are all lost. Halving also works on the enum value, so JAIL2 (7) becomes PAROLE2 (3).
4. **Flags can disappear in `highestCrimeAction`.** The escalation assigns `highest` outright (`:1202-1203`), which drops W's flag bits. The NORELEASE check at `:3910` reads the bit from the returned value, and the EXECUTE announcement at `:3276` compares for equality with the bare constant. Both behave differently depending on whether escalation happened.
5. **Comment and constant disagree.** `Arrest.java:1005` says crimes expire after "three real days". The constant is seven (`LegalBehavior.java:52`), and the code uses the constant.
6. **`canFocusOn` ignores its name test.** The name-match branch returns true, and the fall-through also returns true (`:966-971`), so the name check has no effect.
7. **Dead code.** `lastBanishR` is declared (`:64`) and read (`:1568-1569`) but never assigned, so the "cache" never caches. `getBanishRoom` (`:1157`) is never called. `isTrespassRoom` (`:505-522`) is called only from a commented-out line (`:2388`). The bribe journal branch in BribeGateGuard depends on a constant false (`:51`).
8. **Ability lookups without null checks.** `Banishment` is invoked without a check (`:1883-1895`), as are `Shaming` (`:1917-1922`) and `Prisoner` (`:3390-3391`, `:3603`). Whether `CMClass.getAbility` can return null here is unverified.
9. **Name-keyed caches.** `bannedMOBCheck` is keyed by display name (`:2411-2422`), so identically named NPCs are checked once and then skipped until the set evicts them. `bannedItemCache` is keyed by item name (`:2289`, `:2318`).
10. **Offence expiry skips the duplicate check.** The expiry path adds to `offenses` and to the old list (`:2837-2838`) without the check that filing uses (`:1514-1526`). Repeat expiries may stack duplicate records. Unverified.
11. **Aggressive BRIBE parse reads the wrong field.** `Aggressive.java:169` reads `bribeParts.get(1)` (peace ticks) for the pay window. The help file says the third value is "TICKS TO PAY" (`resources/help/arc_behaviors.ini:98-106`). With three values, the pay window equals the peace time. The default of 20000 ticks is about 22 real hours.
12. **Instalment payments earn more peace.** The safe-time formula uses the total paid so far (`Aggressive.java:115-117`). Paying half now and half later earns 0.5 + 1.0 = 1.5 times the peace time. Paying all at once earns 1.0. This looks unintended.
13. **Aggressive docs disagree with code.** The help says `checklevel` attacks targets "within 6 levels below" (`resources/help/arc_behaviors.ini`, BEHAVIOR_AGGRESSIVE). The code is `observer.level < target.level + EXPRATE` (`Aggressive.java:306`). With EXPRATE=6 (`coffeemud.ini:406`), targets at most five levels below are attacked. The help also says builders with CMDMOBS are spared, but the code checks ORDER and CMDROOMS (`:198-199`).
14. **Mana, movement and the sign convention.** `CombatAbilities` adds the cost back in the short-mana path (`:646`), adds 5 mana per attempt (`:648`) and adds 5 movement per attempt (`:655`). `DefaultCharState.adjMana` adds its argument (`Common/DefaultCharState.java:310-313`). Every spending path passes a negative amount (`Abilities/StdAbility.java:2074-2076`). As read, a mob short of mana gets its cost added back before the cast, and the cast's own deduction then nets to zero, on a 30% roll. Possibly a deliberate allowance, but it reads as a sign error.
15. **Proficiency clamp.** `levelDiff` is set to 50 when it is negative or above 50 (`:673-674`). A mob under the skill's level therefore gets proficiency 100 (`:676`), the same as a mob far above it.
16. **`lastSpell` is effectively dead.** It is written only if already non-null (`:694`, `:698`) and is cleared each tick (`:767-768`), so it stays null unless loaded from saved stats. The intent is unclear.
17. **Mode words are prefix-matched.** `getParmsMinusCombatMode` removes any token that is a prefix of a mode name (`:159-175`), so short tokens such as "O" or "DEF" count as modes.
18. **Coin value versus coin count.** `charge(((Coins)msg.tool()).value(), ...)` (`BribeGateGuard.java:509`) charges the coin count. `StdCoins.value()` returns the count (`Items/Basic/StdCoins.java:44-47`, set by `setNumberOfCoins` at `:118-124`), while `getTotalValue` is denomination times count (`:153-156`). The two differ when the denomination is not 1. Unverified in play.
19. **Null risk in the gate.** `exit.Name().equals(e.Name())` (`BribeGateGuard.java:440`) uses `e`, which is set only in tick (`:534`). A payer who leaves before the first tick would hit null.
20. **Monster-only shared dependencies.** The `tickMobsSet.size()` check is read outside its lock (`Arrest.java:2780`). The read is benign, but it is inconsistent with the locked writes (`:2557-2560`, `:2783-2787`).
21. **Stale doc comment.** The `BANISH` mask is documented as "a public shaming" (`Common/interfaces/Law.java:670`).

## Takeaways for a Python SUD

Single-player means the player is the only possible criminal, and the only victim who matters. Several of the mechanics here are multiplayer-driven (group and clan checks, PK timers, persistent coffee-table statistics, channel broadcasts). They can go.

**Borrow:**

- **The crime table as data.** One row per crime: where it applies, flags, name, sentence and a line of text. Load it from TOML or YAML, not code. Crime keys can be the Python names of actions, so new actions become crimes by adding a row.
- **Sentences as structured values, not strings.** A base enum plus a small set of flags, with parameters:

```python
from dataclasses import dataclass
from enum import IntEnum

class Sentence(IntEnum):      # base codes from Law.java:615-637
    WARN = 0; THREATEN = 1
    PAROLE1 = 2; PAROLE2 = 3; PAROLE3 = 4; PAROLE4 = 5
    JAIL1 = 6; JAIL2 = 7; JAIL3 = 8; JAIL4 = 9
    EXECUTE = 10

@dataclass
class Warrant:
    crime: str
    sentence: Sentence
    separate: bool = False     # laws.ini "separate": no recidivism step
    prior_offences: int = 0    # counted per crime, as in fileAllWarrants
    state: str = "SEEKING"
```

- **Escalation counted from prior offences only.** Prior offences add steps (capped at three, as in `Arrest.java:1199-1205`). The warrant under judgement should not count itself (see Problems 1).
- **A small case state machine.** The 15 states are more than a single-player game needs. A workable subset is SEEK, ARREST, SUBDUE, ESCORT, REPORT, JUDGE, JAILED and RELEASED, with DETAIN and BANISH as optional destinations. Each state gets one handler that returns the next state.
- **Witness odds by alignment.** Keep the idea of a per-NPC roll, weighted by the accused's alignment (`Arrest.java:811-812`).
- **Consequences as conditions.** Jail, parole and shaming are timed conditions on the player (the `Prisoner` idea), with a tick countdown.
- **Mode-weighted skill choice for NPC opponents.** The mode table (`CombatAbilities.java:563-614`) is a good small spec. The repertoire bucket and the 1.5x aggro lead are worth copying.
- **One predicate-driven fight-joining rule.** AlignHelper, BrotherHelper and ClanHelper reduce to "join when `predicate(observer, attacker, target)` holds". In a single-player game this may serve only faction-style allies.

**Simplify:**

- Remove group and clan checks, PK timers, channel broadcasts and per-area database storage.
- Replace the packed integer punishment code with the dataclass above.
- Make witness detection one function over the NPCs in the room and the adjacent rooms.
- Keep the bribe as time, but compute it from the single payment. The instalment quirk should not be copied.

**Drop:**

- The dead code (`lastBanishR`, `getBanishRoom`, `isTrespassRoom`, the journal path).
- The name-keyed caches. Use object identity.
- The stringly-typed sentence and crime parsing, once the data is structured.

## Coverage

- **Read in full (all 16 manifest files):** `Arrest.java` (4148 lines, six chunks), `CombatAbilities.java` (1066), `BribeGateGuard.java` (609), `Aggressive.java` (353), `ClanHelper.java` (183), `BrotherHelper.java` (137), `AlignHelper.java` (115), `AntiVagrant.java` (162), `Beggar.java` (169), `Clericness.java` (68), `Bardness.java` (66), `ActiveTicker.java` (166), `Behavior.java` (196), `LegalBehavior.java` (370), `ChattyBehavior.java` (54) and `MobileBehavior.java` (46). In the helper and preset files, lines 1 to 16 (package, imports and licence header) were not read.
- **Read beyond the bucket, for interactions:**
  - `StdBehavior.java` (full).
  - `Common/interfaces/Law.java` (constants and punishment tables, `:615-766`, and the default law text).
  - `Common/DefaultLawSet.java` (`:134`, `:290-330`, `:758-800`, `:860-935`, `:971-1080`, sampled).
  - `Common/DefaultArrestWarrant.java` (`:101`, `:175-190`, `:277-305`).
  - `Common/interfaces/LegalWarrant.java` (method list).
  - `Abilities/Fighter/Fighter_Behead.java` (`:98-150`, sampled).
  - `MOBS/StdMOB.java` (`:4810-4830`, sampled).
  - `Abilities/Properties/Prop_ReqEntry.java` (`:155-170`).
  - `Abilities/StdAbility.java` (`:1994-2003`, `:2066-2078`).
  - `Common/DefaultCharState.java` (`:305-325`).
  - `Items/Basic/StdCoins.java` (`:44-47`, `:79-88`, `:118-156`).
  - `core/CMProps.java` (`:681-686`, `:1222-1228`, `:3536-3545`).
  - `Libraries/Dice.java` (`:409-412`).
  - `resources/laws.ini` (read in full, 234 lines).
  - `resources/help/arc_behaviors.ini` (`:60-190`, sampled).
  - `coffeemud.ini` (lines 406, 1099, 1104, 2151).
- **Named but not read:** the effect abilities listed in Key files, the `CMLib` services (clans, channels, tracking, coffee tables, law), and `LegalLibrary`.
- **Skipped:** nothing in the manifest.
