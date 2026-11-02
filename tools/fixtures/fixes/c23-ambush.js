// C23: Prop_Ambush MOBS= CHANCE= DETECT=parts:dc SURPRISE= MSG= SPOTMSG= WAIT= ONCE|RESET= MASK=; Prop_Surprised;
// AMBUSH_PROG SURPRISED|SPOTTED|AVOIDED.
FIXFILE = "c23-ambush";
var Dir = CM.core.Directions;
var TPS = Tickable.TICKID_PROPERTY_SPECIAL;
function s(x) { return "" + x; }
function has(t, p) { return s(t).replace(/\s+/g, " ").indexOf(p) >= 0; }
var BP = CMClass.getMOB("GenMob"); BP.setName("a fixc23 bandit"); BP.recoverPhyStats(); CMLib.catalog().addCatalog(BP); BP.destroy();
var rooms = [];
function pair(name, parms) {
	var a = scratchRoom(), b = scratchRoom();
	a.setDisplayText("Fixc23 road to " + name); b.setDisplayText("Fixc23 " + name);
	a.rawDoors()[Dir.EAST] = b; a.setRawExit(Dir.EAST, CMClass.getExit("Open"));
	b.rawDoors()[Dir.WEST] = a; b.setRawExit(Dir.WEST, CMClass.getExit("Open"));
	var P = CMClass.getAbility("Prop_Ambush"); P.setMiscText(parms); b.addNonUninvokableEffect(P);
	scriptOn(b, "AMBUSH_PROG SURPRISED\nmpsetvar FIXTEST FIXC23S yes\n~\nAMBUSH_PROG SPOTTED\nmpsetvar FIXTEST FIXC23P yes\n~\nAMBUSH_PROG AVOIDED\nmpsetvar FIXTEST FIXC23A yes\n~\n");
	rooms.push(a); rooms.push(b);
	return { from: a, to: b, P: P };
}
var V = newMob("GenMob", scratchRoom());
V.setName("Fixc23pip");
V.baseState().setHitPoints(5000); V.recoverMaxState(); V.resetToMaxState();
V.setPlayerStats(CMClass.getCommon("DefaultPlayerStats"));
var VS = CMClass.getCommon("FakeSession"); VS.initialize(null, "MEMORY"); V.setSession(VS); VS.setMob(V);
var mark = 0;
function out() { var all = s(field(VS, "bout").toString()); var o = all.substring(mark); mark = all.length; return o.replace(/\x1b\[[0-9;]*m/g, "").replace(/\^./g, ""); }
function bandits(R) { var l = []; for (var i = 0; i < R.numInhabitants(); i++) if (s(R.fetchInhabitant(i).Name()) == "a fixc23 bandit") l.push(R.fetchInhabitant(i)); return l; }
function go(p) { V.makePeace(true); p.from.bringMobHere(V, false); out(); CMLib.tracking().walk(V, Dir.EAST, false, false); }
["FIXC23S", "FIXC23P", "FIXC23A"].forEach(clearVar);
// not spotted: they burst out, and the party loses its first round
var a1 = pair("woods", "MOBS=\"a fixc23 bandit*2\" DETECT=WIS:40 SURPRISE=1 MSG=\"Fixc23 figures burst from the undergrowth!\" SPOTMSG=\"Fixc23 you spot steel.\"");
go(a1);
var queued = field(a1.P, "pending") == V;
a1.P.tick(a1.P, TPS);
var b1 = bandits(a1.to), att = 0;
b1.forEach(function (b) { if (b.getVictim() == V) att++; });
check("surprised", queued && b1.length == 2 && att == 2 && has(out(), "Fixc23 figures burst from the undergrowth!") && gvar("FIXC23S") == "yes" && b1[0].hasTag("AMBUSH"),
	"queued " + queued + " bandits " + b1.length + " attacking " + att + " prog '" + gvar("FIXC23S") + "'");
var SP = V.fetchEffect("Prop_Surprised");
CMLib.tracking().walk(V, Dir.WEST, false, false);
check("caught", SP != null && V.location() == a1.to && has(out(), "caught by surprise"), "moved while surprised");
SP.tick(V, Tickable.TICKID_MOB);
V.makePeace(true); b1.forEach(function (b) { b.makePeace(true); });
CMLib.tracking().walk(V, Dir.WEST, false, false);
check("recovered", V.fetchEffect("Prop_Surprised") == null && V.location() == a1.from, "still caught");
b1.forEach(function (b) { b.destroy(); });
// ONCE: not again
go(a1);
check("once", s(a1.P.getStat("ARMED")) == "false" && field(a1.P, "pending") == null, "armed again");
// spotted: they wait; the party strikes first
var a2 = pair("glade", "MOBS=\"a fixc23 bandit\" DETECT=WIS+Thief_DetectTraps:-50 WAIT=2 SPOTMSG=\"Fixc23 you spot steel.\"");
go(a2);
a2.P.tick(a2.P, TPS);
var b2 = bandits(a2.to);
check("spotted", b2.length == 1 && (!b2[0].isInCombat()) && has(out(), "Fixc23 you spot steel.") && gvar("FIXC23P") == "yes" && V.fetchEffect("Prop_Surprised") == null,
	"bandits " + b2.length + " fighting " + (b2.length ? b2[0].isInCombat() : "-"));
V.setVictim(b2[0]); b2[0].setVictim(V);
a2.P.tick(a2.P, TPS);
check("first-strike", field(a2.P, "standoff") == 0 && !b2[0].amDestroyed(), "standoff " + field(a2.P, "standoff"));
V.makePeace(true); b2[0].destroy();
// spotted, and they walk on: avoided
var a3 = pair("hollow", "MOBS=\"a fixc23 bandit\" DETECT=WIS:-50 WAIT=3");
go(a3);
a3.P.tick(a3.P, TPS);
var b3 = bandits(a3.to);
a3.from.bringMobHere(V, false);
a3.P.tick(a3.P, TPS);
check("avoided", b3.length == 1 && b3[0].amDestroyed() && gvar("FIXC23A") == "yes", "bandit still there or no prog");
// spotted, and they wait too long: the ambushers strike
var a4 = pair("ford", "MOBS=\"a fixc23 bandit\" DETECT=WIS:-50 WAIT=2");
go(a4);
a4.P.tick(a4.P, TPS);
var b4 = bandits(a4.to);
a4.P.tick(a4.P, TPS);
var calm = !b4[0].isInCombat();
a4.P.tick(a4.P, TPS);
check("standoff", calm && b4[0].getVictim() == V, "calm " + calm + " fighting " + b4[0].isInCombat());
V.makePeace(true); b4[0].destroy();
// RESET=2: armed again two ticks later
var a5 = pair("pass", "MOBS=\"a fixc23 bandit\" DETECT=WIS:40 SURPRISE=0 RESET=2");
go(a5);
a5.P.tick(a5.P, TPS);
var spent = s(a5.P.getStat("ARMED")) == "false";
a5.P.tick(a5.P, TPS); a5.P.tick(a5.P, TPS);
check("reset", spent && s(a5.P.getStat("ARMED")) == "true", "armed " + a5.P.getStat("ARMED"));
V.makePeace(true); bandits(a5.to).forEach(function (b) { b.destroy(); });
// MASK and CHANCE
var a6 = pair("bridge", "MOBS=\"a fixc23 bandit\" MASK=\"-NAMES +Fixc23nobody\"");
go(a6);
var a7 = pair("cliff", "MOBS=\"a fixc23 bandit\" CHANCE=0");
go(a7);
check("mask-chance", field(a6.P, "pending") == null && field(a7.P, "pending") == null, "sprang");
// the spotting bonus: a stat's (n-10)/2 and a skill's proficiency/10
V.baseCharStats().setStat(CM.Common.interfaces.CharStats.STAT_WISDOM, 18); V.recoverCharStats();
var SS = CMClass.getAbility("Thief_DetectTraps"); SS.setProficiency(50); V.addAbility(SS);
check("bonus", CM.Abilities.Properties.Prop_Ambush.bonus(V, "WIS+Thief_DetectTraps") == 9, "bonus " + CM.Abilities.Properties.Prop_Ambush.bonus(V, "WIS+Thief_DetectTraps"));
check("help", has(CMLib.help().getHelpText("PROP_AMBUSH", M, true), "DETECT") && has(CMLib.help().getHelpText("PROP_SURPRISED", M, true), "TICKS"), "no help");

V.makePeace(true); V.setPlayerStats(null); V.destroy();
rooms.forEach(function (r) { bandits(r).forEach(function (b) { b.destroy(); }); r.destroy(); });
CMLib.catalog().delCatalog(CMLib.catalog().getCatalogMob("a fixc23 bandit"));
done();
