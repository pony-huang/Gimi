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
import android.util.Log;
import android.util.DisplayMetrics;
import android.view.Display;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.lang.reflect.Method;
import java.nio.ByteBuffer;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;

/** Shizuku UserService。仅在 ADB shell UID 下管理一个副屏，不保留任何跨任务画面。 */
public final class ShellMobileUseService extends IMobileUseService.Stub {
    // PUBLIC | OWN_CONTENT_ONLY | DESTROY_CONTENT_ON_REMOVAL | TRUSTED。
    // DESTROY_CONTENT_ON_REMOVAL 是核心隔离不变量，释放显示时禁止任务迁移到主屏。
    private static final int DISPLAY_FLAGS = 1 | 8 | (1 << 8) | (1 << 10);
    private HandlerThread displayThread;
    private ImageReader reader;
    private VirtualDisplay display;
    private Context shellContext;
    private MobileDisplayGeometry displayGeometry;
    private Object windowManagerService;
    private Integer previousImePolicy;

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
            DisplayManager manager = (DisplayManager) shellContext.getSystemService(Context.DISPLAY_SERVICE);
            display = manager.createVirtualDisplay(
                    "GimiMobileUse", displayGeometry.width, displayGeometry.height,
                    displayGeometry.densityDpi, reader.getSurface(),
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

    @Override public synchronized void launch(String component, int displayId) {
        requireDisplay(displayId);
        if (component == null || !component.matches("[A-Za-z0-9_.$]+/[A-Za-z0-9_.$]+")) {
            throw new IllegalArgumentException("Invalid launcher component");
        }
        command("cmd", "activity", "start-activity", "--display", Integer.toString(displayId),
                "-n", component);
    }

    @Override public synchronized void tap(int displayId, int x, int y) {
        requireDisplay(displayId);
        requirePoint(x, y);
        command("input", "-d", Integer.toString(displayId), "tap",
                Integer.toString(x), Integer.toString(y));
    }

    @Override public synchronized void swipe(int displayId, int x1, int y1, int x2, int y2, int durationMs) {
        requireDisplay(displayId);
        requirePoint(x1, y1);
        requirePoint(x2, y2);
        if (durationMs < 100 || durationMs > 5000) throw new IllegalArgumentException("Invalid duration");
        command("input", "-d", Integer.toString(displayId), "swipe",
                Integer.toString(x1), Integer.toString(y1), Integer.toString(x2), Integer.toString(y2),
                Integer.toString(durationMs));
    }

    @Override public synchronized void back(int displayId) {
        requireDisplay(displayId);
        command("input", "-d", Integer.toString(displayId), "keyevent", "4");
    }

    @Override public synchronized byte[] capture() {
        requireShell();
        if (display == null || reader == null) throw new IllegalStateException("Display stopped");
        ensureGeometry();
        Image image = null;
        long deadline = System.currentTimeMillis() + 2500;
        while (image == null && System.currentTimeMillis() < deadline) {
            image = reader.acquireLatestImage();
            if (image == null) {
                try { Thread.sleep(80); } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException("Capture interrupted", interrupted);
                }
            }
        }
        if (image == null) return null;
        try {
            Image.Plane plane = image.getPlanes()[0];
            ByteBuffer pixels = plane.getBuffer();
            int rowStride = plane.getRowStride();
            int pixelStride = plane.getPixelStride();
            int width = displayGeometry.width;
            int height = displayGeometry.height;
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
            Bitmap bitmap = Bitmap.createBitmap(colors, width, height, Bitmap.Config.ARGB_8888);
            try (ByteArrayOutputStream bytes = new ByteArrayOutputStream()) {
                bitmap.compress(Bitmap.CompressFormat.JPEG, 65, bytes);
                return bytes.toByteArray();
            } catch (IOException impossible) {
                throw new IllegalStateException(impossible);
            } finally {
                bitmap.recycle();
            }
        } finally {
            image.close();
        }
    }

    @Override public synchronized int[] geometry(int displayId) {
        requireDisplay(displayId);
        return new int[] { displayGeometry.width, displayGeometry.height, displayGeometry.densityDpi };
    }

    @Override public synchronized void recoverSurface(int displayId) {
        requireDisplay(displayId);
        replaceReader(displayGeometry);
    }

    @Override public synchronized void stop() {
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
        return new MobileDisplayGeometry(size.x, size.y, metrics.densityDpi);
    }

    private ImageReader newReader(MobileDisplayGeometry geometry) {
        return ImageReader.newInstance(geometry.width, geometry.height, PixelFormat.RGBA_8888, 2);
    }

    private void ensureGeometry() {
        MobileDisplayGeometry current = readGeometry();
        if (current.sameAs(displayGeometry)) return;
        display.resize(current.width, current.height, current.densityDpi);
        replaceReader(current);
        displayGeometry = current;
    }

    private void replaceReader(MobileDisplayGeometry geometry) {
        ImageReader replacement = newReader(geometry);
        ImageReader previous = reader;
        display.setSurface(replacement.getSurface());
        reader = replacement;
        if (previous != null) previous.close();
    }

    private void command(String... args) {
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
