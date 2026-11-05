# NPCs, dialogue and encounters

[Handbook](README.md) · Feature families [N](feature-index.md#n), [C](feature-index.md#c)

## Build a creature from responsibilities

A GenMob can represent a person, animal, undead creature, construct, spirit or
invented species. Choose a race for body, senses, breathing, natural weapons,
size and corpse behavior; a class/level for progression; then add abilities,
equipment, properties, behaviors and scripts. Display text alone does not grant
flight, underwater breathing or immunity. Verify those mechanics on a mortal
character with the correct senses and equipment.

Give each behavior a clear job. Mobile moves, Schedule supplies a routine,
combat behaviors choose abilities, and a script reacts to story events. Two
independent movement controllers or two reward handlers can conflict. Every
behavior supports a WHEN condition; `Prop_When` can gate properties and
behaviors together. `GenBehavior` creates a reusable parameterized scripted
behavior through the generic editor. PARAM/PARM semantics differ from ability
EFFECTPARM and MOBPROG LOAD arguments; use the corresponding help.

## Daily life and awareness

| System | Use and checks |
| --- | --- |
| Schedule | Time/season/weather/mask routines; walk to work, shop, sit, sleep and lock up |
| Sleeper, Sentry | Sleep/wake and watch responses; test combat and time jumps |
| Memory | First/repeat meetings and remembered interactions |
| Patroller | Routes, pauses and patrol control |
| Aggression warnings | Give a warning/grace period instead of instant attack |
| Escortee | A quest escort with destination/failure behavior |
| Hireling | Paid follower contracts and configurable services |
| Prop_Affinity | Relationship changes and NPC reactions |
| Prop_NPCLines, Sounder | Greeting/service/event barks and ambient pools |

For example, Schedule can use `DAY: Market#5 SHOP; NIGHT: Inn#2 SLEEP`.
Use real room IDs, add an arrival SAY/EMOTE and a SHOPMSG for closed hours.
When neither relevant area is observed, catch-up can place the NPC directly at
the correct routine location. It need not simulate every unseen footstep.
Use `STAT BEHAVIORS npc` to inspect current target, timer and flags, and
`TRACEB` to follow supported transitions.

## Conversation systems

MudChat is appropriate for pattern-driven replies, weighted alternatives,
conditions, delays and topic pools. Use CHATTEST for a dry conversation,
CHATLINT for malformed or provably shadowed entries, and CHATRELOAD after
file edits. Literal/OR shadowing can be proved; arbitrary guarded/masked
patterns are checked conservatively. Tick-entry odds are conditional on guards
holding, not a promise that a line will appear every N turns.

TALK and TALK_PROG provide explicit interaction instead of requiring the player
to guess a SAY phrase. `MPMENU` adds numbered, conditional choices. Topic
discovery can highlight `[[topic]]` references and support later questions.
`CHECK` can drive dialogue skill checks with authored success/failure paths;
avoid treating an unsuccessful check as a dead end unless that is intended.
The [MUDChat manual](../../CoffeeMud/guides/MUDChat.html) supplies inherited
file grammar; the [cookbook](cookbook.md) supplies a complete numbered menu.

Language comprehension applies before normal dialogue and services. A
monolingual guard does not process incomprehensible speech as a keyword hit.
Use NOTUNDERSTOOD_PROG or a translated/visual-language route for a deliberate
fallback. See [languages](../languages.md).

`Scene` coordinates ambient multi-actor scenes, one line per tick, with actor
selection, SAY/EMOTE/commands, PAUSE, ECHO and MOBPROG DO. It supports named
once/repeat state, conditions and interruption. Saved run state is available.
It is suitable for guards chatting or an arrival scene; it is not the proposed
execution choreography system. Actor disappearance, leaving and interruption
need deliberate narrative handling. See [N28's worked fixture](../../tools/fixtures/fixes/n28-scene.js).

## Combat AI and boss design

| Tool | Responsibility |
| --- | --- |
| CombatAbilities and presets | Use known abilities, including support targeting and rotations |
| Healer / NiceAbilities | Helpful casts with HP thresholds and target policies |
| TargetPriority | Prefer a role, lowest HP, last attacker, or sticky target |
| Threat functions | Read/change threat and select enemies from scripts |
| Morale | Flee, rally, surrender or berserk under authored conditions |
| Pursue | Chase with a leash, then return home |
| BossPhases | Once-per-fight thresholds/rounds/adds and actions |
| EncounterReset | Leash, cleanup and reset after disengagement |
| Telegraphs / WINDUP | Visible, cancellable delayed attacks |
| Prop_MobRank | Shared minion/normal/elite/boss stat and reward presets |
| Prop_PartyScaler | Scale at engagement to the participating party |

A useful boss has a readable opening, one response the player can learn, and a
predictable reset policy. Give its summoned adds cleanup rules. BossPhases
accepts semicolon-separated triggers and comma-separated actions:

```text
ONENGAGE: SAY "You should have left."; AT=50%: HEAL 10%, SAY "Still standing!"; RESET=FULLHEAL
```

Thresholds fire once per fight and phase is saved. RESET can be FULLHEAL,
LEAVECOMBAT, LEASH or NEVER. `PHASE_PROG` and PHASE let scripts react. Avoid
using an unguarded HITPRCNT trigger to award a one-time phase reward each tick.

Named MPALARM and MPTELEGRAPH can carry windups across save/restore, with
interrupt conditions and avoidance. Test stun, displacement, death, escape and
re-entry during the delay. A GenAbility WINDUP participates in this pattern.

DIFFICULTY presets, NPC armor/attack/damage/speed/rejuvenation/money formulas,
rank presets and party scaling can all multiply one another. Tune one layer at
a time. `CONSIDER ALL`, COMBATDEBUG, and ordinary-character PLAYTEST expose
the resulting fight; an invulnerable Archon gives misleading balance results.
PLAYTEST suspends privileges but does not reconstruct a well-balanced level-1
character from your builder. Use TESTCHAR/KIT for representative builds.

## Encounters and respawns

RandomMonsters/RandomItems support catalog weights, conditions, nearby-party
placement, caps and despawn rules. Encounter tables live in
`world/encounters/*.enc`; entry-triggered encounters can use level/budget and
conditions. Arena/wave rooms coordinate consecutive fights and completion.
Ambushes support detection and surprise. Story-aware respawns prevent a slain
story creature returning after its plot state changed. Loot tables are covered
in [generation](12-generation.md).

Decide whether population is fixed, rejuvenating, generated on entry or
spawned by a quest. Mixing them can duplicate monsters. Observe a room while
the despawn condition changes, leave and return, then save/restore. Caps and
catalog once-ever rules should still hold. Area-wide invasions/raids remain
deferred; existing spawn and wave primitives are available.

## Death, defeat and checkpoints

`Prop_Essential MODE=YIELD` stops combat below a threshold and fires
YIELD_PROG; KNOCKOUT intercepts a killing blow and fires DEFEAT_PROG. Protection
lasts a configured number of ticks, and NOXP changes defeat XP. These are
authored alternatives to mandatory death, not a universal invulnerability flag.

`Prop_Downed` supports a revive window before the remainder of PLAYERDEATH
applies. Define whole-party defeat behavior and per-area/room overrides with
`Prop_DeathRules`. Test the last active character falling, companions in other
rooms, recruited characters and followers.

`Prop_Checkpoint` records a separate respawn room for participating characters;
ON can be ENTER, REST or COMMAND:pray, with healing, area respawn and save
options. `DEATH=CHECKPOINT` uses it, with recall as fallback. Recall and
checkpoint are different concepts. Test the path back after defeat so a
relocked puzzle cannot strand the player.
