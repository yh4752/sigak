package com.sigak.search.hybrid

import org.springframework.stereotype.Component

@Component
class ReciprocalRankFusion {

    fun fuse(
        keywordCandidates: List<ArticleSearchCandidate>,
        vectorCandidates: List<ArticleSearchCandidate>,
        rrfK: Int,
        keywordWeight: Double,
        vectorWeight: Double,
        limit: Int
    ): List<ArticleSearchCandidate> {
        val scoresByArticleId = mutableMapOf<Long, Double>()
        val bestRankByArticleId = mutableMapOf<Long, Int>()

        accumulate(scoresByArticleId, bestRankByArticleId, keywordCandidates, rrfK, keywordWeight)
        accumulate(scoresByArticleId, bestRankByArticleId, vectorCandidates, rrfK, vectorWeight)

        return scoresByArticleId
            .map { (articleId, score) ->
                FusedCandidate(
                    articleId = articleId,
                    score = score,
                    bestRank = bestRankByArticleId.getValue(articleId)
                )
            }
            .sortedWith(
                compareByDescending<FusedCandidate> { candidate -> candidate.score }
                    .thenBy { candidate -> candidate.bestRank }
                    .thenBy { candidate -> candidate.articleId }
            )
            .take(limit)
            .mapIndexed { index, candidate ->
                ArticleSearchCandidate(
                    articleId = candidate.articleId,
                    rank = index + 1,
                    score = candidate.score
                )
            }
    }

    private fun accumulate(
        scoresByArticleId: MutableMap<Long, Double>,
        bestRankByArticleId: MutableMap<Long, Int>,
        candidates: List<ArticleSearchCandidate>,
        rrfK: Int,
        weight: Double
    ) {
        candidates.forEach { candidate ->
            val score = weight / (rrfK + candidate.rank)
            scoresByArticleId[candidate.articleId] = scoresByArticleId.getOrDefault(candidate.articleId, 0.0) + score
            bestRankByArticleId[candidate.articleId] = minOf(
                bestRankByArticleId[candidate.articleId] ?: candidate.rank,
                candidate.rank
            )
        }
    }

    private data class FusedCandidate(
        val articleId: Long,
        val score: Double,
        val bestRank: Int
    )
}
