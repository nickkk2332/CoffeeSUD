# Society: clans, factions, organisations, law and economy

[Handbook](README.md) · [Organization option reference](../organizations.md)
· [F feature index](feature-index.md#f)

This chapter covers the systems that make a world feel inhabited by groups
rather than individuals: who belongs to what (clans, governments and NPC
guilds), how much each group likes the player (factions and reputation), what
the law forbids and what happens to criminals (the Arrest behavior), who rules
which territory (conquest), and how money moves (currencies, shops, bankers,
exchange). It is written for world creators who build these in the game and in
world files, for scripters who drive them from MOBPROG and JavaScript, and, in
a few places, for engine developers who need to know which Java class owns a
rule.

Almost all of this machinery is inherited from CoffeeMud 5.11 and works as it
did there. CoffeeSUD adds a layer on top for a single player with a party of
characters: NPC-run organisations (`NPCRUN` clans, the `GuildMaster` behavior
and `CREATE GUILD`), clan timers on the game clock, merit-based ranks,
`PLAYEREFFECT` faction handlers, town reputation, the `LAW` and `PRICECHECK`
tools, currency files and exchange rates, and a set of script commands for
warrants, shops, banks and diplomacy. Each section says which is which.

## In this chapter

- [How the pieces fit](#how-the-pieces-fit)
- [What CoffeeSUD removed or changed](#what-coffeesud-removed-or-changed)
- [Clans and governments](#clans-and-governments)
  - [Concepts](#clan-concepts)
  - [Stock governments](#stock-governments)
  - [The governments file](#the-governments-file)
  - [Powers and voting](#powers-and-voting)
  - [Clans as world content](#clans-as-world-content)
  - [Clan commands](#clan-commands)
  - [Dues, ranks, merit and news](#dues-ranks-merit-and-news)
  - [Clan settings](#clan-settings)
  - [Party-founded clans](#party-founded-clans)
- [NPC organisations and guilds](#npc-organisations-and-guilds)
  - [Scaffold a guild](#scaffold-a-guild)
  - [GuildMaster](#guildmaster)
  - [Recruitment and advancement](#recruitment-and-advancement)
  - [Diplomacy and ClanEnemyAggressive](#diplomacy-and-conquest)
- [Factions](#factions)
  - [Faction files](#faction-files)
  - [Change triggers](#change-triggers)
  - [Abilities, effects and reactions](#abilities-effects-and-reactions)
  - [Who holds a faction](#who-holds-a-faction)
  - [Automatic reaction factions](#automatic-reaction-factions)
  - [Scripting factions](#scripting-factions)
  - [Faction tools](#faction-tools)
- [Law and crime](#law-and-crime)
  - [Setting up a legal system](#setting-up-a-legal-system)
  - [The law file](#the-law-file)
  - [Arrest, trial and punishment](#arrest-trial-and-punishment)
  - [Law tools](#law-tools)
  - [Law and scripts](#law-and-scripts)
- [Conquest and area control](#conquest-and-area-control)
- [Economy](#economy)
  - [Currencies](#currencies)
  - [Money handling](#money-handling)
  - [Shops and shopkeeper pricing](#shops-banks-and-currencies)
  - [Banks and debts](#banks-and-debts)
  - [Money changers and exchange rates](#money-changers-and-exchange-rates)
  - [Economy scripting](#economy-scripting)
- [Worked examples](#worked-examples)
- [Persistence notes](#persistence-notes)
- [Troubleshooting and gotchas](#troubleshooting-and-gotchas)
- [See also](#see-also)

## How the pieces fit

Four things are easy to confuse. Keep them separate when you design.

| Concept | What it is | Where it lives | How a player changes it |
| --- | --- | --- | --- |
| Membership (clan) | An affiliation with a named group, with a position (rank), powers, dues and diplomacy | Clan definition plus a position per member; clans with no player members in `world/clans.cmare`, others in the save | Joining, promotion, `CLANASSIGN`, merit, scripts |
| Standing (faction) | A number with named bands, reactions and effects | A faction file (`.ini`); each character's value is saved with the character | Deeds that match change triggers, `MPFACTION`, scripts |
| Rule (law, conquest) | Which acts are crimes in an area, and which clan rules it | An `Arrest` or `Conquerable` behavior on an area plus a law file; control points in the save | Committing crimes, fighting, flags, `MPCONQUER` |
| Money | Coins in a currency, bank accounts, shop prices | Currency definitions, shopkeeper and area settings, coin items | Buying, selling, banking, tax, fines |

A player can be liked by a temple without belonging to it, hold a guild rank
while a town's watch wants them, and rule a town while despised by it. Model
each axis separately and connect them deliberately:

- Gate things on membership with `-CLAN` and `-CLANRANK` masks.
- Gate things on standing with `-FACTION` masks, faction range codes in shop
  PREJUDICE, and `FACTION_PROG` triggers.
- Gate things on rule with `-RULER` masks and the area script functions.
- Gate things on law with `-WANTED`, `-OFFICER`, `-JUDGE` masks and the warrant
  script functions.

Masks are the glue. The mask language is described in
[24-behaviors-and-masks.md](24-behaviors-and-masks.md); the keys used in this
chapter are `-CLAN`, `-CLANRANK`, `-RULER`, `-FACTION`, `-TATTOO`, `-WANTED`,
`-OFFICER` and `-JUDGE`. In a mask, a minus key starts a list of required
values written with plus signs (`-CLAN "+Thieves Guild"` means "is in the
Thieves Guild"), and a plus key starts a list of excluded values written with
minus signs. The flag-style keys `+WANTED`/`-WANTED` and `+OFFICER`/`-OFFICER`
work the other way round: `+WANTED` requires the subject to be wanted and
`-WANTED` requires them not to be.

## What CoffeeSUD removed or changed

| Inherited feature | In CoffeeSUD |
| --- | --- |
| Players founding clans for each other, elections among players | Still present in code. A single player has one profile with a party of characters; `CLANVOTES=PROFILE` makes the profile a single voter. |
| Clan web sites, clan forums (`CLANWEBSITES`, `CLANFORUMDATA`) | The web server and forum system were removed; the ini keys are read but nothing serves them. |
| `CHANNEL` power and clan channels | No player-to-player channels. Only the `INFO` and `QUESTCHAT` channels exist; clan news goes to `INFO` or to member windows (`CLANNEWS`). |
| Inactivity purge of clans by real login time | Clan maintenance counts game time when `CLANCLOCK=GAME` (the default). Clans with minimum members `0` are never purged. |
| Real-time dues collected once a year | Replaced by per-clan dues periods on the game calendar (`DUESPERIOD`). The old yearly sweep remains only under `CLANCLOCK=REAL`. |
| Clan PK, `CLANPVPKILLS` | The commands exist, but a player's characters are one person; player-versus-player rules follow `PARTYFIGHT`. |
| Autonomous NPC clan warfare (`ClanWarfare`), deity holy days, `Prop_ClanDisguise` | Not implemented (deferred in the project plan). Do not rely on them. |
| Wanted posters, bounty hunters, supply and demand market | Not implemented (deferred). Use scripts and explicit restock schedules. |

Clan commands, governments, conquest items (flags, law books, banners,
pamphlets and so on), clan banks and the clan spells still exist and are
reachable. They were designed for groups of players, so a single-player world
mostly uses clans as the membership layer of NPC organisations.

## Clans and governments

Source: [Clan.java](../../CoffeeMud/com/planet_ink/coffee_mud/Common/interfaces/Clan.java),
[DefaultClan.java](../../CoffeeMud/com/planet_ink/coffee_mud/Common/DefaultClan.java),
[DefaultClanGovernment.java](../../CoffeeMud/com/planet_ink/coffee_mud/Common/DefaultClanGovernment.java),
[Organizations.java](../../CoffeeMud/com/planet_ink/coffee_mud/core/Organizations.java),
stock data in [clangovernments.xml](../../CoffeeMud/resources/clangovernments.xml).

### Clan concepts

A **clan** is a named group. Its **government** (also called a clan type) is a
data record that defines the clan's ladder of **positions**, what each
position may do (its **powers**), which decisions need a vote, how members are
promoted automatically, who may join, and which abilities and effects the clan
grants as it gains levels. A clan holds the live state: members, premise,
home, donation and morgue rooms, experience and level, tax rate, dues,
relations with other clans, treasury and flags.

Vocabulary used throughout this chapter:

| Term | Meaning |
| --- | --- |
| Position | One rank on the government's ladder, for example `CUTPURSE`. Has an `ID` (text), a numeric `ROLEID`, a `RANK`, a seat limit `MAX`, an `INNERMASK`, and powers. |
| Role | The number stored on a member (the position's `ROLEID`). A mob's clan membership is a (clan, role) pair. |
| Rank | Seniority. **Lower `RANK` is more senior** (the boss has `RANK=0`). `-CLANRANK ">=X"` compares by rank: "X or more senior". |
| Seats | `MAX` limits how many members may hold a position. It is a whole number or a percentage of membership (`MAX=2%`). NPC members count. |
| Applicant | A member at the government's `AUTOPOSITION`, waiting for acceptance. In NPC governments `AUTOPOSITION` equals `ACCEPTPOSITION`, so there is no applicant stage. |
| Power | One of 29 named clan functions (`ACCEPT`, `ASSIGN`, `DECLARE`...), granted per position. |
| `NPCRUN` | A clan flag (or government `NPCFLAGS`) marking an organisation run by NPCs. It is kept in `world/clans.cmare`, gets a generated standing faction under `AUTOREACTION=CLAN`, and is never purged for lack of members. |

Role numbers are array indices. The loader requires the `ROLEID` values in one
government to be exactly `0` to `n-1` with no gaps and no repeats; a bad file
makes that government fail to load and logs the reason. Role order need not
follow rank order.

### Stock governments

`resources/clangovernments.xml` ships fifteen governments. The first eleven
are the inherited player governments; the last four are CoffeeSUD's NPC
organisation templates (category `NPCGUILD`).

| ID | Name | Positions (top first; role/rank/seats) | Promotion | Votes (max days, quorum %) |
| --- | --- | --- | --- | --- |
| 0 | `GANG` | BOSS 6/0/1, LIEUTENANT 5/1, TREASURER 4/2/1, ENCHANTER 3/3/3, STAFF 2/4, MEMBER 1/5, APPLICANT 0/6 | `RANK` | none; the boss holds nearly every power |
| 1 | `GUILD` | GUILDMASTER 6/0/5, SECRETARY 5/1, TREASURER 4/2/1, ENCHANTER 3/3/3, CHIEF 2/4, MEMBER 1/5, APPLICANT 0/6 | `RANK` | 24, 100 |
| 2 | `UNION` | SENATOR 6/0/5, SECRETARY 5/1/1, TREASURER 4/2/1, ENCHANTER 3/3/3, SHERIFF 2/4, CITIZEN 1/5, APPLICANT 0/6 | `RANK` | 48, 66 |
| 3 | `FELLOWSHIP` | FIRST CITIZEN 6/0/1, MANAGER 5/1, TREASURER 4/2, ENCHANTER 3/3, SOLDIER 2/4, CITIZEN 1/5, APPLICANT 0/6 | `RANK` | 100, 33 |
| 4 | `THEOCRACY` | HIGH PRIEST 6/0/1, PRIEST 5/1, TREASURER 4/2/1, ENCHANTER 3/3/3, CRUSADER 2/4, BELIEVER 1/5, APPLICANT 0/6 | `LEVEL_OVERWRITE` | none; requires a deity (`-DEITY`) |
| 5 | `FAMILY` | PATRON 6/0/1, MATRON 5/0/1, MISTRESS 4/1, MISTER 3/1, NEUTER 7/1, COUSIN 2/2, CHILD 1/3, BLACKSHEEP 0/4 | `NONE` | none; family only |
| 6 | `ANARCHY` | FREEMAN 0/0 | `NONE` | none |
| 7 | `PROFESSION` (category `CLASSCLAN`) | OFFICER 2/0, MEMBER 1/1, APPLICANT 0/2 | `NONE` | 10, 66; not public, not rivalrous |
| 8 | `CO-OP` | HOUSE MOTHER 3/0/1, HOUSE FATHER 2/0/1, MEMBER 1/1, APPLICANT 0/2 | `RANK` | none |
| 9 | `MERITOCRACY` | HIGH ENTREPRENEUR 5/0/2%, ENTREPRENEUR 4/1/5%, DILIGENT WORKER 3/2/10%, HARD WORKER 2/3/50%, WORKER 1/4, APPLICANT 0/5 | `XP_OVERWRITE` | none |
| 10 | `PLUTOCRACY` | TYCOON 5/0/1%, MOGUL 4/1/3%, LANDLORD 3/2/5%, TENNANT 2/3/50%, SERF 1/4, APPLICANT 0/5 | `GOLD_OVERWRITE` | none |
| 11 | `GUILD_NPC` | GUILDMASTER 4/0/1, MASTER 3/1, ADEPT 2/2, JOURNEYMAN 1/3, INITIATE 0/4 | `MERIT` | none |
| 12 | `ORDER` | GRANDMASTER 4/0/1, MARSHAL 3/1, CHAMPION 2/2, KNIGHT 1/3, SQUIRE 0/4 | `MERIT` | none |
| 13 | `TEMPLE` | HIEROPHANT 4/0/1, BISHOP 3/1, CURATE 2/2, PRIEST 1/3, ACOLYTE 0/4 | `MERIT` | none |
| 14 | `CULT` | HIGHPRIEST 4/0/1, ORACLE 3/1, DEVOTEE 2/2, INITIATE 1/3, SEEKER 0/4 | `MERIT` | none |

The NPC templates have no voting, conquest disabled, `NPCFLAGS=NPCRUN`,
minimum members `0`, `DUESPERIOD=MONTH`, `DUESGRACE=1`,
`DUESLATE=WARN,DEMOTE,EXILE` and `DUESAUTO=false`. Every rank above the lowest
has a placeholder `INNERMASK` of the form `-TATTOO +ORDER_RANK_1` (or
`GUILD_NPC_RANK_1`, `TEMPLE_RANK_1`, `CULT_RANK_1`, up to `_4` for the top
rank). Those tattoos are not given by anything: replace the masks with the
real requirements of your organisation (see [the example](#example-1-a-thieves-guild-with-ranks-and-dues)).

The inherited governments grant per-level abilities and effects (clan
donation, clan home, clan-property wards, stat adjusters and so on). Of those
the clan spells and the conquest items assume a populated player clan; a
single-player world normally ignores them or strips them from a custom
government.

### The governments file

Governments are data. The file is `resources/clangovernments.xml`. A world may
carry its own full copy at `world/resources/clangovernments.xml`; when the
world has that file it is read in place of the stock one (a world copy does
not merge with the stock file). In builder mode, `CREATE GOVERNMENT`, `MODIFY
GOVERNMENT` and `DESTROY GOVERNMENT` rewrite the whole file, and because
builder mode writes changes to `resources/` into the world's own copy, the
edit lands in `world/resources/clangovernments.xml`. `LIST GOVERNMENTS` shows
the loaded ones. Governments are cached after the first read; restart the game
(or use the in-game editors) after editing the file by hand.

The root is `<CLANTYPES>`, with one `<CLANTYPE>` per government.

```xml
<CLANTYPE TYPEID=15 NAME="THIEVES" CATEGORY="NPCGUILD">
  <SHORTDESC>a thieves' guild whose ranks are earned</SHORTDESC>
  <LONGDESC>Description shown in help and details.</LONGDESC>
  <POSITIONS>
    <POSITION ID="FOOTPAD" ROLEID=0 RANK=3 NAME="FOOTPAD" PLURAL="FOOTPADS" MAX=999999 INNERMASK="" PUBLIC="true">
      <POWER>CLAN_TITLES</POWER>
    </POSITION>
    ...
  </POSITIONS>
  <VOTING MAXDAYS=0 QUORUMPCT=0 />
  <AUTOPOSITION>FOOTPAD</AUTOPOSITION>
  <ACCEPTPOSITION>FOOTPAD</ACCEPTPOSITION>
  <REQUIREDMASK></REQUIREDMASK>
  <ENTRYSCRIPT></ENTRYSCRIPT>
  <EXITSCRIPT></EXITSCRIPT>
  <AUTOPROMOTEBY>MERIT</AUTOPROMOTEBY>
  <PUBLIC>true</PUBLIC>
  <FAMILYONLY>false</FAMILYONLY>
  <RIVALROUS>true</RIVALROUS>
  <OVERRIDEMINMEMBERS>0</OVERRIDEMINMEMBERS>
  <NPCFLAGS>NPCRUN</NPCFLAGS>
  <DUESPERIOD>MONTH</DUESPERIOD>
  <DUESGRACE>1</DUESGRACE>
  <DUESLATE>WARN,DEMOTE,EXILE</DUESLATE>
  <DUESAUTO>false</DUESAUTO>
  <CONQUEST><ENABLED>false</ENABLED><ITEMLOYALTY>false</ITEMLOYALTY><DEITYBASIS>false</DEITYBASIS></CONQUEST>
  <TITLES><TITLE>*, @x2 of @x1</TITLE></TITLES>
  <ABILITIES></ABILITIES>
  <EFFECTS></EFFECTS>
</CLANTYPE>
```

Angle brackets inside attribute or element text (for example in a mask or
script) must be written as `&lt;` and `&gt;`.

**`<CLANTYPE>` attributes**

| Attribute | Meaning |
| --- | --- |
| `TYPEID` | Unique number. The ids of all governments must be `0` to `n-1` with no gaps, so a new government takes the next free number. |
| `NAME` | The government's name (`THIEVES`). Used by `CREATE GUILD ... TEMPLATE=`, help and `CLANDETAILS`. |
| `CATEGORY` | Free text. Empty is the default category. Categories limit membership counts (`MAXCLANCATS`) and decide whether players may found clans of the type (`PUBLICCLANCATS`, `CLASSCLAN` for professions). NPC templates use `NPCGUILD`. |
| `ISDEFAULT` | Optional boolean read by the loader. |

**`<CLANTYPE>` children**

| Element | Meaning and default |
| --- | --- |
| `SHORTDESC`, `LONGDESC` | Text for listings and help. |
| `POSITIONS` | The ladder; see below. Required. |
| `VOTING` | Attributes `MAXDAYS` (days a vote stays open; `0` or less falls back to 54 in the maintenance code) and `QUORUMPCT` (percent of voters that must vote), and child `<POWER>` entries naming the functions that need a vote. Required (may be empty). |
| `AUTOPOSITION` | Position ID new members and applicants get. Required. |
| `ACCEPTPOSITION` | Position ID `CLANACCEPT` and `GuildMaster` give. Required. |
| `REQUIREDMASK` | A mask a character must pass to apply or join (for example `-BASECLASS +Thief`). Empty means anyone. |
| `ENTRYSCRIPT`, `EXITSCRIPT` | MOBPROG source run when a member joins or leaves. They run whenever a character becomes a member (leaves the applicant role or is added) or leaves, however it comes about (`CLANACCEPT`, `CLANEXILE`, `CLANRESIGN`, `GuildMaster`, `MPSETCLAN` on a player). `CLAN_PROG JOIN` fires alongside the entry script. `ORGCHECK GOVERNMENTS` checks them with the script checker. |
| `AUTOPROMOTEBY` | `NONE`, `LEVEL`, `RANK`, `GOLD`, `XP`, `LEVEL_OVERWRITE`, `RANK_OVERWRITE`, `XP_OVERWRITE`, `GOLD_OVERWRITE`, `JOINDATE`, `JOINDATE_OVERWRITE`, `MERIT`, `MERIT_DEMOTE`. See [Dues, ranks, merit and news](#dues-ranks-merit-and-news). |
| `PUBLIC` | Whether the clan type is listed and joinable by outsiders. |
| `FAMILYONLY` | Restricts membership to a family. |
| `RIVALROUS` | Whether clans of this type can declare war and be fought. `CLANDECLARE` refuses when either clan is not rivalrous. |
| `OVERRIDEMINMEMBERS` | Minimum members for this type, overriding `MINCLANMEMBERS`. `0` means the clan is never purged for size. |
| `XPPERLEVELFORMULA` | Clan XP needed per clan level; `@x1` is the level. Empty uses the engine default. |
| `MISCVARS` | `VAR=value` pairs available to the type's text. |
| `CONQUEST` | `ENABLED` (clans of this type may conquer areas), `ITEMLOYALTY` (clan items buy loyalty), `DEITYBASIS` (conquest by worship). |
| `TITLES` | `<TITLE>` patterns granted to every member. `*` is the character's name, `@x1` the clan name, `@x2` the position name. |
| `ABILITIES` | `<ABILITY ID PROFF LEVEL QUALIFYONLY PARM ROLES>`: an ability granted at a clan level. `QUALIFYONLY=true` makes it earnable instead of granted. |
| `EFFECTS` | `<EFFECT ID LEVEL PARMS ROLES>`: a property added at a clan level, for example `Prop_Adjuster` with `PARMS="ATTACK+1 HITPOINTS+4"`. |
| `DUESPERIOD`, `DUESGRACE`, `DUESLATE`, `DUESAUTO`, `STANDINGFACTION`, `NPCFLAGS` | CoffeeSUD organisation settings, defaults for every clan of this type. A clan's own value overrides the government's. See [Dues, ranks, merit and news](#dues-ranks-merit-and-news). |

**`<POSITION>` attributes**

| Attribute | Meaning |
| --- | --- |
| `ID` | Text key used in `AUTOPOSITION`, `-CLANRANK`, `ROLE=`, `KIT.` and script functions. May contain spaces. |
| `ROLEID` | The number stored on members; must follow the `0..n-1` rule. |
| `RANK` | Seniority; lower is higher. Several positions may share a rank. |
| `NAME`, `PLURAL` | Display names (used in titles and listings). |
| `MAX` | Seat limit: a number or a percentage such as `5%`. Percentages are of the membership. |
| `INNERMASK` | A mask a member must pass to hold the position. It gates `CLANASSIGN`, `GuildMaster` joining (for the accept position), auto-promotion and merit. |
| `PUBLIC` | Whether holders are shown in the clan's public member listing. |

Children of `<POSITION>`: `<POWER>` entries (see next section) and an optional
`<TITLES>` block of `<TITLE>` patterns granted to holders of the position.

### Powers and voting

The 29 powers (the `Clan.Function` enum). A position without a power cannot do
that thing; with a power it can, and a power listed in the government's
`<VOTING>` block makes the action a ballot.

| Group | Powers |
| --- | --- |
| Membership | `ACCEPT`, `REJECT`, `ASSIGN`, `EXILE` |
| Property and places | `SET_HOME`, `SET_DONATE`, `MORGUE`, `PROPERTY_OWNER`, `HOME_PRIVS` |
| Money | `DEPOSIT`, `WITHDRAW`, `DEPOSIT_LIST`, `TAX` (also gates `CLANDUES` and `CLANTAX`) |
| Politics | `DECLARE`, `PREMISE`, `VOTE_ASSIGN`, `VOTE_OTHER`, `ENCHANT` |
| Orders | `ORDER_UNDERLINGS` (the `ORDER` command to lower-ranked members), `ORDER_CONQUERED` (orders to the residents of a conquered area) |
| Records | `CHANNEL`, `READ_MOTD`, `CREATE_MOTD` (both involved in `CLANMOTD`), `JOURNAL`, `CREATE_LAW` (law books), `CREATE_CALENDAR` (`CALENDAR CLAN`) |
| Benefits | `LIST_MEMBERS`, `CLAN_BENEFITS` (clan spells and level benefits), `CLAN_TITLES` |

**Votes.** For each function named in `<VOTING>`, a position that has
`VOTE_ASSIGN` (for `ASSIGN`) or `VOTE_OTHER` (for the others) gets
`MUST_VOTE_ON` for it. A `<POWER>` written directly on the position afterwards
turns that function back into `CAN_DO`, so a boss with `ACCEPT` listed on the
position does not vote on accepting even if `ACCEPT` is in `<VOTING>`.

When a member uses a command that needs a ballot, the engine asks whether to
start the vote. If the clan has exactly one voter the action runs at once. A
ballot closes when everyone has voted or `MAXDAYS` have passed. A quorum is
`QUORUMPCT` percent of the eligible voters (at least 2 unless there is a
single voter), and the yes votes must outnumber the no votes; a tie fails. A
passing ballot runs the stored command with the authority of the top position.
Voters cast votes with `CLANVOTE`. Vote timing runs on the game clock under
`CLANCLOCK=GAME`.

Single-player consequence: each party character is a separate member and
voter, so a `FELLOWSHIP` of one player's party would otherwise have to vote
with itself. `CLANVOTES=PROFILE` fixes that (see
[Party-founded clans](#party-founded-clans)). NPC governments avoid the whole
issue with an empty `<VOTING>` block and no `VOTE_*` powers.

### Clans as world content

A clan is created with `CREATE CLAN <name>` (needs the `CMDCLANS` permission
or builder mode) and edited with `MODIFY CLAN <name>`, which walks through:
government, category, premise, minimum members, experience, tax rate,
dues per period, the `DUESPERIOD`, `DUESGRACE`, `DUESLATE`, `DUESAUTO`,
`STANDINGFACTION` and `FLAGS` settings, and the relation to every other clan
(`NEUTRAL`, `WAR`, `HOSTILE`, `FRIENDLY` or `ALLY`). `LIST CLANS` and
`DESTROY CLAN` complete the set.

In builder mode `SAVE` writes every clan that has no player members (and every
`NPCRUN` clan) to `world/clans.cmare`, so a new game starts with them. The
file is generated and best not edited by hand:

```xml
<CLANS>
<CLAN><ID>Thieves Guild</ID><PREMISE>...</PREMISE><ACCEPT>...</ACCEPT><STATUS>0</STATUS>
<DATA>&lt;POLITICS&gt;...&lt;/POLITICS&gt;</DATA><RECALL>Riverton#40</RECALL>
<DONATION></DONATION><MORGUE></MORGUE><TROPHIES>0</TROPHIES></CLAN>
</CLANS>
```

`DATA` holds the politics block: government id, tax rate, dues, experience,
level, flags, category, minimum members, the per-clan organisation settings
(`ORG_DUESPERIOD` and friends) and the saved relations. The clan table loads
before the areas, so mobs in area files can name clans that exist only in
`clans.cmare`. In a restored saved game the save's clan table wins over
`clans.cmare`; clans founded during play stay save state.

NPCs join clans from world content with a `<CLAN>` tag on the mob in the area
file (what the builder tools write for you):

```xml
<CLAN ROLE="CUTPURSE">Thieves Guild</CLAN>
```

`ROLE` accepts a position ID or a role number. The `ClanHelper` behavior sets
membership too: `ClanHelper CLAN="Thieves Guild" ROLE=CUTPURSE` (the older
positional form `ClanHelper Thieves Guild` still works) and also makes the mob
join fights on behalf of fellow members. In a script, `MPSETCLAN <mob> <clan>
<role>` does the same, with a role number or the position's name.

Use `ORGCHECK CLANS` (in builder mode) to list world-file mobs whose `CLAN`
names a clan that does not exist; those mobs lose the clan at load.

### Clan commands

All of these exist in CoffeeSUD. Optional arguments are in parentheses. A
character who belongs to several clans names the clan with the first argument;
otherwise the first clan the character holds the power in is used.

| Command | Usage | Purpose | Power needed |
| --- | --- | --- | --- |
| `CLANLIST` (`CLANS`) | `CLANLIST` | List the clans. | none |
| `CLANDETAILS` (`CLAN`) | `CLANDETAILS (clan)` | Show a clan: level, premise, members by position, relations, areas, and, for merit governments, "Next rank: ..., requires: ...". Also makes that clan the first choice for other clan commands. | none |
| `CLANWHO` (`CLWH`) | `CLANWHO (clan/member)` | Members present, or one member's clan record. | `LIST_MEMBERS` |
| `CLANAPPLY` | `CLANAPPLY clan` | Apply to a clan. NPC-run clans refuse direct applications: join through the guildmaster. | none |
| `CLANACCEPT` | `CLANACCEPT (clan) member/ALL` | Accept an applicant. | `ACCEPT` |
| `CLANREJECT` | `CLANREJECT (clan) member/ALL` | Reject an applicant. | `REJECT` |
| `CLANEXILE` | `CLANEXILE (clan) member` | Remove a member. | `EXILE` |
| `CLANASSIGN` | `CLANASSIGN (clan) member position` | Promote or demote to a position (its `INNERMASK` and seats must allow it). | `ASSIGN` |
| `CLANRESIGN` | `CLANRESIGN (clan)` | Leave a clan. | none |
| `CLANCREATE` | `CLANCREATE` or `CLANCREATE name PARTY` | Found a clan for `CLANCOST` gold, or a Fellowship for the whole party. | none; needs a public government category |
| `CLANDECLARE` | `CLANDECLARE (clan) other relation` | Declare `NEUTRAL`, `WAR`, `HOSTILE`, `FRIENDLY` or `ALLY` toward another clan. At most one change per game month per pair. Both clans must be rivalrous. | `DECLARE` |
| `CLANPREMISE` | `CLANPREMISE (clan)` | Write the clan's premise. | `PREMISE` |
| `CLANMOTD` | `CLANMOTD (clan)` | Set the message of the day. | `CREATE_MOTD` / `READ_MOTD` |
| `CLANQUAL` | `CLANQUAL (clan)` | Edit the qualification mask applicants must pass. | `PREMISE` |
| `CLANTAX` | `CLANTAX (clan) (percent)` | Set the share of members' experience the clan takes. | `TAX` |
| `CLANDUES` | `CLANDUES PAY (clan)`; `CLANDUES (clan) (amount)`; `CLANDUES FORGIVE member (clan)` | Pay arrears from carried money; list who is behind and set the per-period charge; forgive a debt. | `TAX` for all but `PAY` |
| `CLANVOTE` | `CLANVOTE (clan) (number)` | List and cast votes on open ballots. | `VOTE_*` |
| `CLANHOMESET`, `CLANDONATESET`, `CLANMORGUESET` | `CLANxxxSET (clan)` | Set the current room as the clan's home, donation room or morgue. | `SET_HOME`, `SET_DONATE`, `MORGUE` |
| `CLANKILLS`, `CLANPVPKILLS` | `CLANKILLS (clan)` | Members ranked by kills of rival-clan mobs (and players). | membership |
| `CALENDAR CLAN` | see `CALENDAR` | Add clan events. | `CREATE_CALENDAR` |
| `ORDER` | see `ORDER` | Command lower-ranked members. | `ORDER_UNDERLINGS` |
| `PARTY CLANS` | `PARTY CLANS` | Show every party character's clans and ranks. | none |

Shipped help for each is in [tool-reference.md](tool-reference.md) and
`HELP CLANS`. Builder commands are `CREATE|MODIFY|DESTROY|LIST CLAN` and
`CREATE|MODIFY|DESTROY|LIST GOVERNMENT`, and `CREATE GUILD` (see
[Scaffold a guild](#scaffold-a-guild)).

### Dues, ranks, merit and news

**Dues (CoffeeSUD).** The clan's `DUES` amount is charged per period. The
period and consequences are organisation settings, given on the government as
defaults and overridable on each clan (`MODIFY CLAN`, or `MPSETCLANDATA clan
KEY value`):

| Setting | Values | Default if unset |
| --- | --- | --- |
| `DUESPERIOD` | `DAY`, `WEEK`, `MONTH`, `YEAR`, on the game calendar | `YEAR` |
| `DUESGRACE` | Number of unpaid periods tolerated before lateness counts | `0` |
| `DUESLATE` | Comma list of steps `WARN`, `DEMOTE`, `EXILE`. The first missed period past grace takes step 1, the second step 2, and so on; the last step repeats | `WARN` |
| `DUESAUTO` | `TRUE`: dues are paid automatically from carried money, then from the member's bank accounts. `FALSE`: the member pays with `CLANDUES PAY` or by saying `dues` to the guildmaster | `TRUE` (the NPC templates set `false`) |

`DUES` itself is the per-period charge, edited with `MODIFY CLAN` ("Clan
Dues/period"), `CLANDUES clan amount` or `MPSETCLANDATA clan DUES n`. It is `0`
by default, which means no dues at all, so nothing in this section happens
until you set a charge. The saved `DUESNEXT` deadline prevents billing the same
period twice after a restore; changing `DUESPERIOD` resets it. Payment goes to
a bank account of the clan if it has one, otherwise to the clan's `TREASURY`
setting. NPC members are counted as members but never owe dues. The script
function `CLANMEMBER(who clan DUESOWED)` reads a member's arrears and the
`CLAN_PROG DUESLATE` trigger fires on each late step (see
[26-mobprog-triggers.md](26-mobprog-triggers.md)).

Under `CLANCLOCK=REAL` the inherited yearly collection (bank accounts only,
needs a clan bank) runs instead; leave the default `GAME` unless you want that.

**Ranks.** A member moves by `CLANASSIGN`, by scripts (`MPSETCLAN`), by
`GuildMaster`, and by the government's `AUTOPROMOTEBY` rule. The inherited
modes sort members by level, rank, gold or experience donated, or join date
and fill seats once a day; the `_OVERWRITE` forms let the best candidates
displace incumbents. `NONE` disables automatic promotion.

**Merit (CoffeeSUD).** `MERIT` moves a member to the *most senior* position
whose `INNERMASK` the member passes and whose seats (`MAX`) are not full.
`MERIT_DEMOTE` also lowers a member who no longer passes. Both are checked on
the live character whenever a faction band, a level or a tattoo changes, on a
quest win, and each game day. A member who is late with dues is not promoted
over their current rank. The member is told of a change, `CLAN_PROG PROMOTE`
or `DEMOTE` fires, and `CLANDETAILS` and the guildmaster's `rank` reply say
what the next rank requires, plus the clan's `STANDINGFACTION` band when set.
Governance (powers, voting) is independent of the rank's flattering title:
grant `ACCEPT`, `ASSIGN`, `EXILE`, `DECLARE`, `WITHDRAW` and friends only to
positions that should really have them. To drive advancement entirely from
your own scripts, use `AUTOPROMOTEBY=NONE` and `MPSETCLAN`/`CLANASSIGN`; the
`GuildMaster` parameter `RANKS=SCRIPT` only stops the guildmaster itself from
advancing members.

**News.** Level changes, promotions, relations and warnings are announced
according to `CLANNEWS`. The affected member always gets a personal line, for
example "You are now a Cutpurse of the Thieves Guild."

### Clan settings

World settings (put in `world/world.ini`, or `WORLD SET KEY=value` in builder
mode; they take effect on the next start; defaults are in `coffeemud.ini`):

| Key | Values | Meaning |
| --- | --- | --- |
| `CLANCLOCK` | `GAME` (default), `REAL` | Whether clan maintenance, votes, dues, purge grace and the `CLANDECLARE` cooldown count game time or real time. |
| `CLANNEWS` | `INFO` (default), `MEMBERS`, `OFF` | Clan announcements go to the `INFO` channel, to each member character's window, or nowhere. |
| `CLANVOTES` | `CHARACTER` (default), `PROFILE` | Whether each character is a voter or the whole profile counts once. |
| `AUTOREACTION` | comma list of `AREA`, `CLAN`, `DEITY`, `NAME`, `PLANAR`, `RACE` | Which automatic reaction factions exist. `CLAN` generates a standing faction for each `NPCRUN` clan (see [Automatic reaction factions](#automatic-reaction-factions)). Shipped value: `PLANAR`. |
| `CLANCOST` | gold (default 10000) | Cost to found a clan, including `CLANCREATE name PARTY`. |
| `MINCLANMEMBERS`, `MAXCLANMEMBERS` | numbers (1, 0) | Minimum members to stay active, maximum members before applications are refused (`0` no limit). A government's `OVERRIDEMINMEMBERS` or a clan's own minimum wins. |
| `DAYSCLANDEATH`, `DAYSCLANOVERTHROW` | game days (2160, 952) | Inactivity windows used by maintenance. |
| `MAXCLANCATS` | `NPCGUILD 10, 1` | Per-category cap on simultaneous memberships; a bare number is the default for other categories. |

`coffeemud.ini` also holds the inherited `MINCLANLEVEL`, vote defaults
(`CLANVOTEO/D/R`), trophy prizes (`CLANTROP*`), `CLANENCHCOST`, `PUBLICCLANCATS`
and the unused web/forum keys. Clan trophies (awards for most members, most
conquered areas and so on) are still computed and announced.

The shipped comment on `MAXCLANCATS` says `0` means unlimited; in the code a
value of `0` blocks all membership of that category (the join checks test
`size() >= max`). Use a large number instead.

### Party-founded clans

Each party character is a separate member and, under `CLANVOTES=CHARACTER`, a
separate voter. `CLANCREATE <name> PARTY` founds a `FELLOWSHIP` for the party:
every character joins, the founding cost is paid once from the founder, and
the founder takes the top position. All characters must belong to the
founder's profile and pass the government's requirements. With
`CLANVOTES=PROFILE` the profile counts as one voter, so a power that needs a
ballot runs at once when every eligible voter is in the profile; it does not
give powers to ranks that lack them and does not override other profiles'
votes. Test a party-founded clan around save and restore, since each character
has its own membership row.

## NPC organisations and guilds

An NPC organisation is a clan with the `NPCRUN` flag, one of the `NPCGUILD`
governments, and an NPC (the guildmaster) that handles joining, rank, dues
and kit by conversation. It is how a single-player world gets thieves'
guilds, temples, knightly orders and cults that the player can join, rise
in, be fined by and be thrown out of. Source:
[Guilds.java](../../CoffeeMud/com/planet_ink/coffee_mud/core/Guilds.java) and
[GuildMaster.java](../../CoffeeMud/com/planet_ink/coffee_mud/Behaviors/GuildMaster.java).
The longer option reference is [organizations.md](../organizations.md).

### Scaffold a guild

In builder mode (or with the `CMDCLANS`, `CMDROOMS` and `CMDMOBS`
permissions):

```text
CREATE GUILD Knights of the Rose TEMPLATE=ORDER HALL=Castle#14
```

`TEMPLATE` is the name of any government in `clangovernments.xml`
(`GUILD_NPC` when omitted; the shipped templates are `GUILD_NPC`, `ORDER`,
`TEMPLE` and `CULT`). `HALL` is a room ID (the current room when omitted);
the hall needs at least one direction with no exit. The name may not contain
a double quote or `<`, and must not already be a clan. The command makes:

| Created | Details |
| --- | --- |
| The clan | `NPCRUN`, minimum members `0`, active, recall room set to the hall. |
| A standing faction | `CLAN_<CODE>` (the clan name upper-cased, other characters turned into `_`), built from `resources/examples/clanreaction.ini` and saved with the world. Set as the clan's `STANDINGFACTION`. |
| A guildmaster | A `GenMob` named "the (name) guildmaster" in the hall, a member at the top rank, with `GuildMaster CLAN="(name)" RANKS=MERIT DUES=TRUE`. |
| A trainer | A `GenMob` with `MOBTeacher`, a member at the accept position. It teaches nothing until you configure it (see [21-mobs.md](21-mobs.md)). |
| A quartermaster | A `GenShopkeeper` with an empty shelf and `PREJUDICE` `BUY=0.8 CLAN:(name with underscores)` (members buy at 80%). |
| A members' room | A `StoneRoom` in the first free direction from the hall, linked both ways with open exits, gated by `Prop_ReqEntry` `-CLANRANK "+(name)>=(accept position)"`. |

Then `SAVE` (the clan goes to `world/clans.cmare`, the faction to
`world/resources/factions/`). The scaffold is a starting point, not a finished
guild. Before shipping: replace the placeholder `INNERMASK` tattoos in the
government, stock the quartermaster, give the trainer its lessons, write the
guildmaster's quests and rank dialogue, set `DUES` on the clan, check the
exits and doors, and test join, advance, leave and rejoin. Because the
template is a government shared by every clan built from it, give a guild
that needs its own ranks its own government (see
[Example 1](#example-1-a-thieves-guild-with-ranks-and-dues)).

### GuildMaster

`GuildMaster` is a behavior for NPCs ([24-behaviors-and-masks.md](24-behaviors-and-masks.md#guildmaster)).
It listens for exact phrases spoken in its room by a character and answers the
speaker. Phrases (case-insensitive, nothing else in the sentence): `join`,
`rank`, `advance`, `leave`, `dues`, `kit`, `kit replace`. Each party character
is handled on its own.

Parameters (one parameter line):

| Parameter | Default | Meaning |
| --- | --- | --- |
| `CLAN="name"` | required | The organisation. Without it the master says "This guild is not configured." |
| `JOINMASK="mask"` | the clan's basic requirement mask | An extra mask the joiner must pass (the government's `REQUIREDMASK` is checked as well). Use single quotes for a quoted argument inside, as `"-CLANRANK '+Temple of Arden>=ACOLYTE'"`. |
| `JOINFEE=n` | `0` | Gold charged on joining, in the speaker's currency. |
| `JOINQUEST="name"` | none | A quest the speaker must have won. |
| `RANKS=MERIT\|SCRIPT` | `MERIT` | `MERIT` re-checks merit on `join`, `rank` and `advance`. `SCRIPT` leaves advancement to your scripts. |
| `DUES=TRUE\|FALSE` | `TRUE` | Whether saying `dues` collects arrears. |
| `KIT.POSITION="catalog item,catalog item"` | none | Items issued to a member of that position (use the position `ID`, for example `KIT.INITIATE`). Items are names in the world [catalog](22-items.md). |
| `KITFEE=n` | `0` | Charge for `kit replace`. |
| `LEAVEMSG="text"`, `REFUSEMSG="text"` | stock lines | The reply on leaving and on a refused join. |

`join` checks, in order: not already a member; the government's required mask;
`JOINMASK`; `JOINQUEST` won; the fee affordable; the category membership cap
(`MAXCLANCATS`, default ten for `NPCGUILD`); and the accept position's own
`INNERMASK`. On success the fee is paid, the character becomes a member at the
accept position (running the government's entry script and `CLAN_PROG JOIN`
once), merit is evaluated, and the starting kit is issued. `leave` removes the
membership and runs the exit script. `kit` gives the kit for the current
position once per character and rank (recorded as a `GUILDKIT_...` tattoo);
`kit replace` issues it again for `KITFEE`. If a kit item is missing from the
catalog the master says the kit is unavailable and gives nothing.

### Recruitment and advancement

To build a guild by hand (this is what `CREATE GUILD` automates):

1. Choose or create a government with the ranks and powers you want
   ([the governments file](#the-governments-file)). Set `AUTOPROMOTEBY` to
   `MERIT`, `NPCFLAGS` to `NPCRUN`, `OVERRIDEMINMEMBERS` to `0`.
2. `CREATE CLAN <name>`, then `MODIFY CLAN <name>` to select the government,
   set `DUES`, a minimum of `0` members, `STANDINGFACTION` and the `NPCRUN` flag.
3. Make the standing faction (a hand-written file, or let the `AUTOREACTION=CLAN`
   template generate `CLAN_<CODE>`).
4. Place the guildmaster NPC and add `GuildMaster` with a parameter line.
   Place the trainer and quartermaster, and gate the members' rooms with
   `Prop_ReqEntry` `-CLANRANK "+Name>=POSITION"`.
5. Decide how members earn rank. The `INNERMASK` of each position is an
   ordinary mask: it can test tattoos (`-TATTOO +GUILD_NPC_RANK_1`), faction
   bands (`-FACTION +THIEVES_TRUSTED`), level, class, quests (`-QUESTWIN`),
   or a script condition (`-IF`). Tattoos come from `MPTATTOO` in quest and
   dialogue scripts.
6. Test: lack of money, a missing quest, a full rank (`MAX`), advancement after
   the requirement is met, dues arrears, demotion and expulsion, and leaving
   and rejoining.

`MERIT` promotes to the most senior position whose mask the member passes and
whose seats are free; `MERIT_DEMOTE` also demotes. NPC members count toward
seats, so a `MAX=1` guildmaster seat held by the NPC guildmaster is not
available to the player. Rechecks run whenever a faction band, level or tattoo
changes, on quest wins, and each game day, so a promotion appears as soon as the
mask is satisfied, usually without talking to the master.

CoffeeSUD alternatives: for a simple "rank by deeds" ladder with no powers,
dues or diplomacy, a tattoo rank ladder (`world/ranks.ini`, `MPRANK`, the
`RANKS` command; see [03-characters.md](03-characters.md)) needs no clan at
all. Use a clan when you want powers, dues, relations, shop prices by clan, or
`-CLANRANK` masks.

### Diplomacy and conquest

Clans declare a relation toward each other: `NEUTRAL`, `WAR`, `HOSTILE`,
`FRIENDLY` or `ALLY`. A relation matters when it is **mutual**: the combined
state of clans A and B comes from what each declared about the other.

| A declares \ B declares | NEUTRAL | WAR | HOSTILE | FRIENDLY | ALLY |
| --- | --- | --- | --- | --- | --- |
| NEUTRAL | NEUTRAL | NEUTRAL | NEUTRAL | NEUTRAL | NEUTRAL |
| WAR | NEUTRAL | **WAR** | HOSTILE | NEUTRAL | NEUTRAL |
| HOSTILE | NEUTRAL | HOSTILE | **HOSTILE** | NEUTRAL | NEUTRAL |
| FRIENDLY | NEUTRAL | NEUTRAL | NEUTRAL | **FRIENDLY** | FRIENDLY |
| ALLY | NEUTRAL | NEUTRAL | NEUTRAL | FRIENDLY | **ALLY** |

A clan allied to someone who is at war with a third clan is also at war with
that third clan. Hostile and war clans may take each other's conquered areas;
war permits player-versus-player fighting under the usual rules.

CoffeeSUD gives scripts direct control, with no cooldown and no need for a
boss:

```text
mpclanrelation 'Orc Horde' 'Riverton Militia' WAR MUTUAL
```

`MPCLANRELATION <clan> <other clan> <WAR|HOSTILE|NEUTRAL|FRIENDLY|ALLY>
[MUTUAL]` sets the first clan's declaration, and the other's too with
`MUTUAL`. Without `MUTUAL` only one side changes and a one-sided war shows as
`NEUTRAL`. It fires `CLAN_PROG RELATION`. `MODIFY CLAN` also edits a clan's
outgoing relations, and starting relations are saved in `world/clans.cmare`.
The script function `CLANRELATION(clanA clanB)` returns the combined state, and
`clanrelation(clanA clanB == WAR)` tests it.

**ClanEnemyAggressive.** An NPC with this behavior attacks any character or NPC
whose clan has a live relation with the NPC's clan in a set of states. It reads
the combined relation each time, so a script that makes peace stops the
fighting at once.

| Parameter | Default | Meaning |
| --- | --- | --- |
| `CLAN="name"` | the NPC's own clan | The clan whose enemies are attacked. |
| `STATES=WAR,HOSTILE` | `WAR,HOSTILE` | Combined relation states that count as enemies. |
| `CHANCE=n` | `100` | Percent chance per check. |
| `CHECKLEVEL` | off | Skip targets more than five levels above the NPC. |
| `MESSAGE="text"` | none | Shout when attacking. |

`ClanHelper` (fellow members join each other's fights) is the defensive
counterpart. Both need the NPC to be a member of the clan.

## Factions

Source: [DefaultFaction.java](../../CoffeeMud/com/planet_ink/coffee_mud/Common/DefaultFaction.java),
[Factions.java](../../CoffeeMud/com/planet_ink/coffee_mud/Libraries/Factions.java),
shipped files in [resources/factions](../../CoffeeMud/resources/factions/) and
[resources/examples](../../CoffeeMud/resources/examples/), annotated template
[factiontemplate.ini](../../CoffeeMud/resources/examples/factiontemplate.ini).

A faction is a numeric standing that a mob or character may carry, divided
into named bands ("ranges"). Deeds change the number according to **change
triggers**. The band the number is in decides **reactions** (how other NPCs
treat the holder), **effects** (what the holder gets), which **abilities** the
holder is considered fit to use, and which text and prices they see. Shipped
factions are `alignment.ini` (good and evil), `inclination.ini` (lawful and
chaotic) and `triumph.ini` (an area-clearing score). A world adds its own for
temples, guilds, towns and species.

Use a faction when the player's standing is a matter of degree and history (a
town's regard, a temple's trust). Use a clan when the player belongs, with
powers and a rank. Use a tattoo (a simple saved flag) for yes/no facts.

### Faction files

A faction is a text file `resources/factions/NAME.ini`. A world's own file is
`world/resources/factions/NAME.ini`; builder-mode edits are written there. The
faction's ID is the file name in upper case (`THIEVES.INI`); script commands,
`STANDINGFACTION`, `FACTIONS=` and masks accept the file name, the ID or the
display name. Lines are `KEY=value`; `#` starts a comment; keys are case
sensitive and upper case. Keys that come in numbered families (`RANGE1`,
`CHANGE1`...) may use any number; only `PLAYEREFFECT` must count 1, 2, 3...
without a gap.

Create, edit and remove factions with `CREATE FACTION name.ini`,
`MODIFY FACTION name`, `LIST FACTIONS` and `DESTROY FACTION` (permission
`CMDFACTIONS`; `CREATE` asks to confirm and starts from the annotated
template). Run `ORGCHECK` after hand-editing a file ([Faction tools](#faction-tools)).

Factions are loaded on first use. A faction that must hand out
`AUTODEFAULTS` values to every new character has to be loaded at start:
list its file in the `FACTIONS` setting (semicolon list, in `world/world.ini`
or `coffeemud.ini`; shipped value `alignment.ini`).

**Settings**

| Key | Values and default | Meaning |
| --- | --- | --- |
| `NAME` | text | What the player sees in `FACTIONS` and `SCORE`. |
| `MINIMUM`, `MAXIMUM` | integers | Nominal bounds. In practice the engine recomputes the bounds from the ranges (lowest `low`, highest `high`), so make the ranges cover the scale you want. |
| `SCOREDISPLAY` | `TRUE`/`FALSE` | Show in `SCORE`. |
| `SHOWINFACTIONSCMD` | `TRUE`/`FALSE` | List in the `FACTIONS` command. |
| `SPECIALREPORTED` | `TRUE`/`FALSE` | Average the faction into area reports. Only one faction should have it. |
| `EDITALONE` | `TRUE`/`FALSE` | A shortcut for this faction in the mob/area editors. |
| `INHERITED` | `TRUE`/`FALSE`, default `TRUE` | Whether offspring inherit the faction. |
| `DEFAULT` | `value mask; value mask; value` | Value given when the faction is added by hand or by an event. The first entry whose mask the mob passes wins; a bare number is the fallback. At least one value. Default `0`. |
| `AUTODEFAULTS` | same form | Values given automatically to mobs and characters on creation or entry to the world, only if the faction is loaded and they have no saved value. Empty means never. |
| `AUTOCHOICES` | `value; value; value` (masks allowed) | Values a new character may choose at creation. The band names are shown as the choices. Blank means no choice. Part of the `FACTIONS` step of `CHARCREATIONSCRIPTS`. |
| `CHOICEINTRO` | resource path | Text shown before the choice. |
| `RATEMODIFIER` | decimal, `1.0` | Multiplier on every change. |
| `EXPERIENCE` | `NONE` (default), `EXTREME`, `HIGHER`, `LOWER`, `FOLLOWHIGHER`, `FOLLOWLOWER` | How the faction scales experience from kills: `EXTREME` more experience for killing opposite standing; `HIGHER`/`LOWER` by the killer's own value; `FOLLOWHIGHER`/`FOLLOWLOWER` by the victim's value. At most a 25 percent swing. |
| `USELIGHTREACTIONS` | `true`/`false` | Use the cheaper reaction engine (different holders in one room may each get some reaction) instead of the stricter one (the winning holder gets the reactions). The shipped example reaction files use `true`. |
| `SHOWMASK` | mask | The faction appears in `FACTIONS` and `SCORE` only for holders who pass the mask (for example `-TATTOO +READ_CULT_LETTER`). |
| `HIDDENNAME` | text | What an explicit `FACTIONS name` lookup says while the standing is hidden, instead of revealing it. |

**`RANGEn = low ; high ; display name ; CODE ; alignment flag`**

Bands partition the scale. `CODE` is the globally unique, simple word used in
masks (`-FACTION +THIEVES_TRUSTED`), in `PREJUDICE` strings, in
`PLAYEREFFECT` and `REACTION` lines and by `FACTIONBAND`. The optional last
field marks the band as an alignment equivalent: `EVIL`, `NEUTRAL`, `GOOD`,
`LAWFUL`, `CHAOTIC`, `MODERATE` (used by `alignment.ini` and `inclination.ini`
so the engine can ask "is this mob evil"); leave it blank for ordinary
factions. Do not leave gaps between bands or overlap them: values in a gap have
no band, and overlapping bands make the first match win. `ORGCHECK` reports both.

```ini
RANGE1=-1000;-501;Shunned;THIEVES_SHUNNED;
RANGE2=-500;-101;Distrusted;THIEVES_DISTRUSTED;
RANGE3=-100;100;Neutral;THIEVES_NEUTRAL;
RANGE4=101;500;Trusted;THIEVES_TRUSTED;
RANGE5=501;800;Honoured;THIEVES_HONOURED;
RANGE6=801;1000;Exalted;THIEVES_EXALTED;
```

**`FACTORn = gain ; loss ; mask`** Certain groups gain or lose standing more
easily: `FACTOR1=0.5;1.5;-RACE +Dwarf` halves gains and increases losses for
dwarves. The first matching entry applies.

**`RELATIONn = FACTION.INI pct%`** Any change to this faction also changes the
named one by that percentage of the change, positive or negative
(`RELATION1=ALIGNMENT.INI 5.35%`). Used to make rivals move together and
oppositely.

### Change triggers

`CHANGEn = TRIGGER(parameters);DIRECTION;FACTOR%;FLAGS;TARGET MASK`

The five fields are separated by semicolons, and trailing fields may be
omitted. Example: `CHANGE1=BRIBE(AMOUNT=10);UP;10%;OUTSIDER JUST100;-CLAN "+Thieves Guild"`.

**Triggers**

| Trigger | Fires when | Parameters |
| --- | --- | --- |
| `MURDER` | A holder of this faction is killed. The change goes to those who benefit from the kill (the killer and allies in the fight), with the victim as the target. | none |
| `KILL` | A holder of this faction kills something. | `WAIT` |
| `DYING` | A holder dies of any cause. | `WAIT` |
| `BRIBE` | A holder gives coins to another mob (not to a shopkeeper). The gift must be at least the larger of `AMOUNT` and `PCT` percent of what the receiver carries (1 if both are zero). | `AMOUNT=n`, `PCT=n` |
| `GIFT` | A holder gives a non-coin item to a mob. (CoffeeSUD addition.) | none |
| `SOCIAL` | A holder uses a social on a mob. | `ID=SOCIALNAME` or `ID=ALL`, `WAIT` |
| a social name (`HUG *`) | The same, written with the social as the trigger: its name with `<T-NAME>`, `SELF` or `*` for any variant. | `WAIT` |
| `TALK` | A holder says something to a mob (or in a room with listening mobs) matching a regular expression. | `REGEX`, `WAIT` |
| `MUDCHAT` | A holder converses with a `MudChat` mob and the mob replies with something new. | `WAIT` |
| `ARRESTED` | A holder is handcuffed by an officer of the law. | none |
| `AREAKILL` | A holder has helped kill `PCT` percent of the mobs of the area they are in. | `PCT`, `WAIT` |
| `AREAASS` | The same for the highest-level mobs of an area. | `PCT`, `WAIT` |
| `AREAEXPLORE` | A holder has visited `PCT` percent of the area (with no `PCT`, the first room visited in it). | `PCT`, `WAIT` |
| `SINK`, `SUNK` | A holder sinks a ship; a holder's own ship is sunk. | `WAIT` |
| `TIME` | Every ten ticks (about 40 seconds of real time) for the characters currently in the game. `ROUNDS=n` stretches the period to n times that. | `ROUNDS` |
| `ADDOUTSIDER` | Every ten ticks, a character who does not hold the faction but meets the mask gets it added. Use with direction `ADD`. | none |
| `MSG_TYPE` | Any message of that type from the holder; the name is `MSG_` plus a `CMMsg` type word (`MSG_GET`, `MSG_DROP`, `MSG_GIVE`...). | `SMSG`, `TMSG`, `OMSG` (text the message must contain), `SRC`, `TGT`, `OTH` (names; `me` means the holder), `WAIT` |
| An ability ID | The holder uses that ability (`Thief_Steal`). | `WAIT` |
| An ability type, domain or flag word | The holder uses any ability of that kind (`THIEF SKILL`, `ILLUSION`, `HEALING`). | `WAIT` |

Parameters go in parentheses after the trigger, separated by spaces. Every
trigger accepts `MASK="..."` (a mask the *acting* character must pass),
`CHANCE=n`, `WITHIN=n` (only within n ticks of the event timer being reset) and
`WAIT=n` (ticks to wait before it can fire again for the same character). For
example `AREAKILL(PCT=80 WAIT=9999 MASK="+AREAINSTANCE")`.

**Directions** (first letters are enough): `UP` and `DOWN` change the value by
the standard amount; `TOWARDS` and `AWAY` move toward or away from the target's
standing; `OPPOSITE` moves away from the target's side in proportion to how
far apart they are; `MINIMUM` and `MAXIMUM` jump to the end of the scale;
`REMOVE` takes the faction away; `ADD` gives it the `DEFAULT` value (for
`ADDOUTSIDER`).

**Factor**: a percentage multiplier on the standard change. The standard
change is 100 points, adjusted by the level gap between actor and target
(killing something far below you counts for nothing) unless `JUST100` is
given, multiplied by `RATEMODIFIER`, the factor and any `FACTOR` entry. Since
usual scales run to hundreds or thousands of points, a factor of `1%` to `10%`
makes minor deeds and `100%` makes a major one.

**Flags** (space separated)

| Flag | Meaning |
| --- | --- |
| `OUTSIDER` | The change applies even if the target does not hold the faction. Without it, a change involving a target only happens if the target holds the faction too. |
| `SELFOK` | Allow the actor to be its own target (needed for abilities cast on oneself). |
| `JUST100` | Use the flat 100-point base rather than adjusting by level difference. |
| `XP=n`, `RPXP=n` | Also award experience or roleplay experience. |
| `RESTIME=ID` | Reset (or with a leading `-`, disable) the `WITHIN` and `WAIT` timers of the named trigger(s); `ID.n` picks the nth entry. |
| `ANNOUNCE="text"` | A message shown to the character when the change fires. |

The final field is a **target mask**: only targets who pass it count. `MURDER;
OPPOSITE;100%;OUTSIDER;-RACE +Elf` changes standing when an elf is killed.

A character gets the faction value only if the character holds the faction.
A change to a holder that lacks the faction adds it, starting at the amount of
the change (not at `DEFAULT` plus the change); use `AUTODEFAULTS`, `ADDOUTSIDER`
or `MPFACTION` to give the starting value on purpose.

### Abilities, effects and reactions

**`ABILITYn = what ; low ; high`** Marks which skills, spells and so on suit
which band. `what` is an ability ID, an ability type, domain, or flag words
(prefix a flag with `!` to require its absence). The ability is "appropriate"
for the holder only when the holder's value lies between `low` and `high`; put
`!` in front of `low` to invert (`ABILITY6=PALADIN_COMMANDHORSE;!-4499;4500`
means "outside -4499 to 4500"). In play this decides which prayers clerics may
use, which abilities NPCs choose in the Nice/Nasty/Neutral ability behaviors,
and similar gating. Typical use is `alignment.ini`; most worlds do not write
`ABILITY` lines.

**`AFFBEHAVn = ID ; parameters ; mob mask`** Gives NPCs (never characters) in
the faction an ability or behavior while they hold it. One entry per
behavior or ability ID in a faction. Example: `AFFBEHAV1=Aggressive;Message="Die!"
-FACTION +SHUNNED;-RACE +ELF`.

**`REACTIONn = RANGECODE ; mob mask ; ID ; parameters`** Temporary
reactions: an NPC that passes the mob mask and stands in the same room as a
holder whose value is in that band gets the ability, behavior or command for
as long as the holder is there. `<TARGET>` in the parameters is replaced with
the holder's name. This is how a town greets a hero and shuns a villain:

```ini
REACTION1=THIEVES_SHUNNED;-CLAN "+Thieves Guild";Mood;angry
REACTION2=THIEVES_EXALTED;-CLAN "+Thieves Guild";Say;Hail, <TARGET>.
REACTION3=THIEVES_SHUNNED;-CLAN "+Thieves Guild" -CHANCE 50;Aggressive;CHECKLEVEL -NAMES "+<TARGET>"
```


**`PLAYEREFFECTn = Ability ID ; parameters ; band codes or mask`** (CoffeeSUD)
Gives the *character* an effect while the standing is in the stated bands.
The third field is a comma list of range codes (`CULT_TRUSTED,CULT_HONOURED`)
or a mask (starting with `-` or `+`). The effect is a live handler recomputed
from the current band, not a saved effect: it appears when the value enters
the band and disappears when it leaves, and it is never written to the
character's effect list. It can be any ability or property that works in a
property slot, including a scripted `GenAbility` whose script reacts to ticks
and messages, which makes it the hook for "while Hated by the Varn, bounty
hunters follow you".

```ini
PLAYEREFFECT1=Prop_Adjuster;ATTACK+5;CULT_TRUSTED,CULT_HONOURED
PLAYEREFFECT2=MyScriptedGenAbility;MODE=FAVOURED;-TATTOO +CULT_BLESSING
```

Numbering must be consecutive from `1`; the third field's semicolons are not
split further, so keep masks free of semicolons.

### Who holds a faction

- **Characters**: through `AUTODEFAULTS` when the faction is loaded, through the
  creation choice (`AUTOCHOICES`), `ADDOUTSIDER`, an event that adjusts a
  faction they lack (the first change adds it), or `MPFACTION`. Values are
  saved with the character. Auto-awards and masks that test a faction are
  rechecked when a band is crossed during play.
- **NPCs**: through `AUTODEFAULTS` at creation (if loaded), or an explicit value
  written in the area file: `<FACTIONS><FCTN ID="THIEVES.INI">500</FCTN></FACTIONS>`
  on the mob. The editors do it for you: `MODIFY MOB name` has a Factions line
  ("Enter a faction name to add or remove", then the amount). `AFFBEHAV`
  lines apply only to NPCs.
- **Cross-faction rules** (`RELATION`, `FACTOR`) are applied when a change
  fires.

A value is a plain integer. A character without the faction has no value at
all; masks and functions treat that as "no standing" (`FACTION()` returns empty).

### Automatic reaction factions

`AUTOREACTION` (a comma list in `coffeemud.ini` or `world.ini`) switches on
families of generated factions, each built from a template in
`resources/examples/` with `<NAME>`, `<CODE>`, `<FACTIONID>` and `<CLASSID>`
filled in. A generated faction can be overridden by a file of the right name.
They exist to make mobs react to the player's history without writing a file
per thing.

| Type | Faction ID | Template | Override file |
| --- | --- | --- | --- |
| `AREA` | `AREA_<AREA NAME>` | `examples/areareaction.ini` | `factions/<area_name>.ini` |
| `CLAN` | `CLAN_<CODE>` for each `NPCRUN` clan | `examples/clanreaction.ini` | `factions/clanreaction_<code>.ini` |
| `NAME` | `NAME_<MOB NAME>` | `examples/namereaction.ini` | `factions/<mob_name>.ini` |
| `RACE` | `RACE_<RACE>` | `examples/racereaction.ini` | `factions/<race>.ini` |
| `DEITY` | `DEITY_<DEITY>` | `examples/deityreaction.ini` | `factions/<deity>.ini` |
| `PLANAR` | `PLANE_<PLANE>` | factions defined by the planes of existence | none |

The shipped `coffeemud.ini` sets `AUTOREACTION=PLANAR`. These systems cost CPU
and memory in proportion to what is in play; enable only what you use.

**Clan standing (CoffeeSUD).** With `CLAN` in `AUTOREACTION`, every `NPCRUN`
clan has a faction `CLAN_<CODE>` (`<CODE>` is the clan name upper-cased, other
characters `_`) from `clanreaction.ini`. Its bands are Shunned, Distrusted,
Neutral, Trusted, Honoured and Exalted, with codes `<CODE>_SHUNNED` ...
`<CODE>_EXALTED`. By default killing a member lowers standing, giving a member at least 10
coins raises it a little, and giving a member an item raises it a little less;
members react by band (`Mood` changes). Allied
organisations receive half of a change and organisations at war receive the
inverse half; changes in diplomacy update these links. `CREATE GUILD` creates
this faction even without `CLAN` in `AUTOREACTION` but characters only receive
it automatically with `CLAN` listed, so list it (for example
`AUTOREACTION=PLANAR,CLAN`).

**Town reputation (CoffeeSUD).** An area's `REPUTATION` setting names a
template (`MODIFY AREA name REPUTATION examples/areareaction.ini`); an area
with a value gets `AREA_<NAME>` from it, and blank or `NONE` means no
reputation even if `AREA` is in `AUTOREACTION`. Bands are Hated, Villain,
Neutral, Liked, Disliked, Hero, Loved with codes `<CODE>_HATED`, `_VILLAIN`,
`_NEUTRAL`, `_LIKED`, `_DISLIKED`, `_HERO`, `_LOVED` where `<CODE>` is the area
name upper-cased with spaces turned into `_` (`RIVERTON_HERO`). The shipped
template lets natives of a town gain from time spent there, friendly
conversation with its `MudChat` mobs and coin gifts, and makes them lose from
kills and arrests there. Copy `areareaction.ini` and edit it for your own bands. A law's
`LENIENCY` key uses the town band to soften or harden sentences ([The law
file](#the-law-file)).

### Scripting factions

| Script item | Form | Notes |
| --- | --- | --- |
| `MPFACTION` | `MPFACTION $n THIEVES.INI 50`; `MPFACTION $n THIEVES.INI +50`; `MPFACTION $n THIEVES.INI --50`; `MPFACTION $n THIEVES.INI TRUSTED` | A number sets the value; `+n` adds, `--n` subtracts; a band name or code (`TRUSTED`, `THIEVES_TRUSTED`) sets the middle of the band of that faction. Tells the character what changed. |
| `FACTION()` condition | `if faction($n THIEVES.INI > 500)`; `if faction($n THIEVES.INI == Trusted)` | A number compares the value; otherwise the band's display name. |
| `$%FACTION(who id)%` | band display name | Empty when the mob has no standing. |
| `$%FACTIONVALUE(who id)%` | the number | |
| `$%FACTIONBAND(who id)%` | the band code | |
| `FACTION_PROG` | `FACTION_PROG (@GLOBAL) id RANGE/ANY/UP/DOWN (mask)` | Fires when a standing changes or enters a band. See [26-mobprog-triggers.md](26-mobprog-triggers.md). |
| Masks | `-FACTION +CODE`, `+FACTION -CODE` | Range codes, optionally `faction.code`. |

The `id` in the functions may be the file name, the display name or a band code.
Full syntax for the commands and functions is in
[27-mobprog-commands.md](27-mobprog-commands.md) and
[28-mobprog-functions.md](28-mobprog-functions.md).

### Faction tools

| Tool | Use |
| --- | --- |
| `FACTIONS` (`FAC`) | `FACTIONS`: the character's standings. `FACTIONS HERE`: factions relevant to this place. `FACTIONS name`: a faction's band ladder, the current band marked, and points to the next. `FACTIONS PARTY`: a table of every visible faction by party character. Factions whose `SHOWMASK` fails show nothing or the `HIDDENNAME`. |
| `ORGCHECK` | `ORGCHECK (file/CLANS/GOVERNMENTS/ALL)`, in builder mode, DEV mode or with `CMDFACTIONS`. Re-reads faction files line by line and reports a key given twice, band gaps and overlaps, `CHANGE` triggers that match nothing, `REACTION` and `AFFBEHAV` IDs that are not abilities, behaviors, commands or socials, a `REACTION` range that is no band's code, and `RELATION` factions that do not exist. `CLANS` lists world-file mobs naming a missing clan; `GOVERNMENTS` checks entry and exit scripts. |
| `CREATE/MODIFY/DESTROY/LIST FACTION` | Editors, saved to `world/resources/factions/` in builder mode. |
| `AS <character> FACTIONS` | Another party character's standings. |
