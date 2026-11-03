package com.planet_ink.coffee_mud.core;
import com.planet_ink.coffee_mud.core.interfaces.*;
import com.planet_ink.coffee_mud.core.collections.*;
import com.planet_ink.coffee_mud.core.exceptions.CMException;
import com.planet_ink.coffee_mud.Abilities.interfaces.*;
import com.planet_ink.coffee_mud.Areas.interfaces.*;
import com.planet_ink.coffee_mud.Common.interfaces.*;
import com.planet_ink.coffee_mud.Exits.interfaces.*;
import com.planet_ink.coffee_mud.Items.interfaces.*;
import com.planet_ink.coffee_mud.Libraries.interfaces.XMLLibrary.XMLTag;
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
 * G13: how a generated area was made, and making it again.  GENERATE AREA
 * gives the area a GENSOURCE flag (a blurb flag, saved in its .cmare): the
 * template file, the AREA id, the seed and the variables given, written as
 * GENERATE's own words.  REGENERATE AREA builds the area again from it, with
 * the same seed (SAME), another (SEED=n) or a new one, and keeps the rooms
 * marked GENLOCK (Prop_GenLock) as they are, with their exits: the other
 * rooms are replaced by the new build's rooms of the same IDs, and every
 * exit, from inside the area or out, is pointed at the room of its ID.
 * GEN_BACKTICKS=false in the ini keeps real apostrophes in what GENERATE
 * makes, rather than the engine's backticks.
 */
public final class GenSource
{
	private GenSource()
	{
	}

	/** the area flag */
	public static final String	FLAG	= "GENSOURCE";

	/** an area's source, as read */
	public static final class Source
	{
		/** the template file, as GENERATE's FROM names it */
		public final String					file;
		/** the AREA tag's id */
		public final String					id;
		/** the seed it was built with */
		public final long					seed;
		/** the variables given */
		public final Map<String, String>	vars	= new LinkedHashMap<String, String>();

		Source(final String file, final String id, final long seed)
		{
			this.file = file;
			this.id = id;
			this.seed = seed;
		}
	}

	private static String quote(final String v)
	{
		if((v.indexOf(' ') < 0) && (v.indexOf('"') < 0) && (v.length() > 0))
			return v;
		return "\"" + v.replace("\"", "'") + "\"";
	}

	/**
	 * @param file the template file
	 * @param id the AREA id
	 * @param seed the seed
	 * @param vars the variables given
	 * @return the flag's text: file id SEED=n VAR=VALUE ...
	 */
	public static String encode(final String file, final String id, final long seed, final Map<String, String> vars)
	{
		final StringBuilder str = new StringBuilder(quote(file)).append(' ').append(quote(id.toLowerCase())).append(" SEED=").append(seed);
		if(vars != null)
		{
			for(final Map.Entry<String, String> e : vars.entrySet())
			{
				final String k = e.getKey().toUpperCase().trim();
				if(k.equals("SEED") || k.equals("GEN_SEED") || (k.length() == 0))
					continue;
				str.append(' ').append(quote(e.getKey().toLowerCase() + "=" + e.getValue()));
			}
		}
		return str.toString();
	}

	/**
	 * @param text a GENSOURCE flag's text
	 * @return the source, or null if it does not read
	 */
	public static Source decode(final String text)
	{
		if((text == null) || (text.trim().length() == 0))
			return null;
		final List<String> w = CMParms.parse(text.trim());
		if(w.size() < 2)
			return null;
		long seed = 0;
		final Map<String, String> vars = new LinkedHashMap<String, String>();
		for(int i = 2; i < w.size(); i++)
		{
			final String s = w.get(i);
			final int x = s.indexOf('=');
			if(x <= 0)
				continue;
			final String k = s.substring(0, x).trim();
			final String v = s.substring(x + 1).trim();
			if(k.equalsIgnoreCase("SEED"))
				seed = CMath.s_long(v);
			else
				vars.put(k, v);
		}
		final Source S = new Source(w.get(0), w.get(1), seed);
		S.vars.putAll(vars);
		return S;
	}

	/**
	 * Gives an area its GENSOURCE.
	 * @param A the area
	 * @param file the template file
	 * @param id the AREA id
	 * @param seed the seed
	 * @param vars the variables given
	 */
	public static void record(final Area A, final String file, final String id, final long seed, final Map<String, String> vars)
	{
		A.delBlurbFlag(FLAG);
		A.addBlurbFlag(FLAG + " " + encode(file, id, seed, vars));
	}

	/**
	 * @param A an area
	 * @return how it was made, or null if GENERATE did not make it
	 */
	public static Source get(final Area A)
	{
		return (A == null) ? null : decode(A.getBlurbFlag(FLAG));
	}

	/**
	 * @param R a room
	 * @return true if REGENERATE keeps it (Prop_GenLock)
	 */
	public static boolean isLocked(final Room R)
	{
		return (R != null) && (R.fetchEffect("Prop_GenLock") != null);
	}

	/**
	 * Marks a room GENLOCK, or not.
	 * @param R the room
	 * @param lock true to lock it
	 * @return true if that changed it
	 */
	public static boolean lock(final Room R, final boolean lock)
	{
		final Ability A = R.fetchEffect("Prop_GenLock");
		if(lock == (A != null))
			return false;
		if(lock)
		{
			final Ability P = CMClass.getAbility("Prop_GenLock");
			if(P == null)
				return false;
			R.addNonUninvokableEffect(P);
		}
		else
			R.delEffect(A);
		return true;
	}

	/** @return true if GENERATE keeps the engine's backticks for apostrophes (GEN_BACKTICKS, true unless set false) */
	public static boolean backticks()
	{
		final String s = CMProps.instance().getProperty("GEN_BACKTICKS");
		return (s == null) || (s.trim().length() == 0) || CMath.s_bool(s.trim()) || s.trim().equalsIgnoreCase("YES");
	}

	private static String untick(final String s)
	{
		return (s == null) ? null : s.replace('`', '\'');
	}

	private static void untick(final Item I)
	{
		I.setName(untick(I.Name()));
		I.setDisplayText(untick(I.displayText()));
		I.setDescription(untick(I.description()));
		if(I.isReadable())
			I.setReadableText(untick(I.readableText()));
	}

	private static void untick(final MOB M)
	{
		M.setName(untick(M.Name()));
		M.setDisplayText(untick(M.displayText()));
		M.setDescription(untick(M.description()));
		for(final Enumeration<Item> i = M.items(); i.hasMoreElements();)
			untick(i.nextElement());
	}

	/**
	 * GEN_BACKTICKS=false: real apostrophes in what was made, its names and
	 * texts, rather than backticks.
	 * @param o a room, mob, item or area
	 */
	public static void untick(final Object o)
	{
		if(o instanceof Area)
		{
			final Area A = (Area)o;
			A.setDescription(untick(A.description()));
			for(final Enumeration<Room> r = A.getProperMap(); r.hasMoreElements();)
				untick(r.nextElement());
		}
		else
		if(o instanceof Room)
		{
			final Room R = (Room)o;
			R.setDisplayText(untick(R.displayText()));
			R.setDescription(untick(R.description()));
			for(final Enumeration<MOB> m = R.inhabitants(); m.hasMoreElements();)
			{
				final MOB M = m.nextElement();
				if((M != null) && M.isMonster())
					untick(M);
			}
			for(final Enumeration<Item> i = R.items(); i.hasMoreElements();)
				untick(i.nextElement());
			for(int d = 0; d < Directions.NUM_DIRECTIONS(); d++)
			{
				final Exit E = R.getRawExit(d);
				if((E != null) && E.isGeneric())
				{
					E.setName(untick(E.Name()));
					E.setDisplayText(untick(E.displayText()));
					E.setDescription(untick(E.description()));
				}
			}
		}
		else
		if(o instanceof MOB)
			untick((MOB)o);
		else
		if(o instanceof Item)
			untick((Item)o);
	}

	private static boolean followsAPlayer(final MOB M)
	{
		final MOB L = M.getGroupLeader();
		return (L != null) && (L != M) && !L.isMonster();
	}

	private static Room roomFor(final Map<String, Room> kept, final String id)
	{
		return (id == null) ? null : kept.get(id);
	}

	/**
	 * G14: whether a running quest uses a room, or someone or something in it.
	 * @param R the room
	 * @return true if one does
	 */
	public static boolean questUses(final Room R)
	{
		for(final Enumeration<Quest> q = CMLib.quests().enumQuests(); q.hasMoreElements();)
		{
			final Quest Q = q.nextElement();
			if((Q == null) || (!Q.running()))
				continue;
			if(Q.isObjectInUse(R))
				return true;
			for(final Enumeration<MOB> m = R.inhabitants(); m.hasMoreElements();)
			{
				final MOB M = m.nextElement();
				if((M != null) && Q.isObjectInUse(M))
					return true;
			}
			for(final Enumeration<Item> i = R.items(); i.hasMoreElements();)
			{
				final Item I = i.nextElement();
				if((I != null) && Q.isObjectInUse(I))
					return true;
			}
		}
		return false;
	}

	/**
	 * G14: whether a player, or one following a player, is in the area.
	 * @param A the area
	 * @return true if one is
	 */
	public static boolean occupied(final Area A)
	{
		for(final Enumeration<Room> r = A.getProperMap(); r.hasMoreElements();)
		{
			final Room R = r.nextElement();
			for(final Enumeration<MOB> m = R.inhabitants(); m.hasMoreElements();)
			{
				final MOB M = m.nextElement();
				if((M != null) && ((!M.isMonster()) || M.isPlayer() || followsAPlayer(M)))
					return true;
			}
		}
		return false;
	}

	/**
	 * Builds an area again from its GENSOURCE, keeping its GENLOCK rooms.
	 * @param mob the builder
	 * @param A the area
	 * @param seedWord SAME, a number, or null for a new seed
	 * @param extra variables to change, or null
	 * @param save true to write the rooms through the database as GENERATE does
	 * @return what was done, to tell the builder
	 */
	public static String regenerate(final MOB mob, final Area A, final String seedWord, final Map<String, String> extra, final boolean save)
	{
		return regenerate(mob, A, get(A), seedWord, extra, save, null);
	}

	/**
	 * Builds an area again from a source, keeping its GENLOCK rooms, the
	 * rooms named, and the rooms a running quest uses (G14), and runs the
	 * area's GENERATED_PROG after.
	 * @param mob the builder, or null for the world (the Regenerate behaviour)
	 * @param A the area
	 * @param S the source: its GENSOURCE, or the behaviour's FILE and ID
	 * @param seedWord SAME, a number, or null for a new seed
	 * @param extra variables to change, or null
	 * @param save true to write the rooms through the database as GENERATE does
	 * @param keepIds room IDs kept besides the GENLOCK ones, or null
	 * @return what was done
	 */
	public static String regenerate(final MOB mob, final Area A, final Source S, final String seedWord, final Map<String, String> extra, final boolean save, final Set<String> keepIds)
	{
		if(S == null)
			return "'" + A.Name() + "' has no " + FLAG + ": GENERATE AREA did not make it.";
		final CMFile F = new CMFile(Resources.buildResourcePath(S.file), mob);
		if(!F.canRead())
			return "The template " + S.file + " cannot be read.";
		final List<XMLTag> xmlRoot = CMLib.xml().parseAllXML(F.textUnformatted());
		final Hashtable<String, Object> defined = new Hashtable<String, Object>();
		final Map<String, String> vars = new LinkedHashMap<String, String>(S.vars);
		if(extra != null)
			vars.putAll(extra);
		for(final Map.Entry<String, String> e : vars.entrySet())
			defined.put(e.getKey().toUpperCase(), e.getValue());
		CMLib.percolator().buildDefinedIDSet(xmlRoot, defined, new XTreeSet<String>(defined.keySet()));
		final Object o = defined.get(S.id.toUpperCase());
		if((!(o instanceof XMLTag)) || (!((XMLTag)o).tag().equalsIgnoreCase("AREA")))
			return "There is no AREA '" + S.id + "' in " + S.file + ".";
		final XMLTag piece = (XMLTag)o;
		for(final Map.Entry<String, String> e : vars.entrySet())
			defined.put(e.getKey().toUpperCase(), e.getValue());
		try
		{
			CMLib.percolator().checkRequirements(piece, defined);
		}
		catch(final CMException cme)
		{
			return "Required ids for " + S.id + " were missing: " + cme.getMessage();
		}
		final long seed;
		if((seedWord != null) && seedWord.equalsIgnoreCase("SAME"))
			seed = S.seed;
		else
			seed = GenSeed.pick(seedWord, null);
		defined.put("GEN_SEED", "" + seed);
		GenSeed.context(defined);

		// the rooms there are, those kept, and the way in from outside
		final List<Room> oldRooms = new ArrayList<Room>();
		for(final Enumeration<Room> r = A.getProperMap(); r.hasMoreElements();)
			oldRooms.add(r.nextElement());
		final Set<Room> oldSet = new HashSet<Room>(oldRooms);
		final Map<String, Room> locked = new HashMap<String, Room>();
		for(final Room R : oldRooms)
		{
			if(isLocked(R) || ((keepIds != null) && keepIds.contains(R.roomID().toUpperCase())) || questUses(R))
				locked.put(R.roomID(), R);
		}
		Room entrance = A.getRoom(A.Name() + "#0");
		if((entrance == null) && (oldRooms.size() > 0))
			entrance = oldRooms.get(0);
		Room gate = null;
		int direction = Directions.NORTH;
		if(entrance != null)
		{
			for(int d = 0; (d < Directions.NUM_DIRECTIONS()) && (gate == null); d++)
			{
				final Room R2 = entrance.rawDoors()[d];
				if((R2 != null) && !oldSet.contains(R2))
				{
					gate = R2;
					direction = Directions.getOpDirectionCode(d);
				}
			}
		}
		defined.put("ROOMTAG_NODEGATEEXIT", CMLib.directions().getDirectionName(Directions.getOpDirectionCode(direction)));
		final Room gateFor = (gate != null) ? gate : (((mob != null) && (mob.location() != null)) ? mob.location() : entrance);
		if(gateFor != null)
			defined.put("ROOMTAG_GATEEXITROOM", gateFor);

		final Map<String, Room> kept = new HashMap<String, Room>(locked);
		final List<Room> fresh = new ArrayList<Room>();
		final List<Room> dropped = new ArrayList<Room>();
		int lostExits = 0, oneWay = 0;
		String postProblem = null;
		Area N = null;
		final Long before = GenSeed.begin(seed);
		try
		{
			CMLib.map().delArea(A);
			try
			{
				N = CMLib.percolator().findArea(piece, defined, direction);
			}
			catch(final CMException cex)
			{
				return "Unable to regenerate: " + cex.getMessage();
			}
			finally
			{
				CMLib.map().addArea(A);
			}
			if(N == null)
				return "Nothing was generated from " + S.id + ".";
			final String newPrefix = N.Name() + "#";
			final String prefix = A.Name() + "#";
			final List<Room> made = new ArrayList<Room>();
			for(final Enumeration<Room> r = N.getProperMap(); r.hasMoreElements();)
				made.add(r.nextElement());
			// the old rooms that go, out of the area first, so the new ones can take their IDs
			for(final Room R : oldRooms)
			{
				if(!locked.containsKey(R.roomID()))
					A.delProperRoom(R);
			}
			for(final Room R : made)
			{
				String id = R.roomID();
				if((!newPrefix.equals(prefix)) && id.startsWith(newPrefix))
					id = prefix + id.substring(newPrefix.length());
				if(locked.containsKey(id))
				{
					dropped.add(R);
					continue;
				}
				R.setRoomID(id);
				R.setArea(A);
				kept.put(id, R);
				fresh.add(R);
			}
			// every exit to the old or the made rooms points at the room of its ID now
			final Set<Room> stale = new HashSet<Room>(oldSet);
			stale.addAll(dropped);
			stale.addAll(made);
			for(final Room X : new ArrayList<Room>(kept.values()))
			{
				for(int d = 0; d < Directions.NUM_DIRECTIONS(); d++)
				{
					final Room T = X.rawDoors()[d];
					if((T == null) || !stale.contains(T))
						continue;
					final Room to = roomFor(kept, T.roomID());
					if(to == null)
					{
						X.rawDoors()[d] = null;
						X.setRawExit(d, null);
						lostExits++;
					}
					else
						X.rawDoors()[d] = to;
				}
			}
			for(final Enumeration<Area> a = CMLib.map().areas(); a.hasMoreElements();)
			{
				final Area B = a.nextElement();
				if(B == A)
					continue;
				for(final Enumeration<Room> r = B.getProperMap(); r.hasMoreElements();)
				{
					final Room Y = r.nextElement();
					for(int d = 0; d < Directions.NUM_DIRECTIONS(); d++)
					{
						final Room T = Y.rawDoors()[d];
						if((T == null) || !stale.contains(T))
							continue;
						final Room to = roomFor(kept, T.roomID());
						Y.rawDoors()[d] = to;
						if(to == null)
						{
							Y.setRawExit(d, null);
							lostExits++;
							continue;
						}
						final int op = Directions.getOpDirectionCode(d);
						if(to.rawDoors()[op] == null)
						{
							to.rawDoors()[op] = Y;
							to.setRawExit(op, Y.getRawExit(d));
						}
					}
				}
			}
			// a kept room and the new rooms beside it: each exit given its way back where that is free
			for(final Room K : locked.values())
			{
				for(int d = 0; d < Directions.NUM_DIRECTIONS(); d++)
				{
					final int op = Directions.getOpDirectionCode(d);
					final Room T = K.rawDoors()[d];
					if((T != null) && (T.getArea() == A) && (T.rawDoors()[op] != K))
					{
						if(T.rawDoors()[op] == null)
						{
							T.rawDoors()[op] = K;
							T.setRawExit(op, K.getRawExit(d));
						}
						else
							oneWay++;
					}
				}
				for(final Room T : fresh)
				{
					for(int d = 0; d < Directions.NUM_DIRECTIONS(); d++)
					{
						final int op = Directions.getOpDirectionCode(d);
						if((T.rawDoors()[d] == K) && (K.rawDoors()[op] != T))
						{
							if(K.rawDoors()[op] == null)
							{
								K.rawDoors()[op] = T;
								K.setRawExit(op, T.getRawExit(d));
							}
							else
								oneWay++;
						}
					}
				}
			}
			// the players, and those following them, go to the room of the same ID
			for(final Room R : oldRooms)
			{
				if(locked.containsKey(R.roomID()))
					continue;
				Room to = roomFor(kept, R.roomID());
				if(to == null)
					to = roomFor(kept, A.Name() + "#0");
				if((to == null) && (fresh.size() > 0))
					to = fresh.get(0);
				final List<MOB> here = new ArrayList<MOB>();
				for(final Enumeration<MOB> m = R.inhabitants(); m.hasMoreElements();)
					here.add(m.nextElement());
				for(final MOB M : here)
				{
					if((M != null) && ((!M.isMonster()) || M.isPlayer() || followsAPlayer(M)) && (to != null))
						to.bringMobHere(M, true);
				}
			}
			try
			{
				CMLib.percolator().postProcess(defined);
			}
			catch(final CMException cex)
			{
				postProblem = cex.getMessage();
			}
		}
		finally
		{
			GenSeed.end(before);
		}
		for(final Room R : oldRooms)
		{
			if(locked.containsKey(R.roomID()))
				continue;
			if(save)
				CMLib.database().DBDeleteRoom(R);
			CMLib.map().emptyRoom(R, null, true);
			R.destroy();
		}
		for(final Room R : dropped)
		{
			CMLib.map().emptyRoom(R, null, true);
			R.destroy();
		}
		if(N != null)
			N.destroy();
		if(!backticks())
		{
			for(final Room R : fresh)
				untick(R);
		}
		record(A, S.file, S.id, seed, vars);
		A.delBlurbFlag("GENSEED");
		A.addBlurbFlag("GENSEED " + seed);
		for(final Room R : fresh)
			Unsaved.mark(R, "REGENERATE");
		if(save)
		{
			for(final Room R : fresh)
			{
				CMLib.database().DBCreateRoom(R);
				CMLib.database().DBUpdateExits(R);
				CMLib.database().DBUpdateItems(R);
				CMLib.database().DBUpdateMOBs(R);
			}
			for(final Room R : locked.values())
				CMLib.database().DBUpdateExits(R);
			CMLib.database().DBUpdateArea(A.Name(), A);
		}
		Log.sysOut("Regenerate", ((mob == null) ? "The world" : mob.Name()) + " regenerated area " + A.Name() + " with seed " + seed);
		ScriptEvents.generated(A, null, S.id, defined); // G14: GENERATED_PROG (ST20) after a re-roll too
		return "Regenerated '" + A.Name() + "' with seed " + seed + ": " + fresh.size() + " rooms made, " + locked.size() + " kept (GENLOCK)"
			+ ((lostExits > 0) ? (", " + lostExits + " exits removed, to rooms the new build does not have") : "")
			+ ((oneWay > 0) ? (", " + oneWay + " exits at kept rooms one-way, where the new rooms lead elsewhere") : "") + "."
			+ ((postProblem != null) ? (" Not all of it could be finished: " + postProblem) : "");
	}
}
