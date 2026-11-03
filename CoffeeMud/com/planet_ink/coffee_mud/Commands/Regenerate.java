package com.planet_ink.coffee_mud.Commands;
import com.planet_ink.coffee_mud.core.exceptions.CMException;
import com.planet_ink.coffee_mud.core.interfaces.*;
import com.planet_ink.coffee_mud.core.*;
import com.planet_ink.coffee_mud.core.collections.*;
import com.planet_ink.coffee_mud.core.save.SaveGame;
import com.planet_ink.coffee_mud.Abilities.interfaces.*;
import com.planet_ink.coffee_mud.Areas.interfaces.*;
import com.planet_ink.coffee_mud.Behaviors.interfaces.*;
import com.planet_ink.coffee_mud.CharClasses.interfaces.*;
import com.planet_ink.coffee_mud.Commands.interfaces.*;
import com.planet_ink.coffee_mud.Common.interfaces.*;
import com.planet_ink.coffee_mud.Exits.interfaces.*;
import com.planet_ink.coffee_mud.Items.interfaces.*;
import com.planet_ink.coffee_mud.core.exceptions.*;
import com.planet_ink.coffee_mud.Libraries.interfaces.CMLibrary;
import com.planet_ink.coffee_mud.Libraries.interfaces.XMLLibrary.*;
import com.planet_ink.coffee_mud.Locales.interfaces.*;
import com.planet_ink.coffee_mud.MOBS.interfaces.*;
import com.planet_ink.coffee_mud.Races.interfaces.*;

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
 * G13: REGENERATE AREA [SEED=n|SAME] [VAR=VALUE ...] builds the builder's
 * area again from its GENSOURCE, keeping its GENLOCK rooms (core/GenSource);
 * REGENERATE LOCK and UNLOCK mark the builder's room GENLOCK or not, and
 * REGENERATE alone says how the area was made and which rooms are kept.
 * NOSAVE after REGENERATE, as for GENERATE, keeps the database out of it.
 */
public class Regenerate extends StdCommand
{
	public Regenerate()
	{
	}

	private final String[]	access	= I(new String[] { "REGENERATE" });

	@Override
	public String[] getAccessWords()
	{
		return access;
	}

	@Override
	public boolean execute(final MOB mob, final List<String> commands, final int metaFlags)
		throws java.io.IOException
	{
		final Room R = mob.location();
		if(R == null)
			return false;
		final List<String> words = new ArrayList<String>(commands);
		boolean save = true;
		if((words.size() > 1) && words.get(1).equalsIgnoreCase("NOSAVE"))
		{
			save = false;
			words.remove(1);
		}
		final String what = (words.size() > 1) ? words.get(1).toUpperCase() : "";
		final Area A = R.getArea();
		if(what.equals("LOCK") || what.equals("UNLOCK"))
		{
			final boolean lock = what.equals("LOCK");
			if(GenSource.lock(R, lock))
			{
				Unsaved.mark(R, "REGENERATE");
				if(save)
					CMLib.database().DBUpdateRoom(R);
				mob.tell(lock ? L("This room is GENLOCK now: REGENERATE AREA keeps it as it is.") : L("This room is not GENLOCK now: REGENERATE AREA will make it again."));
			}
			else
				mob.tell(lock ? L("This room is GENLOCK already.") : L("This room is not GENLOCK."));
			return false;
		}
		if(what.equals("AREA"))
		{
			SaveGame.warnAboutBuilding(mob);
			String seedWord = null;
			final Map<String, String> extra = new LinkedHashMap<String, String>();
			for(int i = 2; i < words.size(); i++)
			{
				final String w = words.get(i);
				final int x = w.indexOf('=');
				if(x <= 0)
				{
					if(w.equalsIgnoreCase("SAME"))
						seedWord = "SAME";
					continue;
				}
				final String k = w.substring(0, x).trim();
				final String v = w.substring(x + 1).trim();
				if(k.equalsIgnoreCase("SEED"))
					seedWord = v;
				else
					extra.put(k.toLowerCase(), v);
			}
			mob.tell(GenSource.regenerate(mob, A, seedWord, extra, save));
			return false;
		}
		final GenSource.Source S = GenSource.get(A);
		final StringBuilder str = new StringBuilder();
		if(S == null)
			str.append(L("'@x1' has no GENSOURCE: GENERATE AREA did not make it.", A.Name()));
		else
		{
			str.append(L("'@x1' was made by GENERATE AREA @x2 FROM @x3 SEED=@x4", A.Name(), S.id, S.file, "" + S.seed));
			for(final Map.Entry<String, String> e : S.vars.entrySet())
				str.append(' ').append(e.getKey()).append('=').append(e.getValue());
			str.append(".\n\r");
			final List<String> kept = new ArrayList<String>();
			for(final Enumeration<Room> r = A.getProperMap(); r.hasMoreElements();)
			{
				final Room R2 = r.nextElement();
				if(GenSource.isLocked(R2))
					kept.add(R2.roomID());
			}
			str.append(kept.isEmpty() ? L("No room is GENLOCK.") : L("GENLOCK, kept by REGENERATE AREA: @x1.", CMParms.toListString(kept)));
		}
		str.append("\n\r").append(L("REGENERATE AREA [SEED=n|SAME] [VAR=VALUE ...] builds it again; REGENERATE LOCK or UNLOCK marks this room."));
		mob.tell(str.toString());
		return false;
	}

	@Override
	public boolean canBeOrdered()
	{
		return false;
	}

	@Override
	public boolean securityCheck(final MOB mob)
	{
		return CMSecurity.isAllowedAnywhere(mob, CMSecurity.SecFlag.CMDAREAS);
	}
}
