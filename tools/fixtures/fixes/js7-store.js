// JS7: cm.store(key), kept in the save's <JSSTATE>; a new game from world/js/state.json; JS STATE.
FIXFILE = "js7-store";
var JST = CM.core.JSStore;
var R = M.location();
function s(x) { return "" + x; }
function has(text, part) { return s(text).indexOf(part) >= 0; }
JST.clear();

var st = cm.store("world");
st.orders = [{who: "Ann", item: "longsword", readyDay: 12}];
st.served = 7;
check("same", cm.store("world") === st && cm.store() === st, "the same object each time");
var smith = newMob("GenMob", R);
smith.setName("a fixjs7 smith");
smith.setStartRoom(R);
cm.store(smith).mood = "busy";
var rid = s(CMLib.map().getExtendedRoomID(R));
check("key-mob", s(cm.storeKey(smith)) == "instance:" + CM.core.InstanceIds.id(smith), s(cm.storeKey(smith)));
check("key-room", s(cm.storeKey(R)) == "room:" + rid && s(cm.storeKey("quest:fixjs7")) == "quest:fixjs7" && s(cm.storeKey(R.getArea())) == "area:" + R.getArea().Name(),
	s(cm.storeKey(R)));
check("stores", cm.stores().contains("world") && cm.stores().size() == 2, s(cm.stores()));

// a save writes them; a restore puts them back, as new objects
var xml = s(CM.core.save.WorldState.capture(M));
check("saved", has(xml, "<JSSTATE>") && has(xml, "longsword") && has(xml, "a fixjs7 smith"), "no JSSTATE in the save");
var tag = CMLib.xml().parseAllXML(xml).get(0).getPieceFromPieces("JSSTATE");
st.served = 99; // after the save
JST.fromXML(tag);
var back = cm.store("world");
check("restored", back !== st && back.served == 7 && back.orders[0].readyDay == 12 && s(back.orders[0].who) == "Ann", JSON.stringify(back));
check("restored-mob", s(cm.store(smith).mood) == "busy", JSON.stringify(cm.store(smith)));
JST.fromXML(null);
check("restored-none", JST.keys().isEmpty(), "a save without stores leaves none");
// what JSON cannot write is not kept
cm.store("fixjs7").f = function() { return 1; };
cm.store("fixjs7").n = 1;
check("data-only", s(JST.json("fixjs7", 0)) == '{"n":1}', s(JST.json("fixjs7", 0)));

// a new game starts from world/js/state.json
var SF = new java.io.File(new java.io.File(WorldFiles.worldDir(), "js"), "state.json");
SF.getParentFile().mkdirs();
java.nio.file.Files.write(SF.toPath(), new java.lang.String('{"world": {"gold": 5}, "quest:fixjs7": {"step": 1}, "bad": 3}').getBytes("UTF-8"));
var why = JST.newGame();
check("new-game", why == null && cm.store().gold == 5 && cm.store("quest:fixjs7").step == 1 && !JST.has("bad") && !JST.has("fixjs7"),
	"why " + why + ", keys " + JST.keys());
SF["delete"]();
JST.newGame();
check("new-game-empty", JST.keys().isEmpty(), s(JST.keys()));

// JS STATE, in builder mode
var B = newMob("GenMob", R);
B.setName("Fixjs7builder");
var BS = CMClass.getCommon("FakeSession");
BS.initialize(null, "MEMORY");
B.setSession(BS);
BS.setMob(B);
var mark = 0;
function out() { var all = "" + field(BS, "bout").toString(); var o = all.substring(mark); mark = all.length; return o; }
function js(words) { var L = new java.util.ArrayList(); L.add("JS"); var w = words.split(" "); for (var i = 0; i < w.length; i++) L.add(w[i]); CMClass.getCommand("JSCmd").execute(B, L, 0); return out(); }
check("state-none", has(js("STATE"), "There are no JavaScript stores."), "no stores yet");
var said = js("STATE world = {gold: 5, who: 'Ann Lee'}");
check("state-set", has(said, "Store 'world' set.") && cm.store().gold == 5 && s(cm.store().who) == "Ann Lee", said);
said = js("STATE");
check("state-list", has(said, 'world: {"gold":5,"who":"Ann Lee"}'), said);
said = js("STATE world");
check("state-show", has(said, "world = {") && has(said, '"gold": 5'), said);
said = js("STATE world = 3");
check("state-bad", has(said, "not set: a store is an object"), said);
said = js("STATE world DELETE");
check("state-delete", has(said, "Store 'world' taken away.") && !JST.has("world"), said);

JST.clear();
smith.destroy(); B.destroy();
done();
