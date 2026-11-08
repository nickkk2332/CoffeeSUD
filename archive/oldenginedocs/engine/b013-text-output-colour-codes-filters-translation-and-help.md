# Text output: colour codes, filters, translation and help

> Part of the CoffeeMud NEWOVERVIEW. Bucket: `b013` (Text output: colour codes, filters, translation and help) · 10 files · 7737 lines of Java.

## Summary

Most text a player reads passes through this bucket. `CoffeeFilter` turns an outgoing message into client text: it expands the `^` colour language, resolves pronoun tags for the viewer, applies the `(s)` and `a(n)` grammar markers, word-wraps to the session width and converts MXP or MSP markup for clients that understand it. `Brown` holds the colour tables (16 basic colours, 27 semantic colour roles, a 232-entry 256-colour palette) and the per-player overrides. `DirtyLanguage` is a regex-style rule engine that rewrites typed commands before the parser sees them; despite its name it is not a profanity filter, and its shipped English translation layer is empty. `MUDHelp` runs the help system: `.ini` files become property tables, a long fallback cascade finds an entry, and virtual pages are generated from game data. For a single-player text game the colour-role model, the perspective-aware message templates and the help-as-data layout are the parts worth reusing; the Telnet, MXP and multi-session plumbing is not.

## Key files and types

| File or class | Role | Lines |
|---|---|---|
| `com/planet_ink/coffee_mud/Libraries/CoffeeFilter.java` | `TelnetFilter` implementation. Output pipeline (`fullOutFilter`, `colorOnlyFilter`, `mxpSafetyFilter`, `wrapOnlyFilter`), `^` code expansion in `convertEscape`, pronoun and grammar markers, input cleaning | 2087 |
| `com/planet_ink/coffee_mud/Libraries/MUDHelp.java` | `HelpLibrary` implementation. Loads `resources/help/*.ini`, runs the lookup cascade (`getHelpText`), virtual pages (`fixHelp`), generated ability, recipe, deity and script help, in-game help editing | 2104 |
| `com/planet_ink/coffee_mud/Libraries/DirtyLanguage.java` | `LanguageLibrary` implementation. Rule-file engine for command pre-processing, item parsers, session translation (`L()`), locale selection. Name is historical | 1049 |
| `com/planet_ink/coffee_mud/Libraries/Brown.java` | `ColorLibrary` implementation. Code-to-ANSI and code-to-HTML tables, `COLORSCHEME` and per-player overrides, 256-colour palette from `resources/skills/colors.txt` | 955 |
| `com/planet_ink/coffee_mud/Libraries/interfaces/ColorLibrary.java` | `Color` enum (basic colours, attributes, backgrounds with ANSI, HTML and code letters), `SpecialColor` roles, `ColorState`, `Color256`, and a 256-entry HTML palette (`html256`) | 555 |
| `com/planet_ink/coffee_mud/Libraries/interfaces/HelpLibrary.java` | Help API, `HelpSection` and `HelpMatchType` enums | 294 |
| `com/planet_ink/coffee_mud/Libraries/interfaces/TelnetFilter.java` | Filter API. Its `Pronoun` enum lists the tag suffixes | 200 |
| `com/planet_ink/coffee_mud/Common/DefaultFormatting.java` | Per-session formatting record (wrap, colour state, marked colours). No references outside its own files | 169 |
| `com/planet_ink/coffee_mud/Libraries/interfaces/LanguageLibrary.java` | Translation and parser API (`preCommandParser`, item parsers, `sessionTranslation`, `finalTranslation`, `setLocale`) | 164 |
| `com/planet_ink/coffee_mud/Common/interfaces/Formatting.java` | Interface for `DefaultFormatting`. No references outside its own files | 160 |

Paths in this chapter are relative to `com/planet_ink/coffee_mud/` unless they start with `resources/`, `coffeemud.ini` or `guides/`, which are relative to the CoffeeMud folder.

Call sites outside this bucket:

- `Common/DefaultSession.java`: `getWrap` (1059-1066), `onlyPrint` calls `finalTranslation` (1426-1436), `wraplessPrintln` (1624-1628), `applyFilters` picks `fullOutFilter` (1675-1695), `getColorCodes` builds the per-player table (1788-1811), `simpleInFilter` on input (2662, 2743), `preCommandParser` on each command (3670).
- `Commands/Help.java:49-108` is the player `HELP` command. `Commands/ColorSet.java:238-251` is the in-game palette editor.
- `WebMacros/StdWebMacro.java:123, 246, 365, 376` uses the HTML tables for web output.
- `core/CMLib.java:106, 117, 132, 139, 590-592` defines the library slots (`HELP`, `TELNET`, `COLOR`, `LANGUAGE`; `coffeeFilter()` returns the `TELNET` slot).
- `application/VFShell.java:1138, 1173, 1181, 1241` constructs `Brown`, `MUDHelp`, `DirtyLanguage` and `CoffeeFilter` at boot.
- `core/CMProps.java:159-168` (`ESC0`..`ESC9`), `197` (`COLORSCHEME`), `3012` (`clearLookups`).
- `Common/interfaces/Session.java:1075-1105` defines the client mode flags (`TELNET_MSP` 90, `TELNET_MXP` 91, `TELNET_ANSI16` 252, `TELNET_ANSI` 253, `TELNET_ANSI256` 254).

## How it works

### 1. The output pipeline

Output is synchronous. No tick is involved, and nothing is queued for later.

1. A message to a player goes to `DefaultSession.applyFilters` (`Common/DefaultSession.java:1675-1695`), which calls `CoffeeFilter.fullOutFilter(session, mob, source, target, tool, msg, noWrap)`. Other entry points: `colorOnlyFilter` for prefixes and boxes (`DefaultSession.java:1389, 1649, 1663`), `mxpSafetyFilter` for raw text (`DefaultSession.java:1541`), and `wraplessPrintln` for help bodies (`DefaultSession.java:1624-1628`).
2. `DefaultSession.onlyPrint` (`DefaultSession.java:1426`) runs `CMLib.lang().finalTranslation(msg)` immediately before the socket write. It then drops an exact repeat of the previous line (`DefaultSession.java:1430-1435`).
3. Per-session colour state (`currentColor`, `lastColor`, marked colours) is initialised at `DefaultSession.java:588-589` and changed by the filter as it goes.

### 2. Colour codes

#### 2.1 The code language

A colour is `^` followed by one character. Upper case is the dim version and lower case is the bold or bright version. This is the basic mapping from `Libraries/interfaces/ColorLibrary.java:53-82` (ANSI sequences shown as `ESC[...m`):

| Code | Colour | ANSI | Code | Colour | ANSI |
|---|---|---|---|---|---|
| `^w` | WHITE | `ESC[1;37m` | `^W` | GREY (normal) | `ESC[0;37m` |
| `^k` | DARKGREY | `ESC[1;30m` | `^K` | BLACK | `ESC[0;30m` |
| `^g` | LIGHTGREEN | `ESC[1;32m` | `^G` | GREEN | `ESC[0;32m` |
| `^b` | LIGHTBLUE | `ESC[1;34m` | `^B` | BLUE | `ESC[0;34m` |
| `^r` | LIGHTRED | `ESC[1;31m` | `^R` | RED | `ESC[0;31m` |
| `^y` | YELLOW | `ESC[1;33m` | `^Y` | BROWN | `ESC[0;33m` |
| `^c` | LIGHTCYAN | `ESC[1;36m` | `^C` | CYAN | `ESC[0;36m` |
| `^p` | LIGHTPURPLE | `ESC[1;35m` | `^P` | PURPLE | `ESC[0;35m` |

Attributes and specials (`ColorLibrary.java:69-73`, `Brown.java:611-621`): `^.` reset (`ESC[0;0m`), `^!` bold, `^_` underline, `^*` blink, `^/` italics, `^N` normal (the `NORMAL` role). Backgrounds use `^~` plus a letter (`ColorLibrary.java:74-82`): `^~w` white (47), `^~g` green (42), `^~b` blue (44), `^~r` red (41), `^~y` yellow (43), `^~c` cyan (46), `^~p` purple (45), `^~k` black (40, special-cased at `CoffeeFilter.java:692-693`).

Other prefixes handled in `CoffeeFilter.convertEscape` (`CoffeeFilter.java:436-887`):

- `^#nnn` foreground 256-colour, where `nnn` is three base-6 digits (`CoffeeFilter.java:596-611`). `^##RRGGBB` is truecolour, reduced to 256 colours (`CoffeeFilter.java:511-585`). `^|` is the background equivalent.
- `^?` returns to the previous colour (`CoffeeFilter.java:448-466`). `^[` pushes the current colour and `^]` pops it (`CoffeeFilter.java:442-447, 448-449`).
- `^^` is a literal caret, `^"` a literal quote (`CoffeeFilter.java:785-787, 828-830`).
- `^<...^>` and `^&...;` carry MXP tags and entities through to MXP clients and are stripped otherwise (`CoffeeFilter.java:736-784`).
- `^0`..`^9` expand to configured escape strings `ESCAPE0`..`ESCAPE9` (`coffeemud.ini:1898-1907`, all empty by default). They expand when there is no session or the client has MSP, and are deleted otherwise (`CoffeeFilter.java:788-811`).

#### 2.2 Semantic colour roles

Most text is coloured by role, not by hue. `ColorLibrary.SpecialColor` (`ColorLibrary.java:215-244`) defines 27 roles, each with a one-letter code. Defaults are in code (`Brown.java:638-664`) and in the shipped `COLORSCHEME` (`coffeemud.ini:1186-1212`). The two agree.

| Role (code) | Used for | Default |
|---|---|---|
| `YOU_FIGHT` (`f`) | your attacks | LIGHTPURPLE |
| `FIGHT_YOU` (`e`) | attacks on you | LIGHTRED |
| `FIGHT` (`F`) | other fights | RED |
| `SPELL` (`S`) | spell text | YELLOW |
| `EMOTE` (`E`) | emotes | LIGHTPURPLE |
| `TALK` (`T`) | say | LIGHTBLUE |
| `TELL` (`t`) | tells | CYAN |
| `CHANNEL` (`Q`) | channel text | LIGHTCYAN + BGBLUE |
| `CHANNELFORE` (`q`) | channel foreground | LIGHTCYAN |
| `IMPORTANT1` / `2` / `3` (`x`, `X`, `Z`) | emphasis levels | LIGHTCYAN+BGBLUE, YELLOW+BGBLUE, YELLOW+BGRED |
| `DING` (`z`) | bell | `\u0007` (`Brown.java:654`) |
| `ROOMTITLE` (`O`), `ROOMDESC` (`L`) | room text | LIGHTCYAN, WHITE |
| `DIRECTION` (`D`), `DOORDESC` (`d`) | exits and doors | LIGHTCYAN+BGBLUE, LIGHTBLUE |
| `ITEM` (`I`), `MOB` (`M`) | items and creatures | LIGHTGREEN, LIGHTPURPLE |
| `HITPOINTS` (`h`), `MANA` (`m`), `MOVES` (`v`) | status values | LIGHTCYAN, with ratio thresholds (below) |
| `NORMAL` (`N`), `HIGHLIGHT` (`H`) | base text, highlight | GREY, LIGHTCYAN |
| `UNEXPDIRECTION` (`U`), `UNEXPDOORDESC` (`u`) | unexplored exits | CYAN+BGBLUE, LIGHTBLUE |
| `WEATHER` (`J`) | weather | WHITE |

Roles `h`, `m` and `v` carry a stat name (`HITS`, `MANA`, `MOVE`; `ColorLibrary.java:236-238`). When one of them is rendered, `CoffeeFilter.java:843-859` computes `pct = cur / max` from the player's state and overrides the colour: below 0.25 it becomes LIGHTRED, below 0.5 YELLOW, and otherwise the role default applies.

`fixSourceFightColor` (`Libraries/Brown.java:460-468`) rewrites combat text so the same event reads from each side: `^F` becomes `^f` in the source's message and `^e` in the target's.

#### 2.3 From codes to escape sequences

- The lookup tables are arrays indexed by the code character. `standardColorLookups` (`Brown.java:611-727`) fills them with ANSI and `standardHTMLlookups` (`Brown.java:492-601`) with HTML.
- Compound roles are built by merging sequences. `mixColorCodes` (`Brown.java:446-458`) takes `ESC[1;36m` and `ESC[44m` and produces `ESC[1;36;44m`. This is the live path for `CHANNEL`, `DIRECTION`, `UNEXPDIRECTION` and the `IMPORTANT` roles (`Brown.java:648-663`).
- ANSI has one bold flag per sequence, which shapes the palette. `Commands/ColorSet.java:238-241` says so directly: the background and foreground share one code, so backgrounds must be dark and foregrounds bright. `ColorSet.java:244-251` enforces this by offering only upper-case basic letters (plus `K`) as backgrounds and lower-case ones as foregrounds.
- `translateCMCodeToANSI` (`Brown.java:363-383`) would build `ESC[<bg>;<bold>;<fg>m` from a code string (`bg = 40 + index`, `fg = 30 + index`, index from `"krgybpcw"` at `Brown.java:237`). It has no callers in the tree, so the live path is the mixing above.

#### 2.4 Per-session palettes

- `COLORSCHEME` (`coffeemud.ini:1186-1212`) is a global list of `ROLE=COLOR+COLOR` pairs. `standardColorLookups` applies it (`Brown.java:665-717`) by name lookup against the enum (`Brown.java:697-705`).
- A player's own palette is a string parsed by `fixPlayerColorDefs` (`Brown.java:326-361`). Entries are `<char><code>` or `(<n>)<code>` separated by `#`. A two-part background form `^X|^Y` is rewritten as `^Y^~X` (`Brown.java:346-349`). Single-letter aliases such as `^r` are resolved one level (`Brown.java:352-359`).
- `DefaultSession.getColorCodes` (`DefaultSession.java:1788-1811`) rebuilds the table only when the player's colour string changes.

#### 2.5 The 256-colour palette

- `resources/skills/colors.txt` has 232 data rows (codes 0-231). The header line (`colors.txt:1`) names nine columns, but the rows have eight fields. The code reads field 0 (number), 1 (CM name), 2 (second name), 4 (16-colour fallback), 5 (hex), 6 (expertise number), and 7 (basic letter or base-6 code). Field 3 is unused.
- Codes 16-231 are the 6x6x6 cube. The code is `16 + 36*r + 6*g + b` (`Brown.java:874-884`). Basic codes are 0-15.
- Codes 232-255 (the grey ramp) appear in the HTML table (`ColorLibrary.java:115-117`) but not in `colors.txt`. The filter still emits them directly as `ESC[38;5;<n>m` (`CoffeeFilter.java:625`).
- Each palette row has an expertise number (`Brown.java:906`) that refers to a `TUNING<n>` expertise definition (`Brown.java:787`). The generator uses it for level and ordering (`Brown.java:785-829`). Whether the dye skills gate on it is unverified.
- Clients without 256-colour support get the 16-colour fallback from `getANSI16Equivalent` (`CoffeeFilter.java:616-619`, `Brown.java:944-948`), mapped during `activate` (`Brown.java:914-930`).

#### 2.6 Truecolour reduction (formula as written)

`CoffeeFilter.java:537-549` reduces `#RRGGBB` to a 256-colour index:

```java
if(Math.max(dig1, Math.max(dig2,dig3)-Math.min(dig1, Math.min(dig2,dig3)))<20)   // line 537
    grayValue = (int)Math.round((dig1+dig2+dig3)/3.0/10.0)*10;                    // line 539
// ...accepted when 8 <= grayValue <= 238, index = 232 + (grayValue-8)/10          // lines 540-541
// otherwise cube: digr = clamp(round(dig1/255*5), 0, 5), index = 16 + 36*digr + 6*digg + digb   // 545-548
```

Line 537 does not compute the channel spread it appears to test. See Problems.

### 3. Filters (CoffeeFilter)

#### 3.1 Entry points

| Method | Used for | What it does |
|---|---|---|
| `simpleOutFilter` (`CoffeeFilter.java:131-175`) | Minimal output | Turns `` ` `` into `'`, handles `\n`, `\r`, `\%` and `\'` escapes |
| `fullOutFilter` (`CoffeeFilter.java:1144-1966`) | Everything sent to a player | Wrap, MXP tags and URL links, sound directives, pronouns, `(s)`, `a(n)`, colour codes, capitalisation, colour reset |
| `colorOnlyFilter` (`CoffeeFilter.java:891-984`) | Prefixes, boxes, pre-wrapped text | Colour codes and MXP entity escaping, no wrap |
| `mxpSafetyFilter` (`CoffeeFilter.java:988-1060`) | Raw text | Escapes for MXP clients only (buggy, see Problems) |
| `wrapOnlyFilter` (`CoffeeFilter.java:178-428`) | Help columns | Wrap and escapes; used only by `MUDHelp.columnHelper` (`MUDHelp.java:445-455`) |

#### 3.2 Order of work inside fullOutFilter

1. `wrap = S.getWrap()`, or 78 without a session (`CoffeeFilter.java:1163`). The session default is `PlayerStats.DEFAULT_WORDWRAP = 78` (`Common/interfaces/PlayerStats.java:53`); a player with the default width uses the terminal width if known (`DefaultSession.java:1059-1066`).
2. Per character (`CoffeeFilter.java:1172-1857`):
   - MXP URL linking when `http://` or `https://` appears (`CoffeeFilter.java:1178-1187, 1281-1291`).
   - `` ` `` becomes `'` (`1214-1216`).
   - `!!SOUND(...)` and `!!MUSIC(...)` (`1217-1264`). See 3.5.
   - MXP escaping of `>`, `"`, `&` (`1265-1319`), `%XX` hex escapes (`1320-1338`), and `\` escapes (`1374-1402`).
   - `(s)`, `(es)`, `(ys)` plural markers (`1339-1373`). See 3.4.
   - `<X-TAG>` pronoun tags (`1403-1770`). See 3.3.
   - ESC sequences, which are dropped for clients without ANSI (`1771-1806`).
   - `^` codes via `convertEscape` (`1807-1820`).
   - `.` sets the capitalisation flag for the next word (`1821-1825`).
   - `a(n)` article fixes (`1826-1854`, post-pass at `1909-1932`).
3. Wrapping (`1857-1892`): see 3.2.1.
4. First letter upper-cased unless the preceding character is a digit (`1895-1899`).
5. If the current colour is not normal, `^N` is appended and the state reset (`1900-1907`).

##### 3.2.1 Word wrap

- `len` is the column limit in buffer characters. It starts at `wrap` (`1164`). A CR or LF resets it to `loop + wrap` (`1202-1204, 1209-1210`).
- Each space records `lastSp`. When `loop` reaches `len`, the line breaks at the last space (`1859-1889`). Two spaces become CR LF, and a single space is replaced by CR LF. The break is skipped if the next word ends at CR, LF or end of text (`endLookAhead`, `1090-1141`).
- Colour codes and escapes do not consume columns. Their length is added back to `len` (`1813-1816`).

#### 3.3 Pronoun tags

`<S-NAME>`, `<T-HIM-HER>`, `<O-...>` and similar tags name the source, target or tool (`CoffeeFilter.java:1412-1446`). The suffix comes from the `Pronoun` enum (`TelnetFilter.java:52-70`):

| Tag suffix | Replacement (examples from code) |
|---|---|
| `-NAME` | the name; `you` if the viewer is that mob; `someone` or `something` if the viewer cannot see it (`CoffeeFilter.java:1458-1480`) |
| `-NAMESELF` | `yourself` when source and target are the same and the viewer is that mob (`1541-1542`) |
| `-NAMENOART` | the name with the article removed (`1513-1535`) |
| `-HIS-HER`, `-HIM-HER`, `-HE-SHE` | `your`, `his`/`her`/`its`, `him`/`her`/`it`, `he`/`she`/`it`; the viewer's own form gives `you` (`1592-1625, 1655-1671`) |
| `-HIMHERSELF`, `-HISHERSELF` | `yourself`, `himself`/`herself`/`itself`, `themself` for null (`1626-1654`) |
| `-IS-ARE`, `-HAS-HAVE` | `are`/`is`, `have`/`has` (`1706-1737`) |
| `IS-ARE` (`ISARE2`) | `is` or `are` from the preceding word: `A`, `YOU`, `1`, or after a `you` substitution (`1717-1725`) |
| `-YOUPOSS` | `your`, or `X's`, or `someone's` (`1565-1582`) |
| `-SIRMADAM`, `-MRMS`, `-MISTERMADAM` | honorifics from the mob's character stats, `sir` and `Mr.` for non-mobs (`1673-1705`) |
| `-WITHNAME` | ` with <name>` via `L(...)` (`1584-1591`) |
| `-ACCOUNTNAME` | the player's account name for player mobs (`1481-1511`) |

Two rules make the output consistent. After a `you` substitution, `doSagain` is set (`1466-1467`) so later `is/are` and `(s)` markers use second-person forms. Sentence-initial capitalisation is set after `. ` followed by a space or a `<` tag (`1821-1825`), and the replacement is capitalised accordingly (`1744-1746`). The replaced text is scanned again at the same position (`1739-1750`), so a replacement can contain its own markup.

#### 3.4 Grammar markers

- `(s)`, `(es)` and `(ys)` (`CoffeeFilter.java:1339-1373`). The singular or plural choice comes from `getLastWord` (`1062-1088`). The words `A`, `YOU` and `1` count as singular (`1348-1351`). `(ys)` gives `y` or `ies`. `(es)` gives `` or `es`.
- `a(n)` (`1826-1846`). The article becomes `an` before a vowel in `aeiouAEIOU` and `a` otherwise (`1836`). Only the first letter is tested, and a deferred pass (`1909-1932`) handles cases with a colour code in front.

#### 3.5 MXP and MSP

- `!!SOUND(file ...)` and `!!MUSIC(...)` become `<SOUND file>` tags for MXP clients (`1234-1244`). MSP clients keep the text (`1245-1251`). Otherwise the directive is removed (`1253-1261`). Sound is suppressed for players without `MOB.Attrib.SOUND` and for listeners who cannot hear the source (`1227-1232`).
- `^<` and `^&` pass MXP tags and entities through only when `S.isInlineAllowed(InProto.MXP, ...)` says so (`CoffeeFilter.java:736-784`).
- MXP is detected per session (`CoffeeFilter.java:1171`). `TELNET_MSP` and `TELNET_MXP` are the mode flags (`Session.java:1075-1077`).

#### 3.6 Input filters

- `simpleInFilter` (`CoffeeFilter.java:1968-2036`) turns `'` into `` ` ``. It escapes `%00`, `%FF`, `%1B` and `%07` unless the user is an archon. It strips `^<`, `^>` and `^&` unless MXP tags are permitted. A backspace (char 8) removes the previous character.
- `secondaryUserInputFilter` (`CoffeeFilter.java:430-434`) rewrites `<S-` to `[S-`, `<T-` to `[T-` and `@x` to `0x`, so typed text can be stored as a template. It is applied in `Commands/Say.java:332`, `Emote.java:109`, `Tell.java:159`, `Whisper.java:92`, and in several abilities.
- `fullInFilter` (`CoffeeFilter.java:2039-2059`) encodes newlines as `\r` and carriage returns as `\n` text. `safetyInFilter` (`CoffeeFilter.java:2062-2086`) does the same for stored strings.

### 4. Translation and input rewriting (DirtyLanguage)

#### 4.1 Name

The class name suggests profanity handling, but nothing in it or in the rule files filters words. It is the rewrite engine for typed commands and the localisation hook for output. The translation layer is empty for English (see 4.5).

#### 4.2 Rule language

`resources/parser_en_TX.properties` (186 lines) and `resources/translation_en_TX.properties` (101 lines) are read by `loadFileSections` (`DirtyLanguage.java:216-544`). Locale-specific files are named `parser_<LANG>_<COUNTRY>.properties` and `translation_...`. `CMFile` resolves the case difference between `EN` and `en` (`core/CMFile.java:164`).

| Command | Effect (`DirtyLanguage.java` enum at 49-60) |
|---|---|
| `REPLACEWHOLE` | Replace the whole command when it equals the string (case-insensitive). Preferred, fastest |
| `REPLACEEXACT` | Same, case-sensitive |
| `REPLACEALL` | Replace every occurrence of a substring |
| `REPLACE` | Regex match; the replacement overwrites the whole command; `\1`..`\n` are groups |
| `IGNOREWHOLE`, `IGNOREALL`, `IGNORE` | Drop the command if it matches (exact, substring, regex) |
| `AUTOIGNORE n` | Record unchanged input shorter than `n` (default 100) as an `IGNOREWHOLE` line in the rule file |
| `DEFINE "X" AS "Y"` | Macro, substituted into later patterns (`{COMMANDS}` in the shipped file, line 101) |

#### 4.3 Sections and entry points

Parser sections (`parser_en_TX.properties:97-185`): `command-word-processor`, `command-pre-processor`, `item-pre-processor`, `item-fail-processor` (the header calls it `failed-item-processor`, line 15), `raw-input-processor`. Translation sections (`translation_en_TX.properties:96-101`): `session-translation` (may have per-class sub-sections such as `session-translation:<class>`), `filter-translation`, `final-translation`.

- `preCommandParser` (`DirtyLanguage.java:616-787`) is called by `DefaultSession.java:3670`, `Commands/Shell.java:712`, `Libraries/CMJournals.java:899` and `Abilities/Properties/Prop_LangTranslator.java:297`.
- `preItemParser` and `failedItemParser` are called from `Locales/StdRoom.java`, `rawInputParser` from `Libraries/CharCreation.java` and `Abilities/Common/Painting.java`.
- `commandWordTranslation` runs at boot for command words (`Libraries/StdLibrary.java`, `I()` helper).
- `L(text, args)` (`Libraries/StdLibrary.java:88-91`) calls `fullSessionTranslation`, which looks up the caller's class by `FINDER.getCaller()` and checks class-scoped sections before the global one (`DirtyLanguage.java:976-1036`). It then substitutes `@x1`, `@x2`, ... through `CMStrings.replaceVariables` (not read).

#### 4.4 What the command pre-processor does

1. The command words are joined with tabs into one string (`DirtyLanguage.java:643-645`). Rules match against that string.
2. Rules run in file order. A `REPLACE` that changes the command sets it to the replacement. A replacement can contain `\n`, and `insertExpansion` (`DirtyLanguage.java:593-612`) splits it into several commands. `REPLACEWHOLE` stops at its first hit (`697-700`). `IGNORE*` returns an empty command list (`737-757`).
3. If nothing changed and the command is short enough, `AUTOIGNORE` writes it into the rule file (`765-782`, `918-948`).
4. The result is split back into word lists (`783-786`).

The shipped rules (`parser_en_TX.properties:101-177`) do the following:

- Normalise diff-style counters, so `#2sword` becomes `2.sword` (`104-105`). Expand ordinal words, so `first` becomes `1.` (`107-115`).
- Expand command shortcuts. `ge` and `g` become `get`, `pu` becomes `put`, and so on (`119-137`).
- Compound phrases with `it`, such as `open X and close it`, and `put X in Y` becoming `put X from Y` (`117`, `144-149`).
- Normalise directions for `open`, `close`, `look`, `lock` and `unlock` (`150-157`).
- Split multi-item commands on `and` and commas, so `get sword and shield` becomes two commands (`159-168`).
- Strip articles (`170-177`).

#### 4.5 Output translation and locale

- `finalTranslation` runs last, just before the socket write (`DefaultSession.java:1426`). The shipped `[final-translation]`, `[filter-translation]` and `[session-translation]` sections hold no rules (`translation_en_TX.properties:96-101`). English output passes through unchanged apart from `@x` substitution.
- `propertiesLoaded` sets the locale from `LANGUAGE` and `COUNTRY` (`DirtyLanguage.java:102-106`). `coffeemud.ini:157-158` sets them to `en` and `TX`. `setLocale` (`DirtyLanguage.java:85-100`) rebuilds `createLocale` and clears the cached rule sets (`584-591`). The cache keys are `PARSER_<LANG>_<CTRY>` and `TRANSLATION_<LANG>_<CTRY>` (`546-555`).

### 5. Help (MUDHelp)

#### 5.1 Data

- `resources/help/*.ini` holds 15 files. Keys are upper case and values are text (`KEY=text`, with `\` continuation lines). Files named `arc_*.ini` form the archon help (`getArcHelpFile`, `MUDHelp.java:1695-1753`). The rest form player help (`getHelpFile`, `MUDHelp.java:1756-1817`).
- `<ZAP=word>` tags are replaced by masking (`MUDHelp.java:1731-1743, 1795-1806`).
- Keys are upper-cased for lookup (`MUDHelp.java:1009`). Lower-case keys are logged at load time (`MUDHelp.java:1719-1723, 1782-1786`).
- Short values that name another key are aliases. `COLOR=ANSI` (`resources/help/help.ini:194`) and `COLORLIST=COLORSET` (`help.ini:872`) are examples.

Corpus size (counted with a script that approximates Java `Properties` parsing; approximate): 5,591 unique keys in the player files, 627 in the archon files. Roughly 2,800 player-file entries are short values naming another key. About 1,000 of them differ from their target only by underscores (`ABANDON_SHIP` and `ABANDONSHIP`). The rest are abbreviations (`AAF`), or bare names pointing at a class-prefixed key (`HERD` to `HERDING`, `ABIDING_AURA` to `PALADIN_ABIDING_AURA`). Eight keys appear in more than one player file. The later file wins, and `File.list()` order is not sorted, so the winner depends on the platform (`MUDHelp.java:1767-1788`, unverified).

#### 5.2 Lookup cascade

`getHelpText` (`MUDHelp.java:1005-1661`) tries these stages in order and returns the first hit:

1. Callouts for `CHANNEL_`, `NOCHANNEL_`, `AREAHELP_`, `GENCHARCLASS_`, `GENRACE_`, `SOCIAL_` (`1028-1126`).
2. Exact key (`1130`), then skill prefixes `SPELL_`, `PRAYER_`, `SONG_`, `DANCE_`, `PLAY_`, `CHANT_`, `BEHAVIOR_`, `POWER_`, `SKILL_`, `PROP_` (`52-56`, `1134-1145`).
3. Social, government, generic ability, expertise, character class, race, achievement, area, deity, channel, command, and script method help (`1147-1336`). Script method help is parsed from `guides/Scriptable.html` (5.5).
4. Recipes: "X is an item that is craftable by Y" (`1339-1361`).
5. Archon file, then player file (`1363-1369`).
6. Inexact matches in this order: prefix (`1377-1392`), currency (`1393-1410`), contains via `CMLib.english().containsString` (`1412-1427`), social, government, ability, expertise, achievement (`1430-1507`), raw substring (`1508-1523`), area (`1526-1541`), channel (`1554-1580`), suffix stripping (`1583-1596`), command (`1598-1616`), recipe (`1618-1634`).

After the cascade, a text of 1-30 characters that names another key is followed as a redirect (`1637-1652`). The text is then passed through `fixHelp` (`1657-1660`).

`normalizeHelpText` (`MUDHelp.java:854-864`) takes a skip counter. `getHelpMatch(..., skipEntries)` (`846-852`) returns the Nth match, so a caller can page through matches. `Help.java:66-86` uses `skipEntries = 0`, and `Help.java:74` adds up to five "see also" keys through `getSeeAlsoHelpOn` (`MUDHelp.java:1986-2039`).

#### 5.3 Virtual pages

`fixHelp` (`MUDHelp.java:458-843`) rewrites a help text that starts with a tag. It generates the body from game data:

| Tag | Generated content | Lines |
|---|---|---|
| `<CURRENCY>`, `<CURRENCIES>` | Denominations, exchange rates, per area or world | `463-514` |
| `<EXPERTISE>` | Name, requirements, who may use it | `516-532` |
| `<CHARCLASS>` | Max stats, qualifiers, races, prime stat, attack, practice, train, HP, mana, moves, damage, weapon and armour limits, bonuses | `533-571` |
| `<RACE>` | Stat mods, trains and practices, senses, abilities, languages, life expectancy, XP adjustment, immunities, equipment | `572-626` |
| `<CALENDAR>` | Hours, days, months, seasons from the local clock | `627-628`, `329-369` |
| `<COLORS>`, `<COLORS256>` | Colour table from `Brown.getColorInfo` | `629-632` |
| `<ABILITY>` | Spell or skill block: type, proficiency, domain, availability by class, prerequisites, masks, allows, components, use cost, quality, targets, range, commands | `633-833` |

The ability block resolves the type from the key prefix (`MUDHelp.java:638-679`). It tries the full key, then a key with underscores removed from the right, which lets `SPELL_FIRE_BALL` match `Spell_Fireball` (`683-691`). Proficiency is shown as a word when the combat-prowess setting asks for it (`741-745`), using `proficiencyRanges` (`64`, `88-102`).

Text containing `@` goes through `CMLib.webMacroFilter().virtualPageFilter` (`MUDHelp.java:836-837`), so help can run web macros.

#### 5.4 Other generated help

- Craft recipes: a recipe name in any craft skill's list yields "X is an item that is craftable by Y." (`1339-1361`, `1618-1634`).
- Deities: help runs the `Deities` command internally (`1262-1281`).
- Channels: `CHANNEL` and `NOCHANNEL` pages receive the channel name and an extra description (`1286-1310`).
- Topics (`getTopics`, `141-185`) are keys whose value is short, empty or not a key, which separates topic names from alias entries.

#### 5.5 Script method help

`getScriptableHelp` (`MUDHelp.java:874-980`) reads `guides/Scriptable.html` at run time. It finds `<h3>` anchors with a regex, takes the block up to the next anchor, and extracts `Usage:` and `Example:` sections. The result is cached in `PARSED_SCRIPTABLE_HELP` (`877-882`). Help for MOBPROG methods is therefore derived from the guide, not written separately.

#### 5.6 Editing and caching

- `addModifyHelpEntry` (`MUDHelp.java:1874-1983`) opens a journal-style message editor (`CMLib.journals().makeMessageASync`, `1946`). On save it rewrites the `.ini` file in place, keeping continuation lines (`1958-1976`). Deleting an entry uses the same path with `deleteOnly`.
- Caches: `MAIN HELP FILE` and `ARCHON HELP FILE` (`MUDHelp.java:1699, 1744, 1760, 1808`), cleared by `unloadHelpFile` (`2042-2095`), which also clears intro and help text caches.

#### 5.7 Messages

Examples of text this library produces:

- `"Help files unloaded. Next HELP, AHELP, new char will reload."` (`MUDHelp.java:2094`)
- `"See also help on: "` (`Commands/Help.java:86`)
- `"No help is available on '@x1'."` followed by search matches or a pointer to `COMMANDS` and `TOPICS` (`Commands/Help.java:97, 101`)
- `"Invoked  : Automatic"` for auto-invoked abilities (`MUDHelp.java:830`)
- `"The time here is divided into @x1 hours, each of which is about @x2 real minutes long."` (`MUDHelp.java:333-335`)

## Content worth mining

### Colour tables

The 16 basic colours, their letters and ANSI codes are in How it works 2.1. The 27 roles and their defaults are in 2.2. Both can be reused as a theme dictionary.

### Palette sample (`resources/skills/colors.txt`)

| Code | Name | Hex | Base-6 | Note |
|---|---|---|---|---|
| 0 | black | `#000000` | basic | 16-colour fallback 0 |
| 16 | black | `#000000` | 000 | first cube entry |
| 59 | dark gray | `#5f5f5f` | 111 | |
| 145 | deep silver (medium gray) | `#afafaf` | 333 | |
| 231 | pale white | `#ffffff` | 555 | last cube entry |

The file has 232 rows with names in two columns, and an expertise number per row. Dye and lacquer colours come from these rows.

### Generated dye and lacquer lists

`Brown.generateRecipes` (`Brown.java:774-848`) writes `resources/skills/dyeing.txt` and `lacquering.txt` (450-451 lines each) from the palette. Rows are name, level, price tier, a coloured display name with `^?` reset, and the expertise number. It has no callers in the tree, so these are snapshots.

### Pronoun tags and grammar markers

The tag table is in 3.3. The `(s)`, `(es)` and `(ys)` markers and the `a(n)` article are in 3.4.

### Help content

- 15 `.ini` files of player and archon help, roughly 5,600 keys (5.1). Skill, spell, prayer, song, chant, behaviour, property, disease, poison and trap entries each have their own file (for example `skill_help.ini`, `spell_help.ini`, `prayer_help.ini`).
- `resources/help/help.txt` and `arc_help.txt` hold the top-level help text.
- Virtual pages build character class and race descriptions from the loaded class and race definitions, so those pages follow the data.

### Parser rule set (`resources/parser_en_TX.properties`)

The shipped rules (4.4) are a worked example of a small command grammar: multi-item splits, ordinals, counters, shortcuts and direction normalisation, all in about 75 lines.

## Notable design ideas

- **One letter per colour, case as weight.** A colour is two characters (`^r`), easy to type and to store. Upper case is dim and lower case is bold, which keeps one rule across the palette (`ColorLibrary.java:53-82`).
- **Roles, not hues.** Output is tagged with a role (`FIGHT_YOU`, `ITEM`, `DIRECTION`). The role-to-colour map is global (`COLORSCHEME`) and per player (`fixPlayerColorDefs`), so the same text adapts to the player's taste without touching code.
- **Colour state stack.** `^[` pushes, `^]` pops and `^?` returns to the previous colour (`CoffeeFilter.java:442-466`). A nested coloured phrase restores the outer colour instead of resetting to normal.
- **Perspective pronouns.** `<S-NAME>` shows `you` to the actor, `someone` to a viewer who cannot see the actor, and the right verb form after that (`CoffeeFilter.java:1458-1480, 1466-1467`). A single template serves every viewer.
- **Perspective fight colour.** `fixSourceFightColor` (`Brown.java:460-468`) makes the same blow show one colour to the attacker and another to the victim.
- **Health warnings in the colour layer.** The status colours change with the ratio (2.2), so a prompt warns without any game logic outside the filter (`CoffeeFilter.java:853-858`).
- **Help as data.** `.ini` entries, aliases and a continuation syntax keep help editable by builders. Virtual pages turn the game's own data into help, and recipe and deity entries are inferred from other systems (5.3-5.4).
- **Palette rows carry an expertise number** (2.5). The dye generator uses it for level and ordering. Whether play gates colours on it is unverified.
- **Single ANSI sequence.** Fore, back and bold merge into one escape sequence (`Brown.java:446-458`). `ColorSet.java:238-241` documents the limit this places on the palette.
- **Declarative rewrite rules.** The rule file holds ordered patterns and macros, separated into phases (command word, pre-processor, item, failed item, raw input). Changing phrasing needs no code.

## Problems and cruft

### Confirmed by reading (two also by evaluating the expressions)

1. **Grey detection precedence** (`CoffeeFilter.java:537`). `Math.max(dig1, Math.max(dig2,dig3) - Math.min(dig1, Math.min(dig2,dig3)))` takes the maximum of the red channel and the spread of the other two. It should be `max(r,g,b) - min(r,g,b)`. Effect: `#808080` maps to 145 (`#afafaf`) instead of the ramp entry 244 (`#808080`), and `#202020` to 59 (`#5f5f5f`) instead of 234. Confirmed by evaluating the expression.
2. **Background lookup collision** (`Brown.java:270-282`, `ColorLibrary.java:67-68, 81-82`). Lower-case `k` is both the background key for `BLACK` and `BGDEFAULT` and the foreground key for `DARKGREY`, which maps to `BGWHITE`. The second loop overwrites the first. Result: `getBackgroundCodeMap()['k']` and `['K']` are `BGWHITE`, and `BGBLACK` and `BGDEFAULT` are unreachable. The shipped `^~k` is special-cased to black (`CoffeeFilter.java:692-693`), but `^~K` gives white, and the HTML path (`StdWebMacro.java:365` via `getBackgroundHtmlTag`) gives white for `^~k`. ANSI and HTML disagree. Confirmed by evaluating the expressions.
3. **Infinite loop in `wrapOnlyFilter`** (`CoffeeFilter.java:354-370`). The inner `while` on `<` and `&` advances only when the character is not the terminator. At `^>` or `;` the loop neither advances nor exits, so it spins forever. Any `^&` followed later by `;`, or `^<` followed by `^>`, hangs the thread. The only caller is the class help table (`MUDHelp.java:448`), so this is latent unless such markup appears in those generated fields (unverified).
4. **Help redirect cycle** (`MUDHelp.java:1637-1652`). If key A's text names key B and B's text names A, and both texts are under 31 characters, the loop alternates forever. Only a self-reference is detected (`1642`).
5. **`mxpSafetyFilter` is wrong in three ways** (`CoffeeFilter.java:1021-1025`). The `&` condition is inverted: `!lt;` and `gt;` should be `!lt;` and `!gt;`. Bare `&` is never escaped, and `&gt;` becomes `&amp;gt;`. There is no bounds check, so `substring(loop, loop+3)` throws when `&` is in the last two characters. The `fullOutFilter` version (`1298-1306`) is correct.
6. **`getHelpList` reads the wrong table** (`MUDHelp.java:1686`). Values for the second (archon) table are read from the first.
7. **`setLocale` needs both parts to change** (`DirtyLanguage.java:92`). The condition uses `&&`, so changing only the language or only the country is ignored.
8. **`IGNOREALL` is case-sensitive while input is lower-cased** (`DirtyLanguage.java:526-528` stores the pattern as written; matches at `747` and `845` lower-case the input). Upper-case patterns never match. `REPLACEALL` (`427-451`) lower-cases the pattern and does not have this problem.
9. **`REPLACEEXACT` rules leak between sections** (`DirtyLanguage.java:235, 253-254, 536-537`). The map is declared once and never cleared. Sections are attached by reference, so later rules appear in earlier sections. The shipped translation file has one stray uncommented line (`translation_en_TX.properties:30`) that triggers this with a harmless rule.
10. **Mixed-case section names in `addAutoIgnoredString`** (`DirtyLanguage.java:926-929`). It checks `sectionNameU` but reads `fileIndexes.get(sectionName)`. A name that is not upper-case passes the check and then dereferences null (latent).
11. **Character-code parsing returns the wrong thing** (`Brown.java:233-241`). The loop increments `i` instead of decrementing it, so a code whose last character is not a letter throws, and `return 3` is unreachable. `translateCMCodeToANSI` has no callers in the tree, so this is latent.
12. **The "no wrap" flag does not stop wrapping** (`DefaultSession.java:1624-1628, 1675`). `fullOutFilter`'s `wrapOnly` only suppresses `(s)` and pronoun expansion (`CoffeeFilter.java:1340, 1404`). The wrap still uses `S.getWrap()` (`1163`). `TelnetFilter.java:147` documents the narrower meaning, so the name `noWrap` at the call site is misleading.

### Latent risks

- `Brown.getColorInfo` dereferences `color256s`, which stays null if `colors.txt` is missing (`Brown.java:738`).
- The `CHANNEL` callout does not check for null before `replaceAll` (`MUDHelp.java:1043-1045`).
- `WITHNAME` casts `regarding` to `PhysicalAgent` with no check (`CoffeeFilter.java:1589`).
- `fixPlayerColorDefs` uses a player-supplied index without a bounds check (`Brown.java:338-350`). Whether `ColorSet` validates it is unverified.
- `addAutoIgnoredString` rewrites a data file at run time (`DirtyLanguage.java:945-947`). Players' typos become permanent ignores.
- `isUrlEndChar[c%127]` aliases every character to the table (`CoffeeFilter.java:1178`). Characters 127 and above map to low indices, so some non-ASCII characters are treated as URL terminators.
- `L()` finds its caller with `SecurityManager.getClassContext()` (`StdLibrary.java:45-52`, `DirtyLanguage.java:1032`). This depends on a deprecated API and a fixed stack depth. The cost per call is unmeasured.
- The `a(n)` rule checks only the first letter for a vowel (`CoffeeFilter.java:1836`). "a hour" and "an unicorn" come out wrong.
- `padRight` in the class help (`MUDHelp.java:552-561`) may count colour markup as width, which would misalign columns. Unverified against `CMStrings.padRight`.
- Duplicate help keys across player files resolve by file order (5.1).

### Cruft and dead code

- `Formatting` and `DefaultFormatting` have no users (`Common/interfaces/Formatting.java`, `Common/DefaultFormatting.java`). Their `FCode` flags ignore the tag parameter (`DefaultFormatting.java:140-153`).
- `translateCMCodeToANSI`, `translateCMCodeToFGNumber` and the `mixHTMLCodes` path have no outside callers. `standardHTMLlookups` is used only by `StdWebMacro`.
- `generateRecipes` (`Brown.java:774-848`) is a build-time tool with no caller, and it writes into the resources tree.
- `wrapOnlyFilter` (`CoffeeFilter.java:178-428`) repeats most of `fullOutFilter` (`1144-1966`) without the pronoun, grammar and colour features.
- The name `DirtyLanguage` and the class name `Brown` say nothing about their roles.
- Documentation mismatches: `colors.txt:1` lists nine columns for eight fields. `coffeemud.ini:1177` lists a `CHANNELTALK` role that does not exist (`ColorLibrary.java:215-244`). `parser_en_TX.properties:15` calls a section `failed-item-processor`, but the section is `item-fail-processor` (`:183`). `translation_en_TX.properties:13` has a stray `a` after `[filter-translation]`.

## Takeaways for a Python SUD

**Output path.** Build one renderer that takes a message template, the actor, the target and the viewer, and returns text. It should run in four steps: expand pronoun tags for the viewer, map role markup to styles, wrap on the visible text, and restore the style state. Keep styling in a theme dict (roles to style names), loaded from a TOML file, with one role per game concept (about 20 is enough). Drop the 256-colour and truecolour paths unless you target 256-colour terminals.

```python
THEME = {"attack_you": "bold red", "attack_other": "red", "item": "green",
         "room_title": "bold cyan", "status_low": "yellow", "normal": "default"}
```

**Templates.** Keep the perspective idea. Give each entity a small object with subject, object, possessive, reflexive and verb forms. Use `you` when the viewer is the entity. Use "someone" when the viewer cannot perceive it (dark rooms, invisible NPCs). Single-player simplifies this, but NPC-to-NPC events still need the hidden-actor case.

**Grammar.** Put plural and article logic in two helper functions, `pluralize(word, n)` and `a_an(word)`, rather than in the text markup. Keep the `doSagain` lesson: after a second-person substitution, verbs and plurals must follow the second person.

**Help.** Store help as TOML or YAML tables keyed by normalised names. Keep alias entries. Generate descriptions for races, classes, spells and items from the data classes so they cannot drift from the numbers. Lookup should be exact, then alias, then prefix, then `difflib.get_close_matches`. Drop the fourteen-stage cascade. Keep a "see also" list built from shared tags.

**Input rewriting.** Keep a short ordered list of `(pattern, replacement)` rules applied to a normalised command string, with a multi-command form for macros. Drop `AUTOIGNORE` and the tab-joined encoding; `shlex.split` and an alias dictionary cover most of it. Phrase splitting (`get a and b`) is worth copying as a rule, not as code.

**Translation.** No localisation layer is needed for a single-language game. Keep one message catalogue so text is editable without touching code. Drop the caller-class lookup and the `SecurityManager` trick.

**Drop entirely.** Telnet and MXP negotiation, MSP sound directives, session-level spam suppression (unless you add a TUI), per-session colour caches, and `Formatting`/`DefaultFormatting`.

**Rethink for one player.** The viewer is always the player, which removes most of the `S`, `T` and `O` plumbing. Wrap width comes from `shutil.get_terminal_size()`. Colour detection can use `NO_COLOR` and `isatty()`. Anything the code reads from `PlayerStats` (wrap, colour string, sound attribute) becomes plain configuration.

**Borrow with care.** The files carry Apache License 2.0 headers (for example `Libraries/Brown.java:1-35`). Check the terms before copying code or data.

## Coverage

- **Read in full (10 of 10):** `Libraries/MUDHelp.java` (two reads), `Libraries/CoffeeFilter.java` (two reads), `Libraries/DirtyLanguage.java`, `Libraries/Brown.java`, `Libraries/interfaces/ColorLibrary.java`, `Libraries/interfaces/HelpLibrary.java`, `Libraries/interfaces/TelnetFilter.java`, `Common/DefaultFormatting.java`, `Libraries/interfaces/LanguageLibrary.java`, `Common/interfaces/Formatting.java`.
- **Read in full (data):** `resources/parser_en_TX.properties`, `resources/translation_en_TX.properties`.
- **Read in part:** `resources/skills/colors.txt` (header, sample rows, field count), `coffeemud.ini` (`LANGUAGE`, `COUNTRY`, `COLORSCHEME`, `ESCAPE0`-`ESCAPE9`, the `COLORSCHEME` comment), `Commands/Help.java:49-108`, `Commands/ColorSet.java:225-262`, `Libraries/StdLibrary.java:36-100`, `core/CMFile.java:140-200`, `core/CMLib.java` accessor lines, `core/CMProps.java` escape and colour lines, `Common/DefaultSession.java` (call sites only).
- **Counted by script, not read:** `resources/help/*.ini` (key and alias counts only; approximate).
- **Checked by evaluating the expressions:** Problems 1 and 2 (grey test and background map), using a line-by-line transcription of the Java.
- **Confirmed by reading only, not executed:** Problems 3, 4, 5 and 8.
- **Skipped:** `CMStrings` and `CMParms` internals (`replaceVariables`, `removeColors`, `padRight`, `parseAny`, `combineWithTabs`), `Common/interfaces/Session.java` beyond its constants, `Resources`, `ScriptingEngine`, the `guides/` text other than the `Scriptable.html` parse path, and the `siplet` client package, which has its own separate colour and Telnet code.
