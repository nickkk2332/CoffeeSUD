# Presentation authoring

[Creator handbook](creator/README.md) · [Cookbook](creator/cookbook.md)

Presentation text is evaluated for each viewer without changing the authored
text. Existing title screens and character panels remain available. `NOTES`
still means builder playtest notes, `WORLD MAP` still draws the builder map,
and `TOUCH` still activates waypoints.

## Conditional descriptions and first entry

Rooms, items, creatures, exits, areas and readable text accept:

```
<VARIES><IFVAR BRIDGE=1>A new plank bridge spans the gorge.</IFVAR><ELSE>Charred ropes hang over the gorge.</ELSE></VARIES>
```

All legacy weather, season, time, tide, disposition, `VISITED`, direction and
`MASKn` tags work. `FROMUNKOWN` and `FROMUNKNOWN` are both accepted. New checks:

- `IFVAR name`, `IFVAR name=value`, or `IFVAR name GE 3` (EQ/NE/LT/LE/GT/GE).
  `VAR` is an alias. These read the global `*` script scope.
- `QUESTWON "quest name"`, `QUESTRUNNING "quest name"`, `QUESTSTEP "quest name" n`.
- `PARTYTATTOO name`, `HOLIDAY name`, `WANTED`, `INCOMBAT`, `FIRSTVISIT`.
- `FULLMOON`, `NEWMOON`, `WAXING`, `WANING`, `HOUR 2-4`, `MONTH name` or number,
  `DARK`, `LIT`, `INDOORS`, `OUTDOORS`.

Set world variables on a named host, for example `MPSETVAR WORLD BRIDGE 1`; the `*` lookup reads them from the global script store.

Blocks nest; `ELSE` toggles the previous condition. Use `&gt;` inside a tag's
arguments. A lone `+IF` zapper mask always passes; use `-IF "condition"`.
`AHELP VARIES` includes the tag reference and examples.

`FIRSTVISIT` is true for the character's first entry until their next move;
`VISITED` is already true during the arrival look. `Prop_FirstEntry` on a room
or area provides a separate saved once-only message:

```
TEXT="The torches flare as one." ONCE=CHARACTER TATTOO=SAW_THRONE SETVAR=THRONE=1
```

Use `ONCE=PROFILE` to share the once marker across a profile. `ID=stable-name`
keeps the marker independent of a room's name. SETVAR writes on the entering
character. These clue writes store literal values.

`<CALL waterdepth>` inserts a holding object's `FUNCTION_PROG` return, with
`$n` as viewer, in conditional or plain text. Calls are cached for one room
look, capped at 32 calls, and protected from recursive rendering. JavaScript
keeps its existing runtime limit. Text functions should be free of side effects.

## Lore and personal journal

Put UTF-8 definitions in `world/lore/*.ini`:

```
SUNDERING=The Sundering|History|^{LORE}The kingdom fell beneath the sea.^N
BESTIARY=The Deep Ones|Creatures|FILE=deep-ones.txt
```

Files resolve beneath `world/lore`. Java properties continuation and escaping
rules apply. `LORE`/`CODEX` lists unlocked categories and counts; `LORE History`
lists a category, `LORE SUNDERING` or `LORE The Sundering` reads it, and `LORE NEW`
lists unread entries. Unlock tattoos and read state survive saves.

```
MPLORE $n ADD SUNDERING
MPLORE $n ADD SUNDERING PROFILE
```

`Prop_Lore` on an item, room or mob:

```
ENTRY=SUNDERING ON=READ MSG="You decipher the old account." SCOPE=CHAR
```

ON supports READ, GET, EXAMINE, ENTER, KILL and DEATH. KILL credits the killer
of the host; DEATH credits the dying player. PROFILE stores `LORE_SUNDERING`
on the profile. Character unlocks use the same tattoo on the character.
Existing tome-page `LORE:key` tattoos remain recognized. Quests can use `SET LORE key` to unlock the current step's lore for participants, including characters who accept during that step.

Help entries can include `<MASK=zapper>`, `<LOCKED=teaser>` and `<CATEGORY=name>`.
The metadata is stripped; failing a mask shows the teaser or hides the entry.

`JOURNAL` lists saved entries newest first. `WRITE text`, `DELETE id`, `SEARCH
phrase` and a category filter are available. IDs remain stable after deletion;
the newest 300 entries are kept. Script entries carry game dates, their source
and the recipient's location:

```
MPJOURNAL $n Clues "The vault opens to the word Ashgrove."
MPJOURNAL $n "Remember the hermit."
```

Set `JOURNALAUTO=QUEST,LEVEL,AREA,LORE` for automatic events. It is empty by
default. `JOURNALTALK=true` records topics learned from `[[topic]]` dialogue.
Scene scripts can use MPJOURNAL in a DO step, or NOTE "text" for a Rumors entry for each player present.

## Details, senses, darkness and visibility

`Prop_Details` on rooms, items or creatures:

```
fountain,basin=A cracked marble basin. HIGHLIGHT;ledger,red ledger=EXAMINEONLY An entry is circled. READ="Vault, third bell." TATTOO=CLUE_VAULT SETVAR=LEDGER=1 MASK="-INT +8"
```

Real objects take priority. LOOK/EXAMINE/READ try detail keywords afterward;
`LOOK keyword ON object` qualifies the host. EXAMINEONLY restricts the detail
to EXAMINE. READ supplies alternate text. Clues run once for each character,
host and detail definition. HIGHLIGHT marks keywords in the room description.
LOOK_PROG receives the matched keyword in `$g`.

`Prop_Senses` on rooms, items, creatures or exits:

```
SOUND="Water drips.|A pebble falls.";TOUCH="Nine studs; the third is loose.";TASTE="Salt." CHANCE=100 TATTOO=FOUND_STUD
```

Quote text and option values containing spaces or semicolons. `|` separates
alternatives. LISTEN [target], TOUCH/FEEL target and TASTE target work in
darkness; SETVAR/TATTOO run on first use of each sense. LISTEN falls back to
sound emotes, then to a learned thief Listen skill. EAVESDROP/THIEFLISTEN invokes
that skill directly. TOUCH falls back to its existing social. SNIFF and
Prop_Smell retain their existing behavior.

A dark room's `Prop_DarkDescription`:

```
TITLE="Somewhere dark" DESC="Cold water laps at your knees."
```

Alternatively use DARK/LIT blocks in its VARIES description. This fallback
reveals no room contents; normal senses and exit display still apply.

`Prop_ShowWhen` can gate items, creatures, exits and rooms per viewer:

```
MASK="-TATTOO +SPIRITSIGHT" WHEN="IFVAR GHOSTS=1" HIDEMODE=UNSEEN
```

CHECK defaults to VIEWER; ROOMPLAYERS, ANYPLAYER or HOST changes whose mask is
tested. WHEN accepts a VARIES tag with its argument, or a zapper mask. A hidden
host cannot be handled, attacked or crossed. HIDDEN lets successful SEARCH
reveal it permanently to that character; UNSEEN requires the condition.
Optional ID stabilizes discovery markers. SYSOPMSGS bypasses it for builders.

## Player maps and map items

MAP [radius] draws known rooms and connections, indoors or outdoors; MAP AREA
name centers on a known room in that area. Radius is bounded to 1–12. CARTOGRAPHY
is the paper-making skill. `AUTOMAP ON|OFF` and `AUTOMAP SIZE n` configure the
same renderer used by the window panel. A player can enable it even when
AWARERANGE is zero.

`MAPFOG=KNOWN` (default) combines visited and revealed rooms. VISITED excludes
revealed rooms until entered; ALL is an author opt-in to showing unexplored
rooms. Unmappable rooms/areas, hidden glyphs and conditionally hidden exits
are filtered. Revealing rooms never records a visit.

```
Prop_MapGlyph: CHAR=I COLOR=y LABEL="Inn" LEGEND
Prop_MapGlyph: HIDE
Prop_MapReveal: ROOMS=Coast#1-40,Coast#45 MARK=Coast#12:X:"Buried chest" COLOR=r
```

Prop_MapReveal goes on a readable item. AREA="name" reveals a whole area.
Read it to save knowledge and markers on the reader; revealed rooms appear
dim. Normal map items render titles for the reader rather than caching raw
conditional titles across all viewers.

## Ambient lines and narration

Emoter retains its existing types, timing, broadcast, INROOM and EXPIRES:

```
min=8 max=20 chance=40 RETRY NOREPEAT=2 QUIETAFTER=3;NIGHT SOUND an owl hoots.;IFVAR WOLVES=1 WEIGHT=3 ONCE SOUND wolves howl nearby.
```

Per-line prefixes: DAY/NIGHT/DAWN/DUSK, seasons, RAIN/SNOW/FOG,
INDOORS/OUTDOORS, IFVAR name=value, MASK="...", ONCE and WEIGHT=n. Masks are
checked in the actual room. RETRY chooses among eligible lines, weighted lines
use their weights, and NOREPEAT excludes recent lines while alternatives exist.
ONCE is per character and saved. QUIETAFTER suppresses ambience during combat
or narration, and for that many game ticks after reading, sensing or combat.
Sounder bark-table lines accept the same prefixes alongside existing weights,
masks, event chances and cooldowns.

```
MPNARRATE $n crypt.txt#awakening PAUSE STYLE=LORE
```

Reads `world/text/crypt.txt`; `[awakening]` starts a section. Blank lines
separate paragraphs. Script substitutions and VARIES apply. Targets ROOM and
AREA broadcast to their players. NOPAUSE is the default. PAUSE uses asynchronous
Enter callbacks between paragraphs, takes no turns, and holds the single-player
real-time clock. Completion or disconnect releases only the narration's hold. Background party sessions receive the text without an input hold; characters with open windows page individually.
STYLE selects a named color alias.

## Templates and named colors

Prompt tokens include `%{PVAR name}` (player), `%{QVAR quest var}`,
`%{TIMEMODE}` (REAL/TURN) and `%{LEADER}`. Existing `%{VAR name}` preserves
player-first lookup with global fallback; `%{VAR * name}` explicitly reads
global scope, and `%{VAR host name}` reads a named host.

`world/score.txt` adds lines to SCORE, using prompt tokens and VARIES. Lines
that render blank are hidden. `ROOMHEADER` and `ROOMFOOTER` use the same text
engine, for example `ROOMHEADER=^O%r^N ^w[%z]^N`. Empty settings retain the
standard room layout. A hidden room's title is never exposed by a template.

```
COLORALIASES=LORE=##C8A060,DANGER=LIGHTRED+BOLD,WHISPER=DARKGREY,MAGIC=LIGHTPURPLE
```

Use `^{LORE}text^N` anywhere normal color codes work. COLORSET ALIASES lists
aliases; COLORSET LORE LIGHTGREEN saves a personal override, and COLORSET LORE
DEFAULT removes it. COLORSET DEFAULT resets all color preferences. Named
aliases expand before wrapping and strip cleanly with ANSI disabled.


See [authoring identities, checks and recovery](authoring-reliability.md) for
saved source IDs, migration aliases, validation and world asset tracking.
