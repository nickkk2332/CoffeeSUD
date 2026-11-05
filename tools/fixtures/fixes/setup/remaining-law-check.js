FIXFILE='remaining-law-check';
var B=CMLib.law().getLegalBehavior(M.location()),A=CMLib.law().getLegalObject(M.location());
check('custom law survives fresh boot',B!=null && (''+B.legalInfo(A).getInternalStr('SALESTAX'))=='7.25',B);
done();
