package com.planet_ink.coffee_mud.Behaviors;
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
   Copyright 2001-2025 Bo Zimmerman

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

public class Healer extends ActiveTicker
{
	@Override
	public String ID()
	{
		return "Healer";
	}

	@Override
	protected int canImproveCode()
	{
		return Behavior.CAN_MOBS;
	}

	protected static final List<String> healingVector=new Vector<String>();
	/** C1: heal only those under this percent of their hit points, the most hurt first; 0 for as before */
	protected int healAt=0;
	/** C1: its spells (SPELLS=id,id), or null for the six it always had */
	protected List<String> spells=null;
	/** C1: whom it heals (TARGET=GROUP|MASK:zapper), or null for as before */
	protected SupportPicker.Support healTarget=null;

	@Override
	public void setParms(final String newParms)
	{
		super.setParms(newParms);
		healAt=CMParms.getParmInt(newParms,"HEALAT",0);
		final String sp=CMParms.getParmStr(newParms,"SPELLS","").trim();
		spells=(sp.length()==0)?null:CMParms.parseCommas(sp,true);
		healTarget=(newParms.toUpperCase().indexOf("TARGET=")>=0)?SupportPicker.parse(newParms,"TARGET","GROUP"):null;
	}

	/** C1: a spell of its own, or one given it for the purpose */
	protected Ability spell(final MOB mob, final String id)
	{
		Ability A=mob.fetchAbility(id);
		if(A==null)
		{
			A=CMClass.getAbility(id);
			if(A==null)
				return null;
			A.setSavable(false);
			mob.addAbility(A);
		}
		A.setProficiency(100);
		return A;
	}

	/**
	 * C1: cures first, then a heal for the most hurt (under HEALAT=n), of
	 * those TARGET= names (or, as before, those a player leads).
	 * @param mob the healer
	 * @param R where it is
	 */
	protected void healNeediest(final MOB mob, final Room R)
	{
		final List<MOB> who;
		if(healTarget!=null)
			who=SupportPicker.allies(mob,healTarget);
		else
		{
			if(R.numPCInhabitants()==0)
				return;
			who=new ArrayList<MOB>();
			for(final Enumeration<MOB> m=R.inhabitants();m.hasMoreElements();)
			{
				final MOB M=m.nextElement();
				if((M==null)||(M==mob)||M.amDead())
					continue;
				final MOB followMOB=M.getGroupLeader();
				if((followMOB.getVictim()==mob)||(followMOB.isMonster()))
					continue;
				who.add(M);
			}
			Collections.sort(who,new Comparator<MOB>()
			{
				@Override
				public int compare(final MOB a, final MOB b)
				{
					return Double.compare(SupportPicker.hpPct(a),SupportPicker.hpPct(b));
				}
			});
		}
		final List<Ability> skills=new ArrayList<Ability>();
		final List<String> cures=new ArrayList<String>();
		for(final String id : (spells!=null)?spells:healingVector)
		{
			final Ability A=spell(mob,id);
			if(A==null)
				continue;
			skills.add(A);
			for(final String kind : new String[]{"POISON","DISEASE"})
			{
				if(SupportPicker.treats(A,kind)&&(!cures.contains(kind)))
					cures.add(kind);
			}
		}
		Object[] pick=SupportPicker.pick(mob,skills,who,(healAt>0)?healAt:101,cures.isEmpty()?null:cures,false);
		if((pick==null)&&(healAt<=0))
			pick=SupportPicker.pick(mob,skills,who,0,null,true); // a blessing for one who lacks it
		if(pick==null)
			return;
		final Ability A=(Ability)pick[0];
		final MOB target=(MOB)pick[1];
		final Vector<String> V=new Vector<String>();
		if(!target.isMonster())
			V.addElement(target.name());
		A.invoke(mob,V,target,false,0);
	}

	public Healer()
	{
		super();
		minTicks=10; maxTicks=20; chance=100;
		tickReset();
		if(healingVector.size()==0)
		{
			healingVector.add("Prayer_CureBlindness");
			healingVector.add("Prayer_CureDisease");
			healingVector.add("Prayer_CureLight");
			healingVector.add("Prayer_RemoveCurse");
			healingVector.add("Prayer_Bless");
			healingVector.add("Prayer_Sanctuary");
		}
	}

	@Override
	public String accountForYourself()
	{
		return "benevolent healing";
	}

	@Override
	public boolean tick(final Tickable ticking, final int tickID)
	{
		super.tick(ticking,tickID);
		if((canAct(ticking,tickID))&&(ticking instanceof MOB))
		{
			final MOB mob=(MOB)ticking;
			final Room thisRoom=mob.location();
			if(thisRoom==null)
				return true;

			final double aChance=CMath.div(mob.curState().getMana(),mob.maxState().getMana());
			if((CMath.random()>aChance)||(mob.curState().getMana()<50))
				return true;

			if((healAt>0)||(spells!=null)||(healTarget!=null))
			{
				healNeediest(mob,thisRoom); // C1
				return true;
			}
			if(thisRoom.numPCInhabitants()>0)
			{
				final MOB target=thisRoom.fetchRandomInhabitant();
				MOB followMOB=target;
				if(target != null)
					followMOB=target.getGroupLeader();
				if((target!=null)
				&&(target!=mob)
				&&(followMOB.getVictim()!=mob)
				&&(!followMOB.isMonster()))
				{
					final String tryID=healingVector.get(CMLib.dice().roll(1,healingVector.size(),-1));
					Ability thisOne=mob.fetchAbility(tryID);
					if(thisOne==null)
					{
						thisOne=CMClass.getAbility(tryID);
						thisOne.setSavable(false);
						mob.addAbility(thisOne);
					}
					thisOne.setProficiency(100);
					final Vector<String> V=new Vector<String>();
					if(!target.isMonster())
						V.addElement(target.name());
					thisOne.invoke(mob,V,target,false,0);
				}
			}
		}
		return true;
	}
}
