// A05 build trees on perks.ini: TREE/ROOT/LINKS (a perk is taken next to one held), REQUIRESANY, GROUP (one of a
// group), EFFECTS (held while the perk is, only its own taken off), PERK REFUND (refused while something stands on
// it), PERKS TREE, a perk taken out of perks.ini (its effects go, its pick is free), WORLD CHECK's perks.ini problems.
// A06: MPPERK PICKS gives a pool picks of its own; MPPERK CLEAR takes back only that pool's perks.
FIXFILE = "a05-perks";
function s(x) { return "" + x; }
function has(t, p) { return s(t).indexOf(p) >= 0; }
function list() { var L = new java.util.ArrayList(); for (var i = 0; i < arguments.length; i++) L.add(arguments[i]); return L; }
function write(f, text) { f.getParentFile().mkdirs(); java.nio.file.Files.write(f.toPath(), new java.lang.String(text).getBytes("UTF-8")); PK.clear(); }
var PK = CM.core.Perks;
var PF = PK.file();
var had = PF.isFile() ? s(new java.lang.String(java.nio.file.Files.readAllBytes(PF.toPath()), "UTF-8")) : null;
var R0 = M.location(), made = [];
function hero(name) {
	var h = newMob("GenMob", R0); h.setName(name);
	h.baseCharStats().setMyRace(CMClass.getRace("Human"));
	h.basePhyStats().setLevel(1); h.recoverCharStats(); h.recoverPhyStats();
	h.setPlayerStats(CMClass.getCommon("DefaultPlayerStats"));
	made.push(h); return h;
}
var VS = new JavaAdapter(CM.Common.FakeSession, { isFake: function() { return false; } });
VS.initialize(null, "MEMORY");
var seen = 0;
function said() { var all = s(field(VS, "bout")); var o = all.substring(seen); seen = all.length; return o.replace(/\^./g, "").replace(/\s+/g, " "); }
var PERK = CMClass.getCommand("PerkCmd");
function perk() { said(); var a = ["PERK"]; for (var i = 0; i < arguments.length; i++) a.push(arguments[i]); PERK.execute(H, list.apply(null, a), 0); return said(); }
var H = null;
try {
	write(PF, ["# fixa05",
		"REFUNDS=YES",
		'PERK_FXSPARK NAME="Fx Spark" POOL=FIRE TREE=Fire ROOT=YES LINKS=PERK_FXFLAME EFFECTS="Prop_Adjuster;STR+1"',
		'PERK_FXFLAME NAME="Fx Flame" POOL=FIRE TREE=Fire LINKS=PERK_FXBLAZE,PERK_FXEMBER',
		'PERK_FXBLAZE NAME="Fx Blaze" POOL=FIRE TREE=Fire GROUP=AFFINITY',
		'PERK_FXEMBER NAME="Fx Ember" POOL=FIRE TREE=Fire GROUP=AFFINITY',
		'PERK_FXSMOKE NAME="Fx Smoke" POOL=FIRE TREE=Fire LINKS=PERK_FXBLAZE',
		'PERK_FXWARD NAME="Fx Ward" POOL=GUILD REQUIRESANY=PERK_FXBLAZE,PERK_FXEMBER REFUND=NO',
		""].join("\n"));
	var P = PK.plan();
	check("read", P.perks.size() == 6 && P.refunds && PK.problems().isEmpty(), P.perks.size() + " " + PK.problems());
	H = hero("Fxa05hero"); H.setSession(VS); VS.setMob(H);
	var spark = PK.byId("PERK_FXSPARK"), flame = PK.byId("PERK_FXFLAME"), blaze = PK.byId("PERK_FXBLAZE"), ember = PK.byId("PERK_FXEMBER"),
		smoke = PK.byId("PERK_FXSMOKE"), ward = PK.byId("PERK_FXWARD");
	// A06: a pool's picks given by a script (a boss, a track, a guild), not by level
	check("no-picks", PK.earned(H, "FIRE") == 0 && has(PK.why(H, spark, false), "no fire pick left"), s(PK.why(H, spark, false)));
	var sm = scriptedMob("Fxa05scribe", "ONCE_PROG 100\nmpperk Fxa05hero PICKS FIRE +4\nmpperk Fxa05hero PICKS GUILD 1\n~\n");
	made.push(sm); tickMob(sm);
	check("picks-given", PK.earned(H, "FIRE") == 4 && PK.given(H, "GUILD") == 1, "fire " + PK.earned(H, "FIRE") + " guild " + PK.given(H, "GUILD"));
	// the tree: a root first, then next to what is held
	check("not-next", has(PK.why(H, flame, false), "not next to a perk you have in Fire (Fx Spark or Fx Blaze or Fx Ember)"), s(PK.why(H, flame, false)));
	var str0 = H.charStats().getStat(CM.Common.interfaces.CharStats.STAT_STRENGTH);
	check("root", has(perk("Fx Spark"), "You take Fx Spark") && H.fetchEffect("Prop_Adjuster") != null, "spark not taken, or its effect not held");
	H.recoverCharStats();
	check("effect-held", H.charStats().getStat(CM.Common.interfaces.CharStats.STAT_STRENGTH) == str0 + 1, "strength " + H.charStats().getStat(CM.Common.interfaces.CharStats.STAT_STRENGTH) + " was " + str0);
	check("next", PK.why(H, flame, false) == null && has(PK.why(H, smoke, false), "not next to"), s(PK.why(H, smoke, false)));
	perk("Fx Flame");
	// any-of, and one of a group
	check("requires-any", has(PK.why(H, ward, false), "needs one of Fx Blaze, Fx Ember"), s(PK.why(H, ward, false)));
	perk("Fx Blaze");
	check("group", has(PK.why(H, ember, false), "you have Fx Blaze, and only one of affinity can be held"), s(PK.why(H, ember, false)));
	check("any-met", PK.why(H, ward, false) == null, s(PK.why(H, ward, false)));
	perk("Fx Ward");
	// PERKS and PERKS TREE
	said(); PERK.execute(H, list("PERKS"), 0); var o = said();
	check("list", has(o, "Fx Spark [Fire]") && has(o, "(can be given back)") && has(o, "Trees: Fire") && has(o, "PERK REFUND"), o.substring(0, 600));
	said(); PERK.execute(H, list("PERKS", "TREE", "fire"), 0); var t = said();
	check("tree-view", has(t, "[x] Fx Spark (a way in), next to Fx Flame") && has(t, "[ ] Fx Smoke") && has(t, "[-] Fx Ember"), t);
	// refunds: refused while something stands on it, allowed for a leaf; REFUND=NO holds
	check("refund-dependants", has(perk("REFUND", "Fx Flame"), "first give back what stands on it: Fx Blaze"), "flame was given back under blaze");
	check("refund-any-of", has(perk("REFUND", "Fx Blaze"), "first give back what stands on it: Fx Ward"), "blaze went from under ward");
	check("refund-no", has(perk("REFUND", "Fx Ward"), "it cannot be given back"), "ward was refundable");
	perk("Fx Smoke");
	var left = PK.left(H, "FIRE");
	check("refund-leaf", has(perk("REFUND", "Fx Smoke"), "You give back Fx Smoke") && H.findTattoo("PERK_FXSMOKE") == null && PK.left(H, "FIRE") == left + 1, "left " + PK.left(H, "FIRE") + " was " + left);
	// A06: leaving a pool takes back only its perks
	var sc = scriptedMob("Fxa05clear", "ONCE_PROG 100\nmpperk Fxa05hero CLEAR GUILD\n~\n"); made.push(sc); tickMob(sc);
	check("clear-pool", H.findTattoo("PERK_FXWARD") == null && H.findTattoo("PERK_FXBLAZE") != null, "guild clear took the wrong perks");
	// a perk taken out of perks.ini: its effects go, its tattoo stays, its pick is free again
	write(PF, ["# fixa05 again", "REFUNDS=YES",
		'PERK_FXFLAME NAME="Fx Flame" POOL=FIRE TREE=Fire ROOT=YES LINKS=PERK_FXBLAZE',
		'PERK_FXBLAZE NAME="Fx Blaze" POOL=FIRE TREE=Fire', ""].join("\n"));
	PK.sync(H);
	check("removed-effects", H.fetchEffect("Prop_Adjuster") == null && H.findTattoo("PERK_FXSPARK") != null, "adjuster " + H.fetchEffect("Prop_Adjuster"));
	check("removed-listed", has(PK.removed(H), "PERK_FXSPARK") && PK.spent(H, "FIRE") == 2, "removed " + PK.removed(H) + " spent " + PK.spent(H, "FIRE"));
	said(); PERK.execute(H, list("PERKS"), 0); o = said();
	check("removed-shown", has(o, "No longer in this world") && has(o, "PERK_FXSPARK"), o.substring(0, 600));
	// perks.ini changed: an effect added to a held perk is put on; one taken off it comes off
	write(PF, ["# fixa05 effects", 'PERK_FXFLAME NAME="Fx Flame" POOL=FIRE TREE=Fire ROOT=YES EFFECTS="Prop_Smell;woodsmoke"', ""].join("\n"));
	PK.sync(H);
	check("effect-added", H.fetchEffect("Prop_Smell") != null, "the new effect was not put on");
	// one of the same kind from elsewhere is left alone: not doubled, not taken off
	write(PF, ["# fixa05 effects 2", 'PERK_FXFLAME NAME="Fx Flame" POOL=FIRE TREE=Fire ROOT=YES', ""].join("\n"));
	PK.sync(H);
	var other = CMClass.getAbility("Prop_Adjuster"); other.setMiscText("DEX+1"); H.addNonUninvokableEffect(other);
	write(PF, ["# fixa05 effects 3", 'PERK_FXFLAME NAME="Fx Flame" POOL=FIRE TREE=Fire ROOT=YES EFFECTS="Prop_Adjuster;CON+1"', ""].join("\n"));
	PK.sync(H);
	var kept = H.fetchEffect("Prop_Adjuster") != null && s(H.fetchEffect("Prop_Adjuster").text()) == "DEX+1";
	write(PF, ["# fixa05 effects 4", 'PERK_FXFLAME NAME="Fx Flame" POOL=FIRE TREE=Fire ROOT=YES', ""].join("\n"));
	PK.sync(H);
	check("elsewhere-kept", H.fetchEffect("Prop_Smell") == null && kept && H.fetchEffect("Prop_Adjuster") != null && s(H.fetchEffect("Prop_Adjuster").text()) == "DEX+1",
		"smell " + H.fetchEffect("Prop_Smell") + " adjuster " + (H.fetchEffect("Prop_Adjuster") == null ? null : H.fetchEffect("Prop_Adjuster").text()));
	// WORLD CHECK: a tree with no way in, a link to nothing, a link to another tree
	write(PF, ['PERK_FXA NAME="Fx A" TREE=Ash LINKS=PERK_FXNONE', 'PERK_FXB NAME="Fx B" TREE=Oak ROOT=YES LINKS=PERK_FXA', ""].join("\n"));
	var found = s(PK.problems());
	check("check", has(found, "tree ASH: no perk of it is a ROOT") && has(found, "PERK_FXA: LINKS names PERK_FXNONE, which is not a perk") && has(found, "PERK_FXB: LINKS names PERK_FXA, of another tree"), found);
	var wc = s(CM.core.WorldCheck.run(null, new java.util.HashSet(java.util.Arrays.asList(["DATA"]))));
	check("world-check", has(wc, "perks.ini: tree ASH"), wc.substring(0, 400));
} finally {
	if (had != null) write(PF, had); else if (PF.isFile()) { PF["delete"](); PK.clear(); }
	made.forEach(function (m) { m.setSession(null); if (m.playerStats() != null) m.setPlayerStats(null); m.destroy(); });
}
done();
