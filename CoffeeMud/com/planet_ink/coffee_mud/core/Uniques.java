package com.planet_ink.coffee_mud.core;

import com.planet_ink.coffee_mud.Items.interfaces.*;
import com.planet_ink.coffee_mud.Locales.interfaces.*;
import com.planet_ink.coffee_mud.MOBS.interfaces.*;
import com.planet_ink.coffee_mud.Libraries.interfaces.CatalogLibrary;
import com.planet_ink.coffee_mud.Libraries.interfaces.CatalogLibrary.CataData;
import com.planet_ink.coffee_mud.Libraries.interfaces.CatalogLibrary.CataSpawn;
import com.planet_ink.coffee_mud.Libraries.interfaces.MaskingLibrary;
import com.planet_ink.coffee_mud.Libraries.interfaces.XMLLibrary.XMLTag;
import com.planet_ink.coffee_mud.core.interfaces.*;

import java.util.*;

/**
 * Unique and rare catalog items (DEVPLAN item 1).  A catalog item has a cap
 * (the most copies in the world) and one of three kinds of uniqueness:
 * <ul>
 * <li>TIME, at a time: at most the cap's number at once, as before;</li>
 * <li>ONCE, once ever: once the cap's number have appeared, no more;</li>
 * <li>WHILE, once for a while: as once ever, but a copy lost or destroyed
 * may appear again after the item's number of game days.</li>
 * </ul>
 * It may have several spawn rules, each its own kind, rate and mask, sharing
 * the cap and the uniqueness: the catalog's own rule and the extra ones, each
 * written KIND RATE [WHEN] [MASK].  The kinds are LIVE, DROP, ROOM and
 * CONTAINER; a container rule rolls when a matching container is first
 * opened (OPEN), on the catalog's timer into a random matching container
 * (TIMER), or both.
 * <p>
 * Every copy in the game counts: one made, loaded with the world or a save,
 * or carried in by a character.  For each item the game keeps how many have
 * ever appeared and when each copy was lost, and a save holds that.
 */
public final class Uniques
{
	private Uniques()
	{
	}

	/** the kinds of uniqueness */
	public static final String[]	HOWS	= { "TIME", "ONCE", "WHILE" };
	/** when a container rule rolls */
	public static final String[]	WHENS	= { "OPEN", "TIMER", "BOTH" };

	/**
	 * One spawn rule of a catalog item.
	 */
	public static final class Rule
	{
		/** its kind: LIVE, DROP, ROOM or CONTAINER */
		public final CataSpawn						spawn;
		/** its chance, 0 to 1 */
		public final double							rate;
		/** for a container rule: OPEN, TIMER or BOTH */
		public final String							when;
		/** its mask, perhaps "" for any */
		public final String							maskStr;
		/** the mask compiled, or null for any */
		public final MaskingLibrary.CompiledZMask	mask;

		/**
		 * Makes a rule.
		 * @param spawn its kind
		 * @param rate its chance
		 * @param when for a container rule, when it rolls
		 * @param maskStr its mask
		 */
		public Rule(final CataSpawn spawn, final double rate, final String when, final String maskStr)
		{
			this.spawn = spawn;
			this.rate = rate;
			this.when = whenOf(when);
			this.maskStr = (maskStr == null) ? "" : maskStr.trim();
			this.mask = (this.maskStr.length() == 0) ? null : CMLib.masking().maskCompile(this.maskStr);
		}

		/**
		 * Returns whether this rule rolls when a container is opened.
		 * @return true for a container rule on opening
		 */
		public boolean onOpen()
		{
			return (spawn == CataSpawn.CONTAINER) && (!when.equals("TIMER"));
		}

		/**
		 * Returns whether this rule rolls on the catalog's timer.
		 * @return true for a room rule, or a container rule on a timer
		 */
		public boolean onTimer()
		{
			return (spawn == CataSpawn.ROOM) || ((spawn == CataSpawn.CONTAINER) && (!when.equals("OPEN")));
		}

		/**
		 * Returns whether a mob, room or container matches this rule's mask.
		 * @param E what to check
		 * @return true if it matches, or the rule has no mask
		 */
		public boolean matches(final Environmental E)
		{
			return (mask == null) || mask.empty() || CMLib.masking().maskCheck(mask, E, true);
		}

		/**
		 * Returns the rule as it is written: KIND RATE [WHEN] [MASK].
		 * @return the text
		 */
		public String text()
		{
			return spawn.name() + " " + CMath.toPct(rate)
				+ ((spawn == CataSpawn.CONTAINER) ? (" " + when) : "")
				+ ((maskStr.length() > 0) ? (" " + maskStr) : "");
		}
	}

	/** what is kept of an item that is unique ever or for a while */
	private static final class Track
	{
		/** how many copies have appeared */
		int					ever		= 0;
		/** how many were in the game when last counted */
		int					lastLive	= 0;
		/** the game time each copy still missing was lost, oldest first */
		final LinkedList<Long>	lost	= new LinkedList<Long>();
	}

	/** per catalog item, by its name in upper case */
	private static final Map<String, Track>			tracks	= new TreeMap<String, Track>();
	/** copies made but not in the game yet, such as a generator's */
	private static final Map<Physical, Boolean>		pending	= Collections.synchronizedMap(new WeakHashMap<Physical, Boolean>());
	/** the containers that have rolled their rules, until their contents reset */
	private static final Map<Item, Boolean>			rolled	= Collections.synchronizedMap(new WeakHashMap<Item, Boolean>());
	/** the extra rules compiled, by their text */
	private static final Map<String, Rule>			parsed	= Collections.synchronizedMap(new HashMap<String, Rule>());

	/**
	 * Returns a kind of uniqueness, written in full (TIME, ONCE or WHILE).
	 * @param how what was written, perhaps the first letter
	 * @return the kind, TIME if it is none
	 */
	public static String howOf(final String how)
	{
		final String h = (how == null) ? "" : how.trim().toUpperCase();
		if(h.length() > 0)
		{
			for(final String s : HOWS)
			{
				if(s.startsWith(h))
					return s;
			}
		}
		return "TIME";
	}

	/**
	 * Returns when a container rule rolls, written in full (OPEN, TIMER or
	 * BOTH).
	 * @param when what was written, perhaps the first letter
	 * @return the word, OPEN if it is none
	 */
	public static String whenOf(final String when)
	{
		final String w = (when == null) ? "" : when.trim().toUpperCase();
		if(w.length() > 0)
		{
			for(final String s : WHENS)
			{
				if(s.startsWith(w))
					return s;
			}
		}
		return "OPEN";
	}

	/**
	 * Reads one rule as it is written: KIND RATE [WHEN] [MASK], such as
	 * CONTAINER 5% OPEN -SUBNAMES +"iron chest".
	 * @param line the rule
	 * @param why what is wrong with it, if anything, is put here
	 * @return the rule, or null
	 */
	public static Rule parse(final String line, final StringBuilder why)
	{
		final String s = (line == null) ? "" : line.trim();
		final int x = s.indexOf(' ');
		final String kindWord = ((x < 0) ? s : s.substring(0, x)).toUpperCase();
		CataSpawn kind = null;
		if(kindWord.length() > 0)
		{
			for(final CataSpawn k : CataSpawn.values())
			{
				if((k != CataSpawn.NONE) && k.name().startsWith(kindWord))
				{
					kind = k;
					break;
				}
			}
		}
		if(kind == null)
		{
			if(why != null)
				why.append(CMLib.lang().L("'@x1' is not a kind of rule: LIVE, DROP, ROOM or CONTAINER.", kindWord));
			return null;
		}
		String rest = (x < 0) ? "" : s.substring(x + 1).trim();
		int y = rest.indexOf(' ');
		final String rateWord = (y < 0) ? rest : rest.substring(0, y);
		if((!CMath.isPct(rateWord)) && (!CMath.isNumber(rateWord)))
		{
			if(why != null)
				why.append(CMLib.lang().L("'@x1' is not a rate: try something like 5%.", rateWord));
			return null;
		}
		final double rate = CMath.s_pct(rateWord);
		rest = (y < 0) ? "" : rest.substring(y + 1).trim();
		String when = "OPEN";
		if(kind == CataSpawn.CONTAINER)
		{
			y = rest.indexOf(' ');
			final String w = ((y < 0) ? rest : rest.substring(0, y)).toUpperCase();
			if(w.equals("OPEN") || w.equals("TIMER") || w.equals("BOTH"))
			{
				when = w;
				rest = (y < 0) ? "" : rest.substring(y + 1).trim();
			}
		}
		return new Rule(kind, rate, when, rest);
	}

	/**
	 * Returns a catalog item's rules: its own, if it has one, and its extra
	 * ones.
	 * @param data the item's catalog data
	 * @return the rules, perhaps none
	 */
	public static List<Rule> rules(final CataData data)
	{
		final List<Rule> rules = new ArrayList<Rule>(2);
		if(data == null)
			return rules;
		if(data.getSpawn() != CataSpawn.NONE)
		{
			final String key = "\u0001" + data.getSpawn().name() + " " + data.getRate() + " " + data.getWhen() + " " + data.getMaskStr();
			Rule r = parsed.get(key);
			if(r == null)
			{
				r = new Rule(data.getSpawn(), data.getRate(), data.getWhen(), data.getMaskStr());
				parsed.put(key, r);
			}
			rules.add(r);
		}
		for(final String line : data.getRules())
		{
			Rule r = parsed.get(line);
			if(r == null)
			{
				r = parse(line, null);
				if(r == null)
					continue;
				parsed.put(line, r);
			}
			rules.add(r);
		}
		return rules;
	}

	/**
	 * Returns whether a copy is in the game: in a room, or carried by a mob
	 * that is.
	 * @param P the copy
	 * @return true if it is in the game
	 */
	private static boolean inGame(final Physical P)
	{
		return (P instanceof Item) && CMLib.flags().isInTheGame((Item)P, true);
	}

	/**
	 * Returns how many copies of a catalog item are in the game, with those
	 * just made and not yet put anywhere.
	 * @param data the item's catalog data
	 * @param exclude a copy not to count, or null
	 * @return how many
	 */
	public static int live(final CataData data, final Physical exclude)
	{
		if(data == null)
			return 0;
		int n = 0;
		for(final Enumeration<Physical> e = data.enumeration(); e.hasMoreElements();)
		{
			final Physical P = e.nextElement();
			if((P == null) || (P == exclude) || P.amDestroyed())
				continue;
			if(inGame(P))
			{
				pending.remove(P);
				n++;
			}
			else
			if(pending.containsKey(P))
				n++;
		}
		return n;
	}

	private static Track track(final String name)
	{
		final String key = name.toUpperCase();
		Track t = tracks.get(key);
		if(t == null)
		{
			t = new Track();
			tracks.put(key, t);
		}
		return t;
	}

	/**
	 * Brings what is kept of an item up to date with how many copies are in
	 * the game: copies come back first in place of those lost most lately,
	 * and the rest are new.
	 * @param name the item's name
	 * @param live how many are in the game
	 * @return what is kept
	 */
	private static Track observe(final String name, final int live)
	{
		final Track t = track(name);
		if(live > t.lastLive)
		{
			int d = live - t.lastLive;
			while((d > 0) && (!t.lost.isEmpty()))
			{
				t.lost.removeLast();
				d--;
			}
			t.ever += d;
		}
		else
		if(live < t.lastLive)
		{
			final Long now = Long.valueOf(GameTime.now());
			for(int i = live; i < t.lastLive; i++)
				t.lost.addLast(now);
		}
		t.lastLive = live;
		return t;
	}

	private static long dayMillis()
	{
		return Math.max(1, CMProps.getMillisPerMudHour() * CMLib.time().globalClock().getHoursInDay());
	}

	/**
	 * Returns whether one more copy of a catalog item may come into the
	 * game: fewer than its cap are in it, and, unique ever or for a while,
	 * one may appear again.
	 * @param name the item's name
	 * @param data its catalog data
	 * @param exclude a copy not to count (one coming in), or null
	 * @return true if one may
	 */
	public static synchronized boolean mayAppear(final String name, final CataData data, final Physical exclude)
	{
		if((data == null) || (data.getCap() <= 0))
			return false;
		final int live = live(data, exclude);
		if(live >= data.getCap())
			return false;
		final String how = howOf(data.getUnique());
		if(how.equals("TIME"))
			return true;
		final Track t = observe(name, live);
		if(t.ever < data.getCap())
			return true;
		if(how.equals("ONCE") || t.lost.isEmpty())
			return false;
		final long wait = Math.max(0, data.getUniqueDays()) * dayMillis();
		return GameTime.now() - t.lost.getFirst().longValue() >= wait;
	}

	/**
	 * Notes that a copy of a catalog item came into the game, or is about to:
	 * it counts as one of those ever made, or as one lost coming back.
	 * @param name the item's name
	 * @param data its catalog data
	 * @param P the copy
	 */
	public static synchronized void appeared(final String name, final CataData data, final Physical P)
	{
		if((P != null) && (!inGame(P)))
			pending.put(P, Boolean.TRUE);
		if((data == null) || howOf(data.getUnique()).equals("TIME"))
			return;
		final Track t = track(name);
		final int live = live(data, null);
		if((t.ever < data.getCap()) || t.lost.isEmpty())
			t.ever++;
		else
			t.lost.removeFirst();
		t.lastLive = live;
	}

	/**
	 * Rolls a catalog item's LIVE or DROP rules for a mob coming to life or
	 * dying: the first rule of the kind whose mask the mob meets rolls its
	 * rate, and the item may then appear if its cap and uniqueness allow.
	 * @param name the item's name
	 * @param data its catalog data
	 * @param M the mob
	 * @param live true for LIVE rules, false for DROP ones
	 * @return true if a copy should appear
	 */
	public static boolean dropRoll(final String name, final CataData data, final MOB M, final boolean live)
	{
		if((data == null) || (data.getCap() <= 0))
			return false;
		final CataSpawn kind = live ? CataSpawn.LIVE : CataSpawn.DROP;
		for(final Rule r : rules(data))
		{
			if((r.spawn == kind) && (CMath.random() <= r.rate) && r.matches(M))
				return mayAppear(name, data, null);
		}
		return false;
	}

	/**
	 * Returns whether a copy of a catalog item coming back into the game (a
	 * mob's equipment as it comes back to life, or an item put back in its
	 * room) may: if not, it should not appear.  It is counted if it may.
	 * Nothing is held back while the world loads.
	 * @param I the copy
	 * @return true if it may come in
	 */
	public static boolean admit(final Item I)
	{
		if(!mayReturn(I))
			return false;
		returned(I);
		return true;
	}

	/**
	 * Returns whether a copy of a capped catalog item may come back into the
	 * game, such as an item put back in its room: see {@link #admit(Item)}.
	 * @param I the copy, or the copy kept to make it from
	 * @return true if it may
	 */
	public static boolean mayReturn(final Item I)
	{
		if((I == null) || (!CMLib.flags().isCataloged(I)) || (!CMProps.isState(CMProps.HostState.RUNNING)))
			return true;
		final CataData data = CMLib.catalog().getCatalogData(I);
		if((data == null) || (data.getCap() <= 0))
			return true;
		return mayAppear(I.Name(), data, I);
	}

	/**
	 * Counts a copy of a capped catalog item that came back into the game.
	 * @param I the copy
	 */
	public static void returned(final Item I)
	{
		if((I == null) || (!CMLib.flags().isCataloged(I)) || (!CMProps.isState(CMProps.HostState.RUNNING)))
			return;
		final CataData data = CMLib.catalog().getCatalogData(I);
		if((data != null) && (data.getCap() > 0))
			appeared(I.Name(), data, I);
	}

	/**
	 * Holds back the copies of capped catalog items a mob carries as it comes
	 * back to life, past what their caps allow.
	 * @param mob the mob
	 */
	public static void admitCarried(final MOB mob)
	{
		if((mob == null) || (!CMProps.isState(CMProps.HostState.RUNNING)))
			return;
		List<Item> gone = null;
		for(int i = 0; i < mob.numItems(); i++)
		{
			final Item I = mob.getItem(i);
			if((I != null) && CMLib.flags().isCataloged(I) && (!admit(I)))
			{
				if(gone == null)
					gone = new ArrayList<Item>(1);
				gone.add(I);
			}
		}
		if(gone != null)
		{
			for(final Item I : gone)
			{
				mob.delItem(I);
				I.destroy();
			}
		}
	}

	/**
	 * Counts the catalog's copies in the rooms of the world and on their
	 * mobs: when the world is rebuilt from its files, the areas are read
	 * before the catalog, so the copies a builder placed were not counted as
	 * they were made.
	 */
	public static void registerWorld()
	{
		final CatalogLibrary cat = CMLib.catalog();
		for(final Enumeration<Room> r = CMLib.map().roomsFilled(); r.hasMoreElements();)
		{
			final Room R = r.nextElement();
			if(R == null)
				continue;
			for(int i = 0; i < R.numItems(); i++)
			{
				final Item I = R.getItem(i);
				if((I != null) && CMLib.flags().isCataloged(I))
					cat.newInstance(I);
			}
			for(int m = 0; m < R.numInhabitants(); m++)
			{
				final MOB M = R.fetchInhabitant(m);
				if(M == null)
					continue;
				if(CMLib.flags().isCataloged(M))
					cat.newInstance(M);
				for(int i = 0; i < M.numItems(); i++)
				{
					final Item I = M.getItem(i);
					if((I != null) && CMLib.flags().isCataloged(I))
						cat.newInstance(I);
				}
			}
		}
	}

	/**
	 * Counts every copy of every unique item, noting those lost.  The
	 * catalog's timer and a save call this.
	 */
	public static void observeAll()
	{
		final CatalogLibrary cat = CMLib.catalog();
		for(final String name : cat.getCatalogItemNames())
		{
			final CataData data = cat.getCatalogItemData(name);
			if((data == null) || (data.getCap() <= 0) || howOf(data.getUnique()).equals("TIME"))
				continue;
			synchronized(Uniques.class)
			{
				observe(name, live(data, null));
			}
		}
	}

	/**
	 * Returns how many copies of an item have ever appeared, for one unique
	 * ever or for a while.
	 * @param name the item's name
	 * @return how many
	 */
	public static synchronized int ever(final String name)
	{
		final Track t = tracks.get(name.toUpperCase());
		return (t == null) ? 0 : t.ever;
	}

	/**
	 * Returns how many copies of an item are lost and not yet come again,
	 * for one unique ever or for a while.
	 * @param name the item's name
	 * @return how many
	 */
	public static synchronized int lost(final String name)
	{
		final Track t = tracks.get(name.toUpperCase());
		return (t == null) ? 0 : t.lost.size();
	}

	/**
	 * Returns how many game days until a lost copy of an item that is unique
	 * for a while may appear again.
	 * @param name the item's name
	 * @param data its catalog data
	 * @return the days, 0 if now, -1 if none is waiting
	 */
	public static synchronized long daysUntil(final String name, final CataData data)
	{
		final Track t = tracks.get(name.toUpperCase());
		if((t == null) || t.lost.isEmpty() || (data == null))
			return -1;
		final long left = (Math.max(0, data.getUniqueDays()) * dayMillis()) - (GameTime.now() - t.lost.getFirst().longValue());
		return (left <= 0) ? 0 : ((left + dayMillis() - 1) / dayMillis());
	}

	/**
	 * Forgets what was kept of an item, such as when the catalog entry is
	 * edited back to unique at a time.
	 * @param name the item's name
	 */
	public static synchronized void forget(final String name)
	{
		tracks.remove(name.toUpperCase());
	}

	/**
	 * Returns a container's mark that it has rolled its rules.
	 * @param I the container
	 * @return true if it has
	 */
	public static boolean wasRolled(final Item I)
	{
		return (I instanceof Container) && rolled.containsKey(I);
	}

	/**
	 * Marks a container as rolled, as a save puts it back.
	 * @param I the container
	 */
	public static void markRolled(final Item I)
	{
		if(I instanceof Container)
			rolled.put(I, Boolean.TRUE);
	}

	/**
	 * Takes away a container's mark that it has rolled: its contents were
	 * put back, so the next opening rolls again.
	 * @param C the container
	 */
	public static void refilled(final Item C)
	{
		if(C != null)
			rolled.remove(C);
	}

	/**
	 * Makes a copy of a catalog item and puts it in a container.
	 * @param name the item's name
	 * @param data its catalog data
	 * @param C the container
	 * @return the copy, or null
	 */
	private static Item putIn(final String name, final CataData data, final Container C)
	{
		final Item proto = CMLib.catalog().getCatalogItem(name);
		if((proto == null) || (!C.canContain(proto)))
			return null;
		final ItemPossessor owner = C.owner();
		if((!(owner instanceof MOB)) && (!(owner instanceof Room)))
			return null;
		final Item I = (Item)proto.copyOf();
		CMLib.catalog().changeCatalogUsage(I, true);
		I.setSavable(false); // a builder's SAVE leaves it out of the world's files
		owner.addItem(I);
		I.setContainer(C);
		I.recoverPhyStats();
		appeared(name, data, I);
		return I;
	}

	/**
	 * A container was opened, or looked into while open: the first time, the
	 * container rules on opening of every catalog item roll for it.  Only a
	 * player character's opening rolls them.
	 * @param C the container
	 * @param mob who opened it
	 */
	public static void opened(final Container C, final MOB mob)
	{
		if((C == null) || (mob == null) || (mob.playerStats() == null) || C.amDestroyed()
		|| (C instanceof DeadBody) || rolled.containsKey(C))
			return;
		rolled.put(C, Boolean.TRUE);
		final CatalogLibrary cat = CMLib.catalog();
		for(final String name : cat.getCatalogItemNames())
		{
			final CataData data = cat.getCatalogItemData(name);
			if((data == null) || (data.getCap() <= 0))
				continue;
			for(final Rule r : rules(data))
			{
				if(r.onOpen() && r.matches(C) && (CMath.random() < r.rate))
				{
					if(mayAppear(name, data, null))
						putIn(name, data, C);
					break;
				}
			}
		}
	}

	/**
	 * The catalog's timer, for one item: each room rule and each container
	 * rule on a timer rolls, and if it comes up, copies go into random
	 * matching rooms or containers, as many as the item may have.
	 * @param name the item's name
	 * @param data its catalog data
	 */
	public static void timer(final String name, final CataData data)
	{
		if((data == null) || (data.getCap() <= 0))
			return;
		for(final Rule r : rules(data))
		{
			if((!r.onTimer()) || (CMath.random() >= r.rate) || (!mayAppear(name, data, null)))
				continue;
			final List<Physical> places = (r.spawn == CataSpawn.ROOM) ? rooms(r) : containers(r, CMLib.catalog().getCatalogItem(name));
			for(int tries = data.getCap(); (tries > 0) && (places.size() > 0) && mayAppear(name, data, null); tries--)
			{
				final Physical P = places.get(CMLib.dice().roll(1, places.size(), -1));
				if(P instanceof Room)
				{
					final Item proto = CMLib.catalog().getCatalogItem(name);
					if(proto == null)
						break;
					final Item I = (Item)proto.copyOf();
					CMLib.catalog().changeCatalogUsage(I, true);
					I.setSavable(false);
					((Room)P).addItem(I);
					appeared(name, data, I);
				}
				else
				if(P instanceof Container)
				{
					if(putIn(name, data, (Container)P) == null)
						places.remove(P);
				}
			}
		}
	}

	/**
	 * The rooms a room rule may put a copy in.
	 * @param r the rule
	 * @return the rooms
	 */
	private static List<Physical> rooms(final Rule r)
	{
		final List<Physical> rooms = new ArrayList<Physical>();
		for(final Enumeration<Room> e = CMLib.map().roomsFilled(); e.hasMoreElements();)
		{
			final Room R = e.nextElement();
			if((R != null) && (!CMLib.flags().isHidden(R)) && r.matches(R))
				rooms.add(R);
		}
		return rooms;
	}

	/**
	 * The containers lying in rooms that a container rule may put a copy in.
	 * @param r the rule
	 * @param proto the catalog's own copy, which each must be able to hold
	 * @return the containers
	 */
	private static List<Physical> containers(final Rule r, final Item proto)
	{
		final List<Physical> found = new ArrayList<Physical>();
		if(proto == null)
			return found;
		for(final Enumeration<Room> e = CMLib.map().roomsFilled(); e.hasMoreElements();)
		{
			final Room R = e.nextElement();
			if(R == null)
				continue;
			for(int i = 0; i < R.numItems(); i++)
			{
				final Item I = R.getItem(i);
				if((I instanceof Container) && (!(I instanceof DeadBody)) && (!I.amDestroyed())
				&& ((Container)I).canContain(proto) && r.matches(I))
					found.add(I);
			}
		}
		return found;
	}

	/**
	 * Returns what is kept, for a save.
	 * @return the UNIQUES tag
	 */
	public static String toXML()
	{
		observeAll();
		final StringBuilder str = new StringBuilder("<UNIQUES>");
		synchronized(Uniques.class)
		{
			for(final Map.Entry<String, Track> e : tracks.entrySet())
			{
				final Track t = e.getValue();
				final StringBuilder lost = new StringBuilder();
				for(final Long L : t.lost)
					lost.append((lost.length() > 0) ? "," : "").append(L.longValue());
				str.append("<U NAME=\"").append(CMLib.xml().parseOutAngleBracketsAndQuotes(e.getKey()))
					.append("\" EVER=\"").append(t.ever)
					.append("\" LIVE=\"").append(t.lastLive)
					.append("\" LOST=\"").append(lost).append("\" />");
			}
		}
		str.append("</UNIQUES>");
		return str.toString();
	}

	/**
	 * Puts back what a save kept; with no tag (an older save), it starts
	 * again from the copies in the game.
	 * @param tag the UNIQUES tag, or null
	 */
	public static synchronized void fromXML(final XMLTag tag)
	{
		tracks.clear();
		if(tag == null)
			return;
		for(final XMLTag u : tag.contents())
		{
			if(!u.tag().equalsIgnoreCase("U"))
				continue;
			final String name = CMLib.xml().restoreAngleBrackets(u.getParmValue("NAME"));
			if((name == null) || (name.length() == 0))
				continue;
			final Track t = track(name);
			t.ever = CMath.s_int(u.getParmValue("EVER"));
			t.lastLive = CMath.s_int(u.getParmValue("LIVE"));
			for(final String L : CMParms.parseCommas(u.getParmValue("LOST"), true))
				t.lost.addLast(Long.valueOf(CMath.s_long(L)));
		}
	}

	/**
	 * Returns a catalog item's uniqueness and rules, for CATALOG LIST and
	 * EDIT: "once ever, 1 of 1 appeared" and the like.
	 * @param name the item's name
	 * @param data its catalog data
	 * @return the text
	 */
	public static String describe(final String name, final CataData data)
	{
		if(data == null)
			return "";
		final StringBuilder str = new StringBuilder();
		final String how = howOf(data.getUnique());
		final int cap = data.getCap();
		if(cap <= 0)
			str.append(CMLib.lang().L("No cap: none appear by a rule."));
		else
		if(how.equals("TIME"))
			str.append(CMLib.lang().L("At most @x1 at a time; @x2 now.", "" + cap, "" + live(data, null)));
		else
		{
			synchronized(Uniques.class)
			{
				observe(name, live(data, null));
			}
			if(how.equals("ONCE"))
				str.append(CMLib.lang().L("Once ever: @x1 may appear in all; @x2 have, @x3 now.", "" + cap, "" + ever(name), "" + live(data, null)));
			else
			{
				str.append(CMLib.lang().L("Once for a while: @x1, and one lost again after @x2 game days; @x3 have appeared, @x4 now.",
						"" + cap, "" + data.getUniqueDays(), "" + ever(name), "" + live(data, null)));
				final long days = daysUntil(name, data);
				if(days >= 0)
					str.append(" ").append(CMLib.lang().L("The next may appear in @x1 game days.", "" + days));
			}
		}
		for(final Rule r : rules(data))
			str.append("\n\r  ").append(r.text());
		for(final String line : data.getRules())
		{
			final StringBuilder why = new StringBuilder();
			if(parse(line, why) == null)
				str.append("\n\r  ").append(CMLib.lang().L("(not read: @x1: @x2)", line, why.toString()));
		}
		return str.toString();
	}
}
