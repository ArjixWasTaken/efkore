package dev.efkore.sql

import dev.efkore.expressions.*
import dev.efkore.metadata.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.assertEquals

@Table(name = "users")
class TestUser(
    @Id @GeneratedValue var id: Int = 0,
    var name: String = "",
    var age: Int = 0,
    var email: String = "",
    var active: Boolean = false,
    var score: Double = 0.0
)

@Table(name = "big_users")
class BigUser(
    @Id @GeneratedValue var id: Long = 0L,
    var name: String = ""
)

class ZekoTranslatorTest {

    // ── Helper factories ──────────────────────────────────────────────────

    private val root = QueryRootExpression(TestUser::class)

    private fun filterExpr(body: Expression) =
        FilterExpression(root, LambdaExpression(ParameterExpression("it", TestUser::class), body))

    private fun orderByExpr(desc: Boolean = false) =
        OrderByExpression(root, LambdaExpression(ParameterExpression("it", TestUser::class), PropertyExpression(TestUser::name)), desc)

    private fun limitExpr(count: Int) = LimitExpression(root, count)
    private fun offsetExpr(count: Int) = OffsetExpression(root, count)

    private val p = PropertyExpression(TestUser::name)
    private val pAge = PropertyExpression(TestUser::age)

    // ── H2 dialect tests ──────────────────────────────────────────────────

    @Test
    fun `select star`() {
        val result = ZekoTranslator(SqlDialect.H2).translate(root)
        assertEquals(SqlAndParams("""SELECT * FROM "testusers"""", emptyList()), result)
    }

    @Test
    fun `where equal`() {
        val result = ZekoTranslator(SqlDialect.H2).translate(filterExpr(eq(p, constant("Alice"))))
        assertEquals(SqlAndParams("""SELECT * FROM "testusers" WHERE "name" = ?""", listOf("Alice")), result)
    }

    @Test
    fun `where comparisons`() {
        val h2 = ZekoTranslator(SqlDialect.H2)

        var r = h2.translate(filterExpr(gt(pAge, constant(18))))
        assertEquals(SqlAndParams("""SELECT * FROM "testusers" WHERE "age" > ?""", listOf(18)), r)

        r = h2.translate(filterExpr(lt(pAge, constant(18))))
        assertEquals(SqlAndParams("""SELECT * FROM "testusers" WHERE "age" < ?""", listOf(18)), r)

        r = h2.translate(filterExpr(ge(pAge, constant(18))))
        assertEquals(SqlAndParams("""SELECT * FROM "testusers" WHERE "age" >= ?""", listOf(18)), r)

        r = h2.translate(filterExpr(le(pAge, constant(18))))
        assertEquals(SqlAndParams("""SELECT * FROM "testusers" WHERE "age" <= ?""", listOf(18)), r)

        r = h2.translate(filterExpr(ne(pAge, constant(18))))
        assertEquals(SqlAndParams("""SELECT * FROM "testusers" WHERE "age" <> ?""", listOf(18)), r)
    }

    @Test
    fun `and or combinations`() {
        val h2 = ZekoTranslator(SqlDialect.H2)
        val nameEq = eq(p, constant("Alice"))
        val ageGt = gt(pAge, constant(18))

        var r = h2.translate(filterExpr(and(nameEq, ageGt)))
        assertEquals(SqlAndParams("""SELECT * FROM "testusers" WHERE ("name" = ? AND "age" > ?)""", listOf("Alice", 18)), r)

        r = h2.translate(filterExpr(or(nameEq, ageGt)))
        assertEquals(SqlAndParams("""SELECT * FROM "testusers" WHERE ("name" = ? OR "age" > ?)""", listOf("Alice", 18)), r)

        r = h2.translate(filterExpr(and(nameEq, or(ageGt, eq(pAge, constant(30))))))
        assertEquals(
            SqlAndParams("""SELECT * FROM "testusers" WHERE ("name" = ? AND ("age" > ? OR "age" = ?))""", listOf("Alice", 18, 30)),
            r
        )
    }

    @Test
    fun `order by`() {
        val h2 = ZekoTranslator(SqlDialect.H2)
        val r = h2.translate(orderByExpr())
        assertEquals(SqlAndParams("""SELECT * FROM "testusers" ORDER BY "name"""", emptyList()), r)
    }

    @Test
    fun `order by descending`() {
        val r = ZekoTranslator(SqlDialect.H2).translate(orderByExpr(desc = true))
        assertEquals(SqlAndParams("""SELECT * FROM "testusers" ORDER BY "name" DESC""", emptyList()), r)
    }

    @Test
    fun `limit`() {
        val r = ZekoTranslator(SqlDialect.H2).translate(limitExpr(10))
        assertEquals(SqlAndParams("""SELECT * FROM "testusers" LIMIT 10""", emptyList()), r)
    }

    @Test
    fun `offset`() {
        val r = ZekoTranslator(SqlDialect.H2).translate(offsetExpr(5))
        assertEquals(SqlAndParams("""SELECT * FROM "testusers" OFFSET 5""", emptyList()), r)
    }

    @Test
    fun `order by limit offset`() {
        val chain = limitExpr(10).let { OffsetExpression(it, 5) }
            .let { OrderByExpression(it, LambdaExpression(ParameterExpression("it", TestUser::class), PropertyExpression(TestUser::name)), false) }
        val r = ZekoTranslator(SqlDialect.H2).translate(chain)
        assertEquals(SqlAndParams("""SELECT * FROM "testusers" ORDER BY "name" LIMIT 10 OFFSET 5""", emptyList()), r)
    }

    @Test
    fun `distinct`() {
        val r = ZekoTranslator(SqlDialect.H2).translate(DistinctExpression(root))
        assertEquals(SqlAndParams("""SELECT DISTINCT * FROM "testusers"""", emptyList()), r)
    }

    @Test
    fun `like predicates`() {
        val h2 = ZekoTranslator(SqlDialect.H2)
        val s = PropertyExpression(TestUser::name)

        var r = h2.translate(filterExpr(StartsWithExpression(s, ConstantExpression("Ali"))))
        assertEquals(SqlAndParams("""SELECT * FROM "testusers" WHERE "name" LIKE ?""", listOf("Ali%")), r)

        r = h2.translate(filterExpr(ContainsExpression(s, ConstantExpression("li"))))
        assertEquals(SqlAndParams("""SELECT * FROM "testusers" WHERE "name" LIKE ?""", listOf("%li%")), r)

        r = h2.translate(filterExpr(EndsWithExpression(s, ConstantExpression("ce"))))
        assertEquals(SqlAndParams("""SELECT * FROM "testusers" WHERE "name" LIKE ?""", listOf("%ce")), r)
    }

    @Test
    fun `aggregates`() {
        val h2 = ZekoTranslator(SqlDialect.H2)
        val s = LambdaExpression(ParameterExpression("it", TestUser::class), PropertyExpression(TestUser::age))

        var r = h2.translate(CountExpression(root))
        assertEquals(SqlAndParams("""SELECT COUNT(*) FROM "testusers"""", emptyList()), r)

        r = h2.translate(SumExpression(root, s))
        assertEquals(SqlAndParams("""SELECT SUM("age") FROM "testusers"""", emptyList()), r)

        r = h2.translate(AvgExpression(root, s))
        assertEquals(SqlAndParams("""SELECT AVG("age") FROM "testusers"""", emptyList()), r)

        r = h2.translate(MinExpression(root, s))
        assertEquals(SqlAndParams("""SELECT MIN("age") FROM "testusers"""", emptyList()), r)

        r = h2.translate(MaxExpression(root, s))
        assertEquals(SqlAndParams("""SELECT MAX("age") FROM "testusers"""", emptyList()), r)
    }

    @Test
    fun `insert`() {
        val entity = TestUser(id = 42, name = "Alice", age = 30, email = "a@b.com", active = true, score = 95.5)
        val model = EntityModel.resolve<TestUser>()
        val params = mutableListOf<Any?>()
        val translator = ZekoTranslator(SqlDialect.H2)
        val sql = translator.translateInsert(entity, model, params)
        assertEquals("""INSERT INTO "users" ("active", "age", "email", "name", "score") VALUES (?, ?, ?, ?, ?)""", sql)
        assertEquals(listOf(true, 30, "a@b.com", "Alice", 95.5), params)
    }

    @Test
    fun `update`() {
        val entity = TestUser(id = 42, name = "Bob", age = 25, email = "b@c.com", active = false, score = 80.0)
        val model = EntityModel.resolve<TestUser>()
        val params = mutableListOf<Any?>()
        val translator = ZekoTranslator(SqlDialect.H2)
        val sql = translator.translateUpdate(entity, model, params)
        assertEquals("""UPDATE "users" SET "active" = ?, "age" = ?, "email" = ?, "name" = ?, "score" = ? WHERE "id" = ?""", sql)
        assertEquals(listOf(false, 25, "b@c.com", "Bob", 80.0, 42), params)
    }

    @Test
    fun `delete`() {
        val entity = TestUser(id = 7)
        val model = EntityModel.resolve<TestUser>()
        val params = mutableListOf<Any?>()
        val translator = ZekoTranslator(SqlDialect.H2)
        val sql = translator.translateDelete(entity, model, params)
        assertEquals("""DELETE FROM "users" WHERE "id" = ?""", sql)
        assertEquals(listOf(7), params)
    }

    @Test
    fun `find by key`() {
        val result = ZekoTranslator(SqlDialect.H2).translate(FindExpression(TestUser::class, mapOf("id" to 1)))
        assertEquals(SqlAndParams("""SELECT * FROM "testusers" WHERE "id" = ?""", listOf(1)), result)
    }

    // ── POSTGRES dialect tests ────────────────────────────────────────────

    @Test
    fun `postgres parameter markers`() {
        val pg = ZekoTranslator(SqlDialect.POSTGRES)

        var r = pg.translate(filterExpr(eq(p, constant("Alice"))))
        assertEquals(SqlAndParams("""SELECT * FROM "testusers" WHERE "name" = $1""", listOf("Alice")), r)

        r = pg.translate(filterExpr(and(eq(p, constant("Alice")), gt(pAge, constant(18)))))
        assertEquals(
            SqlAndParams("""SELECT * FROM "testusers" WHERE ("name" = $1 AND "age" > $2)""", listOf("Alice", 18)),
            r
        )

        r = pg.translate(filterExpr(StartsWithExpression(p, ConstantExpression("Ali"))))
        assertEquals(SqlAndParams("""SELECT * FROM "testusers" WHERE "name" LIKE $1""", listOf("Ali%")), r)

        val entity = TestUser(id = 1, name = "Alice", age = 30, email = "a@b.com", active = true, score = 95.5)
        val model = EntityModel.resolve<TestUser>()
        val params = mutableListOf<Any?>()
        val sql = pg.translateInsert(entity, model, params)
        assertEquals("""INSERT INTO "users" ("active", "age", "email", "name", "score") VALUES ($1, $2, $3, $4, $5)""", sql)
        assertEquals(listOf(true, 30, "a@b.com", "Alice", 95.5), params)
    }

    @Test
    fun `postgres ddl serial`() {
        val model = EntityModel.resolve<TestUser>()
        val ddl = ZekoTranslator(SqlDialect.POSTGRES).translateDdl(model)
        assertEquals(
            """CREATE TABLE IF NOT EXISTS "users" (
  "active" BOOLEAN,
  "age" INT,
  "email" VARCHAR(255),
  "id" SERIAL PRIMARY KEY,
  "name" VARCHAR(255),
  "score" DOUBLE
)""",
            ddl
        )
    }

    @Test
    fun `postgres ddl bigserial`() {
        val model = EntityModel.resolve<BigUser>()
        val ddl = ZekoTranslator(SqlDialect.POSTGRES).translateDdl(model)
        assertEquals(
            """CREATE TABLE IF NOT EXISTS "big_users" (
  "id" BIGSERIAL PRIMARY KEY,
  "name" VARCHAR(255)
)""",
            ddl
        )
    }
}
