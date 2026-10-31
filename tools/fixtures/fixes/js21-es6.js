// JS21: Rhino 1.7.15 with modern JavaScript, in JRUN, in script blocks, and
// in JavaScript classes.
FIXFILE = "js21-es6";
check("rhino-version", ("" + Packages.org.mozilla.javascript.Context.getCurrentContext().getImplementationVersion()).indexOf("1.7.15") >= 0,
	"" + Packages.org.mozilla.javascript.Context.getCurrentContext().getImplementationVersion());

// modern JavaScript in JRUN
let nums = [1, 2, 3];
const twice = x => x * 2;
check("arrow-functions", nums.map(twice).join(",") == "2,4,6", nums.map(twice).join(","));
check("template-strings", `n=${nums.length}` == "n=3", `n=${nums.length}`);
check("includes-find", nums.includes(2) && nums.find(x => x > 1) == 2, "includes/find");
check("object-assign", JSON.stringify(Object.assign({}, {k: 1})) == '{"k":1}', JSON.stringify(Object.assign({}, {k: 1})));

// a template string and a quoted string in a MOBPROG script block
var bard = scriptedMob("a bard", "RAND_PROG 100\n<SCRIPT>\nconst who = 'the bard';\nsetVar('FIXTEST', 'FIXTPL', `sung by ${who}`);\n</SCRIPT>\n~\n");
clearVar("FIXTPL");
tickMob(bard);
check("script-block-template", gvar("FIXTPL") == "sung by the bard", "FIXTPL='" + gvar("FIXTPL") + "'");
bard.destroy();

// a JavaScript class compiles and can be made
var loaded = CM.core.CMClass.loadClass(CM.core.CMClass.CMObjectType.MOB, "/resources/examples/Lemming.js", true);
var lem = CMClass.getMOB("Lemming");
check("js-class", loaded && lem != null, "Lemming.js loaded=" + loaded + " made=" + lem);
if (lem != null) lem.destroy();
done();
