package com.planet_ink.coffee_mud.core;

import java.util.*;

import org.mozilla.javascript.Context;
import org.mozilla.javascript.Function;
import org.mozilla.javascript.Scriptable;

import com.planet_ink.coffee_mud.core.interfaces.*;
import com.planet_ink.coffee_mud.Areas.interfaces.Area;
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
 * B11: the builder object in JRUN's scope, so that a script that builds is
 * short: here(), room(id), dig(dir, type), link(fromId, dir, toId),
 * create(kind, name, roomId), saveRoom(id), forEachRoom(area, fn) and
 * cmd(line), each as the builder who ran it.
 */
public final class JSBuilder
{
	private final MOB			mob;
	private final Scriptable	scope;

	/**
	 * A builder object for a JRUN.
	 * @param mob who ran it
	 * @param scope the script's scope
	 */
	public JSBuilder(final MOB mob, final Scriptable scope)
	{
		this.mob = mob;
		this.scope = scope;
	}

	/**
	 * The room the builder is in.
	 * @return the room
	 */
	public Room here()
	{
		return mob.location();
	}

	/**
	 * A room by its ID.
	 * @param id the ID
	 * @return the room, or null
	 */
	public Room room(final String id)
	{
		return CMLib.map().getRoom(id);
	}

	private int dirCode(final String dir)
	{
		return CMLib.directions().getGoodDirectionCode(dir);
	}

	/**
	 * A room made from the builder's room in a direction, as CREATE ROOM
	 * makes it (WORLD DIG's), linked both ways, saved and tagged TODO.
	 * @param dir the direction
	 * @param type a locale class or template, or null for this room's
	 * @return the room, or null
	 */
	public Room dig(final String dir, final String type)
	{
		final Room R = mob.location();
		final int d = dirCode(dir);
		if((R == null) || (d < 0) || (R.rawDoors()[d] != null))
			return null;
		return DigMode.dig(mob, R, d, type, true, null, null);
	}

	/**
	 * An exit from one room to another, one way, saved.
	 * @param fromId the room it leads from
	 * @param dir the direction
	 * @param toId the room it leads to
	 * @return true if linked
	 */
	public boolean link(final String fromId, final String dir, final String toId)
	{
		final Room R = room(fromId);
		final Room to = room(toId);
		final int d = dirCode(dir);
		if((R == null) || (to == null) || (d < 0))
			return false;
		R.rawDoors()[d] = to;
		if(R.getRawExit(d) == null)
		{
			final Exit X = CMClass.getExit("Open");
			R.setRawExit(d, X);
		}
		CMLib.database().DBUpdateExits(R);
		return true;
	}

	/**
	 * A mob or item put in a room: the catalog's of that name, or one of
	 * that class.  The room is marked unsaved (B4) for saveRoom.
	 * @param kind "mob" or "item"
	 * @param name the catalog name or class
	 * @param roomId the room, or null for the builder's
	 * @return what was made, or null
	 */
	public Physical create(final String kind, final String name, final String roomId)
	{
		final Room R = ((roomId == null) || (roomId.length() == 0)) ? mob.location() : room(roomId);
		if(R == null)
			return null;
		if(kind.equalsIgnoreCase("mob"))
		{
			MOB M = CMLib.catalog().getCatalogMob(name);
			M = (M != null) ? (MOB)M.copyOf() : CMClass.getMOB(name);
			if(M == null)
				return null;
			M.bringToLife(R, true);
			M.setStartRoom(R);
			Unsaved.mark(R, "JRUN");
			return M;
		}
		Item I = CMLib.catalog().getCatalogItem(name);
		I = (I != null) ? (Item)I.copyOf() : CMClass.getItem(name);
		if(I == null)
			return null;
		R.addItem(I);
		Unsaved.mark(R, "JRUN");
		return I;
	}

	/**
	 * A room's mobs and items saved, as SAVE ROOM does.
	 * @param id the room, or null for the builder's
	 * @return true if saved
	 */
	public boolean saveRoom(final String id)
	{
		final Room R = ((id == null) || (id.length() == 0)) ? mob.location() : room(id);
		final Object S = CMClass.getCommand("Save");
		if((R == null) || (S == null))
			return false;
		try
		{
			S.getClass().getMethod("saveChangedRoom", MOB.class, Room.class).invoke(S, mob, R);
			Unsaved.saved(R);
			return true;
		}
		catch(final Exception e)
		{
			Log.errOut("JSBuilder", e);
			return false;
		}
	}

	/**
	 * Calls a function for each room of an area.
	 * @param area the area's name
	 * @param fn the function, given each room
	 * @return how many rooms
	 */
	public int forEachRoom(final String area, final Function fn)
	{
		final Area A = CMLib.map().getArea(area);
		if((A == null) || (fn == null))
			return 0;
		final List<Room> rooms = new ArrayList<Room>();
		for(final Enumeration<Room> r = A.getProperMap(); r.hasMoreElements();)
		{
			final Room R = r.nextElement();
			if(R != null)
				rooms.add(R);
		}
		final Context cx = Context.getCurrentContext();
		for(final Room R : rooms)
			fn.call(cx, scope, scope, new Object[] { Context.javaToJS(R, scope) });
		return rooms.size();
	}

	/**
	 * A command, run as the builder typed it.
	 * @param line the command
	 */
	public void cmd(final String line)
	{
		mob.doCommand(CMParms.parse(line), 0);
	}
}
