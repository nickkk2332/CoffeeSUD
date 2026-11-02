// C1: CombatAbilities SUPPORT=SELF|LEADER|GROUP|MASK:zapper, HEALAT=n (the most hurt first, only under n%),
// CUREFIRST=Poison, BUFFOUTOFCOMBAT; Healer HEALAT= SPELLS= TARGET=; NiceAbilities HEALAT=.
FIXFILE = "c1-support";
function s(x) { return "" + x; }
var R = scratchRoom();
var V = newMob("GenMob", R);
V.setName("Fixc1pip");
var VS = new JavaAdapter(CM.Common.FakeSession, { isFake: function() { return false; } });
VS.initialize(null, "MEMORY"); V.setSession(VS); VS.setMob(V);
V.setPlayerStats(CMClass.getCommon("DefaultPlayerStats"));
function npc(name, hp, level) {
	var m = newMob("GenMob", R);
	m.setName(name);
	m.basePhyStats().setLevel(level || 20);
	m.baseState().setHitPoints(hp); m.baseState().setMana(1000);
	m.recoverPhyStats(); m.recoverCharStats(); m.recoverMaxState(); m.resetToMaxState();
	return m;
}
function teach(m, ids) { ids.forEach(function (id) { var A = CMClass.getAbility(id); A.setProficiency(100); m.addAbility(A); }); }
function behave(m, cls, parms) { var B = CMClass.getBehavior(cls); B.setParms(parms); m.addBehavior(B); return B; }
function hp(m) { return m.curState().getHitPoints(); }
function fight(a, b) { a.setVictim(b); b.setVictim(a); }
function peace() { [shaman, chief, foe, selfish].forEach(function (m) { if (m) m.makePeace(true); }); }
function tickN(m, B, n) { for (var i = 0; i < n; i++) { m.setActions(20); m.curState().setMana(m.maxState().getMana()); B.tick(m, Tickable.TICKID_MOB); } }

var chief = npc("Fixc1chief", 100);
var foe = npc("Fixc1foe", 500);
var shaman = npc("Fixc1shaman", 100);
var selfish = null;
teach(shaman, ["Prayer_CureSerious", "Prayer_RemovePoison", "Prayer_Bless"]);
var SB = behave(shaman, "CombatAbilities", "proficient SUPPORT=MASK:\"-NAMES +Fixc1chief\" HEALAT=60 CUREFIRST=Poison BUFFOUTOFCOMBAT");
check("parsed", s(field(SB, "support").mode) == "MASK" && field(SB, "healAt") == 60 && s(field(SB, "cureFirst")) == "[POISON]" && field(SB, "buffOut") == true,
	field(SB, "support").mode + " " + field(SB, "healAt") + " " + field(SB, "cureFirst"));

// in a fight: the hurt chief (not its leader) is healed, under 60%
chief.curState().setHitPoints(40);
fight(shaman, foe);
tickN(shaman, SB, 6);
check("heal-chief", hp(chief) > 40, "the chief stayed at " + hp(chief));
// above the threshold: no heal
chief.curState().setHitPoints(80);
tickN(shaman, SB, 6);
check("healat", hp(chief) == 80, "healed at 80%: " + hp(chief));
// poison first
var P = CMClass.getAbility("Poison");
P.startTickDown(foe, chief, 50);
check("poisoned", chief.fetchEffect("Poison") != null, "no poison to cure");
tickN(shaman, SB, 6);
check("cure-first", chief.fetchEffect("Poison") == null, "still poisoned");
// out of a fight: a blessing for the chief
peace();
chief.delEffect(chief.fetchEffect("Prayer_Bless"));
tickN(shaman, SB, 9);
check("buff", chief.fetchEffect("Prayer_Bless") != null || shaman.fetchEffect("Prayer_Bless") != null, "no blessing before a fight");

// SUPPORT=SELF: the chief is not its business
selfish = npc("Fixc1selfish", 100);
teach(selfish, ["Prayer_CureSerious"]);
var SS = behave(selfish, "CombatAbilities", "proficient SUPPORT=SELF HEALAT=60");
chief.curState().setHitPoints(30);
selfish.curState().setHitPoints(50);
fight(selfish, foe);
tickN(selfish, SS, 6);
check("self", hp(chief) == 30 && hp(selfish) > 50, "chief " + hp(chief) + " self " + hp(selfish));
peace();

// Healer: HEALAT, SPELLS, TARGET=MASK
var acolyte = npc("Fixc1acolyte", 100);
var HB = behave(acolyte, "Healer", "min=1 max=1 chance=100 HEALAT=70 SPELLS=Prayer_CureSerious TARGET=MASK:\"-NAMES +Fixc1pip\"");
V.baseState().setHitPoints(100); V.recoverMaxState(); V.curState().setHitPoints(50);
chief.curState().setHitPoints(20);
tickN(acolyte, HB, 4);
check("healer", hp(V) > 50 && hp(chief) == 20, "pip " + hp(V) + " chief " + hp(chief));
V.curState().setHitPoints(90);
tickN(acolyte, HB, 4);
check("healer-healat", hp(V) == 90, "healed at 90%: " + hp(V));

// NiceAbilities: HEALAT and its mask (it acts only with a player in the room: the harness's own)
var R0 = M.location();
var nurse = newMob("GenMob", R0);
nurse.setName("Fixc1nurse"); nurse.basePhyStats().setLevel(20); nurse.baseState().setMana(1000);
nurse.recoverPhyStats(); nurse.recoverMaxState(); nurse.resetToMaxState();
teach(nurse, ["Prayer_CureSerious"]);
// (NiceAbilities, as always, uses only the prayers its alignment allows)
CMLib.factions().setAlignment(nurse, CM.Common.interfaces.Faction.Align.GOOD);
var chief2 = newMob("GenMob", R0);
chief2.setName("Fixc1chieftwo"); chief2.baseState().setHitPoints(100); chief2.recoverMaxState(); chief2.resetToMaxState();
var hurt2 = newMob("GenMob", R0);
hurt2.setName("Fixc1other"); hurt2.baseState().setHitPoints(100); hurt2.recoverMaxState(); hurt2.resetToMaxState();
var NB = behave(nurse, "NiceAbilities", "min=1 max=1 chance=100 HEALAT=60;-NAMES +Fixc1chieftwo");
chief2.curState().setHitPoints(30);
hurt2.curState().setHitPoints(10);
tickN(nurse, NB, 4);
check("nice", hp(chief2) > 30 && hp(hurt2) == 10, "the nurse left the chief at " + hp(chief2) + ", the other " + hp(hurt2));
[nurse, chief2, hurt2].forEach(function (m) { m.destroy(); });

// the help
check("help", s(CMLib.help().getHelpText("BEHAVIOR_COMBATABILITIES", M, true)).indexOf("SUPPORT=") >= 0, "no SUPPORT= in AHELP COMBATABILITIES");

[chief, foe, shaman, selfish, acolyte].forEach(function (m) { m.destroy(); });
V.setPlayerStats(null); V.destroy();
R.destroy();
done();
