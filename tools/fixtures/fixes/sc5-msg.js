// SC5: CNCLMSG_PROG and EXECMSG_PROG read their message with MSG(); MPMSG
// changes it in CNCLMSG_PROG; EXECMSG_PROG reads a copy; =S|T|O filters take
// * wildcards and MASK=.
FIXFILE = "sc5-msg";
var R = M.location();
// clear weather: rain or snow would weaken the fireball below
var CL = R.getArea().getClimateObj();
CL.setNextWeatherType(CM.Common.interfaces.Climate.WEATHER_CLEAR);
CL.forceWeatherTick(R.getArea());
["FIXSC5A", "FIXSC5B", "FIXSC5C", "FIXSC5D", "FIXSC5E", "FIXSC5F"].forEach(clearVar);
var V = newMob("GenMob", R); V.setName("Fixsc5victim");
// no saving throw against the fireball (its magic save is its save plus its intelligence): the damage is read as sent
var CS = CM.Common.interfaces.CharStats;
[V.baseCharStats(), V.charStats()].forEach(function (c) { c.setStat(CS.STAT_SAVE_SPELLS, 0); c.setStat(CS.STAT_SAVE_MAGIC, -c.getStat(CS.STAT_INTELLIGENCE)); });
var O = newMob("GenMob", R); O.setName("Fixsc5other");
var S1 = scriptOn(R, "CNCLMSG_PROG DAMAGE ALL\n"
	+ "mpsetvar FIXTEST FIXSC5A $%MSG(TYPE)%:$%MSG(VALUE)%:$%MSG(TOOLID)%:$%MSG(SOURCE)%:$%MSG(TARGET)%\n"
	+ "if msg(VALUE > 10)\n mpsetvar FIXTEST FIXSC5B big\nendif\n"
	+ "mpmsg value *0.5\n"
	+ "mpmsg text o <S-NAME> is fixsc5 chilled.\n"
	+ "mpmsg target Fixsc5other\n"
	+ "~\n");
var fire = CMClass.getAbility("Spell_Fireball");
var dm = CMClass.getMsg(M, V, fire, CMMsg.MASK_ALWAYS | CMMsg.TYP_DAMAGE, null);
dm.setValue(20);
var allowed = R.okMessage(M, dm);
R.delScript(S1);
check("read", gvar("FIXSC5A") == "DAMAGE:20:Spell_Fireball:" + M.Name() + ":Fixsc5victim" && gvar("FIXSC5B") == "big", "FIXSC5A='" + gvar("FIXSC5A") + "', FIXSC5B='" + gvar("FIXSC5B") + "'");
check("change", allowed && dm.value() == 10 && ("" + dm.othersMessage()) == "<S-NAME> is fixsc5 chilled." && dm.target() == O, "allowed " + allowed + ", value " + dm.value() + ", others '" + dm.othersMessage() + "', target " + dm.target());

// the filters: * in a stat's value, and MASK=
var part = ("" + M.Name()).substring(1, 3);
var S2 = scriptOn(R, "CNCLMSG_PROG SPEAK =S NAME=*xyzzy*\nmpsetvar FIXTEST FIXSC5C wrong\n~\n"
	+ "CNCLMSG_PROG SPEAK =T MASK=-NAMES +Fixsc5victim\nmpsetvar FIXTEST FIXSC5D masked\n~\n"
	+ "CNCLMSG_PROG SPEAK =S NAME=*" + part + "*\nmpsetvar FIXTEST FIXSC5F wild\n~\n");
var sm = CMClass.getMsg(M, V, null, CMMsg.MSG_SPEAK, null, null, null);
R.okMessage(M, sm);
R.delScript(S2);
check("filters", gvar("FIXSC5C") == "" && gvar("FIXSC5D") == "masked" && gvar("FIXSC5F") == "wild", "FIXSC5C='" + gvar("FIXSC5C") + "', FIXSC5D='" + gvar("FIXSC5D") + "', FIXSC5F='" + gvar("FIXSC5F") + "'");

// EXECMSG_PROG reads a copy: the message changing after does not change it
var S3 = scriptOn(R, "EXECMSG_PROG SPEAK ALL\nmpsetvar FIXTEST FIXSC5E $%MSG(TYPE)%:$%MSG(OTEXT)%\n~\n");
var em = CMClass.getMsg(M, null, null, CMMsg.MSG_SPEAK, null, null, "fixsc5 crime");
R.executeMsg(M, em);
em.setOthersMessage("changed");
runQueued(S3);
R.delScript(S3);
check("exec-copy", gvar("FIXSC5E") == "SPEAK:fixsc5 crime", "FIXSC5E='" + gvar("FIXSC5E") + "'");
V.destroy(); O.destroy();
done();
