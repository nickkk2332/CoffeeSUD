# Completion of the remaining implementation gaps

This follows `project-review-fixes.md` and addresses the 18 actionable gaps
left in the follow-up inventory: 17 partial implementations and the G17 defect.
The current inventory is **388 implemented, 1 partial, 20 deliberately deferred**.
Historical baseline statuses remain in their original columns.

The remaining partial is JS2's unsafe forced-preemption boundary for arbitrary
synchronous native/compiled Java. The interpreted instruction budget and bounded
JSON background jobs remain available. This pass does not change the deliberate
deferrals or the executions draft.

Implemented changes:

- G17: XML restoration handles XML entities without URL-decoding percent signs.
- JS21: JCONSOLE preserves backticks and template literals.
- DT8: PLAYTEST party/kit/difficulty options and PARTY MORTAL/ARCHON controls.
- DT9: separate display-only DEVLOG WINDOW with filters and retained history.
- DT15: PLAYTEST RUN and WORLD TEST run disposable campaigns; .play, .test and JS tests.
- DT11: interpreter-derived command signatures and variable-assignment diagnostics.
- DT14: generator variable declarations, requirements contradictions, attribute checking.
- DT17: text checking on room contents, exits, readables, FILE links, and detail keywords.
- DT19: bounded live field summaries for behavior diagnostics.
- DT21: read-only LAW TEST through warrant eligibility rules and custom LAW SET.
- DT33: console for existing switch, plate and signal primitives; I12 remains deferred.
- I6: mixed legacy ALLSET bonus properties and identification progress.
- I7: identification respects the configured curse detection level.
- I13: weapon attacks on blockages use combat damage and message hooks.
- S16: assisted activities save and rebind their live Ability references.
- SC14: original scriptableTest.script automated with its documented setup.
- G1: removed the seed warm-up, isolated setup dice and avoided rewinding other threads;
  cold snapshots include crafted equipment and test multiple seeds and fresh processes.
- DT4: conservative literal/OR pattern shadowing checks and tick-entry probability,
  delay and queued-response analysis.

Deliberate boundaries:

- JS2: arbitrary synchronous native/compiled Java cannot be forcibly preempted safely
  while it mutates the live game. Bounded JSON background jobs exist from the prior pass.
- Explicit deferred features in LATER.md, including the generic puzzle controller,
  and the executions design draft are outside this completion pass.


The generator checker exposed real shipped content defects, which are repaired:
shared adjective includes and name/display transforms now live in shared libraries;
misspelled material/junk references are corrected; planet generation uses valid
selection attributes, merged definitions and a defined layout selector.

S16 links use an owner's persistent identity plus the ability ID. Pending links
survive another save and bind before an assistance tick does work. Generic objects
that already carry an effect still receive its saved activity data. The played
regression invokes Construction's native HELP path, saves, starts another process,
and verifies that assistance advances the restored producer. Shipwright and generic
wright helpers also avoid ending simply because they have no separate work item.

The original self-test now runs to its `ERRORS=NONE!` completion marker, alongside
the focused MOBPROG regression fixtures. Its keyword list escapes a literal SCRIPT
marker, and its harness supplies its old channel and explicit lock/key setup. No
new channel is added to the shipped game configuration.

Usage and analysis boundaries are documented in
[`AuthoringCompletion.md`](../CoffeeMud/guides/AuthoringCompletion.md).

## Verification

Targeted checks completed before the full suite:

- Focused generation, console, item, law, chat, text, behavior and puzzle fixtures.
- Original scripting self-test completion.
- Actual log-window creation, filtering and closing under a virtual display.
- Legacy skill/spell bonuses activate and revoke through normal equip/remove messages.
- Cold generation snapshots, including crafted equipment and campaign dice isolation.
- Separate-process assisted activity and custom-law persistence, party presets,
  per-member powers, and both in-game runner commands.
- Standalone `.play`, `.test`, successful JavaScript and intentional failing JavaScript
  tests; failed assertions return a failing process exit status.

Four seed snapshots (370170, 13, 14, 91827) produced identical SHA-256 hashes
across separate fresh game processes, with no seed warm-up.

Final results (see [`remaining-verification.json`](remaining-verification.json)):

| Check | Result |
| --- | --- |
| JavaScript regression sequence | 3,475 passing checks across 332 fixtures |
| Played regression suites | 442 passing reported checks |
| Smoke | 97 passing checks |
| Engine | 47 passes and the same 3 known baseline failures |
| Additional graphical log view | 4 passing checks under a virtual display |

The main regression suites report **3,917 checks**; the separate graphical checks
bring that to **3,921**. Nested assertions in the played subprocesses and the
standalone runner checks are additional.

The initial aggregate `tools/check.sh` invocation completed all played, smoke and
engine checks but failed a new blockage test that assumed a particular random
damage result. A reference-roll comparison was also invalid because message
handlers can consume dice. The final assertion uses CNCLMSG to set damage to 13
and verifies the exact HP change, checking the supported message contract directly.
The complete 332-fixture JavaScript sequence then passed in an isolated game copy.
Its Java sources are byte-identical to the final checkout; no engine code changed
between the aggregate build and this replay.
