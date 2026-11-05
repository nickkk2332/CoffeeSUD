# Presentation, lore, languages and vocabulary

[Handbook](README.md) · [Presentation options](../presentation.md)
· [Language options](../languages.md) · [Identity and validation](../authoring-reliability.md)

## Text is rendered for a viewer

Room titles/descriptions, object text, readable writing and many output paths
can render conditional text for the actual viewer. Use VARIES blocks for
story, calendar, light, party, wanted or quest conditions:

```text
<VARIES><IFVAR BRIDGE=1>A repaired bridge spans the stream.</IFVAR><ELSE>Only broken ropes remain.</ELSE></VARIES>
```

Use script scope consistently when setting BRIDGE. Conditions include QUESTWON,
QUESTRUNNING, QUESTSTEP, PARTYTATTOO, HOLIDAY, WANTED, INCOMBAT, FIRSTVISIT,
calendar and light tests. FIRSTVISIT remains true through the arrival look until
the next move; VISITED is already true on arrival. Nest blocks carefully and
escape comparison brackets in XML attributes.

`<CALL function>` can return computed text. It is cached within a look with a
bounded number of calls; keep it side-effect free. Rendering the same text for
two viewers should not grant rewards, consume quest items or move the clock.
Use events for consequences.

## First entry, clues and sensory actions

Prop_FirstEntry shows a first-arrival line with CHARACTER/PROFILE once policy,
optional tattoo/variable and stable ID. Prop_Details supplies aliases for
features that do not need full item objects; actual objects take priority.
Details can have READ text, examine-only restrictions, masks and saved clue
rewards. Prop_Senses adds LISTEN, TOUCH/FEEL and TASTE text/pools with chances
and first-use marks. Use real objects when they must be moved, attacked,
contained or referenced as inventory.

```text
inscription,runes=Ancient letters spiral around a seal. ID=crypt-runes SETVAR=READ_RUNES=1
```

The ID above follows the detail description. Put ID on the property/source for
other supported properties. Quoted settings can contain spaces, semicolons and
literal quotes escaped as `\"`; split records only outside quotes. An INI file
can add a second Java-properties escaping layer. Do not move an example between
formats without accounting for it.

Prop_DarkDescription provides a safe title/description while contents remain
unseen. Prop_ShowWhen controls per-viewer conditional visibility and discovery.
Visibility is not just an output filter: test LOOK, GET, targeting, map and
interaction paths. Preserve source IDs and aliases when revising clues.

## Lore, journals and opening narration

Lore records live in UTF-8 `world/lore/*.ini`:

```ini
SUNDERING=The Sundering|History|The old coast lies beneath the sea.
BESTIARY=Deep Ones|Creatures|FILE=deep-ones.txt
```

A FILE path is relative to the lore directory. MPLORE, Prop_Lore and quest
SETLORE unlock entries. CHARACTER/PROFILE scope determines sharing. LORE/CODEX
offers categories and NEW entries. Learning a fact and reading a player's
journal entry are separate systems.

JOURNAL saves personal entries with stable deletion IDs and category/search
support; the newest 300 are retained. MPJOURNAL writes authored entries.
JOURNALAUTO can record quest, level, area and lore events, and JOURNALTALK can
record conversations. Avoid filling the journal with a repeated ambient bark.

`MPNARRATE $n opening.txt#arrival PAUSE STYLE=LORE` reads paragraphs from
`world/text/opening.txt`, beginning at `[arrival]`. Blank lines separate pages.
NOPAUSE is default. PAUSE uses asynchronous Enter callbacks, consumes no turns
and temporarily holds real-time progression for visible readers. Background
party sessions receive text without an input hold. Unread pages and pause
ownership survive saves; finishing releases its hold and preserves a prior
manual pause. A menu and narration replace each other on one input stream.

## Maps, ambience and interface

Player map fog distinguishes visited, revealed and unknown rooms. Reveal is
knowledge, not a visit. Prop_MapGlyph adds symbols/legends or hides a room;
Prop_MapReveal adds saved discoveries and markers through readable items.
Hidden/conditionally unavailable exits and unmappable content stay filtered.
Builder WORLD MAP is a different diagnostic map.

Emoter and Sounder support time/season/weather/mask/variable conditions,
weights, ONCE, no-repeat and quiet-after rules. Quiet periods can follow
combat, sensory reading or narration. Random lines should enrich the scene;
critical navigation or puzzle instructions need a reliable route too.

World score text, room header/footer templates and prompt tokens can show
script variables, quest variables, time mode and leader. Named COLORALIASES
provide semantic styles such as LORE or DANGER, with personal COLORSET overrides
and clean plain-text output. Window panels and related presentation additions
are indexed in [PR](feature-index.md#pr). Test ANSI off, narrow terminal,
headless output and a second viewer. Do not expose a hidden room title via
a custom footer or cached map label.

## Languages are mechanics

CREATE/MODIFY LANGUAGE defines a GenLanguage. Dictionaries live in
`world/vocab/languages/`. WORDFILE, inline HASHEDWORDS, SUFFIXES, deterministic
SYLLABLES, KEEPWORDS, KEEPNAMES and WRITTENNAME build an invented language.
TRANSLATESCRIPT can define translate(word,language) and optionally garble;
null falls back to normal rules. Preserved words take priority. Hooks use JS
approval and instruction limits; LANGUAGE PREVIEW/COVERAGE check output.

Learned proficiency and translators determine comprehension. Related-language
INTERPRETS scales proficiency rather than granting full fluency. Unknown speech
does not reach normal SPEECH/MudChat. Prop_Monolingual or
Prop_LanguageSpeaker UNDERSTANDS=ONLY removes automatic Common understanding;
learned languages/translators still work. Visual Sign Language/Semaphore follow
sight rather than hearing.

MPLEARNLANG teaches/changes proficiency and synchronizes live effects. It
respects language caps unless FORCE is explicit. MPSAYIN speaks one line and
restores the prior language. Prop_LanguageImmersion grants saved exposure and
learning with chance, gains, caps and stable source IDs. Test on an ordinary
character: Archons can receive stock languages at full proficiency on login.

Foreign inscriptions can be inline:

```text
^<LANG Elvish NOTE="elegant runes"^>Speak friend and enter.^</LANG^>
```

Everyone sees foreign text; readers with comprehension also receive a partial
or complete translation. This changes output, not the authored source text.

## Vocabulary, socials and help

`world/vocab/glossary.ini` renames whole words/phrases with plural/case support,
scope, exceptions and optional FINAL processing. It is presentation vocabulary,
not a change of engine IDs. Avoid cascading rename loops. Prop_LocalLists can
override contextual health, damage, hit/miss, corpse, magic-word, gait and
time-of-day text. Races and Prop_WeaponMessages provide more specific overrides.
Lists have indexed/grid shapes; changing the number of slots can be invalid.

World socials use the stock tab-separated format, with STOCK/REMOVE controls.
World help files overlay stock help; builder HELP/AHELP edits have final edit
precedence. Hiding stock entries and deleting an effective edited entry use
different markers. A recreated topic clears the final deletion marker.
Provenance is shown to Archons. Do not edit the stock help files to ship a
world's vocabulary.

Run `WORLD RELOAD ASSETS` after direct vocabulary edits. Resource overrides may
need a subsystem reload/restart. Run `CHECKTEXT` and
`WORLD CHECK ONLY=VOCAB,LORE,TEMPLATES,IDS` before publishing; these checks do
not execute every rendering branch. External assets remain live across saves.

## Title, prologue and character windows

Set the accepted GAMENAME world setting for the game's name. The launcher reads
`world/text/title.txt` before the engine starts; `@MudInfo?NAME@` expands to that
name and stock color codes render. `world/text/prologue.txt` appears for a new
game, with pages separated by a line of three or more dashes. This is a launch
presentation format: it does not run NPC scripts or viewer-specific state.
MPNARRATE's section/blank-paragraph format is different and runs in the game.
Keep the opening readable when title/prologue files are absent.

Character windows can show a side panel with STATUS, MAP and PARTY sections.
`WINDOWS PANEL ON` enables it and OFF hides it. WINDOWPANEL can select/order
sections in engine configuration or launch overrides. STATUS uses
`world/text/panel.txt` with prompt/color tokens, or a built-in status layout.
The panel refreshes with the prompt, so put durable game state in its source
variables, not in the panel renderer. Headless play uses the terminal.

**Configuration placement:** several presentation settings are consumed by the
engine but currently excluded from the `world.ini` allowlist. WINDOWPANEL,
COLORALIASES, ROOMHEADER, ROOMFOOTER, MAPFOG, JOURNALAUTO and JOURNALTALK must
currently go in `coffeemud.ini` or launch overrides. `WORLD SET` refuses them.
Their templates, vocab and text files remain world-owned. This mismatch with
some legacy help/comments is an open [audit finding](../../audit/developer-tools-audit.md#open-configuration-gap),
not a reason to assume arbitrary INI keys work in a world file.
