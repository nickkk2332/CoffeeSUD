package com.planet_ink.coffee_mud.Abilities.Properties;
import com.planet_ink.coffee_mud.core.interfaces.*;
import com.planet_ink.coffee_mud.core.*;
import com.planet_ink.coffee_mud.core.collections.*;
import com.planet_ink.coffee_mud.Abilities.interfaces.*;
import com.planet_ink.coffee_mud.Common.interfaces.*;
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
 * C19: respawns that follow the story.  On a mob or an item with a rejuv:
 * WHEN="[mask]" is checked as it would come back (on the mob, there; for an
 * item, on someone in its room), and if it fails, ELSE=WAIT (the default:
 * it tries again later), NEVER (it stays gone) or REPLACE:name (the catalog's
 * mob or item, or a class, comes in its place, for good).
 */
public class Prop_RejuvWhen extends Property
{
	@Override
	public String ID()
	{
		return "Prop_RejuvWhen";
	}

	@Override
	public String name()
	{
		return "Comes back only when";
	}

	@Override
	protected int canAffectCode()
	{
		return Ability.CAN_MOBS | Ability.CAN_ITEMS;
	}

	@Override
	public String accountForYourself()
	{
		return "Comes back only when the story allows";
	}

	protected MaskingLibrary.CompiledZMask	when	= null;
	protected String						orElse	= "WAIT";

	@Override
	public void setMiscText(final String newText)
	{
		super.setMiscText(newText);
		final Map<String, String> q = CMParms.parseExactParms((newText == null) ? "" : newText);
		final String w = CMParms.getParmStr(q, "WHEN", "").trim();
		when = (w.length() == 0) ? null : CMLib.masking().getPreCompiledMask(w);
		final String e = CMParms.getParmStr(q, "ELSE", "WAIT").trim();
		orElse = e.toUpperCase().startsWith("REPLACE:") ? ("REPLACE:" + e.substring(8).trim()) : e.toUpperCase();
	}

	/**
	 * @return the mask, or null
	 */
	public MaskingLibrary.CompiledZMask when()
	{
		return when;
	}

	/**
	 * @return WAIT, NEVER or REPLACE:name
	 */
	public String orElse()
	{
		return orElse;
	}
}
