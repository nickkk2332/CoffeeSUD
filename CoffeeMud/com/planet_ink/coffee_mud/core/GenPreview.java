package com.planet_ink.coffee_mud.core;
import com.planet_ink.coffee_mud.core.interfaces.*;
import com.planet_ink.coffee_mud.Areas.interfaces.*;
import com.planet_ink.coffee_mud.Commands.interfaces.*;
import com.planet_ink.coffee_mud.Items.interfaces.*;
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
 * G4: GENERATE PREVIEW type id [FROM file] [VAR=VALUE ...] [SEED=n] [dir]
 * builds what GENERATE would (an area unlinked, a room in a free direction,
 * mobs and items in the builder's room), with the database left out, lists
 * it (each room's title, node type, exits, mobs and items) with the seed,
 * and destroys it again.  GENERATE PREVIEW KEEP then runs the real GENERATE
 * with the same words and seed.
 */
public final class GenPreview
{
	private GenPreview()
	{
	}

	private static final ThreadLocal<boolean[]>	active	= ThreadLocal.withInitial(() -> new boolean[1]);
	private static final Map<MOB, List<String>>	last	= new WeakHashMap<MOB, List<String>>();

	/** @return true while a preview is being built on this thread, so GENERATE says less */
	public static boolean isActive()
	{
		return active.get()[0];
	}

	private static String exits(final Room R)
	{
		final StringBuilder str = new StringBuilder();
		for(int d = 0; d < Directions.NUM_DIRECTIONS(); d++)
		{
			if(R.getRoomInDir(d) != null)
				str.append((str.length() > 0) ? "," : "").append(CMLib.directions().getDirectionChar(d).toLowerCase());
		}
		return (str.length() == 0) ? "none" : str.toString();
	}

	private static String contents(final Room R)
	{
		final StringBuilder str = new StringBuilder();
		final List<String> mobs = new ArrayList<String>();
		for(final Enumeration<MOB> m = R.inhabitants(); m.hasMoreElements();)
		{
			final MOB M = m.nextElement();
			if((M != null) && M.isMonster())
				mobs.add(M.name() + " (" + M.phyStats().level() + ")");
		}
		final List<String> items = new ArrayList<String>();
		for(final Enumeration<Item> i = R.items(); i.hasMoreElements();)
		{
			final Item I = i.nextElement();
			if((I != null) && (I.container() == null))
				items.add(I.name());
		}
		if(mobs.size() > 0)
			str.append("\n\r      mobs: ").append(CMParms.toListString(mobs));
		if(items.size() > 0)
			str.append("\n\r      items: ").append(CMParms.toListString(items));
		return str.toString();
	}

	private static int count(final Room R, final boolean mobs)
	{
		int n = 0;
		if(mobs)
		{
			for(final Enumeration<MOB> m = R.inhabitants(); m.hasMoreElements();)
			{
				final MOB M = m.nextElement();
				if((M != null) && M.isMonster())
					n++;
			}
		}
		else
			n = R.numItems();
		return n;
	}

	/**
	 * GENERATE PREVIEW, and PREVIEW KEEP.
	 * @param mob the builder
	 * @param generate the GENERATE command
	 * @param words the words after PREVIEW
	 * @param metaFlags as the command was given
	 * @return what to tell the builder
	 */
	public static String preview(final MOB mob, final Command generate, final List<String> words, final int metaFlags)
	{
		final Room here = mob.location();
		if((here == null) || (words.size() == 0))
			return "Preview what? GENERATE PREVIEW [TYPE] [ID] (FROM [FILE]) ([VAR=VALUE]...) (SEED=[N]) ([DIR]), or GENERATE PREVIEW KEEP.";
		if(words.get(0).equalsIgnoreCase("KEEP"))
		{
			final List<String> keep;
			synchronized(last)
			{
				keep = last.remove(mob);
			}
			if(keep == null)
				return "There is no preview to keep: GENERATE PREVIEW something first.";
			try
			{
				generate.execute(mob, new ArrayList<String>(keep), metaFlags);
			}
			catch(final java.io.IOException e)
			{
				return e.getMessage();
			}
			return "Kept: " + CMParms.combineQuoted(keep, 0) + ".";
		}
		final List<String> w = new ArrayList<String>(words);
		final String type = w.get(0).toUpperCase();
		String seed = null;
		for(final String s : w)
		{
			if(s.toUpperCase().startsWith("SEED="))
				seed = s.substring(5).trim();
		}
		if(seed == null)
		{
			seed = "" + GenSeed.pick(null, null);
			w.add("SEED=" + seed);
		}
		int dir = -1;
		if(w.size() > 2)
			dir = CMLib.directions().getGoodDirectionCode(w.get(w.size() - 1));
		final List<String> keep = new ArrayList<String>();
		keep.add("GENERATE");
		keep.addAll(w);
		final List<String> run = new ArrayList<String>();
		run.add("GENERATE");
		run.add("NOSAVE");
		final StringBuilder str = new StringBuilder();
		active.get()[0] = true;
		try
		{
			if(type.startsWith("AREA"))
			{
				if(dir >= 0)
					w.remove(w.size() - 1);
				else
				if(!keep.get(keep.size() - 1).equalsIgnoreCase("UNLINKED"))
					keep.add("UNLINKED");
				run.addAll(w);
				if(!run.get(run.size() - 1).equalsIgnoreCase("UNLINKED"))
					run.add("UNLINKED");
				final Set<Area> before = new HashSet<Area>();
				for(final Enumeration<Area> a = CMLib.map().areas(); a.hasMoreElements();)
					before.add(a.nextElement());
				generate.execute(mob, run, metaFlags);
				Area A = null;
				for(final Enumeration<Area> a = CMLib.map().areas(); a.hasMoreElements();)
				{
					final Area A2 = a.nextElement();
					if(!before.contains(A2))
						A = A2;
				}
				if(A == null)
					return "Nothing was built to preview.";
				final List<Room> rooms = new ArrayList<Room>();
				for(final Enumeration<Room> r = A.getProperMap(); r.hasMoreElements();)
					rooms.add(r.nextElement());
				Collections.sort(rooms, new Comparator<Room>()
				{
					@Override
					public int compare(final Room a, final Room b)
					{
						final String ia = a.roomID(), ib = b.roomID();
						final int x = ia.lastIndexOf('#'), y = ib.lastIndexOf('#');
						if((x >= 0) && (y >= 0) && CMath.isInteger(ia.substring(x + 1)) && CMath.isInteger(ib.substring(y + 1)))
							return Integer.compare(CMath.s_int(ia.substring(x + 1)), CMath.s_int(ib.substring(y + 1)));
						return ia.compareTo(ib);
					}
				});
				int mobs = 0, items = 0;
				final StringBuilder list = new StringBuilder();
				for(final Room R : rooms)
				{
					mobs += count(R, true);
					items += count(R, false);
					list.append("\n\r  ").append(R.roomID()).append("  ").append(R.displayText(null)).append(" [").append(GenFill.nodeType(R)).append("] exits ").append(exits(R)).append(contents(R));
				}
				str.append("Preview of AREA ").append(A.Name()).append(" (seed ").append(seed).append("): ").append(rooms.size()).append(" rooms, ").append(mobs).append(" mobs, ").append(items).append(" items.");
				str.append(list);
				CMLib.map().obliterateMapArea(A);
			}
			else
			if(type.startsWith("ROOM"))
			{
				if(dir < 0)
				{
					for(int d = 0; (d < Directions.NUM_DIRECTIONS()) && (dir < 0); d++)
					{
						if((here.getRoomInDir(d) == null) && (here.getRawExit(d) == null))
							dir = d;
					}
					if(dir < 0)
						return "There is no free direction here to preview a room in.";
					w.add(CMLib.directions().getDirectionName(dir));
					keep.add(CMLib.directions().getDirectionName(dir));
				}
				run.addAll(w);
				generate.execute(mob, run, metaFlags);
				final Room R = here.rawDoors()[dir];
				if(R == null)
					return "Nothing was built to preview.";
				str.append("Preview of ROOM (seed ").append(seed).append("):\n\r  ").append(R.displayText(null)).append(" [").append(GenFill.nodeType(R)).append("] exits ").append(exits(R)).append(contents(R));
				str.append("\n\r  ").append(R.description(null));
				here.rawDoors()[dir] = null;
				here.setRawExit(dir, null);
				CMLib.map().emptyRoom(R, null, true);
				if(R.getArea() != null)
					R.getArea().delProperRoom(R);
				R.destroy();
			}
			else
			if(type.startsWith("MOB") || type.startsWith("ITEM"))
			{
				final Set<Object> before = new HashSet<Object>();
				for(final Enumeration<MOB> m = here.inhabitants(); m.hasMoreElements();)
					before.add(m.nextElement());
				for(final Enumeration<Item> i = here.items(); i.hasMoreElements();)
					before.add(i.nextElement());
				run.addAll(w);
				generate.execute(mob, run, metaFlags);
				final List<Physical> made = new ArrayList<Physical>();
				for(final Enumeration<MOB> m = here.inhabitants(); m.hasMoreElements();)
				{
					final MOB M = m.nextElement();
					if((M != null) && !before.contains(M))
						made.add(M);
				}
				for(final Enumeration<Item> i = here.items(); i.hasMoreElements();)
				{
					final Item I = i.nextElement();
					if((I != null) && !before.contains(I) && (I.container() == null))
						made.add(I);
				}
				str.append("Preview of ").append(type).append(" (seed ").append(seed).append("):");
				for(final Physical P : made)
				{
					str.append("\n\r  ").append(P.name()).append(" (level ").append(P.phyStats().level()).append(")");
					if(P instanceof MOB)
					{
						final MOB M = (MOB)P;
						str.append(" ").append(M.charStats().getMyRace().name()).append(" ").append(M.charStats().getCurrentClass().name());
						final List<String> carried = new ArrayList<String>();
						for(final Enumeration<Item> i = M.items(); i.hasMoreElements();)
							carried.add(i.nextElement().name());
						if(carried.size() > 0)
							str.append("\n\r      carrying: ").append(CMParms.toListString(carried));
					}
					else
					if(P instanceof Item)
						str.append(" worth ").append(((Item)P).baseGoldValue());
				}
				for(final Physical P : made)
					P.destroy();
			}
			else
				return "GENERATE PREVIEW works for an AREA, a ROOM, a MOB or an ITEM.";
		}
		catch(final java.io.IOException e)
		{
			return e.getMessage();
		}
		finally
		{
			active.get()[0] = false;
		}
		synchronized(last)
		{
			last.put(mob, keep);
		}
		str.append("\n\r").append("GENERATE PREVIEW KEEP builds it, with SEED=" + seed + ".");
		return str.toString();
	}
}
