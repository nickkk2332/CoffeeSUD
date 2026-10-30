package com.planet_ink.coffee_mud.Libraries.editors;
import com.planet_ink.coffee_mud.Libraries.interfaces.*;
import com.planet_ink.coffee_mud.Libraries.interfaces.AbilityMapper.AbilityMapping;
import com.planet_ink.coffee_mud.Libraries.interfaces.AbilityParameters.*;
import com.planet_ink.coffee_mud.core.exceptions.*;
import com.planet_ink.coffee_mud.core.*;
import com.planet_ink.coffee_mud.core.collections.*;
import com.planet_ink.coffee_mud.core.interfaces.*;
import com.planet_ink.coffee_mud.Abilities.interfaces.*;
import com.planet_ink.coffee_mud.Areas.interfaces.*;
import com.planet_ink.coffee_mud.Behaviors.interfaces.*;
import com.planet_ink.coffee_mud.CharClasses.interfaces.*;
import com.planet_ink.coffee_mud.Commands.interfaces.*;
import com.planet_ink.coffee_mud.Common.interfaces.*;
import com.planet_ink.coffee_mud.Common.interfaces.AbilityComponent.*;
import com.planet_ink.coffee_mud.Exits.interfaces.*;
import com.planet_ink.coffee_mud.Items.interfaces.*;
import com.planet_ink.coffee_mud.Items.interfaces.RawMaterial.*;
import com.planet_ink.coffee_mud.Locales.interfaces.*;
import com.planet_ink.coffee_mud.MOBS.interfaces.*;
import com.planet_ink.coffee_mud.Races.interfaces.*;

import java.io.IOException;
import java.net.Socket;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/*
   Copyright 2008-2025 Bo Zimmerman

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
public class EditorCodedSpellList extends AbilityParmEditorImpl
{
	public EditorCodedSpellList()
	{
		super("CODED_SPELL_LIST",CMLib.lang().L("Spell Affects"),ParmType.SPECIAL);
	}

	@Override
	public void createChoices()
	{
	}

	@Override
	public int maxColWidth()
	{
		return 20;
	}

	@Override
	public boolean confirmValue(String oldVal)
	{
		if(oldVal.length()==0)
			return true;
		if(oldVal.charAt(0)=='*')
			oldVal = oldVal.substring(1);
		final int x=oldVal.indexOf('(');
		int y=oldVal.indexOf(';');
		if((x<y)&&(x>0))
			y=x;
		if(y<0)
			return CMClass.getAbility(oldVal)!=null;
		return CMClass.getAbility(oldVal.substring(0,y))!=null;
	}

	@Override
	public String convertFromItem(final ItemCraftor A, final Item I)
	{
		return CMLib.coffeeMaker().getCodedSpellsOrBehaviors(I);
	}

	@Override
	public String defaultValue()
	{
		return "";
	}

	@Override
	public String[] fakeUserInput(final String oldVal)
	{
		final Vector<String> V = new Vector<String>();
		final Vector<String> V2 = new Vector<String>();
		final List<CMObject> spells=CMLib.coffeeMaker().getCodedSpellsOrBehaviors(oldVal);
		for(int s=0;s<spells.size();s++)
		{
			final CMObject O = spells.get(s);
			V.addElement(O.ID());
			V2.addElement(O.ID());
			if(O instanceof Ability)
				V2.addElement(((Ability)O).text());
			else
			if(O instanceof Behavior)
				V2.addElement(((Behavior)O).getParms());
			else
				V2.add("");
		}
		V.addAll(V2);
		V.addElement("");
		return CMParms.toStringArray(V);
	}

	@Override
	public String commandLinePrompt(final MOB mob, final String oldVal, final int[] showNumber, final int showFlag) throws java.io.IOException
	{
		final List<CMObject> spells=CMLib.coffeeMaker().getCodedSpellsOrBehaviors(oldVal);
		final StringBuffer rawCheck = new StringBuffer("");
		for(int s=0;s<spells.size();s++)
		{
			rawCheck.append(spells.get(s).ID()).append(";");
			if(spells.get(s) instanceof Ability)
				rawCheck.append(((Ability)spells.get(s)).text()).append(";");
			else
			if(spells.get(s) instanceof Behavior)
				rawCheck.append(((Behavior)spells.get(s)).getParms()).append(";");
		}
		boolean okToProceed = true;
		++showNumber[0];
		String newVal = null;
		while(okToProceed)
		{
			okToProceed = false;
			CMLib.genEd().spellsOrBehavs(mob,spells,showNumber[0],showFlag,true);
			final StringBuffer sameCheck = new StringBuffer("");
			for(int s=0;s<spells.size();s++)
			{
				sameCheck.append(spells.get(s).ID()).append(';');
				if(spells.get(s) instanceof Ability)
					sameCheck.append(((Ability)spells.get(s)).text()).append(";");
				else
				if(spells.get(s) instanceof Behavior)
					sameCheck.append(((Behavior)spells.get(s)).getParms()).append(";");
			}
			if(sameCheck.toString().equals(rawCheck.toString()))
				return oldVal;
			try
			{
				newVal = CMLib.coffeeMaker().packCodedSpellsOrBehaviors(spells);
			}
			catch(final CMException e)
			{
				mob.tell(e.getMessage());
				okToProceed = true;
				break;
			}
		}
		return (newVal==null)?oldVal:newVal.toString();
	}
}
