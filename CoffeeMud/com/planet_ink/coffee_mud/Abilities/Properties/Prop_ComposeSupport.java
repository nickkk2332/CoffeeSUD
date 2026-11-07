package com.planet_ink.coffee_mud.Abilities.Properties;
import com.planet_ink.coffee_mud.core.*;
import com.planet_ink.coffee_mud.Abilities.interfaces.*;
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
 * I05: an item that gives its wearer composed-skill supports while it is
 * worn or wielded, directly or socketed in something worn (a gem in
 * Prop_ItemSlot, through Prop_ItemSlotFiller's ADDS).  SUPPORTS=id,id: the
 * support definitions it gives; each is added to every composed skill it
 * fits, beyond the linked ones (at most two from gear).
 */
public class Prop_ComposeSupport extends Property implements AbilityComposition.SupportSource
{
	@Override
	public String ID()
	{
		return "Prop_ComposeSupport";
	}

	@Override
	public String name()
	{
		return "Gives composed-skill supports";
	}

	@Override
	protected int canAffectCode()
	{
		return Ability.CAN_ITEMS;
	}

	protected final List<String> supports = new ArrayList<String>();

	@Override
	public void setMiscText(final String newText)
	{
		super.setMiscText(newText);
		supports.clear();
		final String list = CMParms.getParmStr((newText == null) ? "" : newText, "SUPPORTS", "");
		for(final String id : CMParms.parseCommas(list, true))
		{
			if(id.trim().length() > 0)
				supports.add(id.trim().toLowerCase());
		}
	}

	@Override
	public List<String> heldSupports()
	{
		return Collections.unmodifiableList(supports);
	}

	@Override
	public String accountForYourself()
	{
		return "Gives the composed-skill supports " + supports + " while worn";
	}
}
