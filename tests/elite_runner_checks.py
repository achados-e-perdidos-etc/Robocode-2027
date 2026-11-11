#!/usr/bin/env python3
"""O validador deve rejeitar resultados incompletos ou adversarios comprometidos."""
import argparse
import json
from pathlib import Path
import re
import sys
import tempfile

ROOT=Path(__file__).resolve().parents[1]
sys.path.insert(0,str(ROOT/'tools'))
from elite_duel import parse_result


def main():
    p=argparse.ArgumentParser(description=__doc__)
    p.add_argument('--log',required=True,type=Path)
    p.add_argument('--suite',required=True,type=Path)
    p.add_argument('--output',type=Path)
    args=p.parse_args()
    key,names,rounds,positions=args.suite.read_text().strip().split('|')
    names=names.split(',');rounds=int(rounds);coords=None if positions=='random' else list(map(float,positions.split(',')))
    original=args.log.read_text()
    mutations={
        'engine_error':original+'\nERROR:\tinvalid wave\n',
        'robot_exception':original+'\njava.lang.IllegalStateException: fixture\n',
        'opponent_memory_error':original+'\njava.lang.OutOfMemoryError: fixture\n',
        'opponent_stopped':original+'\nUnable to stop thread: voidious.Diamond\n',
        'missing_round':re.sub(r'^ROUND_TURNS\t0\t[^\n]+\n','',original,flags=re.M),
        'wrong_robot':original.replace('voidious.Diamond 1.8.28','fixture.Wrong'),
        'team_ally':original.replace('ROSTER\tequipe.titanprime*\tequipe.titanprime*\t0\t-1\t','ROSTER\tequipe.titanprime*\tequipe.titanprime*\t0\t0\t'),
        'incomplete_battle':original.replace('STATUS\tcomplete','STATUS\terror'),
        'bad_initial_position':re.sub(r'(START_POSITION\t0\tequipe.titanprime\*\t)[^\t]+',r'\g<1>10',original),
        'non_finite_position':re.sub(r'(START_POSITION\t0\tequipe.titanprime\*\t)[^\t]+',r'\g<1>NaN',original),
    }
    with tempfile.TemporaryDirectory() as temp:
        log=Path(temp)/'fixture.log';log.write_text(original)
        assert parse_result(log,rounds,names,coords)['valid'],'Reference battle must be valid'
        for label,text in mutations.items():
            assert text!=original,label+' mutation did not apply'
            log.write_text(text)
            assert not parse_result(log,rounds,names,coords)['valid'],label+' accepted'
    report={'passed':True,'reference':key,'rejected_mutations':list(mutations)}
    if args.output:args.output.write_text(json.dumps(report,indent=2)+'\n')
    print(json.dumps(report))


if __name__=='__main__':main()
