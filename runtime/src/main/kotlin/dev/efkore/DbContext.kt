package dev.efkore

import dev.efkore.metadata.EntityModel
import dev.efkore.runtime.R2dbcExecutor
import dev.efkore.sql.SqlDialect
import dev.efkore.tracking.ChangeTracker
import dev.efkore.tracking.EntityState
import io.r2dbc.spi.ConnectionFactory
import kotlinx.coroutines.reactive.awaitFirst
import kotlinx.coroutines.reactive.awaitFirstOrNull
import org.slf4j.LoggerFactory
import kotlin.reflect.KClass
import kotlin.reflect.KMutableProperty1

class DbContextOptions(
    val connectionFactory: ConnectionFactory,
    val dialect: SqlDialect = SqlDialect.H2
)

open class DbContext(val options: DbContextOptions) {
    private val log = LoggerFactory.getLogger(DbContext::class.java)
    internal val models = mutableMapOf<KClass<*>, EntityModel<*>>()

    internal val executor = R2dbcExecutor(options.connectionFactory, options.dialect)
    internal val changeTracker = ChangeTracker()
    val database = DatabaseFacade(this)

    @Suppress("UNCHECKED_CAST")
    internal fun <T : Any> model(type: KClass<T>): EntityModel<T> =
        models.getOrPut(type) { EntityModel(type) } as EntityModel<T>

    protected fun <T : Any> dbSet(type: KClass<T>): DbSet<T> {
        model(type)
        return DbSet(type, this)
    }

    protected inline fun <reified T : Any> dbSet(): DbSet<T> = dbSet(T::class)

    suspend fun <R> transaction(block: suspend () -> R): R {
        return executor.withConnection { conn ->
            conn.beginTransaction().awaitFirstOrNull()
            try {
                val result = block()
                conn.commitTransaction().awaitFirstOrNull()
                result
            } catch (e: Exception) {
                conn.rollbackTransaction().awaitFirstOrNull()
                throw e
            }
        }
    }

    suspend fun saveChanges(): Int {
        val pending = changeTracker.pendingChanges()
        if (pending.isEmpty()) return 0
        var count = 0
        executor.withConnection { conn ->
            conn.beginTransaction().awaitFirstOrNull()
            try {
                pending.forEach { entry ->
                    @Suppress("UNCHECKED_CAST")
                    val m = model(entry.entityType as KClass<Any>)
                    when (entry.state) {
                        EntityState.Added -> {
                            val insertCols = if (m.keyColumn.isGenerated) m.nonKeyColumns else m.columns
                            val colNames = insertCols.joinToString(", ") { "\"${it.columnName}\"" }
                            val params = insertCols.joinToString(", ") { "?" }
                            val sql = "INSERT INTO \"${m.tableName}\" ($colNames) VALUES ($params)"
                            log.debug("INSERT: {}", sql)
                            val stmt = if (m.keyColumn.isGenerated) {
                                conn.createStatement(sql).returnGeneratedValues(m.keyColumn.columnName)
                            } else {
                                conn.createStatement(sql)
                            }
                            insertCols.forEachIndexed { i, col ->
                                @Suppress("UNCHECKED_CAST")
                                val v = (col.property as KMutableProperty1<Any, *>).get(entry.entity)
                                if (v != null) stmt.bind(i, v)
                                else stmt.bindNull(i, (col.property.returnType.classifier as KClass<*>).java)
                            }
                            val result = stmt.execute().awaitFirst()
                            if (m.keyColumn.isGenerated) {
                                result.map { row, _ -> row.get(0) }.awaitFirstOrNull()?.let { key ->
                                    @Suppress("UNCHECKED_CAST")
                                    (m.keyColumn.property as KMutableProperty1<Any, Any?>).set(
                                        entry.entity,
                                        coerceKey(key, m.keyColumn.property.returnType.classifier as KClass<*>)
                                    )
                                }
                            }
                            count++
                        }
                        EntityState.Modified -> {
                            val changed = changeTracker.changedColumns(entry, m)
                            if (changed.isNotEmpty()) {
                                val sets = changed.joinToString(", ") { "\"${it.columnName}\" = ?" }
                                val sql = "UPDATE \"${m.tableName}\" SET $sets WHERE \"${m.keyColumn.columnName}\" = ?"
                                log.debug("UPDATE: {}", sql)
                                val stmt = conn.createStatement(sql)
                                changed.forEachIndexed { i, col ->
                                    @Suppress("UNCHECKED_CAST")
                                    val v = (col.property as KMutableProperty1<Any, *>).get(entry.entity)
                                    if (v != null) stmt.bind(i, v)
                                    else stmt.bindNull(i, (col.property.returnType.classifier as KClass<*>).java)
                                }
                                @Suppress("UNCHECKED_CAST")
                                val keyVal = (m.keyColumn.property as KMutableProperty1<Any, *>).get(entry.entity)
                                if (keyVal != null) stmt.bind(changed.size, keyVal)
                                stmt.execute().awaitFirst()
                                count++
                            }
                        }
                        EntityState.Deleted -> {
                            val sql = "DELETE FROM \"${m.tableName}\" WHERE \"${m.keyColumn.columnName}\" = ?"
                            log.debug("DELETE: {}", sql)
                            val stmt = conn.createStatement(sql)
                            @Suppress("UNCHECKED_CAST")
                            val keyVal = (m.keyColumn.property as KMutableProperty1<Any, *>).get(entry.entity)
                            if (keyVal != null) stmt.bind(0, keyVal)
                            stmt.execute().awaitFirst()
                            count++
                        }
                        else -> {}
                    }
                }
                @Suppress("UNCHECKED_CAST")
                pending.map { it.entityType as KClass<Any> }.distinct().forEach { t ->
                    changeTracker.resetAfterSave(model(t))
                }
                conn.commitTransaction().awaitFirstOrNull()
            } catch (e: Exception) {
                conn.rollbackTransaction().awaitFirstOrNull()
                throw e
            }
        }
        return count
    }

    private fun coerceKey(value: Any, targetType: KClass<*>): Any = when (targetType) {
        Int::class -> (value as Number).toInt()
        Long::class -> (value as Number).toLong()
        else -> value
    }
}

class DatabaseFacade(private val ctx: DbContext) {
    private val log = LoggerFactory.getLogger(DatabaseFacade::class.java)

    suspend fun ensureCreated() {
        ctx.executor.withConnection { conn ->
            ctx.models.values.forEach { model ->
                val ddl = model.createDdl()
                log.debug("DDL: {}", ddl)
                conn.createStatement(ddl).execute().awaitFirstOrNull()
            }
        }
    }
}
