// S16, once planted: the crop grows in the room, which the save is made with
FIXFILE = "s16-planted";
var R = M.location();
var crop = R.fetchEffect("Farming");
check("growing", crop != null, "no crop grows in " + CMLib.map().getExtendedRoomID(R));
if (crop != null) M.tell("FIXINFO crop ticks left " + crop.getStat("TICKDOWN") + ", activity " + crop.getStat("ACTIVITY"));
done();
