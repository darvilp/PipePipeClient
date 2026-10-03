# PipePipe All Features release instructions

The release channel is `unofficial/all-features` in `darvilp/PipePipeClient`, with a clean sibling `PipePipeExtractor`. The release runbook is [release/README.md](release/README.md); source pins and identity are in [release/source-manifest.json](release/source-manifest.json).

## Use one release entry point

- Use `python3 tools/release.py --check` for preflight and `python3 tools/release.py` for a signed local candidate. GitHub Actions uses the same entry point. See `--help` for the output override.
- Do not invent per-release shell wrappers, manually sign a replacement APK, use a debug build as the release candidate, or bypass the entry point with direct Gradle commands. The internal backend and verifier are shared release implementation details.
- The standard private config is `${XDG_CONFIG_HOME:-$HOME/.config}/pipepipe/release.env`, or `PIPEPIPE_RELEASE_ENV`. This host links the standard location to its existing setup. Check it before asking for signing credentials or searching old chats.
- The entry point accepts legacy `KEYSTORE_FILE`/`KEYSTORE_PASSWORD` and canonical `KEY_PATH`/`KEY_STORE_PASSWORD`, plus `KEY_ALIAS` and `KEY_PASSWORD`. Nonempty process inputs override the file. GitHub Actions supplies the four `PIPEPIPE_ALL_FEATURES_*` repository secrets described in the runbook.
- Never print or commit private paths, aliases, passwords, key material or base64 credentials. Never silently replace the key. The selected certificate and final APKs must match `signer_sha256` in the tracked manifest.

## Prepare, test, then publish

1. Inspect branch, HEAD and status before editing. Integrate in a separate worktree and preserve existing work. Migrate maintained feature branches individually and prove their builds before combining them. Keep unrelated changes out of the candidate.
2. Preserve package `InfinityLoop1309.NewPipeEnhanced.debug.allfeatures` and label `PipePipe All Features (Unofficial)`. The `.debug` text is historical identity; the release APK must be minified and non-debuggable. Increment the fork counter for each newly distributed app build and match Gradle and manifest versions. Pin extractor and FFmpeg inputs; commit the candidate so both checkouts are clean.
3. Run the release entry point. It verifies source/toolchain/signing before building, checks JVM tests and the existing lint baseline, and verifies all four ABI APKs. Never reset the lint baseline to make a build pass. Run relevant Android regression tests separately and report debug/emulator evidence separately from signed-release device acceptance.
4. Hand over the signed arm64 APK with its exact source commit, filename, SHA-256 and signer. Record install/update and retained-data checks, playback and changed-feature testing on the intended device. Never clear user data or change devices implicitly. Use the runbook's disposable-device probes only in their documented scope.
5. Wait for user test-drive acceptance and explicit publication approval. Then bring the accepted source onto `unofficial/all-features`, make exact client/extractor revisions available on the forks, and tag the accepted client commit as `all-features/v<version_name>`. Do not replace existing tags or APKs. A branch push builds artifacts; the tag workflow creates a draft prerelease.
6. Verify the draft target, downloaded manifests, APK signer and hashes before publishing. A later CI build can differ byte-for-byte from the local APK: identify both artifacts and do not claim it is the exact tested file without a matching hash. Preserve old releases; rollback may require a corrected build with a higher version code.

Do not push, tag, create a release or publish merely because a local build passes. A request for a local test-drive build authorizes only that local build and handoff. Documentation/tooling edits do not invalidate the provenance of an earlier APK; keep its source commit and hash recorded separately from subsequent commits.
