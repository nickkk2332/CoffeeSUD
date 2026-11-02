// JS17: msg(), cancel(), room(), findItem(), door(), signal() and state() in a <SCRIPT>.
FIXFILE = "js17-helpers";
var R = M.location();
function s(x) { return "" + x; }
["FIXJS17M", "FIXJS17R", "FIXJS17I", "FIXJS17S", "FIXJS17G", "FIXJS17D"].forEach(clearVar);

// msg() and cancel(): a CNCLMSG_PROG reads its message, and refuses a small one
var V = newMob("GenMob", R);
V.setName("Fixjs17victim");
var S1 = scriptOn(R, "CNCLMSG_PROG DAMAGE ALL\n<SCRIPT>\nsetVar('FIXTEST', 'FIXJS17M', '' + msg().value() + ':' + msg().source().Name());\nif (msg().value() < 5) cancel();\n</SCRIPT>\n~\n");
var small = CMClass.getMsg(M, V, null, CMMsg.MASK_ALWAYS | CMMsg.TYP_DAMAGE, null);
small.setValue(3);
check("msg-cancel", !R.okMessage(M, small) && gvar("FIXJS17M") == "3:" + M.Name(), "FIXJS17M='" + gvar("FIXJS17M") + "'");
var big = CMClass.getMsg(M, V, null, CMMsg.MASK_ALWAYS | CMMsg.TYP_DAMAGE, null);
big.setValue(9);
check("msg", R.okMessage(M, big) && gvar("FIXJS17M") == "9:" + M.Name(), "FIXJS17M='" + gvar("FIXJS17M") + "'");
R.delScript(S1);

// a door, from anywhere: a scratch room through a free exit
var dir = -1;
for (var d = 0; d < CM.core.Directions.NUM_DIRECTIONS(); d++) if (R.getRoomInDir(d) == null && R.getRawExit(d) == null) { dir = d; break; }
var R2 = scratchRoom();
var E1 = CMClass.getExit("GenExit"), E2 = CMClass.getExit("GenExit");
E1.setDoorsNLocks(true, false, true, true, false, false);
E2.setDoorsNLocks(true, false, true, true, false, false);
var op = CM.core.Directions.getOpDirectionCode(dir);
R.rawDoors()[dir] = R2; R.setRawExit(dir, E1);
R2.rawDoors()[op] = R; R2.setRawExit(op, E2);
var dname = s(CMLib.directions().getDirectionName(dir));

// a signal's listener
var bell = newMob("GenMob", R);
bell.setName("a fixjs17bell");
scriptOn(bell, "SIGNAL_PROG fixjs17ring\nmpsetvar FIXTEST FIXJS17G rang\n~\n");
var lamp = CMClass.getItem("GenItem");
lamp.setName("a fixjs17 lamp");
R.addItem(lamp);
var m = scriptedMob("a fixjs17 helper", "RAND_PROG 100\n<SCRIPT>\n"
	+ "var r = room('" + R.roomID() + "');\n"
	+ "setVar('FIXTEST', 'FIXJS17R', '' + (r == host().location()));\n"
	+ "var l = findItem(r, 'lamp');\n"
	+ "setVar('FIXTEST', 'FIXJS17I', (l == null) ? 'none' : '' + l.Name());\n"
	+ "state('$i', 'mood', 'merry');\n"
	+ "setVar('FIXTEST', 'FIXJS17S', '' + state('$i', 'mood'));\n"
	+ "var opened = door('" + R.roomID() + "', '" + dname + "', 'OPEN');\n"
	+ "setVar('FIXTEST', 'FIXJS17D', '' + opened);\n"
	+ "signal('fixjs17bell fixjs17ring');\n"
	+ "</SCRIPT>\nmpdisable\n~\n");
tickMob(m);
runQueued(m.scripts().nextElement());
runQueued(bell.scripts().nextElement());
check("room", gvar("FIXJS17R") == "true", "FIXJS17R='" + gvar("FIXJS17R") + "'");
check("findItem", gvar("FIXJS17I") == "a fixjs17 lamp", "FIXJS17I='" + gvar("FIXJS17I") + "'");
check("state", gvar("FIXJS17S") == "merry", "FIXJS17S='" + gvar("FIXJS17S") + "'");
check("door", gvar("FIXJS17D") == "true" && E1.isOpen() && E2.isOpen(), "FIXJS17D='" + gvar("FIXJS17D") + "', open " + E1.isOpen() + "/" + E2.isOpen());
check("signal", gvar("FIXJS17G") == "rang", "FIXJS17G='" + gvar("FIXJS17G") + "'");

R.rawDoors()[dir] = null; R.setRawExit(dir, null);
V.destroy(); m.destroy(); bell.destroy(); lamp.destroy();
done();
