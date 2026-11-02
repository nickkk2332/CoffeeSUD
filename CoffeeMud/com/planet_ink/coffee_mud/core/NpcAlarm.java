package com.planet_ink.coffee_mud.core;

import java.util.*;

import com.planet_ink.coffee_mud.Abilities.interfaces.Ability;
import com.planet_ink.coffee_mud.Behaviors.interfaces.Behavior;
import com.planet_ink.coffee_mud.Common.interfaces.CMMsg;
import com.planet_ink.coffee_mud.Locales.interfaces.Room;
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
 * N4, N5: an alarm raised by an NPC: Sleeper's ALARM, and Sentry's.  The
 * spec is "RADIUS=6 MASK=... MSG=...": a shout in the alarm room (a NOISE
 * message, which EXECMSG_PROG can catch), and every NPC within RADIUS rooms
 * that fits MASK is woken (a Sleeper stays up a while) and Alerted for the
 * raiser's Sentry ALERTTICKS= (40): it heads for the alarm, or the
 * intruder, and attacks them and their party.  With no spec, the raiser's
 * own Sentry ALARM= is used, or the defaults.
 */
public final class NpcAlarm
{
	/** how many rooms an alarm carries, by default */
	public static final int		DEFAULT_RADIUS	= 6;
	/** what is shouted, by default */
	public static final String	DEFAULT_MSG		= "Intruders!";
	/** how long the roused stay alert, by default */
	public static final int		DEFAULT_ALERT	= 40;

	private NpcAlarm()
	{
	}

	/** an alarm's settings */
	public static final class Spec
	{
		/** rooms it carries */
		public int		radius	= DEFAULT_RADIUS;
		/** who hears it, or "" for every NPC */
		public String	mask	= "";
		/** what is shouted */
		public String	msg		= DEFAULT_MSG;
	}

	/**
	 * @param spec "RADIUS=6 MASK=... MSG=..."
	 * @return it read
	 */
	public static Spec parse(final String spec)
	{
		final Spec S = new Spec();
		if((spec == null) || (spec.trim().length() == 0))
			return S;
		final String up = spec.toUpperCase();
		S.radius = CMParms.getParmInt(spec, "RADIUS", DEFAULT_RADIUS);
		final int m = up.indexOf("MSG=");
		final int k = up.indexOf("MASK=");
		// MSG= and MASK= run to the next of them, or the end, so they may have spaces
		if(m >= 0)
			S.msg = spec.substring(m + 4, ((k > m) ? k : spec.length())).trim();
		if(k >= 0)
			S.mask = spec.substring(k + 5, ((m > k) ? m : spec.length())).replaceAll("(?i)\\s*RADIUS=\\d+\\s*", " ").trim();
		if((S.msg.length() > 1) && S.msg.startsWith("\"") && S.msg.endsWith("\""))
			S.msg = S.msg.substring(1, S.msg.length() - 1);
		if((S.mask.length() > 1) && S.mask.startsWith("\"") && S.mask.endsWith("\""))
			S.mask = S.mask.substring(1, S.mask.length() - 1);
		return S;
	}

	/**
	 * The raiser's own alarm spec: its Sentry's ALARM=, or "".
	 * @param raiser the NPC
	 * @return the spec
	 */
	public static String specOf(final MOB raiser)
	{
		for(final Enumeration<Behavior> b = raiser.behaviors(); b.hasMoreElements();)
		{
			final Behavior B = b.nextElement();
			if((B != null) && B.ID().equals("Sentry"))
				return CMParms.getParmStr(B.getParms(), "ALARM", "");
		}
		return "";
	}

	/**
	 * @param raiser the NPC
	 * @return its Sentry's ALERTTICKS=, or the default
	 */
	public static int alertTicksOf(final MOB raiser)
	{
		for(final Enumeration<Behavior> b = raiser.behaviors(); b.hasMoreElements();)
		{
			final Behavior B = b.nextElement();
			if((B != null) && B.ID().equals("Sentry"))
				return CMParms.getParmInt(B.getParms(), "ALERTTICKS", DEFAULT_ALERT);
		}
		return DEFAULT_ALERT;
	}

	/**
	 * Raises an alarm.
	 * @param raiser who raises it
	 * @param intruder who it is for, or null
	 * @param spec "RADIUS=6 MASK=... MSG=...", or null for the raiser's own
	 * @return the NPCs it reached
	 */
	public static List<MOB> raise(final MOB raiser, final MOB intruder, final String spec)
	{
		final List<MOB> reached = new ArrayList<MOB>();
		final Room here = (raiser == null) ? null : raiser.location();
		if(here == null)
			return reached;
		final Spec S = parse((spec == null) ? specOf(raiser) : spec);
		here.show(raiser, null, CMMsg.MSG_NOISE, "<S-NAME> shout(s) '" + S.msg + "'");
		final List<Room> rooms = CMLib.tracking().getRadiantRooms(here, CMLib.tracking().newFlags(), Math.max(0, S.radius));
		if(!rooms.contains(here))
			rooms.add(0, here);
		for(final Room R : rooms)
		{
			for(final Enumeration<MOB> m = R.inhabitants(); m.hasMoreElements();)
			{
				final MOB M = m.nextElement();
				if((M == null) || (M == raiser) || (M.playerStats() != null) || M.amDead())
					continue;
				if((S.mask.length() > 0) && (!CMLib.masking().maskCheck(S.mask, M, true)))
					continue;
				rouse(M);
				alert(M, here, intruder, alertTicksOf(raiser));
				reached.add(M);
			}
		}
		return reached;
	}

	/**
	 * Alerts an NPC: for a while it heads for the alarm, or the intruder, and
	 * attacks them.
	 * @param M the NPC
	 * @param where the alarm room
	 * @param intruder who it is for, or null
	 * @param ticks how long
	 */
	public static void alert(final MOB M, final Room where, final MOB intruder, final int ticks)
	{
		if((ticks <= 0) || (where == null))
			return;
		final Ability old = M.fetchEffect("Alerted");
		if(old != null)
			old.unInvoke();
		final Ability A = CMClass.getAbility("Alerted");
		if(A == null)
			return;
		A.setMiscText("ROOM=\"" + CMLib.map().getExtendedRoomID(where) + "\" INTRUDER=\"" + ((intruder == null) ? "" : intruder.Name()) + "\"");
		A.startTickDown(M, M, ticks);
	}

	/**
	 * Wakes an NPC, and keeps its Sleeper up a while.
	 * @param M the NPC
	 */
	public static void rouse(final MOB M)
	{
		for(final Enumeration<Behavior> b = M.behaviors(); b.hasMoreElements();)
		{
			final Behavior B = b.nextElement();
			if((B != null) && B.ID().equals("Sleeper"))
				B.setStat("ROUSED", "1");
		}
		if(CMLib.flags().isSleeping(M))
			CMLib.commands().postStand(M, true, false);
	}
}
