// G13: GENERATE AREA ... UNLINKED; GEN_BACKTICKS=false keeps apostrophes; GENSOURCE records how an area was made;
// REGENERATE AREA [SEED=n|SAME] builds it again keeping GENLOCK rooms (Prop_GenLock, REGENERATE LOCK) and their exits.
FIXFILE = "g13-regenerate";
var GS = CM.core.GenSource;
function s(x) { return "" + x; }
function has(t, p) { return s(t).replace(/\s+/g, " ").indexOf(p) >= 0; }
var F = new java.io.File(new java.io.File(WorldFiles.worldDir(), "resources/randareas"), "fixg13.xml");
F.getParentFile().mkdirs();
java.nio.file.Files.write(F.toPath(), new java.lang.String('<AREADATA><AREA id="fixg13_area" layout="TREE" requires="areaname=$,areasize=int">'
	+ '<NAME>$areaname</NAME><SIZE>$areasize</SIZE><CLASS>StdArea</CLASS><ROOM select="first">'
	+ '<ROOM class="StdRoom"><TITLE>The miner\'s hall</TITLE><DESCRIPTION select="any-1"><DESCRIPTION>A cold miner\'s hall $roomtag_index.</DESCRIPTION>'
	+ '<DESCRIPTION>A damp hall $roomtag_index.</DESCRIPTION><DESCRIPTION>A dusty hall $roomtag_index.</DESCRIPTION><DESCRIPTION>A narrow hall $roomtag_index.</DESCRIPTION>'
	+ '<DESCRIPTION>A low hall $roomtag_index.</DESCRIPTION><DESCRIPTION>A wet hall $roomtag_index.</DESCRIPTION></DESCRIPTION><EXIT class="Open" /></ROOM>'
	+ '</ROOM></AREA></AREADATA>').getBytes("UTF-8"));
var here = M.location();
function freeDir() { for (var d = 0; d < CM.core.Directions.NUM_DIRECTIONS(); d++) if (here.getRoomInDir(d) == null && here.getRawExit(d) == null) return d; return -1; }
function cmd(C, words) { var L = new java.util.ArrayList(); words.forEach(function (w) { L.add(w); }); CMClass.getCommand(C).execute(M, L, 0); }
function rooms(A) { var o = {}; for (var r = A.getProperMap(); r.hasMoreElements();) { var R = r.nextElement(); o[s(R.roomID())] = R; } return o; }
function snap(A) { var o = []; var m = rooms(A); for (var k in m) o.push((k + "|" + s(m[k].displayText()) + "|" + s(m[k].description())).replace(/`/g, "'")); o.sort(); return o.join("\n"); }
function links(A, but) {
	// every exit inside the area points at the room the area has by that ID, both ways
	var m = rooms(A), bad = [];
	for (var k in m) for (var d = 0; d < CM.core.Directions.NUM_DIRECTIONS(); d++) {
		var T = m[k].rawDoors()[d];
		if (T == null) continue;
		if (T.getArea() == A && m[s(T.roomID())] !== T) bad.push(k + " " + d + " stale");
		if (T.getArea() == A && T.rawDoors()[CM.core.Directions.getOpDirectionCode(d)] !== m[k] && k != but && s(T.roomID()) != but) bad.push(k + " " + d + " one-way");
	}
	return bad.join(",");
}
var made = [];
try {
	// unlinked, apostrophes kept, GENSOURCE
	CMProps.instance().setProperty("GEN_BACKTICKS", "false");
	var exitsBefore = [];
	for (var d = 0; d < CM.core.Directions.NUM_DIRECTIONS(); d++) exitsBefore.push(here.rawDoors()[d]);
	cmd("Generate", ["GENERATE", "NOSAVE", "AREA", "fixg13_area", "FROM", "randareas/fixg13.xml", "areaname=Fixg13Tunnels", "areasize=5", "SEED=21", "UNLINKED"]);
	var A = CMLib.map().getArea("Fixg13Tunnels");
	made.push(A);
	var same = true;
	for (var d = 0; d < CM.core.Directions.NUM_DIRECTIONS(); d++) if (here.rawDoors()[d] !== exitsBefore[d]) same = false;
	check("unlinked", A != null && same && A.properSize() >= 3, "area " + A + " exits unchanged " + same);
	var src = GS.get(A);
	check("gensource", src != null && s(src.file) == "randareas/fixg13.xml" && s(src.id) == "fixg13_area" && src.seed == 21 && s(src.vars.get("areaname")) == "Fixg13Tunnels"
		&& has(A.getBlurbFlag("GENSOURCE"), "SEED=21"), s(A.getBlurbFlag("GENSOURCE")));
	var R0 = A.getRoom("Fixg13Tunnels#0");
	check("apostrophes", R0 != null && s(R0.displayText()) == "The miner's hall", R0 == null ? "no #0" : R0.displayText());
	CMProps.instance().setProperty("GEN_BACKTICKS", "");
	// the same seed again: the same rooms, new objects; the GENLOCK room kept with its exits
	var first = snap(A), oldRooms = rooms(A);
	var keepId = null;
	for (var k in oldRooms) if (k != "Fixg13Tunnels#0" && keepId == null) keepId = k;
	var K = oldRooms[keepId];
	K.setDescription("Written by hand.");
	K.bringMobHere(M, false);
	cmd("Regenerate", ["REGENERATE", "LOCK"]);
	check("lock", GS.isLocked(K), "not locked");
	R0.bringMobHere(M, false);
	cmd("Regenerate", ["REGENERATE", "NOSAVE", "AREA", "SEED=SAME"]);
	var now = rooms(A), kept = now[keepId] === K, fresh = true;
	for (var k in now) if (k != keepId && now[k] === oldRooms[k]) fresh = false;
	var firstNoKeep = first.split("\n").filter(function (l) { return l.indexOf(keepId + "|") != 0; }).join("\n");
	var nowNoKeep = snap(A).split("\n").filter(function (l) { return l.indexOf(keepId + "|") != 0; }).join("\n");
	check("same", kept && fresh && s(K.description()) == "Written by hand." && nowNoKeep == firstNoKeep && links(A) == ""
		&& M.location() === now["Fixg13Tunnels#0"] && s(now["Fixg13Tunnels#0"].displayText()) == "The miner`s hall",
		"kept " + kept + " fresh " + fresh + " links " + links(A) + " at " + M.location().roomID() + "\n" + firstNoKeep + "\n---\n" + nowNoKeep);
	// another seed: other rooms, the kept one still there; the seed is recorded
	cmd("Regenerate", ["REGENERATE", "NOSAVE", "AREA", "SEED=99"]);
	var now2 = rooms(A);
	check("other", now2[keepId] === K && s(K.description()) == "Written by hand." && GS.get(A).seed == 99 && s(A.getBlurbFlag("GENSEED")) == "99" && links(A, keepId) == "",
		"kept " + (now2[keepId] === K) + " seed " + GS.get(A).seed + " links " + links(A, keepId));
	K.bringMobHere(M, false);
	cmd("Regenerate", ["REGENERATE", "UNLOCK"]);
	check("unlock", !GS.isLocked(K), "still locked");
	here.bringMobHere(M, false);
	// a linked area: the gate is pointed at the new entrance, and back
	var dir = freeDir();
	cmd("Generate", ["GENERATE", "NOSAVE", "AREA", "fixg13_area", "FROM", "randareas/fixg13.xml", "areaname=Fixg13Mine", "areasize=4", "SEED=5", s(CMLib.directions().getDirectionName(dir))]);
	var B = CMLib.map().getArea("Fixg13Mine");
	made.push(B);
	B.getRoom("Fixg13Mine#0").bringMobHere(M, false);
	cmd("Regenerate", ["REGENERATE", "NOSAVE", "AREA", "SEED=6"]);
	var E0 = B.getRoom("Fixg13Mine#0");
	check("gate", here.rawDoors()[dir] === E0 && E0.rawDoors()[CM.core.Directions.getOpDirectionCode(dir)] === here && links(B) == "",
		"gate " + here.rawDoors()[dir] + " entrance " + E0);
	here.rawDoors()[dir] = null; here.setRawExit(dir, null);
	here.bringMobHere(M, false);
	check("help", has(CMLib.help().getHelpText("REGENERATE", M, true), "GENLOCK") && has(CMLib.help().getHelpText("GENERATE", M, true), "UNLINKED")
		&& has(CMLib.help().getHelpText("PROP_GENLOCK", M, true), "REGENERATE"), "no help");
} finally {
	CMProps.instance().setProperty("GEN_BACKTICKS", "");
	here.bringMobHere(M, false);
	made.forEach(function (A) { if (A != null) CMLib.map().obliterateMapArea(A); });
	if (F.exists()) F["delete"]();
}
done();
