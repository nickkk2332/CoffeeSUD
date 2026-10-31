# CoffeeSUD: later

Things the owner wants, but that are out of the current development scope.
They are kept here so they are not forgotten. Nothing here is planned: when
the owner brings one back, it moves to `DEVPLAN.md`.

## From the builder capabilities audit

Moved here from the [builder capabilities audit](audit/README.md) on
2026-10-31, in full. The IDs are the audit's; *See also* lines point into
`audit/`.

| ID | Item | Theme | Value | Effort |
|---|---|---|---|---|
| [N13](#n13) | Companion behaviour: NPC party members who take orders and comment | NPC behaviour and dialogue | high | large |
| [N14](#n14) | Dialogue trees: a Dialogue behaviour and .dlg files | NPC behaviour and dialogue | high | large |
| [N23](#n23) | Rumours: a world list, gossiping NPCs, rumours from events | NPC behaviour and dialogue | medium | medium |
| [N25](#n25) | LLM calls off the game thread | NPC behaviour and dialogue | medium | medium |
| [N26](#n26) | LLM NPCs with lore files and saved memory per player | NPC behaviour and dialogue | medium | medium |
| [N27](#n27) | LLM NPCs that act from a declared whitelist | NPC behaviour and dialogue | medium | medium |
| [C24](#c24) | Invasions and raids on an area | Combat and encounters | medium | large |
| [I12](#i12) | PuzzleController: puzzle state, sequences and solve actions | Items, properties and puzzles | high | large |
| [W24](#w24) | Gradual and conditional restocking | World systems (time, weather, environment, economy, law) | medium | medium |
| [W25](#w25) | Exposure: body temperature from climate, shelter and clothing | World systems (time, weather, environment, economy, law) | medium | large |
| [W26](#w26) | Bounty hunters and wanted posters | World systems (time, weather, environment, economy, law) | medium | large |
| [W27](#w27) | Market: supply and demand that remembers and recovers | World systems (time, weather, environment, economy, law) | medium | large |
| [CR22](#cr22) | Linked storage chests for the profile: Prop_SharedStash | Crafting, gathering and housing | low | medium |
| [F19](#f19) | ClanWarfare behaviour: NPC clans raid and conquer | Factions and organisations | medium | large |
| [F21](#f21) | Deity holy days, favor gates and portents | Factions and organisations | low | medium |
| [F22](#f22) | Prop_ClanDisguise: pass as a member in uniform | Factions and organisations | low | medium |
| [P30](#p30) | Training that takes game time | Characters, progression and custom abilities | low | medium |
| [B14](#b14) | CHATDRAFT: LLM drafts of fixed NPC dialogue | Builder tools | low | small |
| [DT39](#dt39) | DETERMINISTIC=1: exact replay for playtests | Testing and debugging tools | medium | large |
| [DT40](#dt40) | SIMFIGHT: simulate the real party against an encounter | Testing and debugging tools | medium | large |

<a id="n13"></a>

### N13. Companion behaviour: NPC party members who take orders and comment

*Theme:* NPC behaviour and dialogue · *Kind:* behaviour · *Value:* high · *Effort:* large

**Today.** The companion behaviours fall short:
- Hireling is paid and timed on the game clock.
- Follower follows whoever leaves the room.
- Loyalty seeks one named character, and searches every tick because its INTERVAL, (2*60)/4000, is 0.

A script can make an NPC follow (`MPFORCE $i follow $n`). ORDER gives `autoguard` (wait here), NOFOLLOW and kill. Every remark can be scripted with ENTRY_PROG, KILL_PROG, HITPRCNT_PROG and VAR guards. But after PARTY LEAD, handOver re-points only the profile's characters, so NPC followers keep the old leader. There are no stances, no loyalty and no ready-made remarks.

**Add.** Behaviour `Companion`:
- `JOIN=SPEECH "join me"|GIVE <price>|SCRIPT`.
- `FOLLOWS=PARTY`: follows again after PARTY LEAD, SWITCH or REST.
- `STANCE=AGGRESSIVE|DEFENSIVE|PASSIVE`.
- Orders: `wait here`, `follow`, `guard <name>`, `stance <x>`, `camp`, `go home`.
- `LOYALTY=n`, moved by `LIKES=` and `DISLIKES=` event:amount pairs (KILLINNOCENT, GIFT, HEALED, LEFTBEHIND), and `LEAVEAT=n`.
- `BARKS=`, inline or in a line file, keyed by events: ENTER_AREA, ENTER_ROOM, AFTER_KILL, LOW_HP, LEADER_LOW_HP, SEE_ITEM, SEE_MOB, REST, IDLE, QUEST_STEP or PLAYER_ACTION. Each has a chance, a cooldown and ONCE.
- `TALK <companion>` answers "what do you think?" from lines keyed by area or locale.

LOYALTY and STANCE are stats that scripts can read, and the state is saved. Loyalty's interval gets fixed along the way.

**Example.** `Companion JOIN=SPEECH FOLLOWS=PARTY STANCE=DEFENSIVE LOYALTY=60 DISLIKES=KILLINNOCENT:-30 LEAVEAT=10 BARKS="Dark Wood:I hate these trees."`. Told `wait here`, the squire waits outside the dragon's lair.

*See also* (in `audit/`): N21 (Two NPC party-member behaviours with loyalty, orders and saved terms; Companion and a configurable Hireling overlap); P9 (Companion NPCs (N13 (later)) and recruiting an NPC into the profile (P9) are alternatives for NPC party members); N18 (Both fix Loyalty's zero interval).

<a id="n14"></a>

### N14. Dialogue trees: a Dialogue behaviour and .dlg files

*Theme:* NPC behaviour and dialogue · *Kind:* behaviour, data file · *Value:* high · *Effort:* large

**Today.** The engine has no model of nodes and choices. Branching talk is built by hand:
- a SPEECH_PROG or GREET_PROG state machine, with per-player MPSETVAR state and one FUNCTION_PROG per node through MPCALLFUNC;
- menus with MPCHOOSE, MPPROMPT and MPCONFIRM, which block, and which abort the script when their 60 s timeout runs out;
- MudChat `$@group` switches, which are per NPC and expire.

Conditions (IF, +IF), ONCE (a VAR guard) and checks (STAT, ISABLE, RAND) all exist. What is missing is a format that ties them together.

**Add.** Behaviour `Dialogue FILE=innkeeper.dlg START=root`, reading world/dialogue/*.dlg:
- `NODE id`;
- `SAY` lines with $-variables and random alternatives;
- `CHOICE` lines with a label, a condition (a zapper mask with +IF, or an IF expression), `GOTO node` or `END`, and `ONCE` per player;
- `EFFECT` lines: MOBPROG commands with $n the player and $i the NPC, or a JavaScript block;
- `CHECK=CHA 14` or `SKILL=Skill_Haggle 50` on a choice, with SUCCESS and FAIL nodes.

Choices are shown numbered and answered through the non-blocking Session.InputCallback, as Prop_QuestGiver does, at no time cost. Each player's node and ONCE flags are kept in saved script variables. TALK (N9) opens the tree; MPDIALOG (N2) opens any node.

**Example.** The innkeeper's root node offers:
1. Ask about the mine, shown only if `QUESTSCRIPTED($n mine)` is false.
2. Rent a room, with EFFECT `mpcallfunc RENT`.
3. [CHA 14] Flirt for a discount. On SUCCESS it sets VAR DISCOUNT 1.
4. Leave.

*See also* (in `audit/`): N2, N9, N29 (N2's MPDIALOG, N9's TALK fallback and N29's CHECK= on choices all hook into N14 (later)'s dialogue trees).

<a id="n23"></a>

### N23. Rumours: a world list, gossiping NPCs, rumours from events

*Theme:* NPC behaviour and dialogue · *Kind:* data file, behaviour and script command · *Value:* medium · *Effort:* medium

**Today.** No rumour, news or gossip system exists; every "heard any rumours?" answer is written per NPC and goes stale. The workaround is shared script variables on a global host (MPSETVAR, saved) read back by SPEECH_PROG or MudChat `$%VAR(...)%`, with masks, expiry, weights and tell-once made by hand.

**Add.** A `world/rumors.txt` file: text, AREA or room mask, condition (+IF or quest state), weight, EXPIRES (game days), TELLONCE. A `Rumors` behaviour (`TOPICS=rumor news gossip CHANCE=10`) answers those words with a fitting rumour and sometimes volunteers one. `MPRUMOR ADD|REMOVE "text" AREA=... DAYS=...` for scripts and quests. Optional automatic rumours: area conquered, quest started or won, named NPC killed. Rumours told are kept per player (and in the notes, if approved) and saved.

**Example.** The Riverford barmaid hints "Black Ned's gang camps by the old mill." Ned's DEATH_PROG runs `MPRUMOR ADD "They say a stranger killed Black Ned" AREA=Riverford DAYS=10`, and she passes that on.

*See also* (in `audit/`): PR7, N24, N28 (N23 (later)'s rumours, N24's [[topic]] lines and N28's overheard scenes can write PR7 journal entries).

<a id="n25"></a>

### N25. LLM calls off the game thread

*Theme:* NPC behaviour and dialogue · *Kind:* behaviour, script command and world option · *Value:* medium · *Effort:* medium

**Today.** In single-player, executeRunnable queues every job on the one game thread. A MudChat backtick reply or MPLLM calls LLMSession.chat there, so the HTTP call can freeze the game, terminal and windows included, for up to 20 seconds. Timeout and temperature are fixed at 20 s and 0.7. This matters only to owners who install the langchain4j jar (LANGCHAIN4J_JAR_PATH is empty by default).

**Add.** Run LLMSession.chat on a dedicated worker thread, the one exception to the single game thread, and post the reply back as a game-thread job into the NPC's say queue. Show "X ponders your words..." at once. Ini settings LLM_TIMEOUT and LLM_TEMPERATURE, and a per-NPC `${LLMTEMP}`. In turn-based time the reply arrives before the next prompt. MPLLM gets a callback form: `MPLLM host var prompt CALLBACK=FUNC`.

**Example.** The LLM-voiced oracle no longer locks the game: the player sees "The oracle closes her eyes..." and her answer follows seconds later, while a turn-based world waits.

*See also* (in `audit/`): N10 (Both make NPC replies land before the next prompt in turn-based time, N25 (later) for LLM replies).

<a id="n26"></a>

### N26. LLM NPCs with lore files and saved memory per player

*Theme:* NPC behaviour and dialogue · *Kind:* behaviour · *Value:* medium · *Effort:* medium

**Today.** Context per message already works: the text after a backtick reply goes through mcFilter each time, so `$<...>` (speaker), `${...}` (NPC) and `$%...%` (script functions) can tell the LLM who speaks and when; a `${LLMCONTEXT=}` is not needed. Lore fits in the system prompt via `${LLMPROMPT=...}`, `${LLMPROMPT+...}` or an included `%file`. But memory is one session per NPC, reset after a hard-coded 360000 ms idle and lost on save. Document retrieval is Archon-only and commented out.

**Add.** `${LLMLORE=lore/riverford.txt}` per NPC, chat group or area, plus a world lore file, put in the system prompt, or retrieved by embedding when large; `LLMMEMSCOPE=PERPLAYER|NPC`; `LLMIDLE=` game minutes before reset, or never; the transcript saved with the game.

**Example.** The village elder knows from the lore file that the dam broke last spring, greets the player by name, mentions their unfinished "Missing Sheep" quest, and after a save and reload remembers their talk.

*See also* (in `audit/`): S9 (N26 (later)'s saved LLM transcript is S9's LLM session state).

<a id="n27"></a>

### N27. LLM NPCs that act from a declared whitelist

*Theme:* NPC behaviour and dialogue · *Kind:* behaviour · *Value:* medium · *Effort:* medium

**Today.** An LLM NPC can only talk: every reply becomes `say`. Crude hooks exist: its SPEAK_PROG fires on its own reply, so `SPEAK_PROG p 'go through'` can run `mpopen gate`, and MPLLM puts a reply in a VAR for IF and STRCONTAINS to check. But the reply has no target ($t is null), tags would be spoken aloud, and nothing limits what may happen or how often per player.

**Add.** `${LLMACTIONS=GIVE(rusty key) ONCE; SETVAR(TRUST 0-10); STARTQUEST(minerats); EMOTE; ATTACK IF AFFINITY<-50}` declares the allowed actions, and the system prompt explains tags such as `[[GIVE rusty key]]`. MudChat strips the tags from the speech, checks each against the list and its per-player limits, and runs it as MOBPROG commands, the NPC as monster and the speaker as $n. Rejected tags are logged. CHATTEST, if approved, tests it.

**Example.** A gatekeeper with OPEN(gate) declared is talked round and answers "Fine, go through." `[[SETVAR TRUST 8]] [[OPEN gate]]`; the gate opens. Undeclared, it would not.

<a id="c24"></a>

### C24. Invasions and raids on an area

*Theme:* Combat and encounters · *Kind:* behaviour and script command · *Value:* medium · *Effort:* large

**Today.** The CREATE QUEST wizard has two invasion templates, competitive_invasion1.quest and competitive_invasion2.quest. They bring invaders into a room with Mobile, count kills, and reward the winner through an announcer. The invaders win when time runs out. They work single-player, and running quests are saved. Marching (MPTRACKTO, MPWALKTO, Patroller, MOBHunter) and waves (quest STEPs) must be scripted by hand. Conquerable needs clan flags.

**Add.** An area behaviour `Invasion` with SOURCE=room ids, MOBS=CATALOG:orcs or a cmare file, WAVES=3, SIZE=4-6, EVERY=20 ticks and TARGET=room id. Attackers march to TARGET and fight what they meet on the way. START=mask, or a command `MPINVADE <area> START|STOP`, starts it. WIN when all waves are dead, LOSE when N attackers reach TARGET. Each outcome sets a variable and runs a FUNC, or MPROOMSTATE if room states are added. It also echoes to the area and on INFO, clears away the attackers left, and keeps its state in saves.

**Example.** A quest step starts an orc raid on Millbrook, in three waves from the northern ford to the mill. If the player holds, the reeve rewards them. If not, the mill burns and the bakery closes.

*See also* (in `audit/`): C20 (Both spawn waves of catalog mobs with win and lose outcomes; share the wave code); F19 (later) (Both march a catalog band from a staging room to a target (an invasion vs a clan raid)).

<a id="i12"></a>

### I12. PuzzleController: puzzle state, sequences and solve actions

*Theme:* Items, properties and puzzles · *Kind:* behaviour, builder tool · *Value:* high · *Effort:* large

**Today.** Ritual triggers with TriggeredAffects already do ordered and timed sequences without a script: '&' (in order), '|', TIMEOUT, SAY, PUTTHING, INROOM, CHECK \<mask> and more, tracked per player, then ADD or REMOVE an ability or behaviour. But PUSH, PULL, OPEN, GET and signals are not steps, state cannot span objects (three levers in positions), and there is no saved solved flag or wrong-move handling. The rest is MPSETVAR counters by hand.

**Add.** A PuzzleController behaviour on a room, area or hidden item, built on the Triggerer with new PULL, PUSH, OPEN and SIGNAL codes: INPUTS="lever1,lever2,lever3", MODE=ALL|ORDER|COUNT=n|EXPR="lever1=up & lever3=down", WINDOW=\<ticks or turns>, WRONG=reset|keep with WRONGMSG=, ONSOLVE= and ONFAIL= action lists, RESET=never|areareset|ticks:300, ONCE=true. Inputs and the solved flag are saved. The PUZZLE builder command shows the state.

**Example.** Hall of Bells: MODE=ORDER INPUTS=bell_low,bell_high,bell_mid WRONG=reset WRONGMSG="The bells clash and the echoes die." The right order opens the hidden stair.

*See also* (in `audit/`): SC6, I9, I15, I25 (The puzzle props send and receive SC6's signals (signal gate1 on, ONFULL=signal, SIGNAL inputs)); DT33 (DT33's PUZZLE command shows and works I12 (later)'s controller and the switch and plate props).

<a id="w24"></a>

### W24. Gradual and conditional restocking

*Theme:* World systems (time, weather, environment, economy, law) · *Kind:* property · *Value:* medium · *Effort:* medium

**Today.** A generic shopkeeper's stock resets all at once. Every `INVRESETRATE` ticks, `doInventoryReset` empties the shelves, the player's sales included, and reloads the built stock. Between resets a sold-out item stays gone. `INVRESETRATE` is a number of ticks, set per shop, per area or in the ini. Stock cannot depend on season, weather, a holiday or a variable. `Prop_ClosedSeason` does not work on a stock item, which has no room.

**Add.** `Prop_Restock` on a shopkeeper or an area. `RATE="1 per 6 HOURS"` or `"25% per DAY"` brings each built item back toward its built quantity. `SELLOFF="20% per DAY"` makes goods the player sold go a little at a time. A conditional line such as `STOCK="strawberries;10;WHEN=+SEASON +SUMMER"` keeps an item in stock only while its mask holds. `INVRESETRATE` also takes time words ("3 DAYS").

**Example.** The fruit seller has strawberries only in summer and apples in fall. A day after the player buys the armourer's last helm, it is back.

<a id="w25"></a>

### W25. Exposure: body temperature from climate, shelter and clothing

*Theme:* World systems (time, weather, environment, economy, law) · *Kind:* behaviour, property, script function · *Value:* medium · *Effort:* large

**Today.** There is no temperature model. The area's COLD and HOT bits and the weather only shift WeatherAffects' disease rolls and a few spells. Clothing counts only as cover for the feet, torso and legs. Some pieces can be reused: `Disease_FrostBite`, `Disease_Cold`, `Spell_ResistCold`, `Chant_ColdWard`, and `Prop_HereAdjuster` for penalties.

**Add.** An area behaviour `Exposure(cold=0-3 heat=0-3)`, which `AUTOAREAPROPS` can give to every area, puts an Exposure effect on the character. Each tick the effect adds up:
- the climate bits, the weather and the time of day;
- shelter (indoors, `HideoutShelter`, tents);
- being wet (swimming, rain);
- a lit fire in the room.

Clothing protects through `Prop_Insulation(WARM=n COOL=n)`, which by default comes from the material (fur, wool, leather). The stages chilled, cold and freezing bring movement and dexterity penalties, then damage and FrostBite; heat has its own stages. The prompt and `SCORE` show the stage, and `EXPOSURE($n)` returns it.

**Example.** In the Northern Wastes at night, away from a campfire, the player goes from chilled to freezing in about 30 ticks without a fur cloak (`WARM=2`). Inns and caves are safe.

*See also* (in `audit/`): W3 (Both hurt characters by cold and heat from where they are (a typed hazard zone vs a body-temperature model)).

<a id="w26"></a>

### W26. Bounty hunters and wanted posters

*Theme:* World systems (time, weather, environment, economy, law) · *Kind:* behaviour, property · *Value:* medium · *Effort:* large

**Today.** Every Arrest pursuit check is gated on `inMyMetroArea`, so a player wanted in one town is free one area over. `MOBHunter` stays in its own area (`TrackingFlag.AREAONLY`), and no mask means "wanted". `Skill_TrackCriminal`, `Skill_PrisonerTransfer` and `Skill_CollectBounty` have machinery to reuse. No wanted poster exists.

**Add.** A `BountyHunter` behaviour with these parameters:
- `AREAS="Riverton;Ashford"`: whose warrants it serves;
- `MINFINE=200` or `MINCRIMES=1`;
- `CHANCE`: per game hour;
- `RANGE`: the areas it travels.

When the player is wanted there but is elsewhere, the hunter tracks them down. The player can pay the fine plus a fee, or surrender and be taken to that jail with the warrant intact, or fight. If the hunter wins, it delivers them anyway. `Prop_WantedPoster` makes readable notices of the player's warrants and bounties. This needs a script query for warrants and a `+WANTED` mask, which are proposed separately.

**Example.** After robbing the Ashford bank the player flees to the hills. Two game days later Grek the bounty hunter walks into their inn.

*See also* (in `audit/`): W1, PR1 (W1's +WANTED mask is what W26 (later)'s bounty hunters need and what PR1's \<WANTED> tag tests).

<a id="w27"></a>

### W27. Market: supply and demand that remembers and recovers

*Theme:* World systems (time, weather, environment, economy, law) · *Kind:* behaviour, script command, script function · *Value:* medium · *Effort:* large

**Today.** Supply moves prices only through `DEVALRATE`. It counts the copies in one shop's stock and resets with that stock, so after the player sells 50 pelts to one tanner, the tanner next door still pays full price. `PRICEMASKS` can be changed by `MPSET $a` or a quest's `GIVE STAT`, but area stats set by a script are not saved. Nothing recovers over time or links towns. The StockMarket behaviour that DOCS describes is not in this tree.

**Add.** An area behaviour `Market`, for example `GOODS="pelts: -RESOURCE +FUR +HIDE; grain: -RESOURCE +WHEAT +BARLEY" BASE=1.0 SELLDROP=2% BUYRISE=1% RECOVER=10% per DAY MIN=0.3 MAX=3.0`. It keeps a saved price factor for each good, applies it to every shop in the area, and moves it back toward `BASE` each game day. `TRADE=Ashford:grain` passes part of a price change on to another town. `MPMARKET <area> <good> +/-pct` moves a factor, and `MARKET(<area> <good>)` reads it.

**Example.** The player sells a load of pelts in Riverton, and the price drops 40% and recovers over a week. A quest in which the grain caravan is robbed runs `MPMARKET $a grain +80`.

*See also* (in `audit/`): JS6, W1, W4, W6, W12, W14, W15 (JS6's cm world members (law, passTime, holiday, weather, shop, bank, market) are JavaScript forms of these W items and depend on them).

<a id="cr22"></a>

### CR22. Linked storage chests for the profile: Prop_SharedStash

*Theme:* Crafting, gathering and housing · *Kind:* property · *Value:* low · *Effort:* medium

**Today.** Bankers with the same `bankChain()` reach the same account from anywhere. But StdBanker keeps deposits by depositor name, so each character has their own account (saved as player data). A clan banker shares by clan, but clans are not world content. Prop_ItemTransporter and Prop_ItemTransReceiver move items one way only. A chest at home keeps its contents in the save, but can be reached only there.

**Add.** Prop_SharedStash on containers: `NAME=home`, an optional `MASK` (a zapper mask) and `CAPACITY=n`. Every container with the same NAME shows and holds one shared list of items. The list is kept in the save like player data and belongs to the profile, so every party character reaches it. PUT and GET work from any linked container.

**Example.** A "Wayfarer's coffer" in each inn reaches the same goods as the strongbox in the party's house.

<a id="f19"></a>

### F19. ClanWarfare behaviour: NPC clans raid and conquer

*Theme:* Factions and organisations · *Kind:* behaviour and world option · *Value:* medium · *Effort:* large

**Today.** Conquest moves only when members of a conquest-enabled clan kill natives in an area that holds their flag, and only against a ruler they are at `WAR` or `HOSTILE` with. NPC members never travel or fight on their own, so NPC clans never gain or lose ground without the player. A warlord's `DAY_PROG` (`mpmload`, `mpsetclan`, `mpoload`, `mptrackto`) or a JavaScript behaviour can fake a raid, but both are large and brittle.

**Add.** `ClanWarfare`, on a warlord NPC or a staging room. Parameters: `CLAN=`, `STAGING=`, `TARGETS=`, `EVERY=<game days>`, `SIZE=`, `RAIDER="catalog:orc raider"`, `FLAG="catalog:Horde Banner"`, `AUTOWAR=`, `RETREAT=<hp%>`, `RUMOUR=`. It runs when due, if the clan is at war with the target's ruler or the target has no ruler. It builds a band from the catalog and marches it there. The band plants the flag, fights natives and the ruling clan, and garrisons if it wins. `INFO` posts the rumour first and the result after. World option `WARS=ON|OFF`.

**Example.** Every 7 game days the Orc Horde sends 4 raiders from Grimtusk Keep toward Millbrook. Left alone, Millbrook falls within a month. Hunting the raiders on the road holds the line.

*See also* (in `audit/`): C24 (later) (Both march a catalog band from a staging room to a target (an invasion vs a clan raid)); F3 (F19 (later)'s raids change area rule, which F3 sets directly from scripts).

<a id="f21"></a>

### F21. Deity holy days, favor gates and portents

*Theme:* Factions and organisations · *Kind:* property · *Value:* low · *Effort:* medium

**Today.** Deities have ritual triggers, blessings, curses and powers. A ritual can already be limited to holy days with `CHECK <zapper mask>` (`+MONTHS`, `+DAY`, `+SEASONS`, `+WEATHER`, `+IF` and others). Favor exists as the `AUTOREACTION=DEITY` faction, which is off by default, and holidays can give mobs behaviours. But favor changes nothing by itself, holy days bring no reward, and a deity shows no signs. `GenDeity`'s only fields are `CLERREQ`, `CLERRIT`, `WORREQ`, `WORRIT` and `SVCRIT`.

**Add.** Three `GenDeity` fields. `HOLYDAYS=<holiday names or m-d>`: services and worship on those days give double favor and a guaranteed blessing. `FAVORGATES="<blessing or power>:<range code>"`: the blessing or power is granted only at that favor. `PORTENTS="<range code>:<message or weather>"`: signs that a favored or disfavored worshipper sees, such as rain that follows them.

**Example.** Ceres, goddess of the harvest: services during the Harvest Fair grant `Prop_ImproveGather`. Her infidels see crops wither as they enter farmland.

*See also* (in `audit/`): W6 (F21 (later)'s deity holy days are named holidays, tested with W6's tests by name).

<a id="f22"></a>

### F22. Prop_ClanDisguise: pass as a member in uniform

*Theme:* Factions and organisations · *Kind:* property and script trigger · *Value:* low · *Effort:* medium

**Today.** `ClanHelper`, `Conquerable`, the `-CLAN` mask and faction reactions read real membership. Masks cover part of this. `GateGuard`, `Prop_ReqEntry` and `Aggressive` accept `-WORN "+cult robes"` with `+OR`, `-CHANCE` and `-IF`. So a guard can let through members or wearers of the uniform, with a chance to fail. `WEAR_PROG` with `mpsetclan` grants real membership instead, which is saved and carries the clan's benefits.

**Add.** `Prop_ClanDisguise` for wearable items: `CLAN="Cult of the Black Sun" POSITION=ACOLYTE DETECT="-INT +15" CHANCE=20 ONDETECT=ATTACK|ALARM|SCRIPT`. While it is worn, NPCs' `-CLAN` checks (and `-CLANRANK`, if added) treat the wearer as that member. An NPC that passes `DETECT` rolls `CHANCE` once per encounter to see through it. A success fires `ONDETECT` and a new trigger, `CLAN_PROG DISCOVERED`. The wearer gets no titles or benefits. If this is declined, document the `-WORN` mask pattern instead.

**Example.** Robes taken from a dead acolyte get the party past the cult's door guards. But the sharp-eyed high priest may see through them and raise the alarm.

*See also* (in `audit/`): ST9, F2, F3, F9, F10, F16 (These fire ST9's CLAN_PROG events (RELATION, CONQUER, PROMOTE, DUESLATE, DISCOVERED), and ST9 and F10 both make addMember run the entry and exit scripts).

<a id="p30"></a>

### P30. Training that takes game time

*Theme:* Characters, progression and custom abilities · *Kind:* world option · *Value:* low · *Effort:* medium

**Today.** TRAIN, LEARN and PRACTICE cost only a command's action time. The expertise file's #TIME cost is documented but commented out in ColumbiaUniv. The Scholar's Studying skill learns over several ticks, but skills learned that way cannot be used. A trainer's CNCLMSG_PROG TRAIN can MPFORCE $n WAIT n so turn-based time passes, but the character is not busy and nothing interrupts it.

**Add.** A TRAINTIME= ini setting, per TRAINCOSTS entry and per stat; a TIME="2 hours per level" parameter on teachers (MOBTeacher); and the expertise #TIME field put into effect. While training, the character is busy as when crafting, the clock moves on as with WAIT, and an attack interrupts it.

**Example.** Learning Fireball at the archmage's tower takes three in-game days, while the world and a timed quest move on.

<a id="b14"></a>

### B14. CHATDRAFT: LLM drafts of fixed NPC dialogue

*Theme:* Builder tools · *Kind:* builder tool · *Value:* low · *Effort:* small

**Today.** In builder mode the `LLM <text>` command (needs LISTADMIN and an installed LLM) answers the Archon, with a session per character. A builder can paste an NPC's description, ask for MudChat entries in chat.dat syntax and copy them into a file by hand. MUDPercolator's LLM="true" values serve random areas only. An LLM at play time would cost money, need the network and change from play to play.

**Add.** `CHATDRAFT <mob> [topics...] [TO world/chat/<file>]` gathers the NPC's name, description, race, area and lore files, prompts the configured LLM, and writes a MudChat group, optionally with a .dlg skeleton. It shows a diff and asks before writing. `CHATDRAFT VARIANTS <file> <entry>` adds alternative replies to an entry. The result is plain data, so the game never needs the LLM at play time.

**Example.** `chatdraft blacksmith weapons armor prices gossip to world/chat/riverford.dat` gives a blacksmith group of four entries, with three to five weighted replies each in his gruff voice, for the owner to trim.

<a id="dt39"></a>

### DT39. DETERMINISTIC=1: exact replay for playtests

*Theme:* Testing and debugging tools · *Kind:* world option · *Value:* medium · *Effort:* large

**Today.** RANDOMSEED and TIMEMODE=TURNS are the only settings that make a run repeatable, and PLAN.md notes that exact replay is not done. Objects in a tick group tick in an order set by identity hash codes. The boot loads on several threads. IDs and database keys use Math.random(). So a recorded playtest or a golden transcript drifts between runs, and a difference after a world edit may mean nothing.

**Add.** A DETERMINISTIC=1 setting, in the ini and on the command line, for tests. With it:
- tick groups order their objects by registration order and a stable sequence number;
- the boot loads on one thread;
- generated IDs and database keys come from a seeded generator;
- time is forced to TURNS.

tools/smoke.sh and any replay of recorded input turn it on. This is engine work spread across the scheduler, the boot and ID generation.

**Example.** A recorded 300-command run through act 1 gives a byte-identical transcript every time, so any difference after a world edit is meaningful.

*See also* (in `audit/`): JS13 (Exact replay needs JS13's Math.random on the shared dice as well as DT39 (later)'s deterministic scheduler); DT15, DT25 (DT25 records .play files for DT15's runner, and exact replay of them needs DT39 (later)).

<a id="dt40"></a>

### DT40. SIMFIGHT: simulate the real party against an encounter

*Theme:* Testing and debugging tools · *Kind:* debug tool · *Value:* medium · *Effort:* large

**Today.** CHARGEN COMBAT ([classes]...) (MOB=\<name>) (ITERATIONS=x) (EXPORT=file) (FAILCHECK) (SKIPLEVELS) [start] [end] runs stock class characters, each with its class's CombatAbilities AI, one-on-one against a mob, catalog mobs included. It reports wins per class and level. It cannot use the real party with its gear, skills and followers. It cannot face several opponents, a room or an encounter table. It reports nothing per member.

**Add.** SIMFIGHT [PARTY|name...] VS [HERE|mob name|encounter:\<table>|catalog:\<name>*n] ITERATIONS=50 AI=CLASS [DIFFICULTY=x], built as an extension of CharGen. It copies the party and the opponents into a scratch room and runs the fight headless at full speed. Followers are on AUTOASSIST and party members use class AI. It reports the win rate and median rounds; HP and mana left and deaths, per member; the deadliest ability; and how often telegraph or phase triggers fired.

**Example.** After setting up the lich, with phases and a telegraphed blast, SIMFIGHT PARTY VS HERE ITERATIONS=100 gives 'win 34%, cleric dies 61%, median 14 rounds'. The builder weakens the blast and reruns until the win rate is about 70%.

*See also* (in `audit/`): C9 (DT40 (later) simulates fights against C9's encounter: tables); DT35 (DT35's combat log is meant for DT40 (later)'s SIMFIGHT runs).
