//extends com.planet_ink.coffee_mud.Commands.StdCommand
// S6: a command class of the world's own, in WORLDDIR/classes/commands/
var CMParms=Packages.com.planet_ink.coffee_mud.core.CMParms;

function ID()
{
	return "FixWorld";
}

var commands=CMParms.toStringArray(CMParms.parse("FIXWORLD"));

function getAccessWords()
{
	return commands;
}

function execute(mob, commands, x)
{
	mob.tell("FIXWORLDCMD ran from the world's own classes.");
	return false;
}
