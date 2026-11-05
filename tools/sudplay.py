#!/usr/bin/env python3
"""Play the single-player game from a script of input lines.

    sudplay.py SCRIPTFILE [NAME=VALUE ...]

Starts CoffeeMud/sud.sh (or $SUD_GAME_DIR/sud.sh) with the given ini
overrides and types the script one
line at a time.  Each line is sent only after the game has answered the
previous one and gone quiet, the way a person waits to read before typing;
some questions are asked asynchronously, and an answer sent too early is
read as a command.  A script line "#expect TEXT" instead waits (up to
EXPECT_LIMIT seconds) until TEXT has appeared in the output since the last
line was sent, for commands that run longer than a pause, and "#sleep N"
waits N seconds, to let game time pass.  The transcript
goes to standard output.  Exits with the game's exit code, or 124 if it does
not finish in time.
"""
import os
import json
import subprocess
import sys
import threading
import time

QUIET = 0.8        # seconds of silence that mean "the game is waiting"
REPLY_LIMIT = 30   # most seconds to wait for an answer to one line
BOOT_LIMIT = 120   # most seconds to wait for the first prompt
EXPECT_LIMIT = int(os.environ.get('SUDPLAY_EXPECT_LIMIT', '1800'))
TOTAL_LIMIT = int(os.environ.get('SUDPLAY_LIMIT', '300'))
EXIT_WAIT = 60     # most seconds to wait for the game to end after the script


def main():
    script = open(sys.argv[1]).read().split('\n')
    if script and script[-1] == '':
        script.pop()
    game_dir = os.environ.get('SUD_GAME_DIR') or os.path.join(os.path.dirname(os.path.abspath(__file__)), '..', 'CoffeeMud')
    game = os.path.join(game_dir, 'sud.sh')
    command = json.loads(os.environ['SUDPLAY_COMMAND']) if 'SUDPLAY_COMMAND' in os.environ else [game]
    proc = subprocess.Popen(command + sys.argv[2:], stdin=subprocess.PIPE,
                            stdout=subprocess.PIPE, stderr=subprocess.STDOUT)
    lock = threading.Lock()
    state = {'last': time.time(), 'count': 0, 'text': bytearray()}

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
                state['text'] += b

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
    sent_at = 0
    for line in script:
        if proc.poll() is not None:
            break
        if line.startswith('#sleep '):
            time.sleep(float(line.split()[1]))
            continue
        if line.startswith('#expect '):
            want = line[len('#expect '):].encode()
            end = time.time() + EXPECT_LIMIT
            while time.time() < end and proc.poll() is None:
                with lock:
                    if want in state['text'][sent_at:]:
                        break
                time.sleep(0.5)
            wait_for_reply(state['count'], QUIET)
            continue
        with lock:
            before = state['count']
            sent_at = len(state['text'])
        try:
            proc.stdin.write((line + '\n').encode())
            proc.stdin.flush()
        except BrokenPipeError:
            break
        wait_for_reply(before, REPLY_LIMIT)
    # a script that quits lets the game finish what it is doing, such as a save
    # after the last answer, before its input ends
    end = time.time() + EXIT_WAIT
    while time.time() < end and proc.poll() is None:
        time.sleep(0.1)
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
