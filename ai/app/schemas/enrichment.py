from typing import Annotated

from pydantic import BaseModel, Field, StringConstraints


RequiredText = Annotated[str, StringConstraints(strip_whitespace=True, min_length=1)]


class EnrichmentRequest(BaseModel):
    title: RequiredText
    source: RequiredText
    url: RequiredText
    publishedAt: RequiredText
    topics: list[str] = Field(default_factory=list)
    rawContent: RequiredText


class EnrichmentResponse(BaseModel):
    summary: str
    whyItMatters: str
    suggestedTopics: list[str]
    suggestedPrimaryCategory: str
    suggestedImportanceScore: int = Field(ge=0, le=100)
    modelName: str = "unknown-enrichment"
