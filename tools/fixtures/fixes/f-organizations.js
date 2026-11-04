FIXFILE="f-organizations";
var O=CM.core.Organizations, Clan=CM.Common.interfaces.Clan;
function list() { var l=new java.util.ArrayList(); for(var i=0;i<arguments.length;i++)l.add(arguments[i]);return l; }
var A=CMClass.getCommon("DefaultClan"), B=CMClass.getCommon("DefaultClan");
A.setName("Fix Guild One"); B.setName("Fix Guild Two"); A.setGovernmentID(0); B.setGovernmentID(0);
CMLib.clans().addClan(A);CMLib.clans().addClan(B);
var R=scratchRoom(), npc=newMob("GenMob",R); npc.setName("Fixorgmember");
var roles=A.getGovernment().getPositions(), low=roles[0], high=roles[0];
for(var i=0;i<roles.length;i++) { if(roles[i].getRank()>low.getRank()) low=roles[i]; if(roles[i].getRank()<high.getRank()) high=roles[i]; }
try {
 npc.setClan(A.clanID(),high.getRoleID());
 check("rank seniority",O.rankTest(npc,A.clanID(),">=",low.getID()) && !O.rankTest(npc,A.clanID(),"<",low.getID()),"rank comparison");
 check("rank mask",CMLib.masking().maskCheck('-CLANRANK "+Fix Guild One>='+low.getID()+'"',npc,true),"mask rejects senior member");
 check("rank mask outsider",!CMLib.masking().maskCheck('-CLANRANK "+Fix Guild Two>='+low.getID()+'"',npc,true),"outsider passed");
 check("position",String(O.memberValue(npc,A,"POSITION"))==String(high.getID()),"position ID");
 check("clans",String(O.clanNames(npc)).indexOf("Fix Guild One")>=0,"missing clan");
 var host=scriptedMob("Fixorgscript", "ONCE_PROG\nmpclanrelation 'Fix Guild One' 'Fix Guild Two' WAR MUTUAL\nmpsetvar FIXTEST FIXORGREL $%CLANRELATION('Fix Guild One' 'Fix Guild Two')%\nif clanrank($i 'Fix Guild One' >= "+low.getID()+")\nmpsetvar FIXTEST FIXORGRANK yes\nendif\nmpsetvar FIXTEST FIXORGPOS $%CLANPOS($i 'Fix Guild One')%\nmpsetvar FIXTEST FIXORGSAFE $%CLANRANK('Fix Guild One')%\n~\n");
 host.setClan(A.clanID(),high.getRoleID()); tickMob(host);
 check("diplomacy script",A.getClanRelations(B.clanID())==Clan.REL_WAR && B.getClanRelations(A.clanID())==Clan.REL_WAR && gvar("FIXORGREL")=="WAR",gvar("FIXORGREL"));
 check("relation helper",CMLib.clans().isCommonClanRelations(A.clanID(),B.clanID(),Clan.REL_WAR),"reads self instead of other clan");
 check("named clan script",gvar("FIXORGRANK")=="yes" && gvar("FIXORGPOS")==String(high.getID()) && gvar("FIXORGSAFE")=="",gvar("FIXORGRANK")+":"+gvar("FIXORGPOS"));
 var alone=scriptedMob("Fixorgunclanned","ONCE_PROG\nif clanrank($i < 0)\nmpsetvar FIXTEST FIXORGNONE yes\nendif\n~\n");tickMob(alone);
 check("legacy unjoined rank",gvar("FIXORGNONE")=="yes",gvar("FIXORGNONE"));alone.destroy();
 host.destroy();
 A.setStat("DUESPERIOD","MONTH"); A.setStat("DUESNEXT","12345"); A.setStat("TREASURY","12.5");
 var copy=CMClass.getCommon("DefaultClan");copy.setDataXML(A.getDataXML());
 check("organisation persistence",String(copy.getStat("DUESPERIOD"))=="MONTH" && String(copy.getStat("DUESNEXT"))=="12345" && String(copy.getStat("TREASURY"))=="12.5" && copy.getClanRelations(B.clanID())==Clan.REL_WAR,"settings lost");
} finally { npc.destroy();R.destroy();CMLib.clans().removeClan(A);CMLib.clans().removeClan(B); }
done();
