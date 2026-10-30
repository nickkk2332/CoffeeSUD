# Parameter parsing and mini data formats (JSON, TSON, LPC)

> Part of the CoffeeMud NEWOVERVIEW. Bucket: `b007` (Parameter parsing and mini data formats (JSON, TSON, LPC)) · 4 files · 9695 lines of Java.

## Summary

These four files are CoffeeMud's text plumbing. `CMParms` (5,437 lines) splits command lines, finds `KEY=VALUE`, `KEY+n` and `KEY>=n` settings inside free text (this is how behaviours, properties and abilities read their parameter strings), and supplies list and escaping helpers. `MiniJSON`, `MiniTSON` and `MiniLPC` are small hand-written readers and writers for JSON, for a JSON variant whose values carry a type code, and for the LPC literal syntax of the original mudlib. The design ideas are the useful part. Several functions are broken (confirmed by running them, listed below), so a Python game should borrow the syntax and use standard libraries for the parsing itself.

## Key files and types

Paths are relative to `com/planet_ink/coffee_mud/`.

| File or class | Role | Lines |
|---|---|---|
| `core/CMParms.java` | Tokenisers, `getParm*` key finders, the `parseEQParms` pair-list family, escaping and list helpers. All static | 5437 |
| `core/MiniJSON.java` | JSON parser: recursive (`parse`, `parseObject`) and resumable streaming (`getJSONParser`); `JSONObject`; reflection POJO converters | 1916 |
| `core/MiniTSON.java` | `MiniJSON` subclass; type-prefixed values through a `TSONTypeHandler` registry | 1188 |
| `core/MiniLPC.java` | LPC literal reader and writer; `LPCObject` is a `TreeMap` | 1154 |

Use sites, counted by grep: `CMParms.parse` 480 calls, `getParmStr` 364, `getParmBool` 88, the `parseEQParms` family 43, `toKeyValueSlashListString` 42. About 48 files reference `MiniJSON`, among them `Commands/Say.java`, `application/MUDProxy.java` and `Libraries/intermud/cm1/RequestHandler.java`, and a separate copy lives under `com/planet_ink/siplet/support/`. Outside its tests, `MiniTSON` is used only in `Common/DefaultPlayerStats.java` (lines 762 and 1151). `MiniLPC` is used only by `Libraries/intermud/i3/LPCData.java:44`.

## How it works

### Command tokenising

`CMParms.parse` (`core/CMParms.java:788`) splits on whitespace and keeps double-quoted runs together, with backslash escapes: `get "big sword" from chest` gives `get`, `big sword`, `from`, `chest`. `parseAny` (`:992`) splits on one delimiter and can honour escaped delimiters. Mobprog-style "bits" use single quotes for grouping (`getBitWork` `:1293`, `getCleanBits` `:1366`); `cleanBit` (`:1207`) turns inner quotes into backticks. `parseDelimitedParenList` (`:2678`) reads `sword(sharp) shield ring(gold,2)`, counting nested parentheses. `parseDoubleDelimited` (`:2550`) splits a script on an outer and an inner delimiter: with `;` and `=`, `say hi; wave=1 ; bow` gives `[say hi]`, `[wave, 1]`, `[bow]`.

### Keyed lookups: set, add, subtract, compare

The `getParm*` family searches free text for a key. The match is case-insensitive and must start a word: a letter just before the key stops it (`:1515`). A key followed by punctuation is rejected (`hasPunctuation`, `:1475`). A value ends at whitespace, `;`, `:` or a closing quote. The same key can be set or changed. In `HP+5 MP-2 DEX=14`, `getParmPlus` returns 5 for HP and -2 for MP, and `getParmInt(text,"HP",-1)` returns the default because a sign follows the key. That is the documented rule (`:2966`), and it holds in the code. `getParmCompare` (`:1802`) was meant to test `STR>=12`-style comparisons, but it never matches (see Problems).

### Pair lists

`parseEQParmsList` (`:2382`) is a state machine. Keys start with a letter or underscore and end at `=`. Quoted values lose their quotes. An unquoted value may contain spaces, because a later `WORD=` starts a new key (`lastPossibleStart`, `:2513-2517`). So `NAME=Big Bad Joe AGE=3` gives `NAME` = `Big Bad Joe`. `parseStrictEQParms` (`:2153`) stops unquoted values at the first space. `parseLooseParms` (`:1982`) matches abbreviated keys against a fixed list and renames repeats to `NAME2`.

### Escaping and storage

Display helpers (`combine`, `toListString`, `toTightListString`) join with commas or spaces. For storage, `toSafeSemicolonListString` (`:3428`) escapes `;` and `\` with a backslash, and `parseSafeSemicolonList` (`:3461`) reverses it. `toKeyValueSlashListString` (`:4091`) writes `KEY=VALUE/KEY=VALUE/`, and `parseKeyValueSlashListString` (`:4157`) reads it back.

### MiniJSON

- Two parsers. `parse`, `parseObject` and `parseArray` are recursive and stop at `MAX_DEPTH = 500` (`core/MiniJSON.java:48`). `getJSONParser` (`:1177`) is resumable: it keeps an explicit stack of `ParseFrame`s in a `ParseContext` (`:1025-1162`), so input can arrive in chunks. The Intermud CM1 link uses it (`Libraries/intermud/cm1/RequestHandler.java:462`).
- `JSONObject` (`:394`) extends `LinkedHashMap`, so keys keep insertion order. Its checked getters (`getCheckedString` and so on) throw `MJSONException` for a missing key or the wrong type. Integers parse to `Long`, decimals to `Double`, and `null` is a sentinel object, `MiniJSON.NULL` (`:244`).
- `fromPOJOtoJSON` (`:1697`) and `fromJSONtoPOJO` (`:1749`) copy declared fields by name using reflection. There are no annotations and no superclass fields.

### MiniTSON

`MiniTSON` lets a value carry a type code as letters before it: `I5` (Integer), `H4` (Short), `F1.5` (Float), `i[4,5]` (int array), `pa["k",I3]` (Pair), `al[...]` (ArrayList), `hm{...}` (HashMap). Each code is a `TSONTypeHandler` holding a prefix, the Java classes it covers, and parse and serialise converters (`:41-95`). Two static tables map prefix to handler and class to handler (`:99`, `:102`). Plain JSON types take no prefix (`:1074`). Collection types are registered by simple class name after a classpath scan of `core/collections` (`registerCollectionTypes`, `:853-882`). Registration sits in an instance initialiser (`:103-850`), so every `new MiniTSON()` runs it again. Player data stores per-class variable maps as TSON text inside XML (`Common/DefaultPlayerStats.java:762`, `:1151`).

### MiniLPC

`parseLPC` and `parseObject` (`core/MiniLPC.java:883`, `:789`) read LPC literals: mappings `([ "key": value, ])` and arrays `({ a, b })`. Mappings load into `LPCObject`, a `TreeMap` (`:213`), so the keys always come back sorted. Strings use C-style escapes, and `\uXXXX` is meant to decode through Cp1251 (`:624-633`).

## Content worth mining

The useful content is the syntax, not a catalogue.

| Form | Meaning | Source |
|---|---|---|
| `KEY=VALUE`, `KEY="several words"` | set a field | `parseEQParmsList` |
| `KEY+5`, `KEY-2` | adjust a field by a delta | `getParmPlus` |
| `ID(params) ID2` | list of items, each with optional arguments | `parseDelimitedParenList` |
| `say hi; wave=1` | script lines with outer and inner delimiters | `parseDoubleDelimited` |
| `'big sword'` | single-quoted group in a mobprog line | `getCleanBits` |

## Notable design ideas

- One string holds a small typed language, and the same key can be set (`HP=30`) or adjusted (`HP+5`). Builders and scripts can change a field without a separate command.
- The lookups are forgiving: they scan for the key anywhere and ignore the rest of the text. That suits hand-written content and is fragile for anything stricter.
- A resumable JSON parser with an explicit frame stack handles input that arrives in pieces.
- Type-tagged JSON keeps the difference between Integer, Long and Short through a round trip. The handler table makes a new type a single `create` call.
- Parsed parameter maps are cached for 30 seconds in a 500-entry `LimitedTreeMap` (`core/CMParms.java:55-56`).

## Problems and cruft

"Probe" marks items reproduced by compiling the code and running it on small inputs. The rest are by reading.

1. **`getParmStrs` never returns on `KEY+` or `KEY-`.** The `+`/`-` branch adds the default and calls `continue` without advancing `x` (`core/CMParms.java:1610-1616`). The list grows until `OutOfMemoryError` (probe). `getParmStr` returns the default in the same case (`:1519-1520`). The one caller is `Libraries/Achievements.java:4759`.
2. **`getParmBool` throws when the text ends with `=`** (`:3110-3111`, probe: `StringIndexOutOfBoundsException`). It has 88 callers.
3. **`getParmCompare` never matches.** After it finds the operator, it looks for a digit at the operator's own position (`:1835`). Every operator tested failed (probe). The comparator is never written. Its one caller, `Abilities/Properties/Prop_ReqStat.java:108`, passes a one-element array, and the function writes index 1 (`:1843`, `:1867`) on every call. Probe: `ArrayIndexOutOfBoundsException` each time. So `passesMuster` should throw at its first stat check unless the tracking or sneaking early return applies (by reading). The caller also reads index 0, which the function never sets.
4. **JSON escaping mangles non-ASCII.** `toJSONString(String)` encodes UTF-8 bytes, and each byte above 127 comes out as `\uffXX` (`core/MiniJSON.java:346-380`). `café` becomes `cafￃﾩ` (probe). JSON output for say, tell, channel and room-entry events uses this (`Commands/Say.java:78`, `Commands/GTell.java:133`, `MOBS/StdMOB.java:3843`, `Libraries/CMChannels.java:899`).
5. **Surrogate pairs in `\u` escapes** decode to two U+FFFD characters (`core/MiniJSON.java:904-911`, probe).
6. **POJO converters fail on common types.** A Map or Collection field with content throws `NullPointerException` (`:1654`, `:1672`, probe). An enum-valued field recurses until `StackOverflowError` in MiniJSON (probe) and in MiniLPC (`core/MiniLPC.java:943`, probe).
7. **Output that is not valid.** A NaN or infinite double is written as a bare `NaN` (probe: `{"d":NaN}`), and TSON cannot read it back. Unknown types fall back to raw `toString()` (`core/MiniJSON.java:583-585`), so a `LinkedList` inside a TSON object is written as `[p, q]` (probe).
8. **Lenient and inconsistent JSON.** `parseObject` and `parseArray` do not check for trailing text. Only `parse` does (`:1572`, `:1466-1470`, probe). `{"a":1,}` is accepted and `[1,]` is rejected. `1e0` and `1e05` are rejected (`:771`, probe).
9. **TSON type handlers match exact classes.** The lookup is `classToHandler.get(obj.getClass())` (`core/MiniTSON.java:1070`), so a `Room` implementation that is not the registered class skips the `RmR` handler (`:819`). A Proxy stand-in confirmed the exact match. A mixed `Object[]` whose first element is an Integer is tagged `I[...]` (`:1084-1085`) and then fails to parse: `{"mixed":I[I1,"two"]}` gives "Cannot convert String 'two' to Integer" (probe). The `RmR` error text says "Triad" (`:827`).
10. **`toSemicolonListString(Object[])` repeats the first item.** The loop starts at 0 after the first item was already written (`core/CMParms.java:3343`, probe: `a;a;b`).
11. **Slash-separated maps lose data.** `replaceAll("/","\\/")` writes an unescaped `/` (`:4099`), so the value `a/b` reads back as `a` (probe). `Abilities/Languages/GenLanguage.java:253` and `:336` store and read a map this way.
12. **Partial matches count as matches.** `indexOf(byte[],byte[])` (`:4617-4627`, `:4635`) and `compareRange` (`:5176-5183`) return a hit when the array or buffer ends early. `indexOf({1,2},{2,3})` returns 1 (probe).
13. **A null list returns a match.** `startsWith` and its relatives return 0 for a null list (`:5214-5215`), so `startsAnyWith(null,"x")` is true (probe). No callers.
14. **Smaller defects, mostly without callers.** `parseCommandFlags` returns every token, not the matched flags (`:892`). `parseCommasSafe` is the same as `parseCommas` and does not handle the escapes its name implies (`:868-871`, probe). `createDelimiter` indexes a table with `(byte)c`, so a character from 128 up throws `ArrayIndexOutOfBoundsException` (`:93`, `:99`, probe). Its two callers are `Libraries/MUDPercolator.java:71` and `Abilities/Fighter/Fighter_PlanarVeteran.java:106`.
15. **The parse cache leaks.** `parseLooseParms` returns the cached map itself on a hit (`:1994-1995`, probe), so a caller that edits its result changes later results. The cache key is the array's identity hash (`core/collections/LimitedTreeMap.java:66-80`), so fresh arrays almost never hit.
16. **LPC defects.** `\uXXXX` passes hex nybbles as bytes to Cp1251 (`core/MiniLPC.java:627-633`), so `A` gives four control characters (probe). `LPCObject.putString` escapes once when it stores and again on output (`:443-444`, probe), but nothing calls it. A top-level `true`, or a number at the very end of the text, fails to parse (`:756`, probe).
17. **Dead or stale code.** `NEEDNEWKEY` is never returned (`core/MiniJSON.java:77`, `:1499`). The `fields == null` checks cannot fire (`:1704`, `core/MiniLPC.java:950`). A copied message says "Expected {" for a `)` check (`core/MiniLPC.java:687`).
18. **Shared static state.** The punctuation table and the parse caches are static (`core/CMParms.java:49`, `:55-56`). Thread safety is unverified.

## Takeaways for a Python SUD

- Do not port `CMParms` or the mini parsers. Python already has `shlex.split` for quoting and escapes (the job of `parse`), `json` for JSON, and `re` for keyed lookups. Write the lookup as a small function over a token list, with tests.
- Borrow the set-or-delta syntax for builder files and player commands. This regex was run on sample input:

```python
import re
TOKEN = re.compile(r'([A-Za-z_]\w*)([+\-=])("[^"]*"|\S+)')
TOKEN.findall('HP+5 MP-2 DEX=14 NAME="Big Bad Joe"')
# [('HP', '+', '5'), ('MP', '-', '2'), ('DEX', '=', '14'), ('NAME', '=', '"Big Bad Joe"')]
TOKEN.findall('FLAG= HP=30')   # the trailing "FLAG=" is skipped, not an error
```

- For saves, use one dataclass per record type and `json`, with an explicit `"type"` field where needed. A converter dict can replace `TSONTypeHandler`. The instance-initialiser and static-table design is not needed in a single-player game.
- Drop the LPC reader (there is no mudlib to talk to), the Intermud and JSON-proxy outputs, and the XML embedding of player data. These exist for multiplayer and interoperation.
- Keep caching only if profiling shows parsing costs anything. A single-player game parses far less text.
- Write one test per defect in the list above before writing any parser. Those are the cases a Python version must get right.

## Coverage

- Read in full: `core/CMParms.java` (all 5,437 lines), `core/MiniJSON.java` (1,916), `core/MiniTSON.java` (1,188), `core/MiniLPC.java` (1,154).
- Skimmed: none. The `CMParms` list and array helpers (lines 3143 to 5437) were read in full but not traced in depth.
- Context checked by grep and short reads: `Libraries/Achievements.java`, `Abilities/Properties/Prop_ReqStat.java`, `Abilities/Languages/GenLanguage.java`, `Common/DefaultPlayerStats.java`, `Libraries/intermud/cm1/RequestHandler.java`, `Commands/Say.java`, `Commands/GTell.java`, `MOBS/StdMOB.java`, `Libraries/CMChannels.java`, `Libraries/intermud/i3/LPCData.java`, `Libraries/MUDPercolator.java`, `Abilities/Fighter/Fighter_PlanarVeteran.java`, `core/collections/LimitedTreeMap.java`.
- Probes: the four files and their project dependencies were compiled and run on small inputs, outside the repository. Items marked "probe" come from those runs.
- Not verified: the streaming parser's error path (`core/MiniJSON.java:1196`, a `java.lang.Error`) and how `RequestHandler` copes with it; thread safety of the static caches; the cost of the TSON class scan; and TSON handling of a real `StdRoom`, which cannot be constructed outside the server.
