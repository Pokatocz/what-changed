#!/usr/bin/env python3
"""dragtest.py <combo> : the draggable scrollbar (1.3.0) with the real mouse, on a copy of the combo's tested client.
300 items are taken out of the pack snapshot so the Items tab has a long list. Window 1080x1920, GUI scale 3.
Opens What Changed? from the title button, Items tab, drags the thumb, checks it stays after release, clicks the
track, scrolls with the wheel. Prints the thumb position after each step; screenshots in logs/<combo>/drag/."""
import json
import os
import shutil
import signal
import subprocess
import sys
import time

import numpy as np
from PIL import Image

C = sys.argv[1]
SPEC = {'fab120': 'fabric:1.20.1:0.20.1', 'fab263': 'fabric:26.3:0.20.1', 'forge120': '1.20.1-forge-47.4.28',
        'forge263': '26.3-forge-66.1.9', 'neo1202': 'neoforge::20.2.93', 'neo263': 'neoforge::26.3.0.115',
        'fab1218': 'fabric:1.21.8:0.20.1', 'forge1211': '1.21.1-forge-52.1.20', 'neo1201': '1.20.1-forge-47.1.106'}[C]
T = os.path.expanduser('~/.pokato-tools')
JAVA = T + ('/jdk-17/bin/java' if C in ('fab120', 'forge120', 'neo1202', 'neo1201') else '/jdk-21/bin/java' if C in ('fab1218', 'forge1211') else '/jdk-25/bin/java')
WCT = os.environ.get('WCT', os.path.expanduser('~/wct'))
MCMAIN = os.environ.get('MCMAIN', os.path.expanduser('~/mctest/main'))
SRC = f'{WCT}/run/{C}/client'
D = f'{WCT}/run/{C}-drag'
OUT = f'{WCT}/logs/{C}/drag'
shutil.rmtree(D, ignore_errors=True)
shutil.copytree(SRC, D, ignore=shutil.ignore_patterns('wctest-out', 'logs', 'crash-reports', 'screenshots'))
os.makedirs(OUT, exist_ok=True)
snap = f'{D}/config/whatchanged/pack-snapshot.json'
s = json.load(open(snap))
for k in sorted(s['items'])[:300]:
    del s['items'][k]
json.dump(s, open(snap, 'w'))
o = {}
for line in open(f'{D}/options.txt'):
    if ':' in line:
        k, v = line.rstrip('\n').split(':', 1)
        o[k] = v
o.update(guiScale='3', menuBackgroundBlurriness='0')
open(f'{D}/options.txt', 'w').write(''.join(f'{k}:{v}\n' for k, v in o.items()))

DISP = ':77'
ENV = dict(os.environ, DISPLAY=DISP, LIBGL_ALWAYS_SOFTWARE='1', SDL_VIDEO_FORCE_EGL='1', SDL_VIDEO_X11_FORCE_EGL='1',
           XDG_RUNTIME_DIR='/tmp/xdg-runtime')
ENV.pop('WCTEST_MODE', None)
xv = subprocess.Popen(['Xvfb', DISP, '-screen', '0', '1080x1920x24', '+extension', 'GLX', '+render', '-noreset'],
                      stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL, start_new_session=True)
time.sleep(2)


def xdo(*a):
    subprocess.run(['xdotool', *map(str, a)], env=ENV)


def shot(name):
    p = f'{OUT}/{name}.png'
    subprocess.run(['import', '-display', DISP, '-window', 'root', p], capture_output=True)
    return Image.open(p).convert('RGB')


def thumb(im):
    a = np.asarray(im).astype(int)[258:1818, 1026:1034]
    light = (a.min(axis=2) > 180).sum(1) >= 4
    ys = np.where(light)[0]
    return (int(ys[0]) + 258, int(ys[-1]) + 258) if len(ys) else None


xdo('mousemove', 1075, 1915)
game = subprocess.Popen(['portablemc', '--main-dir', MCMAIN, 'start', SPEC, '--mc-dir', D, '--jvm', JAVA,
                         '--resolution', '1080x1920', '-u', 'WCTester'], env=ENV, stdout=open(f'{OUT}/game.out', 'w'),
                        stderr=subprocess.STDOUT, start_new_session=True)
LOG = f'{D}/logs/latest.log'
t0 = time.time()
while 'Modpack check done' not in (open(LOG, errors='replace').read() if os.path.exists(LOG) else ''):
    if time.time() - t0 > 900 or game.poll() is not None:
        print('game did not start')
        sys.exit(1)
    time.sleep(2)
print([l.split('[What Changed?]')[-1].strip() for l in open(LOG, errors='replace') if 'Modpack check done' in l][0])
t1 = time.time()
while time.time() - t1 < 300:     # title screen: our gray button top-left instead of the loading screen
    im = shot('probe').crop((30, 20, 530, 60))
    r, g, b = [sum(c) / len(c) for c in zip(*im.getdata())]
    if abs(r - g) < 25 and abs(g - b) < 25 and 50 < r < 200:
        break
    time.sleep(2)
time.sleep(8)


def stop():
    for d in os.listdir('/proc'):
        if d.isdigit() and int(d) != os.getpid():
            try:
                cmd = open(f'/proc/{d}/cmdline', 'rb').read().split(b'\0')
            except OSError:
                continue
            if cmd and cmd[0].endswith(b'java') and any(D.encode() in c for c in cmd):
                os.kill(int(d), signal.SIGTERM)
    time.sleep(3)
    os.killpg(game.pid, signal.SIGTERM) if game.poll() is None else None
    xv.terminate()


res = {}
shot('0_title')
xdo('mousemove', 282, 40); time.sleep(0.5); xdo('click', 1); time.sleep(4)
xdo('mousemove', 334, 136); time.sleep(0.5); xdo('click', 1); time.sleep(3)
xdo('mousemove', 1062, 1000); time.sleep(2)
res['start'] = thumb(shot('1_items_top'))
if not res['start']:
    print('NO SCROLLBAR'); stop(); sys.exit(2)
cy = (res['start'][0] + res['start'][1]) // 2
xdo('mousemove', 1030, cy); time.sleep(1.0)
xdo('mousedown', 1); time.sleep(0.8)
for k in range(1, 21):
    xdo('mousemove', 1030, cy + k * 30); time.sleep(0.25)
time.sleep(1.5)
res['held'] = thumb(shot('2_dragging_held'))
xdo('mouseup', 1); time.sleep(1.5)
xdo('mousemove', 1062, 1000); time.sleep(1.5)
res['released'] = thumb(shot('3_after_release'))
xdo('mousemove', 1030, 400); time.sleep(1.5)
res['moved_no_button'] = thumb(shot('4_moved_after_release'))
xdo('mousemove', 1030, 1760); time.sleep(0.8)
xdo('mousedown', 1); time.sleep(0.8); xdo('mouseup', 1); time.sleep(1.5)
xdo('mousemove', 1062, 1000); time.sleep(1.5)
res['track_click'] = thumb(shot('5_track_click'))
xdo('mousemove', 600, 1000); time.sleep(0.5)
for _ in range(10):
    xdo('click', 4); time.sleep(0.25)
time.sleep(1.5)
res['wheel_up'] = thumb(shot('6_wheel_up'))
errs = [l.strip()[:200] for l in open(LOG, errors='replace') if ('Exception' in l or 'ERROR' in l) and 'whatchanged' in l.lower()]
stop()
s0, h, r, m, tc, w = (res[k] for k in ('start', 'held', 'released', 'moved_no_button', 'track_click', 'wheel_up'))
ok = (h and h[0] > s0[0] + 400 and r and abs(r[0] - h[0]) < 15 and m == r and tc and tc[1] > 1700 and w and w[0] < tc[0])
print('thumb', res)
print('wc errors:', errs[:3])
print('DRAG', 'OK' if ok else 'FAIL')
