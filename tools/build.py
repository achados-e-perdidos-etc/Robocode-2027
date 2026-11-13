#!/usr/bin/env python3
"""Compila o Java unico e empacota todas as classes internas em titanprime.jar."""
import argparse
import hashlib
import json
from pathlib import Path
import re
import subprocess
import tempfile
import zipfile

ROOT=Path(__file__).resolve().parents[1]


def main():
    p=argparse.ArgumentParser(description=__doc__)
    p.add_argument('--api',required=True,type=Path)
    p.add_argument('--source',type=Path,default=ROOT/'robots/equipe/titanprime.java')
    p.add_argument('--output',type=Path,default=ROOT/'dist/titanprime.jar')
    p.add_argument('--ecj',type=Path)
    a=p.parse_args()
    version=re.search(r'DIAGNOSTIC_VERSION = "([^"]+)"',a.source.read_text()).group(1)
    with tempfile.TemporaryDirectory() as tmp:
        classes=Path(tmp)/'classes';classes.mkdir()
        compiler=['java','-jar',str(a.ecj.resolve()),'-8','-nowarn'] if a.ecj else ['javac','--release','8']
        subprocess.run(compiler+['-encoding','UTF-8','-cp',str(a.api.resolve()),'-d',str(classes),str(a.source.resolve())],check=True,timeout=90)
        compiled=sorted(classes.rglob('*.class'))
        assert compiled and all(int.from_bytes(f.read_bytes()[6:8],'big')==52 for f in compiled)
        assert all(str(f.relative_to(classes)).startswith('equipe/titanprime') for f in compiled)
        a.output.parent.mkdir(parents=True,exist_ok=True)
        properties=('robot.classname=equipe.titanprime\nrobot.name=Titan Prime\nrobot.version='+version
                    +'\nrobot.author.name=Projeto Titan; original engine by Voidious; kd-tree by Rednaxela\n'
                    'robot.description=Altered derivative of the open-source Diamond engine. Single Java source.\n')
        with zipfile.ZipFile(a.output,'w',zipfile.ZIP_DEFLATED) as z:
            for f in compiled:
                info=zipfile.ZipInfo(str(f.relative_to(classes)),date_time=(2026,1,1,0,0,0))
                info.compress_type=zipfile.ZIP_DEFLATED;z.writestr(info,f.read_bytes())
            for name,contents in [('equipe/titanprime.properties',properties)] + [
                    ('licenses/'+license.name,license.read_text()) for license in sorted((ROOT/'licenses').glob('*.txt'))]:
                info=zipfile.ZipInfo(name,date_time=(2026,1,1,0,0,0));info.compress_type=zipfile.ZIP_DEFLATED
                z.writestr(info,contents)
        report=dict(source_sha256=hashlib.sha256(a.source.read_bytes()).hexdigest(),
                    jar_sha256=hashlib.sha256(a.output.read_bytes()).hexdigest(),
                    class_major=52,classes=len(compiled),version=version)
        a.output.with_suffix('.json').write_text(json.dumps(report,indent=2)+'\n')
        print(json.dumps(report))


if __name__=='__main__':main()
