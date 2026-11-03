// P20: GenDisease: a disease as data, its fields and XML, catching, immunity, its delay (message, damage, ONTICK), proximity, cures, and its end.
FIXFILE = "p20-disease";
function s(x) { return "" + x; }
function has(t, p) { return s(t).indexOf(p) >= 0; }
var CS = CM.Common.interfaces.CharStats;
var DA = java.lang.Class.forName("com.planet_ink.coffee_mud.Abilities.interfaces.DiseaseAffect");
var R = scratchRoom();
var made = [];
function npc(name, race) {
	var m = CMClass.getMOB("GenMob"); m.setName(name);
	m.baseCharStats().setMyRace(CMClass.getRace(race)); m.basePhyStats().setLevel(10);
	m.baseCharStats().setStat(CS.STAT_CONSTITUTION, 14);
	m.baseCharStats().setStat(CS.STAT_SAVE_DISEASE, -200);
	m.recoverCharStats(); m.recoverPhyStats(); m.recoverMaxState(); m.resetToMaxState();
	m.bringToLife(R, true); made.push(m); return m;
}
clearVar("P20TICK"); clearVar("P20CAUGHT"); clearVar("P20GONE");
try {
	var G = CMClass.getAbility("GenDisease");
	check("class", G != null && DA.isInstance(G) && G.isGeneric(), s(G));
	var D = G.copyOf();
	D.setStat("CLASS", "FixAshLung");
	D.setStat("NAME", "Ash Lung"); D.setStat("DISPLAY", "(Ash Lung)");
	D.setStat("TICKS", "40"); D.setStat("DELAY", "2"); D.setStat("SPREAD", "PROXIMITY, CONTACT");
	D.setStat("CATCHCHANCE", "100"); D.setStat("DIFFICULTY", "2");
	D.setStat("AFFECTMSG", "<S-NAME> cough(s) up grey ash."); D.setStat("DAMAGE", "1");
	D.setStat("ADJUSTMENTS", "con-2"); D.setStat("IMMUNEMASK", "-RACE +Dwarf");
	D.setStat("SCRIPT", "FUNCTION_PROG ONAFFECT\nmpsetvar FIXTEST P20CAUGHT yes\n~\nFUNCTION_PROG ONTICK\nmpsetvar FIXTEST P20TICK yes\n~\nFUNCTION_PROG ONEXPIRE\nmpsetvar FIXTEST P20GONE yes\n~\n");
	var A = CMClass.getAbility("FixAshLung");
	check("registered", A != null && s(A.name()) == "Ash Lung" && (A.classificationCode() & 31) == CM.Abilities.interfaces.Ability.ACODE_DISEASE
		&& A.spreadBitmap() == (DA.getField("SPREAD_PROXIMITY").getInt(null) | DA.getField("SPREAD_CONTACT").getInt(null)) && A.difficultyLevel() == 2, s(A));
	// the XML, read back
	var xml = s(D.getStat("ALLXML"));
	var D2 = G.copyOf(); D2.setStat("ALLXML", xml);
	check("xml", s(D2.ID()) == "FixAshLung" && s(D2.getStat("SPREAD")) == "CONTACT,PROXIMITY" && s(D2.getStat("DAMAGE")) == "1" && has(D2.getStat("SCRIPT"), "ONTICK"), s(D2.getStat("SPREAD")));
	// catching it: the adjustments hold, ONAFFECT runs
	var miner = npc("Fixp20 miner", "Human"), dwarf = npc("Fixp20 dwarf", "Dwarf"), other = npc("Fixp20 carter", "Human");
	var con0 = miner.charStats().getStat(CS.STAT_CONSTITUTION);
	check("caught", A.invoke(miner, miner, true, 10) && miner.fetchEffect("FixAshLung") != null, "not caught");
	miner.recoverCharStats();
	check("adjusted", miner.charStats().getStat(CS.STAT_CONSTITUTION) == con0 - 2 && gvar("P20CAUGHT") == "yes", miner.charStats().getStat(CS.STAT_CONSTITUTION) + "/" + con0 + " " + gvar("P20CAUGHT"));
	check("immune", !A.invoke(dwarf, dwarf, true, 10) && dwarf.fetchEffect("FixAshLung") == null, "the dwarf caught it");
	// each delay: the message, the damage, ONTICK, and the cough reaches someone near
	var E = miner.fetchEffect("FixAshLung");
	miner.baseState().setHitPoints(500); miner.recoverMaxState(); miner.resetToMaxState();
	var hp0 = miner.curState().getHitPoints();
	for (var i = 0; i < 2; i++) E.tick(miner, Tickable.TICKID_MOB);
	check("delay", miner.curState().getHitPoints() < hp0 && gvar("P20TICK") == "yes", hp0 + " -> " + miner.curState().getHitPoints() + " " + gvar("P20TICK"));
	// PROXIMITY: each delay someone in the room may catch it (here, only the carter can)
	dwarf.destroy();
	for (var j = 0; j < 40 && other.fetchEffect("FixAshLung") == null; j++) E.tick(miner, Tickable.TICKID_MOB);
	check("proximity", other.fetchEffect("FixAshLung") != null, "no spread in " + j + " ticks");
	// a cure: DIFFICULTY 2 is beaten by a healer over level 20
	var healer = npc("Fixp20 priest", "Human"); healer.basePhyStats().setLevel(30); healer.recoverPhyStats();
	var cure = CMClass.getAbility("Prayer_CureDisease");
	cure.invoke(healer, miner, true, 30);
	check("cured", miner.fetchEffect("FixAshLung") == null && gvar("P20GONE") == "yes", s(miner.fetchEffect("FixAshLung")) + " " + gvar("P20GONE"));
	// the data type for a Java disease, and for this one (COPY, CREATE over a coded one)
	check("type", s(CMLib.ableParms().getGenericClassID(CMClass.getAbility("Disease_Cold"))) == "GenDisease" && s(CMLib.ableParms().getGenericClassID(A)) == "GenDisease"
		&& s(CMLib.ableParms().getGenericClassID(CMClass.getAbility("GenAbility"))) == "GenAbility", s(CMLib.ableParms().getGenericClassID(A)));
	check("help", has(CMLib.help().getHelpText("GENDISEASE", M, true), "CREATE DISEASE"), "no help");
} finally {
	made.forEach(function (m) { if (!m.amDestroyed()) m.destroy(); });
	if (typeof D != "undefined" && D != null) CMClass.delClass(CM.core.CMClass.CMObjectType.ABILITY, D);
}
done();
