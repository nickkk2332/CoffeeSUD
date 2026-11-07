// I05 sockets: a type per slot (TYPE="Gems,Runes"); ONLOSS=KEEP gives the socketed items back to the owner when the host
// goes (the default lets them go with it); Prop_ComposeSupport on worn gear, or on a gem socketed in it, adds a support to
// the wearer's composed skills (COMPOSE SHOW says so), and stops when it is taken off.
FIXFILE = "i05-sockets";
var AC = CM.core.AbilityComposition, MD = CM.core.ModuleDefinitions, OM = CM.core.OptionalModules, W = CM.Items.interfaces.Wearable;
var Files = java.nio.file.Files, JString = java.lang.String;
var folder = Files.createTempDirectory("sud-i05-").toFile();
var originalWorld = WorldFiles.worldDir();
var currentField = java.lang.Class.forName("com.planet_ink.coffee_mud.core.OptionalModules").getDeclaredField("current"); currentField.setAccessible(true);
var previousCampaign = currentField.get(null);
var knownField = java.lang.Class.forName("com.planet_ink.coffee_mud.core.save.WorldFiles").getDeclaredField("knownSums"); knownField.setAccessible(true);
var previousKnown = knownField.get(null);
function s(x) { return "" + x; }
function read(file) { return s(new JString(Files.readAllBytes(file.toPath()), "UTF-8")); }
function write(file, obj) { Files.write(file.toPath(), new JString(JSON.stringify(obj)).getBytes("UTF-8")); }
function copyDir(from, to) { var fs = from.listFiles(); for (var i = 0; i < fs.length; i++) write(new java.io.File(to, s(fs[i].getName())), JSON.parse(read(fs[i]))); }
function dir(name) { var d = new java.io.File(folder, name); d.mkdirs(); return d; }
var R = scratchRoom(), made = [];
var J = newMob("GenMob", R); J.setName("Fxi05jeweler"); made.push(J);
function item(name) { var I = CMClass.getItem("GenItem"); I.setName(name); J.addItem(I); return I; }
function gem(name, type, adds) { var g = item(name); var F = CMClass.getAbility("Prop_ItemSlotFiller"); F.setMiscText("TYPE=" + type + (adds ? " ADDS=\"" + adds + "\"" : "")); g.addNonUninvokableEffect(F); return g; }
function put(host, g) { var m = CMClass.getMsg(J, host, g, CMMsg.MSG_PUT, null); if (!R.okMessage(J, m)) return false; R.send(J, m); return true; }
function holds(I) { for (var i = 0; i < J.numItems(); i++) if (J.getItem(i) == I) return true; return false; }
try {
	// a type per slot
	var crown = item("a fxi05 crown"); var S = CMClass.getAbility("Prop_ItemSlot"); S.setMiscText("NUM=2 TYPE=\"Gems,Runes\""); crown.addNonUninvokableEffect(S);
	var ruby = gem("a fxi05 ruby", "Gems"), opal = gem("a fxi05 opal", "Gems"), rune = gem("a fxi05 rune", "Runes"), pearl = gem("a fxi05 pearl", "Pearls");
	check("typed-fit", put(crown, ruby), "the ruby did not go into the gem slot");
	check("typed-full", !put(crown, opal), "a second gem went in while only the rune slot was empty");
	check("typed-other", put(crown, rune) && !put(crown, pearl), "the rune slot did not take the rune, or took a pearl");
	check("typed-text", s(S.text()).indexOf('TYPE="Gems,Runes"') >= 0, s(S.text()).substring(0, 120));
	// custody: KEEP gives the gems back when the host goes; by default they go with it
	var ring = item("a fxi05 ring"); var K = CMClass.getAbility("Prop_ItemSlot"); K.setMiscText("NUM=1 TYPE=Gems ONLOSS=KEEP"); ring.addNonUninvokableEffect(K);
	put(ring, opal);
	check("kept-in", !holds(opal), "the opal is still loose in the inventory");
	ring.destroy();
	check("custody-keep", holds(opal) && !opal.amDestroyed(), "the opal did not come back when the ring was destroyed");
	var band = item("a fxi05 band"); var D = CMClass.getAbility("Prop_ItemSlot"); D.setMiscText("NUM=1 TYPE=Gems"); band.addNonUninvokableEffect(D);
	var jade = gem("a fxi05 jade", "Gems"); put(band, jade); band.destroy();
	check("custody-default", !holds(jade), "the jade came back though the band's sockets keep nothing");

	// gear that gives a composed-skill support
	var ex = new java.io.File("../docs/creator/examples");
	copyDir(new java.io.File(ex, "ability-composition/modules"), dir("modules"));
	copyDir(new java.io.File(ex, "ability-composition/compositions"), dir("compositions"));
	WorldFiles.configure(folder);
	var plan = MD.load(folder, "abilities.composition", "");
	currentField.set(null, OM.prepare(plan, null, false)); AC.reset();
	var a = CMClass.getAbility("Skill_Compose"); a.setProficiency(100); J.addAbility(a); AC.learn(J, "example.ember");
	function supports() { return s(AC.compiled(J, "example.ember").supports.stream().map(function (d) { return d.id; }).collect(java.util.stream.Collectors.toList())); }
	check("no-gear", supports().indexOf("example.burning") < 0, supports());
	var staff = item("a fxi05 staff"); staff.setRawProperLocationBitmap(W.WORN_HELD);
	var P = CMClass.getAbility("Prop_ComposeSupport"); P.setMiscText("SUPPORTS=example.burning"); staff.addNonUninvokableEffect(P);
	check("carried-not", supports().indexOf("example.burning") < 0, "a support counted from a staff only carried: " + supports());
	staff.wearAt(W.WORN_HELD);
	check("held-gives", supports().indexOf("example.burning") >= 0 && s(AC.show(J, "example.ember")).indexOf("From your gear: [example.burning]") >= 0, supports() + " / " + s(AC.show(J, "example.ember")).substring(0, 300));
	staff.unWear();
	check("off-stops", supports().indexOf("example.burning") < 0, supports());
	// socketed: a gem's ADDS gives it while its host is worn
	var amulet = item("a fxi05 amulet"); amulet.setRawProperLocationBitmap(W.WORN_NECK);
	var AS = CMClass.getAbility("Prop_ItemSlot"); AS.setMiscText("NUM=1 TYPE=Gems"); amulet.addNonUninvokableEffect(AS);
	var echo = gem("a fxi05 echo stone", "Gems", "Prop_ComposeSupport(SUPPORTS=example.power)");
	put(amulet, echo); amulet.wearAt(W.WORN_NECK);
	check("socketed-gives", supports().indexOf("example.power") >= 0, supports());
} finally {
	made.forEach(function (m) { if (!m.amDestroyed()) m.destroy(); });
	WorldFiles.configure(originalWorld); knownField.set(null, previousKnown); currentField.set(null, previousCampaign); AC.reset();
	var stream = Files.walk(folder.toPath()); try { var paths = stream.sorted(java.util.Comparator.reverseOrder()).iterator(); while (paths.hasNext()) Files.deleteIfExists(paths.next()); } finally { stream.close(); }
}
done();
