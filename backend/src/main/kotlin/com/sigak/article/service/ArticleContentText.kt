package com.sigak.article.service

object ArticleContentText {
    fun plain(text: String): String = text
        .replace(Regex("<[^>]*>"), " ")
        .replace("&nbsp;", " ").replace("&amp;", "&").replace("&lt;", "<").replace("&gt;", ">")
        .replace(Regex("\\s+"), " ").trim()

    fun excerpt(text: String): String = plain(text).take(1200)
}
