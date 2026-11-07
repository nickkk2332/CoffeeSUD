package com.planet_ink.coffee_mud.Abilities.Properties;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import com.planet_ink.coffee_mud.Abilities.StdAbility;
import com.planet_ink.coffee_mud.Abilities.interfaces.Ability;
import com.planet_ink.coffee_mud.Items.interfaces.Item;
import com.planet_ink.coffee_mud.MOBS.interfaces.MOB;
import com.planet_ink.coffee_mud.core.CombatRules;
import com.planet_ink.coffee_mud.core.ProgressionTracks;

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
 * P01: progression tracks (the optional module progression.tracks) on a
 * character or an item.  On an item, TRACKS=id,id names its mastery tracks.
 * On either, it is the source of the combat stats the host's milestones
 * give, read by combat profiles; a character gets one by itself when a
 * milestone first gives stats.
 */
public class Prop_Tracks extends StdAbility implements com.planet_ink.coffee_mud.core.interfaces.CombatStatSource
{
	@Override
	public String ID()
	{
		return "Prop_Tracks";
	}

	@Override
	public String name()
	{
		return "Progression tracks";
	}

	@Override
	public int classificationCode()
	{
		return Ability.ACODE_PROPERTY;
	}

	@Override
	protected int canAffectCode()
	{
		return Ability.CAN_MOBS | Ability.CAN_ITEMS;
	}

	@Override
	public CombatRules.Stats combatStats() throws IOException
	{
		if(affected instanceof MOB)
			return ProgressionTracks.stats(ProgressionTracks.of((MOB)affected));
		if(affected instanceof Item)
			return ProgressionTracks.stats(ProgressionTracks.of((Item)affected));
		return CombatRules.sumBonuses(new ArrayList<CombatRules.Stats>());
	}

	@Override
	public List<String> combatStatDetails() throws IOException
	{
		final List<String> out = new ArrayList<String>();
		out.add("milestones of progression tracks");
		return out;
	}

	@Override
	public String accountForYourself()
	{
		return "Progression tracks" + (text().trim().length() > 0 ? ": " + text() : "");
	}
}
