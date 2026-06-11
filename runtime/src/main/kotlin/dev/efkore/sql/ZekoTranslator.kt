package dev.efkore.sql

import dev.efkore.expressions.*
import dev.efkore.metadata.EntityModel
import io.zeko.db.sql.ANSIQuery
import java.util.concurrent.atomic.AtomicInteger

data class BoundSql(val sql: String, val params: List<Any?>)

enum class SqlDialect { H2, POSTGRES }

private data class QueryChain(
    val root: QueryRootExpression,
    val filter: FilterExpression?,
    val project: ProjectExpression?,
    val orderBys: List<Pair<LambdaExpression, Boolean>>,
    val limit: Int?,
    val offset: Int?,
    val distinct: Boolean
)

private fun unwrapChain(expr: Expression): QueryChain {
    var current = expr
    var filter: FilterExpression? = null
    var project: ProjectExpression? = null
    val orderBys = mutableListOf<Pair<LambdaExpression, Boolean>>()
    var limit: Int? = null
    var offset: Int? = null
    var distinct = false

    while (current !is QueryRootExpression) {
        when (current) {
            is FilterExpression   -> { filter = current; current = current.source }
            is ProjectExpression  -> { project = current; current = current.source }
            is OrderByExpression  -> { orderBys.add(0, current.keySelector to current.descending); current = current.source }
            is ThenByExpression   -> { orderBys.add(0, current.keySelector to current.descending); current = current.source }
            is LimitExpression    -> { limit = current.count; current = current.source }
            is OffsetExpression   -> { offset = current.count; current = current.source }
            is DistinctExpression -> { distinct = true; current = current.source }
            else -> throw IllegalArgumentException("Unexpected expression in query chain: $current")
        }
    }
    return QueryChain(current as QueryRootExpression, filter, project, orderBys, limit, offset, distinct)
}

class ZekoTranslator(private val dialect: SqlDialect = SqlDialect.H2) {

    private fun paramMarker(index: Int): String = when (dialect) {
        SqlDialect.H2       -> "?"
        SqlDialect.POSTGRES -> "\$$index"
    }

    fun translate(expr: Expression, model: EntityModel<*>): BoundSql = when (expr) {
        is AggregateExpression -> translateAggregate(expr, model)
        is AnyExpression       -> translateExists(expr.source, expr.predicate, negated = false, model)
        is AllExpression       -> translateExists(expr.source, expr.predicate, negated = true, model)
        else                   -> translateSelect(expr, model)
    }

    private fun translateSelect(expr: Expression, model: EntityModel<*>): BoundSql {
        val chain = unwrapChain(expr)
        val params = mutableListOf<Any?>()
        val idx = AtomicInteger(0)

        val selectFields = if (chain.project != null) {
            arrayOf(q(colName(chain.project.selector.body, model)))
        } else {
            model.columns.map { q(it.columnName) }.toTypedArray()
        }

        var query = ANSIQuery().fields(*selectFields).from(model.tableName)
        chain.filter?.let { query = query.where(buildCond(it.predicate.body, model, params, idx)) }
        chain.orderBys.forEach { (sel, desc) ->
            val col = q(colName(sel.body, model))
            query = if (desc) query.orderDesc(col) else query.order(col)
        }
        if (chain.limit != null || chain.offset != null) {
            query = query.limit(chain.limit ?: Int.MAX_VALUE, chain.offset ?: 0)
        }

        var sql = query.toSql()
        if (chain.distinct) sql = sql.replaceFirst("SELECT ", "SELECT DISTINCT ")
        return BoundSql(sql, params)
    }

    private fun translateAggregate(expr: AggregateExpression, model: EntityModel<*>): BoundSql {
        val chain = unwrapChain(expr.source)
        val params = mutableListOf<Any?>()
        val idx = AtomicInteger(0)
        val aggField = when (expr.op) {
            AggregateOp.COUNT -> "COUNT(*)"
            AggregateOp.SUM   -> "SUM(${q(colName(expr.selector!!.body, model))})"
            AggregateOp.MIN   -> "MIN(${q(colName(expr.selector!!.body, model))})"
            AggregateOp.MAX   -> "MAX(${q(colName(expr.selector!!.body, model))})"
            AggregateOp.AVG   -> "AVG(${q(colName(expr.selector!!.body, model))})"
        }
        var query = ANSIQuery().fields(aggField).from(model.tableName)
        chain.filter?.let { query = query.where(buildCond(it.predicate.body, model, params, idx)) }
        return BoundSql(query.toSql(), params)
    }

    fun translateDelete(predicate: LambdaExpression, model: EntityModel<*>): BoundSql {
        val params = mutableListOf<Any?>()
        val idx = AtomicInteger(0)
        val cond = buildCond(predicate.body, model, params, idx)
        return BoundSql("DELETE FROM ${q(model.tableName)} WHERE $cond", params)
    }

    private fun translateExists(
        source: Expression,
        predicate: LambdaExpression,
        negated: Boolean,
        model: EntityModel<*>
    ): BoundSql {
        val params = mutableListOf<Any?>()
        val idx = AtomicInteger(0)
        var cond = buildCond(predicate.body, model, params, idx)
        if (negated) cond = "NOT ($cond)"
        val sql = ANSIQuery().fields("1").from(model.tableName).where(cond).limit(1).toSql()
        return BoundSql(sql, params)
    }

    private fun buildCond(
        expr: Expression,
        model: EntityModel<*>,
        params: MutableList<Any?>,
        idx: AtomicInteger
    ): String = when (expr) {
        is BinaryExpression -> {
            val op = when (expr.op) {
                BinaryOp.AND -> "AND"
                BinaryOp.OR  -> "OR"
                BinaryOp.GT  -> ">"
                BinaryOp.GE  -> ">="
                BinaryOp.LT  -> "<"
                BinaryOp.LE  -> "<="
                BinaryOp.EQ  -> "="
                BinaryOp.NE  -> "<>"
            }
            if (expr.op == BinaryOp.AND || expr.op == BinaryOp.OR) {
                "(${buildCond(expr.left, model, params, idx)} $op ${buildCond(expr.right, model, params, idx)})"
            } else {
                params.add((expr.right as ConstantExpression).value)
                "${q(colName(expr.left, model))} $op ${paramMarker(idx.incrementAndGet())}"
            }
        }
        is StringCallExpression -> {
            val col = q(colName(expr.target, model))
            val raw = expr.arg.value as String
            val pattern = when (expr.op) {
                StringOp.STARTS_WITH -> "$raw%"
                StringOp.ENDS_WITH   -> "%$raw"
                StringOp.CONTAINS    -> "%$raw%"
            }
            params.add(pattern)
            "$col LIKE ${paramMarker(idx.incrementAndGet())}"
        }
        is IsNullExpression    -> "${q(colName(expr.property, model))} IS NULL"
        is IsNotNullExpression -> "${q(colName(expr.property, model))} IS NOT NULL"
        is InListExpression -> {
            if (expr.values.isEmpty()) {
                "1 = 0"
            } else {
                val markers = expr.values.map { v ->
                    params.add(v)
                    paramMarker(idx.incrementAndGet())
                }
                "${q(colName(expr.target, model))} IN (${markers.joinToString(", ")})"
            }
        }
        is UnaryExpression     -> "NOT (${buildCond(expr.operand, model, params, idx)})"
        else -> throw IllegalArgumentException("Cannot convert $expr to SQL condition")
    }

    private fun colName(expr: Expression, model: EntityModel<*>): String {
        require(expr is PropertyExpression) { "Expected PropertyExpression, got $expr" }
        return model.columns.first { it.property == expr.property }.columnName
    }

    private fun q(name: String) = "\"$name\""
}
