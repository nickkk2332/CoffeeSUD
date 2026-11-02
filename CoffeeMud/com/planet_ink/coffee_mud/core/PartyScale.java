package com.planet_ink.coffee_mud.core;
import com.planet_ink.coffee_mud.Areas.interfaces.*;
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
 * C6: whether a mob's fight scales to the party (Prop_PartyScaler on it, its
 * room or its area, or Prop_Doppleganger on it), for CONSIDER.
 */
public final class PartyScale
{
	private PartyScale()
	{
	}

	/**
	 * @param M a mob
	 * @return whether its fight scales to the party
	 */
	public static boolean scales(final MOB M)
	{
		if(M == null)
			return false;
		if((M.fetchEffect("Prop_PartyScaler") != null) || (M.fetchEffect("Prop_Doppleganger") != null))
			return true;
		final Room R = M.location();
		if(R == null)
			return false;
		if(R.fetchEffect("Prop_PartyScaler") != null)
			return true;
		final Area A = R.getArea();
		return (A != null) && (A.fetchEffect("Prop_PartyScaler") != null);
	}
}
