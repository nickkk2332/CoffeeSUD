// C25: CONSIDER (or CONSIDER ALL): the foes here against the party, in one word; ranks shown; party scaling noted.
FIXFILE = "c25-consider";
var FO = CM.core.FightOdds;
function s(x) { return "" + x; }
function has(t, p) { return s(t).replace(/\s+/g, " ").indexOf(p) >= 0; }
var R = scratchRoom();
function npc(name, lvl) { var m = newMob("GenMob", R); m.setName(name); m.basePhyStats().setLevel(lvl); m.recoverPhyStats(); m.recoverCharStats(); m.recoverMaxState(); m.resetToMaxState(); return m; }
var V = npc("Fixc25pip", 10);
V.setPlayerStats(CMClass.getCommon("DefaultPlayerStats"));
var VS = CMClass.getCommon("FakeSession"); VS.initialize(null, "MEMORY"); V.setSession(VS); VS.setMob(V);
var mark = 0;
function out() { var all = s(field(VS, "bout").toString()); var o = all.substring(mark); mark = all.length; return o.replace(/\x1b\[[0-9;]*m/g, "").replace(/\^./g, ""); }
function consider(line) { out(); CMClass.getCommand("Consider").execute(V, CM.core.CMParms.parse(line), 0); return out(); }
check("nobody", has(consider("consider"), "There is no one here to fight."), "no 'no one'");
var pals = [npc("Fixc25tam", 10), npc("Fixc25bo", 10)];
pals.forEach(function (p) { p["setFollowing(com.planet_ink.coffee_mud.MOBS.interfaces.MOB)"](V); });
var guards = [];
for (var i = 0; i < 4; i++) guards.push(npc("a fixc25 guard", 10));
var sarge = npc("a fixc25 sergeant", 10);
var RK = CMClass.getAbility("Prop_MobRank"); RK.setMiscText("ELITE"); sarge.addNonUninvokableEffect(RK); sarge.recoverPhyStats();
var all = consider("consider");
check("barracks", has(all, "Four fixc25 guards and a fixc25 sergeant [elite]:") && has(all, "for your party of three."), all);
var verdicts = ["trivial", "an easy fight", "a fair fight", "a hard fight", "a very hard fight", "deadly"], one = false;
verdicts.forEach(function (w) { if (has(all, ": " + w + " for")) one = true; });
check("one-word", one, all);
check("all", s(consider("consider all")) == s(all), "CONSIDER ALL differs");
// those fighting the party are the fight
guards[0].setVictim(pals[0]); pals[0].setVictim(guards[0]);
check("hostile", has(consider("consider"), "A fixc25 guard:"), "not just the guard who fights");
guards[0].makePeace(true); pals[0].makePeace(true);
// a party scaler is noted
var PS = CMClass.getAbility("Prop_PartyScaler"); PS.setMiscText("BASE=3 HP=+25%/MEMBER"); sarge.addNonUninvokableEffect(PS);
check("scales", has(consider("consider"), "They scale to your party."), "no scaling note");
check("verdict", s(FO.verdict(0.1)) == "trivial" && s(FO.verdict(1.0)) == "a hard fight" && s(FO.verdict(3.0)) == "deadly", "verdicts");
check("single", has(consider("consider fixc25 sergeant"), "[elite]"), "CONSIDER one still works");
check("help", has(CMLib.help().getHelpText("CONSIDER", M, false), "CONSIDER ALL"), "no help");

[V].concat(pals, guards, [sarge]).forEach(function (m) { m.makePeace(true); if (m == V) m.setPlayerStats(null); m.destroy(); });
R.destroy();
done();
