// I23: Prop_Attunement PROPS HOW=REST:n RITUAL="INROOM x & SAY y" ON=EQUIP LIMIT BIND MSG MASK; UNATTUNE; ATTUNED kept.
FIXFILE = "i23-attune";
var W = CM.Items.interfaces.Wearable;
var CS = CM.Common.interfaces.CharStats;
var PS = CM.Common.interfaces.PhyStats;
var TPS = Tickable.TICKID_PROPERTY_SPECIAL;
function s(x) { return "" + x; }
function has(t, p) { return s(t).replace(/\s+/g, " ").indexOf(p) >= 0; }
var X = CMClass.getAreaType("StdArea"); X.setName("Fixi23Grove");
X.basePhyStats().setDisposition(X.basePhyStats().disposition() | PS.IS_UNSAVABLE); X.recoverPhyStats();
CMLib.map().addArea(X);
var GROVE = CMClass.getLocale("StdRoom"); GROVE.setRoomID("Fixi23Grove#0"); GROVE.setDisplayText("Fixi23 grove"); GROVE.setArea(X);
var V = CMClass.getMOB("GenMob"); V.setName("Fixi23pip"); V.bringToLife(GROVE, true);
V.setPlayerStats(CMClass.getCommon("DefaultPlayerStats"));
var VS = CMClass.getCommon("FakeSession"); VS.initialize(null, "MEMORY"); V.setSession(VS); VS.setMob(V);
var mark = 0;
function out() { var all = s(field(VS, "bout").toString()); var o = all.substring(mark); mark = all.length; return o.replace(/\x1b\[[0-9;]*m/g, "").replace(/\^./g, ""); }
function thing(name, slot, parms) {
	var I = CMClass.getItem("GenItem"); I.setName(name); I.setRawLogicalAnd(false); I.setRawProperLocationBitmap(slot); V.addItem(I);
	var P = CMClass.getAbility("Prop_Attunement"); P.setMiscText(parms); I.addNonUninvokableEffect(P);
	return { I: I, P: P };
}
var dex0 = V.charStats().getStat(CS.STAT_DEXTERITY);
// rest with it
var cloak = thing("a fixi23 elven cloak", W.WORN_ABOUT_BODY, "PROPS=\"Prop_WearAdjuster(dex+2)\" HOW=REST:3 LIMIT=2 MSG=\"The cloak settles about you.\"");
cloak.I.wearAt(W.WORN_ABOUT_BODY); V.recoverCharStats();
var inert = V.charStats().getStat(CS.STAT_DEXTERITY) == dex0 && has(cloak.P.accountForYourself(), "Requires attunement");
V.basePhyStats().setDisposition(V.basePhyStats().disposition() | PS.IS_SITTING); V.recoverPhyStats();
out();
cloak.P.tick(cloak.P, TPS); cloak.P.tick(cloak.P, TPS); cloak.P.tick(cloak.P, TPS);
V.recoverCharStats();
var said = out();
check("rest", inert && s(cloak.P.attunedTo()) == "Fixi23pip" && V.charStats().getStat(CS.STAT_DEXTERITY) == dex0 + 2 && has(said, "The cloak settles about you.") && has(cloak.P.text(), "ATTUNED"),
	"inert " + inert + " to '" + cloak.P.attunedTo() + "' dex " + dex0 + "->" + V.charStats().getStat(CS.STAT_DEXTERITY) + " '" + said + "'");
V.basePhyStats().setDisposition(V.basePhyStats().disposition() & ~PS.IS_SITTING); V.recoverPhyStats();
// a ritual: in the grove, the word
var staff = thing("a fixi23 druid staff", W.WORN_HELD, "PROPS=\"Prop_WearAdjuster(wis+1)\" RITUAL=\"INROOM Fixi23Grove#0 & SAY attune\" LIMIT=2");
CMLib.commands().postSay(V, null, "I attune to thee", false, false);
check("ritual", s(staff.P.attunedTo()) == "Fixi23pip", "staff to '" + staff.P.attunedTo() + "'");
// a third: over the limit
var ring = thing("a fixi23 ring", W.WORN_LEFT_FINGER, "PROPS=\"Prop_WearAdjuster(str+1)\" ON=EQUIP LIMIT=2");
out();
GROVE.send(V, CMClass.getMsg(V, ring.I, null, CMMsg.MSG_WEAR, null));
said = out();
check("limit", s(ring.P.attunedTo()) == "" && has(said, "more than 2"), "ring to '" + ring.P.attunedTo() + "' '" + said + "'");
// UNATTUNE frees a place
var l = new java.util.ArrayList(); ["UNATTUNE", "staff"].forEach(function (w) { l.add(w); });
CMClass.getCommand("Unattune").execute(V, l, 0);
GROVE.send(V, CMClass.getMsg(V, ring.I, null, CMMsg.MSG_WEAR, null));
check("unattune", s(staff.P.attunedTo()) == "" && s(ring.P.attunedTo()) == "Fixi23pip", "staff '" + staff.P.attunedTo() + "' ring '" + ring.P.attunedTo() + "'");
// bound: inert for another
var other = CMClass.getMOB("GenMob"); other.setName("Fixi23thief"); other.bringToLife(GROVE, true);
var amul = thing("a fixi23 bound amulet", W.WORN_NECK, "PROPS=\"Prop_WearAdjuster(con+1)\" ON=EQUIP BIND=true LIMIT=5");
GROVE.send(V, CMClass.getMsg(V, amul.I, null, CMMsg.MSG_WEAR, null));
amul.P.tick(amul.P, TPS);
var mine = amul.I.fetchEffect("Prop_WearAdjuster") != null;
other.moveItemTo(amul.I); amul.P.tick(amul.P, TPS);
check("bind", mine && amul.I.fetchEffect("Prop_WearAdjuster") == null, "mine " + mine + " theirs " + (amul.I.fetchEffect("Prop_WearAdjuster") != null));
check("help", has(CMLib.help().getHelpText("PROP_ATTUNEMENT", M, true), "UNATTUNE") && has(CMLib.help().getHelpText("UNATTUNE", M, false), "Lets go of your bond"), "no help");

V.setPlayerStats(null); V.destroy(); other.destroy();
CMLib.map().obliterateMapArea(X);
done();
