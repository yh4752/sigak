import json

import pytest

from rag_evaluation.artifacts import ArtifactWriter


def test_failed_serialization_preserves_previous_artifact(tmp_path):
    writer = ArtifactWriter(tmp_path, "run-one")
    writer.write_json("artifact.json", {"status": "original"})
    with pytest.raises(ValueError):
        writer.write_json("artifact.json", {"bad": float("nan")})
    payload = json.loads((tmp_path / "artifact.json").read_text())
    assert payload == {"runId": "run-one", "data": {"status": "original"}}


def test_writer_rejects_path_escape(tmp_path):
    writer = ArtifactWriter(tmp_path, "run-one")
    with pytest.raises(ValueError, match="filename"):
        writer.write_text("../private.md", "no")
