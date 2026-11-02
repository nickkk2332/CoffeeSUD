// W15: BANKBALANCE and DEBT; MPBANK and MPDEBT; cm.bank()'s pay, debt, lend and forgive.
FIXFILE = "w15-bank";
function s(x) { return "" + x; }
function has(t, p) { return s(t).indexOf(p) >= 0; }
var Banks = CM.core.Banks;
var R = M.location();
var saver = newMob("GenMob", R);
saver.setName("Fixw15saver");
function runScript(body) { var m = scriptedMob("Fixw15clerk", "RAND_PROG 100\n" + body + "\n~\n"); tickMob(m); m.destroy(); }
function bal() { clearVar("W15B"); runScript("mpsetvar FIXTEST W15B $%BANKBALANCE(Fixw15saver 'Fixw15 Trust')%"); return gvar("W15B"); }
function debt(due) { clearVar("W15D"); runScript("mpsetvar FIXTEST W15D $%DEBT(Fixw15saver Vargo" + (due ? " DUE" : "") + ")%"); return gvar("W15D"); }
var none = bal();
runScript("mpbank Fixw15saver 'Fixw15 Trust' +100 the wolf bounty");
var paid = bal();
runScript("mpbank Fixw15saver 'Fixw15 Trust' -30 a fee");
var less = bal();
runScript("mpbank Fixw15saver 'Fixw15 Trust' -500 too much");
var kept = bal();
check("bank", none == "0" && paid == "100" && less == "70" && kept == "70", none + " " + paid + " " + less + " " + kept);
// debts, with interest and a due date on the game clock
var hours = R.getArea().getTimeObj().getHoursInDay();
runScript("mpdebt Fixw15saver Vargo +200 INTEREST 5 DUE 2 DAYS FOR a loan");
var owed = debt(false), due = Number(debt(true));
var D = CMLib.beanCounter().getDebt("Fixw15saver", "Vargo").get(0);
check("debt", owed == "200" && due <= 2 * hours && due >= 2 * hours - 1 && Math.abs(D.interest() - 0.05) < 0.0001 && s(D.reason()) == "a loan",
	"owed " + owed + " due " + due + " of " + hours + " interest " + D.interest() + " reason " + D.reason());
runScript("mpdebt Fixw15saver Vargo -50");
var after = debt(false);
var testScript = "if debt(Fixw15saver Vargo >= 150)\n mpsetvar FIXTEST W15T yes\nendif";
clearVar("W15T"); runScript(testScript);
var tested = gvar("W15T");
runScript("mpdebt Fixw15saver Vargo CLEAR");
check("pay-clear", after == "150" && tested == "yes" && debt(false) == "0" && debt(true) == "", "after " + after + " test " + tested + " then " + debt(false) + "/" + debt(true));
// JavaScript, through a banker of the chain
var banker = newMob("GenBanker", R);
banker.setName("Fixw15banker");
banker.setBankChain("Fixw15 Trust");
var B = cm.bank(banker);
var ok1 = B.pay(saver, 25, "a gift");
B.lend(saver, 40, 3, 10);
var lent = B.debt(saver);
B.forgive(saver);
check("js", ok1 && bal() == "95" && lent == 40 && B.debt(saver) == 0 && (!B.pay(saver, -1000, "no")), "balance " + bal() + " lent " + lent);
var help = CMLib.help().getHelpText("BEHAVIOR_SCRIPTABLE", M, true);
check("help", has(help, "MPBANK [CODE] [BANK CHAIN]") && has(help, "MPDEBT [CODE]") && has(help, "BANKBALANCE([CODE]") && has(help, "DEBT([CODE] [TO WHOM]"), "no help");
runScript("mpbank Fixw15saver 'Fixw15 Trust' -95 closed");
check("closed", bal() == "0", "balance " + bal());
banker.destroy(); saver.destroy();
done();
