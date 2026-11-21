from pathlib import Path
import argparse,hashlib,json,os,shutil,subprocess,tempfile,zipfile
repo=Path(__file__).resolve().parents[2]
p=argparse.ArgumentParser();p.add_argument('--api',type=Path,required=True);p.add_argument('--data-zip',type=Path,required=True);p.add_argument('--base',type=Path,default=repo/'robots/equipe/titanprime.java');p.add_argument('--output',type=Path);a=p.parse_args()
source=repo/'variants/aprendizado-pc/equipe/titanprime.java';probe=Path(__file__).with_name('memoryprobe.java')
compiler=['javac','--release','8'] if shutil.which('javac') else ['java','-m','jdk.compiler/com.sun.tools.javac.Main','-source','8','-target','8','-Xlint:-options']
with tempfile.TemporaryDirectory(prefix='titan-memory-') as tmp:
 root=Path(tmp);(root/'uploaded').mkdir();classes=root/'classes';classes.mkdir();base=root/'base';base.mkdir()
 with zipfile.ZipFile(a.data_zip) as z:
  for n in ['memoria-a.dat','memoria-b.dat']:
   info=z.getinfo('titanprime.data/'+n);assert info.file_size<=70000;(root/'uploaded'/n).write_bytes(z.read(info))
 def run(cmd):return subprocess.run(cmd,check=True,capture_output=True,text=True,timeout=120).stdout
 run(compiler+['-encoding','UTF-8','-cp',str(a.api.resolve()),'-d',str(classes),str(source),str(probe)])
 current=run(['java','-cp',str(classes)+os.pathsep+str(a.api.resolve()),'equipe.memoryprobe',str(root)])
 run(compiler+['-encoding','UTF-8','-cp',str(a.api.resolve()),'-d',str(base),str(a.base.resolve())])
 old=run(['java','-cp',str(base)+os.pathsep+str(classes)+os.pathsep+str(a.api.resolve()),'equipe.memoryprobe',str(root),'baseline'])
 assert 'checks=39' in current and 'checks=3' in old
 report={'passed':True,'source_sha256':hashlib.sha256(source.read_bytes()).hexdigest(),'production_output':current,'base_compatibility_output':old,'battles_run':0}
 if a.output:a.output.write_text(json.dumps(report,indent=2)+'\n')
 print(json.dumps(report,indent=2))
