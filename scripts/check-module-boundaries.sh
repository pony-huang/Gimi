#!/usr/bin/env bash
#
# check-module-boundaries.sh
# 静态扫描模块依赖边界，禁止：
#   - feature -> feature
#   - domain -> data / feature / app
#   - data -> data
#
# 该脚本只读取 settings.gradle.kts 与各模块的 build.gradle.kts，不引入新工具。
# 任何输出违反都让脚本以非零退出码失败，可直接挂到 CI。
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"

violations=0

check_no_dependency_between() {
    local source_group="$1"
    local target_group="$2"
    local description="$3"
    local pattern="project(\":${target_group}:"

    while IFS= read -r module; do
        if [[ "$module" == *":build.gradle.kts" ]]; then
            if grep -qE "${pattern}" "$module"; then
                echo "❌ ${module} depends on ${target_group}:* — forbidden (${description})"
                violations=$((violations + 1))
            fi
        fi
    done < <(find "${source_group}" -maxdepth 2 -name "build.gradle.kts")
}

check_no_dependency_between "feature" "feature" "feature -> feature"
check_no_dependency_between "domain" "data" "domain -> data"
check_no_dependency_between "domain" "feature" "domain -> feature"
check_no_dependency_between "domain" "app" "domain -> app"
check_no_dependency_between "data" "data" "data -> data"

# domain 模块反向依赖校验：源码不能 import data / feature / app。
echo "--- domain source import scan ---"
if grep -rn --include="*.kt" \
    -E '^import github\.ponyhuang\.gimi\.(data|feature|app)\.' \
    domain/ 2>/dev/null; then
    echo "❌ domain imports data/feature/app — forbidden"
    violations=$((violations + 1))
fi

if [[ "$violations" -ne 0 ]]; then
    echo
    echo "Module boundary guard failed: ${violations} violation(s)."
    exit 1
fi

echo "Module boundary guard passed."