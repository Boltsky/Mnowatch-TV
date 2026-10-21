package com.example.data.api

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class BrowseDto(
    @field:Json(name = "vj") val vj: List<VjDto> = emptyList(),
    @field:Json(name = "genre") val genre: List<GenreDto> = emptyList()
)

@JsonClass(generateAdapter = true)
data class VjDto(
    @field:Json(name = "id") val id: Int,
    @field:Json(name = "name") val name: String,
    @field:Json(name = "icon") val icon: String = "",
    @field:Json(name = "pipe") val pipe: String = ""
)

@JsonClass(generateAdapter = true)
data class GenreDto(
    @field:Json(name = "id") val id: Int,
    @field:Json(name = "name") val name: String,
    @field:Json(name = "icon") val icon: String = "",
    @field:Json(name = "pipe") val pipe: String = ""
)
