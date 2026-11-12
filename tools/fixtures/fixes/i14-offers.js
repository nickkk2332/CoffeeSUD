// I14 offer books: a seller's offers drawn by weight from its catalog list, from the offers stream, kept in its property's text
// (saved with it) and drawn again only when the refresh comes; one offer held through a reroll and a refresh; a paid reroll
// refused before anything is drawn when the fee cannot be paid, and otherwise paid and drawn as one; a purchase takes the price.
FIXFILE = "i14-offers";
var OB = CM.core.OfferBooks, RS = CM.core.RandomStreams, GT = CM.core.GameTime;
function s(x) { return "" + x; }
function throws(name, fn, part) { var why = ""; try { fn(); } catch (e) { why = s(e); } check(name, why.length > 0 && (!part || why.indexOf(part) >= 0), "expected a refusal" + (part ? " naming '" + part + "'" : "") + ": " + why); }
var R = scratchRoom(), made = [], catalogued = [];
function catalog(name, value) { var I = CMClass.getItem("GenItem"); I.setName(name); I.setDisplayText(name + " lies here."); I.setBaseValue(value); I.recoverPhyStats(); CMLib.catalog().addCatalog(I); I.destroy(); catalogued.push(name); }
function book() { return JSON.parse(s(seller.fetchEffect("Prop_OfferBook").text())).book; }
function money(m) { return Number(CMLib.beanCounter().getTotalAbsoluteNativeValue(m)); }
var seller = null, A = null;
try {
	catalog("a fixi14 sword", 100); catalog("a fixi14 cap", 40); catalog("a fixi14 lamp", 20); catalog("a fixi14 rope", 10); catalog("a fixi14 drum", 60);
	seller = newMob("GenMob", R); seller.setName("Vendor Vey");
	var P = CMClass.getAbility("Prop_OfferBook");
	P.setMiscText(JSON.stringify({ items: [{ name: "a fixi14 sword", weight: 3 }, "a fixi14 cap", "a fixi14 lamp", "a fixi14 rope", "a fixi14 drum"], size: 3, price: 150, reroll: 25, refresh: "1 mudday" }));
	seller.addNonUninvokableEffect(P);
	check("valid", s(P.accountForYourself()).indexOf("Keeps an offer book") >= 0, s(P.accountForYourself()));
	A = newMob("GenMob", R); A.setName("Buyer Bo");
	CMLib.beanCounter().giveSomeoneMoney(A, 100.0);

	// the first look draws the book: three different items, priced at 150% of value, from the offers stream
	var at = RS.counter("offers");
	var shown = s(OB.show(A)), b = book();
	var names = b.offers.map(function (o) { return o.name; });
	check("drawn", b.offers.length == 3 && names.filter(function (n, i) { return names.indexOf(n) == i; }).length == 3 && RS.counter("offers") > at
		&& shown.indexOf("Vendor Vey's offers:") >= 0 && shown.indexOf("OFFERS REROLL draws the others again for") >= 0, shown);
	check("priced", b.offers.every(function (o) { var v = { "a fixi14 sword": 150, "a fixi14 cap": 60, "a fixi14 lamp": 30, "a fixi14 rope": 15, "a fixi14 drum": 90 }[o.name]; return o.price == v; }), JSON.stringify(b.offers));
	var keptText = s(seller.fetchEffect("Prop_OfferBook").text()), counter = RS.counter("offers");
	OB.show(A);
	check("kept", s(seller.fetchEffect("Prop_OfferBook").text()) == keptText && RS.counter("offers") == counter, "the book was drawn again before its refresh");

	// hold one; a reroll draws the others again and keeps it; the fee is taken once
	var heldName = s(b.offers[1].name);
	var said = s(OB.hold(A, 2));
	check("held", said.indexOf("holds " + heldName + " for you") >= 0 && book().offers[1].held === true, said);
	var before = money(A);
	said = s(OB.reroll(A));
	var r = book(), stillHeld = r.offers.filter(function (o) { return o.held === true; });
	check("rerolled", said.indexOf("You pay") >= 0 && money(A) == before - 25 && stillHeld.length == 1 && stillHeld[0].name == heldName && r.offers.length == 3, said + " / " + JSON.stringify(r.offers));

	// a reroll that cannot be paid is refused before anything is drawn
	CMLib.beanCounter().subtractMoney(A, money(A) - 10);
	var text = s(seller.fetchEffect("Prop_OfferBook").text());
	at = RS.counter("offers");
	throws("reroll-unpaid", function () { OB.reroll(A); }, "that costs");
	check("unpaid-draws-nothing", RS.counter("offers") == at && s(seller.fetchEffect("Prop_OfferBook").text()) == text && money(A) == 10, "something was drawn or paid");

	// buying takes the price and the offer; a refresh keeps the held one and fills the rest
	CMLib.beanCounter().giveSomeoneMoney(A, 200.0);
	var cheap = -1; r = book(); for (var i = 0; i < r.offers.length; i++) if (!r.offers[i].held && (cheap < 0 || r.offers[i].price < r.offers[cheap].price)) cheap = i;
	var buying = r.offers[cheap]; before = money(A);
	said = s(OB.buy(A, cheap + 1));
	var bought = null; for (var i = 0; i < A.numItems(); i++) if (s(A.getItem(i).Name()) == buying.name) bought = A.getItem(i);
	if (bought != null) made.push(bought);
	check("bought", said.indexOf("You buy " + buying.name) >= 0 && bought != null && money(A) == before - buying.price && book().offers.length == 2, said);
	// the refresh comes: the book was drawn more than a mud day ago (its draw time set back, not the game clock moved)
	var all = JSON.parse(s(seller.fetchEffect("Prop_OfferBook").text()));
	all.book.drawn = Number(GT.now()) - CM.core.CMProps.getMillisPerMudHour() * 25;
	seller.fetchEffect("Prop_OfferBook").setMiscText(JSON.stringify(all));
	var drawn = book().drawn;
	OB.show(A);
	var f = book(), keptHeld = f.offers.filter(function (o) { return o.held === true; });
	check("refreshed", f.drawn > drawn && f.offers.length == 3 && keptHeld.length == 1 && keptHeld[0].name == heldName, JSON.stringify(f));
	said = s(OB.release(A));
	check("released", said.indexOf("go back to the book") >= 0 && book().offers.every(function (o) { return o.held !== true; }), said);
	throws("release-none", function () { OB.release(A); }, "you hold no offer here");

	// a book that cannot be read says why
	var Bad = CMClass.getAbility("Prop_OfferBook"); Bad.setMiscText(JSON.stringify({ items: ["a fixi14 nothing"] }));
	check("problem", s(Bad.accountForYourself()).indexOf("no catalog item 'a fixi14 nothing'") >= 0, s(Bad.accountForYourself()));
	function evs(m) { var e = CM.core.Provenance.recent(m), o = ""; for (var i = 0; i < e.size(); i++) o += s(e.get(i).describe()) + "\n"; return o; }
	var ev = evs(A);
	check("ledger", ev.indexOf("held the offer of") >= 0 && ev.indexOf("to draw the offer book again") >= 0 && ev.indexOf("bought ") >= 0, ev);
} finally {
	for (var i = 0; i < made.length; i++) if (!made[i].amDestroyed()) made[i].destroy();
	if (A != null) A.destroy(); if (seller != null) seller.destroy();
	for (var i = 0; i < catalogued.length; i++) { var c = CMLib.catalog().getCatalogItem(catalogued[i]); if (c != null) CMLib.catalog().delCatalog(c); }
	R.destroy();
}
done();
