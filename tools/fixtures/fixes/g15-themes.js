// G15: seven new themes (crypt, ruins, castle, volcano, sky, mine, sewer), the set pieces in inc_setpieces.xml, the theme skeleton,
// random_dungeon rolling them, and GENERATE CHECK finding nothing wrong in them.
FIXFILE = "g15-themes";
var D = CM.core.Directions;
function s(x) { return "" + x; }
function has(t, p) { return s(t).replace(/\s+/g, " ").indexOf(p) >= 0; }
var THEMES = { crypt: "a burial corridor|a passage lined with niches|a crumbling ossuary walk|a mausoleum chamber|a vaulted crypt|a chapel of bones|a sealed tomb|a forgotten burial niche|an embalmer",
	ruins: "a crumbled colonnade|a rubble-strewn avenue|a toppled gateway|a roofless temple hall|a collapsed courtyard|a ruined bathhouse|a buried shrine|a looted treasury|a sunken crypt",
	castle: "a draughty corridor|a gallery of faded tapestries|a stair landing|a great hall|a guard room|a ruined armoury|bedchamber|a dusty chapel|a locked solar",
	volcano: "a cracked lava tube|a ledge above a magma river|a sulphurous fissure|a glowing cavern|forge hall|a hall of obsidian|an obsidian grotto|a smoking vent|a crystal geode",
	sky: "a windswept rope bridge|a path along a floating isle|a cloud-wreathed stair|a storm temple court|an eyrie platform|a sky garden|a floating crag|a harpy nest|a wind shrine",
	mine: "a timbered shaft|a rail-cart tunnel|a narrow seam|a vaulted dig chamber|an ore-sorting cavern|camp|a played-out gallery|a flooded sump|a collapsed drift",
	sewer: "a sewer channel|a slime-slick walkway|a low culvert|an overflow cistern|a junction chamber|a vaulted pump room|a blocked outflow|nest|cache" };
var SET = ["a riddle chamber", "a room with a lever", "a healing shrine", "a narrow corridor", "a collapsed passage"];
function cmd(C, words) { var L = new java.util.ArrayList(); words.forEach(function (w) { L.add(w); }); CMClass.getCommand(C).execute(M, L, 0); }
function rooms(A) { var o = []; for (var r = A.getProperMap(); r.hasMoreElements();) o.push(r.nextElement()); return o; }
function mobs(A) { var n = 0; rooms(A).forEach(function (R) { for (var i = 0; i < R.numInhabitants(); i++) if (R.fetchInhabitant(i).isMonster()) n++; }); return n; }
var made = [];
function gen(id, file, name, theme, seed) {
	cmd("Generate", ["GENERATE", "NOSAVE", "AREA", id, "FROM", "randareas/" + file, "areaname=" + name, "areasize=14", "level_range=5", "aggrochance=0", "theme=" + theme, "SEED=" + seed, "UNLINKED"]);
	var A = CMLib.map().getArea(name); made.push(A); return A;
}
try {
	var bad = [], setSeen = {}, riddle = null;
	for (var t in THEMES) {
		[1, 2].forEach(function (seed) {
			var A = gen(t + "_dungeon", t + ".xml", "Fixg15" + t + seed, t, seed);
			if (A == null) { bad.push(t + " " + seed + ": not built"); return; }
			var titles = rooms(A).map(function (R) { return s(R.displayText()); });
			var themed = titles.filter(function (x) { return THEMES[t].split("|").some(function (p) { return x.indexOf(p) >= 0; }); }).length;
			if (A.properSize() < 8 || themed < 3 || mobs(A) < 1) bad.push(t + " " + seed + ": rooms " + A.properSize() + " themed " + themed + " mobs " + mobs(A) + " " + titles.join(", "));
			titles.forEach(function (x) { SET.forEach(function (p) { if (x == p) setSeen[p] = true; }); });
			rooms(A).forEach(function (R) { if (s(R.displayText()) == "a riddle chamber" && riddle == null) riddle = R; });
		});
	}
	check("themes", bad.length == 0, bad.join("\n"));
	check("setpieces", Object.keys(setSeen).length >= 2, "seen: " + Object.keys(setSeen).join(", "));
	// GENERATE CHECK finds nothing wrong in them
	var problems = [];
	Object.keys(THEMES).concat(["inc_setpieces", "theme_skeleton"]).forEach(function (f) {
		var P = CM.core.GenerateCheck.check("randareas/" + f + ".xml");
		if (P.size() > 0) problems.push(f + ": " + P);
	});
	check("genchk", problems.length == 0, problems.join("\n"));
	// the riddle chest, built on its own, opens to its answer
	var here = M.location(), dir = -1;
	for (var d = 0; d < 4; d++) if (dir < 0 && here.getRoomInDir(d) == null && here.getRawExit(d) == null) dir = d;
	var VM = new java.util.HashMap(); // the tags a layout would give it: its one exit, back
	VM.put("ROOMTAG_NODEEXITS", "," + s(CMLib.directions().getDirectionChar(D.getOpDirectionCode(dir))).toLowerCase() + ",");
	VM.put("ROOMTAG_NODETYPE", "leaf");
	VM.put("LEVEL_RANGE", "5");
	var RR = CM.core.GenCalls.room("randareas/inc_setpieces.xml", "setpiece_riddle_room", here, dir, VM, M);
	var chest = (RR == null) ? null : RR.findItem("an iron-bound chest");
	var pw = (chest == null) ? null : chest.fetchEffect("Prop_OpenPassword");
	var answers = { "speak without a mouth": "echo", "more of me you take": "footsteps", "keys but open no locks": "piano", "roots nobody sees": "mountain", "Voiceless it cries": "wind" };
	var want = null;
	if (chest != null) for (var q in answers) if (has(chest.description(), q)) want = answers[q];
	var lockedBefore = chest != null && chest.isLocked() && !chest.isOpen();
	if (RR != null) { RR.bringMobHere(M, false); CMLib.commands().postSay(M, null, want == null ? "nothing" : want, false, false); }
	check("riddle", lockedBefore && want != null && pw != null && s(pw.text()) == want && chest.isOpen(),
		"chest " + chest + " stats " + (chest == null ? "" : ("haslock " + chest.getStat("HASLOCK") + " haslid " + chest.getStat("HASLID") + " defclosed " + chest.getStat("DEFCLOSED") + " deflocked " + chest.getStat("DEFLOCKED") + " door " + chest.hasADoor())) + " locked " + lockedBefore + " want " + want + " parms " + (pw == null ? "none" : pw.text()) + " open " + (chest == null ? "" : chest.isOpen()));
	here.bringMobHere(M, false);
	if (RR != null) { here.rawDoors()[dir] = null; here.setRawExit(dir, null); CMLib.map().obliterateMapRoom(RR); }
	// random_dungeon rolls a new theme; the skeleton builds
	cmd("Generate", ["GENERATE", "NOSAVE", "AREA", "random_dungeon", "FROM", "randareas/example.xml", "areaname=Fixg15Rnd", "areasize=12", "level_range=5", "theme=sewer", "SEED=3", "UNLINKED"]);
	var RA = CMLib.map().getArea("Fixg15Rnd"); made.push(RA);
	var sk = gen("skeleton_dungeon", "theme_skeleton.xml", "Fixg15Skel", "skeleton", 4);
	check("random", RA != null && rooms(RA).some(function (R) { return THEMES.sewer.split("|").some(function (p) { return s(R.displayText()).indexOf(p) >= 0; }); })
		&& sk != null && rooms(sk).some(function (R) { return has(R.displayText(), "skeleton"); }), "random " + (RA == null ? "none" : rooms(RA).map(function (R) { return s(R.displayText()); }).join(", ")));
	var guide = s(new CM.core.CMFile("guides/RandomAreas.html", null).text());
	check("guide", has(guide, "BRANCH") && has(guide, "SPOTTEDMESH") && has(guide, "with replacement") && has(guide, "theme_skeleton.xml"), "guide not fixed");
} finally {
	here = M.location();
	made.forEach(function (A) { if (A != null) CMLib.map().obliterateMapArea(A); });
}
done();
