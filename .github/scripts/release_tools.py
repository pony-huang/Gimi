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
VERSION = re.compile(r'(0|[1-9]\d*)\.(0|[1-9]\d*)\.(0|[1-9]\d*)(?:-(beta|rc)\.([1-9]\d*))?')
PLUGINS = ('spotify', 'zhihu', 'xiaohongshu', 'v2ex', 'weibo', 'netease')
PLUGIN_CATALOG = Path('data/plugin/src/main/assets/plugin_catalog.json')


def plugin_catalog(root):
    """校验内置插件目录：schema 版本、条目键/包名/接口版本、zh/en 文案、标签齐全，且禁止预填分发字段（apkUrl/sha256 等只能由发布产物回填）；返回解析后的目录。"""
    catalog = json.loads((root / PLUGIN_CATALOG).read_text(encoding='utf-8'))
    if not isinstance(catalog, dict) or type(catalog.get('schemaVersion')) is not int or \
            catalog['schemaVersion'] != 1 or not isinstance(catalog.get('entries'), list):
        raise ValueError('Unsupported plugin catalog schema')
    keys = []
    for entry in catalog['entries']:
        if not isinstance(entry, dict):
            raise ValueError('Plugin catalog entry must be an object')
        key = entry.get('key')
        if key not in PLUGINS or key in keys:
            raise ValueError(f'Unknown or duplicate plugin: {key}')
        keys.append(key)
        if entry.get('packageName') != f'github.ponyhuang.gimi.plugin.{key}':
            raise ValueError(f'Unexpected plugin package: {key}')
        if type(entry.get('pluginVersion')) is not int or entry['pluginVersion'] <= 0:
            raise ValueError(f'Invalid plugin interface version: {key}')
        for field in ('title', 'iconKey', 'repoUrl', 'releaseAssetPrefix'):
            if not isinstance(entry.get(field), str) or not entry[field].strip():
                raise ValueError(f'Missing plugin {field}: {key}')
        for field in ('summary', 'description'):
            localized_text(entry.get(field), key)
        if not isinstance(entry.get('highlights'), list):
            raise ValueError(f'Invalid plugin highlights: {key}')
        for highlight in entry['highlights']:
            localized_text(highlight, key)
        if type(entry.get('sortOrder')) is not int or not isinstance(entry.get('tags'), list):
            raise ValueError(f'Invalid plugin ordering or tags: {key}')
        if any(not isinstance(tag, str) or not tag.strip() for tag in entry['tags']):
            raise ValueError(f'Invalid plugin tag: {key}')
        # 安装信息只能来自本次验证后的产物，避免人工目录残留旧版地址或摘要。
        if any(entry.get(field) is not None for field in ('apkUrl', 'sha256', 'sizeBytes', 'versionCode', 'versionName')):
            raise ValueError(f'Generated installation fields in source catalog: {key}')
    if set(keys) != set(PLUGINS):
        raise ValueError('Catalog must describe every released plugin')
    return catalog


def localized_text(value, key):
    """要求 zh/en 两个 locale 均为非空字符串，否则按插件键报错。"""
    if not isinstance(value, dict) or any(
        not isinstance(value.get(locale), str) or not value[locale].strip() for locale in ('zh', 'en')
    ):
        raise ValueError(f'Missing localized plugin text: {key}')


def write_plugin_subscription(root, destination, repository, version, version_code):
    """按本次验证过的 APK 生成 plugin-subscription.json：逐插件核验 APK 存在、大小与命名前缀，回填 apkUrl/sha256/sizeBytes/versionCode/versionName，编辑字段原样保留。"""
    version_key(version)
    if type(version_code) is not int or not 0 < version_code <= 2100000000:
        raise ValueError('Invalid plugin APK versionCode')
    if not re.fullmatch(r'[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+', repository):
        raise ValueError('Invalid release repository')
    catalog = plugin_catalog(root)
    repo = repository.split('/')[-1]
    for entry in catalog['entries']:
        name = f'{repo}-plugin-{entry["key"]}-v{version}.apk'
        apk = destination / name
        if not apk.is_file() or not 0 < apk.stat().st_size <= 50 * 1024 * 1024:
            raise ValueError(f'Missing, empty or oversized plugin APK: {name}')
        if not name.startswith(entry['releaseAssetPrefix']):
            raise ValueError(f'Plugin asset prefix does not match release: {entry["key"]}')
        entry.update(
            apkUrl=f'https://github.com/{repository}/releases/download/v{version}/{name}',
            sha256=hashlib.sha256(apk.read_bytes()).hexdigest(),
            sizeBytes=apk.stat().st_size,
            versionCode=version_code,
            versionName=version,
        )
    (destination / 'plugin-subscription.json').write_text(
        json.dumps(catalog, ensure_ascii=False, indent=2) + '\n', encoding='utf-8',
    )


def run(*args):
    """执行命令并返回 strip 后的标准输出；非零退出由 check_output 直接抛错。"""
    return subprocess.check_output(args, text=True).strip()


def version_key(version):
    """解析 X.Y.Z[-beta.N|-rc.N] 为可比较元组：beta < rc < 正式版，数字段按数值比较。"""
    match = VERSION.fullmatch(version)
    if not match:
        raise ValueError('Version must be X.Y.Z, X.Y.Z-beta.N or X.Y.Z-rc.N')
    major, minor, patch, tier, number = match.groups()
    base = tuple(map(int, (major, minor, patch)))
    # 预发布层级：beta 早于 rc，rc 早于正式版。
    if tier is None:
        return base + (2, 0)
    return base + (0 if tier == 'beta' else 1, int(number))


def config(root=ROOT):
    """读取并交叉校验 version.txt、release-please manifest 与 gradle.properties 的 versionCode 三方一致；返回 (版本名, versionCode)。"""
    version = (root / 'version.txt').read_text(encoding='utf-8').strip()
    version_key(version)
    codes = re.findall(r'^releaseVersionCode=(\d+)\s*$', (root / 'gradle.properties').read_text(encoding='utf-8'), re.M)
    if len(codes) != 1 or not 0 < int(codes[0]) <= 2100000000:
        raise ValueError('Exactly one valid releaseVersionCode is required')
    manifest = json.loads((root / '.release-please-manifest.json').read_text(encoding='utf-8'))
    if manifest['.'] != version:
        raise ValueError('version.txt and release-please manifest differ')
    return version, int(codes[0])


def notes(text, version):
    """从 CHANGELOG.md 提取指定版本的章节正文，作为 GitHub Release 与应用内更新弹窗的共同文案；章节缺失、重复或为空都拒绝。"""
    # release-please uses both linked and plain SemVer headings, sometimes with dates.
    sections = list(re.finditer(r'^## (?:\[)?(\d+\.\d+\.\d+(?:-(?:beta|rc)\.[1-9]\d*)?)(?:\]|\s|$).*$', text, re.M))
    matches = [(i, item) for i, item in enumerate(sections) if item[1] == version]
    if len(matches) != 1:
        raise ValueError(f'Exactly one CHANGELOG entry for {version} is required')
    i, item = matches[0]
    body = text[item.end():sections[i + 1].start() if i + 1 < len(sections) else len(text)].strip()
    if not body:
        raise ValueError('Release notes cannot be empty')
    return body + '\n'


def releases():
    """经 gh api 分页拉取仓库全部 Release 并摊平为列表。"""
    pages = json.loads(run('gh', 'api', '--paginate', '--slurp', f'repos/{os.environ["GITHUB_REPOSITORY"]}/releases'))
    return [release for page in pages for release in page]


def current_release(items, tag):
    """查找 tag 对应的 Release；若已发布（非草稿）即抛错——发布不可变，重试只能覆盖草稿。"""
    matches = [item for item in items if item['tag_name'] == tag]
    if matches and not matches[0]['draft']:
        raise ValueError('Published releases are immutable; create a new version')
    return matches[0] if matches else None


def latest_release(items):
    """取语义版本最高的非草稿 Release（beta/rc 参与排序），作为递增校验的基线；无候选返回 None。"""
    candidates = [item for item in items if not item['draft'] and VERSION.fullmatch(item['tag_name'].removeprefix('v'))]
    return max(candidates, key=lambda item: version_key(item['tag_name'].removeprefix('v')), default=None)


def validate(tag):
    """发布前置校验：tag 与版本文件一致、检出即 tag 提交、tag 在 main 历史内、版本必须超过全部已发布版本；并把 Release 正文写入 temp/release/notes.md 供 publish 使用。"""
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
        raise ValueError('Release version must exceed all published versions, including prereleases')
    output = ROOT / 'temp/release'
    output.mkdir(parents=True, exist_ok=True)
    (output / 'notes.md').write_text(notes((ROOT / 'CHANGELOG.md').read_text(encoding='utf-8'), version))
    print(f'Validated {tag}, versionCode={code}')


def apk_metadata(aapt, path):
    """用 aapt badging 解析 APK 的 (包名, versionCode, versionName) 三元组。"""
    output = run(aapt, 'dump', 'badging', str(path))
    match = re.search(r"package: name='([^']+)' versionCode='(\d+)' versionName='([^']+)'", output)
    if not match:
        raise ValueError(f'Cannot read APK metadata: {path}')
    return match[1], int(match[2]), match[3]


def package(aapt, apksigner):
    """核验并打包发版产物：逐 APK 校验元数据与签名证书、对比上一分发 APK 确保 versionCode 递增，生成订阅目录与 SHA256SUMS，产物落 temp/release/dist。"""
    version, code = config()
    repo = os.environ['GITHUB_REPOSITORY'].split('/')[-1]
    fingerprint = os.environ['RELEASE_CERT_SHA256'].lower().replace(':', '')
    entries = [(ROOT / f'app/build/outputs/apk/release/app-{abi}-release.apk',
                f'{repo}-v{version}-{abi}.apk', 'github.ponyhuang.gimi') for abi in ('arm64-v8a', 'universal')]
    entries += [(ROOT / f'plugins/{name}/build/outputs/apk/release/{name}-release.apk',
                 f'{repo}-plugin-{name}-v{version}.apk', f'github.ponyhuang.gimi.plugin.{name}') for name in PLUGINS]
    destination = ROOT / 'temp/release/dist'
    destination.mkdir(parents=True, exist_ok=True)
    # 重试只能发布本轮产物，旧附件不能混进完整性检查与上传列表。
    for path in destination.iterdir():
        path.unlink()
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
    }, indent=2) + '\n', encoding='utf-8')
    write_plugin_subscription(ROOT, destination, os.environ['GITHUB_REPOSITORY'], version, code)
    (destination / 'SHA256SUMS').write_text(''.join(
        f'{hashlib.sha256(path.read_bytes()).hexdigest()}  {path.name}\n'
        for path in sorted(destination.iterdir()) if path.name != 'SHA256SUMS'), encoding='utf-8')
    print(f'Validated and packaged {len(entries)} signed APKs')


def publish(tag):
    """发布流程：确认远端 tag 未被移动，创建/更新草稿并上传附件，逐附件核对名称与大小后才转正；预发布带 --prerelease 且不成为 latest。"""
    version, _ = config()
    if tag != f'v{version}':
        raise ValueError('Tag does not match version.txt')
    release = current_release(releases(), tag)
    subprocess.run(['git', 'fetch', '--no-tags', 'origin', f'refs/tags/{tag}'], check=True)
    if run('git', 'rev-parse', 'FETCH_HEAD^{commit}') != run('git', 'rev-parse', 'HEAD'):
        raise ValueError('Remote tag changed during the release build')
    note_path = ROOT / 'temp/release/notes.md'
    flags = ['--prerelease'] if re.search(r'-(?:beta|rc)\.', version) else []
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
        plugin_catalog(ROOT)
        version, code = config()
        notes((ROOT / 'CHANGELOG.md').read_text(encoding='utf-8'), version)
        if args.base_ref and subprocess.run(
            ['git', 'cat-file', '-e', f'{args.base_ref}:version.txt'],
            stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL,
        ).returncode == 0:
            base_version = run('git', 'show', f'{args.base_ref}:version.txt')
            if base_version != version:
                base_properties = run('git', 'show', f'{args.base_ref}:gradle.properties')
                base_code = int(re.search(r'^releaseVersionCode=(\d+)\s*$', base_properties, re.M)[1])
                if version_key(version) <= version_key(base_version) or code <= base_code:
                    raise ValueError('Version PR must increment both versionName and releaseVersionCode')
    elif args.command == 'validate':
        validate(args.tag)
    elif args.command == 'package':
        package(args.aapt, args.apksigner)
    else:
        publish(args.tag)
