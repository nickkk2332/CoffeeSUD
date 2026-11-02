// N21: Hireling terms: KEYWORDS=, MSG_OFFER=/MSG_HIRED=, CONTRACT=QUEST:/DAYS/UNTILFIRED, UPKEEP= (two unpaid days and it
// leaves), REFUSE= orders, FLEEHP= unless LOYAL= (its opinion), the HIRE menu and HIRE PAY, the saved state.
FIXFILE = "n21-hireling";
var SM = CM.core.ScriptMenus;
var NA = CM.core.NpcAffinity;
function s(x) { return "" + x; }
function has(text, part) { return s(text).replace(/\s+/g, " ").indexOf(part) >= 0; }
var R = scratchRoom();
var pip = newMob("GenMob", R);
pip.setName("Fixn21pip");
var PSn = new JavaAdapter(CM.Common.FakeSession, { isFake: function() { return false; } });
PSn.initialize(null, "MEMORY"); pip.setSession(PSn); PSn.setMob(pip);
pip.setPlayerStats(CMClass.getCommon("DefaultPlayerStats"));
var mark = 0;
function out() { var all = s(field(PSn, "bout").toString()); var o = all.substring(mark); mark = all.length; return o.replace(/\x1b\[[0-9;]*m/g, "").replace(/\^./g, ""); }
function words(w) { var L = new java.util.ArrayList(); w.split(" ").forEach(function (x) { if (x.length > 0) L.add(x); }); return L; }
function hireling(name, parms) {
	var m = newMob("GenMob", R);
	m.setName(name);
	var B = CMClass.getBehavior("Hireling");
	B.setParms(parms);
	m.addBehavior(B);
	return { mob: m, B: B };
}
var merc = hireling("Fixn21merc", "50;2;KEYWORDS=SWORD,MERCENARY MSG_OFFER=\"Fixn21 @x1 buys my blade @x2.\" MSG_HIRED=\"Fixn21 my blade is yours @x1.\" "
	+ "CONTRACT=\"QUEST:Fixn21 War\" UPKEEP=5 REFUSE=STEAL,KILL MSG_NOORDER=\"Fixn21 not that.\"");
var cur = CMLib.beanCounter().getCurrency(merc.mob);
CMLib.beanCounter().addMoney(pip, cur, 200.0);
function money() { return CMLib.beanCounter().getTotalAbsoluteValue(pip, cur); }
out();
R.send(pip, CMClass.getMsg(pip, merc.mob, null, CMMsg.MSG_SPEAK, "<S-NAME> say(s) to <T-NAMESELF> 'need a sword?'."));
check("keywords", has(out(), "until the Fixn21 War is done.") , "no offer");
R.send(pip, CMClass.getMsg(pip, merc.mob, null, CMMsg.MSG_SPEAK, "<S-NAME> say(s) to <T-NAMESELF> 'are you for hire?'."));
check("old-keyword", !has(out(), "Fixn21"), "an old keyword answered");
// HIRE: the terms, a menu; PAY
// (this session answers a menu at once, from the input queued: 1, the first choice)
var before = money();
PSn.setFakeInput("1");
CMClass.getCommand("Hire").execute(pip, words("HIRE Fixn21merc"), 0);
var hired = out();
check("menu", has(hired, "to work until the Fixn21 War is done, and 5") && has(hired, "Hire Fixn21merc"), hired);
check("hired", merc.mob.amFollowing() == pip && has(hired, "Fixn21 my blade is yours until the Fixn21 War is done.") && money() == before - 50,
	"following " + merc.mob.amFollowing() + " money " + money() + " / " + hired);
// orders it will not take
check("refuse", !merc.mob.okMessage(merc.mob, CMClass.getMsg(pip, merc.mob, null, CMMsg.MSG_SPEAK, CMMsg.MSG_ORDER, CMMsg.MSG_SPEAK,
	"^T<S-NAME> order(s) <T-NAMESELF> to 'steal sword from bob'^?.")) && has(out(), "Fixn21 not that."), "it took the order");
check("orders", merc.mob.okMessage(merc.mob, CMClass.getMsg(pip, merc.mob, null, CMMsg.MSG_SPEAK, CMMsg.MSG_ORDER, CMMsg.MSG_SPEAK,
	"^T<S-NAME> order(s) <T-NAMESELF> to 'say hi'^?.")), "it refused an order it may take");
// wages: a day on, paid; broke, two unpaid days, gone
var day = CMProps.getMillisPerMudHour() * CMLib.time().globalClock().getHoursInDay();
function dayOn(B) { var f = B.getClass().getDeclaredField("lastUpkeep"); f.setAccessible(true); f.setLong(B, f.getLong(B) - day - 10); }
function tick(h) { h.B.tick(h.mob, Tickable.TICKID_MOB); }
dayOn(merc.B);
before = money();
tick(merc);
check("upkeep", money() == before - 5, "money " + before + " -> " + money());
var state = s(merc.B.getStat("STATE"));
check("state", has(state, "<FOR>Fixn21pip</FOR>") && has(state, "PAIDAGO"), state);
CMLib.beanCounter().subtractMoney(pip, cur, money());
dayOn(merc.B); tick(merc);
check("unpaid-1", merc.mob.amFollowing() == pip && has(out(), "I've not been paid"), "it left at once");
dayOn(merc.B); tick(merc);
check("unpaid-2", s(merc.B.getStat("WORKING")) == "" && merc.mob.amFollowing() == null, "still working for " + merc.B.getStat("WORKING"));

// a quest contract ends with the quest
R.bringMobHere(merc.mob, false);
CMLib.beanCounter().addMoney(pip, cur, 100.0);
var coins = CMLib.beanCounter().makeBestCurrency(cur, 50.0); pip.addItem(coins);
R.send(pip, CMClass.getMsg(pip, merc.mob, coins, CMMsg.MSG_GIVE, "<S-NAME> give(s) <O-NAME> to <T-NAMESELF>."));
check("rehired", s(merc.B.getStat("WORKING")) == "Fixn21pip", "not hired again");
CM.core.QuestRecords.won("Fixn21 War", "Fixn21pip");
tick(merc);
check("quest-ends", s(merc.B.getStat("WORKING")) == "" , "still working after the war");

// nerve: FLEEHP, unless loyal
function hireDirect(h) {
	var c = CMLib.beanCounter().makeBestCurrency(cur, 10.0); pip.addItem(c);
	R.send(pip, CMClass.getMsg(pip, h.mob, c, CMMsg.MSG_GIVE, "<S-NAME> give(s) <O-NAME> to <T-NAMESELF>."));
}
var foe = newMob("GenMob", R); foe.setName("Fixn21foe");
var coward = hireling("Fixn21coward", "10;60;CONTRACT=UNTILFIRED FLEEHP=50 LOYAL=30");
var brave = hireling("Fixn21brave", "10;60;CONTRACT=UNTILFIRED FLEEHP=50 LOYAL=30");
CMLib.beanCounter().addMoney(pip, cur, 50.0);
hireDirect(coward); hireDirect(brave);
NA.set(brave.mob, "Fixn21pip", 40);
[coward, brave].forEach(function (h) { h.mob.curState().setHitPoints(1); h.mob.setVictim(foe); });
tick(coward); tick(brave);
check("flees", s(coward.B.getStat("WORKING")) == "" && !coward.mob.isInCombat(), "the coward stayed");
check("loyal", s(brave.B.getStat("WORKING")) == "Fixn21pip", "the loyal one left");
brave.mob.makePeace(true); foe.makePeace(true);

[merc.mob, coward.mob, brave.mob, foe].forEach(function (m) { m.destroy(); });
pip.setPlayerStats(null); pip.destroy();
R.destroy();
done();
