package com.planet_ink.coffee_mud.Libraries.editors;

import com.planet_ink.coffee_mud.Libraries.interfaces.*;
import com.planet_ink.coffee_mud.Libraries.interfaces.AbilityMapper.AbilityMapping;
import com.planet_ink.coffee_mud.Libraries.interfaces.AbilityParameters.AbilityParmEditor;
import com.planet_ink.coffee_mud.Libraries.interfaces.AbilityParameters.ParmType;
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
import com.planet_ink.coffee_mud.Common.interfaces.AbilityComponent.CompConnector;
import com.planet_ink.coffee_mud.Common.interfaces.AbilityComponent.CompLocation;
import com.planet_ink.coffee_mud.Common.interfaces.AbilityComponent.CompType;
import com.planet_ink.coffee_mud.Exits.interfaces.*;
import com.planet_ink.coffee_mud.Items.interfaces.*;
import com.planet_ink.coffee_mud.Items.interfaces.RawMaterial.Material;
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

public abstract class AbilityParmEditorImpl implements AbilityParmEditor
{
	private final String	ID;
	private final ParmType	fieldType;
	private String			prompt	= null;
	private String			header	= null;

	protected PairList<String, String>	choices	= null;

	public AbilityParmEditorImpl(final String fieldName, final String shortHeader, final ParmType type)
	{
		ID=fieldName;
		fieldType = type;
		header = shortHeader;
		prompt = CMStrings.capitalizeAndLower(CMStrings.replaceAll(ID,"_"," "));
		createChoices();
	}

	@Override
	public String ID()
	{
		return ID;
	}

	@Override
	public ParmType parmType()
	{
		return fieldType;
	}

	@Override
	public String prompt()
	{
		return prompt;
	}

	@Override
	public String colHeader()
	{
		return header;
	}

	@Override
	public int maxColWidth()
	{
		return Integer.MAX_VALUE;
	}

	@Override
	public int minColWidth()
	{
		return 0;
	}

	@Override
	public boolean confirmValue(final String oldVal)
	{
		final boolean spaceOK = fieldType != ParmType.ONEWORD;
		boolean emptyOK = false;
		switch(fieldType)
		{
		case STRINGORNULL:
			emptyOK = true;
		//$FALL-THROUGH$
		case ONEWORD:
		case STRING:
		{
			if((!spaceOK) && (oldVal.indexOf(' ') >= 0))
				return false;
			return (emptyOK)||(oldVal.trim().length()>0);
		}
		case NUMBERORNULL:
			if(oldVal.length()==0)
				return true;
			//$FALL-THROUGH$
		case NUMBER:
			return CMath.isInteger(oldVal);
		case NUMBER_PAIR:
		{
			if(oldVal==null)
				return emptyOK;
			final int x=oldVal.indexOf(',');
			return CMath.isInteger(oldVal)
				||((x>0)&&(CMath.isInteger(oldVal.substring(0,x).trim()))&&(CMath.isInteger(oldVal.substring(x+1).trim())));
		}
		case CHOICES:
			if(!CMStrings.contains(choices.toArrayFirst(new String[0]),oldVal))
				return CMStrings.contains(choices.toArrayFirst(new String[0]),oldVal.toUpperCase().trim());
			return true;
		case MULTICHOICES:
			return CMath.isInteger(oldVal)||choices().containsFirst(oldVal);
		case SPECIAL:
			break;
		}
		return false;
	}

	@Override
	public String[] fakeUserInput(final String oldVal)
	{
		boolean emptyOK = false;
		switch(fieldType)
		{
		case STRINGORNULL:
			emptyOK = true;
		//$FALL-THROUGH$
		case ONEWORD:
		case STRING:
		{
			if(emptyOK && (oldVal.trim().length()==0))
				return new String[]{"NULL"};
			return new String[]{oldVal};
		}
		case NUMBERORNULL:
			if(oldVal.trim().length()==0)
				return new String[]{"NULL"};
			//$FALL-THROUGH$
		case NUMBER:
		case NUMBER_PAIR:
			return new String[]{oldVal};
		case CHOICES:
		{
			if(oldVal.trim().length()==0) return new String[]{"NULL"};
			final Vector<String> V = new XVector<String>(choices.toArrayFirst(new String[0]));
			for(int v=0;v<V.size();v++)
			{
				if(oldVal.equalsIgnoreCase(V.elementAt(v)))
					return new String[]{choices.get(v).second};
			}
			return new String[]{oldVal};
		}
		case MULTICHOICES:
			if(oldVal.trim().length()==0)
				return new String[]{"NULL"};
			if(!CMath.isInteger(oldVal))
			{
				final Vector<String> V = new XVector<String>(choices.toArrayFirst(new String[0]));
				for(int v=0;v<V.size();v++)
				{
					if(oldVal.equalsIgnoreCase(V.elementAt(v)))
						return new String[]{choices.get(v).second,""};
				}
			}
			else
			{
				final Vector<String> V = new Vector<String>();
				for(int c=0;c<choices.size();c++)
				{
					if(CMath.bset(CMath.s_int(oldVal),CMath.s_int(choices.get(c).first)))
					{
						V.addElement(choices.get(c).second);
						V.addElement(choices.get(c).second);
					}
				}
				if(V.size()>0)
				{
					V.addElement("");
					return CMParms.toStringArray(V);
				}
			}
			return new String[]{"NULL"};
		case SPECIAL:
			break;
		}
		return new String[]{};
	}

	@Override
	public String commandLinePrompt(final MOB mob, final String oldVal, final int[] showNumber, final int showFlag)
	throws java.io.IOException
	{
		String str = null;
		boolean emptyOK = false;
		final boolean spaceOK = fieldType != ParmType.ONEWORD;
		switch(fieldType)
		{
		case STRINGORNULL:
			emptyOK = true;
		//$FALL-THROUGH$
		case ONEWORD:
		case STRING:
		{
			++showNumber[0];
			boolean proceed = true;
			while(proceed&&(mob.session()!=null)&&(!mob.session().isStopped()))
			{
				str = CMLib.genEd().prompt(mob,oldVal,showNumber[0],showFlag,prompt(),emptyOK).trim();
				if((!spaceOK) && (str.indexOf(' ') >= 0))
					mob.tell(CMLib.lang().L("Spaces are not allowed here."));
				else
					proceed=false;
			}
			break;
		}
		case NUMBERORNULL:
		case NUMBER:
		{
			final String newStr=CMLib.genEd().prompt(mob,oldVal,++showNumber[0],showFlag,prompt(),true);
			if(newStr.trim().length()==0)
				str="";
			else
				str = Integer.toString(CMath.s_int(newStr));
			break;
		}
		case NUMBER_PAIR:
		{
			final String newStr=CMLib.genEd().prompt(mob,oldVal,++showNumber[0],showFlag,prompt(),true);
			if(newStr.trim().length()==0)
				str="";
			else
			if(CMath.isInteger(newStr))
				str = Integer.toString(CMath.s_int(newStr));
			else
			{
				final int x=newStr.indexOf(',');
				if(x>0)
				{
					str = Integer.toString(CMath.s_int(newStr.substring(0,x).trim()))
						+ Integer.toString(CMath.s_int(newStr.substring(x+1).trim()));
				}
			}
			break;
		}
		case CHOICES:
			str = CMLib.genEd().promptMultiOrExtra(mob,oldVal,++showNumber[0],showFlag,prompt(),choices);
			break;
		case MULTICHOICES:
			str = CMLib.genEd().promptMultiOrExtra(mob,oldVal,++showNumber[0],showFlag,prompt(),choices);
			if(CMath.isInteger(str))
				str = Integer.toString(CMath.s_int(str));
			break;
		case SPECIAL:
			break;
		}
		return str;
	}

	@Override
	public String commandLineValue(final String oldVal)
	{
		return oldVal;
	}

	public abstract void createChoices();

	@Override
	public PairList<String,String> createChoices(final Enumeration<? extends Object> e)
	{
		if(choices != null)
			return choices;
		choices = new PairVector<String,String>();
		Object o = null;
		for(;e.hasMoreElements();)
		{
			o = e.nextElement();
			if(o instanceof String)
				choices.add((String)o,CMStrings.capitalizeAndLower((String)o));
			else
			if(o instanceof Ability)
				choices.add(((Ability)o).ID(),((Ability)o).name());
			else
			if(o instanceof Race)
				choices.add(((Race)o).ID(),((Race)o).name());
			else
			if(o instanceof Environmental)
				choices.add(((Environmental)o).ID(),((Environmental)o).ID());
		}
		return choices;
	}

	@SuppressWarnings("unchecked")
	@Override
	public PairList<String,String> createChoices(final List<? extends Object> V)
	{
		return createChoices(new IteratorEnumeration<Object>((Iterator<Object>)V.iterator()));
	}

	@Override
	public PairList<String,String> createChoices(final String[] S)
	{
		final XVector<String> X=new XVector<String>(S);
		Collections.sort(X);
		return createChoices(X.elements());
	}

	public PairList<String,String> createBinaryChoices(final String[] S)
	{
		if(choices != null)
			return choices;
		choices = createChoices(new XVector<String>(S).elements());
		for(int i=0;i<choices.size();i++)
		{
			if(i==0)
				choices.get(i).first =Integer.toString(0);
			else
				choices.get(i).first = Integer.toString(1<<(i-1));
		}
		return choices;
	}

	public PairList<String,String> createNumberedChoices(final String[] S)
	{
		if(choices != null)
			return choices;
		choices = createChoices(new XVector<String>(S).elements());
		for(int i=0;i<choices.size();i++)
			choices.get(i).first = Integer.toString(i);
		return choices;
	}

	@Override
	public PairList<String, String> choices()
	{
		return choices;
	}

	@Override
	public int appliesToClass(final Object o)
	{
		return 0;
	}

	@SuppressWarnings("unchecked")
	protected Pair<String[],String[]> getBuildingCodesNFlags()
	{
		Pair<String[],String[]> codesFlags = (Pair<String[],String[]>)Resources.getResource("BUILDING_SKILL_CODES_FLAGS");
		if(codesFlags == null)
		{
			RecipeDriven A=(RecipeDriven)CMClass.getAbility("Masonry");
			if(A==null)
				A=(RecipeDriven)CMClass.getAbility("Construction");
			if(A==null)
				A=(RecipeDriven)CMClass.getAbility("Excavation");
			if(A!=null)
				A.getRecipeFormat();
			codesFlags = (Pair<String[],String[]>)Resources.getResource("BUILDING_SKILL_CODES_FLAGS");
		}
		return codesFlags;
	}

	public String L(final String str, final String... xs)
	{
		return CMLib.lang().fullSessionTranslation(getClass(), str, xs);
	}

	public String L(final Class<?> clazz, final String str, final String ... xs)
	{
		return CMLib.lang().fullSessionTranslation(clazz, str, xs);
	}
}
