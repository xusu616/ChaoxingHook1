package com.fredoseep.chaoxinghook.xposed

import com.fredoseep.chaoxinghook.MainHook
import io.github.libxposed.api.XposedModule
import io.github.libxposed.api.XposedModuleInterface.ModuleLoadedParam
import io.github.libxposed.api.XposedModuleInterface.PackageReadyParam

/** API 102 initializes the framework before the final host class loader is available. */
class ModuleEntry : XposedModule() {
    override fun onModuleLoaded(param: ModuleLoadedParam) {
        HookRuntime.initialize(this)
    }

    override fun onPackageReady(param: PackageReadyParam) {
        if (param.packageName == "com.chaoxing.mobile") {
            MainHook().install(param.classLoader)
        }
    }
}
