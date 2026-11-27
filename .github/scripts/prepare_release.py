"""Bump version files on the current branch: version.txt, gradle.properties, and a CHANGELOG section.

NewPipe 式发版：版本提升是普通开发提交的一部分——在发版分支上运行本脚本，
审查产物后自行 commit 并随分支合并 main；打 tag 推送后 CI 负责构建与发布。
版本号由发版人显式输入，脚本只执行不推算。
"""
import argparse
import datetime
from pathlib import Path
import re
import subprocess

import release_tools as release

VERSION_CODE = re.compile(r'^(releaseVersionCode=)(\d+)([ \t]*)$', re.M)
# Conventional Commits 前缀：type(scope)!: subject。
SUBJECT = re.compile(r'^(\w+)(?:\([^)]*\))?(!?):\s*(.+)$')
GROUPS = (
    ('### 新功能与体验', ('feat',)),
    ('### 修复', ('fix',)),
)


def run(*args):
    """执行命令并返回 strip 后的标准输出；非零退出由 check_output 直接抛错。"""
    return subprocess.check_output(args, text=True).strip()


def previous_tag(base):
    """取 base 可达的最高语义版本 tag 作为对比基线；无历史 tag 时返回占位（首版）。

    用完整 refname 解析：分支与 tag 同名时 %(refname:short) 会被 git 消歧成
    `tags/vX.Y.Z`，按短名匹配版本正则会失配。
    """
    output = run('git', 'tag', '--merged', base, '--format=%(refname)')
    candidates = [tag.removeprefix('refs/tags/') for tag in output.splitlines()
                  if release.VERSION.fullmatch(tag.removeprefix('refs/tags/').removeprefix('v'))]
    return max(candidates, key=lambda tag: release.version_key(tag.removeprefix('v')), default='v0.0.0')


def commit_subjects(base, previous_tag):
    """取上一发版内容之后到 base 的提交标题。

    火车式发版的上一个 tag 通常就是发布合并提交（内容已在 tag 里），因此范围起点用
    tag 第一父提交 `tag^1`；首个 tag 不存在时（仓库早期）退回全部 main 历史。
    """
    try:
        subprocess.run(['git', 'cat-file', '-e', f'{previous_tag}^{{commit}}'], check=True,
                       stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
        range_args = (f'{previous_tag}^1..{base}',)
    except subprocess.CalledProcessError:
        range_args = (base,)
    output = run('git', 'log', '--format=%s', *range_args)
    return [line for line in output.splitlines() if line]


def changelog_section(version, subjects, repository, base, today=None):
    """按 Conventional Commits 分组生成 CHANGELOG 章节：feat/fix 各成一节，未归类的杂项提交不进正文（发版前人工整理）。

    标题用既有链接式格式，notes() 的章节提取规则依赖它。
    """
    base_tag = previous_tag(base)
    sections = []
    for title, types in GROUPS:
        bullets = [f'- {SUBJECT.match(subject).group(3)}' for subject in subjects
                   if (match := SUBJECT.match(subject)) and match.group(1) in types]
        if bullets:
            sections.append(f'{title}\n\n' + '\n'.join(bullets))
    if not sections:
        raise ValueError('No feat/fix commits found for the changelog section')
    date = today or datetime.date.today().isoformat()
    heading = f'## [{version}](https://github.com/{repository}/compare/{base_tag}...v{version}) ({date})'
    return heading + '\n\n' + '\n\n'.join(sections) + '\n'


def bump(version, repository, base):
    """校验版本推进后，在当前分支工作区写入三处版本文件；不提交不开 PR，产物随分支正常走 review。"""
    release.version_key(version)
    # 以当前工作目录为仓库根：脚本可在任意 worktree 中运行，不锚定 .github/scripts 所在主检出。
    root = Path.cwd()
    if not (root / 'version.txt').is_file() or not (root / 'gradle.properties').is_file():
        raise ValueError('Run this script from a repository checkout root')
    _, base_code = release.config(root)
    if run('git', 'rev-parse', 'HEAD') != run('git', 'rev-parse', base):
        raise ValueError(f'Current checkout must be {base}')
    if subprocess.run(['git', 'rev-parse', '--verify', '--quiet', f'v{version}^{{commit}}'],
                      stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL).returncode == 0:
        raise ValueError(f'Tag v{version} already exists')

    code = base_code + 1
    if not 0 < code <= 2100000000:
        raise ValueError('Android versionCode range exhausted')
    base_version, _ = release.config(root)
    if release.version_key(version) <= release.version_key(base_version):
        raise ValueError('New version must advance the current version.txt')

    (root / 'version.txt').write_text(version + '\n', encoding='utf-8')
    properties = (root / 'gradle.properties').read_text(encoding='utf-8')
    (root / 'gradle.properties').write_text(
        VERSION_CODE.sub(lambda match: f'{match[1]}{code}{match[3]}', properties), encoding='utf-8')
    changelog = (root / 'CHANGELOG.md').read_text(encoding='utf-8')
    marker = changelog.index('## ')
    updated = changelog[:marker] + changelog_section(version, commit_subjects(base, f'v{base_version}'), repository, base) + '\n' + changelog[marker:]
    (root / 'CHANGELOG.md').write_text(updated, encoding='utf-8')
    # notes() 要求章节正文非空；这里提前走一次发布前同款校验，拦截空章节。
    release.notes(updated, version)
    print(f'Bumped to {version} (versionCode={code}); review and commit the three files')


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('version', help='目标版本号，例如 1.0.0-beta.6')
    parser.add_argument('--base', default='HEAD', help='变更历史扫描基线，默认当前 HEAD')
    parser.add_argument('--repository', default=None, help='默认取 GITHUB_REPOSITORY 或 gh 推断的 origin')
    args = parser.parse_args()
    repository = args.repository or release.run('gh', 'repo', 'view', '--json', 'nameWithOwner', '--jq', '.nameWithOwner')
    bump(args.version, repository, args.base)
