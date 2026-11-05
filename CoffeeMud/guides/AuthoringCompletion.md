# Authoring completion follow-up

These commands fill gaps identified in the project status review. Builder and
DEV permissions apply as described in their help topics.

- `PLAYTEST ON PARTY=Alice,Bob DIFFICULTY=EASY KIT=scout` brings profile
  companions into the party, applies the optional kit and difficulty, and suspends
  party powers. `PLAYTEST OFF` ends mortal mode and heals the party. This mode
  keeps game changes. `PLAYTEST GAME` provides the disposable campaign workflow.
- `PARTY MORTAL Alice` and `PARTY ARCHON Alice` control one active party member
  in builder mode. ARCHON grants the Archon class through the existing manual.
- `DEVLOG WINDOW WARN MODULE Scripting,Quest*` opens a separate log display.
  `DEVLOG WINDOW OFF` closes it. Terminal ON/OFF and the window have independent
  filters. Headless games use ON or LAST.
- `PLAYTEST RUN encounter.play` and `WORLD TEST` run files in `world/tests` on
  scratch worlds, databases and saves. Results arrive asynchronously. `.test`
  uses the `.play` directives. JavaScript tests can call `test(name, function)`
  and `assert(condition, message)` or `assert.equal(actual, expected)`. They
  execute in a builder test campaign with JRUN's game bindings. Syntax errors,
  thrown errors and failed assertions fail the run. The developer checkout
  needs Python 3 and `tools/playtest.py`.
- `LAW TEST ASSAULT AS Alice AGAINST Bob` evaluates the warrant rules without
  issuing a warrant or changing the game's random stream. It reports why the
  law would refuse; ignore chances use a fixed diagnostic roll. `LAW SET
  SALESTAX=7.25` edits an area-specific custom law; builder edits are world content.
- `PUZZLE LIST`, `PUZZLE SET lever up`, `PUZZLE RESET lever`, and `PUZZLE SIGNAL
  ROOM gate_open arguments` operate the existing puzzle properties and scripts.
  Switch actions and signal handlers run normally. The generic controller in
  I12 remains deferred.

`SCRIPTCHECK` now checks command argument shapes, common function argument
counts, unbalanced WHILE blocks and variable reads with no known assignment in
the loaded script. These are static diagnostics: external state and dynamic
command construction can require author judgment. Assignments in other event
handlers count as possible definitions; the checker does not pretend to prove
that an arbitrary script executes safely.

`GENERATE CHECK` checks unknown attributes, undeclared variable references,
conflicting value requirements and incompatible constant definitions, together
with its existing class, reference, selector, layout and JavaScript checks.
Known optional generation controls and context variables are recognized. Public
library template IDs are callable entry points, so an unreferenced ID alone is
not reported as dead content.

`CHECKTEXT ROOM|AREA|WORLD` includes items, creatures, exits, readable FILE links
and detail aliases. `STAT BEHAVIORS` reports bounded live field summaries.
`CHATLINT` proves shadowing for literal and OR patterns; it avoids assuming that
masks or guarded entries always match. `CHATTEST` includes tick-entry odds and
delay/queue state. Guarded tick odds are conditional on those guards holding.

Item sets share legacy ALLSET counting across adjusters, spell casters, skill
enablers, resisters and extra damage properties. Identification includes tier
progress. Curse identification uses the examiner's level, and weapon strikes on
blocked exits use combat damage and the attack/damage message hooks.

Assisted common skills retain references to the actual producer's effect after
a save. Pending references remain serializable and bind before an assistance
tick performs work. Seeded generation uses its own dice stream; one-time crafting setup no
longer advances that stream or rewinds other threads' campaign rolls. Tests cover
cold runs and crafted equipment. XML text preserves percent operators, and the
JavaScript console preserves template literal backticks.

Arbitrary synchronous native or compiled Java still cannot be safely force-stopped
while it has access to live game objects. Rhino's interpreted instruction budget
and the bounded JSON background-job API remain the supported time-limit mechanisms.
