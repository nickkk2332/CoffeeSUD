#!/usr/bin/env python3
"""Drive a CoffeeMud telnet server with prompt rules, then run a command script.

Used to record a baseline of the stock multiplayer server before the
single-player conversion. Usage:

    mudbot.py PORT NAME [command[@@quiet-seconds] ...]

Prompts during login and character creation are answered by RULES (first
regex that matches the tail of the output wins). Once the player is in the
game, each command is sent and its output printed.
"""
import os
import re
import socket
import sys
import time

IAC, DONT, DO, WONT, WILL, SB, SE = 255, 254, 253, 252, 251, 250, 240
ANSI = re.compile(r'\x1b\[[0-9;?]*[A-Za-z]')


class Conn:
    def __init__(self, port):
        self.sock = socket.create_connection(('127.0.0.1', port))
        self.sock.settimeout(0.2)

    def _strip(self, buf):
        out = bytearray()
        i = 0
        while i < len(buf):
            b = buf[i]
            if b == IAC and i + 1 < len(buf):
                cmd = buf[i + 1]
                if cmd in (DO, DONT, WILL, WONT) and i + 2 < len(buf):
                    if cmd == DO:
                        self.sock.send(bytes([IAC, WONT, buf[i + 2]]))
                    elif cmd == WILL:
                        self.sock.send(bytes([IAC, DONT, buf[i + 2]]))
                    i += 3
                    continue
                if cmd == SB:
                    j = buf.find(bytes([IAC, SE]), i)
                    i = len(buf) if j < 0 else j + 2
                    continue
                i += 2
                continue
            out.append(b)
            i += 1
        return ANSI.sub('', out.decode('latin-1')).replace('\r', '')

    def read(self, quiet=0.6, limit=10.0):
        """Read until the server has been quiet for `quiet` seconds."""
        data = bytearray()
        start = last = time.time()
        while time.time() - start < limit:
            try:
                d = self.sock.recv(65536)
                if not d:
                    break
                data += d
                last = time.time()
            except socket.timeout:
                if data and time.time() - last >= quiet:
                    break
        return self._strip(data)

    def send(self, line):
        self.sock.send((line + '\r\n').encode('latin-1'))


def rules(name, password):
    return [
        (r're-roll \(y/N\)\?\s*$', 'N'),
        (r'races \(\?\):.*:\s*$', 'Human'),
        (r'following [Cc]lasses:\s*\n(\[[^\n]*\]\s*\n)?:\s*$', 'Fighter'),
        (r'Select one: ([A-Za-z]+)[^\n]*\n:\s*$', r'\1'),
        (r'gender \(.*\)\?\s*$', 'M'),
        (r'select from the following:\s*([A-Za-z]+)[^\n]*\n:\s*$', r'\1'),
        (r'ENTER to complete\.\s*\n:\s*$', ''),
        (r'or R for random roll\.\s*\n:\s*$', 'R'),
        (r'Account name:\s*$', name),
        (r'Command or Name \(\?\):\s*$', 'N'),
        (r'[Cc]haracter name.*:\s*$', name),
        (r'[Nn]ame (for|of) (your|the) (new )?character.*:\s*$', name),
        (r'name you would like to create \(y/N\)\?\s*$', 'Y'),
        (r'new account you would like to create \(y/N\)\?\s*$', 'Y'),
        (r'ANSI colors \(Y/n\)\?\s*$', 'N'),
        (r'(account )?password\s*\n?:\s*$', password),
        (r'(Enter|Re-?enter|Confirm).*password.*:\s*$', password),
        (r'e-?mail.*:\s*$', ''),
        (r'\(Y/N\)\s*:?\s*$', 'Y'),
        (r'\(y/N\)\s*\?\s*$', 'Y'),
        (r'\(Y/n\)\s*\?\s*$', 'Y'),
        (r'[Pp]ress [Ee]nter.*$', ''),
        (r'<cr>.*$', ''),
    ]


def main():
    port = int(sys.argv[1])
    name = sys.argv[2]
    script = sys.argv[3:]
    password = os.environ.get('MUDBOT_PASSWORD', 'smoketest1')
    extra = [(re.compile(p, re.S), a) for p, a in
             (tuple(r.split('=>', 1)) for r in os.environ.get('MUDBOT_RULES', '').split('||') if '=>' in r)]
    table = extra + [(re.compile(p, re.S), a) for p, a in rules(name, password)]
    c = Conn(port)
    out = c.read(quiet=1.5)
    print(out, end='')
    c.send('')
    for _ in range(80):
        out = c.read()
        print(out, end='')
        tail = out[-300:]
        if re.search(r'^\s*<[^>\n]*>\s*$', tail.split('\n')[-1]) or re.search(r'\d+Hp.*>\s*$', tail):
            break  # an in-game prompt
        for pat, ans in table:
            m = pat.search(tail)
            if m:
                ans = m.expand(ans) if '\\' in ans else ans
                print('[bot] ' + ans)
                c.send(ans)
                break
        else:
            if not out:
                print('[bot] <enter>')
                c.send('')
    else:
        print('\n[bot] never reached the game prompt')
        sys.exit(2)
    for cmd in script:
        quiet = float(os.environ.get('MUDBOT_QUIET', '1.0'))
        if '@@' in cmd:  # "command@@SECONDS" waits longer for slow output
            cmd, q = cmd.rsplit('@@', 1)
            quiet = float(q)
        print('\n[cmd] ' + cmd)
        c.send(cmd)
        print(c.read(quiet=quiet, limit=float(os.environ.get('MUDBOT_LIMIT', '120'))), end='')
    c.send('quit')
    c.read()
    c.send('Y')


if __name__ == '__main__':
    main()
