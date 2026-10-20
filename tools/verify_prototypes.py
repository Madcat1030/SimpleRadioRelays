from pathlib import Path
from zipfile import ZipFile
import hashlib,json,struct
def inspect(data):
    at=0
    def read(fmt):
        nonlocal at
        v=struct.unpack_from('>'+fmt,data,at)[0];at+=struct.calcsize('>'+fmt);return v
    assert read('I')==0xcafebabe
    minor,major=read('H'),read('H');n=read('H'); cp=[None]*n; i=1
    while i<n:
        tag=read('B')
        if tag==1:
            size=read('H');cp[i]=data[at:at+size].decode('utf-8',errors='replace');at+=size
        elif tag in (3,4):at+=4
        elif tag in (5,6):at+=8;i+=1
        elif tag in (7,8,16,19,20):at+=2
        elif tag in (9,10,11,12,17,18):at+=4
        elif tag==15:at+=3
        else:raise ValueError(tag)
        i+=1
    pool=data[:at];at+=6;count=read('H');at+=count*2
    def attributes():
        nonlocal at
        result={}
        for _ in range(read('H')):
            name=cp[read('H')];size=read('I');result[name]=data[at:at+size];at+=size
        return result
    def members():
        nonlocal at
        result={}
        for _ in range(read('H')):
            start=at;flags=read('H');name=cp[read('H')];desc=cp[read('H')];attributes();result[name+desc]=data[start:at]
        return result
    fields=members();methods=members();attrs=attributes();assert at==len(data)
    return major,pool,fields,methods,attrs,cp

p=Path(__file__).resolve().parents[1]
results=[]
for module in ['core','foci']:
    folder=p/module
    originals=[f for f in (folder/'build/libs').glob('*.jar') if not any(t in f.name for t in ['prototype','sources'])]
    outputs=list((folder/'build/libs').glob('*-prototype.jar'))
    assert len(originals)==len(outputs)==1,(originals,outputs)
    original,output=originals[0],outputs[0]
    report=json.loads((folder/'build/prototype/packaging-report.json').read_text())
    removed=set(report['removed_test_only_methods']);checked=classes=0
    with ZipFile(original) as old,ZipFile(output) as new:
        assert new.testzip() is None
        assert set(old.namelist())-set(new.namelist())=={n for n in old.namelist() if n.startswith('dev/paul/simpleradiorepeaters/gametest/')}
        before=json.loads(old.read('fabric.mod.json'));after=json.loads(new.read('fabric.mod.json'))
        del before['entrypoints']['fabric-gametest'];assert before==after
        for name in new.namelist():
            if name=='fabric.mod.json':continue
            a,b=old.read(name),new.read(name)
            if name.endswith('.class'):
                ma,pa,fa,aa,ca,cp1=inspect(a);mb,pb,fb,bb,cb,cp2=inspect(b)
                assert ma==mb==61 and pa==pb and fa==fb and ca==cb,name
                assert {k:v for k,v in aa.items() if name[:-6]+'.'+k not in removed}==bb,name
                assert not any(isinstance(x,str) and ('gametest/' in x or 'org/junit/' in x) for x in cp2),name
                checked+=len(bb);classes+=1
            else:assert a==b,name
        if module=='core':
            assert not any('nether_focus' in n or 'end_focus' in n for n in new.namelist())
            assert not any('simpleradiofoci' in n for n in new.namelist())
            assert not any('NativeTravelMixin' in n or 'NativeTransmissionMixin' in n for n in new.namelist())
        else:
            assert after['depends']['simpleradio_repeaters']=='0.5.0'
    results.append({'module':module,'passed':True,'jar':output.name,'bytes':output.stat().st_size,'sha256':hashlib.sha256(output.read_bytes()).hexdigest(),
                    'classes':classes,'retained_methods_identical':checked,'removed_test_helpers':len(removed),'no_obfuscation':True})
out=p/'validation/0.5.0';out.mkdir(parents=True,exist_ok=True)
(out/'prototype-verification.json').write_text(json.dumps(results,indent=2)+'\n')
print(json.dumps(results,indent=2))
