package com.planet_ink.coffee_mud.core;
import com.planet_ink.coffee_mud.core.interfaces.*;
import com.planet_ink.coffee_mud.core.exceptions.CMException;
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
 * G5 and G11: generation from a template file at play time, a fresh roll
 * each call (no cache), for MPGENERATE, GENSTRING, FROMGENFILE FRESH and
 * JavaScript's gen object.  Every call defines PLAYER_LEVEL, PARTY_SIZE,
 * AREA_LEVEL, AREA_THEME, HOUR, SEASON, WEATHER and GAME_DAY, unless the
 * caller gave them.
 */
public final class GenCalls
{
	private GenCalls()
	{
	}

	/** a template tag ready to build, and its variables */
	public static final class Prepared
	{
		/** the tag */
		public final XMLTag					piece;
		/** the variables */
		public final Map<String, Object>	defined;

		Prepared(final XMLTag piece, final Map<String, Object> defined)
		{
			this.piece = piece;
			this.defined = defined;
		}
	}

	/**
	 * A template file's text, from resources/ (and the world's overlay), else as a path.
	 * @param file the file
	 * @return its text
	 * @throws CMException if there is no such file
	 */
	public static StringBuffer text(final String file) throws CMException
	{
		CMFile F = new CMFile(Resources.buildResourcePath(file.trim()), null);
		if(!F.canRead())
			F = new CMFile(file.trim(), null);
		if(!F.canRead())
			throw new CMException("No generator file " + file);
		return F.textUnformatted();
	}

	/**
	 * VAR=VALUE words, as GENERATE takes them.
	 * @param words the words
	 * @return the variables, keys in upper case
	 */
	public static Map<String, String> vars(final String words)
	{
		final Map<String, String> m = new HashMap<String, String>();
		if(words != null)
		{
			for(final Map.Entry<String, String> e : CMParms.parseEQParms(words).entrySet())
				m.put(e.getKey().toUpperCase().trim(), e.getValue());
		}
		return m;
	}

	/**
	 * The variables every call sees, where the caller gave none: the player's
	 * level and party, the area's level and theme, the hour, season, weather.
	 * @param defined the variables
	 * @param player the player, or null
	 * @param here where it is, or null
	 */
	public static void context(final Map<String, Object> defined, final MOB player, final Room here)
	{
		final Area A = (here == null) ? null : here.getArea();
		if(!defined.containsKey("PLAYER_LEVEL"))
			defined.put("PLAYER_LEVEL", "" + ((player == null) ? 1 : player.phyStats().level()));
		if(!defined.containsKey("PARTY_SIZE"))
			defined.put("PARTY_SIZE", "" + ((player == null) ? 1 : Math.max(1, player.getGroupMembers(new HashSet<MOB>()).size())));
		if(A != null)
		{
			if(!defined.containsKey("AREA_LEVEL"))
				defined.put("AREA_LEVEL", "" + A.getIStat(Area.Stats.MED_LEVEL));
			if(!defined.containsKey("AREA_THEME"))
			{
				final int t = A.getTheme();
				defined.put("AREA_THEME", ((t >= 0) && (t < Area.THEME_NAMES.length)) ? Area.THEME_NAMES[t] : ("" + t));
			}
			final TimeClock C = A.getTimeObj();
			if((C != null) && !defined.containsKey("HOUR"))
				defined.put("HOUR", "" + C.getHourOfDay());
			if(!defined.containsKey("SEASON"))
				defined.put("SEASON", Calendars.season(A).name);
			if(!defined.containsKey("WEATHER"))
			{
				final int w = A.getClimateObj().weatherType(here);
				defined.put("WEATHER", ((w >= 0) && (w < Climate.WEATHER_DESCS.length)) ? Climate.WEATHER_DESCS[w] : "CLEAR");
			}
		}
		GenSeed.context(defined);
	}

	/**
	 * A template tag ready to build: the file read, the variables set, the
	 * tag's requirements checked and its rewards defined.
	 * @param file the file
	 * @param id the tag's id
	 * @param tag the kind it must be (ITEM, MOB, STRING, ROOM, AREA, QUEST), or null
	 * @param vars the caller's variables, or null
	 * @param player the player, or null
	 * @param here where, or null
	 * @return the tag and its variables
	 * @throws CMException if it cannot be built
	 */
	public static Prepared prepare(final String file, final String id, final String tag, final Map<String, String> vars, final MOB player, final Room here) throws CMException
	{
		final List<XMLTag> xml = CMLib.xml().parseAllXML(text(file));
		final Map<String, Object> defined = new Hashtable<String, Object>();
		if(vars != null)
			defined.putAll(vars);
		CMLib.percolator().buildDefinedIDSet(xml, defined, new TreeSet<String>(defined.keySet()));
		final Object o = defined.get(id.toUpperCase().trim());
		if((!(o instanceof XMLTag)) || ((tag != null) && !((XMLTag)o).tag().equalsIgnoreCase(tag)))
			throw new CMException("No " + ((tag == null) ? "tag" : tag) + " '" + id + "' in " + file);
		final XMLTag piece = (XMLTag)o;
		if(vars != null)
			defined.putAll(vars);
		context(defined, player, here);
		CMLib.percolator().checkRequirements(piece, defined);
		CMLib.percolator().preDefineReward(piece, defined);
		CMLib.percolator().defineReward(piece, defined);
		return new Prepared(piece, defined);
	}

	/**
	 * Items from an ITEM tag, a fresh roll.
	 * @param file the file
	 * @param id the tag's id
	 * @param vars the caller's variables, or null
	 * @param player the player, or null
	 * @param here where, or null
	 * @return the items, not placed
	 * @throws CMException if it cannot be built
	 */
	public static List<Item> items(final String file, final String id, final Map<String, String> vars, final MOB player, final Room here) throws CMException
	{
		final Prepared P = prepare(file, id, "ITEM", vars, player, here);
		final List<Item> L = CMLib.percolator().findItems(P.piece, P.defined);
		CMLib.percolator().postProcess(P.defined);
		return L;
	}

	/**
	 * Mobs from a MOB tag, a fresh roll.
	 * @param file the file
	 * @param id the tag's id
	 * @param vars the caller's variables, or null
	 * @param player the player, or null
	 * @param here where, or null
	 * @return the mobs, not brought to life
	 * @throws CMException if it cannot be built
	 */
	public static List<MOB> mobs(final String file, final String id, final Map<String, String> vars, final MOB player, final Room here) throws CMException
	{
		final Prepared P = prepare(file, id, "MOB", vars, player, here);
		final List<MOB> L = CMLib.percolator().findMobs(P.piece, P.defined);
		CMLib.percolator().postProcess(P.defined);
		return L;
	}

	/**
	 * Text from a STRING tag, a fresh roll.
	 * @param file the file
	 * @param id the tag's id
	 * @param vars the caller's variables, or null
	 * @param player the player, or null
	 * @param here where, or null
	 * @return the text, or ""
	 * @throws CMException if it cannot be built
	 */
	public static String string(final String file, final String id, final Map<String, String> vars, final MOB player, final Room here) throws CMException
	{
		final Prepared P = prepare(file, id, "STRING", vars, player, here);
		final String s = CMLib.percolator().findString("STRING", P.piece, P.defined);
		CMLib.percolator().postProcess(P.defined);
		return (s == null) ? "" : s;
	}

	/**
	 * Links a new room off an old one, as GENERATE does.
	 * @param from the old room
	 * @param to the new one
	 * @param dir the direction from the old one
	 */
	public static void link(final Room from, final Room to, final int dir)
	{
		if(to.roomID().length() == 0)
		{
			to.setArea(from.getArea());
			to.setRoomID(from.getArea().getNewRoomID(from, dir));
		}
		final int opDir = Directions.getOpDirectionCode(dir);
		Exit E = to.getExitInDir(opDir);
		if(E == null)
			E = CMClass.getExit("Open");
		from.setRawExit(dir, E);
		from.rawDoors()[dir] = to;
		if(to.getRoomInDir(opDir) == null)
		{
			to.setRawExit(opDir, E);
			to.rawDoors()[opDir] = from;
		}
	}

	/**
	 * A room from a ROOM tag, linked off a room in a direction it has free.
	 * @param file the file
	 * @param id the tag's id
	 * @param here the room it is linked to
	 * @param dir the direction from there
	 * @param vars the caller's variables, or null
	 * @param player the player, or null
	 * @return the room
	 * @throws CMException if it cannot be built, or the way is taken
	 */
	public static Room room(final String file, final String id, final Room here, final int dir, final Map<String, String> vars, final MOB player) throws CMException
	{
		if((here == null) || (dir < 0) || (here.getRoomInDir(dir) != null))
			throw new CMException("No free way to build a room in");
		final Prepared P = prepare(file, id, "ROOM", vars, player, here);
		P.defined.put("ROOMTAG_NODEGATEEXIT", CMLib.directions().getDirectionName(Directions.getOpDirectionCode(dir)));
		P.defined.put("ROOMTAG_GATEEXITROOM", here);
		final Room R = CMLib.percolator().buildRoom(here.getArea(), P.piece, P.defined, new Exit[Directions.NUM_DIRECTIONS()], dir);
		if(R == null)
			throw new CMException("Nothing generated");
		link(here, R, dir);
		CMLib.percolator().postProcess(P.defined);
		ScriptEvents.generated(null, R, id.toUpperCase().trim(), P.defined);
		return R;
	}

	/**
	 * An area from an AREA tag, its entrance linked off a room in a direction it has free.
	 * @param file the file
	 * @param id the tag's id
	 * @param here the room it is linked to
	 * @param dir the direction from there
	 * @param vars the caller's variables, or null
	 * @param seed the seed's text (a number, GAME, ...), or null for a new one
	 * @param player the player, or null
	 * @return the area
	 * @throws CMException if it cannot be built, or the way is taken
	 */
	public static Area area(final String file, final String id, final Room here, final int dir, final Map<String, String> vars, final String seed, final MOB player) throws CMException
	{
		if((here == null) || (dir < 0) || (here.getRoomInDir(dir) != null))
			throw new CMException("No free way to build an area in");
		final Prepared P = prepare(file, id, "AREA", vars, player, here);
		final long s = GenSeed.pick(seed, null);
		P.defined.put("GEN_SEED", "" + s);
		P.defined.put("ROOMTAG_NODEGATEEXIT", CMLib.directions().getDirectionName(Directions.getOpDirectionCode(dir)));
		P.defined.put("ROOMTAG_GATEEXITROOM", here);
		final Long before = GenSeed.begin(s);
		final Area A;
		try
		{
			A = CMLib.percolator().findArea(P.piece, P.defined, dir);
			if(A == null)
				throw new CMException("Nothing generated");
			A.addBlurbFlag("GENSEED " + s);
			CMLib.map().addArea(A);
			CMLib.map().registerWorldObjectLoaded(A, null, A);
			Room R = A.getRoom(A.Name() + "#0");
			if(R == null)
				R = A.getFilledProperMap().nextElement();
			link(here, R, dir);
			CMLib.percolator().postProcess(P.defined);
		}
		finally
		{
			GenSeed.end(before);
		}
		ScriptEvents.generated(A, null, id.toUpperCase().trim(), P.defined);
		return A;
	}

	/**
	 * A quest from a QUEST tag, made and started.
	 * @param file the file
	 * @param id the tag's id
	 * @param vars the caller's variables, or null
	 * @param player the player, or null
	 * @param here where, or null
	 * @return the quest
	 * @throws CMException if it cannot be built or started
	 */
	public static Quest quest(final String file, final String id, final Map<String, String> vars, final MOB player, final Room here) throws CMException
	{
		final Prepared P = prepare(file, id, "QUEST", vars, player, here);
		final String script = CMLib.percolator().buildQuestScript(P.piece, P.defined, null);
		CMLib.percolator().postProcess(P.defined);
		if(script == null)
			throw new CMException("No quest script generated");
		final Quest Q = (Quest)CMClass.getCommon("DefaultQuest");
		Q.setScript(script, true);
		if((Q.name().trim().length() == 0) || (Q.duration() < 0))
			throw new CMException("The generated quest has no name or duration");
		if(CMLib.quests().fetchQuest(Q.name()) != null)
			throw new CMException("A quest named " + Q.name() + " already exists");
		CMLib.quests().addQuest(Q);
		if((!Q.running()) && (!Q.startQuest()))
		{
			CMLib.quests().delQuest(Q);
			throw new CMException("The generated quest " + Q.name() + " would not start");
		}
		return Q;
	}
}
