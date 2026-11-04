FIXFILE="f-factions";
var O=CM.core.Organizations, F=CMClass.getCommon("DefaultFaction"), area=CMClass.getAreaType("StdArea");
F.initializeFaction(new java.lang.StringBuffer('NAME=Fix faction effects\nMINIMUM=0\nMAXIMUM=100\nDEFAULT=0\nSCOREDISPLAY=true\nSHOWINFACTIONSCMD=true\nRATEMODIFIER=1\nRANGE1=0;49;Unknown;FIXORG_UNKNOWN;\nRANGE2=50;100;Trusted;FIXORG_TRUSTED;\nSHOWMASK=-TATTOO +FIXORG_DISCOVERED\nHIDDENNAME=an unknown standing\nPLAYEREFFECT1=Prop_Adjuster;ATTACK+17;FIXORG_TRUSTED\n'),"FIX_ORG_EFFECTS.INI");
CMLib.factions().addFaction(F);
var old=area.getStat("REPUTATION"), base=M.phyStats().attackAdjustment();
try {
 M.addFaction(F.factionID(),10);M.recoverPhyStats();base=M.phyStats().attackAdjustment();
 check("hidden faction",!O.visible(F,M),"undiscovered visible");
 M.addTattoo("FIXORG_DISCOVERED");
 check("discovered faction",O.visible(F,M),"tattoo not read");
 M.addFaction(F.factionID(),60);M.recoverPhyStats();
 check("player effect",M.phyStats().attackAdjustment()==base+17,"attack "+M.phyStats().attackAdjustment()+" expected "+(base+17));
 M.addFaction(F.factionID(),20);M.recoverPhyStats();
 check("band effect removed",M.phyStats().attackAdjustment()==base,"effect remained");
 check("effect not saved",M.fetchEffect("Prop_Adjuster")==null&&M.fetchEffect("Prop_FactionEffects")==null,"faction handlers became stored effects");
 check("band ladder",String(O.factionLadder(F,M)).indexOf("30 points to Trusted")>=0,"wrong ladder");
 var item=CMClass.getItem("GenItem"), zap=CMClass.getAbility("Prop_StockZapper");
 zap.setMiscText('MASK="-TATTOO +FIXORG_BUYER" HIDE=TRUE MSG="Members only."');item.addNonUninvokableEffect(zap);
 var SZ=CM.Abilities.Properties.Prop_StockZapper;
 check("hidden stock",!SZ.visible(item,M)&&!SZ.allowed(item,M),"outsider can buy");
 zap.setMiscText('MASK="-TATTOO +FIXORG_BUYER" HIDE=FALSE');
 check("restricted listing",SZ.visible(item,M)&&!SZ.allowed(item,M),"restricted stock hidden or allowed");
 M.addTattoo("FIXORG_BUYER");
 check("stock unlocked",SZ.allowed(item,M),"buyer still refused");
 var guild=CMClass.getCommon("DefaultClan");guild.setName("Fix Stock Knights");guild.setGovernmentID(0);CMLib.clans().addClan(guild);
 var buyer=newMob("GenMob",M.location());buyer.setClan(guild.clanID(),6);
 try {
  zap.setMiscText("MASK=\"-CLANRANK '+Fix Stock Knights>=MEMBER'\" HIDE=TRUE");
  check("nested rank mask",SZ.allowed(item,buyer)&&!SZ.allowed(item,M),"quoted clan stock rule did not distinguish buyers");
 } finally {buyer.destroy();CMLib.clans().removeClan(guild);}
 item.destroy();
 check("reputation opt in",CMLib.factions().getSpecialAreaFaction(area)==null,"unconfigured area got reputation");
 area.setName("Fix reputation town");area.setStat("REPUTATION","examples/areareaction.ini");
 check("town reputation",CMLib.factions().getSpecialAreaFaction(area)!=null,"configured town got none");
 var xml=String(CMLib.coffeeMaker().getEnvironmentalMiscTextXML(area,true));
 var copy=CMClass.getAreaType("StdArea");copy.setMiscText(xml);
 check("reputation persistence",String(copy.getStat("REPUTATION"))=="examples/areareaction.ini","area setting lost");copy.destroy();
 area.setStat("REPUTATION","NONE");check("reputation disabled",CMLib.factions().getSpecialAreaFaction(area)==null,"NONE ignored");
} finally { M.removeFaction(F.factionID());M.delTattoo("FIXORG_DISCOVERED");M.delTattoo("FIXORG_BUYER");CMLib.factions().removeFaction(F.factionID());area.destroy();M.recoverPhyStats(); }
done();
