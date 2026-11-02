// JS15: JS(expr) and JS$(expr) in conditions and text, MPJSFILE, and cancel() in <SCRIPT>.
FIXFILE = "js15-jsfunc";
var R = M.location();
var W = WorldFiles.worldDir();
function s(x) { return "" + x; }
["FIXJS15A", "FIXJS15B", "FIXJS15C", "FIXJS15D", "FIXJS15F", "FIXJS15T"].forEach(clearVar);
var m = scriptedMob("a fixjs15 sage", "RAND_PROG 100\n"
	+ "IF JS(('' + cm.host().Name()).length > 3)\nMPSETVAR FIXTEST FIXJS15A yes\nENDIF\n"
	+ "IF JS(1 > 2)\nMPSETVAR FIXTEST FIXJS15D wrong\nELSE\nMPSETVAR FIXTEST FIXJS15D right\nENDIF\n"
	+ "MPSETVAR FIXTEST FIXJS15B $%JS$(6 * 7)%\n"
	+ "MPSETVAR FIXTEST FIXJS15C $%JS(host().Name())%\n"
	+ "mpdisable\n~\n");
tickMob(m);
runQueued(m.scripts().nextElement());
check("if", gvar("FIXJS15A") == "yes" && gvar("FIXJS15D") == "right", "FIXJS15A='" + gvar("FIXJS15A") + "', FIXJS15D='" + gvar("FIXJS15D") + "'");
check("value", gvar("FIXJS15B") == "42" && gvar("FIXJS15C") == "a fixjs15 sage", "FIXJS15B='" + gvar("FIXJS15B") + "', FIXJS15C='" + gvar("FIXJS15C") + "'");

// MPJSFILE file args
var F = new java.io.File(W, "js/fixjs15.js");
F.getParentFile().mkdirs();
java.nio.file.Files.write(F.toPath(), new java.lang.String("setVar('FIXTEST', 'FIXJS15F', 'args=' + args + ' host=' + host().Name());\n").getBytes("UTF-8"));
var m2 = scriptedMob("a fixjs15 reader", "RAND_PROG 100\nMPJSFILE world:js/fixjs15.js one two\nmpdisable\n~\n");
tickMob(m2);
runQueued(m2.scripts().nextElement());
check("mpjsfile", gvar("FIXJS15F") == "args=one two host=a fixjs15 reader", "FIXJS15F='" + gvar("FIXJS15F") + "'");

// cancel() in <SCRIPT>: a CNCLMSG_PROG refuses its message
var idol = CMClass.getItem("GenItem");
idol.setName("a fixjs15 idol");
R.addItem(idol);
var S1 = scriptOn(R, "CNCLMSG_PROG GET ALL\n<SCRIPT>\nif (target() != null && target().Name() == 'a fixjs15 idol' && getVar('FIXTEST', 'FIXJS15T') != 'disarmed') { cancel(); }\n</SCRIPT>\n~\n");
var get = CMClass.getMsg(M, idol, null, CMMsg.MSG_GET, null);
check("cancel", !R.okMessage(M, get), "the idol may not be taken while the trap is set");
VARS.setVar("FIXTEST", "FIXJS15T", "disarmed");
check("cancel-not", R.okMessage(M, CMClass.getMsg(M, idol, null, CMMsg.MSG_GET, null)), "the trap disarmed, it may");
R.delScript(S1);
// a last value of "cancel" does the same
var S2 = scriptOn(R, "CNCLMSG_PROG SPEAK ALL\n<SCRIPT>\n'cancel';\n</SCRIPT>\n~\n");
check("cancel-value", !R.okMessage(M, CMClass.getMsg(M, null, null, CMMsg.MSG_SPEAK, "<S-NAME> say(s) 'hi'")), "a script ending in 'cancel'");
R.delScript(S2);

F["delete"]();
m.destroy(); m2.destroy(); idol.destroy();
done();
