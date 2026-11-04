package com.planet_ink.coffee_mud.Commands;
import java.util.*;
import com.planet_ink.coffee_mud.core.*;
import com.planet_ink.coffee_mud.Behaviors.*;
import com.planet_ink.coffee_mud.Behaviors.interfaces.*;
import com.planet_ink.coffee_mud.Items.interfaces.*;
import com.planet_ink.coffee_mud.MOBS.interfaces.*;
/** Published route state, including lines with no sign item. */
public class Timetable extends StdCommand
{
    private final String[] access=I(new String[]{"TIMETABLE"});
    @Override public String[] getAccessWords() { return access; }
    @Override public boolean execute(final MOB mob, final List<String> commands, final int flags)
    {
        final String name=CMParms.combine(commands,1);
        final List<String> lines=new ArrayList<String>();
        final Set<com.planet_ink.coffee_mud.core.interfaces.PhysicalAgent> seen=Collections.newSetFromMap(new IdentityHashMap<com.planet_ink.coffee_mud.core.interfaces.PhysicalAgent,Boolean>());
        for(final Enumeration<com.planet_ink.coffee_mud.Locales.interfaces.Room> e=CMLib.map().rooms();e.hasMoreElements();)
        {
            final com.planet_ink.coffee_mud.Locales.interfaces.Room R=e.nextElement();
            append(R,name,lines,seen);
            for(final Enumeration<MOB> mobs=R.inhabitants();mobs.hasMoreElements();) append(mobs.nextElement(),name,lines,seen);
            for(final Enumeration<Item> items=R.items();items.hasMoreElements();) append(items.nextElement(),name,lines,seen);
        }
        for(final Enumeration<Boardable> e=CMLib.map().ships();e.hasMoreElements();) append(e.nextElement().getBoardableItem(),name,lines,seen);
        mob.tell(lines.isEmpty()?L("No transport line matches."):String.join("\n\r",lines));
        return false;
    }
    private void append(final com.planet_ink.coffee_mud.core.interfaces.PhysicalAgent host, final String name,
                        final List<String> lines, final Set<com.planet_ink.coffee_mud.core.interfaces.PhysicalAgent> seen)
    {
        if(host==null||!seen.add(host)) return;
        for(final Enumeration<Behavior> e=host.behaviors();e.hasMoreElements();)
        {
            final Behavior B=e.nextElement();
            if(B instanceof Travel.Timetable)
            {
                final String text=((Travel.Timetable)B).timetable();
                if(name.length()==0||CMLib.english().containsString(host.Name(),name)||CMLib.english().containsString(text,name)) lines.add(text);
            }
        }
    }
    @Override public boolean canBeOrdered() { return true; }
}
