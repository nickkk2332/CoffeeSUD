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
   Copyright 2015-2025 Bo Zimmerman

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
public class Prop_ItemBinder extends Property implements TriggeredAffect
{
	@Override
	public String ID()
	{
		return "Prop_ItemBinder";
	}

	@Override
	public String name()
	{
		return "Allows an item to be bound to player, account, or clan.";
	}

	@Override
	protected int canAffectCode()
	{
		return Ability.CAN_ITEMS;
	}

	protected enum BoundTo
	{
		CHARACTER,
		ACCOUNT,
		GROUP,
		CLAN,
		PLAYER,
		NPC,
		NOONE
	}

	protected BoundTo to = BoundTo.CHARACTER;

	protected enum BoundOn
	{
		PICKUP,
		EQUIP
	}

	protected BoundOn on = BoundOn.PICKUP;

	protected String boundToName = "";

	protected boolean keepOverDeath = false;

	protected String msgStr=defaultMessage();

	/** I20: TRADE: whether a bound item may be given or sold: NONE, ALLOWED (only to whom it allows, the default) or ANY */
	protected enum Trade
	{
		NONE,
		ALLOWED,
		ANY
	}

	protected Trade		trade		= Trade.ALLOWED;
	/** I20: who it is allotted to (ALLOT), and for how long (ALLOTFOR, empty for as long as it lies unclaimed) */
	protected String	allot		= "";
	protected long		allotFor	= 0;
	protected long		allotAt		= -1;
	/** I20: where it came from (SOURCE), for inspection */
	protected String	source		= "";
	/** I20: how long a binding lasts (EXPIRES, empty for ever), when it was made, and whether it has ended */
	protected long		expires		= 0;
	protected long		boundAt		= -1;
	protected boolean	freed		= false;

	/** I20: a duration in words, as "2 mudhours", in game milliseconds; 0 for none */
	protected static long duration(final String words)
	{
		if((words==null)||(words.trim().length()==0))
			return 0;
		try
		{
			final int ticks=CMLib.time().parseTickExpression(CMLib.time().globalClock(),words.trim());
			return (ticks>0)?(long)ticks*CMProps.getTickMillis():0;
		}
		catch(final com.planet_ink.coffee_mud.core.exceptions.CMException e)
		{
			return 0;
		}
	}

	/** I20: the text with KEY="value" set (or taken away, for null) */
	protected static String withParm(final String text,final String key,final String value)
	{
		String t=text.replaceAll("(?i)(^|\\s)"+key+"=(\"[^\"]*\"|\\S*)","").trim();
		if(value!=null)
			t=(t+" "+key+"=\""+CMStrings.escape(value)+"\"").trim();
		return t;
	}

	protected void store(final String key,final String value)
	{
		super.miscText=withParm(text(),key,value);
	}

	@Override
	public void setMiscText(final String text)
	{
		final String boundTo=CMParms.getParmStr(text, "TO", "CHARACTER");
		to = (BoundTo)CMath.s_valueOf(BoundTo.class,boundTo.toUpperCase().trim());
		for(final BoundTo b : BoundTo.values())
		{
			if(b.name().startsWith(boundTo.toUpperCase().trim()))
				to=b;
		}
		if(to == null)
			to = BoundTo.CHARACTER;

		final String boundOn=CMParms.getParmStr(text, "ON", "PICKUP");
		on = (BoundOn)CMath.s_valueOf(BoundOn.class,boundOn.toUpperCase().trim());
		for(final BoundOn b : BoundOn.values())
		{
			if(b.name().startsWith(boundOn.toUpperCase().trim()))
				on=b;
		}
		if(on == null)
			on = BoundOn.PICKUP;

		boundToName = CMStrings.deEscape(CMParms.getParmStr(text, "BOUND", ""));
		keepOverDeath = CMParms.getParmBool(text, "DEATHKEEP", false);
		msgStr=CMParms.getParmStr(text,"MESSAGE",defaultMessage());
		// I20: the fields beside the binding: trade, allotment, source, expiry, and when each began
		final Trade t=(Trade)CMath.s_valueOf(Trade.class,CMParms.getParmStr(text,"TRADE","ALLOWED").toUpperCase().trim());
		trade=(t==null)?Trade.ALLOWED:t;
		allot=CMStrings.deEscape(CMParms.getParmStr(text,"ALLOT",""));
		allotFor=duration(CMStrings.deEscape(CMParms.getParmStr(text,"ALLOTFOR","")));
		allotAt=CMath.s_long(CMParms.getParmStr(text,"ALLOTAT","-1"));
		source=CMStrings.deEscape(CMParms.getParmStr(text,"SOURCE",""));
		expires=duration(CMStrings.deEscape(CMParms.getParmStr(text,"EXPIRES","")));
		boundAt=CMath.s_long(CMParms.getParmStr(text,"BOUNDAT","-1"));
		freed=CMParms.getParmBool(text,"FREED",false);
		super.setMiscText(text);
		if((allot.length()>0)&&(allotAt<0)&&(allotFor>0))
		{
			allotAt=GameTime.now();
			store("ALLOTAT",Long.toString(allotAt));
		}
	}

	/** I20: who it is allotted to now, or "" once claimed or the allotment has run out */
	protected String allottedTo()
	{
		if(allot.length()==0)
			return "";
		if((allotFor>0)&&(allotAt>=0)&&((GameTime.now()-allotAt)>=allotFor))
			return "";
		return allot;
	}

	/** I20: ends a binding whose time is up: the item is free, and binds no more */
	protected void expire()
	{
		if((boundToName.length()>0)&&(expires>0)&&(boundAt>=0)&&((GameTime.now()-boundAt)>=expires))
		{
			boundToName="";
			freed=true;
			String t=withParm(text(),"BOUND",null);
			t=withParm(t,"BOUNDAT",null);
			super.miscText=withParm(t,"FREED","true");
		}
	}

	protected void bound(final String name)
	{
		boundToName=name;
		boundAt=GameTime.now();
		String t=withParm(text(),"BOUND",boundToName);
		if(expires>0)
			t=withParm(t,"BOUNDAT",Long.toString(boundAt));
		super.miscText=t;
	}

	protected static String mudHours(final long ms)
	{
		final long hour=Math.max(1,CMProps.getMillisPerMudHour());
		return Math.max(1,(ms+hour-1)/hour)+" mud hour(s)";
	}

	protected String defaultMessage()
	{
		return "<O-NAME> flashes and flies out of <S-HIS-HER> hands!";
	}

	@Override
	public long flags()
	{
		return Ability.FLAG_ZAPPER|Ability.FLAG_NODISENCHANT;
	}

	@Override
	public int triggerMask()
	{
		return TriggeredAffect.TRIGGER_GET;
	}

	@Override
	public String accountForYourself()
	{
		expire();
		// I20: each field on its own: allocation, use, trade, source, expiry
		final List<String> out=new ArrayList<String>();
		final String allotted=allottedTo();
		if(allotted.length()>0)
			out.add("Allotted to: "+allotted+((allotFor>0)?" for "+mudHours(allotFor-(GameTime.now()-allotAt))+" more":""));
		if(boundToName.length()>0)
			out.add("Bound to: "+boundToName+((expires>0)&&(boundAt>=0)?" for "+mudHours(expires-(GameTime.now()-boundAt))+" more":""));
		else
		if(freed)
			out.add("Its binding has ended; anyone may use it");
		else
		if(to!=BoundTo.NOONE)
			out.add("Binds to "+this.to.toString().toLowerCase()+" on "+this.on.toString().toLowerCase()+((expires>0)?", for "+mudHours(expires):""));
		out.add("Trade: "+((trade==Trade.NONE)?"cannot be given or sold once bound":(trade==Trade.ANY)?"may be given or sold, bound or not":"once bound, given only to whom it is bound to, and not sold"));
		if(source.length()>0)
			out.add("From: "+source);
		return String.join("; ",out);
	}

	@Override
	public boolean okMessage(final Environmental myHost, final CMMsg msg)
	{
		if(affected==null)
			return true;

		final MOB mob=msg.source();
		if(mob.location()==null)
			return true;

		expire();
		// I20: an allotted item is taken first by the one it is allotted to, until the allotment runs out
		final String allotted=allottedTo();
		if(msg.amITarget(affected)&&(msg.targetMinor()==CMMsg.TYP_GET)&&(allotted.length()>0)
		&&(!(affected instanceof Item)||!(((Item)affected).owner() instanceof MOB))&&!allotted.equals(mob.Name()))
		{
			mob.tell(L("@x1 is allotted to @x2.",affected.name(),allotted));
			return false;
		}
		if(msg.amITarget(affected) && (to != BoundTo.NOONE))
		{
			switch(msg.targetMinor())
			{
			case CMMsg.TYP_HOLD:
			case CMMsg.TYP_WEAR:
			case CMMsg.TYP_WIELD:
			case CMMsg.TYP_GET:
			case CMMsg.TYP_EAT:
			case CMMsg.TYP_DRINK:
				// once bound, only those it is bound to may take, use or put it on
				if((this.boundToName.length()>0) && (!allows(msg.source())))
				{
					mob.location().show(mob,null,affected,CMMsg.MSG_OK_ACTION,msgStr);
					return false;
				}
				break;
			default:
				break;
			}
		}
		else
		if((this.boundToName.length()>0)
		&&(trade!=Trade.ANY)
		&&(msg.tool()==affected)
		&&(msg.sourceMinor()==CMMsg.TYP_SELL))
		{
			msg.source().tell(L("@x1 is bound to @x2, and can not be sold.",affected.name(),this.boundToName));
			return false;
		}
		else
		if((this.boundToName.length()>0)
		&&(to != BoundTo.NOONE)
		&&(trade!=Trade.ANY)
		&&(msg.tool()==affected)
		&&(msg.targetMinor()==CMMsg.TYP_GIVE)
		&&(msg.target() instanceof MOB)
		&&((trade==Trade.NONE)||(!allows((MOB)msg.target()))))
		{
			msg.source().tell(L("@x1 is bound to @x2, and can not be given away.",affected.name(),this.boundToName));
			return false;
		}
		return true;
	}

	/**
	 * Whether someone may have this bound item: the one it is bound to, or one
	 * of the group, profile or organization it is bound to.
	 * @param who the one taking, using, or being given it
	 * @return true if they may
	 */
	protected boolean allows(final MOB who)
	{
		if((who == null) || (this.boundToName.length()==0))
			return true;
		switch(to)
		{
		case NOONE:
			return true;
		case CHARACTER:
			return this.boundToName.equals(who.Name());
		case PLAYER:
			return this.boundToName.equals(who.Name()) || (!who.isPlayer());
		case NPC:
			return this.boundToName.equals(who.Name()) || who.isPlayer();
		case GROUP:
			return CMParms.parse(this.boundToName).contains(who.Name());
		case ACCOUNT:
			return (who.playerStats()!=null)
				&&(who.playerStats().getAccount()!=null)
				&&(this.boundToName.equals(who.playerStats().getAccount().getAccountName()));
		case CLAN:
			return who.getClanRole(this.boundToName)!=null;
		default:
			return true;
		}
	}

	final String getBindyName(final MOB mob)
	{
		switch(to)
		{
		case NOONE:
			return "";
		case CHARACTER:
		case PLAYER:
		case NPC:
			return mob.Name();
		case GROUP:
		{
			final Set<MOB> grp = mob.getGroupMembers(new HashSet<MOB>());
			final List<String> grpMemberNames = new ArrayList<String>();
			for(final MOB M : grp)
				grpMemberNames.add(M.Name());
			return CMParms.combineQuoted(grpMemberNames, 0);
		}
		case ACCOUNT:
			if((mob.playerStats()!=null)
			&&(mob.playerStats().getAccount()!=null))
				return mob.playerStats().getAccount().getAccountName();
			break;
		case CLAN:
		{
			final Clan C=CMLib.clans().findRivalrousClan(mob);
			if(C!=null)
				return C.getName();
			break;
		}
		}
		return "";
	}

	@Override
	public void affectPhyStats(final Physical affected, final PhyStats affectableStats)
	{
		if ((keepOverDeath)
		&&(affected instanceof Item)
		&&(((Item)affected).owner() instanceof MOB))
		{
			if(on == BoundOn.PICKUP)
				affectableStats.setSensesMask(affectableStats.sensesMask()|PhyStats.SENSE_ITEMDEATHKEEPER);
			else
			if((on == BoundOn.EQUIP) && (((Item)affected).amBeingWornProperly()))
				affectableStats.setSensesMask(affectableStats.sensesMask() | PhyStats.SENSE_ITEMDEATHKEEPER);
		}
	}

	@Override
	public void executeMsg(final Environmental myHost, final CMMsg msg)
	{
		if(msg.amITarget(affected) && (to != BoundTo.NOONE))
		{
			switch(msg.targetMinor())
			{
			case CMMsg.TYP_HOLD:
			case CMMsg.TYP_WEAR:
			case CMMsg.TYP_WIELD:
			case CMMsg.TYP_EAT:
			case CMMsg.TYP_DRINK:
				if((this.boundToName.length()==0)
				&&(!freed)
				&&(on == BoundOn.EQUIP))
				{
					final String name = getBindyName(msg.source());
					if(name.length()>0)
						bound(name);
				}
				break;
			case CMMsg.TYP_GET:
				if((allot.length()>0)&&(allottedTo().equals(msg.source().Name())||(allottedTo().length()==0)))
				{
					// I20: the allotment ends once it is claimed
					allot="";
					super.miscText=withParm(withParm(withParm(text(),"ALLOT",null),"ALLOTFOR",null),"ALLOTAT",null);
				}
				if((this.boundToName.length()==0)
				&&(!freed)
				&&(on == BoundOn.PICKUP)
				&&((!(msg.source() instanceof ShopKeeper)))
				&&((!msg.source().isMonster())||(msg.source().amFollowing()!=null)))
				{
					final String name = getBindyName(msg.source());
					if(name.length()>0)
						bound(name);
				}
				break;
			default:
				break;
			}
		}
	}
}
