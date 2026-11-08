# Armor and wearable items

> Part of the CoffeeMud NEWOVERVIEW. Bucket: `b123` (Items/Armor) · 52 files · 4591 lines of Java.

Citations are relative to the CoffeeMUD folder. The package prefix `com/planet_ink/coffee_mud/` is sometimes dropped (so `Libraries/MUDFight.java` means `com/planet_ink/coffee_mud/Libraries/MUDFight.java`), and a bare `File.java:line` is in `Items/Armor/`, e.g. `StdArmor.java:639`.

## Summary

The armor family turns a wearable item into a data record plus a set of message hooks. `StdArmor` adds worn slots, clothing layers, a height-based fit check, protection with enchantment, durability that drops when the wearer takes damage, and condition text on top of `StdContainer`. Of the 52 files, 34 contain only a constructor that sets name, slots, armor, weight, value and material; the `Gen*` classes are data-driven templates whose fields are saved as XML and edited in game, and they are what crafting recipes and character creation actually produce. A few classes carry distinctive mechanics: `GenEarring` (wearable only where a piercing exists), `GenGangline` (ties two wearers together), `GlowingMageArmor` (spell-made, self-destroying), `GenCloak` (a worn disguise name) and a few enchanted or unique pieces with small stat effects. The most reusable design is wear and tear: there is no tick, and the item degrades inside its own `executeMsg` when damage reaches its wearer, using a material-by-damage-type table.

## Key files and types

| File | Role | Lines |
|---|---|---|
| `com/planet_ink/coffee_mud/Items/Armor/StdArmor.java` | Wearable armor base: slots, layers, fit, protection, enchantment, wear and tear, condition text, identity | 746 |
| `com/planet_ink/coffee_mud/Items/Armor/GenArmor.java` | Data-driven armor template; stat codes and XML save format | 219 |
| `com/planet_ink/coffee_mud/Items/Armor/StdThinArmor.java` | Thin accessory base (eyes by default): no fit check, no wear and tear | 127 |
| `com/planet_ink/coffee_mud/Items/Armor/GenThinArmor.java` | Template for thin accessories; exposes only layer codes | 172 |
| `com/planet_ink/coffee_mud/Items/Armor/GenEarring.java` | Earrings and body jewelry, gated by piercings on the wearer | 254 |
| `com/planet_ink/coffee_mud/Items/Armor/GenGangline.java` | Leather straps that tether two wearers; reroutes mount, dismount and put | 312 |
| `com/planet_ink/coffee_mud/Items/Armor/StdShield.java` | Held-slot armor base; `Shield` is a marker interface | 56 |
| `com/planet_ink/coffee_mud/Items/Armor/GenShield.java` | Template shield (held) | 63 |
| `com/planet_ink/coffee_mud/Items/Armor/GenBelt.java` | Waist belt that is also a container (capacity 20, weapons only) | 59 |
| `com/planet_ink/coffee_mud/Items/Armor/GenCloak.java` | Hooded cloak; when worn it shows its readable text as its name | 67 |
| `com/planet_ink/coffee_mud/Items/Armor/GlowingMageArmor.java` | Breastplate created by the mage-armor spell; not savable, self-destroying | 88 |
| `com/planet_ink/coffee_mud/Items/Armor/MichaelsMithrilChain.java` | Named unique mithril chain with enchantment and lightning immunity | 78 |
| `com/planet_ink/coffee_mud/Items/Armor/GlovesSpeed.java` | Gloves that add speed and attack bonuses while worn | 71 |
| `com/planet_ink/coffee_mud/Items/Armor/EternityLeafArmor.java` | Leaf suit that grants the sneaking flag while worn | 67 |
| `com/planet_ink/coffee_mud/Items/Armor/Hat_Intelligence.java` | Feathered cap with +4 intelligence while worn | 67 |
| `com/planet_ink/coffee_mud/Items/Armor/EternityBarkArmor.java`, `EternityLeafShield.java`, `DrowChainMailArmor.java` | Data-only unique or variant pieces (see catalogue) | 59 |
| 31 constructor-only classes (`ChainMailArmor.java` ... `WalkingBoots.java`) | Catalogue entries: name, slots, armor, weight, value, material | 56 to 58 |
| `com/planet_ink/coffee_mud/Items/interfaces/Armor.java` (outside bucket) | `LAYERMASK_SEETHROUGH` (61), `LAYERMASK_MULTIWEAR` (68), layer meaning (70-77), `SizeDeviation` (128-134) | 135 |

## How it works

### Data model (StdArmor)

- **Protection.** `basePhyStats().setArmor(...)`, default 10 (`com/planet_ink/coffee_mud/Items/Armor/StdArmor.java:58`). While worn, the item's armor is subtracted from the wearer's armor stat (`StdArmor.java:639`). The stat is therefore lower-is-better on mobs: the editor prompts "Armor (lower-better)" for mobs and "Armor (higher-better)" for items (`com/planet_ink/coffee_mud/Libraries/CMGenEditor.java:3569-3571`).
- **Enchantment.** `phyStats().ability()`, default 0 (`StdArmor.java:59`). A positive value is a bonus, and it is applied per slot (see Protection).
- **Slots.** `properWornBitmap` (`StdArmor.java:56`) and `wornLogicalAnd` (`StdArmor.java:57`). With AND, every listed slot must be free (a suit). With OR, one slot is enough (`StdArmor.java:99-109`).
- **Clothing layer.** Fields `layer` and `layerAttributes` (`StdArmor.java:46-47`), accessors at `StdArmor.java:75-97`. Layer 0 is the base, positive layers are outer garments such as coats and shawls, and negative layers are underwear and hose (`com/planet_ink/coffee_mud/Items/interfaces/Armor.java:70-77`). Attributes are `LAYERMASK_SEETHROUGH` = 1 and `LAYERMASK_MULTIWEAR` = 2 (`Armor.java:61`, `:68`).
- **Durability.** `usesRemaining`, from 0 to 100. The default is 100 (`StdArmor.java:63`). `setUsesRemaining` turns `Integer.MAX_VALUE` into 100 (`StdArmor.java:68-73`), so this path cannot express indestructible armor.
- **Value.** `baseGoldValue`, default 150 (`StdArmor.java:60`). `value()` scales the price by `usesRemaining/100` while uses are below 1000 (`StdArmor.java:695-701`). A half-worn item is worth half.
- **Weight.** Inherited from `StdItem` (`com/planet_ink/coffee_mud/Items/Basic/StdItem.java:89`), which is 1.
- **Material.** Drives condition wording, wear and tear, and saving-throw bonuses. The field is `material()`, set per class.
- **Dead field.** `int sheath` is declared (`StdArmor.java:45`, `com/planet_ink/coffee_mud/Items/Armor/StdThinArmor.java:42`) and never read.

### Wearing and layers

- `canWear` (`StdArmor.java:99-109`). `where==0` asks whether any slot is free. An AND item needs `whereCantWear(mob)==0`. An OR item needs `mob.freeWearPositions(where, layer, attrs) > 0`.
- `StdMOB.freeWearPositions` (`com/planet_ink/coffee_mud/MOBS/StdMOB.java:5881-5894`) subtracts items already occupying the spot from the spot's capacity. It returns 0 when the global cap `MAXITEMSWORN` is reached (`StdMOB.java:5886`).
- Per-location capacity comes from `MAXWEARPERLOC` (`StdMOB.java:5901`) and the body-part grid `Race.BODY_WEARGRID`. A location whose body part is missing has no positions.
- `StdMOB.fetchWornItems` (`StdMOB.java:5982-6011`) decides which items collide with a new item by layer. Items on the same spot within one layer step of the new item, or above it, count as occupying. Two equal-layer items may coexist only if both carry `MULTIWEAR` (`StdMOB.java:5985`, `:5999-6003`). This is a reading of the code, not a test.
- Other systems read the layer attributes: `com/planet_ink/coffee_mud/Commands/Equipment.java:214` and `com/planet_ink/coffee_mud/Abilities/Common/Costuming.java:307` both check `LAYERMASK_SEETHROUGH`.

### Fit (sizing)

- `getSizingDeviation` (`StdArmor.java:169-212`) runs only when both item and wearer have a height (`:175-176`) and the `EQUIPSIZE` security flag is not disabled (`:203`).
- Tolerance, in height units, depends on the slot:
  - The small set (torso, legs, waist, arms, hands, feet) allows 20 (`StdArmor.java:161-167`, `:179-180`).
  - Floating and mouth allow -1, meaning no check (`:189-192`).
  - Eyes and ears allow 1000 (`:194-198`).
  - Neck allows 5000 (`:200-201`).
  - Anything else, including head, allows 200 (`:178`).
- A wearer shorter than the item minus tolerance gets `TOO_LARGE`. A wearer taller than the item plus tolerance gets `TOO_SMALL` (`:205-208`). The veto happens in `okMessage` on `TYP_WEAR` (`:219-237`).
- The item's base height is copied from its owner the first time it is recovered while worn (`StdArmor.java:624-627`). The first wearer sets the fit for later wearers, and this is sticky.
- `StdThinArmor` always returns `FITS` (`com/planet_ink/coffee_mud/Items/Armor/StdThinArmor.java:104-108`).

### Protection and enchantment

- `affectPhyStats` (`StdArmor.java:630-653`) applies only when the item is worn. It does not apply in inventory, not to a floating item unless it fits there, and not to a held item unless it is a `Shield` (`:635-637`).
- Base: `affectableStats.setArmor(armor - phyStats().armor())` (`:639`).
- Enchantment applies only when `ability > 0` and the item is not `MULTIWEAR` (`:640`). Its size depends on slot:
  - For AND items, the ability is first multiplied by the number of slot bits (`:642`).
  - Torso: `ability*5` (`:643-644`).
  - Head or held: `ability*2` (`:646-647`).
  - Any other non-floating slot: `ability*1` (`:649-650`).
- By the code's arithmetic (not run), `MichaelsMithrilChain` (base armor 50, ability 75, torso, not AND) removes 50 + 75*5 = 425 from the wearer's armor stat (`MichaelsMithrilChain.java:52-54`, `StdArmor.java:639-644`).
- Combat reads the wearer's armor through `MUDFight.adjustedArmor` (`com/planet_ink/coffee_mud/Libraries/MUDFight.java:348-373`). The default formula string is `(@x1-( (((@x2-9)/5)*((@x3-9)/5)*((@x3-9)/5*@x8)) +(@x4*@x8)-(0.15*@xx>0*@x5)-(0.15*@xx>0*@x6)-(0.3*@xx>0*@x7)*@x9))-100` (`com/planet_ink/coffee_mud/core/CMProps.java:3203`). I did not evaluate it further; it belongs to the combat bucket.
- Saving-throw bonuses are set per material in `affectCharStats` (`StdArmor.java:655-693`):
  - Metal: electric save -10 (`:662-665`).
  - Leather: acid save +10 (`:666-669`).
  - Mithril: magic save +10 (`:670-673`).
  - Cloth and paper: fire save -10 (`:674-678`).
  - Glass, rock, precious, vegetation, flesh, synthetic: fire save +10 (`:679-687`).
  - Energy: paralysis save +10 (`:688-691`).
  - The sign convention (higher save means more resistance) is inferred. `com/planet_ink/coffee_mud/Behaviors/WeatherAffects.java:999` tests `STAT_SAVE_FIRE < 10` as a threshold, which fits that reading.

### Condition text and wear and tear (control flow)

**Trigger points, all in `StdArmor.executeMsg` (`StdArmor.java:241-610`):**

1. Look or examine (`:246-251`). If the item is wear-and-tear capable, uses are below 100, and the item is visible, the owner is told its `armorHealth()` text.
2. Damage to the wearer (`:252-265`). The item must not be in inventory and the owner must be a MOB (`:253-254`). The message must target the owner (`:255`), be `TYP_DAMAGE` with value above 0 (`:256-257`), and not be poison or disease (`:259-263`).
3. Probability gate (`:264-265`). The degradation roll happens only if `roll% > (level/2 + 10*ability + (20 if IS_BONUS))` and also `roll% > owner DEX`. The first term uses `phyStats().level()` and the second term uses the owner's DEX.

**Damage type (`:267-277`).** If the tool is a `Weapon`, its `weaponDamageType()` is used. Otherwise the type comes from `Weapon.MSG_TYPE_MAP` keyed by `msg.sourceMinor()` (`com/planet_ink/coffee_mud/Items/interfaces/Weapon.java:135`). The types run from `TYPE_NATURAL=0` to `TYPE_SCRAPING=16` (`Weapon.java:49-81`).

**Degradation table.** Keyed by material family, then damage type. Each row is "chance of loss, amount". Lines refer to `StdArmor.java`.

| Material family | Damage type: chance of loss and amount |
|---|---|
| Cloth, paper (`:284-321`) | striking 25% of 1d5 (`:294-296`); lasering 75% of 1d75 (`:298-301`); melting, burning, disrupting 25% of 1d15 (`:305-307`); natural, sonic, scraping 1% of 1 (`:311-312`); piercing, shoot, slashing 5% of 2 (`:317-318`) |
| Glass (`:322-356`) | sonic, disrupting 75% of 1d75 (`:335-336`); striking, frost, stultify 5% of 1d20 (`:341-342`); bashing, natural 10% of 1d10 (`:346-347`); piercing, shoot, slashing 5% of 1d15 (`:352-353`) |
| Leather, flesh (`:357-397`) | striking, corrupting 25% of 1d5 (`:365-367`); lasering, disrupting 50% of 1d50 (`:371-372`); melting, burning 25% of 1d15 (`:376-377`); piercing, shoot 10% of 1d4 (`:389-390`); slashing 5% of 1 (`:393-394`) |
| Mithril (`:398-401`) | 1% gate, then falls through to the metal rules (`:399-401`) |
| Metal (`:402-452`) | lasering, disrupting 35% of 1d35 (`:414-415`); melting 25% of 1d15 (`:418-419`); burning 1% of 1 (`:422-423`); bashing 5% of 2 (`:436-437`), plus a tinnitus disease chance on head armor (`:426-435`); striking, natural, sonic 5% of 2 (`:442-443`); piercing, shoot, slashing 2% of 1 (`:448-449`) |
| Synthetic (`:453-491`) | lasering, disrupting 20% of 1d25 (`:462-464`); bursting, melting, scraping, stultify 5% of 1d10 (`:469-471`); burning, sonic 1% of 1 (`:474-476`); bashing, striking, natural 5% of 1d4 (`:480-482`); piercing, shoot 5% of 1d8 (`:485-487`) |
| Rock, precious (`:492-533`) | lasering, scraping 50% of 1d50, not for precious (`:503-505`); sonic, disrupting 35% of 1d35 (`:509-511`); melting 5% of 1d5 (`:513-515`); burning 1% of 1 (`:517-519`); bashing, striking, natural 5% of 2 (`:523-525`); piercing, shoot, slashing 2% of 1 (`:529-531`) |
| Wood (`:534-571`) | striking 20% of 1 (`:543-544`); melting, burning, scraping 20% of 1d5 (`:546-550`); lasering, disrupting, corrupting 50% of 1d50 (`:555-556`); piercing, shoot, slashing 2% of 1 (`:567-568`) |
| Energy, gas (`:572-574`) | no loss |
| Anything else (`:575-578`) | 1% of 1 |

The table is written inline. The author's own comment says it should be a chart in `lists.ini` (`StdArmor.java:281`).

**Consequences (`StdArmor.java:582-608`).**
- After any change, physical stats are recomputed (`:582-583`).
- At under 10 uses, and above 0, the owner is told "@x1 is nearly destroyed! (@x2%)" (`:585-590`).
- At 0 or below, uses reset to 100 (`:597`). A trailer message is queued, "^I@x1 is destroyed!!^?", plus a line to the room (`:598`). Containers are emptied (`:599-600`), the item is unworn and destroyed (`:601-602`), and the owner's stats and room are recomputed (`:603-607`).

**Condition text (`StdArmor.java:111-152`).** The durability bands are `{95,75,50,25,10,5,0}` (`:113`). The band index picks a line from a list chosen by material: cloth and paper use `ARMOR_CONDITION_CLOTH`, rock, precious, synthetic and glass use `..._GLASS`, flesh and leather use `..._LEATHER`, metal and mithril use `..._METAL`, wood uses `..._WOODEN`, and everything else uses `..._OTHER` (`:124-148`). The lists are in `resources/lists.ini:714-790`. Each list is a comma-separated set of lines with a `@x1` name slot and a `@x2` percent slot. Band 0 is blank in five of the six lists, so an item at 95 to 99 uses shows an empty look line in those materials (unverified whether `tell("")` prints a blank line). `CMProps.getListFileChoiceFromIndexedList` picks randomly when a band has alternatives (`com/planet_ink/coffee_mud/core/CMProps.java:2571-2577`).

### Identity (secret names)

- `secretIdentity()` (`StdArmor.java:711-745`) shows the unidentified item as its name with an ability suffix, such as "name +N" or "name -N" (`:715-719`).
- It adds a power level from `CMLib.itemBuilder().timsLevelCalculator(this)`, shown as "(Power level: N)" when that differs from the item level (`:720-722`). The calculator lives in `com/planet_ink/coffee_mud/Libraries/TimsLibrary.java:51-54` and `:152`, and I only read part of it.
- It ends with the line "Base Protection: N" (`:743`). This line appears to double count the item's armor. See Problems.
- Unique items set a secret description through the same field: `Hat_Intelligence.java:48` ("Hat of Intelligence (Increases IQ)"), `GlovesSpeed.java:48`, `DrowChainMailArmor.java:48`, `EternityLeafShield.java:48`, `MichaelsMithrilChain.java:49`.

### Generic templates (Gen*) and where they come from

- `GenArmor` (`GenArmor.java:35-59`) defaults to no worn slot (`IN_INVENTORY`, `:53`), weight 25 (`:49`), value 5 (`:52`), armor 10 (`:56`), level 1 (`:55`), and leather (`:58`). Anything built from it is not wearable until a builder sets a slot.
- Its fields are exposed as stat codes. `getStatCodes()` (`:187-203`) appends the shared `GenericBuilder.GenItemCode` list to its own `MYCODES`: `HASLOCK`, `HASLID`, `CAPACITY`, `CONTAINTYPES`, `RESETTIME`, `LAYER`, `LAYERATTRIB`, `DEFCLOSED`, `DEFLOCKED` (`:105`). `getStat` and `setStat` dispatch on those codes (`:107-175`).
- Save format. `text()` returns `CMLib.coffeeMaker().getEnvironmentalMiscTextXML(this,false)` (`:68-71`), and `setMiscText` unpacks it and recovers stats (`:97-103`). A generic item is therefore a record with an XML body.
- `GenThinArmor` exposes only `LAYER` and `LAYERATTRIB` (`GenThinArmor.java:93`, `:118-123`).
- Crafting. Counts are the number of recipe lines in `resources/skills/*.txt` whose output class is the template (sixth tab-separated field, counted on `%` lines; not decoded further):
  - `GenArmor`: 565 lines. armorsmith 172, tailor 105, masterarmorsmith 95, jewelmaking 62, masterleatherworking 46, leatherworking 46, cobbler 16, weaving 8, mastertailor 8, scrimshaw 4, torturesmith 2, blacksmith 1.
  - `GenShield`: 23 lines. carpentry 8, armorsmith 8, masterarmorsmith 7.
  - `GenEarring`: 16 lines, all in jewelmaking.
  - `GenShoes`: 1 line, in carpentry.
- Character creation. `PlayerRace.outfit` (`com/planet_ink/coffee_mud/Races/PlayerRace.java:177-195`) builds the default outfit from `GenShirt`, `GenShoes`, `GenPants` and `GenBelt` (`:182-191`). `StdRace.cleanOutfit` then sets each item's base value to 0 (`com/planet_ink/coffee_mud/Races/StdRace.java:818-826`). `GenBelt` is also made by `Races/Birdman.java:271`, `Races/Gnome.java:226`, `Races/HalfOgre.java:182` and `Races/Dwarf.java:218`.
- Import. `com/planet_ink/coffee_mud/Commands/Import.java:4069-4072` chooses `GenShield` if the object's name contains "SHIELD", otherwise `GenArmor`.
- Prayers. `com/planet_ink/coffee_mud/Abilities/Prayers/Prayer_UnholyArmament.java:152` creates a `GenShield`, renames it, and sets its material to mithril.

### Thin accessories and earrings

- `StdThinArmor` (`StdThinArmor.java:46-60`) defaults to `WORN_EYES` (`:53`), armor 1 (`:55`), value 150 (`:57`), and uses 100 (`:58`). It overrides fit to `FITS` (`:104-108`) and wear and tear to false (`:110-114`). Its identity text prints the base armor directly (`:125`), which is correct where `StdArmor` is not.
- `GenThinArmor` (`GenThinArmor.java:44-59`) is its generic template.
- `GenEarring` (`GenEarring.java:41-69`):
  - Defaults: ears (`:59`), AND (`:60`), armor 0 (`:61`), weight 1 (`:62`), value 40 (`:64`), gold (`:68`).
  - Layer -10 (`:65`), with `MULTIWEAR` (`:66`), so several earrings can share one layer and location.
- Piercing gate.
  - `hasFreePiercing` (`GenEarring.java:82-99`) counts tattoos named `<LOCATION>:` whose text contains "PIERCE" (`:92-93`). A piercing is free if the count exceeds the earrings already worn at that spot (`:98`).
  - `canWear` requires `hasFreePiercingFor` (`:130-138`, `:101-128`).
  - The refusal is "You need pierced @x1 to wear that." (`:180`).
- Piercing creation (`com/planet_ink/coffee_mud/Abilities/Common/BodyPiercing.java`). The skill writes a tattoo "<LOCATION>:Pierced <part>" or "<LOCATION>:A pierced <part>" (`:332-337`). It refuses when the location already has as many tattoos as it has wear positions, with "That location is already decorated." (`:304-307`).
- Display. `recoverPhyStats` appends " on <piercing text>" to the earring's name, with "both " in front when the text has no article (`GenEarring.java:236-249`). It also deletes entries from other earrings' cached `wearLocs` (`:227-231`), which is cross-object state.
- Recipes. Sixteen recipes in `resources/skills/jewelmaking.txt`, from level 2 (barbell) to level 73. Two are torso-piercing recipes (levels 10 and 73). Most of the rest are ear, waist, or multi-location jewelry. The nose chain's wear spec lists ears and head together.

### Gangline (GenGangline)

- Item (`GenGangline.java:36-59`): worn about the body (`:51`), not AND (`:52`), armor 0 (`:53`), weight 5 (`:55`), leather (`:58`). The description is a set of straps for binding a team together (`:49`).
- Put on a follower. A `TYP_PUT` of the gangline into a MOB (`:85-103`) runs the Dress command on that MOB and vetoes the put. The Dress command refuses unless the target follows the actor or is held or an animal (`com/planet_ink/coffee_mud/Commands/Dress.java`).
- Connect. A `TYP_MOUNT` between two wearers (`:116-189`) needs both to wear a gangline (`:121-128`), and neither may already ride (`:131-156`). The message becomes "<O-NAME> connect(s) <S-NAME> to <T-NAME>." (`:158-160`). A trailer makes one wearer ride the other and recomputes the owner's stats (`:161-175`).
- Command failures. The item reads failed DISMOUNT, MOUNT and PUT command text (`:191-241`) and handles the failed command itself.
- Keep together. `executeMsg` (`:245-311`) starts a 4000 ms check after the mount leaves (`:264`). When the wearer is elsewhere, 1500 ms later it brings the wearer into the mount's room (`:285-306`, `R.bringMobHere` at `:303`).
- Timing is wall-clock milliseconds through `CMLib.threads().scheduleRunnable`, not ticks.

### Other special items

- **GlowingMageArmor** (`GlowingMageArmor.java:33-88`). Armor 45, weight 0, value 40000, material "nothing". It is not savable (`:59`). Each `okMessage` destroys it if it is in inventory, has no owner, or is in a room (`:70-72`). It refuses get, push, pull and remove with "The mage armor cannot be removed from where it is." (`:83`). Created by `Abilities/Spells/Spell_MageArmor.java:86-88`, which adds an XLEVEL-based amount to its armor and sets `LAYERMASK_SEETHROUGH`. Destroyed on unInvoke (`Spell_MageArmor.java:124`), with "<S-YOUPOSS> magical armor fades away." (`:130`). `Abilities/Spells/Spell_ImprovedRepairingAura.java:156` excludes it from repair.
- **GenCloak** (`GenCloak.java:63-65`). When properly worn and its readable text is non-empty, it sets its name to that text. The editor asks for a "Cloak Name" in that field (`com/planet_ink/coffee_mud/Libraries/CMGenEditor.java:11384`). This is a disguise hook.
- **GenBelt** (`GenBelt.java:52-54`). Waist-worn, capacity 20, accepts daggers, one-handed weapons, swords and other weapons. It is clothing that holds weapons. The description mentions a built-in sheath (`:47`), but no sheath code exists; the sheath is just the container.
- **GenHat** (`GenHat.java:52`). Head-worn with capacity 20, also a container.
- **GlovesSpeed** (`GlovesSpeed.java:66-67`). Adds `CMProps.getSpeedAdjustment()` to speed and +10 to attack adjustment while worn. The default `BONUSACT` is 1.0 (`CMProps.java:2997`), and base speed is 1.0 (`com/planet_ink/coffee_mud/Common/DefaultPhyStats.java:84`). Actions per tick scale with speed (`com/planet_ink/coffee_mud/Libraries/MUDFight.java:329`), so the default doubles action gain. This matches the secret text "Double attack speed" (`GlovesSpeed.java:48`). The text also says "truly usable only by fighters", and no class check exists in this file or anywhere that references it.
- **EternityLeafArmor** (`EternityLeafArmor.java:63-66`). Sets the sneaking disposition while worn and not held.
- **Hat_Intelligence** (`Hat_Intelligence.java:64`). Intelligence +4 while worn, hard-coded.
- **MichaelsMithrilChain** (`MichaelsMithrilChain.java:67-73`). Refuses electric damage while worn, with "<S-NAME> appear(s) to be unaffected." (`:72`). The secret text claims "+75" (`:49`), which is the enchantment, not the armor value; the effective protection is larger (see Protection).
- **EternityBarkArmor, EternityLeafShield, DrowChainMailArmor.** Data only. They set `IS_BONUS` or a secret name, and no further code.

### Timing

- The bucket has no `tick()` methods (checked in all 52 files).
- Everything is message-driven. Veto checks run in `okMessage`: the size check on `TYP_WEAR`, and the removal refusals on the mage armor. Condition text, wear and tear, and the gangline mount logic run in `executeMsg`.
- Stats are recomputed through `recoverPhyStats`, `affectPhyStats` and `affectCharStats` whenever the wearer's stats are rebuilt.
- The only timers are in `GenGangline` (4000 ms and 1500 ms, `:264` and `:306`).
- Spell-created items have lifetimes set by their spell (`Spell_MageArmor.unInvoke`).

### Messages shown to players

| Situation | Message | Source |
|---|---|---|
| Too big or too small to wear | "@x1 doesn't fit you -- it's too big." / "... too small." | `StdArmor.java:227`, `:233` |
| Condition on look or examine | Band text from the material list, e.g. "is slightly worn" (cloth band 1), "is just a few threads" (cloth band 6), "is almost destroyed" (metal band 6) | `StdArmor.java:111-152`; `resources/lists.ini:714-776` |
| Nearly destroyed | "@x1 is nearly destroyed! (@x2%)" | `StdArmor.java:590` |
| Destroyed | "^I@x1 is destroyed!!^?" and "^I@x1 being worn by <S-NAME> is destroyed!^?" | `StdArmor.java:598` |
| Metal head armor and bashing damage | Invokes the `Disease_Tinnitus` ability on the wearer (no message text in this code) | `StdArmor.java:426-435` |
| Earring without a piercing | "You need pierced @x1 to wear that." | `GenEarring.java:180` |
| Piercing location full (piercing skill) | "That location is already decorated." | `Abilities/Common/BodyPiercing.java:306` |
| Gangline pair not both wearing it | "Both targets must be wearing @x1 to do that." | `GenGangline.java:128` |
| Gangline connect | "<O-NAME> connect(s) <S-NAME> to <T-NAME>." | `GenGangline.java:158` |
| Gangline disconnect | "<S-NAME> disconnects <T-NAME> from <O-NAME>." | `GenGangline.java:224` |
| Mithril chain vs lightning | "<S-NAME> appear(s) to be unaffected." | `MichaelsMithrilChain.java:72` |
| Mage armor cannot be removed | "The mage armor cannot be removed from where it is." | `GlowingMageArmor.java:83` |

### Interactions with other systems

- **Damage pipeline.** Armor degrades from `TYP_DAMAGE` messages that target the owner. Those come from combat code outside this bucket (not traced). The armor reads the weapon type from the tool, and falls back on the message code.
- **Equipment and dressing.** `com/planet_ink/coffee_mud/Commands/Dress.java` puts clothes on other mobs and needs the target to follow the actor. `com/planet_ink/coffee_mud/Commands/Equipment.java:214` reads the see-through attribute.
- **Race outfits and recipes.** Covered under Generic templates.
- **Power level.** `TimsLibrary.calculateBaseValue` (`com/planet_ink/coffee_mud/Libraries/TimsLibrary.java:763-787`) prices armor from material points squared plus material value, times weight/2. `getMaterialArmorPoints` (`:848`) uses level bands from `getArmorMaterialPointsArray` (`:734`). Callers are `Abilities/Fighter/Fighter_ArmoredVanity.java:110`, `Fighter_ConfidentVanity.java:109` and `Fighter_LuckyVanity.java:139`, each testing whether the calculated value times 3 is below the item's `baseGoldValue()`, plus `Tests/Timsvalue.java:165-167`. `TimsLibrary` implements `ItemBalanceLibrary` (`TimsLibrary.java:35`). I did not follow the calculation further.

## Content worth mining

The catalogue below covers all 52 classes. Each row is `com/planet_ink/coffee_mud/Items/Armor/<Class>.java`, class line in parentheses. "Worn" is the slot set. "AND" means every listed slot is needed. "Armor" is the item's own value (lower on the wearer means better, see Protection). "Value" is `baseGoldValue` after the constructor chain. Weight is the field value; the unit is not verified.

| Class (line) | Name | Worn | AND | Armor | Wt | Value | Material | Notes |
|---|---|---|---|---|---|---|---|---|
| BandedArmor (33) | a suit of banded armor | torso, arms, legs | yes | 44 | 55 | 400 | iron |  |
| ChainMailArmor (33) | a suit of chain mail armor | torso, arms, legs | yes | 37 | 60 | 150 | steel |  |
| ChainMailVest (33) | a chain mail vest | torso | no | 25 | 30 | 75 | steel | single torso piece |
| Dress (33) | a nice dress | torso, arms, legs | yes | 8 | 10 | 5 | cotton |  |
| DrowChainMailArmor (33) | a suit of dark chain mail armor | torso, arms, legs | yes | 65 | 60 | 1500 | steel | secret-identity text only |
| EternityBarkArmor (33) | a suit of Eternity Tree Bark Armor | torso, arms, legs | yes | 100 | 15 | 25150 | wood | IS_BONUS; no extra code |
| EternityLeafArmor (33) | a suit of Eternity Tree Leaf Armor | torso, arms, legs | yes | 50 | 15 | 25150 | seaweed | sneak flag while worn |
| EternityLeafShield (33) | a huge leaf | held | yes | 30 | 15 | 15150 | seaweed | IS_BONUS; no extra code |
| FieldPlate (33) | suit of Field Plate | torso, arms, legs | yes | 59 | 80 | 4000 | steel |  |
| FullPlate (33) | suit of Full Plate | torso, arms, legs, feet, head, hands | yes | 90 | 90 | 20000 | steel | six slots |
| GenArmor (35) | some generic armor | template (none) | no | 10 | 25 | 5 | leather | editable template |
| GenBelt (33) | a knitted weapon belt | waist | no | 0 | 1 | 1 | cotton | container, capacity 20, weapons only |
| GenCloak (33) | a hooded cloak | about body | no | 1 | 1 | 1 | cotton | worn: name from readable text |
| GenDress (33) | a nice dress | legs, waist, torso | yes | 2 | 3 | 1 | cotton |  |
| GenEarring (41) | a pretty earring | ears | yes | 0 | 1 | 40 | gold | layer -10; MULTIWEAR; piercing-gated |
| GenGangline (36) | a gangline | about body | no | 0 | 5 | 5 | leather | ties two wearers |
| GenHat (33) | a hat | head | no | 0 | 1 | 1 | cotton | container, capacity 20 |
| GenPants (33) | a pair of pants | legs | no | 1 | 1 | 1 | cotton |  |
| GenShield (33) | a shield | held | no | 10 | 15 | 5 | oak | editable template |
| GenShirt (33) | a nice tunic | torso | yes | 2 | 1 | 1 | cotton | same values as Shirt |
| GenShoes (33) | a pair of shoes | feet | no | 1 | 1 | 1 | leather | same as Shoes except material |
| GenThinArmor (35) | a generic wearable thing | template (none) | no | 10 | 25 | 5 | leather | editable thin template |
| GlovesSpeed (33) | a pair of gloves | hands | no | 15 | 1 | 10150 | unset | speed and attack bonus |
| GlowingMageArmor (33) | a mystical glowing breast plate | torso | no | 45 | 0 | 40000 | nothing | spell-made; not savable; self-destroys |
| GreatHelm (33) | a steel Great Helm. | head | no | 18 | 10 | 60 | steel |  |
| Hat (33) | a feathered cap | head | no | 2 | 1 | 5 | leather |  |
| Hat_Intelligence (33) | a feathered cap | head | no | 2 | 1 | 6000 | cotton | +4 intelligence |
| Helmet (33) | a helmet | head | no | 10 | 10 | 16 | iron |  |
| HideArmor (33) | suit of Hide Armor | torso, arms, legs | yes | 29 | 15 | 30 | leather |  |
| Hood (33) | a cloth hood | head | no | 2 | 1 | 5 | cotton |  |
| LeatherArmor (33) | a suit of leather armor | torso, arms, legs | yes | 12 | 15 | 10 | leather |  |
| LeatherBoots (33) | a pair of leather boots | feet | no | 1 | 1 | 5 | leather |  |
| LeatherBracers (33) | a pair of leather bracers | L wrist, R wrist, arms | yes | 1 | 5 | 5 | leather |  |
| LeatherCap (33) | a leather cap | head | no | 4 | 1 | 5 | leather |  |
| LeatherGauntlets (33) | Leather Gauntlets | hands, L wrist, R wrist | yes | 1 | 5 | 5 | leather |  |
| MetalBracers (33) | a metal bracer | L wrist, R wrist | no | 2 | 5 | 10 | steel |  |
| MichaelsMithrilChain (33) | a chain mail vest made of mithril | torso | no | 50 | 40 | 10150 | mithril | enchanted (ability 75); lightning immunity; named unique |
| PaddedArmor (33) | a suit of padded armor | torso, arms, legs | yes | 12 | 30 | 8 | leather |  |
| Pants (33) | a pair of pants | legs | no | 1 | 1 | 1 | cotton |  |
| RingMail (33) | suit of ring mail | torso, arms, legs | yes | 22 | 50 | 200 | steel |  |
| Robes (33) | a set of robes | torso, arms, legs | yes | 8 | 10 | 5 | cotton |  |
| ScaleMail (33) | a suit of Scalemail | torso, arms, legs | yes | 29 | 60 | 240 | steel |  |
| Shirt (33) | a nice tunic | torso | yes | 2 | 1 | 1 | cotton | extends GenArmor |
| Shoes (33) | a pair of shoes | feet | no | 1 | 1 | 1 | cotton | same as GenShoes except material |
| SplintMail (33) | suit of splint mail | torso, arms, legs | yes | 44 | 60 | 160 | steel |  |
| StdArmor (37) | a shirt of armor | torso (default) | no | 10 | 1 | 150 | unset | base class |
| StdShield (33) | a shield | held | no | 10 | 15 | 150 | steel | held base |
| StdThinArmor (35) | a piece of armor | eyes | no | 1 | 1 | 150 | unset | thin base |
| SteelGauntlets (33) | some steel gauntlets | hands, L wrist, R wrist | yes | 3 | 5 | 20 | steel |  |
| StuddedLeatherArmor (33) | suit of studded leather armor | torso, arms, legs | yes | 22 | 35 | 40 | leather |  |
| ThreadbareRobes (33) | a set of worn robes | torso, arms, legs | yes | 5 | 2 | 1 | cotton |  |
| WalkingBoots (33) | a pair of nice hide walking boots | feet | no | 1 | 5 | 5 | leather |  |

Groups:

- **Suits (AND, torso plus arms plus legs).** Banded, chain mail, field plate, hide, leather, padded, ring mail, scale, splint, studded leather, the two Eternity suits, dress, robes, threadbare robes, and the generic tunic and dress (legs, waist, torso). These are the "one item, several slots" pieces, and the AND flag is what makes them occupy every listed slot.
- **Single pieces (OR).** Helmets and caps (head), gloves (hands), metal bracers (wrists), boots and shoes (feet), pants (legs), and chain vest (torso). One free slot is enough.
- **Multi-slot small pieces (AND).** Gauntlets (hands with both wrists) and leather bracers (both wrists and arms).
- **Generic templates.** Every `Gen*` class, which the crafting system and races use. Each is editable and records its own fields in XML.
- **Thin.** Earrings and the eyes-slot base. These never wear out and are not fit-checked.

Other content with reusable text:

- Condition bands per material, in `resources/lists.ini:714-790`. Short, evocative, and easy to port. Examples: cloth "is shredded and ripped", leather "is badly torn up", glass "is practically shards", wood "has some splintering", metal "is a crumpled mess".
- Recipe counts above. The shield recipes give good names: targe, round, buckler, heater, kite, tower, parma, scutum, pavise, and others.
- Jewelry recipe names: barbell, loop, stud, post, gauge, earring, dangle earring, coin earrings, charm earring, nose chain, body hoop, body chain, bodyring, and charm loop, plus two torso-piercing recipes.

## Notable design ideas

- **Damage as wear.** Durability falls inside the item's own `executeMsg` when damage hits its wearer (`StdArmor.java:252-265`). No separate system tracks it. The combat code does not know armor exists.
- **Material by damage type.** A table keyed by material then damage type, with chance and amount per cell (`StdArmor.java:282-580`). Leather resists piercing less than metal resists slashing, and glass shatters to lasers and sonics. This is more interesting than a single armor-to-damage formula.
- **Enchantment weighted by slot.** Torso enchantment counts five times, head and held twice, others once, and AND suits multiply by slot count (`StdArmor.java:640-651`). A magic robe is worth more on the torso than on the feet.
- **Layers.** Each garment has an integer layer, underwear negative, outer garments positive, plus MULTIWEAR and SEETHROUGH bits (`Armor.java:61-77`). Two garments can share a spot in a sensible order without special cases.
- **Disguise through a readable name.** A worn cloak shows its readable text as its name (`GenCloak.java:63-65`). That is a clean hook.
- **Secret identity.** Unidentified magic items show a mundane name and reveal the enchantment later (`StdArmor.java:711-722`). That suits a single-player mystery.
- **Body state as a requirement.** Earrings check tattoos on the wearer (`GenEarring.java:82-99`). The piercing skill writes those tattoos (`BodyPiercing.java:332-337`). Equipment depends on the body's history.
- **Gear as a relationship.** The gangline makes two wearers into a linked pair with rules for mounting and separation (`GenGangline.java:116-311`).

## Problems and cruft

- **Base Protection likely double counts.** `StdArmor.java:743` prints `phyStats().armor() - stats.armor()`. `stats` is built by `affectPhyStats` on a fresh object, where the item's armor is subtracted from zero (`StdArmor.java:639`). So for a worn item with armor A and enchantment term E, the printed value is about 2A + E rather than A + E. `StdThinArmor.java:125` prints the armor directly, which suggests the intent. Read-only conclusion; not run.
- **Mithril falls through to metal.** `StdArmor.java:398-402` gives mithril a 1% gate and then falls into the metal rules. The fall-through is commented (`:401`).
- **Hidden special case.** Head armor of metal, under bashing damage with two 1% rolls, invokes `Disease_Tinnitus` (`StdArmor.java:426-435`). It is not in data and not documented in the item.
- **Empty first condition band.** Five of six lists have a blank band 0 (`resources/lists.ini:714-776`), so a 95 to 99 uses item shows an empty look line (unverified in play).
- **Durability cannot be infinite.** `setUsesRemaining(Integer.MAX_VALUE)` becomes 100 (`StdArmor.java:70-71`).
- **Redundant reset.** `StdArmor.java:597` sets uses to 100 immediately before destroying the item.
- **Gangline write during veto.** `GenGangline.java:176` sets the owner's charisma to 25 during `okMessage`. The trailer then calls `recoverCharStats` (`:173`), which rebuilds stats from base and effects, so the change looks transient. The intent is unclear; the commented-out line at `:177` shows the same change was once aimed at the other wearer. Unverified.
- **Gangline null risks and dead code.** `GenGangline.java:89-93` dereferences `R` without a null check. `:216-217` tests `folM==null` after a cast that cannot be null.
- **Gangline open TODO.** `GenGangline.java:110-113` is an empty branch with a TODO about room-leave restrictions, so leaving a room is never blocked.
- **Gangline timing in milliseconds.** `GenGangline.java:264` and `:306` use wall-clock time and not ticks. This is out of step with the rest of the engine.
- **Gangline message parsing.** `GenGangline.java:191-241` parses failed command text to intercept DISMOUNT, MOUNT and PUT. This depends on the wording of failure messages.
- **Destroy in okMessage.** `GlowingMageArmor.java:70-72` destroys the item during a veto check, a side effect where none is expected.
- **Unguarded location.** `MichaelsMithrilChain.java:72` calls `mob.location().show(...)` without a null check.
- **Cross-object mutation.** `GenEarring.java:227-231` removes entries from other earrings' caches during `recoverPhyStats`.
- **Unused field.** `int sheath` (`StdArmor.java:45`, `StdThinArmor.java:42`) is never read. `GenBelt` describes a sheath that does not exist as code (`GenBelt.java:47`).
- **Duplicate pairs.** Std and Gen versions with the same name and nearly the same values: Hat and GenHat, Dress and GenDress, Pants and GenPants, Shoes and GenShoes, Shirt and GenShirt. Hat and Hat_Intelligence share a name, so the magic hat looks like the mundane one until identified.
- **Doc versus code.** The fighter-only claim on the gloves (`GlovesSpeed.java:48`) and the "wearable by thieves" claim on the bark armor are not enforced in code.
- **Lore in descriptions.** Several Eternity pieces carry lore text in their descriptions (a "Fox god" reference). It is data only and has no code effect.

## Takeaways for a Python SUD

**Borrow.**
- The clothing model: named slots, an AND flag for suits, an integer layer, and MULTIWEAR. It is small and handles cloaks over armor, and under-layers, cleanly.
- The durability model: a 0 to 100 value that falls when damage reaches the wearer, with a material-by-damage-type table as data. In Python this is a dict keyed by material then damage type, with `(chance, dice)` values taken from `StdArmor.java`.
- Condition bands per material as text data, which cost nothing and add flavour.
- Enchantment weighted by slot. A per-slot multiplier table is simpler than the code's special cases.
- Secret names for unidentified magic gear.
- A disguise name for worn garments (`GenCloak`).

A sketch of the data model, with CoffeeMud's lower-is-better convention inverted to higher-is-better:

```python
from dataclasses import dataclass

@dataclass
class Armor:
    name: str
    slots: frozenset[str]        # {"torso", "arms", "legs"}
    all_slots: bool = False      # suit: must cover every slot
    layer: int = 0               # <0 under, 0 base, >0 outer
    multiwear: bool = False      # equal-layer items may share a spot
    protection: int = 0          # higher is better
    enchant: int = 0
    material: str = "cloth"
    durability: int = 100

SLOT_ENCHANT = {"torso": 5, "head": 2, "held": 2}   # others count 1
WEAR = {                                              # material -> damage -> (chance, dice)
    "metal":   {"slashing": (0.02, "1"), "bashing": (0.05, "2")},
    "leather": {"slashing": (0.05, "1"), "piercing": (0.10, "1d4")},
}
CONDITION_BANDS = [95, 75, 50, 25, 10, 5, 0]
```

**Simplify.**
- The Gen XML editing and miscText storage. Plain data files (YAML or TOML) loaded into dataclasses do the same job.
- The height-based fit check. A single size category per creature is enough unless the SUD has varied body sizes.
- Wear-and-tear probability gates on level and DEX. Keep the damage-type table and drop the extra gates unless they matter for play.
- Thin accessories. A separate "accessory" slot type is clearer than a separate class family.
- Spell-made armor with self-destruction. A `temporary: expires_at_turn` flag does the same job.

**Drop.**
- The Dress command and gangline. They assume other people who can be dressed or tethered, which a single-player SUD does not have. A companion-follow mechanic could carry the same tether idea if the game needs one.
- Piercing and jewelry gating unless body modification is a design goal.
- The sticky first-wearer height.
- Millisecond timers. Use turns or ticks.

**Multiplayer assumptions to rethink.**
- The Dress command requires that the target follow the actor (`Commands/Dress.java`, see Gangline). In a single-player SUD this becomes "companion follows player".
- Gangline needs two wearers. Replace it with a companion or mount rule if the game has one.
- The message veto flow (`okMessage` before `executeMsg`) is useful for "cannot wear" checks. In Python, a `can_wear()` method plus event callbacks does the same job without a bus.

## Coverage

- **Read in full (21):** `StdArmor`, `GenArmor`, `GenThinArmor`, `StdThinArmor`, `GenGangline`, `GenEarring`, `GlowingMageArmor`, `MichaelsMithrilChain`, `GlovesSpeed`, `EternityLeafArmor`, `GenCloak`, `Hat_Intelligence`, `GenShield`, `DrowChainMailArmor`, `EternityBarkArmor`, `EternityLeafShield`, `GenBelt`, `StdShield`, `Robes`, `ThreadbareRobes`, `WalkingBoots`.
- **Scanned (31 constructor-only):** `ChainMailArmor`, `ChainMailVest`, `Dress`, `FieldPlate`, `FullPlate`, `GenDress`, `GenHat`, `GenPants`, `GenShirt`, `GenShoes`, `GreatHelm`, `Hat`, `Helmet`, `HideArmor`, `Hood`, `LeatherArmor`, `LeatherBoots`, `LeatherBracers`, `LeatherCap`, `LeatherGauntlets`, `MetalBracers`, `PaddedArmor`, `Pants`, `RingMail`, `ScaleMail`, `Shirt`, `Shoes`, `SplintMail`, `SteelGauntlets`, `StuddedLeatherArmor`, `BandedArmor`. Fields were extracted by script and checked against method declarations: no overrides beyond `ID()` and the constructor.
- **Outside the bucket, read in part:** `Items/interfaces/Armor.java`, `Shield.java`, `MiscMagic.java`, `Wearable.java` (constants), `Weapon.java` (constants and map), `StdMOB.java` (wear positions and layering), `CMProps.java` (speed, condition lists, formula), `resources/lists.ini` (condition text), `MUDFight.java` (`adjustedArmor`), `TimsLibrary.java` (power level and armor value helpers; partial), `BodyPiercing.java` (piercing writes and limit; partial), `Commands/Dress.java` (full), `Spell_MageArmor.java` (partial), `Spell_ImprovedRepairingAura.java` (one line), `Prayer_UnholyArmament.java` (partial), `PlayerRace.java` and `StdRace.java` (outfits), `Import.java` (partial), `CMGenEditor.java` (partial), `WeatherAffects.java` (one line), `DefaultPhyStats.java` (default speed), `StdItem.java` (defaults, `affectPhyStats`), and the recipe files in `resources/skills/` (counts only).
- **Skipped:** the `StdContainer` body (bucket b172), the full `TimsLibrary`, the crafting engine, and the decoding of recipe wear-location strings such as `MS-10:HEAD||...`, which I did not verify.
- **Repository:** only the chapter file was written. The `NEWOVERVIEW/_coverage/b123.tsv` file in the repo was not touched.
