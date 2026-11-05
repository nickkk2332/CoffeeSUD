FIXFILE='review-isolation';
var prepared=CM.core.IsolatedPlaytest.prepare(M),lines=[];
for(var i=0;i<prepared.command.size();i++)lines.push(String(prepared.command.get(i)));
CM.core.Vocabulary.write('resources/review-child-command.txt',lines.join('\n'));
check('prepared child uses complete snapshot',new java.io.File(prepared.directory,'saves/playtest/.complete').isFile(),prepared.directory);
done();
