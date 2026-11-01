package com.planet_ink.coffee_mud.Commands;
import com.planet_ink.coffee_mud.core.interfaces.*;
import com.planet_ink.coffee_mud.core.*;
import com.planet_ink.coffee_mud.core.collections.*;
import com.planet_ink.coffee_mud.Abilities.interfaces.*;
import com.planet_ink.coffee_mud.Areas.interfaces.*;
import com.planet_ink.coffee_mud.Common.interfaces.*;
import com.planet_ink.coffee_mud.Common.interfaces.TimeClock.TimePeriod;
import com.planet_ink.coffee_mud.Items.interfaces.*;
import com.planet_ink.coffee_mud.Locales.interfaces.*;
import com.planet_ink.coffee_mud.MOBS.interfaces.*;

import java.util.*;
import java.util.function.Predicate;

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
 * DT22, for builders: PRICECHECK item [AT shopkeeper] [FOR character] [SELL]
 * shows how a shop's price for something was reached: each setting and where
 * it came from (the shopkeeper, its area, a parent area or the ini, and a
 * quest that gave it), each step of the sum as the shop library makes it,
 * both prices, the budget left and when the stock is next put back.
 */
public class PriceCheckCmd extends StdCommand
{
	public PriceCheckCmd()
	{
	}

	private final String[] access = I(new String[] { "PRICECHECK" });

	@Override
	public String[] getAccessWords()
	{
		return access;
	}

	private static String byQuest(final Environmental E, final String stat)
	{
		for(final Enumeration<Quest> q = CMLib.quests().enumQuests(); q.hasMoreElements();)
		{
			final Quest Q = q.nextElement();
			if((Q != null) && Q.running() && Q.gaveStat(E, stat))
				return ", given by the quest " + Q.name();
		}
		return "";
	}

	private static String areaSource(final Area start, final Area A, final String stat, final Predicate<Economics> has, final Set<Area> seen)
	{
		if((A == null) || (!seen.add(A)))
			return null;
		if(has.test(A))
			return ((A == start) ? "the area " : "the parent area ") + A.Name() + byQuest(A, stat);
		for(final Enumeration<Area> p = A.getParents(); p.hasMoreElements();)
		{
			final String s = areaSource(start, p.nextElement(), stat, has, seen);
			if(s != null)
				return s;
		}
		return null;
	}

	/**
	 * Where a shop's setting comes from: the shop itself, its area or a parent
	 * area, or the ini, as the getFinal* methods find it.
	 * @param shopM the shopkeeper mob
	 * @param SK the shop
	 * @param stat the setting's stat name
	 * @param ini the ini key it falls back to
	 * @param has whether a shop or area has its own
	 * @return the words
	 */
	public static String source(final MOB shopM, final ShopKeeper SK, final String stat, final String ini, final Predicate<Economics> has)
	{
		if(has.test(SK))
			return "set on " + shopM.name() + byQuest((SK instanceof Environmental) ? (Environmental)SK : shopM, stat);
		Area A = CMLib.map().getStartArea(shopM);
		if(A == null)
			A = CMLib.map().areaLocation(shopM);
		final String s = areaSource(A, A, stat, has, new HashSet<Area>());
		if(s != null)
			return s;
		return "the ini (" + ini + ")";
	}

	private static String ticks(final long ticks)
	{
		final long perHour = Math.max(1, CMProps.getTicksPerMudHour());
		final long hours = (ticks + perHour - 1) / perHour;
		return ticks + ((ticks == 1) ? " tick" : " ticks") + " (about " + hours + ((hours == 1) ? " game hour)" : " game hours)");
	}

	/**
	 * The settings a shop prices by, each with where it came from.
	 * @param shopM the shopkeeper mob
	 * @param SK the shop
	 * @param custM the customer, for the ignore mask
	 * @return the lines
	 */
	public static String settings(final MOB shopM, final ShopKeeper SK, final MOB custM)
	{
		final StringBuilder str = new StringBuilder("Settings:");
		final String prejudice = SK.getFinalPrejudiceFactors();
		str.append("\n\r  PREJUDICE ").append((prejudice.trim().length() == 0) ? "(none)" : prejudice).append(", ")
			.append(source(shopM, SK, "PREJUDICE", "PREJUDICE", E -> E.getRawPrejudiceFactors().length() > 0));
		final String[] masks = SK.getFinalItemPricingAdjustments();
		str.append("\n\r  PRICEMASKS ").append((masks.length == 0) ? "(none)" : String.join("; ", masks)).append(", ")
			.append(source(shopM, SK, "PRICEMASKS", "PRICEFACTORS",
				E -> (E.getRawItemPricingAdjustments() != null) && (E.getRawItemPricingAdjustments().length > 0)));
		final Pair<Long, TimePeriod> budget = SK.getFinalBudget();
		final boolean noLimit = (budget == null) || (budget.first.longValue() >= (Long.MAX_VALUE / 4));
		str.append("\n\r  BUDGET ").append(noLimit ? "(no limit)" : (budget.first + " a " + budget.second.name().toLowerCase())).append(", ")
			.append(source(shopM, SK, "BUDGET", "BUDGET", E -> E.getRawBbudget().length() > 0));
		final double[] rates = SK.getFinalDevalueRate();
		str.append("\n\r  DEVALRATE ").append(((rates == null) || ((rates[0] <= 0.0) && (rates[1] <= 0.0))) ? "(none)"
				: (CMath.toPct(rates[0]) + " an item and " + CMath.toPct(rates[1]) + " a raw material, for each in stock")).append(", ")
			.append(source(shopM, SK, "DEVALRATE", "DEVALUERATE", E -> E.getRawDevalueRate().length() > 0));
		final int reset = SK.getFinalInvResetRate();
		str.append("\n\r  INVRESETRATE ").append((reset <= 0) ? "(never)" : (reset + " ticks")).append(", ")
			.append(source(shopM, SK, "INVRESETRATE", "INVRESETRATE", E -> E.getRawInvResetRate() != 0));
		final String ignore = SK.getFinalIgnoreMask();
		if(ignore.trim().length() > 0)
		{
			str.append("\n\r  IGNOREMASK ").append(ignore).append(", ")
				.append(source(shopM, SK, "IGNOREMASK", "IGNOREMASK", E -> E.getRawIgnoreMask().length() > 0));
			if((custM != null) && CMLib.masking().maskCheck(ignore, custM, false))
				str.append(": ").append(custM.name()).append(" is ignored");
		}
		return str.toString();
	}

	/**
	 * The budget left and the time to the next stock reset.
	 * @param shopM the shopkeeper mob
	 * @param SK the shop
	 * @return the lines
	 */
	public static String budgetAndReset(final MOB shopM, final ShopKeeper SK)
	{
		final StringBuilder str = new StringBuilder();
		final long left = SK.budgetLeft();
		final Pair<Long, TimePeriod> budget = SK.getFinalBudget();
		final boolean noLimit = (budget == null) || (budget.first.longValue() >= (Long.MAX_VALUE / 4));
		if(left < 0)
			str.append("Budget left: not kept by this shop.");
		else
		if(left >= (Long.MAX_VALUE / 4))
			str.append("Budget left: ").append(noLimit ? "no limit." : "not counted until its first reset.");
		else
			str.append("Budget left: ").append(CMLib.beanCounter().nameCurrencyShort(shopM, left)).append(" (").append(left).append(").");
		if((SK.ticksToBudgetReset() >= 0) && (!noLimit))
			str.append(" It resets in ").append(ticks(SK.ticksToBudgetReset())).append(".");
		final int reset = SK.ticksToInventoryReset();
		str.append("\n\r").append((reset < 0) ? "The stock is never put back." : "The stock is put back in " + ticks(reset) + ".");
		if(!shopM.isGeneric())
			str.append(" (Only generic shopkeepers reset.)");
		return str.toString();
	}

	/**
	 * A shopkeeper in a room, by name, or else the first one not a player
	 * there (a player's own shop, such as Merchant's, is asked for by name).
	 * @param mob the one asking, never the answer
	 * @param R the room
	 * @param name the name, or null
	 * @return the shopkeeper mob, or null
	 */
	public static MOB findShopkeeper(final MOB mob, final Room R, final String name)
	{
		if(name != null)
		{
			final MOB M = R.fetchInhabitant(name);
			return ((M != null) && (CMLib.coffeeShops().getShopKeeper(M) != null)) ? M : null;
		}
		for(final Enumeration<MOB> m = R.inhabitants(); m.hasMoreElements();)
		{
			final MOB M = m.nextElement();
			if((M != null) && (M != mob) && M.isMonster() && (CMLib.coffeeShops().getShopKeeper(M) != null))
				return M;
		}
		return null;
	}

	/**
	 * The whole PRICECHECK report.
	 * @param shopM the shopkeeper mob
	 * @param custM the customer
	 * @param product the thing priced
	 * @param pawn true for what the shop pays, false for what it asks
	 * @return the report
	 */
	public static String report(final MOB shopM, final MOB custM, final Environmental product, final boolean pawn)
	{
		final ShopKeeper SK = CMLib.coffeeShops().getShopKeeper(shopM);
		final StringBuilder str = new StringBuilder();
		str.append("Pricing ").append(product.name()).append(" at ").append(shopM.name()).append(" for ").append(custM.name()).append(":\n\r");
		if(!SK.doISellThis(product))
			str.append("(").append(shopM.name()).append(" does not buy or sell such things: these are the prices if it did.)\n\r");
		str.append(settings(shopM, SK, custM)).append("\n\r");
		str.append(pawn ? "What the shop pays for it, step by step:" : "What the shop asks for it, step by step:");
		for(final String line : CMLib.coffeeShops().explainPrice(shopM, custM, product, SK, pawn))
			str.append("\n\r  ").append(line);
		final CoffeeShop shop = SK.getShop(custM);
		final ShopKeeper.ShopPrice sells = CMLib.coffeeShops().sellingPrice(shopM, custM, product, SK, shop, true);
		final ShopKeeper.ShopPrice pays = CMLib.coffeeShops().pawningPrice(shopM, custM, product, SK);
		str.append("\n\rSells for ").append(CMLib.coffeeShops().describePrice(shopM, sells))
			.append(". Pays ").append(CMLib.coffeeShops().describePrice(shopM, pays)).append(".");
		str.append("\n\r").append(budgetAndReset(shopM, SK));
		return str.toString();
	}

	@Override
	public boolean execute(final MOB mob, final List<String> commands, final int metaFlags)
		throws java.io.IOException
	{
		final Room R = mob.location();
		if(R == null)
			return false;
		final List<String> itemWords = new ArrayList<String>();
		final List<String> atWords = new ArrayList<String>();
		final List<String> forWords = new ArrayList<String>();
		boolean pawn = false;
		List<String> into = itemWords;
		for(int i = 1; i < commands.size(); i++)
		{
			final String w = commands.get(i);
			if(w.equalsIgnoreCase("AT") && (i > 1))
				into = atWords;
			else
			if(w.equalsIgnoreCase("FOR") && (i > 1))
				into = forWords;
			else
			if(w.equalsIgnoreCase("SELL") && (i > 1) && (i == commands.size() - 1))
				pawn = true;
			else
				into.add(w);
		}
		if(itemWords.isEmpty())
		{
			mob.tell(L("PRICECHECK [ITEM] (AT [SHOPKEEPER]) (FOR [CHARACTER]) (SELL)"));
			return false;
		}
		final String itemName = CMParms.combine(itemWords, 0);
		final MOB shopM = findShopkeeper(mob, R, atWords.isEmpty() ? null : CMParms.combine(atWords, 0));
		if(shopM == null)
		{
			mob.tell(atWords.isEmpty() ? L("There is no shopkeeper here.") : L("There is no shopkeeper '@x1' here.", CMParms.combine(atWords, 0)));
			return false;
		}
		MOB custM = mob;
		if(!forWords.isEmpty())
		{
			custM = R.fetchInhabitant(CMParms.combine(forWords, 0));
			if(custM == null)
			{
				mob.tell(L("There is no '@x1' here.", CMParms.combine(forWords, 0)));
				return false;
			}
		}
		final ShopKeeper SK = CMLib.coffeeShops().getShopKeeper(shopM);
		Environmental product = null;
		if(pawn)
		{
			product = custM.fetchItem(null, Wearable.FILTER_ANY, itemName);
			if((product == null) && (custM != mob))
				product = mob.fetchItem(null, Wearable.FILTER_ANY, itemName);
			if(product == null)
				product = SK.getShop(custM).getStock(itemName, custM);
		}
		else
		{
			product = SK.getShop(custM).getStock(itemName, custM);
			if(product == null)
				product = custM.fetchItem(null, Wearable.FILTER_ANY, itemName);
			if((product == null) && (custM != mob))
				product = mob.fetchItem(null, Wearable.FILTER_ANY, itemName);
		}
		if(product == null)
			product = R.findItem(null, itemName);
		if(product == null)
		{
			mob.tell(L("Neither @x1's stock, @x2 nor the room has '@x3'.", shopM.name(), custM.name(), itemName));
			return false;
		}
		mob.tell(report(shopM, custM, product, pawn));
		return false;
	}

	@Override
	public boolean canBeOrdered()
	{
		return false;
	}

	@Override
	public boolean securityCheck(final MOB mob)
	{
		return EnvToolsCmd.mayChange(mob) || CMSecurity.isAllowedAnywhere(mob, CMSecurity.SecFlag.CMDMOBS);
	}
}
