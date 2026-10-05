"""Assign a stable Android install version to release-please PRs before CI."""
import base64
import json
import os
import re
import subprocess
from urllib.parse import quote

from release_tools import config, version_key


CODE = re.compile(r'^(releaseVersionCode=)(\d+)([ \t]*)$', re.M)


def increment_properties(properties, base_code):
    matches = list(CODE.finditer(properties))
    if len(matches) != 1:
        raise ValueError('Exactly one releaseVersionCode is required')
    code = max(base_code + 1, int(matches[0][2]))
    if not 0 < code <= 2100000000:
        raise ValueError('Android versionCode range exhausted')
    # main is the allocation baseline: re-running on the same PR keeps its code.
    return CODE.sub(lambda match: f'{match[1]}{code}{match[3]}', properties)


def api(endpoint, body=None):
    command = ['gh', 'api', endpoint]
    if body is not None:
        command += ['--method', 'PUT', '--input', '-']
    return json.loads(subprocess.check_output(
        command, input=json.dumps(body) if body is not None else None, text=True,
    ))


def contents(repo, branch, path):
    return api(f'repos/{repo}/contents/{path}?ref={quote(branch, safe="")}')


def decode(file):
    return base64.b64decode(file['content']).decode('utf-8')


def prepare(number, repo, base_version, base_code):
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
    subprocess.run(['gh', 'workflow', 'run', 'ci.yml', '--ref', branch], check=True)


if __name__ == '__main__':
    base_version, base_code = config()
    for pr in json.loads(os.environ['RELEASE_PRS']):
        prepare(pr['number'], os.environ['GITHUB_REPOSITORY'], base_version, base_code)
