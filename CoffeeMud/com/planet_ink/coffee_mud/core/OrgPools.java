package com.planet_ink.coffee_mud.core;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.*;

import com.planet_ink.coffee_mud.Common.interfaces.Clan;
import com.planet_ink.coffee_mud.Common.interfaces.ClanPosition;
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
		/** the ranks (position names or IDs) whose services it pays for, and who may take from it; none: every member, and no one */
		public final List<String>	spenders, takers;

		Kind(final String id, final String parms)
		{
			this.id = id.trim().toUpperCase(Locale.ROOT);
			title = CMParms.getParmStr(parms, "TITLE", this.id.toLowerCase(Locale.ROOT));
			max = Math.max(1, CMath.s_long(CMParms.getParmStr(parms, "MAX", "1000")));
			money = Math.max(0, CMath.s_long(CMParms.getParmStr(parms, "MONEY", "0")));
			material = CMParms.getParmStr(parms, "MATERIAL", "").trim().toUpperCase(Locale.ROOT);
			spenders = ranks(CMParms.getParmStr(parms, "SPENDERS", ""));
			takers = ranks(CMParms.getParmStr(parms, "TAKERS", ""));
		}

		private static List<String> ranks(final String list)
		{
			final List<String> out = new ArrayList<String>();
			for(final String r : CMParms.parseCommas(list, true))
			{
				if(r.trim().length() > 0)
					out.add(r.trim());
			}
			return Collections.unmodifiableList(out);
		}
	}

	/** a member's rank in an organization, as its position's name and ID; empty for one who is no member */
	private static List<String> rankOf(final MOB M, final Clan C)
	{
		final List<String> out = new ArrayList<String>();
		final Pair<Clan, Integer> role = (M == null) ? null : M.getClanRole(C.clanID());
		if(role == null)
			return out;
		final int r = role.second.intValue();
		out.add(C.getRoleName(r, false, false));
		final ClanPosition[] ps = (C.getGovernment() == null) ? null : C.getGovernment().getPositions();
		if((ps != null) && (r >= 0) && (r < ps.length) && (ps[r] != null))
			out.add(ps[r].getID());
		return out;
	}

	private static boolean ranked(final List<String> rank, final List<String> allowed)
	{
		for(final String a : allowed)
		{
			for(final String r : rank)
			{
				if(a.equalsIgnoreCase(r))
					return true;
			}
		}
		return false;
	}

	/**
	 * Why a member may not have a service paid from a pool, or "": not a
	 * member, or not of a rank its SPENDERS name.
	 */
	public static String whyNotSpend(final MOB M, final Clan C, final String pool) throws IOException
	{
		final Kind k = kind(pool);
		final List<String> rank = rankOf(M, C);
		if(rank.isEmpty())
			return CMLib.lang().L("only for members of @x1", C.getName());
		if(!k.spenders.isEmpty() && !ranked(rank, k.spenders))
			return CMLib.lang().L("@x1's @x2 pays only for @x3", C.getName(), k.title.toLowerCase(Locale.ROOT), String.join(", ", k.spenders));
		return "";
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

	/** notes a pool's movement in the LEDGER of the character it was for */
	public static void note(final MOB M, final Clan C, final String kind, final String pool, final long units, final long receipt)
	{
		if(M != null)
			Provenance.event(M, FAMILY, "orgpools.ini " + pool.toUpperCase(Locale.ROOT), kind + " " + units + " unit(s) of " + C.getName() + "'s " + pool.toLowerCase(Locale.ROOT) + ", receipt #" + receipt);
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
		// each contributor's and each consumer's totals, by pool: given and added in; spent and taken out; a refund takes back a spend
		final String side = (kind.equals("gave") || kind.equals("added")) ? "givers" : "users";
		final long signed = kind.equals("refunded") ? -units : units;
		final JSONObject totals = L.containsKey(side) ? (JSONObject)L.get(side) : new JSONObject();
		final JSONObject byPool = totals.containsKey(pool) ? (JSONObject)totals.get(pool) : new JSONObject();
		byPool.put(who, Long.valueOf(((byPool.get(who) instanceof Number) ? ((Number)byPool.get(who)).longValue() : 0) + signed));
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
			note(M, C, "gave", k.id, n, receipt);
			ModuleStats.count(FAMILY, k.id.toLowerCase(Locale.ROOT), "units given");
			return CMLib.lang().L("You give @x1 unit(s) to @x2's @x3 (receipt #@x4); it holds @x5.", "" + n, C.getName(), k.title, "" + receipt, "" + (now + n));
		}
	}

	/** a script's grant to a pool, paid by no one; refused past its MAX; @return the receipt number */
	public static long add(final Clan C, final String pool, final long n, final String by, final String what) throws IOException
	{
		final Kind k = kind(pool);
		if(n <= 0)
			throw new IOException("add at least one unit");
		synchronized(C)
		{
			final long now = units(C, k.id);
			if(now + n > k.max)
				throw new IOException(k.title + " holds at most " + k.max + "; it has " + now);
			put(C, k.id, now + n);
			final long receipt = record(C, "added", by, k.id, n, what);
			C.update();
			return receipt;
		}
	}

	/** a refund of a spend: the units go back (past MAX if they must: they were there before) */
	public static long refund(final Clan C, final String pool, final long n, final String forWhom, final String what) throws IOException
	{
		final Kind k = kind(pool);
		synchronized(C)
		{
			put(C, k.id, units(C, k.id) + n);
			final long receipt = record(C, "refunded", forWhom, k.id, n, what);
			C.update();
			return receipt;
		}
	}

	/** POOLS TAKE: a member of a rank its TAKERS name takes n units out, as money or a pile of its material; @return what happened */
	public static String take(final MOB M, final Clan C, final String pool, final long n) throws IOException
	{
		final Kind k = kind(pool);
		final List<String> rank = rankOf(M, C);
		if(rank.isEmpty())
			throw new IOException("only members take from " + C.getName() + "'s " + k.title);
		if(k.takers.isEmpty() || !ranked(rank, k.takers))
			throw new IOException(k.takers.isEmpty() ? "no one takes from " + C.getName() + "'s " + k.title
				: "only " + String.join(", ", k.takers) + " take from " + C.getName() + "'s " + k.title);
		if(n <= 0)
			throw new IOException("take at least one unit");
		synchronized(C)
		{
			final long now = units(C, k.id);
			if(now < n)
				throw new IOException(C.getName() + "'s " + k.title + " holds " + now + ", not " + n);
			String got;
			if(k.money > 0)
			{
				final String cur = CMLib.beanCounter().getCurrency(M);
				CMLib.beanCounter().giveSomeoneMoney(M, cur, (double)n * k.money);
				got = CMLib.beanCounter().nameCurrencyShort(cur, (double)n * k.money);
			}
			else
			{
				final int code = com.planet_ink.coffee_mud.Items.interfaces.RawMaterial.CODES.FIND_CaseSensitive(k.material);
				if(code < 0)
					throw new IOException("no registered material " + k.material);
				final com.planet_ink.coffee_mud.Items.interfaces.Item I = CMLib.materials().makeItemResource(code);
				I.basePhyStats().setWeight((int)Math.min(Integer.MAX_VALUE, n));
				CMLib.materials().adjustResourceName(I);
				I.recoverPhyStats();
				M.addItem(I);
				got = I.name();
			}
			put(C, k.id, now - n);
			final long receipt = record(C, "took", M.Name(), k.id, n, "");
			C.update();
			note(M, C, "took", k.id, n, receipt);
			ModuleStats.count(FAMILY, k.id.toLowerCase(Locale.ROOT), "units taken");
			return CMLib.lang().L("You take @x1 unit(s) from @x2's @x3 (receipt #@x4): @x5.", "" + n, C.getName(), k.title, "" + receipt, got);
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
