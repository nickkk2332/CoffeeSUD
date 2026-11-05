# Characters, game start and progression

[Handbook](README.md) · Feature families [P](feature-index.md#p), [S](feature-index.md#s)

## Design the opening contract

Choose whether the game has a fixed hero, offered templates, or free creation;
whether new characters can join later; and how death and saving work. These
choices affect every tutorial, quest reward and encounter. Put world-owned rules
in `world/world.ini`; see the [allowlist](world-settings.md). Set the start room
with `START`, with qualified `START_…` rules where needed. Origin-specific
`START` values and creation scripts can choose a different opening.

`CREATIONTEMPLATES=OFFER` offers authored player templates alongside custom
creation; `ONLY` restricts creation to templates. Templates can carry race,
class/level, abilities, stats, factions, tattoos and equipment, with locked
fields. Test the actual NEW flow: editing a template does not retroactively
rebuild an existing character.

## Creation data

| File/system | Purpose | Important distinction |
| --- | --- | --- |
| `origins.ini` | Background choices, unlocks, start, items, skills, factions and script |
| `kits/*.kit` | Starting equipment and selectable kit groups | Also usable by TESTCHAR/KIT and PLAYTEST |
| `creation.ini` | Step order, optional/random steps and custom questions | Choice gifts run as part of creation |
| `perks.ini` | Traits, perks and flaws with costs, prerequisites and effects | Define your budget and mutually exclusive choices |
| `CHARCREATIONSCRIPTS` | Hooks around engine creation stages | MOBPROG hooks, not a replacement file format |
| `scripts/progression.js` | Creation and progression hooks | Return values can affect XP, training and stat rolls |

Origins are sectioned records, not `world/data/origins.ini`. A small example:

```ini
[ORPHAN]
NAME=Temple Orphan
DESCRIPTION=Raised beside the temple library.
START=New Area#0
ITEMS=GenItem
ABILITIES=Prayer_Bless 25
TATTOOS=ORPHAN
MONEY=10
```

The selected record gives `ORIGIN_ORPHAN`, so masks and scripts can identify it.
Add `MASK` to restrict eligibility and `UNLOCK` for a character/profile tattoo.
Use catalog names to give meaningful items instead of the blank `GenItem` above.

A kit uses directives, **not** INI assignments:

```text
# world/kits/scout.kit
starting all
choose equipment
title A scout's travelling kit
item GenWeapon
item GenArmor
gold 20
ability Skill_Recall 50
```

Kits in the same `choose` group are alternatives. Matching automatic kits and
chosen kits are both applied. `starting` controls inclusion during creation;
a testing-only kit can omit it. `wear no` leaves equipment unworn.

`creation.ini` has `STEPS=…`, modes such as `GENDER=RANDOM`, and custom sections
with `PROMPT`, `SHOWIF` and repeated `CHOICE` records. Choices can have NAME,
HELP, MASK, EFFECTS, FACTION, START and SCRIPT. Use [P12's fixture](../../tools/fixtures/fixes/p12-creation.js)
as an exact grammar example. Review and BACK/QUICKNEW flows are supported;
verify gifts do not accidentally become repeatable benefits in your custom hooks.

## Races and classes

`CREATE RACE`, `MODIFY RACE`, `CREATE CLASS` and `MODIFY CLASS` define generic
races/classes without a Java plugin. Races set body parts, size, ageing,
racial/cultural abilities, natural weapons and corpse/health text. Classes set
qualifications, base-class relationships, stat limits, level gains, skill
mappings and outfits. Existing abilities may be granted at selected levels and
proficiencies. `LIST` and the editors expose supported IDs and fields.

Use `ADDCHARSTAT_…`, `REPLACECHARSTAT_…` and `STATEFFECT_…` rules for world stats
and effects where appropriate. A new displayed stat is useful only when
formulas, scripts or abilities consume it. Data classes still obey engine
interfaces: custom body mechanics or a wholly different combat loop can require
JavaScript/Java. Native and JS class extension is covered in [extensions](05-javascript.md).

## Abilities, properties, diseases, poisons and trades

`CREATE ABILITY ID` creates a generic ability. Configure its target/affect types,
classification/domain, trigger words, masks, costs, components, messages,
damage/healing formulas, duration, stat adjustments, secondary effects and script.
`HERESTATS` and `MOCKABILITY` also work when the definition is attached as a
passive property. Its instance parameters are available as `$PARMS` and
`EFFECTPARM(KEY)`, so one custom property can serve many objects.

Named script functions provide lifecycle hooks:

| Hook | When |
| --- | --- |
| `CANCAST` | Before costs; `RETURN false` refuses |
| `ONCAST` | Successful cast, with `$t` as target |
| `ONTICK` | Each tick while attached |
| `ONEXPIRE` | When the effect ends |
| `ONLEARN` | When the character gains the ability |
| `ONTRIGGER`, `ONDISARM` | Generic trap activation/disarming |

`CASTFAILMSG` supplies a useful refusal. `TARGETMODE` accepts SINGLE,
ALLENEMIES, ALLALLIES, ALLINROOM or GROUPANYWHERE. `MAXTARGETS` limits the list;
`AOEMSG` prints once. Casting cost and CANCAST occur once; each target then has
its own target mask, message, saving throw and effect. Design friendly-fire
rules explicitly. `ACTIONCOST`, `COMBATACTIONCOST` and `COMPONENTS` can override
defaults. `SUMMON`, count/level formulas and `SUMMONFLAGS` author temporary allies.

Generic disease, poison, gathering, crafting and wright definitions expose
their own lifecycle and data fields; TWEAK wraps an existing ability with
adjusted masks, effects or scripts. Prefer these to copying native source for
a small rule variation. Consult the [current GENABILITY help](tool-reference.md#genability)
and the editor for exact field names rather than copying a design proposal.

## Advancement and discovery

World settings control XP, new-character level/XP, per-level practices/trains,
additional training levels, maximum level and ageing. Rank ladders add named
progression alongside levels. `MPLEVELUP`/`MPLEVELDOWN` apply actual level gains
or losses; directly setting LEVEL is not equivalent. `MPEXP target NEXTLEVEL`
and `EXPTONEXT` help milestone rewards. `PROGRESSION class [race] [level]`
shows the resulting curve; `PROGRESSION XP` estimates authored XP sources and
cannot prove the rewards of arbitrary dynamic scripts.

Teachers and training services can require quests, reputation, fees or known
skills. Respec/refund tools, skill trees, achievement rewards and known recipes
are available. Achievement rewards include SCRIPT, EFFECT, FACTION, VAR and
RECIPE as well as native rewards. Profile rewards can apply to future characters;
make once-only and repeatable grants deliberate.

`Prop_Discovery` awards first-discovery XP, tattoos and journal lines for
ENTER/LOOK/READ/OPEN/GET/FIND/TALK, with CHARACTER, PARTY or PROFILE scope.
`DISCOVERYXP` supplies a world exploration formula. Test rewards with two party
members and two separate campaigns to catch scope mistakes.

## Profile, party and recruitment

One profile owns multiple playable characters. `PROFILE` lists them; `SWITCH`
changes the controlled character. `PARTY ADD` brings in a profile character,
`PARTY REMOVE` removes them from the group but leaves them active, and `PARTY
REST` sends them back to the profile. `AS name command` orders one character.
Windows are views of characters in the same running world; they do not create
independent campaigns. See `HELP PARTY` for current forms and turn rules.

Set `PARTYMAX`, `ACTIVECHARMAX` and `PROFILEMAX` deliberately; zero means
unlimited. `NEWCHARS` controls later creation. `PARTYSUMMON=ALLOW|MEET|MASK`
controls bringing in remote characters, with `PARTYSUMMONMASK` and a refusal
message. World restrictions cannot be relaxed by a player's PARTYMEET option.
Following characters of the same profile count toward the party cap. Lowering a
limit does not delete existing characters. See [party rules](../party-rules.md).

`MPRECRUIT npc player [PARTY|REST] [NAME=…]` turns a met NPC into a profile
character; `Prop_Recruitable` offers an authored command route. Name, profile
and party limits still apply. Ordinary followers, hired help and summoned mobs
remain distinct from recruited player characters. Feigned death does not remove
a profile character from the switching interface.

Training that consumes game time and the generic companion controller remain
deferred. Existing teachers, followers, affinity, hirelings and recruitment are
available now.

## Make advancement discoverable

SKILLTREE displays prerequisite chains and learned, learnable, locked or hidden
skills. Supply `world/resources/skills/skillhints.ini` when a hidden skill needs
a discovery hint; class-qualified entries can override general hints. A hint
should point to a real teacher, quest or place, not merely expose a forbidden
ability ID. TEACHLIST and trainer restrictions should agree with those chains.

Rank ladders in `world/ranks.ini` provide named progression with script/reward
support alongside levels. Added stats and STATEFFECT rules can influence gains
and combat, but a displayed stat alone does not create a mechanic. Ageing rules
and starting ages should match the race's age categories. Use PROGRESSION and
fresh ordinary characters to check the complete resulting progression.
