package com.planet_ink.coffee_mud.core;

import java.util.*;

import com.planet_ink.coffee_mud.Abilities.interfaces.Ability;
import com.planet_ink.coffee_mud.Common.interfaces.Quest;
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
 * Q11: a character's own deadline for a quest (MPQTIMER who quest 6
 * mudhours), kept in their quest journal (Q3) and so saved with them, and
 * counted on the game clock, so a pause or turn-based time holds it.  When
 * it comes, QUESTTIMEOUT_PROG quest fires on the character's scripts for the
 * quest; with no such trigger there, the quest fails for them, EXPIRED.
 */
public final class QuestTimers
{
	private static volatile Ticker	ticker	= null;

	private QuestTimers()
	{
	}

	/** the ticker runs from the game's start, and from the first MPQTIMER */
	public static synchronized void startTicking()
	{
		if(ticker == null)
		{
			ticker = new Ticker();
			CMLib.threads().startTickDown(ticker, Tickable.TICKID_MISCELLANEOUS, 1);
		}
	}

	/**
	 * MPQTIMER who quest game-time|OFF.
	 * @param mob the character
	 * @param quest the quest's name
	 * @param time as 6 MUDHOURS, or a number of ticks, or OFF
	 * @return null, or what was wrong
	 */
	public static String set(final MOB mob, final String quest, final String time)
	{
		if((mob == null) || (quest == null) || (quest.length() == 0))
			return "no character or quest";
		if(time.trim().equalsIgnoreCase("OFF"))
		{
			QuestJournal.setTimer(mob, quest, -1);
			return null;
		}
		final int ticks;
		try
		{
			ticks = CMLib.time().parseTickExpression(CMLib.time().localClock(mob), time.trim());
		}
		catch(final CMException e)
		{
			return "not a game time: " + time;
		}
		if(ticks <= 0)
			return "not a game time: " + time;
		QuestJournal.setTimer(mob, quest, GameTime.now() + ((long)ticks * CMProps.getTickMillis()));
		startTicking();
		return null;
	}

	/**
	 * @param mob a character
	 * @param quest a quest's name
	 * @return the ticks left on their deadline for it, or -1 if they have none
	 */
	public static long ticksLeft(final MOB mob, final String quest)
	{
		final long due = QuestJournal.timer(mob, quest);
		if(due < 0)
			return -1;
		return Math.max(0, due - GameTime.now()) / Math.max(1, CMProps.getTickMillis());
	}

	/** each tick: the deadlines that have come */
	public static void tick()
	{
		final long now = GameTime.now();
		for(final Enumeration<MOB> m = CMLib.players().players(); m.hasMoreElements();)
		{
			final MOB M = m.nextElement();
			if((M == null) || (M.location() == null) || M.amDestroyed())
				continue;
			final Ability A = M.fetchEffect(QuestJournal.ID);
			if((A == null) || (A.text().indexOf("\"t\":{\"") < 0))
				continue;
			for(final Map.Entry<String, Long> e : QuestJournal.read(M).timers.entrySet())
			{
				if(e.getValue().longValue() <= now)
				{
					QuestJournal.setTimer(M, e.getKey(), -1);
					expire(M, e.getKey());
				}
			}
		}
	}

	/**
	 * When a character's quest ends for them (won, failed, dropped), their
	 * deadline for it goes too.
	 * @param quest the quest's name
	 * @param who the character's name
	 */
	public static void left(final String quest, final String who)
	{
		final MOB mob = ((quest == null) || (who == null)) ? null : CMLib.players().getPlayerAllHosts(who);
		if((mob != null) && (QuestJournal.timer(mob, quest) >= 0))
			QuestJournal.setTimer(mob, quest, -1);
	}

	/**
	 * A character's deadline came: QUESTTIMEOUT_PROG on their scripts for
	 * the quest, or, with none, the quest fails for them, EXPIRED.
	 * @param mob the character
	 * @param quest the quest's name
	 */
	public static void expire(final MOB mob, final String quest)
	{
		final Quest Q = CMLib.quests().fetchQuest(quest);
		final String name = (Q != null) ? Q.name() : quest;
		if(ScriptEvents.questTimeout(mob, name))
			return;
		if(Q != null)
			QuestRecords.fail(Q, mob, QuestRecords.EXPIRED, "EXPIRED");
		else
			QuestRecords.ended(name, mob.Name(), QuestRecords.EXPIRED, "EXPIRED");
	}

	private static final class Ticker implements Tickable
	{
		@Override
		public String ID()
		{
			return "QuestTimers";
		}

		@Override
		public String name()
		{
			return ID();
		}

		@Override
		public CMObject newInstance()
		{
			return this;
		}

		@Override
		public CMObject copyOf()
		{
			return this;
		}

		@Override
		public void initializeClass()
		{
		}

		@Override
		public int compareTo(final CMObject o)
		{
			return (o == this) ? 0 : 1;
		}

		@Override
		public int getTickStatus()
		{
			return Tickable.STATUS_NOT;
		}

		@Override
		public boolean tick(final Tickable ticking, final int tickID)
		{
			try
			{
				QuestTimers.tick();
			}
			catch(final RuntimeException e)
			{
				Log.errOut("QuestTimers", e);
			}
			return true;
		}
	}
}
