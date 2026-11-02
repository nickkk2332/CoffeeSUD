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
 * I26: a broken item (ITEMBREAK=BREAK, Prop_Durability): it cannot be worn,
 * held or wielded, its armour and damage are nothing, and it shows as
 * (broken), until it is mended (its uses above 0 again), when it goes and
 * the item has its name back (ORIGINAL=).
 */
public class Prop_Broken extends Property
{
	@Override
	public String ID()
	{
		return "Prop_Broken";
	}

	@Override
	public String name()
	{
		return "Broken";
	}

	@Override
	protected int canAffectCode()
	{
		return Ability.CAN_ITEMS;
	}

	@Override
	public String accountForYourself()
	{
		return "Broken";
	}

	protected boolean ticking = false;

	@Override
	public void setAffectedOne(final Physical P)
	{
		super.setAffectedOne(P);
		if((P != null) && (!ticking))
		{
			ticking = true;
			CMLib.threads().startTickDown(this, Tickable.TICKID_PROPERTY_SPECIAL, 1);
		}
	}

	@Override
	public void affectPhyStats(final Physical host, final PhyStats stats)
	{
		super.affectPhyStats(host, stats);
		stats.setArmor(0);
		stats.setDamage(0);
		stats.addAmbiance("(broken)");
	}

	@Override
	public boolean okMessage(final Environmental myHost, final CMMsg msg)
	{
		if(!super.okMessage(myHost, msg))
			return false;
		if((msg.target() == affected)
		&& ((msg.targetMinor() == CMMsg.TYP_WEAR) || (msg.targetMinor() == CMMsg.TYP_WIELD) || (msg.targetMinor() == CMMsg.TYP_HOLD)))
		{
			msg.source().tell(msg.source(), affected, null, CMLib.lang().L("<T-NAME> is broken."));
			return false;
		}
		return true;
	}

	/** mended: it goes */
	protected void mended()
	{
		final Item I = (Item)affected;
		final String orig = CMParms.getParmStr(CMParms.parseExactParms(text()), "ORIGINAL", "");
		if(orig.length() > 0)
			I.setName(orig);
		I.delEffect(this);
		setAffectedOne(null);
		I.recoverPhyStats();
	}

	@Override
	public boolean tick(final Tickable ticking, final int tickID)
	{
		if(tickID != Tickable.TICKID_PROPERTY_SPECIAL)
			return super.tick(ticking, tickID);
		if((!(affected instanceof Item)) || ((Item)affected).amDestroyed())
		{
			this.ticking = false;
			return false;
		}
		if(((Item)affected).usesRemaining() > 0)
		{
			this.ticking = false;
			mended();
			return false;
		}
		return true;
	}
}
