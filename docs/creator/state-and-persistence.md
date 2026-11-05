# State, persistence and reload matrix

[Handbook](README.md) · [World workflow](02-world-workflow.md)
· [Save testing](13-testing-release.md)

A world definition answers “what starts a new game?” A campaign snapshot
answers “what has happened in this playthrough?” Code registration answers
“what functions should run in this process?” Keep these three responsibilities
separate when designing a reusable system.

| System | Campaign behavior | Author responsibility |
| --- | --- | --- |
| Rooms, exits, mobs and items | Live changes and supported object state persist | Publish initial definitions in builder mode; test references after a fresh restore |
| Physical mob/item identity | Persistent instance ID survives moving/renaming; copies get independent identities | Resolve saved references by identity rather than ambiguous names |
| Authored clue/waypoint identity | Stable source IDs preserve discovered/used state | Keep IDs across revisions; give independent copies new IDs; maintain migration aliases |
| MOBPROG variables and per-object state | Supported saved scopes persist | Choose character/profile/instance/world scope deliberately |
| Script trigger history and queues | Supported ONCE/time/delay guards, alarms, waits and pending responses persist | Use explicit stages; do not assume an arbitrary active interpreter stack is saved |
| Local variables/function parameters | Temporary invocation data | Put durable facts in saved state before suspending |
| JS cm.store | JSON-compatible data persists | Store values/IDs, not functions or live Java objects |
| JS listeners, commands, verbs, every/at schedules | Code registration is recreated at boot | Register idempotently in init.js; use stable registration keys |
| JS after callbacks | One-shot timer/data and function text persist | The restored function must use data/cm rather than captured locals |
| Background jobs | Cancelled on new game/restore; not saved | Handle cancellation, timeout and missing results; apply world changes in the callback |
| Generic/native effects and behaviors | Engine-supported state hooks persist | Declare STATE/ACTIVITY or implement the save contract for custom state |
| JS reload | Compatible declared state and effect metadata migrate; incompatible instances stay old | Read the report; test clean restart as well as reload |
| Pending menus | Displayed options/source binding persist | Source edits can cancel a pending choice; keep dependent work in its answer handler |
| Paged narration | Unread pages and temporary pause ownership persist | Avoid overlapping input; respect manual pauses and headless fallbacks |
| Quests | Controller, participants, steps, objectives/outcomes and generated definitions persist | Keep IDs and named steps stable; test sharing, deadlines and exactly-once rewards |
| Crafting, crops and supported assistance | In-progress activity and supported producer references persist | Custom transient object references need a state contract; assistance binds before a work tick |
| Clans, factions, legal state | Membership/control/dues and supported standing, warrants/fines/records persist | Export definitions separately; test timetable jumps and jurisdiction changes |
| Vehicles, journeys, instances | Supported interiors, riders, route/leg progress, limits and visit timers persist | Configure missing participants/hosts, reset policy and safe recovery destinations |
| Map knowledge, lore, journals and learned languages | Character/profile knowledge and exposure state persist | Decide scope and source identity; definitions and text remain external assets |
| Game clock and dice | Saved clock/dice position restores | Native real-time I/O, Date and code changes prevent a general exact-replay guarantee |
| External scripts, dictionaries, lore and text | Live world files, with accepted-version provenance in the manifest | Distribute matching world versions; a campaign does not embed a private asset bundle |

## Design a saved state machine

For a multi-stage interaction, save a small stage name, durable participants,
required room IDs, and the remaining game-time delay. At each resumed step,
resolve the participants again and check whether the action still makes sense.
Give missing, dead, distant or disconnected participants an explicit outcome.
Treat “waiting for a menu answer” and “waiting three ticks” as different states.

Do not serialize a closure that happens to point at the right mob today.
Do not let rendering a description advance the state machine. Give rewards at
one authoritative transition and mark that transition durably before it can
be repeated. Use a named cancellation/reset path for encounters and puzzles.

## Version a source independently from an instance

A named catalog sword is a definition. Two copies are two physical instances.
An inscription's ID is an authored discovery source and can intentionally be
shared by copies. A quest ID names a story/controller. A display name is prose.
Moving a physical sword, renaming a clue and replacing a quest title therefore
have different migration consequences.

Use ALIASES for supported old authored IDs and LEGACYKEYS for complete old
saved tokens when key shape/location changed. Ambiguous legacy physical-name
references remain unbound rather than choosing a random candidate. Keep a
pre-change save for compatibility testing; a test made after the edit cannot
prove that migration works.

## Verify across a real process boundary

Start an ordinary campaign, reach the interesting intermediate state, save and
quit. Start a new process and restore. Complete or cancel the interaction;
save and restore again. Then start NEW and confirm it receives the authored
initial state rather than inheriting the previous campaign. Repeat with a
second character when sharing is involved.

The [museum pack](examples/README.md) automates a small version of this test.
The feature index links the larger save, travel, dialogue, production and
instance regression scenarios. A field surviving an in-process reload is
useful evidence, but it is not a substitute for this process-boundary check.
