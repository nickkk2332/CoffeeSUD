package com.planet_ink.coffee_mud.Abilities.Properties;
import com.planet_ink.coffee_mud.core.interfaces.*;
import com.planet_ink.coffee_mud.core.*;
import com.planet_ink.coffee_mud.core.collections.*;
import com.planet_ink.coffee_mud.Abilities.interfaces.*;
import com.planet_ink.coffee_mud.Behaviors.interfaces.*;
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
 * I4: properties and behaviours that are there only while a condition
 * holds.  PROPS="Prop_X(args);Behavior_Y(args)" (abilities or behaviours), or
 * ID= with PARMS=; WHEN= a zapper mask; CHECK=HOST (the thing it is on: a
 * room, area, exit, item or mob), OWNER (an item's owner), ROOMPLAYERS (any
 * of the player's side in its room) or TRIGGERER (the last of them to come
 * in); EVERY=n ticks (1) it looks again; CHANCE= percent each time it would
 * come on; COOLDOWN=n ticks or nH game hours before it may come on again
 * after it goes; ONCE=CHAR (once a character, a tattoo), PROFILE (once a
 * profile) or WORLD (once a game, kept in its text); ONSTART= and ONEND=
 * actions (as Prop_Switch's); ELSEMSG= what one who comes in is told while
 * it is off.  What it wraps is added when it comes on and taken off when
 * it goes.
 */
public class Prop_When extends Property
{
	@Override
	public String ID()
	{
		return "Prop_When";
	}

	@Override
	public String name()
	{
		return "Only when";
	}

	@Override
	protected int canAffectCode()
	{
		return Ability.CAN_ROOMS | Ability.CAN_AREAS | Ability.CAN_EXITS | Ability.CAN_ITEMS | Ability.CAN_MOBS;
	}

	@Override
	public String accountForYourself()
	{
		return "Something here comes and goes";
	}

	protected String						base		= "";
	protected Map<String, String>			parms		= new HashMap<String, String>();
	protected final List<String[]>			wraps		= new ArrayList<String[]>();
	protected MaskingLibrary.CompiledZMask	when		= null;
	protected String						check		= "HOST";
	protected int							every		= 1;
	protected int							down		= 0;
	protected int							chance		= 100;
	protected int							cooldown	= 0;
	protected int							coolLeft	= 0;
	protected String						once		= "";
	protected boolean						spent		= false;
	protected boolean						on			= false;
	protected final List<Ability>			addedA		= new ArrayList<Ability>();
	protected final List<Behavior>			addedB		= new ArrayList<Behavior>();
	protected MOB							triggerer	= null;
	protected boolean						ticking		= false;

	/**
	 * Splits PROPS: semicolons, not inside brackets or quotes; each ID or ID(args).
	 * @param s the list
	 * @return the ID and args of each
	 */
	public static List<String[]> parseWraps(final String s)
	{
		final List<String[]> list = new ArrayList<String[]>();
		int depth = 0;
		boolean q = false;
		final StringBuilder b = new StringBuilder();
		final List<String> parts = new ArrayList<String>();
		for(final char c : s.toCharArray())
		{
			if(c == '"')
				q = !q;
			if(!q)
			{
				if(c == '(')
					depth++;
				else
				if(c == ')')
					depth--;
			}
			if((c == ';') && (depth <= 0) && (!q))
			{
				parts.add(b.toString());
				b.setLength(0);
			}
			else
				b.append(c);
		}
		parts.add(b.toString());
		for(final String raw : parts)
		{
			final String p = raw.trim();
			if(p.length() == 0)
				continue;
			final int x = p.indexOf('(');
			if((x > 0) && p.endsWith(")"))
				list.add(new String[] { p.substring(0, x).trim(), p.substring(x + 1, p.length() - 1) });
			else
				list.add(new String[] { p, "" });
		}
		return list;
	}

	@Override
	public void setMiscText(final String newText)
	{
		super.setMiscText(newText);
		final String t = (newText == null) ? "" : newText;
		parms = CMParms.parseExactParms(t);
		wraps.clear();
		final String id = CMParms.getParmStr(parms, "ID", "").trim();
		if(id.length() > 0)
			wraps.add(new String[] { id, CMParms.getParmStr(parms, "PARMS", "") });
		wraps.addAll(parseWraps(CMParms.getParmStr(parms, "PROPS", "")));
		final String w = CMParms.getParmStr(parms, "WHEN", "").trim();
		when = (w.length() == 0) ? null : CMLib.masking().getPreCompiledMask(w);
		check = CMParms.getParmStr(parms, "CHECK", "HOST").toUpperCase();
		every = Math.max(1, CMParms.getParmInt(parms, "EVERY", 1));
		chance = Math.max(0, Math.min(100, CMParms.getParmInt(parms, "CHANCE", 100)));
		final String cd = CMParms.getParmStr(parms, "COOLDOWN", "0").trim().toUpperCase();
		if(cd.endsWith("H"))
			cooldown = (int)Math.max(1, (CMProps.getMillisPerMudHour() / Math.max(1, CMProps.getTickMillis())) * CMath.s_int(cd.substring(0, cd.length() - 1)));
		else
			cooldown = CMath.s_int(cd);
		coolLeft = CMParms.getParmInt(parms, "COOLLEFT", 0);
		once = CMParms.getParmStr(parms, "ONCE", "").toUpperCase();
		spent = CMParms.getParmBool(parms, "SPENT", false);
		base = t.replaceAll("(?i)\\s*\\bCOOLLEFT=\\S*", "").replaceAll("(?i)\\s*\\bSPENT=\\S*", "").trim();
	}

	@Override
	public String text()
	{
		return base + ((coolLeft > 0) ? (" COOLLEFT=" + coolLeft) : "") + (spent ? " SPENT=true" : "");
	}

	@Override
	public void setAffectedOne(final Physical P)
	{
		super.setAffectedOne(P);
		if((P != null) && (!ticking))
		{
			ticking = true;
			CMLib.threads().startTickDown(this, Tickable.TICKID_PROPERTY_SPECIAL, 1);
		}
	}

	/**
	 * @return whether what it wraps is there now
	 */
	public boolean isOn()
	{
		return on;
	}

	@Override
	public String getStat(final String code)
	{
		if(code.equalsIgnoreCase("ON"))
			return "" + on;
		return super.getStat(code);
	}

	protected Room room()
	{
		return (affected instanceof Room) ? (Room)affected : CMLib.map().roomLocation(affected);
	}

	/** the ones the condition is tested on */
	protected List<Physical> subjects()
	{
		final List<Physical> list = new ArrayList<Physical>();
		if(check.equals("OWNER"))
		{
			if((affected instanceof Item) && (((Item)affected).owner() instanceof MOB))
				list.add((MOB)((Item)affected).owner());
		}
		else
		if(check.equals("ROOMPLAYERS"))
		{
			final Room R = room();
			if(R != null)
			{
				for(final Enumeration<MOB> m = R.inhabitants(); m.hasMoreElements();)
				{
					final MOB M = m.nextElement();
					if((M != null) && GameDifficulty.playerSide(M))
						list.add(M);
				}
			}
		}
		else
		if(check.equals("TRIGGERER"))
		{
			if((triggerer != null) && (triggerer.location() == room()) && (!triggerer.amDestroyed()))
				list.add(triggerer);
		}
		else
		if(affected != null)
			list.add(affected);
		return list;
	}

	/** the mob ONCE=CHAR marks, if there is one */
	protected MOB marked(final List<Physical> subjects)
	{
		for(final Physical P : subjects)
		{
			if(P instanceof MOB)
				return (MOB)P;
		}
		return null;
	}

	protected String onceTattoo()
	{
		return "WHEN:" + Integer.toHexString(base.hashCode()).toUpperCase();
	}

	/**
	 * Whether it should be on now.
	 * @return true if so
	 */
	public boolean holds()
	{
		final List<Physical> subjects = subjects();
		if(subjects.isEmpty())
			return false;
		if(when == null)
			return true;
		for(final Physical P : subjects)
		{
			if(CMLib.masking().maskCheck(when, P, true))
				return true;
		}
		return false;
	}

	protected boolean mayStart(final List<Physical> subjects)
	{
		if(spent || (coolLeft > 0))
			return false;
		final MOB M = marked(subjects);
		if(once.equals("CHAR") && (M != null) && (M.findTattoo(onceTattoo()) != null))
			return false;
		if(once.equals("PROFILE") && (M != null) && (M.playerStats() != null) && (M.playerStats().getAccount() != null)
		&& (M.playerStats().getAccount().findTattoo(onceTattoo()) != null))
			return false;
		return (chance >= 100) || (CMLib.dice().rollPercentage() <= chance);
	}

	protected void start()
	{
		if((affected == null) || on)
			return;
		final List<Physical> subjects = subjects();
		if(!mayStart(subjects))
			return;
		on = true;
		for(final String[] w : wraps)
		{
			final Ability A = CMClass.getAbility(w[0]);
			if(A != null)
			{
				A.setMiscText(w[1]);
				A.setSavable(false);
				affected.addNonUninvokableEffect(A);
				addedA.add(A);
				continue;
			}
			final Behavior B = CMClass.getBehavior(w[0]);
			if((B != null) && (affected instanceof PhysicalAgent))
			{
				B.setParms(w[1]);
				B.setSavable(false);
				((PhysicalAgent)affected).addBehavior(B);
				addedB.add(B);
			}
		}
		affected.recoverPhyStats();
		final MOB M = marked(subjects);
		if(once.equals("WORLD"))
			spent = true;
		else
		if(once.equals("CHAR") && (M != null))
			M.addTattoo(onceTattoo());
		else
		if(once.equals("PROFILE") && (M != null) && (M.playerStats() != null) && (M.playerStats().getAccount() != null))
			M.playerStats().getAccount().addTattoo(onceTattoo());
		run(CMParms.getParmStr(parms, "ONSTART", ""), M);
	}

	protected void end()
	{
		if(!on)
			return;
		on = false;
		for(final Ability A : addedA)
		{
			if(affected != null)
				affected.delEffect(A);
			A.setAffectedOne(null);
		}
		addedA.clear();
		for(final Behavior B : addedB)
		{
			if(affected instanceof PhysicalAgent)
				((PhysicalAgent)affected).delBehavior(B);
		}
		addedB.clear();
		if(affected != null)
			affected.recoverPhyStats();
		if(cooldown > 0)
			coolLeft = cooldown;
		run(CMParms.getParmStr(parms, "ONEND", ""), marked(subjects()));
	}

	protected void run(final String actions, final MOB who)
	{
		if(actions.length() == 0)
			return;
		final Room R = room();
		final MOB M = (who != null) ? who : ((R != null) ? CMLib.map().getFactoryMOB(R) : null);
		if(M == null)
			return;
		try
		{
			Actions.run((affected instanceof PhysicalAgent) ? (PhysicalAgent)affected : R, M, actions);
		}
		finally
		{
			if(M != who)
				M.destroy();
		}
	}

	/**
	 * Looks again now.
	 */
	public void look()
	{
		if(holds())
			start();
		else
			end();
	}

	@Override
	public void executeMsg(final Environmental myHost, final CMMsg msg)
	{
		super.executeMsg(myHost, msg);
		final Room R = room();
		if((R != null) && (msg.targetMinor() == CMMsg.TYP_ENTER) && (msg.target() == R) && GameDifficulty.playerSide(msg.source()))
		{
			triggerer = msg.source();
			if(check.equals("TRIGGERER") || check.equals("ROOMPLAYERS"))
				down = 0; // look on the next tick
			final String m = CMParms.getParmStr(parms, "ELSEMSG", "");
			if((!on) && (m.length() > 0))
				msg.source().tell(m);
		}
	}

	@Override
	public boolean tick(final Tickable ticking, final int tickID)
	{
		if(tickID != Tickable.TICKID_PROPERTY_SPECIAL)
			return super.tick(ticking, tickID);
		if((affected == null) || ((affected instanceof Item) && ((Item)affected).amDestroyed()))
		{
			this.ticking = false;
			return false;
		}
		if((coolLeft > 0) && (!on))
			coolLeft--;
		if(--down > 0)
			return true;
		down = every;
		look();
		return true;
	}
}
