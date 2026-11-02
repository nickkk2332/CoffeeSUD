package com.planet_ink.coffee_mud.Commands;
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
   Copyright 2004-2025 Bo Zimmerman

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
public class Hire extends StdCommand
{
	public Hire()
	{
	}

	private final String[] access=I(new String[]{"HIRE"});
	@Override
	public String[] getAccessWords()
	{
		return access;
	}

	@Override
	public boolean execute(final MOB mob, final List<String> commands, final int metaFlags)
		throws java.io.IOException
	{
		// N21: HIRE <npc> PAY: its price, given
		final boolean pay=(commands.size()>2)&&commands.get(commands.size()-1).equalsIgnoreCase("PAY");
		final String rest=pay?CMParms.combine(commands.subList(0,commands.size()-1),1):CMParms.combine(commands,1);
		if(pay)
			return payHireling(mob,rest);
		Environmental target=mob.location().fetchFromRoomFavorMOBs(null,rest);
		if((target!=null)&&(!target.name().equalsIgnoreCase(rest))&&(rest.length()<4))
			target=null;
		if((target!=null)&&(!CMLib.flags().canBeSeenBy(target,mob)))
			target=null;
		CMMsg msg=null;
		if(target==null)
			msg=CMClass.getMsg(mob,null,null,CMMsg.MSG_SPEAK,L("^T<S-NAME> say(s) 'I'm looking to hire some help.'^?"));
		else
			msg=CMClass.getMsg(mob,target,null,CMMsg.MSG_SPEAK,L("^T<S-NAME> say(s) to <T-NAMESELF> 'Are you for hire?'^?"));
		if(mob.location().okMessage(mob,msg))
			mob.location().send(mob,msg);
		// N21: a hireling's terms, as a menu
		final Behavior B=(target instanceof MOB)?((MOB)target).fetchBehavior("Hireling"):null;
		if((B!=null)&&(B.getStat("WORKING").length()==0))
		{
			final MOB H=(MOB)target;
			final ScriptMenus.Menu M=new ScriptMenus.Menu();
			M.prompt=L("@x1 asks @x2 to work @x3.",H.name(mob),CMLib.beanCounter().nameCurrencyShort(H,CMath.s_double(B.getStat("PRICE"))),B.getStat("TERMS"));
			M.who=mob;
			M.dflt="NO";
			final ScriptMenus.Option yes=new ScriptMenus.Option();
			yes.key="HIRE";
			yes.label=L("Hire @x1",H.name(mob));
			yes.command="HIRE \""+H.Name()+"\" PAY";
			final ScriptMenus.Option no=new ScriptMenus.Option();
			no.key="NO";
			no.label=L("Not now");
			no.command="";
			M.options.add(yes);
			M.options.add(no);
			ScriptMenus.open(M,mob.session());
		}
		return false;
	}

	/**
	 * N21: pays a hireling here its price, as GIVE would.
	 * @param mob the one hiring
	 * @param name the hireling's name
	 * @return false
	 */
	protected boolean payHireling(final MOB mob, final String name)
	{
		final Room R=mob.location();
		final MOB H=(R==null)?null:R.fetchInhabitant(name);
		final Behavior B=(H==null)?null:H.fetchBehavior("Hireling");
		if(B==null)
		{
			mob.tell(L("There is no hireling '@x1' here.",name));
			return false;
		}
		final double price=CMath.s_double(B.getStat("PRICE"));
		final String currency=CMLib.beanCounter().getCurrency(H);
		if(CMLib.beanCounter().getTotalAbsoluteValue(mob,currency)<price)
		{
			mob.tell(L("You can't afford @x1.",CMLib.beanCounter().nameCurrencyShort(H,price)));
			return false;
		}
		CMLib.beanCounter().subtractMoney(mob,currency,price);
		final Coins C=CMLib.beanCounter().makeBestCurrency(currency,price);
		if(C==null)
			return false;
		mob.addItem(C);
		final CMMsg msg=CMClass.getMsg(mob,H,C,CMMsg.MSG_GIVE,L("<S-NAME> give(s) <O-NAME> to <T-NAMESELF>."));
		if(R.okMessage(mob,msg))
			R.send(mob,msg);
		else
			CMLib.beanCounter().addMoney(mob,currency,price);
		return false;
	}

	@Override
	public double combatActionsCost(final MOB mob, final List<String> cmds)
	{
		return CMProps.getCommandCombatActionCost(ID());
	}

	@Override
	public double actionsCost(final MOB mob, final List<String> cmds)
	{
		return CMProps.getCommandActionCost(ID());
	}

	@Override
	public boolean canBeOrdered()
	{
		return true;
	}

}
