package com.planet_ink.coffee_mud.core;
import com.planet_ink.coffee_mud.core.interfaces.*;
import com.planet_ink.coffee_mud.Common.interfaces.*;
import com.planet_ink.coffee_mud.Exits.interfaces.*;
import com.planet_ink.coffee_mud.Items.interfaces.*;
import com.planet_ink.coffee_mud.Locales.interfaces.*;
import com.planet_ink.coffee_mud.MOBS.interfaces.*;

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
 * I2: a door, or a container, changed as a character would change it: both
 * sides of a door at once, with a message in both rooms, and the door's
 * relock timer started (MPDOOR, the puzzle properties, JavaScript's door()).
 */
public final class Doors
{
	private Doors()
	{
	}

	/** what can be done */
	public static final String[] VERBS = { "OPEN", "CLOSE", "LOCK", "UNLOCK", "TOGGLE" };

	/**
	 * A door of a room, by direction, or a container there, by name.
	 * @param R the room
	 * @param what a direction, or a container's name
	 * @return the exit or container, or null
	 */
	public static Environmental find(final Room R, final String what)
	{
		if((R == null) || (what == null) || (what.trim().length() == 0))
			return null;
		final int d = CMLib.directions().getGoodDirectionCode(what.trim());
		if(d >= 0)
			return R.getExitInDir(d);
		final Item I = R.findItem(null, what.trim());
		return (I instanceof Container) ? I : null;
	}

	/**
	 * Its state: open, closed or locked, or nothing when it has no door.
	 * @param E an exit or a container
	 * @return the state
	 */
	public static String state(final Environmental E)
	{
		if((E instanceof Exit) && ((Exit)E).hasADoor())
		{
			final Exit X = (Exit)E;
			return X.isOpen() ? "open" : (X.isLocked() ? "locked" : "closed");
		}
		if((E instanceof Container) && ((Container)E).hasADoor())
		{
			final Container C = (Container)E;
			return C.isOpen() ? "open" : (C.isLocked() ? "locked" : "closed");
		}
		return "";
	}

	/** the default message of a change, with the exit's own words */
	protected static String defaultMsg(final Environmental E, final String verb)
	{
		final String open = (E instanceof Exit) ? ((Exit)E).openWord() : "open";
		final String close = (E instanceof Exit) ? ((Exit)E).closeWord() : "close";
		final String w = verb.equals("OPEN") ? open : verb.equals("CLOSE") ? close : verb.toLowerCase();
		return "<T-NAME> " + CMLib.english().makePlural(w) + ".";
	}

	/**
	 * Changes a door of a room, or a container there.
	 * @param R the room
	 * @param what a direction, or a container's name
	 * @param verb OPEN, CLOSE, LOCK, UNLOCK or TOGGLE
	 * @param message what the rooms see (&lt;T-NAME&gt; the door), null for the
	 *        default, or "" for none
	 * @return null if done, or why not
	 */
	public static String change(final Room R, final String what, final String verb, final String message)
	{
		if(R == null)
			return "no such room";
		final Environmental E = find(R, what);
		if(E == null)
			return "no door or container called " + what;
		return change(R, E, verb, message);
	}

	/**
	 * Changes a door of a room, or a container there.
	 * @param R the room
	 * @param E the exit, or the container
	 * @param verb OPEN, CLOSE, LOCK, UNLOCK or TOGGLE
	 * @param message what the rooms see, null for the default, or "" for none
	 * @return null if done, or why not
	 */
	public static String change(final Room R, final Environmental E, final String verb, final String message)
	{
		if((R == null) || (!((E instanceof Exit) || (E instanceof Container))))
			return "no door or container";
		final String v = (verb == null) ? "" : verb.trim().toUpperCase();
		if(!CMParms.contains(VERBS, v))
			return "OPEN, CLOSE, LOCK, UNLOCK or TOGGLE, not " + verb;
		final boolean exit = E instanceof Exit;
		final boolean hasDoor = exit ? ((Exit)E).hasADoor() : ((Container)E).hasADoor();
		final boolean hasLock = exit ? ((Exit)E).hasALock() : ((Container)E).hasALock();
		final boolean isOpen = exit ? ((Exit)E).isOpen() : ((Container)E).isOpen();
		final boolean isLocked = exit ? ((Exit)E).isLocked() : ((Container)E).isLocked();
		if(!hasDoor)
			return "it has no door";
		String act = v;
		if(act.equals("TOGGLE"))
			act = isOpen ? "CLOSE" : "OPEN";
		if((act.equals("LOCK") || act.equals("UNLOCK")) && (!hasLock))
			return "it has no lock";
		if((act.equals("OPEN") && isOpen) || (act.equals("CLOSE") && (!isOpen))
		|| (act.equals("LOCK") && isLocked) || (act.equals("UNLOCK") && (!isLocked)))
			return null; // as it is
		final String raw = (message == null) ? defaultMsg(E, act) : ((message.length() == 0) ? null : message);
		final String text = (raw == null) ? null : named(raw, E.name());
		int dir = -1;
		if(exit)
		{
			for(int d = Directions.NUM_DIRECTIONS() - 1; d >= 0; d--)
			{
				if(R.getExitInDir(d) == E)
				{
					dir = d;
					break;
				}
			}
		}
		final MOB src = CMLib.map().getFactoryMOB(R);
		try
		{
			if(act.equals("OPEN") && isLocked)
				send(src, R, E, dir, CMMsg.MSG_UNLOCK, null);
			if(act.equals("LOCK") && isOpen)
				send(src, R, E, dir, CMMsg.MSG_CLOSE, null);
			final int code = act.equals("OPEN") ? CMMsg.MSG_OPEN : act.equals("CLOSE") ? CMMsg.MSG_CLOSE : act.equals("LOCK") ? CMMsg.MSG_LOCK : CMMsg.MSG_UNLOCK;
			send(src, R, E, dir, code, text);
			// the far side sees it too
			if(exit && (dir >= 0) && (text != null))
			{
				final Room R2 = R.getRoomInDir(dir);
				final Exit E2 = R.getPairedExit(dir);
				if((R2 != null) && (R2 != R))
				{
					R2.showHappens(CMMsg.MSG_OK_VISUAL, named(raw, (E2 != null) ? E2.name() : E.name()));
				}
			}
			R.recoverRoomStats();
		}
		finally
		{
			src.destroy();
		}
		return null;
	}

	/** the message with the door's name in it, the same for all who see it */
	protected static String named(final String text, final String name)
	{
		return CMStrings.capitalizeFirstLetter(text.replace("<T-NAMESELF>", name).replace("<T-NAME>", name));
	}

	protected static void send(final MOB src, final Room R, final Environmental E, final int dir, final int code, final String text)
	{
		final CMMsg msg = CMClass.getMsg(src, E, null, code, text);
		if(E instanceof Exit)
			CMLib.utensils().roomAffectFully(msg, R, dir);
		else
			R.send(src, msg);
	}
}
