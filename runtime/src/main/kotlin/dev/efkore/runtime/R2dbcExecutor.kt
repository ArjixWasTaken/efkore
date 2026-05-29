package dev.efkore.runtime

import io.r2dbc.spi.ConnectionFactory
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.reactive.asFlow
import kotlinx.coroutines.reactive.awaitFirst
import kotlinx.coroutines.reactive.awaitFirstOrNull
import org.slf4j.LoggerFactory

class R2dbcExecutor(
    private val connectionFactory: ConnectionFactory
) {
    private val log = LoggerFactory.getLogger(R2dbcExecutor::class.java)

    suspend fun execute(sql: String, params: List<Any?>): List<Map<String, Any?>> {
        log.debug("SQL: {} | params: {}", sql, params)
        val conn = connectionFactory.create().awaitFirst()
        return try {
            val stmt = conn.createStatement(sql)
            params.forEachIndexed { i, v ->
                if (v != null) stmt.bind(i, v)
                else stmt.bindNull(i, Any::class.java)
            }
            stmt.execute().asFlow()
                .map { result ->
                    result.map { row, meta ->
                        meta.columnMetadatas.associate { md ->
                            md.name to row.get(md.name)
                        }
                    }.asFlow().toList()
                }
                .toList()
                .flatten()
        } finally {
            conn.close().awaitFirstOrNull()
        }
    }
}
