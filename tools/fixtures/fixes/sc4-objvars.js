// SC4: variables kept on one object (@$i), not on its name; MPSTATE and
// STATE(); MPSETINTERNAL VARHOST INSTANCE; kept in the object's ExtraData,
// so a save writes them; PERSIST keeps a mob's through its rebirth.
FIXFILE = "sc4-objvars";
var R = M.location();
["FIXSC4A", "FIXSC4B", "FIXSC4C", "FIXSC4D", "FIXSC4E", "FIXSC4F", "FIXSC4G"].forEach(clearVar);
var g1 = newMob("GenMob", R); g1.setName("a fixsc4 guard");
var g2 = newMob("GenMob", R); g2.setName("a fixsc4 guard");
var S1 = scriptOn(g1, "RAND_PROG 100\n"
	+ "mpsetvar @$i BRIBED yes\n"
	+ "mpsetvar $i SHARED 1\n"
	+ "mpsetvar @$i ARROWS 5\n"
	+ "mpsetvar @$i ARROWS -1\n"
	+ "mpsetvar FIXTEST FIXSC4A $<@$i BRIBED>:$<@$i ARROWS>\n"
	+ "mpstate $i open 1\n"
	+ "if state($i open)\n mpsetvar FIXTEST FIXSC4E $%STATE($i open)%\nendif\n"
	+ "mpdisable\n~\n");
tickMob(g1);
runQueued(S1);
var S2 = scriptOn(g2, "RAND_PROG 100\n"
	+ "mpsetvar FIXTEST FIXSC4B [$<@$i BRIBED>]:$<$i SHARED>\n"
	+ "if var(@$i BRIBED == yes)\n mpsetvar FIXTEST FIXSC4C yes\nelse\n mpsetvar FIXTEST FIXSC4C no\nendif\n"
	+ "if state($i open)\n mpsetvar FIXTEST FIXSC4F open\nelse\n mpsetvar FIXTEST FIXSC4F shut\nendif\n"
	+ "mpsetinternal VARHOST INSTANCE\n"
	+ "mpsetvar $i COUNT 7\n"
	+ "mpsetvar FIXTEST FIXSC4D $<$i COUNT>\n"
	+ "~\n");
// g2 runs only its own script
g1.delScript(S1);
tickMob(g2);
runQueued(S2);
check("own", gvar("FIXSC4A") == "yes:4", "FIXSC4A='" + gvar("FIXSC4A") + "'");
check("not-shared", gvar("FIXSC4B") == "[]:1" && gvar("FIXSC4C") == "no", "FIXSC4B='" + gvar("FIXSC4B") + "', FIXSC4C='" + gvar("FIXSC4C") + "'");
check("state", gvar("FIXSC4E") == "1" && gvar("FIXSC4F") == "shut", "FIXSC4E='" + gvar("FIXSC4E") + "', FIXSC4F='" + gvar("FIXSC4F") + "'");
var X1 = g1.fetchEffect("ExtraData");
var kept = (X1 == null) ? "" : ("" + X1.getStat("SCRIPTVARS"));
check("on-object", kept.indexOf("BRIBED") >= 0 && kept.indexOf("OPEN") >= 0 && ("" + CMLib.coffeeMaker().getMobXML(g1)).indexOf("SCRIPTVARS") >= 0, "g1's ExtraData: '" + kept + "'");
var X2 = g2.fetchEffect("ExtraData");
check("instance", gvar("FIXSC4D") == "7" && X2 != null && ("" + X2.getStat("SCRIPTVARS")).indexOf("COUNT") >= 0 && VARS.getVar("a fixsc4 guard", "COUNT") == "", "FIXSC4D='" + gvar("FIXSC4D") + "', by name '" + VARS.getVar("a fixsc4 guard", "COUNT") + "'");
check("instance-saved", ("" + S2.getStateXML()).indexOf("<VARHOST>INSTANCE</VARHOST>") >= 0, "the engine's state: " + S2.getStateXML());

// MPSTATE CLEAR takes it off
var S3 = scriptOn(g1, "RAND_PROG 100\nmpstate $i open clear\nif state($i open)\n mpsetvar FIXTEST FIXSC4G still\nelse\n mpsetvar FIXTEST FIXSC4G gone\nendif\nmpdisable\n~\n");
tickMob(g1);
runQueued(S3);
check("clear", gvar("FIXSC4G") == "gone", "FIXSC4G='" + gvar("FIXSC4G") + "'");

// a mob comes back to life without its values, unless PERSIST is set
var OV = CM.core.ObjectVars;
var r1 = newMob("GenMob", R); r1.setName("a fixsc4 rat");
var r2 = newMob("GenMob", R); r2.setName("a fixsc4 rat");
OV.vars(r1).put("NIBBLES", "3");
OV.vars(r2).put("NIBBLES", "3"); OV.vars(r2).put("PERSIST", "1");
var k1 = OV.persisting(r1), k2 = OV.persisting(r2);
OV.vars(r1).remove("NIBBLES"); OV.vars(r2).remove("NIBBLES"); // as the rebirth takes the effects
OV.reborn(r1, k1); OV.reborn(r2, k2);
check("persist", OV.get(OV.hostOf(r1), "NIBBLES") == "" && OV.get(OV.hostOf(r2), "NIBBLES") == "3", "r1 '" + OV.get(OV.hostOf(r1), "NIBBLES") + "', r2 '" + OV.get(OV.hostOf(r2), "NIBBLES") + "'");

[g1, g2, r1, r2].forEach(function(x) { x.destroy(); });
VARS.setVar("a fixsc4 guard", "SHARED", "");
done();
