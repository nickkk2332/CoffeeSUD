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
- **Example:** `IF RAND0NUM(3 == 0)`; `MPSETVAR $i PICK $%RAND0NUM($<$i LINES>.LENGTH#)%`
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

### Variables, tags and script glue

### VAR

- **Test:** `VAR(host NAME cmp value)`: reads the saved script variable `NAME` kept for `host` and compares it.
- **Value:** `$%VAR(host NAME)%`: the variable's text (the same as `$<host NAME>`); empty when unset.
- **Example:** `IF VAR($n STAGE == 2)`; `IF VAR(@$i BRIBED == yes)`; `IF VAR(* ALARM == ON)`
- **Notes:** `host` is a creature or item (`$n`, `$i`, a name), a room ID, `*` (any host that has the variable), or `@code` for a variable stored on that exact object (`@$i`). `==`/`=`/`!=`/`<>` compare the two texts *exactly and case-sensitively*; the other operators compare as whole numbers, where an unset or non-numeric variable is 0. The variable name is upper-cased. If the value contains spaces, the word after the operator is the whole rest of the line, so no quotes are needed. How variables are stored and scoped (script, quest, world, per-object) is in [04-mobprog.md](04-mobprog.md); setting them is `MPSETVAR`, in [27-mobprog-commands.md](27-mobprog-commands.md).

### VARNAMES

- **Test:** `VARNAMES(host prefix cmp value)`: the sorted, space-separated names of `host`'s variables starting with `prefix`, compared as text (`.in.` works).
- **Value:** `$%VARNAMES(host prefix)%`: that list; an empty host means any host (`*`).
- **Example:** `IF VARNAMES($n QUEST_ .in. QUEST_ONE)`; `FOR $0 IN $%VARNAMES($n QUEST_)%`
- **Notes:** not inherited. Names come back upper case. Use with `FOR` to walk a family of variables.

### QVAR

- **Test:** `QVAR(quest STAT cmp value)`: compares a stat of a quest as text.
- **Value:** `$%QVAR(quest STAT)%`
- **Example:** `IF QVAR(rescue STEPNAME == spared)`
- **Notes:** `quest` is a quest name (`*` for the quest this script belongs to). `STAT` is any quest stat (`NAME`, `DURATION`, `FINISHED`, `REPEATABLE`, `MAXWINS`, `REMAINING`, `STEPNAME`, `STEPOF:label`, ...) or a variable the quest script set. An unknown quest gives false. See [08-quests.md](08-quests.md).

### STATE

- **Test:** `STATE(thing NAME)`: true if the value kept on that one object is set (not empty, not `0`, not `false`); `STATE(thing NAME cmp value)` compares it.
- **Value:** `$%STATE(thing NAME)%`
- **Example:** `IF STATE($i open)`; `MPSTATE $i open clear` (command) clears it.
- **Notes:** not inherited. Per-object state lives on the object and moves and dies with it, unlike `VAR`, which is keyed by name. See [27-mobprog-commands.md](27-mobprog-commands.md) for `MPSTATE`.

### HASTAG

- **Test:** `HASTAG(thing tag)`: true if the object carries that tag.
- **Value:** `$%HASTAG(thing)%`: all the object's tags, space separated, quoted if they contain spaces.
- **Example:** `IF HASTAG($i sentry)`
- **Notes:** tags can be on creatures, items, rooms and exits. A syntax error is logged under the label `HASTATTOO`. `TAGS` is the clearer name for the value form.

### TAGS

- **Value:** `$%TAGS(thing)%`: the object's tags, space separated, quoted if needed.
- **Test:** `TAGS(thing cmp text)`
- **Example:** `MPECHO Tags: $%TAGS($i)%`
- **Notes:** not inherited; the same list `$%HASTAG(thing)%` gives.

### TAGGED

- **Test:** `TAGGED(tag)`: true if any object in the world has the tag.
- **Value:** `$%TAGGED(tag)%`: `true` or `false`.
- **Example:** `IF TAGGED(grateful) AND !TAGGED(hunted)`
- **Notes:** not inherited. Counts creatures, items, rooms and exits that carry the tag. `FOR $0 IN #tag` loops over them (see [27-mobprog-commands.md](27-mobprog-commands.md)).

### NUMTAGGED

- **Value:** `$%NUMTAGGED(tag)%`: how many objects carry the tag.
- **Test:** `NUMTAGGED(tag cmp n)`
- **Example:** `IF NUMTAGGED(grateful >= 5)`
- **Notes:** not inherited.

### HASTATTOO

- **Test:** `HASTATTOO(who tattoo)`: true if the creature carries the tattoo (a named flag set with `MPTATTOO`).
- **Value:** `$%HASTATTOO(who)%`: all the creature's tattoo names, space separated.
- **Example:** `IF HASTATTOO($n KNOWS_TOWER)`; `IF !HASTATTOO($n SEEN_ELDER)`
- **Notes:** the tattoo name is matched whole, ignoring case. Tattoos are saved with the character and are the standard once-per-player flag. A syntax error (empty tattoo) is logged. Timed tattoos: [HASTATTOOTIME](#hastattootime).

### HASTATTOOTIME

- **Test:** `HASTATTOOTIME(who tattoo cmp ticks)`: compares the ticks left on a timed tattoo (as set by `MPTATTOO who 'ticks NAME'`).
- **Value:** `$%HASTATTOOTIME(who tattoo)%`: the ticks left; empty if the creature has no such tattoo.
- **Example:** `IF HASTATTOOTIME($n COOLDOWN < 15)`
- **Notes:** false if the tattoo is not present. Use [TICKS](#ticks) to convert game time to ticks.

### HASACCTATTOO

- **Test:** `HASACCTATTOO(who tattoo)`: true if the creature's *account* tattoo list has the tattoo.
- **Value:** `$%HASACCTATTOO(who)%`: the account's tattoo names, space separated.
- **Example:** `IF HASACCTATTOO($n BEASTMARK)`
- **Notes:** CoffeeSUD has no logins; the "account" is the single-player profile, shared by all characters in it. `$` codes are allowed in the tattoo name. Set with `MPACCTATTOO`.

### ARG

- **Test:** `ARG(NAME)`: true if the argument is set (not empty, not `0`, not `false`); `ARG(NAME cmp value)` compares it.
- **Value:** `$%ARG(NAME)%`: the value given by `LOAD=file NAME=value`, else the file's `ARGDEFAULT NAME=value`, else empty.
- **Example:** `LOAD=progs/guard.script POST=castle#12` on the host and `IF ARG(POST == castle#12)` in the file
- **Notes:** not inherited. This is how one script file serves many hosts. A generic behaviour's parameters are read the same way: [PARM](#parm).

### PARM

- **Test:** `PARM(NAME)` or `PARM(NAME cmp value)`, as [ARG](#arg).
- **Value:** `$%PARM(NAME)%`: the parameter of the generic behaviour (or the `LOAD=` argument) of that name.
- **Example:** `MPECHO The price is $%PARM(price)% crowns.`
- **Notes:** not inherited; it shares one lookup with `ARG`: the generic behaviour's parameters first, then `LOAD=` arguments.

### EFFECTPARM

- **Test:** `EFFECTPARM(KEY)` or `EFFECTPARM(KEY cmp value)`.
- **Value:** `$%EFFECTPARM(KEY)%`: the `KEY=VALUE` instance text of a `GenAbility` effect that is running this script; `EFFECTPARM(*)` gives all of it.
- **Example:** `IF EFFECTPARM(FREQ == 5)`
- **Notes:** not inherited. Empty outside a `GenAbility` script. `$PARMS` is the whole text as a code.

### CALLFUNC

- **Test:** `CALLFUNC(name args)`: runs the host's own `FUNCTION_PROG name` (or a JavaScript function of that name) with `args` as its `$g`; true if it returned any text other than `CANCEL`.
- **Value:** `$%CALLFUNC(name args)%`: what the function returned (`RETURN text`). `$%CALLFUNC(target name args)%` runs the function on another script host (room ID, `TAG:tag`, object name).
- **Example:** `IF CALLFUNC(is_open $n)`; `MPECHO The gate stands at $%CALLFUNC(Gatehouse height)% feet.`
- **Notes:** the cross-script form exists only in the value form; the test form finds only the host's own functions. See `FUNCTION_PROG` in [26-mobprog-triggers.md](26-mobprog-triggers.md) and `MPCALLFUNC` in [27-mobprog-commands.md](27-mobprog-commands.md).

### JS

- **Test:** `JS(expression)`: runs a JavaScript expression and is true if the result is truthy.
- **Value:** `$%JS(expression)%` or `$%JS$(expression)%`: the result as text (whole numbers without a decimal point, nothing for `undefined`).
- **Example:** `IF JS(1 > 2)`; `IF JS(('' + cm.host().Name()).length > 3)`; `MPECHO The host is $%JS(host().Name())%`; `$%JS$(6 * 7)%` is `42`
- **Notes:** not inherited. The expression runs in the same scope as a `<SCRIPT>` block (`host()`, `source()`, `target()`, `cm` and so on) and is subject to the same JavaScript approval rule; an unapproved expression logs a `Security` error and is false/empty. `JS$` is a second spelling of the value form. See [05-javascript.md](05-javascript.md).

### MEMORY

- **Test:** `MEMORY(npc who field cmp value)`: what an NPC remembers about a character.
- **Value:** `$%MEMORY(npc who field)%`
- **Example:** `IF MEMORY($i $n MET == true)`; `IF MEMORY($i $n DAYSSINCE >= 7)`
- **Notes:** not inherited. `field` is `MET` (`true`/`false`), `COUNT` (meetings), `LASTSEEN` (game time stamp), `DAYSSINCE` (game days since last seen, or `-1` if never met) or `LASTTOPIC`. Numbers compare as numbers, anything else as text. See [06-npcs-dialogue.md](06-npcs-dialogue.md).

### GENVAR

- **Test:** `GENVAR(area NAME cmp value)`: compares a variable a generator recorded for an area it made, as text.
- **Value:** `$%GENVAR(area NAME)%`
- **Example:** `IF GENVAR('The Old Crypt' AREA_SIZE >= 20)`
- **Notes:** not inherited. Empty for an area no generator made. See [12-generation.md](12-generation.md).

### Creatures: who or what someone is

### ISNPC

- **Test:** `ISNPC(who)`: true if the creature exists and is not player-controlled.
- **Value:** `$%ISNPC(who)%`: `true` or `false`.
- **Example:** `IF ISNPC($n)`
- **Notes:** "player-controlled" means the hero and the party members; every other creature is an NPC.

### ISPC

- **Test:** `ISPC(who)`: true if the creature is player-controlled (the opposite of `ISNPC` for a creature that exists).
- **Value:** `$%ISPC(who)%`: `true` or `false`.
- **Example:** `IF ISPC($n)`

### ISALIVE

- **Test:** `ISALIVE(who)`: true if the creature exists and is not dead.
- **Value:** `$%ISALIVE(who)%`: the creature's health in words while alive, or `<name> is dead.`; empty if there is no such creature.
- **Example:** `IF !ISALIVE($t)`

### ISFIGHT

- **Test:** `ISFIGHT(who)`: true if the creature is in combat.
- **Value:** `$%ISFIGHT(who)%`: the name of its current opponent, empty if not fighting.
- **Example:** `IF ISFIGHT($i) AND HITPRCNT($i < 25)`

### ISIMMORT

- **Test:** `ISIMMORT(who)`: true if the creature holds the IMMORT security flag (a game-master or cheat-mode character).
- **Value:** `$%ISIMMORT(who)%`: `true` or `false`.
- **Example:** `IF !ISIMMORT($n)`

### ISCHARMED

- **Test:** `ISCHARMED(who)`: true if the creature has an effect flagged as charming.
- **Value:** `$%ISCHARMED(who)%`: the names of those effects, each followed by a space.
- **Example:** `IF ISCHARMED($t)`

### ISFOLLOW

- **Test:** `ISFOLLOW(who)`: true if the creature follows someone who is in the host's room.
- **Value:** `$%ISFOLLOW(who)%`: the leader's name, if in the room.
- **Example:** `IF ISFOLLOW($n)`

### ISSERVANT

- **Test:** `ISSERVANT(who)`: true if the creature has a liege and that liege is in the room with the host.
- **Value:** `$%ISSERVANT(who)%`: the liege's name, if the creature has one (in the room or not).
- **Example:** `IF ISSERVANT($n)`

### ISPKILL

- **Test:** `ISPKILL(who)`: true if the creature has the player-kill attribute set.
- **Value:** `$%ISPKILL(who)%`: `true` or `false`.
- **Example:** `IF ISPKILL($n)`
- **Notes:** inherited from multiplayer; the flag exists on any creature but nothing in single-player play sets it by default.

### ISGOOD

- **Test:** `ISGOOD(thing)`: true if the creature (or item) counts as good.
- **Value:** `$%ISGOOD(who)%`: the creature's alignment band name, or its alignment number if it has no band.
- **Example:** `IF ISGOOD($n) AND !ISEVIL($t)`
- **Notes:** true when the thing's disposition has the Good flag set (and not Evil); otherwise, for a creature, when its alignment faction is in the good range. `ISEVIL` and `ISNEUTRAL` are the matching checks.

### ISEVIL

- **Test:** `ISEVIL(thing)`: true if evil.
- **Value:** `$%ISEVIL(who)%`: the creature's alignment name in lower case.
- **Example:** `IF ISEVIL($n)`

### ISNEUTRAL

- **Test:** `ISNEUTRAL(thing)`: true if neither good nor evil.
- **Value:** `$%ISNEUTRAL(who)%`: the creature's alignment number.
- **Example:** `IF ISNEUTRAL($n)`

### ISBIRTHDAY

- **Test:** `ISBIRTHDAY(who)`: true if the character's birthday is today and this year's has been marked as celebrated.
- **Value:** `$%ISBIRTHDAY(who)%`: the next birthday spelled out in the calendar's words (weekday, day, month, year name).
- **Example:** `IF ISBIRTHDAY($n)`
- **Notes:** player-controlled characters only; false for NPCs. The date is read on the clock of the character's start room.

### IPADDRESS

- **Test:** `IPADDRESS(who cmp text)`: compares the address of the character's session.
- **Value:** `$%IPADDRESS(who)%`
- **Example:** `IF IPADDRESS($n == 127.0.0.1)`
- **Notes:** inherited. CoffeeSUD has no network connections, so this has no practical use; the value is whatever the local session reports and is empty for NPCs.

### NAME

- **Test:** `NAME(thing cmp text)`: compares the thing's name as text.
- **Value:** `$%NAME(thing)%`: the name.
- **Example:** `IF NAME($t == 'a rusty key')`
- **Notes:** compare with `==`, `!=`, `>`, `<` or `.in.`; for a word-anywhere search use [ISNAME](#isname).

### ISNAME

- **Test:** `ISNAME(thing text)`: true if the thing's name contains the text as a name-search would match it.
- **Value:** `$%ISNAME(thing)%`: the name.
- **Example:** `IF ISNAME($t guard)`

### SEX

- **Test:** `SEX(who == M)`, `SEX(who != F)`. The value is `M`, `F` or `N` (or the words `MALE`, `FEMALE`, `NEUTER`, or the numbers 1, 2, 0). Only `==` and `!=` are accepted.
- **Value:** `$%SEX(who)%`: `male`, `female` or `neuter`.
- **Example:** `IF SEX($n == F)`

### POSITION

- **Test:** `POSITION(thing == STANDING)`: `STANDING`, `SITTING` or `SLEEPING`; only `==` and `!=`; the position must start with your word.
- **Value:** `$%POSITION(thing)%`: `STANDING`, `SITTING` or `SLEEPING`.
- **Example:** `IF POSITION($n == SLEEPING)`

### LEVEL

- **Test:** `LEVEL(thing cmp n)`: the thing's level (creature or item).
- **Value:** `$%LEVEL(thing)%`
- **Example:** `IF LEVEL($n >= 10)`

### CLASS

- **Test:** `CLASS(who == name)`: the creature's current class (its display name) starts with the name; only `==`/`!=`.
- **Value:** `$%CLASS(who)%`: the class display name.
- **Example:** `IF CLASS($n == Mage)`

### BASECLASS

- **Test:** `BASECLASS(who == name)`: the base class of the current class starts with the name; `==`/`!=`.
- **Value:** `$%BASECLASS(who)%`
- **Example:** `IF BASECLASS($n != Fighter)`

### RACE

- **Test:** `RACE(who == name)`: the race name starts with the name; `==`/`!=`.
- **Value:** `$%RACE(who)%`
- **Example:** `IF RACE($n == Dwarf)`

### RACECAT

- **Test:** `RACECAT(who == name)`: the racial category (Humanoid, Undead...) starts with the name; `==`/`!=`.
- **Value:** `$%RACECAT(who)%`
- **Example:** `IF RACECAT($t == Undead)`

### DEITY

- **Test:** `DEITY(who == name)`: the name of the deity the creature worships equals the name (empty for none); `==`/`!=`.
- **Value:** `$%DEITY(who)%`
- **Example:** `IF DEITY($n == Aphrodite)`

### MOOD

- **Test:** `MOOD(who == name)`: the creature's `Mood` effect equals the name; `==`/`!=`.
- **Value:** `$%MOOD(who)%`: the mood name, capitalised; empty if it has no mood.
- **Example:** `IF MOOD($i == GRUMPY)`
- **Notes:** a creature with no `Mood` effect gives false even for `!=`.

### HASTITLE

- **Test:** `HASTITLE(who title)`: true if the player-controlled character has earned the exact title.
- **Value:** `$%HASTITLE(who title)%`: the character's *active* title (empty if the title argument is empty or the creature is not a player).
- **Example:** `IF HASTITLE($n 'the Brave')`

### STAT

- **Test:** `STAT(thing STATNAME cmp value)`: compares one stat of the object.
- **Value:** `$%STAT(thing STATNAME)%`
- **Example:** `IF STAT($n HITS < 20)`; `IF STAT($n STRENGTH >= 15)`
- **Notes:** `STATNAME` is any stat code the object itself exposes (its modify codes), then for creatures: character stats (`STRENGTH`, `CHARISMA`...), current state (`HITS`, `MANA`, `MOVE`, `HUNGER`, `THIRST`, `FATIGUE`), physical stats (`SENSES`, `DISPOSITION`, `LEVEL`, `ABILITY`, `REJUV`, `WEIGHT`, `HEIGHT`, `ARMOR`, `DAMAGE`, `ATTACK`), player stats, the `BASE` and `MAX` forms of those, `STINK`, and the Gen mob/item/physical pseudo-stats; also a stat of one of the object's behaviors. `==`/`!=` compare as case-insensitive text; every other operator as whole numbers. An unknown stat logs a `Syntax` error and is false. The full code lists are in [21-mobs.md](21-mobs.md) and [22-items.md](22-items.md); [GSTAT](#gstat) uses the Gen-code names instead.

### GSTAT

- **Test:** `GSTAT(thing STATNAME cmp value)`: as `STAT`, but the stat name is a "Gen" code: `GenMob` codes for creatures (including `LEVEL`, `RACE`, `CLASS`, `ALIGNMENT`...), `GenItem` codes for items.
- **Value:** `$%GSTAT(thing STATNAME)%`
- **Example:** `IF GSTAT($n CHARISMA > 10)`; `IF GSTAT($i IDLETICKS .in. '2 3 4')`
- **Notes:** also reads character stats, current/base/max state, physical stats and player stats. Operators as in `STAT`.

### HITPRCNT

- **Test:** `HITPRCNT(who cmp percent)`: hit points as a percentage of the maximum, rounded to a whole number.
- **Value:** `$%HITPRCNT(who)%`
- **Example:** `IF HITPRCNT($i < 50)`

### OBJTYPE

- **Test:** `OBJTYPE(thing == ClassID)`: true if the object's Java class ID contains the text; `==`/`!=`.
- **Value:** `$%OBJTYPE(thing)%`: the class ID in lower case.
- **Example:** `IF OBJTYPE($o == Weapon)`
- **Notes:** the class ID is the engine class name such as `GenWeapon` or `StdMOB`; "contains" lets `Weapon` match both.

### ISLIKE

- **Test:** `ISLIKE(thing mask)`: true if the thing passes the zapper mask.
- **Value:** `$%ISLIKE(mask)%`: the mask in plain words.
- **Example:** `IF ISLIKE($n '-CLASS +Mage -LEVEL +>=10')`
- **Notes:** the mask language is described in [24-behaviors-and-masks.md](24-behaviors-and-masks.md). Quote the mask.

### AFFECTED

- **Test:** `AFFECTED(thing effect)`: true if the thing is affected by that ability, spell, property or disease. `*` means "any effect at all".
- **Value:** `$%AFFECTED(thing)%`: the name of the thing's first effect, if any.
- **Example:** `IF AFFECTED($n Spell_Sleep)`; `IF AFFECTED($t *)`
- **Notes:** give the ability ID (`Spell_Sleep`) or its name; an unknown name logs an error and tests the text as an ID.

### ISBEHAVE

- **Test:** `ISBEHAVE(thing behavior)`: true if the thing has that behavior. `*` means "any behavior".
- **Value:** `$%ISBEHAVE(thing)%`: the behavior IDs, space separated.
- **Example:** `IF ISBEHAVE($i Sentinel)`

### ISABLE

- **Test:** `ISABLE(who skill)`: true if the living creature knows the skill, spell or expertise.
- **Value:** `$%ISABLE(who skill)%`: the skill's proficiency number; for an expertise, its name and level.
- **Example:** `IF ISABLE($n Skill_Dodge)`

### EXPERTISE

- **Test:** `EXPERTISE(who skill KIND cmp n)`: the creature's expertise level in the skill.
- **Value:** `$%EXPERTISE(who skill KIND)%`
- **Example:** `IF EXPERTISE($n Spell_Fireball LEVEL >= 2)`
- **Notes:** `KIND` is `X1`..`X6`, `LEVEL`, `TIME`, `MAXRANGE`, `LOWCOST`, `XPCOST` or `LOWFREECOST`. An unknown creature, skill or kind logs an error and is false.

### HASPERK

- **Test:** `HASPERK(who PERK_ID)`: true if the character has taken the perk (by ID or name).
- **Value:** `$%HASPERK(who PERK_ID)%`: `true` or `false`.
- **Example:** `IF HASPERK($n ARTISAN)`
- **Notes:** not inherited. An unknown perk name falls back to checking for a tattoo of that name. See [03-characters.md](03-characters.md).

### KNOWSRECIPE

- **Test:** `KNOWSRECIPE(who skill recipe name)`: true if the character knows the recipe.
- **Value:** `$%KNOWSRECIPE(who skill recipe name)%`: `true` or `false`.
- **Example:** `IF KNOWSRECIPE($n Blacksmithing 'iron sword')`
- **Notes:** not inherited. Where recipes are kept on the profile (`PROFILERECIPES`), the profile's knowledge counts. See [07-items-crafting.md](07-items-crafting.md).

### CANSEE

- **Test:** `CANSEE(who thing)`: true if `who` can see `thing` (lighting, hiding, invisibility).
- **Value:** `$%CANSEE(who thing)%`: `true` or `false`.
- **Example:** `IF CANSEE($i $n)`

### CANHEAR

- **Test:** `CANHEAR(who thing)`: true if `who` can hear `thing` move.
- **Value:** `$%CANHEAR(who thing)%`: `true` or `false`.
- **Example:** `IF CANHEAR($i $n)`

### ISSPEAKING

- **Test:** `ISSPEAKING(who language)`: true if the creature currently speaks the language. `any` means "anything but Common", `none` means "Common".
- **Value:** `$%ISSPEAKING(who)%`: the name of the language it speaks.
- **Example:** `IF ISSPEAKING($n Elvish)`
- **Notes:** inside a `NOTUNDERSTOOD_PROG` and for the speaker (`$n`), it reports the language of the unintelligible speech. See [11-presentation-language.md](11-presentation-language.md).

### UNDERSTANDS

- **Test:** `UNDERSTANDS(who language)`: true if the creature understands the language at all (proficiency above 0).
- **Value:** `$%UNDERSTANDS(who language)%`: `true` or `false`. `$%UNDERSTANDS$(who language)%`: the effective proficiency, 0 to 100.
- **Example:** `IF UNDERSTANDS($n Elvish)`; `IF EVAL($%UNDERSTANDS$($n Elvish)% >= 50)`
- **Notes:** not inherited. Counts known languages, related languages and translators. `UNDERSTANDS$` is a real name in the table but a `$` cannot appear in a condition, so it is available only as a value (`$%UNDERSTANDS$(...)%`), typically inside `EVAL` or `MATH`. A bare `UNDERSTANDS(...)` test is true when the value is non-empty, not `0` and not `false`.

### BREATH

- **Test:** `BREATH(who cmp n)`
- **Value:** `$%BREATH(who)%`: the ticks of breath the creature has left (all of it where it can breathe).
- **Example:** `IF BREATH($n < 5)`
- **Notes:** not inherited; see [10-world-simulation.md](10-world-simulation.md).

### CHECK

- **Test:** `CHECK(who STAT|skill difficulty [modifier])`: rolls a d20, adds the stat or skill bonus and the modifier, and is true when the total reaches the difficulty.
- **Value:** `$%CHECK(who STAT|skill difficulty [modifier])%`: `true` or `false` (the same roll).
- **Example:** `IF CHECK($n CHA 15)`; `IF CHECK($n Skill_Disarm 12 +2)`
- **Notes:** not inherited. `STAT` is a base stat by name, short name or three-letter prefix (`CHA`, `CHARISMA`); its bonus is `(value - 10) / 2`. A skill's bonus is its proficiency divided by 10. The modifier is a small arithmetic expression and may use a value form: `CHECK($n CHA 15 $%AFFINITY($i $n)%/10)`. Each call rolls anew and has side effects: it stores the margin (total minus difficulty) as the variable `CHECKMARGIN` for the character, and tells the character the result unless the `CHECKROLLS` setting is `OFF` (`FULL` shows the roll). See [29-combat-and-encounters.md](29-combat-and-encounters.md).

### Advancement, money and standing

### EXP

- **Test:** `EXP(who cmp n)`: the creature's experience points.
- **Value:** `$%EXP(who)%`
- **Example:** `IF EXP($n >= 1000)`

### EXPTONEXT

- **Value:** `$%EXPTONEXT(who)%`: the experience the character still needs for its next level; `0` when there is no next level.
- **Test:** `EXPTONEXT(who cmp n)`
- **Example:** `IF EXPTONEXT($n <= 50)`
- **Notes:** not inherited.

### TRAINS

- **Test:** `TRAINS(who cmp n)`: unspent training sessions.
- **Value:** `$%TRAINS(who)%`
- **Example:** `IF TRAINS($n > 0)`

### PRACS

- **Test:** `PRACS(who cmp n)`: unspent practice sessions.
- **Value:** `$%PRACS(who)%`
- **Example:** `IF PRACS($n >= 5)`

### QUESTPOINTS

- **Test:** `QUESTPOINTS(who cmp n)`
- **Value:** `$%QUESTPOINTS(who)%`
- **Example:** `IF QUESTPOINTS($n >= 10)`

### GOLDAMT

- **Test:** `GOLDAMT(thing cmp n)`: the money a creature carries, counted in the *host's* currency and expressed in base value (rounded); for a coin item its total value; for any other item its worth.
- **Value:** `$%GOLDAMT(thing)%`
- **Example:** `IF GOLDAMT($n >= 100)`; `IF GOLDAMT($o < 100)`
- **Notes:** other kinds of thing log a `Syntax` error. For a specific currency use [VALUE](#value).

### VALUE

- **Test:** `VALUE(thing CURRENCY cmp n)`: like `GOLDAMT` but in the designated currency (for a creature, its money in that currency; for a coin item, its value if the coin is of that currency; for other items their worth).
- **Value:** `$%VALUE(thing CURRENCY)%`
- **Example:** `IF VALUE($n CROWN >= 5)`
- **Notes:** an unknown currency logs an error and the test is false. Currency designations and denominations are in [09-society.md](09-society.md).

### CURRENCY

- **Test:** `CURRENCY(thing cmp code)`: compares the currency the thing uses as text.
- **Value:** `$%CURRENCY(thing)%`: the currency code (empty for the default).
- **Example:** `IF CURRENCY($i == CROWN)`

### MONEYSTR

- **Value:** `$%MONEYSTR(amount [CODE] [LONG|SHORT])%`: an amount in base value spelled in coin names.
- **Example:** `MPECHO The crossing costs $%MONEYSTR(36 CROWN LONG)%.`
- **Notes:** not inherited. `CODE` defaults to the host's currency; `SHORT` abbreviates. `amount` must be a number or a `Syntax` error is logged.

### DENOMNAME

- **Value:** `$%DENOMNAME(CODE value [count])%`: the name of the denomination of that currency worth `value`, for `count` coins (default 1).
- **Example:** `$%DENOMNAME(CROWN 240)%`
- **Notes:** not inherited.

### EXCHANGE

- **Value:** `$%EXCHANGE(CODE)%`: what a money changer pays for coins of that currency, in percent of their value.
- **Test:** `EXCHANGE(CODE cmp n)`
- **Example:** `IF EXCHANGE(COPPER < 90)`
- **Notes:** not inherited; see `MPEXCHANGE` in [27-mobprog-commands.md](27-mobprog-commands.md).

### BANKBALANCE

- **Value:** `$%BANKBALANCE(who chain [currency])%`: the money the person has on deposit with that bank chain, in base value.
- **Test:** `BANKBALANCE(who chain [currency] cmp n)`
- **Example:** `IF BANKBALANCE($n 'Guild Trust' >= 500)`
- **Notes:** not inherited. `who` may be a creature or a plain name.

### DEBT

- **Value:** `$%DEBT(who owedTo)%`: what `who` owes `owedTo`, in base value. With `DUE` (`$%DEBT(who owedTo DUE)%`): the game hours until it falls due (negative once overdue), empty if there is no debt.
- **Test:** `DEBT(who owedTo [DUE] cmp n)`
- **Example:** `IF DEBT($n Vargo > 0)`; `IF DEBT($n Vargo DUE < 0)`
- **Notes:** not inherited.

### FACTION

- **Test:** `FACTION(who faction cmp value)`: the creature's standing with the faction. If `value` is a number the creature's faction value is compared; otherwise the name of its range (Friendly, Hostile...).
- **Value:** `$%FACTION(who faction)%`: the name of the range the creature is in; empty if it has no standing.
- **Example:** `IF FACTION($n alignment.ini >= 500)`
- **Notes:** `faction` is the faction ID (the file name, such as `alignment.ini`), its name, or a band code. **Known fault:** the comparison is always done as whole numbers, so a range *name* on the right (`FACTION($n alignment.ini == GOOD)`) reads as 0 on both sides and `==` is always true. Use [FACTIONBAND](#factionband) to test a band by name and [FACTIONVALUE](#factionvalue) for the number. See [09-society.md](09-society.md).

### FACTIONVALUE

- **Test:** `FACTIONVALUE(who faction cmp n)`: the creature's numeric standing.
- **Value:** `$%FACTIONVALUE(who faction)%`: the number; empty if the creature has no standing with that faction.
- **Example:** `IF FACTIONVALUE($n elves.ini <= -500)`
- **Notes:** not inherited. An unknown creature or faction logs an error and is false.

### FACTIONBAND

- **Test:** `FACTIONBAND(who faction cmp CODE)`: compares the code name of the band the creature is in, as text.
- **Value:** `$%FACTIONBAND(who faction)%`: that code name, empty if none.
- **Example:** `IF FACTIONBAND($n inclination.ini == GOOD)`
- **Notes:** not inherited. The band codes are those listed in the faction's `RANGE=` lines.

### RANK

- **Value:** `$%RANK(who)%`: the creature's `Prop_MobRank` rank (`NORMAL` when none). `$%RANK(who LADDER)%`: its rank number on a progression ladder, `0` for none.
- **Test:** `RANK(who == ELITE)`; `RANK(who LADDER >= 2)`
- **Example:** `IF RANK($i == ELITE)`
- **Notes:** not inherited. See [29-combat-and-encounters.md](29-combat-and-encounters.md) and [03-characters.md](03-characters.md).

### AFFINITY

- **Value:** `$%AFFINITY(npc who [BAND])%`: how much the NPC likes `who`, as a number, or with `BAND` as its band name.
- **Test:** `AFFINITY(npc who cmp n)`
- **Example:** `IF AFFINITY($i $n >= 20)`
- **Notes:** not inherited; see [06-npcs-dialogue.md](06-npcs-dialogue.md).

### EXPLORED

- **Test:** `EXPLORED(who where cmp percent)`: how much of `where` the character has seen. `where` is `world`, an area name, a thing in an area, or a single room (a room counts 100 if visited, otherwise 0).
- **Value:** `$%EXPLORED(who where)%`: the percentage, for `world`, an area name or a thing in an area; empty otherwise.
- **Example:** `IF EXPLORED($n world >= 50)`; `IF EXPLORED($n 'Old Keep' == 100)`
- **Notes:** player-controlled characters only; others give false.

### SETCOUNT

- **Value:** `$%SETCOUNT(who set)%`: how many pieces of an item set the character has (worn or carried, as the set counts them).
- **Test:** `SETCOUNT(who set cmp n)`
- **Example:** `IF SETCOUNT($n bone_armor >= 3)`
- **Notes:** not inherited; see [17-item-modifiers-crafting.md](17-item-modifiers-crafting.md).

### Items and containers

### HAS

- **Test:** `HAS(thing item words)`: true if the creature carries (or wears) something matching the words; for a room, if it holds such an item; for an item, if its name matches.
- **Value:** `$%HAS(thing)%`: the name of a random item the creature carries (loose, not worn, not inside a container); for a container, a random item inside it; for a room, a random item lying there.
- **Example:** `IF HAS($n 'a long sword')`; `IF HAS($i $o)`
- **Notes:** the item words can be an object code (`$o`), in which case that very object is checked.

### HASNUM

- **Test:** `HASNUM(thing item cmp n)`: how many things matching `item` the creature (or room) holds. `item` may be `all`.
- **Value:** `$%HASNUM(thing item)%`
- **Example:** `IF HASNUM($n 'healing potion' >= 2)`; `IF HASNUM($i all == 0)`
- **Notes:** counts stacks as one each.

### WORN

- **Test:** `WORN(thing item words)`: true if the creature is *wearing or wielding* an item matching the words; for an item, if its name matches and it is not in inventory.
- **Value:** `$%WORN(thing)%`: a random worn item's name.
- **Example:** `IF WORN($n mace)`

### WORNON

- **Test:** `WORNON(who LOCATION item words)`: true if something worn on that body location matches the words.
- **Value:** `$%WORNON(who LOCATION)%`: the names of the items worn there, each followed by a space.
- **Example:** `IF WORNON($n WIELD sword)`
- **Notes:** `LOCATION` is a wear-location name such as `HEAD`, `WIELD`, `TORSO`, `FEET` (see [22-items.md](22-items.md)); an unknown one logs an error and is false. The item words may not be empty (that logs a `Syntax` error); read the value form (`$%WORNON($n HEAD)%`) if you need to know whether a slot is empty.

### ITEMCOUNT

- **Test:** `ITEMCOUNT(thing cmp n)`: the number of items the thing holds, stacks counted by their size.
- **Value:** `$%ITEMCOUNT(thing)%`
- **Example:** `IF ITEMCOUNT($o >= 10)`
- **Notes:** for a creature or room, the items present; for an area, every item in it; for a container, the container plus its contents; for raw material, its weight.

### MOBITEM

- **Test:** `MOBITEM(creature number words)`: true if the creature's item at that position (1-based, loose items only) matches the words.
- **Value:** `$%MOBITEM(creature number)%`: that item's name.
- **Example:** `IF MOBITEM($n 1 sword)`
- **Notes:** the creature is looked up *in the host's room* by name or position.

### NUMITEMSMOB

- **Test:** `NUMITEMSMOB(creature cmp n)`: the number of loose items the creature carries.
- **Value:** `$%NUMITEMSMOB(creature)%`
- **Example:** `IF NUMITEMSMOB($n == 0)`

### INCONTAINER

- **Test:** `INCONTAINER(thing [container])`: for an item, true if it is inside that container; for a creature, if it is riding that mount. With no second argument: true if it is in nothing / riding nothing.
- **Value:** `$%INCONTAINER(thing)%`: the name of what it is in or riding; for a container with nothing around it, the quoted names of its contents.
- **Example:** `IF INCONTAINER($o)`; `IF INCONTAINER($n horse)`

### ISCONTENT

- **Test:** `ISCONTENT(thing [words])`: for a rideable (a mount, boat, bed), true if a rider matching the words is aboard (with no words, if nobody is); for a container, if it holds a matching item (with no words, if it is empty).
- **Value:** `$%ISCONTENT(thing)%`: the names of the riders and the contents.
- **Example:** `IF ISCONTENT(ship)`; `IF ISCONTENT($i)`

### ISOPEN

- **Test:** `ISOPEN(direction | thing)`: true if the exit in that direction (from the host's room), or the named container or exit, is open.
- **Value:** `$%ISOPEN(direction | thing)%`: `true` or `false`.
- **Example:** `IF ISOPEN(north)`; `IF ISOPEN($o)`

### ISLOCKED

- **Test:** `ISLOCKED(direction | thing)`: true if that exit or container is locked.
- **Value:** `$%ISLOCKED(direction | thing)%`: the *key name* of the lock (empty when it has none), not `true`/`false`.
- **Example:** `IF ISLOCKED(east)`
- **Notes:** use `$%DOORSTATE(...)%` to read `open`, `closed` or `locked` directly.

### DOORSTATE

- **Value:** `$%DOORSTATE(room direction)%` or `$%DOORSTATE(room container)%`: `open`, `closed` or `locked`; empty if there is nothing there.
- **Test:** `DOORSTATE(room direction == locked)`
- **Example:** `IF DOORSTATE(HERE north == locked)`
- **Notes:** not inherited. `room` is `HERE`, a room ID, or a thing whose room is used.

### SWITCHSTATE

- **Value:** `$%SWITCHSTATE(thing)%`: the state of the `Prop_Switch` effect on a thing, or on an exit written `room:direction`; empty if it has none.
- **Test:** `SWITCHSTATE(thing == state)`
- **Example:** `IF SWITCHSTATE('rusted lever' == UP)`
- **Notes:** not inherited; see [07-items-crafting.md](07-items-crafting.md).

### CONTENTWEIGHT

- **Value:** `$%CONTENTWEIGHT(thing)%`: the weight of what is in or on the item (0 if it is not an item).
- **Test:** `CONTENTWEIGHT(thing cmp n)`
- **Example:** `IF CONTENTWEIGHT('stone pedestal' >= 50)`
- **Notes:** not inherited; used by weighing puzzles.

### Rooms and populations

Unless an entry says otherwise, "the room" is the room the host is in and "the
area" its area.

### INROOM

- **Test:** `INROOM(room)`: the host is in that room. `INROOM(thing cmp room)`: the thing is (`==`) or is not (`!=`) in it.
- **Value:** `$%INROOM(thing)%`: the extended room ID of the thing's room (the host's room when the argument is empty).
- **Example:** `IF INROOM(Midgaard#3001)`; `IF INROOM($n == $i)`; `IF INROOM(BEACON)`
- **Notes:** `room` may be an extended ID (`Area#number`), `#number` or a bare number (in the host's area), a player's name, or an object code (`$i`, `$n`: that object's room). Special words: `BEACON` is the start room of the creature (the host, or the thing in the comparison); `MYPROPERTY` a room the creature owns; `ANYPROPERTY` any room with a land title; `ANYSPECIAL` the host's start room or any property. An empty room argument gives true. Comparisons are on the room IDs as text.

### INAREA

- **Test:** `INAREA(area)`: the host is in that area. `INAREA(thing cmp area)`.
- **Value:** `$%INAREA(thing)%`: the area's name, `Nowhere` if it has none; the host's own area if the argument is empty.
- **Example:** `IF INAREA(Midgaard)`; `IF INAREA($n != 'Old Keep')`
- **Notes:** `area` is a name (exact, then by containing the text) or an object code (that object's area). An instance of an area counts as its parent area. In JavaScript, `INAREA$()` is the value form.

### ISRECALL

- **Test:** `ISRECALL(room)`: the host's start (recall) room is that room. `ISRECALL(thing cmp room)`.
- **Value:** `$%ISRECALL(thing)%`: the thing's start room ID.
- **Example:** `IF ISRECALL($n == $i)` (both call the same place home)

### INLOCALE

- **Test:** `INLOCALE(text)`: the host's room is of that locale. `INLOCALE(thing text)`.
- **Value:** `$%INLOCALE(thing)%`: the room's name (the host's room with no argument).
- **Example:** `IF INLOCALE($i INDOORS)`; `IF INLOCALE($n CITY)`
- **Notes:** true if the room's class ID contains the text (`WoodRoom`), or its domain name contains it (`CITY`, `WOODS`, `STONE`, `CAVE`...), or the text starts `INDOOR`/`OUTDOOR` and the room is of that kind. An empty text is true.

### ISHERE

- **Test:** `ISHERE(name)`: an item or creature matching the name is in the room.
- **Value:** `$%ISHERE()%`: the name of the area (not the room; this value form is not very useful).
- **Example:** `IF ISHERE($o)`; `IF ISHERE('the golden idol')`

### ROOMMOB

- **Test:** `ROOMMOB(number|name keywords)`: the creature at that 1-based position (or of that name) in the room matches the keywords (by name or display text).
- **Value:** `$%ROOMMOB(number|name)%`: that creature's name in the form the room's contents would name it (`second orc`).
- **Example:** `IF ROOMMOB(1 guard)`; `FOR`-style use: `$%ROOMMOB($0)%`

### ROOMITEM

- **Test:** `ROOMITEM(number keywords)`: the loose item at that 1-based position in the room matches the keywords.
- **Value:** `$%ROOMITEM(number)%`: its name as the room's contents would name it.
- **Example:** `IF ROOMITEM(1 coin)`

### ROOMPC

- **Test:** `ROOMPC(number|name keywords)`: the player-controlled character at that 1-based position (or of that name) in the room matches the keywords.
- **Value:** `$%ROOMPC(number|name)%`: its context name.
- **Example:** `IF ROOMPC(1 anna)`

### AREAPC

- **Test:** `AREAPC(number|name keywords)`: the same for the player-controlled characters in the host's whole area.
- **Value:** `$%AREAPC(number|name)%`: the character's name.
- **Example:** `IF AREAPC(1 anna)`
- **Notes:** counts the controllable characters currently in the world, so a party member in another area does not count.

### NUMMOBSROOM

- **Test:** `NUMMOBSROOM(cmp n)`: the creatures in the room. `NUMMOBSROOM(name cmp n)`: those whose name or display text contains the name. `NUMMOBSROOM(MASK=mask cmp n)`: those passing the zapper mask. `*` counts everyone.
- **Value:** `$%NUMMOBSROOM(name)%`: the count (all creatures with no argument or `*`).
- **Example:** `IF NUMMOBSROOM(orc >= 3)`; `IF NUMMOBSROOM('MASK=-PLAYERS' == 0)`
- **Notes:** the host and every player-controlled character count, unless a mask or name excludes them. Quote a mask that has spaces. See [24-behaviors-and-masks.md](24-behaviors-and-masks.md).

### NUMITEMSROOM

- **Test:** `NUMITEMSROOM(cmp n)`: loose items in the room (those not inside a container).
- **Value:** `$%NUMITEMSROOM()%`
- **Example:** `IF NUMITEMSROOM(== 0)`

### NUMPCSROOM

- **Test:** `NUMPCSROOM(cmp n)`: player-controlled characters in the room.
- **Value:** `$%NUMPCSROOM()%`
- **Example:** `IF NUMPCSROOM(>= 2)`

### NUMPCSAREA

- **Test:** `NUMPCSAREA(cmp n)`: player-controlled characters in the host's area.
- **Value:** `$%NUMPCSAREA()%`
- **Example:** `IF NUMPCSAREA(== 0)`

### NUMMOBS

- **Test:** `NUMMOBS(name|MASK=mask cmp n)`: the creatures in the whole world whose name contains the text, or that pass the mask.
- **Value:** `$%NUMMOBS(name|MASK=mask)%`
- **Example:** `IF NUMMOBS(dragon > 0)`; `IF NUMMOBS(MASK=-PLAYERS > 0)`
- **Notes:** scans every loaded room, so use it sparingly in frequent triggers.

### NUMMOBSINAREA

- **Test:** `NUMMOBSINAREA(name|MASK=mask|* cmp n)`: the same, limited to the host's area; `*` counts every creature there.
- **Value:** `$%NUMMOBSINAREA(name|MASK=mask|*)%`
- **Example:** `IF NUMMOBSINAREA(guard < 2)`

### NUMRACES

- **Test:** `NUMRACES(race cmp n)`: creatures of that race (exact race name) in the whole world.
- **Value:** `$%NUMRACES(race)%`
- **Example:** `IF NUMRACES(Dragon >= 1)`

### NUMRACESINAREA

- **Test:** `NUMRACESINAREA(race cmp n)`: creatures of that race in the host's area.
- **Value:** `$%NUMRACESINAREA(race)%`
- **Example:** `IF NUMRACESINAREA(Orc > 5)`

### ROOMWEIGHT

- **Value:** `$%ROOMWEIGHT(room)%`: the combined weight of the creatures (with what they carry) and items in the room.
- **Test:** `ROOMWEIGHT(room cmp n)`
- **Example:** `IF ROOMWEIGHT(HERE >= 200)`
- **Notes:** not inherited; for pressure-plate puzzles. `room` is `HERE`, a room ID or a thing whose room is used.

### RESPAWNROOM

- **Value:** `$%RESPAWNROOM(who)%`: the room ID of the character's respawn checkpoint, empty if none.
- **Test:** `RESPAWNROOM(who == room)`
- **Example:** `IF RESPAWNROOM($n == Midgaard#3001)`
- **Notes:** not inherited; see [29-combat-and-encounters.md](29-combat-and-encounters.md).

### ROOMSTATE

- **Value:** `$%ROOMSTATE(room|HERE|area)%`: the current state of the `RoomStates` behavior on the room (else its area's), empty if none.
- **Test:** `ROOMSTATE(HERE == FLOODED)`
- **Example:** `IF ROOMSTATE(HERE == DARK)`
- **Notes:** not inherited; see [10-world-simulation.md](10-world-simulation.md) and the `RoomStates` behavior in [24-behaviors-and-masks.md](24-behaviors-and-masks.md).

### ISDARK

- **Test:** `ISDARK(room|HERE|thing)`: the room is dark (night, a dark place and no light source in it).
- **Value:** `$%ISDARK(room|HERE|thing)%`: `true` or `false`.
- **Example:** `IF ISDARK(HERE)`
- **Notes:** not inherited.

### ISLIT

- **Test:** `ISLIT(room|HERE|thing)`: the opposite of `ISDARK` (false when the room cannot be found).
- **Value:** `$%ISLIT(room|HERE|thing)%`: `true` or `false`.
- **Example:** `IF ISLIT(HERE)`
- **Notes:** not inherited. A room that cannot be found counts as not dark: `ISLIT` is then true and `ISDARK` false, so check your argument.

### FLOODLEVEL

- **Value:** `$%FLOODLEVEL(room|HERE)%`: the water level from a `Floodable` behavior: 0 (dry) to 3 (full); `0` if there is none.
- **Test:** `FLOODLEVEL(HERE >= 2)`
- **Example:** `IF FLOODLEVEL(HERE >= 2)`
- **Notes:** not inherited; see [10-world-simulation.md](10-world-simulation.md).

### ISTIDE

- **Test:** `ISTIDE(room HIGH|LOW|NONE|SPRING|NEAP[:span])`: the tide at the room is in that state now.
- **Value:** `$%ISTIDE(room STATE)%`: `true` or `false`.
- **Example:** `IF ISTIDE(HERE HIGH)`
- **Notes:** not inherited; see [10-world-simulation.md](10-world-simulation.md).

### CATALOG

- **Test:** `CATALOG(name)`: a catalog creature or item of that name exists and a copy may be made (it is not at its cap, and a unique item has not appeared).
- **Value:** `$%CATALOG(name)%`: `true` or `false`.
- **Example:** `IF CATALOG(a fixsc3 sunblade)` then `MPOLOAD CATALOG the Sunblade`
- **Notes:** not inherited. The catalog is the world's list of shared prototypes (see [02-world-workflow.md](02-world-workflow.md)). The whole text is the name, no quotes needed.

### CATALOGCOUNT

- **Value:** `$%CATALOGCOUNT(name)%`: how many copies of the catalog entry exist in the world (for unique items, those that count against the cap).
- **Test:** `CATALOGCOUNT(name cmp n)`
- **Example:** `IF CATALOGCOUNT(a goblin guard < 4)`

### Time and weather

Game time comes from the clock of the host's area (each area may keep its own
calendar; see [10-world-simulation.md](10-world-simulation.md)). The `ISRL*`
functions use the computer's real clock and have little meaning in a
turn-based single-player game.

### ISTIME

- **Test:** `ISTIME(word)`: `DAYTIME`, `DAWN`, `DUSK`, `NIGHTTIME` (any start of those, so `night` works), or an hour number 0 to the day's last hour.
- **Value:** `$%ISTIME()%`: the time-of-day phrase in lower case with a trailing space (`it is daytime `, `it is nighttime `).
- **Example:** `IF ISTIME(night)`; `IF ISTIME(DAWN) OR ISTIME(DUSK)`
- **Notes:** use the test form. The value form returns the clock's descriptive sentence fragment, not a single word.

### ISHOUR

- **Test:** `ISHOUR(hour)`: the hour of the day equals the number.
- **Value:** `$%ISHOUR()%`: the current hour.
- **Example:** `IF ISHOUR(6)`

### ISDAY

- **Test:** `ISDAY(day)`: the day of the month equals the number.
- **Value:** `$%ISDAY()%`: the day of the month.
- **Example:** `IF ISDAY(1)`

### ISMONTH

- **Test:** `ISMONTH(month)`: the month number equals the number (as the calendar counts them).
- **Value:** `$%ISMONTH()%`
- **Example:** `IF ISMONTH(12)`

### ISYEAR

- **Test:** `ISYEAR(year)`
- **Value:** `$%ISYEAR()%`
- **Example:** `IF ISYEAR(1201)`

### ISSEASON

- **Test:** `ISSEASON(name)`: the area's season is that one: `SPRING`, `SUMMER`, `FALL`, `WINTER` (or the start of the name), or the area's calendar's own named season (spaces allowed).
- **Value:** `$%ISSEASON()%`: the calendar's season name, else the built-in one.
- **Example:** `IF ISSEASON(winter)`; `IF ISSEASON(the Burn)`
- **Notes:** a calendar can name its seasons and say which built-in season each counts as; `ISSEASON(summer)` is true during a named season that counts as summer.

### ISWEATHER

- **Test:** `ISWEATHER(name)`: the weather at the host's room starts with the name.
- **Value:** `$%ISWEATHER()%`: the current weather name.
- **Example:** `IF ISWEATHER(rain)`
- **Notes:** the names are `CLEAR`, `CLOUDY`, `WINDY`, `RAIN`, `THUNDERSTORM`, `SNOW`, `HAIL`, `HEAT`, `SLEET`, `BLIZZARD`, `DUST`, `DROUGHT`, `COLD`, `FOG`.

### ISMOON

- **Test:** `ISMOON(phase)`: the moon is in that phase: `NEW`, `WAXCRESCENT`, `WAXQUARTER`, `WAXGIBBOUS`, `FULL`, `WANEGIBBOUS`, `WANEQUARTER`, `WANECRESCENT`, `BLUE` (any start of those). `ISMOON(name phase)` names one of the calendar's moons (`ISMOON(Ember full)`); `ISMOON(name)` is true when that moon is in the sky. `ISMOON()` is true when any moon is out (the stars can be seen).
- **Value:** `$%ISMOON()%`: the current phase name.
- **Example:** `IF ISMOON(full)`; `IF ISMOON(Ember full)`

### ISWEEKDAY

- **Test:** `ISWEEKDAY(name)`: today is that weekday of the area's calendar (case-insensitive, whole name).
- **Value:** `$%ISWEEKDAY(name)%`: `true` or `false`.
- **Example:** `IF ISWEEKDAY(Marketday)`
- **Notes:** not inherited. False if the calendar has no weekdays. `$%WEEKDAY()%` gives the name.

### WEEKDAY

- **Value:** `$%WEEKDAY()%`: today's weekday name on the area's calendar, empty if it has none.
- **Test:** `WEEKDAY(== name)` compares the weekday name as text (`ISWEEKDAY(name)` is the plainer test).
- **Example:** `MPECHO It is $%WEEKDAY()%.`
- **Notes:** not inherited.

### DATETIME

- **Test:** `DATETIME(PERIOD cmp n)`: the current number for that period. `PERIOD` is `HOUR`, `DAY` (day of month, `DATE` also works), `WEEK` (week of the year), `MONTH`, `SEASON` (0 to 3 for the built-in seasons), `YEAR`; plural spellings are accepted.
- **Value:** `$%DATETIME(PERIOD)%`; also `$%DATETIME(STAMP)%` (the clock's hours counted from year 0, for use with `ELAPSED`), `$%DATETIME(DAYOFYEAR)%` and `$%DATETIME(WEEKDAY)%` (the weekday name).
- **Example:** `IF DATETIME(HOUR >= 18)`; `MPSETVAR $n SEEN $%DATETIME(STAMP)%`
- **Notes:** `ALLTIME` gives 0. The clock is the area's of the host. `STAMP`, `DAYOFYEAR` and `WEEKDAY` exist only as values.

### GAMETIME

- **Test:** `GAMETIME(cmp n)`: the game clock in milliseconds since the game began.
- **Value:** `$%GAMETIME()%`: those milliseconds; `$%GAMETIME(HOURS|DAYS|MONTHS|YEARS)%`: whole game hours, days, months or years counted from year 0 of the area's calendar.
- **Example:** `IF EVAL($%GAMETIME(DAYS)% >= 100)`
- **Notes:** not inherited. The test form has no unit; use `EVAL` or `MATH` with the value form for units.

### ELAPSED

- **Value:** `$%ELAPSED(stamp [HOURS|DAYS|WEEKS|MONTHS])%`: whole units of game time since the stamp (hours by default).
- **Test:** `ELAPSED(stamp DAYS >= 3)`
- **Example:** `MPSETVAR $i ORDERED NOW` earlier, then `IF ELAPSED($<$i ORDERED> DAYS >= 3)`
- **Notes:** not inherited. A stamp is what `$%DATETIME(STAMP)%` returns or what `MPSETVAR host var NOW` stores. Counted on the area's calendar.

### TICKS

- **Value:** `$%TICKS(n [HOURS|DAYS|WEEKS|MONTHS])%`: the number of game ticks in that much game time.
- **Test:** `TICKS(3 DAYS > 500)`
- **Example:** `MPTATTOO $n '$%TICKS(2 DAYS)% DAZED'`
- **Notes:** not inherited. Timed tattoos count down in ticks; this converts a calendar span for them.

### TURNS

- **Test:** `TURNS(cmp n)`: turns since the game began.
- **Value:** `$%TURNS()%`
- **Example:** `IF TURNS(>= 1000)`
- **Notes:** not inherited; the count is saved with the game and is what `TURN_PROG` receives in `$g`.

### ISRLHOUR

- **Test:** `ISRLHOUR(hour)`: the computer's real hour (0 to 23).
- **Value:** `$%ISRLHOUR()%`
- **Example:** `IF ISRLHOUR(23)`

### ISRLDAY

- **Test:** `ISRLDAY(day)`: the real day of the month.
- **Value:** `$%ISRLDAY()%`
- **Example:** `IF ISRLDAY(25)`

### ISRLMONTH

- **Test:** `ISRLMONTH(month)`: the real month, 1 to 12.
- **Value:** `$%ISRLMONTH()%`
- **Example:** `IF ISRLMONTH(12)`

### ISRLYEAR

- **Test:** `ISRLYEAR(year)`
- **Value:** `$%ISRLYEAR()%`
- **Example:** `IF ISRLYEAR(2027)`

### ISHOLIDAY

- **Test:** `ISHOLIDAY(name)`: the holiday (a holiday quest) of that name is running.
- **Value:** `$%ISHOLIDAY(name)%`: `true` or `false`.
- **Example:** `IF ISHOLIDAY(MoonFeast)`
- **Notes:** not inherited; see [10-world-simulation.md](10-world-simulation.md).

### HOLIDAY

- **Value:** `$%HOLIDAY()%`: the names of the holidays on now, comma separated.
- **Test:** `HOLIDAY(== text)` compares the whole comma-separated list exactly, so use `ISHOLIDAY(name)` to ask about one holiday.
- **Example:** `MPECHO Today we celebrate: $%HOLIDAY()%`
- **Notes:** not inherited.

