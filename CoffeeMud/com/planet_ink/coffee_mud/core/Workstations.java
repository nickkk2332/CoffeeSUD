package com.planet_ink.coffee_mud.core;

import java.util.*;

import com.planet_ink.coffee_mud.Abilities.interfaces.Ability;
import com.planet_ink.coffee_mud.Items.interfaces.Item;
import com.planet_ink.coffee_mud.Locales.interfaces.Room;
import com.planet_ink.coffee_mud.MOBS.interfaces.MOB;
import com.planet_ink.coffee_mud.core.interfaces.ItemPossessor;
import com.planet_ink.coffee_mud.core.interfaces.Taggable;

import com.planet_ink.coffee_mud.Common.interfaces.PlayerStats;
import com.planet_ink.coffee_mud.Items.interfaces.Boardable;
import com.planet_ink.coffee_mud.Items.interfaces.Item;
import com.planet_ink.coffee_mud.Locales.interfaces.Room;
import com.planet_ink.coffee_mud.MOBS.interfaces.MOB;
import com.planet_ink.coffee_mud.core.interfaces.LandTitle;
import com.planet_ink.coffee_mud.core.interfaces.PrivateProperty;
import com.planet_ink.coffee_mud.Common.interfaces.Clan;

/*
   Copyright 2026 CoffeeSUD contributors

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at
/**
 * CR3: workstations and tool wear.  The stations in a room are its own
 * Prop_Workstation and those of the items lying in it; an item there with
 * a tag (TAGS) is a station of that name with no bonus.  A crafter in the
 * room gets the best SPEED, PROF, MAXLEVEL and QUALITY of the stations that
 * help their skill.  A component with the fate wear(n) is kept but loses n
 * uses (its condition) and breaks at none.
 */
public final class Workstations
{
	private Workstations()
	{
	}

	/**
	 * A workstation, as Prop_Workstation is.  (Abilities are loaded by the
	 * game's own class loader, so core code knows them by this interface.)
	 */
	public interface Station
	{
		/**
		 * @param tag a station's name, as forge
		 * @return whether this is one
		 */
		public boolean isA(String tag);

		/**
		 * @param skill a skill
		 * @return whether this helps it
		 */
		public boolean helps(Ability skill);

		/** @return percent off the time a craft takes */
		public int speed();

		/** @return added to the crafter's proficiency */
		public int prof();

		/** @return recipes up to this level may be made here */
		public int maxLevel();

		/** @return the quality it adds */
		public int quality();
	}

	/**
	 * @param R a room
	 * @return its stations: the room's own, then those of the items lying in it
	 */
	public static List<Station> at(final Room R)
	{
		final List<Station> found = new ArrayList<Station>(2);
		if(R == null)
			return found;
		for(final Enumeration<Ability> a = R.effects(); a.hasMoreElements();)
		{
			final Ability A = a.nextElement();
			if(A instanceof Station)
				found.add((Station)A);
		}
		for(int i = 0; i < R.numItems(); i++)
		{
			final Item I = R.getItem(i);
			if((I == null) || (I.container() != null))
				continue;
			for(final Enumeration<Ability> a = I.effects(); a.hasMoreElements();)
			{
				final Ability A = a.nextElement();
				if(A instanceof Station)
					found.add((Station)A);
			}
		}
		return found;
	}

	/**
	 * @param R a room
	 * @param tag a station's name, as forge
	 * @return whether there is one there: a Station of that TAG, or an item lying there with that tag
	 */
	public static boolean has(final Room R, final String tag)
	{
		if((R == null) || (tag == null) || (tag.trim().length() == 0))
			return false;
		for(final Station W : at(R))
		{
			if(W.isA(tag))
				return true;
		}
		for(int i = 0; i < R.numItems(); i++)
		{
			final Item I = R.getItem(i);
			if((I != null) && (I.container() == null) && (I instanceof Taggable) && ((Taggable)I).hasTag(tag.trim()))
				return true;
		}
		return false;
	}

	/**
	 * @param mob a crafter
	 * @param skill their skill
	 * @return the stations in their room that help it
	 */
	public static List<Station> helping(final MOB mob, final Ability skill)
	{
		final List<Station> found = new ArrayList<Station>(2);
		if((mob == null) || (skill == null))
			return found;
		for(final Station W : at(mob.location()))
		{
			if(W.helps(skill))
				found.add(W);
		}
		return found;
	}

	/** @return the best SPEED of the stations helping, in percent */
	public static int speed(final MOB mob, final Ability skill)
	{
		int best = 0;
		for(final Station W : helping(mob, skill))
			best = Math.max(best, W.speed());
		return best;
	}

	/** @return the best PROF of the stations helping */
	public static int prof(final MOB mob, final Ability skill)
	{
		int best = 0;
		for(final Station W : helping(mob, skill))
			best = Math.max(best, W.prof());
		return best;
	}

	/** @return the best MAXLEVEL of the stations helping, or 0 */
	public static int maxLevel(final MOB mob, final Ability skill)
	{
		int best = 0;
		for(final Station W : helping(mob, skill))
			best = Math.max(best, W.maxLevel());
		return best;
	}

	/** @return the best QUALITY of the stations helping */
	public static int quality(final MOB mob, final Ability skill)
	{
		int best = 0;
		for(final Station W : helping(mob, skill))
			best = Math.max(best, W.quality());
		return best;
	}

	/**
	 * @param mob a crafter
	 * @param skill their skill
	 * @param ticks how long a craft takes
	 * @param min the least it may take
	 * @return how long it takes at the best station
	 */
	public static int faster(final MOB mob, final Ability skill, final int ticks, final int min)
	{
		final int pct = speed(mob, skill);
		if(pct <= 0)
			return ticks;
		return Math.max(min, ticks - (int)Math.round(ticks * (pct / 100.0)));
	}

	/**
	 * @param mob a crafter
	 * @param station what a recipe or skill asks for, or nothing
	 * @return null if it is there (or none is asked for), else what they are told
	 */
	public static String refusal(final MOB mob, final String station)
	{
		if((station == null) || (station.trim().length() == 0) || (mob == null))
			return null;
		if(has(mob.location(), station.trim()))
			return null;
		return CMLib.lang().L("You need @x1 here.", CMLib.english().startWithAorAn(station.trim().toLowerCase()));
	}

	/**
	 * A tool used with the fate wear(n): kept, its uses down by n; it breaks at none.
	 * @param I the tool
	 * @param n how much
	 */
	public static void wear(final Item I, final int n)
	{
		if((I == null) || (n <= 0) || I.amDestroyed())
			return;
		final int left = I.usesRemaining() - n;
		if(left > 0)
		{
			I.setUsesRemaining(left);
			I.recoverPhyStats();
			return;
		}
		final ItemPossessor P = I.owner();
		if(P instanceof MOB)
			((MOB)P).tell(CMLib.lang().L("@x1 breaks.", CMStrings.capitalizeFirstLetter(I.name((MOB)P))));
		else
		if(P instanceof Room)
			((Room)P).showHappens(com.planet_ink.coffee_mud.Common.interfaces.CMMsg.MSG_OK_VISUAL, CMLib.lang().L("@x1 breaks.", CMStrings.capitalizeFirstLetter(I.name())));
		I.destroy();
	}
}
