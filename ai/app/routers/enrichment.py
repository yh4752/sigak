from fastapi import APIRouter, Depends, HTTPException

from app.config import EnrichmentSettings, load_enrichment_settings
from app.schemas.enrichment import EnrichmentRequest, EnrichmentResponse
from app.services.mock_enrichment_service import enrich_article
from app.services.openai_enrichment_service import EnrichmentProviderError, OpenAIEnrichmentService


router = APIRouter(prefix="/api/enrichment", tags=["enrichment"])


def get_enrichment_service(
    settings: EnrichmentSettings = Depends(load_enrichment_settings),
) -> OpenAIEnrichmentService | None:
    if settings.enrichment_provider == "mock":
        return None
    if settings.enrichment_provider == "openai":
        return OpenAIEnrichmentService(settings)
    raise HTTPException(status_code=503, detail="Article enrichment provider is not configured.")


@router.post("/article", response_model=EnrichmentResponse, response_model_exclude_none=True)
def enrich_article_endpoint(
    request: EnrichmentRequest,
    service: OpenAIEnrichmentService | None = Depends(get_enrichment_service),
) -> EnrichmentResponse:
    if service is None:
        return enrich_article(request)
    try:
        return service.enrich(request)
    except EnrichmentProviderError as error:
        raise HTTPException(status_code=error.status_code, detail=str(error)) from None
