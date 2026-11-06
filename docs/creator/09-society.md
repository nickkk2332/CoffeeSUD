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
