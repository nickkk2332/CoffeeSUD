// Ordinary service NPCs consume only speech they can perceive and understand.
FIXFILE="ar-services";
var r=scratchRoom(),p=newMob("GenMob",r),n=newMob("GenMob",r),guild=CMClass.getCommon("DefaultClan"),Lang=CM.core.Languages;
p.setName("Arserviceplayer");p.setPlayerStats(CMClass.getCommon("DefaultPlayerStats"));guild.setName("ArServiceGuild");guild.setGovernmentID(0);CMLib.clans().addClan(guild);
function speech(tool,words){return CMClass.getMsg(p,n,tool,CMMsg.MSG_SPEAK,"<S-NAME> say(s) '"+words+"'.");}
try {
 var mono=CMClass.getAbility("Prop_Monolingual");mono.setMiscText("LANGUAGES=Orcish");n.addNonUninvokableEffect(mono);
 var gm=CMClass.getBehavior("GuildMaster");gm.setParms('CLAN=ArServiceGuild RANKS=SCRIPT');n.addBehavior(gm);
 var msg=speech(null,"join");gm.executeMsg(n,msg);
 check("uncomprehended join refused",p.getClanRole(guild.clanID())==null,"Common bypassed Orcish service");
 var common=CMClass.getAbility("Elvish");common.setProficiency(1);n.addAbility(common);msg=speech(common,"join");
 gm.executeMsg(n,msg);check("service acts only on understood words",String(Lang.speechText(n,msg))!="join"&&p.getClanRole(guild.clanID())==null,"clear command supplied at partial proficiency");
 n.delAbility(common);n.delEffect(mono);var senses=n.phyStats().sensesMask();n.phyStats().setSensesMask(senses|CM.Common.interfaces.PhyStats.CAN_NOT_HEAR);
 msg=speech(null,"join");gm.executeMsg(n,msg);check("deaf service ignores audio",p.getClanRole(guild.clanID())==null&&Lang.speechText(n,msg)==null,"deaf service acted");
 var sign=CMClass.getAbility("SignLanguage");sign.setProficiency(100);n.addAbility(sign);msg=speech(sign,"join");
 check("deaf service can see signs",String(Lang.speechText(n,msg))=="join","visual language used hearing");
 n.phyStats().setSensesMask(senses|CM.Common.interfaces.PhyStats.CAN_NOT_SEE);check("blind service misses signs",Lang.speechText(n,msg)==null,"visual language bypassed sight");
 n.phyStats().setSensesMask(senses);gm.executeMsg(n,speech(null,"join"));check("understood join works",p.getClanRole(guild.clanID())!=null,"comprehensible service broken");
} finally {if(p.getClanRole(guild.clanID())!=null)guild.delMember(p);CMLib.clans().removeClan(guild);p.setPlayerStats(null);p.destroy();n.destroy();r.destroy();}
done();
