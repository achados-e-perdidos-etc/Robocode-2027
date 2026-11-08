#!/usr/bin/env python3
"""Quatro melhorias independentes: selecao e validacao longa, com processos novos por batalha."""
import argparse,hashlib,json,os,re,shutil,subprocess,zipfile
from pathlib import Path
import attack,bo3,diagnose,samples,train
FLAGS=('CONTEXT_DEFENSE','TACTICAL_MELEE','EFFICIENT_FIRE','PREDICTIVE_TRUST')
PROFILES={'base':None,'defense':(1,0,0,0),'melee':(0,1,0,0),'power':(0,0,1,0),'trust':(0,0,0,1),'all':(1,1,1,1),'neutral':(0,0,0,0)}
CONTEXTS={'diamond':('voidious.Diamond 1.8.28',2),'supermercutio':('reference.supermercutio*',2),'strong_melee':('voidious.Diamond 1.8.28,reference.supermercutio*',3),'vingation':('titan.TitanVingation 1.7',2)}
SELECTION=(10103,10331)
VALIDATION=(10709,10949,11213)
def sha(s):return hashlib.sha256(s.encode()).hexdigest()
def prepare(source):
 if 'USE_CONTEXT_DEFENSE' in source:return source
 patch=json.loads(Path(__file__).with_name('four-prototype.json').read_text())
 if sha(source)!=patch['baseline_sha256']:raise ValueError('Original 3.8 source required')
 for c in patch['changes']:
  if source.count(c['old'])!=1:raise ValueError('Ambiguous patch')
  source=source.replace(c['old'],c['new'],1)
 return source

def profile(source,name):
 if name=='base':return source
 source=prepare(source)
 for key,value in zip(FLAGS,PROFILES[name]):
  source,n=re.subn(r'USE_'+key+r' = (?:true|false)','USE_'+key+' = '+str(bool(value)).lower(),source)
  if n!=1:raise ValueError('Missing flag '+key)
 return source

def parse_case(path,case):
 text=path.read_text();name='treino.four'+case['profile']+'*';own='\n'.join(x for x in text.splitlines() if x.startswith('['+name+']'))
 scores=[{'robot':n,'score':int(s),'wins':int(w)} for n,s,w in re.findall(r'RESULT\t([^\t\n]+)\t(\d+)\t(\d+)',text)]
 starts=[{'round':int(i),'robot':n,'x':float(x),'y':float(y),'heading':float(h)} for i,n,x,y,h in re.findall(r'START_POSITION\t(\d+)\t([^\t\n]+)\t([^\t\n]+)\t([^\t\n]+)\t([^\t\n]+)',text)]
 turns={int(i):int(t) for i,t in re.findall(r'ROUND_TURNS\t(\d+)\t(\d+)',text)}
 errors=[s for s in text.splitlines() if any(x in s for x in ('Exception','ERROR\t','Unable to stop thread','No score will be generated'))]
 tel=[diagnose.parse_telemetry(x,case['rounds']) for x in own.splitlines() if ' TELEMETRY ' in x]
 usage=[list(map(int,s.split(','))) for s in re.findall(r'GUN_USAGE \[([^\]]+)\]',own)]
 stats=[{'round':int(i),'shots':int(s),'hits':int(h),'walls':int(w),'skipped':int(k)} for i,s,h,w,k in re.findall(r'TitanPrime round=(\d+) shots=(\d+) hits=(\d+) waves=\d+ matched=\d+ walls=(\d+) skipped=(\d+)',own)]
 upgrades=[{k:int(v) for k,v in re.findall(r'(\w+)=(\d+)',line)} for line in own.splitlines() if ' UPGRADES ' in line]
 N=case['rounds'];versions=re.findall(r'^VERSION\t([^\n]+)',text,re.M);cold=re.findall(r'COLD_START gun=(\d+) surf=(\d+)',own);memory=re.findall(r'COLD_GUN_CONTEXT enemies=(\d+)',own)
 valid='STATUS\tcomplete' in text and not errors and versions==['1.11.1'] and len(scores)==case['physical_count'] and len(starts)==N*case['physical_count'] and set(turns)==set(range(N)) and sorted(t['round'] for t in tel)==list(range(N)) and sorted(t['round'] for t in stats)==list(range(N)) and len(usage)==N and cold==[('1228','128')] and memory==['0']
 if case['profile']!='base':valid=valid and sorted(u['round'] for u in upgrades)==list(range(N))
 valid=valid and len({(s['round'],s['robot']) for s in starts})==N*case['physical_count']
 if valid:
  for i in range(N):
   t=next(t for t in tel if t['round']==i);s=next(s for s in stats if s['round']==i)
   if t['shots']!=s['shots'] or t['hits']!=s['hits']:raise ValueError('Telemetry mismatch')
  if sum(map(sum,usage))!=sum(t['shots'] for t in tel):raise ValueError('Gun count mismatch')
 result=next((s for s in scores if s['robot']==name),None);valid=valid and result is not None
 reasons=[]
 if 'STATUS\tcomplete' not in text:reasons.append('battle_not_completed')
 if errors:reasons.append('runtime_errors')
 if versions!=['1.11.1']:reasons.append('engine_version')
 if len(scores)!=case['physical_count']:reasons.append('score_count')
 if len(starts)!=N*case['physical_count'] or len({(s['round'],s['robot']) for s in starts})!=N*case['physical_count']:reasons.append('position_samples')
 if set(turns)!=set(range(N)):reasons.append('round_durations')
 if sorted(t['round'] for t in tel)!=list(range(N)):reasons.append('telemetry_rounds')
 if sorted(t['round'] for t in stats)!=list(range(N)):reasons.append('legacy_stats_rounds')
 if len(usage)!=N:reasons.append('gun_usage_rounds')
 if cold!=[('1228','128')] or memory!=['0']:reasons.append('initial_memory')
 if case['profile']!='base' and sorted(u['round'] for u in upgrades)!=list(range(N)):reasons.append('upgrade_rounds')
 if result is None:reasons.append('own_result_missing')
 return {**case,'validation_reasons':reasons,'status':'complete' if valid else 'invalid','scores':scores,'wins':result['wins'] if valid else None,'engine':versions,'cold':cold,'empty_memory':memory,'round_turns':turns,'start_positions':starts,'position_adjustments':re.findall(r'POSITION_ADJUSTED\t([^\n]+)',text),'errors':errors,'telemetry':tel,'round_stats':stats,'gun_usage':usage,'upgrades':upgrades,'skipped':sum(s['skipped'] for s in stats)}

def run_case(a,case,attempt):
 # Arquivar apenas pastas-raiz: mover o pai tambem move seus filhos .data.
 for folder in list((a.home/'robots').rglob('*.data')):
  if any(p.name.endswith('.data') for p in folder.relative_to(a.home/'robots').parents if str(p)!='.'):continue
  dest=a.output/'archived-data'/case['key']/str(attempt)/folder.relative_to(a.home/'robots');dest.parent.mkdir(parents=True,exist_ok=True);shutil.move(str(folder),str(dest))
 if case['context']=='vingation':
  dest=a.home/'robots/.data/titan/TitanVingation.data';dest.parent.mkdir(parents=True,exist_ok=True);shutil.copytree(a.output/'received-data',dest)
 log=a.output/(case['key']+'-attempt'+str(attempt)+'.log');suite=log.with_suffix('.suite')
 setup={**case,'category':'melee' if case['physical_count']>2 else 'duel'}
 suite.write_text(f"{case['key']}|treino.four{case['profile']}*,{case['opponents']}|{case['rounds']}|{samples.positions(setup)}\n")
 with log.open('w') as stream:subprocess.run(['java','-Xmx768m','-Djava.awt.headless=true',*train.JAVA_OPTIONS,'-cp',str(a.output)+os.pathsep+str(a.home/'libs/*'),'samplerunner',str(a.home),str(suite)],cwd=a.home,stdout=stream,stderr=subprocess.STDOUT,timeout=1200,check=True)
 return {**parse_case(log,case),'attempt':attempt}

def phase(a,label,names,contexts,seeds,rounds):
 results=[];attempts=[]
 for ci,context in enumerate(contexts):
  for si,seed in enumerate(seeds):
   opponents,count=CONTEXTS[context];offset=(ci+si)%len(names);ordered=names[offset:]+names[:offset]
   cases=[{'key':f'{label}-{n}-{context}-{seed}','profile':n,'context':context,'seed':seed,'opponents':opponents,'physical_count':count,'rounds':rounds} for n in ordered]
   group=[]
   for c in cases:
    row=run_case(a,c,0);group.append(row);attempts.append(row);print(c['key'],row['status'],row['wins'],flush=True)
   if any(r['status']!='complete' for r in group):
    group=[]
    for c in cases:
     row=run_case(a,c,1);group.append(row);attempts.append(row);print('RETRY',c['key'],row['status'],row['wins'],flush=True)
   results+=group;(a.output/(label+'.json')).write_text(json.dumps({'results':results,'attempts':attempts},indent=2)+'\n')
 return {'results':results,'attempts':attempts}

def rank(rows):
 if any(r['status']!='complete' for r in rows):return (-1,-999999)
 return (sum(r['wins'] for r in rows),-sum(r['skipped'] for r in rows))

def main():
 p=argparse.ArgumentParser(description=__doc__);p.add_argument('--home',type=Path);p.add_argument('--ecj',type=Path);p.add_argument('--output',type=Path);p.add_argument('--source',type=Path,default=train.ROOT/'robots/equipe/titanprime.java');p.add_argument('--vingation-jar',type=Path);p.add_argument('--saved-data',type=Path);p.add_argument('--export',type=Path);p.add_argument('--profile',choices=PROFILES,default='all');a=p.parse_args()
 if a.export:
  a.export.parent.mkdir(parents=True,exist_ok=True);a.export.write_text(profile(a.source.read_text(),a.profile));return
 for key in ('home','ecj','output','source','vingation_jar','saved_data'):
  if getattr(a,key) is not None:setattr(a,key,getattr(a,key).resolve())
 if not all((a.home,a.output,a.vingation_jar,a.saved_data)):p.error('home/output/vingation-jar/saved-data required')
 if a.output.exists():p.error('Use new output directory to preserve attempts')
 a.output.mkdir(parents=True);source=a.source.read_text();sources={n:profile(source,n) for n in PROFILES if n!='neutral'}
 plan={'source_sha256':sha(source),'variants_sha256':{n:sha(s) for n,s in sources.items()},'profiles':PROFILES,'contexts':CONTEXTS,'selection_seeds':SELECTION,'validation_seeds':VALIDATION,'selection_rounds':8,'validation_rounds':12,'metric':'All round wins, equal weight','promotion_rule':'At least four extra round wins in reserved validation; all included cases valid; at most three extra skipped turns. Report every context. No preference for first rounds or BO3.','invalid_policy':'One retry of entire paired context/seed group if any case invalid. Preserve all original attempts; never replace valid-only groups.','jar_sha256':hashlib.sha256(a.vingation_jar.read_bytes()).hexdigest(),'saved_files':[{'path':str(f.relative_to(a.saved_data)),'sha256':hashlib.sha256(f.read_bytes()).hexdigest()} for f in sorted(a.saved_data.rglob('*')) if f.is_file()]}
 (a.output/'plan.json').write_text(json.dumps(plan,indent=2)+'\n');shutil.copytree(a.saved_data,a.output/'received-data')
 newhome=a.output/'engine';shutil.copytree(a.home,newhome,ignore=shutil.ignore_patterns('treino','*.data','robot.database','*.log'));a.home=newhome
 for jar in list((newhome/'robots').rglob('*.jar')):
  with zipfile.ZipFile(jar) as z:has_titan=any(n.startswith('titan/') for n in z.namelist())
  if has_titan:jar.unlink()
 shutil.copy2(a.vingation_jar,newhome/'robots/titanvingation.jar')
 runner=a.output/'samplerunner.java';runner.write_text(samples.RUNNER.replace('new BattlefieldSpecification(800,600),3,450','new BattlefieldSpecification(800,600),Integer.parseInt(a[2]),450'));train.compile_java(a,[runner],a.output)
 for n,s in sources.items():attack.install(a,'four'+n,s)
 selection=phase(a,'selection',list(sources),list(CONTEXTS)[:3],SELECTION,8)
 groups={n:[r for r in selection['results'] if r['profile']==n] for n in sources}
 selected=max((n for n in sources if n!='base'),key=lambda n:(*rank(groups[n]),-sum(PROFILES[n])))
 print('SELECTED',selected,{n:rank(rows) for n,rows in groups.items()},flush=True)
 validation=phase(a,'validation',['base',selected],list(CONTEXTS),VALIDATION,12)
 b=[r for r in validation['results'] if r['profile']=='base'];c=[r for r in validation['results'] if r['profile']==selected]
 valid=all(r['status']=='complete' for r in b+c)
 checks={'all_validation_valid':valid,'selection_complete':all(r['status']=='complete' for r in selection['results']), 'four_extra_round_wins':valid and rank(c)[0]>=rank(b)[0]+4,'at_most_three_extra_skips':valid and sum(r['skipped'] for r in c)<=sum(r['skipped'] for r in b)+3}
 report={**plan,'selection':selection,'selected':selected,'validation':validation,'checks':checks,'proposed_promotion':all(checks.values())}
 (a.output/'candidate.java.txt').write_text(sources[selected]);(a.output/'neutral.java.txt').write_text(profile(source,'neutral'));(a.output/'results.json').write_text(json.dumps(report,indent=2)+'\n');print('FINAL',rank(b),rank(c),checks,flush=True)
if __name__=='__main__':main()
