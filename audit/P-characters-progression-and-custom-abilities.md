# Characters, progression and custom abilities

Character creation, starting kits, levelling, training and teachers, achievements, ranks, ageing, races, classes and GenAbility-style custom abilities and diseases. Part of the [builder capabilities audit](README.md).

<a id="p1"></a>

## P1. GenAbility as a complete custom property

*Kind:* property · *Value:* high · *Effort:* small · *Decision:* yes / no

**Today.** `CREATE ABILITY` makes a GenAbility, and its SCRIPT already runs when the GenAbility is attached as an effect. But HERESTATS and MOCKABILITY are set up only in `invoke()` and `autoInvocation()`, so a GenAbility attached statically gives no stats. The script cannot read the text of its own instance, so each setting needs its own copy. The workaround is to attach `Scriptable(LOAD=...)` plus `Prop_HereAdjuster` or `Prop_WearAdjuster`.

**Add.** (a) When a GenAbility is added as a non-invoked effect, build its HERESTATS and MOCKABILITY effects in `setAffectedOne` or `setMiscText`. (b) Give the SCRIPT the instance text as `$PARMS`, and a function `EFFECTPARM(KEY)`. Then one class serves many settings, such as `Prop_MyTrap(DAMAGE=10 MSG=...)`. (c) Write an AHELP GENABILITY page with a worked custom-property example. Today the only help is LIST ABILITYFLAGS.

**Example.** The builder makes Prop_Haunted once. It is a GenAbility whose MOBPROG brings cold drafts, flickering lights and moans at night. It is attached to twenty rooms with different `FREQ=` values and saved in `world/custom.cmare`.

*See also:* SC13, N7, JS9 (Four ways to give a shared script or effect per-instance parameters (SC13's ARG(), N7's PARM(), P1's EFFECTPARM(), JS9's parms); decide them together).

<!-- from: properties/pr-genability-prop -->

<a id="p2"></a>

## P2. Exact level-ups from scripts: MPLEVELUP, MPLEVELDOWN, EXPTONEXT

*Kind:* script command and script function · *Value:* high · *Effort:* small · *Decision:* yes / no

**Today.** `MPEXP $n 100%` posts one level's XP span. That is exactly one level only when the character sits at a level boundary, as with milestone-only XP (kill XP turned off through XPMOD). From mid-level it overshoots or falls short. A negative MPEXP can take a level away. `MPSET LEVEL` changes the number with no gains. A JavaScript block can call `CMLib.leveler().level()` through Packages, but only with JSCRIPTS=2 or approval. STAT() has no code for XP to the next level.

**Add.** `MPLEVELUP <target> [n]` and `MPLEVELDOWN <target> [n]`. Up posts exactly `getExpNeededLevel()+1` XP, or calls `level()` when the character has no XP. Down calls `unLevel` with the XP adjusted. Also add an `MPEXP <target> NEXTLEVEL` keyword, a function `EXPTONEXT($n)`, and the same calls for JavaScript.

**Example.** When the dragon dies, the quest's last step runs MPLEVELUP on each party member with "You feel the weight of what you have done", and each gains one level.

<!-- from: character-progression/cp-milestone-levelup -->

<a id="p3"></a>

## P3. Named lifecycle hooks and a cast-failure message for GenAbility scripts

*Kind:* script trigger · *Value:* high · *Effort:* medium · *Decision:* yes / no

**Today.** Most of these hooks exist under other names. On a successful cast the script gets a message whose text is the ability ID, with the cast arguments as $0-$9 (`IMASK_PROG p <ID>`). On expiry it gets `UNINVOKE-<ID>`. RAND_PROG and DELAY_PROG fire each tick while the effect is on. CASTMASK can veto a cast before costs, with zapper keys such as +IF, -HOUR and -LOCATION. But it always prints "You do not meet the requirements: \<maskDesc>" (TARGETMASK has TARGETFAILMSG), and the target is not $t during the check. Nothing fires when a mob learns the ability. GenTrap sends a message only when it springs, and nothing on disarm.

**Add.** A CASTFAILMSG field, and the target as $t during the CASTMASK check. Named functions called where the old messages are sent, as GenCommand calls EXECUTE: CANCAST (before costs; RETURN false cancels), ONCAST, ONTICK and ONEXPIRE. The old messages stay. New hooks: ONLEARN, when a mob gains the ability, and ONTRIGGER and ONDISARM for GenTrap.

**Example.** Moonblade's `FUNCTION_PROG CANCAST` runs `IF !istime(night) MPECHO The moon is hidden. RETURN false`. ONCAST gives the target a silver glow, and ONEXPIRE echoes "the moonlight fades".

*See also:* ST2 (ST2's SKILL_PROG LEARN and P3's ONLEARN hook both fire when a mob gains an ability, from the same addAbility point); I20 (Both extend GenTrap scripting (TRIGGERS and DISARM vs ONTRIGGER and ONDISARM hooks)); P21, P22 (Named script hooks (CANCAST, ONCAST, ONTICK, ONEXPIRE) on GenAbility, GenTweakAbility, GenPoison and trade skills; one hook design).

<!-- from: data-content/dc-genability-lifecycle -->

<a id="p4"></a>

## P4. GenAbility TARGETMODE: area, group and all-enemies abilities

*Kind:* data file (GenAbility field) · *Value:* high · *Effort:* medium · *Decision:* yes / no

**Today.** A GenAbility resolves exactly one target by its can-target code. It never calls `StdAbility.properTargets`, which the engine's own area spells use. There are rough substitutes. Can Target and Can Affect set to ROOMS with HERESTATS gives a room aura, through `Prop_HereAdjuster` with `MASK=`. A script on `IMASK_PROG p <ID>` can FOR-loop over the room with MPDAMAGE or MPCAST. None of them gives each target its own saving throw, damage formula, cast message, POSTCASTAFFECT and POSTCASTABILITY.

**Add.** A field TARGETMODE, built on properTargets, with the values SINGLE (default), ALLENEMIES, ALLALLIES (the caster's group in the room), ALLINROOM or GROUPANYWHERE. Each target goes through the existing steps: target mask, cast message, saving throw, damage or heal formula, HERESTATS, public effects, extra castings and the script's cast event. Optional fields: a MAXTARGETS formula, and an AOEMSG shown once before the messages for each target.

**Example.** Thunderclap has TARGETMODE=ALLENEMIES, damage 2d6+@x1/2, and public effect Spell_Deafness for 3 ticks. A party song, Marching Cadence, has TARGETMODE=ALLALLIES and HERESTATS for move regeneration.

<!-- from: data-content/dc-genability-targetmode -->

<a id="p5"></a>

## P5. Achievement rewards: SCRIPT, EFFECT, FACTION, VAR and RECIPE

*Kind:* data file (achievement rewards) · *Value:* high · *Effort:* medium · *Decision:* yes / no

**Today.** Achievement REWARDS have 13 types: QP, XP, CURRENCY, TITLE, ABILITY, EXPERTISE, STAT, NOPURGE, CLANXP, CLANCURRENCY, TATTOO, ITEM and MOB. Story consequences need detours. A recipe is an ITEM reward of a catalog GenRecipe item. A permanent passive is an autogain ABILITY reward: a GenAbility set to AUTOINVOKE with HERESTATS, whose script can also run ONCE_PROG with MPFACTION or MPSETVAR, though the auto-invoked copy is not saved, so that ONCE_PROG runs again at every load unless the script guards it. A TATTOO reward can be tested later with hastattoo() and masks. FACTION, SCRIPT, VAR and a timed EFFECT do not exist as rewards.

**Add.** New reward types:
- `SCRIPT(LOAD=progs/x.script)` or an inline script, run once on the player with ONCE_PROG or FUNCTION_PROG ONACHIEVE;
- `EFFECT(ID=Prop_Adjuster PARMS="con+1" TICKS=0)`, permanent when TICKS=0;
- `FACTION(ID=elves.ini AMOUNT=500)`;
- `VAR(NAME=chapter VALUE=2 SCOPE=*)`, a script variable;
- `RECIPE(SKILL=Weaponsmithing NAME="rune blade")`.

The ACHIEVEMENTS command shows what each reward gives. [ACCOUNT] (profile) achievements may give these rewards to every later character.

**Example.** Friend of the Grove (QUESTOR, three grove quests) gives 1000 FACTION elves.ini, a permanent EFFECT Prop_Resistance (poison 10%), and a SCRIPT that changes the elder's next greeting.

*Re-checked against the code after writing: corrected.*

*See also:* ST29 (Alternatives for running a script when an achievement is earned: an ACHIEVE_PROG trigger or a SCRIPT reward); Q10 (Both extend Achievements.parseAwards' award syntax (FACTION, catalog ITEM, SCRIPT, VAR, RECIPE)); CR4 (P5's RECIPE reward needs a place to keep known recipes, which CR4's Prop_KnownRecipes is).

<!-- from: data-content/dc-achievement-rewards -->

<a id="p6"></a>

## P6. Character origins (backgrounds) as data

*Kind:* data file · *Value:* high · *Effort:* medium · *Decision:* yes / no

**Today.** An origin can be scripted. `CHARCREATIONSCRIPTS=FACTIONS:LOAD=progs/origin.script` runs a ONCE_PROG before the start room is set. The script picks an origin with MPCHOOSE, records it with MPTATTOO, and gives items, skills, standing and money (MPOLOAD, MPENABLE, MPFACTION, MPMONEY). Then `START_MASK=TempleOfAsh#0=-TATTOO +ORIGIN_ORPHAN` sets the start room. Nothing lists or describes origins, and the parts sit in coffeemud.ini and resources/progs, not in the world. STARTINGITEMS splits by race only.

**Add.** A file `world/data/origins.ini`. Each origin has:
- NAME, DESCRIPTION, and MASK (the races and classes allowed);
- UNLOCK, an account tattoo such as ORIGIN_EXILE, given by profile achievements;
- START (room ID), ITEMS (catalog names with counts), ABILITIES (ID and proficiency), FACTIONS (ID and amount), TATTOOS and MONEY;
- SCRIPT (a ONCE_PROG).

When origins exist, character creation adds an ORIGIN step after class. A CHARCREATIONSCRIPTS code ORIGIN runs old-style scripts after it.

**Example.** Temple Orphan, for humans and half-elves, starts at the Temple of Ash. It gives a worn prayer book, Prayer_Bless at 25%, +1000 Faith of Ash and the tattoo ORPHAN, so the head priest greets them by name.

*See also:* P7, P12 (P6's ORIGIN step and P7's KIT step slot into P12's data-driven creation steps).

<!-- from: data-content/dc-origins -->

<a id="p7"></a>

## P7. Starting kits by class, origin or mask, with a choice of kit

*Kind:* data file · *Value:* high · *Effort:* medium · *Decision:* yes / no

**Today.** STARTINGITEMS splits by race prefix and accepts catalog names. Class outfits are fixed per GenCharClass (OUTFIT, stored as full item XML). There are workarounds:
- a kit by any mask: a [PLAYER] JUSTBE achievement with PLAYERMASK and an ITEM reward, but JUSTBE is checked periodically, so the kit arrives a little after creation;
- a menu of kits: a `CHARCREATIONSCRIPTS=CLASS` ONCE_PROG with BASECLASS, MPCHOOSE and MPOLOAD.

Nothing declares kits as data or puts their items on.

**Add.** A thin data layer over these routes: `world/kits.ini`, or STARTINGKIT_n keys, for example `KIT="Soldier's kit" MASK="-BASECLASS +Fighter" CHOOSE=fighter ITEMS="1 \"a short sword\", 1 \"a leather jerkin\"" MONEY="20 gold coins" WEAR=YES`. Items can be catalog names, class IDs or LOAD= items. Every kit whose mask matches is given. Kits that share a CHOOSE group are offered as a menu, in a KIT step (P12) or right after CLASS.

**Example.** Fighters choose "sword and shield" or "two-hander". Every dwarf also gets a mining pick. Characters tattooed ORIGIN_NOBLE get a signet ring and a better cloak.

*See also:* P6, P12 (P6's ORIGIN step and P7's KIT step slot into P12's data-driven creation steps); P8, DT16 (DT16's kit files and TESTCHAR overlap P7's starting kits (both under world/kits) and P8's ready-made characters; share one format).

<!-- from: character-progression/cp-starting-kits -->

<a id="p8"></a>

## P8. Ready-made characters (templates) offered by NEW

*Kind:* data file and world option · *Value:* high · *Effort:* medium · *Decision:* yes / no

**Today.** Creation always goes through race, gender, stats and class. A fixed hero is possible with workarounds. `DISABLE=CHARCRRACE,CHARCRGENDER,CHARCRCLASS,CHARCRSTAT` picks at random among the allowed choices. With one race available and `CLASSSYSTEM=NO-[ClassID]`, those picks are fixed. A `CHARCREATIONSCRIPTS` END ONCE_PROG then sets stats, skills, items, tattoos and the description, and MPCHOOSE can offer preset bundles. IMPORT reads characters from XML, but NEW does not offer them. The player always types the name.

**Add.** Catalog mobs in category PLAYERTEMPLATE, or in `world/templates.cmare`.
- With `CREATIONTEMPLATES=OFFER`, NEW lists them, each with a one-line pitch, next to "make your own". With ONLY, they are the only route.
- Picking one copies race, gender, class and level, stats, abilities and their proficiency, expertises, factions, tattoos, items, money and description.
- `LOCK=NAME,GENDER` keeps fields fixed. `NAME=RANDOM` draws a name.
- Builders edit templates in builder mode like any catalog mob.

**Example.** In a detective world you always play Inspector Hale (LOCK=NAME). A dungeon crawler offers six ready-made adventurers for a quick party, next to custom creation.

*See also:* P7, DT16 (DT16's kit files and TESTCHAR overlap P7's starting kits (both under world/kits) and P8's ready-made characters; share one format); S10 (Both use world/templates.cmare, for player templates and for builder templates).

<!-- from: character-progression/cp-pregen-characters -->

<a id="p9"></a>

## P9. MPRECRUIT: an NPC met in the world joins the profile

*Kind:* script command and property · *Value:* high · *Effort:* medium · *Decision:* yes / no

**Today.** Companions met in the world can only be followers or hirelings (Follower, Hireling), and they stay NPCs. The player cannot SWITCH to them, they do not level as players, and the party save does not keep them. The only NPC-to-player conversion is hard-coded in Age.java, for a grown-up baby. MPPLAYERCLASS only changes the class. A JavaScript block could repeat the Age.java steps through CMLib, but that is fragile.

**Add.** `MPRECRUIT <npc> <player> [PARTY|REST] [NAME=...]`. It makes a player character from the NPC and keeps race, class and level, stats, abilities, effects, inventory, factions, tattoos and description. It adds the character to the profile, which then either joins the party or rests. It refuses with a message if the profile or party is full (P10) or the name is taken. Party.bringIn already applies the existing caps. Also add a property `Prop_Recruitable` (MASK=, MESSAGE=, COST=), so the player can type RECRUIT MIRA with no script, and the same call for JavaScript.

**Example.** You free the ranger Mira from the goblin cave. When you ask her to join, her SPEECH_PROG answers "I'll travel with you" and runs `MPRECRUIT $i $n PARTY`. SWITCH MIRA then plays her.

*See also:* N13 (Companion NPCs (N13) and recruiting an NPC into the profile (P9) are alternatives for NPC party members); P10 (P9's recruit refuses when P10's profile or party size is full).

<!-- from: character-progression/cp-recruit-npc -->

<a id="p10"></a>

## P10. World rules for the profile and party: size, new characters, roles, cheats

*Kind:* world option · *Value:* high · *Effort:* medium · *Decision:* yes / no

**Today.**
- `COMMONACCOUNTSYSTEM=n` caps the profile, but n must be above 1, so a world with one character only is not possible. The reward "1 ACCOUNT BONUSCHARLIMIT" raises the cap.
- `MAXCONNSPERACCOUNT=n` caps how many of the profile's characters are in the world at once, party members included. When it refuses a PARTY ADD, the message goes to the new member's own session, and the player sees only a generic failure.
- `DISABLE=NEWCHARACTERS` blocks the first character too.
- There are no party roles and no cheat setting (PLAN.md:441-442).

**Add.** Settings in the ini or in `world/rules.ini`:
- PARTYMAX=n, the most characters in the party at once, and PROFILEMAX=n;
- NEWCHARS=ALWAYS|FIRSTONLY|CHEAT|MASK, with the mask checked on the leader, e.g. "+ROOMSTR(Adventurers' Guild)", and a NEWCHARMESSAGE explaining where to go;
- PARTYROLES="healer:-BASECLASS +Cleric +Druid; scout:-BASECLASS +Thief", warned about at NEW and PARTY;
- a command, CHEAT ON/OFF, kept in the save and shown in SAVES; it unlocks NEWCHARS=CHEAT and other options marked as cheats.

Also fix the lost refusal message.

**Example.** A lone-wolf world sets PARTYMAX=1 NEWCHARS=FIRSTONLY. A dungeon crawler sets PARTYMAX=4 NEWCHARS=MASK, so new characters are made only at the Adventurers' Guild.

*See also:* P9 (P9's recruit refuses when P10's profile or party size is full); S3, S6 (P10's world/rules.ini, S14 (now in S6)'s rules.ini and S3's world.ini are one world rule file).

<!-- from: character-progression/cp-profile-party-rules -->

<a id="p11"></a>

## P11. Prop_Discovery: XP and marks for discoveries, once per character, party or profile

*Kind:* property and world option · *Value:* high · *Effort:* medium · *Decision:* yes / no

**Today.** Once-per-character room XP needs two properties. `Prop_EnterAdjuster "experience+150 MASK=+TATTOOS -FOUND_GROTTO"` pays those without the tattoo, and `Prop_TattooAdder "FOUND_GROTTO"` adds the tattoo after entry (ACCOUNT tattoos too). Other triggers need scripts (LOOK_PROG, OPEN_PROG, EXECMSG_PROG READ, GREET_PROG) using HASTATTOO, MPTATTOO and MPEXP. Achievements such as ROOMENTER, EXPLORE and AREAVISIT live in resources/. Missing: a single property for all of this, once per party, a "hidden thing found" trigger, and XP for every room seen for the first time.

**Add.** A property Prop_Discovery, for rooms, exits, items or mobs:
- XP=150, or XP=10% of the level's span;
- ON=ENTER|LOOK|READ|OPEN|GET|FIND|TALK, where FIND means a hidden thing was found;
- ONCE=CHARACTER|PARTY|PROFILE;
- MESSAGE=, TATTOO=, MASK= and a JOURNAL line.

The XP is tagged DISCOVERY:, so XPMOD can scale it. A world option `DISCOVERYXP=<formula>` pays for each room a character enters for the first time.

**Example.** The grotto behind the waterfall gives 150 XP the first time anyone in the party enters. Reading the ancient tablet gives 40 XP and the tattoo LORE_TABLET, which an NPC asks about later.

*See also:* ST3, PR3 (Three hooks on a character's first entry to a room (a trigger, once-only text, discovery XP) that can share one first-visit flag); I11 (P11's ON=FIND discovery XP fires when a hidden thing is found, as I11's discoveries are).

<!-- from: character-progression/cp-discovery-xp -->

<a id="p12"></a>

## P12. Creation steps and questions as data

*Kind:* data file · *Value:* high · *Effort:* large · *Decision:* yes / no

**Today.** Creation is a fixed Java state machine, and the order of its steps cannot change. There are partial routes:
- A faction works as a question: AUTOCHOICES with zapper masks, a CHOICEINTRO screen, and range names as the answers. `START_<range code>` and -FACTION masks then use the answer.
- `CHARCREATIONSCRIPTS` at RACE, GENDER, STATS, CLASS, FACTIONS or END runs a ONCE_PROG that can use MPCHOOSE or MPPROMPT (see `resources/progs/chardesc.script`). But the mob is moved to a random room, and the player cannot go back to a question.

**Add.** A file `world/creation.ini`.
- `STEPS=RACE,GENDER,ORIGIN,STATS,CLASS,KIT,FACTIONS,DEITY,REVIEW` sets the order. Each standard step can be SKIP or RANDOM, replacing the CHARCR* DISABLE flags. At REVIEW the player can go back to a step.
- A custom step: `[ORIGIN] PROMPT="Where were you raised?" INTRO=text/origin.txt SHOWIF=<zapper>`, then CHOICE lines. Each has NAME, HELP, MASK and EFFECTS in the achievement reward syntax, plus FACTION=, START= and SCRIPT=LOAD=. The script runs in the character's real room.
- Each answer is stored as a tattoo (CHARCR_ORIGIN_NOBLE).

P6 and P7 can build on it.

**Example.** "Origin: Noble / Farmhand / Street urchin". The noble gets +1 CHA and 50 gold and starts in the manor. The urchin gets Thief_Swipe at 25% and starts at the docks, and the cooper greets urchins differently (HASTATTOO).

*See also:* P6, P7 (P6's ORIGIN step and P7's KIT step slot into P12's data-driven creation steps); P27 (P27's REVIEW with BACK is the REVIEW step of P12).

<!-- from: character-progression/cp-creation-questions -->

<a id="p13"></a>

## P13. Picks at level-up and at creation: perks, traits and flaws

*Kind:* data file · *Value:* high · *Effort:* large · *Decision:* yes / no

**Today.** A level-up gives fixed gains. The only choice is how to spend trains and practices. Perks can be built today:
- a GenAbility, passive and AUTOINVOKE, with MOCKABILITY or HERESTATS;
- listed in allqualifylist.txt [EACH] with a level, MASK= and REQUIRES=;
- priced in SKILLCOST ("Perk_Tough 1 TRAIN");
- learned from a MOBTeacher, or given by an achievement.

Flaw-like effects exist too (SlowLearner, BadReputation, Allergies). Missing: a schedule of picks with its own currency, traits and flaws bought with points at creation, and a screen that shows locked perks with the reason.

**Add.** A file `world/perks.ini`.
- Each perk: `PERK_TOUGH NAME="Tough" POOL=GENERAL MASK="-LEVEL +>=3" REQUIRES=PERK_HARDY COST=1 REWARDS="20 HITPOINTS 1 SAVE_POISON"`.
- The schedule: `PICKS="GENERAL every 3 levels; CLASS:Fighter at 1,5,10"`.
- At creation, `TRAITS=2`; flaws have a negative COST and give points back.
- PERKS lists available, owned and locked perks. PERK \<name> takes one.
- Taken perks are tattoos, with a function HASPERK() and a command MPPERK.

**Example.** At levels 3, 6 and 9 the player picks Pack mule (+50% carry), Night eyes (infravision) or Silver tongue (better shop prices). At creation, the flaw Bad back pays for the trait Lucky.

<!-- from: character-progression/cp-perks -->

<a id="p14"></a>

## P14. GenAbility action costs and components in the ability's record

*Kind:* data file (GenAbility fields) · *Value:* medium · *Effort:* small · *Decision:* yes / no

**Today.** A GenAbility's action cost can be set only in coffeemud.ini, in the DEFABLETIME and DEFCOMABLETIME exception lists ("Spell_Emberbrand 2.0, 0.10"). `StdAbility.castingTime` and `combatCastingTime` read these lists. GenAbility has no cost field, while GenCommand has ACTCOST and CBTCOST. Components are kept only in `resources/skills/components.txt`, edited with CREATE/MODIFY COMPONENT. A reagent can also be checked by CASTMASK with -ITEM or +ITEM, and used up with MPJUNK in the script. Neither the cost nor the components travel with the spell in custom.cmare.

**Add.** New fields on GenAbility, GenTweakAbility and the craft and gather skills:
- ACTIONCOST and COMBATACTIONCOST, in actions, with -1 for the default;
- COMPONENTS, in the syntax of a components.txt line, e.g. `(held:consumed:1:ember shard:)`.

The component library reads an ability's COMPONENTS field before components.txt.

**Example.** Greater Ward takes 2.0 actions in combat and uses up a pinch of salt from the caster's pack. Both are set in the spell's own record.

<!-- from: data-content/dc-genability-costs -->

<a id="p15"></a>

## P15. Starting level for characters made later in the game

*Kind:* world option · *Value:* medium · *Effort:* small · *Decision:* yes / no

**Today.** Every new character starts at level 1, and no STARTLEVEL setting exists. Two routes give a head start through real level-ups. First, an [ACCOUNT] achievement: its REWARDS ("N XP", TRAINS, PRACTICES, items) go to each new character at creation, and a story script can unlock it with MPACCTATTOO or MPACHIEVE. Second, a `CHARCREATIONSCRIPTS` END ONCE_PROG can run `MPEXP $i n`. Neither can follow the party's level.

**Add.** A setting `NEWCHARLEVEL=1|N|PARTYMIN|PARTYAVG|LEADER-n`. It is applied after creation through real level-ups, as CHARGEN does, so HP, practices, trains and autogain skills are granted. An optional setting `NEWCHARXP=keep|floor` goes with it. Kits with -LEVEL masks (P7) can then give gear for the level.

**Example.** The party is level 9 when a cleric is created. With NEWCHARLEVEL=PARTYMIN-1 the cleric joins at level 8, with eight levels' worth of trains and practices to spend.

*Re-checked against the code after writing: accurate.*

<!-- from: character-progression/cp-new-char-start-level -->

<a id="p16"></a>

## P16. Teachers gated by mask, rank and price, and a TEACHLIST view

*Kind:* behaviour · *Value:* medium · *Effort:* small · *Decision:* yes / no

**Today.** Every NPC starts with the NOTEACH attribute. Only mobs with MOBTeacher, Prop_Trainer or Prop_StatTrainer, or of the Teacher class, can teach or TRAIN. So a setting allowing only marked trainers is not needed. One bug: TRAIN takes the first mob in the room even when it refuses, so the player must name the trainer. MOBTeacher already limits skills by its ID list, and proficiency by its N% parameter. SKILLCOST sets prices per skill. Gating students needs a CNCLMSG_PROG on each teacher for TEACH, PRACTICE or TRAIN, with IF, MPECHOAT, RETURN CANCEL and MPMONEY.

**Add.** New parameters for MOBTeacher and Prop_Trainer:
- STUDENTMASK="\<zapper>" and REFUSEMSG="...";
- SKILLMASK.\<AbilityID>="\<zapper>" for each skill;
- PRICE="5 gold per level", added to the normal cost;
- HIDDEN=skill list.

The masks also gate PRACTICE and GAIN. A TEACHLIST command shows what this teacher offers you. TRAIN should skip a trainer that refuses.

**Example.** Brother Aldous teaches Bless to any Acolyte of the temple. He teaches Sanctuary only to Priests, with `SKILLMASK.Prayer_Sanctuary="-CLANRANK '+Temple of Arden>=PRIEST'"`. He refuses anyone whose temple standing is Shunned.

<!-- from: character-progression/cp-gated-teachers, organizations-factions/org-teacher-student-mask -->

<a id="p17"></a>

## P17. Trains and practices per level as formulas

*Kind:* world option, data file · *Value:* medium · *Effort:* small · *Decision:* yes / no

**Today.** Each level gives one train, and floor(WIS/6) practices plus the class's LVLPRAC. GenCharClass has LVLPRAC, FSTPRAC and FSTTRAN, and its HP, mana and move gains are already formulas. GenRace has no train or practice fields, and the ini has no formula for either. A global LEVEL_PROG with MPTRAINS or MPPRACS can add or take back points on chosen levels, and TRAINCOSTS converts one into the other.

**Add.** Ini keys FORMULA_TRAINSPERLEVEL and FORMULA_PRACTICESPERLEVEL: @x1 the new level, @x2 WIS, @x3 INT, @x4 the class bonus, @x5 the race bonus. Optionally TRAINSATLEVELS="1:3, 5:2, 10:2" for bursts at chosen levels. New fields: LVLTRAIN on GenCharClass; TRAINS1, PRACS1, LVLPRAC and LVLTRAIN on GenRace.

**Example.** A low-power world gives a train every second level and a flat 2 practices a level. Elves get 2 extra practices at level 1, humans an extra train.

*See also:* JS23 (JS23's practicesForLevel and trainsForLevel override P17's per-level formulas; they are alternatives).

<!-- from: character-progression/cp-progression-formulas -->

<a id="p18"></a>

## P18. Creation screens that follow the content, with locked choices shown

*Kind:* presentation · *Value:* medium · *Effort:* small · *Decision:* yes / no

**Today.** After races.txt and classes.txt, the creation prompt already lists the races and classes that qualify, builder-made GenRace and GenCharClass included, and typing one shows its HELP. But the intro texts are static, classes.txt is printed raw (not even through TextMacros.filter), and the only list macro is @RaceList@, names only. Races locked behind a RACE_ profile tattoo are hidden silently.

**Add.** Filter classes.txt through TextMacros. New macros: @ClassList@, @RaceList?FORMAT=TABLE@ (name and the first line of HELP), @DeityList@, @StatList@ (ADDCHARSTAT stats included) and @if?Tattoo=RACE_DROW@. A SHOWLOCKED=YES setting and an UNLOCKHINT field on GenRace and GenCharClass list a locked choice with its hint.

**Example.** Until the Underdark quest is finished in some game of the profile, the race screen shows "Drow (locked: reach the Underdark)". After that, the RACE_DROW tattoo makes it pickable.

<!-- from: character-progression/cp-creation-screens -->

<a id="p19"></a>

## P19. Summons as a GenAbility field

*Kind:* data file · *Value:* medium · *Effort:* medium · *Decision:* yes / no

**Today.** Summoning needs Java: stock spells such as Spell_SummonMonster build their mob in code. GenAbility has no summon field, and no Prop_ spawns a mob. A script can do it with MPMLOAD, MPFORCE follow, and MPPURGE in an IMASK_PROG p UNINVOKE-\<ID> and on the summoner's death. That is fragile, and since followers are saved with the player (PLAN 4d), a missed cleanup is saved too.

**Add.** GenAbility fields SUMMON=\<catalog mob name or class>, SUMMONCOUNT and SUMMONLEVEL (formulas, @x1 the caster's level), and SUMMONFLAGS (FOLLOW, ASSIST, NOSAVE, DISMISSONEXPIRE, BIND to the caster). The summon lives while the effect lasts, leaves with the UNINVOKEMSG, and is a follower for party turns and AUTOASSIST.

**Example.** A druid-like class's "Call the Pack" summons 1+@x1/10 grey wolves from the catalog for 20 ticks. They follow and assist, and go when the effect ends.

<!-- from: data-content/dc-genability-summon -->

<a id="p20"></a>

## P20. GenDisease: diseases as data

*Kind:* data file, builder tool · *Value:* medium · *Effort:* medium · *Decision:* yes / no

**Today.** There are 141 Java diseases and no generic one, and CREATE has no DISEASE. A GenAbility with CLASSIFICATION=DISEASE already counts for Prayer_CureDisease and about 31 other checks, and HERESTATS, TICKAFFECTS, TARGETMASK, a script and Prop_HereSpellCast on rooms cover much. It lacks DiseaseAffect: difficultyLevel, which cures compare with the caster's level, spread through Disease.catchIt, a DELAY between effects, and convertAbilityToGeneric for diseases.

**Add.** GenDisease, a GenAbility subclass implementing DiseaseAffect, and CREATE DISEASE. Fields: TICKS formula, DELAY, SPREAD (STD, CONTACT, PROXIMITY, CONSUMPTION, DAMAGE, GET, HEARING, INHERITED), CATCHCHANCE, DIFFICULTY, STARTMSG, AFFECTMSG, DONEMSG, ADJUSTMENTS, DAMAGE per delay, EFFECTS (Prop_SpellAdder syntax), IMMUNEMASK, MALICIOUS, SCRIPT, HELP.

**Example.** Ash Lung spreads by proximity in the Cinder Mines: CON -2, a cough every 5 ticks, 1d4 damage. Prayer_CureDisease cures it at difficulty 12, and dwarves are immune.

<!-- from: data-content/dc-gendisease -->

<a id="p21"></a>

## P21. Scripts on poisons, tweaks and trade skills

*Kind:* data file, script trigger · *Value:* medium · *Effort:* medium · *Decision:* yes / no

**Today.** Only GenAbility, GenTrap and GenCommand take a SCRIPT. Race and class logic already works through effects: a scripted GenAbility listed as a GenRace racial effect (NUMREFF, GETREFF) runs on every mob of the race, players included, and an AUTOINVOKE one given as a level 1 autogain (GETCABLEGAIN) does the same for a class. Crafting sends TYP_ITEMGENERATED, so EXECMSG_PROG ITEMGENERATED sees the made item as $t. GenPoison, GenTweakAbility and the trade skill records have no script, and there is no event for a failed craft.

**Add.** SCRIPT on GenPoison (ONAFFECT, ONTICK, ONCURE), on GenTweakAbility (see the TWEAK item), and on GenCraftSkill and GenGatheringSkill (ONCRAFTED with $o the new item, ONFOUND, ONFAILED). As a convenience, SCRIPT on GenRace and GenCharClass, run as a hidden, unsaved effect.

**Example.** The gathering skill Ember Mining has an ONFOUND that rarely spawns an ember sprite.

*See also:* ST13 (ST13's CRAFT_PROG and P21's ONCRAFTED and ONFAILED skill scripts are alternative hooks on the same crafted or ruined item); JS23 (JS23 says it complements P21's SCRIPT field on GenRace and GenCharClass); P3, P22 (Named script hooks (CANCAST, ONCAST, ONTICK, ONEXPIRE) on GenAbility, GenTweakAbility, GenPoison and trade skills; one hook design).

<!-- from: data-content/dc-generic-scripts-everywhere -->

<a id="p22"></a>

## P22. Masks, effects and scripts on TWEAK abilities

*Kind:* data file · *Value:* medium · *Effort:* medium · *Decision:* yes / no

**Today.** GenTweakAbility renames a Java ability and overrides its triggers, cost, range, cooldown and messages (codes 0-17), with no masks, effects or script. A GenAbility wrapper keeps the Java behaviour (CASTMASK "-LOCALE +OUTDOORS", POSTCASTABILITY "Spell_Lightning" through Prop_SpellAdder), but under a new ID, so class qualifications and scrolls must name the wrapper. Prop_RestrictSpells or a CNCLMSG_PROG can forbid a spell in places.

**Add.** On GenTweakAbility, keeping the original ID: CASTMASK and TARGETMASK (zapper, checked before delegating); POSTCASTABILITY and POSTCASTAFFECT (Prop_SpellAdder syntax, after a successful invoke); a SCRIPT with CANCAST, ONCAST and ONEXPIRE functions; DAMAGEMULT and DURATIONMULT, applied in okMessage to messages whose tool is this ability.

**Example.** Stormcall is a TWEAK of Spell_Lightning: CASTMASK -LOCALE +OUTDOORS, Fighter_Trip cast as well 20% of the time, and a script raising DAMAGEMULT to 1.25 in thunderstorms.

*See also:* P3, P21 (Named script hooks (CANCAST, ONCAST, ONTICK, ONEXPIRE) on GenAbility, GenTweakAbility, GenPoison and trade skills; one hook design).

<!-- from: data-content/dc-tweak-plus -->

<a id="p23"></a>

## P23. Retraining with refunds

*Kind:* property, script command · *Value:* medium · *Effort:* medium · *Decision:* yes / no

**Today.** REMORT, gated by REMORTMASK (say -TATTOOS +RESPEC_TOKEN) with REMORTRETAIN keeping LEVELS, RACE and GENDER, already gives a full respec through real level-ups. Prop_ReRollStats (BONUSPOINTS=, PICKCLASS=TRUE) redoes stats and class once but refunds no trains. A script can MPDISABLE a skill and refund by hand with MPTRAINS or MPPRACS. Nothing records what a purchase cost, so a fair, selective refund cannot be made.

**Add.** PlayerStats records each purchase: ability, expertise or stat, cost type, amount, level. Prop_Respec on an NPC, item or room: WHAT=STATS|SKILLS|EXPERTISES|CLASS|ALL, COST="500 gold" (per skill or whole), ONCE=YES, MASK=. A RESPEC command lists refundable purchases, removes those chosen (autogain skills stay) and refunds trains and practices. MPRESPEC does the same from a script.

**Example.** The Oracle of Mists gives one free full respec after the tutorial island, then charges 500 gold a skill.

<!-- from: character-progression/cp-respec -->

<a id="p24"></a>

## P24. SKILLTREE view with prerequisites and hints

*Kind:* presentation · *Value:* medium · *Effort:* medium · *Decision:* yes / no

**Today.** QUALIFY lists what can be learned now, with its cost, and hides skills with unmet prerequisites. WILLQUALIFY [level] [class] lists by level, hiding SECRET and unmet MASKED skills. Prerequisites appear only in HELP \<skill>, through formatPreRequisites, or when learning fails. Nothing shows the REQUIRES chain, and no field can hint where a skill is taught.

**Add.** A SKILLTREE [class|skill] command: the class's skills grouped by REQUIRES chain, each marked learned (with proficiency), learnable now (with cost), locked (the unmet level, prerequisite or mask, worded as formatPreRequisites does) or hidden. Optional fields in allqualifylist and GenCharClass abilities: HINT="Taught by the hermit of Greyfen", and SHOW=HINT, which shows a masked skill's name and hint without its mask.

**Example.** A ranger sees Tracking > Hunting (50%) > Camouflage (locked: needs Hunting at 50%), and a line "??? - seek the Druid circle in the Fernwood".

<!-- from: character-progression/cp-skill-tree -->

<a id="p25"></a>

## P25. Rank ladders

*Kind:* data file, script command, script function · *Value:* medium · *Effort:* medium · *Decision:* yes / no

**Today.** A rank can already be a tattoo (MPTATTOO), a faction range or a clan position (MPSETCLAN, CLANRANK()), and masks gate skills, Prop_ReqTattoo and shops on it. titles.ini gives titles by mask and achievements give one-time rewards. But AutoAwards effects check their mask only at login, so a promotion's effects wait; faction AFFBEHAV is for mobs only; and no ladder is shown.

**Add.** A world/ranks.ini: LADDER=GUILD NAME="Thieves' Guild" RANKS="Footpad, Cutpurse, Shadow, Master", and per rank REQUIRES=\<mask> or promotion by hand, REWARDS (achievement syntax, once), EFFECTS held while at the rank, and TITLE. The rank is a tattoo (GUILD_2). Add MPRANK $n GUILD +1/-1/=3, RANK($n GUILD), a RANKS command, a RANKUP achievement event, and optional FACTION=id.

**Example.** In a world without levels, the Order of the Lantern unlocks prayers by rank with MASK=+TATTOOS +LANTERN_3. Quests promote with MPRANK; RANKS shows "Lantern-bearer (3/5): next rank needs the Vigil of Ash".

*Re-checked against the code after writing: accurate.*

*See also:* F9 (Alternatives for ranks earned by deeds: a tattoo rank ladder or clan positions with MERIT promotion).

<!-- from: character-progression/cp-rank-ladders -->

<a id="p26"></a>

## P26. Effects for added stats

*Kind:* world option · *Value:* medium · *Effort:* medium · *Decision:* yes / no

**Today.** ADDCHARSTAT adds a stat that is shown, rolled and trainable but does nothing by itself. FORMULA_ keys get fixed positional parameters (getMaxCarry builds a fixed array), and class and race code read the six stats by constant. Scripts read an added stat with STAT($n LUCK), and the IFSTAT mask tests it, but only for equality. AutoAwards can give step effects with such masks, checked at login.

**Add.** @STAT(NAME) and @BASESTAT(NAME) in every FORMULA_ key. STATEFFECT_\<STAT> ini entries mapping points to effects, applied on recover to char stats, phy stats or state, for example STATEFFECT_LUCK="per point over 10: +1 SAVE_GENERAL, +1 CRITCHANCE%". Test and document REPLACECHARSTAT for renaming, so the screens and stats.txt follow.

**Example.** A world adds LUCK: each point over 10 gives +1 to all saves, and a loot script reads it for rare drops. Charisma is renamed Presence.

<!-- from: character-progression/cp-stat-effects -->

<a id="p27"></a>

## P27. Creation review with BACK, and a QUICK NEW

*Kind:* presentation · *Value:* medium · *Effort:* medium · *Decision:* yes / no

**Today.** After the last step, creation goes straight to "Character creation complete!". Each step only confirms itself (Y/n), and the code has no BACK. A mistake two steps back means RETIRE and starting over, or a one-time Prop_ReRollStats (stats and class, PICKCLASS=TRUE) in the start room or on an NPC. The CHARCR* DISABLE flags pick at random for every character, not for one NEW.

**Add.** A REVIEW step showing the sheet: race, gender, stats, class, faction choices, custom answers, kit, start room. BACK \<step> redoes that step and every later step that depends on it; DONE finishes. NEW \<name> QUICK fills each unanswered step at random, or from a template.

**Example.** The review shows "Drow Mage, STR 9 ...". The player types BACK CLASS and becomes a Thief without rolling the stats again.

*See also:* P12 (P27's REVIEW with BACK is the REVIEW step of P12).

<!-- from: character-progression/cp-creation-review -->

<a id="p28"></a>

## P28. EXPDEFER and RPAWARDS timers on game time

*Kind:* world option · *Value:* low · *Effort:* small · *Decision:* yes / no

**Today.** The EXPDEFER "every N mud-hours" timer, on both the timed and the COMMAND paths, and the RPAWARDS interval compare System.currentTimeMillis() with PlayerStats lastXPAward, and the award sets that from the wall clock. Phase 3 moved game logic to core/GameTime but missed these lines. In turn-based or paused play, held XP is released by real time.

**Add.** Use GameTime.now() for lastXPAward and for the deferral checks. The value is already saved with the character (LASTXPAWARD is a PlayerStats code), so only the clock source changes.

**Example.** A turn-based world where experience settles at dawn (EXPDEFER=24 100%) releases it at the in-game dawn, however long the player thinks over each turn.

<!-- from: character-progression/cp-progression-clock -->

<a id="p29"></a>

## P29. Ageing as data

*Kind:* data file, script trigger, world option · *Value:* low · *Effort:* medium · *Decision:* yes / no

**Today.** Age category effects are hard-coded in StdRace.agingAffects, and the ageing diseases in tickAging; GenRace has only the AGING year chart. There are DISABLE=ALL_AGEING, ISBIRTHDAY(), STAT($n AGENAME) and birthday masks such as +BIRTHDAY, so a DAY_PROG with ISBIRTHDAY can mark a birthday. Nothing fires on a change of age category, and starting age comes only from the race chart.

**Add.** A GenRace field AGEEFFECTS="MIDDLEAGED: STR-1 DEX-1 WIS+1; VENERABLE: ..." that replaces the defaults when set. An ini AGEDISEASES= naming the ageing diseases and their odds. An AGE_PROG trigger and an AGECATEGORY achievement event on each birthday and change of category, with $t the category. STARTAGE=\<mask>:\<years> entries.

**Example.** In a long campaign, elves keep their stats until ANCIENT, humans gain 1 WIS at middle age, a veteran origin starts older, and the camp script holds a feast on AGE_PROG.

<!-- from: character-progression/cp-age-data -->

<a id="p30"></a>

## P30. Training that takes game time

*Kind:* world option · *Value:* low · *Effort:* medium · *Decision:* yes / no

**Today.** TRAIN, LEARN and PRACTICE cost only a command's action time. The expertise file's #TIME cost is documented but commented out in ColumbiaUniv. The Scholar's Studying skill learns over several ticks, but skills learned that way cannot be used. A trainer's CNCLMSG_PROG TRAIN can MPFORCE $n WAIT n so turn-based time passes, but the character is not busy and nothing interrupts it.

**Add.** A TRAINTIME= ini setting, per TRAINCOSTS entry and per stat; a TIME="2 hours per level" parameter on teachers (MOBTeacher); and the expertise #TIME field put into effect. While training, the character is busy as when crafting, the clock moves on as with WAIT, and an attack interrupts it.

**Example.** Learning Fireball at the archmage's tower takes three in-game days, while the world and a timed quest move on.

<!-- from: character-progression/cp-training-time -->
