package com.planet_ink.coffee_mud.Abilities.Properties;
import com.planet_ink.coffee_mud.core.interfaces.*;
import com.planet_ink.coffee_mud.core.*;
import com.planet_ink.coffee_mud.core.collections.*;
import com.planet_ink.coffee_mud.Abilities.interfaces.*;
import com.planet_ink.coffee_mud.Areas.interfaces.*;
import com.planet_ink.coffee_mud.Behaviors.interfaces.*;
import com.planet_ink.coffee_mud.CharClasses.interfaces.*;
import com.planet_ink.coffee_mud.Commands.interfaces.*;
import com.planet_ink.coffee_mud.Common.interfaces.*;
import com.planet_ink.coffee_mud.Exits.interfaces.*;
import com.planet_ink.coffee_mud.Items.interfaces.*;
import com.planet_ink.coffee_mud.Libraries.interfaces.*;
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
 * W13: an area's or a room's own lines for the sky: DAWN=, DAY=, DUSK= and
 * NIGHT= (under the sky), INDAWN= to INNIGHT= (indoors), WEATHER_[STATE]=
 * (its description, as it starts and as LOOK and WEATHER tell it),
 * STOP_[STATE]= (its ending line) and MOON= (TIME's moon line), each with
 * alternatives between |, NONE for nothing; what is not set comes from
 * lists.ini (or the area's climate file).  A room's own come before its
 * area's, and an area's before its parents'.
 */
public class Prop_SkyText extends Property implements KeyedLines
{
	@Override
	public String ID()
	{
		return "Prop_SkyText";
	}

	@Override
	public String name()
	{
		return "Sky and weather lines";
	}

	@Override
	protected int canAffectCode()
	{
		return Ability.CAN_ROOMS | Ability.CAN_AREAS;
	}

	@Override
	public String accountForYourself()
	{
		return "Its own lines for the time of day and the weather";
	}

	protected Map<String, String[]>	lines	= new Hashtable<String, String[]>();

	@Override
	public void setMiscText(final String newMiscText)
	{
		super.setMiscText(newMiscText);
		lines = new Hashtable<String, String[]>();
		for(final Map.Entry<String, String> e : CMParms.parseEQParms(newMiscText).entrySet())
		{
			final String[] alts = e.getValue().split("\\|");
			for(int i = 0; i < alts.length; i++)
				alts[i] = alts[i].trim().equalsIgnoreCase("NONE") ? "" : alts[i].trim();
			lines.put(e.getKey().toUpperCase().trim(), alts);
		}
	}

	/**
	 * One of a key's lines, by the game's dice.
	 * @param key DAWN, INNIGHT, WEATHER_RAIN, STOP_SNOW, MOON...
	 * @return the line ("" for NONE), or null when it is not set
	 */
	@Override
	public String line(final String key)
	{
		final String[] alts = lines.get(key.toUpperCase());
		if((alts == null) || (alts.length == 0))
			return null;
		return alts[CMLib.dice().roll(1, alts.length, -1)];
	}

	/**
	 * @param key a key
	 * @return true if it is set
	 */
	@Override
	public boolean has(final String key)
	{
		return lines.containsKey(key.toUpperCase());
	}
}
