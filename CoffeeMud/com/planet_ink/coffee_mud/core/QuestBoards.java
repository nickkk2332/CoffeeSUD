package com.planet_ink.coffee_mud.core;

import java.util.*;

import com.planet_ink.coffee_mud.Areas.interfaces.Area;
import com.planet_ink.coffee_mud.Behaviors.interfaces.Behavior;
import com.planet_ink.coffee_mud.Common.interfaces.Faction;
import com.planet_ink.coffee_mud.Common.interfaces.Quest;
import com.planet_ink.coffee_mud.Locales.interfaces.Room;
import com.planet_ink.coffee_mud.MOBS.interfaces.MOB;
import com.planet_ink.coffee_mud.core.exceptions.CMException;
import com.planet_ink.coffee_mud.core.interfaces.*;

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
 * Q14: a board of generated jobs (Prop_QuestBoard on an item or a room):
 * COUNT quests made through RandomQuests, listed when the board is read
 * or looked at, taken with ACCEPT n.  At each REFRESH, on the game clock,
 * the jobs done or given up go and new ones come up to COUNT, or, with
 * none taken, the oldest is replaced.  The quests carry the board's key
 * (QUESTBOARD) and when they were posted (POSTED) as quest variables, so a
 * saved game keeps them with the board.  The generator is given the
 * reader's PLAYER_LEVEL, PARTY_SIZE, PLAYER_FACTION_[ID] and QUESTS_DONE,
 * and with LEVELRANGE=n, TARGET_MIN_AREALEVEL and TARGET_MAX_AREALEVEL
 * around the level.
 */
public final class QuestBoards
{
	/** the quest variable with the board's key */
	public static final String	BOARD_VAR	= "QUESTBOARD";
	/** the quest variable with when it was posted, on the game clock */
	public static final String	POSTED_VAR	= "POSTED";

	private static final Set<String>					OWN			= new HashSet<String>(Arrays.asList("COUNT", "REFRESH", "VARS", "FILE", "LEVELRANGE"));
	private static final Map<String, List<String>>		lastListed	= new HashMap<String, List<String>>();

	private QuestBoards()
	{
	}

	/**
	 * @param board the board
	 * @return its key: its name and where it is
	 */
	public static String key(final Physical board)
	{
		if(board == null)
			return "";
		final Room R = CMLib.map().roomLocation(board);
		return board.Name() + "@" + ((board instanceof Area) ? board.Name() : ((R == null) ? "" : CMLib.map().getExtendedRoomID(R)));
	}

	/**
	 * @param key a board's key
	 * @return its running quests, the oldest first
	 */
	public static List<Quest> quests(final String key)
	{
		final List<Quest> L = new ArrayList<Quest>();
		if((key == null) || (key.length() == 0))
			return L;
		for(final Enumeration<Quest> q = CMLib.quests().enumQuests(); q.hasMoreElements();)
		{
			final Quest Q = q.nextElement();
			if((Q != null) && Q.running() && key.equals(Q.getQuestVars().get(BOARD_VAR)))
				L.add(Q);
		}
		Collections.sort(L, (a, b) -> Long.compare(posted(a), posted(b)));
		return L;
	}

	private static long posted(final Quest Q)
	{
		return CMath.s_long(Q.getQuestVars().get(POSTED_VAR));
	}

	/**
	 * @param Q a quest
	 * @return true if a character in the game is on it
	 */
	public static boolean inProgress(final Quest Q)
	{
		for(final Enumeration<MOB> m = CMLib.players().players(); m.hasMoreElements();)
		{
			final MOB M = m.nextElement();
			if((M != null) && QuestMarkers.accepted(M, Q))
				return true;
		}
		return false;
	}

	/**
	 * @param Q a quest
	 * @return true if a character took it, whether on it still or not
	 */
	public static boolean taken(final Quest Q)
	{
		if(inProgress(Q) || (Q.getWinners().size() > 0))
			return true;
		for(final Enumeration<MOB> m = CMLib.players().players(); m.hasMoreElements();)
		{
			final MOB M = m.nextElement();
			if((M != null) && (QuestRecords.get(Q.name(), M.Name()) != null))
				return true;
		}
		return false;
	}

	/**
	 * @param parms the board's parameters
	 * @param reader who reads it, or null
	 * @return the game time between refreshes, in ms
	 */
	public static long refreshMs(final String parms, final MOB reader)
	{
		String r = CMParms.getParmStr(parms, "REFRESH", "1 mudday").trim();
		final int x = r.indexOf(':');
		if(x > 0) // MUDDAY:1
			r = r.substring(x + 1).trim() + " " + r.substring(0, x).trim();
		try
		{
			final int ticks = CMLib.time().parseTickExpression(CMLib.time().localClock(reader), r);
			if(ticks > 0)
				return (long)ticks * CMProps.getTickMillis();
		}
		catch(final CMException e)
		{
			Log.errOut("QuestBoards", "REFRESH=" + r + ": " + e.getMessage());
		}
		return (long)CMProps.getMillisPerMudHour() * 24;
	}

	/**
	 * What the generator is told of the reader.
	 * @param reader who reads the board
	 * @return PLAYER_LEVEL, PARTY_SIZE, PLAYER_FACTION_[ID], QUESTS_DONE
	 */
	public static Map<String, String> context(final MOB reader)
	{
		final Map<String, String> vars = new LinkedHashMap<String, String>();
		if(reader == null)
			return vars;
		vars.put("PLAYER_LEVEL", "" + reader.phyStats().level());
		int party = 0;
		for(final MOB M : reader.getGroupMembers(new HashSet<MOB>()))
		{
			if((M != null) && (M.playerStats() != null))
				party++;
		}
		vars.put("PARTY_SIZE", "" + Math.max(1, party));
		for(final Enumeration<String> f = reader.factions(); f.hasMoreElements();)
		{
			final String id = f.nextElement();
			final Faction F = CMLib.factions().getFaction(id);
			String k = id.toUpperCase();
			if(k.endsWith(".INI"))
				k = k.substring(0, k.length() - 4);
			k = k.replaceAll("[^A-Z0-9]", "_");
			if(F != null)
				vars.put("PLAYER_FACTION_" + k, "" + reader.fetchFaction(id));
		}
		vars.put("QUESTS_DONE", "" + QuestRecords.questsWon(reader.Name()));
		return vars;
	}

	private static String quoted(final String v)
	{
		return "\"" + v.replace('"', '\'') + "\"";
	}

	/**
	 * The RandomQuests parameters for one job: the board's own taken out,
	 * VARS="..." opened, FILE as PATH, the reader's context added.
	 * @param parms the board's parameters
	 * @param reader who reads the board, or null
	 * @return the parameters
	 */
	public static String generatorParms(final String parms, final MOB reader)
	{
		final Map<String, String> P = CMParms.parseEQParms(parms);
		final StringBuilder str = new StringBuilder("MINQUESTS=1 MAXQUESTS=1 INLINE=true MIN=1 MAX=1 CHANCE=100");
		if(!P.containsKey("EXPIRE"))
			str.append(" EXPIRE=\"\""); // a job waits on the board until a refresh takes it away
		for(final Map.Entry<String, String> e : P.entrySet())
		{
			final String k = e.getKey().toUpperCase();
			if(OWN.contains(k))
				continue;
			str.append(" ").append(k).append("=").append(quoted(e.getValue()));
		}
		if(P.containsKey("FILE"))
			str.append(" PATH=").append(quoted(P.get("FILE")));
		if(P.containsKey("VARS"))
		{
			for(final Map.Entry<String, String> e : CMParms.parseEQParms(P.get("VARS")).entrySet())
				str.append(" ").append(e.getKey().toUpperCase()).append("=").append(quoted(e.getValue()));
		}
		final Map<String, String> ctx = context(reader);
		for(final Map.Entry<String, String> e : ctx.entrySet())
			str.append(" ").append(e.getKey()).append("=").append(quoted(e.getValue()));
		final int range = CMath.s_int(P.get("LEVELRANGE"));
		if((range > 0) && ctx.containsKey("PLAYER_LEVEL"))
		{
			final int level = CMath.s_int(ctx.get("PLAYER_LEVEL"));
			str.append(" TARGET_MIN_AREALEVEL=").append(Math.max(1, level - range));
			str.append(" TARGET_MAX_AREALEVEL=").append(level + range);
		}
		return str.toString();
	}

	/**
	 * Makes one job for a board.
	 * @param board the board
	 * @param parms its parameters
	 * @param reader who reads it, or null
	 * @return the quest, running, or null if none could be made
	 */
	public static Quest generate(final Physical board, final String parms, final MOB reader)
	{
		if(!(board instanceof PhysicalAgent))
			return null;
		final Behavior B = CMClass.getBehavior("RandomQuests");
		if(B == null)
			return null;
		B.startBehavior((PhysicalAgent)board);
		B.setParms(generatorParms(parms, reader));
		String name = "";
		for(int i = 0; (i < 10) && (name.length() == 0); i++)
		{
			B.tick(board, Tickable.TICKID_MOB);
			name = B.getStat("QUEST");
		}
		final Quest Q = (name.length() > 0) ? CMLib.quests().fetchQuest(name) : null;
		if(Q == null)
			return null;
		Q.setQuestVar(BOARD_VAR, key(board));
		Q.setQuestVar(POSTED_VAR, "" + GameTime.now());
		return Q;
	}

	private static void retire(final Quest Q)
	{
		if(Q.running())
			Q.stopQuest();
		CMLib.quests().delQuest(Q);
	}

	/**
	 * Brings a board up to date: when its refresh has come (or it has no
	 * jobs), the jobs done or given up go, and new ones come up to COUNT;
	 * with none taken and the board full, the oldest is replaced.
	 * @param board the board
	 * @param parms its parameters
	 * @param reader who reads it, or null
	 * @return its jobs, the oldest first
	 */
	public static synchronized List<Quest> refresh(final Physical board, final String parms, final MOB reader)
	{
		final String key = key(board);
		final int count = Math.max(1, CMParms.getParmInt(parms, "COUNT", 3));
		List<Quest> jobs = quests(key);
		long newest = -1;
		for(final Quest Q : jobs)
			newest = Math.max(newest, posted(Q));
		final boolean due = jobs.isEmpty() || (newest < 0) || ((GameTime.now() - newest) >= refreshMs(parms, reader));
		if(!due)
			return jobs;
		boolean gone = false;
		for(final Quest Q : jobs)
		{
			if(taken(Q) && (!inProgress(Q)))
			{
				retire(Q);
				gone = true;
			}
		}
		jobs = quests(key);
		if((!gone) && (jobs.size() >= count))
		{
			for(final Quest Q : jobs)
			{
				if(!taken(Q))
				{
					retire(Q);
					break;
				}
			}
			jobs = quests(key);
		}
		for(int tries = 0; (jobs.size() < count) && (tries < count * 2); tries++)
		{
			final Quest Q = generate(board, parms, reader);
			if(Q != null)
				jobs.add(Q);
		}
		return quests(key);
	}

	/**
	 * READ or LOOK at a board: brings it up to date and lists the jobs the
	 * reader may take, noted as shown (QUESTS OFFERED) and remembered for
	 * ACCEPT n.
	 * @param board the board
	 * @param parms its parameters
	 * @param reader who reads it
	 * @return the list
	 */
	public static String show(final Physical board, final String parms, final MOB reader)
	{
		final List<Quest> open = new ArrayList<Quest>();
		for(final Quest Q : refresh(board, parms, reader))
		{
			if(Q.canAcceptQuest(reader))
				open.add(Q);
		}
		final List<String> names = new ArrayList<String>();
		final StringBuilder str = new StringBuilder("");
		if(open.isEmpty())
			str.append(CMLib.lang().L("^HThere are no jobs on @x1 for you now.^N\n\r", board.name()));
		else
		{
			str.append(CMLib.lang().L("^HJobs on @x1:^N\n\r", board.name()));
			for(int i = 0; i < open.size(); i++)
			{
				final Quest Q = open.get(i);
				names.add(Q.name());
				QuestRecords.shown(Q, reader);
				str.append("^w").append(i + 1).append(") ").append((Q.displayName().trim().length() > 0) ? Q.displayName() : Q.name()).append("^N\n\r");
				final String instr = Q.instructions();
				if((instr != null) && (instr.trim().length() > 0))
					str.append("     ").append(instr.trim()).append("\n\r");
				if(Q.getStat("REWARDS").length() > 0)
					str.append("     ").append(CMLib.lang().L("Reward: @x1", QuestRewards.describe(Q.getStat("REWARDS")))).append("\n\r");
			}
			str.append(CMLib.lang().L("^HEnter ACCEPT [NUMBER] to take a job.^N\n\r"));
		}
		synchronized(lastListed)
		{
			lastListed.put(reader.Name().toUpperCase(), names);
		}
		return str.toString();
	}

	/**
	 * ACCEPT n: the job numbered n (or named) on the board the character
	 * read last.
	 * @param mob the character
	 * @param which the number or the name
	 * @return the quest, or null
	 */
	public static Quest chosen(final MOB mob, final String which)
	{
		final List<String> names;
		synchronized(lastListed)
		{
			names = lastListed.get(mob.Name().toUpperCase());
		}
		if((names == null) || names.isEmpty() || (which == null))
			return null;
		final String w = which.trim();
		if(CMath.isInteger(w))
		{
			final int n = CMath.s_int(w);
			return ((n >= 1) && (n <= names.size())) ? CMLib.quests().fetchQuest(names.get(n - 1)) : null;
		}
		for(final String name : names)
		{
			final Quest Q = CMLib.quests().fetchQuest(name);
			if((Q != null) && (Q.name().equalsIgnoreCase(w) || Q.displayName().toLowerCase().startsWith(w.toLowerCase())))
				return Q;
		}
		return null;
	}
}
