# Developer-tool and creator-documentation audit

Date: 2026-11-05. This audit follows the implementation completion work and
accompanies the new [creator handbook](../SUDDocs/creator/README.md).

## Scope and method

The review reconciles the current feature inventory with author-facing entry
points, permissions, source ownership, persistence, reload and diagnostics.
It covers the 18 feature families, all 409 reviewed entries, 90 command classes
referenced by that inventory, and 342 shipped help topics selected from commands,
properties, behaviors, generic definitions and their fixtures. Source links and
current command securityCheck bodies are published in the reference set.

The status baseline is the inventory's **followup_status**, not its historical
status column: 388 implemented, one partial, 20 deferred. This documentation
pass does not claim to retest every combination of those features. Existing
evidence is recorded in [remaining verification](remaining-verification.json)
and [the completion report](remaining-implementation-fixes.md). The new example
pack adds a complete author/publish/play/restore path and an exporter regression.

Inputs reviewed include command implementations and their operation-level
checks; WorldFiles/WorldSettings and save/reload code; JSCm, JSBuilder,
JScriptable, timers and job contracts; ScriptCheck; creation and recipe formats;
world asset loaders; the feature fixtures; current help; README and the SUD
manuals; presentation, vocabulary, organizations, party and travel references.
Historical “Today/Add” audit proposals are not treated as current API contracts.

## Findings and disposition

| Finding | Consequence | Disposition |
| --- | --- | --- |
| No single current creator entry point | Authors must reconstruct workflows from proposals, help and legacy manuals | Added handbook, task navigation, 13 system/workflow chapters, 28 cookbook recipes and runnable examples; linked from README and SUD guides |
| Historical statuses can look current | Implemented features appear unfinished, or a proposal appears usable | Generated all-409 index uses followup status and separates partial/deferred work |
| Source world, cache and campaign boundaries unclear | Accidental source edits, unshipped runtime changes or misleading tests | Added mode/save matrix, ownership/reload/recovery chapters and release workflow |
| Old JS store prose describes location/name identity | Authors may key state incorrectly or fear renaming/moving objects | Corrected API manual and documented persistent instance IDs and legacy migration |
| README promises exact restoration without asset qualification | Changed dictionaries/text/scripts can surprise existing campaigns | Clarified live external assets and world-version provenance |
| README implies most INI keys can be world overrides | Engine settings may be silently ineffective in world.ini | Added generated exact allowlist and precedence; corrected README |
| Inline markup changed by readable world export | A saved/reloaded VARIES room description gains indentation and displays literal markup | Fixed WorldFiles layout to keep literal name/display/description/property values intact; added targeted regression and real publish/restart example |
| cm.cmd bypasses queued local-verb interception | A scripted test may invoke stock PULL instead of Prop_Switch/VERB handling | Documented forced-path contract; tests use played input or the explicit queue entry point |
| DEV group grants GOTO | DEV/runner movement can bypass closed doors, giving false confidence in gating tests | Documented test privilege boundary; fresh-process museum save/restore sessions use ordinary non-DEV games |
| Playtest assertion markers share the player's input stream | An assertion between a menu and its answer can become the answer | Cookbook/playtest guidance groups interactive input before assertions |
| Generic-ability help uses AFFECT as an attachment command | AFFECT/AFFECTS actually displays effects | Corrected help and cookbook to use the MODIFY effects editor |
| Help/parameter detail is hard to discover | Examples omit valid options or mix distinct grammars | Added generated help/reference snapshots with provenance and explicit host/file/scope labels |

The exporter change protects literal values while retaining readable structural
indentation. It does not automatically repair world files whose literal text
was already changed by a prior export; re-enter the intended text and save, or
recover the source from version control/backups. It does not treat every nested
XML element as arbitrary text. Scripts and other structured fields keep their
existing serialization paths.

## Open configuration gap

The runtime consumes WINDOWPANEL, COLORALIASES, ROOMHEADER, ROOMFOOTER, MAPFOG,
JOURNALAUTO and JOURNALTALK, but WorldSettings's current allowlist excludes all
seven. Some inherited help/comments describe them as world.ini settings.
A direct probe of `WorldSettings.isWorldKey` returns false for these seven
and true for GAMENAME. Consequently WORLD SET refuses them and world.ini
cannot currently carry these defaults as advertised.

Workaround: supply them in coffeemud.ini or launch overrides and distribute the
configuration/launcher with the world. Templates, dictionaries and text files
remain world-owned. The handbook labels this placement explicitly. A future
implementation fix should add selected keys to the allowlist and test new-game
configuration, precedence and reload/restart expectations. This pass reports
the gap; it does not silently claim those keys are world-owned.

## Open script-lint path gap

Runtime Scriptable LOAD accepts `world:scripts/museum-guide.script`; the museum
conversation verified it in a dedicated probe. SCRIPTCHECK ALL in the same published world
reports `the museum guide (inline):1 LOAD=world:scripts/museum-guide.script: no
such file`. ScriptCheck.fileText/checkFile pass the path through
Resources.makeFileResourceName, which prefixes `resources/` without resolving
the world: scheme. This can also affect WORLD CHECK's SCRIPTS domain.

Workaround: put the source in world/resources/progs/name.script and refer to it
as LOAD=progs/name.script; the resource overlay remains world-owned. The guide
labels this limit; the final museum pack uses the lintable resource-overlay
path. A second probe confirmed no SCRIPTCHECK problems with that path. A future fix should share the interpreter's resolver,
including parameterized LOAD handling, and test both lint and execution.

## Open property-key validation gap

The published museum passes headless CHECK=1 with zero errors and four warnings.
Two warnings are expected starter-area reach/text warnings. Two report valid
Prop_Switch MSG_UP and MSG_DOWN as unknown keys. The played/API checks verify
these messages and the corresponding state transitions work.

PropCheck derives accepted assignment keys from literal KEY= strings in help
plus explicit schemas. It does not currently model arbitrary state suffixes,
so dynamic MSG_state keys can be flagged even when the property accepts them;
other state-key families can have the same structural limitation. The guide
calls out the warning and retains the supported options rather than changing
the example to an unsupported spelling. A future fix should give such families
a schema/prefix contract, while continuing to reject misspelled unrelated keys.

## Open cached-catalog smoke failure

The full current-build smoke sequence twice failed its unique-container scenario:
94 checks passed and three failed. The catalog item was present, but effective
policy read `NONE TIME cap 0` instead of `CONTAINER ONCE cap 1`, so the expected
Sunblade did not spawn. The same full smoke sequence with the previous exporter
passed all 97 checks.

A controlled restore comparison separates the observed cache state from the
exporter implementation. With separate database/save copies and the same source
world paths, the previous exporter also reads NONE/TIME/0 from the failed cache;
the current exporter reads CONTAINER/ONCE/1 from the healthy cache. Neither probe
rebuilt its cache. A short new/save/restore test with the previous exporter also
preserved the expected policy. Only WorldFiles class files were exchanged in the
comparison game; the source checkout's runtime remained unchanged.

This establishes that the bad state is already in that cache; it does not explain
how the two full runs produced different cache histories. The cache-creation
cause remains unresolved in this documentation pass. Do not describe the aggregate
check as passing. Worlds relying on catalog uniqueness/caps should verify their
policy after fresh starts and process-boundary restores, including cache reuse.
This finding is separate from the confirmed/fixed literal-text formatting defect.

## Current tool boundaries creators must understand

- JS2 remains partial. Interpreted instruction budgets work, and JSON background
  jobs isolate blocking work. Arbitrary synchronous native/compiled Java with
  live object access cannot safely be force-stopped. Background jobs have bounded
  workers/queue, discard late results and need explicit I/O timeouts; they are
  not saved and cannot capture game objects or closures.
- PLAYTEST ON/OFF changes the current world; it is not rollback. PLAYTEST GAME
  provides a separate disposable snapshot/process. Archon mortal mode suspends
  powers, but a representative character still needs representative stats/skills.
- Static diagnostics are useful and intentionally incomplete. Dynamic/external
  script state, guarded chat probabilities, arbitrary JS, narrative solvability
  and balance still need runtime tests. WORLD CHECK does not assign clue IDs or
  execute every translation hook.
- WORLD SET takes effect at startup. Asset/script/class reloads have different
  contracts. Resource overrides can need subsystem reload/restart; native Java
  is not hot-swapped by asset reload. JS replacement migrates declared state,
  and incompatible instances retain the prior class with a report.
- World files and text assets are live external definitions. Save manifests
  record accepted versions rather than embedding a private world bundle.
  Supported version migration is not arbitrary backward compatibility.
- Authored source IDs, physical instance IDs, class/catalog IDs and display names
  serve different purposes. Copying a clue with the same authored ID deliberately
  shares progress; copying a physical item gives it an independent instance store.
- The Python world runner is a developer-checkout tool, not an engine-only
  distribution feature. Each file receives isolated saves; a later test file
  cannot restore a slot from an earlier file. `.test` is the `.play` grammar.
- Numbered dialogue, narration and windows share input-stream ownership rules.
  New interactions can replace earlier ones. Pending interactions restore with
  source checks and defined headless fallbacks.
- Headless games cannot display a graphical log window. DEVLOG terminal/history
  and the printed PLAYTEST GAME launch command are supported alternatives.
- Builder command gates do not describe every subcommand. Source excerpts in
  the command audit are navigation aids; execute() can impose further target,
  filesystem, builder/DEV and operation-specific checks.

## Deliberate deferrals

The [feature index](../SUDDocs/creator/feature-index.md#deferred-work) lists all 20
deferred entries, with links to LATER.md. They include the generic companion
and `.dlg` dialogue controllers, dedicated LLM services, rumor service,
area invasions, generic puzzle controller, linked profile stash, gradual
restocking, exposure, bounty posters/hunters, persistent market, autonomous
clan warfare, deity holy-day/favor/portent integration, clan disguise, timed training, CHATDRAFT,
exact deterministic replay and SIMFIGHT.

Existing recruitment, MudChat/MPMENU/Scene, JSON workers, waves, puzzle primitives,
housing, shops, climate, warrants, diplomacy/conquest, teachers, recording and
combat diagnostics remain available. The executions proposal remains a draft
outside the reviewed 409; it is not documented as an implemented cutscene API.

## Documentation maintenance and verification

`python3 tools/creator_docs.py --write` derives feature coverage, the source/help
reference, command gates and the world settings list. `--check` verifies freshness,
all inventory paths and local links/anchors in the new handbook/audit. A new
feature family requires an explicit chapter mapping. Coverage is discoverability
evidence, not proof that a chapter explains every edge case.

`python3 SUDDocs/creator/examples/check.py` builds the supplied museum on temporary
world/database/save copies, publishes it, runs ordinary-character and API tests,
then exercises a campaign save and restore in separate ordinary game processes.
Failures retain transcripts. A separate s25-markup fixture checks literal markup
and authored multiline text survive readable formatting.

Results are captured in [creator-guide-verification.json](creator-guide-verification.json):

| Check | Result |
| --- | --- |
| Documentation freshness, links, anchors and inventory paths | Pass: 409 mappings, 342 help topics, 90 command gates |
| Final museum pack | Pass: 15 played checks, six JS test cases and nine build/save/restore transcript checks |
| Focused literal-export regression | Pass: 13 checks |
| Full JavaScript sequence | Pass: 3,488 checks across 333 fixtures |
| Played suite in aggregate run | 429 passing checks; graphical case could not start because xvfb-run was absent from PATH |
| Separate graphical panel check with workspace Xvfb | Pass: all 13 checks; combined played coverage is 442 |
| Engine suite | Matches baseline: 47 passes, three known space-test failures |
| Headless museum check using lintable source path | Pass: zero errors, four documented warnings |
| Current smoke, aggregate and replay | Both: 94 passes, three unique-catalog failures |
| Previous-exporter full smoke | Pass: 97 checks |
| Catalog restore comparison | Failure follows failed cache with old exporter; healthy cache works with current exporter |

The initial `FIXES_JOBS=2 ENGINE=1 tools/check.sh` command exited with failure.
Its fixes failure was the missing graphical executable on PATH; the separate
actual display run filled that coverage. Its smoke failure reproduced in a
standalone replay and remains open as described above. The complete aggregate
command itself is not relabelled as a pass.

Together, the full JS sequence and combined played coverage provide 3,930 passing
checks, with the smoke caveat reported separately. Museum checks are additional;
the 13 focused exporter checks are also part of the full JS count and should not
be counted twice. Python helper syntax and staged whitespace checks passed.
