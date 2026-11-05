# Historical MarketLens 1.0 UI smoke script; not used for the Studio Compose UI.
# Run only on a disposable emulator seeded with the verified source responses.
import subprocess,os,time,re,xml.etree.ElementTree as E,json
from pathlib import Path
ADB=os.environ.get('MARKET_ADB','/workspace/android/toolchain/sdk/platform-tools/adb')
SERIAL=os.environ.get('MARKET_TEST_SERIAL','emulator-5554')
if not SERIAL.startswith('emulator-'):raise SystemExit('Disposable emulator required')
base=[ADB,'-s',SERIAL];checks=0

def call(args):return subprocess.check_output(base+args,timeout=50)
def shell(*args):return call(['shell']+list(args))
def tree():
 shell('uiautomator','dump','/sdcard/marketlens-smoke.xml')
 return E.fromstring(call(['exec-out','cat','/sdcard/marketlens-smoke.xml']))
def nodes(root,needle):return [n for n in root.iter('node') if needle in n.attrib.get('text','')]
def tap_node(node):
 values=list(map(int,re.findall(r'\d+',node.attrib['bounds'])))
 if len(values)!=4:raise AssertionError('Missing UI bounds')
 l,t,r,b=values
 if r<=l or b<=t:raise AssertionError('Control is offscreen')
 shell('input','tap',str((l+r)//2),str((t+b)//2))
def wait(needle,seconds=60):
 end=time.time()+seconds
 while time.time()<end:
  root=tree();matches=nodes(root,needle)
  if matches:return root,matches[0]
 raise AssertionError('UI text not found: '+needle)
def check(value,why):
 global checks
 if not value:raise AssertionError(why)
 checks+=1;print('PASS '+why,flush=True)
def shot(name):Path('build/'+name+'.png').write_bytes(call(['exec-out','screencap','-p']))

try:
 root,_=wait('52-week highs');check(bool(nodes(root,'59')) and bool(nodes(root,'51')),'Both cached exchange lists render with source date');shot('discover')
 tap_node(nodes(root,'52-week lows')[0]);root,_=wait('ACI');check(bool(nodes(root,'SAVED DOWNLOAD')),'Low toggle changes rows and retains saved-data label')
 field=next(n for n in root.iter('node') if n.attrib.get('text')=='Search company or NSE symbol');tap_node(field);shell('input','text','RELIANCE');shell('input','keyevent','66')
 root,_=wait('Save stock');check(bool(nodes(root,'RELIANCE')),'Search opens the selected stock detail')
 root,_=wait('₹1,311.00',90);check(bool(nodes(root,'52-week low')),'Saved quote and annual range render');shot('quote')
 save=next(n for n in root.iter('node') if 'Save stock' in n.attrib.get('text',''));tap_node(save);root,_=wait('Saved stock');check(True,'Save stock persists watchlist selection')
 tap_node(nodes(root,'Fund holdings')[0]);root,_=wait('Matched by ISIN');check(bool(nodes(root,'INE002A01018')) and bool(nodes(root,'Parag Parikh')),'Holdings jump displays exact-ISIN scheme matches');shot('holdings')
 tap_node(next(n for n in root.iter('node') if n.attrib.get('text')=='Watchlist'));root,_=wait('RELIANCE');check(bool(nodes(root,'04-Nov-2026')),'Watchlist retains the quote source timestamp')
 tap_node(next(n for n in root.iter('node') if n.attrib.get('text')=='Funds'));wait('Coverage you can inspect');shell('input','swipe','260','970','260','410','400');root,_=wait('Saved scheme snapshots');check(bool(nodes(root,'snapshots · 6')),'Fund coverage page shows six saved disclosures');shot('funds')
 print('PASS: '+str(checks)+' Android UI/offline checks with verified source responses. No trades.',flush=True)
except Exception as e:
 print('FAIL: '+repr(e),flush=True)
 shot('device-failure');raise
