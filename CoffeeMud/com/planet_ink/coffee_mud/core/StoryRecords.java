package com.planet_ink.coffee_mud.core;

import com.planet_ink.coffee_mud.Abilities.interfaces.Ability;
import com.planet_ink.coffee_mud.MOBS.interfaces.MOB;

/* Copyright 2026 CoffeeSUD contributors. Licensed under the Apache License, Version 2.0. */
/** Saved presentation state, independent of authored world definitions. */
public final class StoryRecords
{
    private StoryRecords() {}
    public static MiniJSON.JSONObject read(final MOB who)
    {
        final Ability A=who==null?null:who.fetchEffect("Prop_StoryRecords");
        if(A!=null&&!A.text().trim().isEmpty())
            try { return new MiniJSON().parseObject(A.text()); }
            catch(final MiniJSON.MJSONException e) { Log.errOut("StoryRecords",e.getMessage()); }
        return new MiniJSON.JSONObject();
    }
    public static void write(final MOB who, final MiniJSON.JSONObject data)
    {
        if(who==null) return;
        Ability A=who.fetchEffect("Prop_StoryRecords");
        if(A==null) { A=CMClass.getAbility("Prop_StoryRecords"); who.addNonUninvokableEffect(A); }
        A.setMiscText(data.toString());
    }
    public static MiniJSON.JSONObject object(final MiniJSON.JSONObject data, final String key)
    {
        final Object old=data.get(key);
        if(old instanceof MiniJSON.JSONObject) return (MiniJSON.JSONObject)old;
        final MiniJSON.JSONObject value=new MiniJSON.JSONObject(); data.put(key,value); return value;
    }
    public static String string(final MiniJSON.JSONObject data, final String key)
    { final Object value=data.get(key); return value==null?"":value.toString(); }
    public static long number(final MiniJSON.JSONObject data, final String key)
    { final Object value=data.get(key); return value instanceof Number?((Number)value).longValue():CMath.s_long(string(data,key)); }
    public static String token(final String prefix, final String key)
    {
        try
        {
            final byte[] bytes=java.security.MessageDigest.getInstance("SHA-256").digest(key.toUpperCase().getBytes(java.nio.charset.StandardCharsets.UTF_8));
            final StringBuilder out=new StringBuilder(prefix);
            for(final byte b : bytes) out.append(String.format("%02X",Integer.valueOf(b&255)));
            return out.toString();
        }
        catch(final java.security.NoSuchAlgorithmException e) { throw new IllegalStateException(e); }
    }
}
