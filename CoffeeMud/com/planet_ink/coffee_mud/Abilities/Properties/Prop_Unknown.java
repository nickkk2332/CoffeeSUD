package com.planet_ink.coffee_mud.Abilities.Properties;
import com.planet_ink.coffee_mud.core.interfaces.*;
import com.planet_ink.coffee_mud.core.*;
import com.planet_ink.coffee_mud.Abilities.interfaces.*;
import com.planet_ink.coffee_mud.Common.interfaces.*;
import com.planet_ink.coffee_mud.Items.interfaces.*;
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
 * A23: an item of a kind a character must learn (ItemKnowledge): KIND=,
 * NAME=, DISPLAY=, DESC=, VALUE=, REVEAL= (IDENTIFY, APPRAISE, EXAMINE, USE)
 * and MASK=.  Examining or using it reveals it when its REVEAL says so.
 */
public class Prop_Unknown extends Property
{
	@Override
	public String ID()
	{
		return "Prop_Unknown";
	}

	@Override
	public String name()
	{
		return "Unknown until learned";
	}

	@Override
	protected int canAffectCode()
	{
		return Ability.CAN_ITEMS;
	}

	@Override
	public String accountForYourself()
	{
		return "Of a kind (" + CMParms.getParmStr(text(), "KIND", "") + ") one must learn: "
			+ CMParms.getParmStr(text(), "REVEAL", "IDENTIFY").toLowerCase() + " reveals it";
	}

	@Override
	public void executeMsg(final Environmental myHost, final CMMsg msg)
	{
		super.executeMsg(myHost, msg);
		if(!(affected instanceof Item) || !msg.amITarget(affected) || (msg.source() == null))
			return;
		String how = null;
		switch(msg.targetMinor())
		{
		case CMMsg.TYP_EXAMINE:
			how = "EXAMINE";
			break;
		case CMMsg.TYP_WEAR:
		case CMMsg.TYP_WIELD:
		case CMMsg.TYP_HOLD:
			how = "USE";
			break;
		default:
			break;
		}
		if(how == null)
			return;
		final String learned = ItemKnowledge.reveal(msg.source(), (Item)affected, how);
		if(learned.length() > 0)
			msg.addTrailerMsg(CMClass.getMsg(msg.source(), null, null, CMMsg.MSG_OK_VISUAL | CMMsg.MASK_ALWAYS, learned, CMMsg.NO_EFFECT, null, CMMsg.NO_EFFECT, null));
	}
}
