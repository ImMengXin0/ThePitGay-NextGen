import gzip
import hashlib
from pathlib import Path
import struct
import tempfile
import unittest
import uuid
import zlib

from scan_entity_uuids import scan_world


def text(value):
    data = value.encode()
    return struct.pack(">H", len(data)) + data


def tag(kind, name, payload):
    return bytes([kind]) + text(name) + payload


def entity(identifier, legacy=False, passengers=b""):
    result = tag(8, "id", text("minecraft:chicken"))
    if legacy:
        parts = struct.unpack(">2q", identifier.bytes)
        result += tag(4, "UUIDMost", struct.pack(">q", parts[0])) + tag(4, "UUIDLeast", struct.pack(">q", parts[1]))
    else:
        result += tag(11, "UUID", struct.pack(">i", 4) + identifier.bytes)
    result += tag(9, "Pos", bytes([6]) + struct.pack(">i3d", 3, -17, 64, 16))
    if passengers:
        result += tag(9, "Passengers", bytes([10]) + struct.pack(">i", 1) + passengers)
    return result + b"\0"


def region(path, entities, compression=2, legacy=False, external=False):
    payload = tag(9, "Entities", bytes([10]) + struct.pack(">i", len(entities)) + b"".join(entities)) + b"\0"
    if legacy:
        payload = tag(10, "Level", payload) + b"\0"
    data = tag(10, "", payload)
    data = gzip.compress(data) if compression == 1 else zlib.compress(data) if compression == 2 else data
    path.parent.mkdir(parents=True, exist_ok=True)
    header = bytearray(8192)
    header[:4] = struct.pack(">I", (2 << 8) | 1)
    if external:
        (path.parent / "c.0.0.mcc").write_bytes(data)
        data = b""
    chunk = struct.pack(">I", len(data) + 1) + bytes([compression | (128 if external else 0)]) + data
    path.write_bytes(header + chunk.ljust(4096, b"\0"))


class ScannerTest(unittest.TestCase):
    def test_modern_and_legacy_duplicates_without_world_writes(self):
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            key = uuid.uuid4()
            region(root / "entities/r.0.0.mca", [entity(key)])
            region(root / "region/r.-1.0.mca", [entity(key, legacy=True)], compression=1, legacy=True)
            before = {str(p): hashlib.sha256(p.read_bytes()).hexdigest() for p in root.rglob("*.mca")}
            result = scan_world(root)
            self.assertTrue(result["complete"])
            self.assertEqual(2, result["entities"])
            self.assertEqual(2, len(result["duplicates"][str(key)]))
            self.assertEqual(before, {str(p): hashlib.sha256(p.read_bytes()).hexdigest() for p in root.rglob("*.mca")})

    def test_nested_passengers_and_external_uncompressed_chunks(self):
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            key = uuid.uuid4()
            region(root / "entities/r.0.0.mca", [entity(uuid.uuid4(), passengers=entity(key)), entity(key)],
                   compression=3, external=True)
            result = scan_world(root)
            self.assertTrue(result["complete"])
            self.assertEqual(3, result["entities"])
            self.assertIn("Passengers", result["duplicates"][str(key)][0]["index"])

    def test_corrupt_or_unsupported_regions_are_reported_incomplete(self):
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            region(root / "entities/r.0.0.mca", [entity(uuid.uuid4())], compression=4)
            (root / "entities/r.1.0.mca").write_bytes(b"broken")
            result = scan_world(root)
            self.assertFalse(result["complete"])
            self.assertEqual(2, len(result["errors"]))
            self.assertEqual({}, result["duplicates"])

    def test_empty_directory_cannot_be_reported_as_clean_world(self):
        with tempfile.TemporaryDirectory() as temp:
            self.assertFalse(scan_world(temp)["complete"])


if __name__ == "__main__":
    unittest.main()
