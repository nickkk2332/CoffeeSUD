#!/usr/bin/env python3
"""Run playtest files (DT15): regression playtests of the world, kept as content.

    playtest.py [FILE.play ...]

With no files, runs world/tests/*.play.  Each file is played by sudplay.py in
a fresh game on a scratch copy of the world and database, in turns with a
fixed seed (42), as a DEV=1 game so that ASSERT answers.  A file holds one
command a line, and these:

    #seed N              the dice's seed (default 42)
    #realtime            real time instead of turns
    #builder             builder mode instead of a new game (an Archon)
    #restore SLOT        restore this save (from #save in an earlier file's run
                         is not kept: saves are the run's own)
    #new                 a new game (the default)
    #char NAME,RACE,CLASS[,GENDER[,ALIGNMENT]]
                         the character a new game makes (default
                         Wanderer,Human,Apprentice,M,good)
    #expect TEXT         TEXT is in what the step's commands printed
    #reject TEXT         TEXT is not
    #assert WHAT         ASSERT WHAT is ok: HERE room, HAS item,
                         VAR host name op value, QUEST name STEP n
    #wait N              N ticks pass (WAIT N)
    #save SLOT           SAVE SLOT
    # anything else      a comment

A step is a run of commands up to the next #expect, #reject or #assert.
Prints PASS or FAIL for each file, with each failing step and its line, and
exits 1 if any failed.  KEEP=1 keeps the scratch folders.
"""
import os
import shutil
import subprocess
import sys
import tempfile

ROOT = os.path.abspath(os.path.join(os.path.dirname(os.path.abspath(__file__)), '..'))
GAME = os.path.join(ROOT, 'CoffeeMud')


class Play:
    def __init__(self, path):
        self.path = path
        self.seed = '42'
        self.turns = True
        self.builder = False
        self.restore = None
        self.char = ['Wanderer', 'Human', 'Apprentice', 'M', 'good']
        self.lines = []      # (kind, text, line number)

    def read(self):
        n = 0
        for raw in open(self.path, encoding='utf-8').read().split('\n'):
            n += 1
            line = raw.rstrip('\r')
            s = line.strip()
            if not s:
                continue
            if s.startswith('#'):
                word, _, rest = s[1:].partition(' ')
                word = word.lower()
                rest = rest.strip()
                if word == 'seed':
                    self.seed = rest
                elif word == 'realtime':
                    self.turns = False
                elif word == 'builder':
                    self.builder = True
                elif word == 'restore':
                    self.restore = rest
                elif word == 'new':
                    self.restore = None
                elif word == 'char':
                    parts = [p.strip() for p in rest.split(',')]
                    for i, p in enumerate(parts[:5]):
                        if p:
                            self.char[i] = p
                elif word in ('expect', 'reject', 'assert'):
                    self.lines.append((word, rest, n))
                elif word == 'wait':
                    self.lines.append(('cmd', 'wait ' + rest, n))
                elif word == 'save':
                    self.lines.append(('cmd', 'save ' + rest, n))
                # any other # line is a comment
                continue
            self.lines.append(('cmd', s, n))

    def script(self):
        """The sudplay script, a mark before each check; and the checks, by mark."""
        out = []
        if self.restore is None:
            name, race, cls, gender, align = self.char
            if self.builder:
                out += [name, 'Y', 'N', race, 'Y', gender, 'N', cls, 'Y', align, '']
            else:
                out += [name, 'Y', 'N', race, 'Y', gender, 'N', cls, 'Y', align, '']
        checks = []   # (mark, kind, text, line)
        mark = 0
        out.append('assert mark 0')
        out.append('#expect PLAYSTEP 0')
        pending = False
        for kind, text, n in self.lines:
            if kind == 'cmd':
                out.append(text)
                pending = True
                continue
            if pending:
                mark += 1
                out.append('assert mark %d' % mark)
                out.append('#expect PLAYSTEP %d' % mark)
                pending = False
            if kind == 'assert':
                out.append('assert ' + text)
                mark += 1
                out.append('assert mark %d' % mark)
                out.append('#expect PLAYSTEP %d' % mark)
            checks.append((mark, kind, text, n))
        out.append('quit')
        out.append('Y' if self.builder else 'n')
        return out, checks


def segments(transcript):
    """The output between marks: segment k is what came after PLAYSTEP k-1 up to PLAYSTEP k."""
    segs = {}
    cur = []
    prev = None
    for line in transcript.split('\n'):
        stripped = line.strip()
        if 'PLAYSTEP ' in stripped:
            num = stripped.split('PLAYSTEP ', 1)[1].split()[0]
            if num.isdigit():
                if prev is not None:
                    segs[int(num)] = '\n'.join(cur)
                prev = int(num)
                cur = []
                continue
        cur.append(line)
    return segs


def run(path):
    play = Play(path)
    play.read()
    script, checks = play.script()
    work = tempfile.mkdtemp(prefix='sudplaytest.')
    try:
        os.makedirs(os.path.join(work, 'db'))
        shutil.copy(os.path.join(GAME, 'resources', 'fakedb', 'fakedb.schema'), os.path.join(work, 'db'))
        shutil.copytree(os.environ.get('WORLD', os.path.join(GAME, 'world')), os.path.join(work, 'world'))
        sfile = os.path.join(work, 'play.script')
        with open(sfile, 'w', encoding='utf-8') as f:
            f.write('\n'.join(script) + '\n')
        args = [sys.executable, os.path.join(ROOT, 'tools', 'sudplay.py'), sfile,
                'DBSERVICE=jdbc:fakedb:' + os.path.join(work, 'db'),
                'WORLDDIR=' + os.path.join(work, 'world'),
                'SAVEDIR=' + os.path.join(work, 'saves'),
                'RANDOMSEED=' + play.seed,
                'TIMEMODE=' + ('TURNS' if play.turns else 'REALTIME')]
        if play.builder:
            args.append('BUILDER=1')
        else:
            args.append('DEV=1')
            args.append('RESTORE=' + (play.restore if play.restore else 'NEW'))
        res = subprocess.run(args, stdout=subprocess.PIPE, stderr=subprocess.STDOUT)
        transcript = res.stdout.decode('utf-8', 'replace')
        with open(os.path.join(work, 'transcript.txt'), 'w', encoding='utf-8') as f:
            f.write(transcript)
        segs = segments(transcript)
        fails = []
        for mark, kind, text, n in checks:
            seg = segs.get(mark)
            if seg is None:
                fails.append('line %d: the play did not reach this step' % n)
                continue
            if kind == 'expect' and text not in seg:
                fails.append("line %d: expected '%s'" % (n, text))
            elif kind == 'reject' and text in seg:
                fails.append("line %d: '%s' should not show" % (n, text))
            elif kind == 'assert':
                if 'ASSERT ok: ' not in seg:
                    why = [l[l.index('ASSERT FAIL'):].strip() for l in seg.split('\n') if 'ASSERT FAIL' in l]
                    fails.append('line %d: %s' % (n, why[0] if why else 'ASSERT ' + text + ': no answer'))
        name = os.path.relpath(path)
        if fails:
            print('FAIL  %s' % name)
            for f in fails:
                print('        ' + f)
            print('        (transcript: %s)' % os.path.join(work, 'transcript.txt'))
        else:
            print('PASS  %s (%d checks)' % (name, len(checks)))
        return not fails, work
    finally:
        pass


def main():
    files = sys.argv[1:]
    if not files:
        tests = os.path.join(GAME, 'world', 'tests')
        if os.path.isdir(tests):
            files = sorted(os.path.join(tests, f) for f in os.listdir(tests) if f.endswith('.play'))
    if not files:
        print('No playtest files (world/tests/*.play).')
        return 0
    ok = True
    for path in files:
        passed, work = run(path)
        ok = ok and passed
        if passed and os.environ.get('KEEP') != '1':
            shutil.rmtree(work, ignore_errors=True)
    return 0 if ok else 1


if __name__ == '__main__':
    sys.exit(main())
