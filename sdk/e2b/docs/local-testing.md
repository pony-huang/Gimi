# 本地验证与真实 E2B 联调

本指南从仓库根目录操作。SDK 是独立 JVM 工程，可以直接在 Windows、macOS 或 Linux 上验证，不需要启动 Android 应用。先运行无额度消耗的单元测试，再运行手动云端 smoke 程序。

## 1. 准备环境

安装 JDK 21，确认 `java -version` 显示 21。Gradle 使用 SDK 自带的 wrapper，不需要全局安装。

本地网络必须能访问 `api.e2b.app` 与 `sandbox.e2b.app` 的 HTTPS 443 端口。创建响应若返回其他官方 domain，还需要放行对应 `sandbox.<domain>`。Gradle 首次运行另外需要访问 Gradle 分发站、插件仓库和 Maven Central。

可先探测 control plane（不带 key，不创建沙箱）：

```shell
curl --connect-timeout 10 --max-time 20 https://api.e2b.app/health
```

Windows PowerShell 若 `curl` 是别名，使用 `curl.exe`。HTTP 200 表示这个服务可达；不证明 key 有效，也不证明沙箱数据接口可达。若出现 `CONNECT tunnel failed, response 403`，请求被代理/网络白名单拦截，尚未到 E2B；需要调整所在环境的网络访问规则。Codex 云环境的白名单与本机网络独立，修改本机配置不会改变云环境权限。

## 2. 运行单元测试与构建

Windows PowerShell：

```powershell
.\sdk\e2b\gradlew.bat -p sdk/e2b test jar sourcesJar generatePomFileForSdkPublication --console=plain
```

macOS/Linux：

```bash
./sdk/e2b/gradlew -p sdk/e2b test jar sourcesJar generatePomFileForSdkPublication --console=plain
```

这些测试使用 MockWebServer，不读取真实 key，也不创建远端沙箱。成功时显示 `BUILD SUCCESSFUL`；当前基线为 26 项测试。

| 产物 | 相对仓库根目录的位置 |
| --- | --- |
| 测试报告 | `sdk/e2b/build/reports/tests/test/index.html` |
| SDK JAR | `sdk/e2b/build/libs/e2b-sdk-0.1.0-SNAPSHOT.jar` |
| sources JAR | `sdk/e2b/build/libs/e2b-sdk-0.1.0-SNAPSHOT-sources.jar` |
| Maven POM | `sdk/e2b/build/publications/sdk/pom-default.xml` |

以上生成产物均被 Git 忽略。

## 3. 创建本地 smoke 工程

在仓库根目录创建以下三个文件。`temp/` 已被忽略，测试工程不会进入 commit；不要把这些文件建在 SDK 的 `src/test` 下。

```text
temp/e2b-smoke/
  settings.gradle.kts
  build.gradle.kts
  src/main/java/Main.java
```

`temp/e2b-smoke/settings.gradle.kts`：

```kotlin
pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
    }
}
rootProject.name = "e2b-smoke"
includeBuild("../../sdk/e2b")
```

`temp/e2b-smoke/build.gradle.kts`：

```kotlin
plugins { application }
repositories { mavenCentral() }
java { toolchain { languageVersion.set(JavaLanguageVersion.of(21)) } }
dependencies { implementation("dev.e2b.community:e2b-sdk:0.1.0-SNAPSHOT") }
application { mainClass.set("Main") }
tasks.withType<JavaCompile>().configureEach { options.encoding = "UTF-8" }
```

composite build 会把这个 Maven 坐标替换成当前 `sdk/e2b` 源码，不必先 publishToMavenLocal，也不需要修改宿主的根 settings。

`temp/e2b-smoke/src/main/java/Main.java`：

```java
import dev.e2b.sdk.*;
import java.util.Arrays;
import java.util.Collections;
import java.util.UUID;

public final class Main {
    private static void check(boolean condition, String step) {
        if (!condition) throw new IllegalStateException("FAIL: " + step);
        System.out.println("PASS: " + step);
    }

    public static void main(String[] args) {
        E2BConfig config = E2BConfig.builder().requestTimeoutSeconds(30).build();
        String marker = UUID.randomUUID().toString();
        try (E2BClient client = new E2BClient(config)) {
            Sandbox sandbox = client.create(new CreateOptions(
                "base", 180, Collections.singletonMap("smokeRun", marker),
                Collections.emptyMap()
            ));
            String id = sandbox.getSandboxId();
            System.out.println("Sandbox: " + id + ", smokeRun: " + marker);
            try {
                check(sandbox.isRunning(), "create/getInfo/isRunning");
                sandbox.setTimeout(180);
                check(client.list(new ListOptions(
                    100, null, Arrays.asList("running"),
                    Collections.singletonMap("smokeRun", marker)
                )).getItems().stream().anyMatch(item -> id.equals(item.getSandboxId())),
                    "list with metadata filter");

                CommandResult command = sandbox.getCommands().run(
                    "printf 'hello SDK\\n'; printf 'stderr SDK\\n' >&2"
                );
                check(command.getExitCode() == 0
                    && "hello SDK\n".equals(command.getStdout())
                    && "stderr SDK\n".equals(command.getStderr()), "command output");
                try {
                    sandbox.getCommands().run("exit 7");
                    throw new IllegalStateException("Missing CommandExitException");
                } catch (CommandExitException expected) {
                    check(expected.getResult().getExitCode() == 7, "non-zero exit");
                }

                String dir = "/tmp/e2b-sdk-smoke";
                String text = "你好 E2B\n";
                byte[] bytes = new byte[] {0, 1, 2, (byte) 255};
                check(sandbox.getFiles().makeDir(dir), "makeDir");
                sandbox.getFiles().write(dir + "/message.txt", text);
                check(text.equals(sandbox.getFiles().read(dir + "/message.txt")),
                    "UTF-8 file round trip");
                sandbox.getFiles().write(dir + "/binary.bin", bytes);
                check(Arrays.equals(bytes, sandbox.getFiles().readBytes(dir + "/binary.bin")),
                    "binary file round trip");
                check(sandbox.getFiles().getInfo(dir + "/binary.bin").getSize() == bytes.length,
                    "file metadata");
                check(sandbox.getFiles().list(dir).size() >= 2, "list directory");
                sandbox.getFiles().rename(dir + "/binary.bin", dir + "/moved.bin");
                check(!sandbox.getFiles().exists(dir + "/binary.bin")
                    && sandbox.getFiles().exists(dir + "/moved.bin"), "rename/exists");
                sandbox.getFiles().remove(dir + "/moved.bin");
                check(!sandbox.getFiles().exists(dir + "/moved.bin"), "remove");

                CommandOptions inputOptions = new CommandOptions(
                    null, Collections.emptyMap(), null, 30, true
                );
                try (CommandHandle handle = sandbox.getCommands().start("cat", inputOptions)) {
                    handle.sendStdin("stdin test\n");
                    handle.closeStdin();
                    check("stdin test\n".equals(handle.await().getStdout()),
                        "background command/stdin/EOF");
                }

                // 暂停阶段不使用 Sandbox 的 try-with-resources，否则作用域结束会销毁沙箱。
                check(sandbox.pause(), "pause");
                check("paused".equals(client.getInfo(id).getState()), "paused state");
                sandbox = client.connect(id, 180);
                check(sandbox.isRunning(), "connect/resume");
                check(text.equals(sandbox.getFiles().read(dir + "/message.txt")),
                    "file survives pause/resume");
                System.out.println("Application host: " + sandbox.getHost(8080));
            } finally {
                // 只清理本次创建的 ID，不删除账号中的其他沙箱。
                client.kill(id);
                check(!sandbox.isRunning(), "sandbox cleanup");
            }
            System.out.println("ALL CHECKS PASSED");
        }
    }
}
```

此程序创建一个 `base` 沙箱，TTL 为 180 秒；正常完成或检查失败都会进入 finally，尝试删除本次沙箱。若进程被强制结束或清理时断网，finally 可能不执行/不成功，需要按第 6 节手动清理。

## 4. 设置 key 并运行

key 只通过当前终端的 `E2B_API_KEY` 环境变量提供。SDK 自动读取该变量。使用仍然有效、有额度的 key；不要把它填进 Java 文件、Gradle 文件或文档。

Windows PowerShell，在仓库根目录：

```powershell
$env:E2B_API_KEY = Read-Host "E2B_API_KEY"
.\sdk\e2b\gradlew.bat -p temp/e2b-smoke run --console=plain --no-daemon
```

macOS/Linux 的 Bash，在仓库根目录：

```bash
read -r -s -p 'E2B_API_KEY: ' E2B_API_KEY
export E2B_API_KEY
printf '\n'
./sdk/e2b/gradlew -p temp/e2b-smoke run --console=plain --no-daemon
```

真实运行会消耗 E2B 额度。`--no-daemon` 让这次运行使用独立 Gradle 进程，方便每次切换 key。成功输出应包含逐项 `PASS`、`sandbox cleanup`、`ALL CHECKS PASSED` 和 `BUILD SUCCESSFUL`。只看到 BUILD SUCCESSFUL 的编译命令不等于已经完成云端联调。

结束后可清除当前终端变量：PowerShell 使用 `Remove-Item Env:E2B_API_KEY`，Bash 使用 `unset E2B_API_KEY`。

## 5. 代理与错误定位

Java/OkHttp 不保证自动读取终端的 `HTTP_PROXY` / `HTTPS_PROXY`。如果浏览器或 curl 能访问而 JVM 报 DNS/连接错误，确认本地代理需要使用的主机和端口；可以为当前终端设置 JVM 代理参数，然后重新运行。以下 `7890` 只是 HTTP 代理示例，请替换成实际端口：

PowerShell：

```powershell
$env:JAVA_TOOL_OPTIONS = "-Dhttps.proxyHost=127.0.0.1 -Dhttps.proxyPort=7890 -Dhttp.proxyHost=127.0.0.1 -Dhttp.proxyPort=7890"
```

Bash：

```bash
export JAVA_TOOL_OPTIONS='-Dhttps.proxyHost=127.0.0.1 -Dhttps.proxyPort=7890 -Dhttp.proxyHost=127.0.0.1 -Dhttp.proxyPort=7890'
```

这些参数会同时应用到 Gradle 和它启动的 Java 程序。如果原来已有 JAVA_TOOL_OPTIONS，应保留原参数并追加。企业代理若签发自己的证书，需要把其 CA 配置到 JDK truststore，保留 TLS 证书校验。

| 现象 | 处理 |
| --- | --- |
| `Set E2B_API_KEY or provide an API key` | 在运行 Gradle 的同一终端设置环境变量，或配置 IDE Run Configuration 的环境变量 |
| 代理 `CONNECT ... 403`，请求没有到 E2B | 放行 API 和响应对应的沙箱域名；这个错误不能判断 key 是否失效 |
| `AuthenticationException`，E2B 返回 401/403 | 检查 key 有效期、账号/模板权限以及具体操作的套餐支持；暂停不支持不能直接认定为 SDK 协议错误 |
| `RateLimitException` | 查看 retryAfter，停止并发运行，按服务端提示间隔重试 |
| 创建成功，但 command/file 操作连接失败 | 确认 `sandbox.<domain>` 可达，而不只是 API 域名；使用 console 输出的 ID 清理远端沙箱 |
| `CommandExitException` | 检查 result 的 exitCode/stdout/stderr；smoke 中的 `exit 7` 是预期验证 |
| `TransportException` | 检查 cause 的 DNS、代理、证书或 timeout 原因，并记录失败步骤 |
| `ProtocolException` | 保留失败操作、sandbox ID、envdVersion 及脱敏后的异常信息，提交协议兼容问题 |

请求超时、命令超时、沙箱 TTL 和 key 的有效期是四件不同的事：request timeout 默认 60 秒（本程序设为 30），command timeout 默认 60 秒（stdin 示例设为 30），sandbox TTL 本程序为 180 秒。key 是否“两小时后失效”由凭据来源决定，SDK 不设置其有效期；加长沙箱 TTL 不能延长 key 的有效期。

## 6. 检查清理结果

正常程序最后已经按 ID kill，并确认 `isRunning == false`。还可以在 E2B 控制台按程序输出的 sandbox ID 核对。不要使用“删除所有沙箱”操作，因为账号可能存在其他工作负载。

若异常中断，使用仍有效的 key 和输出的 ID，在同一个 main 中暂时替换为下面的清理逻辑，再执行 run：

```java
E2BConfig config = E2BConfig.builder().build();
try (E2BClient client = new E2BClient(config)) {
    boolean removed = client.kill("替换为本次测试输出的 sandbox ID");
    System.out.println(removed ? "Deleted" : "Already absent");
}
```

已暂停沙箱的 TTL 不应作为主动清理的替代。清理失败应在控制台再次核对，不把本地 `client.close()` 当作远端销毁。

## 已有验证与真实联调记录

SDK 初次提交时，26 项 MockWebServer/Java 编译测试通过，JAR、sources JAR、POM 和 Gradle metadata 已生成；当时的云环境因网络白名单限制，未完成真实账号联调。

2026-10-12，在 Windows 本机基于 `feature/e2bsanbox` 的 `3d23b01f`，使用有效凭据通过当前 SDK 完成以下真实实例抽样。测试工程放在忽略目录 `temp/e2b-smoke/`，凭据仅通过进程环境变量传入。

| 范围 | 实际验证 | 结果 |
| --- | --- | --- |
| 生命周期 | 创建、状态查询、暂停、按原 ID 重连恢复、销毁后 not-found | 通过 |
| 命令 | stdout/stderr、非零退出码异常、后台 stdin 输入与 EOF | 通过 |
| 代码执行 | 写入 Python/JavaScript 文件后通过命令运行；参数、环境变量、工作目录、生成中文文件与异常 stderr | 通过 |
| 文件持久化 | 关闭客户端后新客户端重连；两次暂停恢复后中文及 4096 字节二进制内容、覆盖、重命名、删除状态 | 通过 |
| CLI 安装持久化 | `python3 -m pip install --user pyfiglet` 安装 1.0.4，运行 `pyfiglet PERSIST`；暂停并关闭客户端后由新客户端恢复，命令路径、版本、安装位置和输出与恢复前完全一致 | 通过 |

每轮测试均主动删除该轮创建的沙箱并确认资源不存在；没有删除账号中的其他实例。CLI 安装位置为 `/home/user/.local/bin/pyfiglet`，Python 包位置为 `/home/user/.local/lib/python3.11/site-packages`。

本机使用 JDK 21 构建。JVM 直连依赖仓库等待 TLS 握手，显式配置本机代理后完成 SDK 构建及真实联调。带代理的 MockWebServer 断连用例收到 HTTP 502；清除代理参数后以 `--offline --no-daemon` 运行 `test`，26 项本地测试全部通过。这里的 offline 仅指 Gradle 依赖解析，不是云端实例测试。

本次证明同一沙箱在客户端关闭和暂停恢复后保留文件及已安装 CLI；未验证长时间暂停、TTL 到期、销毁后恢复、新建实例继承、模板/快照/卷或 Android 宿主运行。代码执行通过文件和命令接口完成，不代表实现了 Code Interpreter 的 `runCode`。本节是抽样联调记录，不是全接口兼容性保证。
