// G6: Prop_LootTable: ON=DEATH into the corpse (ROLLS, GOLD), ON=OPEN once until the contents reset (ROLLED, kept in its text), CHANCE, catalog caps; the sample loot.xml.
FIXFILE = "g6-loot";
var PS = CM.Common.interfaces.PhyStats;
function s(x) { return "" + x; }
function has(t, p) { return s(t).replace(/\s+/g, " ").indexOf(p) >= 0; }
var F = new java.io.File(new java.io.File(WorldFiles.worldDir(), "resources/randareas"), "fixg6.xml");
F.getParentFile().mkdirs();
java.nio.file.Files.write(F.toPath(), new java.lang.String('<AREADATA>'
	+ '<ITEM id="fixg6_loot" class="GenItem" level="1" name="a fixg6 gem for $player_level from $mob_level" />'
	+ '<ITEM id="fixg6_fang" class="catalog" name="Fixg6 Fang" />'
	+ '</AREADATA>').getBytes("UTF-8"));
var X = CMClass.getAreaType("StdArea"); X.setName("Fixg6Warrens");
X.basePhyStats().setDisposition(X.basePhyStats().disposition() | PS.IS_UNSAVABLE); X.recoverPhyStats();
CMLib.map().addArea(X);
var R = CMClass.getLocale("StdRoom"); R.setRoomID("Fixg6Warrens#0"); R.setDisplayText("Fixg6 cave"); R.setArea(X);
var fang = null;
function inside(C) { var o = []; for (var i = 0; i < R.numItems(); i++) { var I = R.getItem(i); if (I.container() == C) o.push(s(I.Name())); } return o; }
function lootMob(name, text) {
	var m = CMClass.getMOB("GenMob"); m.setName(name); m.basePhyStats().setLevel(4); m.recoverPhyStats(); m.bringToLife(R, true);
	var P = CMClass.getAbility("Prop_LootTable"); P.setMiscText(text); m.addNonUninvokableEffect(P);
	return m;
}
function corpseOf(name) { for (var i = 0; i < R.numItems(); i++) { var I = R.getItem(i); if (I instanceof CM.Items.interfaces.DeadBody && has(I.Name(), name)) return I; } return null; }
try {
	var K = newMob("GenMob", R); K.setName("Fixg6hunter");
	// DEATH: two rolls and some money into the corpse
	var g = lootMob("Fixg6goblin", "FILE=randareas/fixg6.xml ID=fixg6_loot ROLLS=2 GOLD=5");
	CMLib.combat().postDeath(K, g, null);
	var body = corpseOf("Fixg6goblin"), inBody = inside(body);
	var gems = inBody.filter(function (n) { return n == "a fixg6 gem for 4 from 4"; }).length, coins = 0;
	for (var i = 0; i < R.numItems(); i++) if (R.getItem(i).container() == body && R.getItem(i) instanceof CM.Items.interfaces.Coins) coins++;
	check("death", body != null && gems == 2 && coins >= 1, "corpse " + body + " holds " + inBody.join(", "));
	// CHANCE=0: nothing
	var g2 = lootMob("Fixg6runt", "FILE=randareas/fixg6.xml ID=fixg6_loot ROLLS=2 CHANCE=0");
	CMLib.combat().postDeath(K, g2, null);
	var body2 = corpseOf("Fixg6runt");
	check("chance", body2 != null && inside(body2).filter(function (n) { return has(n, "fixg6 gem"); }).length == 0, "runt's corpse holds " + inside(body2).join(", "));
	// OPEN: once, until its contents reset
	var V = CMClass.getMOB("GenMob"); V.setName("Fixg6pip"); V.basePhyStats().setLevel(2); V.recoverPhyStats(); V.bringToLife(R, true); V.setPlayerStats(CMClass.getCommon("DefaultPlayerStats"));
	var chest = CMClass.getItem("GenContainer"); chest.setName("a fixg6 chest"); R.addItem(chest);
	var CP = CMClass.getAbility("Prop_LootTable"); CP.setMiscText("FILE=randareas/fixg6.xml ID=fixg6_loot ROLLS=1?1"); chest.addNonUninvokableEffect(CP);
	function open() { CP.executeMsg(chest, CMClass.getMsg(V, chest, null, CMMsg.MSG_OPEN, "<S-NAME> open(s) <T-NAME>.")); return inside(chest).length; }
	var o1 = open(), o2 = open(), text = s(CP.text());
	CP.setStat("ROLLED", "FALSE");
	var o3 = open();
	check("open", o1 == 1 && o2 == 1 && has(text, "ROLLED=TRUE") && o3 == 2 && s(CP.getStat("ROLLED")) == "TRUE" && has(inside(chest)[0], "a fixg6 gem for 2"), o1 + "/" + o2 + "/" + o3 + " " + text + " " + inside(chest).join(", "));
	// a catalog item keeps its cap
	fang = CMClass.getItem("GenItem"); fang.setName("Fixg6 Fang");
	CMLib.catalog().addCatalog(fang);
	var cd = CMLib.catalog().getCatalogItemData("Fixg6 Fang"), before = cd.numReferences();
	cd.setCap(before + 1); // one more may come into the world
	var chest2 = CMClass.getItem("GenContainer"); chest2.setName("a fixg6 coffer"); R.addItem(chest2);
	var CP2 = CMClass.getAbility("Prop_LootTable"); CP2.setMiscText("FILE=randareas/fixg6.xml ID=fixg6_fang ROLLS=3"); chest2.addNonUninvokableEffect(CP2);
	CP2.executeMsg(chest2, CMClass.getMsg(V, chest2, null, CMMsg.MSG_OPEN, "<S-NAME> open(s) <T-NAME>."));
	var fangs = inside(chest2).filter(function (n) { return n == "Fixg6 Fang"; }).length;
	check("cap", fangs == 1 && cd.numReferences() == before + 1, "fangs " + fangs + " refs " + before + " -> " + cd.numReferences() + ": " + inside(chest2).join(", "));
	// the sample tables
	var L = CM.core.GenCalls.items("randareas/loot.xml", "goblin", null, null, R), L2 = CM.core.GenCalls.items("randareas/loot.xml", "chest", null, null, R);
	check("sample", L.size() == 1 && L2.size() == 1 && L.get(0).Name().length() > 3, "goblin " + L.size() + " chest " + L2.size());
	for (var j = 0; j < L.size(); j++) L.get(j).destroy(); for (var j2 = 0; j2 < L2.size(); j2++) L2.get(j2).destroy();
	check("help", has(CMLib.help().getHelpText("PROP_LOOTTABLE", M, true), "ROLLS="), "no help");
} finally {
	F["delete"]();
	for (var i = R.numItems() - 1; i >= 0; i--) { var I = R.getItem(i); if (I != null) I.destroy(); }
	for (var k = R.numInhabitants() - 1; k >= 0; k--) { var mm = R.fetchInhabitant(k); if (mm != null) mm.destroy(); }
	if (fang != null) CMLib.catalog().delCatalog(fang);
	CMLib.map().delArea(X);
}
done();
