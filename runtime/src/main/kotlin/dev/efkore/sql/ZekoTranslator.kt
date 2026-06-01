package dev.efkore.sql

import dev.efkore.expressions.*
import dev.efkore.metadata.EntityModel
import kotlin.reflect.KMutableProperty1

data class SqlAndParams(val sql: String, val params: List<Any?>)

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
            is FilterExpression -> { filter = current; current = current.source }
            is ProjectExpression -> { project = current; current = current.source }
            is OrderByExpression -> { orderBys.add(0, current.keySelector to current.descending); current = current.source }
            is ThenByExpression -> { orderBys.add(0, current.keySelector to current.descending); current = current.source }
            is LimitExpression -> { limit = current.count; current = current.source }
            is OffsetExpression -> { offset = current.count; current = current.source }
            is DistinctExpression -> { distinct = true; current = current.source }
            else -> throw IllegalArgumentException("Unexpected expression in query chain: $current")
        }
    }
    return QueryChain(current, filter, project, orderBys, limit, offset, distinct)
}

class ZekoTranslator {

    fun translate(root: Expression): SqlAndParams {
        return when (root) {
            is CountExpression -> translateAgg("COUNT(*)", root.source)
            is SumExpression -> translateAgg("SUM(${columnName(root.selector.body)})", root.source)
            is AvgExpression -> translateAgg("AVG(${columnName(root.selector.body)})", root.source)
            is MinExpression -> translateAgg("MIN(${columnName(root.selector.body)})", root.source)
            is MaxExpression -> translateAgg("MAX(${columnName(root.selector.body)})", root.source)
            is FindExpression -> translateFind(root)
            else -> translateChain(root)
        }
    }

    private fun translateAgg(aggExpr: String, source: Expression): SqlAndParams {
        val params = mutableListOf<Any?>()
        val chain = unwrapChain(source)
        val table = tableName(chain.root.entityType)
        val sb = StringBuilder("SELECT $aggExpr FROM \"$table\"")
        chain.filter?.let {
            sb.append(" WHERE ")
            sb.append(buildCondition(it.predicate.body, params))
        }
        return SqlAndParams(sb.toString(), params)
    }

    private fun translateFind(expr: FindExpression): SqlAndParams {
        val params = mutableListOf<Any?>()
        val table = tableName(expr.entityType)
        val wheres = expr.keyValues.entries.joinToString(" AND ") { (key, value) ->
            params.add(value)
            "\"${key.lowercase()}\" = ?"
        }
        return SqlAndParams("SELECT * FROM \"$table\" WHERE $wheres", params)
    }

    private fun translateChain(root: Expression): SqlAndParams {
        val chain = unwrapChain(root)
        val params = mutableListOf<Any?>()
        val table = tableName(chain.root.entityType)

        val select = if (chain.project != null) {
            columnName(chain.project.selector.body)
        } else {
            "*"
        }

        val distinct = if (chain.distinct) "DISTINCT " else ""

        val sb = StringBuilder()
        sb.append("SELECT ${distinct}$select FROM \"$table\"")

        chain.filter?.let {
            sb.append(" WHERE ")
            sb.append(buildCondition(it.predicate.body, params))
        }

        if (chain.orderBys.isNotEmpty()) {
            sb.append(" ORDER BY ")
            sb.append(chain.orderBys.joinToString(", ") { (sel, desc) ->
                val col = columnName(sel.body)
                if (desc) "$col DESC" else col
            })
        }

        if (chain.limit != null) {
            sb.append(" LIMIT ${chain.limit}")
        }
        if (chain.offset != null) {
            sb.append(" OFFSET ${chain.offset}")
        }

        return SqlAndParams(sb.toString(), params)
    }

    private fun buildCondition(expr: Expression, params: MutableList<Any?>): String = when (expr) {
        is BinaryExpression -> {
            val op = when (expr.op) {
                BinaryOp.AND -> "AND"
                BinaryOp.OR -> "OR"
                BinaryOp.GT -> ">"
                BinaryOp.GE -> ">="
                BinaryOp.LT -> "<"
                BinaryOp.LE -> "<="
                BinaryOp.EQ -> "="
                BinaryOp.NE -> "<>"
            }
            if (expr.op == BinaryOp.AND || expr.op == BinaryOp.OR) {
                "(${buildCondition(expr.left, params)} $op ${buildCondition(expr.right, params)})"
            } else {
                params.add((expr.right as ConstantExpression).value)
                "${columnName(expr.left)} $op ?"
            }
        }
        is UnaryExpression -> "NOT (${buildCondition(expr.operand, params)})"
        is StartsWithExpression -> {
            params.add("${expr.value.value}%")
            "${columnName(expr.source)} LIKE ?"
        }
        is ContainsExpression -> {
            params.add("%${expr.value.value}%")
            "${columnName(expr.source)} LIKE ?"
        }
        is EndsWithExpression -> {
            params.add("%${expr.value.value}")
            "${columnName(expr.source)} LIKE ?"
        }
        else -> throw IllegalArgumentException("Unsupported expression in condition: $expr")
    }

    fun translateInsert(entity: Any, model: EntityModel<*>, params: MutableList<Any?>): String {
        val insertCols = model.columns.filter { !it.isGenerated }
        val colNames = insertCols.joinToString(", ") { "\"${it.columnName}\"" }
        val placeholders = insertCols.joinToString(", ") { "?" }
        insertCols.forEach { col ->
            @Suppress("UNCHECKED_CAST")
            (col.property as KMutableProperty1<Any, *>).get(entity).let { params.add(it) }
        }
        return "INSERT INTO \"${model.tableName}\" ($colNames) VALUES ($placeholders)"
    }

    fun translateUpdate(entity: Any, model: EntityModel<*>, params: MutableList<Any?>): String {
        val setCols = model.columns.filter { !it.isKey }
        val sets = setCols.joinToString(", ") { "\"${it.columnName}\" = ?" }
        setCols.forEach { col ->
            @Suppress("UNCHECKED_CAST")
            (col.property as KMutableProperty1<Any, *>).get(entity).let { params.add(it) }
        }
        val keyCol = model.keyColumn
        @Suppress("UNCHECKED_CAST")
        (keyCol.property as KMutableProperty1<Any, *>).get(entity).let { params.add(it) }
        return "UPDATE \"${model.tableName}\" SET $sets WHERE \"${keyCol.columnName}\" = ?"
    }

    fun translateDelete(entity: Any, model: EntityModel<*>, params: MutableList<Any?>): String {
        val keyCol = model.keyColumn
        @Suppress("UNCHECKED_CAST")
        (keyCol.property as KMutableProperty1<Any, *>).get(entity).let { params.add(it) }
        return "DELETE FROM \"${model.tableName}\" WHERE \"${keyCol.columnName}\" = ?"
    }

    private fun columnName(expr: Expression): String {
        require(expr is PropertyExpression) { "Expected PropertyExpression, got $expr" }
        return "\"${expr.property.name.lowercase()}\""
    }

    private fun tableName(entityType: kotlin.reflect.KClass<*>): String {
        return entityType.simpleName?.lowercase() + "s"
    }
}
