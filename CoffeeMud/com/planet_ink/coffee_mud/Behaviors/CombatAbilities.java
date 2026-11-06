package com.planet_ink.coffee_mud.Behaviors;
import com.planet_ink.coffee_mud.core.exceptions.CMException;
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
public class CombatAbilities extends ActiveTicker implements Threat.Holder // C18: its aggro is its threat table
{
	@Override
	public String ID()
	{
		return "CombatAbilities";
	}

	public int					combatMode			= 0;
	public Map<MOB, int[]>		aggro				= null;
	public short				chkDown				= 0;
	public List<String>			skillsNever			= null;
	public List<String>			skillsAlways		= null;
	protected boolean[]			wandUseCheck		= { false, false };
	protected boolean			proficient			= false;
	protected int				preCastSet			= Integer.MAX_VALUE;
	protected int				preCastDown			= Integer.MAX_VALUE;
	/** C1: whom it helps (SUPPORT=SELF|LEADER|GROUP|MASK:zapper) */
	protected SupportPicker.Support	support			= new SupportPicker.Support();
	/** C1: heal only those under this percent of their hit points, the most hurt first; 0 for as before */
	protected int				healAt				= 0;
	/** C1: what it cures first (CUREFIRST=Poison,Disease), or null */
	protected List<String>		cureFirst			= null;
	/** C1: whether it buffs its allies before a fight */
	protected boolean			buffOut				= false;
	/** C22: the rules on +SKILL(...) entries, in their order, or null for none */
	protected List<RotationRule>	rotation		= null;
	/** C22: OPENER=, the skill of a fight's first round, or null */
	protected String			opener				= null;
	/** C22: the rounds of this fight it has acted in */
	protected int				round				= 0;
	/** C22: the round each skill was last tried in, this fight */
	protected Map<String,Integer>	lastUsedRound	= new HashMap<String,Integer>();
	/** C22: what it tried this fight, ID@round, for STAT BEHAVIORS and tests */
	protected List<String>	rotationLog		= new ArrayList<String>();
	protected Map<String,Integer> pendingThreat = new HashMap<String,Integer>();

	/** C22: one +SKILL(...) entry's rules */
	protected static class RotationRule
	{
		String	id			= "";
		int		every		= 0;
		int		first		= 0;
		boolean	once		= false;
		int		below		= 0;
		int		cooldown	= 0;
		int		weight		= 1;
		boolean	self		= false;
		boolean	atCaster	= false;

		/** used only when due, not in the random choice */
		boolean scheduled()
		{
			return (every>0)||(first>0)||once||(below>0);
		}
	}
	protected String			lastSpell			= null;
	protected boolean			noStat				= false;
	protected boolean			noCombatStat		= false;
	protected boolean			useTurnBasedRule	= false;
	protected StringBuffer		record				= null;
	protected int				physicalDamageTaken	= 0;
	protected int				bucketSize			= 0;
	protected List<Ability>		bucket				= null;
	protected InternalWeaponSet	weaponSet			= new InternalWeaponSet();

	protected volatile boolean	actionOverride		= false;

	public final static int COMBAT_RANDOM=0;
	public final static int COMBAT_DEFENSIVE=1;
	public final static int COMBAT_OFFENSIVE=2;
	public final static int COMBAT_MIXEDOFFENSIVE=3;
	public final static int COMBAT_MIXEDDEFENSIVE=4;
	public final static int COMBAT_ONLYALWAYS=5;
	public final static String[] names={
		"RANDOM",
		"DEFENSIVE",
		"OFFENSIVE",
		"MIXEDOFFENSIVE",
		"MIXEDDEFENSIVE",
		"ONLYALWAYS"
	};

	private static class InternalWeaponSet
	{
		public Item	wand		= null;
		public Item	offHandWand	= null;
		public Item	weapon		= null;
	}

	@Override
	public String accountForYourself()
	{
		return "skillful ability using";
	}

	@Override
	public CMObject copyOf()
	{
		final CombatAbilities copy=(CombatAbilities)super.copyOf();
		copy.lastUsedRound=new HashMap<String,Integer>(lastUsedRound);
		copy.rotationLog=new ArrayList<String>(rotationLog);
		copy.pendingThreat=new HashMap<String,Integer>(pendingThreat);
		copy.aggro=aggro==null?null:new Hashtable<MOB,int[]>();
		if(aggro!=null)for(final Map.Entry<MOB,int[]> entry:aggro.entrySet())copy.aggro.put(entry.getKey(),entry.getValue().clone());
		copy.weaponSet=new InternalWeaponSet();
		return copy;
	}

	@Override
	protected String getRunState()
	{
		final XMLLibrary xml = CMLib.xml();
		final StringBuilder out = new StringBuilder("<COMBAT ROUND=\"").append(round)
			.append("\" TICKS=\"").append(tickDown).append("\" PRECAST=\"").append(preCastDown).append("\">");
		out.append(xml.convertXMLtoTag("LAST", xml.parseOutAngleBrackets(lastSpell == null ? "" : lastSpell)));
		for(final Map.Entry<String,Integer> use : lastUsedRound.entrySet())
			out.append("<USED ID=\"").append(use.getKey()).append("\" ROUND=\"").append(use.getValue()).append("\" />");
		for(final String entry : rotationLog) out.append(xml.convertXMLtoTag("LOG", xml.parseOutAngleBrackets(entry)));
		final Map<String,Integer> threats = new TreeMap<String,Integer>(pendingThreat);
		if(aggro != null) for(final Map.Entry<MOB,int[]> entry : aggro.entrySet())
			if(entry.getKey() != null && !entry.getKey().amDestroyed()) threats.put(InstanceIds.reference(entry.getKey()), Integer.valueOf(entry.getValue()[0]));
		for(final Map.Entry<String,Integer> entry : threats.entrySet())
			out.append("<THREAT REF=\"").append(entry.getKey()).append("\" VALUE=\"").append(entry.getValue()).append("\" />");
		return out.append("</COMBAT>").toString();
	}

	@Override
	protected void setRunState(final String text)
	{
		lastUsedRound.clear(); rotationLog.clear(); pendingThreat.clear(); threatTries = 0;
		round = 0;
		if(text == null || text.trim().isEmpty()) return;
		final List<XMLLibrary.XMLTag> roots = CMLib.xml().parseAllXML(text);
		if(roots.isEmpty() || !roots.get(0).tag().equals("COMBAT")) return;
		final XMLLibrary.XMLTag root = roots.get(0);
		round = Math.max(0, CMath.s_int(root.getParmValue("ROUND")));
		tickDown = CMath.s_int(root.getParmValue("TICKS"));
		preCastDown = CMath.s_int(root.getParmValue("PRECAST"));
		lastSpell = CMLib.xml().restoreAngleBrackets(root.getValFromPieces("LAST"));
		aggro = new Hashtable<MOB,int[]>();
		for(final XMLLibrary.XMLTag entry : root.contents())
		{
			if(entry.tag().equals("USED")) lastUsedRound.put(entry.getParmValue("ID"), Integer.valueOf(CMath.s_int(entry.getParmValue("ROUND"))));
			if(entry.tag().equals("LOG")) rotationLog.add(CMLib.xml().restoreAngleBrackets(entry.value()));
			if(entry.tag().equals("THREAT")) pendingThreat.put(entry.getParmValue("REF"), Integer.valueOf(CMath.s_int(entry.getParmValue("VALUE"))));
		}
	}

	/** ticks a saved threat reference has been looked for; after THREAT_TRIES the ones not found are dropped */
	protected int threatTries = 0;
	private static final int THREAT_TRIES = 10;

	private void restoreThreat()
	{
		if(pendingThreat.isEmpty())
			return;
		for(final Iterator<Map.Entry<String,Integer>> entries = pendingThreat.entrySet().iterator(); entries.hasNext();)
		{
			final Map.Entry<String,Integer> entry = entries.next();
			final Environmental target = InstanceIds.find(entry.getKey());
			if(target instanceof MOB)
			{
				if(aggro == null) aggro = new Hashtable<MOB,int[]>();
				aggro.put((MOB)target, new int[]{ entry.getValue().intValue() }); entries.remove();
			}
		}
		// a reference to a mob that is dead or gone is not kept for ever (each look is a search of the world)
		if((!pendingThreat.isEmpty()) && (++threatTries >= THREAT_TRIES))
			pendingThreat.clear();
	}

	@Override
	public void setParms(final String newParms)
	{
		minTicks=1;
		maxTicks=1;
		chance=100;
		bucketSize=CMParms.getParmInt(parms,"bsize",0);
		if(bucketSize == 0)
			bucket = null;
		else
			bucket = new Vector<Ability>(bucketSize);
		super.setParms(newParms);
	}

	protected void makeClass(final MOB mob, final String theParms, final String defaultClassName)
	{
		CharClass C=null;
		if(theParms.trim().length()==0)
		{
			C=CMClass.findCharClass(defaultClassName);
			if((mob.baseCharStats().getCurrentClass()!=C)
			&&(C!=null)
			&&(C.availabilityCode()!=0))
			{
				mob.baseCharStats().setCurrentClass(C);
				mob.recoverCharStats();
			}
			return;
		}
		final List<String> V=CMParms.parse(theParms.trim());
		final List<CharClass> classes=new ArrayList<CharClass>();
		for(int v=0;v<V.size();v++)
		{
			C=CMClass.findCharClass(V.get(v));
			if((C!=null)&&(C.availabilityCode()!=0))
				classes.add(C);
		}
		if(classes.size()==0)
		{
			C=CMClass.findCharClass(defaultClassName);
			if((mob.baseCharStats().getCurrentClass()!=C)
			&&(C!=null)
			&&(C.availabilityCode()!=0))
			{
				mob.baseCharStats().setCurrentClass(C);
				mob.recoverCharStats();
			}
			return;
		}
		for(int i=0;i<classes.size();i++)
		{
			C=classes.get(i);
			mob.baseCharStats().setCurrentClass(C);
			mob.baseCharStats().setClassLevel(C,mob.basePhyStats().level()/classes.size());
		}
		if((skillsNever!=null)&&(skillsNever.size()>0))
		{
			for (int s = 0; s < skillsNever.size(); s++)
			{
				mob.delAbility(mob.fetchAbility(skillsNever.get(s)));
				mob.delEffect(mob.fetchEffect(skillsNever.get(s)));
			}
		}
		mob.recoverCharStats();
	}

	protected String getParmsMinusCombatMode()
	{
		final Vector<String> V=CMParms.parse(getParms());
		for(int v=V.size()-1;v>=0;v--)
		{
			final String s=V.elementAt(v).toUpperCase();
			for(int i=0;i<names.length;i++)
			{
				if(names[i].startsWith(s))
				{
					combatMode=i;
					V.removeElementAt(v);
				}
			}
		}
		return CMParms.combine(V,0);
	}

	@Override
	public void endBehavior(final PhysicalAgent forMe)
	{
		if(CMProps.isState(CMProps.HostState.RUNNING)
		&&(!CMProps.isState(CMProps.HostState.SHUTTINGDOWN))
		&&(forMe != null)
		&&(!forMe.amDestroyed()))
		{
			// what these classes do to mobs is just too grotesque for words
			// so let's just reset the bastards to stock
			if(forMe instanceof MOB)
			{
				final MOB M=(MOB)forMe;
				if(M.isGeneric()
				&&(!M.amDead()))
				{
					final Room room=M.getStartRoom();
					{
						if(room != null)
						{
							// this is just scary.. there must be a better way...
							//M.bringToLife(room, true);
						}
					}
				}
			}
		}
		super.endBehavior(forMe);
	}

	protected void newCharacter(final MOB mob)
	{
		final Set<Ability> oldAbilities=new HashSet<Ability>();
		for(final Enumeration<Ability> a=mob.allAbilities();a.hasMoreElements();)
		{
			final Ability A=a.nextElement();
			if(A!=null)
			{
				final int proficiency=CMLib.ableMapper().getMaxProficiency(mob,true,A.ID())/2;
				if(A.proficiency()<proficiency)
					A.setProficiency(proficiency);
				oldAbilities.add(A);
			}
		}
		mob.charStats().getCurrentClass().startCharacter(mob,true,false);
		for(int a=0;a<mob.numAllAbilities();a++)
		{
			final Ability newOne=mob.fetchAbility(a);
			if((newOne!=null)
			&&(!oldAbilities.contains(newOne)))
			{
				if((!CMLib.ableMapper().qualifiesByLevel(mob,newOne))
				||((this.skillsNever!=null)&&(this.skillsNever.contains(newOne.ID()))))
				{
					mob.delAbility(newOne);
					mob.delEffect(mob.fetchEffect(newOne.ID()));
					a=a-1;
				}
				else
				{
					final int newProf = CMLib.ableMapper().getMaxProficiency(newOne.ID());
					if(newOne.proficiency()<newProf)
					{
						newOne.setProficiency(newProf);
						if(newOne.isAutoInvoked())
						{
							final Ability newAffect=mob.fetchEffect(newOne.ID());
							if(newAffect!=null)
								newAffect.setProficiency(newProf);
						}
					}
				}
			}
		}
	}

	public void setCombatStats(final MOB mob, final int attack, final int armor, final int damage, final int hp, final int mana, final int move, final boolean pushDownSpeed)
	{
		if(this.noCombatStat)
			return;
		Ability A=mob.fetchEffect("Prop_CombatAdjuster");
		if(A==null)
		{
			A=CMClass.getAbility("Prop_CombatAdjuster");
			if(A!=null)
			{
				String text="";
				if(attack!=0)
					text+=" ATTACK"+(attack>0?"+":"")+attack;
				if(armor!=0)
					text+=" ARMOR"+(armor>0?"+":"")+armor;
				if(damage!=0)
					text+=" DAMAGE"+(damage>0?"+":"")+damage;
				if(hp!=0)
					text+=" HP"+(hp>0?"+":"")+hp;
				if(mana!=0)
					text+=" MANA"+(mana>0?"+":"")+mana;
				if(move!=0)
					text+=" MOVE"+(move>0?"+":"")+move;
				if(pushDownSpeed)
					text+=" SPEED=1";
				if(text.length()>0)
				{
					mob.addPriorityEffect(A);
					A.makeNonUninvokable();
					A.makeLongLasting();
					A.setMiscText(text);
					A.setSavable(false);
					mob.recoverPhyStats();
					mob.recoverMaxState();
					mob.resetToMaxState();
				}
			}
		}
	}

	public void setCharStats(final MOB mob)
	{
		if(this.noStat)
			return;
		Ability A=mob.fetchEffect("Prop_StatAdjuster");
		if(A==null)
		{
			A=CMClass.getAbility("Prop_StatAdjuster");
			if(A!=null)
			{
				final CharClass C=mob.charStats().getCurrentClass();
				final int[] stats=C.maxStatAdjustments();
				int numStats=0;
				for(final int stat : CharStats.CODES.BASECODES())
				{
					if(stats[stat]!=0)
						numStats++;
				}
				if(numStats==0)
					return;
				int numPoints=mob.phyStats().level();
				if(mob.phyStats().level()>5)
					numPoints=5+((mob.phyStats().level()-5)/8);
				numPoints=numPoints/numStats;
				final StringBuilder parm=new StringBuilder("");
				for(final int stat : CharStats.CODES.BASECODES())
				{
					if(stats[stat]!=0)
						parm.append(CMStrings.limit(CharStats.CODES.NAME(stat),3)).append("=").append(numPoints).append(" ");
				}
				if(parm.length()>0)
				{
					mob.addNonUninvokableEffect(A);
					A.setMiscText(parm.toString().trim());
					A.setSavable(false);
					mob.recoverPhyStats();
					mob.recoverMaxState();
					mob.resetToMaxState();
				}
			}
		}
	}

	@Override
	public Map<MOB,int[]> threatTable()
	{
		return aggro; // C18
	}

	@Override
	public void adjustThreat(final MOB host, final MOB who, final int delta)
	{
		adjustAggro(host,who,delta); // C18: MPTHREAT, as its own reckoning would
	}

	public void adjustAggro(final MOB hostM, final MOB attackerM, final int amt)
	{
		if(aggro==null)
			aggro=new Hashtable<MOB,int[]>();
		synchronized(aggro)
		{
			int[] I = aggro.get(attackerM);
			if(I==null)
			{
				I=new int[]{0};
				aggro.put(attackerM, I);
			}
			I[0]+=amt;
			final MOB curVictim=hostM.getVictim();
			if((curVictim==attackerM)
			||(curVictim==null)
			||(!aggro.containsKey(curVictim)))
				return;
			final int vicAmt=aggro.get(curVictim)[0];
			if((I[0]>(vicAmt*1.5))
			&&(I[0]>hostM.maxState().getHitPoints()/10)
			&&(!attackerM.amDead())
			&&(attackerM.isInCombat()))
			{
				if((hostM.getGroupMembers(new HashSet<MOB>()).contains(attackerM))
				||(!CMLib.flags().canBeSeenBy(attackerM, hostM)))
					I[0]=0;
				else
				{
					hostM.setVictim(attackerM);
					for(final MOB M : aggro.keySet())
					if(M!=attackerM)
					{
						final int[] set=aggro.get(M);
						set[0]=set[0]/2;
					}
				}
			}
		}
	}

	@Override
	public boolean okMessage(final Environmental host, final CMMsg msg)
	{
		if(useTurnBasedRule
		&&(msg.source()==host)
		&&(msg.sourceMinor()==CMMsg.TYP_WEAPONATTACK)
		&&(!actionOverride)
		&&(msg.source().actions()>=1.0))
		{
			actionOverride = true;
			msg.source().setActions(msg.source().actions()-1.0);
		}
		return super.okMessage(host, msg);
	}

	@Override
	public void executeMsg(final Environmental host, final CMMsg msg)
	{
		if(host instanceof MOB)
		{
			final MOB mob=(MOB)host;
			if(mob.isInCombat())
			{
				final MOB victim=mob.getVictim();
				if (victim == null)
				{
				}
				else
				if((msg.targetMinor()==CMMsg.TYP_DAMAGE)
				&&(msg.value()>0)
				&&(msg.source()!=mob))
				{
					if(((msg.tool()==null)||(msg.tool() instanceof Item))
					&&(msg.target()==mob)
					&&(msg.source()==mob.getVictim()))
						physicalDamageTaken+=msg.value();
					if(msg.target()==host)
						adjustAggro(mob,msg.source(),msg.value()*2);
					else
					{
						if((victim==msg.source())
						||(msg.source().getGroupMembers(new HashSet<MOB>()).contains(victim)))
							adjustAggro(mob,msg.source(),msg.value());
					}
				}
				else
				if((msg.targetMinor()==CMMsg.TYP_HEALING)&&(msg.value()>0)
				&&(msg.source()!=mob)
				&&(msg.target()!=mob))
				{
					if((msg.target()==victim)
					||(msg.source().getGroupMembers(new HashSet<MOB>()).contains(victim)))
						adjustAggro(mob,msg.source(),msg.value()*3);
				}
				else
				if((msg.target()==victim)
				&&(msg.source()!=host)
				&&(msg.tool() instanceof Ability)
				&&(CMath.bset(((Ability)msg.tool()).flags(), Ability.FLAG_AGGROFYING))
				&&(msg.sourceMinor()!=CMMsg.TYP_TEACH))
					adjustAggro(mob,msg.source(),((Ability)msg.tool()).adjustedLevel(msg.source(), 0)*2);
				else
				if((msg.sourceMinor()==CMMsg.TYP_CAST_SPELL)
				&&(!msg.sourceMajor(CMMsg.MASK_ALWAYS))
				&&(msg.source()!=host)
				&&(msg.tool() instanceof Ability)
				&&((((Ability)msg.tool()).classificationCode()&Ability.ALL_ACODES)!=Ability.ACODE_SONG)
				&&(msg.source().isInCombat()))
				{
					if((msg.source()==victim)
					||(msg.source().getGroupMembers(new HashSet<MOB>()).contains(victim)))
					{
						int level=CMLib.ableMapper().qualifyingLevel(msg.source(),(Ability)msg.tool());
						if(level<=0)
							level=CMLib.ableMapper().lowestQualifyingLevel(msg.tool().ID());
						if(level>0)
							adjustAggro(mob,msg.source(),level);
					}
				}
			}
		}
		super.executeMsg(host,msg);
	}

	@Override
	public void startBehavior(final PhysicalAgent forMe)
	{
		super.startBehavior(forMe);
		skillsNever=null;
		wandUseCheck[0]=false;
		proficient=false;
		useTurnBasedRule=(CMProps.getIntVar(CMProps.Int.COMBATSYSTEM) == CombatLibrary.CombatSystem.TURNBASED.ordinal());
		// C22: +SKILL(EVERY=n ...) rules, read before the words, which skip the brackets
		readRotation(getParms());
		final Vector<String> V=CMParms.parse(getParms().replaceAll("\\([^)]*\\)",""));
		String s=null;
		Ability A=null;
		for(int v=0;v<V.size();v++)
		{
			s=V.elementAt(v);
			final String ls=s.toLowerCase();
			if(s.equalsIgnoreCase("proficient"))
				proficient=true;
			else
			if(ls.startsWith("min")||ls.startsWith("max")||ls.startsWith("chance"))
				continue;
			else
			if(s.equalsIgnoreCase("nostat")||s.equalsIgnoreCase("nostats"))
				noStat=true;
			else
			if(s.equalsIgnoreCase("noCombatStat")||s.equalsIgnoreCase("noCombatStats"))
				noCombatStat=true;
			else
			if((s.startsWith("-"))
			&&((A=CMClass.getAbility(s.substring(1)))!=null))
			{
				if(skillsNever==null)
					skillsNever=new Vector<String>();
				skillsNever.add(A.ID());
			}
			else
			if((s.startsWith("+"))
			&&((A=CMClass.getAbility(s.substring(1)))!=null))
			{
				if(skillsAlways==null)
					skillsAlways=new Vector<String>();
				skillsAlways.add(A.ID());
			}
		}
		// C1: whom it helps, and how
		final String p=getParms();
		support=SupportPicker.parse(p,"SUPPORT","LEADER");
		healAt=CMParms.getParmInt(p,"HEALAT",0);
		final String cf=CMParms.getParmStr(p,"CUREFIRST","").trim();
		cureFirst=(cf.length()==0)?null:CMParms.parseCommas(cf.toUpperCase(),true);
		buffOut=false;
		for(final String w : V)
		{
			if(w.toUpperCase().startsWith("BUFFOUTOFCOMBAT"))
				buffOut=true;
		}
		if((buffOut||(healAt>0)||(cureFirst!=null))&&(preCastSet==Integer.MAX_VALUE))
		{
			preCastSet=3; // out of a fight too, every 3 ticks
			preCastDown=3;
		}
		if(skillsNever instanceof Vector)
			((Vector<?>)skillsNever).trimToSize();
		if(skillsAlways instanceof Vector)
			((Vector<?>)skillsAlways).trimToSize();
	}

	protected boolean isRightCombatAbilities(final MOB mob)
	{
		// insures we only try this once!
		Behavior B;
		for(final Enumeration<Behavior> e=mob.behaviors();e.hasMoreElements();)
		{
			B=e.nextElement();
			if((B==null)||(B==this))
				return true;
			else
			if(B instanceof CombatAbilities)
				return false;
		}
		return true;
	}

	/**
	 * C1: whom an ability that helps others would go to: the leader (as
	 * before), no one but itself (SUPPORT=SELF), or under SUPPORT=GROUP or
	 * MASK the most hurt ally it would help.
	 * @param mob the caster
	 * @param A the ability
	 * @param leader whom it follows, or null
	 * @return the one, or null
	 */
	protected MOB helpedBy(final MOB mob, final Ability A, final MOB leader)
	{
		if(support.mode.equals("LEADER"))
			return leader;
		if(support.mode.equals("SELF"))
			return null;
		for(final MOB M : SupportPicker.allies(mob,support))
		{
			if((M!=mob)&&(A.castingQuality(mob,M)==Ability.QUALITY_BENEFICIAL_OTHERS))
				return M;
		}
		return null;
	}

	/**
	 * C1: a cure, a heal for the most hurt under HEALAT, or (out of a fight)
	 * a buff, and on whom.
	 * @param mob the caster
	 * @param victim whom it fights, or null
	 * @return the ability and its target, or null
	 */
	protected Object[] pickSupport(final MOB mob, final MOB victim)
	{
		final List<Ability> skills=new ArrayList<Ability>();
		for(final Enumeration<Ability> a=mob.allAbilities();a.hasMoreElements();)
		{
			final Ability A=a.nextElement();
			if((A==null)
			||(A.isAutoInvoked())
			||(A.triggerStrings()==null)
			||(A.triggerStrings().length==0)
			||((skillsAlways!=null)&&(!skillsAlways.contains(A.ID())))
			||((skillsNever!=null)&&(skillsNever.contains(A.ID()))))
				continue;
			skills.add(A);
		}
		return SupportPicker.pick(mob,skills,SupportPicker.allies(mob,support),healAt,cureFirst,buffOut&&(victim==null));
	}

	/**
	 * C22: reads the rules of +SKILL(...) entries and OPENER=.
	 * @param parms the parameters
	 */
	protected void readRotation(final String parms)
	{
		rotation=null;
		final String o=CMParms.getParmStr(parms,"OPENER","").trim();
		opener=null;
		if(o.length()>0)
		{
			final Ability A=CMClass.getAbility(o);
			opener=(A!=null)?A.ID():o;
		}
		final java.util.regex.Matcher m=java.util.regex.Pattern.compile("\\+([A-Za-z0-9_]+)\\(([^)]*)\\)").matcher(parms);
		while(m.find())
		{
			final Ability A=CMClass.getAbility(m.group(1));
			final RotationRule r=new RotationRule();
			r.id=(A!=null)?A.ID():m.group(1);
			for(final String w : CMParms.parse(m.group(2).toUpperCase()))
			{
				final int x=w.indexOf('=');
				final String k=(x<0)?w:w.substring(0,x);
				final String v=(x<0)?"":w.substring(x+1).replace("%","");
				if(k.equals("EVERY"))
					r.every=CMath.s_int(v);
				else
				if(k.equals("FIRST"))
					r.first=CMath.s_int(v);
				else
				if(k.equals("ONCE"))
					r.once=true;
				else
				if(k.equals("BELOW"))
					r.below=CMath.s_int(v);
				else
				if(k.equals("COOLDOWN"))
					r.cooldown=CMath.s_int(v);
				else
				if(k.equals("WEIGHT"))
					r.weight=Math.max(1,CMath.s_int(v));
				else
				if(k.equals("SELF"))
					r.self=true;
				else
				if(k.equals("TARGET")&&v.equals("CASTER"))
					r.atCaster=true;
			}
			if(rotation==null)
				rotation=new ArrayList<RotationRule>();
			rotation.add(r);
		}
		if((rotation==null)&&(opener!=null))
			rotation=new ArrayList<RotationRule>();
	}

	protected RotationRule ruleOf(final String id)
	{
		if(rotation!=null)
		{
			for(final RotationRule r : rotation)
			{
				if(r.id.equalsIgnoreCase(id))
					return r;
			}
		}
		return null;
	}

	/** C22: whether it was tried this fight */
	protected boolean tried(final String id)
	{
		for(final String s : rotationLog)
		{
			if(s.startsWith(id+"@"))
				return true;
		}
		return false;
	}

	/** C22: whether its cooldown is over */
	protected boolean cooledDown(final RotationRule r)
	{
		final Integer was=lastUsedRound.get(r.id);
		return (r.cooldown<=0)||(was==null)||((round-was.intValue())>r.cooldown);
	}

	/** C22: whether the random choice may use it now: not a scheduled one, and cooled down */
	protected boolean rotationAllows(final MOB mob, final Ability A)
	{
		final RotationRule r=ruleOf(A.ID());
		if(r==null)
			return true;
		return (!r.scheduled())&&cooledDown(r);
	}

	/** C22: a +SKILL by WEIGHT= */
	protected Ability weightedAlways(final MOB mob)
	{
		int total=0;
		final List<Ability> choices=new ArrayList<Ability>();
		final List<Integer> weights=new ArrayList<Integer>();
		for(final String id : skillsAlways)
		{
			final Ability A=mob.fetchAbility(id);
			if(A==null)
				continue;
			final RotationRule r=ruleOf(id);
			final int w=(r==null)?1:r.weight;
			choices.add(A);
			weights.add(Integer.valueOf(w));
			total+=w;
		}
		if(total<=0)
			return mob.fetchRandomAbility();
		int roll=CMLib.dice().roll(1,total,0);
		for(int i=0;i<choices.size();i++)
		{
			roll-=weights.get(i).intValue();
			if(roll<=0)
				return choices.get(i);
		}
		return choices.get(choices.size()-1);
	}

	/** C22: whom a rule's skill goes to */
	protected MOB rotationTarget(final MOB mob, final MOB victim, final Ability A, final RotationRule r)
	{
		if((r!=null)&&r.self)
			return mob;
		if((r!=null)&&r.atCaster)
		{
			MOB best=null;
			int most=0;
			for(final MOB M : Threat.enemies(mob))
			{
				int n=0;
				for(final Enumeration<Ability> a=M.allAbilities();a.hasMoreElements();)
				{
					final Ability B=a.nextElement();
					final int code=(B==null)?-1:(B.classificationCode()&Ability.ALL_ACODES);
					if((code==Ability.ACODE_SPELL)||(code==Ability.ACODE_PRAYER)||(code==Ability.ACODE_CHANT)||(code==Ability.ACODE_SONG))
						n++;
				}
				if(n>most)
				{
					most=n;
					best=M;
				}
			}
			if(best!=null)
				return best;
		}
		if(A.castingQuality(mob,mob)==Ability.QUALITY_BENEFICIAL_SELF)
			return mob;
		return victim;
	}

	/**
	 * C22: the opener on a fight's first round, or the first scheduled skill
	 * whose turn it is (EVERY, FIRST, ONCE, BELOW).
	 * @param mob the mob
	 * @param victim whom it fights
	 * @return the skill and its target, or null
	 */
	protected Object[] rotationPick(final MOB mob, final MOB victim)
	{
		if((round==1)&&(opener!=null))
		{
			final Ability A=mob.fetchAbility(opener);
			if(A!=null)
				return new Object[]{A,rotationTarget(mob,victim,A,ruleOf(opener))};
		}
		for(final RotationRule r : rotation)
		{
			if(!r.scheduled())
				continue;
			final Ability A=mob.fetchAbility(r.id);
			if(A==null)
				continue;
			if(r.once&&tried(r.id))
				continue; // ONCE: once tried this fight, whether it worked or not
			if((r.below>0)&&(SupportPicker.hpPct(mob)>=r.below))
				continue;
			if(!cooledDown(r))
				continue;
			if(r.every>0)
			{
				final int first=(r.first>0)?r.first:r.every;
				if((round<first)||(((round-first)%r.every)!=0))
					continue;
			}
			else
			if((r.first>0)&&(round<r.first))
				continue;
			return new Object[]{A,rotationTarget(mob,victim,A,r)};
		}
		return null;
	}

	protected Ability useSkill(final MOB mob, final MOB victim, final MOB leader) throws CMException
	{
		int tries=0;
		Ability tryA=null;
		// now find a skill to use
		Ability A=null;

		MOB target = null;
		int victimQuality=Ability.QUALITY_INDIFFERENT;
		int selfQuality=Ability.QUALITY_INDIFFERENT;
		int leaderQuality=Ability.QUALITY_INDIFFERENT;
		// C22: the opener, and skills whose turn it is, first
		if((rotation!=null)&&(victim!=null))
		{
			round++;
			final Object[] pick=rotationPick(mob,victim);
			if(pick!=null)
			{
				tryA=(Ability)pick[0];
				target=(MOB)pick[1];
			}
		}
		// C1: cures, heals for the most hurt under HEALAT, and buffs before a fight, first
		if((tryA==null)&&((healAt>0)||(cureFirst!=null)||(buffOut&&(victim==null))))
		{
			final Object[] pick=pickSupport(mob,victim);
			if(pick!=null)
			{
				tryA=(Ability)pick[0];
				target=(MOB)pick[1];
			}
		}
		while((tryA==null)
		&&((++tries)<100)
		&&(mob.numAllAbilities()>0))
		{
			if((bucketSize > 0)
			&&(bucket != null)
			&&(bucket.size()>=bucketSize))
				A=bucket.get(CMLib.dice().roll(1, bucket.size(), -1));
			else
			if((combatMode==COMBAT_ONLYALWAYS)&&(this.skillsAlways!=null)&&(this.skillsAlways.size()>0))
				A=mob.fetchAbility(skillsAlways.get(CMLib.dice().roll(1,skillsAlways.size(),-1)));
			else
			if((rotation!=null)&&(skillsAlways!=null)&&(skillsAlways.size()>0))
				A=weightedAlways(mob); // C22: WEIGHT=
			else
				A=mob.fetchRandomAbility();

			if((A!=null)&&(rotation!=null)&&(!rotationAllows(mob,A)))
				continue; // C22: a scheduled skill waits its turn; COOLDOWN
			if((A==null)
			||(A.isAutoInvoked())
			||(A.triggerStrings()==null)
			||(A.triggerStrings().length==0)
			||((skillsAlways!=null)&&(!skillsAlways.contains(A.ID())))
			||((skillsNever!=null)&&(skillsNever.contains(A.ID()))))
				continue;

			victimQuality=(victim!=null)?A.castingQuality(mob,victim):Ability.QUALITY_INDIFFERENT;
			selfQuality=A.castingQuality(mob,mob);
			final MOB helped=helpedBy(mob,A,leader); // C1: the leader, or whom SUPPORT= names
			leaderQuality=((mob==helped)||(helped==null))?Ability.QUALITY_INDIFFERENT:A.castingQuality(mob,helped);

			if(victimQuality==Ability.QUALITY_MALICIOUS)
			{
				switch(combatMode)
				{
				case COMBAT_RANDOM:
				case COMBAT_ONLYALWAYS:
					tryA=A;
					break;
				case COMBAT_DEFENSIVE:
					if(CMLib.dice().rollPercentage()<=5)
						tryA=A;
					break;
				case COMBAT_OFFENSIVE:
					tryA=A;
					break;
				case COMBAT_MIXEDOFFENSIVE:
					if(CMLib.dice().rollPercentage()<=75)
						tryA=A;
					break;
				case COMBAT_MIXEDDEFENSIVE:
					if(CMLib.dice().rollPercentage()<=25)
						tryA=A;
					break;
				}
			}
			else
			if((selfQuality==Ability.QUALITY_BENEFICIAL_SELF)
			||(leaderQuality==Ability.QUALITY_BENEFICIAL_OTHERS))
			{
				switch(combatMode)
				{
				case COMBAT_RANDOM:
				case COMBAT_ONLYALWAYS:
					tryA=A;
					break;
				case COMBAT_DEFENSIVE:
					tryA=A;
					break;
				case COMBAT_OFFENSIVE:
					if(CMLib.dice().rollPercentage()<=5)
						tryA=A;
					break;
				case COMBAT_MIXEDOFFENSIVE:
					if(CMLib.dice().rollPercentage()<=25)
						tryA=A;
					break;
				case COMBAT_MIXEDDEFENSIVE:
					if(CMLib.dice().rollPercentage()<=75)
						tryA=A;
					break;
				}
			}
			target=victim;
			if(selfQuality==Ability.QUALITY_BENEFICIAL_SELF)
				target=mob;
			else
			if(leaderQuality==Ability.QUALITY_BENEFICIAL_OTHERS)
				target=((helped==null)||(mob.location()!=helped.location()))?mob:helped;
			if((tryA!=null)&&(healAt>0)&&(target!=null)&&SupportPicker.isHeal(tryA)&&(SupportPicker.hpPct(target)>=healAt))
				tryA=null; // C1: not hurt enough yet
			if((target != null) && (tryA != null))
			{
				final Ability eA=target.fetchEffect(tryA.ID());
				if((eA!=null)&&(!eA.isNowAnAutoEffect()))
					tryA = null;
			}
		}

		if((tryA!=null)
		&&(actionOverride||mob.actions()>=CMProps.getSkillCombatActionCost(tryA.ID())))
		{
			actionOverride=false;
			if((bucketSize>0)
			&&(bucket!=null)
			&&(!bucket.contains(tryA)))
				bucket.add(tryA);
			if(CMath.bset(tryA.usageType(),Ability.USAGE_MANA))
			{
				if((CMath.random()>CMath.div(mob.curState().getMana(), mob.maxState().getMana()))
				||(mob.curState().getMana() < tryA.usageCost(mob,false)[0]))
				{
				   if((CMLib.dice().rollPercentage()>30)
				   ||(CMProps.getIntVar(CMProps.Int.MANACONSUMETIME)<=0)
				   ||((mob.amFollowing()!=null)&&(!mob.amFollowing().isMonster())))
					   throw new CMException("Not enough mana");
				   mob.curState().adjMana(tryA.usageCost(mob,false)[0],mob.maxState());
				}
				mob.curState().adjMana(5,mob.maxState());
			}
			if(CMath.bset(tryA.usageType(),Ability.USAGE_MOVEMENT))
			{
				if((CMath.random()>CMath.div(mob.curState().getMovement(),mob.maxState().getMovement()))
				||(mob.curState().getMovement()<tryA.usageCost(mob,false)[1]))
			 	   throw new CMException("Not enough movement");
				mob.curState().adjMovement(5,mob.maxState());
			}
			if(CMath.bset(tryA.usageType(),Ability.USAGE_HITPOINTS))
			{
				if((CMath.random()>CMath.div(mob.curState().getHitPoints(),mob.maxState().getHitPoints()))
				   ||(mob.curState().getHitPoints()<tryA.usageCost(mob,false)[2]))
				 	   throw new CMException("Not enough hp");
			}

			if(proficient)
				tryA.setProficiency(100);
			else
			{
				final int qualLevel=CMLib.ableMapper().qualifyingLevel(mob,tryA);
				if(qualLevel<=0)
					tryA.setProficiency(75);
				else
				{
					int levelDiff=mob.basePhyStats().level()-qualLevel;
					if((levelDiff>50)||(levelDiff<0))
						levelDiff=50;
					tryA.setProficiency(50+levelDiff);
				}
			}
			if(target==null)
				return null;
			if(rotation!=null)
			{
				rotationLog.add(tryA.ID()+"@"+round); // C22
				lastUsedRound.put(tryA.ID(),Integer.valueOf(round)); // a cooldown starts with the try, as a fizzle spends it too
			}
			boolean skillUsed=tryA.invoke(mob,new XVector<String>(target.name()),null,false,0);
			if((combatMode==COMBAT_ONLYALWAYS)&&(!skillUsed))
			{
				int retries=0;
				while((++retries<10)&&(!skillUsed))
					skillUsed=tryA.invoke(mob,new XVector<String>(target.name()),null,false,0);
			}
			if(skillUsed)
			{
				if(mob.actions()>=CMProps.getSkillCombatActionCost(tryA.ID()))
					mob.setActions(mob.actions()-CMProps.getSkillCombatActionCost(tryA.ID()));
				skillUsed=true;
				if(lastSpell!=null)
					lastSpell=tryA.ID();
			}
			else
			{
				if(lastSpell!=null)
					lastSpell="!"+tryA.ID();
				if(record!=null)
					record.append("!");
			}
			if(record!=null)
				record.append(tryA.ID()).append("; ");
			return skillUsed?tryA:null;
		}
		return null;
	}

	@Override
	public boolean tick(final Tickable ticking, final int tickID)
	{
		restoreThreat();
		super.tick(ticking,tickID);
		if(ticking==null)
			return true;
		if((tickID!=Tickable.TICKID_MOB)
		||(!(ticking instanceof MOB)))
		{
			Log.errOut("CombatAbilities",ticking.name()+" does not want to fight.");
			((Behavable)ticking).delBehavior(this);
			return false;
		}
		final MOB mob=(MOB)ticking;

		if(!mob.isInCombat())
		{
			if(round>0)
			{
				round=0; // C22: a new fight starts the rotation again
				lastUsedRound.clear();
				rotationLog.clear();
			}
			if(aggro!=null)
			{
				synchronized(aggro)
				{
					aggro=null;
				}
			}
			if((preCastSet < Integer.MAX_VALUE) && (preCastSet >0) && ((--preCastDown)<=0))
			{
				if(!canActAtAll(mob))
					return true;
				preCastDown=preCastSet;
				if(!isRightCombatAbilities(mob))
					return true;
				try
				{
					useSkill(mob,null,null);
				}
				catch(final CMException cme)
				{
				}
			}
			return true;
		}

		MOB victim=mob.getVictim();
		if(victim==null)
			return true;

		if(!canActAtAll(mob))
			return true;

		// insures we only try this once!
		if(!isRightCombatAbilities(mob))
			return true;

		if(!super.canAct(ticking, tickID))
			return true;

		final Room R=mob.location();
		if((lastSpell!=null)&&(lastSpell.length()>0))
			lastSpell="";

		if(!wandUseCheck[0])
		{
			wandUseCheck[0]=true;
			final Ability wandUse=mob.fetchAbility("Skill_WandUse");
			wandUseCheck[1]=false;
			if(wandUse!=null)
			{
				wandUseCheck[1]=true;
				wandUse.setProficiency(100);
				wandUse.setInvoker(mob);
			}
		}

		boolean rebuildSet=false;
		final int rtt=mob.rangeToTarget();
		if((weaponSet.weapon==null)||(weaponSet.weapon.owner()!=mob)
		||(weaponSet.weapon.amDestroyed())
		||(weaponSet.weapon.amWearingAt(Wearable.IN_INVENTORY))
		||(weaponSet.weapon.minRange()>rtt)
		||(weaponSet.weapon.maxRange()<rtt))
			rebuildSet=true;
		if((weaponSet.wand!=null)
		&&((weaponSet.wand.owner()!=mob)||(weaponSet.wand.amDestroyed())||(weaponSet.wand.amWearingAt(Wearable.IN_INVENTORY))))
			rebuildSet=true;
		if((weaponSet.offHandWand!=null)
		&&((weaponSet.offHandWand.owner()!=mob)||(weaponSet.offHandWand.amDestroyed())||(!weaponSet.offHandWand.amWearingAt(Wearable.IN_INVENTORY))))
			rebuildSet=true;
		if(rebuildSet)
		{
			weaponSet.weapon=null;
			weaponSet.wand=null;
			weaponSet.offHandWand=null;
			Item newWeapon=null;
			for(final Enumeration<Item> i=mob.items();i.hasMoreElements();)
			{
				final Item I=i.nextElement();
				if(I instanceof Wand)
				{
					if(!I.amWearingAt(Wearable.IN_INVENTORY))
						weaponSet.wand=I;
					else
						weaponSet.offHandWand=I;
				}
				if(I instanceof Weapon)
				{
					if((((Weapon)I).minRange()<=rtt)&&(((Weapon)I).maxRange()>=rtt))
					{
						if(I.amWearingAt(Wearable.WORN_WIELD))
							weaponSet.weapon=I;
						else
						if(((newWeapon==null)&&(weaponSet.weapon==null))
						||(I.amWearingAt(Wearable.WORN_HELD)))
							newWeapon=I;
					}
				}
			}
			// first look for an appropriate weapon to weild
			if((weaponSet.weapon==null)&&((--chkDown)<=0))
			{
				if((newWeapon==null)&&(R!=null))
				{
					final List<Item> choices=new ArrayList<Item>(1);
					for(final Enumeration<Item> i=R.items();i.hasMoreElements();)
					{
						final Item I=i.nextElement();
						if((!(I instanceof Weapon))
						||(((Weapon)I).minRange()>rtt)
						||(((Weapon)I).maxRange()<rtt)
						||(I.container()!=null)
						||(!CMLib.flags().isGettable(I))
						||(I.phyStats().level()>mob.phyStats().level()))
							continue;
						choices.add(I);
					}
					final Item I=(choices.size()==0)?null:(Item)choices.get(CMLib.dice().roll(1,choices.size(),-1));
					if(I!=null)
					{
						CMLib.commands().forceInternalCommand(mob,"GET",I);
						if(mob.isMine(I))
							newWeapon=I;
					}
				}
				if(newWeapon!=null)
				{
					CMLib.commands().forceInternalCommand(mob,"WIELD",newWeapon);
				}
				chkDown=5;
			}
		}

		// next deal with aggro changes
		if(aggro!=null)
		{
			synchronized(aggro)
			{
				int winAmt=0;
				MOB winMOB = null;
				int vicAmt=0;
				final int minAmt=mob.maxState().getHitPoints()/10;
				if(aggro.containsKey(victim))
					vicAmt = aggro.get(victim)[0];
				int[] amt = null;
				for(final MOB M : aggro.keySet())
				{
					amt = aggro.get(M);
					if((amt[0]>winAmt)
					&&(CMLib.flags().canBeSeenBy(M,mob)))
					{
						winAmt=amt[0];
						winMOB=M;
					}
				}
				if((winAmt>minAmt)
				&&(winAmt>(vicAmt+(vicAmt/2)))
				&&(winMOB!=null)
				&&(!winMOB.amDead())
				&&(winMOB.isInCombat())
				&&(!mob.getGroupMembers(new HashSet<MOB>()).contains(winMOB)))
				{
					mob.setVictim(winMOB);
					victim=mob.getVictim();
					aggro.clear();
				}
			}
		}
		if(victim==null)
			return true;

		final MOB leader=mob.amFollowing();

		boolean skillUsed=false;
		try
		{
			skillUsed=useSkill(mob, victim, leader)!=null;
		}
		catch (final CMException cme)
		{
			return true;
		}

		Ability A=null;
		// if a skill use failed, take a stab at wanding
		if((!skillUsed)
		&&(wandUseCheck[1])
		&&(victim.location()!=null)
		&&(!victim.amDead())
		&&((weaponSet.wand!=null)||(weaponSet.offHandWand!=null)))
		{
			if((weaponSet.wand==null)&&(weaponSet.offHandWand!=null)&&(weaponSet.offHandWand.canWear(mob,Wearable.WORN_HELD)))
			{
				final Vector<String> V=new Vector<String>();
				V.addElement("hold");
				V.addElement(weaponSet.offHandWand.name());
				mob.doCommand(V,MUDCmdProcessor.METAFLAG_FORCED);
			}
			else
			if(weaponSet.wand!=null)
			{
				A=((Wand)weaponSet.wand).getSpell();
				if(A!=null)
				{
					final MOB target;
					if(A.castingQuality(mob,mob)==Ability.QUALITY_BENEFICIAL_SELF)
						target=mob;
					else
					if(A.castingQuality(mob,victim)==Ability.QUALITY_MALICIOUS)
						target=victim;
					else
					if(((mob!=leader)&&(leader!=null))&&(A.castingQuality(mob,leader)==Ability.QUALITY_BENEFICIAL_OTHERS))
						target=((mob.location()!=leader.location()))?mob:leader;
					else
						target=null;
					if(target!=null)
					{
						final Vector<String> V=new Vector<String>();
						V.addElement("sayto");
						V.addElement(target.name());
						V.addElement(((Wand)weaponSet.wand).magicWord());
						mob.doCommand(V,MUDCmdProcessor.METAFLAG_FORCED);
					}
				}
			}
		}
		return true;
	}

	protected static String[] CODES=null;

	@Override
	public String[] getStatCodes()
	{
		if(CombatAbilities.CODES==null)
		{
			final String[] superCodes=super.getStatCodes();
			CODES=new String[superCodes.length+8];
			for(int c=0;c<superCodes.length;c++)
				CODES[c]=superCodes[c];
			CODES[CODES.length-8]="RECORD";
			CODES[CODES.length-7]="PROF";
			CODES[CODES.length-6]="LASTSPELL";
			CODES[CODES.length-5]="PRECAST";
			CODES[CODES.length-4]="PHYSDAMTAKEN";
			CODES[CODES.length-3]="SKILLSALWAYS";
			CODES[CODES.length-2]="SKILLSNEVER";
			CODES[CODES.length-1]="COMBATMODE";
		}
		return CODES;
	}

	@Override
	protected int getCodeNum(final String code)
	{
		final String[] CODES=getStatCodes();
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
		int x=getCodeNum(code);
		if(x<super.getStatCodes().length)
			return super.getStat(code);
		x=x-super.getStatCodes().length;
		switch(x)
		{
		case 0:
			return (record == null) ? "" : record.toString();
		case 1:
			return Boolean.toString(proficient);
		case 2:
			return lastSpell != null ? lastSpell : "";
		case 3:
			return Integer.toString(preCastSet);
		case 4:
			return Integer.toString(physicalDamageTaken);
		case 5:
			return (skillsAlways == null) ? "" : CMParms.toSemicolonListString(skillsAlways);
		case 6:
			return (skillsAlways == null) ? "" : CMParms.toSemicolonListString(skillsNever);
		case 7:
			return Integer.toString(combatMode);
		}
		return "";
	}

	@Override
	public void setStat(final String code, final String val)
	{
		int x=getCodeNum(code);
		if(x<super.getStatCodes().length)
			super.setStat(code,val);
		x=x-super.getStatCodes().length;
		switch(x)
		{
		case 0:
			if(val.length()==0)
				record=null;
			else
				record=new StringBuffer(val.trim());
			break;
		case 1:
			proficient=CMath.s_bool(val);
			break;
		case 2:
			lastSpell=val;
			break;
		case 3:
			preCastSet=CMath.s_int(val);
			preCastDown=CMath.s_int(val);
			break;
		case 4:
			physicalDamageTaken=CMath.s_int(val);
			break;
		case 5:
			skillsAlways=CMParms.parseSemicolons(val,true);
			if(skillsAlways.size()==0)
				skillsAlways=null;
			break;
		case 6:
			skillsNever=CMParms.parseSemicolons(val,true);
			if(skillsNever.size()==0)
				skillsNever=null;
			break;
		case 7:
			if(CMath.isInteger(val))
				combatMode=CMath.s_int(val);
			else
				combatMode=CMParms.indexOf(names,val.toUpperCase().trim());
			break;
		}
	}
}
