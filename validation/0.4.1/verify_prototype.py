from pathlib import Path
from zipfile import ZipFile
import hashlib,json,struct
ROOT=Path('/workspace/scratch/b97ec851c650')
PROJECT=ROOT/'radio-card-controls/work/simple-radio-repeaters'
TASK=ROOT/'radio-nether-balance'
ORIGINAL=PROJECT/'build/libs/simple-radio-repeaters-fabric-1.20.1-0.4.1.jar'
OUTPUT=PROJECT/'build/libs/simple-radio-repeaters-fabric-1.20.1-0.4.1-prototype.jar'

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

report=json.loads((PROJECT/'build/prototype/packaging-report.json').read_text())
assert report['input_sha256']==hashlib.sha256(ORIGINAL.read_bytes()).hexdigest()
removed=set(report['removed_test_only_methods']); checked=0;changed=[]
with ZipFile(ORIGINAL) as old,ZipFile(OUTPUT) as new:
    assert new.testzip() is None
    oldnames=set(old.namelist());newnames=set(new.namelist())
    assert oldnames-newnames=={n for n in oldnames if n.startswith('dev/paul/simpleradiorepeaters/gametest/')}
    assert not newnames-oldnames
    assert not any('/gametest/' in n or 'PrototypeJar' in n or 'Preview' in n or 'junit' in n.lower() for n in newnames)
    before=json.loads(old.read('fabric.mod.json'));after=json.loads(new.read('fabric.mod.json'))
    del before['entrypoints']['fabric-gametest'];assert before==after
    for name in sorted(newnames-{'fabric.mod.json'}):
        a,b=old.read(name),new.read(name)
        if name.endswith('.class'):
            ma,pa,fa,aa,ca,cp1=inspect(a);mb,pb,fb,bb,cb,cp2=inspect(b)
            assert ma==mb==61 and pa==pb and fa==fb and ca==cb,name
            expected={key:value for key,value in aa.items() if name[:-6]+'.'+key not in removed}
            assert expected==bb,name+' changed a retained method or its debug data'
            checked+=len(bb)
            assert not any(isinstance(x,str) and ('gametest/' in x or 'org/junit/' in x) for x in cp2),name
            if a!=b:changed.append(name)
        else:assert a==b,name
    assert changed==sorted(report['changed_classes'])
    assert not any(n.endswith(('.SF','.RSA','.DSA')) for n in newnames)
result={'passed':True,'version':after['version'],'bytes':OUTPUT.stat().st_size,
        'sha256':hashlib.sha256(OUTPUT.read_bytes()).hexdigest(),'test_classes_removed':len(report['excluded_test_classes']),
        'test_only_methods_removed':len(removed),'classes_checked':sum(n.endswith('.class') for n in newnames),
        'retained_methods_byte_identical_including_debug':checked,'constant_pools_fields_and_class_attributes_unchanged':True,
        'resources_and_mixin_configuration_unchanged':True,'metadata_change':'Removed fabric-gametest entrypoints only',
        'added_renaming_or_obfuscation':False,'required_fabric_intermediary_mapping_retained':True}
(TASK/'analysis/bytecode-verification.json').write_text(json.dumps(result,indent=2)+'\n')
print(json.dumps(result,indent=2))
