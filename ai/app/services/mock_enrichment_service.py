from app.schemas.enrichment import EnrichmentRequest, EnrichmentResponse


MOCK_ENRICHMENT_MODEL_NAME = "mock-enrichment"


def enrich_article(request: EnrichmentRequest) -> EnrichmentResponse:
    primary_topic = request.topics[0] if request.topics else "SOFTWARE_ENGINEERING"
    first_sentence = request.rawContent.strip().split(".")[0].strip()
    summary_text = f"{request.title} discusses {first_sentence}."

    # 외부 LLM 없이도 로컬 개발과 테스트가 가능하도록 결정론적인 mock 응답을 생성한다.
    return EnrichmentResponse(
        summary=summary_text,
        whyItMatters=(
            f"This matters because {request.source} is connected to {primary_topic} "
            "and may affect how technical teams understand the topic."
        ),
        suggestedTopics=request.topics or [primary_topic],
        suggestedPrimaryCategory=primary_topic,
        suggestedImportanceScore=70,
        modelName=MOCK_ENRICHMENT_MODEL_NAME,
    )
