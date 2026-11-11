package com.planet_ink.coffee_mud.core;

import java.io.IOException;
import java.util.*;

import com.planet_ink.coffee_mud.Abilities.interfaces.Ability;
import com.planet_ink.coffee_mud.Items.interfaces.Item;
import com.planet_ink.coffee_mud.Locales.interfaces.Room;
import com.planet_ink.coffee_mud.MOBS.interfaces.MOB;
import com.planet_ink.coffee_mud.core.MiniJSON.JSONObject;
import com.planet_ink.coffee_mud.core.interfaces.Physical;

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
 * I14: offer books.  A seller (a mob or an item with Prop_OfferBook) keeps a
 * few offers drawn from its list of catalog items, by weight, from the
 * campaign's offers stream; the book is kept in the property's text, so it is
 * saved with the seller, and is drawn again only when its refresh comes.  A
 * player can hold one offer, which stays through rerolls and refreshes until
 * it is bought or released; a reroll draws the other offers again for a fee,
 * paid and drawn as one transaction.  OFFERS shows and works the book.
 * The property is reached through its text only: core code never names its
 * class (the engine loads properties with its own class loader).
 */
public final class OfferBooks
{
	private OfferBooks()
	{
	}

	public static final String	PROPERTY	= "Prop_OfferBook";
	public static final String	FAMILY		= "offers";

	/** a book's settings, and the book itself */
	public static final class Book
	{
		public final List<String>	names	= new ArrayList<String>();
		public final List<Double>	weights	= new ArrayList<Double>();
		public final int			size, price, reroll;
		public final String			refresh;
		public final JSONObject		raw;

		Book(final String text) throws IOException
		{
			raw = ModuleDefinitions.parse((text == null) || text.trim().isEmpty() ? "{}" : text);
			ModuleDefinitions.keys(raw, "offer book", "items", "size", "price", "reroll", "refresh", "book");
			for(final Object o : ModuleDefinitions.array(raw.get("items"), "items"))
			{
				if(o instanceof JSONObject)
				{
					final JSONObject j = (JSONObject)o;
					ModuleDefinitions.keys(j, "offer item", "name", "weight");
					names.add(ModuleDefinitions.string(j.get("name"), "item name"));
					final Object w = j.get("weight");
					final double weight = (w == null) ? 1.0 : (w instanceof Number) ? ((Number)w).doubleValue() : -1;
					if(!(weight > 0) || (weight > 1000000))
						throw new IOException("an item's weight is above 0, at most 1000000");
					weights.add(Double.valueOf(weight));
				}
				else
				{
					names.add(ModuleDefinitions.string(o, "item name"));
					weights.add(Double.valueOf(1.0));
				}
			}
			if(names.isEmpty() || (names.size() > 32))
				throw new IOException("an offer book lists 1 to 32 catalog items");
			size = bounded(raw.get("size"), "size", 3, 1, 8);
			price = bounded(raw.get("price"), "price", 100, 1, 10000);
			reroll = bounded(raw.get("reroll"), "reroll", 0, 0, 1000000);
			refresh = raw.containsKey("refresh") ? ModuleDefinitions.string(raw.get("refresh"), "refresh") : "1 mudday";
		}

		private static int bounded(final Object v, final String what, final int dflt, final int min, final int max) throws IOException
		{
			if(v == null)
				return dflt;
			if(!(v instanceof Number) || (((Number)v).doubleValue() != Math.rint(((Number)v).doubleValue())))
				throw new IOException(what + " must be a whole number");
			final long n = ((Number)v).longValue();
			if((n < min) || (n > max))
				throw new IOException(what + " must be " + min + " to " + max);
			return (int)n;
		}

		/** the offers now, each {name, price, held}; none before the first draw */
		List<JSONObject> offers() throws IOException
		{
			final List<JSONObject> out = new ArrayList<JSONObject>();
			final JSONObject b = raw.containsKey("book") ? ModuleDefinitions.object(raw.get("book"), "book") : new JSONObject();
			if(b.containsKey("offers"))
			{
				for(final Object o : ModuleDefinitions.array(b.get("offers"), "offers"))
					out.add(ModuleDefinitions.copy((JSONObject)o));
			}
			return out;
		}

		long drawn() throws IOException
		{
			final JSONObject b = raw.containsKey("book") ? ModuleDefinitions.object(raw.get("book"), "book") : new JSONObject();
			return (b.get("drawn") instanceof Number) ? ((Number)b.get("drawn")).longValue() : -1;
		}
	}

	/** @return the settings and book of a seller's property, checked */
	public static Book read(final Ability prop) throws IOException
	{
		return new Book(prop.text());
	}

	/** the seller here with a book: the first mob, then the first item */
	public static Physical seller(final Room R)
	{
		if(R == null)
			return null;
		for(int i = 0; i < R.numInhabitants(); i++)
		{
			final MOB M = R.fetchInhabitant(i);
			if((M != null) && (M.fetchEffect(PROPERTY) != null))
				return M;
		}
		for(int i = 0; i < R.numItems(); i++)
		{
			final Item I = R.getItem(i);
			if((I != null) && (I.fetchEffect(PROPERTY) != null))
				return I;
		}
		return null;
	}

	private static Physical here(final MOB mob) throws IOException
	{
		if((mob == null) || (mob.location() == null))
			throw new IOException("there is no one here to deal with");
		final Physical S = seller(mob.location());
		if(S == null)
			throw new IOException("no one here keeps an offer book");
		return S;
	}

	private static String currency(final Physical seller)
	{
		return CMLib.beanCounter().getCurrency(seller);
	}

	private static String money(final Physical seller, final double amount)
	{
		return CMLib.beanCounter().nameCurrencyShort(currency(seller), amount);
	}

	/** an item's price in the book: its value times the book's price percent, at least 1 */
	private static long priceOf(final Book b, final String name) throws IOException
	{
		final Item I = CMLib.catalog().getCatalogItem(name);
		if(I == null)
			throw new IOException("no catalog item '" + name + "' in the offer book");
		return Math.max(1, Math.round(I.baseGoldValue() * b.price / 100.0));
	}

	/** draws the offers that are not held, by weight, without the same item twice, from a cursor on the offers stream */
	private static List<JSONObject> draw(final Book b, final JSONObject held, final RandomStreams.Cursor c) throws IOException
	{
		final List<Integer> left = new ArrayList<Integer>();
		for(int i = 0; i < b.names.size(); i++)
		{
			if((held == null) || !b.names.get(i).equals(held.get("name")))
				left.add(Integer.valueOf(i));
		}
		final List<JSONObject> out = new ArrayList<JSONObject>();
		if(held != null)
			out.add(held);
		while((out.size() < b.size) && !left.isEmpty())
		{
			double total = 0;
			for(final Integer i : left)
				total += b.weights.get(i.intValue()).doubleValue();
			double at = ((c.draw() >>> 11) * 0x1.0p-53) * total;
			Integer chosen = left.get(left.size() - 1);
			for(final Integer i : left)
			{
				at -= b.weights.get(i.intValue()).doubleValue();
				if(at < 0)
				{
					chosen = i;
					break;
				}
			}
			left.remove(chosen);
			final JSONObject o = new JSONObject();
			o.put("name", b.names.get(chosen.intValue()));
			o.put("price", Long.valueOf(priceOf(b, b.names.get(chosen.intValue()))));
			o.put("held", Boolean.FALSE);
			out.add(o);
		}
		return out;
	}

	private static void settle(final RandomStreams.Cursor c, final long start)
	{
		for(long i = start; i < c.counter(); i++)
			RandomStreams.draw(RandomStreams.OFFERS);
	}

	private static void store(final Ability prop, final Book b, final List<JSONObject> offers, final long drawn) throws IOException
	{
		final JSONObject next = ModuleDefinitions.copy(b.raw), book = new JSONObject();
		book.put("offers", offers.toArray());
		book.put("drawn", Long.valueOf(drawn));
		next.put("book", book);
		prop.setMiscText(ModuleDefinitions.json(next));
	}

	private static JSONObject heldOf(final List<JSONObject> offers)
	{
		for(final JSONObject o : offers)
		{
			if(Boolean.TRUE.equals(o.get("held")))
				return o;
		}
		return null;
	}

	/** brings a book up to date: the first time, or when its refresh has come, the offers not held are drawn again */
	private static Book current(final Physical seller, final MOB reader) throws IOException
	{
		final Ability prop = seller.fetchEffect(PROPERTY);
		final Book b = read(prop);
		final long drawn = b.drawn(), now = GameTime.now();
		if((drawn >= 0) && ((now - drawn) < QuestBoards.refreshMs("REFRESH=\"" + b.refresh + "\"", reader)))
			return b;
		final RandomStreams.Cursor c = RandomStreams.peek(RandomStreams.OFFERS);
		final long start = c.counter();
		final List<JSONObject> offers = draw(b, heldOf(b.offers()), c);
		store(prop, b, offers, now);
		settle(c, start);
		ModuleStats.count(FAMILY, seller.Name(), drawn < 0 ? "first draws" : "refreshes");
		return read(prop);
	}

	/** OFFERS: the book here */
	public static String show(final MOB mob) throws IOException
	{
		final Physical S = here(mob);
		final Book b = current(S, mob);
		final List<JSONObject> offers = b.offers();
		final StringBuilder s = new StringBuilder(S.Name()).append("'s offers:");
		if(offers.isEmpty())
			s.append(" none left until the book is drawn again.");
		for(int i = 0; i < offers.size(); i++)
		{
			final JSONObject o = offers.get(i);
			s.append("\n  ").append(i + 1).append(") ").append(o.get("name")).append(" - ").append(money(S, ((Number)o.get("price")).doubleValue()))
				.append(Boolean.TRUE.equals(o.get("held")) ? " [held for you]" : "");
		}
		final long left = QuestBoards.refreshMs("REFRESH=\"" + b.refresh + "\"", mob) - (GameTime.now() - b.drawn());
		s.append("\nOFFERS BUY [number] buys one; OFFERS HOLD [number] keeps one through rerolls and refreshes (one at a time).");
		if(b.reroll > 0)
			s.append(" OFFERS REROLL draws the others again for ").append(money(S, b.reroll)).append('.');
		final long hour = Math.max(1, CMProps.getMillisPerMudHour());
		s.append(" The book is drawn again in ").append(Math.max(1, (Math.max(0, left) + hour - 1) / hour)).append(" mud hour(s).");
		return s.toString();
	}

	private static JSONObject offer(final List<JSONObject> offers, final int n) throws IOException
	{
		if((n < 1) || (n > offers.size()))
			throw new IOException(offers.isEmpty() ? "there are no offers now" : "choose 1 to " + offers.size());
		return offers.get(n - 1);
	}

	/** OFFERS HOLD n: keeps one offer through rerolls and refreshes; holding another lets the first go */
	public static String hold(final MOB mob, final int n) throws IOException
	{
		final Physical S = here(mob);
		final Book b = current(S, mob);
		final List<JSONObject> offers = b.offers();
		final JSONObject chosen = offer(offers, n);
		for(final JSONObject o : offers)
			o.put("held", Boolean.valueOf(o == chosen));
		store(S.fetchEffect(PROPERTY), b, offers, b.drawn());
		Provenance.event(mob, FAMILY, S.Name(), "held the offer of " + chosen.get("name"));
		return S.Name() + " holds " + chosen.get("name") + " for you. It stays through rerolls and refreshes until you buy it or OFFERS RELEASE it.";
	}

	/** OFFERS RELEASE: lets the held offer go back to the book */
	public static String release(final MOB mob) throws IOException
	{
		final Physical S = here(mob);
		final Book b = current(S, mob);
		final List<JSONObject> offers = b.offers();
		final JSONObject held = heldOf(offers);
		if(held == null)
			throw new IOException("you hold no offer here");
		held.put("held", Boolean.FALSE);
		store(S.fetchEffect(PROPERTY), b, offers, b.drawn());
		return "You let " + held.get("name") + " go back to the book.";
	}

	private static void pay(final MOB mob, final Physical seller, final double amount) throws IOException
	{
		final double have = CMLib.beanCounter().getTotalAbsoluteValue(mob, currency(seller));
		if(have < amount)
			throw new IOException("that costs " + money(seller, amount) + "; you have " + money(seller, have));
	}

	/** OFFERS REROLL: draws again every offer not held, for the book's fee; paid and drawn as one */
	public static String reroll(final MOB mob) throws IOException
	{
		final Physical S = here(mob);
		final Book b = current(S, mob);
		if(b.reroll <= 0)
			throw new IOException(S.Name() + " does not draw the book again on request");
		pay(mob, S, b.reroll); // refused before anything is drawn
		final RandomStreams.Cursor c = RandomStreams.peek(RandomStreams.OFFERS);
		final long start = c.counter();
		final List<JSONObject> offers = draw(b, heldOf(b.offers()), c);
		final Ability prop = S.fetchEffect(PROPERTY);
		final String before = prop.text();
		store(prop, b, offers, b.drawn());
		try
		{
			CMLib.beanCounter().subtractMoney(mob, currency(S), b.reroll);
		}
		catch(final RuntimeException e)
		{
			prop.setMiscText(before);
			throw e;
		}
		settle(c, start);
		ModuleStats.count(FAMILY, S.Name(), "rerolls");
		Provenance.event(mob, FAMILY, S.Name(), "paid " + money(S, b.reroll) + " to draw the offer book again");
		final StringBuilder s = new StringBuilder("You pay ").append(money(S, b.reroll)).append(" and ").append(S.Name()).append(" draws the book again:");
		for(int i = 0; i < offers.size(); i++)
			s.append("\n  ").append(i + 1).append(") ").append(offers.get(i).get("name")).append(" - ").append(money(S, ((Number)offers.get(i).get("price")).doubleValue()))
				.append(Boolean.TRUE.equals(offers.get(i).get("held")) ? " [held for you]" : "");
		return s.toString();
	}

	/** OFFERS BUY n: buys one offer at its price; its place stays empty until the book is drawn again */
	public static String buy(final MOB mob, final int n) throws IOException
	{
		final Physical S = here(mob);
		final Book b = current(S, mob);
		final List<JSONObject> offers = b.offers();
		final JSONObject chosen = offer(offers, n);
		final double price = ((Number)chosen.get("price")).doubleValue();
		pay(mob, S, price);
		final Item proto = CMLib.catalog().getCatalogItem((String)chosen.get("name"));
		if(proto == null)
			throw new IOException("no catalog item '" + chosen.get("name") + "' to sell");
		final Item made = (Item)proto.copyOf();
		CMLib.catalog().changeCatalogUsage(made, true);
		offers.remove(chosen);
		final Ability prop = S.fetchEffect(PROPERTY);
		final String before = prop.text();
		store(prop, b, offers, b.drawn());
		try
		{
			CMLib.beanCounter().subtractMoney(mob, currency(S), price);
		}
		catch(final RuntimeException e)
		{
			prop.setMiscText(before);
			made.destroy();
			throw e;
		}
		mob.addItem(made);
		ModuleStats.count(FAMILY, S.Name(), "sales");
		Provenance.event(mob, FAMILY, S.Name(), "bought " + made.Name() + " for " + money(S, price));
		Provenance.event(made, FAMILY, S.Name(), "bought by " + mob.Name() + " for " + money(S, price));
		return "You buy " + made.Name() + " from " + S.Name() + " for " + money(S, price) + ".";
	}

	/** what the property says of itself, and WORLD CHECK: why its text is wrong, or "" */
	public static String problem(final String text)
	{
		try
		{
			final Book b = new Book(text);
			for(final String name : b.names)
			{
				if(CMLib.catalog().getCatalogItem(name) == null)
					return "no catalog item '" + name + "'";
			}
			return "";
		}
		catch(final IOException e)
		{
			return e.getMessage();
		}
	}
}
