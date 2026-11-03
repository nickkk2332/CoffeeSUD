// P23: purchases recorded (skills taught and practised, stats trained), Prop_Respec with RESPEC (WHAT, COST, ONCE, MASK), and MPRESPEC.
FIXFILE = "p23-respec";
function s(x) { return "" + x; }
function has(t, p) { return s(t).indexOf(p) >= 0; }
function list() { var L = new java.util.ArrayList(); for (var i = 0; i < arguments.length; i++) L.add(arguments[i]); return L; }
var PU = CM.core.Purchases;
var MA = CM.MOBS.interfaces.MOB.Attrib;
var CS = CM.Common.interfaces.CharStats;
var R = scratchRoom();
var made = [];
function npc(name) { var m = CMClass.getMOB("GenMob"); m.setName(name); m.basePhyStats().setLevel(30); m.recoverPhyStats(); m.bringToLife(R, true); m.setAttribute(MA.NOTEACH, false); made.push(m); return m; }
try {
	var teacher = npc("Fixp23 sage");
	teacher.baseCharStats().setStat(CS.STAT_WISDOM, 25); teacher.recoverCharStats();
	var trainer = npc("Fixp23 drillmaster");
	var PT = CMClass.getAbility("Prop_Trainer"); PT.setMiscText("STR=25 INT=25 DEX=25 CON=25 CHA=25 WIS=25"); trainer.addNonUninvokableEffect(PT); tickMob(trainer); // Prop_Trainer sets the stats on its tick
	var St = CMClass.getMOB("GenMob"); St.setName("Fixp23pupil"); St.setPlayerStats(CMClass.getCommon("DefaultPlayerStats"));
	St.baseCharStats().setMyRace(CMClass.getRace("Human"));
	var F = CMClass.getCharClass("Fighter"); St.baseCharStats().setCurrentClass(F); St.baseCharStats().setClassLevel(F, 10);
	St.basePhyStats().setLevel(10); St.baseCharStats().setStat(CS.STAT_INTELLIGENCE, 18); St.baseCharStats().setStat(CS.STAT_STRENGTH, 12);
	St.recoverCharStats(); St.recoverPhyStats(); St.bringToLife(R, true); made.push(St);
	St.setAttribute(MA.NOTEACH, false); St.setTrains(10); St.setPractices(20);
	var VS = new JavaAdapter(CM.Common.FakeSession, { isFake: function() { return false; } });
	VS.initialize(null, "MEMORY"); St.setSession(VS); VS.setMob(St);
	var seen = 0;
	function said() { var all = s(field(VS, "bout")); var o = all.substring(seen); seen = all.length; return o.replace(/\x1b\[[0-9;]*m/g, "").replace(/\^./g, "").replace(/\s+/g, " "); }
	// a skill taught, then practised
	var L = CMClass.getAbility("Spell_Light"); L.setProficiency(100); teacher.addAbility(L);
	var t0 = St.getTrains(), p0 = St.getPractices();
	L.teach(teacher, St);
	var mine = St.fetchAbility("Spell_Light");
	if (mine != null) mine.practice(teacher, St);
	var buys = PU.list(St);
	var skillT = 0, skillP = 0;
	for (var i = 0; i < buys.size(); i++) if (s(buys.get(i).kind) == "SKILL") { skillT += buys.get(i).trains; skillP += buys.get(i).practices; }
	check("recorded", mine != null && buys.size() >= 2 && (skillT + skillP) > 0 && skillT == t0 - St.getTrains() && skillP == p0 - St.getPractices(),
		buys.size() + " " + skillT + "/" + (t0 - St.getTrains()) + " " + skillP + "/" + (p0 - St.getPractices()));
	check("hidden and saved", St.fetchEffect("Prop_Purchases") != null && s(St.fetchEffect("Prop_Purchases").displayText()) == "" && St.fetchEffect("Prop_Purchases").isSavable(), "not kept");
	// a stat trained
	var str0 = St.baseCharStats().getStat(CS.STAT_STRENGTH), tr1 = St.getTrains();
	teacher.setAttribute(MA.NOTEACH, true); // the drillmaster trains, not the sage
	said(); CMClass.getCommand("Train").execute(St, list("TRAIN", "STR", "NOPROMPT"), 0);
	teacher.setAttribute(MA.NOTEACH, false);
	var th = said();
	check("trained", St.baseCharStats().getStat(CS.STAT_STRENGTH) == str0 + 1 && has(PU.show(St, "STATS"), "Strength"), St.baseCharStats().getStat(CS.STAT_STRENGTH) + " " + PU.show(St, "STATS") + " " + th);
	// RESPEC at an oracle: SKILLS and STATS, 5 a thing
	var oracle = npc("Fixp23 oracle");
	var RS = CMClass.getAbility("Prop_Respec"); RS.setMiscText('WHAT="SKILLS,STATS" COST="5 per skill"'); oracle.addNonUninvokableEffect(RS);
	CMLib.beanCounter().giveSomeoneMoney(St, 100);
	var RC = CMClass.getCommand("RespecCmd");
	said(); RC.execute(St, list("RESPEC"), 0); var h = said();
	check("list", has(h, "Light") && has(h, "Strength") && has(h, "RESPEC ALL"), h);
	var m0 = CMLib.beanCounter().getTotalAbsoluteNativeValue(St), tb = St.getTrains(), pb = St.getPractices();
	RC.execute(St, list("RESPEC", "light"), 0); h = said();
	check("one undone", St.fetchAbility("Spell_Light") == null && St.getTrains() == tb + skillT && St.getPractices() == pb + skillP
		&& Math.abs(m0 - CMLib.beanCounter().getTotalAbsoluteNativeValue(St) - 5) < 0.01 && has(h, "Light"), h + " " + St.getTrains() + "/" + tb);
	var tc = St.getTrains(), statT = 0;
	var b2 = PU.list(St); for (var j = 0; j < b2.size(); j++) if (s(b2.get(j).kind) == "STAT") statT += b2.get(j).trains;
	RC.execute(St, list("RESPEC", "ALL"), 0); h = said();
	check("all undone", St.baseCharStats().getStat(CS.STAT_STRENGTH) == str0 && statT > 0 && St.getTrains() == tc + statT && s(PU.show(St, "ALL")) == "", St.baseCharStats().getStat(CS.STAT_STRENGTH) + " " + statT + " " + h);
	// ONCE and MASK
	oracle.delEffect(RS);
	var RS2 = CMClass.getAbility("Prop_Respec"); RS2.setMiscText('WHAT=ALL ONCE=YES'); oracle.addNonUninvokableEffect(RS2);
	L.teach(teacher, St);
	RC.execute(St, list("RESPEC", "ALL"), 0); said();
	L.teach(teacher, St);
	RC.execute(St, list("RESPEC", "ALL"), 0); h = said();
	check("once", has(h, "already") && St.fetchAbility("Spell_Light") != null, h);
	oracle.delEffect(RS2);
	var RS3 = CMClass.getAbility("Prop_Respec"); RS3.setMiscText('MASK="-RACE +Elf"'); oracle.addNonUninvokableEffect(RS3);
	RC.execute(St, list("RESPEC"), 0); h = said();
	check("mask", has(h, "may not"), h);
	oracle.delEffect(RS3);
	// MPRESPEC from a script
	var priest = newMob("GenMob", R); priest.setName("Fixp23 priest"); scriptOn(priest, "ONCE_PROG 100\nmprespec Fixp23pupil SKILLS ALL\n~\n"); made.push(priest);
	tickMob(priest);
	check("mprespec", St.fetchAbility("Spell_Light") == null, "still knows it");
	check("help", has(CMLib.help().getHelpText("RESPEC", M, false), "RESPEC") && has(CMLib.help().getHelpText("PROP_RESPEC", M, true), "WHAT"), "no help");
} finally {
	made.forEach(function (m) { if (!m.amDestroyed()) m.destroy(); });
}
done();
