// DT5: a developer's game (DEV=1) gives the DEV group's powers, not a sysop's.
FIXFILE = "dt5-check";
var R = M.location();
check("devmode", CM.core.save.SaveGame.isDevMode(), "not a developer's game");
check("powers", CMSecurity.isAllowed(M, R, CMSecurity.SecFlag.CMDQUESTS) && CMSecurity.isAllowed(M, R, CMSecurity.SecFlag.GOTO),
	"no CMDQUESTS or GOTO");
check("not-sysop", !CMSecurity.isAllowed(M, R, CMSecurity.SecFlag.IMMORT) && !CMSecurity.isASysOp(M), "has IMMORT or is a sysop");
check("class-kept", ("" + M.charStats().getCurrentClass().ID()) != "Archon", "became an Archon");
clearVar("FIXISDEV");
var tester = scriptedMob("a fixes tester", "RAND_PROG 100\nif isdev()\n  mpsetvar FIXTEST FIXISDEV yes\nendif\n~\n");
tickMob(tester);
check("isdev", gvar("FIXISDEV") == "yes", "ISDEV() was not true");
tester.destroy();
done();
