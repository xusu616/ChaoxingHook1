package com.fredoseep.chaoxinghook.function;

import android.util.Log;
import java.util.HashSet;
import java.util.Set;
import com.fredoseep.chaoxinghook.xposed.ModernHookCallback;
import com.fredoseep.chaoxinghook.xposed.HookRuntime;

/** WebView Hook 注册和事件分发；具体业务由各功能类实现。 */
public final class WebViewHooks {
    private static final String TAG_WEBVIEW = "CX.WebView";
    private final Set<String> hookedWebViewClients = new HashSet<>();
    private final PostSignHook postSign = new PostSignHook();

    public void install(ClassLoader classLoader) {
        try {
            Class<?> webViewClass = HookRuntime.findClass("android.webkit.WebView", classLoader);
            HookRuntime.hookAllMethods(webViewClass, "setWebViewClient", new ModernHookCallback() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                    Object webViewObj = param.thisObject;

                    // ==========================================
                    // 【精确狙击】：强行接管 WebView 长按事件，注入极客专属下载菜单！
                    // ==========================================
                    if (webViewObj instanceof android.webkit.WebView) {
                        android.webkit.WebView view = (android.webkit.WebView) webViewObj;
                        postSign.attach(view);
                        ImageSaveHook.attach(view);
                    }
                    Object webViewClient = param.args[0];
                    if (webViewClient == null) return;
                    Class<?> clientClass = webViewClient.getClass();
                    if (!hookedWebViewClients.add(clientClass.getName())) return;

                    Class<?> targetClass = clientClass;
                    while (targetClass != null && !targetClass.getName().equals("java.lang.Object")) {
                        HookRuntime.hookAllMethods(targetClass, "shouldInterceptRequest", new ModernHookCallback() {
                            @Override
                            protected void beforeHookedMethod(MethodHookParam innerParam) throws Throwable {
                                String url = null;
                                Object requestObj = null;

                                for (Object arg : innerParam.args) {
                                    if (arg instanceof String) { url = (String) arg; }
                                    else if (arg instanceof android.webkit.WebResourceRequest) {
                                        requestObj = arg;
                                        Object uriObj = HookRuntime.callMethod(arg, "getUrl");
                                        if (uriObj != null) url = uriObj.toString();
                                    }
                                }
                                if (url == null) return;

                                ClassLoader loader = classLoader;
                                if (ExamRiskHook.intercept(url, requestObj, innerParam, loader)) return;
                                if (VideoUnlockHook.intercept(url, requestObj, innerParam, loader)) return;
                                if (CopyRestrictionHook.intercept(url, requestObj, innerParam, loader)) return;
                                LocationSignHook.intercept(url, requestObj, innerParam, loader);
                            }
                        });
                        // ===== 页面加载完成注入：手势/位置签到自动完成 =====
                        HookRuntime.hookAllMethods(targetClass, "onPageFinished", new ModernHookCallback() {
                            @Override
                            protected void afterHookedMethod(MethodHookParam innerParam) throws Throwable {
                                try {
                                    if (innerParam.args.length < 2 || !(innerParam.args[0] instanceof android.webkit.WebView)) return;
                                    android.webkit.WebView wv = (android.webkit.WebView) innerParam.args[0];
                                    postSign.inject(wv);
                                    CopyRestrictionHook.onPageFinished(wv);
                                    AutoSignHook.onPageFinished(wv);
                                } catch (Throwable ignored) {}
                            }
                        });
                        targetClass = targetClass.getSuperclass();
                    }
                }
            });

            ModernHookCallback jsInterceptHook = new ModernHookCallback() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                    if (param.args.length > 0 && param.args[0] instanceof String) {
                        String jsCode = (String) param.args[0];

                        jsCode = ExamRiskHook.transformJavascript(jsCode);
                        param.args[0] = DeviceFlagHook.transformJavascript(jsCode);
                    }
                }
            };
            HookRuntime.hookAllMethods(webViewClass, "evaluateJavascript", jsInterceptHook);
            HookRuntime.hookAllMethods(webViewClass, "loadUrl", jsInterceptHook);

        } catch (Throwable t) {
            Log.e(TAG_WEBVIEW, "Chaoxing AdSkip Error (WebView Hook): ", t);
        }
    }
}
