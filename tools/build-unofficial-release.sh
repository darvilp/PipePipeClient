#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "${BASH_SOURCE[0]}")/.."
release_root=$(pwd -P)
for signing_variable in KEY_PATH KEY_STORE_PASSWORD KEY_ALIAS KEY_PASSWORD; do
    if [[ -z ${!signing_variable:-} ]]; then
        echo "Missing required signing variable: $signing_variable" >&2
        exit 1
    fi
done
[[ -f "$KEY_PATH" ]] || { echo 'Signing keystore does not exist' >&2; exit 1; }
release_sdk=${ANDROID_HOME:-${ANDROID_SDK_ROOT:-}}
[[ -d "$release_sdk" ]] || { echo 'Set ANDROID_HOME to the Android SDK' >&2; exit 1; }
release_source=$(git rev-parse HEAD)
[[ -z $(git status --porcelain) ]] || { echo 'Client source must be clean' >&2; exit 1; }
[[ -z $(git -C ../PipePipeExtractor status --porcelain) ]] || { echo 'Extractor source must be clean' >&2; exit 1; }
python3 - <<'PY'
import hashlib, json, subprocess
from pathlib import Path
m = json.loads(Path('release/source-manifest.json').read_text())
actual = subprocess.check_output(['git', '-C', '../PipePipeExtractor', 'rev-parse', 'HEAD'], text=True).strip()
if actual != m['extractor_commit']:
    raise SystemExit('Extractor revision does not match release/source-manifest.json')
subprocess.run(['git', 'merge-base', '--is-ancestor', m['integration_commit'], 'HEAD'], check=True)
with open('ffmpeg/ffmpeg-kit.aar', 'rb') as stream:
    if hashlib.file_digest(stream, 'sha256').hexdigest() != m['ffmpeg_aar_sha256']:
        raise SystemExit('FFmpeg AAR does not match pinned source hash')
PY
./gradlew :app:testDebugUnitTest :app:assembleRelease :app:lintRelease \
    --max-workers="${UNOFFICIAL_MAX_WORKERS:-2}" --console=plain
python3 tools/check-release-lint.py app/build/reports/lint-results-release.xml
[[ "$release_source" == $(git rev-parse HEAD) && -z $(git status --porcelain) ]] || {
    echo 'Client source changed during build' >&2; exit 1;
}
release_extractor=$(python3 -c 'import json; print(json.load(open("release/source-manifest.json"))["extractor_commit"])')
[[ -z $(git -C ../PipePipeExtractor status --porcelain) && "$release_extractor" == $(git -C ../PipePipeExtractor rev-parse HEAD) ]] || {
    echo 'Extractor source changed during build' >&2; exit 1;
}
release_version=$(python3 -c 'import json; print(json.load(open("release/source-manifest.json"))["version_name"])')
release_timestamp=$(date -u +%Y%m%dT%H%M%SZ)
release_short=${release_source:0:9}
release_output_parent=${1:-"$release_root/build/unofficial"}
mkdir -p "$release_output_parent"
release_output="$release_output_parent/$release_version-$release_short-$release_timestamp"
mkdir "$release_output"
release_apk_name="PipePipe-all-features-$release_version-$release_short-$release_timestamp-arm64-v8a.apk"
release_apk="$release_output/$release_apk_name"
cp "app/build/outputs/apk/release/PipePipe_$release_version-arm64-v8a-release.apk" "$release_apk"
python3 tools/verify-unofficial-apk.py "$release_apk" --sdk "$release_sdk" \
    --output-json "$release_output/apk-manifest.json"
cp release/source-manifest.json "$release_output/source-manifest.json"
cp release/RELEASE_NOTES.md "$release_output/RELEASE_NOTES.md"
python3 - "$release_output" "$release_source" <<'PY'
import hashlib, json, sys
from pathlib import Path
out = Path(sys.argv[1])
p = out / 'source-manifest.json'
m = json.loads(p.read_text())
m['client_commit'] = sys.argv[2]
p.write_text(json.dumps(m, indent=2) + '\n')
with (out / 'SHA256SUMS').open('x') as sums:
    for file in sorted(out.iterdir()):
        if file.name == 'SHA256SUMS':
            continue
        with file.open('rb') as stream:
            sums.write(hashlib.file_digest(stream, 'sha256').hexdigest() + '  ' + file.name + '\n')
PY
printf 'Verified candidate: %s\n' "$release_output"
