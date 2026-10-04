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
import android.os.IBinder;
import android.os.Process;
import android.os.Bundle;
import android.os.SystemClock;
import android.util.Log;
import android.util.DisplayMetrics;
import android.view.Display;
import android.view.Surface;
import android.view.MotionEvent;
import android.view.InputEvent;
import android.view.InputDevice;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.lang.reflect.Method;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;

/** Shizuku UserService。仅在 shell UID 下管理一个进程内副屏，销毁时禁止迁移目标任务到主屏。 */
public final class ShellMobileUseService extends IMobileUseService.Stub {
    // PUBLIC | OWN_CONTENT_ONLY | DESTROY_CONTENT_ON_REMOVAL | TRUSTED。
    // DESTROY_CONTENT_ON_REMOVAL 是核心隔离不变量，释放显示时禁止任务迁移到主屏。
    private static final int DISPLAY_FLAGS = 1 | 8 | (1 << 8) | (1 << 10);
    private HandlerThread displayThread;
    private ImageReader reader;
    private VirtualDisplay display;
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
    private long gestureId = -1;
    private float touchX, touchY;
    private Object inputManager;
    private Method injectInput;

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
            displayGeometry = readGeometry();
            reader = newReader(displayGeometry);
            renderer = new MobileDisplayRenderer(reader.getSurface(), displayGeometry.width, displayGeometry.height);
            DisplayManager manager = (DisplayManager) shellContext.getSystemService(Context.DISPLAY_SERVICE);
            display = manager.createVirtualDisplay(
                    "GimiMobileUse", displayGeometry.width, displayGeometry.height,
                    displayGeometry.densityDpi, renderer.inputSurface(),
                    DISPLAY_FLAGS, null, new Handler(displayThread.getLooper()));
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
            return displayId;
        } catch (Exception failure) {
            Log.e("GimiMobileUseService", "createDisplay failed", failure);
            stop();
            throw new IllegalStateException("Virtual display unsupported", failure);
        }
    }

    @Override public void launch(String component, int displayId) {
        synchronized (this) { requireDisplay(displayId); }
        if (component == null || !component.matches("[A-Za-z0-9_.$]+/[A-Za-z0-9_.$]+")) {
            throw new IllegalArgumentException("Invalid launcher component");
        }
        command("cmd", "activity", "start-activity", "--display", Integer.toString(displayId),
                "-n", component);
    }

    @Override public void tap(int displayId, int x, int y) {
        synchronized (this) { requireDisplay(displayId); requirePoint(x, y); }
        command("input", "-d", Integer.toString(displayId), "tap",
                Integer.toString(x), Integer.toString(y));
    }

    @Override public void swipe(int displayId, int x1, int y1, int x2, int y2, int durationMs) {
        synchronized (this) { requireDisplay(displayId); requirePoint(x1, y1); requirePoint(x2, y2); }
        if (durationMs < 100 || durationMs > 5000) throw new IllegalArgumentException("Invalid duration");
        command("input", "-d", Integer.toString(displayId), "swipe",
                Integer.toString(x1), Integer.toString(y1), Integer.toString(x2), Integer.toString(y2),
                Integer.toString(durationMs));
    }

    @Override public void back(int displayId) {
        synchronized (this) { requireDisplay(displayId); }
        command("input", "-d", Integer.toString(displayId), "keyevent", "4");
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

    @Override public synchronized void setPreview(int displayId, String bindingId, Surface surface, int width, int height) {
        requireDisplay(displayId);
        if (surface == null || !surface.isValid() || width <= 0 || height <= 0 || bindingId == null) {
            throw new IllegalArgumentException("Invalid preview output");
        }
        cancelGesture(displayId);
        renderer.preview(surface,width,height);
        previewBinding = bindingId;
        setImePolicy(displayId, 1); // FALLBACK_DISPLAY：原生输入连接不变，键盘显示在用户主屏。
    }

    @Override public synchronized void clearPreview(int displayId, String bindingId) {
        requireDisplay(displayId);
        // 旧窗口的销毁回调不能解绑刚切换的新窗口。
        if (previewBinding == null || !previewBinding.equals(bindingId)) return;
        cancelGesture(displayId);
        renderer.preview(null,0,0);
        previewBinding = null;
        setImePolicy(displayId, 2);
        hidePreviewKeyboard();
    }

    private void hidePreviewKeyboard() {
        try {
            // 切换为 HIDE 只约束后续请求，不会关闭已经回退到主屏的键盘。
            // shell 拥有 TEST_INPUT_METHOD 权限；此系统入口仅隐藏 IME，不发送目标 App 返回。
            IBinder binder = (IBinder) Class.forName("android.os.ServiceManager")
                    .getMethod("getService", String.class).invoke(null, "input_method");
            Object inputMethod = Class.forName("com.android.internal.view.IInputMethodManager$Stub")
                    .getMethod("asInterface", IBinder.class).invoke(null, binder);
            Class.forName("com.android.internal.view.IInputMethodManager")
                    .getMethod("hideSoftInputFromServerForTest").invoke(inputMethod);
        } catch (ReflectiveOperationException | RuntimeException failure) {
            Log.w("GimiMobileUseService", "Unable to hide preview keyboard", failure);
        }
    }

    private void setImePolicy(int displayId, int policy) {
        try {
            Class.forName("android.view.IWindowManager").getMethod("setDisplayImePolicy",int.class,int.class)
                    .invoke(windowManagerService,displayId,policy);
        } catch (Exception failure) { throw new IllegalStateException("Native keyboard unavailable",failure); }
    }

    @Override public synchronized boolean touch(int displayId, int action, float x, float y, long id, long eventTimeMs) {
        requireDisplay(displayId);
        if (previewBinding == null) return false;
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
            InputEvent.class.getMethod("setDisplayId",int.class).invoke(event,displayId);
            if (inputManager == null) {
                Class<?> manager = Class.forName("android.hardware.input.InputManagerGlobal");
                inputManager = manager.getMethod("getInstance").invoke(null);
                injectInput = manager.getMethod("injectInputEvent",InputEvent.class,int.class);
            }
            return (Boolean)injectInput.invoke(inputManager,event,0); // 异步投递，不等待目标 App 完成动作。
        } catch (Exception failure) { throw new IllegalStateException("Touch injection unavailable",failure); }
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

    private synchronized void encodePendingFrame() {
        PendingFrame pending = pendingFrames.take(SystemClock.elapsedRealtime());
        if (pending == null) return;
        Image image = pending.image;
        MobileDisplayGeometry geometry = pending.geometry;
        long generation = pending.generation;
        try {
            if (reader != pending.source) return;
            // 使用实际取得图像的时间，不能把延后编码的旧帧伪装成动作之后的新帧。
            long now = pending.acquiredAtMs;
            MobileFrameBuffer.Frame previous = frames.latest();
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
            if (previous != null && Arrays.equals(previousPixels, colors)) {
                frames.publish(generation, width, height, now, previous.jpeg, samples);
                captureError = null;
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
                frames.publish(generation, width, height, now, bytes.toByteArray(), samples);
                previousPixels = colors;
                captureError = null;
            } finally {
                bitmap.recycle();
            }
        } catch (RuntimeException | IOException failure) {
            captureError = failure.getClass().getSimpleName();
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
        if (display != null) {
            try { cancelGesture(display.getDisplay().getDisplayId()); }
            catch (RuntimeException failure) { Log.w("GimiMobileUseService","Gesture cancellation unavailable"); }
        }
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
            display.release();
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
        MobileDisplayGeometry current = readGeometry();
        applyGeometry(current);
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
            java.lang.Process child = new ProcessBuilder(args).redirectErrorStream(true).start();
            boolean finished = child.waitFor(10, TimeUnit.SECONDS);
            if (!finished) {
                child.destroyForcibly();
                throw new IllegalStateException("Shell command timed out");
            }
            if (child.exitValue() != 0) {
                throw new IllegalStateException("Shell command failed: " +
                        new String(child.getInputStream().readAllBytes()));
            }
        } catch (IOException | InterruptedException failure) {
            if (failure instanceof InterruptedException) Thread.currentThread().interrupt();
            throw new IllegalStateException("Shell command unavailable", failure);
        }
    }
}
