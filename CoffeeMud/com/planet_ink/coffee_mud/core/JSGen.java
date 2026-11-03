package com.planet_ink.coffee_mud.core;
import com.planet_ink.coffee_mud.core.interfaces.*;
import com.planet_ink.coffee_mud.core.exceptions.CMException;
import com.planet_ink.coffee_mud.Areas.interfaces.*;
import com.planet_ink.coffee_mud.Items.interfaces.*;
import com.planet_ink.coffee_mud.Libraries.interfaces.AreaGenerationLibrary.LayoutManager;
import com.planet_ink.coffee_mud.Libraries.interfaces.AreaGenerationLibrary.LayoutNode;
import com.planet_ink.coffee_mud.Locales.interfaces.*;
import com.planet_ink.coffee_mud.MOBS.interfaces.*;

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
 * G11: JavaScript's gen object, in every scope: generation from a template
 * file, a fresh roll each call (core/GenCalls, as MPGENERATE), the results
 * live objects the script places itself.  vars is a JavaScript object of
 * the template's variables ({level_range: 5}).
 */
public final class JSGen
{
	private final JSCm cm;

	/**
	 * @param cm the scope's cm, for its host's room and the player
	 */
	public JSGen(final JSCm cm)
	{
		this.cm = cm;
	}

	/** a layout's node, for gen.layout */
	public static final class Node
	{
		/** its place in the layout's list */
		public final int		index;
		/** its coordinates */
		public final long		x, y;
		/** its kind: street, leaf, interior ... */
		public final String		type;
		/** the directions it links in */
		public final String[]	exits;

		Node(final int index, final long x, final long y, final String type, final String[] exits)
		{
			this.index = index;
			this.x = x;
			this.y = y;
			this.type = type;
			this.exits = exits;
		}

		@Override
		public String toString()
		{
			return type + "@" + x + "," + y;
		}
	}

	private static Map<String, String> vars(final Object o)
	{
		final Map<String, String> m = new HashMap<String, String>();
		if(o instanceof org.mozilla.javascript.Scriptable)
		{
			final org.mozilla.javascript.Scriptable S = (org.mozilla.javascript.Scriptable)o;
			for(final Object id : S.getIds())
			{
				final Object v = org.mozilla.javascript.ScriptableObject.getProperty(S, id.toString());
				if((v != null) && (v != org.mozilla.javascript.Scriptable.NOT_FOUND))
					m.put(id.toString().toUpperCase(), org.mozilla.javascript.Context.toString(v));
			}
		}
		else
		if(o instanceof Map)
		{
			for(final Object k : ((Map<?, ?>)o).keySet())
				m.put(k.toString().toUpperCase(), String.valueOf(((Map<?, ?>)o).get(k)));
		}
		return m;
	}

	private static RuntimeException fail(final CMException e)
	{
		return new IllegalArgumentException(e.getMessage());
	}

	private Room here()
	{
		return (cm == null) ? null : cm.here();
	}

	private MOB player()
	{
		return JSCm.thePlayer();
	}

	private static int dir(final String d)
	{
		final int code = CMLib.directions().getGoodDirectionCode(d);
		if(code < 0)
			throw new IllegalArgumentException("No direction " + d);
		return code;
	}

	/**
	 * @param file the template file (resources/ or world/resources/)
	 * @param id the ITEM tag's id
	 * @param vars its variables, or null
	 * @return the items, placed nowhere
	 */
	public Item[] item(final String file, final String id, final Object vars)
	{
		try
		{
			final List<Item> L = GenCalls.items(file, id, vars(vars), player(), here());
			return L.toArray(new Item[L.size()]);
		}
		catch(final CMException e)
		{
			throw fail(e);
		}
	}

	/**
	 * @param file the template file
	 * @param id the MOB tag's id
	 * @param vars its variables, or null
	 * @return the mobs, not yet brought to life
	 */
	public MOB[] mob(final String file, final String id, final Object vars)
	{
		try
		{
			final List<MOB> L = GenCalls.mobs(file, id, vars(vars), player(), here());
			return L.toArray(new MOB[L.size()]);
		}
		catch(final CMException e)
		{
			throw fail(e);
		}
	}

	/**
	 * @param file the template file
	 * @param id the STRING tag's id
	 * @param vars its variables, or null
	 * @return the text
	 */
	public String string(final String file, final String id, final Object vars)
	{
		try
		{
			return GenCalls.string(file, id, vars(vars), player(), here());
		}
		catch(final CMException e)
		{
			throw fail(e);
		}
	}

	/**
	 * A room, linked off the host's room in a direction it has free.
	 * @param file the template file
	 * @param id the ROOM tag's id
	 * @param dir the direction
	 * @return the room
	 */
	public Room room(final String file, final String id, final String dir)
	{
		try
		{
			return GenCalls.room(file, id, here(), dir(dir), null, player());
		}
		catch(final CMException e)
		{
			throw fail(e);
		}
	}

	/**
	 * An area, its entrance linked off the host's room in a direction it has free.
	 * @param file the template file
	 * @param id the AREA tag's id
	 * @param dir the direction
	 * @param seed its seed (a number, GAME ...), or null for a new one
	 * @param vars its variables, or null
	 * @return the area
	 */
	public Area area(final String file, final String id, final String dir, final Object seed, final Object vars)
	{
		try
		{
			return GenCalls.area(file, id, here(), dir(dir), vars(vars), (seed == null) ? null : org.mozilla.javascript.Context.toString(seed), player());
		}
		catch(final CMException e)
		{
			throw fail(e);
		}
	}

	/**
	 * A layout's nodes, as GENERATE would lay out an area of that size with that seed.
	 * @param name the layout (TREE, MAZE ...)
	 * @param size how many rooms
	 * @param seed the seed, or null for a new one
	 * @return the nodes: index, x, y, type, exits
	 */
	public Node[] layout(final String name, final int size, final Object seed)
	{
		final long s = GenSeed.pick((seed == null) ? null : org.mozilla.javascript.Context.toString(seed), null);
		final Long before = GenSeed.begin(s);
		try
		{
			// made inside the seeded run: a layout seeds its own generator as it is made
			final LayoutManager LM = CMLib.percolator().getLayoutManager(name);
			if(LM == null)
				throw new IllegalArgumentException("No layout " + name);
			final List<LayoutNode> nodes = LM.generate(size, Directions.NORTH);
			final Node[] out = new Node[(nodes == null) ? 0 : nodes.size()];
			for(int i = 0; i < out.length; i++)
			{
				final LayoutNode n = nodes.get(i);
				final List<String> exits = new ArrayList<String>();
				for(final Integer d : new TreeSet<Integer>(n.links().keySet()))
					exits.add(CMLib.directions().getDirectionName(d.intValue()).toLowerCase());
				out[i] = new Node(i, (n.coord().length > 0) ? n.coord()[0] : 0, (n.coord().length > 1) ? n.coord()[1] : 0,
								  n.type().name().toLowerCase(), exits.toArray(new String[exits.size()]));
			}
			return out;
		}
		finally
		{
			GenSeed.end(before);
		}
	}
}
