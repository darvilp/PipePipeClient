#!/usr/bin/env python3
"""Canonical local/CI entry point for a signed All Features test-drive build."""
import argparse
import json
import os
from pathlib import Path
import re
import shlex
import shutil
import subprocess
import sys

ROOT = Path(__file__).resolve().parents[1]
SIGNING = ('KEY_PATH', 'KEY_STORE_PASSWORD', 'KEY_ALIAS', 'KEY_PASSWORD')
ALIASES = {'KEYSTORE_FILE': 'KEY_PATH', 'KEYSTORE_PASSWORD': 'KEY_STORE_PASSWORD'}
CONFIG_KEYS = set(SIGNING) | set(ALIASES) | {
    'JAVA_HOME', 'ANDROID_HOME', 'ANDROID_SDK_ROOT',
    'PIPEPIPE_RELEASE_OUTPUT', 'UNOFFICIAL_MAX_WORKERS',
}


class ReleaseError(Exception):
    """A diagnostic that contains no private signing data."""


def normalize(values):
    result = dict(values)
    for old, new in ALIASES.items():
        if not result.get(new) and result.get(old):
            result[new] = result[old]
    return result


def read_config(path):
    """Read literal assignments, never execute a private configuration file."""
    try:
        lines = path.read_text().splitlines()
    except OSError:
        raise ReleaseError('Cannot read private release configuration') from None
    values = {}
    for line in lines:
        line = line.strip()
        if not line or line.startswith('#'):
            continue
        if line.startswith('export '):
            line = line[7:]
        name, separator, value = line.partition('=')
        if not separator or name.strip() not in CONFIG_KEYS:
            raise ReleaseError('Unsupported assignment in private release configuration')
        try:
            words = shlex.split(value, comments=True)
        except ValueError:
            raise ReleaseError('Invalid quoting in private release configuration') from None
        if len(words) != 1:
            raise ReleaseError('Expected one quoted literal per configuration assignment')
        values[name.strip()] = words[0]
    return normalize(values)


def load_environment(base):
    env = dict(base)
    config = Path(base.get('PIPEPIPE_RELEASE_ENV') or
                  str(Path(base.get('XDG_CONFIG_HOME', str(Path.home() / '.config')))
                      / 'pipepipe/release.env')).expanduser()
    # CI supplies secrets directly. An explicit override is always honored.
    use_config = bool(base.get('PIPEPIPE_RELEASE_ENV')) or not base.get('CI')
    if use_config and (config.is_file() or base.get('PIPEPIPE_RELEASE_ENV')):
        env = {**read_config(config), **normalize({k: v for k, v in base.items() if v})}
    env = normalize(env)
    missing = [key for key in SIGNING if not env.get(key)]
    if missing:
        raise ReleaseError('Missing signing inputs: ' + ', '.join(missing)
                         + '. See release/README.md for private configuration setup.')
    env['KEY_PATH'] = str(Path(env['KEY_PATH']).expanduser().resolve())
    if not Path(env['KEY_PATH']).is_file():
        raise ReleaseError('Configured signing keystore does not exist')
    return env, config


def prepare_tools(env, manifest):
    java_home = env.get('JAVA_HOME')
    if not java_home:
        local_jdk = Path(f"/usr/lib/jvm/java-{manifest['java_version']}-openjdk-amd64")
        java = shutil.which('java', path=env.get('PATH'))
        java_home = str(local_jdk) if local_jdk.is_dir() else (
            str(Path(java).resolve().parent.parent) if java else '')
    if not java_home:
        raise ReleaseError('Set JAVA_HOME to the manifest JDK version')
    env['JAVA_HOME'] = str(Path(java_home).expanduser().resolve())
    env['PATH'] = str(Path(env['JAVA_HOME']) / 'bin') + os.pathsep + env.get('PATH', '')
    result = subprocess.run([str(Path(env['JAVA_HOME']) / 'bin/java'), '-version'],
                            env=env, capture_output=True, text=True, check=False)
    version = re.search(r'version "(\d+)', result.stderr + result.stdout)
    if result.returncode or not version or int(version[1]) != manifest['java_version']:
        raise ReleaseError('JAVA_HOME does not match the manifest JDK version')
    sdk = Path(env.get('ANDROID_HOME') or env.get('ANDROID_SDK_ROOT')
               or str(Path.home() / 'Android/Sdk')).expanduser().resolve()
    env['ANDROID_HOME'] = env['ANDROID_SDK_ROOT'] = str(sdk)
    if not any((sdk / f"platforms/android-{manifest['compile_sdk']}{suffix}/android.jar").is_file()
               for suffix in ('', '.0')):
        raise ReleaseError('Android SDK is missing the manifest compile platform')
    for tool in ('aapt', 'apksigner', 'zipalign'):
        if not (sdk / 'build-tools' / manifest['build_tools'] / tool).is_file():
            raise ReleaseError('Android SDK is missing the manifest build tools')


def verify_signer(env, manifest):
    result = subprocess.run([
        str(Path(env['JAVA_HOME']) / 'bin/keytool'), '-J-Duser.language=en',
        '-J-Duser.country=US', '-list', '-v', '-keystore', env['KEY_PATH'],
        '-storepass:env', 'KEY_STORE_PASSWORD', '-alias', env['KEY_ALIAS'],
    ], env=env, capture_output=True, text=True, check=False)
    match = re.search(r'SHA256:\s*([A-Fa-f0-9:]+)', result.stdout)
    if (result.returncode or 'PrivateKeyEntry' not in result.stdout or not match
            or match[1].replace(':', '').lower() != manifest['signer_sha256']):
        raise ReleaseError('Private key certificate does not match the release manifest, '
                         'or the keystore could not be unlocked')


def stream_command(command, env, private_values):
    with subprocess.Popen(command, cwd=ROOT, env=env, stdout=subprocess.PIPE,
                          stderr=subprocess.STDOUT, text=True) as process:
        for line in process.stdout:
            for value in sorted(set(private_values), key=len, reverse=True):
                if value:
                    line = line.replace(value, '[private signing input]')
            print(line, end='', flush=True)
        return process.wait()


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--check', action='store_true',
                        help='check signing, toolchain and pinned clean source without building')
    parser.add_argument('--output', type=Path,
                        help='export parent; defaults to E:/apks on WSL when available, else build/unofficial')
    args = parser.parse_args()
    try:
        manifest = json.loads((ROOT / 'release/source-manifest.json').read_text())
        env, config = load_environment(os.environ)
        prepare_tools(env, manifest)
        verify_signer(env, manifest)
    except ReleaseError as error:
        print(f'Release preflight failed: {error}', file=sys.stderr)
        return 1
    except (ValueError, OSError):
        # Diagnostics deliberately exclude exception text: config, keytool and OS
        # errors can contain passwords, aliases or private filesystem paths.
        print('Release preflight failed. Check private config assignments, signing key, '
              'JDK and SDK against release/README.md and source-manifest.json.', file=sys.stderr)
        return 1
    print('Signing certificate and toolchain match the release manifest.', flush=True)
    output = args.output or Path(env.get('PIPEPIPE_RELEASE_OUTPUT') or
                                 ('/mnt/e/apks/pipepipe-all-features'
                                  if Path('/mnt/e/apks').is_dir() and not env.get('CI')
                                  else str(ROOT / 'build/unofficial')))
    command = ['bash', str(ROOT / 'tools/build-unofficial-release.sh')]
    command += ['--check'] if args.check else [str(output.expanduser().resolve())]
    return stream_command(command, env, [str(config), *(env[k] for k in SIGNING)])


if __name__ == '__main__':
    sys.exit(main())
