package com.planet_ink.coffee_mud.core;

import java.util.*;

import com.planet_ink.coffee_mud.core.interfaces.*;
import com.planet_ink.coffee_mud.Areas.interfaces.Area;
import com.planet_ink.coffee_mud.Commands.interfaces.Command;
import com.planet_ink.coffee_mud.Common.interfaces.PhyStats;
import com.planet_ink.coffee_mud.Exits.interfaces.Exit;
import com.planet_ink.coffee_mud.Items.interfaces.Item;
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
 * B12: a scratch area for builders, the Sandbox: a few bare rooms in a row,
 * the area marked unsavable (so the world files and the database never have
 * it), made when first entered.  SANDBOX ENTER and LEAVE go in and back,
 * CLEAR empties it, PREVIEW name puts a copy of a catalog mob or item, or a
 * template, in it and shows it, gone when the builder leaves.  GENERATE ...
 * SANDBOX generates there, NOSAVE, and an area it makes is unsavable too.
 */
public final class Sandbox
{
	private Sandbox()
	{
	}

	/** the sandbox area's name */
	public static final String	AREA	= "Sandbox";
	/** how many rooms it has */
	public static final int		ROOMS	= 4;

	private static final Map<MOB, String>	returnTo	= Collections.synchronizedMap(new WeakHashMap<MOB, String>());
	private static final List<Physical>		previews	= Collections.synchronizedList(new ArrayList<Physical>());
	private static final List<String>		generated	= Collections.synchronizedList(new ArrayList<String>());

	private static void unsavable(final Physical P)
	{
		P.basePhyStats().setDisposition(P.basePhyStats().disposition() | PhyStats.IS_UNSAVABLE);
		P.recoverPhyStats();
	}

	/**
	 * The sandbox area.
	 * @param make true to make it if there is none
	 * @return the area, or null
	 */
	public static Area area(final boolean make)
	{
		Area A = CMLib.map().getArea(AREA);
		if((A != null) || (!make))
			return A;
		A = CMClass.getAreaType("StdArea");
		A.setName(AREA);
		unsavable(A);
		CMLib.map().addArea(A);
		Room prev = null;
		for(int i = 0; i < ROOMS; i++)
		{
			final Room R = CMClass.getLocale("StdRoom");
			R.setRoomID(AREA + "#" + i);
			R.setArea(A);
			R.setDisplayText("Sandbox " + i);
			R.setDescription("A bare room for trying things out.  Nothing here is saved.");
			unsavable(R);
			if(prev != null)
			{
				prev.rawDoors()[Directions.EAST] = R;
				prev.setRawExit(Directions.EAST, CMClass.getExit("Open"));
				R.rawDoors()[Directions.WEST] = prev;
				R.setRawExit(Directions.WEST, CMClass.getExit("Open"));
			}
			prev = R;
		}
		return A;
	}

	/**
	 * The sandbox's first room.
	 * @return the room
	 */
	public static Room first()
	{
		area(true);
		return CMLib.map().getRoom(AREA + "#0");
	}

	/**
	 * Whether a builder is in the sandbox.
	 * @param mob the builder
	 * @return true if so
	 */
	public static boolean isIn(final MOB mob)
	{
		final Room R = mob.location();
		return (R != null) && (R.getArea() != null) && R.getArea().Name().equals(AREA);
	}

	/**
	 * SANDBOX ENTER: the builder goes to the sandbox, to come back to where
	 * they were.
	 * @param mob the builder
	 * @return what was done
	 */
	public static String enter(final MOB mob)
	{
		if(isIn(mob))
			return "You are in the sandbox already.";
		final Room R = mob.location();
		if(R != null)
			returnTo.put(mob, CMLib.map().getExtendedRoomID(R));
		first().bringMobHere(mob, true);
		return "You are in the sandbox, " + ROOMS + " bare rooms in a row; nothing here is saved.  SANDBOX LEAVE takes you back.";
	}

	/**
	 * SANDBOX LEAVE: the builder goes back, and the previews are gone.
	 * @param mob the builder
	 * @return what was done
	 */
	public static String leave(final MOB mob)
	{
		final int gone = dropPreviews();
		if(!isIn(mob))
			return "You are not in the sandbox." + ((gone > 0) ? (" " + gone + " previews are gone.") : "");
		final String id = returnTo.remove(mob);
		Room back = (id == null) ? null : CMLib.map().getRoom(id);
		if(back == null)
			back = mob.getStartRoom();
		if((back != null) && (!back.getArea().Name().equals(AREA)))
			back.bringMobHere(mob, true);
		return "You are back" + ((back == null) ? "" : (" in " + CMLib.map().getExtendedRoomID(back))) + ((gone > 0) ? ("; " + gone + " previews are gone.") : ".");
	}

	private static int dropPreviews()
	{
		int n = 0;
		synchronized(previews)
		{
			for(final Physical P : previews)
			{
				if(!P.amDestroyed())
				{
					P.destroy();
					n++;
				}
			}
			previews.clear();
		}
		return n;
	}

	/**
	 * SANDBOX CLEAR: the sandbox's mobs and items gone, and the areas
	 * GENERATE ... SANDBOX made.
	 * @return what was done
	 */
	public static String clear()
	{
		dropPreviews();
		int n = 0;
		final Area A = area(false);
		if(A != null)
		{
			for(final Enumeration<Room> r = A.getProperMap(); r.hasMoreElements();)
			{
				final Room R = r.nextElement();
				if(R == null)
					continue;
				for(final MOB M : new ArrayList<MOB>(new com.planet_ink.coffee_mud.core.collections.XVector<MOB>(R.inhabitants())))
				{
					if((M != null) && (M.playerStats() == null))
					{
						M.destroy();
						n++;
					}
				}
				for(final Item I : new ArrayList<Item>(new com.planet_ink.coffee_mud.core.collections.XVector<Item>(R.items())))
				{
					if(I != null)
					{
						I.destroy();
						n++;
					}
				}
			}
		}
		int areas = 0;
		synchronized(generated)
		{
			for(final String name : generated)
			{
				final Area G = CMLib.map().getArea(name);
				if(G != null)
				{
					CMLib.map().obliterateMapArea(G);
					areas++;
				}
			}
			generated.clear();
		}
		return "The sandbox is cleared: " + n + " mobs and items" + ((areas > 0) ? (", and " + areas + " generated areas") : "") + " gone.";
	}

	/**
	 * SANDBOX PREVIEW: a copy of a catalog mob or item, or a template, put in
	 * the sandbox and shown, gone when the builder leaves.
	 * @param mob the builder
	 * @param name the catalog name or template
	 * @return what it is, or why there is none
	 */
	public static String preview(final MOB mob, final String name)
	{
		Physical P = CMLib.catalog().getCatalogMob(name);
		if(P == null)
			P = CMLib.catalog().getCatalogItem(name);
		if(P == null)
		{
			final Object T = CMLib.catalog().getBuilderTemplateObject(mob.Name(), name.toUpperCase().trim());
			if((T instanceof MOB) || (T instanceof Item))
				P = (Physical)T;
		}
		if(P == null)
			return "There is no catalog mob or item, or template, '" + name + "'.";
		P = (Physical)P.copyOf();
		if(!isIn(mob))
			enter(mob);
		final Room R = mob.location();
		if(P instanceof MOB)
			((MOB)P).bringToLife(R, true);
		else
			R.addItem((Item)P);
		unsavable(P);
		previews.add(P);
		final StringBuilder str = new StringBuilder(P.name() + " (" + P.ID() + "), level " + P.phyStats().level() + ", in the sandbox:\n\r");
		str.append(CMStrings.removeColors(P.displayText())).append("\n\r");
		if(P.description().length() > 0)
			str.append(CMStrings.removeColors(P.description())).append("\n\r");
		if(P instanceof MOB)
		{
			final MOB M = (MOB)P;
			final List<String> worn = new ArrayList<String>();
			for(final Enumeration<Item> i = M.items(); i.hasMoreElements();)
			{
				final Item I = i.nextElement();
				if(I != null)
					worn.add(I.name() + (I.amWearingAt(Item.IN_INVENTORY) ? "" : " (worn)"));
			}
			str.append("Carries: ").append(worn.isEmpty() ? "nothing" : String.join(", ", worn)).append("\n\r");
			if(M instanceof ShopKeeper)
			{
				final List<String> wares = new ArrayList<String>();
				for(final Iterator<Environmental> e = ((ShopKeeper)M).getShop().getStoreInventory(); e.hasNext();)
					wares.add(e.next().name());
				str.append("Sells: ").append(wares.isEmpty() ? "nothing" : String.join(", ", wares)).append("\n\r");
			}
		}
		str.append("STAT ").append(P.name()).append(" shows the rest; SANDBOX LEAVE takes it away.");
		return str.toString();
	}

	/**
	 * GENERATE ... SANDBOX: the generator run in the sandbox, NOSAVE, and an
	 * area it makes marked unsavable, for SANDBOX CLEAR to take away.
	 * @param mob the builder
	 * @param generate the GENERATE command
	 * @param words its words, NOSAVE among them
	 * @param metaFlags as given
	 * @return what was done
	 */
	public static String generate(final MOB mob, final Command generate, final List<String> words, final int metaFlags)
	{
		if(!isIn(mob))
			enter(mob);
		final Set<String> before = new HashSet<String>();
		for(final Enumeration<Area> a = CMLib.map().areas(); a.hasMoreElements();)
			before.add(a.nextElement().Name());
		try
		{
			generate.execute(mob, words, metaFlags);
		}
		catch(final java.io.IOException e)
		{
			return e.getMessage();
		}
		final List<String> made = new ArrayList<String>();
		for(final Enumeration<Area> a = CMLib.map().areas(); a.hasMoreElements();)
		{
			final Area A = a.nextElement();
			if(!before.contains(A.Name()))
			{
				unsavable(A);
				for(final Enumeration<Room> r = A.getProperMap(); r.hasMoreElements();)
				{
					final Room R = r.nextElement();
					if(R != null)
						unsavable(R);
				}
				generated.add(A.Name());
				made.add(A.Name());
			}
		}
		return made.isEmpty() ? "Generated in the sandbox, not saved." : ("Generated in the sandbox, not saved: " + String.join(", ", made) + " (unsavable; SANDBOX CLEAR takes it away).");
	}
}
