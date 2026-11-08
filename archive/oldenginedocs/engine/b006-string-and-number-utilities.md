# String and number utilities

> Part of the CoffeeMud NEWOVERVIEW. Bucket: `b006` (String and number utilities) · 3 files · 9099 lines of Java.

## Summary

`CMath` (2,778 lines), `CMStrings` (6,112) and `BigCMath` (209) are static helper classes that the rest of the game calls directly. A grep of call sites finds about 2,700 `CMath.s_int(` calls, about 1,350 `CMStrings.padRight(` calls and 256 `CMath.s_parseIntExpression(` calls. Three ideas matter to a designer: a family of parsers that never throw, an infix formula language that data files write with `@x1`-style variables, and a boolean condition language over `$variables`. The string code measures visible width while skipping in-band colour codes. The rest is plumbing, and much of it is defective. Several defects were confirmed by compiling the unmodified source and calling it (marked "run" below), so treat these files as a source of ideas, not code to port.

## Key files and types

| File or class | Role | Lines |
|---|---|---|
| `core/CMath.java` | Never-throwing `s_*` parsers, bit helpers, Roman numerals, infix formula evaluator (streaming and compiled), `NotRandomHigh` | 2,778 |
| `core/CMStrings.java` | Search and replace, case-shape-preserving word replacement, colour-aware length, padding and truncation, text shaping, condition language, diff, glob matchers | 6,112 |
| `core/BigCMath.java` | BigDecimal constants (pi and related), `sqrt`, trig and `atan` via `double` | 209 |
| `CMath.CompiledFormula` / `CompiledOperation` | Pre-compiled formula: a list of value, variable, operation, sublist and previous-value nodes | `CMath.java:1557-1612` |
| `CMStrings` `STRING_EXP_SM`, `StringExpToken` | Table-driven lexer state machine and token class for the condition language | `CMStrings.java:3708-3778` |
| `CMStrings.diffMain` | Adaptation of Google's diff-match-patch (line diff, bisect, semantic cleanup) | `CMStrings.java:4699-5965` |

## How it works

### Parsing that never throws

`s_int`, `s_long`, `s_double` and `s_bool` return 0 or false on bad input (`CMath.java:1978-1988`, `2078-2114`). Enum lookup is case-sensitive and cached per class (`CMath.java:305-323`). Callers can read data without guards, but a typo becomes a silent zero. The number predicates disagree with each other. `isNumber` accepts `.` and commas (`CMath.java:393-409`; `isNumber("1,000")` is true, run). `isDouble` requires a decimal point (`CMath.java:2341-2371`; `isDouble("5")` is false, run). `isInteger` accepts one leading minus.

### The formula language (CMath)

Data writes formulas as strings. `@x1` to `@xN` index a caller-supplied `double[]`, and a bare `@x` means the running value so far (`CMath.java:1401-1420`). Operators are `+ - * / \ % ^`, `<` and `>` as min and max (`1443-1448`), `=` and `!` as 1/0 tests, and `?` as a random roll between two operands (`1474-1476`). There is no operator precedence. The evaluator folds tokens left to right (`1381-1486`), so `2+3*4` is 20 (run).

Two front ends share the operators. `parseMathExpression(StreamTokenizer, ...)` (`1372-1487`) handles one-off strings. `compileMathExpression` (`1637-1732`) turns the same text into a list of `CompiledOperation` nodes, which `parseMathExpression(CompiledFormula, ...)` (`1758-1819`) walks. Combat keeps formulas in compiled form (`Libraries/MUDFight.java:947-953`). Entry points are `s_parseIntExpression` (returns 0 on error, `1324-1334`) and `parseIntExpression` (throws, `1914-1917`).

Error text is developer-facing, for example `"'"+c+"' is an unexpected token after @."` (`CMath.java:1405`) and `"Undefined variable found: $"` (`CMStrings.java:3869`). The bucket shows no text to players.

### One parameter: a number, a formula or a name

`s_parseListLongExpression` (`CMath.java:2307-2317`) evaluates the text as a formula if it parses as one, and otherwise returns the index of the matching name (0 if none matches). `s_parseBitLongExpression` (`2249-2271`) accepts comma-separated names and ORs their bits, using `2^(index-1)` (`2265`). Containers use this with `Container.CONTAIN_DESCS` (`Items/interfaces/Container.java:172-192`). A builder can type `LIQUID,COINS` or `3` into the same field. The bit values are constants such as `CONTAIN_LIQUID=1`, `CONTAIN_COINS=2` and `CONTAIN_SHIELDS=262144` (`Container.java:127-165`).

### Bits, numerals and arithmetic

Bit helpers such as `bset`, `setb` and `unsetb` are plain operators (`CMath.java:722-894`). `isSet` builds its mask with `Math.pow` and `Math.round` (`977-981`, `1168-1173`), and has 225 call sites. Roman numerals (`95-159`) and `getStringFollowedByNumber` (`2467-2524`) split names such as "Sword IV" into a base name and a number. `numAppendage` (`166-182`) gives st, nd, rd and th, with the 11-13 exception. `div` never throws and returns `Infinity` for a zero divisor (`433-691`). `s_parseIntExpression("10/0")` returns -1 (run), because `Math.round(Infinity)` clamps to `Long.MAX_VALUE` and the `int` cast wraps it.

### BigCMath

`BigCMath.java` holds BigDecimal constants for pi and friends (`31-66`) and a `sqrt` that makes one double-precision Newton correction (`77-82`). Its trig functions round-trip through `double` (`113-159`), so they add no precision (read). Four files use it, mostly for the constants: `Libraries/CoffeeDark.java`, `Libraries/EnglishParser.java`, `core/collections/Dir3D.java` and `core/collections/BigVector.java`.

### The condition language (CMStrings)

`parseStringExpression` (`CMStrings.java:4505-4522`) evaluates text such as `$LEVEL > 5 AND $RACE = 'ELF'`. A table-driven lexer (`3708-3724`) produces `StringExpToken`s (`3751-3778`). `$name` is looked up at lex time, first as written and then uppercased (`3859-3873`). A flag decides whether a missing variable becomes `""` or throws.

The grammar has comparisons `= == != < <= > >=` and `IN` (substring), combined with `AND`, `OR`, `XOR`, `&` or `|`, plus `NOT` and parentheses. A comparison is a case-insensitive string compare if either side is a string literal, numeric if either side is numeric, and otherwise "assume[s] they are numbers" (`4174-4192`). Three callers use it: area expressions over `Area.Stats` (`Commands/Areas.java:252-261`), MUDPercolator conditions (`Libraries/MUDPercolator.java:4046`, uppercased first) and achievement VALUE expressions (`Libraries/Achievements.java:7146`).

### Colour-aware strings

CoffeeMud writes colour into the text. `^` followed by a letter sets a colour, `^~` sets background, `^#` and `^|` take 256-colour forms, `^<...^>` wraps a pseudo-HTML tag, `^&...;` is an entity, `^^` is a literal caret, and raw ESC sequences are ANSI (`CMStrings.java:2200-2321`; the code characters are in `Libraries/interfaces/ColorLibrary.java:322-332`). Five separate routines parse this grammar: `removeColors` (`2200`), `limitCut` (`2073`), `markMarkups` (`2402`), `lengthMinusColors` (`2547`) and the capitalize family (`1380-1705`). Their bounds differ. For example the FANSI256 check in `lengthMinusColors` uses `len-8` (`2581`) while the BANSI256 check uses `len-9` (`2593`).

Layout uses one rule: width is visible length. `padRight` (`3418-3428`) and `limit` (`3510-3518`) measure with `lengthMinusColors` and strip colour when they truncate. `limitColors` (`3530-3536`) and `ellipseColored` (`3579-3585`) keep colour instead. `padRight` has about 1,350 callers.

### Word and text helpers

`replaceWord` (`1198-1231`) replaces whole words ignoring case and copies the case shape of each match. Output checked by running: "The Cat sat. CAT!" becomes "The Dog sat. DOG!". Word tests use letter boundaries (`indexOfWord` `684`, `containsWord` `743`). `unWWrap` (`2355-2393`) joins hard-wrapped lines and keeps paragraph breaks. `scrunchWord` (`469-489`) fits a name into a width by removing spaces, then vowels from the right, then truncating. `normalizeCharacters` (`4354-4388`) maps smart quotes, dashes, ellipses and no-break spaces to ASCII.

Glob matching has two implementations: `matches` (`4551-4638`, with `*` and `?` encoded as placeholder characters) and `filenameMatcher` (`6061-6111`). `diffMain` (`4740-4830`) adapts diff-match-patch and has a one-second deadline (`DIFF_TIMEOUT`, `4699`). Only the Shell command's diff uses it (`Commands/Shell.java:1209`). The file header (`4689-4691`) also promises match and patch, which are absent. `convertHtmlToText` (`2718`) is a tag-stack HTML-to-text converter used by six call sites.

## Content worth mining

- **Container content categories**, 20 entries: ANYTHING, LIQUID, COINS, SWORDS, DAGGERS, OTHER WEAPONS, ONE-HANDED WEAPONS, BODIES, READABLES, SCROLLS, CAGED ANIMALS, KEYS, DRINKABLES, CLOTHES, SMOKEABLES, SS COMPONENTS, FOOTWEAR, RAWMATERIALS, EATABLES, SHIELDS (`Items/interfaces/Container.java:172-192`). A starting set for item categories.
- **Formulas inside data**: `resources/skills/expertises.txt:141` contains `+>={27+(5*@x1)}` and `+STR {15+@x1}`. The brace syntax is resolved by a caller outside this bucket.
- **Numeral suffixes in names**: "Sword IV" splits into base "Sword " and 4 (`CMath.java:2467-2524`).
- **Colour code letters**: `~` background, `#` and `|` for 256-colour forms (`ColorLibrary.java:322-332`).
- **Abbreviation**: `scrunchWord("Dragon Slayer of Doom", 10)` gives "DrgnSlyrfD" (run).

## Notable design ideas

- **Parse once, evaluate many.** A formula becomes a list of nodes with variable slots, so each evaluation is an array walk (`CMath.java:1557-1819`).
- **One parameter, three readings.** The parser decides whether a value is a literal, a formula or a list name (`CMath.java:2307-2317`). This suits builder-edited data.
- **Dice inside the arithmetic.** `1?6` is a roll written in the same grammar (`CMath.java:1474-1476`; `CMStrings.java:4108-4113`).
- **A deterministic high roll.** `NotRandomHigh` (`CMath.java:1496-1551`) returns the top of every range. Combat evaluates the same damage formula with it when `biasHigh` is set (`Libraries/MUDFight.java:953`, `1055`). One formula gives a ceiling without a second code path.
- **Case-shape-preserving replacement** (`replaceWord`).
- **Visible-width layout**: column width is measured as the player sees it, not as the string is stored.

## Problems and cruft

"Run" means the unmodified source was compiled into a scratch directory and called. Nothing in the repository was changed. "Read" means the claim comes from reading only.

| Where | Problem | Effect |
|---|---|---|
| `CMath.java:132-133` | `while(s.startsWith("Y")) x+=1000;` never changes `s` | `convertFromRoman("Y")` never returns (run). `convertToRoman(2000)` is "Y" (`98-102`). Callers: `WebMacros/ExpertiseNext.java:73` (guarded by `isRomanNumeral`, which excludes Y), `Libraries/ColumbiaUniv.java:423` |
| `CMStrings.java:4543-4545` | `x` is never advanced in `countSubstrings` | Infinite loop on any match (run). Called from `Libraries/editors/EditorMaterialsRequired.java:451, 460` |
| `CMStrings.java:3404` | `fixMudCRLF` builds `seq` and returns `thisStr` | No-op (run). 15 callers, mostly web form handlers |
| `CMath.java:1377-1379, 1642-1644` | `-` is not made ordinary, so StreamTokenizer lexes `-4` as a number | `2*3-4` is -24 (run); expected 2. `2*3 - 4` is 2 (run). Same in compiled form |
| `CMStrings.java:4401-4483` | Right-recursive with no precedence; `NOT` takes everything to its right (`4408-4416`) | `1=1 OR 1=2 AND 1=2` is true; `NOT 1=1 AND 2=3` is true; `10-3-2 = 9` is true (all run) |
| `CMStrings.java:4518-4521` | Trailing tokens are not checked | `1=1 2=3` and `1=1)` return true (run) |
| `CMStrings.java:3709-3713` | Lexer states 4 and 5 accept only `=` or the same character | `<>`, `><`, `=<`, `=>`, `!<`, `!>` cannot be lexed; the matching cases at `4196-4264` are unreachable. `1 <> 2` throws (run) |
| `CMStrings.java:4190-4191` | Unknown values are parsed with `Double.valueOf` | `$a = $b` with both values `""` throws NumberFormatException (run) |
| `CMath.java:1050-1051, 1058-1059` | `hexDigit` tests `c<'9'` and `c<'f'` | `hexDigit('9')` and `hexDigit('f')` return -1 (run). No callers |
| `CMath.java:892-895` | Recursion returns the highest set bit, not the first | `firstBitSetIndex(6)` is 2; `(0)` is -1 (run). The doc says "first" |
| `CMath.java:2265` | Index 0 gives `pow(2,-1)`, which rounds to 1; `+=` is used, not `\|=` | `ANYTHING` sets LIQUID's bit; `A,A` is 2 (run, with a stand-in array of the same first entries) |
| `CMath.java:2553-2557, 2567-2571` | `round(d,p)` rounds to an integer first and never uses `p` | `round(3.14159,2)` is 3.0 (run) |
| `CMath.java:664-667` | `mul(int,int)` wraps silently | `mul(100000,100000)` is 1410065408 (run) |
| `CMath.java:1008` | `valueLen` is 1 for 10 values, but index 10 needs two digits | `replaceVariables("@x10")` with values 100 to 109 gives "1000" (run) |
| `CMStrings.java:132` | `new StringBuilder(str.charAt(0))` picks the int-capacity constructor | `splitByCapitals("AbcDef")` is `["bc","Def"]` (run). No callers |
| `CMStrings.java:114-117` | `ltrim` starts at index -1 and substrings with it | `ltrim("abc")` throws; `ltrim("  abc")` is `" abc"` (run). No callers |
| `CMStrings.java:673` | `endsWithIgnoreCase` compares the prefix | `("hello world","world")` is false (run). No callers |
| `CMStrings.java:691, 719, 1285` | Word searches scan from the end | `indexOfWord` returns the last match; `replaceFirstWord` replaces the last (run). The doc says "first" (`1265-1268`) |
| `CMStrings.java:635-636` | `containsAnyWordIgnoreCase` lowercases the caller's array in place | Mutates input (run). 4 callers |
| `CMStrings.java:2183-2189` | `limitCut` counts ANSI escape body characters | `limitColors("ESC[31mabcdef", 3)` returns `ESC[31` (run) |
| `CMStrings.java:3362-3365` | `trimCRLF` uses `while ... return` | Removes one character from one end (run). 11 callers |
| `CMStrings.java:164` | `repeat(char)` casts the char to a byte | `repeat(U+2588, 3)` gives three U+FFFD (run). Callers pass ASCII, so this is latent |
| `CMStrings.java:3261, 3425-3427, 3625, 3647-3648` | Padding is capped by `SPACES`, 1024 spaces | `padRight("x",1100)` is 1025 characters; `padLeft("x",1100)` throws (run) |
| `CMStrings.java:4564-4566` | A backslash at the end of a mask reads past the array | `matches("abc","abc\\",true)` throws (run) |
| `CMath.java:1496-1551`, `CMStrings.java:4111` | Condition `?` uses `Math.random`; CMath uses a `Random` that `setRand` can replace (`CMath.java:52, 2292`) | Condition rolls cannot be seeded (read) |
| `CMath.java:393-409, 2341-2371` | Four number predicates with different rules | `isNumber(".")` and `isDouble("5")` disagree with each other (run) |
| `BigCMath.java:191-207` | `atan` uses denominators `2(i+1)` instead of `2i+1`; it ignores `scale`; it does not terminate for 0.5 | `atan(2)` gives 0.4731 against 1.1071 for `Math.atan` (run). No callers of this series |
| `CMStrings.java:3992-4001` | `matchSimpleValue` removes a token from the shared list | Could change a later retry (read) |
| `CMStrings.java:4689-4691` | Header promises match and patch | Not present (read) |

## Takeaways for a Python SUD

- **Do not port the parsers.** Use `int()` and `float()` in one helper with a clear error, and keep the "never throws" rule only where a designer can see the default.
- **Formulas.** Write a small Pratt parser with real precedence, unary minus, `min`, `max` and a `?` dice operator. Compile each formula to a closure once, and pass variables by name (`level`, not `@x1`). Inject a `random.Random` so tests can seed it. For tests, use a `Random` subclass that returns the top of each range, the `NotRandomHigh` idea.
- **Conditions.** Reuse the `$var` idea for designer-written conditions with the same parser, but use Python values rather than strings. Reject trailing tokens, and do not guess types. Make `NOT` bind tightly.
- **Flags.** Use `enum.Flag` with names, and parse `"LIQUID,COINS"` by splitting and OR-ing. Avoid the index-offset mapping.
- **Text.** Keep markup out of the stored text. Store plain text or simple spans, and compute width with a visible-width function (for example `wcwidth`, after stripping ANSI with a regex). Keep colour-aware padding and case-shape replacement as ideas. Use `textwrap`, `fnmatch.fnmatchcase`, `difflib` and `html.unescape` instead of porting the module.
- **Drop.** The in-band `^` grammar, the HTML converter, the diff port and the BigDecimal trig. Nothing in these files is multiplayer-specific. The colour grammar is a multi-client terminal convention, which a single-player SUD can avoid by keeping styling out of game data.

## Coverage

- `core/CMath.java`: read in full except lines 2617-2639 (floor and ceiling wrappers, skimmed).
- `core/BigCMath.java`: read in full.
- `core/CMStrings.java`: read in full except lines 1817-1917 (array and character membership helpers, skimmed), 2806-3232 (rest of the HTML-to-text converter, EOL and CRLF helpers, `makeNumericHash`; skimmed) and 4855-6054 (diff engine internals beyond `diffMain`; skipped).
- Call-site counts come from grep of `CMath.name(` and `CMStrings.name(` patterns, so they are approximate.
