package com.planet_ink.coffee_mud.core;

import java.util.*;
import com.planet_ink.coffee_mud.Abilities.interfaces.Ability;

/* Copyright 2026 CoffeeSUD contributors. Licensed under the Apache License, Version 2.0. */
/** Shared option metadata for builder diagnostics and the newer authored properties. */
public final class AuthoringSchema
{
    private AuthoringSchema() {}
    private static final Map<String,Map<String,String>> schemas=new HashMap<String,Map<String,String>>();
    private static Map<String,String> schema(final String id, final String... fields)
    {
        final Map<String,String> result=new LinkedHashMap<String,String>();
        for(final String field:fields) {final int at=field.indexOf(':');result.put(field.substring(0,at),field.substring(at+1));}
        schemas.put(id.toUpperCase(Locale.ROOT),result); return result;
    }
    private static void identities(final Map<String,String> fields)
    { fields.put("ID","text");fields.put("ALIASES","text");fields.put("LEGACYKEYS","text"); }
    static
    {
        identities(schema("Prop_ShowWhen","MASK:text","CHECK:enum:VIEWER,ROOMPLAYERS,ANYPLAYER,HOST","WHEN:text","HIDEMODE:enum:UNSEEN,HIDDEN"));
        identities(schema("Prop_Senses","SOUND:text","TOUCH:text","TASTE:text","CHANCE:int:0:100","SETVAR:text","TATTOO:text"));
        identities(schema("Prop_Details","READ:text","MASK:text","SETVAR:text","TATTOO:text"));
        identities(schema("Prop_FirstEntry","TEXT:text","ONCE:enum:CHARACTER,PROFILE","SETVAR:text","TATTOO:text"));
        identities(schema("Prop_LanguageImmersion","LANGUAGE:language:any","HEAR:int:0:100","READ:int:0:100","CHANCE:int:0:100","START:int:0:100","MAX:int:0:100","GRANTAFTER:int:1:2147483647"));
        schema("Prop_LanguageSpeaker","LANGUAGE:language","PLAYERS:boolean","NOMOBS:boolean","HOMEONLY:boolean","UNDERSTANDS:enum:ONLY,ALL");
        schema("Prop_Monolingual","LANGUAGES:languages","CONFUSED:text");
        schema("Prop_DarkDescription","TITLE:text","DESC:text");
        schema("Prop_Lore","ENTRY:lore","ON:enum:READ,GET,EXAMINE,ENTER,KILL,DEATH","MSG:text","SCOPE:enum:CHAR,CHARACTER,PROFILE");
        schema("Prop_LocalLists","LOAD:file:vocab/","TOD_CHANGE_OUTSIDE:list","TOD_CHANGE_INSIDE:list","HEALTH_CHART:list","DAMAGE_WORDS:list","WEAPON_HIT_DESCS:list","WEAPON_MISS_DESCS:list","CORPSE_BLURBS:list","MAGIC_WORDS:list","GAIT_LIST:list");
        schema("Prop_WeaponMessages","HIT:text","MISS:text","DAMAGEWORDS:list");
        schema("Prop_Journey","DEST:room","VIA:room","DURATION:number:0.0000001:1.7976931348623157E308","FARE:number:0:1.7976931348623157E308","PARTY:enum:LEADER,EACH","PERHEAD:boolean","LEAVEMSG:text","ARRIVEMSG:text","EVENTS:text","MISSING:enum:WAIT,CONTINUE,CANCEL,RETURN,RESCUE","DEAD:enum:WAIT,CONTINUE,CANCEL,RETURN","DISCONNECTED:enum:TRAVEL,WAIT,CONTINUE,CANCEL,RETURN","CANCEL:enum:HERE,RETURN","REFUND:enum:NONE,FULL,UNUSED");
        identities(schema("Prop_Waypoint","NETWORK:text","NAME:text","DISCOVER:enum:ENTER,TOUCH,RITUAL,QUEST","SCOPE:enum:CHARACTER,PROFILE","COST:text","TIME:number:0:1.7976931348623157E308","MASK:text","MSG:text","RITUAL:text","QUEST:quest"));
        schema("Prop_TravelTime","TIME:number:0:1.7976931348623157E308","MOUNTED:number:0:1.7976931348623157E308","VEHICLE:number:0:1.7976931348623157E308","NIGHT:number:0:1.7976931348623157E308");
        schema("Prop_Road","TIME:number:0:1.7976931348623157E308","NOTFOR:text");
        schema("Prop_Port","NAME:text","KIND:enum:SEA,ROAD,AIR,SPACE","HIDDEN:boolean","KNOWNMASK:text");
        schema("Prop_HelmRestrict","NAVMASK:text","MESSAGE:text","ONFAIL:text");
    }
    public static Set<String> keys(final String id)
    {final Map<String,String> s=schemas.get(id.toUpperCase(Locale.ROOT));return s==null?Collections.<String>emptySet():Collections.unmodifiableSet(s.keySet());}
    public static String describe(final String id)
    {
        final Map<String,String> s=schemas.get(id.toUpperCase(Locale.ROOT));
        if(s==null) return "";
        final StringBuilder out=new StringBuilder("\n\rValidated options: ");
        for(final Map.Entry<String,String> e:s.entrySet()) out.append(e.getKey()).append(" (").append(e.getValue()).append(") ");
        return out.toString().trim();
    }
    public static List<String> check(final String id, final String text)
    {
        final List<String> out=new ArrayList<String>();
        final Map<String,String> fields=schemas.get(id.toUpperCase(Locale.ROOT));
        if(fields==null) return out;
        if(id.equalsIgnoreCase("Prop_Details"))
        {
            for(final String record:Scenery.statements(text))
            {final int at=record.indexOf('=');if(at<1){if(!record.isEmpty())out.add("detail needs keyword=description");}else out.addAll(checkFields(id,record.substring(at+1),fields));}
        }
        else out.addAll(checkFields(id,text,fields));
        return out;
    }
    private static List<String> checkFields(final String id,final String text,final Map<String,String> fields)
    {
        final List<String> out=new ArrayList<String>(AuthoringOptions.errors(text));
        final Set<String> seen=new HashSet<String>();
        for(final AuthoringOptions.Token token:AuthoringOptions.tokens(text))
        {
            if(!token.assignment) continue;
            if(!seen.add(token.key)) out.add("duplicate option "+token.key+"; the first value is used");
            final String type=fields.get(token.key),value=token.value;
            if(type==null) continue; // unknown-key diagnostics are shared with the builder editor.
            if(type.startsWith("int:")||type.startsWith("number:"))
            {
                final String[] limits=type.split(":");
                final String number=token.key.equals("CHANCE")?value.replaceFirst("%$",""):value;
                double n=Double.NaN;
                try { n=Double.parseDouble(number); } catch(final NumberFormatException ignored) {}
                if(!Double.isFinite(n)||n<Double.parseDouble(limits[1])||n>Double.parseDouble(limits[2])||type.startsWith("int:")&&n!=Math.rint(n))
                    out.add(token.key+" must be "+(type.startsWith("int:")?"an integer":"a number")+" from "+limits[1]+" to "+limits[2]+" (got "+value+")");
            }
            else if(type.startsWith("enum:")&&!Arrays.asList(type.substring(5).split(",")).contains(value.toUpperCase(Locale.ROOT))) out.add(token.key+" must be one of "+type.substring(5));
            else if(type.equals("boolean")&&!value.equalsIgnoreCase("true")&&!value.equalsIgnoreCase("false")) out.add(token.key+" must be true or false");
            else if(type.startsWith("language")&&!(type.equals("language:any")&&value.equalsIgnoreCase("ANY")))
            {
                final List<String> ids=type.equals("languages")?CMParms.parseCommas(value,true):Collections.singletonList(value);
                for(final String name:ids) if(Languages.language(name)==null) out.add("unknown language "+name);
            }
            else if(type.startsWith("file:"))
            {final String root=type.substring(5),path=value.startsWith(root)?value:root+value;if(Vocabulary.file(path)==null||!Vocabulary.file(path).isFile())out.add("missing file "+path);}
            else if(type.equals("room")&&!value.isEmpty()&&Travel.destination(value,null)==null) out.add("no room or port "+value);
            else if(type.equals("lore")&&!LoreBook.entries().containsKey(value.toUpperCase(Locale.ROOT))) out.add("unknown lore entry "+value);
            else if(type.equals("quest")&&!value.isEmpty()&&CMLib.quests().findQuest(value)==null) out.add("unknown quest "+value);
            if(type.equals("list")) out.addAll(AuthoringCheck.checkList(token.key,value));
            if(type.equals("text")) for(final String error:TextCheck.checkText(value)) out.add(token.key+": "+error);
        }
        if(id.equalsIgnoreCase("Prop_LanguageImmersion")&&CMath.s_int(Scenery.option(text,"START","5"))>CMath.s_int(Scenery.option(text,"MAX","60"))) out.add("START exceeds MAX");
        if(id.equalsIgnoreCase("Prop_Journey"))
        {
            if(Scenery.option(text,"DEST","").isEmpty())out.add("DEST is required");
            if(Scenery.option(text,"DURATION","").isEmpty())out.add("DURATION is required");
        }
        return out;
    }
}
