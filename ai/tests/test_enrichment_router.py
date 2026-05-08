from fastapi.testclient import TestClient
from pydantic import ValidationError
import pytest

from app.main import app
from app.schemas.enrichment import EnrichmentResponse


client = TestClient(app)


def test_enrich_article_returns_mock_summary_and_insight():
    response = client.post(
        "/api/enrichment/article",
        json={
            "title": "Evaluating Retrieval Agents",
            "source": "arXiv cs.AI",
            "url": "http://arxiv.org/abs/2605.00001v1",
            "publishedAt": "2026-05-05T00:00:00Z",
            "topics": ["CS_RESEARCH"],
            "rawContent": "We study retrieval agents in technical knowledge workflows.",
        },
    )

    assert response.status_code == 200
    assert response.json() == {
        "summary": "Evaluating Retrieval Agents discusses We study retrieval agents in technical knowledge workflows.",
        "whyItMatters": "This matters because arXiv cs.AI is connected to CS_RESEARCH and may affect how technical teams understand the topic.",
        "suggestedTopics": ["CS_RESEARCH"],
        "suggestedPrimaryCategory": "CS_RESEARCH",
        "suggestedImportanceScore": 70,
    }


def test_enrich_article_rejects_whitespace_only_required_text():
    response = client.post(
        "/api/enrichment/article",
        json={
            "title": "   ",
            "source": "arXiv cs.AI",
            "url": "http://arxiv.org/abs/2605.00001v1",
            "publishedAt": "2026-05-05T00:00:00Z",
            "topics": ["CS_RESEARCH"],
            "rawContent": "We study retrieval agents in technical knowledge workflows.",
        },
    )

    assert response.status_code == 422


def test_enrichment_response_rejects_importance_score_outside_0_to_100():
    with pytest.raises(ValidationError):
        EnrichmentResponse(
            summary="Summary",
            whyItMatters="Why it matters",
            suggestedTopics=["CS_RESEARCH"],
            suggestedPrimaryCategory="CS_RESEARCH",
            suggestedImportanceScore=101,
        )
