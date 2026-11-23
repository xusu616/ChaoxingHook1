package com.fredoseep.chaoxinghook.function;

import android.util.Log;

import com.fredoseep.chaoxinghook.xposed.ModernHookCallback;
import com.fredoseep.chaoxinghook.xposed.HookRuntime;

public class MsgReadHook {
    private static final String TAG = "MsgReadHook";

    public static void hook(ClassLoader classLoader){
        try {
            Log.d(TAG,"injected");
            HookRuntime.findAndHookMethod("com.hyphenate.chat.EMChatManager", classLoader, "ackMessageRead", "java.lang.String", "java.lang.String", ModernHookCallback.DO_NOTHING);
        } catch (Exception e) {
            Log.e(TAG,"Error 1 "+e.getMessage());
        }
    }
}
