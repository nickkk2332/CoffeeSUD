// S25, in a game built from the world files: the room's script, kept there
// as CDATA, is as it was made
FIXFILE = "s25-cdata";
var R = otherRoom();
var B = (R == null) ? null : R.fetchBehavior("Scriptable");
var parms = (B == null) ? "" : String(B.getParms());
// the room's storage gives back its lines ended \r\n, as it does any room's
M.tell("FIXINFO script has carriage returns: " + (parms.indexOf("\r") >= 0));
var lines = parms.replace(/\r/g, "");
check("script", lines == "GREET_PROG 100\nif var($n FIXCDATA < 5)\n  mpecho FIXCDATA small\nendif\n~\n", "the script is " + JSON.stringify(lines).replace(/\\/g, "/"));
done();
