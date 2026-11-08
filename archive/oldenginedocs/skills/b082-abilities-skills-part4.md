# Abilities/Skills, part 4: trackers, sea skills, knock-downs, influence and observational crafts

> Part of the CoffeeMud NEWOVERVIEW. Bucket: `b082` (Abilities/Skills [part 4/4: Skill_SeaCharting .. StdSkill]) · 39 files · 9385 lines of Java.

Citations are `file.java:line`. A bare file name means it is under `com/planet_ink/coffee_mud/Abilities/Skills/`; other files are given with their folder, relative to the CoffeeMUD folder. Everything here was read from source; nothing was run. Anything I did not trace is marked "unverified".

## Summary

These 39 classes are the back half of CoffeeMud's skill family. Most are `StdSkill` subclasses that do one thing when typed (`invoke()`) and sometimes leave a timed effect on the character that reacts through `tick()`, `executeMsg()` or `okMessage()`. The group covers three trail-following trackers (NPCs are walked along a route; players are steered by repeated LOOK), a sea and caravan family that keeps chart data in the skill's own text and turns a sea voyage into a timed hidden state, survey and lore skills that reveal facts gated by expertise, knock-down and damage skills that share one difficulty formula, and speech, mood and observation skills. For a single-player Python game the most reusable parts are the trail engine and tracker state machine, the points-gated area survey, the expertise tiers of Ship Lore, and the stowaway voyage modelled as a timer.

## Key files and types

| File or class | Role | Lines |
|---|---|---|
| `Skill_Track.java` | Trail to a named mob, room or area; per-tick state machine; NPC door handling; persistence | 705 |
| `Skill_UnearthDemography.java` | Time-sliced area survey into a shared static table; points-gated facts | 692 |
| `Skill_WildernessLore.java` | Terrain, climate, resource and animal-sign report | 408 |
| `Skill_StableMount.java` | Stables mounts; stored mounts are serialized into the ability's text | 378 |
| `Skill_Stowaway.java` | Hides in a cargo box for a fixed-length voyage to a random or charted shore | 341 |
| `Skill_TrackFriend.java` | Trail to a friend (group, clan or friends list), scent-limited | 339 |
| `Skill_TrackCriminal.java` | Trail to a mob with an outstanding warrant | 335 |
| `Skill_ShipLore.java` | Expertise-tiered facts about a ship | 333 |
| `Skill_TradeCharting.java`, `Skill_SeaCharting.java` | Chart points as `;`-separated room IDs in the skill's text | 331, 326 |
| `Skill_SeaNavigation.java` | Course from a sea chart point, or by name in FULL mode | 282 |
| `Skill_SeaLegs.java`, `Skill_SeaManeuvers.java` | Passive shipboard protection and in-combat turn rewriting | 136, 130 |
| `Skill_TieDown.java` | Binds a water or air rideable, or a ship the caster has rights to, in a seaport | 232 |
| `Skill_Trip.java`, `Skill_Slip.java`, `Skill_Trample.java`, `Skill_TailSwipe.java` | Opposed knock-down and damage skills | 271, 236, 253, 232 |
| `Skill_Subdue.java` | Non-lethal subdue through a virtual HP pool | 227 |
| `Skill_Stonecunning.java` | Passive trap perception with a six-room cache | 225 |
| `Skill_SpreadHate.java`, `Skill_SpreadApathy.java`, `Skill_Stoicism.java` | Mood and disease influence skills | 286, 190, 155 |
| `Skill_Shush.java` | Silencing: converts speech to whispers; reverses in bookshops | 244 |
| `Skill_TurnUndead.java` | Turns undead: destroys them or makes them flee | 179 |
| `Skill_Write.java`, `Skill_SecretWriting.java` | Writing on items; writing in invisible ink | 166, 130 |
| `Skill_Skillcraft.java`, `Skill_Spellcraft.java`, `Skill_Thiefcraft.java` | Learn skills and spells by watching others | 162, 162, 58 |
| `Skill_TwoWeaponFighting.java`, `Skill_TwoDaggerFighting.java` | Off-hand attack each tick | 203, 103 |
| `Skill_Whipsmack.java` | Leather flail strike | 166 |
| `Skill_UndeadInvisibility.java` | Undead cannot target an unseen owner outside combat | 118 |
| `Skill_Swim.java` | One step through water | 253 |
| `Skill_SeaMapping.java` | Sea map making; extends `Skill_Map` (not in bucket) | 131 |
| `Skill_WandUse.java`, `Skill_ShardUse.java` | Marker skills read by wands and shards | 99, 99 |
| `StdSkill.java` | Base class for most skills here | 69 |

Not in the bucket but central: `Abilities/StdAbility.java` (the real base of four skills here), `Libraries/MUDTracker.java` (trail engine), `Libraries/Sense.java` (trackability), `Abilities/Skills/Skill_Map.java` (base of Sea Mapping).

## How it works

### 1. Shared patterns

- **Base classes.** `StdSkill` (`StdSkill.java:36-68`) extends `StdAbility`, fixes classification to `ACODE_SKILL` and targets mobs. Four skills extend `StdAbility` directly: `Skill_UnearthDemography.java:39`, `Skill_StableMount.java:40`, `Skill_TrackFriend.java:38`, `Skill_TrackCriminal.java:36`. Chains: `Skill_SeaMapping` extends `Skill_Map` (`Skill_SeaMapping.java:35`, `Skill_Map.java:35`); `Skill_SecretWriting` extends `Skill_Write`; `Skill_TwoDaggerFighting` extends `Skill_TwoWeaponFighting`; `Skill_Thiefcraft` extends `Skill_Skillcraft`.
- **Three shapes of skill.**
  1. Typed command. `invoke(mob, commands, givenTarget, auto, asLevel)` parses arguments, checks state, calls `super.invoke` (base checks, cost, cast), then `proficiencyCheck(mob, adjustment, auto)`. Failure goes through a fizzle helper such as `beneficialWordsFizzle` or `maliciousFizzle`. Example: `Skill_Track.java:421-704`.
  2. Timed effect. `beneficialAffect` or `maliciousAffect` attaches an effect that reacts through `tick(ticking, tickID)` (per-mob tick, `Tickable.TICKID_MOB`), `executeMsg` and `okMessage`. Examples: the two-tick knock-down at `Skill_Trip.java:262`; the tracker copy added to the mob at `Skill_Track.java:678-684`.
  3. Passive auto skill. `isAutoInvoked()` true and `canBeUninvoked()` false, so it is always on: `Skill_Stonecunning.java:81-91`, `Skill_TwoWeaponFighting.java:81-91`, `Skill_SeaLegs.java:69-79`, `Skill_SeaManeuvers.java:69-79`, `Skill_Stoicism.java:81-91`, `Skill_UndeadInvisibility.java:81-91`, `Skill_Skillcraft.java:75-85`, `Skill_Spellcraft.java:75-85`.
- **Time.** `TIME_TICK = 4000` ms by default (`com/planet_ink/coffee_mud/core/CMProps.java:681`), overridable with TICKTIME (`CMProps.java:2720`). Durations are counted in ticks.
- **Checks.** `StdAbility.proficiencyCheck(mob, adjustment, auto)` (`StdAbility.java:1441`) starts from `proficiency()` (`:1452`) and applies the caller's adjustment when a mob is given (`:1453`). The rest was not traced (unverified). When `auto` is true some gates are skipped (`Skill_Trample.java:194-207`) and messages are sent with `MASK_ALWAYS` (`Skill_Track.java:672`).

### 2. Trail data and the tracking engine

- Trails are lists ordered destination first and location last. `findTrailToRoom` builds the list backward from the destination, always stepping to the neighbour with the lowest index in the radiant list, and stops when it reaches the location (`com/planet_ink/coffee_mud/Libraries/MUDTracker.java:342-381`). The interface javadoc says the same (`Libraries/interfaces/TrackingLibrary.java`, above `trackNextDirectionFromHere`).
- `trackNextDirectionFromHere(theTrail, location, openOnly)` (`MUDTracker.java:464-496`) returns 999 when `location == theTrail.get(0)` (arrived). Otherwise it returns the direction to the adjacent room with the smallest trail index below the current one, or -1 if there is none. `openOnly` skips closed exits (`MUDTracker.java:485`). Track passes `false` (`Skill_Track.java:375`, `:684`). TrackFriend and TrackCriminal pass `true` (`Skill_TrackFriend.java:203`, `:331`; `Skill_TrackCriminal.java:199`, `:327`).
- Search radius, where XLEVEL and XMAXRANGE are the skill's expertise and max-range levels:
  - Track: `50 + 10*(XMAXRANGE + XLEVEL)` (`Skill_Track.java:497`).
  - TrackFriend: `50 + 2*XLEVEL + 10*XMAXRANGE` (`Skill_TrackFriend.java:289`).
  - TrackCriminal: `50 + 10*XLEVEL + 20*XMAXRANGE` (`Skill_TrackCriminal.java:286`), and nearby areas `1 + XLEVEL` (`:241`).
- Trackability. `Sense.canTrack(P)` is false when `PhyStats.CAN_NOT_TRACK` is set on P (`Libraries/Sense.java:153-156`; the bit is `Common/interfaces/PhyStats.java:433`, value 262144). All three trackers set that bit on the tracker itself (`Skill_Track.java:138-142`, `Skill_TrackFriend.java:184-188`, `Skill_TrackCriminal.java:179-184`), so a character who is tracking cannot be tracked. Callers of `canTrack` were not traced (unverified).

### 3. Skill_Track

Fields (`Skill_Track.java:48-55`): `theTrail` (remaining route), `nextDirection` (sentinels: -2 idle, -1 trail ended, 999 arrived, -999 finished; zero or more is the next direction), `andBefriend`, `persistantV`, `targetMob` (a WeakReference), `noUnlock`, and `cacheCode`.

Argument grammar (`:436-563`). A leading `and follow` or `and befriend` makes the tracker follow the target on arrival; a leading `and persist` makes it retry. Trailing words: `RADIUS=n`, `LANDONLY`, `NOUNLOCK`, `NPC` (NPCs avoid water and air unless they swim or fly, `:525-536`), `NOAIR`, `NOWATER`, and `FLAG=<TrackingFlag>`. Tracking the same target again gives "You are already tracking that!" (`:468`). A bare `stop` ends tracking (`:476-480`).

Target resolution (`:566-630`). A named room or area is used first. Otherwise a radiant search looks for a named NPC (`FilterMOBName`, `:380-403`), and an online player who is visible is preferred (`:616-623`). If the target is in the same room the reply is "Try 'look'." (`:586`).

Per-tick control (`tick`, `:178-337`):
- On 999: "The trail seems to pause here." (`:200`), then the effect ends. With a target and `persist`, the tracker schedules a re-track after 30000 ms (`bePersistant`, `:154-176`, delay at `:172`).
- On -1: "The trail dries up here." (`:216`); the sentinel becomes -999 and persistence is tried (`:217-220`).
- On a direction: "The trail seems to continue <dir>." (`:225`).

Monsters walk themselves (`:227-330`). Before stepping, a closed door on the path is opened, and a locked one is unlocked first unless `noUnlock` is set (`:249-261`). If the exit still is not open, the trail ends (`:271-272`). After `CMLib.tracking().walk(mob, dir, false, false)` (`:277`), followers who are bound or held are walked along (`:281-295`). The far-side door is then closed, and relocked if the tracker had unlocked it (`:296-321`).

Players do not move under the tick. The tick sets `nextDirection = -2` (`:331-332`), and the direction is refreshed when the player types LOOK (`executeMsg`, `:363-377`). So a player's tracker is a LOOK-driven hot and cold hint.

Encounter (`checkIfMobFound`, `:339-360`). When the target is in the same room and in the game, the trail stops with "The trail seems to pause here." and, if following was requested, `CMLib.commands().postFollow` runs (`:350-354`).

Route cache (`:657-664`). When `cacheCode == 1` and there is one destination, the route is stored in `cachedPaths` under the key `extendedID(start)->extendedID(dest)`. `cacheCode` is set through `setAbilityCode`; the caller that sets 1 for this skill was not identified (unverified).

Other callers. Several systems look up `Skill_Track` by name with `CMClass.getAbility("Skill_Track")`: `Libraries/MUDTracker.java:245`, `Common/DefaultScriptingEngine.java:13118` and `:13142`, `Items/ClanItems/StdClanCommonItem.java:198` and `:214`, `Abilities/Properties/Prop_ClosedDayNight.java:390` and `:467`, `Abilities/Prayers/Prayer_CallUndead.java:136`, `Abilities/Prayers/Prayer_MalignedPortal.java:285`, `Abilities/Thief/Thief_CallUrchins.java:167`, and `Abilities/Druid/Chant_CallCompanion.java:136`. How each uses it was not traced (unverified).

### 4. Skill_TrackFriend and Skill_TrackCriminal

TrackFriend (`:207-338`):
- Candidates (`:246-266`): the caster's group members, clan members who are in the game (`C.findMember`), and names on the player's friends list. The in-game help describes only group or clan (`resources/help/skill_help.ini`, `SKILL_TRACKFRIEND`), so the friends list is an undocumented extra.
- Scent. The trail "dries up" when `CMLib.flags().canSmell(mob, friend)` becomes false (`:152-158`), and the search filter also requires `canSmell` (`:301`). The smell rule itself was not traced (unverified).
- NPC trackers stop when the next room is in another area (`:165-173`). Players are LOOK-driven as in Track (`:199-203`).
- Racial grants: `Races/Dog.java:117`, `Races/DireWolf.java:89`, `Races/Puppy.java:95`, `Races/Wolf.java:123`, `Races/WereWolf.java:95`.

TrackCriminal (`:203-334`):
- Candidates are inhabitants of nearby areas found by `CMLib.hunt().findInhabitants(allRooms, mob, mobName, 20)` (`:245`). A candidate is kept only if a LegalBehavior of its area reports a warrant (`:246-270`, `hasWarrant` at `:262`). Otherwise: "You don't know anyone with that name who is wanted nearby. You might need to check the warrants?" (`:274`).
- Tick lines: "The clues seem to lead here." (`:144`), "The clues dry up here." (`:151`), "The clues lead you <inDirection>." (`:158`). NPCs stay within their area (`:159-169`).

### 5. Skill_UnearthDemography (area survey)

Data (`:107-121`). Enum `DemogField` has eight fields: COUNT, ALIGNMENT, INCLINATION, AVG_LEVEL, MIN_LEVEL, MAX_LEVEL, BEHAVIOR, PCT_COUNT. Two maps: `allAreaInfo`, a static TreeMap shared by all characters (`:119`), and `knownAreaInfo`, per character (`:121`). What a character has learned is written back into the ability text as XML (`updateText`, `:175-202`).

Survey (tick, `:262-444`):
- Start. `UNEARTH START` attaches the effect for `Integer.MAX_VALUE/5` ticks (`:677`). It ends on STOP, on leaving the surveyed area (`:281-287`), or one second after entering a room in another area (`:221-236`).
- Batch size. Each tick reads `roomCounter` rooms, where `roomCounter = numberOfProperIDedRooms()/10` (`:295-298`), so a full scan takes about ten ticks. The batch is retuned when a tick takes over 100 ms (`:419-423`).
- Per room. The room's saved mob content is loaded (`CMLib.database().DBReadMobContent`, `:362-364`). Each mob's race is tallied: count, faction alignment and inclination sums (`:381-388`), level sum, min and max (`:390-394`), and behaviours through `accountForYourself()`, excluding Scriptable (`:395-412`).
- Summary (`:313-343`). Per race: alignment and inclination become faction range names through `F.fetchRangeName(avg)` (`:322`, `:324`); the most common behaviour is kept (`:329-339`); the percentage of the total is computed (`:340-341`). The result goes into `allAreaInfo` (`:344`), so later characters get it without scanning.

Learning (`getNewTidbit`, `:488-570`):
- `points = (1 + adjustedLevel(invoker)/10) + XLEVEL` (`:502`).
- `maxPoints = 10 + LASTPLAYERLEVEL/10.0` (`:503`).
- Maximum known fields per race: `round(8 * points/maxPoints) + 1` (`:550`).
- If points are at least the number of races already known, an unknown race is chosen, ordered by count (`:505-530`). Otherwise a known race is revisited (`:536-537`). One field is picked at random (`:546-547`). If the character already knows it, nothing is learned (`:548-549`).
- Timing. Entering a room sets `nextTidbit` one tick ahead (`:221-222`), and the tick emits at most one tidbit per entry (`:427-439`). A failure is "You have failed to unearth any new clues about this area." (`:436-437`).

Phrases (`translateField`, `:456-486`): "The <races> in this area are <phrase>." Examples: "Numbering <n>", "Mostly <range>" (alignment and inclination), "Of avg level <n>", "Can be as weak as level <n>", "Can be as high as level <n>", "Known to engage in <behaviour>", "<pct> of all creatures".

Cost. Entering a room halves speed for about one tick unless the character is in combat (`:213-220`, `:447-454`).

### 6. Environment: Skill_WildernessLore and Skill_Stonecunning

Wilderness Lore (`invoke`, `:154-406`):
- Domain line. A switch covers all 24 Room domain types: 14 outdoors and 10 indoors (`com/planet_ink/coffee_mud/Locales/interfaces/Room.java:81-152`). Every domain has a line, so none fall through.
- Climate. "It is unusually <wet, hot, dry, cold, windy> here." from `room.getClimateType()` (`:324-346`); normal climate and void give nothing.
- Resources (`:347-401`). Up to XLEVEL resources are drawn without repetition from `room.resourceChoices()` (`:374-386`) and named in plural lower case: "This is the sort of place that one might find <list>." (`:391`). At XLEVEL 5 or more each carries a chance from `RawMaterial.CODES.FREQUENCY` over the total (`:354-373`). A labelling bug is listed under Problems.
- Signs (`findScat`, `:104-152`). The roll is `rollPercentage() > 10 + X1Level` for nothing (`:106`). Candidates are animal-intelligence NPCs whose home room is in the open wilderness within `5 + XLEVEL/2` (`:124-127`) and of a compatible wet or dry type (`:131-145`). One race is named: ground "You spot some <race> scat / sign / tracks."; water "You spot some <race> scat / sign." (`:116-120`). Signs appear only in rooms that have resources (`:388`).

Stonecunning (passive, `:97-224`):
- Applies to mountains, stone and caves only (`:97-103`).
- On a LOOK at your own room (`:164-170`), a notice roll `proficiencyCheck(mob, 2*XLEVEL, false)` (`:196`) decides whether traps are reported. The result is cached for the last six rooms (`:181-199`), so re-looking gives the same answer.
- If noticed, a trailer after the look reports trapped things: the room, its exits, reverse exits and visible items (`:201-221`, `:105-146`), using `CMLib.utensils().fetchMyTrap` (`:109`). While the room is active the character gets `CAN_SEE_HIDDEN_ITEMS` (`:152-153`).

### 7. Charts, navigation and the ship family

Charts (SeaCharting; TradeCharting is a near copy):
- Chart points live in the skill's own text as `;`-separated extended room IDs (`Skill_SeaCharting.java:128`; append at `:277-284`). ADD needs a watery surface room (`:226`) or, for caravans, a drivable road (`Skill_TradeCharting.java:230`).
- Gates. SeaCharting: on a boardable area or riding a WATER_BASED item (`:105-116`). TradeCharting: on a boardable area or riding a LAND or WAGON item (`Skill_TradeCharting.java:105-118`).
- DISTANCE. `findTrailToAnyRoom` with PASSABLE plus WATERSURFACEONLY (`Skill_SeaCharting.java:305-308`) or DRIVEABLEONLY (`Skill_TradeCharting.java:309-312`), limit 100. The reply uses `trail.size()` (`Skill_SeaCharting.java:316`). The count includes both ends, because the trail includes the location (`MUDTracker.java:372-373`).

Navigation (`Skill_SeaNavigation.java`):
- Target: a chart index (`:149-173`) or, when the skill's text is `FULL` (`:114`), a name matched against room display text or area name (`:176-208`).
- Route: the trail is read from the current location toward the destination, and each step's direction name is appended (`:242-260`).
- On a boardable area it sends a MSG_HUH with text "Your charts say the way there is: <dirs>" and the command `COURSE <dirs>` (`:262-268`). Riding a water mount only prints the text (`:270-273`). The help file says a course is set when the sailor has authority (`SKILL_SEANAVIGATION`); this file checks no authority, and whether the COURSE handler does is unverified.

Sea mapping (`Skill_SeaMapping.java:67-130`). It overrides four hooks of `Skill_Map`: what counts as a map message (riding water or on a boardable), the current room (the ship's room), the map class "SeaMap", and the extra check "This skill only works on board a ship or boat." (`:125`). The mapping engine is in `Skill_Map.java`, which is not in this bucket.

Ship lore (`Skill_ShipLore.java`):
- Target: a ship named in the arguments and found in the room (`:130-145`), or any ship by name. Penalty is 1 when the ship is in the room or the caster has privileges on it, and 2 otherwise (`:131-144`). Then `expertise = XLEVEL / penalty` (`:170`).
- Refusals: "You can't see much from here." when indoors (`:125-128`). After a failure there is a 10000 ms cooldown: "You still can't recall." (`:113-117`, `:159-161`).
- Facts by tier (`:172-308`):
  - expertise above 0, or penalty 1: owner, captain and top speed (`:174-184`).
  - 1 or more: mounted weapons, "it is armed with ..." (`:185-228`).
  - 2 or more: deck rooms, meaning non-indoor and not air (`:244-256`).
  - 4 or more: total rooms, not air (`:257-268`).
  - 5 or more: capacity from `Prop_ReqCapacity` (`items`, `weight`) (`:269-294`).
  - 6 or more: total value (`:295-298`).
  - 7 or more: active effects (`:299-308`).
  - 8 or more: loaded weapons (see Problems).
- Output. expertise + 1 facts are drawn at random from the pool (`:318-326`), reported as "You recall that <fact>." or, in report mode, "I recall that ...".

Stowaway (`Skill_Stowaway.java`):
- Must be on a shore (`:232-237`).
- Destination: a chart index or a name from the SeaCharting text (`:239-292`), or otherwise a random water-surface room within `50 + 10*(XLEVEL + XMAXRANGE)` (`:297-316`) that is not adjacent and not a shore room.
- A failed proficiency roll does not stop the stow. The destination is replaced by a random one and the stow goes ahead (`:299-316`). The help text agrees: "A failed skill check will seem to work fine".
- Boarding. The sailor enters a `WoodRoom` cargo box with `Prop_Crawlspace`, `Prop_RoomDark` and `Prop_ReqCapacity` "people=1" (`:325-332`), and sits (`:333`). The effect lasts `(4*60*1000 / TickMillis) / (1 + XTIME)` ticks (`:334`), which is 60 ticks at the default tick before the XTIME divisor.
- Voyage. At the second tick the box message appears (`:188-190`). After that, a roll of 1 to 50 gives an ambient line on 7 of 50 outcomes (`:195-214`), about 14% of ticks. On expiry the sailor is moved to the destination (`:151-161`) and the box is emptied and destroyed (`:165-172`). Leaving the box early cancels the ride and clears the destination (`:125-142`).

Sea legs (`Skill_SeaLegs.java`, passive). `okMessage` vetoes two classes of incoming message aimed at the owner:
- A `Disease_SeaSickness` tool is blocked when a roll with adjustment `2*XLEVEL` succeeds (`:101-105`). The help says "immunity"; the code is a roll.
- A malicious, moving skill (abstract quality MALICIOUS and `FLAG_MOVING`: Trip, Slip, Trample, Tail Swipe) used on the owner while on a boardable area is blocked on a roll with adjustment `-40 + 2*XLEVEL` (`:107-111`). Message: "<S-NAME> keep(s) <S-HIS-HER> sea legs despite the <O-NAME>." (`:126`).

Sea maneuvers (`Skill_SeaManeuvers.java`, passive). When a ship in combat gets a compass-direction advance command, and the roll succeeds, the source message is replaced by the target message and the others message is patched (`:99-125`). The displayed heading changes. Whether the real heading changes depends on the advance handler (unverified).

Tie down (`Skill_TieDown.java`). Targets a mobile water or air rideable, or a navigable ship the caster has privileges with (`:170-200`), and only in a seaport room (`:204-210`). The target gets IS_BOUND and "Tied Down" (`:100-106`) for `TICKS_FOREVER` (`:223`). A bound rideable cannot be left (`:114-130`). Strength is `abilityCode() = 20 + 8*XLEVEL(invoker)` (`:108-112`). Untying is allowed to the one who tied it, or to a mob whose level is above the tier-er's level plus 6*XLEVEL (`:140-168`).

### 8. Stables (Skill_StableMount)

- Gate. Only in a city or where the caster has privileges (`:202-208`). The help matches: "while either in a city, or a personal or clan home" (`SKILL_STABLE_MOUNT`).
- Storage. Stables are held per area in `stables` (`:100`). They are loaded lazily from the ability text (`getLocalStable`, `:102-143`) and written back by `text()` (`:167-190`) using `CMLib.coffeeMaker().getMobXML`. A stabled mount is taken off the map: `setLocation(null)` on load (`:139-140`), `wanderAway`, `suspendResumeRecurse` and `delInhabitant` on stabling (`:310-314`).
- Matching. Followers first, then stabled names: exact name, then case-insensitive, then substring (`:225-274`). Eligibility: a land-based, animal-intelligence monster Rideable whose race or racial category is in `CMLib.utensils().getFavoredMounts(mob)` (`:280-292`).
- Messages. "<S-NAME> whistle(s) for a stable boy to pick up <T-NAME>." (`:300`); the matching "... to bring in ..." (`:347`); "<T-NAME> seems unwilling to follow you." (`:366-367`).

### 9. Knock-downs and damage (Trip, Slip, Trample, Tail Swipe, Whipsmack, Subdue)

Opposed knock-down formula (Trip, `Skill_Trip.java:243-251`; Slip, `Skill_Slip.java:212-220`, identical):

```
levelDiff  = target.level - (mob.level + 2*XLEVEL)
levelDiff  = levelDiff*5 if levelDiff > 0 else 0
levelDiff -= abilityCode() * mob.DEX
adjustment = -levelDiff - (35 + round((target.DEX - 9) * 3))
success    = proficiencyCheck(mob, adjustment) and target has legs (BODY_LEG > 0)
```

- Trip refuses a caster with no legs and no tail (`:225-229`). The knock-down is `maliciousAffect(mob, target, asLevel, 2, -1)` (`:262`) with "You hit the floor!" (`:263`). A knocked-down character has IS_SITTING until the effect ends (`:118-124`), cannot stand (`:136-137`), and when it acts the effect ends with "<S-NAME> regain(s) <S-HIS-HER> feet." (`:155`).
- Slip has no caster-leg check, otherwise the same mechanics (`Skill_Slip.java:183-235`). A failed auto roll reads "<T-NAMESELF> almost slipped, but maintain(s) <T-HIS-HER> balance." (`:233`).
- Trample needs `mob.weight/2 > target.weight` (`Skill_Trample.java:88-93`, `:202-207`). Damage is `round(roll(1,6,abilityCode) * mob.weight/target.weight)` (`:234`), doubled if the target is not standing (`:235-236`). A standing target is knocked down for two ticks (`:240-244`).
- Tail Swipe. Chance is `(STR_mob - DEX_target)*2` (`Skill_TailSwipe.java:199`). Damage is `roll(1, adjustedLevel/4 + 1, 0)` (`:204-205`), halved on a dodge (`:211-212`). A second roll with `chance/2` (or `chance*2` if chance is not positive) knocks the target down (`:216-222`).
- Whipsmack needs a wielded leather flail (`Skill_Whipsmack.java:102-115`). Damage is `roll(1, weapon damage + adjustedLevel/2, 2)` (`:144-145`), after `rollToHit` (`:150`).
- Subdue (`Skill_Subdue.java`). Damage the caster deals to the target is absorbed: `whomDamage += msg.value()` and `msg.setValue(1)` (`:126-133`). When `curHP - whomDamage <= 0`, the target is put under Skill_ArrestingSap "SAFELY" (`:159-161`), both sides are made peaceful and the kill is recorded (`:162-169`). The examine line reads "<T-NAME> is <pct> health away from being overcome." (`:150`). Armour is `attackAdjustment - 10 + XLEVEL` (`:110`).

Turn Undead (`Skill_TurnUndead.java:106-178`). Only undead targets, and not by an evil caster (`:114-124`). An area blurb flag can add a level bonus (`:129-145`). Proficiency adjustment `((mob.level + 4*levelAdj + 4*XLEVEL) - target.level) * 30` (`:147`). On success, if `mob.level + levelAdj + XLEVEL - target.level > 6`, the target "withers under <S-HIS-HER> holy power!" and takes its remaining HP as damage (`:157-162`). Otherwise it "shake(s) in fear!" and flees (`:166-167`).

### 10. Influence, speech and observation

- Shush (`Skill_Shush.java:109-243`). The caster's own speech is replaced by a WHISPER command (`:112-136`). Verbal spells are refused: "You don't feel comfortable making loud noises right now." (`:141`). Success is `!target.inCombat && proficiencyCheck(mob, -((WIS_target - CHA_mob)*2) + XLEVEL)` (`:195-197`). Malicious duration is `adjustedLevel/10 + 1 + (2*XLEVEL)/3` (`:228`). In a room with a book or readables shopkeeper (`:198-217`) it becomes a beneficial effect of `adjustedLevel/5 + 1 + XLEVEL` (`:226`). It ends when the target enters combat (`:154-156`).
- Spread Hate (`Skill_SpreadHate.java:36-285`). A toggle (`:250-262`). While on, the caster's speech gets a random mood of ANGRY, RUDE or MEAN (`:84-85`, `:116-118`). Speech naming a group (gender words, race names and plurals, racial categories) gives listeners the Disease_Hatred disease with a mask for that group (`:153-176`, `:200-229`). The disease roll is `rollPercentage() > target STAT_SAVE_DISEASE` (`:216`). Matching is longest phrase first over the speech's words (`:181-192`).
- Spread Apathy (`Skill_SpreadApathy.java:35-190`). The same toggle pattern (`:155-188`). Disease_Apathy is applied through `getDisease()` (`:89-99`) to all messages except speech to a target the caster may not fight (`:117-127`).
- Stoicism (`Skill_Stoicism.java:35-155`). Once per tick (`:117-141`) a proficiency roll toggles the ambiance suppressors SUPPRESS_MOOD and SUPPRESS_DRUNKENNESS (`:148-152`). While active, a Mood effect aimed at the owner is refused with "<T-YOUPOSS> stoicism level(s) <T-HIS-HER> mood." (`:98-106`). Spread Hate's mood is therefore suppressed on a stoic speaker (`Skill_SpreadHate.java:112-113`).
- Undead Invisibility (`Skill_UndeadInvisibility.java:93-117`). An undead attacker who is not in combat and is not already fighting the owner cannot target the owner with a malicious action: "You don't see <name>" (`:106`). If the owner was its victim, the victim link is cleared and the owner is made peaceful (`:107-111`).

### 11. Combat helpers

- Two-Weapon Fighting (`Skill_TwoWeaponFighting.java`). In combat with a wielded and a held weapon, `affectPhyStats` adds `speedAdjust*0.1*XLEVEL` speed, reduces attack by `attack/(5+XLEVEL)` and damage by `damage/(20+XLEVEL)` (`:112-126`). On each mob tick, if the character attacked since the last tick (`:128-136`), the off-hand weapon is in range (`:158-170`) and a proficiency roll passes (`:172-181`), the weapons are swapped into the attack slots and `CMLib.combat().postAttack` runs (`:183-189`). A 3% roll gives proficiency help (`:190-194`). `abilityCode()` is fixed at 0 (`:138-141`), but the tick looks for another effect with code 1 and uses its proficiency (`:172-179`).
- Two-Dagger Fighting (`Skill_TwoDaggerFighting.java`). When both weapons are daggers in combat, it adds a speed bonus, and when Two-Weapon Fighting is also present, it re-adds attack and damage bonuses (`:61-78`). It sets `abilityCode()` to 1 while active (`:87-91`), which makes the Two-Weapon tick use its proficiency. Its tick returns early when Two-Weapon Fighting is present (`:94-102`), so there is no second off-hand attack.

### 12. Writing

- Write (`Skill_Write.java:97-164`). Needs INT of at least 5 ("You are too stupid to actually write anything.", `:99-103`) and an unworn item in the pack, or a room item that is not gettable (`:109-123`). Refuses maps (`:132-136`), scrolls (`:138-142`) and text starting with the file prefix (`:144-148`). Success sends MSG_WRITE carrying the text (`:157`); the item applies it.
- Secret Writing (`Skill_SecretWriting.java:89-129`). Writes in the "InvisibleInk" language (`Abilities/Languages/InvisibleInk.java`, not in bucket) by making it the spoken language for the call, un-speaking the current one, and restoring both in a `finally` block (`:92-128`). It adds the ink effect if missing and removes it afterwards (`:98-106`, `:120-125`). Costs normal mana (`:83-87`).

### 13. Observational crafts (Skillcraft, Spellcraft, Thiefcraft)

- Learning by watching (`Skill_Skillcraft.java:125-161`; `Skill_Spellcraft.java:123-161`). When another character in the room, visible to the owner, uses a matching skill (Skillcraft) or casts a matching spell (Spellcraft), the owner may gain it. The gate is `lowestQualifyingLevel(tool) <= myLevel`, where `myLevel = adjustedLevel(owner) - lowest + 1` if the owner has the craft, raised to at least `owner.level/3 + XLEVEL` (`:143-152`). A proficiency roll follows (automatic if the owner lacks the craft), and a trailer line is added: "<T-NAME> do(es) '<skill>'." (`:157`) or "<T-NAME> casts '<spell>'." (`Skill_Spellcraft.java:157`).
- Thiefcraft (`Skill_Thiefcraft.java:51-57`) only changes the craft type to `ACODE_THIEF_SKILL`.
- The trailer carries a copy of the craft with the observed ID as its text (`Skill_Skillcraft.java:154-156`). How the observer is then granted the skill was not traced (unverified).

### 14. Item-use marker skills (Wand Use, Shard Use)

- Both implement `Wand.WandUsage` (`Skill_WandUse.java:35`, `Skill_ShardUse.java:35`; `Items/interfaces/Wand.java:50`). `getEnchantType()` is ACODE_SPELL for wands (`Skill_WandUse.java:57-61`) and ACODE_CHANT for shards (`Skill_ShardUse.java:57-61`). `abilityCode()` returns the owner's XLEVEL (`:94-98`).
- A wand looks for the first usage skill whose enchant type matches, then rolls that skill's proficiency with a null mob (`Items/MiscMagic/StdWand.java:241-262`). Failure prints "<wand> glows faintly for a moment, then fades." (`:262`). Whether the wand reads `abilityCode()` is unverified.

### 15. Swim

- `preInvoke` (`Skill_Swim.java:156-205`) checks the direction and the water. Flight counts as swimmable. A riding mount must be water-based or flying (`:190-196`). The move message is "<S-NAME> start(s) swimming <dir>." (`:197`). While walking, a non-savable copy of the ability gives the IS_SWIMMING disposition (`:229-243`). Casting time is `DEFABLETIME/50` with a floor of 1 (`:104-113`).
- `proficiencyCheck` is overridden to always return true (`:144-154`), so the "struggle(s) against the water" branch (`:225-226`) is unreachable (see Problems).

## Content worth mining

### Catalogue of the 39 files

Triggers, domain and quality come from each file. "Granted by" comes from `addCharAbilityMapping` in `CharClasses/` and racial ability arrays in `Races/` (grep results; a grant with no entry found is marked).

| File | Triggers | Domain | Quality | Granted by (class@level or race) |
|---|---|---|---|---|
| `Skill_Track.java` | TRACKTO | none set | OK_OTHERS | none found |
| `Skill_UnearthDemography.java` | UNEARTHDEMOGRAPHY, DEMOGRAPHY | STREETSMARTS | OK_OTHERS | Reliquist@7 |
| `Skill_WildernessLore.java` | WILDERNESSLORE, WLORE | NATURELORE | INDIFFERENT | Artisan@1, Barbarian@1, Beastmaster@1, Delver@1, Druid@1, Gaian@1, Oracle@9, Scholar@9, Trapper@13 |
| `Skill_StableMount.java` | STABLEMOUNT, STABLE | ANIMALAFFINITY | OK_SELF | Cavalier@30 |
| `Skill_Stowaway.java` | STOWAWAY | DECEPTIVE | INDIFFERENT | Sailor@4 |
| `Skill_TrackFriend.java` | FTRACK, TRACKFRIEND, FRIENDTRACK | RACIALABILITY | OK_OTHERS | race: Dog, DireWolf, Puppy, Wolf, WereWolf |
| `Skill_TrackCriminal.java` | CTRACK, TRACKCRIMINAL, CRIMINALTRACK | LEGAL | OK_OTHERS | Gaoler@18 |
| `Skill_ShipLore.java` | SHIPLORE, SLORE | WATERLORE | INDIFFERENT | Artisan@1, Pirate@9, Sailor@9 |
| `Skill_TradeCharting.java` | TRADECHARTING, TRADECHART | TRAVEL | INDIFFERENT | Bard@19, Cavalier@19, Gypsy@19 |
| `Skill_SeaCharting.java` | SEACHARTING, SEACHART | SEATRAVEL | INDIFFERENT | Pirate@10, Sailor@10 |
| `Skill_SeaNavigation.java` | SEANAVIGATION, SEANAVIGATE | SEATRAVEL | INDIFFERENT | Pirate@12, Sailor@12 |
| `Skill_SeaMapping.java` | SEAMAPPING, SEAMAP | inherited from Skill_Map | inherited | Pirate@8, Sailor@8, Scholar@8 |
| `Skill_SeaLegs.java` | none (passive) | SEATRAVEL | BENEFICIAL_SELF | Sailor@2, Pirate@4 |
| `Skill_SeaManeuvers.java` | none (passive) | SEATRAVEL | BENEFICIAL_SELF | Sailor@14 |
| `Skill_TieDown.java` | TIEDOWN | BINDING | INDIFFERENT | Sailor@7 |
| `Skill_SpreadHate.java` | SPREADHATE, SHATE | INFLUENTIAL | INDIFFERENT | Gaoler@16 |
| `Skill_SpreadApathy.java` | SPREADAPATHY, SAPATHY | INFLUENTIAL | INDIFFERENT | Gaoler@19 |
| `Skill_Stoicism.java` | none (passive) | INFLUENTIAL | OK_SELF | Monk@4 |
| `Skill_Shush.java` | SHUSH | INFLUENTIAL | MALICIOUS | Scholar@17 |
| `Skill_Trip.java` | TRIP | DIRTYFIGHTING | MALICIOUS | Artisan@1, Beastmaster@8, Monk@8, Arcanist@11, Assassin@11, Pirate@11, Reliquist@11, Thief@11, Bard@13, Charlatan@13, Jester@13, Prancer@13, Fighter@14, Cavalier@17, Ranger@17, Paladin@18 |
| `Skill_Slip.java` | SLIPPIFY | DIRTYFIGHTING | MALICIOUS | none found |
| `Skill_Trample.java` | TRAMPLE | FITNESS | MALICIOUS | Barbarian@18; race: Pachyderm, Equine, Bull, Cow, Buffalo |
| `Skill_TailSwipe.java` | TAILSWIPE | RACIALABILITY | MALICIOUS | culture: LizardMan |
| `Skill_Subdue.java` | SUBDUE | EVASIVE | MALICIOUS | Fighter@6 |
| `Skill_Stonecunning.java` | none (passive) | ALERT | BENEFICIAL_SELF | race: Dwarf (racial effect) |
| `Skill_TwoWeaponFighting.java` | none (passive) | MARTIALLORE | BENEFICIAL_SELF | Barbarian@3, Fighter@3, Monk@3, Ranger@4, Assassin@12, Pirate@12, Thief@12, Bard@14, Prancer@14 |
| `Skill_TwoDaggerFighting.java` | none (passive) | inherited | inherited | Assassin@19 |
| `Skill_TurnUndead.java` | TURN | DEATHLORE | MALICIOUS | Healer@1, Purist@1, Cleric@5, Oracle@5 |
| `Skill_Whipsmack.java` | WHIPSMACK | MARTIALLORE | MALICIOUS | Fighter@5, Gaoler@5, Reliquist@15 |
| `Skill_UndeadInvisibility.java` | none (passive) | DEATHLORE | BENEFICIAL_SELF | none found |
| `Skill_Write.java` | WRITE, WR | CALLIGRAPHY | INDIFFERENT | level 1 in 41 classes |
| `Skill_SecretWriting.java` | SECRETWRITING, SECRETWRITE, SWRITE, SWR | CALLIGRAPHY | INDIFFERENT | Scholar@11 |
| `Skill_Skillcraft.java` | none (passive) | STREETSMARTS | OK_SELF | Scholar@11, Oracle@12 |
| `Skill_Spellcraft.java` | none (passive) | ARCANELORE | OK_SELF | Wizard@1, Diviner@4, Arcanist@7, Illusionist@7, Abjurer@8, Alterer@8, Conjurer@8, Enchanter@8, Evoker@8, Mage@8, Transmuter@8, Charlatan@12, Scholar@15 |
| `Skill_Thiefcraft.java` | none (passive) | inherited | inherited | Charlatan@16, Oracle@16 |
| `Skill_Swim.java` | SWIM | FITNESS | INDIFFERENT | level 1 in 41 classes |
| `Skill_WandUse.java` | none | ARCANELORE | BENEFICIAL_SELF | Artisan@1, Mage@1, Arcanist@3, Bard@3, Jester@5, Scholar@5, Thief@11 |
| `Skill_ShardUse.java` | none | ARCANELORE | BENEFICIAL_SELF | Beastmaster@1, Druid@1, Gaian@1, Mer@1, SkyWatcher@1, Gypsy@3, Delver@5, Ranger@5 |
| `StdSkill.java` | none | none | n/a | base class |

Notes on the grants: Track, Slip and Undead Invisibility have no class mapping in `CharClasses/`, and no racial list was found for them either, so how they are obtained is unverified.

### Recipes for a game designer

- Fact pools in Ship Lore (`Skill_ShipLore.java:174-308`). Seven tiers of ship facts: owner and speed; mounted weapons; deck rooms; total rooms; cargo capacity; total value; active effects. A player with expertise E recalls E+1 of the facts known to be available.
- Area survey vocabulary (`Skill_UnearthDemography.java:456-486`): "Numbering", "Mostly <alignment range>", "Of avg level", "Can be as weak as level", "Can be as high as level", "Known to engage in <behaviour>", "<pct> of all creatures". Each fact is a sentence per race.
- Wilderness signs (`Skill_WildernessLore.java:116-120`): ground "You spot some <race> scat / sign / tracks."; water "scat / sign". The race comes from a real nearby animal.
- Ambient voyage lines (`Skill_Stowaway.java:195-213`): "You feel the ship rocking gently in the waves.", "You hear rats crawling around on your crate.", "You hear the sounds of some crewmen talking outside.", "You are extremely bored.", "It is dark and cramped in here."
- Domain lines (`Skill_WildernessLore.java:177-323`): 24 short sentences such as "You are in a forest.", "You are on a city street.", "You are in a magical place.", "You are under the water."
- Trap lines (`Skill_Stonecunning.java:111`): "<name> is trapped." per object.
- Mood words (`Skill_SpreadHate.java:85`): ANGRY, RUDE, MEAN.
- Knock-down lines: "You hit the floor!", "You go down!" (Trip, Trample), "<T-NAME> hit(s) the floor!" (Tail Swipe), "You regain your feet." (Trip and Slip, when there is no room to announce it in; `Skill_Trip.java:163`, `Skill_Slip.java:162-163`).
- Silence lines: "You feel free to speak again." (`Skill_Shush.java:170`), "You don't feel comfortable making loud noises right now." (`:141`).

## Notable design ideas

- Trails as ordered lists, with the direction picked by the earliest-indexed neighbour (`MUDTracker.java:464-496`). This is a small, general engine that any NPC or player can use.
- One tracker, two drivers. NPCs are moved by the tick, while players are steered by a LOOK hook (`Skill_Track.java:227-332`, `:363-377`). The same data serves both.
- Doors as a sub-protocol: open, unlock if needed, pass, then close and relock behind (`Skill_Track.java:237-321`).
- Persistence by rescheduling: a failed trail retries after 30 s (`Skill_Track.java:154-176`).
- The tracker hides itself while tracking (`Skill_Track.java:138-142`).
- A shared static survey: once any character finishes an area, later characters get its summary (`Skill_UnearthDemography.java:119`, `:288-289`, `:344`).
- Self-tuning batches: the survey reads `numberOfProperIDedRooms()/10` rooms per tick, and retunes to stay near 100 ms (`:295-298`, `:419-423`).
- Knowledge points: learnable facts per race grow with level and expertise, and the cap is a curve over the number of fields (`:502-550`).
- A voyage as a timed hidden state. The destination is chosen at boarding and applied at exit (`Skill_Stowaway.java:297-334`, `:151-172`).
- Chart points stored as text: the chart is one `;`-separated string in the skill itself (`Skill_SeaCharting.java:277-284`). FULL mode is a flag in the same text (`Skill_SeaNavigation.java:114`).
- Mounts stored as data in a skill's text, and taken off the map while stabled (`Skill_StableMount.java:102-143`, `:310-314`).
- Non-lethal damage through a virtual HP pool: damage is absorbed and the final blow is a restraint (`Skill_Subdue.java:126-133`, `:153-171`).
- Per-room perception cache: a last-six-rooms cache makes repeated looks consistent (`Skill_Stonecunning.java:181-199`).
- Interception rather than refusal. Speech becomes a whisper (`Skill_Shush.java:112-136`); a message's direction text is rewritten in flight (`Skill_SeaManeuvers.java:120-124`); incoming disease and mood are vetoed (`Skill_SeaLegs.java:101-111`, `Skill_Stoicism.java:98-106`).
- Context modifiers. Shipboard knock-downs are penalised by a fixed -40 (`Skill_SeaLegs.java:111`), and Shush flips from malicious to beneficial in bookshops (`Skill_Shush.java:198-226`).
- One opposed-check formula shared by Trip and Slip (`Skill_Trip.java:243-251`).
- Learning by observation with a level gate (`Skill_Skillcraft.java:143-152`).

## Problems and cruft

Each item was confirmed by reading the cited lines.

1. `Skill_ShipLore.java:182`. The format "it is owned by @x1, and captained by @x1" is given the owner name and the captain name, but only @x1 is used. The captain's name is never shown, and the owner's name is printed twice.
2. `Skill_ShipLore.java:240-241`. The "it has loaded" fact sits outside the `expertise >= 8` block. For expertise 1 to 7 it lists every weapon type as "loaded". At 8 or more with nothing loaded it prints an empty list.
3. `Skill_ShipLore.java:172-176`. `prop.getOwnerName()` is used without a null check, but `LegalLibrary.getPropertyRecord(Item)` may return null (`com/planet_ink/coffee_mud/Libraries/interfaces/LegalLibrary.java:249-265`). Whether a ship can lack a record is unverified.
4. `Skill_UnearthDemography.java:324`. The inclination fallback when the faction is missing uses `avg1` (alignment) instead of `avg2`.
5. `Skill_UnearthDemography.java:512-513`. The comparator reads `o1` twice, so the ordering is not a consistent comparison.
6. `Skill_UnearthDemography.java:119`. `allAreaInfo` is a static, unsynchronized TreeMap shared by all characters and written from tick code (`:344`). Whether ticks run concurrently is unverified.
7. `Skill_WildernessLore.java:375-383`. The resource is chosen at random from `unused`, but its percentage is `chancestr[i]` with the loop counter, so the number may belong to a different resource. `maxRscs` (`:351-353`) is never used.
8. `Skill_Stonecunning.java:131-134`. The room `R` is checked again for every exit (`trapCheck(R)`), and the forward exit `E` is never checked; only the reverse exit `E2` is.
9. `Skill_Stonecunning.java:194`. `size() > 5` before the add lets the cache hold six rooms, not five.
10. `Skill_TurnUndead.java:138-142`. The blurb parser uses `value` where `s` is meant, so multi-token values are misread.
11. `Skill_TurnUndead.java:147` against `:157`. The proficiency term uses `4*levelAdj`, while the wither threshold uses `levelAdj` once.
12. `Skill_Swim.java:144-154`. `proficiencyCheck` always returns true, so the failure branch at `:225-226` is unreachable and Swim cannot fail by proficiency.
13. `Skill_Swim.java:182-184`. `r.domainType()==OUTDOORS_AIR && r.domainType()==INDOORS_AIR` can never be true. It should be `||`.
14. `Skill_StableMount.java:119`. `startsWith("<STABLE>")` never matches the `<STABLE AREA="...">` text that `text()` writes (`:177`). After a reload, the first use of the stable therefore discards stored mounts (`:120-124` builds an empty list). The parse branch at `:125-141` is effectively dead. `getLocalStable` can also return null (`:142`), which `invoke` iterates at `:211`.
15. `Skill_StableMount.java:212`. Stabled mounts are keyed by name in a HashMap, so two mounts with the same name collapse into one choice.
16. `Skill_StableMount.java:145-159`. `isValidRoom` is never called in this file. It duplicates `Abilities/Fighter/Fighter_CallSteed.java:287`.
17. `Skill_Track.java:657-670`. `findTrailToRoom` already ends the list at the location (`MUDTracker.java:372-373`), so `theTrail.add(startRoom)` (`:670`) appends a duplicate. On a cache hit the cached list is the same object, and it grows each time. Routing uses `indexOf`, so direction choice is probably unaffected, but this is unverified.
18. `Skill_SeaCharting.java:308-309`. `trail` is dereferenced without a null check. `findTrailToAnyRoom` returns null when no route exists (`MUDTracker.java:436-447`). TradeCharting checks this (`Skill_TradeCharting.java:313`).
19. Copy-paste. SeaCharting and TradeCharting are near-identical files. Skillcraft and Spellcraft (`Skill_Skillcraft.java:125-161`, `Skill_Spellcraft.java:123-161`) differ only in the trigger class and the message.
20. `Skill_Subdue.java:104`, `:164-169`. `lastKills` (a 250-entry set with a one-day expiry) is written and only read in its own guard. Nothing else uses it.
21. `Skill_ShipLore.java:91-95`. `preInvoke` returns true, which skips the command-queue check in `StdAbility.preInvoke` (`StdAbility.java:1905-1913`).
22. `Skill_SpreadApathy.java:94-96`. `CMClass.getAbility("Disease_Apathy")` is not checked for null before `makeLongLasting()`.
23. `Skill_Slip.java` against `Skill_Trip.java:225-229`. Trip requires the caster to have legs; Slip does not.
24. Documentation and code disagree. Sea Legs promises "immunity to sea sickness" (`resources/help/skill_help.ini`, `SKILL_SEALEGS`), but the code is a roll (`Skill_SeaLegs.java:101-105`). The Swim help says carried weight affects the skill (`SKILL_SWIM`), but this file has no weight check. Spread Hate's help says "grumpy", while the code uses RUDE (`Skill_SpreadHate.java:85`). TrackFriend's help omits the friends list. The Trip help says the target is "much easier to hit", but nothing in this file changes hit chance (unverified elsewhere).
25. Multiplayer assumptions for a single-player game. Track searches online players (`Skill_Track.java:616-623`). TrackFriend's candidate set is group, clan and friends (`Skill_TrackFriend.java:246-266`). Spread Hate and Spread Apathy infect others in the room (`Skill_SpreadHate.java:200-229`). Navigation sends COURSE to a crew (`Skill_SeaNavigation.java:262-268`). Stowaway uses other ships' traffic (`Skill_Stowaway.java:297-316`).
26. `Skill_Stowaway.java:299-316`. A failed roll does not fail the stow. The traveller silently gets a random destination. This is documented, but it is surprising play.

## Takeaways for a Python SUD

1. Trail engine. Keep the destination-first list and the "earliest neighbour in the list" step rule. It is a few lines and works for NPC and player trackers alike.

   ```python
   def next_step(trail: list[str], here: str, exits: dict[str, str], open_only: bool, is_open) -> str | None:
       if here == trail[0]:
           return "arrived"
       here_i = trail.index(here) if here in trail else len(trail)
       best = None
       for direction, room in exits.items():
           if open_only and not is_open(here, direction):
               continue
           if room in trail and trail.index(room) < here_i:
               if best is None or trail.index(room) < best[0]:
                   best = (trail.index(room), direction)
       return best[1] if best else None   # None means the trail dries up
   ```

2. One tracker, two drivers. Model a `Tracker` with a state enum (IDLE, MOVING, PAUSED, DRIED, DONE). NPC trackers advance on the game tick; player trackers advance on the LOOK command. Keep the door sub-protocol (open, unlock if needed, pass, close and relock behind).
3. Expertise gates for knowledge. Ship Lore's shape fits a table: `[(0, owner_speed), (1, weapons), (2, deck_rooms), (4, rooms), (5, capacity), (6, value), (7, effects)]`, with `expertise + 1` random picks. The same pattern works for any "appraise this object" skill.
4. Survey as a world object. `Survey(area)` holds progress (room list, batch size) and the summary. Per character, store a set of revealed `(race, field)` pairs. Use CoffeeMud's point curve as the pacing rule: `points = 1 + level // 10 + expertise`, `max_fields = round(len(FIELDS) * points / max_points) + 1`.
5. Voyage as a timer. A `Voyage(dest, ticks_left)` on the character, with `ticks_left = 60 // (1 + time_bonus)` at a 4 s tick. Choose the destination at boarding, apply it on arrival, and cancel it if the traveller leaves the hold. No ship simulation is needed for a single-player game.
6. Stables as data. Store stabled NPCs in the save file as typed records, not in a text field. Keep them off the map while stabled; do not parse text back.
7. One knock-down formula. Use the Trip and Slip formula (`Skill_Trip.java:243-251`) as one helper, `knockdown_adjustment(attacker, target, expertise, ability_code)`, shared by all knock-down actions. Drop the duplicate in Slip.
8. Merge the copies. Combine SeaCharting and TradeCharting, and the three crafts, into one function parameterised by the domain. Drop the unused `lastKills`, the copied `isValidRoom`, and the dead `maxRscs`.
9. Port the bugs fixed, not copied. Use the right variables (`s` not `value`; `avg2` not `avg1`; `||` not `&&`), and keep the per-room perception cache.
10. Single-player rewrites. Replace group, clan and friends with a companion list. Replace online-player search with NPC search. Speech influence becomes an NPC audience model. Keep the Shush rule (speech turned into whisper; reversed in quiet rooms) and the sea-legs veto as interceptors on the action pipeline.

## Coverage

- Read in full (all 39 files, each in one Read): every file in `b082.tsv`, from `Skill_SeaCharting.java` through `StdSkill.java`, including Track, UnearthDemography, WildernessLore, StableMount, Stowaway, TrackFriend, TrackCriminal, ShipLore, TradeCharting, SeaCharting, SeaNavigation, Trip, SpreadHate, Swim, Trample, Shush, Slip, TailSwipe, TieDown, Subdue, Stonecunning, TwoWeaponFighting, SpreadApathy, TurnUndead, Whipsmack, Write, Skillcraft, Spellcraft, Stoicism, SeaLegs, SeaMapping, SeaManeuvers, SecretWriting, UndeadInvisibility, TwoDaggerFighting, WandUse, ShardUse, Thiefcraft, StdSkill.
- Outside the bucket, read only for interactions: `Libraries/MUDTracker.java:288-496`, `Libraries/Sense.java:145-156`, `Items/MiscMagic/StdWand.java:232-271`.
- Outside the bucket, searched by grep only: `Abilities/StdAbility.java` (text at 621-624, proficiencyCheck at 1441-1453, preInvoke at 1905-1913); `Libraries/interfaces/TrackingLibrary.java`, `LegalLibrary.java` and `Room.java` (domain constants); `Common/interfaces/PhyStats.java:433`; `core/CMProps.java:681`; the grants in `CharClasses/` and `Races/`; `resources/help/skill_help.ini` for the skills in this bucket.
- Skimmed or not read: `Abilities/Skills/Skill_Map.java` (hook signatures only); `Abilities/Diseases/Disease_Apathy.java`, `Disease_Hatred.java`, `Disease_SeaSickness.java`; `Abilities/Misc/Mood.java`; `Abilities/Languages/InvisibleInk.java`; `Abilities/Properties/Prop_*` (presence by glob only); `StdAbility.proficiencyCheck` after line 1453; the hunt and faction libraries; `CMLib.utensils().getFavoredMounts`; the smell rule behind `canSmell`.
- Not run, not edited. No git operations and no MCP tools were used.
