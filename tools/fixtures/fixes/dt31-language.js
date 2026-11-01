// DT31: LANGUAGE PREVIEW shows what a language does to a text; LANGUAGE COVERAGE counts its dictionary over a text.
FIXFILE = "dt31-language";
var R = M.location();
function has(text, part) { return ("" + text).replace(/\s+/g, " ").indexOf(part) >= 0; }
var B = newMob("GenMob", R);
B.setName("Fixlinguist");
var BS = CMClass.getCommon("FakeSession");
BS.initialize(null, "MEMORY");
B.setSession(BS);
BS.setMob(B);
var mark = 0;
function out() { var all = "" + field(BS, "bout").toString(); var s = all.substring(mark); mark = all.length; return s; }
function lang(words) { var L = new java.util.ArrayList(); L.add("LANGUAGE"); var w = words.split(" "); for (var i = 0; i < w.length; i++) if (w[i].length > 0) L.add(w[i]); CMClass.getCommand("LangToolsCmd").execute(B, L, 0); return out(); }

// an invented language: a dictionary of two words, and word lists by length
var Lg = CMClass.getAbility("GenLanguage").copyOf();
Lg.setStat("CLASS", "Fixtongue");
Lg.setStat("NAME", "Fixtongue");
Lg.setStat("WORDS", "a,e/ka,ru/mor,tal/shak,frod/zaneh,quorl");
Lg.setStat("HASHEDWORDS", "HELLO=zaal/WATER=hesh");

check("unknown", has(lang("PREVIEW Nofixtongue 50 hello"), "There is no language 'Nofixtongue'."), "an unknown language");
var said = lang("PREVIEW Fixtongue 100 hello water stone");
check("preview", has(said, "'hello water stone' in Fixtongue (Fixtongue), at 100%:") && has(said, "One who does not know it hears: "), said);
check("dict-words", has(said, "hello -> zaal (dictionary)") && has(said, "water -> hesh (dictionary)"), said);
check("list-word", has(said, "stone -> ") && has(said, "(word list of 5 letters)"), said);
check("full-hears", has(said, "hears: zaal hesh "), said);
check("listener-100", has(said, "A listener at 100% understands: hello water stone"), said);
check("reader", has(said, "Written, it says '") && has(said, "a reader at 100% reads: hello water stone"), said);
said = lang("PREVIEW Fixtongue 0 hello water stone");
check("listener-0", has(said, "A listener at 0% understands: ") && !has(said, "A listener at 0% understands: hello water stone"), said);
// COVERAGE over a room of the test's own
var room = CMClass.getLocale("StdRoom");
room.setRoomID(R.getArea().Name() + "#9431");
room.setArea(R.getArea());
room.setDisplayText("The water hall");
room.setDescription("Water runs here. Hello, says the water; the stone says hello back.");
said = lang("COVERAGE Fixtongue " + room.roomID());
check("coverage", has(said, "Fixtongue's dictionary has 5 of 15 words (33%).") && has(said, "The most common missing (") && has(said, "the (3)"), said);
check("coverage-none", has(lang("COVERAGE Fixtongue Nowherefix#0"), "There is no file, room or area 'Nowherefix#0'."), "a place that is not there");
R.getArea().delProperRoom(room);
// other words are LANGUAGES, as before (which prints only to a character with a session)
said = lang("");
check("languages", !has(said, "LANGUAGE PREVIEW") && !has(said, "There is no language"), said);

CMClass.delClass(CM.core.CMClass.CMObjectType.ABILITY, Lg);
B.setSession(null);
B.destroy();
done();
