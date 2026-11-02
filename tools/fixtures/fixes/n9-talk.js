// N9: TALK [npc] [ABOUT topic]: TALK_PROG [words|ALL], the partner a bare SAY goes to, the fallback to speech,
// the services menu, TALK BYE, and whom a bare TALK means (never the speaker's party).
FIXFILE = "n9-talk";
var SEv = CM.core.ScriptEvents;
var CV = CM.core.Conversations;
var SM = CM.core.ScriptMenus;
function s(x) { return "" + x; }
function has(text, part) { return s(text).replace(/\s+/g, " ").indexOf(part) >= 0; }
var R = scratchRoom();
var V = newMob("GenMob", R);
V.setName("Fixn9pip");
var VS = CMClass.getCommon("FakeSession"); VS.initialize(null, "MEMORY"); V.setSession(VS); VS.setMob(V);
V.setPlayerStats(CMClass.getCommon("DefaultPlayerStats"));
var mark = 0;
function out() { var all = s(field(VS, "bout").toString()); var o = all.substring(mark); mark = all.length; return o.replace(/\x1b\[[0-9;]*m/g, "").replace(/\^./g, ""); }
function run(cmd, words) { var L = new java.util.ArrayList(); var w = words.split(" "); for (var i = 0; i < w.length; i++) if (w[i].length > 0) L.add(w[i]); out(); CMClass.getCommand(cmd).execute(V, L, 0); return out(); }
// two companions, the speaker's party
var comps = [];
for (var i = 0; i < 2; i++) {
	var c = newMob("GenMob", R);
	c.setName("Fixn9companion" + i);
	c["setFollowing(com.planet_ink.coffee_mud.MOBS.interfaces.MOB)"](V);
	comps.push(c);
}
check("tm", SEv.talkFits("mine ore", "the Old MINE!") && SEv.talkFits("P old mine", "the old mine") && !SEv.talkFits("silver", "the old mine")
	&& SEv.talkFits("ALL", "") && !SEv.talkFits("mine", ""), "talkFits");
// only the party here: nobody to talk with
check("party", has(run("Talk", "TALK"), "There is nobody here to talk with."), "the party was picked");

var inn = newMob("GenShopkeeper", R);
inn.setName("Fixn9innkeeper");
scriptOn(inn, "TALK_PROG mine\nmpsetvar FIXTEST FIXN9MINE $n:$g\n~\nTALK_PROG ALL\nmpsetvar FIXTEST FIXN9ALL [$g]\n~\n");
["FIXN9MINE", "FIXN9ALL", "FIXN9HELLO"].forEach(clearVar);
// a bare TALK: the one NPC here outside the party
run("Talk", "TALK");
check("bare", CV.partnerOf(V) == inn && gvar("FIXN9ALL") == "[]" && gvar("FIXN9MINE") == "", "partner " + CV.partnerOf(V) + " ALL '" + gvar("FIXN9ALL") + "'");
// ABOUT a topic
run("Talk", "TALK Fixn9innkeeper ABOUT the old mine");
check("topic", gvar("FIXN9MINE") == "Fixn9pip:the old mine" && gvar("FIXN9ALL") == "[the old mine]", gvar("FIXN9MINE") + " / " + gvar("FIXN9ALL"));
check("st21", s(SEv.lastTopic(inn, "Fixn9pip")) == "the old mine", "LASTTOPIC '" + SEv.lastTopic(inn, "Fixn9pip") + "'");
// a bare SAY goes to the partner, with three in the room
var said = run("Say", "SAY any rooms free?");
// (the fake session renders names as its own: the tag names whom it is said to)
check("say-to", has(said, 'SAY "Fixn9innkeeper"') && has(said, "any rooms free?"), said);

// no TALK_PROG: the speaker says hello to it, for its SPEECH_PROGs
var farmer = newMob("GenMob", R);
farmer.setName("Fixn9farmer");
var FS = scriptOn(farmer, "SPEECH_PROG hello\nmpsetvar FIXTEST FIXN9HELLO $n\n~\n");
said = run("Talk", "TALK Fixn9farmer");
tickMob(farmer); tickMob(farmer); // a SPEECH_PROG reply waits for the NPC's tick
check("hello", has(said, 'SAY "Fixn9farmer"') && has(said, "Hello") && CV.partnerOf(V) == farmer, said);
check("hello-heard", gvar("FIXN9HELLO") == "Fixn9pip", "SPEECH_PROG hello: '" + gvar("FIXN9HELLO") + "'");
check("no-menu", !SM.waiting(V), "a menu for one with no services");
// two NPCs now, no partner: whom?
run("Talk", "TALK BYE");
check("whom", has(run("Talk", "TALK"), "Talk with whom?"), "picked one of two");

// the services menu: a shop without TALK_PROG
var smith = newMob("GenShopkeeper", R);
smith.setName("Fixn9smith");
said = run("Talk", "TALK Fixn9smith");
check("menu", SM.waiting(V) && has(said, "Fixn9smith can help you with:") && has(said, "Trade (LIST, BUY, SELL)") && has(said, "Goodbye"), said);
SM.answer(V, "BYE");
said = out();
check("bye", CV.partnerOf(V) == null && has(said, "You stop talking with Fixn9smith."), said);
// without a partner a bare SAY is to the room
said = run("Say", "SAY just saying");
check("say-room", has(said, "just saying") && has(said, 'SAY "Fixn9pip"'), said);
check("unknown", has(run("Talk", "TALK nosuchfixn9"), "You don't see 'nosuchfixn9' here."), "an unknown name");
check("not-player", has(run("Talk", "TALK Fixn9pip"), "You can TALK with those the game plays"), "talked with a player");

[inn, farmer, smith].concat(comps).forEach(function (m) { m.destroy(); });
V.setPlayerStats(null); V.destroy();
R.destroy();
done();
