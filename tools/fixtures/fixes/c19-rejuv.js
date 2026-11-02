// C19: Prop_RejuvWhen WHEN="mask" ELSE=WAIT|NEVER|REPLACE:name, on a mob's rejuv and an item's (ItemRejuv).
FIXFILE = "c19-rejuv";
function s(x) { return "" + x; }
var SP = CMClass.getMOB("GenMob"); SP.setName("a fixc19 settler"); SP.recoverPhyStats(); CMLib.catalog().addCatalog(SP); SP.destroy();
var SI = CMClass.getItem("GenItem"); SI.setName("a fixc19 sign"); SI.recoverPhyStats(); CMLib.catalog().addCatalog(SI); SI.destroy();
var R = scratchRoom();
R.setDisplayText("Fixc19 bandit camp");
var WHEN = "WHEN=\"-LOCATION +ROOMSTR(bandit)\" ";
function bandit(orElse) {
	var m = newMob("GenMob", R);
	m.setName("Fixc19bandit");
	m.setStartRoom(R);
	m.basePhyStats().setRejuv(2); m.recoverPhyStats();
	var P = CMClass.getAbility("Prop_RejuvWhen"); P.setMiscText(WHEN + "ELSE=" + orElse); m.addNonUninvokableEffect(P);
	return m;
}
function die(m) { CMLib.combat().postDeath(null, m, null); }
function ticks(m, n) { for (var i = 0; i < n; i++) m.tick(m, Tickable.TICKID_MOB); }
function inR(name) { for (var i = 0; i < R.numInhabitants(); i++) if (s(R.fetchInhabitant(i).Name()) == name) return R.fetchInhabitant(i); return null; }
function itemIn(name) { for (var i = 0; i < R.numItems(); i++) if (s(R.getItem(i).Name()) == name) return R.getItem(i); return null; }
// while the mask holds, it comes back as before
var b = bandit("NEVER");
die(b);
var dead = b.amDead();
ticks(b, 4);
check("back", dead && (!b.amDead()) && b.location() == R, "dead " + dead + " now dead " + b.amDead());
// the camp is the settlers' now: NEVER
R.setDisplayText("Fixc19 settlers' camp");
die(b);
ticks(b, 4);
check("never", b.amDestroyed() && inR("Fixc19bandit") == null, "destroyed " + b.amDestroyed());
// WAIT: it waits, and comes back when the mask holds again
var w = bandit("WAIT");
die(w);
ticks(w, 4);
var waited = w.amDead() && !w.amDestroyed();
R.setDisplayText("Fixc19 bandit camp again");
ticks(w, 4);
check("wait", waited && (!w.amDead()) && w.location() == R, "waited " + waited + " back " + !w.amDead());
w.destroy();
// REPLACE: settlers in their place, for good
R.setDisplayText("Fixc19 settlers' camp");
var r = bandit("\"REPLACE:a fixc19 settler\"");
die(r);
ticks(r, 4);
var settler = inR("a fixc19 settler");
check("replace", r.amDestroyed() && settler != null && settler.getStartRoom() == R, "settler " + settler);
// an item's rejuv: the bounty board, replaced by a sign
var I = CMClass.getItem("GenItem");
I.setName("Fixc19 bounty board");
I.basePhyStats().setRejuv(1); I.recoverPhyStats();
R.addItem(I);
var PI = CMClass.getAbility("Prop_RejuvWhen"); PI.setMiscText(WHEN + "ELSE=\"REPLACE:a fixc19 sign\""); I.addNonUninvokableEffect(PI);
CMClass.getAbility("ItemRejuv").loadMeUp(I, R);
var IR = I.fetchEffect("ItemRejuv");
R.delItem(I);
IR.tick(IR, Tickable.TICKID_ROOM_ITEM_REJUV);
check("item", IR != null && itemIn("Fixc19 bounty board") == null && itemIn("a fixc19 sign") != null, "board " + itemIn("Fixc19 bounty board") + " sign " + itemIn("a fixc19 sign"));
// and while it holds, the item comes back
R.setDisplayText("Fixc19 bandit camp");
var J = CMClass.getItem("GenItem");
J.setName("Fixc19 wanted poster");
J.basePhyStats().setRejuv(1); J.recoverPhyStats();
R.addItem(J);
var PJ = CMClass.getAbility("Prop_RejuvWhen"); PJ.setMiscText(WHEN + "ELSE=NEVER"); J.addNonUninvokableEffect(PJ);
CMClass.getAbility("ItemRejuv").loadMeUp(J, R);
var JR = J.fetchEffect("ItemRejuv");
R.delItem(J);
JR.tick(JR, Tickable.TICKID_ROOM_ITEM_REJUV);
check("item-back", itemIn("Fixc19 wanted poster") != null, "the poster did not come back");
check("help", s(CMLib.help().getHelpText("PROP_REJUVWHEN", M, true)).indexOf("REPLACE") >= 0, "no help");

if (settler != null) settler.destroy();
for (var i = R.numItems() - 1; i >= 0; i--) R.getItem(i).destroy();
I.destroy(); J.destroy();
R.destroy();
CMLib.catalog().delCatalog(CMLib.catalog().getCatalogMob("a fixc19 settler"));
CMLib.catalog().delCatalog(CMLib.catalog().getCatalogItem("a fixc19 sign"));
done();
