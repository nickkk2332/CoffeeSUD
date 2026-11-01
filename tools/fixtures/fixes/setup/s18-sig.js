// S18: what the generated mines are like: their rooms and mobs, as one line
// to compare before the save and after the restore
FIXFILE = "s18-sig";
var A = M.location().getArea();
check("in-mines", /^[0-9]+_Mines$/.test(String(A.Name())), "in " + A.Name());
var rooms = [], mobs = 0;
for (var r = A.getProperMap(); r.hasMoreElements();) {
	var R = r.nextElement();
	rooms.push(R.roomID() + "=" + R.displayText());
	for (var i = 0; i < R.numInhabitants(); i++) if (R.fetchInhabitant(i).isMonster()) mobs++;
}
rooms.sort();
var sum = 0, s = rooms.join("|");
for (var j = 0; j < s.length; j++) sum = (sum * 31 + s.charCodeAt(j)) % 1000000007;
M.tell("FIXINFO mines " + A.Name() + " at " + CMLib.map().getExtendedRoomID(M.location()) + ", " + rooms.length + " rooms, " + mobs + " mobs, shape " + sum);
done();
