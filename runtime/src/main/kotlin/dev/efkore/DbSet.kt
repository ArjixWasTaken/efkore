package dev.efkore

import dev.efkore.expressions.*
import kotlin.reflect.KClass
import kotlin.reflect.KMutableProperty1

class DbSet<T : Any>(
    internal val entityType: KClass<T>,
    internal val context: DbContext,
    internal val expression: Expression = QueryRootExpression(entityType)
) {
    // Lambda overloads — plugin rewrites to *Expr calls; throw at runtime without plugin.
    fun filter(predicate: (T) -> Boolean): DbSet<T> =
        throw UnsupportedOperationException("Compile efkore with the compiler plugin")

    fun <R : Any> map(selector: (T) -> R): DbSet<R> =
        throw UnsupportedOperationException("Compile efkore with the compiler plugin")

    fun sortedBy(keySelector: (T) -> Comparable<*>): DbSet<T> =
        throw UnsupportedOperationException("Compile efkore with the compiler plugin")

    fun sortedByDescending(keySelector: (T) -> Comparable<*>): DbSet<T> =
        throw UnsupportedOperationException("Compile efkore with the compiler plugin")

    fun thenBy(keySelector: (T) -> Comparable<*>): DbSet<T> =
        throw UnsupportedOperationException("Compile efkore with the compiler plugin")

    fun thenByDescending(keySelector: (T) -> Comparable<*>): DbSet<T> =
        throw UnsupportedOperationException("Compile efkore with the compiler plugin")

    suspend fun any(predicate: (T) -> Boolean): Boolean =
        throw UnsupportedOperationException("Compile efkore with the compiler plugin")

    suspend fun all(predicate: (T) -> Boolean): Boolean =
        throw UnsupportedOperationException("Compile efkore with the compiler plugin")

    suspend fun removeWhere(predicate: (T) -> Boolean): Long =
        throw UnsupportedOperationException("Compile efkore with the compiler plugin")

    suspend fun <R : Any> sumOf(selector: (T) -> R): R =
        throw UnsupportedOperationException("Compile efkore with the compiler plugin")

    suspend fun <R : Any> minOf(selector: (T) -> R): R =
        throw UnsupportedOperationException("Compile efkore with the compiler plugin")

    suspend fun <R : Any> maxOf(selector: (T) -> R): R =
        throw UnsupportedOperationException("Compile efkore with the compiler plugin")

    suspend fun <R : Any> averageOf(selector: (T) -> R): R =
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

    fun thenByExpr(keySelector: LambdaExpression): DbSet<T> =
        DbSet(entityType, context, ThenByExpression(expression, keySelector, descending = false))

    fun thenByDescendingExpr(keySelector: LambdaExpression): DbSet<T> =
        DbSet(entityType, context, ThenByExpression(expression, keySelector, descending = true))

    suspend fun anyExpr(predicate: LambdaExpression): Boolean =
        context.executor.executeExists(AnyExpression(expression, predicate), context.model(rootEntityType(expression)))

    suspend fun allExpr(predicate: LambdaExpression): Boolean =
        context.executor.executeExists(AllExpression(expression, predicate), context.model(rootEntityType(expression)))

    // Bulk DELETE; bypasses the change tracker, no entities are loaded.
    suspend fun removeWhereExpr(predicate: LambdaExpression): Long =
        context.executor.executeDelete(predicate, context.model(rootEntityType(expression)))

    suspend fun <R : Any> sumOfExpr(resultType: KClass<R>, selector: LambdaExpression): R =
        context.executor.executeScalar(AggregateExpression(AggregateOp.SUM, expression, selector), resultType, context.model(rootEntityType(expression)))

    suspend fun <R : Any> minOfExpr(resultType: KClass<R>, selector: LambdaExpression): R =
        context.executor.executeScalar(AggregateExpression(AggregateOp.MIN, expression, selector), resultType, context.model(rootEntityType(expression)))

    suspend fun <R : Any> maxOfExpr(resultType: KClass<R>, selector: LambdaExpression): R =
        context.executor.executeScalar(AggregateExpression(AggregateOp.MAX, expression, selector), resultType, context.model(rootEntityType(expression)))

    suspend fun <R : Any> averageOfExpr(resultType: KClass<R>, selector: LambdaExpression): R =
        context.executor.executeScalar(AggregateExpression(AggregateOp.AVG, expression, selector), resultType, context.model(rootEntityType(expression)))

    // Non-lambda operations — no plugin needed.
    fun distinct(): DbSet<T> = DbSet(entityType, context, DistinctExpression(expression))
    fun take(n: Int): DbSet<T> = DbSet(entityType, context, LimitExpression(expression, n))
    fun drop(n: Int): DbSet<T> = DbSet(entityType, context, OffsetExpression(expression, n))
    fun addAll(entities: Iterable<T>) { entities.forEach { add(it) } }

    fun update(entity: T) {
        context.changeTracker.track(entity, entityType, dev.efkore.tracking.EntityState.Modified)
    }

    suspend fun find(id: Any): T? {
        val model = context.model(entityType)
        val cached = context.changeTracker.findById(entityType, id, model)
        if (cached != null) return cached

        val pkProp = property(model.keyColumn.property)
        val pred = lambdaExpr(paramExpr("it", entityType), eq(pkProp, constant(id)))
        return filterExpr(pred).firstOrNull()
    }

    suspend fun findOrNull(id: Any): T? = find(id)

    // Raw SQL escape hatch — rows mapped to T like a normal query.
    suspend fun fromSql(sql: String, vararg params: Any?): List<T> =
        context.executor.executeRawQuery(sql, params.toList(), entityType, context.model(entityType))

    // Terminals
    suspend fun toList(): List<T> = context.executor.execute(expression, entityType, context.model(rootEntityType(expression)))
    suspend fun first(): T = toList().first()
    suspend fun firstOrNull(): T? = toList().firstOrNull()
    suspend fun single(): T = toList().single()
    suspend fun singleOrNull(): T? = toList().singleOrNull()

    suspend fun count(): Long =
        context.executor.executeScalar(
            AggregateExpression(AggregateOp.COUNT, expression, null),
            Long::class, context.model(rootEntityType(expression))
        )

    suspend fun contains(entity: T): Boolean {
        val model = context.model(entityType)
        @Suppress("UNCHECKED_CAST")
        val pkVal = (model.keyColumn.property as KMutableProperty1<T, *>).get(entity)!!
        val pkProp = property(model.keyColumn.property)
        val pred = lambdaExpr(paramExpr("it", entityType), eq(pkProp, constant(pkVal)))
        return context.executor.executeExists(AnyExpression(expression, pred), model)
    }

    // Change tracking
    @Suppress("UNCHECKED_CAST")
    private fun rootEntityType(expr: Expression): KClass<Any> = when (expr) {
        is QueryRootExpression -> expr.entityType as KClass<Any>
        is FilterExpression    -> rootEntityType(expr.source)
        is ProjectExpression   -> rootEntityType(expr.source)
        is OrderByExpression   -> rootEntityType(expr.source)
        is ThenByExpression    -> rootEntityType(expr.source)
        is LimitExpression     -> rootEntityType(expr.source)
        is OffsetExpression    -> rootEntityType(expr.source)
        is DistinctExpression  -> rootEntityType(expr.source)
        is AggregateExpression -> rootEntityType(expr.source)
        is AnyExpression       -> rootEntityType(expr.source)
        is AllExpression       -> rootEntityType(expr.source)
        else -> throw IllegalStateException("No root in expression: $expr")
    }

    fun toSql(): dev.efkore.sql.BoundSql =
        context.executor.toSql(expression, context.model(rootEntityType(expression)))

    fun add(entity: T) { context.changeTracker.track(entity, entityType, dev.efkore.tracking.EntityState.Added) }
    fun remove(entity: T) { context.changeTracker.track(entity, entityType, dev.efkore.tracking.EntityState.Deleted) }
}
