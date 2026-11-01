// B9: WORLD PATH lays a chain of rooms, WORLD DIG makes one where the builder walks, each tagged
// TODO for WORLD CHECK until EDIT DESC writes it.
FIXFILE = "b9-dig";
var DM = CM.core.DigMode;
var Dirs = CM.core.Directions;
var R0 = M.location();
function has(text, part) { return ("" + text).replace(/\s+/g, " ").indexOf(part) >= 0; }
function list(a) { var L = new java.util.ArrayList(); for (var i = 0; i < a.length; i++) L.add(a[i]); return L; }
function free(R) { for (var i = 0; i < 4; i++) if (R.rawDoors()[i] == null && R.getRawExit(i) == null) return i; return -1; }
var made = [];

// the words, a quoted value in one word or over several
var a = DM.args(list(["e,n", "TYPE=StdRoom", "NAME=\"A", "fixb9", "trail\"", "LINK=one-way"]));
check("args", ("" + a.get("")) == "e,n" && ("" + a.get("NAME")) == "A fixb9 trail" && ("" + a.get("TYPE")) == "StdRoom" && ("" + a.get("LINK")) == "one-way", "" + a);
a = DM.args(list(["DESC=A whole fixb9 sentence."]));
check("args-one", ("" + a.get("DESC")) == "A whole fixb9 sentence.", "" + a);

// WORLD PATH: two rooms in a line; the builder stays
var d1 = free(R0);
var d1name = "" + CMLib.directions().getDirectionName(d1);
var back1 = Dirs.getOpDirectionCode(d1);
var said = "" + DM.path(M, d1name + "," + d1name, "StdRoom", true, "A fixb9 trail", "A fixb9 description.");
var A = R0.rawDoors()[d1];
var Bq = (A == null) ? null : A.rawDoors()[d1];
check("path", A != null && Bq != null && M.location() == R0 && has(said, "made 2 rooms"), said);
if (A != null) made.push(A);
if (Bq != null) made.push(Bq);
check("linked", A != null && A.rawDoors()[back1] == R0 && Bq != null && Bq.rawDoors()[back1] == A, "the rooms are not linked back");
check("named", A != null && ("" + A.displayText()) == "A fixb9 trail" && ("" + A.description()) == "A fixb9 description." && A.hasTag("TODO") && Bq.hasTag("TODO"), "the new rooms' text or tags");
// following what is there: the same path again makes nothing
said = "" + DM.path(M, d1name + "," + d1name, "StdRoom", true, null, null);
check("follow", has(said, "made 0 rooms and followed 2 exits"), said);
// one way, turning
var d4 = free(Bq);
var d4name = "" + CMLib.directions().getDirectionName(d4);
said = "" + DM.path(M, d1name + "," + d1name + "," + d4name, "StdRoom", false, null, null);
var C = (Bq == null) ? null : Bq.rawDoors()[d4];
if (C != null) made.push(C);
check("one-way", C != null && C.rawDoors()[Dirs.getOpDirectionCode(d4)] == null && has(said, "made 1 rooms and followed 2 exits"), said);

// WORLD DIG: a walk where there is no exit makes a room
A.bringMobHere(M, false);
var d2 = free(A);
DM.on(M, "StdRoom", true);
check("dig-on", has(DM.describe(M), "WORLD DIG is on"), "" + DM.describe(M));
CMLib.tracking().walk(M, d2, false, true, false);
var D = A.rawDoors()[d2];
if (D != null) made.push(D);
check("dig", D != null && M.location() == D && D.hasTag("TODO"), "the builder is in " + CMLib.map().getExtendedRoomID(M.location()));
DM.off(M);
var here = M.location();
var d3 = free(here);
CMLib.tracking().walk(M, d3, false, true, false);
check("dig-off", here.rawDoors()[d3] == null && M.location() == here, "a room was made with WORLD DIG off");

// WORLD CHECK lists them; EDIT DESC writes one and takes the tag off
var parts = new java.util.HashSet(); parts.add("TEXT");
var found = "" + CM.core.WorldCheck.report(CM.core.WorldCheck.run(R0.getArea(), parts));
check("check", has(found, CMLib.map().getExtendedRoomID(A) + ": tagged TODO"), found.substring(0, Math.min(400, found.length)));
var S = new JavaAdapter(CM.Common.FakeSession, { isFake: function() { return false; } });
S.initialize(null, "MEMORY");
var Bld = newMob("GenMob", A);
Bld.setName("Fixb9builder");
Bld.setSavable(false);
Bld.setSession(S);
S.setMob(Bld);
["E", "0", "A fixb9 written room.", "S", "Y"].forEach(function(x) { S.setFakeInput(x); });
var L = list(["EDIT", "DESC", "HERE"]);
CMClass.getCommand("EditCmd").execute(Bld, L, 0);
check("written", ("" + A.description()) == "A fixb9 written room." && !A.hasTag("TODO"), A.description() + " / " + A.hasTag("TODO"));

// put the world back
R0.bringMobHere(M, false);
Bld.setSession(null);
Bld.destroy();
for (var i = made.length - 1; i >= 0; i--) CMLib.map().obliterateMapRoom(made[i]);
check("clean", R0.rawDoors()[d1] == null, "the first room is still linked");
CM.core.Unsaved.clear();
CM.core.UndoLog.clear();
done();
