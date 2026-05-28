package dev.efkore.metadata

import kotlin.reflect.KClass
import kotlin.reflect.KMutableProperty1
import kotlin.reflect.full.findAnnotation
import kotlin.reflect.full.memberProperties

data class ColumnInfo(
    val property: KMutableProperty1<*, *>,
    val columnName: String,
    val isKey: Boolean,
    val isGenerated: Boolean
)

class EntityModel<T : Any>(val entityClass: KClass<T>) {
    val tableName: String = run {
        entityClass.findAnnotation<Table>()?.name?.takeIf { it.isNotEmpty() }
            ?: entityClass.simpleName!!.lowercase() + "s"
    }

    val columns: List<ColumnInfo> = entityClass.memberProperties
        .filterIsInstance<KMutableProperty1<*, *>>()
        .map { prop ->
            val colAnn = prop.findAnnotation<Column>()
            val colName = colAnn?.name?.takeIf { it.isNotEmpty() } ?: prop.name.lowercase()
            ColumnInfo(
                property = prop,
                columnName = colName,
                isKey = prop.findAnnotation<Id>() != null,
                isGenerated = prop.findAnnotation<GeneratedValue>() != null
            )
        }

    val keyColumn: ColumnInfo = columns.first { it.isKey }

    val nonKeyColumns: List<ColumnInfo> = columns.filter { !it.isKey }

    fun createDdl(): String {
        val cols = columns.joinToString(",\n  ") { col ->
            val type = when (col.property.returnType.classifier) {
                Int::class -> if (col.isGenerated) "INT AUTO_INCREMENT" else "INT"
                Long::class -> if (col.isGenerated) "BIGINT AUTO_INCREMENT" else "BIGINT"
                String::class -> "VARCHAR(255)"
                Double::class -> "DOUBLE"
                Boolean::class -> "BOOLEAN"
                else -> "VARCHAR(255)"
            }
            val pk = if (col.isKey) " PRIMARY KEY" else ""
            "\"${col.columnName}\" $type$pk"
        }
        return "CREATE TABLE IF NOT EXISTS \"${tableName}\" (\n  $cols\n)"
    }

    companion object {
        inline fun <reified T : Any> resolve(): EntityModel<T> = EntityModel(T::class)
    }
}
