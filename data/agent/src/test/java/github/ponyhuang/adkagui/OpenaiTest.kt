package github.ponyhuang.adkagui

import com.google.adk.kt.agents.BaseAgent
import com.google.adk.kt.agents.Instruction
import com.google.adk.kt.agents.LlmAgent
import com.google.adk.kt.agents.RunConfig
import com.google.adk.kt.agents.StreamingMode
import com.google.adk.kt.annotations.Param
import com.google.adk.kt.annotations.Tool
import com.google.adk.kt.artifacts.InMemoryArtifactService
import com.google.adk.kt.events.Event
import com.google.adk.kt.runners.ReplRunner
import com.google.adk.kt.sessions.InMemorySessionService
import com.google.adk.kt.types.Content
import com.google.adk.kt.types.GenerateContentConfig
import com.google.adk.kt.webserver.AdkWebServer
import com.google.adk.kt.webserver.loaders.AgentLoader
import com.google.adk.kt.webserver.telemetry.ApiServerSpanExporter
import com.google.gson.Gson
import org.junit.Test

/**
 * github.ponyhuang.adkagui.Openai 使用样例 — 多工具 / plan 场景测试
 */
class OpenaiTest {

    /** 时间查询工具 */
    class TimeService {
        @Tool
        fun getCurrentTime(
            @Param("Name of the city to get the time for") city: String
        ): Map<String, String> {
            return mapOf("city" to city, "time" to "2026-06-27T10:30:00+08:00")
        }
    }

    /** 天气查询工具 */
    class WeatherService {
        @Tool
        fun getCurrentWeather(
            @Param("City name") city: String
        ): Map<String, Any> {
            return mapOf(
                "city" to city,
                "temperature" to 28.5,
                "condition" to "晴",
                "humidity" to 0.65,
                "windSpeed" to 12.0
            )
        }

        @Tool
        fun getForecast(
            @Param("City name") city: String,
            @Param("Number of forecast days") days: String
        ): Map<String, Any> {
            val daily = (1..days.toLong()).map { day ->
                mapOf(
                    "date" to "2026-06-${27 + day}",
                    "high" to (28 + day),
                    "low" to (18 + day),
                    "condition" to listOf("晴", "多云", "小雨", "阴").zip((1..days.toLong()))
                        .firstOrNull { it.second.toInt() == day.toInt() }?.first.orEmpty()
                )
            }
            return mapOf("city" to city, "forecast" to daily)
        }
    }

    /** 计算器工具 */
    class CalculatorService {
        @Tool
        fun calculate(
            @Param("Mathematical expression to evaluate, e.g. '2 + 3 * 4'") expression: String
        ): Map<String, Any> {
            // 简单模拟 — 真实场景用表达式引擎
            return mapOf(
                "expression" to expression,
                "result" to "mock_result_for_$expression"
            )
        }

        @Tool
        fun convertUnit(
            @Param("Numeric value") value: Double,
            @Param("Source unit, e.g. 'km', 'mile', 'kg', 'lb'") fromUnit: String,
            @Param("Target unit") toUnit: String
        ): Map<String, Any> {
            val factors = mapOf(
                "km" to mapOf("mile" to 0.621371),
                "mile" to mapOf("km" to 1.60934),
                "kg" to mapOf("lb" to 2.20462),
                "lb" to mapOf("kg" to 0.453592)
            )
            val factor = factors[fromUnit]?.get(toUnit) ?: 1.0
            return mapOf(
                "value" to value,
                "from" to fromUnit,
                "to" to toUnit,
                "result" to value * factor
            )
        }
    }

    /** 任务 / 计划管理工具（plan 测试核心） */
    class PlannerService {
        private val tasks = mutableListOf<MutableMap<String, Any>>()
        private var nextId = 1

        @Tool
        fun createPlan(
            @Param("Plan title") title: String,
            @Param("Plan description") description: String
        ): Map<String, Any> {
            val plan = mutableMapOf(
                "id" to nextId++,
                "title" to title,
                "description" to description,
                "tasks" to emptyList<Map<String, Any>>(),
                "status" to "active"
            )
            return plan
        }

        @Tool
        fun addTaskToPlan(
            @Param("Plan ID") planId: String,
            @Param("Task name") taskName: String,
            @Param("Task priority: high, medium, low") priority: String,
            @Param("Estimated hours") estimatedHours: Double
        ): Map<String, Any> {
            val task: MutableMap<String, Any> = mutableMapOf(
                "id" to nextId++,
                "planId" to planId,
                "name" to taskName,
                "priority" to priority,
                "estimatedHours" to estimatedHours,
                "status" to "pending"
            )
            tasks.add(task)
            return task
        }

        @Tool
        fun listTasks(
            @Param("Plan ID") planId: String
        ): Map<String, Any> {
            val planTasks = tasks.filter { it["planId"] == planId }
            return mapOf("planId" to planId, "tasks" to planTasks)
        }

        @Tool
        fun markTaskComplete(
            @Param("Task ID") taskId: String
        ): Map<String, Any> {
            val task = tasks.find { it["id"] == taskId }
            task?.put("status", "completed")
            return task ?: mapOf("error" to "task not found")
        }
    }

    /** 搜索 / 知识库工具 */
    class SearchService {
        @Tool
        fun searchDocuments(
            @Param("Search query") query: String,
            @Param("Maximum number of results") maxResults: String
        ): Map<String, Any> {
            val mockResults = listOf(
                mapOf(
                    "title" to "文档1：${query}介绍",
                    "score" to 0.95,
                    "snippet" to "这是关于${query}的介绍..."
                ),
                mapOf(
                    "title" to "文档2：${query}最佳实践",
                    "score" to 0.87,
                    "snippet" to "使用${query}的建议..."
                ),
                mapOf(
                    "title" to "文档3：${query}常见问题",
                    "score" to 0.72,
                    "snippet" to "${query}相关FAQ..."
                )
            ).take(maxResults.toInt())
            return mapOf("query" to query, "results" to mockResults)
        }

        @Tool
        fun getDocument(
            @Param("Document title") title: String
        ): Map<String, Any> {
            return mapOf(
                "title" to title,
                "content" to "这是<<${title}>>的完整内容。此处省略正文...\n\n## 概述\n...\n\n## 详细说明\n...",
                "wordCount" to 1520
            )
        }
    }

    /** 通知 / 消息工具 */
    class NotificationService {
        @Tool
        fun sendNotification(
            @Param("Recipient identifier") recipient: String,
            @Param("Notification title") title: String,
            @Param("Notification body content") body: String,
            @Param("Channel: email, sms, push") channel: String
        ): Map<String, Any> {
            return mapOf(
                "recipient" to recipient,
                "title" to title,
                "channel" to channel,
                "status" to "sent",
                "messageId" to "msg_${System.currentTimeMillis()}"
            )
        }
    }

    // ---- 测试方法 --------------------------------------------------


    private fun printEvents(events: Iterator<Event>) {
        val gson = Gson().newBuilder().setPrettyPrinting().create()
        events.forEach { event ->
            println(gson.toJson(event))
        }
        System.out.flush()
    }

    // ================================================================
    // 单工具测试
    // ================================================================

    val timeAgent = LlmAgent(
        name = "TimeAgent",
        model = PropertiesUtils.claudeModel(),
        instruction = Instruction("You help users check the time."),
        tools = TimeService().generatedTools(),
        generateContentConfig = GenerateContentConfig()
    )

    /** 时间查询 — 同步 */
    @Test
    fun singleTool_time_sync() {
        printEvents(
            ReplRunner(timeAgent).run(
                userId = "u1", sessionId = "s1",
                newMessage = Content.fromText(
                    "user", "现在北京时间几点？"
                ),
                runConfig = RunConfig(StreamingMode.SSE)

            )
        )
    }

    val weatherAgent = LlmAgent(
        name = "WeatherAgent",
        model = PropertiesUtils.claudeModel(),
        instruction = Instruction("You help users check weather."),
        tools = WeatherService().generatedTools()
    )

    /** 天气查询 — 同步 */
    @Test
    fun singleTool_weather_sync() {
        printEvents(
            ReplRunner(weatherAgent).run(
                "u1", "s1",
                Content.fromText("user", "上海今天天气怎么样？")
            )
        )
    }

    val calcAgent = LlmAgent(
        name = "CalcAgent",
        model = PropertiesUtils.claudeModel(),
        instruction = Instruction("You help users with calculations."),
        tools = CalculatorService().generatedTools()
    )

    /** 计算器 — 同步 */
    @Test
    fun singleTool_calculator_sync() {
        printEvents(
            ReplRunner(calcAgent).run(
                "u1", "s1",
                Content.fromText("user", "计算 (3 + 5) * 12 的结果")
            )
        )
    }

    // ================================================================
    // 多工具组合测试 — plan 场景
    // ================================================================

    /** Plan 场景 1：时间 + 天气组合查询 */
    @Test
    fun multiTool_timeAndWeather_sync() {
        val tools = TimeService().generatedTools() + WeatherService().generatedTools()
        val agent = LlmAgent(
            name = "MultiAgent",
            model = PropertiesUtils.openaiModel(),
            instruction = Instruction("You are a helpful assistant. Use tools to answer questions."),
            tools = tools
        )
        printEvents(
            ReplRunner(agent).run(
                "u1", "s1",
                Content.fromText("user", "查一下北京当前的天气和时间")
            )
        )
    }

    /** Plan 场景 2：计划创建与管理 */
    @Test
    fun multiTool_planManagement_sync() {
        val agent = LlmAgent(
            name = "PlannerAgent",
            model = PropertiesUtils.claudeModel(),
            instruction = Instruction(
                """You are a project planner. Help users create plans and manage tasks.
First create a plan, then add tasks, and show the task list."""
            ),
            tools = PlannerService().generatedTools()
        )
        printEvents(
            ReplRunner(agent).run(
                "u1", "s1",
                Content.fromText(
                    "user",
                    "帮我创建一个「周末购物计划」，加上三个任务：去超市买菜、去书店买书、去商场买衣服"
                )
            )
        )
    }

    /** Plan 场景 3：计划 + 通知组合 */
    @Test
    fun multiTool_planAndNotify_sync() {
        val tools = PlannerService().generatedTools() + NotificationService().generatedTools()
        val agent = LlmAgent(
            name = "PlanNotifyAgent",
            model = PropertiesUtils.claudeModel(),
            instruction = Instruction(
                """You help users create plans and send notifications.
After creating tasks, send a notification to confirm."""
            ),
            tools = tools
        )
        printEvents(
            ReplRunner(agent).run(
                "u1", "s1",
                Content.fromText(
                    "user",
                    "创建一个「项目上线计划」，添加两个任务：代码审查和部署上线，完成后通知我"
                )
            )
        )
    }

    /** Plan 场景 4：搜索 + 文档获取 + 计划 */
    @Test
    fun multiTool_searchAndPlan_sync() {
        val tools = SearchService().generatedTools() + PlannerService().generatedTools()
        val agent = LlmAgent(
            name = "ResearchAgent",
            model = PropertiesUtils.claudeModel(),
            instruction = Instruction(
                """You are a research assistant. Search for information,
then organize findings into a plan."""
            ),
            tools = tools
        )
        printEvents(
            ReplRunner(agent).run(
                "u1", "s1",
                Content.fromText(
                    "user",
                    "搜索关于 Jetpack Compose 的资料，然后创建一个学习计划"
                )
            )
        )
    }

    /** Plan 场景 5：天气 + 计划（外出决策） */
    @Test
    fun multiTool_weatherAndPlan_sync() {
        val tools = WeatherService().generatedTools() + PlannerService().generatedTools()
        val agent = LlmAgent(
            name = "OutdoorPlanner",
            model = PropertiesUtils.claudeModel(),
            instruction = Instruction(
                """You help users plan outdoor activities. Check weather first,
then create a plan based on conditions."""
            ),
            tools = tools
        )
        printEvents(
            ReplRunner(agent).run(
                "u1", "s1",
                Content.fromText(
                    "user",
                    "我计划这周末去杭州玩两天，帮我查天气并做一个出行计划"
                )
            )
        )
    }

    /** Plan 场景 6：全部工具组合 */
    @Test
    fun multiTool_allTools_sync() {
        val tools = TimeService().generatedTools() +
                WeatherService().generatedTools() +
                CalculatorService().generatedTools() +
                PlannerService().generatedTools() +
                SearchService().generatedTools() +
                NotificationService().generatedTools()
        val agent = LlmAgent(
            name = "FullAssistant",
            model = PropertiesUtils.openaiModel(),
            instruction = Instruction(
                """You are a comprehensive assistant. You can:
- Check time and weather
- Perform calculations and unit conversions
- Create plans and manage tasks
- Search documents
- Send notifications
Use the most appropriate tools to help the user."""
            ),
            tools = tools
        )
        printEvents(
            ReplRunner(agent).run(
                "u1", "s1",
                Content.fromText(
                    "user",
                    "帮我规划明天去上海的行程：查天气和时间，计算从北京到上海1200km大约需要多少英里，搜索上海旅游攻略，最后创建一个行程计划并通知我"
                ),
                RunConfig(StreamingMode.SSE)
            )
        )
    }

    // ================================================================
    // Plan 场景 — 多步推理测试
    // ================================================================

    /** 多步推理：先查天气 → 再决定是否创建户外计划 */
    @Test
    fun plan_multiStep_conditionalPlan_sync() {
        val tools = WeatherService().generatedTools() + PlannerService().generatedTools()
        val agent = LlmAgent(
            name = "ConditionalPlanner",
            model = PropertiesUtils.claudeModel(),
            instruction = Instruction(
                """You make conditional plans. Check weather first.
If the weather is good (sunny/clear), create an outdoor activity plan.
If bad (rain), create an indoor activity plan instead."""
            ),
            tools = tools
        )
        printEvents(
            ReplRunner(agent).run(
                "u1", "s1",
                Content.fromText("user", "帮我安排明天下午的活动")
            )
        )
    }

    /** 多步推理：搜索 → 阅读文档 → 总结 → 创建待办 */
    @Test
    fun plan_multiStep_researchAndTodo_sync() {
        val tools = SearchService().generatedTools() + PlannerService().generatedTools()
        val agent = LlmAgent(
            name = "ResearchTodoAgent",
            model = PropertiesUtils.claudeModel(),
            instruction = Instruction(
                """You research topics and create actionable todo lists.
1. Search for relevant documents
2. Get the most relevant document content
3. Create a plan with tasks based on what you learned"""
            ),
            tools = tools
        )
        printEvents(
            ReplRunner(agent).run(
                "u1", "s1",
                Content.fromText(
                    "user",
                    "帮我研究一下 Kotlin Coroutines，然后制定一个两周的学习计划"
                )
            )
        )
    }

    /** 多步推理：计算 → 单位转换 → 计划 */
    @Test
    fun plan_multiStep_calculateAndPlan_sync() {
        val tools = CalculatorService().generatedTools() + PlannerService().generatedTools()
        val agent = LlmAgent(
            name = "BudgetPlanner",
            model = PropertiesUtils.claudeModel(),
            instruction = Instruction(
                """You help with budget planning. Use the calculator for numbers,
then organize results into a plan."""
            ),
            tools = tools
        )
        printEvents(
            ReplRunner(agent).run(
                "u1", "s1",
                Content.fromText(
                    "user",
                    "我预算5000元，帮我算一下如果每天花150元可以用多少天，然后把结果整理成一个预算计划"
                )
            )
        )
    }

    val tools = TimeService().generatedTools() +
            WeatherService().generatedTools() +
            CalculatorService().generatedTools() +
            PlannerService().generatedTools() +
            SearchService().generatedTools() +
            NotificationService().generatedTools()
    val fullAssistant = LlmAgent(
        name = "FullAssistant",
        model = PropertiesUtils.claudeModel(),
        instruction = Instruction(
            """You are a comprehensive assistant. You can:
- Check time and weather
- Perform calculations and unit conversions
- Create plans and manage tasks
- Search documents
- Send notifications
Use the most appropriate tools to help the user."""
        ),
        tools = tools
    )


    @Test
    fun openaiModel_withDeepSeek_webServer() {
        // 启用 debug 日志

        val sessionService = InMemorySessionService()
        val artifactService = InMemoryArtifactService()
        val server = AdkWebServer(
            port = 8080,
            sessionService = sessionService,
            artifactService = artifactService,
            agentLoader = createAgentLoader(),
            apiServerSpanExporter = ApiServerSpanExporter(),
        )
        server.start(wait = true)

    }

    fun createAgentLoader(): AgentLoader {
        return object : AgentLoader {
            private val agents = mapOf(
                "CalcAgent" to calcAgent,
                "WeatherAgent" to weatherAgent,
                "TimeAgent" to timeAgent,
                "FullAssistant" to fullAssistant,
            )

            override fun listAgents(): List<String> {
                return agents.keys.toList()
            }

            override fun loadAgent(agentName: String): BaseAgent? {
                return agents[agentName]
            }
        }
    }
}