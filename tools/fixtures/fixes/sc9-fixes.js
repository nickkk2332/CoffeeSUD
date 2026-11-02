// SC9's review: ISSPEAKING(who COMMON) only when speaking no other language;
// ISRLMONTH() from 1; CANSEE() and CANHEAR() give true or false;
// HASACCTATTOO's name takes $ codes; CLANQUALIFIES can be true;
// CMDFAIL_PROG hears a command alone and the last word after it.
FIXFILE = "sc9-fixes";
var R = M.location();
["FIXSC9A", "FIXSC9B", "FIXSC9C", "FIXSC9D", "FIXSC9E", "FIXSC9F", "FIXSC9G", "FIXSC9H", "FIXSC9TN"].forEach(clearVar);
var C = CMClass.getCommon("DefaultClan");
C.setName("FixSC9Clan");
C.setGovernmentID(0);
CMLib.clans().addClan(C);
var acct = M.playerStats().getAccount();
if (acct != null) acct.addTattoo("FIXSCNINE");
VARS.setVar("FIXTEST", "FIXSC9TN", "FIXSCNINE");
var m = newMob("GenMob", R); m.setName("a fixsc9 sage");
var S = scriptOn(m, "FUNCTION_PROG go\n"
	+ "if isspeaking($i COMMON)\n mpsetvar FIXTEST FIXSC9A common\nendif\n"
	+ "mpspeak $i Elvish\n"
	+ "if isspeaking($i COMMON)\n mpsetvar FIXTEST FIXSC9B wrong\nelse\n mpsetvar FIXTEST FIXSC9B elvish\nendif\n"
	+ "mpsetvar FIXTEST FIXSC9C $%ISRLMONTH()%\n"
	+ "mpsetvar FIXTEST FIXSC9D $%CANSEE($i $n)%\n"
	+ "if cansee($i $n)\n mpsetvar FIXTEST FIXSC9D $<FIXTEST FIXSC9D>:true\nelse\n mpsetvar FIXTEST FIXSC9D $<FIXTEST FIXSC9D>:false\nendif\n"
	+ "if hasacctattoo($n $<FIXTEST FIXSC9TN>)\n mpsetvar FIXTEST FIXSC9E yes\nendif\n"
	+ "if clanqualifies($n FixSC9Clan)\n mpsetvar FIXTEST FIXSC9F yes\nelse\n mpsetvar FIXTEST FIXSC9F no\nendif\n"
	+ "~\n");
S.callFunction(m, "go", "", M);
check("isspeaking", gvar("FIXSC9A") == "common" && gvar("FIXSC9B") == "elvish", "FIXSC9A='" + gvar("FIXSC9A") + "', FIXSC9B='" + gvar("FIXSC9B") + "'");
check("isrlmonth", gvar("FIXSC9C") == "" + (java.util.Calendar.getInstance().get(java.util.Calendar.MONTH) + 1), "FIXSC9C='" + gvar("FIXSC9C") + "'");
// the value is the test's answer, true or false (false after dark)
check("cansee", gvar("FIXSC9D") == "true:true" || gvar("FIXSC9D") == "false:false", "FIXSC9D='" + gvar("FIXSC9D") + "'");
check("hasacctattoo", acct == null || gvar("FIXSC9E") == "yes", "FIXSC9E='" + gvar("FIXSC9E") + "'");
check("clanqualifies", gvar("FIXSC9F") == "yes", "FIXSC9F='" + gvar("FIXSC9F") + "'");
C.addMember(M, C.getGovernment().getAcceptPos());
clearVar("FIXSC9F");
S.callFunction(m, "go", "", M);
check("clanqualifies-member", gvar("FIXSC9F") == "no", "FIXSC9F='" + gvar("FIXSC9F") + "' for a member");
C.delMember(M);
CMLib.clans().removeClan(C);
if (acct != null) acct.delTattoo("FIXSCNINE");

// CMDFAIL_PROG: a command alone, and the last word after it
var SR = scriptOn(R, "CMDFAIL_PROG PRAY ALTAR\nmpsetvar FIXTEST FIXSC9G $g\n~\nCMDFAIL_PROG KNEEL\nmpsetvar FIXTEST FIXSC9H knelt\n~\n");
var cm = CMClass.getMsg(M, null, null, CMMsg.MSG_COMMANDFAIL, null, CMMsg.MSG_COMMANDFAIL, "pray at altar", CMMsg.MSG_COMMANDFAIL, null);
R.okMessage(M, cm);
var km = CMClass.getMsg(M, null, null, CMMsg.MSG_COMMANDFAIL, null, CMMsg.MSG_COMMANDFAIL, "kneel", CMMsg.MSG_COMMANDFAIL, null);
R.okMessage(M, km);
R.delScript(SR);
check("cmdfail", gvar("FIXSC9G").toUpperCase().indexOf("ALTAR") >= 0 && gvar("FIXSC9H") == "knelt", "FIXSC9G='" + gvar("FIXSC9G") + "', FIXSC9H='" + gvar("FIXSC9H") + "'");
m.destroy();
done();
