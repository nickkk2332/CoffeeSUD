// SC14: confirmed MOBPROG bugs.
FIXFILE = "sc14-mobprog";
var R = M.location();

// LEVEL_PROG fills $0..$9 with its trigger's words, not the whole array
var lv = scriptedMob("a level watcher", "LEVEL_PROG 100\nmpsetvar FIXTEST FIXLVL1 $1\n~\n");
clearVar("FIXLVL1");
var msg = CMClass.getMsg(M, null, null, CMMsg.MSG_LEVEL, null);
msg.setValue(M.basePhyStats().level() + 1);
CMLib.map().sendGlobalMessage(M, CMMsg.TYP_LEVEL, msg);
check("level-prog-locals", gvar("FIXLVL1") == "100", "$1='" + gvar("FIXLVL1") + "'");
lv.destroy();

// NUMMOBS with a mask counts the mobs it matches
var census = scriptedMob("a census taker", "RAND_PROG 100\nif nummobs(MASK=-PLAYERS > 0)\nmpsetvar FIXTEST FIXNUM yes\nelse\nmpsetvar FIXTEST FIXNUM no\nendif\n~\n");
clearVar("FIXNUM");
tickMob(census);
check("nummobs-mask", gvar("FIXNUM") == "yes", "FIXNUM='" + gvar("FIXNUM") + "'");
census.destroy();

// MPDAMAGE ... KILL with a damage type as well kills
var victim = newMob("GenMob", R);
victim.setName("fixvictim");
var killer = scriptedMob("a fire mage", "RAND_PROG 100\nmpdamage fixvictim 5000 5000 kill fire\n~\n");
tickMob(killer);
check("mpdamage-kill-type", victim.amDead() || victim.amDestroyed() || victim.curState().getHitPoints() <= 0, "the victim lived with " + victim.curState().getHitPoints() + " hit points");
killer.destroy();
if (!victim.amDestroyed()) victim.destroy();

// INROOM(BEACON) is true for a mob in its start room
var homebody = scriptedMob("a homebody", "RAND_PROG 100\nif inroom(BEACON)\nmpsetvar FIXTEST FIXBEACON yes\nelse\nmpsetvar FIXTEST FIXBEACON no\nendif\n~\n");
homebody.setStartRoom(R);
clearVar("FIXBEACON");
tickMob(homebody);
check("inroom-beacon", gvar("FIXBEACON") == "yes", "FIXBEACON='" + gvar("FIXBEACON") + "'");
homebody.destroy();

// MPMLOAD of a mob in the room copies it without taking the template's home:
// the template keeps its start room, and the copy lives where it was loaded
var home = otherRoom();
var template = newMob("GenMob", R);
template.setName("fixtemplate");
template.setStartRoom(home);
var loader = scriptedMob("a summoner", "RAND_PROG 100\nmpmload fixtemplate\n~\n");
tickMob(loader);
check("mpmload-template-home", template.getStartRoom() == home, "the template's start room became " + template.getStartRoom());
var copies = 0;
for (var i = 0; i < R.numInhabitants(); i++) {
	var x = R.fetchInhabitant(i);
	if (x != template && x.Name() == "fixtemplate") {
		copies++;
		check("mpmload-copy-home", x.getStartRoom() == R, "the copy's start room is " + x.getStartRoom());
		x.destroy(); i--;
	}
}
check("mpmload-copy", copies == 1, copies + " copies");
loader.destroy(); template.destroy();

// the text forms of ISNPC, ISPC, ISIMMORT, EVAL and STRCONTAINS
var texts = scriptedMob("a reckoner", "RAND_PROG 100\nmpsetvar FIXTEST FIXISNPC $%ISNPC($i)%\nmpsetvar FIXTEST FIXISPC $%ISPC($i)%\nmpsetvar FIXTEST FIXIMM $%ISIMMORT($i)%\nmpsetvar FIXTEST FIXEVAL $%EVAL(3 > 2)%\nmpsetvar FIXTEST FIXSTRC $%STRCONTAINS(hello hello)%\nmpsetvar FIXTEST FIXSTRN $%STRCONTAINS(hello world)%\n~\n");
tickMob(texts);
check("text-isnpc", gvar("FIXISNPC") == "true", "ISNPC='" + gvar("FIXISNPC") + "'");
check("text-ispc", gvar("FIXISPC") == "false", "ISPC='" + gvar("FIXISPC") + "'");
check("text-isimmort", gvar("FIXIMM") == "false", "ISIMMORT='" + gvar("FIXIMM") + "'");
check("text-eval", gvar("FIXEVAL") == "true", "EVAL='" + gvar("FIXEVAL") + "'");
check("text-strcontains", gvar("FIXSTRC") == "true", "STRCONTAINS(hello hello)='" + gvar("FIXSTRC") + "'");
check("text-strcontains-not", gvar("FIXSTRN") == "false", "STRCONTAINS(hello world)='" + gvar("FIXSTRN") + "'");
texts.destroy();
// a trigger at 100 percent always fires (the roll is 1 to 100)
var always = scriptedMob("a creature of habit", "RAND_PROG 100\nmpsetvar FIXTEST FIXALWAYS ++\n~\n");
clearVar("FIXALWAYS");
for (var i = 0; i < 300; i++) tickMob(always);
check("percent-100-always", gvar("FIXALWAYS") == "300", "fired " + gvar("FIXALWAYS") + " times in 300");
always.destroy();
done();
