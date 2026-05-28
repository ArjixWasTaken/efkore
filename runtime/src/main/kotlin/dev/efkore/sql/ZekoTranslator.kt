package dev.efkore.sql

import dev.efkore.expressions.*

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
        else -> throw IllegalArgumentException("Unsupported expression in condition: $expr")
    }

    private fun columnName(expr: Expression): String {
        require(expr is PropertyExpression) { "Expected PropertyExpression, got $expr" }
        return "\"${expr.property.name.lowercase()}\""
    }

    private fun tableName(entityType: kotlin.reflect.KClass<*>): String {
        return entityType.simpleName?.lowercase() + "s"
    }
}
