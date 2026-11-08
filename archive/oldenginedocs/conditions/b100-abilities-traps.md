# Traps, bombs and trapped objects

> Part of the CoffeeMud NEWOVERVIEW. Bucket: `b100` (Abilities/Traps) · 82 files · 12475 lines of Java.

Citations are relative to the CoffeeMUD folder, with the package prefix `com/planet_ink/coffee_mud/` dropped (for example `Abilities/Traps/StdTrap.java:796`). Everything here was read from source and not executed. Where a comment, help file or data file says something different from the code, the text says so.

## Summary

A trap is an ability attached to a room, exit, container or item as an effect. It waits for a trigger message (someone enters the room, opens or unlocks a door or chest, or picks up an item), then its `spring()` applies one effect: damage, poison, disease, a spell, a pit, a cage, a cave-in or a noise. It re-arms after a reset countdown counted in 4-second ticks, and the countdown length depends on the level of the thief who set it. Bombs use the same object with a player-activated fuse and a sticky or shaped delivery. The thief skills build their menus from two text tables (`resources/skills/traps.txt` and `bombs.txt`), so the catalogue is data-driven, with 41 player-settable traps and 13 bomb types. A legacy family of 23 trigger-by-effect variants on `Trap_Trap` is used for locked doors, chests and automatically trapped doors.

## Key files and types

| File or class | Role | Lines |
|---|---|---|
| `Abilities/interfaces/Trap.java` | Interface: `spring`, `sprung`, `disable`, `resetTrap`, `setTrap`, `canSetTrapOn`, `maySetTrap`, `isABomb`, `activateBomb` | 184 |
| `Abilities/Traps/StdTrap.java` | Base class: state, trigger routing (`okMessage`, `executeMsg`), saves and exemptions, `setTrap`, reset `tick`, persistence string | 841 |
| `Abilities/Traps/StdBomb.java` | Bomb base: `CAN_ITEMS`, `isABomb()`, 3-tick fuse | 87 |
| `Abilities/Traps/GenTrap.java` | Data-driven ("Gen") trap: 18 stored slots kept in a static map keyed by ID | 702 |
| `Abilities/Traps/Trap_Trap.java` | Legacy base: random trap type 0-2, needle, blade, gas, spell and pit effects | 474 |
| `Abilities/Traps/Trap_Enter.java`, `Trap_ExitRoom.java`, `Trap_Get.java`, `Trap_Open.java`, `Trap_Unlock.java` | Legacy trigger bases (102, 136, 85, 82, 86 lines) | 491 |
| `Abilities/Traps/Trap_RoomPit.java` | Two-room pit; base of AcidPit, SnakePit, SpikePit, DeepPit and the pit ThruPits | 277 |
| `Abilities/Traps/Trap_SnakePit.java` | Caged-animal trap; base of `Trap_RatPit` | 210 |
| `Abilities/Traps/Trap_AcidPit.java`, `Trap_SpikePit.java`, `Trap_DeepPit.java` | Pit variants with their own damage and per-tick effects | 169, 171, 91 |
| `Abilities/Traps/Trap_CrushingRoom.java`, `Trap_FloodRoom.java`, `Trap_CaveIn.java`, `Trap_Avalanche.java`, `Trap_Boulders.java` | Room-scale environmental traps | 218, 299, 194, 167, 157 |
| `Abilities/Traps/Trap_BearTrap.java` | Holds the victim with a struggle pool | 196 |
| `Abilities/Traps/Trap_SpellBlast.java`, `Bomb_SpellBlast.java` | Spell stored on a scroll component | 199, 170 |
| `Abilities/Traps/Bomb_Sticky.java`, `Bomb_Shaped.java` | Bombs that attach to a target, or need one next to them | 354, 297 |
| `Abilities/Traps/Bomb_Heat.java`, `Bomb_Sonic.java` | Bombs with side effects (cooked food, fish and resource override) | 211, 193 |
| `Abilities/Traps/Bomb_AcidBurst.java`, `Bomb_Explosive.java`, `Bomb_FlameBurst.java`, `Bomb_Noxious.java`, `Bomb_Pepper.java`, `Bomb_Poison.java`, `Bomb_Smoke.java`, `Bomb_Spores.java`, `Bomb_Water.java` | Template bombs: avoid check, then one effect | 127, 145, 143, 133, 128, 157, 128, 156, 116 |
| `Abilities/Traps/Trap_*.java` (the other 41 StdTrap-family classes) | One trap each; list in Content | 62 to 218 |
| `Thief/Thief_Trap.java` (outside bucket) | Lays a trap; reads `resources/skills/traps.txt`; handles accidents and discovery | (read for interaction) |
| `Thief/Thief_MakeBomb.java`, `Thief/Thief_RemoveTraps.java` (outside) | Lays a bomb from `bombs.txt`; disarms a trap | (read for interaction) |
| `Libraries/CoffeeUtensils.java` (outside) | `makeADeprecatedTrap` picks legacy traps for doors and containers | 341-420 |
| `resources/skills/traps.txt` | 41 rows: display name, level, trap class | data |
| `resources/skills/bombs.txt` | 117 rows: 13 bomb types times 9 size tiers | data |
| `resources/skills/mtraps.txt` | 5 rows used by `Thief/Thief_MinorTrap.java` | data |
| `resources/help/traps.ini` | In-game help for each trap; a few entries disagree with the code (see Problems) | data |

## How it works

### Data model

`StdTrap` keeps its state in a few fields:

- `sprung` (`Abilities/Traps/StdTrap.java:61`), `disabled` (line 64), and `reset`, the ticks to wait after a spring (line 62; the comment says "5 minute reset is standard", but 60 ticks at 4 s is 4 minutes, see Problems).
- `ableCode` (line 63), which is the setter's bonus. `setTrap` writes the thief's extra-level value into it (`StdTrap.java:632`, called from `Thief_Trap.java:365`). Damage formulas use `trapLevel() + abilityCode()`.
- `trapLevel` (line 65): the class's own level. `-1` on the base class means it cannot be set. `StdTrap.java:96` returns it.
- `invokerName` (line 126) and `newMessaging`, up to three custom texts: trigger, damage and avoid.

Persistence uses one string (`StdTrap.java:306-316`): `` `Setter` "trigger" "damage" "avoid" :LEVEL/CODE:subclass-text ``. `setMiscText` parses it back (`StdTrap.java:257-300`). The thief skill strips `"` and `@` from custom messages before storing them (`Thief/Thief_Trap.java:245-249`), so the format cannot be broken by player text.

The host is the `affected` object. `canAffectCode()` says what kinds of host are legal: `CAN_ROOMS`, `CAN_EXITS` (doors and containers) or `CAN_ITEMS`. `invoker()` returns the setter, or a placeholder `StdMOB` at the trap's level if the setter is no longer loaded (`StdTrap.java:226-250`).

### Lifecycle: arm, trigger, spring, reset

```
set (setTrap) -> ARMED -> trigger message -> spring() -> SPRUNG -> reset ticks -> ARMED
                   |                                                 |
                   +-> disable() by a disarming thief --> DISABLED   +-> bomb: explode and destroy
```

- `setTrap` (`StdTrap.java:623-648`) copies the template, sets `reset = baseRejuvTime(qualifyingLevel + bonus)` (line 629), sets the invoker (line 630) and the bonus (line 632), and attaches the copy to the host. A non-permanent trap also starts a destruction tick after `baseDestructTime(...) = level * 30` ticks (`StdTrap.java:140-142`, `:640-641`). A permanent trap (owned land, or an exit into owned land) is saved to the database instead (`Thief/Thief_Trap.java:343-367`).
- `StdTrap.spring` (`StdTrap.java:796-804`) sets `sprung = true`, `tickDown = reset`, and starts a one-tick countdown on `TICKID_TRAP_RESET`, unless the trap is a bomb.
- `tick` (`StdTrap.java:743-776`) decrements `tickDown` once per tick (line 757). At zero, a normal trap sets `sprung = false`. A bomb explodes instead (`explodeBomb`, line 763) and destroys its item.
- `resetTrap` (`StdTrap.java:778-788`) re-arms a sprung trap at once. A thief can use it on a trap that they laid (`Thief/Thief_Trap.java:309`).
- `disable()` (`StdTrap.java:203-215`) is the disarm. An uninvokable trap stays in place with a countdown. A removable one is uninvoked.

### Trigger routing

Triggers come from two hooks. `okMessage` can veto a message; `executeMsg` runs after the action and can spring the trap.

- Rooms (`CAN_ROOMS`): an ENTER by someone who is not the owner springs the trap (`StdTrap.java:437-458`). Most room traps therefore fire after the mover has arrived. `Trap_Enter` (below) is the exception: it vetoes the move.
- Doors and containers (`CAN_EXITS`): OPEN springs the trap (`StdTrap.java:490`). An UNLOCK springs it only when the door is locked (`:477-486`).
- Items (`CAN_ITEMS`): GET, PUSH or PULL by a non-owner springs it (`:509-511`). A bomb is activated instead by its owner's HOLD (`:498-505`), which prints "You activate <T-NAME>.".
- Travel-through traps (`getTravelThroughFlag()`, `StdTrap.java:145`): ENTER records the direction the mover came from (`:441-458`). A later LEAVE by a different exit springs the trap and cancels the move (`okMessage`, `:381-411`). The `return !sprung()` at line 407 is what cancels it.
- Giving an item trap to someone outside the setter's group is refused with "<S-NAME> can't accept <T-NAME>." (`StdTrap.java:373-378`).
- An owner who touches a sprung trap clears it. `Trap_Get`, `Trap_Open` and `Trap_Unlock` do this with `if(msg.source().isMine(affected)) unInvoke()` (`Abilities/Traps/Trap_Get.java:64-72`).

### Who is exempt, and who saves

- `canInvokeTrapOn` (`StdTrap.java:190-200`): the setter must be allowed to fight the victim (`mayIFight`), and the victim must not be in the setter's group.
- `isLocalExempt` (`:169-188`): for a permanent trap, someone with land privileges is exempt, and so is a monster whose start room is in the same area.
- `doesSaveVsTraps` (`:158-167`): `rollPercentage() <= save`, where `save` is the victim's `STAT_SAVE_TRAPS` plus the victim's level minus the setter's level. `Libraries/Dice.java:409-411` makes the roll 1 to 100.
- Avoid: a failed check, a save, the setter or a group member all print the avoid text and do nothing else (for example `Trap_RoomPit.java:242-250`).

### Timing

- One tick is 4000 ms (`core/CMProps.java:681`). The reset tick ID is 5 and the destruction tick ID is 6 (`core/interfaces/Tickable.java:92,94`).
- Default reset: `baseRejuvTime(L) = round((30 - 0.75 * min(L, 29)) * 30)` ticks, minimum 1 (`StdTrap.java:130-138`). Here `L` is the setter's qualifying level plus the bonus, so the setter's level sets the length of the reset. Sample values:

| Setter level L | Reset ticks | Minutes |
|---|---|---|
| 0 | 900 | 60 |
| 10 | 675 | 45 |
| 20 | 450 | 30 |
| 29 | 248 | about 16.5 |

- Non-permanent destruction: `L * 30` ticks (`:142`). At L = 20 that is 600 ticks, or 40 minutes.
- Overrides: Flood room 16 ticks (`Trap_FloodRoom.java:76-79`), Crushing room 16 (`Trap_CrushingRoom.java:76-78`), Cave-in 6 (`Trap_CaveIn.java:76-78`), Darkfall 20 (`Trap_Darkfall.java:76-78`), Bear trap 35 (`Trap_BearTrap.java:76-78`), Tripline 2 (`Trap_Tripline.java:76-78`), Shaped bomb 10 (`Bomb_Shaped.java:58`), Acid pit at least 15 (`Trap_AcidPit.java:116-121`). `StdBomb` uses 3 ticks, or 12 seconds (`StdBomb.java:76-84`).

### Damage and effect numbers

Most StdTrap-family damage uses one form: `CMLib.dice().roll(trapLevel() + abilityCode(), S, 1)`. That is N dice of S sides plus 1 (`Libraries/Dice.java:398-405`). N is the class level plus the setter's bonus, and S is set per trap. Examples: 6 for pits, the bear trap and acid spray; 8 for shock and sparks; 10 for the explosive bomb; 12 for the flame burst; 20 for the avalanche, boulders and cave-in; 24 for the acid burst; 30 for the crushing room.

- Average per victim: Avalanche at L = 25 with bonus 0 is `roll(25,20,1)`, which gives 26 to 501 with a mean of 263.5 (`Trap_Avalanche.java:155`).
- Legacy `Trap_Trap` uses the victim's level instead: gas `roll(level,10,1)` (`Trap_Trap.java:202,216`), needle `roll(level,5,1)` (`:237`), blade `roll(level,2,0)` (`:261`), pit fall `roll(level,3,1)` (`:355`).
- `GenTrap`'s default damage formula is `((@x1+@x2)*(1?6))+1` (`Abilities/Traps/GenTrap.java:83`). Here `?` gives a random value between its operands (`core/CMath.java:1796-1797`), so the default is about `(L + bonus) * d6 + 1`.

### Pits, and rooms that move

`Trap_RoomPit` makes a pit on the first fall (`makePit`, `Trap_RoomPit.java:167-198`). It creates two temporary rooms, "Inside a dark pit", with `setSavable(false)` and an empty room ID (lines 173-174, 183-184). A closed doorway joins the room above to the bottom room, and the doorway has `openDelayTicks` of 10 (line 260).

- The fall (`finishSpringing`, `:224-235`) either floats the victim gently into the pit (weight under 5) or applies damage and "hit(s) the pit floor with a THUMP!". The damage is `roll(L+bonus, 6, 1)` (line 231).
- Climbing back through the room above is not a trigger. `executeMsg` (`:201-222`) suppresses the spring when the move comes through the DOWN exit.
- `unInvoke` (`:85-133`) kills everything still in the two pit rooms with `killMeDead(false)` and destroys both rooms. The `tick` method (`:152-165`) postpones that destruction while a player is still in the pit.

`Trap_BearTrap` (`:163-192`) deals `roll(L+bonus,6,1)` piercing and holds the victim. Its struggle pool is `250 + (L+bonus)*10`, scaled by the victim's speed (lines 187-188). Each struggle takes away STR plus level (lines 145-146), and other exits are blocked until the pool is empty (`okMessage`, `:130-159`).

`Trap_CrushingRoom` (`:159-194`) runs a 16-tick cycle. Above 13 ticks the walls close in. From 5 to 13, each tick deals `roll(L+bonus,30,1)` bashing to every occupant the setter may fight (line 181). At 4 or below the walls retract. Exits are blocked throughout (`okMessage`, `:133-156`).

`Trap_FloodRoom` (`:229-263`) fills the room above 13 ticks, holds the water from 13 down to 3, and drains it at 2 or below. While `2 < tickDown < 13`, `affectPhyStats` (`:185-200`) adds `IS_SWIMMING` and, unless the victim can breathe freshwater, `CAN_NOT_BREATHE`.

### Monsters, poison, disease and spells

- `Trap_SnakePit.setTrap` (`:104-120`) packs up to five caged Ophidian animals into its text as `<SNAKES>...</SNAKES>`. On a fall (`finishSpringing`, `:169-209`) it splits the text on `</MOBITEM><MOBITEM>` and brings each animal to life with `NO_REJUV`, setting the victim as the target. Animals that are dead or out of combat are destroyed at the next reset (`tick`, `:149-166`). `Trap_RatPit` does the same with Rodent-race cages (`Trap_RatPit.java:76-92`).
- `Trap_MonsterCage` releases one monster stored as cage text (`:159-184`), and destroys it at reset if it is idle (`:111-122`).
- Poison and disease traps copy an offensive affect from a container when they are set. `returnOffensiveAffects` looks for `ACODE_POISON` or `ACODE_DISEASE` (`Trap_Needle.java:75-85`) and stores the affect's ID (`:113-118`). On a spring, the stored ability is invoked on the victim at `trapLevel() + abilityCode()` (`:172-176`). Defaults are `Poison`, `Disease_Cold` and `Poison_Slumberall`.
- Gas traps (`Trap_PoisonGas.java:173-179`, `Trap_SleepGas.java:174-180`) apply the stored affect to every occupant the setter may fight, except the setter.
- `Trap_SpellBlast` stores the scroll's first spell ID at set time (`Trap_SpellBlast.java:115-117`) and invokes it at `trapLevel() + abilityCode()` on the victim (`:188-192`).

### Bombs

- `StdBomb` (`StdBomb.java:52-84`) can only be placed on items, is a bomb, and has a 3-tick fuse.
- Activation: the owner's HOLD (`StdTrap.java:498-505`) calls `activateBomb` (`:418-428`), which starts the countdown at `reset` ticks. When the countdown ends (`tick`, `:757-768`), `explodeBomb` runs and the bomb is destroyed.
- `StdTrap.explodeBomb` (`:729`) handles a bomb inside a container by calling `explodeContainer` (`:684-715`), which destroys the container if its material allows (`canExplodeOutOf`), and damages or moves the contents. Otherwise it springs on the owner, or on everyone in the room (`springOnRoomMobs`, `:716-727`).
- **Sticky** (`Bomb_Sticky.java`): PUT onto a target sticks it (`okMessage`, `:118-136`). On explosion, a lockable target may lose its lock. The burst chance is `100 - 10*diff` percent, where `diff` is the target's level minus the bomb's level, and it is always 100 when `diff <= 0` (`:183-190`). A burst lock then refuses lock and unlock attempts for `openDelayTicks`, through a `ThinAbility` named "Unlockable" (`:203-268`). An item target takes `roll(1, die, bonus/2)`, where `die = max(L+bonus, 5)` is scaled by material: cloth halved, leather a third, paper doubled, flesh a quarter, metal, rock and liquid zero, and everything else a fifth (`:281-309`).
- **Shaped** (`Bomb_Shaped.java`): must sit next to its target in the same room (`isShaped`, `:167-177`). Its fuse is 10 ticks. It needs at least 10 lb of iron to build (`minRequiredIron`, line 62). It triggers on the owner or on a 25% roll (`spring`, `:276-277`). Damage is `roll(dl, die, 1)`, where `dl = (L+bonus)/divider` and `die = max(dl, 20/divider)`. The divider is 2 for a light bomb (`:285-290`). A door target is forced open (see Problems).
- **Template bombs**: the other 11 bomb classes use one shape. The victim is avoided if the setter cannot fight them, or the victim is the setter or in the setter's group, or they save. Otherwise a trigger message is shown and the effect applies at `trapLevel() + abilityCode()`. The effect is a damage roll, or an invoked spell or ability (table under Content).
- `Bomb_Heat` (`Bomb_Heat.java:122-181`): when it destroys a non-player corpse, it leaves a food item named "some cooked <name>". The name is the body's saved name, or else its race. The food's material is a random flesh or vegetation resource of that race.
- `Bomb_Sonic` (`Bomb_Sonic.java:102-146`): in a room with fish resources, it spawns trapLevel fish items. It then makes sure a room-wide `TemporaryAffects` effect exists for ten ticks, and sets its text to override the room's resource to NOTHING.

### Setting, disarming and discovering traps

`Thief/Thief_Trap.java` is the skill that lays traps. It reads the list from `traps.txt` through `fetchRecipes` (`:388-411`) and keeps the entries whose `maySetTrap(mob, qualifyingLevel)` passes (`:109-120`). It then sorts them by the recipe level in the second column (`:123-134`). The success check is `proficiencyCheck(mob, (mob level + 2*XLEVEL - host level)*3)` (`:287-288`).

- Discovery: if the host already has a trap, the lay action deals with it first (`:290-332`).
  - A disabled one fails the attempt.
  - A sprung one is reset by a thief who can reset it (`:309`).
  - An unsprung trap of the same type that the thief set has a chance of `30 + 5*XLEVEL` percent, and on success the thief has "already discovered" it (`:320-325`).
  - Otherwise the old trap springs on the thief (`:330`).
- Accidents: on a failed attempt, a 50% roll lays the trap and then springs it on the thief (`:372-376`). The message is "<S-NAME> trigger(s) the trap on accident!". See Problems: for most StdTrap-family traps, this spring does nothing.
- `Thief/Thief_MakeBomb.java` does the same with `bombs.txt` (`:98-110`, `:226-236`). Bombs are not armed until the owner activates them.
- `Thief/Thief_RemoveTraps.java` disarms. Its check is `((level + 2*XLEVEL) - host level) * 3` (`:124-125`), and success calls `theTrap.disable()` (`:196`). On owned land the trap is uninvoked and its effect removed (`:206-215`).
- `Ranger/Ranger_SetSnare.java:103` creates `Trap_Snare` directly.

### Trapped objects in the world

Built-in objects hold legacy traps set by their constructors: `Items/Basic/TrappedChest.java:44` (`Trap_Trap`), `Exits/TrappedLockedDoor.java:44` (`Trap_Trap`), `Exits/TrappedDoor.java:44` (`Trap_Open`), `Exits/NeedleDoor.java:44` and `Items/Basic/NeedleChest.java:44` (`Trap_OpenNeedle`), and `Exits/PitOpen.java:50` (`Trap_EnterPit`).

`CoffeeUtensils.makeADeprecatedTrap` (`Libraries/CoffeeUtensils.java:341-400`) picks a legacy trap for a door or container from a roll of `Math.round(Math.random()*100)`:

- Locked door: Open below 20, Unlock below 80, otherwise Enter.
- Unlocked door: Open below 50, otherwise Enter. A door-less exit gets Enter.
- Lockable container: Open below 20, Unlock below 80, otherwise Get. Unlocked container: Open below 50, otherwise Get. Plain container and item: Get.

`setTrapped` (`:403-407`) gives the trap a reset of 50 ticks (about 3 minutes 20 seconds). The second overload (`:411-420`) removes existing traps before adding the new one.

### The legacy `Trap_Trap` family

`Trap_Trap` implements `Trap` directly. It is not player-settable: `maySetTrap` and `canSetTrapOn` both return false (`Trap_Trap.java`, the "as these are not standard traps" block).

- Type: `trapType()` returns `CMLib.dice().roll(1, 3, -1)`, which is 0 (needle), 1 (pit or blade) or 2 (gas). It is re-rolled on every spring and on every unInvoke (`:72-75`, `:384`, `:427`). Spell type 3 is never produced. Subclasses override it with a constant.
- Effects: `needle` (`:229`) deals piercing damage and applies Poison. `blade` (`:253`) is the same with a smaller roll. `gas` (`:186`) fills the room if the victim's level is above 15 (`:193`), otherwise only the victim is hit. `victimOfSpell` (`:276`) invokes the spell named in the text, with `;` introducing its arguments. `fallInPit` (`:303`) applies only when the host is an `Exit` (`:394`). A blade on a room is a blade (`:396`).
- Triggers: `Trap_Enter` (`Trap_Enter.java:64-98`) springs on ENTER in `okMessage` and returns false, which stops the move. `Trap_ExitRoom` (`Trap_ExitRoom.java:84-130`) records the entry direction in `safeDirs` and springs on a LEAVE by another exit. `Trap_Get`, `Trap_Open` and `Trap_Unlock` spring on GET/PUSH/PULL, OPEN and UNLOCK/JUSTICE/DELICATE_HANDS respectively.
- Saves: the legacy check is `rollPercentage() < save`, with no level term (for example `Trap_Trap.java:186-190`).
- Shared state: `benefactor` is a static `StdMOB` (`Trap_Trap.java:64`). `spring` changes its location and level for every legacy trap (`:380-383`), and `invoker()` returns it when no setter is recorded (`:363-367`).

### Gen traps

`GenTrap` is the data-driven trap. Its values occupy 18 stored slots, `V_NAME` to `V_LEVL` (`GenTrap.java:57-76`). They are edited through the stat codes in `CODES` (`GenTrap.java:397-418`): CLASS, TEXT, NAME, LEVEL, ACODE, CANAFFECTMASK, CANTARGETMASK, ISBOMB, AVOIDMSG, TRIGMSG, DAMMSG, ACOMP, DMGF, DMGT, DMGM, ABILITY, ABILTXT, ABILTIK, SCRIPT, HELP and BASELEVEL. The slots are kept in a static `Hashtable` keyed by ID (`GenTrap.java:56`), so every instance with the same ID shares one record. `setTrap` consumes the ACOMP components (`:207-222`). `spring` (`:275-345`) runs the avoid check, shows the trigger text, applies `DMGF` through `postDamage` (`:309-310`), optionally invokes ABILITY at `trapLevel()+abilityCode()` (`:341`), and optionally runs a scripting hook (`getScripter`, `:254-273`).

## Content worth mining

### The 41 player-settable traps (`traps.txt`)

I checked each of the 41 rows against its class: the level in `traps.txt` matches the class's `trapLevel` in all 41. The legacy classes are not in this table.

Trigger key: **E** enter the room (non-owner); **O** open a door or container, or unlock a locked one; **G** get, push or pull an item; **T** leave by a different exit (travel-through). "Doors" means `CAN_EXITS`, which covers containers. L is the class level and c is the setter's bonus.

| Trap (traps.txt) | Lvl | Class | Host | Trig | Effect and numbers |
|---|---|---|---|---|---|
| noisy trap | 1 | Trap_Noise | door, container, item | O, G | No damage. Every other room in the area is told "You hear a loud noise coming from somewhere." (`Trap_Noise.java:92-100`) |
| popping noise | 1 | Trap_Popper | item | G | Same, with "**POP**" in the text (`Trap_Popper.java:88-99`) |
| pit trap | 1 | Trap_RoomPit | room | E | Two-room pit. `roll(L+c,6,1)` if weight is 5 or more (`Trap_RoomPit.java:231`) |
| small pit trap | 1 | Trap_RoomThruPit | room | T | The pit, triggered on leaving by another exit (`Trap_RoomThruPit.java:51-60`) |
| tripline | 1 | Trap_Tripline | room | E | Victim sits; falls to the room below if the down exit is open (`Trap_Tripline.java:120-144`); reset 2 ticks |
| darkfall | 2 | Trap_Darkfall | room | E | Room is dark while sprung (`Trap_Darkfall.java:82-92`); reset 20 ticks |
| poisoned needle | 4 | Trap_Needle | door, container, item | O, G | Poison at L+c on the victim (`Trap_Needle.java:172-176`); component is a poisoned drink |
| poisoned pricker | 4 | Trap_NeedlePrick | item | G | As the needle, with a different message (`Trap_NeedlePrick.java:168-176`) |
| snare trap | 5 | Trap_Snare | room | E | Victim sits and is bound by `Thief_Bind` on a dummy item named "the snare" (`Trap_Snare.java:136-144`); component 5 lb cloth |
| launcher trap | 6 | Trap_Launcher | door, container, item | O, G | `roll(L+c, D, 1)`, D = the ranged weapon's damage (default 3) (`Trap_Launcher.java:137-159`) |
| sleep gas | 7 | Trap_SleepGas | door, container, item | O, G | Poison_Slumberall at L+c on each occupant the setter may fight (`Trap_SleepGas.java:174-180`) |
| small spike pit | 7 | Trap_SpikeThruPit | room | T | Spike pit on travel-through (`Trap_SpikeThruPit.java:51-60`) |
| ignition trap | 8 | Trap_Ignition | item | G | Burning on the item at `L/5 + c` (`Trap_Ignition.java:148-152`); a carried item is dropped |
| spike pit | 8 | Trap_SpikePit | room | E | Fall `roll(L+c,6,1)` plus each component dagger's damage dice, or 5d4 if none (`Trap_SpikePit.java:159-166`); five daggers are stored (`:101-115`) |
| small snake pit | 9 | Trap_SnakeThruPit | room | T | Snake pit on travel-through (`Trap_SnakeThruPit.java:51-60`) |
| weapon swing | 9 | Trap_WeaponSwing | door, container, item | O, G | `roll(L+c, D, 1)`, D = the melee weapon's damage (default 3) (`Trap_WeaponSwing.java:137-162`) |
| greasy | 10 | Trap_Greasy | item | G | "is covered in grease": the victim drops the item; disabled after 20 slips (`Trap_Greasy.java:75,150-155`) |
| monster cage | 10 | Trap_MonsterCage | room | E | Releases the stored monster (`Trap_MonsterCage.java:172-184`) |
| snake pit | 10 | Trap_SnakePit | room | E | Up to five Ophidian monsters released on the fall (`Trap_SnakePit.java:104-120,192-206`); fall damage `roll(L+c,6,1)` |
| gluey | 11 | Trap_Gluey | item | G | The item is made non-removable and non-droppable, and the flags persist (`Trap_Gluey.java:94-95`) |
| small rat pit | 11 | Trap_RatThruPit | room | T | Rat pit on travel-through (`Trap_RatThruPit.java:51-60`) |
| rat pit | 12 | Trap_RatPit | room | E | Rodent-race cage monsters (`Trap_RatPit.java:76-92`) |
| small deep pit | 13 | Trap_DeepThruPit | room | T | Deep pit on travel-through (`Trap_DeepThruPit.java:51-60`) |
| infected | 13 | Trap_Infected | item | G | The stored disease at L+c on the victim (`Trap_Infected.java:172-176`); component diseased food |
| spore trap | 13 | Trap_SporeTrap | door, container, item | O, G | Disease at L+c (`Trap_SporeTrap.java:175-179`) |
| deep pit | 14 | Trap_DeepPit | room | E | `roll(L+c,15,1)`, capped at 95% of the victim's base hit points (`Trap_DeepPit.java:83-86`) |
| acid spray | 15 | Trap_AcidSpray | door, container, item | O, G | `roll(L+c,6,1)` melting acid (`Trap_AcidSpray.java:140`); component a lemon or lime |
| bear trap | 16 | Trap_BearTrap | room | E | `roll(L+c,6,1)` piercing; struggle pool `250 + 10*(L+c)` scaled by speed (`Trap_BearTrap.java:185-188`); reset 35 |
| small acid pit | 17 | Trap_AcidThruPit | room | T | Acid pit on travel-through (`Trap_AcidThruPit.java:51-62`) |
| poison gas | 17 | Trap_PoisonGas | door, container, item | O, G | Poison at L+c on every occupant (`Trap_PoisonGas.java:173-179`) |
| acid pit | 18 | Trap_AcidPit | room | E | Needs one lime. Fall `roll(L+c,6,1)` (`:132`); while sprung, `roll(L+c,6,1)` acid to the pit each tick (`:157`) |
| electric shock | 19 | Trap_ElectricShock | door, container, item | O, G | `roll(L+c,8,1)` electric (`Trap_ElectricShock.java:136-138`); component 10 lb metal |
| sparking trap | 19 | Trap_Spark | item | G | `roll(L+c,8,1)` burning (`Trap_Spark.java:136`); component 10 lb metal |
| boulders | 20 | Trap_Boulders | room (rock types) | E | `roll(L+c,20,1)` bashing to the victim only (`Trap_Boulders.java:149`); 50 lb rock |
| cave-in | 22 | Trap_CaveIn | room (cave) | E | `roll(L+c,20,1)` bashing to each occupant the setter may fight (`Trap_CaveIn.java:182`); blocks movement; 100 lb wood |
| spell blast | 23 | Trap_SpellBlast | door, container, item | O, G | The scroll's spell at L+c on the victim (`Trap_SpellBlast.java:188-192`) |
| boomerang | 24 | Trap_Boomerang | item | G | The item moves to the setter's inventory: "Magically, <T-NAME> appear(s) in your inventory." (`Trap_Boomerang.java:105-112`) |
| crushing room | 24 | Trap_CrushingRoom | room (indoors) | E | 16-tick cycle; `roll(L+c,30,1)` bashing for about nine ticks (`Trap_CrushingRoom.java:169-190`); 100 lb rock |
| vanishing trap | 24 | Trap_Vanishing | item | G | Sets `IS_INVISIBLE` in the base disposition (`Trap_Vanishing.java:92-93`) |
| avalanche | 25 | Trap_Avalanche | room (mountain) | E | `roll(L+c,20,1)` bashing to each occupant the setter may fight (`Trap_Avalanche.java:155`); 100 lb rock |
| flood room | 29 | Trap_FloodRoom | room (indoors) | E | 16-tick flood (`Trap_FloodRoom.java:229-263`); setter destroys 100 rock and 10 waterskins (`:136-149`) |

### Bombs (`bombs.txt`)

`bombs.txt` lists nine size tiers per bomb: Tiny, Small, Lesser, Moderate, Improved, Greater, Large, Huge and Massive. The tier levels are the Tiny level plus 10 per tier, so Tiny is 1 to 13 and Massive is 81 to 93. Every tier of a bomb uses the same class. The table gives the Tiny level and the class level.

| Bomb | Tiny lvl (bombs.txt) | Class lvl | Component | Effect and numbers |
|---|---|---|---|---|
| water | 1 | 1 | full fresh-water container | Extinguishes the target (`Bomb_Water.java:111`); no damage |
| smoke | 2 | 2 | wooden item | Spell_Blindness at L+c (`Bomb_Smoke.java:118-122`) |
| poison gas | 3 | 5 | poisoned drink | Poison at L+c (`Bomb_Poison.java:147-151`) |
| heat | 4 | 6 | oak or wood | `roll(L+c,2,1)` burning (`Bomb_Heat.java:204`); a destroyed non-player corpse leaves a cooked food item (see How it works) |
| pepper | 5 | 7 | peppers | Spell_Irritation at L+c (`Bomb_Pepper.java:118-122`) |
| sonic | 6 | 8 | reed | Underwater `roll(L+c,5,1)` sonic; otherwise Spell_Deafness and `roll(L+c,1,1)` (`Bomb_Sonic.java:172-186`) |
| explosive | 7 | 10 | coal | `roll(L+c,10,1)` burning (`Bomb_Explosive.java:138`) |
| stink | 8 | 12 | egg | Spell_StinkingCloud, cast on the victim by the setter (`Bomb_Noxious.java:118-123`) |
| spore | 9 | 15 | diseased meat | Disease at L+c (`Bomb_Spores.java:146-150`) |
| flame burst | 10 | 17 | lamp oil | `roll(L+c,12,1)` burning (`Bomb_FlameBurst.java:136`) |
| acid burst | 11 | 20 | lemons | `roll(L+c,24,1)` melting (`Bomb_AcidBurst.java:120`) |
| sticky | 12 | 25 | cotton | Attaches; 3-tick fuse; lock burst or item damage (`Bomb_Sticky.java:172-309`) |
| shaped | 13 | 28 | iron, at least 10 lb | Next to target; 10-tick fuse; `roll(dl,die,1)` (`Bomb_Shaped.java:262-292`) |

`Bomb_SpellBlast` (class level 23, `Bomb_SpellBlast.java:63-66`) is not in `bombs.txt`, and no other file refers to it (checked by grep). It is an orphan.

### The legacy family (23 classes, not player-settable)

Trigger by effect, from `Trap_Trap`:

- Enter: `Trap_Enter`, `Trap_EnterBlade`, `Trap_EnterGas`, `Trap_EnterNeedle`, `Trap_EnterPit`, `Trap_EnterSpell`.
- Leave: `Trap_ExitRoom`, `Trap_ExitPit`.
- Get: `Trap_Get`, `Trap_GetBlade`, `Trap_GetGas`, `Trap_GetNeedle`, `Trap_GetSpell`.
- Open: `Trap_Open`, `Trap_OpenBlade`, `Trap_OpenGas`, `Trap_OpenNeedle`, `Trap_OpenSpell`.
- Unlock: `Trap_Unlock`, `Trap_UnlockBlade`, `Trap_UnlockGas`, `Trap_UnlockNeedle`, `Trap_UnlockSpell`.

The Spell variants set the default spell to `Spell_Sleep` in their constructors (for example `Trap_GetSpell.java:72`).

### Messages worth reusing

- "<S-NAME> trigger(s) a trap!" and "<S-NAME> avoid(s) setting off a trap!" (`StdTrap` subclasses, for example `Trap_Boomerang.java:96-102`).
- "The exits are blocked off! Water starts pouring in!" (`Trap_FloodRoom.java:294`).
- "The walls start closing in around you!" (`Trap_CrushingRoom.java:170`) and "The walls begin retracting..." (`:189`).
- "<S-NAME> fall(s) into a pit!" and "<S-NAME> float(s) gently into the pit!" (`Trap_RoomPit.java:227,254`).
- "<S-NAME> notice(s) something about @x1 .. it's kinda sticky." (`Trap_Gluey.java:89`).

## Notable design ideas

- **A trap is an effect on a host.** It is not a room or item flag: the trap object sits in the host's effects and listens to the same messages as everything else. It can veto a move (`Trap_Enter`, `Trap_CaveIn`, `Trap_FloodRoom`), so no special movement code is needed.
- **Phases from one countdown.** The flood, crushing room and cave-in use a single `tickDown` with thresholds (`> 13`, `> 4`, `<= 2`). That gives a readable timeline with messages, and it is cheap.
- **Setter skill sets the clock.** The reset and the destruction time both come from the setter's qualifying level (`StdTrap.java:629`, `:640-641`). A skilled setter's traps reset faster and last longer.
- **Poison and disease by transfer.** A trap stores the affect from a poisoned drink or diseased food when it is laid. The crafting ingredient becomes the hazard.
- **Struggle pool.** The bear trap's pool (`Trap_BearTrap.java:187-188`) is a simple, visible escape counter, reduced by strength and level.
- **Social rules as flags.** A setter's own traps do not spring on them. A permanent trap spares people with land privileges and monsters from the same area (`StdTrap.java:169-188`). This is a clean rule set for a world with owners and residents.
- **Shaped and sticky bombs.** The bombs need a physical relation to a target (next to it, or stuck to it), and a material test for the component. The sticky bomb's lock-burst chance (`Bomb_Sticky.java:183-190`) is a single formula.
- **Accidents and discovery.** Failed laying can set the trap off on the layer (`Thief_Trap.java:372-376`), and walking into an armed trap of someone else's can spring it (`:330`). Both are good flavour, though they need fixing (below).

## Problems and cruft

Each item below was checked against the code. Items marked as design rather than defect are labelled.

1. **The base trap type is random, and the help says otherwise.** `Trap_Trap.trapType()` rolls `1d3-1` on every call (`Trap_Trap.java:72-75`). It is used on every spring and every unInvoke (`:384`, `:427`). The help file says the ability code selects the type ("Set ability to trap type (0-3)", `resources/help/traps.ini`, TRAP_TRAP). The code never reads the ability code for that. The base classes that do not override the roll are `Trap_Trap`, `Trap_Enter`, `Trap_Get`, `Trap_Open`, `Trap_Unlock` and `Trap_ExitRoom`. The auto-trapped doors and containers use these base classes (`CoffeeUtensils.java:341-400`), so their effect changes from trigger to trigger.
2. **Unclear pit cleanup.** `unInvoke` checks `trapType()==PIT_BLADE` (`Trap_Trap.java:427`). Because that is a fresh roll, the pit rooms of a base-class trap are cleaned up only about a third of the time. Pit rooms left behind are not destroyed.
3. **Exit pit does blade damage in rooms.** `Trap_ExitPit` is `PIT_BLADE` on a room host. `spring` checks `affected instanceof Exit` before making a pit (`Trap_Trap.java:394-396`), so on a room it does a blade. The help says "A pit trap triggered when leaving the room" (TRAP_EXITPIT). The reverse happens on exits: `Trap_OpenBlade` (help: "A blade trap triggered when opening") and `Trap_UnlockBlade`, which has the same `CAN_EXITS | CAN_ITEMS` pattern, make a pit when the host is an exit.
4. **Sleep gas help disagrees with the code.** The help lists "Rooms and Containers" (TRAP_SLEEPGAS). The class has `CAN_EXITS | CAN_ITEMS` (`Trap_SleepGas.java:52-55`), which is doors and containers, not rooms.
5. **Stale help entry.** The help has `TRAP_SPELLBOMB` ("Spell Bomb", targets Scrolls), but no class of that name exists. The real class, `Bomb_SpellBlast`, is orphaned (see Content). `TRAP_EXITROOM` is also titled "Trap_ExiRoom".
6. **Accident springs do nothing.** `Thief_Trap.java:372-376` and `Thief_MakeBomb.java:234-236` lay the trap, announce the accident, and call `T.spring(mob)`. The setter is the trap's invoker, and most StdTrap-family springs begin with `if((target!=invoker())...)` (for example `Trap_RoomPit.java:240`, `Trap_Boomerang.java:91`), so the call does nothing and prints nothing. In the bombs, `target==invoker()` routes to the avoid message instead. Either way the announced accident has no effect, and the trap stays armed for the next visitor.
7. **Recipe level and gate disagree, and the recipe level is overwritten.** The thief writes the recipe level into the trap with `setMiscText(":"+level+":")` (`Thief_Trap.java:118`, `Thief_MakeBomb.java:107`). That text sets the ability code, and `setTrap` then overwrites the ability code with the setter's bonus (`StdTrap.java:632`). The recipe level therefore changes only the displayed "Lvl" and the sort order. The gate is the class `trapLevel` through `maySetTrap` (`StdTrap.java:518-528`). For bombs the two differ a lot. For example, the tiny sticky bomb is listed at level 12 (`bombs.txt`), but `Bomb_Sticky.java:57` sets `trapLevel = 25`, so a thief below level 25 cannot see it.
8. **Shaped bomb always forces doors open.** `Bomb_Shaped.java:225-228` computes `adjustment = (level + bonus) - targetLevel*5`, clamps it to 0 or below, then tests `rollPercentage() > adjustment`. `rollPercentage()` returns 1 to 100 (`Libraries/Dice.java:409-411`), so the test is always true and the door is always unlocked and opened. The level term has no effect. If that was meant as a chance, the test is wrong.
9. **Persistent side effects.** `Trap_Vanishing` sets `IS_INVISIBLE` in the base disposition (`:92-93`), and `Trap_Gluey` sets non-removable and non-droppable through `CMLib.flags()` (`:94-95`), which writes the base `sensesMask` (`Libraries/Sense.java`, `setRemovable`/`setDroppable`). Disabling the trap removes only its effect, so these changes stay. Whether that is intended is unverified, but nothing reverts them.
10. **Shared mutable MOB.** `Trap_Trap.benefactor` is a static `StdMOB` (`Trap_Trap.java:64`). Every legacy spring writes its location and level (`:380-383`). On a threaded server, two legacy traps springing at the same moment could overwrite each other's values. (Threading not checked further.)
11. **Acid pit swallows the reset tick.** `Trap_AcidPit.tick` returns `false` when a reset tick arrives and the pit is not in its active state (`Trap_AcidPit.java:164`), so the countdown is not passed to `StdTrap.tick`. In practice this only affects disabled traps, which are dead anyway, so the effect is small.
12. **Double spring.** `Bomb_SpellBlast.spring` calls `super.spring(target)` twice (`Bomb_SpellBlast.java:161-162`). The second call is harmless, but it is dead code.
13. **Reset comment.** `StdTrap.java:62` says "5 minute reset is standard". At 4 s a tick, 60 ticks is 4 minutes. The default is always overwritten by `setTrap`, so this is cosmetic.
14. **Caged-animal checks create throwaway MOBs.** `Trap_SnakePit.getCagedAnimal` (`:84-101`), `Trap_RatPit` (`:76-92`) and `Trap_MonsterCage` (`:77-93`) call `unCageMe()` on each cage to read the race. `GenCaged.unCageMe` (`Items/Basic/GenCaged.java:135`) builds a new MOB each time, and nothing destroys it.
15. **Copy-paste classes.** Many classes differ only in messages: Needle and NeedlePrick, PoisonGas and SleepGas, Noise and Popper, Greasy and Ignition, Infected and SporeTrap. `getPoison` is the method name even where the component is not poison (`Trap_Greasy.java:77`, `Trap_Ignition.java:75`). The 18 legacy variant classes are the same pattern again.
16. **Multiplayer assumptions.** `mayIFight`, group membership, land titles and `DBUpdateRoom` assume several players sharing a world. In a single-player game the "setter" is almost always the player, and the permanent-trap rules have no meaning.
17. **GenTrap shares data by ID.** Every instance with one ID reads one static record (`GenTrap.java:56`). That is a deliberate design (the editor edits the class), but a copied trap cannot have its own numbers.
18. **Heavy numbers.** A roll of `(L+c) d20` at level 25 averages 263 per victim (see How it works). Room-wide traps hit every occupant the setter may fight, so a crowded room multiplies these numbers. The flood room deals no damage at all, only drowning and swimming effects, which may be intended.

## Takeaways for a Python SUD

- **Model a trap as a small object.** `Trap(host, kinds, trigger_set, effect, level, bonus, reset_turns, armed, owner)`. Keep the same states (armed, sprung, disabled), but drive them from the game's event dispatcher. There is no need for tick threads: decrement `reset_turns` in the main loop.
- **Replace the classes with data and about ten effects.** The 41 traps reduce to rows like `(name, level, host, trigger, effect, dice_count, dice_sides)`, which is what `traps.txt` already does. The effects that cover them are damage (one victim or the room), apply a named condition (poison, disease, sleep), a fall (two-room pit, or a single "pit" state the player climbs out of), a cage (monsters from data), movement block, timed phases, darkness, alarm to the area, sticky or burn on an item, and a spell on the victim. Drop the 23 legacy classes entirely.
- **Keep the save formula.** `roll_d100 <= save + victim_level - setter_level` is compact and fair. Store the save in the character, not in a trap.
- **Keep the skill-driven setter.** Use a single proficiency check of the form `(setter_level + skill_bonus - host_level) * 3` for laying and disarming. Keep the discovery roll for an armed trap (`30 + 5*bonus`), since it is good gameplay.
- **Measure reset in turns, keep the shape.** Use `reset_turns = max(1, round((30 - 0.75 * min(L, 29)) * k))` for some scale `k`, so that skill makes traps reset faster. Keep the destruction time tied to the setter's level.
- **Single-player simplifications.** Drop `mayIFight`, groups, the static benefactor, database saves and multi-owner land rules. Keep two social rules: a player's own traps do not spring on the player, and a trap placed in a resident's home area does not hurt that resident.
- **Make the level single.** Use one number for the listing, the gate and the damage. Do not write the recipe level into the ability code, and do not let the list show a level the gate ignores.
- **Make accidents and effects explicit.** A failed lay can set the trap off on the layer, but then the trap must actually fire on the layer. Write it that way on purpose.
- **Keep the good parts.** The bear trap's struggle pool, the flood and crushing-room phase timelines, the poison or disease transfer from a drink or food item, the sticky bomb's lock-burst chance, and the two-room pit that is created on demand and destroyed after use.
- **Avoid the bugs.** Keep flags as effect objects, not base stats, so removing a trap also removes its change. Pass a fixed trap type, not a fresh random roll per call. Do not clamp a value and then compare it to a 1-100 roll.

## Coverage

- **Read in full:** `StdTrap.java`, `StdBomb.java`, `GenTrap.java`, `Trap_Trap.java`, `Trap_Enter.java`, `Trap_ExitRoom.java`, `Trap_Get.java`, `Trap_Open.java`, `Trap_Unlock.java`, `Trap_RoomPit.java`, `Trap_SnakePit.java`, `Trap_AcidPit.java`, `Trap_SpikePit.java`, `Trap_DeepPit.java`, `Trap_CrushingRoom.java`, `Trap_FloodRoom.java`, `Trap_CaveIn.java`, `Trap_BearTrap.java`, `Trap_SpellBlast.java`, `Bomb_Sticky.java`, `Bomb_Shaped.java`, `Bomb_SpellBlast.java`, and the 11 remaining bombs (method bodies, with the license header, imports and braces skipped).
- **Read by method (spring, tick, setTrap, canSetTrapOn) and key fields:** the remaining StdTrap-family traps (Avalanche, Boomerang, Boulders, Darkfall, Electric shock, Gluey, Greasy, Ignition, Infected, Launcher, Monster cage, Needle, Needle prick, Noise, Popper, Poison gas, Sleep gas, Snare, Spark, Spore, Tripline, Vanishing, Weapon swing, Acid spray), `Trap_RatPit`, and the six thru-pit variants (62 lines each).
- **Skimmed (declarations and the trapType, canAffect and trigger lines only):** the legacy variant classes `Trap_EnterBlade`, `EnterGas`, `EnterNeedle`, `EnterPit`, `EnterSpell`, `ExitPit`, `GetBlade`, `GetGas`, `GetNeedle`, `GetSpell`, `OpenBlade`, `OpenGas`, `OpenNeedle`, `OpenSpell`, `UnlockBlade`, `UnlockGas`, `UnlockNeedle`, `UnlockSpell`.
- **Outside the bucket, for interaction only:** `Thief/Thief_Trap.java`, `Thief/Thief_MakeBomb.java`, `Thief/Thief_RemoveTraps.java`, `Ranger/Ranger_SetSnare.java` (grep), `Libraries/CoffeeUtensils.java` (341-420), `Libraries/CMMap.java:1207` (`getExitDir`), `Libraries/Dice.java` (roll and rollPercentage), `core/CMath.java` (the `?` operator), `Items/Basic/GenCaged.java` (`unCageMe`), `Libraries/Sense.java` (`setRemovable`, `setDroppable`), the trap and bomb data files, and `resources/help/traps.ini`.
- **Not read:** `resources/skills/atrapping.txt` and the animal-trapping code (a different system), and `Thief/Thief_MinorTrap.java` (only its data file was checked).
- **Verification:** the 41-row level table and the trap-class set were checked by script against `traps.txt`. The 13 bomb classes were checked against `bombs.txt`. Nothing was run.
