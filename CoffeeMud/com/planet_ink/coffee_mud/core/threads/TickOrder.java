package com.planet_ink.coffee_mud.core.threads;

import java.lang.ref.WeakReference;
import java.util.*;

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
 * The order tickers run in within a tick group: the order their objects first
 * came to the tick system.  It was the objects' hash codes, which are the
 * JVM's identity hashes for most of them: stable for one build, but moved by
 * unrelated changes (one more class used at boot), so a seeded turn-based
 * game did not play the same in the next build.  Each object is given a
 * serial number the first time it is ordered, kept while it lives.
 */
public final class TickOrder
{
	private TickOrder()
	{
	}

	private static final class Entry
	{
		final WeakReference<Object>	ref;
		final long					serial;

		Entry(final Object o, final long serial)
		{
			ref = new WeakReference<Object>(o);
			this.serial = serial;
		}
	}

	private static final Map<Integer, List<Entry>>	byHash	= new HashMap<Integer, List<Entry>>();
	private static long								next	= 0;
	private static int								added	= 0;

	/** an object's serial: the order it first came; the same number while it lives */
	public static synchronized long of(final Object o)
	{
		final Integer h = Integer.valueOf(System.identityHashCode(o));
		List<Entry> list = byHash.get(h);
		if(list == null)
		{
			list = new ArrayList<Entry>(1);
			byHash.put(h, list);
		}
		for(final Iterator<Entry> i = list.iterator(); i.hasNext();)
		{
			final Entry e = i.next();
			final Object x = e.ref.get();
			if(x == o)
				return e.serial;
			if(x == null)
				i.remove();
		}
		final long serial = ++next;
		list.add(new Entry(o, serial));
		if((++added) >= 4096)
			sweep();
		return serial;
	}

	/** forgets the objects that are gone */
	private static void sweep()
	{
		added = 0;
		for(final Iterator<List<Entry>> l = byHash.values().iterator(); l.hasNext();)
		{
			final List<Entry> list = l.next();
			list.removeIf(e -> e.ref.get() == null);
			if(list.isEmpty())
				l.remove();
		}
	}
}
