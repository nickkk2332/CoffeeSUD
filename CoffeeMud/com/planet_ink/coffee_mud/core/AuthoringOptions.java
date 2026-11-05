package com.planet_ink.coffee_mud.core;

import java.util.*;

/* Copyright 2026 CoffeeSUD contributors. Licensed under the Apache License, Version 2.0. */
/** The option syntax used by presentation, language and travel authoring. */
public final class AuthoringOptions
{
    private AuthoringOptions() {}

    public static final class Token
    {
        public final String key, value;
        public final int start, end;
        public final boolean assignment;
        private Token(final String key, final String value, final int start, final int end, final boolean assignment)
        { this.key=key; this.value=value; this.start=start; this.end=end; this.assignment=assignment; }
    }

    private static boolean separator(final char c) { return Character.isWhitespace(c)||c==';'; }
    public static List<Token> tokens(final String text)
    {
        final List<Token> out=new ArrayList<Token>();
        for(int i=0;i<text.length();)
        {
            if(separator(text.charAt(i))) { i++; continue; }
            final int start=i;
            while(i<text.length()&&!separator(text.charAt(i))&&text.charAt(i)!='='&&text.charAt(i)!='"') i++;
            final String key=text.substring(start,i);
            int eq=i;
            while(eq<text.length()&&Character.isWhitespace(text.charAt(eq))) eq++;
            final boolean assignment=!key.isEmpty()&&eq<text.length()&&text.charAt(eq)=='=';
            if(assignment) { i=eq+1; while(i<text.length()&&Character.isWhitespace(text.charAt(i))) i++; }
            final StringBuilder value=new StringBuilder();
            boolean quoted=false;
            // Consume the entire token, including quoted segments. Never scan its contents for keys.
            while(i<text.length()&&(quoted||!separator(text.charAt(i))))
            {
                final char c=text.charAt(i++);
                if(c=='\\'&&i<text.length()&&(text.charAt(i)=='"'||text.charAt(i)=='\\'||text.charAt(i)==';'))
                    value.append(text.charAt(i++));
                else if(c=='"') quoted=!quoted;
                else value.append(c);
            }
            if(i==start) i++;
            out.add(new Token(key.toUpperCase(Locale.ROOT),assignment?value.toString():text.substring(start,i),start,i,assignment));
        }
        return out;
    }

    public static String option(final String text, final String key, final String fallback)
    {
        for(final Token token:tokens(text)) if(token.assignment&&token.key.equalsIgnoreCase(key)) return token.value;
        return fallback;
    }
    public static boolean flag(final String text, final String key)
    {
        for(final Token token:tokens(text)) if(!token.assignment&&token.value.equalsIgnoreCase(key)) return true;
        return false;
    }
    public static double number(final String text,final String key,final double fallback)
    {
        try {final double n=Double.parseDouble(option(text,key,""));return Double.isFinite(n)?n:fallback;}
        catch(final NumberFormatException e){return fallback;}
    }
    public static int integer(final String text,final String key,final int fallback)
    {
        String value=option(text,key,"");
        if(key.equalsIgnoreCase("CHANCE")&&value.endsWith("%"))value=value.substring(0,value.length()-1);
        try {return Integer.parseInt(value);}
        catch(final NumberFormatException e){return fallback;}
    }
    public static boolean bool(final String text,final String key,final boolean fallback)
    {
        final String value=option(text,key,"");return value.equalsIgnoreCase("true")?true:value.equalsIgnoreCase("false")?false:fallback;
    }
    public static List<String> statements(final String text)
    {
        final List<String> out=new ArrayList<String>();
        boolean quoted=false; int start=0;
        for(int i=0;i<text.length();i++)
        {
            final char c=text.charAt(i);
            if(c=='\\'&&i+1<text.length()&&(text.charAt(i+1)=='"'||text.charAt(i+1)=='\\'||text.charAt(i+1)==';')) { i++; continue; }
            if(c=='"') quoted=!quoted;
            else if(c==';'&&!quoted) { out.add(text.substring(start,i).trim()); start=i+1; }
        }
        out.add(text.substring(start).trim()); return out;
    }
    public static List<String> errors(final String text)
    {
        boolean quoted=false;
        for(int i=0;i<text.length();i++)
        {
            final char c=text.charAt(i);
            if(c=='\\'&&i+1<text.length()&&(text.charAt(i+1)=='"'||text.charAt(i+1)=='\\'||text.charAt(i+1)==';')) { i++; continue; }
            if(c=='"') quoted=!quoted;
        }
        return quoted?Collections.singletonList("Unclosed quoted value."):Collections.<String>emptyList();
    }
    public static String without(final String text, final Set<String> options, final Set<String> flags)
    {
        final StringBuilder out=new StringBuilder(); int end=0;
        for(final Token t:tokens(text)) if(t.assignment?options.contains(t.key):flags.contains(t.value.toUpperCase(Locale.ROOT)))
        { out.append(text.substring(end,t.start)); end=t.end; }
        return out.append(text.substring(end)).toString().trim();
    }
}
