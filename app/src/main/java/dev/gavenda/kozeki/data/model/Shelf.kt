package dev.gavenda.kozeki.data.model

/** A shelf the user made, with how many books of the library are on it. */
data class Shelf(val id: String, val name: String, val bookCount: Int)
