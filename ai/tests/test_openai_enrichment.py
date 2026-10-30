import json

import httpx
import pytest
from fastapi.testclient import TestClient

from app.main import app
from app.config import EnrichmentSettings, load_enrichment_settings
from app.routers.enrichment import get_enrichment_service
from app.schemas.enrichment import EnrichmentRequest
from app.services.openai_enrichment_service import OpenAIEnrichmentService, EnrichmentProviderError


ARTICLE = {"title": "Retrieval agents", "source": "arXiv", "url": "https://example.com/paper",
           "publishedAt": "2026-10-30T00:00:00Z", "topics": ["CS_RESEARCH"],
           "rawContent": "We study retrieval agents. They improve retrieval accuracy."}
OUTPUT = {"summary": "검색 에이전트의 검색 정확도 개선을 연구했다.",
          "whyItMatters": "검색 시스템 설계에 참고할 수 있다.", "suggestedTopics": ["retrieval"],
          "suggestedPrimaryCategory": "CS_RESEARCH", "suggestedImportanceScore": 65,
          "evidenceQuotes": ["They improve retrieval accuracy."]}


def envelope(output=None, **updates):
    result = {"id": "resp_fixture", "status": "completed", "model": "gpt-5.6-luna-2026-10-01",
              "output": [{"type": "message", "status": "completed", "content": [
                  {"type": "output_text", "text": json.dumps(output or OUTPUT)}]}],
              "usage": {"input_tokens": 700, "output_tokens": 120}}
    result.update(updates)
    return result


def service(handler):
    client = httpx.Client(transport=httpx.MockTransport(handler))
    return OpenAIEnrichmentService(EnrichmentSettings("openai", "fixture-secret"), client)


def test_real_enrichment_sends_bounded_strict_request_and_returns_provenance():
    requests = []

    def handler(request):
        requests.append(request)
        return httpx.Response(200, json=envelope())

    result = service(handler).enrich(EnrichmentRequest(**ARTICLE, inputScope="ABSTRACT"))
    sent = json.loads(requests[0].content)
    assert str(requests[0].url) == "https://api.openai.com/v1/responses"
    assert requests[0].headers["authorization"] == "Bearer fixture-secret"
    assert sent["model"] == "gpt-5.6-luna"
    assert sent["reasoning"] == {"effort": "none"}
    assert sent["max_output_tokens"] == 2048
    assert sent["store"] is False
    assert sent.get("tools", []) == []
    assert sent["text"]["format"]["type"] == "json_schema"
    assert sent["text"]["format"]["strict"] is True
    assert sent["text"]["format"]["schema"]["additionalProperties"] is False
    assert len(requests[0].content) <= 16384
    assert result.summary == OUTPUT["summary"]
    assert result.promptVersion == "article-summary-v1"
    assert result.inputScope == "ABSTRACT"
    assert result.truncated is False
    assert result.evidenceQuotes == OUTPUT["evidenceQuotes"]
    assert result.inputTokens == 700
    assert result.outputTokens == 120
    assert result.providerResponseId == "resp_fixture"
    assert result.modelName == "gpt-5.6-luna-2026-10-01"
    assert len(requests) == 1


def test_serialized_payload_truncates_raw_text_deterministically():
    requests = []

    def handler(request):
        requests.append(request)
        return httpx.Response(200, json=envelope())

    provider = service(handler)
    article = {**ARTICLE, "rawContent": ARTICLE["rawContent"] + ' 한글 "\\\n' * 20000}
    first = provider.enrich(EnrichmentRequest(**article))
    second = provider.enrich(EnrichmentRequest(**article))
    assert first.truncated is True
    assert first.inputScope == "FEED_EXCERPT"
    assert len(requests[0].content) <= 16384
    assert requests[0].content == requests[1].content
    assert second.truncated is True


@pytest.mark.parametrize("change", [
    {"summary": "English only"}, {"summary": " "}, {"whyItMatters": "English only"},
    {"evidenceQuotes": []}, {"evidenceQuotes": ["not present"]}, {"evidenceQuotes": [" "]},
    {"evidenceQuotes": ["We study"] * 4}, {"suggestedTopics": ["topic"] * 9},
    {"suggestedPrimaryCategory": "INVALID"}, {"suggestedImportanceScore": 101},
    {"suggestedImportanceScore": "65"}, {"extra": "invalid"},
])
def test_invalid_grounding_or_output_is_rejected(change):
    provider = service(lambda _: httpx.Response(200, json=envelope({**OUTPUT, **change})))
    with pytest.raises(EnrichmentProviderError) as error:
        provider.enrich(EnrichmentRequest(**ARTICLE))
    assert error.value.status_code == 502
    assert "fixture-secret" not in str(error.value)


@pytest.mark.parametrize("updates", [
    {"status": "incomplete"},
    {"usage": None}, {"id": ""},
    {"output": [{"type": "message", "content": [{"type": "refusal", "refusal": "secret"}]}]},
    {"output": [{"type": "message", "content": [{"type": "output_text", "text": "invalid"}]}]},
])
def test_invalid_response_envelope_is_rejected(updates):
    provider = service(lambda _: httpx.Response(200, json=envelope(**updates)))
    with pytest.raises(EnrichmentProviderError) as error:
        provider.enrich(EnrichmentRequest(**ARTICLE))
    assert error.value.status_code == 502


@pytest.mark.parametrize("status, expected", [(400, 503), (401, 503), (403, 503), (404, 503), (429, 502), (500, 502)])
def test_provider_error_is_sanitized_and_never_retried(status, expected):
    requests = []

    def handler(request):
        requests.append(request)
        return httpx.Response(status, json={"error": "fixture-secret private-body"})

    with pytest.raises(EnrichmentProviderError) as error:
        service(handler).enrich(EnrichmentRequest(**ARTICLE))
    assert error.value.status_code == expected
    assert "private-body" not in str(error.value)
    assert "fixture-secret" not in str(error.value)
    assert len(requests) == 1


def test_uncertain_timeout_is_not_retried():
    requests = []

    def handler(request):
        requests.append(request)
        raise httpx.ReadTimeout("fixture-secret", request=request)

    with pytest.raises(EnrichmentProviderError) as error:
        service(handler).enrich(EnrichmentRequest(**ARTICLE))
    assert error.value.status_code == 504
    assert len(requests) == 1


def test_real_mode_requires_key_without_network():
    provider = OpenAIEnrichmentService(EnrichmentSettings("openai", ""))
    with pytest.raises(EnrichmentProviderError) as error:
        provider.enrich(EnrichmentRequest(**ARTICLE))
    assert error.value.status_code == 503


def test_settings_default_to_mock_and_read_only_server_environment(monkeypatch):
    monkeypatch.delenv("SIGAK_ENRICHMENT_PROVIDER", raising=False)
    monkeypatch.delenv("OPENAI_API_KEY", raising=False)
    assert load_enrichment_settings().enrichment_provider == "mock"
    monkeypatch.setenv("SIGAK_ENRICHMENT_PROVIDER", " OPENAI ")
    monkeypatch.setenv("OPENAI_API_KEY", "fixture-secret")
    assert load_enrichment_settings() == EnrichmentSettings("openai", "fixture-secret")


def test_router_returns_real_provenance_and_sanitized_errors():
    app.dependency_overrides[get_enrichment_service] = lambda: service(
        lambda _: httpx.Response(200, json=envelope()))
    try:
        response = TestClient(app).post("/api/enrichment/article", json=ARTICLE)
        assert response.status_code == 200
        assert response.json()["providerResponseId"] == "resp_fixture"
        app.dependency_overrides[get_enrichment_service] = lambda: service(
            lambda _: httpx.Response(401, text="fixture-secret"))
        response = TestClient(app).post("/api/enrichment/article", json=ARTICLE)
        assert response.status_code == 503
        assert "fixture-secret" not in response.text
    finally:
        app.dependency_overrides.clear()


def test_oversized_metadata_rejects_before_any_provider_request():
    requests = []
    provider = service(lambda request: requests.append(request))
    article = {**ARTICLE, "title": "제목" * 20000}
    with pytest.raises(EnrichmentProviderError) as error:
        provider.enrich(EnrichmentRequest(**article))
    assert error.value.status_code == 422
    assert requests == []


def test_quote_from_discarded_tail_cannot_support_truncated_summary():
    article = {**ARTICLE, "rawContent": "앞부분 " * 20000 + "ONLY IN DISCARDED TAIL"}
    output = {**OUTPUT, "evidenceQuotes": ["ONLY IN DISCARDED TAIL"]}
    provider = service(lambda _: httpx.Response(200, json=envelope(output)))
    with pytest.raises(EnrichmentProviderError) as error:
        provider.enrich(EnrichmentRequest(**article))
    assert error.value.status_code == 502


def test_malformed_provider_json_returns_sanitized_error():
    provider = service(lambda _: httpx.Response(200, text="private-provider-body"))
    with pytest.raises(EnrichmentProviderError) as error:
        provider.enrich(EnrichmentRequest(**ARTICLE))
    assert error.value.status_code == 502
    assert "private-provider-body" not in str(error.value)


def test_network_failure_is_sanitized_without_retry():
    requests = []

    def handler(request):
        requests.append(request)
        raise httpx.ConnectError("fixture-secret", request=request)

    with pytest.raises(EnrichmentProviderError) as error:
        service(handler).enrich(EnrichmentRequest(**ARTICLE))
    assert error.value.status_code == 502
    assert "fixture-secret" not in str(error.value)
    assert len(requests) == 1


def test_unknown_provider_is_rejected_without_falling_back_to_mock():
    app.dependency_overrides[load_enrichment_settings] = lambda: EnrichmentSettings("invalid", "")
    try:
        response = TestClient(app).post("/api/enrichment/article", json=ARTICLE)
        assert response.status_code == 503
    finally:
        app.dependency_overrides.clear()


def test_router_rejects_unsupported_input_scope():
    app.dependency_overrides[load_enrichment_settings] = lambda: EnrichmentSettings()
    try:
        response = TestClient(app).post(
            "/api/enrichment/article", json={**ARTICLE, "inputScope": "FULL_ARTICLE"}
        )
        assert response.status_code == 422
    finally:
        app.dependency_overrides.clear()


def test_settings_do_not_expose_secret_when_rendered_for_diagnostics():
    assert "fixture-secret" not in repr(EnrichmentSettings("openai", "fixture-secret"))


def test_unexpected_provider_model_stops_as_configuration_failure():
    provider = service(lambda _: httpx.Response(200, json=envelope(model="gpt-6")))
    with pytest.raises(EnrichmentProviderError) as error:
        provider.enrich(EnrichmentRequest(**ARTICLE))
    assert error.value.status_code == 503


@pytest.mark.parametrize("usage", [
    {"input_tokens": 32769, "output_tokens": 1},
    {"input_tokens": 1, "output_tokens": 2049},
    {"input_tokens": -1, "output_tokens": 1},
    {"input_tokens": True, "output_tokens": 1},
])
def test_usage_violation_stops_backend_circuit_as_configuration_failure(usage):
    provider = service(lambda _: httpx.Response(200, json=envelope(usage=usage)))
    with pytest.raises(EnrichmentProviderError) as error:
        provider.enrich(EnrichmentRequest(**ARTICLE))
    assert error.value.status_code == 503
    assert "fixture-secret" not in str(error.value)


@pytest.mark.parametrize("error", [
    {"type": "insufficient_quota", "code": None},
    {"code": "credit_balance_exhausted"},
    {"code": "organization_usage_limit_exceeded"},
    {"code": "organization_spend_limit_exceeded"},
    {"code": "project_spend_limit_exceeded"},
    {"code": "billing_hard_limit_reached"},
])
def test_billing_limit_error_stops_backend_circuit_without_provider_body(error):
    requests = []

    def handler(request):
        requests.append(request)
        return httpx.Response(429, json={"error": {**error, "message": "fixture-secret"}})

    with pytest.raises(EnrichmentProviderError) as failure:
        service(handler).enrich(EnrichmentRequest(**ARTICLE))
    assert failure.value.status_code == 503
    assert "fixture-secret" not in str(failure.value)
    assert len(requests) == 1
