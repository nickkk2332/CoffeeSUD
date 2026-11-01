// B13: AHELP BUILDING tells the workflow, and builder mode's login ends with a summary.
FIXFILE = "b13-help";
function has(text, part) { return ("" + text).replace(/\s+/g, " ").indexOf(part) >= 0; }
var help = "" + CMLib.help().getHelpText("BUILDING", M, true);
check("ahelp", has(help, "SAVE ROOM") && has(help, "LIST UNSAVED") && has(help, "WORLD STATUS") && has(help, "RUNFILE"), help.substring(0, 300));
check("alias", has("" + CMLib.help().getHelpText("BUILDERMODE", M, true), "SAVE ROOM"), "BUILDERMODE is no topic");
var lines = "" + CM.core.save.SaveGame.builderSummary();
check("summary", has(lines, "AHELP BUILDING tells how building works here"), lines);
CM.core.Unsaved.mark(M.location(), "TEST");
lines = "" + CM.core.save.SaveGame.builderSummary();
check("summary-unsaved", has(lines, "1 rooms have unsaved mobs or items: LIST UNSAVED lists them."), lines);
CM.core.Unsaved.clear();
done();
