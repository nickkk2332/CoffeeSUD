// N12: Prop_NPCLines: an NPC's own words for its stock lines (postSay): short names and quoted lines, alternatives,
// @x values, $n, a [mask] for one customer, an empty text for silence, and an area's lines for its whole town.
FIXFILE = "n12-npclines";
var NL = CM.core.NpcLines;
var PS = CM.Common.interfaces.PhyStats;
function s(x) { return "" + x; }
function has(text, part) { return s(text).replace(/\s+/g, " ").indexOf(part) >= 0; }
var R = scratchRoom();
function customer(name) {
	var m = newMob("GenMob", R);
	m.setName(name);
	var S = CMClass.getCommon("FakeSession"); S.initialize(null, "MEMORY"); m.setSession(S); S.setMob(m);
	return { mob: m, s: S, mark: 0 };
}
var pip = customer("Fixn12pip"), bob = customer("Fixn12bob");
function out(c) { var all = s(field(c.s, "bout").toString()); var o = all.substring(c.mark); c.mark = all.length; return o.replace(/\x1b\[[0-9;]*m/g, "").replace(/\^./g, ""); }
var smith = newMob("GenShopkeeper", R);
smith.setName("Fixn12smith");
var P = CMClass.getAbility("Prop_NPCLines");
P.setMiscText("NOTBUYING=\"Fixn12 bah, junk.\"|\"Fixn12 take that rubbish elsewhere.\" STOLEN=\"Fixn12 I know where that came from, $n.\" "
	+ "\"I'm afraid my prices are firm.\"=\"Fixn12 not a copper less.\" OFFER=\"Fixn12 @x2? I'd give @x1, no more.\" "
	+ "NOTBUYING=\"Fixn12 for you, friend, still no.\"[-NAMES +Fixn12pip] ACCOUNTCLOSED=\"\"");
smith.addNonUninvokableEffect(P);
function says(c, text) { out(c); CMLib.commands().postSay(smith, c.mob, text, false, false); return out(c); }

check("masked", has(says(pip, "I'm not interested."), "Fixn12 for you, friend, still no."), "pip did not get his own line");
var b = says(bob, "I'm not interested.");
check("alternatives", (has(b, "Fixn12 bah, junk.") || has(b, "Fixn12 take that rubbish elsewhere.")) && !has(b, "I'm not interested"), b);
check("name", has(says(pip, "I don't buy stolen goods."), "Fixn12 I know where that came from, Fixn12pip."), "no $n");
check("quoted", has(says(pip, "I'm afraid my prices are firm."), "Fixn12 not a copper less."), "the quoted line was not reworded");
check("values", has(says(pip, CMLib.lang().L("I'll give you @x1 for @x2.", "5 gold", "a fixn12 cup")), "Fixn12 a fixn12 cup? I'd give 5 gold, no more."), "the values");
check("silent", s(says(pip, "I have closed that account. Thanks for your business.")).trim() == "", "an empty line was said");
check("unlisted", has(says(pip, "Thank you for your trust."), "Thank you for your trust."), "a line it has no words for changed");
check("stockline", s(NL.stockLine("TIMEUP")) == "Your time is up.  Goodbye!" && NL.stockLine("NOSUCH") == null, "stockLine");

// a town's lines, and a smith of its own over them
var X = CMClass.getAreaType("StdArea");
X.setName("Fixn12Town");
X.basePhyStats().setDisposition(X.basePhyStats().disposition() | PS.IS_UNSAVABLE);
X.recoverPhyStats();
CMLib.map().addArea(X);
var XR = CMClass.getLocale("StdRoom");
XR.setRoomID("Fixn12Town#0");
XR.setArea(X);
var TP = CMClass.getAbility("Prop_NPCLines");
TP.setMiscText("FIRM=\"Fixn12 the town's prices are firm.\" STOLEN=\"Fixn12 the watch will hear of this.\"");
X.addNonUninvokableEffect(TP);
var clerk = newMob("GenShopkeeper", XR);
clerk.setName("Fixn12clerk");
XR.bringMobHere(pip.mob, false);
out(pip);
CMLib.commands().postSay(clerk, pip.mob, "I'm afraid my prices are firm.", false, false);
check("town", has(out(pip), "Fixn12 the town's prices are firm."), "the area's line");
XR.bringMobHere(smith, false);
check("own-first", has(says(pip, "I'm afraid my prices are firm."), "Fixn12 not a copper less."), "the town's line over the smith's own");
check("town-fills", has(says(pip, "I don't buy stolen goods."), "Fixn12 I know where that came from"), "the smith's own STOLEN");
CMLib.commands().postSay(clerk, pip.mob, "I don't buy stolen goods.", false, false);
check("town-stolen", has(out(pip), "Fixn12 the watch will hear of this."), "the town's STOLEN");

[smith, clerk].forEach(function (m) { m.destroy(); });
[pip, bob].forEach(function (c) { c.mob.destroy(); });
CMLib.map().obliterateMapArea(X);
R.destroy();
done();
