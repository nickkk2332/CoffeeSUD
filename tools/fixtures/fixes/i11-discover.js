// I11: Prop_Discoverable FOUNDBY=examine:x|look:x|search, CHECK/DC/STAT, MSG, HINT, STAYS/RESET; hidden exits not
// passable; SEARCH without a skill; FOUND kept in the text.
FIXFILE = "i11-discover";
var Dir = CM.core.Directions;
var PS = CM.Common.interfaces.PhyStats;
var SE = CM.core.ScriptEvents;
var TPS = Tickable.TICKID_PROPERTY_SPECIAL;
function s(x) { return "" + x; }
function has(t, p) { return s(t).replace(/\s+/g, " ").indexOf(p) >= 0; }
var X = CMClass.getAreaType("StdArea"); X.setName("Fixi11Manor");
X.basePhyStats().setDisposition(X.basePhyStats().disposition() | PS.IS_UNSAVABLE); X.recoverPhyStats();
CMLib.map().addArea(X);
function room(i, name) { var r = CMClass.getLocale("StdRoom"); r.setRoomID("Fixi11Manor#" + i); r.setDisplayText(name); r.setArea(X); return r; }
var LIB = room(0, "Fixi11 library"), SECRET = room(1, "Fixi11 secret study");
var D1 = CMClass.getExit("Open"); D1.setName("a narrow gap");
LIB.rawDoors()[Dir.NORTH] = SECRET; LIB.setRawExit(Dir.NORTH, D1);
SECRET.rawDoors()[Dir.SOUTH] = LIB; SECRET.setRawExit(Dir.SOUTH, CMClass.getExit("Open"));
var V = CMClass.getMOB("GenMob"); V.setName("Fixi11pip"); V.bringToLife(LIB, true); V.setPlayerStats(CMClass.getCommon("DefaultPlayerStats"));
var VS = CMClass.getCommon("FakeSession"); VS.initialize(null, "MEMORY"); V.setSession(VS); VS.setMob(V);
var mark = 0;
function out() { var all = s(field(VS, "bout").toString()); var o = all.substring(mark); mark = all.length; return o.replace(/\x1b\[[0-9;]*m/g, "").replace(/\^./g, ""); }
function cmd(line) { out(); var l = new java.util.ArrayList(); line.split(" ").forEach(function (w) { l.add(w); }); var took = SE.verb(V, l); return { took: took, said: out() }; }
function item(name) { var I = CMClass.getItem("GenItem"); I.setName(name); CMLib.flags().setGettable(I, false); LIB.addItem(I); return I; }
var shelf = item("a dusty bookshelf");
var P = CMClass.getAbility("Prop_Discoverable");
P.setMiscText("FOUNDBY=examine:bookshelf MSG=\"Behind a loose book you find a narrow gap.\" HINT=\"One book sticks out.\"");
D1.addNonUninvokableEffect(P); D1.recoverPhyStats();
var hiddenNow = CMath.bset(D1.phyStats().disposition(), PS.IS_NOT_SEEN);
out();
CMLib.tracking().walk(V, Dir.NORTH, false, false);
var said = out();
check("hidden", hiddenNow && V.location() == LIB && has(said, "You can't go that way."), "not seen " + hiddenNow + " in " + V.location() + " '" + said + "'");
out(); LIB.send(V, CMClass.getMsg(V, shelf, null, CMMsg.MSG_LOOK, null)); said = out();
check("hint", has(said, "One book sticks out.") && (!P.isFound()), "'" + said + "' found " + P.isFound());
out(); LIB.send(V, CMClass.getMsg(V, shelf, null, CMMsg.MSG_EXAMINE, null)); said = out();
check("examine", P.isFound() && has(said, "Behind a loose book") && (!CMath.bset(D1.phyStats().disposition(), PS.IS_NOT_SEEN)), "found " + P.isFound() + " '" + said + "'");
check("kept", has(P.text(), "FOUND=true"), "text " + P.text());
CMLib.tracking().walk(V, Dir.NORTH, false, false);
check("through", V.location() == SECRET, "in " + V.location());
LIB.bringMobHere(V, false);
// SEARCH, no skill: one easy, one too hard; one that hides again
var lever = item("a small brass lever");
var PL = CMClass.getAbility("Prop_Discoverable"); PL.setMiscText("FOUNDBY=search STAYS=false RESET=2"); lever.addNonUninvokableEffect(PL); lever.recoverPhyStats();
var gem = item("a tiny fixi11 gem");
var PG = CMClass.getAbility("Prop_Discoverable"); PG.setMiscText("CHECK=\"INT 60\""); gem.addNonUninvokableEffect(PG); gem.recoverPhyStats();
var r = cmd("search");
check("search", r.took && PL.isFound() && (!PG.isFound()) && has(r.said, "You find"), "took " + r.took + " lever " + PL.isFound() + " gem " + PG.isFound() + " '" + r.said + "'");
r = cmd("search");
check("nothing", r.took && has(r.said, "You find nothing.") && (!PG.isFound()), "'" + r.said + "'");
PL.tick(PL, TPS); PL.tick(PL, TPS);
check("stays", (!PL.isFound()) && CMath.bset(lever.phyStats().disposition(), PS.IS_NOT_SEEN), "found " + PL.isFound());
check("help", has(CMLib.help().getHelpText("PROP_DISCOVERABLE", M, true), "FOUNDBY"), "no help");

V.setPlayerStats(null); V.destroy();
CMLib.map().obliterateMapArea(X);
done();
