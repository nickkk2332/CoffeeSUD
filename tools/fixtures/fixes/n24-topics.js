// N24: [[topic]] and [[shown|topic]] in an NPC's spoken lines (SAY and postSay) highlight the words and teach the topic
// to the player characters who hear it, kept in their KNOWN_TOPICS variable; ASK npc ABOUT and TALK npc ABOUT with
// no topic list the known topics the NPC answers to (SPEECH_PROG and TALK_PROG words, its MudChat entries, not
// ALL); HINTTOPICS (a tag, or the world's setting) has an NPC tell its topics the first time a character talks with it.
FIXFILE = "n24-topics";
var NT = CM.core.NpcTopics;
var CMFile = CM.core.CMFile;
var Resources = CM.core.Resources;
function s(x) { return "" + x; }
function has(text, part) { return s(text).replace(/\s+/g, " ").indexOf(part) >= 0; }
function list(jl) { var a = []; for (var i = 0; i < jl.size(); i++) a.push(s(jl.get(i))); return a.join(","); }
// rendering
var L = new java.util.ArrayList();
var shown = NT.render("Folk avoid [[the old mine|mine]] since the [[collapse]].", L);
check("render", s(shown) == "Folk avoid ^Hthe old mine^? since the ^Hcollapse^?." && list(L) == "mine,collapse", shown + " / " + list(L));
check("render-none", s(NT.render("No markup here.", null)) == "No markup here.", "plain line changed");

var R = scratchRoom();
var V = newMob("GenMob", R);
V.setName("Fixn24pip");
var VS = CMClass.getCommon("FakeSession"); VS.initialize(null, "MEMORY"); V.setSession(VS); VS.setMob(V);
V.setPlayerStats(CMClass.getCommon("DefaultPlayerStats"));
var mark = 0;
function out() { var all = s(field(VS, "bout").toString()); var o = all.substring(mark); mark = all.length; return o.replace(/\x1b\[[0-9;]*m/g, "").replace(/\^./g, ""); }
function run(cmd, words) { var J = new java.util.ArrayList(); var w = words.split(" "); for (var i = 0; i < w.length; i++) if (w[i].length > 0) J.add(w[i]); out(); CMClass.getCommand(cmd).execute(V, J, 0); return out(); }
function npcSay(npc, line) { var J = new java.util.ArrayList(); J.add("SAY"); J.add(line); out(); CMClass.getCommand("Say").execute(npc, J, 0); return out(); }
function known() { return list(NT.known(V)); }

// an NPC's SAY teaches the topics to who hears it
var inn = newMob("GenMob", R);
inn.setName("Fixn24innkeeper");
var heard = npcSay(inn, "Folk don't go near [[the old mine|mine]] since the [[collapse]].");
check("say-shown", has(heard, "Folk don't go near the old mine since the collapse.") && !has(heard, "[["), heard);
check("say-learned", known() == "mine,collapse" && has(heard, "(You can ask about mine now.)") && has(heard, "(You can ask about collapse now.)"), known() + " / " + heard);
check("var", s(VARS.getVar("Fixn24pip", "KNOWN_TOPICS")) == "mine|collapse", "KNOWN_TOPICS '" + VARS.getVar("Fixn24pip", "KNOWN_TOPICS") + "'");
heard = npcSay(inn, "The [[mine]] again?");
check("once", known() == "mine,collapse" && !has(heard, "(You can ask about"), heard);
// postSay too (MudChat, scripts, shopkeepers)
out();
CMLib.commands().postSay(inn, null, "Ask about the [[captain]].", false, false);
heard = out();
check("postsay", known() == "mine,collapse,captain" && has(heard, "Ask about the captain.") && !has(heard, "[["), known() + " / " + heard);
// a player's own [[...]] is just words
run("Say", "SAY the [[dragon]] is real");
check("player-no", known() == "mine,collapse,captain", "a player's line taught " + known());

// what the miner answers to: SPEECH_PROG words, TALK_PROG words, not SPEECH_PROG ALL
var miner = newMob("GenMob", R);
miner.setName("Fixn24miner");
scriptOn(miner, "SPEECH_PROG collapse cave-in\nmpsetvar FIXTEST FIXN24COL $n\n~\nTALK_PROG captain\nmpsetvar FIXTEST FIXN24CAP $n\n~\nSPEECH_PROG ALL\nmpsetvar FIXTEST FIXN24ALL $n\n~\n");
check("answers", NT.answers(miner, V, "collapse") && NT.answers(miner, V, "the captain") && !NT.answers(miner, V, "mine"), "answers");
check("askable", list(NT.askable(V, miner)) == "collapse,captain", list(NT.askable(V, miner)));
var asked = run("Say", "ASK Fixn24miner ABOUT");
check("ask-list", has(asked, "You could ask Fixn24miner about: collapse and captain."), asked);
check("ask-quiet", !has(asked, "'ABOUT'") && !has(asked, "ask(s)") && !has(asked, "You ask"), "ASK ABOUT said something: " + asked);
asked = run("Talk", "TALK Fixn24miner ABOUT");
check("talk-list", has(asked, "You could ask Fixn24miner about: collapse and captain."), asked);
check("topics-of", list(NT.topicsOf(miner)) == "collapse,cave-in,captain", list(NT.topicsOf(miner)));
// nobody, or nothing known
check("ask-whom", has(run("Say", "ASK ABOUT"), "You could ask Fixn24miner about"), "ASK ABOUT with the partner");
run("Talk", "TALK BYE");
check("ask-none", has(run("Say", "ASK ABOUT"), "Ask whom?"), "ASK ABOUT with no partner");
check("ask-unknown", has(run("Say", "ASK nosuchfixn24 ABOUT"), "You don't see 'nosuchfixn24' here."), "unknown name");
check("ask-nothing", has(run("Say", "ASK Fixn24innkeeper ABOUT"), "You know of nothing to ask Fixn24innkeeper about."), "nothing to ask");
// ASK npc ABOUT topic still asks
["FIXN24COL", "FIXN24CAP", "FIXN24ALL"].forEach(clearVar);
asked = run("Say", "ASK Fixn24miner ABOUT collapse");
tickMob(miner); tickMob(miner);
check("ask-topic", has(asked.toLowerCase(), "about collapse"), asked);
check("ask-heard", gvar("FIXN24COL") == "Fixn24pip", "SPEECH_PROG collapse: '" + gvar("FIXN24COL") + "' ALL: '" + gvar("FIXN24ALL") + "'");

// MudChat: entries found by the topic's words; an entry for anything does not count
new CMFile(Resources.makeFileResourceName("fixn24.dat"), null, CMFile.FLAG_FORCEALLOW).saveText(
	">smith\n(mine | ore)\n9\"Fixn24 the mine is closed.\n(forge)\n9\"Fixn24 hot work.\n");
Resources.removeResource("MUDCHAT:fixn24.dat");
var smith = newMob("GenMob", R);
smith.setName("Fixn24smith");
var MC = CMClass.getBehavior("MudChat");
MC.setParms("fixn24.dat=smith");
smith.addBehavior(MC);
check("chat-answers", NT.answers(smith, V, "mine") && !NT.answers(smith, V, "captain") && !NT.answers(smith, V, "collapse"), "MudChat answers");
check("chat-topics", list(NT.topicsOf(smith)) == "mine,forge", list(NT.topicsOf(smith)));
// a bare TALK: the hello, and what you could ask
asked = run("Talk", "TALK Fixn24smith");
check("talk-bare", has(asked, "You could ask Fixn24smith about: mine."), asked);
run("Talk", "TALK BYE");

// HINTTOPICS: the first talk with a tagged NPC tells its topics, and teaches them
var guide = newMob("GenMob", R);
guide.setName("Fixn24guide");
guide.addTag("HINTTOPICS");
scriptOn(guide, "SPEECH_PROG ferry\nmpsetvar FIXTEST FIXN24F $n\n~\nTALK_PROG p river crossing\nmpsetvar FIXTEST FIXN24R $n\n~\n");
check("hints", NT.hints(guide) && !NT.hints(miner), "hints()");
asked = run("Talk", "TALK Fixn24guide");
check("hint", has(asked, "Fixn24guide could tell you about: ferry and river crossing."), asked);
check("hint-learned", known() == "mine,collapse,captain,ferry,river crossing", known());
run("Talk", "TALK BYE");
asked = run("Talk", "TALK Fixn24guide");
check("hint-once", !has(asked, "could tell you about"), asked);
run("Talk", "TALK BYE");
check("hint-untagged", !NT.hint(V, miner), "an untagged NPC hinted");

[inn, miner, smith, guide].forEach(function (m) { m.destroy(); });
VARS.setVar("Fixn24pip", "KNOWN_TOPICS", "");
VARS.setVar("Fixn24pip", "TOPICS_HINTED", "");
V.setPlayerStats(null); V.destroy();
R.destroy();
done();
