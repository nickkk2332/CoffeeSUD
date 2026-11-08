# Guides for players and builders

> Part of the CoffeeMud NEWOVERVIEW. Bucket: `c11` (content: Guides for players and builders) · 8 files · 410,065 bytes of data.

Citation convention: guide paths are relative to the CoffeeMud folder (`guides/GameBuildersGuide.html:4603` is a line of the HTML source). A bare "guide N" means the file named in that section, which is the Game Builder's Guide unless the sentence names another file. Java paths are relative to `com/planet_ink/coffee_mud/`, so `Behaviors/MudChat.java:601` means that file. Line numbers were taken from the files as they are now. Where a guide statement was checked against code, the result is given; "unverified" marks statements that were not checked.

## Summary

These eight HTML files are the manuals that ship in `guides/`: the 6,487-line Game Builder's Guide (the bulk of the bucket), a 1,280-line Random Generation guide, a Builders' FAQ, a MUDChat tutorial, two design or feature overviews, a short player introduction and a frameset stub. Together they document the builder's data model: abilities, languages, crafting, traps, poisons, components, classes, factions, planes, quest scripts, holidays, socials, polls, clans, achievements, commands, and the XML grammar for generated areas. No Java code parses the HTML; the web server only publishes the folder (`web/pub.ini:63`), and the engine reads the same concepts from data files and classes. Checked against code, most field names and mechanics hold, but several statements are stale or wrong (the remort level, the `EXPLESS` disable keyword, MUDChat's LLM operator, the Random Areas layout list, and some anchors). For a single-player Python SUD the valuable material is the design beneath the editors: scripted "mystery" slots, faction ranges with change triggers, socials with per-audience messages, weighted chat rules, and a deterministic language scrambler.

## Key files and types

| File or class | Role | Lines |
|---|---|---|
| `guides/GameBuildersGuide.html` | Builder reference: CMFS, abilities, languages, crafting, gathering, wrighting, traps, poisons, components, all-qualify, recipes, classes, factions, planes, quests, holidays, socials, polls, clans, governments, awards, titles, achievements, commands | 6,487 |
| `guides/RandomAreas.html` | Random generation: XML tag grammar, GENERATE command, StdAutoGenInstance and AutoGrid, MQL notes | 1,280 |
| `guides/BuildersFAQ.html` | About forty builder questions: DISABLE flags, clans, remorting, tattoos, shops, combat modes, mob behaviour | 557 |
| `guides/MUDChat.html` | Tutorial for `resources/chat.dat`: triggers, weights, operators, ChatGroups, LLM use | 285 |
| `guides/GameOverview.html` | Design essay: the fantasy, heroic and tech games, tech rules, auto-generated planets, a ship-console example | 715 |
| `guides/Features.html` | Feature list for 5.11, with counts | 194 |
| `guides/HowToPlay.html` | Player introduction (Mud School, HELP, OOC) | 37 |
| `guides/Reference.html` | Frameset that loads `refs/nav.html` and `refs/intro.html` | 6 |
| `Behaviors/MudChat.java` | Parses and runs chat.dat (`parseChatData` at 481-650; response operators at 1121-1210) | 1,755 |
| `Libraries/MUDPercolator.java` | Random-generation engine: tags, select, define, text modifiers, layouts, MQL | 8,243 |
| `Commands/Generate.java` | GENERATE command; object types at 45-54 | 447 |
| `Libraries/layouts/*.java` | Ten layout generators (APARTMENT to TREE) | (folder) |
| `Libraries/Socials.java` | Loads `resources/socials.txt`; social creation and editing | 1,268 |
| `Common/DefaultSocial.java` | Social object and target rules (`targetable` 277-303, `invoke` 345) | 1,062 |
| `Libraries/CoffeeFilter.java` | Pronoun tokens such as `-HIS-HER` (`Pronoun` enum at 57) | 2,087 |
| `Common/DefaultQuest.java` | Quest-script interpreter: STEP at 1152, SET at 1202, IMPORT at 3351, LOAD at 3518, GIVE at 3791; accept functions at 182-225 | 7,124 |
| `Common/interfaces/Quest.java` | `QCODES` header-keyword enum (826) | 870 |
| `Libraries/Quests.java` | Holidays file name (45) and default holiday text (485-495) | 2,680 |
| `Common/DefaultScriptingEngine.java` | MOBPROG functions used by quests: MPQSET (10749), MPSTEPQUEST (13247), MPQUESTWIN (13365) | 16,435 |
| `Common/interfaces/Faction.java` | Change-direction, flag and key-value constants (1308-1312); `MiscTrigger` enum (1314-1319) | 1,903 |
| `Abilities/interfaces/Ability.java` | Cost sentinels `COST_ALL`, `COST_PCT`, `COST_NORMAL` (651-655) | 1,109 |
| `Abilities/StdAbility.java` | Cost conversion (1614-1618, 1799) and compounding penalty (1995-2030) | 3,018 |
| `Abilities/Languages/StdLanguage.java` | Word translation (`translate` 212-236) and partial garbling (`messChars` 252) | 858 |
| `Abilities/StdPlanarAbility.java`, `Abilities/interfaces/PlanarAbility.java` | Plane overlay; `PlanarSpecFlag` (334-339) | 2,235 / 341 |
| `Libraries/CMGenEditor.java` | Plane editor (`modifyPlane` at 10188) | 12,704 |
| `Libraries/interfaces/AchievementLibrary.java` | Achievement `Event` enum (71-) with 47 event codes | 897 |
| `Common/interfaces/Poll.java`, `Libraries/Polls.java` | Poll flags (310-320); voting by IP option (215, 262) | 321 / 482 |
| `core/CMFile.java` | `//` and `::` path prefixes (129-130, 1864-1866) | 2,306 |
| `core/CMSecurity.java` | `FS:` and `VFS:` codes (302, 317); `DisFlag` enum (2942-) | 3,068 |
| `Commands/Create.java` | CREATE object list (1724-1731); CREATE PLANE (2028) | 2,682 |
| `Libraries/CoffeeMaker.java` | Serialises behaviours and affects (5272-5312) | 6,719 |
| `WebMacros/RebuildReferenceDocs.java` | Sysop macro that regenerates `guides/refs/` from `web/admin/work/*.cmvp` | 99 |
| `resources/quests/holidays/holidays.quest` | The shipped holiday definitions | 2,398 bytes |
| `coffeemud.ini` | DISABLE documentation (1951-1972), CLASSSYSTEM (466-495), REMORTMASK (696), DIRECTIONS (1055-1059) | - |

## How it works

### 1. How the guides are published and where they are checked

- The guides are static HTML. `web/pub.ini:63` maps `/guides` to the `guides/` folder (`web/admin.ini:64` and `:69` do the same). No Java file opens these pages.
- `guides/refs/` holds 79 generated pages, including one per class (`pclass_*.html`). They are outside this bucket. `WebMacros/RebuildReferenceDocs.java:58-80` (sysop only) turns each `web/admin/work/*.cmvp` source into `guides/refs/*.html`. `Reference.html` is the frameset that loads `refs/nav.html` and `refs/intro.html`.
- Some data files point readers at these guides. For example `resources/randareas/example.xml:18` says "See guides/RandomAreas.html for information about documents of this type". The default GENERATE file is the same example (`Commands/Generate.java:119`, `Areas/StdAutoGenInstance.java:63`).
- Broken links: `GameBuildersGuide.html` has 32 links to `ArchonTome.html`, which is not in `guides/` (it has `ArchonGuide.html`). `BuildersFAQ.html:265` links to `MudChat.html`, but the file is `MUDChat.html`, so the link fails on a case-sensitive server.

### 2. CMFS: the file layer the builder guide starts with (GameBuildersGuide.html:58-284)

- CMFS mirrors local files and a database virtual file system (VFS). A VFS copy is preferred when both exist, so upgrades do not overwrite edits made in the database (guide 118-121).
- `//` forces the local file and `::` forces the VFS (`core/CMFile.java:129-130`; prefix handling at 1864-1866 and 1894-1895). The guide's `::resources/progs/myprog.script` example uses this form.
- Access is controlled by security codes `FS:` (both layers) and `VFS:` (database only), with paths such as `VFS: RESOURCES/TEXT/` (guide 247-273). Parsing is in `core/CMSecurity.java:302` and `:317`.

### 3. Abilities: one data model for spells, skills, chants, traps and effects (GameBuildersGuide.html:285-684)

- An ability is a Java class with an ID such as `Spell_`, `Skill_`, `Chant_`, `Prayer_`, `Trap_`, `Poison_`, `Common_` or `Lang_`. Builders can also create abilities at run time with `CREATE ABILITY` (guide 331-341).
- The same ID means different things by list. On a mob's affects list it is a lasting effect (for example a permanent strength boost). On its abilities list it is a skill the mob can invoke (guide 316-327). Effects serialise as `<AFFECS><AFF><ACLASS>...` in `Libraries/CoffeeMaker.java:5272-5312`.
- The editor exposes about forty fields. They are: name; type and domain; command words; minimum and maximum range; ticks between casts; duration override; affect string; auto-invoking flag; skill flags; override cost; cost type; number of arguments; can-affect and can-target lists; tick and periodic effects; quality code (malicious, beneficial, ok, indifferent); affect adjustments; caster and target masks; a Scriptable parameter; six messages (target-fail, fizzle, auto-cast, normal-cast, post-cast, uninvoke); attack type; quiet-effect ID; public effects; extra castings; a damage or healing formula; and help text that starts with `<ABILITY>` (guide 346-681).
- Cost encoding: `Ability.java:651` `COST_ALL = Integer.MAX_VALUE`, `:653` `COST_PCT = Integer.MAX_VALUE-100`, and `:655` `COST_NORMAL = -1`. `StdAbility.java:1617-1618` treats a value above `COST_PCT` as a percentage of the maximum: `(COST_ALL - consumed)/100 * maxAmt`. The guide's band "2147483647-100 to 2147483647" (guide 441-443) includes `COST_PCT` itself, which the strict test does not treat as a percentage. `StdAbility.java:1799` uses `> COST_PCT-1`, so the two checks disagree at that one value.
- Repeated casting: a compounding rule with `compoundingTicks`, `pctPenalty` and `amtPenalty` raises the cost of casts made close together. A negative `amtPenalty` refuses the cast with "You can't do that again just yet." (`StdAbility.java:1995-2030`).
- Formula variables (`@x1` caster level, `@x2` target level, `@x3` expertise, `@x4`-`@x8` expertise levels, `@x9` adjusted level) are described at guide 444-446, 516-518 and 665-666. Their evaluation was not traced (unverified).

### 4. Languages (GameBuildersGuide.html:685-768; StdLanguage.java:212-260)

- A language has word lists keyed by length, hashed direct replacements, and "Translation Language IDs" that say which other languages are understood without study (guide 712-766).
- `StdLanguage.translate` (212-236) works in four steps. It first looks up an exact hashed word. It keeps player names (`CMLib.players().getPlayerAllHosts`). It then picks from the list for the word's length, falling back to the last list. The choice is `choices[abs(hash(lowercase word)) % choices.length]`, so a given word always becomes the same substitute.
- Partial comprehension is handled by `messChars` (252), which garbles some letters of a message, using half the requested count.

### 5. Crafting, gathering and wrighting (GameBuildersGuide.html:769-1168; recipes 1723-2045)

- Craft skill fields: command words, skill verb, recipe file (in `resources/skills/`), raw materials, can mend, can refit, can bundle, can sit, MSP sound, and help (guide 798-881). Wrighting adds can make doors, can re-title, can re-describe and a clan-only flag (1080-1166).
- Gathering skill fields: command words, verb, can bundle, raw materials, can sit, room mask and player mask (zapper masks), yield formula, start, found, not-found and complete messages, droppable items, and a cosmetic-only flag (918-1050). The complete message gets `@x1` (count) and `@x2` (plural item name).
- Recipe row fields (guide 882-888 and 1742-2044): final name (`%` is replaced by the material), level, build time in ticks, materials required (an amount or a component code), base value, class ID, and per-type extras in fields 6-9 (lid or lock, statue, ride basis, weapon class, wear location, smoke, capacity, hands, liquid capacity, light duration, uses, armour, damage, container type, attack bonus), then spell list and key-value pairs such as `RACEREQUIREMENT`.
- A component code in "Materials Req" gives disposition (inventory, held, worn, nearby or onground), fate (consumed or kept), amount, component ID, sub-type and a zapper mask (guide 1804-1811).
- The recipe data and its loader are covered in c04 and c05 and were not traced here.

### 6. Traps and poisons (GameBuildersGuide.html:1169-1488)

- Trap fields: level (a guideline for lethality), is-bomb, can affect, can target, components, avoid, spring and damage messages, a damage formula using `@x1` trap level, `@x2` ability code, `@x3` invoker level and `@x4` target level, damage type, damage message type (which saving throw applies), an ability to invoke with parameters and a tick override, and a Scriptable parameter (guide 1200-1297).
- Poison fields (GenPoison editor, guide 1340-1475): trigger string, ticks, delay, start message, target-tell, periodic affect message, affect-target (one shot or lasting), force peace, damage per tick, done message, addiction chance (0-100), cast and fail messages, stat adjustments in the `Prop_HaveAdjuster` syntax, mood, and an effects list in the form `[ability id];[ability parms];...`. A UniPoison is a one-off poison given as parameters (guide 1332-1338).
- The code that applies traps and poisons was not traced (unverified).

### 7. Components, all-qualify and classes (GameBuildersGuide.html:1489-1722 and 2046-2451)

- A component is a material or a ritual that must be present or performed to invoke a skill. Its fields are conjunction (AND, OR, or MESSAGE for a description), location (inventory, onground, held, worn, nearby, or trigger for a ritual), consumed or kept, amount, type, sub-type, zapper mask and a trigger ritual. Rituals may use custom socials that only exist as components (guide 1626-1633).
- All-qualify entries make a skill available to every class (EACH) or to all classes as a group (ALL). Each entry has a qualifying level, auto-gained flag, default proficiency, qualifying mask and required skills with minimum proficiency (guide 1669-1712).
- Class fields (guide 2099-2451) include names by level, level cap, base class, availability, HP, mana and movement formulas that use `@x1` (level) through `@x9` (adjusted stats), practices and attack per level, armour restrictions, qualification mask, minimum stats, required races, stat adjustments, starting money, a sub-class switch rule (`BASE`, `ANY` or `NONE`, guide 2377-2384), limits on how many skills of each kind a class may learn, class abilities with proficiencies, and security code sets.
- Class-system modes: the guide describes four (guide 2052-2067). `coffeemud.ini:466-495` documents more: `SUB`, `SUB-[N]`, `BASE-[N]`, `NO`, `NO-BASE`, `NO-SUBONLY`, `NO-[X]`, `NO-GRP-[X]`, `MULTI`, `MULTI-[N]`, `APP-SUB`, `APP-NO`, `APP-MULTI`, `DISABLED`, and the `GAIN=` and `SWITCH=` training costs. `core/CMProps.java:2739-2750` parses them; both costs default to 1.

### 8. Factions (GameBuildersGuide.html:2452-2939)

- A faction is a numeric value on each creature, divided into named ranges. Each range has a code for zapper masks. The faction also has change triggers, change directions and flags, cross-faction relations, ability allowances, and effects or reaction effects that give mobs behaviours or abilities (guide 2504-2937).
- Triggers: the guide documents nine misc triggers (murder, kill, time, add outsider, bribe, talk, MudChat, arrested, social) plus ability-based triggers. The code's `MiscTrigger` enum (`Common/interfaces/Faction.java:1314-1319`) has sixteen values, adding SINK, SUNK, AREAKILL, AREAASS, AREAEXPLORE, DYING and CMMSG, which the guide omits.
- Directions (`Faction.java:1308`): UP, DOWN, OPPOSITE, MINIMUM, MAXIMUM, REMOVE, ADD, AWAY and TOWARD. These match guide 2666-2684.
- Flags (`Faction.java:1310`): OUTSIDER, SELFOK and JUST100. Key-value flags (`Faction.java:1312`) are XP, RPXP, RESTIME and ANNOUNCE. The guide documents XP and RPXP only (guide 2697-2704).
- Auto-reaction is set in `coffeemud.ini:444` (`AUTOREACTION=PLANAR`), with the faction file list at 502 (`FACTIONS=alignment.ini`). The guide says alignment is itself a faction (guide 2462-2467); this was not traced.

### 9. Planes (GameBuildersGuide.html:2940-3180)

- A plane is a set of overrides that mirror the area a traveller stands in (guide 2943-2952). The editor stores the settings in a map (`Libraries/CMGenEditor.java:10188`, `modifyPlane`), and they are applied through `Abilities/StdPlanarAbility.java`.
- Spec flags are `NOINFRAVISION`, `BADMUNDANEARMOR` and `ALLBREATHE` (`Abilities/interfaces/PlanarAbility.java:334-339`).
- "Is Like" copies every attribute the plane does not set from another plane (guide 2994-2998). Other fields include a level formula (`@x1` area median level, `@x2` mob level, guide 3021-3026), alt hours per day, recover rate, fatigue rate, elite level, room descriptions, race mix, bonus damage stat, absorptions and promotions (guide 2977-3178).
- `CREATE PLANE` is implemented (`Commands/Create.java:2028-2035`) but is missing from the CREATE help list (`Create.java:1726-1731`).

### 10. Quest scripts (GameBuildersGuide.html:3181-4591)

- A quest script is a list of keyword lines ending in `;`. `<SCRIPT>` ... `</SCRIPT>` embeds JavaScript. `<OPTION>` blocks pick one at random. Numbers in square brackets are expressions, and `?` gives a random value (`1?4`) (guide 3254-3266, 4013-4034).
- Header keywords (guide 3289-3415) are set with `SET`. The engine's list (`Common/interfaces/Quest.java:826-832`, `QCODES`) is CLASS, NAME, DURATION, WAIT, MINPLAYERS, PLAYERMASK, RUNLEVEL, DATE, MUDDAY, INTERVAL, SPAWNABLE, DISPLAY, INSTRUCTIONS, PERSISTANCE, AUTHOR, EXPIRATION, QUESTTYPE and CATEGORY. The guide does not document `AUTHOR` or `QUESTTYPE`, although the templates use `set author $#AUTHOR` (for example `resources/quests/templates/auto_capture1.quest:140`).
- Steps: `STEP` ends a phase. `STEP AUTO` advances when the duration runs out. `STEP BACK` rewinds and `STEP BREAK` stops evaluation (guide 3421-3464). The parser is `Common/DefaultQuest.java:1152-1170`, and `MPSTEPQUEST` advances a step (`Common/DefaultScriptingEngine.java:13247`). `SET PRESERVE` keeps objects across steps (guide 3402-3414; `DefaultQuest.java:1852`).
- Object commands: `SET` (AREA, ROOM, MOB, ITEM, ABILITY, and the `GROUP` forms), `LOAD` (MOB, ITEM, ABILITY and groups, from an imported file), `GIVE` (ITEM, ITEMS, STAT, BEHAVIOR, ABILITY, AFFECT, FOLLOWER, SCRIPT), `TAKE`, `RESET` and `IMPORT` (MOBS, ITEMS, ABILITIES). Dispatch: `DefaultQuest.java:1202` (SET), `:3351` (IMPORT), `:3518` (LOAD), `:3791` (GIVE).
- Object specifiers (guide 4035-4062): AREA, ROOM, MOB, ITEM, ABILITY, the group forms, LOADEDMOBS and LOADEDITEMS, ENVOBJ, STUFF and ARG1 to ARGN. They combine with `+` and `-`, as in `MOBGROUP-MOB`.
- Mystery mode (guide 4078-4420): AGENT, ACTION, TARGET, TOOL, MOTIVE, WHEREHAPPENED and WHEREAT, WHENHAPPENED and WHENAT, and FACTION, each with a `GROUP` form and a `#NUMBER` sample. The dispatch cases are in `DefaultQuest.java` at 2542 (AGENT), 2573 (FACTION), 2915 (WHENHAPPENED), 3037 (MOTIVE), 3055 (ACTION), 3073 (WHEREHAPPENED) and 3266 (TARGET and TOOL).
- Acceptance and progress are driven by MOBPROG (guide 4420-4513). A mob script defines `CAN_ACCEPT` and `DO_ACCEPT`, which the quest manager calls (`DefaultQuest.java:182-225`). Scripts then use `MPQSET`, `MPQUESTWIN` and `MPENDQUEST` (`DefaultScriptingEngine.java:10749`, `:13365`). The guide's example awards experience and quest points and then ends the quest for the player.
- JavaScript (guide 4514-4580): the manager strips semicolons from script text unless they are escaped. How the JavaScript is evaluated was not traced (unverified).

### 11. Holidays (GameBuildersGuide.html:4592-4740; holidays.quest)

- A holiday is one named block inside a single quest called `holidays`, stored at `quests/holidays/holidays.quest` (`Libraries/Quests.java:45`). Each block has a schedule (random interval in ticks, mud date or real-life date), a duration, an area list, a mob mask, an optional mood, aggression, price factors, MudChat words, and behaviours and properties to apply (guide 4600-4700).
- `CREATE HOLIDAY` writes default text from `Libraries/Quests.java:485-495`: `WAIT 900+(1?100)`, `INTERVAL 1`, `DURATION 900`, `PERSISTANCE TRUE`, the area group, `QUIET`, and a mob group selected with `RESELECT MASK=+INT 3`.
- The shipped file (2,398 bytes) defines `drought` (line 8, weather), `famine` (line 21, weather), `harsh winter` (line 34) and `newyear` (line 47, `MUDDAY 1-1`). The guide says there are three weather holidays by default (guide 4603); the file also has the date-based New Year entry.

### 12. Socials (GameBuildersGuide.html:4741-5180; resources/socials.txt)

- A social is a verb with variants for each target type: `NONE`, `ALL`, `SELF`, `MOBTARGET` (`<T-NAME>`), `ITEMTARGET` (`<I-NAME>`), `INVTARGET` (`<V-NAME>`), `EQUIPTARGET` (`<E-NAME>`), or a custom word that is paired with an argument (guide 4748-5123). The mapping is in `Libraries/Socials.java:585-615`. The help text at `Socials.java:591-593` lists `<T-NAME> (EQUIPTARGET)`, which should read `<E-NAME>`.
- Each variant has: a you-see text; an action class; an others-see text and an effect class; a target-sees text and its effect class; a you-see text for "no target"; and a sound file. The editor's classes are W (words), M (small movement or hands), L (large movement), S (sound), V (visual), H (hands) and Q (quiet move), `Socials.java:349`, `:399` and `:457`. In the data file the first letter is the source class (W speech, M hands, S noise, O large movement, Q subtle), and the second is the others and target class (T hands, S noise, W speech, V noisy movement, O visual, Q subtle), `Libraries/Socials.java:52-130`. So the file's O is the editor's L when it is the source, but the file's O is visual (the editor's V) when it is the others or target class, and the file's V is the editor's M (movement with noise).
- Pronoun tokens such as `<S-HIS-HER>` (guide 5124 onwards) expand through the `Pronoun` enum in `Libraries/CoffeeFilter.java:57-70`, which holds `-HIS-HER` and similar codes.
- Targeting: `Common/DefaultSocial.java:277-303` (`targetable`) ties each variant to a kind of target: a mob, a room item, an inventory item or an equipped item. If the target does not fit, `invoke` (345-395) looks for another social with the same base name and the argument.
- Size: `resources/socials.txt` has 843 lines, which are variants, for 338 distinct verbs (counted with `awk` on the first word of the second column). Features.html's "over 700 socials" counts variants, not verbs.

### 13. MUDChat (MUDChat.html; Behaviors/MudChat.java)

- `chat.dat` is a list of ChatGroups. Each group starts with `>name` and holds trigger lines and response lines (`MUDChat.html:124-184`). Trigger prefixes are `*` (only in combat), `L` (only when an LLM is installed) and `.` (only without one) (guide 88-96). The bracket after them selects the kind: `(` said aloud, `[` targeted action, `{` untargeted action, and `<` evaluated on each tick with an optional percentage (guide 45; `MudChat.java:509-544`). The internal kinds are `SAY`, `EMOTE`, `TEMOTE` and `RANDOM` (`MudChat.java:99-101`).
- Pattern operators (guide 103-119): `|` or, `&` and, `~` and-not, `^` start of input, `=` exact, `/` zapper mask. Parsing is in `MudChat.java:1220-1400` (partly read).
- Responses start with a weight digit from 1 to 9 (`MudChat.java:226`, `623-638`). An optional operator follows: `"` say, `:` emote, `!` command, and a backtick for an LLM prompt. `~` joins several responses on one line (`MUDChat.html:123`; response operators at `MudChat.java:1121-1144`). Variables are `$r` (rest of sentence), `$w` (matched text), `$n` (speaker), `$t` (target), `$$`, `${STAT}`, `$<STAT>` for the target, and `$%func%` (MOBPROG function). `$@group` switches the speaker's group (guide 162-167; `MudChat.java:1095-1110`).
- LLM settings: `LLMPROMPT` and `LLMMEM` set the session prompt and memory size (guide 216-235). `MudChat.java:1049-1067` creates the session. An idle session is dropped after 360000 ms (`MudChat.java:1052`). Replies wait `RESPONSE_DELAY = 2` ticks (`MudChat.java:90`), and a reply that is wrapped in quotes has them stripped (`MudChat.java:1175-1179`).
- `@group` links copy another group's entries into this one (guide 162-177). See problem 11 below for a defect in that copy.
- Data changes apply at startup, or after `UNLOAD` of the resource (guide 179-183).

### 14. Random generation (RandomAreas.html; Libraries/MUDPercolator.java)

- Grammar: AREA, ROOM, MOB, ITEM, CONTENT, EXIT, RACE, FACTION, QUEST, OBJECT, BEHAVIOR, ABILITY, AFFECT and SHOPINVENTORY (guide 554-1050). Meta-attributes are `id`, `load`, `from`, `content_load`, `like`, `insert`, `copyof`, `validate`, `condition`, `llm`, `define`, `predefine`, `tagdefine`, `select`, `merge`, `pickweight`, `action` and `requires` (guide 143-358).
- Select modes (guide 267-301) are implemented in `selectChoices` (`MUDPercolator.java:4205-4372`). `ALL` and `NONE` are direct. `FIRST`, `FIRST-X`, `LAST`, `LAST-X` take fixed positions. `LIMIT-X` takes up to X at random. `ANY` and `ANY-X` draw without replacement. `PICK-X` draws X weighted by `pickweight` (`:4267`). `REPEAT-X` draws X items with replacement (`:4349-4361`).
- `define` takes `=`, `+=`, `-=`, `*=` and `/=`. Identifiers beginning with `_` persist for the room group, and `__` for the whole area (guide 227-252). The scope rules were not traced (unverified).
- Text modifiers `l:`, `u:`, `j:`, `1:`, `p:`, `c:` and `_:` (guide 376-385) are parsed by `Varidentifier` (`MUDPercolator.java:1624-1690`) and applied at 7973-7981. `$SYSTEM_RANDOM_NAME:[X]-[Y]` builds a name from syllables (`:3581`). `(a(n))` fixes the article (`:7812`, `:7989-8002`).
- Layouts are loaded by name from `Libraries/layouts/` (`MUDPercolator.java:520-540`). The ten names are APARTMENT, BOXCITY, BOXCITYSQUARE, BRANCH, CROSS, GRIDCITY, MAZE, MESH, SPOTTEDMESH and TREE.
- GENERATE object types are STRING, AREA, MOB, ROOM, ITEM, QUEST and SCRIPT (`Commands/Generate.java:45-54`). The `FROM` clause sets the XML file (`:112`). The guide lists six types and omits SCRIPT (guide 425-440).
- Instanced areas: `StdAutoGenInstance` is an instance generator. Its passive-lapse default is 30 minutes (`Areas/interfaces/Area.java:812`), and `PASSIVEMINS` overrides it (`Areas/StdAutoGenInstance.java:900-907`). `NORESET` blocks the reset trick (`:377`).
- MQL: `SELECT: ... FROM ... WHERE ...` with narrowing terms and aggregate prefixes `MEDIAN`, `MEAN`, `COUNT` and `UNIQUE` (guide 1051-1264). The clause parser is `MQLClause` at `MUDPercolator.java:4427` onward. Execution was not traced (unverified).

### 15. The design overviews (GameOverview.html, Features.html)

- GameOverview describes three games: the fantasy game (levels, classes, skills; guide 69-268), the heroic game (superhero storylines with a PvP villain role; 270-298) and the tech game (space trade and ships; 300-441). The three match the theme bits in `Areas/interfaces/Area.java:709-713` (`THEME_FANTASY=1`, `THEME_TECHNOLOGY=2`, `THEME_HEROIC=4`) and the `MUDTHEME` setting (`coffeemud.ini:1042-1053`). FAQ Q26 gives the same three genres (guide 447-456).
- The tech section is design intent. It says "Do not gain levels" for the tech game (guide 328) and describes a Max Tech Level that rises when average player tech passes MTL-5 (guide 383-386). The code has a tech item family: 42 files in `Items/BasicTech/`, with a `TECHLEVEL` key (`Items/BasicTech/GenTechItem.java:90`). Manufacturers carry tech offsets against "Max Tech Level-10" (`Common/interfaces/Manufacturer.java:59`, `Common/DefaultManufacturer.java:47`). A grep found no global max-tech variable (unverified in full).
- The ship-console example (guide 450-640) walks through a freighter at the command line: `= altitude 25`, `= autopilot orbit`, `= speed warp 3`, `= target ship 1`, `= fire phasers`. It is a sketch; the parser for `=` commands was not traced.
- Features.html (guide 25-189) lists the claims that the guides also make: 48 classes in 7 base classes (line 40), over 2,000 spells and abilities (line 40), over 700 socials (line 43), 300 mage spells (line 128). The code has 2,559 ability classes outside `interfaces/`, 423 `Spell_` classes, 365 `Prayer_` classes and 316 `Chant_` classes. It has 56 class files in `CharClasses/` (including base and helper classes) and 251 race classes in `Races/`, most of them creatures. These are approximate and do not reconcile with the listed numbers.

## Content worth mining

Summaries of catalogues and field sets that a text game could reuse. Nothing here is copied at length.

**Mystery slots (guide 4078-4420).** A logic-problem quest is built from typed slots, each with a group form:

| Slot | Meaning | Group form |
|---|---|---|
| AGENT | the person who acted (also sets MOB) | AGENTGROUP |
| ACTION | what was done | ACTIONGROUP |
| TARGET | the person or object acted on (sets MOB or ITEM) | TARGETGROUP |
| TOOL | what was used (sets MOB or ITEM) | TOOLGROUP |
| MOTIVE | a string giving the reason | MOTIVEGROUP |
| WHEREHAPPENED / WHEREAT | the room (sets ROOM) | WHEREHAPPENEDGROUP / WHEREATGROUP |
| WHENHAPPENED / WHENAT | a time, given as hours from now or a timeclock | WHENHAPPENEDGROUP / WHENATGROUP |
| FACTION | a faction, or ANY | FACTIONGROUP |

**Quest command vocabulary.** Header keywords: `SET NAME`, `DISPLAY`, `CATEGORY`, `INSTRUCTIONS`, `DURATION`, `EXPIRATION`, `WAIT`, `INTERVAL`, `SPAWNABLE` (`FALSE`, `TRUE`, `ONCE`, `ALL`, `ALLONCE`, `ANY`, `ANYONCE`), `PERSISTANCE`, `DATE`, `MUDDAY`, `MINPLAYERS`, `PLAYERMASK`, `RUNLEVEL`, `PRESERVE`, `QUIET`, `OPTIONAL` (guide 3291-3420). Flow: `STEP`, `STEP AUTO`, `STEP BACK`, `STEP BREAK` (3421-3464). Objects: `LOAD`, `GIVE`, `TAKE`, `RESET`, `IMPORT`, plus `<OPTION>` and `<SCRIPT>` blocks.

**Faction change triggers (guide 2582-2665).** Murder, Kill, Time (about every 40 seconds, changeable with `ROUNDS=`), Add Outsider, Bribe (`PCT=`, `AMOUNT=`), Talk (`REGEX=`, `WAIT=`), MudChat, Arrested, Social (`ID=`, `ID=ALL`, `WAIT=`), and ability ID, type, domain or flag. Every trigger can add `CHANCE=` and `MASK=`.

**Social codes.** The variant targets and class codes listed in section 12 above. The sample socials are harmless emotes such as the TWIGGLE walkthrough (guide 4748-5070).

**Random-generation select modes** (`MUDPercolator.java:4205-4372`): `ALL`, `NONE`, `FIRST`, `FIRST-X`, `LAST`, `LAST-X`, `LIMIT-X`, `ANY`, `ANY-X`, `PICK-X` (weighted by `pickweight`), `REPEAT-X`, and a bare math expression for a count.

**Achievement event families (47 codes, `Libraries/interfaces/AchievementLibrary.java:71`).** Combat: KILLS, GROUPKILLS, DEATHS, CLANKILLS, SHIPSSUNK. Progression: LEVELSGAINED, LEVELUP, CLASSLEVELSGAINED, CLASSLEVELUP, LEVELDOWN, CLASSLEVELDOWN, TIMEPLAYED. Skills: CRAFTING, MENDER, DECONSTRUCTING, SKILLUSE, SKILLPROF, EFFECTSHAD, CMDUSE, SOCIALUSE. World: EXPLORE, ROOMENTER, AREAVISIT, INSTANCEEXPIRE. Social and economy: CHARITY, ENTITLED, GOTITEM, BIRTHS, RACEBIRTH, PLAYERBORN, PLAYERBORNPARENT, CHARACTERS. Clan and conquest: CLANMEMBERS, CLANLEVELSGAINED, CLANDECLARE, CLANPROPERTY, CONQUEREDAREAS, CONQUESTPOINTS. Meta: ACHIEVER, QUESTOR, SCRIPTED, JUSTBE, REMORT, RETIRE.

**Poll flags (`Common/interfaces/Poll.java:310-320`).** Active, preview results, allow abstain, vote by IP or account, hide results, and not offered at login. Each flag has a matching editor prompt (`Libraries/Polls.java:215`, `:262`).

**Plane fields (guide 2977-3178).** Level formula, MOB resistances, MOB stat trainer and adjuster, size multipliers, copies per mob, max weapon range, required weapons, atmosphere material, hours-per-day multiplier, recover rate, fatigue rate, elite level, race mix, spec flags, bonus damage stat, absorptions, room title colours, room adjectives, area and room effects, behaviours, behaviour replacements, abilities by domain or flag or level, factions, area blurbs and promotions.

**Clan governments.** `resources/clangovernments.xml` defines eleven types: GANG (line 2), GUILD (159), UNION (327), FELLOWSHIP (485), THEOCRACY (650), FAMILY (816), ANARCHY (1032), PROFESSION (1117, category CLASSCLAN), CO-OP (1200), MERITOCRACY (1326) and PLUTOCRACY (1481). c02 covers them in detail.

**Pronoun tokens** (`Libraries/CoffeeFilter.java:57-70`). HIS-HER, HIM-HER, HE-SHE, HIM-HERSELF, HIS-HERSELF, IS-ARE, HAS-HAVE, SIRMADAM, MRMS, MISTERMADAM, YOUPOSS. Each has a viewer-aware form (you, or the name, or the pronoun). This is the mechanism that lets one message read correctly to the actor, the target and the room.

**Language substitution.** The guide's model is a per-language list of nonsense words keyed by length, plus direct word replacements. A Python SUD could build an invented tongue from a few tables and get the same effect for every speaker.

**Sample chat rules.** The guide's examples (the healer ChatGroup at `MUDChat.html:130-138` and the city-guard example at `MUDChat.html:251-276`) are simple weighted trigger sets. They can be paraphrased as rule tables without copying the text.

**Design sketch: the freighter console** (guide 450-640). A menu-driven ship console with `=` for console input, `<` to go up a menu level, and navigation, engineering, sensors and tactical screens. It shows how a space game could use one command grammar for many systems.

## Notable design ideas

- **Mystery slots as a clue engine.** Splitting a crime into agent, action, target, tool, motive, place and time, each with a sampled group form, lets a generator build a solvable mystery from the world's own people and places. This could drive crimes in a living-world simulation.
- **Factions as ranged numbers with triggers.** A faction is a number with named ranges, changed by triggers that carry a chance, a source mask and cross-faction spill-over. Reputation can then gate prices, rooms and dialogue.
- **Socials with three viewpoints.** Each action has a message for the actor, a message for bystanders and a message for the target, plus a channel (visual, hearing, hands). A bystander who cannot see still hears a sound. This is a good model for perception in a text world.
- **Chat as weighted rules with inheritance.** Responses carry weights; groups can link to others with `@`; a rule can be restricted to combat or to an LLM being present; variables such as `$t` are filled at runtime.
- **Calendar events as scripted quests.** Holidays are quests with a schedule, a duration and a mask on who is affected. Seasonal weather and price changes fit the same mechanism.
- **Select operators for generation.** `PICK-X` (weighted, without replacement), `ANY-X`, `LIMIT-X` and `REPEAT-X` give a small, explicit vocabulary for choosing content. `define` scopes (`_` for a group of rooms, `__` for an area) give generation context without globals (scope rules unverified).
- **Deterministic language scrambling.** The same word always becomes the same substitute, keyed by a hash, so a conversation in an invented tongue stays consistent. Player names are passed through.
- **Planes as overlays.** A plane is a set of overrides on an existing area, with an "is like" parent. This is a compact way to describe variant worlds.
- **A file layer with explicit prefixes.** CMFS lets a database copy override a local file, with `//` and `::` to force either. Overrides and their origin are visible.

## Problems and cruft

1. **Remort level is stale.** `guides/BuildersFAQ.html:137` says remorting "allows only levels greater than 60". The default mask is `REMORTMASK=-LEVEL +>90` (`coffeemud.ini:693-696`). The player help is generated from that setting (`resources/help/help.ini:2361`), and the command checks the mask (`Commands/Remort.java:121`).
2. **Wrong DISABLE keyword.** `BuildersFAQ.html:381` says `EXPLESS` in DISABLE stops experience gain. The disable flag is `EXPERIENCE` (`core/CMSecurity.java:2942-2945`), which `coffeemud.ini:1951-1958` documents. `EXPLESS` is a per-race or per-class flag (`Races/interfaces/Race.java:576`, `CharClasses/interfaces/CharClass.java:778`), so the FAQ mixes the two.
3. **"None of it works yet" is out of date or unverified.** `BuildersFAQ.html:468-469` says the electronics do not work. The source has a 42-file `Items/BasicTech/` package. Runtime behaviour was not checked.
4. **FAQ anchors.** Anchor `q23` is used for two questions (`BuildersFAQ.html:390` and `:405`), `q36` for two (`:530` and `:539`), and `q37` is both a name and an id on different questions (`:156` and `:545`). The FAQ also links `MudChat.html` (`:265`) for a file named `MUDChat.html`.
5. **Broken guide links.** `GameBuildersGuide.html` has 32 links to `ArchonTome.html` (for example line 4088), which does not exist; the file is `ArchonGuide.html`.
6. **Random Areas table of contents.** `RandomAreas.html:34` points to `#racetag`, but the anchor is `name="raceag"` (line 890). `:30` points to `#culturalability`, but the anchor is `culturalabilitytag` (834). `:33` has `href="exittag"` with no `#`. The ITEM section reuses `name="mobtag"` (746). The text uses both `CULTUREABILITY` (908) and `CULTURALABILITY` (834).
7. **Random Areas layout list is incomplete.** `RandomAreas.html:621-625` names seven layouts. The code loads ten, including BRANCH, MESH and SPOTTEDMESH (`Libraries/layouts/`).
8. **REPEAT-X is misdescribed.** The guide says `repeat-X` "always selects every choice" (`RandomAreas.html:289-293`). The code draws X items with replacement (`MUDPercolator.java:4349-4361`).
9. **MUDChat LLM operator.** `MUDChat.html:245-246` says an LLM response is marked with a single quote, and the example uses `5'Hello LLM...`. The code uses a backtick (`Behaviors/MudChat.java:1144`). A single quote falls through to the default branch (`MudChat.java:1192`), which turns the text into a command.
10. **MUDChat header operators.** `MUDChat.html:45` describes `"` as stderr and `'` as stdout. In `parseChatData`, `"` only writes to the system log (`MudChat.java:503-505`) and `'` is not handled at all (`:501-639`).
11. **`@group` copy defect.** In `MudChat.java:596-601`, the loop builds `copyCE` with the new `fromGroup` and `orderNum`, then adds the original `CE` to the list. The tickies loop at 603-609 adds the copy correctly. For ordinary entries, linked responses keep their source group's variables and order.
12. **Social help text.** `Libraries/Socials.java:591-593` lists `<T-NAME> (EQUIPTARGET)`, which should be `<E-NAME>`.
13. **Social editor menus in the guide are old.** The guide's menus (lines 4795, 4862, 4880, 4914) omit `Q)uiet move`, which the code offers (`Socials.java:349`, `:399`, `:457`).
14. **Undocumented achievement events.** EFFECTSHAD and CMDUSE (`AchievementLibrary.java:71`) are missing from the guide's achievement table (guide 5861-6388).
15. **Undocumented faction triggers and flags.** Seven `MiscTrigger` values (SINK, SUNK, AREAKILL, AREAASS, AREAEXPLORE, DYING, CMMSG; `Faction.java:1314-1319`) and the RESTIME and ANNOUNCE flags (`Faction.java:1312`) are not in the guide.
16. **Cost-band boundary.** The guide gives the percentage band as `2147483647-100` to `2147483647` (guide 441-443). The strict test in `StdAbility.java:1617` leaves `COST_PCT` itself out, and `:1799` includes it. Builders would see different costs at that one value.
17. **Clan governments.** The guide lists six types by English name, such as "tyranny (Clan)" and "democracy (Fellowship)" (guide 5347-5350). The XML has eleven, and the first is `GANG` (see section 14 above; c02 covers this).
18. **Class-system modes.** The guide gives four modes (guide 2052-2067). `coffeemud.ini:466-495` documents at least fourteen, with cost keys.
19. **Example path.** The example `LOAD=quests/murdermystery/murdermystery.quest` (guide 3260) does not exist in `resources/quests/` (which holds `holidays`, `random` and `templates`).
20. **CREATE help omits PLANE.** `Commands/Create.java:1726-1731` leaves PLANE out of the object list, although `:2028` implements it.
21. **Holiday count.** The guide says three weather holidays (guide 4603). `holidays.quest` has four entries, the fourth being the date-based New Year at line 47.
22. **Marketing counts.** Features.html's "over 700 socials" (line 43) counts 843 variant lines for 338 verbs. "48 classes in 7 base classes" (line 40) and "300 spells" (line 128) do not reconcile with the class and file counts above.
23. **Quest-script keywords undocumented.** `AUTHOR` and `QUESTTYPE` (`Common/interfaces/Quest.java:826-832`) are not in the guide, although templates use `AUTHOR`.
24. **Mobile freeze claim unverified.** `BuildersFAQ.html:319-321` says mobile mobs stop moving when an Archon with SYSMSGS is in the room. `Behaviors/Mobile.java:325-360` has no check for CMDROOMS or ORDER. The aggressive exemption that the FAQ also describes is real (`Behaviors/Aggressive.java:198-199`).
25. **Skill naming trap.** The FAQ calls the skill "Marketeering" (guide 501-514). Its ID is `Merchant` (`Abilities/Common/Merchant.java:42-45`), and the help key maps `MERCHANT=MARKETEERING` (`resources/help/common_help.ini:1222`). Builders who type the display name will not find the class.
26. **Design essay read as spec.** GameOverview's tech and heroic sections are intentions. The guide itself calls them "ideas" in places, but Features.html presents them as shipped features.
27. **Multiplayer assumptions.** The guides assume player-versus-player, clans and conquest, polls by IP or account, boards, and player children. These matter for a single-player port (see the takeaways).

## Takeaways for a Python SUD

- **Use the guides' field tables as a schema.** The ability, faction, plane, class, trap and poison editors are already lists of typed fields. A Python version could keep them as dataclasses loaded from YAML or TOML, with the defaults in code. A sketch:

```python
from dataclasses import dataclass, field
from typing import Callable

@dataclass(frozen=True)
class FactionRange:
    code: str            # the zapper-mask code, e.g. "BADDY"
    name: str
    low: int
    high: int

@dataclass
class ChangeTrigger:
    event: str           # "kill", "bribe", "talk", "social", "time", ...
    direction: str       # "up", "down", "toward", "away", "opposite", ...
    factor: float = 1.0  # share of the change applied
    chance: float = 1.0
    source_ok: Callable[[object], bool] | None = None   # replaces MASK=

@dataclass
class ChatRule:
    pattern: str                                       # "hello|hi", "healer&cure", "kill~self"
    responses: list[tuple[int, str, str]] = field(default_factory=list)  # (weight 1-9, kind, text)
    needs_llm: bool | None = None                      # None: either, True: only with LLM
```

- **Borrow the mystery slots.** A simulated world can generate a crime as a set of filled slots (agent, action, target, tool, motive, place, time) and then let an investigation ask about each slot. This is more reusable than a fixed plot.
- **Borrow factions with ranges and triggers.** Reputation with named ranges, changed by a small set of events and gated by simple predicates, covers prices, access and dialogue in one structure. Keep the trigger list short (the guide's sixteen are mostly multiplayer or combat hooks).
- **Borrow socials as three messages plus a channel.** Keep the actor, bystander and target texts, and let each observer see or hear the one that applies. Pronoun expansion can be a small function of the observer's relation to the actor, not the token table.
- **Borrow weighted chat rules, drop the rest.** Weighted responses and `@`-style group links are useful for NPC talk. Keep the simple operators (`|`, `&`, `~`) and drop zapper-mask syntax in favour of Python predicates.
- **Borrow calendar events as scheduled quests.** Holidays and seasonal changes map to scheduled world events with a duration and a set of affected places.
- **Borrow deterministic language scrambling.** Hash the word to pick a substitute so one invented language stays consistent. Keep player names (here, the player's own name) unchanged.
- **Simplify quest scripts into state machines.** The STEP, BREAK, BACK and PRESERVE vocabulary is a state machine with a list of objects to clean up. In Python, use an enum of steps and a dict of owned objects.
- **Drop the multiplayer parts.** Polls by IP, clans and their governments, conquest, boards, player-versus-player, PK settings, child-rearing between players, and marketeering shops for other players are not needed. Where the guides assume other humans (the heroic game's villain role, GameOverview 290-294), the SUD equivalent is an agent in the simulation.
- **Keep the LLM hook optional.** MUDChat's LLM path is a clean hook (prompt variables, memory size, idle expiry). A single-player game can use it for dialogue without making it required.

## Coverage

- **Read in full:** `guides/Reference.html`, `guides/HowToPlay.html`, `guides/Features.html`, `guides/GameOverview.html`, `guides/MUDChat.html`, `guides/BuildersFAQ.html`, and `guides/RandomAreas.html` (lines 1-1281 in three reads).
- **Read in full as text, with gaps:** `guides/GameBuildersGuide.html`. Lines 1-3538 (CMFS, abilities, languages, crafting, gathering, wrighting, traps, poisons, components, all-qualify, recipes, classes, factions, planes) were read in full. Lines 3850-4591 (the quest commands, mystery, MOBPROG and JavaScript sections) were read as text, except lines 4200-4380 (the rest of the mystery SET forms), which were read only through keyword matches. Lines 3539-3849 (the SET list and object specifiers) were skimmed through a keyword listing. Holidays, socials, polls, clans, governments, awards, titles and achievements were read as text. Generic commands were read to the end of the Script field.
- **Code read or searched:** `Behaviors/MudChat.java` (481-650, 796-815, 1049-1067, 1095-1210 and the operator parsing in part), `Libraries/MUDPercolator.java` (520-540, 1624-1690, 4205-4372, and grep for the rest), `Libraries/Socials.java` (50-130, 349-400, 455-490, 585-630), `Common/DefaultSocial.java` (277-303, 345-400), `Libraries/CoffeeFilter.java` (57-70), `Common/DefaultQuest.java` (182-225, 300-340 by grep, 1106-1170, 1202-1212, 2542-3266 by grep, 3351-3791 by grep), `Common/interfaces/Quest.java` (826-832), `Libraries/Quests.java` (45, 171-191, 476-495), `Common/DefaultScriptingEngine.java` (grep only), `Common/interfaces/Faction.java` (1290-1345), `Common/interfaces/Poll.java` (300-330), `Libraries/Polls.java` (215, 262), `Libraries/interfaces/AchievementLibrary.java` (71-, full enum), `Abilities/interfaces/Ability.java` (645-660), `Abilities/StdAbility.java` (1608-1625, 1795-1806, 1995-2030), `Abilities/Languages/StdLanguage.java` (200-260), `Abilities/interfaces/PlanarAbility.java` (334-339), `Libraries/CMGenEditor.java` (10188-10230 in part), `Libraries/CoffeeMaker.java` (55-130, 5245-5330), `core/CMFile.java` and `core/CMSecurity.java` (grep and 1611-1628, 2942-3011), `core/CMProps.java` (2735-2750, 3300-3330), `Commands/Create.java` (1722-1735, 2026-2035), `Commands/Generate.java` (45-54, 95-120, 200-215), `Commands/Remort.java` (121-124), `Behaviors/Aggressive.java` (190-205), `Behaviors/Mobile.java` (325-380), `Libraries/Sense.java` (1480-1500, 1630-1643, 2252-2262), `Areas/interfaces/Area.java` (705-716, 812), `Abilities/Common/Merchant.java` (42-46), `WebMacros/RebuildReferenceDocs.java` (40-99), `coffeemud.ini` (466-495, 551, 693-696, 1042-1059, 1951-1972), and `resources/quests/holidays/holidays.quest`.
- **Skimmed or grep-only:** `Libraries/layouts/*.java` (names only), `Libraries/MUDPercolator.java` MQL and define scope handling, `Items/BasicTech/` (count and one class), `Races/` and `CharClasses/` (counts only).
- **Not covered here:** the recipe loader and recipe data (c04, c05), clan governments in depth (c02), the socials text (c03), quest templates and holiday data (c06, c07), help files (c08), and the MUDChat content in `resources/chat.dat` (c03). Zapper-mask syntax, the `=` ship parser, trap and poison application, component checking, and JavaScript evaluation are unverified.
