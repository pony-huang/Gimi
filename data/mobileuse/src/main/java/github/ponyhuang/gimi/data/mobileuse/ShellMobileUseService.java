package github.ponyhuang.gimi.data.mobileuse;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Point;
import android.graphics.PixelFormat;
import android.hardware.display.DisplayManager;
import android.hardware.display.VirtualDisplay;
import android.media.Image;
import android.media.ImageReader;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Process;
import android.os.Bundle;
import android.os.SystemClock;
import android.util.Log;
import android.util.DisplayMetrics;
import android.view.Display;
import android.view.Surface;
import android.view.MotionEvent;
import android.view.InputDevice;
import android.view.KeyEvent;
import github.ponyhuang.gimi.domain.mobileuse.MobileKey;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.lang.reflect.Method;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;

/** Shizuku UserService。仅在 shell UID 下管理一个进程内副屏，销毁时禁止迁移目标任务到主屏。 */
public final class ShellMobileUseService extends IMobileUseService.Stub {
    private HandlerThread displayThread;
    private ImageReader reader;
    private VirtualDisplay display;
    private MobileVirtualDisplay virtualDisplay;
    private Context shellContext;
    private MobileDisplayGeometry displayGeometry;
    private Boolean landscapeOverride;
    private Object windowManagerService;
    private Integer previousImePolicy;
    private final MobileFrameBuffer frames = new MobileFrameBuffer();
    private String captureError;
    private int[] previousPixels;
    private final MobileFrameMailbox<PendingFrame> pendingFrames = new MobileFrameMailbox<>();
    private final Runnable encodeFrameTask = this::encodePendingFrame;
    private Handler frameHandler;
    private MobileDisplayRenderer renderer;
    private String previewBinding;
    private boolean previewInteractive;
    private long gestureId = -1;
    private float touchX, touchY;
    private final MobileInputInjector input = new MobileInputInjector();
    private final MobileAppLauncher appLauncher = new MobileAppLauncher();
    private MobileClipboard clipboard;

    public ShellMobileUseService() {}

    public ShellMobileUseService(Context ignored) {}

    @Override public int uid() {
        return Process.myUid();
    }

    @Override public synchronized int createDisplay() {
        requireShell();
        if (display != null) return display.getDisplay().getDisplayId();
        try {
            displayThread = new HandlerThread("gimi-mobile-use-display");
            displayThread.start();
            // ActivityThread.systemMain 会创建 Handler，必须在有 Looper 的线程上初始化。
            FutureTask<Context> shellContextTask = new FutureTask<>(() -> {
                Class<?> activityThread = Class.forName("android.app.ActivityThread");
                Object thread = activityThread.getMethod("systemMain").invoke(null);
                Context system = (Context) activityThread.getMethod("getSystemContext").invoke(thread);
                return system.createPackageContext("com.android.shell", Context.CONTEXT_IGNORE_SECURITY);
            });
            new Handler(displayThread.getLooper()).post(shellContextTask);
            shellContext = shellContextTask.get(5, TimeUnit.SECONDS);
            clipboard = new MobileClipboard(shellContext);
            displayGeometry = readGeometry();
            reader = newReader(displayGeometry);
            renderer = new MobileDisplayRenderer(reader.getSurface(), displayGeometry.width, displayGeometry.height);
            virtualDisplay = MobileVirtualDisplay.create(shellContext, displayGeometry.width, displayGeometry.height,
                    displayGeometry.densityDpi, renderer.inputSurface(), new Handler(displayThread.getLooper()));
            display = virtualDisplay.getDisplay();
            if (display == null || display.getDisplay() == null) {
                throw new IllegalStateException("Virtual display creation failed");
            }
            int displayId = display.getDisplay().getDisplayId();
            // shell 虚拟屏不能承载 IME；禁止输入框获焦时键盘回退到用户主屏。
            Class<?> global = Class.forName("android.view.WindowManagerGlobal");
            windowManagerService = global.getMethod("getWindowManagerService").invoke(null);
            Class<?> windowManagerClass = Class.forName("android.view.IWindowManager");
            Method getPolicy = windowManagerClass.getMethod("getDisplayImePolicy", int.class);
            previousImePolicy = (Integer) getPolicy.invoke(windowManagerService, displayId);
            windowManagerClass.getMethod("setDisplayImePolicy", int.class, int.class)
                    .invoke(windowManagerService, displayId, 2); // DISPLAY_IME_POLICY_HIDE
            // 避免 ROM 默认把虚拟屏当作桌面 freeform 窗口，坐标始终对应全屏内容。
            try {
                windowManagerClass.getMethod("setWindowingMode", int.class, int.class)
                        .invoke(windowManagerService, displayId, 1);
            } catch (ReflectiveOperationException failure) {
                Log.w("GimiMobileUseService", "Fullscreen windowing policy unavailable", failure);
            }
            return displayId;
        } catch (Exception failure) {
            Log.e("GimiMobileUseService", "createDisplay failed", failure);
            stop();
            throw new IllegalStateException("Virtual display unsupported", failure);
        }
    }

    @Override public int launch(String component, int displayId) {
        synchronized (this) { requireDisplay(displayId); }
        return appLauncher.launch(component, displayId, this::command);
    }

    @Override public boolean tap(int displayId, int x, int y) {
        synchronized (this) { requireDisplay(displayId); requirePoint(x, y); }
        long now = SystemClock.uptimeMillis();
        MotionEvent down = MotionEvent.obtain(now, now, MotionEvent.ACTION_DOWN, x, y, 0);
        MotionEvent up = MotionEvent.obtain(now, now + 50, MotionEvent.ACTION_UP, x, y, 0);
        down.setSource(InputDevice.SOURCE_TOUCHSCREEN);
        up.setSource(InputDevice.SOURCE_TOUCHSCREEN);
        try {
            return MobileInputDelivery.pair(() -> input.send(down, displayId), () -> input.send(up, displayId), () -> {
                command("input", "-d", Integer.toString(displayId), "tap", Integer.toString(x), Integer.toString(y));
                return true;
            });
        } finally { down.recycle(); up.recycle(); }
    }

    @Override public boolean pressKey(int displayId, String key) {
        synchronized (this) { requireDisplay(displayId); }
        // 只接受契约声明的符号键，不能把任意文本作为 shell 参数或键码执行。
        MobileKey.valueOf(key);
        int code = KeyEvent.keyCodeFromString("KEYCODE_" + key);
        if (code == KeyEvent.KEYCODE_UNKNOWN) throw new IllegalArgumentException("Unsupported key");
        long now = SystemClock.uptimeMillis();
        KeyEvent down = new KeyEvent(now, now, KeyEvent.ACTION_DOWN, code, 0, 0, -1, 0, 0, InputDevice.SOURCE_KEYBOARD);
        KeyEvent up = KeyEvent.changeAction(down, KeyEvent.ACTION_UP);
        return MobileInputDelivery.pair(() -> input.send(down, displayId), () -> input.send(up, displayId), () -> {
            command("input", "-d", Integer.toString(displayId), "keyevent", Integer.toString(code));
            return true;
        });
    }

    @Override public boolean gesture(int displayId, int x1, int y1, int x2, int y2, int holdMs, int durationMs) {
        final MobileDisplayGeometry geometry;
        synchronized (this) {
            requireDisplay(displayId);
            requirePoint(x1, y1); requirePoint(x2, y2);
            if (holdMs < 0 || holdMs > 5000 || durationMs < 0 || durationMs > 5000) {
                throw new IllegalArgumentException("Invalid gesture duration");
            }
            cancelGesture(displayId);
            geometry = displayGeometry;
        }
        long downTime = SystemClock.uptimeMillis();
        boolean pressed = false;
        try {
            if (!agentTouch(displayId, geometry, MotionEvent.ACTION_DOWN, x1, y1, downTime)) return false;
            pressed = true;
            // 等待期间不持显示锁，截图编码和预览仍可继续；整个序列由客户端输入锁串行化。
            SystemClock.sleep(holdMs);
            int steps = Math.max(1, durationMs / 16);
            for (int step = 1; durationMs > 0 && step <= steps; step++) {
                SystemClock.sleep(Math.max(0, downTime + holdMs + (long) durationMs * step / steps - SystemClock.uptimeMillis()));
                float fraction = (float) step / steps;
                if (!agentTouch(displayId, geometry, MotionEvent.ACTION_MOVE,
                        x1 + (x2 - x1) * fraction, y1 + (y2 - y1) * fraction, downTime)) {
                    throw new IllegalStateException("Gesture partially delivered");
                }
            }
            if (!agentTouch(displayId, geometry, MotionEvent.ACTION_UP, x2, y2, downTime)) {
                throw new IllegalStateException("Gesture release unavailable");
            }
            pressed = false;
            return true;
        } finally {
            // 部分投递后发生异常仍需抬起触点；此时结果未知，调用方不得自动重发手势。
            if (pressed) {
                synchronized (this) {
                    if (display != null && display.getDisplay().getDisplayId() == displayId) {
                        try { inject(displayId, MotionEvent.ACTION_CANCEL, x2, y2, downTime, SystemClock.uptimeMillis()); }
                        catch (RuntimeException ignored) { Log.w("GimiMobileUseService", "Agent gesture cancellation unavailable"); }
                    }
                }
            }
        }
    }

    private synchronized boolean agentTouch(int displayId, MobileDisplayGeometry geometry,
                                            int action, float x, float y, long downTime) {
        requireDisplay(displayId);
        if (!displayGeometry.sameAs(geometry)) throw new IllegalStateException("Display geometry changed during gesture");
        return inject(displayId, action, x, y, downTime, SystemClock.uptimeMillis());
    }

    @Override public boolean back(int displayId) {
        return pressKey(displayId, MobileKey.BACK.name());
    }

    @Override public synchronized boolean preparePaste(int displayId, String text) {
        requireDisplay(displayId);
        return clipboard != null && clipboard.prepare(text);
    }

    @Override public synchronized void restoreClipboard() {
        requireShell();
        if (clipboard != null) clipboard.restore();
    }

    @Override public Bundle runShell(int displayId, String command) {
        synchronized (this) { requireDisplay(displayId); }
        try {
            github.ponyhuang.gimi.domain.mobileuse.MobileShellResult result =
                    new MobileShellCommandExecutor().execute(displayId, command);
            Bundle payload = new Bundle();
            if (result.getExitCode() != null) payload.putInt("exitCode", result.getExitCode());
            payload.putString("output", result.getOutput());
            payload.putBoolean("timedOut", result.getTimedOut());
            payload.putBoolean("outputTruncated", result.getOutputTruncated());
            return payload;
        } catch (IOException failure) {
            throw new IllegalStateException("Shell process unavailable", failure);
        } catch (InterruptedException failure) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Shell process interrupted; inspect before retrying", failure);
        }
    }

    @Override public synchronized Bundle capture() {
        requireShell();
        if (display == null || reader == null) throw new IllegalStateException("Display stopped");
        ensureGeometry();
        Bundle result = new Bundle();
        if (captureError != null) result.putString("error", captureError);
        MobileFrameBuffer.Frame frame = frames.latest();
        if (frame == null) return result;
        result.putByteArray("jpeg", frame.jpeg);
        result.putIntArray("samples", frame.samples);
        result.putInt("width", frame.width);
        result.putInt("height", frame.height);
        result.putLong("capturedAtMs", frame.capturedAtMs);
        result.putLong("sequence", frame.sequence);
        result.putLong("generation", frame.generation);
        return result;
    }

    @Override public synchronized void setPreview(int displayId, String bindingId, Surface surface, int width, int height, boolean interactive) {
        requireDisplay(displayId);
        if (surface == null || !surface.isValid() || width <= 0 || height <= 0 || bindingId == null) {
            throw new IllegalArgumentException("Invalid preview output");
        }
        cancelGesture(displayId);
        renderer.preview(surface,width,height);
        previewBinding = bindingId;
        // 预览只控制触摸准入；全程沿用建屏时的 HIDE 策略，避免切窗触发 IME 和内容重排。
        previewInteractive = interactive;
    }

    @Override public synchronized void clearPreview(int displayId, String bindingId) {
        requireDisplay(displayId);
        // 旧窗口的销毁回调不能解绑刚切换的新窗口。
        if (previewBinding == null || !previewBinding.equals(bindingId)) return;
        cancelGesture(displayId);
        renderer.preview(null,0,0);
        previewBinding = null;
        previewInteractive = false;
    }

    @Override public synchronized boolean touch(int displayId, int action, float x, float y, long id, long eventTimeMs) {
        requireDisplay(displayId);
        if (previewBinding == null || !previewInteractive) return false;
        if (!Float.isFinite(x) || !Float.isFinite(y) || action < 0 || action > 3) return false;
        if (action == 0) {
            cancelGesture(displayId);
            if (x < 0 || y < 0 || x >= displayGeometry.width || y >= displayGeometry.height || id < 0) return false;
            if (!inject(displayId,MotionEvent.ACTION_DOWN,x,y,id,eventTimeMs)) return false;
            gestureId = id;
        } else {
            if (gestureId != id) return false;
            int nativeAction = action == 1 ? MotionEvent.ACTION_MOVE : action == 2 ? MotionEvent.ACTION_UP : MotionEvent.ACTION_CANCEL;
            x = Math.max(0,Math.min(x,displayGeometry.width-1));
            y = Math.max(0,Math.min(y,displayGeometry.height-1));
            boolean accepted = inject(displayId,nativeAction,x,y,id,eventTimeMs);
            if (action == 2 || action == 3) gestureId = -1;
            touchX = x; touchY = y;
            return accepted;
        }
        touchX = x; touchY = y;
        return true;
    }

    private boolean inject(int displayId, int action, float x, float y, long downTime, long eventTime) {
        MotionEvent event = MotionEvent.obtain(downTime,Math.max(downTime,eventTime),action,x,y,0);
        try {
            event.setSource(InputDevice.SOURCE_TOUCHSCREEN);
            return input.send(event, displayId);
        }
        finally { event.recycle(); }
    }

    private void cancelGesture(int displayId) {
        if (gestureId < 0) return;
        try { inject(displayId,MotionEvent.ACTION_CANCEL,touchX,touchY,gestureId,SystemClock.uptimeMillis()); }
        finally { gestureId = -1; }
    }

    private synchronized void queueFrame(ImageReader source, MobileDisplayGeometry geometry,
                                         long generation) {
        if (reader != source) return;
        try {
            Image image = source.acquireLatestImage();
            if (image == null) return;
            long acquiredAtMs = SystemClock.elapsedRealtime();
            PendingFrame frame = new PendingFrame(image, source, geometry, generation, acquiredAtMs);
            long delayMs = pendingFrames.offer(frame, acquiredAtMs, PendingFrame::close);
            if (delayMs >= 0) frameHandler.postDelayed(encodeFrameTask, delayMs);
        } catch (RuntimeException failure) {
            captureError = failure.getClass().getSimpleName();
            Log.w("GimiMobileUseService", "Frame acquisition failed; display retained", failure);
        }
    }

    private void encodePendingFrame() {
        PendingFrame pending;
        MobileFrameBuffer.Frame previous;
        int[] cachedPixels;
        synchronized (this) {
            pending = pendingFrames.take(SystemClock.elapsedRealtime());
            if (pending == null) return;
            previous = frames.latest();
            cachedPixels = previousPixels;
        }
        Image image = pending.image;
        MobileDisplayGeometry geometry = pending.geometry;
        long generation = pending.generation;
        try {
            if (reader != pending.source) return;
            // 像素采样与 JPEG 编码在服务锁外执行：输入注入、capture 和预览绑定不再等待全帧处理。
            // 使用实际取得图像的时间，不能把延后编码的旧帧伪装成动作之后的新帧。
            long now = pending.acquiredAtMs;
            Image.Plane plane = image.getPlanes()[0];
            ByteBuffer pixels = plane.getBuffer();
            int rowStride = plane.getRowStride();
            int pixelStride = plane.getPixelStride();
            int width = geometry.width;
            int height = geometry.height;
            int[] samples = new int[64 * 96];
            for (int sy = 0; sy < 96; sy++) {
                for (int sx = 0; sx < 64; sx++) {
                    int offset = (sy * height / 96) * rowStride + (sx * width / 64) * pixelStride;
                    samples[sy * 64 + sx] = ((pixels.get(offset) & 255) * 77
                            + (pixels.get(offset + 1) & 255) * 150
                            + (pixels.get(offset + 2) & 255) * 29) >> 8;
                }
            }
            int[] colors = new int[width * height];
            for (int y = 0; y < height; y++) {
                for (int x = 0; x < width; x++) {
                    int offset = y * rowStride + x * pixelStride;
                    int red = pixels.get(offset) & 255;
                    int green = pixels.get(offset + 1) & 255;
                    int blue = pixels.get(offset + 2) & 255;
                    colors[y * width + x] = 0xff000000 | red << 16 | green << 8 | blue;
                }
            }
            // 比较完整像素后才复用 JPEG，避免采样网格之外的小文字变化丢失。
            if (previous != null && Arrays.equals(cachedPixels, colors)) {
                synchronized (this) {
                    frames.publish(generation, width, height, now, previous.jpeg, samples);
                    captureError = null;
                }
                return;
            }
            Bitmap bitmap = Bitmap.createBitmap(colors, width, height, Bitmap.Config.ARGB_8888);
            try (ByteArrayOutputStream bytes = new ByteArrayOutputStream()) {
                // 保持原图尺寸，只降低编码质量，给 Binder 元数据和并发事务留出空间。
                for (int quality : new int[] {65, 45, 30, 15}) {
                    bytes.reset();
                    bitmap.compress(Bitmap.CompressFormat.JPEG, quality, bytes);
                    if (bytes.size() <= 750_000) break;
                }
                if (bytes.size() > 750_000) throw new IllegalStateException("Frame exceeds Binder payload budget");
                byte[] jpeg = bytes.toByteArray();
                synchronized (this) {
                    // 发布前以 generation 复核：旋转或换 Surface 之后开始的编码不会污染新缓存。
                    frames.publish(generation, width, height, now, jpeg, samples);
                    previousPixels = colors;
                    captureError = null;
                }
            } finally {
                bitmap.recycle();
            }
        } catch (RuntimeException | IOException failure) {
            synchronized (this) { captureError = failure.getClass().getSimpleName(); }
            Log.w("GimiMobileUseService", "Capture failed; display retained", failure);
        } finally {
            if (image != null) image.close();
        }
    }

    @Override public synchronized int[] geometry(int displayId) {
        requireDisplay(displayId);
        return new int[] { displayGeometry.width, displayGeometry.height, displayGeometry.densityDpi };
    }

    @Override public synchronized int[] rotateDisplay(int displayId) {
        requireDisplay(displayId);
        boolean landscape = displayGeometry.width <= displayGeometry.height;
        applyGeometry(displayGeometry.oriented(landscape));
        // 方向属于本次显示会话；主屏旋转、切换窗口和后续截图均不能覆盖用户选择。
        landscapeOverride = landscape;
        return new int[] { displayGeometry.width, displayGeometry.height, displayGeometry.densityDpi };
    }

    @Override public synchronized void stop() {
        if (clipboard != null) clipboard.restore();
        clipboard = null;
        if (display != null) {
            try { cancelGesture(display.getDisplay().getDisplayId()); }
            catch (RuntimeException failure) { Log.w("GimiMobileUseService","Gesture cancellation unavailable"); }
        }
        previewInteractive = false;
        previewBinding = null;
        clearPendingFrames();
        frames.reset();
        captureError = null;
        previousPixels = null;
        if (display != null) {
            if (windowManagerService != null && previousImePolicy != null) {
                try {
                    Class.forName("android.view.IWindowManager")
                            .getMethod("setDisplayImePolicy", int.class, int.class)
                            .invoke(windowManagerService, display.getDisplay().getDisplayId(), previousImePolicy);
                } catch (Exception failure) {
                    Log.w("GimiMobileUseService", "Unable to restore display IME policy", failure);
                }
            }
            virtualDisplay.close();
            virtualDisplay = null;
            display = null;
        }
        if (renderer != null) {
            try { renderer.close(); }
            catch (RuntimeException failure) { Log.w("GimiMobileUseService","Renderer cleanup failed",failure); }
            renderer = null;
        }
        windowManagerService = null;
        previousImePolicy = null;
        if (reader != null) {
            reader.close();
            reader = null;
        }
        if (displayThread != null) {
            displayThread.quitSafely();
            displayThread = null;
        }
        shellContext = null;
        displayGeometry = null;
        landscapeOverride = null;
    }

    /** Shizuku 用固定 transaction 调用 destroy；主动清理并终止非 daemon 服务进程。 */
    @Override public void destroy() {
        stop();
        System.exit(0);
    }

    private void requireShell() {
        if (Process.myUid() != 2000) throw new SecurityException("ADB shell mode required");
    }

    private void requireDisplay(int id) {
        requireShell();
        if (display == null || display.getDisplay().getDisplayId() != id || id == Display.DEFAULT_DISPLAY) {
            throw new IllegalStateException("Virtual display is unavailable");
        }
        ensureGeometry();
    }

    private void requirePoint(int x, int y) {
        if (!displayGeometry.contains(x, y)) {
            throw new IllegalArgumentException("Point outside virtual display");
        }
    }

    private MobileDisplayGeometry readGeometry() {
        DisplayManager manager = (DisplayManager) shellContext.getSystemService(Context.DISPLAY_SERVICE);
        Display main = manager.getDisplay(Display.DEFAULT_DISPLAY);
        if (main == null) throw new IllegalStateException("Primary display unavailable");
        Point size = new Point();
        DisplayMetrics metrics = new DisplayMetrics();
        main.getRealSize(size);
        main.getRealMetrics(metrics);
        MobileDisplayGeometry geometry = new MobileDisplayGeometry(size.x, size.y, metrics.densityDpi);
        return landscapeOverride == null ? geometry : geometry.oriented(landscapeOverride);
    }

    private ImageReader newReader(MobileDisplayGeometry geometry) {
        clearPendingFrames();
        long generation = frames.reset();
        captureError = null;
        previousPixels = null;
        // 一帧等待编码时，acquireLatestImage 仍需至少两个可获取槽位才能丢弃旧帧。
        ImageReader result = ImageReader.newInstance(geometry.width, geometry.height, PixelFormat.RGBA_8888, 3);
        frameHandler = new Handler(displayThread.getLooper());
        result.setOnImageAvailableListener(source -> queueFrame(source, geometry, generation), frameHandler);
        return result;
    }

    private void clearPendingFrames() {
        if (frameHandler != null) frameHandler.removeCallbacks(encodeFrameTask);
        pendingFrames.reset(PendingFrame::close);
    }

    /** 等待编码的图像及其 Surface/几何身份；替换、编码和停止三个路径均负责关闭图像。 */
    private static final class PendingFrame {
        final Image image;
        final ImageReader source;
        final MobileDisplayGeometry geometry;
        final long generation;
        final long acquiredAtMs;

        PendingFrame(Image image, ImageReader source, MobileDisplayGeometry geometry,
                     long generation, long acquiredAtMs) {
            this.image = image;
            this.source = source;
            this.geometry = geometry;
            this.generation = generation;
            this.acquiredAtMs = acquiredAtMs;
        }

        void close() { image.close(); }
    }

    private void ensureGeometry() {
        // 副屏建好后有自己的尺寸；用户主屏旋转不能重新配置后台应用。
        // 显式副屏旋转仍由 rotateDisplay/applyGeometry 完成。
        if (displayGeometry == null) throw new IllegalStateException("Display geometry unavailable");
    }

    private void applyGeometry(MobileDisplayGeometry current) {
        if (current.sameAs(displayGeometry)) return;
        // 分辨率切换由系统向目标应用派发配置变更；不重建显示，任务和预览绑定得以保留。
        cancelGesture(display.getDisplay().getDisplayId());
        // 先让系统接受尺寸；拒绝 resize 时不替换截图 Surface，旧画面仍可继续使用。
        display.resize(current.width, current.height, current.densityDpi);
        replaceReader(current);
        displayGeometry = current;
    }

    private void replaceReader(MobileDisplayGeometry geometry) {
        ImageReader replacement = newReader(geometry);
        ImageReader previous = reader;
        renderer.resize(replacement.getSurface(), geometry.width, geometry.height);
        reader = replacement;
        if (previous != null) previous.close();
    }

    private void command(String... args) {
        // 等待 shell 子进程期间不持显示锁，否则滑动会阻塞 ImageReader 消费并卡住预览。
        try {
            github.ponyhuang.gimi.domain.mobileuse.MobileShellResult result =
                    new MobileShellCommandExecutor().executeArguments(args);
            if (result.getTimedOut() || result.getExitCode() == null || result.getExitCode() != 0 ||
                    result.getOutput().contains("Error:")) {
                throw new IllegalStateException("Shell command failed or timed out; inspect before retrying");
            }
        } catch (IOException | InterruptedException failure) {
            if (failure instanceof InterruptedException) Thread.currentThread().interrupt();
            throw new IllegalStateException("Shell command unavailable", failure);
        }
    }
}
