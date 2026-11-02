// C16: Prop_DeathRules on a room or area: PLAYERDEATH=, MOBDEATH=, ITEMLOOTPOLICY= before the game's; TO= a defeat
// that is not death, GEAR=CONTAINER:room/container, HEAL=, MSG=, MASK=; the followers stay with the party.
FIXFILE = "c16-deathrules";
var Dir = CM.core.Directions;
var PS = CM.Common.interfaces.PhyStats;
var DR = CM.core.DeathRules;
function s(x) { return "" + x; }
function has(t, p) { return s(t).replace(/\s+/g, " ").indexOf(p) >= 0; }
var X = CMClass.getAreaType("StdArea");
X.setName("Fixc16Camp");
X.basePhyStats().setDisposition(X.basePhyStats().disposition() | PS.IS_UNSAVABLE);
X.recoverPhyStats();
CMLib.map().addArea(X);
function room(i, name) { var r = CMClass.getLocale("StdRoom"); r.setRoomID("Fixc16Camp#" + i); r.setDisplayText(name); r.setArea(X); return r; }
var CAMP = room(0, "Fixc16 bandit camp"), CAGE = room(1, "Fixc16 cage"), TENT = room(2, "Fixc16 chief's tent"), HALL = room(3, "Fixc16 sparring hall");
var box = CMClass.getItem("GenContainer"); box.setName("a fixc16 strongbox"); TENT.addItem(box);
var V = CMClass.getMOB("GenMob");
V.setName("Fixc16pip");
V.baseState().setHitPoints(400); V.recoverMaxState(); V.resetToMaxState();
V.bringToLife(CAMP, true);
V.setPlayerStats(CMClass.getCommon("DefaultPlayerStats"));
var sword = CMClass.getWeapon("Sword"); V.addItem(sword); sword.wearAt(CM.Items.interfaces.Wearable.WORN_WIELD);
var charm = CMClass.getItem("GenItem"); charm.setName("a fixc16 charm"); V.addItem(charm);
V.recoverPhyStats();
var pal = CMClass.getMOB("GenMob"); pal.setName("Fixc16pal"); pal.bringToLife(CAMP, true);
pal["setFollowing(com.planet_ink.coffee_mud.MOBS.interfaces.MOB)"](V);
var chief = CMClass.getMOB("GenMob"); chief.setName("Fixc16chief"); chief.bringToLife(CAMP, true);
var P = CMClass.getAbility("Prop_DeathRules");
P.setMiscText("TO=Fixc16Camp#1 GEAR=\"CONTAINER:Fixc16Camp#2/a fixc16 strongbox\" HEAL=25 MSG=\"<S-NAME> fall(s), beaten, and <S-IS-ARE> dragged off.\"");
CAMP.addNonUninvokableEffect(P);
chief.setVictim(V); V.setVictim(chief);
CMLib.combat().postDeath(chief, V, null);
check("defeated", (!V.amDead()) && V.location() == CAGE && V.curState().getHitPoints() == 100, "dead " + V.amDead() + " in " + V.location() + " hp " + V.curState().getHitPoints());
check("party", pal.location() == CAGE && pal.amFollowing() == V, "the follower stayed behind");
check("gear", sword.container() == box && charm.container() == box && CMLib.map().roomLocation(sword) == TENT && V.fetchWieldedItem() == null, "sword in " + sword.container());
check("peace", !chief.isInCombat() && !V.isInCombat(), "still fighting");
// the rules' lists, before the game's
var PH = CMClass.getAbility("Prop_DeathRules");
PH.setMiscText("MOBDEATH=\"OUT 3\" PLAYERDEATH=\"OUT 2,RETAIN\" ITEMLOOTPOLICY=\"RUIN NOLOOT\"");
HALL.addNonUninvokableEffect(PH);
var duelist = CMClass.getMOB("GenMob"); duelist.setName("Fixc16duelist"); duelist.bringToLife(HALL, true);
var stray = CMClass.getMOB("GenMob"); stray.setName("Fixc16stray"); stray.bringToLife(CAMP, true);
check("mobdeath", s(DR.mobDeath(duelist)) == "OUT 3" && s(DR.mobDeath(stray)) == s(CMProps.getVar(CMProps.Str.MOBDEATH)) && CMLib.combat().isKnockedOutUponDeath(duelist, null),
	"hall " + DR.mobDeath(duelist) + " camp " + DR.mobDeath(stray));
check("playerdeath", s(DR.playerDeath(duelist)) == "OUT 2,RETAIN" && s(DR.itemLootPolicy(duelist)) == "RUIN NOLOOT", "playerdeath " + DR.playerDeath(duelist));
// on the area, and MASK
var PA = CMClass.getAbility("Prop_DeathRules"); PA.setMiscText("MOBDEATH=\"OUT 1\" MASK=\"-NAMES +Fixc16stray\""); X.addNonUninvokableEffect(PA);
check("area-mask", s(DR.mobDeath(stray)) == "OUT 1" && s(DR.mobDeath(chief)) == s(CMProps.getVar(CMProps.Str.MOBDEATH)) && s(DR.mobDeath(duelist)) == "OUT 3",
	"stray " + DR.mobDeath(stray) + " chief " + DR.mobDeath(chief));
check("help", has(CMLib.help().getHelpText("PROP_DEATHRULES", M, true), "GEAR=CONTAINER"), "no help");

[V, pal, chief, duelist, stray].forEach(function (m) { m.makePeace(true); if (m == V) m.setPlayerStats(null); m.destroy(); });
CMLib.map().obliterateMapArea(X);
done();
