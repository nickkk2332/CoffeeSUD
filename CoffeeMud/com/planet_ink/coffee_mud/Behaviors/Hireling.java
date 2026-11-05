package com.planet_ink.coffee_mud.Behaviors;
import com.planet_ink.coffee_mud.Libraries.interfaces.*;
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
import com.planet_ink.coffee_mud.Locales.interfaces.*;
import com.planet_ink.coffee_mud.MOBS.interfaces.*;
import com.planet_ink.coffee_mud.Races.interfaces.*;

import java.util.*;
import java.util.concurrent.TimeUnit;

/*
   Copyright 2003-2025 Bo Zimmerman

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

public class Hireling extends StdBehavior
{
	@Override
	public String ID()
	{
		return "Hireling";
	}

	@Override
	protected String getRunState()
	{
		if((onTheJobUntil==0)&&(partials.size()==0))
			return "";
		final StringBuilder str=new StringBuilder("");
		str.append(CMLib.xml().convertXMLtoTag("FOR",CMLib.xml().parseOutAngleBrackets(workingFor)));
		if(onTheJobUntil!=0)
			str.append(CMLib.xml().convertXMLtoTag("LEFT",Math.max(1,onTheJobUntil-GameTime.now())));
		str.append(CMLib.xml().convertXMLtoTag("WARN",(nextWarn==Long.MAX_VALUE)?-1:Math.max(0,nextWarn-GameTime.now())));
		if(lastUpkeep>0) // N21
			str.append(CMLib.xml().convertXMLtoTag("PAIDAGO",Math.max(0,GameTime.now()-lastUpkeep)));
		if(unpaidDays>0)
			str.append(CMLib.xml().convertXMLtoTag("UNPAID",unpaidDays));
		for(final Map.Entry<String,Double> e : new XVector<Map.Entry<String,Double>>(partials.entrySet()))
			str.append("<PAID N=\"").append(CMLib.xml().parseOutAngleBrackets(e.getKey())).append("\">").append(e.getValue()).append("</PAID>");
		return str.toString();
	}

	@Override
	protected void setRunState(final String xml)
	{
		final List<com.planet_ink.coffee_mud.Libraries.interfaces.XMLLibrary.XMLTag> tags=CMLib.xml().parseAllXML(xml);
		workingFor=CMLib.xml().restoreAngleBrackets(CMLib.xml().getValFromPieces(tags,"FOR"));
		final long left=CMath.s_long(CMLib.xml().getValFromPieces(tags,"LEFT"));
		onTheJobUntil=(left>0)?(GameTime.now()+left):0;
		final long warn=CMath.s_long(CMLib.xml().getValFromPieces(tags,"WARN"));
		nextWarn=(warn<0)?Long.MAX_VALUE:(GameTime.now()+warn);
		final String paidAgo=CMLib.xml().getValFromPieces(tags,"PAIDAGO"); // N21
		lastUpkeep=(paidAgo.length()>0)?(GameTime.now()-CMath.s_long(paidAgo)):0;
		unpaidDays=CMath.s_int(CMLib.xml().getValFromPieces(tags,"UNPAID"));
		partials.clear();
		for(final com.planet_ink.coffee_mud.Libraries.interfaces.XMLLibrary.XMLTag tag : tags)
		{
			if(tag.tag().equalsIgnoreCase("PAID"))
				partials.put(CMLib.xml().restoreAngleBrackets(tag.getParmValue("N")),Double.valueOf(CMath.s_double(tag.value())));
		}
	}

	protected Map<String, Double>	partials		= new Hashtable<String, Double>();
	protected String				workingFor		= "";
	protected long					nextWarn		= 0;
	protected long					onTheJobUntil	= 0;
	protected double				price			= 100.0;
	protected int					minutes			= 30;
	protected MaskingLibrary.CompiledZMask zapperMask = null;

	// N21: the terms, KEY=value anywhere in the parameters
	protected List<String>    keywords        = new XVector<String>("HIRE","HIRELING","WORK","WORKS","TIME","AVAILABLE");
	protected String        msgOffer        = null;
	protected String        msgHired        = null;
	protected String        msgRefuse        = null;
	protected String        msgExpire        = null;
	protected String        msgNoOrder        = null;
	protected String        contract        = "MINUTES";
	protected String        contractQuest    = "";
	protected double        upkeep            = 0;
	protected int            fleeHp            = 0;
	protected int            loyal            = 25;
	protected List<String>    refuseOrders    = new ArrayList<String>();
	protected long            lastUpkeep        = 0;
	protected int            unpaidDays        = 0;

	protected static final java.util.regex.Pattern TERM=java.util.regex.Pattern.compile(
		"(?i)(^|[\\s;])(KEYWORDS|MSG_OFFER|MSG_HIRED|MSG_REFUSE|MSG_EXPIRE|MSG_NOORDER|CONTRACT|UPKEEP|FLEEHP|LOYAL|REFUSE)=(\"([^\"]*)\"|[^\\s;]+)");

	/**
	 * N21: reads the KEY=value terms out of the parameters.
	 * @param p the parameters
	 * @return the rest: price;minutes;mask
	 */
	protected String readTerms(final String p)
	{
		keywords=new XVector<String>("HIRE","HIRELING","WORK","WORKS","TIME","AVAILABLE");
		msgOffer=msgHired=msgRefuse=msgExpire=msgNoOrder=null;
		contract="MINUTES";
		contractQuest="";
		upkeep=0;
		fleeHp=0;
		loyal=25;
		refuseOrders=new ArrayList<String>();
		if(p==null)
			return "";
		final java.util.regex.Matcher m=TERM.matcher(p);
		final StringBuffer rest=new StringBuffer("");
		while(m.find())
		{
			final String key=m.group(2).toUpperCase();
			final String val=(m.group(4)!=null)?m.group(4):m.group(3);
			if(key.equals("KEYWORDS"))
			{
				keywords=new XVector<String>();
				for(final String k : CMParms.parseCommas(val.toUpperCase(),true))
					keywords.add(k);
			}
			else
			if(key.equals("MSG_OFFER"))
				msgOffer=val;
			else
			if(key.equals("MSG_HIRED"))
				msgHired=val;
			else
			if(key.equals("MSG_REFUSE"))
				msgRefuse=val;
			else
			if(key.equals("MSG_EXPIRE"))
				msgExpire=val;
			else
			if(key.equals("MSG_NOORDER"))
				msgNoOrder=val;
			else
			if(key.equals("CONTRACT"))
			{
				final String c=val.trim().toUpperCase();
				if(c.startsWith("QUEST:"))
				{
					contract="QUEST";
					contractQuest=val.trim().substring(6).trim();
				}
				else
				if(c.equals("DAYS")||c.equals("UNTILFIRED")||c.equals("MINUTES"))
					contract=c;
			}
			else
			if(key.equals("UPKEEP"))
				upkeep=CMath.s_double(val);
			else
			if(key.equals("FLEEHP"))
				fleeHp=CMath.s_int(val);
			else
			if(key.equals("LOYAL"))
				loyal=CMath.s_int(val);
			else
			if(key.equals("REFUSE"))
			{
				for(final String r : CMParms.parseCommas(val.toUpperCase(),true))
					refuseOrders.add(r);
			}
			m.appendReplacement(rest,java.util.regex.Matcher.quoteReplacement(m.group(1))); // the ; or space before it stays
		}
		m.appendTail(rest);
		return rest.toString().trim();
	}

	protected static long dayMillis()
	{
		return Math.max(1,CMProps.getMillisPerMudHour()*CMLib.time().globalClock().getHoursInDay());
	}

	/** N21: how long a contract runs, in game milliseconds */
	protected long contractMillis()
	{
		if(contract.equals("DAYS"))
			return minutes()*dayMillis();
		if(contract.equals("QUEST")||contract.equals("UNTILFIRED"))
			return Long.MAX_VALUE/4;
		return minutes()*TimeManager.MILI_MINUTE;
	}

	/** N21: the contract's length, in words */
	protected String termsText()
	{
		if(contract.equals("DAYS"))
			return L("for @x1 days",""+minutes());
		if(contract.equals("QUEST"))
			return L("until the @x1 is done",contractQuest);
		if(contract.equals("UNTILFIRED"))
			return L("until you let me go");
		return L("for @x1 \"hours\"",""+gamehours());
	}

	protected String offerLine(final MOB observer)
	{
		final String p=CMLib.beanCounter().nameCurrencyShort(observer,price());
		if(msgOffer!=null)
			return CMStrings.replaceAll(CMStrings.replaceAll(msgOffer,"@x1",p),"@x2",termsText());
		if(contract.equals("MINUTES"))
			return L("I'm for hire.  Just give me @x1 and I'll work for you for @x2 \"hours\".",p,""+gamehours());
		return L("I'm for hire.  Just give me @x1 and I'll work for you @x2.",p,termsText());
	}

	protected String hiredLine(final String skills)
	{
		if(msgHired!=null)
			return CMStrings.replaceAll(CMStrings.replaceAll(msgHired,"@x1",termsText()),"@x2",skills);
		if(contract.equals("MINUTES"))
			return L("Ok.  You've got me for at least @x1 \"hours\".  My skills include: @x2.  I'll follow you.  Just ORDER me to do what you want.",""+gamehours(),skills);
		return L("Ok.  You've got me @x1.  My skills include: @x2.  I'll follow you.  Just ORDER me to do what you want.",termsText(),skills);
	}

	protected boolean keywordHit(final String upperSrcMsg)
	{
		for(final String k : keywords)
		{
			if(CMStrings.containsWord(upperSrcMsg,k))
				return true;
		}
		return false;
	}

	/**
	 * N21: each tick on the job: wages (UPKEEP a game day), the end of a
	 * QUEST contract, and nerve (FLEEHP, unless its opinion of the employer
	 * reaches LOYAL).
	 * @param observer the hireling
	 * @return true if the contract ended
	 */
	protected boolean contractEnds(final MOB observer)
	{
		final Room R=observer.location();
		final MOB boss=(R==null)?null:R.fetchInhabitantExact(workingFor);
		if((upkeep>0)&&(lastUpkeep>0)&&((GameTime.now()-lastUpkeep)>=dayMillis()))
		{
			lastUpkeep+=dayMillis();
			final String currency=CMLib.beanCounter().getCurrency(observer);
			if((boss!=null)&&(CMLib.beanCounter().getTotalAbsoluteValue(boss,currency)>=upkeep))
			{
				CMLib.beanCounter().subtractMoney(boss,currency,upkeep);
				unpaidDays=0;
				if(boss!=null)
					boss.tell(L("You pay @x1 its wages of @x2.",observer.name(boss),CMLib.beanCounter().nameCurrencyShort(observer,upkeep)));
			}
			else
			{
				unpaidDays++;
				if(unpaidDays>=2)
				{
					CMLib.commands().postSay(observer,boss,L("No wages, no work.  Goodbye."),false,false);
					allDone(observer);
					return true;
				}
				CMLib.commands().postSay(observer,boss,L("I've not been paid my @x1.",CMLib.beanCounter().nameCurrencyShort(observer,upkeep)),false,false);
			}
		}
		if(contract.equals("QUEST")&&(contractQuest.length()>0))
		{
			final String st=QuestRecords.status(contractQuest,boss,workingFor);
			if(st.equals("WON")||st.equals("FAILED")||st.equals("ABANDONED")||st.equals("EXPIRED"))
			{
				CMLib.commands().postSay(observer,boss,(msgExpire!=null)?msgExpire:L("The job is done.  Goodbye!"),false,false);
				allDone(observer);
				return true;
			}
		}
		if((fleeHp>0)&&observer.isInCombat()
		&&(CMath.div(observer.curState().getHitPoints(),Math.max(1,observer.maxState().getHitPoints()))*100.0<fleeHp)
		&&((boss==null)||(NpcAffinity.get(observer,workingFor)<loyal)))
		{
			CMLib.commands().postSay(observer,null,L("This isn't worth it!"),false,false);
			observer.makePeace(true);
			allDone(observer);
			return true;
		}
		return false;
	}

	@Override
	public String getStat(final String code)
	{
		// N21: for HIRE's menu
		if(code.equalsIgnoreCase("PRICE"))
			return ""+price();
		if(code.equalsIgnoreCase("TERMS"))
			return termsText()+((upkeep>0)?L(", and @x1 a day",""+upkeep):"");
		if(code.equalsIgnoreCase("WORKING"))
			return (onTheJobUntil!=0)?workingFor:"";
		return super.getStat(code);
	}

	@Override
	public String accountForYourself()
	{
		return "availability for hiring";
	}

	public void setPrice(final String s)
	{
		price=100.0;
		if(CMath.isNumber(s))
		{
			if(CMath.isDouble(s))
				price=CMath.s_double(s);
			else
				price=CMath.s_long(s);
		}
	}

	public void setMinutes(final String s)
	{
		minutes=30;
		if(CMath.isNumber(s))
		{
			if(CMath.isDouble(s))
				minutes=(int)Math.round(CMath.s_double(s));
			else
				minutes=(int)CMath.s_long(s);
		}
	}

	@Override
	public void setParms(final String allParms)
	{
		super.setParms(allParms);
		final String newParms=readTerms(allParms); // N21: the terms out, the rest as before
		final int dex=newParms.indexOf(';');
		zapperMask=null;
		if(dex>=0)
		{
			final int dex2=newParms.indexOf(';',dex+1);
			if(dex2>dex)
			{
				setPrice(newParms.substring(0,dex));
				setMinutes(newParms.substring(dex+1,dex2));
				final String s=newParms.substring(dex2+1);
				if(s.trim().length()>0)
					zapperMask=CMLib.masking().maskCompile(s.trim());
			}
			else
			{
				setPrice(newParms.substring(0,dex));
				setMinutes(newParms.substring(dex+1));
			}
		}
		else
		{
			setPrice(newParms);
			setMinutes("30");
		}
	}

	protected double price()
	{
		return price;
	}

	protected int minutes()
	{
		return minutes;
	}

	protected MaskingLibrary.CompiledZMask zapper()
	{
		return zapperMask;
	}

	protected double gamehours()
	{
		final double d=CMath.div((minutes() * 60L * 1000L),CMProps.getMillisPerMudHour());
		final long d2=Math.round(d*10.0);
		return CMath.div(d2,10.0);
	}

	public void allDone(final MOB observer)
	{
		workingFor="";
		onTheJobUntil=0;
		CMLib.commands().postFollow(observer,null,false);
		observer.setFollowing(null);
		int direction=-1;
		for(final int dir : Directions.CODES())
		{
			if(observer.location().getExitInDir(dir)!=null)
			{
				if(observer.location().getExitInDir(dir).isOpen())
				{
					direction=dir;
					break;
				}
				direction=dir;
			}
		}
		if(direction>=0)
			CMLib.tracking().walk(observer,direction,false,false);
		if(observer.getStartRoom()!=null)
			observer.getStartRoom().bringMobHere(observer,false);
	}

	@Override
	public boolean tick(final Tickable ticking, final int tickID)
	{
		super.tick(ticking,tickID);
		if(tickID!=Tickable.TICKID_MOB)
			return true;
		if(onTheJobUntil==0)
			return true;
		final MOB observer=(MOB)ticking;
		if((workingFor.length()>0)&&contractEnds(observer)) // N21
			return true;
		if(GameTime.now()>onTheJobUntil)
		{
			final Double D=partials.get(workingFor);
			partials.remove(workingFor);
			CMLib.commands().postStand(observer,true, false);
			if(!canActAtAll(observer))
			{
				workingFor="";
				onTheJobUntil=0;
				observer.setFollowing(null);
				if(observer.getStartRoom()!=null)
					observer.getStartRoom().bringMobHere(observer,false);
				return true;
			}
			MOB talkTo=null;
			if((workingFor.length()>0)&&(observer.location()!=null))
			{
				talkTo=observer.location().fetchInhabitant(workingFor);
				if(talkTo!=null)
					observer.setFollowing(talkTo);
			}
			int additional=0;
			if(D!=null)
				additional+=(int)Math.round(CMath.mul(CMath.div(minutes(),price()),D.doubleValue()));
			if(additional<=0)
			{
				if(talkTo!=null)
					CMLib.commands().postSay(observer,talkTo,(msgExpire!=null)?msgExpire:L("Your time is up.  Goodbye!"),true,false);
				allDone(observer);
			}
			else
			{
				if(talkTo!=null)
					CMLib.commands().postSay(observer,talkTo,L("Your base time is up, but you've paid for @x1 more minutes, so I'll hang around.",
							""+additional),true,false);
				onTheJobUntil+=(additional*TimeManager.MILI_MINUTE);
			}
		}
		else
		if((workingFor.length()>0)
		   &&(observer.location()!=null)
		   &&(observer.amFollowing()==null)
		   &&(canFreelyBehaveNormal(observer)))
		{
			final MOB talkTo=observer.location().fetchInhabitant(workingFor);
			if(talkTo!=null)
			{
				CMLib.commands().postFollow(observer,talkTo,false);
				observer.setFollowing(talkTo);
			}
		}
		else
		if(GameTime.now() > nextWarn)
		{
			final long l = onTheJobUntil - GameTime.now();
			if((l/2) < 20000)
				nextWarn = Long.MAX_VALUE;
			else
				nextWarn = GameTime.now() + (l/2);
			if(l > 0)
				CMLib.commands().postSay(observer, null, L("I'm on the job for @x1 more.",CMLib.time().date2EllapsedTime(l,TimeUnit.MINUTES, false)));
		}
		return true;
	}

	@Override
	public boolean okMessage(final Environmental host, final CMMsg msg)
	{
		final MOB source=msg.source();
		if(host instanceof MOB)
		{
			final MOB observer=(MOB)host;

			// N21: REFUSE=: orders it will not take
			if((msg.targetMinor()==CMMsg.TYP_ORDER)
			&&(msg.amITarget(observer))
			&&(!refuseOrders.isEmpty())
			&&(msg.targetMessage()!=null))
			{
				final String t=msg.targetMessage();
				final int a=t.indexOf(" to '");
				final int b=(a<0)?-1:t.indexOf('\'',a+5);
				final String order=((a>=0)&&(b>a))?t.substring(a+5,b).trim().toUpperCase():"";
				final String verb=(order.indexOf(' ')>0)?order.substring(0,order.indexOf(' ')):order;
				for(final String r : refuseOrders)
				{
					if((verb.length()>0)&&(r.startsWith(verb)||verb.startsWith(r)))
					{
						CMLib.commands().postSay(observer,source,(msgNoOrder!=null)?msgNoOrder:L("I won't do that."),false,false);
						return false;
					}
				}
			}

			if(msg.amITarget(observer)
			&&(!msg.amISource(observer))
			&&(msg.targetMinor() == CMMsg.TYP_GIVE)
			&&(msg.tool() instanceof Coins))
			{
				if(!CMLib.masking().maskCheck(zapper(),source,false))
				{
					CMLib.commands().postSay(observer,null,(msgRefuse!=null)?msgRefuse:L("I wouldn't work for the likes of you."),false,false);
					return false;
				}
				if(!CMLib.beanCounter().isCurrencyMatch(((Coins)msg.tool()).getCurrency(),CMLib.beanCounter().getCurrency(observer)))
				{
					CMLib.commands().postSay(observer,null,L("I'm sorry, I only deal in @x1.",CMLib.beanCounter().getDenominationName(CMLib.beanCounter().getCurrency(observer))),false,false);
					return false;
				}
			}

			if((observer.soulMate()==null)
			&&(msg.amISource(observer))
			&&((msg.targetMinor()==CMMsg.TYP_GIVE)||(msg.targetMinor()==CMMsg.TYP_DROP))
			&&((msg.target() instanceof Coins)||(msg.tool() instanceof Coins)))
			{
				if((msg.target() instanceof MOB)
				&&(!CMSecurity.isAllowed(((MOB)msg.target()),source.location(),CMSecurity.SecFlag.CMDROOMS)))
					CMLib.commands().postSay(observer,null,L("I don't think so."),false,false);
				return false;
			}
		}
		return true;
	}

	@Override
	public void executeMsg(final Environmental affecting, final CMMsg msg)
	{
		super.executeMsg(affecting,msg);
		final MOB source=msg.source();
		if(!canActAtAll(affecting))
			return;
		if(!(affecting instanceof MOB))
			return;

		final MOB observer=(MOB)affecting;
		if((msg.sourceMinor()==CMMsg.TYP_QUIT)
		&&(msg.amISource(observer)||msg.amISource(observer.amFollowing())))
		   allDone(observer);
		else
		if((msg.sourceMinor()==CMMsg.TYP_SPEAK)
		&&(!msg.amISource(observer))
		&&(msg.sourceMessage()!=null)
		&&(!msg.source().isMonster()))
		{
			final String heard=Languages.speechText(observer,msg);
			final String upperSrcMsg=heard==null?null:heard.toUpperCase();
			if(upperSrcMsg == null)
			{
				// nothing done
			}
			else
			if(keywordHit(upperSrcMsg)) // N21: KEYWORDS=
			{
				if(onTheJobUntil==0)
					CMLib.commands().postSay(observer,null,offerLine(observer),false,false);
				else
				{
					final long l = onTheJobUntil - GameTime.now();
					if(l > 0)
						CMLib.commands().postSay(observer, null, L("I'm on the job for @x1 more.",CMLib.time().date2EllapsedTime(l,TimeUnit.MINUTES, false)));
					else
						CMLib.commands().postSay(observer, null, L("I'm on the job, but don't know why."));
				}
			}
			else
			if((CMStrings.containsWord(upperSrcMsg, "FIRED"))
			&&((workingFor!=null)&&(msg.source().Name().equals(workingFor)))
			&&(msg.amITarget(observer))
			&&(onTheJobUntil!=0))
			{
				CMLib.commands().postSay(observer,msg.source(),L("Suit yourself.  Goodbye."),false,false);
				allDone(observer);
			}
			else
			if(CMStrings.containsWord(upperSrcMsg, "SKILLS")||CMStrings.containsWord(upperSrcMsg, "SKILL"))
			{
				final StringBuffer skills = new StringBuffer("");
				for(final Enumeration<Ability> a=observer.allAbilities();a.hasMoreElements();)
				{
					final Ability A=a.nextElement();
					if(A!=null)
					{
						if(A.proficiency() == 0)
							A.setProficiency(50 + observer.phyStats().level() - CMLib.ableMapper().lowestQualifyingLevel(A.ID()));
						skills.append(", " + A.name());
					}
				}
				if(skills.length()>2)
					CMLib.commands().postSay(observer, source, L("My skills include: @x1.",skills.substring(2)),false,false);
			}
		}
		else
		if(msg.amITarget(observer)
		&&(!msg.amISource(observer))
		&&(msg.targetMinor()==CMMsg.TYP_GIVE)
		&&(msg.tool() instanceof Coins))
		{
			double given=((Coins)msg.tool()).getTotalValue();
			if(partials.get(msg.source().Name())!=null)
			{
				given+=partials.get(msg.source().Name()).doubleValue();
				partials.remove(msg.source().Name());
			}
			if(given<price())
			{
				if(onTheJobUntil!=0)
				{
					if(workingFor.equals(source.Name()))
						CMLib.commands().postSay(observer,source,L("I'm still working for you.  I'll put that towards an extension though."),true,false);
					else
						CMLib.commands().postSay(observer,source,L("Sorry, I'm on the job right now.  Give me @x1 more later on and I'll work for @x2 \"hours\".",CMLib.beanCounter().nameCurrencyShort(observer,(price()-given)),""+gamehours()),true,false);
				}
				else
					CMLib.commands().postSay(observer,source,L("My price is @x1.  Give me @x2 more and I'll work for you for @x3 \"hours\".",CMLib.beanCounter().nameCurrencyShort(observer,price()),CMLib.beanCounter().nameCurrencyShort(observer,(price()-given)),""+gamehours()),true,false);
				partials.put(msg.source().Name(),Double.valueOf(given));
			}
			else
			{
				if(onTheJobUntil!=0)
				{
					if(workingFor.equals(source.Name()))
						CMLib.commands().postSay(observer,source,L("I'm still working for you.  I'll put that towards an extension though."),true,false);
					else
						CMLib.commands().postSay(observer,source,L("Sorry, I'm on the job right now.  Give me 1 more coin later on and I'll work."),true,false);
					partials.put(msg.source().Name(),Double.valueOf(given));
				}
				else
				{
					if(given>price())
						partials.put(msg.source().Name(),Double.valueOf(given-price()));
					final StringBuffer skills=new StringBuffer("");
					for(final Enumeration<Ability> a=observer.allAbilities();a.hasMoreElements();)
					{
						final Ability A=a.nextElement();
						if(A!=null)
						{
							if(A.proficiency()==0)
								A.setProficiency(50+observer.phyStats().level()-CMLib.ableMapper().lowestQualifyingLevel(A.ID()));
							skills.append(", "+A.name());
						}
					}
					workingFor=source.Name();
					lastUpkeep=GameTime.now(); // N21
					unpaidDays=0;
					final long duration = contractMillis();
					onTheJobUntil=GameTime.now() + duration;
					nextWarn = GameTime.now() + (duration/2);
					CMLib.commands().postFollow(observer,source,false);
					observer.setFollowing(source);
					CMLib.commands().postSay(observer,source,hiredLine((skills.length()>2)?skills.substring(2):""),true,false);
				}
			}
		}
	}
}
