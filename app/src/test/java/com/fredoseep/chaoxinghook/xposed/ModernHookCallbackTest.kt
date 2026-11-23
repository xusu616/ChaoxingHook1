package com.fredoseep.chaoxinghook.xposed

import io.github.libxposed.api.XposedInterface
import java.lang.reflect.Executable
import java.util.Collections
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class ModernHookCallbackTest {
    @Test
    fun `before hook changes copied arguments and proceeds once`() {
        val receiver = Receiver()
        val chain = FakeChain(receiver, listOf("original")) { args -> args[0] }
        val callback = object : ModernHookCallback() {
            override fun beforeHookedMethod(param: MethodHookParam) {
                assertSame(receiver, param.thisObject)
                assertSame(chain.getExecutable(), param.method)
                param.args[0] = "changed"
            }
        }

        assertEquals("changed", callback.intercept(chain))
        assertEquals(listOf("original"), chain.getArgs())
        assertEquals(1, chain.proceedCount)
        assertArrayEquals(arrayOf<Any?>("changed"), chain.proceededArguments)
    }

    @Test
    fun `early result skips original and still runs after hook`() {
        val chain = FakeChain(Receiver(), listOf("original")) { error("Original must not run") }
        var afterCalled = false
        val callback = object : ModernHookCallback() {
            override fun beforeHookedMethod(param: MethodHookParam) {
                param.setResult("short circuit")
            }

            override fun afterHookedMethod(param: MethodHookParam) {
                afterCalled = true
                assertEquals("short circuit", param.getResult())
                assertFalse(param.hasThrowable())
            }
        }

        assertEquals("short circuit", callback.intercept(chain))
        assertTrue(afterCalled)
        assertEquals(0, chain.proceedCount)
    }

    @Test
    fun `do nothing callback skips original for void methods`() {
        val chain = FakeChain(Receiver(), emptyList()) { error("Original must not run") }

        assertNull(ModernHookCallback.DO_NOTHING.intercept(chain))
        assertEquals(0, chain.proceedCount)
    }

    @Test
    fun `after hook replaces original result`() {
        val chain = FakeChain(Receiver(), emptyList()) { "original result" }
        val callback = object : ModernHookCallback() {
            override fun afterHookedMethod(param: MethodHookParam) {
                assertEquals("original result", param.getResult())
                param.setResult("replacement result")
            }
        }

        assertEquals("replacement result", callback.intercept(chain))
        assertEquals(1, chain.proceedCount)
    }

    @Test
    fun `original exception reaches after hook and propagates unchanged`() {
        val failure = IllegalStateException("original failure")
        val chain = FakeChain(Receiver(), emptyList()) { throw failure }
        var afterCalled = false
        val callback = object : ModernHookCallback() {
            override fun afterHookedMethod(param: MethodHookParam) {
                afterCalled = true
                assertTrue(param.hasThrowable())
                assertSame(failure, param.getThrowable())
                assertNull(param.getResult())
            }
        }

        val thrown = assertThrows(IllegalStateException::class.java) { callback.intercept(chain) }
        assertSame(failure, thrown)
        assertTrue(afterCalled)
        assertEquals(1, chain.proceedCount)
    }

    @Test
    fun `after hook can recover original exception with result`() {
        val failure = IllegalArgumentException("original failure")
        val chain = FakeChain(Receiver(), emptyList()) { throw failure }
        val callback = object : ModernHookCallback() {
            override fun afterHookedMethod(param: MethodHookParam) {
                assertSame(failure, param.getThrowable())
                param.setResult("recovered")
                assertFalse(param.hasThrowable())
            }
        }

        assertEquals("recovered", callback.intercept(chain))
        assertEquals(1, chain.proceedCount)
    }

    @Test
    fun `before hook exception outcome skips original and is preserved`() {
        val failure = IllegalStateException("forced failure")
        val chain = FakeChain(Receiver(), emptyList()) { error("Original must not run") }
        val callback = object : ModernHookCallback() {
            override fun beforeHookedMethod(param: MethodHookParam) {
                param.setThrowable(failure)
            }

            override fun afterHookedMethod(param: MethodHookParam) {
                assertSame(failure, param.getThrowable())
            }
        }

        assertSame(failure, assertThrows(IllegalStateException::class.java) { callback.intercept(chain) })
        assertEquals(0, chain.proceedCount)
    }

    private class Receiver {
        fun target(value: String): String = value
    }

    /** 模拟框架的不可变实参，并记录原实现实际收到的参数。 */
    private class FakeChain(
        private val receiver: Any?,
        arguments: List<Any?>,
        private val original: (Array<out Any?>) -> Any?,
    ) : XposedInterface.Chain {
        private val immutableArguments = Collections.unmodifiableList(arguments.toList())
        private val targetMethod = Receiver::class.java.getDeclaredMethod("target", String::class.java)
        var proceedCount = 0
            private set
        var proceededArguments: Array<out Any?>? = null
            private set

        override fun getExecutable(): Executable = targetMethod

        override fun getThisObject(): Any? = receiver

        override fun getArgs(): List<Any?> = immutableArguments

        override fun getArg(index: Int): Any? = immutableArguments[index]

        override fun proceed(): Any? = runOriginal(immutableArguments.toTypedArray())

        override fun proceed(args: Array<out Any?>): Any? = runOriginal(args)

        override fun proceedWith(thisObject: Any): Any? = runOriginal(immutableArguments.toTypedArray())

        override fun proceedWith(thisObject: Any, args: Array<out Any?>): Any? = runOriginal(args)

        private fun runOriginal(args: Array<out Any?>): Any? {
            proceedCount += 1
            proceededArguments = args.copyOf()
            return original(args)
        }
    }
}
