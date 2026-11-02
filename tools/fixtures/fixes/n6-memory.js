// N6: Memory: what each character did (MET, ATTACKED, GAVE, HEALED, STOLE, KILLEDKIN, TALKED, TRACK=), as script
// variables MEM_FACT_NAME; GREETIF/GREET, REFUSEIF/REFUSE, HOSTILEIF; SHARE=AREA; FORGET.
FIXFILE = "n6-memory";
var R = M.location();
function s(x) { return "" + x; }
function has(text, part) { return s(text).indexOf(part) >= 0; }
var V = newMob("GenMob", R);
V.setName("Fixn6pip");
var VS = CMClass.getCommon("FakeSession"); VS.initialize(null, "MEMORY"); V.setSession(VS); VS.setMob(V);
V.setPlayerStats(CMClass.getCommon("DefaultPlayerStats"));
var mark = 0;
function out() { var all = s(field(VS, "bout").toString()); var o = all.substring(mark); mark = all.length; return o.replace(/\x1b\[[0-9;]*m/g, "").replace(/\^./g, "").replace(/\s+/g, " "); }
function mem(host, fact) { return s(VARS.getVar(host.Name(), "MEM_" + fact + "_Fixn6pip")); }
var keeper = newMob("GenShopkeeper", R);
keeper.setName("Fixn6keeper");
var K = CMClass.getBehavior("Memory");
K.setParms("KIN=\"-NAMES +Fixn6villager\" SHARE=AREA HOSTILEIF=KILLEDKIN>0 REFUSEIF=STOLE>0 REFUSE=\"Fixn6 thief! Out!\" "
	+ "GREETIF=HEALED>0 GREET=\"Fixn6 welcome back, $n.\" FORGET=2d TRACK=BOWED:bows");
keeper.addBehavior(K);
var neighbour = newMob("GenMob", R);
neighbour.setName("Fixn6villager");
var N = CMClass.getBehavior("Memory");
N.setParms("KIN=\"-NAMES +Fixn6villager\"");
neighbour.addBehavior(N);

// met; given; healed; talked; tracked
R.send(V, CMClass.getMsg(V, R, null, CMMsg.MSG_ENTER, null));
check("met", mem(keeper, "MET") == "1" && mem(keeper, "FIRSTMET").length > 0, "MET '" + mem(keeper, "MET") + "' first '" + mem(keeper, "FIRSTMET") + "'");
var coin = CMClass.getItem("GenItem"); coin.setName("a fixn6 ring"); coin.setBaseValue(25); coin.recoverPhyStats(); V.addItem(coin);
R.send(V, CMClass.getMsg(V, keeper, coin, CMMsg.MSG_GIVE, "<S-NAME> give(s) <O-NAME> to <T-NAMESELF>."));
check("gave", mem(keeper, "GAVE") == "25", "GAVE '" + mem(keeper, "GAVE") + "'");
R.send(V, CMClass.getMsg(V, keeper, null, CMMsg.MSG_HEALING, "<S-NAME> heal(s) <T-NAME>."));
check("healed", mem(keeper, "HEALED") == "1", "HEALED '" + mem(keeper, "HEALED") + "'");
R.send(V, CMClass.getMsg(V, keeper, null, CMMsg.MSG_SPEAK, "<S-NAME> say(s) 'Hello.'"));
check("talked", mem(keeper, "TALKED") == "1", "TALKED '" + mem(keeper, "TALKED") + "'");
R.send(V, CMClass.getMsg(V, null, null, CMMsg.MSG_OK_ACTION, "<S-NAME> bows deeply."));
check("track", mem(keeper, "BOWED") == "1", "BOWED '" + mem(keeper, "BOWED") + "'");
// GREETIF: healed, so a welcome when they come back
out();
R.send(V, CMClass.getMsg(V, R, null, CMMsg.MSG_ENTER, null));
check("greet", has(out(), "Fixn6 welcome back, Fixn6pip."), "no welcome");
// a script reads it
var reader = scriptedMob("a fixn6 clerk", "RAND_PROG 100\nmpsetvar FIXTEST FIXN6 $<Fixn6keeper MEM_GAVE_Fixn6pip>\n~\n");
clearVar("FIXN6");
tickMob(reader); reader.destroy();
check("script", gvar("FIXN6") == "25", "script read '" + gvar("FIXN6") + "'");

// stolen from: the shop refuses; and the neighbour knows (SHARE=AREA)
R.send(V, CMClass.getMsg(V, keeper, null, CMMsg.MSG_THIEF_ACT, "<S-NAME> pick(s) <T-YOUPOSS> pocket."));
check("stole", mem(keeper, "STOLE") == "1" && mem(neighbour, "STOLE") == "1", "keeper '" + mem(keeper, "STOLE") + "', neighbour '" + mem(neighbour, "STOLE") + "'");
out();
var list = CMClass.getMsg(V, keeper, null, CMMsg.MSG_LIST, null);
check("refuses", !keeper.okMessage(keeper, list) && has(out(), "Fixn6 thief! Out!"), "the shop did not refuse");
// FORGET=2d: three game days on, it is forgotten
K.setStat("AGE", "" + (3 * CMLib.time().globalClock().getHoursInDay()));
list = CMClass.getMsg(V, keeper, null, CMMsg.MSG_LIST, null);
check("forgets", keeper.okMessage(keeper, list) && mem(keeper, "STOLE") == "", "still refuses, STOLE '" + mem(keeper, "STOLE") + "'");

// a kinsman killed here: hostile on sight
var kinsman = newMob("GenMob", R);
kinsman.setName("Fixn6villager");
R.send(kinsman, CMClass.getMsg(kinsman, null, V, CMMsg.MSG_OK_VISUAL, null, CMMsg.MSG_OK_VISUAL, null, CMMsg.MSG_DEATH, "<S-NAME> is DEAD!!!"));
check("killedkin", mem(keeper, "KILLEDKIN") == "1", "KILLEDKIN '" + mem(keeper, "KILLEDKIN") + "'");
tickMob(keeper);
check("hostile", keeper.isInCombat() && keeper.getVictim() == V, "not attacking");
keeper.makePeace(true); V.makePeace(true);
// ATTACKED counts a fight once, not each blow
var att0 = CMath.s_int(mem(neighbour, "ATTACKED"));
R.send(V, CMClass.getMsg(V, neighbour, null, CMMsg.MSG_NOISYMOVEMENT | CMMsg.MASK_MALICIOUS, "<S-NAME> shove(s) <T-NAME>."));
check("attacked", CMath.s_int(mem(neighbour, "ATTACKED")) == att0 + 1, "ATTACKED '" + mem(neighbour, "ATTACKED") + "'");
neighbour.makePeace(true); V.makePeace(true);

// kept in a save: the state, and back as variables
var state = s(call(K, "getRunState", []));
check("state", has(state, "FACT=\"KILLEDKIN\"") && has(state, "WHO=\"Fixn6pip\""), state.substring(0, Math.min(200, state.length)));

[keeper, neighbour, kinsman].forEach(function (m) { if (!m.amDestroyed()) m.destroy(); });
V.setPlayerStats(null); V.destroy();
done();
