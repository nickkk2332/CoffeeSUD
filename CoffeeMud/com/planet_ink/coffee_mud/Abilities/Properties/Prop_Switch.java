package com.planet_ink.coffee_mud.Abilities.Properties;
import com.planet_ink.coffee_mud.core.interfaces.*;
import com.planet_ink.coffee_mud.core.*;
import com.planet_ink.coffee_mud.core.collections.*;
import com.planet_ink.coffee_mud.Abilities.interfaces.*;
import com.planet_ink.coffee_mud.Common.interfaces.*;
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
 * I9: a lever, button, crank or dial that keeps its state, on an item or an
 * exit.  VERBS="pull,push,turn,press,flip" (taken before the game's own
 * commands; ACTIVATE and DEACTIVATE too) move it to its next state, or "to
 * x" to that one; STATES="down,up" (or 1-8, a dial); START=; DESC_x= what
 * LOOK adds in state x; MSG_x= what the room sees when it moves to x;
 * ACTION_x= actions (Actions: MPDOOR, MPSIGNAL, MPUNHIDE...) run then;
 * RESET=n ticks before it goes back to START (and runs its action);
 * REQUIRES= (or MASK=) a mask on who moves it, REQMSG= what they are told
 * otherwise; ONCE=true: it moves once.  SWITCH_PROG x runs on its scripts
 * and its room's; SWITCHSTATE(thing) reads it.  The state is kept in the
 * text (STATE=), so saves keep it.
 */
public class Prop_Switch extends Property implements VerbTaker
{
	@Override
	public String ID()
	{
		return "Prop_Switch";
	}

	@Override
	public String name()
	{
		return "A switch";
	}

	@Override
	protected int canAffectCode()
	{
		return Ability.CAN_ITEMS | Ability.CAN_EXITS;
	}

	@Override
	public String accountForYourself()
	{
		return "Moves between " + CMParms.toListString(states.iterator());
	}

	protected String				base		= "";
	protected List<String>			verbs		= new ArrayList<String>();
	protected List<String>			states		= new ArrayList<String>();
	protected String				start		= "";
	protected String				state		= "";
	protected int					resetTicks	= 0;
	protected int					resetDown	= 0;
	protected boolean				once		= false;
	protected boolean				moved		= false;
	protected String				reqMsg		= "";
	protected MaskingLibrary.CompiledZMask	requires	= null;
	protected Map<String, String>	parms		= new HashMap<String, String>();
	protected boolean				ticking		= false;

	/**
	 * The states of a STATES= list: words, or a range of numbers.
	 * @param spec the list
	 * @return the states
	 */
	public static List<String> statesOf(final String spec)
	{
		final List<String> list = new ArrayList<String>();
		final String s = spec.trim();
		final java.util.regex.Matcher m = java.util.regex.Pattern.compile("^(\\d+)\\s*-\\s*(\\d+)$").matcher(s);
		if(m.matches())
		{
			final int a = CMath.s_int(m.group(1)), b = CMath.s_int(m.group(2));
			for(int i = Math.min(a, b); (i <= Math.max(a, b)) && (list.size() < 100); i++)
				list.add("" + i);
		}
		else
		{
			for(final String w : CMParms.parseCommas(s, true))
				list.add(w.trim().toLowerCase());
		}
		return list;
	}

	@Override
	public void setMiscText(final String newText)
	{
		super.setMiscText(newText);
		final String t = (newText == null) ? "" : newText;
		parms = CMParms.parseExactParms(t);
		verbs.clear();
		for(final String v : CMParms.parseCommas(CMParms.getParmStr(parms, "VERBS", "pull,push,turn,press,flip"), true))
			verbs.add(v.trim().toLowerCase());
		states = statesOf(CMParms.getParmStr(parms, "STATES", "down,up"));
		if(states.isEmpty())
			states.add("off");
		start = CMParms.getParmStr(parms, "START", states.get(0)).toLowerCase();
		if(!states.contains(start))
			start = states.get(0);
		final String kept = CMParms.getParmStr(parms, "STATE", "").toLowerCase();
		state = states.contains(kept) ? kept : start;
		moved = CMParms.getParmBool(parms, "MOVED", false);
		resetTicks = Math.max(0, CMParms.getParmInt(parms, "RESET", 0));
		once = CMParms.getParmBool(parms, "ONCE", false);
		String req = CMParms.getParmStr(parms, "REQUIRES", "").trim();
		if(req.length() == 0)
			req = CMParms.getParmStr(parms, "MASK", "").trim();
		requires = (req.length() == 0) ? null : CMLib.masking().getPreCompiledMask(req);
		reqMsg = CMParms.getParmStr(parms, "REQMSG", "It will not move.");
		base = t.replaceAll("(?i)\\s*\\bSTATE=\\S*", "").replaceAll("(?i)\\s*\\bMOVED=\\S*", "").trim();
		ScriptEvents.propVerbsSeen();
	}

	@Override
	public String text()
	{
		// its state goes with it into saves and world files
		if(state.equals(start) && (!moved))
			return base;
		return base + " STATE=" + state + (moved ? " MOVED=true" : "");
	}

	/**
	 * @return its state
	 */
	public String state()
	{
		return state;
	}

	@Override
	public String getStat(final String code)
	{
		if(code.equalsIgnoreCase("STATE"))
			return state;
		return super.getStat(code);
	}

	@Override
	public void setStat(final String code, final String val)
	{
		if(code.equalsIgnoreCase("STATE"))
		{
			if(states.contains(val.toLowerCase()))
				state = val.toLowerCase();
		}
		else
			super.setStat(code, val);
	}

	public void reset(final MOB actor)
	{
		CMLib.threads().deleteTick(this, Tickable.TICKID_PROPERTY_SPECIAL);
		ticking=false;
		resetDown=0;
		moved=false;
		moveTo(actor,start,"reset");
	}

	protected String parm(final String key, final String st)
	{
		return CMParms.getParmStr(parms, key + "_" + st, "");
	}

	/**
	 * Moves it to a state: its message, its action, SWITCH_PROG.
	 * @param mob who moved it, or null
	 * @param to the state
	 * @param verb the word they used, for the default message
	 */
	public void moveTo(final MOB mob, final String to, final String verb)
	{
		final Physical host = affected;
		if((host == null) || (!states.contains(to)))
			return;
		state = to;
		final Room R = (mob != null) ? mob.location() : CMLib.map().roomLocation(host);
		String msg = parm("MSG", to);
		if((msg.length() == 0) && (mob != null))
			msg = "<S-NAME> " + verb.toLowerCase() + "(s) <T-NAME>.";
		if((R != null) && (msg.length() > 0))
		{
			if(mob != null)
				R.show(mob, host, CMMsg.MSG_OK_ACTION, msg);
			else
				R.showHappens(CMMsg.MSG_OK_VISUAL, CMStrings.capitalizeFirstLetter(msg.replace("<T-NAME>", host.name()).replace("<T-NAMESELF>", host.name())));
		}
		final String action = parm("ACTION", to);
		if((action.length() > 0) && ((mob != null) || (R != null)))
		{
			final MOB actor = (mob != null) ? mob : CMLib.map().getFactoryMOB(R);
			try
			{
				Actions.run((host instanceof PhysicalAgent) ? (PhysicalAgent)host : null, actor, action);
			}
			finally
			{
				if(mob == null)
					actor.destroy();
			}
		}
		ScriptEvents.switched((host instanceof PhysicalAgent) ? (PhysicalAgent)host : null, R, to, mob);
		if((resetTicks > 0) && (!to.equals(start)))
		{
			resetDown = resetTicks;
			if(!ticking)
			{
				ticking = true;
				CMLib.threads().startTickDown(this, Tickable.TICKID_PROPERTY_SPECIAL, 1);
			}
		}
	}

	@Override
	public boolean takeVerb(final MOB mob, final PhysicalAgent host, final String verb, final String rest)
	{
		if(host != affected)
			return false;
		final String v = verb.toLowerCase();
		final boolean act = v.equals("activate") || v.equals("deactivate");
		if((!verbs.contains(v)) && (!act))
			return false;
		// the line names it: "pull lever", "turn dial to 3"
		String name = rest;
		String to = null;
		final java.util.regex.Matcher m = java.util.regex.Pattern.compile("(?i)^(.*?)\\s+to\\s+(\\S+)$").matcher(rest.trim());
		if(m.matches())
		{
			name = m.group(1);
			to = m.group(2).toLowerCase();
		}
		if(!ScriptEvents.names(host, name.trim()))
			return false;
		if(act)
			to = v.equals("activate") ? states.get(Math.min(1, states.size() - 1)) : states.get(0);
		if((to != null) && (!states.contains(to)))
		{
			mob.tell(CMLib.lang().L("@x1 has no '@x2': @x3.", host.name(mob), to, CMParms.toListString(states.iterator())));
			return true;
		}
		if((once && moved) || ((requires != null) && (!CMLib.masking().maskCheck(requires, mob, true))))
		{
			mob.tell(mob, host, null, reqMsg);
			return true;
		}
		if(to == null)
			to = states.get((states.indexOf(state) + 1) % states.size());
		if(to.equals(state))
		{
			mob.tell(CMLib.lang().L("@x1 is already @x2.", CMStrings.capitalizeFirstLetter(host.name(mob)), to));
			return true;
		}
		moved = true;
		moveTo(mob, to, verb);
		return true;
	}

	@Override
	public void executeMsg(final Environmental myHost, final CMMsg msg)
	{
		super.executeMsg(myHost, msg);
		if((msg.target() == affected) && (affected != null)
		&& ((msg.targetMinor() == CMMsg.TYP_LOOK) || (msg.targetMinor() == CMMsg.TYP_EXAMINE)))
		{
			final String d = parm("DESC", state);
			if(d.length() > 0)
				msg.addTrailerMsg(CMClass.getMsg(msg.source(), affected, null, CMMsg.MSG_OK_VISUAL, d, CMMsg.NO_EFFECT, null, CMMsg.NO_EFFECT, null));
		}
	}

	@Override
	public boolean tick(final Tickable ticking, final int tickID)
	{
		if(tickID != Tickable.TICKID_PROPERTY_SPECIAL)
			return super.tick(ticking, tickID);
		if((resetDown > 0) && (--resetDown <= 0))
		{
			this.ticking = false;
			if(!state.equals(start))
				moveTo(null, start, "");
			return false;
		}
		if(resetDown <= 0)
			this.ticking = false;
		return resetDown > 0;
	}
}
