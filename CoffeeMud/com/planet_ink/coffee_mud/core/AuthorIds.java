package com.planet_ink.coffee_mud.core;

import java.util.*;
import com.planet_ink.coffee_mud.core.interfaces.*;
import com.planet_ink.coffee_mud.Abilities.interfaces.Ability;

/* Copyright 2026 CoffeeSUD contributors. Licensed under the Apache License, Version 2.0. */
/** Saved source identities, independent of names, locations and authored prose. */
public final class AuthorIds
{
    private AuthorIds() {}
    private static String identity(final Physical host, final String scope, final String params, final boolean create)
    {
        final String explicit=Scenery.option(params,"ID","");
        if(!explicit.isEmpty()) return explicit;
        synchronized(host)
        {
            Ability ids=host.fetchEffect("Prop_AuthorIdentity");
            String id=ids==null?"":Scenery.option(ids.text(),scope,"");
            if(id.isEmpty()&&create)
            {
                if(ids==null) { ids=CMClass.getAbility("Prop_AuthorIdentity"); host.addNonUninvokableEffect(ids); }
                id="AUTO:"+UUID.randomUUID().toString();
                ids.setMiscText(ids.text()+" "+scope+"="+id);
            }
            return id;
        }
    }
    public static String key(final String prefix, final Physical host, final String scope, final String params, final String part, final boolean create)
    {
        final String id=identity(host,scope,params,create);
        return id.isEmpty()?null:StoryRecords.token(prefix,scope+":"+id+":"+part);
    }
    /** The first entry is canonical. Old complete hashes may be supplied when migrating shipped content. */
    public static List<String> keys(final String prefix, final Physical host, final String scope, final String params, final String part, final String legacy, final boolean create)
    {
        final List<String> keys=new ArrayList<String>();
        final String current=key(prefix,host,scope,params,part,create);
        if(current!=null) keys.add(current);
        if(legacy!=null) keys.add(legacy);
        for(final String alias:CMParms.parseCommas(Scenery.option(params,"ALIASES",""),true))
        {
            keys.add(StoryRecords.token(prefix,scope+":"+alias+":"+part));
            if(scope.equals("FIRSTENTRY"))keys.add(StoryRecords.token(prefix,alias));
            if(scope.equals("WAYPOINT"))keys.add(StoryRecords.token(prefix,part+"\n"+alias));
        }
        keys.addAll(CMParms.parseCommas(Scenery.option(params,"LEGACYKEYS",""),true));
        return keys;
    }
    public static boolean recorded(final Tattooable record, final String prefix, final Physical host, final String scope, final String params, final String part, final String legacy)
    {
        if(record==null) return false;
        for(final String token:keys(prefix,host,scope,params,part,legacy,false))
            if(record.findTattoo(token)!=null)
            {
                final String canonical=key(prefix,host,scope,params,part,true);
                if(record.findTattoo(canonical)==null) record.addTattoo(canonical);
                return true;
            }
        return false;
    }
}
