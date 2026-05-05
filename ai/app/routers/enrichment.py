from fastapi import APIRouter

from app.schemas.enrichment import EnrichmentRequest, EnrichmentResponse
from app.services.mock_enrichment_service import enrich_article


router = APIRouter(prefix="/api/enrichment", tags=["enrichment"])


@router.post("/article", response_model=EnrichmentResponse)
def enrich_article_endpoint(request: EnrichmentRequest) -> EnrichmentResponse:
    return enrich_article(request)
