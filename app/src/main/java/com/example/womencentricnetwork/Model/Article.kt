package com.example.womencentricnetwork.Model

data class Article(
    val id: String = "",
    val title: String = "",
    val content: String = "",
    val authorName: String = "",
    val timestamp: Long = 0L,
    val category: String = "",
    val imageUrl: String? = null
)

