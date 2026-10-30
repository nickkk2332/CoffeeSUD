# Class registry, library registry and resources

> Part of the CoffeeMud NEWOVERVIEW. Bucket: `b001` (Class registry, library registry and resources) · 5 files · 7310 lines of Java.

## Summary

This bucket is CoffeeMud's bootstrap and shared-service layer. `CMClass` is a prototype registry: at boot it loads every game object type (races, classes, mobs, abilities, items, behaviours, commands, web macros, tests) from Java class files or JavaScript sources into per-type collections, and hands out fresh copies by ID, so content can be added by dropping in a file or a stored record. `CMLib` is a service locator for 55 named libraries (combat, dice, shops, help, quests, factions, and so on), each reached through a static accessor such as `CMLib.combat()` and identified by the interface it implements. `Resources` is a cache over the `resources/` tree, with a small persistent key-value store for system state. All three keep their state per thread group, so one JVM can host several MUDs that share or privately own each object. For a single-player Python game the registry and service-locator ideas are worth borrowing; the per-host machinery is not.

## Key files and types

Paths are relative to `com/planet_ink/coffee_mud/`.

| File or class | Role | Lines |
|---|---|---|
| `core/CMClass.java` | Prototype registry and class loader. `CMObjectType` enum (22 types), typed getters and finders, the boot sequence `loadAllCoffeeMudClasses`, pools for messages and factory mobs | 3971 |
| `core/CMLib.java` | Service locator. `Library` enum (55 entries), static accessors, `registerLibrary`, activation, `killThread` | 1785 |
| `core/Resources.java` | Per-host resource cache for text, raw, binary and multi-list files, plus the `::/coffeemud_properties.ini` section store | 1295 |
| `Libraries/StdLibrary.java` | Base class of all libraries: `L()` translation, `newInstance`/`copyOf`, `compareTo`, a no-op tick, database health check | 181 |
| `Libraries/interfaces/CMLibrary.java` | Lifecycle contract: `activate`, `shutdown`, `propertiesLoaded`, `getServiceClient`, two `L` overloads | 78 |

Read only as far as needed for the claims below: `core/CMFile.java` (path prefixes, `text`/`raw`), `core/CMProps.java` (`isPrivateToMe`), `core/collections/XVector.java` and `LimitedTreeMap.java`, `Commands/GenCommand.java`, `Abilities/GenAbility.java`.

## How it works

Citations give the path below `com/planet_ink/coffee_mud/`, so `core/CMClass.java:410` means `com/planet_ink/coffee_mud/core/CMClass.java:410` relative to the CoffeeMUD folder.

### 1. Per-host registries keyed by thread group

- Each class keeps a 256-slot array indexed by the first character of the current thread group's name: `clss` (`core/CMClass.java:63`), `libs` (`core/CMLib.java:55`), `rscs` (`core/Resources.java:28`). Constructors claim the slot for their group (`CMClass.java:96-104`, `CMLib.java:60-66`, `Resources.java:78-84`).
- Lookups such as `c()` (`CMClass.java:119-122`) and `l()` (`CMLib.java:72-75`) read the slot from the calling thread, so the same static call returns different data for different hosts.
- The base host is `MudHost.MAIN_HOST = '0'` (`core/interfaces/MudHost.java:50`).
- Other hosts share the base host's prototype collections unless the type is named in `PRIVATERESOURCES` (`coffeemud.ini:2319`, empty by default). For example, `c.races=baseC.races` at `CMClass.java:3234-3235`. The check is `CMProps.isPrivateToMe` (`core/CMProps.java:2623-2625`), backed by a set filled at `core/CMProps.java:2729-2734`.

### 2. The prototype registry (CMClass)

**Object types.** `CMObjectType` (`CMClass.java:143-196`) lists 22 types, each with the interface that defines it (`ancestorName`): RACE, CHARCLASS, MOB, ABILITY, LOCALE, EXIT, BEHAVIOR, CLAN, WEAPON, ARMOR, MISCMAGIC, AREA, COMMAND, CLANITEM, TECH, COMPTECH, SOFTWARE, ITEM, WEBMACRO, COMMON, LIBRARY, TEST. Order matters. `getObjectType` (`CMClass.java:1670-1685`) returns the first ancestor the object satisfies, and WEAPON, ARMOR and MISCMAGIC come before ITEM (`CMClass.java:161-180`), so more specific item kinds match first.

**Storage** (`CMClass.java:215-234`). Most types live in an `XVector`, a sorted `Vector`. Two use a `Hashtable`: `common` (line 215) and `webMacros` (line 233). `commandWords` (line 234) maps trigger words to commands. The static `classes` table (line 59) caches loaded `Class` objects by name.

**Ordering and lookup.** `XVector.sort()` calls `compareTo` on `Comparable` elements through `anyComparator` (`core/collections/XVector.java:276-314`). For CMObjects `compareTo` compares `classID` case-insensitively (`Libraries/StdLibrary.java:81-84`, `Abilities/StdAbility.java:1414-1417`). Lists are therefore sorted by ID, and `getGlobal(List, ID)` is a binary search on `classID` (`CMClass.java:2054-2082`). Map lookup (`getGlobal(Map, ID)`, `CMClass.java:2333-2347`) tries the exact key first, then scans every key case-insensitively.

**Prototype versus copy.** Two families of getters:

- Copies. `getNewGlobal` calls `newInstance()` on the prototype it finds (`CMClass.java:2040-2046`, `2317-2323`). Examples: `getAbility` (`1194-1204`), `getMOB` (`1086-1089`), `getBasicItem` (`1170-1173`), `getBehavior` (`1182-1185`).
- Shared templates. `getRace` (`1313-1316`), `getCharClass` (`1253-1256`), `getCommand` (`1289-1292`), `getLibrary` (`1050-1053`) and every `...Prototype` method (for example `getAbilityPrototype`, `1241-1244`) return the stored object. Callers must not mutate it.
- `getWebMacro` (`1301-1304`) is an exact `Hashtable.get` with no copy and no fallback. `addClass` stores keys uppercased (`1601`), and the callers pass uppercase names (`application/RebuildRefDocs.java:116`, `Libraries/WebMacroCreamer.java:378`, `896`, `1161`).

**Finders.**

- `findAbility(name, classDomain, flags, exactOnly)` (`CMClass.java:2104-2144`) filters by classification code and flags. It then tries the exact ID (`getGlobal`), then `CMLib.english().fetchEnvironmental` on name and display text, first exact and then fuzzy. Results are cached in `ableFinder` (line 60), a `LimitedTreeMap(300000, 100, true)`: entries expire after 300000 ms idle, at most 100 are kept, keys are case-insensitive (`core/collections/LimitedTreeMap.java:56-63` documents the arguments as expireMs, max, caseInsensitive).
- `findAbility(name, CharStats)` (`CMClass.java:2231-2256`) keeps only abilities for which `CMLib.ableMapper().getQualifyingLevel(classID, true, abilityID) >= 0` holds for one of the character's classes (lines 2238-2244). This restricts the list to what a character's classes can learn.
- `findCommand` (`CMClass.java:1941-1985`) tries the exact ID, then name or access words, then (when `startsWith` is set) prefixes. `findCommandByTrigger` (`1493-1508`) uses the uppercased `commandWords` map and falls back to a prefix scan.
- `getUnknown` (`CMClass.java:1883-1900`) resolves an ID whose type is not known by searching, in order, ITEM, ARMOR, WEAPON, MISCMAGIC, TECH, MOB, ABILITY and CLANITEM (`1871-1874`).

**Adding, removing and reloading.** `addClass` (`CMClass.java:1587-1620`) inserts into the list (then sorts) or the map, then clears what depends on the change: command words (`1608-1609`), library registration (`1611-1612`), and for abilities the `SYSTEM_FILTERED_ITEM_CRAFTORS` resource and `ableFinder` (`1614-1617`). `delClass` (`1534-1578`) also purges cached `Class` objects whose name is the removed class or starts with it followed by `$` (`1538-1547`). `loadClass(type, path, quiet)` (`1695-1716`) loads files into an existing set.

### 3. Loading classes and scripts

- `CMClass extends ClassLoader` (`CMClass.java:55`). Its `loadClass(String, boolean)` (`3018-3140`) does three things in order. It returns a cached class from `classes` (`3031-3037`). For core, application, `.interfaces.` and non-CoffeeMud names it delegates to the system loader (`3038-3057`). Otherwise it reads the bytes from a `CMFile`, which may be VFS or local (`3059-3064`), and defines the class (`finishDefineClass`, `2963-3003`).
- JavaScript classes (`.js`) are compiled with Rhino's `ClassCompiler` (`3107-3126`) after the loader reads header directives. `//EXTENDS` sets the superclass (`3077-3089`; a failed load is rethrown). `//PACKAGE` sets the output package (`3090-3091`). `//IMPLEMENTS` adds interfaces (`3092-3105`; a failed load is skipped with `continue`). The script runtime exposes two helpers from `JScriptLib` (`3627-3651`): `toJavaString`, and `getCMType`, which returns the lower-case type name.
- Directory loading: `loadObjectsIntoList` (`2671-2798`) lists `.class` files (skipping names containing `$`, line 2683), then `.js` files (`2686-2690`). Each class must pass `checkAncestry` (`2884-2895`: public, non-abstract, non-interface, assignable to the ancestor). It is instantiated with its no-argument constructor (`2727`). Maps store it under the uppercased simple class name (`2750-2756`); lists replace a same-named entry or append (`2758-2773`). If `ID()` differs from the class name, the loader logs `Possible class ID misnaming ID=... but class name=...` (`2747-2749`).

### 4. Boot sequence (`loadAllCoffeeMudClasses`)

Runs once per host (`CMClass.java:3190-3611`). Each type has one `coffeemud.ini` key that defaults to `%DEFAULT%` (`coffeemud.ini:2251-2272`). The value is a semicolon-separated list of directories or files. `loadObjectList` sorts by ID (`2537-2565`); `loadClassList` handles `%default%` (`2578-2605`).

1. Libraries. Load the LIBRARY list, call `CMLib.registerLibraries` (`3202-3205`), and fail if any `Library` slot is still empty: `Fatal Error: libraries are unregistered: ...` (`3206-3210`). Then call `propertiesLoaded` (`3211`).
2. Common objects (Hashtable), then web macros. Web macros always use `%DEFAULT%`, ignoring any ini key (`3224`), and track the longest name (`3226-3231`). Then races, character classes, mobs, exits, areas and locales.
3. Abilities. The configured list is loaded. If it contains `%DEFAULT%`, twenty standard subfolders of `Abilities/` are also loaded: Fighter, Ranger, Paladin, Druid, Languages, Properties, Diseases, Poisons, Misc, Prayers, Thief, Archon, Skills, Common, Specializations, Songs, Spells, SuperPowers, Tech, Traps (`3306-3401`). Languages is skipped when `DisFlag.LANGUAGES` is set (`3327`). Generic ability records from the database follow (`3409-3433`).
4. Items: basic, weapons, armor, misc magic, clan items, and tech (with COMPTECH and SOFTWARE appended, `3486-3498`). At least one of the six item sets must be non-empty (`3501-3502`).
5. Behaviours, then commands (plus database generic commands, `3523-3532`), then tests.
6. Failure policy. Any exception prints a stack trace and aborts the boot with `return false` (`3547-3551`). Each category also fails the boot when empty (for example `3203-3204`, `3241-3242`).
7. After the list phase: `reloadCommandWords` (`3553`), generic races and character classes from the database (`3573-3606`, inserted in sorted order by `addRace` and `addCharClass`, `2353-2390`), then a timestamp (`3609`).

**Data-defined objects.** The database supplies record data for abilities, commands, races and classes. Each record is applied to a `Gen*` prototype: `setStat("ALLXML", data)` for abilities and commands, `setRacialParms` (`3579`) for races and `setClassParms` (`3599`) for classes. For commands the registration happens inside the object: the ID branch of `GenCommand.setStat` calls `CMClass.addClass` (`Commands/GenCommand.java:208-219`). For abilities the ID comes from the record (`Abilities/GenAbility.java:42`, `1495`), and the loader keeps the object only when the ID is neither a generic name nor the type name (`CMClass.java:3420-3425`).

### 5. Service locator (CMLib)

- The `Library` enum (`CMLib.java:96-160`) has 55 entries. Each carries its ancestor interface (for example `COMBAT(CombatLibrary.class)`). The ordinal indexes the per-host `libraries[]` and `registered[]` arrays (`89-90`).
- Accessors. `CMLib.combat()` (`460-463`) casts `l().libraries[Library.COMBAT.ordinal()]`. The `_combat()` form (`1012-1015`) reads a specific host object. Several accessor names differ from the enum name: `beanCounter()` is MONEY (`430-433`), `coffeeShops()` is SHOPS (`440-443`), `itemBuilder()` is TIMS (`400-403`), `law()` is LEGAL (`490-493`), `lang()` is LANGUAGE (`761-764`), `hunt()` is WORLDHUNT (`690-693`), `space()` is GALACTIC (`560-563`).
- Registration (`registerLibrary`, `CMLib.java:1482-1504`). The enum code comes from `convertToLibraryCode` (`1464-1474`), which returns the first entry whose ancestor the object's class satisfies. For a library not private to this host, the host uses the base host's slot: the base host receives the object if its slot is empty, otherwise this host copies the base host's instance (`1490-1497`). For a private library, or on the base host, the object goes into this host's own slot, and a later registration overwrites an earlier one (`1499-1501`). Each registration sets `registered[code]` (`1502`).
- Activation. `activateLibraries` (`1627-1644`) copies shared slots and calls `activate()` on owned ones, logging `Unable to find library ...` for an empty slot (`1639-1640`). `propertiesLoaded` (`1602-1620`) calls `propertiesLoaded` on each library and resets three code counters: `CharStats.CODES`, `RawMaterial.CODES`, `Wearable.CODES` (`1617-1619`).
- `killThread` (`1513-1578`) refuses to kill the calling thread by throwing `ThreadDeath` (`1517-1518`), interrupts the target and retries, then dumps the stack of a thread that will not stop (`1561-1568`, message `Unable to kill thread`).

### 6. Library base class and lifecycle

- `CMLibrary` (`Libraries/interfaces/CMLibrary.java:23-77`) defines the lifecycle. `activate()` runs after boot and before connections are accepted (doc at lines 25-30). `propertiesLoaded()` runs when properties change. `getServiceClient()` returns a tick client for a library with its own thread, otherwise null. Two `L(...)` overloads translate strings.
- `StdLibrary` (`Libraries/StdLibrary.java:25`) supplies defaults. `activate()` sets `name` to the ID plus the host code (`117-121`). `tick` returns false (`156-160`), and `getTickStatus` returns `STATUS_NOT` (`150-154`). `checkDatabase` (`162-180`) pings every database connection, logs `DB: ...` on error, and returns false if a second check still fails.
- `L(String, ...)` (`StdLibrary.java:87-91`) passes the calling class to `CMLib.lang().fullSessionTranslation`. The caller is found with `SecurityManager.getClassContext()[2]` (`45-52`), so the same message key can be translated differently depending on which class asks for it.
- `newInstance` (`54-66`) uses reflection on the no-argument constructor. `copyOf` (`68-79`) uses `clone()`.

### 7. Resources

- Storage. A per-host `STreeMap` with case-insensitive keys (`core/Resources.java:37-53`). `_getResource` returns the stored object itself, not a copy (`737-747`).
- Text. `getFileResource` looks up the normalized name (`897-905`). On a miss it reads with `CMFile.text()`, which converts line ends to MUD format (`core/CMFile.java:1018-1024`), caches the result unless `FILERESOURCENOCACHE` is set (`912-913`), and returns an empty buffer, not cached, for a missing file (`916`).
- Raw and binary. `getRawFileResource` decodes the raw bytes using `CHARSETINPUT` (`958-966`; `coffeemud.ini:96` sets `iso-8859-1`) and caches under the same key (`974`). `getBinFileResource` caches a `byte[]` under that key too (`1000-1002`).
- Names. `makeFileResourceName` ensures a `resources/` prefix (`664-682`). The prefix `::` selects the VFS and `//` the local filesystem (`core/CMFile.java:129-130`). `PRIVATERESOURCEPATH` (`coffeemud.ini:2328`, empty by default) redirects private paths (`932-938`).
- Writes. `updateFileResource` updates the cache and then writes (`1014-1030`). `saveFileResource` writes on behalf of a user, checked through `CMFile(filename, whoM)` (`1041-1055`).
- Compression. Off by default (`29`). With `setCompression(true)`, StringBuffers are stored as compressed bytes through `CMLib.encoder()` (`688-691`, `765-776`, decompression at `742-744`).
- Multi-lists. A file format of key lines, each followed by entries and ended by a blank line (doc at `441-451`). `getMultiLists` parses it into a `Hashtable` (`617-656`). `getCachedMultiLists` caches it under `PARSED_MULTI: <NAME>` (`566-579`). `updateMultiListFile` writes it back with CRLF line ends (`452-479`).
- Property sections. `::/coffeemud_properties.ini` (`1124`, `1289`) holds bracketed sections of `key=value` pairs. Values are URL-encoded on save (`1281`) and decoded on load (`1154`). `getPropResource` returns `""` when a key is absent (`1214-1224`). `setPropResource` with an empty value deletes the key (`1239-1250`). Section names are prefixed with the host code on lookup (`1166-1167`).
- Per-object maps. `getPersonalMap(key, create)` keeps one `TreeMap` per key in a `WeakHashMap` under `SYSTEM_PERSONAL_MAPS` (`492-515`).
- In-place edit. `findRemoveProperty` removes one `KEY=` line from a properties file and saves it (`1065-1102`).

## Content worth mining

### Library catalogue

Paths are relative to `com/planet_ink/coffee_mud/`. Each implementation was found by grep for `class ... implements <Interface>`, with exactly one implementer per interface. The roles paraphrase the accessor doc comments in `core/CMLib.java`.

| Enum code | Accessor | Interface | Implementation | Role |
|---|---|---|---|---|
| DATABASE | `database()` | DatabaseEngine | `core/database/DBInterface.java:54` | database connections |
| THREADS | `threads()` | ThreadEngine | `core/threads/ServiceEngine.java:47` | thread access |
| INTERMUD | `intermud()` | IntermudInterface | `Libraries/IntermudClient.java:55` | Intermud3 networking |
| WEBMACS | `webMacroFilter()` | WebMacroLibrary | `Libraries/WebMacroCreamer.java:70` | web macro filtering |
| LISTER | `lister()` | ListingLibrary | `Libraries/CMLister.java:36` | listings of strings, items, objects |
| MONEY | `beanCounter()` | MoneyLibrary | `Libraries/BeanCounter.java:39` | money |
| SHOPS | `coffeeShops()` | ShoppingLibrary | `Libraries/CoffeeShops.java:44` | shops |
| COMBAT | `combat()` | CombatLibrary | `Libraries/MUDFight.java:43` | combat |
| HELP | `help()` | HelpLibrary | `Libraries/MUDHelp.java:42` | help files |
| TRACKING | `tracking()` | TrackingLibrary | `Libraries/MUDTracker.java:45` | tracking and movement |
| MASKING | `masking()` | MaskingLibrary | `Libraries/MUDZapper.java:44` | zapper masks (who qualifies) |
| CHANNELS | `channels()` | ChannelsLibrary | `Libraries/CMChannels.java:55` | chat channels |
| COMMANDS | `commands()` | CommonCommands | `Libraries/CommonMsgs.java:43` | command shortcuts and common events |
| ENGLISH | `english()` | EnglishParsing | `Libraries/EnglishParser.java:44` | grammar and input parsing |
| SLAVERY | `slavery()` | SlaveryLibrary | `Libraries/SlaveryParser.java:39` | slavery and geas |
| JOURNALS | `journals()` | JournalsLibrary | `Libraries/CMJournals.java:49` | message boards and journals |
| FLAGS | `flags()` | CMFlagLibrary | `Libraries/Sense.java:41` | flag checks |
| OBJBUILDERS | `coffeeMaker()` | GenericBuilder | `Libraries/CoffeeMaker.java:48` | GenObject construction |
| SESSIONS | `sessions()` | SessionsList | `Libraries/Sessions.java:31` | telnet sessions |
| TELNET | `coffeeFilter()` | TelnetFilter | `Libraries/CoffeeFilter.java:39` | telnet input and output |
| XML | `xml()` | XMLLibrary | `Libraries/XMLManager.java:43` | XML parsing |
| SOCIALS | `socials()` | SocialsList | `Libraries/Socials.java:40` | socials |
| UTENSILS | `utensils()` | CMMiscUtils | `Libraries/CoffeeUtensils.java:51` | random world utilities |
| STATS | `coffeeTables()` | StatisticsLibrary | `Libraries/CoffeeTables.java:35` | statistics |
| MAP | `map()` | WorldMap | `Libraries/CMMap.java:50` | areas and rooms |
| QUEST | `quests()` | QuestManager | `Libraries/Quests.java:37` | quests |
| ABLEMAP | `ableMapper()` | AbilityMapper | `Libraries/CMAbleMap.java:43` | ability collections and class qualifications |
| ENCODER | `encoder()` | TextEncoders | `Libraries/CMEncoder.java:30` | hashing and compression |
| SMTP | `smtp()` | SMTPLibrary | `Libraries/SMTPclient.java:34` | email |
| DICE | `dice()` | DiceLibrary | `Libraries/Dice.java:23` | dice rolls |
| FACTIONS | `factions()` | FactionManager | `Libraries/Factions.java:41` | factions |
| CLANS | `clans()` | ClanManager | `Libraries/Clans.java:49` | clans |
| POLLS | `polls()` | PollManager | `Libraries/Polls.java:40` | player polls |
| TIME | `time()` | TimeManager | `Libraries/CoffeeTime.java:39` | real-time utilities |
| COLOR | `color()` | ColorLibrary | `Libraries/Brown.java:36` | ANSI colour |
| LOGIN | `login()` | CharCreationLibrary | `Libraries/CharCreation.java:57` | login and character creation |
| TIMS | `itemBuilder()` | ItemBalanceLibrary | `Libraries/TimsLibrary.java:35` | item balancing |
| LEVELS | `leveler()` | ExpLevelLibrary | `Libraries/CoffeeLevels.java:44` | levels and experience |
| EXPERTISES | `expertises()` | ExpertiseLibrary | `Libraries/ColumbiaUniv.java:40` | expertises |
| MATERIALS | `materials()` | MaterialLibrary | `Libraries/RawCMaterial.java:38` | raw materials |
| LEGAL | `law()` | LegalLibrary | `Libraries/MUDLaw.java:41` | laws and property |
| LANGUAGE | `lang()` | LanguageLibrary | `Libraries/DirtyLanguage.java:37` | localization of `L()` strings |
| CATALOG | `catalog()` | CatalogLibrary | `Libraries/CMCatalog.java:42` | catalogued mobs and items |
| PLAYERS | `players()` | PlayerLibrary | `Libraries/CMPlayers.java:49` | players and accounts |
| AUTOAWARDS | `awards()` | AutoAwardsLibrary | `Libraries/AutoAwards.java:41` | automatic awards and titles |
| ABLEPARMS | `ableParms()` | AbilityParameters | `Libraries/CMAbleParms.java:50` | recipe and skill parameters |
| GENEDITOR | `genEd()` | GenericEditor | `Libraries/CMGenEditor.java:65` | generic object editor |
| AREAGEN | `percolator()` | AreaGenerationLibrary | `Libraries/MUDPercolator.java:58` | random maps and objects |
| TECH | `tech()` | TechLibrary | `Libraries/GroundWired.java:44` | tech and electricity |
| PROTOCOL | `protocol()` | ProtocolLibrary | `Libraries/CMProtocols.java:64` | MXP, MSDP and other protocols |
| ACHIEVEMENTS | `achievements()` | AchievementLibrary | `Libraries/Achievements.java:54` | achievements |
| ABLECOMP | `ableComponents()` | AbilityComponents | `Libraries/CMAbleComps.java:49` | ability components |
| GALACTIC | `space()` | GalacticMap | `Libraries/CoffeeDark.java:40` | space maps |
| CITY | `city()` | CityMap | `Libraries/CityCafe.java:38` | city objects |
| WORLDHUNT | `hunt()` | WorldHuntLibrary | `Libraries/WorldHuntUtils.java:40` | searching areas and rooms |

Two file names do not match their roles. `DirtyLanguage` implements the localization library, as its interface doc says (`Libraries/interfaces/LanguageLibrary.java:36-40`). `Sense` implements the flag library.

### Ability families by folder

The twenty `Abilities/` subfolders loaded from `%DEFAULT%` (`CMClass.java:3306-3401`) form a ready-made taxonomy: Fighter, Ranger, Paladin, Druid, Languages, Properties, Diseases, Poisons, Misc, Prayers, Thief, Archon, Skills, Common, Specializations, Songs, Spells, SuperPowers, Tech, Traps. The folder contents were not read.

### Object types

The 22 `CMObjectType` entries (`CMClass.java:143-189`) are a usable taxonomy for a game's content kinds: race, class, mob, ability, room, exit, behaviour, clan, weapon, armour, magic item, area, command, clan item, tech, component, software, item, web macro, common object, library, test.

### Short messages

These are the strings the code logs. They are not game text.

- `Fatal Error: libraries are unregistered: ` (`CMClass.java:3208`)
- `Possible class ID misnaming ID=... but class name=...` (`CMClass.java:2749`)
- `WARNING: class failed ancestral check: ` (`CMClass.java:2721`)
- `Unable to find library ` (`CMLib.java:1640`)
- `Sort failure @ ` (`CMClass.java:2562`)
- `Unable to kill thread ` (`CMLib.java:1564`)

## Notable design ideas

- **Thread group as tenant.** Each registry is picked by the calling thread's group (`CMClass.java:119-122`). One JVM can run several MUDs with shared content and separate private state, decided by one config list (`CMProps.java:2729-2734`, `CMClass.java:3213-3216`).
- **Class IDs as the registry key.** `classID` is the lookup key and the sort key (`CMClass.java:2926-2939`, `2040-2082`). Libraries are identified by the interface they implement, not a config name (`CMLib.java:1464-1474`).
- **Prototypes and copies.** Templates are stored once and copied on demand (`CMClass.java:2040-2046`), so content objects can be shared safely when they are read-only.
- **Data as class.** The `Gen*` family builds live objects from stored records, with the ID taken from the data (`Abilities/GenAbility.java:1495`, `Commands/GenCommand.java:208-219`). Builders can change behaviour without writing Java.
- **Hot loading with purge.** `delClass` removes registry entries and cached classes, including inner classes (`CMClass.java:1538-1547`), so content can be reloaded in a running game.
- **Boot as a data pipeline.** Each category is one ini key with `%DEFAULT%` plus extra paths (`coffeemud.ini:2251-2272`), and each logs a count as it loads.
- **Translation by caller class.** `L()` picks the translation scope from the calling class (`StdLibrary.java:45-52`, `87-91`).
- **Small persistent store.** The `::/coffeemud_properties.ini` sections (`Resources.java:1104-1294`) hold system state in a plain INI-like file with URL-encoded values.

## Problems and cruft

### Bugs found by reading (as written; none were run)

1. **`getByType(ARMOR, id)` returns an area type.** `CMClass.java:410-411` calls `getAreaType` for ARMOR, although `getArmor` exists at `CMClass.java:1158-1161`. `getObjectType` returns ARMOR for armour (`CMClass.java:1670-1685` with the enum order at `161-180`), so `Commands/GModify.java:180`, `Libraries/CoffeeMaker.java:3150` and `Common/DefaultMessage.java:834` reach the wrong lookup whenever the type is armour. The caller's null handling was not traced.
2. **`randomArea` fallback does nothing.** `CMClass.java:1011-1012` reassigns `areaTypes=c().areaTypes`, which is the same empty list, instead of the base host's list `clss[MudHost.MAIN_HOST].areaTypes`. The `get` at line 1016 then indexes an empty list.
3. **`delClass` misses mixed-case keys in hashtable sets.** `CMClass.java:1561` removes `O.ID().trim()`, but `addClass` stores `O.ID().trim().toUpperCase()` (`1601`), and `loadObjectsIntoList` does the same (`2753-2755`). For `common` and `webMacros`, removal fails for any ID with lowercase letters, such as `DefaultPlayerStats` (`1470`).
4. **`getClassesInPackage` ignores its own fallback.** `CMClass.java:550-556` chooses a loader `c` and then calls `c().getResources(path)`. With no CMClass for the thread group this throws, and the broad catch at `583-585` returns an empty array silently. The only caller is `core/MiniTSON.java:857`.
5. **`removePersonalMap` never removes.** `core/Resources.java:525` reads `SYSTEM_PERSONAL_MISC`, but `getPersonalMap` stores under `SYSTEM_PERSONAL_MAPS` (`495-498`). Caller: `Libraries/CMPlayers.java:177`.
6. **Binary and text entries collide in the cache.** `_getBinFileResource` stores a `byte[]` under the same key as text (`1000-1002`). `_toStringBuffer` returns null for a `byte[]` (`851-865`), so a later `getFileResource` of that path returns null (`903-905`). Latent, because text and binary paths rarely overlap.
7. **Text and raw caches collide on line endings.** `getFileResource` caches the converted text (`911-913`). `getRawFileResource` then returns that cached copy (`955-957`), which contradicts its doc, "with unnormalized line endings" (`294-301`). Which form a caller gets depends on call order.
8. **Multi-list reads and writes disagree.** `getMultiLists` hard-codes a `resources/` prefix (`629`), but the writer normalizes with `makeFileResourceName` (`478`). The reader also swallows every exception (`631-633`) and returns an empty map, which hides a missing file. The parsed map is a `Hashtable` (`619`), so saved files come out in hash order, not file order.
9. **Boot can fail on one bad database row.** `CMClass.java:3418` calls `CMClass.getAbility(type).copyOf()`. `getAbility` returns null for an unknown `typeClass` (`1194-1204`, `2040-2046`), which throws a NullPointerException. The catch at `3547-3551` turns that into a failed boot. Not reproduced.
10. **Section names do not round-trip for hand-written files.** Lookup adds the host code to the section name (`Resources.java:1166-1167`), but names read from the file are stored without it (`1134-1141`). A hand-written `[FOO]` section can never be found through `getPropResource("FOO", ...)`. Only names that already carry the host code are visible. Whether any shipped file depends on this was not checked.
11. **Unsafe double-checked locking.** `getAllPropResources` tests `propResources` outside the lock (`Resources.java:1118-1123`), and the field is not `volatile` (`32`).
12. **Rhino context leak on error.** `Context.enter()` (`CMClass.java:3107`) is paired with `Context.exit()` (`3133`) without a `finally`, so a compile error leaves the context attached to the thread.
13. **Silent retry in binary search.** `getGlobal(List, ...)` swallows `IndexOutOfBoundsException` and restarts the search with no bound (`2075-2078`). That hides concurrent modification.
14. **Lock held while sleeping.** `Resources.shareWith` sleeps inside `synchronized(rscs)` (`115-121`).
15. **Partial unload.** `CMClass.unload` (`3950-3970`) does not clear `libraries`, `tests` or `MSGS_CACHE`.

### Latent hazards

- `_getResource` returns the live cached object (`Resources.java:737-747`). A caller that edits the returned buffer edits the cache. Callers were not checked for this.
- `getSync` hands out per-key lock objects from `syncCache`, a `LimitedTreeMap` with a 5-minute idle expiry (`CMClass.java:61`, `3875-3893`). An evicted entry can give two callers different locks for one key. Callers were not checked.
- `getMsg` and `getFactoryMOB` use exceptions for control flow when the pool is empty (`CMClass.java:3684-3694`, `3902-3912`).
- Pooled factory mobs are not reset when returned (`CMClass.java:3862-3873`). The caller must clean up.
- The ancestor null check in `loadObjectsIntoList` (`2738`) dereferences a null ancestor when a class fails to instantiate and `quiet` is false. The ancestor is a known interface, so this should not occur in practice.
- `StdLibrary.L` depends on `SecurityManager`, which is deprecated for removal in recent JDKs (`StdLibrary.java:45`). Whether this build's JVM allows it was not checked. The index `[2]` also assumes exactly one wrapper frame.

### Cruft and documentation drift

- `coffeemud.ini:2285-2293` lists library names HTTP, RESPAWN and SECURITY, and class name DBVFS. None of them appears in the `Library` enum (`CMLib.java:96-153`) or in `CMObjectType` (`CMClass.java:143-189`). DBVFS is described as a database table (`coffeemud.ini:2313`).
- `OBJECTS_ITEMTYPES` lists MISCMAGIC twice (`CMClass.java:201`, `205`).
- The comment on `ancestorName` says "in meters" (`CMClass.java:191`).
- `intializeClasses` is misspelt (`CMClass.java:2484`) and is called from `application/MUD.java:1605`.
- `getUnknown` logs `Unknown Unknown '...'` (`CMClass.java:1898`).
- The boot counters use `=` where they should use `+=`. Line 3366 drops the Archon count added at 3362, and lines 3336, 3390 and 3396 also overwrite totals. The logged numbers are wrong, but nothing else uses them.
- The boot logs `t.printStackTrace()` rather than `Log.errOut` (`CMClass.java:3549`).

## Takeaways for a Python SUD

1. **One registry per content kind, lower-case IDs.** A generic class keyed by ID, with separate calls for a shared template and for a fresh copy. The sketch below is a suggestion, not code from the project.

   ```python
   class Registry(Generic[T]):
       def __init__(self) -> None:
           self._protos: dict[str, T] = {}

       def add(self, proto: T) -> None:
           self._protos[proto.id.lower()] = proto

       def prototype(self, obj_id: str) -> T | None:   # shared, do not mutate
           return self._protos.get(obj_id.lower())

       def new(self, obj_id: str) -> T | None:          # fresh copy for a world object
           proto = self.prototype(obj_id)
           return copy.deepcopy(proto) if proto else None
   ```

   Lower-case every key on the way in and out. That avoids the case mismatch in bug 3 above.
2. **A small `Services` container in place of 55 slots.** Give it typed attributes for the services a single-player game needs (combat or resolution, dice, time, text, help, commands, world map, quests, factions or reputation, economy). Each attribute is a `typing.Protocol` modelled on the Java interfaces in `Libraries/interfaces/`. Check at startup that every attribute is set, as `CMClass.java:3206-3210` does.
3. **Boot as a data pipeline that reports instead of aborting.** Load content from a directory tree of YAML, TOML or JSON. Log a count per category, as the Java boot does. Skip and report bad records rather than aborting, which avoids the failure in item 9. Keep the rule that core categories (races, classes, abilities) must be non-empty.
4. **Data-defined objects without the XML round trip.** A record is a dataclass or a dict, and a generic class is built from it (`Ability.from_record(rec)`). Drop the JavaScript and class-byte loading (`CMClass.java:3018-3137`). Plain modules and data files cover a single-player game.
5. **Commands by trigger word.** Keep the exact-then-prefix lookup from `CMClass.java:1493-1508` and `1941-1985`. It suits a text parser.
6. **Ability search with a class filter.** Keep the two-stage match (exact ID, then name or alias, then fuzzy) and the filter that shows only what the character's classes can use (`CMClass.java:2231-2256`). Skip the TTL cache. It adds nothing at this scale.
7. **Resources as typed caches.** One cache keyed by normalized path, with separate text and bytes caches, so item 6 cannot happen. Return immutable strings or copies, never the cached buffer (see the `_getResource` hazard above). Use `pathlib` for the data root in place of `::` and `//`. The section store (`Resources.java:1104-1294`) maps to `configparser` without the host-code prefix.
8. **Translation as an argument.** The `L(text, class)` idea (`StdLibrary.java:87-91`) becomes an explicit scope argument, `t(key, scope=...)`. Avoid stack introspection.
9. **Drop the multi-host machinery.** The thread-group arrays (`CMClass.java:63`, `CMLib.java:55`, `Resources.java:28`), the private and shared split (`CMProps.java:2729-2734`), the message and mob pools (`CMClass.java:236-239`, `3661-3931`), the `ThreadDeath` helper (`CMLib.java:1513-1578`) and reflection copying (`StdLibrary.java:54-79`). Python's allocator is adequate, and pooled mutable objects are where the reset hazards above come from.
10. **Concurrency, if the world ticks in the background.** Keep one owner for mutable world state and pass registries explicitly, instead of global lookups through `CMClass.c()`. The per-key lock map (`CMClass.java:3875-3893`) is only worth copying if the tick is threaded, and then with keys that do not expire.

## Coverage

- **Read in full, every line:** `core/CMClass.java` (3971 lines), `core/CMLib.java` (1785), `core/Resources.java` (1295), `Libraries/StdLibrary.java` (181), `Libraries/interfaces/CMLibrary.java` (78).
- **Read in part, for the claims above:** `core/CMFile.java` (prefix handling at lines 52-54 and 129-130; `text`, `textUnformatted` and `raw` at 1018-1040, 1122-1135 and 1220-1240); `core/CMProps.java` (700, 2623-2625, 2729-2734); `core/collections/XVector.java` (276-314); `core/collections/LimitedTreeMap.java` (constructor docs at 56-63); `Commands/GenCommand.java` (208-219); `Abilities/GenAbility.java` (42, 1211, 1495); `Abilities/StdAbility.java` (1414-1417); `coffeemud.ini` (96, 2151, 2251-2272, 2285-2328); `application/MUD.java:1605`.
- **Grep-checked only:** the 55 library implementers (one class declaration per interface, bodies not read). Callers of `getByType` (`Commands/GModify.java`, `Libraries/CoffeeMaker.java`, `Common/DefaultMessage.java`), `getClassesInPackage` (`core/MiniTSON.java`), `removePersonalMap` (`Libraries/CMPlayers.java`), `getWebMacro` (`application/RebuildRefDocs.java`, `Libraries/WebMacroCreamer.java`), and `intializeClasses` (`application/MUD.java`).
- **Skipped:** the bodies of the library implementations; the `Gen*` classes beyond the calls listed above; the content directories under `resources/` and the ability folder contents; the `coffeemud.ini` sections other than those cited.
