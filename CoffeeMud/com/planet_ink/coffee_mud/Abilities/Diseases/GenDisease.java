package com.planet_ink.coffee_mud.Abilities.Diseases;
import com.planet_ink.coffee_mud.core.interfaces.*;
import com.planet_ink.coffee_mud.core.*;
import com.planet_ink.coffee_mud.core.CMClass.CMObjectType;
import com.planet_ink.coffee_mud.core.CMath.CompiledFormula;
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
import java.util.regex.Pattern;
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
 * P20: a disease as data.  TICKS and DELAY are formulas (@x1 the giver's
 * level, @x2 its expertise, @x3 the sufferer's level); SPREAD the ways it
 * passes on (STD, CONTACT, PROXIMITY, CONSUMPTION, DAMAGE, GET, HEARING,
 * INHERITED), each try going ahead CATCHCHANCE percent of the time before
 * the disease save; DIFFICULTY is what cures compare with the healer's level
 * (times 10); every DELAY ticks it shows AFFECTMSG, does DAMAGE and, for
 * PROXIMITY, may pass to someone in the room.  ADJUSTMENTS (Prop_Adjuster)
 * and EFFECTS (Prop_SpellAdder) are held while it lasts; IMMUNEMASK names
 * who never catches it; SCRIPT is a script run with it, with ONAFFECT,
 * ONTICK and ONEXPIRE functions.
 */
public class GenDisease extends Disease implements SpellHolder
{
	public String	ID	= "GenDisease";

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

	@Override
	public String displayText()
	{
		return (String) V(ID, V_DISP);
	}

	@Override
	public String[] triggerStrings()
	{
		return (String[]) V(ID, V_TRIG);
	}

	protected Object V(String ID, final int varNum)
	{
		if(ID==null)
			ID="GenDisease"; // the Disease constructor asks for DISEASE_DELAY before ours has run
		if(vars.containsKey(ID))
			return vars.get(ID)[varNum];
		final Object[] O=makeEmpty();
		vars.put(ID,O);
		return O[varNum];
	}

	protected void SV(final String ID,final int varNum,final Object O)
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

	public GenDisease()
	{
		super();
	}

	@Override
	public CMObject newInstance()
	{
		try
		{
			final GenDisease A=this.getClass().getDeclaredConstructor().newInstance();
			cloneFix(A);
			return A;
		}
		catch(final Exception e)
		{
			Log.errOut(ID(),e);
		}
		return new GenDisease();
	}

	@Override
	protected void cloneFix(final Ability E)
	{
		if(E instanceof GenDisease)
		{
			((GenDisease)E).ID=ID;
			((GenDisease)E).adjusterA = null;
			((GenDisease)E).effects = null;
			((GenDisease)E).scriptObj = null;
			((GenDisease)E).scriptHash = 0;
			((GenDisease)E).diseaseTick = -1;
		}
	}

	@Override
	public boolean isGeneric()
	{
		return true;
	}

	protected static final Map<String,Object[]> vars=new Hashtable<String,Object[]>();
	protected static final int	V_NAME			= 0;	// S
	protected static final int	V_DISP			= 1;	// S
	protected static final int	V_TRIG			= 2;	// S[]
	protected static final int	V_HELP			= 3;	// S
	protected static final int	V_TICKS			= 4;	// P<S,F>
	protected static final int	V_DELAY			= 5;	// P<S,F>
	protected static final int	V_SPRD			= 6;	// I
	protected static final int	V_CTCH			= 7;	// P<S,F>
	protected static final int	V_DIFF			= 8;	// I
	protected static final int	V_START			= 9;	// S
	protected static final int	V_AMSG			= 10;	// S
	protected static final int	V_DONE			= 11;	// S
	protected static final int	V_ADJS			= 12;	// S
	protected static final int	V_DAMG			= 13;	// P<S,F>
	protected static final int	V_EFFS			= 14;	// P<S,List<Ability>>
	protected static final int	V_IMMU			= 15;	// S
	protected static final int	V_MALI			= 16;	// B
	protected static final int	V_SCRP			= 17;	// S
	protected static final int	NUM_VS			= 18;	//

	protected Object[] makeEmpty()
	{
		final Object[] O=new Object[NUM_VS];
		O[V_NAME]="Disease";
		O[V_DISP]="(Diseased)";
		O[V_TRIG]=new String[0];
		O[V_HELP]="<ABILITY>This disease is not yet documented.";
		O[V_TICKS]=makeFormulaPair("48");
		O[V_DELAY]=makeFormulaPair("5");
		O[V_SPRD]=Integer.valueOf(0);
		O[V_CTCH]=makeFormulaPair("100");
		O[V_DIFF]=Integer.valueOf(0);
		O[V_START]=CMLib.lang().L("^G<S-NAME> come(s) down with a disease.^?");
		O[V_AMSG]=CMLib.lang().L("<S-NAME> ache(s) and groan(s).");
		O[V_DONE]=CMLib.lang().L("Your disease has run its course.");
		O[V_ADJS]="";
		O[V_DAMG]=makeFormulaPair("0");
		O[V_EFFS]=new Pair<String,List<Ability>>("",new ArrayList<Ability>(0));
		O[V_IMMU]="";
		O[V_MALI]=Boolean.TRUE;
		O[V_SCRP]="";
		return O;
	}

	protected static Pair<String,CompiledFormula> makeFormulaPair(final String formula)
	{
		final String f=((formula==null)||(formula.trim().length()==0))?"0":formula.trim();
		final CompiledFormula expr = CMath.compileMathExpression(f);
		return new Pair<String,CompiledFormula>(f, expr);
	}

	protected static Pair<String,List<Ability>> makeEffectsPair(final String effects)
	{
		final List<Ability> aV = new LinkedList<Ability>();
		for(final CMObject o : CMLib.coffeeMaker().getCodedSpellsOrBehaviors(effects))
		{
			if(o instanceof Ability)
				aV.add((Ability)o);
		}
		return new Pair<String,List<Ability>>(effects, aV);
	}

	/** the SPREAD words, for the bits 1, 2, 4 ... of DiseaseAffect's SPREAD_ constants */
	public static final String[] SPREAD_NAMES = { "STD", "CONTACT", "PROXIMITY", "CONSUMPTION", "DAMAGE", "GET", "HEARING", "INHERITED" };

	/**
	 * @param s SPREAD words (or DiseaseAffect.SPREAD_DESCS), by commas or spaces, or a number
	 * @return the bitmap
	 */
	public static int spreadFrom(final String s)
	{
		if(s==null)
			return 0;
		if(CMath.isInteger(s.trim()))
			return CMath.s_int(s.trim());
		int bits=0;
		for(final String w : CMParms.parseAny(s.toUpperCase().replace(' ',','),',',true))
		{
			final String word=w.trim();
			for(int i=0;i<SPREAD_NAMES.length;i++)
			{
				if(SPREAD_NAMES[i].equals(word)
				||((i<DiseaseAffect.SPREAD_DESCS.length)&&DiseaseAffect.SPREAD_DESCS[i].replace(' ','_').equalsIgnoreCase(word)))
					bits|=(1<<i);
			}
		}
		return bits;
	}

	/**
	 * @param bits a spread bitmap
	 * @return its words, by commas
	 */
	public static String spreadWords(final int bits)
	{
		final StringBuilder s=new StringBuilder("");
		for(int i=0;i<SPREAD_NAMES.length;i++)
		{
			if(CMath.bset(bits,1<<i))
				s.append((s.length()>0)?",":"").append(SPREAD_NAMES[i]);
		}
		return s.toString();
	}

	protected Ability adjusterA = null;
	protected List<Ability> effects = null;
	private ScriptingEngine scriptObj = null;
	private int scriptHash = 0;

	protected Ability getAdjusterA()
	{
		if(adjusterA == null)
		{
			adjusterA = CMClass.getAbility("Prop_Adjuster");
			adjusterA.setAffectedOne(affected);
			adjusterA.setMiscText(CMStrings.replaceVariables((String)V(ID,V_ADJS),formulaVarss()));
		}
		return adjusterA;
	}

	protected final static List<Ability> emptyEffects = new ArrayList<Ability>(0);

	protected List<Ability> getOtherEffects()
	{
		if(effects == null)
		{
			final Physical affected=this.affected;
			if(!(affected instanceof MOB))
				return emptyEffects;
			@SuppressWarnings("unchecked")
			final Pair<String,List<Ability>> peffects = (Pair<String,List<Ability>>)V(ID,V_EFFS);
			final List<Ability> neffects = new LinkedList<Ability>();
			for(final Ability effA : peffects.second)
			{
				final Ability A = (Ability)effA.copyOf();
				A.setMiscText(effA.text());
				A.makeNonUninvokable();
				A.makeLongLasting();
				A.setAffectedOne(affected);
				neffects.add(A);
			}
			effects = neffects;
		}
		return effects;
	}

	/**
	 * @return the disease's script, or null
	 */
	public ScriptingEngine getScripter()
	{
		final String s=(String)V(ID,V_SCRP);
		if(s.hashCode()!=scriptHash)
		{
			scriptHash=s.hashCode();
			scriptObj=null;
			if(s.trim().length()>0)
			{
				scriptObj=(ScriptingEngine)CMClass.getCommon("DefaultScriptingEngine");
				if(scriptObj!=null)
				{
					scriptObj.setSavable(false);
					scriptObj.setScript(s);
					scriptObj.preApproveScripts();
				}
			}
		}
		return scriptObj;
	}

	protected double[] formulaVars()
	{
		final double[] vars = new double[10];
		if(invoker != null)
		{
			vars[0] = invoker.phyStats().level();
			vars[1] = super.getXLEVELLevel(invoker);
		}
		if(affected != null)
			vars[2] = affected.phyStats().level();
		return vars;
	}

	protected String[] formulaVarss()
	{
		final double[] d=formulaVars();
		final String[] vars = new String[10];
		for(int i=0;i<vars.length;i++)
			vars[i]=""+(int)Math.round(d[i]);
		return vars;
	}

	protected int formula(final int v)
	{
		@SuppressWarnings("rawtypes")
		final Pair P = (Pair)V(ID,v);
		return (int)Math.round(CMath.parseMathExpression((CompiledFormula)P.second, formulaVars(), 0));
	}

	@Override
	public void setAffectedOne(final Physical P)
	{
		final Ability adjusterA = getAdjusterA();
		super.setAffectedOne(P);
		adjusterA.setAffectedOne(P);
		adjusterA.setMiscText(CMStrings.replaceVariables((String)V(ID,V_ADJS),formulaVarss()));
	}

	@Override
	public void setInvoker(final MOB M)
	{
		final Ability adjusterA = getAdjusterA();
		super.setInvoker(M);
		adjusterA.setMiscText(CMStrings.replaceVariables((String)V(ID,V_ADJS),formulaVarss()));
	}

	@Override
	protected int DISEASE_TICKS()
	{
		return formula(V_TICKS);
	}

	@Override
	protected int DISEASE_DELAY()
	{
		return Math.max(1,formula(V_DELAY));
	}

	@Override
	protected String DISEASE_DONE()
	{
		return (String)V(ID,V_DONE);
	}

	@Override
	protected String DISEASE_START()
	{
		return (String)V(ID,V_START);
	}

	@Override
	protected String DISEASE_AFFECT()
	{
		return (String)V(ID,V_AMSG);
	}

	@Override
	public int spreadBitmap()
	{
		return ((Integer)V(ID,V_SPRD)).intValue();
	}

	@Override
	public int difficultyLevel()
	{
		return ((Integer)V(ID,V_DIFF)).intValue();
	}

	@Override
	public boolean isMalicious()
	{
		return ((Boolean)V(ID,V_MALI)).booleanValue();
	}

	@Override
	public int abstractQuality()
	{
		return isMalicious()?Ability.QUALITY_MALICIOUS:Ability.QUALITY_INDIFFERENT;
	}

	@Override
	protected int canAffectCode()
	{
		return CAN_MOBS|CAN_ITEMS;
	}

	@Override
	protected int canTargetCode()
	{
		return CAN_MOBS;
	}

	/**
	 * @param target someone
	 * @return true if IMMUNEMASK says they never catch it
	 */
	public boolean immune(final Physical target)
	{
		final String mask=(String)V(ID,V_IMMU);
		return (mask.trim().length()>0)&&(target instanceof MOB)&&CMLib.masking().maskCheck(mask,target,true);
	}

	@Override
	protected boolean catchIt(final MOB mob, final Physical target)
	{
		if(immune(target))
			return false;
		final int chance=formula(V_CTCH);
		if((chance<100)&&(CMLib.dice().rollPercentage()>chance))
			return false;
		return super.catchIt(mob,target);
	}

	@Override
	public boolean invoke(final MOB mob, final List<String> commands, final Physical givenTarget, final boolean auto, final int asLevel)
	{
		final Physical target=(givenTarget!=null)?givenTarget:mob;
		if(immune(target))
			return false;
		final boolean had=(target!=null)&&(target.fetchEffect(ID())!=null);
		if(!super.invoke(mob,commands,givenTarget,auto,asLevel))
			return false;
		if((!had)&&(target instanceof MOB))
		{
			final Ability A=target.fetchEffect(ID());
			if(A instanceof GenDisease)
			{
				final GenDisease D=(GenDisease)A;
				D.diseaseTick=D.DISEASE_DELAY();
				ScriptHooks.call(D.getScripter(),(MOB)target,"ONAFFECT",mob,target,"");
			}
		}
		return true;
	}

	@Override
	public void unInvoke()
	{
		final Physical P=affected;
		final boolean going=(P instanceof MOB)&&canBeUninvoked();
		if(going)
			ScriptHooks.call(getScripter(),(MOB)P,"ONEXPIRE",(MOB)P,P,"");
		super.unInvoke();
	}

	@Override
	public void affectCharStats(final MOB affected, final CharStats affectableStats)
	{
		super.affectCharStats(affected, affectableStats);
		getAdjusterA().affectCharStats(affected, affectableStats);
		for(final Ability A : getOtherEffects())
			A.affectCharStats(affected, affectableStats);
	}

	@Override
	public void affectPhyStats(final Physical affected, final PhyStats affectableStats)
	{
		super.affectPhyStats(affected,affectableStats);
		getAdjusterA().affectPhyStats(affected, affectableStats);
		for(final Ability A : getOtherEffects())
			A.affectPhyStats(affected, affectableStats);
	}

	@Override
	public void affectCharState(final MOB affectedMob, final CharState affectableMaxState)
	{
		super.affectCharState(affectedMob, affectableMaxState);
		getAdjusterA().affectCharState(affectedMob, affectableMaxState);
		for(final Ability A : getOtherEffects())
			A.affectCharState(affectedMob, affectableMaxState);
	}

	@Override
	public boolean tick(final Tickable ticking, final int tickID)
	{
		if(!super.tick(ticking, tickID))
			return false;
		if(!(affected instanceof MOB))
			return true;
		final MOB mob=(MOB)affected;
		getAdjusterA().tick(ticking, tickID);
		for(final Ability A : getOtherEffects())
			A.tick(ticking, tickID);
		final ScriptingEngine S=getScripter();
		if(S!=null)
			S.tick(mob, tickID);
		if(tickID!=Tickable.TICKID_MOB)
			return true;
		if(diseaseTick<0)
			diseaseTick=DISEASE_DELAY();
		if((!mob.amDead())&&((--diseaseTick)<=0)&&(mob.location()!=null))
		{
			diseaseTick=DISEASE_DELAY();
			final MOB diseaser=(invoker!=null)?invoker:mob;
			final String amsg=DISEASE_AFFECT();
			if((amsg!=null)&&(amsg.length()>0))
				mob.location().show(mob,null,CMMsg.MSG_NOISE,amsg);
			final int damage=formula(V_DAMG);
			if(damage>0)
				CMLib.combat().postDamage(diseaser,mob,this,damage,CMMsg.MASK_ALWAYS|CMMsg.TYP_DISEASE,-1,null);
			ScriptHooks.call(S,mob,"ONTICK",diseaser,mob,"");
			if(CMath.bset(spreadBitmap(),DiseaseAffect.SPREAD_PROXIMITY)&&(!mob.amDead()))
				catchIt(mob);
		}
		return true;
	}

	@Override
	public boolean okMessage(final Environmental myHost, final CMMsg msg)
	{
		if(!super.okMessage(myHost, msg))
			return false;
		for(final Ability A : getOtherEffects())
		{
			if(!A.okMessage(myHost, msg))
				return false;
		}
		final ScriptingEngine S=getScripter();
		if((S!=null)&&(affected!=null)&&(!S.okMessage(myHost, msg)))
			return false;
		return true;
	}

	@Override
	public void executeMsg(final Environmental myHost, final CMMsg msg)
	{
		super.executeMsg(myHost, msg);
		for(final Ability A : getOtherEffects())
			A.executeMsg(myHost, msg);
		final ScriptingEngine S=getScripter();
		if((S!=null)&&(affected!=null))
			S.executeMsg(myHost, msg);
	}

	@Override
	public List<Ability> getSpells()
	{
		final List<Ability> spells = new ArrayList<Ability>();
		spells.add(this);
		if(this.effects != null)
			spells.addAll(this.effects);
		return spells;
	}

	@Override
	public String getSpellList()
	{
		return "";
	}

	@Override
	public void setSpellList(final String list)
	{
	}

	@Override
	public int getSaveStatIndex()
	{
		return getStatCodes().length;
	}

	private static final String[] CODES={"CLASS", // 0
										 "TEXT", // 1
										 "NAME", // 2
										 "DISPLAY", // 3
										 "HELP", // 4
										 "TRIGSTR",// 5
										 "TICKS", // 6
										 "DELAY", // 7
										 "SPREAD", // 8
										 "CATCHCHANCE", // 9
										 "DIFFICULTY", // 10
										 "STARTMSG", // 11
										 "AFFECTMSG", // 12
										 "DONEMSG", // 13
										 "ADJUSTMENTS", // 14
										 "DAMAGE", // 15
										 "EFFECTS", // 16
										 "IMMUNEMASK", // 17
										 "MALICIOUS", // 18
										 "SCRIPT", // 19
										};

	@Override
	public String[] getStatCodes()
	{
		return CODES;
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

	@SuppressWarnings("rawtypes")
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
			return (String) V(ID, V_DISP);
		case 4:
			return (String) V(ID, V_HELP);
		case 5:
			return CMParms.toListString((String[]) V(ID, V_TRIG));
		case 6:
			return ((Pair) V(ID, V_TICKS)).first.toString();
		case 7:
			return ((Pair) V(ID, V_DELAY)).first.toString();
		case 8:
			return spreadWords(spreadBitmap());
		case 9:
			return ((Pair) V(ID, V_CTCH)).first.toString();
		case 10:
			return "" + difficultyLevel();
		case 11:
			return (String) V(ID, V_START);
		case 12:
			return (String) V(ID, V_AMSG);
		case 13:
			return (String) V(ID, V_DONE);
		case 14:
			return (String) V(ID, V_ADJS);
		case 15:
			return ((Pair) V(ID, V_DAMG)).first.toString();
		case 16:
			return ((Pair) V(ID, V_EFFS)).first.toString();
		case 17:
			return (String) V(ID, V_IMMU);
		case 18:
			return "" + isMalicious();
		case 19:
			return (String) V(ID, V_SCRP);
		default:
			if (code.equalsIgnoreCase("javaclass"))
				return "GenDisease";
			else
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
		final String v=(val==null)?"":val;
		switch(getCodeNum(code))
		{
		case 0:
			if(v.trim().length()>0)
			{
				V(ID,V_NAME); // force creation, if necc
				final Object[] O=vars.get(ID);
				vars.remove(ID);
				vars.put(v,O);
				if(num!=9)
					CMClass.delClass(CMObjectType.ABILITY,this);
				ID=v;
				if(num!=9)
					CMClass.addClass(CMObjectType.ABILITY,this);
			}
			break;
		case 1:
			setMiscText(v);
			break;
		case 2:
			SV(ID, V_NAME, v);
			break;
		case 3:
			SV(ID, V_DISP, v);
			break;
		case 4:
			SV(ID, V_HELP, v);
			break;
		case 5:
			SV(ID, V_TRIG, CMParms.parseCommas(v, true).toArray(new String[0]));
			break;
		case 6:
			SV(ID, V_TICKS, makeFormulaPair(v));
			break;
		case 7:
			SV(ID, V_DELAY, makeFormulaPair(v));
			break;
		case 8:
			SV(ID, V_SPRD, Integer.valueOf(spreadFrom(v)));
			break;
		case 9:
			SV(ID, V_CTCH, makeFormulaPair(v.length()==0?"100":v));
			break;
		case 10:
			SV(ID, V_DIFF, Integer.valueOf(CMath.s_int(v)));
			break;
		case 11:
			SV(ID, V_START, v);
			break;
		case 12:
			SV(ID, V_AMSG, v);
			break;
		case 13:
			SV(ID, V_DONE, v);
			break;
		case 14:
			SV(ID, V_ADJS, v);
			break;
		case 15:
			SV(ID, V_DAMG, makeFormulaPair(v));
			break;
		case 16:
			SV(ID, V_EFFS, makeEffectsPair(v));
			break;
		case 17:
			SV(ID, V_IMMU, v);
			break;
		case 18:
			SV(ID, V_MALI, Boolean.valueOf(v.trim().length()==0 || CMath.s_bool(v)));
			break;
		case 19:
			SV(ID, V_SCRP, v);
			break;
		default:
			if(code.equalsIgnoreCase("allxml")&&ID().equalsIgnoreCase("GenDisease"))
				parseAllXML(v);
			else
				super.setStat(code, v);
			break;
		}
	}

	@Override
	public boolean sameAs(final Environmental E)
	{
		if(!(E instanceof GenDisease))
			return false;
		if(!((GenDisease)E).ID().equals(ID))
			return false;
		if(!((GenDisease)E).text().equals(text()))
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
			{
				final XMLTag piece=CMLib.xml().getPieceFromPieces(V, getStatCodes()[c]);
				if(piece!=null)
					setStat(getStatCodes()[c],CMLib.xml().restoreAngleBrackets(piece.value()));
			}
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
