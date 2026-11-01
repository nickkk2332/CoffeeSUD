// DT16: kits (world/kits/*.kit) and TESTCHAR, characters of the profile made from them with no questions.
FIXFILE = "dt16-testchar";
var R = M.location();
var Party = CM.core.Party;
function has(text, part) { return ("" + text).indexOf(part) >= 0; }
function run(cmdName, words) { var L = new java.util.ArrayList(); var w = words.split(" "); for (var i = 0; i < w.length; i++) L.add(w[i]); CMClass.getCommand(cmdName).execute(M, L, 0); }
var kits = new java.io.File(WorldFiles.worldDir(), "kits");
kits.mkdirs();
var kitText = "# a fixes kit\nrace Dwarf\nclass Fighter\ngender F\nalignment good\nlevel 5\ngold 250\nitem Longsword\nability Skill_Dodge 75\nvar FIXKITACT 3\nflurble 2\nroom " + CMLib.map().getExtendedRoomID(R) + "\n";
java.nio.file.Files.write(new java.io.File(kits, "fixtank.kit").toPath(), new java.lang.String(kitText).getBytes("UTF-8"));

// the kit as read
var KitCmd = CMClass.getCommand("KitCmd");
var K = call(KitCmd, "readKit", ["fixtank"]);
check("read", K != null && ("" + K.get("RACE")) == "Dwarf" && ("" + K.get("LEVEL")) == "5", "the kit was not read");
check("read-bad", has(K.problems, "fixtank.kit:11: 'flurble' is not a kit setting"), "problems " + K.problems);

// TESTCHAR makes a character of the profile, no questions
run("TestCharCmd", "TESTCHAR Fixtank KIT=fixtank");
var T = CMLib.players().getLoadPlayer("Fixtank");
check("made", T != null, "no character Fixtank");
if (T != null) {
	check("race-class", ("" + T.baseCharStats().getMyRace().ID()) == "Dwarf" && ("" + T.baseCharStats().getCurrentClass().ID()) == "Fighter", T.baseCharStats().getMyRace().ID() + " " + T.baseCharStats().getCurrentClass().ID());
	check("level", T.basePhyStats().level() == 5, "level " + T.basePhyStats().level());
	check("gender", String.fromCharCode(T.baseCharStats().getStat(CM.Common.interfaces.CharStats.STAT_GENDER)) == "F", "gender " + T.baseCharStats().getStat(CM.Common.interfaces.CharStats.STAT_GENDER));
	check("gold", CMLib.beanCounter().getTotalAbsoluteNativeValue(T) >= 250, "gold " + CMLib.beanCounter().getTotalAbsoluteNativeValue(T));
	var sword = T.findItem(null, "longsword");
	check("item", sword != null && !sword.amWearingAt(CM.Items.interfaces.Wearable.IN_INVENTORY), "the sword is " + (sword == null ? "missing" : "not worn"));
	var dodge = T.fetchAbility("Skill_Dodge");
	check("ability", dodge != null && dodge.proficiency() == 75, "dodge " + (dodge == null ? "missing" : dodge.proficiency()));
	check("var", ("" + VARS.getVar("Fixtank", "FIXKITACT")) == "3", "FIXKITACT is '" + VARS.getVar("Fixtank", "FIXKITACT") + "'");
	check("profile", Party.profileOf(M) != null && Party.profileOf(M).findPlayer("Fixtank") != null, "Fixtank is not in the profile");
	check("resting", !bool(CMLib.flags().isInTheGame(T, true)), "Fixtank came into the world without PARTY");
}
function bool(x) { return ("" + x) == "true"; }
// the name is taken now
run("TestCharCmd", "TESTCHAR Fixtank");
check("taken", CMLib.players().getLoadPlayer("Fixtank") == T, "a second Fixtank");

// PARTY: into the world, in the party
run("TestCharCmd", "TESTCHAR Fixheal PARTY");
var H = Party.findInWorld(M, "Fixheal");
check("party", H != null && bool(Party.isInParty(Party.leaderOf(M), H)), "Fixheal is " + (H == null ? "not in the world" : "not in the party"));
if (H != null) Party.rest(H);

// KIT on a follower or mob here
var kitted = newMob("GenMob", R);
kitted.setName("Fixkitted");
kitted.basePhyStats().setLevel(1);
kitted.recoverPhyStats();
run("KitCmd", "KIT fixtank Fixkitted");
check("kit-mob", kitted.basePhyStats().level() >= 5 && kitted.findItem(null, "longsword") != null, "level " + kitted.basePhyStats().level());
kitted.destroy();
done();
