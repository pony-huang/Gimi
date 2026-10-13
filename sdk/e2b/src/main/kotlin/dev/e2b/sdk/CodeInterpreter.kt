package dev.e2b.sdk

/**
 * Code Interpreter execution options; an empty context uses the language's persistent default kernel.
 * @property language Interpreter language, default python.
 * @property envs Environment variables for this execution only.
 * @property timeoutSeconds Total execution deadline in seconds; zero disables it.
 * @property maxOutputBytes Maximum encoded response bytes read into memory.
 */
data class CodeOptions @JvmOverloads constructor(
    val language: String = "python",
    val envs: Map<String, String> = emptyMap(),
    val timeoutSeconds: Int = 60,
    val maxOutputBytes: Int = 4 * 1024 * 1024,
) {
    init {
        require(language.isNotBlank())
        require(timeoutSeconds >= 0)
        require(maxOutputBytes > 0)
    }
}

/**
 * Completed interpreter execution, including a kernel error rather than a transport failure.
 * @property stdout Concatenated stdout chunks.
 * @property stderr Concatenated stderr chunks.
 * @property text Main expression's textual result; display-only results are excluded.
 * @property error Normalized kernel error and traceback; empty on successful code execution.
 * @property executionCount Kernel execution counter, if supplied.
 */
data class CodeExecutionResult(
    val stdout: String,
    val stderr: String,
    val text: String,
    val error: String,
    val executionCount: Int?,
)

/** Stateful code execution on the sandbox's Code Interpreter service. */
class CodeInterpreter internal constructor(private val envd: Envd) {
    @JvmOverloads
    fun run(code: String, options: CodeOptions = CodeOptions()): CodeExecutionResult {
        require(code.isNotBlank())
        return envd.executeCode(code, options).use { response ->
            val source = response.body?.source() ?: throw ProtocolException("Missing interpreter body")
            val stdout = StringBuilder()
            val stderr = StringBuilder()
            var text = ""
            var error = ""
            var executionCount: Int? = null
            var bytesRead = 0L
            try {
            while (!network { source.exhausted() }) {
                val remaining = options.maxOutputBytes.toLong() - bytesRead
                // 先限制扫描长度，再读取整行，避免富媒体结果或恶意响应无限占用内存。
                val newline = network { source.indexOf('\n'.code.toByte(), 0, remaining + 1) }
                val lineBytes = if (newline >= 0) newline + 1 else source.buffer.size
                if (lineBytes > remaining) {
                    throw ProtocolException("Interpreter output exceeds configured limit")
                }
                val line = network { source.readUtf8(lineBytes) }.trim()
                bytesRead += lineBytes
                if (line.isEmpty()) continue
                val message = Json.obj(Json.parse(line))
                fun string(name: String): String {
                    val value = message.get(name)?.takeUnless { it.isJsonNull } ?: return ""
                    if (!value.isJsonPrimitive || !value.asJsonPrimitive.isString) throw ProtocolException("Invalid interpreter text field")
                    return value.asString
                }
                when (string("type")) {
                    "stdout" -> stdout.append(string("text"))
                    "stderr" -> stderr.append(string("text"))
                    "result" -> {
                        val main = message.get("is_main_result")
                        if (main != null && (!main.isJsonPrimitive || !main.asJsonPrimitive.isBoolean)) {
                            throw ProtocolException("Invalid interpreter result flag")
                        }
                        if (main?.asBoolean == true) text = string("text")
                    }
                    "error" -> {
                        val trace = message.get("traceback")
                        val traceback = when {
                            trace == null || trace.isJsonNull -> ""
                            trace.isJsonArray -> trace.asJsonArray.joinToString("\n") {
                                if (!it.isJsonPrimitive || !it.asJsonPrimitive.isString) throw ProtocolException("Invalid interpreter traceback")
                                it.asString
                            }
                            else -> string("traceback")
                        }
                        error = "${string("name")}: ${string("value")}" + traceback.takeIf { it.isNotEmpty() }?.let { "\n$it" }.orEmpty()
                    }
                    "number_of_executions" -> {
                        val count = message.get("execution_count")
                        if (count == null || !count.isJsonPrimitive || !count.asJsonPrimitive.isNumber) {
                            throw ProtocolException("Invalid interpreter execution count")
                        }
                        executionCount = count.asInt
                    }
                }
            }
            CodeExecutionResult(stdout.toString(), stderr.toString(), text, error, executionCount)
            } catch (failure: E2BException) {
                val partial = stdout.toString() + stderr.toString()
                if (partial.isEmpty()) throw failure
                throw PartialOutputException(failure, partial)
            }
        }
    }
}
