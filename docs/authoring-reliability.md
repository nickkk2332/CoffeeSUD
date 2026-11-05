# Authoring identities, checks and recovery

The presentation, vocabulary and travel properties share an option parser.
Double-quoted values can contain spaces, semicolons and text that resembles
settings. Inside a value, `\"` means a literal quote and `\\` a literal
backslash. For example:

```
SOUND="A label says CHANCE=0; someone whispers \"wait\"." CHANCE=100
```

Only the outer `CHANCE=100` is a setting. Semicolons split detail records
and other statement lists only outside quoted values. Property files retain
their normal Java properties escaping as an additional file format layer.
PROPCHECK and the relevant WORLD CHECK domains report unfinished quotes,
duplicate settings, unknown keys, invalid values and unresolved references.
AHELP for properties with a schema includes the accepted types and ranges.

## Saved source identities

`Prop_ShowWhen`, `Prop_Senses`, each `Prop_Details` record,
`Prop_LanguageImmersion`, `Prop_FirstEntry` and `Prop_Waypoint` accept:

```
ID=crypt-relic ALIASES=old-crypt-relic LEGACYKEYS=FOUND_...
```

An explicit ID replaces names, locations and prose in the saved key. Keep it
when editing text, chances, gains, masks or display names. Give independent
sources different IDs. Sense clues remain separate for SOUND, TOUCH and TASTE;
waypoint IDs are scoped to their network. Put a detail ID after its description:

```
inscription,runes=Ancient letters. ID=crypt-inscription SETVAR=READ_RUNES=1
```

ALIASES lists previous IDs, separated by commas. Reading an old marker migrates
it to the current ID. First-entry and waypoint aliases also recognise the key
format used before this reliability pass. LEGACYKEYS accepts complete old saved
tokens for migrations that also changed a name, room, network or old key shape.
Keep aliases in shipped content while players may still restore older saves.

Without an explicit ID, the first stateful use assigns a saved
`Prop_AuthorIdentity` to the source. Later renames, moves and prose edits keep
that identity. Existing markers and immersion counters migrate when their old
definition still matches. A definition changed before migration needs an
explicit LEGACYKEYS entry. Copies carrying the same identity deliberately share
state; assign a new explicit ID to make a copy independent. A detail without an
explicit ID is additionally keyed by its keyword/alias list.

## World assets and validation

World checksums and WORLD STATUS track `vocab/`, `lore/`, `text/`, `resources/`,
`score.txt`, `ranks.ini`, `crops.txt`, `resources.txt` and `materials.txt`.
These hand-authored assets are separate from the generated files that SAVE
writes or deletes. Existing scripts, routes and generated world definitions
retain their tracking too.

Assets remain live world definitions. Campaign saves preserve learned state
and source identities; they do not embed a private copy of external dictionaries,
lore or presentation files. Restoring against changed assets therefore produces
the world-change notice. Keep the corresponding world version with a campaign
when its exact wording and rules must remain fixed.

`WORLD RELOAD ASSETS` clears dictionary, translation-script, glossary, help and
social caches. WORLD RELOAD CHANGED recognises these tracked assets too.
Resource overrides can need their existing subsystem reload or a restart;
reloading vocabulary does not rebuild faction, race or class definitions.
Those overrides remain listed as changed until their own loader accepts them.
WORLD RELOAD SCRIPT acknowledges the resource dependencies it reads, and
WORLD RELOAD CHANGED dispatches resource script changes to that loader.

```
WORLD CHECK ONLY=VOCAB,LORE,TEMPLATES,ROUTES,IDS
```

| Domain | Checks |
| --- | --- |
| VOCAB | Language files and IDs, INTERPRETS percentages, translation-script syntax/approval, glossary rules, list shapes, language properties and world help markup |
| LORE | Duplicate entry IDs, missing lore files, text markup and property references |
| TEMPLATES | Presentation property options, score/narration markup and race health charts |
| ROUTES | Journey/waypoint/travel settings, route files and stop references, ambiguous waypoint names |
| IDS | Duplicate explicit source IDs; waypoint collisions are errors, intentionally shared presentation IDs are warnings |

File-backed diagnostics name the file and line; object-backed diagnostics name
the room, object and property. Invalid definitions are errors in their dedicated
domain, so headless `CHECK=1` can fail a build. PROPCHECK also reports property
schema problems. Validation does not execute translation scripts, assign source
identities or play through arbitrary script branches. LANG markup is checked
separately from VARIES, including unknown languages and unmatched/nested tags.

## Help edits and deletion

Stock help loads first, then world help files alphabetically, then the builder's
`world_help.ini` or `arc_world_help.ini`. The editor's version therefore wins
over files with names such as `zzz_content.ini`; provenance names that winner.

`hide.txt` and `settings.ini` HIDE still suppress stock entries before world
overlays, allowing a world to replace a hidden stock topic. Builder deletions
use `vocab/help/deleted.txt`, applied after every overlay. `ARC:KEY` affects only
Archon help. Saving a recreated topic clears its final deletion marker.

## Journey recovery

Add recovery options to `Prop_Journey`:

```
MISSING=RESCUE DEAD=CONTINUE DISCONNECTED=TRAVEL
CANCEL=RETURN REFUND=UNUSED
```

| Setting | Values and defaults |
| --- | --- |
| MISSING | WAIT (default), CONTINUE, RESCUE, CANCEL, RETURN |
| DEAD | CONTINUE (default), WAIT, CANCEL, RETURN |
| DISCONNECTED | TRAVEL (default), WAIT, CONTINUE, CANCEL, RETURN |
| CANCEL | HERE (default), RETURN |
| REFUND | NONE (default), FULL, UNUSED |

WAIT pauses without spending another hour and explains why. RESUME retries
recovery. CONTINUE releases the affected seat and lets the remaining party
finish; a missing/dead passenger cannot permanently block that arrival. RESCUE
tries to bring a separated, living, noncombat passenger back through normal
entry/leave checks. An unresolvable passenger still waits. TRAVEL allows a
disconnected character already aboard to stay with the group, including
inactive party characters.

CANCEL follows the configured cancellation destination. RETURN returns the
passengers still together to the origin; separated passengers stay where they
are and their markers are cleared. Refused returns remain paused and can be
retried, including after a save. Leader death or leaving the transit room ends
the trip at the current location.

FULL refunds the fare actually charged to each payer. UNUSED refunds the
fraction of the duration still untravelled. Saved receipts retain the original
currency and prevent a repeated cancellation from paying twice. Successful
arrival never refunds. Trips saved before fare receipts were introduced cannot
reconstruct an old payment, so only newly started trips have refundable fares.
Cancellation and arrival clear the trip's passenger markers.

NPC services also share speech comprehension: guildmasters, stables, hirelings,
teachers, concierges, riddlers and MOB-hosted JavaScript speech callbacks receive
only perceived, understood speech. Partial knowledge supplies partial text;
visual languages use sight, and spoken languages use hearing.
