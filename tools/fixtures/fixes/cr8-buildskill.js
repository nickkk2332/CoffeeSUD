// CR8: GenBuildingSkill (NAME, TRIGSTR, FILENAME, MATLIST, MAINRESOURCE, MAINMATERIAL, VERB, SOUND, CANSIT, HELP) and CREATE/MODIFY/DESTROY BUILDSKILL.
FIXFILE = "cr8-buildskill";
function s(x) { return "" + x; }
function has(t, p) { return s(t).indexOf(p) >= 0; }
function setField(obj, name, val) { for (var c = obj.getClass(); c != null; c = c.getSuperclass()) { try { var f = c.getDeclaredField(name); f.setAccessible(true); f.set(obj, val); return; } catch (e) { } } throw "no field " + name; }
var RF = new java.io.File(new java.io.File(WorldFiles.worldDir(), "resources/skills"), "fixcr8ice.txt");
RF.getParentFile().mkdirs();
java.nio.file.Files.write(RF.toPath(), new java.lang.String("title\t1\t15\t0\t\t\tTITLE\tNA\t-\t-\ttitle\t\tBrief 1-6 word description of a room.\n"
	+ "ice wall\t1\t25\t10\t\tDIR\tWALL\tNA\t-\t\tice wall @x1\t\tA wall of packed ice.\n").getBytes("UTF-8"));
var X = scratchRoom();
var V = newMob("GenMob", X);
V.setName("Fixcr8carver");
var VS = new JavaAdapter(CM.Common.FakeSession, { isFake: function() { return false; } });
VS.initialize(null, "MEMORY"); V.setSession(VS); V.setPlayerStats(CMClass.getCommon("DefaultPlayerStats")); VS.setMob(V);
var seen = 0;
function said() { var all = s(field(VS, "bout")); var o = all.substring(seen); seen = all.length; return o; }
function list() { var L = new java.util.ArrayList(); for (var i = 0; i < arguments.length; i++) L.add(arguments[i]); return L; }
var G = null;
try {
	G = CMClass.getAbility("GenBuildingSkill").copyOf();
	G.setStat("CLASS", "FixCr8Icecarving");
	G.setStat("NAME", "Fixcr8 Icecarving"); G.setStat("TRIGSTR", "fixicecarve"); G.setStat("FILENAME", "fixcr8ice.txt");
	G.setStat("MATLIST", "ROCK"); G.setStat("MAINRESOURCE", "Ice"); G.setStat("MAINMATERIAL", "stone"); G.setStat("VERB", "carving"); G.setStat("SOUND", "chisel.wav");
	var R = CMClass.getAbility("FixCr8Icecarving");
	check("made", R != null && R.isGeneric() && s(R.getStat("JAVACLASS")) == "GenBuildingSkill" && (R.classificationCode() & CM.Abilities.interfaces.Ability.ALL_DOMAINS) == CM.Abilities.interfaces.Ability.DOMAIN_BUILDINGSKILL
		&& s(R.triggerStrings()[0]) == "FIXICECARVE" && s(R.name()) == "Fixcr8 Icecarving", R == null ? "none" : R.getStat("JAVACLASS"));
	// kept as data: ALLXML read back as at boot
	var xml = s(G.getStat("ALLXML"));
	var G2 = CMClass.getAbility("GenBuildingSkill").copyOf(); G2.setStat("ALLXML", xml);
	check("data", has(xml, "<MAINRESOURCE>Ice</MAINRESOURCE>") && s(G2.ID()) == "FixCr8Icecarving" && s(G2.getStat("FILENAME")) == "fixcr8ice.txt" && s(G2.getStat("VERB")) == "carving" && s(G2.getStat("MATLIST")) == "ROCK", xml.substring(0, 200));
	// the skill: its recipe file, its main resource, its verb
	var A = R.copyOf(); A.setProficiency(100); V.addAbility(A); A = V.fetchAbility("FixCr8Icecarving");
	said(); A.invoke(V, list("list"), null, false, 0); var l = said();
	setField(A, "dir", java.lang.Integer.valueOf(0));
	var row = java.lang.reflect.Array.newInstance(java.lang.String, 13);
	["ice wall", "1", "25", "10", "", "DIR", "WALL", "NA", "-", "", "ice wall @x1", "", "A wall of packed ice."].forEach(function (c, i) { row[i] = c; });
	var verb = s(A.establishVerb(V, row));
	check("skill", has(l, "ice wall") && has(l, "Ice required") && verb.indexOf("carving the ice wall") == 0, l.replace(/\s+/g, " ").substring(0, 200) + " / " + verb);
	// MODIFY: a coded building skill points at BUILDSKILL; MODIFY BUILDSKILL refuses one that is not data
	var Mo = CMClass.getCommand("Modify"), Con = CMClass.getAbility("Construction");
	said(); var w = call(Mo, "wrongGenAbilityMessage", [V, Con]); var wm = said();
	said(); var b = Mo.buildSkills(V, list("MODIFY", "BUILDSKILL", "Construction")); var bm = said();
	check("modify", s(w) == "true" && has(wm, "Try MODIFY BUILDSKILL") && s(b) == "false" && has(bm, "not a data building skill") && s(CMLib.ableParms().getGenericClassID(Con)) == "GenBuildingSkill", wm + " / " + bm);
	// DESTROY BUILDSKILL
	V.delAbility(A);
	var gone = CMClass.getCommand("Destroy").abilities(V, list("DESTROY", "BUILDSKILL", "FixCr8Icecarving"));
	check("destroy", s(gone) == "true" && CMClass.getAbility("FixCr8Icecarving") == null, s(gone));
	G = null;
	check("help", has(CMLib.help().getHelpText("GENBUILDSKILL", M, true), "MAINMATERIAL"), "no help");
} finally {
	if ((G != null) && (CMClass.getAbility("FixCr8Icecarving") != null)) CMClass.delClass(CM.core.CMClass.CMObjectType.ABILITY, CMClass.getAbility("FixCr8Icecarving"));
	RF["delete"]();
	V.setPlayerStats(null); V.destroy();
}
done();
