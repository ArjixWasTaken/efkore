package dev.efkore.sql

import dev.efkore.expressions.*
import dev.efkore.metadata.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

@Entity
@Table("blog")
class TestBlog {
    @Id @GeneratedValue var id: Int = 0
    @Column("url") var url: String = ""
    @Column("rating") var rating: Int = 0
}

class ZekoTranslatorTest {

    private val model = EntityModel(TestBlog::class)
    private val translator = ZekoTranslator()
    private val postgresTranslator = ZekoTranslator(SqlDialect.POSTGRES)

    private val ratingProp get() = model.columns.first { it.columnName == "rating" }
    private val urlProp    get() = model.columns.first { it.columnName == "url" }

    private val root = QueryRootExpression(TestBlog::class)
    private val param = paramExpr("it", TestBlog::class)

    @Test
    fun `simple filter generates correct SQL and params`() {
        val pred = gt(property(ratingProp.property), constant(3))
        val filter = FilterExpression(root, lambdaExpr(param, pred))

        val bound = translator.translate(filter, model)
        assertTrue(bound.sql.contains("""WHERE "rating" > ?""")) { "SQL: ${bound.sql}" }
        assertEquals(listOf(3), bound.params)
    }

    @Test
    fun `multi-column sort generates ORDER BY in declaration order`() {
        val ratingLambda = lambdaExpr(param, property(ratingProp.property))
        val urlLambda    = lambdaExpr(param, property(urlProp.property))

        val orderBy = OrderByExpression(root, ratingLambda, false)
        val thenBy  = ThenByExpression(orderBy, urlLambda, false)

        val bound = translator.translate(thenBy, model)
        assertTrue(bound.sql.contains("""ORDER BY "rating" ASC, "url" ASC""")) { "SQL: ${bound.sql}" }
        assertTrue(bound.params.isEmpty())
    }

    @Test
    fun `string startsWith generates LIKE with trailing wildcard`() {
        val pred = StringCallExpression(StringOp.STARTS_WITH, PropertyExpression(urlProp.property), ConstantExpression("https://high"))
        val filter = FilterExpression(root, lambdaExpr(param, pred))

        val bound = translator.translate(filter, model)
        assertTrue(bound.sql.contains("""WHERE "url" LIKE ?""")) { "SQL: ${bound.sql}" }
        assertEquals(listOf("https://high%"), bound.params)
    }

    @Test
    fun `string contains generates LIKE with surrounding wildcards`() {
        val pred = StringCallExpression(StringOp.CONTAINS, PropertyExpression(urlProp.property), ConstantExpression("high"))
        val filter = FilterExpression(root, lambdaExpr(param, pred))

        val bound = translator.translate(filter, model)
        assertTrue(bound.sql.contains("""WHERE "url" LIKE ?""")) { "SQL: ${bound.sql}" }
        assertEquals(listOf("%high%"), bound.params)
    }

    @Test
    fun `null predicate generates IS NULL without params`() {
        val pred = IsNullExpression(PropertyExpression(urlProp.property))
        val filter = FilterExpression(root, lambdaExpr(param, pred))

        val bound = translator.translate(filter, model)
        assertTrue(bound.sql.contains("""WHERE "url" IS NULL""")) { "SQL: ${bound.sql}" }
        assertTrue(bound.params.isEmpty())
    }

    @Test
    fun `aggregate COUNT generates COUNT(*)`() {
        val countExpr = AggregateExpression(AggregateOp.COUNT, root, null)

        val bound = translator.translate(countExpr, model)
        assertTrue(bound.sql.contains("COUNT(*)")) { "SQL: ${bound.sql}" }
        assertTrue(bound.params.isEmpty())
    }

    @Test
    fun `any generates SELECT 1 FROM tbl WHERE cond LIMIT 1`() {
        val pred   = gt(property(ratingProp.property), constant(3))
        val anyExpr = AnyExpression(root, lambdaExpr(param, pred))

        val bound = translator.translate(anyExpr, model)
        assertTrue(bound.sql.contains("SELECT 1")) { "SQL: ${bound.sql}" }
        assertTrue(bound.sql.contains("""WHERE "rating" > ?""")) { "SQL: ${bound.sql}" }
        assertEquals(listOf(3), bound.params)
    }

    @Test
    fun `all generates SELECT 1 FROM tbl WHERE NOT cond LIMIT 1`() {
        val pred    = gt(property(ratingProp.property), constant(3))
        val allExpr = AllExpression(root, lambdaExpr(param, pred))

        val bound = translator.translate(allExpr, model)
        assertTrue(bound.sql.contains("SELECT 1")) { "SQL: ${bound.sql}" }
        assertTrue(bound.sql.contains("NOT")) { "SQL: ${bound.sql}" }
        assertEquals(listOf(3), bound.params)
    }

    @Test
    fun `postgres dialect uses dollar-N markers`() {
        val pred   = gt(property(ratingProp.property), constant(3))
        val filter = FilterExpression(root, lambdaExpr(param, pred))

        val bound = postgresTranslator.translate(filter, model)
        assertTrue(bound.sql.contains("> \$1")) { "SQL: ${bound.sql}" }
        assertFalse(bound.sql.contains("> ?")) { "SQL should not contain H2 marker: ${bound.sql}" }
        assertEquals(listOf(3), bound.params)
    }

    @Test
    fun `compound AND condition param order matches marker order`() {
        val pred = and(
            gt(property(ratingProp.property), constant(3)),
            eq(property(urlProp.property), constant("x"))
        )
        val filter = FilterExpression(root, lambdaExpr(param, pred))

        val bound = postgresTranslator.translate(filter, model)
        assertEquals(listOf(3, "x"), bound.params) { "Params out of order: ${bound.params}" }
        assertTrue(bound.sql.contains("> \$1")) { "SQL: ${bound.sql}" }
        assertTrue(bound.sql.contains("= \$2")) { "SQL: ${bound.sql}" }
    }
}
