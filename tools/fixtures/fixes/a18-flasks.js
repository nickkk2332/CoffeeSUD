// A18 flasks and charms on Prop_Invokable: REFUSE=HEALTHY,NOAILMENT does nothing and spends nothing when there is
// nothing to do (REFUSEMSG); REFILL=KILL:n gives charges back for the holder's kills; AUTO=HP<n uses it by itself when
// its holder falls below n% health, once a tick and not in a tick it was used by hand; IDENTIFY's text names all three.
FIXFILE = "a18-flasks";
var W = CM.Items.interfaces.Wearable;
var TPS = Tickable.TICKID_PROPERTY_SPECIAL;
function s(x) { return "" + x; }
function has(t, p) { return s(t).replace(/\s+/g, " ").indexOf(p) >= 0; }
var R = scratchRoom(), made = [];
function npc(name) { var m = newMob("GenMob", R); m.setName(name); m.baseState().setHitPoints(100); m.recoverMaxState(); m.resetToMaxState(); made.push(m); return m; }
var V = npc("Fixa18 drinker");
V.setPlayerStats(CMClass.getCommon("DefaultPlayerStats"));
var VS = CMClass.getCommon("FakeSession"); VS.initialize(null, "MEMORY"); V.setSession(VS); VS.setMob(V);
var mark = 0;
function out() { var all = s(field(VS, "bout").toString()); var o = all.substring(mark); mark = all.length; return o.replace(/\x1b\[[0-9;]*m/g, "").replace(/\^./g, ""); }
try {
	// a healing flask: refused at full health, nothing spent; used when hurt
	var flask = CMClass.getItem("GenItem"); flask.setName("a fixa18 red flask"); V.addItem(flask); flask.setRawWornCode(W.WORN_HELD);
	var F = CMClass.getAbility("Prop_Invokable");
	F.setMiscText("VERB=QUAFF SPELLS=Prayer_CureLight TARGET=SELF CHARGES=3 REQUIRE=HELD REFUSE=HEALTHY REFILL=KILL:1 AUTO=HP<30");
	flask.addNonUninvokableEffect(F);
	out(); call(F, "use", [V]);
	var said = out();
	check("refuse-healthy", F.left() == 3 && has(said, "You are not hurt;") && has(said, "would do nothing."), "left " + F.left() + " '" + said + "'");
	V.curState().setHitPoints(60);
	call(F, "use", [V]);
	check("used-when-hurt", F.left() == 2 && V.curState().getHitPoints() > 60, "left " + F.left() + " hp " + V.curState().getHitPoints());
	// refills on a kill by its holder (a death names its killer as the tool), not on another's
	var foe = npc("a fixa18 rat"), other = npc("a fixa18 bystander");
	F.executeMsg(flask, CMClass.getMsg(foe, null, other, CMMsg.MSG_DEATH, null));
	check("not-others-kills", F.left() == 2, "left " + F.left() + " after another's kill");
	F.executeMsg(flask, CMClass.getMsg(foe, null, V, CMMsg.MSG_DEATH, null));
	F.executeMsg(flask, CMClass.getMsg(npc("a fixa18 rat"), null, V, CMMsg.MSG_DEATH, null));
	check("refill-kill", F.left() == 3, "left " + F.left() + " after two kills (capped at 3)");
	// automatic use below 30% health, once a tick, not in a tick it was used by hand
	V.curState().setHitPoints(20);
	call(F, "use", [V]);
	var afterHand = F.left();
	V.curState().setHitPoints(20);
	F.tick(F, TPS);
	check("auto-not-same-tick", afterHand == 2 && F.left() == 2, "left " + F.left() + " (by hand " + afterHand + ")");
	V.curState().setHitPoints(20);
	F.tick(F, TPS);
	check("auto-used", F.left() == 1 && V.curState().getHitPoints() > 20, "left " + F.left() + " hp " + V.curState().getHitPoints());
	V.curState().setHitPoints(90);
	F.tick(F, TPS);
	check("auto-not-above", F.left() == 1, "left " + F.left() + " at 90%");
	// nothing to cure: refused
	var charm = CMClass.getItem("GenItem"); charm.setName("a fixa18 bone charm"); V.addItem(charm);
	var C = CMClass.getAbility("Prop_Invokable");
	C.setMiscText("VERB=RUB SPELLS=Prayer_CureDisease TARGET=SELF CHARGES=2 REFUSE=NOAILMENT REFUSEMSG=\"The charm stays cold.\"");
	charm.addNonUninvokableEffect(C);
	out(); call(C, "use", [V]);
	said = out();
	check("refuse-noailment", C.left() == 2 && has(said, "The charm stays cold."), "left " + C.left() + " '" + said + "'");
	var ail = CMClass.getAbility("Disease_Cold"); if (ail != null) ail.invoke(V, V, true, 0);
	var ailing = V.fetchEffect("Disease_Cold") != null;
	call(C, "use", [V]);
	check("ailing-used", !ailing || C.left() == 1, "ailing " + ailing + " left " + C.left());
	check("identify", has(F.accountForYourself(), "at full health") && has(F.accountForYourself(), "each kill by its holder refills 1")
		&& has(F.accountForYourself(), "below 30% health"), s(F.accountForYourself()));
} finally {
	VS.setMob(null); V.setSession(null);
	made.forEach(function (m) { if (!m.amDestroyed()) m.destroy(); });
	R.destroy();
}
done();
