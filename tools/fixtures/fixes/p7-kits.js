// P7: starting kits in world/kits ("starting" mask, "choose" groups): the KIT creation step, every kit that fits given, the chosen ones too.
FIXFILE = "p7-kits";
function s(x) { return "" + x; }
function has(t, p) { return s(t).indexOf(p) >= 0; }
function write(f, text) { f.getParentFile().mkdirs(); java.nio.file.Files.write(f.toPath(), new java.lang.String(text).getBytes("UTF-8")); }
var SK = CM.core.StartingKits;
var KD = SK.dir();
var files = {
	"fixp7axe": "starting all\nchoose fixweapon\ntitle Fixp7 two-hander\nitem GenWeapon\n",
	"fixp7sword": "# for humans\nstarting -RACE +Human\nchoose fixweapon\ntitle Fixp7 sword and shield\nitem GenWeapon\nitem 2 catalog:a fixp7 ration\ngold 20\n",
	"fixp7pick": "starting -RACE +Dwarf\nitem GenItem\n",
	"fixp7noble": "starting -TATTOO +ORIGIN_FIXNOBLE\nwear no\nitem GenArmor\nability Skill_Recall 50\n",
	"fixp7test": "level 5\nitem GenItem\n" };
var R = scratchRoom();
function hero(name, race) {
	var h = newMob("GenMob", R);
	h.setName(name);
	h.baseCharStats().setMyRace(CMClass.getRace(race)); h.recoverCharStats();
	h.setPlayerStats(CMClass.getCommon("DefaultPlayerStats"));
	return h;
}
var H = hero("Fixp7hero", "Human"), D = hero("Fixp7dwarf", "Dwarf");
var VS = new JavaAdapter(CM.Common.FakeSession, { isFake: function() { return false; } });
VS.initialize(null, "MEMORY"); H.setSession(VS); VS.setMob(H);
var seen = 0;
function said() { var all = s(field(VS, "bout")); var o = all.substring(seen); seen = all.length; return o.replace(/\s+/g, " "); }
var ration = CMClass.getItem("GenItem"); ration.setName("a fixp7 ration"); CMLib.catalog().addCatalog(ration);
var CC = CMLib.login(), LSI = null;
CC.getClass().getDeclaredClasses().forEach(function (c) { if (s(c.getSimpleName()) == "LoginSessionImpl") LSI = c; });
var ctor = null; LSI.getDeclaredConstructors().forEach(function (k) { if (k.getParameterCount() == 1) ctor = k; }); ctor.setAccessible(true);
var lo = ctor.newInstance(CC);
function lset(name, v) { var f = LSI.getDeclaredField(name); f.setAccessible(true); f.set(lo, v); }
function lget(name) { var f = LSI.getDeclaredField(name); f.setAccessible(true); return f.get(lo); }
function step(name) {
	var ms = CC.getClass().getDeclaredMethods();
	for (var i = 0; i < ms.length; i++) if (s(ms[i].getName()) == name) { ms[i].setAccessible(true); return ms[i].invoke(CC, lo, VS); }
	throw "no " + name;
}
function count(m, part) { var n = 0; for (var i = 0; i < m.numItems(); i++) if (has(m.getItem(i).Name(), part) || has(m.getItem(i).ID(), part)) n++; return n; }
try {
	for (var f in files) write(new java.io.File(KD, f + ".kit"), files[f]);
	var all = SK.all(), names = []; for (var i = 0; i < all.size(); i++) names.push(s(all.get(i).name));
	check("starting kits", names.join(",") == "fixp7axe,fixp7noble,fixp7pick,fixp7sword" || (names.indexOf("fixp7test") < 0 && names.indexOf("fixp7axe") >= 0 && names.indexOf("fixp7sword") >= 0), names.join(","));
	check("groups", s(SK.groups(H)) == "[FIXWEAPON]" && SK.inGroup(H, "FIXWEAPON").size() == 2 && SK.inGroup(D, "FIXWEAPON").size() == 1, s(SK.groups(H)));
	// the KIT step, for the human: a menu
	lset("mob", H); lset("kitGroup", java.lang.Integer.valueOf(0)); lget("kits").clear();
	said();
	var r1 = step("charcrKitStart"), o1 = said();
	check("menu", s(r1) == "INPUT_REQUIRED" && s(lget("state")) == "CHARCR_KITPICKED" && has(o1, "Fixp7 two-hander") && has(o1, "Fixp7 sword and shield"), o1.substring(0, 200));
	lset("lastInput", "bow");
	step("charcrKitPicked"); var o2 = said();
	check("not one", s(lget("state")) == "CHARCR_KITSTART" && has(o2, "is not one of the kits"), o2);
	lset("lastInput", "fixp7 sword");
	step("charcrKitPicked");
	step("charcrKitStart");
	check("chosen", s(lget("kits")) == "[fixp7sword]" && s(lget("state")) == "CHARCR_FACTIONNEXT" && lget("index") == -1, s(lget("kits")) + " " + s(lget("state")));
	// the dwarf has only one: taken, with no question
	lset("mob", D); lset("kitGroup", java.lang.Integer.valueOf(0)); lget("kits").clear();
	var r3 = step("charcrKitStart");
	check("one taken", r3 == null && s(lget("kits")) == "[fixp7axe]" && s(lget("state")) == "CHARCR_FACTIONNEXT", s(lget("kits")));
	// what they get
	H.addTattoo("ORIGIN_FIXNOBLE");
	var why = SK.apply(H, java.util.Arrays.asList("fixp7sword"));
	var weapon = null; for (var i = 0; i < H.numItems(); i++) if (s(H.getItem(i).ID()) == "GenWeapon") weapon = H.getItem(i);
	var armor = null; for (var i = 0; i < H.numItems(); i++) if (s(H.getItem(i).ID()) == "GenArmor") armor = H.getItem(i);
	check("given", why.size() == 0 && count(H, "GenWeapon") == 1 && count(H, "fixp7 ration") == 2 && count(H, "GenItem") == 2 /* the rations: no pick */
		&& CMLib.beanCounter().getTotalAbsoluteNativeValue(H) == 20, s(why) + " " + H.numItems());
	check("worn and not", weapon != null && !weapon.amWearingAt(CM.Items.interfaces.Wearable.IN_INVENTORY) && armor != null && armor.amWearingAt(CM.Items.interfaces.Wearable.IN_INVENTORY), s(weapon) + " " + s(armor));
	check("noble's ability", H.fetchAbility("Skill_Recall") != null && H.fetchAbility("Skill_Recall").proficiency() == 50, "none");
	SK.apply(D, java.util.Arrays.asList("fixp7axe"));
	check("dwarf", count(D, "GenWeapon") == 1 && count(D, "GenItem") == 1 && count(D, "GenArmor") == 0, D.numItems());
	// KIT still reads the files, and takes a count
	var KC = CMClass.getCommand("KitCmd").getClass();
	var K = KC.getMethod("readKit", java.lang.String).invoke(null, "fixp7sword");
	check("kit reads it", K.problems.size() == 0, s(K.problems));
	check("help", has(CMLib.help().getHelpText("KIT", M, true), "starting"), "no help");
} finally {
	for (var f in files) { var F = new java.io.File(KD, f + ".kit"); if (F.isFile()) F["delete"](); }
	[H, D].forEach(function (m) { m.setSession(null); m.setPlayerStats(null); m.destroy(); });
	CMLib.catalog().delCatalog(ration);
}
done();
