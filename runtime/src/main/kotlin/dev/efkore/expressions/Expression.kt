package dev.efkore.expressions

import kotlin.reflect.KClass
import kotlin.reflect.KProperty1

enum class BinaryOp { GT, GE, LT, LE, EQ, NE, AND, OR }

sealed class Expression

data class ParameterExpression(val name: String, val type: KClass<*>) : Expression()
data class PropertyExpression(val property: KProperty1<*, *>) : Expression()
data class ConstantExpression(val value: Any?) : Expression()
data class BinaryExpression(val op: BinaryOp, val left: Expression, val right: Expression) : Expression()
data class UnaryExpression(val operand: Expression) : Expression()  // NOT
data class LambdaExpression(val parameter: ParameterExpression, val body: Expression) : Expression()

// Query nodes
data class QueryRootExpression(val entityType: KClass<*>) : Expression()
data class FilterExpression(val source: Expression, val predicate: LambdaExpression) : Expression()
data class ProjectExpression(val source: Expression, val selector: LambdaExpression) : Expression()
data class OrderByExpression(val source: Expression, val keySelector: LambdaExpression, val descending: Boolean) : Expression()
data class LimitExpression(val source: Expression, val count: Int) : Expression()
data class OffsetExpression(val source: Expression, val count: Int) : Expression()
data class DistinctExpression(val source: Expression) : Expression()

enum class AggregateOp { COUNT, SUM, MIN, MAX, AVG }
data class AggregateExpression(
    val op: AggregateOp,
    val source: Expression,
    val selector: LambdaExpression?
) : Expression()

data class AnyExpression(val source: Expression, val predicate: LambdaExpression) : Expression()
data class AllExpression(val source: Expression, val predicate: LambdaExpression) : Expression()

data class ThenByExpression(
    val source: Expression,
    val keySelector: LambdaExpression,
    val descending: Boolean
) : Expression()

enum class StringOp { STARTS_WITH, ENDS_WITH, CONTAINS }
data class StringCallExpression(
    val op: StringOp,
    val target: PropertyExpression,
    val arg: ConstantExpression
) : Expression()

data class IsNullExpression(val property: PropertyExpression) : Expression()
data class IsNotNullExpression(val property: PropertyExpression) : Expression()

// `it.prop in listOf(...)` — values resolved at runtime, one bind param per element
data class InListExpression(val target: PropertyExpression, val values: Collection<Any?>) : Expression()

// Thin builders referenced by the compiler plugin to emit string/null predicate nodes
fun stringStartsWith(target: PropertyExpression, arg: ConstantExpression): StringCallExpression =
    StringCallExpression(StringOp.STARTS_WITH, target, arg)
fun stringEndsWith(target: PropertyExpression, arg: ConstantExpression): StringCallExpression =
    StringCallExpression(StringOp.ENDS_WITH, target, arg)
fun stringContains(target: PropertyExpression, arg: ConstantExpression): StringCallExpression =
    StringCallExpression(StringOp.CONTAINS, target, arg)
fun isNullPred(property: PropertyExpression): IsNullExpression = IsNullExpression(property)
fun isNotNullPred(property: PropertyExpression): IsNotNullExpression = IsNotNullExpression(property)
fun inListPred(target: PropertyExpression, values: Collection<Any?>): InListExpression =
    InListExpression(target, values)
