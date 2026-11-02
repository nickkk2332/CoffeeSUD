package com.planet_ink.coffee_mud.core;

import java.util.*;

import com.planet_ink.coffee_mud.Common.interfaces.Quest;
import com.planet_ink.coffee_mud.Common.interfaces.ScriptingEngine;
import com.planet_ink.coffee_mud.Libraries.interfaces.XMLLibrary;
import com.planet_ink.coffee_mud.Libraries.interfaces.XMLLibrary.XMLTag;
import com.planet_ink.coffee_mud.MOBS.interfaces.MOB;

/*
   Copyright 2026 CoffeeSUD contributors

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

	   http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License.
*/
/**
 * Q4: how each quest stands for each character: WON, FAILED, ABANDONED or
 * EXPIRED (and ACTIVE while they carry the quest's script), with an outcome
 * word (MPQUESTWIN's OUTCOME, or why it failed) and when, on the game clock;
 * Q5 counts the wins.  Saved with the world (world.xml's QUESTRECORDS),
 * beside the quests' winners.
 */
public final class QuestRecords
{
	/** the statuses */
	public static final String	ACTIVE		= "ACTIVE";
	public static final String	WON			= "WON";
	public static final String	FAILED		= "FAILED";
	public static final String	ABANDONED	= "ABANDONED";
	public static final String	EXPIRED		= "EXPIRED";

	private QuestRecords()
	{
	}

	/** a character's record of a quest */
	public static final class Record
	{
		/** WON, FAILED, ABANDONED, EXPIRED, or "" */
		public String	status	= "";
		/** the outcome word, or why it failed */
		public String	outcome	= "";
		/** when the status came, on the game clock */
		public long		when	= 0;
		/** Q5: how many times won */
		public int		wins	= 0;
		/** Q5: when last won, on the game clock */
		public long		lastWin	= 0;
	}

	/** quest name, then character name, to the record */
	private static final Map<String, Map<String, Record>>	records	= new TreeMap<String, Map<String, Record>>(String.CASE_INSENSITIVE_ORDER);

	/**
	 * @param quest a quest's name
	 * @param who a character's name
	 * @return the record, or null
	 */
	public static synchronized Record get(final String quest, final String who)
	{
		if((quest == null) || (who == null))
			return null;
		final Map<String, Record> m = records.get(quest);
		return (m == null) ? null : m.get(who);
	}

	private static synchronized Record make(final String quest, final String who)
	{
		Map<String, Record> m = records.get(quest);
		if(m == null)
		{
			m = new TreeMap<String, Record>(String.CASE_INSENSITIVE_ORDER);
			records.put(quest, m);
		}
		Record R = m.get(who);
		if(R == null)
		{
			R = new Record();
			m.put(who, R);
		}
		return R;
	}

	/**
	 * From DefaultQuest.declareWinner: a win.
	 * @param quest the quest's name
	 * @param who the winner's name
	 */
	public static void won(final String quest, final String who)
	{
		recordWon(quest, who);
		QuestItems.left(quest, who, WON); // Q8: what was bound to it, as REMOVEON says
		QuestTimers.left(quest, who); // Q11: and their deadline
	}

	private static synchronized void recordWon(final String quest, final String who)
	{
		if((quest == null) || (who == null) || (who.trim().length() == 0))
			return;
		final Record R = make(quest, who);
		R.status = WON;
		R.outcome = "";
		R.when = GameTime.now();
		R.wins++;
		R.lastWin = R.when;
	}

	/**
	 * A win taken away (MPQUESTWIN -name, MODIFY QUEST UNWIN).
	 * @param quest the quest's name
	 * @param who the character's name
	 */
	public static synchronized void unwon(final String quest, final String who)
	{
		final Record R = get(quest, who);
		if(R == null)
			return;
		R.wins = Math.max(0, R.wins - 1);
		if(R.status.equals(WON))
		{
			R.status = "";
			R.outcome = "";
		}
	}

	/**
	 * MPQUESTWIN ... OUTCOME word.
	 * @param quest the quest's name
	 * @param who the winner's name
	 * @param outcome the word
	 */
	public static synchronized void setOutcome(final String quest, final String who, final String outcome)
	{
		if((quest == null) || (who == null))
			return;
		make(quest, who).outcome = (outcome == null) ? "" : outcome.trim();
	}

	/**
	 * The quest ended for a character otherwise than by a win.
	 * @param quest the quest's name
	 * @param who the character's name
	 * @param status FAILED, ABANDONED or EXPIRED
	 * @param reason why, or ""
	 */
	public static void ended(final String quest, final String who, final String status, final String reason)
	{
		recordEnded(quest, who, status, reason);
		QuestItems.left(quest, who, status); // Q8
		QuestTimers.left(quest, who); // Q11
	}

	private static synchronized void recordEnded(final String quest, final String who, final String status, final String reason)
	{
		if((quest == null) || (who == null) || (who.trim().length() == 0))
			return;
		final Record R = make(quest, who);
		R.status = status;
		R.outcome = (reason == null) ? "" : reason.trim();
		R.when = GameTime.now();
	}

	/**
	 * From DefaultQuest.acceptQuest: the character took the quest on.
	 * @param quest the quest's name
	 * @param who the character's name
	 */
	public static synchronized void accepted(final String quest, final String who)
	{
		if((quest == null) || (who == null) || (who.trim().length() == 0))
			return;
		final Record R = make(quest, who);
		R.status = ACTIVE;
		R.outcome = "";
		R.when = GameTime.now();
	}

	/**
	 * QUESTSTATUS(who quest): ACTIVE once accepted, WON, FAILED, ABANDONED
	 * or EXPIRED as it last went; ACTIVE too for one who carries the
	 * quest's script with no record of it; else "".
	 * @param quest the quest's name
	 * @param mob the character, or null
	 * @param who their name
	 * @return the status
	 */
	public static String status(final String quest, final MOB mob, final String who)
	{
		final Quest Q = CMLib.quests().fetchQuest(quest);
		final Record R = answering((Q != null) ? Q.name() : quest, who); // Q12
		if((R != null) && (R.status.length() > 0))
			return R.status;
		if((Q != null) && (mob != null) && QuestMarkers.accepted(mob, Q))
			return ACTIVE;
		return "";
	}

	/**
	 * QUESTOUTCOME(who quest).
	 * @param quest the quest's name
	 * @param who the character's name
	 * @return the outcome word, or why it failed, or ""
	 */
	public static String outcome(final String quest, final String who)
	{
		final Quest Q = CMLib.quests().fetchQuest(quest);
		final Record R = answering((Q != null) ? Q.name() : quest, who); // Q12
		return (R == null) ? "" : R.outcome;
	}

	/**
	 * Q12: the record that answers for a character: theirs; for a SHARED
	 * PROFILE quest, a win of any of the profile's characters, else theirs,
	 * else the profile's latest.
	 * @param quest the quest's name
	 * @param who the character's name
	 * @return the record, or null
	 */
	private static Record answering(final String quest, final String who)
	{
		final Record own = get(quest, who);
		if(((own != null) && own.status.equals(WON)) || (!QuestSharing.isProfile(quest)))
			return own;
		Record won = null;
		Record latest = null;
		for(final String n : QuestSharing.profileNames(who))
		{
			final Record R = get(quest, n);
			if((R == null) || (R.status.length() == 0))
				continue;
			if(R.status.equals(WON) && ((won == null) || (R.when > won.when)))
				won = R;
			if((latest == null) || (R.when > latest.when))
				latest = R;
		}
		if(won != null)
			return won;
		if((own != null) && (own.status.length() > 0))
			return own;
		return (latest != null) ? latest : own;
	}

	/**
	 * Q5: QUESTWINS(who quest).
	 * @param quest the quest's name
	 * @param who the character's name
	 * @return how many times they won it (1 for a winner from before the count was kept)
	 */
	public static int wins(final String quest, final String who)
	{
		final Quest Q = CMLib.quests().fetchQuest(quest);
		int wins = 0;
		for(final String n : QuestSharing.creditNames(quest, who)) // Q12: the profile's, for SHARED PROFILE
		{
			final Record R = get((Q != null) ? Q.name() : quest, n);
			if((R != null) && (R.wins > 0))
				wins += R.wins;
			else
			if((Q != null) && (Q.getWinners().get(n) != null))
				wins++;
		}
		return wins;
	}

	/**
	 * Q5: when a character last won a quest, on the game clock.
	 * @param quest the quest's name
	 * @param who the character's name
	 * @return the game time, or -1 if they never did
	 */
	public static long lastWin(final String quest, final String who)
	{
		final Quest Q = CMLib.quests().fetchQuest(quest);
		long last = -1;
		for(final String n : QuestSharing.creditNames(quest, who)) // Q12: the profile's, for SHARED PROFILE
		{
			final Record R = get((Q != null) ? Q.name() : quest, n);
			final Long L = (Q != null) ? Q.getWinners().get(n) : null;
			final long t = ((R != null) && (R.wins > 0)) ? R.lastWin : (L == null) ? -1 : L.longValue();
			last = Math.max(last, t);
		}
		return last;
	}

	/**
	 * Q5: QUESTWINAGE(who quest).
	 * @param quest the quest's name
	 * @param who the character's name
	 * @return game hours since they last won it, or -1 if they never did
	 */
	public static long winAgeHours(final String quest, final String who)
	{
		final long last = lastWin(quest, who);
		if(last < 0)
			return -1;
		return Math.max(0, GameTime.now() - last) / Math.max(1, CMProps.getMillisPerMudHour());
	}

	/**
	 * A character's records, for QUESTS WON and QUESTS FAILED.
	 * @param who the character's name
	 * @return quest name to record
	 */
	public static synchronized Map<String, Record> of(final String who)
	{
		final Map<String, Record> all = new TreeMap<String, Record>(String.CASE_INSENSITIVE_ORDER);
		for(final Map.Entry<String, Map<String, Record>> e : records.entrySet())
		{
			final Record R = e.getValue().get(who);
			if(R != null)
				all.put(e.getKey(), R);
		}
		return all;
	}

	/**
	 * MPQUESTFAIL, and a KEYPLAYER FAIL death: the quest fails for a
	 * character: the record, QUESTFAIL_PROG and QUEST_PROG FAIL, then their
	 * quest scripts are ended and taken away, as MPENDQUEST does.
	 * @param Q the quest
	 * @param mob the character
	 * @param status FAILED or EXPIRED
	 * @param reason why, or ""
	 */
	public static void fail(final Quest Q, final MOB mob, final String status, final String reason)
	{
		if((Q == null) || (mob == null))
			return;
		final List<MOB> party = QuestSharing.partyOn(Q, mob); // Q12: SET SHARED PARTY: theirs too
		final String why = ((reason == null) || (reason.trim().length() == 0)) ? status : reason.trim();
		ended(Q.name(), mob.Name(), status, why);
		ScriptEvents.questFail(Q, mob, why);
		ScriptEvents.quest(Q, "FAIL", mob, why);
		for(int i = mob.numScripts() - 1; i >= 0; i--)
		{
			final ScriptingEngine S = mob.fetchScript(i);
			if((S != null) && (S.defaultQuestName() != null) && S.defaultQuestName().equalsIgnoreCase(Q.name()))
			{
				S.stepQuest(mob, mob, Q.name());
				mob.delScript(S);
				S.endQuest(mob, mob, Q.name());
			}
		}
		if(!party.isEmpty())
		{
			QuestSharing.spread(() -> {
				for(final MOB P : party)
					fail(Q, P, status, reason);
			});
		}
	}

	// ------------------------------------------------------------ offers (Q7)

	private static String key(final String kind, final MOB mob)
	{
		return kind + ":" + mob.Name().toUpperCase();
	}

	/**
	 * Q7: a quest offered to a character, as Prop_QuestGiver offers it:
	 * OFFERED:NAME, with when on the game clock (REOFFER reads it), kept
	 * with the quest's variables and so in the save.
	 * @param Q the quest
	 * @param mob the character
	 */
	public static void offered(final Quest Q, final MOB mob)
	{
		if((Q == null) || (mob == null) || (!Q.running()))
			return;
		Q.setQuestVar(key("OFFERED", mob), "" + GameTime.now());
		shown(Q, mob);
	}

	/**
	 * Q7: a quest shown to a character (an offer, a quest board's entry),
	 * for QUESTS OFFERED only: SHOWN:NAME.
	 * @param Q the quest
	 * @param mob the character
	 */
	public static void shown(final Quest Q, final MOB mob)
	{
		if((Q == null) || (mob == null) || (!Q.running()))
			return;
		Q.setQuestVar(key("SHOWN", mob), "" + GameTime.now());
	}

	/**
	 * Q7: QUESTS OFFERED: the running quests offered or shown to a
	 * character that they may still accept.
	 * @param mob the character
	 * @return the quests, by name
	 */
	public static List<Quest> offeredTo(final MOB mob)
	{
		final List<Quest> found = new ArrayList<Quest>();
		if(mob == null)
			return found;
		for(final Enumeration<Quest> q = CMLib.quests().enumQuests(); q.hasMoreElements();)
		{
			final Quest Q = q.nextElement();
			if((Q == null) || (!Q.running()))
				continue;
			if((Q.getStat(key("OFFERED", mob)).length() > 0) || (Q.getStat(key("SHOWN", mob)).length() > 0))
			{
				if(Q.canAcceptQuest(mob) && (!found.contains(Q)))
					found.add(Q);
			}
		}
		Collections.sort(found, new Comparator<Quest>()
		{
			@Override
			public int compare(final Quest a, final Quest b)
			{
				return a.name().compareToIgnoreCase(b.name());
			}
		});
		return found;
	}

	// ------------------------------------------------------------ the save

	/** a new game: no records */
	public static synchronized void newGame()
	{
		records.clear();
	}

	/**
	 * @return the records, as world.xml's QUESTRECORDS
	 */
	public static synchronized String toXML()
	{
		final XMLLibrary xml = CMLib.xml();
		final StringBuilder str = new StringBuilder("<QUESTRECORDS>");
		for(final Map.Entry<String, Map<String, Record>> e : records.entrySet())
		{
			for(final Map.Entry<String, Record> r : e.getValue().entrySet())
			{
				final Record R = r.getValue();
				str.append("<R Q=\"").append(xml.parseOutAngleBracketsAndQuotes(e.getKey()))
				   .append("\" W=\"").append(xml.parseOutAngleBracketsAndQuotes(r.getKey()))
				   .append("\" S=\"").append(R.status)
				   .append("\" O=\"").append(xml.parseOutAngleBracketsAndQuotes(R.outcome))
				   .append("\" T=\"").append(R.when)
				   .append("\" N=\"").append(R.wins)
				   .append("\" L=\"").append(R.lastWin)
				   .append("\" />");
			}
		}
		return str.append("</QUESTRECORDS>\n").toString();
	}

	/**
	 * Puts back the records a save had.
	 * @param tag QUESTRECORDS, or null for none
	 */
	public static synchronized void fromXML(final XMLTag tag)
	{
		records.clear();
		if(tag == null)
			return;
		final XMLLibrary xml = CMLib.xml();
		for(final XMLTag r : tag.contents())
		{
			if(!r.tag().equalsIgnoreCase("R"))
				continue;
			final Record R = make(xml.restoreAngleBrackets(r.getParmValue("Q")), xml.restoreAngleBrackets(r.getParmValue("W")));
			R.status = r.getParmValue("S");
			R.outcome = xml.restoreAngleBrackets(r.getParmValue("O"));
			R.when = CMath.s_long(r.getParmValue("T"));
			R.wins = CMath.s_int(r.getParmValue("N"));
			R.lastWin = CMath.s_long(r.getParmValue("L"));
		}
	}
}
