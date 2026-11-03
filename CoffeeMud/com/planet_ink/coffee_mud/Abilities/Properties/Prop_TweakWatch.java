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
 * P22: left beside an effect a TWEAK's cast put on a mob, hidden and never
 * saved; when the effect is gone it calls the tweak script's ONEXPIRE and
 * goes too.  Its text: the tweak's ID, then the effect's.
 */
public class Prop_TweakWatch extends Property
{
	@Override
	public String ID()
	{
		return "Prop_TweakWatch";
	}

	@Override
	public String name()
	{
		return "a tweak's watch";
	}

	@Override
	protected int canAffectCode()
	{
		return Ability.CAN_MOBS;
	}

	@Override
	public String displayText()
	{
		return "";
	}

	@Override
	public boolean isSavable()
	{
		return false;
	}

	@Override
	public boolean tick(final Tickable ticking, final int tickID)
	{
		if(!super.tick(ticking, tickID))
			return false;
		final Physical P=affected;
		final String[] ids=text().trim().split(" ");
		if((P instanceof MOB)&&(ids.length>=2)&&(P.fetchEffect(ids[1])==null))
		{
			final Ability T=CMClass.getAbility(ids[0]);
			if(T!=null)
				HostScripts.call("TWEAK:"+ids[0], T.getStat("SCRIPT"), (MOB)P, "ONEXPIRE", P, null);
			P.delEffect(this);
			return false;
		}
		return true;
	}
}
