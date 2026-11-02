// A property (an effect that is always on its host), written in JavaScript (JS5).
// Copy this file to world/js/properties/Name.js, rename it, and change ID().
// The game loads every file in world/js/properties/ when it starts; in builder
// mode, LOAD ABILITY world/js/properties/Name.js loads it again after a change.
// Give it to a thing in its .cmare file, or with MODIFY, as any property:
// Name WORDS=hello
//
// The line below is code: it names the Java class this one extends.
//extends com.planet_ink.coffee_mud.Abilities.Properties.Property

// the class's name: the file's, and unique among abilities
function ID() { return "MyProperty"; }

function name() { return "my property"; }

// what the property says it does, in LOOK and the like
function accountForYourself() { return "Hums quietly."; }

// a message about to happen near the host; return false to stop it
function okMessage(host, msg)
{
	return this.super$okMessage(host, msg);
}

// a message that happened near the host
function executeMsg(host, msg)
{
	this.super$executeMsg(host, msg);
}

// the property's own text (WORDS=hello above) is this.text(), and one
// setting of it CMParms.getParmStr(this.text(), "WORDS", "")
var CMParms = Packages.com.planet_ink.coffee_mud.core.CMParms;
