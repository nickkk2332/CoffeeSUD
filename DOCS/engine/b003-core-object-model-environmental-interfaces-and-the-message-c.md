# Core object model: environmental interfaces and the CMMsg message system

> Part of the CoffeeMud NEWOVERVIEW. Bucket: `b003` (Core object model: environmental interfaces and the message (CMMsg) system) · 60 files · 8958 lines of Java.

Path convention: file paths are relative to the CoffeeMUD folder. After this line the common prefix `com/planet_ink/coffee_mud/` is dropped, so `core/CMProps.java:681` means `com/planet_ink/coffee_mud/core/CMProps.java` line 681. Line numbers are from the files as they stand. Items marked "by reading" were traced in the code but not executed.

## Summary

Almost every object in the world (rooms, mobs, items, exits, abilities, behaviours, areas) implements one stack of small interfaces: `Tickable` for periodic time, `Affectable` and `StatsAffecting` for layered stats, `Behavable` for behaviours and scripts, `Taggable`, `MsgListener` for veto and reaction, and `Contingent`/`Expireable` for lifetime. Actions are not performed by direct calls between objects. Each action is wrapped in a `CMMsg`: an actor, an optional target and tool, and three codes (for the actor, the target and everyone else). Each code combines perception bits (hands, eyes, mouth, sound, movement) with an action type such as GET or SPEAK. The room first asks every listener whether the event may happen (`okMessage`), then tells every listener it has happened (`executeMsg`), then runs follow-up events. Because the perception bits travel with the event, one message produces the correct text for each observer. That is the idea most worth borrowing for a single-player Python game. The mask constants, name tables, message pool and some iteration code have errors that are listed under "Problems and cruft".

## Key files and types

| File or class | Role | Lines |
|---|---|---|
| `com/planet_ink/coffee_mud/Common/interfaces/CMMsg.java` | Message contract: codes, mask bits, action types, `MSG_*` constants, name tables | 1684 |
| `com/planet_ink/coffee_mud/Common/DefaultMessage.java` | Default `CMMsg`: codes stored as major and minor parts, `modify()`, name matching, flat serialization | 928 |
| `com/planet_ink/coffee_mud/core/interfaces/Environmental.java` | Base of nearly every game object; extends Tickable, StatsAffecting, MsgListener, Contingent, Modifiable, Expireable | 190 |
| `com/planet_ink/coffee_mud/core/interfaces/MsgListener.java` | `okMessage` (veto) and `executeMsg` (apply) | 70 |
| `com/planet_ink/coffee_mud/core/interfaces/Physical.java` | Things with a place in the world; name and description as seen by a viewer; extends Environmental, Affectable | 71 |
| `com/planet_ink/coffee_mud/core/interfaces/Affectable.java` | `basePhyStats`/`phyStats`, `recoverPhyStats`, effect list | 159 |
| `com/planet_ink/coffee_mud/core/interfaces/StatsAffecting.java` | `affectPhyStats`, `affectCharStats`, `affectCharState` hooks | 82 |
| `com/planet_ink/coffee_mud/core/interfaces/Behavable.java` | Behaviours and scripts on an object | 144 |
| `com/planet_ink/coffee_mud/core/interfaces/Tickable.java` | `tick(Tickable,int)`, `TICKID_*` and `STATUS_*` constants | 180 |
| `com/planet_ink/coffee_mud/core/interfaces/TickClient.java`, `TickableGroup.java` | Scheduler wrappers around one ticking object and one thread's worth of objects | 147, 213 |
| `com/planet_ink/coffee_mud/core/interfaces/PhysicalAgent.java`, `Rider.java`, `Rideable.java`, `Combatant.java` | Agents with behaviours; mounts and their `Basis` enum; combat state | 37, 54, 370, 182 |
| `com/planet_ink/coffee_mud/core/interfaces/ItemPossessor.java`, `ItemCollection.java`, `MOBCollection.java` | Item and mob containers; the `Expire` enum | 115, 132, 280 |
| `com/planet_ink/coffee_mud/core/interfaces/Contingent.java`, `Expireable.java`, `Modifiable.java`, `CMObject.java` | Lifetime, expiry, string-keyed fields, class identity | 57, 40, 88, 90 |
| `com/planet_ink/coffee_mud/core/interfaces/EventHandlers.java` | Per-action approve/execute hooks; not used anywhere | 114 |
| `com/planet_ink/coffee_mud/Common/DefaultEnvironmental.java`, `DefaultModifiable.java` | Base implementations; fields are a map keyed by upper-case names | 251, 136 |
| `com/planet_ink/coffee_mud/Common/DefaultItemCollection.java`, `WeakItemCollection.java` | Item lists; the weak variant backs clan and player external-item stores | 235, 316 |
| `com/planet_ink/coffee_mud/Locales/StdRoom.java` (outside bucket) | `okMessage`, `executeMsg`, `send`, `sendAndExec`, trailers, room stat recovery | 3363 |
| `com/planet_ink/coffee_mud/Items/Basic/StdItem.java` (outside) | Item veto rules and `executeMsg` | 2386 |
| `com/planet_ink/coffee_mud/MOBS/StdMOB.java` (outside) | Mob veto, `executeMsg`, message display rules, tick phases, stat recovery | 6287 |
| `com/planet_ink/coffee_mud/Commands/Get.java`, `Drop.java` (outside) | The two commands traced below | 284, 258 |
| `com/planet_ink/coffee_mud/Libraries/CommonMsgs.java` (outside) | `handleBeingGetted`, `handleBeingDropped`, `handleBeingGivenTo`: the code that moves items | 3051 |
| `com/planet_ink/coffee_mud/core/CMClass.java` (outside) | `CMMsg` factories and recycling pool | 3971 |
| `com/planet_ink/coffee_mud/core/threads/ServiceEngine.java`, `StdTickClient.java`, `StdTickGroup.java` (outside) | Tick scheduling | 1871, 223, 380 |
| `com/planet_ink/coffee_mud/Libraries/CMMap.java` (outside) | Expiration sweep | 3073 |
| `com/planet_ink/coffee_mud/Abilities/StdAbility.java` (outside) | Effect durations counted on ticks | 3018 |

## How it works

### 1. The message record

Fields (`Common/DefaultMessage.java:74-89`):

- **Source.** Always a mob (`Common/interfaces/CMMsg.java:44-50`). The only field that is never null.
- **Target and tool.** Either may be any `Environmental` or null (`CMMsg.java:52-66`). A tool is the means, such as the container in "get X from bag" or the spell in a cast.
- **Three codes.** Each code is kept as a major part (mask bits) and a minor part (action type): `targetMajorMask`/`targetMinorType`, and the same for source and others (`DefaultMessage.java:74-79`). `targetCode()` returns `major | minor` (`DefaultMessage.java:499-502`).
- **Three texts.** `sourceMsg`, `targetMsg`, `othersMsg` (`DefaultMessage.java:80-82`). The source sees its text, the target sees its text, everyone else sees the others text. Null means silent for that party.
- **Value.** A free integer whose meaning depends on the type (`CMMsg.java:612-633`). Used for amounts, such as the power given in `TYP_POWERCURRENT` or the coins in a value check.
- **Trailers.** Lists of follow-up messages and runnables, plus a flag to suspend them (`DefaultMessage.java:87-89`; `CMMsg.java:856-893`).

The code word is split by two constants (`CMMsg.java:938-940`):

- `MINOR_MASK = 2047` (bits 0 to 10): the action type.
- `MAJOR_MASK = 2147482624` (hex `0x7FFFFC00`): the perception bits. See problem 3 for an overlap.

Mask bits (`CMMsg.java:943-970`). The comments are the codebase's own:

| Mask | Value | Meaning in the codebase | Where the code acts on it |
|---|---|---|---|
| `MASK_HANDS` | 2048 | small hand movements | Hand verbs are seen by eyes or hands (`StdMOB.java:3899`) |
| `MASK_MOVE` | 4096 | large body movement | Seen or heard moves (`StdMOB.java:3905`) |
| `MASK_EYES` | 8192 | looking and seeing | Target text shown if the viewer can see the source (`StdMOB.java:3793`) |
| `MASK_MOUTH` | 16384 | speaking and eating | Mouth without sound is seen or heard (`StdMOB.java:3905`) |
| `MASK_SOUND` | 32768 | general body noises | Shown if the viewer can hear the source (`StdMOB.java:3884`) |
| `MASK_ALWAYS` | 65536 | always confirmed | `StdRoom.show` skips `okMessage` for these (`StdRoom.java:1775`) |
| `MASK_MAGIC` | 131072 | magical | Item veto "Please don't do that." for magic aimed at non-gettable items (`StdItem.java:1140-1147`) |
| `MASK_DELICATE` | 262144 | thief-style, delicate | `MSG_DELICATE_*` constants (`CMMsg.java:1437-1441`) |
| `MASK_MALICIOUS` | 524288 | harmful to the target | Source starts combat with the target (`StdMOB.java:3489`) |
| `MASK_CHANNEL` | 1048576 | channel message | Routed to the channel filter (`StdMOB.java:3511`) |
| `MASK_OPTIMIZE` | 2097152 | repeated message, skip recalculation | `handleBeingGetted` skips room recalculation (`CommonMsgs.java:2674-2708`) |
| `MASK_CNTRLMSG` | 4194304 | internal, not observable | Suppresses source echo (`StdMOB.java:3694`) and others echo (`StdMOB.java:3899`) |
| `MASK_INTERMSG` | 8388608 | intermediate step of a larger action | Skips auto-bundling (`CommonMsgs.java:2746`) |
| `MASK_SPAMMY` | 16777216 | spam-prone | Hidden when the viewer has the NOSPAM attribute (`StdMOB.java:3871`) |

Action types (`TYP_*`) run from 1 to 154 (`CMMsg.java:974-1280`). Channels occupy 2000 to 2047 (`CMMsg.java:1282-1283`). The `TYPE_DESCS` array (`CMMsg.java:1291-1313`) gives their names.

Message constants are the OR of one or more masks and one type. For example `MSG_GET = MASK_HANDS | TYP_GET = 2048 | 8 = 2056` (`CMMsg.java:1417`). `NO_EFFECT = 0` (`CMMsg.java:1401`) is used for "nothing happens" on the target or others side.

Two small enums: `CMMsg.View` (SOURCE, TARGET, OTHERS; `CMMsg.java:923-928`) and `CheckedMsgResponse` (FORCEDOK, CANCEL, CONTINUE; `CMMsg.java:1678-1683`), which the tracking library uses.

Creation and recycling. `CMClass.getMsg(...)` overloads (`core/CMClass.java:3682-3760` and following) take a message from `MSGS_CACHE` or make one, then call `modify(...)` (`DefaultMessage.java:141-245`). `modify` sets the actors, codes and texts. It does not reset `value`, the trailers or the suspend flag. The cache holds up to `MAX_MSGS = 10000 + maxMemory/10000` entries (`core/CMClass.java:236-238`).

### 2. Two-phase dispatch

The contract is in `core/interfaces/MsgListener.java:40-68`. `okMessage` is the veto phase: it may modify or reject the message, and returns true unless the event must be cancelled, in which case the listener should tell the initiator why. `executeMsg` is the apply phase: the event has already been approved and cannot be vetoed.

Callers check, then send. `Commands/Get.java:74-96` and `Commands/Drop.java:64-68` call `R.okMessage(mob,msg)` and only then `R.send(mob,msg)`. `Room.send` does not re-check the main message (`Locales/StdRoom.java:1734-1739`). It does check each trailer before running it (`StdRoom.java:1701-1728`).

**Veto order in `StdRoom.okMessage`** (`StdRoom.java:745-903`). The first `false` stops the walk:

1. Area (`StdRoom.java:747`).
2. If the room itself is the target: movement checks (ENTER, LEAVE, FLEE), "You can't shop here." for shop verbs, "You can't really dig here." for DIG, and "You can't do that here." for hand or mouth actions that are not magical (`StdRoom.java:849`).
3. Source mob.
4. Every other mob in the room.
5. Every item in the room (each item runs its own `okMessage`).
6. Room effects, then behaviours, then scripts.
7. Exits, in reverse direction order.

**Veto order in `StdItem.okMessage`** (`Items/Basic/StdItem.java:1072-1110`). The code says this order is fixed: behaviours, then scripts, then effects. Then the message is checked against the item: a `tool == this` branch for throws and pours, then the target-specific switch (`StdItem.java:1150-1215`), then per-verb rules such as GET (`StdItem.java:1359-1401`).

**Apply order in `StdRoom.send`** (`StdRoom.java:1734-1739`, `sendAndExec` at `1688-1699`):

1. `source.executeMsg` (the actor's own reaction and echo).
2. Every other mob's `executeMsg`.
3. The room's `executeMsg` (`StdRoom.java:906-1050`): area, room-as-target cases (ENTER and LEAVE recalculate room stats unless `MASK_OPTIMIZE`; LOOK and EXAMINE; READ answers "There is nothing written here."; DIG creates a hole), then gravity spread, items (`StdRoom.java:995-1002`), exits, behaviours, scripts, effects.
4. `CMLib.commands().monitorGlobalMessage` (`Libraries/CommonMsgs.java:234`), which calls every registered `MsgMonitor`.
5. Trailers, depth-limited to 3 (`StdRoom.java:1704`).

### 3. One action traced: picking up a sword

1. **Parse.** `Get.get(mob, null, sword, quiet, "get", false)` (`Commands/Get.java:62`).
2. **Worn check.** `if(!getThis.amWearingAt(Wearable.IN_INVENTORY))` (`Get.java:74`). For a floor item `amWearingAt(0)` is true because `myWornCode == 0` (`Items/Basic/StdItem.java`, `amWearingAt`), so no REMOVE message is sent. A worn item would first get a `MSG_REMOVE` (`Get.java:74-80`).
3. **Build.** `CMClass.getMsg(mob, sword, null, MSG_GET, "<S-NAME> get(s) <T-NAME>.")` (`Get.java:81`). Source, target and others codes are all `HANDS|GET`. `quiet` passes a null text.
4. **Veto.** `R.okMessage(mob, msg)` (`Get.java:82`). The room asks the mob first (`StdMOB.okMessage`, `MOBS/StdMOB.java:2459`: deity, classes, race, effects, carried items, behaviours, scripts, factions), then the other mobs, then the sword. `StdItem.okMessage` runs its GET block (`StdItem.java:1359-1401`). Refusals are spoken directly to the actor:
   - "You can't see that." (`StdItem.java:1366`)
   - "@x1 is too powerful to endure possessing it." (`StdItem.java:1379`): applies when `mob.level < item.level - (10 + mob.level/5)`
   - "@x1 is too heavy." (`StdItem.java:1386`): applies when the weight exceeds `maxCarry - current weight`
   - "You can't carry that many items." (`StdItem.java:1391`)
   - "You can't get @x1." (`StdItem.java:1396`)
5. **Send.** `R.send(mob, msg)` (`Get.java:91`). First `StdMOB.executeMsg` for the actor (`MOBS/StdMOB.java:3412`). Its default branch echoes the source text, "You get a sword.", unless `MASK_CNTRLMSG` is set (`StdMOB.java:3694-3695`).
6. **Others.** `sendAndExec` runs each other mob's `executeMsg`. The others branch shows "Bob gets a sword." because the code has `MASK_HANDS` and no `MASK_CNTRLMSG`, and the viewer is awake and can see the actor (`StdMOB.java:3899-3901`).
7. **Move.** The room's `executeMsg` reaches the sword's `StdItem.executeMsg` (`StdItem.java:1630`), which dispatches `TYP_GET` to `CommonMsgs.handleBeingGetted` (`StdItem.java:1715-1716`). For a plain item that method runs `R.delItem(item)` (`CommonMsgs.java:2702`) and `mob.addItem(item)` (`CommonMsgs.java:2705`), then recalculates the room unless `MASK_OPTIMIZE` is set.
8. **Finish.** `monitorGlobalMessage`, then trailers. A GET has none.

Compound verbs reuse the same parts. A GIVE reaches its target through `StdMOB.java:3712-3714`, which calls `CommonMsgs.handleBeingGivenTo` (`CommonMsgs.java:2561-2575`). That method sends two intermediate messages with `MASK_INTERMSG`: a DROP by the giver and a GET by the receiver. It sends them with `Room.send` and no `okMessage` check, because the GIVE itself has already been approved.

### 4. From bits to text: what each observer sees

The rules are in `StdMOB.executeMsg` (`MOBS/StdMOB.java:3412-3910`). Two flags are computed once per message (`StdMOB.java:3452-3455`): `canseesrc = CMLib.flags().canBeSeenBy(srcM, this)` and `canhearsrc` (moving or speaking, depending on the type). Each branch also checks `asleep`.

- **Actor (`3484-3697`).** The actor's own text is echoed by the default branch, unless `MASK_CNTRLMSG` (`3694`).
- **Target (`3704-3802`).** Target text is shown when the target can hear (`MASK_SOUND`, `canhearsrc`, `3779`), can see (`MASK_EYES`, `canseesrc`, `3793`), or the target is moving or mouthing and can either see or hear (`3801`).
- **Others (`3806-3910`).** In order:
  - `MASK_SPAMMY` text is dropped for viewers with NOSPAM (`3871`).
  - Channel text goes to the channel filter (`3876-3881`).
  - `MASK_SOUND` and can-hear shows the text (`3884`).
  - `TYP_AROMA` shows if the viewer can smell.
  - `MASK_EYES`, `MASK_HANDS` or `MASK_ALWAYS`, without `MASK_CNTRLMSG`, shows if awake and can see the actor (`3899-3901`).
  - `MASK_MOVE`, or `MASK_MOUTH` without `MASK_SOUND`, shows if awake and the actor can be seen or heard (`3905-3906`).
  - Enter, leave and flee have their own branch (`3829`).

One message string serves every observer. Blind, deaf, sleeping and far-away observers are handled by the flags, not by separate texts.

Text expansion happens at output. `StdMOB.tell` (`StdMOB.java:3376-3380`) hands the text to `Session.stdPrintln`. `DefaultSession.stdPrintln` (`Common/DefaultSession.java:1715-1718`) passes it through `applyFilters` (`DefaultSession.java:1675-1695`) and then `CoffeeFilter.fullOutFilter` (`Libraries/CoffeeFilter.java:1144`). That filter resolves tokens: `<S-NAME>`, `<T-NAME>` and the pronoun forms are listed in the `Pronoun` enum (`CoffeeFilter.java:57-76`) and parsed in the `case '<'` branch (`CoffeeFilter.java:1404-1407`). The filter also wraps and colours text. The verb-agreement rule behind "get(s)" was not traced.

### 5. Time: ticks

- **Tick length.** `TIME_TICK = 4000` ms (`core/CMProps.java:681`). `TICKTIME` below 500 is replaced by 4000 (`CMProps.java:2720-2722`). `TICKS_PER_RLMIN = 60000 / TIME_TICK`, which is 15 at the default (`CMProps.java:683`).
- **Contract.** `tick(ticking, tickID)` returns true to keep ticking and false to stop for good (`core/interfaces/Tickable.java:58-72`).
- **Tick IDs** (`Tickable.java:82-136`): MOB 0, ITEM_BEHAVIOR 1, EXIT_REOPEN 2, DEADBODY_DECAY 3, LIGHT_FLICKERS 4, TRAP_RESET 5, TRAP_DESTRUCTION 6, ITEM_BOUNCEBACK 7, ROOM_BEHAVIOR 8, AREA 9, ROOM_ITEM_REJUV 10, EXIT_BEHAVIOR 11, SPELL_AFFECT 12, QUEST 13, CLAN 14, CLANITEM 15, EMAIL 16 (solitary), and others up to EVENT 27. `TICKID_SOLITARYMASK = 65536` (`Tickable.java:77`) asks for a private thread group.
- **Registration.** `ServiceEngine.startTickDown` (`core/threads/ServiceEngine.java:349-400`) puts the object into an existing group with the same interval and thread-group letter that has room (`getMaxObjectsPerThread`), or creates one. A solitary ID always gets a new group. If the object is already registered for that ID, the method returns null (`ServiceEngine.java:385`).
- **Countdown.** `StdTickClient.tickTicker` (`core/threads/StdTickClient.java:140-170`) decrements `tickDown` on each period. At zero it calls `tick` and resets to `reTickDown`. So a registration with `numTicks = N` ticks every N periods. `StdTickGroup.java:351-354` removes a client whose `tickTicker` returns true.
- **MOB tick order** (`MOBS/StdMOB.java:4007-4410`): start (`4011`), death and rejuvenation handling (`4018-4070`), effects (`4318`; a false from an effect's `tick` calls `unInvoke`), behaviours (`4337`), scripts (`4349`), factions (`4382`), classes (`4391`), race (`4394`), end (`4396`). Each phase sets `tickStatus`, using the `STATUS_*` values in `Tickable.java:139-179`.
- **Effect duration.** `StdAbility.tick` (`Abilities/StdAbility.java:2843-2861`) counts `tickDown` down only on `TICKID_MOB` ticks. At zero it calls `unInvoke` and returns false. So an effect on a mob lasts N mob ticks, which is about 4 s × N at the default. Effects on rooms or items need their own ticker, for example `Abilities/Properties/Prop_WeakBridge.java:144`.
- **Item tickers are lazy.** An item registers its behaviour ticker only when it gets its first behaviour (`Items/Basic/StdItem.java:2064-2070`).

### 6. Stats: layers and recomputation

The contract (`core/interfaces/Affectable.java:57-82`) is that base stats are the builder's values, and `recoverPhyStats()` copies them into the live object and then applies every contributor. `StatsAffecting` gives each contributor a hook (`StatsAffecting.java:53`).

- **Item** (`StdItem.java:219-226`): copy base; apply own effects; clear the hidden flag on owned items.
- **Mob** (`StdMOB.java:798-835`), in order: copy base; speed adjustment; weight adjustment; room `affectPhyStats`; riding; deity; each class and race; each carried item (see below); effects; factions; a light-source check for followers.
- **Carried items** use `ApplyRecAffectPhyStats` (`StdMOB.java:191-205`): recover the item's own stats first, then apply its contribution to the mob.
- **Room** (`StdRoom.java:1291-1299`): area, own effects, items, inhabitants. `reallyRecoverRoomStats` (`StdRoom.java:1316-1323`) then recovers exits, items and inhabitants.
- **Coalescing.** `recoverRoomStats` (`StdRoom.java:1329-1343`) uses an `AtomicInteger` marker (`StdRoom.java:73`). A re-entrant call returns at once, and the owner runs one extra pass if the marker was raised meanwhile.
- **Deduplication.** `addEffect` ignores a second effect with the same ID (`StdItem.java:1936-1946`). `addBehavior` does the same for behaviours (`StdItem.java:2056-2064`).

### 7. Expiry

- **Categories and timeouts** (`core/interfaces/ItemPossessor.java:83-108`; defaults in `core/CMProps.java:3067-3081`), in minutes: Monster_EQ 30, Player_Drop 1200, Resource 60, Monster_Body 30, Player_Body 1330. `Never` and `Inheret` do not expire. `getExpirationMilliseconds` multiplies by `TimeManager.MILI_MINUTE` (`ItemPossessor.java:106`).
- **Setting.** `StdRoom.addItem(item, expire)` (`Locales/StdRoom.java:2358-2384`) stores `now + ms` as the expiration date. Body categories are inserted at the top of the list.
- **Sweep.** `CMMap.roomMaintSweep` (`Libraries/CMMap.java:2609-2680`) runs from `CMMap.tick` (`CMMap.java:2475`). It queues each expired room (`ROOM_EXPIRATION_MILLIS = 2500000`, `Libraries/interfaces/WorldMap.java:48`) and each owned item or mob whose date has passed. Each is offered as `MSG_EXPIRE` with `okMessage`, then sent with `sendOthers`. Items destroy themselves on `TYP_EXPIRE` (`StdItem.java:1750-1753`).

### 8. Message catalogue (selected)

Values were computed from the constants in `CMMsg.java`.

| Constant | Value | Major bits | Action type (value) | Typical use |
|---|---|---|---|---|
| `NO_EFFECT` | 0 | none | none | "nothing happens" on target or others side |
| `MSG_GET` | 2056 | HANDS | GET (8) | Taking an item (`Get.java:81`) |
| `MSG_REMOVE` | 2128 | HANDS | REMOVE (80) | Taking a worn item off first (`Get.java:76`) |
| `MSG_DROP` | 2063 | HANDS | DROP (15) | Dropping (`Drop.java:64`) |
| `MSG_PUT` | 2055 | HANDS | PUT (7) | Putting into a container |
| `MSG_GIVE` | 2060 | HANDS | GIVE (12) | Giving to a mob (`StdMOB.java:3712`) |
| `MSG_WEAR` / `MSG_WIELD` | 2064 / 2059 | HANDS | WEAR (16) / WIELD (11) | Equipment |
| `MSG_OPEN` / `MSG_CLOSE` / `MSG_LOCK` / `MSG_UNLOCK` | 2053 / 2054 / 2058 / 2057 | HANDS | 5 / 6 / 10 / 9 | Doors and containers |
| `MSG_LOOK` | 8216 | EYES | LOOK (24) | Looking at an object (`StdRoom.java:946`) |
| `MSG_READ` | 8217 | EYES | READ (25) | Reading |
| `MSG_EXAMINE` | 8284 | EYES | EXAMINE (92) | Close look |
| `MSG_SPEAK` | 49179 | MOUTH, SOUND | SPEAK (27) | Speech |
| `MSG_TELL` | 65611 | ALWAYS | TELL (75) | Private messages |
| `MSG_EMOTE` | 34882 | HANDS, SOUND | EMOTE (66) | Emotes |
| `MSG_CAST_VERBAL_SPELL` | 180252 | MAGIC, MOUTH, SOUND | CAST_SPELL (28) | Spoken spell |
| `MSG_CAST` | 182300 | HANDS, MAGIC, MOUTH, SOUND | CAST_SPELL (28) | Full spell |
| `MSG_WEAPONATTACK` | 563223 | HANDS, MOVE, SOUND, MALICIOUS | WEAPONATTACK (23) | Melee attack |
| `MSG_ATTACKMISS` | 39034 | HANDS, MOVE, SOUND | ATTACKMISS (122) | Missed attack |
| `MSG_DAMAGE` | 65618 | ALWAYS | DAMAGE (82) | Damage applied |
| `MSG_DEATH` | 98367 | ALWAYS, SOUND | DEATH (63) | Death |
| `MSG_ENTER` / `MSG_LEAVE` / `MSG_FLEE` | 36895 / 36897 / 36901 | MOVE, SOUND | ENTER (31) / LEAVE (33) / FLEE (37) | Room changes |
| `MSG_TRAVEL` | 36992 | MOVE, SOUND | TRAVEL (128) | Intent to move |
| `MSG_SLEEP` / `MSG_SIT` / `MSG_STAND` | 4130 / 4131 / 4132 | MOVE | 34 / 35 / 36 | Posture |
| `MSG_EAT` / `MSG_DRINK` | 18462 / 18487 | HANDS, MOUTH | EAT (30) / DRINK (55) | Consumption |
| `MSG_BUY` / `MSG_SELL` | 51213 / 51214 | HANDS, MOUTH, SOUND | BUY (13) / SELL (14) | Shops |
| `MSG_OK_ACTION` | 98357 | ALWAYS, SOUND | OK_ACTION (53) | Confirmed noisy event |
| `MSG_OK_VISUAL` | 65590 | ALWAYS | OK_VISUAL (54) | Confirmed quiet event |
| `MSG_EXPIRE` | 65630 | ALWAYS | EXPIRE (94) | Expiry sweep |
| `MSG_COMMAND` | 4259960 | ALWAYS, CNTRLMSG | COMMAND (120) | Meta command |
| `MSG_SKILLFAIL` | 65685 | ALWAYS | SKILLFAIL (149) | Failed skill |
| `MSG_WEATHER` | 104571 | ALWAYS, HANDS, MOVE, SOUND | WEATHER (123) | Weather |
| `MSG_THINK` | 129 | none | THINK (129) | Internal thought, never seen |

The full set has about 150 action types. Most hand verbs share one perception pattern, so a game can classify them by mask as well as by name.

## Content worth mining

- **A verb table with default perception.** The catalogue above is a ready-made table. Hand verbs (GET, DROP, PUT, GIVE, WEAR, WIELD, HOLD, OPEN, CLOSE, LOCK, UNLOCK) are all HANDS-only, so one rule covers them. Speech is MOUTH and SOUND. Looking is EYES. Combat is HANDS, MOVE, SOUND and MALICIOUS. A single-player game can use these bit patterns directly for "does the player notice this?"
- **Expiry defaults** (section 7): equipment dropped by monsters 30 minutes, player drops 20 hours, resources 1 hour, monster corpses 30 minutes, player corpses 22 hours 10 minutes. Room expiry is about 41.7 minutes.
- **Ticker subsystems** (`Tickable.java:82-136`): doors that close themselves, corpse decay, lights that flicker, trap reset and destruction, innkeys returning to the desk, room and area behaviours, rejuvenation, quests, clans, auctions, electronics and calendar events. Each is a candidate timer for a Python world.
- **Ability cost currencies** (`core/interfaces/CostDef.java:33-45`): TRAIN, PRACTICE, MANA, MOVEMENT, HITPOINT, XP, GOLD, QP.
- **Mount types** (`core/interfaces/Rideable.java:44-56`): land, water, air, furniture (sit, sleep, table), enter-in, ladder, wagon and hook.
- **Climate flags** (`core/interfaces/Places.java:91-113`): NORMAL 0, WET 1, COLD 2, WINDY 4, HOT 8, DRY 16, VOID 32, with a full mask of 63.
- **Ready-made messages** (short quotes): "You can't carry that many items.", "You can't see that.", "@x1 is too heavy.", "There is nothing written here.", "You can't do that here." (`StdRoom.java:849`), "<S-NAME> get(s) <T-NAME>." (`Get.java:81`).
- **An appraisal NPC.** `Behaviors/ItemIdentifier.java` (lines 81-150) makes an observer charge a fee to identify an item handed to it. It then speaks a description: material, weight, weapon class and damage type, and whether the item is cursed. The fee refusal is "You'll need @x1 for me to identify that." (`ItemIdentifier.java:101`). A useful template for a shopkeeper or sage NPC.

## Notable design ideas

1. **One message, many perceptions.** The perception bits decide who notices an event (`StdMOB.java:3806-3910`). The game writes one sentence and gets the right result for sighted, blind, deaf, sleeping and distant observers.
2. **Veto and reason together.** `okMessage` says why it refuses, in the same place it refuses (for example `StdItem.java:1366`). The check and its explanation cannot drift apart.
3. **Follow-up events queued by the event.** Trailers run after the main event (`StdRoom.java:1701-1728`). `ItemIdentifier.java:131-154` queues an examine, a speech line, a give and a drop as one reaction to a single GIVE. Depth is capped at 3.
4. **Compound verbs decomposed into intermediate messages.** A GIVE is a DROP plus a GET, flagged `MASK_INTERMSG` so that listeners such as auto-bundling can skip them (`CommonMsgs.java:2571-2573`, `2746`).
5. **Batch flag.** `MASK_OPTIMIZE` lets a bulk action such as "get all" skip per-item room recalculation and do the weight change by hand (`CommonMsgs.java:2674-2708`).
6. **Coalesced recalculation.** `recoverRoomStats` (`StdRoom.java:1329-1343`) collapses repeated requests into one or two passes.
7. **Copy-on-write lists.** `SVector` is backed by a `CopyOnWriteArrayList` (`core/collections/SVector.java:26-42`), so readers never lock. Problem 2 shows a case where index-based iteration over the same list still goes wrong.
8. **Tick groups.** Objects are grouped by tick interval and thread group, with a solitary flag for heavy objects (`ServiceEngine.java:373-400`).
9. **Lazy expiry.** Items store a timestamp, and a periodic sweep removes them (`CMMap.java:2609-2680`). No timer per item.
10. **Return false to stop.** Ticks and effects end by returning false (`Tickable.java:70-72`; `StdAbility.java:2843-2861`).
11. **Serialized messages.** `toFlatString` and `parseFlatString` write a whole message to one line, with escapes for commas (`DefaultMessage.java:790-909`). Useful for logs and replays.

## Problems and cruft

Items 1 to 3 are the most serious. All are logic errors found by reading and were not run.

1. **Live items can disappear from `WeakItemCollection`** (`Common/WeakItemCollection.java:196-218` and `242-261`; by reading). `getItem(i)` (`264-278`) already removes a cleared slot. `delItem` and `isContent` then call `innerContents.remove(i)` a second time. That second call removes whatever moved into slot i, which is a live item whenever the cleared slot is not the last one. The class backs clan and player external-item stores (`Common/DefaultClan.java:96`, `Common/DefaultPlayerStats.java:173`), so the loss is reachable in play once a stored item has been collected.
2. **Items miss the message after a pick-up** (`Locales/StdRoom.java:995-1002` with `Libraries/CommonMsgs.java:2702`; by reading). `StdRoom.executeMsg` loops by index over the live list. The target item removes itself during its own `executeMsg`, so the item after it shifts into the removed slot and the loop steps past it. That item never receives the GET's `executeMsg`, so its behaviours, effects and scripts miss the event. Any listener that removes items during the loop has the same problem.
3. **Mask overlap.** `MAJOR_MASK = 0x7FFFFC00` (`CMMsg.java:940`) includes bit 1024. Bit 1024 is also the top bit of `MINOR_MASK` (`CMMsg.java:938`). Every channel code (2000 to 2047) therefore carries bit 1024 in its major part. I found no code that tests bit 1024, but the overlap is unintended. The value that would split the two cleanly is `0x7FFFF800` (2147481600).
4. **Name tables are wrong in places** (checked by script against the constants).
   - `MASK_DESCS` entry 11 is named "INTERMSG" but maps to 4194304, which is `MASK_CNTRLMSG` (`CMMsg.java:1316-1319` and `966`). `MASK_INTERMSG` (`968`) and `MASK_SPAMMY` (`970`) have no name, so string lookup cannot reach them.
   - `TYPE_DESCS[128]` is "PREMOVE", but `TYP_TRAVEL = 128` (`CMMsg.java:1228`, `1309`).
   - `TYP_QUESTSTART`, `TYP_QUESTSTOP` and `TYP_AGE` (`CMMsg.java:1276-1280`) have no name; the array ends at index 151.
   - The reverse map for "CHANNEL" stores 2046, not a base value: the loop is `i2 < 2047` and each pass overwrites the same key (`CMMsg.java:1347-1352`).
   - Two comments name the wrong constant: `MSG_WROTE` is described as "fail to target" (`CMMsg.java:1628-1629`), and `MSG_TRAIN` uses the noisy-movement type (`CMMsg.java:1663`).
5. **Code 0 matches everything** (`DefaultMessage.java:672-675`). `matches` returns true when the code is 0, so `isSource(0)` and `isTarget(0)` are always true. Callers must test `NO_EFFECT` first. `StdMOB` mostly does (for example `StdMOB.java:3484`).
6. **`equals` without `hashCode`** (`DefaultMessage.java:764-788`). `equals` compares codes, actors, targets and texts, but `hashCode` is identity, so equal messages can hash differently.
7. **Serialization loses or rejects some messages** (`DefaultMessage.java:790-909`). A null source cannot round-trip: `parseFlatString` requires a mob (`881-895`). An empty others text is written as an empty last field, which `split(",")` discards, so parsing sees 11 fields and throws (`800-814`, `882-883`). A deserialized mob is placed in a random room (`873-874`).
8. **Recycled messages keep old state.** `StdElecPanel.java:250` and `StdCompPanel.java:240` return a message to the pool without clearing it. `modify` does not reset trailers, `value` or the suspend flag (`DefaultMessage.java:141-245`), and `returnMsg` does not clear them (`core/CMClass.java:3661-3676`). Only the finalizer path clears them (`DefaultMessage.java:117-138`). A reused message can therefore run stale trailers or carry a stale value.
9. **The pool depends on the garbage collector.** Messages return to the pool only through `DefaultMessage.finalize` (`DefaultMessage.java:117-138`). Finalizers are deprecated in current JDKs and may run late or never, so the pool gives no reliable reuse.
10. **One exception ends a ticker permanently** (`core/threads/StdTickClient.java:158-161`; `StdTickGroup.java:351-354`). `tickTicker` catches any `Throwable`, logs it, and returns true, which removes the client. A mob whose tick throws stops being ticked, with a log line as the only trace. The downstream effect was not traced.
11. **`startTickDown` returns null** for an object that is already registered (`ServiceEngine.java:378-385`). A caller that uses the returned client without checking fails silently.
12. **Inconsistent tick comparator.** `TickClient.compareTo` orders by identity hash code and returns -1 in both directions when hash codes are equal (`StdTickClient.java:111-119`).
13. **Possible stale room stats** (`StdRoom.java:1329-1343`; by reading). After the second pass, `finally` sets the marker to 0. A request made during the second pass is absorbed without a rerun, so room stats can stay stale until the next change.
14. **Duplicates are allowed.** `ItemCollection.addItem` promises "Duplicates will not be permitted" (`core/interfaces/ItemCollection.java:33-35`), but `DefaultItemCollection.addItem` never checks (`Common/DefaultItemCollection.java:156-161`).
15. **`WeakItemCollection` index bugs.** `copyOf` pairs items by index after skipping cleared entries (`WeakItemCollection.java:77-125`), so container links can attach to the wrong item. `eachItem` removes entries while iterating forward and skips the next one (`281-307`).
16. **Dead interface.** `EventHandlers` (`core/interfaces/EventHandlers.java:33-38`) says "Currently unused. Move along." Nothing in the tree implements or calls it.
17. **Naming drift.** The `Expire` enum has a misspelt `Inheret` (`ItemPossessor.java:91`).
18. **Cost of the design.** Every action is offered to every item, effect and exit in the room (`StdRoom.java:745-903`). Each move recalculates every inhabitant and item (`StdRoom.java:1316-1323`). Not measured. `MASK_OPTIMIZE` exists to avoid some of it.

## Takeaways for a Python SUD

The brief leaves real-time versus turn-based open. Most of these suggestions hold either way.

1. **Event object.** A dataclass with `kind` (an enum of action types), `actor`, optional `target` and `tool`, `flags` (an `IntFlag` of perception bits), `texts` for actor, target and witnesses, `value`, and a list of `follow_ups`. Keep a single table that maps each kind to its default flags, as `MSG_*` does.
2. **Perception as `IntFlag`.** Give HANDS, MOVE, EYES, MOUTH, SOUND, ALWAYS, MAGIC, MALICIOUS, INTERNAL and SPAM explicit, non-overlapping values. `enum.IntFlag` also prints names, which removes the table drift described in problem 4.
3. **One perception check for the player.** `can_perceive(player, event)` is true when EYES and the room is lit, or SOUND and the player is not deaf, or HANDS and the object is within reach, or ALWAYS. Drop the sleeping, follower, channel and room-sync branches, which exist for multiplayer.
4. **Two-phase dispatch.** `veto(event)` returns a reason or None and runs over the room, then items, effects and exits, stopping at the first refusal. `apply(event)` runs afterwards. Queue follow-ups with `event.then(...)`, with a depth cap of 3.
5. **Iterate over snapshots.** Use `for item in list(room.items)` and mutate after the loop, or key items by id. This avoids problem 2.
6. **Stats as a fold.** Store base values. Compute effective values with a function that folds over modifiers in a fixed order (room, equipment, effects), cache the result, and invalidate on change. Do not recalculate a whole room on every move.
7. **Ticks.** Each object has `tick(ctx) -> bool`, and a False return unregisters it. Use integer countdowns, as `StdTickClient` does, and skip thread groups, solitary flags and status fields. For real time, a heap of `(next_due, object)` gives the same behaviour.
8. **Expiry.** Store `expires_turn` on items and sweep every N turns, as `CMMap` does. Use the timeouts above, expressed in game-minutes.
9. **Effects count down on the owner's tick.** `StdAbility.tick` shows the pattern: the owner calls `effect.tick(owner)` each turn, and a False return removes the effect and calls `on_expire`.
10. **Do not port.** The finalizer pool, the re-entrancy marker (the game is single-threaded), `MASK_OPTIMIZE` as a visible flag, sleeping and follower perception, channel routing and GMCP output.
11. **Verb data.** Keep the verb table as a Python dict or YAML file. Give each verb default text templates with tokens such as `{actor}` and `{target}`, and helpers for pronouns and verb agreement.

## Coverage

- **Read in full (33 files):** `Common/interfaces/CMMsg.java`; `Common/DefaultMessage.java`; `core/interfaces/` Environmental, MsgListener, Physical, CMObject, Tickable, Affectable, Behavable, StatsAffecting, Contingent, Expireable, Modifiable, EventHandlers, TickClient, TickableGroup, Combatant, PhysicalAgent, ItemPossessor, ItemCollection, MsgMonitor, Taggable, Decayable, Rider, Rideable, MOBCollection, EachApplicable, CMObjectWrapper; `Common/interfaces/CMCommon.java`; `Common/DefaultEnvironmental.java`; `Common/DefaultModifiable.java`; `Common/DefaultItemCollection.java`; `Common/WeakItemCollection.java`.
- **Read in part (1 file):** `core/interfaces/MUDCmdProcessor.java`, its constants and the action-point and `enqueCommand` contracts.
- **Skimmed, declarations and inheritance only (26 files):** Places, Followable, LiquidHolder, CloseableLockable, MudHost, FactionMember, AbilityContainer, Tattooable, Readable, Recipes, RecipeDriven, Achievable, BoundedObject, CostDef, PrivateProperty, Drink, Dischargeable, PlayerOwned, CostManager, SensedEnvironmental, DBIdentifiable, AutoBundler, XMLConfigureable, Cost, MOBPossessor, CMCallback.
- **Outside the bucket, followed for interaction:** `Locales/StdRoom.java` (okMessage, executeMsg, send, sendAndExec, trailers, stat recovery, addItem with expiry); `Items/Basic/StdItem.java` (okMessage, executeMsg, recoverPhyStats, addEffect, addBehavior); `MOBS/StdMOB.java` (okMessage, executeMsg, recoverPhyStats, tick); `Commands/Get.java`; `Commands/Drop.java`; `Libraries/CommonMsgs.java` (get, drop, remove, give, monitorGlobalMessage); `core/CMClass.java` (getMsg, returnMsg); `core/threads/ServiceEngine.java`, `StdTickClient.java`, `StdTickGroup.java`; `Libraries/CMMap.java` (sweep); `Abilities/StdAbility.java` (tick, unInvoke); `Behaviors/ItemIdentifier.java`; `Libraries/CoffeeFilter.java` and `Common/DefaultSession.java` (output path); `core/collections/SVector.java`; `Items/BasicTech/StdElecPanel.java`, `Items/CompTech/StdCompPanel.java` (message recycling); `Libraries/interfaces/WorldMap.java`, `core/CMProps.java` (constants).
- **Checked by script:** the mask values, the type-name table, and the derived `MSG_*` values were computed from the constants in `CMMsg.java` and compared with the name tables. Results are in problems 3 and 4 and in section 8.
- **Skipped:** none. Every file in the manifest was at least scanned.
- **Not verified:** the verb-agreement rule that renders "get(s)"; the consequence of a ticker being dropped after an exception (problem 10); the real-world frequency of the stale-room-stats race (problem 13); the runtime effect of problems 1 and 2.
