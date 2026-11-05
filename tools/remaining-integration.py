#!/usr/bin/env python3
"""Played persistence and in-game runner regressions for the remaining authoring work."""
import os
from pathlib import Path
import shutil
import subprocess
import sys

ROOT=Path(__file__).resolve().parent.parent
GAME=ROOT/'CoffeeMud'
scratch=Path(sys.argv[1]).resolve();scratch.mkdir(parents=True,exist_ok=True)
world,db,saves=(scratch/n for n in ('world','db','saves'))
shutil.copytree(GAME/'world',world);db.mkdir();shutil.copyfile(GAME/'resources/fakedb/fakedb.schema',db/'fakedb.schema')
env=os.environ.copy();env['SUDPLAY_EXPECT_LIMIT']='150'
count=0

def check(ok,name,why=''):
 global count
 if not ok:raise AssertionError(name+': '+why)
 count+=1;print('FIXTEST ok remaining-integration/'+name,flush=True)

def play(name,script,*settings):
 f=scratch/(name+'.script');f.write_text(script)
 with (scratch/(name+'.txt')).open('w') as output:
  result=subprocess.run([sys.executable,str(ROOT/'tools/sudplay.py'),str(f),f'DBSERVICE=jdbc:fakedb:{db}',f'WORLDDIR={world}',f'SAVEDIR={saves}','TIMEMODE=TURNS','RANDOMSEED=42',*settings],cwd=GAME,env=env,stdout=output,stderr=subprocess.STDOUT,timeout=480)
 text=(scratch/(name+'.txt')).read_text(errors='replace')
 check(result.returncode==0,name+' process',text[-2500:])
 check('FIXTEST FAIL' not in text and 'JS error in JRUN' not in text,name+' assertions',text[-2500:])
 return text

newchar='Y\nN\nHuman\nY\nM\nN\nApprentice\nY\ngood\n\n'
try:
 text=play('assist-save','Assistant\n'+newchar+'jrun /resources/_fixes/remaining-assist-save.js\nquit\nn\n','DEV=1','RESTORE=NEW')
 check('FIXTEST done remaining-assist-save' in text,'assisted craft saved')
 text=play('assist-restore','jrun /resources/_fixes/remaining-assist-check.js\nquit\nn\n','DEV=1','RESTORE=assistance')
 check('FIXTEST done remaining-assist-check' in text,'assisted craft restored')
 text=play('law-build','Builder\n'+newchar+'jrun /resources/_fixes/remaining-law-build.js\nquit\nY\n','BUILDER=1')
 check('FIXTEST done remaining-law-build' in text,'law authored')
 tests=world/'tests';tests.mkdir(exist_ok=True)
 (tests/'remaining.play').write_text('#char Runner,Human,Apprentice\nlook\n#assert HERE New Area#0\n')
 (tests/'remaining.js').write_text("test('room exists', function(){assert(mob().location()!=null);});\n")
 text=play('law-and-runner','Builder\n'+'jrun /resources/_fixes/remaining-law-check.js\nworld test remaining.play\n#expect WORLD TEST PASS\nplaytest run remaining.js\n#expect WORLD TEST PASS\nquit\nY\n','BUILDER=1')
 check('FIXTEST done remaining-law-check' in text,'law restored from world content')
 check(text.count('WORLD TEST PASS')>=2,'both in-game runner commands complete')
 print('FIXTEST done remaining-integration (%d checks)'%count,flush=True)
except Exception as exc:
 print('FIXTEST FAIL remaining-integration: '+str(exc),flush=True)
 raise
