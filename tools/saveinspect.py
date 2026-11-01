#!/usr/bin/env python3
"""Print a saved game, and change a few things in it, outside the game.

    tools/saveinspect.py SAVE                     what the save holds
    tools/saveinspect.py SAVE --var HOST NAME VALUE   set a script variable
    tools/saveinspect.py SAVE --hp 40             set the character's hit points
    tools/saveinspect.py SAVE --room "Area#12"    move the character to a room

SAVE is the save's folder, such as CoffeeMud/saves/hero.  A change writes
world.xml again, and keeps the old one as world.xml.bak.  The game must not
be running on that save.  world.xml is CoffeeSUD's own XML, read here with
patterns rather than an XML parser, since its text is escaped only for < and >.
"""
import os
import re
import sys


def unesc(s):
    return s.replace("&lt;", "<").replace("&gt;", ">")


def esc(s):
    return s.replace("<", "&lt;").replace(">", "&gt;")


def tag(text, name):
    m = re.search(r"<%s>(.*?)</%s>" % (name, name), text, re.S)
    return unesc(m.group(1)) if m else ""


def player_block(world):
    m = re.search(r"<PLAYER[^>]*>.*?</PLAYER>", world, re.S)
    return m


def show(folder):
    header = os.path.join(folder, "header.properties")
    if not os.path.isfile(header):
        sys.exit("No saved game in " + folder)
    print("Header:")
    with open(header, encoding="utf-8", errors="replace") as f:
        for line in f:
            line = line.rstrip("\n")
            if line and not line.startswith("#"):
                print("  " + line)
    path = os.path.join(folder, "world.xml")
    if not os.path.isfile(path):
        print("No world.xml.")
        return
    world = open(path, encoding="utf-8", errors="replace").read()
    m = player_block(world)
    if m:
        p = m.group(0)
        head = p.split("<INVENTORY>")[0]
        print("Character: %s in %s, hit points %s, mana %s, movement %s" % (
            tag(head, "NAME"), tag(head, "ROOM"), tag(head, "HP"), tag(head, "MANA"), tag(head, "MOVE")))
    party = re.search(r"<PARTY>(.*?)</PARTY>", world, re.S)
    if party:
        for pm in re.finditer(r"<PLAYER[^>]*>(.*?)</PLAYER>", party.group(1), re.S):
            head = pm.group(1).split("<INVENTORY>")[0]
            print("Party: %s in %s" % (tag(head, "NAME"), tag(head, "ROOM")))
    quests = re.search(r"<QUESTS>(.*?)</QUESTS>", world, re.S)
    if quests:
        for q in re.finditer(r"<QUEST[^>]*>(.*?)</QUEST>", quests.group(1), re.S):
            body = q.group(1)
            running = tag(body, "RUNNING") == "true"
            print("Quest: %s, %s" % (tag(body, "NAME"),
                  ("running, step " + tag(body, "STEP")) if running else "not running"))
    sv = re.search(r"<SCRIPTVARS>(.*?)</SCRIPTVARS>", world, re.S)
    if sv:
        for v in re.finditer(r"<VAR>(.*?)</VAR>", sv.group(1), re.S):
            body = v.group(1)
            print("Variable: %s %s = %s" % (tag(body, "HOST"), tag(body, "NAME"), tag(body, "VALUE")))


def write(folder, world):
    path = os.path.join(folder, "world.xml")
    os.replace(path, path + ".bak")
    with open(path, "w", encoding="utf-8") as f:
        f.write(world)


def set_player_field(folder, name, value):
    path = os.path.join(folder, "world.xml")
    world = open(path, encoding="utf-8", errors="replace").read()
    m = player_block(world)
    if not m:
        sys.exit("The save has no character in world.xml.")
    p = m.group(0)
    head, sep, rest = p.partition("<INVENTORY>")
    new_head, n = re.subn(r"<%s>.*?</%s>" % (name, name), "<%s>%s</%s>" % (name, esc(value), name), head, count=1, flags=re.S)
    if n == 0:
        sys.exit("The character has no " + name + " in world.xml.")
    write(folder, world[:m.start()] + new_head + sep + rest + world[m.end():])
    print("Set the character's %s to %s." % (name, value))


def set_var(folder, host, name, value):
    path = os.path.join(folder, "world.xml")
    world = open(path, encoding="utf-8", errors="replace").read()
    sv = re.search(r"<SCRIPTVARS>(.*?)</SCRIPTVARS>", world, re.S)
    if not sv:
        sys.exit("The save has no SCRIPTVARS in world.xml.")
    body = sv.group(1)
    for v in re.finditer(r"<VAR>(.*?)</VAR>", body, re.S):
        if tag(v.group(1), "HOST") == host and tag(v.group(1), "NAME") == name:
            new_var = re.sub(r"<VALUE>.*?</VALUE>", "<VALUE>" + esc(value) + "</VALUE>", v.group(0), count=1, flags=re.S)
            body = body[:v.start()] + new_var + body[v.end():]
            break
    else:
        body += "<VAR><SCOPE></SCOPE><HOST>%s</HOST><NAME>%s</NAME><VALUE>%s</VALUE></VAR>\n" % (esc(host), esc(name), esc(value))
    write(folder, world[:sv.start(1)] + body + world[sv.end(1):])
    print("Set %s %s to %s." % (host, name, value))


def main(argv):
    if len(argv) < 2 or argv[1] in ("-h", "--help"):
        print(__doc__)
        return 0
    folder = argv[1]
    args = argv[2:]
    if not args:
        show(folder)
    elif args[0] == "--var" and len(args) == 4:
        set_var(folder, args[1], args[2], args[3])
    elif args[0] == "--hp" and len(args) == 2:
        set_player_field(folder, "HP", args[1])
    elif args[0] == "--room" and len(args) == 2:
        set_player_field(folder, "ROOM", args[1])
    else:
        print(__doc__)
        return 1
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv))
