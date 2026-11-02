// JS13: Math.random is the game's dice; cm.ticks() and cm.realNow().
FIXFILE = "js13-random";
var RND = CMLib.dice().getRandomizer();
function s(x) { return "" + x; }
RND.setSeed(42);
var a = [Math.random(), Math.random(), Math.random()];
RND.setSeed(42);
var b = [RND.nextDouble(), RND.nextDouble(), RND.nextDouble()];
check("dice", a[0] == b[0] && a[1] == b[1] && a[2] == b[2] && a[0] >= 0 && a[0] < 1, "Math.random " + a + " / dice " + b);
// a <SCRIPT> too: it seeds the dice itself, as the game's own ticks may roll in between
clearVar("FIXJS13S");
var m = scriptedMob("a fixjs13 gambler", "RAND_PROG 100\n<SCRIPT>\nvar r = Packages.com.planet_ink.coffee_mud.core.CMLib.dice().getRandomizer();\n"
	+ "r.setSeed(9);\nvar a = Math.random();\nr.setSeed(9);\nvar b = r.nextDouble();\nsetVar('FIXTEST', 'FIXJS13S', '' + (a == b));\n</SCRIPT>\nmpdisable\n~\n");
tickMob(m);
runQueued(m.scripts().nextElement());
check("script", gvar("FIXJS13S") == "true", "FIXJS13S='" + gvar("FIXJS13S") + "'");
// the clock
var tm = CMProps.getTickMillis();
check("ticks", cm.ticks() == Math.floor(cm.now() / tm) && sud.time.ticks() == cm.ticks(), "ticks " + cm.ticks() + ", now " + cm.now() + ", tick " + tm);
check("realNow", Math.abs(cm.realNow() - java.lang.System.currentTimeMillis()) < 5000, "realNow " + cm.realNow());
RND.setSeed(java.lang.System.nanoTime());
m.destroy();
done();
