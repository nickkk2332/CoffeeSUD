// JS5: world/js/<kind>/ folders join the class paths; a JavaScript class
// extending JSWorldBehavior hears onEnter and onDaypart; LOAD=world:path;
// world/js and world/scripts in the world's checksum, LIST SCRIPTS CUSTOM
// and a WORLD CHECK part JS.
FIXFILE = "js5-world";
var R = M.location();
var WF = CM.core.save.WorldFiles;
var W = WF.worldDir();
function write(f, text) { f.getParentFile().mkdirs(); java.nio.file.Files.write(f.toPath(), new java.lang.String(text).getBytes("UTF-8")); }
function has(text, part) { return ("" + text).indexOf(part) >= 0; }
["FIXJS5A", "FIXJS5B", "FIXJS5C"].forEach(clearVar);
var sum0 = "" + WF.checksum();

// the class folders
var bell = new java.io.File(W, "js/behaviors/FixJS5Bell.js");
write(bell, "//extends com.planet_ink.coffee_mud.Behaviors.JSWorldBehavior\n"
	+ "function ID() { return 'FixJS5Bell'; }\n"
	+ "var VARS = Packages.com.planet_ink.coffee_mud.core.CMClass.getCommon('DefaultScriptingEngine');\n"
	+ "function onEnter(mob, room) { VARS.setVar('FIXTEST', 'FIXJS5A', mob.Name() + ':' + this.parm('TONE')); }\n"
	+ "function onDaypart(area, was, now) { VARS.setVar('FIXTEST', 'FIXJS5B', was + '>' + now); }\n");
write(new java.io.File(W, "js/properties/FixJS5Glow.js"), "//extends com.planet_ink.coffee_mud.Abilities.Properties.Property\nfunction ID() { return 'FixJS5Glow'; }\n");
var paths = CM.application.SUD.worldClassPaths(new java.util.Properties());
check("paths", has(paths.get("BEHAVIORS"), ";world/js/behaviors") && has(paths.get("ABILITIES"), ";world/js/properties"), "the paths: " + paths);

// a class extending JSWorldBehavior
check("load", CMClass.loadClass(CMClass.CMObjectType.BEHAVIOR, "world/js/behaviors/FixJS5Bell.js", false), "it did not load");
var B = CMClass.getBehavior("FixJS5Bell");
check("class", B != null, "no FixJS5Bell");
if (B != null) {
	B.setParms("TONE=low");
	R.addBehavior(B);
	R.executeMsg(M, CMClass.getMsg(M, R, null, CMMsg.MSG_ENTER, null));
	check("enter", gvar("FIXJS5A") == M.Name() + ":low", "FIXJS5A='" + gvar("FIXJS5A") + "'");
	B.tick(R, Tickable.TICKID_ROOM_BEHAVIOR);
	var f = null;
	for (var c = B.getClass(); c != null && f == null; c = c.getSuperclass()) { try { f = c.getDeclaredField("lastDaypart"); } catch (e) { } }
	f.setAccessible(true);
	f.set(B, "FIXOLD");
	B.tick(R, Tickable.TICKID_ROOM_BEHAVIOR);
	check("daypart", gvar("FIXJS5B") == "FIXOLD>" + R.getArea().getTimeObj().getTODCode().name(), "FIXJS5B='" + gvar("FIXJS5B") + "'");
	R.delBehavior(B);
}

// LOAD=world:scripts/...
write(new java.io.File(W, "scripts/fixjs5.script"), "FUNCTION_PROG hi\nmpsetvar FIXTEST FIXJS5C world\n~\n");
var m = newMob("GenMob", R); m.setName("a fixjs5 crier");
var S = scriptOn(m, "LOAD=world:scripts/fixjs5.script");
S.callFunction(m, "hi", "", M);
check("world-load", gvar("FIXJS5C") == "world", "FIXJS5C='" + gvar("FIXJS5C") + "'");
m.destroy();
check("checksum", ("" + WF.checksum()) != sum0, "the checksum did not change");

// LIST SCRIPTS CUSTOM and WORLD CHECK
var SN = CMClass.getCommon("FakeSession"); SN.initialize(null, "MEMORY");
M.session().setBeingSnoopedBy(SN, true);
var L = new java.util.ArrayList(); ["LIST", "SCRIPTS", "CUSTOM"].forEach(function(w) { L.add(w); });
CMClass.getCommand("ListCmd").execute(M, L, 0);
M.session().setBeingSnoopedBy(SN, false);
check("list", has(field(SN, "bout").toString(), "world/scripts/fixjs5.script"), "LIST SCRIPTS CUSTOM: " + field(SN, "bout").toString());
write(new java.io.File(W, "js/behaviors/FixJS5Broken.js"), "//extends com.planet_ink.coffee_mud.Behaviors.NoSuchFixBehavior\nfunction ID( { return 'x'; }\n");
var parts = new java.util.HashSet(); parts.add("JS");
var found = "" + CM.core.WorldCheck.run(null, parts);
check("check", has(found, "FixJS5Broken.js: //extends com.planet_ink.coffee_mud.Behaviors.NoSuchFixBehavior, which is no class") && has(found, "FixJS5Broken.js:2:") && !has(found, "FixJS5Bell"), "found: " + found);
["js/behaviors/FixJS5Bell.js", "js/behaviors/FixJS5Broken.js", "js/properties/FixJS5Glow.js", "scripts/fixjs5.script"].forEach(function(p) { new java.io.File(W, p)["delete"](); });
done();
