package dev.efkore

import dev.efkore.metadata.EntityModel
import dev.efkore.runtime.R2dbcExecutor
import dev.efkore.sql.ZekoTranslator
import dev.efkore.tracking.EntityState
import io.r2dbc.spi.ConnectionFactory
import kotlin.reflect.KClass

abstract class DbContext(
    private val connectionFactory: ConnectionFactory
) {
    internal val models = mutableMapOf<KClass<*>, EntityModel<*>>()
    internal val executor = R2dbcExecutor(connectionFactory)
    @PublishedApi
    internal val sets = mutableListOf<DbSet<*>>()

    @Suppress("UNCHECKED_CAST")
    @PublishedApi
    internal fun <T : Any> model(type: KClass<T>): EntityModel<T> =
        models.getOrPut(type) { EntityModel(type) } as EntityModel<T>

    @Suppress("UNCHECKED_CAST")
    protected inline fun <reified T : Any> set(): DbSet<T> {
        model(T::class)
        val dbSet = DbSet(T::class, this)
        sets.add(dbSet)
        return dbSet
    }

    suspend fun saveChanges(): Int {
        val translator = ZekoTranslator()
        var count = 0
        for (set in sets) {
            @Suppress("UNCHECKED_CAST")
            val model = model(set.entityType) as EntityModel<Any>
            for (entry in set.changeTracker.getChanges()) {
                val params = mutableListOf<Any?>()
                val sql = when (entry.state) {
                    EntityState.Added -> translator.translateInsert(entry.entity, model, params)
                    EntityState.Modified -> translator.translateUpdate(entry.entity, model, params)
                    EntityState.Deleted -> translator.translateDelete(entry.entity, model, params)
                    EntityState.Unchanged -> continue
                }
                count += executor.executeUpdate(sql, params)
            }
            set.changeTracker.acceptChanges()
        }
        return count
    }
}
