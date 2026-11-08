#!/usr/bin/env python3
"""Build and exercise the documented example on isolated world/db/save copies.

Uses the existing sudplay/playtest runners. KEEP=1 retains passing artifacts.
Failures retain their transcripts. No engine source or shipped world is edited.
"""
from pathlib import Path
import os
import shutil
import subprocess
import sys
import tempfile

from make_world import create

HERE = Path(__file__).resolve().parent
ROOT = HERE.parents[2]


def main():
    work = Path(tempfile.mkdtemp(prefix='sud-creator-examples.'))
    game = create(work / 'deployment')
    env = dict(os.environ)
    env['JAVA_TOOL_OPTIONS'] = '-Djava.awt.headless=true ' + env.get('JAVA_TOOL_OPTIONS', '')
    env['WORLD'] = str(game / 'world')
    env['SUDPLAY_EXPECT_LIMIT'] = '30'
    env['SUDPLAY_LIMIT'] = '240'
    overrides = ['WORLDDIR=' + str(game / 'world'), 'SAVEDIR=' + str(game / 'saves'),
                 'DBSERVICE=jdbc:fakedb:' + str(game / 'db'), 'TIMEMODE=TURNS', 'RANDOMSEED=42']

    def play(name, text, args, expected):
        script = work / (name + '.input')
        script.write_text(text, encoding='utf-8')
        result = subprocess.run([sys.executable, str(ROOT / 'tools/sudplay.py'), str(script)] + overrides + args,
                                cwd=ROOT, env=env, stdout=subprocess.PIPE, stderr=subprocess.STDOUT)
        transcript = result.stdout.decode('utf-8', 'replace')
        (work / (name + '.txt')).write_text(transcript, encoding='utf-8')
        if result.returncode:
            raise RuntimeError(name + ': process failed: ' + str(result.returncode))
        for marker in expected:
            if marker not in transcript:
                raise RuntimeError(name + ': missing ' + marker)
        if 'JavaScript error' in transcript or 'JS error in JRUN' in transcript:
            raise RuntimeError(name + ': JavaScript error')
        print('PASS ' + name + ' (' + str(len(expected)) + ' transcript checks)', flush=True)

    passed = False
    try:
        character = 'Curator\nY\nN\nHuman\nY\nM\nN\nApprentice\nY\ngood\n\n'
        play('build', character + 'jrun /resources/creator/build.js\nsave\nquit\nY\n', ['BUILDER=1'],
             ['CREATOR MUSEUM BUILT', 'START=CreatorMuseum#0'])
        if not list((game / 'world/areas').glob('*museum*')):
            raise RuntimeError('builder SAVE did not export the museum')
        result = subprocess.run([sys.executable, str(ROOT / 'tools/playtest.py'),
                                 str(game / 'world/tests/museum.play'), str(game / 'world/tests/museum.js')],
                                cwd=ROOT, env=env, stdout=subprocess.PIPE, stderr=subprocess.STDOUT)
        transcript = result.stdout.decode('utf-8', 'replace')
        (work / 'world-tests.txt').write_text(transcript, encoding='utf-8')
        print(transcript, end='', flush=True)
        if result.returncode:
            raise RuntimeError('world tests failed')
        visitor = 'Visitor\nY\nN\nHuman\nY\nM\nN\nApprentice\nY\ngood\n\n'
        play('save', visitor + 'pull lever\neast\nread tablet\nmuseumbell\nsave museum\nquit\nn\n',
             ['RESTORE=NEW'],
             ['The east gate grinds open.', 'You learn the history', 'Museum bell count: 1'])
        play('restore', 'museumstatus\nlore COAST\nwest\nlook\nquit\nn\n',
             ['RESTORE=museum'],
             ["Restored 'museum'.", 'Museum bell count: 1', 'The Drowned Coast', 'The east gate stands open.'])
        passed = True
        print('PASS creator example build, normal play, JavaScript contracts and fresh-process restore.')
        return 0
    except (RuntimeError, OSError) as error:
        print('FAIL ' + str(error), file=sys.stderr)
        return 1
    finally:
        if passed and os.environ.get('KEEP') != '1':
            shutil.rmtree(work)
        else:
            print('Artifacts: ' + str(work), flush=True)


if __name__ == '__main__':
    sys.exit(main())
