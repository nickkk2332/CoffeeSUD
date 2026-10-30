package com.planet_ink.coffee_mud.core.save;

import com.planet_ink.coffee_mud.core.*;
import com.planet_ink.coffee_mud.core.interfaces.*;
import com.planet_ink.coffee_mud.core.collections.Pair;
import com.planet_ink.coffee_mud.core.collections.XVector;
import com.planet_ink.coffee_mud.Abilities.interfaces.*;
import com.planet_ink.coffee_mud.Areas.interfaces.*;
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
 * <li>the player's hit points, mana and movement, and the variables that
 * scripts share.</li>
 * </ul>
 * Players and the mobs following them are otherwise not here: they are saved
 * with the player.  Mobs that cannot be saved, such as summoned ones, are
 * left out.  Timed effects go back on once the player is in the game (see
 * {@link #finishRestore(MOB)}), since the player is often who caused them.
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
		final XMLLibrary xml = CMLib.xml();
		final StringBuilder str = new StringBuilder("<WORLD VERSION=\"" + VERSION + "\">\n");
		if(player != null)
		{
			str.append("<PLAYER>");
			str.append(xml.convertXMLtoTag("NAME", xml.parseOutAngleBrackets(player.Name())));
			str.append(xml.convertXMLtoTag("HP", player.curState().getHitPoints()));
			str.append(xml.convertXMLtoTag("MANA", player.curState().getMana()));
			str.append(xml.convertXMLtoTag("MOVE", player.curState().getMovement()));
			str.append(effectsXML(player));
			str.append("</PLAYER>\n");
		}
		str.append("<ROOMS>\n");
		for(final Enumeration<Room> r = CMLib.map().rooms(); r.hasMoreElements();)
		{
			final Room R = r.nextElement();
			if((R == null) || R.amDestroyed())
				continue;
			final String roomID = CMLib.map().getExtendedRoomID(R);
			if(roomID.length() == 0)
				continue;
			str.append("<ROOM>").append(xml.convertXMLtoTag("ID", xml.parseOutAngleBrackets(roomID)));
			str.append(effectsXML(R));
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
			str.append(itemsXML(roomItems(R)));
			str.append("</ROOM>\n");
		}
		str.append("</ROOMS>\n");
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
				   .append("\" NEXT=\"").append(C.nextWeatherType(null)).append("\">")
				   .append(xml.convertXMLtoTag("NAME", xml.parseOutAngleBrackets(A.Name())))
				   .append("</AREA>\n");
			}
		}
		str.append("</WEATHER>\n");
		str.append(scriptVarsXML());
		str.append("</WORLD>\n");
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
		clearWorld();
		final XMLTag rooms = world.getPieceFromPieces("ROOMS");
		if(rooms != null)
		{
			for(final XMLTag roomTag : rooms.contents())
			{
				final Room R = getRoom(roomTag.getValFromPieces("ID"));
				if(R == null)
					continue;
				for(final XMLTag piece : roomTag.contents())
				{
					if(piece.tag().equalsIgnoreCase("DOOR"))
						setDoor(R, piece);
					else
					if(piece.tag().equalsIgnoreCase("MOB"))
						bringBack(piece, R);
				}
				laterEffects(R, roomTag);
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
		}
		final XMLTag dead = world.getPieceFromPieces("DEAD");
		if(dead != null)
		{
			for(final XMLTag deadTag : dead.contents())
			{
				final XMLTag mobTag = deadTag.getPieceFromPieces("MOB");
				if(mobTag != null)
					bringBackDead(mobTag, CMath.s_int(deadTag.getParmValue("LEFT")));
			}
		}
		final XMLTag taken = world.getPieceFromPieces("TAKEN");
		if(taken != null)
		{
			for(final XMLTag takenTag : taken.contents())
			{
				final Room R = getRoom(takenTag.getValFromPieces("ROOM"));
				if(R == null)
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
		player = world.getPieceFromPieces("PLAYER");
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
				}
			}
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
		if((M == null) || (!M.isMonster()) || (!M.isSavable()) || M.amDestroyed())
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
	private static List<Item> roomItems(final Room R)
	{
		final List<Item> items = new ArrayList<Item>();
		for(int i = 0; i < R.numItems(); i++)
		{
			final Item I = R.getItem(i);
			if((I == null) || I.amDestroyed())
				continue;
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
		}
		return items;
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
		final StringBuilder str = new StringBuilder("<" + tag + ">");
		str.append(xml.convertXMLtoTag("CLASS", CMClass.classID(M)));
		str.append(xml.convertXMLtoTag("LEVEL", M.basePhyStats().level()));
		str.append(xml.convertXMLtoTag("ABILITY", M.basePhyStats().ability()));
		str.append(xml.convertXMLtoTag("REJUV", M.basePhyStats().rejuv()));
		str.append(xml.convertXMLtoTag("TEXT", xml.parseOutAngleBrackets(M.text())));
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
			str.append("<ITEM>");
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
			return null;
		}
		M.setMiscText(CMLib.xml().restoreAngleBrackets(tag.getValFromPieces("TEXT")));
		M.basePhyStats().setLevel(tag.getIntFromPieces("LEVEL"));
		M.basePhyStats().setAbility(tag.getIntFromPieces("ABILITY"));
		M.basePhyStats().setRejuv(tag.getIntFromPieces("REJUV"));
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
	 */
	private static void bringBack(final XMLTag tag, final Room R)
	{
		final MOB M = makeMob(tag);
		if(M == null)
			return;
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
			M.destroy();
		for(final Enumeration<Room> r = CMLib.map().rooms(); r.hasMoreElements();)
		{
			final Room R = r.nextElement();
			if((R == null) || R.amDestroyed())
				continue;
			final List<MOB> mobs = new ArrayList<MOB>();
			for(int m = 0; m < R.numInhabitants(); m++)
			{
				final MOB M = R.fetchInhabitant(m);
				if((M != null) && M.isMonster())
					mobs.add(M);
			}
			for(final MOB M : mobs)
				M.destroy();
			final List<Item> items = new ArrayList<Item>();
			for(int i = 0; i < R.numItems(); i++)
			{
				final Item I = R.getItem(i);
				if(I != null)
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
			str.append("</EFFECT>");
		}
		return (str.length() > 0) ? ("<EFFECTS>" + str.toString() + "</EFFECTS>") : "";
	}

	/** things restored with timed effects, put on them once the player is in */
	private static final List<Pair<Physical, XMLTag>>	laterEffects	= new ArrayList<Pair<Physical, XMLTag>>();
	/** the saved player's state, put back once they are in */
	private static XMLTag								player			= null;

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
	 * points, mana, movement and timed effects, then the timed effects on the
	 * rest of the world.
	 * @param mob the player
	 */
	public static void finishRestore(final MOB mob)
	{
		final XMLTag playerTag = player;
		player = null;
		if((playerTag != null)
		&& (mob.Name().equalsIgnoreCase(CMLib.xml().restoreAngleBrackets(playerTag.getValFromPieces("NAME")))))
		{
			mob.recoverMaxState();
			mob.curState().setHitPoints(Math.max(1, Math.min(playerTag.getIntFromPieces("HP"), mob.maxState().getHitPoints())));
			mob.curState().setMana(Math.min(playerTag.getIntFromPieces("MANA"), mob.maxState().getMana()));
			mob.curState().setMovement(Math.min(playerTag.getIntFromPieces("MOVE"), mob.maxState().getMovement()));
			final XMLTag effects = playerTag.getPieceFromPieces("EFFECTS");
			if(effects != null)
				putEffects(mob, effects, mob);
		}
		final List<Pair<Physical, XMLTag>> todo = new ArrayList<Pair<Physical, XMLTag>>(laterEffects);
		laterEffects.clear();
		for(final Pair<Physical, XMLTag> p : todo)
		{
			if(!p.first.amDestroyed())
				putEffects(p.first, p.second, mob);
		}
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

	private static Room getRoom(final String id)
	{
		if((id == null) || (id.length() == 0))
			return null;
		return CMLib.map().getRoom(CMLib.xml().restoreAngleBrackets(id));
	}
}
