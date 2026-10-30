# Files, logging, directions and encoders

> Part of the CoffeeMud NEWOVERVIEW. Bucket: `b008` (Files, logging, directions and encoders) · 18 files · 9933 lines of Java.

## Summary

This bucket is the plumbing under the game's I/O. `Log` provides named logging channels, one instance per host, a one-line configuration grammar and size-based rotation. `CMFile` replaces `java.io.File` with an object that resolves each path to either local disk or a database-backed virtual file tree. `Directions` maps words to one of 7 or 11 direction codes, holds the direction vocabulary and phrasing, and does the grid geometry. The rest is a built-in SMTP server and mailer that turns e-mail into in-game "journals" and mailboxes, a vendored Base64 utility, and an in-process pipe that lets a web request drive a normal session without TCP. For a single-player SUD the reusable parts are the direction model and switchable log channels. The thread-group registries, the SMTP stack and the shared VFS exist for multiple hosts and remote users.

## Key files and types

Citations are relative to the CoffeeMud folder. The prefix `com/planet_ink/coffee_mud/` is written in full in this table and dropped elsewhere.

| File | Role | Lines |
|---|---|---|
| `com/planet_ink/coffee_mud/core/CMFile.java` | `CMFile extends File`; local or VFS file object; `CMVFSDir`/`CMVFSFile` tree; security bits | 2306 |
| `core/Log.java` | `Log extends java.util.logging.Logger`; `Log.Type` channels; config, writers, rotation | 2045 |
| `core/Directions.java` | Direction codes, 7/11 mode, name charts, parsing, geometry, phrase tables | 1659 |
| `core/smtp/ProcessSMTPrequest.java` | One SMTP session: command queue, MIME parsing, delivery to journals and mailboxes | 1115 |
| `core/B64Encoder.java` | Vendored Base64 with gzip and stream wrappers; Java-object helpers | 982 |
| `core/smtp/SMTPserver.java` | Listener thread; journal-list parser; `Tickable` that fans out journal posts | 751 |
| `core/CoffeeIOPipe.java` | Ring-buffer pipe pair and `Socket` stand-in (`CoffeePipeSocket`) | 403 |
| `core/smtp/MassMailer.java` | Outbound queue, run on its own thread after each e-mail tick | 310 |
| `core/exceptions/*.java` (10 files) | Checked exception hierarchy rooted at `CoffeeMudException` | 362 in total |

## How it works

### 1. Directions: codes, vocabulary and parsing

- Codes: NORTH 0, SOUTH 1, EAST 2, WEST 3, UP 4, DOWN 5, GATE 6, NORTHEAST 7, NORTHWEST 8, SOUTHEAST 9, SOUTHWEST 10 (`core/Directions.java:80-106`). GATE is a non-compass exit; the words HERE, THERE, NOWHERE and VORTEX map to it (`:195-198`).
- The mode is per host: `DIRECTIONS=7` or `11` (`coffeemud.ini:1059`) is applied by `reInitialize` from `core/CMProps.java:3241`. About 400 lines in the tree branch on `NUM_DIRECTIONS()`.
- Three naming flavours (compass, ship, caravan) each have a word chart (`Directions.java:177-243`) and from, of and to phrase arrays (`:131-137`), giving "the north", "north of" and "to the northeast".
- Parsing (`:680-692`) is a case-insensitive prefix match over an ordered chart, so chart order settles ambiguity ("N" is NORTH, not NORTHEAST). In 7-direction mode the `NUM_DIRECTIONS` guard skips the diagonals. `getDirectionCode` (`:604-617`) adds a fallback: any word beginning with a direction letter.
- Geometry: the origin is the north-west corner (`:1580-1582`). `adjustXYByDirections` (`:1001-1038`) steps coordinates. `getRelativeDirection` (`:1614-1657`) uses `atan2` to choose a sector. `getGradualDirectionCode` (`:1365-1562`) is a switch table that turns one step toward a target. `getGradualCourse` (`:1288-1356`) plans a path; see Problems.

### 2. Log: channels, configuration and rotation

- Eight channels (`core/Log.java:94-103`), each with a six-character label for output: error, help, debug, info, warning, kills, combat, access. Config keys are `ERRMSGS`, `HLPMSGS`, `DBGMSGS`, `SYSMSGS`, `WRNMSGS`, `KILLMSGS`, `CBTMSGS` and `ACCMSGS`.
- Each host has one `Log`, stored under the first character of its thread-group name (`:165-180`). Static calls such as `Log.errOut(module, msg)` reach the caller's instance through `l()`.
- A config value has the form `[N]TARGET[<cap]`. TARGET is ON (screen), OFF, FILE (the shared `mud.log`), BOTH, or OWNFILE (one file per channel, named `mud_<channel>.log`). `N` is the number of rotated files kept. `<cap` sets a line or byte limit, and applies only to OWNFILE (`makeConfig`, `:448-520`). The value lives in the System property `LOG.<NAME>_<TYPE>` (`configureLog`, `:559-563`) and is read lazily (`getConfig`, `:529-538`).
- Each channel has ten writer slots indexed by priority; slots above the configured maximum are null (`getWriter`, `:383-437`). Unprioritised calls use slot 0.
- Rotation counts lines and bytes per writer (`writeBytes`, `:352-373`) and calls `rollLog` (`:312-342`). The writer sits in a one-element array (`fileOutWriter[0]` at `:320`, `config.writers[0][0]` at `:335`), so rollover updates every cached reference without reconfiguring.
- Line format: `yyyyMMdd.HHmm.ss`, the channel label, a 14-character module field, then the message (`makeLogEntry`, `:806-817`).
- Because `Log` is also a `java.util.logging.Logger`, third-party logging is mapped to channels by level (`TYPE_LEVEL_MAP`, `:131-156`; overrides `:1783-2044`).

### 3. CMFile: one file API over disk and database

- `CMFile` extends `java.io.File` (`core/CMFile.java:42`). A leading `//` forces local disk and `::` forces the virtual tree (`:129-130`). `vfsifyFilename` strips both (`:1859-1881`).
- The VFS is an in-memory tree of `CMVFSDir` and `CMVFSFile` (`:283-648`). Entries are sorted by upper-cased name and binary-searched (`:413-420`). Contents are stored in the database, Base64-encoded (`core/database/VFSLoader.java:126-155`).
- The tree is per host and loaded lazily. The main host reads it from the database; other hosts share it unless `DBVFS` is set privately (`:1767-1802`). Catalog and map data are grafted in as virtual directories (`:1806-1823`).
- The constructor decides read and write access separately for local and VFS, using `CMSecurity` (`:217-251`; mask definitions at `:49-65`). `canRead` and `canWrite` read those bits (`:703-734`).
- Local lookups ignore case. The constructor walks the directory listing and corrects each path segment's case (`:149-190`).
- Writes go to the VFS for VFS-only files and to disk otherwise (`saveText`, `:1465-1507`). Concurrent local opens are capped at 256 (`:1056`, `:1066`).

### 4. E-mail: SMTP in, journals and mailboxes out

- `SMTPserver` is a thread that accepts on `PORT` (`core/smtp/SMTPserver.java:461-479`). It answers `421` when the host is not running, is suspended, or the address is blocked (`:468-471`, `:392-396`). Sessions run in a bounded pool sized by `MAXTHREADS` (`:109`; `web/email.ini:22` sets 10).
- `ProcessSMTPrequest` is one session. It queues commands split at CRLF (`:242-247`) and batches replies until the queue drains (`:1012-1030`). `MAIL FROM` must name a local account (`:845-860`). `RCPT TO` accepts a mailbox or a journal. For a journal, the sender must pass the journal's mask (`:966-982`). Otherwise it refuses with 550 or 553. `DATA` prefers the HTML part over the text part and rejects other MIME types with 552 (`:309-466`).
- A journal is a named address such as `Announcements@domain`, configured as `Name (flags and criteria)` in `web/email.ini:87`. Flags are `forward`, `keepall` and `subscribeonly` (`SMTPserver.java:238-322`). A post to a forward journal is stored (`ProcessSMTPrequest.java:566-568`). A subject or body reading "subscribe" or "unsubscribe" edits the mailing list (`SMTPserver.java:583-597`).
- `SMTPserver.tick` (`:545-696`) fans journal posts out to subscriber mail and queues mailbox mail. `MassMailer` sends the queue on its own thread (`:684`; `core/smtp/MassMailer.java:136-308`). `rightTimeToSendEmail` (`:117-133`) allows a send within 30 minutes of the post time, and then within 30 minutes of the same clock time on each later day. Ignore lists and the AUTOFORWARD and NOAUTOFORWARD flags suppress forwarding (`:181-224`).

### 5. Encoders and the in-process pipe

- `B64Encoder` is a vendored Base64 implementation with optional gzip and stream wrappers (`core/B64Encoder.java:56-180`, `:737-973`). It is used for VFS storage (`VFSLoader.java:149-155`), SMTP AUTH credentials (`Libraries/SMTPclient.java:655-683`) and session prompt strings (`Common/DefaultSession.java:4262`). `B64decodeToObject` (`:545`) deserializes a Java object, and nothing in the tree calls it.
- `CoffeeIOPipe` is a ring buffer of ints for each direction. When it is full, the oldest bytes are overwritten (`:176-191`). `CoffeeIOPipes` cross-wires two pipes by swapping their inner-class stream objects (`:358-360`). `CoffeePipeSocket` extends `Socket`, so the web layer can attach a normal session (`WebMacros/WebSock.java:133-137`).

## Content worth mining

Direction vocabulary (`core/Directions.java:117-137`):

| Code | Compass (11-direction) | Ship | Caravan |
|---|---|---|---|
| NORTH / SOUTH | North / South | Foreward / Aft | Foreward / Back |
| EAST / WEST | East / West | Starboard / Portside | Right / Left |
| NE, NW, SE, SW | Northeast, Northwest, Southeast, Southwest | Fore Starboard, Fore Portside, Aft Starboard, Aft Portside | Fore Right, Fore Left, Back Right, Back Left |
| UP / DOWN | Up / Down | Above / Below | Up / Down |

The phrase tables give natural movement text: "arrived from the north", "to the northeast", "above you", and "out of nowhere" for GATE (`:131-137`).

Journal flags are a useful pattern. `web/email.ini:87` ships "Announcements (forward keepall subscribeonly)". Under `subscribeonly`, ordinary senders may only send subscribe or unsubscribe mail (`ProcessSMTPrequest.java:520-527`).

Channel defaults (`coffeemud.ini:1936-1944`): info, error, warning and debug are BOTH, help is OWNFILE, and kills, combat and access are OFF.

## Notable design ideas

- Named log channels, each switched on or off by a one-line string and changeable at runtime through a System property.
- The writer-cell indirection, which lets rotation update every cached writer in place.
- One file API over disk and database, with virtual directories grafted in from other subsystems so world data can be listed like files.
- Journals as addressable bulletin boards that are also mail endpoints, with per-journal flags.
- Retry scheduling based on the post's time of day.
- Inner-class streams swapped to cross-wire two pipes without copying data.

## Problems and cruft

Items marked [run] were confirmed by compiling the real code in a harness. The rest come from reading.

- [run] `getGradualCourse` overshoots. The diagonal count is `|dx-dy|` rather than `min` (`Directions.java:1310-1314`), and the straight-step loop appears twice (`:1316-1328`, `:1339-1351`). From (0,0) to (5,0) it returns ten EAST steps. From (0,0) to (5,2) it ends at (7,3). Its only caller is commented out (`Items/Basic/StdNavigableBoardable.java:1205-1226`), so it is dead today.
- [run] The prefix fallback misreads ordinary words: "door" gives DOWN, "sword" gives SOUTH, "enter" gives EAST (`Directions.java:609-615`). `Commands/Transfer.java:580-581` tries it before a fuzzy room search.
- [run] `makeConfig` throws `StringIndexOutOfBoundsException` for values ending in a digit, such as `ON2` or `OWNFILE3` (`Log.java:497-503`). `ON<3` sets a line cap, not a level (`:494`), so no configuration can set a maximum level. Only slot 0 ever prints.
- [run] `warnOut(module, Throwable)` logs to the error channel, so it is dropped when error is OFF. The other two `warnOut` overloads with exceptions use the same `Type.error` call (`Log.java:1093`, `:1129`, `:1381`; the last two are from reading).
- [read] The kills key is `KILMSGS` in the ini (`coffeemud.ini:1943`) but `KILLMSGS` in code (`Log.java:101`). The setting is therefore ignored (read at `application/MUD.java:2148`). The ini comment also says `mud_help.txt` (`coffeemud.ini:1930`), but the file is named `.log` (`Log.java:296`).
- [read] `makeLogEntry` uses one static `SimpleDateFormat` (`Log.java:41`, `:808`), which is not thread-safe across hosts. `numLines` and `getLogReader` open the log relative to the working directory, not `logPath` (`:692`, `:727`).
- [run] `CMVFSDir.add` on an existing name keeps the old author and time, because `get(CMVFSFile)` returns its argument (`CMFile.java:607-616`). The save paths hide this by deleting first (`:1361`, `:1477`).
- [read] `CMFile`: `renameTo` returns false after a successful VFS move (`:2147-2173`). `equals` ignores case but `hashCode` does not (`:1995-2006`, `:2057-2060`). `compareTo` inverts its sign (`:1962`). The directory branch in `CMVFSDir.delete` can never run (`:567`). `textUnformatted` tests `c<0` on a `char` (`:1181-1182`).
- [read] SMTP: the journal mask is built from the journal's name, not its parameter (`SMTPserver.java:273`), so documented masks such as `-RACE +Elf` (`web/email.ini:86`) are lost.
- [read] The last-run timestamp is read as `LASTALLPROCESING` (`SMTPserver.java:558-559`) but written as `LASTALLPROCESSING` (`:658`). Every tick therefore falls back to ten minutes ago (`:560-561`).
- [read] `SMTPserver.tick` sleeps one second and calls `System.gc()` on every call (`:686-693`).
- [read] `MassMailer` never closes its SMTP clients (`MassMailer.java:250-301`). Only `SMTPclient.close()` closes them (`Libraries/SMTPclient.java:587`).
- [read] End of stream reads as 0xFFFF, the same value used for a read timeout. A `(char)` cast of -1 gives 0xFFFF, confirmed in isolation. The `c<0` branch (`ProcessSMTPrequest.java:215`) therefore never fires. A closed client is dropped only after the 10-second idle timeout (`:45`), and the read loop polls in the meantime.
- [read] A message with no `Subject:` header gets `250` and is then dropped, because `subject` stays null (`:297`, `:496`).
- [read] Unknown SMTP commands get no reply: `replyData=lastReplyData` (`:1010`), which is always null (`:181`). `MAIL` and `RCPT` call `substring(5)` on any parameter (`:827-828`, `:928-929`), which throws for parameters shorter than five characters.
- [read] `CMInputStream.peek()` consumes the byte (`CoffeeIOPipe.java:84-97`). Nothing in the tree calls it.
- [read] Deserializing a Base64 string (`B64Encoder.java:545`) is a hazard if it is ever used on player input. Nothing calls it now.

## Takeaways for a Python SUD

- **Directions.** Keep one table of eight compass vectors `(dx, dy)` plus up, down and an in/out or gate exit. Parse with an exact-match dictionary and unambiguous abbreviations, and reject ambiguous prefixes. Do not port the letter-prefix fallback. Use `math.atan2` with 45-degree sectors for "to the north-east" text. Keep the phrase tables as data. Skip ship and caravan naming unless the SUD has vehicles; if it does, make the names a data table keyed by vehicle type.
- **Logging.** Use the standard `logging` module with one logger per channel (for example `sud.combat` and `sud.errors`) and a rotating file handler. Keep the on/off switch per channel. The config grammar, priority slots and per-host registry are not worth porting.
- **Saves and files.** Use `pathlib` with JSON or SQLite. Write saves atomically: write a temporary file, then `os.replace` it. Leave out the case-insensitive walk and the local/VFS split, since a single-player game has one storage backend.
- **Journals as in-world mail.** A `Board` object with posts, subscribers, a keep-days limit and a subscribe-only flag, plus NPC mailboxes. Delivery becomes an in-game-time event. The same-hour retry idea maps to "delivered at the same hour tomorrow".
- **Encodings.** Use JSON for save data. Never deserialize pickles from untrusted sources.
- **Drop.** The pipe classes (use direct calls or `queue.Queue`; silently overwriting old text is wrong for a game), the SMTP stack, per-thread-group singletons, the 256-file cap, `System.gc()` and the per-host VFS sharing. One module-level config object is enough for one player.

## Coverage

- Read in full: `Directions.java`, `Log.java`, `CMFile.java`, `SMTPserver.java`, `ProcessSMTPrequest.java`, `MassMailer.java`, `CoffeeIOPipe.java`.
- Skimmed: `B64Encoder.java` (header, public entry points and call sites; streaming internals not read).
- Exceptions: all ten read; usage counted by grep. `StatOutOfRangeException` has no references outside its own file.
- Several behaviours were confirmed by compiling the real source, with minimal stubs, in a throwaway harness outside the repository. These are the [run] items above. Everything else comes from reading and is unverified at runtime.
- Usage counts come from grep: about 2,750 log calls across about 400 files; about 266 `new CMFile(` sites in 118 files; about 400 lines referring to `NUM_DIRECTIONS`.
