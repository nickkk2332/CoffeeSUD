FIXFILE="f-party-clan";
var O=CM.core.Organizations, Party=CM.core.Party, P=CMProps.instance(), keys=["CLANVOTES","PARTYMAX","ACTIVECHARMAX","PROFILEMAX"];
var was={};keys.forEach(function(k){was[k]=P.getProperty(k);});
var money=CMLib.beanCounter().getTotalAbsoluteNativeValue(M), companion=null, clan=null;
try {
 P.setProperty("CLANVOTES","PROFILE");P.setProperty("PARTYMAX","0");P.setProperty("ACTIVECHARMAX","0");P.setProperty("PROFILEMAX","0");
 var npc=newMob("GenMob",M.location());npc.setName("Fixorgcompanion");
 var result=CM.core.Recruits.recruit(npc,M,"PARTY","Fixorgcompanion");
 companion=CMLib.players().getLoadPlayer("Fixorgcompanion");
 check("founding companion",result==null&&companion!=null&&Party.isInParty(Party.leaderOf(M),companion),String(result));
 if(companion!=null) {
  var cost=CMProps.getIntVar(CMProps.Int.CLANCOST);CMLib.beanCounter().giveSomeoneMoney(M,cost+100);
  var before=CMLib.beanCounter().getTotalAbsoluteNativeValue(M);
  var founded=CM.core.Guilds.foundParty(M,"Fix Party Company","FELLOWSHIP");clan=CMLib.clans().getClan("Fix Party Company");
  check("party founding",clan!=null&&M.getClanRole(clan.clanID())!=null&&companion.getClanRole(clan.clanID())!=null,String(founded));
  if(clan!=null) {
   check("founding cost once",Math.abs(CMLib.beanCounter().getTotalAbsoluteNativeValue(M)-(before-cost))<0.001,"wrong cost");
   check("profile one voter",clan.getNumVoters(CM.Common.interfaces.Clan.Function.PREMISE)==1,"voters "+clan.getNumVoters(CM.Common.interfaces.Clan.Function.PREMISE));
   var commands=new java.util.ArrayList();commands.add("CLANPREMISE");commands.add("a common purpose");
   check("authorised profile action",CMLib.clans().goForward(M,clan,commands,CM.Common.interfaces.Clan.Function.PREMISE,true),"still waiting for party to vote");
   var stranger=newMob("GenMob",M.location());
   check("no outsider privilege",!CMLib.clans().goForward(stranger,clan,commands,CM.Common.interfaces.Clan.Function.PREMISE,true),"outsider got clan power");stranger.destroy();
   check("party clans view",String(O.partyClans(M)).indexOf("Fix Party Company")>=0&&String(O.partyClans(M)).indexOf("Fixorgcompanion")>=0,"party memberships missing");
  }
 }
} finally {
 if(clan!=null){if(M.getClanRole(clan.clanID())!=null)clan.delMember(M);if(companion!=null&&companion.getClanRole(clan.clanID())!=null)clan.delMember(companion);clan.destroyClan();}
 if(companion!=null&&CMLib.flags().isInTheGame(companion,true))Party.rest(companion);
 CMLib.beanCounter().subtractMoney(M,CMLib.beanCounter().getTotalAbsoluteNativeValue(M)-money);
 keys.forEach(function(k){if(was[k]==null)P.remove(k);else P.setProperty(k,was[k]);});
}
done();
