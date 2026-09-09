import hashlib
import math
from typing import Any


class Matcher:
    def __init__(self, model_name: str, mongo: Any = None):
        self.model_name = model_name
        self.mongo = mongo
        self._model = None

    def _load(self):
        if self._model is None:
            from sentence_transformers import SentenceTransformer
            self._model = SentenceTransformer(self.model_name)
        return self._model

    def score(self, tender_text: str, profile_segments: list[str]) -> tuple[float, str]:
        if not profile_segments:
            return 0.0, ""
        model = self._load()
        key = hashlib.sha256((self.model_name + "\n" + tender_text + "\n" + "\n".join(profile_segments)).encode()).hexdigest()
        cached = self.mongo.find_one({"key": key}) if self.mongo is not None else None
        if cached:
            return float(cached["score"]), cached["segment"]
        vectors = model.encode([tender_text, *profile_segments], normalize_embeddings=True)
        best_index, best_score = max(enumerate(vectors[1:]), key=lambda item: sum(a * b for a, b in zip(vectors[0], item[1])))
        score = float(sum(a * b for a, b in zip(vectors[0], vectors[best_index + 1])))
        segment = profile_segments[best_index]
        if self.mongo is not None:
            self.mongo.replace_one({"key": key}, {"key": key, "model": self.model_name, "score": score, "segment": segment}, upsert=True)
        return score, segment
