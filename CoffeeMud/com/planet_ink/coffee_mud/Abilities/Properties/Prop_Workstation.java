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
 * CR3: a workstation, on an item or a room: TAG= (what it is, a comma list,
 * as forge,anvil, which STATION= of a recipe or skill asks for), SKILLS= (the
 * skills it helps, by ID or name; all when not given), SPEED= (percent off
 * the time a craft takes), PROF= (added to the crafter's proficiency),
 * MAXLEVEL= (recipes up to that level may be made here, above the crafter's
 * own) and QUALITY= (for quality tiers).  It works for a crafter in the room
 * with it.  An item tagged forge (TAGS) with no Prop_Workstation counts as
 * a forge too, with no bonus.
 */
public class Prop_Workstation extends Property implements Workstations.Station
{
	@Override
	public String ID()
	{
		return "Prop_Workstation";
	}

	@Override
	public String name()
	{
		return "Workstation";
	}

	@Override
	protected int canAffectCode()
	{
		return Ability.CAN_ITEMS | Ability.CAN_ROOMS;
	}

	protected Set<String>	tags		= new TreeSet<String>();
	protected Set<String>	skills		= new TreeSet<String>();
	protected int			speed		= 0;
	protected int			prof		= 0;
	protected int			maxLevel	= 0;
	protected int			quality		= 0;

	private static int num(final String text, final String key)
	{
		return CMath.s_int(CMParms.getParmStr(text, key, "0").trim().replace("+", ""));
	}

	@Override
	public void setMiscText(final String newMiscText)
	{
		super.setMiscText(newMiscText);
		tags = new TreeSet<String>();
		for(final String s : CMParms.parseCommas(CMParms.getParmStr(newMiscText, "TAG", CMParms.getParmStr(newMiscText, "TAGS", "")), true))
			tags.add(s.trim().toLowerCase());
		skills = new TreeSet<String>();
		for(final String s : CMParms.parseCommas(CMParms.getParmStr(newMiscText, "SKILLS", ""), true))
			skills.add(s.trim().toUpperCase());
		speed = Math.max(0, Math.min(90, num(newMiscText, "SPEED")));
		prof = num(newMiscText, "PROF");
		maxLevel = num(newMiscText, "MAXLEVEL");
		quality = num(newMiscText, "QUALITY");
	}

	/**
	 * @param tag a station's name, as forge
	 * @return whether this is one
	 */
	@Override
	public boolean isA(final String tag)
	{
		return (tag != null) && tags.contains(tag.trim().toLowerCase());
	}

	/**
	 * @param skill a skill
	 * @return whether this helps it
	 */
	@Override
	public boolean helps(final Ability skill)
	{
		if(skills.size() == 0)
			return true;
		return (skill != null) && (skills.contains(skill.ID().toUpperCase()) || skills.contains(skill.Name().toUpperCase()));
	}

	/** @return percent off the time a craft takes */
	@Override
	public int speed()
	{
		return speed;
	}

	/** @return added to the crafter's proficiency */
	@Override
	public int prof()
	{
		return prof;
	}

	/** @return recipes up to this level may be made here */
	@Override
	public int maxLevel()
	{
		return maxLevel;
	}

	/** @return the quality it adds, for quality tiers */
	@Override
	public int quality()
	{
		return quality;
	}

	@Override
	public String accountForYourself()
	{
		return "A workstation" + (tags.isEmpty() ? "" : (": " + CMParms.toListString(tags)));
	}
}
