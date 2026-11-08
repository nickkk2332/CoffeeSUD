# Socials, MUDChat lines, factions and screen texts

> Part of the CoffeeMud NEWOVERVIEW. Bucket: `c03` (content: Socials, MUDChat lines, factions and screen texts) · 29 files · 263,490 bytes of data.

Citation convention: paths are relative to the CoffeeMud folder. Java paths below omit the prefix `com/planet_ink/coffee_mud/`; add it to get the path from the CoffeeMud folder. Line numbers were checked against the source. Anything not confirmed by reading is marked "unverified".

## Summary

CoffeeMud's small-talk layer is data-driven in four parts. `resources/socials.txt` holds 843 tab-separated emote definitions covering 338 command words (ACCUSE, SMILE, BOW and so on), each with actor and room text. The 312 entries with a `<T-NAME>`, `<I-NAME>` or `<E-NAME>` variant carry target text, and 295 entries carry a failure text. `resources/chat.dat` is a small pattern-and-response language that gives NPCs weighted, first-match replies to speech and to other people's emotes, and it powers the chatty dogs, cats, healers and similar mobs. `resources/factions/*.ini` define three reputation scales (alignment, inclination and an end-game "Triumph" score) as named ranges, zapper-gated starting values and change rules keyed to socials, abilities, combat and time, and the faction values gate some abilities. The `resources/text/` screens (login, character creation, blocked and offline notices, rules, help, leaderboards) are plain text, most of them passed through the web-macro filter. For a single-player SUD these systems show how a world can react to what the player types, and how NPCs can have personalities without per-NPC scripts.

## Key files and types

| File or class | Role | Lines |
|---|---|---|
| `resources/socials.txt` | 843 social definitions, nine tab-separated fields each | 843 lines, 116,813 bytes |
| `resources/chat.dat` | MUDChat data: operator legend, default group, 18 named groups | 2,952 lines, 62,359 bytes |
| `resources/factions/alignment.ini` | Good/evil scale: 7 ranges, 31 ability gates, 3 change rules, auto-defaults | 308 lines, 16,210 bytes |
| `resources/factions/inclination.ini` | Lawful/chaotic scale: 7 ranges, 31 ability gates, 54 change rules (25 keyed to socials) | 358 lines, 19,070 bytes |
| `resources/factions/triumph.ini` | End-game "Triumph" score: 5 tiers, 17 area and death rules | 293 lines, 17,523 bytes |
| `resources/text/*.txt` (24 files) | Login, creation, blocked/offline screens, rules, help, leaderboards, MXP definitions | 38 bytes to 5.5 KB each |
| `Libraries/Socials.java` | Loads and indexes socials, fetches by name and target, saves, lists | 1268 |
| `Libraries/interfaces/SocialsList.java` | Interface for the socials library | 229 |
| `Common/DefaultSocial.java` | One social: messages, codes, zapper, sound, invoke, action cost | 1062 |
| `Common/interfaces/Social.java` | Social API; `SocialFlag` enum (CONFIRM, TARG_CONFIRM) | 422 |
| `Commands/Emote.java` | EMOTE command and the `,social` shortcut | 184 |
| `Commands/SocialsCmd.java` | SOCIALS command (lists base names) | 63 |
| `Libraries/EnglishParser.java` | Resolves a typed command word to a social (`findSocial` at line 826) | 4102 |
| `Behaviors/MudChat.java` | Chat engine: parser, matcher, responder, response queue, personality | 1755 |
| `Behaviors/interfaces/ChattyBehavior.java` | Interface for chatty behaviours (last thing said, last responded to) | 54 |
| `Behaviors/QuestChat.java` | Subclass of MudChat | 97 |
| `Libraries/Factions.java` | Faction library: loading, lookup, auto-assignment, alignment purity, periodic tick | 2109 |
| `Libraries/interfaces/FactionManager.java` | Faction library API | 630 |
| `Common/DefaultFaction.java` | One faction: INI parsing, ranges, change events, `executeMsg`, `executeChange` | 3557 |
| `Common/interfaces/Faction.java` | Faction API and enums (`Align`, `MiscTrigger`, change directions) | 1903 |
| `Abilities/Diseases/Disease_Smiles.java` | Contagious effect that a SMILE social can start | 171 |
| `Abilities/Properties/Prop_Socials.java` | Socials defined on an object or room through property text | 177 |
| `Abilities/Prayers/Prayer.java` | Alignment check for prayers (`prayerAlignmentCheck`, lines 372-419) | 432 |
| `Abilities/Misc/InstanceArea.java` | Resets faction timers and counters when an instance is created (line 1914) | 2669 |
| `WebMacros/MudInfo.java` | Macro expansion for screen texts (`@MudInfo?NAME@` and others) | 87 |
| `Commands/Top.java` | TOP command; serves the leaderboard templates | 292 |

## How it works

### 1. Socials: the data format

`resources/socials.txt` is read once and parsed by `putSocialsInHash` (`Libraries/Socials.java:51-204`). Each line has nine tab-separated fields:

| Field | Meaning | Example: ACCUSE with a target (`socials.txt:6`) |
|---|---|---|
| 1 | Two-letter code: actor visibility, then others/target visibility | `ww` |
| 2 | Name, optionally with a target variant (`<T-NAME>`, `SELF`, `ALL`, `<I-NAME>`, `<E-NAME>`, a direction) | `ACCUSE <T-NAME>` |
| 3 | Text shown to the actor | "You look accusingly at <T-HIM-HER>." |
| 4 | Text shown to the room | "<S-NAME> looks accusingly at <T-NAMESELF>." |
| 5 | Text shown to the target | "<S-NAME> looks accusingly at you." |
| 6 | Text when the target is absent or invalid | "Accuse somebody who's not even there ??" |
| 7 | MSP sound file (90 socials have one, e.g. `bark.wav`) | empty |
| 8 | Zapper mask: who may use the social | empty in all 843 |
| 9 | Flags, comma-separated (`CONFIRM`, `TARG_CONFIRM`) | empty in all 843 |

The parser's empty-field handling matters for reading the file. A line needs a code, a name and at least a source text; trailing fields may be missing and keep their defaults (`Socials.java:134-199`). The code is decoded in two switch blocks (`Socials.java:65-132`):

| Letter | As actor (first letter) | As others or target (second letter) |
|---|---|---|
| `W` | `MSG_SPEAK` | `W`: `MSG_SPEAK` |
| `M` | `MSG_HANDS` (small hand movement) | `T`: `MSG_HANDS` |
| `S` | `MSG_NOISE` | `S`: `MSG_NOISE` |
| `O` | `MSG_NOISYMOVEMENT` | `V`: `MSG_NOISYMOVEMENT` |
| `Q` | `MSG_SUBTLEMOVEMENT` | `Q`: `MSG_SUBTLEMOVEMENT` |
| (none) | | `O`: `MSG_OK_VISUAL` (always delivered, `CMMsg.java:1497`) |

The CMMsg masks behind these codes are `MASK_HANDS` (`CMMsg.java:944`), `MASK_MOVE` (946), `MASK_MOUTH` (950), `MASK_SOUND` (952) and `MASK_ALWAYS` (954, "anything!"). The perception check on the receiving side is in the message pipeline and was not read.

Token expansion in the texts uses `<S-NAME>` (actor, 1072 uses), `<T-NAME>` (484), `<T-NAMESELF>` (323), `<S-HIS-HER>` (224), `<S-HIM-HERSELF>` (126), `<T-HIM-HER>` (94), `<S-HE-SHE>` (49), `<T-HIS-HER>` (44) and a few more. Expansion itself was not read; the behaviour here is inferred from the three-view design (fields 3 to 5).

Storage: the parsed table is cached as a resource (`Socials.java:207-219`). The hash key is the first word of the name (`realName`, lines 226-233), so `ACCUSE`, `ACCUSE <T-NAME>` and `ACCUSE SELF` form one set (`put`, lines 235-253). Saving rewrites the whole file sorted by name (`save`, lines 1003-1056).

### 2. Finding a social for a typed command

Socials are resolved in `EnglishParser.findSocial` (`Libraries/EnglishParser.java:826`), called from two points in command resolution:

- an exact match is tried early, before channel names and built-in commands (`EnglishParser.java:1008`);
- a prefix match is tried after the built-in command lookup (`EnglishParser.java:1073`), and it sets the command word to the social's base name (1074).

Inside a set, `fetchSocial` looks for an exact target match first, then a prefix match on the argument (`Socials.java:746-763`). The target type is chosen by what the player typed: a mob gives `<T-NAME>`, an item in the room gives `<I-NAME>`, an item in the inventory gives `<V-NAME>`, and an item worn or held gives `<E-NAME>` (`Socials.java:785-806`). `SELF` and `ALL` are handled first (`Socials.java:875-876`).

The `,` prefix forces a social lookup (`Commands/Emote.java:79-97`). A social that fails its criteria is passed back as ordinary emote text.

Ambiguity: when a base name is only a prefix, `fetchSocial` returns the first key that starts with it, in the iteration order of the socials map (`Socials.java:844-851`, `820-829`). The result depends on map order, not on alphabet or frequency.

The SOCIALS command prints every base name in four columns of width 19 (`Commands/SocialsCmd.java:53`; `Socials.java:1248-1267`).

### 3. Executing a social

`DefaultSocial.invoke` (`Common/DefaultSocial.java:345-464`) does the setup:

1. Confirmation: if the social carries `CONFIRM`, the player is asked "Are you sure (Y/n)?" (line 411). If it carries `TARG_CONFIRM` and the target is a player, the target is asked "<name> want(s) to <verb> you. Is this OK (Y/n)?" and a refusal produces "<name> forbid(s) you to <verb>." (lines 432-462).
2. Target resolution: with no explicit target, the room is searched with `fetchFromMOBRoomFavorsMOBs` and retried with numbered names (`X.2`) until the match is visible (lines 370-381). An exit target is dropped when the social is not targetable (lines 382-387).
3. A social that cannot target the object is redirected to a matching variant (lines 388-398).

`invokeIntern` (lines 466-577) then picks one of four message shapes, each sent through `R.okMessage` and then `R.send`:

- no target: actor text and room text (lines 507-523);
- target present: actor, target and room text (lines 524-575);
- target missing or wrong kind: the failure text, with the actor's source code (lines 490-506).

Empty text fields are dropped (lines 474-488). The `auto` flag sets `MASK_ALWAYS` on the message (lines 494, 511, 527). A successful social bumps two counters: the coffee-table statistic `STAT_SOCUSE` and the achievement event `SOCIALUSE` (lines 502-503, 519-520, 535-536).

Role-play experience: a social aimed at a player or NPC can award RP experience (lines 538-573). The amounts come from `RP_SOCIAL_PC`, `RP_SOCIAL_NPC` and `RP_SOCIAL_OTH` with a cool-down (`awardRPXP`, lines 305-317). The default `RPAWARDS=` is empty, so this is off out of the box (`coffeemud.ini:776-802`).

Action cost: each social takes a share of a player's action budget. The default is `DEFSOCTIME=0.10` out of play and `DEFCOMSOCTIME=1.0` in combat (`coffeemud.ini:955-965`; `CMProps.java:1291-1305`). Per-social exceptions are a comma list of IDs with costs (`coffeemud.ini:955-958`). `StdMOB.calculateActionCost` applies the social's `checkedActionsCost` when the command is queued (`MOBS/StdMOB.java:2290-2308`).

Messages, by example (short texts only): "Accuse who??" (failure text of ACCUSE, `socials.txt:6`); "Perhaps you should shower your affections on someone who is here?" (failure of BEARHUG); "Are you sure (Y/n)?" (`DefaultSocial.java:411`).

### 4. Socials that do more than print

- **SMILE can start a contagious effect.** After a SMILE with a target is delivered, `invokeIntern` checks: the social name starts with "SMILE"; the actor has charisma 16 or more; both are the same race; a 1-in-100 roll (`rollPercentage()==1`) succeeds; neither party has the non-reproductive code; and the genders differ (`Common/DefaultSocial.java:555-567`). If all hold, `Disease_Smiles` is invoked on the target. The disease lasts 10 ticks and shows its effect every 2 ticks (`Abilities/Diseases/Disease_Smiles.java:90-99`). On its last tick, a failed save swaps it for `Disease_Giggles` (lines 148-157). The check matches by name prefix, so it is brittle (see Problems).
- **Factions react to socials.** A social performed by a faction holder, aimed at a mob, can trigger faction changes (`Common/DefaultFaction.java:1710-1743`). The shipped `inclination.ini` has 25 such rules, for example applauding, greeting, saluting and smurfing (`resources/factions/inclination.ini:255-282`).
- **Chat reacts to emote text.** A chatty NPC reads the room text of other people's emotes and the target text of emotes aimed at it (`Behaviors/MudChat.java:1634-1676`). Several `chat.dat` emote triggers repeat social wording, for example `[looks accusingly]` (`resources/chat.dat:101`), which matches the target text of ACCUSE (`resources/socials.txt:6`), and `[apologizes to you]` (`chat.dat:109`), which matches APOLOGIZE (`socials.txt:19`).
- **Objects and rooms can define socials.** `Prop_Socials` parses its own `NAME`, `TARGET`, `SRCCODE`, `OTHCODE`, `YOUSEE`, `OTHSEE`, `TARGSEE`, `NOTARGSEE`, `MSPFILE`, `ZAPMASK`, `FLAGSTR`, `WORNONLY`, `MASK` and `LOAD=` keys (`Abilities/Properties/Prop_Socials.java:55-112`). `LOAD` pulls lines from a file.
- **Emotes from behaviours.** `Prop_UseEmoter` looks up a social and invokes it with no target (`Abilities/Properties/Prop_UseEmoter.java:487-489`). The `Mood` effect schedules random "passive socials" with a random interval (`Abilities/Misc/Mood.java:430-445`).
- **Breeding gate.** `AnimalHusbandry` looks up the MATE social as a gate step. If the pair can breed and the wife does not already have a `Pregnancy` effect, it applies a fertility chant to the pair, and further effects follow (`Abilities/Common/AnimalHusbandry.java:125-145`; the rest was not traced).

### 5. MUDChat: the chat.dat language

`chat.dat` begins with a legend (lines 1-100). The essentials:

- **Lines** start with a marker: `(` a say-pattern, `[` a targeted emote pattern, `{` an untargeted emote pattern, `<` a pattern checked every mob tick (with an optional percentage), `*` before a pattern means combat only, `L` means "only when an LLM is installed", `.` means "only when no LLM is installed", `>` starts a named group, `@` links to another group, `%` includes a file, `"` writes a log line, `#` is a comment, `${NAME=value}` defines a variable (`MudChat.java:494-572`).
- **Responses** are a weight digit 1-9 followed by text. `~` separates several responses on one line. The prefixes are `"` (say), `:` (emote), `!` (run as a command) and a backquote (LLM prompt). With no prefix and a target present, the text becomes `sayto "<name>" <text>` (`MudChat.java:1121-1196`).
- **Pattern operators:** `|` (or), `&` (and), `~` (and not), parentheses, `^` (match at start), `=` (exact), `/` (zapper mask). The grammar is parsed by `parseExpression` (`MudChat.java:1241-1419`) and evaluated strictly left to right with no precedence. `A | B & C` is therefore `(A or B) and C` (`MudChat.java:1508-1553`, the `rollingTruth` loop).
- **Keys:** each pattern is indexed under its longest word (`parseExpression`, lines 1302-1314). A lookup collects the entries that share a word with the input, plus entries with no key (`getChatEntries`, lines 813-826), and sorts them by file order (`comparator`, lines 802-811). The first match wins.
- **Variables** in responses (`mcFilter`, lines 965-1047): `$n` (speaker), `$t` (target), `$r` (rest of the sentence after the match), `$w` (whole message), `$$`, `${STAT}` (speaker stat), `$<STAT>` (target stat), `$%fn(...)%` (a MOBPROG function call). Stats come from `CoffeeMaker.getAnyGenStat` (`MudChat.java:961`).
- **Groups:** `>` names one or more groups, and the group names are matched in upper case (`newChattyGroup`, lines 396-437). `@default` copies the default group's entries into the current group (`MudChat.java:586-612`). `%file` inserts another file (613-622). `$@` switches the conversation to another group for six minutes (1094-1113).

### 6. MUDChat: runtime behaviour

**Which group a mob uses** (`getMyBaseChatGroup`, `MudChat.java:736-768`): the explicit parameter from the behaviour's `parms` (`chatDatFilename=group`), then the mob's name with the article removed, then its race name, then its class name, then the first group (the default).

**Triggers.** Four kinds of event start a reaction:

1. *Speech* (`executeMsg`, `MudChat.java:1603-1632`): the speaker is a player who is addressing the mob, or speaks in a room with fewer than three player characters present and no active talk delay (`numPCInhabitants()<3`, line 1609). The mob does not react twice to the same speaker until the reaction resets (`checkReactionReset`, lines 1555-1577).
2. *Emote* (1634-1676): a player's emote aimed at the mob is matched against `[` entries, and any other visible emote against `{` entries. Both use the emote text, with punctuation stripped and lower-cased.
3. *Another NPC speaks* (1634-1636): sets `talkDown` to 8 (`TALK_WAIT_DELAY`, line 91). The mob stays quiet for eight ticks.
4. *Random per tick* (`tick`, lines 1711-1729): `<N` entries roll 1-100 on each mob tick and fire when the roll is at most N (`match`, lines 1465-1505). The shipped data has no such entries (see Coverage).

**Responses and timing.** `queResponse` (`MudChat.java:1070-1214`):

- Chooses one response line with probability proportional to its weight (`roll(1,total,0)`, lines 1080-1090).
- Issues every `~` part of the chosen line, in order (lines 1117-1212). A part that is blank stops the rest (lines 1119-1120).
- Skips a command that is already queued (lines 1200-1208).
- Queues each command with a delay of `RESPONSE_DELAY` = 2 mob ticks (line 90, line 1210).

The tick then runs one queued command per tick (lines 1731-1751). A queued command whose combat state no longer matches is dropped (lines 1741-1742). A new mob starts with `tickDown` = 3 (`setParms`, line 381; `tick`, lines 1695-1699), so it does not react during its first few ticks.

**Personality.** `${PERSONALITY}` (`getVarValue`, lines 891-959) builds a short personality for the mob. It seeds `java.util.Random` with the hash of the mob name plus its start room (lines 907-909), picks one or two traits (the default is two, `maxTraits`, line 893), and draws them from the list for the mob's alignment and inclination (lines 916-943). A `Mood` effect's text is appended (lines 951-953). The lists are in `resources/lists.ini:2003-2050` (35 to 43 traits per key, see Content).

**LLM mode.** `isLLMInstalled` is checked when entries are gathered (`MudChat.java:838-845`), when a tick-based entry is considered (1718-1719), and when a backquote reply is queued (1146). With no LLM, `L` lines are skipped and `.` lines are used. With an LLM, the reverse. A backquote response calls `getLLMSession` (lines 1049-1068) and the reply runs on a separate thread (1152-1188). The session expires after six minutes idle (line 1052). The LLM backend is a LangChain4j binding loaded by reflection for Ollama, Anthropic or OpenAI (`Libraries/CMProtocols.java:4645-4690`). It is off by default: `LANGCHAIN4J_JAR_PATH` and `LANGCHAIN4J_LLM_TYPE` are empty (`coffeemud.ini:2219` and `2227`), and the timeout is 20 seconds (`CMProtocols.java:74`).

**Which mobs use it.** Fourteen Java sites call `getBehavior("MudChat")`, among them the animal and creature mobs (`MOBS/Dog.java:57`, `MOBS/Cat.java:58`, `MOBS/Puppy.java`, `MOBS/Kitten.java`, `MOBS/Duck.java`, `MOBS/Coyote.java`, `MOBS/Penguin.java`), the teacher (`MOBS/Teacher.java:57`) and `MOBS/HeavenlyServent.java:58`. Some abilities also attach it, for example `Chant_SpeakWithAnimals`. `QuestChat` extends `MudChat` (`Behaviors/QuestChat.java:35`).

### 7. Factions: the data model

Each faction is one INI file under `resources/factions/`. Recognised keys (`DefaultFaction.initializeFaction`, `Common/DefaultFaction.java:530-641`):

| Key | Meaning |
|---|---|
| `NAME`, `MINIMUM`, `MAXIMUM` | Display name and the scale bounds (swapped if reversed, lines 558-562) |
| `SCOREDISPLAY`, `SHOWINFACTIONSCMD`, `SPECIALREPORTED`, `EDITALONE`, `INHERITED` | Where and whether the faction is shown |
| `DEFAULT` | Starting value for manual additions; first matching zapper wins, else 0 (`findDefault`, lines 1166-1177) |
| `AUTODEFAULTS` | Starting value when a mob or player is created or enters a room; first matching zapper wins, an empty mask matches everyone, no match means no faction (`findAutoDefault`, lines 1179-1192) |
| `AUTOCHOICES`, `CHOICEINTRO` | Values offered at character creation, and the intro text file (`CharCreation.java:3856-3869`) |
| `RATEMODIFIER` | Multiplier on every change (default 1.0) |
| `EXPERIENCE` | `NONE`, `EXTREME` and similar (read at line 564; the effect was not traced) |
| `RANGEn` | `low ; high ; label ; CODE ; alignment-equivalent` (`DefaultFactionRange`, lines 2701-2713) |
| `CHANGEn` | A change rule (section 8) |
| `ABILITYn` | Ability gate: `flag-or-id ; low ; high`, with `!` to invert the range (lines 3445-3460; check at lines 1235-1254) |
| `FACTORn` | `gain ; loss ; zapper` multiplier for matching mobs (`findFactor`, lines 1256-1264) |
| `RELATIONn` | Another faction that receives a scaled copy of each change (lines 1986-1996) |
| `REACTIONn`, `AFFBEHAVn` | Reaction and behaviour hooks (lines 604-609, 635-639) |

Loading: factions load on demand (`Libraries/Factions.java:263-300`). `reloadFactions` preloads the `FACTIONS=` list (`Factions.java:94-101`), and the shipped setting preloads only `alignment.ini` (`coffeemud.ini:497-502`). `Factions.java:158` is named `buildFactionFromXML`, but it parses INI text (see Problems).

Per-mob storage: `StdMOB.addFaction` clamps the value to the faction's minimum and maximum (`MOBS/StdMOB.java:5776-5795`). `fetchFaction` returns `Integer.MAX_VALUE` when the mob has no such faction (lines 5825-5831).

Two enum sets encode the alignment scales (`Common/interfaces/Faction.java:1841-1852`): `Align` values `EVIL`, `NEUTRAL`, `GOOD` (the alignment group) and `CHAOTIC`, `MODERATE`, `LAWFUL` (the inclination group). `getAlignPurity` and `isAlignmentLoaded` use the fifth column of each range to group ranges (`Libraries/Factions.java:254-260`, `873-911`).

Auto-assignment: `Factions.updatePlayerFactions` (`Libraries/Factions.java:739-770`) walks the loaded factions and assigns `AUTODEFAULTS` where the mob has no value. It is called from `MOBS/StdMOB.java:1336` and when a mob enters a room (`Locales/StdRoom.java:784`). Only loaded factions take part, so a faction not preloaded may not be assigned (unverified: which factions are loaded at runtime depends on other code).

### 8. Factions: change events

**Rule syntax.** `CHANGEn = TRIGGER(PARMS);DIRECTION;FACTOR%;FLAGS;ZAPPER` (`resources/factions/alignment.ini:228-230`, the documentation block). `createChangeEvent` (`Common/DefaultFaction.java:2081-2103`) and the event constructor (lines 2371-2397) split the string. The trigger is resolved by `setEventID` (lines 2400-2464) in this order:

1. a `MiscTrigger` name: MURDER, TIME, ADDOUTSIDER, KILL, BRIBE, TALK, MUDCHAT, ARRESTED, SOCIAL, SINK, SUNK, AREAKILL, AREAASS, AREAEXPLORE, DYING, CMMSG (`Common/interfaces/Faction.java:1314-1319`);
2. an ability class description, domain description or flag description;
3. an ability ID;
4. a social name, or `NAME *` for any usage;
5. a CoffeeMud message-type name.

The return value of `setEventID` is ignored by the constructor (`DefaultFaction.java:2385`), so an unresolved trigger is stored under an empty key and never fires. The shipped data has three such rows (see Problems).

**Trigger parameters** are `NAME=value` pairs (`setTriggerParameters`, lines 2580-2588):

- `WAIT=n` is a cool-down in ticks, stored as `n × TICKTIME` milliseconds (line 2587), and enforced per mob and per event (`checkApplyEventWait`, lines 1266-1278);
- `WITHIN=n` requires that the event's timer for the mob was reset within `n` ticks (`applies`, lines 2540-2566);
- `CHANCE=n` is a percent chance (see the bug in section 8, amount formula);
- `PCT`, `MASK`, `ID`, `REGEX`, `SMSG`, `TMSG`, `OMSG`, `SRC`, `TGT` and `OTH` are read by the individual handlers.

**Flags** after the direction and factor (`setFlags`, lines 2521-2531): `OUTSIDER` lets the change apply when the target has no standing in the faction; `SELFOK` lets it apply to self-targeted actions; `JUST100` fixes the base at 100 (ignores the level difference); `XP=n` and `RPXP=n` award experience; `RESTIME=event` resets the WITHIN timers of another event; `ANNOUNCE="text"` posts to the faction-announcement channels (`executeChange`, lines 1961-1972).

**Dispatch.** `executeMsg` (`Common/DefaultFaction.java:1281-1749`) switches on the message's minor type:

- entering a room (`TYP_ENTER`, 1286-1321): AREAEXPLORE, once per room visited; `PCT=0` fires on the first room in the area, otherwise at a percentage visited;
- death (`TYP_DEATH`, 1323-1439): KILL and MURDER over combat beneficiaries (1330-1346); AREAASS for the highest-level mob in the area (1350-1384); AREAKILL for a percentage of the area's population (1386-1420); DYING (1425-1437);
- sinking (1440-1460): SINK and SUNK for boats;
- speech (`TYP_SPEAK`, 1496-1553): TALK matches a Java regex (`REGEX`, default `.*`) against what was said;
- MUDCHAT (1554-1607): a player converses with a ChattyBehavior mob, and the mob's last thing said differs from the player's line;
- message types (1611-1664): any CMMsg type, filtered by `SMSG`, `TMSG`, `OMSG`, `SRC`, `TGT`, `OTH`;
- abilities (1665-1709): an ability cast with a usage message;
- socials (1710-1743): the faction holder performs a social at a mob. If any generic `SOCIAL` rule exists, the per-social rules are not consulted at all (see Problems).

**Time-based rules.** `Factions.tick` runs every 10 mob ticks, about 40 seconds at the default 4-second tick (`application/MUD.java:1672` registers it; `Libraries/Factions.java:807-870` runs it). Each round it walks the online sessions and applies `ADDOUTSIDER` rules to players who have no standing (`addOutsidersAndTimers`, 772-804) and `TIME` rules to players who have it. `TIME(ROUNDS=n)` counts rounds in a counter shared by all players (lines 789-801). `TIME` with no parameters fires every round. The tick does nothing when no session is connected (line 809).

**Amount formula** (`executeChange`, `DefaultFaction.java:1785-2005`). The base is 100 for `JUST100` or when the two parties are the same level. Otherwise, with `EXPRATE` (default 6, `coffeemud.ini:406`):

```
levelDiff = targetLevel - sourceLevel
if levelDiff < -EXPRATE:  base = 0
else:                     base = 100 + 100 * min(levelDiff / EXPRATE, EXPRATE)
```

(lines 1810-1827.) So a target 3 levels below gives 50, a target 12 levels above gives 300, and the base is capped at 700. Then by direction (lines 1888-1958):

| Direction | Adjustment |
|---|---|
| `UP` / `DOWN` / `TOWARD` / `AWAY` | sign × `round(RATEMODIFIER × base)` × factor × `findFactor` (`TOWARD` and `AWAY` choose the sign by comparing with the target's value, lines 1922-1930) |
| `OPPOSITE` | sign away from the target; magnitude `base/2 + round(base/2 × abs(src − tgt) / difference)` (lines 1904-1916) |
| `MINIMUM` / `MAXIMUM` | `minimum − value` or `maximum − value` (factor not applied) |
| `REMOVE` | the faction is removed (sentinel `Integer.MAX_VALUE`) |
| `ADD` | `findDefault` if the mob has no value |

Worked example: the inclination rule `APPLAUD *(WAIT=10);UP;5%;OUTSIDER JUST100` gives base 100, then 100 × 0.05 = +5 per use, with a 40-second cool-down (`resources/factions/inclination.ini:255`).

Other steps: a per-event percentage gate (lines 1805-1808, see the CHANCE bug); the `RESTIME` resets (1833-1886); the `ANNOUNCE` message; the change message `facMsg` through `R.okMessage`, so other listeners can alter the value (1973-1981); relations (1986-1996); and the final apply in `StdMOB` (`MOBS/StdMOB.java:3577-3586`), where the sentinel removes the faction and any other value is adjusted by the amount.

### 9. Factions: what the values do

**Ability gates.** A faction's ABILITY line lists the abilities it restricts. `getAbilityUsage` matches an ability by flag, class, domain or ID (`DefaultFaction.java:1199-1227`), and `canUse` checks the mob's value against the range (1235-1254). `StdAbility.appropriateToMyFactions` (`Abilities/StdAbility.java:2865-2888`) walks the mob's factions. Alignment has 31 gates, for example paladin prayers that need a good value and a few that need an evil one (`alignment.ini:247-277`). Inclination has 31 (`inclination.ini:297-327`).

**Prayer success.** `prayerAlignmentCheck` (`Abilities/Prayers/Prayer.java:372-419`) applies to player prayers:

- a Holy prayer uses the GOOD purity (`hq=1000`), an Unholy prayer the EVIL purity (`hq=0`), and anything else the NEUTRAL purity minus 10 (lines 384-398);
- purity is the percentage distance from the ideal end (`Factions.java:873-911`: GOOD is `|pct − pct(top)|`, EVIL is `|pct(bottom) − pct|`);
- the prayer succeeds when `d100 > basis` (line 401), so a fully good cleric always succeeds with a Holy prayer, and a mid-scale one succeeds about half the time;
- a failure message is shown: "The goodness of <prayer> disrupts your prayer." (line 410), "The non-good nature of <prayer> disrupts your thought." (413), and their evil counterparts (407, 416).

**Alignment equivalence.** The fifth column of each range is used for grouping (`DefaultFactionRange` at `DefaultFaction.java:2701-2713`). In `alignment.ini` the "somewhat evil" band (`alignment.ini:102`) is grouped as NEUTRAL, not EVIL, so only the pure and "evil" bands count as evil for purity. This may be intentional; it is recorded here as a fact of the data.

**Instances.** `InstanceArea` resets a participant's faction timers and counters when it creates the instance (`Abilities/Misc/InstanceArea.java:1914-1915`). That scopes WITHIN windows and per-event counters to one instance, which is how the Triumph challenges are bounded.

### 10. Triumph: the end-game faction

`triumph.ini` defines one faction with five tiers (`triumph.ini:105-109`):

| Points | Label | Code |
|---|---|---|
| 0 to 100 | Struggling to Triumph | TRIUMPH0 |
| 101 to 1,000 | Triumphant I! | TRIUMPH1 |
| 1,001 to 10,000 | Triumphant II! | TRIUMPH2 |
| 10,001 to 100,000 | Triumphant III! | TRIUMPH3 |
| 100,001 to 999,999 | Triumphant IV! | TRIUMPH4 |

Its 17 rules (`triumph.ini:234-250`) are all restricted by the mask `+AREAINSTANCE` (instanced areas only). They use `JUST100`, so each gives a fixed base of 100 times a 1 percent factor, which is +1 point per event (and −1 for DYING). Each area rule has a WAIT of 9999 ticks, about 11 hours, so each fires at most about once per mob per event per instance.

| Rule | Trigger and threshold | Change | Announcement |
|---|---|---|---|
| CHANGE1 | DYING | −1 | none |
| CHANGE2 | AREAKILL, 50% of population | +1 | "Triumphant kills achieved!" |
| CHANGE3 | AREAASS, 100% | +1 | "Triumphant assassinations achieved" |
| CHANGE4 | AREAKILL, 80% | +1 | "Triumphant slaughter achieved" |
| CHANGE5 | AREAKILL, 100% | +1 | "Triumphant massacre achieved" |
| CHANGE6 | AREAEXPLORE, 80% | +1 | "Triumphant exploration achieved" |
| CHANGE7 | AREAKILL, 80%, within 750 ticks | +1 | "Timely kills achieved!" |
| CHANGE8 | AREAEXPLORE, 80%, within 750 ticks | +1 | "Timely exploration achieved!" |
| CHANGE9 to CHANGE16 | Kills and exploration at 80%, within 600, 450, 300 and 150 ticks | +1 each | "Quick", "Fast", "Very fast" and "Blindingly fast" variants |
| CHANGE17 | AREAEXPLORE, first room in the area | 0 | "You have entered a Triumph Area!" |

Ticks are 4 seconds by default, so 750 ticks is 50 minutes and 150 ticks is 10 minutes.

The area handlers count per player per event (`data.setCounter(""+event, count)`, lines 1374-1375 and 1409-1410) and compare `count / population` with PCT (lines 1376-1378 and 1411-1413). AREAASS uses the area's maximum-level mob count, and AREAKILL the area population (`Area.Stats.POPULATION`). `DefaultFaction.java:1292-1319` handles AREAEXPLORE.

Tier arithmetic: with +1 per rule and one fire per event, the 17 rules give about 17 points on the first pass. Each event can fire again after its 9999-tick WAIT (about 11 hours), so the total grows slowly. Either way it is far short of the 101-point threshold of the first Triumph tier. The thresholds seem sized for a different scale. This is an inference from the amount formula (lines 1810-1826 and 1951-1954), not a runtime measurement.

Instance types that use this faction live in `resources/skills/areainstancetypes.txt` (for example line 66, `PLAYFACTIONS="triumph.ini"`, with a `LEADERREQ` of `-FACTION +TRIUMPH0`). That file belongs to another chapter and is cited only as the link.

### 11. Screen texts

The `resources/text/` files are shown at fixed points in the session. Each is loaded with `Resources.getFileResource` or `CMFile.text()`, and most are then passed through `CMLib.webMacroFilter().virtualPageFilter`, which expands `@Macro?ARG@` tokens:

| File | Shown when | Code |
|---|---|---|
| `text/intro.txt` (a random `intro*.txt` is chosen) | Connection greeting | `application/MUD.java:370-380` |
| `text/blocked.txt`, `text/connblocked.txt` | Blocked address, permanent or temporary (`@mins@` is replaced directly) | `application/MUD.java:329-340` |
| `text/offline.txt` | Server offline | `application/MUD.java:415` |
| `text/down.txt` | Shutdown screen, read raw by the offline server (no macros) | `application/OffLine.java:299-304` |
| `text/nologins.txt` | Login closed | `Libraries/CharCreation.java:897-901` |
| `text/newacct.txt`, `text/doneacct.txt`, `text/email.txt` | Account creation | `CharCreation.java:756-759`, `1416-1419`, `1445-1448` |
| `text/selchar.txt` | Character selection | `CharCreation.java:1824-1827` |
| `text/newchar.txt`, `text/newchardone.txt` | Character creation start and finish | `CharCreation.java:2646-2649`, `4118` |
| `text/themes.txt`, `races.txt`, `classes.txt`, `stats.txt` | Creation choices | `CharCreation.java:2936`, `3054-3089`, `3282`, `3708-3732` |
| `text/alignment.txt`, `text/inclination.txt` | Faction choice intro, if the faction has `AUTOCHOICES` | `CharCreation.java:3856-3869` |
| `text/rules.txt` | The RULES command | `Commands/Rules.java:52` |
| `text/mxp.txt` | MXP element definitions, sent when MXP is negotiated | `CharCreation.java:506-510`, `Commands/MXP.java:94-96` |
| `text/topplayers.txt`, `topaccounts.txt` (and `-prev` copies) | TOP command, served as templates with a WIDTH parameter | `Commands/Top.java:209-273` |

Colour and layout use CoffeeMud's inline codes, for example `^g`, `^H`, `^x`, `^N` and `^?` (reset), as seen in `alignment.txt` and `rules.txt`.

Macros: `WebMacros/MudInfo.java:52-87` returns the configured MUD name for `NAME`, the domain for `DOMAIN` and the mail or password flags for others. `resources/text/intro.txt` and `nologins.txt` use `@MudInfo?NAME@`. The top-list templates use a loop over ranks 1 to 10 and `WIDTH`, with arithmetic in the macro (`Top.java:209-232`). Nothing in the Java tree writes `topplayers.txt` or `topaccounts.txt`; the `-prev` copies are presumably snapshots from an external process (unverified).

`mxp.txt` defines clickable SEND links (room exits, items, mobs, shops), GAUGE bars for hit points, mana and movement, and prompt fields (lines 1-61). It is sent as raw escape sequences after `\033[6z` (`CharCreation.java:508-510`).

## Content worth mining

**The social verb catalogue.** The 338 base names, the 843 entries, and their shapes:

| Shape | Count |
|---|---|
| Base only (no target) | 318 |
| `<T-NAME>` (targets a person) | 308 |
| `SELF` (reflexive) | 204 |
| `ALL` (everyone in the room) | 3 (BRICK, MUTE, WOOT) |
| `<I-NAME>` or `<E-NAME>` (targets an item) | 4 (BOW, POINT, TOUCH, TOUCH) |
| Compass direction (POINT NORTH and so on) | 6 |

Themes, each with examples (names only; texts not reproduced):

- Greetings and farewells: HI, HAIL, WAVE, FAREWELL, GOODNIGHT, SALUTE, GREET, HANDSHAKE, HIGHFIVE.
- Affection and mood: HUG, KISS, CUDDLE, COMFORT, SMILE, WINK, BLUSH, SIGH, SULK, GRIN.
- Physical comedy and noise: BONK, BOUNCE, FART, BURP, HICCUP, SNEEZE, YODEL, WHISTLE, SNORE, ZERBERT.
- Animal and creature: BARK, MOO, HOWL, PURR, BITE, ROAR, WORM, PET.
- Silly and playful: SMURF, WIGGY, SNOWBALL, RASPBERRY, WEDGIE, NOOGIE, PIE, AIR (air guitar).
- Confrontation: ACCUSE, CHALLENGE, GLARE, THREATEN, GROWL, SLAP, PUNCH, TACKLE, STRANGLE, SHUDDER.
- Violent or gory (mild, slapstick): BLEED, CLAW, POUND, CRAMP.
- Intimate or sexual contact: a family of socials (for example RAVISH, LAPDANCE, GROPE, FRENCH, FONDLE, CARESS, SMOOCH, TWERK, STRIP, LUST, and MATE and MAKE as untargeted gags). Texts are not reproduced. Anyone running a SUD for general audiences should gate or omit these.
- Substance-themed: BEER (a six-pack gag) and a cigarette line (`socials.txt:105-106`). A keyword search for drug-trade terms found nothing.

Codes used across the file:

| Code | Entries | Meaning (actor, others) |
|---|---|---|
| `mo` | 298 | small hand gesture, visible to others |
| `ss` | 118 | audible noise, heard by all |
| `ov` | 90 | large movement, noisy to others |
| `mv` | 90 | hand gesture, noisy movement to others |
| `ww` | 78 | speech, heard by all |
| `oo` | 72 | noisy movement, visible |
| `mt` | 37 | hand gesture, hand contact with the target |
| `ot` | 28 | noisy movement, hand contact with the target |
| `ms` | 13 | hand gesture, noise to others |
| 10 others | 19 | e.g. `ws`, `sv`, `qo`, `mw`, `wv`, `wo`, `st`, `so`, `qv`, `os` |

Sound: 90 socials name a `.wav` file for MSP clients (for example BARK at `socials.txt:32` and BOUNCE at `socials.txt:82`).

**MUDChat groups** (`chat.dat`). Trigger lines and response lines per group (counts from a script over the file):

| Group (first names) | Triggers | Responses | Notes |
|---|---|---|---|
| default (everything above `>`) | 579 | 1,436 | ELIZA-style keyword talk, insults, emote replies |
| dog (dog, fido, wolf, beagle, puppy) | 5 | 11 | greetings, tail wagging, growl at "bad dog" |
| cat (cat, kitten, lion) | 2 | 3 | purring and tail talk |
| pig, horse, duck, skeleton, doll, bird, bat, chicken, insect, lizard, fox | 2 each | 3 to 4 | short fixed replies |
| papa_smurf (smurf) and smurfette | 2 and 1 | 5 and 4 | fixed replies, `@default` |
| monster, rabbit | 3 each | 6 each | emote-only reactions |
| healer (healer, cleric, doctor) | 5 | 12 | an NPC that offers healing and reacts to insults with a command |

Four named groups link to the default group with `@default`, so they inherit its keyword lines (`chat.dat:2798`, `2858`, `2925`, `2952`). The healer group has a profanity rule whose reply is the command `!kill $t`, which makes the NPC attack the speaker (`chat.dat:2947`).

The default group's themes, by sampling (no lines are quoted): greetings and small talk; family, childhood and feelings questions in the style of a therapist bot (`.(i feel )`, `.(my father )`, `.(remember )`); money, work, dreams, faith and identity; a set of insults and profanity responses; dating, body and pregnancy questions in the same bot style (for example `chat.dat:2198-2200`); and some emote reactions. A few triggers key on personal names; those are not reproduced.

Minors: a keyword search for child-related words (child, kid, baby, boy, girl, little) found childhood and family questions in `chat.dat` (for example 1290-1294 and 2270) and playful or endearing wording in socials (`socials.txt:15`, `195`, `269`, `348`). One adult-themed STRIP text (`socials.txt:706`) uses "baby" as a term of address in a scene between adults, not about a child. Two canned questions about being alone with a girl or a boy (`chat.dat:1708-1714`) do not name an age and are ambiguous; they are not described further and should be dropped rather than ported. None of these places a child in a sexual context.

**Personality traits** (`resources/lists.ini:2003-2050`). Six lists, keyed by alignment and inclination (35 to 43 traits each):

| Key | Examples |
|---|---|
| LAWFUL | Disciplined, Honorable, Organized, Dutiful, Stern, Loyal |
| MODERATE | Balanced, Flexible, Pragmatic, Diplomatic, Versatile |
| CHAOTIC | Impulsive, Free-spirited, Spontaneous, Reckless, Erratic |
| GOOD | Compassionate, Altruistic, Kind, Merciful, Generous, Heroic |
| NEUTRAL | Self-interested, Indifferent, Practical, Survivalist, Detached |
| EVIL | Cruel, Malicious, Vengeful, Deceitful, Greedy, Ruthless |

**Faction tables.**

Alignment (`resources/factions/alignment.ini:100-106`): pure evil (−10,000 to −7,500), evil (−7,499 to −4,500), somewhat evil (−4,499 to −1,500), pure neutral (−1,499 to 1,500), somewhat good (1,501 to 4,500), good (4,501 to 7,500), pure goodness (7,501 to 10,000). Auto-defaults: demons and devils start at −10,000 and everyone else at 0 (`alignment.ini:69`).

Inclination (`resources/factions/inclination.ini:101-107`): pure chaotic, chaotic, somewhat chaotic, moderate, somewhat lawful, lawful, pure lawful, over the same bands. Auto-defaults: fighters and paladins start at 10,000, barbarians at −5,000, thieves (base class) and devils at −10,000, others at 0 (`inclination.ini:69`).

Triumph tiers: see section 10.

**Screen text.** The login, creation and help screens are a ready source of short, well-written prose for a SUD's first-run experience: the alignment explanation (`alignment.txt`), the class, race, stat and theme blurbs (`classes.txt`, `races.txt`, `stats.txt`, `themes.txt`), and the four-line rules text (`rules.txt`).

## Notable design ideas

- **A social is four sentences plus a visibility code.** Each social declares what the actor, the room and the target see, and how loud it is. That is a compact way to write perspective-dependent narration, and the failure text handles the "nobody there" case in the same row.
- **Chat as data with fall-through.** A line is a pattern with weighted replies, first match wins, and groups inherit from a default with `@default`. The language is small enough to write by hand and to read back.
- **Two modes in one file.** The `.` and `L` prefixes let the same group be a fixed-reply bot or an LLM-driven character, depending on whether the backend exists. The fallback is authored into the data.
- **NPCs react to emotes, not just speech.** Socials feed the chat engine, so a player's gesture can make an NPC respond. Speech and emotes share one matching path.
- **Pacing built in.** Replies wait two ticks, one command runs per tick, a repeated command is dropped, and a talking NPC silences the others for eight ticks. This keeps chatty rooms from scrolling away.
- **Two-axis alignment.** Good/evil and lawful/chaotic are separate scales, each with named bands. The 3-by-3 grid gives characters and NPCs a readable label without a single number.
- **Change size scales with level difference.** A kill of a higher-level target is worth more, and a much lower one is worth nothing, with the base capped. This ties reputation to the level system, and `JUST100` switches the scaling off per rule.
- **Cool-downs and windows as data.** `WAIT` (per mob, per event), `WITHIN` (time since a reset) and `RESTIME` (one event resets another) are simple words in the rule string, and instances reset them automatically.
- **Personality from a seed.** A mob's traits are chosen from its name and location, so the same NPC always has the same personality without storing it.
- **Triggers name things by ID.** A rule can refer to a social, an ability, a domain or a message type with the same syntax, which makes the reputation system easy to extend.

## Problems and cruft

Each item is from reading the code; runtime effects are marked unverified.

1. **Chat `@` links add the wrong object.** In `parseChatData`, the `@` loop copies each inherited entry and sets `orderNum` and `fromGroup` on the copy, then adds the original (`Behaviors/MudChat.java:596-601`; the tick-rule loop at 603-608 correctly adds the copy). Inherited entries keep the source group's order number, so they can sort among the current group's entries in the first-match order. Runtime effect unverified.
2. **`${PERSONALITY:n}` with a number throws.** `maxTraits` is parsed from `var.substring(x+1)`, where `x` indexes the split parts, so it reads text such as `RSONALITY:3` and gets 0 (`MudChat.java:898-899`). `r.nextInt(0)` then throws (line 910). The shipped data uses only the bare `${PERSONALITY}` (`chat.dat:94`), so this is latent.
3. **Empty trait lists throw.** If neither the alignment nor the inclination faction is loaded, `keys` is empty, `traits` is empty, and `traits.get(r.nextInt(0))` throws (`MudChat.java:916-947`). Edge case, unverified in practice.
4. **Comparator violates its contract.** `comparator` returns 1 when the order numbers are equal (`MudChat.java:802-811`), and ties are possible across groups because numbers are per group. Lists are small, so the risk is low.
5. **A blank response part stops the reply.** `if(finalCommand.trim().length()==0) return;` (`MudChat.java:1119-1120`) drops every later part of the same line. The comment asks "why not continue?"
6. **Pronoun swap is naive.** The `:` emote replaces every " her " and " his " with the speaker's possessive (`MudChat.java:1128-1129`), whatever the sense of "her".
7. **Tick-pattern `<` without a number always fires.** `CMath.s_int("")` is 0 and 0 ≤ any roll (`MudChat.java:1481`). No shipped entry uses `<`, so this is latent.
8. **CHANCE is inverted.** `executeChange` returns when the roll is below the chance (`Common/DefaultFaction.java:1805-1808`): `if(rollPercentage() < pctChance) return;`. A `CHANCE=10` rule therefore applies about 90 percent of the time. No shipped rule uses CHANCE, so this is latent.
9. **MINIMUM and MAXIMUM misbehave for non-members.** `executeChange` reads the source's value, which is `Integer.MAX_VALUE` when the mob lacks the faction (`DefaultFaction.java:1787`, `MOBS/StdMOB.java:5829`). `MAXIMUM` then computes `maximum − MAX_VALUE`, a large negative, which sets the faction to the minimum (line 1893). `MINIMUM` overflows the int and sets the faction to the maximum (line 1896). No such rule is in the shipped data, and the path to a non-member source was reasoned, not run.
10. **WAIT is not enforced for non-members.** `checkApplyEventWait` returns true without recording the time when the mob has no faction data (`DefaultFaction.java:1266-1278`). A rule that adds the faction can therefore fire repeatedly for a non-member.
11. **A generic SOCIAL rule hides per-social rules.** If any `SOCIAL` rule exists, `findSocialChangeEvents` is not consulted (`DefaultFaction.java:1717-1719`). Shipped data has no generic SOCIAL rule.
12. **Three rules never fire.** `inclination.ini:243` names `CHANT_EARTHFEED`, which is not an ability ID; the Earthfeed chant is `Chant_Bury`, displayed as "Earthfeed" (`Abilities/Druid/Chant_Bury.java:44`). `inclination.ini:257` names `CLEAN *`, and `:263` names `MEDIDATE *`; no social named CLEAN or MEDIDATE exists (MEDITATE does, `socials.txt:173`). The constructor ignores the failed trigger (`DefaultFaction.java:2385`), so these are stored under an empty key and never run.
13. **Unbalanced quote.** `triumph.ini:241` (CHANGE8) has an odd number of quotation marks in the MASK value; the other sixteen rules close the quote before the parenthesis. Effect unverified.
14. **A personal name is hard-coded.** `triumph.ini:73` gives an AUTODEFAULTS grant to one named character. Not reproduced here. Data of this kind is not worth copying.
15. **Stale comment.** `DefaultFaction.java:1948-1949` refers to "1.42 and 150", constants that do not appear in the surrounding code.
16. **Misnamed loader.** `Factions.java:158` is `buildFactionFromXML`, but it parses INI text.
17. **Documentation disagrees with the code.** The change-factor comment says "normal values are between 0 and 150 points" (`alignment.ini:194`, `inclination.ini:193`). With the level formula, the base is 100 at equal levels and up to 700 (section 8).
18. **Prefix social lookup is map-order dependent.** See section 2 (`Socials.java:844-851`).
19. **Grammar slips in socials.** "touchs you" (`socials.txt:760-761`) and "point(s)" constructions (`socials.txt:513`) read awkwardly.
20. **Stray text in a screen.** The first line of `resources/text/email.txt` is a literal `%0D`, which would be shown to the player.
21. **Fragile name-based disease hook.** The SMILE check matches the name prefix `SMILE` (`DefaultSocial.java:555`), so a renamed or new social with the same prefix would inherit the effect.
22. **Duplicated wording.** Chat emote triggers copy social wording by hand (section 4). Changing a social's text silently breaks the matching trigger.
23. **Leaderboard data is outside the tree.** The templates are read, but nothing in the Java tree writes them (section 11).

## Takeaways for a Python SUD

**Socials: keep the four-sentence idea, drop the codes.** A TSV or YAML table with one social per row (name, target kind, actor text, room text, target text, no-target text, cost) is easy to edit and to port:

```python
@dataclass(frozen=True)
class Social:
    name: str                  # "ACCUSE"
    target: str | None         # None, "SELF", "NPC" or "ITEM"
    actor: str                 # "You look accusingly at {tgt.obj}."
    room: str                  # "{actor.name} looks accusingly at {tgt.name}."
    tgt_msg: str | None
    no_target: str | None
    cost: float = 0.0          # in turns or ticks; default 0 in turn-based play
    tags: frozenset[str] = frozenset()   # {"confirm", "adult"}
```

Resolve names exactly first, then by prefix in sorted order, to avoid the map-order issue. Keep a visibility field (who sees it, who hears it) if NPCs are in the room. Use one token mechanism (`str.format_map` with a viewer-aware object) for pronouns.

**MUDChat: port the matcher, not the Java.** A small rules engine in Python is a few hundred lines:

- a `Rule` holds a parsed pattern (an AND/OR/AND-NOT tree, evaluated left to right, as the original does) and a list of weighted `Reply` objects;
- a `Reply` is one of say, emote, command or LLM prompt;
- groups are dictionaries of rules with an `inherit` link to a default group;
- selection uses `random.Random(seed)` so tests can be repeatable;
- replies go onto a small queue with a delay measured in game turns, not wall-clock ticks.

Keep the "one reaction per turn" and "no duplicate queued command" rules; they stop a chatty NPC from flooding a single-player screen.

**Factions: a dataclass and a loader.** Load each faction from TOML or INI into:

```python
@dataclass
class Range:
    low: int; high: int; label: str; code: str; align: str

@dataclass
class ChangeRule:
    trigger: str          # "APPLAUD", "AREAKILL", "TIME", "ability_id"
    direction: str        # UP, DOWN, OPPOSITE, TOWARD, AWAY, MIN, MAX, REMOVE, ADD
    factor: float         # 0.05 for "5%"
    zapper: str = ""      # who qualifies
    wait: int = 0         # cool-down in turns
    within: int | None = None
    just100: bool = False
```

The base amount, if you keep the level scaling, is one line:

```python
def base_change(src_level: int, tgt_level: int, expr_rate: int = 6) -> float:
    if expr_rate <= 0:                      # 0 turns level scaling off
        return 100.0
    if tgt_level - src_level < -expr_rate:
        return 0.0
    level_factor = min((tgt_level - src_level) / expr_rate, expr_rate)
    return 100.0 + 100.0 * level_factor
```

The single-player version can drop OUTSIDER, PvP KILL and MURDER, and group-kill credit. Keep the cool-downs (WAIT), the windows (WITHIN) with a reset on new quest or area, the TIME drift, and the ability gates. Gate abilities such as spells or prayers with a success roll of `d100 > basis`, where the basis is the distance from the ideal end of the scale.

**Personality: seed from a stable hash.** Python's `hash()` is randomized per process, so use `zlib.crc32` or `hashlib` on the NPC's name and location, then `random.Random(seed).sample(traits, k)`. Store nothing.

**Screen texts: plain templates.** Keep the `.txt` screens as templates with `string.Template` or `str.format` placeholders for the game name and other values. Drop the MXP, colour codes and web-macro engine. Keep the prose.

**Adult and violent content.** Decide up front. Tag the sexual and gory socials (`adult`, `gore`) and let the game filter them by setting, or leave them out. The ELIZA-style sexual and profanity lines are not worth porting.

**What to drop for one player.** Multiplayer-only pieces: PvP level scaling, group-kill credit, the OUTSIDER and SELFOK distinctions, TALK and MUDCHAT triggers aimed at other players, and RP experience for socials between humans. Keep NPC-to-NPC talk for ambient chatter.

## Coverage

- **`resources/socials.txt`**: all 843 lines processed by script for field structure, code counts, target variants, sound, zapper and flags, and the distinct base names. About 60 lines read as text: the opening lines, and sample blocks for each variant and code. Adult and violent entries were identified by name and checked against the text only as needed.
- **`resources/chat.dat`**: lines 1-100 (legend) read in full. Group headers listed. Default-group sections read at lines 98-102, 2194-2202, 2686-2735, 2847-2880 and 2914-2952. Trigger and response lines counted by script over the whole file, and about 1 in 9 default-group trigger lines sampled. Named groups read in full where short.
- **`resources/factions/alignment.ini`**, **`inclination.ini`**, **`triumph.ini`**: all non-comment lines read (grep). Documentation block in `alignment.ini` (lines 108-232) read in full. Other comment blocks skimmed. Trigger names checked against socials, abilities and domains by script.
- **`resources/text/`**: read in full: alignment, inclination, classes, races, stats, themes, rules, intro, newacct, newchar, newchardone, doneacct, email, offline, down, nologins, blocked, connblocked, selchar, mxp (lines 1-61, element names listed). `topplayers.txt` and `topaccounts.txt`: structure only, with names masked; no names reproduced. `topplayers-prev.txt` and `topaccounts-prev.txt`: skipped (same format as the current files, not opened). `credits.txt`: excluded, as assigned.
- **Java, read in full or in large part**: `Behaviors/MudChat.java` (all 1755 lines, read in sections), `Common/DefaultSocial.java` (lines 277-800 and 1036-1062), `Libraries/Socials.java` (lines 1-330, 746-1056, 1210-1268), `Common/interfaces/Social.java` (296-422), `Commands/Emote.java` (60-100), `Commands/SocialsCmd.java`, `Abilities/Diseases/Disease_Smiles.java` (36-170), `Libraries/Factions.java` (94-101, 158-300, 739-912), `Common/DefaultFaction.java` (507-680, 1085-1111, 1166-1281, 1281-1500, 1500-1758, 1785-2009, 2081-2107, 2366-2600, 2614-2713, 2800-2840, 2747-2756, 3445-3485 partly).
- **Java, skimmed**: `Libraries/Socials.java` 330-745 (admin editor) and 1056-1209 (help and list lookup); `Common/DefaultFaction.java` 1-500 (getters), 680-1085 (tags and reactions), 2107-2366 (event helpers), 2840-3557 (editor and persistence); `Libraries/Factions.java` 300-739 and 912-2109 (admin editor and rebuild); `Behaviors/QuestChat.java` (not read beyond its class line).
- **Java, targeted reads**: `Libraries/EnglishParser.java` (815-875, 1000-1080), `MOBS/StdMOB.java` (2290-2330, 3577-3600, 5776-5831), `Abilities/Prayers/Prayer.java` (372-432), `Abilities/StdAbility.java` (2860-2890), `Abilities/Misc/InstanceArea.java` (1896-1918), `Abilities/Misc/Mood.java` (430-445), `Abilities/Common/AnimalHusbandry.java` (125-145), `Abilities/Properties/Prop_Socials.java` (55-112), `Abilities/Properties/Prop_UseEmoter.java` (480-492), `Libraries/CMProtocols.java` (4645-4690, LLM binding), `Libraries/CharCreation.java` (the screen-text call sites), `application/MUD.java` (329-380, 415, 1655-1675), `application/OffLine.java` (296-304), `Commands/Top.java` (196-292), `Commands/MXP.java` (86-100), `Commands/Rules.java`, `WebMacros/MudInfo.java`, `core/CMProps.java` (1260-1305, 3100-3105), `Common/interfaces/CMMsg.java` (944-1501 constants), `Libraries/Dice.java` (`rollPercentage`), `Libraries/CoffeeMaker.java` (name only), `resources/lists.ini` (2003-2050, counted by script), `resources/skills/areainstancetypes.txt` (header and lines 66-74), `coffeemud.ini` (the keys cited).
- **Not read**: the message-perception pipeline that applies the social codes, the MOBPROG engine behind `$%...%`, the word-matching helper `removePunctuationStrict`, the LLM session classes, and the admin editors. Each is cited only where it is called.
