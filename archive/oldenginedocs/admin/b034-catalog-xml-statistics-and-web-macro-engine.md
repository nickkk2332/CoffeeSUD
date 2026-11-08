# Catalog, XML, statistics and web macro engine

> Part of the CoffeeMud NEWOVERVIEW. Bucket: `b034` (Catalog, XML, statistics and web macro engine) · 10 files · 8904 lines of Java.

## Summary

These ten files hold the data plumbing of CoffeeMud. The catalog (`CMCatalog`) keeps named prototype mobs and items, tracks every live copy of each one, and spawns or drops copies under data-driven rules (a zapper mask, a rate and a cap). The XML library (`XMLManager`) is a hand-written XML parser and writer, with a reflection-based object serializer, and much of the saved game text (rooms, mobs, items and exits) passes through it. The web macro engine (`WebMacroCreamer`) renders the server's HTML pages by expanding `@macro@` tokens, including block forms (if, for, loop, block) and an embedded JavaScript block. The statistics library (`CoffeeTables` and `DefaultCoffeeTableRow`) counts game events per calendar day under prefix-coded keys (class, race, level, area and so on) and stores one XML row per day. For a single-player Python game the prototype-plus-spawn-rule model and the per-day event counters are the most reusable parts, while the XML and web layers are better replaced with standard formats and a standard template approach.

## Key files and types

File paths are relative to `com/planet_ink/coffee_mud/`, except `coffeemud.ini`, which sits at the CoffeeMUD folder root. Citations in the text use the same base.

| File or class | Role | Lines |
|---|---|---|
| `Libraries/CMCatalog.java` | Catalog: prototype lists, instance tracking, drop and spawn rules, builder templates, virtual file view | 1980 |
| `Libraries/XMLManager.java` | XML state-machine parser, escaping helpers, reflective object bridge | 1953 |
| `Libraries/WebMacroCreamer.java` | Web macro engine: page filter, macro dispatch, block forms, JavaScript block, web cache codes, `doGet` | 1801 |
| `Libraries/interfaces/CatalogLibrary.java` | Catalog API, plus nested `CataData`, `CataSpawn` (NONE, LIVE, DROP, ROOM) and `RoomContent` | 815 |
| `Libraries/interfaces/WebMacroLibrary.java` | Web macro and web cache API | 833 |
| `Libraries/interfaces/XMLLibrary.java` | XML API, plus nested `XMLTag` and `XMLTags` | 678 |
| `Common/DefaultCoffeeTableRow.java` | One day of statistics: `long[19]` counters per key, poll fields, XML serialization | 360 |
| `Common/interfaces/CoffeeTableRow.java` | Statistics row interface and the `STAT_*` event indices | 221 |
| `Libraries/CoffeeTables.java` | Statistics library: daily rollover, hourly poll and flush | 195 |
| `Libraries/interfaces/StatisticsLibrary.java` | Statistics API (`bump`, `update`, `readRawStats`) | 68 |

Referenced outside the bucket: `Commands/Catalog.java` (the in-game catalog command, read in part), `application/MUD.java:1432-1440` (the NOCACHE switches), `coffeemud.ini:2127` (default `NOCACHE=MAPFINDERS`), `core/interfaces/MudHost.java:46` (60-minute support period), `Libraries/CoffeeFilter.java:57-76` (pronoun token table), `Tests/XMLManagerTest.java` (unit tests for the XML functions).

## How it works

### Catalog: prototypes, instances and spawn rules

#### Data model

Two sorted lists hold the prototypes, `icatalog` for items and `mcatalog` for mobs, each a `PairVector` of prototype and `CataData` (`CMCatalog.java:54-55`). Each list is ordered by `Name()` (case-insensitive) and searched by binary search (`79-108`, `141-196`). The name is the only key. `addCatalogReplace` overwrites an existing prototype with the same name and destroys the old object (`175`).

A prototype is marked by the `PhyStats.IS_CATALOGED` disposition bit, which `changeCatalogFlag` sets and clears (`59-77`). Saved text carries the bit because callers call `text()` after changing it (`503`, `637`).

`CataData` (interface `CatalogLibrary.java:574-814`, implementation `CMCatalog.java:1215-1604`) holds:

- `category`, a string (`1218`).
- `lmaskStr` and `lmaskV`, a zapper mask in string and compiled form (`1217-1218`; semantics documented at `CatalogLibrary.java:576-604`).
- `spawn`, a `CataSpawn` value (`1219`).
- `rate`, a double from 0 to 1 (`1220`; `CatalogLibrary.java:617-626`).
- `cap`, an int with default 0 (`1221`; `CatalogLibrary.java:662-670`).
- `deathPickup`, a counter bumped on death or pickup (`1222`, `1355-1358`).
- `refs`, a vector of `WeakReference<Physical>` to every live copy (`1223-1224`).

Legacy data: `build()` reads an old `LIVE` tag. A zero rate gives spawn NONE and cap 0. Otherwise the spawn is LIVE or DROP and cap becomes 9999 (`1560-1573`). Current data reads `SPAWN` and `CAP` (`1576-1579`). `data()` writes `<CATALOGDATA CATAGORY="..."><RATE/><LMASK/><SPAWN/><CAP/></CATALOGDATA>` (`1524-1539`).

Live copies are counted by `numReferences()`, which counts the weak references that still resolve (`1331-1340`). `cleanHouse()` drops destroyed or uncataloged ones (`1361-1381`). `getLiveReference()` returns a copy that is in the game, or a dead mob that has rejuvenation set (`1384-1413`).

#### Maintenance

- `addCatalog` (`446-483`) copies the object, marks the original cataloged, adds the copy to the list, writes it to the `CATALOG_ITEMS` or `CATALOG_MOBS` table (`466`, `472`), and registers the original as an instance (`480`).
- `submitToCatalog` (`486-520`) stops the object ticking (`497`), clears its flag, serializes it (`503`) and calls `addCatalogReplace`.
- `updateCatalog` (`627-726`) replaces the prototype and saves it. It then searches rooms, inventories and shops for copies with the same name, using the search string `$name$` with a limit of 50 hits per search (`689-703`). Each match is re-flagged and re-serialized (`715-716`), and shopkeepers holding matches are resynced (`718-722`).
- `checkCatalogIntegrity` (`869-935`) compares a copy with its prototype using `sameAs` and per-stat codes (`893-899`). A difference in clan roles appends `CLANID,` (`907-921`). `updateCatalogIntegrity` (`845-856`) clears the cataloged flag on a copy that differs.
- `delCatalog` (`523-596`) removes the prototype and its database row (`535-547`), clears the flag on every known copy (`552-573`), then rebuilds the content of each room where a copy was found (`574-592`).
- `roomContent` and `updateRoomContent` (`312-443`) snapshot a room (shop stock, floor items, mob inventories, mobs) as `RoomContent` objects with dirty and deleted flags. Write-back covers only changed entries. Shop stock is resubmitted first (`423`), then the database rows are written.

#### Drops

`getDropItem` (`938-972`) runs for a dying mob (corpse drop, `live=false`) or an equipped mob (`live=true`). A candidate needs all of the following:

1. Spawn equals LIVE or DROP (`951`).
2. `Math.random() <= rate` (`952`).
3. The zapper mask matches the mob (`953`).
4. `numReferences() < cap` (`954`).

One survivor is picked uniformly (`968`), copied and registered (`969-970`). The rate is an independent gate per candidate, not a weight. The chance that a given item wins depends on how many other candidates pass their own gates.

Because the default cap is 0 (`1221`), an entry with no `CAP` never matches, since `0 < 0` is false. Legacy LIVE and DROP data gets 9999. The in-game Auto-Spawn command sets these fields (`Commands/Catalog.java:609-697`): the spawn kind from a menu, the cap as a positive integer (`642-652`), the drop rate as a positive percentage (`654-667`) and a mask (`668-697`). Choosing NONE clears the mask, rate and cap (`631-636`). The MUDGrinder pages set them too (`WebMacros/grinder/GrinderItems.java:577-604`, `WebMacros/grinder/GrinderMobs.java:588-612`).

#### Spawns

`activate` (`981-992`) starts the catalog's tick with the support period `TIME_SAVETHREAD_SLEEP`, which is 60 minutes (`MudHost.java:46`; `CMCatalog.java:988`). It also calls `tick` once at once (`989`). Each run of `tick` (`1035-1147`) does the following:

1. Groups ROOM-spawn entries that have cap above 0 and room left, counting them by mask string. Masks used by more than one entry are marked for caching (`1042-1079`).
2. Purges dead references on every entry (`1062`, `1087`, `1115`).
3. For each eligible item entry, tests `Math.random() < rate` (`1091`) and then loops while `--maxFails > 0` and the entry is under cap. `maxFails` starts at `(cap - refs) * 10` (`1094-1095`). Each successful room pick creates one item, which goes straight into the room with `setSavable(false)` (`1101-1105`).
4. Mobs follow the same pattern (`1116-1137`). A mob copy is brought to life in the room, flagged non-savable, given that room as its start room, and registered (`1129-1134`).

#### Room choice

`getSpawnRoom` (`994-1032`) works in two ways:

- With no mask, it tries up to 10 random rooms and accepts only a room for which `flags.isHidden(R)` is true (`997-1005`).
- With a mask, it collects every room that is not hidden and passes `maskCheck` (`1017-1021`). The list is cached per mask string for the tick (`1008-1030`), and one entry is chosen uniformly (`1031`).

`isHidden` tests only the `IS_HIDDEN` bit and never reports a mob in combat as hidden (`Libraries/Sense.java:530-540`). The unmasked test looks inverted against the masked one. The intent is unverified.

#### Builder templates and the file view

Builder templates are a separate store from the catalog (`1779-1899`). They are player-owned copies of a room, mob, item or exit, saved as XML under two player-data sections, `SYSTEM_BTEMPLATE_PERSONAL` and `SYSTEM_BTEMPLATE_SHARED` (`50-52`). Keys look like `SYSTEM_BTEMPLATE_<PLAYER>_<ID>` (`1862`). `makeValidNewBuilderTemplateID` (`1779-1792`) refuses an ID whose text before the first underscore names an existing player or account (`1789`), so one player cannot shadow another's template. `getBuilderTemplateList` marks each entry with a prefix: a space for personal, `+` for shared and `*` for someone else's (`1831-1837`).

The catalog also appears as a virtual directory tree, `catalog/mobs/...` and `catalog/items/...`, with one `.cmare` file per entry and an `all.cmare` file per folder (`1606-1776`). File contents are generated on read (`1684-1693`). Any change to the catalog clears the cached root, which forces a rebuild (for example `403`, `435`, `538`, `614-619`).

#### Import

`addCataDataFromXML` (`1907-1978`) chooses the `CATADATAS` block with the most matching names (`1945-1960`). It returns all matches or none (`1961-1973`). The "nothing found" error is at `1975-1976`.

### XML: parser, escaping and the object bridge

#### Data model and parser

`XMLpiece` (`XMLManager.java:109-428`) holds the tag name (upper-cased, `128`, `1087`), the raw inner text as `value` (set when the closing tag is seen, `1117`), `contents` (child tags), `parms` (attributes, names upper-cased, `1407`), a parent link, and four buffer offsets used by the streaming parser. `XMLTags` is a list sorted by lower-cased tag name (`XMLLibrary.java:419-441`).

The parser is a character-driven state machine with 14 states (`State`, `54-56`), run by `parseXML` (`1495-1543`). A tag must start with a letter (`1067-1070`). Attributes may be quoted, unquoted or bare (`1270-1493`). Self-closing tags end in `beginTagSelfEnd` (`1163-1193`). Comments, processing instructions and CDATA sections are removed by `handleTagBounds` (`984-1026`, bounds listed at `53`). A close tag searches up the parent chain for its opener (`1105-1124`). When none is found, the parser logs an error, except for names beginning with `HADLEY` (`1121`). A tag name that exactly matches an entry in the pronoun token table is not parsed as a tag (`1075-1081`; table at `Libraries/CoffeeFilter.java:57-76`). In practice only `IS-ARE` can match, because the other entries begin with a dash and a tag must begin with a letter.

#### Entry points and streaming

- `parseAllXML(String)` copies the text into a new buffer and parses it (`1546-1549`).
- `parseAllXML(StringBuffer)` parses the caller's buffer in place (`1552-1559`). Comment and declaration removal (`1012`, `1020`) therefore changes the caller's buffer.
- `getXMLParser` (`1561-1589`) returns a runnable for streaming input. Each run parses what has arrived, moves finished top-level tags to the output list, deletes the consumed text and resets the parser. Its only caller found is the CM1 intermud request handler (`Libraries/intermud/cm1/RequestHandler.java:441`).

#### Escaping

- `convertXMLtoTag(name, value)` writes the value as given, with no escaping. An empty value becomes `<NAME />` (`710-715`).
- `parseOutAngleBrackets` escapes `<` and `>` (`437-468`). `parseOutAngleBracketsAndQuotes` also escapes `"` (`471-515`). Neither escapes `&` or `%`.
- `restoreAngleBrackets` decodes `&lt;`, `&gt;`, `&quot;`, `&amp;` and `&apos;` (case-insensitive) and `%XX` hex pairs (`518-615`).

The pair is not symmetric. Writers leave `&` and `%` alone, and readers decode them. The unit tests show the author knows this, since the round-trip test avoids `&` (`Tests/XMLManagerTest.java:488-491`). Three effects follow from reading the code (traced, not run):

- An `&` followed by `l`, `g`, `q` or `a` (any case), with enough characters left, is dropped silently when the text after it is not an entity. The `case 'l'` path (`546-553`) appends nothing when its test fails. So `Tom&Lee` loads as `TomLee`.
- Near the end of a string, `&q...` and `&a...` can throw `StringIndexOutOfBoundsException`. The bound check (`542`) guarantees only four characters, but the `quot;` and `apos;` tests read five or six (`563-582`). For example, `&abc` at the end of a string fails this way.
- `%` followed by two hex digits is decoded, so `100%41` loads as `100A` (`592-607`). The unit tests expect this (`Tests/XMLManagerTest.java:481-486`), so it is intended for URL-style input. It also applies to saved game text.

CoffeeMaker writes room, mob, item and exit text with these helpers (for example `CoffeeMaker.java:1933`, `2206`) and reads it back with `restoreAngleBrackets` (`1076`, `1118`, `1167`). Any saved text with `&` followed by one of those letters is therefore exposed.

#### Query helpers

The `get...FromPieces` family finds the first child with a matching name, ignoring case (`833-843`, `809-821`). Numeric helpers return 0 on bad text (`624-699`). Boolean helpers treat any value starting with `T` as true (`877-879`). They also ignore the default they are given. `getBoolFromPieces(list, tag, defVal)` returns false when the tag is missing (`907-915`), and the per-piece version does the same (`305-313`). `CoffeeMaker.java:4314` passes `true` as the default for `SSCONST`, so an old file without that tag loads as false.

#### Object bridge

`fromPOJOtoXML` (`1709-1742`) writes each declared field of the object's own class as an element with the field's name. Null fields get `ISNULL=TRUE`. Arrays become `<VALUE>` children (`1678-1688`). It reads `getDeclaredFields()` (`1715`), so inherited fields are not written. `fromXMLtoPOJO` (`1779-1951`) reads the fields back by reflection and converts each value by its declared type. A malformed number raises `IllegalArgumentException` (`1927-1929`).

A `String` field is written with `parseOutAngleBrackets` (`1690-1691`) but read back without `restoreAngleBrackets` (`1870-1871`). So a value containing `a<b` comes back as `a&lt;b`. Strings inside arrays are decoded (`1826-1827`). Callers exist in `MOBS/StdLibrarian.java`, `Common/DefaultEnvironmental.java` and `Abilities/Misc/ExtraData.java`. They were not examined.

### Web macro engine

#### Request path

`convertOutput` (`84-115`) is the output converter for the embedded web server. It adds the HTTP status to the request objects (`86-89`), adds a `CLAN` parameter when the page sits under a clan's web path (`92-98`), and runs the page through `virtualPageFilter` (`103-105`).

`virtualPageFilter` (`392-865`) scans for `@`, reads a macro name with `parseFoundMacro` (`923-961`), handles the block forms itself, and otherwise calls `runMacro` (`867-921`). `runMacro` looks the name up with `CMClass.getWebMacro` (`896`) and calls `runBinaryMacro` or `runMacro` on the macro object (`905-915`). An unknown name is replaced by `[error]` (`827-829`). Macros are plugin classes under `WebMacros/`. The `WebMacro` interface declares `isAWebPath`, `isAdminMacro` and `preferBinary` (`WebMacros/interfaces/WebMacro.java:69-79`). Their defaults are all false (`WebMacros/StdWebMacro.java:66-81`).

#### Syntax and block forms

A token is `@NAME@` or `@NAME?PARAMS@`. Parameters may contain other macros, which are expanded first (`876-892`). Inside an extended macro (one whose name contains `?` after a letter or digit), `@@` stands for a literal `@` (`933-943`). Names are scanned up to `CMClass.longestWebMacro` characters unless they are extended (`348`, `956`).

Block forms, all implemented in `virtualPageFilter`:

| Form | Behaviour | Lines |
|---|---|---|
| `@if?COND@ ... @elif?COND@ ... @else@ ... @endif@` | COND runs as a macro. Its output is compared with `true`, or with `false` when the condition starts with `!` | `420-480`, `588-660` |
| `@for?VAR=LIST@ ... @next@` | Repeats the body for each comma item in LIST, setting VAR as a request parameter | `706-769` |
| `@loop@ ... @back@` | Repeats the body while a pass produces non-empty output. `@break@` produces empty output and ends the loop | `662-704`, `771-776` |
| `@block?NAME@ ... @/block@` and `@insert?NAME@` | Stores a fragment in the object map and emits it later | `537-550`, `576-586` |
| `@trim?CR LF TAB SPACE@ ... @/trim@` | Removes the named characters from the body | `552-574` |
| `@jscript@ ... @/jscript@` | Runs the body as JavaScript and emits what `write()` collected | `482-522` |
| `@<!--@ ... @-->@` | Deletes the block | `524-535` |

Matching ends are found by counting nested openers (`myEndif`, `myElse`, `myBack`, `myNext`, `myEndBlock`, `963-1093`).

#### Limits and admin gating

A page must finish within 120 seconds unless the thread is the admin server (`401-408`). The same check runs after each loop and for (`694-701`, `759-766`). Loop and for output is capped at 200 MB, after which `[error]` is inserted (`675-679`, `737-741`). Any exception inside the filter is logged and the partial page is returned (`846-857`).

The admin server flag comes from the thread's `ADMIN` configuration (`398-400`). `runMacro` refuses an admin macro on a non-admin thread, returns `[error]` and logs the client address (`900-903`). `doGet` (`1151-1204`) serves a macro named by the last segment of the URL. It checks only that the macro exists and that `isAWebPath()` is true (`1161-1171`). It has no equivalent admin check. A macro that is both a web path and an admin macro must check authentication itself. `RebuildReferenceDocs` does this and requires a sysop (`WebMacros/RebuildReferenceDocs.java:61-65`). Whether the public listener can reach `doGet` is outside this bucket.

#### JavaScript block

`@jscript@` creates a Rhino context (`502`), exposes `request()`, `write()`, `toJavaString()` and `getCMType()` (`1095-1143`), evaluates the body with `initStandardObjects` (`507`), and emits the buffer written by `write()`. An exception becomes `[jscript error: ...]` (`517`). Rhino's `initStandardObjects` is the unrestricted initializer; the restricted one is `initSafeStandardObjects`. The bundled Rhino version was not checked, so whether Java packages are reachable from the script is unverified. The safety of this block therefore rests on who can write page templates.

#### Web cache codes

Macros that run without a real HTTP request get a synthetic request (`createFakeRequest`, `117-282`). Its `getCookieNames` returns the keys of the request object map, not cookie names (`211-214`).

The web caches hold items and mobs that a page can name by a code, outside the normal world. They live in `Resources` under `SYSTEM_WEB_ITEM_CACHE` and `SYSTEM_WEB_MOB_CACHE` (`1270`, `1296`). An item's code is `Long.toString(hash << 5)` followed by its index in the cache, where the hash covers `ID/Name/displayText` (`1327`, `1347`, `1354`). A code therefore changes when the name or description changes. Lookup tries the exact code first, then retries with the last two characters dropped as a prefix (`1446-1451`). Catalog entries are named `CATALOG-<name>` and bypass the caches (`1511`, `1557`, `1577`, `1591`). `contributeMOBsToWebCache` copies a mob, sets its expiration to now and stops its items ticking (`1716-1739`). `getWebCacheSuffix` shows how long ago an expired object was cached, for objects expired within the last day (`1787-1800`).

### Statistics

#### Data model

One `DefaultCoffeeTableRow` covers one calendar day. `CoffeeTables` sets the start at 00:00 and the end at 23:59:59.999 (`70-90`). The row holds `stats`, a map from a key to a `long[19]` (`STAT_TOTAL = 19`), plus poll fields: `highestOnline`, `numberOnlineTotal`, `numberOnlineCounter` (the number of polls) and their player-count twins (`57-65`).

The 19 event indices are logins, ticks online, new players, levels gained, deaths, PK deaths, marriages, births, divorces, class changes, purges, skill uses, social uses, command uses, warrants, arrests, paroles, jailings and executions (`CoffeeTableRow.java:160-198`). Quest events use indices 1 to 9 under their own keys (`201-217`). The index gives the event type, and the key gives what it happened to.

Keys (`DefaultCoffeeTableRow.java:240-281`) are a one-letter prefix followed by a name:

| Prefix | Meaning | Lines |
|---|---|---|
| X | Area name (the model area for instances) | `244-249` |
| B | Base class | `250` |
| C | Current class ID | `251` |
| R | Race ID | `252` |
| L | Level | `253` |
| G | Gender code | `254` |
| F | Worshipped deity ID | `255` |
| Q | Each clan ID | `256-257` |
| J | Group size | `258-259` |
| P | Non-monster group members, at least 1 | `260-268` |
| A | Ability ID | `271-272` |
| U | Quest name | `274-275` |
| S | Social base name | `277-278` |
| M | Command ID | `280-281` |

A mob event therefore bumps nine or more keys at once (more with clans), so one death is counted by area, class, race, level and so on. NPC events are skipped (`191`).

#### Event flow

`CoffeeTables.bump(E, type)` (`61-132`) is the entry point. About 46 call sites in 25 files use it, for example `Behaviors/Arrest.java` (8), `Abilities/Prayers/Prayer_Marry.java` (2) and `Abilities/Misc/Pregnancy.java` (1). It returns early when statistics are disabled (`66-67`) and creates the day's row on first use. The first bump after start-up is discarded, because the first-use block ends with a `return` (`68-96`, the return is at `95`). When the calendar date changes, the old row is written (`106`), and the new day's row is read or created (`107-124`). Then the event goes to the day's row (`131`).

#### Polling and persistence

`CoffeeTables.tick` runs every 60 minutes on the support thread (`142`, `MudHost.java:46`). It checks database health and sleeps 100 seconds on an error (`158-163`). Otherwise it records a poll with `STAT_SPECIAL_NUMONLINE` (`166`) and flushes the row (`167`). The poll counts sessions and distinct players. A player is an account or an address, depending on `COMMONACCOUNTSYSTEM` (`DefaultCoffeeTableRow.java:194-238`). Addresses are used only as in-memory set keys. The average is `numberOnlineTotal / numberOnlineCounter`.

`data()` writes `<HIGH>`, `<NUMONLINE>`, `<NUMCOUNT>`, `<HIGHP>`, `<NUMPONLINE>` and a `<STATS>` block with one tag per key, holding a comma-separated list of counts (`121-146`). Keys are not escaped (`XMLManager.java:710-715`). `populate` parses a row back and adds duplicate keys together (`284-325`). `update()` (`46-59`) writes the current row. It is called by the hourly poll, by the `Stat` command (`Commands/Stat.java:114`) and by the web page (`WebMacros/CoffeeTableRows.java:80`). It is not called by `shutdown()` (`179-188`). The shutdown path elsewhere was not checked.

#### Readout

`readRawStats` (`190-194`) reads a date range from the database. `totalUp(code, tot)` (`165-180`) adds the arrays whose keys start with the code. The code `*` matches class-keyed entries only, through the `C` prefix (`172-173`).

## Content worth mining

- **Event and key taxonomy.** The 19 event types and the prefix table above. A text game can keep the same idea as a per-day `Counter` keyed by tuples such as `("death", area, class)`.
- **Pronoun token table** (`Libraries/CoffeeFilter.java:57-76`): HISHER, HIMHER, NAME, NAMESELF, HESHE, ISARE, HASHAVE, YOUPOSS, HIMHERSELF, HISHERSELF, SIRMADAM, MRMS, MISTERMADAM, ISARE2, NAMENOART, ACCOUNTNAME and WITHNAME. Each has an optional emote form. These are substitution tokens for gender-aware text.
- **Macro vocabulary** (`WebMacroCreamer.java:78-81`) and the block forms in the table above. The set is small and could drive a template engine.
- **Catalog spawn record** (`1524-1539`): category, rate, mask, spawn kind and cap in one short XML record. It is a compact spawn rule.
- **Builder-template kinds.** Rooms, mobs, items and exits, with personal, shared and other-owner markers (`1831-1837`).

## Notable design ideas

1. **Prototype registry with instance tracking.** Prototypes are kept apart from the world. Each copy is linked back by a weak reference and a flag. When a prototype changes, its copies are found by search and updated. Copies never hold a strong reference to the catalog (`1215-1224`, `627-726`).
2. **Spawn rules as data.** A rule is a category, a mask, a rate, a kind (room, live, drop) and a cap (`CataData`, `1215-1224`). The tick enforces the cap. It caches the eligible rooms once per tick for each distinct mask (`1074-1079`, `1008-1030`), so many rules that share a mask scan the world once.
3. **Gate, then pick.** Drop candidates pass a chance test and a mask test, and then one is chosen uniformly (`951-968`).
4. **Flat, prefix-coded counters.** Nine or more keys per mob event give a multi-way breakdown with no schema. The same map serializes as one XML row per day (`240-281`, `121-146`).
5. **Block macros by counting.** `myEndif`, `myElse`, `myBack` and `myNext` (`963-1093`) match block ends by counting nested openers, so blocks nest without a grammar.
6. **Owner-prefixed template names with a spoof check** (`1779-1792`).
7. **Schema-free serialization** by reflection, so any class's fields become XML and back (`1709-1951`).

## Problems and cruft

Bugs and hazards found by reading. Each was traced in the code and not run.

1. **Ampersand corruption on load.** Text containing `&` followed by `l`, `g`, `q` or `a` can lose the `&` on load (`XMLManager.java:541-591`). Writers do not escape `&` (`437-468`), so saved item, mob and room text can change silently.
2. **Exception on a truncated entity** (`562-582`). Input such as `&abc` at the end of a string throws.
3. **Boolean defaults ignored** (`305-313`, `907-915`). `CoffeeMaker.java:4314` depends on the default.
4. **String fields not decoded** on read (`1870-1871`), while writes escape them (`1690-1691`).
5. **Catalog locks do not lock.** `getSync` builds a new string on every call and never interns it (`CMCatalog.java:858-866`). Each `synchronized(getSync(...))` therefore locks a fresh object (calls at `458`, `498`, `605`, `633`, `731`, `761`). Mutual exclusion comes only from the list locks (`506`, `514`, `533`, `543`) and from `CataDataImpl`'s synchronized methods.
6. **Unmasked room spawns look inverted.** With no mask, only hidden rooms are accepted (`1001-1003`). With a mask, hidden rooms are excluded (`1020`). The intent is unverified.
7. **Unbounded spawns when the cache is off.** The cache is disabled when `NOCACHE` lists `CATALOG` (`MUD.java:1436`) or when the `CATALOGCACHE` disable flag is set (`core/CMSecurity.java:2995`). Then `addReference` does nothing (`1418-1419`), `numReferences()` stays 0 (`1331-1340`), and the cap is never reached. Each tick can create up to ten times the cap per entry (`1094-1095`, `1101-1105`). The default `NOCACHE` is `MAPFINDERS` (`coffeemud.ini:2127`), so this is off by default.
8. **Drop entries without a cap never drop** (`951-954`, default `0` at `1221`). The Auto-Spawn command insists on a cap above 0 (`Commands/Catalog.java:647`), so this mainly affects hand-edited or imported XML.
9. **Import clears the caller's list** on a missing name (`1969`).
10. **Replaced prototype destroyed** (`175`). Any other reference to the old object is left pointing at a destroyed object.
11. **First statistics event discarded** after start-up (`68-96`).
12. **Statistics lost on shutdown.** `CoffeeTables.shutdown` does not flush (`179-188`), so up to an hour of counts can be lost (see the flush paths above).
13. **Prefix collisions in totals.** `totalUp("L1")` also sums keys beginning `L1x`, such as L10 to L19 (`172`). Callers build codes like `"A"+abilityID` and `"M"+commandID` (`Commands/Stat.java:298`, `436`), so one ID that is a prefix of another is merged into it.
14. **Naming swap.** `highestOnline()` returns the player field and `highestCharsOnline()` returns the session field (`91-113`).
15. **Unescaped keys** in the stats row (`121-146`, `XMLManager.java:710-715`). An area name containing a markup character would break the row.
16. **Unterminated comments delete the rest of the input** (`984-1026`). `parseAllXML(StringBuffer)` also changes the caller's buffer (`1552-1559`).
17. **Cookie names** from the fake request are the request object keys (`211-214`).
18. **Macro name guard.** `parseFoundMacro` (`923-930`) returns null for any name that starts with `x` or `X`, because its digit test is redundant with its letter test. `clearWebMacros` uses the other parser (`363`, `319-352`), so the two passes disagree.
19. **Dead or no-op code.** `beAttrib` is never called (`XMLManager.java:1297-1323`, only the definition found). `if(M.isGeneric() || true)` (`WebMacroCreamer.java:1720`). `I.sameAs(I2);` has no effect (`1774`). The `HADLEY` special case (`XMLManager.java:1121`). `WebMacroCreamer.tick` checks a property and saves resources each run, but `activate` (`1218-1221`) schedules nothing, so it probably never runs (unverified).
20. **`Context.exit()` outside `finally`** (`502-519`). An `Error` thrown by the script skips it.
21. **Mislabelled statistics docs.** The comments for PAROLES and JAILINGS repeat "warning" (`CoffeeTableRow.java:191-194`).

Multiplayer assumptions that a single-player port should drop: online-player polling, account and address deduplication, session counts, PK deaths and the social-law counters (warrants, arrests, paroles, jailings).

## Takeaways for a Python SUD

- **Prototypes by ID, not name.** Store prototypes as dataclasses keyed by a stable ID. Each world object holds `proto_id`. CoffeeMud keys by name and needs a cataloged bit and weak references (`CMCatalog.java:141-196`, `1331-1340`). A single-player game can answer "all copies of X" with a plain query over the world, so the weak references and the flag are unnecessary.
- **Spawn and drop rules as data.** Use a small `SpawnRule` (kind, rate, cap, predicate) and choose drops with `random.choices(..., weights=...)` on a seeded RNG. That replaces the chance gate at `951-968` with a real weight. Run rules on game time (for example once per simulated day), not on a 60-minute wall-clock tick, so saves and tests are deterministic. Check the cap before each spawn, not after a retry budget.
- **Masks as predicates.** A zapper-style mask (`-LEVEL +>10 -RACE +Elf`) reads well in data. Start with dict predicates such as `{"min_level": 10, "race": "elf"}`, and add a parser only if authors need one. Cache eligible-room lists per predicate for each tick, as `CMCatalog` does (`1074-1079`).
- **Per-day counters as tuples.** Use a `Counter` keyed by tuples such as `("death", area, cls)`. Keep the events that drive a world sim: deaths, births, marriages, crimes, quests and skill use. Feed them to NPC memory or a town chronicle, not a database. Drop the online poll, the account deduplication and the PK counters.
- **Text templates, not a macro language.** Use `string.Template` or Jinja2 for substitution and conditionals, plus a pronoun-token table for gender-aware sentences. Do not embed a scripting engine. If dynamic text is needed, register named Python functions.
- **Data formats.** Use JSON, TOML or YAML rather than a hand-written XML parser. If XML is unavoidable, use `xml.etree.ElementTree`, which escapes correctly. Keep one escape and one unescape pair from the standard library, and test round trips that include `&`, `%`, `<` and quotes.
- **Stable object IDs.** Give each instance an integer or UUID. Do not derive cache codes from a hash of the name and description (`1327-1354`), since the code changes whenever the text does.
- **Skip for now.** Owner-prefixed blueprint names (`1779-1792`) matter only if players can share blueprints. The web cache, the virtual file view and the reflective object bridge are not needed for a single-player text game.

## Coverage

- **Read in full (every line):** `Libraries/CMCatalog.java` (1-1980), `Libraries/XMLManager.java` (1-1953), `Libraries/WebMacroCreamer.java` (1-1801), `Libraries/interfaces/CatalogLibrary.java`, `Libraries/interfaces/XMLLibrary.java`, `Libraries/interfaces/WebMacroLibrary.java`, `Libraries/interfaces/StatisticsLibrary.java`, `Libraries/CoffeeTables.java`, `Common/DefaultCoffeeTableRow.java` and `Common/interfaces/CoffeeTableRow.java`.
- **Read in part, outside the bucket:** `Commands/Catalog.java:600-704`, `Libraries/Sense.java:528-547`, `Libraries/CoffeeFilter.java:57-127`, `application/MUD.java:1424-1440`, `Tests/XMLManagerTest.java:440-501`, `WebMacros/StdWebMacro.java:55-90`, `WebMacros/RebuildReferenceDocs.java:40-70`, and `coffeemud.ini` (one line, by grep).
- **Checked by grep only:** callers of `setCap`, `setSpawn`, `setRate` (found in `Commands/Catalog.java` and `WebMacros/grinder/`), `getXMLParser`, `getBoolFromPieces`, `restoreAngleBrackets`, `totalUp`, `coffeeTables().bump` and `coffeeTables().update`; the `intern` and `beAttrib` searches; the `CATALOGNOCACHE` and `CATALOGCACHE` uses.
- **Skipped:** the other `WebMacros/` implementations, most of the catalog command and the MUDGrinder pages, the database layer, and the item and mob text load and save code that reads the cataloged flag.
