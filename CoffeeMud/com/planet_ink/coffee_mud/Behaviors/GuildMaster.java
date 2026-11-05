package com.planet_ink.coffee_mud.Behaviors;

import java.util.*;
import com.planet_ink.coffee_mud.core.*;
import com.planet_ink.coffee_mud.core.interfaces.*;
import com.planet_ink.coffee_mud.Common.interfaces.*;
import com.planet_ink.coffee_mud.Items.interfaces.*;
import com.planet_ink.coffee_mud.MOBS.interfaces.*;

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
/** Conversation front end for an NPC organisation, operating on the speaker. */
public class GuildMaster extends StdBehavior
{
    @Override public String ID() { return "GuildMaster"; }
    @Override public String accountForYourself() { return "guild membership and advancement"; }
    @Override protected int canImproveCode() { return CAN_MOBS; }
    private String param(final String key, final String fallback) { return CMParms.getParmStr(getParms(),key,fallback); }
    private void say(final MOB master, final MOB who, final String message) { CMLib.commands().postSay(master,who,message,false,false); }

    public String serve(final MOB master, final MOB who, final String request)
    {
        final Clan C=CMLib.clans().findClan(param("CLAN",""));
        if(C==null) return "This guild is not configured.";
        final String action=request.trim().toLowerCase();
        final boolean member=who.getClanRole(C.clanID())!=null&&(C.getGovernment().getAutoRole()==C.getGovernment().getAcceptPos()||who.getClanRole(C.clanID()).second.intValue()!=C.getGovernment().getAutoRole());
        if(action.equals("join"))
        {
            if(member) return "You already belong to "+C.name()+".";
            final String mask=Organizations.parameterMask(param("JOINMASK",C.getBasicRequirementMask()));
            final String quest=param("JOINQUEST","");
            final Quest Q=quest.length()==0?null:CMLib.quests().findQuest(quest);
            if(!CMLib.masking().maskCheck(C.getBasicRequirementMask(),who,true)||!CMLib.masking().maskCheck(mask,who,true)||(quest.length()>0&&(Q==null||!Q.wasWinner(who.Name())))) return param("REFUSEMSG","You have not met our requirements.");
            final double fee=Math.max(0,CMParms.getParmDouble(getParms(),"JOINFEE",0));
            final String currency=CMLib.beanCounter().getCurrency(who);
            if(CMLib.beanCounter().getTotalAbsoluteValue(who,currency)<fee) return "Joining costs "+CMLib.beanCounter().nameCurrencyShort(currency,fee)+".";
            if(who.getClanRole(C.clanID())==null&&CMLib.clans().getClansByCategory(who,C.getCategory()).size()>=CMProps.getMaxClansThisCategory(C.getCategory())) return "You already belong to the maximum number of organisations of this kind.";
            if(!C.canBeAssigned(who,C.getGovernment().getAcceptPos())) return param("REFUSEMSG","You cannot join this guild.");
            CMLib.beanCounter().subtractMoney(who,currency,fee);
            C.addMember(who,C.getGovernment().getAcceptPos());
            if(param("RANKS","MERIT").equalsIgnoreCase("MERIT")) Organizations.changed(who);
            kit(who,C,false);
            return "Welcome to "+C.name()+".";
        }
        if(!member) return "You do not belong to "+C.name()+". Say join to apply.";
        if(action.equals("leave")) { C.delMember(who); return param("LEAVEMSG","You have left "+C.name()+"."); }
        if(action.equals("dues")) return CMath.s_bool(param("DUES","TRUE"))?Organizations.payDues(who,C):"Ask the guild collector about dues.";
        if(action.equals("rank")||action.equals("advance"))
        {
            if(param("RANKS","MERIT").equalsIgnoreCase("MERIT")) Organizations.changed(who);
            return "You are "+Organizations.memberValue(who,C,"RANKNAME")+". "+Organizations.rankDetails(who,C);
        }
        if(action.equals("kit")||action.equals("kit replace")) return kit(who,C,action.endsWith("replace"));
        return "Say join, rank, advance, leave, dues, kit, or kit replace.";
    }

    private String kit(final MOB who, final Clan C, final boolean replace)
    {
        final String role=Organizations.memberValue(who,C,"POSITION");
        final String names=param("KIT."+role,"");
        if(names.length()==0) return "There is no kit for your rank.";
        final String tattoo="GUILDKIT_"+C.clanID().toUpperCase().replace(' ','_')+"_"+role;
        final boolean issued=who.findTattoo(tattoo)!=null;
        if(issued&&!replace) return "You have already received this rank's kit. Say kit replace for a replacement.";
        final List<Item> items=new ArrayList<Item>();
        for(final String name : CMParms.parseCommas(names,true))
        {
            final Item prototype=CMLib.catalog().getCatalogItem(name);
            if(prototype==null) { for(final Item I : items) I.destroy(); return "The guild's kit is unavailable."; }
            items.add((Item)prototype.copyOf());
        }
        final double fee=issued?Math.max(0,CMParms.getParmDouble(getParms(),"KITFEE",0)):0;
        final String currency=CMLib.beanCounter().getCurrency(who);
        if(CMLib.beanCounter().getTotalAbsoluteValue(who,currency)<fee) { for(final Item I : items) I.destroy(); return "You cannot afford the replacement kit."; }
        CMLib.beanCounter().subtractMoney(who,currency,fee);
        for(final Item I : items) who.addItem(I);
        who.addTattoo(tattoo);
        who.recoverPhyStats();
        return "You receive your "+role.toLowerCase()+" kit.";
    }

    @Override public void executeMsg(final Environmental host, final CMMsg msg)
    {
        super.executeMsg(host,msg);
        if(!(host instanceof MOB)||msg.source()==host||msg.sourceMinor()!=CMMsg.TYP_SPEAK||msg.sourceMessage()==null) return;
        final MOB master=(MOB)host;
        if(msg.source().location()!=master.location()||(msg.target()!=null&&msg.target()!=master)) return;
        final String words=Languages.speechText(master,msg);
        if(words==null) return;
        final String command=words.trim().toLowerCase();
        if(Arrays.asList("join","rank","advance","leave","dues","kit","kit replace").contains(command)) say(master,msg.source(),serve(master,msg.source(),command));
    }
}
