# Factions and organisations

[Creator handbook](creator/README.md) · [Cookbook](creator/cookbook.md)

Organisations use the existing clan system. Membership belongs to each character, so two companions can join different guilds or hold different ranks. Standing is a faction; belonging to an organisation and being liked by it are separate things.

## Make a guild

In builder mode:

```
CREATE GUILD Knights of the Rose TEMPLATE=ORDER HALL=Castle#14
```

Templates are `GUILD_NPC`, `ORDER`, `TEMPLE` and `CULT`. This creates the clan, its standing faction, a guildmaster, a trainer, an empty quartermaster and an adjoining members' room. The hall needs a free exit. The scaffold is editable content: set quests, lessons, inventory, prices, kits and rank requirements before shipping the world. `SAVE` writes it with the world, including `world/clans.cmare`, faction files and government definitions.

The templates have five ranks, no voting, minimum membership zero, `MERIT` promotion and the `NPCRUN` default. Senior ranks initially require placeholder tattoos such as `ORDER_RANK_1`. Replace each position's `INNERMASK` with its real requirements. `MAX` limits seats, including NPC members. Lower position `RANK` numbers mean greater seniority.

An NPC's hand-authored `<CLAN ROLE="KNIGHT">Knights of the Rose</CLAN>` accepts a position ID or numeric role. `ClanHelper CLAN="Knights of the Rose" ROLE=KNIGHT` also sets a named role; the older positional ClanHelper syntax still works.

## Membership and merit

Put `GuildMaster` on an NPC, for example:

```
CLAN="Thieves Guild" JOINMASK="-BASECLASS +Thief" JOINFEE=50
JOINQUEST="Proof of Nerve" RANKS=MERIT DUES=TRUE
KIT.INITIATE="a guild lockpick,a guild cloak" KITFEE=25
```

Parameters are written on one behaviour parameter line. Kit entries are comma-separated catalog names. Join NPC organisations by speaking to their guildmaster. The speaker can say `join`, `rank`, `advance`, `leave`, `dues`, `kit`, or `kit replace`. A kit is issued once per character and rank; replacement uses `KITFEE`. `LEAVEMSG` and `REFUSEMSG` customise replies. `RANKS=SCRIPT` leaves advancement requests to scripts; use an AUTOPROMOTEBY=NONE government to disable automatic merit checks too. Joining and leaving use the clan's normal entry/exit scripts and clan events exactly once.

Government `AUTOPROMOTEBY=MERIT` moves a member to the highest qualifying available position. `MERIT_DEMOTE` also lowers their rank if its requirements cease to pass. Faction changes, tattoos, level-ups, quest wins and each game day's maintenance recheck loaded members. `CLANDETAILS` and the guildmaster explain the next rank's mask. Set `STANDINGFACTION` on the clan or government to show its standing alongside the rank.

## Dues and world rules

These settings can be placed in `world.ini` or changed with `WORLD SET`:

| Setting | Values |
| --- | --- |
| `CLANCLOCK` | `GAME` (default) uses advancing game time for maintenance, votes and declaration cooldowns; `REAL` keeps real-time legacy maintenance |
| `CLANNEWS` | `INFO` (default), `MEMBERS` (each member's session), or `OFF`; rank changes still tell the affected member |
| `CLANVOTES` | `CHARACTER` (default), or `PROFILE` to count the profile once |
| `AUTOREACTION` | Existing reaction types plus `CLAN` for NPC organisations |

Clan and government settings include `DUESPERIOD=DAY|WEEK|MONTH|YEAR`, `DUESGRACE=<periods>`, `DUESLATE=WARN,DEMOTE,EXILE` and `DUESAUTO=TRUE|FALSE`. Clan values override government defaults. `DUES` is the charge each period. Periods follow the world's calendar; the saved `DUESNEXT` deadline prevents billing the same period twice after restore. Late steps advance with unpaid periods after the grace period; the final step repeats for further missed periods.

`CLANDUES PAY [clan]` or saying `dues` to a guildmaster pays arrears from carried currency. Automatic payment uses carried currency followed by the member's bank accounts. A compatible clan bank receives payment; without one, `TREASURY` records it on the clan. NPC members are counted but do not owe dues. With `CLANCLOCK=REAL`, the legacy annual collection retains its bank-account rules.

`CLANCREATE <name> PARTY` founds a Fellowship with the current party, validates eligibility first, and charges the founding cost once. `PARTY CLANS` lists each companion's memberships and ranks. `CLANVOTES=PROFILE` permits an authorised action immediately when every eligible voter belongs to that profile; it does not confer powers on unauthorised ranks or bypass other profiles' votes.

## Script API and masks

```
if clanrank($n 'Thieves Guild' >= CUTPURSE)
  mpechoat $n Welcome, guildmate.
endif
mpclanrelation 'Orc Horde' 'Town Watch' WAR MUTUAL
mpconquer Millbrook 'Orc Horde' RULE
mpconquer Millbrook 'Orc Horde' -200
mpconquer Millbrook 'Orc Horde' FREE
```

Named-clan `CLANRANK` comparisons describe seniority, taking a position ID or role number. The legacy unnamed comparison retains numeric role semantics. `MPCONQUER` operates on an area's Conquerable behaviour and uses its normal events and transitions. It accepts `+n`, `-n`, `=n`, `RULE`, or `FREE`.

| Function | Result |
| --- | --- |
| `CLANPOS(who clan)` | Position ID |
| `CLANMEMBER(who clan field)` | `JOINDATE`, `DUESOWED`, `DONATEDGOLD`, `DONATEDXP`, or `RANKNAME` |
| `CLANS(who)` | Membership list |
| `CLANRELATION(clan other)` | Combined diplomacy state |
| `AREACLAN(area)` | Ruling clan ID |
| `AREACONTROL(area clan)` | Control points |
| `REVOLTCHANCE(area)` | Current revolt chance |

Use values as `$%CLANPOS($n 'Thieves Guild')%`, and comparisons as `clanrelation('Orc Horde' 'Town Watch' == WAR)` or `areaclan(Millbrook == 'Orc Horde')`. Diplomacy supports `WAR`, `HOSTILE`, `NEUTRAL`, `FRIENDLY` and `ALLY`; `MUTUAL` sets both sides. MODIFY CLAN also edits outgoing relations.

`-CLANRANK "+Thieves Guild>=CUTPURSE"` admits Cutpurses or higher. `-RULER "+Orc Horde"` matches an object in territory ruled by that clan, while `-RULER +*` matches any ruled territory. Positive forms exclude matches using negative arguments. `ClanEnemyAggressive` reads current combined diplomacy everywhere, with `CLAN=`, `STATES=WAR,HOSTILE`, `CHANCE=`, `MESSAGE=` and optional `CHECKLEVEL`.

## Standing, effects, towns and shops

With `AUTOREACTION=CLAN`, `NPCRUN` clans get `CLAN_<CODE>` standing from `resources/examples/clanreaction.ini`. `clanreaction_<code>.ini` overrides one organisation. Member kills lower standing; bribes and gifts raise it. Allied organisations receive half the change, enemy organisations at war its inverse half. Changes in diplomacy update these links.

An area's `REPUTATION=examples/areareaction.ini` opts it into town standing; use another template for custom bands and reactions. Blank or `NONE` means no automatic area reputation, even if `AUTOREACTION` includes AREA. MODIFY AREA exposes this field. A law's `LENIENCY=RIVERTON_HERO=-1;RIVERTON_VILLAIN=1` shifts the base punishment by the applicable town standing band, preserving sentence flags.

Faction file settings:

```
SHOWMASK=-TATTOO +READ_CULT_LETTER
HIDDENNAME=an unknown standing
PLAYEREFFECT1=Prop_Adjuster;ATTACK+5;CULT_TRUSTED,CULT_HONOURED
PLAYEREFFECT2=MyScriptedGenAbility;MODE=FAVOURED;-TATTOO +CULT_BLESSING
```

Player effects are live faction handlers, recomputed from the current band or mask, and do not become saved copies in the character's effect list. A scripted generic ability can use its normal tick/message hooks here. Autoawards are also marked for recheck when character requirements change.

`FACTIONS <name>` shows the ladder, current band and points to the next. `FACTIONS PARTY` shows companion standings, hiding undiscovered cells. SCORE also observes `SHOWMASK`; explicit lookup can return `HIDDENNAME` without exposing the real faction or its value.

Shop PREJUDICE accepts `BUY=0.8 CLAN:Knights_of_the_Rose`, `BUY=0.8 CLANPOS:Knights_of_the_Rose:KNIGHT`, `BUY=0.8 MASK(-CLANRANK "+Knights of the Rose>=KNIGHT")`, and faction band codes such as `BUY=0.8 RIVERTON_HERO`.

Put `Prop_StockZapper` on an inventory prototype:

```
MASK="-CLANRANK '+Knights of the Rose>=KNIGHT'" HIDE=TRUE MSG="Only Knights may buy this armour."
```

Failing customers cannot buy it. `HIDE=TRUE` hides it from LIST; `HIDE=FALSE` lists it as restricted. Access is checked for each customer.
