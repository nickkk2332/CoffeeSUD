// C3: BossPhases: ONENGAGE:, AT=n%:, AT=ROUND n:, AT=ADDSDEAD:, ONDEATH:, each once a fight; SAY, EMOTE, SUMMON xN,
// AFFECT, HEAL, IMMUNE, ROOM EMOTE, CALL; PHASE_PROG n and PHASE($i); RESET=LEAVECOMBAT; the run state.
FIXFILE = "c3-phases";
function s(x) { return "" + x; }
function has(t, p) { return s(t).replace(/\s+/g, " ").indexOf(p) >= 0; }
var R = scratchRoom();
var V = newMob("GenMob", R);
V.setName("Fixc3pip");
V.baseState().setHitPoints(5000); V.recoverMaxState(); V.resetToMaxState();
var VS = CMClass.getCommon("FakeSession"); VS.initialize(null, "MEMORY"); V.setSession(VS); VS.setMob(V);
var mark = 0;
function out() { var all = s(field(VS, "bout").toString()); var o = all.substring(mark); mark = all.length; return o.replace(/\x1b\[[0-9;]*m/g, "").replace(/\^./g, ""); }
var lich = newMob("GenMob", R);
lich.setName("Fixc3lich");
lich.baseState().setHitPoints(1000); lich.recoverMaxState(); lich.resetToMaxState();
var B = CMClass.getBehavior("BossPhases");
B.setParms("ONENGAGE: SAY \"Fools, you dare?\"; AT=60%: SUMMON GenMob x2, EMOTE raises its bony arms; "
	+ "AT=25%: AFFECT Prop_AbilityImmunity \"WEAPONS\" 3, HEAL 20%; AT=ROUND 5: ROOM EMOTE \"The crypt shakes.\"; "
	+ "AT=ADDSDEAD: SAY \"My servants!\", CALL LICHCRY; ONDEATH: ROOM EMOTE \"The phylactery cracks.\"; RESET=LEAVECOMBAT");
lich.addBehavior(B);
check("parsed", field(B, "phases").size() == 6 && s(field(B, "reset")) == "LEAVECOMBAT", "phases " + field(B, "phases").size());
["FIXC3P", "FIXC3F", "FIXC3N"].forEach(clearVar);
scriptOn(lich, "PHASE_PROG 2\nmpsetvar FIXTEST FIXC3P two\n~\nFUNCTION_PROG LICHCRY\nmpsetvar FIXTEST FIXC3F called\n~\n");
function fight() { lich.setVictim(V); V.setVictim(lich); }
function tick() { fight(); B.tick(lich, Tickable.TICKID_MOB); }
out();
tick(); // round 1
check("engage", has(out(), "Fools, you dare?"), "no ONENGAGE line");
lich.curState().setHitPoints(500);
tick(); // round 2
var adds = field(B, "summoned");
check("summon", adds.size() == 2 && adds.get(0).hasTag("SUMMONED") && adds.get(0).amFollowing() == lich && s(B.getStat("PHASE")) == "2" && gvar("FIXC3P") == "two",
	"adds " + adds.size() + " phase " + B.getStat("PHASE") + " prog '" + gvar("FIXC3P") + "'");
tick(); // round 3: nothing new
check("once", field(B, "summoned").size() == 2, "summoned again");
lich.curState().setHitPoints(200);
tick(); // round 4
check("affect-heal", lich.fetchEffect("Prop_AbilityImmunity") != null && lich.curState().getHitPoints() == 400 && s(B.getStat("PHASE")) == "3",
	"immunity " + (lich.fetchEffect("Prop_AbilityImmunity") != null) + " hp " + lich.curState().getHitPoints() + " phase " + B.getStat("PHASE"));
out();
tick(); // round 5
check("round", has(out(), "The crypt shakes."), "no AT=ROUND 5");
// the adds die
var list = field(B, "summoned"); var a0 = list.get(0), a1 = list.get(1);
a0.destroy(); a1.destroy();
out();
tick();
check("addsdead", has(out(), "My servants!") && gvar("FIXC3F") == "called" && s(B.getStat("PHASE")) == "5", "phase " + B.getStat("PHASE") + " call '" + gvar("FIXC3F") + "'");
// PHASE() in a script
scriptOn(lich, "RAND_PROG 100\nmpsetvar FIXTEST FIXC3N $%PHASE($i)%\nmpdisable\n~\n");
tickMob(lich);
check("phase-fn", gvar("FIXC3N") == "5", "PHASE() gave '" + gvar("FIXC3N") + "'");
var state = s(B.getStat("STATE"));
check("state", has(state, 'PHASE="5"'), state);
// RESET=LEAVECOMBAT: the fight ends, the phases rearm
lich.makePeace(true); V.makePeace(true);
B.tick(lich, Tickable.TICKID_MOB);
check("rearm", s(B.getStat("PHASE")) == "1" && field(B, "fired").size() == 0, "phase " + B.getStat("PHASE"));
// ONDEATH
out();
tick();
CMLib.combat().postDeath(V, lich, null);
check("ondeath", has(out(), "The phylactery cracks."), "no ONDEATH");
// IMMUNE
var golem = newMob("GenMob", R);
golem.setName("Fixc3golem");
golem.baseState().setHitPoints(100); golem.recoverMaxState(); golem.resetToMaxState();
var G = CMClass.getBehavior("BossPhases"); G.setParms("AT=50%: IMMUNE 2"); golem.addBehavior(G);
golem.setVictim(V); V.setVictim(golem);
golem.curState().setHitPoints(40);
G.tick(golem, Tickable.TICKID_MOB);
CMLib.combat().postDamage(V, golem, null, 10, CMMsg.MSG_OK_VISUAL | CMMsg.MASK_ALWAYS, -1, null);
check("immune", golem.curState().getHitPoints() == 40, "hurt while immune: " + golem.curState().getHitPoints());
check("help", has(CMLib.help().getHelpText("BEHAVIOR_BOSSPHASES", M, true), "ADDSDEAD"), "no help");

[golem, V].forEach(function (m) { m.makePeace(true); if (!m.amDestroyed()) m.destroy(); });
if (!lich.amDestroyed()) lich.destroy();
R.destroy();
done();
