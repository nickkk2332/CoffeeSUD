// W16: {MATERIAL= WEIGHT= DESC=} after a denomination: the coins' material, weight and look.
FIXFILE = "w16-coins";
function s(x) { return "" + x; }
function has(t, p) { return s(t).indexOf(p) >= 0; }
function writeFile(F, t) { F.getParentFile().mkdirs(); java.nio.file.Files.write(F.toPath(), new java.lang.String(t).getBytes("UTF-8")); }
var RM = CM.Items.interfaces.RawMaterial;
var BC = CMLib.beanCounter();
var F = new java.io.File(new java.io.File(WorldFiles.worldDir(), "resources"), "currencies.ini");
writeFile(F, "CROWN=1 copper bit(s) (cb);12 silver shilling(s) (ss) {MATERIAL=SILVER WEIGHT=0.02 DESC=\"A thin disc stamped with the crowned stag; worn smooth.\"};240 gold crown(s) (gc)\n"
	+ "HACK=1 iron hacksilver bit(s) (hb) {WEIGHT=0.5 DESC=\"A heavy lump of dwarven iron.\"}\n"
	+ "BEAD=1 moon bead(s) (mb) {MATERIAL=GLASS}\n");
BC.reloadCurrencies();
var crown = BC.getCurrencySet("CROWN");
var ss = crown.denominations()[1];
check("parsed", crown.denominations().length == 3 && s(ss.name()) == "silver shilling(s)" && s(ss.abbr()) == "ss" && ss.material() == RM.RESOURCE_SILVER
	&& ss.weight() == 0.02 && s(ss.description()) == "A thin disc stamped with the crowned stag; worn smooth." && crown.denominations()[0].material() == -1,
	"denominations " + crown.denominations().length + " name " + ss.name() + " desc " + ss.description());
var shillings = BC.makeCurrency("CROWN", 12.0, 100);
check("shilling", shillings.material() == RM.RESOURCE_SILVER && s(shillings.description()).indexOf("A thin disc stamped with the crowned stag; worn smooth. Equal to 12 copper bits") == 0
	&& shillings.basePhyStats().weight() == 2, "material " + shillings.material() + " weight " + shillings.basePhyStats().weight() + " desc " + shillings.description());
var hack = BC.makeCurrency("HACK", 1.0, 10);
check("heavy", hack.basePhyStats().weight() == 5 && s(hack.description()) == "A heavy lump of dwarven iron." && hack.material() == RM.RESOURCE_IRON,
	"weight " + hack.basePhyStats().weight() + " desc " + hack.description() + " material " + hack.material());
var beads = BC.makeCurrency("BEAD", 1.0, 3);
var bits = BC.makeCurrency("CROWN", 1.0, 300);
check("glass", beads.material() == RM.RESOURCE_GLASS && s(beads.description()) == "" && bits.basePhyStats().weight() == 3 && bits.material() == RM.RESOURCE_COPPER,
	"beads " + beads.material() + " bits " + bits.basePhyStats().weight() + "/" + bits.material());
check("help", has(CMLib.help().getHelpText("CURRENCY_FILE", M, true), "{MATERIAL="), "no help");
shillings.destroy(); hack.destroy(); beads.destroy(); bits.destroy();
F["delete"]();
BC.reloadCurrencies();
done();
