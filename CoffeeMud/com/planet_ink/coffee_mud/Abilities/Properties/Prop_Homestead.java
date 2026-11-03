package com.planet_ink.coffee_mud.Abilities.Properties;
import com.planet_ink.coffee_mud.core.interfaces.*;
import com.planet_ink.coffee_mud.core.*;
import com.planet_ink.coffee_mud.core.collections.*;
import com.planet_ink.coffee_mud.Abilities.interfaces.*;
import com.planet_ink.coffee_mud.Areas.interfaces.*;
import com.planet_ink.coffee_mud.Behaviors.interfaces.*;
import com.planet_ink.coffee_mud.CharClasses.interfaces.*;
import com.planet_ink.coffee_mud.Commands.interfaces.*;
import com.planet_ink.coffee_mud.Common.interfaces.*;
import com.planet_ink.coffee_mud.Exits.interfaces.*;
import com.planet_ink.coffee_mud.Items.interfaces.*;
import com.planet_ink.coffee_mud.Libraries.interfaces.*;
import com.planet_ink.coffee_mud.Locales.interfaces.*;
import com.planet_ink.coffee_mud.MOBS.interfaces.*;
import com.planet_ink.coffee_mud.Races.interfaces.*;

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
 * CR7: land a character may build on without a deed, on a room or a whole
 * area.  MASK= (a zapper mask on the builder) and SKILLS= (the building
 * skills allowed, by ID or name; all when not given) say who and how.
 * CLAIM=true (the default) has the first build in a room with no title
 * claim it: it gets a Prop_LotsForSale owned by the builder (or by
 * @PROFILE with CLAIM=PROFILE or PROFILEPROPERTY=true), priced PRICE= (at
 * most 1, the default, pays no property tax), and MAXROOMS=n puts a
 * Prop_ReqCapacity on it, so the lot grows by the usual building rules to
 * n rooms at most.  If that build does not start, the claim is taken back.
 * CLAIM=false lets them build there with no title at all.
 */
public class Prop_Homestead extends Property
{
	@Override
	public String ID()
	{
		return "Prop_Homestead";
	}

	@Override
	public String name()
	{
		return "Homestead";
	}

	@Override
	protected int canAffectCode()
	{
		return Ability.CAN_ROOMS | Ability.CAN_AREAS;
	}

	protected String		mask		= "";
	protected Set<String>	skills		= new TreeSet<String>();
	protected boolean		claim		= true;
	protected boolean		forProfile	= false;
	protected int			maxRooms	= 0;
	protected int			price		= 1;

	@Override
	public void setMiscText(final String newMiscText)
	{
		super.setMiscText(newMiscText);
		mask = CMParms.getParmStr(newMiscText, "MASK", "").trim();
		skills = new TreeSet<String>();
		for(final String s : CMParms.parseCommas(CMParms.getParmStr(newMiscText, "SKILLS", ""), true))
			skills.add(s.trim().toUpperCase());
		final String c = CMParms.getParmStr(newMiscText, "CLAIM", "true").trim();
		forProfile = c.equalsIgnoreCase("PROFILE");
		claim = forProfile || CMath.s_bool(c) || c.length() == 0;
		maxRooms = CMParms.getParmInt(newMiscText, "MAXROOMS", 0);
		price = Math.max(1, CMParms.getParmInt(newMiscText, "PRICE", 1));
		if(CMParms.getParmBool(newMiscText, "NOTAX", false) || CMParms.parse(newMiscText.toUpperCase()).contains("NOTAX"))
			price = 1; // a price of 1 pays no property tax
	}

	@Override
	public String accountForYourself()
	{
		return "Land that may be built on without a deed" + (claim ? ", claimed by the first build" : "")
			+ ((maxRooms > 0) ? (", up to " + maxRooms + " rooms") : "");
	}

	/**
	 * @param R a room
	 * @return the homestead on it, or on its area or a parent area, or null
	 */
	public static Prop_Homestead at(final Room R)
	{
		if(R == null)
			return null;
		Ability A = R.fetchEffect("Prop_Homestead");
		if(A instanceof Prop_Homestead)
			return (Prop_Homestead)A;
		final Area area = R.getArea();
		if(area == null)
			return null;
		A = area.fetchEffect("Prop_Homestead");
		if(A instanceof Prop_Homestead)
			return (Prop_Homestead)A;
		for(final Enumeration<Area> p = area.getParents(); p.hasMoreElements();)
		{
			A = p.nextElement().fetchEffect("Prop_Homestead");
			if(A instanceof Prop_Homestead)
				return (Prop_Homestead)A;
		}
		return null;
	}

	/**
	 * @param mob a builder
	 * @param skill the building skill
	 * @return whether this lets them build with it
	 */
	public boolean fits(final MOB mob, final Ability skill)
	{
		if((mob == null) || (skill == null))
			return false;
		if((skills.size() > 0) && (!skills.contains(skill.ID().toUpperCase())) && (!skills.contains(skill.Name().toUpperCase())))
			return false;
		return (mask.length() == 0) || CMLib.masking().maskCheck(mask, mob, true);
	}

	/**
	 * CLAIM=false: building here with no title.
	 * @param mob the builder
	 * @param R the room
	 * @param skill the building skill
	 * @return whether they may
	 */
	public static boolean allows(final MOB mob, final Room R, final Ability skill)
	{
		final Prop_Homestead H = at(R);
		return (H != null) && (!H.claim) && H.fits(mob, skill);
	}

	/**
	 * The first build on a homestead room with no title: it becomes the
	 * builder's (or the profile's) lot.
	 * @param mob the builder
	 * @param R the room
	 * @param skill the building skill
	 * @return what was put on the room, to take back if the build does not start, or null if nothing was claimed
	 */
	public static List<Ability> claim(final MOB mob, final Room R, final Ability skill)
	{
		final Prop_Homestead H = at(R);
		if((H == null) || (!H.claim) || (!H.fits(mob, skill)) || (CMLib.law().getLandTitle(R) != null))
			return null;
		final String owner = H.forProfile && (Party.profileOf(mob) != null) ? ProfileOwner.TOKEN : ProfileOwner.buyerName(mob);
		final List<Ability> added = new ArrayList<Ability>(2);
		final Ability lot = CMClass.getAbility("Prop_LotsForSale");
		if(lot == null)
			return null;
		lot.setMiscText(owner + "/" + H.price);
		R.addNonUninvokableEffect(lot);
		added.add(lot);
		if((H.maxRooms > 0) && (R.fetchEffect("Prop_ReqCapacity") == null))
		{
			final Ability cap = CMClass.getAbility("Prop_ReqCapacity");
			if(cap != null)
			{
				cap.setMiscText("rooms=" + H.maxRooms);
				R.addNonUninvokableEffect(cap);
				added.add(cap);
			}
		}
		if(!CMLib.law().doesOwnThisLand(mob, R))
		{
			unclaim(R, added);
			return null;
		}
		return added;
	}

	/**
	 * Takes back a claim whose build did not start.
	 * @param R the room
	 * @param added what claim put on it
	 */
	public static void unclaim(final Room R, final List<Ability> added)
	{
		if((R == null) || (added == null))
			return;
		for(final Ability A : added)
			R.delEffect(A);
	}

	/**
	 * A claim kept: the room saved with its new lot, and the builder told.
	 * @param mob the builder
	 * @param R the room
	 */
	public static void claimed(final MOB mob, final Room R)
	{
		final LandTitle T = CMLib.law().getLandTitle(R);
		if(T != null)
			T.updateTitle();
		if(mob != null)
			mob.tell(CMLib.lang().L("You claim this land as a homestead."));
	}
}
