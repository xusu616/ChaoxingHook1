package com.fredoseep.chaoxinghook.xposed

import io.github.libxposed.api.XposedInterface
import io.github.libxposed.api.XposedModule
import java.lang.reflect.Constructor
import java.lang.reflect.Executable
import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Method
import java.lang.reflect.Modifier

/** API 102 注册入口和现有 Java 功能实际使用的反射操作。 */
object HookRuntime {
    private lateinit var module: XposedModule

    @JvmStatic
    fun initialize(value: XposedModule) {
        check(!::module.isInitialized || module === value) { "HookRuntime already belongs to another module" }
        module = value
    }

    @JvmStatic
    fun hookMethod(method: Executable, callback: ModernHookCallback): XposedInterface.HookHandle {
        return module.hook(method).intercept { chain -> callback.intercept(chain) }
    }

    @JvmStatic
    fun hookAllMethods(
        clazz: Class<*>,
        name: String,
        callback: ModernHookCallback,
    ): Set<XposedInterface.HookHandle> {
        val handles = linkedSetOf<XposedInterface.HookHandle>()
        for (method in clazz.declaredMethods) {
            if (method.name == name) handles.add(hookMethod(method, callback))
        }
        return handles
    }

    @JvmStatic
    fun hookAllConstructors(
        clazz: Class<*>,
        callback: ModernHookCallback,
    ): Set<XposedInterface.HookHandle> {
        val handles = linkedSetOf<XposedInterface.HookHandle>()
        for (constructor in clazz.declaredConstructors) {
            handles.add(hookMethod(constructor, callback))
        }
        return handles
    }

    @JvmStatic
    fun findClass(name: String, loader: ClassLoader?): Class<*> = Class.forName(name, false, loader)

    @JvmStatic
    fun findClassIfExists(name: String, loader: ClassLoader?): Class<*>? {
        return try {
            findClass(name, loader)
        } catch (_: ClassNotFoundException) {
            null
        }
    }

    @JvmStatic
    fun findAndHookMethod(
        name: String,
        loader: ClassLoader?,
        methodName: String,
        vararg parameters: Any,
    ): XposedInterface.HookHandle {
        return findAndHookMethod(findClass(name, loader), methodName, *parameters)
    }

    @JvmStatic
    fun findAndHookMethod(
        clazz: Class<*>,
        name: String,
        vararg parameters: Any,
    ): XposedInterface.HookHandle {
        require(parameters.isNotEmpty() && parameters.last() is ModernHookCallback) {
            "The last parameter must be a ModernHookCallback"
        }
        val parameterTypes = parameters.dropLast(1).map { type ->
            when (type) {
                is Class<*> -> type
                is String -> primitiveType(type) ?: findClass(type, clazz.classLoader)
                else -> throw IllegalArgumentException("Unsupported parameter type: $type")
            }
        }.toTypedArray()
        var current: Class<*>? = clazz
        while (current != null) {
            val method = try {
                current.getDeclaredMethod(name, *parameterTypes)
            } catch (_: NoSuchMethodException) {
                current = current.superclass
                continue
            }
            return hookMethod(method, parameters.last() as ModernHookCallback)
        }
        throw NoSuchMethodException("${clazz.name}.$name(${parameterTypes.joinToString { it.name }})")
    }

    @JvmStatic
    fun getObjectField(instance: Any, name: String): Any? {
        var current: Class<*>? = instance.javaClass
        while (current != null) {
            val field = try {
                current.getDeclaredField(name)
            } catch (_: NoSuchFieldException) {
                current = current.superclass
                continue
            }
            field.isAccessible = true
            return field.get(instance)
        }
        throw NoSuchFieldException("${instance.javaClass.name}.$name")
    }

    @JvmStatic
    @Throws(Throwable::class)
    fun callMethod(instance: Any, name: String, vararg args: Any?): Any? {
        val methods = linkedMapOf<List<Class<*>>, Method>()
        var current: Class<*>? = instance.javaClass
        while (current != null) {
            for (method in current.declaredMethods) {
                if (method.name == name && !method.isBridge && !method.isSynthetic &&
                    !Modifier.isStatic(method.modifiers)
                ) {
                    methods.putIfAbsent(method.parameterTypes.toList(), method)
                }
            }
            current = current.superclass
        }
        // 包含接口继承的默认方法；类自身／父类的实现优先。
        for (method in instance.javaClass.methods) {
            if (method.name == name && !method.isBridge && !method.isSynthetic &&
                !Modifier.isStatic(method.modifiers)
            ) {
                methods.putIfAbsent(method.parameterTypes.toList(), method)
            }
        }
        val method = selectExecutable(methods.values.toList(), args, "${instance.javaClass.name}.$name")
        method.isAccessible = true
        return try {
            method.invoke(instance, *args)
        } catch (exception: InvocationTargetException) {
            throw exception.targetException
        }
    }

    @JvmStatic
    @Throws(Throwable::class)
    fun newInstance(clazz: Class<*>, vararg args: Any?): Any {
        val constructor: Constructor<*> = selectExecutable(clazz.declaredConstructors.toList(), args, clazz.name)
        constructor.isAccessible = true
        return try {
            constructor.newInstance(*args)
        } catch (exception: InvocationTargetException) {
            throw exception.targetException
        }
    }

    private fun <T : Executable> selectExecutable(
        executables: List<T>,
        args: Array<out Any?>,
        description: String,
    ): T {
        val candidates = executables.filter { executable ->
            val types = executable.parameterTypes
            types.size == args.size && types.indices.all { index ->
                val value = args[index]
                if (value == null) !types[index].isPrimitive
                else boxedType(types[index]).isAssignableFrom(value.javaClass)
            }
        }
        if (candidates.isEmpty()) {
            throw NoSuchMethodException("No compatible signature for $description")
        }
        val matches = candidates.filter { candidate ->
            candidates.none { other -> other !== candidate && moreSpecific(other, candidate) }
        }
        if (matches.size != 1) {
            throw IllegalArgumentException("Ambiguous signatures for $description: ${matches.joinToString()}")
        }
        return matches.single()
    }

    private fun moreSpecific(candidate: Executable, other: Executable): Boolean {
        val candidateTypes = candidate.parameterTypes
        val otherTypes = other.parameterTypes
        var strictlyMoreSpecific = false
        for (index in candidateTypes.indices) {
            val candidateType = candidateTypes[index]
            val otherType = otherTypes[index]
            if (candidateType == otherType) continue
            val boxedCandidate = boxedType(candidateType)
            val boxedOther = boxedType(otherType)
            if (!boxedOther.isAssignableFrom(boxedCandidate)) return false
            // 对同一包装类型，运行时实参精确匹配的引用参数优先于拆箱参数。
            if (boxedCandidate == boxedOther && candidateType.isPrimitive) return false
            strictlyMoreSpecific = true
        }
        return strictlyMoreSpecific
    }

    private fun primitiveType(name: String): Class<*>? = when (name) {
        "boolean" -> Boolean::class.javaPrimitiveType
        "byte" -> Byte::class.javaPrimitiveType
        "char" -> Char::class.javaPrimitiveType
        "short" -> Short::class.javaPrimitiveType
        "int" -> Int::class.javaPrimitiveType
        "long" -> Long::class.javaPrimitiveType
        "float" -> Float::class.javaPrimitiveType
        "double" -> Double::class.javaPrimitiveType
        "void" -> Void.TYPE
        else -> null
    }

    private fun boxedType(type: Class<*>): Class<*> = when (type) {
        Boolean::class.javaPrimitiveType -> Boolean::class.javaObjectType
        Byte::class.javaPrimitiveType -> Byte::class.javaObjectType
        Char::class.javaPrimitiveType -> Char::class.javaObjectType
        Short::class.javaPrimitiveType -> Short::class.javaObjectType
        Int::class.javaPrimitiveType -> Int::class.javaObjectType
        Long::class.javaPrimitiveType -> Long::class.javaObjectType
        Float::class.javaPrimitiveType -> Float::class.javaObjectType
        Double::class.javaPrimitiveType -> Double::class.javaObjectType
        else -> type
    }
}
