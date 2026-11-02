package com.planet_ink.coffee_mud.core;

import java.util.*;

import com.planet_ink.coffee_mud.Abilities.interfaces.Ability;
import com.planet_ink.coffee_mud.Behaviors.interfaces.Behavior;
import com.planet_ink.coffee_mud.Common.interfaces.Quest;
import com.planet_ink.coffee_mud.Common.interfaces.ScriptingEngine;
import com.planet_ink.coffee_mud.Items.interfaces.Item;
import com.planet_ink.coffee_mud.Locales.interfaces.Room;
import com.planet_ink.coffee_mud.MOBS.interfaces.MOB;
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
 * Q2: the marks a quest's giver shows in room listings, to each viewer as
 * the quest stands for them: AVAILABLE while they may accept it, ACTIVE once
 * they have, READY when the host's READYFUNC (a FUNCTION_PROG) says they can
 * turn it in.  From Prop_QuestMarker (QUEST=name or *, AVAILABLE=, ACTIVE=,
 * READY=, READYFUNC=), or, with QUESTMARKERS=AUTO, on every running quest's
 * first quest mob and every Prop_QuestGiver host.
 */
public final class QuestMarkers
{
	/** the property's ID, looked for by name (it is of another class loader) */
	public static final String	ID				= "Prop_QuestMarker";
	/** the marks by default */
	public static final String	DEF_AVAILABLE	= "^Y(!)^?";
	public static final String	DEF_ACTIVE		= "^w(?)^?";
	public static final String	DEF_READY		= "^Y(?)^?";

	private QuestMarkers()
	{
	}

	/**
	 * @return true if QUESTMARKERS=AUTO, in coffeemud.ini or world.ini
	 */
	public static boolean auto()
	{
		return CMProps.instance().getStr("QUESTMARKERS").trim().equalsIgnoreCase("AUTO");
	}

	/**
	 * From Sense.getDispositionBlurbs: the mark a thing shows its viewer.
	 * @param seen the mob or item looked at
	 * @param seer who looks
	 * @return " " and the mark, or ""
	 */
	public static String marker(final Physical seen, final MOB seer)
	{
		if((!(seen instanceof PhysicalAgent))
		||(seen instanceof Room)
		||(seer == null)
		||(seen == seer)
		||((seer.playerStats() == null) && seer.isMonster()))
			return "";
		final PhysicalAgent host = (PhysicalAgent)seen;
		final Ability A = host.fetchEffect(ID);
		// by exact key: READY is not READYFUNC
		final Map<String, String> parms;
		final List<Quest> quests;
		if(A != null)
		{
			parms = CMParms.parseEQParms(A.text());
			quests = questsOf(host, parm(parms, "QUEST", "*").trim());
		}
		else
		if(auto())
		{
			parms = new HashMap<String, String>();
			quests = autoQuests(host);
		}
		else
			return "";
		if(quests.isEmpty())
			return "";
		final String readyFunc = parm(parms, "READYFUNC", "").trim();
		boolean available = false;
		boolean active = false;
		for(final Quest Q : quests)
		{
			if((Q == null) || (!Q.running()))
				continue;
			if(accepted(seer, Q))
			{
				if((readyFunc.length() > 0) && ready(host, seer, readyFunc))
					return mark(parm(parms, "READY", DEF_READY));
				active = true;
			}
			else
			if(Q.canAcceptQuest(seer))
				available = true;
		}
		if(available)
			return mark(parm(parms, "AVAILABLE", DEF_AVAILABLE));
		if(active)
			return mark(parm(parms, "ACTIVE", DEF_ACTIVE));
		return "";
	}

	/** Q13: a mark set empty (AVAILABLE= on a quest's turn-in mob) shows nothing */
	private static String mark(final String m)
	{
		return (m.trim().length() == 0) ? "" : (" " + m);
	}

	private static String parm(final Map<String, String> parms, final String key, final String dflt)
	{
		final String v = parms.get(key);
		return (v == null) ? dflt : v;
	}

	/**
	 * @param mob a character
	 * @param Q a quest
	 * @return true if they have accepted it (they carry its script)
	 */
	public static boolean accepted(final MOB mob, final Quest Q)
	{
		for(final Enumeration<ScriptingEngine> e = mob.scripts(); e.hasMoreElements();)
		{
			final ScriptingEngine E = e.nextElement();
			if((E.defaultQuestName() != null) && Q.name().equalsIgnoreCase(E.defaultQuestName()))
				return true;
		}
		return false;
	}

	/**
	 * @param host a quest giver
	 * @param which QUEST=: a quest's name, or * for every running quest it
	 *        has a script from, is first quest mob or item of, or gives
	 *        (Prop_QuestGiver)
	 * @return the quests
	 */
	public static List<Quest> questsOf(final PhysicalAgent host, final String which)
	{
		final List<Quest> found = new ArrayList<Quest>();
		if(!which.equals("*") && (which.length() > 0))
		{
			final Quest Q = CMLib.quests().fetchQuest(which);
			if(Q != null)
				found.add(Q);
			return found;
		}
		for(final Enumeration<Quest> q = CMLib.quests().enumQuests(); q.hasMoreElements();)
		{
			final Quest Q = q.nextElement();
			if((Q == null) || (!Q.running()))
				continue;
			if((Q.getQuestMob(1) == host) || (Q.getQuestItem(1) == host) || hasScriptOf(host, Q) || gives(host, Q))
				found.add(Q);
		}
		return found;
	}

	/**
	 * QUESTMARKERS=AUTO: the running quests a thing is first quest mob of,
	 * and those its Prop_QuestGiver gives.
	 * @param host a mob or item
	 * @return the quests
	 */
	public static List<Quest> autoQuests(final PhysicalAgent host)
	{
		final List<Quest> found = new ArrayList<Quest>();
		for(final Enumeration<Quest> q = CMLib.quests().enumQuests(); q.hasMoreElements();)
		{
			final Quest Q = q.nextElement();
			if((Q == null) || (!Q.running()))
				continue;
			if(((host instanceof MOB) && (Q.getQuestMob(1) == host)) || gives(host, Q))
				found.add(Q);
		}
		return found;
	}

	private static boolean hasScriptOf(final PhysicalAgent host, final Quest Q)
	{
		for(final Enumeration<ScriptingEngine> e = host.scripts(); e.hasMoreElements();)
		{
			final ScriptingEngine E = e.nextElement();
			if((E.defaultQuestName() != null) && Q.name().equalsIgnoreCase(E.defaultQuestName()))
				return true;
		}
		return false;
	}

	private static boolean gives(final PhysicalAgent host, final Quest Q)
	{
		final Ability G = host.fetchEffect("Prop_QuestGiver");
		return (G != null) && Q.name().equalsIgnoreCase(CMParms.getParmStr(G.text(), "QUEST", "").trim());
	}

	/**
	 * READYFUNC: the host's FUNCTION_PROG, run with the viewer as $n.
	 * @param host the giver
	 * @param seer the viewer
	 * @param fn the function's name
	 * @return true if it returned something, not CANCEL
	 */
	public static boolean ready(final PhysicalAgent host, final MOB seer, final String fn)
	{
		final List<ScriptingEngine> engines = new ArrayList<ScriptingEngine>();
		for(final Enumeration<ScriptingEngine> e = host.scripts(); e.hasMoreElements();)
			engines.add(e.nextElement());
		for(final Enumeration<Behavior> b = host.behaviors(); b.hasMoreElements();)
		{
			final Behavior B = b.nextElement();
			if(B instanceof ScriptingEngine)
				engines.add((ScriptingEngine)B);
		}
		final MOB monster = (host instanceof MOB) ? (MOB)host : seer;
		for(final ScriptingEngine E : engines)
		{
			final ScriptingEngine.MPContext ctx = new ScriptingEngine.MPContext(host, monster, seer, host,
				(host instanceof Item) ? (Item)host : null, null, seer.Name(), new Object[ScriptingEngine.SPECIAL_NUM_OBJECTS]);
			final String r = E.callFunc(fn, seer.Name(), ctx);
			if((r != null) && (r.trim().length() > 0) && (!r.trim().equalsIgnoreCase("cancel")))
				return true;
		}
		return false;
	}
}
