# 入口 PID 用于停止整个受管进程树；wait 可被 TERM 打断并立即执行退出清理。
trap 'exit 143' TERM INT
if ! printf '%s\n' "$$" > "$3"; then exit 125; fi

# HOME 为全局共享目录（跨会话持久，承载工具登录态/配置），据此计算命令目录以支持用户配置的 npm prefix。
PATH="$HOME/.local/bin:$HOME/.npm-global/bin:$PATH"
export PATH
if command -v npm >/dev/null 2>&1; then
    npm_prefix=$(npm prefix -g 2>/dev/null)
    if [ -n "$npm_prefix" ] && [ -d "$npm_prefix/bin" ]; then
        PATH="$npm_prefix/bin:$PATH"
        export PATH
    fi
fi

# 命令作为位置参数传入，避免拼接命令文本改变引号与重定向语义。
if [ -x /bin/bash ]; then
    /bin/bash -o pipefail -c "$1" &
else
    /bin/sh -c "$1" &
fi
command_pid=$!
wait "$command_pid"
command_status=$?
if [ "$2" = "1" ] && [ "$command_status" -eq 0 ]; then
    # 保持 PRoot 入口存活，供 CLI 自行派生的授权轮询/服务进程继续运行。
    # stop_job 或禁用环境会统一结束整个 PRoot 进程树。
    while :; do sleep 3600 & wait "$!"; done
fi
exit "$command_status"
