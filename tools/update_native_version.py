#!/usr/bin/env python3
"""
Tool to synchronize version strings embedded in native libTeamTalk5-jni.so binaries.
"""
import glob, sys, re, os

def update_version(new_version):
    base_dir = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
    pattern = os.path.join(base_dir, 'src/main/jniLibs/**/libTeamTalk5-jni.so')
    files = glob.glob(pattern, recursive=True)
    if not files:
        print(f"No .so files found at {pattern}")
        return False

    new_ver_bytes = new_version.encode('ascii') + b'\x00'

    for so_path in files:
        with open(so_path, 'rb') as f:
            data = f.read()

        # Find existing version pattern like 5.27.X\x00
        match = re.search(rb'5\.27\.[0-9]+\x00', data)
        if not match:
            print(f"Warning: version string not found in {so_path}")
            continue

        old_ver_bytes = match.group(0)
        if len(new_ver_bytes) > len(old_ver_bytes) + 4:
            print(f"Error: new version {new_version} is too long for buffer")
            continue

        # Pad or adjust with null bytes to preserve exact section length
        padded_new = new_ver_bytes.ljust(len(old_ver_bytes), b'\x00')
        new_data = data.replace(old_ver_bytes, padded_new)
        assert len(data) == len(new_data), "Length mismatch!"

        with open(so_path, 'wb') as f:
            f.write(new_data)
        print(f"Updated {os.path.basename(os.path.dirname(so_path))}: {old_ver_bytes.decode(errors='ignore').strip(chr(0))} -> {new_version}")

    return True

if __name__ == '__main__':
    ver = sys.argv[1] if len(sys.argv) > 1 else '5.27.12'
    update_version(ver)
