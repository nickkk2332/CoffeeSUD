package com.planet_ink.coffee_mud.core;

import java.util.*;
import com.planet_ink.coffee_mud.core.interfaces.*;
import com.planet_ink.coffee_mud.Common.interfaces.*;
import com.planet_ink.coffee_mud.Abilities.interfaces.*;
import com.planet_ink.coffee_mud.Behaviors.interfaces.*;
import com.planet_ink.coffee_mud.Items.interfaces.*;
import com.planet_ink.coffee_mud.Locales.interfaces.*;
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
/** Builder scaffolding and party founding, with validation before world changes. */
public final class Guilds
{
    private Guilds() {}
    public static ClanGovernment government(final String name)
    {
        for(final ClanGovernment G : CMLib.clans().getStockGovernments()) if(G.getName().equalsIgnoreCase(name)) return G;
        return null;
    }

    public static String create(final String name, final String template, final String hallID)
    {
        if(name.trim().length()==0||name.indexOf('"')>=0||name.indexOf('<')>=0) return "Give the guild a valid name.";
        if(CMLib.clans().findClan(name)!=null) return "That clan already exists.";
        final ClanGovernment G=government(template);
        if(G==null) return "Unknown government template: "+template;
        final Room hall=CMLib.map().getRoom(hallID);
        if(hall==null) return "Unknown hall room: "+hallID;
        int direction=-1;
        for(int d=0;d<Directions.NUM_DIRECTIONS();d++) if(hall.getRoomInDir(d)==null) { direction=d; break; }
        if(direction<0) return "The hall needs a free exit for its members' room.";
        final String innerID=hall.getArea().getNewRoomID(hall,direction);
        if(innerID.length()==0) return "The hall cannot have a room in that direction.";
        final Clan C=(Clan)CMClass.getCommon("DefaultClan");
        C.setName(name.trim()); C.setGovernmentID(G.getID()); C.setMinClanMembers(0); C.setFlag(Clan.ClanFlag.NPCRUN,true);
        C.setStatus(Clan.CLANSTATUS_ACTIVE); C.setRecall(hallID); C.create();
        final Faction F=Organizations.clanFaction(C,true);
        if(F==null) { C.destroyClan(); return "The clan reaction template could not be loaded."; }
        C.setStat("STANDINGFACTION",F.factionID()); C.update();
        final MOB master=(MOB)CMClass.getMOB("GenMob");
        master.setName("the "+name+" guildmaster"); master.setDisplayText("The guildmaster is here, ready to discuss membership.");
        final List<Integer> top=C.getTopRankedRoles(null);
        master.setClan(C.clanID(),top.isEmpty()?G.getAcceptPos():top.get(0).intValue());
        final Behavior service=CMClass.getBehavior("GuildMaster"); service.setParms("CLAN=\""+name+"\" RANKS=MERIT DUES=TRUE"); master.addBehavior(service);
        master.setStartRoom(hall); master.bringToLife(hall,true);
        final MOB trainer=(MOB)CMClass.getMOB("GenMob"); trainer.setName("a "+name+" trainer"); trainer.setDisplayText("A guild trainer waits for students.");
        trainer.setClan(C.clanID(),G.getAcceptPos()); trainer.addBehavior(CMClass.getBehavior("MOBTeacher")); trainer.setStartRoom(hall); trainer.bringToLife(hall,true);
        final MOB shop=(MOB)CMClass.getMOB("GenShopkeeper"); shop.setName("the "+name+" quartermaster"); shop.setDisplayText("The guild quartermaster tends an empty counter.");
        shop.setClan(C.clanID(),G.getAcceptPos()); shop.setStartRoom(hall); shop.bringToLife(hall,true);
        ((ShopKeeper)shop).setPrejudiceFactors("BUY=0.8 CLAN:"+name.replace(' ','_'));
        final Room inner=CMClass.getLocale("StoneRoom"); inner.setRoomID(innerID); inner.setArea(hall.getArea());
        inner.setDisplayText(name.replace('\'','`')+" members room"); inner.setDescription("A private room reserved for members of "+name+".");
        final Ability gate=CMClass.getAbility("Prop_ReqEntry"); gate.setMiscText("-CLANRANK \"+"+name+">="+G.getPosition(""+G.getAcceptPos()).getID()+"\""); inner.addNonUninvokableEffect(gate);
        hall.setRawDoor(direction,inner); hall.setRawExit(direction,CMClass.getExit("Open"));
        final int back=Directions.getOpDirectionCode(direction); inner.setRawDoor(back,hall); inner.setRawExit(back,CMClass.getExit("Open"));
        CMLib.database().DBCreateRoom(inner); CMLib.database().DBUpdateExits(hall); CMLib.database().DBUpdateExits(inner);
        CMLib.database().DBUpdateMOBs(hall);
        return "Created guild '"+name+"', standing, guildmaster, trainer, quartermaster and members' room "+inner.roomID()+". Save the world to keep the scaffold.";
    }

    public static String foundParty(final MOB founder, final String name, final String template)
    {
        if(!CMProps.isSinglePlayer()) return "Party founding is available in single-player games.";
        if(name.trim().length()==0||CMLib.clans().findClan(name)!=null) return "Give a new clan name.";
        final ClanGovernment G=government(template);
        if(G==null||!CMProps.isPublicClanGvtCategory(G.getCategory())) return "That government is not available for founding.";
        final List<MOB> party=Party.inOrder(Party.leaderOf(founder));
        final Clan C=(Clan)CMClass.getCommon("DefaultClan"); C.setName(name.trim()); C.setGovernmentID(G.getID());
        for(final MOB M : party)
        {
            if(Party.profileOf(M)!=Party.profileOf(founder)) return "Every founding member must belong to your profile.";
            if(CMLib.clans().getClansByCategory(M,G.getCategory()).size()>=CMProps.getMaxClansThisCategory(G.getCategory())||!CMLib.masking().maskCheck(C.getBasicRequirementMask(),M,true)||!C.canBeAssigned(M,G.getAcceptPos()))
                return M.Name()+" is not eligible to found this clan.";
        }
        final int cost=CMProps.getIntVar(CMProps.Int.CLANCOST);
        if(CMLib.beanCounter().getTotalAbsoluteNativeValue(founder)<cost) return "You cannot afford the clan founding cost.";
        C.setRecall(CMLib.map().getExtendedRoomID(founder.location())); C.setStatus(Clan.CLANSTATUS_ACTIVE); C.create();
        CMLib.beanCounter().subtractMoney(founder,Math.max(0,cost));
        final List<Integer> top=C.getTopRankedRoles(null);
        C.addMember(founder,top.isEmpty()?G.getAcceptPos():top.get(0).intValue());
        for(final MOB M : party) if(M!=founder) C.addMember(M,G.getAcceptPos());
        return "Your party founded "+C.name()+". The founding cost was paid once.";
    }
}
