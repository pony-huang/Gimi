"""Validate release inputs, APKs, and the draft-to-public release transition."""
import argparse
import hashlib
import json
import os
from pathlib import Path
import re
import shutil
import subprocess

ROOT = Path(__file__).resolve().parents[2]
VERSION = re.compile(r'(0|[1-9]\d*)\.(0|[1-9]\d*)\.(0|[1-9]\d*)(?:-rc\.([1-9]\d*))?')
PLUGINS = ('spotify', 'zhihu', 'xiaohongshu', 'v2ex', 'weibo')


def run(*args):
    return subprocess.check_output(args, text=True).strip()


def version_key(version):
    match = VERSION.fullmatch(version)
    if not match:
        raise ValueError('Version must be X.Y.Z or X.Y.Z-rc.N')
    major, minor, patch, rc = match.groups()
    return tuple(map(int, (major, minor, patch))) + (int(rc) if rc else float('inf'),)


def config(root=ROOT):
    version = (root / 'version.txt').read_text().strip()
    version_key(version)
    codes = re.findall(r'^releaseVersionCode=(\d+)\s*$', (root / 'gradle.properties').read_text(), re.M)
    if len(codes) != 1 or not 0 < int(codes[0]) <= 2100000000:
        raise ValueError('Exactly one valid releaseVersionCode is required')
    manifest = json.loads((root / '.release-please-manifest.json').read_text())
    if manifest['.'] != version:
        raise ValueError('version.txt and release-please manifest differ')
    return version, int(codes[0])


def notes(text, version):
    # release-please uses both linked and plain SemVer headings, sometimes with dates.
    sections = list(re.finditer(r'^## (?:\[)?(\d+\.\d+\.\d+(?:-rc\.[1-9]\d*)?)(?:\]|\s|$).*$', text, re.M))
    matches = [(i, item) for i, item in enumerate(sections) if item[1] == version]
    if len(matches) != 1:
        raise ValueError(f'Exactly one CHANGELOG entry for {version} is required')
    i, item = matches[0]
    body = text[item.end():sections[i + 1].start() if i + 1 < len(sections) else len(text)].strip()
    if not body:
        raise ValueError('Release notes cannot be empty')
    return body + '\n'


def releases():
    pages = json.loads(run('gh', 'api', '--paginate', '--slurp', f'repos/{os.environ["GITHUB_REPOSITORY"]}/releases'))
    return [release for page in pages for release in page]


def current_release(items, tag):
    matches = [item for item in items if item['tag_name'] == tag]
    if matches and not matches[0]['draft']:
        raise ValueError('Published releases are immutable; create a new version')
    return matches[0] if matches else None


def latest_release(items):
    candidates = [item for item in items if not item['draft'] and VERSION.fullmatch(item['tag_name'].removeprefix('v'))]
    return max(candidates, key=lambda item: version_key(item['tag_name'].removeprefix('v')), default=None)


def validate(tag):
    version, code = config()
    if tag != f'v{version}':
        raise ValueError('Tag does not match version.txt')
    if run('git', 'rev-parse', 'HEAD') != run('git', 'rev-parse', f'refs/tags/{tag}^{{commit}}'):
        raise ValueError('Checkout is not the tagged commit')
    subprocess.run(['git', 'merge-base', '--is-ancestor', 'HEAD', 'origin/main'], check=True)
    items = releases()
    current_release(items, tag)
    previous = latest_release(items)
    if previous and version_key(version) <= version_key(previous['tag_name'].removeprefix('v')):
        raise ValueError('Release version must exceed all published versions, including RCs')
    output = ROOT / 'temp/release'
    output.mkdir(parents=True, exist_ok=True)
    (output / 'notes.md').write_text(notes((ROOT / 'CHANGELOG.md').read_text(), version))
    print(f'Validated {tag}, versionCode={code}')


def apk_metadata(aapt, path):
    output = run(aapt, 'dump', 'badging', str(path))
    match = re.search(r"package: name='([^']+)' versionCode='(\d+)' versionName='([^']+)'", output)
    if not match:
        raise ValueError(f'Cannot read APK metadata: {path}')
    return match[1], int(match[2]), match[3]


def package(aapt, apksigner):
    version, code = config()
    repo = os.environ['GITHUB_REPOSITORY'].split('/')[-1]
    fingerprint = os.environ['RELEASE_CERT_SHA256'].lower().replace(':', '')
    entries = [(ROOT / f'app/build/outputs/apk/release/app-{abi}-release.apk',
                f'{repo}-v{version}-{abi}.apk', 'github.ponyhuang.gimi') for abi in ('arm64-v8a', 'universal')]
    entries += [(ROOT / f'plugins/{name}/build/outputs/apk/release/{name}-release.apk',
                 f'{repo}-plugin-{name}-v{version}.apk', f'github.ponyhuang.gimi.plugin.{name}') for name in PLUGINS]
    destination = ROOT / 'temp/release/dist'
    destination.mkdir(parents=True, exist_ok=True)
    for source, name, application_id in entries:
        if apk_metadata(aapt, source) != (application_id, code, version):
            raise ValueError(f'Unexpected APK metadata: {source}')
        certificates = run(apksigner, 'verify', '--print-certs', str(source))
        digests = re.findall(r'Signer #\d+ certificate SHA-256 digest: (\w+)', certificates)
        if digests != [fingerprint]:
            raise ValueError(f'Unexpected signing certificate: {source}')
        shutil.copyfile(source, destination / name)
    # Check the actual previous distributed APK, including the legacy run_number scheme.
    previous = latest_release(releases())
    if previous:
        assets = previous['assets']
        apk = next((a for a in assets if a['name'].endswith('-universal.apk')), None)
        if apk is None:
            apk = next((a for a in assets if a['name'].endswith('-arm64-v8a.apk')), None)
        if apk is None:
            raise ValueError('Previous release has no main APK; cannot verify upgrade versionCode')
        previous_dir = ROOT / 'temp/release/previous'
        previous_dir.mkdir(parents=True, exist_ok=True)
        subprocess.run(['gh', 'release', 'download', previous['tag_name'], '--pattern', apk['name'],
                        '--dir', str(previous_dir), '--clobber'], check=True)
        if code <= apk_metadata(aapt, previous_dir / apk['name'])[1]:
            raise ValueError('Increment releaseVersionCode above the previously distributed APK')
    (destination / 'release-metadata.json').write_text(json.dumps({
        'versionName': version, 'versionCode': code, 'commit': run('git', 'rev-parse', 'HEAD'),
        'certificateSha256': fingerprint,
    }, indent=2) + '\n')
    (destination / 'SHA256SUMS').write_text(''.join(
        f'{hashlib.sha256(path.read_bytes()).hexdigest()}  {path.name}\n'
        for path in sorted(destination.iterdir()) if path.name != 'SHA256SUMS'))
    print(f'Validated and packaged {len(entries)} signed APKs')


def publish(tag):
    version, _ = config()
    if tag != f'v{version}':
        raise ValueError('Tag does not match version.txt')
    release = current_release(releases(), tag)
    subprocess.run(['git', 'fetch', '--no-tags', 'origin', f'refs/tags/{tag}'], check=True)
    if run('git', 'rev-parse', 'FETCH_HEAD^{commit}') != run('git', 'rev-parse', 'HEAD'):
        raise ValueError('Remote tag changed during the release build')
    note_path = ROOT / 'temp/release/notes.md'
    flags = ['--prerelease'] if '-rc.' in version else []
    if release is None:
        subprocess.run(['gh', 'release', 'create', tag, '--verify-tag', '--draft', '--title', tag,
                        '--notes-file', str(note_path), *flags], check=True)
    else:
        subprocess.run(['gh', 'release', 'edit', tag, '--draft', '--title', tag,
                        '--notes-file', str(note_path), f'--prerelease={str(bool(flags)).lower()}'], check=True)
    files = sorted((ROOT / 'temp/release/dist').iterdir())
    # Only unpublished drafts may replace partial uploads on retry.
    subprocess.run(['gh', 'release', 'upload', tag, *map(str, files), '--clobber'], check=True)
    uploaded = json.loads(run('gh', 'release', 'view', tag, '--json', 'assets'))['assets']
    expected = {file.name: file.stat().st_size for file in files}
    actual = {asset['name']: asset['size'] for asset in uploaded}
    if actual != expected:
        raise ValueError('Draft assets differ from the validated package')
    subprocess.run(['gh', 'release', 'edit', tag, '--draft=false',
                    f'--latest={str(not bool(flags)).lower()}'], check=True)


if __name__ == '__main__':
    parser = argparse.ArgumentParser()
    parser.add_argument('command', choices=('check', 'validate', 'package', 'publish'))
    parser.add_argument('--tag')
    parser.add_argument('--base-ref')
    parser.add_argument('--aapt')
    parser.add_argument('--apksigner')
    args = parser.parse_args()
    if args.command == 'check':
        version, _ = config()
        notes((ROOT / 'CHANGELOG.md').read_text(), version)
        if args.base_ref and subprocess.run(
            ['git', 'cat-file', '-e', f'{args.base_ref}:version.txt'],
            stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL,
        ).returncode == 0:
            base_version = run('git', 'show', f'{args.base_ref}:version.txt')
            if base_version != version:
                base_properties = run('git', 'show', f'{args.base_ref}:gradle.properties')
                base_code = int(re.search(r'^releaseVersionCode=(\d+)\s*$', base_properties, re.M)[1])
                if version_key(version) <= version_key(base_version) or config()[1] <= base_code:
                    raise ValueError('Version PR must increment both versionName and releaseVersionCode')
    elif args.command == 'validate':
        validate(args.tag)
    elif args.command == 'package':
        package(args.aapt, args.apksigner)
    else:
        publish(args.tag)
