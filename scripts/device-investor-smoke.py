# Historical MarketLens 1.1 UI smoke script; not used for the Studio Compose UI.
# Disposable emulator only. Uses verified, locally seeded public source responses.
import os,subprocess,time,re,json,xml.etree.ElementTree as E
from pathlib import Path
ADB=os.environ.get('MARKET_ADB','/workspace/android/toolchain/sdk/platform-tools/adb');SERIAL=os.environ.get('MARKET_TEST_SERIAL','emulator-5554')
if not SERIAL.startswith('emulator-'):raise SystemExit('Disposable emulator required')
base=[ADB,'-s',SERIAL];checks=0

def call(args):return subprocess.check_output(base+args,timeout=60)
def shell(*args):return call(['shell']+list(args))
def tree():
 shell('uiautomator','dump','/sdcard/marketlens-investor.xml')
 return E.fromstring(call(['exec-out','cat','/sdcard/marketlens-investor.xml']))
def find(root,text,exact=False):
 out=[]
 for n in root.iter('node'):
  value=n.attrib.get('text','');b=list(map(int,re.findall(r'\d+',n.attrib.get('bounds',''))))
  if (value.casefold()==text.casefold() if exact else text.casefold() in value.casefold()) and len(b)==4 and b[2]>b[0] and b[3]>b[1]:out.append(n)
 return out
def tap(node):
 l,t,r,b=map(int,re.findall(r'\d+',node.attrib['bounds']));shell('input','tap',str((l+r)//2),str((t+b)//2))
def wait(text,seconds=60,exact=False):
 end=time.time()+seconds
 while time.time()<end:
  root=tree();matches=find(root,text,exact)
  if matches:return root,matches[0]
 raise AssertionError('Not visible: '+text)
def seek(text,max_swipes=7,exact=False):
 for n in range(max_swipes+1):
  root=tree();matches=find(root,text,exact)
  if matches:return root,matches[0]
  shell('input','swipe','260','990','260','450','350')
 raise AssertionError('Not found while scrolling: '+text)
def nav(name):root=tree();tap(find(root,name,True)[-1])
def check(ok,message):
 global checks
 if not ok:raise AssertionError(message)
 checks+=1;print('PASS '+message,flush=True)
def shot(name):Path('build/'+name+'.png').write_bytes(call(['exec-out','screencap','-p']))
def enter(node,value):
 tap(node);shell('input','keyevent','123',*(['67']*24));shell('input','text',value)
def field(index):
 root=tree();return [n for n in root.iter('node') if n.attrib.get('class')=='android.widget.EditText'][index]
def state():
 raw=call(['exec-out','run-as','app.marketlens','cat','shared_prefs/investor-v11.xml'])
 root=E.fromstring(raw)
 return json.loads(next(n.text for n in root if n.attrib.get('name')=='state'))

try:
 root,_=wait('52-week highs',90);check(bool(find(root,'IPOs',True)) and bool(find(root,'Portfolio',True)),'Expanded navigation renders after in-place update')
 nav('Watchlist');root,_=wait('RELIANCE');check(True,'Original watchlist survives the update')
 nav('Discover');root,field=wait('Search company or NSE symbol');tap(field);shell('input','text','SEDEMAC');shell('input','keyevent','66');root,_=wait('Fund holdings',90);tap(find(root,'Fund holdings')[0]);root,_=seek('MF / UTI filing holders');check(bool(find(root,'20.57%')) and bool(find(root,'23')),'SEDEMAC source ownership and holder count render')
 root,combined=seek('Combined company stake');check(bool(find(root,'4.33%')),'Grouped SBI stake is labelled combined');shot('v11-holdings')
 root,button=seek('View complete names as filed');tap(button);root,_=wait('One combined disclosed position');check(bool(find(root,'SBI Balanced Hybrid Fund')),'Original grouped names are available without per-scheme allocation');shot('v11-holding-names');tap(find(root,'Done',True)[0])
 nav('Portfolio');root,button=wait('Add an equity holding');tap(button);root,_=wait('NSE symbol');fields=[n for n in root.iter('node') if n.attrib.get('class')=='android.widget.EditText'];enter(fields[0],'SEDEMAC');tap(find(tree(),'Continue',True)[0]);root,_=wait('manual position');fields=[n for n in root.iter('node') if n.attrib.get('class')=='android.widget.EditText'];tap(field(0));shell('input','keyevent','123',*(['67']*24));shell('input','text','10');shell('input','keyevent','61');shell('input','keyevent','123',*(['67']*24));shell('input','text','3000');shell('input','keyevent','61');shell('input','keyevent','123',*(['67']*24));shell('input','text','50');time.sleep(1);shell('input','keyevent','4');root,save=wait('Save',exact=True);tap(save);root,_=wait('₹30,050.00');saved=state();check(saved['positions']['SEDEMAC']['quantity']==10 and saved['positions']['SEDEMAC']['fees']==50,'Manual position persists independently of a broker');shot('v11-portfolio')
 nav('IPOs');root,_=wait('Vedant Poly Fabs Limited',90);check(True,'Official IPO records render from source data');root,button=seek('Issue details & tracking');tap(button);root,button=wait('Track this IPO');tap(button);root,_=wait('Tracked · tap to stop');check(bool(state().get('ipos')),'Original IPO identity can be tracked locally')
 root,_=seek('1200 Equity Shares',10);check(True,'Official issue lot information is displayed');shot('v11-ipo')
 nav('IPOs');root,button=wait('GMP',exact=True);tap(button);root,_=wait('GMP / UNOFFICIAL');root,_=seek('Antala Industries',5);check(True,'GMP has an explicit unofficial identity and publisher rows');shot('v11-gmp')
 root,button=seek('Observed quote history',3);tap(button);root,_=wait('Locally observed publisher snapshots');check(True,'Observed GMP history is source-labelled');tap(find(root,'Done',True)[0]);nav('IPOs');root,button=wait('IPOWatch',exact=True);tap(button);root,_=seek('Vedant Poly Fabs',6);check(True,'Second original GMP publisher remains separate');shot('v11-gmp-second')
 print('PASS: '+str(checks)+' Android investor UI/offline checks. No orders, applications or calendar events.',flush=True)
except Exception as error:
 print('FAIL: '+repr(error),flush=True)
 shot('v11-device-failure');raise
