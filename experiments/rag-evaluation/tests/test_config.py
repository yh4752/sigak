import pytest

from rag_evaluation.config import EvaluationSettings


def test_settings_load_deterministic_defaults_without_openai_key(monkeypatch):
    monkeypatch.delenv("OPENAI_API_KEY", raising=False)
    monkeypatch.setenv("SIGAK_AI_SERVER_URL", "http://localhost:8000")

    settings = EvaluationSettings.from_env()

    assert settings.ai_server_url == "http://localhost:8000"
    assert settings.chunk_tokenizer == "o200k_base"
    assert settings.top_k == 5
    assert settings.candidate_limit == 10
    assert settings.rrf_k == 60
    assert settings.openai_api_key is None


def test_settings_require_distinct_generator_and_judge_for_full_run(monkeypatch):
    monkeypatch.setenv("OPENAI_API_KEY", "test-key")
    monkeypatch.setenv("SIGAK_RAG_RERANKER_MODEL", "reranker-model")
    monkeypatch.setenv("SIGAK_RAG_GENERATOR_MODEL", "same-model")
    monkeypatch.setenv("SIGAK_RAG_JUDGE_MODEL", "same-model")

    settings = EvaluationSettings.from_env()

    with pytest.raises(ValueError, match="generator and judge models must differ"):
        settings.validate_full_run()


def test_settings_representation_hides_api_key(monkeypatch):
    monkeypatch.setenv("OPENAI_API_KEY", "test-private-value")
    assert "test-private-value" not in repr(EvaluationSettings.from_env())
