# Factions and organisations

Clans and guilds run by NPCs, ranks, dues, diplomacy, conquest, faction effects and display, reputation and deities. Part of the [builder capabilities audit](README.md).

<a id="f1"></a>

## F1. Clan rank and member data for a named clan

*Kind:* script function · *Value:* high · *Effort:* small · *Decision:* yes

**Today.** `clan($n IN 'Temple of Arden')` tests membership in any clan, and `islike($n '-CLAN "+Temple of Arden(PRIEST)"')` tests an exact position. But `clanrank()` looks only at the rivalrous or first clan and returns the role index, not seniority. Its function form casts a non-MOB argument to MOB, which throws. Member records (join date, dues owed, gold and XP donated) cannot be read, and nothing lists a character's clans.

**Add.** `clanrank(who 'clan' >= PRIEST)` with an explicit clan, compared by position rank, taking position IDs or numbers. `$%CLANPOS(who clan)%` gives the position ID. `$%CLANMEMBER(who clan JOINDATE|DUESOWED|DONATEDGOLD|DONATEDXP|RANKNAME)%` reads the member record. `$%CLANS(who)%` lists memberships. Fix the cast.

**Example.** The temple doorkeeper says "Welcome, Father" if `clanrank($n 'Temple of Arden' >= PRIEST)`, though the player's first clan is the Thieves Guild. A collector says "You owe `$%CLANMEMBER($n 'Thieves Guild' DUESOWED)%` gold."

*See also:* F4 (Both compare clan positions by rank (clanrank() vs -CLANRANK)); F16 (F16 reads arrears through F1's CLANMEMBER(DUESOWED)).

<!-- from: organizations-factions/org-clan-script-api -->

<a id="f2"></a>

## F2. MPCLANRELATION and clanrelation() for scripted diplomacy

*Kind:* script command, script function, builder tool · *Value:* high · *Effort:* small · *Decision:* yes

**Today.** Clan relations are set only by `CLANDECLARE`. An NPC whose role has DECLARE power (the GANG BOSS) can run it from a MOBPROG, but it sets one side only, needs both clans rivalrous, has a one-mud-month cooldown in real time, and announces only on CLANINFO channels. `mpsetclandata <clan> POLITICS <xml>` replaces the whole politics XML. No script function reads a relation, and `MODIFY CLAN` has no relations editor.

**Add.** `mpclanrelation <clan> <otherclan> <WAR|HOSTILE|NEUTRAL|FRIENDLY|ALLY> [MUTUAL]`, with no cooldown. It calls `setClanRelations` on one side, or both with MUTUAL, and fires CLAN_PROG RELATION. `clanrelation(clanA clanB == WAR)` and `$%CLANRELATION(clanA clanB)%` return the combined state. `MODIFY CLAN` gets a relations list, and `world/clans.cmare` holds the starting relations.

**Example.** After the third raid, a DAY_PROG on the Orc Warlord runs `mpclanrelation 'Orc Horde' 'Riverton Militia' WAR MUTUAL`. The ending of the "Broken Oath" quest makes the Knights of the Rose allies of the Temple.

*See also:* ST9, F3, F9, F10, F16, F22 (later) (These fire ST9's CLAN_PROG events (RELATION, CONQUER, PROMOTE, DUESLATE, DISCOVERED), and ST9 and F10 both make addMember run the entry and exit scripts); F7 (F2 keeps starting relations in F7's world/clans.cmare); F12 (F12 reads the live relation that F2's MPCLANRELATION sets).

<!-- from: organizations-factions/org-clan-relations-cmd -->

<a id="f3"></a>

## F3. MPCONQUER and area ruler functions

*Kind:* script command, script function · *Value:* high · *Effort:* small · *Decision:* yes

**Today.** A script cannot give an area to a clan, change control points or free an area. Control changes only through the clan flag item, kills and revolts, or JavaScript calling `CMLib.law().getLegalBehavior(area).setControlPoints(clan, n)`. Reading the ruler is indirect: `clandata('Orc Horde' AREAS)` lists the areas a clan rules, to be tested with `strcontains`. Control points and revolt chance cannot be read.

**Add.** `mpconquer <area> <clan> <+n|-n|=n|RULE|FREE>` adds or sets control points, makes the clan the ruler outright, or ends its rule (`endClanRule`), firing CLAN_PROG CONQUER or LOSEAREA. The condition `areaclan(area == 'Orc Horde')` and the functions `$%AREACLAN(area)%`, `$%AREACONTROL(area clan)%` and `$%REVOLTCHANCE(area)%` read the state.

**Example.** At the climax of the quest "Hold the Bridge", if the defenders die, `mpconquer Millbrook 'Orc Horde' RULE`. Later, burning the orc supply camp runs `mpconquer Millbrook 'Orc Horde' -200`, and a revolt becomes possible.

*See also:* ST9, F2, F9, F10, F16, F22 (later) (These fire ST9's CLAN_PROG events (RELATION, CONQUER, PROMOTE, DUESLATE, DISCOVERED), and ST9 and F10 both make addMember run the entry and exit scripts); S5 (S5 saves the control points that F3's MPCONQUER changes); F19 (later) (F19 (later)'s raids change area rule, which F3 sets directly from scripts).

<!-- from: organizations-factions/org-conquest-script -->

<a id="f4"></a>

## F4. Mask keys -CLANRANK and -RULER

*Kind:* property, behaviour (mask keys) · *Value:* high · *Effort:* small · *Decision:* yes

**Today.** `-CLAN "+Thieves Guild(SHADOW)"` matches one position, and `-CLAN "+Thieves Guild(<power>)"` any position holding a power. "Cutpurse or better" means listing positions or giving senior ones a marker power, which breaks when ranks are added. `-IF "<script condition>"` can call `clanrank()` or test `clandata(clan AREAS)`, but it is clumsy. No mask key knows who rules the area; `-LOCATION +CLANOWNED` is about property titles, not conquest.

**Add.** `-CLANRANK "+Thieves Guild>=CUTPURSE"` (also <, <=, =, >), compared by position rank. `-RULER "+Orc Horde"` and `+RULER`: the ruling clan of the subject's current area (for rooms and items, their area), with `"+*"` for any ruler. Both work wherever masks do: Prop_ReqEntry, GateGuard, Aggressive, MudChat, REACTION masks, `titles.ini`, shop ignore masks.

**Example.** Orc guards in occupied Millbrook have the Aggressive mask `-RULER "+Orc Horde" -CLAN "+Riverton Militia"`: they attack militia only while the Horde rules. The guild vault's Prop_ReqEntry uses `-CLANRANK "+Thieves Guild>=SHADOW"`.

*See also:* F1 (Both compare clan positions by rank (clanrank() vs -CLANRANK)).

<!-- from: organizations-factions/org-zapper-clanrank-ruler -->

<a id="f5"></a>

## F5. Fix MPFACTION band names and ability faction rules; add FACTIONVALUE

*Kind:* script command, script function · *Value:* high · *Effort:* small · *Decision:* yes

**Today.** Three bugs. `MPFACTION` with a band name searches the alignment faction's bands, whatever faction is named, and only by display name, so `mpfaction $n thieves.ini TRUSTED` fails. CHANGE rules keyed to an ability ID never fire: keys are stored in upper case and looked up with the mixed-case `A.ID()`. This also breaks the stock `alignment.ini` rules CHANGE2 to CHANGE16 (PRAYER_BURY and others). `$%FACTION()%` gives only the band name. Numbers already work: `faction($n thieves.ini > 500)`, `mpfaction $n thieves.ini +50`.

**Add.** `MPFACTION` resolves band names and band codes in the named faction. DefaultFaction looks up ability triggers in upper case. New functions `$%FACTIONVALUE(who id)%` (the number) and `$%FACTIONBAND(who id)%` (the band code).

**Example.** A quest reward runs `mpfaction $n thieves.ini TRUSTED`. A merchants' guild faction with `CHANGE5=Thief_Steal;DOWN;50%;;-CLAN "+Merchants Guild"` now costs a thief standing when caught stealing from guild shops.

*See also:* DT37 (F5 fixes the ability-ID faction CHANGE rules that DT37 reports as matching nothing).

<!-- from: organizations-factions/org-faction-script-fixes -->

<a id="f6"></a>

## F6. Faction band effects on the player, applied live

*Kind:* data file · *Value:* high · *Effort:* medium · *Decision:* yes

**Today.** A faction gives NPCs effects and behaviours by range (AFFBEHAV), but only when `mob.isMonster()`, and only when faction data is refreshed, not when a value crosses a band. A player gets nothing for standing beyond ABILITY gates. Auto-awards with a `-FACTION` mask are checked when assigned, on entering the world, so a band crossed mid-session changes nothing. The workaround is an always-on scripted GenAbility with `EXECMSG_PROG FACTIONCHANGE` running `MPAFFECT` and `MPUNAFFECT`.

**Add.** A faction key `PLAYEREFFECTn = <Ability ID>; <parms>; <range codes or mask>`, also in CMGenEditor. On TYP_FACTIONCHANGE the effect is added when the value enters the range and removed when it leaves. It is not saved, being recomputed. A scripted GenAbility here is the band-change hook. Also re-check auto-award character masks on faction, tattoo and level change.

**Example.** `house_varn.ini`: `PLAYEREFFECT1 = Prop_Adjuster; cha+2; HONORED EXALTED`, and `PLAYEREFFECT2 = Varn_Bounty; ; HATED`, a GenAbility whose script sends bounty hunters in Varn cities.

*See also:* ST14, JS8 (All three need DefaultFaction to report a change and a band crossing beyond the source's room (FACTION_PROG, live band effects, a JS factionChange event)).

<!-- from: data-content/dc-faction-player-effects -->

<a id="f7"></a>

## F7. Clans as world content: world/clans.cmare

*Kind:* data file, builder tool · *Value:* high · *Effort:* medium · *Decision:* yes

**Today.** Clans are not world content. A new game copies only CONTENT_TABLES, which has no CMCLAN, so a clan made with `CREATE CLAN` in builder mode never reaches a normal game. The clan table is also read after the map, so every `<CLAN>` tag on an area-file mob is dropped at boot and ClanHelper logs "Unknown clan". `ROLE=<n>` on the mob tag is a role index only. An ONCE_PROG `mpsetclan` on each NPC is today's stand-in.

**Add.** `world/clans.cmare`, one `<CLAN>` per content clan: name, government, premise, category, accept mask, recall, morgue and donation rooms, level and XP, dues, tax, relations, flags, minimum members. It loads before the areas. Builder mode writes it on SAVE, and `world.sum` covers it. In a restored game the save's CMCLAN wins; clans founded in play stay save state. `ROLE=` on the mob tag and on ClanHelper takes a position ID.

**Example.** The owner builds the Thieves Guild, the Temple of Arden and the Orc Horde, places the guildmaster and cutpurses with `<CLAN ROLE=...>`, and commits the file. Every new game starts with all three, and the cutpurses defend each other.

*See also:* F2 (F2 keeps starting relations in F7's world/clans.cmare); S10, F18 (F7 and S10 both add world/clans.cmare, which F18's CREATE GUILD writes).

<!-- from: organizations-factions/org-clans-as-content -->

<a id="f8"></a>

## F8. Clan timers on game time, NPC members counted

*Kind:* world option · *Value:* high · *Effort:* medium · *Decision:* yes

**Today.** An NPC-only clan can already be kept from fading with minimum members 0: OVERRIDEMINMEMBERS in `clangovernments.xml`, `MODIFY CLAN`, `mpsetclandata 'Orc Horde' MINMEMBERS 0`, or `MINCLANMEMBERS=0` in `coffeemud.ini`. Trophies go off with the NOTROPHY flag. But clan timers use real time (`System.currentTimeMillis`): purge grace, vote expiry, dues, tattoo expiry and the CLANDECLARE cooldown. They ignore pause and turn-based time, and run while the game is closed. Only player member rows count, so NPC members add nothing to size or leadership.

**Add.** A world option `CLANCLOCK=GAME`: daily maintenance, vote expiry, dues periods, purge grace and the declare cooldown counted with GameTime in game days. NPCs with a `<CLAN>` tag count as members for size and leaders.

**Example.** The Orc Horde has no player members and, with minimum members 0, lasts the whole campaign. In turn-based play a vote or a dues month passes after 30 game days, not a real month.

*See also:* F16 (Both move clan dues periods to the game calendar).

<!-- from: organizations-factions/org-npc-clans-game-clock -->

<a id="f9"></a>

## F9. Ranks earned by deeds: AUTOPROMOTEBY MERIT

*Kind:* data file · *Value:* high · *Effort:* medium · *Decision:* yes

**Today.** Auto-promotion ranks members by level, rank, gold, XP or join date, once a real day, filling only seats held by ASSIGN roles. INNERMASK is only a gate, and the daily run tests it on a ThinPlayer, which has no faction or tattoo data. An NPC with ASSIGN power can run `clanassign $n cutpurse`, which checks INNERMASK on the live MOB, but someone must script when.

**Add.** `<AUTOPROMOTEBY>` modes MERIT and MERIT_DEMOTE. A member moves to the most senior position whose INNERMASK they pass, within MAX seats, and with MERIT_DEMOTE drops back on failing it. The check runs on the loaded MOB at faction band, level or tattoo change, quest win, and each game day. The member is told; CLAN_PROG PROMOTE or DEMOTE fires. `CLANDETAILS` shows "Next rank: Shadow, requires: ..." from the mask. `<STANDINGFACTION>thieves.ini</STANDINGFACTION>` shows that faction beside the rank.

**Example.** Footpad on joining. Cutpurse needs `-FACTION +THIEVES_TRUSTED +THIEVES_HONOURED`; Shadow `-FACTION +THIEVES_HONOURED -TATTOO +JOB_WAREHOUSE`; Guildmaster, MAX=1, `-TATTOO +SUCCEEDED_MASTER`. Stealing for the guild raises the standing, and the rank follows.

*See also:* ST9, F2, F3, F10, F16, F22 (later) (These fire ST9's CLAN_PROG events (RELATION, CONQUER, PROMOTE, DUESLATE, DISCOVERED), and ST9 and F10 both make addMember run the entry and exit scripts); F10, F18 (F10 and F18 use F9's MERIT promotion); P25 (Alternatives for ranks earned by deeds: a tattoo rank ladder or clan positions with MERIT promotion).

<!-- from: organizations-factions/org-merit-ranks -->

<a id="f10"></a>

## F10. GuildMaster behaviour for NPC-run guilds

*Kind:* behaviour · *Value:* high · *Effort:* medium · *Decision:* yes

**Today.** The pieces exist, but each guild wires them by hand. `CLANAPPLY` joins at once, with ENTRYSCRIPT, when the clan's apply role is the accept position. The acceptance mask takes `-CLASS`, `-QUESTWIN` and `-IF`. An NPC with ACCEPT, ASSIGN and EXILE power (the GANG BOSS) can run `clanaccept`, `clanassign` and `clanexile` from a SPEECH_PROG. Fees and kits take a BRIBE_PROG and an `mpoload` in ENTRYSCRIPT. There is no kit per rank, and nothing handles each party character separately.

**Add.** A thin GuildMaster behaviour over these commands: `CLAN=`, `JOINMASK=`, `JOINFEE=`, `JOINQUEST=` (a quest to win first), `RANKS=MERIT|SCRIPT` (MERIT needs F9), `KIT.<POSITION>="catalog item names"` (given once per rank, replaced for a fee), `DUES=TRUE` (takes payment of the arrears that the yearly CLANDUES sweep records), `LEAVEMSG=`, `REFUSEMSG=`. It answers "join", "rank" or "advance", "leave", "dues" and "kit". It uses addMember and delMember, which today send nothing (DefaultClan.java:820-836), so it runs the government's ENTRYSCRIPT and EXITSCRIPT itself, as CLANACCEPT and CLANEXILE do, and fires CLAN_PROG once ST9 adds it. It treats each party character on its own.

**Example.** Mother Ysolde in the Hidden Sanctum: "join" needs the Thief class and the "Proof of Nerve" quest, costs 50 gold, makes you a Footpad and hands you a lockpick set. At Shadow, "kit" gives the black hood.

*Re-checked against the code after writing: corrected.*

*See also:* ST9, F2, F3, F9, F16, F22 (later) (These fire ST9's CLAN_PROG events (RELATION, CONQUER, PROMOTE, DUESLATE, DISCOVERED), and ST9 and F10 both make addMember run the entry and exit scripts); F9, F18 (F10 and F18 use F9's MERIT promotion).

<!-- from: organizations-factions/org-guildmaster-behavior -->

<a id="f11"></a>

## F11. Reputation per town, and law that heeds it

*Kind:* world option, data file · *Value:* medium · *Effort:* small · *Decision:* yes

**Today.** A town's reputation can be built as an ordinary faction: a `riverton.ini` in `FACTIONS=`, CHANGE triggers with `MASK=+AREAS Riverton`, REACTION lines, and shop PREJUDICE on its band codes. The automatic kind is all or nothing: `AUTOREACTION=AREA` makes an `AREA_<NAME>` faction for every area, wilderness and dungeons too, from one template. The law ignores reputation; `laws.ini` has no leniency key.

**Add.** An area stat `REPUTATION=<template file|NONE>`, a `MODIFY AREA` field stored in the `.cmare`. Areas with it get their reputation faction from that template; areas without it get none, whatever AUTOREACTION says. A law key `LENIENCY="<range codes>=-1;<range codes>=+1"` moves the punishment down or up by the offender's reputation there. Document PREJUDICE with reputation range codes (`BUY=0.8 RIVERTON_HERO`).

**Example.** Riverton and Ashford use `towns.ini`; the swamp has no reputation. A Riverton hero caught drunk gets a warning instead of parole.

*See also:* F17 (Both generate reputation factions from templates (per town vs per NPC clan)).

<!-- from: world-systems/ws-reputation-optin -->

<a id="f12"></a>

## F12. ClanEnemyAggressive behaviour

*Kind:* behaviour · *Value:* medium · *Effort:* small · *Decision:* yes

**Today.** ClanHelper only defends fellow members. Conquerable makes the ruling clan's NPCs attack members of a clan at WAR with it, reading the live relation, but only in conquerable areas and only for the ruler's NPCs. Elsewhere a builder must mirror war state in a global variable, set by the script that declares war, and test it with `-IF "var(...)"` in an Aggressive mask.

**Add.** `ClanEnemyAggressive [CLAN=<own clan, default the NPC's>] [STATES=WAR,HOSTILE] [CHECKLEVEL] [MESSAGE=...] [CHANCE=n]`. It attacks any MOB, player or NPC, in a clan whose combined relation with its own is in STATES. It reads the live relation matrix (reusing `isAtClanWar` and `getCommonClanRelations`), so `MPCLANRELATION` changes it at once.

**Example.** The Riverton militia carry ClanEnemyAggressive. While the Horde is at WAR, militia and orc raiders fight on sight in the streets. When the player brokers peace, they stop.

*See also:* F2 (F12 reads the live relation that F2's MPCLANRELATION sets).

<!-- from: organizations-factions/org-clan-enemy-aggressive -->

<a id="f13"></a>

## F13. Clan prices and rank-gated shop stock

*Kind:* property · *Value:* medium · *Effort:* small · *Decision:* yes

**Today.** Shop PREJUDICE matches faction band codes, tattoos, class, race, gender and alignment, not clan or rank. A member price works through a tattoo given in ENTRYSCRIPT (`mptattoo $n KNIGHT_OF_ROSE`) and removed in EXITSCRIPT. A CNCLMSG_PROG on the shopkeeper can cancel BUY of one item when `islike()` fails. Nothing hides an item from LIST per buyer, and Prop_HaveZapper on stock does not stop BUY.

**Add.** PREJUDICE tokens `CLAN:<name>` and `CLANPOS:<name>:<position>`, or a `MASK(<zapper>)` part, as in `BUY=0.8 CLAN:Knights_of_the_Rose`. A Prop_StockZapper for stock items, `MASK="<zapper>" HIDE=TRUE|FALSE MSG="..."`: it hides the item from LIST for buyers who fail the mask, or lists it marked restricted, and refuses BUY.

**Example.** The Knights' armourer sells Rose Plate only to Knights (`-CLANRANK "+Knights of the Rose>=KNIGHT"`) and gives every member 20% off. The fence shows its poisons only to Shadows.

<!-- from: organizations-factions/org-shop-rank-stock -->

<a id="f14"></a>

## F14. Hidden factions, band ladder and party standings table

*Kind:* presentation · *Value:* medium · *Effort:* small · *Decision:* yes

**Today.** Whether a faction shows is one switch for everyone (SHOWINFACTIONSCMD, SCOREDISPLAY), so a secret order's standing is always shown or never. `FACTIONS <name>` and `FACTIONS HERE` filter the list but show only the current line, not the ladder. `AS <character> FACTIONS` shows one party character at a time, and `PARTY` has no faction view. A script can name a hidden standing with `$%FACTION($n cult.ini)%`.

**Add.** Faction keys `SHOWMASK=<zapper>` (shown in FACTIONS and SCORE only to holders who pass it) and `HIDDENNAME="an unknown standing"`. `FACTIONS <name>` shows the band ladder, the current band marked, and the points to the next. `FACTIONS PARTY` shows a table: a row per visible faction, a column per party character, band names in the cells.

**Example.** Standing with the Cult of the Black Sun is tracked from the start but appears only once the character has the tattoo READ_CULT_LETTER. `FACTIONS PARTY` shows the paladin Hated by the Thieves Guild and the rogue Trusted.

*See also:* N15 (F14's SHOWMASK and HIDDENNAME would hide a one-NPC faction, half of why N15 proposes Prop_Affinity instead of a faction).

<!-- from: organizations-factions/org-faction-visibility-party -->

<a id="f15"></a>

## F15. Clan news reaches the player

*Kind:* world option · *Value:* medium · *Effort:* small · *Decision:* yes

**Today.** Clan announcements go to channels flagged CLANINFO. This fork keeps INFO (flagged CONQUESTS) and QUESTCHAT, so promotions, level-ups, fading warnings, dues and trophy messages reach no one. Adding CLANINFO to the INFO line of `CHANNELS=` in `coffeemud.ini` fixes most of it without Java. Only auto-promotion and accept tell the member directly, and only when the member has a session.

**Add.** A world option `CLANNEWS=INFO|MEMBERS|OFF`. INFO sends clan news to the INFO channel. MEMBERS tells it directly to member characters, in their own windows. Either way the affected member always gets a personal line: "You are now a Cutpurse of the Thieves Guild."

**Example.** When MERIT promotion makes the rogue a Shadow, the rogue's window shows it, and INFO says "The Thieves Guild has risen to level 3."

<!-- from: organizations-factions/org-clan-news -->

<a id="f16"></a>

## F16. Dues on the game calendar, with consequences

*Kind:* data file · *Value:* medium · *Effort:* medium · *Decision:* yes

**Today.** Clan dues (`MODIFY CLAN` "Clan Dues/yr", `mpsetclandata <clan> DUES n`) are taken once a mud year on real time, need a clan bank account, and come silently from coins, then the bank. `CLANDUES` lists members behind, but arrears have no consequence. A collector can be scripted: a DAY_PROG counts periods with `mpsetvar`, takes coin with `mpmoney`, and an NPC with ASSIGN and EXILE power runs `clanassign` or `clanexile`.

**Add.** Government and clan keys: `DUESPERIOD=DAY|WEEK|MONTH|YEAR` on the game calendar, `DUESGRACE=<periods>`, `DUESLATE="WARN,DEMOTE,EXILE"` (a step per missed period), `DUESAUTO=TRUE|FALSE`. With DUESAUTO false, dues are paid with `CLANDUES PAY [clan]` or to the GuildMaster. `CLANMEMBER(DUESOWED)` reads arrears, and CLAN_PROG DUESLATE fires. The clan bank becomes optional; the money goes to clan XP or a treasury stat.

**Example.** The Merchants' Guild charges 20 gold a game month. After two missed months the member discount ends (demotion). After four the member is expelled, and the guild seal crumbles in EXITSCRIPT.

*See also:* ST9, F2, F3, F9, F10, F22 (later) (These fire ST9's CLAN_PROG events (RELATION, CONQUER, PROMOTE, DUESLATE, DISCOVERED), and ST9 and F10 both make addMember run the entry and exit scripts); F1 (F16 reads arrears through F1's CLANMEMBER(DUESOWED)); F8 (Both move clan dues periods to the game calendar).

<!-- from: organizations-factions/org-dues-with-consequences -->

<a id="f17"></a>

## F17. Clan standing generated from each NPC organization

*Kind:* world option and data file · *Value:* medium · *Effort:* medium · *Decision:* yes

**Today.** `AUTOREACTION` builds reaction factions for `AREA`, `NAME`, `RACE`, `PLANAR` and `DEITY` from template files, with per-name override files. It has no clan type, and this fork's `coffeemud.ini` sets `AUTOREACTION=PLANAR` only. A builder can hand-write one faction per guild, with `REACTION` masks `-CLAN "+Thieves Guild"` and `KILL` and `BRIBE` rules masked to members. Each file must then be kept in step with its clan by hand. This complements the per-area town reputation.

**Add.** A `CLAN` type for `AUTOREACTION`. For each clan flagged `NPCRUN` it builds a faction `CLAN_<CODE>` from `resources/examples/clanreaction.ini`, where `<NAME>` is the clan name. Defaults: `REACTION` masks `-CLAN "+<NAME>"`, so every member reacts by band. Killing a member lowers standing, and a bribe or gift raises it. `RELATION` entries come from the clan's relations (ally +50%, war -50%). A file `clanreaction_<code>.ini` overrides the template for one clan.

**Example.** Kill a Thieves Guild cutpurse and the whole guild in the city turns cold to you. The Watch, at war with the guild, warms to you a little.

*See also:* F11 (Both generate reputation factions from templates (per town vs per NPC clan)).

<!-- from: organizations-factions/org-clan-reaction-factions -->

<a id="f18"></a>

## F18. CREATE GUILD generator and single-player governments

*Kind:* generator and data file · *Value:* medium · *Effort:* medium · *Decision:* yes

**Today.** An NPC guild needs a government, a clan, a standing faction, a scripted guildmaster, entry rules for the hall, a trainer and a shop, all wired by hand. Each piece is editable data (`CREATE GOVERNMENT`, `MODIFY GOVERNMENT`, `CREATE CLAN`, `CREATE FACTION`). But the stock governments are built for player politics: voting, applicants, trophies. Only `GANG` works without voting.

**Add.** Ship `GUILD_NPC`, `ORDER`, `TEMPLE` and `CULT` in `clangovernments.xml`: no voting, `MERIT` promotion, `NPCRUN` defaults, and four or five named ranks with placeholder `INNERMASK`s. Add a builder command `CREATE GUILD <name> TEMPLATE=<government> HALL=<room>`. It makes the clan in `world/clans.cmare` and a standing faction (Shunned to Exalted, with `KILL` and `BRIBE` rules masked to members). It also places a guildmaster in the hall, `Prop_ReqEntry` on the inner rooms, and a stub trainer and shop. It needs clans kept as world content. It uses `GuildMaster` and `-CLANRANK` if those are approved.

**Example.** `CREATE GUILD Knights of the Rose TEMPLATE=ORDER HALL=Castle#14` gives an order the player can join, rise in and be thrown out of. The owner then writes its quests and kit.

*See also:* F7, S10 (F7 and S10 both add world/clans.cmare, which F18's CREATE GUILD writes); F9, F10 (F10 and F18 use F9's MERIT promotion).

<!-- from: organizations-factions/org-guild-wizard -->

<a id="f19"></a>

## F19. ClanWarfare behaviour: NPC clans raid and conquer

*Kind:* behaviour and world option · *Value:* medium · *Effort:* large · *Decision:* later

Wanted, but out of the current scope: moved to [`LATER.md`](../LATER.md#f19).

<a id="f20"></a>

## F20. Party-founded clans: the profile votes once

*Kind:* world option and command · *Value:* low · *Effort:* small · *Decision:* yes

**Today.** Each party character is a separate member and voter. Under a voting government such as `FELLOWSHIP`, every `MUST_VOTE_ON` action waits until the player has used `CLANVOTE` with each character, or until the vote's MAXDAYS (100 game days for FELLOWSHIP) run out with the quorum (33%, at least 2 votes) cast. One character founds the clan alone and pays `CLANCOST` once (10000 gold in `coffeemud.ini`, which can be lowered); each of the others must `CLANAPPLY` and be accepted. Voting can be avoided. `GANG`'s `BOSS` holds every power as `CAN_DO`, and the leader can `clanaccept` the others after `AS <character> clanapply`.

**Add.** World option `CLANVOTES=PROFILE`: all characters of the profile count as one voter, so actions that need a vote run at once. `CLANCREATE <name> PARTY` founds the clan with every party character joining together and pays `CLANCOST` once. `PARTY CLANS` lists each character's clans and ranks.

**Example.** The party founds The Gray Company under `FELLOWSHIP` and buys a hall with `Prop_RoomForSale`. They set it as the clan home and declare war on the bandits without voting with themselves.

*Re-checked against the code after writing: corrected.*

<!-- from: organizations-factions/org-party-clan -->

<a id="f21"></a>

## F21. Deity holy days, favor gates and portents

*Kind:* property · *Value:* low · *Effort:* medium · *Decision:* later

Wanted, but out of the current scope: moved to [`LATER.md`](../LATER.md#f21).

<a id="f22"></a>

## F22. Prop_ClanDisguise: pass as a member in uniform

*Kind:* property and script trigger · *Value:* low · *Effort:* medium · *Decision:* later

Wanted, but out of the current scope: moved to [`LATER.md`](../LATER.md#f22).
