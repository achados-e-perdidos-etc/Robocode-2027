#!/usr/bin/env python3
"""Audita fontes, nomes, links locais, JSONs, sintaxe Python e integridade dos ZIPs."""
import argparse
import ast
import hashlib
import json
from pathlib import Path
import re
import zipfile

ROOT=Path(__file__).resolve().parents[1]


def main():
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--output',type=Path)
    args=parser.parse_args()
    files=sorted(p for p in ROOT.rglob('*') if p.is_file() and not
                 any(part in ('.git','__pycache__','dist') for part in p.relative_to(ROOT).parts))
    errors=[];counts=dict(markdown=0,python=0,json=0,zip=0,battles=0)
    for p in files:
        rel=str(p.relative_to(ROOT))
        if rel.lower()!=rel:errors.append('Uppercase path: '+rel)
        if p.suffix=='.py':
            counts['python']+=1
            try:ast.parse(p.read_text(),filename=rel)
            except SyntaxError as e:errors.append(str(e))
        elif p.suffix=='.json':
            counts['json']+=1
            try:json.loads(p.read_text())
            except ValueError as e:errors.append(rel+': '+str(e))
        elif p.suffix=='.md':
            counts['markdown']+=1
            for target in re.findall(r'\[[^\]]*\]\(([^)]+)\)',p.read_text()):
                target=target.split('#',1)[0]
                if target and ':' not in target and not (p.parent/target).exists():
                    errors.append(rel+': broken local link '+target)
        elif p.suffix=='.zip':
            counts['zip']+=1
            with zipfile.ZipFile(p) as z:
                bad=z.testzip()
                if bad:errors.append(rel+': CRC failure '+bad)
        elif p.suffix=='.battle':
            counts['battles']+=1
            if 'equipe.titanprime*' not in p.read_text():errors.append(rel+': missing canonical robot')
    java=[str(p.relative_to(ROOT)) for p in files if p.suffix=='.java']
    if java!=['robots/equipe/titanprime.java']:errors.append('Unexpected Java source set: '+str(java))
    source=ROOT/'robots/equipe/titanprime.java'
    report=dict(passed=not errors,files_inspected=len(files),checks=counts,java_sources=java,
                source_sha256=hashlib.sha256(source.read_bytes()).hexdigest(),errors=errors,
                scope='Structural integrity, not a claim to execute historical 3.x experiments against the 4.x API')
    if args.output:args.output.write_text(json.dumps(report,indent=2)+'\n')
    print(json.dumps(report,indent=2));raise SystemExit(0 if not errors else 1)


if __name__=='__main__':main()
