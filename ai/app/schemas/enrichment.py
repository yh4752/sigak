from typing import Annotated, Literal

from pydantic import BaseModel, Field, StringConstraints


RequiredText = Annotated[str, StringConstraints(strip_whitespace=True, min_length=1)]


class EnrichmentRequest(BaseModel):
    title: RequiredText
    source: RequiredText
    url: RequiredText
    publishedAt: RequiredText
    topics: list[str] = Field(default_factory=list)
    rawContent: RequiredText
    inputScope: Literal["FEED_EXCERPT", "ABSTRACT", "FULL_TEXT"] = "FEED_EXCERPT"


class EnrichmentResponse(BaseModel):
    summary: str
    whyItMatters: str
    suggestedTopics: list[str]
    suggestedPrimaryCategory: str
    suggestedImportanceScore: int = Field(ge=0, le=100)
    modelName: str = "unknown-enrichment"
    promptVersion: str | None = None
    inputScope: Literal["FEED_EXCERPT", "ABSTRACT", "FULL_TEXT"] | None = None
    truncated: bool | None = None
    evidenceQuotes: list[str] | None = None
    inputTokens: int | None = Field(default=None, ge=0, le=70000)
    outputTokens: int | None = Field(default=None, ge=0, le=2048)
    providerResponseId: str | None = None
    cachedInputTokens: int | None = Field(default=None, ge=0, le=70000)
    cacheWriteTokens: int | None = Field(default=None, ge=0, le=0)
