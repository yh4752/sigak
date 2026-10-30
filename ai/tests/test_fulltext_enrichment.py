import json

import httpx
import pytest
from fastapi.testclient import TestClient

from app.config import EnrichmentSettings, load_enrichment_settings
from app.main import app
from app.routers.enrichment import get_enrichment_service
from app.schemas.enrichment import EnrichmentRequest
from app.services.openai_enrichment_service import EnrichmentProviderError
from test_openai_enrichment import ARTICLE, OUTPUT, envelope, service


@pytest.fixture(autouse=True)
def forbid_external_http_calls(monkeypatch):
    handle_request = httpx.HTTPTransport.handle_request

    def local_fixture_only(transport, request):
        if request.url.host not in {"127.0.0.1", "localhost"}:
            raise AssertionError("External HTTP is disabled in full-text fixture tests")
        return handle_request(transport, request)

    monkeypatch.setattr(httpx.HTTPTransport, "handle_request", local_fixture_only)


def test_full_text_route_preserves_tail_and_returns_v2_provenance():
    requests = []
    tail = "UNIQUE FULL TEXT FINAL EVIDENCE."
    article = {**ARTICLE, "inputScope": "FULL_TEXT", "rawContent": "Complete body. " * 2500 + tail}

    def handler(request):
        requests.append(request)
        return httpx.Response(200, json=envelope({**OUTPUT, "evidenceQuotes": [tail]}))

    app.dependency_overrides[get_enrichment_service] = lambda: service(handler)
    try:
        response = TestClient(app).post("/api/enrichment/article", json=article)
        assert response.status_code == 200
        result = response.json()
        assert result["inputScope"] == "FULL_TEXT"
        assert result["promptVersion"] == "article-summary-v2-fulltext"
        assert result["truncated"] is False
        assert result["evidenceQuotes"] == [tail]
        assert result["cachedInputTokens"] == 0
        assert result["cacheWriteTokens"] == 0
        payload = json.loads(requests[0].content)
        submitted = json.loads(payload["input"][0]["content"])
        assert submitted["rawContent"] == article["rawContent"]
        assert len(requests[0].content) <= 65536
        assert payload["prompt_cache_options"] == {"mode": "explicit"}
        assert "breakpoints" not in payload["prompt_cache_options"]
        assert "prewarm" not in payload["prompt_cache_options"]
        assert "ttl" not in payload["prompt_cache_options"]
    finally:
        app.dependency_overrides.clear()


def test_over_cap_full_text_is_rejected_before_transport():
    requests = []
    app.dependency_overrides[get_enrichment_service] = lambda: service(
        lambda request: requests.append(request))
    article = {**ARTICLE, "inputScope": "FULL_TEXT", "rawContent": "Full body. " * 7000}
    try:
        response = TestClient(app).post("/api/enrichment/article", json=article)
        assert response.status_code == 422
        assert response.json() == {"detail": "Full article exceeds the enrichment input limit."}
        assert requests == []
    finally:
        app.dependency_overrides.clear()


@pytest.mark.parametrize("input_tokens", [32769, 70000])
def test_new_input_usage_ceiling_accepts_full_text_up_to_70000(input_tokens):
    provider = service(lambda _: httpx.Response(200, json=envelope(
        usage={"input_tokens": input_tokens, "output_tokens": 2048})))
    result = provider.enrich(EnrichmentRequest(**ARTICLE))
    assert result.inputTokens == input_tokens


@pytest.mark.parametrize("details", [
    {"cached_tokens": -1},
    {"cached_tokens": True},
    {"cached_tokens": 701},
    {"cache_write_tokens": 1},
    {"cache_write_tokens": -1},
    {"cache_write_tokens": True},
])
def test_invalid_cache_usage_stops_backend_circuit(details):
    provider = service(lambda _: httpx.Response(200, json=envelope(usage={
        "input_tokens": 700, "output_tokens": 120, "input_tokens_details": details})))
    with pytest.raises(EnrichmentProviderError) as failure:
        provider.enrich(EnrichmentRequest(**ARTICLE))
    assert failure.value.status_code == 503


def test_cached_input_usage_is_preserved_for_accounting():
    provider = service(lambda _: httpx.Response(200, json=envelope(usage={
        "input_tokens": 700, "output_tokens": 120,
        "input_tokens_details": {"cached_tokens": 320, "cache_write_tokens": 0}})))
    result = provider.enrich(EnrichmentRequest(**ARTICLE))
    assert result.cachedInputTokens == 320
    assert result.cacheWriteTokens == 0


def test_lifespan_reuses_provider_client_and_closes_it_on_shutdown(monkeypatch):
    import app.main as main_module

    requests = []
    shared_client = httpx.Client(transport=httpx.MockTransport(
        lambda request: requests.append(request) or httpx.Response(200, json=envelope())))
    created_clients = []

    def create_client():
        created_clients.append(shared_client)
        return shared_client

    monkeypatch.setattr(main_module, "create_enrichment_http_client", create_client, raising=False)
    app.dependency_overrides[load_enrichment_settings] = lambda: EnrichmentSettings("openai", "fixture-secret")
    try:
        with TestClient(app) as client:
            assert not shared_client.is_closed
            for _ in range(2):
                response = client.post("/api/enrichment/article", json=ARTICLE)
                assert response.status_code == 200
            assert len(created_clients) == 1
            assert app.state.enrichment_http_client is shared_client
        assert shared_client.is_closed
        assert len(requests) == 2
    finally:
        app.dependency_overrides.clear()
        shared_client.close()


def test_full_text_transport_failure_stays_sanitized_and_is_not_retried():
    requests = []
    article = {**ARTICLE, "inputScope": "FULL_TEXT"}

    def handler(request):
        requests.append(request)
        raise httpx.ReadTimeout("fixture-secret private provider body", request=request)

    app.dependency_overrides[get_enrichment_service] = lambda: service(handler)
    try:
        response = TestClient(app).post("/api/enrichment/article", json=article)
        assert response.status_code == 504
        assert "fixture-secret" not in response.text
        assert "private provider body" not in response.text
        assert len(requests) == 1
    finally:
        app.dependency_overrides.clear()


def test_production_http_pool_does_not_replay_a_lost_response(monkeypatch):
    from http.server import BaseHTTPRequestHandler, HTTPServer
    from threading import Thread

    import app.services.openai_enrichment_service as provider_module

    requests = []

    class LostResponseHandler(BaseHTTPRequestHandler):
        def do_POST(self):
            requests.append(self.rfile.read(int(self.headers["Content-Length"])))
            self.close_connection = True

        def log_message(self, *_):
            pass

    server = HTTPServer(("127.0.0.1", 0), LostResponseHandler)
    thread = Thread(target=server.serve_forever, daemon=True)
    thread.start()
    monkeypatch.setattr(provider_module, "RESPONSES_URL", f"http://127.0.0.1:{server.server_port}/v1/responses")
    client = provider_module.create_enrichment_http_client()
    provider = provider_module.OpenAIEnrichmentService(EnrichmentSettings("openai", "fixture-secret"), client)
    try:
        with pytest.raises(EnrichmentProviderError) as failure:
            provider.enrich(EnrichmentRequest(**ARTICLE, inputScope="FULL_TEXT"))
        assert failure.value.status_code == 502
        assert len(requests) == 1
    finally:
        client.close()
        server.shutdown()
        server.server_close()
        thread.join(timeout=2)
