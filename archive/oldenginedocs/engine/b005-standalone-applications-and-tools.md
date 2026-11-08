# Standalone applications and tools

> Part of the CoffeeMud NEWOVERVIEW. Bucket: `b005` (Standalone applications and tools) · 8 files · 8139 lines of Java.

## Summary

These classes are separate programs that reuse CoffeeMud's libraries. They are not the game loop. The largest, MUDProxy, is a non-blocking TCP proxy that owns each player's socket, so a backend game server can restart, or a player can be handed to another server, without the connection dropping. VFShell and UpgradeTool are operator tools: VFShell runs the in-game command set over the virtual file system, and UpgradeTool downloads a release, backs up the install and three-way merges the new files into the user's copy. The other five are small: a stand-alone "MUD is down" page, a scripted test client, an Intermud router console, a translation-string extractor and a reference-doc generator. For a single-player SUD the reusable ideas are the scenario runner, the privileged admin console, the three-way merge of shipped data and marker-based string extraction. The network machinery is multiplayer-only.

## Key files and types

Paths are relative to the CoffeeMud folder. Java files sit under `com/planet_ink/coffee_mud/`, shortened here to `application/...` and `Libraries/...`.

| File | Role | Lines |
|---|---|---|
| `application/MUDProxy.java` | NIO proxy between players and backends: socket pairing, telnet and MCCP filter, distress and reconnect, HMAC-signed control packets, load balancing | 2521 |
| `application/UpgradeTool.java` | Release upgrader: version check, backup zip, download, three-way merge of code and data, compile | 1981 |
| `application/VFShell.java` | Command shell over the virtual file system and the in-game `SHELL` command; offline (boots DB and libraries) or online over a socket | 1309 |
| `application/AutoPlayTester.java` | Scripted telnet client with an embedded Rhino JavaScript API (`waitFor`, `writeLine`) | 629 |
| `application/OffLine.java` | Stand-alone server that answers connections with a "down" text and throttles abusive addresses | 618 |
| `application/I3Router.java` | Command-line front end and interactive console for the Intermud-3 router (`Libraries/intermud/i3/router/`) | 475 |
| `application/LocalizationHelper.java` | Text scanner that collects `L("...")` and `I("...")` strings from all sources for translators | 471 |
| `application/RebuildRefDocs.java` | Boots the MUD, runs the `RebuildReferenceDocs` web macro as a sysop, then shuts down | 135 |

## How it works

### Connection proxy (MUDProxy)

**Pairing and I/O.** Each accepted player socket is registered with a selector and paired with a new socket to a backend (`handleAccept`, `application/MUDProxy.java:1707-1783`; `channelPairs` at 1771-1781). Reads are filtered on service-engine threads (`ReadProcessor`, 307-373). Buffers come from a pool of 64 KB buffers capped at 1024 (53-54, 299).

**Backend choice.** `ROUNDROBIN` is the default; `LEASTCONN` and `RANDOM` are selected with `STRATEGY=` (58, 107-112, 1738-1767). Each proxy port maps to one or more backends named by the `PROXY` and `PORT` ini keys (628-675).

**Telnet and MCCP filter.** `filter()` (1443-1668) is a byte state machine for telnet IAC sequences, ANSI CSI sequences (1559-1571) and compression. MCCP (options 85 and 86, `Common/interfaces/Session.java:1069-1071`) is handled on both links. The proxy inflates the backend's stream (`ZInputStream`) and deflates its own output to the player (`ZOutputStream`, 1626-1645). On reconnect only the backend input side is reset (1826-1832), so the player's compressed stream stays with the proxy.

**Distress and reconnect.** If a backend link drops while the session holds data, or the link is more than 15 s old and has been up more than 7 s, the proxy keeps the player connected and sends "Connection to server lost. Please stand by" (`putKeyInDistress`, 2062-2096; the test is in `closeKey`, 2132-2137). A distress thread (2262-2356) polls every 2 s while pingers exist and every 10 s otherwise (2274). It opens a probe to the backend, which answers the MPCP handshake (1502-1508). When the probe succeeds, the player is reconnected (2323-2325). The proxy then sends `ClientInfo` with `reconnect: true` and a `SessionInfo` packet holding the stored session map (1837-1850). The backend is expected to restore the character from that packet; its handler is outside this bucket. The bulky `mob_xml` field is dropped from the proxy's copy (1848).

**Control packets (MPCP).** Control traffic travels inside the player's telnet stream as a private subnegotiation, option 202 (`Common/interfaces/Session.java:1091`). A packet is `IAC SB 202`, a 20-byte HMAC-SHA1 of the payload keyed with `MPCPKEY` (IAC bytes doubled, 852-867), the plain-text payload `COMMAND {json}`, then `IAC SE` (868-870). The MAC authenticates but does not encrypt. Packets from client sockets are accepted only from loopback (1089-1093). Each packet carries a timestamp that must be within 5 s from clients or 1 s from backends (1124-1126). The commands (enum at 117-205) are:

- SESSIONINFO stores session state on the proxy (1135-1138).
- DISCONNECT closes a player's connection (1139-1154).
- LISTSESSIONS, DEBUGON, DEBUGOFF and MESSAGE (a broadcast to all players) need the control password (1128-1129, 1155-1226, 1239).
- FORWARD queues an opaque payload for another backend, delivered on a dedicated channel if needed (1333-1362, 2429-2510).
- BLOCKIP sets a timed or permanent address block (1363-1383).
- TRANSFER moves a player and their saved session to another backend (1277-1332).

### Stand-alone "MUD is down" server (OffLine)

It runs as its own process. No other Java file in `com/` refers to the class, so the MUD's listener switches (`application/MUD.java:706` and `:2236`, which act on `CMLib.hosts()`) never reach it. It listens on each port in the `PORT` key (`application/OffLine.java:537-552`). Once its listener is up, `initHost` sets `acceptConnections` (145-146). Each connection then gets `resources/text/down.txt` and is closed after 250 ms (299-321). Throttling: after six connections from one address within 180 s, the next is refused and the address is autoblocked until none of its entries remain in a five-minute memory (245-275, 282-291). With the flag clear, the server sends `resources/text/offline.txt` and drops the connection (333-344).

### Scripted test client (AutoPlayTester)

It negotiates GMCP and announces itself as a client (`application/AutoPlayTester.java:146-162`), then runs a JavaScript file through Rhino (384-403). The API is expect and send. `waitFor(regex)` polls every 100 ms for up to 60 s (275-311). It matches each new line, then the whole text received since the call (294-297). `writeLine` pauses 500 ms before every send (320-326). The default script, `resources/autoplayer/autoplay.js` (line 47), is not present in this checkout.

### Admin shell (VFShell)

Two modes. With `RUNCM1SERVER` set and a user name given, the shell logs in to a running server on a CM1-style socket (port 27733, 124-127). It sends `LOGIN` and `SHELL <command>`, then reads a `[BLOCK <terminator>]` framed reply with a 30 s limit (143-174). Otherwise `spawnShellMUD` (1030) boots the database connector (1041-1062), registers 49 library implementations (1103-1265) and runs a read-execute loop (1279-1294). Each line runs as the in-game `SHELL` command for a fake level-101 sysop mob (971-981, 1287). The fake session is about 725 lines of mostly no-op overrides (239-965). Its one real job is console I/O (669-680).

### Upgrade tool (UpgradeTool)

`main` (`application/UpgradeTool.java:82-364`) runs these steps:

1. Read the installed version from `HOST_VERSION` in the source's `MUD.java` (166, 487-537). The constant reads `5.11.0.4` in this checkout (`application/MUD.java:86`).
2. Fetch the release list and choose the target (169-219).
3. Zip the whole install as `CoffeeMud_Backup_<installed version>.zip`, unless that file already exists (222-227).
4. Download and unzip the base release. Unless `--merge` is given, refuse if any file differs from it (239-259).
5. Download the target release (262). Delete all `.class` files under `com` (266-275). Replace `com` and `lib` with the release, keeping only user files that exist in neither (276-281, 367-398).
6. Compile every `.java` file with `javax.tools`, with the `lib` jars on the classpath (283-316).
7. Merge root files, `guides`, `resources` and `web` (319-334).

Any exception unzips the backup over the tree (343-364).

Merge rules. Text files get a line-level LCS three-way merge: a line the user changed keeps the user's version, and conflicts are logged (1673-1784). INI and properties files merge by section and key, with user values winning and conflicts logged (1220-1597, 1493-1494). `.html` files under `guides` are always replaced (900-905). Binary files are kept (924-928). Obsolete files are deleted only if they are unchanged from base (1016-1095).

### Smaller tools

- **I3Router** (`application/I3Router.java`) parses `--name --ip --port --password --debug --daemon` (128-136) and starts the router on its own thread (148-162). The interactive console has a menu (194-203) and handlers for `add`, `del`, `boot`, `list` and `info` (207-456). The router itself is in `Libraries/intermud/i3/router/`, which this file only drives.
- **LocalizationHelper** (`application/LocalizationHelper.java`) scans every `.java` file under the source root for `L(` (translator text) or `I(` (parser input) markers (44, 404-406). It tracks nested `class`, `enum` and `interface` names by indentation (145-200), joins string literals split across lines (250-282) and prints `[session-translation:<Class>]` blocks of `replaceexact` lines (442-456).
- **RebuildRefDocs** (`application/RebuildRefDocs.java`) boots the MUD with map loads skipped (75-82), waits until it is running (83-94), makes the factory mob a sysop for the run (117-120), runs the `RebuildReferenceDocs` macro (`WebMacros/RebuildReferenceDocs.java`, not read) and shuts down (131).

## Content worth mining

This bucket holds almost no game content. The useful text is the kind a text game needs for its own tools:

| Text | Where | Reuse |
|---|---|---|
| "Connection to server lost. Please stand by" | `application/MUDProxy.java:2069-2070` | Holding message while a backend restarts |
| "-- Connection restored --" | `application/MUDProxy.java:1853-1854` | Confirmation after a silent reconnect |
| Admin verbs `add`, `del`, `boot`, `list`, `info` with `peer`, `mud`, `channel` targets | `application/I3Router.java:194-203` | Dev console verb set |
| `[session-translation:Class]` followed by `replaceexact` lines | `application/LocalizationHelper.java:442-456` | Translation file format |
| Shell usage block | `application/VFShell.java:98-103` | Help text for a debug console |

## Notable design ideas

- **A connection that outlives the game process.** The proxy owns the socket and the compressed stream. The game can restart, or hand a player to another server, while the player sees a holding message (`application/MUDProxy.java:2062-2096`, 2262-2356).
- **Session state as a snapshot.** SESSIONINFO keeps a plain map on the proxy (1135-1138), and the backend restores from it (1846-1850). Reconnect is a restore, not a replay.
- **One command language, several front ends.** VFShell's offline path and its online CM1 path both pass text to the in-game `SHELL` command (`application/VFShell.java:1287`, 143-151).
- **Expect-style scripting.** AutoPlayTester drives a live session with regex waits (`application/AutoPlayTester.java:275-311`).
- **Three-way merge for shipped content.** UpgradeTool keeps user edits while taking upstream changes. It makes a backup first and restores automatically on failure (`application/UpgradeTool.java:1673-1784`, 222-227, 343-364).

## Problems and cruft

- **UpgradeTool, security.** Downloads use plain `http://` URLs (57-59, 653-685), and the file has no checksum or signature check. The downloaded Java is compiled and loaded (283-316), so a network attacker could replace server code during an upgrade. Zip entries are written without a path check (719), so `../` names could write outside the install.
- **UpgradeTool, `--merge`.** `mergeJava` deletes `com` and copies the release (386-389). User edits to shipped `.java` files are discarded, and only the backup keeps them. The class comment says the backup holds "any files that are changed or deleted" (`application/UpgradeTool.java:49`), but the code zips the whole tree (222-227).
- **UpgradeTool, other.** An existing backup for the same version is never refreshed (222-224). Each LCS allocates an (n+1) by (m+1) int table (1808), and `threeWayMerge` builds two of them (1678-1679), so memory grows with the product of the file lengths. The message at 211 prints the installed version where the target was meant. `isTextFile` accumulates a value it never uses (813-815).
- **MUDProxy.** BLOCKIP, TRANSFER and FORWARD check only the MAC, not the control password (1277-1383). That may be intended, but the code does not say so. The control password is logged at startup (696). CMD mode takes both secrets as command-line arguments (388, 394). The MAC is compared with `Arrays.equals` (1104), and replay protection is time-only (1124-1126). The static initialiser reads `MUD.serviceEngine` (63), so the class cannot be used without initialising the MUD class.
- **VFShell.** Any argument starting with `-u` or `-p` is taken as a credential, even when quoted (82-94). A new `BufferedReader` is made for each line (673, 1285), so read-ahead on piped input may discard lines (not exercised). A leftover probe for `/test.the.database` remains (1086-1098).
- **I3Router.** `info channels` cannot find a channel by name. The `channels` keyword is not stripped before `findChannel` is called (407-409). The interactive loop dereferences `getRouter()` (191, 461), which is null if the router failed to start (`Libraries/intermud/i3/router/I3Router.java:83`), and calls `readLine()` without checking for EOF (187). Five messages lack a space before "has been" (252, 269, 282, 308, 330). The port check rejects only negative values (136), and the usage text's password range is not checked.
- **OffLine.** `closeSocks` assigns to its own parameters (172-174), which does nothing. `isOK` is set just before it is tested (524, 532). `defaultShutdown` is empty (438-440).
- **AutoPlayTester.** Read errors are swallowed (227-228), so a dropped link looks like silence until `waitFor` times out 60 s later. `waitForOptions` is a stub that returns an empty string (546-557), yet it appears in the API list. The sign branch in `rand` can never apply (528-532).
- **LocalizationHelper.** A `break` at line 210 stops the scan of the rest of a file when a `commonTelL(` call has no closing quote on its line. The source root is hard-coded and relative (407).
- **RebuildRefDocs.** It waits for `RUNNING` with no timeout (83-94) and gives every character sysop level for the run (117).

## Takeaways for a Python SUD

1. **Scenario runner (from AutoPlayTester).** Write a small send and expect runner that drives the engine in-process through its text input and output, not through a socket. Use regex expects, and count timeouts in simulation ticks so the same test works whether the game is real-time or turn-based. A scenario can be plain data:

```python
SCENARIO = [
    ("send", "look"),
    ("expect", r"You are in (?P<room>.+)\."),
    ("send", "north"),
    ("expect", r"You cannot go that way"),
]
```

2. **Admin console with a privileged actor (from VFShell and I3Router).** Run admin commands through the same dispatcher as players, with an actor that carries an admin flag. Give the engine an output interface with a `write()` method rather than a full session class. I3Router's verb set (`list`, `info`, `boot`, `del`) suits debug tools.

3. **Three-way merge of shipped data (from UpgradeTool).** Keep the content files as shipped alongside each save. On update, merge per key for JSON or TOML: keep the player's values, add new upstream keys and log conflicts. Back up before any change, and restore on failure.

4. **Marker extraction for text (from LocalizationHelper).** Wrap player-facing strings in a function such as `_()` and extract them with Python's `ast` module, not a text scanner. `ast` handles the concatenation and nesting cases that LocalizationHelper parses by hand.

5. **Drop.** MUDProxy's MPCP, MCCP and load balancing; OffLine's throttling; I3Router; and the whole-server boot in RebuildRefDocs, since a doc generator can read the content registry directly. A single-player game needs no control channel, no session transfer and no per-player MAC. The one useful piece is a session snapshot that can be saved and restored after a crash, which in SUD is the save file. Connection pairing, distress, reconnect and IP throttling all assume many clients.

## Coverage

- **Read in full:** all eight files. `MUDProxy.java` (2521 lines), `UpgradeTool.java` (1981), `VFShell.java` (1309), `AutoPlayTester.java` (629), `OffLine.java` (618), `I3Router.java` (475), `LocalizationHelper.java` (471), `RebuildRefDocs.java` (135).
- **Skimmed:** none.
- **Consulted for cited lines only:** `application/MUD.java` (86, 113, 116, 706, 1683, 2236); `Common/interfaces/Session.java` (1069-1071, 1091); `Libraries/intermud/i3/router/I3Router.java` (76-95); `resources/text/down.txt` and `offline.txt` (existence only); `resources/autoplayer/` (absent).
- **Not read:** `WebMacros/RebuildReferenceDocs.java`, `Commands/Shell.java`, the rest of the Intermud-3 router, and the backend's `SessionInfo` handler.
- **Documentation against code:** the UpgradeTool class comment describes a backup of changed or deleted files (49), but the code backs up the whole tree. The other class comments match the code.
