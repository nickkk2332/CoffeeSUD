// ST8: BOARD_PROG and DISEMBARK_PROG, SAIL_PROG and DOCK_PROG from an order
// to the vessel, VESSEL_ARRIVE_PROG and VESSEL_GREET_PROG.
FIXFILE = "st8-vessel";
var SEv = CM.core.ScriptEvents;
var R = M.location();
var R2 = otherRoom();
["FIXST8B", "FIXST8D", "FIXST8S", "FIXST8K", "FIXST8A", "FIXST8G", "FIXST8X"].forEach(clearVar);
var ship = CMClass.getItem("GenSailingShip");
ship.setName("the Fixtern");
ship.setDisplayText("The Fixtern is here.");
R.addItem(ship);
ship.dockHere(R);
CMLib.map().registerWorldObjectLoaded(R.getArea(), R, ship);
ship.rename("the Fixtern");
var deck = ship.getArea().getProperMap().nextElement();
var SS = scriptOn(ship, "SAIL_PROG ANCHOR\nmpsetvar FIXTEST FIXST8S $n:$0\n~\nVESSEL_ARRIVE_PROG ALL\nmpsetvar FIXTEST FIXST8A $G\n~\n");
var SD = scriptOn(deck, "BOARD_PROG\nmpsetvar FIXTEST FIXST8B $n:$G\n~\nDISEMBARK_PROG\nmpsetvar FIXTEST FIXST8D $n\n~\nDOCK_PROG\nmpsetvar FIXTEST FIXST8K $G\n~\n");
var SG = scriptOn(R2, "VESSEL_GREET_PROG fixtern\nmpsetvar FIXTEST FIXST8G $G\n~\nVESSEL_GREET_PROG nosuchfixship\nmpsetvar FIXTEST FIXST8X $G\n~\n");
var B = newMob("GenMob", R);
B.setName("Fixsteightsailor");
function runAll() { runQueued(SS); runQueued(SD); runQueued(SG); }
// aboard and off, as a move across the vessel's border
deck.bringMobHere(B, false);
SEv.moved(B, R, deck);
runAll();
check("board", gvar("FIXST8B") == "Fixsteightsailor:the Fixtern", "FIXST8B='" + gvar("FIXST8B") + "'");
// an order from the deck: LOWER ANCHOR
ship.setAnchorDown(false);
var order = CMClass.getMsg(B, null, null, CMMsg.MSG_HUH, null, CMMsg.MSG_HUH, "lower anchor", CMMsg.NO_EFFECT, null);
ship.okMessage(B, order);
runAll();
check("sail", gvar("FIXST8S") == "Fixsteightsailor:ANCHOR", "FIXST8S='" + gvar("FIXST8S") + "'");
check("dock", ship.isAnchorDown() && gvar("FIXST8K") == "" + CMLib.map().getExtendedRoomID(R), "anchor " + ship.isAnchorDown() + ", FIXST8K='" + gvar("FIXST8K") + "'");
R.bringMobHere(B, false);
SEv.moved(B, deck, R);
runAll();
check("disembark", gvar("FIXST8D") == "Fixsteightsailor", "FIXST8D='" + gvar("FIXST8D") + "'");
// into another room
var id2 = "" + CMLib.map().getExtendedRoomID(R2);
SEv.vesselArrived(ship, R2);
runAll();
check("arrive", gvar("FIXST8A") == id2, "FIXST8A='" + gvar("FIXST8A") + "'");
check("greet", gvar("FIXST8G") == id2 && gvar("FIXST8X") == "", "FIXST8G='" + gvar("FIXST8G") + "', FIXST8X='" + gvar("FIXST8X") + "'");
R2.delScript(SG);
B.destroy();
ship.destroy();
done();
