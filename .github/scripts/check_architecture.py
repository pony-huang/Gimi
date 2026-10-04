"""Check declared production dependencies and imports without an Android SDK.

This is a source-level guard for this repository's literal Kotlin Gradle DSL.
It does not resolve Gradle's transitive dependencies or dynamically computed DSL.
"""
from pathlib import Path
import re
import sys

ROOT = Path(__file__).resolve().parents[2]
PROJECT = re.compile(r'(\w+)\s*\(\s*project\(\s*"(:[^"]+)"\s*\)\s*\)')
INCLUDE = re.compile(r'include\(\s*"(:[^"]+)"\s*\)')
IMPORT = re.compile(r'^import\s+([\w.]+)', re.MULTILINE)
DOMAIN_FORBIDDEN = (
    'android.', 'androidx.', 'dagger.', 'okhttp3.', 'retrofit2.',
    'com.google.adk.', 'com.google.genai.', 'com.openai.', 'com.anthropic.',
    'io.objectbox.',
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
    layer = owner.split(':')[1]
    target_layer = target.split(':')[1]
    if layer == 'app':
        return True
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


if __name__ == '__main__':
    modules, errors = check(ROOT)
    if errors:
        print('\n'.join(errors), file=sys.stderr)
        sys.exit(1)
    print(f'Architecture checks passed for {len(modules)} modules.')
