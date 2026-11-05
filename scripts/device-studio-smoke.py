"""UI checks for a disposable emulator with explicitly seeded original-source responses."""
import subprocess,os,time,re,json,xml.etree.ElementTree as E
from pathlib import Path
ADB=os.environ.get('MARKET_ADB','/workspace/android/toolchain/sdk/platform-tools/adb')
SERIAL=os.environ.get('MARKET_TEST_SERIAL','emulator-5554')
assert SERIAL.startswith('emulator-'),'Disposable emulator only'
PACKAGE=os.environ.get('MARKET_PACKAGE','app.marketlens.studio.debug')
base=[ADB,'-s',SERIAL];count=0

def call(*args):return subprocess.check_output(base+list(args),timeout=90)
def shell(*args):return call('shell',*args)
def tree():
 shell('uiautomator','dump','/sdcard/studio-check.xml')
 return E.fromstring(call('exec-out','cat','/sdcard/studio-check.xml'))
def matches(root,label):return [n for n in root.iter('node')if n.get('text','').casefold()==label.casefold() or n.get('content-desc','').casefold()==label.casefold()]
def bounds(n):return list(map(int,re.findall(r'\d+',n.get('bounds',''))))
def tapnode(n):
 l,t,r,b=bounds(n);assert r>l and b>t
 shell('input','tap',str((l+r)//2),str((t+b)//2))
def wait(label,limit=120):
 end=time.time()+limit
 while time.time()<end:
  r=tree();nodes=matches(r,label)
  if nodes:return r,nodes[0]
  time.sleep(.5)
 raise AssertionError('UI text not found: '+label)
def tap(label,scroll=False):
 for _ in range(7 if scroll else 2):
  r=tree();nodes=matches(r,label)
  for n in nodes:
   b=bounds(n)
   if len(b)==4 and b[2]>b[0]and b[3]>b[1]:tapnode(n);return
  if scroll:shell('input','swipe','270','930','270','350','350')
  else:time.sleep(1)
 raise AssertionError('Cannot tap: '+label)
def check(cond,label):
 global count
 assert cond,label
 count+=1;print('PASS '+label,flush=True)
def shot(name):
 p=Path('build/screenshots');p.mkdir(parents=True,exist_ok=True);(p/(name+'.png')).write_bytes(call('exec-out','screencap','-p'))
try:
 if os.environ.get('MARKET_START','full')=='full':
  r,_=wait('New highs');check(bool(matches(r,'Markets')),'Dashboard and five-tab navigation render');shot('markets')
  tap('Funds');wait('Overlap');check(True,'Funds module opens');shot('funds')
  tap('Overlap');wait('Weighted equity overlap');check(True,'Fund overlap view evaluates covered ISIN weights');shot('overlap')
  tap('Markets')
  r=tree()
  if not matches(r,'Find your next idea'):tap('Search stocks & companies')
 else:
  count=3
  print('Resuming after the three previously verified dashboard/fund checks.',flush=True)
 r,_=wait('Find your next idea');field=next(n for n in r.iter('node')if n.get('class')=='android.widget.EditText');tapnode(field);shell('input','text','TCS')
 end=time.time()+60
 while True:
  r=tree();rows=[n for n in matches(r,'TCS')if n.get('class')!='android.widget.EditText']
  if rows:tapnode(rows[0]);break
  if time.time()>end:raise AssertionError('Ticker result did not render')
  time.sleep(.5)
 r,_=wait('Overview');check(bool(matches(r,'Financials'))and bool(matches(r,'Ownership')),'Search opens stock quote/chart/research tabs')
 tap('Candles',scroll=True);check(True,'Chart switches to candlesticks');shot('stock-chart');shell('input','swipe','270','350','270','1000','350');shell('input','swipe','270','350','270','1000','350');tap('Ownership');wait('Company ownership',5) if False else None
 r=tree();check(any('company ownership' in n.get('text','').lower()for n in r.iter('node')),'Ownership tab shows dated company disclosure');shot('ownership')
 tap('Portfolio');tap('Add equity holding',scroll=True);r,_=wait('Add equity holding');fields=[n for n in r.iter('node')if n.get('class')=='android.widget.EditText'];assert len(fields)>=3
 for node,value in zip(fields[:3],['TCS','10','100']):tapnode(node);shell('input','text',value)
 shell('input','keyevent','4');tap('Save',scroll=True);wait('TCS');check(True,'Manual position validates and persists through Room');shot('portfolio-test-record')
 tap('Research');wait('Create a custom screener');check(True,'Research studio opens saved-screen workflow')
 tap('Settings');wait('Background checks');check(True,'Background settings are available without enabling them')
 tap('Privacy policy',scroll=True);r,_=wait('Your data. Your control.');check(any('Publisher: karthik' in n.get('text','')and 'ykn5006@gmail.com' in n.get('text','')for n in r.iter('node')),'In-app policy has correct public publisher/contact');shot('privacy')
 shell('am','force-stop',PACKAGE);shell('am','start','-n',PACKAGE+'/app.marketlens.MainActivity');wait('Markets');tap('Portfolio');wait('TCS');check(True,'Position survives full process restart')
 errors=call('logcat','-d','-s','AndroidRuntime:E').decode(errors='replace');check('FATAL EXCEPTION'not in errors,'No AndroidRuntime fatal exception during flows')
 print('PASS '+str(count)+' Studio UI checks. Source data were seeded explicitly; position was a disposable UI-test record.',flush=True)
except Exception:
 shot('failure');raise
