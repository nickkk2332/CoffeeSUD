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

		Service(final JSONObject o) throws IOException
		{
			ModuleDefinitions.keys(o, "service", "id", "title", "words", "mask", "refusal", "hours", "station", "cost");
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

	/** the service is given: its cost paid; @return what to tell them */
	public static String pay(final MOB who, final MOB npc, final Service s) throws IOException
	{
		ModuleStats.count(FAMILY, s.id, "given");
		if(s.money > 0)
		{
			final String cur = CMLib.beanCounter().getCurrency(npc);
			CMLib.beanCounter().subtractMoney(who, cur, s.money);
			return CMLib.lang().L("You pay @x1 for @x2.", CMLib.beanCounter().nameCurrencyShort(cur, s.money), s.title);
		}
		if(!s.pool.isEmpty())
		{
			final Clan C = orgFor(who, s);
			if(C == null)
				throw new IOException("only for members of " + (s.org.isEmpty() ? "an organization" : s.org));
			final long receipt = OrgPools.spend(C, s.pool, s.units, who.Name(), s.title);
			return CMLib.lang().L("@x1's @x2 pays @x3 for @x4 (receipt #@x5).", C.getName(), s.pool.toLowerCase(Locale.ROOT), "" + s.units, s.title, "" + receipt);
		}
		return "";
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
						.append((v.from >= 0) ? "; open " + v.from + "-" + v.to : "").append(why.isEmpty() ? "; you may" : "; not now: " + why);
				}
			}
		}
		return (s.length() == 0) ? CMLib.lang().L("No one here offers services.") : s.toString();
	}
}
