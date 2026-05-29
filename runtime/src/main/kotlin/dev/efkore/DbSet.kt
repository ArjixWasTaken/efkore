package dev.efkore

import dev.efkore.expressions.*
import dev.efkore.runtime.Materializer
import dev.efkore.sql.ZekoTranslator
import kotlin.reflect.KClass

class DbSet<T : Any>(
    internal val entityType: KClass<T>,
    internal val context: DbContext,
    private val expression: Expression = QueryRootExpression(entityType)
) {
    private val translator = ZekoTranslator()

    fun filter(predicate: LambdaExpression): DbSet<T> =
        DbSet(entityType, context, FilterExpression(expression, predicate))

    fun <R : Any> map(selector: LambdaExpression, resultType: KClass<R>): DbSet<R> =
        DbSet(resultType, context, ProjectExpression(expression, selector))

    fun sortedBy(keySelector: LambdaExpression): DbSet<T> =
        DbSet(entityType, context, OrderByExpression(expression, keySelector, descending = false))

    fun sortedByDescending(keySelector: LambdaExpression): DbSet<T> =
        DbSet(entityType, context, OrderByExpression(expression, keySelector, descending = true))

    fun skip(count: Int): DbSet<T> =
        DbSet(entityType, context, OffsetExpression(expression, count))

    fun take(count: Int): DbSet<T> =
        DbSet(entityType, context, LimitExpression(expression, count))

    fun distinct(): DbSet<T> =
        DbSet(entityType, context, DistinctExpression(expression))

    suspend fun toList(): List<T> {
        val (sql, params) = translator.translate(expression)
        val rows = context.executor.execute(sql, params)
        val materializer = Materializer(entityType, context.model(entityType))
        return rows.map { materializer.materialize(it) }
    }

    suspend fun first(): T? = toList().firstOrNull()
    suspend fun firstOrNull(): T? = toList().firstOrNull()
}
