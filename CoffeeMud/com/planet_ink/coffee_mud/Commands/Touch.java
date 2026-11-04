package com.planet_ink.coffee_mud.Commands;
import java.util.*;
import com.planet_ink.coffee_mud.core.*;
import com.planet_ink.coffee_mud.core.interfaces.*;
import com.planet_ink.coffee_mud.MOBS.interfaces.*;
/** Touch an authored object, including attunable waystones. */
public class Touch extends StdCommand
{
    private final String[] access=I(new String[]{"TOUCH"});
    @Override public String[] getAccessWords() { return access; }
    @Override public boolean execute(final MOB M, final List<String> commands, final int flags)
    {
        if(commands.size()==1)
        {
            boolean found=false;
            for(final com.planet_ink.coffee_mud.Abilities.interfaces.Ability A : Waypoints.here(M)) found=Waypoints.use(M,A.affecting())||found;
            if(!found) M.tell(L("Touch what here?"));
            return false;
        }
        final Environmental target=Waypoints.target(M,CMParms.combine(commands,1));
        if(target==null) M.tell(L("Touch what here?"));
        else if(!Waypoints.use(M,target)) Combinations.act(M,"TOUCH",target,null);
        return false;
    }
    @Override public boolean canBeOrdered() { return true; }
}
