# Presentation, lore, maps, interface and languages

[Handbook](README.md) · [Presentation options](../presentation.md)
· [Language options](../languages.md) · [Identity and validation](../authoring-reliability.md)

Everything the player reads is built at the moment it is shown, for the person
reading it. This chapter covers how to control that text: conditional
descriptions (`<VARIES>`), first-visit and clue text, senses, dark rooms,
per-viewer visibility, the lore codex, the personal journal, narration, player
maps, ambient lines, prompts and score templates, colours, the title and
prologue screens, window panels, the glossary and other vocabulary files, and
the language system (comprehension, invented languages, learning by exposure).
It is written for world creators (who set these things up in the game and in
world files), scripters (who drive them from MOBPROG and JavaScript) and, in a
few places, engine developers.

## In this chapter

- [How presentation text works](#how-presentation-text-works): rendering, option syntax, saved state
- [Text is rendered for a viewer](#text-is-rendered-for-a-viewer): VARIES, every tag, ELSE and nesting, CALL, PREVIEW and CHECKTEXT
- [First entry, clues and sensory actions](#first-entry-clues-and-sensory-actions): Prop_FirstEntry, Prop_Details, Prop_Senses, Prop_DarkDescription, Prop_ShowWhen
- [Lore, journals and opening narration](#lore-journals-and-opening-narration): the codex, JOURNAL, MPNARRATE
- [Maps, ambience and interface](#maps-ambience-and-interface): MAP, AUTOMAP, map items, ambient lines, prompts, score, room header and footer, colours
- [Title, prologue and character windows](#title-prologue-and-character-windows): title.txt, prologue.txt, window panels, where settings may be set
- [Languages are mechanics](#languages-are-mechanics): comprehension, Prop_Monolingual, invented languages, immersion, inline inscriptions, scripts
- [Vocabulary, socials and help](#vocabulary-socials-and-help): glossary, local lists, damage words, VOCAB, socials, help overlays
- [Worked examples](#worked-examples)
- [Quick reference](#quick-reference): commands, properties, files, settings
- [Persistence notes](#persistence-notes)
- [Troubleshooting](#troubleshooting)
- [See also](#see-also)

## How presentation text works

### Authored text and viewer text

Presentation text is never rewritten when it is shown. A room description that
contains `<VARIES>` tags stays exactly as the builder typed it in the world
file; each time a character looks, the engine evaluates the tags for that
character, in that room, at that moment, and prints the result. Two characters
standing in the same room can therefore read different text. Every piece of
state the text reads (a story variable, a quest step, the weather) is outside
the text.

Consequences worth remembering:

- A look is a read. Text must not grant rewards, consume items, or advance a
  clock. Put consequences in scripts, events or the once-only properties
  described below.
- Because the stored text is unchanged, `STAT`, the room editor and the world
  files always show the raw tags. Use `PREVIEW` (see
  [Previewing and checking](#previewing-and-checking)) to see a rendering.
- Conditions are evaluated against the viewer. In CoffeeSUD a "viewer" is a
  character; a party of several characters means several viewers, and each
  window or `AS` view renders for its own character.

### Where viewer text is rendered

`StoryText.render` is called for:

| Surface | Notes |
| --- | --- |
| Room title and description | `StdRoom.displayText(mob)` and `description(mob)` |
| Area title and description | `StdArea` |
| Item display text and description | `StdItem` |
| Creature display text and description | `StdMOB` |
| Exit display text and description | `StdExit` |
| Readable text | Plain readables, `GenBook`, `GenJournal` entries, `GenTome` pages |
| Lore entries | `LORE` output |
| MPNARRATE text | Each viewer's copy |
| Clue and sense text | Prop_Details, Prop_Senses, Prop_FirstEntry, Prop_Lore `MSG`, Prop_DarkDescription |
| Ambient lines | Emoter and Sounder lines |
| `world/score.txt` lines, `ROOMHEADER`, `ROOMFOOTER` | Through the template renderer |

Names (the short name used in lists and messages) are not conditional.
Source: [StoryText.java](../../CoffeeMud/com/planet_ink/coffee_mud/core/StoryText.java).

### Option syntax shared by the presentation properties

Prop_FirstEntry, Prop_Details, Prop_Senses, Prop_ShowWhen, Prop_DarkDescription,
Prop_Lore, Prop_MapGlyph, Prop_MapReveal, Prop_LanguageImmersion,
Prop_Monolingual, Prop_LocalLists and Prop_WeaponMessages all read their text
with the same parser ([AuthoringOptions](../../CoffeeMud/com/planet_ink/coffee_mud/core/AuthoringOptions.java)):

- A property text is a list of tokens separated by whitespace or semicolons.
- `KEY=value` sets an option. Keys are case-insensitive. The first occurrence of
  a key wins.
- A bare word (no `=`) is a flag, for example `HIGHLIGHT`, `LEGEND`, `HIDE`.
- A value that contains spaces or semicolons must be in double quotes:
  `TEXT="The torches flare as one; the throne turns."`. Inside quotes, write a
  literal quote as `\"`, a literal backslash as `\\` and a literal semicolon as
  `\;`.
- An unclosed quote is an error that `PROPCHECK` and `WORLD CHECK` report
  ("Unclosed quoted value").
- Where a property holds several records (Prop_Details), records are separated
  by a semicolon that is outside quotes.

The same text may pass through more than one layer. A world file stores a
property text inside XML, and `.ini` files (lore, local lists, glossary-style
files) are read as Java properties, which adds its own backslash escaping and
`\` line continuation. Do not copy an example from one place to another without
checking which layer applies. Examples in this chapter are the property text as
typed into the builder, unless a code block says `ini`.

### Stable IDs for saved presentation state

Several presentation properties remember, per character (or per profile), that
something has already happened: a first-entry message shown, a clue found, a
hidden thing discovered, a language exposure counted. They key that memory on a
stable source ID, not on the item's name or the room's prose.

- Give the property an explicit `ID=name` (for example `ID=museum-shrine`).
  Renaming the host or rewriting its text then keeps the saved state.
- Without `ID`, rooms are identified by their room ID, areas by name, and
  anything else by a generated `AUTO:` identity that is written into a
  `Prop_AuthorIdentity` effect on the host the first time it is needed.
- When migrating content whose identity changed, `ALIASES=old-id,other-old-id`
  and `LEGACYKEYS=old-token` make the new source recognise the older markers.
  Prop_Details, Prop_Senses, Prop_FirstEntry, Prop_ShowWhen and
  Prop_LanguageImmersion accept `ID`, `ALIASES` and `LEGACYKEYS`.
- `WORLD CHECK ONLY=IDS` reports duplicate explicit IDs ("copied sources share
  saved state"). Give a different ID to each separate puzzle.

See [authoring identities, checks and recovery](../authoring-reliability.md) for
the full story.

### Where personal presentation state is saved

The state a character accumulates (journal, lore read markers, map reveals and
marks, automap size, personal colour overrides, language exposure counts) lives
in one JSON record on the character, a `Prop_StoryRecords` effect. Lore unlocks,
first-entry markers and clue markers are tattoos on the character (or on the
profile when profile scope is chosen). All of it is campaign state: it is
written by `SAVE` into the campaign, never into the world files. The
definitions that produce it (lore files, text files, glossary, dictionaries)
are world files. See [Persistence notes](#persistence-notes).

## Text is rendered for a viewer

### The VARIES envelope

A conditional text must **start** with the literal characters `<VARIES>` (no
leading space or newline). Everything after it is scanned for tags. Text that
does not start with `<VARIES>` is shown as typed, except that `<CALL ...>` tags
are still expanded.

```text
<VARIES><IFVAR BRIDGE=1>A repaired bridge spans the stream.</IFVAR><ELSE>Only broken ropes remain.</ELSE></VARIES>
```

A closing `</VARIES>` is optional; if present, it is removed from the output.

Text without any `<` is never examined, so ordinary text costs nothing.
Unknown tags (for example `<CUSTOM>`) are left in the output untouched, so
MXP-style markup written with `^<` and `^>` and other angle-bracket text are not
damaged. The depth of nested tags is limited to 32.

Source: `StoryText.render`, `StoryText.parse`.

### How a tag block is read

A block is an opening tag, content and the matching closing tag with the same
name, `<NAME args>content</NAME>`. The engine:

1. Finds the next known tag.
2. Finds its matching closing tag, counting nested tags **of the same name**.
   Closing tags are matched without regard to case, but write tags in capitals
   (CHECKTEXT warns otherwise).
3. Tests the condition. If true, the content is parsed recursively and
   appended.
4. Remembers the result for a following `<ELSE>`.

Blocks of different names can nest freely. Identical names nest correctly too
(`<IFVAR A><IFVAR B>...</IFVAR></IFVAR>`).

Arguments are written in the opening tag after the name and a space. The
arguments end at the first `>`. A condition that needs a greater-than or
less-than sign must write `&gt;` and `&lt;` (the engine restores them), or use
the word forms `GE`, `GT`, `LE`, `LT` of IFVAR.

### ELSE

`<ELSE>...</ELSE>` is true when the **previous sibling block at the same
level** was false. It only looks one block back, and it records its own result,
so a second `<ELSE>` in a row inverts again:

```text
<VARIES><NIGHT>It is dark.</NIGHT><ELSE>A bright day.</ELSE></VARIES>
```

An `<ELSE>` with no earlier sibling is treated as following a true block, so it
shows nothing. For three or more alternatives, nest the later ones inside an
earlier ELSE:

```text
<VARIES><DAWN>The sky pales.</DAWN><ELSE><DAY>Full sun.</DAY><ELSE><DUSK>Red light.</DUSK><ELSE>Stars.</ELSE></ELSE></ELSE></VARIES>
```

Text that sits between blocks (outside any tag) is always shown.

### Tag reference

All of the tags below work in every surface listed above. A tag that tests the
"viewer" uses the character looking; one that tests the "room" uses the room of
the text (for an item or creature, the room it is in; for an area description,
the viewer's room).

#### Time of day

| Tag | True when |
| --- | --- |
| `DAWN`, `DAY`, `DUSK`, `NIGHT` | The area's clock is in that time of day. `DAYTIME` and `NIGHTTIME` are synonyms of `DAY` and `NIGHT`. |

#### Season

| Tag | True when |
| --- | --- |
| `SPRING`, `SUMMER`, `FALL`, `WINTER` | The area's season. For an area with a custom calendar it is the built-in season that the calendar's current named season counts as. |
| `<the Burn>...</the Burn>` | Any season name defined by the area's calendar (see [chapter 10](10-world-simulation.md)). Named-season blocks are resolved first, before the other tags, and may contain spaces. |

#### Weather

The weather is the area's weather, so a text inside a cave still "knows" it is
raining outside.

| Tag | True when |
| --- | --- |
| `CLEAR`, `CLOUDY`, `WINDY`, `RAIN`, `THUNDERSTORM`, `SNOW`, `HAIL`, `HEATWAVE`, `SLEET`, `COLD`, `DROUGHT`, `BLIZZARD`, `DUSTSTORM`, `FOG` | The area's current weather type. (`COLD` is the winter-cold weather.) |

#### Tides

| Tag | True when |
| --- | --- |
| `HIGHTIDE`, `LOWTIDE` | The room's tide is high or low. See [chapter 10](10-world-simulation.md). |

#### The viewer

| Tag | True when |
| --- | --- |
| `VISITED` | The viewer has visited the room. It is already true on the arrival look (the visit is recorded before the look). |
| `FIRSTVISIT` | This is the viewer's first entry to the room, and stays true until their next move (so it is true for the whole arrival look). |
| `INCOMBAT` | The viewer is in combat. |
| `WANTED` | The viewer is wanted (the zapper mask `+WANTED`). |
| `SWIMMING`, `FLYING`, `CLIMBING`, `FALLING`, `SITTING`, `CRAWLING`, `INVISIBLE`, `HIDDEN` | The viewer's disposition has that bit. `CRAWLING` and `SITTING` test the same bit. |
| `MASK1`, `MASK2`, ... (or `MASK`) | The viewer passes the zapper mask in the tag's arguments. |
| `PARTYTATTOO name` | Any character in the viewer's group has that tattoo. |

#### The room

| Tag | True when |
| --- | --- |
| `DARK` | The room is dark (the room's darkness; independent of the viewer's darkvision). |
| `LIT` | The room is not dark. |
| `INDOORS`, `OUTDOORS` | The room's domain is indoors or outdoors. |
| `FROMNORTH`, `FROMSOUTH`, `FROMEAST`, `FROMWEST`, `FROMUP`, `FROMDOWN`, `FROMNORTHEAST`, `FROMNORTHWEST`, `FROMSOUTHEAST`, `FROMSOUTHWEST`, `FROMGATE` | The viewer arrived from that direction (the room before the move lies that way). |
| `FROMUNKNOWN` (and the historical spelling `FROMUNKOWN`) | The viewer has no previous room, or it is not next to this one (a teleport, a login, a recall). |

#### Calendar and moon

| Tag | True when |
| --- | --- |
| `FULLMOON`, `NEWMOON`, `WAXING`, `WANING` | The moon phase at the room. |
| `HOUR 2-4` | The area clock's hour of day is in the list. Ranges and single numbers, comma separated: `<HOUR 2-4,22>`. |
| `MONTH name` | The current month by name, or by number from 1 (ranges allowed): `<MONTH 11-12>`. |
| `HOLIDAY name` | The named holiday is on. See [chapter 10](10-world-simulation.md). |

#### World state, quests and the party

| Tag | True when |
| --- | --- |
| `IFVAR name` | The script variable is set and is not empty, `0` or `false`. |
| `IFVAR name=value` | The variable equals the value. Also `==`, `!=`, `>=`, `<=`, `>`, `<`. |
| `IFVAR name GE 3` | Word comparators: `EQ`, `NE`, `LT`, `LE`, `GT`, `GE`. |
| `VAR ...` | An alias of `IFVAR`. |
| `QUESTWON "quest name"` | The viewer has won the quest. |
| `QUESTRUNNING "quest name"` | The quest is running (not specific to the viewer). |
| `QUESTSTEP "quest name" n` | The quest is running and at step `n`. |

Quote quest names that contain spaces. `IFVAR` names may contain letters,
digits and `_ . : -`. The comparison is numeric when both sides are numbers,
otherwise text, using the same comparison as MOBPROG tests.

**Which variable does IFVAR read?** It reads the `*` script scope: the first
variable of that name found on **any** host (a variable set with
`MPSETVAR WORLD BRIDGE 1`, a character's own `MPSETVAR $n BRIDGE 1`, a quest or
room variable). There is no per-viewer lookup. For text that must depend on one
character's own variable, use a tattoo and `MASKn` (below), or a
`PARTYTATTOO`. Clue options `SETVAR=` write a variable on the entering
character's name, so `IFVAR` finds those too; choose distinctive names.

#### MASKn: any zapper mask

```text
<VARIES><MASK1 +RACE Elf>You belong here.</MASK1><ELSE>You are a stranger.</ELSE></VARIES>
<VARIES><MASK1 -IF "VAR(* BRIDGE == 1)">The bridge is up.</MASK1></VARIES>
<VARIES><MASK1 -TATTOO +SAW_THRONE>You remember the throne.</MASK1></VARIES>
```

The mask is any [zapper mask](24-behaviors-and-masks.md). Gotchas:

- Masks that contain `>` or `<` must write `&gt;` / `&lt;`.
- A lone `+IF "condition"` always passes. To test a condition, use
  `-IF "condition"` (the minus form fails when the condition is false).
- Masks test the viewer, not the host.
- `MASK`, `MASK1`, `MASK2`, ... are all the same; the digit only separates
  blocks you want to write next to each other.

### CALL: text computed by a script function

`<CALL function args>` is replaced by the value returned by a `FUNCTION_PROG`
(MOBPROG) or JavaScript function in the scripts of the object that **holds**
the text (the room, item, creature or area). `$n` inside the function is the
viewer.

```text
The rope runs into the dark; the water is about <CALL waterdepth> feet below.
```

with, in the room's script:

```text
FUNCTION_PROG waterdepth
if var(* BUCKETS >= 3)
  return 15
else
  return 30
endif
~
```

(The details of `FUNCTION_PROG` and `RETURN` are in
[the MOBPROG guide](04-mobprog.md).) Limits and rules:

- `<CALL ...>` also works in text that is not a VARIES text.
- Results are cached for one look, per viewer and host, so the same call twice in
  a description runs the function once.
- At most 32 calls are made in one look; the rest are replaced by nothing. A
  call that is already running (recursion) returns nothing.
- JavaScript retains its normal time limit (`JSTIMELIMIT`).
- A call inside a branch that is not shown is not made.
- Keep functions free of side effects. Two viewers rendering the same text, or
  one viewer rendering it twice, must not change the world.

### Legacy tags

Everything that worked before CoffeeSUD's additions still works: weather, season
and time-of-day tags, `VISITED`, direction tags (`FROMxxx`), disposition tags
and `MASKn`. The additions are `IFVAR`/`VAR`, the quest tags, `PARTYTATTOO`,
`HOLIDAY`, `WANTED`, `INCOMBAT`, `DARK`/`LIT`, `INDOORS`/`OUTDOORS`, `HOUR`,
`MONTH`, the moon tags, `FIRSTVISIT`, `FROMUNKNOWN` and `CALL`. In CoffeeSUD,
viewer-aware text also now works on items, creatures, exits and areas, not only
rooms.

### Previewing and checking

```text
PREVIEW night winter snow
PREVIEW from north visited
PREVIEW dusk as Bob var BRIDGE=1
CHECKTEXT
CHECKTEXT area
CHECKTEXT world
```

`PREVIEW` shows the current room's title and description as they would read
under the conditions given and changes nothing: a time of day (`DAY`, `NIGHT`,
`DAWN`, `DUSK`), a season, a weather (`RAIN`, `SNOW`, `CLEAR`, `FOG`, ...),
`FROM direction` or `FROM UNKNOWN`, `VISITED` or `FIRSTVISIT`, `AS character`
(a creature in the room, or a player, for MASK parts and dispositions) and
`VAR NAME=VALUE` (a script variable of the reader, restored afterwards). Only
rooms vary under `PREVIEW`; `PREVIEW ITEM` and `PREVIEW MOB` explain that.

`CHECKTEXT` (default scope `ROOM`; also `AREA`, and `WORLD` or `ALL`) lists
what would print as text instead of varying: tags in a text that does not
start with `<VARIES>`, unknown tags in a VARIES text (for example `<nigth>`),
tags in small letters, tags opened and not closed (or closed and not opened),
a `MASK` containing `>`, a colour code left open at the end, and problems with
`^<LANG` tags. It checks the room, its exits, items and creatures (and what
creatures carry), and `FILE=` readables. Both commands require the CMDROOMS
security flag (always held in builder mode).

`AHELP VARIES` lists the tags in-game; `AHELP PROP_DETAILS`, `PROP_SENSES`
and the other property help entries list options.

### Worked example: a bridge that changes

A quest or lever sets a world variable; the description reacts.

```text
MODIFY ROOM DESCRIPTION
<VARIES><IFVAR BRIDGE=1>A new plank bridge spans the gorge.</IFVAR><ELSE>Charred ropes hang over the gorge.</ELSE></VARIES> A cold wind pulls at your clothes.
```

In the carpenter's quest or script:

```text
MPSETVAR WORLD BRIDGE 1
```

Check with `PREVIEW var BRIDGE=1`, then `PREVIEW var BRIDGE=0`. Save a game with
the bridge built and restore it: `WORLD` variables are campaign state, so the
bridge stays built; a new game starts with the ropes.

### Worked example: a room that depends on how you arrived

```text
<VARIES><FIRSTVISIT>The torches flare as one, and the empty throne seems to turn toward you.</FIRSTVISIT><ELSE>The throne stands empty.</ELSE></VARIES> <MASK1 -TATTOO +SAW_PORTRAIT>You recall the portrait in the hall.</MASK1> <FROMDOWN>Steps spiral away behind you.</FROMDOWN>
```

`FIRSTVISIT` is only true on the first arrival look. For text that should be
remembered across the whole game, use Prop_FirstEntry (next section) so the
once-only marker is saved.
