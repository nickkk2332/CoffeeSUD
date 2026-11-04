package com.planet_ink.coffee_mud.Abilities.Properties;

import java.util.*;
import com.planet_ink.coffee_mud.core.*;
import com.planet_ink.coffee_mud.core.interfaces.*;
import com.planet_ink.coffee_mud.Abilities.interfaces.*;
import com.planet_ink.coffee_mud.Common.interfaces.*;
import com.planet_ink.coffee_mud.MOBS.interfaces.*;

/*
   Copyright 2026 CoffeeSUD contributors

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

       http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License.
*/
/** Transient FData handlers, rebuilt from PLAYEREFFECT rules, never saved on a MOB. */
public class Prop_FactionEffects extends Property
{
    @Override public String ID() { return "Prop_FactionEffects"; }
    @Override public String name() { return "Faction effects"; }
    @Override protected int canAffectCode() { return Ability.CAN_MOBS; }
    private Map<String,Ability> active=new LinkedHashMap<String,Ability>();
    private boolean refreshing=false;

    private List<Ability> handlers()
    {
        if(refreshing||!(affected instanceof MOB)) return new ArrayList<Ability>(active.values());
        refreshing=true;
        try
        {
            final MOB M=(MOB)affected;
            final Faction F=CMLib.factions().getFaction(text());
            final Map<String,Ability> next=new LinkedHashMap<String,Ability>();
            final Faction.FRange range=F==null?null:F.fetchRange(M.fetchFaction(text()));
            if(F!=null&&range!=null&&!M.isMonster())
            {
                final int count=CMath.s_int(F.getTagValue("PLAYEREFFECT"));
                for(int i=0;i<count;i++)
                {
                    final String rule=F.getTagValue("PLAYEREFFECT"+i);
                    final String[] p=rule.split(";",3);
                    if(p.length<3) continue;
                    final String gate=p[2].trim();
                    final boolean fits=gate.startsWith("-")||gate.startsWith("+")?CMLib.masking().maskCheck(gate,M,true):CMParms.parseCommas(gate.toUpperCase(),true).contains(range.codeName().toUpperCase());
                    if(!fits) continue;
                    Ability A=active.get(rule);
                    if(A==null)
                    {
                        A=CMClass.getAbility(p[0].trim());
                        if(A==null) continue;
                        A.setSavable(false);
                        A.setMiscText(p[1]);
                        A.setAffectedOne(M);
                    }
                    next.put(rule,A);
                }
            }
            for(final Map.Entry<String,Ability> e : active.entrySet()) if(!next.containsKey(e.getKey())) { e.getValue().unInvoke(); e.getValue().setAffectedOne(null); }
            active=next;
            return new ArrayList<Ability>(active.values());
        }
        finally { refreshing=false; }
    }
    @Override public void affectPhyStats(final Physical M, final PhyStats stats) { for(final Ability A : handlers()) A.affectPhyStats(M,stats); }
    @Override public void affectCharStats(final MOB M, final CharStats stats) { for(final Ability A : handlers()) A.affectCharStats(M,stats); }
    @Override public void affectCharState(final MOB M, final CharState stats) { for(final Ability A : handlers()) A.affectCharState(M,stats); }
    @Override public boolean tick(final Tickable M, final int id) { for(final Ability A : handlers()) A.tick(M,id); return true; }
    @Override public boolean okMessage(final Environmental host, final CMMsg msg) { for(final Ability A : handlers()) if(!A.okMessage(host,msg)) return false; return true; }
    @Override public void executeMsg(final Environmental host, final CMMsg msg) { for(final Ability A : handlers()) A.executeMsg(host,msg); }
    @Override public CMObject copyOf() { final Prop_FactionEffects copy=(Prop_FactionEffects)super.copyOf(); copy.active=new LinkedHashMap<String,Ability>(); copy.refreshing=false; return copy; }
}
