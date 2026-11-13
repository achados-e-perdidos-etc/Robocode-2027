"""Checks checkpoint history retention and recovery without running a second robot."""
import argparse
import importlib.util
import json
from pathlib import Path
import struct
import tempfile
import zlib


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--snapshot', required=True, type=Path)
    parser.add_argument('--output', required=True, type=Path)
    args = parser.parse_args()
    module_path = Path(__file__).resolve().parents[1] / 'tools/archive_learning.py'
    spec = importlib.util.spec_from_file_location('archive_learning', module_path)
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    original = args.snapshot.read_bytes()
    metadata = module.inspect_snapshot(original)
    assert metadata['opponents']
    with tempfile.TemporaryDirectory() as directory:
        root = Path(directory)
        data = root / 'robot.data'
        data.mkdir()
        archive = module.Archive(root / 'history')
        for generation in range(1, 41):
            raw = bytearray(original[:-8])
            struct.pack_into('>q', raw, 44, generation)
            raw.extend(struct.pack('>Q', zlib.crc32(raw)))
            (data / ('memoria-a.dat' if generation % 2 else 'memoria-b.dat')).write_bytes(raw)
            captured = archive.capture_directory(data)
            assert len(captured) == 1
        assert not archive.capture_directory(data)
        count = archive.db.execute('SELECT count(*) FROM snapshots').fetchone()[0]
        assert count == 40
        archive.close()
        archive = module.Archive(root / 'history')
        assert not archive.capture_directory(data)
        (data / 'memoria-a.dat').write_bytes(b'partial')
        damaged = bytearray(original)
        damaged[-1] ^= 1
        (data / 'memoria-b.dat').write_bytes(damaged)
        assert not archive.capture_directory(data)
        assert archive.db.execute('SELECT count(*) FROM snapshots').fetchone()[0] == 40
        objects = list((root / 'history').glob('*/*.dat'))
        assert len(objects) == 40
        for path in objects:
            module.inspect_snapshot(path.read_bytes())
        restored = archive.restore(objects[0].stem, data)
        assert (data / 'memoria-a.dat').read_bytes() == objects[0].read_bytes()
        assert (data / 'memoria-b.dat').read_bytes() == objects[0].read_bytes()
        assert restored['opponents'] == metadata['opponents']
        archive.close()
    report = dict(passed=True, schema=metadata['schema'], checkpoints_retained=40,
                  snapshots_in_robot_directory=2, restarted_deduplication=True,
                  partial_and_corrupt_files_ignored=True, all_archived_objects_valid=True,
                  archived_checkpoint_restored=True,
                  scope='Storage integrity; does not establish combat performance or capture every raw observation.')
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(json.dumps(report, indent=2) + '\n')
    print(json.dumps(report))


if __name__ == '__main__':
    main()
