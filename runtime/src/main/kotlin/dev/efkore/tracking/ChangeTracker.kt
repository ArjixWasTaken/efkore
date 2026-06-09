package dev.efkore.tracking

import dev.efkore.metadata.EntityModel
import kotlin.reflect.KClass
import kotlin.reflect.KMutableProperty1

enum class EntityState { Added, Modified, Deleted, Unchanged, Detached }

class EntityEntry(
    val entity: Any,
    val entityType: KClass<*>,
    var state: EntityState,
    val snapshot: MutableMap<String, Any?> = mutableMapOf()
)

class ChangeTracker {
    private val entries = mutableListOf<EntityEntry>()

    fun <T : Any> track(entity: T, type: KClass<T>, state: EntityState) {
        val existing = entries.find { it.entity === entity }
        if (existing != null) {
            existing.state = state
        } else {
            entries.add(EntityEntry(entity, type, state))
        }
    }

    fun <T : Any> findById(type: KClass<T>, id: Any, model: EntityModel<*>): T? {
        @Suppress("UNCHECKED_CAST")
        return entries
            .filter { it.entityType == type && it.state != EntityState.Deleted && it.state != EntityState.Detached }
            .map { it.entity as T }
            .firstOrNull { entity ->
                val pkProp = model.keyColumn.property as KMutableProperty1<T, *>
                pkProp.get(entity) == id
            }
    }

    fun pendingChanges(): List<EntityEntry> = entries.filter { it.state != EntityState.Unchanged && it.state != EntityState.Detached }

    fun resetAfterSave(model: EntityModel<*>) {
        val toRemove = entries.filter { it.state == EntityState.Deleted }
        entries.removeAll(toRemove)
        entries.filter { it.state == EntityState.Added || it.state == EntityState.Modified }.forEach { entry ->
            entry.state = EntityState.Unchanged
            val m = model as EntityModel<Any>
            m.columns.forEach { col ->
                @Suppress("UNCHECKED_CAST")
                entry.snapshot[col.columnName] = (col.property as KMutableProperty1<Any, *>).get(entry.entity)
            }
        }
    }

    fun changedColumns(entry: EntityEntry, model: EntityModel<*>): List<dev.efkore.metadata.ColumnInfo> {
        @Suppress("UNCHECKED_CAST")
        val m = model as EntityModel<Any>
        return m.columns.filter { col ->
            if (col.isKey) return@filter false
            val current = (col.property as KMutableProperty1<Any, *>).get(entry.entity)
            entry.snapshot[col.columnName] != current
        }
    }
}
