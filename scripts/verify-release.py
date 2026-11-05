"""Check produced APK/AAB metadata, native alignment and signatures without reading signing secrets."""
from pathlib import Path
from zipfile import ZipFile
import struct,subprocess,json,sys,os,shutil
root=Path(__file__).resolve().parents[1]
sdk=Path(os.environ.get('ANDROID_HOME','/workspace/android/toolchain/sdk'))
build_tools=sdk/'build-tools/35.0.0'
report=[]
for rel in ['app/build/outputs/apk/release/app-release.apk','app/build/outputs/bundle/release/app-release.aab']:
 p=root/rel
 if not p.exists():raise SystemExit('Missing artifact: '+rel)
 with ZipFile(p)as z:
  libs=[]
  for n in z.namelist():
   if not n.endswith('.so'):continue
   b=z.read(n);assert b[:4]==b'\x7fELF',n
   bits=b[4];end='<' if b[5]==1 else '>'
   phoff=struct.unpack_from(end+('Q'if bits==2 else 'I'),b,32 if bits==2 else 28)[0]
   size,count=struct.unpack_from(end+'HH',b,54 if bits==2 else 42)
   alignment=[]
   for i in range(count):
    off=phoff+i*size
    if struct.unpack_from(end+'I',b,off)[0]==1:alignment.append(struct.unpack_from(end+('Q'if bits==2 else 'I'),b,off+(48 if bits==2 else 28))[0])
   assert alignment and all(x>=16384 for x in alignment),(n,alignment)
   libs.append({'file':n,'load_segment_alignment':alignment})
  policy_name='assets/privacy.txt' if p.suffix=='.apk' else 'base/assets/privacy.txt'
  policy=z.read(policy_name).decode();assert 'Publisher: karthik' in policy and 'ykn5006@gmail.com' in policy
 if p.suffix=='.apk':
  subprocess.run([str(build_tools/'zipalign'),'-c','-P','16','4',str(p)],check=True,stdout=subprocess.DEVNULL)
  subprocess.run(['java','-jar',str(build_tools/'lib/apksigner.jar'),'verify',str(p)],check=True,stdout=subprocess.DEVNULL)
  metadata=subprocess.check_output([str(build_tools/'aapt2'),'dump','badging',str(p)],text=True)
  assert "package: name='app.marketlens.studio'" in metadata and "targetSdkVersion:'36'" in metadata
  assert "versionName='1.1.0'" in metadata and "versionCode='2'" in metadata
 else:
  signer=shutil.which('jarsigner') or '/workspace/android/toolchain/jdk17/bin/jarsigner'
  output=subprocess.check_output([signer,'-verify',str(p)],text=True,stderr=subprocess.STDOUT)
  assert 'jar verified' in output,output
  metadata='Signed Android App Bundle; native ELF segments verified. APK built from the same release variant targets API 36.'
 report.append({'artifact':p.name,'bytes':p.stat().st_size,'native_libraries':libs,'metadata':metadata if p.suffix=='.aab' else metadata.splitlines()[:6]})
out=root/'releases/VERIFICATION.json';out.parent.mkdir(exist_ok=True);out.write_text(json.dumps(report,indent=2))
print('PASS: release APK/AAB signatures, separate app ID/version, target API 36, bundled privacy policy, 16 KB ELF alignment and APK ZIP alignment.')
