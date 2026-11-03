// CR15: world/resources.txt: a subtype's row (display name, value, description, smell, effects, nourishment) on any raw resource of its base given that subtype; bundles keep it; an item loaded from XML keeps its own.
FIXFILE = "cr15-variants";
function s(x) { return "" + x; }
function has(t, p) { return s(t).indexOf(p) >= 0; }
var RM = CM.Items.interfaces.RawMaterial, MsgC = CM.Common.interfaces.CMMsg;
var VF = new java.io.File(WorldFiles.worldDir(), "resources.txt");
var X = scratchRoom();
var V = newMob("GenMob", X);
V.setName("Fixcr15herbalist");
var VS = new JavaAdapter(CM.Common.FakeSession, { isFake: function() { return false; } });
VS.initialize(null, "MEMORY"); V.setSession(VS); VS.setMob(V);
V.setPlayerStats(CMClass.getCommon("DefaultPlayerStats"));
var seen = 0;
function said() { var all = s(field(VS, "bout")); var o = all.substring(seen); seen = all.length; return o; }
var things = [];
function mk(code, sub) { var I = sub == null ? CMLib.materials().makeItemResource(code) : CMLib.materials().makeItemResource(code, sub); things.push(I); return I; }
try {
	var plain = mk(RM.RESOURCE_HERBS, "moonpetal");
	check("no file", has(plain.name(), "moonpetal") && !has(plain.name(), "silvery") && plain.fetchEffect("Spell_Light") == null, plain.name());
	java.nio.file.Files.write(VF.toPath(), new java.lang.String("SUBTYPE\tBASE_RESOURCE\tDISPLAY_NAME\tVALUE\tWEIGHT_PER_UNIT\tDESCRIPTION\tSMELL\tEFFECTS\tNOURISHMENT\n"
		+ "# fixcr15\nmoonpetal\tHERBS\tsilvery moonpetal\t40\t\tA pale petal that glows in the dark.\tsweet night-blooming\tSpell_Light\t25\n"
		+ "fixcrember\tWOODEN\tember-wood\t9\n").getBytes("UTF-8"));
	var a = mk(RM.RESOURCE_HERBS, "moonpetal");
	check("made", has(a.name(), "silvery moonpetal") && a.baseGoldValue() == 40 && s(a.description()) == "A pale petal that glows in the dark." && a.fetchEffect("Spell_Light") != null
		&& (a instanceof CM.Items.interfaces.Food) && a.nourishment() == 25, a.name() + " / " + a.baseGoldValue() + " / " + a.description());
	// a subtype set after it is made, as a gathering skill does
	var b = mk(RM.RESOURCE_HERBS, null);
	b.setSubType("MOONPETAL");
	check("set later", has(b.name(), "silvery moonpetal") && b.baseGoldValue() == 40 && b.fetchEffect("Spell_Light") != null, b.name());
	// another base: not this row; a family base: any resource of it
	var c = mk(RM.RESOURCE_OAK, "moonpetal"), d = mk(RM.RESOURCE_PINE, "fixcrember");
	check("base", !has(c.name(), "silvery") && has(d.name(), "ember-wood") && d.baseGoldValue() == 9, c.name() + " / " + d.name());
	// a bundle keeps its name
	a.basePhyStats().setWeight(3); a.recoverPhyStats(); CMLib.materials().adjustResourceName(a);
	check("bundle", has(a.name(), "silvery moonpetal") && has(a.name(), "3"), a.name());
	// SNIFF
	X.addItem(a);
	said();
	X.send(V, CMClass.getMsg(V, a, null, MsgC.MSG_SNIFF, null));
	var sn = said();
	check("smell", has(sn, "sweet night-blooming smell"), sn);
	// loaded from XML: it keeps what it was saved with
	var e = mk(RM.RESOURCE_HERBS, "moonpetal");
	e.setName("a fixcr15 keepsake petal"); e.setBaseValue(7);
	var copy = CMClass.getItem("GenFoodResource"); copy.setMiscText(e.text()); copy.recoverPhyStats(); things.push(copy);
	check("loaded", s(copy.Name()) == "a fixcr15 keepsake petal" && copy.baseGoldValue() == 7 && s(copy.getSubType()) == "MOONPETAL", copy.Name() + " / " + copy.baseGoldValue());
	check("help", has(CMLib.help().getHelpText("RESOURCEVARIANTS", M, true), "NOURISHMENT"), "no help");
} finally {
	things.forEach(function (I) { I.destroy(); });
	VF["delete"]();
	V.setPlayerStats(null); V.destroy();
}
done();
