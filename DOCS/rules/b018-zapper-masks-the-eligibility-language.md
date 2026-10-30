# Zapper masks: the eligibility language

> Part of the CoffeeMud NEWOVERVIEW. Bucket: `b018` (Zapper masks: the eligibility language) · 2 files · 10892 lines of Java.

## Summary

A zapper mask is CoffeeMud's one-string eligibility language. A mask such as `-CLASS +thief +mage` or `+LEVEL -<5 ->20` is compiled once, cached, and checked against a mob, item, room, area or offline player. Properties, behaviors, commands, channels, journals, clans and quests all gate on masks, which is why builders type them everywhere. Each keyword carries one sign: `+KEY` lists what fails, `-KEY` lists what is required, and the values after it take the opposite sign. Bare class, race, alignment and gender names are generated from the live content tables, and the same string also yields an English description.

The language is large (200 keyword constants, 305 spellings, of which five are internal level spellings). Most errors are logged and dropped rather than reported to the builder, and the description generator and the offline-player evaluator disagree with the main evaluator in several places (see Problems).

## Key files and types

Paths are relative to `com/planet_ink/coffee_mud/` (so `Libraries/MUDZapper.java` is `com/planet_ink/coffee_mud/Libraries/MUDZapper.java`), except paths that begin with `resources/`, which are relative to the CoffeeMUD folder.

| File or method | Role | Lines |
|---|---|---|
| `Libraries/interfaces/MaskingLibrary.java` | Public API; `ZapperKey` keyword enum (341-561); `CompiledZMask` and `CompiledZMaskEntry` (563-648) | 648 |
| `Libraries/MUDZapper.java` | The whole implementation (`MUDZapper extends StdLibrary`) | 10,244 |
| `MUDZapper.getMaskCodes` | Keyword map built from `ZapperKey.keys()` | 488-507 |
| `MUDZapper.buildSavedClasses`, `getLooseCodes` | Bare "loose" codes from class, race, alignment and gender tables | 323-430 |
| `MUDZapper.getPreCompiledMask` | Compile cache | 462-480 |
| `MUDZapper.maskCompile` | Text to entries | 4158-5653 |
| `MUDZapper.maskCheck`, `maskCheckSubEntries` | Evaluation for `Environmental` targets | 5677-5738; 6045-8918 |
| `MUDZapper.maskCheck` (ThinPlayer overloads) | Evaluation for offline player records (subset) | 9255-9911 |
| `MUDZapper.maskDesc` | English description, built by a second switch | 1198-3641 |
| `MUDZapper.parseMaskKeys`, `syntaxCheck` | Key listing; shallow syntax check | 1064-1196; 3702-3723 |
| `MUDZapper.makeSkillFlagObject`, `evaluateSkillFlagObject` | Skill-flag expressions | 538-652; 712-757 |
| `MUDZapper.dateMasksToNextTimeClock` | When a date mask next changes | 9176-9253 |
| `MUDZapper.parseSpecialItemMask` | Item shorthand such as `5-10`, `RING`, `WEAPON` | 10017-10243 |
| `MUDZapper.getRequiredRaces`, `getAbilityEduReqs`, `minMaskLevel` | Queries other systems use to read a mask | 3769-4156 |
| `resources/help/zappermasks.txt` | Builder help text (7,359 bytes) | n/a |

Callers, from grep: 756 `CMLib.masking()` call sites in 165 files (accessor at `core/CMLib.java:500`). Examples: `Abilities/Properties/Prop_HaveZapper.java:304,315`, `Prop_WearZapper.java:84-98`, `Prop_RideZapper.java`, `Commands/Channel.java:152`, `Commands/CommandJournal.java:347`, `Commands/ClanApply.java:84-97`, `Behaviors/StockMarket.java` (`getRequiredRaces`), `Libraries/ColumbiaUniv.java` (`minMaskLevel`, `getAbilityEduReqs`), `Abilities/Thief/Thief_Runecasting.java` (date masks), `MOBS/StdCraftBroker.java` (`parseSpecialItemMask`).

## How it works

### 1. Text, tokens and caching

- A mask is a plain string. `CMParms.parse` (`core/CMParms.java:788`) splits it on whitespace, groups double-quoted text (`"+my name"`), and honors backslash escapes.
- Compilation upper-cases the whole string first (`MUDZapper.java:4167`), so keywords and most values match case-insensitively. Two exceptions: `CLASSTYPE` arguments are read from the original text (4200-4201), and `-IF` expression tokens are upper-cased because they come from the same token list (4167, 5545-5560).
- `getPreCompiledMask` keys a cache by the lower-cased, trimmed string. The cache is a `PrioritizingLimitedMap(200, 10 minutes)` held in `Resources` (462-480). `buildSavedClasses` clears the compiled and loose-code caches when `CMClass.getLastClassUpdatedTime()` changes (325-328, 418).

### 2. The sign convention

- `+KEY` is the deny side. The mask fails when the target matches a listed value, and those values carry a minus sign. `+RACE -elf -dwarf` means "anyone except elves and dwarves".
- `-KEY` is the require side. The mask fails unless the target matches a listed value, and those values carry a plus sign. `-CLASS +thief +mage` means "a thief or a mage".
- The pairing is enforced by lookup tables. `_CLASS` (`-CLASS`) reads the `+name` table (`compiledCache.put(ZapperKey._CLASS, pclassList)`, 350), and `CLASS` (`+CLASS`) reads the `-name` table (349). A value with the wrong sign is not found, which ends the list (see Problems).
- Several families ignore the value sign at compile time, so only the keyword sign decides the result: date keys (5294), `EFFECT` (4561), `SKILL` (4973), `SKILLFLAG` (5009), `ITEM` and `WORN` (5036), `MATERIAL` (5057), `WORNON` (5082), `DISPOSITION` (5106), `SENSES` (5130), `SEASON` (5157), `WEATHER` (5187), `DOMAIN` (5214), `PORT` (5245), `RESOURCE` (5445).
- Each constant has its name form (`+NAME` or `-NAME`, from the constructor at `MaskingLibrary.java:545-555`) plus listed aliases. Plurals are aliases (`-NAMES`, `+LEVELS`). One alias has no sign: `CLASSES` (`MaskingLibrary.java:349`).

### 3. Keyword reference

For list families, the values after `+KEY` take `-`, and the values after `-KEY` take `+`. "Lines" gives the check (or compile) location in `MUDZapper.java` unless it says otherwise.

#### 3.1 Flags and actor kinds (no value list)

| Keyword spellings | `+` form | `-` form | Lines |
|---|---|---|---|
| `PLAYER`, `PLAYERS` | `+PLAYER` accepts the whole group at once for player characters | `-PLAYER` fails for player characters | 6952-6963 |
| `NPC`, `MOB`, `MOBS` | `+NPC` accepts the whole group for monsters | `-NPC` fails for monsters | 6956-6967 |
| `SYSOP`, `SYSOPS` | accepts the group for admins | fails for admins | 6059-6065 |
| `SUBOP` | accepts the group for admins or area sub-ops | fails for them | 6067-6076 |
| `OFFICER`, `JUDGE` | fails for players, and for monsters when the room has a legal behavior that does not count them as an officer or judge | fails for monsters the room's legal behavior counts as an officer or judge | 6546-6587 |
| `ISHOME` | fails unless the mob is in its home or beacon area | fails if it is | 8563-8578 |
| `AREAINSTANCE` | fails unless the room's area is an instance child | fails if it is | 8460-8475 |
| `CHANCE` (minus form only) | none | `-CHANCE N` fails when a 1-100 roll is below N | 8251-8255 |

#### 3.2 List families (values follow the keyword)

| Keyword spellings | Values | What is compared | Lines |
|---|---|---|---|
| `CLASS`, `CLASSES` / `CLASS` | class name or ID | current class (`actual` picks base or display name) | 6077-6086 |
| `BASECLASS` | base class names | base class of the current class | 6087-6114 |
| `ANYCLASS` | class names or IDs | mob holds a level in that class (multiclass) | 8093-8141 |
| `CLASSTYPE` | object-type codes, resolved by `CMClass.findTypeAncestor` (`CMClass.java:1656-1662`) | the object's Java type | 4195-4287; 6115-6140 |
| `RACE` | race names or IDs | race (base or current) | 6141-6143; 6982-6990 |
| `RACECAT` | racial category names | racial category | 6968-7003 |
| `ALIGNMENT` | `Faction.Align` names: INDIFF, EVIL, NEUTRAL, GOOD, LAWFUL, CHAOTIC, MODERATE | alignment name or inclination name | 6145-6149; 8793-8797 |
| `GENDER` | MALE, FEMALE, NEUTER, or first letter | gender (base or current) | 6150-6163; 8798-8811 |
| `NAME` | exact name | target name (base or display) | 6836-6851; 8070-8079 |
| `SUBNAME` | `*tail`, `head*`, `*middle*`, or plain (contains) | partial name, case-insensitive | 6871-6951 |
| `ACCOUNT` | account name, falling back to the target name | player's account | 6852-6870; 8080-8092 |
| `TATTOO` | tattoo names; area blurb names | tattoo on the mob, or an area blurb | 6398-6423 |
| `TAG` | tag IDs | `mob.hasTag` | 6424-6447 |
| `ACCCHIEVE` | achievement tattoo IDs | tattoo on the player's account, or an area blurb | 6610-6645 |
| `MOOD` | mood names (default NORMAL) | text of the mob's Mood effect | 6588-6609 |
| `SECURITY`, `SECURITIES`, `SEC` | `CMSecurity` flag names | `CMSecurity.isAllowed(mob, room, flag)` (flag enum at `CMSecurity.java:2341`) | 6812-6835 |
| `DEITY` | deity IDs, or ANY | worshipped character ID | 7964-7993 |
| `QUESTWIN` | quest IDs or names | mob won the quest (`wasWinner`) | 6670-6695 |
| `CLAN` | clan ID, `*`, or `ID(ROLE)` | membership, optionally a role or function | 7004-7165 |
| `CLANLEVEL` | clan name, `1` (first clan), or `*`, then level operators | level in that clan | 6249-6332 |
| `EXPERTISE` | expertise IDs | `fetchExpertise` | 6646-6669 |
| `SKILL` | ability ID, optional `(proficiency)` | ability known at that proficiency or more | 6696-6725; 6765-6785 |
| `SKILLFLAG` | expression (section 8) | some skill matching the expression | 6726-6764; 6786-6811 |
| `EFFECT` | effect or behavior ID or name | `fetchEffect` or `fetchBehavior` | 7995-8069 |
| `FACTION` | faction range codes | `isFactionedThisWay` | 8024-8049 |
| `AREA` | area names, `*tail` | room's area name | 8344-8395 |
| `PARENTAREA` | area names | the area or an ancestor (`isParentRecurse`) | 8396-8459 |
| `AREABLURB` | blurb flag name, optional value | area has that blurb flag (with that value) | 8476-8562 |
| `HOME` | start area name or plane name, `*tail` | start (home) area | 8615-8665 |
| `PLANE` | plane names (default Prime Material) | plane of existence | 8579-8614 |
| `LOCATION` | OWNED, CLANOWNED, PRIV, ROOMID(x), ROOMSTR(x) | room checks (`checkLocation`, 654-710) | 6500-6525; compile 4812-4887 |
| `PORT` | port numbers | host port or public port | 8254-8281 |
| `RIDE`, `FOLLOW` | nested mask (all later entries in the group) | applied to the mount, or to the leader | 7932-7963 |
| `ITEM` | item names | mob carries an item with that name | 8727-8750 |
| `WORN` | item names | mob wears that item (item targets: see Problems) | 8751-8792 |
| `WORNON` | wear-location names (`Wearable.CODES`) | location bit on the item | 7174-7201 |
| `MATERIAL` | material names | item material | 7166-7173 |
| `RESOURCE` | resource names; resource-flag groups | item material is one of these | 7860-7867 |
| `DISPOSITION` | Disposition enum names (ISHIDDEN, ISSLEEPING, ...) | disposition bits | 7202-7227 |
| `SENSES` | Senses enum names | sense bits | 7228-7253 |
| `WEAPONTYPE` | damage types (`Weapon.TYPE_DESCS`) | wielded weapon's damage type | 6448-6469 |
| `WEAPONCLASS` | weapon classes (`Weapon.CLASS_DESCS`) | wielded weapon's class | 6526-6545 |
| `WEAPONAMMO` | ammunition types | wielded weapon's ammo type ("" for no ammo) | 6470-6499 |
| `JAVACLASS` | exact ID, or `.Suffix` matched against Java class or interface names | target's class | 7868-7931 |
| `DOMAIN` | room domain names or numbers | `room.domainType()` | 7544-7573 |
| `WEATHER` | weather names or numbers | area weather type | 7514-7543 |
| `SEASON`, `SEASONS` | SPRING, SUMMER, FALL, WINTER, or numbers | area season | 7482-7513 |

#### 3.3 Numeric families (exactly one value token follows)

Compile reads the next token as a plain integer (5493-5540). The sign of the keyword decides the direction. The direction is not the same for every family.

| Family | Keywords | `+` fails when | `-` fails when | Lines |
|---|---|---|---|---|
| Base stats | `STR`/`STRENGTH`, `INT`/`INTELLIGENCE`, `WIS`/`WISDOM`, `DEX`/`DEXTERITY`, `CON`/`CONSTITUTION`, `CHA`/`CHARISMA` | base value below N | base value above N | 8190-8245 |
| Current stats | `ADJSTR`/`ADJSTRENGTH`, `ADJINT`, `ADJWIS`, `ADJDEX`, `ADJCON`, `ADJCHA` (long forms too) | current value below N | current value above N | 8142-8189 |
| Group size | `GROUPSIZE` | fewer than N in the group (an area: population) | more than N | 8842-8867 |
| Ability | `ABILITY`, `ABLE`, `ABLES` | magic ability above N | magic ability below N | 8256-8267 |
| Weight | `WEIGHT` | above N | below N | 8268-8279 |
| Armor | `ARMOR` | above N | below N | 8280-8291 |
| Damage | `DAMAGE` | above N | below N | 8292-8303 |
| Attack | `ATTACK` | attack bonus above N | below N | 8304-8315 |
| Value | `VALUE` | above N (mobs: total currency) | below N | 8316-8343 |
| Chance | `CHANCE` (minus only) | (no plus form) | fails when a 1-100 roll is below N | 8251-8255 |

So `+` means "at least" for stats and group size, and "at most" for ability, weight, armor, damage, attack and value. The help file agrees with the code, for example "+STR X (<WORD> those with strength less than X)" and "+ABILITY X (<WORD> ... greater than X)" (`resources/help/zappermasks.txt`).

#### 3.4 Level families

| Keyword | Form | Notes |
|---|---|---|
| `LEVEL`, `LEVELS` / `-LEVEL`, `-LEVELS` | `+LEVEL` takes operators with a minus sign; `-LEVEL` takes them with a plus sign | Target's level (base if `actual`) |
| `CLASSLEVEL` / `-CLASSLEVEL` | same shape | Level in the current class |
| `MAXCLASSLEVEL` / `-MAXCLASSLEVEL` | same shape | Highest level across the mob's classes |
| `ANYCLASSLEVEL` / `-ANYCLASSLEVEL` | class name, then operators, repeated | Level in each named class |
| `CLANLEVEL` / `-CLANLEVEL` | clan name (or `1`, `*`), then operators | Level in that clan |

Operators (`levelCompiledHelper`, 781-817):

| Operator | Meaning | Internal entry |
|---|---|---|
| `=` | equal | LVLEQ |
| `>` | greater than | LVLGR |
| `>=` | greater or equal | LVLGE |
| `<` | less than | LVLLT |
| `<=` | less or equal | LVLLE |

Examples: `+LEVEL -<5 ->20` rejects levels below 5 or above 20. `-LEVEL +>=5` requires level 5 or higher. A standalone `-<5` (no keyword) compiles to a disallow entry (5638-5640). A standalone `+<5` is logged as "Bad Zappermask key" and dropped (5642-5644). Level checks are skipped for targets that are not `Physical` (6164-6176).

#### 3.5 Date and time families

Each date keyword accepts plus or minus value tokens, and the sign is ignored (5294). Its value is one of three forms:

- `N`: exact match.
- `N of M`: `Triad`, true when `num % M == N` (`isDateMatch`, 3652-3656; compile 5318-5325).
- `Nth M` (for example `3rd 5`): `Pair`, true when `floor(num / M) == N` (3659-3664; compile 5306-5313). The compile code carries the comment `-DAY +3rd 5 -- no earthly idea` (5281), so this form is not confidently specified.

| Family | Keywords | Clock read | Lines |
|---|---|---|---|
| Hour | `HOUR`, `HOURS` | area clock, hour of day | 7282-7290 |
| Day of month | `DAY` | area clock | 7774-7805 |
| Day of year | `DAYOFYEAR` | area clock | 7638-7649 |
| Week of month | `WEEK`, `WEEKS` | area clock | 7378-7409 |
| Week of year | `WEEKOFYEAR`, `WEEKSOFYEAR` | area clock | 7706-7737 |
| Month | `MONTH`, `MONTHS` | area clock | 7574-7605 |
| Year | `YEAR`, `YEARS` | area clock | 7314-7345 |
| Season | `SEASON`, `SEASONS` | area clock | 7482-7513 |
| Birth day | `BIRTHDAY` | player's birthday | 7806-7837 |
| Birth day of year | `BIRTHDAYOFYEAR` | birthday clock | 7670-7705 |
| Birth season | `BIRTHSEASON` | birthday clock | 7446-7481 |
| Birth week | `BIRTHWEEK` | birthday clock | 7410-7445 |
| Birth week of year | `BIRTHWEEKOFYEAR` | birthday clock | 7738-7773 |
| Birth year | `BIRTHYEAR` | player's birthday | 7346-7377 |
| Birth month | `BIRTHMONTH` | player's birthday | 7606-7637 |

Birth keys apply to players only. For a monster, `+BIRTH...` passes and `-BIRTH...` fails (7806-7837 pattern).

#### 3.6 Logic and special keys

- `IF` / `-IF`: values are script condition tokens. Each is compiled with `DefaultScriptingEngine.parseEval` (5555-5563). A parse failure is logged and the condition dropped (5567-5571). `-IF` requires at least one condition to be true (8868-8890). `+IF` accepts the group at once if any condition is true, and otherwise does nothing (8891-8910). The language itself is in `guides/Scriptable.html` (not read).
- `IFSTAT` / `-IFSTAT`: values are `NAME=VALUE`, `NAME!=VALUE`, `NAME>=VALUE`, `NAME<=VALUE`, `NAME&=MASK` (all bits), and `NAME|=MASK` (any bit). Names are generic stat names, read with `CoffeeMaker.getAnyGenStat` for physical objects (8666-8726; compile 5392-5430).
- `OR` / `-OR`: `+OR` joins two groups; `-OR` joins them and negates the second (see section 5).
- `QUALLVL` / `-QUALLVL`: an ability ID, then an optional integer adjustment. The check compares `CMLib.ableMapper().qualifyingClassLevel` with `qualifyingLevel + adjustment` (7838-7859). See Problems.

### 4. Compilation (`maskCompile`, 4158-5653)

- Each keyword consumes the tokens after it, depending on its family. List families collect values with `fromHereStartsWith` (896-932). Raw-string lists (`ITEM`, `WORN`, `MATERIAL`) take every `+` or `-` token up to the next keyword (5021-5067). Numeric families take exactly one token (5493-5540). Level families take operator tokens (4368-4415).
- `fromHereStartsWith` matches values by prefix. It walks a sorted map with `ceilingKey` and accepts the first key that starts with the typed text, so `-FIG` can resolve to `-FIGHTER` (910-911). A list ends at the next keyword, at an unsigned token, or at a loose code. A signed value that is not found is logged (`Bad ZapperMask parm`, 923) and skipped, and the list ends.
- A token that is not a keyword is tried as a loose code (5595-5646), in this order: an exact loose key (generated for class, race, alignment and gender names, 323-419); a faction range code with a leading minus (5604-5612); a prefix of a loose key (5615-5624); a level operator with a leading minus (5638-5640).
- An unsigned token that matches none of these stops compilation (5642-5645). Everything after it is ignored.
- `OR` and `-OR` each start a new group (5382-5391). `RIDE` and `FOLLOW` put every following entry in the same group into their own nested entry (5374-5381).
- The result is `CompiledZapperMaskImpl(entries, useItemFlag, useRoomFlag)` (5649-5652). The flags tell the evaluator whether to build an item or resolve a room.
- `syntaxCheck` (3702-3723) accepts any string that contains one keyword or one loose code. It cannot catch most mistakes.

### 5. Evaluation (`maskCheck`, 5677-5738; `maskCheckSubEntries`, 6045-8918)

- `maskCheck` returns true to pass and false to reject (`MaskingLibrary.java:135, 156-157`). A null target or an empty mask passes.
- Top level: groups are OR-ed. A group that follows `-OR` is negated first (5713-5736). So `-CLASS +thief +OR -CLASS +mage` means thief or mage, and `-CLASS +fighter -OR -RACE +dwarf` means fighter, or anyone who is not a dwarf.
- Inside a group: entries run in order and the group is an AND. An entry can reject the group (return false), or accept it at once (return true). Accepting entries are `+SYSOP` (6059-6061), `+SUBOP` (6067-6070), `+PLAYER` and `+NPC` (6960-6967), `+RIDE` and `+FOLLOW` (7940-7963) and `+IF` when a condition holds (8891-8910). A group with no rejecting entry reaches `return true` (8917).
- Targets: a non-MOB target is checked against a placeholder `StdMOB` (`nonCrashingMOB`, 276-283; 5705). Item checks use the item itself, or the first item the placeholder carries (285-298). Room checks use the target's room. For an `Area`, the room is the area's outdoor metro room (`outdoorRoom`, 5655-5665).
- Stat keys use fixed sources. `+STR` and friends always read base stats (`getBaseCharStats`, 5667-5675, which copies the base stats and applies the race). `+ADJSTR` and friends always read current stats. The `actual` flag chooses base or adjusted values only for class, race, gender, level, class level, name and deity keys (`MaskingLibrary.java:132`).
- For an `Area` target, the placeholder mob receives the area's median alignment (5711-5712), so `-ALIGNMENT` works on areas.
- A `NullPointerException` in any entry is caught and that entry is skipped (8913-8915).

### 6. Numbers, levels and chance

- Numeric directions are in section 3.3. Level operators are in section 3.4.
- `-CHANCE N` fails when `rollPercentage()` is below N (8251-8255). `rollPercentage` returns 1 to 100 (`Libraries/Dice.java:409-412`), so `-CHANCE 30` fails on rolls 1 to 29 and passes about 71% of the time.

### 7. Date and time

- Area keys read the room's area clock (`room.getArea().getTimeObj()`). WEATHER reads `room.getArea().getClimateObj().weatherType(room)` (7514-7523). DOMAIN reads `room.domainType()` (7544-7552).
- Birth keys read `mob.playerStats().getBirthday()` (indexed by `PlayerStats.BIRTHDEX_*`, for example 7348-7352, 7608-7612, 7806-7812) or the birthday clock (`getBirthdayClock`, 7415). They need `mob.isPlayer()`.
- `dateMaskToNextTimeClock` and `dateMaskToExpirationTimeClock` (9176-9253) find when a mask's date part next changes. They brute-force by bumping a copy of the clock (9034-9173). `Thief_Runecasting` uses them.
- The offline evaluator ignores every date key (9728-9840).

### 8. Skill flags (SKILLFLAG)

- Syntax: a `+` or `-` token with `&`-joined terms, for example `-SKILLFLAG +ENCHANTMENT`. A `!` before a term negates it (`makeSkillFlagObject`, 538-594).
- A term is resolved in this order: classification (`Ability.ACODE.DESCS`, `Ability.java:706`: SKILL, SPELL, PRAYER, SONG, TRAP, PROPERTY, THIEF SKILL, LANGUAGE, CHANT, COMMON SKILL, DISEASE, POISON, SUPERPOWER, ARCHON SKILL, TECH SKILL); domain (`Ability.DOMAIN.DESCS`, 893); flag (`Ability.FLAG_DESCS`, 1036); quality (`Ability.QUALITY_DESCS`, 1095: MALICIOUS, INDIFFERENT, OK_SELF, OK_OTHERS, BENEFICIAL_SELF, BENEFICIAL_OTHERS). If nothing matches exactly, domain, flag and quality names are tried as prefixes or suffixes (618-650).
- Within one term list, every term must match the same skill (`evaluateSkillFlagObject`, 712-757). The keyword passes if any skill the mob knows matches (6747-6760). For a spell-holding object such as a scroll or wand, the check reads its spell list text instead (6729-6745).

### 9. Shorthand masks (`parseSpecialItemMask`, 10017-10243)

Used by craft brokers and item requests. It turns a few words into a full mask.

| Input | Produced mask | Note |
|---|---|---|
| `5` | `-LEVEL +=5` | exactly level 5 |
| `5-` | `-LEVEL +>=5` | level 5 or more |
| `5-10` | `+LEVEL -<5 ->10` | levels 5 to 10 |
| `RING` | `-WORNON +finger` | |
| `ARMOR`, `WEAPON`, `FOOD`, `DRINK`, `POTION`, `WAND`, `RESOURCE` | `-JAVACLASS` lists of generic classes (10092-10114) | |
| weapon damage type or class name | `-WEAPONTYPE +"..."` or `-WEAPONCLASS +"..."` (10145-10192) | |
| wand or potion ability name | `-SKILL +<ability ID>` (10195-10207) | |
| `NAME` or `NAMED`, then text | `-NAME +"..."` with the rest of the line (10118-10123) | |
| material name | `-RESOURCE +<name>` (10124-10128) | |

Two notes. A `-10` form cannot work: the `x==0` branch is reachable only for tokens that start with a digit (10027, 10061-10071). Errors are reported by inserting a message at the front of the list and returning null (10042).

### 10. Descriptions and help

- `maskDesc(text, skipFirstWord)` (1198-3641) walks the same keyword table and writes English. "Allows only ..." and "Disallows ..." are the two standard openings. `skipFirstWord` drops the opening so the phrase can follow another word.
- `maskHelp` returns the help text with `<WORD>` replaced by the caller's word, or "disallow" by default (509-520). The text is read once from `resources/help/zappermasks.txt` and cached as a resource (300-322).
- `maskDesc` is a second switch, not derived from the compiled entries. That is the main source of drift listed under Problems.

### 11. The offline-player evaluator

`maskCheck(…, ThinPlayer)` (9255-9911) evaluates a mask against an offline player record with a subset of the keys. Class, base class, race, race category, name, level, quest win, deity, port, chance, anyclass and the ride and follow keys are implemented. Most item, area, stat, skill and date keys fall into a no-op list (9683-9840) and pass. `+IF` and `-IF` always reject (9841-9843). `-RIDE` and `-FOLLOW` always reject, and `+RIDE` and `+FOLLOW` always pass (9677-9682).

### 12. Worked examples

- `-CLASS +thief +mage`: thieves or mages only (value list 4181-4194; check 6081-6086).
- `+RACE -elf -dwarf`: anyone except elves and dwarves (check 6982-6990).
- `+LEVEL -<5 ->20`: levels 5 to 20 only, the same as the shorthand `5-20` (checks 8812-8829).
- `-STR 12`: base strength at most 12 (8220-8224).
- `-SKILL +Spell_Sleep(75)`: the ability at 75% proficiency or more (6696-6725).
- `-ITEM +lantern`: the mob carries an item named lantern (8736-8749).
- `-CLASS +thief -OR -RACE +dwarf`: a thief, or anyone who is not a dwarf (5713-5736).
- `-CHANCE 30`: fails about 29% of checks, and each check rolls again.

## Content worth mining

- The keyword catalogue (section 3) is a ready builder vocabulary for a text game. Most of it maps to world state: time, season, weather, terrain, areas and parent areas, skills and proficiency, items carried or worn, materials, factions, quest completion.
- Vocabulary lists in the source:
  - Weapon damage types (`Items/interfaces/Weapon.java:87-101`): NATURAL, SLASHING, PIERCING, BASHING, BURNING, BURSTING, SHOOTING, FROSTING, GASSING, MELTING, STRIKING, LASERING, SONICING, DISRUPTING.
  - Weapon classes (`Weapon.java:166-178`): AXE, BLUNT, EDGED, FLAILED, HAMMER, KARATE, POLEARM, RANGED, SWORD, DAGGER, STAFF, THROWN.
  - Outdoor domains (`Locales/interfaces/Room.java:110-125`): CITY, WOODS, ROCKY, PLAINS, UNDERWATER, AIR, WATERSURFACE, JUNGLE, SWAMP, DESERT, HILLS, MOUNTAINS, SPACEPORT, SEAPORT.
  - Indoor domains (`Room.java:155-165`): STONE, WOODEN, CAVE, MAGIC, IN_UNDERWATER, GAP, CAVELAKESURFACE, METAL, INNERSEAPORT, CAVESEAPORT. Their numeric values start at `INDOORS` = 128 (`Room.java:78`).
  - Weather (`Common/interfaces/Climate.java:193-195`): CLEAR, CLOUDY, WINDY, RAIN, THUNDERSTORM, SNOW, HAIL, HEAT, SLEET, BLIZZARD, DUST, DROUGHT, COLD, FOG.
  - Seasons (`Common/interfaces/TimeClock.java:968-976`): SPRING, SUMMER, FALL, WINTER.
  - Alignments (`Common/interfaces/Faction.java:1841`): INDIFF, EVIL, NEUTRAL, GOOD, LAWFUL, CHAOTIC, MODERATE. The last three are inclinations.
  - Genders: MALE, FEMALE, NEUTER.
  - Skill classifications, domains, flags and qualities: section 8.
  - Dispositions and senses: `Libraries/interfaces/CMFlagLibrary.java` enums `Disposition` (line 1656) and `Senses` (line 1856).
  - Location types (`MUDZapper.java:180-187`): OWNED, CLANOWNED, PRIV, ROOMID(x), ROOMSTR(x).
  - Security flags (`core/CMSecurity.java:2341`): about 200 names, not listed here.
- The shorthand in section 9 is a good model for item and level requirements.
- The help file (`resources/help/zappermasks.txt`, 7,359 bytes) is a terse, example-driven reference. Each line gives a mask, a plain-English effect, and `<WORD>` for the verb.
- Test classes exist for some keys: `Tests/DateMasks.java`, `Tests/ZapperAnyclasslevel.java`, `Tests/PropHavezapper.java`, `Tests/PropRidezapper.java`, `Tests/PropWearzapper.java`. They were not read.

## Notable design ideas

- One sign per keyword gives both allow-lists and deny-lists in one syntax. `-CLASS +thief +mage` and `+CLASS -thief -mage` have the same shape with the polarity flipped.
- The vocabulary comes from live data. Bare tokens such as `-FIGHTER`, `-ELF`, `-GOOD` and `-MALE` are created from the class, race, alignment and gender tables (323-419, 5595-5646). A new race or class becomes valid in every mask without a code change.
- Values match by prefix against sorted keys (910-911, 896-932), so `-FIG` is enough.
- `OR` and `-OR` turn a flat list into a boolean expression. Each group is an AND, and `-OR` negates the group after it.
- Accepting entries (`+SYSOP`, `+PLAYER`, `+IF`, `+RIDE`, `+FOLLOW`) act as exemptions that end the group early.
- Nested masks: `-RIDE` and `-FOLLOW` apply the remaining conditions to the mount or the leader (5374-5381; 7932-7963). This is composition over related objects.
- `-CHANCE` puts a random check into the data, so a mask can be a probabilistic gate.
- Area-level checks reuse mob checks by giving a placeholder mob the area's median alignment (5711-5712).
- Date masks have a "next time it flips" solver (9176-9253), which suits messages such as "the gate opens at dusk".
- Skill flags form a small set algebra (`&`, `!`, prefix matching over classification, domain, flag and quality), 538-757.
- A single string drives both evaluation and English description (1198-3641), so builders can read what a mask does.
- Compiled masks are cached by normalized text (462-480).

## Problems and cruft

Everything here comes from reading the code. Nothing was executed, so each item needs a runtime check before anyone relies on it.

Silent failure:

1. An unsigned, unknown token ends compilation, and the rest of the mask is dropped (5642-5645).
2. A value with the wrong sign ends its list. `-CLASS FIGHTER` produces a `_CLASS` entry with no values, and that entry rejects everyone (`fromHereStartsWith` 915-931; check 6081-6086). The unsigned `FIGHTER` then stops compilation (item 1).
3. A standalone `+<5`-style level operator is logged and dropped (5638-5644). A malformed `IF` script is logged and dropped (5567-5571).
4. `-IFSTAT` accepts only `=`-based operators. A bare `>` or `<` without `=` is silently ignored (5408-5424).
5. A `NullPointerException` in any entry is swallowed (8913-8915; 9906-9908).
6. `syntaxCheck` passes any string containing one keyword (3711-3716).

Logic bugs:

7. `QUALLVL` compile does not consume the ability token (`v` is not advanced, 5335-5351). The token is then read by the main loop as an unsigned unknown word, which stops compilation (item 1). Entries after `+QUALLVL` or `-QUALLVL` are dropped. The description code does consume the token (2459-2471), so the two disagree.
8. An empty group is always true. `maskCheckSubEntries` returns true for an empty set (8917), and an `OR` with nothing on one side creates one (5382-5391). A leading `OR` or `-OR`, a trailing `+OR`, or two `OR` tokens in a row therefore make the top-level result true, so the mask passes everyone (by reading the group and connector logic at 5713-5736).
9. `minMaskLevel` overwrites instead of taking the maximum. `-LEVEL +>=10 +<=20` ends at the floor, because `LVLLE` resets the value to `minMinLevel` (3985-3989). The same pattern appears at 4012-4017 and 4039-4044.
10. `-WEAPONCLASS` passes non-weapons, while `-WEAPONTYPE` rejects them (6458-6469 against 6536-6545).
11. For item targets, `+WORN` and `-WORN` run the opposite test to the mob branch. `amWearingAt(IN_INVENTORY)` means "not worn" (`Wearable.java:251`; `StdItem.java:374-378`), so `+WORN` on an item passes only if the item is worn (8762-8767), and `-WORN` on an item fails if it is worn (8786-8790). The comment at 8790 says the two should behave differently, which they do, but in the opposite direction from the mob branch (8753-8761, 8774-8785).
12. A `FACTION` list that contains `EVIL`, `NEUTRAL` or `GOOD` loses the faction names collected so far (`parms.clear()`, 4746). The alignment word and the values after it are then re-read by the main loop as loose codes or as separate single-value entries (4744-4749).
13. `QUALLVL` compares two qualifying thresholds from `AbilityMapper` (`qualifyingClassLevel` and `qualifyingLevel + adjustment`, 7838-7859; `AbilityMapper.java:406-467`), not the mob's level. The description says "Qualifies for ...", which suggests a level check. Unverified against the intended design.
14. `IF` tokens are upper-cased (4167, 5545-5560), but `CLASSTYPE` arguments are not (4200-4201). Script names that are case-sensitive would break. Unverified.
15. `-CHANCE N` fails on N-1 of 100 rolls, not N. The description says `100-N` percent are allowed (3121-3126), so the code allows 101-N percent. The help file has the same off-by-one ("<WORD> the given % of the time").
16. `parseSpecialItemMask`: the `x==0` branch (10061-10071) cannot run, because it is entered only for tokens that start with a digit (10027).

Descriptions that disagree with the code:

17. `ITEM` and `WORN` in the minus form print numbers, not names. They call the three-argument `appendCommaList`, which parses each value with `CMath.s_int` (1045-1062, used at 3373 and 3382). The compile step stores names (5021-5042).
18. `-WEAPONCLASS` uses the wrong sign and the wrong header. Its description reads `-` values (3510), but its compiled values are `+` entries (`compiledCache` at 357-358), and its header says "Disallows" (3506-3510).
19. `-BIRTHDAYOFYEAR`, `+BIRTHDAYOFYEAR`, `-BIRTHWEEKOFYEAR` and `+BIRTHWEEKOFYEAR` are inverted relative to their siblings. Their values are not listed (2253-2269, 2289-2306; compare 2235-2288, which is correct).
20. `-RESOURCE` has no header in the single-value case, and its plural header is singular (2553-2557).
21. `-SUBNAME` picks plural text by counting minus values, but it lists plus values (3342, 3346).
22. The `QUALLVL` description has its branches in the wrong order. "Qualifies for" shows for any non-zero adjustment, and the "levels before" branch can never run (2459-2471, 2484-2496). The ability name is appended with no separator (2464, 2489).
23. `-IF` says "Requires not meeting the following condition(s)" (3543-3544), but the check requires a met condition (8868-8890). The help text ("unless they meet") matches the code.
24. Typos and wording: "The the following types" (1405-1407), "seasosn" (1973), "lega judge" (3575), "Requires followed mounts" (1601), "who`ve" (2705), "whose who are" (3289-3291), "Requires meets" (3555).

Structure:

25. Two evaluators. The offline `ThinPlayer` path (9255-9911) is a partial copy with different semantics for `IF`, `RIDE` and `FOLLOW`, and most item, area, stat and date keys are no-ops there.
26. The placeholder mob is a shared singleton (276-283), and area checks change it (5711-5712). That is not thread-safe.
27. The `actual` flag is applied inconsistently. Stat keys ignore it (8142-8245), while class, race and level keys honor it.
28. Help gaps (checked by searching the file): `CLASSTYPE`, `QUALLVL`, `AREABLURB`, `AREAINSTANCE`, `OFFICER`, `JUDGE`, `PLANE`, the birth family, and the `ADJ` stats (only a combined line) are not documented. `-WORN` appears twice.
29. Comments in `maskCompile` name the wrong sign or key in places (for example `case _SKILL: // +skills` at 4960, `case _WORNON: // +WornOn` at 5069, and the `ADJCONSTITUTION` case, which says `-adjcha`, at 5501).

## Takeaways for a Python SUD

Borrow:

- Keep the eligibility idea, but store it as data, not as a string language. Parse once, validate at load time, and report unknown names as errors. CoffeeMud's silent drops are the main reason its masks are hard to trust. A predicate tree is a reasonable shape (illustrative, not from the codebase):

```python
from dataclasses import dataclass

@dataclass(frozen=True)
class Verdict:
    ok: bool
    reasons: tuple[str, ...] = ()        # e.g. "You are too weak for this."

# Node types: AllOf(...), AnyOf(...), Not(...), Has(skill="spell_sleep", min_pct=75),
# StatMax("str", 12), LevelRange(5, 20), Carries("lantern"),
# Clock(hours=..., seasons=...), WeatherIn(...), Flag("quest:x", True)

def check(node, actor, world) -> Verdict:
    ...
```

- Generate the English text from the same tree, so the description cannot drift from the rule. Test that round trips match.
- Use one numeric convention with named bounds (`min_str`, `max_str`). CoffeeMud uses opposite directions for stats and for item magnitudes, which is the kind of mismatch a single convention avoids.
- Keep the vocabulary generated from content tables (races, classes, skills, weather, seasons), so new content is valid automatically. The bare-token idea (`-FIGHTER`) is worth keeping if the SUD has a text authoring language.
- Keep the world-state families, which are the valuable part for a living world: time of day, season, weather, terrain, areas and parent areas, skill thresholds, item possession, factions, quest flags, and skill-flag set algebra.
- Keep the early-accept exemptions only if the game needs them. They are easy to misread.
- Cache compiled predicates by their normalized source, and clear the cache when content reloads.

Simplify or drop:

- Drop the `ThinPlayer` duplicate. One evaluator with one set of semantics is enough.
- Drop the base-versus-adjusted flag. Name the two values (`base_str`, `str`) instead.
- Replace `IF` script strings with Python callables, or with a small safe expression language. Do not `eval`.
- Replace `CHANCE` with an explicit probability and an injected, seeded RNG, so tests are deterministic.
- Drop the Java-reflection `CLASSTYPE` idea.

Rethink for one player:

- `PLAYER`, `NPC`, `SYSOP`, `SUBOP`, `ACCOUNT`, `ACCCHIEVE` and host `PORT` have little meaning with no other humans. Keep an NPC-only test if the SUD has NPC routines.
- `GROUPSIZE`, `FOLLOW`, `RIDE`, `CLAN` and `CLANLEVEL` are group or organization concepts. A single-player game may want factions or reputation instead.
- `BIRTH*` keys map to the character's own birthday, which may be a feature or may be dropped.
- `ISHOME` is useful for NPC routines (home location, and "is away from home").
- `DEITY`, `TATTOO` and `TAG` can become general flags on the world state.

## Coverage

- Read in full: `Libraries/interfaces/MaskingLibrary.java` (648 lines); `Libraries/MUDZapper.java` (all 10,244 lines, in sequential chunks of 400 lines or fewer); `resources/help/zappermasks.txt` (7,359 bytes).
- Read in part, for definitions the mask language depends on: `core/CMParms.java:788-845` (tokenizer); `Libraries/interfaces/AbilityMapper.java:395-470`; `Libraries/Dice.java:405-412`; `Abilities/interfaces/Ability.java:700-716, 888-900, 1030-1040, 1090-1100`; `Locales/interfaces/Room.java:78, 110-125, 155-165`; `Common/interfaces/Climate.java:193-195`; `Items/interfaces/Weapon.java:87-101, 166-178`; `Common/interfaces/Faction.java:1841`; `Common/interfaces/TimeClock.java:968-976`; `Items/interfaces/Wearable.java:251`; `Items/Basic/StdItem.java:374-378`; `core/CMSecurity.java:2341` (names only); `core/CMClass.java:1656-1662`; `core/CMLib.java:500`; `MOBS/interfaces/MOB.java:44`.
- Keyword spellings: generated by script from the `ZapperKey` enum (200 constants, 305 spellings), then grouped into families by reading `maskCompile` and the check code.
- Callers: seen only by grep (file and line), not read. Examples include `Prop_HaveZapper`, `Prop_WearZapper`, `Prop_RideZapper`, `Channel`, `CommandJournal`, `ClanApply`, `StockMarket`, `ColumbiaUniv`, `Thief_Runecasting`, `StdCraftBroker`.
- Not read: `Libraries/editors/EditorZappermask.java`; `Tests/*Zapper*`, `Tests/Prop*zapper*` and `Tests/DateMasks.java`; `guides/Scriptable.html` (the `IF` language); the full `CMSecurity` flag list.
- Nothing was executed. Every "by reading" finding in Problems needs a runtime test before it is relied on.
