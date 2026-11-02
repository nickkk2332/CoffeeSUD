package com.planet_ink.coffee_mud.Abilities.Properties;
import com.planet_ink.coffee_mud.core.interfaces.*;
import com.planet_ink.coffee_mud.core.*;
import com.planet_ink.coffee_mud.core.collections.*;
import com.planet_ink.coffee_mud.Abilities.interfaces.*;
import com.planet_ink.coffee_mud.Common.interfaces.*;
import com.planet_ink.coffee_mud.Exits.interfaces.*;
import com.planet_ink.coffee_mud.Items.interfaces.*;
import com.planet_ink.coffee_mud.Libraries.interfaces.*;
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
 * I26: an item's own wear and tear.  MULT=50 the percent of the usual wear
 * it takes (with coffeemud.ini's ITEMWEAR); UNBREAKABLE: none at all;
 * BREAK=true or BROKENNAME="a shattered %s": worn out it breaks rather than
 * goes, and is named so; NOREPAIR: nobody mends it.  While it is worn
 * below half it shows as (worn).
 */
public class Prop_Durability extends Property
{
	@Override
	public String ID()
	{
		return "Prop_Durability";
	}

	@Override
	public String name()
	{
		return "Durability";
	}

	@Override
	protected int canAffectCode()
	{
		return Ability.CAN_ITEMS;
	}

	@Override
	public String accountForYourself()
	{
		return unbreakable ? "Unbreakable" : "";
	}

	protected boolean	unbreakable	= false;
	protected boolean	noRepair	= false;

	@Override
	public void setMiscText(final String newText)
	{
		super.setMiscText(newText);
		final String t = (newText == null) ? "" : newText;
		final String up = " " + t.toUpperCase() + " ";
		final Map<String, String> q = CMParms.parseExactParms(t);
		unbreakable = up.matches(".*\\sUNBREAKABLE(=TRUE)?\\s.*") || CMParms.getParmBool(q, "UNBREAKABLE", false);
		noRepair = up.matches(".*\\sNOREPAIR(=TRUE)?\\s.*") || CMParms.getParmBool(q, "NOREPAIR", false);
	}

	@Override
	public void affectPhyStats(final Physical host, final PhyStats stats)
	{
		super.affectPhyStats(host, stats);
		if((host instanceof Item) && ((Item)host).subjectToWearAndTear() && (((Item)host).usesRemaining() > 0)
		&& (((Item)host).usesRemaining() < 50) && (host.fetchEffect("Prop_Broken") == null))
			stats.addAmbiance("(worn)");
	}

	@Override
	public boolean okMessage(final Environmental myHost, final CMMsg msg)
	{
		if(!super.okMessage(myHost, msg))
			return false;
		if(noRepair && (affected instanceof Item))
		{
			final boolean mending = ((msg.target() == affected) && (msg.tool() instanceof Ability)
				&& (((Ability)msg.tool()).ID().toUpperCase().contains("MEND") || ((Ability)msg.tool()).ID().toUpperCase().contains("REPAIR")))
				|| ((msg.tool() == affected) && (msg.targetMinor() == CMMsg.TYP_GIVE) && (msg.target() instanceof MOB) && (((MOB)msg.target()).fetchBehavior("ItemMender") != null));
			if(mending)
			{
				msg.source().tell(msg.source(), affected, null, CMLib.lang().L("<T-NAME> cannot be mended."));
				return false;
			}
		}
		return true;
	}
}
