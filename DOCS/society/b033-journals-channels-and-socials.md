# Journals, channels and socials

> Part of the CoffeeMud NEWOVERVIEW. Bucket: `b033` (Journals, channels and socials) · 10 files · 8794 lines of Java.

All Java paths are relative to `com/planet_ink/coffee_mud/`. `resources/` and `coffeemud.ini` are relative to the CoffeeMUD folder. Nothing was run, so behaviour below comes from reading the code. Where a comment or the in-game help disagrees with the code, the code is reported.

## Summary

Three systems share one idea, a named stream of text, and this bucket holds all three. Socials are an emote catalogue (`resources/socials.txt`, 843 variants over 338 verbs) in which each variant carries text for the actor, the room and the target, a message code and an action cost. Channels are named chat streams declared in one line of `coffeemud.ini`; flags turn a channel into a read-only feed for events such as calendar announcements, and each channel keeps a short in-memory queue plus a database backlog. Journals are one persisted record type (`JournalEntry`) behind command journals such as BUG reports, web forums, clan forums, calendar events and cron jobs. For a single-player SUD the social catalogue, the calendar and the journal storage model are the most reusable parts; the player-to-player plumbing (channel ignores, forum masks, intermud, email subscriptions) mostly is not.

## Key files and types

| File or type | Role | Lines |
|---|---|---|
| `Libraries/CMJournals.java` | Journal library: command and forum journals, clan forums, cron jobs, calendar events, expiry sweeps, line editor, subscriptions | 2389 |
| `Libraries/Socials.java` | Social list: parses `socials.txt`, finds variants, editor, default text, save, help | 1268 |
| `Libraries/CMChannels.java` | Channel library: config parsing, visibility, sending, queue, backlog trimming | 1234 |
| `Common/DefaultSocial.java` | One social variant: target resolution, messages, confirm prompts, action cost, RP and disease hooks | 1062 |
| `Libraries/interfaces/JournalsLibrary.java` | Journal interface; nested CommandJournal, ForumJournal, JournalMetaData, flag enums | 857 |
| `Libraries/interfaces/ChannelsLibrary.java` | Channel interface; CMChannel, ChannelMsg, ChannelFlag (48 values) | 521 |
| `Common/DefaultJournalEntry.java` | Journal entry record, date parsing, XML codec (effectively unused) | 473 |
| `Common/interfaces/Social.java` | Social interface, SocialFlag enum | 422 |
| `Common/interfaces/JournalEntry.java` | Entry interface; JournalAttrib bit flags | 339 |
| `Libraries/interfaces/SocialsList.java` | Social library interface and the `socials.txt` line format | 229 |

Callers outside the bucket that were read in part: `Commands/Emote.java`, `Commands/CommandJournal.java`, `Commands/SocialsCmd.java`, `Commands/Channel.java`, `Commands/Create.java`.

## How it works

### 1. Socials: the data model

- One object per variant, not per verb. `Social.java:28-30` says a social exists "for each social variation (smile, smile self, smile bob)", and the code follows that. The variant name is parsed in `DefaultSocial.setName` (`DefaultSocial.java:132-161`): the first word is the verb (`baseName`), the second word is the target token (`socialTarget`) and the rest is an argument (`socialArg`). `isTargetable` is true when the target token ends in `-NAME>` (line 151).
- Fields (`DefaultSocial.java:44-60`): `sourceMsg` (what the actor sees), `othersSeeMsg` (third parties), `targetSeesMsg`, `failedTargetMsg` (what the actor sees when the target is missing), `soundFile` (an MSP sound), `zapperMask`, three CMMsg codes (`sourceCode`, `othersCode`, `targetCode`) and a flag set holding `CONFIRM` and `TARG_CONFIRM` (`Social.java:417-421`).
- Message codes come from the first two columns of each line (`Socials.java:65-127`). First column: `W` speech, `M` hands, `S` noise, `O` noisy movement, `Q` quiet movement. Second column: `T` hands, `S` noise, `W` speech, `V` noisy movement, `O` visual only, `Q` quiet, applied to both the others and the target codes. An unknown letter logs an error and the line is skipped (`Socials.java:84-90`, `128-132`).
- Target classes. `Socials.fetchSocial` maps an object to a target token (`Socials.java:785-806`): mob to `<T-NAME>`, room item to `<I-NAME>`, carried item to `<V-NAME>`, worn or wielded item to `<E-NAME>`. `DefaultSocial.targetable` enforces the same mapping (`DefaultSocial.java:277-303`). It also refuses plain, ungettable room items for touch-type codes, with the comment "added so that touch didn't work on wallpaper" (lines 288-291).

### 2. Socials: file format and loading

- `resources/socials.txt` (path set at `Socials.java:42`) holds one variant per line: `codes TAB ID TAB you-see TAB others TAB target TAB failed TAB sound TAB zapper TAB flags` (format given in `SocialsList.java:150-152`).
- `putSocialsInHash` (`Socials.java:51-204`) keeps a line once its code and ID fields are tab-terminated and the you-see field follows (the block opens at line 141). Later fields are optional; any that are absent stay null. Flags are comma-separated `SocialFlag` names (lines 171-183).
- The parsed map is cached as the resource `PARSED_SOCIALS: <file>` (lines 207-219). Variants are grouped by verb (`realName`, lines 226-233). `put` replaces a variant with the same full name (lines 235-253).

### 3. Socials: lookup from a command

- The `EMOTE` command, used with a leading `,`, tries an exact match and then a prefix match on the words (`Commands/Emote.java:79-96`). If the variant passes `meetsCriteriaToUse` (its zapper mask), it runs `social.invoke(mob, commands, null, false)`. Otherwise the text falls through as a free-form emote.
- `fetchSocialFromSet` (`Socials.java:864-928`) picks a variant by target. With no target word it wants a variant with no target part (lines 880-889). With a target word it wants a `<T-NAME>` variant whose argument matches (890-896), then any `<...>` variant (899-912), then a prefix match on the argument (913-926).
- The verb is matched by the prefix fallback in `getSocialsSet` (`Socials.java:1068-1093`), which returns the first hash key that starts with the typed text. So `PO` can resolve to whichever of POINT, POKE or POUT the hash iterates first.

### 4. Socials: invocation and message flow

- `DefaultSocial.invoke` (`DefaultSocial.java:345-464`):
  1. If the CONFIRM flag is set and the last word is `CONFIRMED`, strip it (lines 351-355).
  2. Read the target word (not `SELF` or `ALL`) and the argument word (361-368).
  3. With no target object supplied, search the room, favouring mobs. Dotted indices such as `name.2` are accepted until a visible match is found (370-381).
  4. Variant switching. If the found object is not targetable by this variant, look up the variant for that object class and invoke it instead (388-398). For example, `SMILE` aimed at a room item selects `SMILE <I-NAME>` if one exists.
  5. Direction words. If the found object is the exit in the direction this variant names, and the variant is not targetable, the target is dropped (382-387). The six `POINT <DIRECTION>` entries rely on this.
  6. Confirmation. CONFIRM asks the actor "Are you sure (Y/n)?" (lines 401-431). TARG_CONFIRM sends the target "<actor> want(s) to <verb> you.  Is this OK (Y/n)? " (lines 432-462) and tells the actor "forbid(s) you" on a no (458). Neither prompt does anything on timeout (`timedOut()` is empty at lines 414-416 and 447-449), so an unanswered request is silent. No social in `socials.txt` sets either flag.
- `invokeIntern` (`DefaultSocial.java:466-577`) takes one of three paths and builds a `CMMsg` each time:
  - no target, or an invalid target: only the failed text, with no-effect others (490-506);
  - no target: actor and others texts (508-523);
  - target: actor, target and others texts (524-575).
  Each message goes through `R.okMessage` and then `R.send` (497-499, 514-516, 530-532), so a room listener can veto it. This is the same two-phase flow the rest of the engine uses. The MSP sound string is appended to each text (472, 511, 527-529).
- After a successful player-initiated social (533-574) the code bumps a coffee-table statistic (`STAT_SOCUSE`, lines 502, 519, 535), raises an achievement event `SOCIALUSE` (503, 520, 536) and posts role-play experience under the key `SOCIAL:<ID>` for player, NPC or room targets (545-573). That award is throttled by `RP_AWARD_DELAY` (`awardRPXP`, 305-317). The shipped `RPAWARDS=` line is empty (`coffeemud.ini:802`); whether the amounts are then zero was not verified.
- Disease hook (`DefaultSocial.java:555-567`). If the variant name starts with `SMILE`, all of these must hold: the actor has Charisma of at least 16 (556); the actor and target are the same race (557); `rollPercentage()` returns 1 (558), and `Dice.java:409-411` returns 1 to 100, so this is a 1 percent roll; neither party has reproductive code `N` (559-560); the genders differ (561); AUTODISEASE is not disabled (562). Then `Disease_Smiles` is applied to the target unless it already has it (564-566). The disease is named "Contagious Smiles" (`Abilities/Diseases/Disease_Smiles.java:43`).
- Cost. `actionsCost` is `CMProps.getSocialActionCost(baseName())` (`DefaultSocial.java:1037-1041`), which defaults to `DEFSOCTIME` 0.10 actions (`coffeemud.ini:964`, stored in hundredths, `CMProps.java:3103`). `combatActionsCost` uses `DEFCOMSOCTIME` 1.0 (`coffeemud.ini:965`). `checkedActionsCost` chooses between them by combat state (1050-1055). Per-verb exceptions come from a comma list in the same setting (`coffeemud.ini:954-963`, `CMProps.java:1260-1304`).

### 5. Socials as channel messages

- `makeChannelMsg` (`DefaultSocial.java:580-586`) wraps the social in a channel tag, `^<CHANNEL "NAME"^>[NAME] ` before it and `^</CHANNEL^>^N^.` after, and sets the message's channel code to `TYP_CHANNEL + channelInt` (`CMMsg.java:962` MASK_CHANNEL, `1283` TYP_CHANNEL = 2000).
- `makeMessage` (`DefaultSocial.java:589-695`) resolves the target across the whole game: a player on any host (`getPlayerAllHosts`, line 605, with the comment "socials come from anywhere on the map"), then a catalogued offline mob (608-615), then, for an intermud name, a factory mob placed in a random room (617-625). A target that is not in the game or not seeable is dropped (628-635).
- On a channel, a social is triggered by a message that starts with `,`, or with `:` followed by a letter or space (`CMChannels.java:1034-1055`). If the social does not pass `meetsCriteriaToUse`, the text goes out as an emote on the channel instead (1056-1071).

### 6. Socials: editor, defaults and help

- `Socials.modifySocialInterface` (`Socials.java:521-744`) is the builder editor. Target keywords are `NONE`, `ALL`, `SELF`, `<T-NAME>`, `<I-NAME>`, `<V-NAME>` and `<E-NAME>`, with friendly aliases such as `MOBTARGET` and `EQUIPTARGET` (602-615). The editor asks for you-see, others, target-sees (only for `<T-NAME>`), failed-target (for every targeted variant, 693-698), sound, zapper mask and flags (701).
- `makeDefaultSocial` (`Socials.java:946-1000`) writes starter text by target kind:

| Target | Actor sees | Others see | Failed text |
|---|---|---|---|
| none | "You `<verb>`." | "`<S-NAME>` `<verb>`s." | none |
| ALL | "You `<verb>` everyone." | "`<S-NAME>` `<verb>`s everyone." | "`<Verb>` who?" |
| `<T-NAME>` | "You `<verb>` `<T-NAME>`." | "`<S-NAME>` `<verb>`s `<T-NAMESELF>`." (target sees "`<S-NAME>` `<verb>`s you.") | "`<Verb>` who?" |
| SELF | "You `<verb>` yourself." | "`<S-NAME>` `<verb>`s `<S-HIM-HERSELF>`." | none |

- `save` (`Socials.java:1003-1053`) rewrites `socials.txt` in name order through `getEncodedLine` (`DefaultSocial.java:720-791`).
- `getSocialsHelp` (`Socials.java:1124-1206`) previews a verb by running each message through `CoffeeFilter.fullOutFilter` with two stand-in mobs, "the target" and "someone" (1136-1139).
- `getSocialsBaseTable` (`Socials.java:1248-1267`) lays out the `SOCIALS` command list in four columns of 19 characters.

### 7. Channels: configuration and data model

- `CMChannel` (`ChannelsLibrary.java:356-432`) has a name, external names (I3, IMC2, Grapevine, Discord), a zapper mask for who may read, colour overrides, a flag set and a message queue. The anonymous implementation is `CMChannels.createNewChannel` (`CMChannels.java:110-181`). The queue holds 100 entries (`CMChannels.java:63`).
- `loadChannels` (`CMChannels.java:751-872`) reads three comma-separated lists:
  - `CHANNELS`: `NAME [mask] [FLAG ... | COLOUR ...]`. `parseOutFlags` (691-748) strips the flags and colour words, and what remains is the zapper mask.
  - The I3 list: `NAME LEVEL remoteName` (784-815). It sets the I3 mapping and a level mask.
  - The IMC2 list: the same shape (816-847).
  - An AUCTION channel is added unless the `CHANNELAUCTION` disable flag is set (848-857).
  - The list is capped at 31 channels (858-862).
- The cap follows from the storage. A player's muted channels are one `int` bit mask, with bit N for channel N (`CMChannels.java:377`, `425`, `452`; `CMath.isSet`, `CMath.java:977-981`). `Commands/Channel.java:119-134` turns a channel back on by clearing its bit with `mask & (mask - bit)`. `Commands/Channels.java:62-66` uses `Integer.MAX_VALUE` (every bit set) as "all channels off". The `ChannelsLibrary.java:38-43` comment calls the indexed-vector design legacy.
- `ChannelFlag` has 48 values (`ChannelsLibrary.java:472-519`). Shipped channels use them as configuration words; see the content section.

### 8. Channels: visibility

- `mayReadThisChannel` (`CMChannels.java:335-380`; session version 383-428) requires, in order:
  - a non-null sender and reader, and a live reader unless offline reads are allowed (352-355);
  - for CLANONLY or CLANALLYONLY, the CHANNEL clan privilege and either a common clan or, for ALLYONLY, an ally (359-369);
  - the sender is not ignored by channel name, by sender name or by `channel.sender` (371; 418-420);
  - the reader passes the channel's zapper mask (372; 421);
  - for SAMEAREA, the reader is in the sender's area (373-376; 422-424);
  - the reader has not muted the channel (377).
- `getFlaggedChannelNames` (`CMChannels.java:567-586`) lets a reader with the privacy attribute see only the channels they could read. That is how the calendar announcement path picks its CALENDAR channels (`CMJournals.java:1281`).

### 9. Channels: sending

- The entry point is `createAndSendChannelMessage` (`CMChannels.java:961-1144`), called from `Commands/Channel.java:71` and `:366`. The channel name is prefix-matched (965), and the text first passes the `CHANNELFILTER` ini filter (972).
- Four text shapes:
  - System message (1018-1032): `[NAME] 'text'`, with `<S-NAME>` in front unless the mob's name starts with `^`.
  - Social (1034-1055): see section 5.
  - Emote (1056-1071): `[NAME] Name text`.
  - Speech (1073-1081): "You gossip 'text'" for the actor and `<S-NAME> gossips 'text'` for others. The channel name is pluralised with `makePlural` (1078).
- Flags change the text. ACCOUNTOOC replaces `<S-NAME>` with the account name (1083-1096). REALNAMEOOC substitutes the character name (1098-1111). NOLANGUAGE sets the message tool to the Common language (1113-1114). ADDROOM, ADDAREA and ADDACCOUNT append the room, area or account to the name (980-988). TWITTER posts the raw text outside the game (1120-1121). NOMOUTH changes the source code (1017).
- Delivery. `R.okMessage` gates the message in the sender's room (1117-1118), then `channelQueUp` stores it (1125). Every local online session then gets `sendChannelCMMsgTo` (1131-1140). That function checks readability again (`CMChannels.java:875-923`, test at 890), sends GMCP JSON if the client asked for it (894-902), and runs the message and its trailers (903-920). External channels are handled by `CMLib.intermud().imudChannel` (1142-1143).
- Audience key. `getExtraChannelDataFields` (`CMChannels.java:925-958`) builds a `subNameField`: an area-name hash for SAMEAREA channels and clan-name hashes for CLANONLY or CLANALLYONLY channels. The backlog is partitioned by this key, so history follows the same audience as delivery.

### 10. Channels: queue and backlog

- `channelQueUp` (`CMChannels.java:458-503`): when the queue holds 100 messages the oldest is dropped (466-467), and the new message is added to the front with its timestamp (469-488). A flat copy goes to the database backlog (490-502) unless the channel is NOBACKLOG, `CHANNELBACKLOGS` is disabled, or `CHANNELBACKLOG` is `"0"`.
- `getChannelQue` (296-332) returns the newest in-memory messages and then pages older ones from the backlog. `searchChannelQue` (210-236) searches the in-memory queue for NOBACKLOG channels and the backlog otherwise.
- The utility tick (`CMChannels.java:1171-1220`) trims the backlog. An integer keeps that many messages per channel. `N DAYS`, `N WEEKS`, `N MONTHS` or `N YEARS` keeps messages newer than that age (1186-1205). `0` deletes every backlog row for every channel (`core/database/BackLogLoader.java:460-463`). That is the "disables the entire backlog process" behaviour documented at `coffeemud.ini:1532-1540`, whose default is 10000.

### 11. Journals: the entry record

- `JournalEntry` (`JournalEntry.java:49-316`), implemented in `DefaultJournalEntry.java:42-61`: `key` (unique across all journals), `from`, `to`, `subj`, `msg`, `date` (ms) with `dateStr` as the source text, `update`, `expiration`, `parent` (for threads), `attributes`, `data`, `cardinal`, `msgIcon`, `replies`, `views`.
- `to` is the addressing field: `ALL`, a player name, or `MASK=<zapper>`, which `canReadMessage` checks (`CMJournals.java:373-377`). A reader with the JOURNALS security flag may read anything unless the caller asks to ignore privileges (371-372).
- `dateStr` accepts three forms (`DefaultJournalEntry.java:187-222`): an integer (epoch ms); two numbers separated by `/` (date and update time); or a time-period code with three slashes, parsed against a named in-game clock (the clock name may follow a space) and stored as the timestamp (207-219).
- Attribute bits (`JournalEntry.java:318-338`): each value's bit is 2 to the power of its ordinal, so PROTECTED = 1, STUCKY = 2, ATTACHMENT = 4. ATTACHMENT entries keep their files under VFS keys `<key>/%` (`DefaultJournalEntry.java:350-364`, query at 361).
- **Documentation conflict.** `JournalEntry.java:321-327` says PROTECTED means "stuck to the top" and STUCKY means "protected from auto-purging". The code does the opposite:
  - the expiry sweep skips PROTECTED entries (`CMJournals.java:1075`), as does the mail mass-mailer (`core/smtp/MassMailer.java:157`, `261`, `277`);
  - intro entries and cron entries are PROTECTED (`core/database/JournalLoader.java:1019`, `1032`; `Commands/Create.java:1819`);
  - STUCKY marks pinned threads, which are listed separately and kept in sticky-key metadata (`JournalLoader.java:265`, `297`, `1077`).

### 12. Journals: command journals

- `loadCommandJournals` (`CMJournals.java:256-355`) parses entries of the form `NAME [flags] [zapper mask]`. Flags (`JournalsLibrary.java:607-651`): CHANNEL=, ADDROOM, EXPIRE=days, ADMINECHO, CONFIRM, SCRIPT=, REPLYSELF=, REPLYALL=, MOTD, ASSIGN=. What remains is the mask that decides who may post (`Commands/CommandJournal.java:346-349`). ASSIGN defaults to `ALL` (`CMJournals.java:307-319`).
- Storage: the journal is `SYSTEM_<NAME>S` (336-339), and its security flag is registered (306).
- Posting (`Commands/CommandJournal.java:340-400`):
  - the mask check, then the `review` and `transfer` sub-commands;
  - `getScriptValue` (`CMJournals.java:671-692`), which can rewrite the text through a MOBPROG script;
  - the room ID is prefixed if ADDROOM is set, and `notifyPosting` runs;
  - the entry is written with `to` = `ALL`, and the author gets "Your <name> message has been sent.  Thank you." (379);
  - CHANNEL echoes the post to a channel.
  - The CONFIRM flag first asks "Submit this <journal>: '<text>' (Y/n)?" (branch at line 387).
- The subject field is the message itself, padded to at least 20 characters (`CommandJournal.java:376`).

### 13. Journals: forums and clan forums

- `parseForumJournals` (`CMJournals.java:470-639`) reads `NAME FLAG=mask ...` entries. Flags (`JournalsLibrary.java:817-856`): READ, POST, REPLY, ADMIN, ATTACH, MAXATTACH, CATEGORY, EXPIRE, SORTBY. Defaults: an empty read mask means public (542); attach defaults to `+SYSOP -NAMES` (547); the maximum number of attachments defaults to 3 (548).
- `authorizationCheck` (611-629) requires the read mask first and then the mask for the action asked about. `maskCheck` with an empty mask passes (599-607).
- Clan forums (`registerClanForum`, 437-468): a line tagged `[category]` is used only by clan governments in that category, with `<CLANTYPE>` and `<CLANNAME>` substituted (449-458).
- Summary statistics (`getJournalStats`, 93-242) cache the thread count, post count, intro and latest-message keys and sticky keys in the resource `FORUM_JOURNAL_STATS`. They are computed from the database (236) and cleared by `clearJournalSummaryStats` (245-254).
- The web front end (`WebMacros/JournalFunction.java`, `JournalInfo.java`) is outside this bucket and was only searched.

### 14. Journals: subscriptions and notifications

- `notifyPosting` (`CMJournals.java:1556-1577`) tells each online player whose subscriptions include `" P :<JOURNAL>"` (line 1558) "^w<journal> Notification: <from> just added a new message.^?". `notifyReplying` (1580-1597) tells the author of the message being replied to. Subscriptions are read from the player's `PlayerStats` (1566, 1590).
- Email subscriptions are kept in `mailinglists.txt` through `Resources.getCachedMultiLists` (2277-2288, 2299-2337, 2346-2377). Subscribe and unsubscribe texts come from the `SUBSCRIPTION_STRS` list with `<NAME>` substituted (2320-2326, 2360-2366) and go out through `CMLib.smtp().emailOrJournal` (2328, 2368). Subscription is effective only when the MAILBOX property is set (2296, 2343).

### 15. Journals: the line editor

- Two implementations of one editor. `makeMessage` (`CMJournals.java:2097-2272`) is blocking and uses `sess.choose`. `makeMessageASync` (1600-2093) is an event-driven state machine (`MsgMkrState`, 1542-1553) with a GMCP path (1608-1662) for clients that have a graphical editor.
- Menu (2123, 2127-2266): `A` add lines (a single `.` on a blank line leaves add mode), `D` delete, `L` list, `I` insert before a line, `E` edit a line, `R` replace text (search, then replace), `S` save, `Q` quit, and `W` (GMCP clients only) rewrite the whole document. Help text is at 1525-1540.
- `JOURNAL_BOUNDARY` (`JournalsLibrary.java:383`) is the separator that non-forum journals use to tack replies onto the end of a message.

### 16. Journals: the utility tick (sweeps, MOTD)

- `activate` (`CMJournals.java:1094-1115`) starts a tick at `TIME_UTILTHREAD_SLEEP/10` (1102-1104), loads cron jobs in creation order (1107-1112) and initialises calendar events (1113).
- `tick` (1373-1438):
  - Calendar events run on their own event tick, which returns false so that it is removed (1381-1389).
  - Every 60 normal ticks (`SWEEP_TICK_MAX`, `CMJournals.java:58`, 1392-1394) the expiry sweep runs (1407), unless the save or journal thread is disabled. The same branch deletes the generated `text/motd.txt` when the day of the month changes (1399-1406).
  - Cron runs on every tick (1411-1430).
- Expiry (`expirationJournalSweep`, 999-1092):
  - item journals use the `EXPIRE` stat recorded by `registerItemJournal` (2380-2388; sweep 1002-1019);
  - command journals use EXPIRE days (1020-1039);
  - calendar entries older than one year are deleted (1044-1053);
  - forum entries use EXPIRE days and skip PROTECTED entries (1061-1091).
  All of them measure age on the wall clock, `System.currentTimeMillis()` minus days times `MILI_DAY` (1008, 1027, 1070).

### 17. Cron jobs

- A cron job is a `SYSTEM_CRON` journal entry, run by `runCronJob` (`CMJournals.java:750-921`). The creation command is `CREATE CRON [NAME] [INTERVAL]` (`Commands/Create.java:1785-1822`), which sets PROTECTED and stores the interval in `data`.
- Fields: `from` is the player the job runs as (765); `update` is the next due time (761-762, 790-792); `data` holds `INTERVAL="..."` as milliseconds or a tick expression (774, 782-783); `msg` is the body (805).
- Body lines:
  - a plain command is alias-expanded and queued on the runner, with `setActions(99)` limiting work per run (885-906);
  - `<MOBPROG>...</MOBPROG>` runs a MOBPROG script (818-842);
  - `<SCRIPT>...</SCRIPT>` runs Mozilla Rhino JavaScript with `mob()`, `toJavaString` and `getCMType` helpers (844-875, `JScriptWindow` 712-748);
  - a runner with no session gets a fake session (793-803);
  - backticks are turned into apostrophes (817, 824, 850).
- The return value is the next due time. A job that returns -1 is removed (1423-1425).

### 18. Calendar events

- Entries live in `SYSTEM_CALENDAR`. `CalendarCmd` writes `from` (SYSTEM, Holiday, a clan or an NPC), `to` (an area list or ALL), `subj`, `expiration` (`Commands/CalendarCmd.java:634-639`; one mud hour per duration unit unless real time is used) and `data`: `<HOURS>n</HOURS>` plus an optional `<PERIOD>n UNIT</PERIOD>` (lines 639-645), where UNIT is a `TimePeriod` such as `YEAR` (`Common/interfaces/TimeClock.java:736-745`).
- `resetCalendarEvents` (`CMJournals.java:1140-1181`) finds the next start time and sets one event tick (1178). Holidays come from `CMLib.quests().getHolidayEntries` (1149).
- `processCalendarEvents` (1302-1371) makes the announcements:
  - SYSTEM and Holiday events with no area go to every CALENDAR-flagged channel (1324-1333);
  - area-limited events go to online players in those areas (1334-1346 and `postCalendarEventTo`, 1260-1300);
  - clan events use `clanAnnounce` (1350-1356); NPC events go out as the NPC (1359-1366).
- The announcement text is "<subject> is now started, and will end at <date>." (`CMJournals.java:1254`). The end date is read from the in-game clock when one applies (1197-1199).
- Recurrence (`processCalendarExpiration`, 923-997). For a PERIOD event past its expiry, a copy moves to the next occurrence. With an in-game clock, the date is the original moved by `n * multiplier` periods with `TimeClock.bump` (953-959). In real time, months and seasons use `Calendar.add` (961-975) and other periods add `n * increment * multiplier` (976-977). The new entry is written (986) and the old entry's data is cleared (993).

## Content worth mining

### Socials catalogue (`resources/socials.txt`, 843 variants)

Counts were taken with awk over the file. They are not stated in the code.

| Measure | Value |
|---|---|
| Variants / distinct verbs | 843 / 338 |
| Target kind | none 318; `<T-NAME>` 308; SELF 204; ALL 3; `<I-NAME>` 3; `<E-NAME>` 1; direction 6 (POINT UP, DOWN, NORTH, SOUTH, EAST, WEST) |
| Source code | hands 440; noisy movement 191; noise 124; speech 84; quiet movement 4 |
| Verbs with the most variants | POINT 10, POKE 7, LICK 6 |
| Sound-file variants | 90, using 40 distinct MSP files |
| Zapper masks / CONFIRM flags | none |

Reusable verb names: GREET, HAIL, WINK, YODEL, SNORE, SMIRK, STRETCH, TSK, CLAP, COUGH, HOP, BECKON, FAREWELL, SHIVER, THREATEN, WORSHIP, SILLY. SMILE is a three-variant example: `SMILE`, `SMILE <T-NAME>`, `SMILE SELF`. Some verbs are crude or adult in tone. They are not listed or quoted here.

Default-text pattern (`makeDefaultSocial`, see section 6): a bare verb gives "You <verb>." and "<S-NAME> <verb>s."; ALL gives "You <verb> everyone."; a mob target gives "You <verb> <T-NAME>." and "<S-NAME> <verb>s you." The text uses tokens such as `<S-NAME>`, `<T-NAME>`, `<T-NAMESELF>` and `<S-HIM-HERSELF>`, which are expanded when displayed.

### Channels in the shipped config (`coffeemud.ini:1454-1464`)

| Channel | Options | Effect |
|---|---|---|
| GOSSIP, GRATZ, CHAT | none | ordinary chat |
| OOC | ACCOUNTOOC NOLANGUAGE | shows account names; Common tongue |
| QUESTCHAT | PLAYERREADONLY | read-only for players |
| WIZ | mask +SYSOP -NAMES | staff only |
| CLANTALK | CLANONLY CLANINFO CONQUESTS LOGINS LOGOFFS | clan chat plus clan event lines |
| INFO | READONLY CALENDAR EXECUTIONS LEVELS NEWPLAYERS BIRTHS MARRIAGES ACHIEVEMENTS DIVORCES CHRISTENINGS | read-only event feed; calendar sink |
| WIZINFO | READONLY LOGINS LOGOFFS DETAILEDLEVELS DETAILEDDEATHS PLAYERPURGES, mask +SYSOP -NAMES | staff event feed |
| ICOFF | NOLANGUAGE, I3=coffeemud_universe, mask -LEVELS +>91 | bridged to the I3 network, with a level filter |
| AUCTION | added automatically | unless disabled (`CMChannels.java:848-857`) |

The 48 `ChannelFlag` values (`ChannelsLibrary.java:472-519`) fall into three groups:
- who may read or write: CLANONLY, CLANALLYONLY, SAMEAREA, SAMEHOST, READONLY, PLAYERREADONLY, ARCHONREADONLY, NOMOUTH and others;
- how a message is shown: ACCOUNTOOC, REALNAMEOOC, NOLANGUAGE, ADDROOM, ADDAREA, ADDACCOUNT, NOBACKLOG and others;
- automatic event feeds: EXECUTIONS, LOGINS, LOGOFFS, BIRTHS, MARRIAGES, DIVORCES, CHRISTENINGS, LEVELS, DEATHS, CONQUESTS, CONCEPTIONS, NEWPLAYERS, LOSTLEVELS, PLAYERPURGES, CLANINFO, WARRANTS, ACHIEVEMENTS, CALENDAR and others.
External bridges are TWITTER, DISCORD, I3, IMC2 and GRAPEVINE.

### Journal catalogue

Command journals (`coffeemud.ini:1496-1501`):

| Journal | Flags | Effect |
|---|---|---|
| BUG | CONFIRM, SCRIPT=progs/bugreport.script | asks before posting; runs a script on the text |
| TYPO | ADDROOM | adds the room ID |
| IDEA, TASK | none | plain posts |
| STUCK | CHANNEL=WIZINFO, EXPIRE=2, ADMINECHO | echoes to WIZINFO, purges after 2 days, alerts admins at login |

Forum journals (`coffeemud.ini:1519-1524`):

| Forum | Masks and expiry |
|---|---|
| Open Discussion | POST excludes anonymous (level 0) users; ADMIN SYSOP; EXPIRE 90 |
| Announcements | READ excludes anonymous users; POST, REPLY, ADMIN SYSOP; EXPIRE 90 |
| Player Chat | READ and POST exclude anonymous users; ADMIN SYSOP; EXPIRE 360 |
| Bugs and Typos | READ and POST exclude anonymous users; ATTACH +NAMES; EXPIRE 360 |
| Archons Speak | READ SYSOP only; EXPIRE 360 |

Short messages worth reusing, quoted from the code:
- "Your <name> message has been sent.  Thank you." (`Commands/CommandJournal.java:379`)
- "<journal> Notification: <from> just added a new message." (`CMJournals.java:1573`)
- "<subject> is now started, and will end at <date>." (`CMJournals.java:1254`)
- "[<channel>] '<text>'" for system channel lines (`CMChannels.java:1020`)
- "Are you sure (Y/n)?" (`DefaultSocial.java:411`)

### Numbers worth keeping

- 100 messages per channel queue (`CMChannels.java:63`); backlog default 10000 (`coffeemud.ini:1540`).
- A 31-channel ceiling from the int bit mask (`CMChannels.java:858`).
- Journal sweep every 60 utility ticks (`CMJournals.java:58`); attachment default 3 (548); JOURNALLIMIT 100 (`coffeemud.ini:1530`).
- Social action cost 0.10 out of combat and 1.0 in combat (`coffeemud.ini:964-965`).

## Notable design ideas

1. A social is one record per variant, and the target kind is part of its key (`DefaultSocial.java:132-161`). Typing `SMILE` with an object moves to the variant written for that kind of object (388-398), so authors write only the variants they care about.
2. One social object yields a room action, a channel message and an emote (`DefaultSocial.java:580-586`; `Commands/Emote.java:79-96`). A single data item serves three verbs.
3. Channels are declared in a one-line mini-language, such as `OOC ACCOUNTOOC NOLANGUAGE` or `WIZ +SYSOP -NAMES`. Flags turn ordinary chat into a feed (`CMChannels.java:691-748`).
4. History is partitioned by the same audience rule that governs delivery (`CMChannels.java:925-958`). No separate store is needed for each audience.
5. Journal entries are one record type for mail, forum posts, bug reports, notices, calendar events and cron jobs. `to` addresses an entry to ALL, a name or a zapper mask (`CMJournals.java:373-377`), and attribute bits (`JournalEntry.java:318-338`) carry state.
6. The database is the scheduler. Cron due times and calendar events are rows, so they survive restarts (`CMJournals.java:1107-1113`).
7. Recurrence is declared as text, `<PERIOD>1 YEAR</PERIOD><HOURS>24</HOURS>` (`CalendarCmd.java:639-645`), and computed against the in-game clock when one applies (`CMJournals.java:953-959`).
8. Calendar announcements are ordinary channel messages with a CALENDAR flag (`CMJournals.java:1281-1295`), so the same mute, ignore and mask rules apply to news.
9. Socials cost actions, with a separate combat figure and per-verb overrides (`CMProps.java:1260-1304`). That is a light action economy with no separate table.

## Takeaways for a Python SUD

- **Socials become data.** Store them in a YAML or TOML file, one record per verb with a target-kind key, and generate missing variants from a template. Keep the observer split from the source (what the actor sees, what the room sees, what the target sees). A sketch:

```python
@dataclass(frozen=True)
class SocialVariant:
    verb: str
    target: str                  # "none", "self", "npc", "item", "inventory", "equipment", "direction"
    actor_text: str              # "You smile at {target}."
    room_text: str               # "{actor} smiles at {target}."
    target_text: str | None = None
    no_target_text: str | None = None
    action_cost: float = 0.1     # turns, mirroring DEFSOCTIME
    combat_cost: float = 1.0     # DEFCOMSOCTIME
    requires: str | None = None  # small predicate such as "cha >= 16"
```

- **Drop what needs other humans.** Remove TARG_CONFIRM (it has no timeout reply), channel ignore and mute lists, the intermud and factory-mob paths, role-play experience between players, email subscriptions and clan forums. "ALL" becomes "everyone here".
- **Keep the target taxonomy.** Variant switching, direction targets and per-verb action cost carry over to NPCs, room items, inventory and exits without change.
- **Replace channels with one event feed.** A channel becomes a named stream with a predicate filter, an optional same-location flag, `collections.deque(maxlen=100)` for recent messages and an optional SQLite log. Calendar and world news publish to it. Keep per-player mutes as a set of channel names rather than a 31-bit mask.
- **Journals become one table.** Use `Entry(key, author, to, subject, body, created_at, expires_at, parent, flags)`, with an `enum.Flag` for PROTECTED, STUCKY and ATTACHMENT. Name the flags by what they do, because the source comments are wrong. Let `to` mean "all" or a named audience.
- **Run expiry, cron and calendar on simulated time.** CoffeeMud uses wall-clock milliseconds (`CMJournals.java:761`, `790`, `1008`), which is wrong for a paused or turn-based game. Store due times in game ticks.
- **Compute recurrence from the anchor.** Work out the next occurrence as `anchor + k * period`, where k is the smallest value that lies after now. Do not step forward from the last occurrence, and do not reproduce the compounding in `CMJournals.java:963-975`.
- **Cron becomes a scheduler of command lists.** Use `ScheduledTask(due_tick, interval_ticks, actions)`, where the actions are already-parsed commands. Do not embed a script engine; `CMJournals.java:858-868` shows the trust problem.
- **The message editor becomes `$EDITOR`,** or a multi-line prompt ending with a `.` line. The operations in `CMJournals.java:2123-2266` (add, insert, delete, edit, replace, list) are pure functions over a list of lines and port directly.
- **Drop the rest:** MSP sound, GMCP, the web forum, SMTP, I3, IMC2, Grapevine, Discord, Twitter, and the account-name modes (ACCOUNTOOC and REALNAMEOOC), which exist only because of accounts.

## Problems and cruft

1. **Calendar recurrence drifts.** In real-time mode, `processCalendarExpiration` (`CMJournals.java:963-975`) rebuilds the base date from `newEntry.date()` on each pass, but it offsets by the cumulative `multiplier`. For a MONTH period of 1, occurrences move forward by 1, then 3, then 6 months within one sweep. Read, not run.
2. **Immediate recurrence is probably dead for player-made events.** `getCalendarEvent` schedules the next occurrence only when the message text contains `<PERIOD>` (`CMJournals.java:1201-1203`). `CalendarCmd` writes that tag into `data` (`CalendarCmd.java:645`). Such events then wait for the 60-tick sweep (1040-1043). Read, not run.
3. **Subscription message bugs.** The subscribe branch tests `msgs.length>0` but reads `msgs[1]`, which throws when the list has one entry (`CMJournals.java:2325-2326`). The unsubscribe branch tests `msgs[1]` but reads `msgs[3]` (`2365-2366`).
4. **Inverted attribute docs** (`JournalEntry.java:321-327`); see section 11.
5. **Broken XML codec.** `DefaultJournalEntry.getXML` omits the closing quote after the `DATA` attribute (`DefaultJournalEntry.java:421`), so the output is not well-formed. `setXML` (435-458) does not read ATTRIB or CARD, which `getXML` writes (415-416), so a round trip loses attributes and the cardinal number. Nothing else calls either method except `hashCode`, which caches the hash of the XML on first use and never invalidates it (428-433).
6. **Wrong help text.** The socials help lists the equipment target as `<T-NAME> (EQUIPTARGET)` (`Socials.java:592`). The code maps EQUIPTARGET to `<E-NAME>` (615), so a builder who follows the help picks the wrong target.
7. **Naive pluralisation.** Default socials append "s" to the verb (`Socials.java:960`, `968`, `978`, `995`), so KISS would default to "kisss". Channels use `makePlural` (`CMChannels.java:1078`), so the codebase already has the right tool.
8. **Save path.** `Socials.save` (`Socials.java:1009-1043`) sorts with an O(n²) selection sort. Its duplicate check uses `equals`, which `DefaultSocial` does not override, so the check can never match. `DefaultSocial.sameAs` (891-931) ignores the zapper mask.
9. **Prefix lookups depend on hash order.** `Socials.java:820-829`, `844-851` and `1087-1090` return the first key that matches. With several verbs sharing a prefix, the result depends on the order of the hash.
10. **Unused constant.** `CMJournals.QUEUE_SIZE` (line 57) is never read. The channel queue has its own constant (`CMChannels.java:63`).
11. **Duplicated editor.** Two copies of the message editor (`CMJournals.java:1600-2093` and `2097-2272`) carry the same operations and near-identical help text.
12. **Multiplayer assumptions.** TARG_CONFIRM has no reply on timeout (`DefaultSocial.java:438-461`). Socials give role-play experience for targeting another player (545-548). Channels check clan allies (359-369) and per-channel ignores (418-420). Socials can target a player on any host (605).
13. **Backlog cost, unmeasured.** Each channel message costs a database insert (`CMChannels.java:490-502`). Each utility tick runs a DELETE per channel for the count and age limits (`BackLogLoader.java:460-473`), even when nothing is over the limit.
14. **Cron is a trust surface.** A row in the journal table can run JavaScript with access to the runner's mob (`CMJournals.java:712-748`, `858-868`) and can queue commands as the runner (793-805).
15. **Wall-clock timers.** Expiry and cron use real time (`CMJournals.java:761`, `790`, `1008`, `1027`, `1070`). That suits a live server but not a paused game.
16. **Fragile search.** `canReadMessage` assumes the search term is lower-case and that `subj`, `from` and `msg` are non-null (`CMJournals.java:362-368`). Minor.

## Coverage

- **Read in full (all 10 manifest files):** `Libraries/CMJournals.java` (2389 lines), `Libraries/Socials.java` (1268), `Libraries/CMChannels.java` (1234), `Common/DefaultSocial.java` (1062), `Libraries/interfaces/JournalsLibrary.java` (857), `Libraries/interfaces/ChannelsLibrary.java` (521), `Common/DefaultJournalEntry.java` (473), `Common/interfaces/Social.java` (422), `Common/interfaces/JournalEntry.java` (339), `Libraries/interfaces/SocialsList.java` (229).
- **Read in part, outside the bucket:** `Commands/Emote.java` 70-110; `Commands/CommandJournal.java` 340-400; `Commands/Channel.java` 110-140; `Commands/Channels.java` 55-70; `Commands/CalendarCmd.java` 595-650; `Commands/Create.java` 1785-1822; `Commands/SocialsCmd.java` (whole, 63 lines); `core/database/BackLogLoader.java` 451-482; `core/database/JournalLoader.java` 258-300, 1010-1035, 1070-1082; `core/CMProps.java` 1260-1304 and 3103-3104, 3713-3730; `Libraries/Dice.java` 409-412; `Common/interfaces/TimeClock.java` 736-745; `Abilities/Diseases/Disease_Smiles.java` (name only, lines 43 and 116); `coffeemud.ini` lines 802, 954-965, 1454-1540 and 2151; `resources/socials.txt` (summarised with awk, counts only).
- **Searched or skimmed only:** `WebMacros/Journal*.java`, `Items/Basic/StdJournal.java`, `Libraries/Quests.java` (holiday entries), `Behaviors/Emoter.java`, `core/smtp/MassMailer.java` (PROTECTED checks), `CMMsg.java` constants.
- **Not read:** `core/database/JournalLoader.java` and `BackLogLoader.java` in full (they belong to the database bucket).
