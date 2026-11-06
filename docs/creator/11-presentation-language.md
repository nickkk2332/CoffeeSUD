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

## First entry, clues and sensory actions

These properties are attached with `CREATE PROPERTY`-style editing in the
builder (the `Properties` field of a room, item, creature or area; see
[chapter 23](23-properties-reference.md)) or in a world-building script
(`host.addNonUninvokableEffect(ability)` with `setMiscText`). Each one has an
in-game help entry (`AHELP PROP_FIRSTENTRY`, `PROP_DETAILS`, `PROP_SENSES`,
`PROP_DARKDESCRIPTION`, `PROP_SHOWWHEN`) that lists its options.

### Prop_FirstEntry

Source: [Prop_FirstEntry](../../CoffeeMud/com/planet_ink/coffee_mud/Abilities/Properties/Prop_FirstEntry.java).
Targets: rooms and areas.

A once-only line shown when a player character enters the room (or any room of
the area). Unlike the `FIRSTVISIT` tag, which is only true for one look, the
marker is saved, so the message is never repeated for that character.

| Option | Meaning |
| --- | --- |
| `TEXT="..."` | The message. VARIES and `<CALL>` are allowed. Colour codes allowed. |
| `ONCE=CHARACTER` or `ONCE=PROFILE` | Whose memory holds the marker. `CHARACTER` is the default. `PROFILE` shares it across every character of the profile. |
| `ID=name` | Stable identity of the marker (recommended). |
| `TATTOO=name` | A tattoo given to the character on first entry (scripts and masks can test it). |
| `SETVAR=NAME=value` | A script variable written on the entering character (a literal value). |
| `ALIASES`, `LEGACYKEYS` | Migration of older markers. |

It triggers on an ordinary entry or a recall into the room, only for player
characters.

```text
ID=throne-room TEXT="^xThe torches flare as one, and the empty throne seems to turn toward you.^N" TATTOO=SAW_THRONE SETVAR=THRONE=1 ONCE=PROFILE
```

The message is printed in addition to the room look. Because text with a tattoo
or variable is a state change, it is the right place for "first time" effects;
`<FIRSTVISIT>` is the right tool for text that is merely different the first
time you look.

### Prop_Details

Source: [Prop_Details](../../CoffeeMud/com/planet_ink/coffee_mud/Abilities/Properties/Prop_Details.java),
dispatch in [Scenery](../../CoffeeMud/com/planet_ink/coffee_mud/core/Scenery.java).
Targets: rooms, items, creatures.

Prop_Details gives a room (or object) extra things to look at, without creating
an item for each one. A detail is a set of keywords and a description:

```text
fountain,basin=A cracked marble basin. HIGHLIGHT;ledger,red ledger=EXAMINEONLY An entry is circled. READ="Vault, third bell." TATTOO=CLUE_VAULT SETVAR=LEDGER=1 MASK="-INT +8"
```

The text is a list of records separated by `;`. A record is
`keyword,keyword,...=description options`. The description is the text that is
left once the options are removed.

| Option | Meaning |
| --- | --- |
| `EXAMINEONLY` (flag) | Only `EXAMINE` shows this detail; `LOOK` and `READ` skip it. |
| `READ="text"` | Text shown by `READ keyword`. A detail with no `READ` text is not shown by `READ`. |
| `HIGHLIGHT` (flag) | Marks the detail's keywords in the room description (the same `^H` highlight that hidden items get). |
| `MASK="zapper"` | The detail is available only to viewers who pass the mask. |
| `TATTOO=name` | Given to the viewer the first time they see the detail. |
| `SETVAR=NAME=value` | A script variable written on the viewer the first time. |
| `ID=name` | Stable identity for the clue marker (otherwise the keyword list is the identity). |
| `ALIASES`, `LEGACYKEYS` | Migration. |

How it is used:

- `LOOK keyword`, `EXAMINE keyword` and `READ keyword` try real objects first;
  only when none matches do they look through the details of the room, the
  items there, the creatures there and the items you carry. A keyword must
  match a whole keyword of a record, ignoring case (`LOOK AT` / `LOOK THE` is
  understood). The details of a room are unavailable to a viewer who cannot
  see the room (it is dark and they have no light or darkvision).
- `LOOK keyword ON object` limits the search to one host.
- A detail on a creature or item is available only while that host can be seen,
  is not inside a closed container, and passes any Prop_ShowWhen on it.
- The clue options (`TATTOO`, `SETVAR`) run once per character and detail,
  while the description is shown every time.
- A matching `LOOK_PROG` on the host receives the matched keyword in `$g`, so a
  script can react to "looked at the runes".
- The description may contain VARIES and `<CALL>`.

Gotchas:

- A bare `;` ends a record. Rewrite the sentence (comma, dash, full stop). Do
  not rely on quoting the whole description to hide a semicolon: option parsing
  keeps the quote marks in the text it leaves (unverified in play; test it).
- Details are not objects. They cannot be taken, moved, attacked or counted.
  When the player must carry, open or fight it, make a real item or creature.
- `WORLD CHECK ONLY=IDS` and `PROPCHECK` validate options; `CHECKTEXT` checks
  the VARIES text of room descriptions but not text inside a property.

### Prop_Senses and the sense commands

Source: [Prop_Senses](../../CoffeeMud/com/planet_ink/coffee_mud/Abilities/Properties/Prop_Senses.java).
Targets: rooms, items, creatures, exits.

```text
SOUND="Water drips.|A pebble falls." TOUCH="Nine studs; the third is loose." TASTE="Salt." CHANCE=100 TATTOO=FOUND_STUD
```

| Option | Meaning |
| --- | --- |
| `SOUND="..."` | Text for `LISTEN`. |
| `TOUCH="..."` | Text for `TOUCH` / `FEEL`. |
| `TASTE="..."` | Text for `TASTE`. |
| `CHANCE=n` | Percent chance (default 100) that the sense produces text. When the roll fails the command ends with no output. |
| `TATTOO=name`, `SETVAR=NAME=value` | First-use clue, tracked separately for each sense. |
| `ID=name`, `ALIASES`, `LEGACYKEYS` | Stable identity. |

Within a value, `|` separates alternatives; one is chosen at random each time.
Values can contain VARIES. Quote values that contain spaces or semicolons.

The commands:

| Command | Behaviour |
| --- | --- |
| `LISTEN [target]` | Needs hearing. Uses the target's Prop_Senses `SOUND`; else the sound lines of an Emoter or Sounder on the target (for a room, also on its area); else a learned Thief Listen skill; else "You hear nothing unusual." |
| `TOUCH target` / `FEEL target` | Waypoint use first (TOUCH still attunes waypoints); then Prop_Senses `TOUCH`; then a script `USE_PROG` or a TOUCH combination; finally the stock `TOUCH` social. `TOUCH` with no target touches any waypoints in the room. |
| `TASTE target` | Prop_Senses `TASTE` only; else "You taste nothing unusual." |
| `SNIFF` | Unchanged; uses `Prop_Smell` ([chapter 23](23-properties-reference.md)). |

The target may be nothing (the room), `HERE`/`ROOM`, a direction (the exit), or
an item or creature in the room or carried. These commands work in the dark.
A target hidden from the viewer by Prop_ShowWhen is treated as absent
(`TOUCH` answers "You cannot find that here.").

Use senses for atmosphere and hints (an owl, a loose stud, a smell of ash).
Because a failed `CHANCE` roll is silent, do not make a puzzle depend on one
roll; keep `CHANCE=100` for clues.

### Prop_DarkDescription: dark rooms

Source: [Prop_DarkDescription](../../CoffeeMud/com/planet_ink/coffee_mud/Abilities/Properties/Prop_DarkDescription.java) and
[Presentation.dark](../../CoffeeMud/com/planet_ink/coffee_mud/core/Presentation.java).
Targets: rooms.

When a viewer cannot see a dark room, the look normally says "You can't see
anything!". With Prop_DarkDescription the room supplies a safe title and
description instead; the contents of the room stay hidden, and exits and
senses behave as normal.

```text
TITLE="Somewhere dark" DESC="Cold water laps at your knees."
```

Defaults: `TITLE` is "Somewhere dark", `DESC` is empty. Both accept VARIES.

An alternative needs no property: write the room's description with `DARK` and
`LIT` blocks.

```text
<VARIES><DARK>You feel cold walls on both sides.</DARK><LIT>Carved reliefs line a narrow gallery.</LIT></VARIES>
```

If the description starts with `<VARIES>` and contains `<DARK>`, an unseen
viewer gets the rendered description (without a title) as the dark fallback.
`DARK` and `LIT` test the room's own darkness, not the viewer's darkvision, so
a viewer with darkvision reads the lit text even though the room is dark.

### Prop_ShowWhen: things only some viewers see

Source: [Prop_ShowWhen](../../CoffeeMud/com/planet_ink/coffee_mud/Abilities/Properties/Prop_ShowWhen.java) and
[ViewRules](../../CoffeeMud/com/planet_ink/coffee_mud/core/ViewRules.java).
Targets: items, creatures, exits, rooms.

Prop_ShowWhen hides its host from viewers who do not satisfy a condition:

```text
MASK="-TATTOO +SPIRITSIGHT" WHEN="IFVAR GHOSTS=1" HIDEMODE=UNSEEN
```

| Option | Meaning |
| --- | --- |
| `MASK="zapper"` | The viewer must pass this mask. |
| `WHEN="..."` | A further condition: a VARIES tag with its argument (`IFVAR GHOSTS=1`, `NIGHT`, `QUESTWON "The Seer"`), or a zapper mask. |
| `CHECK=VIEWER` (default) | Whose mask is tested: the viewer. |
| `CHECK=ROOMPLAYERS` | Visible if any player character in the host's room passes. |
| `CHECK=ANYPLAYER` | Visible if any player character anywhere passes. |
| `CHECK=HOST` | The host is tested instead (a creature's own mask). |
| `HIDEMODE=UNSEEN` (default) | Visible only while the condition holds. |
| `HIDEMODE=HIDDEN` | Not seen until the character finds it with `SEARCH`; once found it stays visible to that character even when the condition later fails. |
| `ID=name`, `ALIASES`, `LEGACYKEYS` | Stable identity of the discovery marker. |

Until the condition passes, the host cannot be seen, listed, targeted, handled,
attacked or (for an exit) crossed: actions aimed at it fail with "You cannot
find that here." Builders with the SYSOPMSGS attribute (builder mode) see
everything. Maps and the map renderer honour it for rooms and exits.

Prop_ShowWhen is not a purely visual filter: test `LOOK`, `GET`, targeting,
map and interaction paths when you add it to something. A hidden creature that
is already fighting a party member keeps fighting; use it for scenery, clues,
doors and ghosts, not as an invisibility spell.

### Worked example: a crypt with clues

Goal: a crypt whose runes can be read, whose vault door can be felt in the dark,
and whose ghosts only appear after the player drinks the seer's tea (which
grants the tattoo `SPIRITSIGHT`).

On the crypt room:

```text
Prop_Details:     inscription,runes=Ancient letters spiral around a seal. HIGHLIGHT READ="Beneath the ninth bell the vault opens." ID=crypt-runes TATTOO=READ_RUNES SETVAR=READ_RUNES=1
Prop_Senses:      ID=crypt-air SOUND="Water drips.|Something shifts in the dark." TOUCH="Cold stone; a ring of nine studs." CHANCE=100
Prop_DarkDescription: TITLE="The crypt, unlit" DESC="Cold air and the echo of your own steps."
Prop_FirstEntry:  ID=crypt-first TEXT="The air tastes of old smoke." ONCE=CHARACTER
```

The crypt's description, with a clue that appears after the runes are read:

```text
<VARIES>Pillars march into the gloom. <MASK1 -TATTOO +READ_RUNES>You know what the nine studs on the vault door are for.</MASK1></VARIES>
```

The tattoo `READ_RUNES` was given to the character who read the runes, so the
sentence appears for that character only. (`<IFVAR READ_RUNES>` would also work,
but it reads the first `READ_RUNES` variable found on any host, so once one
character has read the runes it is true for every character.)

On the ghost (a creature), so that only a player who has the tea sees it:

```text
Prop_ShowWhen: MASK="-TATTOO +SPIRITSIGHT" ID=crypt-ghost
```

Test as a fresh character: `LOOK`, `LOOK runes`, `READ runes`, `LISTEN`, `FEEL`
(in the dark as well as lit). The ghost does not appear until the tattoo is
given (with the default `HIDEMODE=UNSEEN` no `SEARCH` is needed; after the
tattoo the ghost is simply there). Test with a second character that has not
read the runes: the clue sentence must not appear for them.

## Lore, journals and opening narration

### The lore codex

Source: [LoreBook](../../CoffeeMud/com/planet_ink/coffee_mud/core/LoreBook.java),
[Lore command](../../CoffeeMud/com/planet_ink/coffee_mud/Commands/Lore.java),
[Prop_Lore](../../CoffeeMud/com/planet_ink/coffee_mud/Abilities/Properties/Prop_Lore.java).

The codex is a reference the player fills as they learn. A lore entry is
defined in the world and unlocked per character (or per profile); nothing about
an entry is visible until it is unlocked.

**Defining entries.** Put UTF-8 files named `*.ini` in `world/lore/` (not in
subfolders). Each line is a Java-properties entry:

```ini
SUNDERING=The Sundering|History|^{LORE}The kingdom fell beneath the sea.^N
BESTIARY=The Deep Ones|Creatures|FILE=deep-ones.txt
COAST=The Drowned Coast|History|The museum was built after the sea took the old harbour.
```

- The key (`SUNDERING`) is case-insensitive and is stored upper case. Keep keys
  unique across all files (`WORLD CHECK ONLY=LORE` reports duplicates; if two
  files define a key, the later file by name wins).
- The value is `title|category|text`. The text may contain `|` (only the first
  two pipes separate fields). A missing category is `Lore`.
- The text is rendered for the reader, so it can use VARIES, `<CALL>`, colour
  codes and aliases.
- `FILE=name.txt` as the text field reads the entry's body from a file under
  `world/lore/`. A line that is only `KEY=FILE=name.txt` makes the key the title
  and `Lore` the category. Paths that leave `world/lore/` are refused.
- Because the files are read as Java properties, a backslash at the end of a
  line continues it, and `\n`, `\t` and `\\` are escapes. Use `FILE=` for long
  entries instead of long continuation lines.
- The files are read each time the codex is used (there is no cache), so an
  edit shows at once. After editing during a game, `WORLD RELOAD ASSETS`
  acknowledges the changed asset files.

**Unlocking entries.** Three ways, all of which store a tattoo `LORE_KEY` on the
character:

1. A script: `MPLORE $n ADD SUNDERING` (character) or
   `MPLORE $n ADD SUNDERING PROFILE` (shared by every character of the
   profile). See [the MOBPROG command reference](27-mobprog-commands.md).
2. A property, Prop_Lore, on an item, room or creature:

   ```text
   ENTRY=SUNDERING ON=READ MSG="You decipher the old account." SCOPE=CHAR
   ```

   | Option | Meaning |
   | --- | --- |
   | `ENTRY=key` | The lore key to unlock (required). |
   | `ON=` | `READ` (default), `GET`, `EXAMINE`, `ENTER`, `KILL` or `DEATH`. `READ`/`GET`/`EXAMINE`/`ENTER` fire when the character does that to the host. `KILL` fires when the host dies and credits its killer. `DEATH` fires when the host (a player character) dies and credits that character. |
   | `MSG="..."` | A line shown when the entry is newly unlocked. |
   | `SCOPE=CHAR` (default) or `PROFILE` | Where the tattoo is stored. Aliases: `CHARACTER`. |

   Prop_Lore targets items, rooms and creatures, and fires only for player
   characters. The message appears only the first time.
3. A quest step: `SET LORE key` in the quest script unlocks the entry for the
   quest's participants for that step, including characters who accept
   while the step is current. See [chapter 8](08-quests.md).

A `GenTome` page can also carry `LORE=key` (see [chapter 22](22-items.md)); older
`LORE:KEY` tattoos from tome pages are still recognised.

**The command.** `LORE` and `CODEX` are the same command.

```text
LORE                    categories with counts
LORE History            entries in a category (a star marks unread entries)
LORE SUNDERING          read an entry, by key
LORE The Sundering      ... or by full title
LORE NEW                unread entries
```

Reading an entry shows its title in the colour alias `LORE` (if the world
defines one; see [Colours](#colours-and-named-aliases)) followed by its category and text, and
marks it read. With nothing matching the player sees "No unlocked lore
matches." Locked entries are never listed.

**Gated help entries.** A help entry (in `world/vocab/help/*.ini`) may begin
with metadata that is stripped before display:

```text
<MASK=-TATTOO +LORE_SUNDERING><LOCKED=You have not yet learned this.><CATEGORY=History>The full text...
```

When the viewer fails the mask the help shows the `LOCKED` teaser, or nothing
if there is none.

**Thief skill clash.** The thief skill `Thief_Lore` also uses the word `LORE`.
The command defined here is the one players reach with `LORE`; do not give the
same word a second meaning in your own content.

### The personal journal

Source: [Chronicle](../../CoffeeMud/com/planet_ink/coffee_mud/core/Chronicle.java),
[Journal command](../../CoffeeMud/com/planet_ink/coffee_mud/Commands/Journal.java).

The journal is the player's saved, dated notebook. It is not the same thing as
a `GenJournal` or `GenBook` item (a board in the world), the quest journal
(`QUESTS`, see [chapter 8](08-quests.md)) or the builder's `NOTES` command (which
is the playtest-notes tool).

```text
JOURNAL                         all entries, newest first
JOURNAL Clues                   only the category "Clues"
JOURNAL WRITE Remember the hermit.
JOURNAL SEARCH vault            entries whose text, category or source contains "vault"
JOURNAL DELETE 12               delete entry number 12
```

Each entry shows `#id [category] game date — text (source; location)`.
Entry numbers never change and are not reused after a deletion. Only the newest
300 entries are kept; the oldest is dropped when the 301st is added. `JOURNAL
WRITE` makes an entry in the category `Notes`.

**Writing entries from scripts.** `MPJOURNAL target [category] "text"`:

```text
MPJOURNAL $n Clues "The vault opens to the word Ashgrove."
MPJOURNAL $n "Remember the hermit."
```

With two arguments the category is `Notes`. The entry records the script's host
as its source, the character's location, and the game date of the character's
local clock. Quote the text. In a scene (`Scene` behavior), a `NOTE "text"`
step writes a `Rumors` entry for every player present; see
[chapter 6](06-npcs-dialogue.md).

**Automatic entries.** The engine can add entries for you. Set in
`coffeemud.ini` or a launch override (not `world.ini`; see
[where settings may be set](#where-presentation-settings-may-be-set)):

```ini
JOURNALAUTO=QUEST,LEVEL,AREA,LORE
JOURNALTALK=true
```

| Kind in `JOURNALAUTO` | Entry |
| --- | --- |
| `QUEST` | "Accepted quest: ..." and "Completed quest: ..." |
| `LEVEL` | "Reached level N." |
| `AREA` | "Discovered AREA NAME." (first entry to a new area) |
| `LORE` | "Learned TITLE." (when a lore entry is unlocked) |

`JOURNALAUTO` is empty by default. `JOURNALTALK=true` records the topics a
character learns from `[[topic]]` dialogue lines, once each, in category
`People`. Do not spam the journal: a repeated ambient bark should never write
an entry.

### Narration: story text from files

Source: [Narration](../../CoffeeMud/com/planet_ink/coffee_mud/core/Narration.java).

`MPNARRATE` shows a block of story text from a file, page by page, to a player,
the whole room or the whole area.

```text
MPNARRATE $n opening.txt#arrival PAUSE STYLE=LORE
MPNARRATE ROOM crypt.txt#awakening
MPNARRATE AREA bells.txt NOPAUSE
```

Syntax: `MPNARRATE target file[#section] [PAUSE|NOPAUSE] [STYLE=ALIAS]`.

- `target` is a character reference such as `$n`, or `ROOM` or `AREA` (relative
  to the script's host). Only player characters receive it.
- `file` is a file under `world/text/` (paths that escape that folder return
  nothing). With `#section` only the lines after a line that is exactly
  `[section]` (until the next `[...]` line) are used; without it the whole file
  is used with the section header lines removed.
- Blank lines separate pages (paragraphs).
- Script substitutions (`$n`, `$i`, ...) and VARIES apply to the text, for each
  viewer.
- `STYLE=NAME` wraps the text in the colour alias `NAME` (for example `LORE`).
- `NOPAUSE` is the default. With `PAUSE` each paragraph is printed and the
  player is prompted `[Enter to continue]` before the next.

```text
[arrival]
The guide sets a ledger on the desk.

'Some things here still remember the sea,' she says.
```

How `PAUSE` behaves in a single-player game:

- It uses asynchronous Enter callbacks. No game thread blocks. It takes no
  game turns.
- In real-time mode it holds the real-time clock (a pause) while any reader is
  on a page, and releases only its own hold when the last reader finishes. A
  pause the player set by hand is left in place.
- In turn-based mode nothing moves while the reader is paging anyway.
- A party member in the background (or a window that cannot open) receives
  the whole text at once with no hold. A character in an open window pages in
  that window.
- Starting a narration cancels a pending script menu for that character, and
  a menu replaces a pending narration; one input stream carries only one.
- Unread pages and the pause ownership are saved in the campaign and resume
  after a restore.

Script menus (`MPMENU`) and narration share input; do not start narration from a
tick-driven trigger that can fire while the player is in a menu. A narration
from a player-caused trigger (`GREET_PROG`, `ENTRY_PROG`, a `FUNCTION_PROG`
called from speech) is safe.

For the very first screens of a new game (the title and the prologue), see
[Title, prologue and character windows](#title-prologue-and-character-windows).

## Maps, ambience and interface

### The player map: MAP and AUTOMAP

Source: [PlayerMaps](../../CoffeeMud/com/planet_ink/coffee_mud/core/PlayerMaps.java),
[PlayerMap command](../../CoffeeMud/com/planet_ink/coffee_mud/Commands/PlayerMap.java),
[AutoMap command](../../CoffeeMud/com/planet_ink/coffee_mud/Commands/AutoMap.java).

Every character has a free map of what they know. It works indoors and
outdoors, honours unmappable rooms and areas, and never reveals rooms the
character has not learned about.

```text
MAP                     the map around you, at your automap radius
MAP 5                   radius 5 (1 to 12)
MAP AREA Old Vael       centred on the first known room of that area, radius 12
AUTOMAP                 toggle the automatic map after each look
AUTOMAP ON
AUTOMAP OFF
AUTOMAP SIZE 4          save your own radius (1 to 12)
```

- `MAP` is the player's map. `CARTOGRAPHY` invokes the paper-making Cartography
  skill, and `WORLD MAP` is the builder's diagnostic map with IDs and flags.
  They are different tools.
- The default radius is the saved `AUTOMAP SIZE`, otherwise half of the world's
  `AWARERANGE` (minimum 1). `AWARERANGE` is a world setting (shipped value 3).
- With `AUTOMAP` on, the map is printed after every room look. It is shown only
  when the player has not switched it off and either `AWARERANGE` is above 0 or
  the player has a saved size. A player can switch it on even when
  `AWARERANGE` is 0 (`AUTOMAP ON` then saves radius 3 unless a size is saved).
  `AUTOMAP` with no argument toggles. It is also a `CONFIG` option.
- Only the floor you stand on is drawn. Exits up and down appear in the legend
  as `<` and `>` with the destination's title, from your own floor.
- Rooms are drawn as `#` (indoors) or `o` (outdoors), `*` is you. Rooms you
  have only learned about are drawn dim (dark grey). Corridors are `|`, `-`,
  `/` and `\`.
- A map is limited to about 1000 rooms and the chosen radius.

**Fog of war.** `MAPFOG` (in `coffeemud.ini` or a launch override; see
[where settings may be set](#where-presentation-settings-may-be-set)) decides
which rooms are drawn:

| `MAPFOG` | Rooms drawn |
| --- | --- |
| `KNOWN` (default) | Rooms the character has visited, plus rooms revealed by map items or scripts. |
| `VISITED` | Only rooms visited. Revealed rooms stay off the map until entered. |
| `ALL` | Every mappable room (an author opt-in; spoils exploration). |

Whichever value is set, the room you stand in is always drawn, and rooms or
areas flagged unmappable (`Prop_RoomUnmappable`, the area's unmappable flag), rooms hidden by Prop_ShowWhen
and rooms whose glyph property says `HIDE` are never drawn. An exit that
Prop_ShowWhen hides from the viewer is not drawn either. Revealing a room never
records a visit: `VISITED` markers, `VISITED` tags and discovery experience are
unaffected.

### Prop_MapGlyph: room symbols and legends

Source: [Prop_MapGlyph](../../CoffeeMud/com/planet_ink/coffee_mud/Abilities/Properties/Prop_MapGlyph.java). Targets: rooms.

```text
CHAR=I COLOR=y LABEL="Inn" LEGEND
HIDE
```

| Option | Meaning |
| --- | --- |
| `CHAR=x` | The symbol (first character used). Default `#` indoors, `o` outdoors. |
| `COLOR=code` | A colour code letter (`y`) or code (`^y`). Default `w`. |
| `LABEL="text"` | The legend text. |
| `LEGEND` (flag) | Add this glyph and label to the legend. Without it the label is not listed. |
| `HIDE` (flag) | The room is never drawn on the player's map. |

Unvisited (revealed-only) rooms are drawn dark grey whatever the glyph colour.

### Map items: Prop_MapReveal

Source: [Prop_MapReveal](../../CoffeeMud/com/planet_ink/coffee_mud/Abilities/Properties/Prop_MapReveal.java). Targets: readable items.

Reading an item with Prop_MapReveal teaches the reader rooms and marks. The
knowledge is saved on the character.

```text
ROOMS=Coast#1-40,Coast#45 MARK=Coast#12:X:"Buried chest" COLOR=r
AREA="Old Vael"
```

| Option | Meaning |
| --- | --- |
| `ROOMS=` | Comma-separated room IDs, or ranges `Area#first-last` (at most 10 000 rooms per range). |
| `AREA="name"` | Every room of that area. |
| `MARK=room:G:"label"` | A labelled single-character glyph on a room. The label can be quoted or one word. Several `MARK=` may be given. |
| `COLOR=code` | Colour of the marks (default `r`). |

A marked room is also revealed. Marks appear in the map legend. Reading the item
again changes nothing that was already known. Use Prop_MapReveal on a plain
readable item; the stock map items (`BardMap`, `GenMap`) draw their own text
(see [chapter 22](22-items.md)).

### Ambient lines: Emoter and Sounder conditions

Emoter and Sounder (see [chapter 24](24-behaviors-and-masks.md)) produce ambient
lines. CoffeeSUD lets each line carry conditions, a weight, and a once-only flag:

```text
min=8 max=20 chance=40 RETRY NOREPEAT=2 QUIETAFTER=3;NIGHT SOUND an owl hoots.;IFVAR WOLVES=1 WEIGHT=3 ONCE SOUND wolves howl nearby.
```

**Per-line prefixes** come before the line's type word (`SIGHT`, `SOUND`,
`SMELL`, `SOCIAL`, `PUFF`, and the other type words of the behavior) and must be
written in capitals:

| Prefix | Meaning |
| --- | --- |
| `DAY`, `NIGHT`, `DAWN`, `DUSK` | Time of day. |
| `SPRING`, `SUMMER`, `FALL`, `WINTER` | Season. |
| `RAIN`, `SNOW`, `FOG` | Weather. |
| `INDOORS`, `OUTDOORS` | Room type. |
| `IFVAR name=value` | A world variable test; the expression is the next word with no spaces (`IFVAR WOLVES=1`, `IFVAR CHAPTER>=3`). |
| `MASK="zapper"` | The listener must pass the mask. |
| `WEIGHT=n` | Relative chance (1 to 10000, default 1). |
| `ONCE` | The line is heard at most once per character (saved). |

All the prefixes on a line must hold. A line whose first word is ordinary
mixed-case text ("Fog rolls in...") has no prefixes; only the capitalised
keywords above are read, so the old syntax is unchanged. The line separator is
`;` (or `/` when there is no `;`), so line text cannot contain it.

**First-field options** (the part before the first `;`):

| Option | Meaning |
| --- | --- |
| `RETRY` (flag) | If the chosen line is not eligible, pick again among the eligible lines. |
| `NOREPEAT=n` | The last `n` lines used in a room are not repeated while alternatives exist. |
| `QUIETAFTER=n` | Stay silent in a room while a player is in combat or being narrated to, and for `n` game ticks after reading a detail, a sense or a narration page. `0` (the default) never silences. Emoter only. |
| `MASK="zapper"` | A mask for the whole behavior. |

Notes:

- Lines are selected with the game's dice, by weight, among those whose
  conditions fit. Conditions are tested in the actual room; an area-level Emoter
  tests each room's own conditions and speaks only in rooms with players.
- If any line has a mask or `ONCE`, the line is delivered individually to the
  players in the room who qualify and can perceive it, and each one's `ONCE`
  marker is set; otherwise it is sent to the room.
- `LISTEN` uses the `SOUND` lines of a behavior on the target (or on the area
  for a room) and honours their conditions.
- Sounder bark tables (`IDLE:`, `GREET:` and the other events) accept the same
  prefixes: `IDLE: ONCE SOUND the bell tolls once.`
- Use random lines to enrich the scene. Critical directions or puzzle
  instructions need a reliable route (a detail, a sign, a script), not an
  ambient line the player may never see.

### Prompts and templates

The prompt, `world/score.txt`, `ROOMHEADER`, `ROOMFOOTER` and the window panel
text all use the same prompt-token engine (`CMLib.utensils().buildPrompt`), so
the tokens below work in all of them. Players set their own prompt with
`PROMPT`; `PROMPT DEFAULT` restores the default. `AHELP`/`HELP PROMPT` lists
the classic tokens (hit points `%h/%H`, mana `%m/%M`, moves `%v/%V`, room name
`%r`, area name `%z`, time `%t`/`%T`, weather `%@`, group `%j`, factions
`%f NAME`, stats `%-STAT NAME`, combat sections `%/` and `%\`, and so on).

CoffeeSUD adds these script and party tokens:

| Token | Value |
| --- | --- |
| `%{VAR name}` | The character's own script variable `name`; if empty, the first variable of that name on any host. |
| `%{VAR * name}` | Explicitly the first variable of that name on any host. |
| `%{VAR host name}` | The variable `name` of a named host (for example `WORLD`). |
| `%{PVAR name}` | Only the character's own variable. |
| `%{QVAR quest name}` | A quest-scoped variable. Quote a quest name with spaces: `%{QVAR "Ashes of Vael" STAGE}`. |
| `%{TIMEMODE}` | `REAL` or `TURN`. |
| `%{LEADER}` | The party leader's name. |

An unrecognised `%{...}` is printed as written. Colour codes work in all
templates.

**`world/score.txt`** adds lines to the plain `SCORE` command (after the
effects; not to `SCORE DETAILS` and the other forms). One template per row;
each row is rendered with VARIES first and then the tokens above; a row that
renders to nothing (only colour codes or blanks) is not shown.

```text
Chapter : %{VAR CHAPTER}
<VARIES><IFVAR CURSED=1>^rThe ash-mark burns on your palm.^N</IFVAR></VARIES>
```

**`ROOMHEADER` and `ROOMFOOTER`** (settings; see
[where settings may be set](#where-presentation-settings-may-be-set)). The
header replaces the first line of a room look, which is normally the room title
(in the `^O` room-title colour) followed by disposition notes such as
"(hidden)". The footer is appended after the room's contents. Both use the
template engine, so they can show the area, a danger marker, the time or a
story variable:

```ini
ROOMHEADER=^O%r^N ^w[%z]^N
ROOMFOOTER=Chapter %{VAR CHAPTER}
```

Empty values (the shipped defaults) keep the standard layout. Templates are
used only when the viewer can see the room, so a hidden room's title is not
leaked. A header replaces the disposition notes and the MXP room-name link; if
you want either, write it into the template. Check the result with ANSI off.

### Colours and named aliases

The colour codes are `^` followed by a letter: `^w` white, `^r` light red,
`^g` light green, `^y` yellow, `^b` light blue, `^p` light purple, `^c` cyan,
`^k` dark grey. The upper-case letter is the darker colour (`^R` maroon, `^G`
dark green, `^B` dark blue, `^P` dark purple, `^C` dark cyan, `^Y` dark yellow,
`^W` grey, `^K` black). `^N` returns to normal and `^?` to the previous colour. The game also has
semantic codes that a player can recolour with `COLORSET`: `^H` highlighted
text, `^O` room titles, `^L` room descriptions, `^J` weather, `^D`/`^d`
explored exits and doors, `^U`/`^u` unexplored ones, `^I` items, `^M` creatures,
`^E` emotes, `^T` says, `^S` spells, and the fight colours `^f`, `^e` and `^F`.
A 24-bit colour is written `^##RRGGBB`. `^!`, `^_` and `^/` are bold,
underline and italics. Several codes (`^x`, `^X`, `^Z`) are unused in
single player and a world may use them freely.

For readable, restyle-able colour, the world defines **named aliases** and text
uses them:

```ini
COLORALIASES=LORE=##C8A060,DANGER=LIGHTRED+BOLD,WHISPER=DARKGREY,MAGIC=LIGHTPURPLE
```

Then any text where colour codes work can say `^{LORE}The kingdom fell.^N`.

- Each alias is `NAME=value`, separated by commas. Names use letters, digits
  and `_`. A value is one or more parts joined with `+`: a colour name
  (`WHITE`, `LIGHTGREEN`, `LIGHTBLUE`, `LIGHTRED`, `YELLOW`, `LIGHTCYAN`,
  `LIGHTPURPLE`, `GREY`, `GREEN`, `BLUE`, `RED`, `BROWN`, `CYAN`, `PURPLE`,
  `DARKGREY`, `BLACK`), `BOLD`, `UNDERLINE`, `ITALICS`, a single code letter
  (`y` or `^y`) or a hex colour (`##C8A060`).
- Aliases expand before line wrapping and are stripped when the player's ANSI
  is off. An alias that is not defined expands to nothing (plain text).
- Players use `COLORSET ALIASES` to list the aliases and their values,
  `COLORSET LORE LIGHTGREEN` to override one for themselves (saved on the
  character) and `COLORSET LORE DEFAULT` to remove the override. `COLORSET
  DEFAULT` resets all colour preferences, aliases included. Only names the world
  defines can be overridden; `COLORSET` alone opens the stock colour menu.
- The engine itself uses `LORE` for lore entry titles and accepts any alias in
  MPNARRATE's `STYLE=`. A world that does not define `LORE` shows those
  uncoloured.

Choose colours that also read on a plain terminal, and test with ANSI off,
a narrow terminal and headless output.

## Title, prologue and character windows

### Title screen and game name

Source: [SUD.java](../../CoffeeMud/com/planet_ink/coffee_mud/application/SUD.java).
The launcher chapter ([chapter 18](18-installing-and-running.md)) describes the
saved-game menu in full; the authoring rules are:

- `GAMENAME` (a world setting; also in `coffeemud.ini`) is the game's name. The
  shipped value is `CoffeeSUD`.
- `world/text/title.txt` is printed before the saved-game menu. In it,
  `@MudInfo?NAME@` becomes the game name. Only the launcher's colour codes
  work here: `^w ^r ^g ^y ^b ^p ^c ^k` and their upper-case brighter forms,
  `^N` or `^?` to reset, and `^^` for a literal `^`. Aliases (`^{LORE}`) and
  the semantic codes (`^H`) are **not** available because the engine has not
  started. The screen always ends in the normal colour. It is not shown for a
  `CHECK=1` run. If the file exists, the engine does not also show the inherited
  `resources/text/intro.txt` picture.
- `world/text/prologue.txt` is shown for a new game (after choosing New, or
  with `RESTORE=NEW`; never in builder mode, never when restoring). Pages are
  separated by a line of three or more dashes (`---`); the launcher prints a
  page, then `[Enter] `, then the next. The same limited colour codes apply.

This is a launch presentation format. It cannot run NPC scripts or show
viewer-specific text, because no character exists yet. MPNARRATE's
`[section]`/blank-paragraph format is different and runs inside the game.
Keep the opening readable when both files are absent.

For a prologue that runs in the game (after the character exists, in the start
room), use a creation script. `CHARCREATIONSCRIPTS` is a world setting; an
`END:` script runs once when a new character has been made. Example
(an adaptation of the stock `ONCE_PROG` pattern, not a shipped file):

```ini
CHARCREATIONSCRIPTS=END:LOAD=progs/prologue.script
```

```text
ONCE_PROG
    MPNARRATE $i prologue.txt#intro PAUSE STYLE=LORE
~
```

with `world/text/prologue.txt` holding a `[intro]` section. Here `$i` is the
character being created (as in the stock `chardesc.script`). Check the order of
the prompts against your creation scripts; see [chapter 3](03-characters.md).

### Character window panels

Source: [WindowPanel](../../CoffeeMud/com/planet_ink/coffee_mud/core/WindowPanel.java),
[WindowsCmd](../../CoffeeMud/com/planet_ink/coffee_mud/Commands/WindowsCmd.java).

When the game has a display, every character other than the one you play in the
terminal can have a window of its own (`WINDOWS ON`; see
[chapter 31](31-saves-and-party.md)). Each window can show a side panel next to
its text, drawn again at every prompt (so after each command and each move).

```text
WINDOWS PANEL ON                 all three sections
WINDOWS PANEL OFF                no panel
WINDOWS PANEL STATUS,PARTY       chosen sections, in this order
WINDOWS PANEL                    show the current setting
```

`WINDOWPANEL=STATUS,MAP,PARTY` in `coffeemud.ini` (or a launch override) starts
a game with the panel on. Sections may be abbreviated (`STAT`, `PAR`). `ON`,
`ALL`, `1`, `YES` and `TRUE` mean all three; `OFF`, `0`, `NO`, `FALSE` or empty
mean none. A section that is not one of the three is refused. The `WINDOWS`
command is available only in single-player games with the profile system.

| Section | Contents |
| --- | --- |
| `STATUS` | `world/text/panel.txt`, one template per line, with the prompt tokens ([Prompts and templates](#prompts-and-templates)) and colour codes. Without the file: room name, area, then `HP`, `Mana` and `Moves` in the stock colours. |
| `MAP` | The player map ([The player map](#the-player-map-map-and-automap)) around the window's character, radius 3, honouring the same fog, glyph and marker rules as `MAP`. |
| `PARTY` | Each character of the party, in order, with a ten-segment hit-point bar and `hp/max`. Bars turn yellow below half and red below a quarter. |

```text
^w%r^N / %z
HP %h/%H   Moves %v/%V
Chapter %{VAR CHAPTER}
```

The panel is a view. Put durable state in script variables and quest records
and let the template read it; the renderer keeps nothing. Headless and
terminal play use the terminal, with no panel. Test with a second window and
confirm that a template does not name a hidden room (the room name token is
rendered for the window's own character).

### Where presentation settings may be set

Several presentation settings are read by the engine from the properties the
engine loads at start, but a world's `world.ini` accepts only an allow-list of
keys ([world-settings.md](world-settings.md)). The result:

| Setting | `world.ini`? | Where it goes |
| --- | --- | --- |
| `GAMENAME` | yes | `world.ini` or `coffeemud.ini` |
| `AWARERANGE` | yes | `world.ini` or `coffeemud.ini` |
| `CHARCREATIONSCRIPTS` | yes | `world.ini` or `coffeemud.ini` |
| `MAPFOG` | no | `coffeemud.ini` or a launch override |
| `JOURNALAUTO`, `JOURNALTALK` | no | `coffeemud.ini` or a launch override |
| `ROOMHEADER`, `ROOMFOOTER` | no | `coffeemud.ini` or a launch override |
| `COLORALIASES` | no | `coffeemud.ini` or a launch override |
| `WINDOWPANEL` | no | `coffeemud.ini` or a launch override (`WINDOWS PANEL` changes the running game) |
| `LISTFILE`, `COLORSCHEME`, `LANGUAGE`, `MAXLANGUAGES` | no (engine settings) | `coffeemud.ini` |

`WORLD SET` refuses the keys marked "no", and an unknown key in `world.ini` is
reported at start and ignored. Their files and templates (lore, text, panel,
score, glossary, dictionaries) are still world-owned, and `WORLD CHECK
ONLY=TEMPLATES` checks `ROOMHEADER` and `ROOMFOOTER` whatever file defined
them. A launch override is `KEY=value` on the command line:

```sh
CoffeeMud/sud.sh MAPFOG=VISITED ROOMFOOTER="Chapter %{VAR CHAPTER}"
```

(See [chapter 18](18-installing-and-running.md) for the launcher and its flags.)
A few older comments and the `WindowPanel` source comment say `world.ini` can
set `WINDOWPANEL`; the shipped help (`HELP WINDOWS`) and the allow-list are
right: it cannot. This is a known open item in the
[developer tools audit](../../audit/developer-tools-audit.md#open-configuration-gap).

## Languages are mechanics

Source: [Languages](../../CoffeeMud/com/planet_ink/coffee_mud/core/Languages.java),
[LanguageLexicon](../../CoffeeMud/com/planet_ink/coffee_mud/core/LanguageLexicon.java),
[GenLanguage](../../CoffeeMud/com/planet_ink/coffee_mud/Abilities/Languages/GenLanguage.java),
[LangToolsCmd](../../CoffeeMud/com/planet_ink/coffee_mud/Commands/LangToolsCmd.java).
`AHELP LANGUAGE_AUTHORING` summarises the authoring side.

In CoffeeSUD, language is a gameplay system, not decoration. Whether a
character understands speech, writing, an inscription or a creature depends on
what they know and carry. Everything below shares one comprehension check.

### The comprehension model

Each language is an ability (a `Language`), known at a proficiency from 0 to
100. A character speaks one language at a time (`SPEAK languagename`, or the
language chosen for them by a property); `LANGUAGES` (or `LANGS`) lists what
they know. How much of a language L a listener, reader or NPC understands is
the **best** of:

1. 100% for Common, unless the creature is made monolingual (below); 100% for
   each language a monolingual creature declares.
2. The proficiency of any language effect on the listener that translates L
   (a spell such as Speak with Animals, a `Prop_LangTranslator`, or a learned
   language).
3. The proficiency of any learned language ability that translates L.
4. Language effects on the room the listener is in.
5. Language effects on items the listener carries.

A language that merely **interprets** L contributes its own proficiency times
the stated percentage: a language at 50% with `INTERPRETS=Elvish(60)` gives
30% understanding of Elvish. Result: `UNDERSTANDS$(who language)` in a script is
this number.

What the number does:

- **Speech heard.** Speech needs to be heard (and spoken within earshot). At
  100% the listener reads the words as spoken. At 0% they get a scrambled
  rendering (`"?"` for Common) and the unknown-speech hooks run
  (`NOTUNDERSTOOD_PROG`, the `Prop_Monolingual` confused line). Between 0 and
  100 the speech is partly translated: each word is either understood or
  not, decided by a fixed hash of the language and the word, so a given word is
  always understood at a given proficiency and raising the proficiency only
  ever adds words. A non-Common language heard with some understanding also
  shows the listener a private line "... (translated from Elvish)", unless a
  translator they hold already prints its own line.
- **Speech reaching NPCs.** Creatures only react to what they understand:
  unintelligible speech never reaches `SPEECH_PROG`, MudChat or NPC services
  (orders, trade and the other dialogue paths use the same check).
- **Writing.** A readable item with a language effect, a `GenTome` page with
  `LANG=`, and an inline `^<LANG` tag show scrambled text to a reader who does
  not know the language, and a translation to one who does.
- **Sign language and semaphore** are *visual* languages: they follow sight,
  not hearing. A deaf character who can see the speaker understands them;
  unknown words show as `?`.

Learned proficiency is campaign state. Languages are learned by training
(`LANGCOST`, shipped `3 PRACTICE`), taught by NPCs, given by scripts, or picked
up by exposure. The number of languages a character can learn is limited by
their class's language allowance (the `MAXLANGUAGES` setting, shipped `3`, is
the stock value; `0` on a class means unlimited) plus any bonus granted to the
character or profile; `QUALIFY` shows how many more a character may learn.
Scripts and properties that grant a new language respect that limit unless they
say `FORCE`.

### Prop_Monolingual: a creature that knows one language

`Prop_Monolingual` (targets creatures) removes the automatic understanding of
Common.

```text
LANGUAGES=Orcish,Goblinese CONFUSED="<S-NAME> grunt(s) and shrug(s)."
```

| Option | Meaning |
| --- | --- |
| `LANGUAGES=a,b` | The languages the creature understands fully (default `Common`). |
| `CONFUSED="..."` | An emote the creature performs when it hears speech it cannot understand. It is a normal message: `<S-NAME>` is the creature. |

Learned languages, translators, and room and carried-item interpreters still
work, so a captured interpreter (`Prop_LangTranslator`) or a player who has
learned Orcish can bargain with the chieftain; everyone else gets shrugs. When
it is attached, a monolingual creature that speaks Common switches to its first
declared language, so its replies are in that language.

The same restriction is available on `Prop_LanguageSpeaker` with
`UNDERSTANDS=ONLY LANGUAGE=Orcish`.

### Prop_LanguageSpeaker and Prop_LangTranslator

`Prop_LanguageSpeaker` forces the language its host speaks (see
[chapter 23](23-properties-reference.md)):

```text
LANGUAGE=Dwarven players=true ; -NAMES +BOB
LANGUAGE=Orcish UNDERSTANDS=ONLY
```

Options: `LANGUAGE`, `PLAYERS` (also affects players), `NOMOBS`, `HOMEONLY` (only
creatures whose start area is the property's area), `UNDERSTANDS=ONLY`
(restricts comprehension, as above), and an optional zapper mask after a `;`.
On a creature it makes that creature speak it; on a room or area it applies to
those who enter or are born there; on an item, to those who pick it up.

`Prop_LangTranslator` (creatures, items, rooms) translates other languages
into Common for everyone in the room, or, with `NOTRANSLATE`, quietly gives the
holder understanding of the listed languages (optionally only for words
prefixed with a backquote, with percentages, and with `#PLAYERNAME` trusted
speakers). Examples: `100% Dwarven 50% Elvish`, `NOTRANSLATE 75% Dwarven`.
Both are covered in [chapter 23](23-properties-reference.md).

### Creating an invented language

Invented languages are generic language objects (`GenLanguage`), stored in the
world with the other custom objects (`custom/abilities/ID.cmare`).

```text
CREATE LANGUAGE Dunetongue
MODIFY LANGUAGE Dunetongue
```

`CREATE LANGUAGE ID` makes a new language (the ID cannot contain a space);
`MODIFY LANGUAGE ID` edits an existing generic one. The editor walks the fields
below. In a world-building script the same fields are set with `setStat` on a
`GenLanguage` copy (see the museum pack's `build.js`).

| Field | Meaning |
| --- | --- |
| `NAME` | The language's name. |
| `WORDS` (`N letter words`) | Fallback word lists by word length, separated by `/` (one list per length), used for unknown words when nothing else applies. |
| `HASHEDWORDS` | An inline dictionary, `WORD=translation` pairs separated by `/`. **Wins over** the dictionary file. |
| `INTERPRETS` | Other languages this one partly understands: `Elvish(60),Sylvan(30)`. See the comprehension model. The editor's prompt adds plain IDs (full strength); percentages are set with `setStat` or in the stored definition. |
| `NATURALLANG` | Whether this is a natural (spoken or written) language. Spells such as Fluency and Read Language act only on natural languages; the default for a new language is true. |
| `VERB`, `TRANSVERB` | The speaking and translating verbs in messages. |
| `HELP` | Help text for the language. |
| `NAMESTYLE` | A name-generation style for this language ([chapter 12](12-generation.md)). |
| `WORDFILE` | A dictionary file (below). |
| `SUFFIXES` | Inflection endings: `S:en,ED:ka`. |
| `SYLLABLES` | Syllable banks for unknown words: `ka,ra,sa/lin,tor,esh`. |
| `KEEPWORDS` | Words that are never translated: `Hakim,Qarth`. |
| `KEEPNAMES` | `true` keeps capitalised words and the names of areas, creatures and catalog entries. |
| `WRITTENNAME` | The name of the script in "translated from ..." messages (default: the language name). |
| `TRANSLATESCRIPT` | A JavaScript rule hook (below). |

**How one word is translated** (first rule that applies wins):

1. `KEEPWORDS`, or `KEEPNAMES` for a word that starts with a capital or is the
   name of a known area, creature or catalog entry. Note that with
   `KEEPNAMES=true` the first word of a sentence is also kept, because it
   starts with a capital.
2. `TRANSLATESCRIPT`'s `translate(word, language)`, if the script is approved
   and returns a non-null value (the result is used as returned).
3. `HASHEDWORDS`.
4. The `WORDFILE` dictionary.
5. `SUFFIXES`: if the word ends with an ending and the rest is in the
   dictionary, the translation of the root plus the ending's replacement is
   used (`walked` with `ED:ka` and `WALK=tal` gives `talka`; the plural `-ies`
   spelling falls back to `-y`).
6. `SYLLABLES`: a word is rebuilt from one to four syllables (one per three
   letters), picked from the banks in turn by a fixed hash of the word, so an
   unknown word always comes out the same way.
7. Otherwise the stock behaviour: the `WORDS` lists by length, else the word
   is kept.

Dictionary, suffix and syllable results copy the original word's case
(lower, Title, UPPER).

**Dictionary files.** `WORDFILE=desert.txt` names a file under
`world/vocab/languages/`; a path beginning `vocab/` is used as given. Files are
UTF-8, one `WORD=translation` per line; blank lines and lines starting with
`#` are ignored; words are matched without regard to case. A path that leaves
the world folder is refused. A large dictionary is easier to maintain as a
file than as `HASHEDWORDS`.

```text
# world/vocab/languages/dunetongue.txt
HELLO=zaal
WATER=ésh
WALK=tal
CITY=qarth
```

**TRANSLATESCRIPT.** A hook for languages built by rule rather than by table:

```js
function translate(word, language) {
    return String(word).split('').reverse().join('');
}
function garble(text, count) {
    return 'encoded:' + text;
}
```

`translate` may return `null` to fall back to the normal rules. `garble(text,
count)`, optional, replaces how a whole text is scrambled for a listener who
does not know the language. The script runs under the usual JavaScript time
limit (`JSTIMELIMIT`) and approval policy (`JSCRIPTS`; the shipped `2` runs
every script, `1` requires approval through `MODIFY JSCRIPT` in builder mode, `0`
disables them). An unapproved script is ignored and the dictionaries are used;
`WORLD CHECK ONLY=VOCAB` warns about it.

**Previewing and measuring.** In builder mode, a `DEV=1` game, or with the
CMDABILITIES security flag:

```text
LANGUAGE PREVIEW Dunetongue 40 the well is dry
LANGUAGE COVERAGE Dunetongue Oasis
LANGUAGE COVERAGE Dunetongue HERE
```

`LANGUAGE PREVIEW id [proficiency] text` (default 50%) shows what one who does
not know the language hears, what a listener at that proficiency understands,
how the speaker at that proficiency is heard by those who do not know it, what
the writing looks like and what a reader at that proficiency reads, and then a
`Words:` list showing each word's translation and its source ("dictionary",
"dictionary file", "dictionary root and suffix", "syllable banks", "kept word
or name", "translation script (or normal fallback)", or the word-list fallback).
`LANGUAGE COVERAGE id target` counts how many words of a room's text (title,
description, creatures and items), of every room in an area, or of a text file
the dictionary covers, and lists the most common missing words, so you know what
to add. `LANGUAGE` with any other argument is the ordinary `LANGUAGES` list.

### Learning by exposure: Prop_LanguageImmersion

Source: `Languages.expose`. Attach `Prop_LanguageImmersion` to an area, room,
creature or item to let characters pick up a language by hearing and reading
it.

```text
LANGUAGE=Orcish HEAR=1 READ=3 CHANCE=20% START=5 MAX=60 GRANTAFTER=25 ID=orc-camp
```

| Option | Default | Meaning |
| --- | --- | --- |
| `LANGUAGE=` | `ANY` | The language, or `ANY` for whatever is heard or read. |
| `HEAR=n` | `0` | Proficiency gained for a successful exposure by hearing speech in the language. |
| `READ=n` | `0` | The same for reading writing in it (a written item, a tome page, an inline tag). |
| `CHANCE=n%` | `20` | Chance that a given exposure helps. |
| `START=n` | `5` | Proficiency a newly granted language starts at. |
| `MAX=n` | `60` | Exposure never raises proficiency above this. |
| `GRANTAFTER=n` | `25` | Exposures needed before an unknown language is first granted. |
| `ID=`, `ALIASES=`, `LEGACYKEYS=` | | Stable identity of the exposure counter. |

A zero `HEAR` or `READ` disables that kind of exposure. The property is
looked for on the learner, the speaker or item, the learner's room and area,
and the items the learner carries. Every exposure is counted (even when the
chance roll fails); a successful roll grants the language at `START` once the
count has reached `GRANTAFTER`, respecting the language limit, or raises a known
language by the gain, up to `MAX`. Live effects are updated immediately.
Counters are saved per character, and an item keeps its identity when it moves
from room to room, so a carried teaching item does not start over.

### Foreign inscriptions inside descriptions

Text anywhere (room and item descriptions, readable text, echoes, help)
can mark part of itself as written in a language:

```text
Above the door: ^<LANG Elvish NOTE="elegant runes"^>Speak friend and enter.^</LANG^>
```

The tag is the colour-markup form: `^<LANG language [NOTE="..."]^>`, the text, and
`^</LANG^>`. Rules:

- Every viewer sees the foreign rendering of the text.
- A viewer with some comprehension also sees `(you read: "..."; translated from
  WRITTENNAME)` after it, with unknown words still garbled at partial
  proficiency.
- A viewer who understands nothing sees the optional `NOTE` text before the
  foreign text (`elegant runes: ...`).
- The language is named by its ID or name. An unknown language shows `?` for
  every letter, and `CHECKTEXT` reports it.
- `LANG` tags do not nest and the closing tag takes no attributes.
- The stored text is unchanged; only the output differs. Reading counts as a
  `READ` exposure for Prop_LanguageImmersion.

### Language commands and functions for scripts

| Script element | Behaviour |
| --- | --- |
| `MPLEARNLANG who language N` | Set the character's proficiency to `N`. |
| `MPLEARNLANG who language +N` / `-N` | Adjust it. |
| `MPLEARNLANG who language N FORCE` | Also bypass the language limit for a new language. |
| `MPSAYIN language text` | The script's host says one line in the language and then goes back to the language it was speaking (a temporary language effect it needed is removed). |
| `NOTUNDERSTOOD_PROG [language\|ANY] [p text]` | Runs on a **listener** that hears speech it understands 0% of. `$n` is the speaker, `$g` the language, `$0` the garbled text. The optional `p text` matches when the garbled text contains `text`. The language may be omitted. |
| `ISSPEAKING($n Elvish)` | True if the speech event (or character) is in that language. In `NOTUNDERSTOOD_PROG` it describes the event, even if the speaker changes language before a queued response runs. |
| `UNDERSTANDS($n Elvish)` | True when `$n` has any comprehension of the language (counting translators, interpreters and the room). |
| `UNDERSTANDS$($n Elvish)` | The comprehension, 0 to 100. |
| `MPENABLE $n Elvish ++5` | The stock command also updates live effects and respects the language limit for a new language. |

```text
NOTUNDERSTOOD_PROG ANY
mpechoat $n The guard looks baffled.
mpsetvar $n SUSPICIOUS 1
~
NOTUNDERSTOOD_PROG Common p ???
mpechoat $n The guard cannot follow your words.
~
SPEECH_PROG p mellon
if understands($n Elvish)
  mpechoat $n The door sighs open.
endif
~
```

The first block runs for any language the guard cannot follow. See
[the trigger reference](26-mobprog-triggers.md), the command reference
[27](27-mobprog-commands.md) and the function reference
[28](28-mobprog-functions.md).

Testing languages: the staff-level characters have stock skills at full
proficiency on login, so test learning, immersion and comprehension on an
ordinary character and confirm saved proficiency after a restore.

## Vocabulary, socials and help

World vocabulary files live under `world/vocab/` and are UTF-8. They rename
what the player reads without changing engine IDs, commands or saved data.
`.ini` files here are read as Java properties (backslash escapes and `\` line
continuation) unless this section says otherwise. Run `WORLD RELOAD ASSETS`
after direct edits in a running game; a restart or subsystem reload may be
needed for resource overrides (see the `VOCAB RELOAD` command below).

### The glossary: world/vocab/glossary.ini

Source: [Vocabulary](../../CoffeeMud/com/planet_ink/coffee_mud/core/Vocabulary.java).
The glossary renames whole words and phrases everywhere the game prints them,
without regular expressions. The file is checked at most once a second for
changes.

```text
mana = aether
gold coin(s) = crown(s)
Hit Points = vigor; SCOPE=SCORE
spell = working; SCOPE=HELP
Archon = Warden; NOT: Archon Hall,Archon's Blade
FINAL spark = ember
```

One rule per line, `term = replacement` followed by optional `; options`:

| Element | Meaning |
| --- | --- |
| `term = replacement` | Replace the whole word or phrase. Matching ignores case; the replacement copies the case pattern of the match (`Mana` becomes `Aether`, `MANA` becomes `AETHER`, `Hit Points` becomes `Vigor`). A replacement can be quoted. |
| `(s)` on a term | Also match the plural and the `(s)` form. `gold coin(s) = crown(s)` turns `gold coins` into `crowns` and `gold coin(s)` into `crown(s)`. |
| `; SCOPE=ALL` (default), `HELP` or `SCORE` | Where the rule applies: everywhere, help text only, or the SCORE display only. |
| `; NOT: a,b` | Phrases in which the term is left alone (`Archon Hall` keeps its `Archon`). |
| `FINAL term = ...` or `; FINAL` | Apply at final output instead of with the normal translation (the very end of the output pipeline). |
| a line `SCOPE=SCORE` by itself | The default scope for the rules that follow. |
| a line `NOT: a,b` by itself | The default exceptions for the rules that follow. |
| `#` or `;` at the start of a line | A comment. |

How it works:

- Matches are whole words: `mana` does not touch `manage`, and a term can
  start after a colour code (`^Hmana`).
- Rules run in file order, each on the output of the previous one. Avoid
  cycles and cascades (`a = b` followed by `b = c`).
- Markup is left alone: a term inside a `^<...^>` markup tag (such as an MXP
  link's command) or inside a `<...>` placeholder like `<S-NAME>` is not
  rewritten; the text between the tags is. The term itself cannot contain `=`.
- Normal rules run on translated messages (the `L(...)` text), on help output
  and on SCORE; `FINAL` rules run on the last stage. Help and score output
  also get their scoped rules.
- The glossary renames words in text, nothing else. A creature called
  "Archon" is still an Archon to `STAT` and to masks.

`WORLD CHECK ONLY=VOCAB` flags lines without `=`, unknown scopes and unclosed
quotes.

### The translation and parser rule files and the VOCAB command

The inherited rule files `resources/translation_en_TX.properties` and
`parser_en_TX.properties` (the `LANGUAGE` and `COUNTRY` settings name them)
rewrite text and typed commands with `REPLACE`, `REPLACEALL`, `REPLACEWHOLE`
and `IGNORE...` rules in sections (`SESSION-TRANSLATION`, `FILTER-TRANSLATION`,
`FINAL-TRANSLATION`, and for typed lines `COMMAND-PRE-PROCESSOR`,
`ITEM-PRE-PROCESSOR`, `ITEM-FAIL-PROCESSOR`, `RAW-INPUT-PROCESSOR`). A world adds
its own file under `world/resources/` with the same name. The glossary is the
easier route for renaming terms; use these files for regular-expression
rewrites.

`VOCAB` (builder mode, a `DEV=1` game, or the CMDAREAS security flag) tests and
edits them:

```text
VOCAB TEST the fixcolour of it
VOCAB INPUT get sword and shield from chest
VOCAB LIST TECH_LEVEL_NAMES
VOCAB RELOAD
VOCAB ADD final-translation replaceall "colour" with "color"
```

| Subcommand | Behaviour |
| --- | --- |
| `VOCAB TEST text` | Shows the text after session, final and filter translation, with each rule that matched. |
| `VOCAB INPUT command line` | Shows what the raw-input parser and the command pre-processor make of a typed line: the commands it becomes and each rule that matched. |
| `VOCAB LIST KEY` | Shows a `lists.ini` key's value in effect and the file it came from (the world's `resources/` over the game's). |
| `VOCAB RELOAD` | Forgets the cached rule files, lists, socials and help so they are read again. |
| `VOCAB ADD SECTION rule` | In builder mode only, appends a rule to the world's own rule file under the section; it takes effect at once. |

### Local lists: Prop_LocalLists

Source: [Prop_LocalLists](../../CoffeeMud/com/planet_ink/coffee_mud/Abilities/Properties/Prop_LocalLists.java).
Targets: creatures, rooms, areas.

The game keeps several global charts in `resources/lists.ini` (time-of-day
messages, the health chart, damage words, hit and miss text, corpse blurbs, gaits,
magic words). Prop_LocalLists replaces any of them for a creature, a room or an
area. An underground area can announce the glowmoss brightening instead of the
sunrise; a clockwork creature can have its own health lines.

```text
LOAD=vocab/lists_underdark.ini
MAGIC_WORDS="mellon,ithildin"
TOD_CHANGE_INSIDE="^JThe glowmoss brightens.^?,,,^JThe moss dims.^?"
```

Keys (all optional): `TOD_CHANGE_OUTSIDE`, `TOD_CHANGE_INSIDE`, `HEALTH_CHART`,
`DAMAGE_WORDS`, `WEAPON_HIT_DESCS`, `WEAPON_MISS_DESCS`, `CORPSE_BLURBS`,
`MAGIC_WORDS`, `GAIT_LIST`; and `LOAD=file`, a Java-properties file under
`world/vocab/` (a name not starting `vocab/` is looked for there) that holds
any of the same keys in the stock `lists.ini` shape. Inline keys win over the
file. Quote values that contain spaces, commas or semicolons.

**Lookup order.** For a given key the engine tries, in order: the creature
acting (for combat text, the attacker), the creature, the room, the area, the
area's parent areas, then the global list. The first Prop_LocalLists that has
the key wins. A weapon's text uses its wielder's context (a racial natural
weapon has no owner, so combat supplies the wielder). `Prop_SkyText`
keeps priority for its own time-of-day text.

**List shapes** (as in `lists.ini`): commas separate indexed entries, `|`
separates random alternatives within an entry, semicolons separate grid rows.

| Key | Shape |
| --- | --- |
| `TOD_CHANGE_OUTSIDE`, `TOD_CHANGE_INSIDE` | Four entries: dawn, day, dusk, night. An empty entry says nothing. |
| `HEALTH_CHART` | Eleven entries, from near death to perfect health, with `<MOB>` for the creature's name. |
| `WEAPON_HIT_DESCS`, `WEAPON_MISS_DESCS` | Six entries, one per attack category: missile, thrown, slash/bash, other, pierce, laser. Hits use `<DAMAGE>` and `<TOOLNAME>`. |
| `DAMAGE_WORDS` | See below. |
| `CORPSE_BLURBS` | Rows `cause=phrase\|phrase` separated by `;` (or the stock `cause,phrase,phrase` rows). The cause is an ability ID, a weapon damage type or class, or `ALL`. |
| `MAGIC_WORDS` | A comma list of command words for wands and the like; one is picked deterministically per item. |
| `GAIT_LIST` | Rows `NAME,present verb,arrival,departure` separated by `;`. |

`WORLD CHECK ONLY=VOCAB` (and `PROPCHECK`) verify that the health chart has 11
entries, hit and miss lists have 6, damage rows start with a damage type or
`ALL`, and referenced files exist.

### Damage words

Damage words are the verbs in combat lines ("scratches", "decimates") chosen by
how much damage was done. The stock `DAMAGE_WORDS` in `lists.ini` is a grid: a
row per weapon damage type (`ALL`, `NATURAL`, `SLASHING`, `PIERCING`,
`BASHING`, `BURNING`, ...), each starting with the type and then the words in
increasing severity, with `(s)` for the plural. `DAMAGE_WORDS_THRESHOLDS` gives
the damage thresholds; it has one more entry than a row has words.

```text
DAMAGE_WORDS="ALL,scuff(s),scratch(es),wound(s),maul(s),ruin(s);BURNING,warm(s),singe(s),scorch(es),char(s),incinerate(s)"
```

The example above is a local list with five words per row: damage beyond the
fifth step keeps using the last word (the stock rows have 16 words, one per
threshold step). Local lists use the same grid. A row whose type matches the
damage type beats `ALL`. A local `DAMAGE_WORDS` may also be a single flat list
of words with no leading type; it is then used for every type. These words
apply to ability and spell damage as well as weapon hits (`<DAMAGE>` in the
message). Quote the whole value, because it contains semicolons. `Prop_WeaponMessages` (below) can give
a weapon its own damage words, which win over local words.

### Per-race and per-weapon text

A custom race (`GenRace`, see [chapter 3](03-characters.md)) can carry two
fields, edited in `MODIFY RACE`:

- `HEALTHCHART`: eleven health lines with `<MOB>`, with `|` alternatives. It
  overrides the race's borrowed `HEALTHRACE` text; the local and global charts
  remain fallbacks.
- `CORPSEBLURBS`: `cause=phrase|alternative` pairs separated by `;`:

  ```text
  BURNING=a heap of melted springs;ALL=a pile of clockwork gears
  ```

  Race corpse text wins over local lists; then the stock corpse rules apply.

`Prop_WeaponMessages` (targets weapons, including racial natural weapons) sets
a weapon's own hit and miss lines:

```text
HIT="<S-NAME> sear(s) <T-NAMESELF> with <TOOLNAME>: <DAMAGE>." MISS="<S-NAME> swing(s) wide." DAMAGEWORDS="singes,scorches,burns,incinerates"
```

`HIT` and `MISS` may hold `|`-separated alternatives chosen at random.
Normal source and target substitutions work, and `<TOOLNAME>` is the weapon's
name. Weapon text overrides local hit and miss lists, and its `DAMAGEWORDS`
override local damage words. `DAMAGEWORDS` takes either a flat severity list or
a grid with rows starting `ALL` or a damage type, like `DAMAGE_WORDS`.

### Socials and help overlays

**Socials.** `world/vocab/socials.txt` is read after the stock
`resources/socials.txt`, so a world line replaces a stock social of the same
name. It uses the stock tab-separated format (a code, the social name with an
optional target pattern, and the texts the actor, others and target see; look
at `resources/socials.txt` for the columns). In this line `<TAB>` stands for a
real tab character:

```text
ss<TAB>GREETMOON<TAB>You greet the moon.<TAB><S-NAME> greet(s) the moon.<TAB><TAB><TAB><TAB><TAB>
```

`world/vocab/socials.ini` chooses which stock socials remain:

```ini
STOCK=BOW,NOD,SMILE,WAVE
REMOVE=CURSE
```

`STOCK` is `ALL` (default), `NONE`, or a comma list to keep; `REMOVE` is a
comma list pruned before the world's `socials.txt` loads (so a world social
may re-add a removed name). Builder `CREATE`/`MODIFY`/`DESTROY SOCIAL` save the
effective set to the world file and set `STOCK=NONE`. `Prop_Socials LOAD=` also
looks in `world/vocab/` before `resources/`.

**Help.** Stock help files load alphabetically, then `world/vocab/help/*.ini`
alphabetically, then the builder file (`world_help.ini`) last. A world entry
overrides a stock entry with the same key. Files named `arc_*.ini` hold Archon
help (shown by `AHELP`); the others hold normal help.

- To hide stock entries: list keys one per line in `vocab/help/hide.txt`
  (`#` comments allowed), or `HIDE=CLANS,CHANNELS` in `vocab/help/settings.ini`.
  A key prefixed `ARC:` hides only Archon help. A world file can replace a
  hidden key.
- Builder `HELP` edits write `world_help.ini` and `AHELP` edits write
  `arc_world_help.ini`. Destroying an entry records it in
  `vocab/help/deleted.txt` (a final deletion; `ARC:KEY` for Archon help); saving
  the topic again clears the deletion.
- Archons see the selected entry's source file after its text.
- Help entries can carry the lore gating metadata described in
  [The lore codex](#the-lore-codex).
- Do not edit the stock help files to ship a world's vocabulary; use the
  world overlay.

Run `WORLD RELOAD ASSETS` or `VOCAB RELOAD` after editing socials or help in a
running game.

## Worked examples

### Example 1: a museum gallery with clues, lore and its own language

This builds on the museum pack in [examples/world](examples/world/) (its
`build.js`, `lore/`, `text/` and `vocab/` files). The goal: a gallery whose gate
text reflects a lever, a room with a readable inscription and a faint sound,
a shrine with a once-only line, a tablet that unlocks a codex entry, a paged
introduction, and a small invented language.

**World files.**

```ini
# world/lore/museum.ini
COAST=The Drowned Coast|History|The museum was built after the sea took the old harbour.
```

```text
# world/text/museum.txt
[arrival]
The guide sets a ledger on the desk.

'Some things here still remember the sea,' she says.
```

```text
# world/vocab/languages/museum.txt
HELLO=sela
SEA=mar
GATE=esh
FRIEND=tavi
```

**Properties** (as typed into the builder or set by `build.js`):

```text
Entrance room description:
<VARIES><IFVAR MUSEUM_GATE=1>The east gate stands open.</IFVAR><ELSE>The east gate is closed.</ELSE></VARIES> A guide waits beside a brass lever.

Gallery room:
  Prop_Details:  inscription,runes=Nine carved waves surround a seal. ID=museum-waves SETVAR=MUSEUM_RUNES=1
  Prop_Senses:   ID=museum-gallery SOUND="The distant surf echoes through the stone." CHANCE=100

Stone tablet (readable, ungettable item in the gallery):
  Prop_Lore:     ENTRY=COAST ON=READ SCOPE=CHAR MSG="You learn the history of the coast."

Shrine room:
  Prop_FirstEntry: ID=museum-shrine TEXT="A single candle burns before the sea-worn altar." ONCE=CHARACTER
```

The lever is a `Prop_Switch` item whose actions run `setvar WORLD MUSEUM_GATE 1`
(see [chapter 7](07-items-crafting.md)); the gate text reads that variable.

**The invented language** (CREATE LANGUAGE MuseumSpeech, then MODIFY LANGUAGE
MuseumSpeech; or the equivalent `setStat` calls in the script):

```text
NAME          Museum Speech
WORDFILE      museum.txt
SYLLABLES     sa,ta,ma/la,ri,ven
SUFFIXES      S:en
WRITTENNAME   Coastal Runes
```

**Narration.** The guide's script, on a player-caused trigger such as
`GREET_PROG`:

```text
MPNARRATE $n museum.txt#arrival PAUSE
```

**Check as an ordinary character.**

1. `LOOK` in the entrance: "closed". Pull the lever; `LOOK`: "open".
2. In the gallery, `LOOK inscription` shows the carving; `READ tablet` unlocks
   the entry; `LORE` shows `History (1)`; `LORE COAST` reads it; `JOURNAL`
   has nothing yet unless `JOURNALAUTO=LORE`.
3. `LISTEN` repeats the surf line.
4. Enter the shrine: the candle line appears once. Leave and return: nothing.
5. `LANGUAGE PREVIEW MuseumSpeech 100 hello friend of the sea` shows `hello ->
   sela (dictionary file)`; words not in the file, such as `of` and `the`, come out of the syllable banks.
6. `SAVE`, restore, and confirm the lore, the first-entry marker and the gate
   variable survived; start a new game and confirm they did not.
7. `WORLD CHECK ONLY=VOCAB,LORE,TEMPLATES,IDS` is clean.

### Example 2: an underground area with its own words, map and sounds

A cavern area where the sun never rises, light behaves differently and the
player should see a map with a landmark.

`world/vocab/lists_underdark.ini`:

```ini
TOD_CHANGE_INSIDE=^JThe glowmoss brightens and the day-cycle begins.^?,,,^JThe glowmoss dims and the night-cycle begins.^?
HEALTH_CHART=^r<MOB> is bleeding black ichor.^N,^r<MOB> is badly torn.^N,^y<MOB> is hurt.^N,^y<MOB> is scratched.^N,^g<MOB> is bruised.^N,^g<MOB> is steady.^N,^g<MOB> looks fit.^N,^c<MOB> looks strong.^N,^c<MOB> looks hale.^N,^c<MOB> looks untouched.^N,^c<MOB> is the picture of health.^N
MAGIC_WORDS=glim,umbra,shael
```

On the area, a property and a behavior:

```text
Prop_LocalLists:  LOAD=vocab/lists_underdark.ini
Behavior Emoter:  min=10 max=30 chance=40 RETRY NOREPEAT=2 QUIETAFTER=3;SOUND Water drips somewhere far below.;WEIGHT=2 SMELL The air smells of wet stone.;NIGHT SOUND Something large turns over in its sleep.
```

On the inn (a room):

```text
Prop_MapGlyph: CHAR=I COLOR=y LABEL="Mushroom inn" LEGEND
```

Settings in `coffeemud.ini` (these are not `world.ini` keys):

```ini
MAPFOG=KNOWN
COLORALIASES=LORE=##C8A060,DANGER=LIGHTRED+BOLD
ROOMHEADER=^O%r^N ^w[%z]^N
```

And `world/vocab/glossary.ini`, renaming the world's terms:

```text
mana = focus
sun = glow-moss
```

Check: `LOOK` shows `Room [Area]`; wait for the time of day to change inside the
area and watch the custom message; `MAP` shows the yellow `I`, a legend and
dim rooms you only read about; `PREVIEW night` works as described; `COLORSET
ALIASES` lists `LORE` and `DANGER`; fight a creature and read the health line;
turn ANSI off and check nothing leaks as literal `^{...}`.

### Example 3: an orc camp and a language barrier

Goal: the chieftain understands only Orcish; a guard reacts to the wrong
language; a wall shows Elvish writing; spending time in camp teaches Orcish
slowly.

On the chieftain (a creature):

```text
Prop_Monolingual: LANGUAGES=Orcish CONFUSED="<S-NAME> frown(s) and grunt(s)."
```

On the camp's area (so everyone present is exposed):

```text
Prop_LanguageImmersion: LANGUAGE=Orcish HEAR=2 READ=3 CHANCE=25% START=5 MAX=40 GRANTAFTER=15 ID=orc-camp
```

On the gate guard (a property and a Scriptable behavior):

```text
Prop_Monolingual: LANGUAGES=Orcish
```

```text
NOTUNDERSTOOD_PROG ANY
mpechoat $n The guard shouts something you cannot follow and brandishes a spear.
~
GREET_PROG 100
if understands($n Orcish)
  mpechoat $n The guard nods curtly and lets you pass.
endif
~
```

On the room's description, a wall in Elvish:

```text
Scratched into the lintel: ^<LANG Elvish NOTE="fine, flowing script"^>The road ends at the sea.^</LANG^>
```

The monolingual creatures speak Orcish themselves, which is what the camp's
immersion property hears. Check: as a new character, speak to the chieftain and
watch the frown and the guard's reaction. Wait in the camp for a while and use `LANGUAGES` to see
Orcish arrive at 5% and grow. `LANGUAGE PREVIEW Orcish 40 follow me to the
fire` shows how much of a sentence a 40% speaker gets. Give the character a
translator charm (`Prop_LangTranslator` with `NOTRANSLATE 100% Orcish`) and confirm
the chieftain's speech is now understood. Save, restore in a new process, and
confirm the proficiency and the exposure counter persisted.

### Example 4: a treasure map that marks the world

A readable item that teaches rooms and marks the dig site, and a journal
entry from the sage who gave it:

```text
Prop_MapReveal: ROOMS=Coast#1-40,Coast#45 MARK=Coast#12:X:"Buried chest" COLOR=r
```

The sage's script when handing it over:

```text
MPJOURNAL $n Clues "The old smuggler's map marks a chest on the coast. (the sage)"
```

After reading the item, `MAP` (with `MAPFOG=KNOWN`) shows the coves dim, a red
`X` with "Buried chest" in the legend; `JOURNAL SEARCH chest` finds the clue.
Under `MAPFOG=VISITED` the coves would not appear until walked.

## Quick reference

### Commands

| Command | Audience | Purpose |
| --- | --- | --- |
| `LORE`, `CODEX` | player | Codex: `LORE`, `LORE category`, `LORE key-or-title`, `LORE NEW`. |
| `JOURNAL` | player | `JOURNAL [category]`, `JOURNAL WRITE text`, `JOURNAL DELETE id`, `JOURNAL SEARCH words`. |
| `MAP` | player | `MAP [radius]`, `MAP AREA name`. |
| `AUTOMAP` | player | `AUTOMAP [ON\|OFF]`, `AUTOMAP SIZE n`. |
| `LISTEN [target]`, `TOUCH`/`FEEL [target]`, `TASTE target` | player | Sense text from Prop_Senses; `SNIFF` uses Prop_Smell. |
| `LOOK`/`EXAMINE`/`READ keyword`, `LOOK keyword ON object` | player | Reach Prop_Details after real objects. |
| `PROMPT [text\|DEFAULT]` | player | The prompt template. |
| `COLORSET`, `COLORSET ALIASES`, `COLORSET NAME colour\|DEFAULT`, `COLORSET DEFAULT` | player | Colours and personal alias overrides. |
| `LANGUAGES`, `LANGS`, `SPEAK language` | player | Known languages; choose the language you speak. |
| `WINDOWS PANEL [ON\|OFF\|sections]` | player | The window side panel (single-player games with a display). |
| `PREVIEW [conditions]`, `CHECKTEXT [ROOM\|AREA\|WORLD]` | builder (CMDROOMS) | Render and lint conditional text. |
| `LANGUAGE PREVIEW id [prof] text`, `LANGUAGE COVERAGE id target` | builder (CMDABILITIES) | Inspect an invented language. |
| `CREATE LANGUAGE id`, `MODIFY LANGUAGE id` | builder | Edit a generic language. |
| `VOCAB TEST\|INPUT\|LIST\|RELOAD\|ADD` | builder (CMDAREAS) | Test and edit translation and parser rule files. |
| `WORLD RELOAD ASSETS` | builder | Reload vocabulary, help, socials and presentation assets. |
| `WORLD CHECK ONLY=VOCAB,LORE,TEMPLATES,IDS` | builder | Lint presentation assets. |

See also the generated [tool reference](tool-reference.md) for each command's
shipped help.

### Scripting elements

| Element | Where | Purpose |
| --- | --- | --- |
| `MPNARRATE target file[#section] [PAUSE\|NOPAUSE] [STYLE=ALIAS]` | MOBPROG | Paged story text. |
| `MPLORE target ADD key [PROFILE]` | MOBPROG | Unlock a lore entry. |
| `MPJOURNAL target [category] "text"` | MOBPROG | Add a journal entry. |
| `MPLEARNLANG who language N\|+N\|-N [FORCE]` | MOBPROG | Set or adjust a language. |
| `MPSAYIN language text` | MOBPROG | Speak one line in a language. |
| `NOTUNDERSTOOD_PROG [language\|ANY] [p text]` | trigger | Heard speech that is 0% understood. |
| `UNDERSTANDS(who language)`, `UNDERSTANDS$(who language)`, `ISSPEAKING(who [language])` | function | Comprehension tests. |
| `SET LORE key` | quest script | Unlock a lore entry for the step's participants. |
| `<CALL function args>` | any text | Text computed by `FUNCTION_PROG` or JavaScript. |

### Properties

| Property | Targets | Options |
| --- | --- | --- |
| `Prop_FirstEntry` | rooms, areas | `TEXT ONCE ID TATTOO SETVAR ALIASES LEGACYKEYS` |
| `Prop_Details` | rooms, items, creatures | per detail: `EXAMINEONLY READ HIGHLIGHT MASK TATTOO SETVAR ID ALIASES LEGACYKEYS` |
| `Prop_Senses` | rooms, items, creatures, exits | `SOUND TOUCH TASTE CHANCE TATTOO SETVAR ID ALIASES LEGACYKEYS` |
| `Prop_DarkDescription` | rooms | `TITLE DESC` |
| `Prop_ShowWhen` | items, creatures, exits, rooms | `MASK WHEN CHECK HIDEMODE ID ALIASES LEGACYKEYS` |
| `Prop_Lore` | items, rooms, creatures | `ENTRY ON MSG SCOPE` |
| `Prop_MapGlyph` | rooms | `CHAR COLOR LABEL LEGEND HIDE` |
| `Prop_MapReveal` | readable items | `ROOMS AREA MARK COLOR` |
| `Prop_Monolingual` | creatures | `LANGUAGES CONFUSED` |
| `Prop_LanguageSpeaker` | creatures, items, rooms, exits, areas | `LANGUAGE PLAYERS NOMOBS HOMEONLY UNDERSTANDS` and a mask after `;` |
| `Prop_LangTranslator` | creatures, items, rooms | `NOTRANSLATE`, percentages, languages, `` `words ``, `#PLAYER` |
| `Prop_LanguageImmersion` | areas, rooms, creatures, items | `LANGUAGE HEAR READ CHANCE START MAX GRANTAFTER ID ALIASES LEGACYKEYS` |
| `Prop_LocalLists` | areas, rooms, creatures | `LOAD` and the nine list keys |
| `Prop_WeaponMessages` | weapons | `HIT MISS DAMAGEWORDS` |

All of the options for these properties are described in the property help
(`AHELP PROP_<NAME>`) and [chapter 23](23-properties-reference.md).

### Files in the world folder

| File | Purpose |
| --- | --- |
| `world/lore/*.ini` | Codex entries (`KEY=title\|category\|text`, optional `FILE=`). |
| `world/lore/*` (other) | Long lore bodies named by `FILE=`. |
| `world/text/NAME.txt` | MPNARRATE sources (`[section]`, blank-line pages). |
| `world/text/title.txt`, `world/text/prologue.txt` | Launch screens. |
| `world/text/panel.txt` | STATUS section of the window panel. |
| `world/score.txt` | Extra SCORE lines. |
| `world/vocab/glossary.ini` | Whole-word renames. |
| `world/vocab/languages/*.txt` | Language dictionaries (`WORD=translation`). |
| `world/vocab/*.ini` | `LOAD=` lists for Prop_LocalLists. |
| `world/vocab/socials.txt`, `world/vocab/socials.ini` | World socials and pruning. |
| `world/vocab/help/*.ini`, `hide.txt`, `settings.ini`, `deleted.txt` | Help overlay. |
| `world/resources/translation_*.properties`, `parser_*.properties` | The world's translation and parser rules. |
| `world/custom/abilities/ID.cmare` | Generic languages. |

### Settings

| Setting | Default | Where | Meaning |
| --- | --- | --- | --- |
| `GAMENAME` | `CoffeeSUD` | world.ini or coffeemud.ini | Game name; `@MudInfo?NAME@`. |
| `AWARERANGE` | `3` | world.ini or coffeemud.ini | Automap and `MAP` default radius (half), 0 disables the automatic map unless a player set a size. |
| `CHARCREATIONSCRIPTS` | empty | world.ini or coffeemud.ini | Creation scripts, including a scripted prologue (`END:`). |
| `MAPFOG` | `KNOWN` | coffeemud.ini / launch | `KNOWN`, `VISITED`, `ALL`. |
| `JOURNALAUTO` | empty | coffeemud.ini / launch | `QUEST,LEVEL,AREA,LORE`. |
| `JOURNALTALK` | `false` | coffeemud.ini / launch | Record `[[topic]]` dialogue as journal entries. |
| `ROOMHEADER`, `ROOMFOOTER` | empty | coffeemud.ini / launch | Room look templates. |
| `COLORALIASES` | empty | coffeemud.ini / launch | Named colour aliases. |
| `WINDOWPANEL` | empty | coffeemud.ini / launch | Window panel sections. |
| `MAXLANGUAGES` | `3` | coffeemud.ini | Stock language allowance per class. |
| `LANGCOST` | `3 PRACTICE` | coffeemud.ini | Cost of learning a language. |
| `JSCRIPTS` | `2` | coffeemud.ini | Approval policy for TRANSLATESCRIPT and other scripts. |
| `LISTFILE` | `/resources/lists.ini` | coffeemud.ini | The global lists file; a `world/resources/lists.ini` overlays it (`VOCAB LIST` shows which wins). |

## Persistence notes

| What | Stored in | A reload or restore |
| --- | --- | --- |
| Description, Prop_* options, GenLanguage fields | World files (areas and `custom/abilities`) | `WORLD RELOAD` reloads areas; a new game starts from them. |
| Lore, text, vocab, glossary, dictionaries, score, panel, title, prologue | Live world asset files | Read when needed; `WORLD RELOAD ASSETS` clears caches. Every campaign sees the current files. |
| Lore unlocks, first-entry markers, clue and found markers | Tattoos on the character (or profile with `PROFILE`/`ONCE=PROFILE`) | Saved with the campaign. |
| Lore read markers, journal and its counters, map reveals and marks, automap size, personal colour overrides, language exposure counters | The character's `Prop_StoryRecords` record | Saved with the campaign. |
| Learned languages and proficiency | The character's abilities | Saved with the campaign. |
| Variables read by `IFVAR`, `%{VAR}` | Script variable stores (`WORLD`, quests, characters) | Saved with the campaign. |
| Unread narration pages and pause ownership | The campaign's narration record | Resumed on restore. |
| Window panel sections set with `WINDOWS PANEL` | Memory | Not saved; set `WINDOWPANEL` to make it permanent. |
| `MAPFOG`, `ROOMHEADER` and the other ini settings | `coffeemud.ini` or launch override | Read at start. |

A campaign never embeds a private copy of the world's lore or text files: it
refers to them, so distribute matching world versions with a save. The
character's own records survive edits to the files; a deleted lore entry just
stops being listed.

## Troubleshooting

**Tags appear in the text.** The text does not start with `<VARIES>`. Run
`CHECKTEXT`. Unknown tags (typos) are left in the text on purpose.

**A condition with `>` or `<` breaks the tag.** Write `&gt;` / `&lt;`, or use
`IFVAR name GE 3`.

**`ELSE` shows when it should not, or twice.** It inverts the previous sibling
only. Nest alternatives inside an `ELSE` for three or more branches.

**`IFVAR` is true for every character.** It reads the `*` scope, any host's
variable. Use a tattoo with `MASKn`, or `PARTYTATTOO`, for per-character text.

**`+IF` mask always passes.** Write `-IF "condition"`.

**First-visit line never shows.** `VISITED` is already true on arrival; use
`FIRSTVISIT` or Prop_FirstEntry. Prop_FirstEntry fires for player characters
entering (or recalling to) a room it is on, or a room of an area it is on.
Moving to a changed `ID` shows the line again; keep `ID` when editing.

**A clue repeats or is lost on rewrite.** The marker follows `ID`. Without it,
the identity follows the keywords (Prop_Details) or an engine-generated ID.
Add `ID=` before shipping and `ALIASES=` when renaming.

**`LOOK keyword` finds nothing.** The keyword must match a whole keyword of the
record; a bare `;` ended the record early; the host is hidden by Prop_ShowWhen,
inside a closed container, or the room is dark (a dark room's details need
light).

**`LISTEN`/`FEEL` is silent.** The target has no text for that sense, the
`CHANCE` roll failed, or the viewer cannot hear.

**A lore entry never appears.** Keys are case-insensitive but must be exactly
the `ENTRY=`/`MPLORE` key; files must end in `.ini` and sit directly in
`world/lore/`; a malformed `\uXXXX` escape makes Java reject the whole file
(logged as an error). `WORLD CHECK ONLY=LORE` reports problems.

**The journal has no automatic entries.** `JOURNALAUTO` is empty by default and
cannot be set in `world.ini`.

**`ROOMHEADER`/`MAPFOG`/`COLORALIASES`/`WINDOWPANEL` in `world.ini` do
nothing.** They are not world keys. Put them in `coffeemud.ini` or pass them on
the command line.

**`MAP` says "No map is available here."** The starting room is unmappable or
hidden, or not yet known; or `MAP AREA` found no known room in that area.

**Ambient lines never play.** Check the prefixes are capitals, the separator
`;`, that `QUIETAFTER` is not silencing combat or narration, that a player
is in the room, and any mask. A line whose first word is an ordinary capitalised
word is text, not a prefix.

**`MPNARRATE ... PAUSE` seems stuck.** It waits for Enter. A pending script
menu or another narration for the same character replaces it. Background party
members receive the whole text at once.

**A glossary rule does nothing.** The rule is whole-word and case-folded:
`mana` does not touch `manage`; a rule under another `SCOPE` runs only there;
`FINAL` rules run at the last stage; edits are noticed within a second;
renaming never changes engine IDs, commands or masks.

**Title screen colours are wrong.** `title.txt` and `prologue.txt` accept only
the launcher's sixteen colour codes, `^N`, `^?` and `^^`; aliases and
semantic codes (`^H`) do not exist before the engine starts.

**`COLORSET LORE red` opens the colour menu.** `LORE` is not defined in
`COLORALIASES`.

**Language:** speech to an NPC is silently ignored because its language is
not understood (`NOTUNDERSTOOD_PROG` runs instead); `UNDERSTANDS$()` gives the
number to debug with. A `TRANSLATESCRIPT` that is not approved is ignored
(`WORLD CHECK ONLY=VOCAB` warns). With `KEEPNAMES=true` the first word of a
sentence is kept too. The editor's INTERPRETS prompt cannot set percentages.
Staff characters have full stock languages; test as an ordinary character.

## See also

- [Handbook index](README.md)
- [World workflow and file formats](02-world-workflow.md): `vocab/`, `lore/`,
  `text/` in the world folder, `WORLD RELOAD`, `WORLD CHECK`.
- [Areas, rooms and exits](20-areas-rooms-exits.md): descriptions, exits,
  unmappable rooms.
- [Items](22-items.md): readable items, `GenTome` pages with `LANG=` and `LORE=`,
  maps.
- [Properties reference](23-properties-reference.md) and
  [behaviors and masks](24-behaviors-and-masks.md): Prop_* options, Emoter,
  Sounder, zapper masks.
- [MOBPROG guide](04-mobprog.md), [triggers](26-mobprog-triggers.md),
  [commands](27-mobprog-commands.md), [functions](28-mobprog-functions.md):
  MPNARRATE, MPLORE, MPJOURNAL, MPLEARNLANG, MPSAYIN, NOTUNDERSTOOD_PROG.
- [JavaScript](05-javascript.md): building languages and worlds from scripts.
- [NPC dialogue](06-npcs-dialogue.md): `[[topic]]` lines, scenes, barks.
- [Quests](08-quests.md): `SET LORE`, quest journals.
- [World simulation](10-world-simulation.md): time of day, seasons, weather,
  holidays, tides, darkness.
- [Saves and party](31-saves-and-party.md): windows, profile, what persists.
- [Installing and running](18-installing-and-running.md): the title screen and
  launch overrides.
- [Configuration](19-configuration-and-administration.md),
  [world settings](world-settings.md), [state and persistence](state-and-persistence.md).
- [Testing and release](13-testing-release.md): PREVIEW, CHECKTEXT and world checks.
- [Cookbook](cookbook.md) and the [example world pack](examples/world/).
- [Presentation options](../presentation.md), [language options](../languages.md)
  and [identities and validation](../authoring-reliability.md): the original
  feature notes.
