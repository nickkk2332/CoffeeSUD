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
 * CR20: furniture that changes its room.  ROOMEFFECT=[ABILITY](PARMS) and
 * ROOMBEHAVIOR=[BEHAVIOR](PARMS) are put on the room while the item is
 * there placed (not to be picked up, as decorated or mounted) or lies on
 * the floor of an owned room, and taken off when it leaves; they are not
 * saved with the room.  COMFORT=n adds to the room's comfort, which
 * COMFORT() reads in scripts.  ACTS=fire,forge makes it a workstation of
 * those names (no bonus): fire for a craft that needs one.
 */
public class Prop_Furnishing extends Property implements Workstations.Station
{
	@Override
	public String ID()
	{
		return "Prop_Furnishing";
	}

	@Override
	public String name()
	{
		return "Furnishing";
	}

	@Override
	protected int canAffectCode()
	{
		return Ability.CAN_ITEMS;
	}

	protected String		effectID	= "";
	protected String		effectParms	= "";
	protected String		behaviorID	= "";
	protected String		behaviorParms	= "";
	protected int			comfort		= 0;
	protected Set<String>	acts		= new TreeSet<String>();

	protected Room			appliedTo	= null;
	protected Ability		roomA		= null;
	protected Behavior		roomB		= null;
	protected boolean		syncing		= false;

	private static String[] split(final String s)
	{
		final String t = (s == null) ? "" : s.trim();
		final int x = t.indexOf('(');
		if((x > 0) && t.endsWith(")"))
			return new String[] { t.substring(0, x).trim(), t.substring(x + 1, t.length() - 1).trim() };
		return new String[] { t, "" };
	}

	/**
	 * @param text the parameters
	 * @param key a key whose value may be ID(PARMS WITH SPACES)
	 * @return the value: quoted, to its closing parenthesis, or to a space
	 */
	protected static String parenValue(final String text, final String key)
	{
		final java.util.regex.Matcher m = java.util.regex.Pattern.compile("(?i)(^|\\s)" + key + "\\s*=\\s*").matcher(text);
		if(!m.find())
			return "";
		int x = m.end();
		if((x < text.length()) && ((text.charAt(x) == '"') || (text.charAt(x) == '\'')))
			return CMParms.getParmStr(text, key, "");
		final int start = x;
		int depth = 0;
		while(x < text.length())
		{
			final char c = text.charAt(x);
			if(c == '(')
				depth++;
			else
			if(c == ')')
				depth--;
			else
			if(Character.isWhitespace(c) && (depth <= 0))
				break;
			x++;
		}
		return text.substring(start, x).trim();
	}

	@Override
	public void setMiscText(final String newMiscText)
	{
		super.setMiscText(newMiscText);
		final String[] e = split(parenValue(newMiscText, "ROOMEFFECT"));
		effectID = e[0];
		effectParms = e[1];
		final String[] b = split(parenValue(newMiscText, "ROOMBEHAVIOR"));
		behaviorID = b[0];
		behaviorParms = b[1];
		comfort = CMParms.getParmInt(newMiscText, "COMFORT", 0);
		acts = new TreeSet<String>();
		for(final String s : CMParms.parseCommas(CMParms.getParmStr(newMiscText, "ACTS", "").replace('|', ','), true))
			acts.add(s.trim().toLowerCase());
		unapply();
	}

	@Override
	public String accountForYourself()
	{
		return "Furnishing" + ((comfort != 0) ? (", comfort " + comfort) : "") + (acts.isEmpty() ? "" : (", serves as " + CMParms.toListString(acts)));
	}

	/** @return its comfort */
	public int comfort()
	{
		return comfort;
	}

	/**
	 * @return the room it furnishes now, or null
	 */
	public Room furnishing()
	{
		return appliedTo;
	}

	/**
	 * @param I the item
	 * @return the room it furnishes where it is: placed there, or on the floor of an owned room
	 */
	protected static Room qualifying(final Item I)
	{
		if((I == null) || I.amDestroyed() || (I.container() != null) || (!(I.owner() instanceof Room)))
			return null;
		final Room R = (Room)I.owner();
		if(!CMLib.flags().isGettable(I))
			return R;
		final LandTitle T = CMLib.law().getLandTitle(R);
		return ((T != null) && (T.getOwnerName() != null) && (T.getOwnerName().length() > 0)) ? R : null;
	}

	/** puts its effect and behaviour on the room it furnishes, or takes them off */
	protected void sync()
	{
		if(syncing || (!(affected instanceof Item)))
			return;
		syncing = true;
		try
		{
			final Room want = qualifying((Item)affected);
			if(want == appliedTo)
				return;
			unapply();
			if(want == null)
				return;
			appliedTo = want;
			if(effectID.length() > 0)
			{
				final Ability A = CMClass.getAbility(effectID);
				if(A != null)
				{
					A.setMiscText(effectParms);
					A.setSavable(false);
					want.addNonUninvokableEffect(A);
					roomA = A;
				}
			}
			if(behaviorID.length() > 0)
			{
				final Behavior B = CMClass.getBehavior(behaviorID);
				if(B != null)
				{
					B.setParms(behaviorParms);
					B.setSavable(false);
					want.addBehavior(B);
					roomB = B;
				}
			}
			want.recoverRoomStats();
		}
		finally
		{
			syncing = false;
		}
	}

	/** takes its effect and behaviour off the room it furnished */
	protected void unapply()
	{
		final Room R = appliedTo;
		appliedTo = null;
		if(R == null)
			return;
		final boolean was = syncing;
		syncing = true; // the room's recovery runs the item's stats again: no sync from it
		try
		{
			if(roomA != null)
				R.delEffect(roomA);
			if(roomB != null)
				R.delBehavior(roomB);
			roomA = null;
			roomB = null;
			R.recoverRoomStats();
		}
		finally
		{
			syncing = was;
		}
	}

	@Override
	public void affectPhyStats(final Physical host, final PhyStats affectableStats)
	{
		super.affectPhyStats(host, affectableStats);
		if(host == affected)
			sync();
	}

	@Override
	public void executeMsg(final Environmental myHost, final CMMsg msg)
	{
		super.executeMsg(myHost, msg);
		sync();
	}

	@Override
	public void setAffectedOne(final Physical P)
	{
		final boolean leaving = (P == null) || (P != affected);
		super.setAffectedOne(P); // first: the room's recovery below must not put it back
		if(leaving)
			unapply();
	}

	/**
	 * @param R a room
	 * @return the comfort of the furnishings in it
	 */
	public static int comfortOf(final Room R)
	{
		int total = 0;
		if(R == null)
			return 0;
		for(int i = 0; i < R.numItems(); i++)
		{
			final Item I = R.getItem(i);
			if((I == null) || (qualifying(I) != R))
				continue;
			final Ability A = I.fetchEffect("Prop_Furnishing");
			if(A != null)
				total += CMParms.getParmInt(A.text(), "COMFORT", 0);
		}
		return total;
	}

	@Override
	public boolean isA(final String tag)
	{
		return (tag != null) && acts.contains(tag.trim().toLowerCase());
	}

	@Override
	public boolean helps(final Ability skill)
	{
		return true;
	}

	@Override
	public int speed()
	{
		return 0;
	}

	@Override
	public int prof()
	{
		return 0;
	}

	@Override
	public int maxLevel()
	{
		return 0;
	}

	@Override
	public int quality()
	{
		return 0;
	}
}
