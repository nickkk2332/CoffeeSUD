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
