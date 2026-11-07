// Phase 2 stage 1, defects found by the audit: a wind-up's state (ticks left, damage taken, who braced) and a
// Prop_Invokable's cooldown and recharge countdown kept in their saved text; a bound item can be neither worn by
// another nor given away; formation rows kept in the save and through PARTY LEAD.
FIXFILE = "p2-fixes";
function s(x) { return "" + x; }
var IDS = CM.core.InstanceIds, Party = CM.core.Party;
var R = M.location(), made = [];
function npc(name) { var m = newMob("GenMob", R); m.setName(name); made.push(m); return m; }
var acct = CMClass.getCommon("DefaultPlayerAccount"); acct.setAccountName("Fixp2profile");
function character(name) {
	var m = npc(name);
	m.setPlayerStats(CMClass.getCommon("DefaultPlayerStats")); m.playerStats().setAccount(acct);
	var S = CMClass.getCommon("PartySession"); S.initialize(null, "0"); S.setMob(m); m.setSession(S); CMLib.sessions().add(S);
	CMLib.players().addPlayer(m); acct.addNewPlayer(m); return m;
}
try {
	// a wind-up under way: its state is in its text, which the save keeps, and comes back from it
	var dragon = npc("a fixp2 dragon"), foe = npc("a fixp2 knight");
	var T = CMClass.getAbility("Prop_Telegraph");
	T.setMiscText("NAME=SWEEP TICKS=3 AVOID=COMMAND:brace");
	dragon.addEffect(T);
	var brace = CMClass.getMsg(foe, null, null, CMMsg.TYP_HUH, null, CMMsg.TYP_HUH, "brace", CMMsg.TYP_HUH, null);
	check("braced", !T.okMessage(dragon, brace), "brace was not taken");
	tickMob(dragon);
	var hit = CMClass.getMsg(foe, dragon, null, CMMsg.MSG_DAMAGE, null); hit.setValue(5);
	T.executeMsg(dragon, hit);
	var text = s(T.text());
	check("telegraph-text", text.indexOf("LEFT=2") >= 0 && text.indexOf("TAKEN=5") >= 0 && text.indexOf(s(IDS.reference(foe))) >= 0, text);
	var T2 = CMClass.getAbility("Prop_Telegraph"); T2.setMiscText(text);
	check("telegraph-restored", field(T2, "ticksLeft") == 2 && field(T2, "damageTaken") == 5 && field(T2, "avoided").contains(IDS.reference(foe))
		&& s(T2.text()) == s(T2.text()) && s(field(T2, "tgName")) == "SWEEP", "ticks " + field(T2, "ticksLeft") + " taken " + field(T2, "damageTaken") + " avoided " + field(T2, "avoided"));
	check("telegraph-saved", s(CM.core.save.WorldState.capture(M)).indexOf("LEFT=2") >= 0, "the save does not carry the wind-up");
	T.telegraphDone();
	check("telegraph-done-text", s(T.text()).indexOf("LEFT=") < 0, s(T.text()));

	// a charged item's cooldown and recharge countdown are in its text too
	var V = CMClass.getAbility("Prop_Invokable");
	V.setMiscText("VERB=QUAFF SPELLS=Spell_Light CHARGES=3 COOLDOWN=5 RECHARGE=TICKS=20 LEFT=1 COOLLEFT=4 RECHARGEIN=7");
	check("invokable-restored", field(V, "left") == 1 && field(V, "coolLeft") == 4 && field(V, "rechargeDown") == 7, "left " + field(V, "left") + " cool " + field(V, "coolLeft") + " recharge " + field(V, "rechargeDown"));
	var vt = s(V.text());
	check("invokable-text", vt.indexOf("LEFT=1") >= 0 && vt.indexOf("COOLLEFT=4") >= 0 && vt.indexOf("RECHARGEIN=7") >= 0 && vt.split("COOLLEFT").length == 2, vt);
	var V2 = CMClass.getAbility("Prop_Invokable"); V2.setMiscText("VERB=QUAFF SPELLS=Spell_Light CHARGES=3 COOLDOWN=5");
	check("invokable-fresh", s(V2.text()).indexOf("COOLLEFT") < 0 && s(V2.text()).indexOf("RECHARGEIN") < 0, s(V2.text()));

	// a bound item: another may neither wear it nor be given it; the one it is bound to may
	var owner = npc("Fixp2 Owner"), other = npc("Fixp2 Other");
	var ring = CMClass.getItem("GenItem"); ring.setName("a fixp2 bound ring");
	var B = CMClass.getAbility("Prop_ItemBinder"); B.setMiscText("TO=CHARACTER ON=PICKUP BOUND=\"Fixp2 Owner\""); ring.addNonUninvokableEffect(B);
	owner.addItem(ring);
	check("bound-wear-other", !B.okMessage(other, CMClass.getMsg(other, ring, null, CMMsg.MSG_WEAR, null)), "another could wear it");
	check("bound-wear-owner", B.okMessage(owner, CMClass.getMsg(owner, ring, null, CMMsg.MSG_WEAR, null)), "the owner could not wear it");
	check("bound-give", !B.okMessage(owner, CMClass.getMsg(owner, other, ring, CMMsg.MSG_GIVE, null)), "it could be given away");
	var G = CMClass.getAbility("Prop_ItemBinder"); G.setMiscText("TO=GROUP ON=PICKUP BOUND=\"Fixp2 Owner Fixp2 Other\"");
	check("bound-give-group", G.okMessage(owner, CMClass.getMsg(owner, other, ring, CMMsg.MSG_GIVE, null)), "a group member could not be given it");

	// formation rows: in the save, and kept when the lead is handed over
	var a = character("Fixp2rowa"), b = character("Fixp2rowb"), c = character("Fixp2rowc");
	b.setFollowing(a); c.setFollowing(a);
	a.addFollower(a, 0); a.addFollower(b, 1); a.addFollower(c, 2);
	var saved = s(CM.core.save.WorldState.capture(a));
	check("rows-saved", saved.indexOf("<NAME>Fixp2rowb</NAME>") >= 0 && /<NAME>Fixp2rowc<\/NAME>[\s\S]*?<ROW>2<\/ROW>/.test(saved), saved.substring(0, 600));
	Party.handOver(a, b);
	check("rows-handover", b.amFollowing() == null && a.amFollowing() == b && c.amFollowing() == b
		&& b.fetchFollowerOrder(b) == 0 && b.fetchFollowerOrder(a) == 1 && b.fetchFollowerOrder(c) == 2, // the two swap rows; the rest keep theirs
		"b " + b.fetchFollowerOrder(b) + " a " + b.fetchFollowerOrder(a) + " c " + b.fetchFollowerOrder(c));
} finally {
	made.forEach(function (m) {
		if (m.session() != null) CMLib.sessions().remove(m.session());
		if (m.playerStats() != null) { CMLib.players().delPlayer(m); m.setPlayerStats(null); }
		if (!m.amDestroyed()) m.destroy();
	});
}
done();
