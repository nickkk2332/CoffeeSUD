# Druid chants, part 4: Chant_MassFungalGrowth to Chant_Rockfeet

> Part of the CoffeeMud NEWOVERVIEW. Bucket: `b054` (Abilities/Druid [part 4/7: Chant_MassFungalGrowth .. Chant_Rockfeet]) · 50 files · 9520 lines of Java.

All paths are relative to the CoffeeMUD folder. Java lives under `com/planet_ink/coffee_mud/`, which is shortened to `pkg/` in the tables below (for example `pkg/Abilities/Druid/Chant.java`). Line numbers were checked against the source.

## Summary

These 50 druid chants are the `Chant` subclasses that run alphabetically from `Chant_MassFungalGrowth` to `Chant_Rockfeet`. They all extend the `Chant` base class, which adds the chant rules (classification, weather mask, a 50% "emotional disruption" roll) on top of the generic ability engine in `StdAbility`. The chants fall into a few mechanical families: status effects on creatures (snares, plant chokes, the moon-family room effects), persistent room effects (the trap, the plant maze, the mud), item builders and modifiers (plant walls, mold, recharge), and meditation loops that slowly move a caster's faction scores. Several of them depend on a per-caster "plant" item found in the room, and most are gated by room terrain or time of day. The catalogue in the last section is the most reusable part for a single-player text game, but many checks assume other players (PK flags, groups, corpse ownership, followers).

## Key files and types

| File | Role | Lines |
|---|---|---|
| `pkg/Abilities/Druid/Chant.java` | Base class: classification, default quality, weather mask, alignment disruption roll | 201 |
| `pkg/Abilities/Druid/Chant_PlantTrap.java` | Room trap; springs on entry and casts a plant choke or constriction on the victim | 331 |
| `pkg/Abilities/Druid/Chant_Reincarnation.java` | 1800-tick race geas; random new race on death; committed at expiry | 293 |
| `pkg/Abilities/Druid/Chant_ResuscitateCompanion.java` | Auto-invoked; tracks dead animal companions and revives their corpses | 287 |
| `pkg/Abilities/Druid/Chant_PlantMaze.java` | Builds a runtime grid room ("WoodsMaze") and moves the room's occupants into it | 266 |
| `pkg/Abilities/Druid/Chant_PlantSelf.java` | Sun-rooted meditation with faction drift; uprooted by movement, speech or combat | 263 |
| `pkg/Abilities/Druid/Chant_PlantSnare.java` | Binds all targets in the room; struggle budget per target | 234 |
| `pkg/Abilities/Druid/Chant_PlantChoke.java` | Caster's plant wraps a victim's neck and blocks breathing | 233 |
| `pkg/Abilities/Druid/Chant_PlantConstriction.java` | Caster's plant wraps an arm (speed halved) or a leg (movement halved) | 255 |
| `pkg/Abilities/Druid/Chant_PlantWall.java` | Item barrier for ranged combat; absorbs attacks and spells | 261 |
| `pkg/Abilities/Druid/Chant_PurpleMoon.java` (also RedMoon, PaleMoon, PeaceMoon, MoonCalf, PiercingMoon) | Moon family: room effects with per-alignment or per-stat modifiers | 219, 213, 216, 211, 224, 193 |
| `pkg/Abilities/Druid/Chant_NaturalBalance.java`, `Chant_NaturalOrder.java` | Meditation loops that drift alignment or inclination | 249 each |
| `pkg/Abilities/Druid/Chant_MetalMold.java`, `Chant_Mold.java` | Acid damage to metal; timed mold that destroys an item | 157 each |
| `pkg/Abilities/Druid/Chant_PlanarLink.java`, `Chant_PlanarAdaptation.java`, `Chant_PlaneWalking.java` | Planar travel and breathing adaptation | 230, 183, 137 |
| `pkg/Abilities/Druid/Chant_MuddyGrounds.java` | Turns the room to mud (room effect plus a mud item) | 256 |
| `pkg/Abilities/Druid/Chant_ReverseFortune.java` | Flips the sign of a target's auto-award parameters | 159 |
| `pkg/Abilities/Druid/Chant_PoisonousVine.java` | Summons a monster built from the caster's level | 165 |
| `pkg/Abilities/StdAbility.java` (outside bucket) | Duration rules (`beneficialAffect`, `maliciousAffect`), cost handling, `copyOf` | n/a |
| `pkg/Abilities/Druid/Druid_MyPlants.java` (outside) | `myPlant(room, mob, n)` finds the caster's plant in a room | n/a |
| `pkg/Abilities/Druid/Chant_SummonPlants.java`, `Chant_SummonFungus.java` (outside) | Base classes for plant and fungus summoning; `buildMyThing` | n/a |
| `pkg/Abilities/Misc/AutoAwards.java` (outside) | Applies auto-award parameter strings; `applyAward` with `reverse` | n/a |
| `pkg/Libraries/Dice.java` (outside) | `roll(n,d,m)` sums n dice of d sides plus m (so `roll(1,10,0)` gives 1 to 10); `rollPercentage()` gives 1 to 100 | 398-412 |

## How it works

### 1. The base class and the chant rules

`Chant` (`pkg/Abilities/Druid/Chant.java`) sets the common identity and rules:

- Trigger words are `CHANT` and `CH` (`Chant.java:84`). The default quality is `QUALITY_OK_SELF` (`Chant.java:87-90`), and the default target is `CAN_MOBS` (`Chant.java:99-108`).
- `classificationCode()` returns `ACODE_CHANT`, or `ACODE_SKILL` when the chant has been set to "render mundane" (`Chant.java:110-123`). In that mode the verbal cast becomes ordinary noise (`Chant.java:69-82`). This lets a chant be used as a non-magical skill.
- `chantAlignmentCheck` (`Chant.java:167-189`) runs inside `Chant.invoke` for non-auto casts by a player who owns the ability, does not disregard armour checks, and is not rendering mundane (`Chant.java:169-173`). It rolls `rollPercentage()<50` (`Chant.java:174`). On a failed roll it can stop the chant in one of two ways:
  - If the caster's faction is not appropriate: "Extreme emotions disrupt your chant." (`Chant.java:178`).
  - If the caster's armour is not non-metal: "... watch(es) <S-HIS-HER> armor absorb <S-HIS-HER> magical energy!" (`Chant.java:184`).
- `weatherQue(Room)` (`Chant.java:126-165`) returns a bit mask: `WIND=1`, `RAIN=2`, `HOT=4`, `COLD=8`, `CALM=16`. The mask depends on the area's climate (for example, `WEATHER_CLEAR` gives all four weather bits and `WEATHER_FOG` gives RAIN|CALM). Blizzard, thunderstorm and heat wave return nothing, and indoor rooms return nothing. Only the weather-summoning chants outside this bucket read it (`Chant_SummonRain.java`, `Chant_SummonSquall.java`, `Chant_CalmWind.java` and others).

### 2. Durations, the tick, and costs

Durations are counted in mob ticks. The tick length is `TIME_TICK = 4000` ms (`pkg/core/CMProps.java:681`), so 1800 ticks is 7,200 seconds, or two hours at the default setting. The duration helpers are in `StdAbility`:

- **Beneficial default** (`beneficialAffect(mob,target,asLevel,0)`, `StdAbility.java:2341-2349`). `getBeneficialTickdownTime` (`StdAbility.java:2312-2330`) uses a positive adjustment as an absolute number of ticks. Zero means the default `beneficialTicks = (casterLevel*4)+40`. When a player casts on another mob, the caster level is capped at the target's level plus `EXPRATE`. `adjustBeneficialTickdownTime` (`StdAbility.java:2273`) clamps the result between 5 and `TICKSPERMUDDAY`.
- **Malicious default** (`maliciousAffect`, `StdAbility.java:2185`). `getMaliciousTickdownTime` (`StdAbility.java:2174-2182`) uses `round(adjustedLevel*1.1)+25` ticks at zero, then runs it through `adjustMaliciousTickdownTime`.
- A negative adjustment is a percentage of the default. `Ability.TICKS_FOREVER = Integer.MAX_VALUE-1000` (`pkg/Abilities/interfaces/Ability.java:46`) is used for permanent meditation effects.
- `TICKSPERMUDDAY` is `millisPerMudHour * hoursInDay / tickMillis` (`pkg/Common/DefaultTimeClock.java:313`).

Costs come from `overrideMana()`. The base returns `COST_NORMAL` (`StdAbility.java:309-312`). Flat values in this bucket are PlantTrap 100 (`Chant_PlantTrap.java:75-79`), Reincarnation 200 (`Chant_Reincarnation.java:71-75`), RechargeShards 100, RefreshRunes 50 and RestoreMana 50. `Ability.COST_ALL - N` means N percent of maximum mana, computed at `StdAbility.java:1614-1618`. MoveSky uses `COST_ALL-99` (99%), and PlaneWalking uses `COST_ALL-90` (90%).

### 3. Room effects: the trap, the moon family, the maze and the mud

**Plant trap** (`Chant_PlantTrap.java`). The trap is a room effect (`CAN_ROOMS`, line 66) that implements the `Trap` interface. It is cast only outdoors, and only if the caster already knows plant choke or plant constriction (`Chant_PlantTrap.java:285-304`). The room effect is applied with `beneficialAffect(mob,target,asLevel,0)` (`Chant_PlantTrap.java:322`). When the trap springs:

- `executeMsg` (`Chant_PlantTrap.java:241-249`) watches for `TYP_ENTER` into the room by someone who is not the invoker and not following the invoker. That person is passed to `spring()`.
- `doMyThing` (`Chant_PlantTrap.java:203-231`) skips the trap if the invoker cannot fight the target, the target is in the invoker's group, or the target is exempt (`isLocalExempt`, lines 169-188: land-title holders with privileges, and monsters from the same area). Otherwise the target rolls against its trap-save stat. If the target fails, a random choice from `{Chant_PlantChoke, Chant_PlantConstriction}` (filtered to the abilities the invoker knows, line 202) is cast with `A.invoke(target,target,true,0)` (`Chant_PlantTrap.java:227`). The victim is passed as the caster.
- If the room is owned land, the trap is made non-uninvokable (`Chant_PlantTrap.java:316-318`).

**Moon family.** Most of the moon chants share one shape: a room effect that checks `canSeeTheMoon` on every tick and removes itself when the moon is hidden. Occupants can receive a copy of the effect (PaleMoon, MoonCalf, PeaceMoon). Only one moon-domain effect may sit in a room, checked by `classificationCode & ALL_DOMAINS == DOMAIN_MOONALTERING` (`Chant_PurpleMoon.java:185-194`). The distinctive effects are:

| Chant | Effect in the code | Source |
|---|---|---|
| PurpleMoon | Damage taken by the target changes by `round(value/2)`. Chaotic targets have it negated, so they take roughly half. Lawful targets take roughly one and a half times. Other targets are unchanged. Cast only by chaotic casters (`Chant_PurpleMoon.java:125-136, 145-146`). | `pkg/Abilities/Druid/Chant_PurpleMoon.java` |
| RedMoon | Same formula with the alignments swapped: evil targets take about half, good targets take about one and a half times (`Chant_RedMoon.java:35-48`). | `pkg/Abilities/Druid/Chant_RedMoon.java` |
| PaleMoon | Occupants get `STAT_SAVE_POISON - 50` (`Chant_PaleMoon.java:141`). | `pkg/Abilities/Druid/Chant_PaleMoon.java` |
| MoonCalf | Occupants get `INTELLIGENCE - 10`, with a floor of 1 (`Chant_MoonCalf.java:147-149`). Flagged `FLAG_MINDALTERING`. | `pkg/Abilities/Druid/Chant_MoonCalf.java` |
| PeaceMoon | Any malicious message in the room (source, target or others, and not `MASK_ALWAYS`) is vetoed. The source is told "Nah, you feel too peaceful under that bright moon." (`Chant_PeaceMoon.java:103-123`). Both parties are made peaceful (`:117-118`). | `pkg/Abilities/Druid/Chant_PeaceMoon.java` |
| PiercingMoon | Castable only at night and dusk, and only outdoors (`Chant_PiercingMoon.java:152, 157`). The room effect removes itself when the time of day is not dusk or night (`Chant_PiercingMoon.java:106-121`). Flagged `FLAG_WEATHERAFFECTING` (`:86`), so it is seen by `Chant_ControlWeather.okMessage` (`pkg/Abilities/Druid/Chant_ControlWeather.java:95-101`). | `pkg/Abilities/Druid/Chant_PiercingMoon.java` |

**Plant maze** (`Chant_PlantMaze.java`). Invoke requires a plant of the caster in the room (`:173`). It creates a new `GridLocale` from the `WoodsMaze` class (`:183`), sized `10 + X1Level + XLEVELLevel` on each axis (`:184-185`). It then:

- Names the room "<Plant> Maze", derived from the plant's name by pluralising and un-pluralising it (`:189-192`).
- Adds `Prop_NoTeleportOut` (`:193`) and copies the original room's exits into the new room.
- Moves every occupant into a random grid child, with "appears out of" and "disappear(s) into" messages.
- Ends when the plant is gone (`tick`) or when the invoker leaves in a quit or shutdown (`okMessage`). In those cases the occupants are returned to the original room.

**Muddy grounds** (`Chant_MuddyGrounds.java`). The room effect multiplies the room's weight by 2 and adds 1 (`:100`) and adds the "muddy" ambiance. A new `GenItem` called "the mud" is added to the room (`:228-235`). It is unsavable unless the caster owns the land (`:224-227`). The effect prints "You are slogging through the mud..." on entry and advance (`executeMsg`).

### 4. Binding, choking and the struggle pattern

**Snare** (`Chant_PlantSnare.java`). Snare has a range of 2 (`:72`), flags `FLAG_BINDING` (`:92`), and sets `IS_BOUND` (`:101`). Setup needs an outdoor woodland, plains, hills, jungle or swamp room, or a wooden or vegetation material (`castingQuality` at `:151`, `invoke` at `:175`). For each target in range:

- The struggle budget is `400 + 100*XLEVEL`, scaled by the target's base speed (`:216-217`).
- Each struggle, which is a hands or move action, costs `STR*4` from the budget (`:123`). The action is vetoed. When the budget goes negative, the binding ends and the target is told "...manage(s) to break <S-HIS-HER> way free of the plants." (`unInvoke`).
- The bind lasts `adjustedLevel*10` ticks (`:220`).

**Plant choke** (`Chant_PlantChoke.java`). Targets a mob (`CAN_MOBS`) and needs a neck slot. The caster's plant moves onto the victim and is worn at the neck (`WORN_NECK`). It lasts 5 ticks (`:224`). While it is worn, `affectPhyStats` sets `CAN_NOT_BREATHE` on the wearer (`:165`). The wearer removes it with a chance of `roll <= STR*3` (`:150`). Monsters are automatically stripped of the plant on each tick (`tick`, around `:135`).

**Plant constriction** (`Chant_PlantConstriction.java`). Chooses arms or legs at random from those the target has (`Chant_PlantConstriction.java:240`). It lasts 20 ticks (`:246`). Arms halve speed (`affectPhyStats`, `:165`). Legs halve movement (`affectCharState`, `:174`). The removal chance is `roll <= STR*4` (`:150`). The two plant binds use different constants (3 times STR for choke, 4 times STR for constriction).

**Rockfeet** (`Chant_Rockfeet.java`). A malicious effect on a mob's hands and feet. On every hand or move action the code rolls `rollPercentage() > STR*3` (`:122`). On a hit it costs 1 movement and adds fatigue equal to one tick's worth of milliseconds (`:123-128`). The effect is applied with `maliciousAffect(mob,target,asLevel,0,-1)` (`invoke`).

### 5. Plant ownership and plant items

Most plant-based chants need a plant that belongs to the caster. `Druid_MyPlants.myPlant(room, mob, n)` returns the n-th item in the room that belongs to the mob (`pkg/Abilities/Druid/Druid_MyPlants.java:137-154`). If none is present and the chant is auto-invoked, `Chant_SummonPlants.buildPlant` creates one (`Chant_PlantChoke.java:173, 195`; the same in `Chant_PlantConstriction.java:209`).

Plant items are also created by the chants themselves:

- Plant wall (`Chant_PlantWall.java`): an item barrier. It needs ranged combat, and its range is 1 to 10 (`maxRange adjustedMaxInvokerRange(10)`, `minRange 1`). Its HP is `hitpoints/6 + 2*(X1Level + XLEVELLevel)` (`:241`). Each attack from a ranged weapon or a hack from a melee attacker reduces it. A ranged spell is absorbed with the message "The plant wall absorbs <O-NAME> from <S-NAME>." (`:160`). The wall is destroyed when HP drops below zero.
- Plant bed (`Chant_PlantBed.java`): a non-gettable `GenBed` called "a plant bed". It is sleep furniture with one rider, weight 1000, and a `Prop_RideResister` for "disease poison" (`:127` onwards). It is not castable indoors, in deserts, in cities, underwater, in the air or on the water surface.
- Nectar (`Chant_Nectar.java`): an outdoor fountain. It is a `Spring` item named "an enormous flower" carrying a `Poison_Liquor` effect (`:211`).
- Plant pass and reef walking (`Chant_PlantPass.java`, `Chant_ReefWalking.java`): transport between the caster's plant rooms, by index or name, with `FLAG_TRANSPORTING`. Reef walking extends it and requires the target room to be watery (`Chant_ReefWalking.java:84`).

When the plant chants are used on a large area, `Chant_SummonPlants.buildMyThing` (`pkg/Abilities/Druid/Chant_SummonPlants.java:174`) grants the area a `Chant_DruidicConnection` effect once the caster's plants cover at least 50% of the visitable rooms (`Chant_SummonPlants.java:195-200`). Mass Fungal Growth uses this path.

### 6. Meditation loops: PlantSelf, NaturalBalance and NaturalOrder

These three chants are permanent effects (`TICKS_FOREVER`, for example `Chant_PlantSelf.java:251`). Each has the same loop, in `tick`:

- If the caster is in combat, the effect ends (`Chant_PlantSelf.java:133`; the `else` at the end of the tick).
- Otherwise, if less than 60 seconds of real time have passed since the last pulse (`:140`), it does nothing. Otherwise it calls `proficiencyCheck(null,0,false)` (`:142`) and resets the timer (`:144`).
- Faction drift: `Chant_PlantSelf.java:148` computes `oneHalfPct = round(total*0.015)`, which is 1.5% of the total alignment range. NaturalBalance and NaturalOrder use half of 1% (`Chant_NaturalBalance.java:159`, `Chant_NaturalOrder.java:159`). The shift is applied only if the faction's `MODERATE` purity is below 99, and its sign depends on whether `EVIL` purity is below `GOOD` purity (in the code). PlantSelf uses the alignment score. NaturalOrder applies the same drift to the inclination score.
- A flavour line is shown from a 10-way switch, `roll(1,10,0)` (`Chant_PlantSelf.java:165`).

PlantSelf has extra rules. It can be cast only outdoors, in the sun, not in combat, and only in fertile terrain (`Chant_PlantSelf.java:211-237`). Any movement, hand action, or sound-making mouth action (`executeMsg`, `:105`) causes the caster to "uproot" themselves, which ends the effect. NaturalBalance and NaturalOrder are outdoors-only and not allowed in cities (`Chant_NaturalBalance.java:222`; the same check is at `Chant_NaturalOrder.java:222`). They share the same `executeMsg` rules.

### 7. Item builders, modifiers and utilities

- **Mold** (`Chant_Mold.java`). Refuses rooms, exits and non-food items (`:102-106`). It targets an unworn food item, or a mob, whose food items all receive the effect (`:138-140`). The food gets a `Disease_Lockjaw` effect and a `maliciousAffect` of `TICKSPERMUDMONTH*3` ticks (`:131`). When the malicious effect ends it calls `item.destroy()` (`unInvoke`). The mold is therefore a timed destroy.
- **Metal mold** (`Chant_MetalMold.java:132-142`). The target is a metal item, preferring worn or wielded ones. Damage is `2 + sum over num rolls of 1d2+2`, where `num = (level + X1Level + 2*XLEVELLevel)/2`. Bonus items take half. Enchanted items divide by `1 + ability`. The damage is applied as an acid item-damage message.
- **Plague and poison** (`Chant_Plague.java`, `Chant_Poison.java`). Both are thin wrappers that cast `Disease_Plague` or `Poison_Venom` on the target. Plague refuses to cast on a target who already has the disease (`castingQuality`).
- **Neutralize poison** (`Chant_NeutralizePoison.java`). Removes every effect of class `ACODE_POISON` from the target (`returnOffensiveAffects`, around lines 90-100) and turns a poison drink into fresh water. It implements `MendingSkill`.
- **Restore mana** (`Chant_RestoreMana.java:121`). Adds 50 mana to the target when the cast message value is not positive. Druids learn it as a secret skill at level 3 (`pkg/CharClasses/Druid.java:167`).
- **Refresh runes** (`Chant_RefreshRunes.java`). Adds one use to a scroll whose spells include a chant. It refuses if the scroll already has more uses than spells.
- **Recharge shards** (`Chant_RechargeShards.java`). Adds `RECHARGE_AMT = 5` charges (`:46`) to a `Wand`. A fully charged wand is destroyed.
- **Release magic** (`Chant_ReleaseMagic.java`). Calls `CMLib.utensils().disenchantItem(target)`. The item either vanishes or "fades and becomes dull", losing its bonus flag and having its level set to at least 1.
- **Read runes** (`Chant_ReadRunes.java:107-110`). Adds a temporary copy of the chant to the caster while calling `postRead`, then removes it.
- **Reabsorb** (`Chant_Reabsorb.java`). Destroys an unworn item on the ground. It refuses other people's player corpses (`:114-117`) and refuses in indoor, city, water and spaceport rooms.
- **Nectar weave** (`Chant_NectarWeave.java`). Re-materials an unworn item to honey and renames it. A drink effect (`drinkFlag`) removes itself when the drink is empty.

### 8. Death, companions and reincarnation

**Reincarnation** (`Chant_Reincarnation.java`) has three stages, and the race change is committed late:

1. **Cast.** The target must not be a monster, undead or golem (`:240-246`). Non-auto casts on other players need a group or PK flags (`:248-258`). A successful cast applies the effect with `beneficialAffect(mob,target,asLevel,1800)` (`:280`). If the target already has the effect, casting it again removes it (`:229-239`).
2. **Geas.** `affectPhyStats` and `affectCharStats` (`:87-117`) show the target as the new race while the effect runs. The name becomes "<Name> the <Race>", or "a <Race> called <Name>" if the name has a space (`:92-95`).
3. **Death.** When the target dies (`okMessage`, `:187`), a new race is picked from `CMClass.randomRace()`, looping until it is not a golem, can breed with itself, is in the fantasy theme (`Area.THEME_FANTASY`), is not the current race, and is not `StdRace` (`:191-197`). The effect is kept through death and resurrection by setting `canBeUninvoked=false` (`:203`), and "You are being reincarnated as a <race>!!" is shown (`:199`).
4. **Expiry.** On the tick where the timer reaches 1 (`tick`, `:137-156`), the new race is written into the base race (`mob.baseCharStats().setMyRace(newRace)`, `:147`). The message is "Your reincarnation geas is lifted as your form solidifies." (`:144`). So the chant does not undo itself. It is a delayed commit.
5. **Death limit.** If the server's `PLAYERDEATH` setting contains a `PUR n` token and the player's death count is at least `n-1`, `okMessage` returns false (`:204-221`). The outcome of that veto for the death itself is not traced here.

**Resuscitate companion** (`Chant_ResuscitateCompanion.java`) is auto-invoked and cannot be uninvoked (`isAutoInvoked` returns true, `canBeUninvoked` returns false). `okMessage` watches for an animal that is following the caster and dies (`:128`). It adds the animal's dead body to a list of up to 10 weak references (`:166`). The dead body is only added after the death message has finished, through `addTrailerRunnable`. `invoke` (from `:182`) refuses bodies that are not on the list, player corpses (`:196-200`), and targets in combat. On success it sets the saved mob to null, destroys the corpse, calls `bringToLife`, and schedules a check that prints "twitch(es) and fade(s) away" if the revived mob failed to appear. `supportsMending` lets the chant mend animal corpses.

### 9. Planar, sky and time chants

- **Planar link** (`Chant_PlanarLink.java`). The caster must be on a plane (`getPlaneOfExistence != null`) and in an area that holds a `PlanarAbility` (`:185-189`). The room ID must start with the area name, otherwise the magic "won't seem to work in this exact place" (`:194`). The duration is `TicksPerHour + TicksPerMinute*adjustedLevel + 30*TicksPerMinute*XLEVELLevel` (`:212-216`). The link binds `boundPlane` and records the original area name for group members, whose planar travel is then routed through `okMessage`.
- **Planar adaptation** (`Chant_PlanarAdaptation.java`). Requires a plane. `affectCharStats` changes `getBreathables()` to the plane's breathing set (`:114` onwards), and re-casting with a different set ends the old effect first.
- **Plane walking** (`Chant_PlaneWalking.java`) extends `StdPlanarAbility`. It needs a caster's plant in the room (`:113-125`) and costs 90% of mana. Non-auto casts set `alwaysRandomArea = true`.
- **Move sky** (`Chant_MoveSky.java:104-135`). Moves the area's time of day to a target: NIGHT goes to DAWN, DUSK goes to DAWN, DAWN goes to DUSK, DAY goes to DUSK. It does this by calling `TO.tickTock(1)` in a loop of `getHoursInDay()` iterations (`:131-135`). It costs 99% of mana.
- **Predict tides** (`Chant_PredictTides.java:123`). In a watery room, or one whose resources include fish, or one that is a boarded area on water, it prints `getTidePhase(R).getDesc()`. A class hook uses it (`pkg/CharClasses/Mer.java:300`).
- **Predict weather** (`Chant_PredictWeather.java:92-104`). Outdoors, on each mob tick, it prints the area climate's `nextWeatherDescription` whenever that differs from the last one shown (`lastPrediction`, `Chant_PredictWeather.java:102-104`).
- **Predict phase** (`Chant_PredictPhase.java:94-160`). Looks for a moon-summoning effect in the room and names it (`Chant_PredictPhase.java:104-105`). At XLEVEL it also computes the stat "sway" the moon has on the caster and lists it, in lines like "movement +N", "hit points -N", "armor +N" (`:116-160`). The exact sway calculation was not traced.

### 10. Combat and defence

- **Meteor strike** (`Chant_MeteorStrike.java:134`). Range 1 to 5. Each proper target takes `CMLib.dice().roll(maxDie,6,30)` damage, which is 30 plus `maxDie` six-sided dice, where `maxDie = (adjustedLevel + 2*X1Level)/2`. The damage is halved if the message value is positive. It is applied with `TYP_FIRE` and bashing.
- **Prayer ward** (`Chant_PrayerWard.java:107-125`). Vetoes a malicious cast that is classed as `ACODE_PRAYER` and aimed at the warded mob, with `rollPercentage() < 35`. That is 34 of the 100 possible rolls (see Dice.java:409-412). Its `castingQuality` is indifferent when the target's current victim has no prayer abilities (`Chant_PrayerWard.java:92-103`).
- **Repel vermin** (`Chant_RepelVermin.java:89, 94-111`). Sets `pointsRemaining = 8 + adjustedLevel/5`. Each vermin melee advance in the room costs one point and is struggled against. The effect is vetoed while points remain.
- **Poisonous vine** (`Chant_PoisonousVine.java:90-144`). Extends `Chant_SummonVine`. `determineMonster` creates a `GenMOB` with race "Vine", named "a poisonous vine". Level is the greater of the adjusted level and `caster level - 5 + XLEVEL/2` (`Chant_PoisonousVine.java:95-97`). The ability stat is set to `CMProps.getMobHPBase()+2`, damage to `6 + 5*(level/5)`, and armor stat to `100 - (30 + level/2)` (`:105-140` area; the exact effect of the ability stat on hit points was not traced). It is NO_REJUV, gives no experience (`Prop_ModExperience` with 0, `:142`), and is tagged "SUMMONED_BY:<caster>" (`:144`). Its poison is chosen by `classlevel/5`: 0 is `Poison_Sting`, 1 is `Poison_Bloodboil`, 2 is `Poison_Venom`, and 3 or more is `Poison_Decreptifier` (`:116-124`).
- **Reverse fortune** (`Chant_ReverseFortune.java:93, 138-141`). Sets the `REVERSER` stat on the target's `AutoAwards` effect. `AutoAwards.applyAward(..., reverse)` (`pkg/Abilities/Misc/AutoAwards.java`, around line 446) then swaps every `+` and `-` in each award's parameter string. A good bonus becomes a penalty, and the reverse holds until the effect ends.

### 11. Other systems this bucket touches

- Class grants (`CMLib.ableMapper().addCharAbilityMapping(ID, level, abilityID, autoGain)`). The `autoGain` flag means "granted free, not bought" (`pkg/Libraries/interfaces/AbilityMapper.java:59`). Examples are in the next section.
- Faction and alignment (`CMLib.factions()`). PlantSelf, NaturalBalance and NaturalOrder use `getAlignPurity` and `postSkillFactionChange`. SkyWatcher gates some chants on which alignments are loaded (`pkg/CharClasses/SkyWatcher.java:205-212`).
- Weather summoning (`weatherQue`). Used by the weather-summoning chants outside this bucket.

## Content worth mining

The table lists all 50 chants. Each file is under `pkg/Abilities/Druid/`. Quality and domain come from the code. Mana is the `overrideMana` value, or "default" when that method is not overridden.

| File (lines) | ID | Quality · domain | Mana | What the code does |
|---|---|---|---|---|
| Chant_PlantTrap (331) | Chant_PlantTrap | Malicious · plant control | 100 | Room trap: springs when a non-follower enters; the victim is attacked by a random plant choke or constriction cast with the victim as caster. Owned land makes it non-removable. |
| Chant_Reincarnation (293) | Chant_Reincarnation | Other · breeding | 200 | 1800-tick race geas. On death a random fantasy race is chosen; the new race is written into the base race when the geas expires. |
| Chant_ResuscitateCompanion (287) | Chant_ResuscitateCompanion | Indifferent · animal affinity | default | Auto-invoked, not uninvokable. Remembers up to 10 dead animal followers and revives their corpses. Mends animal corpses. |
| Chant_PlantMaze (266) | Chant_PlantMaze | Malicious · plant growth | default | Builds a runtime grid room sized from the caster and moves the occupants into it. Cleans up on quit, shutdown or room reset. |
| Chant_PlantSelf (263) | Chant_PlantSelf | Self · shape shifting | default | Outdoors in sun: forever rooted meditation with 60 s faction drift. Ends on movement, hand or speech actions, or combat. |
| Chant_PlantWall (261) | Chant_PlantWall | Indifferent · plant growth | default | Item barrier for ranged combat. Its HP is the caster's hit points/6 plus 2 per X1 and XLEVEL level (`:241`). Absorbs ranged attacks and spells. |
| Chant_NectarWeave (257) | Chant_NectarWeave | Indifferent · nature lore | default | Re-materials an unworn item to honey and renames it; a drink effect removes itself when empty. |
| Chant_MassFungalGrowth (103) | Chant_MassFungalGrowth | Indifferent · plant growth | default | Radiant search (range adjLevel + 2*XMAXRANGE) for cave or rock rooms, then builds fungus in up to adjLevel rooms (extends Chant_SummonFungus). |
| Chant_Rockfeet (194) | Chant_Rockfeet | Malicious · shape shifting | default | Heavy hands and feet: hand and move actions may cost 1 movement and fatigue (roll above STR*3). |
| Chant_RepelVermin (257) | Chant_RepelVermin | Beneficial other · animal affinity | default | Anti-vermin field: absorbs vermin advances for 8 + adjLevel/5 points. |
| Chant_MuddyGrounds (256) | Chant_MuddyGrounds | Malicious · weather mastery | default | Room becomes mud: mud item, room weight x2+1, muddy ambiance, slogging messages. |
| Chant_PlantConstriction (255) | Chant_PlantConstriction | Malicious · plant control | default | Plant wraps an arm (speed halved) or a leg (movement halved) for 20 ticks. |
| Chant_NaturalBalance (249) | Chant_NaturalBalance | Self · enduring | default | Forever meditation. Drifts alignment by 0.5% of range per minute while not in combat. |
| Chant_NaturalOrder (249) | Chant_NaturalOrder | Self · enduring | default | As NaturalBalance but drifts inclination (law and chaos). |
| Chant_Nectar (234) | Chant_Nectar | Indifferent · plant growth | default | Outdoors: an enormous flower that is a poisoned spring (Poison_Liquor). |
| Chant_PlantSnare (234) | Chant_PlantSnare | Malicious · plant control | default | Binds room targets with a struggle budget; bind lasts adjLevel*10 ticks. |
| Chant_PlantChoke (233) | Chant_PlantChoke | Malicious · plant control | default | Plant on the neck blocks breathing for 5 ticks; removal chance 3*STR percent. |
| Chant_PlanarLink (230) | Chant_PlanarLink | Self · cosmology | default | Links caster or target to the current plane's area; 1 hour, plus adjusted-level minutes, plus 30 minutes per XLEVEL. |
| Chant_MoonCalf (224) | Chant_MoonCalf | Malicious · moon altering | default | Moon room effect: occupants lose 10 intelligence. |
| Chant_PurpleMoon (219) | Chant_PurpleMoon | Malicious · moon altering | default | Room effect: chaotic targets take half damage, lawful targets take about 1.5x. Chaotic casters only. |
| Chant_PaleMoon (216) | Chant_PaleMoon | Malicious · moon altering | default | Room effect: occupants get -50 poison save. |
| Chant_RedMoon (213) | Chant_RedMoon | Malicious · moon altering | default | Room effect: evil targets take half, good targets about 1.5x. Evil casters only. |
| Chant_PeaceMoon (211) | Chant_PeaceMoon | Indifferent · moon altering | default | Room effect: malicious actions vetoed; combatants made peaceful. |
| Chant_PiercingMoon (193) | Chant_PiercingMoon | Indifferent · moon summoning | default | Outdoors at night or dusk only; ends when time of day changes. Weather-affecting flag. |
| Chant_PlantPass (193) | Chant_PlantPass | Indifferent · shape shifting | default | Travel to another of the caster's plant rooms (transporting flag). |
| Chant_PlanarAdaptation (183) | Chant_PlanarAdaptation | Self · cosmology | default | Adjusts breathable gases to the current plane's breathing set. |
| Chant_Reabsorb (171) | Chant_Reabsorb | Malicious · deep magic | default | Destroys an unworn ground item; not other players' corpses; not indoors, city or water. |
| Chant_PoisonousVine (165) | Chant_PoisonousVine | Beneficial self · plant control | default | Summons a poisonous vine monster; level from caster level; no XP; poison by class level. |
| Chant_PredictPhase (164) | Chant_PredictPhase | Indifferent · moon summoning | default | Names a moon-summoning effect in the room; at XLEVEL it lists the moon's stat effects on the caster. |
| Chant_ReverseFortune (159) | Chant_ReverseFortune | Other · cosmology | default | Flips + and - in the target's auto-award parameters. |
| Chant_PrayerWard (158) | Chant_PrayerWard | Beneficial self · preserving | default | Vetoes malicious prayer casts at the warded mob, about 34% of the time. |
| Chant_MetalMold (157) | Chant_MetalMold | Malicious · plant growth | default | Acid damage to a metal item (worn items preferred). |
| Chant_Mold (157) | Chant_Mold | Malicious · plant growth | default | Lockjaw and a 3 MUD month timer on food items (or a mob's food); the food is destroyed when the timer ends. |
| Chant_NeutralizePoison (155) | Chant_NeutralizePoison | Beneficial other · preserving | default | Removes all poison-class effects; poison drink becomes fresh water. |
| Chant_PredictWeather (150) | Chant_PredictWeather | Self · weather mastery | default | Outdoors: reports the next weather description when it changes. |
| Chant_MeteorStrike (149) | Chant_MeteorStrike | Malicious · rock control | default | Ranged 1-5: 30 + maxDie d6 damage to each target; halved if message value is positive. |
| Chant_PlantBed (148) | Chant_PlantBed | Indifferent · plant growth | default | Creates a non-gettable pea-pod bed (sleep furniture). |
| Chant_RechargeShards (146) | Chant_RechargeShards | Indifferent · deep magic | 100 | Adds 5 charges to a Wand; destroys a fully charged one. |
| Chant_MoveSky (144) | Chant_MoveSky | Indifferent · moon summoning | 99% | Advances the area's time of day to the next target phase. |
| Chant_Moonbeam (137) | Chant_Moonbeam | Self · moon summoning | default | Light source on the target that removes darkness; the "follow" text is not backed by code in this file. |
| Chant_Phosphorescence (137) | Chant_Phosphorescence | Self · shape shifting | default | Light source on the target that removes darkness. |
| Chant_PlaneWalking (137) | Chant_PlaneWalking | Indifferent · cosmology | 90% | Planar travel (extends StdPlanarAbility); needs a caster's plant; random area. |
| Chant_RefreshRunes (137) | Chant_RefreshRunes | Indifferent · preserving | 50 | Gives a chant scroll one more use. |
| Chant_PredictTides (136) | Chant_PredictTides | Indifferent · moon summoning | default | Reports the tide phase in watery rooms. |
| Chant_RestoreMana (131) | Chant_RestoreMana | Beneficial other · preserving | 50 | Adds 50 mana to the target. Secret at Druid level 3. |
| Chant_ReadRunes (123) | Chant_ReadRunes | Indifferent · nature lore | default | Reads an item by briefly giving the caster a copy of the chant. |
| Chant_Plague (122) | Chant_Plague | Malicious · enduring | default | Casts Disease_Plague on the target. |
| Chant_Poison (113) | Chant_Poison | Malicious · enduring | default | Casts Poison_Venom on the target. |
| Chant_ReleaseMagic (113) | Chant_ReleaseMagic | Indifferent · nature lore | default | Disenchants an item; it dulls or vanishes. |
| Chant_ReefWalking (107) | Chant_ReefWalking | Indifferent · plant growth | default | Plant travel, but only to watery rooms (extends Chant_PlantPass). |


### Class availability (from `pkg/CharClasses/*.java`, level and whether auto-granted)

| Class | Chants granted (level; `true` = free) |
|---|---|
| Druid (`Druid.java`) | RestoreMana 3 (secret); NaturalBalance 7; NaturalOrder 8; PlantPass 11; Poison 11; PlantBed 13; Plague 17; PlantMaze 18; Reabsorb 18; Nectar 19; PlantSnare 21; NeutralizePoison 22; PlanarAdaptation 28; Reincarnation 30 (free); PlaneWalking 35 |
| Gaian | PlantSelf 7; PlantPass 9; PlantBed 11; PlantWall 12 (free); PlantMaze 14 (free); PoisonousVine 15; PlantSnare 17; PlantConstriction 17; PlantChoke 19; PlantTrap 23; PlanarAdaptation 28; PlanarLink 35 |
| Mer | Phosphorescence 1 (free); PredictTides 1; NaturalBalance 7; NaturalOrder 8; ReefWalking 11 (free); MuddyGrounds 14; NeutralizePoison 22; PlanarAdaptation 28 |
| Gypsy | RefreshRunes 6 (free); ReleaseMagic 8 (free); RechargeShards 9; ReverseFortune 21 |
| SkyWatcher | PredictWeather 1; PredictPhase 1; Moonbeam 2; PaleMoon 3; MuddyGrounds 7; PiercingMoon 9; MoonCalf 12; PeaceMoon 15; PrayerWard 19; MeteorStrike 25 (free); PlanarAdaptation 28; MoveSky 30 (free); PlanarLink 35. RedMoon and PurpleMoon at 13, each gated on the EVIL alignment being loaded (`SkyWatcher.java:205-212`). |
| Ranger | RepelVermin 4; PredictWeather 5; Moonbeam 8; NaturalBalance 15; NaturalOrder 15; ResuscitateCompanion 22; NeutralizePoison 25 |
| Beastmaster | NaturalBalance 7; NaturalOrder 8; Plague 17; NeutralizePoison 22; PlanarAdaptation 28 |
| Delver | Mold 6; Rockfeet 9; PlantPass 11 (free); MassFungalGrowth 23; MetalMold 25 (free) |
| Archon | MoveSky 30 (free); NectarWeave 30 (free) |

## Notable design ideas

- **Delayed commit for a race change.** Reincarnation applies the new race as a temporary affect for 1800 ticks. Only when it expires is the race written into the base stats (`Chant_Reincarnation.java:145-148`). The geas also survives death, because it sets `canBeUninvoked=false` (`:203`).
- **Veto as a rule.** PeaceMoon does not change the actor's intent directly. It vetoes every malicious message in the room and makes both sides peaceful (`Chant_PeaceMoon.java:103-123`). This shows how the two-phase message flow can express a rule with no special case in the actor's code.
- **Sign inversion.** PurpleMoon and RedMoon are the same formula with the alignment test swapped. ReverseFortune flips the signs of award strings (`AutoAwards.applyAward(... reverse)`). Both use a data-level negation.
- **Struggle budget.** The snare's budget is `400 + 100*XLEVEL`, scaled by the target's speed. Each struggle costs `STR*4` (`Chant_PlantSnare.java:216-217, 123`). The bind is a quantity to be used up, not a timer.
- **Rooms that build rooms.** PlantMaze creates a new room grid from a class name, sets its size from the caster, copies exits, and generates flavour text from the plant's name. Its cleanup returns every occupant on quit or room reset.
- **Idle communion.** PlantSelf, NaturalBalance and NaturalOrder earn slow passive changes in faction score just by sitting in the right place. Their movement or combat rules make the cost of the effect a matter of staying put.
- **Companion registry.** ResuscitateCompanion keeps weak references to up to 10 dead companions and revives only those (`Chant_ResuscitateCompanion.java:166`).

## Problems and cruft

- **Both messages sent on a veto.** `Chant_Plague.java:104`: `if(okMessage(msg)||okMessage(msg2))` sends both messages (`:106-107`) even if one of them was vetoed. This looks like a `||` that should be `&&`, but the intent is not documented. Treat it as a likely bug.
- **Roll range mismatch in meditation flavour.** `Chant_PlantSelf.java:165`, `Chant_NaturalBalance.java:166` and `Chant_NaturalOrder.java:166` switch on `CMLib.dice().roll(1,10,0)`. That returns 1 to 10 (`pkg/Libraries/Dice.java:398-405`), but the cases are 0 to 9. Case 0 can never run, and a roll of 10 matches no case.
- **Unreachable branch.** `Chant_ResuscitateCompanion.java:196-200` returns early for player corpses. The later `if(playerCorpse)` branch that calls `CMLib.utensils().resurrect` (`:224-225`) can therefore never run.
- **Misleading comment.** `Chant_Reincarnation.java:140` says "undo the affects of this spell", but lines 145-148 commit the new race to the base stats. The comment is wrong. The death-limit veto (`:216-218`) also has no visible follow-up in this file.
- **Name versus behaviour.** `Chant_RechargeShards` is named for shards, but `invoke` accepts only a `Wand` (`:85`). The name is a leftover from an older design, or the file is incomplete.
- **Typo in a player message.** `Chant_PlantSelf.java:237`: "This magic wonly works in fertile soil."
- **Sub-spell attribution.** `Chant_PlantTrap.java:227` invokes the sub-chant with the victim as the caster (`A.invoke(target,target,true,0)`). The consequences for the victim's faction, experience and aggression are not traced.
- **Odd effective chance.** `Chant_PrayerWard.java:120` tests `rollPercentage()<35`. Because rolls run 1 to 100 (`Dice.java:409-412`), the effective chance is 34%.
- **Possibly mismatched gate.** `SkyWatcher.java:211-212` grants PurpleMoon when the EVIL alignment is loaded, but `Chant_PurpleMoon.java:145-146` requires the caster to be chaotic. These are different axes (alignment versus inclination). This may be intentional, but it is worth checking.
- **Moonbeam follow.** `Chant_Moonbeam.java` says the moonbeam "follow(s)" the target, but the only effect method in the file is `affectPhyStats` (light source and darkness). The following behaviour is not implemented here, as far as this file shows.
- **Synchronous time loop.** `Chant_MoveSky.java:131-135` runs up to `getHoursInDay()` calls to `tickTock`. The loop has no early exit for the target phase other than the comparison in each step.
- **Multiplayer assumptions.** PlantTrap (group and `mayIFight`), Reincarnation (PK flags, groups, the `PUR` death counter), Reabsorb (player corpses), PeaceMoon (every combatant in the room), and the plant ownership checks all assume several human players.

## Takeaways for a Python SUD

- **Define chants as data.** A table of records is enough: `id`, `domain`, `quality` (benefit, malice, neutral), `mana` (flat or percent), `target` (self, creature, item, room), `duration` (ticks, default by level, or forever) and an effect class. Keep the catalogue in one file.
- **Borrow the duration rule.** Use `casterLevel*4 + 40` ticks, clamped between 5 and one in-game day. For malicious effects use `round(level*1.1) + 25`. Use absolute values where the design calls for them, and keep `forever` as a sentinel.
- **Collapse the moon family.** One `SkyEffect` class with fields for alignment filter, sign and stat modifier covers PurpleMoon, RedMoon, PaleMoon, MoonCalf and PeaceMoon. Enforce one sky effect per room.
- **Keep the veto pattern.** In Python, an event is a small object with a `vetoed` flag, passed through room listeners. PeaceMoon becomes "for each event in the room, if malicious and not forced, set vetoed and make both parties peaceful".
- **Keep the struggle budget.** A `Bound` status with `budget`, decremented by `strength*4` on each attempt, is short and readable.
- **Keep the delayed commit.** For a shape or race change, store the new form as a status for N ticks and write it to the base record when the status expires.
- **Drop the multiplayer checks.** Remove PK flags, group checks, corpse ownership, follower-trap exemptions and the `PUR` death limit. Keep the companion revival idea, because it suits single-player pets.
- **Drop the engine plumbing.** GridLocale room generation, database updates and sound effects are not needed. A Python version can create a new room dictionary instead.

A sketch of the moon idea, not tested:

```python
def default_ticks(caster_level: int, ticks_per_day: int) -> int:
    return min(max(caster_level * 4 + 40, 5), ticks_per_day)

@dataclass
class SkyEffect:
    alignment: str   # "lawful" or "chaotic" (or "good", "evil")
    sign: int        # +1 raises damage taken by about half, -1 lowers it
    ticks_left: int

    def damage_taken(self, target_alignment: str, amount: int) -> int:
        if target_alignment != self.alignment:
            return amount
        return amount + self.sign * round(amount / 2)
```

## Coverage

- **Read in full:** `pkg/Abilities/Druid/Chant.java` (201 lines), `pkg/Abilities/Druid/Chant_PlantTrap.java` (331), `pkg/Abilities/Druid/Chant_Reincarnation.java` (293), `pkg/Abilities/Druid/Chant_PurpleMoon.java` (219).
- **Read in substance (invoke, okMessage, tick and affect methods, with boilerplate removed):** PlantChoke, PlantConstriction, PlantSnare, PlantSelf, PlantMaze, PlantBed, PlantWall (most of it), Rockfeet, MeteorStrike, MassFungalGrowth, Plague, Poison, Mold, MetalMold, NeutralizePoison, PrayerWard, NaturalBalance (invoke and tick), NaturalOrder (tick), PaleMoon, PeaceMoon, MoonCalf, RedMoon, PiercingMoon, RestoreMana, Reabsorb, RefreshRunes, ReadRunes, ReleaseMagic, RechargeShards, PlanarAdaptation, PlanarLink (invoke and okMessage), PlaneWalking (invoke), MoveSky, MuddyGrounds (effect and invoke), Nectar (invoke), ReverseFortune (unInvoke and cast completion), Phosphorescence, Moonbeam, PredictWeather, PredictPhase, PredictTides (in part).
- **Skimmed (identity, quality, domain, mana and which methods exist, plus partial reading):** ResuscitateCompanion (most of it), NectarWeave, RepelVermin, PlantPass, ReefWalking, PoisonousVine (`determineMonster` only).
- **Outside the bucket, read for the mechanics they support:** `StdAbility.java` (duration, cost and clone paths), `Druid_MyPlants.java` (`myPlant`), `Chant_SummonPlants.java` (`buildMyThing`), `Chant_SummonFungus.java` (`buildMyPlant`), `Chant_ControlWeather.java` (the weather-flag check), `Dice.java`, `CMProps.java:681`, `DefaultTimeClock.java:313`, `AbilityMapper.java:59`, `AutoAwards.java` (`applyAward`), and the class mapping lines in the CharClasses files.
- **Not read:** the bodies of the Chant_SummonVine, StdPlanarAbility and Chant_DruidicConnection code paths, which the chants call but which belong to other buckets.
