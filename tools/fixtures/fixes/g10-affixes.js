// G10: affixes as data (world/affixes.xml, else randareas/affixes.xml): prefix and suffix by kind and level, effects, level and value, cursed; enchant(), AFFIXES= on a template ITEM.
FIXFILE = "g10-affixes";
var AF = CM.core.Affixes;
function s(x) { return "" + x; }
function has(t, p) { return s(t).replace(/\s+/g, " ").indexOf(p) >= 0; }
var W = new java.io.File(WorldFiles.worldDir(), "affixes.xml");
var T = new java.io.File(new java.io.File(WorldFiles.worldDir(), "resources/randareas"), "fixg10.xml");
T.getParentFile().mkdirs();
function write(F, t) { java.nio.file.Files.write(F.toPath(), new java.lang.String(t).getBytes("UTF-8")); }
var made = [];
function item(cls, name, level, value) { var I = CMClass.getItem(cls); I.setName(name); I.setDisplayText(name + " lies here."); I.basePhyStats().setLevel(level); I.setBaseValue(value); I.recoverPhyStats(); made.push(I); return I; }
function effects(I) { var o = []; for (var e = I.effects(); e.hasMoreElements();) { var A = e.nextElement(); o.push(s(A.ID()) + "(" + s(A.text()) + ")"); } return o.join(","); }
try {
	// the stock file
	AF.reload();
	var sw = item("GenWeapon", "a short sword", 8, 100), before = s(sw.Name());
	var n = AF.apply(sw, 2);
	check("stock", AF.available() && n >= 1 && s(sw.Name()) != before && has(sw.displayText(), s(sw.Name())) && sw.basePhyStats().level() >= 8, n + " " + sw.Name() + " / " + sw.displayText());
	// the world's own: kinds, level, power, value, effects, an and a, prefix and suffix, cursed
	write(W, '<AFFIXES>'
		+ '<AFFIX id="glow" name="Fixg10 Glowing" pos="prefix" kinds="item" level="1-99" pickweight="5" power="3"><AFFECT class="Prop_WearAdjuster" parms="armor+1" /></AFFIX>'
		+ '<AFFIX id="ash" name="Fixg10 Ash" pos="suffix" kinds="any" level="1-99" pickweight="5" cursed="true"><AFFECT class="Prop_WearAdjuster" parms="strength-1" /></AFFIX>'
		+ '<AFFIX id="blade" name="Fixg10 Edged" pos="prefix" kinds="weapon" level="1-99" pickweight="5" />'
		+ '<AFFIX id="high" name="Fixg10 Lofty" pos="prefix" kinds="any" level="50-99" pickweight="5" />'
		+ '</AFFIXES>');
	AF.reload();
	var st = item("GenItem", "a stone", 5, 100);
	AF.apply(st, 2);
	check("world", s(st.Name()) == "a Fixg10 Glowing stone of Fixg10 Ash" && st.basePhyStats().level() == 9 && st.baseGoldValue() == 143
		&& has(effects(st), "Prop_WearAdjuster(armor+1 strength-1)") && !CMLib.flags().isRemovable(st)
		&& s(st.displayText()) == "a Fixg10 Glowing stone of Fixg10 Ash lies here.", st.Name() + " lvl " + st.basePhyStats().level() + " value " + st.baseGoldValue() + " " + effects(st));
	write(W, '<AFFIXES><AFFIX id="icy" name="Icy" pos="prefix" kinds="any" level="1-99" /></AFFIXES>');
	AF.reload();
	var st2 = item("GenItem", "a stone", 5, 100); AF.apply(st2, 1);
	check("article", s(st2.Name()) == "an Icy stone", st2.Name());
	// enchant() uses them; and a template ITEM's AFFIXES=
	var st3 = item("GenItem", "a pebble", 5, 10);
	CMLib.itemBuilder().enchant(st3, 100);
	write(T, '<AREADATA><ITEM id="fixg10_ring" class="GenItem" level="5" name="a fixg10 ring" AFFIXES="1" /></AREADATA>');
	var L = CM.core.GenCalls.items("randareas/fixg10.xml", "fixg10_ring", null, null, M.location());
	check("enchant", s(st3.Name()) == "an Icy pebble" && L.size() == 1 && s(L.get(0).Name()) == "an Icy fixg10 ring", st3.Name() + " / " + (L.size() ? L.get(0).Name() : "none"));
	made.push(L.get(0));
	check("help", has(CMLib.help().getHelpText("AFFIXES", M, true), "pickweight"), "no help");
} finally {
	if (W.exists()) W["delete"]();
	if (T.exists()) T["delete"]();
	AF.reload();
	made.forEach(function (I) { I.destroy(); });
}
done();
