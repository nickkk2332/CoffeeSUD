// I20 binding fields, each on its own: allocation (ALLOT, ALLOTFOR: who may take it first, for how long), use (the binding),
// trade (TRADE=NONE, ALLOWED or ANY), source (SOURCE) and expiry (EXPIRES: how long a binding lasts); kept in the text.
FIXFILE = "i20-binding";
var GT = CM.core.GameTime, hour = CM.core.CMProps.getMillisPerMudHour();
function s(x) { return "" + x; }
var R = scratchRoom(), mobs = [], items = [];
// party members (following the test's player): an unfollowing NPC's pickup binds nothing, as before
function person(name) { var m = newMob("GenMob", R); m.setName(name); m.setFollowing(M); mobs.push(m); return m; }
function thing(name, parms) { var I = CMClass.getItem("GenItem"); I.setName(name); I.setRawProperLocationBitmap(CM.Items.interfaces.Wearable.WORN_NECK); var B = CMClass.getAbility("Prop_ItemBinder"); B.setMiscText(parms); I.addNonUninvokableEffect(B); items.push(I); return [I, B]; }
function get(m, I, B) { var msg = CMClass.getMsg(m, I, null, CMMsg.MSG_GET, null); var ok = B.okMessage(m, msg); if (ok) { B.executeMsg(m, msg); R.delItem(I); m.addItem(I); } return ok; }
function wear(m, I, B) { return B.okMessage(m, CMClass.getMsg(m, I, null, CMMsg.MSG_WEAR, null)); }
function give(m, to, I, B) { return B.okMessage(m, CMClass.getMsg(m, to, I, CMMsg.MSG_GIVE, null)); }
function back(B, key, hours) { var t = s(B.text()); var at = Number(/ALLOTAT="?(-?\d+)/.exec(t) ? /ALLOTAT="?(-?\d+)/.exec(t)[1] : /BOUNDAT="?(-?\d+)/.exec(t)[1]); B.setMiscText(t.replace(new RegExp(key + '="?-?\\d+"?'), key + '="' + (at - hours * hour) + '"')); }
try {
	var ann = person("Fixi20 Ann"), bob = person("Fixi20 Bob");
	// allocation: the warden's amulet is Ann's to take first, for a mud hour; then anyone's
	var a = thing("a fixi20 amulet", 'TO=CHARACTER ON=PICKUP ALLOT="Fixi20 Ann" ALLOTFOR="1 mudhour" TRADE=NONE SOURCE="the furnace warden" EXPIRES="2 mudhours"');
	var I = a[0], B = a[1]; R.addItem(I);
	check("inspect", s(B.accountForYourself()).indexOf("Allotted to: Fixi20 Ann for 1 mud hour(s) more") >= 0 && s(B.accountForYourself()).indexOf("From: the furnace warden") >= 0
		&& s(B.accountForYourself()).indexOf("Trade: cannot be given or sold once bound") >= 0 && /ALLOTAT="\d+"/.test(s(B.text())), s(B.accountForYourself()) + " / " + s(B.text()));
	check("allotted-refused", !get(bob, I, B), "Bob took Ann's allotted amulet");
	check("allotted-taken", get(ann, I, B) && s(B.text()).indexOf("ALLOT=") < 0 && s(B.accountForYourself()).indexOf("Bound to: Fixi20 Ann for 2 mud hour(s) more") >= 0, s(B.text()) + " / " + s(B.accountForYourself()));
	// trade NONE: not even to someone it would allow
	check("trade-none", !give(ann, bob, I, B) && !B.okMessage(ann, CMClass.getMsg(ann, bob, I, CMMsg.MSG_SELL, null)), "a TRADE=NONE item could be given or sold");
	check("use-bound", wear(ann, I, B) && !wear(bob, I, B), "the binding does not decide who may wear it");
	// expiry: two mud hours after binding, it is anyone's, and binds no more
	back(B, "BOUNDAT", 3);
	check("expired", wear(bob, I, B) && s(B.accountForYourself()).indexOf("Its binding has ended") >= 0 && s(B.text()).indexOf("FREED") >= 0 && s(B.text()).indexOf("BOUND=") < 0, s(B.text()) + " / " + s(B.accountForYourself()));
	R.addItem(I); ann.delItem(I);
	check("no-rebind", get(bob, I, B) && s(B.text()).indexOf("BOUND=") < 0, s(B.text()));

	// an allotment that runs out: anyone may take it then
	var c = thing("a fixi20 ring", 'TO=CHARACTER ON=PICKUP ALLOT="Fixi20 Ann" ALLOTFOR="1 mudhour"');
	R.addItem(c[0]);
	check("allot-held", !get(bob, c[0], c[1]), "taken before the allotment ran out");
	back(c[1], "ALLOTAT", 2);
	check("allot-run-out", get(bob, c[0], c[1]) && s(c[1].text()).indexOf('BOUND="Fixi20 Bob"') >= 0, s(c[1].text()));

	// trade ANY: a bound item may be given and sold; using it stays the bound one's
	var d = thing("a fixi20 charm", 'TO=CHARACTER ON=PICKUP TRADE=ANY BOUND="Fixi20 Ann"');
	ann.addItem(d[0]);
	check("trade-any", give(ann, bob, d[0], d[1]) && d[1].okMessage(ann, CMClass.getMsg(ann, bob, d[0], CMMsg.MSG_SELL, null)) && !wear(bob, d[0], d[1]), "TRADE=ANY did not let it be given, or let another wear it");
	// the default, ALLOWED, is what it was: given only to whom it allows, not sold
	var e = thing("a fixi20 band", 'TO=CHARACTER ON=PICKUP BOUND="Fixi20 Ann"');
	ann.addItem(e[0]);
	check("trade-allowed", !give(ann, bob, e[0], e[1]) && s(e[1].accountForYourself()).indexOf("given only to whom it is bound to, and not sold") >= 0, s(e[1].accountForYourself()));
} finally {
	for (var i = 0; i < items.length; i++) if (!items[i].amDestroyed()) items[i].destroy();
	for (var i = 0; i < mobs.length; i++) { mobs[i]["setFollowing(com.planet_ink.coffee_mud.MOBS.interfaces.MOB)"](null); if (!mobs[i].amDestroyed()) mobs[i].destroy(); }
	R.destroy();
}
done();
