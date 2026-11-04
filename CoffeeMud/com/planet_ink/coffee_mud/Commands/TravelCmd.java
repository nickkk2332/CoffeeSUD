package com.planet_ink.coffee_mud.Commands;
import java.util.*;
import com.planet_ink.coffee_mud.core.*;
import com.planet_ink.coffee_mud.Abilities.interfaces.*;
import com.planet_ink.coffee_mud.MOBS.interfaces.*;
/** Travel only through discovered, accessible waypoint networks. */
public class TravelCmd extends StdCommand
{
    private final String[] access=I(new String[]{"TRAVEL"});
    @Override public String[] getAccessWords() { return access; }
    @Override public boolean execute(final MOB M, final List<String> commands, final int flags)
    {
        final List<Ability> here=Waypoints.here(M);
        if(here.isEmpty()) { M.tell(L("There is no waypoint here.")); return false; }
        final String destination=CMParms.combine(commands,1);
        if(destination.length()==0)
        { for(final Ability A : here) M.tell(Waypoints.list(M,A)); }
        else if(destination.equalsIgnoreCase("ATTUNE"))
        { for(final Ability A : here) M.tell(Waypoints.attune(M,A)); }
        else
        {
            String result="You have not discovered that destination in this network.";
            for(final Ability A : here)
            {
                result=Waypoints.travel(M,A,destination);
                if(!result.startsWith("You have not discovered")) break;
            }
            M.tell(result);
        }
        return false;
    }
    @Override public boolean canBeOrdered() { return true; }
}
