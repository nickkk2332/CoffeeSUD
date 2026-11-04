package com.planet_ink.coffee_mud.Commands;
import java.util.*;
import com.planet_ink.coffee_mud.core.*;
import com.planet_ink.coffee_mud.Abilities.interfaces.*;
import com.planet_ink.coffee_mud.Locales.interfaces.*;
import com.planet_ink.coffee_mud.MOBS.interfaces.*;
/** Known authored navigation destinations. */
public class Ports extends StdCommand
{
    private final String[] access=I(new String[]{"PORTS"});
    @Override public String[] getAccessWords() { return access; }
    @Override public boolean execute(final MOB mob, final List<String> commands, final int flags)
    {
        final List<String> lines=new ArrayList<String>();
        for(final Enumeration<Room> e=CMLib.map().rooms();e.hasMoreElements();)
        {
            final Room R=e.nextElement();
            if(!Travel.knownPort(R,mob)) continue;
            final Ability P=R.fetchEffect("Prop_Port");
            lines.add(Travel.option(P.text(),"NAME",R.displayText())+" ("+Travel.option(P.text(),"KIND","SEA")+")");
        }
        Collections.sort(lines);
        mob.tell(lines.isEmpty()?L("You know of no named ports."):String.join("\n\r",lines));
        return false;
    }
    @Override public boolean canBeOrdered() { return true; }
}
