// JS8: world-wide cm.on events: death, beforeDeath, levelUp, questWin, questEnd, enterRoom,
// beforeEnterRoom, enterArea, itemGained, itemLost, factionChange, turn; cm.off(event, fn).
FIXFILE = "js8-world";
var JE = CM.core.JSEvents;
var SE = CM.core.ScriptEvents;
var R = M.location();
function s(x) { return "" + x; }
var seen = [];
function saw(what) { seen.push(what); }
function last() { return seen.length ? seen[seen.length - 1] : ""; }
JE.clear();

// moving: enterRoom, beforeEnterRoom, enterArea
var dir = -1;
for (var d = 0; d < CM.core.Directions.NUM_DIRECTIONS(); d++) {
	var E = R.getExitInDir(d);
	if ((R.getRoomInDir(d) != null) && (E != null) && E.isOpen()) { dir = d; break; }
}
var walker = newMob("GenMob", R);
walker.setName("a fixjs8 walker");
cm.on("enterRoom", function(mob, room, from) { if (s(mob.Name()).indexOf("fixjs8") >= 0) saw("enter " + mob.Name() + " " + room.roomID() + " from " + from.roomID()); });
cm.on("beforeEnterRoom", function(mob, room, from) { return s(mob.Name()) != "a fixjs8 blocked"; });
if (dir >= 0) {
	var to = R.getRoomInDir(dir);
	check("enterRoom", CMLib.tracking().walk(walker, dir, false, true) && walker.location() == to
		&& last() == "enter a fixjs8 walker " + to.roomID() + " from " + R.roomID(), "walk " + walker.location() + ", saw " + last());
	var blocked = newMob("GenMob", R);
	blocked.setName("a fixjs8 blocked");
	check("beforeEnterRoom", !CMLib.tracking().walk(blocked, dir, false, true) && blocked.location() == R, "blocked is at " + blocked.location());
	blocked.destroy();
} else
	fail("enterRoom", "no open exit from " + R.roomID());
// an area of its own, so that the move crosses a border
var A2 = CMClass.getAreaType("StdArea");
A2.setName("Fixjs8 Elsewhere");
var R2 = CMClass.getLocale("StdRoom");
R2.setRoomID("");
R2.setArea(A2);
cm.on("enterArea", function(mob, area, from) { saw("area " + area.Name() + " from " + (from == null ? "nowhere" : from.Name())); });
SE.moved(walker, R, R2);
check("enterArea", last() == "area Fixjs8 Elsewhere from " + R.getArea().Name(), "saw " + last());

// items
check("no-item-events", !JE.itemEvents, "nothing listens for items yet");
cm.on("itemGained", function(mob, item) { saw("got " + item.Name()); });
cm.on("itemLost", function(mob, item) { saw("lost " + item.Name()); });
check("item-events", JE.itemEvents, "listening for items");
var coin = CMClass.getItem("GenItem");
coin.setName("a fixjs8 trinket");
walker.addItem(coin);
check("itemGained", last() == "got a fixjs8 trinket", "saw " + last());
walker.addItem(coin);
check("itemGained-once", seen.filter(function(x) { return x == "got a fixjs8 trinket"; }).length == 1, "twice: " + seen);
walker.delItem(coin);
check("itemLost", last() == "lost a fixjs8 trinket", "saw " + last());
coin.destroy();

// factions
var F = CMLib.factions().getFaction("inclination.ini");
if (F != null) {
	cm.on("factionChange", function(mob, faction, change) { saw("faction " + faction.factionID() + " " + change); });
	walker.addFaction(s(F.factionID()), 0);
	walker.adjustFaction(s(F.factionID()), 25);
	check("factionChange", last() == "faction " + F.factionID() + " 25", "saw " + last());
}

// quests
var Q = CMClass.getCommon("DefaultQuest");
Q.setName("fixjs8 quest");
cm.on("questWin", function(mob, quest) { saw("won " + quest.name() + " by " + mob.Name()); });
cm.on("questEnd", function(quest, mob, why) { saw("ended " + quest.name() + " " + why); });
SE.quest(Q, "WIN", M, "");
check("questWin", last() == "won fixjs8 quest by " + M.Name(), "saw " + last());
SE.quest(Q, "END", null, "TIME");
check("questEnd", last() == "ended fixjs8 quest TIME", "saw " + last());

// turns
var onTurn = function(leader, n) { saw("turn " + n); };
cm.on("turn", onTurn);
SE.turn(M);
check("turn", s(last()).indexOf("turn ") == 0 && s(last()) == "turn " + SE.turns(), "saw " + last());
check("off-fn", cm.off("turn", onTurn) == 1 && cm.listening("turn").isEmpty(), "listening " + cm.listening("turn"));

// death and beforeDeath
var survivor = newMob("GenMob", R);
survivor.setName("a fixjs8 survivor");
var victim = newMob("GenMob", R);
victim.setName("a fixjs8 victim");
cm.on("beforeDeath", function(dead, killer) { return s(dead.Name()) != "a fixjs8 survivor"; });
cm.on("death", function(dead, killer) { saw("died " + dead.Name() + " by " + (killer == null ? "nobody" : killer.Name())); });
survivor.curState().setHitPoints(0);
CMLib.combat().postDeath(walker, survivor, null);
check("beforeDeath", !survivor.amDead() && survivor.curState().getHitPoints() >= 1 && s(last()).indexOf("died a fixjs8 survivor") < 0, "survivor dead " + survivor.amDead());
CMLib.combat().postDeath(walker, victim, null);
check("death", s(seen.join("|")).indexOf("died a fixjs8 victim by a fixjs8 walker") >= 0, "saw " + seen.join("|"));

// level
var hero = newMob("GenMob", R);
hero.setName("a fixjs8 hero");
hero.baseCharStats().setCurrentClass(CMClass.getCharClass("Fighter"));
hero.recoverCharStats();
cm.on("levelUp", function(mob, level) { saw("level " + mob.Name() + " " + level); });
var lv = hero.basePhyStats().level();
CMLib.leveler().level(hero);
check("levelUp", last() == "level a fixjs8 hero " + (lv + 1), "level " + lv + " > " + hero.basePhyStats().level() + ", saw " + last());

JE.clear();
check("cleared", !JE.itemEvents && cm.listening(null).isEmpty(), "after clear");
walker.destroy(); survivor.destroy(); hero.destroy();
if (!victim.amDestroyed()) victim.destroy();
done();
