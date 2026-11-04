// Switching is a view change; feign death must keep working without locking the player out.
FIXFILE = "party-controls";
var WR = CM.core.WorldRules, Party = CM.core.Party, P = CMProps.instance();
var keys = ["PARTYMAX", "ACTIVECHARMAX", "PARTYSUMMON", "PARTYSUMMONMASK", "PARTYSUMMONMESSAGE"];
var before = {}; keys.forEach(function(k) { before[k] = P.getProperty(k); });
var R = scratchRoom(), acct = CMClass.getCommon("DefaultPlayerAccount"), made = [];
acct.setAccountName("Fixpartyprofile");
function list() { var l = new java.util.ArrayList(); for(var i=0;i<arguments.length;i++) l.add(arguments[i]); return l; }
function character(name) {
    var m = newMob("GenMob", R); m.setName(name);
    m.setPlayerStats(CMClass.getCommon("DefaultPlayerStats")); m.playerStats().setAccount(acct);
    var S = CMClass.getCommon("PartySession"); S.initialize(null,"0"); S.setMob(m); m.setSession(S); CMLib.sessions().add(S);
    CMLib.players().addPlayer(m); acct.addNewPlayer(m); made.push(m); return m;
}
try {
    var a = character("Fixpartya"), b = character("Fixpartyb"), c = character("Fixpartyc");
    b.setFollowing(a);
    var first = a.session(), second = b.session();
    var feign = CMClass.getAbility("Spell_FeignDeath"); feign.setProficiency(100);
    feign.invoke(b, list(), b, true, 0);
    check("feigning", b.fetchEffect("Spell_FeignDeath") != null, "cast failed");
    var sw = CMClass.getCommand("Switch");
    sw.execute(a, list("SWITCH", b.Name()), 0);
    check("switch to feigned", first.mob() == b && b.session() == first && second.mob() == a, "sessions did not swap");
    check("still feigning", b.fetchEffect("Spell_FeignDeath") != null, "switch removed the effect");
    sw.execute(b, list("SWITCH", a.Name()), 0);
    check("switch back", first.mob() == a && second.mob() == b, "cannot switch back");
    P.setProperty("PARTYMAX", "2");
    check("follow cannot bypass cap", WR.followRefusal(c,a) != null, "third character can FOLLOW around PARTYMAX");
    P.setProperty("ACTIVECHARMAX", "2");
    check("creation respects active cap", WR.newCharRefusal(acct,a) != null, "NEW bypassed active cap");
    check("active cap", WR.partyRefusal(acct,1,2,true) != null && WR.partyRefusal(acct,1,2,false) == null, "active character limit");
    P.setProperty("PARTYSUMMON", "MEET"); Party.setMeetWhereLeft(false);
    check("world meet wins", Party.isMeetWhereLeft() && WR.summonRefusal(a) != null, "preference bypassed world rule");
    CMClass.getCommand("PartyCmd").execute(a,list("PARTY","MEET","OFF"),0);
    check("meet off refused", Party.isMeetWhereLeft(), "player disabled required meeting");
    P.setProperty("PARTYSUMMON", "MASK"); P.setProperty("PARTYSUMMONMASK", "-RACE +Dwarf");
    check("summon mask", WR.summonRefusal(a) != null, "mask did not refuse");
    P.setProperty("PARTYSUMMONMASK", "-RACE +Human");
    a.baseCharStats().setMyRace(CMClass.getRace("Human")); a.recoverCharStats();
    check("summon allowed", WR.summonRefusal(a) == null, "passing mask refused");
    P.setProperty("PARTYSUMMONMASK", "");
    check("empty mask closed", WR.summonRefusal(a) != null, "empty restricted mask allows summoning");
    var help = "" + CMLib.help().getHelpText("PARTY", a, false);
    check("merged party help", help.indexOf("PARTY ADD") >= 0 && help.indexOf("PARTY RULES") >= 0 && help.indexOf("SWITCH") >= 0, help);
    check("world keys", CM.core.save.WorldSettings.isWorldKey("PARTYSUMMON") && CM.core.save.WorldSettings.isWorldKey("ACTIVECHARMAX"), "keys rejected");
} finally {
    keys.forEach(function(k) { if(before[k] == null) P.remove(k); else P.setProperty(k,before[k]); });
    Party.setMeetWhereLeft(false);
    made.forEach(function(m) { var f=m.fetchEffect("Spell_FeignDeath"); if(f != null) f.unInvoke(); CMLib.sessions().remove(m.session()); CMLib.players().delPlayer(m); m.setPlayerStats(null); m.destroy(); });
    R.destroy();
}
done();
