// S10, in builder mode: a template, a clan with no players, a notice board
// with a post, and a file in the database's file system, all to go to the
// world files at quit
FIXFILE = "s10-build";
var R = M.location();
var guard = CMClass.getMOB("GenMob");
guard.setName("a template guard");
check("template", CMLib.catalog().addNewBuilderTemplateObject(M.Name(), "FIXGUARD", guard), "no template");
var C = CMClass.getCommon("DefaultClan");
C.setName("FixHorde");
C.setPremise("FIXCLAN the horde of the hills");
CMLib.database().DBCreateClan(C);
CMLib.clans().addClan(C);
check("clan", CMLib.clans().getClan("FixHorde") != null, "no clan");
var board = CMClass.getItem("StdJournal");
board.setName("a fix notice board");
board.setDisplayText("A notice board hangs here.");
R.addItem(board);
CMLib.database().DBUpdateItems(R);
CMLib.database().DBWriteJournal("a fix notice board", M.Name(), "ALL", "FIXPOST the old king", "The old king sleeps under the hill.");
check("post", CMLib.database().DBReadJournalMsgsByUpdateDate("a fix notice board", true).size() == 1, "no post");
var F = new CM.core.CMFile("::fixdir/fixvfs.txt", null, CM.core.CMFile.FLAG_FORCEALLOW);
check("vfs", F.saveText("FIXVFS kept in the database"), "the file was not saved");
// F7/F18: generated organisation content survives a fresh world load.
var result=CM.core.Guilds.create("Fix Rose Order","ORDER",CMLib.map().getExtendedRoomID(R));
check("guild generator",String(result).indexOf("Created guild")>=0,String(result));
var order=CMLib.clans().getClan("Fix Rose Order");
if(order!=null) {
 order.setDues(12);order.setStat("DUESNEXT","123456789");order.setStat("TREASURY","35");
 order.setClanRelations(C.clanID(),CM.Common.interfaces.Clan.REL_WAR,CM.core.Organizations.now());order.update();
 check("guild scaffold",order.getSize()==3&&R.fetchInhabitant("guildmaster")!=null&&R.fetchInhabitant("quartermaster")!=null,"missing members");
}
done();
