#!/usr/bin/env python3
"""Archive Titan checkpoints outside Robocode's file quota, without a history cap."""
import argparse
import csv
from datetime import datetime, timezone
import hashlib
import io
import json
import math
import re
from pathlib import Path
import sqlite3
import struct
import time
import zlib

LIMIT = 70000
EXPANDED_LIMIT = 8 * 1024 * 1024
HEADER = struct.Struct('>5i3dqi')


def inspect_snapshot(data):
    if not 64 <= len(data) <= LIMIT:
        raise ValueError('snapshot size')
    if struct.unpack('>Q', data[-8:])[0] != zlib.crc32(data[:-8]):
        raise ValueError('snapshot checksum')
    magic, schema, inputs, hidden, outputs, width, height, cooling, generation, count = HEADER.unpack_from(data)
    if (magic != 0x54504D31 or schema not in (1, 2) or (inputs, hidden, outputs) != (13, 20, 61)
            or generation < 0 or not all(math.isfinite(v) and v > 0 for v in (width, height, cooling))):
        raise ValueError('snapshot header')
    if schema == 2:
        decoder = zlib.decompressobj(16 + zlib.MAX_WBITS)
        body = decoder.decompress(data[56:-8], EXPANDED_LIMIT - 56 + 1)
        if len(body) > EXPANDED_LIMIT - 56 or not decoder.eof or decoder.unused_data:
            raise ValueError('snapshot compression')
    else:
        body = data[56:-8]
    if count < 0 or count > len(body) // 12500:
        raise ValueError('snapshot records')
    stream = io.BytesIO(body)

    def take(n):
        value = stream.read(n)
        if len(value) != n:
            raise ValueError('truncated record')
        return value

    def integer():
        return struct.unpack('>i', take(4))[0]

    def text(maximum):
        length = struct.unpack('>H', take(2))[0]
        if length > maximum * 3:
            raise ValueError('record name size')
        # Robot names and gun labels are normally ASCII; Java uses modified UTF-8.
        raw = take(length).replace(b'\xc0\x80', b'\0')
        return raw.decode('utf-8', errors='surrogatepass')

    names = []
    for _ in range(count):
        name = text(160)
        if not name or name in names:
            raise ValueError('duplicate or empty opponent')
        names.append(name)
        _, samples_seen = struct.unpack('>qq', take(16))
        if samples_seen < 0:
            raise ValueError('negative learning counter')
        for (weight,) in struct.iter_unpack('>f', take(12488)):
            if not math.isfinite(weight) or abs(weight) > 10000:
                raise ValueError('invalid neural weight')
        guns = integer()
        if not 0 <= guns <= 8:
            raise ValueError('gun count')
        for _ in range(guns):
            text(80)
            hits, shots = struct.unpack('>di', take(12))
            if not math.isfinite(hits) or hits < 0 or not 0 <= shots <= 128:
                raise ValueError('invalid gun rating')
        for _ in range(2):
            samples = integer()
            if not 0 <= samples <= 24:
                raise ValueError('sample count')
            for _ in range(samples):
                values = struct.unpack('>14f', take(56))
                if (not all(math.isfinite(v) and abs(v) <= 1000000 for v in values)
                        or not .099 <= values[0] <= 3.001 or values[1] < 0
                        or abs(values[2]) > 8.001 or abs(values[3]) != 1):
                    raise ValueError('invalid defensive sample')
        shots, hits, weighted = struct.unpack('>iid', take(16))
        if not 0 <= hits <= shots <= 128 or not math.isfinite(weighted) or weighted < 0:
            raise ValueError('invalid defensive prior')
    if stream.read(1):
        raise ValueError('trailing record bytes')
    return dict(schema=schema, generation=generation, opponents=names,
                width=width, height=height, cooling=cooling, bytes=len(data))


class Archive:
    def __init__(self, root):
        self.root = Path(root)
        self.root.mkdir(parents=True, exist_ok=True)
        self.db = sqlite3.connect(self.root / 'index.sqlite3')
        self.db.execute('''CREATE TABLE IF NOT EXISTS snapshots
            (sha256 TEXT PRIMARY KEY, captured_utc TEXT NOT NULL, metadata TEXT NOT NULL)''')
        self.db.execute('''CREATE TABLE IF NOT EXISTS rounds
            (battle TEXT NOT NULL, round INTEGER NOT NULL, sha256 TEXT NOT NULL,
             captured_utc TEXT NOT NULL, record TEXT NOT NULL,
             PRIMARY KEY (battle, round, sha256))''')
        self.db.commit()
        self.last_diagnostic_sha = None

    def capture(self, path):
        with Path(path).open('rb') as stream:
            data = stream.read(LIMIT + 1)
        metadata = inspect_snapshot(data)
        digest = hashlib.sha256(data).hexdigest()
        destination = self.root / digest[:2] / (digest + '.dat')
        if not destination.exists():
            destination.parent.mkdir(parents=True, exist_ok=True)
            # Install complete checkpoints atomically, including after a prior interrupted copy.
            temporary = destination.with_suffix('.tmp')
            temporary.write_bytes(data)
            temporary.replace(destination)
        elif hashlib.sha256(destination.read_bytes()).hexdigest() != digest:
            raise ValueError('archive object damaged: ' + str(destination))
        with self.db:
            cursor = self.db.execute('INSERT OR IGNORE INTO snapshots VALUES (?, ?, ?)',
                                     (digest, datetime.now(timezone.utc).isoformat(),
                                      json.dumps(metadata, ensure_ascii=True)))
        return metadata | dict(sha256=digest, added=cursor.rowcount == 1)

    def capture_directory(self, directory):
        captured = []
        for name in ('memoria-a.dat', 'memoria-b.dat'):
            try:
                result = self.capture(Path(directory) / name)
                if result['added']:
                    captured.append(result)
            except (FileNotFoundError, PermissionError, ValueError, zlib.error, UnicodeError):
                # A file being replaced by the robot can be incomplete; retry next poll.
                continue
        return captured

    def close(self):
        self.db.close()

    def capture_diagnostics(self, directory):
        try:
            with (Path(directory) / 'diagnostico.csv').open('rb') as stream:
                data = stream.read(48001)
        except (FileNotFoundError, PermissionError):
            return 0
        digest = hashlib.sha256(data).hexdigest()
        if digest == self.last_diagnostic_sha or len(data) > 48000:
            return 0
        try:
            reader = csv.DictReader(io.StringIO(data.decode('utf-8')))
            required = {'version', 'battle', 'round', 'rounds', 'won', 'energy', 'spent'}
            if not required.issubset(reader.fieldnames or []):
                return 0
            records = list(reader)
            numeric = {'round', 'rounds', 'initial_opponents', 'ticks', 'won', 'energy', 'decisions',
                       'shots', 'hits', 'spent', 'damage_nominal', 'received_nominal', 'bullet_collisions',
                       'walls', 'skipped', 'edge_ticks', 'duel_ticks', 'duel_shots_resolved', 'duel_hits_resolved'}
            for row in records:
                if (None in row or any(value is None for value in row.values())
                        or any(row[key] == '' for key in required)
                        or not row['battle'] or row['won'] not in ('0', '1')
                        or not 1 <= int(row['round']) <= int(row['rounds'])
                        or not all(math.isfinite(float(row[key])) for key in numeric.intersection(row))):
                    return 0
        except (ValueError, UnicodeError, csv.Error):
            return 0
        added = 0
        captured = datetime.now(timezone.utc).isoformat()
        with self.db:
            for row in records:
                payload = json.dumps(row, ensure_ascii=True, sort_keys=True)
                revision = hashlib.sha256(payload.encode()).hexdigest()
                cursor = self.db.execute('INSERT OR IGNORE INTO rounds VALUES (?, ?, ?, ?, ?)',
                                         (row['battle'], int(row['round']), revision, captured, payload))
                added += cursor.rowcount
        self.last_diagnostic_sha = digest
        return added

    def restore(self, digest, directory):
        if not re.fullmatch('[0-9a-f]{64}', digest):
            raise ValueError('restore requires a complete SHA256 checkpoint id')
        source = self.root / digest[:2] / (digest + '.dat')
        data = source.read_bytes()
        if hashlib.sha256(data).hexdigest() != digest:
            raise ValueError('archive object checksum')
        metadata = inspect_snapshot(data)
        self.capture_directory(directory)
        for name in ('memoria-a.dat', 'memoria-b.dat'):
            destination = Path(directory) / name
            temporary = destination.with_suffix('.restore.tmp')
            temporary.write_bytes(data)
            temporary.replace(destination)
        return metadata | dict(restored_sha256=digest)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--data-dir', required=True, type=Path)
    parser.add_argument('--archive-dir', type=Path, default=Path.home() / 'titan-historico')
    parser.add_argument('--interval', type=float, default=0.2)
    parser.add_argument('--once', action='store_true')
    parser.add_argument('--restore', metavar='SHA256', help='restore a checkpoint while Robocode is closed')
    args = parser.parse_args()
    if not 0.05 <= args.interval <= 60:
        parser.error('--interval must be between 0.05 and 60 seconds')
    if not args.data_dir.is_dir():
        parser.error('--data-dir must be the existing titanprime.data directory')
    if args.archive_dir.resolve().is_relative_to(args.data_dir.resolve()):
        parser.error('keep the archive outside the robot data directory')
    archive = Archive(args.archive_dir)
    try:
        if args.restore:
            print(json.dumps(archive.restore(args.restore, args.data_dir), ensure_ascii=True), flush=True)
            return
        while True:
            for checkpoint in archive.capture_directory(args.data_dir):
                print(json.dumps(checkpoint, ensure_ascii=True), flush=True)
            revisions = archive.capture_diagnostics(args.data_dir)
            if revisions:
                print(json.dumps(dict(type='diagnostics', new_revisions=revisions)), flush=True)
            if args.once:
                break
            time.sleep(args.interval)
    except KeyboardInterrupt:
        pass
    finally:
        archive.close()


if __name__ == '__main__':
    main()
