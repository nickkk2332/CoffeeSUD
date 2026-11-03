package com.planet_ink.coffee_mud.Abilities.Common;
import com.planet_ink.coffee_mud.core.interfaces.*;
import com.planet_ink.coffee_mud.core.*;
import com.planet_ink.coffee_mud.core.CMClass.CMObjectType;
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
import com.planet_ink.coffee_mud.Libraries.interfaces.XMLLibrary.*;
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
 * CR8: a building skill as data (CREATE BUILDSKILL), as Construction or
 * Masonry are in code: its name, help, command words, recipe file (in the
 * building skills' format), the materials it takes, its main resource (the
 * word shown, as Ice, and the material it is taken from, as ice), its verb
 * (building, carving), its sound and whether it can be done sitting.  It is
 * kept with the world like the other data skills.
 */
public class GenBuildingSkill extends BuildingSkill
{
	public String	ID	= "GenBuildingSkill";

	@Override
	public String ID()
	{
		return ID;
	}

	@Override
	public String Name()
	{
		return name();
	}

	@Override
	public String name()
	{
		return (String) V(ID, V_NAME);
	}

	private static final Map<String,Object[]> vars=new Hashtable<String,Object[]>();

	private static final int	V_NAME	= 0;	// S
	private static final int	V_TRIG	= 1;	// S[]
	private static final int	V_HELP	= 2;	// S
	private static final int	V_FNAM	= 3;	// S
	private static final int	V_RSCS	= 4;	// S
	private static final int	V_MAIN	= 5;	// S
	private static final int	V_MMAT	= 6;	// S
	private static final int	V_VERB	= 7;	// S
	private static final int	V_SOND	= 8;	// S
	private static final int	V_CNST	= 9;	// B
	private static final int	NUM_VS	= 10;

	private static final Object[] makeEmpty()
	{
		final Object[] O=new Object[NUM_VS];
		O[V_NAME]="Building Skill";
		O[V_TRIG]=new String[]{"BUILDWITH"};
		O[V_HELP]="<ABILITY>This skill is not yet documented.";
		O[V_FNAM]="";
		O[V_RSCS]="WOODEN";
		O[V_MAIN]="Wood";
		O[V_MMAT]="wooden";
		O[V_VERB]="building";
		O[V_SOND]="hammer.wav";
		O[V_CNST]=Boolean.valueOf(false);
		return O;
	}

	private static final Object V(final String ID, final int varNum)
	{
		if(vars.containsKey(ID))
			return vars.get(ID)[varNum];
		final Object[] O=makeEmpty();
		vars.put(ID,O);
		return O[varNum];
	}

	private static final void SV(final String ID,final int varNum,final Object O)
	{
		if(vars.containsKey(ID))
			vars.get(ID)[varNum]=O;
		else
		{
			final Object[] O2=makeEmpty();
			vars.put(ID,O2);
			O2[varNum]=O;
		}
	}

	public GenBuildingSkill()
	{
		super();
	}

	@Override
	public CMObject newInstance()
	{
		try
		{
			final GenBuildingSkill A=this.getClass().getDeclaredConstructor().newInstance();
			A.ID=ID;
			return A;
		}
		catch(final Exception e)
		{
			Log.errOut(ID(),e);
		}
		return new GenBuildingSkill();
	}

	@Override
	protected void cloneFix(final Ability E)
	{
	}

	@Override
	public boolean isGeneric()
	{
		return true;
	}

	@Override
	public String[] triggerStrings()
	{
		return (String[]) V(ID, V_TRIG);
	}

	@Override
	public boolean canBeDoneSittingDown()
	{
		return ((Boolean) V(ID, V_CNST)).booleanValue();
	}

	@Override
	public String supportedResourceString()
	{
		return (String) V(ID, V_RSCS);
	}

	@Override
	public String getRecipeFilename()
	{
		return (String) V(ID, V_FNAM);
	}

	@Override
	protected String getMainResourceName()
	{
		return (String) V(ID, V_MAIN);
	}

	@Override
	protected String getSoundName()
	{
		return (String) V(ID, V_SOND);
	}

	@Override
	protected int[][] getBasicMaterials(final MOB mob, final int woodRequired, String miscType)
	{
		if(miscType.length()==0)
			miscType=(String) V(ID, V_MMAT);
		return fetchFoundResourceData(mob, woodRequired, miscType, null, 0, null, null, false, 0, null);
	}

	@Override
	public String establishVerb(final MOB mob, final String[] recipe)
	{
		final String verb=super.establishVerb(mob, recipe);
		final String mine=((String) V(ID, V_VERB)).trim();
		if((mine.length()>0)&&(!mine.equalsIgnoreCase("building"))&&verb.startsWith(L("building")+" "))
			return mine+verb.substring(L("building").length());
		return verb;
	}

	private static final String[] CODES={"CLASS",//0
										 "TEXT",//1
										 "NAME",//2S
										 "HELP",//3S
										 "TRIGSTR",//4S[]
										 "FILENAME",//5S
										 "MATLIST",//6S
										 "MAINRESOURCE",//7S
										 "MAINMATERIAL",//8S
										 "VERB",//9S
										 "SOUND",//10S
										 "CANSIT",//11B
										};

	@Override
	public String[] getStatCodes()
	{
		return CODES;
	}

	@Override
	public int getSaveStatIndex()
	{
		return getStatCodes().length;
	}

	@Override
	protected int getCodeNum(final String code)
	{
		for(int i=0;i<CODES.length;i++)
		{
			if(code.equalsIgnoreCase(CODES[i]))
				return i;
		}
		return -1;
	}

	@Override
	public String getStat(final String code)
	{
		switch(getCodeNum(code))
		{
		case 0:
			return ID();
		case 1:
			return text();
		case 2:
			return (String) V(ID, V_NAME);
		case 3:
			return (String) V(ID, V_HELP);
		case 4:
			return CMParms.toListString((String[]) V(ID, V_TRIG));
		case 5:
			return (String) V(ID, V_FNAM);
		case 6:
			return ((String) V(ID, V_RSCS)).replace('|', ',');
		case 7:
			return (String) V(ID, V_MAIN);
		case 8:
			return (String) V(ID, V_MMAT);
		case 9:
			return (String) V(ID, V_VERB);
		case 10:
			return (String) V(ID, V_SOND);
		case 11:
			return Boolean.toString(((Boolean) V(ID, V_CNST)).booleanValue());
		default:
			if(code.equalsIgnoreCase("javaclass"))
				return "GenBuildingSkill";
			if(code.equalsIgnoreCase("allxml"))
				return getAllXML();
			return super.getStat(code);
		}
	}

	@Override
	public void setStat(String code, final String val)
	{
		int num=0;
		int numDex=code.length();
		while((numDex>0)&&(Character.isDigit(code.charAt(numDex-1))))
			numDex--;
		if(numDex<code.length())
		{
			num=CMath.s_int(code.substring(numDex));
			code=code.substring(0,numDex);
		}
		switch(getCodeNum(code))
		{
		case 0:
			if(val.trim().length()>0)
			{
				V(ID,V_NAME); // force creation, if necc
				final Object[] O=vars.get(ID);
				vars.remove(ID);
				vars.put(val,O);
				if(num!=9)
					CMClass.delClass(CMObjectType.ABILITY,this);
				ID=val;
				if(num!=9)
					CMClass.addClass(CMObjectType.ABILITY,this);
			}
			break;
		case 1:
			setMiscText(val);
			break;
		case 2:
			SV(ID, V_NAME, val);
			break;
		case 3:
			SV(ID, V_HELP, val);
			break;
		case 4:
			SV(ID, V_TRIG, CMParms.parseCommas(val.toUpperCase(), true).toArray(new String[0]));
			break;
		case 5:
			SV(ID, V_FNAM, val.trim());
			break;
		case 6:
			SV(ID, V_RSCS, val.toUpperCase().replace(',', '|'));
			break;
		case 7:
			SV(ID, V_MAIN, val);
			break;
		case 8:
			SV(ID, V_MMAT, val.trim());
			break;
		case 9:
			SV(ID, V_VERB, val);
			break;
		case 10:
			SV(ID, V_SOND, val);
			break;
		case 11:
			SV(ID, V_CNST, Boolean.valueOf(CMath.s_bool(val)));
			break;
		default:
			if(code.equalsIgnoreCase("allxml")&&ID.equalsIgnoreCase("GenBuildingSkill"))
				parseAllXML(val);
			else
				super.setStat(code, val);
			break;
		}
	}

	@Override
	public boolean sameAs(final Environmental E)
	{
		if(!(E instanceof GenBuildingSkill))
			return false;
		if(!((GenBuildingSkill)E).ID().equals(ID))
			return false;
		if(!((GenBuildingSkill)E).text().equals(text()))
			return false;
		return true;
	}

	private void parseAllXML(final String xml)
	{
		final List<XMLTag> V=CMLib.xml().parseAllXML(xml);
		if((V==null)||(V.size()==0))
			return;
		for(int c=0;c<getStatCodes().length;c++)
		{
			if(getStatCodes()[c].equals("CLASS"))
				ID=CMLib.xml().restoreAngleBrackets(CMLib.xml().getValFromPieces(V, getStatCodes()[c]));
			else
			if(!getStatCodes()[c].equals("TEXT"))
				setStat(getStatCodes()[c],CMLib.xml().restoreAngleBrackets(CMLib.xml().getValFromPieces(V, getStatCodes()[c])));
		}
	}

	private String getAllXML()
	{
		final StringBuffer str=new StringBuffer("");
		for(int c=0;c<getStatCodes().length;c++)
		{
			if(!getStatCodes()[c].equals("TEXT"))
			{
				str.append("<"+getStatCodes()[c]+">"
						+CMLib.xml().parseOutAngleBrackets(getStat(getStatCodes()[c]))
						+"</"+getStatCodes()[c]+">");
			}
		}
		return str.toString();
	}
}
