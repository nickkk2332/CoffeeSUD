// G9: GENERATE FILL roomtemplate FROM file ROOM|AREA [KEEPTEXT] [SEED=n]: the layout's tags from the rooms' real exits
// (node type, run, exits, gate, neighbours) and the depth from the entrance; text unless KEEPTEXT or GENLOCK; mobs, items, effects; exits left alone.
FIXFILE = "g9-fill";
var GS = CM.core.GenSource;
var D = CM.core.Directions;
function s(x) { return "" + x; }
function has(t, p) { return s(t).replace(/\s+/g, " ").indexOf(p) >= 0; }
var F = new java.io.File(new java.io.File(WorldFiles.worldDir(), "resources/randareas"), "fixg9.xml");
F.getParentFile().mkdirs();
java.nio.file.Files.write(F.toPath(), new java.lang.String('<AREADATA><ROOM id="fixg9_rooms" select="any-1">'
	+ '<ROOM class="StdRoom" condition="$roomtag_nodetype=\'leaf\'"><TITLE>A glade, depth $roomtag_depth</TITLE><DESCRIPTION>A quiet glade of $areaname.</DESCRIPTION>'
	+ '<MOB class="GenMob" level="1" name="a fixg9 fox" display="A fixg9 fox is here." /></ROOM>'
	+ '<ROOM class="StdRoom" condition="$roomtag_nodetype=\'street\'"><TITLE>A path running $roomtag_noderun</TITLE><DESCRIPTION>A path to the gate $roomtag_nodegateexit.</DESCRIPTION></ROOM>'
	+ '<ROOM class="StdRoom" condition="$roomtag_nodetype=\'square\'"><TITLE>A crossing $roomtag_nodeexits</TITLE><DESCRIPTION>A crossing of $AREA_NUMROOMS rooms.</DESCRIPTION>'
	+ '<ITEM class="GenItem" name="a fixg9 signpost" display="A fixg9 signpost stands here." /><AFFECT class="Prop_NoSave" /></ROOM>'
	+ '</ROOM></AREADATA>').getBytes("UTF-8"));
var here = M.location();
var gateDir = -1;
for (var d = 0; d < 4; d++) if (gateDir < 0 && here.getRoomInDir(d) == null && here.getRawExit(d) == null && here.getRoomInDir(D.getOpDirectionCode(d)) == null) gateDir = d;
function cmd(words) { var L = new java.util.ArrayList(); words.forEach(function (w) { L.add(w); }); CMClass.getCommand("Generate").execute(M, L, 0); }
function link(a, dir, b) { var E = CMClass.getExit("Open"); a.setRawExit(dir, E); a.rawDoors()[dir] = b; b.setRawExit(D.getOpDirectionCode(dir), E); b.rawDoors()[D.getOpDirectionCode(dir)] = a; }
function mobsNamed(R, n) { var c = 0; for (var i = 0; i < R.numInhabitants(); i++) if (s(R.fetchInhabitant(i).Name()) == n) c++; return c; }
function doors(R) { var o = []; for (var d = 0; d < D.NUM_DIRECTIONS(); d++) o.push(R.rawDoors()[d] == null ? "-" : s(R.rawDoors()[d].roomID())); return o.join(","); }
var A = CMClass.getAreaType("StdArea");
A.setName("Fixg9Wood");
CMLib.map().addArea(A);
var R = [];
for (var i = 0; i < 4; i++) { var X = CMClass.getLocale("StdRoom"); X.setRoomID("Fixg9Wood#" + i); X.setArea(A); X.setDisplayText("Fixg9 hall " + i); X.setDescription("Dug by hand."); R.push(X); }
try {
	// #0 the entrance (the gate one side, #1 the other); #1 a crossing to #2 and #3, both leaves
	var op = D.getOpDirectionCode(gateDir);
	link(here, gateDir, R[0]);
	link(R[0], gateDir, R[1]);
	var side = (gateDir == D.NORTH || gateDir == D.SOUTH) ? D.EAST : D.NORTH;
	link(R[1], gateDir, R[2]);
	link(R[1], side, R[3]);
	var before = R.map(doors).join("|");
	R[1].bringMobHere(M, false);
	cmd(["GENERATE", "NOSAVE", "FILL", "fixg9_rooms", "FROM", "randareas/fixg9.xml", "areaname=Fixg9Wood", "AREA", "SEED=4"]);
	var run = (gateDir == D.NORTH || gateDir == D.SOUTH) ? "ns" : "ew";
	check("street", s(R[0].displayText()) == "A path running " + run && s(R[0].description()) == "A path to the gate " + s(CMLib.directions().getDirectionName(op)).toLowerCase() + ".",
		R[0].displayText() + " / " + R[0].description());
	check("square", has(R[1].displayText(), "A crossing ,") && s(R[1].description()) == "A crossing of 4 rooms." && R[1].findItem("a fixg9 signpost") != null && R[1].fetchEffect("Prop_NoSave") != null,
		R[1].displayText() + " / " + R[1].description());
	check("leaf", s(R[2].displayText()) == "A glade, depth 2" && s(R[3].displayText()) == "A glade, depth 2" && s(R[2].description()) == "A quiet glade of Fixg9Wood."
		&& mobsNamed(R[2], "a fixg9 fox") == 1 && mobsNamed(R[3], "a fixg9 fox") == 1, R[2].displayText() + " / " + R[3].displayText() + " foxes " + mobsNamed(R[2], "a fixg9 fox"));
	check("exits", R.map(doors).join("|") == before && R[1].getRoomInDir(side) === R[3], before + "\n" + R.map(doors).join("|"));
	// one room, keeping its text
	R[3].setDisplayText("Hand room");
	R[3].bringMobHere(M, false);
	cmd(["GENERATE", "NOSAVE", "FILL", "fixg9_rooms", "FROM", "randareas/fixg9.xml", "areaname=Fixg9Wood", "ROOM", "KEEPTEXT"]);
	check("keeptext", s(R[3].displayText()) == "Hand room" && mobsNamed(R[3], "a fixg9 fox") == 2 && mobsNamed(R[2], "a fixg9 fox") == 1, R[3].displayText() + " foxes " + mobsNamed(R[3], "a fixg9 fox"));
	// a GENLOCK room keeps its text
	R[2].setDisplayText("Locked room");
	GS.lock(R[2], true);
	cmd(["GENERATE", "NOSAVE", "FILL", "fixg9_rooms", "FROM", "randareas/fixg9.xml", "areaname=Fixg9Wood", "AREA"]);
	check("genlock", s(R[2].displayText()) == "Locked room" && s(R[3].displayText()) == "A glade, depth 2" && mobsNamed(R[2], "a fixg9 fox") == 2, R[2].displayText() + " / " + R[3].displayText());
	check("help", has(CMLib.help().getHelpText("GENERATE", M, true), "GENERATE FILL"), "no help");
} finally {
	here.bringMobHere(M, false);
	here.rawDoors()[gateDir] = null; here.setRawExit(gateDir, null);
	CMLib.map().obliterateMapArea(A);
	if (F.exists()) F["delete"]();
}
done();
