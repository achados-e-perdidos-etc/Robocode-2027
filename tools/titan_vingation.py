#!/usr/bin/env python3
"""Compara TitanVingation limpo e com os dados recebidos, em batalhas longas."""
import argparse,hashlib,json,os,re,shutil,subprocess
from pathlib import Path
import bo3,samples,train

def main():
 p=argparse.ArgumentParser(description=__doc__);p.add_argument('--home',type=Path,required=True);p.add_argument('--ecj',type=Path);p.add_argument('--output',type=Path,required=True);p.add_argument('--jar',type=Path,required=True);p.add_argument('--saved-data',type=Path,required=True);p.add_argument('--rounds',type=int,default=10);p.add_argument('--seeds',default='9001,9257');p.add_argument('--resume',action='store_true');a=p.parse_args()
 for k in ('home','ecj','output','jar','saved_data'):
  if getattr(a,k) is not None:setattr(a,k,getattr(a,k).resolve())
 if a.rounds<2:p.error('At least two rounds')
 if a.output.exists() and not a.resume:p.error('Use a new output folder or --resume')
 seeds=list(map(int,a.seeds.split(',')));a.output.mkdir(parents=True,exist_ok=a.resume)
 source=(train.ROOT/'robots/equipe/titanprime.java').read_text();home=a.output/'engine'
 if not a.resume:shutil.copytree(a.home,home,ignore=shutil.ignore_patterns('treino','*.data','robot.database','*.log'))
 a.home=home
 import zipfile
 for other in list((home/'robots').rglob('*.jar')):
  with zipfile.ZipFile(other) as z:has_titan=any(n.startswith('titan/') for n in z.namelist())
  if has_titan and hashlib.sha256(other.read_bytes()).hexdigest()!=hashlib.sha256(a.jar.read_bytes()).hexdigest():other.unlink()
 if not any(hashlib.sha256(x.read_bytes()).hexdigest()==hashlib.sha256(a.jar.read_bytes()).hexdigest() for x in (home/'robots').rglob('*.jar')):shutil.copy2(a.jar,home/'robots/titanvingation.jar')
 jars=[x for x in (home/'robots').rglob('*.jar') if hashlib.sha256(x.read_bytes()).hexdigest()==hashlib.sha256(a.jar.read_bytes()).hexdigest()]
 if len(jars)!=1:raise ValueError('Expected exactly one original TitanVingation jar in isolated installation')
 if jars[0].parent!=home/'robots':jars[0].rename(home/'robots/titanvingation.jar')
 bo3.install(a,'primev3',source)
 runner=a.output/'samplerunner.java';runner.write_text(samples.RUNNER.replace('if(r.getClassName().startsWith("sample"))','if(true)').replace('new BattlefieldSpecification(800,600),3,450','new BattlefieldSpecification(800,600),Integer.parseInt(a[2]),450'));train.compile_java(a,[runner],a.output)
 prime='treino.primev3*';v3='titan.TitanVingation 1.7'
 contexts={'diamond':('voidious.Diamond 1.8.28',2),'supermercutio':('reference.supermercutio*',2),'strong_melee':('voidious.Diamond 1.8.28,reference.supermercutio*',3)}
 cases=[]
 for context,(opponents,count) in contexts.items():
  for seed in seeds:
   for label,robot in [('prime',prime),('vingation_clean',v3),('vingation_saved',v3)]:cases.append({'key':f'{label}-{context}-{seed}','label':label,'robot':robot,'context':context,'seed':seed,'opponents':opponents,'physical_count':count,'category':'duel' if count==2 else 'melee'})
 for seed in seeds:
  for mode in ('clean','saved'):cases.append({'key':f'direct-{mode}-{seed}','label':'direct_'+mode,'robot':prime,'context':'direct','seed':seed,'opponents':v3,'physical_count':2,'category':'duel'})
 saved_files=[{'path':str(x.relative_to(a.saved_data)),'sha256':hashlib.sha256(x.read_bytes()).hexdigest(),'bytes':x.stat().st_size} for x in sorted(a.saved_data.rglob('*')) if x.is_file()]
 if not saved_files:raise ValueError('Saved data template is empty')
 plan={'saved_files':saved_files,'rounds':a.rounds,'seeds':seeds,'cases':cases,'jar_sha256':hashlib.sha256(a.jar.read_bytes()).hexdigest(),'source_sha256':hashlib.sha256(source.encode()).hexdigest(),'metric':'Round wins across all rounds, not BO3 or points','data_policy':'Fresh JVM per battle; external robot .data folders archived before every battle; embedded resources retained; saved variants restore identical supplied TitanVingation.data before each battle'}
 if a.resume:
  previous=json.loads((a.output/'results.json').read_text());results=previous.pop('results')
  if previous!=json.loads(json.dumps(plan)):raise ValueError('Resume protocol differs')
 else:
  (a.output/'plan.json').write_text(json.dumps(plan,indent=2)+'\n');results=[]
 for case in cases:
  if any(r['key']==case['key'] for r in results):continue
  # Preserva os dados produzidos, fora da pasta pesquisada pelo motor, sem reutiliza-los.
  for folder in list((home/'robots').rglob('*.data')):
   if any(parent.name.endswith('.data') for parent in folder.relative_to(home/'robots').parents if str(parent)!='.'):continue
   dest=a.output/'archived-data'/case['key']/folder.relative_to(home/'robots');dest.parent.mkdir(parents=True,exist_ok=True);shutil.move(str(folder),str(dest))
  if case['label'].endswith('saved'):
   dest=home/'robots/.data/titan/TitanVingation.data';dest.parent.mkdir(parents=True,exist_ok=True);shutil.copytree(a.saved_data,dest)
  suite=a.output/(case['key']+'.suite');suite.write_text(f"{case['key']}|{case['robot']},{case['opponents']}|{a.rounds}|{samples.positions(case)}\n")
  log=a.output/(case['key']+'.log')
  with log.open('w') as f:subprocess.run(['java','-Xmx768m','-Djava.awt.headless=true',*train.JAVA_OPTIONS,'-cp',str(a.output)+os.pathsep+str(home/'libs/*'),'samplerunner',str(home),str(suite)],cwd=home,stdout=f,stderr=subprocess.STDOUT,check=True,timeout=900)
  text=log.read_text();versions=re.findall(r'^VERSION\t([^\n]+)',text,re.M)
  scores=[{'robot':n,'score':int(s),'wins':int(w)} for n,s,w in re.findall(r'RESULT\t([^\t\n]+)\t(\d+)\t(\d+)',text)]
  rounds=[{'round':int(i),'robot':n,'firsts':int(w)} for i,n,w in re.findall(r'ROUND_SCORE\t(\d+)\t([^\t\n]+)\t(\d+)',text)]
  starts=[{'round':int(i),'robot':n,'x':float(x),'y':float(y),'heading':float(h)} for i,n,x,y,h in re.findall(r'START_POSITION\t(\d+)\t([^\t\n]+)\t([^\t\n]+)\t([^\t\n]+)\t([^\t\n]+)',text)]
  errors=[l for l in text.splitlines() if any(x in l for x in ('Exception','ERROR\t','Unable to stop thread','No score will be generated'))]
  valid='STATUS\tcomplete' in text and not errors and len(versions)==1 and len(scores)==case['physical_count'] and len(rounds)==a.rounds*case['physical_count'] and len(starts)==a.rounds*case['physical_count']
  own=next((s for s in scores if s['robot']==case['robot']),None)
  row={**case,'status':'complete' if valid else 'invalid','engine':versions,'scores':scores,'round_scores':rounds,'start_positions':starts,'position_adjustments':re.findall(r'POSITION_ADJUSTED\t([^\n]+)',text),'errors':errors,'wins':own['wins'] if valid and own else None,'round_win_rate':own['wins']/a.rounds if valid and own else None}
  results.append(row);(a.output/'results.json').write_text(json.dumps({**plan,'results':results},indent=2)+'\n');print(case['key'],row['status'],row['wins'],flush=True)
if __name__=='__main__':main()
