package dev.efkore.sample

import dev.efkore.metadata.Column
import dev.efkore.metadata.GeneratedValue
import dev.efkore.metadata.Id
import dev.efkore.metadata.Table

@Table("blogs")
data class Blog(
    @Id @GeneratedValue var id: Long,
    @Column var title: String,
    @Column var content: String,
    @Column var published: Boolean
)
