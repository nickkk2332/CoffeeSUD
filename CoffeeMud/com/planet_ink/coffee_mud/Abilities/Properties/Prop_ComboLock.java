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
 * I16: a combination lock on an exit or a container: CODE=7-3-9 (or
 * sun-moon-star, or a word: 739; RANDOM rolls one for each new game, kept
 * here and in the script variable VAR= of the host COMBO); SYMBOLS=0-9 or
 * "sun,moon,star" (each dial's); DIALS= how many (the code's); VERB=turn:
 * TURN DIAL 2 TO 3, SET DIALS 7 3 9, ENTER 739 (ON thing when there are
 * more); SHOW=true: LOOK reads the dials; TRIES=3 wrong entries, then
 * ONWRONG= actions; ONOPEN= actions; RELOCK=n ticks after it opens; MSG=
 * when it opens.  The dials are kept in the text (READ=), as is the code
 * that was rolled (GOT=).
 */
public class Prop_ComboLock extends Property implements VerbTaker
{
	@Override
	public String ID()
	{
		return "Prop_ComboLock";
	}

	@Override
	public String name()
	{
		return "A combination lock";
	}

	@Override
	protected int canAffectCode()
	{
		return Ability.CAN_ITEMS | Ability.CAN_EXITS;
	}

	@Override
	public String accountForYourself()
	{
		return "A lock with " + dials + " dials";
	}

	protected String		base		= "";
	protected List<String>	symbols		= new ArrayList<String>();
	protected List<String>	code		= new ArrayList<String>();
	protected String[]		read		= new String[0];
	protected int			dials		= 3;
	protected String		verb		= "turn";
	protected boolean		show		= false;
	protected int			tries		= 0;
	protected int			wrong		= 0;
	protected int			relock		= 0;
	protected int			relockDown	= 0;
	protected boolean		random		= false;
	protected String		got			= "";
	protected String		varName		= "";
	protected Map<String, String> parms	= new HashMap<String, String>();
	protected boolean		ticking		= false;
	protected Room			openedIn	= null;

	/** a code's parts: dashes or spaces or commas between, or one character each */
	protected List<String> parts(final String s)
	{
		final List<String> list = new ArrayList<String>();
		final String t = s.trim().toLowerCase();
		if(t.matches(".*[-,\\s].*"))
		{
			for(final String w : t.split("[-,\\s]+"))
			{
				if(w.length() > 0)
					list.add(w);
			}
		}
		else
		{
			// a word of one-character symbols, or one symbol
			boolean single = true;
			for(final String sym : symbols)
				single = single && (sym.length() == 1);
			if(single)
			{
				for(final char c : t.toCharArray())
					list.add("" + c);
			}
			else
				list.add(t);
		}
		return list;
	}

	@Override
	public void setMiscText(final String newText)
	{
		super.setMiscText(newText);
		final String t = (newText == null) ? "" : newText;
		parms = CMParms.parseExactParms(t);
		symbols = Prop_Switch.statesOf(CMParms.getParmStr(parms, "SYMBOLS", "0-9"));
		verb = CMParms.getParmStr(parms, "VERB", "turn").toLowerCase();
		show = CMParms.getParmBool(parms, "SHOW", false);
		tries = Math.max(0, CMParms.getParmInt(parms, "TRIES", 0));
		relock = Math.max(0, CMParms.getParmInt(parms, "RELOCK", 0));
		varName = CMParms.getParmStr(parms, "VAR", "").trim();
		final String c = CMParms.getParmStr(parms, "CODE", "").trim();
		random = c.equalsIgnoreCase("RANDOM");
		got = CMParms.getParmStr(parms, "GOT", "").trim();
		dials = CMParms.getParmInt(parms, "DIALS", 0);
		if(random)
		{
			if(dials <= 0)
				dials = 3;
			if(parts(got).size() != dials)
			{
				final StringBuilder g = new StringBuilder();
				for(int i = 0; i < dials; i++)
					g.append((i > 0) ? "-" : "").append(symbols.get(CMLib.dice().roll(1, symbols.size(), -1)));
				got = g.toString();
			}
			code = parts(got);
		}
		else
			code = parts(c);
		if(dials <= 0)
			dials = Math.max(1, code.size());
		read = new String[dials];
		final List<String> kept = parts(CMParms.getParmStr(parms, "READ", ""));
		for(int i = 0; i < dials; i++)
			read[i] = ((i < kept.size()) && symbols.contains(kept.get(i))) ? kept.get(i) : symbols.get(0);
		if(random && (varName.length() > 0))
		{
			final ScriptingEngine S = (ScriptingEngine)CMClass.getCommon("DefaultScriptingEngine");
			S.setVar("COMBO", varName.toUpperCase(), got);
		}
		base = t.replaceAll("(?i)\\s*\\bREAD=\\S*", "").replaceAll("(?i)\\s*\\bGOT=\\S*", "").trim();
		ScriptEvents.propVerbsSeen();
	}

	/**
	 * @return what the dials read, dashes between
	 */
	public String reading()
	{
		return CMParms.combineWith(Arrays.asList(read), '-');
	}

	/**
	 * @return the code, dashes between
	 */
	public String code()
	{
		return CMParms.combineWith(code, '-');
	}

	@Override
	public String text()
	{
		return base + " READ=" + reading() + (random ? (" GOT=" + got) : "");
	}

	@Override
	public String getStat(final String code)
	{
		if(code.equalsIgnoreCase("READ"))
			return reading();
		if(code.equalsIgnoreCase("CODE"))
			return code();
		return super.getStat(code);
	}

	protected boolean locked()
	{
		if(affected instanceof Exit)
			return ((Exit)affected).isLocked() || (!((Exit)affected).isOpen());
		if(affected instanceof Container)
			return ((Container)affected).isLocked() || (!((Container)affected).isOpen());
		return false;
	}

	protected boolean right()
	{
		if(code.size() != read.length)
			return false;
		for(int i = 0; i < read.length; i++)
		{
			if(!read[i].equalsIgnoreCase(code.get(i)))
				return false;
		}
		return true;
	}

	protected void opens(final MOB mob)
	{
		final Room R = mob.location();
		if((R == null) || (!locked()))
			return;
		openedIn = R;
		final String m = CMParms.getParmStr(parms, "MSG", "");
		Doors.change(R, affected, "OPEN", (m.length() == 0) ? CMLib.lang().L("<T-NAME> clicks and swings open.") : m);
		Actions.run((affected instanceof PhysicalAgent) ? (PhysicalAgent)affected : null, mob, CMParms.getParmStr(parms, "ONOPEN", ""));
		wrong = 0;
		if(relock > 0)
		{
			relockDown = relock;
			if(!ticking)
			{
				ticking = true;
				CMLib.threads().startTickDown(this, Tickable.TICKID_PROPERTY_SPECIAL, 1);
			}
		}
	}

	protected void wrongTry(final MOB mob)
	{
		mob.tell(CMLib.lang().L("Nothing happens."));
		if((tries > 0) && (++wrong >= tries))
		{
			wrong = 0;
			Actions.run((affected instanceof PhysicalAgent) ? (PhysicalAgent)affected : null, mob, CMParms.getParmStr(parms, "ONWRONG", ""));
		}
	}

	@Override
	public boolean takeVerb(final MOB mob, final PhysicalAgent host, final String v, final String rest)
	{
		if((host != affected) || (mob.location() == null))
			return false;
		final String w = v.toLowerCase();
		String line = rest.trim();
		// ON thing: this one only
		final java.util.regex.Matcher on = java.util.regex.Pattern.compile("(?i)^(.*)\\s+on\\s+(.+)$").matcher(line);
		if(on.matches())
		{
			if(!ScriptEvents.names(host, on.group(2).trim()))
				return false;
			line = on.group(1).trim();
		}
		final String low = line.toLowerCase();
		if(w.equals(verb) && low.matches("^dial\\s+\\S+\\s+to\\s+\\S+$"))
		{
			final String[] p = low.split("\\s+");
			final int n = CMath.s_int(p[1]);
			if((n < 1) || (n > dials) || (!symbols.contains(p[3])))
			{
				mob.tell(CMLib.lang().L("The dials are 1 to @x1, and read @x2.", "" + dials, CMParms.toListString(symbols.iterator())));
				return true;
			}
			read[n - 1] = p[3];
			mob.location().show(mob, host, CMMsg.MSG_OK_ACTION, CMLib.lang().L("<S-NAME> turn(s) a dial on <T-NAME>."));
			mob.tell(CMLib.lang().L("The dials read @x1.", reading()));
			if(right())
				opens(mob);
			return true;
		}
		final boolean set = w.equals("set") && low.startsWith("dials ");
		final boolean enter = w.equals("enter") && (low.length() > 0) && (!low.startsWith("dial"));
		if((!set) && (!enter))
			return false;
		final List<String> entered = parts(set ? low.substring(6) : low);
		if(entered.size() != dials)
		{
			if(enter && (!on.matches()))
				return false; // ENTER of something else
			mob.tell(CMLib.lang().L("It takes @x1.", "" + dials));
			return true;
		}
		for(final String e : entered)
		{
			if(!symbols.contains(e))
			{
				if(enter && (!on.matches()))
					return false;
				mob.tell(CMLib.lang().L("The dials read @x1.", CMParms.toListString(symbols.iterator())));
				return true;
			}
		}
		for(int i = 0; i < dials; i++)
			read[i] = entered.get(i);
		mob.location().show(mob, host, CMMsg.MSG_OK_ACTION, set ? CMLib.lang().L("<S-NAME> set(s) the dials of <T-NAME>.") : CMLib.lang().L("<S-NAME> enter(s) a code on <T-NAME>."));
		if(right())
			opens(mob);
		else
			wrongTry(mob);
		return true;
	}

	@Override
	public void executeMsg(final Environmental myHost, final CMMsg msg)
	{
		super.executeMsg(myHost, msg);
		if(show && (msg.target() == affected) && (affected != null)
		&& ((msg.targetMinor() == CMMsg.TYP_LOOK) || (msg.targetMinor() == CMMsg.TYP_EXAMINE)))
			msg.addTrailerMsg(CMClass.getMsg(msg.source(), affected, null, CMMsg.MSG_OK_VISUAL, CMLib.lang().L("The dials read @x1.", reading()), CMMsg.NO_EFFECT, null, CMMsg.NO_EFFECT, null));
	}

	@Override
	public boolean tick(final Tickable ticking, final int tickID)
	{
		if(tickID != Tickable.TICKID_PROPERTY_SPECIAL)
			return super.tick(ticking, tickID);
		if((relockDown > 0) && (--relockDown <= 0))
		{
			this.ticking = false;
			if((openedIn != null) && (affected != null))
				Doors.change(openedIn, affected, "LOCK", CMLib.lang().L("<T-NAME> swings shut and locks."));
			return false;
		}
		if(relockDown <= 0)
			this.ticking = false;
		return relockDown > 0;
	}
}
