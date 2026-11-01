// ST2: SKILL_PROG fires for the scripts around a character who learns,
// improves, masters or fails a skill; $t is the skill, $g its proficiency.
FIXFILE = "st2-skill";
var SEv = CM.core.ScriptEvents;
var R = M.location();
var CS = CM.Common.interfaces.CharStats;
function fakePlayer(name) {
	var P = newMob("StdMOB", R);
	P.setName(name);
	P.setPlayerStats(CMClass.getCommon("DefaultPlayerStats"));
	P.baseCharStats().setStat(CS.STAT_INTELLIGENCE, 25);
	P.recoverCharStats();
	return P;
}
function setField(obj, name, v) {
	for (var c = obj.getClass(); c != null; c = c.getSuperclass()) {
		try { var f = c.getDeclaredField(name); f.setAccessible(true); f.setLong(obj, v); return; } catch (e) { }
	}
	throw "no field " + name;
}
["FIXST2L", "FIXST2I", "FIXST2M", "FIXST2X", "FIXST2F", "FIXST2B", "FIXST2G"].forEach(clearVar);
var S = scriptOn(R, "SKILL_PROG LEARN Skill_Climb\nmpsetvar FIXTEST FIXST2L $n:$t:$g\n~\n"
	+ "SKILL_PROG IMPROVE *\nmpsetvar FIXTEST FIXST2I $t:$g\n~\n"
	+ "SKILL_PROG MASTER 'climb'\nmpsetvar FIXTEST FIXST2M $t\n~\n"
	+ "SKILL_PROG FAIL ALL -NAME +fixst2nobody\nmpsetvar FIXTEST FIXST2X $t\n~\n"
	+ "SKILL_PROG FAIL Skill_Climb\nmpsetvar FIXTEST FIXST2F $t:$g\n~\n"
	+ "SKILL_PROG LEARN Skill_Swim\nmpsetvar FIXTEST FIXST2G $t\n~\n");
check("used", SEv.isUsed(SEv.SKILL_PROG) && SEv.SKILL_PROG == 61 && SEv.DISCOVER_PROG == 62, "SKILL_PROG " + SEv.SKILL_PROG);
var P = fakePlayer("Fixstwolearner");
// a mentor in the room, scripted as a behaviour
var T = newMob("GenMob", R);
T.setName("a fixst2 mentor");
var B = CMClass.getBehavior("Scriptable");
B.setParms("SKILL_PROG LEARN *\nmpsetvar FIXTEST FIXST2B $n\n~\n");
T.addBehavior(B);

// LEARN
var A = CMClass.getAbility("Skill_Climb");
A.setProficiency(50);
P.addAbility(A);
runQueued(S);
for (var i = 0; i < 4; i++) B.dequeResponses(null);
check("learn", gvar("FIXST2L") == "Fixstwolearner:Climb:50", "FIXST2L='" + gvar("FIXST2L") + "'");
check("learn-behaviour", gvar("FIXST2B") == "Fixstwolearner", "FIXST2B='" + gvar("FIXST2B") + "'");
// a skill a level brings: told by addAbility already, so not again; one that did not come that way is
clearVar("FIXST2L");
SEv.levelGrant(P, A);
runQueued(S);
check("level-told-once", gvar("FIXST2L") == "", "FIXST2L='" + gvar("FIXST2L") + "'");
SEv.levelGrant(P, CMClass.getAbility("Skill_Swim"));
runQueued(S);
check("level-racial", gvar("FIXST2G") == "Swim", "FIXST2G='" + gvar("FIXST2G") + "'");

// IMPROVE, by practice with a teacher
var TA = CMClass.getAbility("Skill_Climb");
TA.setProficiency(100);
T.addAbility(TA);
P.setPractices(10);
A.practice(T, P);
runQueued(S);
var imp = gvar("FIXST2I").split(":");
check("improve-practice", imp[0] == "Climb" && CMath.s_int(imp[1]) == A.proficiency() && A.proficiency() > 50, "FIXST2I='" + gvar("FIXST2I") + "', proficiency " + A.proficiency());

// MASTER, and IMPROVE, by use (helpProficiency), up to the maximum
var max = CMLib.ableMapper().getMaxProficiency(P, true, "Skill_Climb");
A.setProficiency(max - 1);
clearVar("FIXST2I");
for (var i = 0; i < 600 && A.proficiency() < max; i++) {
	setField(A, "lastCastHelp", -10000000);
	A.helpProficiency(P, 0);
}
runQueued(S);
check("master", A.proficiency() >= max && gvar("FIXST2M") == "Climb", "proficiency " + A.proficiency() + "/" + max + ", FIXST2M='" + gvar("FIXST2M") + "'");
check("improve-use", gvar("FIXST2I") == "Climb:" + max, "FIXST2I='" + gvar("FIXST2I") + "'");

// FAIL: a failed proficiency roll; the masked line is not for this learner
A.setProficiency(0);
var failed = false;
for (var i = 0; i < 60 && !failed; i++) failed = !A.proficiencyCheck(P, 0, false);
runQueued(S);
check("fail", failed && gvar("FIXST2F") == "Climb:0" && gvar("FIXST2X") == "", "FIXST2F='" + gvar("FIXST2F") + "', FIXST2X='" + gvar("FIXST2X") + "'");

// not a character's: an NPC learning tells nothing
for (var i = 0; i < 4; i++) B.dequeResponses(null);
clearVar("FIXST2B");
T.addAbility(CMClass.getAbility("Skill_Trip"));
for (var i = 0; i < 4; i++) B.dequeResponses(null);
check("npc-silent", gvar("FIXST2B") == "", "FIXST2B='" + gvar("FIXST2B") + "'");

R.delScript(S);
P.setPlayerStats(null);
P.destroy();
T.destroy();
done();
