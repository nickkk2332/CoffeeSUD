// W14: MPSHOP REMOVE, STOCK, PRICE, CLEAR, RESET and BUDGET; SHOPPRICE and SHOPBUDGET; cm.shop()'s new members.
FIXFILE = "w14-shop";
function s(x) { return "" + x; }
function has(t, p) { return s(t).indexOf(p) >= 0; }
var Shops = CM.core.Shops;
var R = M.location();
var keeper = newMob("GenShopkeeper", R);
keeper.setName("Fixw14smith");
function item(name, value) { var I = CMClass.getItem("GenItem"); I.setName(name); I.setBaseValue(value); I.recoverPhyStats(); return I; }
keeper.getShop().addStoreInventory(item("an iron ingot", 10), 5, -1);
keeper.getShop().addStoreInventory(item("a fixw14 hammer", 40), 2, -1);
keeper.setMiscText(keeper.text()); // the stock as built, for RESET
function count(name) { var P = Shops.shelf(keeper, name); return (P == null) ? 0 : P.number; }
function runScript(body) { var m = scriptedMob("Fixw14clerk", "RAND_PROG 100\n" + body + "\n~\n"); tickMob(m); m.destroy(); }
var built = count("iron ingot") + "/" + count("fixw14 hammer");
runScript("mpshop Fixw14smith REMOVE 'iron ingot' 2");
var two = count("iron ingot");
runScript("mpshop Fixw14smith REMOVE 'iron ingot' ALL");
var none = count("iron ingot");
check("remove", built == "5/2" && two == 3 && none == 0 && Shops.shelf(keeper, "iron ingot") == null, built + " " + two + " " + none);
runScript("mpshop Fixw14smith STOCK 'fixw14 hammer' 7");
var floor = item("a fixw14 lantern", 25);
R.addItem(floor);
runScript("mpshop Fixw14smith STOCK 'fixw14 lantern' 3");
check("stock", count("fixw14 hammer") == 7 && count("fixw14 lantern") == 3 && keeper.getShop().getStock("fixw14 lantern", M) != floor, count("fixw14 hammer") + " " + count("fixw14 lantern"));
// a price set, as SHOPPRICE sees it (for RAND_PROG's $n, the host)
clearVar("W14A"); clearVar("W14B"); clearVar("W14G");
runScript("mpsetvar FIXTEST W14A $%SHOPPRICE(Fixw14smith 'fixw14 hammer')%\nmpshop Fixw14smith PRICE 'fixw14 hammer' 500\nmpsetvar FIXTEST W14B $%SHOPPRICE(Fixw14smith 'fixw14 hammer')%\n"
	+ "if shopprice(Fixw14smith 'fixw14 hammer' > 400)\n mpsetvar FIXTEST W14G yes\nendif");
check("price", Shops.shelf(keeper, "fixw14 hammer").price == 500 && gvar("W14A") != gvar("W14B") && Number(gvar("W14B")) >= 500 && gvar("W14G") == "yes",
	"before " + gvar("W14A") + " after " + gvar("W14B") + " test " + gvar("W14G"));
// the budget
clearVar("W14U"); clearVar("W14V");
runScript("mpsetvar FIXTEST W14U $%SHOPBUDGET(Fixw14smith)%\nmpshop Fixw14smith BUDGET 250\nmpsetvar FIXTEST W14V $%SHOPBUDGET(Fixw14smith)%");
check("budget", gvar("W14U") == "-1" && gvar("W14V") == "250" && keeper.budgetLeft() == 250, gvar("W14U") + " / " + gvar("W14V"));
// clear, then put back as built
runScript("mpshop Fixw14smith CLEAR");
var cleared = keeper.getShop().totalStockSize();
runScript("mpshop Fixw14smith RESET");
check("reset", cleared == 0 && count("iron ingot") == 5 && count("fixw14 hammer") == 2 && count("fixw14 lantern") == 0, "cleared " + cleared + " ingots " + count("iron ingot"));
// JavaScript
var J = cm.shop(keeper);
var took = J.remove("iron ingot", 1);
J.setBudget(90);
check("js", took == 1 && count("iron ingot") == 4 && J.stock("fixw14 hammer", 9) && count("fixw14 hammer") == 9 && J.setPrice("fixw14 hammer", 77)
	&& Shops.shelf(keeper, "fixw14 hammer").price == 77 && J.budget() == 90 && J.reset() && count("iron ingot") == 5, "took " + took + " ingots " + count("iron ingot"));
var help = CMLib.help().getHelpText("BEHAVIOR_SCRIPTABLE", M, true);
check("help", has(help, "MPSHOP [SHOPKEEPER] REMOVE") && has(help, "SHOPPRICE([SHOPKEEPER]") && has(help, "SHOPBUDGET("), "no help");
floor.destroy(); keeper.destroy();
done();
