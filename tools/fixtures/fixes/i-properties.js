// I1, I18, I19: property and command fixes.
FIXFILE = "i-properties";
var R = M.location();
var TA = CM.Abilities.interfaces.TriggeredAffect;

// I1: TRIGGER=HITTING_WITH and DROP_PUTIN have bits of their own
check("trigger-bits", TA.TRIGGER_DROP_PUTIN != TA.TRIGGER_PUT && TA.TRIGGER_HITTING_WITH != TA.TRIGGER_MOUNT, "DROP_PUTIN=" + TA.TRIGGER_DROP_PUTIN + " HITTING_WITH=" + TA.TRIGGER_HITTING_WITH);
var PS = CMClass.getAbility("Prop_PropSetter");
PS.setMiscText("TRIGGER=HITTING_WITH");
check("propsetter-hitting-with", field(PS, "trigger") == TA.TRIGGER_HITTING_WITH, "trigger=" + field(PS, "trigger"));

// I1: a quiver limited to 2 arrows counts only the arrows in it
var quiver = CMClass.getItem("GenContainer");
quiver.setName("a quiver"); quiver.setCapacity(1000);
R.addItem(quiver);
var LC = CMClass.getAbility("Prop_LimitedContents");
LC.setMiscText("2 -NAMES +arrow");
quiver.addNonUninvokableEffect(LC);
for (var i = 0; i < 3; i++) { var rock = CMClass.getItem("GenItem"); rock.setName("rock"); R.addItem(rock); rock.setContainer(quiver); }
var arrow = CMClass.getItem("GenItem"); arrow.setName("arrow"); M.addItem(arrow);
var put = CMClass.getMsg(M, quiver, arrow, CMMsg.MSG_PUT, null);
check("limited-counts-matching", LC.okMessage(M, put), "refused the first arrow (it counted the rocks)");
for (var i = 0; i < 2; i++) { var a2 = CMClass.getItem("GenItem"); a2.setName("arrow"); R.addItem(a2); a2.setContainer(quiver); }
check("limited-limits", !LC.okMessage(M, put), "let a third arrow in");
// ... and a limit on a room limits what is dropped in it
var RLC = CMClass.getAbility("Prop_LimitedContents");
RLC.setMiscText("0 -NAMES +arrow");
R.addNonUninvokableEffect(RLC);
var drop = CMClass.getMsg(M, arrow, null, CMMsg.MSG_DROP, null);
check("limited-room-drop", !RLC.okMessage(M, drop), "dropped an arrow where none are allowed");
R.delEffect(RLC);
arrow.destroy(); quiver.destroy();

// I1: ON= abbreviations work
var IB = CMClass.getAbility("Prop_ItemBinder");
IB.setMiscText("ON=EQ");
check("binder-on-abbrev", ("" + field(IB, "on")) == "EQUIP", "ON=EQ gave " + field(IB, "on"));

// I1: ModFaction *150% is one and a half times
var MF = CMClass.getAbility("Prop_ModFaction");
MF.setMiscText("*150%");
var times = CMath.parseMathExpression(field(MF, "operation"), [100.0], 0.0);
check("modfaction-percent-times", Math.round(times) == 150, "*150% of 100 gave " + times);
MF.setMiscText("/50%");
var div = CMath.parseMathExpression(field(MF, "operation"), [100.0], 0.0);
check("modfaction-percent-div", Math.round(div) == 200, "/50% of 100 gave " + div);

// I1: MoveRestrictor's ISEARCH looks for items in the room
var away = otherRoom();
var symbol = CMClass.getItem("GenItem"); symbol.setName("a holy symbol"); away.addItem(symbol);
var MR = CMClass.getAbility("Prop_MoveRestrictor");
MR.setMiscText("ISEARCH=symbol CHANCE=100");
var walker = newMob("GenMob", R);
walker.addNonUninvokableEffect(MR);
var enter = CMClass.getMsg(walker, away, null, CMMsg.MSG_ENTER, null);
check("moverestrictor-isearch", !MR.okMessage(walker, enter), "entered a room with the item");
symbol.destroy(); walker.destroy();

// I18: READ <direction> reads the exit there
var dirs = CM.core.Directions;
var exitDir = -1;
for (var d = 0; d < 20; d++) { try { if (R.getExitInDir(d) != null) { exitDir = d; break; } } catch (e) { break; } }
if (exitDir >= 0) {
	var X = R.getExitInDir(exitDir);
	var reader = scriptOn(X, "EXECMSG_PROG READ all\nmpsetvar FIXTEST FIXREAD exit\n~\n");
	clearVar("FIXREAD");
	var cmds = new java.util.ArrayList(); cmds.add("READ"); cmds.add(CMLib.directions().getDirectionName(exitDir));
	CMClass.getCommand("Read").execute(M, cmds, 0);
	runQueued(reader);
	check("read-direction", gvar("FIXREAD") == "exit", "READ " + CMLib.directions().getDirectionName(exitDir) + " did not read the exit");
	X.delScript(reader);
} else fail("read-direction", "no exit from the start room");

// I19: Prop_CloseCommand closes an open door on its phrase, even one that starts with PUSH
var doorDir = -1;
for (var d = 0; d < 20 && doorDir < 0; d++) { try { if (R.getExitInDir(d) != null) doorDir = d; } catch (e) { break; } }
var oldExit = R.getRawExit(doorDir);
var door = CMClass.getExit("GenExit");
door.setDoorsNLocks(true, true, true, false, false, false);
R.setRawExit(doorDir, door);
var CC = CMClass.getAbility("Prop_CloseCommand");
CC.setMiscText("push stone;noclose");
check("closecommand-noclose-not-phrase", field(CC, "commandPhrases").size() == 1, "phrases " + field(CC, "commandPhrases").size());
door.addNonUninvokableEffect(CC);
check("door-open", door.isOpen(), "the test door is not open");
var huh = CMClass.getMsg(M, null, null, CMMsg.MSG_COMMANDFAIL, null, CMMsg.NO_EFFECT, "push stone", CMMsg.NO_EFFECT, null);
var refused = !CC.okMessage(M, huh);
check("closecommand-phrase-taken", refused, "the phrase was not taken");
check("closecommand-closes", !door.isOpen(), "the door is still open");
// NOCLOSE still stops an ordinary CLOSE
door.setDoorsNLocks(true, true, true, false, false, false);
var close = CMClass.getMsg(M, door, null, CMMsg.MSG_CLOSE, null);
check("closecommand-noclose", !CC.okMessage(M, close), "NOCLOSE let an ordinary close through");
R.setRawExit(doorDir, oldExit);
door.destroy();
done();
