// JS9: Prop_JScript (stat hooks and messages) and JSBehavior (messages and ticks), written in JavaScript.
FIXFILE = "js9-props";
var R = M.location();
function s(x) { return "" + x; }
function write(f, text) { f.getParentFile().mkdirs(); java.nio.file.Files.write(f.toPath(), new java.lang.String(text).getBytes("UTF-8")); }
var dir = new java.io.File(new java.io.File(WorldFiles.worldDir(), "js"), "props");
var BF = new java.io.File(dir, "fixjs9blade.js");
write(BF, [
	"// a sword: 1 damage for each 10 kills, counted in parms.KILLS",
	"function affectPhyStats(host, affected, stats) {",
	"  if (affected == host) stats.setDamage(stats.damage() + Math.floor(parms.KILLS / 10));",
	"}",
	"function executeMsg(host, msg) {",
	"  if (msg.sourceMinor() == CMMsg.TYP_DEATH && host.owner() != null && msg.tool() == host.owner()) {",
	"    parms.KILLS++;",
	"    host.recoverPhyStats();",
	"  }",
	"}",
	"function okMessage(host, msg) {",
	"  return !(msg.targetMinor() == CMMsg.TYP_GET && msg.target() == host && parms.CURSED == 'yes');",
	"}",
	"function onAdded(host) { cm.var('FIXTEST', 'FIXJS9ADD', host.Name()); }",
	"function onRemoved(host) { cm.var('FIXTEST', 'FIXJS9DEL', host.Name()); }"
].join("\n"));
var SF = new java.io.File(dir, "fixjs9strong.js");
write(SF, "function affectCharStats(host, mob, stats) { stats.setStat(CM.Common.interfaces.CharStats.STAT_STRENGTH, stats.getStat(CM.Common.interfaces.CharStats.STAT_STRENGTH) + parms.PLUS); }\n"
	+ "var CM = Packages.com.planet_ink.coffee_mud;\n"
	+ "function affectCharState(host, mob, state) { state.setHitPoints(state.getHitPoints() + 100); }\n");
var TF = new java.io.File(dir, "fixjs9clock.js");
write(TF, "function tick(host, id) { parms.TICKS = (parms.TICKS || 0) + 1; }\n"
	+ "function okMessage(host, msg) { return !(msg.sourceMinor() == CMMsg.TYP_SPEAK && msg.source() != null && s(msg.source().Name()) == 'a fixjs9 mute'); }\n"
	+ "function s(x) { return '' + x; }\n"
	+ "function onAdded(host) { parms.STARTED = 'yes'; }\n");

// the sword
["FIXJS9ADD", "FIXJS9DEL"].forEach(clearVar);
var sword = CMClass.getWeapon("GenWeapon");
sword.setName("a fixjs9 blade");
sword.basePhyStats().setDamage(5);
sword.recoverPhyStats();
var P = CMClass.getAbility("Prop_JScript");
P.setMiscText("SCRIPT=world:js/props/fixjs9blade.js KILLS=19");
sword.addNonUninvokableEffect(P);
sword.recoverPhyStats();
check("added", gvar("FIXJS9ADD") == "a fixjs9 blade", "onAdded: '" + gvar("FIXJS9ADD") + "'");
check("affectPhyStats", sword.phyStats().damage() == 6, "damage " + sword.phyStats().damage());
var killer = newMob("GenMob", R);
killer.setName("a fixjs9 killer");
killer.addItem(sword);
var victim = newMob("GenMob", R);
var death = CMClass.getMsg(victim, null, killer, CMMsg.MSG_DEATH, null);
P.executeMsg(sword, death);
check("parms", sword.phyStats().damage() == 7 && s(P.text()).indexOf("KILLS=20") >= 0, "damage " + sword.phyStats().damage() + ", text " + P.text());
check("text", s(P.text()) == "SCRIPT=world:js/props/fixjs9blade.js KILLS=20", "text '" + P.text() + "'");
// a copy keeps the count, as a save would
var copy = sword.copyOf();
copy.recoverPhyStats();
check("copied", copy.phyStats().damage() == 7, "copy's damage " + copy.phyStats().damage());
copy.destroy();
P.setMiscText("SCRIPT=world:js/props/fixjs9blade.js KILLS=20 CURSED=yes");
var get = CMClass.getMsg(killer, sword, null, CMMsg.MSG_GET, null);
check("okMessage", !P.okMessage(sword, get), "a cursed blade is not got");
sword.delEffect(P);
check("removed", gvar("FIXJS9DEL") == "a fixjs9 blade", "onRemoved: '" + gvar("FIXJS9DEL") + "'");
sword.recoverPhyStats();
check("gone", sword.phyStats().damage() == 5, "damage " + sword.phyStats().damage());

// a mob's stats
var strong = CMClass.getAbility("Prop_JScript");
strong.setMiscText("SCRIPT=world:js/props/fixjs9strong.js PLUS=5");
var base = killer.charStats().getStat(CM.Common.interfaces.CharStats.STAT_STRENGTH);
var hp = killer.maxState().getHitPoints();
killer.addNonUninvokableEffect(strong);
killer.recoverCharStats();
killer.recoverMaxState();
check("affectCharStats", killer.charStats().getStat(CM.Common.interfaces.CharStats.STAT_STRENGTH) == base + 5, "strength " + base + " > " + killer.charStats().getStat(CM.Common.interfaces.CharStats.STAT_STRENGTH));
check("affectCharState", killer.maxState().getHitPoints() == hp + 100, "hit points " + hp + " > " + killer.maxState().getHitPoints());

// a file that is not there
var none = CMClass.getAbility("Prop_JScript");
none.setMiscText("SCRIPT=world:js/props/fixjs9nothere.js");
victim.addNonUninvokableEffect(none);
victim.recoverPhyStats();
check("missing", s(none.hooks().error()).indexOf("no such file") >= 0, "error " + none.hooks().error());

// a behaviour
var B = CMClass.getBehavior("JSBehavior");
B.setParms("SCRIPT=world:js/props/fixjs9clock.js");
victim.addBehavior(B);
check("behaviour-added", s(B.getParms()).indexOf("STARTED=yes") >= 0, "parms " + B.getParms());
B.tick(victim, Tickable.TICKID_MOB);
B.tick(victim, Tickable.TICKID_MOB);
check("behaviour-tick", s(B.getParms()).indexOf("TICKS=2") >= 0, "parms " + B.getParms());
var mute = newMob("GenMob", R);
mute.setName("a fixjs9 mute");
var say = CMClass.getMsg(mute, null, null, CMMsg.MSG_SPEAK, "hello");
check("behaviour-ok", !B.okMessage(victim, say) && B.okMessage(victim, CMClass.getMsg(killer, null, null, CMMsg.MSG_SPEAK, "hi")), "the mute may not speak");

BF["delete"](); SF["delete"](); TF["delete"]();
killer.destroy(); victim.destroy(); mute.destroy(); sword.destroy();
done();
