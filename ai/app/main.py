from contextlib import asynccontextmanager

from fastapi import FastAPI

from app.routers.embedding import router as embedding_router
from app.routers.enrichment import router as enrichment_router
from app.services.openai_enrichment_service import create_enrichment_http_client


@asynccontextmanager
async def lifespan(app: FastAPI):
    client = create_enrichment_http_client()
    app.state.enrichment_http_client = client
    try:
        yield
    finally:
        client.close()
        app.state.enrichment_http_client = None


app = FastAPI(title="Sigak AI Server", lifespan=lifespan)
app.include_router(enrichment_router)
app.include_router(embedding_router)


@app.get("/health")
def health() -> dict[str, str]:
    return {"status": "ok"}
