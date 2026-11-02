// N15: Prop_Affinity: an NPC's opinion of each character, moved by gifts, bribes, talk (once a day), quests won,
// attacks (once an hour), theft and killings; DECAY; bands; AFFINITY() and MPAFFINITY; SHOPFACTOR; REFUSE;
// [AFFINITY >=25] in Prop_NPCLines; a village's on its area.
FIXFILE = "n15-affinity";
var NA = CM.core.NpcAffinity;
var PS = CM.Common.interfaces.PhyStats;
function s(x) { return "" + x; }
function has(text, part) { return s(text).replace(/\s+/g, " ").indexOf(part) >= 0; }
var R = scratchRoom();
var V = newMob("GenMob", R);
V.setName("Fixn15pip");
var VS = CMClass.getCommon("FakeSession"); VS.initialize(null, "MEMORY"); V.setSession(VS); VS.setMob(V);
V.setPlayerStats(CMClass.getCommon("DefaultPlayerStats"));
var mark = 0;
function out() { var all = s(field(VS, "bout").toString()); var o = all.substring(mark); mark = all.length; return o.replace(/\x1b\[[0-9;]*m/g, "").replace(/\^./g, ""); }
["AFFINITY_FIXN15PIP", "AFFINITYAT_FIXN15PIP", "AFFINITYQ_FIXN15PIP_FIXN15_QUEST", "AFFINITYLAST_FIXN15PIP_TALK_ORE", "AFFINITYLAST_FIXN15PIP_ATTACKED"]
	.forEach(function (k) { VARS.setVar("Fixn15smith", k, ""); });
var smith = newMob("GenShopkeeper", R);
smith.setName("Fixn15smith");
var P = CMClass.getAbility("Prop_Affinity");
P.setMiscText("START=0 DECAY=5 GIFT(rare ore)=+10 TALK(ore)=+2 QUESTWIN(Fixn15 Quest)=+30 ATTACKED=-40 STOLE=-30 "
	+ "KILLED(-NAMES +Fixn15villager)=-50 BRIBE=0.5 SHOPFACTOR=FRIENDLY:0.9,DEVOTED:0.8,HOSTILE:1.5 REFUSE=HOSTILE REFUSEMSG=\"Fixn15 get out.\"");
smith.addNonUninvokableEffect(P);
function aff() { return NA.get(smith, "Fixn15pip"); }
check("start", aff() == 0 && s(NA.band(0)) == "NEUTRAL", "start " + aff());
// a gift, a bribe
var ore = CMClass.getItem("GenItem"); ore.setName("a lump of rare ore"); V.addItem(ore);
R.send(V, CMClass.getMsg(V, smith, ore, CMMsg.MSG_GIVE, "<S-NAME> give(s) <O-NAME> to <T-NAMESELF>."));
check("gift", aff() == 10, "after the gift " + aff());
var coins = CMLib.beanCounter().makeCurrency(CMLib.beanCounter().getCurrency(smith), 1.0, 20);
V.addItem(coins);
R.send(V, CMClass.getMsg(V, smith, coins, CMMsg.MSG_GIVE, "<S-NAME> give(s) <O-NAME> to <T-NAMESELF>."));
check("bribe", aff() == 20, "after the bribe " + aff());
// talk: once a game day
function speak(words) { R.send(V, CMClass.getMsg(V, smith, null, CMMsg.MSG_SPEAK, "<S-NAME> say(s) to <T-NAMESELF> '" + words + "'.")); }
speak("tell me of the ore");
speak("more of the ore");
check("talk", aff() == 22, "after talk " + aff());
// a quest won, counted when next read
CM.core.QuestRecords.won("Fixn15 Quest", "Fixn15pip");
check("questwin", aff() == 52 && s(NA.band(aff())) == "FRIENDLY", "after the quest " + aff());
check("shop-friendly", NA.shopFactor(smith, V) == 0.9, "factor " + NA.shopFactor(smith, V));
// a script: MPAFFINITY, AFFINITY(), IF
clearVar("FIXN15A"); clearVar("FIXN15IF");
var clerk = scriptedMob("a fixn15 clerk", "RAND_PROG 100\nmpaffinity Fixn15smith Fixn15pip +30\n"
	+ "mpsetvar FIXTEST FIXN15A $%AFFINITY(Fixn15smith Fixn15pip)%:$%AFFINITY(Fixn15smith Fixn15pip BAND)%\n"
	+ "if affinity(Fixn15smith Fixn15pip > 75)\nmpsetvar FIXTEST FIXN15IF yes\nendif\nmpdisable\n~\n");
clerk.getStartRoom(); R.bringMobHere(clerk, false);
tickMob(clerk);
check("script", gvar("FIXN15A") == "82:DEVOTED" && gvar("FIXN15IF") == "yes", gvar("FIXN15A") + " / " + gvar("FIXN15IF"));
clerk.destroy();
// decay: two game days on, 10 less
var day = CMProps.getMillisPerMudHour() * CMLib.time().globalClock().getHoursInDay();
VARS.setVar("Fixn15smith", "AFFINITYAT_FIXN15PIP", "" + (CM.core.GameTime.now() - 2 * day - 1000));
check("decay", aff() == 72, "after two days " + aff());
// an attack: once an hour
R.send(V, CMClass.getMsg(V, smith, null, CMMsg.MSG_NOISYMOVEMENT | CMMsg.MASK_MALICIOUS, "<S-NAME> shove(s) <T-NAME>."));
R.send(V, CMClass.getMsg(V, smith, null, CMMsg.MSG_NOISYMOVEMENT | CMMsg.MASK_MALICIOUS, "<S-NAME> shove(s) <T-NAME>."));
smith.makePeace(true); V.makePeace(true);
check("attacked", aff() == 32, "after the attack " + aff());
R.send(V, CMClass.getMsg(V, smith, null, CMMsg.MSG_THIEF_ACT, "<S-NAME> pick(s) <T-YOUPOSS> pocket."));
check("stole", aff() == 2 && s(NA.band(aff())) == "NEUTRAL", "after the theft " + aff());
// a villager killed before his eyes
var vill = newMob("GenMob", R);
vill.setName("Fixn15villager");
var k0 = aff();
check("before-kill", k0 == 2, "before the killing " + k0);
R.send(vill, CMClass.getMsg(vill, null, V, CMMsg.MSG_OK_VISUAL, null, CMMsg.MSG_OK_VISUAL, null, CMMsg.MSG_DEATH, "<S-NAME> is DEAD!!!"));
var k1 = aff();
check("killed", k1 == -48 && s(NA.band(k1)) == "COLD", "after the killing " + k1);
// hostile: no trade, dearer
NA.set(smith, "Fixn15pip", -60);
out();
check("refuse", !smith.okMessage(smith, CMClass.getMsg(V, smith, null, CMMsg.MSG_LIST, null)) && has(out(), "Fixn15 get out."), "it traded");
check("shop-hostile", NA.shopFactor(smith, V) == 1.5, "factor " + NA.shopFactor(smith, V));
// its own words by its opinion
var L = CMClass.getAbility("Prop_NPCLines");
L.setMiscText("FIRM=\"Fixn15 for a friend, still firm.\"[AFFINITY >=25] FIRM=\"Fixn15 firm.\"");
smith.addNonUninvokableEffect(L);
out();
CMLib.commands().postSay(smith, V, "I'm afraid my prices are firm.", false, false);
check("lines-cold", has(out(), "Fixn15 firm.") , "the cold line");
NA.set(smith, "Fixn15pip", 30);
CMLib.commands().postSay(smith, V, "I'm afraid my prices are firm.", false, false);
check("lines-friend", has(out(), "Fixn15 for a friend, still firm."), "the friend's line");
// TALK with a TALK_PROG: the topic counts
P.setMiscText(s(P.text()) + " TALK(mine)=+3");
scriptOn(smith, "TALK_PROG ALL\nmpsetvar FIXTEST FIXN15T $g\n~\n");
var Lw = new java.util.ArrayList(); "TALK Fixn15smith ABOUT the mine".split(" ").forEach(function (w) { Lw.add(w); });
CMClass.getCommand("Talk").execute(V, Lw, 0);
check("talk-cmd", aff() == 33, "after TALK " + aff());

// a village: Prop_Affinity on the area, kept under its name
var X = CMClass.getAreaType("StdArea");
X.setName("Fixn15Village");
X.basePhyStats().setDisposition(X.basePhyStats().disposition() | PS.IS_UNSAVABLE);
X.recoverPhyStats();
CMLib.map().addArea(X);
var XR = CMClass.getLocale("StdRoom"); XR.setRoomID("Fixn15Village#0"); XR.setArea(X);
var VA = CMClass.getAbility("Prop_Affinity"); VA.setMiscText("START=5 GIFT(bread)=+4");
X.addNonUninvokableEffect(VA);
VARS.setVar("Fixn15Village", "AFFINITY_FIXN15PIP", "");
var baker = newMob("GenMob", XR); baker.setName("Fixn15baker");
var miller = newMob("GenMob", XR); miller.setName("Fixn15miller");
check("village", NA.get(baker, "Fixn15pip") == 5 && s(NA.holderOf(baker)) == "Fixn15Village", "village " + NA.get(baker, "Fixn15pip"));
XR.bringMobHere(V, false);
var bread = CMClass.getItem("GenItem"); bread.setName("a loaf of bread"); V.addItem(bread);
XR.send(V, CMClass.getMsg(V, baker, bread, CMMsg.MSG_GIVE, "<S-NAME> give(s) <O-NAME> to <T-NAMESELF>."));
check("village-shared", NA.get(miller, "Fixn15pip") == 9, "the miller thinks " + NA.get(miller, "Fixn15pip"));

[smith, vill, baker, miller].forEach(function (m) { if (!m.amDestroyed()) m.destroy(); });
V.setPlayerStats(null); V.destroy();
CMLib.map().obliterateMapArea(X);
R.destroy();
done();
