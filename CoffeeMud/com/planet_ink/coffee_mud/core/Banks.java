package com.planet_ink.coffee_mud.core;
import com.planet_ink.coffee_mud.core.interfaces.*;
import com.planet_ink.coffee_mud.Common.interfaces.*;
import com.planet_ink.coffee_mud.Items.interfaces.*;
import com.planet_ink.coffee_mud.Libraries.interfaces.*;
import com.planet_ink.coffee_mud.MOBS.interfaces.*;

import com.planet_ink.coffee_mud.core.collections.*;

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
 * W15: bank accounts and debts for scripts and JavaScript: a balance read
 * or changed with a ledger line, as a banker would, and debts made, paid
 * and cleared.  Both are the money library's own records, which a save
 * keeps.
 */
public final class Banks
{
	private Banks()
	{
	}

	/**
	 * The currency a bank chain keeps.
	 * @param chain the chain
	 * @return its currency's code ("" for the default)
	 */
	public static String currency(final String chain)
	{
		final String c = CMLib.beanCounter().getBankChainCurrency(chain);
		return (c == null) ? "" : c;
	}

	/**
	 * A depositor's coins at a bank chain.
	 * @param owner the depositor's name
	 * @param chain the chain
	 * @param currency a currency, or null for the account's own
	 * @return the balance in base value, 0 if there is no account
	 */
	public static double balance(final String owner, final String chain, final String currency)
	{
		final Pair<String, Double> P = CMLib.beanCounter().getBankBalance(chain, owner, ((currency == null) || (currency.length() == 0)) ? null : currency);
		return (P == null) ? 0.0 : P.second.doubleValue();
	}

	/**
	 * Pays into or takes from an account (MPBANK), opening it for a
	 * payment in, with a ledger line as a banker writes one.
	 * @param owner the depositor's name
	 * @param chain the chain
	 * @param delta the amount, in base value: a minus takes it out
	 * @param reason why, for the ledger, or ""
	 * @param dated what to date the ledger line by (its area's calendar), or null
	 * @return false if there is not that much to take out
	 */
	public static boolean change(final String owner, final String chain, final double delta, final String reason, final Environmental dated)
	{
		if((owner == null) || (chain == null) || (chain.length() == 0) || (delta == 0.0))
			return false;
		final MoneyLibrary B = CMLib.beanCounter();
		final String currency = currency(chain);
		final String when = (dated == null) ? "" : (CMLib.utensils().getFormattedDate(dated) + ": ");
		final String line = when + ((delta > 0) ? "Deposit of " : "Withdrawal of ") + B.nameCurrencyShort(currency, Math.abs(delta))
			+ (((reason != null) && (reason.trim().length() > 0)) ? (": " + reason.trim()) : "") + ".";
		if(B.getBankBalance(chain, owner, null) != null)
			return B.modifyBankGold(chain, owner, line, currency, delta);
		if(delta < 0)
			return false;
		final Coins C = B.makeBestCurrency(currency, delta);
		if(C == null)
			return false;
		CMLib.database().DBCreatePlayerData(owner, chain, "" + C + C.hashCode(), "COINS;" + CMLib.coffeeMaker().getEnvironmentalMiscTextXML(C, true));
		C.destroy();
		B.addToBankLedger(chain, owner, line);
		return true;
	}

	/**
	 * @param owner the debtor's name
	 * @param owedTo to whom (a bank chain, or anyone)
	 * @return what they owe, in base value
	 */
	public static double debt(final String owner, final String owedTo)
	{
		return CMLib.beanCounter().getDebtOwed(owner, owedTo);
	}

	/**
	 * The game hours until a debt's first due date.
	 * @param owner the debtor's name
	 * @param owedTo to whom
	 * @param clock the clock whose hours count, or null for the world's
	 * @return the hours (less than 0 when past due), or null if they owe nothing
	 */
	public static Long hoursToDue(final String owner, final String owedTo, final TimeClock clock)
	{
		long first = Long.MAX_VALUE;
		for(final MoneyLibrary.DebtItem D : CMLib.beanCounter().getDebt(owner, owedTo))
			first = Math.min(first, D.due());
		if(first == Long.MAX_VALUE)
			return null;
		final long ms = first - GameTime.now();
		return Long.valueOf(ms / Math.max(1, CMProps.getMillisPerMudHour()));
	}

	/**
	 * Adds to a debt (MPDEBT +n), or pays some off (MPDEBT -n), the
	 * first due paid first.
	 * @param owner the debtor's name
	 * @param owedTo to whom
	 * @param amount the amount, in base value
	 * @param interest the interest each month, a fraction (0.05 for 5%)
	 * @param dueMillis the game time it is due, or 0 for no date
	 * @param reason what the debt is for
	 */
	public static void adjust(final String owner, final String owedTo, final double amount, final double interest, final long dueMillis, final String reason)
	{
		final MoneyLibrary B = CMLib.beanCounter();
		if(amount >= 0)
		{
			B.adjustDebt(owner, owedTo, amount, ((reason == null) || (reason.length() == 0)) ? "Debt" : reason, interest,
				(dueMillis > 0) ? dueMillis : (Long.MAX_VALUE / 2));
			return;
		}
		double left = -amount;
		final List<MoneyLibrary.DebtItem> debts = new ArrayList<MoneyLibrary.DebtItem>(B.getDebt(owner, owedTo));
		Collections.sort(debts, new Comparator<MoneyLibrary.DebtItem>()
		{
			@Override
			public int compare(final MoneyLibrary.DebtItem a, final MoneyLibrary.DebtItem b)
			{
				return Long.compare(a.due(), b.due());
			}
		});
		for(final MoneyLibrary.DebtItem D : debts)
		{
			if(left <= 0)
				break;
			final double take = Math.min(left, D.amt());
			B.adjustDebt(owner, owedTo, -take, D.reason(), D.interest(), D.due());
			left -= take;
		}
	}

	/**
	 * Clears every debt one owes another (MPDEBT CLEAR).
	 * @param owner the debtor's name
	 * @param owedTo to whom
	 */
	public static void clear(final String owner, final String owedTo)
	{
		CMLib.beanCounter().delAllDebt(owner, owedTo);
	}
}
