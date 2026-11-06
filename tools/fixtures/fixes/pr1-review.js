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

// a glossary file saved with a byte-order mark reads its first rule
var hadG = V.file("vocab/glossary.ini").isFile(), oldG = hadG ? s(V.read("vocab/glossary.ini")) : "";
try {
	V.write("vocab/glossary.ini", "\uFEFFmana=aether\nsword=short sword\n");
	check("glossary with BOM", s(V.glossary("mana", "ALL", false)) == "aether", s(V.glossary("mana", "ALL", false)));
	// SCORE's lines have had the ALL rules from L() already: SCORE adds only its own scope's
	check("score glossary once", s(V.scopedOnly("a short sword", "SCORE")) == "a short sword", s(V.scopedOnly("a short sword", "SCORE")));
} finally {
	if (hadG) V.write("vocab/glossary.ini", oldG); else V.file("vocab/glossary.ini")["delete"]();
	V.clear();
}

// a saved threat reference to a mob no longer in the world is dropped, not searched for on every tick
var CA = CMClass.getBehavior("CombatAbilities"), pending = field(CA, "pendingThreat");
pending.put("instance:nosuch-fixthreat", java.lang.Integer.valueOf(5));
for (var k = 0; k < 12; k++) callTyped(CA, null, "restoreThreat", [], []);
check("stale threat dropped", pending.isEmpty(), pending);

// several mobs of one name and place that shared a legacy JS store each start from a copy of it
var JSS = java.lang.Class.forName("com.planet_ink.coffee_mud.core.JSStore"), savedF = JSS.getDeclaredField("saved");
savedF.setAccessible(true);
var stores = savedF.get(null), RR = scratchRoom();
RR.setRoomID("FixStoreArea#1");
var g1 = newMob("GenMob", RR), g2 = newMob("GenMob", RR);
g1.setName("a fixstore guard"); g2.setName("a fixstore guard"); g1.setStartRoom(RR); g2.setStartRoom(RR);
var legacy = "mob:" + CMLib.map().getExtendedRoomID(RR) + "/a fixstore guard";
stores.put(legacy, '{"seen":1}');
var k1 = s(CM.core.JSStore.keyOf(g1)), k2 = s(CM.core.JSStore.keyOf(g2));
check("duplicate legacy store copied", k1 != k2 && s(stores.get(k1)) == '{"seen":1}' && s(stores.get(k2)) == '{"seen":1}' && stores.containsKey(legacy),
	k1 + "=" + stores.get(k1) + " " + k2 + "=" + stores.get(k2));
stores.remove(legacy); stores.remove(k1); stores.remove(k2); g1.destroy(); g2.destroy();

// DelayedTransporter moves as a teleport does, unless ENTRY=CHECK asks the rooms first
var from = scratchRoom(), to = CMClass.getLocale("StdRoom"), homeArea = M.location().getArea();
to.setRoomID(homeArea.Name() + "#7701"); to.setArea(homeArea); to.setDisplayText("Fix portal end"); homeArea.addProperRoom(to);
var req = CMClass.getAbility("Prop_ReqEntry"); req.setMiscText("-race +elf"); to.addNonUninvokableEffect(req);
var walker = newMob("GenMob", from);
function portalTo(entry) {
	var P = CMClass.getBehavior("DelayedTransporter");
	P.setParms("min=0 max=0 chance=100" + entry + ";" + CMLib.map().getExtendedRoomID(to));
	return P;
}
var lookup = CMLib.map().getRoom(CMLib.map().getExtendedRoomID(to));
if (lookup == to) {
	var P1 = portalTo(" ENTRY=CHECK"); P1.tick(from, Tickable.TICKID_ROOM_BEHAVIOR); P1.tick(from, Tickable.TICKID_ROOM_BEHAVIOR);
	var checked = walker.location() == from;
	var P2 = portalTo(""); P2.tick(from, Tickable.TICKID_ROOM_BEHAVIOR); P2.tick(from, Tickable.TICKID_ROOM_BEHAVIOR);
	check("portal forces, ENTRY=CHECK asks", checked && walker.location() == to, "checked " + checked + ", at " + walker.location());
}
else
	check("portal forces, ENTRY=CHECK asks", false, "the test room is not found by its ID");
walker.destroy(); homeArea.delProperRoom(to); to.destroy();

// NPC organisations: a character may belong to several
check("several NPC guilds", CMProps.getMaxClansThisCategory("NPCGUILD") == 10 && CMProps.getMaxClansThisCategory("SOMETHINGELSE") == 1,
	CMProps.getMaxClansThisCategory("NPCGUILD") + " " + CMProps.getMaxClansThisCategory("SOMETHINGELSE"));

// a line told to one listener alone keeps the framing the room would give it: the emoter's name, a sound's colour
var LR = scratchRoom(), ear = newMob("GenMob", LR), ES = CMClass.getCommon("FakeSession");
ES.initialize(null, "MEMORY"); ear.setSession(ES); ES.setMob(ear);
var heardAt = 0;
function heardText() { var x = s(field(ES, "bout").toString()), part = x.substring(heardAt); heardAt = x.length; return part; }
var bell = newMob("GenMob", LR); bell.setName("a fixbell ringer");
var EM = CMClass.getBehavior("Emoter"); EM.setParms("min=1000 max=1000;SOUND rings the bell slowly"); bell.addBehavior(EM);
heardText();
var listened = EM.listen(ear, bell), said = heardText();
// (the test session prints <S-NAME> from its own character's view, so a name, not which, is checked here)
function named(text, line) { var at = text.indexOf(line); return at > 0 && /\S/.test(text.substring(0, at)); }
check("listen keeps the emoter's name", listened && named(said, "rings the bell slowly"), said);
var SO = CMClass.getBehavior("Sounder");
callTyped(SO, null, "tellOne", ["MOB", "Environmental", "String", "String"], [ear, bell, "EMOTE", "yawns widely"]);
var emoted = heardText();
callTyped(SO, null, "tellOne", ["MOB", "Environmental", "String", "String"], [ear, bell, "SOUND", "A bell tolls."]);
var sounded = heardText();
check("sounder per-viewer framing", named(emoted, "yawns widely") && sounded.indexOf("A bell tolls.") >= 0,
	emoted + " | " + sounded);
ear.setSession(null); bell.destroy(); ear.destroy();

// a room's SPEECH_PROG hears no foreign speech (a creature's hears what it understands)
var SR = scratchRoom(), talker = newMob("GenMob", SR), RS = scriptOn(SR, "SPEECH_PROG hello\nmpsetvar FIXTEST FIXPRROOM yes\n~\n");
function roomHears(tool) {
	clearVar("FIXPRROOM");
	var msg = CMClass.getMsg(talker, null, tool, CMMsg.MSG_SPEAK, "<S-NAME> say(s) 'hello there'");
	RS.executeMsg(SR, msg); runQueued(RS);
	return gvar("FIXPRROOM") == "yes";
}
var commonHeard = roomHears(null), elvishHeard = roomHears(CMClass.getAbility("Elvish"));
check("room speech_prog ignores foreign speech", commonHeard && !elvishHeard, "common " + commonHeard + ", elvish " + elvishHeard);
talker.destroy();
done();
