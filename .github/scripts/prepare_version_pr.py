"""Assign a stable Android install version without dispatching version PR CI."""
import base64
import json
import os
import re
import subprocess
from urllib.parse import quote

from release_tools import config, version_key


CODE = re.compile(r'^(releaseVersionCode=)(\d+)([ \t]*)$', re.M)


def increment_properties(properties, base_code):
    """把 gradle.properties 文本中的 versionCode 提升到 max(基线+1, 现值)：恰好一个声明、越界即拒绝；现值不低于基线时保持不变，保证同一 PR 反复更新幂等。"""
    matches = list(CODE.finditer(properties))
    if len(matches) != 1:
        raise ValueError('Exactly one releaseVersionCode is required')
    code = max(base_code + 1, int(matches[0][2]))
    if not 0 < code <= 2100000000:
        raise ValueError('Android versionCode range exhausted')
    # main is the allocation baseline: re-running on the same PR keeps its code.
    return CODE.sub(lambda match: f'{match[1]}{code}{match[3]}', properties)


def api(endpoint, body=None):
    """调用 gh api；带 body 时发 PUT（contents 提交用），返回解析后的 JSON。"""
    command = ['gh', 'api', endpoint]
    if body is not None:
        command += ['--method', 'PUT', '--input', '-']
    return json.loads(subprocess.check_output(
        command, input=json.dumps(body) if body is not None else None, text=True,
    ))


def contents(repo, branch, path):
    """按分支读取仓库文件的 contents API 响应（含 sha 与 base64 内容）。"""
    return api(f'repos/{repo}/contents/{path}?ref={quote(branch, safe="")}')


def decode(file):
    """解码 contents API 返回的 base64 内容为 UTF-8 文本。"""
    return base64.b64decode(file['content']).decode('utf-8')


def prepare(number, repo, base_version, base_code):
    """为单个版本 PR 分配 versionCode：校验 PR 形态（open/目标 main/本仓库）与版本推进，按分支现值计算新 code 后经 contents API 以 sha 防并发提交；值无变化时不产生新提交。"""
    pr = api(f'repos/{repo}/pulls/{number}')
    if pr['state'] != 'open' or pr['base']['ref'] != 'main' or pr['head']['repo']['full_name'] != repo:
        raise ValueError('Expected an open same-repository version PR targeting main')
    branch = pr['head']['ref']
    version = decode(contents(repo, branch, 'version.txt')).strip()
    if version_key(version) <= version_key(base_version):
        raise ValueError('Version PR must advance the main version')
    file = contents(repo, branch, 'gradle.properties')
    original = decode(file)
    updated = increment_properties(original, base_code)
    if updated != original:
        # The content SHA rejects concurrent file edits instead of overwriting them.
        api(f'repos/{repo}/contents/gradle.properties', {
            'branch': branch,
            'sha': file['sha'],
            'message': f'chore(release): set Android versionCode for {version}',
            'content': base64.b64encode(updated.encode('utf-8')).decode('ascii'),
        })


if __name__ == '__main__':
    base_version, base_code = config()
    for pr in json.loads(os.environ['RELEASE_PRS']):
        prepare(pr['number'], os.environ['GITHUB_REPOSITORY'], base_version, base_code)
