package com.planet_ink.coffee_mud.core;
import com.planet_ink.coffee_mud.core.interfaces.*;
import com.planet_ink.coffee_mud.Abilities.interfaces.*;
import com.planet_ink.coffee_mud.Common.interfaces.*;
import com.planet_ink.coffee_mud.Items.interfaces.*;
import com.planet_ink.coffee_mud.Libraries.interfaces.*;
import com.planet_ink.coffee_mud.Locales.interfaces.*;
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
 * C19: whether a mob or item may come back now (Prop_RejuvWhen), asked by
 * StdMOB's rejuv and ItemRejuv before they bring it back.
 */
public final class RejuvRules
{
	private RejuvRules()
	{
	}

	/**
	 * What its Prop_RejuvWhen says.
	 * @param P the mob, or the item's copy to come back
	 * @param home where it comes back
	 * @return null to come back, or WAIT, NEVER or REPLACE:name
	 */
	public static String verdict(final Physical P, final Room home)
	{
		if((P == null) || (home == null))
			return null;
		final Ability A = P.fetchEffect("Prop_RejuvWhen");
		if(A == null)
			return null;
		final String spec = A.text();
		final java.util.Map<String, String> q = CMParms.parseExactParms(spec);
		final String w = CMParms.getParmStr(q, "WHEN", "").trim();
		if(w.length() == 0)
			return null;
		final MaskingLibrary.CompiledZMask mask = CMLib.masking().getPreCompiledMask(w);
		boolean ok;
		if(P instanceof MOB)
		{
			// the mob, as if there: its room's hour, season and text, and its own state
			final MOB M = (MOB)P;
			final Room was = M.location();
			M.setLocation(home);
			try
			{
				ok = CMLib.masking().maskCheck(mask, M, true);
			}
			finally
			{
				M.setLocation(was);
			}
		}
		else
		{
			final MOB M = CMLib.map().getFactoryMOB(home);
			try
			{
				ok = CMLib.masking().maskCheck(mask, M, true);
			}
			finally
			{
				M.destroy();
			}
		}
		if(ok)
			return null;
		final String e = CMParms.getParmStr(q, "ELSE", "WAIT").trim();
		return e.toUpperCase().startsWith("REPLACE:") ? ("REPLACE:" + e.substring(8).trim()) : e.toUpperCase();
	}

	/**
	 * REPLACE:name: the catalog's (or a class's) mob or item, there for good.
	 * @param verdict the verdict
	 * @param home where
	 * @param mob true for a mob, false for an item
	 * @return what came, or null
	 */
	public static Physical replace(final String verdict, final Room home, final boolean mob)
	{
		if((verdict == null) || (!verdict.startsWith("REPLACE:")) || (home == null))
			return null;
		final String name = verdict.substring(8).trim();
		final Physical P = Encounters.make(name, mob);
		if(P instanceof MOB)
		{
			final MOB M = (MOB)P;
			M.setStartRoom(home);
			M.text();
			M.bringToLife(home, true);
			home.showOthers(M, null, CMMsg.MSG_OK_ACTION, CMLib.lang().L("<S-NAME> appears!"));
			return M;
		}
		if(P instanceof Item)
		{
			home.addItem((Item)P);
			return P;
		}
		Log.errOut("RejuvRules", "nothing called " + name + " to come back in its place");
		return null;
	}
}
