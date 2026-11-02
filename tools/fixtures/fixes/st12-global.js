// ST12: @GLOBAL on EXECMSG_PROG, DEATH_PROG and KILL_PROG hears world-wide
// messages, once even in the room they happen in; SKILL_PROG @GLOBAL anywhere.
FIXFILE = "st12-global";
var R = M.location();
var R2 = otherRoom();
["FIXST12E", "FIXST12D", "FIXST12K", "FIXST12C", "FIXST12S"].forEach(clearVar);
var SE = scriptOn(R2, "EXECMSG_PROG @GLOBAL DEATH ALL\nmpsetvar FIXTEST FIXST12E $n\n~\n");
var G = newMob("GenMob", R2);
G.setName("a fixst12 bounty clerk");
var SG = scriptOn(G, "DEATH_PROG @GLOBAL -NAME +Fixstbandit\nmpsetvar FIXTEST FIXST12D $n\n~\n");
var I = CMClass.getItem("StdItem");
I.setName("a fixst12 tally");
R2.addItem(I);
var SI = scriptOn(I, "KILL_PROG @GLOBAL\nmpsetvar FIXTEST FIXST12K $n\n~\n");
var SC = scriptOn(R, "EXECMSG_PROG @GLOBAL DEATH ALL\nmpsetvar FIXTEST FIXST12C ++\n~\n");
var V = newMob("GenMob", R);
V.setName("Fixstbandit");
var msg = CMClass.getMsg(V, null, M, CMMsg.MSG_DEATH, null);
R.send(V, msg);
CMLib.map().sendGlobalMessage(V, CMMsg.TYP_DEATH, msg);
function runAll() { runQueued(SE); runQueued(SG); runQueued(SI); runQueued(SC); }
runAll();
check("execmsg", gvar("FIXST12E") == "Fixstbandit", "FIXST12E='" + gvar("FIXST12E") + "'");
check("death", gvar("FIXST12D") != "", "FIXST12D='" + gvar("FIXST12D") + "'");
check("kill", gvar("FIXST12K") != "", "FIXST12K='" + gvar("FIXST12K") + "'");
check("once", gvar("FIXST12C") == "1", "FIXST12C='" + gvar("FIXST12C") + "'");
// the mask: not a bandit
clearVar("FIXST12D");
var V2 = newMob("GenMob", R);
V2.setName("Fixstfarmer");
CMLib.map().sendGlobalMessage(V2, CMMsg.TYP_DEATH, CMClass.getMsg(V2, null, M, CMMsg.MSG_DEATH, null));
runAll();
check("mask", gvar("FIXST12D") == "", "FIXST12D='" + gvar("FIXST12D") + "'");
// SKILL_PROG @GLOBAL, far from the learner
var SS = scriptOn(R2, "SKILL_PROG @GLOBAL LEARN Skill_Climb\nmpsetvar FIXTEST FIXST12S $n\n~\n");
var P = newMob("StdMOB", R);
P.setName("Fixsttwelvelearner");
P.setPlayerStats(CMClass.getCommon("DefaultPlayerStats"));
P.addAbility(CMClass.getAbility("Skill_Climb"));
runQueued(SS);
check("skill-global", gvar("FIXST12S") == "Fixsttwelvelearner", "FIXST12S='" + gvar("FIXST12S") + "'");
R2.delScript(SE); R2.delScript(SS); R.delScript(SC);
G.destroy(); I.destroy(); V.destroy(); V2.destroy();
P.setPlayerStats(null); P.destroy();
done();
