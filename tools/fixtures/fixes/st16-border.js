// ST16: AREA_ENTER_PROG and AREA_LEAVE_PROG only when the area changes, with
// ALL for an area inside another; Prop_AreaEntry and Prop_AreaBanner text.
FIXFILE = "st16-border";
var SEv = CM.core.ScriptEvents;
var R = M.location();
var A = R.getArea();
var PS = CM.Common.interfaces.PhyStats;
function newArea(name) {
	var X = CMClass.getAreaType("StdArea");
	X.setName(name);
	X.basePhyStats().setDisposition(X.basePhyStats().disposition() | PS.IS_UNSAVABLE);
	X.recoverPhyStats();
	CMLib.map().addArea(X);
	var Q = CMClass.getLocale("StdRoom");
	Q.setRoomID(name + "#0");
	Q.setArea(X);
	Q.setDisplayText(name + " room");
	return X;
}
var X = newArea("FixST16 Marsh");
var XR = CMLib.map().getRoom("FixST16 Marsh#0");
var XR2 = CMClass.getLocale("StdRoom");
XR2.setRoomID("FixST16 Marsh#1");
XR2.setArea(X);
["FIXST16E", "FIXST16L", "FIXST16N", "FIXST16X"].forEach(clearVar);
var SX = scriptOn(X, "AREA_ENTER_PROG 100\nmpsetvar FIXTEST FIXST16E $n:$G:$0\n~\nAREA_LEAVE_PROG\nmpsetvar FIXTEST FIXST16L $n:$G\n~\n");
var SM = scriptOn(XR, "AREA_ENTER_PROG -NAME +fixstnobody\nmpsetvar FIXTEST FIXST16X $n\n~\n");
var RID = "" + CMLib.map().getExtendedRoomID(R);
SEv.moved(M, R, XR);
runQueued(SX); runQueued(SM);
check("enter", gvar("FIXST16E") == M.Name() + ":" + A.Name() + ":" + RID, "FIXST16E='" + gvar("FIXST16E") + "'");
check("mask", gvar("FIXST16X") == "", "FIXST16X='" + gvar("FIXST16X") + "'");
clearVar("FIXST16E");
SEv.moved(M, XR, XR2);
runQueued(SX);
check("inside", gvar("FIXST16E") == "" && gvar("FIXST16L") == "", "a move inside the area counted");
SEv.moved(M, XR2, R);
runQueued(SX);
check("leave", gvar("FIXST16L") == M.Name() + ":" + A.Name(), "FIXST16L='" + gvar("FIXST16L") + "'");
// ALL: an area inside the marsh counts for the marsh only with ALL
var Y = newArea("FixST16 Bog");
Y.addParent(X);
var YR = CMLib.map().getRoom("FixST16 Bog#0");
var SA = scriptOn(X, "AREA_ENTER_PROG ALL\nmpsetvar FIXTEST FIXST16N $G\n~\n");
clearVar("FIXST16E");
SEv.moved(M, XR, YR);
runQueued(SX); runQueued(SA);
check("all", gvar("FIXST16N") == "FixST16 Marsh" && gvar("FIXST16E") == "", "FIXST16N='" + gvar("FIXST16N") + "', FIXST16E='" + gvar("FIXST16E") + "'");
// the banner's text, as the player sees it
var P = CMClass.getAbility("Prop_AreaBanner");
P.setMiscText("TITLE=\"FIXST16 THE HAUNTED MARSH\" TEXT=\"Mist clings to the reeds.\" ONCE");
X.addNonUninvokableEffect(P);
var S = new JavaAdapter(CM.Common.FakeSession, { isFake: function() { return false; } });
S.initialize(null, "MEMORY");
var Q = newMob("StdMOB", R);
Q.setName("Fixstsixteen");
Q.setPlayerStats(CMClass.getCommon("DefaultPlayerStats"));
Q.setSession(S);
S.setMob(Q);
SEv.moved(Q, R, XR);
var out = "" + field(S, "bout").toString();
check("banner", out.indexOf("FIXST16 THE HAUNTED MARSH") >= 0 && out.indexOf("Mist clings to the reeds.") >= 0, out);
Q.setSession(null);
Q.setPlayerStats(null);
Q.destroy();
CMLib.map().obliterateMapArea(Y);
CMLib.map().obliterateMapArea(X);
done();
