// G14: the Regenerate behaviour: WHEN=SCRIPT|ONEMPTY|MUDDAY:n (MOON:phase), SEED=RANDOM|DAILY|WEEKLY|SAME|n, KEEP=, from the GENSOURCE;
// it waits while a player is inside; MPREGENERATE; GENERATED_PROG after; its STATE.
FIXFILE = "g14-regen";
var GS = CM.core.GenSource;
function s(x) { return "" + x; }
function has(t, p) { return s(t).replace(/\s+/g, " ").indexOf(p) >= 0; }
var F = new java.io.File(new java.io.File(WorldFiles.worldDir(), "resources/randareas"), "fixg14.xml");
F.getParentFile().mkdirs();
java.nio.file.Files.write(F.toPath(), new java.lang.String('<AREADATA><AREA id="fixg14_area" layout="TREE" requires="areaname=$,areasize=int">'
	+ '<NAME>$areaname</NAME><SIZE>$areasize</SIZE><CLASS>StdArea</CLASS><ROOM select="first">'
	+ '<ROOM class="StdRoom"><TITLE>A shifting passage</TITLE><DESCRIPTION select="any-1"><DESCRIPTION>Cold stone $roomtag_index.</DESCRIPTION>'
	+ '<DESCRIPTION>Damp stone $roomtag_index.</DESCRIPTION><DESCRIPTION>Dusty stone $roomtag_index.</DESCRIPTION><DESCRIPTION>Narrow stone $roomtag_index.</DESCRIPTION>'
	+ '<DESCRIPTION>Low stone $roomtag_index.</DESCRIPTION><DESCRIPTION>Wet stone $roomtag_index.</DESCRIPTION></DESCRIPTION><EXIT class="Open" /></ROOM>'
	+ '</ROOM></AREA></AREADATA>').getBytes("UTF-8"));
var here = M.location();
function cmd(C, words) { var L = new java.util.ArrayList(); words.forEach(function (w) { L.add(w); }); CMClass.getCommand(C).execute(M, L, 0); }
function rooms(A) { var o = {}; for (var r = A.getProperMap(); r.hasMoreElements();) { var R = r.nextElement(); o[s(R.roomID())] = R; } return o; }
function snap(A, but) { var o = []; var m = rooms(A); for (var k in m) if (k != but) o.push(k + "|" + s(m[k].description())); o.sort(); return o.join("\n"); }
function allNew(before, now, but) { for (var k in now) if (k != but && now[k] === before[k]) return false; return true; }
function noneNew(before, now) { for (var k in now) if (now[k] !== before[k]) return false; return true; }
var A = null, m = null;
try {
	cmd("Generate", ["GENERATE", "NOSAVE", "AREA", "fixg14_area", "FROM", "randareas/fixg14.xml", "areaname=Fixg14Maze", "areasize=5", "SEED=3", "UNLINKED"]);
	A = CMLib.map().getArea("Fixg14Maze");
	CMLib.threads().deleteTick(A, -1);
	var B = CMClass.getBehavior("Regenerate");
	B.setParms("WHEN=SCRIPT SEED=SAME KEEP=Fixg14Maze#2");
	A.addBehavior(B);
	clearVar("G14GEN");
	var SA = scriptOn(A, "GENERATED_PROG\nmpsetvar FIXTEST G14GEN $G\n~\n");
	// MPREGENERATE from outside: now, the KEEP room kept, the seed asked for, GENERATED_PROG after
	var before = rooms(A), K = before["Fixg14Maze#2"];
	m = newMob("GenMob", here); m.setName("Fixg14caller");
	scriptOn(m, "RAND_PROG 100\nMPREGENERATE Fixg14Maze SEED=7\n~\n");
	tickMob(m);
	runQueued(SA);
	var now = rooms(A);
	check("script", K != null && now["Fixg14Maze#2"] === K && allNew(before, now, "Fixg14Maze#2") && GS.get(A).seed == 7 && gvar("G14GEN") == "fixg14_area",
		"kept " + (now["Fixg14Maze#2"] === K) + " new " + allNew(before, now, "Fixg14Maze#2") + " seed " + GS.get(A).seed + " GENERATED_PROG " + gvar("G14GEN"));
	// a player inside: it waits, and goes once they have left
	var at = rooms(A)["Fixg14Maze#0"];
	at.bringMobHere(M, false);
	before = rooms(A);
	tickMob(m);
	var waiting = noneNew(before, rooms(A)) && s(B.getStat("PENDING")) == "true";
	here.bringMobHere(M, false);
	B.tick(A, Tickable.TICKID_AREA);
	check("wait", waiting && allNew(before, rooms(A), "Fixg14Maze#2") && s(B.getStat("PENDING")) == "false", "waiting " + waiting + " " + B.getStat("LASTREPORT"));
	// SEED=DAILY: the same build all day
	B.setParms("WHEN=SCRIPT SEED=DAILY KEEP=Fixg14Maze#2");
	B.trigger(A, null);
	var d1 = snap(A, "Fixg14Maze#2"), s1 = GS.get(A).seed;
	B.trigger(A, null);
	check("daily", s1 == GS.get(A).seed && d1 == snap(A, "Fixg14Maze#2"), s1 + " / " + GS.get(A).seed);
	// WHEN=ONEMPTY: when the party leaves
	B.setParms("WHEN=ONEMPTY KEEP=Fixg14Maze#2");
	rooms(A)["Fixg14Maze#0"].bringMobHere(M, false);
	B.tick(A, Tickable.TICKID_AREA);
	before = rooms(A);
	here.bringMobHere(M, false);
	B.tick(A, Tickable.TICKID_AREA);
	check("onempty", allNew(before, rooms(A), "Fixg14Maze#2"), B.getStat("LASTREPORT"));
	// WHEN=MUDDAY:1: a day later
	B.setParms("WHEN=MUDDAY:1 KEEP=Fixg14Maze#2");
	B.setStat("STATE", "");
	B.tick(A, Tickable.TICKID_AREA);
	before = rooms(A);
	B.tick(A, Tickable.TICKID_AREA);
	var still = noneNew(before, rooms(A));
	A.getTimeObj().tickTock(A.getTimeObj().getHoursInDay());
	B.tick(A, Tickable.TICKID_AREA);
	check("mudday", still && allNew(before, rooms(A), "Fixg14Maze#2"), "still " + still + " " + B.getStat("LASTREPORT"));
	// its state for a save
	rooms(A)["Fixg14Maze#0"].bringMobHere(M, false);
	B.setParms("WHEN=SCRIPT KEEP=Fixg14Maze#2");
	B.trigger(A, "SAME");
	var st = s(B.getStat("STATE"));
	var B2 = CMClass.getBehavior("Regenerate");
	B2.setParms("WHEN=SCRIPT");
	B2.setStat("STATE", st);
	here.bringMobHere(M, false);
	check("state", has(st, "<REGENERATE") && has(st, "PENDING=\"TRUE\"") && has(st, "SEED=\"SAME\"") && s(B2.getStat("PENDING")) == "true", st);
	B.tick(A, Tickable.TICKID_AREA);
	check("help", has(CMLib.help().getHelpText("REGENERATE", M, true), "Regenerate behaviour")
		&& has(CMLib.help().getHelpText("BEHAVIOR_REGENERATE", M, true), "ONEMPTY") && has(CMLib.help().getHelpText("BEHAVIOR_SCRIPTABLE", M, true), "MPREGENERATE"), "no help");
} finally {
	here.bringMobHere(M, false);
	if (m != null) m.destroy();
	if (A != null) CMLib.map().obliterateMapArea(A);
	if (F.exists()) F["delete"]();
}
done();
