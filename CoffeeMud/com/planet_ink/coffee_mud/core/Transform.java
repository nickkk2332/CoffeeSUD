package com.planet_ink.coffee_mud.core;
import com.planet_ink.coffee_mud.core.interfaces.*;
import com.planet_ink.coffee_mud.Common.interfaces.*;
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
 * I22: one item turned into another in its place (MPTRANSFORM, JavaScript's
 * transform(), Prop_Transform, ItemUpgrader).  The new item goes where the
 * old one was: carried by the same one or lying in the same room, and, by
 * KEEP, in the same container (CONTAINER), worn in the same place (WORN),
 * with the old one's script variables and states (STATE), tags (TAGS), uses
 * or condition (USES); what was in it stays in it (or falls out where it
 * was, if the new one holds nothing).
 */
public final class Transform
{
	private Transform()
	{
	}

	/** what KEEP may name */
	public static final String[]	KEEPS		= { "STATE", "TAGS", "USES", "CONTAINER", "WORN" };
	/** KEEP when none is given */
	public static final String		DEFAULT		= "CONTAINER,WORN";

	/**
	 * What KEEP names, upper case.
	 * @param keep the list, commas between, or ALL, NONE, or null for the default
	 * @return the set
	 */
	public static Set<String> keeps(final String keep)
	{
		final Set<String> set = new HashSet<String>();
		final String k = ((keep == null) || (keep.trim().length() == 0)) ? DEFAULT : keep.trim().toUpperCase();
		if(k.equals("ALL"))
			set.addAll(Arrays.asList(KEEPS));
		else
		if(!k.equals("NONE"))
		{
			for(final String w : CMParms.parseCommas(k, true))
				set.add(w.trim());
		}
		return set;
	}

	/**
	 * A new item by name: the catalog's, or an item class's.
	 * @param name the name
	 * @return the item, or null
	 */
	public static Item make(final String name)
	{
		if((name == null) || (name.trim().length() == 0))
			return null;
		final Physical P = Encounters.make(name.trim(), false);
		return (P instanceof Item) ? (Item)P : null;
	}

	/**
	 * Puts one item in another's place, and destroys the old one.
	 * @param old the item that goes
	 * @param neu the item that comes, not yet anywhere
	 * @param keep what KEEP names (keeps())
	 * @return the new item, or null if the old one was nowhere
	 */
	public static Item replace(final Item old, final Item neu, final Set<String> keep)
	{
		if((old == null) || (neu == null) || (old == neu))
			return null;
		final ItemPossessor owner = old.owner();
		if(owner == null)
			return null;
		final Container in = old.container();
		final long worn = old.rawWornCode();
		final List<Item> contents = (old instanceof Container) ? new ArrayList<Item>(((Container)old).getContents()) : new ArrayList<Item>(0);
		if(keep.contains("STATE"))
			ObjectVars.reborn(neu, new TreeMap<String, String>(ObjectVars.vars(old)));
		if(keep.contains("TAGS"))
		{
			for(final Enumeration<String> t = old.tags(); t.hasMoreElements();)
				neu.addTag(t.nextElement());
		}
		if(keep.contains("USES"))
			neu.setUsesRemaining(old.usesRemaining());
		// what was in it
		for(final Item I : contents)
			I.setContainer(null);
		if(owner instanceof MOB)
		{
			final MOB M = (MOB)owner;
			old.unWear();
			M.delItem(old);
			M.addItem(neu);
			if(keep.contains("CONTAINER") && (in != null) && (in.owner() == M))
				neu.setContainer(in);
			if(keep.contains("WORN") && (worn != 0) && (in == null))
				neu.wearAt(worn);
		}
		else
		if(owner instanceof Room)
		{
			final Room R = (Room)owner;
			R.delItem(old);
			R.addItem(neu, ItemPossessor.Expire.Player_Drop);
			if(keep.contains("CONTAINER") && (in != null) && (in.owner() == R))
				neu.setContainer(in);
		}
		else
			return null;
		for(final Item I : contents)
		{
			if(neu instanceof Container)
				I.setContainer((Container)neu);
			else
				I.setContainer(neu.container());
		}
		old.destroy();
		neu.recoverPhyStats();
		if(owner instanceof MOB)
		{
			((MOB)owner).recoverPhyStats();
			((MOB)owner).recoverCharStats();
			((MOB)owner).recoverMaxState();
		}
		else
			((Room)owner).recoverRoomStats();
		return neu;
	}

	/**
	 * An item into a new one, by name, in its place.
	 * @param old the item
	 * @param name the catalog's item, or an item class
	 * @param keep KEEP's list, or null for the default
	 * @return the new item, or null
	 */
	public static Item into(final Item old, final String name, final String keep)
	{
		final Item neu = make(name);
		if(neu == null)
			return null;
		final Item done = replace(old, neu, keeps(keep));
		if(done == null)
			neu.destroy();
		return done;
	}
}
