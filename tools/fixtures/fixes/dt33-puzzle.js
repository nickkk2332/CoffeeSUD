// DT33: PUZZLE CHECK finds the rooms, doors and things scripts name that are not there.
FIXFILE = "dt33-puzzle";
var PC = CM.core.PuzzleCheck;
var Directions = CM.core.Directions;
var R = M.location();
function has(text, part) { return ("" + text).replace(/\s+/g, " ").indexOf(part) >= 0; }
var B = newMob("GenMob", R);
B.setName("Fixpuzzler");
var BS = CMClass.getCommon("FakeSession");
BS.initialize(null, "MEMORY");
B.setSession(BS);
BS.setMob(B);
var mark = 0;
function out() { var all = "" + field(BS, "bout").toString(); var s = all.substring(mark); mark = all.length; return s; }
function puzzle(words) { var L = new java.util.ArrayList(); L.add("PUZZLE"); var w = words.split(" "); for (var i = 0; i < w.length; i++) if (w[i].length > 0) L.add(w[i]); CMClass.getCommand("PuzzleCmd").execute(B, L, 0); return out(); }

// a direction with no exit, and the open exit up (no door)
var none = -1;
for (var d = 0; d < 4; d++) if (R.getRawExit(d) == null) { none = d; break; }
var noneName = ("" + CMLib.directions().getDirectionName(none)).toLowerCase();
var up = R.getRawExit(Directions.UP);
var s = scriptedMob("a fixes puzzle box", "RAND_PROG 100\nmpgoto Nowherefix#99\nmpat #99999 say hi\nmpclose " + noneName + "\n"
	+ (((up != null) && !up.hasADoor()) ? "mpopen up\n" : "") + "mpoload a flurbling fixwidget\nmpoload GenItem\nmpgoto $i\n~\n");
var probs = "" + PC.checkHost(s, R);
check("room", has(probs, "a fixes puzzle box inline:2: MPGOTO Nowherefix#99: no such room"), probs);
check("relative-room", has(probs, "inline:3: MPAT #99999: no such room"), probs);
check("no-exit", has(probs, "MPCLOSE " + noneName + ": no exit that way from " + R.roomID()), probs);
if ((up != null) && !up.hasADoor())
	check("no-door", has(probs, "MPOPEN up: the exit from " + R.roomID() + " has no door"), probs);
check("catalog", has(probs, "MPOLOAD a flurbling fixwidget: no item class or catalog entry of that name"), probs);
check("class-ok", !has(probs, "MPOLOAD GenItem"), probs);
check("variable", !has(probs, "$i"), probs);
var said = puzzle("CHECK AREA " + R.getArea().Name());
check("command", has(said, "PUZZLE CHECK " + R.getArea().Name() + ": ") && has(said, "MPGOTO Nowherefix#99: no such room"), said);
check("usage", has(puzzle(""), "Puzzle wiring in " + R.getArea().Name()), "no words");
s.destroy();
B.setSession(null);
B.destroy();
done();
