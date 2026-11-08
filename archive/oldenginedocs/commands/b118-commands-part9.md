# Commands, part 9: Mood to Rebuke

> Part of the CoffeeMud NEWOVERVIEW. Bucket: `b118` (Commands [part 9/12: Mood .. Rebuke]) · 46 files · 7564 lines of Java.

## Summary

This part holds 46 typed verbs whose names run from MOOD to REBUKE. They cover everyday physical actions (PUT, PUSH, PULL, OPEN, READ, POUR, PAY, MOUNT), progression and information (QUALIFY, PRACTICE, QUESTS, POLL), preference switches (channels, sounds, colour, spam, page breaks), social-hierarchy verbs (ORDER, REBUKE, NOFOLLOW, POSE) and staff tools (PURGE, POSSESS, PAUSE, PROXYCTL, POOF, NOPURGE). Most are thin: they check their words, send one `CMMsg` through the room and return. A few have real depth. PUSH and PULL are cancelable multi-tick actions in which helpers' strength adds up. OUTFIT builds starter gear by searching the crafting recipes. NOCHANNEL times a mute with a scripted delay, and ORDER travels as speech, so the target must hear and understand it. For a single-player Python game the reusable parts are the progression listing, the item and count parser, multi-tick actions, timed effects and the gear generator. The session, possession, PvP, hierarchy and telnet verbs can go.

## Key files and types

Paths are relative to the CoffeeMUD folder. Java paths omit the `com/planet_ink/coffee_mud/` prefix, as in the b170 chapter. The base classes are covered in b170; they are listed here only for the lines this part depends on. The other 27 files of this bucket are in the catalogue under "Content worth mining", with their line counts.

| File or class | Role | Lines |
|---|---|---|
| `Commands/Go.java` (base, see b170) | Movement base of PUSH, PULL, PORTSIDE, NORTH, NORTHEAST and NORTHWEST; default direction cost 0.20, divided by 4 under AUTORUN | 283 |
| `Commands/Follow.java` (base, see b170) | Base of NOFOLLOW; `nofollow` and `unfollow` helpers | 210 |
| `Commands/Skills.java` | Base of QUALIFY, POWERS and PRAYERS; ability listing helpers; `pickUniqueFlag` | 589 |
| `Commands/Push.java` | Multi-tick push: helper strength, per-second messages, movement and energy costs | 410 |
| `Commands/Pull.java` | Pull: a near copy of Push, with the walk done first | 403 |
| `Commands/Qualify.java` | QUALIFY: what the character may learn now, by category, domain or class | 590 |
| `Commands/OutFit.java` | OUTFIT: class and race outfits, or level-based metacrafted gear for staff | 392 |
| `Commands/Questwins.java` | QUESTS and QUESTWINS: accepted and won quests, quest detail, DROP | 394 |
| `Commands/Put.java` | PUT: container search, count and ALL, redirects to WEAR and PUT OUT | 305 |
| `Commands/Purge.java` | PURGE: staff destroys items or monsters | 250 |
| `Commands/Possess.java` | POSSESS: staff takes over a monster by moving the session | 248 |
| `Commands/Order.java` | ORDER: sends a command to others as a speech message with a hidden code | 211 |
| `Commands/ProxyCtl.java` | PROXYCTL: control messages to an external proxy, and player transfer | 198 |
| `Commands/NoChannel.java` | NO&lt;channel&gt;: channel mute, optionally timed by a scripted delay | 157 |
| `Commands/Quit.java` | QUIT: confirmation, PK cooldown, logout mask; returns a possessing spirit to its body | 146 |
| `Commands/PlayerKill.java` | PKILL: consensual PvP flag, one-way option, 5-minute cooldown | 116 |
| `Commands/Rebuke.java` | REBUKE: a deity, liege or follower; a rebuked follower stops following | 120 |
| `Commands/Pose.java` | POSE and NOPOSE: a persistent emote shown with the name | 123 |
| `Commands/Poof.java` | POOF: staff editor for the poof and death messages | 123 |
| `Commands/PollCmd.java` | POLL: votes on and reads server polls | 121 |
| `Commands/PreviousCmd.java` | `!`: replays a command from the session history | 122 |
| `Commands/Mood.java` | MOOD: thin wrapper that invokes the Mood ability (`Abilities/Misc/Mood.java:38`, outside this bucket) | 70 |

## How it works

### 1. Four shapes of verb

Most verbs in this part fit one of four shapes.

- **Toggle.** One attribute or account flag is flipped and one line is printed: NOSPAM (`Commands/NoSpam.java:50-70`), NOBATTLESPAM (`Commands/NoBattleSpam.java:50-70`), QUIET (`Commands/Quiet.java:49-69`), NOTEACH (`Commands/NoTeach.java:49-61`), NOSOUNDS (`Commands/NoSounds.java:49-66`) and NOANSI (`Commands/NoANSI.java:49-80`). Several also set telnet modes on the session.
- **Check, message, send.** Validate the words, build a `CMMsg`, ask the room's `okMessage`, then send. PUT, OPEN, READ, PAY, POUR, MOUNT, PRACTICE and REBUKE all follow this pattern, so the room, not the verb, decides the outcome (b170 section 5.1).
- **Listing.** Read-only output: QUALIFY, POWERS, PRAYERS, QUESTS and POLL.
- **Direct edit.** The staff verbs change the world themselves. PURGE destroys, POSSESS moves a session, PAUSE suspends threads, PROXYCTL sends protocol messages and POOF edits player text.

Only PUSH and PULL persist across ticks. Costs follow the b170 scheme. A verb that calls the one-argument getter pays `DEFCMDTIME/100` out of combat (0.20 shipped) and `DEFCOMCMDTIME/100` in combat (1.00 shipped) (`core/CMProps.java:1183-1196`, `coffeemud.ini:545-546`). A verb that does not override its cost is free unless an ini exception applies (`Commands/StdCommand.java:223-233`). Persistent state lives in MOB attributes, `PlayerStats` (poof texts, saved pose, prompt, page break, channel mask) and account flags, never in the command object, which is a shared prototype (b170 section 1.5).

### 2. PUSH and PULL: cancelable multi-tick actions

Both extend Go (`Commands/Push.java:36`, `Commands/Pull.java:36`), so they inherit the direction parser and the busy check `isOccupiedWithOtherWork`. The argument is a `Quad` of target, direction text, direction code and destination (`Push.java:73-146`). `getArgs` reads a trailing direction word. The destination is the room beyond the exit when that exit is open; otherwise it is the mob's own room if the mob floats (`:80-97`). It then finds the target in the room (`:104-112`) and refuses when the target is unseen ("You don't see '@x1' here.", `:116`), when no exit leads that way (`:129`), when the mob is busy (`:136`), or when the mob is fighting and the target is not a mob (`:142`). A siegable item already in combat can be pushed without a direction (`:123-125`).

The verb runs as a process. Once a second `preExecute` is called with `secondsElapsed` (b170 section 3). At second 0 it sends "&lt;S-NAME&gt; start(s) pushing &lt;T-NAME&gt;..." (`Push.java:243-260`). At every eighth second it sends the continue message (`:262-279`). A value of -1 is the stop signal: it sends the stop message and clears the queue (`:231-241`). `canBeCancelled` returns true (`:404-408`). With PULL, these are the only verbs in this part that can be cancelled.

**Helpers.** Another mob with this same verb queued on the same object is a helper (`getHelpers`, `Push.java:148-175`). `getHelpAmounts` adds a helper's strength when it pushes the same way and subtracts it otherwise (`:177-193`). `applyAnyAdjustment` gives the pusher a temporary strength effect for the length of the attempt and removes it in a `finally` (`:195-220`; the effect is built at `:50-71`). A group pushing together adds its strength, and opposed pushers cancel it.

**Cost of a completed push** (`execute`, `Push.java:283-366`). The MSG_PUSH is checked by the source room (`:303`) and, when the push crosses into another room, by that room (`:311`). Movement is debited by `movesReqToPush - maxCarry/3` from the pusher and from each helper (`:319-321`, `:331`). Energy is `round(sqrt(weight))`, never below RUNCOST, paid through `expendEnergy` once per point (`:322-326`). If movement runs out, the push fails with "&lt;S-NAME&gt; fail(s) to push &lt;T-NAME&gt; due to &lt;T-HIS-HER&gt; weight." and the item stays put (`:335-339`). Otherwise the item moves with the Player_Drop expiry and followers (`:341-342`). A pushed mob is walked (`:347`). A rejuvenating NPC that ends away from its start room is marked to wander home after one game hour (`:348-354`).

**Cost formula** (`Push.java:368-396`). With fewer than three words the cost is the default. With a target, `time = movesReqToPush / (maxCarry/3)`. If `time <= 1` the cost is `cost + actions()`; otherwise it is `time * (cost + actions() + 2 * speed)`. The queue stores this number when the verb is queued (`StdMOB.java:2426`, `:2439`) and debits it when `actions() - cost >= 0` (`StdMOB.java:2154-2162`). The debit therefore includes the action pool that was present at queue time (see Problems).

**PULL** (`Commands/Pull.java:282-359`) is the same process with MSG_PULL and the pull messages. The order differs. The puller walks first (`:311`), pays movement and energy (`:313-320`), and only then moves the item into the destination (`:335-336`). Helpers are walked along (`:321-329`). Most of the helper code is duplicated between the two files (`getArgs`, `getStrAdjuster`, `getHelpers`, `getHelpAmounts`, `applyAnyAdjustment`, `clearAdjustment`, `preExecute`).

### 3. QUALIFY and the listing family

QUALIFY (`Commands/Qualify.java:247-583`) is a planner. It changes no state. With no argument it lists everything the character qualifies for now. A category word narrows the list: SKILLS, COMMON SKILLS, CRAFTING SKILLS, EPICUREAN SKILLS, BUILDING SKILLS, GATHERING or NON-CRAFTING SKILLS, THIEVES SKILLS, SPELLS, PRAYERS, POWERS, TECHS, CHANTS, SONGS, LANGUAGES, CLASSES and EXPERTISES (`:263-349`, `:381-508`). Any other word is matched by prefix against the domain descriptions (`:355-374`). A leading UNIQUE drops abilities shared across classes and races (`Skills.java:309-320`, `isUnique` at `:322`).

Eligibility is `ableCheck` (`Qualify.java:150-166`). An ability is listed when all of these hold: its qualifying level is reached (`qualifiesByLevel`); it is not a secret skill; the character does not know it; the filter passes; the special-skill allowance is above zero (`CMLib.ableComponents().getSpecialSkillRemainder`, `:157`); prerequisites are met when the class shows a thin list (`:158`); and any zapper mask on the mapping passes (`:160-162`). The level test is against the character's level for ordinary abilities and against the class level for class-only abilities (`:184-188`). Rows are laid out in two columns, grouped by level (`:196-228`). The output ends with the remaining allowance for common, crafting, non-crafting and language skills (`:545-572`). The classes section shows each class's train cost from `Train.getAvailableCharClasses` (`:394-405`), and the expertise section comes from `CMLib.expertises()` (`:440`). WILLQUALIFY is a separate class that extends Skills, not Qualify (`Commands/WillQualify.java:41`). The check that names it at `Qualify.java:575` therefore looks dead (not traced further).

POWERS and PRAYERS (`Commands/Powers.java:49-66`, `Commands/Prayers.java:49-65`) are listing wrappers over `Skills`: `parsedOutIndividualSkill` (`Skills.java:65-70`), `parseDomainInfo` (`:258`) and `getAbilities` (`:406-432`). Each accepts an individual ability name, a domain or nothing, and prints the character's abilities of that type.

### 4. Quests: QUESTS and QUESTWINS

`Commands/Questwins.java` is one class with several readings. QUESTWINS is QUESTS with the word WON added (`:212-213`). Accepted quests come from `CMLib.quests().getPlayerPersistentQuests`, de-duplicated by name (`:106-126`). Won quests come from `wasWinner` and `whenLastWon` (`:70-91`). A quest is found by number, then display-name prefix, then display-name substring, then internal-name prefix and substring, then the global quest list (`findQuest`, `:140-206`).

Quest detail (`:349-383`) prints the quest's instructions, falling back to the script's INSTRUCTIONS variable and then to "No further information available.". A TIME_REMAINING script variable in ticks is converted with `CMProps.getTickMillis()` and printed as minutes when over 60 seconds (`:364-373`). A PROGRESS variable is printed when present.

DROP (`:222-279`) has an unusual first step. Before it looks at the quest the player named, it scans the mob's scripts for quest scripts whose quest definition no longer exists, calls their DO_DROP function if present, ends them and removes them (`:224-246`). Only then does it find the named quest, ask "Drop the quest '@x1', are you sure (y/N)?" with No as the default (`:271`), and bump the drop statistic (`:273`). Staff with CMDQUESTS or CMDPLAYERS can see another player's won and accepted lists and a quest's winners (`:291-341`).

### 5. OUTFIT: starter gear and metacrafting

OUTFIT (`Commands/OutFit.java:283-368`) has two modes. For the caster, or with no level given, it applies the class outfit and then the race outfit through `CMLib.utensils().outfit` (`:331-342`). With a level and a target, it metacrafts gear at that level (`:293-297`, `:343-353`).

Metacrafting searches recipe libraries rather than inventing items. `metacraftWeaponFor` (`:195-280`) returns nothing if the target already wields something. It picks the weapon class from the class's allowed weapon level (`:206-251`), searches the crafting skills' item sets, and keeps the weapon whose level is closest to, but not above, the target's level (`:263-270`). `metacraftArmorsFor` (`:105-193`) works through each worn location in turn. It takes the armour from the recipe sets for Tailoring, Armorsmithing or Leatherworking (chosen from the class's armour level, `:116-140`), scores each candidate as its armour minus the level gap (`findArmorWinner`, `:64-103`, score at `:93-97`), and fills only locations that are still empty (`:166-179`). Unused candidates are destroyed (`:181-191`, `:268-276`).

Staff can target someone else only with CMDPLAYERS or CMDMOBS (`:304-321`). The success message is "Useful equipment appears mysteriously out of the Java Plane." (`:358`), followed by the equipment list (`:355-357`). OUTFIT is not orderable (`:387-390`).

### 6. PUT and the verbs it hands off

`Commands/Put.java:107-261` is the one verb here that does real parsing. Several word patterns are sent elsewhere first. "put X on", "put on X" and "put X on &lt;body location&gt;" go to WEAR (`:117-124`, `:126-140`, `:142-149`). "put out X" goes to `putout` (`:151-155`). Otherwise the container is found by `parsePossibleContainer` (`:169`), then among the room's favoured items (`:172`), then among any open container in the room (`:177-191`). A missing container gives "I don't see a @x1 here." (`:194`).

Counts and ALL follow the shared rules in b170 section 5.2 (`:198`, `:205-216`, `:229-244`). Coins go through `parseBestPossibleGold` and are silently refused when the purse is short (`:218-222`). Each item is put with MSG_PUT and the MASK_OPTIMIZE flag (`:88-104`). The verb word is "in", or the container's own `putString` if it is a Rideable (`:91`). AutoBundler items bundle after each put (`:101-102`).

`putout` (`:54-86`) sends MSG_EXTINGUISH, but only when exactly one matching item is found or the item is a light (`:79-80`). With several non-light matches it does nothing and says nothing (see Problems).

### 7. ORDER: orders travel as speech

`Commands/Order.java:50-191` lets one person direct visible others. The orderer names a person or ALL (or ALL.x), then the command (`:54-100`). Each target must be visible, able to hear and speak the orderer's language (`:116`, `:174-175`, `canBeHeardSpeakingBy`), and must accept the orderer (`willFollowOrdersOf`, `:122`, `:178`). Without ORDER security, each verb must pass `canBeOrdered` and the orderer's `securityCheck`, and abilities marked FLAG_NOORDERING are refused (`:134-148`, `:154-172`).

The order is sent as speech: a MSG_SPEAK message carrying a MSG_ORDER target code, shown as "&lt;S-NAME&gt; order(s) &lt;T-NAMESELF&gt; to '...'" (`:182-185`). A target's `executeMsg` handles the TYP_ORDER case by queueing the spoken words with METAFLAG_ORDER and cost zero (`MOBS/StdMOB.java:3733-3736`). The queue then refuses an ability that is FLAG_NOORDERING when ordered (`StdMOB.java:2417-2421`). Other abilities also reference MSG_ORDER (for example Spell_Command and Thief_Con); their use is outside this bucket.

### 8. Staff and session tools

- **PURGE** (`Commands/Purge.java:188-236`, security at `:245-248`). `PURGE ITEM <name>` and `PURGE MOB <name>` are explicit forms. A bare name is resolved to an item or a monster in the room (`:210-227`). Players are refused ("@x1 is a PLAYER!!", `:82`). An item is destroyed (`:167`), and a monster is removed with `killMeDead(false)` (`:88`). An `@` suffix limits the search to the room, a named mob or a named container (`:116-145`). ALL and .ALL loop over matches (`:65-75`, `:147-157`).
- **POSSESS** (`Commands/Possess.java:89-202`, security `:244-247`). The target must be a monster in the game (`:171`) and visible or audible to the caster (`:73`). A name may carry a room or area suffix (`name@place`, `:102-116`). Sysops cannot be possessed by non-sysops (`:182-186`). The swap moves the session: the caster's session is handed to the monster, the monster's soulMate is set to the caster, and the caster's session is cleared (`:193-199`). QUIT returns the spirit (`Commands/Quit.java:50-65`). A caster who is already possessing is refused (`:92-95`).
- **PAUSE** (`Commands/Pause.java:58-132`). With no argument it toggles suspension of all threads. The caller's own session is excluded, so the admin can type PAUSE again to resume (`:71-72`). `PAUSE RESUME` resumes (`:82-91`). `PAUSE AREA`, `PAUSE ROOM` or a mob here toggles tick suspension for one object (`:94-117`). A global pause needs `isAllowedEverywhere` (`:67`), and the verb itself needs the PAUSE security (`:129-131`).
- **PROXYCTL** (`Commands/ProxyCtl.java:52-173`, security `:194-197`). Only for a session that has negotiated the MPCP telnet option (`:55`). When the proxy replies, the verb prints the message (`:60-100`), including a two-column table of sessions (`:69-86`). `TRANSFER PLAYER=<name> TARGET=<host>:<port>` asks a local, proxy-connected player's session to move (`:115-160`, checks at `:146-155`). Other words go out as an inline MPCP command with key=value pairs, and upper-case keys are lower-cased (`:161-170`). The password word is optional and falls back to a built-in value (`:110-113`).
- **POOF** (`Commands/Poof.java:56-109`, security `:118-121`). The staff path edits five texts through the generic editor: poof-in, poof-out, transfer-in, transfer-out and death (`:60-93`). The non-staff path, which edits only the death text, is described in a template comment (`:97-103`). It cannot be reached from a typed command, because `securityCheck` requires the same staff group that the non-staff path avoids, and the parser checks that before executing (b170 section 2, `Libraries/EnglishParser.java:995-997`).
- **NOPURGE** (`Commands/NoPurge.java:49-104`, security `:101-104`). Adds a player, account or clan name to the autopurge exemption list with `CMLib.players().noPurge("+name")` (`:59-74`). The `executeInternal` path removes the name when METAFLAG_REVERSED is set (`:83-92`).

### 9. NOCHANNEL: a timed mute built from a MOBPROG delay

The verb has no access words of its own (`Commands/NoChannel.java:41`, `access=null`). It is reached by the NO prefix: the typed word minus its first two letters is the channel name (`:56`). The name is matched exactly and then by prefix. The prefix loop has no break, so the last match wins (`:59-79`). The caster's channel mask must allow the channel (`:80-85`). The clause "FOR &lt;n&gt; &lt;unit&gt;" sets a deadline using `CMLib.english().getMillisMultiplierByName` (`:86-107`). Any earlier timer script for the channel is removed (`:108-116`).

The mute itself is a bit in `PlayerStats.channelMask` (`:119`, `:143`). A timed mute also creates a `DefaultScriptingEngine` with three lines: a delay of N ticks (converted with `CMProps.getTickMillis`), the channel's own toggle command, and a self-deleting MPSCRIPT line (`:120-131`). When the delay ends, the script types the toggle, which turns the channel back on, and removes itself. The timer is therefore a MOBPROG delay, not a scheduler. The player sees "The @x1 channel has been turned off for @x2. Use `@x3` to turn it back on." (`:134-135`).

### 10. PKILL and QUIT: consent, cooldowns and gates

PKILL (`Commands/PlayerKill.java:49-108`) is a consensual PvP flag. The PKILL policy can disable it entirely with ALWAYS or NEVER (`:52-57`). The flag cannot be toggled in combat (`:59-63`), or on land that can be owned, unless the caller is a sysop (`:65-70`). Turning it on asks "Are you absolutely sure (y/N)?" with No as the default (`:97`) and then tells both players that they must both have the flag for sparring (`:104-105`). ONEWAY makes the change permanent (`:74-78`). Turning it off waits five minutes after the last PK fight (`:80-86`). The policy options are documented at `coffeemud.ini:347-353`.

QUIT (`Commands/Quit.java:68-126`) has four gates. A possessing mob returns to its body instead, and a trailing "!" forces that return past the room's veto (`:71-72`). A PK fight in the last five minutes blocks quitting unless the caller is a sysop (`:79-85`). The LOGOUTMASK zapper must match unless the caller is a sysop (`:86-92`; the shipped mask is empty, `coffeemud.ini:812`). A 30-second confirmation defaults to No (`:93`), and on yes the verb sends a global quit message, says "Bye!" and stops the session (`:111-117`).

### 11. Items, money, liquids and mounts

- **PACKAGE** (`Commands/Package.java:49-156`). Packs N identical items into one GenPackagedItems item named after them (`:123`, `:140-150`). Coins, enspelled and burning items are refused (`:115-121`), and every item must be identical (`:126-130`). Items are picked up with `Get.get` (`:135`), and the originals are destroyed after packing (`:148-149`).
- **NOSELL** (`Commands/NoSell.java:50-160`). Marks an item unsellable by adding a `Prop_Unsellable` effect with the text `DROPOFF=true` (`:132-136`). The mark is "temporary" unless the item is generic (`:138-141`). NOSELL OFF removes only effects whose text contains DROPOFF (`:147-156`). Coins are skipped (`:121-122`).
- **POUR** (`Commands/Pour.java:51-148`). Verbs INTO, ONTO and OUT (`:48`). A bare pour goes onto a non-drink and into a drink (`:130-138`). OUT creates a temporary StdDrink with a capacity of 999999 and no content, puts it in the room, and destroys it after the message (`:107-115`, `:145-146`). That is a simple sink for spilled liquid.
- **PAY** (`Commands/Pay.java:58-119`). Coins only: anything else is refused as "You can't pay someone '@x1'?" (`:89`, `:106`). A bare number is converted to the recipient's currency with `abbreviatedPrice` (`:83-84`). Under ORDER or FORCED metaflags, property-protected coins need ORDER security (`:92-98`). The message is a give message with the pay verb (`:123`).
- **OPEN** (`Commands/Open.java:51-109`). Opening an exit also opens its pair, and the far room is told "&lt;exit&gt; opens." (`:81-95`). The message carries the MSP cue "dooropen.wav" (`:54`). An already-open exit gives no message (`:62`).
- **READ** (`Commands/Read.java:51-110`). The last word names the object, and words before it become the text passed to MSG_READ (`:97-106`). The item must be carried, or be an exit (`:53-68`).
- **MOUNT** (`Commands/Mount.java:49-154`). MOUNT, BOARD, RIDE and M. A rider item can be named first (`:77-84`). A room mob can be mounted without its consent only when it is bound, held or a follower ("Only the bound or servants can be mounted unwillingly.", `:95-98`). BOARD on a boardable goes to ENTER (`:121-131`).

### 12. Presentation and preferences

- **POSE** (`Commands/Pose.java:49-103`). The pose is stored as the mob's display text (`:98`) and, for a constant pose, in the saved pose (`:99-100`). "POSE HERE" makes it last only in the current room (`:79-86`). The text passes through the POSEFILTER ini key (`:88`; `coffeemud.ini:1889`). The shipped ini disables the verb (`coffeemud.ini:2079`).
- **PROMPT** (`Commands/Prompt.java:49-77`). Shows the prompt with no argument. Any prefix of DEFAULT resets it to `DEFAULTPROMPT` (`:65-69`). Other text is confirmed with a Y/n prompt (`:70`).
- **PAGEBREAK** (`Commands/PageBreak.java:49-76`). Sets lines per page to a positive number, or disables it by a prefix of "disabled" (`:61-72`).
- **MOOD** (`Commands/Mood.java:50-63`, ability at `Abilities/Misc/Mood.java:1557-1600`). The verb invokes the Mood ability with the words typed. The ability holds one of 21 mood states (`MoodType`, `Abilities/Misc/Mood.java:96-118`), each with an adjective and an adverb. RANDOM rolls over 24 outcomes, and the last three map to NORMAL (`:1581-1586`). FORMAL and POLITE carry a zapper mask on the charisma adjustment (`:98-99`; `Libraries/interfaces/MaskingLibrary.java:389`). The effects of the mood are outside this bucket.
- **PREVIOUS (`!`)** (`Commands/PreviousCmd.java:49-114`). With no argument it replays the newest command. With text, it searches the session history from newest to oldest for a command that starts with the text, then one that ends with it, then one that contains it (`:63-98`). The found command is queued with cost zero, so the normal pipeline runs it (`:108`).
- **POLL** (`Commands/PollCmd.java:49-112`). `Libraries/Polls.java:124-141` sorts the polls into three lists: polls the caller may vote on now, polls held back until login (flag NOTATLOGIN), and results the caller may read but not vote on. The verb votes on the first list, shows results, and prints "Press ENTER to continue" between results (`:72-96`). Monsters are excluded (`:52`).

### 13. Hierarchy: REBUKE and NOFOLLOW

REBUKE (`Commands/Rebuke.java:50-100`) finds its target in four places in order: a room mob, the deity the caster worships (`:60-62`), the caster's liege (`:63-65`) and a follower (`:66-67`). A deity policy can forbid rebuking a deity (`:75-85`). The verb sends MSG_REBUKE (`:88-90`). If the target was following the caster, it stops (`:91-98`). The listeners give the verb its effect: a deity punishes a rebuked follower (`MOBS/StdDeity.java:1149`, `:1189`), and some prayers and skills send MSG_REBUKE to a deity (`Abilities/Prayers/Prayer_Faithless.java:133`, `Abilities/Skills/Skill_Convert.java:246-248`, `:348`).

NOFOLLOW (`Commands/NoFollow.java:50-102`) extends Follow. UNFOLLOW and ALL call `Follow.unfollow` (`Commands/Follow.java:73`). A named follower is removed with `nofollow` (`Follow.java:51`; `NoFollow.java:84`). With no argument it toggles the NOFOLLOW attribute, which means "not accepting new followers or vassals" (`:90-100`).

## Content worth mining

The 46 verbs are grouped by family. Cost notation: **default** is 0.20 out of combat and 1.00 in combat (the one-argument getters). **free** is 0 and 0 (no override, `StdCommand.java:223-233`). **move** is the Go cost, 0.20 out of combat and 0.20 in combat, divided by 4 under AUTORUN (`Go.java:262-281`). "Staff" marks a verb gated by a CMSecurity flag; "player" means open to everyone.

### 1. Movement and physical

| Verb (class) | Words | Syntax | Effect | Cost out / in combat | Restrictions and mark |
|---|---|---|---|---|---|
| North (`North.java`, 76 lines) | NORTH, N | `north` | Walks or runs north | move | Players only in a compass-type direction system (`:68-75`). |
| Northeast (`Northeast.java`, 79) | NORTHEAST, NE | `northeast` | Walks or runs north-east | move | Refused when the world has six or fewer directions (`:70-71`); players only in a compass system. |
| Northwest (`Northwest.java`, 78) | NORTHWEST, NW | `northwest` | Walks or runs north-west | move | As Northeast. |
| Portside (`Portside.java`, 92) | PORTSIDE, PORT | `port [north\|south]` | Ship movement to port: west, or north-west or south-west with a north or south argument | move | Ship direction system for players (`:78-85`); hidden from the command list (`:88-91`). Player. |
| Push (`Push.java`, 410) | PUSH | `push [dir] <thing>` | Pushes an item, exit or mob with helpers; cancelable multi-tick action | default base; formula in section 2 | Busy and combat checks (`:133-144`). Player. |
| Pull (`Pull.java`, 403) | PULL, DRAG | `pull [dir] <thing>` | Pulls an item or mob; the puller walks first | as Push | As Push. Player. |
| Mount (`Mount.java`, 173) | MOUNT, BOARD, RIDE, M | `mount <rider> onto <mount>`, `mount self`, `board <boat>` | Mounts a rideable mob or item; BOARD on a boardable goes to ENTER | default | Unwilling mounting only for bound, held or following mobs (`:95-100`). Player. |

### 2. Items, money and gear

| Verb (class) | Words | Syntax | Effect | Cost out / in combat | Restrictions and mark |
|---|---|---|---|---|---|
| Put (`Put.java`, 305) | PUT, PU, P | `put <items> in <container>`, `put out <item>`, `put <x> on ...` | Puts items into a container; redirects to WEAR; PUT OUT extinguishes | default | Room veto only. Player. |
| Package (`Package.java`, 176) | PACKAGE | `package <items>` | Packs identical items into one package | default | No coins, enspelled or burning items (`:115-121`); identical only (`:126-130`). Player. |
| NoSell (`NoSell.java`, 184) | NOSELL | `nosell [on\|off] <item>` | Toggles an unsellable marker (Prop_Unsellable with DROPOFF) | 0 / 0 unless an ini exception | Coins skipped. Player. |
| Pour (`Pour.java`, 168) | POUR | `pour <source> [into\|onto\|out] [target]` | Moves liquid; OUT empties into a temporary sink | default | Visibility only. Player. |
| Open (`Open.java`, 164) | OPEN, OP, O | `open <exit\|item>` | Opens an exit and its pair, or a container | default | Player. |
| Read (`Read.java`, 137) | READ | `read [words] <item>` | Sends MSG_READ; words before the item are the text | default | Carried items or exits (`:53-68`). Player. |
| Pay (`Pay.java`, 167) | PAY | `pay <who> <amount\|coins\|ALL>` | Gives coins to a visible person | default | Coins only; ORDER or FORCED on property-protected coins needs ORDER security (`:92-98`). Player. |
| OutFit (`OutFit.java`, 392) | OUTFIT | `outfit [level] [who]` | Class and race outfit for self; with a level, metacrafted gear | 0.80 for players, 0 for staff; 4.00 in combat for all (`:371-384`) | Other targets need CMDPLAYERS or CMDMOBS (`:304-321`). Not orderable. Staff for other targets. |

### 3. Skills, progression and information

| Verb (class) | Words | Syntax | Effect | Cost out / in combat | Restrictions and mark |
|---|---|---|---|---|---|
| Qualify (`Qualify.java`, 590) | QUALIFY, QUAL | `qualify [unique] [category\|domain\|name]` | Lists what can be learned now; WILLQUALIFY is the all-levels form | free | Player. |
| Powers (`Powers.java`, 74) | POWERS | `powers [name\|domain]` | Lists super powers the character has | free | Player. |
| Prayers (`Prayers.java`, 73) | PRAYERS | `prayers [name\|domain]` | Lists prayers the character has | free | Player. |
| Practice (`Practice.java`, 170) | PRACTICE, PRAC | `practice [skill] [teacher]` | Trains a skill with a present teacher; no argument shows practice points | default | Teacher must be present and know the skill (`:65-97`); not orderable. Player. |
| Questwins (`Questwins.java`, 394) | QUESTS, QUESTWINS | `quests`, `quests won`, `quests <n\|name>`, `quests drop <name>` | Accepted and won quests; detail; dropping | free | Staff may view another player's lists (`:291-293`). |
| PollCmd (`PollCmd.java`, 121) | POLL | `poll` | Votes on polls and reads results | free | Players only (`:52`); not orderable. |
| NoTeach (`NoTeach.java`, 71) | NOTEACH | `noteach` | Toggles refusing to teach, train or learn | free | Player. |
| Mood (`Mood.java`, 70) | MOOD | `mood [name\|random\|normal]` | Sets the mood state held by the Mood ability | free | Needs the Mood ability; 21 states (section 12). Player. |

### 4. Social and hierarchy

| Verb (class) | Words | Syntax | Effect | Cost out / in combat | Restrictions and mark |
|---|---|---|---|---|---|
| Order (`Order.java`, 211) | ORDER | `order <who\|all> <command>` | Makes visible others carry out a command, as speech (section 7) | default | Targets must hear and understand; orderer needs ORDER security to bypass checks. Staff bypass. |
| Rebuke (`Rebuke.java`, 120) | REBUKE | `rebuke <who>` | Rebukes a deity, liege or follower; a follower stops following | default | Deity policy (`:75-85`); not orderable. Player. |
| NoFollow (`NoFollow.java`, 110) | NOFOLLOW, NOFOL | `nofollow [name\|all\|unfollow] [quietly]` | Toggles accepting followers and vassals; removes named followers | default (Follow's cost) | The quiet word is not removed from the name (see Problems). Player. |
| Pose (`Pose.java`, 123) | POSE, NOPOSE | `pose [here] <text>`, `nopose` | Persistent emote shown with the name | default | Shipped disabled (`coffeemud.ini:2079`); not orderable. Player. |
| Quiet (`Quiet.java`, 78) | QUIET | `quiet [on\|off]` | Stops channel messages and tells | free | Player. |
| PlayerKill (`PlayerKill.java`, 116) | PLAYERKILL, PKILL, PVP | `pkill` | Toggles the consensual PvP flag | free | PKILL policy; not in combat; not on owned land (sysops excepted); 5-minute cooldown to turn off; not orderable. Player. |

### 5. Session, preferences and account

| Verb (class) | Words | Syntax | Effect | Cost out / in combat | Restrictions and mark |
|---|---|---|---|---|---|
| NOMXP (`NOMXP.java`, 96) | NOMXP | `nomxp [quiet]` | Turns MXP off and sends reset codes | free | Disabled by DisFlag.MXP (`:92-95`); not orderable. Player. |
| NoANSI (`NoANSI.java`, 88) | NOANSI, NOCOLOR, NOCOLOUR | `noansi` | Turns ANSI colour off for the account and session | free | Not orderable. Player. |
| NoBattleSpam (`NoBattleSpam.java`, 79) | NOBATTLESPAM | `nobattlespam [on\|off]` | Suppresses combat messages | free | Player. |
| NoSpam (`NoSpam.java`, 79) | NOSPAM | `nospam [on\|off]` | Suppresses "spammy" messages | free | Player. |
| NoSounds (`NoSounds.java`, 79) | NOSOUNDS, NOMSP | `nosounds` | Turns MSP sound and music off | free | Disabled by DisFlag.MSP (`:75-78`). Player. |
| PageBreak (`PageBreak.java`, 86) | PAGEBREAK | `pagebreak <n\|disable>` | Sets lines per page | free | Not orderable. Player. |
| Prompt (`Prompt.java`, 85) | PROMPT | `prompt <text\|default>` | Sets the prompt; DEFAULT restores the default | free | Not orderable. Player. |
| Password (`Password.java`, 163) | PASSWORD | `password` | Three-step prompt: old, new, repeat | free | No security check; not orderable. Player. |
| Quit (`Quit.java`, 146) | QUIT, QUI, Q | `quit [!]` | Confirmed logout; while possessing, returns the spirit, and "!" forces it | default | PK cooldown; LOGOUTMASK; sysops bypass both (`:79-92`). Not orderable. Player. |
| NoChannel (`NoChannel.java`, 157) | NO&lt;channel&gt; (no access words) | `no<channel> [for <n> <unit>]` | Mutes a channel, optionally timed (section 9) | free | Channel mask must allow it. Player. |
| PreviousCmd (`PreviousCmd.java`, 122) | `!` | `!` or `!<text>` | Replays the last command, or the last one matching the text | free | Not orderable. Player. |

### 6. Staff and builder tools

| Verb (class) | Words | Syntax | Effect | Cost out / in combat | Restrictions and mark |
|---|---|---|---|---|---|
| Purge (`Purge.java`, 250) | PURGE | `purge [item\|mob] <name>[@room\|@who\|@container]` | Destroys items or monsters | free | Staff (PURGE security); players refused. |
| Possess (`Possess.java`, 248) | POSSESS, POSS | `possess <name>[@place]` | Moves the session into a monster; QUIT returns | free | Staff (POSSESS); sysops protected. |
| ProxyCtl (`ProxyCtl.java`, 198) | PROXYCTL | `proxyctl [password] <command> [k=v ...]`, `proxyctl transfer player=<name> target=<host>:<port>` | MPCP control of an external proxy | default | Staff (SHUTDOWN everywhere); needs an MPCP session; not orderable. |
| Pause (`Pause.java`, 134) | PAUSE | `pause`, `pause resume`, `pause area\|room\|<mob>` | Suspends all threads, or one object's ticks | free | Staff (PAUSE); global pause needs the everywhere permission. |
| Poof (`Poof.java`, 123) | POOF | `poof` | Edits poof and death messages | free | Staff (SECURITY_GOTO_GROUP). |
| NoPurge (`NoPurge.java`, 106) | NOPURGE | `nopurge <name>` | Exempts a player, account or clan from autopurge | free | Staff (NOPURGE). |

### Other content

- **Mood states.** 21 states, with adjectives and adverbs for each (`Abilities/Misc/Mood.java:96-118`). Several are emotional, a few are sexually suggestive (the names are not reproduced here), and two carry a charisma zapper mask.
- **Quest vocabulary.** The quest-detail fields are instructions, time remaining (ticks shown as minutes or seconds) and progress (`Questwins.java:349-383`).
- **Qualify categories.** The category words in section 3 are a ready-made taxonomy of the skill system: general, common, crafting, gathering, thieving, spells, prayers, powers, techs, chants, songs, languages, classes and expertises.
- **Outfit slots.** OUTFIT walks every worn-location bit, skipping the inventory, held, floating and mouth slots (`OutFit.java:143-158`), so the gear generator is also a slot-coverage algorithm.
- **Command messages.** Short, reusable lines include "You don't seem to have that." (`Read.java:55`), "You may now teach, train, or learn." (`NoTeach.java:55`), "Your spirit has changed bodies, use QUIT to return to yours." (`Possess.java:199`) and "Useful equipment appears mysteriously out of the Java Plane." (`OutFit.java:358`).

## Notable design ideas

- **A verb can be a process.** PUSH and PULL use the command object itself as a per-second callback: a start message on the first second, a heartbeat every eighth second, and a sentinel of -1 for a stop that also clears the queue (`Push.java:223-281`). The verb is cancelable because the queue can call it with the stop sentinel.
- **Group effort is arithmetic, not a special case.** Helpers are simply other mobs with the same queued verb on the same object, and their strength is summed with a sign by direction (`Push.java:177-193`). A group push needs no group object.
- **Gear generation reuses recipes.** OUTFIT asks the crafting skills what they can make, scores each candidate and destroys the rest (`OutFit.java:64-103`). Every generated item is therefore craftable, and the data lives in one place.
- **Timers as data.** NOCHANNEL's timer is a delay-and-act script stored on the mob (`NoChannel.java:117-139`). The same mechanism could express any timed effect.
- **Orders are speech.** An order is a spoken message with a hidden code (`Order.java:182-185`), so the target must hear it and share the language (`:116`). The room hears the order as speech too, because the others code is MSG_SPEAK.
- **Consent for PvP.** Both players must opt in, the opt-in is confirmed with a default of No, and turning it off is locked for five minutes after a fight (`PlayerKill.java:80-105`).
- **Possession by hand-off.** POSSESS moves the session object to the monster and records the caster as soulMate (`Possess.java:193-199`). Nothing of the monster is copied, and QUIT reverses the move.
- **Admin pause that spares the admin.** PAUSE suspends every session except the caller's, so the admin can still type the command to resume (`Pause.java:71-72`).
- **Planner listing.** QUALIFY reports both what is possible and how many slots remain for each skill kind (`Qualify.java:545-572`), which is good design for a progression game.

## Problems and cruft

Items are ordered roughly by how much they matter. "Verified" means read in the code at the cited lines; "unverified" means the behaviour depends on code outside this bucket that was not traced.

1. **NOCHANNEL can crash on a truncated timer.** After removing the words "for", the clause reads `commands.get(i)` past the end of the list (`NoChannel.java:91-97`). "NOGOSSIP FOR" and "NOGOSSIP FOR 5" both reach that read. Whether the caller catches the exception is unverified.
2. **ORDER's AUTOASSIST rule reads backwards.** The check refuses when AUTOASSIST is set (`Order.java:65-71`), but the message says "with your AUTOASSIST flag off". Verified.
3. **Push and Pull cost includes the current pool.** The combat and out-of-combat formulas add `actions()` to the cost (`Push.java:378-380`, `:392-395`). The queue stores the value at queue time and debits it when the pool exceeds it (`StdMOB.java:2154-2162`, `:2426-2439`), so the pool present when the verb was queued is spent as well. This looks unintended. Verified in code; the intent is not documented.
4. **PULL can leave the item behind.** The puller walks first (`Pull.java:311`). The movement check that can fail comes later (`:330-334`), and the item is moved only after it (`:335-336`). A failed pull therefore moves the puller and leaves the item. Verified.
5. **MOUNT dismounts before the check.** The rider's current mount is cleared (`Mount.java:102`) before the MSG_MOUNT is checked (`:149-152`). A refused mount leaves the rider dismounted. Verified.
6. **QUESTS DROP cleans up first.** Stale quest scripts are ended and removed, and their DO_DROP function runs, before the named drop is confirmed or refused (`Questwins.java:224-246`). Verified.
7. **NOFOLLOW's QUIETLY word is not removed.** The quiet flag is detected (`NoFollow.java:56-57`) but the word stays in the name (`:63`). The single-follower branch also ignores the flag (`:84`). So "NOFOLLOW name QUIETLY" searches for a person called "name QUIETLY". Verified.
8. **PUT OUT can silently do nothing.** With several non-light matches, `putout` skips every one and prints nothing (`Put.java:76-84`). Verified.
9. **PURGE ITEM with a container loses the container after the first match.** The first search uses the container (`Purge.java:163`), but the later room search passes null (`:174`). Verified.
10. **POSE can dereference a null PlayerStats.** The guard `pstats != null` is missing when the mob has display text and types POSE with no argument (`Pose.java:52`, `:70-73`). Monsters are the case where `playerStats()` is null. Latent; verified by reading.
11. **POOF's non-staff path is unreachable from a typed command.** Lines `:94-107` run only when `securityCheck` (`:118-121`) has been bypassed, for example by forced execution.
12. **PROXYCTL has a built-in fallback value and a risky loop.** The password falls back to a fixed value when the first word is not numeric (`ProxyCtl.java:110-113`). The key loop changes the map it is iterating (`:164-169`). `MiniJSON.JSONObject` extends LinkedHashMap (`core/MiniJSON.java:395`), so this probably throws ConcurrentModificationException when more than one upper-case key is present. Unverified by running.
13. **PORTSIDE ignores bad input.** Any argument other than north or south is treated as west, with no message (`Portside.java:52-61`). "port east" walks west. Verified.
14. **PROMPT resets on any prefix of DEFAULT.** A single letter "D" resets the prompt (`Prompt.java:65`). Verified.
15. **NOSELL is free unless an exception exists.** The cost compares the default with itself and returns 0 when they match (`NoSell.java:163-175`). Verified. This may be deliberate, but nothing says so.
16. **OUTFIT's cost is four times the default.** `DEFCOMCMDTIME/25` and `DEFCMDTIME/25` give 4.00 in combat and 0.80 out of combat (`OutFit.java:371-384`). Verified arithmetic.
17. **PASSWORD checks the old password last.** The new password is typed twice before the old one is compared (`Password.java:127-140`). The file shows no attempt limit (unverified elsewhere).
18. **NORTHEAST and NORTHWEST have a direction-count guard that NORTH lacks.** `Northeast.java:70-71` and `Northwest.java:70-71` refuse when there are six or fewer directions; `North.java:68-75` does not. Verified.
19. **Duplicated code.** The PUSH and PULL helper set is duplicated (`Push.java:50-220` against `Pull.java:50-220`). NOSPAM and NOBATTLESPAM have the same logic (`NoSpam.java:50-70`, `NoBattleSpam.java:50-70`). POSSESS's `execute` repeats `possess` (`Possess.java:136-200` against `:204-227`).
20. **Dead or odd code.** `errorOut` is defined and never called in `Purge.java:49`, `Pause.java:51` and `Poof.java:49`. PAGEBREAK's initial value is always overwritten (`PageBreak.java:62`). PULL divides weight by 1 (`Pull.java:316`). PUT and PACKAGE both repeat `recoverRoomStats` (`Put.java:258-259`, `Package.java:151-152`). PRACTICE repeats the same ability lookup (`Practice.java:99`, `:121`). `Put.executeInternal` casts `args[1]` where it means `args[i]` (`Put.java:277`). PACKAGE passes an argument its string does not use (`Package.java:106`). REBUKE repeats one argument (`Rebuke.java:71`). NOCHANNEL's script tag uses the object's default string (`NoChannel.java:111`, `:124`); StdCommand does not override `toString`. `Qualify.java:575` tests for the ID WillQualify, but that class does not extend Qualify, so the test is likely dead.
21. **NOCHANNEL's prefix match takes the last channel.** The loop does not break (`NoChannel.java:70-78`), so an ambiguous prefix silently picks the last match.
22. **OUTFIT rebuilds recipe sets on every call.** `craftAllItemSets` runs for each skill and each call, and most results are destroyed (`OutFit.java:254-277`). It is admin-only, so the cost is acceptable, but it is wasteful.

## Takeaways for a Python SUD

**Borrow.**

- **A verb registry with data-driven costs.** Model a verb as a small object with words, an optional cost and a `can_run(actor)` predicate. Keep the costs in a data file, with per-verb overrides, as CoffeeMud does with DEFCMDTIME exceptions. Default to free, and reserve a standard cost for verbs that take time:

  ```python
  @dataclass
  class Verb:
      words: tuple[str, ...]
      run: Callable[..., None]
      cost: float = 0.0          # 0 = free, like StdCommand's default
      orderable: bool = False
      def can_run(self, actor) -> bool:
          return True
  ```

- **Multi-tick actions as generators.** Write PUSH and PULL as generators that yield once per tick, with the start message on the first yield and a heartbeat every eighth tick. Cancelling is `gen.close()`, and the stop message runs in a `finally`. This keeps the cancel logic in one place.
- **Helpers as a sum.** If companions exist, the group push becomes `sum(s for helper in same_way) - sum(s for helper in opposed)`, with no group object.
- **A progression planner.** Port `ableCheck` as a pure function: `[a for a in catalogue if a.level <= actor.level and a.id not in actor.known and prereqs_met(actor, a)]`, plus a WILLQUALIFY variant that drops the level test. Show the remaining slots per category, as QUALIFY does.
- **Shared quantity parsing.** One function should handle "3 sword", "all", "all.sword" and "sword.2" for every item verb (GET, DROP, PUT, GIVE, PACKAGE, NOSELL). Return the count, the rest of the phrase and the match index. CoffeeMud repeats this logic in Get, Drop, Put, Give, Package and NoSell (b170 section 5.2; `Package.java:62`, `NoSell.java:79`).
- **Outfit generation from recipes.** A starter-kit function `outfit_for(char, recipes)` can choose, per slot, the best recipe result by `armour - max(0, char.level - item.level)`. It should create candidates without side effects, so nothing needs destroying afterwards.
- **Timed effects as scheduled events.** Replace MOBPROG delays with a heap of `(expires_at_tick, callback)`. NOCHANNEL's mute becomes `mutes[channel] = expires_at_tick`, and the callback restores it.
- **Liquid and money as values.** Pour as a volume transfer with an infinite sink for spills. Money as integers in one currency, with an abbreviation function for entered amounts.
- **Quests as objects.** A quest with instructions, time remaining and progress, and a DROP that asks first (default No).

**Simplify.**

- Use one toggle helper for the preference verbs. Most of section 5 of the catalogue is a boolean attribute with two messages.
- Keep one source of quest state. The detail code reads script variables, then quest-object fields, then defaults (`Questwins.java:353-359`); a single record would replace all three.
- Turn the weight and energy formulas into one function of weight and strength, and keep the constants in data.

**Drop.**

- Multiplayer and session verbs: ORDER (or keep it for companions only), POSSESS, PROXYCTL, PKILL, REBUKE as a deity system, NOFOLLOW and its vassals, POLL, NOPURGE, POOF and PAUSE (use a turn loop, not threads).
- Telnet and terminal toggles: NOMXP, NOANSI, NOSOUNDS, PAGEBREAK, PROMPT (keep a `prompt` setting if the UI needs one).
- Keep PREVIOUS (`!`) as a history recall in the input layer, since it is cheap and useful.

**Multiplayer assumptions to rethink.** ORDER and REBUKE assume other people have their own queues and rules. PKILL assumes two consenting players. QUIT broadcasts a global message. A single-player game has one queue, one session and one set of rules, so these verbs can be replaced by simpler state.

## Coverage

- **Read in full (all 46 files).** The files were read in full apart from the package and import block at the top (roughly lines 1-17), which holds only imports and the licence header and was skipped in most files. The 46 files are the ones in the bucket manifest. Largest first: Qualify, Push, Pull, Questwins, OutFit, Put, Purge, Possess, Order, ProxyCtl, NoSell, Package, Mount, Practice, Pour, Pay, Open, Password, NoChannel, Quit, Read, Pause, Poof, Pose, PreviousCmd, PollCmd, Rebuke, PlayerKill, NoFollow, NoPurge, NOMXP, Portside, NoANSI, PageBreak, Prompt, NoBattleSpam, NoSounds, NoSpam, Northeast, Northwest, Quiet, North, Powers, Prayers, NoTeach, Mood.
- **Skimmed, outside the bucket.** `Commands/Skills.java` (lines 65-70, 258, 309-322, 406-432); `Commands/Follow.java` (51, 73, 199-208); `Commands/Go.java` (262-281); `Commands/StdCommand.java` (223-233); `MOBS/StdMOB.java` (2154-2162, 2417-2439, 3733-3736); `core/CMProps.java` (1143-1196); `Libraries/Polls.java` (124-141); `core/MiniJSON.java` (395); `Abilities/Misc/Mood.java` (38, 96-118, 1557-1600); `Libraries/interfaces/MaskingLibrary.java` (389); `Commands/WillQualify.java` (41); `MOBS/StdDeity.java` (1149, 1189); `Abilities/Prayers/Prayer_Faithless.java` (133); `Abilities/Skills/Skill_Convert.java` (246-248, 348); the ini keys at `coffeemud.ini` 347-353, 399, 545-546, 812, 1889, 2079.
- **Not read.** The Mood ability body beyond the entry point and enum; the quest library; `CMLib.utensils().outfit`; the crafting library's recipe sets; `Train.getAvailableCharClasses`; `ExpertiseLibrary`; the dice library; the zapper implementation beyond the ADJCHARISMA mask; `MUDTracker` walking; the message listeners on the room and mob side for PUSH and PULL.
