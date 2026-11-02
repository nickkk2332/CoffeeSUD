// SC2: MPSETVAR ... NOW, ELAPSED(), DATETIME(STAMP|DAYOFYEAR|WEEKDAY),
// GAMETIME(unit), WEEKDAY() and TICKS().
FIXFILE = "sc2-time";
var SEv = CM.core.ScriptEvents;
var TC = M.location().getArea().getTimeObj();
["FIXSC2S", "FIXSC2E", "FIXSC2T", "FIXSC2W", "FIXSC2G", "FIXSC2K", "FIXSC2D"].forEach(clearVar);
var m = scriptedMob("a fixsc2 smith", "RAND_PROG 100\nmpsetvar FIXTEST FIXSC2S NOW\nmpsetvar FIXTEST FIXSC2W $%WEEKDAY()%:$%DATETIME(WEEKDAY)%\nmpsetvar FIXTEST FIXSC2G $%GAMETIME(DAYS)%\nmpsetvar FIXTEST FIXSC2K $%TICKS(1 HOURS)%\nmpsetvar FIXTEST FIXSC2D $%DATETIME(STAMP)%\n~\n");
tickMob(m);
runQueued(m.scripts().nextElement());
check("now", gvar("FIXSC2S") == "" + SEv.clockHours(TC) && gvar("FIXSC2D") == gvar("FIXSC2S"), "FIXSC2S='" + gvar("FIXSC2S") + "', stamp " + gvar("FIXSC2D"));
check("weekday", gvar("FIXSC2W") == SEv.weekday(TC) + ":" + SEv.weekday(TC), "FIXSC2W='" + gvar("FIXSC2W") + "'");
check("gametime", gvar("FIXSC2G") == "" + Math.floor(SEv.clockHours(TC) / TC.getHoursInDay()), "FIXSC2G='" + gvar("FIXSC2G") + "'");
check("ticks", CMath.s_int(gvar("FIXSC2K")) == Math.floor(CMProps.getMillisPerMudHour() / CMProps.getTickMillis()), "FIXSC2K='" + gvar("FIXSC2K") + "'");
var stamp = gvar("FIXSC2S");
TC.tickTock(TC.getHoursInDay() * 3);
var e = scriptedMob("a fixsc2 waiter", "RAND_PROG 100\nmpsetvar FIXTEST FIXSC2E $%ELAPSED(" + stamp + " DAYS)%\nif elapsed(" + stamp + " DAYS >= 3)\n  mpsetvar FIXTEST FIXSC2T yes\nendif\n~\n");
tickMob(e);
runQueued(e.scripts().nextElement());
check("elapsed", gvar("FIXSC2E") == "3" && gvar("FIXSC2T") == "yes", "FIXSC2E='" + gvar("FIXSC2E") + "', FIXSC2T='" + gvar("FIXSC2T") + "'");
TC.tickTock(-TC.getHoursInDay() * 3);
m.destroy(); e.destroy();
done();
