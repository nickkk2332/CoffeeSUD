package com.planet_ink.coffee_mud.core;

import java.util.*;

import com.planet_ink.coffee_mud.Common.interfaces.PlayerAccount;
import com.planet_ink.coffee_mud.Common.interfaces.PlayerStats;
import com.planet_ink.coffee_mud.Items.interfaces.Boardable;
import com.planet_ink.coffee_mud.Items.interfaces.Item;
import com.planet_ink.coffee_mud.Locales.interfaces.Room;
import com.planet_ink.coffee_mud.MOBS.interfaces.MOB;
import com.planet_ink.coffee_mud.core.interfaces.LandTitle;
import com.planet_ink.coffee_mud.core.interfaces.PrivateProperty;
import com.planet_ink.coffee_mud.Common.interfaces.Clan;

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
 * CR1: property owned by the profile.  Every character of the player's
 * profile counts as the owner of what any of them owns, and of what the
 * reserved owner @PROFILE owns: land, rooms, private items, vessels.  A
 * world file can ship a home that is already the player's, as
 * Prop_RoomForSale("@PROFILE/0"), and it is never wiped as an owner who
 * does not exist.  With PROFILEPROPERTY=true in the ini, what the player
 * buys is recorded as @PROFILE.  MPGRANT (grant) gives a deed, a vessel or
 * a private item to a character or the profile from a script.
 */
public final class ProfileOwner
{
	private ProfileOwner()
	{
	}

	/** the owner that stands for the whole profile */
	public static final String	TOKEN	= "@PROFILE";

	/**
	 * @param owner an owner name
	 * @return whether it is @PROFILE
	 */
	public static boolean isToken(final String owner)
	{
		return (owner != null) && owner.trim().equalsIgnoreCase(TOKEN);
	}

	/**
	 * @param owner an owner name
	 * @return whether it always exists (isProperlyOwned, a lot's owner check)
	 */
	public static boolean exists(final String owner)
	{
		return isToken(owner);
	}

	/**
	 * @param mob a character
	 * @param owner an owner name
	 * @return whether the character owns what the name owns through the
	 *         profile: @PROFILE, or another character of the same profile
	 */
	public static boolean owns(final MOB mob, final String owner)
	{
		if((mob == null) || (owner == null) || (owner.length() == 0))
			return false;
		final PlayerAccount acct = Party.profileOf(mob);
		if(acct == null)
			return false;
		if(isToken(owner))
			return true;
		return acct.findPlayer(owner) != null;
	}

	/**
	 * @return whether purchases are recorded as @PROFILE (PROFILEPROPERTY=true)
	 */
	public static boolean buysForProfile()
	{
		final CMProps P = CMProps.instance();
		return (P != null) && CMath.s_bool(P.getStr("PROFILEPROPERTY", "false").trim());
	}

	/**
	 * @param buyer who buys
	 * @return the owner to record: @PROFILE under PROFILEPROPERTY=true for a
	 *         character of the profile, else the buyer's name
	 */
	public static String buyerName(final MOB buyer)
	{
		if(buyer == null)
			return "";
		if(buysForProfile() && (Party.profileOf(buyer) != null))
			return TOKEN;
		return buyer.Name();
	}

	/**
	 * @param owner an owner name
	 * @return it as a player reads it: "the party" for @PROFILE
	 */
	public static String shown(final String owner)
	{
		return isToken(owner) ? "the party" : owner;
	}

	/**
	 * MPGRANT: gives property to a character, or to the profile, or takes
	 * it back.  A deed's lot changes hands and the deed goes to them; a
	 * vessel is registered with them so it saves with them, renamed if a
	 * name is given (no prompt), and docked near the room given if it is not
	 * already in a room; a private item (Prop_PrivateProperty) changes owner
	 * and goes to them.
	 * @param I the deed, vessel or item
	 * @param to who gets it, or null to take it back (NOBODY)
	 * @param profile true to record the owner as @PROFILE
	 * @param newName a new name for a vessel, or null
	 * @param near where a vessel docks if it is not in a room, or null
	 * @return null when done, else what is wrong
	 */
	public static String grant(final Item I, final MOB to, final boolean profile, final String newName, final Room near)
	{
		if(I == null)
			return "no such item";
		final PrivateProperty P = (I instanceof PrivateProperty) ? (PrivateProperty)I : CMLib.law().getPropertyRecord(I);
		if(P == null)
			return I.Name() + " is not a deed, a vessel or private property";
		final String owner = (to == null) ? "" : (profile ? TOKEN : to.Name());
		if(I instanceof Boardable)
		{
			if((to != null) && (newName != null) && (newName.trim().length() > 0) && (!newName.trim().equalsIgnoreCase(I.Name())))
			{
				final String bad = nameProblem(newName.trim());
				if(bad != null)
					return bad;
				((Boardable)I).rename(newName.trim());
			}
			unregister(I, P.getOwnerName());
			P.setOwnerName(owner);
			if(to != null)
			{
				final PlayerStats pStats = to.playerStats();
				if((pStats != null) && (!pStats.getExtItems().isContent(I)))
				{
					I.setSavable(false); // the character saves it, not the room
					pStats.getExtItems().addItem(I);
				}
				if((!(I.owner() instanceof Room)) && (near != null))
				{
					// (StdBoardable is loaded by the game's own class loader, so its dockNearest is called by name)
					try
					{
						I.getClass().getMethod("dockNearest", Room.class).invoke(I, near);
					}
					catch(final Exception e)
					{
						((Boardable)I).dockHere(near);
					}
				}
			}
			else
				I.setSavable(true);
			I.recoverPhyStats();
			return null;
		}
		if(I instanceof LandTitle)
		{
			final LandTitle T = (LandTitle)I;
			T.setOwnerName(owner);
			T.setBackTaxes(0);
			T.updateTitle();
			T.updateLot(null);
		}
		else
			P.setOwnerName(owner);
		if((to != null) && (I.owner() != to))
			to.moveItemTo(I);
		I.recoverPhyStats();
		return null;
	}

	/** a vessel taken off whoever kept it: its owner, its clan, or (for @PROFILE) any character */
	private static void unregister(final Item I, final String old)
	{
		if((old == null) || (old.length() == 0) || old.startsWith("#"))
			return;
		final Clan C = CMLib.clans().getClanExact(old);
		if(C != null)
		{
			C.getExtItems().delItem(I);
			return;
		}
		final List<MOB> keepers = new ArrayList<MOB>();
		if(isToken(old))
		{
			for(final Enumeration<MOB> m = CMLib.players().players(); m.hasMoreElements();)
				keepers.add(m.nextElement());
		}
		else
		{
			final MOB M = CMLib.players().getLoadPlayer(old);
			if(M != null)
				keepers.add(M);
		}
		for(final MOB M : keepers)
		{
			if((M != null) && (M.playerStats() != null) && M.playerStats().getExtItems().isContent(I))
			{
				M.playerStats().getExtItems().delItem(I);
				M.playerStats().setLastUpdated(0);
			}
		}
	}

	/** what is wrong with a new vessel name, as a sale's prompt checks it, or null */
	private static String nameProblem(final String name)
	{
		for(final Enumeration<Boardable> s = CMLib.map().ships(); s.hasMoreElements();)
		{
			final Boardable B = s.nextElement();
			if((B != null) && (!B.amDestroyed()) && (B.getArea() != null) && B.getArea().Name().equalsIgnoreCase(name))
				return "a vessel is already called '" + name + "'";
		}
		if(CMLib.map().getArea(name) != null)
			return "an area is already called '" + name + "'";
		if((!CMLib.login().isOkName(name, true)) || CMLib.tech().getMakeRegisteredKeys().contains(name))
			return "'" + name + "' is not a permitted name";
		return null;
	}
}
