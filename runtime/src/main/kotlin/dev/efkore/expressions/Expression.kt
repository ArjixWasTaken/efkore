package dev.efkore.expressions

import kotlin.reflect.KClass
import kotlin.reflect.KProperty1

enum class BinaryOp { GT, GE, LT, LE, EQ, NE, AND, OR }

sealed class Expression

data class ParameterExpression(val name: String, val type: KClass<*>) : Expression()
data class PropertyExpression(val property: KProperty1<*, *>) : Expression()
data class ConstantExpression(val value: Any?) : Expression()
data class BinaryExpression(val op: BinaryOp, val left: Expression, val right: Expression) : Expression()
data class UnaryExpression(val operand: Expression) : Expression()
data class LambdaExpression(val parameter: ParameterExpression, val body: Expression) : Expression()

data class QueryRootExpression(val entityType: KClass<*>) : Expression()
data class FilterExpression(val source: Expression, val predicate: LambdaExpression) : Expression()
data class ProjectExpression(val source: Expression, val selector: LambdaExpression) : Expression()
data class OrderByExpression(val source: Expression, val keySelector: LambdaExpression, val descending: Boolean) : Expression()
data class LimitExpression(val source: Expression, val count: Int) : Expression()
data class OffsetExpression(val source: Expression, val count: Int) : Expression()
data class DistinctExpression(val source: Expression) : Expression()

data class CountExpression(val source: Expression) : Expression()
data class SumExpression(val source: Expression, val selector: LambdaExpression) : Expression()
data class AvgExpression(val source: Expression, val selector: LambdaExpression) : Expression()
data class MinExpression(val source: Expression, val selector: LambdaExpression) : Expression()
data class MaxExpression(val source: Expression, val selector: LambdaExpression) : Expression()
data class StartsWithExpression(val source: Expression, val value: ConstantExpression) : Expression()
data class ContainsExpression(val source: Expression, val value: ConstantExpression) : Expression()
data class EndsWithExpression(val source: Expression, val value: ConstantExpression) : Expression()
data class ThenByExpression(val source: Expression, val keySelector: LambdaExpression, val descending: Boolean) : Expression()
data class FindExpression(val entityType: KClass<*>, val keyValues: Map<String, Any?>) : Expression()

data class AnyExpression(val source: Expression, val predicate: LambdaExpression) : Expression()
data class AllExpression(val source: Expression, val predicate: LambdaExpression) : Expression()
data class IsNullExpression(val property: Expression) : Expression()
data class IsNotNullExpression(val property: Expression) : Expression()
