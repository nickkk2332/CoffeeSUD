// S22, before the save: entry limits as InstanceArea keeps them (one entry
// a day used, with a day to go; one whose day is over), and a planar visit
FIXFILE = "s22-play";
var GameTime = CM.core.GameTime;
var Pair = CM.core.collections.Pair;
var map = M.playerStats().getClassVariableMap(CMClass.getCharClass("StdCharClass"));
var day = 24 * 60 * 60 * 1000;
var now = GameTime.now();
map.put("FIXINST/42/0", new Pair(Packages.java.lang.reflect.Array.newInstance(Packages.java.lang.Integer.TYPE, 1), Packages.java.lang.reflect.Array.newInstance(Packages.java.lang.Long.TYPE, 1)));
var used = map.get("FIXINST/42/0");
used.first[0] = 1;
used.second[0] = now + day;
map.put("FIXOLD/7/0", new Pair(Packages.java.lang.reflect.Array.newInstance(Packages.java.lang.Integer.TYPE, 1), Packages.java.lang.reflect.Array.newInstance(Packages.java.lang.Long.TYPE, 1)));
var old = map.get("FIXOLD/7/0");
old.first[0] = 3;
old.second[0] = now - 1000;
check("set", map.get("FIXINST/42/0").first[0] == 1, "the limit was not set");
// and a planar travel ability with a visit a minute ago, which raised a
// return's difficulty twice
var P = CMClass.getAbility("Spell_Planeshift");
M.addAbility(P);
P = M.fetchAbility("Spell_Planeshift");
P.setStat("VISITS", '<V AGO="60000" N="2">Plane of Fire_FixArea</V>');
check("visit", P.getStat("VISITS").indexOf("Plane of Fire_FixArea") > 0, "the visit was not set: " + P.getStat("VISITS"));
done();
