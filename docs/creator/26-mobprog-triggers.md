# MOBPROG triggers reference

A trigger is the first line of a MOBPROG script block: it names the event the block answers (`GREET_PROG`, `SKILL_PROG`, `VERB_PROG`...) and says which occurrences of that event count. This chapter is the complete reference for the 116 triggers that CoffeeSUD's scripting engine recognises, the ones inherited from CoffeeMud and the ones CoffeeSUD adds. It opens with how triggers work (the trigger line, argument forms, hosts, timing, order, the `$` bindings, cancelling, world-wide hearing), then gives a by-purpose index, then one entry per trigger with its syntax, arguments, when it fires, hosts, bindings and an example. It is for scripters and world creators; the engine notes at the end of some entries are for developers.

The language around triggers (control flow, variables, `LOAD=`, files, debugging in general) is in [MOBPROG scripting](04-mobprog.md). Commands are in [MOBPROG commands](27-mobprog-commands.md), functions and the `$` codes in [MOBPROG functions](28-mobprog-functions.md). Zapper masks, which many triggers take, are in [Behaviors and masks](24-behaviors-and-masks.md).

Source: [DefaultScriptingEngine](../../CoffeeMud/com/planet_ink/coffee_mud/Common/DefaultScriptingEngine.java) (the trigger table is `ScriptingEngine.progs` in [ScriptingEngine](../../CoffeeMud/com/planet_ink/coffee_mud/Common/interfaces/ScriptingEngine.java); message dispatch is `okMessage`/`executeMsg`, ticks are `tick`), [ScriptEvents](../../CoffeeMud/com/planet_ink/coffee_mud/core/ScriptEvents.java) (the events that are not messages), [Scriptable](../../CoffeeMud/com/planet_ink/coffee_mud/Behaviors/Scriptable.java) (the behaviour that carries scripts). The shipped help is `HELP SCRIPTABLE`.

## In this chapter

- [How triggers work](#how-triggers-work): [the trigger line](#the-trigger-line), [argument forms](#argument-forms), [hosts](#hosts), [busy hosts](#busy-hosts), [when a trigger runs](#when-a-trigger-runs), [order and multiple programs](#order-and-multiple-programs), [what the codes mean](#what-the-codes-mean-in-a-trigger), [cancelling](#cancelling-events), [world-wide triggers](#world-wide-and-registered-triggers), [controlling triggers](#controlling-triggers), [message codes](#message-codes), [trying a trigger](#trying-and-debugging-a-trigger), [what a save keeps](#what-a-save-keeps)
- [Choosing a trigger](#choosing-a-trigger)
- [Index of triggers by purpose](#index-of-triggers-by-purpose)
- Reference entries, in groups: [Coming and going](#coming-and-going), [Speech, commands and dialogue](#speech-commands-and-dialogue), [Things: items, containers, shops and puzzles](#things-items-containers-shops-and-puzzles), [Combat and encounters](#combat-and-encounters), [Time and the calendar](#time-and-the-calendar), [Quests and achievements](#quests-and-achievements), [Characters](#characters-login-levels-skills-factions-clans-and-the-party), [Game, world and generation](#game-world-and-generation), [Riding, vessels and journeys](#riding-vessels-and-journeys), [Raw messages and script plumbing](#raw-messages-and-script-plumbing)
- [Worked examples](#worked-examples)
- [Troubleshooting](#troubleshooting)
- [See also](#see-also)

## How triggers work

### The trigger line

A script is a list of blocks. Each block starts with a trigger line and ends with a `~` on a line of its own:

```text
TRIGGER_NAME argument argument ...
command
command
~
```

- The trigger name is the first word of the block. It must be spelled exactly as in this chapter, `_PROG` included (`GREET_PROG`, not `GREET`), in any letter case. The `SCRIPT` command also accepts the name without `_PROG` (`SCRIPT FIRE baker TIME`); `MPNOTRIGGER` needs the full name. A block whose first word is not a trigger is logged as `BAD_TRIGGER` when the engine first ticks it and dropped; `SCRIPTCHECK` finds these without running anything.
- The arguments are the rest of the line. Most engines upper-case them before matching, so matching is case-insensitive. Text you test with `$g` is therefore often upper case; use `$g` (lower case) or `$G` (as stored) accordingly.
- The block is the commands up to `~`. A script in a file has one command per line; a script typed into a behaviour's parameters ends each line with `;`. The details of files, `LOAD=` and quoting are in [MOBPROG scripting](04-mobprog.md).
- A block whose trigger takes no arguments (`ONCE_PROG`, `DEATH_PROG`, `PARTYDEFEAT_PROG`) still needs the line.
- An argument is required, or the block never matches, for the CoffeeSUD "event plus subject" triggers: `QUEST_PROG`, `GAME_PROG`, `PARTY_PROG`, `COMBAT_PROG`, `AFFECT_PROG`, `SKILL_PROG`, `DISCOVER_PROG`, `SAIL_PROG`, `VAR_PROG`, `CLAN_PROG`, `CRAFT_PROG`, `RECIPE_PROG`, `FACTION_PROG`. Their entries say so.

### Argument forms

The triggers fall into a few families by the shape of their arguments.

**Chance or mask** (`GREET_PROG`, `ALL_GREET_PROG`, `GROUP_GREET_PROG`, `ARRIVE_PROG`, `ENTRY_PROG`, `EXIT_PROG`, `FIGHT_PROG`, `LOGIN_PROG`, `LOGOFF_PROG`, `LEVEL_PROG`, `AREA_ENTER_PROG`, `AREA_LEAVE_PROG`, `FIRSTGREET_PROG`, `REGREET_PROG`, `GENERATED_PROG`):

- No argument: always.
- A number: that percent chance (`GREET_PROG 25`). The roll is 1 to 100 and the script runs if it is at or under the number, so 100 is always.
- `ALL`: always.
- Anything else is a zapper mask tested on the creature the event is about, usually the one who entered (`GREET_PROG -RACE +Elf`). A mask can also be written after `P` (`GREET_PROG P -RACE +Elf`). The mask language is in [Behaviors and masks](24-behaviors-and-masks.md).

**Keyword list for a thing** (`GIVE_PROG`, `GET_PROG`, `DROP_PROG`, `PUT_PROG`, `WEAR_PROG`, `REMOVE_PROG`, `CONSUME_PROG`, `BUY_PROG`, `SELL_PROG`, `LOOK_PROG`, `OPEN_PROG` and their kin, `CAST_PROG`, `RIDE_PROG`, and the `-ING` forms):

- No argument or `ALL`: any thing.
- Words: the trigger fires if any one word is a whole word of the thing's name (`Name()`, not its keywords or display text), or equals the thing's class ID. `GIVE_PROG apple banana` fires for "a red apple pie". Words are matched without regard to case.
- Quote a multi-word item so it stays together: `GIVE_PROG 'silver key'`. The quotes are the single quote `'` (or the double quote).
- `P name`: the thing's whole name must equal the phrase (or its ID must). `GIVE_PROG P a silver key`.
- Words that contain a `$` code are expanded before matching.
- `$g` receives the word that matched (lower case), `all` for `ALL`, `p` for a `P` phrase.

**Words said or seen** (`SPEECH_PROG`, `SPEAK_PROG`, `CHANNEL_PROG`, `ACT_PROG`, `MASK_PROG`, `IMASK_PROG`, `TALK_PROG`, `CMDFAIL_PROG`):

- Words: any of them is a whole word of the text, case-insensitive. Punctuation is stripped from the text and the words for `ACT_PROG`, `MASK_PROG` and `IMASK_PROG`. For speech (`SPEECH_PROG`, `SPEAK_PROG`, `CHANNEL_PROG`) it is not: `tower` does not match `tower?`, so list both or use a `P` phrase.
- `P phrase` (or `P` before a quoted phrase): the whole text must equal the phrase.
- `ALL` or nothing: any text.
- Variables and codes cannot be used in the words.
- With several words, the script runs once for the first word found, unless the engine is set `MULTITRIGGER`, in which case it can run again for each word.

**Event plus subject** (the CoffeeSUD triggers):

- The first word is a kind of event from a fixed list, often with `ANY` or `ALL` for every kind; the next is the subject (a quest, a skill, a clan); an optional last part is a detail or a zapper mask. Names are matched without regard to case. `*` or `ALL` stands for any subject.
- A subject with spaces needs quotes only where its bits are split by single words (for example `QUEST_PROG`'s quest name).

**Number lists and ranges** (`TIME_PROG`, `DAY_PROG`, `WEEKDAY_PROG`, `MONTH_PROG`): numbers, ranges `6-18`, steps `6-18/3`, and named values.

### Hosts

Any object that can carry a script is a host: a mob, an item, a room, an area or an exit. A script is attached with the `Scriptable` behaviour (on mobs, items and rooms), with the `SCRIPT` field of an object in a world file (areas and exits, and any other), with the `MPSCRIPT` command at run time, or, for the whole game, by a file in `world/scripts/*.script`, which is carried by one hidden item named WORLD that goes wherever the player goes. See [MOBPROG scripting](04-mobprog.md).

What a host hears depends on the trigger. Each entry's **Hosts** line says which. In general:

- A **message-driven** trigger is heard by whatever the message reaches: the room, the items on its floor, the mobs in it and what they carry, and the area (and its parent areas). A scripted item in a mob's pack hears what the mob's room hears. A mob hears a message about itself.
- A trigger that is about **the host itself** (`ARRIVE_PROG`, `ENTRY_PROG` on a mob, `GETTING_PROG`, `DEATH_PROG` without `@GLOBAL`) works only for hosts that can act or die: usually mobs.
- A trigger fired by the engine from outside (`SKILL_PROG`, `DISCOVER_PROG`, the calendar and weather triggers) names its audience in its entry: usually the room and area around the event, sometimes every host that has the trigger, anywhere.
- A non-mob host has no body. The engine makes a stand-in mob named after it so that commands such as `say` and `mpecho` have a speaker; in the `$` codes `$i` is that stand-in's name, and an object reference to the stand-in (the `$n` of a self-triggered event, for instance) means the host itself.

### Busy hosts

Most mob-hosted message triggers (the greetings, speech, the give/get/drop family, buying and selling, mounting, and similar) run only while the mob is **free**: able to act, not in combat, not following someone (a player's follower is exempt), and with at least half of its hit points. A sleeping, bound, fighting or badly hurt mob does not respond. The same test guards the event triggers that are queued for a mob (`SKILL_PROG`, `QUEST_PROG`, the calendar triggers and the like). `MPSETINTERNAL ACTIVETRIGGER TRUE` relaxes it to "able to act". The tick triggers (`RAND_PROG` and friends), `DEATH_PROG`, `KILL_PROG`, `DAMAGE_PROG`, `FIGHT_PROG`, `HITPRCNT_PROG`, `ACT_PROG` and the other message-mask triggers, `CNCLMSG_PROG`, `EXECMSG_PROG`, `SOCIAL_PROG`, and the event triggers that run at once (`TALK_PROG`, `PHASE_PROG`, `DESTROY_PROG` and so on) do not check it. Non-mob hosts are never busy.

### When a trigger runs

There are four kinds of trigger:

| Kind | How it fires | Examples |
| --- | --- | --- |
| Message | The engine hears a game message in the host's reach and tests the trigger line. | `GREET_PROG`, `SPEECH_PROG`, `GET_PROG`, `EXECMSG_PROG` |
| Tick | The engine ticks the host and tests the trigger. | `RAND_PROG`, `DELAY_PROG`, `ONCE_PROG`, `TIME_PROG`, `FIGHT_PROG` |
| Event | Game code reports something that is not a message (a skill learned, a quest started) through `ScriptEvents`. Nothing is looked for until some script in the world has the trigger. | `SKILL_PROG`, `QUEST_PROG`, `WEATHER_PROG`, `GAME_PROG` |
| Called | A script or a command calls it by name. | `FUNCTION_PROG`, `SIGNAL_PROG` |

There are three timings, shown in the index:

- **before**: the trigger runs while the thing is still being decided, so `RETURN CANCEL` can stop it (`CNCLMSG_PROG`, `CMDFAIL_PROG`, `READ_PROG`, `VERB_PROG`, the `SAVE` kind of `GAME_PROG`).
- **now**: it runs at once in the middle of the event (`DEATH_PROG`, `DAMAGE_PROG`, `TALK_PROG`, `USE_PROG`...).
- **queued**: it is put on the host's queue and runs on the host's next tick. This is the usual case, and it means the deed is already done: the item is in the mob's hands, the player has left. With `MPSETINTERNAL NODELAY TRUE` (or the `SCRIPTABLEDELAY` disable flag) queued scripts run at once. In turn-based time, replies to a player (`SPEECH_PROG`, `GIVE_PROG`, `BRIBE_PROG`, `SOCIAL_PROG`, `TALK_PROG`) are run before the player's next prompt, without the clock moving.
- Tick triggers run on the tick (**tick** in the index).

The engine caps its queue: when more than five responses wait it drops identical ones, and if more than 25 wait (150 for an area) it empties the queue and logs an error. A trigger caused by a script's own commands more than about three levels deep inside one engine is ignored, which stops two scripts from setting each other off forever.

Tick triggers need the host to be ticked. The engine itself does not hold back in a passive area or an empty room unless it is set `MPSETINTERNAL PASSIVE FALSE` (skip passive areas) or `NEEDPC TRUE` (run only while a player character is in the host's room), but a mob in an area with no player near it is normally not ticked at all, so its tick triggers wait until the area wakes. `EVERY_PROG ... CATCHUP` reports what it missed.

### Order and multiple programs

A host may have several script engines (several `Scriptable` behaviours, several scripts), and each engine holds a list of blocks in the order written: the engine's own text first, with each `LOAD=` file's blocks in the position of the `LOAD=`.

- For a **message trigger**, an engine looks down its blocks in order and, after the first block that fires, stops: later blocks, even of another trigger, do not run for that message. That is why `BRIBE_PROG` blocks are listed from the largest sum down, and why `GIVE_PROG all` goes last. With `MPSETINTERNAL MULTITRIGGER TRUE` the engine runs every block that fits.
- `CNCLMSG_PROG` and `CMDFAIL_PROG` are the exception: every fitting block runs, in order, until one returns `CANCEL`.
- For **tick triggers** every block of the engine is tested each tick.
- For **event triggers** every fitting block runs, except where an entry says the first one wins (`VERB_PROG`, `USE_PROG`, `COMBINE_PROG`).
- Different hosts hear the same message independently: the room, the mob and the item each run their own blocks.

### What the codes mean in a trigger

When a block runs, the engine fills in the codes the commands use. The trigger decides who is who:

| Code | Holds | Usual value |
| --- | --- | --- |
| `$n` | the source | the creature that did it or whom it happened to (the one entering, speaking, giving) |
| `$t` | the target | the creature or thing it was done to; often the host itself when nothing else is |
| `$i` | the host as a mob | the mob's name, or the stand-in's for an item, room or area |
| `$o` | the primary item | the thing involved: the item given, taken, dropped, read; the host itself if it is an item and nothing else is |
| `$p` | the secondary item | a second thing: the weapon, the item put into a container; else the host item |
| `$g` | a text | the matched word, the words said, a room ID, a count (lower case); `$G` is the same as stored |
| `$0` to `$9` | extra values | the event's own details (the old weather, the tier, the phase) |

The entries give the meaning of each code per trigger. The full list of `$` codes (`$r` a random player, `$m` his or her, `$e`, `$a` the area name and more) is in [MOBPROG functions](28-mobprog-functions.md). `CNCLMSG_PROG` puts the message object in `$0` for JavaScript and exposes it to scripts through `MSG()` and `MPMSG`.

### Cancelling events

Only a few triggers can stop what is happening. In each, `RETURN CANCEL` is the last thing the block does:

- `CNCLMSG_PROG`: any message about to happen (see [Message codes](#message-codes)). The usual way to forbid or to replace an action.
- `CMDFAIL_PROG`: keeps the "Huh?" (or other failure) message back, so the script can answer the odd command in its own way.
- `READ_PROG`: keeps the thing's own reading text back.
- `VERB_PROG`: does the opposite. Taking the line is the default; `RETURN CONTINUE` hands the line back to the normal commands.
- `GAME_PROG SAVE`: refuses the save.

All other triggers run after the event (queued) or as it happens (now) and cannot stop it. To stop a deed, use `CNCLMSG_PROG` with the right message code.

### World-wide and registered triggers

Some triggers are heard by every host that has them, wherever it is. When a script is parsed the engine registers its host for each trigger it holds; an event then goes to the registered hosts that care. After a restore the scripts load again and register again, so these triggers work in a restored game. The triggers heard this way are: `QUEST_PROG`, `QUESTFAIL_PROG`, `GAME_PROG`, `TURN_PROG` (in the leader's area and the world host), `PARTY_PROG`, `ACHIEVE_PROG`, `VAR_PROG`, `CLAN_PROG` (members' and clan's rooms), `AREA_ENTER_PROG` and `AREA_LEAVE_PROG` (hosts in the area), `LOGIN_PROG`, `LOGOFF_PROG`, `LEVEL_PROG`, `CHANNEL_PROG`, and the calendar triggers (hosts in areas on the clock).

A few message-like triggers hear their event anywhere if their line starts with `@GLOBAL` as its first argument:

```text
DEATH_PROG @GLOBAL -NAME +Bandit
KILL_PROG @GLOBAL
EXECMSG_PROG @GLOBAL DEATH ALL
SKILL_PROG @GLOBAL LEARN Skill_Climb
FACTION_PROG @GLOBAL alignment DOWN
JOURNEY_PROG @GLOBAL ARRIVE
```

Without `@GLOBAL` they hear only what happens near the host. With it they hear it once, even in the room where it happens. `EXECMSG_PROG @GLOBAL` works for the messages the game sends world-wide (`DEATH`, `LEVEL`, `LOGIN`, `QUIT`, `LEGALWARRANT`, `LEGALSTATE`, `WINQUEST`, `ENDQUEST`); the hosts are registered when the script is parsed. For `DEATH_PROG` and `KILL_PROG` the text after `@GLOBAL` is a zapper mask on the dead (for `DEATH_PROG`) or the killer (for `KILL_PROG`).

The scripts in `world/scripts/*.script` run on the WORLD host. It hears world-wide triggers like any other host and also ticks everywhere, so it is the natural home for `GAME_PROG`, `QUEST_PROG`, `TURN_PROG`, `EVERY_PROG` and the like that belong to no place.

### Controlling triggers

- `MPNOTRIGGER trigger-name milliseconds` (a command, see [MOBPROG commands](27-mobprog-commands.md)) turns one trigger off for that host for the time, and empties its queued responses. It affects the trigger name, not one block; among the `-ING` triggers, `GETTING_PROG`, `WEARING_PROG` and `REMOVING_PROG` stop for their own name or their partner's (`GET_PROG`, `WEAR_PROG`, `REMOVE_PROG`), and `DROPPING_PROG` only for `DROP_PROG`'s.
- `SCRIPT DISABLE target [trigger]` and `SCRIPT ENABLE target [trigger]` do the same from the command line for as long as you like.
- `MPSETINTERNAL` changes how an engine behaves; the keys that concern triggers:

| Key | Effect |
| --- | --- |
| `MULTITRIGGER TRUE` | run every fitting block for a message, not only the first |
| `ACTIVETRIGGER TRUE` | mobs are not "busy" unless they cannot act at all |
| `NODELAY TRUE` | queued responses run at once |
| `PASSIVE FALSE` | do not run tick triggers in areas without players |
| `NEEDPC TRUE` | run tick triggers only while a player character is in the host's room |
| `TRACE TRUE` | show the host's script run to the builder |

### Message codes

`EXECMSG_PROG` and `CNCLMSG_PROG` take a **message code** as their first argument (after `@GLOBAL`). The names are those of `LIST MSGTYPES` (types such as `GET`, `DEATH`, `SPEAK`, `HUH`) and `LIST MSGMASKS` (masks such as `MAGIC`). Messages carry three codes, one for the source, one for the target and one for the others. The forms are:

| Form | Matches |
| --- | --- |
| `GET` | a message whose source, target or others code is that type |
| `ST=DEATH` | the source and the target codes are DEATH (the letters `S`, `T`, `O` choose which codes; the others code is then ignored) |
| `<S=GET`, `>S=MAGIC` | `<` tests only the minor type; `>` tests only the major mask; `?` tests both (the default) |
| `>MAGIC`, `<GET`, `?GET` | the same, applied to all three codes when there is no `=` |

After the code come the filters on the command line the player typed most recently (the engine reads the session's history, so only a player's own commands carry words; a mob's message needs `ALL`):

| Filter | Meaning |
| --- | --- |
| `ALL` or nothing | any |
| `P words` | the command line must equal the words |
| `word word` | the command line contains any word |
| `=S NAME=*sword*` | a stat (as `LIST STATS` shows) of the message's source (`=S`), target (`=T`) or tool (`=O`) equals the value, where `*` matches any run of characters |
| `=T MASK=-NAMES +Fixsc5victim` | a zapper mask holds for the source, target or tool |

CoffeeSUD also sends these messages to the scripts of an area so that `EXECMSG_PROG` can hear what is not a command: `WEATHERCHANGE` (text "new old forecast"), `TODCHANGE` (DAWN, DAY, DUSK or NIGHT), `DATECHANGE` (text "day month season year") and `GENERATED` (the generator's ID).

### Trying and debugging a trigger

```text
SCRIPT LIST baker
SCRIPT FIRE baker TIME_PROG 5 AS Bob
SCRIPT FIRE innkeeper SPEECH room AS me
SCRIPT FIRE guard GIVE WITH pass
SCRIPT ALARMS baker
SCRIPTTRACE baker
SCRIPTCHECK area
```

`SCRIPT LIST` shows a host's blocks with the file and line each is written at, `[off]` for a disabled one and `[done]` for a `ONCE_PROG` that has run. `SCRIPT FIRE` runs a host's blocks of a trigger now, as if you (or `AS who`) had caused them, ignoring chances and conditions; given words only the blocks that answer to them run, and `WITH item` supplies `$o`. `SCRIPT RESET host ONCE|DELAY|TIME|ALL` lets `ONCE_PROG`, `DELAY_PROG` and the `TIME_PROG`/`DAY_PROG` of this hour or day run again. `SCRIPT ALARMS` lists what is queued. `SCRIPTTRACE` prints each trigger heard, each chance rolled and each line run for a host. `SCRIPTCHECK` reads scripts without running them and reports unknown triggers and commands. `SCRIPT FIRE` is the fastest way to test the event triggers that are hard to produce by hand (the weather, a quest end, a clan join).

### What a save keeps

A saved game keeps which `ONCE_PROG` blocks have run (but not `ONCE_PROG BOOT`), the hour each `TIME_PROG` and the day each `DAY_PROG` last ran, the ticks left on each `DELAY_PROG`, the next time of each `EVERY_PROG`, `MPNOTRIGGER` cool-downs, queued responses and alarms, the meetings that `FIRSTGREET_PROG` and `REGREET_PROG` count, a player's rooms visited (for `DISCOVER_PROG`), and the turn count (`TURN_PROG`). It does not keep a trigger that was running at the moment of the save. A script whose text has changed since the save starts with fresh state. See [Saves and party](31-saves-and-party.md).

## Choosing a trigger

| To do this | Use |
| --- | --- |
| Greet or react when someone walks in | `GREET_PROG` (not sneakers), `ALL_GREET_PROG`, `GROUP_GREET_PROG` (once per party) |
| Answer a spoken word | `SPEECH_PROG`; a topic asked with TALK: `TALK_PROG` |
| Make a new command in a place or on a thing | `VERB_PROG` (always works, wins over skills); `CMDFAIL_PROG` (only when the game would say Huh?) |
| Forbid or rewrite an action | `CNCLMSG_PROG` |
| React after an action | `EXECMSG_PROG`, or the specific one (`GET_PROG`, `DROP_PROG`...) |
| Take an item given or sell for a price | `GIVE_PROG`, `BRIBE_PROG`, `BUY_PROG`, `SELL_PROG` |
| Do something once, ever | `ONCE_PROG` (plus a tattoo or variable for once per character) |
| Do something now and then | `RAND_PROG` (cheap and random), `DELAY_PROG` (ticks), `EVERY_PROG` (game time, saved) |
| Do something at a time of day | `TIME_PROG`; calendar: `DAY_PROG`, `WEEKDAY_PROG`, `MONTH_PROG`, `SEASON_PROG` |
| React to weather or moon | `WEATHER_PROG`, `MOON_PROG` |
| React to a fight | `FIGHT_PROG`, `HITPRCNT_PROG`, `COMBAT_PROG`, `DEATH_PROG` |
| React to anyone anywhere | `QUEST_PROG`, `GAME_PROG`, `PARTY_PROG`, `@GLOBAL` forms, the WORLD host |
| Script a boss, an arena, an ambush | `PHASE_PROG`, `WAVE_PROG`, `ARENAWIN_PROG`, `AMBUSH_PROG` |
| Make scripts talk to each other | `FUNCTION_PROG` with `MPCALLFUNC`; `SIGNAL_PROG` with `MPSIGNAL` |

## Index of triggers by purpose

The **Origin** column says whether the trigger is inherited from CoffeeMud (I) or added by CoffeeSUD (S); several inherited ones were extended or fixed by CoffeeSUD. **Runs** is the timing from [When a trigger runs](#when-a-trigger-runs). **No.** is the trigger's position in the engine's table (`ScriptingEngine.progs`, counting from 1), which the Java code uses as the trigger code.

**Coming and going**

| Trigger | Origin | No. | Runs | Fires when |
| --- | --- | --- | --- | --- |
| [GREET_PROG](#greet_prog) | I | 1 | queued | someone enters the host's room, unless sneaking |
| [ALL_GREET_PROG](#all_greet_prog) | I | 2 | queued | as GREET_PROG, but sneaking entrants count |
| [GROUP_GREET_PROG](#group_greet_prog) | I | 52 | queued | entry, at most once per tick however many arrive |
| [FIRSTGREET_PROG](#firstgreet_prog) | S | 89 | queued | a player character arrives and this mob has never met them |
| [REGREET_PROG](#regreet_prog) | S | 90 | queued | a player character the mob has met comes back after N game days |
| [ARRIVE_PROG](#arrive_prog) | I | 45 | queued | the host itself arrives in a room or enters the game |
| [ENTRY_PROG](#entry_prog) | I | 8 | queued | the host enters a room, or a room or exit host is entered |
| [EXIT_PROG](#exit_prog) | I | 9 | queued | someone leaves the host's room |
| [AREA_ENTER_PROG](#area_enter_prog) | S | 79 | queued | someone comes into the host's area from another area |
| [AREA_LEAVE_PROG](#area_leave_prog) | S | 80 | queued | someone leaves the host's area for another area |
| [DISCOVER_PROG](#discover_prog) | S | 62 | queued | a player character first visits a room or area |

**Speech, commands and dialogue**

| Trigger | Origin | No. | Runs | Fires when |
| --- | --- | --- | --- | --- |
| [SPEECH_PROG](#speech_prog) | I | 3 | queued | someone else says a word or phrase in the room |
| [SPEAK_PROG](#speak_prog) | I | 47 | queued | the host itself says a word or phrase |
| [TALK_PROG](#talk_prog) | S | 100 | now | a player uses TALK (or TALK ABOUT) on the NPC |
| [NOTUNDERSTOOD_PROG](#notunderstood_prog) | S | 117 | queued | a listener hears speech in a language it does not know |
| [CHANNEL_PROG](#channel_prog) | I | 33 | queued | a message appears on a named channel |
| [SOCIAL_PROG](#social_prog) | I | 38 | queued | a social is performed in the room |
| [CMDFAIL_PROG](#cmdfail_prog) | I | 51 | before | a typed command is unknown or fails |
| [VERB_PROG](#verb_prog) | S | 68 | before | a custom verb, ahead of commands, skills and socials |

**Things: items, containers, shops and puzzles**

| Trigger | Origin | No. | Runs | Fires when |
| --- | --- | --- | --- | --- |
| [GIVE_PROG](#give_prog) | I | 4 | queued | a matching item is given to the host |
| [GIVING_PROG](#giving_prog) | I | 48 | queued | the host gives a matching item to someone |
| [BRIBE_PROG](#bribe_prog) | I | 19 | queued | the host is given at least a sum of money |
| [GET_PROG](#get_prog) | I | 20 | queued | someone picks up a matching item |
| [GETTING_PROG](#getting_prog) | I | 54 | queued | the host mob picks up a matching item |
| [DROP_PROG](#drop_prog) | I | 22 | queued | a matching item is dropped |
| [DROPPING_PROG](#dropping_prog) | I | 53 | queued | the host mob drops a matching item |
| [PUT_PROG](#put_prog) | I | 21 | queued | something is put into a matching container |
| [PUTTING_PROG](#putting_prog) | I | 46 | queued | a matching thing is put into something |
| [WEAR_PROG](#wear_prog) | I | 23 | queued | a matching item is worn, held or wielded |
| [WEARING_PROG](#wearing_prog) | I | 57 | queued | the host mob wears, holds or wields a matching item |
| [REMOVE_PROG](#remove_prog) | I | 24 | queued | a matching worn item is removed |
| [REMOVING_PROG](#removing_prog) | S | 60 | queued | the host mob removes a matching worn item |
| [CONSUME_PROG](#consume_prog) | I | 25 | queued | a matching item is eaten or drunk |
| [BUY_PROG](#buy_prog) | I | 27 | queued | something is bought, usually from the host shopkeeper |
| [SELL_PROG](#sell_prog) | I | 28 | queued | something is sold, usually to the host shopkeeper |
| [LOOK_PROG](#look_prog) | I | 39 | queued | something is looked at |
| [LLOOK_PROG](#llook_prog) | I | 40 | queued | something is examined (LOOK at close range) |
| [READ_PROG](#read_prog) | S | 87 | before | someone reads a book, sign or inscription |
| [OPEN_PROG](#open_prog) | I | 34 | queued | a matching door or container is opened |
| [CLOSE_PROG](#close_prog) | I | 35 | queued | a matching door or container is closed |
| [LOCK_PROG](#lock_prog) | I | 36 | queued | a matching door or container is locked |
| [UNLOCK_PROG](#unlock_prog) | I | 37 | queued | a matching door or container is unlocked |
| [USE_PROG](#use_prog) | S | 112 | now | USE x (ON y) or TOUCH x, handled before the combination table |
| [COMBINE_PROG](#combine_prog) | S | 113 | now | COMBINE x WITH y |
| [SWITCH_PROG](#switch_prog) | S | 111 | now | a Prop_Switch lever, dial or button moves to a state |
| [SET_PROG](#set_prog) | S | 114 | now | a tier of an item set comes on or goes off |
| [DESTROY_PROG](#destroy_prog) | S | 94 | now | an item is being destroyed |
| [DECAY_PROG](#decay_prog) | S | 95 | now | an item rots or its expiry runs out |

**Combat and encounters**

| Trigger | Origin | No. | Runs | Fires when |
| --- | --- | --- | --- | --- |
| [FIGHT_PROG](#fight_prog) | I | 7 | tick | every tick the host is in combat |
| [HITPRCNT_PROG](#hitprcnt_prog) | I | 11 | tick | the host's hit points are at or under a percentage, while fighting |
| [DAMAGE_PROG](#damage_prog) | I | 26 | now | the host is damaged, or an item delivers damage |
| [DEATH_PROG](#death_prog) | I | 10 | now | the host dies (or, with @GLOBAL, anyone dies) |
| [KILL_PROG](#kill_prog) | I | 44 | now | the host kills something (or, with @GLOBAL, anything kills) |
| [COMBAT_PROG](#combat_prog) | S | 78 | tick / queued | a fight starts, ends or someone flees |
| [PHASE_PROG](#phase_prog) | S | 105 | now | a boss enters a phase (BossPhases) |
| [SURRENDER_PROG](#surrender_prog) | S | 102 | now | the host surrenders (Morale SURRENDERAT=) |
| [YIELD_PROG](#yield_prog) | S | 103 | now | the host yields instead of dying (Prop_Essential MODE=YIELD) |
| [DEFEAT_PROG](#defeat_prog) | S | 104 | now | the host is knocked out instead of killed (Prop_Essential MODE=KNOCKOUT) |
| [RESET_PROG](#reset_prog) | S | 101 | now | the host is reset after its foes left (EncounterReset) |
| [WAVE_PROG](#wave_prog) | S | 106 | now | a wave of an arena arrives (WaveSpawner) |
| [ARENAWIN_PROG](#arenawin_prog) | S | 107 | now | the party beats the last wave of an arena |
| [ARENALOSE_PROG](#arenalose_prog) | S | 108 | now | the party leaves or falls in an arena |
| [AMBUSH_PROG](#ambush_prog) | S | 109 | now | an ambush springs (Prop_Ambush) |
| [PARTYDEFEAT_PROG](#partydefeat_prog) | S | 110 | now | every member of the party is down or dead |
| [CAST_PROG](#cast_prog) | I | 49 | queued | a skill, spell or other ability is used on the host |
| [CASTING_PROG](#casting_prog) | I | 50 | queued | the host uses a skill, spell or other ability |

**Time and the calendar**

| Trigger | Origin | No. | Runs | Fires when |
| --- | --- | --- | --- | --- |
| [RAND_PROG](#rand_prog) | I | 5 | tick | random chance every tick |
| [DELAY_PROG](#delay_prog) | I | 16 | tick | after a random number of ticks, over and over |
| [ONCE_PROG](#once_prog) | I | 6 | tick | once, as soon as the host is ticked |
| [TIME_PROG](#time_prog) | I | 14 | tick | at given hours of the day |
| [DAY_PROG](#day_prog) | I | 15 | tick | on given days of the month |
| [EVERY_PROG](#every_prog) | S | 91 | tick | every N hours, days, weeks or months of the area's clock |
| [WEEKDAY_PROG](#weekday_prog) | S | 73 | queued | each new day, by day of the week |
| [MONTH_PROG](#month_prog) | S | 71 | queued | when the month changes |
| [SEASON_PROG](#season_prog) | S | 70 | queued | when the season changes |
| [YEAR_PROG](#year_prog) | S | 72 | queued | when the year changes |
| [TURN_PROG](#turn_prog) | S | 67 | queued | each player turn (or every N turns) |
| [WEATHER_PROG](#weather_prog) | S | 63 | queued | the weather in the host's area changes |
| [MOON_PROG](#moon_prog) | S | 64 | queued | the moon's phase changes |

**Quests and achievements**

| Trigger | Origin | No. | Runs | Fires when |
| --- | --- | --- | --- | --- |
| [QUEST_TIME_PROG](#quest_time_prog) | I | 13 | tick | minutes left in a timed quest, and quest step and end markers |
| [QUEST_PROG](#quest_prog) | S | 65 | queued | a quest starts, is accepted, steps, is won, fails or ends, anywhere |
| [QUESTFAIL_PROG](#questfail_prog) | S | 98 | queued | a quest fails for a character |
| [QUESTTIMEOUT_PROG](#questtimeout_prog) | S | 99 | now | a character's own deadline for a quest arrives (MPQTIMER) |
| [ACHIEVE_PROG](#achieve_prog) | S | 96 | queued | an achievement is earned |

**Characters: login, levels, skills, factions, clans and the party**

| Trigger | Origin | No. | Runs | Fires when |
| --- | --- | --- | --- | --- |
| [LOGIN_PROG](#login_prog) | I | 29 | queued | a character enters the world |
| [LOGOFF_PROG](#logoff_prog) | I | 30 | queued | a character quits |
| [LEVEL_PROG](#level_prog) | I | 32 | now | a character gains (or loses) a level |
| [SKILL_PROG](#skill_prog) | S | 61 | queued | a skill is learned, improved, mastered or failed |
| [AGE_PROG](#age_prog) | S | 115 | now | a player character's birthday, or a new age category |
| [FACTION_PROG](#faction_prog) | S | 77 | queued | a character's standing in a faction changes |
| [CLAN_PROG](#clan_prog) | S | 74 | queued | something happens to a clan |
| [PARTY_PROG](#party_prog) | S | 69 | queued | the party changes |
| [CRAFT_PROG](#craft_prog) | S | 75 | queued | a crafter makes something |
| [RECIPE_PROG](#recipe_prog) | S | 76 | queued | a crafter learns a recipe |
| [AFFECT_PROG](#affect_prog) | S | 93 | queued | an effect is put on a thing or comes off it |

**Game, world and generation**

| Trigger | Origin | No. | Runs | Fires when |
| --- | --- | --- | --- | --- |
| [GAME_PROG](#game_prog) | S | 66 | queued (SAVE: before) | new game, restore, save, new character, switch, pause, resume, time mode |
| [VAR_PROG](#var_prog) | S | 92 | queued | a script variable's change makes a condition true |
| [GENERATED_PROG](#generated_prog) | S | 88 | queued | a generator has made the area or room the host is in |

**Riding, vessels and journeys**

| Trigger | Origin | No. | Runs | Fires when |
| --- | --- | --- | --- | --- |
| [RIDE_PROG](#ride_prog) | I | 55 | queued | someone mounts a rideable |
| [RIDING_PROG](#riding_prog) | I | 56 | queued | the host mob mounts a rideable |
| [DISMOUNT_PROG](#dismount_prog) | S | 59 | queued | someone gets off a rideable |
| [BOARD_PROG](#board_prog) | S | 81 | queued | someone boards a vessel |
| [DISEMBARK_PROG](#disembark_prog) | S | 82 | queued | someone leaves a vessel |
| [SAIL_PROG](#sail_prog) | S | 83 | queued | an order is given to a vessel |
| [VESSEL_ARRIVE_PROG](#vessel_arrive_prog) | S | 84 | queued | the vessel comes into a room |
| [VESSEL_GREET_PROG](#vessel_greet_prog) | S | 85 | queued | a vessel arrives where the host is |
| [DOCK_PROG](#dock_prog) | S | 86 | queued | the vessel's anchor drops |
| [JOURNEY_PROG](#journey_prog) | S | 116 | queued | a journey starts, is cancelled, advances an hour or arrives |

**Raw messages and script plumbing**

| Trigger | Origin | No. | Runs | Fires when |
| --- | --- | --- | --- | --- |
| [ACT_PROG](#act_prog) | I | 18 | queued | the host sees a line of text, from its point of view |
| [MASK_PROG](#mask_prog) | I | 12 | queued | as ACT_PROG |
| [IMASK_PROG](#imask_prog) | I | 43 | queued | the host itself does something that produces text |
| [REGMASK_PROG](#regmask_prog) | I | 31 | queued | the host sees text matching a regular expression |
| [EXECMSG_PROG](#execmsg_prog) | I | 41 | queued | after any game message of a type has happened |
| [CNCLMSG_PROG](#cnclmsg_prog) | I | 42 | before | before any game message of a type is allowed (it can be cancelled) |
| [FUNCTION_PROG](#function_prog) | I | 17 | called | a named function, called by MPCALLFUNC |
| [SIGNAL_PROG](#signal_prog) | S | 97 | queued | another script sends a signal to this thing (MPSIGNAL) |

## Trigger reference

Each entry gives the syntax, the arguments, when the trigger fires and when it does not, the hosts that can use it, what the codes hold, an example block, and notes. Examples are blocks you can put in a script as they stand; they are adapted from the engine's own test fixtures where one exists. `[x]` is optional, `a | b` is a choice, `...` repeats.

## Coming and going

### GREET_PROG

*inherited from CoffeeMud. Table position 1. Runs: queued.*

Syntax:

```text
GREET_PROG [chance | zapper mask]
```

**Arguments.**

- No argument: always. A number: percent chance (`GREET_PROG 25`). Otherwise a zapper mask tested on the one who entered (`GREET_PROG -RACE +Elf`; `P` before a mask also works). See [Argument forms](#argument-forms).

**Fires.** When another creature enters the room the host is in, and the host (a mob) can see it arrive. A sneaking or hidden entrant that the mob cannot sense does not set it off. For an area host, any room of the area counts.

**Does not fire.** The host's own entry; entries the mob cannot sense (sneaking, dark, blind); a mob host that is busy.

**Hosts.** Mob, item (in a room or carried), room, area, exit. For an item carried by a mob, the carrier's room counts and the carrier's own entry does not. A mob host runs it only while it is free (see [Busy hosts](#busy-hosts)).

**Codes.** `$n` the one who entered; `$t` and `$i` the host (a stand-in mob for non-mob hosts); `$o` the host if it is an item; `$g` empty.

Example:

```text
GREET_PROG 100
if ispc($n)
  say Welcome to the Gilded Goose, $n.
endif
~
```

**Notes.** Queued: runs on the host's next tick. Use [ALL_GREET_PROG](#all_greet_prog) to hear sneakers as well, and [ARRIVE_PROG](#arrive_prog) for the host's own arrival.

### ALL_GREET_PROG

*inherited from CoffeeMud. Table position 2. Runs: queued.*

Syntax:

```text
ALL_GREET_PROG [chance | zapper mask]
```

**Arguments.** As [GREET_PROG](#greet_prog).

**Fires.** When another creature enters the room, sneaking or not. The mob does not need to see it, only to be able to act.

**Does not fire.** The host's own entry; a busy mob host; a mob that cannot act (asleep, paralysed).

**Hosts.** Mob, item, room, area, exit, as GREET_PROG.

**Codes.** As GREET_PROG.

Example:

```text
ALL_GREET_PROG -RACE +Elf
mpechoat $n The old gate-warden watches you pass, hand on his sword.
~
```

**Notes.** Queued. The chance or mask is tested on the entrant.

### GROUP_GREET_PROG

*inherited from CoffeeMud. Table position 52. Runs: queued.*

Syntax:

```text
GROUP_GREET_PROG [chance | zapper mask]
```

**Arguments.** As [GREET_PROG](#greet_prog).

**Fires.** When someone enters the room, as ALL_GREET_PROG, but after one run it will not run again until the host's next tick, so a party walking in together sets it off once, for the first to enter.

**Does not fire.** Area hosts (the trigger needs the host's own room); the second and later members of a group arriving in one tick.

**Hosts.** Mob, item, room, exit. Not areas.

**Codes.** `$n` the first to enter; `$t` and `$i` the host; `$o` the host item; `$g` empty.

Example:

```text
GROUP_GREET_PROG 100
mpecho The sentry blows a short note on his horn as the group files in.
~
```

**Notes.** The once-a-tick gate is shared by every GROUP_GREET_PROG on the same script engine, and `MPNOTRIGGER GROUP_GREET_PROG` stops it as it does the others.

### FIRSTGREET_PROG

*added by CoffeeSUD. Table position 89. Runs: queued.*

Syntax:

```text
FIRSTGREET_PROG [chance]
```

**Arguments.** Optional percent chance (or mask) as GREET_PROG, tested on the arriving character.

**Fires.** When a player character arrives in the mob's room and the mob's record of meetings has no earlier meeting with that character. Every arrival the mob sees (and every TALK to it) updates the record: times met, first and last meeting, in game time. The record is saved with the game.

**Does not fire.** Characters already met; arrivals the mob cannot sense; non-player arrivals; a busy mob.

**Hosts.** Mob only.

**Codes.** As GREET_PROG: `$n` the character, `$t` and `$i` the mob, `$g` empty.

Example:

```text
FIRSTGREET_PROG 100
say Well met, stranger. I am Old Ned; I mend nets.
~
REGREET_PROG 3
say Back again, $n? Three days since I saw your face.
~
```

**Notes.** Pair it with [REGREET_PROG](#regreet_prog). The functions `MEMORY()` and its kin read the same record (see [MOBPROG functions](28-mobprog-functions.md)). Fixture: `st21-meet.js`.

### REGREET_PROG

*added by CoffeeSUD. Table position 90. Runs: queued.*

Syntax:

```text
REGREET_PROG [game days] [chance]
```

**Arguments.** The first word is a number of game days (0 or none: any return). The optional second word is a chance or mask as GREET_PROG.

**Fires.** When a player character the mob has met before arrives in its room and at least that many game days have passed since the last meeting.

**Does not fire.** First meetings (use FIRSTGREET_PROG); meetings sooner than the number of days.

**Hosts.** Mob only.

**Codes.** As FIRSTGREET_PROG.

Example:

```text
REGREET_PROG 1
say You again, $n. A day gone and you are as muddy as ever.
~
```

**Notes.** A game day is the length of the world clock's day. The record is updated by the same arrival, so the interval is measured from the meeting before this one.

### ARRIVE_PROG

*inherited from CoffeeMud. Table position 45. Runs: queued.*

Syntax:

```text
ARRIVE_PROG [chance | zapper mask]
```

**Arguments.** A percent chance, or a zapper mask tested on the host mob (the arriving one).

**Fires.** When the scripted mob enters a room, or appears in the world; for an item, when the mob that carries it does.

**Does not fire.** Rooms, areas and exits never hear it; other creatures' moves.

**Hosts.** Mob, item carried by a mob. A mob host runs it only while it is free (see [Busy hosts](#busy-hosts)).

**Codes.** `$n` and `$i` the arriving mob (the carrier, for an item); `$t` the host mob; `$o` the host item; `$g` empty.

Example:

```text
ARRIVE_PROG 100
mpecho $i stamps the road dust from $m boots.
~
```

**Notes.** A scripted item carried by a mob that runs "full throttle" does not queue duplicates: an unrun ARRIVE_PROG of the same script is replaced. Compare [ENTRY_PROG](#entry_prog).

### ENTRY_PROG

*inherited from CoffeeMud. Table position 8. Runs: queued.*

Syntax:

```text
ENTRY_PROG [chance | zapper mask]
```

**Arguments.** A percent chance, or a zapper mask tested on the one who moved.

**Fires.** When the scripted mob enters a room; when a scripted room is entered; when a scripted exit is used to enter. An item hears any entry its room hears.

**Does not fire.** A mob host that is busy.

**Hosts.** Mob, room, exit, item. A mob host runs it only while it is free (see [Busy hosts](#busy-hosts)).

**Codes.** `$n` the one who entered; `$t` and `$i` the host; `$o` the host item; `$g` the entered room's ID (for example `Midgaard#3001`).

Example:

```text
ENTRY_PROG 100
if !inroom($i == Lighthouse#2)
  mpgoto Lighthouse#2
endif
~
```

**Notes.** If an earlier run of the same script for the same mover is still queued, the engine runs that one first, then queues the new one. Queued unless `NODELAY`.

### EXIT_PROG

*inherited from CoffeeMud. Table position 9. Runs: queued.*

Syntax:

```text
EXIT_PROG [chance | zapper mask]
```

**Arguments.** A percent chance, or a zapper mask tested on the one who left.

**Fires.** When a creature other than the host leaves the room the host is in.

**Does not fire.** The host's own departure.

**Hosts.** Mob, item, room. A mob host runs it only while it is free (see [Busy hosts](#busy-hosts)).

**Codes.** `$n` the one who left; `$t` and `$i` the host; `$o` the host item; `$g` the ID of the room left.

Example:

```text
EXIT_PROG 100
if ispc($n)
  mpecho $i mutters, "Another one off to the marsh."
endif
~
```

**Notes.** As ENTRY_PROG, an earlier queued run for the same mover is run first.

### AREA_ENTER_PROG

*added by CoffeeSUD. Table position 79. Runs: queued.*

Syntax:

```text
AREA_ENTER_PROG [ALL] [chance | zapper mask]
```

**Arguments.** `ALL` counts a move between this area and an area inside it (a sub-area) too; without it such moves are ignored. Then an optional percent chance or a zapper mask tested on the mover. With neither: always.

**Fires.** When a creature crosses into the area (or a parent area) of the host from a room in a different area, or comes into the world (from no room, as when a saved game loads), once for each host.

**Does not fire.** Moves inside one area; moves between the area and its sub-areas (without ALL).

**Hosts.** Any host located in the area (mob, item, room) and the area itself, or in an area whose sub-area is the one entered.

**Codes.** `$n` the mover; `$t` the host; `$g` the name of the area left (empty from nowhere); `$0` the ID of the room left.

Example:

```text
AREA_ENTER_PROG ALL
mpecho A bell rings once from the harbour wall as you cross into Brenn.
~
```

**Notes.** Prop_AreaEntry and Prop_AreaBanner show text at the same border without a script (see [Properties reference](23-properties-reference.md)). Queued. Fixture: `st16-border.js`.

### AREA_LEAVE_PROG

*added by CoffeeSUD. Table position 80. Runs: queued.*

Syntax:

```text
AREA_LEAVE_PROG [ALL] [chance | zapper mask]
```

**Arguments.** As [AREA_ENTER_PROG](#area_enter_prog).

**Fires.** When a creature moves out of the host's area into a different area.

**Does not fire.** Moves inside one area; moves into a sub-area (without ALL).

**Hosts.** As AREA_ENTER_PROG.

**Codes.** `$n` the mover; `$t` the host; `$g` the name of the area entered; `$0` the ID of the room entered.

Example:

```text
AREA_LEAVE_PROG 100
if ispc($n)
  mpechoat $n The gate-guard calls after you: "Mind the wolves past the ridge!"
endif
~
```

**Notes.** Fires with a vessel area too when someone steps from the deck to the shore (the deck is its own area).

### DISCOVER_PROG

*added by CoffeeSUD. Table position 62. Runs: queued.*

Syntax:

```text
DISCOVER_PROG ROOM|AREA [PARTY] [zapper mask]
```

**Arguments.** `ROOM` fires for the first visit to the room the host hears. `AREA` fires for the first room visited in a new area. `PARTY` adds: only if nobody else in the character's group had been there. A trailing zapper mask is tested on the character.

**Fires.** When a player character enters a room for the first time (their visits are kept in the player's stats and the save). Each party member counts separately unless PARTY is given.

**Does not fire.** Repeat visits; non-player mobs.

**Hosts.** Heard by the room, its items, its mobs and what they carry, and its area and parent areas.

**Codes.** `$n` the character; `$t` the room; `$g` the room's ID.

Example:

```text
DISCOVER_PROG ROOM
mpecho The nave opens above you, and the floor is a mosaic of drowned saints.
~
DISCOVER_PROG AREA
mpexp $n 200
~
```

**Notes.** Also used by the discovery XP rules. Fixture: `st3-discover.js`.

## Speech, commands and dialogue

### SPEECH_PROG

*inherited from CoffeeMud. Table position 3. Runs: queued.*

Syntax:

```text
SPEECH_PROG [ALL | P phrase | word word ...]
```

**Arguments.** No argument or `ALL`: any speech. Words: fires when any of the words occurs in the line as a whole word, in any case. Punctuation in the spoken line is kept, so `tower` does not match `tower?`: list both forms. `P` then a phrase: the whole line must equal the phrase. Quote multi-word items: `'tell me' about`. See [Argument forms](#argument-forms).

**Fires.** When another creature says something (SAY, or speech addressed to the mob). A mob host hears only speech it understands in its language; rooms, items and areas ignore speech in any language other than Common.

**Does not fire.** The host's own speech (use [SPEAK_PROG](#speak_prog)); channel talk; a busy mob.

**Hosts.** Mob, item, room, area, exit. A mob host runs it only while it is free (see [Busy hosts](#busy-hosts)).

**Codes.** `$n` the speaker; `$t` who was spoken to (or empty); `$i` the host; `$o` the host item; `$g` the words said, from the matched word on, in lower case (`$G` upper case).

Example:

```text
SPEECH_PROG tower tower?
if ispc($n)
  say The tower? Past the ford, $n. Mind the water.
endif
~
SPEECH_PROG P the sea is calm
mpunlock down
~
```

**Notes.** With several keywords in one script line a spoken line normally runs the script once (the first match); `MPSETINTERNAL MULTITRIGGER TRUE` runs it for each. Queued, and the reply to a player runs before the player's next prompt in turn-based time. Fixtures: `n9-talk.js`, `dt7-script.js`.

### SPEAK_PROG

*inherited from CoffeeMud. Table position 47. Runs: queued.*

Syntax:

```text
SPEAK_PROG [ALL | P phrase | word word ...]
```

**Arguments.** As [SPEECH_PROG](#speech_prog).

**Fires.** When the scripted mob itself says the words; for an item, room or area, when anyone does.

**Does not fire.** Other creatures' speech on a mob host; channel messages.

**Hosts.** Mob (own speech), item, room, area.

**Codes.** `$n` the speaker; `$t` who was spoken to; `$g` the words said from the matched word on (upper case in `$G`).

Example:

```text
SPEAK_PROG open sesame
mpecho The old mob mutters the password and the door grinds.
~
```

**Notes.** Useful for a mob that triggers its own follow-on script (it says a code word; another SPEAK_PROG or SPEECH_PROG on it answers).

### TALK_PROG

*added by CoffeeSUD. Table position 100. Runs: now.*

Syntax:

```text
TALK_PROG [ALL | P phrase | word word ...]
```

**Arguments.** No argument, `ALL` or `*`: any talk. `P` then a phrase: the topic must contain the phrase. Words: any of them in the topic. The topic is whatever follows ABOUT in `TALK npc ABOUT topic`, and empty for a bare TALK.

**Fires.** When a character begins a conversation with the NPC by TALK. It runs at once. If no TALK_PROG fits, the character says the topic (or Hello) to the mob instead, so its SPEECH_PROGs and MudChat answer.

**Does not fire.** Ordinary SAY (that is SPEECH_PROG).

**Hosts.** Mob only (the NPC talked to).

**Codes.** `$n` the speaker; `$t` and `$i` the NPC; `$g` and `$0` the topic.

Example:

```text
TALK_PROG mine ore
say The mine? Flooded since the spring. Ask the foreman.
~
TALK_PROG ALL
say Hm? What would you ask of me, $n?
~
```

**Notes.** All TALK_PROGs that fit run. The topic is also recorded as the last topic for `MEMORY()`. See [NPC dialogue](06-npcs-dialogue.md). Fixture: `n9-talk.js`.

### NOTUNDERSTOOD_PROG

*added by CoffeeSUD. Table position 117. Runs: queued.*

Syntax:

```text
NOTUNDERSTOOD_PROG [language | ANY] [P phrase | text]
```

**Arguments.** The first word is a language name (Common, Dwarvish...) or `ANY`; the second part optionally limits it to garbled text containing the text (a leading `P` is ignored).

**Fires.** When a character hears speech and its proficiency in the language is zero, so the words reach it garbled.

**Does not fire.** Speech the listener understands, even partly.

**Hosts.** The listener's own scripts: mob (or player-character host).

**Codes.** `$n` the speaker; `$t` and `$i` the listener; `$g` the language; `$0` the garbled text as heard.

Example:

```text
NOTUNDERSTOOD_PROG Dwarvish
say I do not follow your tongue, friend. Speak Common.
~
```

**Notes.** Fixture: `l-language.js`. See [Presentation and language](11-presentation-language.md).

### CHANNEL_PROG

*inherited from CoffeeMud. Table position 33. Runs: queued.*

Syntax:

```text
CHANNEL_PROG channel [ALL | P phrase | word word ...]
```

**Arguments.** The channel name (matched ignoring case), then the keyword forms of SPEECH_PROG matched against the text of the message.

**Fires.** When any creature sends a message on the channel; the host hears it wherever it is.

**Does not fire.** The host's own messages.

**Hosts.** Any host (the script registers the host for channel messages when it is parsed).

**Codes.** `$n` the sender; `$g` the matched word, or the message text.

Example:

```text
CHANNEL_PROG QUESTCHAT ALL
mpecho A herald's voice carries across the market.
~
```

**Notes.** CoffeeSUD removed player channels. Only the channels listed under `CHANNELS` in `coffeemud.ini` remain (INFO, QUESTCHAT and the like), so CHANNEL_PROG hears little.

### SOCIAL_PROG

*inherited from CoffeeMud. Table position 38. Runs: queued.*

Syntax:

```text
SOCIAL_PROG [EXACT] [ME] social name
```

**Arguments.** The social's name: it matches any social whose name starts with the word given. `EXACT` requires the whole name. `ME` requires the social to be aimed at the scripted mob.

**Fires.** When a creature other than the host performs a matching social that the host hears.

**Does not fire.** The host's own socials.

**Hosts.** Mob, item, room, area, exit.

**Codes.** `$n` who performed it; `$t` its target; `$g` the social's full name.

Example:

```text
SOCIAL_PROG EXACT ME KNEEL
say Rise, $n. I am no lord.
~
```

**Notes.** Replies to players run before the next prompt in turn-based time, as for SPEECH_PROG. Fixture: `st22-social.js`.

### CMDFAIL_PROG

*inherited from CoffeeMud. Table position 51. Runs: before.*

Syntax:

```text
CMDFAIL_PROG command [ALL | P phrase | word word ...]
```

**Arguments.** `command` is the custom verb. The word the player typed must be a prefix of it (so `OP` fires for OPEN). Then nothing or `ALL` for any words after it, `P` and a phrase for exactly those words, or words to look for in the rest of the line.

**Fires.** When the command line fails to parse (the player would see "Huh?") and its first word starts the verb. It runs before the failure is shown.

**Does not fire.** Commands the game knows; VERB_PROG's job is the other kind of custom verb, which takes precedence over commands, skills and socials.

**Hosts.** Mob, item, room, area, exit (anything that hears the failing message).

**Codes.** `$n` who typed it; `$t` the command's target if any; `$g` the rest of the line (`$G` as upper case); the matched word is put first when you gave words.

Example:

```text
CMDFAIL_PROG PRAY ALTAR
mpecho $n kneels before the altar and prays.
return cancel
~
```

**Notes.** `RETURN CANCEL` keeps the failure message back. It is the only trigger of this kind that can cancel; most others run after the deed. The engine does not check that the message is the Huh message, only that its text starts with the word. Fixtures: `sc9-fixes.js`.

### VERB_PROG

*added by CoffeeSUD. Table position 68. Runs: before.*

Syntax:

```text
VERB_PROG verb[,alias,...] [P phrase | keyword keyword ...]
```

**Arguments.** The verb is matched whole, not as a prefix; aliases follow separated by commas with no spaces. With no further words the rest of the line must name the host; a room or area needs no naming. `P phrase` requires the phrase in the rest of the line; keywords require any one of them.

**Fires.** When a player types the verb, before any command, skill or social of that word is looked for, if the host is within reach.

**Does not fire.** A script that ends with `RETURN CONTINUE` hands the line back to normal parsing.

**Hosts.** Items the player carries or are in the room, other mobs in the room, the room's exits, the room and the area.

**Codes.** `$n` the player; `$t` and `$i` the host; `$o` the host if an item; `$g` the rest of the line as typed (lower case; `$G` as typed).

Example:

```text
VERB_PROG turn,spin P wheel
mpecho The wheel groans round.
mpunlock north
~
```

**Notes.** `LIST VERBS` lists a room's verbs. `Prop_Verbs` does a simple verb with no script. The first host in reach that answers takes the line. Fixture: `st7-verb.js`.

## Things: items, containers, shops and puzzles

### GIVE_PROG

*inherited from CoffeeMud. Table position 4. Runs: queued.*

Syntax:

```text
GIVE_PROG [ALL | P name | word word ...]
```

**Arguments.** Keyword list: `ALL` or nothing for any; words, any of which may be a whole word of the thing's name (or equal its class ID); `P name` for exactly that name. See [Argument forms](#argument-forms). The words are matched against the item given.

**Fires.** When another creature gives the host mob an item (the host is the target), or, on the item itself, when it is given to someone; on a room or area, any giving.

**Does not fire.** The host giving something (that is [GIVING_PROG](#giving_prog)); gifts of money (that is [BRIBE_PROG](#bribe_prog)).

**Hosts.** Mob (the receiver), item (the thing given), room, area. A mob host runs it only while it is free (see [Busy hosts](#busy-hosts)).

**Codes.** `$n` the giver; `$t` and `$i` the host mob; `$o` the item given; `$p` the host item (if the host is an item); `$g` the matched word.

Example:

```text
GIVE_PROG pass
say A pass from the captain. Go through, $n.
mpjunk $o
mpunlock north
~
GIVE_PROG all
say I have no use for that. Take it back.
give $o $n
~
```

**Notes.** Remember `mpjunk $o` to keep the item from being stolen back. Put the specific GIVE_PROGs before `GIVE_PROG all`: by default only the first matching script runs. Queued. Fixture: `dt7-script.js`.

### GIVING_PROG

*inherited from CoffeeMud. Table position 48. Runs: queued.*

Syntax:

```text
GIVING_PROG [ALL | P name | word word ...]
```

**Arguments.** Keyword list: `ALL` or nothing for any; words, any of which may be a whole word of the thing's name (or equal its class ID); `P name` for exactly that name. See [Argument forms](#argument-forms).

**Fires.** When the host mob gives away a matching item; on the item itself, when it is the thing given; on a room or area, any giving.

**Does not fire.** Gifts to the host.

**Hosts.** Mob (the giver), item, room, area.

**Codes.** `$n` the giver; `$t` the receiver; `$o` the item; `$p` the host item.

Example:

```text
GIVING_PROG medal
say Wear it with pride, $t.
~
```

**Notes.** Queued; it runs after the deed.

### BRIBE_PROG

*inherited from CoffeeMud. Table position 19. Runs: queued.*

Syntax:

```text
BRIBE_PROG amount | ANY | ALL
```

**Arguments.** A number: the least value, in the base currency of the host's money. `ANY` or `ALL` accept any sum, in any currency. With a number, coins of another currency do not count.

**Fires.** When another creature gives the host (or, for non-mob hosts, anyone in range) coins worth at least the amount.

**Does not fire.** Sums below the amount; coins in a foreign currency; the host giving money.

**Hosts.** Mob (the receiver), item, room, area, exit. A mob host runs it only while it is free (see [Busy hosts](#busy-hosts)).

**Codes.** `$n` the giver; `$t` and `$i` the host; `$o` the coins; `$p` the host item.

Example:

```text
BRIBE_PROG 500
say Five hundred buys you a quiet night at the gate.
mpunlock north
~
BRIBE_PROG 1
say Hm. It is not enough.
~
```

**Notes.** List bribes from the largest to the smallest: the first fitting script wins. Queued.

### GET_PROG

*inherited from CoffeeMud. Table position 20. Runs: queued.*

Syntax:

```text
GET_PROG [ALL | P name | word word ...]
```

**Arguments.** Keyword list: `ALL` or nothing for any; words, any of which may be a whole word of the thing's name (or equal its class ID); `P name` for exactly that name. See [Argument forms](#argument-forms). Matched against the item picked up; if it is taken out of a container, the item taken, not the container.

**Fires.** When a creature other than the host takes a matching item. On an item host it fires when that item is picked up (or, for a container, when something is taken out of it); on a mob, room or area, for any pick-up it witnesses.

**Does not fire.** The host's own pick-ups ([GETTING_PROG](#getting_prog)).

**Hosts.** Mob (witness), item, room, area. A mob host runs it only while it is free (see [Busy hosts](#busy-hosts)).

**Codes.** `$n` the one who got it; `$t` the thing; `$o` the item taken; `$p` the host item.

Example:

```text
GET_PROG sunblade
mpecho The sunken shrine shudders as $n lifts the blade.
mpoload CATALOG the Sunblade
~
```

**Notes.** Queued. `MPNOTRIGGER GET_PROG` stops GETTING_PROG as well.

### GETTING_PROG

*inherited from CoffeeMud. Table position 54. Runs: queued.*

Syntax:

```text
GETTING_PROG [ALL | P name | word word ...]
```

**Arguments.** Keyword list: `ALL` or nothing for any; words, any of which may be a whole word of the thing's name (or equal its class ID); `P name` for exactly that name. See [Argument forms](#argument-forms).

**Fires.** When the scripted mob itself picks up a matching item.

**Does not fire.** Pick-ups by others; item, room and area hosts.

**Hosts.** Mob only. A mob host runs it only while it is free (see [Busy hosts](#busy-hosts)).

**Codes.** `$n` and `$i` the host; `$t` and `$o` the item.

Example:

```text
GETTING_PROG coin
say Mine now.
~
```

**Notes.** Queued. Disabled with `MPNOTRIGGER GET_PROG` too.

### DROP_PROG

*inherited from CoffeeMud. Table position 22. Runs: queued.*

Syntax:

```text
DROP_PROG [ALL | P name | word word ...]
```

**Arguments.** Keyword list: `ALL` or nothing for any; words, any of which may be a whole word of the thing's name (or equal its class ID); `P name` for exactly that name. See [Argument forms](#argument-forms).

**Fires.** When a creature other than the host drops a matching item. On an item, when that item is dropped; on a mob, room or area, any drop it witnesses.

**Does not fire.** The host's own drops ([DROPPING_PROG](#dropping_prog)).

**Hosts.** Mob (witness), item, room, area. A mob host runs it only while it is free (see [Busy hosts](#busy-hosts)).

**Codes.** `$n` who dropped it; `$t` and `$o` the item; `$p` the host item.

Example:

```text
DROP_PROG all
if ispc($n)
  say No littering in the temple!
endif
~
```

**Notes.** Money dropped on the ground runs the script at once rather than queued, with `$p` a copy of the coins.

### DROPPING_PROG

*inherited from CoffeeMud. Table position 53. Runs: queued.*

Syntax:

```text
DROPPING_PROG [ALL | P name | word word ...]
```

**Arguments.** Keyword list: `ALL` or nothing for any; words, any of which may be a whole word of the thing's name (or equal its class ID); `P name` for exactly that name. See [Argument forms](#argument-forms).

**Fires.** When the scripted mob itself drops a matching item.

**Does not fire.** Drops by others; non-mob hosts.

**Hosts.** Mob only. A mob host runs it only while it is free (see [Busy hosts](#busy-hosts)).

**Codes.** `$n` and `$i` the host; `$t` and `$o` the item.

Example:

```text
DROPPING_PROG torch
say Too heavy.
~
```

**Notes.** Only `MPNOTRIGGER DROP_PROG` stops it; its own name is not checked.

### PUT_PROG

*inherited from CoffeeMud. Table position 21. Runs: queued.*

Syntax:

```text
PUT_PROG [ALL | P name | word word ...]
```

**Arguments.** Keyword list: `ALL` or nothing for any; words, any of which may be a whole word of the thing's name (or equal its class ID); `P name` for exactly that name. See [Argument forms](#argument-forms). Matched against the **container**.

**Fires.** When a creature other than the host puts an item into a container whose name matches. On a container item host, when something is put into it. On a mob, room or area, any such putting it witnesses.

**Does not fire.** The host being the thing put in; the host mob's own putting.

**Hosts.** Container item, mob (witness), room, area. A mob host runs it only while it is free (see [Busy hosts](#busy-hosts)).

**Codes.** `$n` who put it; `$t` and `$o` the container; `$p` the thing put in.

Example:

```text
PUT_PROG offering bowl
mpecho The bowl hums as $n lays $p in it.
~
```

**Notes.** Compare [PUTTING_PROG](#putting_prog), which matches the thing put. Coins put into a container that lies in a room run at once rather than queued; other puttings are queued.

### PUTTING_PROG

*inherited from CoffeeMud. Table position 46. Runs: queued.*

Syntax:

```text
PUTTING_PROG [ALL | P name | word word ...]
```

**Arguments.** Keyword list: `ALL` or nothing for any; words, any of which may be a whole word of the thing's name (or equal its class ID); `P name` for exactly that name. See [Argument forms](#argument-forms). Matched against the thing **put**.

**Fires.** When a matching item is put into a container. On a mob, only its own putting; on an item, when it is the thing put; on a room or area, anyone's.

**Does not fire.** A scripted container receiving things (use PUT_PROG).

**Hosts.** Mob (its own), item (the thing put), room, area.

**Codes.** `$n` who put it; `$t` and `$o` the container; `$p` the thing put.

Example:

```text
PUTTING_PROG key
mpecho The key sinks into the chest with a soft click.
~
```

**Notes.** Disabled by `MPNOTRIGGER PUTTING_PROG`.

### WEAR_PROG

*inherited from CoffeeMud. Table position 23. Runs: queued.*

Syntax:

```text
WEAR_PROG [ALL | P name | word word ...]
```

**Arguments.** Keyword list: `ALL` or nothing for any; words, any of which may be a whole word of the thing's name (or equal its class ID); `P name` for exactly that name. See [Argument forms](#argument-forms).

**Fires.** When a creature other than the host wears, holds or wields a matching item. On an item, when it is put on; on a mob, room or area, for any it witnesses.

**Does not fire.** The host's own wearing ([WEARING_PROG](#wearing_prog)).

**Hosts.** Mob (witness), item, room, area. A mob host runs it only while it is free (see [Busy hosts](#busy-hosts)).

**Codes.** `$n` the wearer; `$t` the host mob; `$o` the item worn; `$p` the host item.

Example:

```text
WEAR_PROG
mpechoat $n The ring is cold, and then it is not.
~
```

**Notes.** On an item host `$o` is the item itself. Queued.

### WEARING_PROG

*inherited from CoffeeMud. Table position 57. Runs: queued.*

Syntax:

```text
WEARING_PROG [ALL | P name | word word ...]
```

**Arguments.** Keyword list: `ALL` or nothing for any; words, any of which may be a whole word of the thing's name (or equal its class ID); `P name` for exactly that name. See [Argument forms](#argument-forms).

**Fires.** When the scripted mob itself wears, holds or wields a matching item.

**Does not fire.** Others wearing things.

**Hosts.** Mob only. A mob host runs it only while it is free (see [Busy hosts](#busy-hosts)).

**Codes.** `$n` and `$i` the host; `$t` and `$o` the item.

Example:

```text
WEARING_PROG cloak
say Warm at last.
~
```

**Notes.** Disabled by `MPNOTRIGGER WEAR_PROG` too.

### REMOVE_PROG

*inherited from CoffeeMud. Table position 24. Runs: queued.*

Syntax:

```text
REMOVE_PROG [ALL | P name | word word ...]
```

**Arguments.** Keyword list: `ALL` or nothing for any; words, any of which may be a whole word of the thing's name (or equal its class ID); `P name` for exactly that name. See [Argument forms](#argument-forms).

**Fires.** When a creature other than the host removes a matching worn item. On an item, when it is taken off.

**Does not fire.** The host's own removing ([REMOVING_PROG](#removing_prog)).

**Hosts.** Mob (witness), item, room, area. A mob host runs it only while it is free (see [Busy hosts](#busy-hosts)).

**Codes.** `$n` who removed it; `$t` and `$o` the item; `$p` the host item.

Example:

```text
REMOVE_PROG ring
mpechoat $n You feel the ring's warmth leave your hand.
~
```

**Notes.** Queued.

### REMOVING_PROG

*added by CoffeeSUD. Table position 60. Runs: queued.*

Syntax:

```text
REMOVING_PROG [ALL | P name | word word ...]
```

**Arguments.** Keyword list: `ALL` or nothing for any; words, any of which may be a whole word of the thing's name (or equal its class ID); `P name` for exactly that name. See [Argument forms](#argument-forms).

**Fires.** When the scripted mob itself removes a matching item it wears.

**Does not fire.** Others removing things.

**Hosts.** Mob only. A mob host runs it only while it is free (see [Busy hosts](#busy-hosts)).

**Codes.** `$n` and `$i` the host; `$t` and `$o` the item.

Example:

```text
REMOVING_PROG helm
say Hot work, this.
~
```

**Notes.** Disabled by `MPNOTRIGGER REMOVE_PROG` too. Fixture: `st18-ride-triggers.js`.

### CONSUME_PROG

*inherited from CoffeeMud. Table position 25. Runs: queued.*

Syntax:

```text
CONSUME_PROG [ALL | P name | word word ...]
```

**Arguments.** Keyword list: `ALL` or nothing for any; words, any of which may be a whole word of the thing's name (or equal its class ID); `P name` for exactly that name. See [Argument forms](#argument-forms).

**Fires.** When a creature other than the host eats or drinks from a matching item. On a food or drink item, when it is consumed.

**Does not fire.** The host itself eating.

**Hosts.** Mob (witness), item, room, area. A mob host runs it only while it is free (see [Busy hosts](#busy-hosts)).

**Codes.** `$n` who ate or drank; `$t` and `$o` the item; `$p` the host item (a copy of it when the host is the food).

Example:

```text
CONSUME_PROG mushroom
mpechoat $n The mushroom tastes of copper and rain.
mpcast Spell_Haste $n
~
```

**Notes.** When the scripted item is itself the food consumed, the script runs at once on a copy, as the item is about to vanish.

### BUY_PROG

*inherited from CoffeeMud. Table position 27. Runs: queued.*

Syntax:

```text
BUY_PROG [ALL | P name | word word ...]
```

**Arguments.** Keyword list: `ALL` or nothing for any; words, any of which may be a whole word of the thing's name (or equal its class ID); `P name` for exactly that name. See [Argument forms](#argument-forms). Matched against the item bought.

**Fires.** When a creature other than the host buys a matching item. A shopkeeper host hears only purchases from itself. Other hosts hear any purchase they witness.

**Does not fire.** Items the shop does not sell; the host buying.

**Hosts.** Shopkeeper mob, any mob (witness), item, room, area. A mob host runs it only while it is free (see [Busy hosts](#busy-hosts)).

**Codes.** `$n` the buyer; `$t` and `$i` the host mob; `$o` and `$p` the product (a plain copy for products that are not items, such as services); `$g` the matched word.

Example:

```text
BUY_PROG ferry ticket
say Hold fast. The river is rough today.
mptransfer $n Farside#1
~
```

**Notes.** A ferryman or guide can run services through BUY_PROG. Queued.

### SELL_PROG

*inherited from CoffeeMud. Table position 28. Runs: queued.*

Syntax:

```text
SELL_PROG [ALL | P name | word word ...]
```

**Arguments.** Keyword list: `ALL` or nothing for any; words, any of which may be a whole word of the thing's name (or equal its class ID); `P name` for exactly that name. See [Argument forms](#argument-forms). Matched against the item sold.

**Fires.** When a creature other than the host sells a matching item. A shopkeeper host hears only sales to itself.

**Does not fire.** The host selling.

**Hosts.** Shopkeeper mob, any mob (witness), item, room, area. A mob host runs it only while it is free (see [Busy hosts](#busy-hosts)).

**Codes.** `$n` the seller; `$t` and `$i` the host; `$o` and `$p` the item sold.

Example:

```text
SELL_PROG relic
say Where did you find this? Say nothing of it to the guard.
~
```

**Notes.** Queued.

### LOOK_PROG

*inherited from CoffeeMud. Table position 39. Runs: queued.*

Syntax:

```text
LOOK_PROG [ALL | P name | word word ...]
```

**Arguments.** Keyword list: `ALL` or nothing for any; words, any of which may be a whole word of the thing's name (or equal its class ID); `P name` for exactly that name. See [Argument forms](#argument-forms). Matched against the thing looked at.

**Fires.** When a creature other than the host looks at something the host hears. The match is against the name of the thing looked at, not the host. `LOOK_PROG` with no argument or `ALL` therefore hears every look in range. Separately, when a character looks at a keyword detail of a thing that has the `Prop_Details` property, that thing's own LOOK_PROG blocks whose argument is `100` or exactly the keyword run, with the keyword in `$g`.

**Does not fire.** Looks by the host itself.

**Hosts.** Mob, item, room, area, exit. A mob host runs it only while it is free (see [Busy hosts](#busy-hosts)).

**Codes.** `$n` the looker; `$t` the thing looked at; `$o` and `$p` the host item; `$g` the matched word (or the detail keyword).

Example:

```text
LOOK_PROG bell
mpecho The bell rings once on its own as $n studies it.
~
```

**Notes.** To react to a look at the host itself, give the host's own name words. Queued.

### LLOOK_PROG

*inherited from CoffeeMud. Table position 40. Runs: queued.*

Syntax:

```text
LLOOK_PROG [ALL | P name | word word ...]
```

**Arguments.** Keyword list: `ALL` or nothing for any; words, any of which may be a whole word of the thing's name (or equal its class ID); `P name` for exactly that name. See [Argument forms](#argument-forms).

**Fires.** As [LOOK_PROG](#look_prog), for the examine action (`EXAMINE`).

**Does not fire.** Plain looks.

**Hosts.** As LOOK_PROG.

**Codes.** As LOOK_PROG: `$t` the thing examined, `$o` and `$p` the host item.

Example:

```text
LLOOK_PROG all
mpechoat $n Scratched into the underside: HE WAS HERE.
~
```

**Notes.** Queued.

### READ_PROG

*added by CoffeeSUD. Table position 87. Runs: before.*

Syntax:

```text
READ_PROG [ALL | P phrase | word word ... | PAGE number-or-title]
```

**Arguments.** `ALL` or nothing: any reading. Words or `P phrase`: the thing's name must contain them. `PAGE n` or `PAGE title` fires for that page of a paged book (GenTome) when it is the page being read.

**Fires.** Just before the reading, so the script can replace the usual text. On a thing, only its own reading. On its holder, room or area, any reading that fits.

**Does not fire.** Readings of other things (on an item host).

**Hosts.** Item (the thing read), its holder mob, room, area. A mob host runs it only while it is free (see [Busy hosts](#busy-hosts)).

**Codes.** `$n` the reader; `$t` and `$o` the thing read; `$p` the host item; `$g` the thing's name.

Example:

```text
READ_PROG sign
mpechoat $n The paint is fresh: "NO SAILING AFTER DARK. - Harbourmaster."
return cancel
~
```

**Notes.** `RETURN CANCEL` keeps the thing's own text back. Fixture: `st19-read.js`.

### OPEN_PROG

*inherited from CoffeeMud. Table position 34. Runs: queued.*

Syntax:

```text
OPEN_PROG [ALL | P name | word word ...]
```

**Arguments.** Keyword list: `ALL` or nothing for any; words, any of which may be a whole word of the thing's name (or equal its class ID); `P name` for exactly that name. See [Argument forms](#argument-forms). Matched against the door or container.

**Fires.** When another creature opens a matching door or container. On a door (exit) or container host, only when that thing itself is opened; on a mob, room or area, for any matching opening it witnesses.

**Does not fire.** The host opening things.

**Hosts.** Mob (witness), item (container), exit (door), room, area. A mob host runs it only while it is free (see [Busy hosts](#busy-hosts)).

**Codes.** `$n` who opened it; `$t` the door or container; `$o` the container item (or the host item); `$p` the host item.

Example:

```text
OPEN_PROG chest
mpecho The chest lid creaks and a cold draught comes out.
~
```

**Notes.** Queued; it cannot stop the opening.

### CLOSE_PROG

*inherited from CoffeeMud. Table position 35. Runs: queued.*

Syntax:

```text
CLOSE_PROG [ALL | P name | word word ...]
```

**Arguments.** Keyword list: `ALL` or nothing for any; words, any of which may be a whole word of the thing's name (or equal its class ID); `P name` for exactly that name. See [Argument forms](#argument-forms).

**Fires.** As [OPEN_PROG](#open_prog), for closing.

**Hosts.** As OPEN_PROG.

**Codes.** As OPEN_PROG.

Example:

```text
CLOSE_PROG door
mpecho The latch drops with a heavy sound.
~
```

### LOCK_PROG

*inherited from CoffeeMud. Table position 36. Runs: queued.*

Syntax:

```text
LOCK_PROG [ALL | P name | word word ...]
```

**Arguments.** Keyword list: `ALL` or nothing for any; words, any of which may be a whole word of the thing's name (or equal its class ID); `P name` for exactly that name. See [Argument forms](#argument-forms).

**Fires.** As [OPEN_PROG](#open_prog), for locking.

**Hosts.** As OPEN_PROG.

**Codes.** As OPEN_PROG.

Example:

```text
LOCK_PROG gate
say Locked. Good. Nobody comes in tonight.
~
```

### UNLOCK_PROG

*inherited from CoffeeMud. Table position 37. Runs: queued.*

Syntax:

```text
UNLOCK_PROG [ALL | P name | word word ...]
```

**Arguments.** Keyword list: `ALL` or nothing for any; words, any of which may be a whole word of the thing's name (or equal its class ID); `P name` for exactly that name. See [Argument forms](#argument-forms).

**Fires.** As [OPEN_PROG](#open_prog), for unlocking.

**Hosts.** As OPEN_PROG.

**Codes.** As OPEN_PROG.

Example:

```text
UNLOCK_PROG vault
mpgoto Treasury#9
mpecho The alarm bell rings out.
~
```

### USE_PROG

*added by CoffeeSUD. Table position 112. Runs: now.*

Syntax:

```text
USE_PROG [ALL | word word ...]
```

**Arguments.** Nothing or `ALL`: any use. Words: any of them must be found in the name of the **other** thing in the use.

**Fires.** When a character types `USE x` or `USE x ON y`, or `TOUCH x`, on either thing involved. The first script that fits takes the action and the game's combination table (world/combinations.txt) is not consulted.

**Does not fire.** `COMBINE` (that is COMBINE_PROG).

**Hosts.** Either thing of the use (item or mob).

**Codes.** `$n` who used it; `$o` the first thing; `$t` the second (or the first, alone).

Example:

```text
USE_PROG crowbar
mpecho The crowbar bites into the lock and it gives.
mpunlock north
~
```

**Notes.** Runs at once. Fixture: `i10-use.js`. See [Items and crafting](07-items-crafting.md).

### COMBINE_PROG

*added by CoffeeSUD. Table position 113. Runs: now.*

Syntax:

```text
COMBINE_PROG [ALL | word word ...]
```

**Arguments.** As [USE_PROG](#use_prog): words are looked for in the other thing's name.

**Fires.** When a character types `COMBINE x WITH y`, on either thing.

**Does not fire.** USE and TOUCH.

**Hosts.** Either thing of the combination (item or mob).

**Codes.** `$n` who combined; `$o` the first thing; `$t` the second.

Example:

```text
COMBINE_PROG gem
mpecho The gem settles into the ring with a hum.
~
```

**Notes.** Runs at once. Fixture: `i10-use.js`.

### SWITCH_PROG

*added by CoffeeSUD. Table position 111. Runs: now.*

Syntax:

```text
SWITCH_PROG [state | ALL]
```

**Arguments.** A state name of the switch (`up`, `down`, `on`...), or `ALL` or nothing for any.

**Fires.** When a thing carrying the Prop_Switch property changes state. The switch's own scripts hear it, and the scripts of its room.

**Does not fire.** Switches without Prop_Switch.

**Hosts.** The switch (item or exit) and its room.

**Codes.** `$n` who moved it; `$t` and `$o` the switch; `$0` the new state.

Example:

```text
SWITCH_PROG up
mpecho The portcullis rattles upward.
mpunlock north
~
```

**Notes.** Runs at once. `SWITCHSTATE(thing)` reads a switch from a script. Fixture: `i9-switch.js`. See [Properties reference](23-properties-reference.md).

### SET_PROG

*added by CoffeeSUD. Table position 114. Runs: now.*

Syntax:

```text
SET_PROG [set id | ALL]
```

**Arguments.** The set id of the Prop_ItemSet pieces, or `ALL` or nothing.

**Fires.** When a wearer gains or loses a tier bonus of an item set.

**Does not fire.** Tiers that did not change.

**Hosts.** The piece the tier's properties are on (item) and the wearer (mob).

**Codes.** `$n` and `$t` the wearer; `$o` the piece; `$0` the tier number; `$1` ON or OFF.

Example:

```text
SET_PROG dragonbone
mpechoat $n The bone plates sing in unison.
~
```

**Notes.** Runs at once. `SETCOUNT($n set)` counts the pieces. Fixture: `i6-itemset.js`.

### DESTROY_PROG

*added by CoffeeSUD. Table position 94. Runs: now.*

Syntax:

```text
DESTROY_PROG [ALL | reason | item words]
```

**Arguments.** `ALL` or nothing: any. A reason: `JUNKED` (removed by MPJUNK) or `DECAY` (rotted away). Otherwise words found in the item's name.

**Fires.** At once, as the item goes: on the item itself, the room it is in, and its carrier.

**Does not fire.** Items not in a room (the script cannot find a place).

**Hosts.** Item, room, mob (carrier).

**Codes.** `$n` a stand-in mob named "the world"; `$t` and `$o` the item; `$g` the reason (empty if none).

Example:

```text
DESTROY_PROG JUNKED
mpecho The cup crumbles to dust in $n's hand.
~
```

**Notes.** The item's own scripts are gone just after. Only JUNKED and DECAY are ever set as reasons by the engine. Fixture: `st27-destroy.js`.

### DECAY_PROG

*added by CoffeeSUD. Table position 95. Runs: now.*

Syntax:

```text
DECAY_PROG
```

**Arguments.** None.

**Fires.** When an item is destroyed because its expiry time passed (food spoils, a summoned item fades), in addition to DESTROY_PROG.

**Does not fire.** Items destroyed for other reasons.

**Hosts.** Item, room, mob (carrier).

**Codes.** `$n` a stand-in named "the world"; `$t` and `$o` the item; `$g` DECAY.

Example:

```text
DECAY_PROG
mpecho The apple blackens and slumps into a puddle.
~
```

**Notes.** Runs at once. Fixture: `st27-destroy.js`.

## Combat and encounters

### FIGHT_PROG

*inherited from CoffeeMud. Table position 7. Runs: tick.*

Syntax:

```text
FIGHT_PROG [chance | zapper mask]
```

**Arguments.** A percent chance rolled each tick, or a zapper mask tested on the host (for an item, on its carrier).

**Fires.** On each tick of the host's script engine while the host mob is fighting. For an item, while its carrier is fighting.

**Does not fire.** Hosts not in combat; a dead host.

**Hosts.** Mob; item carried by a fighter.

**Codes.** `$n` the host's current opponent (for an item, its carrier); `$t` and `$i` the host mob; `$o` the host item. Runs at once, not queued.

Example:

```text
FIGHT_PROG 20
mpkill $r
say You will not leave this hall alive!
~
```

**Notes.** It does not need the host to be "free": it is the one trigger meant for fighting mobs. For a special attack with a visible wind-up see [Combat and encounters](29-combat-and-encounters.md).

### HITPRCNT_PROG

*inherited from CoffeeMud. Table position 11. Runs: tick.*

Syntax:

```text
HITPRCNT_PROG percent
```

**Arguments.** A whole number: the percent of maximum hit points. It fires while current hit points are at or below that share.

**Fires.** On every tick of the script engine while the host is in combat and at or below the share, not once. Guard with a variable to make it fire once.

**Does not fire.** Hosts not fighting; hosts above the share.

**Hosts.** Mob; item carried by a fighter (the carrier's hit points are used).

**Codes.** `$n` the host's opponent (the carrier, for an item); `$t` and `$i` the host; `$o` the host item. Runs at once.

Example:

```text
HITPRCNT_PROG 25
if !var($i RAGED)
  mpsetvar $i RAGED yes
  mpecho $i howls and its eyes go red.
endif
~
```

**Notes.** Compare [PHASE_PROG](#phase_prog), which fires once when a boss enters a phase.

### DAMAGE_PROG

*inherited from CoffeeMud. Table position 26. Runs: now.*

Syntax:

```text
DAMAGE_PROG
```

**Arguments.** None.

**Fires.** When the scripted mob (or the carrier of a scripted item) takes damage, or the scripted item is the weapon that deals it. Runs at once, as the damage message executes.

**Does not fire.** Damage to others.

**Hosts.** Mob, item.

**Codes.** `$n` the attacker; `$t` the one damaged; `$i` the damaged mob; `$o` the host item; `$p` the weapon used (if an item); `$g` the amount of damage.

Example:

```text
DAMAGE_PROG
if ispc($n)
  mpecho $i grunts: "Is that all?"
endif
~
```

**Notes.** It does not need the host to be free. Spells deliver damage with no `$p`.

### DEATH_PROG

*inherited from CoffeeMud. Table position 10. Runs: now.*

Syntax:

```text
DEATH_PROG [@GLOBAL [zapper mask]]
```

**Arguments.** With no argument: the host's own death. With `@GLOBAL`: any death anywhere, heard once, optionally only of one who fits the mask (tested on the dead).

**Fires.** As the death message executes: it is the dying mob's last word. The script runs at once, and the host still dies afterward whatever it does. On an item, room or area it fires for any death it hears; an item carried by a mob fires for the carrier's death.

**Does not fire.** A DEATH_PROG that restores hit points does not save the mob.

**Hosts.** Mob, item, room, area; any host with @GLOBAL.

**Codes.** `$n` the killer (the creature that dealt the blow, else the last to hurt the mob if in the room, else the dead mob); `$t` and `$i` the dead mob; `$o` the host item.

Example:

```text
DEATH_PROG
mpoload CATALOG the Sunblade
mpecho A bell tolls under the water as $i falls.
mpquestwin $n the drowned bell
~
```

**Notes.** Note `$i` names the dying mob even when the host is some other thing. Because the mob dies after the script, use `MPGOTO` to move it to die elsewhere, or `MPJUNK` its gear. Fixture: `st12-global.js`.

### KILL_PROG

*inherited from CoffeeMud. Table position 44. Runs: now.*

Syntax:

```text
KILL_PROG [@GLOBAL [zapper mask]]
```

**Arguments.** As DEATH_PROG. With `@GLOBAL`, the mask is tested on the killer.

**Fires.** When the host mob deals the blow that kills a creature as the death message executes. On an item, room or area, any kill heard.

**Does not fire.** Deaths by other means.

**Hosts.** Mob, item, room, area; any host with @GLOBAL.

**Codes.** `$n` the killer; `$t` and `$i` the one who died; `$o` the host item.

Example:

```text
KILL_PROG
say One less thief in the world.
~
```

**Notes.** As in DEATH_PROG, `$i` is the victim.

### COMBAT_PROG

*added by CoffeeSUD. Table position 78. Runs: tick / queued.*

Syntax:

```text
COMBAT_PROG START|END|FLEE|ANY [zapper mask]
```

**Arguments.** The kind of event; `ANY` is all of them. An optional zapper mask tested on the opponent.

**Fires.** `START` when the host first enters a fight; `END` when a fight is over with both sides alive; `FLEE` when an opponent flees (on a mob or its carried item: someone fighting the host). On a room, any fight there.

**Does not fire.** A fight that ends because the host died.

**Hosts.** Mob; item carried by a mob; room (any fight in it). START and END are noticed at the host's next tick.

**Codes.** `$n` and `$t` the opponent (the one who flees, for FLEE); `$i` the host; `$g` START, END or FLEE.

Example:

```text
COMBAT_PROG START
say Guards! Guards!
~
COMBAT_PROG END
say Hah. Fetch a cloth.
~
```

**Notes.** Fixture: `st15-combat.js`.

### PHASE_PROG

*added by CoffeeSUD. Table position 105. Runs: now.*

Syntax:

```text
PHASE_PROG [number | ALL]
```

**Arguments.** A phase number, or `ALL` or nothing for any.

**Fires.** When the mob's BossPhases behaviour enters that phase.

**Does not fire.** Phases not named.

**Hosts.** The boss mob.

**Codes.** `$n` the boss's opponent (or the boss itself); `$t` and `$i` the boss; `$g` and `$0` the phase number.

Example:

```text
PHASE_PROG 2
mpecho The lich tears its own phylactery from its ribs, and the air darkens.
mpcast Spell_Darkness $i
~
```

**Notes.** See [Combat and encounters](29-combat-and-encounters.md). Fixture: `c3-phases.js`.

### SURRENDER_PROG

*added by CoffeeSUD. Table position 102. Runs: now.*

Syntax:

```text
SURRENDER_PROG
```

**Arguments.** None.

**Fires.** When the host's Morale behaviour makes it surrender.

**Does not fire.** A mob that flees or fights on.

**Hosts.** The surrendering mob.

**Codes.** `$n` and `$t` one it fought; `$i` the host.

Example:

```text
SURRENDER_PROG
say Mercy! Take my purse and let me go!
~
```

**Notes.** Fixture: `c2-morale.js`.

### YIELD_PROG

*added by CoffeeSUD. Table position 103. Runs: now.*

Syntax:

```text
YIELD_PROG
```

**Arguments.** None.

**Fires.** When a killing blow is turned into a yield.

**Does not fire.** Ordinary deaths.

**Hosts.** The essential mob.

**Codes.** `$n` who beat it; `$t` and `$i` the host.

Example:

```text
YIELD_PROG
say Enough. You have beaten me fairly, $n. Spare me and I will remember it.
~
```

**Notes.** Fixture: `c17-essential.js`.

### DEFEAT_PROG

*added by CoffeeSUD. Table position 104. Runs: now.*

Syntax:

```text
DEFEAT_PROG
```

**Arguments.** None.

**Fires.** When a killing blow leaves the mob knocked out instead.

**Does not fire.** Ordinary deaths; yields.

**Hosts.** The essential mob.

**Codes.** `$n` who beat it; `$t` and `$i` the host.

Example:

```text
DEFEAT_PROG
mpecho $i slumps, out cold.
~
```

**Notes.** Fixture: `c17-essential.js`.

### RESET_PROG

*added by CoffeeSUD. Table position 101. Runs: now.*

Syntax:

```text
RESET_PROG
```

**Arguments.** None.

**Fires.** When the EncounterReset behaviour puts the mob back (home, healed) after its opponents left.

**Hosts.** The mob with EncounterReset.

**Codes.** `$n`, `$t` and `$i` the host.

Example:

```text
RESET_PROG
say The wind carries off the stench of blood. I am whole again.
~
```

**Notes.** Fixture: `c21-reset.js`.

### WAVE_PROG

*added by CoffeeSUD. Table position 106. Runs: now.*

Syntax:

```text
WAVE_PROG [number | ALL]
```

**Arguments.** A wave number from 1, or `ALL` or nothing for every wave.

**Fires.** When the WaveSpawner behaviour on the room brings the wave.

**Does not fire.** Other waves.

**Hosts.** The arena room and the arena's non-player-side NPCs there.

**Codes.** `$n` one of the party (or a stand-in); `$t` the host; `$g` and `$0` the wave number.

Example:

```text
WAVE_PROG 2
mpecho The announcer cries: "The second wave! The crowd is on its feet!"
~
```

**Notes.** Fixture: `c20-waves.js`.

### ARENAWIN_PROG

*added by CoffeeSUD. Table position 107. Runs: now.*

Syntax:

```text
ARENAWIN_PROG
```

**Arguments.** None.

**Fires.** When the last wave is beaten.

**Hosts.** As WAVE_PROG.

**Codes.** `$n` the winner (the party leader); `$t` the host; `$0` empty.

Example:

```text
ARENAWIN_PROG
mpecho The gate lifts. The crowd throws laurels.
mpoload CATALOG the Sunblade
~
```

**Notes.** Fixture: `c20-waves.js`.

### ARENALOSE_PROG

*added by CoffeeSUD. Table position 108. Runs: now.*

Syntax:

```text
ARENALOSE_PROG
```

**Arguments.** None.

**Fires.** When the party leaves the arena or falls in it before the last wave is beaten.

**Hosts.** As WAVE_PROG.

**Codes.** `$n` who started it; `$t` the host.

Example:

```text
ARENALOSE_PROG
mpecho The announcer shrugs: "Better luck next time."
~
```

**Notes.** Fixture: `c20-waves.js`.

### AMBUSH_PROG

*added by CoffeeSUD. Table position 109. Runs: now.*

Syntax:

```text
AMBUSH_PROG [SURPRISED | SPOTTED | AVOIDED | ALL]
```

**Arguments.** The outcome to hear, or `ALL`/nothing for every outcome.

**Fires.** When a Prop_Ambush on the room springs, with how it went.

**Does not fire.** Other outcomes.

**Hosts.** The room with Prop_Ambush and the NPCs there (the ambushers).

**Codes.** `$n` the one who came in; `$t` the host; `$g` and `$0` the outcome.

Example:

```text
AMBUSH_PROG SPOTTED
say Hold! It is a trap!
~
```

**Notes.** Fixture: `c23-ambush.js`.

### PARTYDEFEAT_PROG

*added by CoffeeSUD. Table position 110. Runs: now.*

Syntax:

```text
PARTYDEFEAT_PROG
```

**Arguments.** None.

**Fires.** When the whole party in the room is down (PLAYERDEATH's downed state) or dead. It decides what happens next: without one, the PARTYDEFEAT setting in `coffeemud.ini` does.

**Does not fire.** Parties with a living member.

**Hosts.** Room, area (the party's room and area only).

**Codes.** `$n` the last to fall; `$t` the host.

Example:

```text
PARTYDEFEAT_PROG
mpecho Shadowy figures drag you to the cells.
mptransfer all Cells#1
~
```

**Notes.** Fixture: `c8-downed.js`. See [Combat and encounters](29-combat-and-encounters.md).

### CAST_PROG

*inherited from CoffeeMud. Table position 49. Runs: queued.*

Syntax:

```text
CAST_PROG [ALL | P name | word word ...]
```

**Arguments.** Ability name or ID keywords as [Argument forms](#argument-forms): words (a word of the ability's name, or its ID), `P` for the exact name or ID, `ALL`. Speech in another language counts as that language.

**Fires.** When another creature uses a matching ability on the host mob. On a room or area, any matching use there.

**Does not fire.** The host's own casting (use [CASTING_PROG](#casting_prog)); teaching.

**Hosts.** Mob, room, area. Not items. A mob host runs it only while it is free (see [Busy hosts](#busy-hosts)).

**Codes.** `$n` the user; `$t` and `$i` the host mob; `$o` the host item; `$g` the matched word.

Example:

```text
CAST_PROG fire ice
say Fire at me? Fool!
~
CAST_PROG p Spell_Fireball
mpcast Spell_Shield $i
~
```

**Notes.** Queued. Compare AFFECT_PROG for effects arriving and leaving.

### CASTING_PROG

*inherited from CoffeeMud. Table position 50. Runs: queued.*

Syntax:

```text
CASTING_PROG [ALL | P name | word word ...]
```

**Arguments.** As CAST_PROG.

**Fires.** When the host mob itself uses a matching ability, a language it speaks in too. On a room or area, anyone's.

**Does not fire.** Others' casting.

**Hosts.** Mob, room, area. Not items.

**Codes.** `$n` the user; `$t` its target; `$i` the host mob; `$g` the matched word.

Example:

```text
CASTING_PROG Spell_Fireball
mpecho $i's staff flares orange.
~
```

**Notes.** Queued.

## Time and the calendar

### RAND_PROG

*inherited from CoffeeMud. Table position 5. Runs: tick.*

Syntax:

```text
RAND_PROG percent
```

**Arguments.** A whole percent chance rolled every tick of the script engine.

**Fires.** On each tick of the host's script engine, with that chance, as long as the host is alive. It ignores the host's state (asleep, fighting, following).

**Does not fire.** Hosts the game does not tick, such as a mob in an area with no players near.

**Hosts.** Mob, item, room, area, exit (anything the engine ticks).

**Codes.** `$n`, `$t` and `$i` the host (a stand-in mob for non-mob hosts); `$o` the host item; `$g` empty.

Example:

```text
RAND_PROG 5
if ispc($r)
  mpecho $i hums an old work song, off key.
endif
~
```

**Notes.** Many high-chance RAND_PROGs on many mobs slow the game; consider DELAY_PROG, TIME_PROG or EVERY_PROG. Runs at once in the tick. `MPSETINTERNAL NEEDPC TRUE` requires a player character in the host's room.

### DELAY_PROG

*inherited from CoffeeMud. Table position 16. Runs: tick.*

Syntax:

```text
DELAY_PROG min-ticks max-ticks
```

**Arguments.** Two whole numbers: the least and greatest number of engine ticks to wait. If the second is lower, it is raised to the first.

**Fires.** After a random number of ticks between the two, counted from when the engine first ticks the script, and again after each run with a fresh random wait.

**Does not fire.** While the host is dead.

**Hosts.** Any host the engine ticks.

**Codes.** `$n`, `$t` and `$i` the host; `$o` the host item.

Example:

```text
DELAY_PROG 10 20
mpecho The fire pops and settles.
~
```

**Notes.** The remaining wait is kept in a save. In turn-based time a tick passes when the party acts. `SCRIPT RESET host DELAY` starts the waits again.

### ONCE_PROG

*inherited from CoffeeMud. Table position 6. Runs: tick.*

Syntax:

```text
ONCE_PROG [BOOT]
```

**Arguments.** Nothing, or `BOOT`. Any other word is ignored (`ONCE_PROG 100` is common in examples).

**Fires.** On the first tick of the host's script engine. A saved game remembers that a ONCE_PROG ran, so it does not run again after a restore (unless the script text changed). With `BOOT`, it runs once each time the game loads, a restore included, to set up what a save does not keep.

**Does not fire.** Again, after it has run.

**Hosts.** Any host the engine ticks.

**Codes.** `$n`, `$t` and `$i` the host; `$o` the host item.

Example:

```text
ONCE_PROG
mpecho The old bell tolls once, as it has not in a hundred years.
~
ONCE_PROG BOOT
mpsetvar $i SHIFT 0
~
```

**Notes.** Only one ONCE_PROG per script engine is meaningful; use ONCE_PROG for ever-once, and a tattoo or variable for once per character. `SCRIPT RESET host ONCE` lets it run again.

### TIME_PROG

*inherited from CoffeeMud. Table position 14. Runs: tick.*

Syntax:

```text
TIME_PROG hour | range | DAWN | DAY | DUSK | NIGHT ...
```

**Arguments.** One or more of: a whole hour (the area clock's hour of day, from 0); a range `6-18`; a range with a step `6-18/3` (every third hour); or `DAWN`, `DAY`, `DUSK`, `NIGHT`, the hour that part of the day begins on the area's clock.

**Fires.** Once at the start of each listed hour of the host's area's clock (the host must be in a room).

**Does not fire.** A second time in the same hour.

**Hosts.** Any host the engine ticks.

**Codes.** `$n`, `$t` and `$i` the host; `$g` the hour.

Example:

```text
TIME_PROG DAWN
mpecho The baker throws open the shutters.
~
TIME_PROG 20-23/1
mpecho $i bars the door for the night.
~
```

**Notes.** The hour a script last ran is kept in a save. The number of hours in a day depends on the clock (CoffeeMud's default clock has 16 per day, not 24). Fixture: `st11-calendar.js`. See [World simulation](10-world-simulation.md).

### DAY_PROG

*inherited from CoffeeMud. Table position 15. Runs: tick.*

Syntax:

```text
DAY_PROG day | range ...
```

**Arguments.** Days of the month: whole numbers or ranges (`1-7`, `1-30/5`).

**Fires.** Once on each listed day of the month of the host's area's clock, when the engine next ticks.

**Does not fire.** A second time on the same day.

**Hosts.** Any host the engine ticks.

**Codes.** `$n`, `$t` and `$i` the host.

Example:

```text
DAY_PROG 1
mpecho It is rent day: the landlord's boots are on the stairs.
~
```

**Notes.** The day last run is kept in a save. Fixture: `st11-calendar.js`.

### EVERY_PROG

*added by CoffeeSUD. Table position 91. Runs: tick.*

Syntax:

```text
EVERY_PROG n [HOURS | DAYS | WEEKS | MONTHS] [CATCHUP]
```

**Arguments.** `n` a whole number of the unit (default HOURS). `CATCHUP` makes a run that was overdue (the area was passive, or the game not running) report how many intervals it missed.

**Fires.** When the area clock's hour count reaches the next due time. The first time the script is ticked it only schedules the first run. After a run, the next time is moved on by whole intervals.

**Does not fire.** Intervals that pass while the host cannot tick, unless CATCHUP (then once, with `$g` the count).

**Hosts.** Any host the engine ticks, in a room.

**Codes.** `$n`, `$t` and `$i` the host; `$g` 1, or the number of intervals missed with CATCHUP.

Example:

```text
EVERY_PROG 1 DAYS CATCHUP
mpsetvar $i STOCK ++
if var($i STOCK > 5)
  mpsetvar $i STOCK 5
endif
~
```

**Notes.** The next due time is saved. Compare DELAY_PROG (ticks, not game time) and TIME_PROG (a fixed hour). Fixture: `st24-every.js`.

### WEEKDAY_PROG

*added by CoffeeSUD. Table position 73. Runs: queued.*

Syntax:

```text
WEEKDAY_PROG weekday name ... | ANY
```

**Arguments.** Day-of-week names of the area clock (as `TIME` shows them), separated by spaces or commas; `ANY` for every day.

**Fires.** At the start of each new day of the clock the host's area is on.

**Does not fire.** Clocks with no weeks.

**Hosts.** Hosts in areas on the clock, and the world's script host.

**Codes.** `$n` a stand-in named "the sky"; `$t` and `$i` the host; `$g` the weekday now; `$0` the weekday before.

Example:

```text
WEEKDAY_PROG ANY
mpecho The market bell rings: it is $G, and the stalls open.
~
```

**Notes.** Queued. Fixture: `st11-calendar.js`.

### MONTH_PROG

*added by CoffeeSUD. Table position 71. Runs: queued.*

Syntax:

```text
MONTH_PROG number | month name ... | ANY
```

**Arguments.** Month numbers as the clock counts them, or month names, separated by spaces or commas; `ANY` for every change.

**Fires.** When the host's area clock enters one of the months.

**Does not fire.** Days within a month.

**Hosts.** Hosts in areas on the clock, and the world's script host.

**Codes.** `$n` a stand-in named "the sky"; `$t` and `$i` the host; `$g` the month number now; `$0` the month number before.

Example:

```text
MONTH_PROG ANY
mpecho The months turn. The market stalls change their wares.
~
```

**Notes.** Queued. Fixture: `st11-calendar.js`.

### SEASON_PROG

*added by CoffeeSUD. Table position 70. Runs: queued.*

Syntax:

```text
SEASON_PROG SPRING | SUMMER | FALL | WINTER | ANY
```

**Arguments.** A season (or several), or `ANY`.

**Fires.** When the host's area clock enters the season.

**Hosts.** Hosts in areas on the clock, and the world's script host.

**Codes.** `$n` a stand-in named "the sky"; `$g` the new season; `$0` the season before.

Example:

```text
SEASON_PROG WINTER
mpecho Snow closes the pass. The caravans will not come until spring.
~
```

**Notes.** Queued. Fixture: `st11-calendar.js`.

### YEAR_PROG

*added by CoffeeSUD. Table position 72. Runs: queued.*

Syntax:

```text
YEAR_PROG year | ANY
```

**Arguments.** A year number, or `ANY`.

**Fires.** When the host's area clock enters the year.

**Hosts.** Hosts in areas on the clock, and the world's script host.

**Codes.** `$n` a stand-in named "the sky"; `$g` the year now; `$0` the year before.

Example:

```text
YEAR_PROG ANY
mpecho A new year dawns and the bells ring it in.
~
```

**Notes.** Queued. Fixture: `st11-calendar.js`.

### TURN_PROG

*added by CoffeeSUD. Table position 67. Runs: queued.*

Syntax:

```text
TURN_PROG [every-n] [zapper mask]
```

**Arguments.** A number: fire every that many turns (the first word if numeric). The rest is a zapper mask tested on the party leader.

**Fires.** At the end of each turn: a party's turn in turn-based time, or a player's command that took time in real time. The count of turns since the game began is kept in the save.

**Does not fire.** Turns of a party in another area (for hosts not world-wide).

**Hosts.** Hosts in the leader's area, the area and its parents, and the world's script host.

**Codes.** `$n` the party leader; `$t` the host; `$g` the turn count since the game began.

Example:

```text
TURN_PROG 3
mpecho Three more turns, and the candle is gone.
~
```

**Notes.** Queued. `TURNS()` and `ISTURNBASED()` read the count and the mode (see [MOBPROG functions](28-mobprog-functions.md)). Fixture: `st6-game.js`.

### WEATHER_PROG

*added by CoffeeSUD. Table position 63. Runs: queued.*

Syntax:

```text
WEATHER_PROG weather | ANY [old weather]
```

**Arguments.** The new weather, or `ANY`: CLEAR, CLOUDY, WINDY, RAIN, THUNDERSTORM, SNOW, HAIL, HEAT, SLEET, BLIZZARD, DUST, DROUGHT, COLD, FOG. An optional second word limits it to a change from that weather.

**Fires.** When the area's weather changes. A weather put back as it was by a restore fires nothing.

**Does not fire.** Time of day or season changes (use EXECMSG_PROG TODCHANGE or DATECHANGE).

**Hosts.** Every host in the area: its rooms, items, mobs and what they carry, the area and its parents.

**Codes.** `$n` a stand-in named "the weather"; `$t` and `$i` the host; `$g` the new weather; `$0` the old; `$1` the forecast.

Example:

```text
WEATHER_PROG THUNDERSTORM
say Storm's coming. Lines in, lads!
~
```

**Notes.** Queued. `EXECMSG_PROG WEATHERCHANGE ALL` hears the same change with the words "new old forecast" in the message. Fixture: `st4-weather.js`. See [World simulation](10-world-simulation.md).

### MOON_PROG

*added by CoffeeSUD. Table position 64. Runs: queued.*

Syntax:

```text
MOON_PROG phase | ANY
```

**Arguments.** A phase: NEW, WAXCRESCENT, WAXQUARTER, WAXGIBBOUS, FULL, WANEGIBBOUS, WANEQUARTER, WANECRESCENT, BLUE (a blue moon), or `ANY`.

**Fires.** On the new day when the phase of the moon over the host's area becomes the one named.

**Does not fire.** Days when the phase stays the same.

**Hosts.** Every host in the area (as WEATHER_PROG).

**Codes.** `$n` a stand-in named "the sky"; `$t` and `$i` the host; `$g` the phase; `$0` the phase before.

Example:

```text
MOON_PROG FULL
mpecho $i throws back its head and howls.
~
```

**Notes.** Queued. Fixture: `st4-weather.js`.

## Quests and achievements

### QUEST_TIME_PROG

*inherited from CoffeeMud. Table position 13. Runs: tick.*

Syntax:

```text
QUEST_TIME_PROG quest minutes
```

**Arguments.** The quest's name (quote it if it has spaces: `'my quest'`), or `*` for the quest that gave the script. Then minutes: 0 or more, the minutes left; `-1`, each time a quest step is done; `-2` (or any less than -1), when the quest takes its scripts away at the end.

**Fires.** Once, when the quest is running, has a duration, and its minutes remaining have dropped to the number or below. `-1` and `-2` run only on the objects the quest gave the script to (and on a player's tracking script at MPENDQUEST or QUESTS DROP).

**Does not fire.** Quests that are not running; quests with no duration (for the countdown form).

**Hosts.** Any host (the countdown looks the quest up by name). For `-1`, `-2`: objects the quest gave the script.

**Codes.** `$n`, `$t` and `$i` the host.

Example:

```text
QUEST_TIME_PROG 'the drowned bell' 20
say Twenty minutes! The tide will not wait.
~
QUEST_TIME_PROG * -2
mpecho The quest is over and the room goes quiet.
~
```

**Notes.** The run is remembered, so it does not repeat. For start, win and end seen by every host use [QUEST_PROG](#quest_prog). See [Quests](08-quests.md).

### QUEST_PROG

*added by CoffeeSUD. Table position 65. Runs: queued.*

Syntax:

```text
QUEST_PROG START|ACCEPT|STEP|WIN|FAIL|END|ANY quest|* [detail]
```

**Arguments.** The event (`ANY` for all); the quest's name (one word, or in quotes) or `*` for any quest; an optional detail that must equal the event's detail: the step number for STEP; for FAIL and END the outcome (WON, EXPIRED, STOPPED, DROPPED, or the text given to MPQUESTFAIL).

**Fires.** START when a quest starts; ACCEPT when a player accepts; STEP at each step; WIN when MPQUESTWIN names a winner; FAIL when the quest ends with no winner, or fails for a character; END when it ends (outcome in the detail).

**Does not fire.** Events of other quests.

**Hosts.** Every host with the trigger, wherever it is.

**Codes.** `$n` the player (the accepter, winner or one who dropped it) or a stand-in named "the quest"; `$t` the host; `$g` the quest's name; `$0` the detail.

Example:

```text
QUEST_PROG WIN goblin_raid
mpecho The crier shouts that $n drove off the goblins!
~
QUEST_PROG END goblin_raid EXPIRED
say The stables burned. The inn is closed tonight.
~
```

**Notes.** Queued. Fixture: `st5-quest.js`. See [Quests](08-quests.md).

### QUESTFAIL_PROG

*added by CoffeeSUD. Table position 98. Runs: queued.*

Syntax:

```text
QUESTFAIL_PROG quest|*
```

**Arguments.** The quest's name, or `*` or `ALL` for any.

**Fires.** When a quest fails for a character (MPQUESTFAIL, or the death of a KEYPLAYER FAIL mob). The character's own quest scripts hear it too.

**Does not fire.** Quest wins; quests ending for everyone (use QUEST_PROG FAIL).

**Hosts.** Every host with the trigger, wherever it is.

**Codes.** `$n` the character; `$t` the host; `$g` the quest's name; `$0` why.

Example:

```text
QUESTFAIL_PROG oskar
say Oskar is dead, and the road home is closed to you, $n.
~
```

**Notes.** Queued. Fixture: `q4-status.js`.

### QUESTTIMEOUT_PROG

*added by CoffeeSUD. Table position 99. Runs: now.*

Syntax:

```text
QUESTTIMEOUT_PROG [quest|*]
```

**Arguments.** A quest name, or `*`/`ALL`. With none, it fits only the script the quest gave that character.

**Fires.** When the deadline set by MPQTIMER comes, on the character's own scripts. If no QUESTTIMEOUT_PROG hears it, the quest fails for the character, EXPIRED.

**Does not fire.** Deadlines of other characters.

**Hosts.** The character's own scripts (mob).

**Codes.** `$n` the character; `$t` the host; `$g` and `$0` the quest name.

Example:

```text
QUESTTIMEOUT_PROG
mpechoat $n You took too long. The bell sinks back into the mud.
mpquestfail $n bell too late
~
```

**Notes.** Runs at once, busy or not. Fixture: `q11-deadline.js`.

### ACHIEVE_PROG

*added by CoffeeSUD. Table position 96. Runs: queued.*

Syntax:

```text
ACHIEVE_PROG id|* [zapper mask]
```

**Arguments.** An achievement ID, or `*`/`ALL`; then an optional zapper mask on the earner.

**Fires.** When a character earns an achievement.

**Does not fire.** Other achievements.

**Hosts.** Every host with the trigger, wherever it is.

**Codes.** `$n` the earner; `$t` the host; `$g` the achievement's ID.

Example:

```text
ACHIEVE_PROG FIREWALKER
say Word has reached us, $n. Firewalker!
~
```

**Notes.** Queued. Fixture: `st29-achieve.js`.

## Characters: login, levels, skills, factions, clans and the party

### LOGIN_PROG

*inherited from CoffeeMud. Table position 29. Runs: queued.*

Syntax:

```text
LOGIN_PROG [chance | zapper mask]
```

**Arguments.** A percent chance or a zapper mask tested on the character.

**Fires.** When a character logs into the world, wherever the host is: in CoffeeSUD at a new game and when a saved game is restored. Cloaked archons do not trigger it.

**Hosts.** Any host (registered for logins when the script is parsed).

**Codes.** `$n` the character; `$t` and `$i` the host.

Example:

```text
LOGIN_PROG 100
mpechoat $n You wake in the Gilded Goose. The fire has burned low.
~
```

**Notes.** For the game's own events (new game, restore, save) prefer [GAME_PROG](#game_prog). The audit says a SWITCH to another character also sends a login (not re-checked). Fixture: `st1-global-triggers.js`.

### LOGOFF_PROG

*inherited from CoffeeMud. Table position 30. Runs: queued.*

Syntax:

```text
LOGOFF_PROG [chance | zapper mask]
```

**Arguments.** As LOGIN_PROG.

**Fires.** When a character quits the game (QUIT or LOGOFF), or a SWITCH leaves it.

**Does not fire.** Cloaked archons.

**Hosts.** Any host (registered when parsed).

**Codes.** `$n` the character; `$t` and `$i` the host.

Example:

```text
LOGOFF_PROG 100
mpecho The fire gutters as $n leaves.
~
```

**Notes.** Fixture: `st1-global-triggers.js`.

### LEVEL_PROG

*inherited from CoffeeMud. Table position 32. Runs: now.*

Syntax:

```text
LEVEL_PROG [GAIN | LOSS | ANY] [chance | zapper mask]
```

**Arguments.** `GAIN` (the default), `LOSS` or `ANY`, then an optional percent chance or mask tested on the character.

**Fires.** When a character changes level, anywhere in the game, heard once per host.

**Does not fire.** Changes of the wrong kind; cloaked archons.

**Hosts.** Any host (registered when parsed).

**Codes.** `$n` the character; `$t` and `$i` the host; `$g` the new level; `$0` to `$9` the words of the trigger line.

Example:

```text
LEVEL_PROG GAIN 100
mpechoat $n The old trainer nods: "Level $g, is it? Good."
~
```

**Notes.** Fixtures: `st17-level.js`.

### SKILL_PROG

*added by CoffeeSUD. Table position 61. Runs: queued.*

Syntax:

```text
SKILL_PROG [@GLOBAL] LEARN|IMPROVE|MASTER|FAIL|ANY skill|* [zapper mask]
```

**Arguments.** The event; the skill's ID or name (or a word of its name; `*` or `ALL` for any); an optional zapper mask tested on the character. `@GLOBAL` before the event hears it anywhere.

**Fires.** LEARN when a character gains the skill (taught, bought, level grants, a new ability); IMPROVE on each gain in proficiency; MASTER on reaching the highest; FAIL when its use fails. Only player characters.

**Does not fire.** Skills of mobs; race or clan grants that are not gained by addAbility.

**Hosts.** The room, its items and mobs, their carried items, and the area and parents; any host with @GLOBAL.

**Codes.** `$n` the character; `$t` the skill; `$g` its proficiency now.

Example:

```text
SKILL_PROG LEARN Spell_Fireball
say Fireball! Then you must see the Ember Tower, $n.
~
SKILL_PROG MASTER Skill_Climb
mpechoat $n You have it now: no cliff will stop you.
~
```

**Notes.** Queued. Fixtures: `st2-skill.js`, `st12-global.js`.

### AGE_PROG

*added by CoffeeSUD. Table position 115. Runs: now.*

Syntax:

```text
AGE_PROG [BIRTHDAY | CATEGORY | ALL | age category]
```

**Arguments.** `BIRTHDAY` each birthday; `CATEGORY` only when the age category changes; an age category name (INFANT, TODDLER, CHILD, YOUNGADULT, MATURE, MIDDLEAGED, OLD, VENERABLE, ANCIENT) for any of these events while in that category; `ALL` or nothing for any.

**Fires.** When a player character has a birthday (the character's age rises by one).

**Does not fire.** Non-player mobs.

**Hosts.** The character and everything around it: its room, items, mobs, area.

**Codes.** `$n` and `$t` the character; `$g` and `$0` the age category; `$1` the age in years; `$2` BIRTHDAY or CATEGORY.

Example:

```text
AGE_PROG CATEGORY
mpechoat $n You feel your years settle: you are now $0.
~
```

**Notes.** Fixture: `p29-ageing.js`.

### FACTION_PROG

*added by CoffeeSUD. Table position 77. Runs: queued.*

Syntax:

```text
FACTION_PROG [@GLOBAL] faction [range | ANY | UP | DOWN] [zapper mask]
```

**Arguments.** The faction's ID or name; then `ANY` (or nothing) for any change, `UP`, `DOWN`, or a range name that fires only when the standing enters that range; then an optional mask on the character. `@GLOBAL` before the faction hears it anywhere.

**Fires.** When a faction value of a character changes.

**Does not fire.** Changes of other factions.

**Hosts.** The room and its contents and area, or any host with @GLOBAL.

**Codes.** `$n` the character; `$g` the new range; `$0` the amount of change; `$1` the range before; `$2` the faction ID.

Example:

```text
FACTION_PROG alignment UP
mpechoat $n The priest nods: your standing grows.
~
```

**Notes.** Queued. Fixture: `st14-faction.js`. See [Society](09-society.md).

### CLAN_PROG

*added by CoffeeSUD. Table position 74. Runs: queued.*

Syntax:

```text
CLAN_PROG JOIN|LEAVE|PROMOTE|DEMOTE|EXILE|RELATION|CONQUER|LOSEAREA|LEVEL|DUESLATE|ALL clan|ALL
```

**Arguments.** The event, then the clan's ID or name, or `ALL` or nothing for any clan.

**Fires.** JOIN, LEAVE, PROMOTE, DEMOTE, EXILE of a member; RELATION when its relation with another clan changes; CONQUER or LOSEAREA when it wins or loses an area; LEVEL when the clan changes level; DUESLATE when a member's dues are overdue.

**Does not fire.** Other clans.

**Hosts.** Hosts in the member's room, the clan's recall room, the area won or lost, and the world's script host.

**Codes.** `$n` the member (or a stand-in named for the clan); `$t` the area for CONQUER and LOSEAREA; `$g` the new rank, relation or level (the amount owed for DUESLATE); `$0` the event; `$1` the clan; `$2` the detail; `$3` the other clan, the area, or the DUESLATE step.

Example:

```text
CLAN_PROG JOIN Brenn_Guard
mpechoat $n The sergeant salutes: "Welcome to the Guard."
~
```

**Notes.** Queued. Fixture: `st9-clan.js`. See [Society](09-society.md).

### PARTY_PROG

*added by CoffeeSUD. Table position 69. Runs: queued.*

Syntax:

```text
PARTY_PROG JOIN|LEAVE|REST|LEAD|SWITCH|ANY [zapper mask]
```

**Arguments.** The event, or `ANY`, then an optional mask on the member.

**Fires.** When a character joins or leaves the party, rests, takes the lead, or the player switches to a member.

**Does not fire.** Other events.

**Hosts.** Every host with the trigger, wherever it is.

**Codes.** `$n` the member; `$t` the leader; `$g` the event.

Example:

```text
PARTY_PROG JOIN
mpecho The old sage squints at $n: "Another for the road."
~
```

**Notes.** Queued. Fixture: `st10-party.js`. See [Saves and party](31-saves-and-party.md).

### CRAFT_PROG

*added by CoffeeSUD. Table position 75. Runs: queued.*

Syntax:

```text
CRAFT_PROG skill|* item-word|ALL [RUINED|SUCCESS]
```

**Arguments.** The crafting skill's ID or name (or `*`); one word (quote several) to find in the made item's name, or `ALL`; optionally `RUINED` or `SUCCESS`.

**Fires.** When a crafter makes a matching thing.

**Does not fire.** Ruined results when SUCCESS is asked, and the reverse.

**Hosts.** Hosts that hear the crafting message: the room and area, and things and mobs around.

**Codes.** `$n` the crafter; `$t` and `$o` the thing made; `$p` the host item; `$g` the skill.

Example:

```text
CRAFT_PROG Weaponsmithing sword SUCCESS
say A fine blade, $n. The guard captain will pay well.
~
```

**Notes.** Queued. Fixture: `st13-craft.js`. See [Items and crafting](07-items-crafting.md).

### RECIPE_PROG

*added by CoffeeSUD. Table position 76. Runs: queued.*

Syntax:

```text
RECIPE_PROG skill|* item-words|ALL
```

**Arguments.** The skill's ID or name (or `*`), then words to find in the name of the thing the recipe makes, or `ALL`.

**Fires.** When a crafter learns a matching recipe.

**Hosts.** As CRAFT_PROG.

**Codes.** `$n` the crafter; `$t` and `$o` the thing made; `$g` the skill.

Example:

```text
RECIPE_PROG * dagger
say So you have the dagger pattern now. Sharp.
~
```

**Notes.** Queued. Fixture: `st13-craft.js`.

### AFFECT_PROG

*added by CoffeeSUD. Table position 93. Runs: queued.*

Syntax:

```text
AFFECT_PROG ADD|REMOVE|ANY ability|*
```

**Arguments.** The event (`ANY` for both) and the effect's ID, a word of its name, or `*`.

**Fires.** When an effect (spell, disease, property) is added to or removed from something.

**Does not fire.** Effects of other kinds.

**Hosts.** The thing affected, its room, and the owner of an affected item.

**Codes.** `$n` the effect's invoker (or a stand-in named "the world"); `$t` the thing affected; `$g` the effect's ID; `$0` ADD or REMOVE.

Example:

```text
AFFECT_PROG ADD Prayer_Bless
say The gods favour you today, $t.
~
```

**Notes.** Queued. Fixture: `st26-affect.js`.

## Game, world and generation

### GAME_PROG

*added by CoffeeSUD. Table position 66. Runs: queued (SAVE: before).*

Syntax:

```text
GAME_PROG NEWGAME|RESTORE|SAVE|NEWCHAR|SWITCHTO|SWITCHFROM|PAUSE|RESUME|TIMEMODE|ANY [zapper mask]
```

**Arguments.** The event (`ANY` for all) and an optional mask tested on the character.

**Fires.** NEWGAME when a new game begins; RESTORE when a saved game is loaded; SAVE just before a save is made; NEWCHAR after a character is created; SWITCHFROM and SWITCHTO as the player leaves and takes a character; PAUSE and RESUME with the PAUSE command; TIMEMODE when the time mode changes.

**Does not fire.** Other events.

**Hosts.** Every host with the trigger, wherever it is. The world's own scripts (`world/scripts/*.script`) are good hosts for it.

**Codes.** `$n` the character; `$g` the save's slot for SAVE and RESTORE, otherwise the event; `$0` the event; `$1` the detail (the slot, the other character for SWITCHTO and SWITCHFROM, TURNS or REALTIME for TIMEMODE).

Example:

```text
GAME_PROG NEWGAME
mpecho You wake, cold, on the shingle. Gulls wheel over the wreck.
~
GAME_PROG SAVE
if inroom($n == Labyrinth#1)
  mpechoat $n The maze will not let you rest here.
  return cancel
endif
~
```

**Notes.** SAVE runs at once, before the save, so variables it sets are saved; `RETURN CANCEL` refuses the save (say why yourself). The other events are queued. Fixture: `st6-game.js`. See [Saves and party](31-saves-and-party.md).

### VAR_PROG

*added by CoffeeSUD. Table position 92. Runs: queued.*

Syntax:

```text
VAR_PROG host|*|quest variable comparison value
```

**Arguments.** Four words: the variable's host (or its quest name, or `*` for any, or `*GLOBAL*`), the variable name, a comparison (`==`, `=`, `!=`, `<>`, `>`, `<`, `>=`, `<=`), and a value. Numbers compare as numbers, other values as text without case.

**Fires.** When a variable is set and the condition holds now but did not before.

**Does not fire.** Changes that leave it true (it fires on the change to true).

**Hosts.** Every host with the trigger, wherever it is.

**Codes.** `$n` a stand-in named "the world"; `$t` the host; `$g` the new value; `$0` the old value; `$1` the variable's host; `$2` the variable name.

Example:

```text
VAR_PROG *GLOBAL* bridge_state == 1
mpecho Far away, the bridge groans and swings out over the gorge.
~
```

**Notes.** Queued. Fixture: `st25-var.js`. Variables are set by `MPSETVAR` (see [MOBPROG commands](27-mobprog-commands.md)).

### GENERATED_PROG

*added by CoffeeSUD. Table position 88. Runs: queued.*

Syntax:

```text
GENERATED_PROG [percent]
```

**Arguments.** An optional percent chance.

**Fires.** When a generator makes an area or a room: on the area, its rooms and what they hold (or the room and its contents). An area's `onGenerate` text runs once as a GENERATED_PROG script.

**Does not fire.** Hosts outside the generated area.

**Hosts.** Area, room, mob, item (those made or already there).

**Codes.** `$n` a stand-in named "the generator"; `$g` the generator's ID.

Example:

```text
GENERATED_PROG
mpecho The hollow smells of old smoke.
~
```

**Notes.** `EXECMSG_PROG GENERATED ALL` hears the same event. `GENVAR(area name)` reads the generator's variables. Queued. Fixture: `st20-generated.js`. See [Generation](12-generation.md).

## Riding, vessels and journeys

### RIDE_PROG

*inherited from CoffeeMud. Table position 55. Runs: queued.*

Syntax:

```text
RIDE_PROG [ALL | P name | word word ...]
```

**Arguments.** Keywords for the name of the rideable.

**Fires.** When another creature mounts the host (a rideable); on a room, area or mob, when anyone mounts a matching rideable there.

**Does not fire.** The host mounting (use [RIDING_PROG](#riding_prog)).

**Hosts.** Rideable item or mob, room, area, mob (witness). A mob host runs it only while it is free (see [Busy hosts](#busy-hosts)).

**Codes.** `$n` the rider; `$t` the rideable; `$g` the matched word.

Example:

```text
RIDE_PROG horse
say Mind the grey. She bites.
~
```

**Notes.** Queued. Fixture: `st18-ride-triggers.js`.

### RIDING_PROG

*inherited from CoffeeMud. Table position 56. Runs: queued.*

Syntax:

```text
RIDING_PROG [ALL | P name | word word ...]
```

**Arguments.** Keywords for the rideable's name.

**Fires.** When the scripted mob itself mounts a matching rideable.

**Does not fire.** Others mounting.

**Hosts.** Mob only. A mob host runs it only while it is free (see [Busy hosts](#busy-hosts)).

**Codes.** `$n` and `$i` the host; `$t` the rideable; `$g` the matched word.

Example:

```text
RIDING_PROG all
say Up we go.
~
```

**Notes.** Queued. Fixture: `st18-ride-triggers.js`.

### DISMOUNT_PROG

*added by CoffeeSUD. Table position 59. Runs: queued.*

Syntax:

```text
DISMOUNT_PROG [ALL | P name | word word ...]
```

**Arguments.** As RIDE_PROG.

**Fires.** As RIDE_PROG, when another creature dismounts.

**Hosts.** As RIDE_PROG.

**Codes.** `$n` the rider; `$t` the rideable; `$g` the matched word.

Example:

```text
DISMOUNT_PROG all
say Mind the step.
~
```

**Notes.** Queued. Fixture: `st18-ride-triggers.js`.

### BOARD_PROG

*added by CoffeeSUD. Table position 81. Runs: queued.*

Syntax:

```text
BOARD_PROG [ALL | zapper mask]
```

**Arguments.** Nothing or `ALL`, or a zapper mask tested on the one boarding.

**Fires.** When a creature steps from outside into a vessel's own area.

**Hosts.** The vessel, its rooms and crew, and the room outside.

**Codes.** `$n` the one boarding; `$t` the vessel; `$g` its name.

Example:

```text
BOARD_PROG
say Welcome aboard, $n. Mind the rigging.
~
```

**Notes.** Queued. Fixture: `st8-vessel.js`. See [Travel, vehicles and instances](30-travel-vehicles-instances.md).

### DISEMBARK_PROG

*added by CoffeeSUD. Table position 82. Runs: queued.*

Syntax:

```text
DISEMBARK_PROG [ALL | zapper mask]
```

**Arguments.** As BOARD_PROG.

**Fires.** When a creature steps off a vessel's own area to outside.

**Hosts.** As BOARD_PROG.

**Codes.** `$n` the one leaving; `$t` the vessel; `$g` its name.

Example:

```text
DISEMBARK_PROG
say Fair winds, $n.
~
```

**Notes.** Queued. Fixture: `st8-vessel.js`.

### SAIL_PROG

*added by CoffeeSUD. Table position 83. Runs: queued.*

Syntax:

```text
SAIL_PROG COURSE|ANCHOR|STEER|ALL|command
```

**Arguments.** The kind of order: COURSE (SET_COURSE, COURSE), ANCHOR (RAISE_ANCHOR, LOWER_ANCHOR), STEER (STEER, NAVIGATE); `ALL` or `ANY` for any; or a navigation command word itself (RISE, DIVE, TENDER, RAISE, LOWER, JUMP, FOLLOW). An argument is required.

**Fires.** When one allowed to command the vessel types a navigation order, before it is carried out.

**Does not fire.** Orders from those not allowed.

**Hosts.** The vessel and what is aboard.

**Codes.** `$n` the helmsman; `$t` the vessel; `$g` the order as typed; `$0` its kind.

Example:

```text
SAIL_PROG ANCHOR
say Aye, the anchor!
~
```

**Notes.** Queued; it cannot refuse the order. Fixture: `st8-vessel.js`.

### VESSEL_ARRIVE_PROG

*added by CoffeeSUD. Table position 84. Runs: queued.*

Syntax:

```text
VESSEL_ARRIVE_PROG [room ID | area | ALL]
```

**Arguments.** A room ID, an area name, or `ALL`/nothing.

**Fires.** When the vessel arrives in a matching room.

**Does not fire.** Other rooms.

**Hosts.** The vessel and what is aboard.

**Codes.** `$n` a stand-in named for the vessel; `$t` the vessel; `$g` the room's ID; `$0` its area.

Example:

```text
VESSEL_ARRIVE_PROG ALL
say Land ho!
~
```

**Notes.** Queued. Fixture: `st8-vessel.js`.

### VESSEL_GREET_PROG

*added by CoffeeSUD. Table position 85. Runs: queued.*

Syntax:

```text
VESSEL_GREET_PROG [vessel words | ALL]
```

**Arguments.** Words to find in the vessel's name, or `ALL`/nothing.

**Fires.** When a matching vessel comes into the room.

**Does not fire.** Other vessels.

**Hosts.** The room it enters and what is in it.

**Codes.** `$n` a stand-in named for the vessel; `$t` the vessel; `$g` the room's ID; `$0` its area.

Example:

```text
VESSEL_GREET_PROG fixtern
say The Fixtern is in. Fetch the harbourmaster.
~
```

**Notes.** Queued. Fixture: `st8-vessel.js`.

### DOCK_PROG

*added by CoffeeSUD. Table position 86. Runs: queued.*

Syntax:

```text
DOCK_PROG
```

**Arguments.** None.

**Fires.** When the vessel's anchor is lowered.

**Hosts.** The vessel, what is aboard, and the room it is in.

**Codes.** `$n` who dropped it (or a stand-in named for the vessel); `$t` the vessel; `$g` the room's ID.

Example:

```text
DOCK_PROG
say Lines out! We stay the night.
~
```

**Notes.** Queued. Fixture: `st8-vessel.js`.

### JOURNEY_PROG

*added by CoffeeSUD. Table position 116. Runs: queued.*

Syntax:

```text
JOURNEY_PROG [@GLOBAL] [ALL | START | CANCEL | ARRIVE | HOUR]
```

**Arguments.** The phase to hear, or `ALL`/nothing for every phase. `@GLOBAL` before it hears it anywhere.

**Fires.** At the given phase of a Prop_JourneyState trip.

**Does not fire.** Other phases.

**Hosts.** The party leader's room contents, the journey's source host, or any host with @GLOBAL.

**Codes.** `$n` the party leader; `$t` the source host; `$g` the phase; `$0` hours elapsed; `$1` the destination.

Example:

```text
JOURNEY_PROG ARRIVE
mpecho The ferryman lowers the plank. "Farside."
~
```

**Notes.** Queued. A journey's own FUNCTION_PROGs may return PAUSE. Fixture: `t-journeys.js`. See [Travel, vehicles and instances](30-travel-vehicles-instances.md).

## Raw messages and script plumbing

### ACT_PROG

*inherited from CoffeeMud. Table position 18. Runs: queued.*

Syntax:

```text
ACT_PROG [ALL | P phrase | word word ...]
```

**Arguments.** Words: any of them in the text (whole words, punctuation and colour ignored, case ignored). `P` and a phrase: the text must equal it. `ALL` or nothing: every line.

**Fires.** Whenever an action by someone else produces a line the host sees. The text is the message as the host would see it (others' view if there is one, else the target's, else the source's). It is perspective-dependent: a purchase in a dark shop may read as "Someone buys something from someone".

**Does not fire.** The host's own actions (use IMASK_PROG).

**Hosts.** Any host.

**Codes.** `$n` who acted; `$t` the target; `$o` the tool if it is an item, else the host item; `$p` the target if it is an item, else the host item; `$g` the matched word (or the whole text for `ALL` and `P`).

Example:

```text
ACT_PROG hits
if ispc($n)
  say Put him down, $n!
endif
~
```

**Notes.** Useful for mobs to signal each other with socials only they see. Do not use `$` codes or colour codes in the trigger words. Queued.

### MASK_PROG

*inherited from CoffeeMud. Table position 12. Runs: queued.*

Syntax:

```text
MASK_PROG [ALL | P phrase | word word ...]
```

**Arguments.** As [ACT_PROG](#act_prog).

**Fires.** Exactly as ACT_PROG (the two behave alike in CoffeeSUD).

**Hosts.** Any host.

**Codes.** As ACT_PROG.

Example:

```text
MASK_PROG gives you
say A gift?
~
```

**Notes.** They share the code path; only `MPNOTRIGGER` (which treats them as separate triggers) tells them apart.

### IMASK_PROG

*inherited from CoffeeMud. Table position 43. Runs: queued.*

Syntax:

```text
IMASK_PROG [ALL | P phrase | word word ...]
```

**Arguments.** As [ACT_PROG](#act_prog).

**Fires.** Whenever the host mob itself does something that generates a matching line of text. Only mobs can generate events.

**Does not fire.** Others' actions.

**Hosts.** Mob.

**Codes.** As ACT_PROG; `$n` is the host.

Example:

```text
IMASK_PROG howl
mpecho $i growls, a low and dangerous sound.
~
```

**Notes.** Queued.

### REGMASK_PROG

*inherited from CoffeeMud. Table position 31. Runs: queued.*

Syntax:

```text
REGMASK_PROG regular expression | P exact text
```

**Arguments.** A Java regular expression (case-insensitive, `.` matches line ends) searched for in the line; or `P` and an exact, case-sensitive full text.

**Fires.** When someone else's action produces a line the host sees that matches. Unlike ACT_PROG the text keeps its case and punctuation.

**Does not fire.** The host's own actions.

**Hosts.** Any host.

**Codes.** `$n` who acted; `$t` the target; `$g` the text from the start of the match.

Example:

```text
REGMASK_PROG (strikes|hits) .* hard
say Violence! Take it outside!
~
```

**Notes.** Queued. In a script the characters `;` `~` `<` `#` and `\` must be written with a leading `\` when you mean them literally, which matters for regular expressions that use them.

### EXECMSG_PROG

*inherited from CoffeeMud. Table position 41. Runs: queued.*

Syntax:

```text
EXECMSG_PROG [@GLOBAL] message-code [ALL | P command words | words | =S|=T|=O stat=value]
```

**Arguments.** The message type (`LIST MSGTYPES`), optionally with prefixes: `S=`, `T=` or `O=` limit the match to the source, target or others code; `<` and `>` before them use only the minor type or the major mask; `?` either (see [Message codes](#message-codes)). Then `ALL`, or the typed command words (`P` exact), or `=S`, `=T`, `=O` and `stat=value` to match a stat of the source, target or tool (`*` wildcards; `MASK=zapper` for a mask). `@GLOBAL` hears world-wide messages of that type anywhere.

**Fires.** After a message of the type has been carried out, for the host that hears it (the room's occupants, items, the room, the area). The words match the command line the player typed most recently, so mobs should use `ALL`.

**Does not fire.** Messages that were cancelled.

**Hosts.** Any host. With @GLOBAL, hosts anywhere hear messages sent world-wide (DEATH, LEVEL, LOGIN, QUIT, LEGALWARRANT, LEGALSTATE, WINQUEST, ENDQUEST).

**Codes.** `$n` the source; `$t` the target; `$o` the tool if an item (else the host item); `$p` the target if an item (else the host item); `$g` the matched command word and typed line. `MSG()` reads a copy of the message (see [MOBPROG functions](28-mobprog-functions.md)).

Example:

```text
EXECMSG_PROG GET ALL
mpecho $n likes the look of $o.
~
EXECMSG_PROG WEATHERCHANGE ALL
mpecho Weather report: $%MSG(OTEXT)%
~
```

**Notes.** CoffeeSUD also sends the messages WEATHERCHANGE, TODCHANGE, DATECHANGE and GENERATED to the scripts in an area, so EXECMSG_PROG can hear a change in weather, time of day, a new day, or a generation. Several EXECMSG_PROGs on the same message: only the first runs unless MULTITRIGGER. Queued. Fixtures: `sc5-msg.js`, `st12-global.js`, `st4-weather.js`.

### CNCLMSG_PROG

*inherited from CoffeeMud. Table position 42. Runs: before.*

Syntax:

```text
CNCLMSG_PROG message-code [ALL | P command words | words | =S|=T|=O stat=value]
```

**Arguments.** As [EXECMSG_PROG](#execmsg_prog).

**Fires.** Before the message is carried out. `RETURN CANCEL` stops it. The trigger firing does not mean the event will happen: another thing may still refuse it.

**Does not fire.** Messages already carried out.

**Hosts.** Any host that hears the message.

**Codes.** As EXECMSG_PROG, but `$0` is the message object (for JavaScript) and `MSG()`/`MPMSG` read and change the message itself.

Example:

```text
CNCLMSG_PROG HUH P plastify sword
mpechoat $n The sword has been plastified!
return cancel
~
CNCLMSG_PROG GET P get rock
mpechoat $n You may not get the rock.
return cancel
~
```

**Notes.** Run at once. `MPMSG VALUE`, `MPMSG TEXT S|T|O ...` and `MPMSG TARGET` change the message. Typing a wrong command produces a HUH message, so `CNCLMSG_PROG HUH` makes new commands. Fixtures: `sc5-msg.js`, `js15-jsfunc.js`.

### FUNCTION_PROG

*inherited from CoffeeMud. Table position 17. Runs: called.*

Syntax:

```text
FUNCTION_PROG name [parameter ...]
```

**Arguments.** A name, optionally followed by parameter names that read as `$(name)` in the body; the last parameter takes the rest of the arguments.

**Fires.** Never from an event. It runs when something calls it: `MPCALLFUNC`, the `CALLFUNC` function, `SCRIPT CALL`, or `MPMENU` with a key. The arguments are `$g`.

**Does not fire.** Every event.

**Hosts.** Any host.

**Codes.** `$n`, `$t`, `$o` as the caller's; `$g` the arguments; `$(name)` the named parameters.

Example:

```text
FUNCTION_PROG GREET_BIG who
mpecho $i bows low to $(who).
~
```

**Notes.** The body can `RETURN` text for the caller. The engine also finds a trigger line by its own name when called (for example `MPCALLFUNC` can reach a script whose trigger line begins with that word).

### SIGNAL_PROG

*added by CoffeeSUD. Table position 97. Runs: queued.*

Syntax:

```text
SIGNAL_PROG [signal name | ALL]
```

**Arguments.** One or more signal names, or `ALL`/nothing for every signal sent to it.

**Fires.** When `MPSIGNAL target name [args] [DELAY ticks]` reaches it, after the ticks (default 1). Targets are ROOM, AREA, WORLD, `TAG:tag`, a room ID, an exit (`here:north`), or one thing.

**Does not fire.** Signals with other names.

**Hosts.** Any host.

**Codes.** `$n` who caused it; `$t` the thing that sent it; `$o` the sender if an item; `$g` the arguments.

Example:

```text
SIGNAL_PROG lit
mpsetvar @$i LIT ++
if var(@$i LIT >= 3)
  mpecho The sanctum door grinds open.
  mpunlock north
endif
~
```

**Notes.** Fixture: `sc6-signals.js`.

## Worked examples

### A lighthouse keeper

One mob, one script, seven triggers: it remembers visitors, lights the lamp at dusk, answers a word, reacts to a storm and accepts one kind of gift. Give the mob (for example `Brannoch the lighthouse keeper`) the `Scriptable` behaviour with this text, or save it as `world/progs/keeper.script` and give the behaviour `LOAD=progs/keeper.script`.

```text
FIRSTGREET_PROG 100
say Well met, stranger. I am Brannoch; I keep the light.
~
REGREET_PROG 1
say Back so soon, $n? The sea has not drowned you yet.
~
TIME_PROG DUSK
mpsetvar $i LAMP lit
mpecho Brannoch climbs the stair; a moment later the great lamp blooms above.
~
TIME_PROG DAWN
mpsetvar $i LAMP out
mpecho The lamp above gutters out as Brannoch snuffs it.
~
WEATHER_PROG THUNDERSTORM
say Storm! More oil to the lamp, quick!
~
SPEECH_PROG storm storm?
say There will be a blow by nightfall. Mind the rocks.
~
GIVE_PROG oil
if var($i LAMP == out)
  say Oil! Bless you, $n. I shall light her at dusk.
else
  say Just in time, $n.
endif
mpjunk $o
~
GIVE_PROG all
say I have no use for that. Take it back.
give $o $n
~
```

How it works:

- `FIRSTGREET_PROG` and `REGREET_PROG` use the mob's saved record of meetings, so the greeting changes by itself: the first time a character walks in, and again a game day after the last meeting. Both need the mob to be free and the arrival to be a player character.
- `TIME_PROG DUSK` and `DAWN` fire at the hour the area's clock begins those parts of the day. The variable `LAMP` is kept on the mob (a script variable, saved with the game), so `GIVE_PROG` can read it.
- `WEATHER_PROG THUNDERSTORM` is heard because the mob is in the area whose weather changed. Weather is area-wide; `$g` would hold the new weather if the script wanted it.
- `SPEECH_PROG storm storm?` lists the word both bare and with a question mark, because speech keeps its punctuation.
- `GIVE_PROG oil` comes before `GIVE_PROG all`: by default only the first block that fits a message runs. `mpjunk $o` removes the oil so the player cannot take it back. `give $o $n` hands the unwanted item back.

To try it without waiting for dusk:

```text
SCRIPT FIRE keeper TIME_PROG 18
SCRIPT FIRE keeper GREET
SCRIPT LIST keeper
```

The hour in `SCRIPT FIRE ... TIME_PROG` must be one of the hours the block lists; `DUSK` fires at the hour your clock calls dusk (check with `TIME`).

### A puzzle room with three braziers

A sanctum door that opens when three braziers are lit. Three different triggers cooperate: `DISCOVER_PROG` introduces the room once, `VERB_PROG` makes `LIGHT BRAZIER` a command on the braziers, and `SIGNAL_PROG` counts the lighting on the room. A `CNCLMSG_PROG` keeps the braziers on the floor.

The room (the antechamber):

```text
DISCOVER_PROG ROOM
mpecho The antechamber is cold. Three iron braziers stand in a row, unlit.
~
SIGNAL_PROG lit
mpsetvar @$i LIT ++
if var(@$i LIT >= 3)
  mpecho The sanctum door grinds open.
  mpunlock north
endif
~
CNCLMSG_PROG GET =T NAME=*brazier*
mpechoat $n The brazier is bolted to the floor.
return cancel
~
```

Each of the three braziers (an item named `an iron brazier`):

```text
VERB_PROG light,kindle
if var(@$i BURNING == yes)
  mpechoat $n It already burns.
else
  mpsetvar @$i BURNING yes
  mpecho $n kindles $i; green flame leaps up.
  mpsignal ROOM lit
endif
~
```

How it works:

- `VERB_PROG light,kindle` runs before the game's own `LIGHT` skill and the socials, because verbs on things within reach win. With no keywords, the rest of the line must name the brazier, so `LIGHT BRAZIER` works and `LIGHT TORCH` does not touch it. `LIST VERBS` shows the verbs of a room.
- Each brazier keeps its own flag in `@$i BURNING` (a variable kept on that one object). A plain `$i` would be the shared name "an iron brazier" and all three would burn together.
- `MPSIGNAL ROOM lit` sends the signal `lit` to everything in the sender's room, including the room's own script. The room's `SIGNAL_PROG lit` counts them in `@$i LIT` and opens the door on the third.
- `CNCLMSG_PROG GET =T NAME=*brazier*` is checked before a `GET` is carried out: the filter `=T NAME=*brazier*` tests the name of the message's target, and `return cancel` stops it. Because it is on the room it protects all three braziers with one script.
- `DISCOVER_PROG ROOM` runs the first time each player character enters; it is saved with the character's visits.

## Troubleshooting

- **A trigger never fires.** Run `SCRIPTCHECK` on the host: a misspelt trigger (`GREETPROG`) is only reported as `BAD_TRIGGER`. Then check, in turn: the name carries `_PROG`; `SCRIPT LIST host` does not show it `[off]` (MPNOTRIGGER or SCRIPT DISABLE); the mob is not [busy](#busy-hosts); an earlier block of the same engine did not take the event (only the first fitting block runs unless `MULTITRIGGER`); a mask tests the creature the event is about (the entrant, not the host); a keyword is matched against the thing's `Name()`, not its keywords.
- **A SPEECH_PROG keyword misses.** Speech keeps its punctuation, so `hello` does not match `hello!`. List both forms or use `ALL` and test `$g` with `IF STRCONTAINS`.
- **`$g` shows capitals.** Triggers upper-case what they match. `$g` is lower case, `$G` as stored (often upper case).
- **It runs twice.** The room and a mob in it are separate hosts and each hears the message. A host with two engines (two `Scriptable` behaviours) runs both. An `@GLOBAL` line hears once, but a plain line on the same host as well hears it again. `GROUP_GREET_PROG` runs once per tick, `GREET_PROG` once per person.
- **It runs too late to stop the deed.** Queued triggers run after the event. Use `CNCLMSG_PROG` and `RETURN CANCEL` to forbid something.
- **`$i` is the wrong creature in `DEATH_PROG` or `KILL_PROG`.** It names the dead mob. The killer is `$n`.
- **A `ONCE_PROG` ran again after I edited the script.** An edited script starts with fresh state. Use `ONCE_PROG BOOT` for things that must run at each load and a variable or tattoo for something that must happen once per character.
- **`RAND_PROG` and `DELAY_PROG` stop far from the player.** A host in an area with no player near is not ticked. `EVERY_PROG` and the time triggers resume when the area wakes; `EVERY_PROG ... CATCHUP` tells you how many intervals passed.
- **The log says `Attempt to enque more than 25 events`.** Too many responses wait on one engine (150 for an area). Look for a `RAND_PROG` or an `ACT_PROG` on a busy host that queues faster than it runs.
- **A world-wide trigger hears nothing after a restore.** Registration happens when the script is parsed; make sure the script is on a host that exists in the restored world (an area, the WORLD host or a mob that loads).
- **`LOGIN_PROG` or `CHANNEL_PROG` fire rarely.** CoffeeSUD has no logins from other players and only a few channels (INFO, QUESTCHAT). Use `GAME_PROG` for the game's own events.

## See also

- [MOBPROG scripting](04-mobprog.md): structure, hosts, files, `LOAD=`, control flow and variables.
- [MOBPROG commands](27-mobprog-commands.md) and [MOBPROG functions](28-mobprog-functions.md): what to put inside a block, and the `$` codes.
- [JavaScript](05-javascript.md): `cm.on(...)`, `cm.verb` and the world hooks, which hear many of the same events.
- [Behaviors and masks](24-behaviors-and-masks.md): zapper masks used in trigger arguments, and the behaviours (`WaveSpawner`, `BossPhases`, `Morale`, `Prop_Essential`) that raise some triggers.
- [Properties reference](23-properties-reference.md): `Prop_Switch`, `Prop_ItemSet`, `Prop_Ambush`, `Prop_AreaEntry`, `Prop_JourneyState`, `Prop_Verbs`.
- [Quests](08-quests.md), [Society](09-society.md), [World simulation](10-world-simulation.md), [Combat and encounters](29-combat-and-encounters.md), [Travel, vehicles and instances](30-travel-vehicles-instances.md), [NPC dialogue](06-npcs-dialogue.md), [Items and crafting](07-items-crafting.md): the systems that raise the event triggers.
- `HELP SCRIPTABLE` in the game, and [Scripting in CoffeeSUD](../../CoffeeMud/guides/ScriptingSUD.html) (CoffeeSUD's supplement) and [Scripting in CoffeeMud](../../CoffeeMud/guides/Scriptable.html) (the original manual, which does not list the CoffeeSUD additions).
