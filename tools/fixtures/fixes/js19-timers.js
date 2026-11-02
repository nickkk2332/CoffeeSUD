// JS19: cm.every, cm.at and cm.after on the game's clock; after() kept in the save.
FIXFILE = "js19-timers";
var JE = CM.core.JSEvents, JT = CM.core.JSTimers, GT = CM.core.GameTime;
var TM = CMProps.getTickMillis();
function s(x) { return "" + x; }
JE.clear(); JT.clear();
["FIXJS19A", "FIXJS19R"].forEach(clearVar);

// every N ticks
var n = 0;
var key = cm.every(3, function() { n++; }, "fixjs19every");
JE.tick();
check("every-not-yet", n == 0, "n " + n);
GT.advance(3 * TM);
JE.tick();
check("every", n == 1 && s(key) == "fixjs19every", "n " + n);
GT.advance(3 * TM);
JE.tick();
check("every-again", n == 2, "n " + n);
check("stop", cm.stopTimer("fixjs19every") && !cm.stopTimer("fixjs19every"), "stopTimer");
GT.advance(3 * TM);
JE.tick();
check("stopped", n == 2, "n " + n);

// every N hours, on the game's clock
var h = 0;
cm.every("2 hours", function() { h++; }, "fixjs19hours");
JE.tick();
cm.passTime(2);
JE.tick();
check("every-hours", h == 1, "h " + h);
cm.stopTimer("fixjs19hours");

// at: when the clock comes to it, once
var C = cm.clock();
var next = (C.hour() + 1) % C.hoursInDay();
var at = 0;
cm.at("hour " + next, function() { at++; }, "fixjs19at");
JE.tick();
check("at-not-yet", at == 0, "at " + at);
cm.passTime(1);
JE.tick();
JE.tick();
check("at", at == 1, "at " + at + ", hour " + cm.clock().hour() + " wanted " + next);
cm.stopTimer("fixjs19at");
var bad = "";
try { cm.at("teatime", function() {}); } catch (e) { bad = s(e.message); }
check("at-bad", bad.indexOf("is no time") >= 0, bad);

// after: once, with its data
var got = null;
var id = cm.after(5, function(d) { got = d.x; }, {x: 7});
JE.tick();
check("after-not-yet", got == null && cm.timers().contains("after " + id), "timers " + cm.timers());
GT.advance(5 * TM);
JE.tick();
check("after", got == 7 && !cm.timers().contains("after " + id), "got " + got + ", timers " + cm.timers());

// an after() is kept in the save; restored, it is made again from its text
cm.after(10, function(d) { cm.var("FIXTEST", "FIXJS19R", "r" + d.n); }, {n: 4});
var xml = s(CM.core.save.WorldState.capture(M));
check("saved", xml.indexOf("js:timers") >= 0, "no timers in the save");
CM.core.JSStore.fromXML(CMLib.xml().parseAllXML(xml).get(0).getPieceFromPieces("JSSTATE"));
GT.advance(10 * TM);
JE.tick();
check("restored", gvar("FIXJS19R") == "r4", "FIXJS19R='" + gvar("FIXJS19R") + "'");
JT.clear(); JE.clear();
done();
