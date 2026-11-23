package com.fredoseep.chaoxinghook.xposed

import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class HookRuntimeTest {
    @Test
    fun `f0 protocol chooses primitive and most specific nullable argument`() {
        assertEquals("int-string:1:null", HookRuntime.callMethod(Protocol(), "f0", 1, null))
    }

    @Test
    fun `exact boxed overload takes precedence over unboxing`() {
        assertEquals("boxed", HookRuntime.callMethod(BoxedOverloads(), "choose", 1))
    }

    @Test
    fun `private field inherited from superclass remains accessible`() {
        val itemView = Any()
        assertSame(itemView, HookRuntime.getObjectField(ChildViewHolder(itemView), "itemView"))
    }

    @Test
    fun `null with unrelated reference overloads is rejected as ambiguous`() {
        val failure = assertThrows(IllegalArgumentException::class.java) {
            HookRuntime.callMethod(AmbiguousMethods(), "choose", null)
        }
        assertTrue(failure.message.orEmpty().contains("Ambiguous signatures"))
    }

    @Test
    fun `null is incompatible with primitive parameter`() {
        assertThrows(NoSuchMethodException::class.java) {
            HookRuntime.callMethod(PrimitiveOnly(), "choose", null)
        }
    }

    @Test
    fun `constructor resolves primitive and null using same rules as methods`() {
        val response = HookRuntime.newInstance(Response::class.java, 1, null) as Response
        assertEquals("int-string:1:null", response.selected)
    }

    @Test
    fun `ambiguous constructor is rejected`() {
        assertThrows(IllegalArgumentException::class.java) {
            HookRuntime.newInstance(AmbiguousConstructors::class.java, null)
        }
    }

    @Test
    fun `reflection propagates original method exception without invocation wrapper`() {
        val originalFailure = IllegalStateException("protocol failure")
        val thrown = assertThrows(IllegalStateException::class.java) {
            HookRuntime.callMethod(ThrowingProtocol(originalFailure), "fail")
        }
        assertSame(originalFailure, thrown)
    }

    @Test
    fun `overridden signature selects subclass implementation`() {
        assertEquals("child", HookRuntime.callMethod(ChildProtocol(), "value", 1))
    }

    private class Protocol {
        @Suppress("unused")
        private fun f0(status: Int, payload: String?): String = "int-string:$status:$payload"

        @Suppress("unused")
        private fun f0(status: Number, payload: CharSequence?): String = "number-text:$status:$payload"
    }

    private class BoxedOverloads {
        @Suppress("unused", "UNUSED_PARAMETER")
        fun choose(value: Int): String = "primitive"

        @Suppress("unused", "UNUSED_PARAMETER")
        fun choose(value: Int?): String = "boxed"
    }

    private open class ParentViewHolder(@Suppress("unused") private val itemView: Any)

    private class ChildViewHolder(itemView: Any) : ParentViewHolder(itemView)

    private class AmbiguousMethods {
        @Suppress("unused", "UNUSED_PARAMETER")
        fun choose(value: String?): String = "string"

        @Suppress("unused", "UNUSED_PARAMETER")
        fun choose(value: StringBuilder?): String = "builder"
    }

    private class PrimitiveOnly {
        @Suppress("unused", "UNUSED_PARAMETER")
        fun choose(value: Int): String = "primitive"
    }

    private class Response {
        val selected: String

        constructor(status: Int, payload: String?) {
            selected = "int-string:$status:$payload"
        }

        constructor(status: Number, payload: CharSequence?) {
            selected = "number-text:$status:$payload"
        }
    }

    private class AmbiguousConstructors {
        @Suppress("UNUSED_PARAMETER")
        constructor(value: String?)

        @Suppress("UNUSED_PARAMETER")
        constructor(value: StringBuilder?)
    }

    private class ThrowingProtocol(private val failure: Throwable) {
        @Suppress("unused")
        fun fail(): Nothing = throw failure
    }

    private open class ParentProtocol {
        @Suppress("UNUSED_PARAMETER")
        open fun value(number: Number): String = "parent"
    }

    private class ChildProtocol : ParentProtocol() {
        override fun value(number: Number): String = "child"
    }
}
