package com.sigak.support

import com.sigak.collection.domain.NewsSource
import com.sigak.collection.domain.SourceType
import com.sigak.collection.service.SourceRegistry
import org.mockito.Mockito.`when`
import org.mockito.Mockito.mock

fun sourceRegistryFixture(): SourceRegistry {
    val registry = mock(SourceRegistry::class.java)
    val sources = listOf(
        NewsSource("openai-blog", "OpenAI Blog", SourceType.RSS_ATOM, "https://openai.com/news/rss.xml", "AI"),
        NewsSource("google-ai-blog", "Google AI Blog", SourceType.RSS_ATOM, "https://blog.google/technology/ai/rss/", "AI"),
        NewsSource("github-blog", "GitHub Blog", SourceType.RSS_ATOM, "https://github.blog/feed/", "DEVTOOLS"),
        NewsSource("arxiv-cs-ai", "arXiv cs.AI", SourceType.ARXIV, "https://export.arxiv.org/api/query?search_query=cat:cs.AI", "CS_RESEARCH"),
        NewsSource("arxiv-cs-lg", "arXiv cs.LG", SourceType.ARXIV, "https://export.arxiv.org/api/query?search_query=cat:cs.LG", "CS_RESEARCH"),
        NewsSource("arxiv-cs-cl", "arXiv cs.CL", SourceType.ARXIV, "https://export.arxiv.org/api/query?search_query=cat:cs.CL", "CS_RESEARCH")
    )
    `when`(registry.sources()).thenReturn(sources)
    `when`(registry.knownIds()).thenReturn(sources.map { it.id }.toSet())
    return registry
}
