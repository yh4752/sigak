package com.sigak.collection.service

import com.sigak.collection.domain.NewsSource
import com.sigak.collection.domain.SourceType
import org.springframework.stereotype.Component

@Component
class SourceRegistry {

    fun sources(): List<NewsSource> = listOf(
        NewsSource(
            id = "openai-blog",
            name = "OpenAI Blog",
            type = SourceType.RSS_ATOM,
            url = "https://openai.com/news/rss.xml",
            categoryHint = "AI"
        ),
        NewsSource(
            id = "google-ai-blog",
            name = "Google AI Blog",
            type = SourceType.RSS_ATOM,
            url = "https://blog.google/technology/ai/rss/",
            categoryHint = "AI"
        ),
        NewsSource(
            id = "github-blog",
            name = "GitHub Blog",
            type = SourceType.RSS_ATOM,
            url = "https://github.blog/feed/",
            categoryHint = "DEVTOOLS"
        ),
        NewsSource(
            id = "arxiv-cs-ai",
            name = "arXiv cs.AI",
            type = SourceType.ARXIV,
            url = "https://export.arxiv.org/api/query?search_query=cat:cs.AI&sortBy=submittedDate&sortOrder=descending&max_results=10",
            categoryHint = "CS_RESEARCH"
        ),
        NewsSource(
            id = "arxiv-cs-lg",
            name = "arXiv cs.LG",
            type = SourceType.ARXIV,
            url = "https://export.arxiv.org/api/query?search_query=cat:cs.LG&sortBy=submittedDate&sortOrder=descending&max_results=10",
            categoryHint = "CS_RESEARCH"
        ),
        NewsSource(
            id = "arxiv-cs-cl",
            name = "arXiv cs.CL",
            type = SourceType.ARXIV,
            url = "https://export.arxiv.org/api/query?search_query=cat:cs.CL&sortBy=submittedDate&sortOrder=descending&max_results=10",
            categoryHint = "CS_RESEARCH"
        )
    )
}
