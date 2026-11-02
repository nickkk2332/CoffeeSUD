// C7: Prop_Checkpoint NAME= ON=ENTER|REST|COMMAND:word HEAL= RESPAWNAREA= SAVE=; the respawn point kept on the character;
// DEATH=CHECKPOINT; MPSETRESPAWN and RESPAWNROOM().
FIXFILE = "c7-checkpoint";
var Dir = CM.core.Directions;
var PS = CM.Common.interfaces.PhyStats;
var CP = CM.core.Checkpoints;
function s(x) { return "" + x; }
function has(t, p) { return s(t).replace(/\s+/g, " ").indexOf(p) >= 0; }
var X = CMClass.getAreaType("StdArea");
X.setName("Fixc7Crypt");
X.basePhyStats().setDisposition(X.basePhyStats().disposition() | PS.IS_UNSAVABLE);
X.recoverPhyStats();
CMLib.map().addArea(X);
function room(i, name) { var r = CMClass.getLocale("StdRoom"); r.setRoomID("Fixc7Crypt#" + i); r.setDisplayText(name); r.setArea(X); return r; }
var SHRINE = room(0, "Fixc7 old shrine"), CRYPT = room(1, "Fixc7 crypt"), TOWN = room(2, "Fixc7 town"), CAMP = room(3, "Fixc7 camp");
SHRINE.rawDoors()[Dir.EAST] = CRYPT; SHRINE.setRawExit(Dir.EAST, CMClass.getExit("Open"));
CRYPT.rawDoors()[Dir.WEST] = SHRINE; CRYPT.setRawExit(Dir.WEST, CMClass.getExit("Open"));
var V = CMClass.getMOB("GenMob");
V.setName("Fixc7pip");
V.baseState().setHitPoints(400); V.recoverMaxState(); V.resetToMaxState();
V.bringToLife(SHRINE, true);
V.setStartRoom(TOWN);
V.setPlayerStats(CMClass.getCommon("DefaultPlayerStats"));
var VS = CMClass.getCommon("FakeSession"); VS.initialize(null, "MEMORY"); V.setSession(VS); VS.setMob(V);
var mark = 0;
function out() { var all = s(field(VS, "bout").toString()); var o = all.substring(mark); mark = all.length; return o.replace(/\x1b\[[0-9;]*m/g, "").replace(/\^./g, ""); }
var pal = CMClass.getMOB("GenMob"); pal.setName("Fixc7pal"); pal.bringToLife(SHRINE, true);
pal["setFollowing(com.planet_ink.coffee_mud.MOBS.interfaces.MOB)"](V);
// a skeleton of the crypt, dead
var sk = CMClass.getMOB("GenMob"); sk.setName("Fixc7skeleton"); sk.basePhyStats().setRejuv(500); sk.recoverPhyStats(); sk.bringToLife(CRYPT, true); sk.setStartRoom(CRYPT);
CMLib.combat().postDeath(null, sk, null);
var skDead = sk.amDead();
// the shrine: PRAY
var P = CMClass.getAbility("Prop_Checkpoint");
P.setMiscText("NAME=\"Old Shrine\" ON=COMMAND:pray HEAL=100% RESPAWNAREA=true");
SHRINE.addNonUninvokableEffect(P);
V.curState().setHitPoints(50);
out();
CMLib.commands().handleUnknownCommand(V, new java.util.ArrayList(java.util.Arrays.asList(["pray"])));
var said = out();
check("pray", s(CP.idOf(V)).toUpperCase() == "FIXC7CRYPT#0" && CP.roomOf(V) == SHRINE && s(CP.idOf(pal)).toUpperCase() == "FIXC7CRYPT#0", "pip '" + CP.idOf(V) + "' pal '" + CP.idOf(pal) + "'");
check("heal", V.curState().getHitPoints() == V.maxState().getHitPoints() && has(said, "Old Shrine is where you will wake"), "hp " + V.curState().getHitPoints() + " " + said);
check("respawnarea", skDead && (!sk.amDead()) && sk.location() == CRYPT, "dead " + skDead + " now " + sk.amDead());
check("apart", V.getStartRoom() == TOWN, "the recall room moved");
// DEATH=CHECKPOINT wakes them there, else START
var page = CMProps.instance();
var oldDeath = page.getProperty("DEATH");
page.setProperty("DEATH", "CHECKPOINT");
CMLib.login().initDeathRooms(page);
var there = CMLib.login().getDefaultDeathRoom(V);
var palRoom = CMLib.login().getDefaultDeathRoom(pal);
CP.clear(V);
var fallback = CMLib.login().getDefaultDeathRoom(V);
page.setProperty("DEATH", oldDeath == null ? "START" : oldDeath);
CMLib.login().initDeathRooms(page);
check("death", there == SHRINE && fallback == TOWN, "there " + there + " fallback " + fallback);
// ON=ENTER
var PE = CMClass.getAbility("Prop_Checkpoint"); PE.setMiscText("NAME=\"the crypt door\""); CRYPT.addNonUninvokableEffect(PE);
CMLib.tracking().walk(V, Dir.EAST, false, false);
check("enter", CP.roomOf(V) == CRYPT, "entering did not set it: " + CP.idOf(V));
// ON=REST
var PR = CMClass.getAbility("Prop_Checkpoint"); PR.setMiscText("ON=REST"); CAMP.addNonUninvokableEffect(PR);
CAMP.bringMobHere(V, false);
CAMP.send(V, CMClass.getMsg(V, null, null, CMMsg.MSG_SIT, "<S-NAME> sit(s) down."));
check("rest", CP.roomOf(V) == CAMP, "resting did not set it: " + CP.idOf(V));
// scripts
var npc = CMClass.getMOB("GenMob"); npc.setName("Fixc7priest"); npc.bringToLife(TOWN, true);
var S = scriptOn(npc, "FUNCTION_PROG SET\nmpsetrespawn $n Fixc7Crypt#2\nmpsetvar FIXTEST FIXC7R $%RESPAWNROOM($n)%\n~\nFUNCTION_PROG CLR\nmpsetrespawn $n clear\nmpsetvar FIXTEST FIXC7C [$%RESPAWNROOM($n)%]\n~\n");
clearVar("FIXC7R"); clearVar("FIXC7C");
S.callFunction(npc, "SET", "", V);
S.callFunction(npc, "CLR", "", pal);
check("script", s(gvar("FIXC7R")).toUpperCase() == "FIXC7CRYPT#2" && gvar("FIXC7C") == "[]", "set '" + gvar("FIXC7R") + "' cleared '" + gvar("FIXC7C") + "'");
check("help", has(CMLib.help().getHelpText("PROP_CHECKPOINT", M, true), "RESPAWNAREA") && has(CMLib.help().getHelpText("BEHAVIOR_SCRIPTABLE", M, true), "MPSETRESPAWN"), "no help");

[V, pal, sk, npc].forEach(function (m) { m.makePeace(true); if (m == V) m.setPlayerStats(null); m.destroy(); });
CMLib.map().obliterateMapArea(X);
done();
