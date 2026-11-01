// DT22: PRICECHECK shows how a shop's price was reached, and where each setting came from.
FIXFILE = "dt22-pricecheck";
var R = M.location();
var A = R.getArea();
function has(text, part) { return ("" + text).indexOf(part) >= 0; }
function num(d) { var s = Number(d).toFixed(3); s = s.replace(/0+$/, ""); return s.replace(/\.$/, ""); }
var B = newMob("GenMob", R);
B.setName("Fixpricer");
B.baseCharStats().setMyRace(CMClass.getRace("Human"));
B.recoverCharStats();
var BS = CMClass.getCommon("FakeSession");
BS.initialize(null, "MEMORY");
B.setSession(BS);
BS.setMob(B);
var mark = 0;
function out() { var all = "" + field(BS, "bout").toString(); var s = all.substring(mark); mark = all.length; return s; }
function pc(words) { var L = new java.util.ArrayList(); L.add("PRICECHECK"); var w = words.split(" "); for (var i = 0; i < w.length; i++) L.add(w[i]); CMClass.getCommand("PriceCheckCmd").execute(B, L, 0); return out(); }
function run(cmdName, words) { var L = new java.util.ArrayList(); var w = words.split(" "); for (var i = 0; i < w.length; i++) L.add(w[i]); CMClass.getCommand(cmdName).execute(M, L, 0); }

check("no-shop", has(pc("cloak"), "There is no shopkeeper here."), "no shopkeeper yet");
var K = newMob("GenShopkeeper", R);
K.setName("Fixmerchant");
K.setPrejudiceFactors("BUY=0.5 HUMAN");
var made = CMClass.getItem("GenItem");
made.setName("a fixes cloak");
made.setMaterial(CM.Items.interfaces.RawMaterial.RESOURCE_COTTON);
made.setBaseValue(100);
made.recoverPhyStats();
K.getShop().addStoreInventory(made, 1, -1);
var cloak = K.getShop().getStock("cloak", B);
check("stocked", cloak != null, "the cloak is not in stock");
check("not-found", has(pc("flurble"), "has 'flurble'"), "an unknown item");

// a quest gives the area a price mask: the setting says so
var oldMasks = A.getRawItemPricingAdjustments();
new CM.core.CMFile("/resources/_fixes/dt22.quest", M).saveText("set name dt22quest\nset duration 1000\nset area \"" + A.Name() + "\"\ngive stat PRICEMASKS 10.0 -MATERIAL +CLOTH\nstep break\n");
run("Create", "CREATE QUEST LOAD=_fixes/dt22.quest");
var Q = CMLib.quests().fetchQuest("dt22quest");
check("quest", Q != null, "no quest dt22quest");
if (Q != null && !Q.running()) Q.startQuest();
var said = pc("cloak");
check("header", has(said, "Pricing a fixes cloak at Fixmerchant for Fixpricer:"), said);
check("prejudice-set", has(said, "PREJUDICE BUY=0.5 HUMAN, set on Fixmerchant"), said);
check("masks-quest", has(said, "PRICEMASKS 10.0 -MATERIAL +CLOTH, the area " + A.Name() + ", given by the quest dt22quest"), said);
check("budget-ini", has(said, "BUDGET ") && has(said, "the ini (BUDGET)"), said);
check("steps", has(said, "Base price: 100, from the item's value.") && has(said, "Prejudice x0.5: the rule 'BUY=0.5 HUMAN' matched human.")
	&& has(said, "Price mask x10: '10.0 -MATERIAL +CLOTH' matched.") && has(said, "After prejudice and price masks (x5): 500.")
	&& has(said, "of Fixpricer against wisdom ") && has(said, "Rounded to the coins: "), said);
var sells = CMLib.coffeeShops().sellingPrice(K, B, cloak, K, K.getShop(B), true).absoluteGoldPrice;
check("sells-same", has(said, "The shop sells it for: ") && has(said, "(" + num(sells) + " in base value).") && has(said, "Sells for "), "the shop asks " + sells + ": " + said);

// SELL: what the shop pays, with the rules for buying from a customer
said = pc("cloak SELL");
var pays = CMLib.coffeeShops().pawningPrice(K, B, cloak, K).absoluteGoldPrice;
check("sell-steps", has(said, "What the shop pays for it, step by step:") && has(said, "Prejudice: no SELL rule matched (x1).")
	&& has(said, "The shop pays: ") && has(said, "(" + num(pays) + " in base value)."), "the shop pays " + pays + ": " + said);
check("sell-devalue", (K.getFinalDevalueRate() == null) || (K.getFinalDevalueRate()[0] <= 0) || has(said, "in stock (DEVALRATE)"), said);
Q.stopQuest();
CMLib.quests().delQuest(Q);
check("quest-undone", A.getRawItemPricingAdjustments().length == oldMasks.length, "the area's masks after the quest: " + A.getRawItemPricingAdjustments().length);

// no masks: the ini's; then the shop's own budget and stock reset, after a tick
K.setBudget("1000 DAY");
K.setInvResetRate(500);
var spare = made.copyOf();
tickMob(K);
// the reset put back the stock the shop was made with, which had no cloak
K.getShop().addStoreInventory(spare, 1, -1);
said = pc("cloak AT Fixmerchant FOR Fixpricer");
check("budget-own", has(said, "BUDGET 1000 a day, set on Fixmerchant") && has(said, "Budget left: ") && has(said, "(1000). It resets in "), said);
check("reset", has(said, "INVRESETRATE 500 ticks, set on Fixmerchant") && has(said, "The stock is put back in 500 ticks"), said);
check("at-unknown", has(pc("cloak AT nobodyfix"), "There is no shopkeeper 'nobodyfix' here."), "an unknown shopkeeper");

B.setSession(null);
B.destroy(); K.destroy();
done();
