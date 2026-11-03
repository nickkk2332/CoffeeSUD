// CR10: a MASK column in hunting.txt and fishing.txt (and the herb, flower and mushroom lists): a row is a choice only where the gatherer or their room passes it.
FIXFILE = "cr10-masks";
function s(x) { return "" + x; }
function has(t, p) { return s(t).indexOf(p) >= 0; }
var RM = CM.Items.interfaces.RawMaterial, GM = CM.core.GatherMasks;
var SK = new java.io.File(WorldFiles.worldDir(), "resources/skills"); SK.mkdirs();
var FF = new java.io.File(SK, "fishing.txt"), HF = new java.io.File(SK, "hunting.txt");
function write(F, text) { java.nio.file.Files.write(F.toPath(), new java.lang.String(text).getBytes("UTF-8")); }
function uncache() { CM.core.Resources.removeResource("PARSED_RECIPE: fishing.txt"); CM.core.Resources.removeResource("PARSED_RECIPE: hunting.txt"); }
var X = scratchRoom();
var V = newMob("GenMob", X);
V.setName("Fixcr10angler");
V.basePhyStats().setLevel(10); V.recoverPhyStats();
V.baseState().setMovement(5000); V.recoverMaxState(); V.resetToMaxState();
var VS = new JavaAdapter(CM.Common.FakeSession, { isFake: function() { return false; } });
VS.initialize(null, "MEMORY"); V.setSession(VS); VS.setMob(V);
V.setPlayerStats(CMClass.getCommon("DefaultPlayerStats"));
function learn(id) { var A = CMClass.getAbility(id); A.setProficiency(100); V.addAbility(A); return V.fetchAbility(id); }
var Fi = learn("Fishing"), Hu = learn("Hunting");
function attempt(A) { var C = new java.util.ArrayList(); A.invoke(V, C, null, false, 0); var E = V.fetchEffect(A.ID()); var f = (E == null) ? null : field(E, "found"); if (E != null) V.delEffect(E); return f; }
var finds = [];
try {
	write(FF, "#Resource\tOverride Frequency\tOverride Name\tOverride Value\tMASK\nSalmon\t10\tfixcr10 lantern eel\t12\t-LEVEL +>5\nSalmon\t1000\tfixcr10 mud carp\t1\t-LEVEL +>50\n");
	write(HF, "#Resource\t#Domain\tOverride Frequency\tMOB\tMASK\nLEATHER\t*\t1000\tDoe\t-LEVEL +>50\nLEATHER\t*\t1\tCow\t-LEVEL +>5\n");
	uncache();
	// fishing: the carp's mask fails, so every catch is an eel, at its own value
	X.setResource(RM.RESOURCE_SALMON);
	var eels = 0, other = "";
	for (var i = 0; i < 10; i++) { var f = attempt(Fi); if (f != null) { finds.push(f); if (has(f.Name(), "lantern eel") && f.baseGoldValue() == 12) eels++; else other = f.Name() + "/" + f.baseGoldValue(); } }
	check("fishing", eels >= 8 && other == "", eels + " " + other);
	// hunting: the doe's mask fails, so it is a cow (an animal is found in a room next door)
	var Y = scratchRoom(); X.rawDoors()[0] = Y; X.setRawExit(0, CMClass.getExit("Open"));
	X.setResource(RM.RESOURCE_LEATHER);
	var cows = 0, beast = "";
	for (var i = 0; i < 5; i++) { var m = attempt(Hu); if (m != null) { finds.push(m); if (has(s(m.Name()).toLowerCase(), "cow")) cows++; else beast = m.Name(); } }
	check("hunting", cows >= 3 && beast == "", cows + " " + beast);
	// the lists: names with masks, and a weighted pick among rows that pass
	var lines = new java.util.ArrayList(); lines.add("fixcr10 snowdrop\t-LEVEL +>50"); lines.add("fixcr10 rose"); lines.add("fixcr10 frostbloom\t-LEVEL +>5"); lines.add("");
	var names = GM.names(lines, V);
	check("names", names.size() == 2 && s(names.get(0)) == "fixcr10 rose" && s(names.get(1)) == "fixcr10 frostbloom", s(names));
	var rows = new java.util.ArrayList();
	function row(a) { var L = new java.util.ArrayList(); a.forEach(function (x) { L.add(x); }); rows.add(L); }
	row(["fixcr10 ghostcap", "50", "4", "", "-LEVEL +>50"]); row(["fixcr10 inkcap", "1", "1", ""]);
	var picks = {}; for (var i = 0; i < 30; i++) { var p = GM.pick(rows, 1, 4, V); picks[p == null ? "none" : s(p.get(0))] = 1; }
	check("pick", GM.anyMask(rows, 4) && picks["fixcr10 inkcap"] == 1 && picks["fixcr10 ghostcap"] == null && picks.none == null, JSON.stringify(picks));
	// a room key: the hour where the angler is
	var hr = X.getArea().getTimeObj().getHourOfDay(), other = (hr + 3) % X.getArea().getTimeObj().getHoursInDay();
	check("room", GM.passes("-HOURS +" + hr, V) && !GM.passes("-HOURS +" + other, V) && GM.passes("", V), hr + " / " + other);
	// the stock lists, without masks, still read whole
	var flowers = CM.core.Resources.getFileLineVector(CM.core.Resources.getFileResource("skills/floristry.txt", true));
	var stock = 0; for (var i = 0; i < flowers.size(); i++) if (s(flowers.get(i)).trim().length > 0) stock++;
	check("stock", GM.names(flowers, V).size() == stock && stock > 2, GM.names(flowers, V).size() + " / " + stock);
	check("help", has(CMLib.help().getHelpText("GATHERTABLES", M, true), "mycology.txt"), "no help");
} finally {
	finds.forEach(function (I) { I.destroy(); });
	FF["delete"](); HF["delete"](); uncache();
	if (X.rawDoors()[0] != null) { X.rawDoors()[0] = null; X.setRawExit(0, null); }
	V.setPlayerStats(null); V.destroy();
}
done();
