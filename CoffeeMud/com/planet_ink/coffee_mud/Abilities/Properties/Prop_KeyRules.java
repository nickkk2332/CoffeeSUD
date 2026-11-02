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
 * I21: rules for a key.  CONSUME=true: it is used up when it unlocks;
 * BREAK=n: n percent of the time it snaps when it is used; RETURN=room id:
 * once used it is gone back there; MASK=: who may use it (MSG= what the
 * others are told).
 */
public class Prop_KeyRules extends Property
{
	@Override
	public String ID()
	{
		return "Prop_KeyRules";
	}

	@Override
	public String name()
	{
		return "Key rules";
	}

	@Override
	protected int canAffectCode()
	{
		return Ability.CAN_ITEMS;
	}

	@Override
	public String accountForYourself()
	{
		return "A key with rules";
	}

	protected boolean						consume		= false;
	protected int							breakPct	= 0;
	protected String						returnTo	= "";
	protected MaskingLibrary.CompiledZMask	mask		= null;
	protected String						maskMsg		= "";

	@Override
	public void setMiscText(final String newText)
	{
		super.setMiscText(newText);
		final Map<String, String> q = CMParms.parseExactParms((newText == null) ? "" : newText);
		consume = CMParms.getParmBool(q, "CONSUME", false);
		breakPct = Math.max(0, Math.min(100, CMParms.getParmInt(q, "BREAK", 0)));
		returnTo = CMParms.getParmStr(q, "RETURN", "").trim();
		final String m = CMParms.getParmStr(q, "MASK", "").trim();
		mask = (m.length() == 0) ? null : CMLib.masking().getPreCompiledMask(m);
		maskMsg = CMParms.getParmStr(q, "MSG", "The key will not turn for you.");
	}

	/** whether this key is the one a lock message will use: it is carried, and fits */
	protected boolean fitsTarget(final CMMsg msg)
	{
		if((!(affected instanceof DoorKey)) || (msg.source() == null) || (!msg.source().isMine(affected)) || (!Keys.reachable((Item)affected)))
			return false;
		if(!fits((DoorKey)affected, msg))
			return false;
		// another key that fits, and may be used, will do instead
		for(final Enumeration<Item> i = msg.source().items(); i.hasMoreElements();)
		{
			final Item I = i.nextElement();
			if((I != affected) && (I instanceof DoorKey) && Keys.reachable(I) && fits((DoorKey)I, msg))
			{
				final Ability A = I.fetchEffect(ID());
				if(!(A instanceof Prop_KeyRules) || (((Prop_KeyRules)A).mask == null)
				|| CMLib.masking().maskCheck(((Prop_KeyRules)A).mask, msg.source(), true))
					return false;
			}
		}
		return true;
	}

	protected static boolean fits(final DoorKey K, final CMMsg msg)
	{
		final String key = K.getKey();
		if(msg.target() instanceof Exit)
			return Keys.fits(key, ((Exit)msg.target()).keyName(), (Exit)msg.target());
		if(msg.target() instanceof Container)
			return Keys.fits(key, ((Container)msg.target()).keyName(), (Container)msg.target());
		return false;
	}

	@Override
	public boolean okMessage(final Environmental myHost, final CMMsg msg)
	{
		if(!super.okMessage(myHost, msg))
			return false;
		if((mask != null)
		&& ((msg.targetMinor() == CMMsg.TYP_UNLOCK) || (msg.targetMinor() == CMMsg.TYP_LOCK))
		&& ((msg.tool() == affected) || ((msg.tool() == null) && fitsTarget(msg)))
		&& (!CMLib.masking().maskCheck(mask, msg.source(), true)))
		{
			msg.source().tell(maskMsg);
			return false;
		}
		return true;
	}

	@Override
	public void executeMsg(final Environmental myHost, final CMMsg msg)
	{
		super.executeMsg(myHost, msg);
		if((!(affected instanceof Item)) || (msg.tool() != affected)
		|| ((msg.targetMinor() != CMMsg.TYP_UNLOCK) && (msg.targetMinor() != CMMsg.TYP_LOCK)))
			return;
		final Item key = (Item)affected;
		final MOB mob = msg.source();
		if((msg.targetMinor() == CMMsg.TYP_UNLOCK) && consume)
		{
			mob.tell(mob, key, null, CMLib.lang().L("<T-NAME> crumble(s) away in the lock."));
			key.destroy();
			return;
		}
		if((breakPct > 0) && (CMLib.dice().rollPercentage() <= breakPct))
		{
			mob.tell(mob, key, null, CMLib.lang().L("<T-NAME> snap(s) in the lock!"));
			key.destroy();
			return;
		}
		if(returnTo.length() > 0)
		{
			final Room R = CMLib.map().getRoom(returnTo);
			if(R != null)
			{
				mob.tell(mob, key, null, CMLib.lang().L("<T-NAME> vanish(es) from your hand."));
				key.unWear();
				key.setContainer(null);
				R.moveItemTo(key, ItemPossessor.Expire.Never);
			}
		}
	}
}
