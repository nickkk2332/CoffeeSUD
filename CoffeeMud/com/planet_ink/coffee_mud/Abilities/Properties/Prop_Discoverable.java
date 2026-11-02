package com.planet_ink.coffee_mud.Abilities.Properties;
import com.planet_ink.coffee_mud.core.interfaces.*;
import com.planet_ink.coffee_mud.core.*;
import com.planet_ink.coffee_mud.core.collections.*;
import com.planet_ink.coffee_mud.Abilities.interfaces.*;
import com.planet_ink.coffee_mud.Common.interfaces.*;
import com.planet_ink.coffee_mud.Exits.interfaces.*;
import com.planet_ink.coffee_mud.Items.interfaces.*;
import com.planet_ink.coffee_mud.Libraries.interfaces.*;
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
 * I11: something hidden that anyone can find, on an item, an exit or a mob.
 * Until found nobody sees it (and nobody goes through a hidden exit).
 * FOUNDBY=examine:bookshelf|look:fireplace|search (search by default): an
 * EXAMINE (or a LOOK, for look:) of that thing here, or SEARCH, which works
 * without a skill; CHECK="INT 12" or DC=15 with STAT=: d20, the stat's
 * (n-10)/2 and a tenth of Searching's or Thief_Search's proficiency, against
 * the number (no number: found at once); MSG= what the finder is told; HINT=
 * added when one of its FOUNDBY things is looked at; STAYS=false hides it
 * again RESET=n ticks (30) after.  Found, it stays found (FOUND=true in the
 * text, so saves keep it).
 */
public class Prop_Discoverable extends Property implements VerbTaker
{
	@Override
	public String ID()
	{
		return "Prop_Discoverable";
	}

	@Override
	public String name()
	{
		return "Hidden, to be found";
	}

	@Override
	protected int canAffectCode()
	{
		return Ability.CAN_ITEMS | Ability.CAN_EXITS | Ability.CAN_MOBS;
	}

	@Override
	public String accountForYourself()
	{
		return "Hidden, to be found";
	}

	protected String		base		= "";
	protected List<String>	foundBy		= new ArrayList<String>();
	protected String		stat		= "";
	protected int			dc			= 0;
	protected String		msgText		= "";
	protected String		hint		= "";
	protected boolean		stays		= true;
	protected int			reset		= 30;
	protected int			resetDown	= 0;
	protected boolean		found		= false;
	protected boolean		ticking		= false;

	@Override
	public void setMiscText(final String newText)
	{
		super.setMiscText(newText);
		final String t = (newText == null) ? "" : newText;
		final Map<String, String> q = CMParms.parseExactParms(t);
		foundBy.clear();
		for(final String f : CMParms.getParmStr(q, "FOUNDBY", "search").split("\\|"))
		{
			if(f.trim().length() > 0)
				foundBy.add(f.trim().toLowerCase());
		}
		final String check = CMParms.getParmStr(q, "CHECK", "").trim();
		stat = CMParms.getParmStr(q, "STAT", "WIS").trim();
		dc = CMParms.getParmInt(q, "DC", 0);
		if(check.length() > 0)
		{
			final List<String> c = CMParms.parse(check);
			if(c.size() > 0)
				stat = c.get(0);
			if(c.size() > 1)
				dc = CMath.s_int(c.get(1));
		}
		msgText = CMParms.getParmStr(q, "MSG", "");
		hint = CMParms.getParmStr(q, "HINT", "");
		stays = CMParms.getParmBool(q, "STAYS", true);
		reset = Math.max(1, CMParms.getParmInt(q, "RESET", 30));
		found = CMParms.getParmBool(q, "FOUND", false);
		base = t.replaceAll("(?i)\\s*\\bFOUND=\\S*", "").trim();
		ScriptEvents.propVerbsSeen();
		if(affected != null)
			affected.recoverPhyStats();
	}

	@Override
	public String text()
	{
		return (found && stays) ? (base + " FOUND=true") : base;
	}

	/**
	 * @return whether it has been found
	 */
	public boolean isFound()
	{
		return found;
	}

	@Override
	public String getStat(final String code)
	{
		if(code.equalsIgnoreCase("FOUND"))
			return "" + found;
		return super.getStat(code);
	}

	@Override
	public void affectPhyStats(final Physical host, final PhyStats stats)
	{
		super.affectPhyStats(host, stats);
		if(!found)
			stats.setDisposition(stats.disposition() | PhyStats.IS_NOT_SEEN);
	}

	/** the room it is in */
	protected Room room(final MOB mob)
	{
		if(mob != null)
			return mob.location();
		return CMLib.map().roomLocation(affected);
	}

	/**
	 * One tries to find it.
	 * @param mob who
	 * @return true if they found it
	 */
	public boolean tryFind(final MOB mob)
	{
		if(found || (affected == null))
			return false;
		if(dc > 0)
		{
			final int roll = CMLib.dice().roll(1, 20, 0) + Prop_Ambush.bonus(mob, stat + "+Searching+Thief_Search");
			if(roll < dc)
				return false;
		}
		found = true;
		affected.recoverPhyStats();
		final Room R = room(mob);
		if(R != null)
			R.recoverRoomStats();
		final String m = (msgText.length() > 0) ? msgText : CMLib.lang().L("You find <T-NAME>!");
		mob.tell(mob, affected, null, m);
		if(!stays)
		{
			resetDown = reset;
			if(!ticking)
			{
				ticking = true;
				CMLib.threads().startTickDown(this, Tickable.TICKID_PROPERTY_SPECIAL, 1);
			}
		}
		return true;
	}

	/** whether a FOUNDBY entry names a thing, for a kind (examine, look) */
	protected boolean byThis(final String kind, final Environmental E)
	{
		for(final String f : foundBy)
		{
			final int x = f.indexOf(':');
			if(x < 0)
				continue;
			final String k = f.substring(0, x).trim();
			final String n = f.substring(x + 1).trim();
			if((k.equals(kind) || (k.equals("look") && kind.equals("examine")))
			&& (E != null) && (E != affected) && (CMLib.english().containsString(E.Name(), n) || CMLib.english().containsString(E.name(), n)))
				return true;
		}
		return false;
	}

	@Override
	public void executeMsg(final Environmental myHost, final CMMsg msg)
	{
		super.executeMsg(myHost, msg);
		if((affected == null) || (msg.target() == null) || (msg.target() == affected))
			return;
		final int t = msg.targetMinor();
		if((t != CMMsg.TYP_LOOK) && (t != CMMsg.TYP_EXAMINE))
			return;
		final String kind = (t == CMMsg.TYP_EXAMINE) ? "examine" : "look";
		if(!byThis(kind, msg.target()))
		{
			if(!byThis("examine", msg.target()) && !byThis("look", msg.target()))
				return;
			// looked at, not examined: a hint
			if((!found) && (hint.length() > 0))
				msg.addTrailerMsg(CMClass.getMsg(msg.source(), msg.target(), null, CMMsg.MSG_OK_VISUAL, hint, CMMsg.NO_EFFECT, null, CMMsg.NO_EFFECT, null));
			return;
		}
		if(found)
			return;
		if((hint.length() > 0) && (!tryFind(msg.source())))
			msg.addTrailerMsg(CMClass.getMsg(msg.source(), msg.target(), null, CMMsg.MSG_OK_VISUAL, hint, CMMsg.NO_EFFECT, null, CMMsg.NO_EFFECT, null));
		else
		if(hint.length() == 0)
			tryFind(msg.source());
	}

	@Override
	public boolean okMessage(final Environmental myHost, final CMMsg msg)
	{
		if(!super.okMessage(myHost, msg))
			return false;
		// nobody goes through a hidden way
		if((!found) && (affected instanceof Exit) && (msg.tool() == affected)
		&& ((msg.targetMinor() == CMMsg.TYP_ENTER) || (msg.targetMinor() == CMMsg.TYP_LEAVE)))
		{
			msg.source().tell(CMLib.lang().L("You can't go that way."));
			return false;
		}
		return true;
	}

	/**
	 * The hidden things of a room that SEARCH may find.
	 * @param R the room
	 * @return their properties
	 */
	public static List<Prop_Discoverable> searchable(final Room R)
	{
		final List<Prop_Discoverable> list = new ArrayList<Prop_Discoverable>();
		final List<Physical> things = new ArrayList<Physical>();
		for(final Enumeration<Item> i = R.items(); i.hasMoreElements();)
			things.add(i.nextElement());
		for(final Enumeration<MOB> m = R.inhabitants(); m.hasMoreElements();)
			things.add(m.nextElement());
		for(int d = 0; d < Directions.NUM_DIRECTIONS(); d++)
		{
			if(R.getRawExit(d) != null)
				things.add(R.getRawExit(d));
		}
		for(final Physical P : things)
		{
			final Ability A = (P == null) ? null : P.fetchEffect("Prop_Discoverable");
			if((A instanceof Prop_Discoverable) && (!((Prop_Discoverable)A).found) && ((Prop_Discoverable)A).foundBy.contains("search"))
				list.add((Prop_Discoverable)A);
		}
		return list;
	}

	@Override
	public boolean takeVerb(final MOB mob, final PhysicalAgent host, final String verb, final String rest)
	{
		if((host != affected) || (!verb.equalsIgnoreCase("search")) || found || (!foundBy.contains("search")))
			return false;
		final Room R = mob.location();
		if(R == null)
			return false;
		final List<Prop_Discoverable> all = searchable(R);
		if(all.isEmpty() || (all.get(0) != this))
			return false; // the first one searches for them all
		R.show(mob, null, CMMsg.MSG_OK_ACTION, CMLib.lang().L("<S-NAME> search(es) the area."));
		boolean any = false;
		for(final Prop_Discoverable P : all)
			any = P.tryFind(mob) || any;
		if(any)
			return true;
		if((mob.fetchAbility("Thief_Search") != null) || (mob.fetchAbility("Searching") != null))
			return false; // their own skill may yet find something
		mob.tell(CMLib.lang().L("You find nothing."));
		return true;
	}

	@Override
	public boolean tick(final Tickable ticking, final int tickID)
	{
		if(tickID != Tickable.TICKID_PROPERTY_SPECIAL)
			return super.tick(ticking, tickID);
		if((resetDown > 0) && (--resetDown <= 0))
		{
			this.ticking = false;
			found = false;
			if(affected != null)
				affected.recoverPhyStats();
			return false;
		}
		if(resetDown <= 0)
			this.ticking = false;
		return resetDown > 0;
	}
}
