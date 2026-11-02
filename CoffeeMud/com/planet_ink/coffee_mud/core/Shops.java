package com.planet_ink.coffee_mud.core;
import com.planet_ink.coffee_mud.core.interfaces.*;
import com.planet_ink.coffee_mud.Common.interfaces.*;
import com.planet_ink.coffee_mud.Items.interfaces.*;
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
 * W17 and W14: shops for scripts: the shopkeeper meant, a product by name,
 * a price as a customer would pay it, in the shop's coin names.
 */
public final class Shops
{
	private Shops()
	{
	}

	/**
	 * @param E a thing
	 * @return its shop, or null if it keeps none
	 */
	public static ShopKeeper shopOf(final Environmental E)
	{
		return (E == null) ? null : CMLib.coffeeShops().getShopKeeper(E);
	}

	/**
	 * The first shopkeeper in a room other than a character.
	 * @param R the room
	 * @param not a mob to pass over, or null
	 * @return the shopkeeper, or null
	 */
	public static MOB keeperIn(final Room R, final MOB not)
	{
		if(R == null)
			return null;
		for(final Enumeration<MOB> m = R.inhabitants(); m.hasMoreElements();)
		{
			final MOB M = m.nextElement();
			if((M != null) && (M != not) && M.isMonster() && (shopOf(M) != null))
				return M;
		}
		return null;
	}

	/**
	 * A product of a shop: the thing itself, else what it stocks by that name.
	 * @param SK the shop
	 * @param thing the thing a script found, or null
	 * @param name its name, to look for in the stock
	 * @param cust the customer, or null
	 * @return the product, or null
	 */
	public static Environmental product(final ShopKeeper SK, final Environmental thing, final String name, final MOB cust)
	{
		if(thing != null)
			return thing;
		if((SK == null) || (name == null) || (name.trim().length() == 0))
			return null;
		return SK.getShop().getStock(name.trim(), cust);
	}

	/**
	 * What a shopkeeper asks a customer for a thing, as BUY would.
	 * @param shopM the shopkeeper
	 * @param cust the customer, or null for the shopkeeper's price to anyone
	 * @param product the thing
	 * @return the price, or null if it keeps no shop
	 */
	public static ShopKeeper.ShopPrice price(final MOB shopM, final MOB cust, final Environmental product)
	{
		final ShopKeeper SK = shopOf(shopM);
		if((SK == null) || (product == null))
			return null;
		final MOB C = (cust == null) ? shopM : cust;
		return CMLib.coffeeShops().sellingPrice(shopM, C, product, SK, SK.getShop(C), true);
	}

	/**
	 * A price in words: the shop's coin names (or experience, or quest points).
	 * @param shopM the shopkeeper
	 * @param p the price
	 * @return the words
	 */
	public static String words(final MOB shopM, final ShopKeeper.ShopPrice p)
	{
		if(p == null)
			return "";
		if(p.experiencePrice > 0)
			return p.experiencePrice + " experience";
		if(p.questPointPrice > 0)
			return p.questPointPrice + " quest points";
		return CMLib.beanCounter().nameCurrencyLong(CMLib.beanCounter().getCurrency(shopM), p.absoluteGoldPrice);
	}

	/**
	 * A number as a script shows it: whole without a point.
	 * @param d the number
	 * @return the text
	 */
	public static String num(final double d)
	{
		return (d == Math.rint(d)) ? ("" + (long)d) : ("" + d);
	}
}
