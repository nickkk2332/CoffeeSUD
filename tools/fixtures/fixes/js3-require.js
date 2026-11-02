// JS3: require() in JRUN and in a script's <SCRIPT>, from world/js/lib, then
// resources/js/lib; compiled once, read again when the file changes.
FIXFILE = "js3-require";
var WF = CM.core.save.WorldFiles;
function write(f, text) { f.getParentFile().mkdirs(); java.nio.file.Files.write(f.toPath(), new java.lang.String(text).getBytes("UTF-8")); }
var F = new java.io.File(new java.io.File(new java.io.File(WF.worldDir(), "js"), "lib"), "fixjs3.js");
write(F, "var hidden = 7;\nexports.greet = function(n) { return 'hello ' + n; };\nexports.version = 1;\n");
var RF = new java.io.File(new java.io.File(new java.io.File("resources", "js"), "lib"), "fixjs3res.js");
write(RF, "exports.where = 'resources';\n");
CM.core.JSScope.reload();
var D = require('fixjs3');
check("jrun", D.greet('ann') == 'hello ann' && D.version == 1 && typeof hidden == 'undefined', "greet " + D.greet('ann') + ", version " + D.version);
check("resources", require('fixjs3res').where == 'resources', "the resources library");
["FIXJS3A", "FIXJS3B"].forEach(clearVar);
var m = scriptedMob("a fixjs3 sage", "RAND_PROG 100\n<SCRIPT>\nsetVar('FIXTEST', 'FIXJS3A', require('fixjs3').greet('bob'));\n</SCRIPT>\nmpdisable\n~\n");
tickMob(m);
runQueued(m.scripts().nextElement());
check("script", gvar("FIXJS3A") == "hello bob", "FIXJS3A='" + gvar("FIXJS3A") + "'");
// changed: the next scope reads it again
write(F, "exports.greet = function(n) { return 'hi ' + n; };\nexports.version = 2;\n");
F.setLastModified(java.lang.System.currentTimeMillis() + 10000);
var m2 = scriptedMob("a fixjs3 scribe", "RAND_PROG 100\n<SCRIPT>\nsetVar('FIXTEST', 'FIXJS3B', '' + require('fixjs3').version);\n</SCRIPT>\nmpdisable\n~\n");
tickMob(m2);
runQueued(m2.scripts().nextElement());
check("changed", gvar("FIXJS3B") == "2", "FIXJS3B='" + gvar("FIXJS3B") + "'");
m.destroy(); m2.destroy();
F["delete"](); RF["delete"]();
done();
