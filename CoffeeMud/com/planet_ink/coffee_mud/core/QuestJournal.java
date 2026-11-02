package com.planet_ink.coffee_mud.core;

import java.util.*;

import com.planet_ink.coffee_mud.Abilities.interfaces.Ability;
import com.planet_ink.coffee_mud.Common.interfaces.Quest;
import com.planet_ink.coffee_mud.Common.interfaces.TimeClock;
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
 * Q3: a character's quest journal: for each quest, objectives (an ID, a
 * count to reach, the count so far, done or failed, maybe hidden, and the
 * text) and entries dated on the game calendar.  Kept, as JSON, in the
 * character's hidden Prop_QuestLog, so it is saved with them.
 * MPQOBJECTIVE, MPQJOURNAL and QOBJECTIVE() are the scripts' way in; a
 * quest's SET OBJECTIVE and SET JOURNAL give each step's for those on it;
 * QUESTS shows them.  QUESTNOTICES=OFF (a world option) keeps back the
 * "Quest updated:" lines.
 */
public final class QuestJournal
{
	/** the property that keeps a character's journal */
	public static final String	ID		= "Prop_QuestLog";
	/** the most entries kept, oldest going first */
	public static final int		MAX_ENTRIES	= 200;

	private QuestJournal()
	{
	}

	/** an objective of a quest */
	public static final class Objective
	{
		public String	id		= "";
		public int		target	= 1;
		public int		count	= 0;
		/** "", DONE or FAILED */
		public String	state	= "";
		public boolean	hidden	= false;
		public String	text	= "";
		/** Q16: where it is, as AT= gave it: a room ID, an area, or questobj:MOB */
		public String	at		= "";

		/** @return DONE, FAILED, or the count */
		public String value()
		{
			return (state.length() > 0) ? state : ("" + count);
		}
	}

	/** an entry, dated */
	public static final class Entry
	{
		public long		seq		= 0;
		public String	quest	= "";
		public String	date	= "";
		public String	text	= "";
	}

	/** a character's journal */
	public static final class Journal
	{
		public final Map<String, List<Objective>>	objectives	= new LinkedHashMap<String, List<Objective>>();
		public final List<Entry>					entries		= new ArrayList<Entry>();
		public long									seq			= 0;

		/**
		 * @param quest a quest's name
		 * @return its objectives, made if need be
		 */
		public List<Objective> of(final String quest)
		{
			final String key = quest.toLowerCase();
			List<Objective> L = objectives.get(key);
			if(L == null)
			{
				L = new ArrayList<Objective>();
				objectives.put(key, L);
			}
			return L;
		}

		/**
		 * @param quest a quest's name
		 * @param id an objective's ID
		 * @return the objective, or null
		 */
		public Objective find(final String quest, final String id)
		{
			final List<Objective> L = objectives.get(quest.toLowerCase());
			if(L != null)
			{
				for(final Objective O : L)
				{
					if(O.id.equalsIgnoreCase(id))
						return O;
				}
			}
			return null;
		}
	}

	// ------------------------------------------------------------ reading and writing

	private static Ability holder(final MOB mob, final boolean make)
	{
		if(mob == null)
			return null;
		Ability A = mob.fetchEffect(ID);
		if((A == null) && make)
		{
			A = CMClass.getAbility(ID);
			if(A == null)
				return null;
			A.setMiscText("");
			mob.addNonUninvokableEffect(A);
		}
		return A;
	}

	private static String str(final MiniJSON.JSONObject o, final String key)
	{
		final Object v = o.get(key);
		return (v == null) ? "" : v.toString();
	}

	private static long num(final MiniJSON.JSONObject o, final String key)
	{
		final Object v = o.get(key);
		return (v instanceof Number) ? ((Number)v).longValue() : CMath.s_long((v == null) ? "0" : v.toString());
	}

	/**
	 * @param mob a character
	 * @return their journal (empty if they have none)
	 */
	public static Journal read(final MOB mob)
	{
		final Journal log = new Journal();
		final Ability A = holder(mob, false);
		if((A == null) || (A.text().trim().length() == 0))
			return log;
		try
		{
			final MiniJSON.JSONObject root = new MiniJSON().parseObject(A.text());
			log.seq = num(root, "n");
			final Object os = root.get("o");
			if(os instanceof MiniJSON.JSONObject)
			{
				for(final Map.Entry<String, Object> e : ((MiniJSON.JSONObject)os).entrySet())
				{
					if(!(e.getValue() instanceof Object[]))
						continue;
					final List<Objective> L = log.of(e.getKey());
					for(final Object x : (Object[])e.getValue())
					{
						if(!(x instanceof MiniJSON.JSONObject))
							continue;
						final MiniJSON.JSONObject j = (MiniJSON.JSONObject)x;
						final Objective O = new Objective();
						O.id = str(j, "id");
						O.target = (int)num(j, "t");
						O.count = (int)num(j, "c");
						O.state = str(j, "s");
						O.hidden = "true".equals(str(j, "h"));
						O.text = str(j, "x");
						O.at = str(j, "at");
						L.add(O);
					}
				}
			}
			final Object es = root.get("e");
			if(es instanceof Object[])
			{
				for(final Object x : (Object[])es)
				{
					if(!(x instanceof MiniJSON.JSONObject))
						continue;
					final MiniJSON.JSONObject j = (MiniJSON.JSONObject)x;
					final Entry E = new Entry();
					E.seq = num(j, "n");
					E.quest = str(j, "q");
					E.date = str(j, "d");
					E.text = str(j, "x");
					log.entries.add(E);
				}
			}
		}
		catch(final MiniJSON.MJSONException e)
		{
			Log.errOut("QuestJournal", mob.Name() + "'s quest journal could not be read: " + e.getMessage());
		}
		return log;
	}

	/**
	 * @param mob a character
	 * @param log their journal, written back
	 */
	public static void write(final MOB mob, final Journal log)
	{
		final Ability A = holder(mob, true);
		if(A == null)
			return;
		final MiniJSON.JSONObject root = new MiniJSON.JSONObject();
		root.put("n", Long.valueOf(log.seq));
		final MiniJSON.JSONObject os = new MiniJSON.JSONObject();
		for(final Map.Entry<String, List<Objective>> e : log.objectives.entrySet())
		{
			if(e.getValue().isEmpty())
				continue;
			final Object[] arr = new Object[e.getValue().size()];
			for(int i = 0; i < arr.length; i++)
			{
				final Objective O = e.getValue().get(i);
				final MiniJSON.JSONObject j = new MiniJSON.JSONObject();
				j.put("id", O.id);
				j.put("t", Long.valueOf(O.target));
				j.put("c", Long.valueOf(O.count));
				j.put("s", O.state);
				j.put("h", O.hidden ? "true" : "false");
				j.put("x", O.text);
				if(O.at.length() > 0)
					j.put("at", O.at);
				arr[i] = j;
			}
			os.put(e.getKey(), arr);
		}
		root.put("o", os);
		while(log.entries.size() > MAX_ENTRIES)
			log.entries.remove(0);
		final Object[] es = new Object[log.entries.size()];
		for(int i = 0; i < es.length; i++)
		{
			final Entry E = log.entries.get(i);
			final MiniJSON.JSONObject j = new MiniJSON.JSONObject();
			j.put("n", Long.valueOf(E.seq));
			j.put("q", E.quest);
			j.put("d", E.date);
			j.put("x", E.text);
			es[i] = j;
		}
		root.put("e", es);
		A.setMiscText(root.toString());
	}

	// ------------------------------------------------------------ what scripts do

	/**
	 * @param quest a quest's name
	 * @return its name as shown
	 */
	public static String shown(final String quest)
	{
		final Quest Q = CMLib.quests().fetchQuest(quest);
		if(Q == null)
			return quest;
		return (Q.displayName().trim().length() > 0) ? Q.displayName() : Q.name();
	}

	private static void notice(final MOB mob, final String quest, final String what)
	{
		if(CMProps.instance().getStr("QUESTNOTICES").trim().equalsIgnoreCase("OFF"))
			return;
		mob.tell(CMLib.lang().L("^yQuest updated: ^w@x1^y: @x2^N", shown(quest), what));
	}

	private static String counted(final Objective O)
	{
		if(O.state.equals("DONE"))
			return O.text + ((O.target > 1) ? (" " + O.target + "/" + O.target) : "") + " (done)";
		if(O.state.equals("FAILED"))
			return O.text + " (failed)";
		return O.text + ((O.target > 1) ? (" " + Math.min(O.count, O.target) + "/" + O.target) : "");
	}

	/**
	 * MPQOBJECTIVE: ADD id target text, SET id n, BUMP id (n), DONE id,
	 * FAIL id, HIDE id, SHOW id, CLEAR (id).
	 * @param mob the character
	 * @param quest the quest's name
	 * @param op what to do
	 * @param rest the ID and what follows
	 * @return null, or what was wrong
	 */
	public static String objective(final MOB mob, final String quest, final String op, final String rest)
	{
		if((mob == null) || (quest == null) || (quest.length() == 0))
			return "no character or quest";
		final List<String> words = CMParms.parse(rest);
		final String id = (words.size() > 0) ? words.get(0) : "";
		final Journal log = read(mob);
		final String what = op.toUpperCase();
		if(what.equals("CLEAR"))
		{
			if(id.length() == 0)
				log.of(quest).clear();
			else
			{
				final Objective O = log.find(quest, id);
				if(O != null)
					log.of(quest).remove(O);
			}
			write(mob, log);
			return null;
		}
		if(id.length() == 0)
			return "no objective ID";
		if(what.equals("ADD"))
		{
			final String target = (words.size() > 1) ? words.get(1) : "1";
			final String text = (words.size() > 2) ? CMParms.combine(words, 2) : id;
			Objective O = log.find(quest, id);
			final boolean isNew = (O == null);
			if(isNew)
			{
				O = new Objective();
				O.id = id;
				log.of(quest).add(O);
			}
			O.target = Math.max(1, CMath.s_int(target));
			O.text = text;
			write(mob, log);
			if(isNew)
				notice(mob, quest, counted(O));
			return null;
		}
		final Objective O = log.find(quest, id);
		if(O == null)
			return "no objective " + id + " for quest " + quest;
		final int n = (words.size() > 1) ? CMath.s_int(words.get(1)) : 1;
		final String before = O.value();
		if(what.equals("SET"))
			O.count = n;
		else
		if(what.equals("BUMP"))
			O.count += (words.size() > 1) ? n : 1;
		else
		if(what.equals("DONE"))
		{
			O.state = "DONE";
			O.count = Math.max(O.count, O.target);
		}
		else
		if(what.equals("FAIL"))
			O.state = "FAILED";
		else
		if(what.equals("HIDE"))
			O.hidden = true;
		else
		if(what.equals("SHOW"))
			O.hidden = false;
		else
			return "unknown change " + op;
		if((what.equals("SET") || what.equals("BUMP")) && (O.state.length() == 0) && (O.count >= O.target))
			O.state = "DONE";
		if((what.equals("SET") || what.equals("BUMP")) && O.state.equals("DONE") && (O.count < O.target))
			O.state = "";
		write(mob, log);
		if((!O.hidden) && (!before.equals(O.value())))
			notice(mob, quest, counted(O));
		return null;
	}

	/**
	 * MPQJOURNAL: an entry, dated on the game calendar.
	 * @param mob the character
	 * @param quest the quest's name
	 * @param text the entry
	 */
	public static void journal(final MOB mob, final String quest, final String text)
	{
		if((mob == null) || (text == null) || (text.trim().length() == 0))
			return;
		final Journal log = read(mob);
		final Entry E = new Entry();
		E.seq = ++log.seq;
		E.quest = (quest == null) ? "" : quest;
		final TimeClock C = CMLib.time().localClock(mob);
		E.date = (C == null) ? "" : C.getShortTimeDescription();
		E.text = text.trim();
		log.entries.add(E);
		write(mob, log);
		if(E.quest.length() > 0)
			notice(mob, E.quest, E.text);
	}

	/**
	 * QOBJECTIVE(who quest id).
	 * @param mob the character
	 * @param quest the quest's name
	 * @param id the objective's ID
	 * @return the count, DONE or FAILED, or "" if it has none such
	 */
	public static String value(final MOB mob, final String quest, final String id)
	{
		if((mob == null) || (quest == null))
			return "";
		final Objective O = read(mob).find(quest, id);
		return (O == null) ? "" : O.value();
	}

	// ------------------------------------------------------------ a quest's steps

	/**
	 * @param Q a quest
	 * @return the characters in the world who are on it (carry its script)
	 */
	public static List<MOB> onQuest(final Quest Q)
	{
		final List<MOB> on = new ArrayList<MOB>();
		if(Q == null)
			return on;
		for(final Enumeration<MOB> m = CMLib.players().players(); m.hasMoreElements();)
		{
			final MOB M = m.nextElement();
			if((M != null) && (M.location() != null) && QuestMarkers.accepted(M, Q))
				on.add(M);
		}
		return on;
	}

	/**
	 * A quest's SET OBJECTIVE id target text and SET JOURNAL text, for a
	 * character on it: an objective they have already is left as it is, and
	 * an entry is not written twice running.
	 * @param mob the character
	 * @param quest the quest's name
	 * @param objectives the step's objectives: id, target, text
	 * @param journal the step's entries
	 */
	public static void applyStep(final MOB mob, final String quest, final List<String[]> objectives, final List<String> journal)
	{
		if(mob == null)
			return;
		for(final String[] o : objectives)
		{
			if(read(mob).find(quest, o[0]) == null)
				objective(mob, quest, "ADD", o[0] + " " + o[1] + " " + o[2]);
		}
		for(final String text : journal)
		{
			final Journal log = read(mob);
			Entry last = null;
			for(final Entry E : log.entries)
			{
				if(E.quest.equalsIgnoreCase(quest))
					last = E;
			}
			if((last == null) || (!last.text.equals(text.trim())))
				journal(mob, quest, text);
		}
	}

	// ------------------------------------------------------------ QUESTS

	/**
	 * @param mob a character
	 * @param quest a quest's name
	 * @return the lines of its objectives, [x] or [ ], with counts
	 */
	public static List<String> objectiveLines(final MOB mob, final String quest)
	{
		final List<String> lines = new ArrayList<String>();
		final List<Objective> L = read(mob).objectives.get(quest.toLowerCase());
		if(L == null)
			return lines;
		for(final Objective O : L)
		{
			if(O.hidden)
				continue;
			final String box = O.state.equals("DONE") ? "[x]" : O.state.equals("FAILED") ? "[-]" : "[ ]";
			lines.add(box + " " + counted(O));
		}
		return lines;
	}

	/**
	 * @param mob a character
	 * @param quest a quest's name, or null for all
	 * @param most how many, the latest
	 * @return the entries, oldest first, as "date: text" (and the quest, if all)
	 */
	public static List<String> entries(final MOB mob, final String quest, final int most)
	{
		final List<String> lines = new ArrayList<String>();
		final List<Entry> L = read(mob).entries;
		for(final Entry E : L)
		{
			if((quest != null) && (!E.quest.equalsIgnoreCase(quest)))
				continue;
			lines.add(((E.date.length() > 0) ? (E.date + ": ") : "")
				+ (((quest == null) && (E.quest.length() > 0)) ? ("[" + shown(E.quest) + "] ") : "") + E.text);
		}
		while(lines.size() > most)
			lines.remove(0);
		return lines;
	}

	/**
	 * Time on the game clock, for QUESTS: game days, or hours, or less.
	 * @param ticks the ticks left
	 * @return as "about 3 game hours"
	 */
	public static String timeLeft(final long ticks)
	{
		final long ms = Math.max(0, ticks) * CMProps.getTickMillis();
		final long hourMs = Math.max(1, CMProps.getMillisPerMudHour());
		final long hours = ms / hourMs;
		final TimeClock C = CMLib.time().globalClock();
		final int perDay = ((C == null) || (C.getHoursInDay() <= 0)) ? 24 : C.getHoursInDay();
		if(hours >= (perDay * 2))
			return CMLib.lang().L("about @x1 game days", "" + (hours / perDay));
		if(hours >= 2)
			return CMLib.lang().L("about @x1 game hours", "" + hours);
		if(hours == 1)
			return CMLib.lang().L("about a game hour");
		return CMLib.lang().L("less than a game hour");
	}
}
