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
 * P19: on a mob a generic ability summoned (SUMMON).  With BIND it goes when
 * its summoner dies or leaves the game; with TICKS it goes when they run out
 * (DISMISSONEXPIRE: the effect's time; the effect's end sends it too).
 * Text: ID=[ability ID] TICKS=[n] and the SUMMONFLAGS.  Its summoner is its
 * invoker, or after a restore the mob it follows.
 */
public class Prop_Summoned extends Property
{
	@Override
	public String ID()
	{
		return "Prop_Summoned";
	}

	@Override
	public String name()
	{
		return "Summoned";
	}

	@Override
	protected int canAffectCode()
	{
		return Ability.CAN_MOBS;
	}

	protected MOB		summoner	= null;
	protected String	effectID	= "";
	protected String	flags		= "";
	protected int		ticksLeft	= -1;

	@Override
	public String accountForYourself()
	{
		return "Summoned"+((effectID.length()>0)?" by "+effectID:"");
	}

	@Override
	public MOB invoker()
	{
		if(summoner!=null)
			return summoner;
		if(affected instanceof MOB)
			return ((MOB)affected).amFollowing();
		return null;
	}

	@Override
	public void setInvoker(final MOB mob)
	{
		summoner=mob;
	}

	@Override
	public void setMiscText(final String newMiscText)
	{
		super.setMiscText(newMiscText);
		final String t=(newMiscText==null)?"":newMiscText;
		effectID=CMParms.getParmStr(t, "ID", "");
		ticksLeft=CMParms.getParmInt(t, "TICKS", -1);
		flags=Summons.flags(t);
	}

	@Override
	public String text()
	{
		// the ticks left go with the mob into a save
		final StringBuilder s=new StringBuilder("");
		if(effectID.length()>0)
			s.append("ID=").append(effectID).append(' ');
		if(ticksLeft>=0)
			s.append("TICKS=").append(ticksLeft).append(' ');
		return s.append(flags).toString().trim();
	}

	@Override
	public boolean tick(final Tickable ticking, final int tickID)
	{
		if((tickID!=Tickable.TICKID_MOB)||(!(affected instanceof MOB)))
			return true;
		final MOB M=(MOB)affected;
		if(M.amDead()||M.amDestroyed())
			return true;
		final MOB caster=invoker();
		if((flags.indexOf("BIND")>=0)
		&&((caster==null)||caster.amDead()||caster.amDestroyed()||(!CMLib.flags().isInTheGame(caster, true))))
			Summons.dismiss(M);
		else
		if((ticksLeft>=0)&&(--ticksLeft<=0))
			Summons.dismiss(M);
		return true;
	}
}
