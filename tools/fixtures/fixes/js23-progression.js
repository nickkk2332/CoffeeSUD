// JS23: world/scripts/progression.js: xpGain, practicesForLevel, trainsForLevel, onLevel, onLearn,
// onTrain, onCreated, onCreationStep and statRoll (STARTSTAT=SCRIPT).
FIXFILE = "js23-progression";
var JP = CM.core.JSProgression;
var R = M.location();
function s(x) { return "" + x; }
function write(f, text) { f.getParentFile().mkdirs(); java.nio.file.Files.write(f.toPath(), new java.lang.String(text).getBytes("UTF-8")); }
var PF = JP.file();
var had = PF.isFile() ? s(new java.lang.String(java.nio.file.Files.readAllBytes(PF.toPath()), "UTF-8")) : null;
["FIXJS23L", "FIXJS23K", "FIXJS23T", "FIXJS23C", "FIXJS23S", "FIXJS23P"].forEach(clearVar);

// no file: everything as usual
if (PF.isFile()) PF["delete"]();
var hero = newMob("GenMob", R);
hero.setName("a fixjs23 hero");
hero.baseCharStats().setCurrentClass(CMClass.getCharClass("Fighter"));
hero.recoverCharStats();
check("no-file", !JP.has("xpGain") && JP.xpGain(hero, "COMBAT:5", 100) == 100, "xpGain without the file");

write(PF, [
	"function mine(mob) { return ('' + mob.Name()).indexOf('fixjs23') >= 0; }",
	"function xpGain(mob, source, amount) {",
	"  if (!mine(mob)) return amount;",
	"  if (source.indexOf('COMBAT:') == 0) return 0;",
	"  if (source.indexOf('MISC:') == 0) return amount * 1.5;",
	"}",
	"function practicesForLevel(mob) { if (mine(mob)) { cm.var('FIXTEST', 'FIXJS23P', '' + mob.getPractices()); return 7; } }",
	"function trainsForLevel(mob) { if (mine(mob)) return (mob.basePhyStats().level() % 5 == 0) ? 2 : 3; }",
	"function onLevel(mob, level) { cm.var('FIXTEST', 'FIXJS23L', mob.Name() + ' ' + level + ' ' + mob.getPractices()); }",
	"function onLearn(mob, id) { cm.var('FIXTEST', 'FIXJS23K', mob.Name() + ' ' + id); }",
	"function onTrain(mob, what, amount) { cm.var('FIXTEST', 'FIXJS23T', mob.Name() + ' ' + what + ' ' + amount); }",
	"function onCreated(mob) { cm.var('FIXTEST', 'FIXJS23C', 'made ' + mob.Name()); }",
	"function onCreationStep(mob, step, answer) { cm.var('FIXTEST', 'FIXJS23S', step + ': ' + answer); }",
	"function statRoll(mob, bonus) { return { strength: 15, DEX: 14, constitution: 13 + bonus }; }",
	""].join("\n"));
check("defined", s(JP.defined()) == "[onCreated, onCreationStep, xpGain, onLevel, practicesForLevel, trainsForLevel, onLearn, onTrain, statRoll]", s(JP.defined()));

// xpGain, through postExperience
var L = CMLib.leveler();
var got = L.postExperience(hero, "COMBAT:5", null, "", 100, true);
check("xpGain-zero", got == 0, "COMBAT gave " + got);
got = L.postExperience(hero, "MISC:", null, "", 100, true);
check("xpGain-more", got == 150, "MISC gave " + got);
got = L.postExperience(hero, "ABILITY:Skill_Track", null, "", 100, true);
check("xpGain-undefined", got == 100, "no number gave " + got);
var other = newMob("GenMob", R);
other.setName("a bystander");
other.baseCharStats().setCurrentClass(CMClass.getCharClass("Fighter"));
other.recoverCharStats();
got = L.postExperience(other, "COMBAT:5", null, "", 100, true);
check("xpGain-others", got == 100, "a bystander got " + got);
check("xpGain-loss", JP.xpGain(hero, "DEATH:", -40) == -40, "a loss is not passed");

// practices, trains and onLevel, through level()
var p0 = hero.getPractices(), t0 = hero.getTrains();
L.level(hero);
var lv = hero.basePhyStats().level();
// what the level adds besides (an achievement's, say) is not practicesForLevel's: count from where it was asked
var before = parseInt(gvar("FIXJS23P"));
check("practicesForLevel", gvar("FIXJS23P") != "" && parseInt(s(gvar("FIXJS23L")).split(" ").pop()) - before == 7, "practices " + p0 + " > " + hero.getPractices() + ", asked at " + gvar("FIXJS23P") + ", onLevel saw " + gvar("FIXJS23L"));
check("trainsForLevel", hero.getTrains() - t0 == ((lv % 5 == 0) ? 2 : 3), "trains " + t0 + " > " + hero.getTrains() + " at " + lv);
check("onLevel", gvar("FIXJS23L") == "a fixjs23 hero " + lv + " " + hero.getPractices(), gvar("FIXJS23L"));
var q0 = other.getPractices(), u0 = other.getTrains();
L.level(other);
check("perLevel-others", (other.getPractices() - q0 != 7) && (other.getTrains() - u0 == 1), "bystander practices +" + (other.getPractices() - q0) + ", trains +" + (other.getTrains() - u0));

// onLearn: a character's skill, once in the world
var ps = CMClass.getCommon("DefaultPlayerStats");
hero.setPlayerStats(ps);
var A = CMClass.getAbility("Skill_Track");
hero.addAbility(A);
check("onLearn", gvar("FIXJS23K") == "a fixjs23 hero Skill_Track", gvar("FIXJS23K"));

// onTrain, through TRAIN (no session, so no prompt)
var teacher = newMob("GenMob", R);
teacher.setName("a fixjs23teacher");
teacher.setAttribute(CM.MOBS.interfaces.MOB.Attrib.NOTEACH, false); // a new mob will not teach, or be taught
hero.setAttribute(CM.MOBS.interfaces.MOB.Attrib.NOTEACH, false);
hero.setTrains(10);
var HS = CMClass.getCommon("FakeSession");
HS.initialize(null, "MEMORY");
hero.setSession(HS);
HS.setMob(hero);
var cmds = new java.util.ArrayList(); cmds.add("TRAIN"); cmds.add("MANA"); cmds.add("fixjs23teacher"); cmds.add("noprompt");
var mana0 = hero.baseState().getMana();
CMClass.getCommand("Train").execute(hero, cmds, 0);
var gain = hero.baseState().getMana() - mana0;
check("onTrain", (gain > 0) && gvar("FIXJS23T") == "a fixjs23 hero MANA " + gain, "mana +" + gain + ", saw " + gvar("FIXJS23T") + ", told " + field(HS, "bout"));
hero.setSession(null);

// onCreated, as a new character enters the world
CM.core.save.SaveGame.characterCreated(hero);
check("onCreated", gvar("FIXJS23C") == "made a fixjs23 hero", gvar("FIXJS23C"));

// onCreationStep, from character creation's own steps
var CC = CMLib.login();
var LS = null;
for (var c = CC.getClass(); (c != null) && (LS == null); c = c.getSuperclass()) {
	var inner = c.getDeclaredClasses();
	for (var i = 0; i < inner.length; i++) if (s(inner[i].getSimpleName()) == "LoginSessionImpl") LS = inner[i];
}
var login = null;
if (LS != null) {
	var KS = LS.getDeclaredConstructors();
	for (var k = 0; k < KS.length; k++) {
		KS[k].setAccessible(true);
		if (KS[k].getParameterCount() == 1) login = KS[k].newInstance([CC]);
		else if ((login == null) && (KS[k].getParameterCount() == 2)) login = KS[k].newInstance([CC, null]);
	}
	if (login != null) { var mf = LS.getDeclaredField("mob"); mf.setAccessible(true); mf.set(login, hero); }
}
check("login-object", login != null, "no LoginSessionImpl");
if (login != null) {
	call(CC, "charcrRaceDone", [login, null]);
	check("onCreationStep-race", gvar("FIXJS23S") == "race: " + hero.baseCharStats().getMyRace().name(), gvar("FIXJS23S"));
	JP.creationStep(hero, "class");
	check("onCreationStep-class", gvar("FIXJS23S") == "class: Fighter", gvar("FIXJS23S"));

	// statRoll, for STARTSTAT=SCRIPT
	var Int = CM.core.CMProps.Int;
	var oldStart = CMProps.getIntVar(Int.STARTSTAT);
	var oldStr = CMProps.instance().getProperty("STARTSTAT");
	CMProps.instance().setProperty("STARTSTAT", "SCRIPT");
	CMProps.setIntVar(Int.STARTSTAT, 0);
	var CS = CM.Common.interfaces.CharStats;
	check("scriptedStats", JP.scriptedStats(), "STARTSTAT=SCRIPT and statRoll");
	call(CC, "charcrStatInit", [login, null, java.lang.Integer.valueOf(2)]);
	var b = hero.baseCharStats();
	var st = field(login, "state");
	check("statRoll", b.getStat(CS.STAT_STRENGTH) == 15 && b.getStat(CS.STAT_DEXTERITY) == 14 && b.getStat(CS.STAT_CONSTITUTION) == 15
		&& b.getStat(CS.STAT_WISDOM) == CMProps.getIntVar(Int.BASEMINSTAT) && s(st) == "CHARCR_STATDONE",
		"str " + b.getStat(CS.STAT_STRENGTH) + " dex " + b.getStat(CS.STAT_DEXTERITY) + " con " + b.getStat(CS.STAT_CONSTITUTION) + " wis " + b.getStat(CS.STAT_WISDOM) + ", " + st);
	JP.creationStep(hero, "stats");
	check("onCreationStep-stats", s(gvar("FIXJS23S")).indexOf("stats: ") == 0 && s(gvar("FIXJS23S")).indexOf(" 15") > 0, gvar("FIXJS23S"));
	if (oldStr == null) CMProps.instance().remove("STARTSTAT"); else CMProps.instance().setProperty("STARTSTAT", oldStr);
	CMProps.setIntVar(Int.STARTSTAT, oldStart);
	check("scriptedStats-off", !JP.scriptedStats(), "STARTSTAT back to " + oldStr);
}

// an error is told, and the usual kept
write(PF, "function xpGain(mob, source, amount) { throw 'fixjs23 broken'; }\n// changed\n");
check("error-usual", JP.xpGain(hero, "MISC:", 100) == 100, "a throwing xpGain");

PF["delete"]();
if (had != null) write(PF, had);
check("gone", (had != null) || !JP.has("xpGain"), "after the file is gone");
hero.setPlayerStats(null);
hero.destroy(); other.destroy(); teacher.destroy();
done();
