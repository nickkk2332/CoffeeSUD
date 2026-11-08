# 3Scapes: a related MUD with its own designs

[Dossier](README.md) · [3Kingdoms](three-kingdoms.md) · [Proposals](proposals.md)

The [official site](https://www.3scapes.org/) and its
[browsable help tree](https://www.3scapes.org/help/) are separate from We MUD
Together. They present a cooperative multi-realm game with highly differentiated
guilds. Their content includes both current features and explicitly future plans.

## Status matters

The [feature page](https://www.3scapes.org/world/features/) describes account-wide
**Tiers** as live progression using banked experience and unlockable talents.
It explicitly marks four other systems **upcoming**:

| Advertised plan | Distinctive design | Proposed CoffeeSUD version |
| --- | --- | --- |
| Generations | Rebirth exchanges a leveled character for permanent progression, including several thematic talent paths | P09: configurable retirement/reset manifest, bounded legacy rewards and a review of exactly what is lost/retained |
| Positioning | Opt-in party roles alter targeting, protection and damage opportunities | P06/A10: formation policies with a plain-text diagram; optional for an encounter or campaign |
| Primordials | Team bosses use vulnerability windows, fixation and coordinated responses | G03/A10: encounter state machines with readable telegraphs, contribution and recovery paths |
| World Events | Timed portals lead to multi-floor, group-scaled adventures with puzzles and bosses | G22/G01: scheduled or player-invoked events with explicit instance, time and reward contracts |

These are proposals advertised by 3Scapes, not claims about live server behavior.
Their publication is useful design evidence; a scheduled release was not
established in this review. No CoffeeSUD feature should depend on them having
shipped.

## Guild survey

The [guild index](https://www.3scapes.org/guilds/) says “Twenty-two ways to play”
and lists **Adventurer plus 21 named full-guild pages**. That is more precise
than describing it as 22 full guilds. The help tree contains additional legacy
guild topics; an entry alone does not establish current availability.

Each linked page below was examined as a public guild description. They describe
identity and play style more reliably than detailed current numeric balance.

| Guild | Distinctive reference | Proposal pattern |
| --- | --- | --- |
| [Adventurer](https://www.3scapes.org/guilds/adventurer/) | Starter play before committing | P01: temporary curricula and free trial builds |
| [Angel](https://www.3scapes.org/guilds/angel/) | Divine/Fallen allegiances and working through mortal bodies | P03/A21: allegiances, hosts and conduct |
| [Bard](https://www.3scapes.org/guilds/bard/) | Performance, fighting, stealth and lore can be mixed | A14/P02: nonexclusive disciplines and maintained performances |
| [Bladesinger](https://www.3scapes.org/guilds/bladesinger/) | Mentor-led combat and runecraft with later pacts | P04/I05/P10: services, enchantments and patron benefits |
| [Breed](https://www.3scapes.org/guilds/breed/) | Individualized, readiness-based psionic study | P02/P15: knowledge and access separate from raw XP |
| [Changeling](https://www.3scapes.org/guilds/changeling/) | Bodies change natural weapons, protection and possibilities | A21: form capability profiles |
| [Cyborg](https://www.3scapes.org/guilds/cyborg/) | Neural interface plus specialized implant choices | A08/I05: compatible body upgrades and installation requirements |
| [Druid](https://www.3scapes.org/guilds/druid/) | Sacrifice, decay, curses and healing share a source | P10/A15: one resource with different ethical/gameplay uses |
| [Elemental](https://www.3scapes.org/guilds/elemental/) | Master elements in stages; equipment use competes with elemental power | A21/P01: stage-specific powers and restrictions |
| [Fremen](https://www.3scapes.org/guilds/fremen/) | Stronger cooperative identity and group fighting | P03/P06: fellowship synergies with stacking limits |
| [Gentech](https://www.3scapes.org/guilds/gentech/) | Research chosen powers with experience-dependent failure risk | P01/I04: an experiment transaction rather than a fixed class grant |
| [Jedi](https://www.3scapes.org/guilds/jedi/) | Many careers and skills with a temptation theme | P01/P10: modular careers and conduct consequences |
| [Juggernaut](https://www.3scapes.org/guilds/juggernaut/) | A pilot's suit determines much of combat role | A19/A21: shell/loadout transitions and ownership |
| [Knight](https://www.3scapes.org/guilds/knight/) | Martial identity with an honor code; page notes evolving specifics | P03/P10: obligations with authored consequences |
| [Mage](https://www.3scapes.org/guilds/mage/) | Axioms/Theorems versus Analogies offer different approaches to power | A02/P02: different construction grammars sharing a spell runtime |
| [Monk](https://www.3scapes.org/guilds/monk/) | Unarmed capability depends on focus/composure | A15/A20: maintained combat state and explicit fallback |
| [Necromancer](https://www.3scapes.org/guilds/necromancer/) | A large guild space, long mystery and extensive power documentation | P04/P15/Q07: progression embedded in places and knowledge |
| [Priest](https://www.3scapes.org/guilds/priest/) | Patron choice changes spells and progression identity | P10: authored portfolios, services and favor |
| [Sii](https://www.3scapes.org/guilds/sii/) | Symbiotic identity eventually accesses different bodies | P05/A21: host and relationship state separate from current form |
| [Viking](https://www.3scapes.org/guilds/viking/) | A companion band, rune magic and a personal home | P05/P16: a household and followers as progression |
| [Warder](https://www.3scapes.org/guilds/warder/) | Single-weapon dedication with rank and community identity | I12/P03: weapon mastery and recognized qualifications |
| [Witch](https://www.3scapes.org/guilds/witch/) | Potions, curses and support within a coven | I04/P04/P08: shared production and guild services |

These themes reinforce the same architectural point: a guild needs more than
an ability list. It can own resource rules, knowledge access, a progression
graph, services, a home, an ethical contract and relationships. An author should
be able to add only the parts their world needs.

## Existing supporting systems

| Public documentation | Observation | CoffeeSUD opportunity |
| --- | --- | --- |
| [Professions](https://www.3scapes.org/help/professions/) | Minor, specialization and major slots unlock at stages and require earlier investment | P01/P02: cross-class disciplines with prerequisite budgets |
| [Crafting](https://www.3scapes.org/help/crafting/) | Gathering and production cooperate; stored materials and completed items have different reboot lifetimes | I04/G20: multi-stage production and an explicit persistence class for outputs |
| [Binding](https://www.3scapes.org/help/binding/) | Equip/pickup binding restricts who can use an item within the reboot | I20: inspectable ownership with explicit trigger, scope and expiry |
| [Waypoints](https://www.3scapes.org/help/waypoints/) | A last-activated location can determine the next login arrival | Existing travel/start rules can provide a similar pattern; distinguish arrival, fast travel and resurrection |
| [Death](https://www.3scapes.org/help/death/) | XP/stat loss, temporary penalties and services mitigating the toll | P14: a configurable death contract and recovery services; penalties need not be numeric loss |
| [Loyalty perks](https://www.3scapes.org/help/loyalty-perks/) | Play age unlocks a choice of perks | P13: optional attendance/progression milestones; avoid making a returning player permanently ineffective |
| [Equipment](https://www.3scapes.org/help/equipment/) | Acquisition and equipment choice are a major ongoing activity | I19/Q03: collections, comparison and source hints |
| [Getting started](https://www.3scapes.org/start/) | A starter phase introduces guild commitment | Use creation steps, kits, training areas and recruitment already in CoffeeSUD |

Housing, reputation, High Mortal status, exploration and community-facing
achievements appear throughout the site's feature/help directories. Most have
clear existing CoffeeSUD counterparts. The valuable extensions are permissions,
relationship-aware services, progression presentation and reusable project state,
not new copies of bank, clan or home managers.

## Things that should stay world choices

The site currently presents the game as PvP-free, while its help tree retains
Arena/Bloodmatch-style topics. This review did not log in to establish which
legacy activities remain callable. Preserve that uncertainty. Do not infer a
live PvP feature solely from an old help title.

Likewise, reboot-scoped equipment and online social support make sense in a
particular server economy. A CoffeeSUD campaign normally needs its equipment
and unfinished activities to restore faithfully. New policies should be
explicit world settings with previewed consequences, not incidental side
effects of a restart.

My strongest picks are formation roles (P06), staged cross-guild professions
(P01), meaningful guild homes (P04), and voluntary rebirth (P09). The advertised
Primordial design is useful inspiration for boss encounter authoring, with a
single-player version that lets companion policies cover coordinated reactions.
