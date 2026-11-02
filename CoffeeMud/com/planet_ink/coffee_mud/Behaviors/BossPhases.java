package com.planet_ink.coffee_mud.Behaviors;
import com.planet_ink.coffee_mud.core.interfaces.*;
import com.planet_ink.coffee_mud.core.*;
import com.planet_ink.coffee_mud.core.collections.*;
import com.planet_ink.coffee_mud.Abilities.interfaces.*;
import com.planet_ink.coffee_mud.Behaviors.interfaces.*;
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
 * C3: a boss's phases, each once a fight.  Statements, ; between them:
 * TRIGGER: action, action...  Triggers: ONENGAGE, AT=n% (its hit points
 * under n%), AT=ROUND n, AT=ADDSDEAD (all it summoned are dead), ONDEATH.
 * Actions: SAY text, EMOTE text, YELL text, CAST ability, SUMMON catalog
 * name xN, BEHAVE behaviour [parms], UNBEHAVE behaviour, AFFECT ability
 * ["parms"] [ticks], REMOVE ability, IMMUNE ticks, HEAL n%, MOVETO room,
 * ROOM EMOTE text, ROOM AFFECT ability [ticks], CALL function.  Each AT=
 * that fires starts the next phase (from 1); PHASE_PROG n runs on its
 * scripts and PHASE($i) reads it.  RESET=FULLHEAL (the default: when it is
 * whole again out of a fight), LEAVECOMBAT, LEASH (EncounterReset) or NEVER
 * says when the phases rearm; its summons go with the fight.  The phase is
 * kept in a saved game, and set back to 1 when it dies.
 */
public class BossPhases extends StdBehavior
{
	@Override
	public String ID()
	{
		return "BossPhases";
	}

	@Override
	public String name()
	{
		return "a boss's phases";
	}

	@Override
	protected int canImproveCode()
	{
		return Behavior.CAN_MOBS;
	}

	@Override
	public String accountForYourself()
	{
		return "fighting in phases";
	}

	/** one trigger and what it does */
	protected static final class Phase
	{
		String			trigger	= "";
		int				pct		= 0;
		int				roundAt	= 0;
		List<String>	actions	= new ArrayList<String>();
	}

	protected final List<Phase>		phases		= new ArrayList<Phase>();
	protected String				reset		= "FULLHEAL";

	// the fight
	protected final Set<Integer>	fired		= new HashSet<Integer>();
	protected int					phase		= 1;
	protected int					round		= 0;
	protected boolean				engaged		= false;
	protected int					immuneFor	= 0;
	protected final List<MOB>		summoned	= new ArrayList<MOB>();
	protected boolean				everSummoned= false;

	@Override
	public void setParms(final String newParms)
	{
		super.setParms(newParms);
		phases.clear();
		reset = "FULLHEAL";
		final String p = (newParms == null) ? "" : newParms;
		for(final String st : splitOutside(p, ';'))
		{
			final String s = st.trim();
			if(s.length() == 0)
				continue;
			if(s.toUpperCase().startsWith("RESET="))
			{
				reset = s.substring(6).trim().toUpperCase();
				continue;
			}
			final int x = s.indexOf(':');
			if(x < 0)
				continue;
			final Phase P = new Phase();
			final String t = s.substring(0, x).trim().toUpperCase().replace(" ", "");
			P.trigger = t;
			if(t.startsWith("AT=ROUND"))
			{
				P.trigger = "ROUND";
				P.roundAt = CMath.s_int(t.substring(8));
			}
			else
			if(t.equals("AT=ADDSDEAD"))
				P.trigger = "ADDSDEAD";
			else
			if(t.startsWith("AT="))
			{
				P.trigger = "HP";
				P.pct = CMath.s_int(t.substring(3).replace("%", ""));
			}
			for(final String a : splitOutside(s.substring(x + 1), ','))
			{
				if(a.trim().length() > 0)
					P.actions.add(a.trim());
			}
			phases.add(P);
		}
		rearm(null);
	}

	/** splits at a character, not inside quotes */
	protected static List<String> splitOutside(final String s, final char c)
	{
		final List<String> list = new ArrayList<String>();
		final StringBuilder cur = new StringBuilder();
		boolean q = false;
		for(int i = 0; i < s.length(); i++)
		{
			final char ch = s.charAt(i);
			if(ch == '"')
				q = !q;
			if((ch == c) && !q)
			{
				list.add(cur.toString());
				cur.setLength(0);
			}
			else
				cur.append(ch);
		}
		list.add(cur.toString());
		return list;
	}

	protected static String unquote(final String s)
	{
		final String t = s.trim();
		if((t.length() > 1) && t.startsWith("\"") && t.endsWith("\""))
			return t.substring(1, t.length() - 1);
		return t;
	}

	/** the phases ready again; its summons gone */
	protected void rearm(final MOB mob)
	{
		fired.clear();
		phase = 1;
		round = 0;
		engaged = false;
		immuneFor = 0;
		for(final MOB M : summoned)
		{
			if((M != null) && (!M.amDestroyed()))
			{
				if((M.location() != null) && (!M.amDead()))
					M.location().show(M, null, CMMsg.MSG_OK_ACTION, CMLib.lang().L("<S-NAME> fade(s) away."));
				M.destroy();
			}
		}
		summoned.clear();
		everSummoned = false;
	}

	/** does one action */
	protected void act(final MOB mob, final String action)
	{
		final Room R = mob.location();
		if(R == null)
			return;
		final String up = action.toUpperCase();
		final List<String> w = CMParms.parse(action);
		final String rest = (w.size() > 1) ? action.substring(action.indexOf(' ') + 1).trim() : "";
		if(up.startsWith("SAY "))
			CMLib.commands().postSay(mob, null, unquote(rest), false, false);
		else
		if(up.startsWith("EMOTE "))
			R.show(mob, null, CMMsg.MSG_OK_ACTION, "<S-NAME> " + unquote(rest));
		else
		if(up.startsWith("YELL "))
			mob.doCommand(new XVector<String>("YELL", unquote(rest)), MUDCmdProcessor.METAFLAG_FORCED);
		else
		if(up.startsWith("ROOM EMOTE "))
			R.showHappens(CMMsg.MSG_OK_ACTION, unquote(rest.substring(6).trim()));
		else
		if(up.startsWith("ROOM AFFECT "))
		{
			final List<String> a = CMParms.parse(rest.substring(7).trim());
			final Ability A = (a.size() > 0) ? CMClass.getAbility(a.get(0)) : null;
			if(A != null)
			{
				final int ticks = (a.size() > 1) ? CMath.s_int(a.get(a.size() - 1)) : 0;
				if((a.size() > 2) || ((a.size() == 2) && (!CMath.isInteger(a.get(1)))))
					A.setMiscText(unquote(a.get(1)));
				if(ticks > 0)
					A.startTickDown(mob, R, ticks);
				else
					R.addNonUninvokableEffect(A);
			}
		}
		else
		if(up.startsWith("CAST "))
		{
			final String id = (w.size() > 1) ? w.get(1) : "";
			Ability A = mob.fetchAbility(id);
			if(A == null)
			{
				A = CMClass.getAbility(id);
				if(A == null)
					return;
				A.setSavable(false);
				mob.addAbility(A);
			}
			A.setProficiency(100);
			final MOB target = (A.castingQuality(mob, mob) == Ability.QUALITY_BENEFICIAL_SELF) || (mob.getVictim() == null) ? mob : mob.getVictim();
			A.invoke(mob, new XVector<String>(target.name()), target, false, 0);
		}
		else
		if(up.startsWith("SUMMON "))
		{
			String name = rest;
			int n = 1;
			final java.util.regex.Matcher m = java.util.regex.Pattern.compile("(?i)^(.*?)\\s+x\\s*(\\d+)$").matcher(name);
			if(m.matches())
			{
				name = m.group(1).trim();
				n = Math.max(1, Math.min(20, CMath.s_int(m.group(2))));
			}
			if(name.toUpperCase().startsWith("CATALOG:"))
				name = name.substring(8).trim();
			name = unquote(name);
			final ScriptLib.CatalogLoad L = new ScriptLib.CatalogLoad();
			L.name = name;
			L.count = n;
			L.force = true;
			final List<MOB> made = new ArrayList<MOB>();
			for(final Physical P : ScriptLib.catalogCopies(L, true))
			{
				if(P instanceof MOB)
					made.add((MOB)P);
			}
			for(int i = made.size(); i < n; i++)
			{
				final MOB M = CMClass.getMOB(name);
				if(M == null)
					break;
				made.add(M);
			}
			for(final MOB M : made)
			{
				M.addTag("SUMMONED");
				M.setStartRoom(null);
				M.bringToLife(R, true);
				M.setFollowing(mob);
				summoned.add(M);
				everSummoned = true;
				if(mob.getVictim() != null)
					CMLib.combat().postAttack(M, mob.getVictim(), M.fetchWieldedItem());
			}
		}
		else
		if(up.startsWith("BEHAVE "))
		{
			final String id = (w.size() > 1) ? w.get(1) : "";
			if(mob.fetchBehavior(id) == null)
			{
				final Behavior B = CMClass.getBehavior(id);
				if(B != null)
				{
					B.setParms(unquote(CMParms.combine(w, 2)));
					mob.addBehavior(B);
				}
			}
		}
		else
		if(up.startsWith("UNBEHAVE "))
		{
			final Behavior B = mob.fetchBehavior((w.size() > 1) ? w.get(1) : "");
			if(B != null)
				mob.delBehavior(B);
		}
		else
		if(up.startsWith("AFFECT "))
		{
			final Ability A = CMClass.getAbility((w.size() > 1) ? w.get(1) : "");
			if(A == null)
				return;
			int ticks = 0;
			if((w.size() > 2) && CMath.isInteger(w.get(w.size() - 1)))
				ticks = CMath.s_int(w.get(w.size() - 1));
			if((w.size() > 2) && (!CMath.isInteger(w.get(2))))
				A.setMiscText(unquote(w.get(2)));
			if(mob.fetchEffect(A.ID()) != null)
				mob.delEffect(mob.fetchEffect(A.ID()));
			if(ticks > 0)
				A.startTickDown(mob, mob, ticks);
			else
			{
				A.setSavable(false);
				mob.addNonUninvokableEffect(A);
			}
			mob.recoverPhyStats();
			mob.recoverCharStats();
			mob.recoverMaxState();
		}
		else
		if(up.startsWith("REMOVE "))
		{
			final Ability A = mob.fetchEffect((w.size() > 1) ? w.get(1) : "");
			if(A != null)
			{
				A.unInvoke();
				mob.delEffect(A);
				mob.recoverPhyStats();
			}
		}
		else
		if(up.startsWith("IMMUNE "))
			immuneFor = CMath.s_int((w.size() > 1) ? w.get(1) : "0");
		else
		if(up.startsWith("HEAL "))
		{
			final int pct = CMath.s_int(((w.size() > 1) ? w.get(1) : "0").replace("%", ""));
			mob.curState().adjHitPoints((int)Math.round(mob.maxState().getHitPoints() * pct / 100.0), mob.maxState());
		}
		else
		if(up.startsWith("MOVETO "))
		{
			final Room to = CMLib.map().getRoom(unquote(rest));
			if((to != null) && (to != R))
			{
				R.show(mob, null, CMMsg.MSG_OK_ACTION, CMLib.lang().L("<S-NAME> vanish(es)!"));
				final MOB victim = mob.getVictim();
				to.bringMobHere(mob, false);
				to.show(mob, null, CMMsg.MSG_OK_ACTION, CMLib.lang().L("<S-NAME> appear(s)!"));
				mob.setVictim(null);
				if(victim != null)
					victim.makePeace(false);
			}
		}
		else
		if(up.startsWith("CALL "))
		{
			final String fn = (w.size() > 1) ? w.get(1) : "";
			for(final Enumeration<ScriptingEngine> s = mob.scripts(); s.hasMoreElements();)
				s.nextElement().callFunction(mob, fn, "", (mob.getVictim() != null) ? mob.getVictim() : mob);
			for(final Enumeration<Behavior> b = mob.behaviors(); b.hasMoreElements();)
			{
				final Behavior B = b.nextElement();
				if(B instanceof ScriptingEngine)
					((ScriptingEngine)B).callFunction(mob, fn, "", (mob.getVictim() != null) ? mob.getVictim() : mob);
			}
		}
	}

	/** fires a phase once; an AT= one starts the next phase */
	protected void fire(final MOB mob, final int i)
	{
		if(!fired.add(Integer.valueOf(i)))
			return;
		final Phase P = phases.get(i);
		for(final String a : P.actions)
			act(mob, a);
		if(P.trigger.equals("HP") || P.trigger.equals("ROUND") || P.trigger.equals("ADDSDEAD"))
		{
			phase++;
			ScriptEvents.phase(mob, phase);
		}
	}

	@Override
	public boolean tick(final Tickable ticking, final int tickID)
	{
		if(!super.tick(ticking, tickID))
			return false;
		if((tickID != Tickable.TICKID_MOB) || (!(ticking instanceof MOB)))
			return true;
		final MOB mob = (MOB)ticking;
		if(immuneFor > 0)
			immuneFor--;
		if(!mob.isInCombat())
		{
			if(engaged)
			{
				engaged = false;
				if(reset.equals("LEAVECOMBAT"))
					rearm(mob);
			}
			if(reset.equals("FULLHEAL") && (!fired.isEmpty())
			&& (mob.curState().getHitPoints() >= mob.maxState().getHitPoints()))
				rearm(mob);
			return true;
		}
		if(!engaged)
		{
			engaged = true;
			for(int i = 0; i < phases.size(); i++)
			{
				if(phases.get(i).trigger.equals("ONENGAGE"))
					fire(mob, i);
			}
		}
		round++;
		for(final Iterator<MOB> s = summoned.iterator(); s.hasNext();)
		{
			final MOB M = s.next();
			if((M == null) || M.amDestroyed() || M.amDead())
				s.remove();
		}
		final double pct = SupportPicker.hpPct(mob);
		for(int i = 0; i < phases.size(); i++)
		{
			final Phase P = phases.get(i);
			if(fired.contains(Integer.valueOf(i)))
				continue;
			if((P.trigger.equals("HP") && (pct < P.pct))
			|| (P.trigger.equals("ROUND") && (round >= P.roundAt))
			|| (P.trigger.equals("ADDSDEAD") && everSummoned && summoned.isEmpty()))
				fire(mob, i);
		}
		return true;
	}

	@Override
	public boolean okMessage(final Environmental host, final CMMsg msg)
	{
		if(!super.okMessage(host, msg))
			return false;
		if((immuneFor > 0) && (msg.target() == host) && (msg.targetMinor() == CMMsg.TYP_DAMAGE))
			msg.setValue(0); // IMMUNE
		return true;
	}

	@Override
	public void executeMsg(final Environmental host, final CMMsg msg)
	{
		super.executeMsg(host, msg);
		if(!(host instanceof MOB))
			return;
		final MOB mob = (MOB)host;
		if((msg.source() == mob) && ((msg.sourceMinor() == CMMsg.TYP_DEATH) || (msg.othersMinor() == CMMsg.TYP_DEATH)))
		{
			for(int i = 0; i < phases.size(); i++)
			{
				if(phases.get(i).trigger.equals("ONDEATH"))
					fire(mob, i);
			}
			rearm(mob); // back to phase 1 when it comes back
		}
	}

	@Override
	public String getStat(final String code)
	{
		if(code.equalsIgnoreCase("PHASE"))
			return "" + phase;
		return super.getStat(code);
	}

	@Override
	public void setStat(final String code, final String val)
	{
		if(code.equalsIgnoreCase("ENCOUNTERRESET"))
		{
			if(reset.equals("LEASH") || reset.equals("FULLHEAL"))
				rearm(null); // C21's reset
			return;
		}
		super.setStat(code, val);
	}

	@Override
	protected String getRunState()
	{
		if((phase <= 1) && fired.isEmpty())
			return "";
		final StringBuilder f = new StringBuilder("");
		for(final Integer I : fired)
			f.append(f.length() > 0 ? "," : "").append(I);
		return "<PHASES PHASE=\"" + phase + "\" ROUND=\"" + round + "\" FIRED=\"" + f + "\" />";
	}

	@Override
	protected void setRunState(final String xml)
	{
		if((xml == null) || (xml.trim().length() == 0))
			return;
		final List<XMLLibrary.XMLTag> V = CMLib.xml().parseAllXML(xml);
		if((V == null) || V.isEmpty())
			return;
		phase = CMath.s_int(V.get(0).getParmValue("PHASE"));
		round = CMath.s_int(V.get(0).getParmValue("ROUND"));
		fired.clear();
		for(final String s : CMParms.parseCommas(V.get(0).getParmValue("FIRED"), true))
			fired.add(Integer.valueOf(CMath.s_int(s)));
	}
}
