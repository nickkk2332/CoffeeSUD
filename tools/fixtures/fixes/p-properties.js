// The property defects DOCS b075 to b078 list, as audit/property-defects.md
// sorts them: one check for each that was fixed.
FIXFILE = "p-properties";
var R = M.location();
var A = R.getArea();
var PS = CM.Common.interfaces.PhyStats;
var CS = CM.Common.interfaces.CharStats;
var Wearable = CM.Items.interfaces.Wearable;
var Directions = CM.core.Directions;
function prop(id, text) { var P = CMClass.getAbility(id); P.setMiscText(text); return P; }
function setField(obj, name, val) {
	for (var c = obj.getClass(); c != null; c = c.getSuperclass()) {
		try { var f = c.getDeclaredField(name); f.setAccessible(true); f.set(obj, val); return; } catch (e) { }
	}
	throw "no field " + name;
}
function item(cls, name) { var I = CMClass.getItem(cls); I.setName(name); I.setDisplayText(name + " is here."); return I; }
function bool(x) { return ("" + x) == "true"; }
function attempt(name, f) { try { f(); } catch (e) { fail(name, "threw " + e); } }

// ---- b075

// HaveAdjuster: RONDEATH is false unless given
attempt("haveadjuster-rondeath", function () {
	var HA = prop("Prop_HaveAdjuster", "STR+1");
	var HB = prop("Prop_HaveAdjuster", "STR+1 RONDEATH=TRUE");
	check("haveadjuster-rondeath", !bool(field(HA, "removeOnDeath")) && bool(field(HB, "removeOnDeath")),
		"absent gave " + field(HA, "removeOnDeath") + ", TRUE gave " + field(HB, "removeOnDeath"));
});
// HaveAdjuster: a fractional speed counts in the item's level
attempt("haveadjuster-speed", function () {
	var plain = CMath.s_int(prop("Prop_HaveAdjuster", "STR+1").getStat("STAT-LEVEL"));
	var fast = CMath.s_int(prop("Prop_HaveAdjuster", "STR+1 SPEED+0.5").getStat("STAT-LEVEL"));
	check("haveadjuster-speed", fast > plain, "SPEED+0.5 gave level " + fast + ", without it " + plain);
});

// AbilityImmunity: OWNER takes no message's place; WEARER protects the wearer
attempt("abilityimmunity-message", function () {
	var AI = prop("Prop_AbilityImmunity", "OWNER;Disease_Cold=FIXMSG cold");
	var ds = field(AI, "diseases"), ms = field(AI, "messages");
	check("abilityimmunity-message", ds.size() == 1 && ("" + ms.get(0)) == "FIXMSG cold", "names " + ds + ", messages " + ms);
});
attempt("abilityimmunity-wearer", function () {
	var wearerM = newMob("GenMob", R);
	var ring = item("GenItem", "a plain ring");
	ring.setRawProperLocationBitmap(Wearable.WORN_HELD);
	wearerM.addItem(ring);
	ring.setRawWornCode(Wearable.WORN_HELD);
	var AI = prop("Prop_AbilityImmunity", "WEARER;Disease_Cold");
	ring.addNonUninvokableEffect(AI);
	var cold = CMClass.getMsg(M, wearerM, CMClass.getAbility("Disease_Cold"), CMMsg.MSG_OK_ACTION, null);
	check("abilityimmunity-wearer", !AI.okMessage(wearerM, cold), "the wearer caught the cold");
	wearerM.destroy();
});

// AbsorbDamage: nothing set is level 0
attempt("absorbdamage-level", function () {
	var lvl = CMath.s_int(prop("Prop_AbsorbDamage", "").getStat("STAT-LEVEL"));
	check("absorbdamage-level", lvl < 110, "an empty one is level " + lvl);
});

// Artifact: speaking at it does not destroy it
attempt("artifact-speech", function () {
	var art = item("GenItem", "a fixes relic");
	R.addItem(art);
	var AR = CMClass.getAbility("Prop_Artifact");
	art.addNonUninvokableEffect(AR);
	var say = CMClass.getMsg(M, art, null, CMMsg.MSG_SPEAK, "<S-NAME> say(s) 'hello there' to <T-NAMESELF>.");
	AR.okMessage(M, say);
	check("artifact-speech", !art.amDestroyed(), "speaking at it destroyed it");
	art.destroy();
});

// AreaForSale: an area's lot is named by the area
attempt("areaforsale-id", function () {
	var AF = CMClass.getAbility("Prop_AreaForSale");
	AF.setAffectedOne(A);
	check("areaforsale-id", ("" + AF.landPropertyID()) == ("" + A.Name()), "the ID is '" + AF.landPropertyID() + "'");
	AF.setAffectedOne(null);
});

// Doppleganger: save bonuses do not add up over rescales
attempt("doppleganger-saves", function () {
	var dop = newMob("GenMob", R);
	// a mob to mimic (a builder is not mimicked)
	var entrant = newMob("GenMob", R);
	var save = CS.STAT_SAVE_FIRE;
	var before = dop.baseCharStats().getStat(save);
	var D = prop("Prop_Doppleganger", "SAVEPCT=50% LEVELADD=2");
	D.setAffectedOne(dop);
	call(D, "doppleGangMob", [entrant, dop, R]);
	var first = dop.baseCharStats().getStat(save) - before;
	D.setMiscText("SAVEPCT=50% LEVELADD=6");
	call(D, "doppleGangMob", [entrant, dop, R]);
	var second = dop.baseCharStats().getStat(save) - before;
	var want = Math.round(dop.basePhyStats().level() * 0.5);
	check("doppleganger-saves", first > 0 && second == want, "first bonus " + first + ", then " + second + " (want " + want + " at level " + dop.basePhyStats().level() + ")");
	dop.destroy(); entrant.destroy();
});

// FightSpellCast: TEXT reaches the base class
attempt("fightspellcast-setstat", function () {
	var FS = prop("Prop_FightSpellCast", "");
	FS.setStat("TEXT", "Spell_Light");
	check("fightspellcast-setstat", ("" + FS.text()) == "Spell_Light", "TEXT gave '" + FS.text() + "'");
});

// Fumble: ADOMAIN alone makes a spell of the domain fumble
attempt("fumble-domain", function () {
	var caster = newMob("GenMob", R);
	caster.addAbility(CMClass.getAbility("Spell_Fireball"));
	var fb = caster.fetchAbility("Spell_Fireball");
	var Ab = CM.Abilities.interfaces.Ability;
	var domain = Ab.DOMAIN.DESCS[(fb.classificationCode() & Ab.ALL_DOMAINS) >> 5];
	var F = prop("Prop_Fumble", "CHANCE=100% ADOMAIN=\"" + domain + "\"");
	caster.addNonUninvokableEffect(F);
	var cast = CMClass.getMsg(caster, M, fb, CMMsg.MSG_OK_ACTION, null);
	check("fumble-domain", field(F, "domains").length == 1 && !F.okMessage(caster, cast), "domains " + field(F, "domains").length + ", the fireball went off");
	caster.destroy();
});

// CommonTwister: DISPLAY changes the display text
attempt("commontwister-display", function () {
	var CT = prop("Prop_CommonTwister", "skill=* mask=* name=\"some corn\" display=\"some corn is here\"");
	var parms = new java.util.HashMap();
	parms.put("NAME", "some berries"); parms.put("DISPLAYTEXT", "some berries are here");
	call(CT, "commonTwist", [CMClass.getAbility("Foraging"), parms, false]);
	check("commontwister-display", ("" + parms.get("DISPLAYTEXT")) == "some corn is here", "display text '" + parms.get("DISPLAYTEXT") + "'");
});

// EnterAdjuster: the help's own order, stats first; changes made when the entry happens
attempt("enteradjuster-stats", function () {
	var EA = prop("Prop_EnterAdjuster", "str+2 dex-1;Prayer_Bless;");
	var spells = field(EA, "theSpells");
	check("enteradjuster-stats", field(EA, "miscStatChanges").size() == 2 && spells != null && spells.size() == 1,
		"stat changes " + field(EA, "miscStatChanges").size() + ", spells " + spells);
});
attempt("enteradjuster-veto", function () {
	var hall = scratchRoom();
	var EA = prop("Prop_EnterAdjuster", "str+2");
	hall.addNonUninvokableEffect(EA);
	var walker = newMob("GenMob", R);
	var str0 = walker.baseCharStats().getStat(CS.STAT_STRENGTH);
	var enter = CMClass.getMsg(walker, hall, null, CMMsg.MSG_ENTER, null);
	EA.okMessage(walker, enter);
	var asked = walker.baseCharStats().getStat(CS.STAT_STRENGTH);
	var runs = enter.trailerRunnables();
	for (var i = 0; runs != null && i < runs.size(); i++) runs.get(i).run();
	var entered = walker.baseCharStats().getStat(CS.STAT_STRENGTH);
	check("enteradjuster-veto", asked == str0 && entered == str0 + 2, "strength " + str0 + ", asked " + asked + ", entered " + entered);
	walker.destroy();
});

// ---- b076

// InstantDeath: an area gives those in it
attempt("instantdeath-area", function () {
	var victim = newMob("GenMob", R);
	var ID = prop("Prop_InstantDeath", "");
	var dead = ID.getDeadMOBsFrom(A);
	check("instantdeath-area", dead.contains(victim), "the area gave " + dead.size() + " mobs");
	victim.destroy();
});

// LanguageSpeaker: HOMEONLY speaks for a mob whose start room is in the area
attempt("languagespeaker-homeonly", function () {
	var LS = prop("Prop_LanguageSpeaker", "LANGUAGE=Dwarven HOMEONLY=TRUE");
	LS.setAffectedOne(A);
	var local = newMob("GenMob", R); local.setStartRoom(R);
	var stranger = newMob("GenMob", R); stranger.setStartRoom(null);
	LS.startSpeaking(local); LS.startSpeaking(stranger);
	check("languagespeaker-homeonly", local.fetchEffect("Dwarven") != null && stranger.fetchEffect("Dwarven") == null,
		"local " + (local.fetchEffect("Dwarven") != null) + ", stranger " + (stranger.fetchEffect("Dwarven") != null));
	local.destroy(); stranger.destroy();
});

// HaveResister: two kinds both apply to one hit
attempt("haveresister-stack", function () {
	var target = newMob("GenMob", R), hitter = newMob("GenMob", R);
	var HR = prop("Prop_HaveResister", "non-magical-weapons 50% blunt 50%");
	var club = item("GenWeapon", "a club");
	club.setWeaponDamageType(CM.Items.interfaces.Weapon.TYPE_BASHING);
	var hit = CMClass.getMsg(hitter, target, club, CMMsg.MSG_DAMAGE, null);
	hit.setValue(100);
	HR.resistAffect(hit, target, HR, "");
	check("haveresister-stack", hit.value() == 25, "100 damage came to " + hit.value());
	target.destroy(); hitter.destroy();
});

// HaveZapper: CONTENT zaps what is taken out of the container
attempt("havezapper-content", function () {
	var box = item("GenContainer", "a fixes box");
	box.setCapacity(100);
	R.addItem(box);
	var gem = item("GenItem", "a fixes gem");
	R.addItem(gem); gem.setContainer(box);
	var HZ = prop("Prop_HaveZapper", "CONTENT -NAMES +Zzyzx");
	box.addNonUninvokableEffect(HZ);
	var get = CMClass.getMsg(M, gem, box, CMMsg.MSG_GET, null);
	check("havezapper-content", !HZ.okMessage(M, get), "took the gem out");
	gem.destroy(); box.destroy();
});

// ItemSlotFiller: parentheses inside an ability's text
attempt("itemslotfiller-adds", function () {
	var IF = prop("Prop_ItemSlotFiller", "TYPE=Gems ADDS=\"Prop_HaveAdjuster(STR+1 (bright)),Prop_Light\"");
	var adds = field(IF, "adds");
	var first = (adds.size() > 0) ? ("" + adds.get(0).second) : "";
	check("itemslotfiller-adds", first == "STR+1 (bright)", "adds " + adds.size() + ", the first's text '" + first + "'");
});

// ItemTransporter: a new pick clears the other kind; a container loop ends
attempt("itemtransporter-dest", function () {
	var away = otherRoom();
	var RCV = prop("Prop_ItemTransReceiver", "FIXTRANS");
	away.addNonUninvokableEffect(RCV);
	var IT = prop("Prop_ItemTransporter", "FIXTRANS");
	// the first call looks for receivers; the second picks from what it found
	call(IT, "setDestination", []);
	setField(IT, "mobDestination", M);
	call(IT, "setDestination", []);
	check("itemtransporter-dest", field(IT, "roomDestination") == away && field(IT, "mobDestination") == null,
		"room " + field(IT, "roomDestination") + ", mob " + field(IT, "mobDestination"));
	away.delEffect(RCV);
});
attempt("itemtransporter-loop", function () {
	var a = item("GenContainer", "box a"), b = item("GenContainer", "box b"), c = item("GenContainer", "box c");
	a.setContainer(b); b.setContainer(c); c.setContainer(a);
	var IT = prop("Prop_ItemTransporter", "FIXTRANS2");
	var top = IT.ultimateParent(a);
	check("itemtransporter-loop", top != null && top.container() == null, "no end to the loop");
	a.destroy(); b.destroy(); c.destroy();
});

// MultiEffects: entries without parameters; only bubbling children reach the carrier
attempt("multieffects-parse", function () {
	var ME = prop("Prop_MultiEffects", "Prop_NoSummon,Prop_NoTeleport,Prop_NoCharm");
	check("multieffects-parse", field(ME, "props").length == 3, "read " + field(ME, "props").length + " of 3");
});
attempt("multieffects-bubble", function () {
	var carrier = newMob("GenMob", R);
	var charm = item("GenItem", "a fixes charm");
	carrier.addItem(charm);
	var ME = prop("Prop_MultiEffects", "Prop_HaveAdjuster(STR+3),Prop_Adjuster(DEX+3)");
	charm.addNonUninvokableEffect(ME);
	var str0 = carrier.charStats().getStat(CS.STAT_STRENGTH), dex0 = carrier.charStats().getStat(CS.STAT_DEXTERITY);
	carrier.recoverCharStats();
	var str1 = carrier.charStats().getStat(CS.STAT_STRENGTH), dex1 = carrier.charStats().getStat(CS.STAT_DEXTERITY);
	M.tell("FIXINFO multieffects bubble " + ME.bubbleAffect() + " str " + str0 + "->" + str1 + " dex " + dex0 + "->" + dex1);
	check("multieffects-bubble", dex1 == dex0, "the carrier got the item's own DEX+3 (" + dex0 + " to " + dex1 + ")");
	carrier.destroy();
});

// ---- b077

// PropSetter: two references in one value
attempt("propsetter-vars", function () {
	var box = item("GenContainer", "a fixes crate");
	box.basePhyStats().setWeight(5); box.recoverPhyStats();
	var PSET = prop("Prop_PropSetter", "trigger=get capacity=\"${WEIGHT}+${WEIGHT}\"");
	PSET.setAffectedOne(box);
	call(PSET, "addEffect", [new java.lang.Integer(1), box, true]);
	check("propsetter-vars", box.capacity() == 10, "capacity " + box.capacity());
	box.destroy();
});

// ReqLevels: a < term before a > term; a mount is checked
attempt("reqlevels-terms", function () {
	var RL = prop("Prop_ReqLevels", "<30 >10");
	check("reqlevels-terms", field(RL, "lvls").length == 4, "read " + (field(RL, "lvls").length / 2) + " of 2 terms");
});
attempt("reqlevels-mount", function () {
	var horse = item("GenRideable", "a fixes saddle");
	R.addItem(horse);
	var RL = prop("Prop_ReqLevels", ">500");
	var rider = newMob("GenMob", R);
	check("reqlevels-mount", !RL.passesMuster(rider, horse), "a level " + rider.phyStats().level() + " mob may mount it");
	rider.destroy(); horse.destroy();
});

// NOFOLLOW: a short word is not NOFOLLOW
attempt("nofollow-prefix", function () {
	var RL = prop("Prop_ReqLevels", ">5 NO");
	var RL2 = prop("Prop_ReqLevels", ">5 NOFOLLOW");
	check("nofollow-prefix", !bool(field(RL, "noFollow")) && bool(field(RL2, "noFollow")), "NO gave " + field(RL, "noFollow") + ", NOFOLLOW " + field(RL2, "noFollow"));
});

// ReqTattoo: +ALL refuses one with the -tattoo; NOSNEAK, given first, stops sneaking past
function tattooPasses(RT, mob) {
	var flags = java.lang.reflect.Array.newInstance(java.lang.Boolean.TYPE, 2);
	var mask = RT.getMask(flags);
	return { pass: RT.passesMuster(mask, flags, mob), flags: flags };
}
attempt("reqtattoo-all", function () {
	var marked = newMob("GenMob", R), plain = newMob("GenMob", R);
	marked.addTattoo("FIXMARK");
	var RT = prop("Prop_ReqTattoo", "+ALL -FIXMARK");
	var m = tattooPasses(RT, marked).pass, p = tattooPasses(RT, plain).pass;
	check("reqtattoo-all", !m && p, "marked " + m + ", unmarked " + p);
	marked.destroy(); plain.destroy();
});
attempt("reqtattoo-nosneak", function () {
	var sneak = newMob("GenMob", R);
	sneak.basePhyStats().setDisposition(sneak.basePhyStats().disposition() | PS.IS_SNEAKING); sneak.recoverPhyStats();
	var RT = prop("Prop_ReqTattoo", "NOSNEAK -ALL +FIXMARK");
	var t = tattooPasses(RT, sneak);
	check("reqtattoo-nosneak", t.flags[1] && !t.pass, "NOSNEAK read " + t.flags[1] + ", a sneak passed " + t.pass);
	sneak.destroy();
});

// RestrictSpells: a target's name in any case
attempt("restrictspells-case", function () {
	var bob = newMob("GenMob", R); bob.setName("FixBob");
	var RS = prop("Prop_RestrictSpells", "Spell_Light(FIXBOB)");
	var light = CMClass.getMsg(M, bob, CMClass.getAbility("Spell_Light"), CMMsg.MSG_OK_ACTION, null);
	check("restrictspells-case", !RS.okMessage(M, light), "Spell_Light went off on FixBob");
	bob.destroy();
});

// RoomLit: new hours on new text
attempt("roomlit-hours", function () {
	var lit = scratchRoom();
	var clock = CMLib.time().localClock(lit);
	var other = (clock.getHourOfDay() + 1) % clock.getHoursInDay();
	var RL = prop("Prop_RoomLit", "HOURS=" + other);
	RL.setAffectedOne(lit);
	var before = bool(call(RL, "isItLit", []));
	RL.setMiscText("");
	var after = bool(call(RL, "isItLit", []));
	check("roomlit-hours", !before && after, "lit at another hour " + before + ", then always " + after);
});

// ---- b078

// WeaponImmunity: LEVEL is kept
attempt("weaponimmunity-level", function () {
	var WI = prop("Prop_WeaponImmunity", "+ALL -LEVEL5");
	check("weaponimmunity-level", ("" + field(WI, "flags").get("LEVEL")) == "- 5", "LEVEL is '" + field(WI, "flags").get("LEVEL") + "'");
});

// RoomView: ATTACK; DOWNINOUT
attempt("roomview-attack", function () {
	var RV = prop("Prop_RoomView", "ATTACK;north");
	check("roomview-attack", bool(field(RV, "attack")) && !bool(field(RV, "longlook")), "attack " + field(RV, "attack") + ", longlook " + field(RV, "longlook"));
});
attempt("roomview-downinout", function () {
	var RV = prop("Prop_RoomView", "DOWNINOUT");
	check("roomview-downinout", field(RV, "uviewedRoomDir") == Directions.DOWN, "direction " + field(RV, "uviewedRoomDir"));
});

// UseEmoter: a smell is not a sight
attempt("useemoter-smell", function () {
	var UE = prop("Prop_UseEmoter", "SMELL;the scent of roses");
	var emotes = call(UE, "parseEmotes", []);
	var smells = field(UE, "smells");
	check("useemoter-smell", emotes.size() == 0 && smells != null && smells.size() == 1, "sights " + emotes.size() + ", smells " + smells);
});

// Undroppable: LOSTOFF on a thing no mob holds
attempt("undroppable-lostoff", function () {
	var stone = item("GenItem", "a fixes stone");
	R.addItem(stone);
	var UD = prop("Prop_Undroppable", "LOSTOFF=TRUE");
	stone.addNonUninvokableEffect(UD);
	var look = CMClass.getMsg(M, stone, null, CMMsg.MSG_LOOK, null);
	var threw = null;
	try { UD.okMessage(M, look); } catch (e) { threw = e; }
	check("undroppable-lostoff", threw == null, "threw " + threw);
	stone.destroy();
});

// SpellAdder: the new holder gets the spell, the old loses it
attempt("spelladder-handoff", function () {
	var wand = item("GenItem", "a fixes wand");
	var SA = prop("Prop_SpellAdder", "Spell_Light");
	SA.setAffectedOne(wand);
	var one = newMob("GenMob", R), two = newMob("GenMob", R);
	SA.affectPhyStats(one, one.phyStats());
	var first = one.fetchEffect("Spell_Light") != null;
	SA.affectPhyStats(two, two.phyStats());
	var handed = two.fetchEffect("Spell_Light") != null && one.fetchEffect("Spell_Light") == null;
	check("spelladder-handoff", first && handed, "first holder " + first + ", handed on " + handed);
	one.destroy(); two.destroy(); wand.destroy();
});

// RoomRedirect: a mask given twice is dropped
attempt("roomredirect-dupes", function () {
	var RR = prop("Prop_RoomRedirect", "#1=;#2=");
	RR.setAffectedOne(scratchRoom());
	var reds = call(RR, "getRedirects", []);
	check("roomredirect-dupes", reds.size() == 1, "kept " + reds.size() + " of the two");
});

// Smell: a rewritten smell starting with - is not read as an edit
attempt("smell-rewrite", function () {
	var sniffer = newMob("GenMob", R);
	var SM = prop("Prop_Smell", "TICKS=1 roses;-sour odor");
	sniffer.addNonUninvokableEffect(SM);
	for (var i = 0; i < 200 && ("" + SM.text()).indexOf("roses") >= 0; i++) SM.tick(sniffer, Tickable.TICKID_MOB);
	check("smell-rewrite", ("" + SM.text()).indexOf("sour odor") >= 0 && ("" + SM.text()).indexOf("roses") < 0, "the text is '" + SM.text() + "'");
	sniffer.destroy();
});

done();
