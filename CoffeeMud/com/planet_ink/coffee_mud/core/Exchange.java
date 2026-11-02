package com.planet_ink.coffee_mud.core;

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
 * W29: the world's exchange rates (MPEXCHANGE): what coins of a currency
 * fetch at every money changer, in percent of their value (100 as they
 * are), kept in the save.
 */
public final class Exchange
{
	private Exchange()
	{
	}

	/** each currency's rate, in percent, where it is not 100 */
	private static final Map<String, Double>	rates	= new TreeMap<String, Double>();

	private static String key(final String code)
	{
		final String c = (code == null) ? "" : code.trim().toUpperCase();
		if(c.equals("DEFAULT"))
			return "";
		return c;
	}

	/**
	 * Sets a currency's rate (MPEXCHANGE).
	 * @param code the currency's code, or DEFAULT for the world's default
	 * @param percent what its coins fetch, in percent of their value; 100 ends it
	 */
	public static synchronized void set(final String code, final double percent)
	{
		if(percent == 100.0)
			rates.remove(key(code));
		else
			rates.put(key(code), Double.valueOf(Math.max(0.0, percent)));
	}

	/**
	 * @param code a currency's code ("" for the default)
	 * @return what its coins fetch, in percent of their value
	 */
	public static synchronized double percent(final String code)
	{
		Double D = rates.get(key(code));
		if((D == null) && (key(code).length() == 0))
		{
			// coins of the default currency say "": the world's default (W2) by its code
			if(CMLib.beanCounter() instanceof com.planet_ink.coffee_mud.Libraries.BeanCounter)
				D = rates.get(key(((com.planet_ink.coffee_mud.Libraries.BeanCounter)CMLib.beanCounter()).defaultCurrencyCode()));
		}
		return (D == null) ? 100.0 : D.doubleValue();
	}

	/** @return the rates as a save keeps them: CODE=percent;CODE=percent */
	public static synchronized String saved()
	{
		final StringBuilder str = new StringBuilder();
		for(final Map.Entry<String, Double> e : rates.entrySet())
			str.append((str.length() > 0) ? ";" : "").append(e.getKey().length() == 0 ? "DEFAULT" : e.getKey()).append("=").append(e.getValue());
		return str.toString();
	}

	/**
	 * Puts back the rates a save had (none for a new game).
	 * @param saved the saved text, or null
	 */
	public static synchronized void restore(final String saved)
	{
		rates.clear();
		if(saved == null)
			return;
		for(final String part : saved.split(";"))
		{
			final int x = part.indexOf('=');
			if((x > 0) && CMath.isNumber(part.substring(x + 1).trim()))
				set(part.substring(0, x), CMath.s_double(part.substring(x + 1).trim()));
		}
	}
}
