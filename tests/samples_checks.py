#!/usr/bin/env python3
"""O auditor nao confunde pontos, participantes de equipe ou falhas com vitorias."""
from pathlib import Path
import math
import sys
sys.path.insert(0, str(Path(__file__).resolve().parents[1] / 'tools'))
import samples


def block(team=False):
    own = 'treino.probe*'
    names = ['sampleteam.MyFirstLeader'] + ['sampleteam.MyFirstDroid (' + str(i) + ')' for i in range(1, 5)] if team else ['sample.Walls']
    setup = list(map(float, samples.positions({'physical_count': 6 if team else 2, 'seed': 401,
                                              'category': 'team' if team else 'duel'}).split(',')))
    lines = ['[' + own + '] COLD_START gun=1228 surf=128',
             'ROSTER\t' + own + '\t' + own + '\t0\t-1\tfalse\t100\tACTIVE\t' + str(setup[0]) + '\t' + str(setup[1])]
    for i, name in enumerate(names):
        lines.append('ROSTER\t' + name + '\t' + ('sampleteam.MyFirstTeam (1)' if team else name) + '\t1\t' + ('0' if team else '-1') + '\tfalse\t100\tACTIVE\t700\t500')
    for i, wins in enumerate((0, 1, 2)):
        for j, name in enumerate([own] + names):
            lines.append('START_POSITION\t' + str(i) + '\t' + name + '\t' + str(setup[j*3]) + '\t' + str(setup[j*3+1]) + '\t' + str(math.radians(setup[j*3+2])))
        lines += ['ROUND_TURNS\t' + str(i) + '\t300',
                  'ROUND_SCORE\t' + str(i) + '\t' + own + '\t' + str(wins),
                  '[' + own + '] TitanPrime round=' + str(i) + ' shots=1 hits=1 waves=2 matched=1 walls=0 skipped=0',
                  '[' + own + '] AUDIT_SHOT sample.Walls']
    lines += ['RESULT\t' + own + '\t100\t2',
              'RESULT\t' + ('sampleteam.MyFirstTeam (1)' if team else 'sample.Walls') + '\t999\t1',
              'STATUS\tcomplete']
    return '\n'.join(lines) + '\n'


def main():
    case = {'key': 'probe', 'category': 'duel', 'physical_count': 2, 'seed': 401}
    row = samples.parse_block(block(), case, 'probe')
    assert row['series_win'] and row['round_wins'] == [0, 1, 1]
    assert row['score_share'] < 0.1 and row['telemetry_complete']
    assert row['shots'] == 3 and row['eligible']
    assert row['positions_verified']
    wrong = samples.parse_block(block().replace('START_POSITION\t0\t' + 'treino.probe*\t', 'START_POSITION\t3\t' + 'treino.probe*\t'), case, 'probe')
    assert wrong['status'] == 'invalid' and wrong['series_win'] is None
    broken = samples.parse_block(block() + 'java.lang.InstantiationException: sampleex.Slave\n', case, 'probe')
    assert broken['status'] == 'invalid' and broken['series_win'] is None and not broken['eligible']
    missing = samples.parse_block(block().replace('ROSTER\tsample.Walls', 'MISSING\tsample.Walls'), case, 'probe')
    assert missing['status'] == 'invalid' and missing['series_win'] is None
    interactive = samples.parse_block(block(), {**case, 'category': 'interactive'}, 'probe')
    assert interactive['status'] == 'complete' and not interactive['eligible']
    team = samples.parse_block(block(True), {**case, 'category': 'team', 'physical_count': 6}, 'probe')
    assert team['series_win'] and len(team['scores']) == 2 and len(team['roster']) == 6
    try:
        samples.parse_block(block(True).replace('\t1\t0\tfalse', '\t1\t-1\tfalse'),
                            {**case, 'category': 'team', 'physical_count': 6}, 'probe')
    except ValueError:
        pass
    else:
        raise AssertionError('Independent opponents counted as a team')
    duplicate = block().replace('COLD_START gun=1228 surf=128', 'COLD_START gun=1228 surf=128\n[treino.probe*] COLD_START gun=2000 surf=200')
    try:
        samples.parse_block(duplicate, case, 'probe')
    except ValueError:
        pass
    else:
        raise AssertionError('Multiple cold starts accepted')
    post = samples.parse_block(block() + '[treino.probe*] AUDIT_SHOT samplesentry.BorderGuard\n', case, 'probe')
    assert post['sentry_shots'] == 1 and post['unreported_shots'] == 1 and post['shots'] == 4
    expected = set(samples.FIGHTERS + samples.DEMOS + samples.INTERACTIVE + samples.DEPENDENT +
                   ['sampleex.Slave', 'samplesentry.BorderGuard', 'sampleteam.MyFirstTeam'])
    covered = {n for r in samples.cases() for n in r['opponents'].split(',')}
    assert expected == covered and len(expected) == 25
    assert all(r['physical_count'] == 6 for r in samples.cases() if r['category'] == 'team')
    print('SAMPLES CHECKS PASSED: BO3 over points, 5-member teams, initialization failures, telemetry and 25 entries')


if __name__ == '__main__':
    main()
