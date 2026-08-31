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
        arch = os.path.basename(os.path.dirname(so_path))
        with open(so_path, 'rb') as f:
            data = bytearray(f.read())

        anchors = [b'nAudioFmt\x00', b'abusePrevent\x00', b'webm_vp8\x00']
        for anchor in anchors:
            idx = data.find(anchor)
            if idx != -1:
                target_offset = idx + len(anchor)
                data[target_offset:target_offset+12] = write_buf
                print(f"Updated {arch} at 0x{target_offset:x} ({anchor.decode(errors='ignore').strip(chr(0))}) -> {new_version}")

        with open(so_path, 'wb') as f:
            f.write(data)

    return True

if __name__ == '__main__':
    ver = sys.argv[1] if len(sys.argv) > 1 else '5.28.0'
    update_version(ver)
