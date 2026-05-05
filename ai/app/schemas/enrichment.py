from pydantic import BaseModel, Field


class EnrichmentRequest(BaseModel):
    title: str = Field(min_length=1)
    source: str = Field(min_length=1)
    url: str = Field(min_length=1)
    publishedAt: str = Field(min_length=1)
    topics: list[str] = Field(default_factory=list)
    rawContent: str = Field(min_length=1)


class EnrichmentResponse(BaseModel):
    summary: str
    whyItMatters: str
    suggestedTopics: list[str]
    suggestedPrimaryCategory: str
    suggestedImportanceScore: int
