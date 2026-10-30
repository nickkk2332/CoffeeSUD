# English parsing and common command messages

> Part of the CoffeeMud NEWOVERVIEW. Bucket: `b014` (English parsing and common command messages) · 4 files · 8994 lines of Java.

## Summary

`EnglishParser` (4102 lines) turns typed words into game objects. It decides which command or skill the player means, splits off a container phrase ("from bag"), reads counts and occurrence syntax ("get 10 bags", "get 2.sword"), handles "all", "last" and category words such as "armor", parses money, and supplies English grammar helpers (articles, plurals, number words). `CommonMsgs` (3051 lines) is the message layer behind the common verbs: it posts get, drop, give, say and similar actions as real commands or messages, renders look, examine and exit text, and runs the hygiene, aging and mana-upkeep ticks. Together they form the front end of every typed action; the rest of the engine reaches them only through `CMLib.english()` and `CMLib.commands()`. For a single-player text game, the noun-phrase grammar and the look and examine text are the parts most worth reusing.

## Key files and types

| File or class | Role | Lines |
|---|---|---|
| `Libraries/EnglishParser.java` (class at line 44) | Implements `EnglishParsing`: command and skill lookup, noun-phrase matching (`fetch*`), container and count parsing, money, grammar helpers | 4102 |
| `Libraries/CommonMsgs.java` (class at line 43) | Implements `CommonCommands`: `post*` shortcuts, `handleBeingX` message listeners, room, item, mob and exit look text, tick helpers, `<VARIES>` parser | 3051 |
| `Libraries/interfaces/EnglishParsing.java` | Public contract of the parser (about 85 methods) | 1132 |
| `Libraries/interfaces/CommonCommands.java` | Public contract of the message layer; `LookView` enum at 690-708 | 709 |
| `EnglishParser.FetchFlags` (3054-3069) | Parsed "2.", "all" and "last" flags for one search string | (inner) |
| `CommonMsgs.VaryNode` (84-88) | Prefix trie for `<VARIES>` tag names | (inner) |
| `Commands/Get.java` (outside bucket) | Reference consumer of the parser; `get` helper 62-99, `execute` 102-238 | (cross-ref) |
| `Locales/StdRoom.java` 2612-2830 (outside) | Room-side search wrappers: `fetchFromRoomFavorItems`, `fetchFromMOBRoom` | (cross-ref) |
| `MOBS/StdMOB.java` 2240-2280, 4616-4653 (outside) | `doCommand` dispatch; inventory search | (cross-ref) |
| `Locales/interfaces/Room.java` 242-310 (outside) | `VariationCode` enum behind `<VARIES>` tags | (cross-ref) |
| `Abilities/Spells/Spell.java:77` (outside) | Spell trigger words `CAST`, `CA`, `C` | (cross-ref) |

## How it works

### 1. The token list

Command input arrives as a mutable `List<String>` of words. The command removes the verb itself (`Commands/Get.java:112`). Each parser then deletes the words it consumes, so later stages see only what is left. This is the central control pattern of the bucket:

- `parsePossibleContainers` cuts "from bag" off the end (`EnglishParser.java:2691-2694`).
- `parseMaxToGive` replaces a leading count with "all" and removes bundle words (`2852-2855`).
- `getSkillToInvoke` removes the invocation word and the skill name (`1223-1302`).

The pattern is compact, but callers must know their list changes. `findCommand` passes a copy to `getSkillToInvoke` (`1000`), and `preInvokeSkill` copies again (`1310`). The real `invokeSkill` then parses the original list a second time (`MOBS/StdMOB.java`, `doCommand(Object ...)`). A first character that is not a letter or digit is split off as its own word (`EnglishParser.java:986-991`), so `'hello` becomes `'` and `hello`.

### 2. Resolving the command word (`findCommand`, 976-1111)

Passes run in this order and the first hit wins.

| Step | Test | Lines |
|---|---|---|
| 1 | Exact command trigger (`CMClass.findCommandByTrigger(word, true)`), with security and disable checks | 994-998 |
| 2 | Exact skill trigger (`getSkillToInvoke`) | 1000-1003 |
| 3 | Word is a skill family word (for example CAST) but no skill matched: return null | 1005-1006 |
| 4 | Exact social | 1008-1010 |
| 5 | Exact channel, `NO`+channel, or command journal | 1012-1039 |
| 6 | Inexact skill: any trigger that starts with the word | 1042-1064 |
| 7 | Inexact command trigger, after skills so that abbreviations such as CA and PR resolve to skills first (comment at 1065-1066) | 1067-1071 |
| 8 | Inexact social; rewrites `commands[0]` to the social's base name | 1073-1078 |
| 9 | Inexact channel and journal | 1080-1109 |
| 10 | Fallback: `CMLib.leveler().deferCommandCheck(mob, null, commands)` | 1110 |

`StdMOB.doCommand` (2240-2248) resolves the word. The protected overload (2251-2280) then runs a `Command`, invokes a `Social`, or calls `invokeSkill` for an `Ability`. If nothing is found it calls `handleUnknownCommand` (`CommonMsgs.java:91-117`). That prints "You don't know how to @x1 that." when the word is a skill family word, and "Huh?" otherwise.

### 3. Skill words (`getSkillToInvoke`, 1177-1305)

- **Candidates.** The mob's abilities whose trigger list contains the first word (1183-1204). Several abilities with the same ID keep the one with higher proficiency (1197-1199). Different IDs make the word ambiguous.
- **Family forcing.** Spell, song, prayer and chant abilities are forced into the ambiguous path whenever arguments follow (1206-1221). So "fireball bob" is not a spell use, and "cast fireball bob" is. The spell family triggers are `CAST`, `CA` and `C` (`Abilities/Spells/Spell.java:77`).
- **Disambiguation.** For an ambiguous word, the second word is matched against skill names: exact name first (1239-1242), then prefix, then two-word names (1266-1284). If nothing matches, an ability whose name contains " WORD" is accepted (1288-1300).
- **Trace.** "cast fireball bob" with many spells known. The first loop sees several IDs and sets `foundMoreThanOne` (1183-1204). `CAST` is removed (1230). The second loop finds `Spell_Fireball` by exact name (1239-1242). The `!foundMoreThanOne` branch removes `FIREBALL` (1263-1264), leaving `bob` as the target.
- **Messages.** "You don't know how to do that." (1314, 1333) and "You are not high enough level to do that." (1321, 1340).

### 4. Noun phrases: flags, matching and tiers

**4a. Flags** (`fetchFlags`, 3095-3157). The search string is upper-cased and read as follows.

| Input | Occurrence | All | Last | Name searched |
|---|---|---|---|---|
| `sword` | 0 (any) | no | no | SWORD |
| `2.sword` or `sword.2` | 2 | no | no | SWORD |
| `all` or `all swords` | none | yes | no | ALL or SWORDS |
| `last.sword` or `sword.last` | `Short.MAX_VALUE` | no | yes | SWORD |

Callers rewrite `all.sword` and `sword.all` to `ALL sword` (`fetchItemList` 2033-2042, `Get.java:134-143`, `parsePossibleContainers` 2663-2672). A search of fewer than two characters, or of "the", matches nothing (3100-3101). "ALL" counts as a prefix only when a space or the end follows (3105-3116), so "alloy" is an ordinary name.

**4b. Category words.** When a filter is passed, the words ARMOR, WEAPON, COIN(S), CURRENCY, MONEY, ITEM and ITEMS replace the name with a predicate (`getGeneralItemFilter`, 2564-2613; applied at 3077-3090). A match is either a name word from the word list (for example "armor") or a Java type (`Armor`, `Weapon`, `Coins`, `Item`).

**4c. The text match** (`containsString` and `unsafeContainsString`, 1591-1743). Matching is case-insensitive. Color codes (`^` sequences) and punctuation are skipped on both sides (1634-1711). A leading or trailing `$` anchors the match to the start or end of the name (1616-1620, 1629, 1736-1737). The string "ALL" matches everything (1608-1609). Strings that are equal once punctuation is removed also match (`equalsPunctuationless`, 1535-1570). Attempts start only at word starts (1738-1740). So "sword" finds "sword", "sw" finds "sword", and "ord" does not.

**4d. Tiers** (`fetchEnvironmental` over an `Iterable`, 3175-3244).
1. Exact match on ID, `name()` or `Name()` (3187-3203).
2. Substring match on name or `Name()` (3210-3220).
3. Only if tiers 1 and 2 found nothing, substring match on display text and the mob's generic name (3221-3235).

Occurrence works by counting down. Each match decrements the counter, and the match that reaches zero is returned (3200-3201, 3216-3217). With `all`, objects that have no display text are skipped (3198, 3214). That appears to hide objects that are not placed in the world, but the code gives no reason, so the intent is unconfirmed.

**4e. Container scope.** Item searches take a `goodLocation`, and only items whose `container()` is that object pass (`I.container()==goodLocation`, 3694 and 3724). A null location means loose items. This is the mechanism behind "from bag".

**4f. Mob and room.** A mob's inventory is searched exact first, then inexact (`StdMOB.fetchFromInventory`, 4624-4626). `StdRoom.fetchFromMOBRoom` (2730-2824) works as follows.
- A "room." prefix, or the room's own generic name followed by a dot, forces the room (2733-2736).
- "my " limits the search to the mob's items (2742-2747).
- With favor-items, the mob's unworn inventory is searched first (2748-2758).
- A dot-number exception (2762-2784) lets dot syntax such as "3.sword" reach floor items when the mob already carries some of the same item. The code's comment says it is a workaround, and it recomputes the occurrence to count past the mob's own items.
- The room is searched next, and results the viewer cannot see are skipped by bumping the occurrence (2787-2809).

Room items (`fetchFromRoomFavorItems`, 2612-2652) search contents, then exits, then inhabitants, in that order, and share one counter across the three lists (2619-2632). The counter is reset between the exact and inexact passes (2626).

### 5. Counting identical things

- `getContextNumber` (1780-1799) numbers identical objects, but only among those in the same container. The second sword in the room is "sword.2", and a sword in a bag does not count against it. It returns 0 for the first or only match, the index for the nth match, and -1 for no match.
- `getContextName` (1856-1866) renders "sword" or "sword.N" from that number.
- `getAllContextNames` (1869-1898) gives each item in a list its own name key ("sword", "sword.2" and so on), and an empty string for filtered-out items. It compares Name and container with case-sensitive `equals`, while `getContextNumber` uses `equalsIgnoreCase` (1787). The difference is minor.

### 6. Worked trace: "get 2.sword from bag"

`Commands/Get.java`, `execute` (102-238):

1. The verb is removed, leaving `["2.sword", "from", "bag"]` (112).
2. `parsePossibleContainers(mob, commands, FILTER_ANY, true)` (124, implemented at 2616-2703). The backward scan finds "from" at index 1 (2622-2636) and the container word "bag". The bag must be in the mob's inventory or the room and must have contents (2681-2687). On the first hit, the words from "from" onward are removed (2691-2694), leaving `["2.sword"]`.
3. `parseMaxToGive(mob, commands, containers.size()==0, R, true)` (127, implemented at 2846). With one word the numeric branch is skipped (2849), so the limit is `Integer.MAX_VALUE`.
4. The loop (145-191). The bag belongs to the mob (`mob.isMine`, 158), so the lookup is `fetchFromMOBRoomFavorsItems(mob, bag, "2.sword", UNWORNONLY)` (159). Flags give occurrence 2 and name SWORD (3119-3151). The second sword whose container is the bag is returned (3694, 3704-3705).
5. `get(mob, bag, sword, ...)` (62-99, called at 199). The sword is already carried, so no MSG_REMOVE is sent (74-80). The MSG_GET names the bag as target and the sword as tool (81). Its text comes from `theWhat` ("<O-NAME> from <T-NAME>", line 72), giving "<S-NAME> get(s) <O-NAME> from <T-NAME>." `CommonMsgs.handleBeingGetted` (2649-2719) then clears the sword's container.

Contrast "get 2 swords from bag". The count branch sets `maxToGive=2` and rewrites the first word to "all" (2852-2855). The loop then fetches "all swords" and "all swords.2" (`Get.java:154-190`).

"get 3 gold" never takes the count path. `parseNumPossibleGold` returns non-zero for money words (2182-2236, tested at 2850), so `parsePossibleRoomGold` splits the coin pile instead (`Get.java:163`, 2444-2489).

Bare "get 2 swords" with no container sets `breakPackages` to true (`Get.java:127`). If "swords" names a bundle or pack in the room, the code splits or unbundles it (2856-2939). The carry cap is the mob's maximum carry, capped at 3000 (2873-2875), and above the cap the message is "You can only handle @x1 at a time." (2878).

Failure messages from `Get` (212-236): "You don't see '@x1' in <T-NAME>." (218), "@x1 is closed." (220), "You don't see anything here." (224), "You don't see '@x1' here." (229), "You don't see '@x1' in <T-NAME> here." (232), and "You don't see '@x1' in any '@x2'." (234).

### 7. Containers and counts

- `parsePossibleContainers` (2616-2703) scans from the end for "from" only (2626). After "from" it accepts "all" or a number (2630-2633). It can return several containers.
- `parsePossibleContainer` (2706-2758) is the single-container version used by put, look, examine and sheath. It also accepts "in" and "on" (2719-2721) and can require the container to have contents (2749).
- `parseMaxToGive` (2846-2981) has three cases.
  - A positive leading integer sets the limit and rewrites the word to "all" (2852-2855). With `breakPackages`, it can split a raw-material bundle (2883-2896) or unbundle a pack (2899-2902), and it can take one unit from "N.name from pack" (2943-2976).
  - A non-integer first word with "FROM" in the middle takes one unit of a bundle whose name matches (2942-2976).
  - Otherwise the limit is the maximum.

### 8. Money

- A leading "a pile of " is removed before parsing (2184-2185; the other money parsers do the same).
- Denomination and currency names are matched with `containsString`. Plural forms "(s)" and "(ys)" are expanded (`matchAnyCurrencySet` 2332-2366; `matchAnyDenomination` 2369-2441).
- `parseBestPossibleGold` (2492-2562) subtracts the amount from the purse. If the total fell by more than the amount paid, it subtracts the excess (2537-2544), then returns a new coin object. Refusal: "You don't have that much @x1." (2553).
- `parsePossibleRoomGold` (2444-2489) splits a floor pile when the requested amount is smaller than the pile, creating a new `StdCoins` in the same container.

### 9. Grammar helpers

- **Lists.** `toEnglishStringList` (93-164) joins with commas and an Oxford comma. Two items get only "and".
- **Numbers.** `makeNumberWords` (214-267) produces "one thousand two hundred and thirty-four" style text, with "point" for decimals. `makeNumberthWords` (200-211) produces "first", "twenty-first" and so on. `num_words` (72-77), `num_words_10` (78-81) and `num_words_x` (82-85) hold the vocabulary. `EnglishParser.java:216` notes that the words are not localised.
- **Plurals.** `makePlural` (334-389) applies these rules in order.
  1. An explicit "(s)", "(es)", "(ses)" or "(ys)" template (339-346).
  2. "X of Y" pluralises X (351-359).
  3. The uncountable list `fnouns` (362-363).
  4. The irregular table `feewords1` to `feewords2` (364-366).
  5. -is becomes -es (367-368).
  6. -s, -z, -x, -ch and -sh take -es (376-377).
  7. Consonant plus y becomes -ies (380-381).
  8. -fe becomes -ves, and the -f list does the same (382-385).
  9. -o takes -es (386-387). Anything else takes -s.
- **Singular.** `makeSingular` (392-412) reverses the common cases.
- **Articles.** `properIndefiniteArticle` (427-515) chooses "a" or "an" from the first letter. A vowel letter gives "an", including u (496-506), so "unicorn" becomes "an unicorn".
- **Past tense.** `makePastTense` (270-295) appends "d" after a final vowel and "ed" otherwise, with no doubling rule.
- **Distances, speeds and times.** `getBestDistance` (517-546) returns the first unit in `SpaceObject.DISTANCES` that fits (the loop at 522-541). `stringifyElapsedTimeOrTicks` (2761-2783) renders "1d 2h 3m 4s 5ms" and adds an average per tick when ticks are given. `getMillisMultiplierByName` (2276-2298) maps unit names such as "ticks", "seconds" and "weeks" by prefix, so "h" means hours.
- **English detection.** `probabilityOfBeingEnglish` (2984-3052) returns 100 for any string under 100 characters (2986-2987). Otherwise it returns 0 if more than 20% of characters are punctuation (3030-3032) or the average word length falls outside 2 to 8 (3033-3035). Otherwise it computes chi-squared, equal to 10 times ((average length minus 4.7) squared, divided by 4.7) plus the sum over 26 letters of (observed minus expected) squared, divided by expected and by 26 (3036-3045). The result is round(100 minus chi-squared), clamped to 0 through 100 (3046-3051).

### 10. Look, examine and room text

**Look modes.** `LookView` (`CommonCommands.java:690-708`) has LOOK_LONG (examine), LOOK_NORMAL, LOOK_BRIEFOK and LOOK_MINIMAL. `handleBeingRoomLookedAt` (2098-2139) picks LONG for examine, BRIEFOK when there is no source message or the player has the compress attribute, and NORMAL otherwise (2106-2108).

**Room view** (`getFullRoomView`, 1842-2096).
- Sysop attributes add the room ID, domain, climate words and weight (1860-1889).
- The room name and disposition blurbs come first (1890-1893), then the description. LOOK_LONG wraps any description word that matches a floor item's name in a clickable tag (1898-1944).
- The exits paragraph is appended in some modes (1946-1948).
- Floor items are grouped and compressed (1967-2026). Items hidden in plain sight are skipped, unless the viewer can see hidden things or the look is LONG (2001-2004).
- Other inhabitants are listed when the viewer can see them, and shopkeepers get their own tag (2043-2060). Inhabitants that are only barely visible are counted for the darkness lines (2073-2077).
- Darkness lines: "There is something here, but it's too dark to make out." (2084), "The darkness conceals several others." (2086) and "You are not alone, but it's too dark to tell." (2089).
- While rendering, items with a missing or wrong owner are repaired and logged (1975-1995). See Problems.

**Exits paragraph** (`getRoomExitsParagraph`, 2141-2193). Adjacent rooms with the same display title are merged into "<Title> continues <direction>" (2157-2178). The remaining exits are phrased in one of two styles, chosen by a 50/50 roll (2179): "north is a door" or "a door is north" (`getExitFragment`, 2195-2208).

**Exit look** (`handleBeingExitLookedAt`, 2303-2405). The exit's own description is shown, with "@x1" replaced by the view text (2336-2337). If the exit has a clear view, the code walks up to five rooms in that direction (2354-2362), or the whole grid parent (2348-2349). It collects items and inhabitants and prints "Yonder, you can also see: @x1" (2383-2385). `isAClearExitView` (2277-2301) allows the view only for open-air and underwater domains, and not in a blizzard or dust storm.

**Examine** (`getExamineItemString`, 1098-1480). The text is scaled by the viewer's INT.
- **Level.** Exact at INT 18 or more, bucketed in 5s for INT 10 to 17, and bucketed in 10s below 10 (1102-1114).
- **Weight.** The bucket width is 4 under 10, 10 under 50, 20 under 150, and 100 otherwise. Below INT 18 the width is halved, and below INT 10 the full width is used (1117-1140).
- **Containers.** A capacity estimate with random error sized `5 * (18 - INT)` (1204-1208). See Problems.
- **Drinks and food.** The unit noun is the first unit above the sip or bite size, from the thresholds 1, 50, 250, 500, 1000 and 5000. Drink nouns used are swallow, quaff, swig, gulp and guzzle. Food nouns used are nibble, bite, morsel, mouthful and gorging (1246-1274; 1346-1372). "Sip" and "crumb" cannot occur, because the size is at least 1 (1256, 1354-1362). See Problems item 16. Size words tiny, small, average, large, huge and humongous come from a ratio (1276-1301; 1374-1399).
- **Weapons and armor.** Hand use and weapon class (1414-1419), wear locations and the layer hint (1440-1476). The layer hint appears only when the mob's level is at least the item's level plus 100 divided by INT (1466).

**Mob look** (`handleBeingMobLookedAt`, 2407-2560). The race and class sentence comes first (2431-2463). A long look at INT above 5 lists body parts (2466-2514). A long look at INT above 12 compares each stat with the race average (2515-2527, using `relativeCharStatTest` at 1051-1062). After a long look the "Consider" command runs on the target (2547-2558).

**Item look** (`handleBeingItemLookedAt`, 1550-1700). The description comes first. Examine text is added when the message is TYP_EXAMINE and the item is not wallpaper (1632-1634). Container contents are listed (1635-1696). The liquid in an open container is shown through a temporary resource item (1648-1665).

**Exit list** (`lookAtExits`, 2821-2892, and `lookAtExitsShort`, 2895-2933). The long form prints "Obvious exits:" and one line per direction, colored by whether the player has visited the destination (2855-2860). The short form prints "[Exits: ...]" (2908-2932). Fog stops both: "It is too foggy." (2836, 2904).

### 11. Command plumbing and messages

- **Forced commands.** `forceStandardCommand` (212-231) and `forceInternalCommand` (197-210) run any `Command` as if typed, with the forced metaflag. The `post*` shortcuts are one-line wrappers, for example `postGet` (392-397), `postDrop` (374-377) and `postWear` (414-417). NPC and skill code uses them. `postLook`, `postFlee`, `postSheath`, `postDraw`, `postStand`, `postSleep` and `postFollow` (420-497) follow the same pattern with fixed argument lists.
- **Failure messages.** `postCommandFail` and `postCommandRejection` (137-178) send MSG_COMMANDFAIL and MSG_COMMANDREJECT messages through the room, so listeners see failures.
- **Message listeners.** Each action has a `handleBeingX(CMMsg)` listener.
  - Get (2649-2719), Drop (2722-2751), Remove (2754-2768), Wear (2771-2788), Wield (2791-2803), Hold (2806-2818).
  - Read (2579-2646). Text is sent as a trailer message ("It says '@x1'."). Text beginning with the file prefix loads a resource file. Empty text gives "There is nothing written on @x1." (2638-2641).
  - Give (2563-2576). Implemented as a drop by the giver and a get by the receiver, each sent as an intermediate message.
  - Sit, sleep and stand (782-818) change posture bits and print the message.
  - Recall (820-875) prints "<S-NAME> appears out of the Java Plane." (824).
  - `handleBeingLookedAt` (1065-1077) and `handleBeingSniffed` (730-740) dispatch by target type.
- **Speech.** `postSay` (640-678) applies the SAYFILTER INI filter (643) and sends MSG_SPEAK to the room, with a private form for targeted speech. `postTell` (511-637) routes "name@mud" to intermud (515-522), sends "You tell ..." and the target's copy, rings a bell for AFK targets (530), and records reply and tell-stack data (613-633).

### 12. Ticks and hygiene

**Mana upkeep** (`tickManaConsumption`, 878-949). After MANACONSUMETIME ticks (880-885) the code collects active sustained effects that cost mana: non-automatic spells, prayers, songs and chants (884-903). The base price is MANACONSUMEAMT. The special values -100 (the mob's level) and -200 (the average lowest qualifying level) replace it (908-926). The upkeep charge is basePrice times (count plus 1) (934). If mana is short, the message "<S-YOUPOSS> strength of will begins to crumble." is shown, a random sustained effect is removed, and the charge is recomputed as basePrice times count (937-943). The code comment calls this "about 3 cost = 1 regen".

**Aging** (`tickAging`, 952-1049). The age in minutes grows by elapsed milliseconds divided by 60000 (956-958). Birthdays come from the game calendar (971-977). Each birthday adds a year to the age stat and, for players older than 10 minutes, prints "Happy Birthday!" (988). Old characters roll for diseases. Disease_Cancer needs venerable age and two 1% rolls (1006-1013). Disease_Arthritis and Disease_Alzheimers need ancient age, each with a 1% roll (1016-1032). If no disease branch fired, a 10% roll checks each maximum stat (1034-1046). If the base maximum plus the current stat is 0 or less, the mob gets "Your max @x1 has fallen below 1!" and dies (1041-1042).

**Hygiene.** `isHygienicMessage` (2211-2224) counts movement by non-skill tools, and the BATHE and WASH socials, for players only. `handleHygienicMessage` (2227-2275) lowers hygiene and prints a cleaning message. A tattoo of value 20 stops the "can't get any cleaner" message from repeating (2249-2250, 2266-2268; the unit is unchecked). Smell is driven by hygiene buckets (742-767).

**Room smells** (`handleBeingRoomSniffed`, 1767-1839). The room's domain and, for some domains, the season pick one message.

### 13. The `<VARIES>` room-text language

A description starting with `<VARIES>` is parsed by `parseVaries` (3045-3050). Tags come from `Room.VariationCode` (`Locales/interfaces/Room.java` 242-310):

- S: season (SUMMER, SPRING, WINTER, FALL).
- C: time of day (DAY, DAYTIME, NIGHT, NIGHTTIME, DAWN, DUSK).
- W: weather (about twenty names, including RAIN, SNOW, FOG and BLIZZARD).
- M: posture or mode (SWIMMING, FLYING, CRAWLING, SITTING, FALLING, CLIMBING, INVISIBLE, HIDDEN).
- V: visited.
- F: arrived-from direction (FROMNORTH and the other directions, and the diagonals).
- X: a Zapper mask in `<MASK ...>`, checked by `CMLib.masking().maskCheck` (2977).
- `<ELSE>` inverts the current condition (2992-2994). `<VARIES>` resets it (2995-2997).

Tags are recognised by walking a prefix trie of the names (56-88 to build it, 2944-2954 to read it). Nested blocks recurse (3027). A tag is recognised only when the walk reaches a coded name, so plain `<` characters in prose pass through. See Problems for tag shadowing.

## Content worth mining

### Command and parse messages

| Message | Where |
|---|---|
| "Huh?" | CommonMsgs 99, 108, 111 |
| "You don't know how to @x1 that." | CommonMsgs 106 |
| "You don't know how to do that." | EnglishParser 1314, 1333 |
| "You are not high enough level to do that." | EnglishParser 1321, 1340 |
| "You don't see anyone called '@x1' here buying or selling." | EnglishParser 1984-1985 |
| "You don't have that much @x1." | EnglishParser 2553 |
| "You can only handle @x1 at a time." | EnglishParser 2878 |
| "You can't see that!" | CommonMsgs 1556, 2644 |
| "You can't see that way!" | CommonMsgs 2404 |
| "You can't see anything!" | CommonMsgs 2115, 2830 |
| "You don't see anything special." | CommonMsgs 2393 |

### Quantity vocabulary (drinks and food)

These words are chosen in `CommonMsgs.getExamineItemString` (not in `EnglishParser`). The noun is the first unit above the sip or bite size:

| Size (sip or bite) | Drink noun | Food noun |
|---|---|---|
| 1 to 49 | swallow | nibble |
| 50 to 249 | quaff | bite |
| 250 to 499 | swig | morsel |
| 500 to 999 | gulp | mouthful |
| 1000 to 4999 | guzzle | gorging |
| 5000 or more | no entry (overflow, Problems item 16) | gorging (default, 1354-1362) |

The drink table is at 1246-1264 and the food table at 1346-1364. Adjectives come from the ratio of the unit to the sip, from tiny to humongous (1276-1301; 1374-1399).

### Hygiene and smell

- Cleaning messages: "You feel a little cleaner, but are still very dirty." (2237), "You feel a little cleaner; almost perfect." (2240), "You feel a little cleaner." (2242), "You are already perfectly clean." (2250), "You can't get any cleaner here." (2269).
- Smell of a dirty player, by bucket (754-765): "has a slight aroma about …", "smells pretty sweaty.", "stinks pretty bad.", "smells most foul.", "reeks of noxious odors."

### Room smells by domain (1777-1834)

- Open water and wet outdoors: "It smells very WET here." (1777).
- Seaport outdoors: clean, wet and breezy (1780). Indoor seaport: humid and fishy (1783).
- Caves: dank and mildewy (1787).
- Woods, hills and plains: season-dependent faint or rich smells (1795-1812).
- Jungle: "a rich floral and plant aroma" (1817). Mountains and rocks: musty and rocky, with a spring variant (1826-1829). Swamp: stinky and gassy (1834).

### Stat and body text

- Relative stat phrases (2520-2525): weaker or stronger; clumsier or more nimble; more sickly or healthier; more repulsive or more attractive; more naive or wiser; dumber or smarter.
- Body parts (2469-2512) include "no legs", "no arms", "no head", gills, antennae, tails and wings.

### Word lists

- Irregular plurals (`EnglishParser.java:70-71`): foot/feet, goose/geese, louse/lice, dormouse/dormice, man/men, mouse/mice, tooth/teeth, woman/women, ox/oxen, child/children, brother/brethren.
- -f and -fe plurals (67-68): calf/calves, half/halves, knife/knives, life/lives, wife/wives, elf/elves, self/selves, shelf/shelves, leaf/leaves, sheaf/sheaves, thief/thieves, loaf/loaves, wolf/wolves, plus the -fe rule.
- Invariant nouns (69): bison, buffalo, deer, fish, moose, pike, salmon, sheep, shrimp, squid, trout, ore, and a few more.
- Articles (64): a, an, all of, some one, a pair of, a pile of, one of, all, the, some, each.
- Prepositions (52-61): about seventy words, from about through to within without.
- Number words (72-85): zero to nineteen, the tens, and the scale words thousand, million, billion, trillion, quadrillion, quintillion and sextillion.

### Money

Denomination names come from the currency definitions, which are outside this bucket. Matching uses the same fuzzy name test as items, and the "A PILE OF" prefix is stripped (2184).

## Notable design ideas

1. **Numbering inside the container.** Identical objects are counted only within their own container (1780-1799), so the sword in the room and the sword in the bag keep separate counts.
2. **One selector grammar.** `2.sword`, `sword.2`, `last.sword`, `all sword` and bare `all` (3097-3157 and 1608) are all selectors over one match list.
3. **Consume-as-you-parse.** Each stage handles its own words and leaves the rest (section 1). It is terse and easy to extend, at the cost of mutable state.
4. **Two-phase actions.** A veto check (`okMessage`) runs before anything changes, then `send` applies the action (`Get.java:76-82` and `96`). Any listener can stop an action before it happens.
5. **Perception as a stat.** The same object reads as a range for a low-INT viewer (1102-1140). Knowledge is a stat rather than a separate system.
6. **Quantity vocabulary.** "swallow", "quaff", "gulp" and the rest are chosen by size (1246-1274; 1346-1372), so the text scales with the amount.
7. **Conditional room prose.** `<VARIES>` blocks switch text on season, time, weather, posture, visited state and direction of arrival (2935-3050).
8. **Clickable room words.** In a long look, words in the room description that match floor items become links (1898-1944).
9. **Exits as prose.** Merged "continues" for similar adjacent rooms (2157-2178) and random phrasing (2179).
10. **Sustain costs.** Spells and songs drain mana on a schedule, and when mana runs out one effect collapses at a time (878-949).
11. **Real-minute aging.** Age accrues in minutes, and birthdays follow the game calendar (952-1003).
12. **Smell as a stat.** Hygiene buckets drive what others notice (742-767), and room smells depend on domain and season (1767-1839).
13. **Language detection.** The chi-squared letter-frequency test (2984-3052) answers "is this text English?", which could drive an in-world reading or cipher mechanic.

## Problems and cruft

All items come from reading the code and were not executed. The list runs roughly from most to least serious.

1. **The shared occurrence counter is wrong on a miss.** `fetchAvailable(..., int[] counterSlap)` (3850-3974) is meant to carry a count from one list to the next. On a match it returns early (3882, 3897, 3918, 3928, 3946, 3958), so the counter is not updated. On a miss it adds `occurrance - myOccurrance` (3972). That value already includes the old counter, so the new value is `2c + k` rather than `c + k`. The interface javadoc (`EnglishParsing.java:797-800`) says the counter is updated when a match is found, which is the reverse of the code. `StdRoom.fetchFromRoomFavorItems` (2619-2632) relies on this across contents, exits and inhabitants. Example: one matching door in contents, one in exits, and two inhabitant doors. "4.door" should return the second inhabitant (the fourth match), but returns the first (the third).
2. **Wrong ID in the counter path.** Line 3876 compares `ID()`, which is the library's own ID ("EnglishParser"), instead of `I.ID()`. The exact ID test in this path therefore never matches an item. Compare line 3697, which is correct.
3. **Stale `lastO`.** The non-item branch assigns `lastO = I` (3899, 3930, 3960), so "last" can return the wrong object.
4. **Color-code skip reads the wrong buffer.** Line 1691 tests `toSrchC[x]` while indexing with the search string's position. It can throw when the target is shorter. About a dozen `catch (IndexOutOfBoundsException)` blocks (for example 3238, 3384 and 3967) would hide such an error and return null.
5. **A target word can be swallowed.** In `getSkillToInvoke`, a second partial match sets `foundMoreThanOne` (1246-1249) while the second-word search is running. If an exact name then wins, the branch at 1266-1284 removes two words (1281-1282) even though one word chose the skill. Example by trace, with spells named "Fireball Storm", "Fireball Wave" and "Fireball", in that iteration order, and the input "cast fireball bob": the exact "Fireball" is chosen, and the branch removes "fireball" and "bob", so the target is lost. The trace is by reading only.
6. **Inconsistent search tiers.** The `Iterable` version of `fetchEnvironmental` searches display text only after names fail (3221-3235). The `Iterator` and `Enumeration` versions search display text in the same pass as names (3437, 3499). Result order therefore depends on the collection type.
7. **Plural results differ from singular.** `fetchEnvironmental` returns the nth match (3200-3201). `fetchEnvironmentals` (3551-3552, 3567-3568) and `fetchAvailableItems` (3798-3799, 3817-3818) return the nth match and every later one, because there is no break. Callers that expect one item get several.
8. **Null filter crashes.** `fetchAvailableItem` and `fetchAvailableItems` call `flags.filter.passesFilter` (3695, 3723) without a null check. `fetchFlags(srchStr, filter)` stores whatever filter it receives (3076), so a null filter stays null. The javadoc says the filter is required, but nothing enforces it.
9. **`removeArticleLead` lowercases the rest.** Lines 761 and 765 return lowercase text after removing an article, so "The Sword of Doom" becomes "sword of doom". The interface (`EnglishParsing.java:77-81`) says color codes are "totally respected".
10. **Unreachable article entries.** "a pair of" and "a pile of" in `ARTICLES` (64) come after "a", so the loop at 762-766 always stops at "a".
11. **Dead code.** The `tried` set in `findCommand` (1045, 1051) is never filled. The second `containsString` test in `matchAnyDenomination` (2400-2402) repeats the first (2397-2399), so it can never be reached.
12. **Heuristic grammar.** "a unicorn" becomes "an unicorn" (496-506). `makePastTense` gives "droped" (288-294). "fourty" is misspelled (79). The plural rules are English-only.
13. **Short text counts as English.** `probabilityOfBeingEnglish` returns 100 for any string under 100 characters (2986-2987).
14. **Lazy tables read directly.** `isPunctuation` (1424-1429) and `isEnglishPunctuation` (1432-1437) index the static tables without calling their lazy initialisers. An early direct call throws a NullPointerException.
15. **Unchecked cast in the liquid look.** Line 1331 casts any `LiquidHolder` inside a container to `Drink`. The drink branch above it does check (1236).
16. **Drink table overflow.** In the drink branch, `drNDex` defaults to `namts.length` (1256), which is 6, but the table has entries 0 to 5. When the sip size reaches 5000 or more, line 1275 reads `namts[6]` and throws. Depends on the `thirstQuenched` values in the data, which were not checked.
17. **Wrong label.** `getFollowAddendum` prints "Ride  :" in its else branch (1543), where "Follow:" was clearly meant.
18. **Liquid text uses the library name.** Line 1660 passes `name`, which is the library's own name field (`Libraries/StdLibrary.java:33`, value "CommonMsgs"), not the liquid's name. The text reads "some CommonMsgs sits here."
19. **Capacity fuzz looks mis-scaled.** `error = 5 * (18 - INT)` (1204) is multiplied by the capacity (1208) and then passed to `plusOrMinus`, which returns a value between minus and plus the range (`Libraries/interfaces/DiceLibrary.java:343`). At INT 17 the error is 5, so the estimate can be off by up to five times the capacity. It may have been meant as a percentage. Unverified.
20. **Parser crash on a trailing `<`.** In `parseVariesCodes` the loop guard checks `aligatorDex` (2945), but the code reads `text.charAt(codeDex)` (2947). Text that ends in `<` or a partial tag throws `StringIndexOutOfBoundsException`.
21. **Shadowed `<VARIES>` tags.** The trie stops at the first node that has a code (2951-2952). So FALLING is shadowed by FALL, DAYTIME by DAY, NIGHTTIME by NIGHT, FROMNORTHEAST and FROMNORTHWEST by FROMNORTH, and FROMSOUTHEAST and FROMSOUTHWEST by FROMSOUTH. A closing tag such as `</FALLING>` then never matches. A search of `resources/` found none of the long forms; the shipped data uses only MASK tags.
22. **Viewing mutates the world.** `getFullRoomView` repairs item owners while it renders (1975-1995). A look should not change state.
23. **Unverified.** `monitorGlobalMessage` removes entries from the list it is iterating (234-245). Whether that fails depends on how `SLinkedList`'s iterator behaves (`core/collections/SLinkedList.java:427-430`).
24. **Multiplayer assumptions.** Tells and intermud (511-522), AFK bells (530), account-wide tell notices (571-588), channels (301-371) and roleplay experience for introductions and speech (1713-1765) all assume other people are present.

## Takeaways for a Python SUD

1. **Port the noun-phrase grammar as data plus functions, not as a class hierarchy.** Parse the phrase once into a value, then select from a list. Python 3.10 or later is assumed for the `X | None` annotation. The prefix form `last.sword` is left out of this sketch.

```python
import re
from dataclasses import dataclass

@dataclass(frozen=True)
class Query:
    name: str = ""       # "" means any noun, as with a bare "all"
    nth: int = 0         # 0 = unspecified; "2.sword" and "sword.2" give 2
    take_all: bool = False
    last: bool = False

_PREFIX = re.compile(r"^(\d+)\.(.+)$")       # 2.sword
_SUFFIX = re.compile(r"^(.+)\.(\d+|last)$")  # sword.2, sword.last

def parse_query(raw: str) -> Query | None:
    s = raw.strip().lower()
    if len(s) < 2 or s == "the":
        return None
    take_all = s == "all" or s.startswith("all ")
    if take_all:
        s = s[3:].strip()
    nth, last = 0, False
    if m := _PREFIX.match(s):
        nth, s = int(m[1]), m[2]
    elif m := _SUFFIX.match(s):
        last = m[2] == "last"
        nth = 0 if last else int(m[2])
        s = m[1]
    return Query(s, nth, take_all, last)

def name_matches(query: str, name: str) -> bool:
    # word-start match: "sw" finds "sword", "ord" does not
    return re.search(r"(?:^|\s)" + re.escape(query), name.lower()) is not None

def select(q: Query, items):
    hits = [i for i in items if name_matches(q.name, i.name)]
    if q.take_all:
        return hits
    if q.last:
        return hits[-1:]
    return hits[q.nth - 1:q.nth] if q.nth else hits[:1]
```

2. **Split containers by returning new lists.** Mirror `parsePossibleContainers` without mutating the input:

```python
def split_container(words: list[str]) -> tuple[list[str], str | None]:
    # EnglishParser.java:2616 scans backward for "from"; here we return (rest, container phrase)
    for i in range(len(words) - 2, 0, -1):
        if words[i] == "from":
            return words[:i], " ".join(words[i + 1:])
    return words, None
```

3. **Gather, then select.** Do not carry a counter across lists (Problems item 1). Collect the candidates from contents, exits and monsters into one ordered list, then call `select` once. This removes the bug class entirely.

4. **Two-phase events.** Model each action as an event object with a `allow(event)` phase that any listener may veto, then an `apply(event)` phase. The `Get` flow (`Get.java:76-96`) is the pattern to copy.

5. **Keep the perception idea.** A `perception` value decides how precisely examine text reports level, weight and capacity. The bucket thresholds at `getExamineItemString` (1102-1140) are a starting point. Fix the capacity scaling first (Problems item 19).

6. **Room text as data.** Write `<VARIES>`-style content as a list of `(condition, text)` pairs in Python, where a condition is a small predicate over season, time, weather, posture and arrived-from. Avoid a custom tag language.

7. **Money as a table.** Denominations in a dict, matched with the same fuzzy name test as items, with plural forms in a table rather than string tricks.

8. **Single-player cuts.** Drop channels, journals, tell, intermud, GMCP and MXP markup, AFK bells, account notices and roleplay experience. Keep say (to nearby characters if useful), posture changes, the exits paragraph and the look modes. Keep mana upkeep and aging as counters. Keep hygiene only if it adds play.

9. **Time model.** Java uses a 4-second tick (`core/CMProps.java:681`, `TIME_TICK = 4000`). In a turn-based SUD, one tick is one turn, so sustain costs and durations remain counters. In a real-time SUD, use the same counters driven by a monotonic clock.

10. **Multiplayer rethinks.** "The second bottle you can see" depends on the viewer, so pass the viewer explicitly into every search. Ownership ("my sword") becomes "the player's items", and the visibility loop can be a simple filter.

## Coverage

- **Read in full:** `Libraries/EnglishParser.java` (all 4102 lines), `Libraries/CommonMsgs.java` (all 3051 lines), `Libraries/interfaces/EnglishParsing.java` (1132 lines), and `Libraries/interfaces/CommonCommands.java` (709 lines).
- **Read in part, outside the bucket, to explain interactions:** `Commands/Get.java` (lines 60-240); `Locales/StdRoom.java` (2600-2660 and 2730-2830); `MOBS/StdMOB.java` (2236-2280 and 4616-4653); `Locales/interfaces/Room.java` (242-310); `Abilities/Spells/Spell.java` (77); `Libraries/StdLibrary.java` (25-40); `core/collections/SLinkedList.java` (427-430); `Libraries/interfaces/DiceLibrary.java` (341-351).
- **Searched only:** callers of the parse helpers in `Commands/*.java` and `Items/MiscMagic/StdDice.java`, using grep; the `<VARIES>`, `<MASK>` and long tag names in `resources/`, using grep; and `CMLib` accessor resolution with `cm.py`.
- **Skipped within the bucket:** none. Outside the bucket, `Locales/StdRoom.java` 2661-2729 was not read.
