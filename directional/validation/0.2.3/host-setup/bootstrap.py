from pathlib import Path
import urllib.request,hashlib,concurrent.futures,json,lzma,subprocess,tarfile,zipfile,os
ROOT=Path(__file__).resolve().parent
DOWNLOADS=ROOT/'downloads';DOWNLOADS.mkdir(exist_ok=True)
def fetch(url,path,expected=None,algorithm='sha256'):
 path=Path(path);path.parent.mkdir(parents=True,exist_ok=True)
 if path.exists() and (expected is None or hashlib.new(algorithm,path.read_bytes()).hexdigest()==expected):return path
 tmp=path.with_suffix(path.suffix+'.partial')
 with urllib.request.urlopen(url,timeout=60) as r,tmp.open('wb') as f:
  while chunk:=r.read(1024*1024):f.write(chunk)
 actual=hashlib.new(algorithm,tmp.read_bytes()).hexdigest()
 if expected and actual!=expected:raise RuntimeError('Checksum mismatch: '+path.name)
 tmp.rename(path)
 print('Fetched',path.name,path.stat().st_size,flush=True)
 return path

def gradle():
 url='https://downloads.gradle.org/distributions/gradle-9.5.0-bin.zip'
 expected=urllib.request.urlopen(url+'.sha256',timeout=30).read().decode().strip()
 f=fetch(url,DOWNLOADS/'gradle.zip',expected)
 if not (ROOT/'gradle-9.5.0/bin/gradle').exists():
  with zipfile.ZipFile(f) as z:z.extractall(ROOT)
 return 'Gradle ready'

def java():
 f=fetch('https://cdn.azul.com/zulu/bin/zulu21.44.17-ca-jdk21.0.8-linux_x64.tar.gz',DOWNLOADS/'jdk.tar.gz')
 if not (ROOT/'zulu21.44.17-ca-jdk21.0.8-linux_x64/bin/javac').exists():
  with tarfile.open(f) as t:t.extractall(ROOT,filter='data')
 link=ROOT/'jdk'
 if not link.exists():link.symlink_to(ROOT/'zulu21.44.17-ca-jdk21.0.8-linux_x64',target_is_directory=True)
 return 'JDK ready'

def display():
 f=fetch('https://deb.debian.org/debian/dists/bookworm/main/binary-amd64/Packages.xz',DOWNLOADS/'Packages.xz')
 wanted={'xvfb','xserver-common','x11-xkb-utils','libxfont2','libxkbfile1','libfontenc1','xkb-data'}
 selected=[]
 for paragraph in lzma.decompress(f.read_bytes()).decode().split('\n\n'):
  lines={line.split(': ',1)[0]:line.split(': ',1)[1] for line in paragraph.splitlines() if ': ' in line and not line.startswith(' ')}
  if lines.get('Package') in wanted:selected.append(lines)
 if len({x['Package'] for x in selected})!=len(wanted):raise RuntimeError('Missing display package')
 for item in selected:
  p=fetch('https://deb.debian.org/debian/'+item['Filename'],DOWNLOADS/Path(item['Filename']).name,item['SHA256'])
  subprocess.run(['dpkg-deb','-x',str(p),str(ROOT/'xvfb')],check=True)
 target=Path('/usr/bin/xkbcomp')
 if not target.exists():target.symlink_to(ROOT/'xvfb/usr/bin/xkbcomp')
 return 'Native display ready'

def fonts():
 manifest=json.load(urllib.request.urlopen('https://piston-meta.mojang.com/mc/game/version_manifest_v2.json',timeout=30))
 url=next(v['url'] for v in manifest['versions'] if v['id']=='1.20.1')
 data=json.load(urllib.request.urlopen(url,timeout=30))
 assets=ROOT/'gradle-cache/caches/fabric-loom/assets'
 index=fetch(data['assetIndex']['url'],assets/'indexes'/ (data['assetIndex']['id']+'.json'),data['assetIndex']['sha1'],'sha1')
 objects=json.loads(index.read_text())['objects']
 for name,item in objects.items():
  if '/font/' in name or name.startswith('minecraft/font'):
   h=item['hash'];fetch('https://resources.download.minecraft.net/'+h[:2]+'/'+h,assets/'objects'/h[:2]/h,h,'sha1')
 return 'Native font assets ready'
with concurrent.futures.ThreadPoolExecutor(max_workers=4) as pool:
 futures=[pool.submit(fn) for fn in [gradle,java,display,fonts]]
 for future in concurrent.futures.as_completed(futures):print(future.result(),flush=True)
print('Bootstrap complete',flush=True)
