package com.fredoseep.chaoxinghook.function;

import android.util.Log;

import com.fredoseep.chaoxinghook.xposed.ModernHookCallback;
import com.fredoseep.chaoxinghook.xposed.HookRuntime;
import static com.fredoseep.chaoxinghook.function.HookConfigRepository.getSignConfig;

/** 复制限制 CSS 拦截及页面可选择、可复制脚本。 */
final class CopyRestrictionHook {
    private static final String TAG_COPY = "CX.Copy";
    static boolean intercept(String url, Object requestObj, ModernHookCallback.MethodHookParam innerParam, ClassLoader loader) throws Throwable {
        if (getSignConfig().enableCopyRestriction && url.contains("notAllowCopy.css")) {
            try {
                Class<?> responseClass = HookRuntime.findClassIfExists("android.webkit.WebResourceResponse", loader);
                if (responseClass != null) {
                    String emptyCss = "";
                    innerParam.setResult(HookRuntime.newInstance(
                            responseClass,
                            "text/css",
                            "utf-8",
                            new java.io.ByteArrayInputStream(emptyCss.getBytes("UTF-8"))
                    ));
                    Log.i(TAG_COPY, "Chaoxing AdSkip: 成功拦截 notAllowCopy.css，复制限制已解除！");
                }
            } catch (Exception e) {}
            return true;
        }
        return false;
    }

    private static String buildCopyEnablerJs() {
        return "(function(){try{"
                + "if(window.__cxCopyOn)return;window.__cxCopyOn=1;"
                + "var s=document.createElement('style');"
                + "s.textContent='*,*::before,*::after{-webkit-user-select:auto!important;user-select:text!important;-webkit-touch-callout:default!important;}';"
                + "(document.head||document.documentElement).appendChild(s);"
                + "['onselectstart','oncopy','oncut','oncontextmenu','ondragstart'].forEach(function(k){"
                + "try{document[k]=null;var b=document.body;if(b)b[k]=null;}catch(e){}});"
                + "['selectstart','copy','cut','contextmenu','dragstart'].forEach(function(t){"
                + "window.addEventListener(t,function(e){e.stopPropagation();},true);"
                + "document.addEventListener(t,function(e){e.stopPropagation();},true);});"
                + "}catch(e){}})();";
    }

    static void onPageFinished(android.webkit.WebView view) {
        if (getSignConfig().enableCopyRestriction) view.evaluateJavascript(buildCopyEnablerJs(), null);
    }
}
