/**
 * AnyStream
 * Copyright (C) 2022 AnyStream Maintainers
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package anystream.db.extensions

import cn.danielw.fop.ObjectFactory
import cn.danielw.fop.ObjectPool
import cn.danielw.fop.PoolConfig
import org.jdbi.v3.core.Handle
import org.jdbi.v3.core.Jdbi
import org.jdbi.v3.core.config.JdbiConfig
import org.jdbi.v3.core.extension.Extensions
import org.jdbi.v3.core.extension.NoSuchExtensionException
import org.jdbi.v3.core.internal.OnDemandExtensions
import org.jdbi.v3.core.internal.exceptions.Unchecked
import java.lang.invoke.MethodHandles
import java.lang.reflect.InvocationHandler
import java.lang.reflect.Method
import java.lang.reflect.Proxy
import java.util.*
import java.util.function.Function
import java.util.stream.Stream
import kotlin.streams.toList

class PooledExtensions : JdbiConfig<PooledExtensions> {
    private lateinit var factory: OnDemandExtensions.Factory
    private var jdbi: Jdbi? = null
        set(value) {
            field = value ?: return
            val onDemandConfig = value.getConfig(OnDemandExtensions::class.java)
            val factoryField = OnDemandExtensions::class.java.getDeclaredField("factory")
            factoryField.isAccessible = true
            factory = factoryField.get(onDemandConfig) as OnDemandExtensions.Factory
        }

    private val fact = object : ObjectFactory<Handle> {
        override fun create(): Handle {
            return requireNotNull(jdbi?.open())
        }

        override fun destroy(handle: Handle) {
            if (!handle.isClosed) handle.close()
        }

        override fun validate(handle: Handle): Boolean {
            return !handle.isClosed
        }
    }
    private val pool = ObjectPool(
        PoolConfig().apply {
            minSize = 0
            maxIdleMilliseconds = 5000
        },
        fact
    )

    fun <E> create(db: Jdbi, extensionType: Class<E>, extraTypes: Array<Class<*>> = emptyArray()): E {
        jdbi = db
        return extensionType.cast(
            factory.onDemand(db, extensionType, *extraTypes)
                .orElseGet { createProxy(db, extensionType, extraTypes) }
        )
    }

    private fun createProxy(db: Jdbi, extensionType: Class<*>, extraTypes: Array<Class<*>>): Any {
        db.getConfig(Extensions::class.java).onCreateProxy()
        val handler = InvocationHandler { proxy: Any?, method: Method, args: Array<Any>? ->
            return@InvocationHandler when (method) {
                EQUALS_METHOD -> proxy === args?.get(0)
                HASHCODE_METHOD -> System.identityHashCode(proxy)
                TOSTRING_METHOD -> "$extensionType@" + Integer.toHexString(System.identityHashCode(proxy))
                else -> {
                    pool.borrowObject().use {
                        val extension = it.`object`.attach(extensionType)
                        invoke(extension, method, args)
                    }
                }
            }
        }
        val types = Stream.of<Stream<out Class<*>>>(
            Stream.of(extensionType),
            Arrays.stream(extensionType.interfaces),
            Arrays.stream(extraTypes)
        ).flatMap(Function.identity())
            .distinct()
            .toList()
            .toTypedArray()
        return Proxy.newProxyInstance(extensionType.classLoader, types, handler)
    }

    override fun createCopy(): PooledExtensions {
        return PooledExtensions()
    }

    fun shutdown() {
        try {
            pool.shutdown()
        } catch (e: InterruptedException) {
            e.printStackTrace()
        }
    }

    companion object {
        private val EQUALS_METHOD: Method
        private val HASHCODE_METHOD: Method
        private val TOSTRING_METHOD: Method

        init {
            try {
                EQUALS_METHOD = Any::class.java.getMethod("equals", Any::class.java)
                HASHCODE_METHOD = Any::class.java.getMethod("hashCode")
                TOSTRING_METHOD = Any::class.java.getMethod("toString")
            } catch (wat: NoSuchMethodException) {
                throw IllegalStateException("OnDemandExtensions initialization failed", wat)
            }
        }

        private operator fun invoke(target: Any, method: Method, args: Array<Any>?): Any? {
            return if (Proxy.isProxyClass(target.javaClass)) {
                val handler = Proxy.getInvocationHandler(target)
                Unchecked.function { params: Array<Any>? -> handler.invoke(target, method, params) }.apply(args)
            } else {
                val handle = Unchecked.function { m: Method? -> MethodHandles.lookup().unreflect(m) }
                    .apply(method)
                    .bindTo(target)
                Unchecked.function { arguments: Array<Any>? -> handle.invokeWithArguments(arguments) }.apply(args)
            }
        }
    }
}

inline fun <reified T : Any> Jdbi.pooled(): T = pooled(T::class.java)

fun <E> Jdbi.pooled(extensionType: Class<E>): E {
    require(extensionType.isInterface) { "On-demand extensions are only supported for interfaces." }
    if (!getConfig(Extensions::class.java).hasExtensionFor(extensionType)) {
        throw NoSuchExtensionException("Extension not found: $extensionType")
    }
    return getConfig(PooledExtensions::class.java).create(this, extensionType)
}
