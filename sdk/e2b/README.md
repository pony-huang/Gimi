# E2B Kotlin/JVM SDK

非官方 E2B SDK，先内置在 Gimi 仓库中，后续可将整个 `sdk/e2b/` 目录迁出。实现语言为 Kotlin，公开接口可直接从 Java 使用；不依赖 Android、Gimi、Hilt、项目版本目录或构建约定插件。

## 已实现

- 沙箱：`create`、`connect`（含暂停后恢复）、`getInfo`、`list`（单页、过滤与 continuation token）、`setTimeout`、`pause`、`kill`、`isRunning`、`getHost`。
- 命令：`run`、`start`（后台进程句柄）、`connect`、`list`、`sendStdin`、`closeStdin`、`kill`；句柄提供 `await`、输入和终止操作。
- 文件：文本/二进制 `read` / `write`、`list`、`getInfo`、`exists`、`makeDir`、`remove`、`rename`。
- Code Interpreter：`sandbox.code.run`，包含持久化语言内核、stdout/stderr、主表达式文本与运行错误。
- `client.cancelRequests()` 取消当前请求而不关闭客户端；`files.readBytes(path, maxBytes)` 提供有界文件读取。
- HTTP/Connect 错误、非零退出异常、分页、认证隔离、超时、Connect 帧解析与输出上限。

[接口全量盘点与优先级](docs/api-priorities.md) · [非废弃 REST 接口清单](docs/api-inventory.md) · [设计与迁出边界](docs/design.md) · [本地测试与真实联调](docs/local-testing.md)

## 构建与使用

JDK 21 用于构建，产物为 Java 8 字节码。Java 8+ 和 Kotlin/JVM 可用；使用 OkHttp 4.12 和 Gson。Android 运行时使用到 `java.util.Base64`，要求 API 26+，或由宿主配置相应 core library desugaring。Gimi 通过根工程的 `includeBuild("sdk/e2b")` 接入此库，SDK 本身仍可独立构建和迁出。

在此目录执行（Windows 使用 `gradlew.bat`）：

```shell
./gradlew test jar sourcesJar generatePomFileForSdkPublication
./gradlew publishToMavenLocal
```

当前本地坐标为 `dev.e2b.community:e2b-sdk:0.1.0-SNAPSHOT`，不代表已经发布到公共仓库，也不代表官方 SDK。提取独立仓库后应由维护者确认正式 group、版本策略和发布信息。

Kotlin：

```kotlin
val config = E2BConfig.builder().build() // 从 E2B_API_KEY 读取密钥
E2BClient(config).use { client ->
    client.create(CreateOptions(template = "base", timeoutSeconds = 300)).use { sandbox ->
        sandbox.files.write("/tmp/message.txt", "你好 E2B")
        val result = sandbox.commands.run("cat /tmp/message.txt")
        println(result.stdout)
    }
}
```

Java（无需 Kotlin 的 `Continuation`、`Flow` 或协程依赖）：

```java
E2BConfig config = E2BConfig.builder().build();
try (Sandbox sandbox = Sandbox.create(config, new CreateOptions("base"))) {
    sandbox.getFiles().write("/tmp/message.txt", "Hello E2B");
    CommandResult result = sandbox.getCommands().run("cat /tmp/message.txt");
    System.out.println(result.getStdout());
}
```

后台进程：

```kotlin
E2BClient(config).use { client ->
    client.create().use { sandbox ->
        sandbox.commands.start("cat", CommandOptions(stdin = true)).use { handle ->
            handle.sendStdin("hello\n")
            handle.closeStdin()
            println(handle.await().stdout)
        }
    }
}
```

分页：`client.list()` 返回 `SandboxPage`，下一页使用 `ListOptions(nextToken = page.nextToken)`。`hasNext == false` 时停止请求；不自动把所有沙箱加载进内存。

所有网络方法均为**阻塞调用**。Android/Kotlin 协程调用方应使用 IO 调度器，并将协程取消绑定到 `client.cancelRequests()`；SDK 不引入 suspend/异步依赖。命令与代码超时默认 60 秒，`timeoutSeconds = 0` 关闭总 deadline；请求超时与远端沙箱存活时间相互独立。

代码解释器需要对应模板，基础 `base` 模板不保证包含内核：

```kotlin
E2BClient(config).use { client ->
    client.create(CreateOptions(template = "code-interpreter-v1")).use { sandbox ->
        sandbox.code.run("x = 1")
        println(sandbox.code.run("x + 1").text)
        println(sandbox.code.run("1 + 1", CodeOptions(language = "javascript")).text)
    }
}
```

## 生命周期和错误

- `Sandbox.close()` **销毁远端沙箱**，对应官方 Python context manager。希望继续复用或暂停保存时调用 `pause()`，不要用上述 `use` / try-with-resources 的自动销毁作用域。
- `E2BClient.close()` 仅取消本地请求并释放连接；不会销毁创建过的沙箱。客户端关闭后不能继续用关联句柄调用网络方法。
- `CommandHandle.close()` 关闭本地输出监听，**不会终止远端进程**；需要显式 `kill()`。`await()` 读取到退出事件和 Connect 结束帧才返回，非零退出抛出携带 `CommandResult` 的 `CommandExitException`。
- `kill` 对已不存在的资源返回 false，`pause` 对已暂停沙箱返回 false，`exists` 只把 not-found 转为 false。鉴权错误不会被吞掉。
- 错误类型包括 `NotFoundException`、`AuthenticationException`、`RateLimitException`、`ProtocolException`、`TransportException`、`CommandExitException`；HTTP 状态、RPC code、`Retry-After` 以属性提供。错误消息不回显服务端正文。
- 输出流失败且已经取得日志时，`PartialOutputException` 提供 `partialOutput` 和原始类型化 `failure`；有界文件读取超限时抛出 `FileSizeLimitException`。
- API 密钥仅发送给 control plane；envd 单独使用创建/连接响应的访问 token。禁止自动跨域重定向和网络重试；对有副作用的操作由调用者决定是否重试。

## 首版边界

文件读写缓冲在内存，未提供流式大文件传输；有界读取不会缓存超过配置上限的完整响应。命令输出通过流式协议读取，`await()` 汇总 UTF-8 输出，默认 stdout/stderr 合计上限 4 MiB，单帧上限 16 MiB。解释器 JSONL 响应默认合计上限 4 MiB，只返回文本日志和主表达式结果。后台句柄不主动启动本地读线程，应及时 `await()` 或 `close()`；大量输出可能因流控阻塞远端进程。尚无逐条输出 callback、PTY、文件 watcher、模板构建、快照/卷或网络规则更新。

测试使用 MockWebServer，无需密钥、不会创建真实沙箱。Java 源文件测试覆盖真实 Java 编译和 try-with-resources。协议测试不能替代云端集成验证。2026-10-12 已完成真实实例抽样，覆盖命令、代码文件执行、文件操作及暂停恢复后的 CLI 安装保留；范围与限制见[真实联调记录](docs/local-testing.md#已有验证与真实联调记录)。
