package github.ponyhuang.gimi.data.mobileuse;

import android.view.InputEvent;
import android.view.KeyEvent;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

/** shell 进程共用的输入注入入口；绑定屏号，按键等待系统确认目标，触摸按时序异步发送。 */
final class MobileInputInjector {
    private Object manager;
    private Method inject;
    private Method setDisplay;
    private boolean unavailable;

    synchronized boolean send(InputEvent event, int displayId) {
        if (displayId <= 0) throw new IllegalArgumentException("Only an owned secondary display may receive input");
        if (unavailable) return false;
        if (manager == null) {
            try {
                Class<?> type;
                try { type = Class.forName("android.hardware.input.InputManagerGlobal"); }
                catch (ClassNotFoundException missing) { type = Class.forName("android.hardware.input.InputManager"); }
                manager = type.getMethod("getInstance").invoke(null);
                inject = type.getMethod("injectInputEvent", InputEvent.class, int.class);
                setDisplay = InputEvent.class.getMethod("setDisplayId", int.class);
            } catch (ReflectiveOperationException failure) {
                // 解析接口阶段尚未发送事件，可以明确拒绝并交给命令兜底。
                unavailable = true;
                return false;
            }
        }
        try {
            setDisplay.invoke(event, displayId);
        } catch (ReflectiveOperationException failure) {
            return false; // 仍未进入注入调用。
        }
        try {
            return Boolean.TRUE.equals(inject.invoke(manager, event, event instanceof KeyEvent ? 1 : 0));
        } catch (InvocationTargetException failure) {
            // Binder 异常无法证明事件没送达，不能在这里把异常转成 false 再执行 input 命令。
            throw new IllegalStateException("Input delivery unknown; do not repeat", failure.getCause());
        } catch (IllegalAccessException failure) {
            return false;
        }
    }
}
