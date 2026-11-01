// DT6: GOD turns developer cheats on and off (Prop_DevCheats) and suspends sysop powers.
FIXFILE = "dt6-cheats";
// the game's own Prop_DevCheats and Aggressive: CMClass loads its own copies of
// these classes, so their statics are reached through instances, not Packages
var PI = CMClass.getAbility("Prop_DevCheats");
function cs(name, args) { return call(PI, name, args); }
var Cheat = { NOCLIP: cs("wordOf", ["NOCLIP"]), NOCOST: cs("wordOf", ["NOCOST"]), NOAGGRO: cs("wordOf", ["NOAGGRO"]), SEEALL: cs("wordOf", ["SEEALL"]) };
var AG = CMClass.getBehavior("Aggressive");
function startFight(monster, victim) { return call(AG, "startFight", [monster, victim, true, false, null]); }
var SecFlag = CM.core.CMSecurity.SecFlag;
var PhyStats = CM.Common.interfaces.PhyStats;
var cmd = CMClass.getCommand("GodCmd");
function has(text, part) { return ("" + text).indexOf(part) >= 0; }
function bool(x) { return ("" + x) == "true"; }
function state(m, c) { var s = cs("state", [m, c]); return (s == null) ? "unset" : ("" + s); }
function god(m, words) { var L = new java.util.ArrayList(); L.add("GOD"); var w = words.split(" "); for (var i = 0; i < w.length; i++) L.add(w[i]); cmd.execute(m, L, 0); }

// two rooms with a locked door between them, and a human with no powers
var R1 = scratchRoom();
var R2 = scratchRoom();
var east = CM.core.Directions.EAST;
var door = CMClass.getExit("GenExit");
door.setDoorsNLocks(true, false, true, true, true, true);
R1.setRawExit(east, door);
R1.setRawDoor(east, R2);
var back = CMClass.getExit("GenExit");
back.setDoorsNLocks(true, false, true, true, true, true);
R2.setRawExit(CM.core.Directions.WEST, back);
R2.setRawDoor(CM.core.Directions.WEST, R1);
var B = newMob("GenMob", R1);
B.setName("Fixgod");
var BS = CMClass.getCommon("FakeSession");
BS.initialize(null, "MEMORY");
B.setSession(BS);
BS.setMob(B);
function out() { return "" + field(BS, "bout").toString(); }
B.baseState().setMovement(100); B.baseState().setMana(500); B.recoverMaxState(); B.resetToMaxState();

// with nothing on, the door stops a character without powers
check("unset", state(B, Cheat.NOCLIP) == "unset" && !bool(cs("noclip", [B, R2])), "NOCLIP is " + state(B, Cheat.NOCLIP));
CMLib.tracking().walk(B, east, false, true, true);
check("door-stops", B.location() == R1, "walked through a locked door with no cheats");

// GOD NOCLIP NOAGGRO ON: through the door, and walking costs nothing
god(B, "NOCLIP NOAGGRO ON");
var P = B.fetchEffect("Prop_DevCheats");
check("god-on", P != null && ("" + P.text()) == "NOCLIP NOAGGRO", "the cheats read '" + (P == null ? "none" : P.text()) + "'");
check("god-said", has(out(), "GOD: NOCLIP on, NOAGGRO on."), "GOD said " + out());
var moves = B.curState().getMovement();
CMLib.tracking().walk(B, east, false, true, true);
check("noclip-door", B.location() == R2, "NOCLIP did not pass the locked door");
check("noclip-free", B.curState().getMovement() == moves, "walking cost " + (moves - B.curState().getMovement()));
// past a Prop_Req* entry check too
var req = CMClass.getAbility("Prop_ReqEntry");
req.setMiscText("-race +elf");
R1.addNonUninvokableEffect(req);
CMLib.tracking().walk(B, CM.core.Directions.WEST, false, true, true);
check("noclip-req", B.location() == R1, "NOCLIP did not pass Prop_ReqEntry");
// off again: the door and the check stop the character
god(B, "NOCLIP OFF");
CMLib.tracking().walk(B, east, false, true, true);
check("noclip-off", B.location() == R1 && state(B, Cheat.NOCLIP) == "false", "NOCLIP OFF still walked, or reads " + state(B, Cheat.NOCLIP));
R2.bringMobHere(B, false);
CMLib.tracking().walk(B, CM.core.Directions.WEST, false, true, true);
check("req-stops", B.location() == R2, "Prop_ReqEntry let an unpowered human by");
R1.bringMobHere(B, false);
R1.delEffect(req);

// NOAGGRO: an aggressive creature leaves the character be
var brute = newMob("GenMob", R1);
brute.setName("Fixbrute");
check("noaggro", !bool(startFight(brute, B)), "the brute attacked through NOAGGRO");
god(B, "NOAGGRO");
check("noaggro-toggle", state(B, Cheat.NOAGGRO) == "false", "GOD NOAGGRO did not turn it off: " + state(B, Cheat.NOAGGRO));
check("aggro", bool(startFight(brute, B)), "the brute would not attack with NOAGGRO off");
brute.makePeace(true); B.makePeace(true);
brute.destroy();

// NOCOST: a spell costs no mana
var spell = CMClass.getAbility("Spell_Light");
spell.setProficiency(100);
B.addAbility(spell);
god(B, "NOCOST ON");
var mana = B.curState().getMana();
spell.invoke(B, new java.util.ArrayList(), B, false, 0);
check("nocost", B.curState().getMana() == mana, "the spell cost " + (mana - B.curState().getMana()) + " mana");
check("nowait", bool(cs("noWait", [B, R1])), "NOCOST left a wait");
god(B, "NOCOST OFF");
B.delEffect(B.fetchEffect("Spell_Light"));
mana = B.curState().getMana();
spell.invoke(B, new java.util.ArrayList(), B, false, 0);
check("cost", B.curState().getMana() < mana, "with NOCOST off the spell cost nothing");

// NODAMAGE and NODEATH
var foe = newMob("GenMob", R1);
foe.setName("Fixfoe");
god(B, "NODAMAGE ON");
var hp = B.curState().getHitPoints();
CMLib.combat().postDamage(foe, B, null, 20, CMMsg.MSG_OK_VISUAL | CMMsg.MASK_ALWAYS, -1, null);
check("nodamage", B.curState().getHitPoints() == hp, "took " + (hp - B.curState().getHitPoints()) + " damage");
god(B, "NODAMAGE OFF");
god(B, "NODEATH ON");
CMLib.combat().postDamage(foe, B, null, hp + 50, CMMsg.MSG_OK_VISUAL | CMMsg.MASK_ALWAYS, -1, null);
check("nodeath", !bool(B.amDead()) && B.curState().getHitPoints() == 1 && B.location() == R1, "dead " + B.amDead() + ", hp " + B.curState().getHitPoints());
foe.makePeace(true); B.makePeace(true);
foe.destroy();
B.resetToMaxState();

// SEEALL: the senses, even when blind
god(B, "SEEALL ON");
B.basePhyStats().setSensesMask(B.basePhyStats().sensesMask() | PhyStats.CAN_NOT_SEE);
B.recoverPhyStats();
var senses = B.phyStats().sensesMask();
check("seeall", (senses & PhyStats.CAN_SEE_DARK) != 0 && (senses & PhyStats.CAN_SEE_INVISIBLE) != 0 && (senses & PhyStats.CAN_SEE_HIDDEN) != 0 && (senses & PhyStats.CAN_NOT_SEE) == 0, "senses " + senses);
god(B, "SEEALL OFF");
check("seeall-off", (B.phyStats().sensesMask() & PhyStats.CAN_NOT_SEE) != 0, "still sees when blind");
B.basePhyStats().setSensesMask(B.basePhyStats().sensesMask() & ~PhyStats.CAN_NOT_SEE);
B.recoverPhyStats();

// a copy, as a save makes, keeps the cheats, and the original stays in force
god(B, "ALL ON");
P = B.fetchEffect("Prop_DevCheats");
var copy = P.copyOf();
check("copy", ("" + copy.text()) == ("" + P.text()) && bool(cs("on", [B, Cheat.NOCLIP])), "the copy reads '" + copy.text() + "'");
var other = newMob("GenMob", R1);
other.addNonUninvokableEffect(copy);
check("copy-own", bool(cs("on", [other, Cheat.SEEALL])) && bool(cs("on", [B, Cheat.SEEALL])), "the copy and the original are not both in force");
other.destroy();
god(B, "RESET");
check("reset", B.fetchEffect("Prop_DevCheats") == null && state(B, Cheat.NOCLIP) == "unset", "RESET left " + state(B, Cheat.NOCLIP));

// a sysop's powers: GOTO walks through doors, IMMORT keeps them alive, until GOD turns them off
var R0 = M.location();
check("sysop", bool(CMSecurity.isAllowed(M, R0, SecFlag.IMMORT)) && bool(cs("noclip", [M, R0])), "the builder is not a sysop here");
god(M, "IMMORT OFF");
check("suspend", !bool(CMSecurity.isAllowed(M, R0, SecFlag.IMMORT)) && bool(CMSecurity.isAllowed(M, R0, SecFlag.GOTO)), "IMMORT OFF did not suspend IMMORT alone");
god(M, "IMMORT ON");
check("unsuspend", bool(CMSecurity.isAllowed(M, R0, SecFlag.IMMORT)), "IMMORT ON did not give it back");
god(M, "ALL OFF");
check("all-off", !bool(cs("noclip", [M, R0])) && !bool(CMSecurity.isAllowed(M, R0, SecFlag.IMMORT)) && !bool(cs("noAggro", [M, R0, false])) && !bool(cs("noWait", [M, R0])),
	"ALL OFF left a power: noclip " + cs("noclip", [M, R0]) + ", immort " + CMSecurity.isAllowed(M, R0, SecFlag.IMMORT));
var list = "" + cmd.listing(M);
check("list", has(list, "NOCLIP    off") && has(list, "Suspended powers: IMMORT"), "GOD listed " + list);
god(M, "RESET");
check("reset-powers", bool(CMSecurity.isAllowed(M, R0, SecFlag.IMMORT)) && bool(cs("noclip", [M, R0])), "RESET did not give the powers back");
var unknown = "" + cmd.listing(M);
check("list-default", has(unknown, "NOCLIP    on (GOTO)"), "GOD listed " + unknown);

B.setSession(null);
B.destroy();
R1.destroy(); R2.destroy();
done();
