// A23 one rule for knowing a thing: an item of a kind (Prop_Unknown) shows one who does not know the kind its unknown name, look
// and description, wherever the engine names it for them; the unknown name finds it; appraisal and a shop's price follow what it
// seems; identifying or examining reveals the kind once, as its REVEAL allows, for every item of it; looking again changes nothing.
FIXFILE = "a23-knowledge";
function s(x) { return "" + x; }
var R = scratchRoom(), mobs = [], items = [];
function person(name) { var m = newMob("GenMob", R); m.setName(name); mobs.push(m); return m; }
function ingot() { var I = CMClass.getItem("GenItem"); I.setName("an ember-bronze ingot"); I.setDisplayText("An ember-bronze ingot glows dully here."); I.setDescription("Bronze with the ember's heat still in it.");
	I.setBaseValue(100); var P = CMClass.getAbility("Prop_Unknown"); P.setMiscText('KIND=embertrial.alloy NAME="a dull ingot" DISPLAY="A dull ingot lies here." DESC="A lump of metal you cannot place." VALUE=2 REVEAL=IDENTIFY,EXAMINE');
	I.addNonUninvokableEffect(P); I.recoverPhyStats(); items.push(I); return I; }
var K = CM.core.ItemKnowledge;
function tattoos(m) { var n = 0; for (var e = m.tattoos(); e.hasMoreElements();) { e.nextElement(); n++; } return n; }
try {
	var ann = person("Fixa23 Ann"), bob = person("Fixa23 Bob");
	var one = ingot(), two = ingot(); ann.addItem(one); R.addItem(two);
	// unknown: the name, the look, the description, for the one who does not know it; the engine's message names are name(viewer)
	check("unknown", s(one.name(ann)) == "a dull ingot" && s(two.displayText(ann)) == "A dull ingot lies here." && s(one.description(ann)) == "A lump of metal you cannot place."
		&& s(one.name()) == "an ember-bronze ingot", s(one.name(ann)) + " / " + s(two.displayText(ann)));
	check("message-name", s(CMLib.coffeeFilter().fullOutFilter(null, ann, ann, one, null, "<T-NAME> is here.", false)).toLowerCase().indexOf("a dull ingot") >= 0,
		s(CMLib.coffeeFilter().fullOutFilter(null, ann, ann, one, null, "<T-NAME> is here.", false)));
	check("found-by-unknown-name", ann.findItem(null, "dull ingot") == one && ann.findItem(null, "ember-bronze") == one, "the unknown name does not find it");
	// appraisal and a shop's price follow what it seems; appraising does not reveal it (its REVEAL leaves APPRAISE out)
	var ap = CMClass.getAbility("Thief_Appraise");
	var worth = s(ap.getWorth(ann, one, true));
	check("appraised-as-seen", worth.indexOf("a dull ingot is worth about") >= 0 && worth.indexOf("You recognize") < 0 && !K.knows(ann, one), worth);
	var SK = CMClass.getMOB("GenShopkeeper"); SK.setName("Fixa23 Buyer"); SK.bringToLife(R, true); mobs.push(SK);
	var paid = Number(CMLib.coffeeShops().pawningPrice(SK, ann, one, SK).absoluteGoldPrice);
	var had = tattoos(ann), text = s(one.fetchEffect("Prop_Unknown").text());
	s(one.name(ann)); s(one.description(ann)); worth = s(ap.getWorth(ann, one, true));
	check("looking-changes-nothing", tattoos(ann) == had && s(one.fetchEffect("Prop_Unknown").text()) == text, "looking or appraising changed something");
	// identifying reveals the kind: every ingot of it is known to Ann, never to Bob
	var said = s(CM.core.ItemIdentity.describe(one, ann));
	check("identified", said.indexOf("You recognize a dull ingot: it is an ember-bronze ingot.") >= 0 && K.knows(ann, one) && K.knows(ann, two) && !K.knows(bob, two)
		&& s(two.name(ann)) == "an ember-bronze ingot" && s(two.name(bob)) == "a dull ingot" && ann.findTattoo("KNOWN_EMBERTRIAL.ALLOY") != null, said);
	var paidKnown = Number(CMLib.coffeeShops().pawningPrice(SK, ann, one, SK).absoluteGoldPrice);
	check("price-follows", paidKnown > paid, "known " + paidKnown + " unknown " + paid);
	check("identify-once", s(CM.core.ItemIdentity.describe(two, ann)).indexOf("You recognize") < 0 && tattoos(ann) == had + 1, "learned twice");
	// examining reveals it for Bob, its REVEAL allows EXAMINE; a MASK can keep it from someone
	var msg = CMClass.getMsg(bob, two, null, CMMsg.MSG_EXAMINE, null);
	two.fetchEffect("Prop_Unknown").executeMsg(two, msg);
	check("examined", K.knows(bob, two), "examining did not reveal it");
	var three = ingot(); three.fetchEffect("Prop_Unknown").setMiscText('KIND=embertrial.ore NAME="a grey lump" REVEAL=IDENTIFY MASK="-NAMES +Fixa23 Ann"'); R.addItem(three);
	check("identify-masked", s(CM.core.ItemIdentity.describe(three, bob)).indexOf("you cannot tell what it is") >= 0 && !K.knows(bob, three), s(CM.core.ItemIdentity.describe(three, bob)));
	// a familiar's sense (cookbook recipe 36): only one with a familiar (its Prop_Familiar on them) recognizes this by examining it
	var four = ingot(); four.fetchEffect("Prop_Unknown").setMiscText('KIND=embertrial.ash NAME="a pinch of grey dust" REVEAL=EXAMINE MASK="-EFFECTS +Prop_Familiar"'); R.addItem(four);
	var cid = person("Fixa23 Cid");
	four.fetchEffect("Prop_Unknown").executeMsg(four, CMClass.getMsg(cid, four, null, CMMsg.MSG_EXAMINE, null));
	var without = K.knows(cid, four);
	var F = CMClass.getAbility("Prop_Familiar"); F.setMiscText("owl"); cid.addNonUninvokableEffect(F);
	four.fetchEffect("Prop_Unknown").executeMsg(four, CMClass.getMsg(cid, four, null, CMMsg.MSG_EXAMINE, null));
	check("familiar-sense", !without && K.knows(cid, four), "without a familiar " + without + ", with one " + K.knows(cid, four));
	function evs(m) { var e = CM.core.Provenance.recent(m), o = ""; for (var i = 0; i < e.size(); i++) o += s(e.get(i).describe()) + "\n"; return o; }
	var led = s(CM.core.Ledgers.item(one));
	check("ledger", led.indexOf("Of a kind one must learn: EMBERTRIAL.ALLOY (shown as a dull ingot until then)") >= 0, led);
} finally {
	for (var i = 0; i < items.length; i++) if (!items[i].amDestroyed()) items[i].destroy();
	for (var i = 0; i < mobs.length; i++) if (!mobs[i].amDestroyed()) mobs[i].destroy();
	R.destroy();
}
done();
