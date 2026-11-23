package com.fredoseep.chaoxinghook.function;

import android.util.Log;

import com.fredoseep.chaoxinghook.xposed.ModernHookCallback;
import com.fredoseep.chaoxinghook.xposed.HookRuntime;

public class ClipboardPopKiller {
    private static final String TAG = "CX.Clipboard";

    public static void hook(ClassLoader loader) {
        HookRuntime.findAndHookMethod("com.chaoxing.mobile.main.clipboard.ClipboardChangeMonitor", loader, "m", "android.app.Activity", ModernHookCallback.DO_NOTHING);
        Log.i(TAG, "剪贴板弹窗拦截已安装");
    }
}