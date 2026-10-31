// JS1: JavaScript runs without approval by default; with JSCRIPTS=1 a refused
// script stays refused, and an approval made in builder mode is kept in the
// world's jscripts.ini.
FIXFILE = "js1-approval";
check("default-2", CMProps.getIntVar(CMProps.Int.JSCRIPTS) == 2, "JSCRIPTS is " + CMProps.getIntVar(CMProps.Int.JSCRIPTS));
var JB = new java.lang.StringBuffer("var fixesJs1 = 'approval test';");
check("runs-under-2", CMSecurity.isApprovedJScript(JB), "refused under JSCRIPTS=2");
CMProps.setIntVar(CMProps.Int.JSCRIPTS, 1);
try {
	check("refused-first", !CMSecurity.isApprovedJScript(JB), "ran without approval");
	check("refused-again", !CMSecurity.isApprovedJScript(JB), "ran on its second try without approval");
	CMSecurity.approveJScript(M.Name(), JB.toString().hashCode());
	check("approved", CMSecurity.isApprovedJScript(JB), "still refused after approval");
	var f = new java.io.File(WorldFiles.worldDir(), "jscripts.ini");
	var text = f.isFile() ? new java.lang.String(java.nio.file.Files.readAllBytes(f.toPath())) : "";
	check("in-world-file", text.indexOf("" + JB.toString().hashCode() + "=" + M.Name()) >= 0, "world/jscripts.ini: '" + text + "'");
	check("only-approvals", text.indexOf("approval test") < 0, "the file holds a script's text");
} finally {
	CMProps.setIntVar(CMProps.Int.JSCRIPTS, 2);
}
done();
