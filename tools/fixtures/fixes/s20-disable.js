// S20: a disable flag ending in * disables every ID that starts with what is
// before it, for abilities, commands, races, classes and factions
FIXFILE = "s20-disable";
var CMSecurity = CM.core.CMSecurity;
CMSecurity.setAnyDisableVar("ABILITY_Spell_Magic*");
CMSecurity.setAnyDisableVar("COMMAND_Gr*");
CMSecurity.setAnyDisableVar("RACE_Half*");
check("ability-wildcard", CMSecurity.isAbilityDisabled("Spell_MagicMissile"), "Spell_MagicMissile is not disabled");
check("ability-other", !CMSecurity.isAbilityDisabled("Spell_Sleep"), "Spell_Sleep is disabled too");
check("command-wildcard", CMSecurity.isCommandDisabled("Group"), "Group is not disabled");
check("command-other", !CMSecurity.isCommandDisabled("Look"), "Look is disabled too");
check("race-wildcard", CMSecurity.isRaceDisabled("HalfElf"), "HalfElf is not disabled");
check("race-other", !CMSecurity.isRaceDisabled("Human"), "Human is disabled too");
CMSecurity.removeAnyDisableVar("ABILITY_Spell_Magic*");
CMSecurity.removeAnyDisableVar("COMMAND_Gr*");
CMSecurity.removeAnyDisableVar("RACE_Half*");
check("removed", !CMSecurity.isAbilityDisabled("Spell_MagicMissile") && !CMSecurity.isCommandDisabled("Group"), "the flags stayed");
// STDTITLES turns off the stock titles file, unless the world has its own
var CMFile = CM.core.CMFile;
check("stock-titles", new CMFile("resources/titles.ini", null, 0).exists(), "no stock titles.ini");
CMSecurity.setAnyDisableVar("STDTITLES");
check("stdtitles-off", !new CMFile("resources/titles.ini", null, 0).exists(), "the stock titles are still read");
CMSecurity.removeAnyDisableVar("STDTITLES");
check("stdtitles-back", new CMFile("resources/titles.ini", null, 0).exists(), "the stock titles did not come back");
done();
