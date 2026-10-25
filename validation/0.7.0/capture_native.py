from pathlib import Path
import os,socket,subprocess,time
project=Path(__file__).resolve().parents[2]
tools=Path(os.environ['RADIO_CAPTURE_TOOLS'])
env=os.environ.copy()
env.update(DISPLAY='127.0.0.1:96',LIBGL_ALWAYS_SOFTWARE='1',GALLIUM_DRIVER='llvmpipe',LP_NUM_THREADS='4',ALSOFT_DRIVERS='null',LD_LIBRARY_PATH=str(tools/'xvfb/usr/lib/x86_64-linux-gnu'),RADIO_PROJECT_DIR=str(project))
here=Path(__file__).resolve().parent
with (here/'xvfb.log').open('w') as displaylog,(here/'native-client.log').open('w') as clientlog:
    display=subprocess.Popen([str(tools/'xvfb/usr/bin/Xvfb'),':96','-screen','0','1600x1000x24','-ac','-nolisten','unix','-listen','tcp'],env=env,stdout=displaylog,stderr=subprocess.STDOUT)
    client=None
    try:
        for attempt in range(50):
            if display.poll() is not None:raise RuntimeError('Native display exited')
            try:
                with socket.create_connection(('127.0.0.1',6096),.2):break
            except OSError:time.sleep(.2)
        else:raise RuntimeError('Native display not ready')
        client=subprocess.Popen(['python3',str(tools/'run_gradle.py'),':core:runClient','-I',str(here/'client-preview.gradle')],env=env,cwd=project,stdout=clientlog,stderr=subprocess.STDOUT)
        while True:
            try:
                code=client.wait(timeout=20);break
            except subprocess.TimeoutExpired:print('Native Minecraft capture running...',flush=True)
        if code:raise RuntimeError('Native capture failed; inspect native-client.log')
    finally:
        if client is not None and client.poll() is None:client.terminate()
        display.terminate();display.wait(timeout=10)
        (project/'core/run/mods/relay-monitor-native-preview.jar').unlink(missing_ok=True)
print('Native capture complete',flush=True)
