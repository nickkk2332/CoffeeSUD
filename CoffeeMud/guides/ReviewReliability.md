# Campaign and authoring reliability

`PLAYTEST ON/OFF` suspends builder powers in the current world and keeps its
changes. `PLAYTEST GAME` takes a snapshot into a disposable campaign, with its
own world, database, saves and log. It opens another window when a display is
available. Closing that window discards the test; the builder process remains
open. Without a display, the command prints the prepared campaign's launch
command. Run it from the stated directory, then remove that temporary folder
when finished. Current party/character state is carried into the snapshot.

Compiled `JS RELOAD` replacements migrate behavior `STATE` and ability
`STATE`/`ACTIVITY`, along with effect duration, proficiency, invoker and saved
flags. An incompatible instance stays on its old class and is counted in the
reload report. Custom fields need a public state contract; the loader cannot
infer arbitrary Java field semantics. Old library scopes retain their imports. State is captured before old-instance
cleanup and reapplied after the new instance attaches; timed effects are not recast.

Pending dialogue choices retain the displayed options and source binding.
Restoring reissues the choice after hosts and party members are available; a
changed or missing source script ends it with an explanation. Narration saves
the unread pages and whether it owns a temporary real-time pause. Finishing
restored narration releases its pause while preserving a prior manual pause.
Background-party choices return to the shared terminal, one at a time. If a
saved windowed narration returns without a visible window, its unread pages are
shown on the shared terminal and its hold is released. A new menu or narration
replaces the other interaction on that input stream.

For blocking work, use JSON data and a separate worker scope:

```javascript
var job = cm.background(function (data) {
    // No cm, game objects, or closures in this scope. Java I/O may be used here.
    return { answer: data.number + 1 };
}, { number: 41 }, 2000, function (error, result) {
    // Runs on the game thread; game changes belong here.
    if (error) debug(error);
    else cm.store("world").answer = result.answer;
});
// job.cancel() requests cancellation. Timeouts discard late results.
```

Workers are a bounded daemon pool with a bounded queue. A timeout requests
interruption and completes once; Java operations that ignore interruption may
retain a worker, but never hold the game thread or deliver a late result.
Jobs are cancelled across new games/restores and are not saved. This API does
not turn arbitrary synchronous Java calls or compiled classes into preemptible
code. Keep those calls short and use explicit I/O timeouts in workers.

Each save folder has a process ownership lock. Separate launchers must use
separate folders or close the existing game. Party windows within a process
share its ownership. Locks are released by the OS after a crash; the lock file
itself may remain.

New snapshots sync their files, include a SHA-256 completeness manifest, and
retain the preceding complete generation. Startup recovers interrupted rename
transactions before presenting slots. Damaged public generations are preserved
under `.damaged-*` when a complete backup can be restored. Directory syncing is
best effort on platforms that do not support it; this is not a guarantee against
every filesystem or hardware failure. Version-1 legacy saves remain readable;
unsupported save/world versions are rejected before replacing game state.

Saves also include `world-manifest.properties` and record the version accepted
by the running process, including explicit asset/script reloads. Assets remain
external and live; the manifest records provenance rather than embedding them.
Native Java files under world/classes/ are tracked at boot; asset reload does not
replace those classes. The expanded catalog changes the checksum scheme to version 3, so an older
save may report a world-version change on its first restore.

Mutable mob/item stores use persistent instance IDs. Moving or renaming an
object keeps its store; copying it creates a distinct instance. Unambiguous old
name stores migrate on access. Ambiguous old data stays available under its
explicit string key. Old queued-actor and journey-host references are rebound
only when their exact name selects one candidate; ambiguous bindings are left
unbound with a log warning. New references use instance IDs.
