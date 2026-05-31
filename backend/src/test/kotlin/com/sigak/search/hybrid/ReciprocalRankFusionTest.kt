package com.sigak.search.hybrid

import kotlin.test.Test
import kotlin.test.assertEquals

class ReciprocalRankFusionTest {

    private val fusion = ReciprocalRankFusion()

    @Test
    fun fusesKeywordAndVectorCandidatesByReciprocalRank() {
        val result = fusion.fuse(
            keywordCandidates = listOf(
                ArticleSearchCandidate(articleId = 10, rank = 1),
                ArticleSearchCandidate(articleId = 20, rank = 2),
                ArticleSearchCandidate(articleId = 30, rank = 3)
            ),
            vectorCandidates = listOf(
                ArticleSearchCandidate(articleId = 30, rank = 1),
                ArticleSearchCandidate(articleId = 10, rank = 2),
                ArticleSearchCandidate(articleId = 40, rank = 3)
            ),
            rrfK = 60,
            keywordWeight = 1.0,
            vectorWeight = 1.0,
            limit = 10
        )

        assertEquals(listOf(10L, 30L, 20L, 40L), result.map { candidate -> candidate.articleId })
        assertEquals(listOf(1, 2, 3, 4), result.map { candidate -> candidate.rank })
    }

    @Test
    fun appliesWeightsAndLimit() {
        val result = fusion.fuse(
            keywordCandidates = listOf(
                ArticleSearchCandidate(articleId = 1, rank = 1),
                ArticleSearchCandidate(articleId = 2, rank = 2)
            ),
            vectorCandidates = listOf(
                ArticleSearchCandidate(articleId = 3, rank = 1),
                ArticleSearchCandidate(articleId = 2, rank = 2)
            ),
            rrfK = 60,
            keywordWeight = 2.0,
            vectorWeight = 1.0,
            limit = 2
        )

        assertEquals(listOf(2L, 1L), result.map { candidate -> candidate.articleId })
    }

    @Test
    fun tieBreaksByBestRankWhenScoresMatch() {
        val result = fusion.fuse(
            keywordCandidates = listOf(
                ArticleSearchCandidate(articleId = 5, rank = 2),
                ArticleSearchCandidate(articleId = 3, rank = 1)
            ),
            vectorCandidates = emptyList(),
            rrfK = 60,
            keywordWeight = 0.0,
            vectorWeight = 0.0,
            limit = 10
        )

        assertEquals(listOf(3L, 5L), result.map { candidate -> candidate.articleId })
    }

    @Test
    fun tieBreaksByArticleIdWhenScoreAndBestRankMatch() {
        val result = fusion.fuse(
            keywordCandidates = listOf(
                ArticleSearchCandidate(articleId = 5, rank = 1),
                ArticleSearchCandidate(articleId = 3, rank = 1)
            ),
            vectorCandidates = emptyList(),
            rrfK = 60,
            keywordWeight = 1.0,
            vectorWeight = 1.0,
            limit = 10
        )

        assertEquals(listOf(3L, 5L), result.map { candidate -> candidate.articleId })
    }
}
