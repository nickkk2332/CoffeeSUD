# Configuration files: coffeemud.ini, lists, laws and web settings

> Part of the CoffeeMud NEWOVERVIEW. Bucket: `c01` (content: Configuration files: coffeemud.ini, lists, laws and web settings) · 14 files · 290684 bytes of data.

## Summary

CoffeeMud keeps almost all of its tuning outside the Java code. `coffeemud.ini` (309 active keys in about 25 headed sections) is the boot configuration, and `CMProps.java` turns it into typed values and holds the in-code fallbacks. `resources/lists.ini` (about 80 keys) is a library of charts and phrase tables (XP ladder, damage vocabulary, weather, personality traits, wand words) that makes flavour text and thresholds editable without recompiling. `laws.ini` and `lawtoc.ini` describe a town's crimes, punishments, taxes and law book, and `movingroom.xml` describes vehicles that travel between stops. The web, email, CM1 and channel settings (`web/*.ini`, `email.ini`, `cm1.ini`) are multiplayer and server infrastructure. For a single-player SUD the useful material is the time scale, the XP and death rules, the combat formulas, the economy knobs, the crime and vehicle data and the text tables.

## Key files and types

| File or class | Role | Lines |
|---|---|---|
| `coffeemud.ini` | Boot configuration: 309 active keys, 25 headed sections | 2526 |
| `com/planet_ink/coffee_mud/core/CMProps.java` | Loads `coffeemud.ini` and `lists.ini`; typed `Str`/`Int`/`Bool` enums; in-code defaults; list-valued key parsers; word filter (`applyINIFilter`) | 3965 |
| `resources/lists.ini` | About 80 keys: charts, phrase tables and word lists | 2056 |
| `CMProps.ListFile` enum (CMProps.java:546-644) | The list of keys the engine expects in `lists.ini` | (same file) |
| `resources/laws.ini` | Town law: officers, judge, jails, taxes, 46 crime records, sentence timings | 234 |
| `resources/lawtoc.ini` | Text of the in-game law book (12 pages, with editing variants) | 250 |
| `com/planet_ink/coffee_mud/Common/DefaultLawSet.java` | Parses `laws.ini`; monthly property-tax assessment | 1081 |
| `com/planet_ink/coffee_mud/Behaviors/Arrest.java` | Policing: witnessing, arrest, trial, sentence and jail timers | 4148 |
| `com/planet_ink/coffee_mud/Behaviors/TaxCollector.java` | Citizen tax on carried coin (`CITTAX`) | 455 |
| `com/planet_ink/coffee_mud/Items/Basic/StdLawBook.java` | The law-book item; loads `lawtoc.ini`; lets authorised officers edit | 1603 |
| `resources/mxp_images.ini` | Default MXP image keys for races, rooms, items, exits (639 key lines) | 712 |
| `com/planet_ink/coffee_mud/Libraries/CMProtocols.java` | Reads `mxp_images.ini` and resolves keys by fallback chain (`getDefaultMXPImage`) | 5149 |
| `resources/movingroom.xml` | Route data for moving rooms (a subway and a bus) | 192 |
| `com/planet_ink/coffee_mud/Behaviors/MovingRoom.java` | Moving-room behaviour; token substitution in messages | 545 |
| `resources/titles.ini` | Automatic title rules (only comments in the shipped copy) | 25 |
| `com/planet_ink/coffee_mud/Libraries/AutoAwards.java` | Title-rule parser and holder management | 894 |
| `resources/parser_en_TX.properties` | Input command pre-processor rules | 186 |
| `resources/translation_en_TX.properties` | Output translation rules (an empty template) | 101 |
| `com/planet_ink/coffee_mud/Libraries/DirtyLanguage.java` | Loads the parser and translation files | 1049 |
| `web/common.ini`, `web/pub.ini`, `web/admin.ini` | Web server settings: shared base, public server, admin server | 292, 74, 83 |
| `web/cm1.ini`, `web/email.ini` | CM1 server and SMTP mail settings | 13, 112 |
| `com/planet_ink/coffee_web/util/CWConfig.java` | Parses web server keys | 1535 |
| `com/planet_ink/coffee_mud/core/smtp/SMTPserver.java`, `MassMailer.java` | Mail server and mail retention timings | 751, 310 |
| `com/planet_ink/coffee_mud/application/MUD.java` | Boot: loads the ini, merges web ini files, starts services | 2471 |
| `com/planet_ink/coffee_mud/Libraries/MUDFight.java` | Combat rolls, damage, damage words, kill XP, death consequences, loot | 3817 |
| `com/planet_ink/coffee_mud/Libraries/CoffeeLevels.java` | XP chart lookup and level-up | 1746 |
| `com/planet_ink/coffee_mud/Abilities/StdAbility.java` | Mana and move cost of skills and spells | 3018 |
| `com/planet_ink/coffee_mud/core/CMath.java` | The expression language behind every `FORMULA_*` and cost key | 2778 |

## How it works

### 1. Loading: from INI file to typed settings

- At boot, `MUD.java:2099-2100` calls `CMProps.loadSystemPropPage(iniFile)` (CMProps.java:849). The file is read with `java.util.Properties` (constructors at CMProps.java:714-721 and 787-829), so backslash line continuations work and keys are case-sensitive.
- Command-line arguments are copied into the same page after the file is read, so they win over the file. A key written `NAME:N` applies only when N is 0 (MUD.java:2100-2112).
- `CMProps.resetSystemVars()` (CMProps.java:2715) is the central dispatcher. It reads each key with `getStr`, `getLong` or `getStr(key, default)` and stores it in the `Str`, `Int` or `Bool` enums (CMProps.java:136-520) through `setVar`, `setIntVar` and `setUpLowVar`. The same method can be re-run when properties change.
- The in-code default in `getStr(key, default)` applies only when a key is missing. The shipped file always wins, so the two can drift apart (see Problems).
- Multi-value keys are parsed by small helpers: `setManaCosts` and `setExceptionCosts` (CMProps.java:2240-2275) for skill-specific overrides such as `Skill_Recall 25`; `parseRPAwards` (CMProps.java:3713); `parseXPDeferDetails` (CMProps.java:3793).

### 2. Time model

The engine has three clocks, and the chapter's numbers depend on which one a setting uses.

- **Ticks.** `TICKTIME=4000` (coffeemud.ini:2151) sets one tick to 4 real seconds. `CMProps.resetSystemVars` derives `TICKS_PER_RLMIN = round(60000/TICK)`, so 15 ticks per real minute, 900 per real hour and 21,600 per real day (CMProps.java:2720-2724).
- **Mud calendar.** `MILLISPERMUDHOUR=600000` (coffeemud.ini:1099) makes one mud hour equal 10 real minutes (150 ticks). `HOURSINDAY=6` (1104), `DAYSINMONTH=24` (1109) and `MONTHSINYEAR=7` (1115) follow. One mud day is therefore 1 real hour, one mud month is 24 mud days (1 real day), and a mud year is 7 real days. The file's own comments agree that "24 days equals 1 RL day" (coffeemud.ini:1571, 1582).
- **Real time.** Some keys count real minutes or days instead: `EXPIRATIONS` (minutes, ItemPossessor.java:104-106), `IDLETIMERS` (minutes, DefaultSession.java:3695), `EMAILDAYS` and `FAILUREDAYS` (real days, MassMailer.java:78-89).

Derived durations for the settings most likely to matter in a game:

| Setting (coffeemud.ini line) | Value | Real time |
|---|---|---|
| `TICKTIME` (2151) | 4000 ms | 4 s per tick |
| `DUELTICKDOWN` (449) | 5 ticks | 20 s |
| `INVRESETRATE` (1851) | 64800 ticks | 3 days |
| `DAYSCLANDEATH` (1573) | 2160 mud days | 90 days |
| `DAYSCLANOVERTHROW` (1584) | 952 mud days | about 39.7 days |
| `CLANVOTEO` / `CLANVOTER` / `CLANVOTED` (1597-1599) | 24 / 48 / 100 mud days | 1 / 2 / about 4.2 days |
| `BUDGET ... MONTH` (1825) | one mud month | 1 day |
| jail1-4 (laws.ini:98-101) | 20 / 40 / 80 / 160 ticks | 80 s / 160 s / 320 s / 640 s |
| parole1-4 (laws.ini:92-95) | 40 / 80 / 160 / 320 ticks | 160 s / 320 s / 640 s / 1280 s |
| `EXPIRATIONS` (1173) | 30 / 1200 / 60 / 30 / 1330 minutes | 0.5 h / 20 h / 1 h / 0.5 h / 22.2 h |

### 3. Experience, levels and rewards

**Level thresholds.** `EXP_CHART` (lists.ini:560-659) has 100 cumulative values. `StdMOB.getExpNextLevel()` (StdMOB.java:276-278) calls `CoffeeLevels.getLevelExperience(level)` with the current level, so the XP needed to advance from level L to L+1 is `chart[L]`. Selected values (computed from the table):

| Advance from | XP needed | Advance from | XP needed |
|---|---|---|---|
| 1 to 2 | 500 | 20 to 21 | 29,000 |
| 2 to 3 | 1,100 | 30 to 31 | 58,500 |
| 5 to 6 | 3,500 | 50 to 51 | 146,500 |
| 10 to 11 | 9,500 | 80 to 81 | 296,500 |
| 19 to 20 | 26,600 | 91 to 92 | 351,500 |

Increments rise by 100 per level from 500 and cap at 5,000 around level 46. Beyond the table, `CoffeeLevels.java:1156-1166` extends it linearly using the last step (5,000). The player cap is `LASTPLAYERLEVEL=91` (coffeemud.ini:515).

**XP words.** `EXPERIENCE_AMOUNTS` (lists.ini:1988-2001) maps gains to 13 phrases, from "barely any" (up to 9 XP) to "an extraordinary amount of" (up to 9,999,990). `CoffeeLevels.getXPAmountTerm` (CoffeeLevels.java:875) uses it when the `EXP-WORDS` prowess option is set (coffeemud.ini:323).

**Kill XP.** Kill rewards are split between everyone who contributed (the "dividers"), in `MUDFight.dispenseExperience` (MUDFight.java:3302-3347):

- Pool: `FORMULA_TOTALCOMBATXP = 100 + ((25*@x1) - (@x1*((25*@x1)^.5)))`, where `@x1` is the number of dividers (coffeemud.ini:2489). Computed values: 1 divider gives 120; 2 give 135.9; 4 give 160; 8 give 186.9; the peak is about 192.6 at 11; 16 give 180; 25 give 100. The formula turns negative near 32 dividers, and the code does not clamp it (see Problems).
- Share: `FORMULA_INDCOMBATXP = (@x1 * (@x2 / @x3)) < 100` (coffeemud.ini:2498). `<` is a minimum in this expression language (CMath.java:1851-1852), so one killer's share is pool times level-squared over the sum of level-squared, capped at 100.

**Fleeing.** `FLEE=EXPERIENCE` (coffeemud.ini:399). `Flee.java:157-158` computes the loss as `max(10, 10 + 5*(own level - foe level))`. The foe is paid a quarter of the loss scaled by missing HP, but only when the two sessions come from different addresses (Flee.java:164-172).

**Roleplay awards.** `RPAWARDS` (coffeemud.ini:802) is empty by default. Its parser handles introductions, speech, socials, emotes, channel use and going AFK, with a minimum interval between awards.

### 4. Death, corpses and consequences

**Death rules.** `PLAYERDEATH` (coffeemud.ini:374) is a comma list of consequence tokens. `MUDFight.handleCombatLossConsequences` (MUDFight.java:3507-3668) applies them in order:

- `UNLEVEL n`: `CMLib.leveler().unLevel` n times (MUDFight.java:3528-3536).
- `RECALL`: the body is not created (MUDFight.java:3538-3539).
- `ASTRAL` and `ASTRAL_RES`: the player becomes a spirit (`You are now a spirit.`). `ASTRAL_RES` lets the spirit re-enter its body, by ENTER and the body's name (MUDFight.java:3541-3557).
- `OUT (expr)`: unconscious for `expr` ticks through `Skill_ArrestingSap` (MUDFight.java:3559-3568).
- `PURGE n`: the character is deleted once its death count reaches `n` (MUDFight.java:3570-3606). The death counter is incremented just before these tokens run (MUDFight.java:1599), so `PURGE 1` is permadeath. Non-fatal deaths print `You have N lives remaining.` (MUDFight.java:3623).
- `LOSESKILL`: removes one random ability (MUDFight.java:3628-3645).
- A numeric or math expression: experience loss, evaluated with `@x1` = dead character's level, `@x2` = killer's level, `@x3` = rejuvenation value (MUDFight.java:3520, 3647-3656).
- `EXPERIENCE`: 100 times the dead character's level (MUDFight.java:1570, 3660-3666).

The shipped default is `(@x1 * 50) < 2000` (coffeemud.ini:374). That is the minimum of 50 times level and 2000, so a level-10 death costs 500 XP and the cap applies from level 40. Monsters use `MOBDEATH=DEATH` (coffeemud.ini:381; MUDFight.java:1575).

**Death location.** `DEATH=START` and `MORGUE=HERE` (coffeemud.ini:1020, 1036). Prefixed variants (`START_`, `DEATH_`, `MORGUE_`) can be chosen by race, class, alignment, faction, level or a zapper mask (coffeemud.ini:994-1035).

**Corpses and loot.**

- `CORPSEGUARD=ALL` decides who may loot a corpse (coffeemud.ini:413; Corpse.java:326).
- `ITEMLOOTPOLICY=20% RUIN` (coffeemud.ini:427) is applied only to monster corpses (CoffeeUtensils.java:1215). Each rule has a percentage, `RUIN` (damage the item) or `LOSS` (destroy it; the default when neither is given, CoffeeUtensils.java:1252-1253), optional `WORN` or `UNWORN`, and an optional `MASK=` zapper mask.
- Monster corpses expire after 30 minutes and player corpses after 1330 minutes (coffeemud.ini:1173; ItemPossessor.java:104-106).

**Messages a player sees.** `You lose N experience points.` (MUDFight.java:1601). `You'll need to dismount to loot the body.` (MUDFight.java:1651). `That is not EVEN a fair fight.` (StdMOB.java:3161).

### 5. Combat formulas

Every formula is a string evaluated by `CMath.parseMathExpression`, with positional variables `@x1..@x10` and `@xx` for the running result. The operators (CMath.java:1840-1860) are: `+ - * / %`; `<` = minimum; `>` = maximum; `?` = uniform random between the left and right operands; `=` and `!` = equal and not equal (1 or 0); `^` = power. Evaluation is left to right with no precedence between operators, so `(@x9-@x8)>0<1.0` means "the larger of the difference and 0, then the smaller of that and 1". Each formula is compiled once at startup (MUDFight.java:94-124).

**Action economy.** `DEFCMDTIME=0.20` and `DEFCOMCMDTIME=1.0` are the action costs of ordinary commands out of and in combat. `DEFABLETIME=0.10` and `DEFCOMABLETIME=1.0` do the same for skills and spells. A value of 1.0 is one tick (coffeemud.ini:536-546, 941-952). The values are stored as integers times 100 (CMProps.java:3099-3101) and divided back out (CMProps.java:1183-1196).

**Combat system.** `COMBATSYSTEM=DEFAULT` selects one of DEFAULT, QUEUE, MANUAL or TURNBASED (coffeemud.ini:291; CMProps.java:3022-3032; the enum is at CombatLibrary.java:51).

**Hit roll.** `MUDFight.rollToHit` (MUDFight.java:409-423) computes a threshold of `round(50 * attack / |armor|) + fudge` and then calls `CMLib.dice().normalizeAndRollLess`, which rolls under the threshold after clamping it to between 5 and 95 (Dice.java:63-75). So a hit chance is never below 5% or above 95%.

- Attack: `FORMULA_ATTACKADJUSTMENT = (50+@x1+(((@x2-9)/5)*((@x3-9)/5)*((@x3-9)/5))+@x4)-(0.15*@xx*@x5)-(0.15*@xx*@x6)-(0.3*@xx*@x7)` (coffeemud.ini:2344). Here `@x1` is base attack, `@x2` and `@x3` are current and base strength (capped at maximum), and `@x5..@x7` are the hunger, thirst and fatigue flags. Each of those three takes away 15%, 15% and 30% of the running value.
- Armor: `FORMULA_ARMORADJUSTMENT` (coffeemud.ini:2352) starts from base armour and adjusts it by dexterity and posture, then subtracts 100. The sign conventions are fiddly (see Problems).
- Level fudge: `FORMULA_ATTACKFUDGEBONUS = @x3 * (@x1 - @x2) * (@x1 - @x2)` (coffeemud.ini:2359), with `@x3` = +1 if the attacker is higher level and -1 otherwise. The bonus is therefore signed and squared: +25 to the threshold when the attacker is 5 levels higher, -25 when 5 lower. The PvP version is zero (coffeemud.ini:2360).

**Damage.**

- Targeted melee (coffeemud.ini:2424): `((1?@x1)+((@x2-10+@x3-@x4)/5)-(0.5*@xx*@x8)+(0.5*@xx*@x9)+(0.2*@xx*@x10)-(0.2*@xx*@x5)-(0.2*@xx*@x6)-(0.2*@xx*@x7))>1`. In words: a random value between 1 and the weapon's damage, plus a strength and level term divided by 5, minus half the running value if the target is unseen, plus half if it is asleep and a fifth if it is sitting, minus a fifth for each of hunger, thirst and fatigue, with a floor of 1.
- Level fudge (coffeemud.ini:2441): `(@x1 - ( ((@x9-@x8)>0<1.0) * @x1 * (((@x9-@x8)<12.0)*((@x9-@x8)<12.0))/120.0))>0`. Read with the operator rules above, this is a damage multiplier of `1 - min(k,12)^2/120` when the defender is k levels higher (k of at least 1), floored at zero. The results: k=1 is 0.8% less damage, k=5 is 20.8% less, k=10 is 83% less, and from k=11 the damage is zero. The PvP version is the identity (coffeemud.ini:2442).

**Damage words.** The wording of a hit comes from `DAMAGE_WORDS` (18 damage types by 16 tiers) indexed by `DAMAGE_WORDS_THRESHOLDS` (MUDFight.java:1805-1862). Tier selection is described in the lists section below.

**NPC hit points.** `FORMULA_NPCHITPOINTS = 3 + @x1 + (@x1 * ((@x2/2) + (1?(@x2/2))))` (coffeemud.ini:2337), with `@x1` = level and `@x2` = the mob's HP code (Dice.java:86). With the comment's "usually 11", the average is about `3 + 10 * level`: 13 at level 1, 103 at level 10, 203 at level 20. This is a derivation from the operators, not a measured value.

**Skill learning.** `FORMULA_PROFGAIN = (100 - (@x3 * 50)) * ((@x1 + 1 - @x2) / ((@x1 * 2) + (10 * @x2)))` (coffeemud.ini:2526), where `@x1` = `LASTPLAYERLEVEL` (91 here), `@x2` = the skill's qualifying level and `@x3` = 1 when fatigued (CMAbleMap.java:2888-2899). It is the percent chance per use of gaining one proficiency point. Derived values: qualifying level 1 gives 47%, 5 gives 37.5%, 10 gives 29%, 30 gives 13%, 50 gives 6%, 90 gives 0.2%. Fatigue halves each of these.

**Mana and move cost.** `MANACOST=-1, Skill_Recall 25` (coffeemud.ini:877). A negative value `-N` costs `50 + N * lowest qualifying level` (StdAbility.java:1780-1781). `MANAMINCOST=-1, 10` (coffeemud.ini:903) sets a floor of the lowest qualifying level, but never below 10 (StdAbility.java:1750-1762). A character with several classes gets `cost - (cost/30) * surplus levels` for fixed costs (StdAbility.java:1784), then the floor applies.

**Recovery.** Hit points, mana and moves recover each tick by formulas in `FORMULA_HITPOINTRECOVER`, `FORMULA_MANARECOVER` and `FORMULA_MOVESRECOVER` (coffeemud.ini:2463-2482). The shipped versions cap the stat contribution with `<`: `min(CON/2, 13)` for hit points, `min((INT+WIS)/6, 5)` for mana and `min(STR/1.5, 16)` for moves. Hunger, thirst and fatigue, sleeping, sitting or riding, flying and swimming each change the running value by a fixed fraction. I did not trace the exact `@xx` chaining, so the precise arithmetic is unverified.

**Injury.** `INJURYSYSTEM=100,60,25,100,2,10,15,20,100` (coffeemud.ini:340) is parsed into nine `INJ*` values (CMProps.java:3160-3169) that `Abilities/Misc/Injury.java` reads (lines 640, 710 and 766). The fields are: chance per hit to affect a limb; hit-point percentage before limbs are affected; a single-blow percentage that removes a limb; chance that a fully damaged limb is removed; a damage multiplier; the minimum level for limb loss (10); the minimum level for bleeding (15); the hit-point loss that starts bleeding (20%); and the chance of bleeding when a limb is lost. Amputation and two thief skills also read the bleed threshold (Amputation.java:652, Thief_Flay.java:225, Thief_CutThroat.java:134).

**Damage display.** `SHOWDAMAGE` (coffeemud.ini:299) can be `YES`, `NO`, `SOURCE` or `TARGET`. Numbers are appended as `(N)` to hit messages (MUDFight.java:748-760).

**Player-versus-player.** `PLAYERKILL=OPTIONAL-4` (coffeemud.ini:356). `OPTIONAL` requires both players to switch PvP on (PlayerKill.java:52-104), and `-4` is a level window: a fight is refused when the levels differ by more than 4 (CMProps.java:1825-1829 parses the window; StdMOB.java:3151-3161 enforces it). Exceptions are system operators, castle-doctrine property holders, duels and disease effects.

**Aggression.** `EXPRATE=6` (coffeemud.ini:406) is not an experience rate. Aggressive behaviours read it as a level-difference threshold: a mob does not start a fight with a candidate it outlevels by `EXPRATE` or more (Aggressive.java:306). VeryAggressive.java:142 and GoodGuardian.java:108 apply the same gate, and Conquerable.java:229 uses twice the value as a default level window.

### 6. Economy and shops

| Setting (coffeemud.ini line) | Shipped value | What it does (code reference) |
|---|---|---|
| `BUDGET` (1825) | `500000 MONTH` | Money a shopkeeper will spend buying from players per period. The period is a mud month (about one real day). Read at StdArea.java:898 |
| `DEVALUERATE` (1839) | `20 0.2` | Price drop per extra identical item sold: 20%, or 0.2% for raw resources. Read at StdArea.java:933 |
| `INVRESETRATE` (1851) | `64800` ticks | Shop stock resets about every 3 real days. Read at StdArea.java:979 |
| `PREJUDICE` (1786) | empty | Buy and sell multipliers per race (`BUY=... SELL=...`). Read at CoffeeShops.java:649 |
| `PRICEFACTORS` (1802) | empty | Price multipliers chosen by zapper mask, for example `0.5 -MATERIAL +WOODEN`. Read at StdArea.java:830 |
| `IGNOREMASK` (1813) | empty | Customers a shopkeeper ignores. Read at StdArea.java:864 |
| `AUCTIONRATES` (1863) | `0,2,0.1%,10%,5%,4,600` | Listing fee, timed-auction fee, per-day add-on, seller cut for live and timed auctions, minimum and maximum days. Read at DefaultAuctionPolicy.java:56 |
| `PROPERTYTAX` (laws.ini:114) | `0.0025` | Monthly tax on owned land, assessed when the area's month changes (DefaultLawSet.java:445-456) |
| `SALESTAX` (laws.ini:119) | `0` | Tax added to shop purchases |
| `CITTAX` (laws.ini:124) | `5` | Percentage of carried coin taken by a TaxCollector (TaxCollector.java:119) |
| `STATCOSTS` (598) | `<18 1, <22 2, <25 3, <99 5` | Training sessions per stat point, by current value. Read at CharCreation.java:3422 |
| `TRAINCOSTS` (822) | see file | Cost of raising HP, mana, moves and practices |
| `COMMONCOST` / `SKILLCOST` / `LANGCOST` (920-922) | `1 TRAIN` / `1 TRAIN` (plus exceptions) / `3 PRACTICE` | Cost to gain a skill, with per-skill exceptions |

Hunger and thirst use `HUNGER=500,100,100` and `THIRST=1000,100,100` (coffeemud.ini:743, 752). The first number is the stomach size, the second the percentage of food gained, and the third the percentage lost through exercise (CMProps.java:2988-2992; DefaultCharState.java:191-194).

### 7. Laws and policing (`laws.ini`, `lawtoc.ini`)

**Roles and places.** `OFFICERS=cityguard` and `JUDGE=justice judge` are keyword matches on mob names (laws.ini:4, 9; DefaultLawSet.java:775-780). `JAIL` and `RELEASEROOM` name rooms by display text (laws.ini:13, 18). `TREASURY` names where the tax money goes (laws.ini:106).

**Who is a victim and who is a trespasser.** `PROTECTED` is a zapper mask: only humanoid races (Human, Elf, Dwarf, Halfling, Gnome and giant-kin) count as victims (laws.ini:74). `TRESPASSERS=-RACE +Undead` makes undead trespassers, while all other mobs are allowed (laws.ini:83). Both are read at DefaultLawSet.java:790-791.

**Crime records.** The format (laws.ini:126-161) is `<CRIME>=<location>;<flags>;<name>;<punishment>[ extras];<message>`. Examples:

- `MURDER=;;murdering <T-NAME>;jail3;Murder is a barbarous offense.` (laws.ini:168)
- `ARMED=!home !giant;!recently !combat witness ignore90;violating the armed citizen law;warning punishcap=parole1;...` (laws.ini:166): carrying a drawn weapon in town is a crime only if witnessed, usually ignored, and capped at parole.

Flags include `witness`, `!witness`, `!recently`, `combat`, `!combat`, `home`, `!home`, `indoors`, `!indoors` and several `ignoreNN` chances. Punishments run warning, threat, parole1-4, jail1-4 and death. Extras are `separate`, `skiptrial`, `detain=`, `fine=`, `norelease`, `punishcap=`, `shame=` and `banish=` (laws.ini:127-161).

The catalogue has 46 records, summarised below. Adult and crude entries are paraphrased.

| Group | Count | What is covered (sentence in brackets) |
|---|---|---|
| Base crimes (laws.ini:163-171) | 9 | Resisting arrest (jail1), trespassing (jail3), indecent exposure (warning, capped at parole1), armed citizen (warning), assault (jail1), murder (jail3), property robbery (jail3, no witness needed), tax evasion (jail1), prison break (jail1) |
| Skill crimes (laws.ini:180-201) | 22 | Twelve `THIEF_*` crimes (swiping, stealing, arson, trapping, bribery, conning, embezzlement, contract killing and death traps, forgery, racketeering, robbery); a prostitution law; an enslavement law; public intoxication (four entries, parole1, capped at jail4); dangerous-magic prayers (three, jail4); kidnapping (jail3) |
| Emote crimes (laws.ini:213-223) | 11 | Matched against emote and social text: spitting, a crude social term, throttling, barking, rude gestures, public fighting, swearing, being sick in the street, and a voodoo emote. CRIME6 is an adult-themed social and CRIME9 concerns indecent exposure |
| Banned substances (laws.ini:231-234) | 4 | Four keywords matched against item names or materials, each with parole3 |

**Sentencing timings.** Parole and jail durations are in ticks (laws.ini:92-101), and the code applies them with `startTickDown` (Arrest.java:3391). `lawtoc.ini` says "in seconds" for the same values (lawtoc.ini:85, 90, 99, 105), which is wrong (see Problems).

**The law book.** `lawtoc.ini` holds 12 pages, `P1` to `P12`, each with a plain version and a `MOD` version. The `MOD` version is shown to an authorised officer and says how to change that page. For example, the jail page lets an officer carry the book to a room and select `A` to add it as a jail (lawtoc.ini:93). `StdLawBook.java:220` loads the file, and the book's menus write back into the law object, so law can be changed in the game world.

**Taxes.** `PROPERTYTAX` is applied when the month changes, not each tick (DefaultLawSet.java:448-456). A value of 1.0 or more is treated as a percentage and divided by 100.

### 8. Lists and text tables (`lists.ini`)

**Mechanism.** `CMProps.getRawListFileEntry` (CMProps.java:2305-2345) reads the file named by `LISTFILE` (coffeemud.ini:1350) into a `Properties` object using the input charset. Each `ListFile` enum constant names one key. Accessors include `getListFileChoiceFromIndexedList` (CMProps.java:2571), which returns a random pick from a tier, and `getListFileChoiceFromIndexedListByHash` (CMProps.java:2586), which picks by a hash so the same item always gets the same word. Within a value, `,` separates tiers, `;` separates groups, and `|` separates random alternatives (lists.ini:4-13).

**Catalogue** (counts from the file):

| Key (lists.ini line) | Entries | Used for (consumer) |
|---|---|---|
| `ARMOR_DESCS` (21) + `ARMOR_DESCS_CEILING=300` (18) | 50 tiers, "vulnerable" to "invulnerable!" | Armour wording; tier = floor(armour / 300 * 50), with an extra `!` per 100 over the ceiling (MUDFight.java:1930-1937) |
| `PROWESS_DESCS` (106) + ceiling 300 (103) | 113 tiers, "none" to "death incarnate!" | Attack and combat wording, from "novice" to "a lord of death X" (MUDFight.java:1977-1982) |
| `COMBAT_ADJS` (222), `COMBAT_NOUNS` (238) | 13 adjectives, 31 nouns | Relative attack wording, "Powerless" to "Deadly", "Neophyte" to "Demigod" |
| `EXTREME_ADVS` (92) | 7 | Intensifiers: "Very", "Extremely", "Incredibly", "Astoundingly", "Stupendously", "Drastically", "Amazingly" (Score.java:74-92; MUDFight.java:1873-1882) |
| `DAMAGE_DESCS` (272) + ceiling 100 (271) | 113 tiers | Damage wording, "none" through "death incarnate!" |
| `DAMAGE_ADJS` (386) | 11 | Damage adjectives, "infinitesimal" through "Forceful" |
| `DAMAGE_WORDS` (448) + `DAMAGE_WORDS_THRESHOLDS` (441) | 18 damage types x 16 tiers; 17 thresholds | Hit verbs by damage type and damage size (MUDFight.java:1805-1862) |
| `WEAPON_HIT_DESCS` (429), `MISS_DESCS` (415), `WEAPON_MISS_DESCS` (403) | 6 each | Hit and miss sentences with `<S-NAME>`, `<T-NAMESELF>`, `<TOOLNAME>` and `<DAMAGE>` tokens (MUDFight.java:2084-2096) |
| `HEALTH_CHART` (497) | 11 tiers, coloured | Health text from "hovering on deaths door" to "in perfect health" (MUDFight.java:2114-2117) |
| `CORPSE_BLURBS` (514) | 17 damage causes, 1 to 3 phrases each | Corpse descriptions (MUDFight.java:1740) |
| `ARMOR_MISFITS` (487) | 7 | Messages for armour a character's class does not suit |
| `ARMOR_CONDITION_*` (714-786) and `WEAPON_CONDITION_*` (686-708) | 6 materials, 6-7 tiers each | Wear descriptions by material (StdArmor.java:128-148; StdWeapon.java:366-369) |
| `WEATHER_*` (833-1041) and `WEATHER_ENDS` (792) | 14 weather types | Weather text: a climate prefix (normal, wet, cold, hot, dry) and a wind suffix (windy or not) (DefaultClimate.java:305, 518-540). Example: "Fluffy cloudbanks" + "move across the sky." |
| `TOD_CHANGE_OUTSIDE` (668) / `TOD_CHANGE_INSIDE` (676) | 4 phases each | Dawn, day, dusk and night messages (DefaultTimeClock.java:762-768) |
| `GAIT_LIST` (1046) | 18 gaits | Gait verbs: present participle, arrival and departure (Gait.java:125) |
| `GENDERS` (896) | 3 (M, F, N) | Pronouns and titles; `N-` is excluded from character creation (MobData.java:1366; PlayerData.java:628) |
| `PERSONALITY_TRAITS` (2003) | 6 alignment groups, 228 traits | Chat personality words (MudChat.java:937) |
| `MAGIC_WORDS` (1066) | 699 invented words | Secret words for wands and staves, chosen by hash (StdWand.java:45, 142) |
| `ANIMAL_ORDER_LIST` (662) | 27 commands | Words an animal follower obeys (StdLanguage.java:598) |
| `ISO_LANG_CODES` (1850) | 136 codes | Language names (MUD.java:468) |
| `TECH_LEVEL_NAMES` (1767) | 79 | Tech levels: Mk.I to Mk.XX, Ver2.0 to Ver9.9, Mach.I to Mach.XX, Gen.1 to Gen.20 |
| `TECH_BABBLE_*` (1800-1818) | 30 verbs, 9 + 34 adjectives, 36 nouns | Generated technical jargon for sci-fi equipment |
| `TECH_SECTOR_X/Y/Z_NAMES` (1821-1848) | 88, 88, 72 | Generated sector designations: constellations, Greek-letter pairs, and "Inferior-I" to "Superior-VIII" |
| `EXPERIENCE_AMOUNTS` (1988) | 13 | XP gain words (CoffeeLevels.java:875) |
| `EXP_CHART` (560) | 100 | XP thresholds (CoffeeLevels.java:1156) |
| `REALESTATE_MARKERS` (551) | 4 | Room text for empty, for-sale and for-rent lots (Prop_RoomForSale.java:125; MUDLaw.java:792) |
| `LMT_STATS` (2050) | 33 | Stat names allowed for the STAT command (Stat.java:1402) |
| `CHARSTAT_CHART` (536) | about 34 | Stat descriptors from "disabled" to "ungodly" in tiers of three |
| `SKILL_PROFICIENCY_DESC` (812), `WIZ_NOAUTOINVOKE` (1794), `RACIAL_CATEGORY_IS_*` (1774-1792) | 14, 20, 7 | Proficiency words; abilities that must not auto-invoke; racial categories (consumers for the first two not traced) |

### 9. Moving rooms (`movingroom.xml`)

`MovingRoom.loadInfo` (MovingRoom.java:126) reads `movingroom.xml`. The file's instructions give the behaviour's parameters as `min=3 max=3 chance=100;[XML section];[time at stops];[time between stops]` (movingroom.xml:5-8). Each route has a short name, a line name, a flag for whether the mover appears on the map, a list of stops (room ids such as `Subway System#0`), and messages for each direction of travel, with separate text for people inside the mover and for people in the room at a stop. The subway route has five stops, travels west and east, opens its doors north and south (movingroom.xml:77-134), and uses the `$disproom` (next stop), `$inopendir`, `$outopendir` and `$traveldir` tokens. `fixOutputString` substitutes them (MovingRoom.java:197-251). The message `$traveldirbound` in the file works only because `$traveldir` is matched as a prefix (MovingRoom.java:206-229); this is not documented.

### 10. Titles (`titles.ini`)

Each rule is `Title=(max:)mask` (titles.ini:1-22). Braces make a title mandatory (the player cannot change it), `*` stands for the player's name, and the mask is a zapper mask. A holder who stops matching loses the title, and when `max` holders are exceeded the most idle one loses it. `AutoAwards.evaluateAutoTitle` (AutoAwards.java:76) parses each line. The header says titles are refreshed every mud hour (titles.ini:20); I did not verify the schedule. The shipped copy has no active rules.

### 11. Text pipeline (`parser_en_TX.properties`, `translation_en_TX.properties`, filters)

**Input.** `DirtyLanguage.getLanguageParser` (DirtyLanguage.java:556-568) loads the parser file. It builds the path with upper-case `EN_TX`, while the file is `parser_en_TX.properties`. `CMFile` resolves the mismatch by matching each path component without regard to case (CMFile.java:147-170), so the file is found on Linux too. The rules are ordered regular-expression rewrites over tab-separated words:

- Abbreviations: `^(?>ge|g)\t(.+)` becomes `get`, `^(?>loo|lo|l)\t(.+)` becomes `look`, and so on (parser_en_TX.properties:120-146).
- Article stripping before objects (`the`, `a`, `an`, `that`).
- Synonym normalisation: `put X inside Y` and `put X in Y` both become `put X from Y`; `look in`, `look at` and `look inside` become `look`.
- Multi-item splitting: `get a, b and c from chest` becomes three commands separated by newlines.
- Exit normalisation: `open north` and similar.

`translation_en_TX.properties` has section headers and no rules, so output translation is a no-op (translation_en_TX.properties:96-101).

**Filters.** `CMProps.applyINIFilter` (CMProps.java:3403-3470) masks whole words in player text. It is used by `SAYFILTER` (Say.java:328, Tell.java:155, Whisper.java:88, GTell.java:60), `EMOTEFILTER` (Emote.java:100), `POSEFILTER` (Pose.java:88) and `CHANNELFILTER` (CMChannels.java:972). A masked word has each non-space character replaced from the sequence `%#@*!$&?` by position. The shipped `CHANNELFILTER` (coffeemud.ini:1882) is a profanity and slur list, not reproduced here.

### 12. Web, mail, CM1 and channel infrastructure

These settings matter only for a networked server.

- `RUNWEBSERVERS=pub,admin` (coffeemud.ini:66; MUD.java:1562) starts two servers. Each reads `web/common.ini`, then its own file, so a later key overrides an earlier one (MUD.java:1063-1068).
- `pub.ini` listens on ports 27744 and 80 and maps `/` to `/web/pub/` (pub.ini:8, 62). `admin.ini` listens on 27777 (admin.ini:8) and sets `ADMIN=true` (admin.ini:72). It also maps `/resources/progs`, `/resources/books` and `/resources/quests` (admin.ini:63-69). Its request and thread timeouts are 2700 seconds (admin.ini:21-34).
- `web/common.ini` sets the request body limit (2 MB), the thread pool (1 core, 10 max), the chunk size and `DUPPOLICY=ENUMERATE` (read at CWConfig.java:1390-1415 and 1505).
- `email.ini` runs an SMTP server on port 25 (email.ini:8). Personal mail is kept for 60 days (`EMAILDAYS`, email.ini:51) and delivery is retried for 5 days (`FAILUREDAYS`, email.ini:43; MassMailer.java:78-89). Journals can forward to subscribers.
- `cm1.ini` sets the CM1 port to 27733 and an idle timeout of 10 minutes (CM1Server.java:144).
- `RUNI3SERVER=TRUE` with `I3PORT=27766` (coffeemud.ini:166-169); Grapevine is off (181). Discord, Twitter and the LangChain4J LLM settings are blank (coffeemud.ini:2193-2233). The LLM hook is wired in `CMProtocols.java` around line 4789.

## Content worth mining

These items are the most reusable as data. Short samples only.

**Level ladder and XP words.** The 100-step cumulative chart (lists.ini:560-659) rises by 100 per level from 500 and levels off at 5,000 steps. Gain words run from "barely any" through "a small amount of", "an ok amount of", "average", "a good amount of", "a significant sum of", "a whole lot of" and "an incredible amount of" to "an extraordinary amount of" (lists.ini:1988-2001).

**Damage vocabulary.** Each of the 18 damage types has 16 verbs in increasing severity. Examples from the file:

| Damage type | Verbs at the low and middle tiers (paraphrased) |
|---|---|
| Slashing | scratch, graze, wound, cut, slice, gut |
| Piercing | scratch, graze, prick, cut, stab, pierce |
| Bashing | scratch, graze, hit, smash, bash, crush, crunch |
| Burning | warm, heat, singe, burn, flame, scorch, incinerate |
| Frosting | cool, chill, frost, freeze, snow, ice, frostbite |
| Stultifying | tingle, pause, stupefy, numb, paralyze |
| Gassing | puff, mist, vapour, fume, cloud, choke |

The top tier for every type is an all-caps "obliterate" entry. The file's comment says tiers can be added or removed if the thresholds change with them.

**Health and corpse text.** The 11-tier health chart runs from "is hovering on deaths door!" through "is covered in blood", "has a few bloody wounds", "is cut and bruised" and "has a few small bruises" to "is in perfect health". Corpse phrases depend on the cause: slashing corpses are "sliced and diced" or "slashed into pieces"; stultifying corpses are "stiff as a board" or "rigidly dead"; frost corpses are "covered in ice" or "frozen solid".

**Weather composition.** Fourteen weather types each have five climate prefixes and two wind suffixes, so a small table yields many distinct sentences. Examples: "Light wisps of cloud" + "obscure the sky."; "A thick fog" + "descends."; "The sun has set and darkness again covers the world." (night text).

**Gaits.** Eighteen gaits (AMBLE, TIPTOE, SKIP, THUNDER, STOMP, GLIDE, SLIDE, STUMBLE, WANDER, WADDLE, DRAG, DANCE, SAUNTER, DRIFT, MEANDER, STROLL, STRIDE, SASHAY), each with its participle and its arrival and departure verbs (lists.ini:1046-1063).

**Personality traits.** Six groups keyed by alignment: LAWFUL (35 traits), MODERATE (40), CHAOTIC (35), GOOD (40), NEUTRAL (43) and EVIL (35). Examples: "Disciplined", "Organized" and "Traditional" for lawful; "Calculating", "Mercenary" and "Prudent" for neutral; "Cruel", "Vengeful" and "Manipulative" for evil.

**Crime catalogue.** The 46 records in the table above, with their punishments, flags and emote triggers. Their structure is reusable even where the content is not.

**Moving rooms.** The subway and the bus are complete examples of a vehicle with stops, dwell times, directional door messages and in-room announcements (movingroom.xml).

**Names and generators.** The sector designations (constellations, Greek-letter pairs, "Inferior-I" to "Superior-VIII"), the tech levels (Mk, Ver, Mach, Gen) and the babble tables produce plausible technical text with few entries.

**Wand words.** 699 invented words (`AAAA`, `AALACHO`, `ABABALOY` ... `ZAL`) are a ready-made source of nonsense incantations.

## Notable design ideas

- **One predicate language for everything.** Zapper masks (`-LEVEL +>10 -RACE +Elf`) decide victims (`PROTECTED`), trespassers, shop price factors and ignore lists, loot rules (`ITEMLOOTPOLICY ... MASK=`), starting and death rooms, channels and the log-out rule. One small language covers every "who qualifies" question.
- **Formulas as data, with a tiny operator set.** Combat and cost rules are strings using `@x` variables, where `<` is a minimum, `>` a maximum and `?` a random value in a range. Balance can be changed without recompiling.
- **Death as a list of consequences.** `PLAYERDEATH` composes tokens (`UNLEVEL 1, LOSESKILL, OUT (...)`, `PURGE 3`) into a policy, and the same handler serves flee penalties.
- **A level-gap damage curve.** The `1 - min(k,12)^2/120` factor makes low-level damage fall smoothly and reach zero at eleven levels down. The to-hit fudge is signed and squared (`sign(d) * d^2`). These are compact, tunable ways to express a difficulty curve.
- **Fallback key chains.** `START_<RACE>`, `START` and `RACE_X` to `RACECAT_Y` to `RACE_*` search from the most specific key to a catch-all. This is a clean pattern for descriptions and assets alike.
- **Numbers turned into words by tier.** `ARMOR_DESCS` maps a stat to `floor(value / ceiling * N)` and appends `!` marks above the ceiling. The player sees "well armored" rather than a number, and the vocabulary can be edited.
- **Pipes for variety.** Any string can list alternatives after `|`, picked at random on each display (lists.ini:4-13).
- **Hash-stable randomness.** Wand words are picked by item hash, so one wand always uses the same word while different wands differ.
- **Compositional weather.** A climate prefix and a wind suffix give many sentences from few entries.
- **Laws as readable and editable items.** The law book is an in-world item; officers change law by carrying it to a room and editing the page.
- **Routes as data.** A vehicle is a list of stops and message templates, not code.
- **Items expire by category.** Each kind of dropped item has its own lifetime in minutes, so the world clears itself without a garbage pass.
- **Command pre-processing as ordered rewrite rules.** Abbreviations, article stripping and multi-item splitting are plain rules applied before the parser sees a command.

## Problems and cruft

**Defects found by reading (behaviour stated as read; the filter loop was also run in a Python port).**

1. **Word filter abandons its scan early.** In `CMProps.applyINIFilter`, when a filter word first occurs inside a longer word at an index of at least `len(word)-1`, the `else` branch sets `fdex=-1` (CMProps.java:3458), so later whole-word occurrences are never masked. Reproduced in the port: with filter `ASS`, "CLASS ASS" stays unmasked, while "PASS ASS" masks correctly because the first occurrence sits at index 1.
2. **Duplicate test hides the disembark message.** The two `riding()` branches at MUDFight.java:1650 and 1653 test `instanceof MOB`. The second should test `Item`, as the gold-loot branch does at 1687-1691. As written, a killer riding an item is not stopped from looting.
3. **Unit mismatch for expiring items.** The item-copy path in StdRace.java:1048-1049 sets an expiry of `EXPIRE_MONSTER_EQ * MILI_HOUR`, but `Expire.getExpirationMilliseconds` multiplies by `MILI_MINUTE` (ItemPossessor.java:106). With the shipped 30, these copies last 30 hours instead of 30 minutes.
4. **Two dead damage thresholds.** `standardHitWord` maps damage to tier `i`, and `getStandardHitWordInternal` adds one and clamps to the last word (MUDFight.java:1805-1845, 1848-1862). Damage above 495 gets the top word, so the entries 795 and 2147483647 never change the output.
5. **Encoding corruption.** `lists.ini` is UTF-8, but `CHARSETINPUT` defaults to ISO-8859-1 (coffeemud.ini:94-100; CMProps.java:2328). The one non-ASCII value, line 1822, already contains the replacement character (bytes EF BF BD) where "ö" was meant, so the constellation "Boötes" displays as three junk characters.
6. **Expression-language sign convention.** `rollToHit` and its armour helper (MUDFight.java:348-427) flip signs several times, and the armour formula's `>0` and `<` operators make it hard to follow. Hit chances are clamped to 5-95 (Dice.java:63-75).

**Documentation and code disagree.**

7. `EXPRATE` is described as the rate at which experience rises and falls with level difference (coffeemud.ini:401-406). The code uses it only as an aggression gate and similar behaviour checks (Aggressive.java:306). No experience formula reads it.
8. `PLAYERDEATH` says the default is `EXPERIENCE` (coffeemud.ini:360-374), but the shipped value is `(@x1 * 50) < 2000`.
9. `FLEE` says "5 xp per level above killers. Min 10" (coffeemud.ini:392). The code is `10 + 5*(own - foe)` with a minimum of 10 (Flee.java:157). A level-10 character fleeing a level-0 foe loses 60, not 50.
10. The `lawtoc.ini` jail and parole penalties are "in seconds" (lawtoc.ini:85-105); `laws.ini` and the code use ticks (laws.ini:91-101; Arrest.java:3391).
11. `MANACOST` says a positive cost is "adjusted downward as level increases" (coffeemud.ini:870). In the cost function, positive costs are reduced only for surplus multi-class levels (StdAbility.java:1784). No level term appears on that path.
12. The in-code defaults in `CMProps` differ from the shipped values for four formula keys: `FORMULA_NPCHITPOINTS` (CMProps.java:3226 vs coffeemud.ini:2337), `FORMULA_HITPOINTRECOVER` (3227), `FORMULA_MANARECOVER` (3228) and `FORMULA_MOVESRECOVER` (3229). Also `AUCTIONRATES` (2855 vs ini:1863), and `STARTINGITEMS` (2848). They matter only if a key is removed from the file. The documented default for `FORMULA_ATTACKFUDGEBONUS` (ini:2358) is linear, but the shipped and code values are squared.

**Dead or fragile configuration.**

14. `DAMAGE_WORDS_THRESHOLDS` relies on the last entries being unreachable, and on the damage index being shifted by one (see defect 4).
15. Weather lookup uses enum order: `DefaultClimate.java:518-519` computes the list as `WEATHER_CLEAR.ordinal() + weather`. The order of `ListFile` (CMProps.java:546-644) must match the constants in Climate.java:161-187, and the enum comments ask that the weather entries stay last. Short lists also need their trailing empty entries, because `getListFileChoiceFromIndexedList` indexes directly (CMProps.java:2573), so a missing tier throws.
16. The `PERSONALITY_TRAITS` table repeats 53 words across alignment groups (for example "Calm" in LAWFUL at lists.ini:2008 and NEUTRAL at 2037, and "Angry" in CHAOTIC at 2022 and EVIL at 2045). The MODERATE group also contains lawful and chaotic traits (lists.ini:2011-2018). This may be deliberate; it looks like copy-paste.
17. `$traveldirbound` in `movingroom.xml` works only through prefix matching (MovingRoom.java:206-229) and is undocumented.
18. Consumers of `SKILL_PROFICIENCY_DESC`, `WIZ_NOAUTOINVOKE` and `CHARSTAT_CHART` were not traced.

**Typos and text.**

19. `laws.ini:217` has "unnacceptable". `lawtoc.ini` has "Tresspassing" (9), "miscellaenous" (26) and "substanced" (221, 227). `movingroom.xml:150` has "The drive closes the door" and "foreward". `lists.ini:669` has a backtick in "Dawn`s".
20. `lists.ini:669` and `671` say the sun rises in the west and sets in the east. This may be a deliberate inversion; I could not tell.

**Security and operations (for any networked build).**

21. `MPCPKEY` ships with a placeholder value (coffeemud.ini:33). Change it before exposing a server.
22. `I3ROUTERS` (coffeemud.ini:174) lists router hosts with passwords in plain text. SMTP relay credentials can also sit inline in `SMTPSERVERNAME` (coffeemud.ini:72-80). Values are not reproduced here.
23. The admin web server runs with `ADMIN=true` and maps script, book and quest directories (admin.ini:67-72). It must not be reachable from outside.

**Multiplayer-only assumptions** (not worth copying for a single-player game): `PLAYERKILL`, `CORPSEGUARD`, `LOGOUTMASK`, the channels and journals, the mail and forum systems, clans and clan trophies, `COMMONACCOUNTSYSTEM`, `MAXCONNSPERIP`, `MAXNEWPERIP`, `WHITELISTIPSCONN`, `BLACKLISTFILE`, the account trial and expiry settings, `RPAWARDS` (multiplayer social incentives), and the I3, CM1, Discord, Twitter and LLM bridges.

## Takeaways for a Python SUD

- **One typed config object.** Use a frozen dataclass per group (`Clock`, `Combat`, `Xp`, `Death`, `Economy`, `Laws`, `Text`), with defaults and a comment beside each field. Load overrides from a TOML file with `tomllib`. Keeping the default in one place avoids the drift this bucket shows.
- **Calendar as constants.** Adopt the mud calendar in code: `MINUTES_PER_MUD_HOUR = 10`, `HOURS_PER_MUD_DAY = 6`, `DAYS_PER_MUD_MONTH = 24`, `MUD_MONTHS_PER_YEAR = 7`. Drive it from a counter of turns or ticks, not wall time, since real-time versus turn-based is undecided. One turn can be defined as N ticks.
- **XP by table and bisect.** Store the chart as a list. Find the level with `bisect.bisect_right(chart, xp)`, and extend beyond the table with the final step, as `CoffeeLevels` does. Keep the single-killer share cap (`min(pool * L**2 / sum_sq, 100)`) for companion or group kills, and drop the divider machinery if there are no groups.
- **Death as data.** Parse a string such as `"UNLEVEL 1, LOSESKILL, OUT 5"` into a list of small callables. Keep the consequence vocabulary short; `PURGE 1` and a few others are enough for permadeath, unconsciousness and XP loss.
- **Formulas without `eval`.** Write combat formulas as Python functions with named arguments (`attacker_level`, `defender_level`), not `@x1` positions. If designers must edit them, parse a whitelisted expression with `ast` and support only `min`, `max`, arithmetic and a `rand_between` helper. Keep the `<` and `>` semantics explicit in the documentation.
- **Level-gap curve.** Port the level-difference factor as `damage_factor(k) = max(0.0, 1 - min(k, 12)**2 / 120)` for a defender k levels above the attacker. Keep the squared, signed to-hit bonus if it suits the feel.
- **Tiered text.** Store each vocabulary as a tuple of phrases, and select with `phrases[min(int(value / ceiling * len(phrases)), len(phrases) - 1)]`. Add `random.choice` for pipe-style variants. Put the damage verbs and their thresholds in one table that cannot silently drop its last entry.
- **Deterministic item words.** Pick secret words with a stable hash of the item's identity, as `StdWand` does.
- **Crimes as records.** Model each crime as a dataclass with `location`, `flags`, `punishment_ladder`, `message`. Keep the recidivism cap (`punishcap`). For one player, a reputation score and guard reactions may be more useful than jail timers.
- **Routes as data.** A `Route` dataclass holds stops, dwell and travel ticks, and message templates per direction and side. Fill the templates with `str.format_map`, which avoids the prefix-matching trap in `movingroom.xml`.
- **Command rewrites as ordered rules.** Keep `(pattern, replacement)` pairs in a TOML list and apply them in order before parsing. Include abbreviations, article stripping, `put X in Y` to `put X from Y` and multi-item splitting. Test the rules in order, since they interact.
- **Expiry per category.** Use a `dict[Category, timedelta]` for dropped items, with one clock for the game world. Check it in one place, not with per-item timers.
- **Shops as a policy object.** `ShopPolicy(budget_per_period, devalue_pct, restock_ticks, buy_multiplier_by_race, price_factor_by_tag)` captures the economy knobs in one place.
- **Drop the networking.** Web servers, mail, channels, journals, clans, I3, connection limits and account systems are not needed. Replace idle timers with autosave.

## Coverage

- **Read in full:** `coffeemud.ini` (all 2,526 lines); `resources/laws.ini` (234); `resources/lawtoc.ini` (250); `resources/titles.ini` (25); `resources/parser_en_TX.properties` (186); `resources/translation_en_TX.properties` (101); `resources/movingroom.xml` (192, the instructions, the subway and the bus); `web/admin.ini`, `web/pub.ini`, `web/cm1.ini` and `web/common.ini` (292).
- **Read in full apart from a few sections:** `resources/lists.ini` (2,056 lines). Read in full: EXP_CHART, damage, health, corpse, prowess, condition, weather, gait, tech names, personality and experience tables. Sampled only: `MAGIC_WORDS` (lines 1066-1764; counted by script, 699 words; first and last words read) and `ISO_LANG_CODES` (1850-1986; counted by script, 136 codes).
- **Read in part:** `web/email.ini` (112; non-comment lines in full, comments read to line 75). `resources/mxp_images.ini` (712; the header, key-prefix counts across all 639 key lines, and the tail read; the middle entries were not read one by one).
- **Java read in part, targeted at the keys:** CMProps.java (boot and reset, defaults, list and filter code, ListFile enum, PvP and formula parsing); MUDFight.java (rolls, damage, hit words, XP dispensing, death and consequences, loot, health and corpse text); StdAbility.java (mana cost); CoffeeLevels.java (chart and XP words); Flee.java; StdMOB.java (PvP level check); CoffeeUtensils.java (loot policy); DefaultLawSet.java; Arrest.java (sentence timer); StdLawBook.java; MovingRoom.java; AutoAwards.java (title parser); CMProtocols.java (MXP keys); DirtyLanguage.java and CMFile.java (parser loading and case resolution); CMath.java (operators); Dice.java; CMAbleMap.java (proficiency); Injury.java and the thief skills (injury consumers); the aggression behaviours (EXPRATE); DefaultClimate.java and DefaultTimeClock.java (weather and time-of-day text); and the SMTP, web and CM1 readers.
- **Skipped:** `resources/ipblock.ini` (excluded by assignment); the `guides/` documentation (not consulted); the rest of the engine.
- **Not run.** No behaviour was executed in the server. Claims come from reading the code. The one exception is the word-filter defect, which was reproduced by porting the loop to Python.
