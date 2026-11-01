package com.planet_ink.coffee_mud.Commands;
import com.planet_ink.coffee_mud.core.interfaces.*;
import com.planet_ink.coffee_mud.core.*;
import com.planet_ink.coffee_mud.core.collections.*;
import com.planet_ink.coffee_mud.core.save.SaveGame;
import com.planet_ink.coffee_mud.Areas.interfaces.*;
import com.planet_ink.coffee_mud.Behaviors.interfaces.*;
import com.planet_ink.coffee_mud.Items.interfaces.*;
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
 * DT28, for builders: VESSEL STATUS name shows a vessel as it is: where, its
 * course, anchor, facing and speed, hull, owner and home port, the
 * behaviours on it with their state, and who is aboard.  (T11 adds the
 * other VESSEL words.)  The listing of instances and the extra columns of
 * LIST SHIPS are in ListCmd, using {@link #shipColumns}.
 */
public class VesselCmd extends StdCommand
{
	public VesselCmd()
	{
	}

	private final String[] access = I(new String[] { "VESSEL" });

	@Override
	public String[] getAccessWords()
	{
		return access;
	}

	/**
	 * A vessel by name, among the world's.
	 * @param name the name
	 * @return the vessel, or null
	 */
	public static Boardable findVessel(final String name)
	{
		Boardable found = null;
		for(final Enumeration<Boardable> s = CMLib.map().ships(); s.hasMoreElements();)
		{
			final Boardable S = s.nextElement();
			if(S == null)
				continue;
			if(S.Name().equalsIgnoreCase(name))
				return S;
			if((found == null) && CMLib.english().containsString(S.Name(), name))
				found = S;
		}
		return found;
	}

	/**
	 * A vessel's course: the leg it is on (the way it heads), then the rest.
	 * The vessel keeps the leg it is on apart, and ends the rest with -1.
	 * @param N the vessel
	 * @return the directions, or none
	 */
	private static String course(final NavigableItem N)
	{
		final List<Integer> course = N.getCurrentCourse();
		if((course == null) || course.isEmpty())
			return "none";
		final List<String> dirs = new ArrayList<String>();
		if(N.getDirectionFacing() >= 0)
			dirs.add(CMLib.directions().getDirectionName(N.getDirectionFacing()).toLowerCase());
		for(final Integer d : course)
		{
			if((d != null) && (d.intValue() >= 0))
				dirs.add(CMLib.directions().getDirectionName(d.intValue()).toLowerCase());
		}
		return dirs.isEmpty() ? "none" : String.join(", ", dirs);
	}

	/**
	 * LIST SHIPS' extra columns: anchor, course and owner.
	 * @param S the vessel
	 * @return the three, as text
	 */
	public static String[] shipColumns(final Boardable S)
	{
		final Item I = S.getBoardableItem();
		final String anchor = (I instanceof NavigableItem) ? (((NavigableItem)I).isAnchorDown() ? "down" : "up") : "-";
		final String course = (I instanceof NavigableItem) ? course((NavigableItem)I) : "-";
		final String owner = ((I instanceof PrivateProperty) && (((PrivateProperty)I).getOwnerName().length() > 0)) ? ((PrivateProperty)I).getOwnerName() : "-";
		return new String[] { anchor, course, owner };
	}

	private static void behaviours(final StringBuilder str, final String what, final PhysicalAgent P)
	{
		if(P == null)
			return;
		for(final Enumeration<Behavior> b = P.behaviors(); b.hasMoreElements();)
		{
			final Behavior B = b.nextElement();
			if(B != null)
				str.append("\n\r  ").append(what).append(" ").append(B.ID()).append(": ").append(B.getStat("DEBUG"));
		}
	}

	/**
	 * VESSEL STATUS: the whole state of a vessel.
	 * @param S the vessel
	 * @return the lines
	 */
	public static String status(final Boardable S)
	{
		final Item I = S.getBoardableItem();
		final StringBuilder str = new StringBuilder("Vessel " + S.Name() + " (" + ((I == null) ? S.ID() : I.ID()) + "):");
		final Room at = (I == null) ? null : CMLib.map().roomLocation(I);
		final Room docked = S.getIsDocked();
		str.append("\n\r  Where: ").append((at == null) ? "nowhere" : CMLib.map().getExtendedRoomID(at))
			.append((docked != null) ? (", docked at " + CMLib.map().getExtendedRoomID(docked)) : ", not docked");
		if(I instanceof NavigableItem)
		{
			final NavigableItem N = (NavigableItem)I;
			final int facing = N.getDirectionFacing();
			str.append("\n\r  Facing ").append((facing >= 0) ? CMLib.directions().getDirectionName(facing).toLowerCase() : "nowhere")
				.append(", anchor ").append(N.isAnchorDown() ? "down" : "up")
				.append(", course: ").append(course(N))
				.append(", top speed ").append(N.getMaxSpeed())
				.append(", pace ").append(I.phyStats().speed());
		}
		if(I instanceof SiegableItem)
		{
			final SiegableItem H = (SiegableItem)I;
			str.append("\n\r  Hull: ").append(I.usesRemaining()).append("% of ").append(H.getMaxHullPoints()).append(" points")
				.append(H.amDead() ? ", sunk" : "");
		}
		final String owner = (I instanceof PrivateProperty) ? ((PrivateProperty)I).getOwnerName() : "";
		str.append("\n\r  Owner: ").append((owner.length() > 0) ? owner : "none")
			.append(", home port: ").append((S.getHomePortID() == null) || (S.getHomePortID().length() == 0) ? "none" : S.getHomePortID());
		final Area A = S.getArea();
		behaviours(str, "Behaviour on the vessel:", I);
		behaviours(str, "Behaviour on its decks:", A);
		final List<String> crew = new ArrayList<String>();
		final List<String> passengers = new ArrayList<String>();
		if(A != null)
		{
			for(final Enumeration<Room> r = A.getProperMap(); r.hasMoreElements();)
			{
				final Room R = r.nextElement();
				if(R == null)
					continue;
				for(final Enumeration<MOB> m = R.inhabitants(); m.hasMoreElements();)
				{
					final MOB M = m.nextElement();
					if(M == null)
						continue;
					if(M.isMonster() && (M.amFollowing() == null))
						crew.add(M.name());
					else
						passengers.add(M.name());
					behaviours(str, M.name() + "'s", M);
				}
			}
		}
		str.append("\n\r  Crew (").append(crew.size()).append("): ").append(crew.isEmpty() ? "none" : String.join(", ", crew));
		str.append("\n\r  Passengers (").append(passengers.size()).append("): ").append(passengers.isEmpty() ? "none" : String.join(", ", passengers));
		return str.toString();
	}

	/**
	 * LIST INSTANCES: each instance area made from another, with its parent,
	 * class, the characters it was made for, the ticks until it times out,
	 * its rooms and where to GOTO.
	 * @return the lines
	 */
	public static String instances()
	{
		final StringBuilder str = new StringBuilder("Instance areas:");
		int n = 0;
		for(final Enumeration<Area> a = CMLib.map().areas(); a.hasMoreElements();)
		{
			final Area A = a.nextElement();
			if((A == null) || (!CMath.bset(A.flags(), Area.FLAG_INSTANCE_CHILD)))
				continue;
			n++;
			Area parent = (A instanceof SubArea) ? ((SubArea)A).getSuperArea() : null;
			if(parent == null)
			{
				final int x = A.Name().indexOf('_');
				if(x > 0)
					parent = CMLib.map().getArea(A.Name().substring(x + 1));
			}
			final List<String> members = new ArrayList<String>();
			if(parent != null)
			{
				for(final MOB M : parent.instanceMembers(A))
					members.add(M.Name());
			}
			final String timeout = A.getStat("TIMEOUT_INSTANCE");
			final Enumeration<Room> r = A.getProperMap();
			final Room first = r.hasMoreElements() ? r.nextElement() : null;
			str.append("\n\r  ").append(A.Name()).append(" (").append(A.ID()).append("), of ").append((parent == null) ? "?" : parent.Name())
				.append(": ").append(A.numberOfProperIDedRooms()).append(" rooms; for ").append(members.isEmpty() ? "no one" : String.join(", ", members))
				.append("; ").append((CMath.isInteger(timeout) && (CMath.s_int(timeout) > 0)) ? ("times out in " + timeout + " ticks") : "no time out")
				.append((first == null) ? "" : ("; GOTO " + CMLib.map().getExtendedRoomID(first)));
		}
		if(n == 0)
			str.append(" none.");
		return str.toString();
	}

	@Override
	public boolean execute(final MOB mob, final List<String> commands, final int metaFlags)
		throws java.io.IOException
	{
		if((commands.size() < 3) || (!commands.get(1).equalsIgnoreCase("STATUS")))
		{
			mob.tell(L("VESSEL STATUS [NAME]"));
			return false;
		}
		final String name = CMParms.combine(commands, 2);
		final Boardable S = findVessel(name);
		if(S == null)
		{
			mob.tell(L("There is no vessel '@x1'.", name));
			return false;
		}
		mob.tell(status(S));
		return false;
	}

	@Override
	public boolean canBeOrdered()
	{
		return false;
	}

	@Override
	public boolean securityCheck(final MOB mob)
	{
		return SaveGame.isBuilderMode() || SaveGame.isDevMode() || CMSecurity.isAllowedAnywhere(mob, CMSecurity.SecFlag.CMDAREAS);
	}
}
