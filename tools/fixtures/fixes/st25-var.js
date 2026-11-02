// ST25: VAR_PROG fires once when a variable's condition comes true.
FIXFILE = "st25-var";
var R2 = otherRoom();
clearVar("FIXST25V");
VARS.setVar("*GLOBAL*", "FIXST25_BRIDGE", "");
var S = scriptOn(R2, "VAR_PROG *GLOBAL* fixst25_bridge == 1\nmpsetvar FIXTEST FIXST25V ++\n~\n");
VARS.setVar("*GLOBAL*", "FIXST25_BRIDGE", "1");
runQueued(S);
check("true", gvar("FIXST25V") == "1", "FIXST25V='" + gvar("FIXST25V") + "'");
VARS.setVar("*GLOBAL*", "FIXST25_BRIDGE", "1");
runQueued(S);
check("stays", gvar("FIXST25V") == "1", "FIXST25V='" + gvar("FIXST25V") + "'");
VARS.setVar("*GLOBAL*", "FIXST25_BRIDGE", "2");
VARS.setVar("*GLOBAL*", "FIXST25_BRIDGE", "1");
runQueued(S);
check("again", gvar("FIXST25V") == "2", "FIXST25V='" + gvar("FIXST25V") + "'");
VARS.setVar("*GLOBAL*", "FIXST25_BRIDGE", "");
R2.delScript(S);
done();
