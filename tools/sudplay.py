#!/usr/bin/env python3
"""Play the single-player game from a script of input lines.

    sudplay.py SCRIPTFILE [NAME=VALUE ...]

Starts CoffeeMud/sud.sh with the given ini overrides and types the script one
line at a time.  Each line is sent only after the game has answered the
previous one and gone quiet, the way a person waits to read before typing;
some questions are asked asynchronously, and an answer sent too early is
read as a command.  The transcript goes to standard output.  Exits with the
game's exit code, or 124 if it does not finish in time.
"""
import os
import subprocess
import sys
import threading
import time

QUIET = 0.8        # seconds of silence that mean "the game is waiting"
REPLY_LIMIT = 30   # most seconds to wait for an answer to one line
BOOT_LIMIT = 120   # most seconds to wait for the first prompt
TOTAL_LIMIT = 300


def main():
    script = open(sys.argv[1]).read().split('\n')
    if script and script[-1] == '':
        script.pop()
    game = os.path.join(os.path.dirname(os.path.abspath(__file__)), '..', 'CoffeeMud', 'sud.sh')
    proc = subprocess.Popen([game] + sys.argv[2:], stdin=subprocess.PIPE,
                            stdout=subprocess.PIPE, stderr=subprocess.STDOUT)
    lock = threading.Lock()
    state = {'last': time.time(), 'count': 0}

    def pump():
        while True:
            b = proc.stdout.read1(65536)
            if not b:
                break
            sys.stdout.buffer.write(b)
            sys.stdout.buffer.flush()
            with lock:
                state['last'] = time.time()
                state['count'] += len(b)

    reader = threading.Thread(target=pump, daemon=True)
    reader.start()
    start = time.time()

    def wait_for_reply(since_count, limit):
        end = time.time() + limit
        while time.time() < end and proc.poll() is None:
            with lock:
                got = state['count'] > since_count
                quiet = time.time() - state['last'] >= QUIET
            if got and quiet:
                return
            time.sleep(0.1)

    wait_for_reply(0, BOOT_LIMIT)
    for line in script:
        if proc.poll() is not None:
            break
        with lock:
            before = state['count']
        try:
            proc.stdin.write((line + '\n').encode())
            proc.stdin.flush()
        except BrokenPipeError:
            break
        wait_for_reply(before, REPLY_LIMIT)
    try:
        proc.stdin.close()  # end of input logs the player out if the script did not quit
    except BrokenPipeError:
        pass
    try:
        proc.wait(timeout=max(1, TOTAL_LIMIT - (time.time() - start)))
    except subprocess.TimeoutExpired:
        proc.kill()
        proc.wait()
        reader.join(2)
        sys.exit(124)
    reader.join(2)
    sys.exit(proc.returncode)


if __name__ == '__main__':
    main()
