package org.shedlang.compiler.util

import kotlin.reflect.KProperty

interface Box<out T> {
    operator fun getValue(thisRef: Any, property: KProperty<*>): T {
        return get()
    }

    fun get(): T

    fun <R> map(func: (T) -> R): Box<R> {
        return MappedBox(this, func)
    }

    companion object {
        fun <T> mutable(): MutableBox<T> {
            return MutableBox(false, null)
        }

        fun <T> of(value: T): Box<T> {
            return MutableBox(true, value)
        }
    }
}

class MutableBox<T>(private var isSet: Boolean, private var value: T?): Box<T> {
    override fun get(): T {
        if (!isSet) {
            throw RuntimeException("box is empty")
        }

        return value as T
    }

    fun set(value: T) {
        if (isSet) {
            throw RuntimeException("box is not empty")
        }
        this.isSet = true
        this.value = value
    }
}

class MappedBox<T, R>(private val box: Box<T>, private val func: (T) -> R): Box<R> {
    override fun get(): R {
        return func(box.get())
    }
}
