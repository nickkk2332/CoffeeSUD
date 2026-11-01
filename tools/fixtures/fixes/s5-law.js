// S5 and S12: an area's laws and conquest in its STATE: warrants, old
// warrants and fines, and who rules it with how many control points
FIXFILE = "s5-law";
var Law = CM.Common.interfaces.Law;
var A = M.location().getArea();
var B = CMClass.getBehavior("Arrest");
B.setParms("");
A.addBehavior(B);
var laws = callTyped(B, null, "getLaws", ["Environmental", "boolean"], [A, java.lang.Boolean.FALSE]);
var W = CMClass.getCommon("DefaultArrestWarrant");
W.setCriminal(M);
W.setCrime("murder");
W.setOffenses(2);
W.setLastOffense(CM.core.GameTime.now() - 5000);
W.setState(Law.STATE_ARRESTING);
laws.warrants().add(W);
field(B, "finesAssessed").put(M, java.lang.Double.valueOf(40.0));
var lastAreaField = null;
for (var c = B.getClass(); c != null && lastAreaField == null; c = c.getSuperclass()) { try { lastAreaField = c.getDeclaredField("lastAreaName"); } catch (e) { } }
lastAreaField.setAccessible(true);
lastAreaField.set(B, A.Name());
var state = B.getStat("STATE");
check("written", state.indexOf("murder") >= 0 && state.indexOf("<FINE") >= 0, "state " + state);
// as after a restore: the law's lists are empty, and a fresh behaviour has the state
laws.warrants().clear();
A.delBehavior(B);
var B2 = CMClass.getBehavior("Arrest");
B2.setParms("");
A.addBehavior(B2);
B2.setStat("STATE", state);
call(B2, "resumeSavedLaw", [A, laws]);
var back = (laws.warrants().size() == 1) ? laws.warrants().get(0) : null;
check("warrant", back != null && back.criminal() == M && back.crime() == "murder" && back.offenses() == 2 && back.state() == Law.STATE_SEEKING,
	"warrants " + laws.warrants().size() + (back == null ? "" : ", crime " + back.crime() + ", state " + back.state()));
check("fine", field(B2, "finesAssessed").get(M) == 40.0, "fine " + field(B2, "finesAssessed").get(M));
// a second area under the same laws does not put it back twice
call(B2, "setRunState", [state]);
call(B2, "resumeSavedLaw", [A, laws]);
check("once", laws.warrants().size() == 1, laws.warrants().size() + " warrants");
laws.warrants().clear();
A.delBehavior(B2);

var C = CMClass.getBehavior("Conquerable");
C.setParms("");
var points = field(C, "clanControlPoints");
points.put("FIXCLAN", java.lang.reflect.Array.newInstance(java.lang.Integer.TYPE, 1));
points.get("FIXCLAN")[0] = 75;
var setF = function(obj, name, value) {
	for (var c = obj.getClass(); c != null; c = c.getSuperclass()) {
		try { var f = c.getDeclaredField(name); f.setAccessible(true); f.set(obj, value); return; } catch (e) { }
	}
};
setF(C, "holdingClan", "FixClan");
setF(C, "prevHoldingClan", "OldClan");
setF(C, "conquestDate", java.lang.Long.valueOf(123456789));
setF(C, "revoltFails", java.lang.Integer.valueOf(3));
var cstate = C.getStat("STATE");
var C2 = CMClass.getBehavior("Conquerable");
C2.setParms("");
C2.setStat("STATE", cstate);
check("conquest", field(C2, "holdingClan") == "FixClan" && field(C2, "prevHoldingClan") == "OldClan"
	&& field(C2, "conquestDate") == 123456789 && field(C2, "revoltFails") == 3 && field(C2, "clanControlPoints").get("FIXCLAN")[0] == 75
	&& field(C2, "totalControlPoints") < 0,
	"state " + cstate);
done();
