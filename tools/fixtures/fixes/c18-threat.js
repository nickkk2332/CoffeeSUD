// C18: a light threat table for an NPC without CombatAbilities (twice the damage, three times the healing of its
// victim), CombatAbilities' own aggro as its table; THREAT($i who), THREAT($i who > n), TOPTHREAT, WEAKEST,
// OPPONENT($i 'MASK=...'), MPTHREAT +n/=n/*x.
FIXFILE = "c18-threat";
var TH = CM.core.Threat;
function s(x) { return "" + x; }
var R = scratchRoom();
function npc(name, hp) {
	var m = newMob("GenMob", R);
	m.setName(name);
	m.baseState().setHitPoints(hp); m.recoverMaxState(); m.resetToMaxState();
	return m;
}
var V = npc("Fixc18pip", 100);
V.setPlayerStats(CMClass.getCommon("DefaultPlayerStats"));
var pal = npc("Fixc18pal", 100);
var cleric = npc("Fixc18cleric", 100);
[pal, cleric].forEach(function (m) { m["setFollowing(com.planet_ink.coffee_mud.MOBS.interfaces.MOB)"](V); });
var ogre = npc("Fixc18ogre", 1000);
ogre.setVictim(V); V.setVictim(ogre); pal.setVictim(ogre);
function hit(a, b, n) { CMLib.combat().postDamage(a, b, null, n, CMMsg.MSG_OK_VISUAL | CMMsg.MASK_ALWAYS, -1, null); }
hit(V, ogre, 10);
hit(pal, ogre, 30);
check("light", TH.get(ogre, V) == 20 && TH.get(ogre, pal) == 60 && TH.top(ogre) == pal, "pip " + TH.get(ogre, V) + " pal " + TH.get(ogre, pal));
// healing the one it fights: three times it, to the healer
V.curState().setHitPoints(50);
CMLib.combat().postHealing(cleric, V, null, 10, CMMsg.MSG_OK_VISUAL | CMMsg.MASK_ALWAYS, null);
check("heal", TH.get(ogre, cleric) == 30, "cleric " + TH.get(ogre, cleric));
// the script functions
V.curState().setHitPoints(30);
["FIXC18T", "FIXC18W", "FIXC18N", "FIXC18G", "FIXC18O"].forEach(clearVar);
scriptOn(ogre, "RAND_PROG 100\nmpsetvar FIXTEST FIXC18T $%TOPTHREAT($i)%\nmpsetvar FIXTEST FIXC18W $%WEAKEST($i)%\n"
	+ "mpsetvar FIXTEST FIXC18N $%THREAT($i Fixc18pip)%\nif THREAT($i Fixc18pal > 50)\nmpsetvar FIXTEST FIXC18G yes\nendif\n"
	+ "mpsetvar FIXTEST FIXC18O $%OPPONENT($i 'MASK=-NAMES +Fixc18cleric')%\nmpthreat $i Fixc18pip =500\nmpthreat $i Fixc18pal *2\nmpdisable\n~\n");
tickMob(ogre);
check("functions", gvar("FIXC18T") == "Fixc18pal" && gvar("FIXC18W") == "Fixc18pip" && gvar("FIXC18N") == "20" && gvar("FIXC18G") == "yes",
	"top " + gvar("FIXC18T") + " weakest " + gvar("FIXC18W") + " threat " + gvar("FIXC18N") + " test " + gvar("FIXC18G"));
check("opponent", gvar("FIXC18O") == "Fixc18cleric", "OPPONENT " + gvar("FIXC18O"));
check("mpthreat", TH.get(ogre, V) == 500 && TH.get(ogre, pal) == 120 && TH.top(ogre) == V, "pip " + TH.get(ogre, V) + " pal " + TH.get(ogre, pal));
check("clear-lead", TH.clearLead(ogre) == V, "no clear lead");
// a mob with CombatAbilities: its own aggro is the table
var brute = npc("Fixc18brute", 1000);
var CA = CMClass.getBehavior("CombatAbilities"); brute.addBehavior(CA);
brute.setVictim(pal); pal.setVictim(brute); V.setVictim(brute);
hit(pal, brute, 5);
hit(V, brute, 25);
check("holder", TH.holderOf(brute) != null && TH.get(brute, V) == 50, "brute's threat from pip: " + TH.get(brute, V));
TH.set(brute, V, 300);
// (it turns on one with a clear lead over whom it fights)
check("holder-set", TH.get(brute, V) == 300 && brute.getVictim() == V, "set " + TH.get(brute, V) + ", fights " + brute.getVictim() + " pal " + TH.get(brute, pal));
// the help
check("help", s(CMLib.help().getHelpText("BEHAVIOR_SCRIPTABLE", M, true)).indexOf("TOPTHREAT") >= 0 && s(CMLib.help().getHelpText("BEHAVIOR_SCRIPTABLE", M, true)).indexOf("MPTHREAT") >= 0, "no help");

[V, pal, cleric, ogre, brute].forEach(function (m) { m.makePeace(true); });
[pal, cleric, ogre, brute].forEach(function (m) { m.destroy(); });
V.setPlayerStats(null); V.destroy();
R.destroy();
done();
