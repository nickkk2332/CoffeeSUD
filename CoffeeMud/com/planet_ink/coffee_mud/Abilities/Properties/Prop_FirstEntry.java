package com.planet_ink.coffee_mud.Abilities.Properties;
import com.planet_ink.coffee_mud.core.*;
import com.planet_ink.coffee_mud.core.interfaces.*;
import com.planet_ink.coffee_mud.Common.interfaces.*;
import com.planet_ink.coffee_mud.Locales.interfaces.*;
import com.planet_ink.coffee_mud.MOBS.interfaces.*;
/* Copyright 2026 CoffeeSUD contributors. Licensed under the Apache License, Version 2.0. */
/** Once-only arrival text and clues, scoped to a character or their profile. */
public class Prop_FirstEntry extends Property
{
	@Override public String ID()
	{
		return "Prop_FirstEntry";
	}
	@Override public String name()
	{
		return "First entry";
	}
	@Override protected int canAffectCode()
	{
		return CAN_ROOMS|CAN_AREAS;
	}
	@Override public void executeMsg(final Environmental host, final CMMsg msg)
	{
		super.executeMsg(host,msg);
		if(!msg.source().isPlayer()||(msg.targetMinor()!=CMMsg.TYP_ENTER&&msg.targetMinor()!=CMMsg.TYP_RECALL)||!(msg.target() instanceof Room))
			return;
		final Room room=(Room)msg.target();
		if(affected!=room&&affected!=room.getArea())
			return;
		final MOB who=msg.source();
		final PlayerAccount account=Party.profileOf(who);
		final Tattooable record=Scenery.option(text(),"ONCE","CHARACTER").equalsIgnoreCase("PROFILE")&&account!=null?account:who;
		final String id=affected instanceof Room?CMLib.map().getExtendedRoomID((Room)affected):affected.Name();
		final String key=StoryRecords.token("FIRSTENTRY_",Scenery.option(text(),"ID",id));
		if(AuthorIds.recorded(record,"FIRSTENTRY_",affected,"FIRSTENTRY",text(),"",key))
			return;
		record.addTattoo(AuthorIds.key("FIRSTENTRY_",affected,"FIRSTENTRY",text(),"",true));
		final String message=StoryText.render(who,affected,room.getArea(),room,Scenery.option(text(),"TEXT",""));
		if(!message.isEmpty())
			who.tell(message);
		final String tattoo=Scenery.option(text(),"TATTOO","");
		if(!tattoo.isEmpty())
			who.addTattoo(tattoo);
		StoryText.setVariable(who,Scenery.option(text(),"SETVAR",""));
	}
}
