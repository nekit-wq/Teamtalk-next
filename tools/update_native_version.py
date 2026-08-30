#!/usr/bin/env python3
"""
Tool to synchronize version strings embedded in native libTeamTalk5-jni.so binaries.
"""
import glob, sys, os

def update_version(new_version):
    base_dir = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
    pattern = os.path.join(base_dir, 'src/main/jniLibs/**/libTeamTalk5-jni.so')
    files = glob.glob(pattern, recursive=True)
    if not files:
        print(f"No .so files found at {pattern}")
        return False

    new_ver_bytes = new_version.encode('ascii')
    if len(new_ver_bytes) > 11:
        new_ver_bytes = new_ver_bytes[:11]

    # 12 bytes buffer padded with nulls
    write_buf = new_ver_bytes.ljust(12, b'\x00')

    for so_path in files:
        with open(so_path, 'rb') as f:
            data = bytearray(f.read())

        arch = os.path.basename(os.path.dirname(so_path))
        target_offset = -1

        if arch == 'arm64-v8a':
            idx = data.find(b'abusePrevent\x00')
            if idx != -1:
                target_offset = idx + len(b'abusePrevent\x00')
        else:
            idx = data.find(b'webm_vp8\x00')
            if idx != -1:
                target_offset = idx + len(b'webm_vp8\x00')

        if target_offset != -1:
            data[target_offset:target_offset+12] = write_buf
            with open(so_path, 'wb') as f:
                f.write(data)
            print(f"Updated {arch} at 0x{target_offset:x} -> {new_version}")
        else:
            print(f"Warning: anchor not found in {so_path}")

    return True

if __name__ == '__main__':
    ver = sys.argv[1] if len(sys.argv) > 1 else '5.27.16'
    update_version(ver)
