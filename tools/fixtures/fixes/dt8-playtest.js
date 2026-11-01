// DT8: PLAYTEST makes the builder a mortal to the game, BUILD runs a command with the powers, and a death returns to the start.
FIXFILE = "dt8-playtest";
var R = M.location();
var cmd = CMClass.getCommand("PlaytestCmd");
var build = CMClass.getCommand("BuildCmd");
var SecFlag = CM.core.CMSecurity.SecFlag;
function bool(x) { return ("" + x) == "true"; }
function words(s) { var L = new java.util.ArrayList(); var w = s.split(" "); for (var i = 0; i < w.length; i++) L.add(w[i]); return L; }
function pt(m, s) { var L = words(s); (("" + L.get(0)) == "BUILD" ? build : cmd).execute(m, L, 0); }
// the game's own Aggressive (CMClass loads its own copy of the class)
var AG = CMClass.getBehavior("Aggressive");
function startFight(monster, victim) { return call(AG, "startFight", [monster, victim, true, false, null]); }

check("archon", bool(CMSecurity.isASysOp(M)) && bool(CMSecurity.isAllowed(M, R, SecFlag.CMDROOMS)), "the builder is not an Archon");
var brute = newMob("GenMob", R);
brute.setName("Fixplaybrute");
check("spared", !bool(startFight(brute, M)), "an aggressive mob attacked the builder");
// outside a playtest, BUILD is the Building skill, not the command
check("build-skill", !bool(build.securityCheck(M)) && !(CMLib.english().findCommand(M, words("BUILD WALL")) instanceof CM.Commands.interfaces.Command), "BUILD is a command outside a playtest");
pt(M, "PLAYTEST ON");
check("on", M.fetchEffect("Prop_Playtest") != null && bool(CMSecurity.isPlaytesting(M)), "PLAYTEST ON did not start one");
check("mortal", !bool(CMSecurity.isASysOp(M)) && !bool(CMSecurity.isAllowed(M, R, SecFlag.CMDROOMS)) && !bool(CMSecurity.isStaff(M)), "the builder kept a power");
check("attacked", bool(startFight(brute, M)), "the aggressive mob spared the playtester");
brute.makePeace(true); M.makePeace(true);
// the prompt says so
var prompt = "" + CMLib.utensils().buildPrompt(M, "<%hhp>");
check("prompt", prompt.indexOf("(playtest)") >= 0, "the prompt is " + prompt);
// a building command is refused, but runs under BUILD
var vars = CMClass.getCommand("Vars");
check("refused", !bool(vars.securityCheck(M)), "VARS is allowed in a playtest");
check("build-command", bool(build.securityCheck(M)), "BUILD is not a command in a playtest");
clearVar("FIXBUILD");
pt(M, "BUILD VARS SET FIXTEST FIXBUILD yes");
check("build", gvar("FIXBUILD") == "yes", "BUILD did not run VARS: '" + gvar("FIXBUILD") + "'");
check("build-after", !bool(CMSecurity.isASysOp(M)), "BUILD left the powers on");
pt(M, "PLAYTEST OFF");
check("off", M.fetchEffect("Prop_Playtest") == null && bool(CMSecurity.isASysOp(M)) && !bool(CMSecurity.isPlaytesting(M)), "PLAYTEST OFF did not give the powers back");
check("prompt-off", ("" + CMLib.utensils().buildPrompt(M, "<%hhp>")).indexOf("(playtest)") < 0, "the prompt still says playtest");
brute.destroy();
clearVar("FIXBUILD");

// a death in a playtest returns to where it began, whole
var B = newMob("GenMob", R);
B.setName("Fixplaytester");
var BS = CMClass.getCommon("FakeSession");
BS.initialize(null, "MEMORY");
B.setSession(BS);
BS.setMob(B);
pt(B, "PLAYTEST ON");
var O = otherRoom();
O.bringMobHere(B, false);
var foe = newMob("GenMob", O);
foe.setName("Fixplayfoe");
CMLib.combat().postDamage(foe, B, null, B.curState().getHitPoints() + 50, CMMsg.MSG_OK_VISUAL | CMMsg.MASK_ALWAYS, -1, null);
check("death", B.location() == R && !bool(B.amDead()) && B.curState().getHitPoints() == B.maxState().getHitPoints(),
	"after the death: in " + (B.location() == null ? "nowhere" : B.location().roomID()) + ", dead " + B.amDead() + ", hp " + B.curState().getHitPoints());
check("death-said", ("" + field(BS, "bout").toString()).indexOf("The playtest takes you back") >= 0, "the playtester was not told");
pt(B, "PLAYTEST OFF");
foe.destroy();
B.setSession(null);
B.destroy();
done();
