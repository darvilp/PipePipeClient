#!/usr/bin/env python3
"""Verify release identity, native ABI, alignment and signer from the actual APK."""
import argparse
import hashlib
import json
from pathlib import Path
import re
import subprocess
import zipfile

parser = argparse.ArgumentParser()
parser.add_argument('apk', type=Path)
parser.add_argument('--sdk', type=Path, required=True)
parser.add_argument('--abi', default='arm64-v8a', choices=['arm64-v8a', 'armeabi-v7a', 'x86_64', 'x86'])
parser.add_argument('--output-json', type=Path)
args = parser.parse_args()
root = Path(__file__).resolve().parents[1]
manifest = json.loads((root / 'release/source-manifest.json').read_text())
build_tools = args.sdk / 'build-tools' / manifest['build_tools']
def output(*command):
    return subprocess.check_output([str(arg) for arg in command], text=True)
def require(condition, message):
    if not condition:
        raise SystemExit(message)

badging = output(build_tools / 'aapt', 'dump', 'badging', args.apk)
package_line = next(line for line in badging.splitlines() if line.startswith('package:'))
attributes = dict(re.findall(r"(\w+)='([^']*)'", package_line))
abi_codes = {'armeabi-v7a': 1, 'x86': 2, 'x86_64': 3, 'arm64-v8a': 4}
version_code = manifest['base_version_code'] * 100 + abi_codes[args.abi]
require(attributes['name'] == manifest['application_id'], 'Unexpected application ID')
require(attributes['versionName'] == manifest['version_name'], 'Unexpected version name')
require(int(attributes['versionCode']) == version_code, 'Unexpected ABI version code')
require('application-debuggable' not in badging, 'Release APK is debuggable')
require("application-label:'PipePipe All Features (Unofficial)'" in badging, 'Unexpected app label')
require("sdkVersion:'23'" in badging and "targetSdkVersion:'36'" in badging, 'Unexpected SDK range')
with zipfile.ZipFile(args.apk) as archive:
    native_abis = {name.split('/')[1] for name in archive.namelist()
                   if name.startswith('lib/') and name.endswith('.so')}
require(native_abis == {args.abi}, f'Unexpected native ABIs: {sorted(native_abis)}')
subprocess.run([str(build_tools / 'zipalign'), '-c', '-P', '16', '4', str(args.apk)], check=True)
certificates = output(build_tools / 'apksigner', 'verify', '--verbose', '--print-certs', args.apk)
signers = re.findall(r'Signer #\d+ certificate SHA-256 digest: (\w+)', certificates)
require(signers == [manifest['signer_sha256']], 'APK signer does not match the established all-features signer')
with args.apk.open('rb') as stream:
    apk_sha256 = hashlib.file_digest(stream, 'sha256').hexdigest()
record = {
    'apk': args.apk.name, 'sha256': apk_sha256,
    'application_id': attributes['name'], 'version_name': attributes['versionName'],
    'version_code': version_code, 'abi': args.abi, 'debuggable': False,
    'signer_sha256': signers[0], 'alignment_page_size_kb': 16,
}
if args.output_json:
    with args.output_json.open('x') as destination:
        json.dump(record, destination, indent=2)
        destination.write('\n')
print(json.dumps(record, indent=2))
