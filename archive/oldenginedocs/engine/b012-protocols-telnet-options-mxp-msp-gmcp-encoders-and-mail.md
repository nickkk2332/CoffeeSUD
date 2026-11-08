# Protocols: telnet options, MXP, MSP, GMCP, encoders and mail

> Part of the CoffeeMud NEWOVERVIEW. Bucket: `b012` (Protocols: telnet options, MXP, MSP, GMCP, encoders and mail) · 10 files · 7789 lines of Java.

## Summary

`CMProtocols` is the server side of CoffeeMud's in-band client protocols. It builds the MSP sound and MXP image tags, parses MCP 2.1 commands, encodes and decodes MSDP and GMCP, and reports game state to clients as JSON. The session layer (`Common/DefaultSession.java`, outside this bucket) negotiates which telnet options a client accepts, then calls into this library for each sub-option and for a periodic ping. The same class also holds a proxy-transfer protocol (MPCP) for moving a logged-in player between muds, an MSSP status block, a Twitter poster and an optional LangChain4j LLM bridge. The rest of the bucket is support code: password hashing and a weak XOR "filter" cipher (`CMEncoder`), a direct-to-MX SMTP client with an in-game journal fallback (`SMTPclient`), and an HTTP client that nothing calls. For a Python SUD the useful parts are the change-detected state push, the stable-ID and fallback-lookup patterns, and the MCP-style plug-in package interface. The telnet, proxy and mail plumbing is multiplayer or transport detail that a single-player game does not need.

## Key files and types

Citations such as `CMProtocols.java:4245` name a file in this table. Paths are relative to the CoffeeMud folder.

| File or class | Role | Lines |
|---|---|---|
| `com/planet_ink/coffee_mud/Libraries/CMProtocols.java` | Implementation of the protocol library: MSP and MXP tags, MCP parser and package dispatch, MSDP and GMCP encoders and decoders, telnet stripping, MSSP, Twitter, MPCP proxy transfer, LLM bridge | 5149 |
| `com/planet_ink/coffee_mud/Libraries/interfaces/ProtocolLibrary.java` | Interface; `InProto` enum (`:57-64`); `MCPPackage` plug-in API (`:378-406`); `LLMSession` (`:348-371`) | 407 |
| `com/planet_ink/coffee_mud/Libraries/SMTPclient.java` | Direct-to-MX SMTP client; `emailIfPossible`; `emailOrJournal` with journal fallback; address validation | 686 |
| `com/planet_ink/coffee_mud/Libraries/interfaces/SMTPLibrary.java` | Interface; port 25, CRLF, 10 s timeout constants | 147 |
| `com/planet_ink/coffee_mud/Libraries/CMEncoder.java` | Password hashing, XOR "filter" encryption, deflate helpers | 297 |
| `com/planet_ink/coffee_mud/Libraries/interfaces/TextEncoders.java` | Interface for the encoder library | 119 |
| `com/planet_ink/coffee_mud/Common/DefaultHttpClient.java` | Hand-rolled HTTP/1.1 client over sockets; plain HTTP only; no callers in the tree | 629 |
| `com/planet_ink/coffee_mud/Common/interfaces/HttpClient.java` | Builder-style HTTP client interface and `Method` enum | 220 |
| `com/planet_ink/coffee_mud/Libraries/mcppkgs/MCPNegotiatePackage.java` | MCP package `mcp-negotiate` (1.0 to 2.0) | 69 |
| `com/planet_ink/coffee_mud/Libraries/mcppkgs/MCPMooSimpleEditPackage.java` | MCP package `dns-org-mud-moo-simpleedit` (1.0) | 66 |

Major types inside `CMProtocols.java`:

- `GMCPCommand` enum, 43 names (`:930-975`).
- `MSDPVariable` enum, 36 names (`:2098-2136`); `MSDPCommand` (`:2089-2096`); `MSDPListable` (`:2079-2087`).
- `McpParseStartState` and `McpParseContState` (`:909-922`, `:1178-1187`).
- `MPCPSessionTask` (`:76-81`).
- `LLM` enum of 27 providers (`:4643-4737`).

Files outside the bucket that were read only far enough to explain the interactions: `Common/interfaces/Session.java` (telnet constants, `:1046-1164`); `Common/DefaultSession.java`; `Libraries/CoffeeFilter.java` (MXP and MSP rewriting); `application/MUDProxy.java` (partial-chunk stitching, `:1044-1075`); `Libraries/CMPlayers.java` (`mergePlayer`, `:182-196`); `Libraries/Dice.java` (`roll`, `:398-406`); `Commands/Reset.java`; `WebMacros/Authenticate.java`; `MOBS/StdMOB.java`; `Abilities/StdAbility.java`; `Libraries/CommonMsgs.java`; `Common/interfaces/CharState.java`; `coffeemud.ini`; `guides/Protocols.html`; `resources/mxp_images.ini`.

## How it works

### 1. Negotiation and transport (what this library assumes)

- At connect the session prints `#$#mcp version: 2.1 to: 2.1` (`DefaultSession.java:619`) unless MCP is disabled. It then sends WILL or DO for TERMTYPE, COMPRESS2, MXP, GMCP, MSP, MSDP, NAWS, LINEMODE and MSSP, each gated by a `CMSecurity.DisFlag` (`DefaultSession.java:619-655`). The sets of options the mud is willing to use are built in `mightSupportTelnetMode` (`DefaultSession.java:725-745`).
- Option codes (`Session.java:1046-1113`): MSDP 69, MSSP 70, COMPRESS 85, COMPRESS2 86, MSP 90, MXP 91, ATCP 200, GMCP 201, MPCP 202, SE 240, SB 250, IAC 255. `TELNETBYTES_GMCP_HEAD` is IAC SB 201 (`Session.java:1149`).
- Inbound sub-options go to `handleSubOption` (`DefaultSession.java:1812`). MSDP goes to `processMsdp` (`:1945-1953`), GMCP to `processGmcp` (`:1954-1965`) and MPCP to an HMAC-SHA1 checked handler (`:1966-1990`).
- The sub-option reader (`DefaultSession.java:2189-2213`) collects bytes until IAC SE or until a time window closes: 100 ms for ordinary options, 5 s for MPCP. It drops the session above 5 MB (10 MB for MPCP). It does not check that IAC SE arrived, so a slow packet is processed truncated.
- Pseudo-codes: `Session.java:1099`, `:1101` and `:1105` define ANSI16, ANSI256 and ANSI as 252, 254 and 253. Those are the byte values of WONT, DONT and DO. Internal flags therefore share the telnet byte space (see Problems).
- Inline output is gated by `isInlineAllowed` (`DefaultSession.java:264-290`). It checks the client's telnet mode and, for MSDP, whether the variable was reported. For MCP it checks the negotiated package version.

### 2. Telnet stripping (for web socket clients)

`stripTelnet` (`CMProtocols.java:4266-4311`) removes IAC DO, DONT, WILL and WONT with their option byte, IAC SB ... IAC SE blocks, and other IAC commands. It is called only by the web socket macros (`WebMacros/WebSockJSON.java:103`, `WebMacros/WebSockASCII.java:53-56`). Companion helpers are `stripLF` (`:4314-4337`), `stripNonASCII` (`:4340-4363`) and `stripANSI` (`:4366-4392`).

`stripANSI` looks for ESC `[` and skips forward to the next `m` or `z` (`:4385`). Those are the only terminators it knows. Any other CSI sequence, such as a cursor move ending in `H` or an erase ending in `J`, is consumed up to the next `m` or `z` anywhere in the buffer. If there is no such letter, the final write has a negative length (`:4390`), which throws `IndexOutOfBoundsException` under the JDK bounds check. This was found by reading and not run.

### 3. MSP: sound tags

- `msp(String, int, int)` (`CMProtocols.java:899-907`) returns a token of the form ` !!SOUND(<file> V=<volume> P=<priority>[ U=<MSPPATH>]) `. It returns an empty string if MSP is disabled (`:901`). It does not check whether the recipient's client supports MSP.
- `msp(String, int)` (`:1482-1486`) fixes volume at 50 and sets priority to `1d50 + argument`, because `roll(1,50,p)` is `1..50 + p` (`Dice.java:398-406`).
- The token is rewritten at output time by `CoffeeFilter`. If the session has MSP, the token passes through. If MXP is on, it becomes an MXP `<SOUND>` tag. Otherwise it is deleted (`Libraries/CoffeeFilter.java:1225-1262`). The mob also needs the SOUND attribute (`:1228-1231`).
- Callers: 118 files call `CMLib.protocol().msp(`, using 46 distinct literal sound names (see Content).

### 4. MXP: image tags and default image lookup

- `mxpImagePath` (`CMProtocols.java:1412-1437`) splits a key on the last `=` and `/`, then prefixes it with `MXPIMAGEPATH` (`coffeemud.ini:1747`, a localhost default). It returns an empty pair if MXP is disabled.
- `mxpImage` (`:1439-1467`) builds `^<IMAGE 'file' URL="..." parms^>^N` (`:1451`, `:1466`). The `^<` and `^>` markers are internal escapes. `CoffeeFilter` turns them into real tags for MXP clients and deletes them for everyone else (`CoffeeFilter.java:736-760`).
- `getDefaultMXPImage` (`:1502-1895`) chooses an image for any game object by walking a precedence chain. For a room it tries `ROOM_<ID>`, then a locale key, then `ROOM_*` and `LOCALE_*` (`:1560-1569`). For an exit it tries `EXIT_<ID>`, then the door name, then `EXIT_WITHDOOR` or `EXIT_OPEN`, then `EXIT_*` (`:1574-1583`). The chain ends at the global `*` key (`:1890-1891`). The chain for each class is in the Catalogue section below.
- The key table is `resources/mxp_images.ini`. It is parsed once and cached as `PARSED: mxp_images.ini` (`:1509-1531`). The value `NULL` is an explicit opt-out: `getHashedMXPImage` returns the empty string for it (`:1495-1499`). A missing or blank key returns null, which means "try the next key" (`:1492-1494`).
- A MOB's race is found by scanning every race class on each call (`:1544-1556`).
- Callers: 13 files, including `MOBS/StdMOB.java`, `Locales/StdRoom.java`, `Items/Basic/StdItem.java`, `Exits/StdExit.java` and `Commands/Score.java`.
- Doc check: the MSP token format matches the example in `guides/Protocols.html` (MSP section). The MXP section of that guide was only skimmed.

### 5. MCP 2.1: parser and package dispatch

- Entry is `CMProtocols.mcp` (`:1300-1410`), called from `DefaultSession.sessionMCPCheck` (`DefaultSession.java:2687-2697`) for every input line that starts with `#$#`. If MCP consumes the line, it is not treated as a command.
- The parser is a hand-written state machine. `parseMcpStart` (`:1012-1176`) reads `#$#<command> <key> key: value` pairs. Multi-line values use `#$#* <tag> key: line` continuation lines and close with `#$#: <tag>` (`parseMcpCont` `:1189-1282`, `parseMcpEnd` `:1284-1296`). The tag is the `_data-tag` value from the start line. Every malformed case logs an error and returns false.
- Negotiation: a `#$#mcp` line must carry an `authentication-key` (`:1372-1375`). The mud answers `#$#mcp-negotiate-can <key> package: <name> min-version: <x> max-version: <y>` for each loaded package and then `#$#mcp-negotiate-end <key>` (`:1381-1386`). A later line with a different key is rejected (`:1364-1368`).
- Packages are loaded reflectively from `Libraries/mcppkgs/`, using the `MCPPACKAGES` property (`CMProtocols.java:1313-1315`, `coffeemud.ini:2271`, default `%DEFAULT%`). Dispatch matches the full package name, or a name followed by `-` (`:1390-1400`). After dispatch the key/value map is cleared (`:1407`).
- The plug-in contract is `MCPPackage` (`ProtocolLibrary.java:378-406`): `packageName`, `minVersion`, `maxVersion` and `executePackage(session, command, clientSupported, variables)`.
- `MCPMooSimpleEditPackage` (`MCPMooSimpleEditPackage.java:49-65`) handles `dns-org-mud-moo-simpleedit-set`. It splits the `content` variable into lines and rejoins them with the literal text `%0D`, then calls `session.setFakeInput` (`DefaultSession.java:693-699`). The `%0D` token matches the one that `Commands/JConsole.java:151` decodes as a line break. That match was inferred from the code and not traced further.

### 6. MSDP

- Wire tokens (`Session.java:1534-1544`): VAR 1, VAL 2, TABLE_OPEN 3, TABLE_CLOSE 4, ARRAY_OPEN 5, ARRAY_CLOSE 6. Character set US-ASCII (`Session.java:1146`). A packet is IAC SB 69 ... IAC SE.
- Parser `buildMsdpMap` (`CMProtocols.java:2003-2077`) is a stack machine that builds nested maps and lists. Keys are upper-cased by `msdpStringify` (`:1897-1922`). List items are skipped when the same string is already present (`:2068`).
- Commands (`MSDPCommand`, `:2089-2096`): LIST, SEND, REPORT, RESET, UNREPORT. `processMsdpResult` (`:2690-2813`) handles them. REPORT stores the variable in the session's `reportables` map with its current comparable value and sends the value at once (`:2703-2706`). UNREPORT removes it (`:2765-2787`). RESET calls `resetMsdpConfigurable`, which is a TODO and does nothing (`:2618-2630`).
- LIST answers (`processMsdpList`, `:2574-2610`): COMMANDS, LISTS, CONFIGURABLE_VARIABLES (always empty, because `MSDPConfigurableVar` has no members, `:2138-2140`), REPORTABLE_VARIABLES and SENDABLE_VARIABLES (all 36 variables), and REPORTED_VARIABLES (the session's current reports).
- Change detection (`getMsdpComparable`, `:2142-2259`): every reportable variable has a comparable value. `pingMsdp` (`:2633-2668`) recomputes each one, compares it with `equals`, and sends only the changed variables inside one IAC SB MSDP ... IAC SE frame (`:2658-2661`). `DefaultSession.doProtocolPings` calls it once per second (`DefaultSession.java:3181-3184`, interval `MSDPPINGINTERVAL = 1000` at `:70`).
- Values (`processMsdpSend`, `:2261-2559`). `ROOM` and `LOCATION` send a nested table with `VNUM`, `NAME`, `AREA`, `TERRAIN` and `EXITS` (`:2432-2488`). `VNUM` is `Math.abs(getExtendedRoomID(R).hashCode())` (`:2450`). `OPPONENT_STRENGTH` sends the output of the CONSIDER command (`:2384-2399`).
- Two variables have no handler: `ARACHNOS_DEVEL` and `ARACHNOS_CHAT` (`:2134-2135`). Requests for them send a name with an empty value (`:2264-2271`). Only `ARACHNOS_MUDLIST` is built (`:2400-2431`).

### 7. GMCP

- Dispatch: `processGmcp` (`CMProtocols.java:3944-3957`) calls `processGmcpStr` (`:2907-3942`). The package word is lower-cased and dots become underscores, then looked up in `GMCPCommand`. Unknown packages return null (`:2941-2948`). If the argument is a bare word it is wrapped as `{"root":"..."}`, and if it is a JSON value it is wrapped as `{"root": ...}` (`:2926-2929`). Responses are framed by `buildGmcpResponse` (`:2815-2852`), which normalizes CR and LF pairs and writes IAC SB 201 ... IAC SE.
- Inbound handlers:
  - `core.hello` stores the client name and version (`:2991-3010`).
  - `core.supports.set`, `.add` and `.remove` maintain a name-to-version map (`:3037-3086`). Setting the list clears it first (`:3038`). Advertising `char.login` makes the mud answer `Char.Login.Default {"type":["password-credentials"]}` (`:3060-3065`).
  - `core.keepalive` resets idle timers (`:3087-3091`). `core.ping` returns the package word only, not the argument (`:3092-3095`).
  - `char.login` calls `session.autoLogin` with `name` and `password` from the JSON, then sends `char.statusvars` (`:2973-2990`).
  - `char.login.credentials` stores the account and password in session stats (`:3011-3036`).
  - `request <x>` rewrites the space to an underscore and dispatches again (`:2967-2972`). Single-word requests such as `request room` therefore work.
  - `ire.composer.setbuffer` feeds text into the input buffer through `setFakeInput` (`:3101-3122`).
  - `msdp` inside GMCP converts the JSON to an MSDP map, runs `processMsdpResult`, and returns JSON (`:2956-2966`).
- Outbound packages, with the lines that build them:

| Package | Built at | Notes |
|---|---|---|
| `char.vitals` | `:3123-3138` | hp, mana, moves and maxima |
| `char.status` | `:3423-3505` | level, TNL, hunger, thirst, fatigue, alignment, state code, enemy |
| `char.base`, `char.maxstats`, `char.worth`, `char.statusvars` | `:3364-3411`, `:3412-3422`, `:3506-3518`, `:3337-3363` | name, class, race, clan(s), XP, quest points, trains, practices |
| `char.items.list` (`inv` and `contents`) | `:3270-3336` | id, name and an attribute string |
| `char.skills.*` and `char.effects.*` | `:3812-3921` | group lists, group contents, and per-skill help text |
| `room.info` | `:3519-3637` | numeric and string IDs, name, zone, description, terrain, move mode, exits, coordinates |
| `room.exits` | `:3638-3719` | door, open, locked and move mode per exit |
| `room.mobiles`, `room.players` | `:3214-3269` | NPC and PC lists |
| `room.items.list` (`inv` and `contents`) | `:3139-3213` | room items and container contents |
| `group` | `:3729-3774` | leader, members, HP and level, tank |
| `comm.channel.players` | `:3777-3811` | see Problems |
| `comm.tick` | `:4134` | one fixed empty packet |
| `comm.quest` | `:3725` | a fixed "ready" reply |

- Timing (`pingGmcp`, `CMProtocols.java:4114-4263`). Each pass is tied to a tick or a fixed interval:
  - `comm.tick` every `REAL_TICK_ADJUST_FACTOR * TICKTIME` = 5 × 4000 ms, i.e. 20 s (`CharState.java:65`, `coffeemud.ini:2151`, `CMProtocols.java:4125-4136`).
  - `char.status` and `group` about once per tick, using `TICKTIME - 1` ms (`:4138-4162`).
  - Effects when the effect count changes (`:4165-4183`).
  - `char.vitals` on every ping (`:4185-4190`).
  - `char.worth`, `char.maxstats`, `char.base` and `char.statusvars` every 15,996 ms (`:4191-4219`).
- Dedupe. `possiblePingGmcp` (`:3959-3977`) builds the response string and sends it only when its `String.hashCode()` differs from the last one stored for that command in `reporteds`.
- Room and item changes come from `invokeRoomChangeGmcp` (`:3980-4112`), which keeps a per-session key for the room, the mobile set, the player set and the item set. The room key is the room's identity hash. The mobile, player and item keys are sums of identity hashes.
- Forced refreshes (`DefaultSession.doPing`, `DefaultSession.java:4060-4118`): `GMCP_PING_MED` clears the status timer, and `StdMOB.combatStarted` calls it when combat begins (`MOBS/StdMOB.java:4002`). `ROOMLOOK` resends room data from `CommonMsgs.java:2110`. `GMCP_PING_EFFECTS` runs when an ability adds or removes an effect (`Abilities/StdAbility.java:658` and `:678`).
- Object IDs. Items and mobs use `Math.abs(hashCode())` (`:3159`, `:3287`). No class under `Items`, `MOBS`, `Locales` or `Common` overrides `hashCode`, so these are identity hashes. Rooms use a hash of the string room ID (`:3544`, `:3672`), which is stable across restarts.

### 8. MPCP: proxy player transfer

- Purpose: move a logged-in character and its session between muds that sit behind a proxy (`application/MUDProxy.java`). Packets are JSON carried in IAC SB 202 and authenticated with HMAC-SHA1 (`DefaultSession.java:1966-1990`). The key is a configuration value and is not reproduced here.
- Commands handled by `processMPCPTransfer` (`CMProtocols.java:277-497`): `Transfer` (tasks `transfer`, `return` and `swap`), `Forward` (a payload relayed to another mud), `SessionInfo` (restores session stats; `:499-523`), and anything else, which goes to the proxy's `ProxyCtl` command when the sender has shutdown rights (`:354-373`).
- Two-phase offer, with a 30 s timeout (`MPCP_OFFER_TIMEOUT_MS`, `:84`). `sendMPCPOffer` (`:644-688`) records the pending offer under the player's name and schedules expiry (`:661-674`). The target validates it in `processMPCPOffer` (`:696-758`), rejecting it if a different player with the same name already exists, and replies with `sendMPCPOfferResponse` (`:764-781`). On success the source stamps the transfer data and sends the `Transfer` (`processMPCPOfferResponse`, `:788-846`).
- Swap: the connection moves to another mud without exporting the character. The timeout is 15 s (`MPCP_SWAP_TIMEOUT_MS`, `:83`). See `sendMPCPSwapResult` (`:586-622`), `processMPCPSessionSwap` (`:525-583`), `processMPCPSwapResult` (`:848-894`).
- Large payloads are cut into 32,768-character chunks with a `partial` flag (`sendMPCPChunks`, `:111-130`). The proxy stitches them back together (`MUDProxy.java:1048-1067`).
- Return home: `scheduleMPCPReturnedMerge` (`:132-152`) retries up to 36 times at 2.5 s intervals while a ghost session still holds the character.
- The offer handler parses the incoming player into a temporary MOB and destroys it with `mergePlayer(incoming, null)` (`:741`, `CMPlayers.java:182-196`). The database is not written at that point.

### 9. MSSP and the Twitter channel hook

- `getMSSPPackage` (`CMProtocols.java:4395-4521`) builds the MSSP table: player count, status, port list, name, uptime, host, web address, language, icon, charset, intermud networks, family, created year, contact, codebase, gameplay, area, help file, mobile, object, room, class and race counts, skill count, and capability flags for ANSI, 256 and true colour, MCP, MCCP, MSP and MXP. `DefaultSession.java:2291-2302` writes it as MSSP VAR and VAL tokens. The contact and email fields come from the admin email setting and are not reproduced here.
- `tweet` (`:4524-4641`) signs an OAuth 1.0a request with HMAC-SHA1 by hand and posts JSON to the Twitter v2 endpoint. It logs an error when the response code is above 200 (`:4612`). Twitter's documented success code for this endpoint is 201, so a successful post would be logged as an error. That is from the API, not verified by running. The only caller is `CMChannels.java:1121`.

### 10. Encoders and passwords (`CMEncoder`)

- Password storage: `makeFinalPasswordString` (`CMEncoder.java:111-119`) hashes the password only when the `HASHPASSWORDS` property is set. The hashed form is `|<salt>|<hash>`, both base64 of 4-byte integers (`:103-109`). The salt is a random int up to `Integer.MAX_VALUE`. The hash is Java's `String.hashCode()` of `(password + salt).toLowerCase()`, so it is 32 bits and case-insensitive.
- `checkPasswordAgainstRandomHashString` (`:134-140`) verifies a raw password against that form.
- `passwordCheck` (`:170-195`) compares plain strings with `equalsIgnoreCase` (`:180`, `:193`). When one side is hashed it uses the check above. When both sides are hashed it calls `checkHashStringPairs` (`:142-153`). That function hashes the other hash string plus a salt, so two differently salted hashes of the same password compare unequal. The callers of the both-hashed path are not verified. `passwordCheck` has six call sites, including `Common/DefaultSession.java:3313`, `Common/DefaultPlayerStats.java:321` and `Libraries/CharCreation.java:1295`.
- `makeRepeatableHashString` (`:123-127`) is a deterministic 32-bit hash XORed with the filter below.
- `generateRandomPassword` (`:156-167`) makes ten characters, alternating digits (from `roll(1,10,-1)`) and lower-case letters.
- Filter "encryption": `filterEncrypt` and `filterDecrypt` (`:261-296`) XOR the bytes with a 256-byte key, base64 and hex. The key (`getFilter`, `:197-246`) starts from a fixed ASCII seed in the source (`:202`, not reproduced). It is then XORed with the mud domain, the mud name, the admin email, and the bytes of every network interface's MAC address. The result depends on the host and config. It is obfuscation, not secrecy: the key can be rebuilt from the config and the host's MAC addresses.
- Use: the web login macro puts `filterEncrypt(login)` and `filterEncrypt(password)` into the `CMAUTH` cookie and into URL parameters (`WebMacros/Authenticate.java:82-90`, `:105-106`).
- Compression: `compressString` runs one `deflate` at BEST_COMPRESSION into a buffer sized to the character count (`CMEncoder.java:73-101`). `decompressString` runs one `inflate` into the same shared buffer (`:49-70`), so output beyond the buffer, 64 KB unless `compressString` has grown it, is silently lost. Character descriptions are stored this way (`MOBS/StdMOB.java:2030`, read at `:2041`).

### 11. Mail (`SMTPclient`)

- Recipient resolution: `emailIfPossible(fromName, toName, subj, msg)` (`SMTPclient.java:181-217`) maps the name to a stored address with `makeValidEmailAddress` (`:355-372`), which checks player records, then accounts, then all-hosts records. If `SMTPSERVERNAME` is set (`coffeemud.ini:80`), the message goes through that relay. Otherwise the client connects to each MX host for the recipient's domain, in the order the DNS server returned them (`:51-69`, `:124-179`, port 25 from `SMTPLibrary.java:35`).
- Protocol (`sendMessage`, `:395-528`): line endings are normalized to CRLF (`:403-450`). The client sends EHLO when AUTH is configured and HELO otherwise (`:452-456`). It supports AUTH PLAIN and AUTH LOGIN, with base64 credentials taken from the `SMTPHostAuth` settings (`:463-484`, `:614-685`). Then it sends MAIL FROM, RCPT TO and DATA, headers (`MIME-Version`, `Content-Type`, `Content-Transfer-Encoding: 7bit`, `Date`, `From`, `Subject`, `Sender`, `Reply-To`, `To`), the body, and a single `.` line (`:485-527`). The body is not dot-stuffed (see Problems).
- `emailOrJournal` (`:255-283`) is the common entry point. It appends a standard disclaimer and, for non-address recipients, an unsubscribe link from `CoffeeUtensils.getUnsubscribeURL` (`:266-269`). It tries the email path first. If that fails it writes the message to the in-game mailbox journal (`DBWriteJournal`, `:281`), unless the `MAXMAILBOX` count is reached (`:272-279`).
- Address check: `getEmailAddressError` (`:308-353`) is a hand-written check of the local-part characters, a 64-character local part, quoted local parts, and a 255-character domain.
- Callers (15 files): `Commands/Email.java:414` (in-game mail), `WebMacros/AccountCreate.java:192` (account e-mails), `Commands/Auction.java:311`, `WebMacros/JournalFunction.java:362` and `Commands/Reset.java:951` and `:968` (password resets).
- `getListMembers` (`:540-580`) sends EXPN. It has no callers. Its loop has no end-of-stream test (see Problems).

### 12. HTTP client (`DefaultHttpClient`)

- Builder interface (`HttpClient.java:42-220`): `doGet`, `doHead`, `doRequest`, `header`, `body`, timeouts, `maxReadBytes`, and response accessors.
- `doRequest` (`DefaultHttpClient.java:187-443`) supports plain HTTP only. An `https` URL throws `Unsupported: ssl` (`:194-196`). Keep-alive sockets are reused, and a drain step (`:234-245`) detects a closed peer. The response is read as a state machine (PREHEAD, INHEAD, INBODY, PRECHUNK, INCHUNK, POSTINCHUNK, POSTCHUNK; `:54-63`, `:283-438`) that handles Content-Length and chunked bodies.
- `getRawUrl` (`:493-548`) accepts only status 200 and 302 with a positive length. It does not follow redirects. Its default cap is 10 MB and its default timeout is 10 s (`:448`, `:454`).
- Nothing in the tree references `DefaultHttpClient` or `getRawUrl` (grep). It is reachable only through the class registry if at all (not verified).

### 13. LLM bridge (in `CMProtocols`, `:4643-5148`)

- Loading: the LangChain4j jar named by `LANGCHAIN4J_JAR_PATH` is loaded in a `URLClassLoader` (`:4756-4787`). The 27 providers are listed in the `LLM` enum (`:4645-4725`), each with its builder class names and required settings. Ollama needs `baseUrl` and `modelName`; Anthropic needs `apiKey`.
- Reflection-based configuration (`:4829-4867`): every setter on the builder whose name matches a settings key `LANGCHAIN4J_<CATEGORY>_<SETTER>` is applied with values parsed from the string. The settings are all-or-nothing. If any optional setting fails to convert, none are applied (`:4871-4888`).
- Caching: a model is cached per category. A failed load is cached as a timestamp and retried after six minutes (`:4750`). The default request timeout is 20 s (`:74`, `:4811`), and the temperature is fixed at 0.7 (`:4800`).
- Sessions (`createLLMSession`, `:5008-5148`): `MessageWindowChatMemory` with a default of 10 messages (`:5027`). When the memory is nearly full, the code keeps the first message and drops one exchange (`:5076-5084`). A personality prefix is added to the first message only (`:5085-5086`). Errors are returned to the player as `LLM Error: <message>` (`:5095`), and surrounding quotes are stripped from replies (`:5100-5106`). The fallback reply is "Something went wrong. Ask the admins to check the log." (`:5112`).
- Archon RAG (`buildCMRagRetreiver`, `:4917-5006`): builds an in-memory embedding store with an Ollama embedding model. The document list is empty and the loader that would fill it is commented out (`:4939-4951`), so the index is built from nothing (by reading).
- Callers: `Behaviors/MudChat.java`, `Commands/LLM.java`, `Abilities/Archon/Archon_LLMIFY.java`, `Common/DefaultScriptingEngine.java`, `Libraries/MUDPercolator.java`.

## Content worth mining

**MSP sound events.** The code plays 46 distinct literal sound names. The most used are listed below with their call-site counts. Counts are call sites, not files.

| Sound | Calls | Sound | Calls |
|---|---|---|---|
| `fireball.wav` | 16 | `earthquake.wav` | 5 |
| `whisper.wav` | 11 | `wind.wav`, `splat.wav`, `hail.wav`, `choke.wav`, `arrow.wav` | 4 each |
| `spelldam1.wav` | 11 | `web.wav`, `shock.wav` | 3 each |
| `spelldam2.wav` | 10 | `poisoned.wav`, `meteor.wav`, `decap.wav`, `blizzard.wav`, `thunder.wav`, `thunderandrain.wav`, `rain.wav`, `dooropen.wav`, `bashed2.wav`, `thunder2.wav`, `rainlong.wav` | 2 each |
| `lightning.wav` | 9 | `death`, `punch` (no extension) | 3 and 2 |
| `appear.wav` | 9 | `yawn.wav`, `water.wav`, `thunder3.wav`, `scream.wav`, `ouch.wav`, `missed.wav`, `levelgain.wav`, `gulp.wav`, `fire.wav`, `drink.wav`, `doorunlock.wav`, `doorlock.wav`, `doh.wav` | 1 each |
| `bashed1.wav` | 8 | `tornado.wav`, `knock.wav`, `explode.wav` | 5 each |
| `bless.wav` | 7 | `windy.wav` | 2 |

Used in: weather behaviour, spells and prayers, several combat skills, and door and lock commands.

**MXP image key families** (`resources/mxp_images.ini`, about 640 entries): `RESOURCE` and `RACE` 176 each, `RACECAT` 45, `CORPSECAT` 44, `EXIT` 27, `LOCALE` 22, `CONTAINER` 21, `ARMOR` 19, `MUSINSTR` 18, `CLAN` 15, `WEAPON` 13, `RIDEABLE` 10, then `ITEM`, `COINS`, `SHIPCOMP`, `LIGHT`, `SHIELD`, `AMMO`, `ROOM`, `DRINK` and a handful of singles. The file maps keys to image file names. Values are not reproduced.

**Precedence chains** (`CMProtocols.java:1502-1895`), useful as a model for tagging:
- Race: `RACE_<ID>`, then `RACECAT_<category>`, then `RACE_*`, then `RACECAT_*`.
- Room: `ROOM_<ID>`, then `LOCALE_INDOOR_<desc>` or `LOCALE_<desc>`, then `ROOM_*`, then `LOCALE_*`.
- Armour: keyed by worn location bit, from torso to wield (`:1787-1806`).
- Weapon: by weapon class, then damage type, then ammunition type (`:1808-1820`).
- Food, drink, containers, wands, potions, pills, scrolls and dust: by item ID, then by material, then a wildcard.

**GMCP command catalogue** (43 names, `CMProtocols.java:930-975`): `core.*` (hello, supports set/add/remove, keepalive, ping, goodbye), `char.*` (login, vitals, statusvars, status, base, maxstats, worth, items, skills, effects), `group`, `room.*` (info, exits, items, mobiles, players), `comm.channel(.players)`, `request.*` (exits, room, area, char, sectors, group, quest), plus `ire.composer.setbuffer`, `rawcolor`, `maplevel`, `client`, `client.version`, `external.discord.hello` and `msdp`.

**MSDP variables** (36, `:2098-2136`): character (ACCOUNT_NAME, CHARACTER_NAME, LEVEL, EXPERIENCE and its MAX, TNL and TNL_MAX, HEALTH and HEALTH_MAX, MANA and MANA_MAX, MOVEMENT and MOVEMENT_MAX, MONEY, ALIGNMENT, AFFECTS); opponent (OPPONENT_NAME, _LEVEL, _HEALTH, _HEALTH_MAX, _RANGE, _STRENGTH); room (ROOM, LOCATION, ROOM_NAME, ROOM_VNUM, ROOM_AREA, ROOM_TERRAIN, ROOM_EXITS); server (SERVER_ID, SERVER_TIME, SPECIFICATION, WORLD_TIME); and three ARACHNOS names.

**MSSP fields** (`:4395-4493`): PLAYERS, STATUS, PORT, NAME, UPTIME, HOSTNAME, WEBSITE, LANGUAGE, ICON, CHARSET, INTERMUD, FAMILY, CRAWL DELAY, CREATED, CONTACT, EMAIL, CODEBASE, GAMEPLAY, AREAS, HELPFILES, MOBILES, OBJECTS, GAMESYSTEM, ROOMS, CLASSES, RACES, SKILLS, ANSI, XTERM 256 COLORS, XTERM TRUE COLORS, MCP, VT100, MCCP, MSP, MXP. Extra key/value pairs can be injected through a configuration list (`:4495-4519`).

**Ability group names.** `getAbilityGroupName` (`:2612-2616`) builds a lower-case "<access code>-<domain>" key from the ability's classification and domain. The skill and effect group browsers use it (`:3843-3861`).

**Short messages** (paraphrased where long): "Your transfer could not be completed." (`:397`, `:404`); "A player named '<name>' already exists here. Transfer rejected." (`:462`); "Your home mud redirected you here, but your character is no longer active on that mud." (`:562`); "A transfer request for <name> is already in progress. Please wait." (`:658`); "Something went wrong. Ask the admins to check the log." (`:5112`).

**LLM providers** (`:4645-4725`): Ollama, Anthropic, Azure OpenAI, DashScope, GitHub Models, Gemini, Vertex Gemini, Vertex PaLM 2, Hugging Face, Jlama, LocalAI, Mistral, OpenAI, OCI GenAI, Qianfan, Cloudflare Workers AI, ZhipuAI, Bedrock (Anthropic, Cohere, Llama 2), Cohere, DeepInfra, Groq, IBM watsonx, NVIDIA, Together AI and Xinference.

## Notable design ideas

1. **Change-detected state push.** Each session keeps the last value of every reported variable (MSDP `reportables`, `CMProtocols.java:2633-2668`) and the last hash of every GMCP packet (`reporteds`, `:3959-3977`). The server recomputes once a second and sends only what changed. This is the most reusable pattern in the bucket.
2. **Snapshot packages with stable string IDs.** Each GMCP package is a small JSON snapshot built from the same world model. Room and exit IDs are hashed to numbers only for the wire (`:3544`, `:3672`). The game keeps the string IDs.
3. **Semantic tokens rendered per client.** Game text carries `^<IMAGE ...^>` and `!!SOUND(...)` tokens. The output filter decides what each session sees, as an MXP tag, an MSP token, or nothing (`CoffeeFilter.java:736-760`, `:1225-1262`). Content is written once and rendered per capability.
4. **Precedence lookup with an explicit opt-out.** The image chain (`:1502-1895`) tries specific, then category, then wildcard. The value `NULL` stops the search and means "no image" (`:1495-1499`).
5. **Plug-in packages by directory and config.** `MCPPackage` objects are loaded from a folder listed in `coffeemud.ini` and dispatched by name or name prefix (`:1313-1400`).
6. **Two-phase handoff with timeouts.** The MPCP offer, verdict and transfer sequence (`:644-846`) is a small, explicit state machine. Every pending state has a timer.
7. **Reusing game text as data.** `OPPONENT_STRENGTH` sends the output of the CONSIDER command (`:2384-2399`), so a client receives the same wording a player sees.
8. **Mail falls back to the in-game journal.** `emailOrJournal` (`SMTPclient.java:255-283`) makes the in-game mailbox the sink of last resort, with a per-recipient cap.

## Problems and cruft

Found by reading. None was run, so each is a claim to check.

1. **Unwrapped and unlabelled MSDP-over-GMCP output.** The GMCP `msdp` reply returns bare JSON with no package word (`CMProtocols.java:2963`). The periodic fallback in `pingGmcp` writes bare JSON straight into the output without the IAC SB GMCP frame (`:4245-4247`), and `DefaultSession.doProtocolPings` writes those bytes raw (`DefaultSession.java:3150-3157`). A GMCP-only client may see stray text or be unable to route the packet.
2. **Malformed `comm.channel.players` JSON.** The `name` value is never closed (`CMProtocols.java:3795`), and player objects are concatenated with no comma between them (`:3794-3809`).
3. **SMTP body not dot-stuffed.** `sendMessage` sends the body unchanged (`SMTPclient.java:520`). A body line that is exactly `.` ends DATA early, and the rest is read as SMTP commands. RFC 5321 section 4.5.2 requires escaping.
4. **`stripANSI` terminators.** Only `m` and `z` end an escape (`CMProtocols.java:4385`). Other CSI sequences swallow text up to the next `m` or `z`. An unterminated sequence gives a negative length at `:4390`.
5. **Escaped 0xFF dropped.** `stripTelnet` removes IAC IAC altogether instead of emitting one 0xFF byte (`:4301-4303`). Minor.
6. **Silent truncation in compression.** `decompressString` makes one `inflate` call into a shared buffer (`CMEncoder.java:60`). Output beyond the buffer is lost with no error. `compressString` makes one `deflate` call into a buffer sized to the character count (`:85-91`), so incompressible input may also be cut. Descriptions use this path (`MOBS/StdMOB.java:2030`, `:2041`).
7. **Weak password checks.** Hashes are 32-bit `String.hashCode` values of lower-cased password plus salt (`CMEncoder.java:105-108`). Plain comparison is case-insensitive (`:180`, `:193`). The both-hashed comparison (`:152`) is wrong for differently salted hashes (see section 10).
8. **Reversible "encryption" in web credentials.** The filter (`:197-257`) is reversible for anyone with the config values and host MAC bytes. It is used for the `CMAUTH` cookie and URL parameters (`WebMacros/Authenticate.java:86-90`, `:105-106`).
9. **Clear-text passwords by e-mail.** The admin reset sends the new password in the message body (`Commands/Reset.java:945-955`). If the account has no valid address, the password is set to the lower-cased character name and sent as well (`:962`, `:968`).
10. **Lookup overwrites in MXP.** `DoorKey` assigns the `KEY_<material>` lookup and then overwrites it at once (`CMProtocols.java:1655-1656`). `Light` does the same (`:1859-1860`). The `PowerGenerator` branch builds `POWERGENERATOR_POWERGENERATOR_<ID>` (`:1724-1726`).
11. **Effect "hash" is a count.** `pingGmcp` compares `mob.numEffects()` with the last value (`:4174-4176`). Replacing one effect with another leaves the count unchanged, so no update is sent. The variable name `lastEffectHash` is misleading.
12. **Identity-based change keys.** Room, MOB and victim objects are compared with `equals`, which is identity here, since none of those classes override it. `OPPONENT_STRENGTH` therefore resends CONSIDER text only when the victim changes (`:2235-2237`, `:4231-4234`).
13. **MSDP arrays lose repeats.** `buildMsdpMap` skips any string already in the list (`:2068`).
14. **MSDP gaps.** RESET is a TODO (`:2618-2630`). `MSDPConfigurableVar` is empty, so CONFIGURABLE_VARIABLES is always empty (`:2138-2140`). `ARACHNOS_DEVEL` and `ARACHNOS_CHAT` have no handler (`:2134-2135`).
15. **Fixed sub-option window.** Non-MPCP sub-options are read for 100 ms and the reader does not check for IAC SE (`DefaultSession.java:2189-2213`). Large MSDP or GMCP payloads can be cut.
16. **Hand-built JSON without escaping.** The character name (`CMProtocols.java:3369`), the enemy name (`:3492`) and the player key in `room.players` (`:3261`) are written unescaped. Most other packages use `MiniJSON.toJSONString`.
17. **Pseudo-codes in the telnet byte space.** ANSI16, ANSI256 and ANSI reuse the byte values of WONT, DONT and DO (`Session.java:1099-1105`). Where those flags reach a telnet parser they are ambiguous. I did not trace every use.
18. **SMTP details.** MX hosts are tried in server order, without sorting by preference (`SMTPclient.java:137-176`). The body is written with the platform default charset (`:109`), while the header declares ISO-8859-1 and 7bit (`:507-508`). `getListMembers` has no callers and loops forever once the stream returns null, because `rstr += reply.readLine()` never stops (`:566-576`).
19. **HTTP client.** Unused and plain-HTTP only. The INBODY state writes the end-of-stream marker as a 0xFF byte (`DefaultHttpClient.java:363`). A `Content-Length: 0` response consumes one extra byte (`:363-366`). A HEAD response with a Content-Length waits for a body that never arrives (`:362-366`, `:274-279`).
20. **LLM settings.** Optional settings are all-or-nothing (`CMProtocols.java:4871-4888`). The Archon index is built from zero documents (`:4939-4951`).
21. **Twitter success code.** A 201 response is logged as an error (`:4612`). Unverified against the live API.
22. **Protocol guide drift** (`guides/Protocols.html`). MSP is said to be negotiated with "WILL MCP", but the code sends WILL MSP (`DefaultSession.java:638`). IAC is given as decimal 252, but the code uses 255 (`Session.java:1113`). `core.ping` is said to echo the document, but the code returns the package word only (`CMProtocols.java:3094`).
23. **Bare `request`.** A `request` with no argument calls `setCharAt(-1)` inside the handler (`:2970`). The outer catch logs it, so the effect is only a log line.
24. **Proxy-only chunking.** Chunks are stitched only by the proxy (`MUDProxy.java:1048-1067`). The mud side expects whole payloads, so chunking is useless without the proxy.

Multiplayer assumptions to drop for a single-player game: the whole of MPCP (ghost sessions, swaps, returns, offers), the `room.players` list, per-account e-mail, and the session-level MSDP and GMCP reporting that exists to serve several clients.

## Takeaways for a Python SUD

1. **Build snapshots, not a protocol.** Define named, JSON-able snapshots such as `char.vitals`, `room.info`, `room.exits`, `char.items` and `group`, computed from the world model. Each tick, serialize each one with `sort_keys=True` and push it only when the text changes. A terminal side panel or a browser panel can read the same data. A minimal sketch:

   ```python
   def push_changes(session, snapshots, last):
       for name, build in snapshots.items():
           text = json.dumps(build(session), sort_keys=True)
           if last.get(name) != text:
               last[name] = text
               session.emit(name, text)
   ```

2. **Stable IDs in the model.** Keep string IDs such as `room:town_square` and `item:4f2a` as the identity. Derive numbers only where a client needs them. Do not use Python's `id()` or `hash()`, which change between runs.
3. **Semantic tokens with per-output renderers.** Let the text layer emit tokens such as `{sound:fireball}` or `{img:race.elf}`. Each output (ANSI terminal, HTML, plain text) decides what a token becomes, and an unknown token becomes nothing. Use CoffeeMud's precedence idea: try `item:<id>`, then `category:<cat>`, then `*`, with a `null` sentinel to stop. Store the table as TOML or YAML rather than the 40-branch `instanceof` ladder at `CMProtocols.java:1502-1895`. Put an `image_keys()` method on each object class instead.
4. **A small plug-in interface.** Mirror `MCPPackage` with an abstract base that has `name`, `min_version`, `max_version` and `execute(session, command, args)`. Load the packages from a folder named in the config, and dispatch on exact name or on name prefix.
5. **Reportable variables.** A registry of named getters, each with a change key, feeds the sidebar or the web panel. Keep the change key a value, not an object identity. CoffeeMud's identity-based keys are the source of its stale-consider bug.
6. **Mailbox as the default sink.** An in-world letters table in the save store fits the single-player model. Drop SMTP. If external mail is ever wanted, use a library and keep the journal fallback.
7. **Drop from this bucket:** telnet framing, MXP and MSP wire details (keep the token idea), the MCP 2.1 parser, MPCP, MSSP, Twitter, the XOR filter, the 32-bit password hash, the HTTP client (use `httpx` or `requests`), and the reflection-based LLM loader (call an SDK directly).
8. **Compression.** If descriptions or logs are compressed, use `zlib` in Python. It has no shared-buffer truncation.
9. **Passwords.** A single-player game may not need accounts at all. If it does, use `hashlib.scrypt` or `argon2`. Do not reuse the encoder's approach.

## Coverage

- **Read in full (10 of 10 files):** `Libraries/CMProtocols.java` (all 5,149 lines, including MPCP, MCP, MSDP, GMCP, MXP, MSSP, Twitter and the LLM bridge); `Libraries/SMTPclient.java`; `Libraries/interfaces/SMTPLibrary.java`; `Libraries/interfaces/ProtocolLibrary.java`; `Libraries/CMEncoder.java`; `Libraries/interfaces/TextEncoders.java`; `Common/interfaces/HttpClient.java`; `Common/DefaultHttpClient.java`; `Libraries/mcppkgs/MCPNegotiatePackage.java`; `Libraries/mcppkgs/MCPMooSimpleEditPackage.java`.
- **Read in part, outside the bucket, to explain interactions:** `Common/DefaultSession.java` (`:264-290`, `:619-655`, `:693-745`, `:1812-1830`, `:1945-1990`, `:2189-2213`, `:2291-2302`, `:2687-2703`, `:3132-3190`, `:4060-4118`); `Common/interfaces/Session.java` (`:1030-1164`); `Libraries/CoffeeFilter.java` (`:736-812`, `:986-1000`, `:1225-1266`); `application/MUDProxy.java` (`:1044-1080`); `Libraries/CMPlayers.java` (`:170-240`); `Libraries/Dice.java` (`:398-406`); `Commands/Reset.java` (`:936-972`); `WebMacros/Authenticate.java` (`:80-110`); `MOBS/StdMOB.java` (`:2000-2045`, `:3990-4005`); `Abilities/StdAbility.java` (`:650-680`); `Libraries/CommonMsgs.java` (`:2105-2112`); `Common/interfaces/CharState.java` (`:60-70`); `coffeemud.ini` (key lines only); `guides/Protocols.html` (MSP, MXP, GMCP sections); `resources/mxp_images.ini` (key survey only).
- **Skimmed or skipped:** the rest of `DefaultSession.java` and `CoffeeFilter.java`; `MUDProxy.java` beyond the stitching function; call sites of the GMCP and MSDP handlers beyond the counts given; the `LLM` callers beyond their names; the values in `mxp_images.ini`; `CMChannels.java` beyond the one `tweet` call.
- **Not run:** all findings in Problems were established by reading. Runtime checks were not attempted.
