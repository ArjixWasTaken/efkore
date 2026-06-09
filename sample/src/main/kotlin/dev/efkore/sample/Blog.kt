package dev.efkore.sample

import dev.efkore.metadata.*

@Entity
@Table("blogs")
data class Blog(
    @Id @GeneratedValue var id: Int = 0,
    @Column("url") var url: String = "",
    @Column("rating") var rating: Int = 0
)
