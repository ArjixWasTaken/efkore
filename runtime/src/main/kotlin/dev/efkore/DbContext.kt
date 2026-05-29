package dev.efkore

import dev.efkore.metadata.EntityModel
import dev.efkore.runtime.R2dbcExecutor
import io.r2dbc.spi.ConnectionFactory
import kotlin.reflect.KClass

abstract class DbContext(
    private val connectionFactory: ConnectionFactory
) {
    internal val models = mutableMapOf<KClass<*>, EntityModel<*>>()
    internal val executor = R2dbcExecutor(connectionFactory)

    @Suppress("UNCHECKED_CAST")
    @PublishedApi
    internal fun <T : Any> model(type: KClass<T>): EntityModel<T> =
        models.getOrPut(type) { EntityModel(type) } as EntityModel<T>

    @Suppress("UNCHECKED_CAST")
    protected inline fun <reified T : Any> set(): DbSet<T> {
        model(T::class)
        return DbSet(T::class, this)
    }
}
