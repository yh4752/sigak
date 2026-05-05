from fastapi import FastAPI

from app.routers.enrichment import router as enrichment_router


app = FastAPI(title="Sigak AI Server")
app.include_router(enrichment_router)


@app.get("/health")
def health() -> dict[str, str]:
    return {"status": "ok"}
