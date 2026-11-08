# Player help files

> Part of the CoffeeMud NEWOVERVIEW. Bucket: `c08` (content: Player help files) · 14 files · 1,078,011 bytes of data.

Citation convention: data paths are relative to the CoffeeMud folder (`resources/help/...`). Java paths are relative to `com/planet_ink/coffee_mud/`, so `Libraries/MUDHelp.java:1756` is the file `com/planet_ink/coffee_mud/Libraries/MUDHelp.java`. Entry counts come from parsing every data file with a script that follows `java.util.Properties` rules (continuation lines, `!`/`#` comments, last definition wins). Where a number is a heuristic, the text says so.

## Summary

Player help is data, not code: 11 `Properties`-format `.ini` files hold 5,614 entries (5,592 distinct keys) and 2,821 alias lines, and three plain-text screens give the `HELP` index and the account menus. Of the entries, 2,037 are `<ABILITY>` templates whose header the engine fills in at display time from the live ability object (class availability and level, prerequisites, cost, targets, range, commands, and the viewer's proficiency in words), and the same mechanism builds `<RACE>`, `<CHARCLASS>` and `<CURRENCY>` text from game objects. For a Python SUD the files are most useful as a catalogue (419 spells, 364 prayers, 316 chants, 114 songs and dances, 665 skill entries, 156 crafts and trades, 79 traps and bombs, 25 poisons, 69 diseases, 35 races, 50 classes) and for the idea of generated help. The prose quotes several numbers that the code does not implement, and the loader has silent duplicate-key and encoding faults, so treat the text as a source of ideas rather than a spec.

## Key files and types

| File or class | Role | Lines |
|---|---|---|
| `resources/help/skill_help.ini` | Skill and general ability help: thief, fighter, paladin, misc. (665 ability blocks) | 6,324 |
| `resources/help/spell_help.ini` | Spells (419 ability blocks) | 3,413 |
| `resources/help/prayer_help.ini` | Cleric prayers (364) | 2,869 |
| `resources/help/chant_help.ini` | Druid chants (316) | 2,596 |
| `resources/help/songs_help.ini` | Bard songs (SING), instrument songs (PLAY), dances (DANCE): 114 | 800 |
| `resources/help/common_help.ini` | Common skills: crafts, gathering, trade, scholarship (156) | 2,522 |
| `resources/help/misc_help.ini` | Config option help, race and class templates, currencies, concept topics, macros | 2,245 |
| `resources/help/help.ini` | Player commands (266 command blocks) and short topics | 3,158 |
| `resources/help/traps.ini` | Traps (65 `TRAP_*`) and bombs (14 `BOMB_*`) | 667 |
| `resources/help/poisons.ini` | Poisons (25 entries) | 201 |
| `resources/help/diseases.ini` | Diseases (69 entries, one of them a template) | 655 |
| `resources/help/help.txt` | Text of the bare `HELP` index screen | 22 |
| `resources/help/accts.txt`, `acctmenu.txt` | Account command list and account menu (character creation screens) | 13, 12 |
| `Libraries/MUDHelp.java` | Loader, lookup chain, template expansion, search, editing (2,104 lines) | 2,104 |
| `Libraries/interfaces/HelpLibrary.java` | Interface; `HelpMatchType` enum at line 277 | 294 |
| `Commands/Help.java` | The `HELP` command: index, match, "See also", no-match text | 118 |
| `Commands/Reset.java` | `helpSets` mapping of files to ability filters (62-203); `RESET SORTHELP` (674-860) | 2,669 |
| `Commands/Config.java` | `CONFIG` help: reads the nested `CONFIG_HELP_OPTIONS` block (92-120) | 559 |
| `Commands/AHelp.java` | Archon help (`AHELP`), backed by `arc_*.ini` | 189 |
| `Commands/Create.java`, `Modify.java`, `Destroy.java` | In-game create, edit and delete of help entries (610-612, 2465, 541) | 2,682, 3,427, 2,466 |
| `Commands/Unload.java` | `UNLOAD HELP` drops the cached help (236-241) | 629 |
| `core/CMFile.java` | Directory listing used by the loader; `listFiles()` at 1701 | 2,306 |
| `Libraries/MUDZapper.java` | `maskHelp()` (510-520) fills `<ZAP=...>` text | 10,244 |
| `Libraries/Dice.java` | `roll(number, die, modifier)` (398-405), used by poison damage | 414 |
| `Libraries/CharCreation.java` | Loads `accts.txt` (1961) and `acctmenu.txt` (1867) | 5,394 |

Mechanics checked against code in this chapter: `Abilities/Paladin/Paladin_AbidingAura.java` (113), `Abilities/Poisons/Poison.java` (492), `Abilities/Poisons/Poison_Heartstopper.java` (123), `Abilities/Poisons/Poison_Caffeine.java`, `Abilities/Prayers/Prayer_AiryForm.java` (160), `Abilities/Prayers/Prayer_DivineResistance.java` (193), `Abilities/Diseases/Disease_Asthma.java` (161), `Abilities/Diseases/Disease_Vampirism.java`, `Abilities/Spells/Spell_Advancement.java`.

## How it works

### 1. File format

Each file is `java.util.Properties` text. `MUDHelp.getHelpFile()` reads the raw bytes with `Properties.load(InputStream)` (`Libraries/MUDHelp.java:1777`), so Java's rules apply:

- `KEY=value`. A trailing backslash continues the value on the next line, and leading spaces on continuation lines are dropped.
- Lines beginning with `!` or `#` are comments. Only `help.ini:1` (`!=EXCLAMATION_POINT`) is affected, and it never loads (see Problems).
- Escape sequences are converted at load time. The text is hand-typed as one long line with a literal `\n\r` at each break, and the value ends each physical line with `\`. Example from `spell_help.ini:2-3`:

```
SPELL_ACHILLES_ARMOR=<ABILITY>\
Usage    : CAST "ACHILLES ARMOR" [TARGET NAME] \n\r\
```

- **Aliases.** A line `KEY=OTHER_KEY` points one name at another. There are 2,821 such lines (for example `SPELL_ACHILLESARMOR=SPELL_ACHILLES_ARMOR` at `spell_help.ini:1`), which cover typos, short forms and synonyms.
- **Tags.** A value can start with a tag. The tags used are `<ABILITY>` (2,037 entries), `<RACE>` (35, all in `misc_help.ini`), `<CHARCLASS>` (50, all in `misc_help.ini`), `<CURRENCY>` (9) and `<CURRENCIES>` (6). `<CALENDAR>` appears once, at `help.ini:2791`. `<EXPERTISE>` is read by `fixHelp` but its data lives in `resources/skills/expertises.txt` (another bucket). `<COLORS>` and `<COLORS256>` are supported but unused in these files. Inside a value, `<ZAP=disallow>` appears once (`help.ini:792`).
- **Macros.** `@...@` tokens (68 in `misc_help.ini`, plus `help.ini` and the account files) are passed through `CMLib.webMacroFilter().virtualPageFilter()` when the text contains `@` (`Libraries/MUDHelp.java:836-841`). For example `RACES` at `misc_help.ini:1271` is `Try HELP@RaceNext?Reset@:@loop@@RaceNext@ @RaceName@@back@.`, and `misc_help.ini:57` shows `@INIValue?MASK=LASTPLAYERLEVEL&VALUE@` inside a sentence. The macro semantics were not traced.
- **Conditional prose.** `misc_help.ini:1268-1270` in `INCLINATION` adds the "See also help on alignment" sentence only inside `@if?CheckFactionLoaded?ALIGNMENT.INI@ ... @endif@`.
- **Backtick apostrophes.** The ability text writes `target`s` with a backtick, 139 times in the six ability files. The backtick is shown to the player.

### 2. Loading and duplicate keys

- `getHelpFile()` (`Libraries/MUDHelp.java:1756-1817`) loads every `.ini` in `resources/help/` whose name does not start with `ARC_` (1771-1774). It merges them into one `Properties` with `putAll` (1778), applies the `<ZAP=` expansion (1791-1807), and caches the result under "MAIN HELP FILE" (1808).
- `getArcHelpFile()` (`Libraries/MUDHelp.java:1695-1753`) loads only the `arc_*.ini` files (`arc_behaviors`, `arc_help`, `arc_misc`, `arc_properties`), which are archon-only and outside this bucket.
- The merged result has 5,592 keys from 5,614 entries. Duplicates are resolved by position: a later line overwrites an earlier one in the same file, and a later file overwrites an earlier file (`putAll`). The file order comes from `CMFile.listFiles()` (`core/CMFile.java:1701`), which concatenates VFS and disk entries without sorting, so the winner across files depends on the directory order of the host. Eight keys are defined in two player files (listed in Problems).

### 3. Lookup: from a typed word to a text

`getHelpText(helpKey, rHelpFile, forM, noFix, skip, exactOnly)` (`Libraries/MUDHelp.java:1005`) is a fall-through chain. The steps in order:

1. **Normalise.** Upper-case, trim, spaces become `_` (1009-1013). The typed `!`, `,`, `:` and `;` are remapped to `EXCLAMATION_POINT`, `COMMA`, `COLON` and `SEMICOLON` (1015-1022), which is needed because `Properties` would otherwise lose the `!` key.
2. **Callouts.** `CHANNEL_`/`NOCHANNEL_` (1028-1053), `AREAHELP_` (1056-1070), `GENCHARCLASS_` and `GENRACE_` (1072-1116) and `SOCIAL_` (1117-1126) are handled before the file is consulted.
3. **Exact key, then prefixes.** The exact key is tried, then each of `SPELL_`, `PRAYER_`, `SONG_`, `DANCE_`, `PLAY_`, `CHANT_`, `BEHAVIOR_`, `POWER_`, `SKILL_`, `PROP_` (`SKILL_PREFIXES`, lines 52-56; probing at 1130-1145). So a player who types `fireball` reaches `SPELL_FIREBALL`.
4. **Other registries.** Socials (1147-1160), clan governments (1161-1173), generic abilities with a `HELP` stat (1174-1188), expertises (1189-1201), char classes and races (1202-1238), achievements (1239-1242), areas (1243-1260), deities (1261-1282), channels (1284-1310) and commands with a `HELP` stat (1312-1330). Archon-only Scriptable method help is read from `guides/Scriptable.html` (1332-1336, `getScriptableHelp` at 874-980).
5. **Recipe sentence (player file only).** If a craft recipe matches the word, the reply is built from `"@x1 is an item that is craftable by @x2."` (1339-1361).
6. **Suffix stripping.** Keys ending in `_SPELL`, `_PRAYER`, `_SONG`, `_DANCE`, `_PLAY`, `_CHANT_`, `_POWER_`, `_SKILL_` are cut and retried (`SKILL_SUFFIXES`, lines 58-62, used at 1582-1596).
7. **Inexact.** A key that starts with the typed text (1377-1392), then currency amounts (1393-1410), then any key whose entry text contains the typed words (1412-1427). Further fallbacks run to line 1636 and were not all traced.
8. **Redirects.** While the current text is shorter than 31 characters and names another key, the lookup follows it (1636-1652). A text that names itself ends the lookup with no result.
9. **Fix-up.** `fixHelp()` expands tags and macros (1659).

### 4. Templates expanded at display time

`fixHelp(tag, str, forM)` (`Libraries/MUDHelp.java:458-843`) runs on every result that is not requested raw. It is the reason the help can show live numbers.

**`<ABILITY>` (633-833).** The header is assembled as follows. The type word (Spell, Prayer, Chant, Song, Skill or SuperPower, 713-733) is padded to nine characters. Then come the name, then `(Proficiency: ...)` for the viewer (741-746). Next are `Domain:` from the classification bits (748-753) and an `Available:` list of classes with the level at which each gets it, from `CMLib.ableMapper().getAvailabilityList(A, 2)` (754-766). `Requires :` gives prerequisites (772-776) and the zapper-mask requirement (777-783). `Allows :` lists the expertises and abilities that this one allows (784, `appendAllowed` at 403-443). Prayers also show each faction's alignment range (785-798). Components (803-808), `Use Cost`, `Quality`, `Targets`, `Range` and `Commands` follow (809-827). Passive abilities print `Invoked  : Automatic` instead (829-830). The entry's own prose comes last.

Schematic of the output shape (labels taken from the strings at `MUDHelp.java:713-830`; values omitted, and not captured from a running game):

```
Spell    : <name>   (Proficiency: <band>)
Domain   : <domain>
Available: <class>(<level>), ...
Requires : <prerequisites>
Use Cost : ...   Quality  : ...   Targets  : ...   Range    : ...
Commands : <trigger words>
<prose from the .ini entry>
```

**Proficiency words.** The numeric proficiency is shown as one of 14 bands when `COMBATPROWESS` is set to the word style (461-462, 741-745). The thresholds are in `proficiencyRanges` (`MUDHelp.java:64`) and the labels come from `SKILL_PROFICIENCY_DESC` in `resources/lists.ini:812-826`: Beginner (up to 5), Novice (10), Adv. Novice (20), Journeyman (30), Adv. Journeyman (40), Intermediate (50), Skilled (60), Very Skilled (70), Talented (75), Very Talented (80), Advanced (85), Very Advanced (90), Near Mastery (95), Mastery (100). `getRPProficiencyStr` (89-101) picks the band. A value above 100 would fall through to Beginner (ordinal stays 0).

**`<RACE>` (572-626).** Race text is built from the race object: abilities, languages, life expectancy (from the aging chart's ancient age), XP adjustment when non-zero, immunities and outfit. An empty help body reads "No further information available" (`MUDHelp.java:1108`, 1234).

**`<CHARCLASS>` (533-571).** Max stats, stat qualifiers, race qualifiers, prime stat, attack points, practices, trains, hit points, mana, movement, damage points, weapon and armour limits, bonuses and limits, all read from the class object.

**`<CURRENCY>` and `<CURRENCIES>` (463-514).** A denomination table is built from `CMLib.beanCounter()`. The lowest denomination is labelled "exchange rate is @x1 of base" (505).

**`<CALENDAR>` and `<COLORS>` (627-632).** Local time and calendar, and the colour tables from `CMLib.color()`.

**`<ZAP=...>` (load time, 1795-1806).** Replaced by the zapper-mask help text from `MUDZapper.maskHelp()` (`Libraries/MUDZapper.java:510-520`), which substitutes `<WORD>` and `<BR>`. The `CLANQUAL` help therefore lists the codes the mask language actually accepts.

### 5. Regeneration and editing

- **`RESET SORTHELP`** (`Commands/Reset.java:674-860`, requires the RESET security flag at 57-60). The `helpSets` enum (62-203) names the file and an ability filter for each family: SKILLS, CHANTS, COMMON, PRAYERS, SONGS, SPELLS, TRAPS, POISONS, DISEASES, and PROPERTIES (archon). The command groups each block (the alias lines plus the body) under its key, matches each ability to a block by display name and then by ID (`Reset.java:764-789`), warns about "Not found", "Mis found", "Re found" and "Unused" blocks, sorts by key, and writes the file after a y/N prompt (841-859). It is the only tool that keeps the families in order.
- **In-game editing.** `addModifyHelpEntry()` (`Libraries/MUDHelp.java:1874-1983`) rewrites one key's block through the journal message editor (1946) and then reloads. It is called from `Create.java:612` (`newhelp.ini` or `arc_newhelp.ini`), `Modify.java:2465` and `Destroy.java:541`.
- **Reload.** `UNLOAD HELP` (`Commands/Unload.java:236-241`) calls `unloadHelpFile()` (`MUDHelp.java:2042`), which clears the cached "MAIN HELP FILE" and "ARCHON HELP FILE" resources so the next lookup re-reads the files.

### 6. Search, "See also" and messages

- Bare `HELP` shows `help/help.txt` (`Commands/Help.java:60-61`). Its sections are MOVEMENT, COMMUNICATION, COMBAT/SPELLS/SKILLS, INFORMATION, OBJECTS, GROUPS, OTHER and BASE CLASSES.
- After a match, `Help.java:74-89` appends `See also help on: ...` with up to five keys. These come from `getSeeAlsoHelpOn()` (`MUDHelp.java:1986-2041`), which re-runs the match with a skip count to find neighbours.
- When nothing matches, `getHelpList()` (`MUDHelp.java:1664-1694`) searches key names and entry text, and `Help.java:97` shows the matches. Bug: the archon loop at line 1686 reads each property from the player file (`rHelpFile1`) instead of its own file (see Problems).
- Strings a player sees: `No help is available on '@x1'.` followed by `Enter 'COMMANDS' for a command list, or 'TOPICS' for a complete list, or 'HELPLIST' to search.` (`Help.java:101-102`); `(Proficiency: @x1)` (`MUDHelp.java:745`); `Invoked  : Automatic` (830); `No further information available` (1108); `@x1 is an item that is craftable by @x2.` (1355).

### 7. Mechanics the help describes, checked against code

The help is a reasonable guide to these, with the exceptions in the next section.

- **Abiding Aura** (`skill_help.ini:17`). `Paladin_AbidingAura.okMessage()` (`Abilities/Paladin/Paladin_AbidingAura.java:67-111`) vetoes a `TYP_DEATH` message for a paladin's group member, only if the paladin's proficiency check passes (76) and the target lacks Dueling (78). It sets hit points to 1 (81), prints `<T-YOUPOSS> death is prevented by <S-YOUPOSS> abiding aura!` (82), and schedules a wall-clock check with `CMLib.threads().scheduleRunnable(runnable, 10000)` (105). If hit points are below `maxHP * 0.10` when the check runs (95), the death is posted. The player sees "The aura will protect you for only 10 seconds, after which you must be above 10% hit points to survive." (106). This is real-time, not tick-based, which matters for a turn-based SUD.
- **Poison ticks** (`Abilities/Poisons/Poison.java:286-302`). Each tick decrements `poisonTick` (291), resets it to `POISON_DELAY()` (293) and calls `CMLib.combat().postDamage` with `POISON_DAMAGE()` (302). Duration comes from `POISON_TICKS()` through `maliciousAffect` (452).
- **Heartstopper** (`Abilities/Poisons/Poison_Heartstopper.java`). `POISON_TICKS` is 8 (61) and `POISON_DELAY` is 1 (67). Damage is `CMLib.dice().roll(1,19,(int)Math.round(rank))` (103), that is 1d19 plus rank (`Libraries/Dice.java:398-405`). Rank defaults to 1.0 (`Poison.java:170`) and can be set by the `RANK` parameter (181).
- **Asthma** (`Abilities/Diseases/Disease_Asthma.java`). `DISEASE_TICKS` is 99999 (92), so the condition lasts until cured, and `DISEASE_DELAY` is 5 (98). Each `affectCharState` call divides the movement state by four (159).
- **Divine Resistance** (`Abilities/Prayers/Prayer_DivineResistance.java`). Adds +100 to every saving throw. The `okMessage` override counts down `prots`, which starts at 4 (90), and decrements it each time a malicious message of a saving-throw type or a trap tool is blocked (139). At zero the prayer ends (141).
- **Airyform** (`Abilities/Prayers/Prayer_AiryForm.java:105-118`). In `okMessage`, damage whose tool is an `Item` is multiplied by 0.75 (118). It is not halved.
- **Advancement** (`Abilities/Spells/Spell_Advancement.java:94, 102`). Adds one to the level and one to the class level, as the help says.
- **Caffeine** (`Abilities/Poisons/Poison_Caffeine.java:131`). Adds `CMProps.getSpeedAdjustment()*0.25*rank` to speed, and clears the sleeping, sneaking and sitting dispositions.

### 8. Numbers in the help against the code

| Help text (file:line) | What the code does | Verdict |
|---|---|---|
| Airyform "takes only half damage from physical weapon sources" (`prayer_help.ini:35`) | Damage from an `Item` tool is multiplied by 0.75 (`Prayer_AiryForm.java:118`) | Wrong: 25% reduction, not 50% |
| Divine Resistance "completely blocks three harmful affects" (`prayer_help.ini:860`) | `prots=4` (`Prayer_DivineResistance.java:90`) | Wrong: four blocks |
| Heartstopper "heavy damage for a few rounds (100hp total)" (`poisons.ini:98`) | 8 hits of 1d19 + rank (`Poison_Heartstopper.java:61, 67, 103`), about 88 on average at the default rank of 1.0 | Roughly; 100 needs rank of about 2.5. Exact tick count unverified |
| Asthma "lowers ones movement rate by 75%" (`diseases.ini:80`) | Movement state divided by four in `affectCharState` (`Disease_Asthma.java:159`) | Same size of effect; the help says "rate", the code changes the movement value |
| Vampirism "lowers charisma by 1" (`diseases.ini:614`) | `chaBonus` defaults to -1 (`Disease_Vampirism.java:139`) | Consistent with the default |
| Abiding Aura "10 seconds ... 10 percent" (`skill_help.ini:17`) | 10000 ms and `0.10 * maxHP` (`Paladin_AbidingAura.java:95, 105`) | Consistent |
| Advancement "increases the level ... by one" (`spell_help.ini:46`) | Level +1 and class level +1 (`Spell_Advancement.java:94, 102`) | Consistent |
| Caffeine "Gives the person a 1/4 attack" (`poisons.ini:155`) | Speed +0.25 per rank (`Poison_Caffeine.java:131`) | Plausible; "attack" not traced |

## Content worth mining

### Abilities by family

| Family | Source | Count | Usage verb and notes |
|---|---|---|---|
| Spells | `spell_help.ini` | 419 | `CAST`; every entry has a usage line |
| Prayers | `prayer_help.ini` | 364 | `PRAY` (355); a few use other forms |
| Chants (druid) | `chant_help.ini` | 316 | `CHANT`, with the words in quotes |
| Songs (sung) | `songs_help.ini` | 43 | `SING` |
| Instrument songs | `songs_help.ini` | 41 | `PLAY`; some must be started in combat |
| Dances | `songs_help.ini` | 30 | `DANCE` |
| Skills (thief, fighter, paladin, other) | `skill_help.ini` | 665 | Keys with `SKILL_` 89, `THIEF_` 77, `FIGHTER_` 32, `PALADIN_` 18; 173 keys have no underscore. 229 entries have no usage line |
| Common skills (craft, gather, trade) | `common_help.ini` | 156 | Varied verbs; a few entries have no example |
| Misc. ability blocks | `misc_help.ini`, `diseases.ini` | 3 | `BADREPUTATION`, `SLOWLEARNER`, `DISEASE_TOURETTES` |

### Spells: sample (paraphrased)

| Key (line) | Usage | What the text says |
|---|---|---|
| `SPELL_FIREBALL` (1174) | `CAST FIREBALL [TARGET NAME]` | A fireball at the target, hurting them badly |
| `SPELL_ACID_ARROW` (18) | `CAST "ACID ARROW" [TARGET NAME]` | An acid arrow that adds acid damage each round |
| `SPELL_ACID_FOG` (25) | `CAST ACID FOG` | An acid cloud that damages those who stay in it |
| `SPELL_ACID_SPRAY` (33) | `CAST "ACID SPRAY" [TARGET]` | Acid that keeps eating at the target after the first hit |
| `SPELL_ACHILLES_ARMOR` (2) | `CAST "ACHILLES ARMOR" [TARGET NAME]` | Near-immunity except to one hidden damage type, which kills |
| `SPELL_ADD_LIMB` (40) | `CAST "ADD LIMB" [TARGET NAME]` | Grows a third arm (more ring and item slots) |
| `SPELL_ADVANCEMENT` (46) | `CAST ADVANCEMENT [TARGET NAME]` | Temporary +1 level, usable for training and for casting |
| `SPELL_ALARM` (53) | `CAST ALARM [TARGET NAME]` | Alarm on a room object; fires once when touched or moved |
| `SPELL_FLAGPORTATION` (1180) | `CAST FLAGPORTATION ([CLAN NAME])` | Teleports the group to a random clan flag; needs a high clan member (usage line has a stray quote) |
| `SPELL_MYSTIC_LOOM` (2206) | `CAST "MYSTIC LOOM"` | A loom that turns materials into cloth, using the Textiling skill |
| `SPELL_MYSTIC_SHINE` (2215) | `CAST "MYSTIC SHINE" [TARGET]` | Light source on a metal item that repels darkness magic |
| `SPELL_TORTURE` (3126) | `CAST "TORTURE" [TARGET NAME]` | The target is in agony and takes damage; bystanders may hear it give information |
| `SPELL_TOURETTES` (3133) | `CAST TOURETTES [TARGET NAME]` | Gives the target a contagious disease causing involuntary outbursts |

### Prayers and chants: sample

| Key (line) | Usage | What the text says |
|---|---|---|
| `PRAYER_ABSORPTION` (1) | `PRAY ABSORPTION [CRIMINAL NAME]` | Takes one random skill from the target; the cleric knows it while the prayer lasts |
| `PRAYER_ACID_HEALING` (9) | `PRAY "ACID HEALING"` | An aura that turns acid damage into an equal amount of healing |
| `PRAYER_ADDICTING_CURSE` (17) | `PRAY "ADDICTING CURSE" [TARGET ITEM]` | Curses food or drink so the next consumer becomes addicted |
| `PRAYER_ADOPTION` (23) | `PRAY ADOPTION [PARENT NAME] [CHILD NAME]` | Blesses an adoption between two players; creates or joins a family |
| `PRAYER_AIRYFORM` (31) | `PRAY AIRYFORM` | Become air-like and take less weapon damage (help says half; code 0.75) |
| `PRAYER_CHRISTEN` (452) | `PRAY CHRISTEN [BABY] [NAME]` | Names a mud-born child; must be done before it learns to walk |
| `PRAYER_DARK_SENSES` (672) | `PRAY "DARK SENSES" [TARGET NAME]` | Lets the target see in the dark |
| `PRAYER_DIVINE_RESISTANCE` (856) | `PRAY "DIVINE RESISTANCE" [TARGET NAME]` | +100 to saves and blocks saving-throw-type harm (help says three blocks; code four) |
| `CHANT_ACID_RAIN` (2) | `CHANT "ACID RAIN"` | Rain around the druid becomes acid and damages everyone in it |
| `CHANT_ACID_WARD` (9) | `CHANT "ACID WARD"` | Resistance (not immunity) to acid attacks |
| `CHANT_AIR_WALL` (17) | `CHANT "AIR WALL"` | Air wall that deflects missiles fired at the druid |
| `CHANT_AIRY_AURA` (25) | `CHANT "AIRY AURA"` | Group members in the room can breathe underwater |
| `CHANT_BREATHEAIR` (1157) | `CHANT "LAND LUNGS" [TARGET]` | Lets the target breathe air (key name and usage words differ) |
| `CHANT_DELAY_POISON` (474) | `CHANT "DELAY POISON" [TARGET]` | Suspends poison effects for the chant's duration |

### Songs, instrument songs and dances: names

- **Sung songs (43):** ARMOR, AWARENESS, BABBLE, BLASTING, CLUMSINESS, COMPREHENSION, DEATH, DEXTERITY, DISGUST, DISTRACTION, DRUNKENNESS, ENCHANT_INSTRUMENT, FLIGHT, FLYING, FRIENDSHIP, HEALTH, KNOWLEDGE, LETHARGY, LIGHTNESS, LULLABY, MANA, MERCY, MUTING, NOTHING, ODE, PROTECTION, PIRATESHANTY, QUICKNESS, RAGE, READ_MUSIC, REBIRTH, RECHARGE_INSTRUMENT, RESTORE_MUSIC, REVELATION, SAUDADE, SEEING, SERENITY, SILENCING, SINGLE_MINDEDNESS, STRENGTH, SUAVE, THANKS, VALOR. Example: `SONG_ARMOR` (`songs_help.ini:7`) makes the bard's group harder to hit; `SONG_LULLABY` (358) makes enemies fall asleep.
- **Instrument songs (41), `PLAY_`:** ACCOMPANIMENT, BACKGROUND, BALLAD, BATTLEHYMN, BLUES, BREAK, CAROL, CHARGE, CLARINETS, CYMBALS, DIRGE, DITTY, DRUMS, FLUTES, GUITARS, HARMONICAS, HARMONY, HARPS, HORNS, LOVE_SONG, LULLABIES, MARCH, MELODY, MYSTICAL, OBOES, ORGANS, PIANOS, RETREAT, REVEILLE, RHYTHM, SOLO, SPIRITUAL, SYMPHONY, TEMPO, TRIBAL, TROMBONES, TRUMPETS, TUBAS, VIOLINS, WOOD_CLAPPERS, XYLOPHONES. `PLAY_BATTLEHYMN` (51) gives damage bonuses and must start in combat.
- **Dances (30), `DANCE_`:** BALLET (37, a group Dexterity bonus), BASSE, BUTOH, BUTTERFLY, CANCAN, CAPOEIRA, CLOG, COTILLON, COURANTE, FLAMENCO, FOXTROT, GRASS, JINGLEDRESS, JITTERBUG, MACABRE, MANIPURI, MORRIS, MUSETTE, POLKA, RAGSSHARQI, SALSA, SQUARE, STOP, SWING, SWORDS, TANGO, TAP, TARANTELLA, WALTZ, WAR.

### Skills: sample (thief, fighter, paladin, general)

| Key (line) | What the text says |
|---|---|
| `SKILL_ABANDONSHIP` (3) | In ship combat, gathers lifeboats, loads cargo and crew, and retreats; with no boats, throws crew overboard |
| `AD_LIB` (35) | Bard copies a skill the target uses that the bard does not know, for a short window |
| `ALERTNESS` (49) | Thief notices room details he or she would otherwise miss; more with expertise |
| `AMBUSH` (65) | Thief positions group members in hiding for an ambush |
| `ANKLELOCK` (90) | After a successful attack, locks the target's ankle; both are stuck in a struggle for a while |
| `BACK_STAB` (434) | Massive one-time attack on a target who cannot see the thief (text has a stray "Shorts : BS" fragment) |
| `BATTLE_CRY` (443) | Barbarian's cry improves the hit rate of group members until battle ends |
| `BERZERK` (517) | Temporary extra hit points, attack points and damage, for slightly less armour score |
| `ABOVE_THE_LAW` (24) | Some respected figures are instantly forgiven their crimes (flavour) |
| `PALADIN_DEFEND` (58) | Stop attacking for an all-out defence; if the paladin goes two full rounds unhit, the battle ends peacefully |
| `FIGHTER_WATERTACTICS` (6158) | Fighter gains an edge in combat in water and can hide by resting still in it |
| `SKILL_ARREST` (167) | Knock out a target and handcuff them to the local judge for trial |
| `THIEF_PAYOFF` (3806) | Pays an officer or judge to overlook the thief's warrants |
| `FIND_CLAN_SHIP` (2173) | Finds a trail to the clan's nearest sailing ship |
| `ROOFIE` (4482) | Thief taints a drinkable in the target's inventory with an intoxicant that takes effect on the next drink |
| `THIEF_WENCHING` (6164) | A paid, companionship-style "proposition" to a bar patron for a temporary buff (see content notes) |
| `SKILL_CHILDLABOR` (950) | Coerces child mobs into faster mundane crafting and gathering (see content notes) |

### Crafts, gathering and trade (common skills)

The 156 keys cover these groups. The grouping is mine:

- **Crafts:** alchemy and improved alchemy (`ALCHEMY` at `common_help.ini:5`, `IMPROVEDALCHEMY` at 23), smithing (`BLACKSMITHING` at 225 and others), cobbling, cooking, baking, distilling, dyeing, embroidery, engraving, fletching, jewel making, lacquering, leatherwork, pottery, sculpting, tailoring, tanning, weaving, welding, carpentry, glassblowing, paper making, scroll scribing, staff, wand and rod making, scrimshaw, masonry, locksmithing, tattooing and body piercing. `ALCHEMY` brews a potion from a spell or prayer the player has mastered, in a glass container with water.
- **Gathering:** foraging, fishing (`FISHING` at 808), hunting, mining, gem digging (`GEM_DIGGING` at 924; `MASTER_GEM_DIGGING` at 1533 is "over twice as slow" and yields more), wood chopping, trawling, herbalism, mycology, shearing, shrooming (`SHROOMING` at 2166, which plants spores indoors or at night), surveying.
- **Animals and farming:** taming, training, bonding, husbandry, trapping, mount taming, domesticating, herding, shepherding, branding, butchering, farming, gardening, irrigation, composting, floristry.
- **Trade and services:** marketeering, black marketeering, snake-oil selling, apothecary, publishing, lecturing, enrolling, studying, transcribing, cataloguing, organizing, appraisal-type skills. Several are adult-themed or slave-related (see content notes).

### Traps and bombs

`traps.ini` has 79 primary entries: 65 `TRAP_*` and 14 `BOMB_*`. Most traps are one of a small number of generated families (the class names follow the same pattern, for example `Abilities/Traps/Trap_EnterGas.java`).

| Family | Trigger | Variants | Count |
|---|---|---|---|
| Enter, Get, Open, Unlock | Entering a room or exit; picking up an item; opening a container or door; unlocking one | base, Blade, Gas, Needle, Spell (`TRAP_ENTERGAS` at 120, `TRAP_GETNEEDLE` at 258, `TRAP_OPENSPELL` at 365, `TRAP_UNLOCKSPELL` at 627) | 20 |
| Pit on enter or exit | Entering or leaving a room | `TRAP_ENTERPIT` (136), `TRAP_EXITPIT`, `TRAP_EXITROOM` (169) | 3 |
| Small pits | Moving through a room | Acid, Deep, Rat, Room, Snake, Spike (`TRAP_ACIDTHRUPIT` at 459, `TRAP_RATTHRUPIT` at 485) | 6 |
| Environment | Room type or weather | Avalanche on mountains (`TRAP_AVALANCHE` at 36), cave-in (70), flood and crushing rooms (235), darkfall, boulders on rocky rooms, bear trap, snare, tripline (596), sleep gas (451) | about 15 |
| Items | Picking up or handling | Glue, grease, ignition, infection, popper (`TRAP_POPPER`), vanishing, boomerang, needle prick, spark (537), cursed-style hooks | about 10 |
| Containers and doors | Opening | Electric shock, launcher, noise (`TRAP_NOISE` at 334), poison gas, needle, spore trap (580), weapon swing | about 10 |

Bombs (`BOMB_*`) are placed or thrown items whose "Targets" line names the ingredient the help expects. The "Targets" line is ambiguous: it names the ingredient for most bombs and a target for `BOMB_SPELLBLAST`. The entries are:

| Key (line) | Name | Targets line | Effect (paraphrase) |
|---|---|---|---|
| `BOMB_ACIDBURST` (12) | Acid Burst | Lemons | Acid blast |
| `BOMB_EXPLOSIVE` (179) | Explosive Bomb | Coal | Fire blast |
| `BOMB_SONIC` (187) | Sonic Bomb | Reed | Annoying sonic blast, weak damage, kills fish |
| `BOMB_HEAT` (196) | Heat Bomb | Wood | Long blast of heat that cooks corpses |
| `BOMB_SHAPED` (205) | Shaped Bomb | Iron | Large blast at one stationary target, placed with PUT |
| `BOMB_STICKY` (215) | Sticky Bomb | Cotton | Sparks; sticks to doors and containers placed with PUT |
| `BOMB_FLAMEBURST` (227) | Flame Burst | Lamp Oil | Fire blast |
| `BOMB_PEPPER` (383) | Pepper Bomb | Some Peppers | Irritation that lowers attack and defence |
| `BOMB_POISON` (409) | Poison Gas Bomb | Poison | Poisons everyone present |
| `BOMB_SMOKE` (512) | Smoke Bomb | Something Wooden | Blindness |
| `BOMB_SPELLBLAST` (547) | Spell Blast | Doors and Containers | A spell strike |
| `BOMB_SPORES` (572) | Spore Bomb | Diseased Meat | Causes disease |
| `BOMB_NOXIOUS` (588) | Stink Bomb | An Egg | Stinking cloud |
| `BOMB_WATER` (653) | Water Bomb | Water container | Puts out burning things |

### Poisons

The 25 entries (`poisons.ini:1-201`) follow one template: `Ability :`, `Parameters :`, `Example :`, `Description :`. Groups by effect:

- **Stat drains:** Bee Sting (19), Bloodboil (27), Decrepifier (36), Goblin Drip (69), MindSap (126), Venom (186), Rotten (157), Sting (179), Heartstopper (93).
- **Intoxicants and sedatives:** Alcohol (1), Inebriation (109), Liquor (117), Hallucinogen (85), Clarity (77, "mildly addictive"; raises intelligence and mind saves), Caffeine (150), Slumberall (171).
- **Other conditions:** Anesthesia (10, hides damage numbers and screams), Firebreather (44, random fire breath), Ghoul Touch (53, paralysis), Hives (101, allergic reaction), Peppersauce (133, blinds), Sickening (164, vomiting), XXX (194), Glowgell (61, harmless; items glow).
- **Base:** `POISON` itself is the ability and standard poison.

### Diseases

The 69 entries (`diseases.ini:1-655`) follow the template `Disease :`, `Parameters :`, `Example :`, `Description :`. Sample, paraphrased:

| Key (line) | Effect |
|---|---|
| `DISEASE_ANEMIA` (11) | Weaker and slower; not contagious (displays as "Amemia", see Problems) |
| `DISEASE_ALZHEIMERS` (20) | Lowers INT and WIS, makes the victim forget skills and wander; age-related |
| `DISEASE_ANTHRAX` (48) | Constitution drains until death; from sheep |
| `DISEASE_ASTHMA` (75) | Movement cut by 75% for the rest of the game unless cured |
| `DISEASE_BLINDNESS` (94) | Cannot see |
| `DISEASE_CANCER` (102) | CON, mana, movement and HP drain until death |
| `DISEASE_CANNIBALISM` (111) | Hunger only for the flesh of the victim's own race |
| `DISEASE_DEPRESSION` (222) | Lower attack and justice saves; periodic hunger and fatigue |
| `DISEASE_GIGGLES` (183) | Harmless and contagious |
| `DISEASE_HATRED` (331) | Cannot trade, group or speak politely with the target's group |
| `DISEASE_LYCANTHROPY` (388) | Becomes a werewolf at full moon; spread by scratches |
| `DISEASE_LYME` (397) | Headaches make the victim forget skills and spells |
| `DISEASE_MALARIA` (416) | Lowers several stats; periodic damage |
| `DISEASE_MUMMYROT` (436) | Constitution drains until death; from mummies |
| `DISEASE_NARCOLEPSY` (453) | Falls asleep suddenly |
| `DISEASE_PLAGUE` (589) | Lowers DEX and CON; periodic damage |
| `DISEASE_SEASICKNESS` (201) | Queasy; reduces mana and movement |
| `DISEASE_SLEEPWALKING` (551) | Walks in its sleep; no other effect |
| `DISEASE_TOURETTES` (579) | Spontaneous outbursts (an ability-template entry) |
| `DISEASE_VAMPIRISM` (608) | Light sensitivity, -1 CHA, cannot swim |
| `DISEASE_WRITERSBLOCK` (630) | Cannot write for a while |
| `DISEASE_ZIKA` (648) | Loses maximum INT; not contagious |

Several entries are sexually transmitted conditions (`DISEASE_AIDS` at `diseases.ini:2`, chlamydia at 128, scabies at 192, gonorrhea at 320, syphilis at 560).

### Races and classes

- **Races (35, `misc_help.ini`, `<RACE>` entries):** Aarakocran (birdfolk), Beastkin, Centaur, Dwarf (1302), Drow (1386), Duergar, Elf, Faerie, Gitthyanki-style planar races (Githyanki), Giant, Gnoll, Gnome, Ghast, Ghost, Ghoul (1435), Giant, Goblin, Half-elf, Halfling, Human (1415), Lich (1275), Lizard man, Merfolk, Mindflayer, Mummy, Mustie (1318), Naga, Ogre, Orc, Pixie, Spectre, Svirfneblin, Tabaxi, Tiefling, Vampire, Zombie. Undead races make up about eight of the entries (Lich, Ghast, Ghoul, Ghost, Mummy, Spectre, Vampire, Zombie).
- **Classes (50, `misc_help.ini`, `<CHARCLASS>` entries):** base classes (Fighter, Thief, Druid, Mage, Cleric, Bard, Paladin, Ranger, Barbarian, Monk, Assassin, Burglar, Trapper and others), and specialists (Abjurer, Alterer, Conjurer, Diviner, Enchanter, Evoker, Illusionist, Transmuter for mages; Gaian, Skywatcher, Mer, Delver, Beastmaster for druids; Necromancer, Purist, Missionary, Healer, Oracle, Shaman, Templar for clerics). Some class text is strongly flavoured ("Like Tarzan or Grizzly Adams, the BeastMaster is the lord of his particular jungle", `misc_help.ini:1245` range).
- Each race or class template is generated from the object, so the numbers shown (stat bonuses, limits) follow the code.

### Other text worth reusing

- The `HELP` index (`help.txt`) groups commands by activity; that grouping is a good layout for a Python SUD's `help` command.
- `CONFIG_HELP_OPTIONS` (`misc_help.ini:59`) holds 31 option descriptions (`BRIEF`, `AUTOLOOT`, `PAGEBREAK` and so on) in one value. Short and clear, for example "Don't see room descriptions when moving from room to room." (`BRIEF`, line 76).
- The `Usage` and `Example` lines use real player syntax with quotes for multi-word names (`CAST "ACID ARROW" orc`). This is worth copying for a text parser.

### Content notes

The following exists in the data. Each is described neutrally, and no sexual content involving minors was found.

- **Sexual transmission:** several diseases are described as sexually transmitted (`diseases.ini:2, 128, 192, 320, 560`).
- **Reproduction and family:** a mating social, pregnancy and a birth timeline appear in `misc_help.ini:1858-1877` (REMORTING). The help gives about 10 mud-months to birth and 9 more to walking, with player status 18-27 mud-months later; these figures were not checked against code. Chants sense or affect pregnancy and offspring (`chant_help.ini:1732`, `1828`, `1837`, `1903`, `1911`). Adoption (`prayer_help.ini:23`) and christening (`prayer_help.ini:452`) cover child mobs.
- **Child labour:** `SKILL_CHILDLABOR` (`skill_help.ini:950`) coerces child mobs into faster crafting and gathering, on private property.
- **Commerce in captives and paid companionship:** `THIEF_WENCHING` (`skill_help.ini:6164`), `PIMPING` (`common_help.ini:1801`), `SLAVEMARKETEERING` (2075) and `SLAVE_TRADING` (2091) describe renting or selling captive mobs and a paid companionship-style skill. These are multiplayer trade systems. The slave examples do not include any sexual content.
- **Drugs and intoxicants:** `DRUGCUTTING` (`common_help.ini:688`), `ROOFIE` (`skill_help.ini:4482`), `ADDICTING_CURSE` (`prayer_help.ini:17`), and poisons such as Alcohol, Liquor and Clarity.
- **Torture:** `SPELL_TORTURE` (`spell_help.ini:3126`), `TORTURESMITHING` (`common_help.ini:2339`, implements of execution and torture).

## Notable design ideas

- **Help generated from the rules.** The `<ABILITY>` header reads class availability, prerequisites, costs, targets and range from the live object (`MUDHelp.java:633-830`), so a balance change shows up in the help without an edit. The prose still goes stale, which is the main weakness (see below).
- **Synonyms as one-line aliases.** Typos and variants (`SPELL_ACHILLESARMOR`, `CHANT_LAND_LUNGS`, `CHANT_BREATHEAIR`) are one-line aliases, and the lookup adds family prefixes so the player types the short name.
- **Proficiency in words.** The 14 bands (Beginner to Mastery, `lists.ini:812-826`) turn a number into a readable skill level, and the code switches between words and percentages with one setting.
- **Trap and bomb families.** Traps are generated from two axes, the trigger (enter, get, open, unlock) and the payload (blade, gas, needle, spell), so one template covers 20 entries. Bombs name their ingredient in the "Targets" line.
- **Conditional and macro help.** `@if?CheckFactionLoaded?...@` makes text depend on which subsystem is loaded (`misc_help.ini:1268`).
- **Zapper help from the parser.** `<ZAP=...>` pulls the mask language's own help (`MUDZapper.java:510`), so the syntax text cannot disagree with the parser.
- **Diseases as long, ticking conditions.** `DISEASE_TICKS` of 99999 with a `DISEASE_DELAY` (`Disease_Asthma.java:92, 98`) gives "until cured" conditions with a single pattern.
- **Last-stand protection by veto.** The Abiding Aura returns false from `okMessage` on a death message, sets hit points to 1, and schedules a check (`Paladin_AbidingAura.java:67-111`). The same pattern could give a single-player "last stand" effect.
- **Self-sorting data.** `RESET SORTHELP` regroups the aliases and entries and reports unused or missing blocks (`Reset.java:674-860`).

## Problems and cruft

### Loading and duplicate keys

- **Eight keys are defined in two player files,** and the winner depends on directory order (`core/CMFile.java:1701`). Examples: `KNOCK` is the command at `help.ini:1659` and the spell alias at `spell_help.ini:1736`; `DISPLAY` is the command `SHOW` (`help.ini:1493`) and a config text (`misc_help.ini:1910`); `COLOR` is `ANSI` (`help.ini:194`) and `COLORS` (`misc_help.ini:2122`); `INFECTED` is a trap (`traps.ini:308`) and a disease (`diseases.ini:348`); `POWERS` and `PK` are similar cases; `CUFF` and `HANDCUFF` point at the same skill from two files.
- **Within-file overwrites.** `COMMON` is a language entry (`misc_help.ini:1755`) and then an alias to `COMMON_SKILLS` (1814), so the language text is never shown. `SKILL_FINDCLANHOME` is an alias to `FIND_CLAN_HOME` (`skill_help.ini:2161`) and then to `FIND_CLAN_SHIP` (2171), so the Home skill's key shows the Ship text. `POISON_GAS` maps to the trap (`traps.ini:399`) and then to the bomb (408); `SPELL_BOMB` maps to the Spell Blast bomb (545) and then to the Spell Bomb trap (554). The last line wins in each case, silently.
- **Harmless duplicates:** `CHIRGURY` (`skill_help.ini:956, 958`, identical), `AUTO_SWIM` (285 and 332), `CHANT_BREATHEAIR` (1154 is a self-alias, the real entry is at 1157), `HONORARY_DEGREE_DRUID` (three times), `GRAFFITI`, `GRAZING`, `SPECIALIZATION_NATURAL`, `THIEF_PAYOFF`, `SONG_DRUNKENNESS`.
- **Dead self-alias.** `CHANT_DELAYPOISON=CHANT_DELAYPOISON` (`chant_help.ini:473`) is the only definition of that key. The redirect loop sees that the text names itself and returns no result (`MUDHelp.java:1641-1647`), so `HELP DELAYPOISON` shows nothing. The real entry is `CHANT_DELAY_POISON` (474). Inferred from reading the loop, not run.
- **No cycle guard.** The redirect loop (`MUDHelp.java:1636-1652`) follows any short value that names a key. Two aliases that name each other would loop forever. A script found no cycles in the current data (2,805 alias edges), so this is a latent risk for editors.

### Parsing and encoding

- **`!` alias never loads.** `help.ini:1` is `!=EXCLAMATION_POINT`, a line `Properties` treats as a comment. The code works around it (`MUDHelp.java:1015-1016`), so the data line is dead.
- **Non-ASCII in a Latin-1 load.** `misc_help.ini:1320` contains a UTF-8 right single quote (bytes E2 80 99, as in "They're"). The file is read as ISO-8859-1 (`MUDHelp.java:1777`), so players see three characters in its place. This is an active display bug.
- **Replacement character in data.** `skill_help.ini:6305` (WRISTLOCK) contains U+FFFD in "enemy's", stored as bytes EF BF BD. It shows as a box or question mark.
- **`Short(s):` field.** It appears 256 times in `help.ini`, 211 of them empty. No Java source refers to `Short(s)` (checked with a fixed-string search), so the field does nothing.
- **Nested Properties.** `CONFIG_HELP_OPTIONS` is a `Properties` text stored as a value and parsed again in `Commands/Config.java:92-100`, using `getBytes()` with the platform default charset. A double parse for 31 short strings.
- **Backtick apostrophes.** 139 apostrophes are typed as backticks and shown that way.

### Typos and malformed text (visible to players)

- `diseases.ini:12` shows "Amemia", but the class localizes the name "Anemia" (`Abilities/Diseases/Disease_Anemia.java:43`).
- `diseases.ini:368-370` (`DISEASE_LEPRESY`, class `Disease_Lepresy`) and `diseases.ini:568-570` (`DISEASE_TETNUS`, class `Disease_Tetnus`) keep misspelt IDs. The correct-spelling aliases (LEPROSY, TETANUS) work.
- "infectuous" (`diseases.ini:188, 644`), "constutition" (497), "throughout" typed as "througout" (`traps.ini:339, 438`), and a garbled sentence in the scabies text (`diseases.ini:197`).
- Trap display names are class IDs: `Trap : Trap_EnterBlade` (`traps.ini:113`) and `Trap : Trap_ExiRoom` (170), a typo of the class `Trap_ExitRoom`.
- Stray characters in usage lines: `spell_help.ini:1181` (`CAST FLAGPORTATION ([CLAN NAME]")`), `common_help.ini:809` and `1454` (`[TARGET])` with an extra parenthesis).
- `BACK_STAB` (`skill_help.ini:434`) starts its description with "Shorts : BS".
- The 'Parameters' line of `TRAP_TRAP` (`traps.ini:1-5`) documents trap types 0-3, but the parameters of most traps are blank.

### Quoted numbers that the code does not match

See the table in How it works, section 8. The cases are Airyform (half, code 0.75), Divine Resistance (three, code four), Heartstopper (100 HP total, code about 88 at default rank), and Asthma (movement "rate", code movement value). The lesson is that numbers typed into prose drift.

### Coverage gaps (heuristic)

A script matched each ability class in the Spells, Prayers, Songs, Common, Skills, Thief, Fighter, Paladin, Druid, Traps, Poisons and Diseases directories against help keys by ID and by display name. Of 2,170 classes, 30 have no matching key in any player file. Most are abstract bases (`Spell`, `Prayer`, `ThiefSkill`, `FighterSkill`, `CraftingSkill`, `BardSkill`, `PaladinSkill`), but these look like real gaps (not run in-game): `Spell_MysticTask` ("Mystic Task"), `Spell_DarkSensitivity` ("Dark Sensitivity"), `Prayer_DivineQuest` ("Divine Quest"), `Skill_RustingStrike` ("Rusting Strike"), `Poison_Hazia` ("Hazia"), and common skills such as Gunsmithing, Flower Arranging, Self Investing and Master Painting Skill. `Skill_AllBreathing` and `Skill_Resistance` have help only in `arc_misc.ini` (lines 726 and 748), so players never see it.

### Lookup and search

- `getHelpList()` (`MUDHelp.java:1686`) reads each archon key's text from the player file (`rHelpFile1`). Archon entries are therefore searched against the wrong text. Harmless for names, wrong for full-text search.
- The "Level" order of the prefix probing means a plain word can match an unexpected family. `help chant` would match only exact keys, but `help breathe` follows the probing into any family that has the word. Unverified in play.
- The 31-character redirect limit is silent: a longer alias value would be shown as text rather than followed (`MUDHelp.java:1636`).

### Multiplayer assumptions

Most group effects (Battle Cry, Airy Aura, Ambush, group Dexterity in Ballet), social and clan references, channels, slave and clan trade, and the child-birth economy assume other people. They need rewriting for a single-player game.

## Takeaways for a Python SUD

- **Store help as structured data, not as prose with numbers.** One record per ability with the fields the game already uses: name, kind (spell, prayer, chant, song, skill, craft, trap, poison, disease), usage pattern, example, body text, aliases, and the numeric fields. Render the header from the record in Python, as `fixHelp` does. Refer to numbers through the same constants the rules use, so prose cannot drift.
- **Validate the help file on load and fail loudly.** Reject duplicate keys across the data, aliases that name missing keys, self-aliases, alias cycles, U+FFFD and mojibake, and non-UTF-8 input. CoffeeMud fails silently on all of these.
- **Use one dictionary with an alias table.** Normalise input (lower-case, strip, convert spaces to underscores, or split with `shlex` so quoted multi-word names work), try the exact key, then the family prefixes that the game has (`spell_`, `prayer_`, `chant_`, `song_`, `dance_`, `skill_`), then a close-match search (`difflib.get_close_matches`), and finally a substring search. Drop the socials, clans, channels, deities and archon layers.
- **Borrow the proficiency bands.** A 14-word table with thresholds (5, 10, 20, 30, 40, 50, 60, 70, 75, 80, 85, 90, 95, 100) gives readable skill levels. Store it once and use it everywhere a skill level is displayed.
- **Generate traps from two axes.** For example:

```python
TRIGGERS = ("enter", "get", "open", "unlock")
PAYLOADS = ("blade", "gas", "needle", "spell")
TRAPS = {f"{t}_{p}": Trap(trigger=t, payload=p) for t in TRIGGERS for p in PAYLOADS}
```

  Environment and item traps stay as separate data records, and bombs keep their ingredient lists as data.
- **Model poisons and diseases as tick-based conditions** with a duration in ticks, a delay between effects, a per-tick effect, and stat changes (the `POISON_TICKS`, `POISON_DELAY`, `POISON_DAMAGE` pattern in `Poison.java`). A "until cured" disease is a very large duration. Decide the clock first: the code uses 4-second ticks plus wall-clock 10-second timers (`Paladin_AbidingAura.java:105`), which would need a different design in a turn-based game.
- **Keep the good prose, drop the multiplayer parts.** Keep the Usage/Example format and the short descriptions. Rewrite group effects as companion or pet effects. Drop the channel, clan, archon, social and trade-with-other-players entries.
- **Be deliberate about the adult and sensitive material.** Disease transmission, drugs, captive trade, torture and child labour are in the data and are design choices, not defaults. The race list (35, about eight undead) and the class list (50) can be trimmed to a smaller roster.
- **Build a check for the help text against the rules.** A test that renders each entry and checks its numbers against the game constants would have caught Airyform, Divine Resistance and Heartstopper.

## Coverage

- **Read in part with Read or sed:** the start and structure of `skill_help.ini`, `spell_help.ini`, `prayer_help.ini`, `chant_help.ini`, `common_help.ini`, `songs_help.ini`, `misc_help.ini` (including `CONFIG_HELP_OPTIONS`, `INCLINATION`, the `RACES` macro, and REMORTING), `help.ini` (start, `COLOR`, `ZAP`, `CALENDAR`), and sampled regions of each.
- **Parsed in full by script (every entry in all 14 files):** counts, aliases, duplicates, tag census, non-ASCII and U+FFFD check, alias cycles and prefix counts.
- **Listed in full through the parser:** `traps.ini` (79 entries), `poisons.ini` (25), `diseases.ini` (69), race and class entries (85, with first sentences), `help.txt`, `accts.txt`, `acctmenu.txt`.
- **Sampled, not read line by line:** about 45 ability entries across the families, and the names of all 156 common skills, 114 songs and dances, and the 266 command blocks in `help.ini` (the last summarised by the `help.txt` index).
- **Code read in full or in the relevant ranges:** `MUDHelp.java` (loader, lookup chain, `fixHelp`, `addModifyHelpEntry`, the `getHelpList` and see-also functions), `Help.java`, `Reset.java` (`helpSets` and `SORTHELP`), `Config.java` (the help branch), `Unload.java` (help branch), and the mechanics listed in How it works, section 7.
- **Not traced:** the web-macro engine (`webMacroFilter`), the journal editor used by `addModifyHelpEntry`, `ableMapper` internals, the zapper parser beyond `maskHelp`, and whether the Poison and Disease tick counts match the in-game message counts.
