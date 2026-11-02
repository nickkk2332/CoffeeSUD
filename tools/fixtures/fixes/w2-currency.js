// W2: the currency file (a world's resources/currencies.ini), DEFAULT, a code defined twice, VALUE() of every code.
FIXFILE = "w2-currency";
function s(x) { return "" + x; }
function has(t, p) { return s(t).indexOf(p) >= 0; }
function writeFile(F, t) { F.getParentFile().mkdirs(); java.nio.file.Files.write(F.toPath(), new java.lang.String(t).getBytes("UTF-8")); }
var BC = CMLib.beanCounter();
var F = new java.io.File(new java.io.File(WorldFiles.worldDir(), "resources"), "currencies.ini");
writeFile(F, "# a test world's coins\nDEFAULT=CROWN\nCROWN=1 copper penny(s) (cp);12 silver shilling(s) (ss);240 gold crown(s) (gc)\nTRADEBAR=notrade;1 trade bar(s) (tb)\n");
BC.reloadCurrencies();
var R = M.location();
var A = R.getArea();
var oldCurrency = s(A.getRawCurrency());
A.setCurrency("");
var crown = BC.getCurrencySet("CROWN");
var bar = BC.getCurrencySet("TRADEBAR");
check("file", crown != null && crown.denominations().length == 3 && bar != null && (!bar.canTrade()) && BC.getAllCurrencies().contains("CROWN"),
	"crown " + crown + " bar " + bar);
// an area with no currency of its own has the world's default, not gold coins
check("default", s(BC.getCurrency(R)) == "CROWN" && s(BC.nameCurrencyLong(M, 36)) == "3 silver shillings" && s(BC.getCurrencySet("").ID()) == "CROWN",
	"currency " + BC.getCurrency(R) + " / " + BC.nameCurrencyLong(M, 36));
// an area that defines CROWN otherwise keeps the file's (and the log says so); one naming a code alone gets it
A.setCurrency("CROWN=1 copper bit(s) (cb)");
var kept = BC.getCurrencySet("CROWN").denominations().length == 3;
A.setCurrency("TRADEBAR");
var named = s(BC.getCurrency(R)) == "TRADEBAR";
A.setCurrency("");
check("twice", kept && named && BC.getCurrencySet("CROWN") != null, "kept " + kept + " named " + named);
// VALUE() knows the built-in codes and the file's
var V = newMob("GenMob", R);
V.setName("Fixw2purse");
BC.giveSomeoneMoney(V, "GOLD", 10.0);
BC.giveSomeoneMoney(V, "CROWN", 30.0);
clearVar("W2G"); clearVar("W2C");
var host = scriptedMob("Fixw2clerk", "RAND_PROG 100\nif value(Fixw2purse GOLD > 5)\n mpsetvar FIXTEST W2G yes\nendif\nif value(Fixw2purse CROWN >= 30)\n mpsetvar FIXTEST W2C yes\nendif\n~\n");
tickMob(host);
check("value", gvar("W2G") == "yes" && gvar("W2C") == "yes", "gold " + gvar("W2G") + " crown " + gvar("W2C"));
// world.ini's keys
check("world-ini", CM.core.save.WorldSettings.isWorldKey("DEFAULTCURRENCY") && CM.core.save.WorldSettings.isWorldKey("CURRENCY_LEAF"), "not world keys");
// with the file gone, the old default comes back
F["delete"]();
BC.reloadCurrencies();
check("gone", s(BC.getCurrency(R)) == "" && BC.getCurrencySet("TRADEBAR") == null && has(BC.nameCurrencyLong(M, 3), "gold coin"),
	"currency '" + BC.getCurrency(R) + "' / " + BC.nameCurrencyLong(M, 3));
check("help", has(CMLib.help().getHelpText("CURRENCY_FILE", M, true), "DEFAULT=CROWN"), "no help");
A.setCurrency(oldCurrency);
host.destroy(); V.destroy();
done();
