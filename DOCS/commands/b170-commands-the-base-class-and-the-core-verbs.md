# Commands: the base class and the core verbs

> Part of the CoffeeMud NEWOVERVIEW. Bucket: `b170` (Commands: the base class and the core verbs) · 25 files · 6162 lines of Java.

## Summary

The typed verbs that are not skills, socials or channels are `Command` objects: one prototype instance per verb, registered under one or more access words, and run with a copy of the parsed words and the acting MOB (`Commands/interfaces/Command.java:39-133`, `MOBS/StdMOB.java:2250-2261`). This bucket holds that contract, its base class `StdCommand`, and the core verbs most play depends on: movement and fleeing, looking, speech, taking, dropping, giving, wearing, killing, sleeping, following and learning from trainers. Almost every verb has the same shape: check the words, build a `CMMsg`, let the room and its contents veto it, then send it, so the verb never edits game state itself. Each verb also declares an action cost paid from a pool that refills every tick, which is why a typed line can be queued and paced. A single-player game would keep the message flow and the action economy; the follower ordering, forced-command and session-pacing parts would mostly be dropped.

## Key files and types

Paths are relative to the CoffeeMUD folder. Java paths below omit the `com/planet_ink/coffee_mud/` prefix, so `Commands/Get.java` means `com/planet_ink/coffee_mud/Commands/Get.java`. Outside this bucket, paths are given in full.

| File or class | Role | Lines |
|---|---|---|
| `Commands/interfaces/Command.java` | Contract: access words, action costs, `securityCheck`, `execute`, `preExecute`, `executeInternal` | 181 |
| `Commands/StdCommand.java` | Base class: ID from class name, cost defaults, argument checks, room target lookup, prototype cloning | 316 |
| `Commands/Go.java` | Movement, compound direction input, queued multi-step walks; base of the direction verbs (East and the others are outside this bucket) | 283 |
| `Commands/Flee.java` | Flight from combat (extends Go), random exit choice, experience penalty and reward | 184 |
| `Commands/Say.java` | Speech, addressing a mob or a neighbouring room, language swap | 434 |
| `Commands/Look.java` | Look at the room, items, exits, containers, directions | 351 |
| `Commands/Get.java` | Take items, from containers, `all` batching | 284 |
| `Commands/Drop.java` | Drop items, auto-unwield, coins | 258 |
| `Commands/Put.java` | Put into containers; redirects "put X on" to Wear; "put out" | 305 |
| `Commands/Give.java` | Give to a visible mob | 231 |
| `Commands/Wear.java` | Wear, with body-location parsing | 203 |
| `Commands/Wield.java` | Wield a weapon | 137 |
| `Commands/Kill.java` | Start combat, retarget, staff `kill ... dead` | 240 |
| `Commands/Sleep.java` | Sleep, and `sleep until` as a tickable auto-waker | 221 |
| `Commands/Sit.java` | Sit down, sit on a rideable or exit, sit by a mob (Mount) | 120 |
| `Commands/Follow.java` | Follow and unfollow; group and NOFOLLOW rules | 210 |
| `Commands/Open.java` | Open objects and exits, mirrors the paired exit | 164 |
| `Commands/Eat.java` | Eat food, from containers, partial bites | 157 |
| `Commands/DrinkCmd.java` | Drink from a drink or from a container | 128 |
| `Commands/Inventory.java` | Inventory listing, coin grouping, name mask | 220 |
| `Commands/Score.java` | Character sheet; extends Affect | 442 |
| `Commands/Train.java` | Train attributes, hit points, movement, practices or class with a teacher, cost confirmation | 699 |
| `Commands/Practice.java` | Practise a known skill with a teacher in the room | 170 |
| `Commands/Teach.java` | Teach a skill or expertise to a student | 144 |
| `Commands/Gain.java` | Rewrites "gain X" into speech addressed to the room | 80 |

## How it works

### 1. From typed line to Command object

#### 1.1 Session input and the queue

A line typed by a player is handled in the session's input loop. It is split with `CMParms.parse` (`Common/DefaultSession.java:3637`), expanded through the player's aliases (`:3644-3651`), and passed through the language's command pre-processor (`:3670`, `Libraries/DirtyLanguage.java:616`). Each resulting word list goes to `enqueCommand` with cost 0, which means "compute the cost from the verb" (`MOBS/StdMOB.java:2293`, `:2413`). The mob's action pool is set to 0 during this step (`Common/DefaultSession.java:3656-3657`) and restored afterwards (`:3675`), and the loop then runs `mob.dequeCommand()` until nothing is ready (`:3686`). In effect, a line runs at once only if it costs nothing. Anything with a cost waits in the queue until the pool holds enough actions.

#### 1.2 Resolution order

`enqueCommand` and `doCommand` both call `CMLib.english().findCommand` (`MOBS/StdMOB.java:2243`, `:2417`), implemented in `Libraries/EnglishParser.java:976-1110`. The passes run in this order, and the first match wins:

| Pass | What matches | Lines |
|---|---|---|
| 0 | A first character that is not a letter or digit is split off as its own word, so `'hello` becomes `'` and `hello` | `EnglishParser.java:986-991` |
| 1 | Exact command word, then the command's `securityCheck` and the DISABLE check | `:993-998` |
| 2 | Exact ability trigger word | `:1000-1003` |
| 3 | Ability invoke word the mob does not have: returns null, reported as "You don't know how to ..." | `:1005-1006` |
| 4 | Exact social, channel name, NO-channel, command journal | `:1008-1039` |
| 5 | Prefix match on ability trigger words | `:1041-1064` |
| 6 | Prefix match on command words, with `securityCheck` and DISABLE | `:1067-1071` |
| 7 | Prefix match on social, channel, journal | `:1073-1108` |
| 8 | `CMLib.leveler().deferCommandCheck(mob, null, ...)` | `:1110` |

Because ability trigger prefixes (pass 5) are tried before command prefixes (pass 6), a short typed prefix can reach a skill before a command. `deferCommandCheck` can also swap the matched command for `DeferCmd` at passes 1 and 6 while experience is being held back (`Libraries/CoffeeLevels.java:1533-1636`, setting `EXPDEFER` at `coffeemud.ini:774`).

If the mob has no location, `findCommand` returns null at once (`EnglishParser.java:978-982`), and the caller reports "Huh?" (`Libraries/CommonMsgs.java:91-100`).

#### 1.3 Abbreviation

Exact lookup is a hash lookup on the upper-cased access word (`core/CMClass.java:1493-1497`, table declared at `:234`). Prefix lookup (`:1498-1507`) walks the same Hashtable and returns the first key that starts with the typed text, so the winner depends on the Hashtable's enumeration order, which the code does not control. Among the 306 command classes that declare a static access list, no two claim the same word, so exact matches never collide. Prefixes do collide. Taking the declared lists at face value, "WE" matches Wealth, Wear, Weather and West, "GE" matches Generate and Get, "WA" matches Go (through WALK) and Wake, and "SI" matches only Sit. A short prefix can therefore reach one of several verbs arbitrarily (see Problems).

#### 1.4 Access words and registration

Each class lists its words in an `access` array, and `I()` passes each one through the language's command-word translation (`Commands/StdCommand.java:78-83`, `Libraries/DirtyLanguage.java:1021-1025`). Example: `Commands/Say.java:42-45` declares `SAY`, a backtick, `SA` and `SAYTO`. `CMClass.reloadCommandWords()` (`core/CMClass.java:3165-3180`) rebuilds the map at boot (`:3553`) and whenever a builder creates or replaces a command (`Commands/Create.java:1486`). A command's ID is its class name (`Commands/StdCommand.java:40-48`). `CMClass.getCommand(id)` returns the prototype (`core/CMClass.java:1289-1291`), and code uses it to delegate, for example `CMClass.getCommand("Wear")` in `Commands/Put.java:120`.

Related verbs are subclasses that change one word or one rule. `Flee` and `East` extend `Go`, `Empty` extends `Drop`, `Draw` extends `Get`, `Wealth` and `Worth` extend `Inventory`, `Shout` `Yell` and `Ask` extend `Say`, and `NoFollow` extends `Follow`.

#### 1.5 Dispatch

`doCommand` (`MOBS/StdMOB.java:2241-2248`) finds the object and sends it to `doCommand(Object, ...)` (`:2250-2288`), which handles Commands, Socials and Abilities. For a Command it records achievement and statistics use (`:2255-2259`), then calls `execute(this, new XVector<String>(commands), metaFlags)` (`:2261`). The verb therefore always gets a copy of the word list. The prototype itself is shared: `newInstance()` returns `this` and `copyOf()` clones it (`Commands/StdCommand.java:276-294`). A verb's per-call state must live in locals or on the mob. Some verbs keep mutable fields on the prototype (see Problems).

### 2. Security and eligibility

Three separate gates decide whether a typed verb runs.

- **Availability.** `securityCheck(mob)` (`Commands/interfaces/Command.java:101-108`) says whether a verb is available to that mob. The base returns true (`Commands/StdCommand.java:270-274`), and none of the 25 files overrides it. `findCommand` applies it to exact and prefix matches (`Libraries/EnglishParser.java:995-997`, `:1068-1070`), and the player's command list uses it too (`Commands/Commands.java:73-79`). The one direction verb that overrides it is `East` (outside this bucket, `Commands/East.java:68-75`): it is available only when the mob's direction type is the compass type, so the compass words are withheld where the room uses a different direction system.
- **Disabling.** A DISABLE entry `COMMAND_<ID>` is stored in the command set of `CMSecurity` (`core/CMSecurity.java:102`, `:1519-1522`). The shipped default disables `COMMAND_POSE` (`coffeemud.ini:2079`). The check uses the class ID in uppercase (`EnglishParser.java:997`, `:1070`) and does not apply to socials, channels or journals.
- **Verb-level staff rules.** `kill <name> dead` needs the KILLDEAD flag and a player (`Commands/Kill.java:81-83`). `give` refuses a property-protected item when the command was ordered or forced, unless the giver holds the ORDER flag (`Commands/Give.java:109-115`, `:159-165`). `score` shows the carried count and weight to a CARRYALL holder (`Commands/Score.java:293-297`).

Ordering is a separate eligibility rule. `canBeOrdered()` is true by default (`Commands/StdCommand.java:264-268`). The core verbs that return false are Train (`Commands/Train.java:695-697`), Practice (`Commands/Practice.java:165-167`), Follow (`Commands/Follow.java:205-207`) and Gain (`Commands/Gain.java:76-79`). The ordering code, outside this bucket, checks `canBeOrdered` and `securityCheck` together (`Commands/Order.java:157`).

Code-issued verbs go through `CommonMsgs.forceStandardCommand`, which calls `execute` directly with `METAFLAG_FORCED` (`Libraries/CommonMsgs.java:212-226`). That path skips the queue, the cost and the `securityCheck` call. The metaflag constants are at `core/interfaces/MUDCmdProcessor.java:38-56`.

The message system is the other gate. A verb sends its `CMMsg` through the room's `okMessage`, and a refusal leads to `postCommandRejection` (`Libraries/CommonMsgs.java:159-178`). See 5.1.

GenCommand is the one data-driven case. Its availability is a zapper mask in the `SECMASK` field (`Commands/GenCommand.java:142-146`, field codes `:150-158`), and its cost and ordering come from `ACTCOST`, `CBTCOST` and `ORDEROK`.

### 3. Action costs, the action pool and the queue

The Command contract says `actionsCost` is the number of actions needed to complete the verb, with 0.0 meaning instant, and that it applies outside combat (`Commands/interfaces/Command.java:57-68`). `combatActionsCost` is the in-combat value (`:70-81`). `checkedActionsCost` picks between them: combat if the mob is fighting, or if its ship is fighting (`Commands/StdCommand.java:241-262`). Gating is stated in the contract: the verb may run only after the action cost is paid and `securityCheck` passes (`Command.java:116-125`).

The defaults are:

- `StdCommand.actionsCost` returns `CMProps.getCommandActionCost(ID(), 0.0)` and `combatActionsCost` returns `getCommandCombatActionCost(ID(), 0.0)` (`Commands/StdCommand.java:223-233`). A verb that does not override either is free.
- A verb that calls the one-argument getters pays `DEFCMDTIME / 100` out of combat and `DEFCOMCMDTIME / 100` in combat (`core/CMProps.java:1183-1197`). Shipped values are 0.20 and 1.0 (`coffeemud.ini:545-546`). The ini stores them as hundredths (`core/CMProps.java:3099-3100`).
- Per-verb exceptions use the ini syntax `DEFCMDTIME=Score 1.0, East 0.5, 0.15`, keyed by uppercase ID (`coffeemud.ini:537-546`, `core/CMProps.java:1143-1152`).

The costs of the verbs in this bucket:

| Verbs | Out of combat | In combat | Source |
|---|---|---|---|
| Look, Inventory, Score | 0 | 0 | No override; `Commands/StdCommand.java:223-233` |
| Say, Get, Put, Drop, Give, Wear, Wield, Eat, DrinkCmd, Open, Sit, Sleep, Kill, Follow, Train, Practice, Teach, Gain | 0.20 | 1.00 | For example `Commands/Get.java:241-250`, `Commands/Say.java:417-426` |
| Go and the direction verbs, Flee | 0.20, times 0.25 when AUTORUN is set | 0.20, times 0.25 with AUTORUN, unless an ID exception exists | `Commands/Go.java:261-276` |

The Go row is not what the ini comment promises. See Problems.

The pool is `actions()`, a plain double (`MOBS/StdMOB.java:2103-2111`). Each tick a mob gains its speed, or half its speed while sitting, and when the queue is empty the fractional part is discarded first (`:4142-4144`). In the DEFAULT combat system a fighting mob also gains one action per tick (`:4207-4208`, `Commands/Kill.java:73`). With `TICKTIME=4000` (`coffeemud.ini:2151`) and a speed of 1.0, a mob out of combat gets one action per tick, enough for five 0.20-cost commands.

The queue (`MOBS/StdMOB.java:2142-2448`):

- `enqueCommand` appends, and a zero-cost command outside `METAFLAG_INORDER` runs at once (`:2429-2446`).
- `prequeCommand` and `prequeCommands` put the new work at the front (`:2340-2372`, `:2374-2411`). `Go` uses the plural form to run a multi-step route in order (`Commands/Go.java:254-255`).
- `dequeCommand` runs the head when `actions() - cost >= 0` (`:2157-2162`). A connected player also waits `cost / speed` ticks since the last command (`:2157-2160`), which paces typed input. A mob with no session is paced only by its pool.
- While a command waits, `preExecute(mob, commands, flags, secondsElapsed, actionsRemaining)` runs once a second (`:2197`, `:2216`). The base returns true (`Commands/StdCommand.java:100-105`).
- `canBeCancelled()` is false in the base (`:235-239`), so no verb in this bucket can be cancelled. `checkCommandCancel` (`MOBS/StdMOB.java:2314-2338`) runs when a new command is queued.

### 4. What StdCommand provides

`StdCommand` is small. Its contents are:

- **Identity.** `ID()` is the class name without its package (`Commands/StdCommand.java:40-54`), and `name()` returns it. `getAccessWords()` returns a field that is always null in the base (`:38`, `:63-66`). Each verb overrides it.
- **Translation helpers.** `L()` translates a message in the context of the class (`:73-76`), and `I()` translates access words (`:78-83`).
- **Typed internal call.** `executeInternal(mob, flags, args...)` builds a word list from the first access word and the non-null arguments, then calls `execute` (`:107-122`). Verbs that have a typed API declare an `internalParameters` table and check it with `checkArguments` (`:125-174`). A mismatch logs "Illegal arguments" through `Log.errOut`. Examples are `Commands/Give.java:50-55` and `:218-230`, and `Commands/Wear.java:49-57` and `:154-184`.
- **Room target lookup.** `getVisibleRoomTarget` (`:176-190`) finds a mob in the room by name. If the match is not visible, it retries `name.2`, `name.3` and so on, provided the typed name has no dot. `getVisibleRoomITarget` does the same for items (`:192-206`). This is the numbering convention players use for "the second guard".
- **Busy check.** `isOccupiedWithOtherWork` (`:208-221`) is true when the mob has a common-skill effect that is not auto-invoked. Push, Pull and Dig use it (`Commands/Push.java:133`, `Commands/Pull.java:133`, `Commands/Dig.java:96`).
- **Shipboard combat.** `checkedActionsCost` (`:241-262`) uses combat costs for a mob on a ship that is fighting.
- **Listing.** `putInCommandlist()` is true (`:311-315`), so the verb appears in the command list when `securityCheck` passes.
- **Prototype support.** `newInstance`, `copyOf` and `compareTo` (`:276-309`).

### 5. Shared verb patterns

#### 5.1 Check, message, send

A core verb validates its words, then builds a `CMMsg` and sends it through the room. `Get.get` (`Commands/Get.java:57-99`) sends a MSG_REMOVE from the room when the item is not already carried, then a MSG_GET to the mob. Each message goes through `R.okMessage(mob, msg)`, and if it is refused the verb returns false. Otherwise `R.send(mob, msg)` runs it. `Drop.drop` (`Commands/Drop.java:59-82`), `Give.give` (`Commands/Give.java:185-197`), `Sit` (`Commands/Sit.java:50-101`) and `Open` (`Commands/Open.java:51-109`) follow the same shape. `Wear.wear` (`Commands/Wear.java:59-86`) chooses MSG_WEAR, MSG_HOLD or MSG_WIELD from the item's body-location bits (`:63-77`) and stores the chosen location in `msg.value()` (`:79`).

The verb does not move the item. `StdItem.executeMsg` handles TYP_DROP and TYP_GET by calling `CMLib.commands().handleBeingDropped` or `handleBeingGetted` (`Items/Basic/StdItem.java:1715-1722`). Those functions (`Libraries/CommonMsgs.java:2722-2750`, `:2649`) remove the item from the mob and add it to the room. The room sends the message to the source first, then to the other inhabitants, then to the room, then to the global monitors (`Locales/StdRoom.java:1734-1738`, `:1688-1699`). The `okMessage` pass runs first (`Locales/StdRoom.java:745`).

#### 5.2 Noun phrases and batching

Four verbs parse object phrases in the same way: Get, Drop, Put and Give. Each one:

- Rewrites `all`, `all.X` and `X.all` into `ALL X` with an all-flag (`Commands/Get.java:133-143`, `Commands/Drop.java:131-141`, `Commands/Put.java:206-216`, `Commands/Give.java:93-103`).
- Reads a leading count, such as `3 sword`, through `CMLib.english().parseMaxToGive`. That function sets the maximum and rewrites the words to `all sword` (`Libraries/EnglishParser.java:2846` onwards). With a "from" container the carry limit is the mob's maximum carry, capped at 3000, and the message is "You can only handle N at a time." (`:2878`).
- Loops over the matches with a suffix counter, so `sword.2` is the second match (`Commands/Get.java:190`, `Commands/Drop.java:194`, `Commands/Put.java:243`, `Commands/Give.java:170`).
- Leaves coins out of a bare `all` request unless the mob carries only gold (`Commands/Drop.java:184-185`, `Commands/Put.java:233-234`, `Commands/Give.java:148-152`). Coins are handled as an item of their own by `parseBestPossibleGold`.

When several items move, each message is marked MASK_OPTIMIZE (`Commands/Get.java:193`, `Commands/Drop.java:198`; flag at `Common/interfaces/CMMsg.java:964`). The room is then recalculated once after the loop (`Commands/Get.java:205-207`, `Commands/Drop.java:208-210`).

#### 5.3 Failure, rejection and silence

Three different outcomes are reported differently:

- **Failure.** `postCommandFail` sends a MSG_COMMANDFAIL that carries the message to the mob (`Libraries/CommonMsgs.java:137-156`). Typical texts are "Get what?", "Drop what?", "Put what where?" and "You don't seem to be carrying that."
- **Rejection.** When a listener refuses a message, the verb calls `postCommandRejection`, which sends MSG_COMMANDREJECT with no text (`Libraries/CommonMsgs.java:159-178`). The listener is expected to have explained why. Examples are `Commands/Look.java:299`, `Commands/Sleep.java:131` and `Commands/Sit.java:99`.
- **Silence.** Some failures return false with no message. A coin count that is too small returns false silently in `Commands/Drop.java:149-150`, `Commands/Put.java:221-222` and `Commands/Give.java:116-117`.

Unknown words go to `handleUnknownCommand` (`Libraries/CommonMsgs.java:91-115`). It reports "You don't know how to <skill> that." when the word belongs to a skill the mob lacks, and "Huh?" otherwise.

#### 5.4 One verb calls another

Verbs delegate by looking up a prototype by ID and calling `execute` with the same word list, or by calling `doCommand`:

- `Put` hands "put X on Y", "put on X" and "put X on <body location>" to Wear (`Commands/Put.java:117-124`, `:126-140`, `:142-149`). "put out X" is a separate path that sends MSG_EXTINGUISH (`:151-155`, `:54-86`).
- `Gain` adds the word SAY to the front and calls `doCommand` (`Commands/Gain.java:58-59`). See section 6.
- `Go` hands a door or a rideable to Enter (`Commands/Go.java:137-142`, `:168-170`). `Sit` hands a mob to Mount (`Commands/Sit.java:83-88`).
- `Kill` wields a held weapon when nothing is wielded, by removing it and then calling Wield's `executeInternal` (`Commands/Kill.java:199-215`).
- Code that wants a room look issues LOOK, or LOOK UNOBTRUSIVELY when it asks for quiet (`Libraries/CommonMsgs.java:420-425`).

#### 5.5 Movement

`Go.execute` (`Commands/Go.java:83-258`) is the one movement path, and every direction verb is a subclass of it.

- It first runs `standIfNecessary`, which issues STAND IFNECESSARY and reports "You need to wake up first.", "You failed to stand up. You might try crawling." or "You need to stand up." (`:52-80`).
- `out` picks the one exit that leads to an outdoor room (`:100-127`). Two such exits give "Which way out? Try ..." and none gives "There is no direct way out of this place. Try a direction."
- A single resolved direction calls `CMLib.tracking().walk` or `run` (`:155-165`). A walk that fails clears the queue (`:164`).
- Several words, commas or counts are parsed in a loop (`:176-206`). Monsters walk at once (`:208-227`). Players get a list of `GO <direction>` steps queued at the front (`:230-233`, `:255`). Each step then pays its own action cost.
- A door or rideable name becomes ENTER (`:168-170`, `:239-245`).

Direction verbs are one-line subclasses. `Commands/North.java`, `Commands/East.java` and the rest declare their words, for example `NORTH` and `N` in North. Up uses `UP` and `U`, Down uses `DOWN` and `D`, and Above, Below, Left, Right and Back have one word each. The actual moving is done by `MUDTracker.move`, outside this bucket. It uses different leave messages for flight (`Libraries/MUDTracker.java:1703-1732`), and its "You are too tired." check applies only to ordinary moves when movement is zero (`:1785-1790`).

#### 5.6 Quiet flags

Three conventions suppress messages, and each verb uses one of them:

- A trailing word `UNOBTRUSIVELY` (`Commands/Get.java:114`, `Commands/Look.java:61-67`, `Commands/Follow.java:137-142`).
- A trailing word `QUIETLY` (`Commands/Stand.java:59`, `Commands/NoFollow.java:56`, `Commands/Sheath.java:70`, `Commands/Draw.java:89`, and `CommonMsgs.postStand` at `Libraries/CommonMsgs.java:467-476`).
- The metaflag `METAFLAG_QUIETLY` (`Commands/Wear.java:147`, `:160`, `Commands/Wield.java:99`, `:109`).

### 6. Verb mechanics worth knowing

**Flee** (`Commands/Flee.java:49-176`). A player, or a follower, must be in combat to flee (`:62-68`). With no direction given, the verb picks a random open exit, with UP used only when nothing else is open (`:113-131`, roll at `:128`). If the penalty is XP and the mob has a last room, it prefers the way it came (`:88-101`). The penalty comes from the `FLEE` setting (`coffeemud.ini:399`, default `EXPERIENCE`, options at `:386-398`). For that option the loss is `10 + (own level - enemy level) x 5`, with a minimum of 10 (`Flee.java:157-159`), applied through `CMLib.combat().handleCombatLossConsequences` (`:161`, outside this bucket). The enemy receives `round(loss x (1 - hp fraction)) / 4` experience (`:165`, `:171`). This is skipped when both sides hold sessions on the same address (`:166-170`). A badly wounded fleer therefore gives more. Each combatant's ability to act is checked first (`:71-86`), and the XP loss applies only when some combatant can still fight.

**Train** (`Commands/Train.java:38-699`). The cost table comes from the `TRAINCOSTS` ini key, parsed into a map from a `Trainable` enum to an amount and a cost (`:51-66`, `:74-118`). Shipped values are in the table under Content worth mining. Targets are the six base stats (`:255-264`), a class change (`:265-297`), or one of the enum entries (`:299-321`). Requirements are checked in this order: enough of the cost (`:327-333`), a visible teacher who is present (`:335-367`), neither party refusing lessons (`:373-382`), not sleeping or sitting (`:383-397`), not fighting (`:398-407`), and for a class change a teacher of the same base class (`:409-438`). A stat training also needs a teacher whose stat is higher than the student's racial stat (`:440-449`).

The training is then a MSG_TRAIN from teacher to student carrying a detail Environmental with the cost (`:451-472`). Listeners can change the gain through `msg.value()` and the cost through the `COST` stat, and the verb reads both back afterwards (`:465-472`, `:552-558`). The player is then asked "Spend <cost> for <what>? (y/N)?" with No as the default (`:654-678`). With `noprompt`, or no session, it applies at once (`:651-652`). The applying code (`:538-649`) spends the cost (`:559`) and applies the gain with a message such as "You feel more powerful!" (mana), "You feel more rested!" (movement), "You feel more trainable!" (training points) or "You feel even healthier!" (hit points). A class change ends the old class and starts the new one, and records a stats bump (`:639-644`).

**Sleep** (`Commands/Sleep.java:38-221`). Plain `sleep` runs the sleep message and changes state through the room (`:100-106`). `sleep until <condition>` adds a non-uninvokable ExtAbility named AutoWaker whose text is the condition (`:70-98`). The condition names are DAY, NIGHT, DAWN, DUSK, HEALED, RESTED and FULL. The words DAYTIME and NIGHTTIME are accepted as prefixes of day and night (`:51-54`, `:76-89`). The command object is the tickable (`:95`, `:162-220`). Each tick it tests the local time of day, or hit points, mana and movement at maximum (FULL), or hit points at maximum (HEALED), or fatigue at or below zero (RESTED) (`:187-212`). On wake it queues WAKE with a cost of 1, one full tick (`:215`). A second `sleep until` is not handled as intended (see Problems).

**Say** (`Commands/Say.java:117-399`). The second word can address someone in the room: `self`, `noone` or `nobody` for a say-to, a mob by name, or a compass direction when the verb is shouting (`:145-265`, shout to neighbours at `:228-256`). The name must not be a prefix of one of fourteen stop words such as HERE, THE, IS, SITS or BEEN, or it is dropped as an addressee (`:53-68`, `:158-166`). The sentence is built as a tagged string, and other rooms hear "You hear someone <verb> '...' <direction>." (`:336-341`, `:379`). When the addressed mob speaks a language, the verb may swap the spoken language for the exchange (`:292-315`). Player speech then goes through the SAYFILTER and the user-input filter (`:328-332`). The GMCP channel messages are sent to clients that have negotiated it (`:70-115`, `:344`).

**Look** (`Commands/Look.java:55-303`). The argument shapes are `look around` (`:79-83`), `look for [all] X` (`:85-94`), `look to X` (`:96-97`), `look exit(s)` (`:100-110`), `look self` or `look me` (`:111-112`), `look at X in Y` for a container (`:132-152`) and `look in X` (`:158-171`). A direction gives the exit description (`:114-124`, `:186-203`). The item lookup order is: room items without coins, then any item, then open containers in the room, then a direction (`:154-204`). A plain `look` sends MSG_LOOK and adds the exit list as a trailing message when AUTOEXITS is set (`:304-341`). The list form marks items, mobs and exits with `^I`, `^M` and `^D` (`:222-252`).

**Kill** (`Commands/Kill.java:50-221`). In combat, naming the current victim gives "You are already fighting X."; naming someone else retargets and costs the command, with "You are now targeting X." (`:159-186`). A grouped mob avoids its own followers when a name matches several mobs (`:126-143`). Before striking, the verb checks `mayPhysicallyAttack` and asks the room whether protection objects object (`:189-196`). Then it calls `CMLib.combat().postAttack` with the wielded weapon (`:218`).

**Practice and Teach.** Practice needs a teacher in the room who knows the skill (`Commands/Practice.java:65-97`). The skill must be savable, so a native skill is refused with "... cannot be practiced, as it is a native skill." (`:115-119`). The skill's `canBeTaughtBy` and `canBePracticedBy` are asked (`:128-131`), messages are checked (`:132-146`), then `practice(teacher, mob)` runs on the skill (`:147`). Teach finds a student in the room, looks the skill up through the student's stats or the mob's own list, and falls back to expertises (`Commands/Teach.java:64-124`). The actual teaching is in `CMLib.expertises().confirmAndTeach`, outside this bucket.

**Open, Eat, Drink, Wear, Wield.** Opening an exit also opens its pair. `Open.open` sends the message, then runs the same message on the paired exit with `opE.executeMsg` (`Commands/Open.java:81-86`), and the far room is told "<exit> opens." (`:88-95`). Already-open exits produce no message from this method (`:62`). Eating food needs the food in hand unless the mob has no hands, in which case the verb uses MSG_EAT_GROUND (`Commands/Eat.java:101-113`). A partial bite changes the text to "take(s) a bite of" (`:116-132`). Drink takes from a drink in the room, or from a drink inside an open container (`Commands/DrinkCmd.java:64-102`). Wear maps a location word to a body-location index by exact or suffix match, and refuses unknown locations with "You can't wear anything on your '...'" (`Commands/Wear.java:101-126`). Held-only items sort to the end of the list (`:133-142`). Wield tries each item that can be wielded and stops at the first success (`Commands/Wield.java:67-82`).

**Inventory and Score.** Inventory groups coins by currency and sorts them by denomination (`Commands/Inventory.java:58-99`). Items the viewer cannot see are noted as "(stuff you can't see right now)" (`:176`). Score turns each base stat into a word from a 34-entry scale (`resources/lists.ini`, `CHARSTAT_CHART`), or prints the number and maximum when COMBATPROWESS does not select words (`Commands/Score.java:99-124`). A value above the top of the scale gets adverbs from its roman-numeral digits (`:66-97`, see Content worth mining). Score shows "(N lives remaining)" only when PLAYERDEATH includes a PUR entry (`:186-205`). `executeInternal` accepts a leading colon to return one section as text for other code (`:405-436`).

**Gain** (`Commands/Gain.java:50-61`). `gain X` rewrites itself to SAY followed by the same words, then calls `doCommand` (`:58-59`). The spoken text "GAIN X" is read by the MOBTeacher behaviour (`Behaviors/MOBTeacher.java:360-381`), which searches the spoken text for "TEACH" or "GAIN " and treats the rest as the request. A bare `gain` replies "Gain what? Enter QUALIFY to see what you can gain." (`Gain.java:53-56`).

## Content worth mining

**Command words.** The access words of the core verbs and of the movement family, from the declarations. Some entries (Enter, Run, Crawl, Push, Pull, Draw, Empty, Wealth, Worth, NoFollow, Shout, Yell, Ask) are in files outside this bucket:

| Group | Verbs and words |
|---|---|
| Movement | GO WALK; NORTH N, SOUTH S, EAST E, WEST W, NORTHEAST NE, NORTHWEST NW, SOUTHEAST SE, SOUTHWEST SW; UP U, DOWN D; ABOVE, BELOW, LEFT, RIGHT, BACK; FOREWARD FORE, PORTSIDE PORT, STARBOARD STB; ENTER EN, RUN, CRAWL CR, PUSH, PULL DRAG, FLEE |
| Objects | GET G, DRAW, DROP DRO, EMPTY EMP, PUT PU P, GIVE GI, WEAR, WIELD, EAT, DRINK DR DRI, OPEN OP O, LOOK LOO LO L, INVENTORY INV I, WEALTH, WORTH, SCORE SC |
| People and fighting | KILL K ATTACK MURDER FIGHT, FOLLOW FOL FO F, NOFOLLOW NOFOL, SAY, backtick, SA, SAYTO, SHOUT SHOUTTO SHOUTAT, YELL YELLTO YELLAT Y, ASK |
| Learning and rest | TEACH, PRACTICE PRAC, TRAIN TR TRA, GAIN, SLEEP SL, SIT REST R |

**Train costs.** The shipped `TRAINCOSTS` table (`coffeemud.ini:822-823`):

| Key | Gives | Costs |
|---|---|---|
| HITPOINTS | +10 hit points (base, maximum and current) | 1 TRAIN |
| MANA | +20 mana | 1 TRAIN |
| MOVES | +20 movement | 1 TRAIN |
| GAIN | +1 training point | 7 PRACTICE |
| PRACTICES | +5 practice points | 1 TRAIN |

**Scales and adverbs.** `CHARSTAT_CHART` (34 entries) runs disabled, feeble, feeble, inferior ×3, below average ×3, average ×3, above average ×3, good ×3, excellent ×3, exceptional ×3, superior ×3, incredible ×3, fantastical ×3, and ends with ungodly. Values past the top add `EXTREME_ADVS` adverbs by roman digit: I Very, V Extremely, X Incredibly, L Astoundingly, C Stupendously, D Drastically, M Amazingly. The top of the scale plus one step therefore reads "Very ungodly". `lists.ini` and `Commands/Score.java:66-97`.

**Short messages.** Examples that could be adapted: "You feel more powerful!" and "You feel more trainable!" (Train), "Nothing like that catches your eye." (Look, `Commands/Look.java:265`), "You can only train with someone whose score is higher than yours." (Train, `:445`), "You don't know how to <skill> that." (`Libraries/CommonMsgs.java:106`), "You are now targeting X." (Kill, `:183`), "You stop fighting." (Flee, `:105`), and "Unknown until '...', try DAY, NIGHT, DAWN, DUSK, HEALED, RESTED, or FULL" (Sleep, `:86`).

**Flee penalties.** The `FLEE` ini comment lists the possible penalties for fleeing: purge, unlevel, losing a random skill, astral forms, unconsciousness, experience loss (the shipped default), recall to the death room, a flat experience loss, a formula, or none when blank (`coffeemud.ini:386-399`). Only the experience options are computed in this bucket (`Commands/Flee.java:154-171`).

## Notable design ideas

- **Verbs as data-backed prototypes.** A verb is one object with words, cost and rules, and runtime-built verbs use the same route. `Commands/Create.java:1398-1486` makes a GenCommand that can replace a built-in verb, and `Commands/GenCommand.java:141-220` reads its cost, ordering and security from stored fields and registers itself on load.
- **Two-phase messages.** The verb checks the room, and then the room and its contents can refuse or change the message before it is applied. Train shows the power of this: a listener can change the gain and the price in flight (`Commands/Train.java:465-472`, `:552-558`).
- **Action economy.** Each verb has a default cost, and the default and per-verb values live in the config file (`core/CMProps.java:1143-1197`). Zero-cost verbs such as Look are free, and a pool that refills each tick sets the pace.
- **Ability words before command prefixes.** Typing a short prefix tries ability trigger words before command words (`Libraries/EnglishParser.java:1041-1067`). A skill can therefore take a short name from a command without any changes to the command.
- **Command substitution.** `deferCommandCheck` can swap any matched verb for `DeferCmd` (`Libraries/CoffeeLevels.java:1533-1636`), so an experience-deferral rule works on every verb without editing any of them.
- **Multi-step walks as queued steps.** "go n n e" becomes queued steps, each of which pays its own cost (`Commands/Go.java:176-255`).
- **Flight costs the enemy.** Flee gives the opponent experience scaled by how hurt the fleer is (`Commands/Flee.java:165`).
- **Confirm before spending.** Train asks before spending, with No as the default (`Commands/Train.java:654`).
- **Sleep as a condition, not a timer.** `sleep until` stores a condition on an effect that a tick checks (`Commands/Sleep.java:90-98`, `:162-220`).

## Problems and cruft

Each item was confirmed by reading the code unless it says "unverified".

1. **Movement in combat ignores DEFCOMCMDTIME.** `Go.combatActionsCost` passes the non-combat default, `DEFCMDTIME`, as the fallback (`Commands/Go.java:270-276`). A direction costs 0.20 in combat, not the DEFCOMCMDTIME value (1.0, `coffeemud.ini:546`) that the comment at `coffeemud.ini:537-539` says applies "when the player is in combat". Flee inherits this (`Commands/Flee.java:35`). The comment and the code disagree unless an exception is set.
2. **Free verbs are undocumented.** Look, Inventory and Score cost nothing in or out of combat because they do not override the costs and `StdCommand` defaults to 0.0 (`Commands/StdCommand.java:223-233`). The ini comment does not say so.
3. **A second `sleep until` is silently ignored.** `Commands/Sleep.java:90` calls `mob.delAbility(mob.fetchAbility("AutoWaker"))`. `fetchAbility(String)` searches only abilities, clan abilities and racial abilities (`MOBS/StdMOB.java:5083-5099`), and an AutoWaker effect is never stored there, so `delAbility` removes nothing. `addNonUninvokableEffect` then returns early because an effect with that ID is present (`MOBS/StdMOB.java:5237-5242`), so the new condition is dropped. The first condition stays until the mob wakes and the tick removes the old effect (`Commands/Sleep.java:169-184`).
4. **Inventory name mask.** The mask is upper-cased before the test `mask.startsWith("all")` (`Commands/Inventory.java:147-149`). The test is therefore never true, and "all" is prepended again. `inventory all sword` searches for the text "ALL SWORD" (`EnglishParser.fetchFlags` strips only the first ALL, `Libraries/EnglishParser.java:3095`, which strips one leading ALL). Plain `inventory sword` still works.
5. **Prefix ambiguity.** Prefix lookup returns the first Hashtable key that matches (`core/CMClass.java:1498-1507`). WE, GE and WA each match two or more verbs, with no rule for which wins. Section 1.3 lists examples.
6. **Silent coin failures.** `Commands/Drop.java:149-150`, `Commands/Put.java:221-222` and `Commands/Give.java:116-117` return false without a message when the mob has fewer coins than requested.
7. **Train checks before the prompt, not after.** The cost check (`Commands/Train.java:327-333`), the teacher's presence and state (`:335-407`) and the room's veto (`:467`) run before the prompt at `:654`. The applying code spends the cost (`:559`) and applies the gain (`:562-630`) without repeating any of them. Whether `doSpend` guards against an insufficient balance is unverified.
8. **Train comment drift.** The `TRAINCOSTS` comment lists a key called TRAINS and gives a default of "TRAINS 1 7 PRACTICE" (`coffeemud.ini:818-821`). The code's keys are HITPOINTS, MANA, MOVES, GAIN and PRACTICES (`Commands/Train.java:51-60`), and the shipped value uses GAIN (`coffeemud.ini:822`).
9. **Gain depends on text matching.** The MOBTeacher behaviour finds "TEACH" anywhere in speech, as a substring, and "GAIN " with a trailing space (`Behaviors/MOBTeacher.java:379-380`). Speech such as "I can't teach you" also takes the teacher path, unless something earlier in the chain stops it (unverified).
10. **Three quiet conventions.** UNOBTRUSIVELY, QUIETLY and METAFLAG_QUIETLY each serve a different group of verbs (section 5.6).
11. **Repeated code.** The `all` normalisation appears four times (section 5.2). The do-once loop idiom is repeated in Drop, Put and Give. `Commands/Put.java:258-259` calls `recoverRoomStats()` twice. `Commands/Look.java:49-52` (`listItems`) is an unused stub.
12. **Unreachable branches.** `Commands/Look.java:308-311` reports "Examine what?" when the first word starts with E, but no Look access word does, and the only internal caller sends LOOK (`Libraries/CommonMsgs.java:423-425`). `Commands/Score.java:388-391` handles an empty word list, which `findCommand` never passes.
13. **Latent argument bug.** `Commands/Get.java:272` and `Commands/Put.java:277` read `args[1]` where `args[i]` is intended. No declared parameter list puts a Container past index 1, so this has no effect yet.
14. **Double lookup and list mutation.** `enqueCommand` resolves the command (`MOBS/StdMOB.java:2417`) and, for a zero cost, `doCommand(List)` resolves it again (`:2430` then `:2243`). `findCommand` also rewrites the caller's list (`Libraries/EnglishParser.java:988-989`, `:1093`), so the caller sees the rewritten words.
15. **Explicit costs are lost in the queue.** When a queued command reaches the head, its cost is recalculated (`MOBS/StdMOB.java:2174-2177`). A cost passed when enqueueing, such as the WAKE cost of 1 (`Commands/Sleep.java:215`), is replaced if the command waited behind another. The effect on play is unverified.
16. **Shared mutable state on prototypes.** `Go` keeps `stander` and `ifneccvec` as fields (`Commands/Go.java:49-50`, `:58-67`), and `Sleep` keeps `tickStatus` (`Commands/Sleep.java:153`). All mobs share these objects, which is safe only if commands run on one thread (unverified).
17. **Minor.** `StdCommand.executeInternal` calls `getAccessWords().length` (`Commands/StdCommand.java:112`), which throws if a subclass forgets to override `getAccessWords`. `Follow.java:152` compares the upper-cased name with the raw typed text, so the self-check is case-sensitive.
18. **Say addressee rule is broad.** Any typed name that is a prefix of one of the fourteen stop words is dropped as an addressee (`Commands/Say.java:53-68`, `:158-166`). A mob called "He" or "Si" cannot be addressed directly in a plain say.

## Takeaways for a Python SUD

- **Registry and resolution.** Keep one object per verb holding its words, cost and rules. Resolve exact words first, then a unique prefix, and ask the player when a prefix is ambiguous instead of picking at random:

  ```python
  class Ambiguous(Exception): pass

  def resolve(word, verbs):          # verbs: list of Verb, each with .words
      w = word.upper()
      exact = [v for v in verbs if w in v.words]
      if exact:
          return exact[0]
      hits = [v for v in verbs if any(x.startswith(w) for x in v.words)]
      if len(hits) == 1:
          return hits[0]
      if hits:
          raise Ambiguous([v.words[0] for v in hits])
      return None
  ```

- **Costs as data.** A single-player game does not need a per-tick action pool for every keystroke. It can keep a turn counter, a per-verb table such as `{"look": 0, "inventory": 0, "get": 1, "walk": 1}`, and a rule that costs are paid before a verb runs. Keep the zero-cost verbs free, as the original does for Look and Inventory.
- **Keep the two-phase messages.** A `can_X` check that items, rooms and NPCs can each refuse, followed by a `do_X` event, gives locked doors, cursed items and refusing guards for free. It is the strongest idea in this bucket.
- **One noun-phrase helper.** Write a single parser for "all", "N x", "x.2", "from Y" and "to Z", and use it in get, drop, put and give. That removes the four copies.
- **Companions, not followers.** Keep "follow" and "order" only for NPC companions. Drop group-leader rules and the forced-command path.
- **Train with a confirmation, and recheck.** Keep the cost prompt with No as the default, and repeat the checks after the player answers.
- **Sleep until as a world condition.** Store the wake condition as a simple field on the character, and check it each game hour. Do not use one object per sleeper.
- **Flight.** Put the penalty and the reward in one table. Drop the XP-to-opponent rule, since there are no other players, but keep the idea that a badly wounded flight has a cost.
- **Keep the living-world shouting.** The multi-room say (`Commands/Say.java:228-256`, `:357-386`) is a cheap way to make a world feel alive, and it survives the move to a single player.
- **Drop from the design.** Prototype singletons with mutable fields, the Hashtable prefix order, the `WE` style ambiguities, the `UNOBTRUSIVELY` and `QUIETLY` split, and the socials and channels fallback order.

## Coverage

- **Read in full (all 25 files):** `Commands/interfaces/Command.java`, `Commands/StdCommand.java`, `Commands/Go.java`, `Commands/Flee.java`, `Commands/Say.java`, `Commands/Look.java`, `Commands/Get.java`, `Commands/Drop.java`, `Commands/Put.java`, `Commands/Give.java`, `Commands/Wear.java`, `Commands/Wield.java`, `Commands/Kill.java`, `Commands/Sleep.java`, `Commands/Sit.java`, `Commands/Follow.java`, `Commands/Open.java`, `Commands/Eat.java`, `Commands/DrinkCmd.java`, `Commands/Inventory.java`, `Commands/Score.java` (its base `Affect.java` not read), `Commands/Train.java`, `Commands/Practice.java`, `Commands/Teach.java`, `Commands/Gain.java`. Only licence headers and import blocks were skipped.
- **Read for interactions, not part of this bucket:** `Commands/East.java` and `Commands/North.java` (direction pattern), `MOBS/StdMOB.java` (queue, dispatch, cost, tick, effects, fetch functions), `Libraries/EnglishParser.java` (findCommand, parseMaxToGive, fetchFlags), `core/CMClass.java` (command lookup and word map), `core/CMProps.java` (cost getters and setters), `core/CMSecurity.java` (disable checks), `Libraries/CommonMsgs.java` (fail, rejection, forced commands, item moves), `Common/DefaultSession.java` (input loop), `Common/interfaces/CMMsg.java` (flags), `Locales/StdRoom.java` (okMessage and send), `Items/Basic/StdItem.java` (drop and get handling), `Libraries/MUDTracker.java` (flight moves), `Libraries/CoffeeLevels.java` (deferCommandCheck), `Behaviors/MOBTeacher.java` (speech handling), `Commands/GenCommand.java`, `Commands/Create.java` (runtime command creation), `Libraries/DirtyLanguage.java` (word translation and pre-processor).
- **Not read:** `Commands/Affect.java` (Score's base), `Commands/Stand.java`, `Commands/Mount.java`, `Commands/Enter.java`, `Commands/Order.java` (only its check line), combat internals (`Libraries/MUDFight.java`), `LoginLibrary.getTrainingCost`, `ExpertiseLibrary.confirmAndTeach`, `Ability.practice`, `DeferCmd`.
