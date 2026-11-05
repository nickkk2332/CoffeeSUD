package com.planet_ink.coffee_mud.core;
import java.util.*;
import java.util.regex.*;
import com.planet_ink.coffee_mud.Libraries.interfaces.XMLLibrary.XMLTag;
/* Copyright 2026 CoffeeSUD contributors. Licensed under the Apache License, Version 2.0. */
/** Shared MovingRoom stop model: structural errors must mean the same to lint and runtime. */
public final class RouteStops
{
    public final List<String> rooms = new ArrayList<String>();
    public final List<String> errors = new ArrayList<String>();
    private RouteStops() { }
    public static XMLTag find(final List<XMLTag> roots, final String name)
    {
        for(final XMLTag tag:roots)
        {
            if(tag.tag().equalsIgnoreCase(name))return tag;
            final XMLTag nested=find(tag.contents(),name);if(nested!=null)return nested;
        }
        return null;
    }
    public static RouteStops parse(final String body)
    {
        final RouteStops result = new RouteStops();
        final SortedMap<Integer,String> stops = new TreeMap<Integer,String>();
        for(final XMLTag tag : CMLib.xml().parseAllXML(body))
        {
            final Matcher index = Pattern.compile("STOP([1-9][0-9]*)").matcher(tag.tag());
            if(!index.matches()) { result.errors.add("unexpected route stop element " + tag.tag()); continue; }
            final int n = CMath.s_int(index.group(1));
            final String room = tag.value().trim();
            if(n <= 0) { result.errors.add("invalid route stop index " + tag.tag()); continue; }
            if(stops.put(Integer.valueOf(n), room) != null) result.errors.add("duplicate STOP" + n);
            if(room.isEmpty()) result.errors.add("STOP" + n + " has no room");
        }
        int expected = 1;
        for(final Map.Entry<Integer,String> stop : stops.entrySet())
        {
            if(stop.getKey().intValue() != expected) result.errors.add("expected STOP" + expected + ", found STOP" + stop.getKey());
            expected = stop.getKey().intValue() + 1;
            result.rooms.add(stop.getValue());
        }
        if(result.rooms.size() < 2) result.errors.add("route needs at least two STOPn rooms");
        return result;
    }
}
