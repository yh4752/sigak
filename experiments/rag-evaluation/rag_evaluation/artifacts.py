from __future__ import annotations

import json
import os
import tempfile
from pathlib import Path


class ArtifactWriter:
    def __init__(self, output_dir: Path, run_id: str):
        self.output_dir = output_dir
        self.run_id = run_id
        output_dir.mkdir(parents=True, exist_ok=True)

    def write_json(self, filename: str, value):
        content = json.dumps({"runId": self.run_id, "data": value}, ensure_ascii=False, indent=2, allow_nan=False) + "\n"
        return self.write_text(filename, content)

    def write_text(self, filename: str, content: str):
        if Path(filename).name != filename or filename in {"", ".", ".."}:
            raise ValueError("artifact filename must be a basename")
        path = self.output_dir / filename
        temporary = None
        try:
            with tempfile.NamedTemporaryFile(mode="w", encoding="utf-8", dir=self.output_dir, delete=False) as stream:
                temporary = Path(stream.name)
                stream.write(content)
                stream.flush()
                os.fsync(stream.fileno())
            temporary.replace(path)
        finally:
            if temporary is not None:
                temporary.unlink(missing_ok=True)
        return path
