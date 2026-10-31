// Puts a LOGIN_PROG on a room of the start area that is not the start room,
// for the played ST1 test: the room greets every login, wherever it happens.
var M = mob();
var CM = Packages.com.planet_ink.coffee_mud;
var here = M.location();
var R = null;
for (var e = here.getArea().getProperMap(); e.hasMoreElements();) {
	var X = e.nextElement();
	if (X != here) { R = X; break; }
}
var B = CM.core.CMClass.getBehavior("Scriptable");
B.setParms("LOGIN_PROG 100\nmpechoat world FIXGREET the quiet room heard $n log in.\n~\n");
R.addBehavior(B);
// as builder commands do: the database cache, then the world files at quit
CM.core.CMLib.database().DBUpdateRoom(R);
M.tell("FIXSETUP st1 room " + CM.core.CMLib.map().getExtendedRoomID(R));
