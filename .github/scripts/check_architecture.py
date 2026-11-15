"""Check declared production dependencies and imports without an Android SDK.

This is a source-level guard for this repository's literal Kotlin Gradle DSL.
It does not resolve Gradle's transitive dependencies or dynamically computed DSL.

Failures are incremental: architecture-baseline.txt records known violations
(signatures without line numbers) and only violations outside the baseline
fail the run. Regenerate the baseline with:

    python .github/scripts/check_architecture.py --update-baseline

Shrink the baseline by fixing violations, never by hand-editing entries.
"""
from pathlib import Path
import re
import sys

ROOT = Path(__file__).resolve().parents[2]
BASELINE_PATH = Path(__file__).resolve().parent / 'architecture-baseline.txt'
PROJECT = re.compile(r'(\w+)\s*\(\s*project\(\s*"(:[^"]+)"\s*\)\s*\)')
INCLUDE = re.compile(r'include\(\s*"(:[^"]+)"\s*\)')
IMPORT = re.compile(r'^import\s+([\w.]+)', re.MULTILINE)
DOMAIN_FORBIDDEN = (
    'android.', 'androidx.', 'dagger.', 'okhttp3.', 'retrofit2.',
    'com.google.adk.', 'com.google.genai.', 'com.openai.', 'com.anthropic.',
    'io.objectbox.',
)

# Modules whose role is not described by their Gradle layer alone.
# runtime: a standalone-process APK composition root (:environment-runtime);
# like :app it may compose :core:* and :domain:* directly, but never features
# or data implementations.
MODULE_ROLES = {':environment-runtime': 'runtime'}

# Dependency exceptions approved in AGENTS.md ("Mandatory boundaries").
# Every entry must have an owner and a removal condition documented there.
APPROVED_DEPENDENCIES = frozenset({
    # Shared voice picker components (:core:designsystem) are the second
    # documented exception consuming :domain:voice presentation models.
    (':core:designsystem', ':domain:voice'),
})

# Import exceptions per owner; keep the prefix as narrow as the exception.
APPROVED_IMPORT_PREFIXES = {
    ':core:designsystem': ('github.ponyhuang.gimi.domain.voice.',),
}

RULES = (
    ('forbidden production dependency', 'ARCH001',
     'depend on a domain contract instead, or record an exception with owner and removal condition in AGENTS.md'),
    ('forbidden import', 'ARCH002',
     'move the symbol to a module visible to the importer, or record an exception with owner and removal condition in AGENTS.md'),
    ('missing direct dependency', 'ARCH003',
     'declare the target module in this module\'s build.gradle.kts dependencies block'),
    ('undeclared module', 'ARCH004',
     'register the module in settings.gradle.kts or fix the dependency target'),
    ('missing build.gradle.kts', 'ARCH005',
     'create the module\'s build.gradle.kts or drop the module from settings.gradle.kts'),
    ('Dependency cycle', 'ARCH006',
     'break the cycle by extracting a shared contract module'),
)


def dependencies(text):
    # Only dependency declarations are matched, so URLs in strings are unaffected.
    text = re.sub(r'/\*.*?\*/', '', text, flags=re.DOTALL)
    text = re.sub(r'^\s*//.*$', '', text, flags=re.MULTILINE)
    return {
        target for config, target in PROJECT.findall(text)
        if not any(marker in config.lower() for marker in ('test', 'ksp', 'kapt'))
    }


def allowed_dependency(owner, target):
    if (owner, target) in APPROVED_DEPENDENCIES:
        return True
    layer = owner.split(':')[1]
    target_layer = target.split(':')[1]
    if layer == 'app':
        return True
    if MODULE_ROLES.get(owner) == 'runtime':
        return target_layer in ('core', 'domain')
    if layer == 'domain':
        return target_layer == 'domain'
    if layer == 'core':
        return target_layer == 'core'
    if layer == 'feature':
        return target_layer in ('domain', 'core')
    if layer == 'data':
        return target_layer in ('domain', 'core') or target == ':plugin-api'
    if layer == 'plugins':
        return target == ':plugin-api' or target_layer == 'core'
    return False


def cycles(graph):
    errors, visiting, visited = [], [], set()

    def visit(node):
        if node in visiting:
            errors.append('Dependency cycle: ' + ' -> '.join(visiting[visiting.index(node):] + [node]))
            return
        if node in visited:
            return
        visiting.append(node)
        for target in sorted(graph.get(node, ())):
            visit(target)
        visiting.pop()
        visited.add(node)

    for node in sorted(graph):
        visit(node)
    return errors


def forbidden_import(owner, name):
    if any(name.startswith(prefix) for prefix in APPROVED_IMPORT_PREFIXES.get(owner, ())):
        return False
    layer = owner.split(':')[1]
    prefix = 'github.ponyhuang.gimi.'
    if layer == 'domain' and name.startswith(DOMAIN_FORBIDDEN):
        return True
    if owner in (':domain:modelcatalog', ':data:modelcatalog') and name.startswith('com.google.adk.'):
        return True
    if not name.startswith(prefix):
        return False
    package = name[len(prefix):]
    if layer in ('domain', 'data', 'core', 'feature') and package.startswith(('app.', 'feature.')):
        if layer != 'feature':
            return True
        return not package.startswith('feature.' + owner.split(':')[2] + '.')
    if layer in ('domain', 'core', 'feature') and package.startswith('data.'):
        return True
    if layer == 'data' and package.startswith('data.'):
        return not package.startswith('data.' + owner.split(':')[2] + '.')
    if layer == 'core' and package.startswith('domain.'):
        return True
    return False


def imported_module(name):
    match = re.match(r'github\.ponyhuang\.gimi\.(domain|data|feature|core)\.([\w]+)\.', name)
    return ':' + ':'.join(match.groups()) if match else None


def check(root):
    modules = INCLUDE.findall((root / 'settings.gradle.kts').read_text())
    graph, errors = {}, []
    for module in modules:
        folder = root.joinpath(*module.strip(':').split(':'))
        build = folder / 'build.gradle.kts'
        if not build.exists():
            errors.append(f'{module}: missing build.gradle.kts')
            continue
        graph[module] = dependencies(build.read_text())
        for target in sorted(graph[module]):
            if target not in modules:
                errors.append(f'{module}: undeclared module {target}')
            elif not allowed_dependency(module, target):
                errors.append(f'{module}: forbidden production dependency {target}')
        source = folder / 'src'
        if not source.exists():
            continue
        for source_set in sorted(source.iterdir()):
            if not source_set.is_dir() or 'test' in source_set.name.lower():
                continue
            for file in sorted(source_set.rglob('*.kt')):
                content = file.read_text()
                for match in IMPORT.finditer(content):
                    name = match.group(1)
                    if forbidden_import(module, name):
                        line = content[:match.start()].count('\n') + 1
                        errors.append(f'{file.relative_to(root)}:{line}: forbidden import {name}')
                    target = imported_module(name)
                    if target in modules and target != module and target not in graph[module]:
                        errors.append(f'{file.relative_to(root)}: missing direct dependency {target}')
    errors.extend(cycles(graph))
    return modules, errors


def signature(error):
    """Normalize an error into a stable baseline entry (no line numbers)."""
    return re.sub(r'(\.kt):\d+:', r'\1:', error.replace('\\', '/'))


def load_baseline(path=BASELINE_PATH):
    if not path.exists():
        return frozenset()
    return frozenset(
        line.strip()
        for line in path.read_text(encoding='utf-8').splitlines()
        if line.strip() and not line.startswith('#')
    )


def split_new(errors, baseline):
    """Return (known_count, new_errors) comparing current errors to the baseline."""
    new = [error for error in errors if signature(error) not in baseline]
    return len(errors) - len(new), sorted(new, key=str.lower)


def rule_for(error):
    for marker, rule_id, hint in RULES:
        if marker in error:
            return rule_id, hint
    return 'ARCH000', 'inspect the reported module boundary'


if __name__ == '__main__':
    modules, errors = check(ROOT)
    if '--update-baseline' in sys.argv:
        entries = sorted({signature(error) for error in errors})
        header = (
            '# Known architecture violations; regenerated only by:\n'
            '#   python .github/scripts/check_architecture.py --update-baseline\n'
            '# Signatures carry no line numbers, so unrelated edits keep entries valid.\n'
            '# Shrink this file by fixing the violation, never by editing it by hand.\n'
        )
        BASELINE_PATH.write_text(header + '\n'.join(entries) + '\n', encoding='utf-8')
        print(f'Baseline updated: {len(entries)} known violation(s).')
        sys.exit(0)
    known, new = split_new(errors, load_baseline())
    if new:
        for error in new:
            rule_id, hint = rule_for(error)
            print(f'[{rule_id}] {error}\n    fix: {hint}', file=sys.stderr)
        print(f'Architecture check failed: {len(new)} new, {known} known in baseline.', file=sys.stderr)
        sys.exit(1)
    print(f'Architecture checks passed for {len(modules)} modules. '
          f'{known} known violation(s) in baseline, 0 new.')
