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
done();
