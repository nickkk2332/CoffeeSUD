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
 * CR12: crafting and gathering XP, on a character, an item (carried, or
 * lying in the room), a room or an area: PCT= (the percent of a craft's XP
 * value a player character earns for it, on top of CRAFTXP), GATHER= (XP
 * for each unit gathered, on top of GATHERXP), SKILLS= (the skills it is
 * for, by ID or name; all when not given) and MASK= (who it is for, a
 * zapper mask).  A guild hall that teaches: PCT=50 on its area.
 */
public class Prop_CraftXP extends Property implements CraftXP.Bonus
{
	@Override
	public String ID()
	{
		return "Prop_CraftXP";
	}

	@Override
	public String name()
	{
		return "Crafting XP";
	}

	@Override
	protected int canAffectCode()
	{
		return Ability.CAN_MOBS | Ability.CAN_ITEMS | Ability.CAN_ROOMS | Ability.CAN_AREAS;
	}

	protected int			pct		= 0;
	protected int			gather	= 0;
	protected Set<String>	skills	= new TreeSet<String>();
	protected String		mask	= "";

	@Override
	public void setMiscText(final String newMiscText)
	{
		super.setMiscText(newMiscText);
		pct = CMath.s_int(CMParms.getParmStr(newMiscText, "PCT", "0").trim().replace("+", ""));
		gather = CMath.s_int(CMParms.getParmStr(newMiscText, "GATHER", "0").trim().replace("+", ""));
		skills = new TreeSet<String>();
		for(final String s : CMParms.parseCommas(CMParms.getParmStr(newMiscText, "SKILLS", ""), true))
			skills.add(s.trim().toUpperCase());
		mask = CMParms.getParmStr(newMiscText, "MASK", "").trim();
	}

	/**
	 * @param mob a character
	 * @param skill their skill
	 * @return whether this counts for them and it
	 */
	protected boolean counts(final MOB mob, final Ability skill)
	{
		if((skills.size() > 0)
		&&((skill == null) || !(skills.contains(skill.ID().toUpperCase()) || skills.contains(skill.Name().toUpperCase()))))
			return false;
		return (mask.length() == 0) || CMLib.masking().maskCheck(mask, mob, true);
	}

	@Override
	public int pct(final MOB mob, final Ability skill)
	{
		return ((pct != 0) && counts(mob, skill)) ? pct : 0;
	}

	@Override
	public int gather(final MOB mob, final Ability skill)
	{
		return ((gather != 0) && counts(mob, skill)) ? gather : 0;
	}

	@Override
	public String accountForYourself()
	{
		final StringBuilder s = new StringBuilder("Crafting XP");
		if(pct != 0)
			s.append(": ").append(pct).append("% of each craft's");
		if(gather != 0)
			s.append((pct != 0) ? ", " : ": ").append(gather).append(" a unit gathered");
		return s.toString();
	}
}
