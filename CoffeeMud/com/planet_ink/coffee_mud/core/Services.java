package com.planet_ink.coffee_mud.core;

import java.io.IOException;
import java.util.*;

import com.planet_ink.coffee_mud.Abilities.interfaces.Ability;
import com.planet_ink.coffee_mud.Common.interfaces.Clan;
import com.planet_ink.coffee_mud.MOBS.interfaces.MOB;
import com.planet_ink.coffee_mud.core.MiniJSON.JSONObject;
import com.planet_ink.coffee_mud.core.collections.Pair;

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
 * P04: service records.  A Prop_Service on an NPC lists its services as JSON:
 * {"services": [{"id", "title", "words": [...], "mask", "refusal", "hours":
 * "6-20", "station", "cost": {"money": n} or {"pool": id, "units": n,
 * "org": name}}]}.  One record answers everyone who asks: TALK's menu offers
 * only the services one may have now, and says why the others not; SERVICES
 * lists them with their hours, cost and eligibility; and the service itself,
 * when one says its word to the NPC, is refused for the same reasons, before
 * the NPC's behaviours or scripts hear it, and otherwise paid (money, or the
 * organization's shared pool, with a receipt).
 */
public final class Services
{
	private Services()
	{
	}

	public static final String	PROPERTY	= "Prop_Service";
	public static final String	FAMILY		= "services";

	/** a service as its record says */
	public static final class Service
	{
		public final String			id, title, mask, refusal, station, pool, org;
		public final List<String>	words;
		public final int			from, to;
		public final long			money, units;
		/** when its cost is kept: on asking ("ask"), or held on asking and kept only when the NPC's script says DONE ("done") */
		public final boolean		onDone;
		/** how many game ticks a "done" service's hold waits for DONE before it is returned */
		public final int			hold;

		Service(final JSONObject o) throws IOException
		{
			ModuleDefinitions.keys(o, "service", "id", "title", "words", "mask", "refusal", "hours", "station", "cost", "pay", "hold");
			final String payWhen = o.containsKey("pay") ? ModuleDefinitions.string(o.get("pay"), "pay").toLowerCase(Locale.ROOT) : "ask";
			if(!payWhen.equals("ask") && !payWhen.equals("done"))
				throw new IOException("pay is ask or done");
			onDone = payWhen.equals("done");
			hold = o.containsKey("hold") ? (int)Math.min(100000, ModuleDefinitions.count(o.get("hold"), "hold")) : 30;
			id = ModuleDefinitions.id(o.get("id"));
			title = o.containsKey("title") ? ModuleDefinitions.string(o.get("title"), "title") : id;
			final List<String> w = new ArrayList<String>();
			for(final Object x : ModuleDefinitions.array(o.get("words"), "words"))
				w.add(ModuleDefinitions.string(x, "word").trim().toLowerCase(Locale.ROOT));
			if(w.isEmpty())
				throw new IOException(id + ": a service is asked for by at least one word");
			words = Collections.unmodifiableList(w);
			mask = o.containsKey("mask") ? ModuleDefinitions.string(o.get("mask"), "mask") : "";
			refusal = o.containsKey("refusal") ? ModuleDefinitions.string(o.get("refusal"), "refusal") : "";
			station = o.containsKey("station") ? ModuleDefinitions.id(o.get("station")) : "";
			if(o.containsKey("hours"))
			{
				final String h = ModuleDefinitions.string(o.get("hours"), "hours");
				final String[] p = h.split("-");
				if((p.length != 2) || !CMath.isInteger(p[0].trim()) || !CMath.isInteger(p[1].trim()))
					throw new IOException(id + ": hours are from-to, as 6-20");
				from = CMath.s_int(p[0].trim());
				to = CMath.s_int(p[1].trim());
			}
			else
			{
				from = -1;
				to = -1;
			}
			final JSONObject c = o.containsKey("cost") ? ModuleDefinitions.object(o.get("cost"), "cost") : new JSONObject();
			ModuleDefinitions.keys(c, "cost", "money", "pool", "units", "org");
			money = c.containsKey("money") ? ModuleDefinitions.count(c.get("money"), "money") : 0;
			pool = c.containsKey("pool") ? ModuleDefinitions.string(c.get("pool"), "pool").toUpperCase(Locale.ROOT) : "";
			units = c.containsKey("units") ? ModuleDefinitions.count(c.get("units"), "units") : (pool.isEmpty() ? 0 : 1);
			org = c.containsKey("org") ? ModuleDefinitions.string(c.get("org"), "org") : "";
		}

		/** its cost in words, in the NPC's currency, or "" when free */
		public String cost(final MOB npc)
		{
			if(money > 0)
				return CMLib.beanCounter().nameCurrencyShort(CMLib.beanCounter().getCurrency(npc), money);
			if(!pool.isEmpty())
				return units + " from " + (org.isEmpty() ? "your organization" : org) + "'s " + pool.toLowerCase(Locale.ROOT);
			return "";
		}
	}

	/** an NPC's services; none without the property or with a text that cannot be read (WORLD CHECK says why) */
	public static List<Service> of(final MOB npc)
	{
		final List<Service> out = new ArrayList<Service>();
		final Ability A = (npc == null) ? null : npc.fetchEffect(PROPERTY);
		if(A == null)
			return out;
		try
		{
			for(final Object o : ModuleDefinitions.array(ModuleDefinitions.parse(A.text()).get("services"), "services"))
				out.add(new Service(ModuleDefinitions.object(o, "service")));
		}
		catch(final IOException e)
		{
			Log.errOut("Services", npc.Name() + ": " + e.getMessage());
		}
		return out;
	}

	/** why a text cannot be read as service records, or "" */
	public static String problem(final String text)
	{
		try
		{
			for(final Object o : ModuleDefinitions.array(ModuleDefinitions.parse(text).get("services"), "services"))
				new Service(ModuleDefinitions.object(o, "service"));
			return "";
		}
		catch(final IOException e)
		{
			return e.getMessage();
		}
	}

	private static int hour(final MOB npc)
	{
		return CMLib.time().localClock(npc).getHourOfDay();
	}

	/** the organization a service's pool is paid from for someone: the one it names (if they are in it), or their first that holds the pool */
	private static Clan orgFor(final MOB who, final Service s)
	{
		for(final Pair<Clan, Integer> p : who.clans())
		{
			final Clan C = p.first;
			if((C != null) && (s.org.isEmpty() || C.getName().equalsIgnoreCase(s.org)))
				return C;
		}
		return null;
	}

	/** why someone may not have a service now, or "": the hours, the station, the mask, the cost */
	public static String why(final MOB who, final MOB npc, final Service s)
	{
		if((s.from >= 0) && (npc != null))
		{
			final int h = hour(npc);
			final boolean open = (s.from <= s.to) ? ((h >= s.from) && (h < s.to)) : ((h >= s.from) || (h < s.to));
			if(!open)
				return CMLib.lang().L("closed until @x1 o'clock", "" + s.from);
		}
		if(!s.station.isEmpty() && ((npc == null) || !Workstations.has(npc.location(), s.station)))
			return CMLib.lang().L("@x1 works only at the @x2", (npc == null) ? "it" : npc.name(), s.station);
		if(!s.mask.isEmpty() && !CMLib.masking().maskCheck(s.mask, who, true))
			return s.refusal.isEmpty() ? CMLib.lang().L("only for @x1", CMLib.masking().maskDesc(s.mask)) : s.refusal;
		if((s.money > 0) && (CMLib.beanCounter().getTotalAbsoluteValue(who, CMLib.beanCounter().getCurrency(npc)) < s.money))
			return CMLib.lang().L("it costs @x1", CMLib.beanCounter().nameCurrencyShort(CMLib.beanCounter().getCurrency(npc), s.money));
		if(!s.pool.isEmpty())
		{
			final Clan C = orgFor(who, s);
			if(C == null)
				return CMLib.lang().L("only for members of @x1", s.org.isEmpty() ? "an organization" : s.org);
			try
			{
				final String rank = OrgPools.whyNotSpend(who, C, s.pool);
				if(!rank.isEmpty())
					return rank;
				if(OrgPools.units(C, s.pool) < s.units)
					return CMLib.lang().L("@x1's @x2 holds too little", C.getName(), s.pool.toLowerCase(Locale.ROOT));
			}
			catch(final IOException e)
			{
				return e.getMessage();
			}
		}
		return "";
	}

	/** the service a said text asks for: one of its words among the words said (a word may be several, as "mend armor") */
	public static Service asked(final MOB npc, final String said)
	{
		final String heard = " " + said.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", " ").trim() + " ";
		for(final Service s : of(npc))
		{
			for(final String w : s.words)
			{
				if(heard.contains(" " + w.replaceAll("[^a-z0-9]+", " ").trim() + " "))
					return s;
			}
		}
		return null;
	}

	/**
	 * The service is asked for: its cost is taken.  Kept at once ("ask"), or
	 * held ("done") until the NPC's script says DONE, and given back if it
	 * says REFUND or the hold runs out.  Each payment is kept in the record
	 * (the last 16), so a script can give back the latest.
	 * @return what to tell them
	 */
	public static String pay(final MOB who, final MOB npc, final Service s) throws IOException
	{
		ModuleStats.count(FAMILY, s.id, "given");
		final JSONObject entry = new JSONObject();
		entry.put("who", who.Name());
		entry.put("id", s.id);
		entry.put("state", s.onDone ? "held" : "paid");
		entry.put("at", Long.valueOf(GameTime.now()));
		if(s.onDone)
			entry.put("until", Long.valueOf(GameTime.now() + ((long)s.hold * CMProps.getTickMillis())));
		String said = "";
		if(s.money > 0)
		{
			final String cur = CMLib.beanCounter().getCurrency(npc);
			CMLib.beanCounter().subtractMoney(who, cur, s.money);
			entry.put("money", Long.valueOf(s.money));
			entry.put("currency", cur);
			said = s.onDone ? CMLib.lang().L("You hand over @x1 for @x2; it is held until it is done.", CMLib.beanCounter().nameCurrencyShort(cur, s.money), s.title)
							: CMLib.lang().L("You pay @x1 for @x2.", CMLib.beanCounter().nameCurrencyShort(cur, s.money), s.title);
		}
		else
		if(!s.pool.isEmpty())
		{
			final Clan C = orgFor(who, s);
			if(C == null)
				throw new IOException("only for members of " + (s.org.isEmpty() ? "an organization" : s.org));
			final long receipt = OrgPools.spend(C, s.pool, s.units, who.Name(), s.title);
			OrgPools.note(who, C, "spent", s.pool, s.units, receipt);
			entry.put("org", C.getName());
			entry.put("pool", s.pool);
			entry.put("units", Long.valueOf(s.units));
			entry.put("receipt", Long.valueOf(receipt));
			said = CMLib.lang().L("@x1's @x2 pays @x3 for @x4 (receipt #@x5)@x6.", C.getName(), s.pool.toLowerCase(Locale.ROOT), "" + s.units, s.title, "" + receipt,
				s.onDone ? CMLib.lang().L("; it is held until it is done") : "");
		}
		Provenance.event(who, FAMILY, npc.Name() + " " + s.id, (s.onDone ? "held " : "paid ") + (s.cost(npc).isEmpty() ? "nothing" : s.cost(npc)) + " for " + s.title);
		if(entry.containsKey("money") || entry.containsKey("pool") || s.onDone)
			keep(npc, entry);
		return said;
	}

	/** the record's root, to keep payments in */
	private static JSONObject root(final MOB npc) throws IOException
	{
		final Ability A = npc.fetchEffect(PROPERTY);
		if(A == null)
			throw new IOException(npc.Name() + " keeps no service records");
		return ModuleDefinitions.parse(A.text());
	}

	private static List<JSONObject> payments(final JSONObject root) throws IOException
	{
		final List<JSONObject> out = new ArrayList<JSONObject>();
		if(root.containsKey("payments"))
		{
			for(final Object o : ModuleDefinitions.array(root.get("payments"), "payments"))
				out.add((JSONObject)o);
		}
		return out;
	}

	private static void store(final MOB npc, final JSONObject root, final List<JSONObject> pays)
	{
		while(pays.size() > 16)
			pays.remove(0);
		root.put("payments", pays.toArray());
		npc.fetchEffect(PROPERTY).setMiscText(ModuleDefinitions.json(root));
	}

	private static void keep(final MOB npc, final JSONObject entry) throws IOException
	{
		final JSONObject root = root(npc);
		final List<JSONObject> pays = payments(root);
		pays.add(entry);
		store(npc, root, pays);
	}

	/** the latest payment of someone for a service in one of these states, or null */
	private static JSONObject latest(final List<JSONObject> pays, final String who, final String id, final String... states)
	{
		for(int i = pays.size() - 1; i >= 0; i--)
		{
			final JSONObject p = pays.get(i);
			if(String.valueOf(p.get("who")).equalsIgnoreCase(who) && String.valueOf(p.get("id")).equalsIgnoreCase(id) && Arrays.asList(states).contains(String.valueOf(p.get("state"))))
				return p;
		}
		return null;
	}

	/** gives a payment back: the money to its payer, or the units to the pool with a receipt */
	private static String giveBack(final MOB npc, final JSONObject p, final MOB payer, final String why) throws IOException
	{
		String back = "";
		if(p.get("money") instanceof Number)
		{
			if(payer == null)
				throw new IOException(p.get("who") + " is not here to take the money back");
			final String cur = String.valueOf(p.get("currency"));
			final double amt = ((Number)p.get("money")).doubleValue();
			CMLib.beanCounter().giveSomeoneMoney(payer, cur, amt);
			back = CMLib.beanCounter().nameCurrencyShort(cur, amt);
		}
		else
		if(p.get("units") instanceof Number)
		{
			final Clan C = CMLib.clans().getClan(String.valueOf(p.get("org")));
			if(C == null)
				throw new IOException("no organization " + p.get("org") + " to give back to");
			final long n = ((Number)p.get("units")).longValue();
			final long receipt = OrgPools.refund(C, String.valueOf(p.get("pool")), n, String.valueOf(p.get("who")), "refund of #" + p.get("receipt"));
			OrgPools.note(payer, C, "refunded", String.valueOf(p.get("pool")), n, receipt);
			back = CMLib.lang().L("@x1 unit(s) to @x2's @x3 (receipt #@x4)", "" + n, C.getName(), String.valueOf(p.get("pool")).toLowerCase(Locale.ROOT), "" + receipt);
		}
		p.put("state", "refunded");
		ModuleStats.count(FAMILY, String.valueOf(p.get("id")), "refunds");
		if(payer != null)
		{
			Provenance.event(payer, FAMILY, npc.Name() + " " + p.get("id"), "refunded " + (back.isEmpty() ? "nothing" : back) + " (" + why + ")");
			payer.tell(CMLib.lang().L("@x1 gives back @x2 for @x3 (@x4).", npc.name(payer), back.isEmpty() ? CMLib.lang().L("nothing") : back, title(npc, String.valueOf(p.get("id"))), why));
		}
		return back;
	}

	private static String title(final MOB npc, final String id)
	{
		for(final Service s : of(npc))
		{
			if(s.id.equalsIgnoreCase(id))
				return s.title;
		}
		return id;
	}

	/** the payer, if they are in the game: in the NPC's room, or a player anywhere */
	private static MOB present(final MOB npc, final String name)
	{
		if(npc.location() != null)
		{
			for(int i = 0; i < npc.location().numInhabitants(); i++)
			{
				final MOB M = npc.location().fetchInhabitant(i);
				if((M != null) && M.Name().equalsIgnoreCase(name))
					return M;
			}
		}
		final MOB M = CMLib.players().getPlayer(name);
		return ((M != null) && !M.amDestroyed()) ? M : null;
	}

	/** MPSERVICE who DONE id: a held payment is kept; @return what happened */
	public static String done(final MOB npc, final MOB who, final String id) throws IOException
	{
		final JSONObject root = root(npc);
		final List<JSONObject> pays = payments(root);
		final JSONObject p = latest(pays, who.Name(), id, "held");
		if(p == null)
			throw new IOException("no held payment of " + who.Name() + " for " + id);
		p.put("state", "paid");
		store(npc, root, pays);
		Provenance.event(who, FAMILY, npc.Name() + " " + id, "kept the held payment for " + title(npc, id));
		return CMLib.lang().L("@x1 is done; the payment is kept.", title(npc, id));
	}

	/** MPSERVICE who REFUND id: the latest payment (held or kept) is given back; @return what happened */
	public static String refund(final MOB npc, final MOB who, final String id) throws IOException
	{
		final JSONObject root = root(npc);
		final List<JSONObject> pays = payments(root);
		final JSONObject p = latest(pays, who.Name(), id, "held", "paid");
		if(p == null)
			throw new IOException("no payment of " + who.Name() + " for " + id + " to give back");
		giveBack(npc, p, who, CMLib.lang().L("refunded"));
		store(npc, root, pays);
		return CMLib.lang().L("@x1 is refunded.", title(npc, id));
	}

	/** holds past their time are given back (the NPC's ticks); a payer not in the game now is given back when they are */
	public static void expire(final MOB npc)
	{
		final Ability A = npc.fetchEffect(PROPERTY);
		if((A == null) || (A.text().indexOf("\"held\"") < 0))
			return;
		try
		{
			final JSONObject root = root(npc);
			final List<JSONObject> pays = payments(root);
			boolean changed = false;
			for(final JSONObject p : pays)
			{
				if(!"held".equals(p.get("state")) || !(p.get("until") instanceof Number) || (((Number)p.get("until")).longValue() > GameTime.now()))
					continue;
				final MOB payer = present(npc, String.valueOf(p.get("who")));
				if((payer == null) && (p.get("money") instanceof Number))
					continue;
				giveBack(npc, p, payer, CMLib.lang().L("it was not done in time"));
				changed = true;
			}
			if(changed)
				store(npc, root, pays);
		}
		catch(final IOException e)
		{
			Log.errOut("Services", npc.Name() + ": " + e.getMessage());
		}
	}

	/** someone's held payments at an NPC, in words, for SERVICES and LEDGER */
	public static List<String> held(final MOB npc, final MOB who)
	{
		final List<String> out = new ArrayList<String>();
		try
		{
			for(final JSONObject p : payments(root(npc)))
			{
				if("held".equals(p.get("state")) && String.valueOf(p.get("who")).equalsIgnoreCase(who.Name()))
					out.add(title(npc, String.valueOf(p.get("id"))) + " at " + npc.Name());
			}
		}
		catch(final IOException e)
		{
		}
		return out;
	}

	/** TALK's menu choices for the services one may have now */
	public static List<String[]> menu(final MOB who, final MOB npc)
	{
		final List<String[]> out = new ArrayList<String[]>();
		for(final Service s : of(npc))
		{
			if(why(who, npc, s).isEmpty())
				out.add(new String[] { "SVC_" + s.id.toUpperCase(Locale.ROOT), s.title + (s.cost(npc).isEmpty() ? "" : " (" + s.cost(npc) + ")"), "SAYTO \"" + npc.name() + "\" " + s.words.get(0) });
		}
		return out;
	}

	/** the services one may not have now, each with why */
	public static List<String> notNow(final MOB who, final MOB npc)
	{
		final List<String> out = new ArrayList<String>();
		for(final Service s : of(npc))
		{
			final String why = why(who, npc, s);
			if(!why.isEmpty())
				out.add(s.title + ": " + why);
		}
		return out;
	}

	/** SERVICES: the services of everyone here, each with its word, cost, hours and whether one may have it now */
	public static String list(final MOB who)
	{
		final StringBuilder s = new StringBuilder();
		if(who.location() != null)
		{
			for(int i = 0; i < who.location().numInhabitants(); i++)
			{
				final MOB npc = who.location().fetchInhabitant(i);
				final List<Service> all = of(npc);
				if(all.isEmpty())
					continue;
				s.append(s.length() > 0 ? "\n" : "").append(npc.name(who)).append(':');
				for(final Service v : all)
				{
					final String why = why(who, npc, v);
					s.append("\n  ").append(v.title).append(" - say ").append(v.words.get(0)).append(v.cost(npc).isEmpty() ? "" : "; " + v.cost(npc))
						.append(v.onDone ? "; paid when done" : "")
						.append((v.from >= 0) ? "; open " + v.from + "-" + v.to : "").append(why.isEmpty() ? "; you may" : "; not now: " + why);
				}
				for(final String h : held(npc, who))
					s.append("\n  Held for you: ").append(h);
			}
		}
		return (s.length() == 0) ? CMLib.lang().L("No one here offers services.") : s.toString();
	}
}
