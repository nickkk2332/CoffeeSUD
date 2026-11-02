package com.planet_ink.coffee_mud.core;
import com.planet_ink.coffee_mud.core.interfaces.*;
import com.planet_ink.coffee_mud.Common.interfaces.*;
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
 * C7: a character's respawn point (Prop_Checkpoint, MPSETRESPAWN), kept as
 * a tattoo, CHECKPOINT:room id: saved with the character, kept through
 * death, and apart from the recall room.  DEATH=CHECKPOINT wakes the dead
 * there.
 */
public final class Checkpoints
{
	private Checkpoints()
	{
	}

	/** how the tattoo begins */
	public static final String TATTOO = "CHECKPOINT:";

	/**
	 * @param M a character
	 * @return their respawn point's room id, or ""
	 */
	public static String idOf(final MOB M)
	{
		if(M == null)
			return "";
		final Tattoo T = M.findTattooStartsWith(TATTOO);
		return (T == null) ? "" : T.getTattooName().substring(TATTOO.length()).trim();
	}

	/**
	 * @param M a character
	 * @return their respawn point, or null
	 */
	public static Room roomOf(final MOB M)
	{
		final String id = idOf(M);
		return (id.length() == 0) ? null : CMLib.map().getRoom(id);
	}

	/**
	 * Forgets a character's respawn point.
	 * @param M the character
	 */
	public static void clear(final MOB M)
	{
		if(M == null)
			return;
		for(Tattoo T = M.findTattooStartsWith(TATTOO); T != null; T = M.findTattooStartsWith(TATTOO))
			M.delTattoo(T);
	}

	/**
	 * Makes a room a character's respawn point.
	 * @param M the character
	 * @param R the room
	 * @return false if the room has no id to keep
	 */
	public static boolean set(final MOB M, final Room R)
	{
		if((M == null) || (R == null))
			return false;
		final String id = CMLib.map().getExtendedRoomID(R);
		if((id == null) || (id.length() == 0))
			return false;
		clear(M);
		M.addTattoo(TATTOO + id);
		return true;
	}
}
