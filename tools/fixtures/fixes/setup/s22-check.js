// S22, after the restore: the entry limit is back with its count and about
// a day still to go; the one whose day was over is not; the planar visit
// is back, about a minute old
FIXFILE = "s22-check";
var GameTime = CM.core.GameTime;
var map = M.playerStats().getClassVariableMap(CMClass.getCharClass("StdCharClass"));
var day = 24 * 60 * 60 * 1000;
var lim = map.get("FIXINST/42/0");
check("back", lim != null, "the entry limit is gone");
check("count", (lim != null) && (lim.first[0] == 1), "count " + ((lim == null) ? "" : lim.first[0]));
var left = (lim == null) ? 0 : (lim.second[0] - GameTime.now());
check("left", (left > day - 10 * 60 * 1000) && (left <= day), "time left " + left);
check("old-gone", map.get("FIXOLD/7/0") == null, "the finished limit came back");
var P = M.fetchAbility("Spell_Planeshift");
var v = (P == null) ? "" : String(P.getStat("VISITS"));
check("visit", v.indexOf(">Plane of Fire_FixArea<") > 0 && v.indexOf('N="2"') > 0, "visits: " + v);
var ago = CMath.s_long(v.replace(/.*AGO="([0-9]+)".*/, "$1"));
check("visit-ago", (ago >= 60000) && (ago < 60000 + 10 * 60 * 1000), "ago " + ago);
done();
