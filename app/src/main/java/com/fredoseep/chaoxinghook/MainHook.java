package com.fredoseep.chaoxinghook;

import com.fredoseep.chaoxinghook.xposed.HostContext;
import android.util.Log;


import com.fredoseep.chaoxinghook.function.AdvertisementHook;
import com.fredoseep.chaoxinghook.function.ChatHook;
import com.fredoseep.chaoxinghook.function.ClipboardPopKiller;
import com.fredoseep.chaoxinghook.function.ExamImageHook;
import com.fredoseep.chaoxinghook.function.ExamRiskHook;
import com.fredoseep.chaoxinghook.function.HomeFeedHook;
import com.fredoseep.chaoxinghook.function.MsgReadHook;
import com.fredoseep.chaoxinghook.function.QRTesting;
import com.fredoseep.chaoxinghook.function.SettingsEntryHook;
import com.fredoseep.chaoxinghook.function.WebViewHooks;

import com.fredoseep.chaoxinghook.xposed.ModernHookCallback;
import com.fredoseep.chaoxinghook.xposed.HookRuntime;
import org.luckypray.dexkit.DexKitBridge;

/** Xposed 入口：仅负责目标包过滤、初始化时机和功能安装。 */
public final class MainHook {
    private static final String TAG_INIT = "CX.Init";
    private static final String TAG_WEBVIEW = "CX.WebView";
    private static final String TAG_EXAM_RISK = "CX.ExamRisk";
    private static final String TAG_EXAM_IMAGE = "CX.ExamImage";
    private static final String TAG_SETTINGS = "CX.Settings";
    private static final String TAG_CLIPBOARD = "CX.Clipboard";
    private static final java.util.concurrent.atomic.AtomicBoolean applicationHooksStarted = new java.util.concurrent.atomic.AtomicBoolean(false);
    private final WebViewHooks webViewHooks = new WebViewHooks();
    private final SettingsEntryHook settingsEntryHook = new SettingsEntryHook();

    public void install(ClassLoader classLoader) {
        Log.d(TAG_INIT, "=== onPackageReady enter: " + System.currentTimeMillis());
        try {
            System.loadLibrary("dexkit");
            Log.i(TAG_INIT, "loadLibrary dexkit OK");
        } catch (Throwable t) {
            Log.e(TAG_INIT, "loadLibrary dexkit FAILED: ", t);
        }

        try{
            MsgReadHook.hook(classLoader);
        } catch (Exception e) {
            Log.e("MsgReadHook","Error at MsgReadHook "+e.getMessage());
        }

        try {
            ExamRiskHook.installProcessExitHook();
            Log.i(TAG_EXAM_RISK, "installProcessExitHook OK");
        } catch (Throwable t) {
            Log.e(TAG_EXAM_RISK, "installProcessExitHook FAILED: ", t);
        }

        try {
            installApplicationReadyHook();
        } catch (Throwable t) {
            Log.e(TAG_INIT, "Application.attach observer FAILED: ", t);
        }
        try {
            webViewHooks.install(classLoader);
            Log.i(TAG_WEBVIEW, "installWebViewHooks OK");
        } catch (Throwable t) {
            Log.e(TAG_WEBVIEW, "installWebViewHooks FAILED: ", t);
        }
        try {
            ExamImageHook.installFileReplaceHook(classLoader);
            Log.i(TAG_EXAM_IMAGE, "installFileReplaceHook OK");
        } catch (Throwable t) {
            Log.e(TAG_EXAM_IMAGE, "installFileReplaceHook FAILED: ", t);
        }
        try {
            ExamImageHook.installExamSnapshotHook(classLoader);
            Log.i(TAG_EXAM_IMAGE, "installExamSnapshotHook OK");
        } catch (Throwable t) {
            Log.e(TAG_EXAM_IMAGE, "installExamSnapshotHook FAILED: ", t);
        }
        QRTesting.hook(classLoader);
        Log.d(TAG_INIT, "=== onPackageReady done; business hooks deferred");
    }

    private void installApplicationReadyHook() {
        HookRuntime.findAndHookMethod(android.app.Application.class, "attach",
                android.content.Context.class, new ModernHookCallback() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) {
                        if (param.hasThrowable()) return;
                        if (param.args.length > 0 && param.args[0] instanceof android.content.Context) {
                            HostContext.attach((android.app.Application) param.thisObject);
                            initializeApplicationHooks((android.content.Context) param.args[0]);
                        }
                    }
                });
        Log.i(TAG_INIT, "Application.attach observer registered");
    }

    private void initializeApplicationHooks(android.content.Context context) {
        if (!"com.chaoxing.mobile".equals(context.getPackageName())) return;
        ClassLoader loader = context.getClassLoader();
        if (loader == null) {
            Log.e(TAG_INIT, "Application.attach ready: ClassLoader unavailable");
            return;
        }
        if (!applicationHooksStarted.compareAndSet(false, true)) return;
        Log.i(TAG_INIT, "[INIT-FIX-1] Application.attach ready: context=" + context.getClass().getName()
                + " loader=" + loader);
        try (DexKitBridge bridge = DexKitBridge.create(loader, true)) {
            Log.i(TAG_INIT, "DexKitBridge.create OK (application loader)");
            AdvertisementHook.install(bridge, loader);
            HomeFeedHook.install(bridge, loader);
            ChatHook.install(bridge, loader);
            Log.i(TAG_INIT, "installCoreHooks completed; inspect individual Hook logs");
        } catch (Throwable t) {
            Log.e(TAG_INIT, "application DexKit/core initialization FAILED: ", t);
        }
        try {
            settingsEntryHook.install(loader);
            Log.i(TAG_SETTINGS, "设置入口观察注册完成（不代表已匹配设置行）");
        } catch (Throwable t) {
            Log.e(TAG_SETTINGS, "installLongPressModuleEntry FAILED: ", t);
        }
        try {
            ClipboardPopKiller.hook(loader);
        } catch (Throwable t) {
            Log.e(TAG_CLIPBOARD, "ClipboardPopKiller FAILED: ", t);
        }
    }
}
