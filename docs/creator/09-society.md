# Society: clans, factions, organisations, law and economy

[Handbook](README.md) · [Organization option reference](../organizations.md)
· [F feature index](feature-index.md#f-factions-and-organizations)

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

- [Standing, membership and rule](#standing-membership-and-rule)
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
  - [Diplomacy and ClanEnemyAggressive](#diplomacy-and-clanenemyaggressive)
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
  - [Shops and shopkeeper pricing](#shops-and-shopkeeper-pricing)
  - [Banks and debts](#banks-and-debts)
  - [Money changers and exchange rates](#money-changers-and-exchange-rates)
  - [Economy scripting](#economy-scripting)
- [Worked examples](#worked-examples)
- [Persistence notes](#persistence-notes)
- [Troubleshooting and gotchas](#troubleshooting-and-gotchas)
- [See also](#see-also)

## Standing, membership and rule

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
| `CLANHOMESET`, `CLANDONATESET`, `CLANMORGUESET` | `CLANxxxSET (clan)` | Set the current room as the clan's home, donation room or morgue. The room must be owned by the clan. | `SET_HOME`, `SET_DONATE`; for the morgue, `MORGUE` to pick the clan and `SET_HOME` to do it |
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
conquered areas and so on) are still in the code; their prizes are the
`CLANTROP*` settings.

The shipped comment on `MAXCLANCATS` says `0` means unlimited; in the code a
value of `0` blocks all membership of that category (the join checks test
`size() >= max`). Use a large number instead.

### Party-founded clans

Each party character is a separate member and, under `CLANVOTES=CHARACTER`, a
separate voter. `CLANCREATE <name> PARTY` (single-player games only) founds a `FELLOWSHIP` for the party:
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

Parameters (one parameter line; put `CLAN=` first, because the reader would otherwise mistake the `CLAN` inside a `-CLANRANK` mask for the key):

| Parameter | Default | Meaning |
| --- | --- | --- |
| `CLAN="name"` | required | The organisation. Without it the master says "This guild is not configured." |
| `JOINMASK="mask"` | the clan's basic requirement mask | An extra mask the joiner must pass (the government's `REQUIREDMASK` is checked as well). Use single quotes for a quoted argument inside, as `"-CLANRANK '+Temple of Arden>=ACOLYTE'"`. |
| `JOINFEE=n` | `0` | Gold charged on joining, in the speaker's currency. |
| `JOINQUEST="name"` | none | A quest the speaker must have won. |
| `RANKS=MERIT\|SCRIPT` | `MERIT` | `MERIT` re-checks merit on `join`, `rank` and `advance`. `SCRIPT` leaves advancement to your scripts. |
| `DUES=TRUE\|FALSE` | `TRUE` | Whether saying `dues` collects arrears. |
| `KIT.POSITION="catalog item,catalog item"` | none | Items issued to a member of that position (use the position `ID`, for example `KIT.INITIATE`). Items are names in the world [catalog](22-items.md). **Known fault:** the parameter reader (`CMParms.getParmStr`) treats a key that contains `.` as punctuation and returns the default, so in the current build a `KIT.` parameter is never read and `kit` always answers "There is no kit for your rank." (checked with a direct call; see the audit notes). `KITFEE` is unaffected. |
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

### Diplomacy and ClanEnemyAggressive

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

**Directions** (written in upper case; the first letters are enough): `UP` and `DOWN` change the value by
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
| `$%FACTION(who id)%` | band display name | Empty when the mob has no standing. Here `id` must be the faction ID (file name, with or without `.ini`). |
| `$%FACTIONVALUE(who id)%` | the number | |
| `$%FACTIONBAND(who id)%` | the band code | |
| `FACTION_PROG` | `FACTION_PROG (@GLOBAL) id RANGE/ANY/UP/DOWN (mask)` | Fires when a standing changes or enters a band. See [26-mobprog-triggers.md](26-mobprog-triggers.md). |
| Masks | `-FACTION +CODE`, `+FACTION -CODE` | Range codes, optionally `faction.code`. |

The `id` in the `FACTION()` condition, `$%FACTIONVALUE%` and `$%FACTIONBAND%` may be the file name, the display name or a band code.
Full syntax for the commands and functions is in
[27-mobprog-commands.md](27-mobprog-commands.md) and
[28-mobprog-functions.md](28-mobprog-functions.md).

### Faction tools

| Tool | Use |
| --- | --- |
| `FACTIONS` (`FAC`) | `FACTIONS`: the character's standings. `FACTIONS HERE`: factions relevant to this place. `FACTIONS name` (the faction's exact name): its band ladder, the current band marked, and points to the next; other text filters your standings by band or faction name. `FACTIONS PARTY`: a table of every visible faction by party character. Factions whose `SHOWMASK` fails show nothing or the `HIDDENNAME`. |
| `ORGCHECK` | `ORGCHECK (file/CLANS/GOVERNMENTS/ALL)`, in builder mode, DEV mode or with `CMDFACTIONS`. Re-reads faction files line by line and reports a key given twice, band gaps and overlaps, `CHANGE` triggers that match nothing, `REACTION` and `AFFBEHAV` IDs that are not abilities, behaviors, commands or socials, a `REACTION` range that is no band's code, and `RELATION` factions that do not exist. `CLANS` lists world-file mobs naming a missing clan; `GOVERNMENTS` checks entry and exit scripts. |
| `CREATE/MODIFY/DESTROY/LIST FACTION` | Editors, saved to `world/resources/factions/` in builder mode. |
| `AS <character> FACTIONS` | Another party character's standings. |

## Law and crime

Source: [Arrest.java](../../CoffeeMud/com/planet_ink/coffee_mud/Behaviors/Arrest.java),
[Conquerable.java](../../CoffeeMud/com/planet_ink/coffee_mud/Behaviors/Conquerable.java),
[DefaultLawSet.java](../../CoffeeMud/com/planet_ink/coffee_mud/Common/DefaultLawSet.java),
[LawCmd.java](../../CoffeeMud/com/planet_ink/coffee_mud/Commands/LawCmd.java),
shipped data [laws.ini](../../CoffeeMud/resources/laws.ini).

The law is a behavior on an area. An area with the `Arrest` behavior has a law
book (crimes, sentences, taxes, officers, a judge, jail rooms), and the
engine runs the whole procedure with real NPCs: a witness sees a crime, a
warrant is issued, an officer tracks the criminal, handcuffs them and walks
them to the judge, the judge sentences them, and the prisoner is jailed,
paroled, fined, banished or executed and later released. The law belongs to
an area and its child areas (its "metro area"). An area with no legal
behavior has no law. `Conquerable` is `Arrest` plus clan conquest, so a
conquerable area has law too.

### Setting up a legal system

To give a town a law, using a copy of the shipped laws:

1. Add the behavior to the area: `MODIFY AREA Riverton BEHAVIORS`, and add
   `Arrest`. The parameter chooses the law (see below); empty uses
   `resources/laws.ini`.
2. Put the people in the world. **Officers** are NPCs whose name or
   displayed description contains an `OFFICERS` word *and* that wander: they must
   have the `Mobile` behavior. **The judge** is an NPC whose name or
   description contains a `JUDGE` word and who does *not* wander. An NPC whose
   name has no such word can be named into a role with the ambiance tags
   `#OFFICER` or `#JUDGE`, and `-OFFICER` keeps an NPC out of both roles;
   `AmbianceAdder` attaches them (see
   [23-properties-reference.md](23-properties-reference.md#ambianceadder)). The wander rules
   still apply.
3. Make the **jail** and **release** rooms and name them in `JAIL` and
   `RELEASEROOM` (a room ID, or text in the room's title; a trailing `$`
   anchors the end of the title, so `The Jail$` matches "The Jail" but not "The
   Jailer's Rest"; a leading `$` anchors the start).
4. Run `LAW` in the town. It lists the law in use, the officers and judges it
   found by name, the jail and release rooms, taxes and crime counts, and
   explains why an NPC that looks like an officer or judge does not count.
5. Try a crime with `LAW TEST` and `LAW ACCUSE`, and play one through.

Arrest parameter forms (`Arrest` takes `[law file or custom] (; KEY=value ...)`):

| Parameter | Meaning |
| --- | --- |
| empty | Use `laws.ini`. |
| `laws_riverton.ini` | Use `resources/laws_riverton.ini`, or the world's copy at `world/resources/laws_riverton.ini`. The reference is saved in the area's `BEHAVES`, so every new game has it. |
| `laws.ini;OFFICERS="watchman" JUDGE="magistrate" SALESTAX=5` | A file plus overrides applied after the file loads. Use this for small differences from a shared file. |
| `custom` | The area has its own editable law, kept in the save, and written to `world/laws/AREA.ini` in builder mode. Law books and `LAW SET` edit it. |

A `custom` law starts from the compiled default law, which has
`ACTIVATED=FALSE`, no officers (`@`), no judge and almost no messages. It does
nothing until you set the keys. Starting from a copy of `laws.ini` is almost
always easier.

**Where the law is read from**, in builder mode versus play:

- In builder mode the law file `world/laws/AREA.ini` (the file is named like the
  area's `.cmare`) is the law of a `custom` area, and edits to the law book (or
  `LAW SET`) are written back to it. A new game starts from that file.
- In a game the law as saved in the campaign wins. Changes during play (a
  conquest, a law book edit) stay in the save and do not touch the world files.
- Named files (`laws_x.ini`) are ordinary resources: world copy first, then stock.

### The law file

A law file is a Java properties file: `KEY=value` lines, `#` comments. The
shipped [laws.ini](../../CoffeeMud/resources/laws.ini) is the reference and is
heavily commented; copy it. Keys:

| Key | Value | Meaning |
| --- | --- | --- |
| `ACTIVATED` | `TRUE`/`FALSE` | `FALSE` turns the law off. Absent means on in a file; the default custom law sets `FALSE`. |
| `OFFICERS` | words | Name words of officer NPCs (they must be `Mobile`). `@` means none. |
| `JUDGE` | words | Name words of the judge NPC (must not wander). `@` means none. |
| `JAIL` | `room;room;...` | Jail rooms: IDs or title text. `@` means the criminal's own room. |
| `RELEASEROOM` | same | Where released prisoners are walked. |
| `TRESPASSROOM` | rooms | Unused by the engine. |
| `PROTECTED` | mask | Who counts as a victim of crime. Others are fair game. The shipped value protects humanoid races. |
| `TRESPASSERS` | mask | Who counts as trespassing (shipped: undead). |
| `ARRESTMOBS` | `true`/`false` | Whether NPCs can commit crimes (except nudity and armed citizen). |
| `WARNINGMSG`, `THREATMSG` | text | What an officer says for those sentences. |
| `JAIL1MSG`..`JAIL4MSG`, `PAROLE1MSG`..`PAROLE4MSG`, `PAROLEDISMISS`, `EXECUTEMSG`, `PREVOFFMSG`, `LAWFREE` | text | The judge's lines. |
| `CHITCHAT`, `CHITCHAT2`, `CHITCHAT3`, `CHITCHAT4` | quoted phrases | What the officer says when delivering to the judge, jail, detention and banishment. |
| `RESISTWARNMSG`, `NORESISTMSG`, `RESISTFIGHTMSG`, `COPKILLERMSG`, `RESISTMSG` | text | Arrest-resistance lines. |
| `JAIL1TIME`..`JAIL4TIME` | ticks (4 seconds) | Jail time. Shipped 20, 40, 80, 160. |
| `PAROLE1TIME`..`PAROLE4TIME` | ticks | Parole time. Shipped 40, 80, 160, 320. |
| `TREASURY` | `roomID;container` | Where tax money goes; `*` as the room with a container that may move. |
| `PROPERTYTAX` | number | Share of a property's price charged each game month, paid to the treasury (see the comment in `laws.ini` for how small and large values are read). Shipped `0.0025`. |
| `SALESTAX` | percent | Added to the price in shops under this law. |
| `CITTAX` | percent | Taken by a `TaxCollector` from the money a citizen carries. |
| `LENIENCY` | `RANGECODES=n;RANGECODES=n` | CoffeeSUD: shifts the sentence by town reputation. `RIVERTON_HERO=-1;RIVERTON_VILLAIN=1` makes heroes one step lighter and villains one step heavier (keeping modifier flags). Needs the area's `REPUTATION` faction. |

**Crime lines.** The core crimes (`RESISTINGARREST`, `TRESPASSING`, `NUDITY`,
`ARMED`, `ASSAULT`, `MURDER`, `PROPERTYROB`, `TAXEVASION`, `PRISONBREAK`) and
the skill, social and contraband crimes use one form:

```text
KEY=PLACES ; FLAGS ; crime name ; SENTENCE ; message to the criminal
ASSAULT=;;assaulting <T-NAME>;jail1;Assault is a hideous offense.
```

`<T-NAME>` in the name is replaced by the victim's name. The fields:

| Field | Values |
| --- | --- |
| `PLACES` | Space separated: `home`, `!home`, `indoors`, `!indoors`, or a room keyword (a word found in the room's title or description: `pub`, `inn`), each negatable with `!`. All must hold. |
| `FLAGS` | `witness`, `!witness`, `combat`, `!combat`, `recently`, `!recently`, `ignore50`, `ignore75`, `ignore90`, `ignore95`, `ignore99` (that percent chance the act is overlooked), `!ignore75` through `!ignore99` (the complement: 25, 10, 5, 1 percent). |
| `SENTENCE` | `warning`, `threat`, `parole1`-`parole4`, `jail1`-`jail4`, `death`, optionally followed by modifiers. |

Flag meanings: `witness` requires a witness in the room; `!witness` waives the
need for one; `combat` and `!combat` require the act to happen in or out of
combat; `recently` and `!recently` require that the criminal has or has not
been caught for it recently. **A crime needs a witness unless it is flagged
`!witness`.** The witness must be an NPC with intelligence above 3 in the criminal's room
or an adjacent one. An officer always notices; any other NPC notices with a
chance that depends on the criminal's inclination (about 76 percent for a
chaotic criminal, 51 for a neutral one and 6 for a lawful one), so repeat
a test before deciding that a crime is not witnessed. The case dies if
the witness dies or is a friend of the accused, and a witness who cannot see
the criminal makes no case. Characters are never witnesses.

Sentence modifiers, space separated after the punishment: `separate` (do not
escalate with other offences), `skiptrial` (the officer punishes at once, no
judge), `detain=ROOMID,TICKS` (hold in a detention room), `fine=AMOUNT` (also
fine), `norelease` (do not release from prison), `punishcap=PUNISHMENT` (the
most severe sentence for a repeat offender), `shame=DAYS` (public shaming for
game days), `banish=DAYS[,ROOMID]` (banish for game days).

Repeat offences escalate the sentence step by step (offences of different
crimes escalate each other unless `separate`), a good-aligned criminal gets one
step back, and a victim who is a known trouble-maker halves the sentence. `punishcap` interacts with
escalation in surprising ways (a capped crime may start at its cap); test
with `LAW TEST` before trusting a sentence ladder.

**Skill crimes.** A key naming an ability ID, an ability type or a domain
makes using it a crime: `THIEF_STEAL=;;robbing <T-NAME>;jail3;...`,
`PRAYER_MALIGNEDPORTAL`, `POISON_ALCOHOL`, and types such as `THIEF_SKILL`,
`SPELL`, `ILLUSION`. Keys beginning with `$` make *having a status effect* a
crime instead.

**Social and emote crimes.** `CRIMEn=emote strings ; places ; flags ; name ;
sentence ; message`. The first field is a space-delimited list of strings
that appear in an emote or social message; quote phrases:
`CRIME1=spit(s) spits;;witness;spitting in city limits;warning punishcap=jail1;Spitting is a dirty habit.`

**Contraband.** `BANNEDn=names ; places ; flags ; name ; sentence ; message`.
The first field lists item names (or resource names) that are illegal to
carry; quote phrases. `BANNED1=roofie;;witness;drug possession;parole3;Drugs are bad.`

### Arrest, trial and punishment

1. **Accusation.** A warrant is issued for a crime the code notices: a killing
   (`MURDER`), nudity, carrying a weapon openly, trespass, banned items, using a
   listed skill or having a listed effect, a listed emote, robbery of a
   property owner, or a script (`MPACCUSE`, the `Prop_ReqEntry` zap event) or
   `LAW ACCUSE`. Officers, the judge and anyone above the law (the `ABOVELAW`
   permission) are exempt.
2. **Seeking and arrest.** An officer finds the criminal ("You are under
   arrest"), and if the criminal resists, subdues them (sap, trip, attack).
   Prisoners are handcuffed (`Skill_HandCuff`).
3. **Trial.** The officer takes the prisoner to the judge, who pronounces the
   sentence unless the sentence is `skiptrial`. `warning` and `threat` are
   spoken by the judge or officer and end the case. Sentences that need a
   judge (parole, jail, death) do nothing if the area has none.
4. **Punishment.** `parole`: the character may not leave the area, use recall
   and the like, or flee combat, for the parole time. `jail`: they are walked
   to a jail room and held for the jail time. `death`: executed. Fines are
   recorded as owed; a `TaxCollector` collects them. `detain`, `shame` and
   `banish` run their timed effects.
5. **Release.** When the time is up the officers walk the prisoner to the
   release room and the warrants that jailed them are closed.

A case also ends when the witness dies or is a friend of the accused, and old
warrants expire after a fixed period (`LegalBehavior.EXPIRATION_MILLIS`, seven
days). Warrants, fines and records are saved with the campaign. Pardoning
everyone at once with the prayer `Prayer_Forgive` still works.

Related behaviors: `TaxCollector` (collects citizen tax and fines;
`WAIT=ms GRACE=ms`), `GateGuard`, `BribeGateGuard` and `DoorwayGuardian` (door
and gate keepers), `Guard` and `GoodGuardian` (defend victims). They are
described in [24-behaviors-and-masks.md](24-behaviors-and-masks.md).

### Law tools

`LAW` works in builder mode, DEV mode, or with `ABOVELAW`.

| Command | Meaning |
| --- | --- |
| `LAW` | The law in force here: behavior and parameter, officers and judges found (and why a named NPC does not qualify: "does not wander", "kept from officialdom"), jail and release rooms (and "FOUND NOWHERE"), taxes, crime counts and warrant count. |
| `LAW CRIMES (text)` | List the crimes with name, sentence, places and flags; `text` filters. |
| `LAW WARRANTS (name)` | List warrants (criminal, crime, victim, offence count, state). |
| `LAW ACCUSE name crime` | Make a warrant as a witness would, by crime key. |
| `LAW PARDON name (crime/ALL)` | Take warrants away. |
| `LAW TEST crime (AS name) (AGAINST name)` | A dry run: says whether a warrant would be issued for the crime by that actor against that victim, and why not. It issues nothing and advances no dice; "ignore" chances use a fixed diagnostic roll. `AS` and `AGAINST` name a person in the room, a player or a party character. An actor above the law is refused, so choose representative characters. |
| `LAW SET KEY=value` | Set one key in this area's law. If the area's law is not already `custom`, the current law is copied into a `custom` law first (the file reference is replaced by `custom`); then the key is set. In builder mode the change is written to `world/laws/AREA.ini`. |

```text
LAW TEST ASSAULT AS Alice AGAINST Bob
LAW SET SALESTAX=7.25
```

`LAW SET` takes one value without line breaks or `~`.

### Law and scripts

| Script item | Meaning |
| --- | --- |
| `MPACCUSE who victim-or-'' crime` | File a warrant under the law of the host's room. `crime` is a crime key, or a complete five-field crime. |
| `ISWANTED(who (crime))`, `WARRANTS(who)`, `FINESOWED(who)` | Condition and values about warrants and fines under the law where the script is. |
| `MPPARDON who (crime/ALL)` | Take warrants away quietly. |
| `MPFINE who +n / -n / n` | Add to, take from or set the fines owed (base value). |
| `MPRELEASE who` | A prisoner's time is up: guards walk them to the release room. |
| `MPSUPPRESSLAW crime/ALL n TICKS/HOURS/DAYS/WEEKS/MONTHS` or `OFF` | Put a crime aside for a while on the game clock; kept in the save. |
| Masks `+WANTED (crime)`, `-WANTED (crime)`, `+OFFICER`, `-OFFICER`, `+JUDGE`, `-JUDGE` | `+WANTED` requires being wanted where the subject is; `-WANTED` the opposite. Usable in shop `IGNOREMASK`, `GateGuard`, `Prop_ReqEntry`, `<VARIES>` text. |
| JavaScript `cm.law(room)` | `wanted(who (crime))`, `finesOwed(who)`, `addFine(who, n)`, `pardon(who)`, `suppress(crime, duration)`. See [05-javascript.md](05-javascript.md). |
| Quest `SET AREA ... GIVE STAT SUPPRESSLAW` | Suppresses a law while a quest runs. |

Examples: a corrupt magistrate, `BRIBE_PROG 500` running `MPPARDON $n ALL` if
`ISWANTED($n)`; a shop whose `IGNOREMASK` is `+WANTED` and which refuses
criminals.

## Conquest and area control

Source: [Conquerable.java](../../CoffeeMud/com/planet_ink/coffee_mud/Behaviors/Conquerable.java),
`HELP CONQUEST`, and the clan item classes under
[Items/ClanItems](../../CoffeeMud/com/planet_ink/coffee_mud/Items/ClanItems/).

An area with the `Conquerable` behavior can be ruled by a clan. Every
conquerable area is also a legal area (the behavior extends `Arrest`), but its
law is always the area's own `custom` law and is inactive unless the behavior
says `LAW=TRUE`. A clan whose government has `CONQUEST ENABLED` may take part.

| Parameter | Default | Meaning |
| --- | --- | --- |
| `JOURNAL="name"` | none | A journal that receives conquest events. |
| `LAW=TRUE\|FALSE` | `FALSE` | Whether the ruling clan may maintain law. With it, officers are members of the ruling clan carrying a clan banner and the judge is a member holding a clan gavel, instead of being found by name. |
| `OWNERSHIP=TRUE\|FALSE` | `TRUE` | Whether private property is signed over to the ruling clan. |
| `LOYALTY=pct` | `0` | Percent loyalty residents gain toward the ruling clan. |
| `LEVELDIFF=n` | twice `EXPRATE` | How far below the killer a resident's level may be and still score control points. |

**How conquest works (inherited).** A clan member with the `Clan Crafting`
skill makes a clan flag and drops it in the area; dropping it reports who
rules, how many control points rule needs and the clans' current scores. A
control point is scored when a resident, intelligent, near-level mob is killed,
made to follow a member, or (for theocracies) converted; the award is the mob's
level. Points fade over time. When a clan's points reach the total the clan
rules the area: residents repudiate the former ruler and swear to the new one,
the former ruler's items can later be cleared for experience, and members with
the order power may command the residents. A flag must remain on the ground or
the area reverts. The ruling clan taxes experience earned there. Conquered areas
can revolt unless clan-crafted items keep enough residents happy. A clan may
only attack a ruled area if it is hostile or at war with the ruler.

**Conquest items.** Book of the Laws (edit the area's law), Guardbanner
(makes the holder an officer), Judge's Gavel (the judge), Donation List,
Pamphlet (spread disloyalty), Shop Apron (the holder becomes a shopkeeper),
Membership Card (loyalty), gathering and crafting items (put residents to
work), and others. Whether to use them in a single-player world is a design
choice: they are powerful but tied to player-clan mechanics. Most worlds
control ownership by script instead.

**Script control (CoffeeSUD).** Set and read rule without items:

| Item | Meaning |
| --- | --- |
| `MPCONQUER area clan +n` / `-n` / `=n` | Add to, subtract from or set the clan's control points. Reaching the total gives the clan rule. |
| `MPCONQUER area clan RULE` | The clan rules the area outright (fires `CLAN_PROG CONQUER`). |
| `MPCONQUER area clan FREE` | Ends the clan's rule if it rules (fires `CLAN_PROG LOSEAREA`). |
| `areaclan(area == clan)`, `$%AREACLAN(area)%` | The ruling clan, empty if none. |
| `$%AREACONTROL(area clan)%` | The clan's control points. |
| `$%REVOLTCHANCE(area)%` | The current chance of revolt. |
| Mask `-RULER "+Orc Horde"`, `-RULER +*` | Subject is in territory ruled by that clan, or by any clan. |
| `clandata(clan AREAS)` | The areas a clan rules. |

```text
mpconquer Millbrook 'Orc Horde' RULE
mpconquer Millbrook 'Orc Horde' -200
mpconquer Millbrook 'Orc Horde' FREE
```

Control points and the ruler are saved with the campaign. Only areas with a
`Conquerable` behavior accept these commands. Design questions to answer
before you rely on conquest: which NPCs are the defenders and how do they react
to the new ruler (`-RULER` in an `Aggressive` mask works), what happens to
shops, guards and quest givers after capture, and how a player takes the
area back. Fully autonomous clan raids (`ClanWarfare`) are not implemented.

## Economy

### Currencies

A **currency** is a named set of coin denominations. Items and shops carry
plain numbers as values; the currency decides how a number is shown and paid in
coins. With the standard `GOLD` currency a price of 36 is 36 gold pieces; with
`CROWN` as defined below it is 3 silver shillings. Currency
definitions have this form (values in the lowest denomination, the plural in
parentheses, the short name in brackets after):

```text
CROWN=1 copper penny(s) (cp);12 silver shilling(s) (ss);240 gold crown(s) (gc)
```

Built-in codes: `GOLD` (copper, silver, gold and platinum pieces, valued
`0.01, 0.1, 1.0, 5.0`), `COPPER`, `CREDIT`, `DOLLAR` and `VICTORY`. A
definition that begins with `notrade;` makes coins nobody can spend or change
in shops (`VICTORY=notrade;1 victory point(s) (v)` is the built-in example).

**Where currencies come from (CoffeeSUD).**

| Source | Form |
| --- | --- |
| `world/resources/currencies.ini` (else `resources/currencies.ini`) | One `CODE=definition` line per currency, and `DEFAULT=CODE`. |
| `CURRENCY_CODE=definition` in `coffeemud.ini` or `world/world.ini` | The same, one key per currency (the key is world-allowlisted as the `CURRENCY_*` family). |
| `DEFAULTCURRENCY=CODE` in `coffeemud.ini` or `world.ini` | The currency of every area with none of its own. Blank keeps the gold coins and Archon notes. |
| An area's `CURRENCY` field | A code (`CROWN`) or a full definition (`CROWN=1 copper...`). Areas inherit from their parent area. |

Files are read after the engine ini, and `coffeemud.ini` or `world.ini` values
come after the file. If a code is defined twice differently (in the file and in
an area, say) the first definition wins and the log says so.

Set an area's currency with `MODIFY AREA name CURRENCY CROWN` (the area editor
has a Currency line too). Mobs, shopkeepers and bankers use the currency of
their start room's area.

**Denomination appearance.** A denomination may end in braces, in any currency
definition including an area's:

```text
12 silver shilling(s) (ss) {MATERIAL=SILVER WEIGHT=0.02 DESC="A thin disc stamped with the crowned stag."}
```

| Key | Meaning |
| --- | --- |
| `MATERIAL=name` | What the coins are made of (a resource name). Without it the first material word of the denomination's name is used (note, bill and dollar are paper). |
| `WEIGHT=n` | Weight of one coin. Default: a hundred coins to the pound. |
| `DESC="text"` | The coins' description, shown before "Equal to ...". |

A currency file is plain text; restart the game after editing it. A
coin item is a `GenCoins` or `StdCoins` (fields `NUMCOINS`, `CURRENCY`,
`DENOM`); money in a mob's pockets is its `MONEY`, or `VARMONEY` for an amount
in a named currency (see [21-mobs.md](21-mobs.md) and
[22-items.md](22-items.md)).

### Money handling

| Task | How |
| --- | --- |
| Give or take money by script | `MPMONEY $n +50` or `MPMONEY $n -50`; the target may be a mob (carried coins), a container or a room (coins appear on, or are taken from, the floor). The amount may name a currency; see `MPMONEY` in [27-mobprog-commands.md](27-mobprog-commands.md). |
| Test what someone carries | `if value($n CROWN >= 30)` and `if goldamt($n > 100)`; see [28-mobprog-functions.md](28-mobprog-functions.md). |
| Show an amount | `$%MONEYSTR(36 CROWN LONG)%` gives "3 silver shillings"; add `SHORT` for abbreviations; without a code the host's currency is used. `$%DENOMNAME(CROWN 12 3)%` gives "3 silver shillings" (the denomination worth 12, three of them). In JavaScript `MONEYSTR$(250, 'CROWN', 'LONG')`. |
| Ask a shop's price | `$%SHOPPRICE(shopkeeper item (customer))%` (base value) and `$%PRICE(item shopkeeper)%` (in coin names) for the price the shop asks the script's `$n`. |
| Taxes | `SALESTAX` and `CITTAX` in the law; `TaxCollector` (see [Law and crime](#law-and-crime)). |
| Fines | Owed under the law: `MPFINE`, `FINESOWED()`; collected by a `TaxCollector`. |
| Clan money | A clan's `TREASURY` setting or a clan bank account. |

### Shops and shopkeeper pricing

A shopkeeper is a mob with a shelf, a deal type and pricing rules. Creating and
configuring shopkeepers, bankers and other service mobs (`GenShopkeeper`,
`GenBanker`, `StdAuctioneer`, postmen, trainers) is covered in
[21-mobs.md](21-mobs.md); this section covers the pricing rules that make a
town's economy.

**Pricing settings.** Each is a field on the shopkeeper (`MODIFY MOB name
STAT value`) and on an area (`MODIFY AREA name STAT value`); the value in
force is the shopkeeper's, else its area's, else a parent area's, else the
default in `coffeemud.ini`.

| Setting | Meaning |
| --- | --- |
| `PREJUDICE` | Price multipliers by customer. See below. Ini default blank (no change). |
| `PRICEMASKS` (ini `PRICEFACTORS`) | Multipliers by item: `0.5 -MATERIAL +WOODEN; 2.0 -MATERIAL +MITHRIL` makes wood half price and mithril double. Every entry whose mask matches the item multiplies. |
| `IGNOREMASK` | Customers the shopkeeper (banker, postal clerk) rudely ignores, as a mask (for example `+WANTED`). |
| `BUDGET` | What the shop spends buying from customers per period: `500000 MONTH` (`HOUR`, `DAY`, `WEEK`, `MONTH`, `YEAR` game periods). Default `500000 MONTH`; `0` means it buys nothing. |
| `DEVALRATE` | Percent price drop per copy already in stock: `20 0.2` is 20 percent per copy for ordinary items and 0.2 percent for raw resources. `100` limits stock to one. |
| `INVRESETRATE` | Ticks between full restocks (`0` never; default 64800, about three real days): purchases from players are cleared and default stock restored. Only generic shopkeepers reset. |
| `WHATISELL` | The deal type (what the shop buys and sells: `ANYTHING`, `GENERAL`, `ARMOR`, `WEAPONS`, `INVENTORY ONLY`, `BANKER`, ...). |

The `coffeemud.ini` keys `PREJUDICE`, `PRICEFACTORS`, `IGNOREMASK`, `BUDGET`,
`DEVALUERATE`, `INVRESETRATE` and `AUCTIONRATES` are engine ini settings; they
are not accepted from `world.ini`, so a world sets these per shopkeeper or per
area.

**`PREJUDICE` grammar.** A bare number is a multiplier for everyone. Otherwise
the value is a semicolon list of rules, each starting `BUY=` (what the shop
charges customers) or `SELL=` (what it pays them). The first rule of the
right kind that matches the customer decides; if none matches the multiplier
is 1.

| Rule | Matches |
| --- | --- |
| `BUY=1.5` | Everyone. |
| `BUY=0.8 DWARF HUMAN` | A customer with any of the words: race, race category, gender, class, alignment name, any tattoo name, or the code of the band they are in for any faction (`SMITHS_HONOURED`). Case does not matter. |
| `BUY=0.8 CLAN:Knights_of_the_Rose` | A member of the clan (underscores stand for spaces). |
| `BUY=0.8 CLANPOS:Knights_of_the_Rose:KNIGHT` | A member holding exactly that position. |
| `BUY=0.8 MASK(-CLANRANK "+Knights of the Rose>=KNIGHT")` | A customer who passes the mask. |
| `RANGE=3` or `RANGE=25%` | Not a price: the shop refuses to buy items whose level is more than that far from the area's median level. |

Example: `BUY=0.7 SMITHS_EXALTED;BUY=0.85 SMITHS_HONOURED;BUY=1.5 SMITHS_SHUNNED;SELL=0.8 CLAN:Smiths_Guild`.
Put the strictest rule first, since the first match wins.

**How the price is worked out.** This is the order the engine uses, and
`PRICECHECK` prints exactly these steps:

1. Base price: the shop's own price for the item, else the item's value. Spells
   and skills sold by trainers and casters cost their qualifying level times 100 or 75;
   retainers cost by level.
2. Multiply by the `PREJUDICE` rule, by the shopkeeper's opinion of the customer (the
   NPC affinity `SHOPFACTOR`; see [06-npcs-dialogue.md](06-npcs-dialogue.md)),
   and by every matching price mask.
3. Charisma against wisdom: for a living shopkeeper, the price is scaled by
   `2 - (customer charisma - shopkeeper wisdom) / ((charisma + wisdom) * 0.8)`
   (charisma at least 1, wisdom at least 3): a charming customer pays less.
   Golems and player shops skip this.
4. Sales tax from the law's `SALESTAX`.
5. Rounded to coins; at least 1.
6. Half price on magic items for a customer under a magical aura.

When the shop buys from a customer, the same prejudice (using the `SELL`
rules, with the affinity factor divided out), price masks and charisma step
apply, then a drop for each copy already in stock (`DEVALRATE`), capped at what
the shop itself would sell it for, limited by `BUDGET`; the shop will not buy
stolen goods.

**Gated stock and restricted shops.** A `Prop_StockZapper` on a stock
prototype restricts who can buy it
([23-properties-reference.md](23-properties-reference.md#prop_stockzapper)):

```text
MASK="-CLANRANK '+Knights of the Rose>=KNIGHT'" HIDE=TRUE MSG="Only Knights may buy this armour."
```

`HIDE=TRUE` hides the item from `LIST` for failing customers; `HIDE=FALSE`
lists it as restricted. Either way `BUY` is refused. To turn customers away
altogether use `IGNOREMASK` or `Prop_ReqEntry` on the shop room. A script can
cancel one sale with `CNCLMSG_PROG` on the shopkeeper.

**Stock by script.** `MPSHOP`:

| Form | Meaning |
| --- | --- |
| `MPSHOP keeper REMOVE item (n/ALL)` | Take some (one) or all of a product away. |
| `MPSHOP keeper STOCK item n` | Set how many there are; an item not stocked is copied from one the script can find, or from the catalog. |
| `MPSHOP keeper PRICE item amount` | Fix the price (`-1` restores the usual). |
| `MPSHOP keeper CLEAR` | Empty the shelves. |
| `MPSHOP keeper RESET` | Restore the stock as built (generic shopkeepers only). |
| `MPSHOP keeper BUDGET amount` | Set the budget left. |

`SHOPBUDGET(keeper)` returns the budget left (`-1` for no limit). Gradual,
conditional restocking and a persistent supply-and-demand market are not
implemented: restock with `RESET`/`STOCK` on a timer script if you want that.

**PRICECHECK.** In builder mode, DEV mode or with `CMDAREAS` or `CMDMOBS`:

```text
PRICECHECK [item] (AT [shopkeeper]) (FOR [character]) (SELL)
```

It lists each pricing setting with where it came from (the shopkeeper, its
area, a parent area, the ini, or a running quest that gave it a `PRICEMASKS`)
and then each step of the sum above for that customer; with `SELL` it works
out what the shop pays instead. The item is looked for in the shop's stock,
then the character's and your own inventory, then the room. Without `AT` the
first non-player shopkeeper here is used; without `FOR`, yourself. The
shopkeeper and the customer must be in the room. Always test
with a normal character: an Archon's privileges may bypass restrictions.

### Banks and debts

A banker (`GenBanker`) keeps accounts for a **chain** (`BANKCHAIN`): bankers
with the same chain name share accounts, so a deposit in one town can be
withdrawn in another. Accounts hold coins and items. Customers use `DEPOSIT`,
`WITHDRAW`, `LIST`, `BORROW`, `VIEW` and `BID` as in other shops. Banker fields
are `COININT` (coin interest, percent per real day), `ITEMINT` (item interest, percent per real day),
`LOANINT` (loan interest, percent per game month), `BANKCHAIN`, plus the shop
pricing fields; see [21-mobs.md](21-mobs.md). Clan treasurers use the clan's
own account (`DEPOSIT`/`WITHDRAW` powers).

Scripts and JavaScript can read and change accounts and debts:

| Item | Meaning |
| --- | --- |
| `$%BANKBALANCE(who chain (currency))%` | Their coins at the chain, in base value. |
| `MPBANK who chain +n / -n (reason)` | Pay into (opening the account if needed) or take from the account, with a dated ledger line. A minus that cannot be covered is refused and logged. |
| `$%DEBT(who owedTo)%` | What they owe a bank chain or anyone. |
| `$%DEBT(who owedTo DUE)%` | Game hours until the debt falls due (below 0 when past due). |
| `MPDEBT who owedTo +n / -n (INTEREST pct) (DUE n HOURS/DAYS/WEEKS/MONTHS) (FOR reason)` or `MPDEBT who owedTo CLEAR` | Add to, pay off or forgive a debt, with monthly interest and a due date on the game clock. |
| JavaScript `cm.bank(banker)` | `pay(who, amount, reason)`, `debt(who)`, `lend(who, amount, ...)`, `forgive(who)`. See [05-javascript.md](05-javascript.md). |

Write both the refusal and the insufficient-funds branches of any script that
takes money.

### Money changers and exchange rates

A `MoneyChanger` NPC takes coins of any tradable currency and hands back coins
of its own area's currency, keeping a cut.

| Parameter | Meaning |
| --- | --- |
| `CUT=n` | Percent the changer keeps (default 5). |
| `CURRENCY=n` (for example `GOLD=5 INGITS=10`) | A separate cut for each named currency. When any is given the changer takes only those currencies. |
| `RATE.CURRENCY=+n` or `-n` | CoffeeSUD: a premium (+) or discount (-) percent on what coins of that currency fetch, before the cut (`RATE.DEFAULT` for the default currency). It is not a fee: the changer still takes every currency. |
| `SPACEMAXCUT`, `SPACEMAXDISTANCE` | Distance-scaled cuts for interstellar currencies. |

`MPEXCHANGE CODE|DEFAULT percent` sets what coins of a currency fetch at every
money changer in the world (100 as they are, which ends the adjustment); it is
kept in the save and combines with each changer's own `RATE`. Read it with
`$%EXCHANGE(CODE)%`. Without rates, every currency converts one to one on base
value, so a world with unequal currencies must set rates to be believable.

### Economy scripting

Summary of the economy items scripts can use (details in
[27-mobprog-commands.md](27-mobprog-commands.md) and
[28-mobprog-functions.md](28-mobprog-functions.md)): `MPMONEY`, `VALUE()`,
`GOLDAMT()`, `MONEYSTR()`, `DENOMNAME()`, `PRICE()`, `SHOPPRICE()`,
`SHOPBUDGET()`, `MPSHOP`, `MPBANK`, `MPDEBT`, `BANKBALANCE()`, `DEBT()`,
`MPEXCHANGE`, `EXCHANGE()`; JavaScript `cm.shop(keeper)` (`remove`, `stock`,
`setPrice`, `setBudget`, `budget`, `reset`), `cm.bank(banker)`, `cm.law(room)`.

## Worked examples

### Example 1: A thieves' guild with ranks and dues

Goal: a guild called "Gutter Hand" that thieves join for 50 gold after a
quest, with four ranks earned by standing and deeds, monthly dues with
consequences, a vault only Shadows may enter, and a starting kit.

**1. Make a government with these ranks.** The stock `GUILD_NPC` has fixed rank
names, so give the guild its own government. Copy the stock file into the world
(from the repository root; use your own world folder if it is not
`CoffeeMud/world`):

```sh
mkdir -p CoffeeMud/world/resources
cp CoffeeMud/resources/clangovernments.xml CoffeeMud/world/resources/clangovernments.xml
```

Add this just before `</CLANTYPES>` in the copy. The range codes in the masks
(`GUTTER_HAND_TRUSTED`...) are the ones the clan's standing faction will have
in step 3: the code is the clan name upper-cased with spaces turned to
underscores.

```xml
<CLANTYPE TYPEID=15 NAME="THIEVES" CATEGORY="NPCGUILD">
<SHORTDESC>a thieves' guild whose ranks are earned</SHORTDESC>
<LONGDESC>Footpads become Cutpurses by earning the guild's trust, Cutpurses become Shadows by trust and a job well done, and a Shadow may be named Master.</LONGDESC>
<POSITIONS>
<POSITION ID="FOOTPAD" ROLEID=0 RANK=3 NAME="FOOTPAD" PLURAL="FOOTPADS" MAX=999999 INNERMASK="" PUBLIC="true">
<POWER>LIST_MEMBERS</POWER>
<POWER>CLAN_TITLES</POWER>
<POWER>DEPOSIT</POWER>
</POSITION>
<POSITION ID="CUTPURSE" ROLEID=1 RANK=2 NAME="CUTPURSE" PLURAL="CUTPURSES" MAX=999999 INNERMASK="-FACTION +GUTTER_HAND_TRUSTED +GUTTER_HAND_HONOURED +GUTTER_HAND_EXALTED" PUBLIC="true">
<POWER>LIST_MEMBERS</POWER>
<POWER>CLAN_TITLES</POWER>
<POWER>DEPOSIT</POWER>
</POSITION>
<POSITION ID="SHADOW" ROLEID=2 RANK=1 NAME="SHADOW" PLURAL="SHADOWS" MAX=999999 INNERMASK="-FACTION +GUTTER_HAND_HONOURED +GUTTER_HAND_EXALTED -TATTOO +GUTTER_JOB_WAREHOUSE" PUBLIC="true">
<POWER>LIST_MEMBERS</POWER>
<POWER>CLAN_TITLES</POWER>
<POWER>DEPOSIT</POWER>
</POSITION>
<POSITION ID="MASTER" ROLEID=3 RANK=0 NAME="MASTER" PLURAL="MASTERS" MAX=2 INNERMASK="-TATTOO +GUTTER_MASTER" PUBLIC="true">
<POWER>LIST_MEMBERS</POWER>
<POWER>CLAN_TITLES</POWER>
<POWER>DEPOSIT</POWER>
<POWER>ACCEPT</POWER>
<POWER>ASSIGN</POWER>
<POWER>EXILE</POWER>
<POWER>DECLARE</POWER>
<POWER>TAX</POWER>
<POWER>WITHDRAW</POWER>
<POWER>PREMISE</POWER>
</POSITION>
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
<TITLES><TITLE>*, the @x2</TITLE></TITLES>
<ABILITIES></ABILITIES>
<EFFECTS></EFFECTS>
</CLANTYPE>
```

`MAX=2` on the Master seat leaves room for the player because the NPC
guildmaster created in step 2 takes the top seat (NPC members count against
seats). Restart so the government loads, and check `LIST GOVERNMENTS`.

**2. Scaffold the guild.** In builder mode stand where the guild will be:

```text
CREATE GUILD Gutter Hand TEMPLATE=THIEVES HALL=Riverton#40
```

This creates the clan, the standing faction `CLAN_GUTTER_HAND`, a guildmaster,
a trainer, a quartermaster and a members' room. Add the world setting that
hands every character the standing faction automatically, in
`world/world.ini`:

```ini
AUTOREACTION=PLANAR,CLAN
```

**3. Standing.** The generated faction has the bands Shunned to Exalted. It
drops when a character kills a member and rises when the character gives members
money or gifts. Guild jobs raise it from scripts: in the dialogue or quest
script of the fence, `mpfaction $n CLAN_GUTTER_HAND +60` after a delivery, and
`mptattoo $n GUTTER_JOB_WAREHOUSE` after the warehouse job. When a character
reaches Master, a quest ending grants `mptattoo $n GUTTER_MASTER`. To make the
standing visible from the start, nothing is needed: its `SCOREDISPLAY` and
`SHOWINFACTIONSCMD` are already true.

**4. The guildmaster's terms.** Edit the guildmaster (`MODIFY MOB guildmaster
BEHAVIORS`) so its `GuildMaster` parameters are:

```text
CLAN="Gutter Hand" JOINMASK="-BASECLASS +Thief" JOINFEE=50 JOINQUEST="Proof of Nerve" RANKS=MERIT DUES=TRUE KIT.FOOTPAD="a guild lockpick,a guild cloak" KITFEE=25
```

A thief who has won "Proof of Nerve" can now say `join` to the guildmaster:
it takes 50 gold and makes them a Footpad. The `KIT.FOOTPAD` and `KITFEE`
parameters are meant to hand over the two catalog items (see
[22-items.md](22-items.md)) once, but see the known fault in the
[GuildMaster](#guildmaster) table: in the current build `KIT.` keys are never
read, so the kit is not issued.

**5. Dues.** The government says monthly, one month of grace, and the steps
`WARN,DEMOTE,EXILE`; it does not say how much. Set it on the clan:
`MODIFY CLAN Gutter Hand` and give "Clan Dues/period" `20`. Members now owe 20
gold per game month. They pay by saying `dues` to the guildmaster or with
`CLANDUES PAY` while carrying coins. With one month of grace, a member who has
paid nothing is fine after one month, warned after two, demoted a rank after
three and expelled after four; a member who is behind is also not promoted.

**6. The vault.** On the vault room add the property `Prop_ReqEntry` with
`-CLANRANK "+Gutter Hand>=SHADOW"`. `CREATE GUILD` already gated the members'
room with the Footpad rank.

**7. Test.** As a thief character: `join` (check the refusals first: not a
thief, no quest, no money), `rank` (it says what the next
rank requires), `CLANDETAILS Gutter Hand`, `FACTIONS Gutter Hand standing`,
`mpfaction` up to Honoured and watch the promotion, miss dues across game
months, then `leave` and `join` again. Save and restore around a dues date;
`DUESNEXT` ensures no double billing. Run `ORGCHECK` and `SAVE`.

### Example 2: A town with laws and guards

Goal: Riverton has a watch, a magistrate, a gaol, a 5 percent sales tax, and
sentences softened for the town's heroes.

1. Give the area its reputation and its law. Reputation first
   (`MODIFY AREA` accepts any area field):

   ```text
   MODIFY AREA Riverton REPUTATION examples/areareaction.ini
   ```

   The reputation faction is `AREA_RIVERTON` with band codes
   `RIVERTON_HERO`, `RIVERTON_VILLAIN` and so on.

2. Add the `Arrest` behavior (`MODIFY AREA Riverton BEHAVIORS`, add `Arrest`) with
   this parameter, on one line:

   ```text
   laws.ini;OFFICERS="watchman" JUDGE="magistrate" JAIL="Riverton Gaol$" RELEASEROOM="Riverton Market Square$" SALESTAX=5 LENIENCY="RIVERTON_HERO=-1;RIVERTON_VILLAIN=1"
   ```

3. Place the people. A mob whose name contains "watchman" with the `Mobile`
   behavior (a watchman that never moves is not an officer), and a mob whose
   name contains "magistrate" *without* `Mobile`, in the room where court
   sits. Create rooms titled exactly "Riverton Gaol" and "Riverton Market
   Square".

4. Check it:

   ```text
   LAW
   LAW CRIMES assault
   LAW TEST ASSAULT AS Alice AGAINST Bob
   ```

   `LAW` should report `Officers (watchman): 1 found`, `Judges (magistrate): 1
   found`, a gaol and release room each `is Riverton#nn`, and a `Taxes:` line that starts `salestax 5%` (the other non-empty tax keys of `laws.ini`, `cittax` and `propertytax`, follow it). If an NPC is named as an officer but does not count, `LAW` says why.

5. Play a crime through. A witness must be in the same or an adjacent room
   or no warrant is issued; the watchman always notices. Let a thief steal in view
   of the watchman: a
   `THIEF_STEAL` warrant appears (`LAW WARRANTS`), the watchman hunts the thief,
   handcuffs them and walks them to the magistrate, who sentences them. Lighten
   or toughen the result with the town's standing: a character with the
   `RIVERTON_HERO` band gets jail one step lighter.

6. Tune with `LAW SET`, understanding what it does. `LAW SET
   PROPERTYTAX=0` first copies the live law into a `custom` law and replaces the
   behavior parameter with `custom`; from then on the area has its own law in
   `world/laws/riverton.ini` and the parameter above is no longer consulted.
   Choose one workflow and stay with it: either edit the parameter and file
   (reproducible, good for a team), or `LAW SET` (fast, for tuning).

7. Prices now include the 5 percent tax. `PRICECHECK loaf AT baker` shows the
   step "Sales tax 5%". Close a shop to wanted customers with the shopkeeper
   `IGNOREMASK` `+WANTED`.

Guards at gates are separate from the law: add `GateGuard` or `BribeGateGuard`
to a mob next to a door to open it for those who pass its mask.

### Example 3: A faction with reputation-gated shop prices

Goal: the Riverton smiths like heroes and charge them less, resent thieves, and
sell their best hammer only to those who honour them.

**1. The faction.** `world/resources/factions/smiths.ini`:

```ini
NAME=Riverton smiths' regard
MINIMUM=-1000
MAXIMUM=1000
SCOREDISPLAY=TRUE
SHOWINFACTIONSCMD=TRUE
SPECIALREPORTED=FALSE
EDITALONE=FALSE
DEFAULT=0
AUTODEFAULTS=0
RATEMODIFIER=1.0
EXPERIENCE=NONE
RANGE1=-1000;-501;Shunned;SMITHS_SHUNNED;
RANGE2=-500;-101;Distrusted;SMITHS_DISTRUSTED;
RANGE3=-100;100;Neutral;SMITHS_NEUTRAL;
RANGE4=101;500;Trusted;SMITHS_TRUSTED;
RANGE5=501;800;Honoured;SMITHS_HONOURED;
RANGE6=801;1000;Exalted;SMITHS_EXALTED;
CHANGE1=KILL;DOWN;50%;OUTSIDER JUST100;-NAMES "+Riverton smith"
CHANGE2=GIFT;UP;10%;OUTSIDER JUST100;-NAMES "+Riverton smith"
CHANGE3=Thief_Steal;DOWN;40%;OUTSIDER JUST100;-NAMES "+Riverton smith"
REACTION1=SMITHS_SHUNNED;-NAMES "+Riverton smith";Say;We do not serve your sort, <TARGET>.
REACTION2=SMITHS_EXALTED;-NAMES "+Riverton smith";Say;Welcome back, <TARGET>. The best is yours.
USELIGHTREACTIONS=true
```

The mask `-NAMES "+Riverton smith"` matches mobs by name, so give the smiths that
name. List the file in `FACTIONS` (`FACTIONS=alignment.ini;smiths.ini` in `world.ini`) so
every character starts at 0, then run `ORGCHECK smiths.ini`. Kills, gifts and
thefts only count for characters who hold the faction, which is why
`AUTODEFAULTS` is set.

**2. Rewards from play.** A quest or dialogue gives standing:
`mpfaction $n smiths.ini +150` after delivering iron, or
`mpfaction $n smiths.ini TRUSTED` for a story beat. `FACTIONS Riverton smiths' regard`
(the faction's exact name) shows the ladder and the points to the next band.

**3. Prices.** On each Riverton smith set the prejudice (`MODIFY MOB smith
PREJUDICE ...`), strictest rule first:

```text
BUY=0.7 SMITHS_EXALTED;BUY=0.85 SMITHS_HONOURED;BUY=1.5 SMITHS_DISTRUSTED;BUY=2.0 SMITHS_SHUNNED
```

Exalted customers pay 70 percent, Honoured 85, Distrusted 150, Shunned 200,
everyone else the usual price. The band codes work because they are faction
range codes; customers without the faction match nothing.

**4. A gated item.** Give the masterwork hammer in the smith's stock a
`Prop_StockZapper`:

```text
MASK="-FACTION +SMITHS_HONOURED +SMITHS_EXALTED" HIDE=FALSE MSG="The smith keeps that for those he honours."
```

Everyone sees the hammer in `LIST` marked as restricted; only Honoured and
Exalted customers can buy it.

**5. Test.** Give a test character standing (`MODIFY USER name` has a Factions
line, or run `mpfaction` from a throwaway script), then check each band:

```text
PRICECHECK hammer AT smith FOR Bob
```

The output names the `PREJUDICE` rule that matched and the stock rule; with a
normal character the numbers are what a player would pay.

### Example 4: A two-currency region

Goal: the highlands (Highmark) use silver-and-gold crowns, the river country
(Rivermouth) uses clay leaves and paper notes, and a border changer converts
between them at a believable rate.

**1. Define the currencies.** `world/resources/currencies.ini`:

```ini
DEFAULT=CROWN
CROWN=1 copper penny(s) (cp);12 silver shilling(s) (ss);240 gold crown(s) (gc)
LEAF=1 clay leaf(s) (cl);10 bronze token(s) (bt);100 river note(s) (rn) {DESC="A stiff printed note, edge-stamped with the river sigil."}
```

Highmark and every area with no currency uses `CROWN` (the default). Restart,
then in Rivermouth:

```text
MODIFY AREA Rivermouth CURRENCY LEAF
```

Mobs, shopkeepers and bankers in Rivermouth now count in leaves. Check with a
script or in game: `$%MONEYSTR(120 LEAF LONG)%` is "1 river note, 2 bronze
tokens".

**2. Re-price the shops.** Item values are plain numbers: a sword worth 100 is
100 copper pennies in Highmark and 100 clay leaves (one river note) in
Rivermouth. Choose the numbers you want. Per-item overrides come from
`MPSHOP Marta PRICE 'iron ingot' 40`, and `PRICEMASKS` on the shop or area
rescale families of items.

**3. The changers.** Currencies convert one to one on base value unless rates
say otherwise: one gold crown (240) would fetch 240 leaves, which is an
unbelievably good deal for a trader. Say a crown should fetch about 140 leaves
and a river note about 170 pennies. In Rivermouth, a changer (coins in, leaves
out):

```text
MoneyChanger CUT=5 RATE.CROWN=-42
```

and in Highmark (coins in, pennies out):

```text
MoneyChanger CUT=5 RATE.LEAF=+71
```

A gold crown (240) at the Rivermouth changer is 240 × 0.58 = 139 before the 5
percent cut, so about 132 leaves.

**4. Events.** A flood devalues leaf coins everywhere:
`mpexchange LEAF 70` (coins of the currency fetch 70 percent at every
changer); `mpexchange LEAF 100` ends it. The adjustment is kept in the save.

**5. A currency nobody can spend.** `TRADEBAR=notrade;1 trade bar(s) (tb)` makes
a reward token shops refuse and changers will not take, useful as a quest
coin.

## Persistence notes

| Thing | World files | Campaign save |
| --- | --- | --- |
| NPC clans, clans with no player members | `world/clans.cmare` (builder `SAVE`) | Membership, ranks, dues owed, treasury, relations, experience, votes, `DUESNEXT`. A restored save wins over `clans.cmare`; clans founded in play stay in the save. |
| Governments | `world/resources/clangovernments.xml` (builder mode writes it) | Not saved; governments load from the files. |
| NPC clan membership | `<CLAN ROLE=...>` on the mob in the area file | The live mob state. |
| Factions | `world/resources/factions/*.ini` | Each character's values; NPC values in area files and the save. Generated factions (`AREA_`, `CLAN_`, `NAME_`...) are rebuilt from templates unless a standing faction was saved by `CREATE GUILD`. |
| Faction effects (`PLAYEREFFECT`) | in the faction file | Never saved; recomputed from the current band. |
| Law | `Arrest` parameter in the area's `BEHAVES` in the `.cmare`; `world/resources/laws_*.ini`; `world/laws/AREA.ini` for `custom` | Warrants, fines owed, jail and parole, suppressed crimes, and in play the live custom law (which wins over the file in a game). |
| Conquest | `Conquerable` behavior on the area | Control points, ruling clan, clan items and revolt state (a `<CONQUEST>` block of the law's run state). |
| Currencies | `world/resources/currencies.ini`, `CURRENCY_*` and `DEFAULTCURRENCY` in `world.ini` | Exchange-rate adjustments (`MPEXCHANGE`) in the save header. |
| Shop settings and stock | Shopkeeper mob and area settings in the area file | Stock, budget left and time to next restock. |
| Bank accounts and debts | The banker mob and `BANKCHAIN` | Accounts, ledgers and debts. |

Scripts that change world data from a game (`MPSHOP`, `MPCONQUER`,
`MPCLANRELATION`) change the live game and the save, never the world files; to
change what a new game starts with, change the files. Changing a government
file does not retroactively edit saved clans: they keep their saved settings and
pick up the new government rules on the next load. Test timetable jumps (dues,
merit rechecks each game day, warrant expiry) with a saved game around the
event. See [state-and-persistence.md](state-and-persistence.md) and
[02-world-workflow.md](02-world-workflow.md).

## Troubleshooting and gotchas

| Symptom | Likely cause and fix |
| --- | --- |
| `CREATE GUILD` says "The hall needs a free exit" | Every direction from the hall already has an exit. Free one or choose another hall. |
| The government will not load; log says "Bad ROLEID" or "Missing positions" | `ROLEID`s must be exactly `0` to `n-1` with no repeats, and `AUTOPOSITION` and `ACCEPTPOSITION` must name existing position IDs. `TYPEID`s must be `0` to `n-1` too. |
| Players cannot be promoted by merit | The `INNERMASK` references a tattoo or faction band nothing grants; the position's `MAX` is full (NPC members count); the member is behind on dues. `rank` says what the next rank requires. Check with `MPTATTOO`/`MPFACTION`. |
| `CLANAPPLY` does nothing for an NPC guild | NPC clans refuse direct applications. Join through the `GuildMaster` (say `join`). |
| A war declaration has no effect | A war needs both clans to declare it. Use `MPCLANRELATION a b WAR MUTUAL`. |
| Faction changes never happen | The character does not hold the faction (`FACTIONS` setting, `AUTODEFAULTS`, `ADDOUTSIDER`, `MPFACTION`), or the change needs a target who holds it (`OUTSIDER` flag), or the trigger name matches nothing (`ORGCHECK`). |
| A faction band has no name or the wrong one | Gaps or overlaps in the `RANGE` lines. `MINIMUM`/`MAXIMUM` do not set bounds; the ranges do. |
| `CHANCE=n` on a change seems backwards | As the code reads, a change with `CHANCE` below 100 returns when a percentile roll is under n, so it fires about (100-n) percent of the time. Test with a few runs, or leave `CHANCE` out and use a `MASK` (unverified in play). |
| `AUTOREACTION=CLAN` factions do not appear for the player | `CLAN` must be in `AUTOREACTION`, the clan must be `NPCRUN`, and the character must be in a room (they are attached on entering). |
| Prices ignore my `PREJUDICE` | The value is read as the shopkeeper's, else the area's, else the ini's: the shopkeeper's blank value falls through. Rules are checked in order and the first match wins; a `BUY`/`SELL` prefix is required; faction codes need the customer to hold the faction. Run `PRICECHECK`. |
| `PRICECHECK` shows a different price from what a player sees | Archon privileges (`ABOVELAW`, `ALLPRIVS`) bypass shop restrictions; test with `FOR` a normal character. |
| Nobody is arrested | The area has no law (`LAW` says so), the law is `ACTIVATED=FALSE` (a fresh `custom` law is), no officer qualifies (officers must have `Mobile`; judges must not), the crime needs a witness (add `!witness` or an NPC who can see it), the criminal is above the law, or the crime is suppressed. |
| The judge never sentences | Sentences other than warnings need a judge who does not wander and whose name contains a `JUDGE` word. |
| `LAW SET` changed my law file's behavior | It converts a file-based law into `custom` (see [Example 2](#example-2-a-town-with-laws-and-guards)). |
| A currency edit does nothing | Restart after editing `currencies.ini`; the first definition of a code wins over later ones. |
| Money changer refuses a coin | The coin's currency is `notrade`, or the changer lists specific currencies and the coin's is not among them, or the changer's own currency is `notrade`. |
| `MAXCLANCATS` of `0` blocks every clan | In the code `0` is "no clans of this kind", not "unlimited". Use a large number. |
| Dues never happen | `DUES` on the clan is `0`; set it. Also check `DUESPERIOD` and `CLANCLOCK` (`REAL` ignores the game clock). |

## See also

- [organizations.md](../organizations.md): the shorter option reference for
  organisations that this chapter expands.
- [24-behaviors-and-masks.md](24-behaviors-and-masks.md): `GuildMaster`,
  `ClanHelper`, `ClanEnemyAggressive`, `Arrest`, `Conquerable`, `MoneyChanger`,
  `TaxCollector`, gate guards, and the mask language.
- [23-properties-reference.md](23-properties-reference.md): `Prop_ReqEntry`,
  `Prop_StockZapper`, `Prop_ModFaction`.
- [21-mobs.md](21-mobs.md): shopkeeper, banker, trainer and money fields.
- [22-items.md](22-items.md): coins, catalog items for kits, clan items.
- [04-mobprog.md](04-mobprog.md), [26-mobprog-triggers.md](26-mobprog-triggers.md),
  [27-mobprog-commands.md](27-mobprog-commands.md),
  [28-mobprog-functions.md](28-mobprog-functions.md): `CLAN_PROG`,
  `FACTION_PROG`, `MPFACTION`, `MPSETCLAN`, `MPCLANRELATION`, `MPCONQUER`,
  `MPACCUSE`, `MPSHOP`, `MPBANK`, `MPDEBT`, `MPEXCHANGE` and their functions.
- [05-javascript.md](05-javascript.md): `cm.shop`, `cm.bank`, `cm.law`.
- [03-characters.md](03-characters.md): rank ladders (`world/ranks.ini`).
- [06-npcs-dialogue.md](06-npcs-dialogue.md): NPC affinity and shop factors.
- [08-quests.md](08-quests.md): quest rewards that grant standing and tattoos.
- [10-world-simulation.md](10-world-simulation.md): calendars, game time and `WAIT`.
- [02-world-workflow.md](02-world-workflow.md), [state-and-persistence.md](state-and-persistence.md),
  [world-settings.md](world-settings.md), [tool-reference.md](tool-reference.md),
  [cookbook.md](cookbook.md).
