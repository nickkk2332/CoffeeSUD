// Phase 2 stage 2, the cookbook's recipes 30-33 and 35 as written: a guarded shrine ember claimed once (30); a sword
// mastery counted only for sword kills, and an egg that grows by stages (31); a collection's entries and completion
// (32); sockets taking gems of their type only (33); a hidden challenge entry (35).
FIXFILE = "p2-recipes";
function s(x) { return "" + x; }
var Agent = CM.Common.interfaces.AccountStats.Agent, Event = CM.Libraries.interfaces.AchievementLibrary.Event;
var R = M.location(), made = [], achs = [];
function npc(name) { var m = newMob("GenMob", R); m.setName(name); made.push(m); return m; }
function hero(name) {
	var P = CMClass.getMOB("StdMOB"); P.setName(name); P.setPlayerStats(CMClass.getCommon("DefaultPlayerStats"));
	P.baseCharStats().setMyRace(CMClass.getRace("Human")); P.basePhyStats().setLevel(5); P.recoverCharStats(); P.recoverPhyStats();
	P.bringToLife(R, true); made.push(P); return P;
}
function ach(line) {
	var id = line.substring(0, line.indexOf("="));
	var err = CMLib.achievements().evaluateAchievement(Agent.PLAYER, line, true); achs.push(id);
	check("defined-" + id, CMLib.achievements().getAchievement(id) != null, "not defined: " + err);
}
try {
	// 30: the guardian stops anyone taking the ember; afterwards, CLAIM blesses once
	var guard = npc("a fixp2 shrine guardian");
	var G = CMClass.getBehavior("ObjectGuardian"); G.setParms("SENTINAL"); guard.addBehavior(G);
	var ember = CMClass.getItem("GenItem"); ember.setName("a shrine ember"); R.addItem(ember);
	var V = CMClass.getAbility("Prop_Invokable");
	V.setMiscText("VERB=CLAIM SPELLS=Prayer_Bless TARGET=SELF CHARGES=1 RECHARGE=DAILY REQUIRE=HELD EMPTYMSG=\"The ember is cold until tomorrow.\"");
	ember.addNonUninvokableEffect(V);
	var pilgrim = hero("Fixp2pilgrim");
	check("shrine-guarded", !R.okMessage(pilgrim, CMClass.getMsg(pilgrim, ember, null, CMMsg.MSG_GET, null)), "the ember could be taken past its guardian");
	guard.destroy();
	check("shrine-free", R.okMessage(pilgrim, CMClass.getMsg(pilgrim, ember, null, CMMsg.MSG_GET, null)), "the ember could not be taken");
	R.delItem(ember); pilgrim.addItem(ember); ember.setRawWornCode(CM.Items.interfaces.Wearable.WORN_HELD);
	call(V, "use", [pilgrim]);
	check("shrine-claimed", pilgrim.fetchEffect("Prayer_Bless") != null && V.left() == 0 && s(V.text()).indexOf("LEFT=0") >= 0, "bless " + pilgrim.fetchEffect("Prayer_Bless") + " left " + V.left());
	var B = pilgrim.fetchEffect("Prayer_Bless"); if (B != null) B.unInvoke();
	call(V, "use", [pilgrim]);
	check("shrine-once", pilgrim.fetchEffect("Prayer_Bless") == null && V.left() == 0, "claimed twice");

	// 31: kills with a sword count toward the mastery, others do not
	ach('ACH_FIXP2BLADE=EVENT="KILLS" DISPLAY="Win 2 fights with a sword" TITLE="" REWARDS="" NUM=2 ZAPPERMASK="-LEVEL +>=0" PLAYERMASK="-WEAPONCLASS +SWORD"');
	var swordsman = hero("Fixp2swordsman");
	CMLib.achievements().possiblyBumpAchievement(swordsman, Event.KILLS, 1, npc("a fixp2 rat"));
	var sword = CMClass.getWeapon("GenWeapon"); sword.setName("a fixp2 sword"); sword.setWeaponClassification(CM.Items.interfaces.Weapon.CLASS_SWORD);
	sword.setRawProperLocationBitmap(CM.Items.interfaces.Wearable.WORN_WIELD); swordsman.addItem(sword);
	R.send(swordsman, CMClass.getMsg(swordsman, sword, null, CMMsg.MSG_WIELD, null)); // wielded as WIELD does it
	check("wielded", swordsman.fetchWieldedItem() == sword, "the sword is not wielded: " + swordsman.fetchWieldedItem());
	CMLib.achievements().possiblyBumpAchievement(swordsman, Event.KILLS, 1, npc("a fixp2 rat"));
	check("mastery-counting", swordsman.findTattoo("ACH_FIXP2BLADE") == null, "an unarmed kill counted");
	CMLib.achievements().possiblyBumpAchievement(swordsman, Event.KILLS, 1, npc("a fixp2 rat"));
	check("mastery-earned", swordsman.findTattoo("ACH_FIXP2BLADE") != null, "two sword kills did not earn it");

	// 31: an egg that grows by stages with kills
	var egg = CMClass.getItem("GenItem"); egg.setName("a warm egg");
	var Gr = CMClass.getAbility("Prop_ItemGrowth");
	Gr.setMiscText("GAIN=KILL STAGES=\"2:NAME='a cracking egg';4:NAME='a hatchling drake'\"");
	egg.addNonUninvokableEffect(Gr);
	var keeper = hero("Fixp2keeper"); keeper.addItem(egg); egg.wearAt(CM.Items.interfaces.Wearable.WORN_HELD);
	// a death message comes from the one who dies, with the killer as its tool
	function killWith(k) { var v = npc("a fixp2 beetle"); var dm = CMClass.getMsg(v, null, k, CMMsg.MSG_DEATH, null); Gr.executeMsg(egg, dm); }
	killWith(keeper); killWith(keeper);
	var stage2 = s(egg.Name());
	killWith(keeper); killWith(keeper);
	check("egg-stages", stage2 == "a cracking egg" && s(egg.Name()) == "a hatchling drake", stage2 + " then " + s(egg.Name()));

	// 32: entries count once each, and the list completes the collection
	ach('ACH_FIXP2SHELL1=EVENT="GOTITEM" DISPLAY="Tide shell" TITLE="" REWARDS="" NUM=1 ITEMMASK="-NAMES \\"+a tide shell\\""');
	ach('ACH_FIXP2SHELL2=EVENT="GOTITEM" DISPLAY="Ember shell" TITLE="" REWARDS="" NUM=1 ITEMMASK="-NAMES \\"+an ember shell\\""');
	ach('ACH_FIXP2SHELLS=EVENT="ACHIEVER" DISPLAY="The shells" TITLE="" REWARDS="" ACHIEVEMENTLIST="ACH_FIXP2SHELL1,ACH_FIXP2SHELL2"');
	var collector = hero("Fixp2collector");
	function pick(name) { var I = CMClass.getItem("GenItem"); I.setName(name); collector.addItem(I); CMLib.achievements().possiblyBumpAchievement(collector, Event.GOTITEM, 1, I); }
	pick("a tide shell");
	check("collection-entry", collector.findTattoo("ACH_FIXP2SHELL1") != null && collector.findTattoo("ACH_FIXP2SHELLS") == null, "first entry");
	pick("an ember shell");
	CMLib.achievements().evaluatePlayerAchievements(collector); // the completion comes at the next check
	check("collection-complete", collector.findTattoo("ACH_FIXP2SHELL2") != null && collector.findTattoo("ACH_FIXP2SHELLS") != null, "completion");

	// 35: a hidden entry is not visible until its mask passes
	ach('ACH_FIXP2TRIAL7=EVENT="KILLS" DISPLAY="Trials: a hidden one" TITLE="" REWARDS="" NUM=1 ZAPPERMASK="-LEVEL +>=0" VISIBLEMASK="-TATTOOS +ACH_FIXP2SHELLS"');
	var A7 = CMLib.achievements().getAchievement("ACH_FIXP2TRIAL7");
	var hidden = A7.canBeSeenBy(swordsman), shown = A7.canBeSeenBy(collector);
	check("challenge-hidden", !hidden && shown, "hidden " + hidden + " shown " + shown);

	// 33: sockets take gems of their own type, up to their number
	var blade = CMClass.getWeapon("GenWeapon"); blade.setName("a fixp2 socketed blade");
	var Slot = CMClass.getAbility("Prop_ItemSlot"); Slot.setMiscText("NUM=1 TYPE=Gems REMOVABLE=TRUE"); blade.addNonUninvokableEffect(Slot);
	function gem(name, type) { var g = CMClass.getItem("GenItem"); g.setName(name); var F = CMClass.getAbility("Prop_ItemSlotFiller"); F.setMiscText("TYPE=" + type + " ADDS=\"Prop_HaveAdjuster(STR+1)\""); g.addNonUninvokableEffect(F); return g; }
	var jeweler = hero("Fixp2jeweler"); jeweler.addItem(blade);
	var rune = gem("a fixp2 rune", "Runes"), ruby = gem("a fixp2 ruby", "Gems"), opal = gem("a fixp2 opal", "Gems");
	jeweler.addItem(rune); jeweler.addItem(ruby); jeweler.addItem(opal);
	function put(g) { var m = CMClass.getMsg(jeweler, blade, g, CMMsg.MSG_PUT, null); if (!R.okMessage(jeweler, m)) return false; R.send(jeweler, m); return true; }
	check("socket-type", !put(rune), "a rune went into a gem socket");
	put(ruby);
	check("socket-filled", s(Slot.text()).indexOf("a fixp2 ruby") >= 0, s(Slot.text()).substring(0, 300));
	check("socket-full", !put(opal), "a second gem went into one socket");
} finally {
	made.forEach(function (m) { if (!m.amDestroyed()) m.destroy(); });
	achs.forEach(function (a) { CMLib.achievements().deleteAchievement(a); });
}
done();
