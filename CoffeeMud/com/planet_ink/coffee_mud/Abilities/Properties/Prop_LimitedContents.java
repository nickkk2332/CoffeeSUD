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
import com.planet_ink.coffee_mud.Libraries.interfaces.MaskingLibrary.CompiledZMask;
import com.planet_ink.coffee_mud.Locales.interfaces.*;
import com.planet_ink.coffee_mud.MOBS.interfaces.*;
import com.planet_ink.coffee_mud.Races.interfaces.*;

import java.lang.ref.WeakReference;
import java.util.*;

/*
   Copyright 2019-2025 Bo Zimmerman

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
public class Prop_LimitedContents extends Property
{
	@Override
	public String ID()
	{
		return "Prop_LimitedContents";
	}

	@Override
	public String name()
	{
		return "Limited Content Types";
	}

	@Override
	protected int canAffectCode()
	{
		return Ability.CAN_ITEMS|Ability.CAN_AREAS|Ability.CAN_ROOMS;
	}

	protected final PairVector<Integer, CompiledZMask> limits = new PairVector<Integer, CompiledZMask>();
	protected String msgStr = "";
	protected boolean actual=true;
	// I15: a receptacle
	protected boolean lockIn=false;
	protected boolean consume=false;
	protected String onPut="";
	protected String onFull="";
	protected String onEmpty="";
	protected String lockMsg="";
	protected int[] tally=new int[0];
	protected boolean wasFull=false;
	protected String baseText="";

	@Override
	public String accountForYourself()
	{
		if(CMath.s_int(text())<=0)
			return "Only 1 may be equipped";
		final int x=text().indexOf(';');
		final String numStr;
		if(x<0)
			numStr = text();
		else
			numStr=text().substring(0,x);
		return L("Only @x1 may be equipped.",numStr);
	}

	/**
	 * I15: the text's parts: a semicolon between, but not inside quotes.
	 * @param text the text
	 * @return the parts
	 */
	public static List<String> parts(final String text)
	{
		final List<String> list=new ArrayList<String>();
		final StringBuilder b=new StringBuilder();
		boolean q=false;
		for(final char c : text.toCharArray())
		{
			if(c=='"')
				q=!q;
			if((c==';')&&(!q))
			{
				list.add(b.toString());
				b.setLength(0);
			}
			else
				b.append(c);
		}
		list.add(b.toString());
		return list;
	}

	@Override
	public String text()
	{
		// I15: what it has consumed goes with it into saves
		boolean any=false;
		for(final int t : tally)
			any=any||(t>0);
		if(!any)
			return baseText;
		final StringBuilder b=new StringBuilder(baseText).append(";TALLY=");
		for(int i=0;i<tally.length;i++)
			b.append((i>0)?",":"").append(tally[i]);
		return b.toString();
	}

	@Override
	public void setMiscText(final String newMiscText)
	{
		super.setMiscText(newMiscText);
		limits.clear();
		this.msgStr="";
		this.actual=true;
		lockIn=false;
		consume=false;
		onPut="";
		onFull="";
		onEmpty="";
		lockMsg="";
		String tallyStr="";
		final StringBuilder kept=new StringBuilder();
		if(newMiscText.length()>0)
		{
			for(String part : parts(newMiscText))
			{
				if(part.trim().toUpperCase().startsWith("TALLY="))
				{
					tallyStr=part.trim().substring(6);
					continue;
				}
				if(kept.length()>0)
					kept.append(";");
				kept.append(part);
				part=part.trim();
				final int x=part.indexOf(' ');
				final String amtStr;
				String mask="";
				if(x>0)
				{
					amtStr=part.substring(0,x);
					mask=part.substring(x+1).trim();
				}
				else
					amtStr=part;
				if(CMath.isInteger(amtStr))
					limits.add(Integer.valueOf(CMath.s_int(amtStr)), CMLib.masking().getPreCompiledMask(mask));
				else
				{
					final Map<String,String> q=CMParms.parseExactParms(part);
					this.msgStr=CMParms.getParmStr(q,"MSG",this.msgStr);
					this.actual=CMParms.getParmBool(q,"ACTUAL",this.actual);
					lockIn=CMParms.getParmBool(q,"LOCKIN",lockIn);
					consume=CMParms.getParmBool(q,"CONSUME",consume);
					onPut=CMParms.getParmStr(q,"ONPUT",onPut);
					onFull=CMParms.getParmStr(q,"ONFULL",onFull);
					onEmpty=CMParms.getParmStr(q,"ONEMPTY",onEmpty);
					lockMsg=CMParms.getParmStr(q,"LOCKMSG",lockMsg);
				}
			}
		}
		baseText=kept.toString();
		tally=new int[limits.size()];
		final String[] ts=tallyStr.split(",");
		for(int i=0;(i<ts.length)&&(i<tally.length);i++)
			tally[i]=CMath.s_int(ts[i].trim());
		wasFull=(affected instanceof Container)&&full(null);
	}

	/**
	 * I15: how many of a limit's kind it holds, and has consumed.
	 * @param i the limit
	 * @param plus an item going in, not yet counted, or null
	 * @param minus an item going out, or null
	 * @return the count
	 */
	protected int held(final int i, final Item plus, final Item minus)
	{
		final CompiledZMask mask=limits.get(i).second;
		int n=(i<tally.length)?tally[i]:0;
		if(affected instanceof Container)
		{
			for(final Item I : ((Container)affected).getContents())
			{
				if((I!=minus)&&(I!=plus)&&(firstLimit(I)==i))
					n+=itemCount(I);
			}
		}
		if((plus!=null)&&(firstLimit(plus)==i))
			n+=itemCount(plus);
		return n;
	}

	/** I15: the first limit an item fits, or -1 */
	protected int firstLimit(final Item I)
	{
		for(int i=0;i<limits.size();i++)
		{
			if(CMLib.masking().maskCheck(limits.get(i).second, I, this.actual))
				return i;
		}
		return -1;
	}

	/**
	 * I15: whether each limit over 0 is met.
	 * @param plus an item going in, or null
	 * @return true if it is full
	 */
	protected boolean full(final Item plus)
	{
		boolean any=false;
		for(int i=0;i<limits.size();i++)
		{
			final int max=limits.get(i).first.intValue();
			if(max<=0)
				continue;
			any=true;
			if(held(i,plus,null)<max)
				return false;
		}
		return any;
	}

	/** I15: whether it holds nothing */
	protected boolean empty(final Item minus)
	{
		if(!(affected instanceof Container))
			return false;
		for(final Item I : ((Container)affected).getContents())
		{
			if(I!=minus)
				return false;
		}
		return true;
	}

	@Override
	public void executeMsg(final Environmental myHost, final CMMsg msg)
	{
		super.executeMsg(myHost, msg);
		if(!(affected instanceof Container))
			return;
		final Container C=(Container)affected;
		if((msg.targetMinor()==CMMsg.TYP_PUT)&&(msg.target()==C)&&(msg.tool() instanceof Item))
		{
			final Item I=(Item)msg.tool();
			final MOB mob=msg.source();
			Actions.run(C,mob,onPut);
			final boolean nowFull=full(I);
			if(consume)
			{
				final int i=firstLimit(I);
				if((i>=0)&&(i<tally.length))
					tally[i]+=itemCount(I);
				I.destroy();
			}
			if(nowFull&&(!wasFull))
				Actions.run(C,mob,onFull);
			wasFull=nowFull;
		}
		else
		if((msg.targetMinor()==CMMsg.TYP_GET)&&(msg.target()==C)&&(msg.tool() instanceof Item)&&(((Item)msg.tool()).container()==C))
		{
			final Item I=(Item)msg.tool();
			wasFull=full(null)&&(firstLimit(I)<0);
			if(empty(I))
				Actions.run(C,msg.source(),onEmpty);
		}
	}

	protected int itemCount(final Item item)
	{
		if(item instanceof RawMaterial)
			return ((RawMaterial)item).phyStats().weight();
		else
		if(item instanceof PackagedItems)
			return ((PackagedItems)item).numberOfItemsInPackage();
		return 1;
	}

	protected Enumeration<Item> getExistingItems(final Physical locatableP)
	{
		if(affected instanceof Container)
			return new IteratorEnumeration<Item>(((Container)affected).getContents().iterator());
		if(affected instanceof Room)
			return ((Room)affected).items();
		final Room R=CMLib.map().roomLocation(locatableP);
		if(R!=null)
			return R.items();
		return new Vector<Item>().elements();
	}

	@Override
	public boolean okMessage(final Environmental myHost, final CMMsg msg)
	{
		if(!super.okMessage(myHost, msg))
			return false;

		// I15: LOCKIN: what is in it stays in it
		if(lockIn
		&&(affected instanceof Container)
		&&(msg.targetMinor()==CMMsg.TYP_GET)
		&&(((msg.target()==affected)&&(msg.tool() instanceof Item)&&(((Item)msg.tool()).container()==affected))
			||((msg.target() instanceof Item)&&(((Item)msg.target()).container()==affected))))
		{
			msg.source().tell(msg.source(),affected,null,(lockMsg.length()>0)?lockMsg:L("It is held fast in <T-NAME>."));
			return false;
		}
		final Item item;
		if(msg.targetMinor()==CMMsg.TYP_DROP)
		{
			// a drop names the item as its target, and lands in the dropper's room
			final Room dropR=msg.source().location();
			if(affected instanceof Room)
			{
				if(dropR!=affected)
					return true;
			}
			else
			if(affected instanceof Area)
			{
				if((dropR==null)||(dropR.getArea()!=affected))
					return true;
			}
			else
				return true;
			if(!(msg.target() instanceof Item))
				return true;
			item=(Item)msg.target();
		}
		else
		if(msg.targetMinor()==CMMsg.TYP_PUT)
		{
			if((!(affected instanceof Item))
			||(msg.target() != affected))
				return true;
			item=(Item)msg.tool();
		}
		else
			return true;

		final MaskingLibrary maskLib=CMLib.masking();
		for(final Pair<Integer,CompiledZMask> p : this.limits)
		{
			if(maskLib.maskCheck(p.second, item, this.actual))
			{
				final int maxLimit = p.first.intValue();
				final int numAdded=itemCount(item);
				if(numAdded > maxLimit)
				{
					if(msgStr.length()==0)
						msg.source().tell(L("You can only put @x1 of those in there.",""+maxLimit));
					else
						msg.source().tell(L(msgStr,""+maxLimit));
					return false;
				}
				int existingCount=0;
				for(final Enumeration<Item> i=this.getExistingItems(msg.source());i.hasMoreElements();)
				{
					final Item I=i.nextElement();
					if(maskLib.maskCheck(p.second, I, this.actual))
						existingCount += itemCount(I);
				}
				if(existingCount + numAdded > maxLimit)
				{
					if(msgStr.length()==0)
						msg.source().tell(L("You can only put @x1 of those in there.",""+maxLimit));
					else
						msg.source().tell(L(msgStr,""+maxLimit));
					return false;
				}
				break;
			}
		}
		return true;
	}
}
