from pathlib import Path
import shutil,subprocess,sys,os,csv,json,re,argparse
from types import SimpleNamespace
sys.path.insert(0,str(Path(__file__).resolve().parents[1]/'tools'));import train,samples
parser=argparse.ArgumentParser(description="Verifica CSV persistente, vitoria tardia e limite de 48 KB no motor real")
parser.add_argument('--home',required=True,type=Path);parser.add_argument('--ecj',required=True,type=Path);parser.add_argument('--output',required=True,type=Path);parser.add_argument('--source',type=Path,default=train.ROOT/'robots/equipe/titanprime.java')
args=parser.parse_args();r=args.output.resolve()
if r.exists():parser.error('Use uma pasta nova')
r.mkdir();h=r/'engine';shutil.copytree(args.home.resolve(),h,ignore=shutil.ignore_patterns('treino','equipe','*.data','robot.database','*.log'))
a=SimpleNamespace(home=h,ecj=args.ecj.resolve())
train.compile_java(a,[args.source.resolve()],h/'robots')
src=r/'loggingdummy.java';src.write_text('package fixture; public class loggingdummy extends robocode.Robot { public void run(){while(true)doNothing();} }')
train.compile_java(a,[src],h/'robots')
src=r/'samplerunner.java';src.write_text(samples.RUNNER.replace('new BattlefieldSpecification(800,600),3,450','new BattlefieldSpecification(800,600),1,450'));train.compile_java(a,[src],r)
suite=r/'probe.suite';suite.write_text('probe|equipe.titanprime*,fixture.loggingdummy*|1|200,300,0,550,300,180\n')
cmd=['java','-Djava.awt.headless=true',*train.JAVA_OPTIONS,'-cp',str(r)+os.pathsep+str(h/'libs/*'),'samplerunner',str(h),str(suite)]
def battle(index):
 with (r/('probe-'+str(index)+'.log')).open('w') as f:subprocess.run(cmd,cwd=h,stdout=f,stderr=subprocess.STDOUT,timeout=120,check=True)
 s=(r/('probe-'+str(index)+'.log')).read_text();assert 'STATUS\tcomplete' in s and 'DIAGNOSTIC_ERROR' not in s
 assert re.search(r'RESULT\tequipe.titanprime\*\t\d+\t1',s)
 files=list(h.rglob('diagnostico.csv'));assert len(files)==1;return files[0]
p=battle(1);first=list(csv.DictReader(p.open()));assert len(first)==1 and first[0]['won']=='1'
text=p.read_text();header,actual=text.split('\n',1);header+='\n';fake=actual.replace(first[0]['battle'],'fixture-old');count=(48000-len(header.encode())-len(actual.encode()))//len(fake.encode());p.write_text(header+fake*count+actual);before=p.stat().st_size
p=battle(2);rows=list(csv.DictReader(p.open()));real=[v for v in rows if v['battle']!='fixture-old'];assert len(real)==2 and all(v['won']=='1' for v in real);assert len({v['battle'] for v in real})==2;assert len([v for v in rows if v['battle']=='fixture-old'])<count;assert p.stat().st_size<=48000
report={'real_probe_rounds':2,'fixture_not_a_performance_benchmark':True,'won_recorded_after_late_win_event':True,'distinct_battles_preserved':True,'no_duplicate_round_rows':True,'rotation_input_bytes':before,'rotation_output_bytes':p.stat().st_size,'max_bytes':48000,'old_fixture_rows_pruned':True,'production_data_directory':str(p.parent.relative_to(h/'robots')),'class_major':int.from_bytes((h/'robots/equipe/titanprime.class').read_bytes()[6:8],'big'),'records':real}
(r/'results.json').write_text(json.dumps(report,indent=2)+'\n');print(json.dumps(report),flush=True)
