// W29: MoneyChanger's RATE.<CODE>=+/-pct, MPEXCHANGE and EXCHANGE(), kept in the save's header.
FIXFILE = "w29-exchange";
function s(x) { return "" + x; }
function has(t, p) { return s(t).indexOf(p) >= 0; }
var Exchange = CM.core.Exchange;
var R = M.location();
function changer(name, parms) {
	var m = newMob("GenMob", R);
	m.setName(name);
	var B = CMClass.getBehavior("MoneyChanger");
	B.setParms(parms);
	m.addBehavior(B);
	return [m, B];
}
var plain = changer("Fixw29plain", "CUT=5");
var up = changer("Fixw29up", "CUT=5 RATE.COPPER=+10");
var down = changer("Fixw29down", "CUT=5 RATE.COPPER=-30");
function got(c, code, v) { return c[1].exchangeValue(c[0], code, v); }
check("premium", Math.abs(got(up, "COPPER", 100) - got(plain, "COPPER", 110)) < 0.001 && Math.abs(got(down, "COPPER", 100) - got(plain, "COPPER", 70)) < 0.001
	&& Math.abs(got(up, "GOLD", 100) - got(plain, "GOLD", 100)) < 0.001,
	"up " + got(up, "COPPER", 100) + " plain110 " + got(plain, "COPPER", 110) + " down " + got(down, "COPPER", 100));
// a RATE is no fee, so the changer still takes every currency
check("still-takes", call(down[1], "doIExchangeThisCurrency", [down[0], "GOLD"]) == true, "a RATE made it refuse gold");
// world-wide, by script
clearVar("W29E");
var host = scriptedMob("Fixw29clerk", "RAND_PROG 100\nmpexchange COPPER 70\nmpsetvar FIXTEST W29E $%EXCHANGE(COPPER)%\n~\n");
tickMob(host);
check("world", gvar("W29E") == "70" && Math.abs(got(plain, "COPPER", 100) - got(plain, "GOLD", 70)) < 0.001
	&& Math.abs(got(down, "COPPER", 100) - got(plain, "GOLD", 49)) < 0.001, "rate " + gvar("W29E") + " plain " + got(plain, "COPPER", 100) + " gold70 " + got(plain, "GOLD", 70));
var saved = s(Exchange.saved());
Exchange.restore(null);
var cleared = Exchange.percent("COPPER");
Exchange.restore(saved);
check("save", saved == "COPPER=70.0" && cleared == 100 && Exchange.percent("COPPER") == 70, "saved '" + saved + "' cleared " + cleared);
Exchange.set("COPPER", 100);
check("off", s(Exchange.saved()) == "" && Exchange.percent("COPPER") == 100, Exchange.saved());
var help = CMLib.help().getHelpText("BEHAVIOR_SCRIPTABLE", M, true);
check("help", has(help, "MPEXCHANGE [CURRENCY]") && has(help, "EXCHANGE([CURRENCY])") && has(CMLib.help().getHelpText("MONEYCHANGER", M, true), "RATE."), "no help");
host.destroy(); plain[0].destroy(); up[0].destroy(); down[0].destroy();
done();
