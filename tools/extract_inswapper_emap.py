#!/usr/bin/env python3
"""Extract the 512x512 float32 EMAP matrix from inswapper_128.onnx.

Uses only Python's standard library; no ONNX/protobuf package or model download is needed.
"""
import argparse
import struct
from pathlib import Path


def varint(f):
    value = 0
    shift = 0
    while True:
        b = f.read(1)
        if not b:
            raise EOFError("unexpected end of protobuf")
        n = b[0]
        value |= (n & 0x7f) << shift
        if not n & 0x80:
            return value
        shift += 7
        if shift > 70:
            raise ValueError("invalid protobuf varint")


def skip_value(f, wire):
    if wire == 0:
        varint(f)
    elif wire == 1:
        f.seek(8, 1)
    elif wire == 2:
        f.seek(varint(f), 1)
    elif wire == 5:
        f.seek(4, 1)
    else:
        raise ValueError(f"unsupported protobuf wire type {wire}")


def read_bytes(f, length):
    data = f.read(length)
    if len(data) != length:
        raise EOFError("unexpected end of protobuf message")
    return data


def parse_tensor(data):
    """Return raw float32 bytes if tensor is exactly [512,512] FLOAT."""
    from io import BytesIO
    f = BytesIO(data)
    dims = []
    data_type = None
    raw = None
    while f.tell() < len(data):
        key = varint(f)
        field, wire = key >> 3, key & 7
        if field == 1 and wire == 0:
            dims.append(varint(f))
        elif field == 2 and wire == 0:
            data_type = varint(f)
        elif field == 9 and wire == 2:
            raw = read_bytes(f, varint(f))
        else:
            skip_value(f, wire)
    if dims == [512, 512] and data_type == 1 and raw is not None and len(raw) == 512 * 512 * 4:
        return raw
    return None


def extract(model_path, output_path):
    candidate = None
    with open(model_path, "rb") as f:
        while True:
            key_bytes = f.read(1)
            if not key_bytes:
                break
            f.seek(-1, 1)
            key = varint(f)
            field, wire = key >> 3, key & 7
            if field == 7 and wire == 2:  # ModelProto.graph
                graph_len = varint(f)
                graph_end = f.tell() + graph_len
                while f.tell() < graph_end:
                    gkey = varint(f)
                    gfield, gwire = gkey >> 3, gkey & 7
                    if gfield == 5 and gwire == 2:  # GraphProto.initializer
                        tensor_len = varint(f)
                        tensor = read_bytes(f, tensor_len)
                        found = parse_tensor(tensor)
                        if found is not None:
                            candidate = found
                    else:
                        skip_value(f, gwire)
                if f.tell() != graph_end:
                    raise ValueError("graph protobuf boundary mismatch")
            else:
                skip_value(f, wire)

    if candidate is None:
        raise RuntimeError("No 512x512 float32 initializer was found in the model.")
    Path(output_path).write_bytes(candidate)
    print(f"Wrote {output_path} ({len(candidate)} bytes)")


if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("model", help="path to inswapper_128.onnx")
    parser.add_argument("output", help="path for inswapper_emap.bin")
    args = parser.parse_args()
    extract(Path(args.model), Path(args.output))
