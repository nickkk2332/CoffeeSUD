// S10, in a new game: the world's template, clan, post and file came with it
FIXFILE = "s10-check";
var list = CMLib.catalog().getBuilderTemplateList("Builder");
var found = false;
for (var i = 0; i < list.size(); i++) if (("" + list.get(i).first).indexOf("FIXGUARD") >= 0) found = true;
check("template", found, list.size() + " templates");
var C = CMLib.clans().getClan("FixHorde");
check("clan", (C != null) && ("" + C.getPremise()).indexOf("FIXCLAN") >= 0, "no clan");
var posts = CMLib.database().DBReadJournalMsgsByUpdateDate("a fix notice board", true);
check("post", (posts != null) && posts.size() == 1 && ("" + posts.get(0).subj()).indexOf("FIXPOST") >= 0, (posts == null) ? "no journal" : posts.size() + " posts");
var F = new CM.core.CMFile("::fixdir/fixvfs.txt", null, CM.core.CMFile.FLAG_FORCEALLOW);
check("vfs", F.exists() && ("" + F.text()).indexOf("FIXVFS") >= 0, "no file");
var order=CMLib.clans().getClan("Fix Rose Order");
check("guild restored",order!=null&&String(order.getGovernment().getName())=="ORDER","missing organisation");
if(order!=null) {
 check("guild politics",String(order.getStat("DUESNEXT"))=="123456789"&&String(order.getStat("TREASURY"))=="35"&&order.getDues()==12&&order.getClanRelations("FixHorde")==CM.Common.interfaces.Clan.REL_WAR,"organisation state lost");
 var hall=CMLib.map().getRoom(order.getRecall()), service=null, gate=false;
 for(var e=hall.inhabitants();e.hasMoreElements();) { var who=e.nextElement();if(who.fetchBehavior("GuildMaster")!=null) service=who; }
 for(var d=0;d<CM.core.Directions.NUM_DIRECTIONS();d++){var room=hall.getRoomInDir(d);if(room!=null&&room.fetchEffect("Prop_ReqEntry")!=null)gate=true;}
 check("guild NPCs and gate",service!=null&&order.getSize()==3&&gate,"scaffold did not reload");
 check("canonical NPC membership",service!=null&&service.getClanRole(order.clanID())!=null&&service.getClanRole(order.clanID()).first==order,"NPC retained an obsolete clan instance");
 var standing=CMLib.factions().getFaction(order.getStat("STANDINGFACTION"));
 check("guild standing file",standing!=null&&String(standing.name()).indexOf("Fix Rose Order")>=0,"standing lost");
}
done();
