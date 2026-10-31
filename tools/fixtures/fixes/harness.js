FIXFILE = "harness";
check("runs", M != null, "no mob");
check("room", M.location() != null, "no room");
done();
