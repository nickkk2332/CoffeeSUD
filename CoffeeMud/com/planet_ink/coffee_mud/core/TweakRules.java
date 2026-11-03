package com.planet_ink.coffee_mud.core;

import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;

import com.planet_ink.coffee_mud.Abilities.interfaces.Ability;
import com.planet_ink.coffee_mud.Common.interfaces.CMMsg;
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
 * P22: a tweak's DAMAGEMULT.  A tweak runs its original ability, so the
 * damage messages carry the original (or the effect it left) as their tool:
 * while a tweak casts, its original is the one named here, and the effects
 * its casts leave are marked, so a damage message with either as its tool is
 * scaled (StdMOB.okMessage, beside GameDifficulty's).
 */
public class TweakRules
{
	private TweakRules()
	{
	}

	private static final ThreadLocal<Object[]> casting = new ThreadLocal<Object[]>();
	private static final Map<Ability, Double> marked = Collections.synchronizedMap(new WeakHashMap<Ability, Double>());

	/**
	 * @param original the ability the tweak runs
	 * @param mult its DAMAGEMULT
	 */
	public static void begin(final Ability original, final double mult)
	{
		casting.set((mult == 1.0) ? null : new Object[] { original, Double.valueOf(mult) });
	}

	/** The cast is over. */
	public static void end()
	{
		casting.remove();
	}

	/**
	 * @param effect an effect a tweak's cast left
	 * @param mult its DAMAGEMULT
	 */
	public static void mark(final Ability effect, final double mult)
	{
		if((effect != null) && (mult != 1.0))
			marked.put(effect, Double.valueOf(mult));
	}

	/**
	 * Scales a damage message whose tool is a casting tweak's original, or an
	 * effect one left.
	 * @param msg the message
	 */
	public static void scaleDamage(final CMMsg msg)
	{
		if(!(msg.tool() instanceof Ability))
			return;
		Double m = marked.isEmpty() ? null : marked.get(msg.tool());
		if(m == null)
		{
			final Object[] c = casting.get();
			if((c != null) && (c[0] == msg.tool()))
				m = (Double)c[1];
		}
		if((m != null) && (msg.value() > 0))
			msg.setValue((int)Math.round(msg.value() * m.doubleValue()));
	}
}
