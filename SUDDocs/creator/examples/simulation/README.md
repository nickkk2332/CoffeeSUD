# Example: a combat simulation

[Chapter 41](../../41-inspection-and-limits.md#simulating-builds)

`ember-vs-strike.json` compares two builds against a training warden with 300 hit
points, evasion 100, block 10, armor 20 and 25% fire resistance:

- `ember`: the example ember bolt with the Concentrated power support (a spell,
  12 fire damage, half again with the support, a two-tick cooldown);
- `strike`: a heavy strike defined in the scenario itself (a weapon skill, 20
  physical damage, no cooldown) with 150 accuracy.

Each build runs six seeded fights in its own engine process:

```bash
python3 tools/simulate.py SUDDocs/creator/examples/simulation/ember-vs-strike.json
```

The report gives each build's observed results over the seeds (kills, turns to
kill, damage per turn, how each fight stopped) apart from the combat profile's
exact expected damage per cast, and the turns those exact figures predict.
`--seeds 1,2,3` runs other seeds; `--json results.json` keeps the numbers.

## A boss that fights back

`frost-warden.json` puts phase 2's combat features in one fight. The warden
(300 hit points, level 3, evasion 100, block 10, armor 20) **acts**, has the
elite trait **Brutish** (`sim.brute`: 100 more hit points and 3 more melee damage,
given as the encounter director gives a trait), and casts **Frost claw** at its
foe whenever the claw is ready (10 cold damage, a two-tick cooldown; each landed
hit builds 50 of the `chill` ailment, and it does double damage to a chilled
foe). When the claw is cooling down it takes a combat round of the engine's own.

The scenario's own combat profile, `sim.frost` (chosen with `"profile"`),
declares the ailment (`Chilled` at 100, for three ticks, `DEX-5`) and the guard
pool **Ward** (`sim.ward`: 30, refilling 3 a tick, held back two ticks after a
hit). Both builds have 150 hit points and the same two skills: **Heavy strike**
(20 physical damage) adds a step to the **Combo** pool (`sim.combo`: up to 5, lost
on a miss or when the fight ends, one less every three turns), and **Finishing
blow** (45 physical damage) is cast whenever three steps can pay for it.

- `warded` holds the Ward, so the warden's claw hits it before hit points;
- `bare` has no Ward.

**Gil the shield-bearer** fights beside each caster with the `guard` role from
the scenario's `formations` (`TARGET=ATTACKER`, `INTERCEPT=50`): once the caster
is at half its hit points or below, Gil draws the warden's attacks on to
himself.

```bash
python3 tools/simulate.py SUDDocs/creator/examples/simulation/frost-warden.json
```

What it reported (`warded` killed the warden in all six fights, `bare` in three;
the other three times its caster fell):

```text
Build warded (its own engine process):
  Observed over 6 seeded runs (sampled):
    killed 6; stalled 0; stopped at the turn limit 0; at the work limit 0; the caster fell 0
    turns to kill: min 24, median 32.0, mean 31, max 36
    damage per turn: 12.9 (the allies dealt 151 in all)
    the caster (150 hit points): 75.83 left on average (a fallen caster counts 0); 2.39 damage taken per turn
    the boss: 91 composed casts and 89 combat rounds in all
    allies fallen: 4 in all
    ailments begun: on the allies chill 6; on the caster chill 6
    guard pool absorbed: 196 of the boss's hits on the caster, 0 of the caster's hits on the boss
    module counters, all runs (as WORLD MODULES REPORT counts them): encounters | sim.brute | traits given 6, ...
Build bare (its own engine process):
  Observed over 6 seeded runs (sampled):
    killed 3; stalled 0; stopped at the turn limit 0; at the work limit 0; the caster fell 3
    ...
    the caster (150 hit points): 29.33 left on average (a fallen caster counts 0); 3.73 damage taken per turn
```

The module counters also show the combo at work (`resources | sim.combo | gained`,
`spent`, `reset on miss`) and Gil's interceptions (`formations | guard |
interceptions`).
