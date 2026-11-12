# Stage 5 item modifiers and crafting

Date: 6 November 2026 UTC. Parent: `f679452c` (Ember Trial, Stage 4).

This slice extends existing affix XML, generation, loot tables, workstations,
raw resources, module state and combat stat sources. It does not replace the
legacy crafting or enchantment systems. The new handler is default off.

The [creator guide](../SUDDocs/creator/17-item-modifiers-crafting.md) documents
schemas, commands, source pins, limits, transactions and remaining scope.
The [Ember Trial](../SUDDocs/creator/examples/ember-trial/README.md) demonstrates
generated equipment and all five selective operations with an ordinary player.

## Final focused validation

| Check | Result |
| --- | --- |
| Build with JDK 21 targeting Java 8 | Passed |
| `tools/item-modifier-tests.sh` | 275 passed |
| `tools/fixes.sh item-modifiers` within the focused run | 52 passed |
| Existing generation seeds, loot, affixes, combat profiles and ability composition | 118 passed in the same run |
| Final `tools/fixes.sh itemcraft` | All 60 checks passed after the validator refinements |
| Creator reference freshness, local links and anchors | Passed: 409 original feature mappings, 353 help topics, 94 command gates |
| Both research dossier verifiers | Passed |
| Python compilation, shell syntax and `git diff --check` | Passed |

The modifier checks exercise real ownership, worn sources, selective changes,
locks, partial/whole material costs, stale inputs, settled observers, repeated
commits, managed copies, strict XML/material validation and malformed ledgers.
Preview/preparation preserve dice and resources. The native save APIs reject
the critical mutation interval. Existing numeric deltas do not acquire an
absolute critical-multiplier default when combined.

The played scenario saves a prepared request, commits after a fresh restore,
repeats settlement, protects/unprotects/removes/adds modifiers, consumes the
last material pile, reopens spent loot, suspends/resumes the module, starts NEW,
and restores an unopened coffer across suspension/reactivation.

An actual Stage 4 `ModuleDefinitions`/`OptionalModules` reader was compiled
separately from `f679452c`: it refuses an items.modifiers native capsule before
restore. The current reader retains the requirement when its definition is
removed, and refuses an unknown future native handler. These are three passing
compatibility probes, not an emulated old-reader test.

## Broad regression run and limits

`ENGINE=1 FIXES_JOBS=10 tools/check.sh` ran before the final validator refinements.
Its aggregate exit status was **1**; it is not claimed as a clean aggregate run.

| Suite | Observed result |
| --- | --- |
| Native fixtures | 337 completed; 3,700 checks passed; no script errors |
| Complete fixes suite | 4,336 checks passed, three failed assertions in two legacy restore scenarios |
| Smoke | 96 passed; one pause-clock check failed |
| Engine TEST ALL | 47 passed; Spaceangles, Spaceinterception and Spaceinterception2 failed exactly as in the recorded baseline |
| Original Ember Trial adventure | All 62 checks passed, including ordinary combat, completion, rescue, fresh restore and source-world isolation |
| New crafting campaign | All 60 checks passed in this broad run |

The S17 restore process ended during startup in the parallel run. The host also
recorded reaching its memory limit and OOM kills; the transcript alone does not
identify a product defect. An isolated copy of its real save restored and
passed all 12 assertions.

The S19 re-entry assertion ran before its queued DOWN completed: the transcript
shows the assertion in New Area followed by arrival at the crypt stair. Waiting
for native movement output makes all six restore/re-entry assertions pass.
The fixture now waits for each move before invoking its zero-cost inspector.

The smoke pause scenario's explicit-real-time replay passes its five clock
samples and pause/resume assertions. Its fixture now selects real-time mode
before sampling. The full broad run was not repeated after those fixture
changes; the scoped replays passed.

Final validator refinements diagnose out-of-definition saved rolls/contracts,
unregistered material names and empty AFFECT children. They accept native
multiword material names and reject noncanonical receipt keys before rolling
or spending. The final focused and played checks above validate those changes.

## Intentional boundaries

Tracked modifiers currently own numeric profile deltas, with up to six modifiers
and eight integer rolls each. Costs use plain GenResource piles; operations are
instantaneous at a matching workstation. Missing pins suspend crafting while
saved numeric rolls remain frozen. The itemCrafted hook is advisory after
settlement, not an arbitrary callback rollback transaction.

Arbitrary effect layers, additional rarity/guarantee policies, currency families,
staged choices, base upgrades, corruption, recombination and reward escrow remain
separate work. Neither the original default world nor the existing Stage 4
deployment was replaced.
