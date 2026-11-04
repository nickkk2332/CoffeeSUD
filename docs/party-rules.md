# Party rules for a world

Put these in the world's `world.ini`, or use `WORLD SET` in builder mode:

```ini
# Up to four profile characters together or active elsewhere.
PARTYMAX=4
ACTIVECHARMAX=4
# Up to eight characters in the player's profile, including resting ones.
PROFILEMAX=8
# Companions stay where they are; the player must go and meet them.
PARTYSUMMON=MEET
```

`0` removes a size limit. Existing campaigns keep their characters when a limit is lowered; further additions must fit the limit. Ordinary FOLLOW also observes PARTYMAX for characters of the same profile.

`PARTYSUMMON=ALLOW` permits PARTY ADD to bring companions to the leader. `MEET` requires meeting them where they are or last rested. `PARTY MEET OFF` cannot relax that world rule.

For conditional summoning:

```ini
PARTYSUMMON=MASK
PARTYSUMMONMASK=-AREA "+Sanctuary"
PARTYSUMMONMESSAGE=Companions can only be called from Sanctuary.
```

The mask checks the character requesting the summon, including their location. An empty restricted mask refuses summoning. A player may still choose `PARTY MEET ON` to meet companions instead.

Players can read `HELP PARTY` for the commands and `PARTY RULES` for the world's limits. `PARTY REMOVE` leaves a character active in the world; `PARTY REST` takes them out of it. `SWITCH` changes which live character is controlled, including a character currently using feign death.
