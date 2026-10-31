// N18: NPC behaviour bugs.
FIXFILE = "n18-behaviours";
var R = M.location();

// MOBHunter knows when it is hunting, and never picks itself as prey
var away = otherRoom();
var hunter = newMob("GenMob", away);
hunter.setName("a bounty hunter");
var H = CMClass.getBehavior("MOBHunter");
H.setParms("radius=0");
hunter.addBehavior(H);
check("hunter-not-hunting", call(H, "isHunting", [hunter]) == false, "hunting with no hunt on");
var hunt = CMClass.getAbility("Thief_Assassinate");
hunter.addEffect(hunt);
check("hunter-hunting", call(H, "isHunting", [hunter]) == true, "does not see its own hunt");
hunter.delEffect(hunt);
var prey = call(H, "findPrey", [hunter]);
check("hunter-not-self", prey == null || prey != hunter, "picked itself as prey");
hunter.destroy();

// Aggressive's BRIBE= keeps its pay window apart from the peace it buys
var A = CMClass.getBehavior("Aggressive");
A.setParms("BRIBE=10,30,50");
var bribe = field(A, "bribe");
check("bribe-peace", bribe.third.intValue() == 30, "peace ticks " + bribe.third);
check("bribe-window", bribe.fourth.intValue() == 50, "pay window " + bribe.fourth + " ticks");

// Loyalty searches every two minutes, not every tick
var L = CMClass.getAbility("Loyalty");
var interval = field(L, "INTERVAL");
check("loyalty-interval", interval > 1, "interval " + interval + " ticks");

// Scavenger leaves what it cannot pick up, even if it matches its mask
var scav = newMob("GenMob", away);
scav.setName("a rat");
var S = CMClass.getBehavior("Scavenger");
scav.addBehavior(S);
var anvil = CMClass.getItem("GenItem");
anvil.setName("a bolted anvil");
anvil.basePhyStats().setSensesMask(anvil.basePhyStats().sensesMask() | CM.Common.interfaces.PhyStats.SENSE_ITEMNOTGET);
anvil.recoverPhyStats();
away.addItem(anvil);
check("anvil-not-gettable", !CMLib.flags().isGettable(anvil), "the anvil can be picked up");
for (var i = 0; i < 60; i++) S.tick(scav, Tickable.TICKID_MOB);
check("scavenger-leaves-ungettable", anvil.owner() == away, "the rat took the anvil");
anvil.destroy();
scav.destroy();
done();
