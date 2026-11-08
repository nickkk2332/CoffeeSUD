# Object builder and XML serialisation (CoffeeMaker)

> Part of the CoffeeMud NEWOVERVIEW. Bucket: `b016` (Object builder and XML serialisation (CoffeeMaker)) · 2 files · 8042 lines of Java.

## Summary

CoffeeMaker is the library that converts live game objects to XML and back: items, mobs, rooms, areas, player characters, accounts, and the custom races, classes, abilities, manufacturers and script files they depend on. Its interface, `GenericBuilder`, also defines the "Gen" stat-code system, which reads and writes any object field by a string name, with `+N` and `-N` deltas and `BASE_`, `CURRENT_` and `MAX_` prefixes. The same XML is the database misc-text format for generic objects and the basis of the Export and Import commands and player transfer; about 200 source files call `CMLib.coffeeMaker()`. For a single-player SUD, the useful ideas are the named-field object model, the split between full definitions for builder-made objects and class defaults for hard-coded ones, and the generic field accessor; the XML plumbing and the player, account and transfer code do not need to come across.

## Key files and types

| File or class | Role | Lines |
|---|---|---|
| `com/planet_ink/coffee_mud/Libraries/CoffeeMaker.java` | `CoffeeMaker extends StdLibrary implements GenericBuilder` (line 48). All save and load code, the Gen stat accessors, the catalog hook, custom-object import, and the player and account blocks. | 6719 |
| `com/planet_ink/coffee_mud/Libraries/interfaces/GenericBuilder.java` | The library interface with its Javadoc, and the enums that name the Gen fields: `GenItemCode` (56), `GenMOBCode` (105), `GenMOBBonusFakeStats` (155), `GenItemBonusFakeStats` (198), `GenPhysBonusFakeStats` (214). | 1323 |

Helpers used from outside the bucket (read only by targeted lookup): `XMLManager.java` (`convertXMLtoTag` 710-716, `getValFromPieces` 809-818, `parseOutAngleBrackets` 437), `core/CMClass.java` (`delClass` 1534), `core/CMProps.java` (`getExtraStatCodesHolder` 3932-3941).

Citation convention: line numbers refer to `CoffeeMaker.java` unless another file is named. Other paths are relative to `com/planet_ink/coffee_mud/`.

## How it works

### Save format: one XML block per object

- Entry points: `getItemXML` (2196), `getMobXML` (1924), `getExitXML` (1938), `getRoomXML` (2494), `getAreaXML` (1787), `getPlayerXML` (4949), `getAccountXML` (4694), `getExtraCustomXML` (4714).
- The misc-text writer for any object is `getEnvironmentalMiscTextXML` (214-233). It combines two helpers:
  - `getGenEnvironmentalXML` (577-996) writes the full definition, only when `E.isGeneric()`.
  - `getOrdEnvironmentalXML` (245-371) writes instance fields, only when `fromTop`: room climate and grid (`RCLIM`, `RATMO`, `XGRID`), area parents and children (`PARENTS`, `CHILDREN`), item wear and state (`IWORN`, `ILOC`, `IUSES`, `ILEVL`, `IABLE`), mob level and rejuvenation (`MLEVL`, `MABLE`, `MREJV`), and tags.
- So a standard (hard-coded) object is saved as its class ID plus a few instance fields, and its name and text come from the class. A generic object, which builders create at runtime, carries the whole definition (`NAME`, `DESC`, `DISP`, `PROP`, and so on).
- Load mirrors save. `unpackEnvironmentalMiscTextXML(E, V, fromTop)` (2760-2788) tries the catalog first, then applies the Gen block if generic and the ordinal block if `fromTop`:

```java
if((!(E instanceof Physical))||(!handleCatalogItem((Physical)E, V, fromTop)))
{
	if(E.isGeneric())
		setGenPropertiesFromXML(E,V);
	if(fromTop)
		setOrdPropertiesFromXML(E,V);
```
(2767-2773)

- Escaping is manual. `convertXMLtoTag` writes data raw and emits `<TAG />` for empty data (XMLManager.java:710-716). Free text is passed through `parseOutAngleBrackets` on save (for example 1933, 2206, 2630, 2669) and `restoreAngleBrackets` on load (1699, 2228, 2259). Because misc text is escaped, a `<NAME>` tag inside it appears as `&lt;NAME&gt;`, which is why `getUnknownNameFromXML` searches for the escaped form (2336).

### Rooms, mobs and items

- `getRoomXML` (2494-2734) writes `AROOM` with `ROOMID`, `RAREA`, `RCLAS`, `RDISP`, `RDESC`, `RTEXT`, then `ROOMEXITS` and `ROOMCONTENT`. It takes its inhabitant and item lists before anything else ("do this quick before a tick messes it up!", 2500). When `andIsInDB` is true, those lists come from a fresh copy read through `makeNewRoomContent` (1912-1921: `DBReadRoom` then `DBReadContent`), which is destroyed at the end (2731-2732).
- Exits: each direction with a door or an exit becomes `REXIT` with `XDIRE`, `XDOOR` and `XEXIT` (2539-2563). Grid rooms add cross-exits with `XDIRE` = 256 + n, and `XDATA` holds `x y code suffix;` entries where `code = (out?256:512) | dir` (2564-2601). The decoder reads codes above 255 at 1304-1326.
- Only monsters are written as `RMOB`, and only if they are not following a player (2615). Player characters never appear in room XML.
- Items are written as `RITEM` if they are savable, or always when the room is not read from the database (2653). An item inside a container or on a rideable carries an `IIDEN` identity token, and the item that holds it names that token in `ILOCA` (2658-2664). The token is `""+item`, Java's default `toString()`; StdItem and StdMOB do not override it (checked by grep only).
- In a room with more than 20 items, identical `RITEM` strings are merged with `COUNT=n` (2649, 2688-2702, 2718-2721).
- Load, `unpackRoomFromXML(Area, ...)` (1258-1426): `NEW` room ids come from `getNewRoomID` (1280-1281), a duplicate id is refused (1282-1283), and exits are rebuilt (1292-1373). An exit to a room not yet loaded gets a placeholder door link (1354, 1369). That link is resolved by `getLinkableRoom` (1204-1255) or later by `fixFillingRoomUnlinkedExits` (1428-1451). With no forced area, that second step walks every room in the world for each room loaded (1378-1390).
- `fillRoomContentFromXML` (1089-1202) creates mobs first, then items (expanding `COUNT`, 1144-1172), then links containers by identity token (1175-1186), then riders and followers (1187-1199). ArchonOnly items are skipped without a message (1149-1150).

### Gen restore clears the object first

- `setGenPropertiesFromXML` (4119-4691) validates only one thing before it changes anything: a MOB must have a `GENDER` tag (4128-4132). It then deletes all abilities and inventory (4136-4137), shop stock (4138-4148), deity blessings, curses and powers (4149-4157), effects (4163), behaviours and scripts (4168-4169), and rebuilds from the XML.
- Order inside it: `setEnvProperties` (4201) runs first, then the type blocks: `Exit` (4206), `Item` (4224), `Wand` (4378), `DeadBody` (4453), `MOB` (4514). The MOB block contains the Banker, PostOffice, Librarian, Auctioneer, CraftBroker and Deity blocks (4526-4676).

### Generic stat accessor (the Gen editing API)

- `getAnyGenStat` (3352) and `setAnyGenStat` (3803) take any `Modifiable` and a field name. They try, in order: the object's own stat list; the name with `CURRENT_`, `BASE_` or `MAX_` removed (`getFinalStatName`, 3281-3294); Physical base stats; the fake Physical stats (`GenPhysBonusFakeStats`); MOB character, state and player stats; the account; the MOB and item Gen codes; the Area stats.
- Deltas: when `supportPlusMinusPrefix` is true and the value starts with `+` or `-`, the new value is the old numeric value plus or minus the number (3805-3829). So `setAnyGenStat(obj, "LEVEL", "+5", true)` adds 5 to the level.
- Fake stats are computed or routed to services. Examples: `CLAN` and `CLANROLE` (3615-3633); `ALIGNMENTDESC`, built from faction range names (3703-3733); and an Area's `DEITY`, the deity with the highest piety, counted only if its piety exceeds `CMath.mul(pop, .20)` (3774-3789).
- Name matching: `getGenItemCodeNum` (5810-5823) and `getGenMobCodeNum` (6038-6050) try an exact match, then any code whose name is a prefix of the input. So `ARMORCLASS` resolves to `ARMOR`.
- `isAnyGenStat` (3297) answers whether a name is valid for an object before anything is written.

### Respawn of generic mobs

- `resetGenMOB` (77-93) runs when a generic mob is reborn. It re-reads misc text (`getGenMOBTextUnpacked`, 57-74, which can fetch a `%DBID>` reference from the database), applies the XML, restores hit points, mana and movement from the leveler, sets wimpy to 10 percent of current hit points (91), and sets experience to the level-1 threshold plus 500 (92).

### Type-specific tags

| Object type | Extra tags (writer lines) |
|---|---|
| Container | `CAPA`, `CONT`, `OPENTK` (617-620) |
| Wand | `MAXUSE`, `ENCHTYPE`, `SPELL` (705-714) |
| Book | `MAXPG`, `MAXCHPG` (721-725) |
| Rideable | `RIDET`, `RIDEC`, `PUTSTR`, `MOUNTSTR`, `DISMOUNTSTR`, `STATESTR`, `STATESUBJSTR`, `RIDERSTR` (727-737) |
| Food, LiquidHolder, Drink | `CAPA2` and `BITE` (745-749); `CAPA2`, `REMAN`, `LTYPE` (751-756); `DRINK` (758-761) |
| Weapon, Armor | `TYPE`, `CLASS`, `MINR`, `MAXR` (763-769); `LAYR`, `LAYA` (778-782) |
| Electronics, Coins | `POWC`, `POWT`, `POWR`, `EACT` (641-647); `CRNC`, `DENOM` (636-640) |
| Ship parts | `SSTHRUST`, `SSFEFF`, `SSNTHRUST`, `SSCONST`, `SSAPORTS`, `SSPDIRS`, `SSMTYPES` (669-688) |
| Deity | `CLEREQ`, `WORREQ`, `CLERIT`, `WORRIT`, `CLERSIT`, `WORRSIT`, `CLERPOW`, `SVCRIT`; blocks `BLESSINGS`, `CURSES`, `POWERS` (908-959) |
| Shopkeeper | `SELLCD`, `SELLIMSK`, `SIVTYP`; `STORE` rows of `SHITEM` (960-979) |
| Banker, PostOffice, Librarian, Auctioneer, CraftBroker | `BANK`, `COININT`, `ITEMINT`, `LOANINT` (864-868); `POSTCHAIN` to `POSTHELD` (871-876); `LIBRCHAIN` to `POSTCMASK` (880-888); `AUCHOUSE` to `MINADAYS` (892-899); `BROCHAIN` to `COMMPCT` (903-906) |
| Dead body (corpse) | `GENDER`, `MRACE`, `MDNAME`, `MDDESC`, `MKNAME`, `MTOD`, `MKPLAY`, `MHASH`, `MDLMSG`, `MBREAL`, `MPLAYR`, `MPKILL`, `MOBS`, `KLTOOL` (796-837) |

### Corpses and player characters

- A corpse's saved mob is written with `getMobXML` (824), the minimal form: class, level, ability, rejuvenation and misc text. The killer's weapon is stored under `KLTOOL` (829-837).
- `getPlayerXML` (4949-5065) writes `PLAYER` with identity and stat tags (`CLASSID`, `NAME`, `PASS`, `CLASS`, `RACE`, `GEND`, three-letter stat tags, `HIT`, `LVL`, `MANA`, `MOVE`, `EXP`, `GOLD`, `ROID`), the player-stat XML in `PFIL` (5023), and the common blocks `ABLTYS`, `SCRPTS`, `INVEN`, `EXTRAINV`, `FACTIONS` and `FOLLOWERS` (5027-5056). `ROID` holds "start room||current room" (5007).
- `EXTRAINV` saves items a player owns that lie in rooms, with the room id and an expiry value (447-495; `IROOM ID=... EXPIRE=...` at 489). On load, an item whose room is not found stays only in the player's extra-item collection (531, 548-557).
- Followers are saved as `FOLLOWER` only when they are monsters and not possessed (5046).

### Catalog templates

- `handleCatalogItem` (3210-3234): for a generic object that is cataloged, the saved misc text is replaced by the catalog object's text (3227), usage is counted (3228), and the phystats from the save are still applied (3212). A catalog object flagged `IS_CATALOGED` is refused with a log message (3220-3221). The catalog is exported from `Commands/Export.java` (510, 746).

### Custom objects and files

- `getExtraCustomXML` (4714-4754) writes a `CUSTOM` block with the racial parameters, class parameters, ability XML and manufacturer XML of every generic object in the export, and a `FILES` block with the text of each external script (4737-4752).
- `importCustomObjects` (4791-4929): an unknown custom race, class or ability is added and written to the database. A standard class with the same id is overridden only when `noDelete` is false (4819-4826). For abilities, the existing class is removed before the new one is added (4893-4894). With a session attached, the import asks "(Y/n)" (4810, 4890).
- `importCustomFiles` (4756-4789) moves paths under the `::` resource root (4769-4774) and skips or confirms existing files.

### Accounts, transfer and passwords

- `getAccountXML` (4694-4712) writes `NAME`, `PASS`, `AXML` and a `PLAYERS` list, so the password string is part of the export.
- `getPlayerTransferXML` (4933-4946) wraps one `PLAYER` with the custom objects and strips the `PASS` element with a regular expression (4945). The interface Javadoc says "base-64 compressed" (GenericBuilder.java:809-815), but the method returns plain XML. The encoding happens in the callers (`Libraries/CMProtocols.java:102, 631, 682`).

### Areas, copies and auto properties

- `getAreaXML` (1787-1830) sets the area to FROZEN while it writes (1793-1794), writes `ACLAS`, `ANAME`, `ADESC`, `ACLIM`, `ASUBS`, `ATECH`, `ADATA` and `AROOMS`, and restores the state (1828).
- `unpackAreaFromXML` (1671-1723) creates the area and its rooms, saving them when `savable` is true (1686-1691, 1714-1717). `unpackAreaObjectFromXML` (1737-1773) builds an in-memory area with no database row and no map entry.
- `copyArea` (6329-6399) does not use XML. It copies the area and each room with `copyOf`, clears sky, grid and doors (6346-6351), assigns new ids (6352), keeps an old-to-new id map, and re-links exits in a second pass (6368-6395).
- CMProps-driven area properties: `AUTOWEATHERPARMS` adds a WeatherAffects behaviour (1594-1605); `AUTOAREAPROPS` is a semicolon list of behaviours or effects, each with optional parameters in parentheses (1606-1644); `DEFAULTPARENTAREA` links a new area to a parent (1646-1667).

### Numeric and flag encodings

- Phystats: 11 pipe-separated values in this order: ability, armor, attackAdjustment, damage, disposition, level, rejuv, speed, weight, height, sensesMask (`getPhyStatsStr` 5490-5503; read by `setPhyStats` 5578-5623).
- Char state: six values, fatigue, hitPoints, hunger, mana, movement, thirst (5506-5514; read by position at 5562-5575). Char stats: every code in order (5516-5523, 5553-5559).
- Special environment flags (`getSpecialEnvFlags` 96-132, `setSpecialEnvFlags` 135-171): 1 droppable, 2 gettable, 4 readable, 8 removable, 16 marks the new style, 32 has door, 64 has lock, 128 defaults closed, 256 defaults locked. A set bit means "allowed", the inverse of the `SENSE_ITEM*` bits it is built from (102-109).
- Coded abilities come in two formats. The `ABILITYID(parms)[settings]` syntax (GenericBuilder.java:1289-1297) is parsed by `getCodedEffects` (6462-6566). The flat `ID;param;ID2;param` list is written by `packCodedSpellsOrBehaviors` (6569-6623) and read by `getCodedSpellsOrBehaviors` (6626-6682).
- Factions: `FACTIONS` with `FCTN ID="..."` children (6402-6430); non-player factions are saved only if savable (6414-6419).

### Shops and stock

- `populateShops` (3105-3208) empties the shelves (3122), then reads each `SHITEM` row: class and type (3139-3154), misc text, then `addStoreInventory(item, count, price)` (3192), then destroys its temporary object (3193). The default price code is -1 (3144). Stock rows hold class, type, count, price code and data (969-976).

### Ammunition bundles

- `makeAmmunition` (5771-5808) builds a `GenAmmunition` bundle. Its name depends on the count: more than 9 gives "a stack of N plurals", more than 5 "a bunch of plurals", more than 1 "several plurals", otherwise a singular name. Weight equals the count (5804).

## Content worth mining

- Gen field names (GenericBuilder.java:56-186): 25 item codes (`CLASS`, `USES`, `LEVEL`, `ABILITY`, `NAME`, `DISPLAY`, `DESCRIPTION`, `SECRET`, `PROPERWORN`, `WORNAND`, `BASEGOLD`, `ISREADABLE`, `ISGETTABLE`, `ISDROPPABLE`, `ISREMOVABLE`, `MATERIAL`, `AFFBEHAV`, `DISPOSITION`, `WEIGHT`, `ARMOR`, `DAMAGE`, `ATTACK`, `READABLETEXT`, `IMG`, `TAGS`), 24 mob codes, and 30 mob fake stats. Together they are the complete editable surface of a builder object.
- Creature-kind tags (3521-3555): the `OBJATTRIB` fake stat builds a quoted tag list for a mob, from `SHOP`, `BANK`, `BANKER`, `POSTOFFICE`, `LIBRARIAN`, `LIBRARY`, `STORE`, `UNDEAD`, `ANIMAL`, `CREATURE`, `PLANT`, `INSECT`, `OUTSIDER`, `VERMIN`, `FISH`, `GOLEM`, `MARINE` and `AGGRESSIVE`. Most are set by `CMLib.flags()` tests; the shop tags are set by the mob's class. This is a ready-made set of creature-kind filters.
- Ammunition phrasing (5779-5799): "a stack of N <plural>" above 9, "a bunch of <plural>" above 5, "several <plural>" above 1, and "sit here" or "sits here" for the room text.
- Player-facing strings: "External resource '@x1' found, import (Y/n)?" (4783); "Custom Race '@x1' found which would override your standard race. Import this custom race anyway (Y/n)?" (4824); "You may not have more than one spell when one of the spells parameters is a spell id or a ; character." (6612); default names "Unknown" (2326, 2329); "nothing" for an empty shop goods list (3701); "irrelevant" for an alignment description with no loaded faction (3731).
- Alignment words (3703-3733): the range names of the inclination and alignment factions, read only when the Chaotic or Evil alignment is loaded (3709, 3719).

## Notable design ideas

- Standard versus generic objects (214-233, 2767-2773): hard-coded classes are referred to by ID, and only builder-made objects carry their own definition. A code change to a class reaches every standard instance, while builder edits stay with the object they were made on.
- One string-keyed accessor for every object (3352, 3803), with a delta form (3805-3829). The builder commands use it too: `Commands/Modify.java` makes 19 calls into the library and `Libraries/CMGenEditor.java` makes 16. Because the editor reaches fields by name, new Gen codes may need no new editor code (not checked).
- Export deduplication (1950-1987, 2153-2187, 2649-2721): objects are keyed by name plus display text, identical copies are dropped, and room stacks are written as `COUNT`. A SUD can store stacks the same way.
- Catalog templates with usage counts (3210-3234): unique items share one text template and are counted, so the template can be updated in one place.
- Two-phase room linking (1354, 1428-1451): a room saves a placeholder link `{{#roomid#}}` and the other room fills it in when it loads, so rooms can load in any order.
- Cross-exits in one integer and one string (2564-2601, 1304-1326): grid rooms encode many destinations in one `REXIT` per target.
- Area deity by population share (3774-3789): the top deity wins only if its piety exceeds 20 percent of the area population. Factions emerge from population-weighted scores rather than set by hand.
- Corpses keep a snapshot of the dead mob (824-828, 4477-4481), restored as a saved mob when the corpse is loaded.

## Problems and cruft

Bugs found by reading (not run):

1. Contributor mask is lost on reload. `POSTCMASK` is written (888), but `LIBRCMASK` is read (4556) and nothing writes it, so every generic Librarian comes back with an empty mask. The setter is reached only for generic objects (2770-2771).
2. Silent truncation: `getCodedSpellsOrBehaviors(PhysicalAgent)` returns as soon as one effect or behaviour has a `;` in its text (6439-6440, 6450-6451). Entries collected before it, and all entries after it, are dropped with no message. The packer refuses the same case with an exception (6610-6612), and the reader does not.
3. `packCodedSpellsOrBehaviors` throws `StringIndexOutOfBoundsException` when a parameter ends in a backslash. The test at 6599 checks `i<txt.length()`, but 6601 reads `charAt(i+1)`.
4. Escape handling in `getCodedEffects` (6462-6566): inside parameters, an escaped `(` loses its backslash and the loop then skips the next character, so the paren is dropped (6502-6505). Escaped `)` and `]` are not recognised and end the group early (6507, 6525). Traced by reading only.
5. `getUnknownTypeFromXML` (2310) calls `substring(0,20)` on the trimmed input, so any XML shorter than 20 characters throws. Its callers are 2327 and 2367.
6. `getRoomMobs` (2098-2100) uses the result of `makeNewRoomContent` without a null check; that result is null for a room with no database row (1917-1920). `getRoomItems` checks for null (2448-2449). `getRoomXML` with `andIsInDB` (2502, 2731-2732) has the same hazard.
7. The ability re-import (1520-1524) calls `CMClass.delClass(ABILITY, getRawAbility(ID))` only when the id does not already exist, so it passes null, which `delClass` ignores (CMClass.java:1534-1535). The line never removes anything. The import path at 4893 does remove the old class.
8. Dead code: 825-826 sits inside `if(!recurCheck)` but tests `recurCheck`, which is always false at that point.
9. The corpse restore swallows every exception (the empty `catch` at 4503-4505), so a partly restored corpse is accepted silently.
10. Some saves change game state: `getPlayerExtraInventory` calls `updateCatalogIntegrity` (479), `getGenEnvironmentalXML` clears and re-sets a mob's money (849-853), and `getPlayerXML` rewrites tattoo names (4969-4970). Room sense flags are changed during export and restored in a `finally` (267-278), which is safe.
11. Vestigial duplicate matching: `getUniqueMobsXML` (1973-1986, 1995-2005) and `getUniqueItemXML` (2165-2185) temporarily copy height, weight and gender onto the duplicate, but the match tests (1979-1982, 2167-2171) never read those fields.
12. Items with empty display text are never exported: the check at 2151 gates the write at 2189, with no message.
13. Recipe pages: `NUMRECIPES` is read at 4350, but the explicit save block (694-700) does not write it. The generic stat loop would write it only for configured extension codes; by default it writes nothing (CMProps.java:3935-3936 returns null, and StdItem.java:2346-2349 uses the full length). The page count appears to persist only through `IUSES`, which is `usesRemaining` (GenRecipe.java:445-448). Unverified in play.
14. Escaping gaps: `CLAN ROLE` (855, 5020), the `IROOM` `EXPIRE=` attribute (489) and the `FILE NAME` attribute (4746) are built without escaping, and `FILE` bodies are inserted raw (4747), so a script containing `</FILE>` would break the block. Whether the XML parser tolerates these was not checked.
15. `setPhyStats` (5582) allocates 11 slots. A twelfth field throws `ArrayIndexOutOfBoundsException`, and the catch at 5593-5596 hits the same error.
16. Account import dereferences tags that may be missing: `xmlPiece.value()` (5221) and `playersPiece.contents()` (5225).
17. `fixFillingRoomUnlinkedExits` walks every room in the world for each room loaded (1378-1390) and ignores `NoSuchElementException` (1404-1406).
18. Stale log names: "getPropertiesStr" (218) and "setPropertiesStr" (2741) are old method names.
19. Prefix matching for stat codes (5817-5821, 6044-6048) accepts any name that starts with a code name, so an unrelated typo can write a real field.
20. Ammunition singular: `ammo.endsWith("s")` strips a final s before pluralising (5777-5778), so "glass" becomes "glas".

Design cruft:

- Three overlapping paths carry the same fields: explicit tags (for example `LIBROVERCHG`, 881), the Gen code table used by the editor, and the extension stat loop (5311-5313). They drift apart; item 1 is one result.
- Multiplayer and account concerns are mixed into the object format: passwords in account and player XML (4694-4712, 4987), the MPCP transfer (4931-4946), session prompts during import (4756-4929), possession checks (5046) and extra inventory for logged-off players (447-495). A single-player game needs none of them.
- Tag reuse: `CAPA2` means nourishment for Food (747) and capacity for LiquidHolder (753). An object that is both would clash.

## Takeaways for a Python SUD

1. Do not port the XML. Use dataclasses with `to_dict()` and `from_dict()`, stored as JSON or TOML for content and SQLite or JSON for world state. The escape helpers, the tag-presence checks and the log-and-skip load pattern go away.
2. Keep the prototype and instance split. Content files define prototypes by id (the role of `CMClass.classID`), and an instance stores only its differences from the prototype (the role of `isGeneric()`, 232-233). A builder-made object is a prototype plus overrides, and a hard-coded one is just the id.
3. Copy the field table and accessor as a registry. Each field has a name, a type and a getter and setter, and `get_field(obj, name)` and `set_field(obj, name, raw)` accept `+N` and `-N` deltas (3805-3829). Drop the prefix matching (item 19). A builder command comes almost for free.
4. Flags: use `enum.Flag` for dispositions and senses, which the code builds as powers of two (5992, 6232). Accept comma or space separated names on input (5983-5986), and keep the printed names in one tuple.
5. Replace coded ability strings with a list of `(ability_id, params)` tuples. The current format needs escape rules that are not implemented correctly (items 2-4).
6. Stacks: store `(prototype_id, state, count)` rows, the Python form of `COUNT` (2718-2721). Identity for containers can be a real id assigned at creation, so no `toString()` tokens are needed.
7. Rooms: load all rooms first, storing exits as ids, then link them in a second pass. That avoids the placeholder link (1354) and the whole-world scan (1378-1390).
8. Expiry: `EXPIRE` (489) is an absolute timestamp. Store game time or a turn number and compare on load, depending on whether the game is real-time or turn-based.
9. Keep the population-weighted faction rule (3774-3789) as a design option. A single player's faction or deity standing could be computed from the player's own actions.
10. Drop for a single player: the MPCP transfer and account blocks (4694-4712, 4931-4946), extra inventory for logged-off players (447-495), follower possession checks (5046), the corpse MOB snapshot (824) unless a corpse needs an owner, and import prompts (4756-4929).
11. Keep the respawn idea (`resetGenMOB`, 77-93: reapply a template and set hit points and experience from level), the ammunition phrasing (5779-5799) and the creature-kind tags (3521-3555).
12. Keep saving pure: `save()` returns data and never mutates objects. Then add a round-trip test (save, load, compare). Item 1 is the kind of bug such a test would catch at once.

## Coverage

- Read in full: `com/planet_ink/coffee_mud/Libraries/CoffeeMaker.java` (all 6719 lines, in 12 ranges of about 600 lines) and `com/planet_ink/coffee_mud/Libraries/interfaces/GenericBuilder.java` (all 1323 lines).
- Checked by targeted reads or greps only: `Libraries/XMLManager.java` (437, 710-716, 809-818), `core/CMClass.java` (1534-1535), `core/CMProps.java` (3932-3941), `MOBS/StdMOB.java` (addBehavior and addNonUninvokableEffect dedupe; getSaveStatIndex 6247-6250; compressMiscText 2035), `Items/Basic/StdItem.java` (2346), `Items/Basic/GenRecipe.java` (445-456), `MOBS/GenLibrarian.java` (115-117, 151, 204), `MOBS/GenShopkeeper.java` (142), `MOBS/interfaces/Librarian.java` (305-316), `Libraries/CMProtocols.java` (102, 631, 682), and the call sites in `Commands/Export.java` and `Commands/Import.java`.
- Not read: the database functions called from here (`DBReadRoom`, `DBReadContent`, `DBUpdateRoom` and others), `XMLManager.parseAllXML` (so the parser's tolerance of unescaped attributes is unverified), `Common/DefaultCoffeeShop.java` (the shelf copy semantics of `addStoreInventory` at 325 were not confirmed), `Libraries/CMCatalog.java`, and the Gen classes beyond the grep hits.
- Method: every finding above comes from reading the code. None was run. Items 1, 3, 4, 5 and 6 in the cruft list are the strongest candidates for a unit test.
