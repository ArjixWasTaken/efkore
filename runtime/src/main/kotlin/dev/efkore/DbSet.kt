package dev.efkore

import dev.efkore.expressions.*
import dev.efkore.runtime.Materializer
import dev.efkore.sql.ZekoTranslator
import dev.efkore.tracking.ChangeTracker
import kotlin.reflect.KClass

class DbSet<T : Any>(
    internal val entityType: KClass<T>,
    internal val context: DbContext,
    private val expression: Expression = QueryRootExpression(entityType),
    internal val changeTracker: ChangeTracker<T> = ChangeTracker(entityType)
) {
    private val translator = ZekoTranslator()

    // Lambda overloads — plugin rewrites to *Expr calls; throw at runtime without plugin.
    fun filter(predicate: (T) -> Boolean): DbSet<T> =
        throw UnsupportedOperationException("Compile efkore with the compiler plugin")

    fun <R : Any> map(selector: (T) -> R): DbSet<R> =
        throw UnsupportedOperationException("Compile efkore with the compiler plugin")

    fun sortedBy(keySelector: (T) -> Comparable<*>): DbSet<T> =
        throw UnsupportedOperationException("Compile efkore with the compiler plugin")

    fun sortedByDescending(keySelector: (T) -> Comparable<*>): DbSet<T> =
        throw UnsupportedOperationException("Compile efkore with the compiler plugin")

    // Expression overloads — the plugin rewrites lambda calls into these.
    fun filterExpr(predicate: LambdaExpression): DbSet<T> =
        DbSet(entityType, context, FilterExpression(expression, predicate))

    @Suppress("UNCHECKED_CAST")
    fun <R : Any> mapExpr(resultType: KClass<R>, selector: LambdaExpression): DbSet<R> =
        DbSet(resultType, context, ProjectExpression(expression, selector))

    fun sortedByExpr(keySelector: LambdaExpression): DbSet<T> =
        DbSet(entityType, context, OrderByExpression(expression, keySelector, descending = false))

    fun sortedByDescendingExpr(keySelector: LambdaExpression): DbSet<T> =
        DbSet(entityType, context, OrderByExpression(expression, keySelector, descending = true))

    fun skip(count: Int): DbSet<T> =
        DbSet(entityType, context, OffsetExpression(expression, count))

    fun take(count: Int): DbSet<T> =
        DbSet(entityType, context, LimitExpression(expression, count))

    fun distinct(): DbSet<T> =
        DbSet(entityType, context, DistinctExpression(expression))

    fun thenBy(keySelector: LambdaExpression): DbSet<T> =
        DbSet(entityType, context, ThenByExpression(expression, keySelector, descending = false))

    fun thenByDescending(keySelector: LambdaExpression): DbSet<T> =
        DbSet(entityType, context, ThenByExpression(expression, keySelector, descending = true))

    suspend fun find(vararg keys: Any?): T? {
        val model = context.model(entityType)
        val keyColumns = model.columns.filter { it.isKey }
        val keyValues = keyColumns.zip(keys.toList()).associate { (col, value) ->
            col.columnName to value
        }
        val findExpr = FindExpression(entityType, keyValues)
        val (sql, params) = translator.translate(findExpr)
        val rows = context.executor.execute(sql, params)
        return rows.map { Materializer(entityType, model).materialize(it) }.firstOrNull()
    }

    suspend fun toList(): List<T> {
        val (sql, params) = translator.translate(expression)
        val rows = context.executor.execute(sql, params)
        val materializer = Materializer(entityType, context.model(entityType))
        return rows.map { materializer.materialize(it) }
    }

    suspend fun first(): T? = toList().firstOrNull()
    suspend fun firstOrNull(): T? = toList().firstOrNull()

    fun add(entity: T): T {
        changeTracker.add(entity)
        return entity
    }

    fun remove(entity: T) {
        changeTracker.remove(entity)
    }
}
