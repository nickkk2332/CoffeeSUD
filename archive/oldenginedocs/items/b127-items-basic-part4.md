# Basic items, part 4: ships, law books, journals, maps, item wrappers, false limbs and keys

> Part of the CoffeeMud NEWOVERVIEW. Bucket: `b127` (Items/Basic [part 4/5: StdItem .. StdNavigableBoardable]) · 8 files · 9052 lines of Java.

Citations are relative to `com/planet_ink/coffee_mud/` (shortened to `Items/...`, `Behaviors/...`, `Common/...` and so on). `resources/...` and `guides/...` are relative to the CoffeeMud folder. Nothing here was executed; every finding comes from reading the code.

## Summary

These eight classes are the specialised basic items. The largest, `StdNavigableBoardable`, is a whole sailing ship: it is an item that is docked in a room, has its own interior area, and takes course, anchor, steer and tender commands that it intercepts in its own message handling. `StdLawBook` and `StdJournal` are windows onto state held elsewhere: the law book reads and edits the `Law` of an area, and the journal reads and writes database rows under its own name. `StdMap` draws an ASCII map of one or more areas on each READ, computed from the room graph. `StdItemWrapper` is a read-only mirror that the planar spells use, `StdLimb` is a worn prosthetic that fills body-part slots, and `StdKey` is a lock name carried by an item. For a single-player Python game the most reusable parts are the ship's turn and move gating, the law book's flag grammar, and the map layout, which are described below.

## Key files and types

| File or class | Role | Lines |
|---|---|---|
| `Items/Basic/StdItem.java` | Base class for every item. Read only where this bucket depends on it; `b172` covers it in depth. | 2386 |
| `Items/Basic/StdNavigableBoardable.java` | Sailing ship: docking, interior area, strategic and tactical movement, course queue, anchor, tender, jump overboard | 1983 |
| `Items/Basic/StdLawBook.java` | Law book: 12 pages that read and edit the `Law` of one area | 1603 |
| `Items/Basic/StdJournal.java` | Journal: a named, database-backed board of entries with read, reply, transfer and copy | 1140 |
| `Items/Basic/StdMap.java` | Map item: ASCII map of named areas, generated on READ | 804 |
| `Items/Basic/StdItemWrapper.java` | Read-only proxy of another item, used by planar spells | 772 |
| `Items/Basic/StdLimb.java` | False limb: a worn item that fills a body-part slot | 290 |
| `Items/Basic/StdKey.java` | Key: carries a lock name that must equal an exit's or container's `keyName()` | 74 |

Inheritance: `StdNavigableBoardable` extends `StdSiegableBoardable` (`Items/Basic/StdSiegableBoardable.java:39`), which extends `StdBoardable` (`Items/Basic/StdBoardable.java:43`), which extends `StdPortal` (`Items/Basic/StdPortal.java:36`), which extends `StdContainer` and then `StdItem`. `StdJournal`, `StdLawBook`, `StdLimb`, `StdKey` and `StdMap` extend `StdItem` directly. `StdItemWrapper` implements `Item` and `CMObjectWrapper` but does not extend `StdItem`.

Related classes outside this bucket: `Items/Basic/StdContainerWrapper.java`, `StdRideableWrapper.java` and `StdPortalWrapper.java` (subclasses of the wrapper); `Items/Basic/GenLawBook.java:35` (data-driven law book, which keeps its own readable-text field); `Common/DefaultLawSet.java` (the `Law` implementation); `Common/interfaces/Law.java` (constants); `Behaviors/Arrest.java` (the enforcement behaviour that reads the flags).

## How it works

### 1. What this bucket inherits from StdItem

- **Worn state is a bitmask.** `amWearingAt` tests `(myWornCode & wornCode)==wornCode`, and `0` means "not worn" (`Items/Basic/StdItem.java:374-379`). `Wearable.IN_INVENTORY` is `0`, `WORN_HELD` is `512` and `WORN_FLOATING_NEARBY` is `4096` (`Items/interfaces/Wearable.java:251`, `271`, `277`).
- **Readable text is `miscText`.** `readableText()` returns `miscText` (`StdItem.java:601-610`). The three readable classes here each give that one string a different meaning: a list of area names for the map, one area name for the law book, and a block of `KEY=value` settings for the journal.
- **Periodic validity check.** Every `10 + d100` ticks, `StdItem.tick` calls `CMLib.flags().validCheck(this)` and destroys the item if the check fails (`StdItem.java:753-762`).

### 2. StdKey: a lock name carried by an item

- Defaults are "a metal key", steel, base value 0, and the description "a small metal key sits here." (`Items/Basic/StdKey.java:46-51`).
- `setKey` and `getKey` store the lock name in `miscText` (`StdKey.java:64-73`). Matching is plain string equality: `StdExit.java:627` (`getKey().equals(keyName())`) and `StdContainer.java:349` (same test), plus `StdContainer.java:382`, which uses `equalsIgnoreCase`.
- Lock names are random unless a lock already has one. `LockSmith.java:235` sets `keyCode=""+Math.random()` when a locksmith starts a job. `getBuilding` (`LockSmith.java:98-111`) then gives the new key the existing lock's name if the target has one, and the random name otherwise. `Common/DefaultCoffeeShop.java:762-768` does a similar thing when a locked container is taken from shop stock without its key: it sets a new random name on the container and creates a `StdKey` with that name.
- Keys have no identity of their own. Any copy with the same name opens the lock.

### 3. StdLimb: false limbs fill body-part slots

- **Which part.** `getBodyPartCode` (`StdLimb.java:127-141`) walks `Race.BODYPARTSTR` in order and returns the first part whose name ends the item's name or secret identity. The table is `ANTENNA, EYE, EAR, HEAD, NECK, ARM, HAND, TORSO, LEG, FOOT, NOSE, GILL, MOUTH, WAIST, TAIL, WING` (`Races/interfaces/Race.java:555-558`). A limb is therefore named by its part: "a left hand" ends in `HAND`.
- **Which slot.** `getWearLocations` (`StdLimb.java:151-163`) maps the part through `Race.BODY_WEARVECTOR` (`Race.java:605-622`). For example `ARM` maps to the arms slot, `EAR` to the ears, `NOSE` and `GILL` to the head slot, and `TAIL` and `WING` to the back slot.
- **Defaults.** Its proper location is "held or floating nearby", and it is not a logical AND of slots (`StdLimb.java:57-58`). Its real wear slot comes from the part (see below).
- **Capacity.** `canWear` (`StdLimb.java:197-223`) allows a limb only while the character's working parts plus worn false limbs of that part are fewer than the racial count `bodyMask()[part]` (lines 207-221). `canWearComplete` gives the messages "This limb looks malformed." (line 241) and "You don't have any empty sockets to wear @x1 on." (line 246).
- **Effect when worn.** `affectCharStats` (`StdLimb.java:180-194`) runs only when the item is not in inventory, held, floating or wielded. If the character has fewer of that part than the racial count, it calls `alterBodypart(part, phyStats().ability())` and so adds the part. Otherwise it calls `setRawWornCode(0)` and unwears the item (lines 189-192).
- **Removal guard.** `okMessage` (`StdLimb.java:82-117`) refuses a TYP_REMOVE of a worn limb while another worn item depends on its slot through `codes.dependency_masks()`, with "You'll need to remove <T-NAMESELF> first." (line 108).
- **Size.** `getSizingDeviation` always returns `FITS` (`StdLimb.java:174-177`).

### 4. StdItemWrapper: a read-only mirror of a live item

- `setWrappedObject` stores the item and copies its phyStats into the wrapper's own `basePhyStats` (`Items/Basic/StdItemWrapper.java:52-60`).
- **Live delegation.** Names, descriptions, display text, value, material, wear state, readable text and tags delegate to the wrapped item (for example `:81-102`, `:145-148`, `:609-656`).
- **Dead setters.** `setName`, `setDisplayText`, `setDescription`, `setMaterial` and similar are empty (`:104-107`, `:117-119`, `:128-130`). `tick` returns `false` (`:225-229`). `okMessage` returns `true` and `executeMsg` does nothing (`:278-286`). Effects, behaviours and scripts are empty collections (`:364-493`). `wearIfPossible` returns `false` (`:536-546`).
- **Snapshot stats.** `phyStats()` returns the snapshot taken at wrap time (`:354-357`). The live names are shown, but stat-based checks see the wrap-time state.
- **Who uses it.** `Abilities/Spells/Spell_PlanarBubble.java` (around lines 262-291) builds a mirror room on another plane. Exits become `StdPortalWrapper`, rideables `StdRideableWrapper`, containers `StdContainerWrapper`, and other items `StdItemWrapper` (line 274). Mobs become `StdMobWrapper`, or `StdRideableWrapper` if they are rideable (lines 288-290). The mirror is rebuilt when a hash changes; the hash combines the viewer's `hashCode`, and the names, display texts and dispositions of the items and mobs in the room (`Spell_PlanarBubble.java:235-257`). `Spell_PlanarDistortion.java:273-276` builds the same kind of wrapper.

### 5. StdMap: an ASCII map generated on READ

**Data model.** `miscText` is a semicolon-separated list of area names (`Items/Basic/StdMap.java:74-84`, parsed at `:218`). `setMiscText` calls `doMapArea` (`:86-91`), which sets the item name to "a map of A, B, and C." and writes the description "Looks like ..." (`:93-116`). The item's `phyStats().level()` is its detail level (see below).

**Pipeline on READ** (`executeMsg`, `:607-693`):

1. **Width.** `cols` is the reader's session wrap, with `0` meaning `254` and anything else meaning `wrap + 2` (`:620-627`).
2. **Collect rooms.** `makeMapRooms` (`:216-240`) collects the rooms of each named area, skipping rooms flagged `SENSE_ROOMUNMAPPABLE` (line 229). `clearTheSkys` (`:190-214`) removes rooms reached through UP or DOWN `GridLocale` rooms.
3. **Place rooms on a grid.** `placeRooms` (`:566-605`) starts from rooms that pass `okToPlace` (`:521-548`). `placeRoom` (`:695-803`) walks the room graph recursively and gives each neighbour a favoured offset: north is `y-1`, east `x+1`, and so on (`:762-771`). An UP or DOWN neighbour takes the first side that has no exit (`:772-797`). When the favoured spot is already taken, the room is deferred if another unplaced room next to it could still claim that spot (`:712-731`); otherwise it goes to `findEmptyCluster` (`:460-511`), which probes rings of positions at spacing `CLUSTERSIZE = 3` (`:445`). Recursion stops silently at depth 500 (`:706`).
4. **Normalise and drop stragglers.** `rebuildGrid` (`:118-188`) shifts coordinates to be non-negative, fills a 2-D array, and removes rooms with no neighbour, repeating the pass recursively while stragglers remain (`:184-185`).
5. **Render.** `finishMapMaking` (`:242-333`) computes `numXSquares = round(floor((width-6)/8))` and `numYSquares = numXSquares/2 + 1` (`:252-253`). Each room cell is 8 characters wide and 6 lines tall: a north wall `---X----` (`:306`, `:311`), side walls with the exit glyphs (`:308`), and a 4-line by 6-column box holding the first 24 characters of the room's display text (`:305-310`). Pages are cut every 9 room columns (`:276`) and every `numYSquares` room rows (`:265`; 5 at the default width).
6. **Exit glyphs.** `dirChar` (`:359-427`) draws `^` for north, `v` south, `>` east, `<` west, `U` up and `D` down. Where there is no exit, the wall character is used: `-` for north and south, `|` for east and west.
7. **Sections.** For large maps, `READ <section> <map>` reads a section. A letter selects a column and digits select a row (`:639-658`). An out-of-range section falls back to `A0` (`:660-665`). The messages are "Section: @x1" (`:669`) and "(@x1) Use 'READ SEC MAPNAME' to read sections ..." (`:682`).

**Detail level.** `phyStats().level()` controls what the map shows:

- Below 1, rooms that are indoors are left out (`:529-530`).
- Below 2, exits that are hidden, invisible or locked by default do not count (`:538-544` and `:556-562`). At 2 or more, they are shown.

**Caching.** `getMyMappedRoom` (`:335-357`) rebuilds the whole map on every READ. The code that would read a cached copy is commented out (`:338-353`). The result is still stored with `Resources.submitResource` (`:355`), but nothing reads it back.

### 6. StdJournal: a named, database-backed board

**Data model.** The journal's name is its database key. Every read and write goes through `CMLib.database()` with `Name()` as the journal (for example `:134`, `:754`, `:815-817`). Entries carry a key, from, to, subject, message, create date and update date, and replies are added with `DBWriteJournalReply` (`:546`). The entry class itself was not read.

**Settings.** `readableText()` holds `KEY=value` pairs, which `getParm` parses with `CMParms.parseEQParms` and caches (`:987-1021`). The recognised keys are `READ, WRITE, REPLY, ADMIN, PRIVATE, MAILBOX, SORTBY, FILTER, EXPIRE, LIMIT, MAXCHARS` (`:979-984`). The bare word `SHORTLIST` in the text switches the list to a compact format (`:829`). The meaning of each key is in the table under "Content worth mining".

**Permissions.** `okMessage` (`:74-160`) vetoes WRITE for anyone who fails the WRITE mask and is neither admin nor a staff member with the JOURNALS flag (`:83-91`). REWRITE (edit or delete) also needs the reader to be the sender or the recipient, or staff (`:145-152`). `canRead` and `canWrite` (`:162-185`) give the same checks to other code.

**Reading** (`executeMsg` TYP_READ, `:324-604`; list and entry logic in `DBRead`, `:807-977`):

- **Arguments** (`:345-357`). A number selects an entry. `NEW` shows only entries newer than the reader's last login (`:336`, `:882`). `ALL` or `OLD` shows everything. (The number handling is wrong; see Problems.)
- **List mode.** Rows are collected newest first (`:863-890`), capped by `JOURNALLIMIT` unless `ALL` is given (`:859-861`). They are printed oldest first (`:892-916`), with `*` marking new entries (`:903-906`) and a `<JRNL "name">` tag around the number (`:909`).
- **Entry mode.** A `FROM / TO / DATE / SUBJ` block and then the body (`:965-972`). A body that starts with `<cmvp>` is run through the web macro filter (`:958-959`).
- **Menu.** After an entry, the prompt is "R)eply E)mail S)top N)ext D)elete T)ransfer C)opy or RETURN:" (built at `:396-429`). N moves on (`:436-452`). E emails the author through `CMLib.smtp().emailOrJournal` (`:456-482`). T and C send the entry to another journal or user through `completeTransfer` (`:187-289`, `:499-523`). D deletes (`:525-536`). R replies, and a reply can be forwarded to a configured journal if the entry was to the reader alone (`REPLYSELF`) or to ALL (`REPLYALL`) (`:553-574`).
- **Pruning.** If `LIMIT` is set, every read deletes the oldest entries beyond that count (`:818-827`).

**Writing** (`:682-761`). A one-line write uses the whole text as the body and the first 40 characters as the subject (`:703-707`). Otherwise the prompts are "Is this a private message (y/N)?" (`:714`), "To whom:" (`:716`), "Enter a subject:" (`:725`) and "Enter your message" (`:735`). For non-staff, a subject that starts with MOTD, MOTM or MOTY has that prefix removed (`:731-734`). `MAXCHARS` truncates the body (`:750-753`). The write calls `notifyPosting` (`:749`) and then "Journal entry added." (`:758`).

**Arrival notice.** When a character enters a room that holds the journal, or looks at or examines the room while carrying the journal, a trailer "@x1 has @x2 new messages." is added (`:773-803`). It is shown once for each change of date and reader, tracked in `lastDateRead` and `lastReadTo`.

### 7. StdLawBook: the law of an area, as a book

**What it points at.** The book stores only an area name in its readable text (`Items/Basic/StdLawBook.java:94`). The chain is `CMLib.map().getArea(name)`, then `CMLib.law().getLegalBehavior(area)` (a `LegalBehavior`), then `B.legalInfo(legalObject)`, which returns a `Law` (`:94-103`). The arrest behaviour holds the law. `resources/help/arc_behaviors.ini:150-156` says the word `custom` in the Arrest behaviour's parameters makes the area's laws changeable through a law book, and `guides/ArchonGuide.html` (GenLawBook section) says the area needs either the Arrest or the Conquerable behaviour for the book to work at all.

**Where the law lives.** `DefaultLawSet` (`Common/DefaultLawSet.java`) keeps lists and maps for crimes, rooms and names, plus a `Properties` object `theLaws` with string keys (`:90-114`). `resetLaw` (`:770-927`) parses the keys into typed fields. The main keys are:

| Key pattern | Holds | Parsed at |
|---|---|---|
| `ACTIVATED` | `FALSE` turns the law off | `DefaultLawSet.java:774` |
| `OFFICERS`, `JUDGE` | Key words that identify officials | `:775`, `:780` |
| `PROPERTYTAX`, `SALESTAX`, `CITTAX`, `TREASURY`, `TAXEVASION` | Tax values, treasury room, evasion crime | `:822-834` |
| `ASSAULT`, `MURDER`, `NUDITY`, `ARMED`, `RESISTINGARREST`, `PROPERTYROB`, `PRISONBREAK`, `TRESPASSING` | The basic crimes | `:839-860` |
| `CRIME<n>` | Keyword crimes, `words;locs;flags;name;sentence;warning` | `:877-887` |
| `BANNED<n>` | Keyword or resource bans, same layout | `:896-905` |
| ability ID, or `$`-prefixed ID or class | Crimes by skill or spell, and illegal effects | `:912-921` |
| `JAIL`, `RELEASEROOM` | Jail and release room lists | `:817-818` |
| `JAIL<n>TIME`, `PAROLE<n>TIME` | Jail and parole times, stored in ticks | `:803-815` |
| `PROTECTED`, `TRESPASSERS` | Victim mask and trespasser mask | `:790-791` |

A value is written with `changeTheLaw` (`StdLawBook.java:235-245`), which calls `setInternalStr` (`DefaultLawSet.java:747-755`) and then `B.updateLaw(area)`, which re-parses the law.

**Pages.** Reading `READ <n> "law book"` opens page `n` (`StdLawBook.java:143-182`). Page 1 prints a fixed text with the area name.

| Page | Method | Content |
|---|---|---|
| 2 | `doOfficersAndJudges` (`:1554-1602`) | Judge and officers found in the area, and the keyword lists |
| 3 | `doVictimsOfCrime` (`:1529-1552`) | Victim mask (`PROTECTED`) |
| 4 | `doJailPolicy` (`:1321-1407`) | Four jail times and the jail rooms |
| 5 | `doParoleAndRelease` (`:1233-1319`) | Four parole times and the release rooms |
| 6 | `doBasicLaw` (`:1157-1231`) | The seven fixed crimes, each with a sentence line |
| 7 | `doTresspassingLaw` (`:1409-1527`) | Trespasser mask and the TRESPASSING crime |
| 8 | `doIllegalInfluence` (`:1034-1155`) | Effects that are crimes to be under (`$`-keys) |
| 9 | `doIllegalSkill` (`:800-921`) | Skills, spells, prayers, chants and songs that are crimes to use |
| 10 | `doIllegalEmotation` (`:612-700`) | Keyword crimes, such as words said or done |
| 11 | `doTaxLaw` (`:923-1032`) | Property, sales and citizen tax percentages, treasury, evasion |
| 12 | `doBannedSubstances` (`:702-798`) | Keyword and resource bans on items obtained |

**Crime record.** Each crime is five strings (`Common/interfaces/Law.java:731-737`): `BIT_CRIMELOCS` (0), `BIT_CRIMEFLAGS` (1), `BIT_CRIMENAME` (2), `BIT_SENTENCE` (3) and `BIT_WARNMSG` (4). A new crime starts as "Name of the crime", with sentence `jail1` (`StdLawBook.java:300-305`).

**Sentences.** The base sentences are `WARNING, THREAT, PAROLE1-4, JAIL1-4, DEATH` (`Law.java:642-653`). Modifiers are `SEPARATE, SKIPTRIAL, DETAIN=, FINE=, NORELEASE, PUNISHCAP=, SHAME=, BANISH=` (`Law.java:673-682`). The editor takes a detention room and time (`StdLawBook.java:446-455`), a fine up to 100000 base-gold value (`:462-463`), and shame and banish days, where 256 means forever (`:474`, `:486`).

**Flags.** The location flags are `home`, `!home`, `indoors` and `!indoors` (`StdLawBook.java:265-268`). The crime flags are `witness`, `!witness`, `!recently`, `combat`, `!combat` and `ignoreNN` / `!ignoreNN` (`:271-287`). They are enforced by `Behaviors/Arrest.java`:

- `witness` / `!witness` (`:2013-2024`). A witness must be in the same room unless `!witness` is given.
- `ignoreNN` (`:2026-2030`). `chance = NN` and the crime is ignored when a d100 roll is at or below it.
- `!ignoreNN` (`:2037-2041`). `chance = 100 - NN`, the same test.
- `combat` / `!combat` (`:2048-2068`).
- `recently` / `!recently` (`:2070-2090`). `recently` needs a previous warrant for the same crime within `600000` ms (ten minutes, line `2074`); `!recently` refuses the crime if there is one.
- Location flags start at `:2111`.

The editor shows the flags as yes or no questions and keeps adjacent `X` and `!X` pairs mutually exclusive (`StdLawBook.java:541-548`, `:579-586`).

**Reading and writing rules.** A player who can see the book can read it. If the area has no legal behaviour the pages "appear blank, and damaged"; if the behaviour has no law for the area, the book says "There is no law here." (`:89-107`). Writing is always vetoed with "You are not allowed to write on @x1. Try reading it." (`:72-75`). Changing the law needs `ABOVELAW` security in the area's first metro room (`:114-116`), or a clan role in the ruling clan that has the `CREATE_LAW` authority (`:117-125`). Reading the book as such a person also sets the law active (`:127-128`).

### 8. StdNavigableBoardable: a sailing ship

**Structure.** The ship is an item that sits in a room, which is its dock (`owner()`). It has its own interior area: `getAreaClassType` returns `StdBoardableShip` (`Items/Basic/StdNavigableBoardable.java:84-87`), and the first room is a `WoodenDeck` locale titled "The Deck" (`:90-95`). Docking changes the owner room (`:1278-1283`, `:1707-1714`). The weight grows by 1200 for each proper room in the ship's area (`:123-129`).

**Defaults.** Base speed is the item's ability, set to 2 (`:77`). The noun is "transport" (`:78`). Verbs are "navigate" and "navigating", the anchor is "anchor" and "lowered" (`:61-69`), and the deck header is "Off the side you see:" (`:79`). `setStat` lets each ship class change the nouns and verbs through `SPECIAL_NOUN_SHIP`, `SPECIAL_VERB_SAIL`, `SPECIAL_VERB_SAILING`, `SPECIAL_HEAD_OFFTHEDECK` and `SPECIAL_TICKS_PER_TURN` (`:1953-1974`).

**Permission.** `securityCheck` (`Items/Basic/StdBoardable.java:602-616`) allows staff with the CMDITEMS flag, anyone if the ship has no owner, the owner or someone with property rights, and a clan member wearing a sailor's cap of the owning clan.

**Speed.** `getMaxSpeed` (`:158-193`) works as follows:

- Start from the ability value (`:161`). On a non-land ship in a room that has an owning area climate, a windy weather type adds 1, and a windy room climate bit adds 1 more. Otherwise a windy room adds 1 on a roll over 50 (`:163-180`).
- If the ship wears out, `speed = round(speed * usesRemaining / 100)`, and the result is 0 below 10 uses (`:183-189`). Otherwise the minimum is 1 (`:190-192`).

**Commands are intercepted, not dispatched.** Ship verbs arrive as messages, and `okMessage` consumes them:

- TYP_COMMAND in the strategic case (`:936-1009`) handles NAVIGATE, RISE, DIVE and STEER.
- TYP_HUH (an unknown command, `CMMsg.java:1164`) and TYP_SKILLFAIL (a skill mistargeted, `CMMsg.java:1270`) run the full table (`:251-869`).
- TYP_COMMANDFAIL with the `F` prefix handles FOLLOW (`:870-934`).
- TYP_HUH on a rider handles TENDER from the deck (`:1052-1131`).

On the TYP_HUH and TYP_SKILLFAIL path, movement verbs (NAVIGATE, RISE, DIVE, STEER) are checked and then re-queued as `METAMSGCOMMAND` on the ship's navigation pseudo-character, with a delay factor of the actor's speed divided by the ship's speed (`:858-866`). On the TYP_COMMAND path, the same verbs start the move at once (`beginNavigate` and `steer`, `:960-1004`). Anchor, tender, raise and lower, and course commands act at once on either path.

**Two navigation scales.** Strategic movement goes from room to room along open exits. Tactical movement, used in ship combat, is a coordinate grid with a facing, where turns are gradual and each step is a maneuver:

- `navTickID` is `TICKID_SPECIALMANEUVER` (24) in tactical mode and `TICKID_AREA` (9) otherwise (`:1260`; `core/interfaces/Tickable.java:100` and `:130`).
- A tactical maneuver is a `MSG_ADVANCE` message with the new coordinates (`:1622-1625`). A turn is one step of `Directions.getGradualDirectionCode` (`:1645`).
- When a tactical step would leave the grid, the ship makes a real room move instead (`:1636-1641`).
- A tactical course may hold up to `speed` directions, and only the last may change direction (`:758-810`).

**Tick.** `tick` (`:1257-1389`) does four things:

1. Area tick (`:1261-1286`). If the ship is not docked at its owner room, it docks there. A tender link is cleared unless both ships are in the same room and neither is in combat (`:1268-1285`).
2. Navigation tick (`:1288-1292`). `ticksSinceMove` and `ticksSinceLastTurn` each increase by one.
3. Following (`:1293-1308`). A followed ship recomputes its course to the target each tick (`getFollowingCourse`, `:1185-1255`), and stops following if no path exists.
4. Course walk (`:1309-1356`). Up to `speed` steps are taken (`:1321`). Each step is gated by the turn timer (`:1325-1327`) and the move timer (`:1328-1330`). The result of `navMove` is CANCEL (clear the course, `:1333-1337`), CONTINUE (take the next course direction, `:1338-1349`) or REPEAT (`:1351-1354`). A steering course keeps its heading through the `COURSE_STEER_MASK` bit (`NavigableItem.java:46`; `:1346`, `:1762`).

With the defaults (`ticksPerMoves = 1`, set to 3 by `GenBalloon.java:71`), the timers allow one room or one turn step per tick. This was traced in the code, not run.

**Strategic move** (`navMove`, `:1584-1745`). A strategic move (`:1670-1733`) builds enter and leave messages for a navigation pseudo-character (`CMLib.tracking().createNavigationMob`, `:1692`). It asks the exit, the leave target, the opposite exit and the enter target for `okMessage` (`:1700-1703`). On success it runs the exit, undocks from the old room, moves the item into the new room and docks there (`:1705-1714`), then calls `haveEveryoneLookOutside` (`:1719`). On failure it says "<S-NAME> can not seem to travel ..." (`:1724`) and clears the course. With no exit it says either "There is no where to ..." or "meanders along the waves" in watery rooms (`:1736-1741`).

**Docking and tender.** TENDER between two ships in the same room (`:272-338`) requires both to be docked (`:307-311`). The two ships then swap gangplanks: each undocks and docks at the other's exit room (`:315-318`). A rider can instead tender a small rideable, which is held in `smallTenderRequests` until RAISE puts it on deck (`:1094-1121`, `:382-439`). LOWER takes such an item off the deck (`:440-497`).

**Other rules.**

- Anchor: a strategic move is refused while the anchor is down (`:710-714`). RAISE and LOWER ANCHOR toggle `anchorDown` (`:498-559`).
- Jump overboard: "overboard" or "water" as the target, from the deck only. Direct damage is `maxState().getHitPoints()/10`, unless the room is watery (`:339-381`, `:369-371`).
- Safety: if the ship is not in a live room, `safetyMove` (`:1825-1851`) moves it to its home port or the first safe room of its area or of any area.
- Too damaged: a course is cancelled with "<S-NAME> is too damaged to move." when the ship has zero speed and fewer than 25 uses (`:1313-1320`).

**Tick drivers outside the item.** Ship combat is driven from `Libraries/MUDFight.java`. `tickAllShips` (`:3699-3716`) ticks every ship with `TICKID_SPECIALMANEUVER` and then `TICKID_SPECIALCOMBAT`. A service started at `MUDFight.java:129` calls it with the standard tick length. `CombatLibrary.java:87` gives `TICKS_PER_SHIP_COMBAT = 4` as "normal 4-second ticks per ship combat round". The exact cadence of that service was not checked.

**Directions.** The compass codes are `NORTH=0, SOUTH=1, EAST=2, WEST=3, UP=4, DOWN=5, GATE=6, NORTHEAST=7, NORTHWEST=8, SOUTHEAST=9, SOUTHWEST=10` (`core/Directions.java:80-106`). The ship code uses both the 7-direction and 11-direction sets.

### 9. The rest of the bucket in brief

- **StdKey** and **StdLimb** have no state beyond the fields described above.
- **StdItemWrapper** holds the wrapped object, its snapshot of stats, and its own container and owner links.
- **StdJournal** keeps last-read tracking (`lastReadTo`, `lastDateRead`) and the parameter cache. **StdLawBook** keeps nothing of its own. **StdMap** has an `oldLevel` field that nothing reads (`StdMap.java:44`). The real data for these three is elsewhere: in the database, in `Law`, and in the room graph.

## Content worth mining

### Ship command vocabulary

| Command (aliases) | Effect | Code |
|---|---|---|
| `NAVIGATE <dir>` (the player-facing verb is set by `SPECIAL_VERB_SAIL`) | One strategic move, or a tactical step forward only | `:638-716`, `:950-981` |
| `STEER <dir>` | Set a persistent heading (steer bit) | `:560-630`, `:982-1004` |
| `COURSE` / `SET COURSE <dir> ...` | Queue a route; tactical courses end with a change of direction | `:717-855` |
| `RISE [UP]` / `DIVE [DOWN]` | Navigate through the UP or DOWN exit | `:631-637`, `:950-956` |
| `RAISE ANCHOR` / `LOWER ANCHOR` | Toggle the anchor, with a room message on each | `:498-559` |
| `TENDER <ship>` | Dock with a docked ship, or ask to be taken aboard | `:272-338`, `:1070-1128` |
| `RAISE <item>` / `LOWER <item>` | Move a tendered item on or off the deck | `:382-497` |
| `JUMP OVERBOARD` / `JUMP` | Leave the deck; fall damage | `:339-381` |
| `FOLLOW <item>` | Track another ship's position | `:870-934` |

Some short messages, quoted from the code:

- "There doesn't look to be anything in that direction." (`:606`, `:683`)
- "The @x1 is @x2, so you won`t be moving anywhere." (`:626`, `:712`)
- "@x1 can't change direction that quickly. You must wait a bit longer." (`:701`)
- "<S-NAME> can not seem to travel @x1." (`:1724`)
- "As there is no where to @x2 @x1, <S-NAME> meanders along the waves." (`:1737`)
- "Jump where? Try JUMP OVERBOARD." (`:376`)
- "The fall <DAMAGES> <T-NAME>!" (`:371`)
- "Your course may not exceed your tactical speed, which is @x1 moves." (`:795`)

### Law book flags and sentences

Basic crimes on pages 6 and 7: `ASSAULT`, `MURDER`, `NUDITY`, `ARMED`, `RESISTINGARREST`, `PROPERTYROB`, `PRISONBREAK`, and `TRESPASSING` on page 7.

| Group | Entries |
|---|---|
| Sentences (`Law.java:642-653`) | `WARNING`, `THREAT`, `PAROLE1`-`PAROLE4`, `JAIL1`-`JAIL4`, `DEATH` |
| Sentence modifiers (`Law.java:673-682`) | `SEPARATE`, `SKIPTRIAL`, `DETAIN=`, `FINE=`, `NORELEASE`, `PUNISHCAP=`, `SHAME=`, `BANISH=` |
| Location flags (`StdLawBook.java:265-268`) | `home`, `!home`, `indoors`, `!indoors` |
| Crime flags (`StdLawBook.java:271-287`) | `witness`, `!witness`, `!recently`, `combat`, `!combat`, `ignoreNN`, `!ignoreNN` |

The lawtoc text (`resources/lawtoc.ini`, 250 lines) gives the help for each page and flag. Its page titles run from "Information" on page 1 to "Banned Substance Law" on page 12.

### Journal settings

| Key | Meaning in code |
|---|---|
| `READ` | Mask for who may read (`:169`, `:337`) |
| `WRITE` | Mask for who may write (`:85`, `:180`) |
| `REPLY` | Mask for who may reply (`:396`) |
| `ADMIN` | Mask for admin rights over the journal (`:333`, `:166`) |
| `PRIVATE` | New posts go only to the writer (`:710-711`) |
| `MAILBOX` | Writers may address users by name (`:713`) |
| `SORTBY` | Starting with `CREAT` sorts by create date, otherwise by update date (`:133`, `:810`) |
| `FILTER` | Restricts which `to` values are visible (`:131`, `:812`) |
| `EXPIRE` | Registers the journal for expiry (`:1014-1015`) |
| `LIMIT` | Maximum entries kept (`:818-827`) |
| `MAXCHARS` | Maximum characters per message (`:750-753`) |
| `SHORTLIST` (bare word) | Compact list format (`:829`) |

### Map glyphs

| Glyph | Meaning |
|---|---|
| `^` | Exit north |
| `v` | Exit south |
| `>` | Exit east |
| `<` | Exit west |
| `U` | Exit up |
| `D` | Exit down |
| `-` / `\|` | Wall where there is no exit (`-` on the north and south sides, `\|` on the east and west) |

### Body parts and wear slots

| Part (index) | Wear slot (`Race.BODY_WEARVECTOR`) |
|---|---|
| ANTENNA (0) | HEAD |
| EYE (1) | EYES |
| EAR (2) | EARS |
| HEAD (3) | HEAD |
| NECK (4) | NECK |
| ARM (5) | ARMS |
| HAND (6) | HANDS |
| TORSO (7) | TORSO |
| LEG (8) | LEGS |
| FOOT (9) | FEET |
| NOSE (10) | HEAD |
| GILL (11) | HEAD |
| MOUTH (12) | MOUTH |
| WAIST (13) | WAIST |
| TAIL (14) | BACK |
| WING (15) | BACK |

Source: `Races/interfaces/Race.java:555-558` and `:605-622`.

## Notable design ideas

- **A vehicle that is also a place.** The ship is an item that lives in a room and has its own interior area. Movement moves the item between rooms and docks it there. The navigation pseudo-character walks the ship through every exit's and room's `okMessage`, so doors, weather and blocking rules apply to ships with no extra code.
- **Commands intercepted at the message layer.** The ship defines its own verbs (`noun_word`, `verb_sail`, and so on) and handles player input through `okMessage`. Ship classes change their wording with `SPECIAL_` stats. The same object can refuse, accept or re-queue a command.
- **Two scales of navigation.** A room graph for strategy, and a coordinate grid with facing and gradual turns for combat. Both are gated by per-tick counters (`ticksSinceMove`, `ticksSinceLastTurn`) rather than real time.
- **A course as a queue with a sentinel.** Course lists end with `-1` (`:812-813`, `:848-849`, `:967-968`), and a steering heading is a flag bit (`COURSE_STEER_MASK`) on the same integer. Small, and easy to port.
- **A map with no stored data.** The map is derived from the area's room graph on READ. Its detail level selects whether hidden exits and indoor rooms appear, so one item serves several levels of knowledge.
- **Sections for big maps.** A letter-and-digit code (`B2`) picks a page of the map. It is an unusual, very compact navigation scheme.
- **Config as text.** A journal's behaviour is set by `KEY=value` text in its readable field, so builders change permissions without code. The law book works the same way with flag tokens such as `!witness` and `ignore75`.
- **A book with no data of its own.** The law book stores only an area name. Reading shows the law, and writing edits it. The same editor code serves every area.
- **Lock and key as a shared random string.** No key identity, no lock object. Any copy of the key with the same name works.
- **Live mirror.** `StdItemWrapper` delegates names and descriptions to the real item and freezes stats at wrap time, which is a clear pattern for "the same thing, seen from elsewhere".

## Problems and cruft

Bugs found by reading. None was run.

1. **Journal entry number taken from the wrong string.** `Items/Basic/StdJournal.java:350` sets `which=CMath.s_int(msg.targetMessage())`, using the whole argument string instead of the token `s`. `Commands/Read.java:97-106` passes everything before the item name as the target text, so `READ 3 NEW JOURNAL` passes `"3 NEW"`. `CMath.s_int` returns 0 for that (`core/CMath.java:2104-2114`), and the list is shown instead of entry 3. Only a lone number works.
2. **Course direction check misses north.** `Items/Basic/StdNavigableBoardable.java:798` tests `otherDir > 0`, but `otherDir` starts at `-1` and `NORTH` is `0`. A first change of direction to north is not caught, and later direction words are accepted. The test at `:779` (`otherDir < 0`) has the same sentinel problem.
3. **`getDirectionFacing` ignores its argument.** `StdNavigableBoardable.java:1560-1565` returns the current facing whenever that is 0 or more. Its callers at `:617`, `:690` and `:1589` therefore assign the facing to itself. Turning still happens later in the turn branch of `navMove` (`:1645-1656`), so the effect is a misleading name more than broken steering. This was traced, not run.
4. **List edited while iterated.** `StdNavigableBoardable.java:1107-1112` removes items from `smallTenderRequests` while iterating over it. `SLinkedList` wraps a `java.util.LinkedList` (`core/collections/SLinkedList.java:26`) and returns a read-only iterator over it (`:427-429`). With three or more pending requests, the next `next()` can throw `ConcurrentModificationException`, and with two it can skip an entry.
5. **Divide by zero on narrow maps.** `StdMap.java:252` gives `numXSquares = round(floor((width-6)/8))`, which is 0 for width under 14. Width is session wrap plus 2 (`:620-627`). `Commands/LineWrap.java:79` accepts any wrap over 10, so a wrap of 11 gives width 13. `Common/DefaultSession.java:1059-1065` also uses the terminal width when it is over 5 and the player has the default wrap. A wrap of 11, or a terminal width of 6 to 11 with the default wrap, therefore gives `numXSquares = 0`, and `grid.length/numXSquares` at `:254` throws `ArithmeticException` on any READ of a map that has rooms. Not run; the arithmetic was checked by hand.
6. **South-west never probed.** `StdMap.java:484-486` sets `xadjust = 1` and then `xadjust = -1` in case 3. The intended probe `(-1, +1)` becomes a duplicate of the west probe, so south-west is never tried.
7. **Page width ignores the width argument.** `StdMap.java:276` cuts pages every nine room columns (`xcoord=x/9`), but the page count uses `numXSquares` (`:252-255`), which depends on width. Nine columns is 72 characters, which fits the default 80-column case only (default wrap 78, `Common/interfaces/PlayerStats.java:53`). Narrower or wider sessions get pages that do not match their width.
8. **Assumes GATE is the last direction.** `StdMap.java:533`, `:578` and `:750` loop to `NUM_DIRECTIONS()-1`. That is `GATE` (code 6) only in the 7-direction set. In the 11-direction set the loops skip `SOUTHWEST` and include `GATE` (`core/Directions.java:258-260`, `:367-377`). `dirChar` (`:397`) skips `GATE` explicitly, so the two code paths disagree.
9. **Cache written, never read.** `StdMap.java:338-355` writes the computed map to `Resources` but reads it back only in commented-out code. Every READ rebuilds the whole map.
10. **Section letter at multiples of 26.** `StdMap.java:680` computes `(char)('A' + map.length % 26 - 1)`, which gives `@` when the column count is a multiple of 26.
11. **Silent recursion cut-off.** `StdMap.java:706` stops `placeRoom` at depth 500 without a log. A very long walk leaves rooms unplaced.
12. **Mail choice always offered.** `StdJournal.java:403-405` has `cmds+="E"` outside the `if` that adds the prompt text, so E is accepted even without a MAILBOX setting. The handler then checks for an email address (`:456-461`), so the damage is limited.
13. **Unreachable "missing entries" message.** `StdJournal.java:918-928` tests whether `selections` holds a non-`StringBuffer`. It never does, so "Use READ ALL [JOURNAL] to see missing entries." can never print.
14. **Admin posts starting "DEL".** `StdJournal.java:690-696`. For admins, a message that starts with "DEL" (any word beginning with those letters) opens the delete-everything confirmation instead of posting.
15. **Identical branches.** `StdJournal.java:1093-1096` return the same string on both sides of the `if`.
16. **Two numberings of the same journal.** `StdJournal.java:1087` numbers entries by create date for `getRawContent`, while the in-game READ uses `SORTBY` (`:133-136`, default by update date). The same page number can name different entries through the two paths. The impact was not checked.
17. **Reading deletes entries.** `StdJournal.java:818-827` deletes the oldest entries beyond `LIMIT` every time the list or an entry is read, because the pruning lives in `DBRead`. A read therefore changes stored data.
18. **Abort message names the wrong flag.** `StdLawBook.java:503` reports `sentenceVarsV.get(size-1)`, the last existing flag, rather than the flag that was aborted. If the list is empty, that is `get(-1)` and throws. The caller's `try/catch` (`:184-186`) logs it.
19. **Mislabelled and duplicated flags.** `StdLawBook.java:277` labels `ignore99` as "1% of the time", and `:278` labels `ignore95` as "5%". `Behaviors/Arrest.java:2028` uses `NN` directly as the chance, so these mean 99% and 95%. The label at `:286` is correct. `ignore50` (`:282`) and `!ignore50` (`:281`) both mean 50%, and `ignore99` appears twice. The editor asks about each entry in turn, and answering yes to two copies appends the flag twice (`:594-602`), so Arrest rolls twice.
20. **WRITE branch in executeMsg is dead.** `StdLawBook.java:190-208` cannot normally run, because `okMessage` vetoes WRITE and REWRITE first (`:72-75`).
21. **Trespass rooms disabled.** `StdLawBook.java:1420-1439` and `:1504-1525` are commented out. Page 7 supports only the mask and the crime.
22. **Tax values unchecked.** `StdLawBook.java:974`, `:982` and `:990` store any number as a tax percentage, with no range check. The effect of a large value was not traced.
23. **Reading changes the law.** `StdLawBook.java:127-128` sets the law active when an authorised person reads the book. Reading has a side effect.
24. **Stat recomputation changes state.** `StdLimb.java:189-192` calls `setRawWornCode(0)` to unwear a false limb when the part count is already full. That changes state during a stat recalculation. The guard for a missing part index (`canWear`, `:203`) is not repeated in `affectCharStats`. Whether a malformed limb can reach that code was not checked.
25. **Stale stats on a mirror.** `StdItemWrapper.java:354-357` returns the wrap-time snapshot from `phyStats()`. `ultimateContainer` (`:670-674`) returns only the immediate container, not the top one.
26. **No-op in speed code.** `StdNavigableBoardable.java:862-863` is `if(speed == 0) speed=0;`.
27. **Redundant branch.** `StdNavigableBoardable.java:1402-1405` assigns `bestM = R2` on both sides.
28. **Boolean `&`.** `StdNavigableBoardable.java:1858` uses `&` between booleans, which does not short-circuit.
29. **No setting for move timer.** `setStat` (`:1953-1974`) has `SPECIAL_TICKS_PER_TURN` but no equivalent for `ticksPerMoves`. Only `GenBalloon.java:71` changes it, in code.
30. **Journal parm cache not volatile.** `StdJournal.java:52` declares `parmCache` without `volatile`, and `getParm` (`:987-998`) uses it in an unsynchronised read. Whether this matters in practice was not checked.

Cruft and multiplayer assumptions:

- Commented-out code: `StdNavigableBoardable.java:1085-1091` (TODO), `:1205-1226` (tactical follow); `StdMap.java:338-353` (cache); `StdLawBook.java:1420-1439` and `:1504-1525` (trespass rooms).
- The sort branch `if(!getSortBy().toUpperCase().startsWith("CREAT"))` is repeated in four places in `StdJournal.java` (`:133`, `:615`, `:637`, `:654`).
- In `StdJournal`, the leading `*` means different things. In the index it marks an entry newer than the reader's last login (`:903-906`). In a single entry it marks an entry the reader sent or received (`:952-955`). The delete check at `:371` reads the same character as "can delete". The overload is confusing.
- Multiplayer assumptions: journal mail, transfer and copy between users (`StdJournal.java:187-289`), reply-all forwarding (`:563-574`), crew and tender between ships (`StdNavigableBoardable.java:1094-1121`), and `announceToAllAboard` for everyone on board.

## Takeaways for a Python SUD

- **Vehicles as objects with a tick.** A Python `Vehicle` could hold `location`, `anchored`, `facing`, `speed`, `course: list[str]` and `ticks_since_move` and `ticks_since_turn`. It would also have one tick method that takes at most one step, as the default gates do here. A single `handle(verb, args) -> bool` method lets the parser try the vehicle first and fall back to the global commands, the same way `okMessage` does (`StdNavigableBoardable.java:936-1009`). Keep the course queue with an explicit end marker, steering as a separate flag rather than a magic bit, the wind and wear speed rule, and the "meanders" message. Drop tender, follow and the tactical grid unless the game needs sea combat.
- **Journals as a plain collection.** `Journal` could be a list of `Entry` dataclasses with `sender`, `recipient`, `subject`, `body`, `created`, `updated` and a `read_by` set. Settings would come from a TOML or dict, not from text inside the object. Drop mail, transfer and copy to other users. Keep the NEW, ALL and number arguments, parsed with a real tokeniser so that `READ 3 NEW` works.
- **Law as typed data, not flag strings.** A `Crime` dataclass could hold `locations`, `witness_required: bool`, `ignore_chance: float`, `requires_combat: bool | None`, `recent_window_s: int`, a `sentence` enum and a `warning` string. That replaces the `!` prefix grammar and the `ignoreNN` tokens, and avoids the mislabelled duplicates above. If the SUD has guards or townsfolk, they can enforce the law from the same data.
- **Maps from coordinates.** If the SUD's rooms carry `(x, y)`, no placement heuristic is needed. Use breadth-first search only for rooms without coordinates, with an explicit stack instead of recursion. Render with the 8 by 6 cell and 24-character name box, which is a good fixed layout for a text terminal. The letter-and-digit section code (`READ MAP B2`) is worth copying for large maps. Keep the detail level as a simple "knowledge" number that controls hidden exits.
- **Keys as shared identifiers.** `lock_id: str` on each door or container and `key.lock_id` on each key, with `uuid4().hex` as the generator. Matching is `==`. A shop that restocks a locked chest without its key can do what `DefaultCoffeeShop.java:762-768` does: give the chest a fresh name and make a key with the same name.
- **Body parts as a table.** A list of parts, each with a wear slot and a count per race, is easy to port (`Race.java:555-622`). A prosthetic fills a missing count. Removal is refused while a dependent item is worn. For a text game this could support injury and prosthetics without much code.
- **A read-only proxy.** A Python proxy class with `__getattr__` that delegates to the live object gives the same result as `StdItemWrapper`. Copy the snapshot only if the stats need to be frozen. This is useful for reflections or illusions, not for ordinary items.

## Coverage

- **Read in full:** `Items/Basic/StdKey.java`, `StdLimb.java`, `StdItemWrapper.java`, `StdMap.java`, `StdJournal.java`, `StdLawBook.java`, `StdNavigableBoardable.java`.
- **StdItem.java, read in part:** the worn-state section (lines 370-570), the readable-text section (598-626), the tick method (742-822), and the method list. Not read: lines 36-369, 571-597, 627-741 and 823-2386, including `okMessage` (1072-1629) and `executeMsg` (1630-1759). The base class is covered in depth by `b172`.
- **Outside the bucket, read in part:** `Behaviors/Arrest.java` (1960-2140, flag evaluation); `Common/interfaces/Law.java` (630-760); `Common/DefaultLawSet.java` (60-240, 728-930); `Races/interfaces/Race.java` (540-625); `Items/Basic/StdBoardable.java` (600-625, `securityCheck`); `Libraries/MUDFight.java` (3680-3775); `Commands/Read.java` (60-125); `Commands/LineWrap.java` (60-95); `Abilities/Spells/Spell_PlanarBubble.java` (140-175, 235-300); `Spell_PlanarDistortion.java` (273-276 only); `Common/DefaultCoffeeShop.java` (740-770); `Abilities/Common/LockSmith.java` (92-112, 160-200, 235); `Exits/StdExit.java` (620-630); `Items/Basic/StdContainer.java` (345-385); `core/Directions.java` (constants); `core/interfaces/Tickable.java` (IDs); `core/threads/ServiceEngine.java` (349-360); `core/collections/SLinkedList.java` and `ReadOnlyIterator.java`; `core/CMath.java` (2104-2114); `Common/interfaces/PlayerStats.java` (53); `resources/lawtoc.ini` (whole file); `resources/help/arc_behaviors.ini` (150-156); `guides/ArchonGuide.html` (GenLawBook section); `resources/skills/clancraft.txt` (line 8).
- **Not read:** `StdSiegableBoardable.java`, `StdPortal.java`, `StdContainer.java`, and `StdBoardable.java` apart from `securityCheck`. The helpers the ship calls from its parents or elsewhere (`amInTacticalMode`, `getTacticalCoords`, `announceToAllAboard`, `haveEveryoneLookOutside`, `getHomePortID`, `unDock`, `dockHere`) are named here but not verified. The journal entry class and the database layer were not read.
- **Not executed:** all findings are from reading. The cadence of the ship-combat service (`MUDFight.java:129`) and the effect of out-of-range tax values were not checked.
