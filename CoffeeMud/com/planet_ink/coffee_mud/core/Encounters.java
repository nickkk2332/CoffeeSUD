package com.planet_ink.coffee_mud.core;
import com.planet_ink.coffee_mud.core.interfaces.*;
import com.planet_ink.coffee_mud.core.save.WorldFiles;
import com.planet_ink.coffee_mud.Abilities.interfaces.*;
import com.planet_ink.coffee_mud.Areas.interfaces.*;
import com.planet_ink.coffee_mud.Common.interfaces.*;
import com.planet_ink.coffee_mud.Exits.interfaces.*;
import com.planet_ink.coffee_mud.Items.interfaces.*;
import com.planet_ink.coffee_mud.Libraries.interfaces.*;
import com.planet_ink.coffee_mud.Locales.interfaces.*;
import com.planet_ink.coffee_mud.MOBS.interfaces.*;
import java.io.File;
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
 * C9: encounter tables, world/encounters/NAME.enc.  One entry a line (# for a
 * comment): its kind, what, and options.
 * <pre>
 * MOBS "a grey wolf*2d3" LEADER="a dire wolf" WEIGHT=4 MASK="-HOUR +20 +21" AMBUSH
 * MOBS "a wraith" LEVEL=8- FROM=HIDDEN
 * MOBS "a lost traveller" ONCE
 * ITEM "a fallen branch*1d2"
 * EMOTE "An owl hoots somewhere above."
 * TRAP Trap_Snare
 * MERCHANT "a travelling pedlar" COOLDOWN=10
 * FUNC LOSTCHILD
 * </pre>
 * Mobs and items come from the catalog, or a class name; NAME*DICE makes
 * that many (2d3, 1d4+1, 3, 2-5).  WEIGHT= (1), MASK= on the one who came
 * in (a zapper mask: +HOUR, +SEASON, -LEVEL, +GROUPSIZE...), LEVEL=lo-hi on
 * the party's highest level, ONCE (once a game), COOLDOWN=n (n encounters
 * of the table before it comes again), FROM=HERE|HIDDEN|ADJACENT, AMBUSH
 * (the monsters strike first).  FUNC runs a FUNCTION_PROG of the thing
 * that rolled the table (an area's or room's scripts), $n the one who came in.
 */
public final class Encounters
{
	private Encounters()
	{
	}

	/** the tag on what an encounter brought */
	public static final String TAG = "ENCOUNTER";

	/** one line of a table */
	public static final class Entry
	{
		public String							kind		= "MOBS";
		public String							what		= "";
		public int								weight		= 1;
		public String							leader		= null;
		public String							maskText	= "";
		public MaskingLibrary.CompiledZMask		mask		= null;
		public int								levelLo		= 0;
		public int								levelHi		= Integer.MAX_VALUE;
		public boolean							once		= false;
		public int								cooldown	= 0;
		public String							from		= "HERE";
		public boolean							ambush		= false;
		public String							line		= "";
		public int								index		= 0;
	}

	/** a table, as read from its file */
	public static final class Table
	{
		public String				name		= "";
		public final List<Entry>	entries		= new ArrayList<Entry>();
		public final List<String>	problems	= new ArrayList<String>();
		public long					modified	= 0;
	}

	/** what came of a roll */
	public static final class Result
	{
		public Entry						entry	= null;
		public final List<PhysicalAgent>	spawned	= new ArrayList<PhysicalAgent>();
		public MOB							leader	= null;
	}

	private static final Map<String, Table>	tables	= new HashMap<String, Table>();
	private static final String[]			KINDS	= { "MOBS", "ITEM", "EMOTE", "TRAP", "MERCHANT", "FUNC" };

	/**
	 * @return the folder of the tables, world/encounters
	 */
	public static File dir()
	{
		final File W = WorldFiles.worldDir();
		return new File((W == null) ? new File(".") : W, "encounters");
	}

	/**
	 * @param name a table's name
	 * @return its file
	 */
	public static File fileOf(final String name)
	{
		final String n = name.trim().toLowerCase();
		return new File(dir(), n.endsWith(".enc") ? n : (n + ".enc"));
	}

	/**
	 * @return the names of the tables there are
	 */
	public static List<String> names()
	{
		final List<String> list = new ArrayList<String>();
		final File[] fs = dir().listFiles();
		if(fs != null)
		{
			for(final File F : fs)
			{
				if(F.isFile() && F.getName().toLowerCase().endsWith(".enc"))
					list.add(F.getName().substring(0, F.getName().length() - 4));
			}
		}
		Collections.sort(list);
		return list;
	}

	/**
	 * A table, read again when its file changed.
	 * @param name its name
	 * @return it, or null if there is no such file
	 */
	public static Table table(final String name)
	{
		if((name == null) || (name.trim().length() == 0))
			return null;
		final File F = fileOf(name);
		if(!F.isFile())
			return null;
		synchronized(tables)
		{
			final Table T = tables.get(F.getPath());
			if((T != null) && (T.modified == F.lastModified()))
				return T;
		}
		try
		{
			final String text = new String(java.nio.file.Files.readAllBytes(F.toPath()), "UTF-8");
			final Table T = parse(name.trim().toLowerCase(), text);
			T.modified = F.lastModified();
			synchronized(tables)
			{
				tables.put(F.getPath(), T);
			}
			return T;
		}
		catch(final java.io.IOException e)
		{
			Log.errOut("Encounters", "cannot read " + F.getPath() + ": " + e.getMessage());
			return null;
		}
	}

	/** forgets what was read, so the next roll reads the files again */
	public static void forget()
	{
		synchronized(tables)
		{
			tables.clear();
		}
	}

	/**
	 * Reads a table's text.
	 * @param name its name
	 * @param text its lines
	 * @return the table, with any problems noted
	 */
	public static Table parse(final String name, final String text)
	{
		final Table T = new Table();
		T.name = name;
		final String[] lines = (text == null) ? new String[0] : text.split("\r?\n");
		for(int i = 0; i < lines.length; i++)
		{
			final String l = lines[i].trim();
			if((l.length() == 0) || l.startsWith("#"))
				continue;
			final String err = entryProblem(l);
			if(err != null)
			{
				T.problems.add((i + 1) + ": " + err);
				continue;
			}
			final Entry E = entry(l);
			E.index = T.entries.size();
			T.entries.add(E);
		}
		return T;
	}

	/**
	 * @param line a line of a table
	 * @return what is wrong with it, or null
	 */
	public static String entryProblem(final String line)
	{
		final String l = (line == null) ? "" : line.trim();
		final int sp = l.indexOf(' ');
		final String kind = ((sp < 0) ? l : l.substring(0, sp)).toUpperCase();
		if(!Arrays.asList(KINDS).contains(kind))
			return "no such kind " + kind + " (" + CMParms.toListString(KINDS) + ")";
		final Entry E = entry(l);
		if(E.what.length() == 0)
			return kind + " of nothing";
		if(E.kind.equals("TRAP") && (!(CMClass.getAbility(E.what) instanceof Trap)))
			return "no trap " + E.what;
		if(E.kind.equals("MOBS") || E.kind.equals("MERCHANT") || E.kind.equals("ITEM"))
		{
			final boolean mob = !E.kind.equals("ITEM");
			final List<String> names = new ArrayList<String>();
			for(final String part : CMParms.parseCommas(E.what, true))
				names.add(nameOf(part));
			if(E.leader != null)
				names.add(E.leader);
			for(final String n : names)
			{
				if(template(n, mob) == null)
					return "no " + (mob ? "mob" : "item") + " '" + n + "' in the catalog or the classes";
			}
		}
		return null;
	}

	/**
	 * Reads one line; no checks.
	 * @param line the line
	 * @return the entry
	 */
	public static Entry entry(final String line)
	{
		final Entry E = new Entry();
		E.line = line.trim();
		String rest = E.line;
		final int sp = rest.indexOf(' ');
		E.kind = ((sp < 0) ? rest : rest.substring(0, sp)).toUpperCase();
		rest = (sp < 0) ? "" : rest.substring(sp + 1).trim();
		if(rest.startsWith("\""))
		{
			final int q = rest.indexOf('"', 1);
			E.what = ((q < 0) ? rest.substring(1) : rest.substring(1, q)).trim();
			rest = (q < 0) ? "" : rest.substring(q + 1).trim();
		}
		else
		{
			final int sp2 = rest.indexOf(' ');
			E.what = ((sp2 < 0) ? rest : rest.substring(0, sp2)).trim();
			rest = (sp2 < 0) ? "" : rest.substring(sp2 + 1).trim();
		}
		final Map<String, String> q = CMParms.parseExactParms(rest);
		E.weight = Math.max(0, CMParms.getParmInt(q, "WEIGHT", 1));
		final String lead = CMParms.getParmStr(q, "LEADER", "").trim();
		E.leader = (lead.length() == 0) ? null : lead;
		E.maskText = CMParms.getParmStr(q, "MASK", "").trim();
		E.mask = (E.maskText.length() == 0) ? null : CMLib.masking().getPreCompiledMask(E.maskText);
		final String lv = CMParms.getParmStr(q, "LEVEL", "").trim();
		if(lv.length() > 0)
		{
			final int x = lv.indexOf('-');
			if(x < 0)
				E.levelLo = E.levelHi = CMath.s_int(lv);
			else
			{
				final String lo = lv.substring(0, x).trim();
				final String hi = lv.substring(x + 1).trim();
				E.levelLo = (lo.length() == 0) ? 0 : CMath.s_int(lo);
				E.levelHi = (hi.length() == 0) ? Integer.MAX_VALUE : CMath.s_int(hi);
			}
		}
		E.once = q.containsKey("ONCE");
		E.cooldown = Math.max(0, CMParms.getParmInt(q, "COOLDOWN", 0));
		E.from = CMParms.getParmStr(q, "FROM", "HERE").trim().toUpperCase();
		E.ambush = q.containsKey("AMBUSH");
		return E;
	}

	/**
	 * @param part NAME*DICE
	 * @return the name
	 */
	public static String nameOf(final String part)
	{
		final int x = part.lastIndexOf('*');
		return ((x < 0) ? part : part.substring(0, x)).trim();
	}

	/**
	 * How many NAME*DICE makes: 2d3, 1d4+1, 3, 2-5 (1 with no dice).
	 * @param part the text
	 * @return how many, at least 1
	 */
	public static int countOf(final String part)
	{
		final int x = part.lastIndexOf('*');
		if(x < 0)
			return 1;
		final String d = part.substring(x + 1).trim().toLowerCase().replace(" ", "");
		final int dx = d.indexOf('d');
		if(dx > 0)
		{
			final int n = CMath.s_int(d.substring(0, dx));
			String sides = d.substring(dx + 1);
			int plus = 0;
			final int px = Math.max(sides.indexOf('+'), sides.indexOf('-'));
			if(px > 0)
			{
				plus = CMath.s_int(sides.substring(px).replace("+", ""));
				sides = sides.substring(0, px);
			}
			return Math.max(1, CMLib.dice().roll(n, CMath.s_int(sides), plus));
		}
		final int rx = d.indexOf('-');
		if(rx > 0)
			return Math.max(1, CMLib.dice().rollInRange(CMath.s_int(d.substring(0, rx)), CMath.s_int(d.substring(rx + 1))));
		return Math.max(1, CMath.s_int(d));
	}

	/** a catalog entry or a class: what copies are made from */
	protected static Physical template(final String name, final boolean mob)
	{
		final Physical P = ScriptLib.catalogEntry(name, mob);
		if(P != null)
			return P;
		if(mob)
			return CMClass.getMOBPrototype(name);
		final Item I = CMClass.getItemPrototype(name);
		return I;
	}

	/**
	 * A copy: a catalog one counted (null past its cap), or a new one of the class.
	 * @param name the catalog name or class
	 * @param mob true for a mob, false for an item
	 * @return the copy, or null
	 */
	public static Physical make(final String name, final boolean mob)
	{
		final Physical P = ScriptLib.catalogEntry(name, mob);
		if(P != null)
			return ScriptLib.catalogCopy(P, false);
		if(mob)
			return CMClass.getMOB(name);
		return CMClass.getItem(name);
	}

	/**
	 * C20: mobs from a spec, in a room: name*dice joined with + (from the
	 * catalog, catalog: being optional, or a mob class), or encounter:table,
	 * a roll of that table.
	 * @param spec the spec
	 * @param R the room
	 * @param foe one of the party, for an encounter's masks, or null
	 * @return the mobs
	 */
	public static List<MOB> spawnSpec(final String spec, final Room R, final MOB foe)
	{
		final List<MOB> made = new ArrayList<MOB>();
		for(final String raw : spec.split("\\+"))
		{
			String part = raw.trim();
			if(part.length() == 0)
				continue;
			if(part.toLowerCase().startsWith("encounter:"))
			{
				final Table T = table(part.substring(10).trim());
				if(T == null)
				{
					Log.errOut("Encounters", "no encounter table " + part.substring(10).trim());
					continue;
				}
				for(final PhysicalAgent P : roll(T, R, foe, R, 0).spawned)
				{
					if(P instanceof MOB)
						made.add((MOB)P);
				}
				continue;
			}
			if(part.toLowerCase().startsWith("catalog:"))
				part = part.substring(8).trim();
			final int n = countOf(part);
			final String name = nameOf(part);
			for(int i = 0; i < n; i++)
			{
				final Physical P = make(name, true);
				if(!(P instanceof MOB))
				{
					Log.errOut("Encounters", "no mob " + name + " in the catalog or the classes");
					break;
				}
				final MOB M = (MOB)P;
				M.setStartRoom(null);
				M.basePhyStats().setRejuv(PhyStats.NO_REJUV);
				M.recoverPhyStats();
				M.text();
				M.bringToLife(R, true);
				made.add(M);
			}
		}
		return made;
	}

	/** the ONCE and COOLDOWN state of the tables, in the game's variables */
	private static ScriptingEngine vars()
	{
		return (ScriptingEngine)CMClass.getCommon("DefaultScriptingEngine");
	}

	protected static String key(final Table T, final Entry E)
	{
		return T.name.toUpperCase() + "_" + E.index;
	}

	/**
	 * @param T a table
	 * @param E one of its entries
	 * @return whether ONCE says it came already
	 */
	public static boolean onceDone(final Table T, final Entry E)
	{
		final ScriptingEngine S = vars();
		return (S != null) && "DONE".equals(S.getVar("ENCOUNTERS", "ONCE_" + key(T, E)));
	}

	/**
	 * @param T a table
	 * @param E one of its entries
	 * @return the table's encounters left before it may come again (COOLDOWN)
	 */
	public static int coolingFor(final Table T, final Entry E)
	{
		final ScriptingEngine S = vars();
		return (S == null) ? 0 : CMath.s_int(S.getVar("ENCOUNTERS", "COOL_" + key(T, E)));
	}

	/**
	 * Forgets the ONCE and COOLDOWN of a table (ENCOUNTER RESET).
	 * @param T the table
	 */
	public static void reset(final Table T)
	{
		final ScriptingEngine S = vars();
		if(S == null)
			return;
		for(final Entry E : T.entries)
		{
			S.setVar("ENCOUNTERS", "ONCE_" + key(T, E), "");
			S.setVar("ENCOUNTERS", "COOL_" + key(T, E), "");
		}
	}

	/**
	 * The party's highest level: the one who came in, and the player's side
	 * in the room with them.
	 * @param who the one who came in
	 * @return the level
	 */
	public static int partyLevel(final MOB who)
	{
		if(who == null)
			return 0;
		int best = who.phyStats().level();
		final Room R = who.location();
		if(R != null)
		{
			for(final Enumeration<MOB> m = R.inhabitants(); m.hasMoreElements();)
			{
				final MOB M = m.nextElement();
				if((M != null) && GameDifficulty.playerSide(M))
					best = Math.max(best, M.phyStats().level());
			}
		}
		return best;
	}

	/**
	 * The party's power, for BUDGET=: the player's side in the room.
	 * @param who the one who came in
	 * @return its power
	 */
	public static double partyPower(final MOB who)
	{
		double p = 0;
		final Room R = (who == null) ? null : who.location();
		if(R == null)
			return (who == null) ? 0 : MobRanks.powerOf(who);
		for(final Enumeration<MOB> m = R.inhabitants(); m.hasMoreElements();)
		{
			final MOB M = m.nextElement();
			if((M != null) && GameDifficulty.playerSide(M))
				p += MobRanks.powerOf(M);
		}
		return p;
	}

	/**
	 * The entries that could come now.
	 * @param T the table
	 * @param who the one who came in, or null (no masks or levels)
	 * @param mobsOnly only MOBS entries
	 * @return them
	 */
	public static List<Entry> candidates(final Table T, final MOB who, final boolean mobsOnly)
	{
		final List<Entry> list = new ArrayList<Entry>();
		final int lvl = partyLevel(who);
		for(final Entry E : T.entries)
		{
			if((E.weight <= 0) || (mobsOnly && (!E.kind.equals("MOBS"))))
				continue;
			if((who != null) && (E.mask != null) && (!CMLib.masking().maskCheck(E.mask, who, true)))
				continue;
			if((who != null) && ((lvl < E.levelLo) || (lvl > E.levelHi)))
				continue;
			if(E.once && onceDone(T, E))
				continue;
			if((E.cooldown > 0) && (coolingFor(T, E) > 0))
				continue;
			list.add(E);
		}
		return list;
	}

	/** one by weight */
	protected static Entry pick(final List<Entry> list)
	{
		int total = 0;
		for(final Entry E : list)
			total += E.weight;
		if(total <= 0)
			return null;
		int roll = CMLib.dice().roll(1, total, 0);
		for(final Entry E : list)
		{
			roll -= E.weight;
			if(roll <= 0)
				return E;
		}
		return list.get(list.size() - 1);
	}

	/**
	 * Rolls a table where one came in, and makes it happen.
	 * @param T the table
	 * @param R the room
	 * @param who the one who came in, or null
	 * @param host what rolled it (for FUNC), or null
	 * @param budgetPct BUDGET=: more MOBS entries until their power is this
	 *        share of the party's, or 0
	 * @return what happened, its entry null if nothing did
	 */
	public static Result roll(final Table T, final Room R, final MOB who, final PhysicalAgent host, final int budgetPct)
	{
		final Result res = new Result();
		if((T == null) || (R == null))
			return res;
		final Entry E = pick(candidates(T, who, false));
		if(E == null)
			return res;
		res.entry = E;
		// the cooldowns of the others run down; this one's starts
		final ScriptingEngine S = vars();
		if(S != null)
		{
			for(final Entry O : T.entries)
			{
				final int c = coolingFor(T, O);
				if(c > 0)
					S.setVar("ENCOUNTERS", "COOL_" + key(T, O), "" + (c - 1));
			}
			if(E.cooldown > 0)
				S.setVar("ENCOUNTERS", "COOL_" + key(T, E), "" + E.cooldown);
			if(E.once)
				S.setVar("ENCOUNTERS", "ONCE_" + key(T, E), "DONE");
		}
		happen(E, R, who, host, res);
		if((budgetPct > 0) && E.kind.equals("MOBS") && (who != null))
		{
			final double want = partyPower(who) * budgetPct / 100.0;
			for(int more = 0; more < 10; more++)
			{
				double have = 0;
				for(final PhysicalAgent P : res.spawned)
				{
					if(P instanceof MOB)
						have += MobRanks.powerOf((MOB)P);
				}
				if(have >= want)
					break;
				final Entry E2 = pick(candidates(T, who, true));
				if(E2 == null)
					break;
				happen(E2, R, who, host, res);
			}
		}
		return res;
	}

	/** does one entry */
	protected static void happen(final Entry E, final Room R, final MOB who, final PhysicalAgent host, final Result res)
	{
		if(E.kind.equals("EMOTE"))
		{
			R.showHappens(CMMsg.MSG_OK_ACTION, E.what);
			return;
		}
		if(E.kind.equals("FUNC"))
		{
			final PhysicalAgent H = (host != null) ? host : R;
			for(final ScriptingEngine S : ScriptEvents.engines(H))
				S.callFunction(H, E.what, "", who);
			return;
		}
		if(E.kind.equals("TRAP"))
		{
			final Ability A = CMClass.getAbility(E.what);
			if((A instanceof Trap) && (who != null))
			{
				final Trap T = ((Trap)A).setTrap(who, R, 0, Math.max(1, partyLevel(who)), false);
				if(T != null)
					T.spring(who);
			}
			return;
		}
		if(E.kind.equals("ITEM"))
		{
			for(final String part : CMParms.parseCommas(E.what, true))
			{
				final int n = countOf(part);
				for(int i = 0; i < n; i++)
				{
					final Physical P = make(nameOf(part), false);
					if(!(P instanceof Item))
						continue;
					final Item I = (Item)P;
					I.basePhyStats().setRejuv(PhyStats.NO_REJUV);
					I.recoverPhyStats();
					R.addItem(I, ItemPossessor.Expire.Monster_EQ);
					I.addTag(TAG);
					res.spawned.add(I);
				}
			}
			R.recoverRoomStats();
			return;
		}
		// MOBS and MERCHANT
		final List<MOB> made = new ArrayList<MOB>();
		MOB leader = null;
		if(E.leader != null)
		{
			final Physical P = make(E.leader, true);
			if(P instanceof MOB)
			{
				leader = (MOB)P;
				made.add(leader);
			}
		}
		for(final String part : CMParms.parseCommas(E.what, true))
		{
			final int n = countOf(part);
			for(int i = 0; i < n; i++)
			{
				final Physical P = make(nameOf(part), true);
				if(P instanceof MOB)
					made.add((MOB)P);
			}
		}
		Room arriveFrom = null;
		int dir = -1;
		if(E.from.equals("ADJACENT"))
		{
			final List<Integer> dirs = new ArrayList<Integer>();
			for(int d = 0; d < Directions.NUM_DIRECTIONS(); d++)
			{
				final Room R2 = R.getRoomInDir(d);
				final Exit X = R.getExitInDir(d);
				if((R2 != null) && (X != null) && X.isOpen() && (R2.getExitInDir(Directions.getOpDirectionCode(d)) != null))
					dirs.add(Integer.valueOf(d));
			}
			if(dirs.size() > 0)
			{
				final int d = dirs.get(CMLib.dice().roll(1, dirs.size(), -1)).intValue();
				arriveFrom = R.getRoomInDir(d);
				dir = Directions.getOpDirectionCode(d);
			}
		}
		for(final MOB M : made)
		{
			M.setStartRoom(null);
			M.basePhyStats().setRejuv(PhyStats.NO_REJUV);
			M.recoverPhyStats();
			M.text();
			M.bringToLife((arriveFrom != null) ? arriveFrom : R, true);
			M.addTag(TAG);
			if((leader != null) && (M != leader))
				M.setFollowing(leader);
			if(E.from.equals("HIDDEN"))
			{
				M.basePhyStats().setDisposition(M.basePhyStats().disposition() | PhyStats.IS_HIDDEN);
				M.recoverPhyStats();
			}
			res.spawned.add(M);
		}
		if(arriveFrom != null)
		{
			// they come in from next door
			for(final MOB M : made)
			{
				if((M.location() == arriveFrom) && ((leader == null) || (M == leader) || (M.amFollowing() == null)))
					CMLib.tracking().walk(M, dir, false, false);
			}
			for(final MOB M : made)
			{
				if(M.location() != R)
					R.bringMobHere(M, true);
			}
		}
		if(res.leader == null)
			res.leader = (leader != null) ? leader : (made.isEmpty() ? null : made.get(0));
		if(E.ambush && (who != null) && E.kind.equals("MOBS"))
		{
			// the first round is theirs
			for(final MOB M : made)
			{
				if(E.from.equals("HIDDEN"))
				{
					M.basePhyStats().setDisposition(M.basePhyStats().disposition() & ~PhyStats.IS_HIDDEN);
					M.recoverPhyStats();
				}
				if((M.location() == who.location()) && (!who.amDead()))
					CMLib.combat().postAttack(M, who, M.fetchWieldedItem());
			}
		}
		R.recoverRoomStats();
	}
}
