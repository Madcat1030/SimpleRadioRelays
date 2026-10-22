"""Run the actual Minecraft book UI under a disposable display; helpers are never bundled."""
from pathlib import Path
import os,socket,subprocess,time

HERE=Path(__file__).resolve().parent
PROJECT=HERE.parents[1]
ROOT=PROJECT.parents[2]
TOOLCHAIN=ROOT/'toolchain'
env=os.environ.copy()
env.update(DISPLAY='127.0.0.1:96',LIBGL_ALWAYS_SOFTWARE='1',GALLIUM_DRIVER='llvmpipe',
           LP_NUM_THREADS='4',ALSOFT_DRIVERS='null',LD_LIBRARY_PATH=str(TOOLCHAIN/'xvfb/usr/lib/x86_64-linux-gnu'))
screenshots=PROJECT/'directional/run/screenshots'
for path in screenshots.glob('Relays-for-Dummies-*.png'):path.unlink()
with (HERE/'xvfb.log').open('w') as display_log,(HERE/'native-client.log').open('w') as client_log:
    display=subprocess.Popen([str(TOOLCHAIN/'xvfb/usr/bin/Xvfb'),':96','-screen','0','1000x800x24','-ac','-nolisten','unix','-listen','tcp'],env=env,stdout=display_log,stderr=subprocess.STDOUT)
    client=None
    try:
        for attempt in range(50):
            if display.poll() is not None:raise RuntimeError('Display stopped; inspect xvfb.log')
            try:
                with socket.create_connection(('127.0.0.1',6096),.2):break
            except OSError:time.sleep(.2)
        else:raise RuntimeError('Display did not become ready')
        client=subprocess.Popen(['python3',str(TOOLCHAIN/'run_gradle.py'),':directional:runClient','-PwithFoci','-I',str(HERE/'handbook-preview.gradle')],cwd=PROJECT,env=env,stdout=client_log,stderr=subprocess.STDOUT)
        for interval in range(60):
            try:code=client.wait(timeout=10);break
            except subprocess.TimeoutExpired:print('Minecraft handbook capture running...',flush=True)
        else:raise RuntimeError('Capture timed out; inspect native-client.log')
        if code:raise RuntimeError('Minecraft client failed; inspect native-client.log')
        print('Minecraft handbook capture finished.',flush=True)
    finally:
        if client is not None and client.poll() is None:client.terminate()
        if display.poll() is None:display.terminate();display.wait(timeout=10)
        (PROJECT/'directional/run/mods/handbook-native-preview.jar').unlink(missing_ok=True)
