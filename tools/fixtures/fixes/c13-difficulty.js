// C13: DIFFICULTY: the ini's for a new game, the command in play (not in a fight), the presets' NPCDAMAGE, NPCHP,
// PLAYERDAMAGE and PLAYERXP on the player's side and the NPCs', DIFFICULTY_<NAME> presets, and @xP and @xD in formulas.
FIXFILE = "c13-difficulty";
var GD = CM.core.GameDifficulty;
function s(x) { return "" + x; }
function has(text, part) { return s(text).replace(/\s+/g, " ").indexOf(part) >= 0; }
var R = scratchRoom();
var V = newMob("GenMob", R);
V.setName("Fixc13pip");
var VS = CMClass.getCommon("FakeSession"); VS.initialize(null, "MEMORY"); V.setSession(VS); VS.setMob(V);
V.setPlayerStats(CMClass.getCommon("DefaultPlayerStats"));
V.baseCharStats().setCurrentClass(CMClass.getCharClass("Fighter"));
V.baseState().setHitPoints(500); V.recoverCharStats(); V.recoverMaxState(); V.resetToMaxState();
var mark = 0;
function out() { var all = s(field(VS, "bout").toString()); var o = all.substring(mark); mark = all.length; return o.replace(/\x1b\[[0-9;]*m/g, "").replace(/\^./g, ""); }
function run(words) { var J = new java.util.ArrayList(); var w = words.split(" "); for (var i = 0; i < w.length; i++) if (w[i].length > 0) J.add(w[i]); out(); CMClass.getCommand("Difficulty").execute(V, J, 0); return out(); }
var was = s(GD.current());
GD.restore(null);
check("ini", s(GD.current()) == "NORMAL", "a new game's: " + GD.current());

// formulas: @xP and @xD, compiled and not
var F = CMath.compileMathExpression("(@xP*10)+@xD");
CMath.setFormulaSide(1);
var v1 = CMath.parseMathExpression(F, [0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0], 0.0);
CMath.setFormulaSide(0);
var v0 = CMath.parseMathExpression(F, [0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0], 0.0);
check("xp-xd", v1 == 11 && v0 == 1 && CMath.parseMathExpression("@xD*2", [0]) == 2, "player " + v1 + " npc " + v0);

// the command
var said = run("DIFFICULTY");
check("show", has(said, "The game is on Normal") && has(said, "Story, Easy, Normal, and Hard"), said);
check("unknown", has(run("DIFFICULTY nightmare"), "There is no difficulty called 'nightmare'"), "an unknown one");
said = run("DIFFICULTY hard");
check("set", s(GD.current()) == "HARD" && has(said, "The game is now on Hard: enemies deal 130% damage and have 120% hit points"), said);
check("xd", CMath.parseMathExpression(F, [0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0], 0.0) == 1.25, "@xD on Hard");

// NPC hit points, at once, and not a follower's
var npc = newMob("GenMob", R);
npc.setName("Fixc13ogre");
npc.baseState().setHitPoints(100); npc.recoverMaxState(); npc.resetToMaxState();
var pet = newMob("GenMob", R);
pet.setName("Fixc13dog");
pet.baseState().setHitPoints(100);
pet["setFollowing(com.planet_ink.coffee_mud.MOBS.interfaces.MOB)"](V);
pet.recoverMaxState(); pet.resetToMaxState();
check("npc-hp", npc.maxState().getHitPoints() == 120 && pet.maxState().getHitPoints() == 100,
	"ogre " + npc.maxState().getHitPoints() + " dog " + pet.maxState().getHitPoints());
run("DIFFICULTY story");
check("npc-hp-follows", npc.maxState().getHitPoints() == 80 && npc.curState().getHitPoints() == 80, "ogre on Story: " + npc.curState().getHitPoints() + "/" + npc.maxState().getHitPoints());

// damage both ways, on Story: NPCs 60%, the player's side 125%
function hit(from, to, n) { var b = to.curState().getHitPoints(); CMLib.combat().postDamage(from, to, null, n, CMMsg.MSG_OK_VISUAL | CMMsg.MASK_ALWAYS, -1, null); return b - to.curState().getHitPoints(); }
var taken = hit(npc, V, 10);
var dealt = hit(V, npc, 10);
var petDealt = hit(pet, npc, 8);
var npcOnNpc = hit(npc, npc, 5);
check("damage", taken == 6 && dealt == 13 && petDealt == 10 && npcOnNpc == 5, "taken " + taken + " dealt " + dealt + " by the dog " + petDealt + " self " + npcOnNpc);
V.makePeace(true); npc.makePeace(true); pet.makePeace(true);

// experience: HARD's 110%
run("DIFFICULTY hard");
var got = CMLib.leveler().postExperience(V, "MISC:", null, "", 100, true);
check("xp", got == 110, "gained " + got);
var npcGot = CMLib.leveler().postExperience(npc, "MISC:", null, "", 100, true);
check("xp-npc", npcGot == 100 || npcGot == 0, "an NPC's: " + npcGot);

// not in a fight
V.setVictim(npc);
check("fight", has(run("DIFFICULTY easy"), "Not in the middle of a fight.") && s(GD.current()) == "HARD", "changed in a fight");
V.makePeace(true); npc.makePeace(true);

// a preset from the ini, and a changed one
CMProps.instance().setProperty("DIFFICULTY_NIGHTMARE", "NPCDAMAGE=200 NPCHP=150 PLAYERDAMAGE=80 PLAYERXP=150 D=2");
said = run("DIFFICULTY nightmare");
check("ini-preset", s(GD.current()) == "NIGHTMARE" && has(said, "enemies deal 200% damage and have 150% hit points") && has(run("DIFFICULTY"), "Hard, and Nightmare"), said);
CMProps.instance().setProperty("DIFFICULTY_NIGHTMARE", "");
// what a save keeps comes back
GD.restore("EASY");
check("restore", s(GD.current()) == "EASY" && GD.inForce().npcHp == 90, "restored " + GD.current());

// the help
check("help", has(CMLib.help().getHelpText("DIFFICULTY", V, false), "STORY"), "no DIFFICULTY help");

GD.restore(was == "NORMAL" ? null : was);
GD.refreshNPCs();
[npc, pet].forEach(function (m) { m.destroy(); });
V.setPlayerStats(null); V.destroy();
R.destroy();
done();
