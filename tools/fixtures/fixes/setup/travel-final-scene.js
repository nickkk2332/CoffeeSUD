FIXFILE="travel-final-scene";
var state=M.fetchEffect("Prop_JourneyState");
check("final hour paused after restore",state!=null&&String(state.status()).indexOf("0.0 game hours")>=0&&String(state.status()).indexOf("paused")>=0&&M.location().roomID()=="FixTravelSave#1",state==null?"missing journey":String(state.status()));
done();
