package com.planet_ink.coffee_mud.core;

import java.lang.ref.WeakReference;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

import com.planet_ink.coffee_mud.Abilities.interfaces.Ability;
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
 * SC4: script variables kept on one object, not on its name. A script names
 * the object as the host @code (MPSETVAR @$i BRIBED 1); the engine turns that
 * into a host of this form, which stands for the object while the game runs.
 * The values are on the object, in its ExtraData effect, so a save keeps
 * them with it, and they go with that copy only.
 */
public final class ObjectVars
{
	/** how the hosts of objects begin */
	public static final String	PREFIX	= "@#";

	/** the ExtraData entry the values are kept in */
	public static final String	STAT	= "SCRIPTVARS";

	/** the variable that keeps a mob's values when it comes back to life */
	public static final String	PERSIST	= "PERSIST";

	private static final Map<String, WeakReference<Physical>> objects = new ConcurrentHashMap<String, WeakReference<Physical>>();

	private ObjectVars()
	{
	}

	/**
	 * The host that stands for an object.
	 * @param P the object
	 * @return its host
	 */
	public static String hostOf(final Physical P)
	{
		final String base = PREFIX + Integer.toHexString(System.identityHashCode(P));
		for(int i = 0; ; i++)
		{
			final String host = (i == 0) ? base : (base + "." + i);
			final WeakReference<Physical> ref = objects.get(host);
			final Physical had = (ref == null) ? null : ref.get();
			if(had == P)
				return host;
			if(had == null)
			{
				if(objects.size() > 5000)
					purge();
				objects.put(host, new WeakReference<Physical>(P));
				return host;
			}
		}
	}

	private static void purge()
	{
		for(final Iterator<Map.Entry<String, WeakReference<Physical>>> i = objects.entrySet().iterator(); i.hasNext();)
		{
			if(i.next().getValue().get() == null)
				i.remove();
		}
	}

	/**
	 * Whether a host is an object's.
	 * @param host the host
	 * @return true if it is
	 */
	public static boolean isHost(final String host)
	{
		return (host != null) && host.startsWith(PREFIX);
	}

	/**
	 * The object a host stands for.
	 * @param host the host
	 * @return the object, or null if it is gone
	 */
	public static Physical objectOf(final String host)
	{
		final WeakReference<Physical> ref = (host == null) ? null : objects.get(host);
		final Physical P = (ref == null) ? null : ref.get();
		return ((P == null) || P.amDestroyed()) ? null : P;
	}

	/**
	 * A readable name for an object's host, for traces and VARS.
	 * @param host the host
	 * @return @name, or the host as it is
	 */
	public static String describe(final String host)
	{
		final Physical P = objectOf(host);
		return (P == null) ? host : ("@" + P.Name());
	}

	/**
	 * The variables kept on an object; changes to the map are kept on it.
	 * @param host the object's host
	 * @return the map, or null if the object is gone
	 */
	public static Map<String, String> vars(final String host)
	{
		final Physical P = objectOf(host);
		return (P == null) ? null : new VarMap(P);
	}

	/**
	 * The variables kept on an object; changes to the map are kept on it.
	 * @param P the object
	 * @return the map
	 */
	public static Map<String, String> vars(final Physical P)
	{
		return new VarMap(P);
	}

	/**
	 * One of an object's variables.
	 * @param host the object's host
	 * @param var the variable
	 * @return its value, or ""
	 */
	public static String get(final String host, final String var)
	{
		final Physical P = objectOf(host);
		if(P == null)
			return "";
		final String val = read(P).get(var.toUpperCase().trim());
		return (val == null) ? "" : val;
	}

	/**
	 * Before a mob comes back to life, which takes its effects: its values, to
	 * put back after, when PERSIST is set.
	 * @param P the mob
	 * @return the values, or null
	 */
	public static Map<String, String> persisting(final Physical P)
	{
		final Map<String, String> data = read(P);
		final String keep = data.get(PERSIST);
		if((keep != null) && (keep.length() > 0) && (!keep.equals("0")) && (!keep.equalsIgnoreCase("false")))
			return data;
		return null;
	}

	/**
	 * After a mob came back to life: puts back the values PERSIST kept.
	 * @param P the mob
	 * @param kept what persisting gave
	 */
	public static void reborn(final Physical P, final Map<String, String> kept)
	{
		if((kept != null) && (!kept.isEmpty()))
			write(P, kept);
	}

	private static Map<String, String> read(final Physical P)
	{
		final Ability A = (P == null) ? null : P.fetchEffect("ExtraData");
		if((A == null) || (!A.isStat(STAT)))
			return new TreeMap<String, String>();
		final Map<String, String> data = CMLib.xml().fromXML(A.getStat(STAT));
		return (data == null) ? new TreeMap<String, String>() : new TreeMap<String, String>(data);
	}

	private static void write(final Physical P, final Map<String, String> data)
	{
		Ability A = P.fetchEffect("ExtraData");
		if(data.isEmpty())
		{
			if((A != null) && A.isStat(STAT))
				A.setStat(STAT, null);
			return;
		}
		if(A == null)
		{
			A = CMClass.getAbility("ExtraData");
			if(A == null)
				return;
			P.addNonUninvokableEffect(A);
		}
		A.setStat(STAT, CMLib.xml().toXML(data));
	}

	/**
	 * An object's variables as a map that keeps its changes on the object.
	 */
	private static final class VarMap extends AbstractMap<String, String>
	{
		private final Physical				P;
		private final Map<String, String>	data;

		private VarMap(final Physical P)
		{
			this.P = P;
			this.data = read(P);
		}

		@Override
		public Set<Map.Entry<String, String>> entrySet()
		{
			return Collections.unmodifiableMap(data).entrySet();
		}

		@Override
		public String get(final Object key)
		{
			return data.get(key);
		}

		@Override
		public boolean containsKey(final Object key)
		{
			return data.containsKey(key);
		}

		@Override
		public int size()
		{
			return data.size();
		}

		@Override
		public String put(final String key, final String value)
		{
			final String old = data.put(key, value);
			write(P, data);
			return old;
		}

		@Override
		public String remove(final Object key)
		{
			final String old = data.remove(key);
			if(old != null)
				write(P, data);
			return old;
		}
	}
}
