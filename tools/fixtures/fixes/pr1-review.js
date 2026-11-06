// Fixes from the review of the creator-authoring work: a lone ^{ in coloured text, legacy ALLSET= written
// back the same on every load, a room's author identity the same in every game, ambience lines whose own
// first word looks like a condition, glossary terms after colour codes and not inside tags, the script
// checker's uncertain findings as warnings, and a curse shown on an item seen by anyone.
FIXFILE = "pr1-review";
function s(v) { return "" + v; }
var CS = CM.core.CMStrings, IS = CM.core.ItemSets, AI = CM.core.AuthorIds, AM = CM.core.Ambience, V = CM.core.Vocabulary, SC = CM.core.ScriptCheck;

var len = -1, problem = "";
try { len = CS.lengthMinusColors("a ^{ b"); } catch (e) { problem = s(e); }
check("lone colour alias", len > 0 && problem == "", len + " " + problem);
check("colour alias stripped", CS.lengthMinusColors("^{NOSUCHALIAS}ab") >= 0, "threw");

var P = CMClass.getAbility("Prop_WearResister");
P.setMiscText('fire 50% ALLSET="fixset-2"');
var t1 = s(P.text()); P.setMiscText(t1); var t2 = s(P.text()); P.setMiscText(t2); var t3 = s(P.text());
check("allset stable", t1 == t2 && t2 == t3 && s(P.getStat("ALLSET")) == "fixset-2", t1 + " | " + t2 + " | " + t3);
check("allset removed cleanly", s(IS.withoutLegacy('fire 50% ALLSET="x-2"')) == "fire 50%" && s(IS.withoutLegacy("fire 50%  ")) == "fire 50%  ",
	"[" + IS.withoutLegacy('fire 50% ALLSET="x-2"') + "]");

var R = M.location(), before = s(R.text()), id = s(callTyped(null, java.lang.Class.forName("com.planet_ink.coffee_mud.core.AuthorIds"), "identity", ["Physical", "String", "String", "boolean"], [R, "AUTHOR", "", true]));
check("room identity stable", s(AI.key("FIX", R, "AUTHOR", "", "x", true)) == s(AI.key("FIX", R, "AUTHOR", "", "x", false)) && id == "AUTO:ROOM:" + CMLib.map().getExtendedRoomID(R) && R.fetchEffect("Prop_AuthorIdentity") == null && s(R.text()) == before, id);

var plain = AM.parse("Fog rolls in from the river.");
check("ambience first word is text", s(plain.text) == "Fog rolls in from the river." && plain.conditions.size() == 0 && s(plain.kind) == "", s(plain.text) + " / " + plain.conditions.size());
var tagged = AM.parse("FOG NIGHT SOUND Once again the bell tolls.");
check("ambience prefixes", tagged.conditions.size() == 2 && s(tagged.kind) == "SOUND" && s(tagged.text) == "Once again the bell tolls.", s(tagged.text) + " / " + tagged.kind + " / " + tagged.conditions.size());

var had = V.file("vocab/glossary.ini").isFile(), old = had ? s(V.read("vocab/glossary.ini")) : "";
try {
	V.write("vocab/glossary.ini", "mana=aether\n"); V.clear();
	check("term after colour code", s(V.glossary("^Hmana^N and \u001b[1mmana", "ALL", false)) == "^Haether^N and \u001b[1maether", s(V.glossary("^Hmana^N and \u001b[1mmana", "ALL", false)));
	check("term inside a tag kept", s(V.glossary("<A HREF='cast mana'>mana</A>", "ALL", false)) == "<A HREF='cast mana'>aether</A>", s(V.glossary("<A HREF='cast mana'>mana</A>", "ALL", false)));
} finally {
	if (had) V.write("vocab/glossary.ini", old); else V.file("vocab/glossary.ini")["delete"]();
	V.clear();
}

var found = SC.checkText("pr1", "ONCE_PROG\nMPSETVAR $i\nMPECHO $<$i NEVERSET>\nMPNOSUCHCMD\n~", null), warns = 0, errors = 0;
for (var i = 0; i < found.size(); i++) { if (SC.isWarning(found.get(i))) warns++; else errors++; }
check("uncertain findings are warnings", warns == 2 && errors == 1, found);

var C = CMClass.getAbility("Prop_Cursed"), low = newMob("GenMob", scratchRoom());
low.basePhyStats().setLevel(1); low.recoverPhyStats();
C.setMiscText("LEVEL=50 HIDDEN");
var hidden = C.detectedBy(low);
C.setMiscText("LEVEL=50 HIDDEN SHOWN=true");
check("shown curse seen by anyone", !hidden && C.detectedBy(low), hidden + " " + C.detectedBy(low));
low.destroy();
done();
