// A behaviour for a room or an area, written in JavaScript (JS5).
// Copy this file to world/js/behaviors/Name.js, rename it, and change ID().
// The game loads every file in world/js/behaviors/ when it starts; in builder
// mode, LOAD BEHAVIOR world/js/behaviors/Name.js loads it again after a change.
// Give it to a room or area in its .cmare file, or with MODIFY ROOM, as any
// behaviour: Name FARE=5
//
// The line below is code: it names the Java class this one extends.
//extends com.planet_ink.coffee_mud.Behaviors.JSWorldBehavior

// the class's name: the file's, and unique among behaviours
function ID() { return "MyWorldBehavior"; }

// what BEHAVIORS shows of it
function accountForYourself() { return "a world script of mine"; }

// someone came into the room (on an area: any of its rooms)
function onEnter(mob, room)
{
	if(mob.isPlayer())
		this.echo("A bell rings as " + mob.name() + " comes in.");
}

// someone left the room
function onLeave(mob, room)
{
}

// the weather changed: was and now are names, as RAIN or CLEAR
function onWeather(area, was, now)
{
}

// the time of day changed: DAWN, DAY, DUSK or NIGHT
function onDaypart(area, was, now)
{
	if(now == "NIGHT")
		this.echo("The lamplighter makes his rounds.");
}

// Helpers it has: this.echo(text) to the room (or every room of the area with
// someone in it), this.roll("3d6+2"), this.spawn("catalog mob name"),
// this.room(), this.area(), and this.parm("FARE") for its own FARE=5.
// Every JavaScript also has require('name'), from world/js/lib/name.js.
