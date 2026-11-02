// ST7: VERB_PROG verbs on things within reach win over commands, skills and
// socials; CONTINUE hands the line back; Prop_Verbs without a script; LIST VERBS.
FIXFILE = "st7-verb";
var SEv = CM.core.ScriptEvents;
var R = M.location();
function list(s) { var L = new java.util.ArrayList(); var w = s.split(" "); for (var i = 0; i < w.length; i++) L.add(w[i]); return L; }
["FIXST7W", "FIXST7P", "FIXST7C"].forEach(clearVar);
var W = CMClass.getItem("GenItem");
W.setName("a fixst7 ship's wheel");
R.addItem(W);
var SW = scriptOn(W, "VERB_PROG turn,spin p wheel\nmpsetvar FIXTEST FIXST7W $n:$g\n~\n");
var P = CMClass.getItem("GenItem");
P.setName("a fixst7 pin cushion");
R.addItem(P);
var SP = scriptOn(P, "VERB_PROG poke\nmpsetvar FIXTEST FIXST7C yes\nreturn continue\n~\n");
var SR = scriptOn(R, "VERB_PROG fixpray,fixkneel\nmpsetvar FIXTEST FIXST7P $n\n~\n");
var L = CMClass.getItem("GenItem");
L.setName("a fixst7 lever");
var PV = CMClass.getAbility("Prop_Verbs");
PV.setMiscText("VERBS=\"pull,yank\" MSG=\"You pull the lever.\" OTHERS=\"<S-NAME> pulls the lever.\"");
L.addNonUninvokableEffect(PV);
R.addItem(L);

check("turn-wheel", SEv.verb(M, list("turn wheel")) && gvar("FIXST7W") == M.Name() + ":wheel", "FIXST7W='" + gvar("FIXST7W") + "'");
clearVar("FIXST7W");
check("alias", SEv.verb(M, list("spin the wheel")) && gvar("FIXST7W") == M.Name() + ":the wheel", "FIXST7W='" + gvar("FIXST7W") + "'");
clearVar("FIXST7W");
check("bare-turn", !SEv.verb(M, list("turn")) && gvar("FIXST7W") == "", "a bare TURN was taken");
check("prefix", !SEv.verb(M, list("tur wheel")), "TUR was taken for TURN");
check("room", SEv.verb(M, list("fixkneel")) && gvar("FIXST7P") == M.Name(), "FIXST7P='" + gvar("FIXST7P") + "'");
check("continue", !SEv.verb(M, list("poke cushion")) && gvar("FIXST7C") == "yes", "FIXST7C='" + gvar("FIXST7C") + "'");
check("prop-verbs", SEv.verb(M, list("yank lever")) && !SEv.verb(M, list("yank rope")), "Prop_Verbs");
// through the command queue, as typed
clearVar("FIXST7W");
M.enqueCommand(list("turn wheel"), 0, 0);
check("queued", gvar("FIXST7W") == M.Name() + ":wheel", "FIXST7W='" + gvar("FIXST7W") + "'");
var lv = "" + SEv.listVerbs(R);
check("list-verbs", lv.indexOf("turn,spin p wheel") >= 0 && lv.indexOf("pull,yank (Prop_Verbs)") >= 0 && lv.indexOf("fixpray,fixkneel") >= 0, lv);
R.delScript(SR);
W.destroy(); P.destroy(); L.destroy();
done();
