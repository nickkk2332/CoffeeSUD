# Commands, part 6: Import, JConsole, JRun and Inventory

> Part of the CoffeeMud NEWOVERVIEW. Bucket: `b115` (Commands [part 6/12: Import .. JRun]) · 4 files · 8448 lines of Java.

## Summary

Four command objects sit in this bucket. IMPORT is the heavyweight: a 7,600-line loader that reads ROM-style `.are` area text, CircleMUD-style `.zon/.wld/.mob/.obj` sets, CoffeeMud XML (`.cmare`) and zip archives, and turns them into rooms, exits, mobiles, items, shops, socials and reset-driven spawns. JRUN and JCONSOLE are the administrator's JavaScript tools, a file runner and an interactive console, both gated by the `JSCRIPTS` security flag and both built on the Mozilla Rhino engine. INVENTORY is the everyday player listing of loose carried items and coins, and it is also the internal helper that other commands use to show someone else's pack. For a single-player SUD the valuable material is IMPORT's translation tables and conversion rules (ROM flags, sector codes, special-function names, mobile and item templates, reset semantics), which document a working mapping from a legacy world format to data-driven objects. The consoles are a builder's debugging aid, and the account, player and catalogue import, together with the interactive prompts, is multiplayer tooling that a single-player game would mostly drop.

## Key files and types

Paths are relative to the CoffeeMUD folder. Java paths below omit the `com/planet_ink/coffee_mud/` prefix, so `Commands/Import.java:5018` means `com/planet_ink/coffee_mud/Commands/Import.java:5018`. Paths outside this bucket are given in full.

| File or class | Role | Lines |
|---|---|---|
| `Commands/Import.java` | IMPORT command: argument parsing, file dispatch, the ROM/Circle text parser, and builders for rooms, exits, mobiles, items, shops, socials and resets | 7618 |
| `Commands/Import.java` `readBlocks` (2200-2368) | Sorts the raw lines of a text area into named sections (rooms, mobiles, objects, resets, shops, specials, socials, programs) | (in file) |
| `Commands/Import.java` `getMOB` (2621-3736) | Builds a mobile template (`GenMob`, `GenUndead`, `GenRideable`, `GenShopkeeper`) from a `#MOBILES` record | (in file) |
| `Commands/Import.java` `getItem` (3839-4827) | Builds an item template from a `#OBJECTS` record, choosing the `Gen*` class by ROM item type | (in file) |
| `Commands/Import.java` `applyRoomCodeBits` (4829-4986) | Maps room flag bits to room properties and room class changes | (in file) |
| `Commands/Import.java` `executeImporter` (5018-7585) | Main loop: dispatch by file type, room and exit passes, resets, post-processing and save | (in file) |
| `Commands/Import.java` tables | `objDescs` (67-263, 195 material names), `colors` (670-777), `getSpell` (990-2164), `itemTypes` (3933-3967), `weaponTypes` (2372-2388), `secTypes` (6232-6246) | (in file) |
| `Commands/JConsole.java` | JCONSOLE: interactive JavaScript console on a per-player Rhino scope that can call ScriptingEngine commands | 445 |
| `Commands/JRun.java` | JRUN: runs a JavaScript file with positional parameters | 165 |
| `Commands/Inventory.java` | INVENTORY: lists loose carried items and coins; `fetchInventory` and `getInventory` are reused by `Commands/Wealth.java:36` and `Commands/Worth.java:35`, which extend it | 220 |
| `Commands/interfaces/Command.java`, `Commands/StdCommand.java` | Contract and defaults for cost, ordering and security (covered in b170) | 181, 316 |
| `core/CMSecurity.java` | `SecFlag.JSCRIPTS`, the IMPORT flags, `SECURITY_IMPORT_GROUP` (2799-2806), JavaScript approval modes (68-70) | (in file) |
| `Common/DefaultScriptingEngine.java` | Engine behind JConsole's scope; its approval check for embedded scripts is at 9276 | (in file) |
| `Common/interfaces/ScriptingEngine.java` | `funcs` (496) and `methods` (620) name lists exposed to JConsole | (in file) |
| `Abilities/Properties/Prop_HaveAdjuster.java`, `Prop_HaveResister.java`, `Prop_SpellAdder.java` | Text-coded effect holders that IMPORT fills in (stat adjusters, resistances, spell lists) | (in file) |
| `Libraries/Sessions.java` | Idle-kick threshold, which IMPORT and EXPORT lengthen (196-200) | (in file) |
| `Commands/Shell.java` | In-game file shell: the `:` command runs JRUN on a file (64, 804-807) | (in file) |

## How it works

### The four commands at a glance

| Command | Access words | Syntax (from in-game help) | Audience and gate | Orderable | Restriction and cost |
|---|---|---|---|---|---|
| IMPORT | `IMPORT` (`Import.java:49`) | `IMPORT (NOPROMPT) (NODELETE) (AREATYPE=TYPE) [FILE NAME(S)]` (`resources/help/arc_help.ini:1561-1568`) | Builder or admin. Command-level check is "any flag in `SECURITY_IMPORT_GROUP`" (`Import.java:7614-7616`, `core/CMSecurity.java:2799-2806`) | Yes (`Import.java:7607-7611`) | Each XML branch checks one flag (see below). Action cost is the base default, 0.0 unless configured (`Commands/StdCommand.java:224-227`, `core/CMProps.java:1143-1150`) |
| JCONSOLE | `JCONSOLE` (`JConsole.java:52`) | `JCONSOLE [statement]` (`arc_help.ini:350-362`) | Admin. `JSCRIPTS` flag (`JConsole.java:440-444`) | No (`JConsole.java:434-438`) | Base action cost |
| JRUN | `JRUN` (`JRun.java:44`) | `JRUN [FILENAME] [PARM1] ...` (`arc_help.ini:2244-2250`) | Admin. `JSCRIPTS` flag (`JRun.java:153-157`). Also reached from the in-game file shell `:` (`Commands/Shell.java:64`, `804-807`) | No (`JRun.java:147-151`) | Base action cost |
| INVENTORY | `INVENTORY`, `INV`, `I` (`Inventory.java:41`) | `INVENTORY [LONG] [mask]` (`Inventory.java:188-195`) | Player | Yes (`Inventory.java:215-219`) | Base action cost |

`canBeOrdered` means that a group leader or charmer may order a follower to run the command (`Commands/interfaces/Command.java:97-101`). None of the four overrides `actionsCost`, so each runs at the configured default.

### IMPORT: arguments and dispatch

`executeImporter` (`Import.java:5018`) works in these steps.

1. Flags are removed from the argument list. `nodelete` sets `nodelete` (`Import.java:5050-5055`), `noprompt` sets `prompt=false` (`5057-5062`), and `areatype=NAME` must name a known area type or the command stops with "Unknown area type" (`5064-5073`).
2. A trailing `Physical` object is taken as the target (`5040-5041`). Room, mobile and item imports put their results there (`5645-5648`, `5846-5865`).
3. If the last argument is a `StringBuffer`, the command runs in compile mode with no session. Errors are appended to the command list through `returnAnError` (`5084-5088`, `966-976`).
4. Directories expand to their files (`5096-5107`).
5. Each file is dispatched by extension or by sniffing its first 20 characters.
   - `.zip`, and `ZipInputStream` entries, are unpacked and queued (`5146-5191`).
   - `.zon`, `.wld`, `.mob`, `.obj`, and the aliases `.shp` and `.doc`, are merged into one synthetic buffer with `#AREA`, `#ROOMS`, `#MOBILES`, `#OBJECTS` and `#RESETS` sections (`5192-5245`). The shop file `.shp` is never read, because `shp` appears only as an alias at `5196`.
   - `.lst` is a list of further files, which are queued (`5258-5289`).
   - XML is recognised by tag: `<AREAS>` (`5290`), `<AREA>` (`5396`), `<AROOM>` (`5450`), `<CATALOG` (`5515`), `<MOBS>` (`5618`), `<CUSTOM>` (`5663`), `<PLAYERS>` or `<ACCOUNTS>` (`5677`), and `<ITEMS>` (`5820`). These branches check `IMPORTROOMS`, `IMPORTMOBS`, `IMPORTITEMS`, `IMPORTPLAYERS` or `CATALOG` as appropriate (`5292`, `5398`, `5454`, `5517-5518`, `5620`, `5679`, `5822`).
   - Anything else is a ROM-style text area. That path has no per-file permission check. The only `CMSecurity` reference between 5879 and 7584 is a debug-flag test at `6474`, so the command-level check is the only gate.

Player-visible messages in this flow include "Import what? Specify the path/filename!" (`5094`), "File not found at" (`5252`), "Found area: ... is this ok?" (`6121`), "Area: ... exists, obliterate first?" (`6102-6103`, default N), "Loading and Linking Rooms..." (`6129`), "Loading objects.." (`6848`), "Resets..." (`7449`), "Saving all areas imported..." (`7487`), and the closing "A good room to look at would be ..." (`7462`).

### IMPORT: the ROM text pipeline

**Sectioning.** `readBlocks` (`Import.java:2200-2368`) walks the lines and sends each to a section list. A line starting with `#` is a section marker, and a numeric `#vnum` inside a section opens a new record (`2234`, `2328-2335`).

| Marker (prefix) | Destination | Lines |
|---|---|---|
| `AREA`, `AUTHOR` | area header | 2240-2248 |
| `MOBILE`, `MOBOLD` | mobile records | 2250-2255 |
| `OBJECT`, `OBJOLD`, `OBJ24B3` | object records | 2257-2263 |
| `ROOM` (not `ROOMPROG`) | a new room record | 2298-2302 |
| `MOBPROG`, `ROOMPROG` | program text | 2286-2296 |
| `RESETS`, `SHOP`, `SPECIALS`, `SOCIALS` | reset, shop, special and social text | 2304-2326 |
| `OLIMITS`, `OMPROGS`, `ECONOMY`, `RANGES`, `CLIMATE`, `RESETMSG`, `VERSION`, `CONTINENT`, `COORDS`, `RESETFREQUENCY`, `VNUMS`, `FLAGS`, `REPAIRS`, `OBJFUNS`, `HELPS`, `PRACTICERS` | discarded, counted as "Ate N unsupported lines" | 2265-2284, 2363-2366 |

**Socials.** Socials are converted before rooms (`Import.java:5887-6036`). Each social record is a name line followed by eight message lines, covering the bare verb (you see, others see), the verb with a target (you see, others see, target sees, no target sees) and the self form (you see, others see). The importer prompts before it changes an existing social, and it calls `CMLib.socials().save(mob)` afterwards (`6026-6030`).

**Area name and author.** `getAreaName` (`Import.java:265-358`) accepts several header styles, including `#AREA` lines, `NAME` lines and `{...}` brace forms, and removes colour codes. `getAreaAuthor` (`529-668`) takes the first of `#AUTHOR`, `BUILDERS`, `NAME`, `#AREA`, `O `, `CREDITS`, or a bracketed name, and skips `NONE`.

**Existing area.** If an area with the same name already exists, the importer asks, defaulting to N (`6102-6103`). On yes it calls `temporarilyDeleteArea` (`442-527`). That method records the incoming exits from other areas (`451-464`), empties and deletes the area's rooms, and deletes the area record. The records are re-linked after the import (`processRoomRelinks`, `408-440`, called at `7451`). Refusing returns without changes.

**Pass 1: rooms** (`Import.java:6139-6483`).
- The area is created with `StdArea` or the given area type, or frozen if it already exists (`6160-6173`).
- Room IDs are `AreaName#vnum`, built at `6228` (`R.setRoomID(areaName+R.roomID())`). Keys are therefore unique across areas.
- Room text is the title and description (`6190-6203`). An empty room is the pair `~` and `~`, which becomes "Emptiness..." (`6190-6198`).
- The sector code picks a room class (`6277-6459`, table below). `applyRoomCodeBits` then adds flag properties (`6461`).

**Pass 2: exits and extras** (`6489-6844`). See "Exits and doors" below. Extra descriptions (`E`) become `GenWallpaper` items on the room (`6515-6527`). Ordinary `C` codes call `applyRoomCodeBits` again (`6785-6795`). Program lines (`>`) are read and then discarded, with the comment "nothing done with the script. :(" (`6803-6821`).

**Resets** (`6846-7309`). See "Reset rules" below. Mobiles and items are built on demand by `getMOB` and `getItem`, which cache converted prototypes by vnum in `areaMOBS`, `doneMOBS`, `areaItems` and `doneItems` (`2635-2668`, `3852-3865`).

**Post-processing.**
- Objects and mobiles that no reset used go into a hidden room named "Unused Objects" (`Import.java:7312-7379`).
- A pet shop is a room flagged as a pet store. Its pets are moved into the stock of the shopkeeper in the next room, at quantity 20 (`7381-7416`).
- A "smurfy wells" pass (`7417-7447`) merges an item with no display text into a sibling whose display text names it, by appending the first item's description to the second.

**Save and activate** (`7486-7583`). Each room is saved with `DBCreateRoom`, `DBUpdateExits`, `DBUpdateMOBs` and `DBUpdateItems` (`7491-7515`). Plain "the ground" exits are rewritten as `OpenDescriptable` exits that carry the display text (`7493-7511`). Cross-area links recorded in `laterLinks` are resolved (`7524-7569`). Every area is set `ACTIVE` and its room list is refilled (`7571-7581`).

### Exits and doors

Exits come from `D` lines in pass 2 (`Import.java:6530-6584`). The first number is a ROM direction code, mapped as follows.

| ROM code | Direction | ROM code | Direction |
|---|---|---|---|
| 0 | north | 6 | northeast |
| 1 | east | 7 | northwest |
| 2 | south | 8 | southeast |
| 3 | west | 9 | southwest |
| 4 | up | 10 | gate |
| 5 | down | | |

Each exit has a flag word (`exitFlag`, masked with `& 31`, `6620`) and a door state (`doorState`, `6621`). The door logic is in `6652-6679`.
- Flag 1, or 6, or 3 with five bits, makes a door that defaults closed (`6652-6663`). Flag 6 also sets exit level 100.
- A positive door state makes a door that defaults closed. A state above 1 adds a lock, with key name `AreaName#state` (`6664-6676`).
- `setDoorsNLocks` is then called with the derived flags (`6678-6679`).
- A `SECRET` exit name becomes "secret door" and sets `IS_HIDDEN` (`6683-6688`).

Links to rooms not yet loaded are kept as temporary door links of the form `#vnum` (`6754`). They are resolved in the same pass when possible (`6709-6748`), or later through `laterLinks` (`6766`, `7524-7569`).

### Mobiles: `getMOB`

**Identity and race** (`Import.java:2699-2751`). The importer reads the simple name, name, display text and description, then the race. The race lookup tries an exact `CMClass.getRace`, then `findRace`, then prefix, suffix and substring matches, and falls back to `StdRace` (`2703-2751`).

**Class choice** (`2781-2829`).
- `GenUndead` if the act flag at position 14 is set, or the name contains a keyword such as GHOUL, SKELETON, ZOMBIE, VAMPIRE, LICH, MUMMY, GHOST or GEIST (`2783-2793`).
- `GenRideable` if the simple name contains HORSE (`2795-2796`).
- `GenShopkeeper` if a `#SHOPS` record names this vnum. The sold categories come from the buy-type numbers (`2807-2823`):
  - all three types 5-7 gives `DEAL_WEAPONS`;
  - types drawn from 2-4, 10 and 26 give `DEAL_MAGIC`;
  - types 0 and 9 give `DEAL_ARMOR`;
  - a name containing LEATHER gives `DEAL_LEATHER`, and "PET " or "PETS " gives `DEAL_PETS`;
  - otherwise `DEAL_INVENTORYONLY`.
- Otherwise `GenMob`.

**Alignment.** The ROM alignment is halved and offset: `setAlignmentOldRange(M, 500+aliFlag)`, where `aliFlag` is the rounded half of the value (`2832-2835`).

**Act flags to behaviours** (`2840-2893`). Bit numbers are ROM flag-letter positions, counted from 0.

| Bit | Behaviour or effect |
|---|---|
| 1 | no `Mobile` behaviour (the mobile stays put) |
| 2 | `Scavenger` |
| 4 | `Prop_Invisibility` |
| 5 | `MobileAggressive`, and with bit 1 also `Aggressive` |
| 7 | wimpy: the wimp point is computed later |
| 9 | `Prop_StatTrainer` |
| 10, 27 | `MOBTeacher` |
| 11, 19 | `Fighterness` |
| 12, 13, 17 | `Mageness` |
| 14 | undead: race becomes `Undead` if it was `Human` or `StdRace` (`2871-2879`) |
| 16 | `Clericness` |
| 18 | `Thiefness` |
| 26 | `Healer` |

**Affect flags** (`2894-2993`) become sense masks (`CAN_NOT_SEE`, `CAN_SEE_INVISIBLE`, `CAN_SEE_INFRARED`, `CAN_SEE_DARK`) or effect objects. Examples are `Prayer_Sanctuary` (bit 7), `Prop_Hidden` (16), `Spell_Sleep` (17), `Spell_Charm` (18), `Spell_Haste` (21), `Prayer_Plague` (23), `Spell_Weaken` (24), `Fighter_Berzerk` (26), `Regeneration` (28) and `Spell_Slow` (29). Bit 15 gives `Thief_Sneak` at proficiency 100 (`2946-2951`).

**Level and HP** (`2996-3010`).
- Level is the first number of the second record line. A zero level becomes 1.
- Base HP comes from `getDRoll`, which returns `(roll*dice)+plus` for a dice string such as `NdM+K` (`Import.java:2590-2618`). The code then computes `baseHP=(baseHP-3)/level-1` and stores it in `basePhyStats().setAbility`. The wimp point is `round(level*(MobHPBase+ability)/8.0)+1` (`3059-3060`).
- Armour, attack and damage are not read from the file. They come from the leveller by level (`3062-3064`, `CMLib.leveler().getLevelMOBArmor`, `getLevelAttack`, `getLevelMOBDamage`).
- Gold is `roll(1,level)+roll(1,10)` in ROM mode (`3068`). In circle mode it is read from the file (`3066`).
- Weight is fixed at 50 (`3069`).

**Position and sex** (`3071-3096`). Position codes 1-4 set `IS_SLEEPING`, and 5-6 set `IS_SITTING`. Sex code 2 gives gender `F`, 3 gives `N`, and anything else gives `M`. Circle format accepts words such as STAND, SIT, SLEEP, MALE, FEMALE and EITHER. `EITHER` is resolved by a 50/50 roll (`3023-3024`).

**Mudchat.** For mobiles whose second record line has at least four fields, a roll above 75 (`3046`) adds `MudChat`. The roll is made at import time, so it differs between imports.

**Circle-only blocks** (`3098-3236`). Off-flags add skills and behaviours. For example, bit 2 gives `Skill_Trip`, bit 3 `Fighter_Berzerk`, bit 5 `Skill_Dodge`, bit 8 `Fighter_Kick`, bit 14 `Fighter_Whomp`, bit 22 `Fighter_Sweep`, bit 19 a guardian behaviour plus `AntiVagrant`, and bit 20 `BrotherHelper`. Resistance and immunity masks become `Prop_Resistance` text, one word per damage type, with a percentage of 10 plus the level (`3192-3234`, percentage at `3233`).

**Scripts** (`3238-3419`). Mobile programs written inline (`>` lines) are joined into a `Scriptable` behaviour's parameter string (`3414-3419`). The importer recognises eight named program files and maps them directly (`3320-3347`): `JANITOR.PRG` to `Scavenger`, `VAGABOND.PRG` to `Vagrant`, `DRUNK.PRG` to the `Inebriation` effect, `BEGGAR.PRG` to `Beggar`, `GATEGRD` and `GATEGRD2` to `GateGuard`, and `MID_CIT` and `CRIER` to nothing. Any other program file is loaded from disk next to the area file (`3356-3361`).

**Specials** (`3421-3696`). About 53 `SPEC_` names are mapped. A selection:

| Special | Behaviour or ability added |
|---|---|
| `SPEC_CAST_MAGE`, `SPEC_WANDERER` | `Mageness` |
| `SPEC_CAST_SENESCHAL` | `CombatAbilities` plus Blindness, DispelMagic, Weaken, AcidArrow, Fireball, AcidFog, Lightning, WeakEnergyDrain, Plague |
| `SPEC_CAST_BEHOLDER`, `SPEC_CAST_PSIONICIST`, `SPEC_CAST_GHOST`, `SPEC_UNDEAD` | `CombatAbilities` plus a list of spells |
| `SPEC_THIEF` | `Thiefness` |
| `SPEC_TEACHER` | `MOBTeacher` |
| `SPEC_HEALER`, `SPEC_CAST_ADEPT` | `Healer` |
| `SPEC_REPAIRMAN` | `ItemMender` |
| `SPEC_EXECUTIONER` | `GoodExecutioner` |
| `SPEC_ASSASSIN*` | `Aggressive` with `MOBKILLER` parameters |
| `SPEC_GUARD`, `SPEC_GUARD_WHITE`, `SPEC_POLICEMAN`, `SPEC_SPECIAL_GUARD` | `GoodGuardian` |
| `SPEC_FIDO` | `CorpseEater` |
| `SPEC_MAYOR`, `SPEC_CAPTAIN` | `MudChat` |
| `SPEC_JANITOR`, `SPEC_SMART` | `Scavenger` |
| `SPEC_BREATH_ANY` | `CombatAbilities` plus `Dragonbreath` (`3591-3595`) |
| `SPEC_BREATH_ACID`, `_FIRE`, `_FROST`, `_GAS`, `_LIGHTNING` | `CombatAbilities` plus the matching breath ability (`3628-3662`) |
| `SPEC_OGRE_MEMBER`, `SPEC_TROLL_MEMBER` | `ROMGangMember` with parameter Ogre or Troll |
| `SPEC_PATROLMAN` | `ROMPatrolman` |
| `SPEC_TAX_MAN` | `RandomTeleporter` plus `Thiefness` |
| `SPEC_SNAKE_CHARM`, `SPEC_KUNGFU_POISON`, `SPEC_TAXIDERMIST`, `SPEC_DEATH_RAIN` | logged "Unsupported spec" (`3489`, `3494`, `3499`, `3515`) |
| `SPEC_REWIELD`, `SPEC_WIZARDOFOZ`, `SPEC_VAMP_HUNTER`, `SPEC_MINO_GUARD` | accepted silently, with the comment "who knows?" (`3603-3609`) |
| `SPEC_CAST_BIGTIME` | nothing, with the comment "In CoffeeMud, they all cast bigtime" (`3623-3626`) |
| any other | error "Unknown mob special" (`3687`) |

**Finishing** (`3697-3733`). Abilities receive `autoInvocation` (`3697-3702`). Rejuvenation is `2*TPM + TPM*level/2` ticks, capped at `30*TPM`, where TPM is ticks per minute (`3703-3705`). A mobile whose display text contains "MONEY CHANGER" gets `MoneyChanger` (`3707-3708`). If a mobile has `CombatAbilities` and also another behaviour whose superclass ends in `CombatAbilities`, the generic one is removed (`3709-3723`). The mobile is recovered, reset to full state and cached (`3724-3733`).

### Items: `getItem` and helpers

**Type mapping** (`Import.java:3933-3967`, then the switch at `4022-4279`). The ROM item-type number, or a circle type name, selects a class.

| ROM type | Name | Class |
|---|---|---|
| 1 | light | `GenLantern` if the name contains LANTERN, else `GenLightSource`. Duration is `val3*80` (`4024-4029`) |
| 2 | scroll | `GenScroll`, uses 3, up to three spells (`4031-4035`) |
| 3 | wand | `GenWand`, charges from value 2, one spell (`4037-4041`) |
| 4 | staff | `GenStaff`, a weapon with a spell (`4043-4049`) |
| 5, 6, 7 | weapon | `GenWeapon` via `doWeapon`, wear adjuster (`4050-4064`) |
| 8, 14, 16, 21, 27, 29 | treasure and miscellaneous | `GenItem` (`4065-4067`, `4096-4098`, `4137-4139`, `4211-4213`, `4234-4236`, `4241-4243`) |
| 23, 24, 99 | corpses | `GenCorpse` (`4217-4222`, `4271-4272`) |
| 9 | armour | `GenShield` if the name contains SHIELD, else `GenArmor` (`4068-4075`) |
| 10 | potion | `GenPotion` with up to three spells (`4076-4080`) |
| 11 | clothing | `GenArmor` with armour 0 (`4081-4085`) |
| 12, 13 | furniture, trash | `GenItem`, or `GenReadable` if the name suggests writing (`4086-4095`) |
| 15 | container | `GenContainer`, or `GenArmor` for belts, bandoliers and sheaths (`4099-4136`) |
| 17 | drink | `GenDrink` (`4141-4191`) |
| 18, 98 | key, room key | `GenKey` with key name `AreaName + objectID` (`4193-4195`, `4267-4270`) |
| 19 | food | `GenFood`. Poisoned food becomes `GenPill` with "Poison;" (`4196-4204`) |
| 20 | money | `StdCoins` (`4205-4210`) |
| 22 | boat | `GenBoat` (`4214-4216`) |
| 25 | fountain | `GenWater`, not gettable, liquid `Integer.MAX_VALUE-5000` (`4223-4228`) |
| 26 | pill | `GenPill` with up to three spells (`4229-4233`) |
| 28 | map | `GenReadable` (`4237-4240`) |
| 97 | portal | `GenPortal`; the target room ID goes in readable text (`4244-4266`) |
| -1 | wallpaper | `GenWallpaper` (`4274-4276`) |

Circle type names (`3933-3967`) map to the same numbers (for example `container` to 15, `drink` to 17, `npc_corpse` to 99). The `jukebox` type is skipped entirely (`3971-3972`, "NO JUKE BOXES!").

**Weapons** (`doWeapon`, `Import.java:2370-2588`).
- The weapon class comes from a type word, then from the name if that gives 0, so `exotic` maps to 0 (`2373`, `2389-2410`).
- Class 0 is ranged. A name containing BOW gets ammunition of 20 arrows, range 1-3, and logical-AND wielding (`2415-2427`).
- Damage is `val2*val3` when `val2` is at least 1, else `val3` (`2469-2472`).
- Damage type comes from a damage word (for example POUND to 7, STAB to 2, CHOP to 25), then from attack-type numbers to `TYPE_BASHING`, `TYPE_BURNING`, `TYPE_NATURAL`, `TYPE_PIERCING`, `TYPE_SLASHING` or `TYPE_BURSTING` (`2473-2587`).

**Armour** (`4073`). `armor = round((v1+v2+v3+v4+1)/4.0) + 1`, using the four value fields.

**Containers** (`4106-4135`). Value 1 is capacity. Value 2 bit flags give a lid (1), a pickproof lock at level 100 (2), closed (4) and a locked lock (8). The key name is `AreaName#v3`.

**Drinks** (`4143-4190`). Alcoholic liquids receive effects `Poison_Beer`, `Poison_Firebreather`, `Poison_Liquor` and so on, chosen by keyword. Liquid held is `v1*10` for these and `v1*30` otherwise.

**Wear and extra flags** (`4282-4480`).
- Wear bits: 0 means not gettable when clear (`4282-4283`); 1 finger; 2 neck; 3 torso; 4 head; 5 legs; 6 feet; 7 hands; 8 arms; 9 held; 10 about body; 11 waist; 12 wrists; 13 wield; 14 held; 15 two-handed (logical AND); 17 ears; 18 ankles, which is feet.
- Extra bits: 0 glowing; 2 dark; 4 evil; 5 invisible; 6 bonus; 7 not droppable; 8 `Prayer_Bless`; 9, 10, 11 `Prop_WearZapper` masks for good, evil or neutral; 12 not removable; 14 not gettable; 16 invisible; 17 good; 18 glass instead of metal; 20 unlocatable.

**Material** (`4377-4386`). The optional text block (`objectDescription`, read at `3896-3898`) is matched against `objDescs` to set a material. Cloth or paper rings become silver (`4389-4395`).

**Effect strings.** The ROM `A` and `F` codes do not create effects directly. They write short strings into one of three generic holders (`adjuster`, `caster`, `resister`), which are attached to the item at `4805-4810`:
- `adjuster` gets stat terms such as " str+2", " armor+5", " attack+10", " damage+3", " mana+", " hit+" and " move+" (`applyItemApplyCode`, `Import.java:3742-3803`). `Prop_HaveAdjuster` reads those by substring at word starts through `CMParms.getParmPlus` (`core/CMParms.java:2761`, `Abilities/Properties/Prop_HaveAdjuster.java:1235-1287`).
- `resister` gets words such as " fire 25%" from the `F` codes (`4704-4707`), and " magic N%" from ROM value 24 where N is twice the negated value (`3813`). `Prop_HaveResister.setMiscText` parses such tokens (`Prop_HaveResister.java:88-104`).
- `caster` gets a semicolon-separated list of ability IDs such as `Prayer_Curse;Poison;` (`3818-3834`, `4626-4675`). `Prop_SpellAdder` splits it with `CMParms.parseSemicolons` (`Abilities/Properties/Prop_SpellAdder.java:134`).

Wearable items use `Prop_WearAdjuster`, `Prop_WearResister` and `Prop_WearSpellCast` instead (`4323-4347`).

**Level, weight and value** (`4348-4363`). Three numeric fields give level, weight and base value. Weight is the value divided by 10 (minimum 1), but multiplied by 10 for rideables (`4352-4356`). A single field is only the level.

**Tail codes** (`4486-4804`). Free-form lines after the record are handled per code: `E` (extra description, becomes item description or readable text, `4489-4521`), `A` and `APPLY` (`applyItemApplyCode`), `F` (resist, sense and caster bits, `4565-4680`), `>` (scripts, discarded in effect, `4721-4741`), `X` (a sounder behaviour, `4533-4553`), and `EXTRA` (`4770-4778`). Unknown codes are reported as "Unknown code for item" (`4801-4802`).

### Rooms: `applyRoomCodeBits`

`Import.java:4829-4986`. The ROM and circle bit layouts differ, and circle format is flagged by `circleFormat`.

Bits that apply to both formats:

| Bit | Effect |
|---|---|
| 0 | dark room (`Prop_RoomDark`, `4840-4841`) |
| 2 | no mobiles (`Prop_ReqNoMOB`, `4846-4847`) |
| 3 | indoors: room class becomes `StoneRoom` (`4837-4838`) |
| 21 | underwater: room class becomes `UnderWater` (`4834-4835`) |

ROM-format bits (`4911-4984`):

| Bit | Effect |
|---|---|
| 4 | no summon out (`Prop_NoSummon`) |
| 9 | capacity 2 (`4916-4921`) |
| 10 | no fighting (`Prop_PeaceMaker`) |
| 11 | capacity 1, solitaire (`4925-4930`) |
| 12 | pet shop: the next room's shopkeeper takes the pets (`4931-4938`) |
| 13 | no recall |
| 14 | level SYSOP (`4943-4948`) |
| 15 | level `>=93` |
| 16 | level `>=91` |
| 17 | level `<=5` |
| 18 | no summon and no charm |
| 19 | player-killing required |
| 20 | no teleport out |
| 24 | no channel |

Circle-format bits (`4851-4910`):

| Bit | Effect |
|---|---|
| 4 | peacemaker (`Prop_Peacemaker`) |
| 5 | quiet (`Prop_NoChannel`) |
| 7 | no magic (`Prop_MagicFreedom`) |
| 8 | solitaire: capacity 1 (`4864-4868`) |
| 9 | no teleport in (`Prop_NoTeleport`) |
| 10 | level SYSOP |
| 16 | player-killing required |
| 17 | no recall |

Format is chosen by `circleFormat`, which is set from the number of bits in the room's code line (`6248-6261`).

### Reset rules

The `#RESETS` section (`Import.java:6849-7309`) drives spawning. The letters, and what the code does with each, are:

| Letter | Meaning in the code | Lines |
|---|---|---|
| `M` | bring a mobile to life in a room (mobile ID in field 2, room in field 4; the max-count field is not read) | 6867-6898 |
| `G` | give an item to the last mobile. For a shopkeeper it becomes stock with quantity from field 3 (negative means 100); light sources bring 2x oil flasks, readable items bring parchment and a journal priced 250, and weapon shops bring 20 arrows at value 50 | 6901-6988 (shop stock 6926-6969) |
| `EC` | give and wear an item on a mobile already in a named room | 6990-7036 |
| `E` | give and wear an item on the last mobile | 7038-7080 |
| `O` | place an item in a room. A gettable item's rejuvenation is `round(60000/TickMillis*4)` times its level, which is 60 times level at the default 4-second tick (`Import.java:7113-7114`) | 7082-7124 |
| `P` | put an item in a container already placed. Gettable items get rejuvenation 1000 (`7169`) | 7126-7188 |
| `D` | set a door: 0 open, 1 closed, 2 closed and locked (`7258-7284`). Diagonal codes 6-9 pick the highest-numbered exit with no door, a heuristic (`7228-7234`) | 7190-7294 |
| `R`, `*`, `S`, `#RE` | ignored | 7296-7307, 6863-6865 |

Resets that name an object or mobile not yet built are kept for later and retried when multiple areas are loaded (`nextResetData`, `6874-6890`, `7477-7484`).

### Spell and skill names

`getSpell` (`Import.java:990-2164`) converts a ROM spell name or number to a CoffeeMud ability ID. It has two steps.

1. **Name chain** (`Import.java:993-1453`). Roughly 150 name prefixes are tested with `startsWith` on the upper-case word, and each assigns a numeric slot. Words under three letters return "" (`997-998`). An unknown word is logged as "Unknown spell" (`1450`).
2. **Slot switch** (`Import.java:1455-2164`). About 243 slot numbers return a CoffeeMud ability ID, and about 104 return "" because there is no equivalent. Unknown slots are logged "Unknown spell num" (`2163-2164`). Samples: slot 3 gives `Prayer_Bless`, 26 `Spell_Fireball`, 28 `Prayer_Heal`, 30 `Spell_Lightning`, 33 `Thief_Poison`, 53 `Spell_IdentifyObject`, 56 `Spell_Fly`, 70 `Spell_AcidArrow` (comment "acid blast", `1587-1588`), and 200 `Acidbreath` (`1677-1678`).

### Socials

Each social record is a name line and eight message lines, converted by `socialFix` (`Import.java:5000-5015`). The ROM tokens are rewritten to CoffeeMud's pronoun tokens, and a backtick becomes a plain quote.

| ROM token | Replacement |
|---|---|
| `$n` | `<S-NAME>` |
| `$N` | `<T-NAMESELF>` |
| `$m` | `<S-HIM-HER>` |
| `$M` | `<T-HIM-HER>` |
| `$s` | `<S-HIS-HER>` |
| `$S` | `<T-HIS-HER>` |
| `$e` | `<S-HE-SHE>` |
| `$E` | `<T-HE-SHE>` |

Each social creates three `Social` objects: the bare verb, the verb with a target (`word <T-NAME>`), and the self-target form (`word SELF`) (`5900-5902`, `5942-5999`).

### JRUN

`JRun.execute` (`JRun.java:52-86`).
- With fewer than two words it prints the usage line "jrun filename1 parm1 parm2 ..." (`55-59`).
- It removes the access word, then reads the file through `CMFile`, which resolves paths in the CoffeeMud virtual file system. A missing file gives "File '...' could not be found." (`62-68`).
- It creates a Rhino context and a `JScriptWindow` scope that holds the MOB and the remaining words (`70-78`). The scope exposes `mob()`, `numParms()`, `getParm(i)`, `getParms()`, `toJavaString` and `getCMType` (`118`, `99-144`).
- The script is evaluated as `<cmd>` at line 1 (`78`). An exception prints "JavaScript error: ..." (`80-83`).
- The security check is `CMSecurity.isAllowed(mob, location, JSCRIPTS)` (`153-157`). The command is not orderable (`147-151`).

Two points matter for safety. Rhino's standard objects are installed (`74`), and the MOB object is handed to the script (`99-102`), so the script can call any Java method reachable from it. The approval table that `DefaultScriptingEngine` checks before running embedded scripts (`Common/DefaultScriptingEngine.java:9276`, `core/CMSecurity.java:1020-1085`) is not consulted here.

### JCONSOLE

`JConsole.execute` (`JConsole.java:62-187`).
- A one-line form evaluates its argument immediately and prints the result, then returns (`93-105`).
- Otherwise it installs an input prompt. The prompt is `>`, or `.` while in add mode (`113-117`). Typing `exit` ends it (`127-128`). A line of just `<` starts a block, and `>` ends it (`129-148`). If the client supports the GMCP `Siplet.Input` extension, the block is edited in a client-side text box titled "JConsole JavaScript" (`132-137`, `149-156`).
- Backticks in input become single quotes (`165`).
- The console's scope is cached in `Resources` under `JCONSOLE_<name>` (`73`, `85`). The help text says it does not survive a reboot (`arc_help.ini:355-357`).
- `JScriptEvent.get` (`JConsole.java:231-298`) makes every name in `ScriptingEngine.methods` (`Common/interfaces/ScriptingEngine.java:620`) callable. The call becomes a one-line mob-program command (`246-254`, `255-297`), and the names in `ScriptingEngine.funcs` (`:496`) are evaluated as functions. A trailing `$` requests the functional result (`299-313`).
- `getVar` and `setVar` access the engine's variable store (`206-214`).
- Security is the `JSCRIPTS` flag (`440-444`), and the command is not orderable (`434-438`).

### INVENTORY

`Inventory.execute` (`Inventory.java:183-202`) strips a leading `long` (`188-194`), joins the rest as a filter (`195`), and prints "You are carrying:" followed by the list, or "Nothing!" (`197-200`). A monster gets no list output (`199`).

`fetchInventory` (`58-99`) takes only items at the top level of the pack: the item must have no container and must be in inventory position (`69-70`). Worn items and the contents of bags are not listed. Each carried item is split into one of two groups.
- Coins with a non-zero denomination are grouped by currency and kept in ascending denomination order (`76-95`).
- Everything else goes into the ordinary view list (`76-77`).

Visibility is checked for each item (`72-75`). If the pack has items but the viewer can see none, the view is cleared and the line "(stuff you can't see right now)" is shown instead (`137-143`, `175`). A filter word is applied as `ALL` plus the words through `CMLib.english().fetchEnvironmental` (`145-163`). Coins are printed with `CMLib.beanCounter().abbreviatedPrice` under a "Money:" header (`101-131`).

`executeInternal` (`204-213`) takes a seer and returns the listing as text for a different character. `CommonMsgs.getInventory` forces the internal `Inventory` command for that purpose (`Libraries/CommonMsgs.java:295-298`). `Commands/Stat.java:940` and `Abilities/Thief/Thief_Peek.java:167` call it through `CMLib.commands().getInventory`.

### Interactions with other systems

- **Database.** Every converted room, exit, mobile and item is written through `CMLib.database()` (`Import.java:6170`, `7491-7515`, `6744`).
- **Map.** Areas and rooms are registered with `CMLib.map()` (`6169`, `7579-7580`), and cross-area links use `CMLib.map().getRoom` (`6720`, `402-405`).
- **Scripting.** Mobile programs are stored as `Scriptable` behaviour text (`3414-3419`). Room programs are read and then discarded (`6803-6821`). JCONSOLE runs its commands through `DefaultScriptingEngine` (`JConsole.java:427`).
- **Factions and leveller.** Alignment goes through `CMLib.factions()` (`2835`). Combat numbers come from `CMLib.leveler()` (`3062-3064`).
- **Socials.** Imported socials go through `CMLib.socials()` (`5900-6029`).
- **Catalog, players, accounts, achievements and channels.** The XML branches call into `CMLib.catalog()` (`5553-5585`), `CMLib.players()` and `CMLib.database()` for accounts and characters (`5744-5812`), `CMLib.achievements()` (`5791-5793`), and `CMLib.channels()` to announce new players (`5799-5801`).
- **Idle timeout.** `Libraries/Sessions.java:186-203` multiplies the idle threshold by 600 while the last command was IMPORT, EXPORT, CHARGEN or MERGE, so a builder mid-import is not kicked. The base kick threshold is 10 minutes (60,000 ms times 10), and the multiplier raises it to 100 hours.

## Content worth mining

**Mobile act flags to behaviours**, from `getMOB` (`Import.java:2840-2893`). This is the table in "How it works", and it is a clean list of NPC roles: sentinel, scavenger, aggressive, teacher, trainer, healer, thief and the caster classes.

**Special functions** (`Import.java:3421-3696`). Fifty-three named specials, each a role for a non-player character. The useful ones for a text game are guard, healer, teacher, thief, shopkeeper, janitor, wanderer, the breath and spell-caster archetypes, and the gang members (`ROMGangMember` with an Ogre or Troll parameter, `3670-3682`).

**Item classes by ROM type** (`Import.java:4022-4279`). The 31 type names and numbers in `itemTypes` (`3933-3967`) map onto about twenty `Gen*` classes. This is a practical catalogue of what a legacy world contains.

**Weapon classes** (`Import.java:2372-2388`).

| Weapon word | Class number | Class |
|---|---|---|
| exotic, bow | 0 | ranged |
| sword | 1 | sword |
| dagger | 9 | dagger |
| spear, arrow, lance | 3 | polearm |
| staff | 10 | staff |
| mace | 4 | blunt |
| axe | 5 | axe |
| flail, whip | 6, 7 | flailed |
| polearm | 8 | polearm |

**Room sectors** (`Import.java:6232-6459`). ROM sector names map to room classes.

| Sector | Outdoor class | Indoor class |
|---|---|---|
| inside, city | `CityStreet` | `StoneRoom` |
| field | `Plains` | `WoodRoom` |
| forest | `Woods` | `WoodRoom` |
| hills | `Hills` | `StoneRoom` |
| mountain | `Mountains` | `StoneRoom` |
| swim | `ShallowWater` | `IndoorShallowWater` |
| noswim | `WaterSurface` | `IndoorWaterSurface` |
| unused | `FrozenPlains` | `IceRoom` |
| air | `InTheAir` | `IndoorInTheAir` |
| desert | `Desert` | `HotRoom` |
| frozen (11), frozen mountain (12) | `FrozenPlains`, `FrozenMountains` | `IceRoom`, `IceRoom` |
| caves (13, 14), swamp (15) | `CaveRoom`, `Swamp` | `CaveRoom`, `Swamp` |

ROM sector numbers 0-10 follow the names in the table. Circle files number sectors differently: 0 `StoneRoom`, 1 `CityStreet`, 2 `Plains`, 3 `Woods`, 4 `Hills`, 5 `Mountains`, 6 `ShallowWater`, 7 `WaterSurface`, 8 `UnderWater`, 9 `InTheAir`, 10 `Desert`, 11 `FrozenPlains`, 12 `FrozenMountains`, 13 and 14 `CaveRoom`, 15 `Swamp` (`Import.java:6279-6330`). For indoor ROM rooms, a sector of 0-5 or 10-15 becomes `CaveRoom` when the title or description contains CAVE (`6453-6455`).

**Materials**. The `objDescs` table (`Import.java:67-263`) maps 195 material words to resource constants. Examples: `iron` to `RESOURCE_IRON`, `mithril` to `RESOURCE_MITHRIL`, `silk` and `velvet` to `RESOURCE_SILK`, `ebony` to `RESOURCE_EBONY`, `oak` to `RESOURCE_OAK`, and `leather` to `RESOURCE_LEATHER`.

**Socials pronoun tokens** (`Import.java:5003-5011`). The eight-row table in "Socials" is a compact template-token scheme for third-person messages.

**Shop stock defaults** (`Import.java:6938-6964`). Light sources bring two oil flasks per unit. Readable items bring parchment and "the adventurers journal" at value 250. Weapon shops bring "a pack of 20 arrows" at value 50, described as "They are sturdy and wooden, but probably not much use without a bow."

**Armour formula** (`Import.java:4073`). `round((v1+v2+v3+v4+1)/4)+1`, a one-line rule for turning legacy armour values into a number.

**Colour conversion** (`Import.java:670-777`). About 100 rows map ROM colour words and codes to CoffeeMud colour codes. This is useful only if the SUD uses colour in its output.

**Warnings and messages.** Import prints and logs many short messages that read well in a game, for example "Malformed room: room has no name", "Unknown door code", "Reset error (no mob) on line", "You dip! You are IN that area! Leave it first..." (`Import.java:446`) and "Emptiness..." (`6196`).

## Notable design ideas

- **Stable keys across areas.** Room IDs are `AreaName#vnum` (`Import.java:6228`), and key names reuse the same pattern (`4194`, `4135`, `6675`). A key or exit in one area can therefore name a room in another area by a string that stays valid.
- **Two-pass linking with deferred links.** Rooms are created first, then exits. Unknown targets become temporary `#vnum` links (`6754`) that are resolved when the target loads, or recorded in `laterLinks` for the end of a multi-area import (`6766`, `7524-7569`).
- **Effects as parameter strings.** The adjuster, resister and caster holders (`Import.java:3742-3834`) store short parameter strings that generic `Prop_*` classes parse (`Prop_HaveAdjuster.java:1235-1287`, `Prop_HaveResister.java:88-104`, `Prop_SpellAdder.java:134`). One class serves many items, and the values stay editable in the running game.
- **Behaviours as composition.** Act flags do not create subclasses. They attach behaviours (`Scavenger`, `Healer`, `MOBTeacher` and so on) to a generic mobile (`Import.java:2840-2893`).
- **Shops from stock resets.** A shopkeeper is not a separate kind of record. Its stock is the `G` reset rows (`6926-6969`), and the categories it sells come from the buy-type numbers (`2807-2823`).
- **Nothing is silently dropped.** Unused objects and mobiles go to a hidden "Unused Objects" room (`7312-7379`), and an unsupported section is counted and reported (`2363-2366`).
- **Compile mode.** A `StringBuffer` argument runs the importer without a session, and errors are collected in the command's own list (`5084-5088`, `966-976`). That makes it testable.
- **Visibility-aware listings.** INVENTORY hides carried items the viewer cannot see, and says so in one line (`Inventory.java:137-143`). Coins are merged into one "Money" line sorted by denomination (`80-95`, `101-131`).
- **Console with a block editor.** JCONSOLE uses `<` and `>` as block markers in plain text (`129-148`), and a GMCP text box when the client supports it (`132-137`, `149-156`).
- **Dynamic dispatch into the command language.** In JCONSOLE, a bare name in `ScriptingEngine.methods` becomes a callable that runs as one mob-program line (`JConsole.java:231-298`). That exposes the whole command set to a REPL without writing wrappers.

## Problems and cruft

1. **Immunities are never produced in ROM `F` codes.** In the `V` branch the resistance and immunity masks are read from the same field: `res=getBitMask(codesLine,3)` and `imm=getBitMask(codesLine,3)` (`Import.java:4576-4577`). The `-100%` branch (`4602-4603`) therefore never fires. The same duplication appears in the default branch (`4683-4684`, `4709-4710`).
2. **Catalogue item loop uses the wrong data.** In the item loop of the `<CATALOG` import, the check is on `itemCatData` but the call uses `mobCatData.get(m)` (`Import.java:5604-5607`). It can use the wrong data or fail with an index error.
3. **A one-line room block aborts the import.** The guard at `6145-6146` checks `roomV.size()==1` and then reads `roomV.get(1)`, which throws. The intended test was `get(0)`, so a header-only room entry aborts the whole area. The exception is caught and reported (`7470-7473`).
4. **Shadowed spell names.** The check for `ACID B` (`Import.java:1008-1009`, mapping to slot 70, `Spell_AcidArrow`) comes before `ACID BR` (`1242-1243`, slot 200, `Acidbreath`), so "acid breath" resolves to acid blast. The second `ENCHANT W` test (`1269-1270`) is unreachable because the first (`1182-1183`) matches.
5. **Mixed-case test always fails.** `mask=mask.trim().toUpperCase()` is followed by `if(!mask.startsWith("all"))` (`Inventory.java:147-149`), which is never true. Typing `inventory all sword` yields `ALL ALL SWORD`. `EnglishParser.fetchFlags` strips only one leading `ALL` (`Libraries/EnglishParser.java:3105-3113`), so the second word is probably searched as a name. This was not traced further.
6. **Exotic weapons default to ranged.** `{"exotic","0"}` shares class 0 with `bow`, and class 0 means `CLASS_RANGED` (`Import.java:2373`, `2415-2416`). Any exotic weapon whose name matches no other keyword is classed as ranged.
7. **Effects mutated after they are attached.** `applyRoomCodeBits` attaches one `Prop_ReqCapacity` object and one `Prop_ReqLevels` object to a room, then changes their text for each later flag (`Import.java:4918-4929`, `4944-4966`). `StdRoom.addNonUninvokableEffect` stores the reference (`Locales/StdRoom.java:2916-2928`), so the last setting wins. A room with ROM bits 9 and 11 ends with capacity 1, not 2.
8. **Dead code.** `baseLevel` is computed and never used (`Import.java:3049-3055`). Circle size weights are computed and then overwritten; the code says so (`3103-3130`, comment at `3125`). The duplicated `doneRooms.containsValue(linkRoom)` test (`6742-6743`) is one clause written twice.
9. **Shop files are dropped.** The `.shp` extension is treated as an alias of `.zon` (`5192-5197`), but no shop data is read from it (`5203-5245`).
10. **Import is non-deterministic.** Sex `EITHER` is a coin flip (`3023-3024`), mudchat is a 25% roll (`3046`), and gold is random (`3068`). Re-importing the same file gives different mobiles.
11. **Heavy copying and scanning.** Most reset rows deep-copy the data lists with `CMParms.copyFlattenList` (`Import.java:6881-6885`, `6913-6914`, `7011-7012`, `7050-7051`, `7096-7097`, `7131-7132`, `7328-7329`, `7348-7351`; `core/CMParms.java:4216-4228`). Cross-area links scan every room (`6713-6748`), `getRoom` scans every key (`384-388`), and `fillInAreaRooms` runs on all areas after each import (`7577-7581`). Large imports are quadratic.
12. **Diagonal door resets are a guess.** Reset codes 6-9 pick the highest-numbered exit with no door (`Import.java:7228-7234`), which is a guess rather than a translation of the ROM code.
13. **Smurf-wells merge is by name match.** `Import.java:7417-7447` merges a description into whichever sibling mentions the item's name. It can attach text to the wrong item.
14. **Short buffer check.** The `<CATALOG` detection calls `buf.substring(0,30)` after checking only that the buffer is longer than 20 characters (`5515-5525`), so a 21-29 character file throws.
15. **JRUN does not exit the context on error.** `Context.exit()` is called after the `catch` and not in a `finally` (`JRun.java:70-85`). An `Error` such as a stack overflow leaves the thread's Rhino context entered.
16. **Scripts bypass the approval table.** JRUN and JCONSOLE check only the `JSCRIPTS` flag (`JRun.java:153-157`, `JConsole.java:440-444`). The approval check that `DefaultScriptingEngine` applies to embedded scripts (`DefaultScriptingEngine.java:9276`, `core/CMSecurity.java:1072-1085`) is not used. The script also receives the MOB object (`JRun.java:99-102`, `JConsole.java:201-203`).
17. **Per-file security is missing for ROM text.** Only the XML branches check a flag (`Import.java:5292`, `5398`, `5454`, `5517-5518`, `5620`, `5679`, `5822`). ROM `.are` text (`5879` onward) needs only some import flag at the command level (`7614-7616`). Any holder of one of `SECURITY_IMPORT_GROUP` can import area text.
18. **Charm and order exposure is unverified.** IMPORT is orderable (`Import.java:7607-7611`). Whether a charmed or led mobile's `securityCheck` is applied again when it is ordered was not traced.
19. **Inventory call with no effect.** `CMLib.beanCounter().getTotalAbsoluteNativeValue(mob)` is called at `Inventory.java:63` and its result is discarded.
20. **Idle multiplier is a multiplayer hack.** The `check*600` multiplier for IMPORT, EXPORT, CHARGEN and MERGE (`Libraries/Sessions.java:196-200`) only protects long-running builder sessions.

## Takeaways for a Python SUD

- **Borrow the reset model as data.** Each reset is a row: kind (`mob`, `give`, `equip`, `put`, `object`, `door`), template ID, room or container, and count. The ROM letters are a useful checklist of what a room needs to spawn (`Import.java:6849-7309`). In Python:

  ```python
  @dataclass
  class Reset:
      kind: Literal["mob", "give", "equip", "put", "object", "door"]
      template: str          # "area#vnum"
      room: str | None = None
      limit: int = 1
      respawn_ticks: int = 60
  ```

- **Keep the stable key scheme.** Use `area#vnum` as a global ID, as `Import.java:6228` does. It makes cross-area links and keys trivial to resolve.
- **Replace the effect strings with dicts.** Store stat adjusters as `{"str": 2, "armor": 5}` and keep the ROM-to-stat table (`Import.java:3742-3803`) as a single dict, `ROM_APPLY = {1: "str", 2: "dex", 3: "int", 4: "wis", 5: "con", 7: "cha", 12: "mana", 13: "hit", 14: "move"}`. Do not carry over the text-parsing.
- **Use flags as sets, not bit masks.** Room flags become a `set[str]`: `dark`, `no_mobs`, `indoors`, `underwater`, `capacity`, `no_summon`, `no_recall`. Mobile flags become `roles`: `sentinel`, `scavenger`, `aggressive`, `teacher`, `healer`, `thief`, `guard`.
- **Turn the sector table into a dict.** `TERRAIN = {"city": "street", "field": "plains", ...}` is the same mapping as the table in "Content worth mining", with two rows (indoor and outdoor) kept separate.
- **Pick specials for a single player.** Keep the archetypes that make sense in a world with one human: teacher, healer, guard, shopkeeper, thief, wanderer, breath and caster archetypes. Drop MudChat, gang members and the player-helper behaviours, which assume other players.
- **Model items as small classes.** `Container(capacity, lock, key_id)`, `Weapon(dice, size, damage_type, weapon_class)`, `Armor(value, slots)`, `Drink(liquid, volume, effects)`, `Food(nourishment)`, `Key(id)`, `Portal(target)` and `Light(duration)`. The `Gen*` types show the field list (`Import.java:4022-4279`).
- **Build the importer offline.** If the SUD needs legacy areas, write a standalone `tools/import_rom.py` that parses the sections listed in `readBlocks` (`Import.java:2240-2326`) and writes JSON, with the same "unsupported" counts. Do not add an in-game import command.
- **Show carried items the way INVENTORY does.** Group coins by currency and sort by denomination (`Inventory.java:80-95`). Keep the "(you can't see)" line for darkness. Decide whether worn items belong in the same listing, since INVENTORY excludes them (`69-70`).
- **Replace JCONSOLE and JRUN with a debug REPL.** `python -i`, or a `debug` command behind a `--debug` flag, covers the same ground. Dispatching bare names into the command set (`JConsole.java:231-298`) is worth copying as a `debug.call("name", args)` helper. Keep it off by default, because it runs arbitrary code.
- **Drop the multiplayer machinery.** Account and player import (`Import.java:5677-5818`), new-player channel announcements (`5799-5801`), obliterate prompts (`6102-6103`, `5752`), catalogues (`5515-5616`), idle-timeout multipliers (`Sessions.java:196-200`), and compile-mode error collection can go.
- **Respect the licence.** The file headers carry an Apache License 2.0 notice (`Import.java:28-42`, `Inventory.java:20-34`). Borrow the ideas and the tables with attribution, and do not copy code.

## Coverage

- **Read in full:** `Commands/Inventory.java` (220 lines), `Commands/JRun.java` (165), `Commands/JConsole.java` (445).
- **Read in full:** `Commands/Import.java`, lines 1-1360, 1380-1419, 1450-1570 and 2155-7618. Not read line by line: the name-chain lines 1361-1379 and 1420-1449, and the slot-switch body 1571-2154. Those were checked with counts (about 243 slots mapped, about 104 blank, 152 name tests), a scan of the name chain for duplicate and shadowed prefixes, and samples of the entries.
- **Read in part, for context:** `Commands/StdCommand.java:51-59, 86-124, 224-274`; `Commands/interfaces/Command.java:50-125`; `core/CMParms.java:2761-2775, 4216-4228`; `core/CMSecurity.java:68-70, 883-890, 908-911, 1005-1085, 2799-2806`; `core/CMClass.java:1194-1225`; `core/CMProps.java:1143-1186, 3099`; `Abilities/Properties/Prop_HaveAdjuster.java` (selected lines), `Prop_HaveResister.java:88-104`, `Prop_SpellAdder.java:134`; `Locales/StdRoom.java:2916-2928`; `Common/DefaultScriptingEngine.java:9260-9285`; `Common/interfaces/ScriptingEngine.java:496, 620`; `Libraries/EnglishParser.java:3071-3130, 3175-3184`; `Libraries/Sessions.java:176-222`; `Libraries/CommonMsgs.java:293-298`; `Commands/Shell.java:60-66, 800-812`; `resources/help/arc_help.ini:350-362, 1561-1586, 2244-2250`; `guides/ArchonGuide.html` (IMPORT and JRUN entries).
- **Skipped:** `Commands/Wealth.java` and `Commands/Worth.java`, which extend Inventory and belong to other buckets. `Libraries/intermud/cm1/commands/Import.java` is a different command.
