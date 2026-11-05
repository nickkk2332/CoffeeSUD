# Factions, organizations, law and services

[Handbook](README.md) · [Organization option reference](../organizations.md)
· [F feature index](feature-index.md#f)

## Standing, membership and rule

Faction standing is a numeric value with named ranges, reactions and change
rules. Clan/organization membership is an affiliation with positions, authority,
dues and diplomacy. Area control belongs to conquest systems. A player can have
high standing with a temple without being a member, or membership in a guild
while wanted by the local law. Model these separately.

Create/edit factions with their generic editor and export them with the world.
Use FACTION_PROG for changes/range crossings and masks for gates. Membership
changes use CLAN_PROG and entry/exit scripts. AUTOREACTION and area reputation
are opt-in policies; an area's REPUTATION setting can select a file or NONE.
Use ORGCHECK to validate cross-references rather than relying on matching prose
names.

## Scaffold a guild

```text
CREATE GUILD Knights of the Rose TEMPLATE=ORDER HALL=Castle#14
```

Substitute a real hall. Templates include GUILD_NPC, ORDER, TEMPLE and CULT.
The scaffold creates an organization, standing faction, guildmaster, trainer,
empty quartermaster and a members' room when a free adjacent exit is available.
It is a starting point: stock the quartermaster, set trainers, replace masks,
check doors and write dialogue before calling it finished.

`SAVE` exports clans, governments and factions in builder mode. The default
scaffold uses NPC administration, merit advancement and five positions. Lower
numeric RANK is more senior; MAX limits seats, including NPC occupants. Replace
placeholder INNERMASK prerequisites with real quests, standing or skills.

## Recruitment and advancement

GuildMaster can handle join, rank, advance, leave, dues and kit dialogue. Its
parameters include CLAN, JOINMASK, JOINFEE, JOINQUEST, RANKS, DUES,
`KIT.position` and KITFEE. Kits refer to catalog item names. Test lack of money,
missing quest, full rank seats and leaving/rejoining.

MERIT promotes to the highest qualifying available position. MERIT_DEMOTE can
also lower rank when requirements cease to hold. SCRIPT with AUTOPROMOTEBY=NONE
leaves advancement to your scripts. Governance, voting rights and authority are
independent of a flattering rank title. Configure who can recruit, expel, spend
funds and declare relations.

CLANCLOCK selects GAME or REAL timing. CLANVOTES selects CHARACTER or PROFILE;
profile voting avoids one player multiplying votes through extra characters.
CLANNEWS chooses INFO, MEMBERS or OFF. Dues define period, grace, late action
and automatic collection. NPCs do not accrue player dues. Test a time jump,
late payment and save/restore around the due date.

`CLANCREATE name PARTY` can create a Fellowship for the party after validation
and charging once. PARTYCLANS shows those affiliations. This differs from an
NPC-run guild and from ordinary group membership.

## Diplomacy and conquest

Script CLANRANK supports named positions. MPCLANRELATION sets mutual diplomatic
relations; MPCONQUER adjusts control through Conquerable. Functions expose
positions, members, clans, relations, area rulers/control and revolt chance.
Masks can test membership/rank or ruler, such as
`-CLANRANK "+Thieves Guild>=CUTPURSE"`. Check names against your definitions.

ClanEnemyAggressive reacts to selected hostile/war relations with its own
chance, level and message rules. Conquest state survives saves. Design who
counts as a defender, how control changes and what happens to services after
capture. Fully autonomous ClanWarfare, deity-specific holy days/favor gates/portents
and uniform-based clan disguise remain deferred; current diplomacy, calendars
and conquest do not imply those integrations.

## Law and crime

An area's legal behavior/law book defines crimes, protected parties,
punishment, arrests, fines and taxation. Warrants, fines and records save with
the campaign. Masks and script functions can inspect wanted status or fines;
law script commands can issue or resolve legal consequences. Use normal law
operations when you want normal immunity, visibility and jurisdiction rules.

```text
LAW TEST ASSAULT AS Alice AGAINST Bob
LAW SET SALESTAX=7.25
```

TEST is a dry run through warrant eligibility: it issues no warrant and does
not advance campaign dice. It uses a fixed diagnostic roll for ignore chances.
An Archon above the law may be refused, so choose representative AS/AGAINST
actors. SET clones a shared law into area-specific custom law before editing;
in builder mode the change exports with the world. Restart to verify the
definition is independent of a prior area's runtime edits.

The generic wanted-poster/bounty-hunter feature remains deferred. Existing
legal officers, warrants and script-based consequences are available.

## Shops, banks and currencies

Shopkeepers need inventory, pricing and opening/service rules. Schedule can
close a shop when the keeper is away. MPSHOP removes, restocks or reprices
stock. PRICECHECK diagnoses a transaction's adjustments; check it with a
normal customer because Archon privileges can bypass restrictions.

Currency files and DEFAULTCURRENCY define world money. Coin material,
weight and descriptions, money formatting and changing exchange rates are
available. Price, denomination and total numeric value are distinct. Bank
accounts and debts can be read/changed from scripts or the JS bank wrapper;
include refusal and insufficient-funds branches.

Economy tools expose authored rules, not a guaranteed simulated market.
Gradual conditional restocking and a persistent supply/demand Market remain
deferred. Use explicit stock/reprice schedules when that is the intended model.
