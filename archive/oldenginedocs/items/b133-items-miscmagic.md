# Items/MiscMagic: spell consumables, wands, bags, cards and archon tools

> Part of the CoffeeMud NEWOVERVIEW. Bucket: `b133` (Items/MiscMagic) · 47 files · 8760 lines of Java.

File paths below are relative to `com/planet_ink/coffee_mud/` inside the CoffeeMUD folder. Line numbers refer to the files as they stand in the repository. Base classes (`StdItem`, `StdContainer`, `StdDrink`, `StdFood`, `StdJournal`, `Ring_Ornamental`) are covered by b172 and are only named here.

## Summary

This bucket holds the magic items that are not ordinary weapons, armor or containers: spell-carrying scrolls, potions, pills and powders; wands and a magic instrument that fire when the holder speaks; bags and chests with unusual containment rules; protection rings; playing cards and dice for table games; and archon-only tools that change a character's level, class, skills or stats. Most spell-carrying items keep a semicolon list of ability IDs (with optional parameters) in their misc text, and cast the spells at the item's level, raised to the spell's lowest qualifying level. Speech triggers wands, the instrument and the cube, through a trailing message added to the spoken text, which makes it the most distinctive mechanic here. The `Gen*` classes show the "data-driven item" pattern, where builders edit every field in-game. Several mechanics have bugs or dead code (see Problems and cruft), so borrow the design rather than the code.

## Key files and types

| File | Role | Lines |
|---|---|---|
| `Items/MiscMagic/StdScroll.java` | Base spell scroll: spell list, read gate, mana, cast, value | 437 |
| `Items/MiscMagic/GenScroll.java` | Data-driven scroll (spell text set by builders) | 153 |
| `Items/MiscMagic/ScrollSpell.java` | Scroll preset with two charges | 51 |
| `Items/MiscMagic/StdPotion.java` | Drink-to-cast potion, consumed on use, can be poured | 307 |
| `Items/MiscMagic/GenPotion.java` | Data-driven potion: a liquid container that carries spells | 239 |
| `Items/MiscMagic/GenMultiPotion.java` | Multi-sip flask: spells on every sip, consumed when empty | 256 |
| `Items/MiscMagic/StdPill.java` | Eat-to-cast pill | 203 |
| `Items/MiscMagic/GenPill.java` | Data-driven pill | 190 |
| `Items/MiscMagic/GenSuperPill.java` | Archon pill: stat, level, class and race deltas read from a text string | 258 |
| `Items/MiscMagic/StdPowder.java` | Dust: thrown at a target or sniffed, casts then vanishes | 209 |
| `Items/MiscMagic/GenPowder.java` | Data-driven powder | 157 |
| `Items/MiscMagic/GoodyBag.java` | Archon bag that restocks itself with coins and pills | 180 |
| `Items/MiscMagic/StdWand.java` | Base wand: magic word, speech trigger, charges, mana, level | 421 |
| `Items/MiscMagic/GenWand.java` | Data-driven wand (spell, enchant type, max charges) | 245 |
| `Items/MiscMagic/Wand_Fire.java` | Fixed wand, magic phrase BLAZE, BURN | 117 |
| `Items/MiscMagic/Wand_Nourishment.java` | Fixed wand, magic word SHAZAM feeds a target | 104 |
| `Items/MiscMagic/Wand_Advancement.java` | Archon wand, LEVEL UP grants experience | 133 |
| `Items/MiscMagic/WandArchon.java` | Archon wand with eight administrative commands | 573 |
| `Items/MiscMagic/GenMagicInstrument.java` | Instrument that casts while played, "stop" ends the effect | 410 |
| `Items/MiscMagic/StdPlayingCard.java` | One card; value, suit and face-up state encoded in `ability()` | 295 |
| `Items/MiscMagic/GenPlayingCard.java` | Data-driven card | 120 |
| `Items/MiscMagic/StdHandOfCards.java` | Container of cards: shuffle, sort, lookup, reveal | 640 |
| `Items/MiscMagic/StdDeckOfCards.java` | 52-card deck with per-player hands | 326 |
| `Items/MiscMagic/StdDice.java` | Die: rolled by dropping it; shows its value on the floor | 237 |
| `Items/MiscMagic/GenDice.java` | Data-driven die | 122 |
| `Items/MiscMagic/Ring_Protection.java` | Protection ring: armor bonus and damage-type resistance | 232 |
| `Items/MiscMagic/RingOfColdProtection.java` | Fixed ring type (silver) | 50 |
| `Items/MiscMagic/RingOfElectricity.java` | Fixed ring type (copper) | 51 |
| `Items/MiscMagic/RingOfFortitude.java` | Fixed ring type (mithril) | 51 |
| `Items/MiscMagic/RingOfJustice.java` | Fixed ring type (sapphire) | 51 |
| `Items/MiscMagic/RingOfMagicResistance.java` | Fixed ring type (opal) | 51 |
| `Items/MiscMagic/BagOfHolding.java` | Sack with negative weight; two of them implode when combined | 79 |
| `Items/MiscMagic/GenBagOfHolding.java` | Data-driven bag of holding (key name in readable text) | 205 |
| `Items/MiscMagic/BagOfEndlessness.java` | Archon bag that duplicates whatever is put into it | 124 |
| `Items/MiscMagic/GenBagOfEndlessness.java` | Data-driven endless bag | 209 |
| `Items/MiscMagic/PortableHole.java` | Bag of holding with capacity scaled by item level | 56 |
| `Items/MiscMagic/HerbChest.java` | Herb-only bag of holding behind a zapper mask | 60 |
| `Items/MiscMagic/Ingredients.java` | Archon bag stocked with one of every raw material; never rots | 100 |
| `Items/MiscMagic/CubicGate.java` | Planeshift cube: 1 to 5 planes, numbered faces | 190 |
| `Items/MiscMagic/LifeFountain.java` | Endless drink that cures, once per drinker per 16 s window | 154 |
| `Items/MiscMagic/DruidicMonument.java` | Immovable stones, a marker used by druid chants | 65 |
| `Items/MiscMagic/PracticePoint.java` | Coin that grants one practice when picked up | 86 |
| `Items/MiscMagic/TrainingSession.java` | Coin that grants one train when picked up | 86 |
| `Items/MiscMagic/ManualArchon.java` | One-shot book that makes the reader an archon | 143 |
| `Items/MiscMagic/ManualAdvancement.java` | Five-use book that grants experience | 114 |
| `Items/MiscMagic/ManualClasses.java` | Unlimited book that changes class (see Problems) | 132 |
| `Items/MiscMagic/ArchonJournal.java` | Journal that only system operators can hold | 88 |

Shared interfaces used throughout: `Items/interfaces/Wand.java` (`Wand`, `Wand.WandUsage`, `WAND_OPTIONS` at lines 50-62), `Items/interfaces/Scroll.java` (`ScrollUsage`, line 49), `Items/interfaces/PlayingCard.java` (suits and card values at lines 30-33), `Common/interfaces/PhyStats.java` (`IS_BONUS`, "flagging this object as being magical", lines 564-565).

## How it works

### 1. Spell-carrying consumables: the shared model

**Data model.** Every spell-carrying item keeps a `List<Ability> spells` and mirrors it in `miscText` as `ID(params);ID(params)`. `StdScroll.buildSpellsList(List)` writes that string (`StdScroll.java:102-114`) and `buildSpellsList(String)` parses it back, instantiating each ability with `CMClass.getAbility`, copying it and storing the parameter text as its misc text (`StdScroll.java:116-139`). Parsing drops any ability in the Archon domain (`StdScroll.java:131`; the same filter appears in `StdPotion.java:211`, `StdPill.java`, and `StdPowder.java`).

**Value.** Each spell adds `100 × lowestQualifyingLevel(spell)` to a base of 200 (`StdScroll.java:141-150`, `StdPotion.java:221-230`). An empty scroll is worth 0 (`StdScroll.java:159-164`). A drunk potion is worth 0 (`StdPotion.java:89-95`).

**Casting level.** The cast level is the item's level, never below the spell's lowest qualifying level: `level = max(item level, lowest)` (`StdScroll.java:296-299`, `StdPotion.java:133-136`, `StdPill.java:77-95`). `StdPowder.spreadIfAble` uses the item level alone (`StdPowder.java:87-89`), so a powder can invoke a spell below its minimum level (what the spell then does was not traced).

**Caster.** Potions and pills cast through a factory mob from `CMLib.map().getFactoryMOB`, set to the cast level (`StdPotion.java:124-138`). That mob is the invoker, so the effect takes its level from the caster. The factory mob is destroyed after use unless one of the resulting effects has it as invoker (`StdPotion.java:140-147`). The source comment reads "don't always, because the invoker, and their level, are often needed."

**Flag.** Items with spells set `PhyStats.IS_BONUS`, which marks them as magical: scrolls and wands only while they have charges (`StdScroll.java:326-335`, `StdWand.java:94-101`), and potions and pills whenever they carry spells (`StdPotion.java:78-87`).

**Drinking, eating, pouring, throwing.**
- `StdPotion.drinkIfAble` skips spells that can neither target nor affect an Item target (`StdPotion.java:130-132`). The owner casts unless the drinker is someone else (`:125`). The potion then gets a `;DRUNK` marker in its spell list (`setDrunk`, `:98-114`) and vanishes (`:267-274`).
- A potion used as the tool of a pour casts on the pour target (`StdPotion.java:287-302`).
- `StdPill` casts on the eater when eaten (`StdPill.java:77-95`, executed on TYP_EAT at `:186`). It has no target filter.
- `StdPowder` is thrown at a target or sniffed (`StdPowder.java:99-116`). If any spell is `QUALITY_MALICIOUS` (`:118-126`), the thrower casts on the target. Otherwise, for a mob target, the target casts on itself (`:85-90`). The powder is destroyed after use (`:93`).
- `GenMultiPotion` casts its spells on every sip through a silent trailer TYP_DRINK (`GenMultiPotion.java:223`, `drinkIfAble` at `:150-186`). It is marked `;DRUNK` and destroyed only when `liquidRemaining() <= thirstQuenched()` (`:182`).

### 2. Scrolls (StdScroll)

**Reading needs a read-magic skill.** A reader may read a spell only if they have a `ScrollUsage` effect whose read type is any (below zero) or matches the spell's class (`StdScroll.java:230-245`). The read-magic skills each return an enchant type from `getReadMagicType()`: `Spell_ReadMagic` returns `ACODE_SPELL` (`Abilities/Spells/Spell_ReadMagic.java:85-88`), `Prayer_ReadPrayer` returns `ACODE_PRAYER`, `Song_ReadMusic` returns `ACODE_SONG` and `Chant_ReadRunes` returns `ACODE_CHANT`. Once the check passes, the reader sees "X glows softly." and the item stores the reader's name in `readableScrollBy` (`:247-251`). Anyone else gets "The markings look magical, and are unknown to you." (`:321-322`). The name is per item instance and is not part of the spell list.

**Expertise.** If the reader has the read-magic skill, its expertise LEVEL bonus is added to the cast level (`StdScroll.java:249-250`, used at `:296`).

**Naming a spell.** `read <words>` is matched with `CMLib.english().fetchEnvironmental` over the spell names. Trailing words that do not match are peeled off and become invoke parameters (`StdScroll.java:270-291`).

**Mana (`useTheScroll`, `StdScroll.java:167-187`).** With `q = qualifyingLevel(mob, spell)`:
- `q > 0` and `q < qualifyingClassLevel`: 0 mana.
- `q > 0` otherwise: 5 mana.
- `q <= 0` (not granted by the reader's classes): 25 mana.
- Short of mana: "You don't have enough mana." (`:182`).

**Cast.** `thisOne.invoke(mob, params, null, true, level)` and one charge is used (`StdScroll.java:300-301`). With no spell name, the scroll lists its contents as "Level NN: name" (`:309-316`).

**Identity text.** "A scroll of X, Y and Z Charges: N" (`StdScroll.java:189-222`).

### 3. Wands and the speech trigger

This is the most distinctive mechanic in the bucket. The steps are:

1. **Speech.** The Say command sends a MSG_SPEAK. Its source message carries the quoted text, and targeted speech also fills the target message (`Commands/Say.java:336-341`).
2. **Detection.** A wand held by the speaker sees the TYP_SPEAK in `StdWand.executeMsg` (`StdWand.java:310-330`). If the wand is not in the inventory and the text contains its magic word (`checkWave`, `StdWand.java:216-222`, a case-insensitive substring test), it adds a trailer TYP_WAND_USE. That trailer carries the text as its message and the spoken-to target as its tool (`:328`).
3. **Wave.** The trailer returns to `executeMsg` (`StdWand.java:305-309`) and calls `waveIfAble` (`:224-296`). The magic word is cut from the text, and the rest up to a quote becomes the parameters (`:231-238`).
4. **Skill.** The speaker must hold a `WandUsage` ability. Four exist: `Skill_WandUse` (spells), `Skill_RelicUse` (prayers), `Skill_ShardUse` (chants) and `PlayInstrument` (songs, `Abilities/Common/PlayInstrument.java:35, 88-91`). The loop at `StdWand.java:241-260` takes a usage whose enchant type matches the spell's class if one exists (it breaks out early for that case, and only for an untyped wand). Otherwise it keeps the last usage that qualifies. A usage with no enchant type qualifies for any wand. The skill makes a proficiency check. On failure: "@x1 glows faintly for a moment, then fades." (`:261-262`).
5. **Charge and mana.** An empty wand says "@x1 seems spent." (`:268-269`). Otherwise `useTheWand` takes mana, and on success the wand says "@x1 glows brightly.", uses one charge, and casts (`:274-289`).
6. **Level.** The cast level is `wand level + min(ability, 10) + expertise(wand usage skill)`, raised to the spell's lowest qualifying level (`StdWand.java:282-287`). The spell is invoked with the speaker as caster and the target, and the wand skill gains proficiency (`:288-289`).

**Magic words.** A wand's default word is picked at random from the `MAGIC_WORDS` list (`StdWand.java:45`). `setSpell` and `setMiscText` instead hash the spell ID into the list with `getWandWord` (`StdWand.java:137-143`, `:146-158`). The list is 700 entries in `resources/lists.ini` (key `MAGIC_WORDS`, starting at line 1066). The hash is Java `String.hashCode`, which the language specifies, so the word for a given spell ID stays the same as long as the word list does not change.

**Mana (`useTheWand`, `StdWand.java:103-126`).** The base is 5 for a spell the holder's classes grant and 25 otherwise. It is reduced by `2 × wandUse.abilityCode()` (the wand skill's level) and floored at 5. The `q < qualifyingClassLevel` branch sets 0, but the floor then makes it 5, so that branch changes nothing (see Problems).

**Value and identity.** A wand with a spell and no charges is worth 0 (`StdWand.java:128-135`). Its identity text reads "'A wand of SPELL+N Charges: X/Y" plus "Say the magic word :`WORD` to the target." (`:169-202`), so once a wand is identified, its word is public information.

**Subclass wands.** Each fixed wand overrides the trigger or its check.
- `Wand_Fire` (`Wand_Fire.java:43-116`). Level 12, 50 charges, value 20000. Its word is the literal phrase "BLAZE, BURN" (`:57`). BLAZE casts `Spell_BurningHands` for one charge (`:88-98`), and BURN casts `Spell_Fireball` for five charges, needing more than four (`:99-109`). The BURN branch cannot be reached by speech (see Problems).
- `Wand_Nourishment` (`Wand_Nourishment.java:73-101`). Word "SHAZAM" (`:55`), aimed at a mob. It adds 50 hunger, with "You are full." or "You feel nourished." depending on the return of `adjHunger`. The meaning of that return value is unverified.
- `Wand_Advancement` (archon only, `Wand_Advancement.java:74-~120`). Word "LEVEL UP" (`:56`). The wand fizzles unless the speaker's last typed command contains "LEVEL UP" (`:87-93`). It charges mana through `useTheWand`, using `CMClass.getAbility("Falling")` (`Abilities/Misc/Falling.java`) as the reference ability at level 0, so Falling is only a stand-in for a 25-mana cost and is never cast. Then it calls `postExperience` until the target levels, with up to 100 tries, or `leveler().level()` for classes without experience (`~:96-118`).

**GenWand.** Stores the spell as an ID in `spellText` (`GenWand.java:71-87`). `setReadableText` accepts an ability ID as the spell (`:95-108`). Its editable stat codes are ENCHTYPE, SPELL and MAXUSES (`GenWand.java:149`, `:175-203`).

### 4. WandArchon and the archon commands

`WandArchon` (`WandArchon.java:37-573`) is archon-only, with 99,999 charges set in its constructor. Its features are:

- **Hunger and thirst.** While worn properly, the holder's hunger and thirst are pinned at maximum (`WandArchon.java:78-88`).
- **Speech.** `checkWave` (`:150-162`) accepts any of its eight words as a token in the message, if the wand is held and worn. Its `okMessage` (`:90-129`) also copies the first word of any spoken sentence that is one of the magic words into `secretWord` (`:116-127`, line 125 assigns it). This runs for speech it can see in the room, not only for speech aimed at the wand.
- **Anti-scripting check.** `safetyCheck` (`:131-148`) fizzles the wand unless the player's last typed command contains the spoken words, or the first word of that command is one of the player's aliases. "The wand fizzles in an irritating way." It skips mobs without a session.

The commands are listed in the content table below. The implementation details that matter for design:

- **Level targets.** "LEVEL ALL UP" uses `LASTPLAYERLEVEL` from the session's properties, defaulting to 30, and goes to 100 if the target is already at or above that (`WandArchon.java:174-190`). "LEVEL X UP" repeats the level step up to 100 tries each (`:205-237`). "LEVEL X DOWN" removes experience to drop a level, or calls `unLevel` when experience is not used (`:418-456`).
- **Skill learning.** "GAIN" and "PRAC" bring skills to 100% by repeatedly calling `helpProficiency` while temporarily setting the target's INTELLIGENCE to 99, then restoring it (`:268-275`, `:306-313`, `:370-377`, `:400-407`). The learning roll is effectively forced.
- **BLAST and BURN.** BLAST sets hit points, mana and movement to 1 (`:547-555`). BURN deals `(round(Math.random()*6)) * 3 + 1` damage, which is 1 to 19 (`:563-565`).
- **RESTORE and REFRESH.** RESTORE cures disease and poison, removes bleeding, injury, amputation and broken limbs, and resets to max (`:458-523`). REFRESH removes bleeding and resets to max (the branch between RESTORE and REWIND).
- **REWIND.** Resets `NEXTCAST` to 0 on every ability, clearing cooldowns (`:524-545`).

### 5. The magic instrument (GenMagicInstrument)

`GenMagicInstrument` (`:37-410`) is a wand whose magic word is empty (`magicWord()` returns "", `:187-190`). Its `checkWave` accepts any message while it is held (`:159-165`). So any `WandUsage` action made with it, including a PlayInstrument command, passes the command text to `waveIfAble` as spell parameters (`:273-301`). `PlayInstrument` sends its command text as the message (`Abilities/Common/PlayInstrument.java:241`, `:325`). The word "stop" ends the instrument's spell on the player (`GenMagicInstrument.java:167-185`). When wielded, the instrument refuses the owner's weapon attacks, with "<S-NAME> play(s) <O-NAME>." (`:253-271`).

### 6. Bags, chests and containment rules

- **BagOfHolding** (`BagOfHolding.java:51-79`). Capacity 1000. Its base weight is set to `-recursiveWeight()` (`:76`), so the bag offsets the weight of what it holds. Putting one bag of holding into another destroys both, with "The bag implodes in your hands!" (`:58-70`). `PortableHole` extends it with capacity `200 × level` (`PortableHole.java:50`) and inherits the implosion rule.
- **BagOfEndlessness** (archon only, `BagOfEndlessness.java:55-56`, capacity MAX-1000, weight 1). `okMessage` admits a put only for actors with the COPYITEMS or CMDITEMS security flag (`:70-78`). `executeMsg` copies the item and its deep contents into the owner's inventory or room (`:93-110`), and the original stays in the bag. This is a duplicator.
- **GoodyBag** (archon only, extends `BagOfEndlessness`). On its first event with an owner, if it is empty, it fills itself with 100 coins of every denomination in every currency (`GoodyBag.java:74-88`) and 17 pills (`:94-174`), listed in the content section.
- **Ingredients** (archon only, extends `BagOfEndlessness`). Creates one item per raw material resource (`Ingredients.java:77-84`; `Items/interfaces/RawMaterial.java` has 210 resource entries, lines 611-820). Each created item and each item put in has its decay time set to 0 and its "Poison_Rotten" effect removed (`:61-62`, `:93-94`).
- **HerbChest** (`HerbChest.java:42-59`). A bag of holding restricted to herbs (`:49`), capacity 500. It carries a `Prop_HaveZapper` with mask `+SYSOP -MOB -skills +alchemy` (`:53-56`). The zapper blocks GET, EAT and DRINK for a mob who fails the mask, at a set percentage, with a message (`Abilities/Properties/Prop_HaveZapper.java:283-327`). What each mask term requires is decided in `Libraries/MUDZapper.java` and was not traced.
- **GenBagOfHolding and GenBagOfEndlessness** store their lock key name in `readableText` (`GenBagOfEndlessness.java:58-76`).

### 7. Cards, decks, hands and dice

**Card encoding** (`StdPlayingCard.java:91-108`). The card's `phyStats().ability()` holds:
- bits 0-3: value 2 to 14, where 14 is the ace (value 1 is also accepted as an ace in lookups);
- bits 4-5: suit, where 0 is spades, 16 clubs, 32 hearts and 48 diamonds (`PlayingCard.suits`, `Items/interfaces/PlayingCard.java:30`);
- bit 64: face up.

The name is regenerated when the value changes (`StdPlayingCard.java:274-291`): "the ace of spades", "a playing card, the king of hearts, lies here", or "A mangled card" for an invalid code. Suit names carry colour codes (`:247-261`).

**Hand** (`StdHandOfCards.java:50-640`). A container with capacity 53 that accepts only PlayingCard (`:78-80`, `:592-598`). Its contents live in the owner's inventory if the owner is a mob or room, and otherwise in `backupContents` (`:61`, `:92-98`).
- **Shuffle** (`:112-143`). Runs `5 × n` iterations. Each one picks a random index (`CMLib.dice().roll(1, n, -1)`, which is 0 to n-1) and moves that card to the end of the list. This is not a Fisher-Yates shuffle, and its quality is unverified.
- **Sort** (`:271-302` ace high, `:308-350` ace low). Selection sort by value, then by suit, using the encoded suit values.
- **Lookup.** By string code such as "SA" or "H10" (`getCard(String)`, `:406-424`), or by bit code (`getCard(int)`, `:507-522`). Both scan the contents.
- **Reveal.** When someone looks at or examines the owner, the face-up cards are listed in a trailer message, "<T-NAME> <T-HAS-HAVE> the following cards shown: ..." (`:605-632`). This supports stud-style play.

**Deck** (`StdDeckOfCards.java:62-330`). A `StdHandOfCards` that holds all 52 cards.
- **Filling.** The first `okMessage` on an owned deck fills it (`:299-300`), and `createDeck` fills it on creation (`:151-170`). `makeAllCards` builds the 52 cards from the suit and value arrays (`:118-127`). Every card added is turned face down (`addCard` override).
- **Shuffle by voice.** A message whose target is the deck, speech type, and whose text contains "SHUFFLE" at a position after the start (`indexOf > 0`, `:308-310`) shuffles the deck and prints "<S-NAME> thoroughly shuffled <O-NAME>." to the room. The original message is cancelled.
- **Hands.** `hands` is a pair list keyed by MOB (`StdDeckOfCards.java:73`), managed with `addPlayerHand` and `removePlayerHand`. `resetDeckBackTo52Cards` (`:182-215`) collects the cards back, destroys hands, and rebuilds the deck if fewer than 52 cards remain.
- **Users.** `Behaviors/PokerDealer.java:109`, `:610-615`, `:1329`, `:1343` and `:1667` create decks and draw from them, and manage player hands.

**Dice** (`StdDice.java:37-237`). The default die is six-sided, with the side count stored in `ability()` (`:52`).
- **Roll by dropping.** `rollTheBones` (`:69-150`) finds the die or dice named in a ROLL or THROW command, and sends MSG_DROP with "<S-NAME> roll(s) <T-NAME>." (`:148`). The TYP_DROP handler then rolls `1..sides` and sets the floor text to "X is lying here, showing N." (`:160-173`). Picking the die up resets the floor text (TYP_GET).
- **Commands.** `okMessage` (`:179-233`) handles ROLL and THROW, which arrive as unknown or failed commands. The social "roll" reuses the arguments of the speaker's previous command if it began with ROLL (`:186-201`).

### 8. Protection rings

`Ring_Ornamental` picks one of 14 ring types at random, `roll(1, 14, -1)` (`Items/Basic/Ring_Ornamental.java:64`), and sets name and base value for it. `Ring_Protection` (`Items/MiscMagic/Ring_Protection.java`) adds two effects:

- **Armor.** While worn, not held, the ring subtracts its own armor plus its `ability()` from the wearer's armor stat (`:53-60`, the calculation at `:58`). The ability is the "+N" in the ring's identity.
- **Resistance.** In `executeMsg` (`:215-232`), an incoming malicious message whose minor type equals the ring's type (`correctTargetMinor`, `:73-107`) is resisted if `rollChance` (`:109-130`) passes. The chances are 15% for opal, 15% for sapphire and 5% for mithril. Every other ring type always resists. The call is `CMLib.combat().resistanceMsgs`.

The mapping from ring type to damage message is in the content table. The fixed-type subclasses (`RingOfColdProtection.java` and the other four) set their level after `super()`. The identity text for every type is never set, which is a dead-code bug covered in Problems.

### 9. Cubic gate (planeshift)

`CubicGate` (`Items/MiscMagic/CubicGate.java`) is a planar travel item.
- **Planes.** On the first time a mob owns it with no saved text, `resetCube` picks `roll(1, 5, 1)` planes, always including "Prime Material" (`:84-101`, the roll at `:91`). The pool is `StdPlanarAbility.getAllPlaneKeys()` (`Abilities/StdPlanarAbility.java:1528-1535`), which reads `resources/skills/planesofexistence.txt`. That file has 37 planes.
- **Faces.** `setCubeDescription` (`:71-83`) numbers the faces with words: "one" up to the number of planes. The identity text reads "Hold, and say 'one' to '<last word>' to activate."
- **Activation.** A single-word speech to the cube, while it is held, that matches a number word (`:172-175`) adds a TYP_WAND_USE carrying that plane's name. The cube then invokes `Spell_Planeshift` with that plane (`:133-152`). If the holder is already on that plane, the reply is "Nothing happens."

### 10. LifeFountain

`LifeFountain` (`LifeFountain.java:52-66`) is a drink source with 999,999 liquid, thirst quenched 250, capacity 0, and `SENSE_ITEMNOTGET` (it cannot be taken). Filling it is refused: "You can't fill the fountain of life." (`:68-90`).

Its silent drink, the one with no messages, casts `Prayer_CureLight`, `Prayer_RemovePoison` and `Prayer_CureDisease` at the item's level (`:126-132`). This happens once per drinker in a window kept in `lastDrinks`, with a 16,000 ms constant (`:102`, `:109`). A visible drink adds the silent drink as a trailer (`:147`), so the cure always rides on a drink. `Abilities/Prayers/Prayer_FountainLife.java:124` creates these fountains.

### 11. Coins, manuals and the archon journal

- **PracticePoint and TrainingSession** (`PracticePoint.java:61-80`, `TrainingSession.java:61-80`). Each is a weightless coin that vanishes on GET or REMOVE. Picking it up from the room gives +1 practice or +1 train, unless the mob already owned it (`!mob.isMine(this)`). Both recover the room's stats.
- **ManualAdvancement** (archon only, five uses, `ManualAdvancement.java:70-103`). Reading requires a read-magic skill that accepts any type or spells, and then gives experience to the next level (`postExperience`, or `leveler().level()` for classes without experience).
- **ManualClasses** (archon only, unlimited uses, `ManualClasses.java:70-119`). Meant to advance the reader to the next class in the list. It does not do that; see Problems.
- **ManualArchon** (archon only, one use, `ManualArchon.java:77-134`). Detaches the session, sets all base stats to 25, levels the reader to 100 through the XP loop, makes the reader an archon at class level 30, adds 30 to their level, sets XP, runs the class start and outfit, saves the player to the database, and destroys the book.
- **ArchonJournal** (archon only, `ArchonJournal.java:60-85`). Anyone other than a system operator who tries to hold, wear, wield, get, push or pull it drops it with "@x1 flashes and falls out of <S-HIS-HER> hands!".

### 12. The "Gen" data-driven pattern

Every `Gen*` class stores its fields as XML in `miscText` through `CMLib.coffeeMaker()` (for example `GenPotion.java:103-112`), and exposes them to the builder as stat codes through `getStat` and `setStat` (`GenPotion.java:116-225`, `GenWand.java:149-220`). The free-text field `readableText` holds a short data string for spells, pill effects and key names.

The pills read their effects from a key-value mini-language. `CMParms.getParmPlus` (`core/CMParms.java:2761`) finds a key by a case-insensitive, start-partial match and reads a signed number after it. So `lev` matches `level+1` and `trai` matches `train+1`. The match is forgiving and gives no error for a typo.

The `Gen*` classes repeat the same boilerplate: a static code list cached per class, a `getStatCodes` that calls `CMProps.getStatCodesList`, and a `sameAs` loop. Examples are `GenPowder.java`, `GenDice.java:110-120`, `GenPlayingCard.java:101-115`, `GenMagicInstrument.java:382-400`, and `GenPotion.java:211-232`.

## Content worth mining

### Magic words (700 entries)

`resources/lists.ini`, key `MAGIC_WORDS`, starting at line 1066. The list is nonsense and stage-magic words, which suits a "say the word" trigger. Sample entries: ABRACADABRA, ALAKAZAM, ACCIO, DIFFINDO, EVANESCO, CONFUNDUS, SHAZAM, BIBBIDY, BEGONE, ALOHA, ABRA, ACKASHAZAM, BOOMBOOM. Wands take a stable word from this list by hashing the spell ID (`StdWand.java:137-143`).

### Fixed wands

| Wand | Word | Effect | Charges, value, level |
|---|---|---|---|
| Wand_Fire | BLAZE, BURN (literal phrase) | BLAZE: Burning Hands (1 charge). BURN: Fireball (5 charges), unreachable | 50, 20000, 12 |
| Wand_Nourishment | SHAZAM | Feeds the target 50 hunger | default |
| Wand_Advancement (archon) | LEVEL UP | Grants one level to the target | 50, 20000 |
| WandArchon (archon) | see below | Eight admin commands | 99999 |

WandArchon commands (`WandArchon.java:45`, identity text at `:60`):

| Command | Effect |
|---|---|
| LEVEL ALL UP | Levels the target to `LASTPLAYERLEVEL` (default 30) |
| LEVEL X UP / LEVEL X DOWN | Gains or loses X levels (X defaults to 1) |
| GAIN X UP / GAIN ALL UP | Brings one skill, or all class skills, to 100% |
| PRAC X UP / PRAC ALL UP | Brings one of the target's existing skills, or all of them, to 100% |
| RESTORE | Cures disease, poison, bleeding, injury, amputation and broken limbs; resets to max |
| REFRESH | Clears bleeding and resets to max |
| REWIND | Clears all ability cooldowns |
| BLAST | Sets hit points, mana and movement to 1 |
| BURN | 1 to 19 fire damage in steps of 3 |

### Pill and bag catalogue (GoodyBag and GenSuperPill)

`GoodyBag.java:94-174` fills a pill for each of these, with the effect in the stat-delta string:
- Training and practice: train+1, prac+1, ques+1 (quest points).
- Experience: expe+100, expe+500, expe+1000, expe+2000, expe+5000.
- Base stats: str+1, int+1, wis+1, dex+1, con+1, cha+1.
- Levels: level+1, level+5, level+10.

The same bag also holds coins: 100 of every denomination in every currency (`GoodyBag.java:86-88`).

### Ring catalogue

`Items/Basic/Ring_Ornamental.java` (names and base values) and `Ring_Protection.java:73-107` (damage message). The "identity" column is what the ring's text would say if `setIdentity` ran (see Problems).

| Level constant | Base name | Protects against | Resist chance | Identity text (intended) |
|---|---|---|---|---|
| 0 GOLD_RING | a gold ring | Acid | always | Sweet Water |
| 1 SILVER_RING | a silver ring | Cold | always | Seven Winters |
| 2 COPPER_RING | a copper ring | Electricity | always | Storms |
| 3 PLATINUM_RING | a platinum ring | Gas | always | Sweet Air |
| 4 GOLD_RING_DIAMOND | a diamond ring | Fire | always | Eternal Blaze |
| 5 GOLD_RING_RUBY | a ruby ring | Mind attacks | always | Focus |
| 6 GOLD_RING_OPAL | an opal ring | Magic (spell casting) | 15% | Mage's Bane |
| 7 GOLD_RING_TOPAZ | a diamond ring (same name as 4) | None; +60 protection | n/a | a named +60 ring |
| 8 GOLD_RING_SAPPHIRE | a sapphire ring | Criminal behaviour (justice) | 15% | Justice |
| 9 MITHRIL_RING | a mithril ring | Physical attacks (weapon attacks) | 5% | Fortitude |
| 10 GOLD_RING_PEARL | a pearl ring | Water | always | the Wave |
| 11 GOLD_RING_EMERALD | an emerald ring | None; +50 protection | n/a | a named +50 ring |
| 12 STEEL_RING | (default) | None; random +0 to +49 protection | n/a | "A ring of protection + N" |
| 13 BRONZE_RING | a bronze ring | Paralysis | always | Bronze Shield |

Fixed-type subclasses: `RingOfColdProtection` (silver), `RingOfElectricity` (copper), `RingOfFortitude` (mithril), `RingOfJustice` (sapphire), `RingOfMagicResistance` (opal).

### Playing cards and dice

- **Cards.** 52 cards: four suits (spades, clubs, hearts, diamonds), values 2 to 14 (`Items/interfaces/PlayingCard.java:30-33`). Named "the ace of spades" and so on. A string code is a suit letter plus a value, such as "SA", "H10" or "DK" (`StdHandOfCards.java:406-424`).
- **Dice.** Any sides count (`StdDice` default 6). Rolled by dropping (`StdDice.java:160-173`). The value shows on the floor while it lies there.

### Planes of existence (37)

`resources/skills/planesofexistence.txt` lists 37 planes. Each line has categories, opposed planes, alignment, prefixes and mob resistances. Names include Earth, Air, Fire, Water, Positive, Negative, Magma, Ooze, Minerals, Dust, Smoke, Ice, Lightning, Vacuum, Radiance, Ash, Steam, Salt, Astral, Ethereal, Nirvana, Arcadia, Seven Heavens, Twin Paradises, Elysium, Happy Hunting Grounds, Olympus, Gladsheim, Limbo, Pandemonium, Abyss, Tarterus, Hades, Gehenna, Nine Hells, Acheron and Concordant Opposition. Earth and Air are opposed, as are Fire and Water.

### Raw materials in the Ingredients bag

About 210 raw material resource entries (`Items/interfaces/RawMaterial.java:611-820`). The bag keeps one of each, never rotting, for crafting.

### Druidic stones

`DruidicMonument` (`DruidicMonument.java:36-65`) is a 1000-weight, non-gettable item called "the druidic stones" and described to players as "some large stones". Druid chants check for it in the room: `Abilities/Druid/Chant_Uplift.java:76` and `Chant_GroveWalk.java:86, 95`, and `CharClasses/Druid.java:399`.

## Notable design ideas

- **Speech as a trigger.** Speech is a message. A trailing message carries the words to the item's wave logic (`StdWand.java:328`), so one mechanism serves wands, the instrument and the cube. Other game parts use the same trailer pattern: `Spell_Spellbinding`, `Spell_StoreSpell` and `Prayer_StorePrayer` store spoken words, and `Prop_ItemSlot` marks slot actions as wand use.
- **Words from spell IDs.** A wand's word is derived from its spell's ID by a hash into a shared word list (`StdWand.java:137-143`). The word is stable for a spell, but any player who knows it can use the wand.
- **Anti-scripting checks.** `Wand_Advancement.java:87-93` and `WandArchon.safetyCheck` (`:131-148`) fizzle the item unless the player typed the words themselves. This blocks echoed or scripted speech from invoking powers.
- **Negative-weight bags.** `BagOfHolding.recoverPhyStats` sets base weight to the negative of contents (`BagOfHolding.java:76`), so a full bag is weightless to carry.
- **Paradox rule.** Two bags of holding put into each other destroy each other (`BagOfHolding.java:58-70`).
- **Drop to roll.** Dice roll on a drop, and the rolled number stays on the floor as display text (`StdDice.java:160-173`). Nothing extra needs to be stored.
- **Reveal trailer.** Looking at a card holder reveals only the face-up cards, through a trailer (`StdHandOfCards.java:605-632`).
- **Stat-delta pills.** A pill can carry many stat changes in one string (`GenSuperPill.java:114-232`), so builders add effects without new classes.
- **Numbered planeshift cube.** The cube's faces are the activation keys, and planes are picked at random from a data file (`CubicGate.java:71-101`).
- **Per-drinker cooldown.** The fountain tracks each drinker's last cure (`LifeFountain.java:102-132`).

## Problems and cruft

1. **Wand_Fire's BURN is unreachable by speech.** The trigger needs the literal phrase "BLAZE, BURN" (`Wand_Fire.java:57`, `StdWand.java:216-222`). That phrase always contains "BLAZE", so the BLAZE branch (`:88-98`) always handles it. The BURN branch (`:99-109`) runs only when BLAZE fails, which happens when charges or mana are short. BURN then needs more than 4 charges and the same mana, so it fails too; when the wand has more than four charges but too little mana, the mana message prints twice. Also, the trigger needs the exact phrase, so saying "blaze" alone does nothing even though the identity text says "Responds to 'Blaze' and 'Burn'" (`Wand_Fire.java:50`).
2. **Misleading second message after a mana failure.** In `StdScroll.readIfAble`, if the spell is found but `useTheScroll` fails for lack of mana, the else branch still runs because `spellName` is not empty. The reader then sees "That is not written on the scroll." after "You don't have enough mana." (`StdScroll.java:293-305`).
3. **ManualClasses picks the wrong class.** The loop compares `thisC` where it should compare `lastC` (`ManualClasses.java:97-111`, line 104). `thisC` stays at the first class in the list unless it is the reader's current class. So the reader is moved to the first class in the list, or to the second class if they are already in the first. The code never selects "the next class" as intended. The first class in the list depends on load order (`core/CMClass.java:217, 3248`) and was not determined.
4. **Ring identities never set.** `Ring_Protection.recoverPhyStats` calls `super.recoverPhyStats()` first (`Ring_Protection.java:62-75`). `Ring_Ornamental.recoverPhyStats` updates the shared `lastLevel` field before that, so the later check `lastLevel != level` in `Ring_Protection` is always false. `setIdentity()` (`:131`) therefore never runs. Its only callers are that check (`:69`), and the only places that set `ability()` for the TOPAZ (+60), EMERALD (+50) and STEEL (random +0 to +49) rings are inside `setIdentity` (`:176`, `:198`, `:206`). So those rings never get an armor bonus, and no identity text or base value increase is applied to any type. This follows from reading the code and should be confirmed in play.
5. **Dead mana branch in wands.** `StdWand.useTheWand` sets 0 mana for cross-class spells (`StdWand.java:109-110`), then the floor sets it back to 5 (`:116-118`). The branch changes nothing.
6. **Wand usage index mismatch.** `WandUsage.WAND_OPTIONS` (`Items/interfaces/Wand.java:56-62`) is indexed by enchant type, but its labels are not the `ACODE` values. Index 3 is labelled CHANT, while `ACODE_SONG` is 3 and `ACODE_CHANT` is 8 (`Abilities/interfaces/Ability.java:677, 687`). `StdWand.waveIfAble` uses the table to prefer a matching usage (`StdWand.java:251-258`), so for an untyped wand a song usage is matched against chant spells. The impact is limited to untyped wands, and the table entry for skills (index 4) is also off.
7. **LifeFountain cooldown is not 16 seconds.** Expired entries are removed only in a global sweep that runs at most once every 16 seconds (`LifeFountain.java:102-112`). A drinker's entry, once set, is not refreshed while it stays in the table. So a drinker can be blocked for up to about 32 seconds, depending on other drinkers. This is from reading the code and was not timed.
8. **False comment on hands.** The comment above `StdHandOfCards.addCard` says that a hand left with no cards "is destroyed" (`StdHandOfCards.java:156-162`). Nothing in the file destroys it. Only `StdDeckOfCards` destroys hands (`resetDeckBackTo52Cards`, `removePlayerHand`).
9. **Face-up cards and `getCard(int)`.** `getCard(int)` compares the whole `ability()` value (`StdHandOfCards.java:517`), so a face-up card (bit 64) never matches a plain value and suit code.
10. **Two-character value lookup.** `getFirstCardOfValue(String)` pads a one-character code with a space and then drops the first character (`:438`, `:442`). A value-only code of two characters, such as "10", therefore compares "0" and fails. A code with a suit prefix works. Which callers pass which form was not checked.
11. **Shuffle quality.** The shuffle moves random cards to the end `5n` times (`StdHandOfCards.java:112-143`). The source comment describes the same method. It is not a standard shuffle, and its statistical quality was not tested.
12. **No-op in GenSuperPill.** `mob.basePhyStats().setLevel(mob.basePhyStats().level())` (`GenSuperPill.java:127`) assigns a field to itself. The level delta is handled elsewhere in the same method.
13. **Side effect in WandArchon.okMessage.** Any speech the wand's `okMessage` sees in its room can change its stored magic word (`WandArchon.java:116-127`). This is probably unintended, because `checkWave` does not depend on the stored word.
14. **Math.random and uneven damage.** BURN uses `Math.round(Math.random() * 6)` (`WandArchon.java:563`). The two end values are half as likely as the middle ones, and the code bypasses `CMLib.dice()`.
15. **Powder destroyed without effect.** `StdPowder.spreadIfAble` destroys the powder after the loop, even when no spell's `canTarget` matched (`StdPowder.java:82-93`). The powder is also cast at the item level without the lowest-level floor used elsewhere (`:87-89`).
16. **Copy-paste Gen boilerplate.** Roughly ten `Gen*` classes repeat the same static code caches and stat lookups. A shared base class would remove the duplication.
17. **Fragile shuffle trigger.** The deck's shuffle test uses `indexOf("SHUFFLE") > 0` (`StdDeckOfCards.java:310`). It works only because targeted speech text starts with a formatting prefix (`Commands/Say.java:339-340`). A message without that prefix would not match.
18. **GoodyBag refills on restart.** `alreadyFilled` is not saved, so an emptied GoodyBag is restocked after a reboot (`GoodyBag.java:74-78`). It is archon-only, so the impact is small.
19. **Duplicate ring name.** The topaz ring's base name is "a diamond ring", the same as the diamond ring (`Ring_Ornamental.java:136`).
20. **Multiplayer assumptions.** Several mechanics assume other players: session-history checks (`Wand_Advancement.java:92`, `WandArchon.java:131-148`), per-drinker cooldowns on a shared fountain, hands keyed by MOB in shared decks (`StdDeckOfCards.java:73`), and "not mine" rules for coins (`PracticePoint.java:73`). A single-player port should drop the first, simplify the second and third, and always award the coins.

## Takeaways for a Python SUD

- **Spell items as data.** Store a spell reference as `SpellRef(id: str, params: str)` in a list, serialized as JSON or TOML. Do not use a semicolon string like `StdScroll.buildSpellsList` does (`StdScroll.java:102-139`).
- **One cast function.** Write `cast(caster, spell, level, target)` once and call it from scroll, potion, pill, powder and wand. Use `cast_level = max(item_level, spell.min_level)` (the rule at `StdScroll.java:296-299`).
- **Mana as a small pure function.** Known spells cost 5, unknown spells cost `max(5, 25 - 2 * skill_level)`, taken from `StdWand.java:103-126` without the dead branch.
- **Speech trigger.** A single parser can match a wand word in `say` input. Derive words with a stable hash such as `zlib.crc32`, not Python's `hash()`, which changes between processes. The "last typed command must contain the words" check (`WandArchon.safetyCheck`) exists to stop macros and scripts; for a single player it is optional.
- **Read gate.** A scroll needs a "read magic" skill. Keep a per-item `readable_by` name on the scroll, as `StdScroll.java:251` does (it stores one name), rather than a global flag.
- **Wands.** Store `charges` and a `magic_word`. Show the messages "glows brightly", "glows faintly" and "seems spent".
- **Cards.** `Card(rank, suit, face_up)` as a frozen dataclass. `Deck.shuffle()` should use `random.Random.shuffle` (Fisher-Yates), not the `5n` move scheme. A `Hand` is a list. Port `sort_by_rank` and the reveal as simple methods.
- **Dice.** A `roll(sides)` command that stores the last result in the room text ("the die shows 4") is enough. Skip the drop trick unless dice must lie on the floor.
- **Bags.** A bag of holding can report a weight of `-sum(contents weight)` so that its contents cost nothing to carry (the original does this at `BagOfHolding.java:76`). The implosion rule is a short special case with a message.
- **Rings and resistance.** Use a `DamageType` enum and a `resist_chance` field on a `Ring` dataclass, not level numbers as type codes (`Ring_Protection.java:73-107`). Test that the identity text is set, which the original does not do (Problems item 4).
- **Pills.** Store effects as a dict such as `{"str": 1, "level": 5}`, applied in a loop. This removes the `getParmPlus` string parser.
- **Cube and planes.** The planeshift cube is a good puzzle item. Use the numbered-face speech trigger, and keep the 37 planes as data (`resources/skills/planesofexistence.txt`).
- **Fountain.** Use a dict of last-use times keyed by player id, with `time.monotonic()`. No global sweep is needed (Problems item 7).
- **Drop from the design.** The archon items (`WandArchon`, `ManualArchon`, `GoodyBag`, `BagOfEndlessness`, `Ingredients`, `ManualClasses`), the duplication bag, the Gen XML storage and all the multiplayer-only checks. Keep the spell-scroll, potion, pill and wand ideas as the core.

## Coverage

**Read in full:** `StdScroll`, `StdWand`, `Wand_Fire`, `Wand_Nourishment`, `Wand_Advancement`, `WandArchon`, `GenMagicInstrument`, `StdPotion`, `StdPill`, `StdPowder`, `GenMultiPotion`, `GenSuperPill`, `GenPowder`, `GenDice`, `GenPlayingCard`, `StdPlayingCard`, `StdHandOfCards`, `StdDeckOfCards`, `StdDice`, `Ring_Protection`, `RingOfColdProtection`, `RingOfElectricity`, `RingOfFortitude`, `RingOfJustice`, `RingOfMagicResistance`, `BagOfHolding`, `BagOfEndlessness`, `PortableHole`, `HerbChest`, `Ingredients`, `GoodyBag`, `CubicGate`, `LifeFountain`, `DruidicMonument`, `PracticePoint`, `TrainingSession`, `ManualArchon`, `ManualAdvancement`, `ManualClasses`, `ArchonJournal`, `ScrollSpell`.

**Read in part:** `GenPotion` (key methods and stat codes), `GenWand` (the core methods and stat codes), `GenScroll` and `GenPill` (constructors and key methods), `GenBagOfHolding` and `GenBagOfEndlessness` (key methods and stat codes).

**Read outside the bucket, in part:** `Items/interfaces/Wand.java` (`WandUsage`, `WAND_OPTIONS`), `Items/interfaces/Scroll.java` (`ScrollUsage`), `Items/interfaces/PlayingCard.java`, `Items/interfaces/RawMaterial.java` (entry count), `Items/Basic/Ring_Ornamental.java` (constructor and naming), `Abilities/Common/PlayInstrument.java` (`WandUsage` and messages), `Abilities/Skills/Skill_WandUse.java` and the two siblings (enchant types), `Abilities/Spells/Spell_ReadMagic.java`, `Prayers/Prayer_ReadPrayer.java`, `Songs/Song_ReadMusic.java` and `Druid/Chant_ReadRunes.java` (read types), `Abilities/interfaces/Ability.java` (ACODE constants), `Abilities/Prayers/Prayer_FountainLife.java` (one line), `core/CMClass.java` (charClass list), `Common/interfaces/PhyStats.java` (IS_BONUS), `Abilities/Properties/Prop_HaveZapper.java` (`okMessage`), `Abilities/Properties/Prop_ItemSlot.java` (one trailer), `Abilities/StdPlanarAbility.java` (`getAllPlaneKeys`), `Commands/Say.java` (message construction), `core/CMParms.java` (`getParmPlus`), `Libraries/Dice.java` (`roll`), `Libraries/CMAbleMap.java` (`qualifyingLevel`), `resources/lists.ini` (`MAGIC_WORDS`), `resources/skills/planesofexistence.txt`, and `Behaviors/PokerDealer.java`, `Abilities/Druid/Chant_Uplift.java`, `Chant_GroveWalk.java`, `CharClasses/Druid.java` (references only).

**Searched only:** `Libraries/MUDZapper.java` (the SYSOP and skill terms, to confirm they exist), `Libraries/CoffeeLevels.java` (`getLevelExperience` only).

**Skipped:** none of the bucket's 47 files were left out. The behaviour of `RawMaterial` values, `Prop_HaveZapper` percentages, and the "fizzle" alias logic in `safetyCheck` was not traced to the end and is marked as unverified where it matters.
