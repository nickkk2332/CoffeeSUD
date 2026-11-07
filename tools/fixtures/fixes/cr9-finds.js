// CR9: a data gathering skill's ITEMXML finds with FREQ, MASK and YIELD; NOTHINGFREQ; @x1-@x4 in YIELDFORMULA and YIELD.
FIXFILE = "cr9-finds";
function s(x) { return "" + x; }
function has(t, p) { return s(t).indexOf(p) >= 0; }
var X = scratchRoom();
var V = newMob("GenMob", X);
V.setName("Fixcr9comber");
V.basePhyStats().setLevel(10); V.recoverPhyStats();
V.baseState().setMovement(5000); V.recoverMaxState(); V.resetToMaxState();
var VS = new JavaAdapter(CM.Common.FakeSession, { isFake: function() { return false; } });
VS.initialize(null, "MEMORY"); V.setSession(VS); VS.setMob(V);
V.setPlayerStats(CMClass.getCommon("DefaultPlayerStats"));
function find(name, tags) { var I = CMClass.getItem("GenItem"); I.setName(name); I.setDisplayText(name + " is here."); var x = s(CMLib.coffeeMaker().getItemXML(I)); I.destroy(); var e = x.lastIndexOf("</ITEM>"); return x.substring(0, e) + tags + x.substring(e); }
function skill(cls, xml, yieldF) { var G = CMClass.getAbility("GenGatheringSkill").copyOf(); G.setStat("CLASS", cls); G.setStat("NAME", cls); G.setStat("TRIGSTR", cls.toUpperCase()); G.setStat("ITEMXML", "<ITEMS>" + xml + "</ITEMS>"); G.setStat("YIELDFORMULA", yieldF); G.setStat("MATLIST", "WOODEN"); var A = G.copyOf(); A.setProficiency(100); V.addAbility(A); return V.fetchAbility(cls); }
function count(name) { var n = 0; for (var i = 0; i < X.numItems(); i++) if (has(X.getItem(i).Name(), name)) n++; for (var e = V.items(); e.hasMoreElements();) if (has(e.nextElement().Name(), name)) n++; return n; }
// This fixture checks yield data. Native skills keep a failure chance at proficiency 100,
// so allow an ordinary fizzle without making the yield assertion depend on one roll.
function gather(A) {
    for (var attempt = 0; attempt < 10; attempt++) {
        var C = new java.util.ArrayList(); A.invoke(V, C, null, false, 0);
        var E = V.fetchEffect(A.ID()), f = (E == null) ? null : field(E, "found");
        if (E != null) { E.unInvoke(); V.delEffect(E); }
        if (f != null) return f;
    }
    return null;
}
try {
	var A = skill("FixCr9Combing", find("a fixcr9 driftwood", "<FREQ>90</FREQ>") + find("a fixcr9 shell", "<FREQ>10</FREQ><YIELD>@x1</YIELD>")
		+ find("a fixcr9 amber", "<MASK>-LEVEL +&gt;50</MASK>") + find("a fixcr9 bottle", "<FREQ>0</FREQ>"), "1");
	var choices = call(A, "getItems", []);
	var tally = { drift: 0, shell: 0, amber: 0, bottle: 0, none: 0 };
	for (var i = 0; i < 300; i++) {
		var f = call(A, "pickFind", [V, choices]);
		var n = (f == null) ? "none" : has(f.Name(), "driftwood") ? "drift" : has(f.Name(), "shell") ? "shell" : has(f.Name(), "amber") ? "amber" : "bottle";
		tally[n]++;
	}
	check("weights", choices.size() == 4 && tally.drift > tally.shell * 3 && tally.shell > 0 && tally.amber == 0 && tally.bottle == 0 && tally.none == 0, JSON.stringify(tally));
	// the mask passes at a higher level
	V.basePhyStats().setLevel(60); V.recoverPhyStats();
	// amber weighs 1 against 100: 300 picks would miss it about one run in twenty, 3000 practically never
	var amber = 0; for (var i = 0; i < 3000; i++) { var f = call(A, "pickFind", [V, choices]); if (f != null && has(f.Name(), "amber")) amber++; }
	V.basePhyStats().setLevel(10); V.recoverPhyStats();
	check("mask", amber > 0, "" + amber);
	// NOTHINGFREQ
	A.setStat("NOTHINGFREQ", "10000");
	var none = 0; for (var i = 0; i < 100; i++) if (call(A, "pickFind", [V, choices]) == null) none++;
	check("nothing", none > 90 && s(A.getStat("NOTHINGFREQ")) == "10000" && has(A.getStat("ALLXML"), "<NOTHINGFREQ>10000</NOTHINGFREQ>"), "" + none);
	A.setStat("NOTHINGFREQ", "0");
	// the options survive the editor's rebuild of the list
	call(A, "rebuildItemXML", []);
	var xml = s(A.getStat("ITEMXML"));
	check("kept", has(xml, "<FREQ>90</FREQ>") && has(xml, "<YIELD>@x1</YIELD>") && has(xml, "<MASK>-LEVEL +&gt;50</MASK>"), xml.substring(0, 200));
	// a find's YIELD, with @x1 the gatherer's level
	var B = skill("FixCr9Shelling", find("a fixcr9 cockle", "<YIELD>@x1</YIELD>"), "1");
	var got = gather(B);
	check("find yield", got != null && count("fixcr9 cockle") == 10, (got == null ? "none" : got.Name()) + " / " + count("fixcr9 cockle"));
	// YIELDFORMULA with @x2 (proficiency) and @x4 (the room's yield left)
	var Y = scratchRoom(); Y.bringMobHere(V, false); X = Y;
	var C2 = skill("FixCr9Raking", find("a fixcr9 clam", ""), "(@x2/50)+(@x4/100)");
	var got2 = gather(C2);
	check("formula", got2 != null && count("fixcr9 clam") == 3, (got2 == null ? "none" : got2.Name()) + " / " + count("fixcr9 clam"));
	check("help", has(CMLib.help().getHelpText("GENGATHERSKILL", M, true), "NOTHINGFREQ"), "no help");
} finally {
	V.setPlayerStats(null); V.destroy();
}
done();
