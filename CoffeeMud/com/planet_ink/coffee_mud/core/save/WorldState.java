package com.planet_ink.coffee_mud.core.save;

import com.planet_ink.coffee_mud.core.*;
import com.planet_ink.coffee_mud.core.interfaces.*;
import com.planet_ink.coffee_mud.core.collections.Pair;
import com.planet_ink.coffee_mud.core.collections.XVector;
import com.planet_ink.coffee_mud.Abilities.interfaces.*;
import com.planet_ink.coffee_mud.Areas.interfaces.*;
import com.planet_ink.coffee_mud.Behaviors.interfaces.*;
import com.planet_ink.coffee_mud.CharClasses.interfaces.*;
import com.planet_ink.coffee_mud.Common.interfaces.*;
import com.planet_ink.coffee_mud.Exits.interfaces.*;
import com.planet_ink.coffee_mud.Items.interfaces.*;
import com.planet_ink.coffee_mud.Libraries.interfaces.XMLLibrary;
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
 * The live world in a saved game.  The database holds the world as built:
 * a restored game boots that, with every mob and item where the builder put
 * it, and {@link #apply(String)} then makes the world as it was when saved.
 * Saved here:
 * <ul>
 * <li>what is in each room: its mobs, where they belong and how hurt they are,
 * with what they carry, and the items on the floor;</li>
 * <li>mobs that are dead and waiting to come back, and how long they have
 * left;</li>
 * <li>items that were taken from where they belong and are due to come back
 * there;</li>
 * <li>which doors are open or locked, and each area's weather;</li>
 * <li>effects with time left to run, on the player, mobs, items, rooms and
 * exits, which the database drops;</li>
 * <li>the player's hit points, mana and movement, what they carry and who
 * follows them, which the character's tables hold only in part;</li>
 * <li>the stats that the engine's built-in mob classes roll when made;</li>
 * <li>quests made in play (generated ones and quests' copies), whole, to be
 * made again from their scripts;</li>
 * <li>each quest's winners, whether it is running, its step and time left,
 * where its mobs and items are, the variables its scripts set on it, and how
 * long a quest that is not running has yet to wait;</li>
 * <li>the variables that scripts share;</li>
 * <li>who rides what: mobs on mounts, characters on their horses, and carts
 * hitched to their teams;</li>
 * <li>what the behaviours and scripts of mobs, items, rooms and areas have
 * built up while running (their STATE): which ONCE_PROGs have run, DELAY
 * countdowns, queued MPALARMs, their own variables, and each behaviour's
 * own.</li>
 * </ul>
 * Mobs that cannot be saved, such as summoned ones, are left out unless they
 * follow the player, and things a quest is using are left to the quest.  The
 * player's inventory, followers and timed effects go back once the player is
 * in the game (see {@link #finishRestore(MOB)}), since the player is often
 * who caused the effects.
 */
public final class WorldState
{
	/** the file in a save that holds the world */
	public static final String	FILE	= "world.xml";

	private static final int	VERSION	= 1;

	private WorldState()
	{
	}

	/**
	 * Writes down the live world.  Runs on the game thread, so the world
	 * stands still while it does.
	 * @param player the player, whose state the database does not keep
	 * @return the world, as XML
	 */
	public static String capture(final MOB player)
	{
		written = new IdentityHashMap<Physical, Integer>();
		synchronized(partlyKept)
		{
			partlyKept.clear();
		}
		try
		{
			return captureWorld(player);
		}
		finally
		{
			written = null;
		}
	}

	/**
	 * The mobs, players and items written to the save being made, each by
	 * the number it is written with (SID), so that who rides what can be
	 * written after them.
	 */
	private static Map<Physical, Integer>	written	= null;

	/**
	 * Returns the number a mob, player or item is written to the save with.
	 * @param P the mob, player or item
	 * @return its number
	 */
	private static int sid(final Physical P)
	{
		final Map<Physical, Integer> w = written;
		if(w == null)
			return 0;
		Integer n = w.get(P);
		if(n == null)
		{
			n = Integer.valueOf(w.size() + 1);
			w.put(P, n);
		}
		return n.intValue();
	}

	/**
	 * Writes who rides what: each rider and the thing it rides, by the
	 * numbers they were written with, for those of both that were written.
	 * @return the RIDES tag
	 */
	private static String ridesXML()
	{
		final StringBuilder str = new StringBuilder("<RIDES>\n");
		final Map<Physical, Integer> w = written;
		for(final Map.Entry<Physical, Integer> e : new ArrayList<Map.Entry<Physical, Integer>>(w.entrySet()))
		{
			if(!(e.getKey() instanceof Rider))
				continue;
			final Rideable ridden = ((Rider)e.getKey()).riding();
			final Integer on = (ridden == null) ? null : w.get(ridden);
			if(on != null)
				str.append("<RIDE R=\"").append(e.getValue()).append("\" ON=\"").append(on).append("\" />\n");
		}
		return str.append("</RIDES>\n").toString();
	}

	private static String captureWorld(final MOB player)
	{
		final XMLLibrary xml = CMLib.xml();
		final StringBuilder str = new StringBuilder("<WORLD VERSION=\"" + VERSION + "\">\n");
		if(player != null)
		{
			str.append(playerXML(player));
			str.append("<PARTY>\n");
			for(final MOB M : Party.othersInWorld(player))
				str.append(playerXML(M));
			str.append("</PARTY>\n");
		}
		final Set<Item> owned = ownedByCharacters();
		str.append(shapesXML());
		str.append("<ROOMS>\n");
		for(final Enumeration<Room> r = CMLib.map().rooms(); r.hasMoreElements();)
		{
			final Room R = r.nextElement();
			if((R == null) || R.amDestroyed())
				continue;
			final String roomID = CMLib.map().getExtendedRoomID(R);
			if(roomID.length() == 0)
				continue;
			// a vessel's rooms are made with it, and filled from here
			str.append((R.getArea() instanceof Boardable) ? "<ROOM SHIP=\"1\">" : "<ROOM>");
			str.append(xml.convertXMLtoTag("ID", xml.parseOutAngleBrackets(roomID)));
			str.append(effectsXML(R));
			str.append(statesXML(R));
			for(int d = 0; d < Directions.NUM_DIRECTIONS(); d++)
			{
				final Exit E = R.getRawExit(d);
				if(E == null)
					continue;
				final String effects = effectsXML(E);
				if(E.hasADoor() || (effects.length() > 0))
				{
					str.append("<DOOR DIR=\"").append(d)
					   .append("\" OPEN=\"").append(E.isOpen())
					   .append("\" LOCKED=\"").append(E.isLocked()).append("\">")
					   .append(effects).append("</DOOR>");
				}
			}
			for(int m = 0; m < R.numInhabitants(); m++)
			{
				final MOB M = R.fetchInhabitant(m);
				if(isSavedHere(M))
					str.append(mobXML(M, "MOB"));
			}
			str.append(itemsXML(roomItems(R, owned)));
			str.append("</ROOM>\n");
		}
		str.append("</ROOMS>\n");
		// what is in space and in no room: where it is, its speed and course
		// are in its text; what characters own comes back with them
		str.append("<SPACE>\n");
		for(final Item I : WorldFiles.spaceItems())
			str.append("<SOBJ>").append(itemsXML(new XVector<Item>(I))).append("</SOBJ>\n");
		str.append("</SPACE>\n");
		str.append("<DEAD>\n");
		for(final MOB M : deadMobs())
		{
			str.append("<DEADMOB LEFT=\"").append(M.phyStats().rejuv()).append("\">");
			str.append(mobXML(M, "MOB"));
			str.append("</DEADMOB>\n");
		}
		str.append("</DEAD>\n");
		str.append("<TAKEN>\n");
		for(final ItemTicker T : takenRejuvItems())
		{
			final Item I = (Item)T.affecting();
			str.append("<TAKENITEM>").append(xml.convertXMLtoTag("ROOM", xml.parseOutAngleBrackets(CMLib.map().getExtendedRoomID(T.properLocation()))));
			str.append(itemsXML(new XVector<Item>(I)));
			str.append("</TAKENITEM>\n");
		}
		str.append("</TAKEN>\n");
		str.append("<WEATHER>\n");
		for(final Enumeration<Area> a = CMLib.map().areas(); a.hasMoreElements();)
		{
			final Area A = a.nextElement();
			final Climate C = A.getClimateObj();
			if(C != null)
			{
				str.append("<AREA NOW=\"").append(C.weatherType(null))
				   .append("\" NEXT=\"").append(C.nextWeatherType(null))
				   .append("\" HOLD=\"").append(C.heldWeather()) // W12: and how long it is still held
				   .append("\" LEFT=\"").append(Math.max(0, C.heldUntil() - GameTime.now())).append("\">")
				   .append(xml.convertXMLtoTag("NAME", xml.parseOutAngleBrackets(A.Name())))
				   .append("</AREA>\n");
			}
		}
		str.append("</WEATHER>\n");
		str.append("<AREAS>\n");
		for(final Enumeration<Area> a = CMLib.map().areas(); a.hasMoreElements();)
		{
			final Area A = a.nextElement();
			final String states = (A == null) ? "" : statesXML(A);
			if(states.length() > 0)
				str.append("<AREA>").append(xml.convertXMLtoTag("NAME", xml.parseOutAngleBrackets(A.Name()))).append(states).append("</AREA>\n");
		}
		str.append("</AREAS>\n");
		str.append(vesselsXML());
		str.append(scriptVarsXML());
		str.append(JSStore.toXML()); // JS7: cm.store
		str.append(questsXML(player));
		str.append(QuestRecords.toXML()); // Q4: each character's quest records
		str.append(com.planet_ink.coffee_mud.core.Yields.toXML()); // CR14: gathered-out rooms
		str.append(ridesXML());
		str.append("</WORLD>\n");
		return str.toString();
	}

	/**
	 * Writes down a character of the player's in the world: where they are,
	 * who of the player's characters they follow, their hit points, mana and
	 * movement, timed effects, what they carry and their followers.
	 * @param player the character
	 * @return the PLAYER tag
	 */
	private static String playerXML(final MOB player)
	{
		final XMLLibrary xml = CMLib.xml();
		final StringBuilder str = new StringBuilder("<PLAYER SID=\"" + sid(player) + "\">");
		str.append(xml.convertXMLtoTag("NAME", xml.parseOutAngleBrackets(player.Name())));
		str.append(xml.convertXMLtoTag("ROOM", xml.parseOutAngleBrackets(CMLib.map().getExtendedRoomID(player.location()))));
		final MOB leader = player.amFollowing();
		if((leader != null) && leader.isPlayer())
			str.append(xml.convertXMLtoTag("LEADER", xml.parseOutAngleBrackets(leader.Name())));
		str.append(xml.convertXMLtoTag("HP", player.curState().getHitPoints()));
		str.append(xml.convertXMLtoTag("MANA", player.curState().getMana()));
		str.append(xml.convertXMLtoTag("MOVE", player.curState().getMovement()));
		str.append(effectsXML(player));
		str.append(limitsXML(player));
		final List<Item> inventory = new ArrayList<Item>();
		for(int i = 0; i < player.numItems(); i++)
		{
			final Item I = player.getItem(i);
			if((I != null) && (!I.amDestroyed()))
				inventory.add(I);
		}
		str.append("<INVENTORY>").append(itemsXML(inventory)).append("</INVENTORY>");
		str.append("<FOLLOWERS>");
		final List<MOB> followers = followersOf(player);
		for(final MOB F : followers)
		{
			str.append("<FOLLOWER LEADER=\"").append(followers.indexOf(F.amFollowing())).append("\">");
			str.append(xml.convertXMLtoTag("ROOM", xml.parseOutAngleBrackets(CMLib.map().getExtendedRoomID(F.location()))));
			str.append(mobXML(F, "MOB"));
			str.append("</FOLLOWER>");
		}
		str.append("</FOLLOWERS>");
		str.append("</PLAYER>\n");
		return str.toString();
	}

	/**
	 * Makes the world as it was saved: empties every room of the mobs and
	 * items the boot put there, then puts back what the save holds.  Runs on
	 * the game thread, before the player logs in.
	 * @param worldXML the saved world, as written by {@link #capture(MOB)}
	 */
	public static void apply(final String worldXML)
	{
		final List<XMLTag> all = CMLib.xml().parseAllXML(worldXML);
		if((all == null) || (all.size() == 0) || (!all.get(0).tag().equalsIgnoreCase("WORLD")))
		{
			Log.errOut("WorldState", "The saved world could not be read.");
			return;
		}
		final XMLTag world = all.get(0);
		restored.clear();
		laterRides.clear();
		laterRooms.clear();
		laterVessels.clear();
		remadeAreas.clear();
		remadeLasting.clear();
		clearWorld();
		final XMLTag shapes = world.getPieceFromPieces("ROOMSHAPES");
		if(shapes != null)
			putShapes(shapes);
		final XMLTag space = world.getPieceFromPieces("SPACE");
		if(space != null)
			putSpace(space); // before the rooms, so that a ship's are there to fill
		final XMLTag rooms = world.getPieceFromPieces("ROOMS");
		if(rooms != null)
		{
			for(final XMLTag roomTag : rooms.contents())
			{
				final Room R = getRoom(roomTag.getValFromPieces("ID"));
				if((R == null) && "1".equals(roomTag.getParmValue("SHIP")))
				{
					laterRooms.add(roomTag); // its vessel is not back yet
					continue;
				}
				if(R == null)
				{
					int mobs = 0, items = 0;
					for(final XMLTag piece : roomTag.contents())
					{
						if(piece.tag().equalsIgnoreCase("MOB"))
							mobs++;
						else
						if(piece.tag().equalsIgnoreCase("ITEM"))
							items++;
					}
					if((mobs > 0) || (items > 0))
						SaveGame.noteWorldChange(CMLib.lang().L("Room @x1 is gone, with @x2 mobs and @x3 items saved there.",
							CMLib.xml().restoreAngleBrackets(roomTag.getValFromPieces("ID")), "" + mobs, "" + items));
					continue;
				}
				if(isFresh(R))
					continue; // as the world has it now
				fillRoom(R, roomTag);
			}
			putBackLaterRooms(false); // a vessel carried in another
		}
		final XMLTag vessels = world.getPieceFromPieces("VESSELS");
		if(vessels != null)
		{
			for(final XMLTag v : vessels.contents())
			{
				final XMLTag travel = v.getPieceFromPieces("TRAVEL");
				if(travel != null)
					laterVessels.add(new Pair<String, String>(CMLib.xml().restoreAngleBrackets(v.getValFromPieces("AREA")), travel.toString()));
			}
		}
		final XMLTag dead = world.getPieceFromPieces("DEAD");
		if(dead != null)
		{
			for(final XMLTag deadTag : dead.contents())
			{
				final XMLTag mobTag = deadTag.getPieceFromPieces("MOB");
				if((mobTag != null) && (!isFresh(getRoom(mobTag.getValFromPieces("START")))))
					bringBackDead(mobTag, CMath.s_int(deadTag.getParmValue("LEFT")));
			}
		}
		final XMLTag taken = world.getPieceFromPieces("TAKEN");
		if(taken != null)
		{
			for(final XMLTag takenTag : taken.contents())
			{
				final Room R = getRoom(takenTag.getValFromPieces("ROOM"));
				if((R == null) || isFresh(R))
					continue;
				for(final Item I : unpackItems(takenTag).items)
				{
					// armed in its room, then taken away again, so that it comes back
					R.addItem(I);
					armRejuv(I, R);
					R.delItem(I);
				}
			}
		}
		final XMLTag vars = world.getPieceFromPieces("SCRIPTVARS");
		if(vars != null)
			setScriptVars(vars);
		QuestRecords.fromXML(world.getPieceFromPieces("QUESTRECORDS")); // Q4
		com.planet_ink.coffee_mud.core.Yields.fromXML(world.getPieceFromPieces("YIELDS")); // CR14
		final XMLTag quests = world.getPieceFromPieces("QUESTS");
		if(quests != null)
		{
			setQuests(quests);
			if(vars != null)
				setScriptVars(vars); // a quest started again may have reset its own
		}
		final XMLTag rides = world.getPieceFromPieces("RIDES");
		if(rides != null)
		{
			for(final XMLTag ride : rides.contents())
				laterRides.add(new Pair<String, String>(ride.getParmValue("R"), ride.getParmValue("ON")));
			putBackRides(); // those among the world's mobs and items; the players' wait for them
		}
		player = world.getPieceFromPieces("PLAYER");
		final XMLTag partyTag = world.getPieceFromPieces("PARTY");
		party = (partyTag == null) ? null : new ArrayList<XMLTag>(partyTag.contents());
		final XMLTag areas = world.getPieceFromPieces("AREAS");
		if(areas != null)
		{
			for(final XMLTag areaTag : areas.contents())
				putStates(CMLib.map().getArea(CMLib.xml().restoreAngleBrackets(areaTag.getValFromPieces("NAME"))), areaTag);
		}
		final XMLTag weather = world.getPieceFromPieces("WEATHER");
		if(weather != null)
		{
			for(final XMLTag areaTag : weather.contents())
			{
				final Area A = CMLib.map().getArea(CMLib.xml().restoreAngleBrackets(areaTag.getValFromPieces("NAME")));
				if((A != null) && (A.getClimateObj() != null))
				{
					A.getClimateObj().setCurrentWeatherType(CMath.s_int(areaTag.getParmValue("NOW")));
					A.getClimateObj().setNextWeatherType(CMath.s_int(areaTag.getParmValue("NEXT")));
					final int hold = CMath.s_int(areaTag.getParmValue("HOLD"), -1); // W12
					final long left = CMath.s_long(areaTag.getParmValue("LEFT"));
					A.getClimateObj().holdWeather(((hold >= 0) && (left > 0)) ? hold : -1, GameTime.now() + left);
				}
			}
		}
		JSStore.fromXML(world.getPieceFromPieces("JSSTATE")); // JS7: before onRestore
	}

	/**
	 * Writes a character's entry limits (InstanceArea's LIMIT, such as one run
	 * a day): how many times each was entered and the game time left before
	 * the count starts again.  They are kept in the character's class
	 * variables, which the character's tables do not hold.  The recent
	 * visits of each planar travel ability (which make a quick return
	 * harder) go with them.
	 * @param player the character
	 * @return the LIMITS tag, or ""
	 */
	@SuppressWarnings("unchecked")
	private static String limitsXML(final MOB player)
	{
		final PlayerStats pStats = player.playerStats();
		final CharClass stdC = CMClass.getCharClass("StdCharClass");
		if((pStats == null) || (stdC == null))
			return "";
		final StringBuilder str = new StringBuilder();
		for(final Map.Entry<String, Object> e : new ArrayList<Map.Entry<String, Object>>(pStats.getClassVariableMap(stdC).entrySet()))
		{
			if((e.getValue() instanceof Pair)
			&& (((Pair<Object, Object>)e.getValue()).first instanceof int[])
			&& (((Pair<Object, Object>)e.getValue()).second instanceof long[]))
			{
				final Pair<int[], long[]> limit = (Pair<int[], long[]>)e.getValue();
				final long left = limit.second[0] - GameTime.now();
				if(left > 0)
					str.append("<LIMIT N=\"").append(limit.first[0]).append("\" LEFT=\"").append(left).append("\">")
					   .append(CMLib.xml().parseOutAngleBrackets(e.getKey())).append("</LIMIT>");
			}
		}
		for(final Enumeration<Ability> a = player.abilities(); a.hasMoreElements();)
		{
			final Ability A = a.nextElement();
			if(A instanceof PlanarAbility)
			{
				final String visits = A.getStat("VISITS");
				if(visits.length() > 0)
					str.append("<VISITS ID=\"").append(A.ID()).append("\">").append(visits).append("</VISITS>");
			}
		}
		return (str.length() == 0) ? "" : ("<LIMITS>" + str.toString() + "</LIMITS>");
	}

	/**
	 * Puts back a character's entry limits and planar visits, as
	 * {@link #limitsXML(MOB)} wrote them.
	 * @param mob the character
	 * @param limits the LIMITS tag, or null
	 */
	private static void putBackLimits(final MOB mob, final XMLTag limits)
	{
		final PlayerStats pStats = mob.playerStats();
		final CharClass stdC = CMClass.getCharClass("StdCharClass");
		if((limits == null) || (pStats == null) || (stdC == null))
			return;
		final Map<String, Object> map = pStats.getClassVariableMap(stdC);
		for(final XMLTag tag : limits.contents())
		{
			if(tag.tag().equalsIgnoreCase("VISITS"))
			{
				final Ability A = mob.fetchAbility(tag.getParmValue("ID"));
				if(A instanceof PlanarAbility)
					A.setStat("VISITS", tag.value());
				continue;
			}
			map.put(CMLib.xml().restoreAngleBrackets(tag.value()),
				new Pair<int[], long[]>(new int[] { CMath.s_int(tag.getParmValue("N")) }, new long[] { GameTime.now() + CMath.s_long(tag.getParmValue("LEFT")) }));
		}
	}

	/**
	 * Writes what a thing's behaviours and scripts have built up while
	 * running (their STATE): a BSTATE for each behaviour, by its ID and its
	 * place among those of that ID, and an SSTATE for each script, by its
	 * text.
	 * @param P the mob, item, room or area
	 * @return the STATES tag, or "" if there is nothing to keep
	 */
	private static String statesXML(final PhysicalAgent P)
	{
		final StringBuilder str = new StringBuilder();
		final Map<String, Integer> seen = new HashMap<String, Integer>();
		for(final Enumeration<Behavior> b = P.behaviors(); b.hasMoreElements();)
		{
			final Behavior B = b.nextElement();
			if(B == null)
				continue;
			final Integer n = seen.containsKey(B.ID()) ? Integer.valueOf(seen.get(B.ID()).intValue() + 1) : Integer.valueOf(0);
			seen.put(B.ID(), n);
			String state;
			try
			{
				state = B.getStat("STATE");
			}
			catch(final Exception e)
			{
				state = "";
			}
			if((state != null) && (state.length() > 0))
				str.append("<BSTATE ID=\"").append(B.ID()).append("\" N=\"").append(n).append("\">").append(state).append("</BSTATE>");
		}
		for(final Enumeration<ScriptingEngine> e = P.scripts(); e.hasMoreElements();)
		{
			final ScriptingEngine S = e.nextElement();
			if(S == null)
				continue;
			final String state = S.getStateXML();
			if(state.length() > 0)
				str.append("<SSTATE KEY=\"").append(scriptKey(S)).append("\">").append(state).append("</SSTATE>");
		}
		return (str.length() == 0) ? "" : ("<STATES>" + str.toString() + "</STATES>");
	}

	private static String scriptKey(final ScriptingEngine S)
	{
		return Integer.toHexString(S.getScript().hashCode());
	}

	/**
	 * Puts back what {@link #statesXML(PhysicalAgent)} wrote, on the
	 * behaviours and scripts the thing has again after the restore.
	 * @param P the mob, item, room or area
	 * @param holder the tag holding its STATES
	 */
	private static void putStates(final PhysicalAgent P, final XMLTag holder)
	{
		final XMLTag states = (holder == null) ? null : holder.getPieceFromPieces("STATES");
		if((states == null) || (P == null))
			return;
		for(final XMLTag tag : states.contents())
		{
			if(tag.tag().equalsIgnoreCase("BSTATE"))
			{
				final String ID = tag.getParmValue("ID");
				int n = CMath.s_int(tag.getParmValue("N"));
				for(final Enumeration<Behavior> b = P.behaviors(); b.hasMoreElements();)
				{
					final Behavior B = b.nextElement();
					if((B != null) && B.ID().equals(ID) && ((n--) == 0))
					{
						try
						{
							B.setStat("STATE", tag.value());
						}
						catch(final Exception e)
						{
							Log.errOut("WorldState", "The state of " + ID + " on " + P.Name() + " could not be put back: " + e.getMessage());
						}
						break;
					}
				}
			}
			else
			if(tag.tag().equalsIgnoreCase("SSTATE"))
			{
				final String key = tag.getParmValue("KEY");
				for(final Enumeration<ScriptingEngine> e = P.scripts(); e.hasMoreElements();)
				{
					final ScriptingEngine S = e.nextElement();
					if((S != null) && scriptKey(S).equals(key))
					{
						S.setStateXML(tag.value());
						break;
					}
				}
			}
		}
	}

	/**
	 * Returns the number a mob, character or item is written to the save
	 * being made with, giving it one if it has none yet, for a skill's
	 * ACTIVITY that names a thing written elsewhere in the save.  Things the
	 * save does not write then have a number nothing is written with.
	 * @param P the mob, character or item
	 * @return its number, or 0 if no save is being made
	 */
	public static int sidFor(final Physical P)
	{
		return (P == null) ? 0 : sid(P);
	}

	/**
	 * Returns the number a mob, character or item has in the save being
	 * written, for a behaviour's STATE that names things written elsewhere
	 * in the save, such as a conquest's clan items.
	 * @param P the mob, character or item
	 * @return its number, or 0 if it is not in the save
	 */
	public static int sidOf(final Physical P)
	{
		final Map<Physical, Integer> w = written;
		final Integer n = ((w == null) || (P == null)) ? null : w.get(P);
		return (n == null) ? 0 : n.intValue();
	}

	/**
	 * Returns what was made from the save being put back, by its number, for
	 * a behaviour's STATE.  Areas' states are put back after the rooms, so
	 * the rooms' mobs and items are there.
	 * @param sid the number
	 * @return the mob, character or item, or null
	 */
	public static Physical restoredThing(final String sid)
	{
		return (sid == null) ? null : restored.get(sid);
	}

	/**
	 * Writes mobs and items as a save does, for a behaviour that keeps them
	 * apart from the world, such as MOBEater's stomach, in its STATE.
	 * @param mobs the mobs
	 * @param items the items
	 * @return the XML
	 */
	public static String thingsXML(final List<MOB> mobs, final List<Item> items)
	{
		final StringBuilder str = new StringBuilder("<THINGS>");
		for(final MOB M : mobs)
			str.append(mobXML(M, "MOB"));
		str.append(itemsXML(items));
		return str.append("</THINGS>").toString();
	}

	/**
	 * Makes the mobs and items {@link #thingsXML(List, List)} wrote, in a room.
	 * @param tags the parsed XML holding the THINGS tag
	 * @param R the room
	 */
	public static void putThings(final List<XMLTag> tags, final Room R)
	{
		XMLTag things = null;
		for(final XMLTag tag : tags)
		{
			if(tag.tag().equalsIgnoreCase("THINGS"))
				things = tag;
		}
		if((things == null) || (R == null))
			return;
		for(final XMLTag piece : things.contents())
		{
			if(piece.tag().equalsIgnoreCase("MOB"))
				bringBack(piece, R);
		}
		for(final Item I : unpackItems(things).items)
			R.addItem(I);
		R.recoverRoomStats();
	}

	/**
	 * Returns what a save would not keep of the world as it is: mobs that
	 * cannot be saved (such as summoned ones without a leader), with or
	 * without scripts, and things in space.
	 * @param player the player
	 * @return one line for each
	 */
	public static List<String> notKept(final MOB player)
	{
		final List<String> lines = new ArrayList<String>();
		final Set<MOB> followers = new HashSet<MOB>((player == null) ? new ArrayList<MOB>() : followersOf(player));
		for(final Enumeration<Room> r = CMLib.map().rooms(); r.hasMoreElements();)
		{
			final Room R = r.nextElement();
			if((R == null) || R.amDestroyed())
				continue;
			final String where = CMStrings.removeColors(R.displayText(player)) + " (" + CMLib.map().getExtendedRoomID(R) + ")";
			for(int m = 0; m < R.numInhabitants(); m++)
			{
				final MOB M = R.fetchInhabitant(m);
				if((M == null) || (!M.isMonster()) || followers.contains(M) || isQuestObject(M) || isSavedHere(M))
					continue;
				final MOB leader = M.getGroupLeader();
				if((leader != null) && (leader != M) && (!leader.isMonster()))
					continue; // follows a character, so saved with them
				final boolean scripted = M.scripts().hasMoreElements() || (M.fetchBehavior("Scriptable") != null);
				lines.add(CMLib.lang().L("@x1 in @x2 cannot be saved@x3, so a restore will not have it.",
					M.Name(), where, scripted ? CMLib.lang().L(", nor its scripts' state") : ""));
			}
		}
		final int inSpace = CMLib.space().numSpaceObjects();
		if(inSpace > 0)
			lines.add(CMLib.lang().L("@x1 things in space (ships, stations, planets) are not saved where they are.", "" + inSpace));
		return lines;
	}

	/**
	 * Returns whether a mob can be saved, leaving aside the player's
	 * characters riding it: a mount counts as unsavable while anyone on it
	 * is, and the player's characters are, as room contents, though the save
	 * keeps them and their ride.
	 * @param M the mob
	 * @return true if it can be saved
	 */
	private static boolean savableBesidesPlayers(final MOB M)
	{
		if(M.isSavable())
			return true;
		if(!(M instanceof Rideable))
			return false;
		final Rideable mount = (Rideable)M;
		final List<Rider> players = new ArrayList<Rider>();
		for(final Enumeration<Rider> r = mount.riders(); r.hasMoreElements();)
		{
			final Rider rider = r.nextElement();
			if((rider instanceof MOB) && (!((MOB)rider).isMonster()))
				players.add(rider);
		}
		if(players.size() == 0)
			return false;
		// on the game thread: asked with them off, then put back on
		for(final Rider rider : players)
			mount.delRider(rider);
		try
		{
			return M.isSavable();
		}
		finally
		{
			for(final Rider rider : players)
				mount.addRider(rider);
		}
	}

	/**
	 * Returns whether a mob in a room is saved with the world: one that can be
	 * saved, and is neither a player nor following one.
	 * @param M the mob
	 * @return true if saved here
	 */
	private static boolean isSavedHere(final MOB M)
	{
		if((M == null) || (!M.isMonster()) || M.amDestroyed() || isQuestObject(M))
			return false;
		// a vessel makes all aboard unsavable, since its own text holds them;
		// the save keeps them in its rooms instead, as they are now
		final boolean aboard = (M.location() != null) && (M.location().getArea() instanceof Boardable);
		if(aboard ? CMath.bset(M.basePhyStats().disposition(), PhyStats.IS_UNSAVABLE) : (!savableBesidesPlayers(M)))
			return false;
		final MOB leader = M.getGroupLeader();
		return (leader == null) || leader.isMonster();
	}

	/**
	 * Returns the items in a room that are saved with the world: all but the
	 * corpses of players, which are saved with the player.
	 * @param R the room
	 * @return the items
	 */
	private static List<Item> roomItems(final Room R, final Set<Item> owned)
	{
		final List<Item> items = new ArrayList<Item>();
		for(int i = 0; i < R.numItems(); i++)
		{
			final Item I = R.getItem(i);
			if((I == null) || I.amDestroyed())
				continue;
			if(owned.contains(I) || owned.contains(I.ultimateContainer(null)))
				continue; // the character's or clan's own tables keep it, as with a vessel bought
			if((I instanceof DeadBody) && ((DeadBody)I).isPlayerCorpse())
				continue;
			items.add(I);
		}
		// a player corpse's contents go with it
		for(final Iterator<Item> i = items.iterator(); i.hasNext();)
		{
			final Item I = i.next();
			if((I.ultimateContainer(null) instanceof DeadBody) && ((DeadBody)I.ultimateContainer(null)).isPlayerCorpse())
				i.remove();
			else
			if(isQuestObject(I) || isQuestObject(I.ultimateContainer(null)))
				i.remove();
		}
		return items;
	}

	/**
	 * Returns the items out in the world that a character's or a clan's own
	 * tables keep (their vessels, titles' properties and the like), which
	 * come back with them and so are not saved with the rooms.
	 * @return the items
	 */
	private static Set<Item> ownedByCharacters()
	{
		final Set<Item> owned = Collections.newSetFromMap(new IdentityHashMap<Item, Boolean>());
		for(final Enumeration<MOB> p = CMLib.players().players(); p.hasMoreElements();)
		{
			final MOB M = p.nextElement();
			if((M != null) && (M.playerStats() != null))
			{
				for(final Enumeration<Item> i = M.playerStats().getExtItems().items(); i.hasMoreElements();)
					owned.add(i.nextElement());
			}
		}
		for(final Enumeration<Clan> c = CMLib.clans().clans(); c.hasMoreElements();)
		{
			final Clan C = c.nextElement();
			if(C != null)
			{
				for(final Enumeration<Item> i = C.getExtItems().items(); i.hasMoreElements();)
					owned.add(i.nextElement());
			}
		}
		return owned;
	}

	/**
	 * Writes each vessel's travel (anchor, course, facing, the vessel it
	 * follows), by the name of its area, which a vessel keeps.
	 * @return the VESSELS tag
	 */
	private static String vesselsXML()
	{
		final XMLLibrary xml = CMLib.xml();
		final StringBuilder str = new StringBuilder("<VESSELS>\n");
		for(final Enumeration<Boardable> b = CMLib.map().ships(); b.hasMoreElements();)
		{
			final Boardable ship = b.nextElement();
			if((!(ship instanceof NavigableItem)) || ((Item)ship).amDestroyed() || (ship.getArea() == null))
				continue;
			final String travel = ((NavigableItem)ship).getTravelStateXML();
			if(travel.length() > 0)
				str.append("<VESSEL>").append(xml.convertXMLtoTag("AREA", xml.parseOutAngleBrackets(ship.getArea().Name()))).append(travel).append("</VESSEL>\n");
		}
		return str.append("</VESSELS>\n").toString();
	}

	/**
	 * Empties a vessel's room of the mobs and items its vessel was made with,
	 * before the saved ones go in: but not the characters, those following
	 * them, or what the characters' tables keep.
	 * @param R the room
	 */
	private static void clearVesselRoom(final Room R)
	{
		final Set<Item> owned = ownedByCharacters();
		final List<MOB> mobs = new ArrayList<MOB>();
		for(int m = 0; m < R.numInhabitants(); m++)
		{
			final MOB M = R.fetchInhabitant(m);
			if((M == null) || (!M.isMonster()) || isQuestObject(M))
				continue;
			final MOB leader = M.getGroupLeader();
			if((leader == null) || leader.isMonster())
				mobs.add(M);
		}
		for(final MOB M : mobs)
			M.destroy();
		final List<Item> items = new ArrayList<Item>();
		for(int i = 0; i < R.numItems(); i++)
		{
			final Item I = R.getItem(i);
			if((I != null) && (!owned.contains(I)) && (!owned.contains(I.ultimateContainer(null)))
			&& (!isQuestObject(I)) && (!isQuestObject(I.ultimateContainer(null))))
				items.add(I);
		}
		for(final Item I : items)
			I.destroy();
	}

	/**
	 * Fills a room from its saved ROOM tag: doors, mobs, effects, states and
	 * items.  A vessel's room is emptied first of what its vessel was made
	 * with.
	 * @param R the room
	 * @param roomTag its tag
	 */
	private static void fillRoom(final Room R, final XMLTag roomTag)
	{
		// a vessel's room has what the vessel was made with; an instance's,
		// what it copied from its parent when it was asked for
		if((R.getArea() instanceof Boardable) || remadeAreas.contains(R.getArea()))
			clearVesselRoom(R);
		for(final XMLTag piece : roomTag.contents())
		{
			if(piece.tag().equalsIgnoreCase("DOOR"))
				setDoor(R, piece);
			else
			if(piece.tag().equalsIgnoreCase("MOB"))
				bringBack(piece, R);
		}
		laterEffects(R, roomTag);
		putStates(R, roomTag);
		final Unpacked items = unpackItems(roomTag);
		for(final Item I : items.items)
		{
			R.addItem(I);
			CMLib.map().registerWorldObjectLoaded(R.getArea(), R, I);
		}
		for(final Item I : items.items)
		{
			if((I.container() == null) && items.respawning.contains(I))
				armRejuv(I, R);
		}
		R.recoverRoomStats();
	}

	/**
	 * Fills the rooms of vessels that were not back when the rest were: one
	 * carried in another, or one a character owns, which comes back with
	 * them.
	 * @param last true when nothing more will come back, to report the rest
	 */
	private static void putBackLaterRooms(final boolean last)
	{
		for(final Iterator<XMLTag> i = laterRooms.iterator(); i.hasNext();)
		{
			final XMLTag roomTag = i.next();
			final Room R = getRoom(roomTag.getValFromPieces("ID"));
			if(R != null)
			{
				i.remove();
				fillRoom(R, roomTag);
			}
		}
		if(last)
		{
			for(final XMLTag roomTag : laterRooms)
			{
				SaveGame.noteWorldChange(CMLib.lang().L("The vessel room @x1 is gone, with what was saved there.",
					CMLib.xml().restoreAngleBrackets(roomTag.getValFromPieces("ID"))));
			}
			laterRooms.clear();
		}
	}

	/**
	 * Puts back each vessel's travel, once every vessel is back.
	 */
	private static void putBackVessels()
	{
		for(final Pair<String, String> v : laterVessels)
		{
			for(final Enumeration<Boardable> b = CMLib.map().ships(); b.hasMoreElements();)
			{
				final Boardable ship = b.nextElement();
				if((ship instanceof NavigableItem) && (ship.getArea() != null) && ship.getArea().Name().equals(v.first))
				{
					((NavigableItem)ship).setTravelStateXML(v.second);
					break;
				}
			}
		}
		laterVessels.clear();
	}

	/**
	 * Returns the dead mobs that are waiting to come back to their rooms.
	 * @return the mobs
	 */
	private static List<MOB> deadMobs()
	{
		final List<MOB> dead = new ArrayList<MOB>();
		for(final Iterator<TickableGroup> g = CMLib.threads().tickGroups(); g.hasNext();)
		{
			for(final Iterator<TickClient> c = g.next().tickers(); c.hasNext();)
			{
				final TickClient client = c.next();
				if((client.getTickID() == Tickable.TICKID_MOB)
				&& (client.getClientObject() instanceof MOB))
				{
					final MOB M = (MOB)client.getClientObject();
					if(M.amDead()
					&& M.isMonster()
					&& M.isSavable()
					&& (!M.amDestroyed())
					&& (M.getStartRoom() != null)
					&& (M.basePhyStats().rejuv() > 0)
					&& (M.basePhyStats().rejuv() != PhyStats.NO_REJUV)
					&& (!dead.contains(M)))
						dead.add(M);
				}
			}
		}
		return dead;
	}

	/**
	 * Returns the respawn tickers of items that were taken from where they
	 * belong, which will put a new one back there in time.
	 * @return the tickers
	 */
	private static List<ItemTicker> takenRejuvItems()
	{
		final List<ItemTicker> taken = new ArrayList<ItemTicker>();
		for(final Iterator<TickableGroup> g = CMLib.threads().tickGroups(); g.hasNext();)
		{
			for(final Iterator<TickClient> c = g.next().tickers(); c.hasNext();)
			{
				final TickClient client = c.next();
				if((client.getTickID() == Tickable.TICKID_ROOM_ITEM_REJUV)
				&& (client.getClientObject() instanceof ItemTicker))
				{
					final ItemTicker T = (ItemTicker)client.getClientObject();
					final Room R = T.properLocation();
					if((R != null)
					&& (!R.amDestroyed())
					&& (T.affecting() instanceof Item)
					&& (!R.isContent((Item)T.affecting()))
					&& (!taken.contains(T)))
						taken.add(T);
				}
			}
		}
		return taken;
	}

	/**
	 * Writes a mob: what it is, as the database would hold it, then where it
	 * belongs, how hurt it is, and what it carries.
	 * @param M the mob
	 * @param tag the tag to write it in
	 * @return the XML
	 */
	private static String mobXML(final MOB M, final String tag)
	{
		final XMLLibrary xml = CMLib.xml();
		final StringBuilder str = new StringBuilder("<" + tag + " SID=\"" + sid(M) + "\">");
		str.append(xml.convertXMLtoTag("CLASS", CMClass.classID(M)));
		str.append(xml.convertXMLtoTag("LEVEL", M.basePhyStats().level()));
		str.append(xml.convertXMLtoTag("ABILITY", M.basePhyStats().ability()));
		str.append(xml.convertXMLtoTag("REJUV", M.basePhyStats().rejuv()));
		str.append(xml.convertXMLtoTag("TEXT", xml.parseOutAngleBrackets(M.text())));
		if(!M.isGeneric())
		{
			// the built-in classes roll these when made, and their text does not hold them
			str.append("<BASE>");
			str.append(statsXML("PHY", M.basePhyStats()));
			str.append(statsXML("CHAR", M.baseCharStats()));
			str.append(statsXML("STATE", M.baseState()));
			str.append("</BASE>");
		}
		final Room start = M.getStartRoom();
		if(start != null)
			str.append(xml.convertXMLtoTag("START", xml.parseOutAngleBrackets(CMLib.map().getExtendedRoomID(start))));
		str.append(xml.convertXMLtoTag("HP", M.curState().getHitPoints()));
		str.append(xml.convertXMLtoTag("MANA", M.curState().getMana()));
		str.append(xml.convertXMLtoTag("MOVE", M.curState().getMovement()));
		final List<Item> inventory = new ArrayList<Item>();
		for(int i = 0; i < M.numItems(); i++)
		{
			final Item I = M.getItem(i);
			if((I != null) && (!I.amDestroyed()))
				inventory.add(I);
		}
		str.append("<INVENTORY>").append(itemsXML(inventory)).append("</INVENTORY>");
		if(!M.amDead())
			str.append(effectsXML(M));
		str.append(statesXML(M));
		str.append("</" + tag + ">");
		return str.toString();
	}

	/**
	 * Writes a list of items, with which of them each is inside, by its place
	 * in the list.
	 * @param items the items
	 * @return the XML
	 */
	private static String itemsXML(final List<Item> items)
	{
		final XMLLibrary xml = CMLib.xml();
		final StringBuilder str = new StringBuilder();
		for(final Item I : items)
		{
			str.append("<ITEM SID=\"").append(sid(I)).append("\">");
			str.append(xml.convertXMLtoTag("CLASS", CMClass.classID(I)));
			str.append(xml.convertXMLtoTag("USES", I.usesRemaining()));
			str.append(xml.convertXMLtoTag("LEVEL", I.basePhyStats().level()));
			str.append(xml.convertXMLtoTag("ABILITY", I.basePhyStats().ability()));
			str.append(xml.convertXMLtoTag("REJUV", I.basePhyStats().rejuv()));
			str.append(xml.convertXMLtoTag("TEXT", xml.parseOutAngleBrackets(I.text())));
			str.append(xml.convertXMLtoTag("IN", items.indexOf(I.container())));
			str.append(xml.convertXMLtoTag("WORN", I.rawWornCode()));
			if(I.expirationDate() != 0)
				str.append(xml.convertXMLtoTag("EXPIRES", I.expirationDate()));
			if(I.fetchEffect("ItemRejuv") != null)
				str.append(xml.convertXMLtoTag("RESPAWNS", "true"));
			str.append(effectsXML(I));
			str.append(statesXML(I));
			str.append("</ITEM>");
		}
		return str.toString();
	}

	/**
	 * Items made from a save, and which of them had a respawn ticker.
	 */
	private static final class Unpacked
	{
		final List<Item>	items		= new ArrayList<Item>();
		final Set<Item>		respawning	= new HashSet<Item>();
	}

	/**
	 * Makes the items written by {@link #itemsXML(List)}, inside each other
	 * as they were.
	 * @param holder the tag holding the ITEM tags
	 * @return the items
	 */
	private static Unpacked unpackItems(final XMLTag holder)
	{
		final List<XMLTag> tags = new ArrayList<XMLTag>();
		for(final XMLTag piece : holder.contents())
		{
			if(piece.tag().equalsIgnoreCase("ITEM"))
				tags.add(piece);
		}
		final List<Item> items = new ArrayList<Item>();
		for(final XMLTag tag : tags)
		{
			final Item I = CMClass.getItem(tag.getValFromPieces("CLASS"));
			if(I == null)
			{
				Log.errOut("WorldState", "No item class " + tag.getValFromPieces("CLASS"));
				SaveGame.noteWorldChange(CMLib.lang().L("No item class @x1: a saved item is lost.", tag.getValFromPieces("CLASS")));
				items.add(null);
				continue;
			}
			I.basePhyStats().setLevel(tag.getIntFromPieces("LEVEL"));
			I.basePhyStats().setAbility(tag.getIntFromPieces("ABILITY"));
			I.basePhyStats().setRejuv(tag.getIntFromPieces("REJUV"));
			I.setUsesRemaining(tag.getIntFromPieces("USES"));
			I.setMiscText(CMLib.xml().restoreAngleBrackets(tag.getValFromPieces("TEXT")));
			I.setContainer(null);
			I.setRawWornCode(tag.getLongFromPieces("WORN", 0));
			I.setExpirationDate(tag.getLongFromPieces("EXPIRES", 0));
			laterEffects(I, tag);
			noteRestored(I, tag);
			putStates(I, tag);
			items.add(I);
		}
		for(int i = 0; i < tags.size(); i++)
		{
			final Item I = items.get(i);
			final int in = tags.get(i).getIntFromPieces("IN", -1);
			if((I != null) && (in >= 0) && (in < items.size()) && (items.get(in) instanceof Container))
				I.setContainer((Container)items.get(in));
		}
		final Unpacked made = new Unpacked();
		for(int i = 0; i < items.size(); i++)
		{
			final Item I = items.get(i);
			if(I != null)
			{
				if(tags.get(i).getBoolFromPieces("RESPAWNS", false))
					made.respawning.add(I);
				I.recoverPhyStats();
				made.items.add(I);
			}
		}
		return made;
	}

	/**
	 * Gives an item in its room the ticker that puts it back there when it is
	 * taken, as the boot does for the items a builder placed.
	 * @param I the item
	 * @param R its room
	 */
	private static void armRejuv(final Item I, final Room R)
	{
		if((I.phyStats().rejuv() == PhyStats.NO_REJUV) || (I.phyStats().rejuv() <= 0))
			return;
		final ItemTicker T = (ItemTicker)CMClass.getAbility("ItemRejuv");
		if(T != null)
		{
			T.unloadIfNecessary(I);
			T.loadMeUp(I, R);
		}
	}

	/**
	 * Makes a mob from what {@link #mobXML(MOB, String)} wrote, without
	 * bringing it to life.
	 * @param tag the mob's tag
	 * @return the mob, or null if it cannot be made
	 */
	private static MOB makeMob(final XMLTag tag)
	{
		final MOB M = CMClass.getMOB(tag.getValFromPieces("CLASS"));
		if(M == null)
		{
			Log.errOut("WorldState", "No mob class " + tag.getValFromPieces("CLASS"));
			SaveGame.noteWorldChange(CMLib.lang().L("No mob class @x1: a saved mob is lost.", tag.getValFromPieces("CLASS")));
			return null;
		}
		noteRestored(M, tag);
		M.setMiscText(CMLib.xml().restoreAngleBrackets(tag.getValFromPieces("TEXT")));
		M.basePhyStats().setLevel(tag.getIntFromPieces("LEVEL"));
		M.basePhyStats().setAbility(tag.getIntFromPieces("ABILITY"));
		M.basePhyStats().setRejuv(tag.getIntFromPieces("REJUV"));
		final XMLTag base = tag.getPieceFromPieces("BASE");
		if(base != null)
		{
			setStats(M.basePhyStats(), base.getPieceFromPieces("PHY"));
			setStats(M.baseCharStats(), base.getPieceFromPieces("CHAR"));
			setStats(M.baseState(), base.getPieceFromPieces("STATE"));
		}
		final Room start = getRoom(tag.getValFromPieces("START"));
		if(start != null)
			M.setStartRoom(start);
		M.recoverCharStats();
		M.recoverPhyStats();
		M.recoverMaxState();
		return M;
	}

	/**
	 * Puts a saved mob back in a room, alive, with what it carried and the
	 * hurts it had.
	 * @param tag the mob's tag
	 * @param R the room
	 * @return the mob, or null if it could not be made
	 */
	private static MOB bringBack(final XMLTag tag, final Room R)
	{
		final MOB M = makeMob(tag);
		if(M == null)
			return null;
		M.bringToLife(R, true);
		CMLib.map().registerWorldObjectLoaded(R.getArea(), R, M);
		final XMLTag inventory = tag.getPieceFromPieces("INVENTORY");
		if(inventory != null)
		{
			M.delAllItems(true);
			for(final Item I : unpackItems(inventory).items)
				M.addItem(I);
		}
		M.recoverCharStats();
		M.recoverPhyStats();
		M.recoverMaxState();
		M.curState().setHitPoints(Math.min(tag.getIntFromPieces("HP"), M.maxState().getHitPoints()));
		M.curState().setMana(Math.min(tag.getIntFromPieces("MANA"), M.maxState().getMana()));
		M.curState().setMovement(Math.min(tag.getIntFromPieces("MOVE"), M.maxState().getMovement()));
		laterEffects(M, tag);
		// after it comes to life, which makes its behaviours again from its
		// text; the tick that does is held while a restored world is put back
		putStates(M, tag);
		return M;
	}

	/**
	 * Makes a saved dead mob: dead, waiting the ticks it had left to come
	 * back to the room it belongs in.
	 * @param tag the mob's tag
	 * @param ticksLeft ticks until it comes back
	 */
	private static void bringBackDead(final XMLTag tag, final int ticksLeft)
	{
		final MOB M = makeMob(tag);
		if((M == null) || (M.getStartRoom() == null))
		{
			if(M != null)
				M.destroy();
			return;
		}
		M.bringToLife(M.getStartRoom(), true);
		M.killMeDead(false);
		M.phyStats().setRejuv(ticksLeft);
	}

	/**
	 * Opens or closes, locks or unlocks a door as it was saved.
	 * @param R the room
	 * @param tag the door's tag
	 */
	private static void setDoor(final Room R, final XMLTag tag)
	{
		final int dir = CMath.s_int(tag.getParmValue("DIR"));
		if((dir < 0) || (dir >= Directions.NUM_DIRECTIONS()))
			return;
		final Exit E = R.getRawExit(dir);
		if((E == null) || (!E.hasADoor()))
			return;
		final boolean open = CMath.s_bool(tag.getParmValue("OPEN"));
		final boolean locked = CMath.s_bool(tag.getParmValue("LOCKED"));
		if(E.hasADoor())
			E.setDoorsNLocks(E.hasADoor(), open, E.defaultsClosed(), E.hasALock(), locked, E.defaultsLocked());
		laterEffects(E, tag);
	}

	/**
	 * Empties every room of the mobs and items the boot put in it, and ends
	 * any mobs that were dead and waiting, so that only what was saved comes
	 * back.
	 */
	private static void clearWorld()
	{
		for(final MOB M : deadMobs())
		{
			if(!isFresh(M.getStartRoom()))
				M.destroy();
		}
		for(final Enumeration<Room> r = CMLib.map().rooms(); r.hasMoreElements();)
		{
			final Room R = r.nextElement();
			if((R == null) || R.amDestroyed() || isFresh(R))
				continue;
			final List<MOB> mobs = new ArrayList<MOB>();
			for(int m = 0; m < R.numInhabitants(); m++)
			{
				final MOB M = R.fetchInhabitant(m);
				if((M != null) && M.isMonster() && (!isQuestObject(M)))
					mobs.add(M);
			}
			for(final MOB M : mobs)
				M.destroy();
			final List<Item> items = new ArrayList<Item>();
			for(int i = 0; i < R.numItems(); i++)
			{
				final Item I = R.getItem(i);
				if((I != null) && (!isQuestObject(I)) && (!isQuestObject(I.ultimateContainer(null))))
					items.add(I);
			}
			for(final Item I : items)
				I.destroy();
		}
	}

	/**
	 * Returns whether an effect is one with a time left to run, which the
	 * database would drop: not one that is always there, from the content,
	 * a race, a class or a skill, and not a respawn ticker.
	 * @param A the effect
	 * @return true if it is saved here
	 */
	private static boolean isTimedEffect(final Ability A)
	{
		return (A != null)
			&& A.canBeUninvoked()
			&& (!A.isAutoInvoked())
			&& (!(A instanceof ItemTicker))
			&& (CMath.s_int(A.getStat("TICKDOWN")) > 0);
	}

	/**
	 * Writes the timed effects on something, with the ticks each has left
	 * and who caused it.
	 * @param P the thing
	 * @return the XML, or nothing if it has none
	 */
	private static String effectsXML(final Physical P)
	{
		final XMLLibrary xml = CMLib.xml();
		final StringBuilder str = new StringBuilder();
		for(final Enumeration<Ability> a = P.effects(); a.hasMoreElements();)
		{
			final Ability A = a.nextElement();
			if(!isTimedEffect(A))
				continue;
			str.append("<EFFECT>");
			str.append(xml.convertXMLtoTag("ID", A.ID()));
			str.append(xml.convertXMLtoTag("TEXT", xml.parseOutAngleBrackets(A.text())));
			str.append(xml.convertXMLtoTag("TICKS", A.getStat("TICKDOWN")));
			str.append(xml.convertXMLtoTag("PROF", A.proficiency()));
			if(A.invoker() != null)
				str.append(xml.convertXMLtoTag("BY", xml.parseOutAngleBrackets(A.invoker().Name())));
			String activity = "";
			try
			{
				activity = A.getStat("ACTIVITY"); // a common skill under way: a crop, a craft
			}
			catch(final Exception e)
			{
			}
			if((activity != null) && (activity.length() > 0))
			{
				str.append("<ACTIVITY>").append(activity).append("</ACTIVITY>");
				final int s = activity.indexOf("<SKIPPED>");
				final int e = activity.indexOf("</SKIPPED>");
				if((s >= 0) && (e > s) && (P instanceof MOB) && (!((MOB)P).isMonster()))
				{
					synchronized(partlyKept)
					{
						final List<String> parts = new ArrayList<String>();
						for(final String f : CMParms.parseCommas(activity.substring(s + "<SKIPPED>".length(), e), true))
							parts.add(f.substring(f.lastIndexOf('.') + 1));
						partlyKept.add(A.name() + " (" + CMParms.toListString(parts.iterator()) + ")");
					}
				}
			}
			str.append("</EFFECT>");
		}
		return (str.length() > 0) ? ("<EFFECTS>" + str.toString() + "</EFFECTS>") : "";
	}

	/** things restored with timed effects, put on them once the player is in */
	private static final List<Pair<Physical, XMLTag>>	laterEffects	= new ArrayList<Pair<Physical, XMLTag>>();

	/** areas a FRESH= restore takes as they are now: by file or area name */
	private static volatile Set<String>	freshAreas	= null;
	/** true if a FRESH=ALL restore takes every area as it is now */
	private static volatile boolean		freshAll	= false;

	/**
	 * Sets the areas the next apply leaves as the world has them now, rather
	 * than as saved (RESTORE name FRESH=).
	 * @param areas area file names or area names, or null
	 * @param all true for every area, or null
	 */
	public static void setFresh(final Set<String> areas, final Boolean all)
	{
		final Set<String> names = new TreeSet<String>(String.CASE_INSENSITIVE_ORDER);
		if(areas != null)
			names.addAll(areas);
		freshAreas = names;
		freshAll = (all != null) && all.booleanValue();
	}

	/**
	 * Returns whether a room is in an area a FRESH= restore leaves as the
	 * world has it now.
	 * @param R the room
	 * @return true if its contents are not put back from the save
	 */
	private static boolean isFresh(final Room R)
	{
		if(freshAll)
			return true;
		final Set<String> names = freshAreas;
		if((names == null) || (names.size() == 0) || (R == null) || (R.getArea() == null))
			return false;
		final Area A = R.getArea();
		return names.contains(A.Name()) || names.contains(WorldFiles.areaFileFor(A));
	}

	/** what was made from the save, by the number it was written with */
	private static final Map<String, Physical>			restored		= new HashMap<String, Physical>();
	/** who rides what, by number, until both are back */
	private static final List<Pair<String, String>>		laterRides		= new ArrayList<Pair<String, String>>();
	/** the rooms of vessels not back yet, such as the characters' own */
	private static final List<XMLTag>					laterRooms		= new ArrayList<XMLTag>();
	/** each vessel's travel, by its area's name, until every vessel is back */
	private static final List<Pair<String, String>>		laterVessels	= new ArrayList<Pair<String, String>>();

	/**
	 * Notes what was made from a saved tag, by the number it was written
	 * with, so that who rides what can be put back.
	 * @param P what was made
	 * @param tag its tag
	 */
	private static void noteRestored(final Physical P, final XMLTag tag)
	{
		final String sid = tag.getParmValue("SID");
		if((P != null) && (sid != null) && (sid.length() > 0))
			restored.put(sid, P);
	}

	/**
	 * Puts riders back on what they rode, for each ride whose rider and
	 * mount are both back, and in the same place.
	 */
	private static void putBackRides()
	{
		for(final Iterator<Pair<String, String>> i = laterRides.iterator(); i.hasNext();)
		{
			final Pair<String, String> ride = i.next();
			final Physical rider = restored.get(ride.first);
			final Physical mount = restored.get(ride.second);
			if((rider == null) || (mount == null))
				continue;
			i.remove();
			if((!(rider instanceof Rider)) || (!(mount instanceof Rideable)) || rider.amDestroyed() || mount.amDestroyed())
				continue;
			final Room riderR = CMLib.map().roomLocation(rider);
			if((riderR == null) || (riderR != CMLib.map().roomLocation(mount)))
				continue;
			((Rider)rider).setRiding((Rideable)mount);
		}
	}
	/** the saved player's state, put back once they are in */
	private static XMLTag								player			= null;
	/** the saved state of the player's other characters in the world */
	private static List<XMLTag>							party			= null;
	/** whether the restored player's state is being put back */
	private static boolean								finishing		= false;

	/**
	 * Keeps a restored thing's timed effects to put on it once the player is
	 * in the game, since the player may be who caused them.
	 * @param P the thing
	 * @param tag its tag, which may hold EFFECTS
	 */
	private static void laterEffects(final Physical P, final XMLTag tag)
	{
		final XMLTag effects = tag.getPieceFromPieces("EFFECTS");
		if((effects != null) && (effects.contents().size() > 0))
			laterEffects.add(new Pair<Physical, XMLTag>(P, effects));
	}

	/**
	 * Called when the restored player is in the game: puts back their hit
	 * points, mana, movement and timed effects, brings the rest of their
	 * party back into the world where they were, as they were, then puts back
	 * the timed effects on the rest of the world.
	 * @param mob the player
	 */
	public static void finishRestore(final MOB mob)
	{
		// bringing in the party logs each of them in, which comes back here
		if(finishing)
			return;
		finishing = true;
		try
		{
			finishRestoring(mob);
		}
		finally
		{
			finishing = false;
		}
	}

	private static void finishRestoring(final MOB mob)
	{
		final XMLTag playerTag = player;
		player = null;
		final List<XMLTag> partyTags = party;
		party = null;
		final Map<MOB, String> leaders = new HashMap<MOB, String>();
		if((playerTag != null)
		&& (mob.Name().equalsIgnoreCase(CMLib.xml().restoreAngleBrackets(playerTag.getValFromPieces("NAME")))))
		{
			// the character's tables keep a start room for an instance or plane
			final Room savedR = getRoom(playerTag.getValFromPieces("ROOM"));
			if((savedR != null) && (savedR != mob.location()))
				savedR.bringMobHere(mob, true);
			putBackPlayer(mob, playerTag);
			leaders.put(mob, CMLib.xml().restoreAngleBrackets(playerTag.getValFromPieces("LEADER")));
			if(partyTags != null)
			{
				for(final XMLTag tag : partyTags)
				{
					final MOB M = Party.bringIn(mob, CMLib.xml().restoreAngleBrackets(tag.getValFromPieces("NAME")), getRoom(tag.getValFromPieces("ROOM")));
					if(M == null)
						continue;
					putBackPlayer(M, tag);
					leaders.put(M, CMLib.xml().restoreAngleBrackets(tag.getValFromPieces("LEADER")));
				}
			}
			for(final Map.Entry<MOB, String> e : leaders.entrySet())
			{
				final MOB leader = (e.getValue().length() > 0) ? CMLib.players().getPlayer(e.getValue()) : null;
				if((leader != null) && (leader != e.getKey()) && CMLib.flags().isInTheGame(leader, true))
					e.getKey().setFollowing(leader);
			}
		}
		// the characters' own vessels are back with them, so their rooms can be
		// filled, and every vessel's travel put back
		putBackLaterRooms(true);
		putBackVessels();
		// the characters and their followers are back, so their rides can be
		putBackRides();
		laterRides.clear();
		// last, since what the characters carry adds to them
		final List<Pair<Physical, XMLTag>> todo = new ArrayList<Pair<Physical, XMLTag>>(laterEffects);
		laterEffects.clear();
		for(final Pair<Physical, XMLTag> p : todo)
		{
			if(!p.first.amDestroyed())
				putEffects(p.first, p.second, mob);
		}
		restored.clear(); // after the effects, whose activities may name what was restored
		takeBackRemadeAreas();
	}

	/**
	 * Puts back what the database does not keep of one of the player's
	 * characters: their hit points, mana, movement and timed effects, what
	 * they carry as it was, and their followers.
	 * @param mob the character
	 * @param playerTag their PLAYER tag
	 */
	private static void putBackPlayer(final MOB mob, final XMLTag playerTag)
	{
		noteRestored(mob, playerTag);
		putBackLimits(mob, playerTag.getPieceFromPieces("LIMITS"));
		final XMLTag inventory = playerTag.getPieceFromPieces("INVENTORY");
		if(inventory != null)
		{
			// the character's tables hold the same items, but not their timed
			// effects, nor what quests gave that cannot be saved there
			mob.delAllItems(true);
			for(final Item I : unpackItems(inventory).items)
				mob.addItem(I);
		}
		final XMLTag followers = playerTag.getPieceFromPieces("FOLLOWERS");
		if(followers != null)
			bringBackFollowers(mob, followers);
		mob.recoverCharStats();
		mob.recoverPhyStats();
		mob.recoverMaxState();
		mob.curState().setHitPoints(Math.max(1, Math.min(playerTag.getIntFromPieces("HP"), mob.maxState().getHitPoints())));
		mob.curState().setMana(Math.min(playerTag.getIntFromPieces("MANA"), mob.maxState().getMana()));
		mob.curState().setMovement(Math.min(playerTag.getIntFromPieces("MOVE"), mob.maxState().getMovement()));
		final XMLTag effects = playerTag.getPieceFromPieces("EFFECTS");
		if(effects != null)
			putEffects(mob, effects, mob);
	}

	/**
	 * Puts saved timed effects back on something, each with the ticks it had
	 * left.  An effect's cause is the player if they caused it, else a mob of
	 * that name nearby, else the thing itself if it is a mob, else the player.
	 * @param P the thing
	 * @param effects the EFFECTS tag
	 * @param mob the player
	 */
	private static void putEffects(final Physical P, final XMLTag effects, final MOB mob)
	{
		for(final XMLTag tag : effects.contents())
		{
			final Ability A = CMClass.getAbility(tag.getValFromPieces("ID"));
			if(A == null)
				continue;
			final int ticks = tag.getIntFromPieces("TICKS");
			if((ticks <= 0) || (P.fetchEffect(A.ID()) != null))
				continue;
			A.setMiscText(CMLib.xml().restoreAngleBrackets(tag.getValFromPieces("TEXT")));
			A.setProficiency(tag.getIntFromPieces("PROF"));
			A.startTickDown(causeOf(CMLib.xml().restoreAngleBrackets(tag.getValFromPieces("BY")), P, mob), P, ticks);
			A.setStat("TICKDOWN", Integer.toString(ticks));
			final XMLTag activity = tag.getPieceFromPieces("ACTIVITY");
			if(activity != null)
				A.setStat("ACTIVITY", activity.value());
		}
		if(P instanceof MOB)
		{
			((MOB)P).recoverCharStats();
			((MOB)P).recoverMaxState();
		}
		if(P instanceof Room)
			((Room)P).recoverRoomStats();
		else
			P.recoverPhyStats();
	}

	private static MOB causeOf(final String name, final Physical P, final MOB mob)
	{
		if((name.length() == 0) || name.equalsIgnoreCase(mob.Name()))
			return mob;
		final MOB character = CMLib.players().getPlayer(name);
		if((character != null) && CMLib.flags().isInTheGame(character, true))
			return character;
		Room R = null;
		if(P instanceof MOB)
			R = ((MOB)P).location();
		else
		if(P instanceof Room)
			R = (Room)P;
		else
		if((P instanceof Item) && (((Item)P).owner() instanceof Room))
			R = (Room)((Item)P).owner();
		if(R != null)
		{
			final MOB M = R.fetchInhabitant(name);
			if(M != null)
				return M;
		}
		return (P instanceof MOB) ? (MOB)P : mob;
	}

	/**
	 * Writes the variables that scripts share: every variable scope, by the
	 * object each variable belongs to.
	 * @return the XML
	 */
	@SuppressWarnings("unchecked")
	private static String scriptVarsXML()
	{
		final XMLLibrary xml = CMLib.xml();
		final StringBuilder str = new StringBuilder("<SCRIPTVARS>\n");
		for(final Iterator<String> k = Resources.findResourceKeys("VARSCOPE-"); k.hasNext();)
		{
			final String scopeKey = k.next();
			if(!scopeKey.toUpperCase().startsWith("VARSCOPE-"))
				continue;
			final Object o = Resources.getResource(scopeKey);
			if(!(o instanceof Resources))
				continue;
			final Resources scope = (Resources)o;
			for(final Iterator<String> h = scope._findResourceKeys("SCRIPTVAR-"); h.hasNext();)
			{
				final String hostKey = h.next();
				if(!hostKey.toUpperCase().startsWith("SCRIPTVAR-"))
					continue;
				final Object vars = scope._getResource(hostKey);
				if(!(vars instanceof Map))
					continue;
				for(final Map.Entry<String, String> e : new ArrayList<Map.Entry<String, String>>(((Map<String, String>)vars).entrySet()))
				{
					str.append("<VAR>");
					str.append(xml.convertXMLtoTag("SCOPE", xml.parseOutAngleBrackets(scopeKey.substring(9))));
					str.append(xml.convertXMLtoTag("HOST", xml.parseOutAngleBrackets(hostKey.substring(10))));
					str.append(xml.convertXMLtoTag("NAME", xml.parseOutAngleBrackets(e.getKey())));
					str.append(xml.convertXMLtoTag("VALUE", xml.parseOutAngleBrackets(e.getValue())));
					str.append("</VAR>\n");
				}
			}
		}
		return str.append("</SCRIPTVARS>\n").toString();
	}

	/**
	 * Puts back the variables that scripts share.
	 * @param vars the SCRIPTVARS tag
	 */
	@SuppressWarnings("unchecked")
	private static void setScriptVars(final XMLTag vars)
	{
		final XMLLibrary xml = CMLib.xml();
		for(final XMLTag tag : vars.contents())
		{
			final String scopeKey = "VARSCOPE-" + xml.restoreAngleBrackets(tag.getValFromPieces("SCOPE"));
			Resources scope = (Resources)Resources.getResource(scopeKey);
			if(scope == null)
			{
				scope = Resources.newResources();
				Resources.submitResource(scopeKey, scope);
			}
			final String hostKey = "SCRIPTVAR-" + xml.restoreAngleBrackets(tag.getValFromPieces("HOST"));
			Map<String, String> H = (Map<String, String>)scope._getResource(hostKey);
			if(H == null)
			{
				H = Collections.synchronizedMap(new HashMap<String, String>());
				scope._submitResource(hostKey, H);
			}
			H.put(xml.restoreAngleBrackets(tag.getValFromPieces("NAME")), xml.restoreAngleBrackets(tag.getValFromPieces("VALUE")));
		}
	}

	/**
	 * Returns the mobs following a player, and those following them, each
	 * after the one it follows.
	 * @param player the player
	 * @return the followers
	 */
	private static List<MOB> followersOf(final MOB player)
	{
		final List<MOB> all = new ArrayList<MOB>();
		final List<MOB> leaders = new ArrayList<MOB>();
		leaders.add(player);
		for(int l = 0; l < leaders.size(); l++)
		{
			final MOB leader = leaders.get(l);
			for(int f = 0; f < leader.numFollowers(); f++)
			{
				final MOB F = leader.fetchFollower(f);
				if((F != null)
				&& F.isMonster()
				&& (!F.amDestroyed())
				&& (F.location() != null)
				&& (F.amFollowing() == leader)
				&& (!all.contains(F)))
				{
					all.add(F);
					leaders.add(F);
				}
			}
		}
		return all;
	}

	/**
	 * Puts back the restored player's followers as they were saved, in place
	 * of those the character's tables brought back, which lack their hurts,
	 * their timed effects, and any that could not be saved there, such as
	 * summoned ones.
	 * @param mob the player
	 * @param followers the FOLLOWERS tag
	 */
	private static void bringBackFollowers(final MOB mob, final XMLTag followers)
	{
		final List<MOB> loaded = followersOf(mob);
		for(final MOB F : loaded)
		{
			F.setFollowing(null);
			F.destroy();
		}
		final List<MOB> made = new ArrayList<MOB>();
		for(final XMLTag tag : followers.contents())
		{
			final XMLTag mobTag = tag.getPieceFromPieces("MOB");
			Room R = getRoom(tag.getValFromPieces("ROOM"));
			if(R == null)
				R = mob.location();
			final MOB F = ((mobTag == null) || (R == null)) ? null : bringBack(mobTag, R);
			made.add(F);
			if(F != null)
			{
				final int leader = CMath.s_int(tag.getParmValue("LEADER"));
				final MOB L = ((leader >= 0) && (leader < made.size() - 1) && (made.get(leader) != null)) ? made.get(leader) : mob;
				F.setFollowing(L);
			}
		}
	}

	/**
	 * Writes every stat of a set of stats.
	 * @param tag the tag to write them in
	 * @param stats the stats
	 * @return the XML
	 */
	private static String statsXML(final String tag, final Modifiable stats)
	{
		final XMLLibrary xml = CMLib.xml();
		final StringBuilder str = new StringBuilder("<" + tag + ">");
		for(final String code : stats.getStatCodes())
		{
			str.append("<S>").append(xml.convertXMLtoTag("N", code))
			   .append(xml.convertXMLtoTag("V", xml.parseOutAngleBrackets(stats.getStat(code))))
			   .append("</S>");
		}
		return str.append("</" + tag + ">").toString();
	}

	/**
	 * Sets stats written by {@link #statsXML(String, Modifiable)}.
	 * @param stats the stats
	 * @param tag their tag, or null
	 */
	private static void setStats(final Modifiable stats, final XMLTag tag)
	{
		if(tag == null)
			return;
		for(final XMLTag S : tag.contents())
		{
			final String code = S.getValFromPieces("N");
			if(stats.isStat(code))
				stats.setStat(code, CMLib.xml().restoreAngleBrackets(S.getValFromPieces("V")));
		}
	}

	/**
	 * Returns whether something is in a running quest's hands, so that the
	 * quest, not the world, saves and restores it.
	 * @param E the thing
	 * @return true if a quest has it
	 */
	private static boolean isQuestObject(final Environmental E)
	{
		return (E != null) && (CMLib.quests().objectInUse(E) != null);
	}

	/** the quests the world's own files gave this game, by name in upper case */
	private static final Set<String>	contentQuests	= new HashSet<String>();
	private static volatile boolean		contentNoted	= false;

	/** each room the world's files gave, by extended ID: a hash of its shape at boot */
	private static final Map<String, Integer>	contentRooms	= new HashMap<String, Integer>();
	/** every room the world's areas name, loaded at boot or not (a thin area's) */
	private static final Set<String>			contentRoomIDs	= new HashSet<String>();
	/** each area the world's files gave, by name: a hash of its shape at boot */
	private static final Map<String, Integer>	contentAreas	= new HashMap<String, Integer>();
	/** whether the shapes have been noted since the boot */
	private static volatile boolean				shapesNoted		= false;

	/**
	 * Notes the shape of each room and area the world's files gave this game,
	 * once it has booted and before a save is put back: a room's class,
	 * texts, behaviours, lasting effects, scripts and exits; an area's
	 * texts, climate and the like.  A save then keeps the rooms and areas
	 * whose shape play has changed, the rooms made in play, and the rooms
	 * play has destroyed.
	 */
	public static void noteContentShapes()
	{
		synchronized(contentRooms)
		{
			contentRooms.clear();
			contentRoomIDs.clear();
			contentAreas.clear();
			for(final Enumeration<Area> a = CMLib.map().areas(); a.hasMoreElements();)
			{
				final Area A = a.nextElement();
				if((A == null) || (A instanceof Boardable))
					continue;
				contentAreas.put(A.Name(), Integer.valueOf(areaShape(A).hashCode()));
				for(final Enumeration<String> ids = A.getProperRoomnumbers().getRoomIDs(); ids.hasMoreElements();)
					contentRoomIDs.add(ids.nextElement());
				for(final Enumeration<Room> r = A.getProperMap(); r.hasMoreElements();)
				{
					final Room R = r.nextElement();
					if(isShapeKept(R))
						contentRooms.put(CMLib.map().getExtendedRoomID(R), Integer.valueOf(roomShape(R).hashCode()));
				}
			}
			shapesNoted = true;
		}
	}

	/**
	 * Returns whether a room's shape is one a save compares with the world's
	 * files: a room of an area the files gave, not a vessel's, a grid's
	 * cell, or a room with no ID.
	 * @param R the room
	 * @return true if its shape is kept
	 */
	private static boolean isShapeKept(final Room R)
	{
		return (R != null)
			&& (!R.amDestroyed())
			&& (R.getArea() != null)
			&& (!(R.getArea() instanceof Boardable))
			&& (R.getGridParent() == null)
			&& (R.roomID().length() > 0);
	}

	private static String roomShape(final Room R)
	{
		return CMLib.coffeeMaker().getRoomShapeXML(R);
	}

	private static String areaShape(final Area A)
	{
		return CMLib.coffeeMaker().getAreaObjectXML(A, null, new HashSet<CMObject>(), new HashSet<String>(), false);
	}

	/**
	 * Writes the rooms and areas play has changed since the boot (see
	 * {@link #noteContentShapes()}): the shape of each room made in play
	 * (NEWROOM) or changed (CHANGEDROOM), the ID of each room destroyed
	 * (GONEROOM), and the shape of each area changed (CHANGEDAREA).
	 * @return the ROOMSHAPES tag, or "" if the shapes were not noted
	 */
	private static String shapesXML()
	{
		if(!shapesNoted)
			return "";
		final XMLLibrary xml = CMLib.xml();
		final StringBuilder str = new StringBuilder("<ROOMSHAPES>\n");
		synchronized(contentRooms)
		{
			final Set<String> areaNames = contentAreas.keySet();
			for(final Enumeration<Area> a = CMLib.map().areas(); a.hasMoreElements();)
			{
				final Area A = a.nextElement();
				if((A == null) || (A instanceof Boardable) || areaNames.contains(A.Name()))
					continue;
				// made in play: an instance, a plane, a generated area
				str.append("<NEWAREA>").append(xml.convertXMLtoTag("CLASS", A.ID()))
				   .append(xml.convertXMLtoTag("NAME", xml.parseOutAngleBrackets(A.Name())))
				   .append(areaShape(A)).append(effectsXML(A)).append(lastingActivitiesXML(A)).append("</NEWAREA>\n");
			}
			for(final Enumeration<Room> r = CMLib.map().rooms(); r.hasMoreElements();)
			{
				final Room R = r.nextElement();
				if(!isShapeKept(R))
					continue; // a vessel's or a grid's
				if(!areaNames.contains(R.getArea().Name()))
				{
					str.append("<NEWROOM>").append(roomShape(R)).append("</NEWROOM>\n"); // of an area made in play
					continue;
				}
				final String roomID = CMLib.map().getExtendedRoomID(R);
				final Integer was = contentRooms.get(roomID);
				if(was == null)
				{
					if(!contentRoomIDs.contains(R.roomID())) // else a thin area's, loaded since
						str.append("<NEWROOM>").append(roomShape(R)).append("</NEWROOM>\n");
				}
				else
				{
					final String shape = roomShape(R);
					if(shape.hashCode() != was.intValue())
						str.append("<CHANGEDROOM>").append(shape).append("</CHANGEDROOM>\n");
				}
			}
			for(final String roomID : contentRooms.keySet())
			{
				final Room R = CMLib.map().getRoom(roomID);
				if((R == null) || R.amDestroyed())
					str.append("<GONEROOM>").append(xml.parseOutAngleBrackets(roomID)).append("</GONEROOM>\n");
			}
			for(final Map.Entry<String, Integer> e : contentAreas.entrySet())
			{
				final Area A = CMLib.map().getArea(e.getKey());
				if(A == null)
					continue;
				final String shape = areaShape(A);
				if(shape.hashCode() != e.getValue().intValue())
					str.append("<CHANGEDAREA>").append(shape).append("</CHANGEDAREA>\n");
			}
		}
		return str.append("</ROOMSHAPES>\n").toString();
	}

	/**
	 * Puts back the rooms and areas play had changed, as
	 * {@link #shapesXML()} wrote them: destroys the rooms that were
	 * destroyed, makes the rooms that were made, then gives each made or
	 * changed room its saved shape and exits, and each changed area its
	 * saved shape.  Runs before the rooms' contents go back.
	 * @param shapes the ROOMSHAPES tag
	 */
	private static void putShapes(final XMLTag shapes)
	{
		final XMLLibrary xml = CMLib.xml();
		for(final XMLTag tag : shapes.contents())
		{
			if(tag.tag().equalsIgnoreCase("NEWAREA"))
				remakeArea(tag);
		}
		for(final XMLTag tag : shapes.contents())
		{
			if(tag.tag().equalsIgnoreCase("GONEROOM"))
			{
				final Room R = getRoom(tag.value());
				if((R != null) && (!isFresh(R)))
					CMLib.map().obliterateMapRoom(R);
			}
		}
		final List<List<XMLTag>> reshaped = new ArrayList<List<XMLTag>>();
		for(final XMLTag tag : shapes.contents())
		{
			final XMLTag aroom = tag.getPieceFromPieces("AROOM");
			if(aroom == null)
				continue;
			final List<XMLTag> shape = aroom.contents();
			final Area A = CMLib.map().getArea(xml.getValFromPieces(shape, "RAREA"));
			if((A == null) || isFreshArea(A))
				continue;
			if(tag.tag().equalsIgnoreCase("NEWROOM"))
			{
				final Room R = CMLib.map().getRoom(xml.getValFromPieces(shape, "ROOMID"));
				if(R == null)
				{
					final String err = CMLib.coffeeMaker().unpackRoomFromXML(null, shape, false, false);
					if((err != null) && (err.length() > 0))
					{
						SaveGame.noteWorldChange(CMLib.lang().L("A room made in play could not be made again: @x1", err));
						continue;
					}
				}
				reshaped.add(shape);
			}
			else
			if(tag.tag().equalsIgnoreCase("CHANGEDROOM"))
				reshaped.add(shape);
		}
		// each made or changed room as saved, now that every room is there to link to
		for(final List<XMLTag> shape : reshaped)
		{
			final Room R = CMLib.map().getRoom(xml.getValFromPieces(shape, "ROOMID"));
			if(R != null)
				reshapeRoom(R, shape);
		}
		for(final XMLTag tag : shapes.contents())
		{
			if(!tag.tag().equalsIgnoreCase("CHANGEDAREA"))
				continue;
			final XMLTag areaTag = tag.getPieceFromPieces("AREA");
			if(areaTag == null)
				continue;
			final Area A = CMLib.map().getArea(xml.getValFromPieces(areaTag.contents(), "ANAME"));
			if((A != null) && (!isFreshArea(A)))
				reshapeArea(A, areaTag.contents());
		}
	}

	/**
	 * Puts back what was in space, as the save's SPACE section has it: takes
	 * out what the boot put there (from the world's files), then puts each
	 * saved thing where it was, with its speed and course.
	 * @param space the SPACE tag
	 */
	private static void putSpace(final XMLTag space)
	{
		for(final Item I : WorldFiles.spaceItems())
		{
			CMLib.space().delObjectInSpace((SpaceObject)I);
			I.destroy();
		}
		for(final XMLTag tag : space.contents())
		{
			for(final Item I : unpackItems(tag).items)
			{
				if(!(I instanceof SpaceObject))
				{
					I.destroy();
					continue;
				}
				CMLib.space().addObjectToSpace((SpaceObject)I, ((SpaceObject)I).coordinates());
				CMLib.map().registerWorldObjectLoaded(null, null, I);
			}
		}
	}

	/** the activities of the player's characters the save being made could not keep whole */
	private static final List<String>	partlyKept	= new ArrayList<String>();

	/**
	 * Returns what the last save made could not keep whole of what the
	 * player's characters had under way: each skill, with the parts of it a
	 * restore starts afresh.
	 * @return the skills, as "Name (part, part)"
	 */
	public static List<String> partlyKeptActivities()
	{
		synchronized(partlyKept)
		{
			return new ArrayList<String>(partlyKept);
		}
	}

	/** the areas made in play that a restore made again, to take back once the characters are in */
	private static final List<Area>	remadeAreas	= new ArrayList<Area>();
	/** the activities of the lasting effects of each area made again, as saved */
	private static final Map<Area, XMLTag>	remadeLasting	= new HashMap<Area, XMLTag>();

	/**
	 * Writes what the lasting effects of an area made in play have under way
	 * (their ACTIVITY, such as an instance's leader), which the area's text,
	 * holding the effects themselves, does not.
	 * @param A the area
	 * @return the LASTING tag, or ""
	 */
	private static String lastingActivitiesXML(final Area A)
	{
		final StringBuilder str = new StringBuilder("");
		for(final Enumeration<Ability> a = A.effects(); a.hasMoreElements();)
		{
			final Ability E = a.nextElement();
			if((E == null) || isTimedEffect(E))
				continue;
			String activity = "";
			try
			{
				activity = E.getStat("ACTIVITY");
			}
			catch(final Exception e)
			{
			}
			if((activity != null) && (activity.length() > 0))
				str.append("<L ID=\"").append(E.ID()).append("\">").append(activity).append("</L>");
		}
		return (str.length() == 0) ? "" : ("<LASTING>" + str.toString() + "</LASTING>");
	}

	/**
	 * Makes again an area made in play (an instance child, a plane, a
	 * generated area), as a save's NEWAREA tag has it, with no rooms yet:
	 * by its instance parent if it has one (see
	 * {@link Area#makeInstanceChildAgain(String)}), else from its class,
	 * with the room numbers of the area it copies.  Its timed effects (an
	 * instance's timer, a plane's own) wait for the player.
	 * @param tag the NEWAREA tag
	 */
	private static void remakeArea(final XMLTag tag)
	{
		final XMLLibrary xml = CMLib.xml();
		final String name = xml.restoreAngleBrackets(tag.getValFromPieces("NAME"));
		if((name.length() == 0) || (CMLib.map().getArea(name) != null))
			return;
		Area A = null;
		final int x = name.indexOf('_');
		if((x > 0) && CMath.isInteger(name.substring(0, x)))
		{
			final Area parentA = CMLib.map().getArea(name.substring(x + 1));
			if(parentA != null)
				A = parentA.makeInstanceChildAgain(name);
		}
		if(A == null)
		{
			A = CMClass.getAreaType(tag.getValFromPieces("CLASS"));
			if(A == null)
			{
				SaveGame.noteWorldChange(CMLib.lang().L("The area @x1, made in play, is of a kind this game does not have.", name));
				return;
			}
			A.setName(name);
			if((A instanceof SubArea) && CMath.bset(A.flags(), Area.FLAG_INSTANCE_CHILD))
			{
				final Area parentA = ((SubArea)A).getSuperArea();
				if(parentA != null)
				{
					A.setTimeObj((TimeClock)parentA.getTimeObj().copyOf());
					for(final Enumeration<String> ids = parentA.getProperRoomnumbers().getRoomIDs(); ids.hasMoreElements();)
					{
						final String id = ids.nextElement();
						final int y = id.indexOf('#');
						if(y >= 0)
							A.addProperRoomnumber(name + id.substring(y));
					}
				}
			}
			CMLib.map().addArea(A);
			A.setAreaState(Area.State.ACTIVE);
		}
		final XMLTag areaTag = tag.getPieceFromPieces("AREA");
		if(areaTag != null)
			reshapeArea(A, areaTag.contents());
		laterEffects(A, tag);
		remadeAreas.add(A);
		final XMLTag lasting = tag.getPieceFromPieces("LASTING");
		if(lasting != null)
			remadeLasting.put(A, lasting);
	}

	/**
	 * Takes back each area made again from the save, once the characters are
	 * in and the areas' effects are back: each effect is told (the internal
	 * stat ADOPT, so that an instance rejoins its parent's list and new
	 * instances and planes are numbered past it), and an instance parent
	 * keeps the child for the characters and followers in it.
	 */
	private static void takeBackRemadeAreas()
	{
		final List<Area> areas = new ArrayList<Area>(remadeAreas);
		remadeAreas.clear();
		final Map<Area, XMLTag> lasting = new HashMap<Area, XMLTag>(remadeLasting);
		remadeLasting.clear();
		for(final Area A : areas)
		{
			if(A.amDestroyed())
				continue;
			if(lasting.containsKey(A))
			{
				for(final XMLTag L : lasting.get(A).contents())
				{
					final Ability E = A.fetchEffect(L.getParmValue("ID"));
					try
					{
						if(E != null)
							E.setStat("ACTIVITY", L.value());
					}
					catch(final Exception ex)
					{
					}
				}
			}
			for(final Enumeration<Ability> e = A.effects(); e.hasMoreElements();)
			{
				final Ability E = e.nextElement();
				try
				{
					if(E != null)
						E.setStat("ADOPT", "");
				}
				catch(final Exception ex)
				{
				}
			}
			final int x = A.Name().indexOf('_');
			final Area parentA = ((x > 0) && CMath.isInteger(A.Name().substring(0, x))) ? CMLib.map().getArea(A.Name().substring(x + 1)) : null;
			if(parentA == null)
				continue;
			for(final Enumeration<Room> r = A.getFilledProperMap(); r.hasMoreElements();)
			{
				final Room R = r.nextElement();
				if(R == null)
					continue;
				for(final Enumeration<MOB> m = R.inhabitants(); m.hasMoreElements();)
				{
					final MOB M = m.nextElement();
					final MOB leader = (M == null) ? null : M.getGroupLeader();
					if((M != null) && ((!M.isMonster()) || ((leader != null) && (!leader.isMonster()))))
						parentA.addInstanceChildMember(A, M);
				}
			}
		}
	}

	/**
	 * Takes away what a room or area's shape holds (behaviours, lasting
	 * effects and scripts), before the saved shape gives them again.
	 * @param P the room or area
	 */
	private static void clearShape(final PhysicalAgent P)
	{
		P.delAllBehaviors();
		P.delAllScripts();
		final List<Ability> lasting = new ArrayList<Ability>();
		for(final Enumeration<Ability> a = P.effects(); a.hasMoreElements();)
		{
			final Ability A = a.nextElement();
			if((A != null) && A.isSavable() && (!isTimedEffect(A)))
				lasting.add(A);
		}
		for(final Ability A : lasting)
			P.delEffect(A);
	}

	/**
	 * Gives a room its saved shape: texts, behaviours, lasting effects,
	 * scripts and exits.  A room whose class play changed is left as it is,
	 * and said so.
	 * @param R the room
	 * @param shape the AROOM tag's pieces
	 */
	private static void reshapeRoom(Room R, final List<XMLTag> shape)
	{
		final XMLLibrary xml = CMLib.xml();
		final String roomClass = xml.getValFromPieces(shape, "RCLAS");
		if(!CMClass.classID(R).equalsIgnoreCase(roomClass))
		{
			// the kind play made it, as MODIFY ROOM CLASS does: the rooms
			// that lead to it are linked to the new one
			final Room newR = CMClass.getLocale(roomClass);
			if(newR == null)
			{
				SaveGame.noteWorldChange(CMLib.lang().L("Room @x1 became a kind of room this game does not have (@x2).", R.roomID(), roomClass));
				return;
			}
			R = CMLib.genEd().changeRoomType(R, newR);
		}
		clearShape(R);
		R.setDisplayText(xml.getValFromPieces(shape, "RDISP"));
		R.setDescription(xml.getValFromPieces(shape, "RDESC"));
		R.setMiscText(xml.restoreAngleBrackets(xml.getValFromPieces(shape, "RTEXT")));
		final List<XMLTag> exits = xml.getContentsFromPieces(shape, "ROOMEXITS");
		if(exits != null)
		{
			final boolean[] linked = new boolean[Directions.NUM_DIRECTIONS()];
			for(final XMLTag x : exits)
			{
				if(!x.tag().equalsIgnoreCase("REXIT"))
					continue;
				final int dir = x.getIntFromPieces("XDIRE");
				if((dir < 0) || (dir >= Directions.NUM_DIRECTIONS()))
					continue; // a grid's cross exit, which the room's text holds
				final String doorID = x.getValFromPieces("XDOOR");
				final List<XMLTag> exitPieces = x.getContentsFromPieces("XEXIT");
				Exit E = null;
				if((exitPieces != null) && (exitPieces.size() > 0))
				{
					E = CMClass.getExit(xml.getValFromPieces(exitPieces, "EXID"));
					if(E != null)
					{
						E.setMiscText(xml.restoreAngleBrackets(xml.getValFromPieces(exitPieces, "EXDAT")));
						E.recoverPhyStats();
					}
				}
				R.rawDoors()[dir] = (doorID.length() > 0) ? CMLib.map().getRoom(doorID) : null;
				R.setRawExit(dir, E);
				linked[dir] = true;
			}
			for(int d = 0; d < linked.length; d++)
			{
				if(!linked[d])
				{
					R.rawDoors()[d] = null;
					R.setRawExit(d, null);
				}
			}
		}
		R.recoverPhyStats();
		R.recoverRoomStats();
	}

	/**
	 * Gives an area its saved shape: description, climate, theme, and what
	 * its text holds (behaviours, lasting effects, atmosphere and the like).
	 * @param A the area
	 * @param shape the AREA tag's pieces
	 */
	private static void reshapeArea(final Area A, final List<XMLTag> shape)
	{
		final XMLLibrary xml = CMLib.xml();
		clearShape(A);
		A.setDescription(xml.getValFromPieces(shape, "ADESC"));
		A.setClimateType(xml.getIntFromPieces(shape, "ACLIM"));
		A.setTheme(xml.getIntFromPieces(shape, "ATECH"));
		A.setMiscText(xml.getValFromPieces(shape, "ADATA"));
		A.recoverPhyStats();
	}

	/**
	 * Returns whether a restore with FRESH= takes an area as the world has it
	 * now.
	 * @param A the area
	 * @return true if so
	 */
	private static boolean isFreshArea(final Area A)
	{
		final Room R = A.getProperMap().hasMoreElements() ? A.getProperMap().nextElement() : null;
		return (R != null) && isFresh(R);
	}

	/**
	 * Notes the quests the world's files gave this game, once it has booted
	 * and before a save is put back: any other quest is one made in play
	 * (generated, or a quest's spawned copy), which a save keeps whole.
	 */
	public static void noteContentQuests()
	{
		synchronized(contentQuests)
		{
			contentQuests.clear();
			for(final Enumeration<Quest> q = CMLib.quests().enumQuests(); q.hasMoreElements();)
			{
				final Quest Q = q.nextElement();
				if((Q != null) && (!Q.isCopy()))
					contentQuests.add(Q.name().toUpperCase());
			}
			contentNoted = true;
		}
	}

	/**
	 * Returns whether a quest was made in play rather than given by the
	 * world's files: generated by RandomQuests, GENERATE QUEST or a quest
	 * giver, or a quest's copy.
	 * @param Q the quest
	 * @return true if made in play
	 */
	private static boolean isMadeInPlay(final Quest Q)
	{
		if(Q.isCopy())
			return true;
		synchronized(contentQuests)
		{
			return contentNoted && (!contentQuests.contains(Q.name().toUpperCase()));
		}
	}

	/**
	 * Writes each quest's state: who has won it, whether it is running, on
	 * which step and with how long left, and where the mobs and items it is
	 * using are, or that they are gone.
	 * @param player the player, whose carried quest items are saved with them
	 * @return the XML
	 */
	private static String questsXML(final MOB player)
	{
		final XMLLibrary xml = CMLib.xml();
		final StringBuilder str = new StringBuilder("<QUESTS>\n");
		for(final Enumeration<Quest> q = CMLib.quests().enumQuests(); q.hasMoreElements();)
		{
			final Quest Q = q.nextElement();
			if(Q == null)
				continue;
			if(isMadeInPlay(Q))
			{
				// not in the world's files: kept whole, to be made again
				str.append("<QUEST GENERATED=\"true\" COPY=\"").append(Q.isCopy()).append("\">");
				str.append(xml.convertXMLtoTag("SCRIPT", xml.parseOutAngleBrackets(Q.script())));
			}
			else
				str.append("<QUEST>");
			str.append(xml.convertXMLtoTag("NAME", xml.parseOutAngleBrackets(Q.name())));
			str.append(xml.convertXMLtoTag("WINNERS", xml.parseOutAngleBrackets(Q.getWinnerStr())));
			str.append(xml.convertXMLtoTag("RUNNING", Q.running()));
			if("true".equals(Q.getStat("FINISHED"))) // Q1
				str.append(xml.convertXMLtoTag("FINISHED", true));
			final Map<String, String> qvars = Q.getQuestVars();
			if(qvars.size() > 0)
			{
				str.append("<VARS>");
				for(final Map.Entry<String, String> e : qvars.entrySet())
				{
					str.append("<VAR>");
					str.append(xml.convertXMLtoTag("NAME", xml.parseOutAngleBrackets(e.getKey())));
					str.append(xml.convertXMLtoTag("VALUE", xml.parseOutAngleBrackets(e.getValue())));
					str.append("</VAR>");
				}
				str.append("</VARS>");
			}
			if((!Q.running()) && (Q.waitRemaining() >= 0))
				str.append(xml.convertXMLtoTag("WAIT", Q.waitRemaining()));
			if(Q.running())
			{
				str.append(xml.convertXMLtoTag("STEP", Q.getStat("STEPNUM")));
				if(Q.getStat("STEPNAME").length() > 0) // Q6: an edited file still restores to the step by its name
					str.append(xml.convertXMLtoTag("STEPNAME", xml.parseOutAngleBrackets(Q.getStat("STEPNAME"))));
				str.append(xml.convertXMLtoTag("REMAINING", Q.ticksRemaining()));
				for(int i = 1; i < 10000; i++)
				{
					final MOB M = Q.getQuestMob(i);
					if(M == null)
						break;
					str.append("<QMOB I=\"").append(i).append("\">");
					if(M.amDead() || M.amDestroyed() || (M.location() == null))
						str.append("<GONE />");
					else
					{
						str.append(xml.convertXMLtoTag("ROOM", xml.parseOutAngleBrackets(CMLib.map().getExtendedRoomID(M.location()))));
						str.append(xml.convertXMLtoTag("HP", M.curState().getHitPoints()));
						if(!M.isGeneric())
						{
							str.append("<BASE>");
							str.append(statsXML("PHY", M.basePhyStats()));
							str.append(statsXML("CHAR", M.baseCharStats()));
							str.append(statsXML("STATE", M.baseState()));
							str.append("</BASE>");
						}
					}
					str.append("</QMOB>");
				}
				for(int i = 1; i < 10000; i++)
				{
					final Item I = Q.getQuestItem(i);
					if(I == null)
						break;
					final ItemPossessor owner = I.owner();
					str.append("<QITEM I=\"").append(i).append("\">");
					if(I.amDestroyed() || (owner == null) || (owner == player))
						str.append("<GONE />");
					else
					if(owner instanceof Room)
						str.append(xml.convertXMLtoTag("ROOM", xml.parseOutAngleBrackets(CMLib.map().getExtendedRoomID((Room)owner))));
					str.append("</QITEM>");
				}
			}
			str.append("</QUEST>\n");
		}
		return str.append("</QUESTS>\n").toString();
	}

	/**
	 * Puts each quest in the state it was saved in: its winners, then, if it
	 * was running, starts it if the boot did not, moves it to its step, gives
	 * it the time it had left, and puts its mobs and items where they were,
	 * or does away with those that were gone; if it was not running, stops it
	 * if the boot started it.  A quest started again spawns afresh what it
	 * uses, which is then made to match the save.
	 * @param quests the QUESTS tag
	 */
	private static void setQuests(final XMLTag quests)
	{
		final XMLLibrary xml = CMLib.xml();
		for(final XMLTag tag : quests.contents())
		{
			Quest Q = CMLib.quests().fetchQuest(xml.restoreAngleBrackets(tag.getValFromPieces("NAME")));
			if((Q == null) && "true".equalsIgnoreCase(tag.getParmValue("GENERATED")))
				Q = makeQuestAgain(tag);
			if((Q == null) && (!"true".equalsIgnoreCase(tag.getParmValue("GENERATED"))))
				SaveGame.noteWorldChange(CMLib.lang().L("Quest @x1 is gone from the world.", xml.restoreAngleBrackets(tag.getValFromPieces("NAME"))));
			if((Q == null) || (Q.isCopy() && (!"true".equalsIgnoreCase(tag.getParmValue("GENERATED")))))
				continue;
			Q.setWinners(xml.restoreAngleBrackets(tag.getValFromPieces("WINNERS")));
			if(!tag.getBoolFromPieces("RUNNING", false))
			{
				if(Q.running())
					Q.stopQuest();
				// Q1: finished as it was saved, or no longer
				final boolean finished = tag.getBoolFromPieces("FINISHED", false);
				if(finished || "true".equals(Q.getStat("FINISHED")))
					Q.setStat("FINISHED", Boolean.toString(finished));
				final XMLTag wait = tag.getPieceFromPieces("WAIT");
				if(wait != null)
					Q.setWaitRemaining(CMath.s_int(wait.value()));
				setQuestVars(Q, tag);
				continue;
			}
			if("true".equals(Q.getStat("FINISHED"))) // Q1: it was running then
				Q.setStat("FINISHED", "false");
			if(!Q.running())
				Q.startQuest();
			if(!Q.running())
			{
				Log.errOut("WorldState", "Quest '" + Q.name() + "' was running when saved, but did not start again.");
				continue;
			}
			int step = tag.getIntFromPieces("STEP", 1);
			final String stepName = xml.restoreAngleBrackets(tag.getValFromPieces("STEPNAME")); // Q6
			if((stepName != null) && (stepName.trim().length() > 0) && (CMath.s_int(Q.getStat("STEPOF:" + stepName)) > 0))
				step = CMath.s_int(Q.getStat("STEPOF:" + stepName));
			if((step > 1) && (CMath.s_int(Q.getStat("STEPNUM")) != step))
				Q.setQuestStep(step);
			if(Q.running())
			{
				setQuestVars(Q, tag);
				Q.setStat("REMAINING", Integer.toString(Math.max(1, tag.getIntFromPieces("REMAINING", 1))));
				for(final XMLTag piece : tag.contents())
				{
					if(piece.tag().equalsIgnoreCase("QMOB"))
					{
						final MOB M = Q.getQuestMob(CMath.s_int(piece.getParmValue("I")));
						if(M == null)
							continue;
						if(piece.getPieceFromPieces("GONE") != null)
							M.destroy();
						else
						{
							final Room R = getRoom(piece.getValFromPieces("ROOM"));
							if((R != null) && (M.location() != R))
								R.bringMobHere(M, false);
							final XMLTag base = piece.getPieceFromPieces("BASE");
							if(base != null)
							{
								setStats(M.basePhyStats(), base.getPieceFromPieces("PHY"));
								setStats(M.baseCharStats(), base.getPieceFromPieces("CHAR"));
								setStats(M.baseState(), base.getPieceFromPieces("STATE"));
								M.recoverCharStats();
								M.recoverPhyStats();
								M.recoverMaxState();
							}
							M.curState().setHitPoints(Math.max(1, Math.min(piece.getIntFromPieces("HP"), M.maxState().getHitPoints())));
						}
					}
					else
					if(piece.tag().equalsIgnoreCase("QITEM"))
					{
						final Item I = Q.getQuestItem(CMath.s_int(piece.getParmValue("I")));
						if(I == null)
							continue;
						if(piece.getPieceFromPieces("GONE") != null)
							I.destroy();
						else
						{
							final Room R = getRoom(piece.getValFromPieces("ROOM"));
							if((R != null) && (I.owner() != R))
								R.moveItemTo(I);
						}
					}
				}
			}
		}
	}

	/**
	 * Makes again a quest that was made in play, from the script it was saved
	 * with.  It is then started, put on its step and given its mobs and items,
	 * as a quest from the world's files is.
	 * @param tag the quest's QUEST tag
	 * @return the quest, or null if its script no longer makes one
	 */
	private static Quest makeQuestAgain(final XMLTag tag)
	{
		final String script = CMLib.xml().restoreAngleBrackets(tag.getValFromPieces("SCRIPT"));
		if(script.trim().length() == 0)
			return null;
		final Quest Q = (Quest)CMClass.getCommon("DefaultQuest");
		if((!Q.setScript(script, true)) || (Q.name().trim().length() == 0))
		{
			Log.errOut("WorldState", "A quest made in play, '" + tag.getValFromPieces("NAME") + "', could not be made again from its script.");
			return null;
		}
		CMLib.quests().addQuest(Q);
		if("true".equalsIgnoreCase(tag.getParmValue("COPY")))
		{
			// a copy runs as soon as it is made; started here, then marked
			if((tag.getBoolFromPieces("RUNNING", false)) && (!Q.running()))
				Q.startQuest();
			Q.setCopy(true);
		}
		return Q;
	}

	/**
	 * Puts back the variables a quest's scripts set on it (MPQSET), saved
	 * with the quest.
	 * @param Q the quest
	 * @param tag its QUEST tag
	 */
	private static void setQuestVars(final Quest Q, final XMLTag tag)
	{
		final XMLTag vars = tag.getPieceFromPieces("VARS");
		if(vars == null)
			return;
		final XMLLibrary xml = CMLib.xml();
		for(final XMLTag var : vars.contents())
			Q.setQuestVar(xml.restoreAngleBrackets(var.getValFromPieces("NAME")), xml.restoreAngleBrackets(var.getValFromPieces("VALUE")));
	}

	private static Room getRoom(final String id)
	{
		if((id == null) || (id.length() == 0))
			return null;
		return CMLib.map().getRoom(CMLib.xml().restoreAngleBrackets(id));
	}
}
