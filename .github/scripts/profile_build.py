#!/usr/bin/env python3
"""Measure warm APK builds; temporarily change a private log tag for an incremental probe."""
import argparse
import hashlib
import html
import shutil
from urllib.parse import unquote, urlparse
import json
import os
import platform
import re
import statistics
import subprocess
import time
from datetime import datetime, timezone
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
PROBE = ROOT / 'feature/chat/src/main/java/github/ponyhuang/gimi/feature/chat/ChatViewModel.kt'
TOKEN = b'private const val TAG: String = "ChatViewModel"'
PROBES = {
    'viewmodel': (PROBE, TOKEN),
    'lifecycle': (ROOT / 'feature/chat/src/main/java/github/ponyhuang/gimi/feature/chat/ChatRunLifecycleCoordinator.kt',
                  b'private const val TAG = "ChatRunLifecycle"'),
}


def generated_snapshot(directory=None):
    directory = directory or ROOT / 'feature/chat/build/generated/ksp/debug'
    return {str(file.relative_to(directory)): hashlib.sha256(file.read_bytes()).hexdigest()
            for file in sorted(directory.rglob('*')) if file.is_file()}


def generated_changes(previous, current):
    return {'added': sorted(current.keys() - previous.keys()),
            'removed': sorted(previous.keys() - current.keys()),
            'changed': sorted(key for key in previous.keys() & current.keys()
                              if previous[key] != current[key])}



def summarize_log(log):
    """Separate executed, cached and skipped tasks from Gradle plain console output."""
    counts = {}
    executed = []
    for line in log.splitlines():
        match = re.fullmatch(r'> Task (:[^ ]+)(?: (UP-TO-DATE|FROM-CACHE|NO-SOURCE|SKIPPED|FAILED))?', line)
        if not match:
            continue
        task, outcome = match.groups()
        outcome = outcome or 'EXECUTED'
        counts[outcome] = counts.get(outcome, 0) + 1
        if outcome == 'EXECUTED':
            executed.append(task)
    return {'task_outcomes': counts, 'executed_tasks': executed,
            'configuration_cache_reused': 'Configuration cache entry reused.' in log}


def profile_details(log, output, name):
    match = re.search(r'See the profiling report at: (file:\S+)', log)
    if not match:
        return {}
    profile = Path(unquote(urlparse(match.group(1)).path))
    if not profile.is_file():
        return {}
    destination = output / f'{name}-profile.html'
    shutil.copy2(profile, destination)
    for assets in ['css', 'js']:
        if (profile.parent / assets).is_dir():
            shutil.copytree(profile.parent / assets, output / assets, dirs_exist_ok=True)
    timings = []
    for row in re.findall(r'<tr>(.*?)</tr>', profile.read_text(), re.S):
        cells = [html.unescape(re.sub(r'<[^>]+>', '', cell)).strip()
                 for cell in re.findall(r'<td[^>]*>(.*?)</td>', row, re.S)]
        if len(cells) == 3 and cells[0].startswith(':') and not cells[2]:
            duration = re.fullmatch(r'([\d.]+)s', cells[1])
            if duration:
                timings.append({'task': cells[0], 'seconds': float(duration.group(1))})
    return {'profile': str(destination.relative_to(ROOT)),
            'slowest_executed_tasks': sorted(timings, key=lambda row: row['seconds'], reverse=True)[:10]}


def build(output, name, args):
    command = [str(ROOT / ('gradlew.bat' if os.name == 'nt' else 'gradlew')),
               ':app:assembleDebug', '--console=plain', '--profile',
               f'--max-workers={args.workers}']
    if args.diagnostics:
        command += ['--info', '-Pksp.incremental.log=true']
    if not args.online:
        command.append('--offline')
    started = time.perf_counter()
    log_path = output / f'{name}.log'
    with log_path.open('w', encoding='utf-8') as log:
        result = subprocess.run(command, cwd=ROOT, stdout=log, stderr=subprocess.STDOUT)
    elapsed = time.perf_counter() - started
    text = log_path.read_text(encoding='utf-8', errors='replace')
    record = {'name': name, 'wall_seconds': round(elapsed, 3),
              'exit_code': result.returncode, 'chat_ksp_outputs': generated_snapshot(), 'log': str(log_path.relative_to(ROOT)),
              **summarize_log(text), **profile_details(text, output, name)}
    print(f'{name}: {elapsed:.2f}s, exit={result.returncode}, '
          f'tasks={record["task_outcomes"]}', flush=True)
    if result.returncode:
        raise RuntimeError(f'Build failed; inspect {log_path}')
    return record


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--iterations', type=int, default=3)
    parser.add_argument('--workers', type=int, default=2)
    parser.add_argument('--online', action='store_true')
    parser.add_argument('--diagnostics', action='store_true', help='Enable Gradle info and KSP incremental logs; compare only with equally configured runs')
    parser.add_argument('--probe', choices=PROBES, default='viewmodel')
    parser.add_argument('--incremental', action='store_true', help='Temporarily change the selected private log tag')
    args = parser.parse_args()
    if args.iterations < 1 or args.workers < 1:
        parser.error('iterations and workers must be positive')
    output = ROOT / 'temp/build-profile' / datetime.now(timezone.utc).strftime('%Y%m%dT%H%M%S.%fZ')
    output.mkdir(parents=True)
    probe, token = PROBES[args.probe]
    original = probe.read_bytes()
    if args.incremental and original.count(token) != 1:
        parser.error('Expected exactly one probe log tag; update the script for this source version')
    java = subprocess.run(['java', '-version'], capture_output=True, text=True)
    revision = subprocess.run(['git', 'rev-parse', 'HEAD'], cwd=ROOT, capture_output=True, text=True)
    dirty = subprocess.run(['git', 'status', '--porcelain'], cwd=ROOT, capture_output=True, text=True)
    records = []
    report = {'utc': datetime.now(timezone.utc).isoformat(), 'platform': platform.platform(),
              'logical_cpus': os.cpu_count(), 'java': '\n'.join(line for line in (java.stderr or java.stdout).splitlines()
                                if not line.startswith('Picked up JAVA_TOOL_OPTIONS:')),
              'git_head': revision.stdout.strip(), 'dirty_files': dirty.stdout.splitlines(),
              'gradle_wrapper': (ROOT / 'gradle/wrapper/gradle-wrapper.properties').read_text(),
              'gradle_properties': (ROOT / 'gradle.properties').read_text(),
              'workers': args.workers, 'offline': not args.online,
              'probe': args.probe, 'diagnostics': args.diagnostics, 'probe_sha256': hashlib.sha256(original).hexdigest(), 'runs': records}
    if args.incremental:
        (output / "probe-original.kt").write_bytes(original)
    expected = original
    try:
        records.append(build(output, 'warmup', args))
        for index in range(args.iterations):
            records.append(build(output, f'no-change-{index + 1}', args))
        if args.incremental:
            # Modify implementation only, without changing API, annotations or persisted data.
            # Every iteration uses a distinct value, so previous task outputs cannot satisfy it.
            for index in range(args.iterations):
                if probe.read_bytes() != expected:
                    raise RuntimeError('Probe changed concurrently; refusing to overwrite it')
                expected = original.replace(token, token[:-1] + f'-build-profile-{output.name}-{index + 1}"'.encode())
                probe.write_bytes(expected)
                records.append(build(output, f'chat-incremental-{index + 1}', args))
    finally:
        if args.incremental and expected != original:
            if probe.read_bytes() != expected:
                report['restore_error'] = 'Concurrent source modification; restore from probe-original.kt manually'
                (output / 'probe-original.kt').write_bytes(original)
            else:
                probe.write_bytes(original)
                report['source_restored'] = True
                # Restore outputs too: no probe APK remains as the latest assembled app.
                try:
                    records.append(build(output, 'restore-build', args))
                except Exception as failure:
                    report['restore_build_error'] = str(failure)
        previous = {}
        for run in records:
            current = run['chat_ksp_outputs']
            run['chat_ksp_changes'] = generated_changes(previous, current)
            previous = current
        for scenario in ['no-change', 'chat-incremental']:
            values = [r['wall_seconds'] for r in records if r['name'].startswith(scenario)]
            if values:
                report[scenario] = {'median_seconds': statistics.median(values),
                                    'min_seconds': min(values), 'max_seconds': max(values)}
        (output / 'summary.json').write_text(json.dumps(report, indent=2, ensure_ascii=False) + '\n')
        print(f'Report: {output / "summary.json"}', flush=True)
    if 'restore_error' in report or 'restore_build_error' in report:
        raise RuntimeError('Probe restoration incomplete; inspect summary.json')


if __name__ == '__main__':
    main()
