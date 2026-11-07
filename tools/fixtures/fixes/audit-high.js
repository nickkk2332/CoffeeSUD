// The high-severity defects of the documentation audit (audit/docs-audit-2026-11.md)
// that can be tested in one game: D-BW-1, D-BW-2, D-SC-1, D-QU-1, D-SE-1, D-SE-2, D-TI-1.
// D-SP-1 and D-SP-2 are played tests in tools/fixes.sh (group saves3).
FIXFILE = "audit-high";
var R = M.location();
function s(x) { return "" + x; }
function has(text, part) { return s(text).replace(/\s+/g, " ").indexOf(part) >= 0; }
function words(t) { var L = new java.util.ArrayList(); var w = t.split(" "); for (var i = 0; i < w.length; i++) L.add(w[i]); return L; }
function hasScript(m, S) { for (var e = m.scripts(); e.hasMoreElements();) if (e.nextElement() == S) return true; return false; }
// a builder of the test's own, whose session answers what it is fed
// a standard (not generic) mob, as a player is: the old D-BW-1 test looked at the builder
var B = newMob("StdMOB", R);
B.setName("Fixauditor");
B.setSavable(false);
// isFake false, so that session() gives it and CATALOG can ask its question
var BS = new JavaAdapter(CM.Common.FakeSession, { isFake: function() { return false; } });
BS.initialize(null, "MEMORY");
B.setSession(BS);
BS.setMob(B);
var mark = 0;
function out() { var all = s(field(BS, "bout").toString()); var t = all.substring(mark); mark = all.length; return t; }
var made = [];
function amob(name) { var m = newMob("GenMob", R); m.setName(name); m.setSavable(false); made.push(m); return m; }

try {
	// D-BW-1: MODIFY MOB x LEVEL n keeps a generic mob's combat numbers set by hand,
	// and still derives them for one nobody has tuned
	var tuned = amob("fixtuned");
	tuned.basePhyStats().setArmor(7);
	tuned.basePhyStats().setAttackAdjustment(123);
	tuned.basePhyStats().setDamage(45);
	tuned.recoverPhyStats();
	out();
	CMClass.getCommand("Modify").mobs(B, words("MODIFY MOB fixtuned LEVEL 12"));
	var said = out();
	var p = tuned.basePhyStats();
	check("bw1-level", p.level() == 12, "level " + p.level());
	check("bw1-kept", p.armor() == 7 && p.attackAdjustment() == 123 && p.damage() == 45,
		"armor " + p.armor() + " attack " + p.attackAdjustment() + " damage " + p.damage() + ": the numbers set by hand were overwritten");
	check("bw1-told", has(said, "were set by hand, so they were kept"), said);
	var fresh = amob("fixfresh");
	CMClass.getCommand("Modify").mobs(B, words("MODIFY MOB fixfresh LEVEL 12"));
	var d = CMClass.getMOB("GenMob");
	CMLib.leveler().fillOutMOB(d, 12);
	var f = fresh.basePhyStats();
	check("bw1-derived", f.level() == 12 && f.attackAdjustment() == d.basePhyStats().attackAdjustment() && f.damage() == d.basePhyStats().damage(),
		"attack " + f.attackAdjustment() + "/" + d.basePhyStats().attackAdjustment() + " damage " + f.damage() + "/" + d.basePhyStats().damage() + ": a new mob's numbers were not derived");
	d.destroy();

	// D-BW-2: CATALOG DELETE of many entries asks first, and NO keeps them
	var lamp = CMClass.getItem("GenItem"); lamp.setName("a fixaudit lamp"); CMLib.catalog().addCatalog(lamp);
	var rope = CMClass.getItem("GenItem"); rope.setName("a fixaudit rope"); CMLib.catalog().addCatalog(rope);
	var before = CMLib.catalog().getCatalogItems().length;
	out();
	BS.setFakeInput("N");
	CMClass.getCommand("Catalog").execute(B, words("CATALOG DELETE everydamnitem"), 0);
	said = out();
	check("bw2-asked", has(said, "This will permanently delete " + before + " catalog entries"), said);
	check("bw2-kept", CMLib.catalog().getCatalogItems().length == before && has(said, "Nothing was deleted"),
		"catalog " + CMLib.catalog().getCatalogItems().length + " of " + before);
	BS.setFakeInput("Y");
	CMClass.getCommand("Catalog").execute(B, words("CATALOG DELETE a fixaudit lamp"), 0);
	check("bw2-single", CMLib.catalog().getCatalogItem("a fixaudit lamp") == null, "a single entry was not deleted after YES: " + out());

	// D-SC-1: FACTION(who faction == band) compares the band's name as text
	var FA = CMClass.getCommon("DefaultFaction");
	FA.initializeFaction(new java.lang.StringBuffer('NAME=Fix audit standing\nMINIMUM=0\nMAXIMUM=100000\nDEFAULT=0\nSCOREDISPLAY=true\nSHOWINFACTIONSCMD=true\nRATEMODIFIER=1\n'
		+ 'RANGE1=0;49;Unknown;FIXAUD_UNKNOWN;\nRANGE2=50;100000;Trusted;FIXAUD_TRUSTED;\n'
		+ 'CHANGE1=GIFT(CHANCE=10);UP;100%;OUTSIDER SELFOK JUST100;\nCHANGE2=BRIBE;UP;100%;OUTSIDER SELFOK JUST100;\n'), "FIX_AUDIT.INI");
	CMLib.factions().addFaction(FA);
	try {
		var friend = amob("fixaudit friend");
		friend.addFaction(FA.factionID(), 60);
		scriptOn(friend, "RAND_PROG 100\n"
			+ "if faction($i FIX_AUDIT.INI == Trusted)\nmpsetvar FIXTEST FIXAUDF1 yes\nelse\nmpsetvar FIXTEST FIXAUDF1 no\nendif\n"
			+ "if faction($i FIX_AUDIT.INI == Unknown)\nmpsetvar FIXTEST FIXAUDF2 yes\nelse\nmpsetvar FIXTEST FIXAUDF2 no\nendif\n"
			+ "if faction($i FIX_AUDIT.INI == FIXAUD_TRUSTED)\nmpsetvar FIXTEST FIXAUDF3 yes\nelse\nmpsetvar FIXTEST FIXAUDF3 no\nendif\n"
			+ "if faction($i FIX_AUDIT.INI >= 50)\nmpsetvar FIXTEST FIXAUDF4 yes\nelse\nmpsetvar FIXTEST FIXAUDF4 no\nendif\nmpdisable\n~\n");
		tickMob(friend);
		check("sc1-band", gvar("FIXAUDF1") == "yes" && gvar("FIXAUDF2") == "no",
			"== Trusted " + gvar("FIXAUDF1") + ", == Unknown " + gvar("FIXAUDF2") + " (should be yes, no)");
		check("sc1-code", gvar("FIXAUDF3") == "yes", "== FIXAUD_TRUSTED " + gvar("FIXAUDF3"));
		check("sc1-number", gvar("FIXAUDF4") == "yes", ">= 50 " + gvar("FIXAUDF4"));

		// D-SE-2: a change with CHANCE=10 fires about 10 percent of the time
		var src = amob("fixaudit giver"), tgt = amob("fixaudit taker");
		src.addFaction(FA.factionID(), 0); tgt.addFaction(FA.factionID(), 0);
		function fired(ev, n) {
			var c = 0;
			for (var i = 0; i < n; i++) {
				src.addFaction(FA.factionID(), 0);
				FA.executeChange(src, tgt, ev);
				if (src.fetchFaction(FA.factionID()) != 0) c++;
			}
			return c;
		}
		var always = fired(FA.getChangeEvents("BRIBE")[0], 20);
		check("se2-harness", always == 20, "a change with no CHANCE fired " + always + " of 20 times");
		var some = fired(FA.getChangeEvents("GIFT")[0], 200);
		check("se2-chance", some > 0 && some < 60, "CHANCE=10 fired " + some + " of 200 times (about 20 expected; the old test gave about 180)");
	} finally {
		CMLib.factions().removeFaction(FA.factionID());
	}

	// D-QU-1: listing a character's quests keeps the tracking script of a quest that
	// is not loaded, and drops only that of a quest deleted in this game
	var holder = amob("fixaudit questholder");
	var S1 = scriptOn(holder, "GREET_PROG 100\nmpecho hello\n~\n"); S1.registerDefaultQuest("fixaudit missing quest");
	var S2 = scriptOn(holder, "GREET_PROG 100\nmpecho hello\n~\n"); S2.registerDefaultQuest("fixaudit deleted quest");
	var Q = CMClass.getCommon("DefaultQuest"); Q.setName("fixaudit deleted quest");
	CMLib.quests().addQuest(Q); CMLib.quests().delQuest(Q);
	CMLib.quests().getPlayerPersistentQuests(holder);
	check("qu1-missing-kept", hasScript(holder, S1), "the tracking script of a quest that is not loaded was deleted");
	check("qu1-deleted-dropped", !hasScript(holder, S2), "the tracking script of a deleted quest was kept");

	// D-SE-1: GuildMaster issues the kit named by KIT.<position>
	var guild = CMClass.getCommon("DefaultClan"); guild.setName("Fix Audit Guild"); guild.setGovernmentID(0); CMLib.clans().addClan(guild);
	try {
		var member = amob("fixaudit member");
		member.setClan(guild.clanID(), guild.getGovernment().getAcceptPos());
		var role = s(CM.core.Organizations.memberValue(member, guild, "POSITION"));
		var badge = CMClass.getItem("GenItem"); badge.setName("a fixaudit badge"); CMLib.catalog().addCatalog(badge);
		var master = amob("fixaudit guildmaster");
		var GM = CMClass.getBehavior("GuildMaster");
		GM.setParms('CLAN="Fix Audit Guild" KIT.' + role.replace(/ /g, "_") + '="a fixaudit badge"');
		said = s(GM.serve(master, member, "kit"));
		check("se1-kit", member.findItem("a fixaudit badge") != null && has(said, "You receive your"), "role " + role + ": " + said);
		CMLib.catalog().delCatalog(CMLib.catalog().getCatalogItem("a fixaudit badge"));
	} finally {
		CMLib.clans().removeClan(guild);
	}

	// D-TI-1: a ticket taker that wants a named ticket refuses boarding without one
	var cart = CMClass.getItem("GenRideable"); cart.setName("a fixaudit cart"); R.addItem(cart);
	var tt = CMClass.getAbility("Prop_TicketTaker"); tt.setMiscText("a fixaudit ticket"); cart.addNonUninvokableEffect(tt);
	var rider = amob("Fixauditrider");
	rider.setPlayerStats(CMClass.getCommon("DefaultPlayerStats"));
	var rs = CMClass.getCommon("PartySession"); rs.initialize(null, "0"); rs.setMob(rider); rider.setSession(rs);
	CMLib.threads().deleteTick(rider, -1);
	var board = CMClass.getMsg(rider, cart, null, CMMsg.MSG_MOUNT, null);
	check("ti1-refused", !tt.okMessage(cart, board), "boarded without the ticket");
	var ticket = CMClass.getItem("GenItem"); ticket.setName("a fixaudit ticket"); rider.addItem(ticket);
	check("ti1-boards", tt.okMessage(cart, board), "refused with the ticket");
	rider.setSession(null);
	cart.destroy();
} finally {
	var catLeft = CMLib.catalog().getCatalogItem("a fixaudit rope"); if (catLeft != null) CMLib.catalog().delCatalog(catLeft);
	catLeft = CMLib.catalog().getCatalogItem("a fixaudit lamp"); if (catLeft != null) CMLib.catalog().delCatalog(catLeft);
	for (var i = 0; i < made.length; i++) made[i].destroy();
	B.setSession(null);
	B.destroy();
}
done();
