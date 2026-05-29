package dev.efkore.tracking

import kotlin.reflect.KClass

enum class EntityState { Unchanged, Added, Modified, Deleted }

data class EntityEntry<T>(
    val entity: T,
    val state: EntityState,
    val snapshot: Map<String, Any?>
)

class ChangeTracker<T : Any>(
    private val entityType: KClass<T>
) {
    private val entries = mutableListOf<EntityEntry<T>>()

    fun track(entity: T, snapshot: Map<String, Any?>) {
        val existing = entries.indexOfFirst { it.entity === entity }
        if (existing >= 0) {
            entries[existing] = EntityEntry(entity, EntityState.Modified, snapshot)
        } else {
            entries.add(EntityEntry(entity, EntityState.Unchanged, snapshot))
        }
    }

    fun add(entity: T) {
        val snapshot = takeSnapshot(entity)
        entries.add(EntityEntry(entity, EntityState.Added, snapshot))
    }

    fun remove(entity: T) {
        val idx = entries.indexOfFirst { it.entity === entity }
        if (idx >= 0) {
            entries[idx] = entries[idx].copy(state = EntityState.Deleted)
        }
    }

    fun getChanges(): List<EntityEntry<T>> =
        entries.filter { it.state != EntityState.Unchanged }

    private fun takeSnapshot(entity: T): Map<String, Any?> {
        val map = mutableMapOf<String, Any?>()
        entityType.java.methods
            .filter { it.name.startsWith("get") && it.parameterCount == 0 && it.declaringClass != Any::class.java }
            .forEach { method ->
                val propName = method.name.removePrefix("get")
                    .replaceFirstChar { it.lowercase() }
                map[propName] = method.invoke(entity)
            }
        return map
    }
}
