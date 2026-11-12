// A23 one rule for knowing a thing: an item of a kind (Prop_Unknown) shows one who does not know the kind its unknown name, look
// and description, wherever the engine names it for them; the unknown name finds it; appraisal and a shop's price follow what it
// seems; identifying or examining reveals the kind once, as its REVEAL allows, for every item of it; looking again changes nothing.
// Every listing that names an item for someone names it so: inventory, equipment, the prompt (a window's panel), the room, a
// container, examining, LOOK FOR, a shop's LIST, VIEW and VALUE, LOOT and LOOT SELF, ITEMMODS, MODCRAFT, CHOICES, OFFERS, and the
// gear sources of COMPOSE SHOW and COMBATRESULT STATS; builder views (WORLD COMBAT STATS) keep the real name.
FIXFILE = "a23-knowledge";
var diceAtStart = CMath.getRandState(); // restored at the end: later fixtures roll as they would without this one
function s(x) { return "" + x; }
function list() { var L = new java.util.ArrayList(); for (var i = 0; i < arguments.length; i++) L.add(arguments[i]); return L; }
var R = scratchRoom(), mobs = [], items = [], catalogued = [];
var Files = java.nio.file.Files, root = null, originalWorld = WorldFiles.worldDir();
var currentField = java.lang.Class.forName("com.planet_ink.coffee_mud.core.OptionalModules").getDeclaredField("current"); currentField.setAccessible(true);
var previousCampaign = currentField.get(null);
var knownField = java.lang.Class.forName("com.planet_ink.coffee_mud.core.save.WorldFiles").getDeclaredField("knownSums"); knownField.setAccessible(true);
var previousKnown = knownField.get(null);
function copy(from, to) { if (from.isDirectory()) { to.mkdirs(); var l = from.listFiles(); for (var i = 0; i < l.length; i++) copy(l[i], new java.io.File(to, s(l[i].getName()))); } else Files.copy(from.toPath(), to.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING); }
function person(name) { var m = newMob("GenMob", R); m.setName(name); mobs.push(m); return m; }
// an item of a kind one must learn: its real name holds "starmetal", which nothing may show one who does not know it
function unknown(I, kind, alias) { var P = CMClass.getAbility("Prop_Unknown"); P.setMiscText('KIND=' + kind + ' NAME="' + alias + '" DESC="Nothing you can place." REVEAL=IDENTIFY'); I.addNonUninvokableEffect(P); I.recoverPhyStats(); return I; }
function mystery(cls, real, kind, alias) { var I = CMClass.getItem(cls); I.setName(real); I.setDisplayText(real + " lies here."); I.setDescription("The real " + real + "."); items.push(I); return unknown(I, kind, alias); }
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

	// every listing that names an item names it as the one it is shown to knows it: Dee knows none of these kinds
	var IM = CM.core.ItemModifiers, IL = CM.core.ItemLoot, IC = CM.core.ItemChoices, MD = CM.core.ModuleDefinitions, OM = CM.core.OptionalModules;
	var CP = CM.core.CombatProfiles, AC = CM.core.AbilityComposition, OB = CM.core.OfferBooks, W = CM.Items.interfaces.Wearable, RM = CM.Items.interfaces.RawMaterial;
	root = Files.createTempDirectory("sud-a23-").toFile();
	var ex = new java.io.File("../SUDDocs/creator/examples");
	copy(new java.io.File(ex, "ability-composition/modules"), new java.io.File(root, "modules"));
	copy(new java.io.File(ex, "ability-composition/compositions"), new java.io.File(root, "compositions"));
	copy(new java.io.File(ex, "combat-profiles/modules"), new java.io.File(root, "modules"));
	copy(new java.io.File(ex, "combat-profiles/combat"), new java.io.File(root, "combat"));
	copy(new java.io.File(ex, "item-modifiers"), root);
	copy(new java.io.File(ex, "loot-policies/modules"), new java.io.File(root, "modules"));
	copy(new java.io.File(ex, "workbench"), root);
	copy(new java.io.File(ex, "saved-choices"), root);
	WorldFiles.configure(root); CP.reset(); AC.reset(); IM.reset(); IL.reset(); IC.reset(); CM.core.Affixes.reload();
	var plan = MD.load(root, "items.modifiers,items.loot,items.choices", ""); currentField.set(null, OM.prepare(plan, null, false));
	check("listing-modules", plan.errors.isEmpty(), s(plan.problems()));
	var dee = person("Fixa23 Dee"); dee.setPlayerStats(CMClass.getCommon("DefaultPlayerStats"));
	var VS = new JavaAdapter(CM.Common.FakeSession, { isFake: function() { return false; } }); // a session the engine looks at, as a player's
	VS.initialize(null, "MEMORY"); dee.setSession(VS); VS.setMob(dee);
	var mark = 0;
	function said() { var all = s(field(VS, "bout").toString()); var o = all.substring(mark); mark = all.length; return o.replace(/\x1b\[[0-9;]*m/g, "").replace(/\^./g, "").replace(/\s+/g, " "); }
	function hidden(out) { return out.indexOf("starmetal") < 0 && out.indexOf("ember-bronze") < 0; }
	var box = mystery("GenContainer", "a starmetal coffer", "embertrial.star", "a plain box"); box.setCapacity(50); dee.addItem(box);
	var bar = mystery("GenItem", "a starmetal bar", "embertrial.star", "a lumpy bar"); dee.addItem(bar); bar.setContainer(box);
	var pendant = mystery("GenItem", "a starmetal pendant", "embertrial.star", "a dull pendant"); pendant.setRawProperLocationBitmap(W.WORN_NECK); dee.addItem(pendant); pendant.wearAt(W.WORN_NECK);
	var CS = CMClass.getAbility("Prop_CombatStats"); CS.setMiscText(JSON.stringify({ schema: 1, resistance: { fire: 25 } })); pendant.addNonUninvokableEffect(CS);
	var bundle = mystery("GenResource", "a starmetal bundle", "embertrial.star", "a rough bundle"); bundle.setMaterial(RM.RESOURCE_COPPER); bundle.setSecretIdentity("starmetal ore");
	bundle.basePhyStats().setWeight(5); bundle.recoverPhyStats(); dee.addItem(bundle);
	var faint = mystery("GenItem", "a starmetal anvil", "embertrial.star", "a faint mark"); faint.setDisplayText(""); R.addItem(faint);

	// INVENTORY, EQUIPMENT, and the prompt's worn item (a window's side panel draws the prompt's codes)
	var inv = s(CMClass.getCommand("Inventory").getInventory(dee, dee, null, false));
	check("inventory", inv.indexOf("A plain box") >= 0 && inv.indexOf("A rough bundle") >= 0 && hidden(inv), inv);
	var eq = s(CMClass.getCommand("Equipment").getEquipment(dee, dee, false));
	check("equipment", eq.indexOf("a dull pendant") >= 0 && hidden(eq), eq);
	var prompt = s(CMLib.utensils().buildPrompt(dee, "%-NECK"));
	check("prompt-panel", prompt.indexOf("a dull pendant") >= 0 && hidden(prompt), prompt);
	// LOOK at the room (its item lines and their tags); an item with no look of its own (the room's description names it) gets none
	said(); R.send(dee, CMClass.getMsg(dee, R, null, CMMsg.MSG_LOOK, null));
	var room = said();
	check("room-look", room.indexOf("A grey lump lies here.") >= 0 && room.indexOf("A dull ingot lies here.") >= 0 && hidden(room), room);
	check("no-look-no-line", s(faint.displayText(dee)) == "" && room.indexOf("faint mark") < 0, s(faint.displayText(dee)));
	// LOOK IN a container, EXAMINE (not what an unknown bundle is a bundle of), LOOK FOR (named and numbered as the viewer knows it)
	said(); R.send(dee, CMClass.getMsg(dee, box, null, CMMsg.MSG_LOOK, null));
	var inside = said();
	check("look-in", inside.indexOf("a plain box contains:") >= 0 && inside.indexOf("A lumpy bar") >= 0 && hidden(inside), inside);
	said(); R.send(dee, CMClass.getMsg(dee, bundle, null, CMMsg.MSG_EXAMINE, null));
	var exam = said();
	check("examine", exam.indexOf("A rough bundle is a level") >= 0 && exam.indexOf("bundle of") < 0 && hidden(exam), exam);
	said(); CMClass.getCommand("Look").execute(dee, list("LOOK", "FOR", "ALL", "grey"), 0);
	var spot = said();
	check("look-for", spot.indexOf("a grey lump") >= 0 && spot.indexOf("a pinch of grey dust") >= 0 && hidden(spot), spot);
	// a shop's LIST, VIEW and VALUE
	var lamp = mystery("GenItem", "a starmetal lamp", "embertrial.star", "a dim lamp"); lamp.setBaseValue(10); lamp.recoverPhyStats();
	var stocked = SK.getShop().addStoreInventory(lamp, 1, -1); // the shop keeps (and destroys) its own copy
	said(); SK.executeMsg(SK, CMClass.getMsg(dee, SK, null, CMMsg.MSG_LIST, null));
	var listed = said();
	check("shop-list", listed.indexOf("a dim lamp") >= 0 && hidden(listed), listed);
	said(); SK.executeMsg(SK, CMClass.getMsg(dee, SK, stocked, CMMsg.MSG_VIEW, null));
	var viewed = said();
	check("shop-view", viewed.indexOf("Interested in a dim lamp?") >= 0 && hidden(viewed), viewed);
	said(); SK.executeMsg(SK, CMClass.getMsg(dee, SK, bar, CMMsg.MSG_VALUE, null));
	var valued = said();
	check("shop-value", valued.indexOf("for a lumpy bar") >= 0 && hidden(valued), valued);
	// LOOT of a container, and LOOT SELF (with a tracked cloak and a veiled charm left out by the filter, named as Dee knows them)
	IL.setFilter(dee, list("untracked"));
	var loot = s(IL.view(dee, box));
	check("loot-view", loot.indexOf("In a plain box:") >= 0 && loot.indexOf("Untracked: a lumpy bar") >= 0 && hidden(loot), loot);
	var cloak = CM.core.GenCalls.items("randareas/workbench.xml", "workbench_cloak", null, dee, R).get(0); items.push(cloak); dee.addItem(cloak);
	var cloakReal = s(cloak.name()); unknown(cloak, "embertrial.star", "a ragged cloak");
	var charm = null, charmReal = "?";
	for (var k = 0; (k < 40) && (charm == null); k++) {
		var C = CM.core.GenCalls.items("randareas/saved-choices.xml", "choices_veiled", null, dee, R).get(0); items.push(C);
		if ((JSON.parse(s(MD.json(IM.data(C)))).veiled || []).length > 0) charm = C;
	}
	if (charm != null) { dee.addItem(charm); charmReal = s(charm.name()); unknown(charm, "embertrial.star", "an odd charm"); }
	function unseen(out) { return hidden(out) && out.indexOf(cloakReal) < 0 && out.indexOf(charmReal) < 0; }
	var self = s(IL.selfView(dee));
	check("loot-self", self.indexOf("a lumpy bar (in a plain box)") >= 0 && self.indexOf("a dull pendant (worn)") >= 0 && self.indexOf("a ragged cloak: ") >= 0 && unseen(self), self);
	// ITEMMODS, MODCRAFT POOL and PREVIEW
	said(); CMClass.getCommand("ItemMods").execute(dee, list("ITEMMODS", "ragged cloak"), 0);
	var mods = said();
	check("itemmods", mods.indexOf("a ragged cloak Base:") >= 0 && unseen(mods), mods);
	said(); CMClass.getCommand("ModCraft").execute(dee, list("MODCRAFT", "POOL", "ragged cloak"), 0);
	var pool = said();
	check("modcraft-pool", pool.indexOf("Affix pool for a ragged cloak") >= 0 && unseen(pool), pool);
	var forge = CMClass.getItem("GenItem"), WS = CMClass.getAbility("Prop_Workstation"); WS.setMiscText("TAG=ember-forge"); forge.addNonUninvokableEffect(WS); R.addItem(forge); items.push(forge);
	var iron = CMClass.getItem("GenResource"); iron.setName("some bench iron"); iron.setMaterial(RM.RESOURCE_IRON); iron.basePhyStats().setWeight(20); iron.setBaseValue(20); iron.recoverPhyStats(); dee.addItem(iron); items.push(iron);
	said(); CMClass.getCommand("ModCraft").execute(dee, list("MODCRAFT", "PREVIEW", "workbench.upgrade", "ragged cloak"), 0);
	var pv = said();
	check("modcraft-preview", pv.indexOf("on a ragged cloak") >= 0 && unseen(pv), pv);
	// CHOICES
	said(); CMClass.getCommand("Choices").execute(dee, list("CHOICES"), 0);
	var ch = said();
	check("choices", (charm != null) && ch.indexOf("an odd charm, veiled 1") >= 0 && unseen(ch), (charm == null) ? "no veiled charm in 40 drops" : ch);
	// OFFERS: the book, and what one buys from it
	var lantern = mystery("GenItem", "a starmetal lantern", "embertrial.star", "a dark lantern"); lantern.setBaseValue(10); lantern.recoverPhyStats();
	CMLib.catalog().addCatalog(lantern); catalogued.push("a starmetal lantern"); lantern.destroy();
	var vendor = person("Fixa23 Vendor"), OBP = CMClass.getAbility("Prop_OfferBook");
	OBP.setMiscText(JSON.stringify({ items: ["a starmetal lantern"], size: 1, price: 150, reroll: 0, refresh: "1 mudday" })); vendor.addNonUninvokableEffect(OBP);
	CMLib.beanCounter().giveSomeoneMoney(dee, 100.0);
	var offers = s(OB.show(dee)), bought = s(OB.buy(dee, 1));
	for (var i = 0; i < dee.numItems(); i++) if (s(dee.getItem(i).Name()) == "a starmetal lantern") items.push(dee.getItem(i));
	check("offers", offers.indexOf("1) a dark lantern") >= 0 && bought.indexOf("You buy a dark lantern") >= 0 && hidden(offers + bought), offers + " / " + bought);
	// COMPOSE SHOW's and COMBATRESULT STATS's gear sources
	var compose = CMClass.getAbility("Skill_Compose"); compose.setProficiency(100); dee.addAbility(compose); AC.learn(dee, "example.ember");
	var shown = s(AC.show(dee, "example.ember"));
	check("compose-show", shown.indexOf("a dull pendant: ") >= 0 && hidden(shown), shown);
	said(); CMClass.getCommand("CombatResult").execute(dee, list("COMBATRESULT", "STATS"), 0);
	var stats = said();
	check("combatresult-stats", stats.indexOf("a dull pendant: ") >= 0 && hidden(stats), stats);
	// a builder's view (WORLD COMBAT STATS, ITEMMODS from code) keeps the real name, and the item is still what it is
	var builder = s(CP.describeStats(dee));
	check("builder-real", builder.indexOf("a starmetal pendant: ") >= 0 && s(IM.inspect(cloak)).indexOf(cloakReal) == 0 && s(pendant.Name()) == "a starmetal pendant"
		&& !K.knows(dee, pendant), builder);
} finally {
	CMath.setRandState(diceAtStart.longValue());
	for (var i = 0; i < items.length; i++) if ((items[i] != null) && !items[i].amDestroyed()) items[i].destroy();
	for (var i = 0; i < mobs.length; i++) if (!mobs[i].amDestroyed()) mobs[i].destroy();
	for (var i = 0; i < catalogued.length; i++) { var c = CMLib.catalog().getCatalogItem(catalogued[i]); if (c != null) CMLib.catalog().delCatalog(c); }
	R.destroy();
	WorldFiles.configure(originalWorld); knownField.set(null, previousKnown); currentField.set(null, previousCampaign);
	CM.core.ItemModifiers.reset(); CM.core.ItemLoot.reset(); CM.core.ItemChoices.reset(); CM.core.Affixes.reload(); CM.core.CombatProfiles.reset(); CM.core.AbilityComposition.reset();
	if (root != null) { var stream = Files.walk(root.toPath()); try { var paths = stream.sorted(java.util.Comparator.reverseOrder()).iterator(); while (paths.hasNext()) Files.deleteIfExists(paths.next()); } finally { stream.close(); } }
}
done();
