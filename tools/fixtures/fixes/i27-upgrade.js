// I27: ItemUpgrader rows (UPGRADEn, UPGRADES file) FROM TO ADJ COST MATERIALS LEVEL KEEP MSG; UPGRADE item [CONFIRM];
// Prop_UpgradePath on the item.
FIXFILE = "i27-upgrade";
var W = CM.Items.interfaces.Wearable;
var SE = CM.core.ScriptEvents;
function s(x) { return "" + x; }
function has(t, p) { return s(t).replace(/\s+/g, " ").indexOf(p) >= 0; }
function catItem(name) { var I = CMClass.getWeapon("GenWeapon"); I.setName(name); I.recoverPhyStats(); CMLib.catalog().addCatalog(I); I.destroy(); }
catItem("Starfall, the fixi27 blade");
var R = scratchRoom();
var V = newMob("GenMob", R); V.setName("Fixi27pip"); V.basePhyStats().setLevel(25); V.recoverPhyStats();
V.setPlayerStats(CMClass.getCommon("DefaultPlayerStats"));
var VS = CMClass.getCommon("FakeSession"); VS.initialize(null, "MEMORY"); V.setSession(VS); VS.setMob(V);
var mark = 0;
function out() { var all = s(field(VS, "bout").toString()); var o = all.substring(mark); mark = all.length; return o.replace(/\x1b\[[0-9;]*m/g, "").replace(/\^./g, ""); }
function cmd(line) { out(); var l = new java.util.ArrayList(); line.split(" ").forEach(function (w) { l.add(w); }); var took = SE.verb(V, l); return { took: took, said: out() }; }
function item(name) { var I = CMClass.getItem("GenItem"); I.setName(name); V.addItem(I); return I; }
var smith = CMClass.getMOB("GenMob"); smith.setName("Fixi27smith"); smith.bringToLife(R, true);
var B = CMClass.getBehavior("ItemUpgrader");
B.setParms("UPGRADE1=\"FROM=notched TO=\\\"catalog:Starfall, the fixi27 blade\\\" COST=200 MATERIALS=\\\"starmetal ingot x3\\\" LEVEL=20 KEEP=TAGS\" "
	+ "UPGRADE2=\"FROM=tag:FIXI27PLAIN ADJ=\\\"dam+2 att+5\\\" COST=10\"");
smith.addBehavior(B);
var blade = CMClass.getWeapon("GenWeapon"); blade.setName("a notched fixi27 blade"); blade.addTag("FIXI27OLD"); V.addItem(blade); blade.wearAt(W.WORN_WIELD);
var r = cmd("upgrade notched");
check("ask", r.took && has(r.said, "Starfall, the fixi27 blade") && has(r.said, "starmetal ingot x3") && (!blade.amDestroyed()), "'" + r.said + "'");
r = cmd("upgrade notched confirm");
var poor = has(r.said, "cannot afford");
CMLib.beanCounter().giveSomeoneMoney(V, 500.0);
r = cmd("upgrade notched confirm");
var lacking = has(r.said, "You need starmetal ingot x3");
var ingots = [item("a starmetal ingot"), item("a starmetal ingot"), item("a starmetal ingot")];
var gold0 = CMLib.beanCounter().getTotalAbsoluteNativeValue(V);
r = cmd("upgrade notched confirm");
var star = V.findItem(null, "Starfall");
check("upgrade", poor && lacking && blade.amDestroyed() && star != null && star.hasTag("FIXI27OLD") && star.rawWornCode() == W.WORN_WIELD
	&& ingots[0].amDestroyed() && Math.abs(CMLib.beanCounter().getTotalAbsoluteNativeValue(V) - (gold0 - 200)) < 0.01,
	"poor " + poor + " lacking " + lacking + " star " + star + " '" + r.said + "'");
// the same item, better
var club = CMClass.getWeapon("GenWeapon"); club.setName("a fixi27 club"); club.addTag("FIXI27PLAIN"); club.basePhyStats().setDamage(4); club.recoverPhyStats(); V.addItem(club);
cmd("upgrade club confirm");
check("adj", club.basePhyStats().damage() == 6 && club.basePhyStats().attackAdjustment() == 5 && (!club.amDestroyed()), "damage " + club.basePhyStats().damage() + " attack " + club.basePhyStats().attackAdjustment());
// its own path, at an anvil
var anvil = CMClass.getItem("GenItem"); anvil.setName("a fixi27 anvil"); CMLib.flags().setGettable(anvil, false); R.addItem(anvil);
anvil.addBehavior(CMClass.getBehavior("ItemUpgrader"));
var axe = CMClass.getWeapon("GenWeapon"); axe.setName("a fixi27 axe"); V.addItem(axe);
var P = CMClass.getAbility("Prop_UpgradePath"); P.setMiscText("ADJ=\"dam+3\" LEVEL=30"); axe.addNonUninvokableEffect(P);
r = cmd("upgrade axe confirm");
check("path", has(r.said, "level 30") && has(P.accountForYourself(), "upgraded"), "'" + r.said + "'");
check("help", has(CMLib.help().getHelpText("BEHAVIOR_ITEMUPGRADER", M, true), "CONFIRM") && has(CMLib.help().getHelpText("PROP_UPGRADEPATH", M, true), "ADJ"), "no help");

V.setPlayerStats(null); V.destroy(); smith.destroy(); anvil.destroy();
var c = CMLib.catalog().getCatalogItem("Starfall, the fixi27 blade"); if (c != null) CMLib.catalog().delCatalog(c);
done();
