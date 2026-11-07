package com.planet_ink.coffee_mud.core;

import java.util.*;

import com.planet_ink.coffee_mud.core.interfaces.Physical;

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
 * Q05: what the optional module families did to a character or an item, with
 * the rule each came from: the recent events (a reaction fired, a reward
 * offered or claimed, a milestone reached, a craft settled, an expedition
 * opened or lost), and the last refused transition with its owner, the rule
 * that refused it and the way to recover.  It is kept in memory since the game
 * began or was restored (the families' own state is what the save keeps), at
 * most 16 events for each of at most 256 owners.  LEDGER shows it.
 */
public final class Provenance
{
	private Provenance()
	{
	}

	public static final int	MAX_EVENTS	= 16;
	public static final int	MAX_OWNERS	= 256;

	/** one event: its game time, the module, the rule it came from, what happened */
	public static final class Event
	{
		public final long	when;
		public final String	module, source, what;

		Event(final String module, final String source, final String what)
		{
			this.when = GameTime.now();
			this.module = module;
			this.source = source;
			this.what = what;
		}

		public String describe()
		{
			return module + " (" + source + "): " + what;
		}
	}

	/** a refused transition: the owner, the rule that refused it, why, and how to recover */
	public static final class Refusal
	{
		public final String	owner, module, source, reason, recovery;

		Refusal(final String owner, final String module, final String source, final String reason, final String recovery)
		{
			this.owner = owner;
			this.module = module;
			this.source = source;
			this.reason = reason;
			this.recovery = recovery;
		}

		public String describe()
		{
			return module + " refused it for " + owner + " (rule " + source + "): " + reason + ". To recover: " + recovery;
		}
	}

	private static final Map<String, Deque<Event>>	events		= new LinkedHashMap<String, Deque<Event>>();
	private static final Map<String, Refusal>		refusals	= new LinkedHashMap<String, Refusal>();

	public static synchronized void reset()
	{
		events.clear();
		refusals.clear();
	}

	private static String key(final Physical P)
	{
		return (P == null) ? "" : InstanceIds.reference(P);
	}

	/** notes an event for an owner (a character or an item) */
	public static synchronized void event(final Physical owner, final String module, final String source, final String what)
	{
		if(owner == null)
			return;
		final String k = key(owner);
		Deque<Event> q = events.remove(k);
		if(q == null)
			q = new ArrayDeque<Event>();
		q.addLast(new Event(module, (source == null) ? "" : source, what));
		while(q.size() > MAX_EVENTS)
			q.removeFirst();
		events.put(k, q);
		while(events.size() > MAX_OWNERS)
			events.remove(events.keySet().iterator().next());
	}

	/** notes a refused transition for an owner */
	public static synchronized void refused(final Physical owner, final String module, final String source, final String reason, final String recovery)
	{
		if(owner == null)
			return;
		final String k = key(owner);
		refusals.remove(k);
		refusals.put(k, new Refusal(owner.Name(), module, (source == null) ? "" : source, reason, recovery));
		while(refusals.size() > MAX_OWNERS)
			refusals.remove(refusals.keySet().iterator().next());
	}

	/** an owner's recent events, oldest first */
	public static synchronized List<Event> recent(final Physical owner)
	{
		final Deque<Event> q = events.get(key(owner));
		return (q == null) ? new ArrayList<Event>() : new ArrayList<Event>(q);
	}

	/** an owner's last refused transition, or null */
	public static synchronized Refusal lastRefusal(final Physical owner)
	{
		return refusals.get(key(owner));
	}
}
