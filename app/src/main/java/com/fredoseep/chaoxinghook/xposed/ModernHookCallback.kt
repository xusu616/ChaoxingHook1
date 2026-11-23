package com.fredoseep.chaoxinghook.xposed

import io.github.libxposed.api.XposedInterface
import java.lang.reflect.Executable

/** 将现有 Java 前置／后置回调接到 API 102 拦截链，参数和结果只属于本次调用。 */
abstract class ModernHookCallback {
    @Throws(Throwable::class)
    protected open fun beforeHookedMethod(param: MethodHookParam) = Unit

    @Throws(Throwable::class)
    protected open fun afterHookedMethod(param: MethodHookParam) = Unit

    internal fun intercept(chain: XposedInterface.Chain): Any? {
        val param = MethodHookParam(chain.executable, chain.thisObject, chain.args.toTypedArray())
        beforeHookedMethod(param)
        if (!param.returnEarly) {
            try {
                param.recordResult(chain.proceed(param.args))
            } catch (throwable: Throwable) {
                // 保存原调用的异常，供后置回调检查；未被回调处理时原样抛回调用方。
                param.recordThrowable(throwable)
            }
        }
        afterHookedMethod(param)
        param.getThrowable()?.let { throw it }
        return param.getResult()
    }

    class MethodHookParam internal constructor(
        @JvmField val method: Executable,
        @JvmField val thisObject: Any?,
        @JvmField var args: Array<Any?>,
    ) {
        private var result: Any? = null
        private var throwable: Throwable? = null
        internal var returnEarly = false
            private set

        fun getResult(): Any? = result

        fun setResult(value: Any?) {
            result = value
            throwable = null
            returnEarly = true
        }

        fun hasThrowable(): Boolean = throwable != null

        fun getThrowable(): Throwable? = throwable

        fun setThrowable(value: Throwable?) {
            throwable = value
            result = null
            returnEarly = true
        }

        internal fun recordResult(value: Any?) {
            result = value
            throwable = null
        }

        internal fun recordThrowable(value: Throwable) {
            throwable = value
            result = null
        }
    }

    companion object {
        @JvmField
        val DO_NOTHING: ModernHookCallback = object : ModernHookCallback() {
            override fun beforeHookedMethod(param: MethodHookParam) {
                param.setResult(null)
            }
        }
    }
}
