// C17: Prop_Essential MODE=YIELD|KNOCKOUT AT= TICKS= MSG= REFUSEMSG= NOXP: the fight ends at the threshold (or a
// killing blow leaves it at 1, out cold), attacks are refused for TICKS, YIELD_PROG / DEFEAT_PROG run, and the one
// who beat it gains a kill's experience.
FIXFILE = "c17-essential";
function s(x) { return "" + x; }
var R = scratchRoom();
function npc(name, hp) { var m = newMob("GenMob", R); m.setName(name); m.basePhyStats().setLevel(10); m.baseState().setHitPoints(hp); m.recoverPhyStats(); m.recoverMaxState(); m.resetToMaxState(); return m; }
var V = npc("Fixc17pip", 1000);
V.setPlayerStats(CMClass.getCommon("DefaultPlayerStats"));
V.baseCharStats().setCurrentClass(CMClass.getCharClass("Fighter")); V.recoverCharStats();
var VS = CMClass.getCommon("FakeSession"); VS.initialize(null, "MEMORY"); V.setSession(VS); VS.setMob(V);
var mark = 0;
function out() { var all = s(field(VS, "bout").toString()); var o = all.substring(mark); mark = all.length; return o.replace(/\x1b\[[0-9;]*m/g, "").replace(/\^./g, ""); }
function essential(m, parms) { var A = CMClass.getAbility("Prop_Essential"); A.setMiscText(parms); m.addNonUninvokableEffect(A); return A; }
function hit(a, b, n) { CMLib.combat().postDamage(a, b, null, n, CMMsg.MSG_OK_VISUAL | CMMsg.MASK_ALWAYS, -1, null); }

// YIELD
var knight = npc("Fixc17knight", 100);
var KA = essential(knight, "MODE=YIELD AT=10% TICKS=3");
["FIXC17Y", "FIXC17D"].forEach(clearVar);
scriptOn(knight, "YIELD_PROG\nmpsetvar FIXTEST FIXC17Y $n\n~\n");
knight.setVictim(V); V.setVictim(knight);
var xp0 = V.getExperience();
hit(V, knight, 50);
check("still-fights", knight.isInCombat() && gvar("FIXC17Y") == "", "yielded at 50%");
hit(V, knight, 45);
check("yields", (!knight.isInCombat()) && V.getVictim() != knight && gvar("FIXC17Y") == "Fixc17pip" && (!knight.amDead()),
	"fighting " + knight.isInCombat() + " prog '" + gvar("FIXC17Y") + "'");
check("xp", V.getExperience() > xp0, "no experience for beating it: " + xp0 + " -> " + V.getExperience());
out();
CMLib.combat().postAttack(V, knight, null);
check("refused", (!knight.isInCombat()) && has(out(), "has yielded"), "attacked while yielded");
for (var i = 0; i < 3; i++) KA.tick(knight, Tickable.TICKID_MOB);
knight.setVictim(V); V.setVictim(knight);
var before = knight.curState().getHitPoints();
hit(V, knight, 1);
check("after", knight.curState().getHitPoints() == before - 1, "still spared after TICKS");
knight.makePeace(true); V.makePeace(true);

// KNOCKOUT
var guard = npc("Fixc17guard", 100);
var GA = essential(guard, "MODE=KNOCKOUT TICKS=2 NOXP");
scriptOn(guard, "DEFEAT_PROG\nmpsetvar FIXTEST FIXC17D $n\n~\n");
guard.setVictim(V); V.setVictim(guard);
var xp1 = V.getExperience();
hit(V, guard, 500);
check("knockout", (!guard.amDead()) && guard.curState().getHitPoints() == 1 && CMLib.flags().isSleeping(guard) && gvar("FIXC17D") == "Fixc17pip",
	"dead " + guard.amDead() + " hp " + guard.curState().getHitPoints() + " asleep " + CMLib.flags().isSleeping(guard) + " prog '" + gvar("FIXC17D") + "'");
check("noxp", V.getExperience() == xp1, "experience with NOXP");
for (var j = 0; j < 2; j++) GA.tick(guard, Tickable.TICKID_MOB);
check("wakes", !CMLib.flags().isSleeping(guard), "still out cold");
check("help", s(CMLib.help().getHelpText("PROP_ESSENTIAL", M, true)).indexOf("KNOCKOUT") >= 0, "no help");

function has(t, p) { return s(t).replace(/\s+/g, " ").indexOf(p) >= 0; }
[knight, guard].forEach(function (m) { m.makePeace(true); m.destroy(); });
V.setPlayerStats(null); V.destroy();
R.destroy();
done();
