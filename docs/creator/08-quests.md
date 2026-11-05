# Quests and story state

[Handbook](README.md) · [Quest feature index](feature-index.md#q)

## Separate the controller from a character's progress

A loaded quest controls world setup, selected objects, timing and steps.
Individual characters have acceptance, objective, outcome, win and cooldown
state. Stopping a global controller and abandoning one character's quest are
different operations. Decide whether the quest is personal, party-shared or
profile-shared before writing reward code.

Store files in `world/quests/`. Use CREATE/MODIFY QUEST or start with a shipped
template. The installed `AHELP QUESTS`, quest templates and `DefaultQuest`
implementation are the current grammar authority.
CoffeeSUD's [scripting supplement](../../CoffeeMud/guides/ScriptingSUD.html)
documents the additions. Do not substitute a MOBPROG file for a quest controller.

## Controller structure

Quest commands set NAME and DURATION, select areas/rooms/mobs/items, load
content, give/take scripts or objects and advance steps. Ensure required
selections resolve. A minimal controller file can be:

```text
set name museum_demo
set duration 1000
set room any
step break
```

This is a controller scaffold, not a complete player quest: it has no acceptance
NPC, objective or win handler. Add those deliberately. SET WAIT without SET
INTERVAL can cause a controller to disappear after its first stop. Use
QUESTCHECK to catch malformed commands and missing files before starting.

Named steps and jumps support branching arcs. Objectives, dates, location
hints and outcomes are part of player-visible quest history. Use QUESTS detail
and WHERE to show the next meaningful action. Main quests can be undroppable
and have an explicit drop hook. Keep quest IDs stable when changing titles.

## Acceptance, objectives and rewards

Define availability and acceptance separately. Re-offer/decline policies,
repeats, cooldowns and win counts determine whether an NPC should ask again.
Quest markers and notices should reflect the same eligibility rules as the
acceptance code; a marker alone does not make a quest accept.

`SET REWARDS` declares rewards using the supported award syntax, including
percentage-of-level XP, catalog items, faction changes, scripts, effects,
variables and known recipes. `MPQUESTWIN player quest REWARD` awards the
declared reward; `MPREWARD` can give an ad hoc bundle. A normal win call and a
separate reward loop can double-pay if both are used. Test repeat completion
and party sharing rather than assuming a boolean WON flag covers every case.

`Prop_QuestItem` binds an item to a quest and supports NODROP, NOSELL, NOGIVE,
KEEPONDEATH and REMOVEON outcome policies. Default removal is broad; set it
explicitly when an artifact should remain after completion. Quest cleanup
must leave the world navigable.

## Time, sharing and persistence

Deadlines and game-time quest schedules use the game's calendar. Long WAIT,
journeys and crafting can consume that time. Decide whether pausing and travel
should preserve or advance urgency; communicate deadlines with dates the player
can inspect. Saved quests retain their controller state, participants, steps,
pending work and generated definitions.

Party/profile credit must use the authored sharing policy. Consider a companion
who is resting, a late recruit and a member in another area. Unlocking a profile
achievement does not automatically mean every character accepted a personal
quest. Test these distinctions with two characters.

Generated quests and boards provide repeatable jobs; use bounded generation
attempts, valid catalogs/templates and cleanup. Generation can fail gracefully;
design the board to offer a useful refusal instead of a mandatory missing job.
Story templates are an easier starting point for arcs than assembling every
selection and script by hand.

## JavaScript quest handlers

`SET JSFILE world:js/quests/name.js` or a `<JSQUEST>` block supplies handlers:
onStart(state), onStep(n,state), onTimeUp(state), onWin(player), onAccept(player)
and onStop(). The `quests` helper and `cm.store('quest:name')` provide common
operations and durable JSON. JScriptable objects can also receive quest
accept/step/win/fail/end handlers. Register one authoritative reward path;
quest events and handler callbacks can both observe the same completion.

## Quest verification checklist

Run QUESTCHECK, inspect QUESTINFO, and use its test controls for start, step,
time and reset. Play the whole quest normally at least once. Verify:

- First offer, refusal, acceptance, duplicate offer and returning later.
- Wrong order, missing/destroyed item, dead giver and alternate branch.
- Win, failure, expiry and abandonment, including cleanup and repeat cooldown.
- Two-character sharing and exactly-once rewards.
- Save in each important step, quit, restore in a fresh process and finish.

Use the [Q fixtures](feature-index.md#q) as examples of objective, reward,
deadline and sharing APIs. Static lint cannot prove a complete story or all
possible player choices.
