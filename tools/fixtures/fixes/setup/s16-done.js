// S16, some time after the restore: the bowl is carved, and the wheat grown
FIXFILE = "s16-done";
var R = CMLib.map().getRoom("New Area#20");
var bowls = 0, wheat = 0;
for (var i = 0; i < M.numItems(); i++) if (String(M.getItem(i).Name()).indexOf("bowl") >= 0) bowls++;
for (var j = 0; j < R.numItems(); j++) {
	var I = R.getItem(j);
	if (String(I.Name()).indexOf("bowl") >= 0) bowls++;
	if (String(I.Name()).indexOf("wheat") >= 0) wheat++;
}
check("bowl", bowls == 1, bowls + " bowls");
check("wheat", wheat > 0, "no wheat grew");
done();
