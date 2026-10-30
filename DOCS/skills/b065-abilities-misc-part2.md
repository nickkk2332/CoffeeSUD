# Misc abilities, part 2: area instancing, moods, injuries and status traits

> Part of the CoffeeMud NEWOVERVIEW. Bucket: `b065` (Abilities/Misc [part 2/3: Firebreath .. PoisonousBite]) · 20 files · 8887 lines of Java.

## Summary

This bucket is the middle third of CoffeeMud's `Abilities/Misc` package. These are mostly not spells or skills but persistent effects that sit on a mob, item, room or area and change how it behaves. The largest is `InstanceArea`, which builds on-demand copies of an area for a player group from a text file of instance types; the only shipped type, "Triumph", is gated by a faction and the in-game calendar. `Mood` rewrites a character's speech and plays idle socials, and `Injury` tracks per-limb damage percentages that heal as hit points return and can end in amputation. The smaller systems cover fear reactions (`Phobias`), floating (`GravityFloat`), a renewable milk resource (`Milkable`), damage-type immunity (`Immunities`), item respawn that keeps nested contents (`ItemRejuv`), walking styles (`Gait`), racial combat traits, NPC loyalty and begging, and four breath weapons on a shared `Dragonbreath` engine. Two mechanisms are broken as shipped (Grazing's cud model and Loyalty's check interval), and several depend on group or multiplayer state.

## Key files and types

Citations in the text use the file name and line; every file is in `com/planet_ink/coffee_mud/Abilities/Misc/` unless the path is given.

| File | Role | Lines |
|---|---|---|
| `Abilities/Misc/InstanceArea.java` | Area instancing: instance-type loader, group entry gate, instance build (`fixRoom`), timeout and teardown, the Transform spell (`invoke`) | 2669 |
| `Abilities/Misc/Mood.java` | Mood effect: rewrites the wearer's speech, idle and speech-triggered socials, 21 moods | 1673 |
| `Abilities/Misc/Injury.java` | Per-limb injury percentages, healing from HP regain, amputation trigger, disease hooks | 823 |
| `Abilities/Misc/Phobias.java` | Fear of named races, race categories or object names, with a reaction chain | 474 |
| `Abilities/Misc/GravityFloat.java` | Floating state for items and mobs, movement penalties, space-gravity test | 452 |
| `Abilities/Misc/Milkable.java` | Renewable liquid resource on a cow (milk): milking, filling, drinking | 428 |
| `Abilities/Misc/Grazing.java` | Outdoor grazing trait for cattle races (cud model, inoperative) | 266 |
| `Abilities/Misc/Immunities.java` | Vetoes malicious messages by damage type; removes listed effects and behaviours | 258 |
| `Abilities/Misc/ItemRejuv.java` | Respawns a room item together with its nested contents | 258 |
| `Abilities/Misc/Gait.java` | Named walking styles with arrive and leave text | 252 |
| `Abilities/Misc/Hamstring.java` | Leg cripple: movement loss, DEX loss, no fleeing | 215 |
| `Abilities/Misc/FoodBegging.java` | Non-combat begging; an NPC gives the beggar food | 212 |
| `Abilities/Misc/Paralysis.java` | Paralysis status (cannot move) | 183 |
| `Abilities/Misc/Loyalty.java` | NPC loyal to a named player; follows and can wander to them | 174 |
| `Abilities/Misc/PoisonousBite.java` | Bite attack that damages and applies Poison | 164 |
| `Abilities/Misc/Gore.java` | Racial damage multiplier for piercing and natural weapons | 130 |
| `Abilities/Misc/Firebreath.java` | Fire breath; misc text fixed to "fire" | 67 |
| `Abilities/Misc/Gasbreath.java` | Gas breath; misc text fixed to "gas" | 67 |
| `Abilities/Misc/Frostbreath.java` | Cold breath | 61 |
| `Abilities/Misc/Lighteningbreath.java` | Lightning breath (ID misspelled, see Problems) | 61 |
| `Abilities/Misc/Dragonbreath.java` (outside bucket, read for mechanics) | Shared breath engine: colour, damage, failure rules | 437 |
| `resources/skills/areainstancetypes.txt` (data) | Instance-type schema header and the Triumph ladder | 157 |
| `resources/lists.ini` (data, `GAIT_LIST`) | 18 gait definitions | 1045-1063 |
| `resources/factions/triumph.ini` (data) | Faction used by Triumph (MIN 0, MAX 999999) | - |

## How it works

### Shared pattern

All twenty classes extend `StdAbility`. The four breath classes extend `Dragonbreath`, which also extends `StdAbility` (`Dragonbreath.java:36`). Most are "property" effects: `classificationCode()` returns `ACODE_PROPERTY` (`Loyalty.java:80-82`, `Injury.java:171-173`, `Mood.java:79-81`, `Phobias.java:79-81`). They attach to a mob, item, room or area and act through `affectPhyStats`, `affectCharStats`, `affectCharState`, `okMessage` (veto before an action), `executeMsg` (react after it), or `tick`. Timed effects are created with `maliciousAffect` or `beneficialAffect`, which copy the ability and call `startTickDown` (`StdAbility.java:2185-2200`). The default tick is 4 seconds (`CMProps.java:681`).

### 1. Area instancing (`InstanceArea.java`)

#### 1.1 Instance types are text data

- `getAllInstanceTypesMap` (`InstanceArea.java:2057-2129`) reads `skills/areainstancetypes.txt` through the extended-file lookup (`:2064`, so a local copy can override the shipped one) and caches the parsed map as a resource (`:2126`).
- Each line is `"Name" KEY=value ...`. Keys are upper-cased (`CMParms.parseEQParms`, `CMParms.java:2364-2420`); values keep their case. `ENABLE` lists are split with `parseDelimitedParenList(..., upKey=true)` (`CMParms.java:2629-2631`, `:2678`), which upper-cases the ability, flag or domain names before matching.
- Several lines with the same name form an array. `LIKE="X"` copies every key the entry lacks from the first entry named X (`InstanceArea.java:2108-2125`).
- `getInstVars` (`:2131-2201`) resolves a request by exact name, then prefix, then substring, then suffix. If a leader is known, entries whose `LEADERREQ` zapper mask fails are dropped (`:2174-2190`, via `CMLib.masking().maskCheck`). A random survivor is returned (`:2198`).
- `InstVar` (`:141-191`) declares 48 keys. Six of them (`ARMORADJ`, `ATTACKADJ`, `SAVEADJ`, `HPADJ`, `DAMAGEADJ`, `DESCRIPTION`) are never read anywhere in the class. The data header also documents `OPPOSED` and `TRANSITIONAL` (`areainstancetypes.txt`, header), which no code references.

Key data fields, from the header and the code:

| Key | Effect (code location) |
|---|---|
| `DURATION` | Area ticks until the instance is torn down (`:425`, tick at `:1436-1447`) |
| `LIMIT` | Entry cap per leader, e.g. `1/DAY` (`:499-518`, checked at `:1813-1852`) |
| `LEADERREQ` | Zapper mask on the group leader choosing among variants (`:2174-2190`) |
| `LEVELADJ` / `ILEVELADJ` | Mob and item level formulas (`:487-498`) |
| `ELITE` | Elite tier formula; adds the stat boost in 1.3 (`:914-927`, `:1243-1261`) |
| `ENABLE` | Grants abilities by name, flag, domain or type; `number(x/y)` grants x picks per y levels (`:522-631`, granted at `:1299-1320`) |
| `FACTIONS` / `PLAYFACTIONS` | Mob faction values; player faction joins on entry (`:1138-1174`, `:1866-1923`) |
| `BEHAVE`, `REFFECT`, `AEFFECT`, `IEFFECT` | Behaviours or abilities added to mobs, rooms, the area, or items (`:1282-1298`, `:931-956`, `:703-713`, `:1034-1058`) |
| `ROOMCOLOR`, `ROOMADJS` | Room title colour and adjectives sprinkled into descriptions (`:789-833`) |
| `ATMOSPHERE`, `HOURS`, `ABSORB` | Air material, day length scale, damage absorption (`:669-701`) |
| `MIXRACE`, `MOBCOPY`, `PREFIX`, `PROMOTIONS` | Mob race mixing, duplication, name prefixes, weighted promotions (`:1097-1116`, `:1331-1359`, `:835-889`, `:640-658`) |

#### 1.2 Entry gate (parent area, `okMessage`)

- The gate runs on an `ENTER` message into a room of an area that carries the effect (or of an area listed in `AREAMATCH`), when the mover comes from a different area (`:1789-1800`). Sysops with the area permission are exempt (`:1795-1796`). Mobs whose start room is in the parent area are not redirected unless they are players (`:1800`).
- `getAppropriateGroup` (`:1581-1612`) returns the leader's group. `findExistingInstance` (`:1641-1701`) looks for an instance that already holds a member of that group and moves stragglers into it.
- If no instance exists, `isApplicableKickoffFor` (`:1703-1719`) checks that the effect's holder (room, area, mob, rideable or item owner) relates to the entrant before anything is created.
- Per-leader limits (`:1813-1852`) are stored in the leader's class-variable map under a key built from the type, the limit and the category hash. When the window has passed the count resets. Otherwise the entrant is refused with "You are not allowed to re-enter this special place right now." unless they have the area-command permission.
- Creation (`:1853-1863`): a `SubThinInstance` named `<n>_<ParentName>`, where `n` comes from a static `AtomicInteger` (`:133`). It copies atmosphere, climate, theme and time clock. Proper room numbers are re-keyed with `convertToMyArea` (`:2390-2401`), which keeps the `#suffix`, so room `Parent#12` becomes `<n>_Parent#12`.
- Faction on entry (`:1866-1923`): each `PLAYFACTIONS` entry is added to each player at its default, a fixed value, or a random value from a named range. The member with the highest existing value becomes the instance leader (`leaderM=M`, `:1912`). That leader later decides which `LEADERREQ` variant applies.
- Level statistics (`:1941-1963`) are computed from `LEVELADJ` with the parent's min and max and cached as resource `STATS_<NAME>`.
- Registration: a static `instanceChildren` map keyed by parent area (`:131`, `:1924-1940`). When the effect sits on a non-area holder, an `InstanceAreaManager` is attached to the parent area to track children (`:1964-1973`, class at `:250-366`).
- Each instance gets its own copy of the effect with `DURATION` as its timeout (`:1975-1994`).
- Target room: the mirror of the requested room ID is used if it exists. Otherwise up to 1000 random proper rooms are tried, each needing access, no land title, and an `okMessage` pass (`:1995-2012`). The entrant's message target is then replaced, so they arrive in the copy (`:2012`).

#### 1.3 Building an instance (`fixRoom`)

`fixRoom` runs once per room, on the first `TYP_NEWROOM` message for that room (`executeMsg` at `:1519-1528`; `roomsDone` at `:1492-1511`). It is a long synchronized method (`:891-1372`).

- Rooms (`:910-985`): the sky is reset; colour and adjectives applied; `REFFECT` behaviours or abilities added; a room with a land title has all non-player mobs and unprotected items destroyed (`:958-985`).
- Exits (`:1005-1026`): boardable exits to other areas are deleted (`:992-1002`). Readable exit lists are rewritten to mirrored room IDs, so the copy's exits point inside the copy.
- Items (`:1028-1086`): weapons and armour get `IADJUST` (a wear adjuster) and `IEFFECT`. Level is set twice through `CMLib.itemBuilder().balanceItemByLevel` and `itemFix`. Weapons with `REQWEAPONS` containing `MAGICAL` get the bonus flag (`:1078-1084`).
- Mobs (`:1090-1330`): only "instance mobs" qualify (`isInstanceMob`, `:779-787`). A mob qualifies if it is a monster with a start room, and lacks a `NOAINST` tattoo. The header tells builders to add that tattoo to normal-area mobs that must not spawn in instances (`areainstancetypes.txt`, header). Such a mob gets: a mixed race (`:1097-1116`); breathables from `ATMOSPHERE`; a level from the formula (`:1120-1123`); name prefixes (`:1125`, `applyMobPrefix` at `:835-889`); `fillOutMOB` (`:1129`); alignment and faction (`:1132-1174`); `MOBRESIST` as `Prop_Resistance` (`:1205-1207`); `SETSTAT`, `BEHAVAFFID`, `ADJSTAT`; `ADJUST`/`ADJSIZE`; `BEHAVE`; and `ENABLE` grants.
- Elite boost (`:1243-1253`): when the elite level is above zero, `Prop_Adjuster` is applied with hit points `+(200+(e-1)*50)`, attack `+(125+(e-1)*12)`, damage `+(110+(e-1)*5)`, armour `+(120+(e-1)*20)`, all saves `+(20+(e-1)*5)`, and `Prop_ModExperience` at `round((e+3)/2)`, where `e` is the elite level.
- `MOBCOPY` (`:1331-1359`) duplicates each instance mob N times in the room.

`hardBumpLevel` (`:123`) feeds a default `Prop_Resistance` of `5*hardBumpLevel` percent (`:1209-1210`). Nothing in the tree ever sets it to a non-zero value (see Problems).

#### 1.4 Level formulas and variables

Each formula is compiled with `CMath.compileMathExpression` and gets seven variables (`vars` arrays at `:520-521`, `:922-925`, `:1060-1062`, `:1120-1122`, `:1180-1182`, `:1947-1954`):

| Variable | Meaning |
|---|---|
| `@x1` | instance level |
| `@x2` | the target's own level (mob or item) |
| `@x3` | instance level again |
| `@x4`, `@x5` | parent area min and max level |
| `@x6` | `CMProps` EXPRATE plus 1 (`:521`) |
| `@x7` | best faction value among the group members (`topPlayerFacVal`) |

The default `LEVELADJ` when none is given is `@x3 + ((((1+@x5-@x4)-(1+@x5-@x2))/(1+@x5-@x4))*(1+@x6)) > 1` (`:489`). If the value is a plain integer, it is added as a bonus (`:491-492`).

Two points matter here:

- In `CMath`, `>` is not a comparison. It returns the larger operand (`CMath.java:1446-1447`). The default formula is therefore `max(1, instance level + scaled term)`, which is the intent: a floor of level 1.
- The header says `@x6 = exprate` (`areainstancetypes.txt`, header), but the code passes `EXPRATE+1` (`:521`) and the default formula adds 1 again (`1+@x6`). The effective multiplier is EXPRATE+2. Whether that is intended is unverified.

The `ENABLE` count syntax is `number(x/y)`. `x` is the number of picks per `y` levels of the mob (`InstanceArea.java:534-584`, granted at `:1305-1319`). The header gives the same meaning (`areainstancetypes.txt`, header).

#### 1.5 Casting Transform (`invoke`)

- `invoke` (`:2497-2668`) first accepts a duration form, `<ticks> <type>` with a target, which just tick-downs an effect (`:2502-2511`). Otherwise it matches the type name, trying longer and shorter word groups until a type is found (`:2519-2529`).
- If the caster is already in that type, the cast fizzles: "^S<S-NAME> attempt(s) to conjure a powerful transformation, and fails." (`:2541-2547`, `:2442-2444`).
- After proficiency, a failed check still transforms 5% of the time (`:2580-2587`). A success creates a `SubThinInstance` named `<n>_<Area>` and a transitional room titled "Between Realities" (`:2589-2602`).
- Followers are moved with fade-in and fade-out messages (`:2637-2663`). Followers who are in combat are fled first (`:2649-2652`).

#### 1.6 Teardown and expiry

- Child tick (`:1427-1490`): when `DURATION` runs out (`--tickDown<=0`, `:1442-1446`) the effect unInvokes and the area is destroyed (`:2447-2458`, `:2203`). Passive area states are blocked for timed children (`:1450-1460`). Recovery and fatigue are applied to player occupants every `RECOVERRATE` pass (default 1 in `coffeemud.ini:691`) times `REAL_TICK_ADJUST_FACTOR` = 5 (`CharState.java:65`, `:1461-1487`).
- `destroyInstance` (`:2203-2380`): "This instance is fading away..." (`:2255`). Players are walked back toward their old room, up to 50 steps (`:2262-2273`). Player corpses return to their owner (`:2279-2303`). Each player gets an `EXPIRE` message whose value is the faction they earned inside (`:2314-2331`). Rooms are emptied, destroyed and the area deleted (`:2335-2378`).
- Faction earned inside is tracked as it happens: `FACTIONCHANGE` messages from players add to child data under `<name>/FACTION:<faction>` (`:1540-1576`).

#### 1.7 The Triumph ladder (shipped data)

`resources/skills/areainstancetypes.txt` defines one type, "Triumph", in 85 lines. The base line (`:66`) sets `DURATION=900` (ticks, about an hour at 4 seconds), `LIMIT="1/DAY"`, `PLAYFACTIONS="triumph.ini"`, `IEFFECT="Prop_NoCraftability Prop_ItemBinder"`, and the level formulas above. The other 84 lines are `LIKE="Triumph"` variants in four tiers of 21 (table in Content worth mining). Each variant gates on a leader mask (`LEADERREQ`) and grants one, two, three or four skill groups, all with `BEHAVE="CombatAbilities"`.

### 2. Moods (`Mood.java`)

#### 2.1 Data model

- `MoodType` (`Mood.java:96-131`) has 21 values: FORMAL, POLITE, HAPPY, HAPPYSILLY, SAD, ANGRY, RUDE, MEAN, PROUD, GRUMPY, EXCITED, SCARED, LONELY, REFLECTIVE, SILLY, APATHETIC, PASSIVE, HORNY, QUEER, LUSTY, SUBMISSIVE. Each has a `statChanges` mask, an adjective and an adverb.
- Only FORMAL (`+ADJCHA 17`, `:98`) and POLITE (`+ADJCHA 13`, `:99`) carry a mask. `invoke` checks it with `CMLib.masking().maskCheck` (`:1639-1645`), so a mood can require a charisma threshold. The mood's adjective is added to the wearer's ambiance (`affectPhyStats`, `:577-586`), so rooms show it.
- The misc text is the mood name. `setMiscText` accepts a name, an index into the enum, or `RANDOM` (`:363-404`).
- The effect is added as non-uninvokable (`:1661-1663`). Setting NORMAL removes it (`:1658-1659`).

#### 2.2 Speech rewriting (`okMessage`)

`okMessage` (`:661-1382`) acts only when the wearer is the source, the tool is null or the common language, and the message is speech, tell or a channel (`:667-676`). It finds the spoken words in the others' message, or in the target's message (`:678-680`). It then resolves an addressee with `target()` (`:618-659`): an explicit target, else the single other inhabitant, else one player among mobs, else null. The mood branch then rewrites the text and the message is rebuilt with `substituteSayInMessage` (`:1366-1376`).

Per-mood behaviour:

- FORMAL: archaic replacements from `formalReplacements` (`:251-361`, for example "you" to "thou", "is" to "ist", "will" to "shall"), a closing period, and a 10-in-15 chance to swap the verb (`:725-759`).
- POLITE: a handshake whenever the addressee changes (`:818-823`), one of seven courtesy phrasings or a bow or curtsy (`:824-861`), and a verb swap (`:862-878`).
- SUBMISSIVE: deferential phrasing, plus kneel or grovel socials at the target (`:883-919`).
- HAPPY and HAPPYSILLY: a smile social when the addressee changes, then a cheerful verb (`:955-985`).
- SAD: cry social when the addressee changes, then sigh, sob or sulk verbs (`:989-1021`).
- ANGRY: growl, snarl and similar verbs; the speech is upper-cased (`:1057`).
- RUDE: sneer, jeer and similar verbs (`:1062-1097`).
- MEAN: mock-insult phrasing (`:1136-1155`), and a 1-in-10 chance to start a fight with an addressee within 10 levels (`:1149-1155`).
- PROUD: a flex social when the addressee changes, then boast verbs (`:1160-1180`).
- GRUMPY: mutter or grumble, with a grumble social (`:1184-1201`).
- EXCITED: shout-type verbs, then 4 to 13 exclamation marks and upper case (`:1211-1240`).
- SCARED: cower social, a 50% stutter (`:1276-1277`), and wince or squeak verbs (`:1244-1278`).
- LONELY: sigh or whine verbs with an "alone" tag (`:1282-1308`).
- REFLECTIVE: each word boundary has a one-in-three chance of an ellipsis, a one-in-three chance of a sentence break with the next word capitalised, and otherwise nothing (`:1330-1361`).
- APATHETIC: a shrug or "not like I care" phrasing (`:925-950`).
- PASSIVE: everything lower case, speech turned to whispers and yells to plain speech (`:762-773`, see the bug in Problems).
- HORNY, LUSTY, QUEER: endearment prefixes or suffixes when a partner selected by `isLustyMatch` is in the room (`:201-214`, `:774-815`).

#### 2.3 Idle and event behaviour

- `tick` (`:406-575`) runs a countdown per mood. PASSIVE uses `roll(1,55,5)`, which is 6 to 60 ticks (`:432-433`). When it fires, a random passive social (four in the pool) is queued 500 ms later through `CMLib.threads().scheduleRunnable` and `enqueCommand` (`:437-452`). SILLY and APATHETIC work the same way (`:515-535`, `:546-566`). HORNY, LUSTY and QUEER pick a partner and use the passive pool with the partner's name as target (`:462-505`).
- `executeMsg` (`:1384-1554`): SILLY and HAPPYSILLY have a 33% chance per speech act to queue a social from a pool (`:1401`, `:1452`). The pool sizes are 149 for SILLY (with repeats used as weights), 43 for HAPPYSILLY and 26 for the flirtation pool (counted from `Mood.java:179-196`, `:220-227`, `:236-239`).
- PROUD: when the wearer kills someone (`TYP_DEATH`, `:1497-1499`), a boast goes out on the first of BOAST, GRATZ, ANNOUNCE, GOSSIP, OOC or CHAT that exists (`:1504-1514`, list at `:138-141`). Seven add-ons are possible (`:1518-1543`).

### 3. Injuries (`Injury.java`)

#### 3.1 Data model

- `injuries[]` has one slot per body part (`Race.BODY_PARTS`, `Race.java:553`, 16 parts; declared `Injury.java:53`). Each slot is a list of (limb name, severity 0 to 100) pairs (`:305-307`).
- `text()` serialises as `part:name:severity;` (`:197-217`). The loader reads the same format (`:291-310`). A `+limb=amount` form adds damage to an unaffected limb (`:224-286`), and a `source/limb` form is handled by the base class (`:288-289`).
- Body parts are indexed by `Race.BODYPARTSTR` (`Race.java:555-559`): ANTENNA 0, EYE 1, EAR 2, HEAD 3, NECK 4, ARM 5, HAND 6, TORSO 7, LEG 8, FOOT 9, NOSE 10, GILL 11, MOUTH 12, WAIST 13, TAIL 14, WING 15.

#### 3.2 Severity wording

`getHealthConditionDesc` (`:68-129`) describes each injury, and `displayText` shows it as `(Injuries: ...)` (`:132-138`). The thresholds (`:89-113`) are:

| Severity | Word | Notes |
|---|---|---|
| under 5 | a bruised | |
| under 10 | a scratched | |
| under 20 | a cut | |
| under 30 | a sliced | |
| under 40 | a gashed | |
| under 60 | a bloody | |
| under 75, or any torso | a mangled | torso never reaches the next words |
| under 100, or any head | a dangling | |
| 100 | a shredded | |

#### 3.3 Injury from a hit (`okMessage`)

- Trigger (`:625-640`): a damage message to this mob, value above zero, with the `<DAMAGE>` token in the target text. It fires when the message is a directed "source/limb" case, or when the HP threshold and chance allow it: `INJPCTHP >= round(HP/maxHP*100)` and a roll at or below `INJPCTCHANCE` (`:638-640`).
- Shipped values (`coffeemud.ini:340`): `INJURYSYSTEM=100,60,25,100,2,10,15,20,100`. Their mapping in `CMProps.java:3161-3166` gives chance 100, HP threshold 60, amputation HP percent 25, amputation chance 100, multiplier 2, minimum level 10. So a mob at or below 60% HP takes limb injuries on every hit.
- Limb choice (`:682-708`) is a weighted random over the still-unaffected limbs, with weights from `INJURYCHANCE` (`:62-65`). The weights are antenna 3, eye 3, ear 3, head 11, neck 3, arm 12, hand 5, torso 35, leg 13, foot 5, nose 3, gill 0, mouth 0, waist 3, tail 3, wing 3. Head and torso are excluded from amputation and some other checks by `Amputation.validamputees` (`Abilities/Misc/Amputation.java:130-142`: head, neck, torso, gills and mouth are false).
- Continuity: a trailing message from the same hit reuses the same limb (`lastMsg`/`lastLoc`, `:684-688`).
- Amount (`:709-712`): `BodyPct = round(damage/maxHP*100)`, and `LimbPct = max(1, BodyPct*INJMULTIPLIER)`. The injury entry gains that much, capped at 100 (`:759-761`).
- Message rewrite (`fixMessageString`, `:597-620`): the target token after `<DAMAGE>` becomes the possessive and the limb name, so "hits <T-NAME>" reads as "hits your arm".

#### 3.4 Amputation

When the limb reaches 100, or when `BodyPct > 5` and either the tool is electronics or `BodyPct >= INJPCTHPAMP` (`:762-764`), the code checks `INJPCTCHANCEAMP` and the minimum level (`:766-767`). Frost and gas weapons never amputate (`:768-779`). Amputation then removes the injury entry and adds a trailer runnable that calls `Amputation.damageLimb` and gives the mob a long-lasting `Amputation` effect (`:780-802`). The amputation class is outside this bucket.

#### 3.5 Healing (`tick`)

- At full HP, every injury is cleared and the effect ends (`:496-501`).
- When HP has risen since the last tick (`lastHP`, `:503`), the heal amount is `pct = round((HP - lastHP)/maxHP*100)`, at least 1 (`:531-533`). Random entries are picked and healed: an entry smaller than `pct` is removed and the rest carries over, and a larger one just loses `pct` (`:535-557`). Healing is therefore in percent of max HP, in step with HP regained. The player help text says the same: "your damaged parts will begin to heal at the same rate as your hit points are re-gained" (`resources/help/misc_help.ini:2166-2171`).
- With no injuries left the effect ends (`:523-528`).
- Infection (`:564-584`): for a non-monster, a 1% roll with a roll above CON*4 invokes `Disease_Infection` from a factory mob named after an injured limb.

#### 3.6 Other entry points

- `damageLimb` (`:349-413`): shows "^G<S-YOUPOSS> @x1 is injured!^?", then adds 10 severity to an existing entry (`:376-377`), or creates one at 10 (`:408`). A head hit has a 1-in-10,000 chance of `Disease_Vertigo` (`:384-394`). A hit when hygiene is high has the same chance of `Disease_FilthFever` (`:395-406`).
- `restoreLimb` (`:461-488`) removes an entry and ends the effect when nothing is left. `affectedLimbNameSet`, `isDamaged` and `unaffectedLimbSet` (`:321-346`, `:416-458`) serve the `LimbDamage` interface. The effect is also a `HealthCondition`.
- `invoke` (`:814-822`) copies the effect and starts it with `TICKS_ALMOST_FOREVER`.

### 4. Phobias (`Phobias.java`)

- Data (`:138-156`): a comma list. Each word is tried as a race name (`CMClass.findRace`), then as a race category (`getRaceCatMap`, `:94-108`), and otherwise kept as a lower-case object name matched with `containsOneOfString`.
- Check (`:158-235`): the effect counts its own ticks and acts on every fourth one (`CHECK_DOWN = 3`, `:85`, `:163`). It looks for a phobic inhabitant who can be seen (`:173-190`, 50% per candidate) or, when fewer than 20 items are on the floor, a phobic item (`:191-209`, 50%). The first hit triggers `reactToPhobia`.
- Reaction (`:274-339`): nothing happens if the mob already has Cowering or Fighter_Whomp (`:279-280`). Otherwise the current mood is saved and the mood is set to SCARED through the `MOOD` command (`:281-296`). Then:
  - 30%: invoke Cowering (`:297-303`).
  - Otherwise, in combat: flee (`:304-315`).
  - Otherwise, out of combat: 40% vomit, which costs 500 hunger points (`:318-323`); else 40% pass out for 3 ticks through Fighter_Whomp (`:325-334`); else flee by `beMobile` (`:335`).
  - Because the rolls are sequential, the out-of-combat chances are 28% vomit, 16.8% faint and 25.2% flee, after the 30% cower.
- Recovery (`:211-232`): when no reaction happened on a check and the mood is still SCARED, the saved mood is restored with the `MOOD` command.
- Prevention (`okMessage`, `:342-380`): eating or drinking a phobic target, or getting, pushing or pulling one, is refused with a short in-character message.
- Approach (`executeMsg`, `:383-402`): when a phobic source handles or moves toward the mob, there is a 20% chance of `reactToPhobia`.
- `invoke` (`:405-473`) applies a phobia set. A named race is used if valid. Otherwise each race is picked with chance 1/N, skipping the caster's own race (`:445-450`). The result is a non-uninvokable effect (`:460-462`).

### 5. Floating (`GravityFloat.java`)

- `invoke` (`:441-451`) runs `PossiblyFloater(target, auto).run()`, which uses the auto flag as the initial gravity guess (`:446-448`).
- `PossiblyFloater.run` (`:86-123`): `confirmGravity` decides whether the target has gravity. If it has none and no effect is present, a copy of the effect is added after a `MASK_ALWAYS` message from a factory mob named "gravity" (`:104-110`). The room shows "<S-NAME> start(s) floating around." (`:114`). If gravity has come back and the effect is not savable, it is removed (`:91-99`).
- `confirmGravity` (`:391-439`): an item keeps gravity when it is in a container, not gettable, rideable, owned by a mob, or a `TechComponent` (`:396-404`). A mob keeps gravity when riding or bound (`:410-414`). Inside a ship, gravity depends on the ship flag `NO_GRAVITY` (`:416-424`). In space the answer is the space object's mass compared to `MULTIPLIER_PLANET_MASS` (`:426-436`).
- Ending: each mob tick runs `checkStopFloating`, which removes the effect when gravity is back (`:147-161`, `:126-145`). A trailer runnable also checks on GET and on putting the item into a container (`:298-311`).
- Stats: weight becomes 1, the floating ambiance is added, and the flying disposition is set (`:163-170`).
- Movement penalties (`okMessage`, `:229-289`): a floating mob fails to touch others' items or mobs 80% of the time (`:246-262`). Entering, leaving, mounting or sitting fails 80% of the time for non-swimmers and 40% for swimmers (`:270-282`). A failed move prints "<S-NAME> flap(s) around, making little progress." (`:192`).
- Self-propulsion (`executeMsg`, `:318-386`): a floating mob that pushes or throws an item with a direction word in the text moves in the opposite direction through `CMLib.tracking().walk`, with `flyingAllowed` set during the move (`:374-381`). The direction words are compared with upper-case names (`:361`) but the message text is not upper-cased (`:342-345`). Whether this ever matches depends on the input's case; unverified.

### 6. Milkable (`Milkable.java`)

- Data (`:91-111`): milk type from `TYPE` (default `RawMaterial.RESOURCE_MILK`), a capacity of `PERDAY` (default 1000), a starting pool of 1000 (`:95`), 100 thirst quenched per drink (`:96`), and `DRINK` and `ON` flags.
- Regeneration (`tick`, `:123-154`): `refill += liquidHeld / ticksPerMudday`, where `ticksPerMudday = hoursInDay * millisPerMudHour / tickMillis` (`:139`). Whole units are added as they accrue (`:141-148`). A full day's yield therefore refills over one mud day.
- Consent: milking is allowed when `ON` is set, automatically for players and non-monsters (`:117-118`), when the cow is bound or held (`:162`), or for a monster whose start-room lord or group leader is milking (`:163-169`). The cow toggles it with "MILK ON/OK" and "MILK NO/OFF" (`:214-235`).
- Command "MILK x INTO y" (`:200-294`): the words are split on "into", the source is found in the room, and the fill makes a `RESOURCE` item of the liquid, weight 1, placed in the container or the owner's inventory (`:326-340`). Messages include "<S-NAME> milk(s) <O-NAME> into <T-NAME>." (`:273`) and "<T-NAME> won't seem to hold still for you." (`:288`).
- Drinking (`:295-299`): a drink aimed at the cow is redirected to the Milkable itself when it is drinkable and milking is allowed. The drink then restores thirst by 100 (`:309-318`).
- Milk never decays (`decayTime`, `:419-422`).

### 7. Grazing (`Grazing.java`)

- Racial trait for Cow, Buffalo, Bull, Minotaur and Calf (`Races/Cow.java:95`, `Buffalo.java:89`, `Bull.java:91`, `Minotaur.java:143`, `Calf.java:89`).
- Start (`invoke`, `:200-265`): the room must be outdoor plains (`:219-223`), the mob must not be fighting (`:225-229`), and must not be busy with a common skill (`isBusy`, `:187-198`). The effect is made long-lasting with a duration of 9999 ticks (`:251`).
- Intended model: every 6 ticks (`tickCount >= 6`, `:131`) grazing adds a cud, up to `3 + XLEVEL` (`getMaxCuds`, `:181-185`), with a message "You have filled a stomach (n/max)" (`:148`). Hungry mobs chew a cud and their hunger is reset to the maximum (`:161-175`).
- Actual behaviour: `tickCount` is declared (`:104`), tested (`:131`) and reset (`:133`, `:160`), but never incremented (grep of the file). So the cud branch never runs. `cuds` stays at 0, the chew branch never has a cud to chew, and hunger is never restored. While grazing, the only output is "<S-NAME> continue(s) grazing." (`:152`), every tick, until the room, combat, a busy skill or a failed show stops it.

### 8. Immunities (`Immunities.java`)

- Data (`:106-123`): `immunityTypes` maps 15 names to `CMMsg.TYP_*` codes, including ACID, COLD, FIRE, GAS, POISON, PARALYZE and SONIC. `ALL` expands to every type (`:135-138`). Any other word is looked up as a behaviour (removed by ID) or an ability (removed by ID) (`:145-156`).
- Veto (`okMessage`, `:195-221`): when the host is not a mob, or the mob is the living target, and the message's target or source minor code is immune, and the major code is malicious, damage or legal warrant, the message is refused with "<T-NAME> seem(s) immune to @x1 attacks from <S-NAME>." (`:213-216`). Self-generated gas is refused silently (`:203-204`). For rooms and areas the target test is skipped (`:198`), so the effect vetoes matching malicious messages passing through them.
- Removal (`tick`, `:160-193`): when the count of behaviours changes, the listed behaviours are removed. When the count of effects changes, listed effects are unInvoked and removed (`:178-191`). This is removal, not prevention, so a listed effect is removed on the next tick after it lands.
- Invocation (`:223-256`): `TICKS=n` or a bare number sets duration, and any immunity names are gathered. The effect is applied only if at least one immunity name was given (`:245`). The call returns true either way.

### 9. Item respawn (`ItemRejuv.java`)

- `loadMeUp` (`:124-138`) attaches an effect to an item in a room and starts a tick-down with `TICKID_ROOM_ITEM_REJUV` for `item.phyStats().rejuv()` (`:137`).
- `loadContent` (`:88-107`) walks the room and records each item in the container tree. It keeps the original (`contents`) and a stopped copy (`ccontents`), and re-links copies inside copied containers.
- `tick` (`:206-257`): if the room is camped by players and the item cannot be camped, or the `ITEMREJUV` security flag is disabled, it postpones (`:225-236`). `verifyFixContents` (`:162-203`) puts a fresh copy back for any original that is missing and not carried by a player. If the item itself is gone, `loadMeUp` rebuilds from the first recorded item (`:241-247`).
- Containers have their doors and locks reset to their defaults (`:248-254`).

### 10. Gait (`Gait.java`)

- Data: `GAIT_LIST` in `resources/lists.ini:1045-1063` gives 18 entries: name, present participle, arrive text and leave text (table in Content worth mining).
- `invoke` (`:136-251`): "gait <name>" matches a list entry by exact name then by prefix (`:161-196`). `RANDOM` rolls `roll(1, 18+3, -1)`, which gives NORMAL 3 times in 21 (`:163-167`). A random pick of the current gait returns silently (`:212-213`). Otherwise the matched gait is added as an effect with misc text `ARRIVE`, `LEAVE` and `STATE` (`:233-245`) and the room sees "<T-NAME> start(s) <state>." (`:225-227`).
- `affectCharStats` sets the arrive and leave strings the room uses when the mob enters or leaves (`:100-105`). `displayText` shows "(<state>)" (`:52-58`).
- The effect is auto-invoked and not uninvokable (`:84-93`).

### 11. Racial and malicious combat traits

- **Gore** (`Gore.java:36-130`): auto-invoked racial trait. A piercing or natural weapon hit has its damage multiplied by `1.5 + 0.1 * XLEVEL` (`:100-106`, `:124-125`). Each weapon attack has a 10% chance of a proficiency gain (`:118-119`).
- **PoisonousBite** (`PoisonousBite.java:37-164`): the BITE command. It needs a mouth and melee range (`:114-123`), and is usage MOVEMENT (`:92-96`). Damage is `roll(1, adjLevel/4 + XLEVEL, 0)` (`:137-138`), halved when the target saves (`:143-144`), and dealt as piercing (`:145`). The poison is the ability named by the effect's misc text, default "Poison" (`:146-155`), applied at half proficiency (`:153`).
- **Hamstring** (`Hamstring.java:37-215`): needs a leg (`:175-179`). Duration is `2*adjLevel + 10*XTIME` (`:195`). Movement loss is `roll(adjLevel,6,0) + 5*XLEVEL`, capped at max movement (`:196-198`). DEX falls by 4, to a floor of 0 (`:119-123`). Fleeing and autorun are refused (`:131-148`). Healing that restores HP removes the effect (`:161-163`). The failure message is "<S-NAME> attempt(s) to tag this place, but can't get it out." (`:212`), which is copied from another ability.
- **Paralysis** (`Paralysis.java:36-183`): sets `CAN_NOT_MOVE` (`:119`). Duration is `10 + max(0, adjLevel - targetLevel)/10` ticks (`:169-172`). The message on expiry is "The paralysis eases out of your muscles." (`:133`). The effect uses `FLAG_UNHOLY | FLAG_PARALYZING` (`:101`).

### 12. NPC loyalty and begging

- **Loyalty** (`Loyalty.java:38-174`): the misc text names a player (`NAME`) and whether the mob may teleport-wander (`TELEPORT`) (`:86-91`). The mob cannot stop following while loyal (`:117-123`). Looking at the mob shows "is loyal to you" to the named player (`:132-136`). Each mob tick, when the mob is free to behave, the effect may wander to the player's room (`:157-159`) and follows the player when they share a room (`:162-170`). If the player cannot be loaded, the effect removes itself (`:99-105`). The check interval is broken (see Problems).
- **FoodBegging** (`FoodBegging.java:37-212`): refused in combat for either party (`:105-115`). It needs the target to be seen (`:121`). Success prints one of seven begging emotes (`:126-137`): rubbing a leg, staring with wide eyes, sitting pretty, rolling on the ground, sad eyes, hungry eyes and a rumbling stomach. After a tick, the target's first food item is given by `postGive`, or by the FEED command as a fallback (`:169-199`). Food inside a container is used only if nothing loose is carried (`:150-165`). With no food, the target says "You feel lousy for not having anything to offer" (`:201-202`).

### 13. Breath weapons

The four classes are subclasses of `Dragonbreath.java` (outside this bucket, read for the mechanics). Each one only sets its misc text and triggers.

- `Dragonbreath.invoke` (`:264`) needs targets (`:265-269`), the ability to breathe here (`:270-273`), and a mouth (`:274-277`). The colour comes from `getBreathColor` (`:175-219`): the misc text's first letter, or for a dragon race the first colour name found in its name or display text (`DragonColors`, `:250-261`), or otherwise a random colour (`:213-215`).
- Damage (`:413-428`): `levelBy = max(1, (level + 2*XLEVEL + 2*X1)/4)` (`:416-418`). Normal breath deals `roll(levelBy,6,levelBy)`, and "lesser" (a misc-text flag, `:159`) deals `roll(levelBy,4,0)` (`:420-422`). A saved target takes half (`:425-426`).
- Failure: gas, pebbles, death and dust each fail 50% of the time (`:355`, `:365`, `:375`, `:385`).
- Dust does no damage and applies a malicious affect for `2 + (X1+XLEVEL)/4` ticks (`:407-411`).

| Class | Misc text | Colour | Weapon type | Strike type | Notes |
|---|---|---|---|---|---|
| `Firebreath.java:54-58` | "fire" (setMiscText forced) | f | `TYPE_BURNING` | `TYP_FIRE` | Message "blast(s) flames" |
| `Gasbreath.java:54-58` | "gas" (setMiscText forced) | g | `TYPE_GASSING` | `TYP_GAS` | 50% failure (`Dragonbreath.java:355`) |
| `Frostbreath.java:48-54` | "cold" | c | `TYPE_FROSTING` | `TYP_COLD` | Injury never amputates after frost (`Injury.java:772-773`) |
| `Lighteningbreath.java:35-54` | "lightning" | l | `TYPE_STRIKING` | `TYP_ELECTRIC` | Display name "Lightningbreath", ID misspelled |

## Content worth mining

**Triumph instance ladder** (`resources/skills/areainstancetypes.txt`, 85 lines of one type).

| Tier | Gate (per line) | Entries | Skill groups per entry |
|---|---|---|---|
| Base (line 66) | `LEADERREQ="-FACTION +TRIUMPH0"`; `DURATION=900`; `LIMIT="1/DAY"` | 1 | none (template) |
| I | `-FACTION +TRIUMPH1 -DAY +n`, n = 1 to 20 (n=20 is used twice, lines 87-88) | 21 | 1 |
| II | `-FACTION +TRIUMPH2 -DAYOFYEAR "+Nth 8"` | 21 | 2 |
| III | `-FACTION +TRIUMPH3 -SEASON +<season> -YEAR "+k of 5"` | 21 | 3 |
| IV | `-FACTION +TRIUMPH4 -YEAR "+k of 21"` | 21 | 4 |

The 21 skill groups are: abjuration, acidizing, breeding, burning, combat_lore, creation, criminal, cursing, electrocuting, evangelism, evasive, freezing, healing, illusion, martial_lore, plant_growth, preserving, stealing, trapping, transmutation and vexing. Tier I's electrocuting entry also grants `Spell_Lightning`. Each group grants `@x7/100` picks per 10 mob levels (`number((@x7/100)/10)`: x = `@x7/100`, y = 10, `InstanceArea.java:534-584`), where `@x7` is the group's best faction value (faction maximum 999999, `resources/factions/triumph.ini`). The picks are drawn from the set of abilities that share the flag, domain or type, without repeats (`InstanceArea.java:1299-1320`).

**Gaits** (`resources/lists.ini:1046-1063`).

| Name | State word | Arrive | Leave |
|---|---|---|---|
| AMBLE | ambling | ambles in | ambles |
| TIPTOE | tiptoeing | tiptoes in | tiptoes |
| SKIP | skipping | skips in | skips |
| THUNDER | thundering | thunders in | thunders |
| STOMP | stomping | stomps in | stomps |
| GLIDE | gliding | glides in | glides |
| SLIDE | sliding | slides in | slides |
| STUMBLE | stumbling | stumbles in | stumbles |
| WANDER | wandering | wanders in | wanders |
| WADDLE | waddling | waddles in | waddles |
| DRAG | dragging | drags in | drags <S-HIM-HERSELF> |
| DANCE | dancing | dances in | dances |
| SAUNTER | sauntering | saunters in | saunters |
| DRIFT | drifting | drifts in | drifts |
| MEANDER | meandering | meanders in | meanders |
| STROLL | strolling | strolls in | strolls |
| STRIDE | striding | strides in | strides |
| SASHAY | sashaying | sashays in | sashays |

**Moods** (`Mood.java:96-131`). The 21 moods with the adjective and adverb shown in the room and used in the speech rewrite (`:98-118`):

| Mood | Adjective | Adverb | Mask |
|---|---|---|---|
| FORMAL | formal | formally | CHA 17 |
| POLITE | polite | politely | CHA 13 |
| HAPPY | happy | happily | |
| HAPPYSILLY | happy | happily | |
| SAD | sad | sadly | |
| ANGRY | angry | angrily | |
| RUDE | rude | rudely | |
| MEAN | mean | meanly | |
| PROUD | proud | proudly | |
| GRUMPY | grumpy | grumpily | |
| EXCITED | excited | excitedly | |
| SCARED | scared | scaredly | |
| LONELY | lonely | lonely | |
| REFLECTIVE | reflective | reflectively | |
| SILLY | silly | sillily | |
| APATHETIC | apathetic | apathetically | |
| PASSIVE | passive | passively | |
| HORNY | horny | hornily | |
| QUEER | queer | queerly | |
| LUSTY | lusty | lustily | |
| SUBMISSIVE | submissive | submissively | |

Mood data pools (names only, the wording is in source): a passive pool of four socials; a silly pool of 149 entries; a happy-silly pool of 43; a flirtation pool of 26 used for the three romantic moods; and 28 mock-insult phrases for MEAN, several of them crude or containing a slur-like term. The phrases are not reproduced here (`Mood.java:143-173`).

**Injury wording** (`Injury.java:89-113`). The severity ladder in section 3.2 is reusable as a flavour table for wounds: nine words from "bruised" to "shredded", with head and torso overrides.

**Breath types** (section 13). A table of four elemental breaths keyed by name, each mapping to a damage type that other systems (here, Injury's amputation rule) use.

**Begging emotes** (`FoodBegging.java:126-137`). Seven short NPC gestures, one of which is chosen at random per attempt.

**Species fear structure** (`Phobias.java:94-108`, `:138-156`). Phobias can name a race, a race category or a free-text object word; the same parser covers all three.

## Notable design ideas

- **Instances by re-keying the same area.** `convertToMyArea` (`InstanceArea.java:2390-2401`) keeps the `#room` suffix, so the authored area's rooms, exits and item lists are copied by ID instead of being duplicated by hand. Exits in the copy point at the copy, using the rewrite at `:1005-1026`.
- **Content defines difficulty.** Level, elite tier, enabled skills and mob factions are formulas over the parent's level range and the group's faction value (`@x1`-`@x7`). A ladder of 85 short lines turns a faction and the calendar into a schedule of challenges with no code change.
- **Zapper masks as the gate.** `LEADERREQ` uses the same mask language as the rest of the server (`MUDZapper`). A builder writes "faction +TRIUMPH2 on this day" as text.
- **Speech as a filter.** `Mood` never changes stats (except the charisma mask on two moods). It rewrites the message in `okMessage` and schedules socials with `enqueCommand`, so one effect changes the voice of a character.
- **Wounds as a second health bar.** Injury records a percentage per limb, heals it in step with HP regained, and turns full damage into amputation. Players see the state as words, not numbers.
- **Fear as a reaction chain.** The phobia reaction (`Phobias.java:274-339`) rolls in sequence (cower, then flee or vomit or faint or flee), which gives plausible varied behaviour from few rules.
- **Floating ends by itself.** `GravityFloat` stops when the entity touches gravity again. It does this with trailer runnables on GET and PUT (`:298-311`) and a per-tick check (`:147-161`), not with an explicit "land" action.
- **Renewable resource with a fractional accumulator.** `Milkable` adds `liquidHeld/ticksPerMudday` to a float each tick and books whole units (`:139-148`). The same pattern would suit any regrowing resource.
- **Respawn keeps the contents.** `ItemRejuv` snapshots a container tree (`:88-107`), so a respawned chest comes back with its contents as they were.

## Problems and cruft

Bugs and inconsistencies found by reading (each checked against the cited lines):

- **Loyalty's check interval is zero.** `INTERVAL = (2 * 60) / 4000` is integer arithmetic and evaluates to 0 (`Loyalty.java:72`). The countdown is reset to 0 and `--checkDown<=0` is true on every mob tick (`:148-150`). The intended two-minute interval never applies, so the wander and follow check runs every tick.
- **Grazing's cud model is dead code.** `tickCount` is never incremented (`Grazing.java:104`, `:131`, `:133`, `:160`). Cuds never accumulate and hunger is never restored (`:161-176`, `:207-211`). Grazing does nothing except print "continue(s) grazing" each tick.
- **Changing the say verb corrupts yells.** `changeSay` skips `y+6` characters after the match (`Mood.java:600`), which is right only for "say(s)". PASSIVE calls it with `"yell(s)"` (`:771`), a 7-character word, so the leftover ")" is kept: "yell(s)" becomes "say(s))". The command uses the same lower-case verb (`Commands/Yell.java:54`), so the path applies to yelled speech. Confirmed by arithmetic on the code; not run.
- **Hamstring's failure text is wrong.** It says the attempt is to "tag this place" (`Hamstring.java:212`), which is copied from another ability.
- **Lightning breath ID is misspelled.** The ID is "Lighteningbreath" (`Lighteningbreath.java:37`), but the display name is "Lightningbreath" (`:40`). Other code relies on the misspelling (`Commands/Import.java:1686`, `:3661`; `Abilities/Druid/Chant_SummonElemental.java:239`), so it cannot be fixed without touching them.
- **Gait's display name can be null.** `displayText` returns `stateWord` when it is null (`Gait.java:55-56`).
- **Dragonbreath colour re-rolls on every call.** When the misc text is empty and the invoker is not a dragon, `getBreathColor` picks a random colour each time (`Dragonbreath.java:213-215`). `flags()` calls it again (`:221-248`), so an effect can change its elemental flags between checks. The four subclasses are not affected because they fix their text.
- **Instance hard-bump is never used.** `hardBumpLevel` (`InstanceArea.java:123`) is only copied (`:2632`), never set to a non-zero value. The branches that use it (`:1129`, `:1209-1210`) are therefore inert. `hardBumpTimeout` (`:132`) is unused.
- **Six instance keys are dead.** `ARMORADJ`, `ATTACKADJ`, `SAVEADJ`, `HPADJ`, `DAMAGEADJ` and `DESCRIPTION` are declared (`:141-191`) and never read. `OPPOSED` and `TRANSITIONAL` are documented in the data header but not implemented.
- **Documented `@x6` differs from the code.** The header says `@x6 = exprate` (`areainstancetypes.txt`, header), but the code supplies `EXPRATE+1` (`InstanceArea.java:521`) and the default formula adds 1 more (`:489`).
- **Bad input throws.** `setMiscText` throws `IllegalArgumentException` for an unknown type (`InstanceArea.java:416`) or a missing `LIKE` target (`:383`). The `LIKE` merge in the loader can throw an NPE if the target name is absent (`:2114`).
- **Typo in the data.** `areainstancetypes.txt` line 155 reads "Cursing-Stealing-/Martial-Electrocuting". Tier I's last two entries both gate on `-DAY +20` (lines 87-88).
- **Typo in the instance text.** "realiities" (`InstanceArea.java:2601`).
- **Injury's documented defaults disagree.** The comment in `coffeemud.ini` says the default is `100,60,10,100,4,10,15,20,100`, but the shipped line (`:340`) is `100,60,25,100,2,10,15,20,100`, and the code fallbacks are `100,40,10,100,4,10` (`CMProps.java:3161-3166`).
- **Injury heal indices can go stale.** `choicesToHeal` stores list indices (`Injury.java:513-518`). After an entry is removed (`:547`), later stored indices in the same list may point at another injury. The pass is rebuilt every tick, so the effect is minor.
- **Injury's invoke sets a field on the wrong object.** `super.tickDown=2` (`:818`) is set on the invoking instance, and the copy then gets `TICKS_ALMOST_FOREVER` (`:820`). The first assignment seems to have no effect.
- **Immunities with only ticks does nothing.** `invoke` returns true without applying an effect if no immunity names are given (`Immunities.java:245-255`).
- **Rarely reached paths.** In `InstanceArea.okMessage`, if no instance room is found after 1000 tries the entrant is not redirected but the instance remains (`:1995-2012`). The result is an empty instance until timeout. Unverified.

Multiplayer assumptions:

- `InstanceArea` is built around groups, group leaders, per-leader entry limits, per-group factions and "the best player faction" (`:1581-1612`, `:1813-1852`, `:1907-1912`). Single-player needs one party, one clock and no limits.
- `Mood`'s PROUD effect broadcasts to channels (`:138-141`, `:1504-1514`). Meaningless for one player.
- `Loyalty` is tied to a named, loadable player (`:99-105`).
- `ItemRejuv` postpones respawns for camping players (`:225-231`). Meaningless without other players.
- `FoodBegging` and `Phobias` assume a room with other inhabitants. That is fine for NPCs.

Content not worth copying: the insult list and the sexual-theme mood pool (both are for a shared-chat MUD); the instance type "Triumph" as shipped, which needs a faction the single-player game would not have.

## Takeaways for a Python SUD

- **Instances as templates, not groups.** Model an instance as a dataclass: `name`, `duration_ticks`, `level_rule`, `room_overrides`, `mob_overrides`, `skill_grants`. Load from TOML, not from a bespoke line format. Keep the `LIKE` idea as a dict merge, and the `#room` re-keying as a simple id suffix.
- **Gates as predicates.** Replace zapper masks with plain callables such as `lambda w: w.faction("triumph") >= 2 and w.season == "winter"`. The calendar ladder becomes a list of `(predicate, variant)` pairs, chosen once per day.
- **A safe level formula.** Do not reuse CMath's operators. Write level as Python: `level = max(1, instance_level + (mob_level - min_lvl) / (max_lvl - min_lvl + 1) * exp_rate)`. Make the multiplier explicit and single-counted.
- **Moods as a speech filter.** Make `Mood` an `Enum` with a table of rules (adjective, adverb, verb list, social). A function `speak(text, mood, ctx) -> str` does the rewrite. Idle socials become events on the game clock; roll with `random` and the same probabilities.
- **Wounds as a dict.** `limbs: dict[str, int]` (0 to 100). Heal in proportion to HP regained. Severity words are a sorted threshold list with `bisect`. Amputation thresholds go in config, not code.
- **Fear as tags.** Give NPCs and objects tags, and a per-tick check on room entry. The reaction chain (cower, flee, faint, sick) is a short weighted choice; keep the probabilities in data.
- **Immunity on apply.** Store `immune: set[DamageType]` and check it when damage is applied. Avoid the count-change removal trick; it exists because CoffeeMud re-checks effect lists.
- **Floating as a status.** Keep "airborne" as a status flag with a `can_touch` check. Drop the space-gravity code unless the game has space travel.
- **Renewable nodes.** A resource with `capacity`, `regen_per_day` and a float accumulator: `self._acc += regen * dt; units = int(self._acc); self._acc -= units`.
- **Respawn with snapshots.** Use `copy.deepcopy` of an item subtree at spawn time, with a respawn timer on the game clock. Drop the camping checks.
- **Breath as a damage formula.** Reuse the dice pattern `levelBy = max(1, (level + bonuses) // 4)`, then `roll(levelBy, 6, levelBy)` for full and `roll(levelBy, 4, 0)` for lesser. Breath colour is data keyed by element.
- **Drop the multiplayer parts.** Loyalty's named-player follow, PROUD broadcasts, group factions and per-leader limits all need rethinking for single-player. Replace them with per-NPC relationship state and per-quest instance rules.

## Coverage

- **Read in full (all 20 files):** InstanceArea.java, Mood.java, Injury.java, Phobias.java, GravityFloat.java, Milkable.java, Grazing.java, Immunities.java, ItemRejuv.java, Gait.java, Hamstring.java, FoodBegging.java, Paralysis.java, Loyalty.java, PoisonousBite.java, Gore.java, Firebreath.java, Gasbreath.java, Frostbreath.java, Lighteningbreath.java. The package line, imports and licence header were skipped in every file.
- **Read in full, outside the bucket:** Dragonbreath.java (for the breath mechanics).
- **Read in full, data:** resources/skills/areainstancetypes.txt (header and Triumph ladder); resources/lists.ini (GAIT_LIST lines); resources/factions/triumph.ini (header and the first settings).
- **Searched or sampled:** StdAbility.java:2185-2200 (maliciousAffect); CMath.java:1400-1470 (operators); CMParms.java (parseEQParms, parseDelimitedParenList, parseSpaceParenList); Factions.java:195-225 (faction file names); Amputation.java:130-142 (validamputees); CMProps.java:3160-3166 (INJURYSYSTEM mapping); coffeemud.ini:320-345 and :691; resources/help/misc_help.ini:2160-2172; Commands/Yell.java:54; Race.java:553-559 (body parts); Races/Cow.java, Buffalo.java, Bull.java, Minotaur.java, Calf.java (grazing grants, grep only); Commands/Import.java and Druid/Chant_SummonElemental.java (Lighteningbreath references, grep only); Areas/PlayerInstanceArea.java and Prop_PlayerInstance.java (other instancing system, grep only).
- **Not covered:** the spells and abilities named as dependencies in this bucket (Cowering, Fighter_Whomp, Disease_*, Poison, CombatAbilities behaviour) beyond their call sites.
