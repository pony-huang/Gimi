package github.ponyhuang.gimi.data.mobileuse

import android.app.ActivityOptions
import android.content.ComponentName
import android.content.Intent
import java.lang.reflect.InvocationTargetException
import java.util.function.Consumer

/** 以 shell 身份在副屏启动应用，先检查任务占用，调用开始后的异常绝不触发第二次启动。 */
internal class MobileAppLauncher {
    fun launch(component: String, displayId: Int, command: Consumer<Array<String>>): Int {
        require(displayId > 0)
        require(component.matches(Regex("[A-Za-z0-9_.$]+/[A-Za-z0-9_.$]+")))
        val target = requireNotNull(ComponentName.unflattenFromString(component))
        val taskManager: Any
        val tasks: List<MobileAppTask>
        val userId: Int
        try {
            taskManager = Class.forName("android.app.ActivityTaskManager").getMethod("getService").invoke(null)
            tasks = tasks(taskManager)
            userId = Class.forName("android.app.ActivityManager").getMethod("getCurrentUser").invoke(null) as Int
        } catch (_: ReflectiveOperationException) {
            return MobileAppLaunchPolicy.INSPECTION_UNAVAILABLE
        } catch (_: RuntimeException) {
            return MobileAppLaunchPolicy.INSPECTION_UNAVAILABLE
        }
        MobileAppLaunchPolicy.rejection(tasks, target.packageName, userId, displayId)?.let { return it }
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
            .setComponent(target).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        val options = ActivityOptions.makeBasic().setLaunchDisplayId(displayId).toBundle()
        val start = taskManager.javaClass.methods.firstOrNull {
            it.name == "startActivityAsUser" && it.parameterTypes.size == 12
        }
        if (start == null) {
            command.accept(arrayOf("am", "start", "--user", "current", "--display", displayId.toString(), "-n", component))
            return MobileAppLaunchPolicy.STARTED
        }
        val result = try {
            start.invoke(taskManager, null, "com.android.shell", null, intent, null, null, null, 0, 0, null, options, -2) as Int
        } catch (failure: InvocationTargetException) {
            throw IllegalStateException("App launch result unknown; inspect before retrying", failure.cause)
        }
        return if (result >= 0) MobileAppLaunchPolicy.STARTED else MobileAppLaunchPolicy.START_REJECTED
    }

    private fun tasks(manager: Any): List<MobileAppTask> {
        val methods = manager.javaClass.methods.filter { it.name == "getTasks" }
        val withDisplay = methods.firstOrNull { it.parameterTypes.size == 4 }
        val raw = if (withDisplay != null) withDisplay.invoke(manager, 200, false, false, -1)
        else methods.first { it.parameterTypes.size == 3 }.invoke(manager, 200, false, false)
        val type = Class.forName("android.app.TaskInfo")
        val display = type.getField("displayId")
        val user = type.getField("userId")
        val base = type.getField("baseActivity")
        val top = type.getField("topActivity")
        val visible = try { type.getField("isVisible") } catch (_: NoSuchFieldException) { null }
        return (raw as List<*>).filterNotNull().map { task ->
            MobileAppTask(display.getInt(task), user.getInt(task),
                (base.get(task) as? ComponentName)?.packageName, (top.get(task) as? ComponentName)?.packageName,
                // 看不到可见性时保守地保护主屏上的所有匹配任务。
                visible?.getBoolean(task) ?: true)
        }
    }
}
