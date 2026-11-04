package com.planet_ink.coffee_mud.core;

import java.util.*;
import com.planet_ink.coffee_mud.core.interfaces.*;
import com.planet_ink.coffee_mud.Abilities.interfaces.*;
import com.planet_ink.coffee_mud.Common.interfaces.*;
import com.planet_ink.coffee_mud.Items.interfaces.*;
import com.planet_ink.coffee_mud.Locales.interfaces.*;
import com.planet_ink.coffee_mud.MOBS.interfaces.*;

/* Copyright 2026 CoffeeSUD contributors. Licensed under the Apache License, Version 2.0. */
/** Discoverable networks, stored on the character or their existing profile. */
public final class Waypoints
{
    private Waypoints() {}
    public static String network(final Ability A) { return Travel.option(A.text(),"NETWORK","DEFAULT"); }
    public static String name(final Ability A) { return Travel.option(A.text(),"NAME",A.affecting().Name()); }
    private static Tattooable record(final MOB M, final Ability A)
    {
        final PlayerAccount profile=Party.profileOf(M);
        return Travel.option(A.text(),"SCOPE","CHARACTER").equalsIgnoreCase("PROFILE")&&profile!=null?profile:M;
    }
    public static String key(final Ability A)
    {
        final Room R=CMLib.map().roomLocation(A.affecting());
        final String text=network(A).toUpperCase()+"\n"+Travel.option(A.text(),"ID",CMLib.map().getExtendedRoomID(R)+"/"+name(A)).toUpperCase();
        try
        {
            final byte[] digest=java.security.MessageDigest.getInstance("SHA-256").digest(text.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            final StringBuilder key=new StringBuilder("WAYPOINT_");
            for(final byte b : digest) key.append(String.format("%02X",Integer.valueOf(b&255)));
            return key.toString();
        }
        catch(final java.security.NoSuchAlgorithmException e) { throw new IllegalStateException(e); }
    }
    public static boolean known(final MOB M, final Ability A) { return record(M,A).findTattoo(key(A))!=null; }
    private static boolean fits(final MOB M, final Ability A)
    {
        return CMLib.masking().maskCheck(Organizations.parameterMask(Travel.option(A.text(),"MASK","")),M,true);
    }
    public static boolean discover(final MOB M, final Ability A, final boolean forced)
    {
        if(M==null||!M.isPlayer()||!fits(M,A)) return false;
        final String mode=Travel.option(A.text(),"DISCOVER","ENTER");
        if(!forced&&mode.equalsIgnoreCase("QUEST"))
        {
            final Quest Q=CMLib.quests().findQuest(Travel.option(A.text(),"QUEST",""));
            if(Q==null||!Q.wasWinner(M.Name())) return false;
        }
        if(!known(M,A))
        {
            record(M,A).addTattoo(key(A));
            M.tell("You attune to "+name(A)+" ("+network(A)+").");
        }
        return true;
    }
    public static List<Ability> all()
    {
        final List<Ability> result=new ArrayList<Ability>();
        for(final Enumeration<Room> e=CMLib.map().rooms();e.hasMoreElements();)
        {
            final Room R=e.nextElement();
            final Ability A=R.fetchEffect("Prop_Waypoint"); if(A!=null) result.add(A);
            for(final Enumeration<Item> items=R.items();items.hasMoreElements();)
            {
                final Ability P=items.nextElement().fetchEffect("Prop_Waypoint"); if(P!=null) result.add(P);
            }
        }
        return result;
    }
    public static List<Ability> here(final MOB M)
    {
        final List<Ability> result=new ArrayList<Ability>();
        if(M.location()==null) return result;
        final Ability A=M.location().fetchEffect("Prop_Waypoint"); if(A!=null) result.add(A);
        for(final Enumeration<Item> e=M.location().items();e.hasMoreElements();)
        {
            final Item I=e.nextElement(); final Ability P=I.fetchEffect("Prop_Waypoint");
            if(P!=null&&CMLib.flags().canBeSeenBy(I,M)) result.add(P);
        }
        for(final Enumeration<Item> e=M.items();e.hasMoreElements();)
        {
            final Ability P=e.nextElement().fetchEffect("Prop_Waypoint"); if(P!=null) result.add(P);
        }
        return result;
    }
    public static Environmental target(final MOB who, final String description)
    {
        final Environmental found=Combinations.thing(who,description);
        if(found!=null) return found;
        for(final Ability A : here(who))
            if(name(A).equalsIgnoreCase(description)||CMLib.english().containsString(name(A),description)) return A.affecting();
        return null;
    }
    public static String list(final MOB M, final Ability from)
    {
        final List<String> names=new ArrayList<String>();
        for(final Ability A : all()) if(network(A).equalsIgnoreCase(network(from))&&known(M,A)&&fits(M,A)) names.add(name(A));
        Collections.sort(names);
        return network(from)+": "+(names.isEmpty()?"no discovered destinations":String.join(", ",names));
    }
    public static boolean use(final MOB M, final Environmental host)
    {
        final Ability A=host instanceof Physical?((Physical)host).fetchEffect("Prop_Waypoint"):null;
        if(A==null) return false;
        final String mode=Travel.option(A.text(),"DISCOVER","ENTER");
        if(mode.equalsIgnoreCase("TOUCH")||mode.equalsIgnoreCase("ENTER")||mode.equalsIgnoreCase("QUEST")) discover(M,A,false);
        M.tell(list(M,A));
        return true;
    }
    public static String attune(final MOB M, final Ability A)
    {
        if(!fits(M,A)) return "You cannot attune to this waypoint.";
        if(Travel.option(A.text(),"DISCOVER","ENTER").equalsIgnoreCase("RITUAL"))
        {
            final String function=Travel.option(A.text(),"RITUAL","");
            final PhysicalAgent host=A.affecting() instanceof PhysicalAgent?(PhysicalAgent)A.affecting():M.location();
            if(function.length()>0)
            {
                final String result=ScriptEvents.callFunction(host,function,"",M);
                if(result==null||ScriptHooks.refused(result)) return "The ritual is incomplete.";
            }
        }
        return discover(M,A,false)?"Waypoint attuned.":"You have not met this waypoint's discovery requirements.";
    }
    public static String travel(final MOB M, final Ability from, final String destination)
    {
        if(M.fetchEffect("Prop_JourneyState")!=null||M.getGroupLeader().fetchEffect("Prop_JourneyState")!=null) return "Finish or cancel your journey first.";
        if(!known(M,from)||!fits(M,from)) return "You must attune to this waypoint first.";
        Ability target=null;
        for(final Ability A : all())
            if(network(A).equalsIgnoreCase(network(from))&&name(A).equalsIgnoreCase(destination)&&known(M,A)&&fits(M,A)) { target=A; break; }
        if(target==null) return "You have not discovered that destination in this network.";
        final Room to=CMLib.map().roomLocation(target.affecting());
        if(to==null||to==M.location()) return "You are already there.";
        final List<MOB> party=Travel.party(M,M.location());
        for(final MOB member : party) if(member.isInCombat()) return "Finish the fight before travelling.";
        final List<String> cost=CMParms.parse(Travel.option(from.text(),"COST","moves 0"));
        if(cost.size()!=2||!CMath.isNumber(cost.get(1))) return "The waypoint has an invalid COST.";
        final double amount=Math.max(0,CMath.s_double(cost.get(1)));
        final String kind=cost.get(0).toUpperCase();
        if(!Arrays.asList("MOVES","MOVEMENT","MANA","GOLD","MONEY").contains(kind)) return "Unknown waypoint cost type.";
        if((kind.equals("MANA")&&M.curState().getMana()<amount)
        ||((kind.equals("MOVES")||kind.equals("MOVEMENT"))&&M.curState().getMovement()<amount)
        ||((kind.equals("GOLD")||kind.equals("MONEY"))&&CMLib.beanCounter().getTotalAbsoluteNativeValue(M)<amount)) return "You cannot pay the waypoint's cost.";
        if(!Travel.transfer(M,party,to,true,() -> {
            if(kind.equals("MANA")) M.curState().adjMana(-(int)Math.ceil(amount),M.maxState());
            else if(kind.equals("MOVES")||kind.equals("MOVEMENT")) M.curState().adjMovement(-(int)Math.ceil(amount),M.maxState());
            else CMLib.beanCounter().subtractMoney(M,amount);
            return true;
        })) return "The party cannot enter that destination.";
        final String message=Travel.option(from.text(),"MSG","");
        if(message.length()>0) for(final MOB member : party) member.tell(message);
        Travel.elapse(Math.round(Math.max(0,CMParms.getParmDouble(from.text(),"TIME",0))*CMProps.getMillisPerMudHour()));
        return "You travel to "+name(target)+".";
    }
}
