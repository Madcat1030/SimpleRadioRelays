from pathlib import Path
import os,subprocess,sys,time
from urllib.parse import urlsplit
root=Path(__file__).resolve().parent
env=os.environ.copy()
env['JAVA_HOME']=str(root/'jdk')
env['GRADLE_USER_HOME']=str(root/'gradle-cache')
env['GRADLE_OPTS']='';env['JAVA_OPTS']=''
options=['-XX:ActiveProcessorCount=4','-Djavax.net.ssl.trustStore=/etc/ssl/certs/java/cacerts',
         '--add-exports=java.base/jdk.internal.org.objectweb.asm=ALL-UNNAMED',
         '-javaagent:'+str(root/'capture-host-agent.jar')]
proxy=urlsplit(env.get('HTTPS_PROXY',''))
if proxy.hostname:
 for scheme in ['http','https']:
  options += [f'-D{scheme}.proxyHost={proxy.hostname}',f'-D{scheme}.proxyPort={proxy.port}']
env['JAVA_TOOL_OPTIONS']=' '.join(options)
project=Path(env.get('RADIO_PROJECT_DIR',root.parent/'recovered/source/simple-radio-addons'))
code=subprocess.call(['bash',str(root/'gradle-9.5.0/bin/gradle'),'-Dorg.gradle.jvmargs=-Xmx3G -XX:ActiveProcessorCount=4',
                      *sys.argv[1:],'--no-daemon','--console=plain','--max-workers=4'],cwd=project,env=env)
# Give the single-use daemon time to release Loom locks before the task namespace closes.
time.sleep(1)
raise SystemExit(code)
