# Presentation

What the player sees: conditional and first-visit text, details and senses, ambient lines, maps, lore codex, journal, narration, prompt, screens and colours. Part of the [builder capabilities audit](README.md).

<a id="pr1"></a>

## PR1. VARIES text on items, mobs, exits and areas

*Kind:* presentation · *Value:* high · *Effort:* small · *Decision:* yes

**Today.** Only `StdRoom` runs `CMLib.commands().parseVaries`. On `StdItem`, `StdMOB`, `StdExit` and `StdArea`, the viewer-aware `description(MOB)` and `displayText(MOB)` return the raw text, though `CommonMsgs` already calls them. The workaround is a `LOOK_PROG` or `TIME_PROG` that runs `MPSET $i DESCRIPTION`, which changes the text for everyone. In rooms, moon, combat, hour and month tests already work through `<MASKn>` (`-IF ISMOON(FULL)`, `-IF ISFIGHT($i)`, `+HOURS`, `+MONTHS`), but they are long to write. Nothing tests for a holiday or for being wanted.

**Add.** Run `parseVaries` in those methods and in readable text. Take the clock and climate from the holder's room and area. Every existing tag then works on things. Add short tags as sugar over `MASK`: `<FULLMOON>`, `<NEWMOON>`, `<WAXING>`, `<WANING>` (`TimeClock.MoonPhase`), `<INCOMBAT>`, `<DARK>`, `<HOUR 2-4>`, `<MONTH name>`, `<VAR name=value>`. Two tags are new tests: `<HOLIDAY name>` (ws-holidays) and `<WANTED>`.

**Example.** The old fountain's description: `<VARIES><WINTER>Its basin is a disc of grey ice.</WINTER><ELSE>Clear water trickles from a stone fish.</ELSE></VARIES>`.

*See also:* W1, W26 (later) (W1's +WANTED mask is what W26 (later)'s bounty hunters need and what PR1's \<WANTED> tag tests); W6 (Both add a VARIES \<HOLIDAY name> tag); PR2, PR16 (PR1's and PR2's new VARIES tags need PR16's VARIES help); PR11 (Both define a \<DARK> VARIES tag, with different meanings).

<!-- from: properties/pr-varies-all, world-systems/ws-varies-time, presentation/pr-varies-everywhere -->

<a id="pr2"></a>

## PR2. VARIES tags for world variables, quests and the party

*Kind:* presentation · *Value:* high · *Effort:* small · *Decision:* yes

**Today.** World state can already change text: `<MASK1 -IF "VAR(* BRIDGE == 1)">` reads the global scope that `WorldState` saves, and the whole party sees the same result. `-QUESTWIN` tests for a quest won, and `&gt;` stands in for `>`. But the syntax is obscure and the VARIES help does not cover it. A lone `+IF` always passes, so the obvious spelling fails without warning. Tattoo masks are per character. Nothing tests for a running quest, a quest step, or a tattoo held by anyone in the party.

**Add.** Short tags that compile to those checks:
- `<IFVAR name>`, `<IFVAR name=value>` and `<IFVAR name GE 3>` (also EQ, NE, LT, LE, GT), on the `*` scope;
- `<QUESTWON quest>`, `<QUESTRUNNING quest>` and `<QUESTSTEP quest n>`;
- `<PARTYTATTOO X>`.

They nest and pair with `<ELSE>` like the other tags. The tests for a running quest, a quest step and a party tattoo are new.

**Example.** `<VARIES><IFVAR BRIDGE=1>A new plank bridge spans the gorge.</IFVAR><ELSE>The bridge hangs in charred ropes.</ELSE></VARIES>`. The carpenter's quest ends with `MPSETVAR * BRIDGE 1`.

*See also:* PR1, PR16 (PR1's and PR2's new VARIES tags need PR16's VARIES help).

<!-- from: presentation/pr-varies-state -->

<a id="pr3"></a>

## PR3. First-visit text: FIRSTVISIT tag and Prop_FirstEntry

*Kind:* presentation and property · *Value:* high · *Effort:* small · *Decision:* yes

**Today.** `<VISITED>` cannot give first-time text. `StdRoom.executeMsg` records the visit (`addRoomVisit`) on entry, before `MUDTracker` shows the arrival look, so `<VISITED>` is already true. There are four workarounds:
- a room `GREET_PROG +TATTOOS -SAW_THRONE` that runs `MPECHOAT` and `MPTATTOO`; its text comes after the look, a tick later unless `MPSETINTERNAL NODELAY true`;
- a VARIES `MASK` on that tattoo, plus the same script;
- a masked `Prop_Sounder` `ENTER`, which cannot set a tattoo;
- a `ROOMENTER` achievement.

**Add.** A `<FIRSTVISIT>` tag that is true during the arrival look of a character's first entry. Player stats keep a "this entry was new" flag until the next move. Also `Prop_FirstEntry` on rooms or areas: `TEXT=` is shown once per character, or once per profile with `ONCE=PROFILE`. Optional `SETVAR=` and `TATTOO=` let scripts react. VARIES is allowed.

**Example.** The throne room: `Prop_FirstEntry TEXT="^xThe torches flare as one, and the empty throne seems to turn toward you.^N" TATTOO=SAW_THRONE`.

*See also:* ST3, P11 (Three hooks on a character's first entry to a room (a trigger, once-only text, discovery XP) that can share one first-visit flag).

<!-- from: presentation/pr-firstvisit -->

<a id="pr5"></a>

## PR5. Title screen, game name and prologue from world data

*Kind:* presentation · *Value:* high · *Effort:* small · *Decision:* yes

**Today.** The launcher (`SUD.chooseSave`) prints a plain "Saved games:" list on stdout, before any colour. Then `resources/text/intro.txt` shows. That file can be replaced, and `@MudInfo?NAME@` expands to `MUD_NAME`, but the launcher passes `CoffeeSUD` as the host name, which overrides the ini's `MUD_NAME`, so the name cannot be set without Java; and it still says "Powered By CoffeeMud v5.11". Only the network path picks a random `intro*.txt`. After a new character, only `newchardone.txt` shows. A prologue is possible through `CHARCREATIONSCRIPTS=END:LOAD=progs/x.script`, a `ONCE_PROG` using `MPPROMPT` and `MPECHO`, and coffeemud.ini documents the hook, with `resources/progs/chardesc.script` as a sample `ONCE_PROG` using `MPPROMPT`, but nothing documents using it for a prologue. PLAN phase 1 still lists a title screen as to do.

**Add.** The launcher shows `world/text/title.txt` (colour codes, `@MudInfo?NAME@`, `GAMENAME=` from the ini) with a menu: Continue (latest save), New game, Load (the list), Quit. A new game shows `world/text/prologue.txt` one page at a time, with [Enter] between pages, or runs `PROLOGUE=script` once in the start room. Document the `CHARCREATIONSCRIPTS END` hook as the place for a scripted prologue. An optional `EPILOGUE` text can serve a command that ends the game, such as `MPENDGAME` if it is added.

**Example.** An ASCII skyline and "Vael: The Ashen Crown", then "C) Continue - Ser Aldric, The Mirefen, day 34". A new game opens with a three-page prologue and wakes the hero in a cell.

*Re-checked against the code after writing: corrected.*

*See also:* PR12 (Both show story text page by page, waiting for Enter (prologue pages vs MPNARRATE PAUSE)).

<!-- from: presentation/pr-title-screen -->

<a id="pr6"></a>

## PR6. A lore codex: LORE command, Prop_Lore and MPLORE

*Kind:* property, script command and presentation · *Value:* high · *Effort:* medium · *Decision:* yes

**Today.** The engine has no codex, bestiary or lore system. Help (`resources/help/*.ini`) is one global table that shows everything from the start, and its `<ZAP=...>` macro hides nothing. The nearest tools are achievements (`SCRIPTED` ones fired by `MPACHIEVE`, listed by `ACHIEVEMENTS`), `GenBook` and `GenReadable` items, tattoos used as flags, and `GenCommand`. None of them has categories, a way to re-read an entry later, or profile scope.

**Add.** Entries go in `world/lore/*.ini` as `KEY=title|category|text`, with VARIES and colour, or as `FILE=`. Three things unlock an entry: `MPLORE $n ADD key`, `Prop_Lore` (`ENTRY=`, `ON=READ|GET|EXAMINE|ENTER|KILL|DEATH`, `MSG=`) on readables, rooms and mobs, and quests. Each unlock is stored as the tattoo `LORE_key`, per character or per profile with `SCOPE=PROFILE`. Saves keep it, and `HASTATTOO` and `+TATTOO` can test it. `LORE` lists categories with counts, `LORE <topic>` reads one entry and `LORE NEW` shows unread ones. Help entries may also take `<MASK=zapper>`, `<LOCKED=teaser>` and `<CATEGORY=>`.

**Example.** Reading the Tablet of Ash adds "The Sundering" under History. The sage's script checks `HASTATTOO($n LORE_SUNDERING)` before it tells the next part.

*See also:* I3 (I3's pages carry LORE= keys from PR6's codex).

<!-- from: properties/pr-lore, data-content/dc-help-lore-gating, presentation/pr-codex -->

<a id="pr7"></a>

## PR7. JOURNAL: saved notes, clues and an automatic chronicle

*Kind:* script command and presentation · *Value:* high · *Effort:* medium · *Decision:* yes

**Today.** `REPLAY` replays only the messages of the current session and is not saved. A builder can hand out a writable `GenBook` or `GenJournal`; its entries live in `CMJRNL`, which saves keep. `CALENDAR ADD` keeps dated events. `COMMANDJOURNALS=` in `coffeemud.ini` can define a journal command, but no script command can write to it. `QUESTS` shows only `INSTRUCTIONS` and `PROGRESS`. Nothing records what happened or what an NPC said.

**Add.** A `JOURNAL` command, with `NOTES` as an alias. It lists entries newest first with the game date, and takes `WRITE text`, `DELETE n`, `SEARCH word` and a category name. The script command `MPJOURNAL $n [category] "text"` records who said it, where and when, under categories such as Clues, People, Places and Rumors. Switches in the ini add automatic entries: quest accepted or won, level gained, new area, lore unlocked. Optionally every `TALK` reply with a `[[topic]]` becomes an entry too. If dialogue and rumour systems are added, they can feed it with a `NOTE` flag. The log is kept in player data and saved.

**Example.** The hermit's script runs `MPJOURNAL $n Clues "The vault opens to the word 'Ashgrove' (the hermit)"`. Ten game days later, `JOURNAL SEARCH vault` finds it.

*See also:* Q3 (Two journals: Q3's quest log with MPQJOURNAL and PR7's JOURNAL with MPJOURNAL and automatic quest entries); N23 (later), N24, N28 (N23 (later)'s rumours, N24's [[topic]] lines and N28's overheard scenes can write PR7 journal entries); DT26 (Both claim the NOTES command (PR7's JOURNAL alias vs DT26's playtest notes)).

<!-- from: npc-dialogue/dlg-notes, presentation/pr-journal -->

<a id="pr8"></a>

## PR8. Prop_Details: extra descriptions by keyword

*Kind:* property · *Value:* high · *Effort:* medium · *Decision:* yes

**Today.** Details are made with scenery items. A `GenItem` gets an empty display text and is made ungettable. `EXAMINE` highlights its name in the room text (`^H`, with an MXP link), `LOOK` shows its description, and a `LOOK_PROG` on it can `MPTATTOO` the viewer. A `CMDFAIL_PROG` can also catch the failed `LOOK`. This works, but each detail is a whole item, and the items clutter saves and the catalog. No property maps keywords to text.

**Add.** `Prop_Details` on rooms, items and mobs: `fountain,basin=A cracked marble basin.;runes,carving=<VARIES>...`. `LOOK`, `EXAMINE` and `READ` try the keywords after real objects fail. Options per detail:
- `EXAMINEONLY`;
- `READ=text`;
- `SETVAR=` or `TATTOO=` on first look, so a quest can require "has examined the runes";
- `HIGHLIGHT`, which colours the keyword in the room text as hidden items are;
- `MASK=`.

A look at a detail fires `LOOK_PROG` with the keyword.

**Example.** A library: `Prop_Details books,shelves=Rows of mouldering ledgers.;ledger,red ledger=EXAMINEONLY One entry is circled in red: 'Vault, third bell.' TATTOO=CLUE_VAULT`.

<!-- from: presentation/pr-room-details -->

<a id="pr9"></a>

## PR9. A map for every player, with fog of war and room glyphs

*Kind:* presentation, property and world option · *Value:* high · *Effort:* medium · *Decision:* yes

**Today.** `AUTOMAP ON` draws a minimap after each look when `AWARERANGE` is above 0:
- the ini sets its size for the whole world, not per player;
- it shows rooms not yet visited;
- its glyphs come only from the domain type;
- it stops at indoor rooms.

`MAP` is the Cartography skill (`Skill_Map`), which is limited to some classes and needs paper. The `REGION` skill works outdoors only. `WHERE`, `AREAS`, prompt `%l`/`%L` and `EXPLORED()` draw nothing. Builders cannot mark an inn or a shrine.

**Add.**
- (a) A free map command: `MAP`, `MAP n` (radius) and `MAP AREA name`. It shows the rooms visited, `*` for you, exits and a legend. It reuses `getMiniMap` with a `DefaultPlayerStats.hasVisited` filter. `Skill_Map` already uses the word `MAP`, so one of the two needs another name.
- (b) `Prop_MapGlyph` on rooms: `CHAR=I COLOR=y LABEL="Inn" LEGEND`, or `HIDE`.
- (c) An ini option, `MAPFOG=VISITED|KNOWN|ALL`.
- (d) `AUTOMAP SIZE n`, set by each player.

All of it honours `Prop_RoomUnmappable`.

**Example.** In town the inn shows as a yellow `I` and the temple as a white `+`. Unexplored alleys stay blank until walked.

*See also:* B10 (Two MAP commands (a player map with fog of war vs a builder map with IDs), both clashing with Skill_Map's word); PR19 (PR19's revealed rooms are shown by PR9's MAP command).

<!-- from: presentation/pr-map-command -->

<a id="pr10"></a>

## PR10. Emoter: conditional, weighted, non-repeating ambient lines

*Kind:* behaviour · *Value:* medium · *Effort:* small · *Decision:* yes

**Today.** `Emoter` picks lines at random with equal odds and can repeat them. It knows `SIGHT`, `SOUND`, `SMELL`, `SOCIAL`, `PUFF`, `INROOM=`, `EXPIRES=` and one `MASK=` for the whole behaviour. It has two bugs:
- `emoteHere` tests the `MASK=` before `emoter.setLocation(room)`, so time and weather masks are unreliable, and a failed mask silences that tick;
- `EmoteObj.mask` is never set, so masks per line never apply.

A room holds only one `Emoter`. The workaround is a `RAND_PROG` that tests `ISTIME` or `VAR`, then runs `MPECHO`.

**Add.** Fix both bugs. Masks are checked after the stand-in mob is placed, and an area emoter checks each room's own conditions. Add prefixes per line in `Emoter` and `Sounder`:
- `DAY`/`NIGHT`/`DAWN`/`DUSK`, `SPRING`..`WINTER` and `RAIN`/`SNOW`/`FOG` (the VARIES names);
- `INDOORS`/`OUTDOORS` and `IFVAR name=value`;
- `MASK="..."`, now parsed;
- `ONCE` (per character) and `WEIGHT=n`.

Add options `NOREPEAT=k`, `RETRY` (pick again among lines that pass) and an area-level `QUIETAFTER=n` that pauses ambience during combat or reading. The old syntax keeps working.

**Example.** A forest area: `min=8 max=20 chance=40;NIGHT SOUND an owl hoots somewhere above.;IFVAR WOLVES=1 SOUND wolves howl, closer than before.`

*See also:* N22 (Both add per-line time, weather and mask conditions, weights and once or cooldown rules to Sounder).

<!-- from: behaviors-world/bw-emoter-weights, presentation/pr-emoter-conditions -->

<a id="pr11"></a>

## PR11. Descriptions for dark rooms

*Kind:* property · *Value:* medium · *Effort:* small · *Decision:* yes

**Today.** When `canBeSeenBy(room, mob)` fails, the title and description are skipped and the player gets "You can't see anything!". To be seen, a dark room needs the moon, a light or darkvision. A builder can add `LISTEN` or `FEEL` with `Prop_Socials` (`SRCCODE=S`) and `SNIFF` with `Prop_Smell`. A `LOOK_PROG` can test `CANSEE` and run `MPECHOAT`. But that text comes after the message, not in its place.

**Add.** `Prop_DarkDescription` on rooms: `TITLE="Somewhere dark" DESC="..."`, with VARIES allowed. It is shown in place of "You can't see anything!". The other form is a pair of tags, `<DARK>...</DARK>` and `<LIT>...</LIT>`, honoured even when the room cannot be seen. Exits are still listed when `AUTOEXITS` is on.

**Example.** A flooded crypt: `Prop_DarkDescription TITLE="Darkness" DESC="Cold water laps at your knees and something brushes past your leg."`

*See also:* PR1 (Both define a \<DARK> VARIES tag, with different meanings).

<!-- from: presentation/pr-dark-desc -->

<a id="pr12"></a>

## PR12. MPNARRATE: story text from files, with pauses

*Kind:* script command · *Value:* medium · *Effort:* small · *Decision:* yes

**Today.** Long story passages have to be written into scripts as `MPECHOAT` lines. `LOAD=` reads only scripts, not text files, and no MOBPROG command echoes a file. Only JavaScript in a `<SCRIPT>` block can call `Resources.getFileResource`. `MPPROMPT $n VAR [Press Enter]` makes a pause, but it blocks the thread. The guide therefore allows it only from player-caused triggers such as `GREET_PROG`, not from tick triggers.

**Add.** `MPNARRATE target file[#section] [PAUSE|NOPAUSE] [STYLE=LORE]`. It reads `world/text/file`, where sections are marked `[name]`. It applies VARIES and `$` variables and shows the text to a player, the room or the area. `PAUSE` waits for [Enter] between paragraphs without blocking a thread. In turn-based time the clock does not move. In real time the game pauses, as `PAUSE` does.

**Example.** The crypt's `ENTRY_PROG` runs `MPNARRATE $n crypt.txt#awakening PAUSE`. Four paragraphs tell of the lich waking, one per Enter.

*See also:* PR5 (Both show story text page by page, waiting for Enter (prologue pages vs MPNARRATE PAUSE)).

<!-- from: presentation/pr-narration -->

<a id="pr13"></a>

## PR13. CALL tag: text computed by a script function

*Kind:* JavaScript hook and presentation · *Value:* medium · *Effort:* small · *Decision:* yes

**Today.** A computed value cannot be placed inside a description. `LOOK_PROG` is queued and runs a tick later. With `MPSETINTERNAL NODELAY true` it runs at once, but after the look, so it can only add text at the end. A `CNCLMSG_PROG` on the room runs before the look is shown and can `MPSET` the `DESCRIPTION` before each `LOOK`. That works, but it rewrites the stored text. `FUNCTION_PROG` with `RETURN` already exists in MOBPROG and JavaScript.

**Add.** A `<CALL fname args>` tag in VARIES text. At once, it calls a `FUNCTION_PROG` or JS function in the scripts of the room, item, mob or area that holds the text, with `$n` as the viewer. What the function returns goes into the text. Results are cached for each look, with a time limit like the one JavaScript already has.

**Example.** A well: "The rope runs into the dark; the water is about `<CALL waterdepth>` feet below." `FUNCTION_PROG waterdepth` returns 30 minus 5 times `VAR(* BUCKETS)`.

<!-- from: presentation/pr-desc-call -->

<a id="pr14"></a>

## PR14. Script variables in the prompt, and world-defined score lines

*Kind:* presentation · *Value:* medium · *Effort:* small · *Decision:* yes

**Today.** Factions cover part of this:
- a faction with `SCOREDISPLAY=TRUE` shows in `SCORE` ("Your Renown : Feared");
- the prompt shows it with `%f` or `%F`;
- `MPFACTION` changes it.

The prompt also has `%-STAT`, `%t`/`%T`, `%z`/`%r` and `%<CONFIGFLAG` sections. But factions are per character and numeric. No token shows a script variable or the time mode, and `SCORE` takes no free lines.

**Add.** Five prompt tokens:
- `%{VAR name}`, a global variable;
- `%{PVAR name}`, the player's own variable;
- `%{QVAR quest var}`;
- `%{TIMEMODE}`, REAL or TURN;
- `%{LEADER}`.

A file, `world/score.txt`, holds lines added to `SCORE`. They use the prompt tokens plus VARIES, and a line that comes out blank is hidden. Builders who want per-character standings should use factions.

**Example.** `score.txt` holds `Chapter : %{VAR CHAPTER}`. A second line uses VARIES to show `^rThe ash-mark burns on your palm.^N` while `CURSED` is 1. The prompt is `^h%hhp ^w[%{VAR CHAPTER}]^N>`.

*See also:* PR20, PR21 (PR20's header templates and PR21's panel build on PR14's prompt tokens).

<!-- from: presentation/pr-status-lines -->

<a id="pr15"></a>

## PR15. LISTEN and TOUCH commands, with Prop_Senses

*Kind:* property and presentation · *Value:* medium · *Effort:* small · *Decision:* yes

**Today.** For smell, `SNIFF` with `Prop_Smell` works. `LISTEN` is only the thief skill `Thief_Listen`, and there is no `FEEL`. `TOUCH` is a stock social (`resources/socials.txt`) for mobs, room items and worn equipment, and `SOCIAL_PROG TOUCH` on the item or mob can answer it. It cannot target exits or untakeable items with no display text, and it shadows any `Prop_Socials` `TOUCH`. `Prop_Socials` can already add local commands to an area, room, exit or mob, or to an item for whoever carries it. For example: `NAME=LISTEN SRCCODE=S TGTCODE=S YOUSEE="Water drips ahead."`, with `TARGET=` and `MASK=`. These commands work in the dark, and `SOCIAL_PROG` lets a script set a tattoo. What is missing is built-in commands with fallbacks and one property for all the senses.

**Add.** Commands `LISTEN [target]` and `TOUCH`/`FEEL target`. `Prop_Senses` on rooms, items, mobs and exits takes `SOUND=..;TOUCH=..;TASTE=..`. A `|` separates alternative texts, VARIES is allowed, and it accepts `CHANCE=` and `SETVAR=` or `TATTOO=` on first use. `LISTEN` in a room with no `SOUND` falls back to the room's sound emotes. All of it works in the dark. The clashes with `Thief_Listen` over the word `LISTEN` and with the stock `TOUCH` social must be settled.

**Example.** A dark vault door: `Prop_Senses TOUCH="Under your fingers, a ring of nine raised studs; the third is loose." TATTOO=FOUND_STUD`.

*Note.* `TOUCH` is already a stock social that `SOCIAL_PROG` can answer, and a room's `LISTEN` can be added with `Prop_Socials` (see *Already possible* in the README). What this item adds is built-in commands with fallbacks and one property for all the senses.

*Re-checked against the code after writing: corrected.*

<!-- from: presentation/pr-senses -->

<a id="pr16"></a>

## PR16. In-game help for VARIES and the description tags

*Kind:* data file · *Value:* medium · *Effort:* small · *Decision:* yes

**Today.** `<VARIES>` is documented only in `guides/ArchonGuide.html`. No `resources/help/*.ini` entry mentions it, so a builder in the game cannot look up the tags or the `MASK` syntax. Zapper masks do have in-game help (`AHELP ZAPPERMASKS`, from `resources/help/zappermasks.txt`).

**Add.** Entries `VARIES`, `DESCRIPTION_TAGS` and `ROOM_TEXT` in `resources/help/arc_misc.ini`. They cover:
- every tag, `<ELSE>` toggling the last tag, and nesting;
- `MASKn` with `-IF "VAR(...)"` and `-QUESTWIN`, and `&gt;` for `>`;
- the trap that a lone `+IF` always passes;
- the `FROMUNKOWN` spelling;
- a link to `ZAPPERMASKS`, and examples ready to copy.

Update them as new tags arrive (`IFVAR`, `FIRSTVISIT`, `CALL`). This is a data change only.

**Example.** `AHELP VARIES` shows the tag table and three examples ready to copy. One of them is the bridge text that changes after `MPSETVAR * BRIDGE 1`.

*See also:* PR1, PR2 (PR1's and PR2's new VARIES tags need PR16's VARIES help); DT17 (Both deal with the FROMUNKOWN tag spelling (document it vs add FROMUNKNOWN as an alias)).

<!-- from: presentation/pr-ahelp-varies -->

<a id="pr17"></a>

## PR17. Named colour aliases

*Kind:* world option · *Value:* medium · *Effort:* small · *Decision:* yes

**Today.** Builders write raw codes such as ^y or ^##C8A060 into every lore line, inscription and whisper, so restyling means editing every description. The engine's semantic codes can be remapped in one line, with COLORSCHEME in the ini or by the player with COLORSET. Several are unused in single player: ^x, ^X and ^Z (IMPORTANT1 to 3), ^Q and ^q (channels), ^t (TELL) and ^z (DING). A world can repurpose them now, for example ^x for lore and ^Z for danger. But the set is fixed and the letters carry no meaning.

**Add.** A COLORALIASES setting in the ini or the world: `COLORALIASES=LORE=##C8A060,DANGER=LIGHTRED+BOLD,WHISPER=DARKGREY,MAGIC=LIGHTPURPLE`. Any text then uses `^{LORE}...^N`. COLORSET lists the aliases so the player can change them, and they are stripped when ANSI is off.

**Example.** Every inscription is written `^{LORE}...^N`. The lore colour changes in one line.

<!-- from: presentation/pr-color-aliases -->

<a id="pr18"></a>

## PR18. Prop_ShowWhen: things only some viewers see

*Kind:* property · *Value:* medium · *Effort:* medium · *Decision:* yes

**Today.** Prop_ClosedSeason hides a mob or item only by time, season or weather, and it checks its mask on the hidden thing, not on the viewer. Exits can be closed, not hidden. There are workarounds. An invisible ghost can be seen with see-invisible from Prop_UseSpellCast2(Spell_DetectInvisible) on a drink or Prop_WearSpellCast on an amulet. Prop_Hidden or the Semi-Hidden flag lets Search find a thing. MPHIDE and MPUNHIDE work from a script (they are global, which is fine in single player). None of these decides by a mask on the viewer.

**Add.** Prop_ShowWhen, with `MASK=` (a zapper mask on the viewer), `CHECK=ROOMPLAYERS|ANYPLAYER|HOST`, `WHEN=` (a VARIES code or mask) and `HIDEMODE=UNSEEN|HIDDEN` (Search can find a HIDDEN host). The mask is checked against the player characters present. Until it passes, the host cannot be seen or touched, and an exit cannot be passed.

**Example.** The seer's tea carries Prop_TattooAdder SPIRITSIGHT. The ghosts and a spectral stair north in the old tower carry `Prop_ShowWhen MASK="-TATTOO +SPIRITSIGHT"`. After the tea, the player sees them.

<!-- from: properties/pr-showwhen -->

<a id="pr19"></a>

## PR19. Map items that reveal and mark rooms

*Kind:* property · *Value:* medium · *Effort:* medium · *Decision:* yes

**Today.** BardMap's readable text can be a semicolon list of room IDs (Area#1;Area#2;...), and reading it draws only those rooms. So a map of just the smugglers' coves works now. GenMap draws whole areas. Both draw the raw room title, cut to 24 characters in 6-character box lines, and VARIES is not applied. A map cannot mark a spot, and reading one teaches the character nothing for later.

**Add.** Prop_MapReveal on readable items, with `ROOMS=Area#1-40` or `AREA=name`. On READ it adds the rooms to a set of known rooms on the character, saved like visited rooms. A MAP command (a separate item) shows them dimmed. `MARK=Area#12:X:"Buried chest"` puts a labelled glyph on that room. StdMap switches to displayText(viewer), so VARIES titles show.

**Example.** Reading a torn treasure map adds the smugglers' coves to the player's MAP, with a red X on cove 3.

*See also:* PR9 (PR19's revealed rooms are shown by PR9's MAP command).

<!-- from: presentation/pr-map-items -->

<a id="pr20"></a>

## PR20. Room header and footer templates

*Kind:* world option · *Value:* low · *Effort:* small · *Decision:* yes

**Today.** The first line of a look is hard-coded: ^O, then the room title from displayText(mob), then the disposition notes. Only its colour can change, through ROOMTITLE in COLORSCHEME or with COLORSET. The ini has no header or footer template, and the area name shows only in the prompt, as %z. So a world cannot show the area, a region tag or a danger marker beside the title. Nor can it add a fixed footer, such as the time of day outdoors.

**Add.** ROOMHEADER and ROOMFOOTER settings in the ini or the world. They use the prompt token engine plus VARIES, for example `ROOMHEADER=^O%r^N ^w[%z]^N`. An empty setting gives today's output.

**Example.** `ROOMHEADER=^O%r^N ^k(%z)^N` gives "Market Square (Old Vael)".

*See also:* PR14, PR21 (PR20's header templates and PR21's panel build on PR14's prompt tokens).

<!-- from: presentation/pr-room-header -->

<a id="pr21"></a>

## PR21. Side panel in the character windows

*Kind:* presentation · *Value:* low · *Effort:* medium · *Decision:* undecided (owner: "Maybe, not sure yet")

**Today.** The Swing character windows (CharacterWindow) hold only a scrolling text pane and an input line. PLAN 5e names health bars, the party and a map as later work, but nothing is defined, and a builder cannot choose what a panel shows. What would feed a panel already exists: the minimap (getMiniMap in Skill_RegionalAwareness) and the prompt token engine.

**Add.** A side panel, turned on by `WINDOWPANEL=STATUS,MAP,PARTY` in the ini or by `WINDOWS PANEL ON`. STATUS is drawn from world/text/panel.txt with the prompt tokens and %{VAR}. MAP is the minimap of the window's character, redrawn on each move. PARTY shows hit-point bars for the party. The panel refreshes with the prompt.

**Example.** panel.txt holds `%r / %z`, `HP %h/%H` and `Chapter %{VAR CHAPTER}`, with the map below.

*See also:* PR14, PR20 (PR20's header templates and PR21's panel build on PR14's prompt tokens).

<!-- from: presentation/pr-window-panel -->
