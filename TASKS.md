# tasks

## what's done

- entity annotations (@Table, @Column, @Id, @GeneratedValue)
- EntityModel - reflection metadata, columns/pk/tablename
- DbContext / DbSet<T> base
- R2DBC wired up (H2 for now, works fine)
- ensureCreated() - DDL from entity model
- add / remove + saveChanges - INSERT/DELETE with key writeback
- change tracker w/ snapshots - dirty detect on saveChanges
- row → entity mapper (Materializer)
- filter {} compiler rewrite → WHERE
- map {} → SELECT col
- sortedBy / sortedByDesc → ORDER BY
- toList / first / firstOrNull / single (single needs testing lol)
- count() pushdown → COUNT(*)
- any / all pushdown → SELECT 1 LIMIT 1
- sumOf / minOf / maxOf / averageOf aggregate pushdown
- distinct() → SELECT DISTINCT
- take / drop → LIMIT / OFFSET (drop is offset)
- contains() existence check by PK
- ZekoTranslator basic - expr tree → SQL, H2 dialect (? markers)
- compiler plugin skeleton, IrCall visitor, buildExpr dispatch

- thenBy / thenByDesc → multi-column ORDER BY  (finally works now)
- string predicates in filter: startsWith, endsWith, contains → LIKE
- null checks: == null / != null → IS NULL / IS NOT NULL
- SqlDialect enum - H2 (?) vs PG ($1, $2...)
- update(entity) - marks Modified, persisted on saveChanges
- find(id) / findOrNull - PK lookup, cached if queried already
- transaction {} block on DbContext
- toSql() on DbSet - inspect generated SQL without executing (useful af)
- ZekoTranslatorTest - bunch of unit tests, no db needed
- QuerySliceTest - 13 integration tests on in-mem H2
- EndToEndTest - 9 compiler plugin e2e tests (kctfork is amazing) !! 

- range membership: it.rating in 2..4 / 2..<4 / until → >= AND <(=) comparisons
- in w/ collection: it.rating in listOf(1,3,5) → IN (...) (works w/ captured vals too)
- !in variants → NOT (...) wrapper (same semantics as NOT IN / NOT BETWEEN)
- Materializer fix - ctor params w/ Kotlin Int used primitive int.class, h2 codec choked ("Cannot decode value of type int") - javaObjectType now

## what's next (kinda rough order)

### query stuff
- groupBy + HAVING (need aggregate-in-projection first)
- flatMap / SelectMany - correlated subquery or cross join
- include / thenInclude - JOIN support for related entities
- compiler plugin rewrites for skip/take (runtime drop/take works but plugin should too)
- isNull/isNotNull plugin rewrites (they end up as == null which works but eh)
- or compound predicates in filter (and chains work, or gets wonky)

### relationships
- @OneToMany / @ManyToOne / @OneToOne annotations
- lazy loading nav props (proxy or delegated property on first access)
- include { it.posts } eager loading
- thenInclude chained eager load
- cascade delete / save for owned collections
- many-to-many via join table (no explicit join entity)

### change tracking
- asNoTracking() - skip snapshot, read-only fast path
- reload(entity) - refresh from db, ditch local changes
- optimistic concurrency @Version / @ConcurrencyToken - detect collisions on saveChanges
- detach(entity) - remove from tracker without deleting

### global filters
- modelBuilder.filter<T> { it.deletedAt == null } - auto applied to all queries
- ignoreQueryFilters() on DbSet to opt out
- built-in soft-delete pattern wired to global filter

### mapping stuff
- @Ignore - exclude property from column mapping
- @ValueConverter - enum→String, Instant→Long etc
- @ColumnDefault for ddl defaults
- @Computed - read-only, excluded from insert/update
- @Transient alias for @Ignore (jpa compat)
- backing-field support - map to private var _field

### raw sql / escape hatches
- fromSql<T>("SELECT...") - raw sql to entity mapping
- executeSql("UPDATE...") - arbitrary dml
- DbSet.fromSql { ... } - mix raw sql as query source, then chain filter etc.

### inheritance
- TPH - discriminator column, single table
- TPT - each subclass gets own table, JOIN on query
- @Keyless entity type - map views, no pk, no tracking

### perf
- compiled/cached query plans - parse expr tree once
- DbContext pooling - reuse contexts (borrow/return pattern)
- split-query for collection includes - avoid cartesian explosion
- batch INSERT on saveChanges (multi-row insert)
- connection resiliency - configurable retry

### transactions
- share physical connection inside transaction {} - saveChanges inside a tx block should use same conn
- nested transactions (savepoints)

### schema / ddl
- ensureDeleted() - drop tables
- migrate() - schema diff, incremental ddl (add column, add table at minimum)
- foreign key / index via annotations

### dbset ops
- removeWhere { predicate } - bulk DELETE with WHERE (no load-then-delete)
- updateWhere { predicate } set { ... } - bulk UPDATE
- upsert / insertOrUpdate

### dialects
- postgres integration test suite (h2 only rn)
- mysql / mariadb dialect
- named parameters (:name style)

### compiler plugin gaps
- better diagnostics - compiler warning when lambda cant be rewritten (not just runtime throw)
- when expression in predicate - desugar to AND/OR chain
- if expression in predicate (same as when, diff ir shape)
- string endsWith rewrite (builder exists, plugin dispatch missing -_-)
- chained property access in predicates (it.address.city == "X" → needs JOIN planning)
- elvis: it.nullable ?: default → COALESCE
- arithmetic in projections: it.price * it.qty → price * qty in SELECT
- let / run blocks inside lambdas

### qol
- DbSet<T> implements Flow<T> or .asFlow()
- pretty/formatted toSql() variant for logging
- DbContextOptions builder dsl
- @DefaultValue annotation

### observability
- structured query logging w/ duration (replace raw slf4j debug)

### publishing
- publish runtime + compiler plugin to maven central
- gradle plugin auto-wires compilerPluginRegistrars
- versioning policy & changelog
