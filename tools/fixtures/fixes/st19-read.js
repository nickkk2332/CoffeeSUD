// ST19: READ_PROG on the thing read (only its own reads), its holder, room
// and area; RETURN CANCEL keeps the usual text back.
FIXFILE = "st19-read";
var R = M.location();
// the things glow, so the reads work after dark too
function glow(I) { I.basePhyStats().setDisposition(I.basePhyStats().disposition() | CM.Common.interfaces.PhyStats.IS_GLOWING); I.recoverPhyStats(); return I; }
["FIXST19R", "FIXST19S"].forEach(clearVar);
var D = CMClass.getItem("GenReadable");
if (D == null) D = CMClass.getItem("GenItem");
D.setName("a fixst19 diary");
R.addItem(glow(D));
var SD = scriptOn(D, "READ_PROG ALL\nmpsetvar FIXTEST FIXST19R $n:$o\nreturn cancel\n~\n");
var G = CMClass.getItem("GenItem");
G.setName("a fixst19 sign");
R.addItem(glow(G));
var SR = scriptOn(R, "READ_PROG sign\nmpsetvar FIXTEST FIXST19S $n:$o\n~\n");
var ok1 = R.okMessage(M, CMClass.getMsg(M, D, null, CMMsg.MSG_READ, null));
check("cancel", !ok1 && gvar("FIXST19R") == M.Name() + ":a fixst19 diary", "ok " + ok1 + ", FIXST19R='" + gvar("FIXST19R") + "'");
clearVar("FIXST19R");
var ok2 = R.okMessage(M, CMClass.getMsg(M, G, null, CMMsg.MSG_READ, null));
check("room", ok2 && gvar("FIXST19S") == M.Name() + ":a fixst19 sign", "ok " + ok2 + ", FIXST19S='" + gvar("FIXST19S") + "'");
check("own-only", gvar("FIXST19R") == "", "the diary heard the sign read: FIXST19R='" + gvar("FIXST19R") + "'");
R.delScript(SR);
D.destroy(); G.destroy();
done();
