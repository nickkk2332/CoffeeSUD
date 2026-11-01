// DT23: TIMESCALE runs the game clock faster or slower than real time.
FIXFILE = "dt23-timescale";
var GT = CM.core.GameTime;
var R = M.location();
function has(text, part) { return ("" + text).indexOf(part) >= 0; }
var B = newMob("GenMob", R);
B.setName("Fixtimer");
var BS = CMClass.getCommon("FakeSession");
BS.initialize(null, "MEMORY");
B.setSession(BS);
BS.setMob(B);
var mark = 0;
function out() { var all = "" + field(BS, "bout").toString(); var s = all.substring(mark); mark = all.length; return s; }
function ts(words) { var L = new java.util.ArrayList(); L.add("TIMESCALE"); if (words.length > 0) L.add(words); CMClass.getCommand("TimeScaleCmd").execute(B, L, 0); return out(); }
function prompt() { return "" + CMLib.utensils().buildPrompt(B, "> "); }
// how many game milliseconds pass in each real one, over a short real wait
function measured() {
	var g0 = GT.now(), r0 = java.lang.System.currentTimeMillis();
	java.lang.Thread.sleep(300);
	var g1 = GT.now(), r1 = java.lang.System.currentTimeMillis();
	return (g1 - g0) / (r1 - r0);
}

// the test game is turn-based, its clock stopped: real time for the clock's checks, put back at the end
var wasTurns = GT.isTurnBased(), wasPaused = GT.isPaused();
if (wasTurns) GT.setTurnBased(false); else if (wasPaused) GT.resume();
var said = ts("");
check("show", has(said, "Time runs at real time (x1)."), said);
check("too-fast", has(ts("50"), "TIMESCALE is from 0.1 to 20.") && GT.rate() == 1, "the rate is " + GT.rate());
check("no-number", has(ts("fast"), "TIMESCALE [0.1 TO 20]"), "a word");
check("prompt-1", !has(prompt(), "(x"), prompt());

said = ts("8");
check("set", GT.rate() == 8 && has(said, "Time runs at x8: 8 game seconds in each real one."), said);
check("prompt-8", has(prompt(), "(x8) "), prompt());
check("real-ms", GT.realMs(8000) == 1000 && GT.realMs(0) == 0, "realMs(8000) is " + GT.realMs(8000));
if (!GT.isStopped()) {
	var ratio = measured();
	check("faster", ratio > 6.5 && ratio < 9.5, "the clock ran x" + ratio);
	// a pause keeps it still, at any rate, and it goes on from where it stopped
	GT.pause();
	var still = GT.now();
	java.lang.Thread.sleep(50);
	check("paused", GT.now() == still, "the paused clock moved " + (GT.now() - still));
	GT.resume();
	check("resumed", GT.now() >= still && GT.now() - still < 2000, "after the pause " + (GT.now() - still));
}

said = ts("0.5");
check("slower", GT.rate() == 0.5 && has(said, "Time runs at x0.5") && has(prompt(), "(x0.5) "), said);
if (!GT.isStopped()) {
	var slow = measured();
	check("slower-clock", slow > 0.3 && slow < 0.7, "the clock ran x" + slow);
}
said = ts("1");
if (wasTurns) GT.setTurnBased(true); else if (wasPaused) GT.pause();
check("mode-back", GT.isTurnBased() == wasTurns && GT.isStopped() == (wasTurns || wasPaused), "the time mode after the test");
check("back", GT.rate() == 1 && has(said, "real time (x1)") && !has(prompt(), "(x"), said);

B.setSession(null);
B.destroy();
done();
