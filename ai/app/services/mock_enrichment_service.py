from app.schemas.enrichment import EnrichmentRequest, EnrichmentResponse


def enrich_article(request: EnrichmentRequest) -> EnrichmentResponse:
    primary_topic = request.topics[0] if request.topics else "SOFTWARE_ENGINEERING"
    first_sentence = request.rawContent.strip().split(".")[0].strip()
    summary_text = f"{request.title} discusses {first_sentence}."

    return EnrichmentResponse(
        summary=summary_text,
        whyItMatters=(
            f"This matters because {request.source} is connected to {primary_topic} "
            "and may affect how technical teams understand the topic."
        ),
        suggestedTopics=request.topics or [primary_topic],
        suggestedPrimaryCategory=primary_topic,
        suggestedImportanceScore=70,
    )
