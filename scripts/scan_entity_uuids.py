"""Read-only scan of Minecraft MCA entity UUIDs. Requires Python 3.10+, no packages."""
import argparse
import collections
import io
import json
from pathlib import Path
import re
import struct
import uuid
import zlib

MAX_NBT = 64 * 1024 * 1024


class NbtReader:
    def __init__(self, data):
        self.stream = io.BytesIO(data)

    def read(self, size):
        if size < 0 or size > MAX_NBT:
            raise ValueError("invalid NBT size")
        data = self.stream.read(size)
        if len(data) != size:
            raise ValueError("truncated NBT")
        return data

    def number(self, fmt):
        return struct.unpack(">" + fmt, self.read(struct.calcsize(">" + fmt)))[0]

    def text(self):
        return self.read(self.number("H")).decode("utf-8", errors="replace")

    def payload(self, tag, depth=0):
        if depth > 64:
            raise ValueError("NBT nesting exceeds 64")
        if 1 <= tag <= 6:
            return self.number({1: "b", 2: "h", 3: "i", 4: "q", 5: "f", 6: "d"}[tag])
        if tag == 7:
            return self.read(self.number("i"))
        if tag == 8:
            return self.text()
        if tag == 9:
            child, count = self.number("B"), self.number("i")
            if count < 0 or count > MAX_NBT or child == 0 and count:
                raise ValueError("invalid NBT list")
            return [self.payload(child, depth + 1) for _ in range(count)]
        if tag == 10:
            result = {}
            while True:
                child = self.number("B")
                if child == 0:
                    return result
                name = self.text()
                result[name] = self.payload(child, depth + 1)
        if tag in (11, 12):
            count = self.number("i")
            fmt, width = ("i", 4) if tag == 11 else ("q", 8)
            return list(struct.unpack(">" + str(count) + fmt, self.read(count * width)))
        raise ValueError("unsupported NBT tag " + str(tag))

    def root(self):
        if self.number("B") != 10:
            raise ValueError("NBT root is not a compound")
        self.text()
        return self.payload(10)


def entity_uuid(entity):
    value = entity.get("UUID")
    if isinstance(value, list) and len(value) == 4:
        return str(uuid.UUID(bytes=struct.pack(">4I", *(part & 0xFFFFFFFF for part in value))))
    if isinstance(value, str):
        return str(uuid.UUID(value))
    if "UUIDMost" in entity and "UUIDLeast" in entity:
        return str(uuid.UUID(int=((entity["UUIDMost"] & ((1 << 64) - 1)) << 64)
                             | (entity["UUIDLeast"] & ((1 << 64) - 1))))
    return None


def entities_in(root):
    entities = root.get("Entities", root.get("Level", {}).get("Entities", []))
    def visit(entity, index, depth=0):
        if depth > 64 or not isinstance(entity, dict):
            raise ValueError("invalid entity/passenger data")
        yield entity, index
        for i, passenger in enumerate(entity.get("Passengers", [])):
            yield from visit(passenger, index + "/Passengers/" + str(i), depth + 1)
    for i, entity in enumerate(entities):
        yield from visit(entity, "Entities/" + str(i))


def decompress(data, compression):
    if compression == 3:
        if len(data) > MAX_NBT:
            raise ValueError("NBT exceeds 64 MiB")
        return data
    if compression not in (1, 2):
        raise ValueError("unsupported compression " + str(compression) + " (including LZ4)")
    decoder = zlib.decompressobj(31 if compression == 1 else 15)
    result = decoder.decompress(data, MAX_NBT + 1)
    if len(result) > MAX_NBT or not decoder.eof:
        raise ValueError("compressed NBT is incomplete or exceeds 64 MiB")
    return result


def scan_world(world):
    world = Path(world).resolve(strict=True)
    if not world.is_dir():
        raise ValueError("world must be a directory")
    seen, duplicates = {}, collections.defaultdict(list)
    report = {"world": str(world), "read_only": True, "complete": True,
              "region_files": 0, "chunks": 0, "entities": 0, "duplicates": {}, "errors": []}
    for path in sorted(world.rglob("r.*.*.mca")):
        if path.parent.name not in ("entities", "region"):
            continue
        if not path.resolve().is_relative_to(world):
            continue
        match = re.fullmatch(r"r\.(-?\d+)\.(-?\d+)\.mca", path.name)
        if not match:
            continue
        rx, rz = map(int, match.groups())
        report["region_files"] += 1
        source = str(path.relative_to(world))
        try:
            with path.open("rb") as stream:
                header = stream.read(8192)
                if len(header) != 8192:
                    raise ValueError("truncated region header")
                for slot in range(1024):
                    location = int.from_bytes(header[slot * 4:slot * 4 + 4], "big")
                    if not location:
                        continue
                    cx, cz = rx * 32 + slot % 32, rz * 32 + slot // 32
                    try:
                        offset, sectors = location >> 8, location & 255
                        if offset < 2 or sectors == 0:
                            raise ValueError("invalid region location")
                        stream.seek(offset * 4096)
                        length = struct.unpack(">I", stream.read(4))[0]
                        if not 1 <= length <= sectors * 4096 - 4:
                            raise ValueError("invalid region chunk length")
                        compression = stream.read(1)[0]
                        if compression & 128:
                            external = path.parent / ("c." + str(cx) + "." + str(cz) + ".mcc")
                            if external.stat().st_size > MAX_NBT:
                                raise ValueError("external chunk exceeds 64 MiB")
                            data = external.read_bytes()
                        else:
                            data = stream.read(length - 1)
                            if len(data) != length - 1:
                                raise ValueError("truncated region chunk")
                        root = NbtReader(decompress(data, compression & 127)).root()
                        report["chunks"] += 1
                        for entity, index in entities_in(root):
                            report["entities"] += 1
                            key = entity_uuid(entity)
                            if key is None:
                                continue
                            entry = {"file": source, "chunk": [cx, cz], "index": index,
                                     "type": entity.get("id"), "position": entity.get("Pos")}
                            if key in seen:
                                if not duplicates[key]:
                                    duplicates[key].append(seen[key])
                                duplicates[key].append(entry)
                            else:
                                seen[key] = entry
                    except (OSError, ValueError, struct.error, IndexError, TypeError, zlib.error) as error:
                        report["errors"].append({"file": source, "chunk": [cx, cz], "error": str(error)})
        except (OSError, ValueError) as error:
            report["errors"].append({"file": source, "error": str(error)})
    report["duplicates"] = dict(duplicates)
    report["complete"] = report["region_files"] > 0 and not report["errors"]
    return report


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("world", type=Path, help="stopped world COPY directory")
    args = parser.parse_args()
    report = scan_world(args.world)
    print(json.dumps(report, ensure_ascii=False, indent=2))
    return 0 if report["complete"] else 2


if __name__ == "__main__":
    raise SystemExit(main())
