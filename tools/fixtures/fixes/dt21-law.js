// DT21: LAW shows an area's law as it found its people and places, and handles warrants.
FIXFILE = "dt21-law";
var R = M.location();
var A = R.getArea();
function has(text, part) { return ("" + text).indexOf(part) >= 0; }
var B = newMob("GenMob", R);
B.setName("Fixlawyer");
var BS = CMClass.getCommon("FakeSession");
BS.initialize(null, "MEMORY");
B.setSession(BS);
BS.setMob(B);
var mark = 0;
function out() { var all = "" + field(BS, "bout").toString(); var s = all.substring(mark); mark = all.length; return s; }
function law(words) { var L = new java.util.ArrayList(); L.add("LAW"); if (words.length > 0) { var w = words.split(" "); for (var i = 0; i < w.length; i++) L.add(w[i]); } CMClass.getCommand("LawCmd").execute(B, L, 0); return out(); }

check("none", has(law(""), "There is no law here"), "a lawless area");
var arrest = CMClass.getBehavior("Arrest");
A.addBehavior(arrest);
var guard = newMob("GenMob", R);
guard.setName("a cityguard");
var said = law("");
check("law", has(said, "The law of " + A.Name() + ": Arrest (laws.ini)"), said);
// an officer must wander: one that does not is named, and why
check("officer-still", has(said, "Officers (cityguard): 0 found") && has(said, "a cityguard @ ") && has(said, "named as an officer, but does not wander"), said);
guard.addBehavior(CMClass.getBehavior("Mobile"));
said = law("");
check("officers", has(said, "Officers (cityguard): 1 found: a cityguard @ "), said);
check("judges", has(said, "Judges (justice, judge): 0 found"), said);
check("jail", has(said, "Jail: 'The Jail$' FOUND NOWHERE"), said);
check("crimes-count", has(said, " basic, ") && has(said, "Warrants: 0"), said);
var crimes = law("CRIMES assault");
check("crimes", has(crimes, "ASSAULT: "), crimes);
// a warrant, made and taken away
var suspect = newMob("GenMob", R);
suspect.setName("Fixsuspect");
var SS = CMClass.getCommon("FakeSession");
SS.initialize(null, "MEMORY");
suspect.setSession(SS);
SS.setMob(suspect);
said = law("ACCUSE Fixsuspect ASSAULT");
check("accuse", has(said, "Fixsuspect is accused of ASSAULT."), said);
said = law("WARRANTS");
check("warrants", has(said, "Fixsuspect: "), said);
said = law("PARDON Fixsuspect ALL");
check("pardon", has(said, "1 warrants against Fixsuspect taken away.") && has(law("WARRANTS Fixsuspect"), "none."), said);
check("no-crime", has(law("ACCUSE Fixsuspect FLURBLING"), "is not accused"), "an unknown crime");

suspect.setSession(null);
A.delBehavior(arrest);
B.setSession(null);
B.destroy(); guard.destroy(); suspect.destroy();
done();
