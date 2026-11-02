// W17: MONEYSTR, PRICE and DENOMNAME, and MONEYSTR$ in a script's JavaScript.
FIXFILE = "w17-money";
function s(x) { return "" + x; }
function has(t, p) { return s(t).indexOf(p) >= 0; }
function writeFile(F, t) { F.getParentFile().mkdirs(); java.nio.file.Files.write(F.toPath(), new java.lang.String(t).getBytes("UTF-8")); }
var BC = CMLib.beanCounter();
var F = new java.io.File(new java.io.File(WorldFiles.worldDir(), "resources"), "currencies.ini");
writeFile(F, "CROWN=1 copper bit(s) (cb);12 silver shilling(s) (ss);240 gold crown(s) (gc)\n");
BC.reloadCurrencies();
var R = M.location();
// a shop with a lantern
var keeper = newMob("GenShopkeeper", R);
keeper.setName("Fixw17trader");
var lantern = CMClass.getItem("GenItem");
lantern.setName("a fixw17 lantern");
lantern.setBaseValue(30);
lantern.recoverPhyStats();
keeper.getShop().addStoreInventory(lantern, 2, -1);
var stocked = keeper.getShop().getStock("fixw17 lantern", M);
clearVar("W17L"); clearVar("W17S"); clearVar("W17D"); clearVar("W17P"); clearVar("W17J"); clearVar("W17T");
var host = scriptedMob("Fixw17clerk", "RAND_PROG 100\n"
	+ "mpsetvar FIXTEST W17L $%MONEYSTR(36 CROWN LONG)%\n"
	+ "mpsetvar FIXTEST W17S $%MONEYSTR(37 CROWN SHORT)%\n"
	+ "mpsetvar FIXTEST W17T $%MONEYSTR(37 CROWN)%\n"
	+ "mpsetvar FIXTEST W17D $%DENOMNAME(CROWN 12 3)%\n"
	+ "mpsetvar FIXTEST W17P $%PRICE('fixw17 lantern' Fixw17trader)%\n"
	+ "<SCRIPT>\nMPSETVAR('FIXTEST', 'W17J', MONEYSTR$(250, 'CROWN', 'LONG'));\n</SCRIPT>\n~\n");
// RAND_PROG's $n is the host: the price is the one it would pay
var want = s(BC.nameCurrencyLong(BC.getCurrency(keeper), CMLib.coffeeShops().sellingPrice(keeper, host, stocked, keeper, keeper.getShop(), true).absoluteGoldPrice));
tickMob(host);
check("moneystr", gvar("W17L") == "3 silver shillings" && gvar("W17T") == "3 silver shillings, 1 copper bit" && gvar("W17S").length > 0 && gvar("W17S") != gvar("W17T"),
	"long '" + gvar("W17L") + "' 37 '" + gvar("W17T") + "' short '" + gvar("W17S") + "'");
check("denomname", gvar("W17D") == "3 silver shillings", gvar("W17D"));
check("price", want.length > 0 && gvar("W17P") == want, "price '" + gvar("W17P") + "' want '" + want + "'");
check("js", has(gvar("W17J"), "1 gold crown") && has(gvar("W17J"), "10 copper bits"), gvar("W17J"));
var help = CMLib.help().getHelpText("BEHAVIOR_SCRIPTABLE", M, true);
check("help", has(help, "MONEYSTR([AMOUNT]") && has(help, "DENOMNAME([CURRENCY]"), "no help");
host.destroy(); keeper.destroy();
F["delete"]();
BC.reloadCurrencies();
done();
