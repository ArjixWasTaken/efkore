package dev.efkore.metadata

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Test

@Entity
@Table("widgets")
class Widget {
    @Id @GeneratedValue var id: Int = 0
    @Column("name") var name: String = ""
    @Ignore var cached: String = ""
    @Transient var temp: Int = 0
}

class EntityModelTest {

    private val model = EntityModel(Widget::class)

    @Test
    fun `ignored and transient properties are excluded from columns`() {
        assertEquals(setOf("id", "name"), model.columns.map { it.columnName }.toSet())
    }

    @Test
    fun `ddl excludes ignored properties`() {
        val ddl = model.createDdl()
        assertFalse(ddl.contains("cached")) { "DDL: $ddl" }
        assertFalse(ddl.contains("temp")) { "DDL: $ddl" }
    }
}
