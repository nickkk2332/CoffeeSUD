package com.planet_ink.coffee_mud.Behaviors;

import java.util.*;
import com.planet_ink.coffee_mud.core.*;
import com.planet_ink.coffee_mud.core.interfaces.*;
import com.planet_ink.coffee_mud.core.collections.*;
import com.planet_ink.coffee_mud.Common.interfaces.*;
import com.planet_ink.coffee_mud.MOBS.interfaces.*;
import com.planet_ink.coffee_mud.Behaviors.interfaces.*;

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
/** Aggression follows live diplomacy, in any area. */
public class ClanEnemyAggressive extends StdBehavior
{
	@Override public String ID()
	{
		return "ClanEnemyAggressive";
	}
	@Override public String accountForYourself()
	{
		return "attacking enemies of its clan";
	}
	@Override protected int canImproveCode()
	{
		return CAN_MOBS;
	}
	@Override public long flags()
	{
		return Behavior.FLAG_POTENTIALLYAGGRESSIVE;
	}
	public boolean enemy(final MOB observer, final MOB target)
	{
		final String own=CMParms.getParmStr(getParms(),"CLAN","");
		final List<String> states=CMParms.parseCommas(CMParms.getParmStr(getParms(),"STATES","WAR,HOSTILE").toUpperCase(),true);
		for(final Pair<Clan,Integer> a : observer.clans())
		{
			if(own.length()>0&&!a.first.name().equalsIgnoreCase(own))
				continue;
			for(final Pair<Clan,Integer> b : target.clans())
				if(a.first!=b.first&&states.contains(Clan.REL_DESCS[CMLib.clans().getCommonClanRelations(a.first,b.first)]))
					return true;
		}
		if(own.length()>0&&observer.getClanRole(own)==null)
		{
			final Clan C=CMLib.clans().findClan(own);
			if(C!=null)
				for(final Pair<Clan,Integer> b : target.clans())
					if(C!=b.first&&states.contains(Clan.REL_DESCS[CMLib.clans().getCommonClanRelations(C,b.first)]))
						return true;
		}
		return false;
	}
	@Override public boolean tick(final Tickable ticking, final int id)
	{
		if(!super.tick(ticking,id)||!(ticking instanceof MOB))
			return true;
		final MOB M=(MOB)ticking;
		if(M.location()==null||M.isInCombat()||!canFreelyBehaveNormal(M)||CMLib.dice().rollPercentage()>CMParms.getParmInt(getParms(),"CHANCE",100))
			return true;
		for(final Enumeration<MOB> e=M.location().inhabitants();e.hasMoreElements();)
		{
			final MOB target=e.nextElement();
			if(target==M||!enemy(M,target))
				continue;
			if(getParms().toUpperCase().contains("CHECKLEVEL")&&target.phyStats().level()>M.phyStats().level()+5)
				continue;
			if(Aggressive.startFight(M,target,true,false,CMParms.getParmStr(getParms(),"MESSAGE",null)))
				break;
		}
		return true;
	}
}
