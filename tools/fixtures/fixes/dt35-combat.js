// DT35: COMBATDEBUG explains each attack on a watched character on one line.
FIXFILE = "dt35-combat";
var R = M.location();
function has(text, part) { return ("" + text).replace(/\s+/g, " ").indexOf(part) >= 0; }
var B = newMob("GenMob", R);
B.setName("Fixcombatwatcher");
var BS = CMClass.getCommon("FakeSession");
BS.initialize(null, "MEMORY");
B.setSession(BS);
BS.setMob(B);
var mark = 0;
function out() { var all = "" + field(BS, "bout").toString(); var s = all.substring(mark); mark = all.length; return s; }
function cd(words) { var L = new java.util.ArrayList(); L.add("COMBATDEBUG"); var w = words.split(" "); for (var i = 0; i < w.length; i++) if (w[i].length > 0) L.add(w[i]); CMClass.getCommand("CombatDebugCmd").execute(B, L, 0); return out(); }

var gob = newMob("GenMob", R);
gob.setName("a fixes goblin");
var dummy = newMob("GenMob", R);
dummy.setName("a fixes dummy");
dummy.baseState().setHitPoints(100000);
dummy.recoverMaxState(); dummy.resetToMaxState();
var absorb = CMClass.getAbility("Prop_AbsorbDamage");
absorb.setMiscText("50% +ALL");
dummy.addNonUninvokableEffect(absorb);
dummy.recoverPhyStats();

check("off", has(cd(""), "COMBATDEBUG: off."), "off at first");
check("on", has(cd("ON a fixes dummy"), "COMBATDEBUG: attacks by or on a fixes dummy."), "ON");
var lines = "", hit = "";
for (var i = 0; i < 40 && hit.length == 0; i++) {
	CMLib.combat().postAttack(gob, dummy, null);
	var o = out();
	lines += o;
	var ls = o.split("\n");
	for (var j = 0; j < ls.length; j++) if (ls[j].indexOf(" HIT") >= 0) hit = ls[j];
	dummy.resetToMaxState();
}
check("line", has(lines, "[dbg] a fixes goblin -> a fixes dummy: atk ") && has(lines, " vs ac ") && has(lines, "% roll "), lines.substring(0, 300));
check("hit", hit.length > 0 && has(hit, "dmg ") && has(hit, "(TARGETED, from ") && has(hit, "FUDGE("), hit);
check("absorb", has(hit, "Prop_AbsorbDamage(50% +ALL) on a fixes dummy -> ") && has(hit, "deals "), hit);
// a fight with no one watched says nothing
cd("OFF");
var other = newMob("GenMob", R);
other.setName("a fixes bystander");
CMLib.combat().postAttack(gob, other, null);
check("quiet", !has(out(), "[dbg]"), "a line with no one watched");
// LOG: the lines in a file too
var F = java.io.File.createTempFile("fixcombat", ".log");
cd("LOG " + F.getPath());
cd("ON");
CMLib.combat().postAttack(gob, other, null);
cd("OFF");
cd("LOG OFF");
var text = "" + new java.lang.String(java.nio.file.Files.readAllBytes(F.toPath()), "UTF-8");
check("log", has(text, "[dbg] a fixes goblin -> a fixes bystander: atk "), text);
F["delete"]();

gob.makePeace(true); dummy.makePeace(true); other.makePeace(true);
gob.destroy(); dummy.destroy(); other.destroy();
B.setSession(null);
B.destroy();
done();
