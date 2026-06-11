package dev.efkore.runtime

import dev.efkore.expressions.AllExpression
import dev.efkore.expressions.Expression
import dev.efkore.expressions.LambdaExpression
import dev.efkore.metadata.EntityModel
import dev.efkore.sql.SqlDialect
import dev.efkore.sql.BoundSql
import dev.efkore.sql.ZekoTranslator
import io.r2dbc.spi.Connection
import io.r2dbc.spi.ConnectionFactory
import io.r2dbc.spi.Result
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.reactive.asFlow
import kotlinx.coroutines.reactive.awaitFirst
import kotlinx.coroutines.reactive.awaitFirstOrNull
import org.slf4j.LoggerFactory
import kotlin.reflect.KClass

class R2dbcExecutor(
    private val connectionFactory: ConnectionFactory,
    dialect: SqlDialect = SqlDialect.H2
) {
    private val translator = ZekoTranslator(dialect)
    private val log = LoggerFactory.getLogger(R2dbcExecutor::class.java)

    suspend fun <T : Any> execute(expr: Expression, entityType: KClass<T>, model: EntityModel<*>): List<T> {
        val bound = translator.translate(expr, model)
        log.debug("SQL: {} | params: {}", bound.sql, bound.params)
        return executeQuery(bound, entityType, model)
    }

    suspend fun <T : Any> executeRawQuery(sql: String, params: List<Any?>, entityType: KClass<T>, model: EntityModel<*>): List<T> {
        log.debug("RAW SQL: {} | params: {}", sql, params)
        return executeQuery(BoundSql(sql, params), entityType, model)
    }

    private suspend fun <T : Any> executeQuery(bound: BoundSql, entityType: KClass<T>, model: EntityModel<*>): List<T> {
        val conn = connectionFactory.create().awaitFirst()
        return try {
            val stmt = conn.createStatement(bound.sql)
            bound.params.forEachIndexed { i, v -> stmt.bind(i, v!!) }
            stmt.execute().asFlow()
                .map { result -> result.map { row, meta -> Materializer.materialize(row, meta, entityType, model) } }
                .map { pub -> pub.asFlow().toList() }
                .toList()
                .flatten()
        } finally {
            conn.close().awaitFirstOrNull()
        }
    }

    suspend fun executeDelete(predicate: LambdaExpression, model: EntityModel<*>): Long {
        val bound = translator.translateDelete(predicate, model)
        return executeUpdate(bound.sql, bound.params)
    }

    suspend fun executeUpdate(sql: String, params: List<Any?>): Long {
        log.debug("DML SQL: {} | params: {}", sql, params)
        return withConnection { conn ->
            val stmt = conn.createStatement(sql)
            params.forEachIndexed { i, v -> stmt.bind(i, v!!) }
            stmt.execute().asFlow()
                .map { result -> result.rowsUpdated.asFlow().toList().sum() }
                .toList()
                .sum()
        }
    }

    @Suppress("UNCHECKED_CAST")
    suspend fun <T : Any> executeScalar(expr: Expression, resultType: KClass<T>, model: EntityModel<*>): T {
        val bound = translator.translate(expr, model)
        log.debug("SCALAR SQL: {} | params: {}", bound.sql, bound.params)
        val conn = connectionFactory.create().awaitFirst()
        return try {
            val stmt = conn.createStatement(bound.sql)
            bound.params.forEachIndexed { i, v -> stmt.bind(i, v!!) }
            val raw = stmt.execute().asFlow()
                .map { result -> result.map { row, _ -> row.get(0) } }
                .map { pub -> pub.asFlow().toList() }
                .toList().flatten().first()
            coerceScalar(raw, resultType) as T
        } finally {
            conn.close().awaitFirstOrNull()
        }
    }

    private fun coerceScalar(value: Any?, target: KClass<*>): Any? = when {
        value == null -> null
        target == Long::class   -> (value as Number).toLong()
        target == Int::class    -> (value as Number).toInt()
        target == Double::class -> (value as Number).toDouble()
        target == Float::class  -> (value as Number).toFloat()
        else -> value
    }

    // SQL is SELECT 1 FROM tbl WHERE cond LIMIT 1.
    // AnyExpression: rows.isNotEmpty() (any matching row found)
    // AllExpression: rows.isEmpty()    (no row violates the predicate)
    suspend fun executeExists(expr: Expression, model: EntityModel<*>): Boolean {
        val bound = translator.translate(expr, model)
        log.debug("EXISTS SQL: {} | params: {}", bound.sql, bound.params)
        val conn = connectionFactory.create().awaitFirst()
        return try {
            val stmt = conn.createStatement(bound.sql)
            bound.params.forEachIndexed { i, v -> stmt.bind(i, v!!) }
            val rows = stmt.execute().asFlow()
                .map { result -> result.map { row, _ -> row.get(0, Int::class.javaObjectType) } }
                .map { pub -> pub.asFlow().toList() }
                .toList().flatten()
            when (expr) {
                is AllExpression -> rows.isEmpty()
                else             -> rows.isNotEmpty()
            }
        } finally {
            conn.close().awaitFirstOrNull()
        }
    }

    fun toSql(expr: Expression, model: EntityModel<*>): BoundSql = translator.translate(expr, model)

    suspend fun executeRaw(sql: String, conn: Connection): Result {
        return conn.createStatement(sql).execute().awaitFirst()
    }

    suspend fun <T> withConnection(block: suspend (Connection) -> T): T {
        val conn = connectionFactory.create().awaitFirst()
        return try {
            block(conn)
        } finally {
            conn.close().awaitFirstOrNull()
        }
    }
}
