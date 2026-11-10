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
