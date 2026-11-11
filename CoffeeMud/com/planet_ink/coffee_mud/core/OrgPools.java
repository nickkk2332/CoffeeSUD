package com.planet_ink.coffee_mud.core;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.*;

import com.planet_ink.coffee_mud.Common.interfaces.Clan;
import com.planet_ink.coffee_mud.MOBS.interfaces.MOB;
import com.planet_ink.coffee_mud.core.MiniJSON.JSONObject;
import com.planet_ink.coffee_mud.core.collections.Pair;
import com.planet_ink.coffee_mud.core.save.WorldFiles;

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
 * P08: shared pools an organization owns.  world/orgpools.ini declares each
 * kind of pool, one a line: [id]=TITLE="..." MAX=n (MONEY=n: the money one
 * unit costs a giver) (MATERIAL=name: or one weight unit of that plain raw
 * material).  Members give to their organization's pool (POOLS GIVE); a
 * service (Prop_Service, a script, cm.poolSpend) spends from it.  Every gift
 * and every spend goes in the organization's ledger with a receipt number,
 * and the ledger keeps each contributor's and each consumer's total; all of
 * it is kept in the organization's own settings (POOLS, POOLLEDGER), saved
 * with it.
 */
public final class OrgPools
{
	private OrgPools()
	{
	}

	public static final String FAMILY = "orgpools";

	/** a kind of pool */
	public static final class Kind
	{
		public final String	id, title, material;
		public final long	max, money;

		Kind(final String id, final String parms)
		{
			this.id = id.trim().toUpperCase(Locale.ROOT);
			title = CMParms.getParmStr(parms, "TITLE", this.id.toLowerCase(Locale.ROOT));
			max = Math.max(1, CMath.s_long(CMParms.getParmStr(parms, "MAX", "1000")));
			money = Math.max(0, CMath.s_long(CMParms.getParmStr(parms, "MONEY", "0")));
			material = CMParms.getParmStr(parms, "MATERIAL", "").trim().toUpperCase(Locale.ROOT);
		}
	}

	public static Map<String, Kind> kinds()
	{
		final Map<String, Kind> out = new TreeMap<String, Kind>();
		final File f = new File(WorldFiles.worldDir(), "orgpools.ini");
		if(!f.isFile())
			return out;
		try(final Reader r = Files.newBufferedReader(f.toPath(), StandardCharsets.UTF_8))
		{
			final Properties p = new Properties();
			p.load(r);
			for(final String key : p.stringPropertyNames())
				out.put(key.trim().toUpperCase(Locale.ROOT), new Kind(key, p.getProperty(key)));
		}
		catch(final IOException | IllegalArgumentException e)
		{
			Log.errOut("OrgPools", "orgpools.ini: " + e.getMessage());
		}
		return out;
	}

	public static List<String> problems()
	{
		final List<String> out = new ArrayList<String>();
		for(final Kind k : kinds().values())
		{
			if((k.money <= 0) && k.material.isEmpty())
				out.add("orgpools.ini: " + k.id + ": no one can give to it (MONEY or MATERIAL)");
		}
		return out;
	}

	private static Kind kind(final String id) throws IOException
	{
		final Kind k = kinds().get(id.trim().toUpperCase(Locale.ROOT));
		if(k == null)
			throw new IOException("no shared pool " + id.trim() + " in orgpools.ini");
		return k;
	}

	private static JSONObject json(final Clan C, final String key) throws IOException
	{
		final String raw = C.getStat(key);
		return (raw == null) || raw.trim().isEmpty() ? new JSONObject() : ModuleDefinitions.parse(raw);
	}

	/** the units in an organization's pool */
	public static long units(final Clan C, final String pool) throws IOException
	{
		final Object o = json(C, "POOLS").get(kind(pool).id);
		return (o instanceof Number) ? ((Number)o).longValue() : 0;
	}

	private static long record(final Clan C, final String kind, final String who, final String pool, final long units, final String what) throws IOException
	{
		final JSONObject L = json(C, "POOLLEDGER");
		final long serial = ((L.get("serial") instanceof Number) ? ((Number)L.get("serial")).longValue() : 0) + 1;
		L.put("serial", Long.valueOf(serial));
		final List<Object> entries = new ArrayList<Object>(L.containsKey("entries") ? Arrays.asList(ModuleDefinitions.array(L.get("entries"), "entries")) : new ArrayList<Object>());
		final JSONObject e = new JSONObject();
		e.put("receipt", Long.valueOf(serial));
		e.put("kind", kind);
		e.put("who", who);
		e.put("pool", pool);
		e.put("units", Long.valueOf(units));
		e.put("what", what);
		e.put("at", Long.valueOf(GameTime.now()));
		entries.add(e);
		while(entries.size() > 20)
			entries.remove(0);
		L.put("entries", entries.toArray());
		// each contributor's and each consumer's totals, by pool
		final String side = kind.equals("gave") ? "givers" : "users";
		final JSONObject totals = L.containsKey(side) ? (JSONObject)L.get(side) : new JSONObject();
		final JSONObject byPool = totals.containsKey(pool) ? (JSONObject)totals.get(pool) : new JSONObject();
		byPool.put(who, Long.valueOf(((byPool.get(who) instanceof Number) ? ((Number)byPool.get(who)).longValue() : 0) + units));
		totals.put(pool, byPool);
		L.put(side, totals);
		C.setStat("POOLLEDGER", ModuleDefinitions.json(L));
		return serial;
	}

	private static void put(final Clan C, final String pool, final long units) throws IOException
	{
		final JSONObject P = json(C, "POOLS");
		P.put(pool, Long.valueOf(units));
		C.setStat("POOLS", ModuleDefinitions.json(P));
	}

	/** POOLS GIVE: a member gives n units to their organization's pool, paid in money or material; @return what happened */
	public static String give(final MOB M, final Clan C, final String pool, final long n) throws IOException
	{
		final Kind k = kind(pool);
		if((M == null) || (M.getClanRole(C.clanID()) == null))
			throw new IOException("only members give to " + C.getName() + "'s " + k.title);
		if(n <= 0)
			throw new IOException("give at least one unit");
		final long now = units(C, k.id);
		if(now + n > k.max)
			throw new IOException(k.title + " holds at most " + k.max + "; it has " + now);
		synchronized(C)
		{
			if(k.money > 0)
			{
				final String cur = CMLib.beanCounter().getCurrency(M);
				final double price = (double)n * k.money;
				if(CMLib.beanCounter().getTotalAbsoluteValue(M, cur) < price)
					throw new IOException("that costs " + CMLib.beanCounter().nameCurrencyShort(cur, price));
				CMLib.beanCounter().subtractMoney(M, cur, price);
			}
			else
			{
				final List<ItemCrafting.Cost> costs = ItemCrafting.pay(M, null, k.material, (int)Math.min(Integer.MAX_VALUE, n));
				for(final ItemCrafting.Cost c : costs)
				{
					if(c.amount < c.weight)
						c.spend();
					else
					{
						M.delItem(c.item);
						c.item.destroy();
					}
				}
			}
			put(C, k.id, now + n);
			final long receipt = record(C, "gave", M.Name(), k.id, n, (k.money > 0) ? "money" : k.material.toLowerCase(Locale.ROOT));
			C.update();
			ModuleStats.count(FAMILY, k.id.toLowerCase(Locale.ROOT), "units given");
			return CMLib.lang().L("You give @x1 unit(s) to @x2's @x3 (receipt #@x4); it holds @x5.", "" + n, C.getName(), k.title, "" + receipt, "" + (now + n));
		}
	}

	/** a service's spend from an organization's pool, for someone; refused, spending nothing, when it holds too little; @return the receipt number */
	public static long spend(final Clan C, final String pool, final long n, final String forWhom, final String what) throws IOException
	{
		final Kind k = kind(pool);
		synchronized(C)
		{
			final long now = units(C, k.id);
			if(now < n)
				throw new IOException(C.getName() + "'s " + k.title + " holds " + now + ", not " + n);
			put(C, k.id, now - n);
			final long receipt = record(C, "spent", forWhom, k.id, n, what);
			C.update();
			ModuleStats.count(FAMILY, k.id.toLowerCase(Locale.ROOT), "units spent");
			return receipt;
		}
	}

	/** POOLS: the pools of a character's organizations, their givers and users, and the last entries */
	public static String show(final MOB M) throws IOException
	{
		final StringBuilder s = new StringBuilder();
		final Map<String, Kind> all = kinds();
		for(final Pair<Clan, Integer> p : M.clans())
		{
			final Clan C = p.first;
			if(C == null)
				continue;
			final JSONObject L = json(C, "POOLLEDGER");
			final List<String> lines = new ArrayList<String>();
			for(final Kind k : all.values())
			{
				final long u = units(C, k.id);
				final JSONObject givers = L.containsKey("givers") ? (JSONObject)((JSONObject)L.get("givers")).get(k.id) : null;
				final JSONObject users = L.containsKey("users") ? (JSONObject)((JSONObject)L.get("users")).get(k.id) : null;
				if((u == 0) && (givers == null))
					continue;
				lines.add("  " + k.title + ": " + u + " of " + k.max + (givers == null ? "" : "; given by " + totals(givers)) + (users == null ? "" : "; used for " + totals(users)));
			}
			if(lines.isEmpty())
				continue;
			s.append(s.length() > 0 ? "\n" : "").append(C.getName()).append(":\n").append(String.join("\n", lines));
			if(L.containsKey("entries"))
			{
				s.append("\n  Last entries:");
				for(final Object o : ModuleDefinitions.array(L.get("entries"), "entries"))
				{
					final JSONObject e = (JSONObject)o;
					s.append("\n    #").append(e.get("receipt")).append(' ').append(e.get("who")).append(' ').append(e.get("kind")).append(' ').append(e.get("units"))
						.append(' ').append(e.get("pool").toString().toLowerCase(Locale.ROOT)).append(" (").append(e.get("what")).append(')');
				}
			}
		}
		return (s.length() == 0) ? "Your organizations have no shared pools in use." : s.toString();
	}

	private static String totals(final JSONObject o)
	{
		final List<String> out = new ArrayList<String>();
		for(final String k : new TreeSet<String>(o.keySet()))
			out.add(k + " " + o.get(k));
		return String.join(", ", out);
	}
}
