// I26: ITEMWEAR and ITEMBREAK in coffeemud.ini; Prop_Durability MULT UNBREAKABLE NOREPAIR BROKENNAME BREAK, (worn);
// Prop_Broken: unusable until mended.
FIXFILE = "i26-durability";
var W = CM.Items.interfaces.Wearable;
var TPS = Tickable.TICKID_PROPERTY_SPECIAL;
function s(x) { return "" + x; }
function has(t, p) { return s(t).replace(/\s+/g, " ").indexOf(p) >= 0; }
var page = CMProps.instance();
var oldWear = page.getProperty("ITEMWEAR"), oldBreak = page.getProperty("ITEMBREAK");
var R = scratchRoom();
var V = newMob("GenMob", R); V.setName("Fixi26pip");
function weapon(name, dur) {
	var I = CMClass.getWeapon("GenWeapon"); I.setName(name); I.basePhyStats().setDamage(10); I.recoverPhyStats(); V.addItem(I); I.wearAt(W.WORN_WIELD);
	if (dur != null) { var P = CMClass.getAbility("Prop_Durability"); P.setMiscText(dur); I.addNonUninvokableEffect(P); I.recoverPhyStats(); }
	return I;
}
function hit(I, n) { CMLib.combat().postItemDamage(V, I, null, n, CMMsg.TYP_JUSTICE, null); }
function amb(I) { return s(java.util.Arrays.toString(I.phyStats().ambiances())); }
// broken, not gone, and mended
var heir = weapon("an heirloom fixi26 sword", "BROKENNAME=\"a shattered %s\"");
hit(heir, 150);
var B = heir.fetchEffect("Prop_Broken");
check("break", (!heir.amDestroyed()) && B != null && s(heir.Name()) == "a shattered heirloom fixi26 sword" && heir.amWearingAt(W.IN_INVENTORY) && heir.phyStats().damage() == 0 && has(amb(heir), "(broken)"),
	"gone " + heir.amDestroyed() + " broken " + (B != null) + " name " + heir.Name() + " damage " + heir.phyStats().damage() + " " + amb(heir));
var wield = R.okMessage(V, CMClass.getMsg(V, heir, null, CMMsg.MSG_WIELD, null));
heir.setUsesRemaining(100); if (B != null) B.tick(B, TPS);
check("mend", (!wield) && heir.fetchEffect("Prop_Broken") == null && s(heir.Name()) == "an heirloom fixi26 sword", "wielded " + wield + " name " + heir.Name());
// MULT, UNBREAKABLE, (worn)
var half = weapon("a fixi26 half sword", "MULT=50");
hit(half, 20);
var stone = weapon("a fixi26 stone sword", "UNBREAKABLE");
hit(stone, 500);
check("mult", half.usesRemaining() == 90 && stone.usesRemaining() == 100 && (!stone.amDestroyed()), "half " + half.usesRemaining() + " stone " + stone.usesRemaining());
hit(half, 120); half.recoverPhyStats();
check("worn", half.usesRemaining() == 30 && has(amb(half), "(worn)"), "uses " + half.usesRemaining() + " " + amb(half));
// the ini: no wear; break instead of destroy for all
page.setProperty("ITEMWEAR", "0");
var plain = weapon("a fixi26 plain sword", null);
hit(plain, 30);
var noWear = plain.usesRemaining() == 100;
page.setProperty("ITEMWEAR", "100"); page.setProperty("ITEMBREAK", "BREAK");
hit(plain, 200);
check("ini", noWear && (!plain.amDestroyed()) && plain.fetchEffect("Prop_Broken") != null, "no wear " + noWear + " gone " + plain.amDestroyed());
page.setProperty("ITEMBREAK", "DESTROY");
var frail = weapon("a fixi26 frail sword", null);
hit(frail, 200);
check("destroy", frail.amDestroyed(), "a worn out item stayed");
// NOREPAIR
var smith = CMClass.getMOB("GenMob"); smith.setName("Fixi26smith"); smith.addBehavior(CMClass.getBehavior("ItemMender")); smith.bringToLife(R, true);
var cursed = weapon("a fixi26 cursed blade", "NOREPAIR");
cursed.unWear();
var given = R.okMessage(V, CMClass.getMsg(V, smith, cursed, CMMsg.MSG_GIVE, null));
check("norepair", !given, "the smith took it");
check("help", has(CMLib.help().getHelpText("PROP_DURABILITY", M, true), "UNBREAKABLE"), "no help");

page.setProperty("ITEMWEAR", oldWear == null ? "100" : oldWear);
page.setProperty("ITEMBREAK", oldBreak == null ? "DESTROY" : oldBreak);
V.destroy(); smith.destroy();
done();
