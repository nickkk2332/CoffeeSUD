package com.planet_ink.coffee_mud.core;
import com.planet_ink.coffee_mud.core.interfaces.*;
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
 * I25: what things weigh, for pressure plates (Prop_PressurePlate) and
 * scripts (ROOMWEIGHT, CONTENTWEIGHT).
 */
public final class Weights
{
	private Weights()
	{
	}

	/**
	 * @param M a mob
	 * @return its weight and all it carries
	 */
	public static int mob(final MOB M)
	{
		int w = M.phyStats().weight();
		for(final Enumeration<Item> i = M.items(); i.hasMoreElements();)
		{
			final Item I = i.nextElement();
			if(I != null)
				w += I.phyStats().weight();
		}
		return w;
	}

	/**
	 * @param M a mob
	 * @param mask what counts, or null
	 * @return whether it counts
	 */
	public static boolean counts(final Physical P, final MaskingLibrary.CompiledZMask mask)
	{
		return (P != null) && ((mask == null) || CMLib.masking().maskCheck(mask, P, true));
	}

	/**
	 * What is in a room weighs: the mobs (what they carry too) and the items.
	 * @param R the room
	 * @param mask what counts, or null for all
	 * @return the weight
	 */
	public static int room(final Room R, final MaskingLibrary.CompiledZMask mask)
	{
		int w = 0;
		if(R == null)
			return 0;
		for(final Enumeration<MOB> m = R.inhabitants(); m.hasMoreElements();)
		{
			final MOB M = m.nextElement();
			if((M != null) && (!M.amDead()) && counts(M, mask))
				w += mob(M);
		}
		for(final Enumeration<Item> i = R.items(); i.hasMoreElements();)
		{
			final Item I = i.nextElement();
			if(counts(I, mask))
				w += I.phyStats().weight();
		}
		return w;
	}

	/**
	 * What is in or on a thing weighs: its contents, and its riders.
	 * @param I the thing
	 * @param mask what counts, or null for all
	 * @return the weight
	 */
	public static int contents(final Item I, final MaskingLibrary.CompiledZMask mask)
	{
		int w = 0;
		if(I instanceof Container)
		{
			for(final Item C : ((Container)I).getContents())
			{
				if(counts(C, mask))
					w += C.phyStats().weight();
			}
		}
		if(I instanceof Rideable)
		{
			for(final Enumeration<Rider> r = ((Rideable)I).riders(); r.hasMoreElements();)
			{
				final Rider X = r.nextElement();
				if((X instanceof MOB) && counts((MOB)X, mask))
					w += mob((MOB)X);
				else
				if((X instanceof Item) && counts((Item)X, mask))
					w += ((Item)X).phyStats().weight();
			}
		}
		return w;
	}

	/**
	 * Whether a number meets a test: &gt;=120, &lt;50, =3, or 120 (at least).
	 * @param test the test
	 * @param n the number
	 * @return true if it does
	 */
	public static boolean meets(final String test, final int n)
	{
		final String t = test.replace(" ", "");
		if(t.startsWith(">="))
			return n >= CMath.s_int(t.substring(2));
		if(t.startsWith("<="))
			return n <= CMath.s_int(t.substring(2));
		if(t.startsWith(">"))
			return n > CMath.s_int(t.substring(1));
		if(t.startsWith("<"))
			return n < CMath.s_int(t.substring(1));
		if(t.startsWith("="))
			return n == CMath.s_int(t.substring(1));
		return n >= CMath.s_int(t);
	}
}
