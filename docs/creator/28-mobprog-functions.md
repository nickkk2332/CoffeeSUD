# MOBPROG functions and dollar codes

This is the reference for everything a MOBPROG script can *ask*: the condition
functions used in `IF`, `ELSEIF` and `WHILE` lines (`HASTATTOO($n KNOWS_TOWER)`,
`LEVEL($n >= 10)`), the value form of the same functions inside text
(`$%LEVEL($n)%`), and the `$` codes (`$n`, `$i`, `$t`, `$o`, `$g`, `$<host VAR>`
and the rest) that script text expands before a command runs. It also gives the
expression rules: operators, quoting, nesting, numeric versus text comparison
and `AND`/`OR`/`NOT`. It is written for scripters and world creators; engine
developers will find the evaluating code named in each section. 242 function
names exist in the engine; every one has its own entry below.

Triggers (`*_PROG`) are in [26-mobprog-triggers.md](26-mobprog-triggers.md),
commands (`MP*` and the control-flow words) in
[27-mobprog-commands.md](27-mobprog-commands.md), and the tutorial-style guide
(hosts, files, `LOAD=`, variables, debugging) in [04-mobprog.md](04-mobprog.md).

## In this chapter

- [How functions and codes work](#how-functions-and-codes-work)
- [By-purpose index](#by-purpose-index)
- [Expression rules](#expression-rules)
- [Dollar codes](#dollar-codes)
- [Function reference](#function-reference): [chance and numbers](#chance-numbers-and-arithmetic),
  [text and lists](#text-and-lists), [variables, tags and script glue](#variables-tags-and-script-glue),
  [creatures](#creatures-who-or-what-someone-is), [advancement and money](#advancement-money-and-standing),
  [items and containers](#items-and-containers), [rooms and populations](#rooms-and-populations),
  [time and weather](#time-and-weather), [quests](#quests), [game mode and party](#game-mode-and-party),
  [combat](#combat-and-encounters), [shops, law and property](#shops-law-and-property),
  [clans and organisations](#clans-and-organisations), [vessels and riding](#vessels-and-riding),
  [messages](#messages), [generators and names](#generators-and-names)
- [Worked examples](#worked-examples)
- [Persistence notes](#persistence-notes)
- [Troubleshooting](#troubleshooting)
- [See also](#see-also)

Source: [DefaultScriptingEngine](../../CoffeeMud/com/planet_ink/coffee_mud/Common/DefaultScriptingEngine.java)
(`parseEval`, `eval`, `functify`, `extraTest`, `extraValue`, `varify`),
the name table `funcs` in
[ScriptingEngine](../../CoffeeMud/com/planet_ink/coffee_mud/Common/interfaces/ScriptingEngine.java),
and the helpers in [ScriptLib](../../CoffeeMud/com/planet_ink/coffee_mud/core/ScriptLib.java).
The original CoffeeMud wording is in
[Scriptable.html](../../CoffeeMud/guides/Scriptable.html); the CoffeeSUD
supplement is [ScriptingSUD.html](../../CoffeeMud/guides/ScriptingSUD.html).

## How functions and codes work

### Two jobs, one name

Every function name in the table serves two jobs, written two ways:

| Form | Where | Result |
| --- | --- | --- |
| **Test** `NAME(args)` | an `IF`, `ELSEIF` or `WHILE` condition, or inside `EVAL`-style tests | true or false |
| **Value** `$%NAME(args)%` | anywhere script text is expanded: `MPECHO`, `MPSETVAR`, command arguments, function arguments | text (often a number, a name, a list of names, or `true`/`false`) |

```text
IF LEVEL($n >= 10)
  MPECHO Welcome, veteran $n. You are level $%LEVEL($n)%.
ENDIF
```

The test form asks a question and often carries its own comparison inside the
parentheses (`LEVEL($n >= 10)`). The value form takes the same arguments
*without* the comparison and hands back the thing being compared
(`$%LEVEL($n)%` gives `12`). Use the value form with `EVAL`, `MATH` or `VAR`
when you need to compare two computed things:
`IF MATH($%LEVEL($n)% > $%LEVEL($i)%)`.

Names are case-insensitive. Arguments are separated by spaces. A word that must
contain spaces is wrapped in single quotes or backticks (`'a long sword'`).
Double quotes do *not* group words in MOBPROG. A function name must be followed
by `(`; spaces between the name and the `(` are tolerated.

A `$%...%` ends at the *first* `%` after it opens, so it cannot contain another
`$%...%`, and a `%` (modulus) cannot appear inside it. Compute the inner value
first (`MPSETVAR`, `MPARGSET` or a named local) and use that:

```text
MPSETVAR $i LASTROLL $%DICE(1d20)%
MPECHO You rolled $<$i LASTROLL>.
```

### The three shapes of a test

1. **Plain test**: no comparison. `ISNPC($n)`, `ISOPEN(north)`, `HASTATTOO($n RED_KEY)`.
2. **Comparison test**: the function computes a value and you compare it,
   with the operator as its own word inside the parentheses:
   `HITPRCNT($i < 50)`, `NUMMOBSROOM(orc >= 3)`, `VAR($i STAGE == 2)`.
3. **Searching test**: the argument is a name or phrase looked up in the thing
   (`HAS($n a long sword)`, `WORN($n sword)`, `ISNAME($t grumpy)`).

The functions from `LISTJOIN` to the end of the table share one generic rule:
write the comparison as `NAME(args cmp value)` and it compares the function's
value form against `value`; write it bare, `NAME(args)`, and it is true when the
value form is the word `true` (or, for `UNDERSTANDS`, `STATE`, `ARG`, `PARM` and
`EFFECTPARM`, when it is non-empty and not `0` or `false`). A bare test of a
function whose value is a number or a name (`WARRANTS(who)`) is therefore always
false; give it a comparison, `WARRANTS(who > 0)`. The comparison is numeric when
both sides are numbers and a case-insensitive text comparison otherwise. The
operators for this group are `==`, `=`, `!=`, `<>`, `>`, `<`, `>=`, `=>`, `<=`
and `=<`; `.in.` is not available for them. Older and the `FACTIONVALUE`..`MEMORY`
functions each parse their own arguments, as their entries show.

### Object arguments

Most functions begin with a *who* or *thing*. It may be:

- a `$` code: `$n` (the actor), `$i` (the host), `$t` (the target), `$o`/`$p`
  (event items), `$r` (a random player here), `$0`..`$9` (event arguments), and
  the others in [Dollar codes](#dollar-codes) that name an object;
- the name of a creature or item in the room, in the host's or actor's
  inventory, a player anywhere, or (for items) anywhere in the area then the
  world;
- an extended room ID (`Midgaard#3001`) for functions that accept rooms;
- a `$<host VAR>` that expands to one of the above.

"Who" arguments (creature-only functions) are resolved relative to the *host
creature*: on an item host the item's proxy creature is used, so `$i` still
works. A name that does not resolve makes the test false; it does not stop the
script. Syntax problems (a missing argument, an unknown operator) are logged to
the script error log and also give false; see [Troubleshooting](#troubleshooting).

### Inherited and added functions

The engine's `funcs` table lists 242 names. The first 118 (`RAND` through
`HASTAG`) are inherited from CoffeeMud 5.11 and behave as
[Scriptable.html](../../CoffeeMud/guides/Scriptable.html) describes, apart from
the fixes noted in the entries. The remaining 124 (`FACTIONVALUE` onward) are
CoffeeSUD additions; entries say "CoffeeSUD adds" where this matters.

Inherited functions that assume multiplayer still exist but have little
meaning in a single-player game: `IPADDRESS`, `ISPKILL`, `NUMPCSAREA`,
`AREAPC`, `ROOMPC` (a "player" is one of your party members), `HASACCTATTOO`
(the "account" is your profile), and the `CLAN*` family (clans are the
single-player organisations of [09-society.md](09-society.md)).

### Test versus value in JavaScript

A `<SCRIPT>` block (and JavaScript hosts) can call the same table: `NAME(args)`
runs the test and returns a boolean, `NAME$(args)` returns the value text. That
`$` suffix exists only in JavaScript, with two exceptions: `UNDERSTANDS$` and
`JS$` are real names in the MOBPROG table (see their entries). See
[05-javascript.md](05-javascript.md).

## By-purpose index

| Purpose | Functions |
| --- | --- |
| Chance, numbers, arithmetic | [RAND](#rand), [RANDNUM](#randnum), [RAND0NUM](#rand0num), [NUMBER](#number), [ISODD](#isodd), [MATH](#math), [EVAL](#eval), [DICE](#dice), [PICK](#pick), [WPICK](#wpick), [ROLLNORMAL](#rollnormal), [CLAMP](#clamp), [ABS](#abs) |
| Text | [STRIN](#strin), [STRCONTAINS](#strcontains), [STRSTARTS](#strstarts), [STRENDS](#strends), [STRPOS](#strpos), [UPPER](#upper), [LOWER](#lower), [CAP](#cap), [LEN](#len), [TRIM](#trim), [SUBSTR](#substr), [REPLACE](#replace), [PADRIGHT](#padright), [ANAME](#aname), [PLURAL](#plural) |
| Lists | [LISTJOIN](#listjoin), [LISTHAS](#listhas), [LISTCOUNT](#listcount), [LISTGET](#listget), [LISTFIND](#listfind), [LISTPICK](#listpick) |
| Variables, tags, glue | [VAR](#var), [VARNAMES](#varnames), [QVAR](#qvar), [STATE](#state), [HASTAG](#hastag), [TAGS](#tags), [TAGGED](#tagged), [NUMTAGGED](#numtagged), [HASTATTOO](#hastattoo), [HASTATTOOTIME](#hastattootime), [HASACCTATTOO](#hasacctattoo), [ARG](#arg), [PARM](#parm), [EFFECTPARM](#effectparm), [CALLFUNC](#callfunc), [JS](#js), [MEMORY](#memory), [GENVAR](#genvar) |
| Is it an NPC, alive, fighting... | [ISNPC](#isnpc), [ISPC](#ispc), [ISALIVE](#isalive), [ISFIGHT](#isfight), [ISIMMORT](#isimmort), [ISCHARMED](#ischarmed), [ISFOLLOW](#isfollow), [ISSERVANT](#isservant), [ISPKILL](#ispkill), [ISGOOD](#isgood), [ISEVIL](#isevil), [ISNEUTRAL](#isneutral), [ISBIRTHDAY](#isbirthday), [IPADDRESS](#ipaddress) |
| Name, kind, stats | [NAME](#name), [ISNAME](#isname), [SEX](#sex), [POSITION](#position), [LEVEL](#level), [CLASS](#class), [BASECLASS](#baseclass), [RACE](#race), [RACECAT](#racecat), [DEITY](#deity), [MOOD](#mood), [HASTITLE](#hastitle), [STAT](#stat), [GSTAT](#gstat), [HITPRCNT](#hitprcnt), [OBJTYPE](#objtype), [ISLIKE](#islike), [AFFECTED](#affected), [ISBEHAVE](#isbehave) |
| Skills, senses, languages | [ISABLE](#isable), [EXPERTISE](#expertise), [HASPERK](#hasperk), [KNOWSRECIPE](#knowsrecipe), [CANSEE](#cansee), [CANHEAR](#canhear), [ISSPEAKING](#isspeaking), [UNDERSTANDS](#understands), [BREATH](#breath), [CHECK](#check) |
| Advancement, money, standing | [EXP](#exp), [EXPTONEXT](#exptonext), [TRAINS](#trains), [PRACS](#pracs), [QUESTPOINTS](#questpoints), [GOLDAMT](#goldamt), [VALUE](#value), [CURRENCY](#currency), [MONEYSTR](#moneystr), [DENOMNAME](#denomname), [EXCHANGE](#exchange), [BANKBALANCE](#bankbalance), [DEBT](#debt), [FACTION](#faction), [FACTIONVALUE](#factionvalue), [FACTIONBAND](#factionband), [RANK](#rank), [AFFINITY](#affinity), [EXPLORED](#explored), [SETCOUNT](#setcount) |
| Items and containers | [HAS](#has), [HASNUM](#hasnum), [WORN](#worn), [WORNON](#wornon), [ITEMCOUNT](#itemcount), [MOBITEM](#mobitem), [NUMITEMSMOB](#numitemsmob), [INCONTAINER](#incontainer), [ISCONTENT](#iscontent), [ISOPEN](#isopen), [ISLOCKED](#islocked), [DOORSTATE](#doorstate), [SWITCHSTATE](#switchstate), [CONTENTWEIGHT](#contentweight) |
| Where, who is here | [INROOM](#inroom), [INAREA](#inarea), [ISRECALL](#isrecall), [INLOCALE](#inlocale), [ISHERE](#ishere), [ROOMMOB](#roommob), [ROOMITEM](#roomitem), [ROOMPC](#roompc), [AREAPC](#areapc), [NUMMOBSROOM](#nummobsroom), [NUMITEMSROOM](#numitemsroom), [NUMPCSROOM](#numpcsroom), [NUMPCSAREA](#numpcsarea), [NUMMOBS](#nummobs), [NUMMOBSINAREA](#nummobsinarea), [NUMRACES](#numraces), [NUMRACESINAREA](#numracesinarea), [ROOMWEIGHT](#roomweight), [RESPAWNROOM](#respawnroom), [ROOMSTATE](#roomstate), [ISDARK](#isdark), [ISLIT](#islit), [FLOODLEVEL](#floodlevel), [ISTIDE](#istide), [CATALOG](#catalog), [CATALOGCOUNT](#catalogcount) |
| Time and weather | [ISTIME](#istime), [ISHOUR](#ishour), [ISDAY](#isday), [ISMONTH](#ismonth), [ISYEAR](#isyear), [ISSEASON](#isseason), [ISWEATHER](#isweather), [ISMOON](#ismoon), [ISWEEKDAY](#isweekday), [WEEKDAY](#weekday), [DATETIME](#datetime), [GAMETIME](#gametime), [ELAPSED](#elapsed), [TICKS](#ticks), [TURNS](#turns), [ISRLHOUR](#isrlhour), [ISRLDAY](#isrlday), [ISRLMONTH](#isrlmonth), [ISRLYEAR](#isrlyear), [ISHOLIDAY](#isholiday), [HOLIDAY](#holiday) |
| Quests | [QUESTWINNER](#questwinner), [QUESTMOB](#questmob), [QUESTOBJ](#questobj), [ISQUESTMOBALIVE](#isquestmobalive), [QUESTROOM](#questroom), [QUESTAREA](#questarea), [QUESTSCRIPTED](#questscripted), [QUESTWINS](#questwins), [QUESTWINAGE](#questwinage), [QUESTSTATUS](#queststatus), [QUESTOUTCOME](#questoutcome), [QOBJECTIVE](#qobjective) |
| Game mode, party, saves | [ISBUILDER](#isbuilder), [ISDEV](#isdev), [ISTURNBASED](#isturnbased), [ISPAUSED](#ispaused), [SAVESLOT](#saveslot), [PARTYSIZE](#partysize), [INPARTY](#inparty), [PARTYLEADER](#partyleader), [PARTY](#party), [ISPLAYED](#isplayed) |
| Combat | [THREAT](#threat), [TOPTHREAT](#topthreat), [WEAKEST](#weakest), [OPPONENT](#opponent), [PHASE](#phase), [ALARM](#alarm) |
| Shops, law, property | [SHOPITEM](#shopitem), [NUMITEMSSHOP](#numitemsshop), [SHOPHAS](#shophas), [SHOPPRICE](#shopprice), [SHOPBUDGET](#shopbudget), [PRICE](#price), [ISWANTED](#iswanted), [WARRANTS](#warrants), [FINESOWED](#finesowed), [OWNER](#owner), [ISOWNER](#isowner), [LANDTITLE](#landtitle), [COMFORT](#comfort) |
| Clans and organisations | [CLAN](#clan), [CLANRANK](#clanrank), [CLANDATA](#clandata), [CLANQUALIFIES](#clanqualifies), [CLANPOS](#clanpos), [CLANMEMBER](#clanmember), [CLANS](#clans), [CLANRELATION](#clanrelation), [AREACLAN](#areaclan), [AREACONTROL](#areacontrol), [REVOLTCHANCE](#revoltchance) |
| Vessels and riding | [ABOARD](#aboard), [RIDERS](#riders), [ISANCHORED](#isanchored), [ISDOCKED](#isdocked), [COURSE](#course), [VESSELROOM](#vesselroom), [RIDING](#riding), [SPEED](#speed), [HULL](#hull) |
| Messages | [MSG](#msg) |
| Generators, names | [GENSTRING](#genstring), [RANDNAME](#randname), [GENVAR](#genvar) |

Functions are listed alphabetically in the engine; the reference below follows
the purpose groups above, which is the order a scripter usually needs them.

## Expression rules

### Where conditions are used

| Line | Meaning |
| --- | --- |
| `IF <condition>` ... `ELSEIF <condition>` ... `ELSE` ... `ENDIF` | branch; `ELSE IF <condition>` on one line is read as `ELSEIF` |
| `WHILE (<condition>) [max passes]` ... `ENDWHILE` | loop; the condition is parenthesised, the optional pass cap follows it and defaults to 1000 |
| `SWITCH <value>` / `CASE <value>` | compares a *value* (usually a `$%...%`), not a condition; see [27-mobprog-commands.md](27-mobprog-commands.md) |

The text after `IF` is parsed once into tokens and cached; each function's
arguments are expanded (`$` codes, `$%...%`, `$<...>`) every time the condition
is evaluated, so a loop or a later trigger sees fresh values. A line that does
not parse logs an `IF Syntax` error naming the problem (for example
`Unknown keyword: ...` when something that is not a function name, connector or
parenthesis appears where one is expected).

### Connectors: AND, OR, NOT

| Written | Meaning |
| --- | --- |
| `AND`, `OR` | combine the result so far with the next test |
| `NOT`, `!` | negate the next test (`!HAS($n key)` is the same as `NOT HAS($n key)`) |
| `AND NOT`, `OR NOT` (also written `ANDNOT`, `ORNOT`) | combine with the negation of the next test |
| `( ... )` | group; groups nest |
| two tests side by side with no connector | treated as `AND` |

Connectors are case-insensitive. **Evaluation is strictly left to right: there is
no precedence of `AND` over `OR`.** `A OR B AND C` means `(A OR B) AND C`. Always
parenthesise when you mix them. (The inherited Scriptable.html example that says
`sex($n == M) or isevil($n) and race($n == Harpy)` is true for a good male gnome
is wrong for this engine: the code folds the result left to right and gives
false.)

```text
IF (RACE($n == Dwarf) OR RACE($n == Gnome)) AND !ISEVIL($n)
  MPECHO The mountain folk are welcome here, $n.
ENDIF
```

### Comparison operators

An operator is a separate word inside the parentheses.

| Operator | Meaning |
| --- | --- |
| `==` or `=` | equal |
| `!=` or `<>` | not equal |
| `>` , `<` | greater, less |
| `>=` or `=>` , `<=` or `=<` | greater or equal, less or equal |
| `.in.` (also `.IN.`) | the left value appears as a whole word (or phrase) inside the right value; only in the functions that use the shared comparison helpers (`NAME`, `STAT`, `GSTAT`, `NUM*`, `HITPRCNT`, `LEVEL`, ...), not in the `LISTJOIN`..`UNDERSTANDS$` group |

A comparison in a function that does not use the operator you wrote logs a
`Syntax` error and gives false.

### Number, text and decimal comparisons

The comparison engines differ by function, and mixing them up is the commonest
source of "it always says true" bugs.

| Engine | Used by | Rule |
| --- | --- | --- |
| Integer | `LEVEL`, `HITPRCNT`, `EXP`, `TRAINS`, `PRACS`, `QUESTPOINTS`, `GOLDAMT`, `VALUE`, `ITEMCOUNT`, `HASNUM`, `EXPLORED`, `FACTION`, `FACTIONVALUE`, `NUM*` counts, `DATETIME`, `GAMETIME`, `TURNS`, `PARTYSIZE`, `HASTATTOOTIME`, `EXPERTISE`, `CLANRANK` (legacy form), `RANDNUM`, `RAND0NUM`, `VAR` and `EVAL` for `<`, `>`, `<=`, `>=`, `STAT`/`GSTAT`/`MEMORY`/`CLANDATA` when the value is a number | both sides are read as whole numbers (`Long.parseLong`). Anything else, **including `2.5`, an empty value or a word, reads as 0.** |
| Text | `NAME`, `CURRENCY`, `SAVESLOT`, `VARNAMES`, `GENVAR`, `PARTYLEADER`, `PARTY`, `FACTIONBAND`, `QVAR`, `INROOM`, `INAREA`, `ISRECALL`, `STAT`/`GSTAT` for `==` `!=`, `MEMORY` and `CLANDATA` when not numeric | case-insensitive; `>` and `<` order alphabetically; `.in.` is whole-word containment |
| Exact text | `VAR` and `EVAL` for `==`, `=`, `!=`, `<>` | **case-sensitive** equality of the two expanded strings |
| Decimal | `MATH` | both sides are arithmetic expressions evaluated as decimals (see [MATH](#math)); `==` is exact on the decimal result |
| Generic | the `LISTJOIN`..`UNDERSTANDS$` group | numeric if both sides are numbers (decimals allowed), else case-insensitive text |
| Name words | `CLASS`, `BASECLASS`, `RACE`, `RACECAT`, `POSITION`, `SEX`, `OBJTYPE`, `DEITY`, `MOOD`, `CLAN` | only `==` and `!=` (`CLAN` also takes `IN` and `NOTIN`). `CLASS`, `BASECLASS`, `RACE`, `RACECAT` and `POSITION` are true when the creature's value *starts with* your word (`CLASS($n == Mag)` matches Mage); `SEX` is true when your word starts with the gender letter (`M`, `F`, `N`); `OBJTYPE` when the Java class ID *contains* your word; `DEITY`, `MOOD` and `CLAN` need the whole name |

To compare decimals or arithmetic, use `MATH`. To compare two computed numbers
use `EVAL` or `MATH` with value forms:
`IF EVAL($%NUMMOBSROOM(*)% > $%NUMPCSROOM()%)`.

### Quoting and escaping

- Words are separated by spaces. `'two words'` or `` `two words` `` keeps them
  together. The closing quote must be followed by a space or the end of the
  text, so an apostrophe inside a word (`don't`) is safe. Double quotes do not
  group.
- Inside function parentheses, parentheses nest, and quoted sections are skipped
  when looking for the closing `)`.
- A `$` preceded by `\` is not expanded (the backslash is left in the text).
  `$$` produces one literal `$`.
- If a substituted value itself contains `$x`, the `$` is replaced by `_` so a
  player cannot inject codes by typing them (`$n` set to "$i" would otherwise
  recurse).
- Case: function names, connectors, flag words (`DAYS`, `WORN`...) and most
  compared words are case-insensitive. Variable names are stored in upper case.

### Nesting

- `$%FUNC(...)%` can sit anywhere a `$` code can, including as an argument of a
  test: `IF NUMMOBSROOM(orc > $%RAND0NUM(3)%)`.
- `$%...%` cannot contain `$%...%` or a literal `%`; use an intermediate
  variable or named local (`MPSETVAR`, `MPLOCAL`; `$(name)`).
- Codes inside a `$<host VAR>` host: `$<$n QUEST_STAGE>` reads the actor's
  variable; `$<@$n LASTVISIT>` reads the per-object variable stored on the
  actor itself.

## Dollar codes

`varify` expands these in every command line, trigger argument list and
function argument before the command or function sees it. Absent things expand
to the empty string. "Actor" is the creature that caused the event (`$n`),
"target" the creature the event was aimed at (`$t`), "host" the scripted thing.
Codes the engine also accepts as *object arguments* of functions
(`HAS($n ...)`, `INROOM($t == ...)`) are marked **obj**.

| Code | Expands to | Notes |
| --- | --- | --- |
| `$0`..`$9` | trigger or function argument number N | text form: a name, a room's title, or a quoted list of names. **obj** when the argument is an object. `$9` also receives the formatted price after a successful [SHOPHAS](#shophas) or [SHOPITEM](#shopitem) test (`5g`, `20xp`, `3qp`). What each trigger puts in `$0`..`$9` is in [26-mobprog-triggers.md](26-mobprog-triggers.md) |
| `$$` | one literal `$` | |
| `$@x` | the identity string of the object code `$x` names (`null` if none) | for example `$@n`; a key, not for display; it changes every load |
| `$a` | the name of the area the host is in | **obj** (the area). `$A` expands to nothing |
| `$b` | name of the last object created by `MPMLOAD`/`MPOLOAD` | **obj** (`$b` or `$B`) |
| `$B` | that object's display (long) text | |
| `$c`, `$C` | name of a random creature in the room other than the host | picked once per script run and kept; a follower of a player is replaced by the player it follows. **obj** |
| `$d` | the room's title as the host sees it | **obj** (the room) for `$d` and `$D` |
| `$D` | the room's description | |
| `$e` | actor's subject pronoun (he, she, it) | |
| `$E` | target's subject pronoun | empty if the target is not a creature |
| `$f` | name of the creature the host is following | **obj** (`$f`/`$F`) |
| `$F` | that creature's subject pronoun | |
| `$g` | the trigger text, lower case | for `SPEECH_PROG` the words spoken; each trigger defines it |
| `$G` | the trigger text, as typed | |
| `$h` | host's object pronoun (him, her, it) | |
| `$H` | a random player's object pronoun | |
| `$i` | the host creature's name | **obj**: the host itself |
| `$I` | the host creature's display (long) text | **obj** |
| `$j` | host's subject pronoun | |
| `$J` | a random player's subject pronoun | |
| `$k` | host's possessive (his, her, its) | |
| `$K` | a random player's possessive | |
| `$l` | the visible creatures in the room except the host, each in double quotes, space separated | usable in `FOR $0 IN $l`-style loops |
| `$L` | the visible items lying loose in the room, quoted, space separated | |
| `$m` | actor's possessive | |
| `$M` | target's possessive | |
| `$n`, `$N` | actor's name | **obj** |
| `$o`, `$O` | primary item's name | **obj**; the item the event concerns; defaults to the host when the host is an item |
| `$p`, `$P` | secondary item's name | **obj** |
| `$PARMS` | a `GenAbility` effect's own instance text | CoffeeSUD adds; empty elsewhere |
| `$q` | name of the scripted host (creature, item, room, area or exit) | **obj** (the host; `$q`/`$Q`) |
| `$Q` | the host's display text | |
| `$r`, `$R` | name of a random player in the room | picked once per run; players that follow another player count as that leader. **obj** |
| `$s` | actor's object pronoun | |
| `$S` | target's object pronoun | |
| `$t`, `$T` | target's name | **obj** |
| `$u`, `$U` | name of the character at the terminal (the one being played now) | CoffeeSUD adds; **obj**; see [31-saves-and-party.md](31-saves-and-party.md) |
| `$w` | name of the owner (holder) of `$o` | **obj** (`$w`, `$W`) |
| `$W` | name of the owner of `$p` | |
| `$x` | direction name of a random exit of the room, or of the exit in direction `d` when written `$xn`, `$xs`, `$xe`, `$xw`, `$xu`, `$xd` | **obj** gives the exit itself |
| `$X` | name of that exit (its door name) | same direction letter rule |
| `$y` | actor's "sir"/"madam" | |
| `$Y` | target's | |
| `$(name)` | the named local variable | CoffeeSUD adds; set with `MPLOCAL name value` or bound by a `FUNCTION_PROG name PARAM1 PARAM2` header; lasts while the body runs, see [27-mobprog-commands.md](27-mobprog-commands.md) |
| `$<host VAR>` | the saved script variable VAR of `host` | host is a name, a code (`$n`, `$i`), `*` (any host), or `@$n` for a variable kept on that very object; see [04-mobprog.md](04-mobprog.md) |
| `$[N quest]` | name of item number N of the quest | **obj** gives the item; N from 1 |
| `${N quest}` | name of mob number N of the quest | **obj** gives the mob |
| `$%FUNC(args)%` | the value form of a function | see [How functions and codes work](#how-functions-and-codes-work) |

Suffixes work on any code, `$<>` and `$%%` and are applied after expansion:

| Suffix | Result | Example |
| --- | --- | --- |
| `.N` | word number N (from 0), words split on spaces with quotes honoured | `$g.0` first word spoken |
| `.N..` | word N through the end | `$g.1..` everything after the first word |
| `.LENGTH#` | number of words | `$l.LENGTH#` creatures present besides the host |
| `.DECOMMA#` | a comma list turned into quoted words | `$<$i SUSPECTS>.DECOMMA#` |
| `.DESEMICOLON#` | a semicolon list turned into quoted words | |

An object argument may also carry an index: when `$N` holds a list, `$N.2` is
its third element as an object.

## Function reference

Each entry gives the **test** form (for `IF`/`WHILE`), the **value** form
(`$%NAME(args)%`), an example and notes. "who" is a creature argument, "thing"
any object (see [Object arguments](#object-arguments)); "cmp" is a comparison
operator from [Comparison operators](#comparison-operators); "n" is a number.
When an entry says "not inherited" the function is a CoffeeSUD addition.

### Chance, numbers and arithmetic

### RAND

- **Test:** `RAND(percent)`: true with about that chance. A trailing `%` is allowed and ignored.
- **Value:** `$%RAND()%`: a fresh roll from 1 to 100.
- **Example:** `IF RAND(25)`
- **Notes:** the test is true when a roll of 1..100 is *below* the number, so `RAND(50)` is true 49 times in 100 and `RAND(100)` is true 99 times in 100.

### RANDNUM

- **Test:** `RANDNUM(max cmp n)`: rolls 1..max and compares the roll.
- **Value:** `$%RANDNUM(max)%`: a roll from 1 to max.
- **Example:** `IF RANDNUM(6 >= 5)` (true one time in three)
- **Notes:** `max` is a whole number or an arithmetic expression (as `MATH`). If it is not an expression, the number of *words* in it is used, so `RANDNUM($<WORLD SUSPECTS>)` picks a position in a space-separated list. `n` is read the same way. See also [RAND0NUM](#rand0num).

### RAND0NUM

- **Test:** `RAND0NUM(max cmp n)`: rolls 0..max-1 and compares.
- **Value:** `$%RAND0NUM(max)%`: a roll from 0 to max-1.
- **Example:** `IF RAND0NUM(3 == 0)`; `MPSETVAR $i PICK $%RAND0NUM($<$i LINES>.LENGTH#)%` then `MPECHO $<$i LINES>.$<$i PICK>`
- **Notes:** like [RANDNUM](#randnum) but zero-based, which suits the `.N` word suffix (`$<$i LINES>.2`).

### NUMBER

- **Test:** `NUMBER(text)`: true if the expanded text is a number, including a sign and decimals (`-2.5`).
- **Value:** `$%NUMBER(text)%`: the number as a whole number, or empty if it is not a number.
- **Example:** `IF NUMBER($g)`
- **Notes:** the value form converts with a whole-number parse, so `$%NUMBER(2.5)%` gives `0`; use the test to check and `MATH` to compute.

### ISODD

- **Test:** `ISODD(n)`: true for an odd whole number.
- **Value:** `$%ISODD(n)%`: the number if it is odd, otherwise empty.
- **Example:** `IF ISODD($%DATETIME(DAY)%)`
- **Notes:** negative odd numbers are not recognised as odd (the check is `n % 2 == 1`).

### MATH

- **Test:** `MATH('expr1' cmp 'expr2')`: both sides are arithmetic expressions, compared as decimals.
- **Value:** `$%MATH(expr)%`: the result rounded to the nearest whole number.
- **Example:** `IF MATH($%LEVEL($n)%+5 > 20)`; `MPSETVAR $i HP $%MATH($(n) * 3 + 1)%`
- **Notes:** in the test form the expression is one word: quote it if it has spaces. Operators evaluate strictly **left to right with no precedence**: `+ - * / \` (divide), `%` (modulus), `^` (power), `?` (random between: `1?6` is a random decimal from about 1 to 6.5), `<` (smaller of two), `>` (larger of two), `=` (1 if equal else 0) and `!` (1 if different). Parentheses group. `$` codes expand first. Because `%` ends a `$%...%`, modulus is only available in the test form.

### EVAL

- **Test:** `EVAL(left cmp right)`: compares two expanded values.
- **Value:** `$%EVAL(left cmp right)%`: `true` or `false`.
- **Example:** `IF EVAL('$g' == 'yes')`; `IF EVAL($%NUMMOBSROOM(*)% > 3)`
- **Notes:** `left` is one word (quote it if it has spaces), `right` is the rest. `==`, `=`, `!=`, `<>` compare the two strings *exactly and case-sensitively*; the other operators compare as whole numbers. Both sides are expanded first, so `$<...>` and `$%...%` work.

### DICE

- **Test:** `DICE(NdM+K cmp n)`: rolls and compares.
- **Value:** `$%DICE(NdM+K)%`: the total.
- **Example:** `IF DICE(1d20+3 >= 15)`; `MPSETVAR $n DAMAGE $%DICE(2d6+1)%`
- **Notes:** not inherited. `N` defaults to 1, `+K`/`-K` is optional, spaces are ignored; a plain number returns itself. A count or die size of 0 or less returns just the modifier.

### PICK

- **Value:** `$%PICK(word word "two words")%`: one of the words at random.
- **Test:** `PICK(list == word)`: the pick compared with a word (rarely useful).
- **Example:** `MPECHO The guard mutters about $%PICK(rain taxes "the old days")%.`
- **Notes:** not inherited. Words are split on spaces; double quotes group a phrase.

### WPICK

- **Value:** `$%WPICK(common:70 rare:25 legendary:5)%`: a weighted random pick.
- **Example:** `MPOLOAD $%WPICK(ring_copper:70 ring_silver:25 ring_jade:5)%`
- **Notes:** not inherited. Each word is `name:weight`; a word without a weight counts 1; weight 0 is never picked; `""` is returned if the weights total 0. Names cannot contain spaces unless quoted with double quotes.

### ROLLNORMAL

- **Value:** `$%ROLLNORMAL(number die modifier)%`: a roll of `number` dice of `die` sides drawn from a normal (bell-curve) distribution, plus the optional modifier.
- **Example:** `MPSETVAR $i HITS $%ROLLNORMAL(3 6 0)%`
- **Notes:** not inherited. Fewer than two numbers returns `0`.

### CLAMP

- **Value:** `$%CLAMP(value low high)%`: `value` limited to the range.
- **Example:** `MPSETVAR $n MORALE $%CLAMP($<$n MORALE> 0 10)%`
- **Notes:** not inherited. Decimals are kept; whole results print without `.0`. Fewer than three numbers returns the text unchanged.

### ABS

- **Value:** `$%ABS(n)%`: the absolute value.
- **Example:** `MPSETVAR $i DIFF $%ABS(-4)%` stores `4`.
- **Notes:** not inherited. Whole results print without a decimal point.

### Text and lists

### STRIN

- **Test:** `STRIN(list word)`: true if `word` is one of the whole words of `list`.
- **Value:** `$%STRIN(list word)%`: the 0-based position of the word in the list, or `-1`.
- **Example:** `IF STRIN('$<WORLD SUSPECTS>' $n)`; `IF STRIN('midgaard#3001 midgaard#3002' $$r)`
- **Notes:** the list is the first word (quote it if it has spaces; quoted phrases inside use double quotes). `word` may end or start with `*` for a prefix, suffix or substring search (`STRIN($g wiz*)`); escape a literal star as `\*`. `$$r` as the word stands for the host's current room ID. The match ignores case.

### STRCONTAINS

- **Test:** `STRCONTAINS(text pattern)`: true if the text matches the word pattern.
- **Value:** `$%STRCONTAINS(text pattern)%`: `true` or `false`.
- **Example:** `IF STRCONTAINS($g '(sword | axe) & buy')`
- **Notes:** case-insensitive and ignores punctuation. In the pattern, `a b` means the words `a` then `b` immediately after; `a & b` both in any order; `a | b` either; `a > b` both, `b` later; `a < b` both, `b` earlier; parentheses group. The text is the first word: quote a literal that has spaces; `$g` needs no quotes because it is expanded after the words are split.

### STRSTARTS

- **Test:** `STRSTARTS(text start)`: true if the text begins with `start`.
- **Value:** `$%STRSTARTS(text start)%`: `true` or `false`.
- **Example:** `IF STRSTARTS($g hello)`
- **Notes:** not inherited. The last word is the prefix; everything before it is the text. Case-insensitive. Works with `!`: `IF !STRSTARTS($g hello)`.

### STRENDS

- **Test:** `STRENDS(text end)`
- **Value:** `$%STRENDS(text end)%`: `true` or `false`.
- **Example:** `IF STRENDS($n the Bold)` (quote as `STRENDS($n 'the Bold')` for a phrase)
- **Notes:** not inherited; same argument rule and case rule as [STRSTARTS](#strstarts).

### STRPOS

- **Value:** `$%STRPOS(text part)%`: the 0-based character position of `part` in `text`, or `-1`.
- **Test:** `STRPOS(text part cmp n)`
- **Example:** `IF STRPOS($g where >= 0)`
- **Notes:** not inherited. Case-insensitive; the last word is the part searched for.

### UPPER

- **Value:** `$%UPPER(text)%`: the text in capitals.
- **Example:** `MPECHO $%UPPER($n)% HAS ARRIVED`
- **Notes:** not inherited; as are [LOWER](#lower), [CAP](#cap), [LEN](#len), [TRIM](#trim), [SUBSTR](#substr), [REPLACE](#replace), [PADRIGHT](#padright), [ANAME](#aname), [PLURAL](#plural). The test form of these value functions is only useful with a comparison (`LEN($g > 20)`).

### LOWER

- **Value:** `$%LOWER(text)%`
- **Example:** `IF EVAL($%LOWER($g)% == 'yes')`
- **Notes:** `$g` is already lower case; `$G` is the original text.

### CAP

- **Value:** `$%CAP(text)%`: the first letter capitalised, the rest unchanged.
- **Example:** `$%CAP(hello there)%` is `Hello there`.

### LEN

- **Value:** `$%LEN(text)%`: the number of characters (after trimming).
- **Test:** `LEN(text cmp n)`
- **Example:** `IF LEN($g > 40)`

### TRIM

- **Value:** `$%TRIM(text)%`: the text without leading and trailing spaces.
- **Example:** `MPSETVAR $i TIDY $%TRIM($<$i RAW>)%`
- **Notes:** the function's own parentheses already trim the argument, so this matters mostly for text built up inside quotes.

### SUBSTR

- **Value:** `$%SUBSTR(text start [length])%`: part of the text, counting from 0.
- **Example:** `$%SUBSTR(abcdef 2 3)%` is `cde`; `$%SUBSTR(abcdef 4)%` is `ef`.
- **Notes:** a `start` beyond the end gives an empty string; a `length` past the end is cut short. The last one or two words, if they are whole numbers, are the start and length; everything before them is the text.

### REPLACE

- **Value:** `$%REPLACE(text 'old' 'new')%`: every `old` replaced by `new` (case-sensitive).
- **Example:** `$%REPLACE(a-b-c '-' '+')%` is `a+b+c`.
- **Notes:** the last two words are `old` and `new`; quote them (single quote, backtick or double quote) if they hold spaces. An empty `old` returns the text unchanged.

### PADRIGHT

- **Value:** `$%PADRIGHT(text width)%`: the text padded with spaces on the right to `width` characters.
- **Example:** `[$%PADRIGHT(ab 4)%]` gives `[ab  ]`.

### ANAME

- **Value:** `$%ANAME(text)%`: the text with "a" or "an" in front, chosen by its first letter.
- **Example:** `$%ANAME(apple)%` is `an apple`.

### PLURAL

- **Value:** `$%PLURAL(text [n])%`: the plural of the text; with `n`, the text unchanged when `n` is 1, the plural otherwise.
- **Example:** `$%PLURAL(sword 2)%` is `swords`; `$%PLURAL(sword 1)%` is `sword`.

### LISTJOIN

- **Value:** `$%LISTJOIN(list separator)%`: the items of the list joined with the separator.
- **Example:** `MPECHO The suspects: $%LISTJOIN($<WORLD SUSPECTS> ', ')%.`
- **Notes:** not inherited. A *list* is split on commas if it contains any, otherwise on spaces; backticks or double quotes keep a multi-word item together. The last word is the separator. The `LIST*` functions are the readable way to work on space- or comma-separated variables; the commands that change lists are in [27-mobprog-commands.md](27-mobprog-commands.md).

### LISTHAS

- **Test:** `LISTHAS(list word)`: true if an item equals the word (case-insensitive).
- **Value:** `$%LISTHAS(list word)%`: `true` or `false`.
- **Example:** `IF LISTHAS($<WORLD SUSPECTS> butler)`

### LISTCOUNT

- **Value:** `$%LISTCOUNT(list)%`: how many items.
- **Test:** `LISTCOUNT(list cmp n)`
- **Example:** `IF LISTCOUNT($<WORLD SUSPECTS> == 3)`

### LISTGET

- **Value:** `$%LISTGET(list n)%`: item number `n`, counting from 0; empty if out of range.
- **Example:** `MPECHO First suspect: $%LISTGET($<WORLD SUSPECTS> 0)%`

### LISTFIND

- **Value:** `$%LISTFIND(list word)%`: the 0-based position of the word in the list, or `-1`.
- **Test:** `LISTFIND(list word cmp n)`
- **Example:** `IF LISTFIND($<WORLD SUSPECTS> maid >= 0)`

### LISTPICK

- **Value:** `$%LISTPICK(list)%`: a random item.
- **Example:** `MPSETVAR WORLD CULPRIT $%LISTPICK($<WORLD SUSPECTS>)%`

