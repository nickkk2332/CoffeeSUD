// I14: Prop_Detector DETECT/DETECTITEM RANGE WHERE EVERY AMBIANCE MSG STOPMSG.
FIXFILE = "i14-detector";
var Dir = CM.core.Directions;
var PS = CM.Common.interfaces.PhyStats;
var W = CM.Items.interfaces.Wearable;
var TPS = Tickable.TICKID_PROPERTY_SPECIAL;
function s(x) { return "" + x; }
function has(t, p) { return s(t).replace(/\s+/g, " ").indexOf(p) >= 0; }
var X = CMClass.getAreaType("StdArea"); X.setName("Fixi14Wood");
X.basePhyStats().setDisposition(X.basePhyStats().disposition() | PS.IS_UNSAVABLE); X.recoverPhyStats();
CMLib.map().addArea(X);
function room(i, name) { var r = CMClass.getLocale("StdRoom"); r.setRoomID("Fixi14Wood#" + i); r.setDisplayText(name); r.setArea(X); return r; }
var A = room(0, "Fixi14 glade"), B = room(1, "Fixi14 path"), C = room(2, "Fixi14 hollow"), D = room(3, "Fixi14 cave");
function link(a, d, b) { a.rawDoors()[d] = b; a.setRawExit(d, CMClass.getExit("Open")); b.rawDoors()[Dir.getOpDirectionCode(d)] = a; b.setRawExit(Dir.getOpDirectionCode(d), CMClass.getExit("Open")); }
link(A, Dir.EAST, B); link(B, Dir.EAST, C); link(C, Dir.EAST, D);
var V = CMClass.getMOB("GenMob"); V.setName("Fixi14pip"); V.bringToLife(A, true); V.setPlayerStats(CMClass.getCommon("DefaultPlayerStats"));
var VS = CMClass.getCommon("FakeSession"); VS.initialize(null, "MEMORY"); V.setSession(VS); VS.setMob(V);
var mark = 0;
function out() { var all = s(field(VS, "bout").toString()); var o = all.substring(mark); mark = all.length; return o.replace(/\x1b\[[0-9;]*m/g, "").replace(/\^./g, ""); }
var blade = CMClass.getItem("GenItem"); blade.setName("an elven fixi14 blade"); blade.setRawLogicalAnd(false); blade.setRawProperLocationBitmap(W.WORN_WIELD);
V.addItem(blade);
var P = CMClass.getAbility("Prop_Detector");
P.setMiscText("DETECT=\"-NAMES +Fixi14orc\" RANGE=2 WHERE=WORN AMBIANCE=\"(glowing blue)\" MSG=\"$p glows faintly blue.\" STOPMSG=\"$p's glow fades.\"");
blade.addNonUninvokableEffect(P);
var orc = CMClass.getMOB("GenMob"); orc.setName("Fixi14orc"); orc.bringToLife(C, true);
out();
P.tick(P, TPS);
var carried = P.sensing();
blade.wearAt(W.WORN_WIELD);
P.tick(P, TPS);
var said = out();
var amb = s(java.util.Arrays.toString(blade.phyStats().ambiances()));
check("worn", (!carried) && P.sensing() && has(said, "glows faintly blue.") && has(amb, "glowing blue"), "carried " + carried + " now " + P.sensing() + " '" + said + "' amb " + amb);
D.bringMobHere(orc, false);
P.tick(P, TPS);
said = out();
check("range", (!P.sensing()) && has(said, "glow fades.") && (!has(s(java.util.Arrays.toString(blade.phyStats().ambiances())), "glowing blue")), "now " + P.sensing() + " '" + said + "'");
// items, ANY, every 2
var rod = CMClass.getItem("GenItem"); rod.setName("a fixi14 dowsing rod"); V.addItem(rod);
var PR = CMClass.getAbility("Prop_Detector"); PR.setMiscText("DETECTITEM=\"-TAGS +FIXI14TREASURE\" EVERY=2 MSG=\"$p twitches.\""); rod.addNonUninvokableEffect(PR);
var chest = CMClass.getItem("GenItem"); chest.setName("a buried fixi14 chest"); chest.addTag("FIXI14TREASURE"); A.addItem(chest);
PR.tick(PR, TPS);
var first = PR.sensing();
PR.tick(PR, TPS);
var second = PR.sensing();
PR.tick(PR, TPS);
check("items", first && second && has(out(), "twitches."), "first " + first + " second " + second);
check("help", has(CMLib.help().getHelpText("PROP_DETECTOR", M, true), "DETECTITEM"), "no help");

V.setPlayerStats(null); V.destroy(); orc.destroy();
CMLib.map().obliterateMapArea(X);
done();
