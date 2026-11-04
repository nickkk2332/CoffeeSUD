// P21: SCRIPT on GenPoison (ONAFFECT, ONTICK, ONCURE), GenCraftSkill (ONCRAFTED, ONFAILED), GenGatheringSkill (ONFOUND, ONFAILED), and GenRace and GenCharClass (run on each mob, never saved).
FIXFILE = "p21-scripts";
function s(x) { return "" + x; }
function has(t, p) { return s(t).indexOf(p) >= 0; }
function setField(obj, name, val) { for (var c = obj.getClass(); c != null; c = c.getSuperclass()) { try { var f = c.getDeclaredField(name); f.setAccessible(true); f.set(obj, val); return; } catch (e) { } } throw "no field " + name; }
var CS = CM.Common.interfaces.CharStats;
var AB = CM.core.CMClass.CMObjectType.ABILITY;
var R = scratchRoom();
var made = [], ables = [];
function npc(name) {
	var m = CMClass.getMOB("GenMob"); m.setName(name); m.basePhyStats().setLevel(10);
	m.baseCharStats().setStat(CS.STAT_SAVE_POISON, -200);
	m.recoverCharStats(); m.recoverPhyStats(); m.recoverMaxState(); m.resetToMaxState();
	m.bringToLife(R, true); made.push(m); return m;
}
function fn(name, v) { return "FUNCTION_PROG " + name + "\nmpsetvar FIXTEST " + v + " $o\n~\n"; }
["P21AFF", "P21TICK", "P21CURE", "P21MADE", "P21RUIN", "P21FOUND", "P21NONE", "P21RACE", "P21CLASS"].forEach(clearVar);
try {
	// a poison's script
	var P = CMClass.getAbility("GenPoison").copyOf(); ables.push(P);
	P.setStat("CLASS", "FixP21Venom"); P.setStat("NAME", "Fixvenom"); P.setStat("TICKS", "20"); P.setStat("DELAY", "1"); P.setStat("DAMAGE", "0");
	P.setStat("SCRIPT", "FUNCTION_PROG ONAFFECT\nmpsetvar FIXTEST P21AFF yes\n~\nFUNCTION_PROG ONTICK\nmpsetvar FIXTEST P21TICK yes\n~\nFUNCTION_PROG ONCURE\nmpsetvar FIXTEST P21CURE yes\n~\n");
	var P2 = CMClass.getAbility("GenPoison").copyOf(); P2.setStat("ALLXML", P.getStat("ALLXML"));
	check("poison xml", has(P2.getStat("SCRIPT"), "ONCURE"), s(P2.getStat("SCRIPT")).length);
	var victim = npc("Fixp21 victim"), biter = npc("Fixp21 spider");
	// Exercise the lifecycle hooks directly: normal poison application always
	// retains a minimum saving throw, even with a very low poison save.
	P.startTickDown(biter, victim, 20);
	var V = victim.fetchEffect("FixP21Venom");
	check("onaffect", V != null && gvar("P21AFF") == "yes", s(V) + " " + gvar("P21AFF"));
	if (V != null) for (var k = 0; k < 4; k++) V.tick(victim, Tickable.TICKID_MOB); // it first acts on its third tick
	check("ontick", gvar("P21TICK") == "yes", gvar("P21TICK"));
	if (V != null) V.unInvoke();
	check("oncure", gvar("P21CURE") == "yes" && victim.fetchEffect("FixP21Venom") == null, gvar("P21CURE"));
	// a crafting skill's: ONCRAFTED with $o the new thing, and ONFAILED
	var smith = npc("Fixp21 smith");
	var C = CMClass.getAbility("GenCraftSkill").copyOf(); ables.push(C);
	C.setStat("CLASS", "FixP21Carving"); C.setStat("NAME", "Fixcarving");
	C.setStat("SCRIPT", fn("ONCRAFTED", "P21MADE") + fn("ONFAILED", "P21RUIN"));
	var C2 = CMClass.getAbility("GenCraftSkill").copyOf(); C2.setStat("ALLXML", C.getStat("ALLXML"));
	check("craft xml", has(C2.getStat("SCRIPT"), "ONCRAFTED"), s(C2.getStat("SCRIPT")).length);
	var job = C.copyOf();
	var bowl = CMClass.getItem("GenItem"); bowl.setName("a fixp21 bowl");
	setField(job, "buildingI", bowl); job.setAffectedOne(smith); job.setInvoker(smith);
	job.unInvoke();
	check("oncrafted", has(gvar("P21MADE"), "fixp21 bowl") && R.isContent(bowl), gvar("P21MADE"));
	var job2 = C.copyOf();
	var cup = CMClass.getItem("GenItem"); cup.setName("a fixp21 cup");
	setField(job2, "buildingI", cup); setField(job2, "messedUp", java.lang.Boolean.TRUE); job2.setAffectedOne(smith); job2.setInvoker(smith);
	job2.unInvoke();
	check("onfailed", has(gvar("P21RUIN"), "fixp21 cup"), gvar("P21RUIN"));
	// a gathering skill's: ONFOUND with $o what was found, ONFAILED when nothing was
	var G = CMClass.getAbility("GenGatheringSkill").copyOf(); ables.push(G);
	G.setStat("CLASS", "FixP21Mining"); G.setStat("NAME", "Fixmining"); G.setStat("ISCOSMETIC", "true");
	G.setStat("SCRIPT", fn("ONFOUND", "P21FOUND") + "FUNCTION_PROG ONFAILED\nmpsetvar FIXTEST P21NONE yes\n~\n");
	var dig = G.copyOf();
	var ember = CMClass.getItem("GenItem"); ember.setName("an fixp21 ember");
	setField(dig, "found", ember); setField(dig, "foundShortName", "an fixp21 ember"); dig.setAffectedOne(smith); dig.setInvoker(smith);
	dig.unInvoke();
	check("onfound", has(gvar("P21FOUND"), "fixp21 ember"), gvar("P21FOUND"));
	var dig2 = G.copyOf(); dig2.setAffectedOne(smith); dig2.setInvoker(smith);
	dig2.unInvoke();
	check("nothing found", gvar("P21NONE") == "yes", gvar("P21NONE"));
	// a race's and a class's: run on each mob of them, never on its own list
	var GR = CMClass.getRace("GenRace").copyOf(); GR.setStat("ID", "FixP21Kin"); GR.setStat("NAME", "Fixkin");
	GR.setStat("SCRIPT", "RAND_PROG 100\nmpsetvar FIXTEST P21RACE $i\n~\n");
	var GR2 = CMClass.getRace("GenRace").copyOf(); GR2.setRacialParms(GR.racialParms());
	check("race xml", has(GR2.getStat("SCRIPT"), "RAND_PROG"), s(GR2.getStat("SCRIPT")).length);
	var GK = CMClass.getCharClass("GenCharClass").copyOf(); GK.setStat("ID", "FixP21Order");
	GK.setStat("SCRIPT", "RAND_PROG 100\nmpsetvar FIXTEST P21CLASS $i\n~\n");
	var GK2 = CMClass.getCharClass("GenCharClass").copyOf(); GK2.setClassParms(GK.classParms());
	check("class xml", has(GK2.getStat("SCRIPT"), "RAND_PROG"), s(GK2.getStat("SCRIPT")).length);
	var kin = npc("Fixp21 kinsman");
	kin.baseCharStats().setMyRace(GR); kin.baseCharStats().setCurrentClass(GK); kin.recoverCharStats();
	var n0 = kin.numScripts(), e0 = kin.numEffects();
	tickMob(kin);
	check("race script", has(gvar("P21RACE"), "kinsman"), gvar("P21RACE"));
	check("class script", has(gvar("P21CLASS"), "kinsman"), gvar("P21CLASS"));
	check("not saved", kin.numScripts() == n0 && kin.numEffects() == e0, kin.numScripts() + " " + kin.numEffects());
	check("help", has(CMLib.help().getHelpText("SKILLSCRIPTS", M, true), "ONCRAFTED"), "no help");
} finally {
	made.forEach(function (m) { if (!m.amDestroyed()) m.destroy(); });
	ables.forEach(function (a) { CMClass.delClass(AB, a); });
}
done();
