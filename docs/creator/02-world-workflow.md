# World files and editing

[Handbook](README.md) · [Tool reference](tool-reference.md)

## Source, cache, and campaign

The world folder is the publishable definition of a new game. The database is
a working cache. Campaign slots contain the changes and state of a playthrough.
Editing the cache in a normal or DEV game does not publish those changes to
the source world. Keep your world directory and engine version together.

| Location under `world/` | Content |
| --- | --- |
| `world.ini` | Allowed game rules and starting settings |
| `areas/*.cmare` | Authored rooms, exits, inhabitants and contents |
| `dev/` | Areas loaded only by builder and DEV games |
| `catalog.cmare`, `templates.cmare` | Reusable catalog and player/builder templates where used |
| `custom/`, legacy `custom.cmare` | Generic definitions, one file per object and kind |
| `classes/` | World-owned native Java definitions loaded at boot |
| `scripts/`, `js/`, `macros/` | MOBPROG, JavaScript and RUNFILE commands |
| `quests/` | Quest definitions, generated templates and holidays where applicable |
| `resources/` | Overlay of stock resource paths, including recipes and generation XML |
| `vocab/`, `lore/`, `text/`, `score.txt` | Language, help, socials, lore and presentation assets |
| `origins.ini`, `creation.ini`, `kits/`, `perks.ini`, `ranks.ini` | Character and progression data |
| `materials.txt`, `resources.txt`, `crops.txt`, `itemsets.txt`, `combinations.txt` | Production and item rules |
| `routes/` | World-owned MovingRoom route files |
| `tests/` | `.play`, `.test` and `.js` regression scenarios |
| `.backup/` | Automatic previous versions of exported files |

This is a map of supported content families, not a requirement to create every
file. See the loader and fixture for an individual format in the
[feature index](feature-index.md). Let the editor export generic definitions
before hand-editing their XML. Custom object files may be split per object;
do not assume all definitions must stay in one monolithic `custom.cmare`.

`world/resources/path` overlays the corresponding `resources/path`. Use the
same logical resource name in consumers; avoid modifying stock resources to
make a world-specific change. Structured assets use their own parsers: a
sectioned origin file, a Java properties lore file, a tab-separated social
file and an XML generator are not interchangeable INI formats.

## Editor workflow

`CREATE` starts a generic definition or places an object. `MODIFY` edits one.
`GMODIFY` and MQL updates can apply broad changes, including rooms, exits and
areas. Preview the selection first and use version control for bulk edits.
`LIST` finds definitions; `STAT` inspects live objects. `EDIT` provides multiline
descriptions and scripts. The effects editor supports filtering, editing an
existing property, and duplicate instances; distinguish editing from adding.

`CATALOG` manages reusable mob/item designs and their generation rules. A
placed copy can diverge from its catalog entry. Use categories, stable names,
caps and once-ever rules intentionally. Builder templates speed repeated room
and object design. Catalog instances are not a replacement for per-instance
state or authored source IDs.

`RUNFILE name args` reads `world/macros/name.cmd`, one game command per line.
It substitutes `$1`–`$9`, `$room` and `$area`. `STOPONERROR` stops on a detected
failure; `#` begins a comment. Interactive editors still need terminal input.
`JRUN filename args` runs JavaScript and provides `builder` helpers for digging,
linking, creating, iterating rooms and saving. These operations have effects;
review the target area before running them.

## Protect and publish edits

```text
LIST UNSAVED
SAVE CHANGED
WORLD STATUS
WORLD DIFF areas/my-area.cmare
WORLD BACKUPS areas/my-area.cmare
HISTORY
```

`LIST UNSAVED` identifies live room changes awaiting save. `SAVE ROOM`, `SAVE
AREA` and `SAVE CHANGED` have different scopes; read `AHELP SAVE` before a large
export. `SAVE ROOM HOME` handles creatures according to their start room.
Normal-game `SAVE slot` is a different operation.

An externally changed, added or deleted file is protected. The builder writes
its proposed content alongside it as `name.builder`; inspect `WORLD DIFF` and
reconcile. `SAVE FORCE` deliberately replaces protected disk content and can
accept a partially loaded world; use it only after reviewing the loss.
`WORLD PROBLEMS` reports skipped/unknown definitions and partial loads.

`UNDO` and `HISTORY` track supported builder operations. They are not a complete
transaction log for arbitrary JavaScript and external edits. File backups cover
exports, not every filesystem operation. `WORLD RESTOREFILE file [time]` restores
a previous disk file; use reload to bring it into the running world. Git supplies
the reviewable history for the complete world.

## Reload deliberately

| Tool | Boundary |
| --- | --- |
| `WORLD RELOAD areaName` | Rebuild an area from disk; players/followers move out and return; unsaved content can block it |
| `WORLD RELOAD SCRIPT file` | Reload file-backed scripts; `RESET` also resets trigger state |
| `WORLD RELOAD QUEST name` / `QUESTS` | Reload quest definitions and their running step |
| `WORLD RELOAD CATALOG` | Reload reusable catalog definitions |
| `WORLD RELOAD ASSETS` | Clear vocabulary, dictionary, translation, help and social caches |
| `WORLD RELOAD CHANGED` | Dispatch supported changed files to their loaders |
| `JS RELOAD [file/ALL]` | Replace compatible JavaScript classes/scopes using declared state contracts |
| `DEVWATCH ON [seconds]` | Watch world files and resource scripts; changes are logged |
| `JS WATCH ON [seconds]` | Watch JavaScript files in builder mode |

Reload is not a universal restart. Native classes are tracked at boot, but asset
reload does not replace Java binaries. Some resource-backed subsystems need
their own cache unload or a restart; they remain changed until accepted.
`WORLD SET KEY=value` writes a rule for the **next startup**. In a normal game
the full world-reload path saves and restores a campaign against the files.

## Shared authoring grammars

Many systems reuse zapper masks. `-RACE +Human +Elf` requires one of those
races; `-TATTOO +ORIGIN_COAST` requires that tattoo. Quote multiword arguments,
for example `-AREA "+New Area"`. Use `AHELP MASKS` for the running engine's
full key list. Some positive keys, particularly +IF and +JS, are permissive
overrides; use their negative forms for required script conditions. Test both
an allowed and a refused character instead of judging a mask by its wording.
The consumer determines whether it tests a viewer, owner, arriving player,
room or item. The same mask can mean something different on a travel gate and
a shop recipe if their subjects differ.

Formulas also have context. `@x1` might be caster level in one field and the
crafter's level difference in another. Read that field's help. Engine math can
evaluate left to right; bracket arithmetic deliberately rather than relying
on JavaScript's operator precedence. A formula string, a dice expression, a
percentage and a JavaScript expression are not interchangeable formats.

Timing units are explicit: ticks for a switch reset or effect, game hours for
a journey or crop rule, minutes for TravelTime, and seconds for a JS runtime
limit. A calendar hour need not equal a fixed number of real seconds under all
time modes. Avoid copying a number between fields without checking the unit.

Message templates such as `<S-NAME>` and `<T-NAMESELF>` refer to the event's
source and target; verb suffixes such as `(s)` select the appropriate view.
They need an actual message context. Prompt `%` codes, MOBPROG `$` codes,
VARIES tags, Java properties escapes and XML escapes are separate layers.
The cookbook labels hosts and file formats to keep those layers clear.

## Stable identities and migrations

Use separate concepts for a definition, a physical copy and a story fact:

- A catalog/class ID identifies a reusable definition.
- A mob or item's persistent instance ID identifies that particular copy.
  Moving or renaming it retains identity; cloning creates a new one.
- A source ID identifies a clue, first-entry event, immersion source or waypoint
  across content revisions. Preserve it when changing prose or moving the source.
- A tattoo or script/store key identifies a story fact. Pick a documented scope:
  character, profile, instance, quest or world.

Presentation and travel properties supporting source IDs accept `ID=`,
`ALIASES=` and `LEGACYKEYS=`. Independent sources need different IDs. Copies
with the same authored ID deliberately share progress. Without an explicit ID,
a saved `Prop_AuthorIdentity` is assigned on first stateful use; edits before
migration may need a complete legacy key. See [identity and recovery details](../authoring-reliability.md).

Campaign saves record accepted world file versions; external assets stay live.
They do not contain private copies of dictionaries, lore and script files.
Version and distribute the matching world when a campaign must keep its rules
and wording. [Release and save testing](13-testing-release.md) covers recovery.
