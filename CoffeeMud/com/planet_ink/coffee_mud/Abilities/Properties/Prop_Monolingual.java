package com.planet_ink.coffee_mud.Abilities.Properties;

import java.util.List;
import com.planet_ink.coffee_mud.core.Languages;
import com.planet_ink.coffee_mud.core.interfaces.Physical;
import com.planet_ink.coffee_mud.MOBS.interfaces.MOB;

/* Copyright 2026 CoffeeSUD contributors. Licensed under the Apache License, Version 2.0. */
/** Declares an NPC's languages before its first speech, order or trade. */
public class Prop_Monolingual extends Property
{
	@Override public String ID()
	{
		return "Prop_Monolingual";
	}
	@Override public String name()
	{
		return "Monolingual";
	}
	@Override protected int canAffectCode()
	{
		return CAN_MOBS;
	}
	@Override public void setAffectedOne(final Physical host)
	{
		super.setAffectedOne(host);
		selectLanguage();
	}
	@Override public void setMiscText(final String text)
	{
		super.setMiscText(text);
		selectLanguage();
	}
	private void selectLanguage()
	{
		if(!(affected instanceof MOB))
			return;
		final MOB who=(MOB)affected;
		final List<String> languages=Languages.restricted(who);
		if(!languages.isEmpty()&&!languages.contains("Common")&&Languages.spoken(who).equalsIgnoreCase("Common"))
			Languages.ensureSpoken(who,languages.get(0));
	}
}
