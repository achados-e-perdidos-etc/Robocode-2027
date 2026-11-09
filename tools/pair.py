#!/usr/bin/env python3
"""Recuperacao e tiro bloqueado; todos os sete robos do repositorio recebidos entram nos testes."""
import argparse,hashlib,json,re,shutil,zipfile
from pathlib import Path
import attack,four,samples,train
FLAGS=('RECOVERY','CLEAR_FIRE')
PROFILES={'base':None,'recovery':(1,0),'clear':(0,1),'both':(1,1)}
ROBOTS={'chimpaze':'review.main.chimpazecomsarna.ChimpazeComSarna*','obrabo':'review.main.obrabobot.OBraboBot*','titan':'review.main.titan.Titan*','titanv2':'review.main.titanv2.TitanV2*','vingation':'review.main.titanvingation.TitanVingation*','titans':'review.main.titans.Titans*','modular':'reviewmod.titan.TitanVingation*'}
CONTEXTS={'diamond':('voidious.Diamond 1.8.28',2),**{'repo_'+k:(v,2) for k,v in ROBOTS.items()},'repo_all':(','.join(ROBOTS.values()),8)}

def prepare(source):
 if 'USE_RECOVERY' in source:return source
 patch=json.loads(Path(__file__).with_name('pair-prototype.json').read_text())
 if four.sha(source)!=patch['baseline_sha256']:raise ValueError('Original 3.9 source required')
 for c in patch['changes']:
  if source.count(c['old'])!=1:raise ValueError('Ambiguous patch')
  source=source.replace(c['old'],c['new'],1)
 return source

def profile(source,name):
 if name=='base':return source
 source=prepare(source)
 for key,value in zip(FLAGS,PROFILES[name]):
  source,n=re.subn('USE_'+key+' = (?:true|false)','USE_'+key+' = '+str(bool(value)).lower(),source)
  if n!=1:raise ValueError('Feature flag missing')
 return source

def install_sources(a):
 inputs=[];manifest=[]
 for p in sorted((a.repository/'main').glob('*.java')):
  source=p.read_text();name=re.search(r'public class (\w+)',source).group(1);original=source
  if p.name=='OBraboBot.java':
   before='10,000 hours in this game 💀)\n", dodgeStreak);';after='10,000 hours in this game 💀)%n", dodgeStreak);'
   if before in source:source=source.replace(before,after)
  package='review.main.'+name.lower();source=re.sub(r'^package\s+[\w.]+;', 'package '+package+';',source,count=1)
  out=a.home/'robots'/Path(package.replace('.','/'))/(name+'.java');out.parent.mkdir(parents=True,exist_ok=True);out.write_text(source);inputs.append(out)
  manifest.append({'path':p.name,'original_sha256':four.sha(original),'deployed_sha256':four.sha(source),'class':package+'.'+name})
 branch=a.repository/'Titan/titan-vingation/src/main/java'
 for p in sorted(branch.rglob('*.java')):
  original=p.read_text();source=original.replace('package titan;','package reviewmod.titan;');source=re.sub(r'(?<![\w.])titan\.', 'reviewmod.titan.',source)
  out=a.home/'robots/reviewmod'/p.relative_to(branch);out.parent.mkdir(parents=True,exist_ok=True);out.write_text(source);inputs.append(out)
  manifest.append({'path':'Titan/'+str(p.relative_to(branch)),'original_sha256':four.sha(original),'deployed_sha256':four.sha(source)})
 train.compile_java(a,inputs,a.home/'robots');return manifest

def main():
 p=argparse.ArgumentParser(description=__doc__);p.add_argument('--home',type=Path);p.add_argument('--ecj',type=Path);p.add_argument('--repository',type=Path);p.add_argument('--output',type=Path);p.add_argument('--source',type=Path,default=train.ROOT/'robots/equipe/titanprime.java');p.add_argument('--export',type=Path);p.add_argument('--profile',choices=PROFILES,default='both');a=p.parse_args()
 if a.export:
  a.export.parent.mkdir(parents=True,exist_ok=True);a.export.write_text(profile(a.source.read_text(),a.profile));return
 for k in ('home','ecj','repository','output','source'):
  if getattr(a,k) is not None:setattr(a,k,getattr(a,k).resolve())
 if not all((a.home,a.repository,a.output)):p.error('home/repository/output required')
 if a.output.exists():p.error('Use new output directory')
 a.output.mkdir(parents=True);source=a.source.read_text();sources={n:profile(source,n) for n in PROFILES}
 plan={'baseline_sha256':four.sha(source),'variants_sha256':{n:four.sha(s) for n,s in sources.items()},'profiles':PROFILES,'contexts':CONTEXTS,'selection_seeds':[13003,13217],'validation_seeds':[13613,13901],'selection_rounds':8,'validation_rounds':10,'metric':'All round wins equally; no BO3 or first-round priority','rule':'At least four more reserved round wins; at most three extra skipped turns; all included cases valid. A single retry of the entire paired group after invalid execution, retaining original attempts.'}
 (a.output/'plan.json').write_text(json.dumps(plan,indent=2)+'\n')
 newhome=a.output/'engine';shutil.copytree(a.home,newhome,ignore=shutil.ignore_patterns('treino','*.data','robot.database','*.log'));a.home=newhome
 for pth in list((a.home/'robots').rglob('*.jar')):
  with zipfile.ZipFile(pth) as z:has_titan=any(n.startswith('titan/') for n in z.namelist())
  if has_titan:pth.unlink()
 manifest=install_sources(a);(a.output/'sources.json').write_text(json.dumps(manifest,indent=2)+'\n')
 runner=a.output/'samplerunner.java';runner.write_text(samples.RUNNER.replace('new BattlefieldSpecification(800,600),3,450','new BattlefieldSpecification(800,600),Integer.parseInt(a[2]),450'));train.compile_java(a,[runner],a.output)
 for n,s in sources.items():attack.install(a,'four'+n,s)
 old=four.CONTEXTS;original_run=four.run_case
 def audited_case(args,case,attempt):
  row=original_run(args,case,attempt);name='treino.four'+case['profile']+'*'
  text=(args.output/(case['key']+'-attempt'+str(attempt)+'.log')).read_text()
  row['pair_metrics']=[{k:int(v) for k,v in re.findall(r'(\w+)=(\d+)',line)} for line in text.splitlines() if line.startswith('['+name+'] PAIR ')]
  if case['profile']!='base' and sorted(v['round'] for v in row['pair_metrics'])!=list(range(case['rounds'])):
   row['status']='invalid';row['wins']=None;row['validation_reasons'].append('pair_metrics')
  return row
 four.run_case=audited_case
 try:
  four.CONTEXTS=CONTEXTS
  selection=four.phase(a,'selection',list(sources),['diamond','repo_titans','repo_all'],(13003,13217),8)
  groups={n:[r for r in selection['results'] if r['profile']==n] for n in sources}
  selected=max((n for n in sources if n!='base'),key=lambda n:(*four.rank(groups[n]),-sum(PROFILES[n])))
  print('SELECTED',selected,{n:four.rank(rows) for n,rows in groups.items()},flush=True)
  validation=four.phase(a,'validation',['base',selected],[k for k in CONTEXTS if k!='diamond'],(13613,13901),10)
 finally:four.CONTEXTS=old;four.run_case=original_run
 b=[r for r in validation['results'] if r['profile']=='base'];c=[r for r in validation['results'] if r['profile']==selected];valid=all(r['status']=='complete' for r in b+c)
 checks={'valid':valid,'selection_valid':all(r['status']=='complete' for r in selection['results']),'four_more_round_wins':valid and four.rank(c)[0]>=four.rank(b)[0]+4,'cpu':valid and sum(r['skipped'] for r in c)<=sum(r['skipped'] for r in b)+3}
 checks['modules_exercised']=all(sum(v[field] for r in c for v in r['pair_metrics'])>0 for flag,field in zip(PROFILES[selected],('recoveries','blocked_shots')) if flag)
 report={**plan,'source_manifest':manifest,'selection':selection,'selected':selected,'validation':validation,'checks':checks,'proposed_promotion':all(checks.values())};(a.output/'results.json').write_text(json.dumps(report,indent=2)+'\n');(a.output/'candidate.java.txt').write_text(sources[selected]);(a.output/'neutral.java.txt').write_text(profile(source,'recovery').replace('USE_RECOVERY = true','USE_RECOVERY = false'))
 print('FINAL',four.rank(b),four.rank(c),checks,flush=True)
if __name__=='__main__':main()
