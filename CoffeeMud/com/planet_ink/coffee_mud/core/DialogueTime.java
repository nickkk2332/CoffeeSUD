package com.planet_ink.coffee_mud.core;

import java.util.*;

import com.planet_ink.coffee_mud.Behaviors.interfaces.Behavior;
import com.planet_ink.coffee_mud.Common.interfaces.CMMsg;
import com.planet_ink.coffee_mud.Common.interfaces.ScriptingEngine;
import com.planet_ink.coffee_mud.Items.interfaces.Item;
import com.planet_ink.coffee_mud.Locales.interfaces.Room;
import com.planet_ink.coffee_mud.MOBS.interfaces.MOB;
import com.planet_ink.coffee_mud.core.interfaces.PhysicalAgent;

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
 * N10: NPC replies at once.  With DIALOGUETIME=INSTANT (the default in
 * turn-based time; TIMEMODE DIALOGUE INSTANT|TICKS sets it), after each
 * command of a player's character the replies it drew in the room are run
 * before the next prompt, without moving the clock: what scripts queued for
 * SPEECH_PROG, GIVE_PROG, BRIBE_PROG, SOCIAL_PROG and TALK_PROG, and what
 * MudChat queued.  A long WAIT stops when an NPC speaks to the one waiting.
 */
public final class DialogueTime
{
	private DialogueTime()
	{
	}

	/** how many times a reply may draw a reply, in one flush */
	private static final int	ROUNDS	= 3;

	private static final Map<MOB, long[]>	spoken	= new WeakHashMap<MOB, long[]>();

	/**
	 * @return the setting: INSTANT, TICKS, or "" for the default
	 */
	public static String setting()
	{
		final String s = CMProps.instance().getStr("DIALOGUETIME");
		return (s == null) ? "" : s.trim().toUpperCase();
	}

	/**
	 * @return true if replies come at once: DIALOGUETIME=INSTANT, or by
	 *         default in turn-based time
	 */
	public static boolean instant()
	{
		final String s = setting();
		if(s.equals("INSTANT"))
			return true;
		if(s.equals("TICKS"))
			return false;
		return GameTime.isTurnBased();
	}

	/**
	 * Sets DIALOGUETIME for this game.
	 * @param value INSTANT, TICKS, or "" for the default
	 */
	public static void set(final String value)
	{
		CMProps.instance().setProperty("DIALOGUETIME", (value == null) ? "" : value.trim().toUpperCase());
	}

	/**
	 * After a command of a player's character: the replies it drew in its
	 * room, at once, if replies come at once.
	 * @param speaker the character
	 * @return how many replies ran
	 */
	public static int flush(final MOB speaker)
	{
		if((speaker == null) || (!instant()))
			return 0;
		final Room R = speaker.location();
		if(R == null)
			return 0;
		int n = 0;
		for(int round = 0; round < ROUNDS; round++)
		{
			final List<PhysicalAgent> here = new ArrayList<PhysicalAgent>();
			here.add(R);
			for(final Enumeration<MOB> m = R.inhabitants(); m.hasMoreElements();)
			{
				final MOB M = m.nextElement();
				if(M != null)
					here.add(M);
			}
			for(final Enumeration<Item> i = R.items(); i.hasMoreElements();)
			{
				final Item I = i.nextElement();
				if((I != null) && (I.container() == null))
					here.add(I);
			}
			int r = 0;
			for(final PhysicalAgent P : here)
				r += repliesOf(P);
			n += r;
			if(r == 0)
				break;
		}
		return n;
	}

	private static int repliesOf(final PhysicalAgent P)
	{
		int n = 0;
		for(final Enumeration<ScriptingEngine> s = P.scripts(); s.hasMoreElements();)
		{
			final ScriptingEngine S = s.nextElement();
			if(S != null)
				n += S.runRepliesNow();
		}
		for(final Enumeration<Behavior> b = P.behaviors(); b.hasMoreElements();)
		{
			final Behavior B = b.nextElement();
			if(B instanceof ScriptingEngine)
				n += ((ScriptingEngine)B).runRepliesNow();
			else
			if(B != null)
				n += B.repliesNow(P);
		}
		return n;
	}

	/**
	 * A message was heard by a mob: one an NPC said to it is counted, so that
	 * a WAIT can stop.
	 * @param host the mob hearing it
	 * @param msg the message
	 */
	public static void heard(final MOB host, final CMMsg msg)
	{
		if((msg.targetMinor() != CMMsg.TYP_SPEAK)
		|| (msg.target() != host)
		|| (msg.source() == host)
		|| (msg.source() == null)
		|| (msg.source().playerStats() != null)
		|| (host.playerStats() == null))
			return;
		synchronized(spoken)
		{
			final long[] c = spoken.get(host);
			if(c == null)
				spoken.put(host, new long[] { 1 });
			else
				c[0]++;
		}
	}

	/**
	 * @param mob a character
	 * @return how many times an NPC has spoken to them
	 */
	public static long spokenTo(final MOB mob)
	{
		synchronized(spoken)
		{
			final long[] c = spoken.get(mob);
			return (c == null) ? 0 : c[0];
		}
	}
}
