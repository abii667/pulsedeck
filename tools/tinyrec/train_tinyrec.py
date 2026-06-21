#!/usr/bin/env python3
"""Train and export PremiumDeck TinyRec v1 as TensorFlow Lite.

This script intentionally keeps the model tiny and compatible with the
Android runner:

  user_vector: float32[1, 64]
  codebook_ids: int32[1, 4]
  score: float32[1, 1]

Real quality depends on real listening/search/playlist events. The
--bootstrap-sample option is only for validating the export and Android load
path; it is not a production recommender dataset.
"""

from __future__ import annotations

import argparse
import hashlib
import json
import math
import random
import shutil
import tempfile
import time
from dataclasses import dataclass
from pathlib import Path
from typing import Iterable

try:
    import numpy as np
except ImportError as exc:  # pragma: no cover - dependency guard
    raise SystemExit(
        "Missing numpy. Install with: python -m pip install -r tools/tinyrec/requirements.txt"
    ) from exc

try:
    import tensorflow as tf
except ImportError as exc:  # pragma: no cover - dependency guard
    raise SystemExit(
        "Missing TensorFlow. Install with: python -m pip install -r tools/tinyrec/requirements.txt"
    ) from exc


USER_VECTOR_DIM = 64
ITEM_VECTOR_DIM = 64
NUM_CODEBOOKS = 4
CODEBOOK_SIZE = 256
MODEL_NAME = "PremiumDeck TinyRec v1"
MODEL_ASSET_NAME = "premiumdeck_tinyrec_v1.tflite"

EVENT_WEIGHTS = {
    "full_listen": 1.0,
    "track_completed": 1.0,
    "like_favorite": 1.2,
    "playlist_add": 1.1,
    "track_added_to_playlist": 1.1,
    "repeat_play": 0.9,
    "search_result_click": 0.7,
    "album_or_artist_open": 0.3,
    "album_opened": 0.3,
    "artist_opened": 0.3,
    "skip_under_30_seconds": -0.9,
    "skip_30_to_60_seconds": -0.5,
    "dislike_hide": -1.2,
    "track_removed_from_playlist": -0.7,
}


@dataclass(frozen=True)
class Candidate:
    item_id: str
    title: str
    artist: str
    album: str
    genre: str
    source: str
    quality_score: float
    codebook_ids: tuple[int, int, int, int]


@dataclass(frozen=True)
class Event:
    user_id: str
    event_type: str
    item_id: str
    title: str
    artist: str
    album: str
    genre: str
    occurred_at_ms: int


def main() -> None:
    args = parse_args()
    random.seed(args.seed)
    np.random.seed(args.seed)
    tf.random.set_seed(args.seed)

    if args.bootstrap_sample:
        candidates, events = bootstrap_data(args.seed)
    else:
        if not args.events or not args.candidates:
            raise SystemExit("--events and --candidates are required unless --bootstrap-sample is set")
        candidates = read_candidates(args.candidates)
        events = read_events(args.events)

    if len(candidates) < 2:
        raise SystemExit("Need at least two candidates to train TinyRec.")
    if len(events) < 4:
        raise SystemExit("Need at least four behavior events to train TinyRec.")

    dataset = build_training_examples(candidates, events, negatives_per_positive=args.negatives)
    model = build_model()
    model.fit(
        {"user_vector": dataset["user_vector"], "codebook_ids": dataset["codebook_ids"]},
        dataset["label"],
        epochs=args.epochs,
        batch_size=args.batch_size,
        validation_split=0.12 if len(dataset["label"]) >= 64 else 0.0,
        verbose=2,
    )

    tflite_bytes = convert_to_tflite(model)
    args.out.parent.mkdir(parents=True, exist_ok=True)
    args.out.write_bytes(tflite_bytes)

    manifest = {
        "model_name": MODEL_NAME,
        "asset_name": MODEL_ASSET_NAME,
        "created_at_ms": int(time.time() * 1000),
        "bootstrap_sample": bool(args.bootstrap_sample),
        "candidate_count": len(candidates),
        "event_count": len(events),
        "training_example_count": int(len(dataset["label"])),
        "input_order": ["codebook_ids", "user_vector"],
        "input_shapes": {
            "codebook_ids": [1, NUM_CODEBOOKS],
            "user_vector": [1, USER_VECTOR_DIM],
        },
        "output_shape": [1, 1],
        "size_bytes": len(tflite_bytes),
        "warning": "Bootstrap models validate the pipeline only; train with real PremiumDeck events before production use."
        if args.bootstrap_sample
        else "",
    }
    args.out.with_suffix(".manifest.json").write_text(json.dumps(manifest, indent=2), encoding="utf-8")
    print(f"Wrote {args.out} ({len(tflite_bytes)} bytes)")
    print(f"Wrote {args.out.with_suffix('.manifest.json')}")


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser()
    parser.add_argument("--events", type=Path, help="JSONL behavior event file")
    parser.add_argument("--candidates", type=Path, help="JSONL candidate metadata file")
    parser.add_argument("--out", type=Path, default=Path("build/tinyrec") / MODEL_ASSET_NAME)
    parser.add_argument("--bootstrap-sample", action="store_true")
    parser.add_argument("--epochs", type=int, default=8)
    parser.add_argument("--batch-size", type=int, default=64)
    parser.add_argument("--negatives", type=int, default=3)
    parser.add_argument("--seed", type=int, default=42)
    return parser.parse_args()


def read_jsonl(path: Path) -> Iterable[dict]:
    with path.open("r", encoding="utf-8") as handle:
        for line_number, line in enumerate(handle, start=1):
            stripped = line.strip()
            if not stripped:
                continue
            try:
                yield json.loads(stripped)
            except json.JSONDecodeError as exc:
                raise SystemExit(f"Invalid JSON in {path}:{line_number}: {exc}") from exc


def read_candidates(path: Path) -> list[Candidate]:
    rows = []
    for raw in read_jsonl(path):
        item_id = str(raw.get("item_id") or raw.get("id") or "").strip()
        if not item_id:
            continue
        title = str(raw.get("title") or item_id)
        artist = str(raw.get("artist") or "")
        album = str(raw.get("album") or "")
        genre = str(raw.get("genre") or "")
        codes = raw.get("codebook_ids") or stable_codebook_ids(item_id, title, artist, album, genre)
        if len(codes) != NUM_CODEBOOKS:
            codes = stable_codebook_ids(item_id, title, artist, album, genre)
        rows.append(
            Candidate(
                item_id=item_id,
                title=title,
                artist=artist,
                album=album,
                genre=genre,
                source=str(raw.get("source") or "LocalLibrary"),
                quality_score=float(raw.get("quality_score") or raw.get("qualityScore") or 0.5),
                codebook_ids=tuple(int(value) % CODEBOOK_SIZE for value in codes),
            )
        )
    return rows


def read_events(path: Path) -> list[Event]:
    rows = []
    now_ms = int(time.time() * 1000)
    for raw in read_jsonl(path):
        item_id = str(raw.get("item_id") or raw.get("itemId") or "").strip()
        if not item_id:
            continue
        event_type = normalize_event_type(str(raw.get("event_type") or raw.get("type") or ""))
        if event_type not in EVENT_WEIGHTS:
            continue
        rows.append(
            Event(
                user_id=str(raw.get("user_id") or raw.get("userId") or "local-user"),
                event_type=event_type,
                item_id=item_id,
                title=str(raw.get("title") or item_id),
                artist=str(raw.get("artist") or ""),
                album=str(raw.get("album") or ""),
                genre=str(raw.get("genre") or ""),
                occurred_at_ms=int(raw.get("occurred_at_ms") or raw.get("occurredAtMillis") or now_ms),
            )
        )
    return rows


def build_training_examples(
    candidates: list[Candidate],
    events: list[Event],
    negatives_per_positive: int,
) -> dict[str, np.ndarray]:
    candidates_by_id = {candidate.item_id: candidate for candidate in candidates}
    all_ids = list(candidates_by_id.keys())
    user_events: dict[str, list[Event]] = {}
    for event in sorted(events, key=lambda row: row.occurred_at_ms):
        user_events.setdefault(event.user_id, []).append(event)

    user_vectors = []
    codebook_ids = []
    labels = []
    for user_id, rows in user_events.items():
        profile = np.zeros((USER_VECTOR_DIM,), dtype=np.float32)
        liked_ids: set[str] = set()
        disliked_ids: set[str] = set()
        for event in rows:
            weight = EVENT_WEIGHTS[event.event_type]
            profile = update_profile(profile, event, weight)
            candidate = candidates_by_id.get(event.item_id) or event_as_candidate(event)
            if weight > 0:
                liked_ids.add(event.item_id)
                label = 1.0
            else:
                disliked_ids.add(event.item_id)
                label = 0.0
            user_vectors.append(profile.copy())
            codebook_ids.append(candidate.codebook_ids)
            labels.append(label)

            negative_pool = [item_id for item_id in all_ids if item_id not in liked_ids and item_id != event.item_id]
            random.shuffle(negative_pool)
            for negative_id in negative_pool[:negatives_per_positive]:
                negative = candidates_by_id[negative_id]
                negative_label = 0.0 if negative_id in disliked_ids else 0.08
                user_vectors.append(profile.copy())
                codebook_ids.append(negative.codebook_ids)
                labels.append(negative_label)

    return {
        "user_vector": np.asarray(user_vectors, dtype=np.float32),
        "codebook_ids": np.asarray(codebook_ids, dtype=np.int32),
        "label": np.asarray(labels, dtype=np.float32),
    }


def build_model() -> tf.keras.Model:
    user_input = tf.keras.Input(shape=(USER_VECTOR_DIM,), dtype=tf.float32, name="user_vector")
    code_input = tf.keras.Input(shape=(NUM_CODEBOOKS,), dtype=tf.int32, name="codebook_ids")

    embeddings = []
    for index in range(NUM_CODEBOOKS):
        code_slice = tf.keras.layers.Lambda(lambda values, i=index: values[:, i], name=f"codebook_{index}_id")(code_input)
        embedding = tf.keras.layers.Embedding(
            input_dim=CODEBOOK_SIZE,
            output_dim=ITEM_VECTOR_DIM,
            name=f"codebook_{index}",
        )(code_slice)
        embeddings.append(embedding)

    item_vector = tf.keras.layers.Average(name="item_vector")(embeddings)
    interaction = tf.keras.layers.Multiply(name="user_item_interaction")([user_input, item_vector])
    merged = tf.keras.layers.Concatenate(name="rank_features")([user_input, item_vector, interaction])
    hidden = tf.keras.layers.Dense(48, activation="relu", name="rank_dense")(merged)
    hidden = tf.keras.layers.Dropout(0.08, name="rank_dropout")(hidden)
    output = tf.keras.layers.Dense(1, activation="sigmoid", name="score")(hidden)
    model = tf.keras.Model(inputs={"user_vector": user_input, "codebook_ids": code_input}, outputs=output)
    model.compile(
        optimizer=tf.keras.optimizers.Adam(learning_rate=0.002),
        loss=tf.keras.losses.BinaryCrossentropy(),
        metrics=[tf.keras.metrics.AUC(name="auc")],
    )
    return model


def convert_to_tflite(model: tf.keras.Model) -> bytes:
    saved_model_dir = Path(tempfile.mkdtemp(prefix="premiumdeck_tinyrec_saved_model_"))
    try:
        model.export(saved_model_dir)
        converter = tf.lite.TFLiteConverter.from_saved_model(str(saved_model_dir))
        converter.optimizations = [tf.lite.Optimize.DEFAULT]
        return converter.convert()
    finally:
        shutil.rmtree(saved_model_dir, ignore_errors=True)


def update_profile(profile: np.ndarray, event: Event, weight: float) -> np.ndarray:
    event_vector = feature_vector(event.item_id, event.title, event.artist, event.album, event.genre)
    signed = event_vector if weight >= 0 else -event_vector
    alpha = min(max(0.08 + abs(weight) * 0.05, 0.04), 0.18)
    next_profile = profile * (1.0 - alpha) + signed * alpha
    return normalize_vector(next_profile)


def feature_vector(*parts: str) -> np.ndarray:
    vector = np.zeros((USER_VECTOR_DIM,), dtype=np.float32)
    for part_index, part in enumerate(parts):
        tokens = affinity_key(part).split()
        if not tokens:
            continue
        for token in tokens:
            digest = hashlib.sha256(f"{part_index}:{token}".encode("utf-8")).digest()
            index = digest[0] % USER_VECTOR_DIM
            sign = 1.0 if digest[1] % 2 == 0 else -1.0
            vector[index] += sign
    return normalize_vector(vector)


def normalize_vector(vector: np.ndarray) -> np.ndarray:
    magnitude = float(np.linalg.norm(vector))
    if magnitude <= 0.0001:
        return vector.astype(np.float32)
    return (vector / magnitude).astype(np.float32)


def stable_codebook_ids(*parts: str) -> tuple[int, int, int, int]:
    digest = hashlib.sha256("|".join(parts).encode("utf-8")).digest()
    return tuple(int(digest[index]) for index in range(NUM_CODEBOOKS))


def event_as_candidate(event: Event) -> Candidate:
    return Candidate(
        item_id=event.item_id,
        title=event.title,
        artist=event.artist,
        album=event.album,
        genre=event.genre,
        source="LocalLibrary",
        quality_score=0.5,
        codebook_ids=stable_codebook_ids(event.item_id, event.title, event.artist, event.album, event.genre),
    )


def normalize_event_type(raw: str) -> str:
    text = affinity_key(raw).replace(" ", "_")
    aliases = {
        "trackcompleted": "track_completed",
        "track_completed": "track_completed",
        "likefavorite": "like_favorite",
        "like_favorite": "like_favorite",
        "trackaddedtoplaylist": "track_added_to_playlist",
        "track_added_to_playlist": "track_added_to_playlist",
        "searchresultclicked": "search_result_click",
        "search_result_clicked": "search_result_click",
        "albumopened": "album_opened",
        "artistopened": "artist_opened",
        "dislikehide": "dislike_hide",
        "trackremovedfromplaylist": "track_removed_from_playlist",
    }
    return aliases.get(text, text)


def affinity_key(raw: str) -> str:
    return "".join(ch.lower() if ch.isalnum() else " " for ch in raw).strip()


def bootstrap_data(seed: int) -> tuple[list[Candidate], list[Event]]:
    random.seed(seed)
    artists = [
        ("Nina Vale", "Soul"),
        ("Orbit House", "Ambient"),
        ("Static Gray", "Noise"),
        ("Deck Lab", "Electronic"),
        ("Mena Route", "Pop"),
        ("Late Signal", "Jazz"),
        ("Aster Route", "Chill Soul"),
        ("Northline", "Acoustic"),
    ]
    candidates = []
    for index in range(256):
        artist, genre = artists[index % len(artists)]
        item_id = f"bootstrap-{index:03d}"
        title = f"{genre} Sketch {index:03d}"
        candidates.append(
            Candidate(
                item_id=item_id,
                title=title,
                artist=artist,
                album=f"{artist} Sessions",
                genre=genre,
                source="PremiumDeck" if index % 3 == 0 else "LocalLibrary",
                quality_score=0.9 if index % 3 == 0 else 0.62,
                codebook_ids=stable_codebook_ids(item_id, title, artist, genre),
            )
        )

    events = []
    now_ms = int(time.time() * 1000)
    positive_artists = {"Nina Vale", "Orbit House", "Aster Route", "Northline"}
    for user_index in range(24):
        user_id = f"bootstrap-user-{user_index:02d}"
        for event_index in range(80):
            candidate = random.choice(candidates)
            positive = candidate.artist in positive_artists and random.random() > 0.15
            event_type = random.choice(["full_listen", "like_favorite", "playlist_add", "repeat_play"]) if positive else random.choice(
                ["skip_under_30_seconds", "dislike_hide", "skip_30_to_60_seconds"]
            )
            events.append(
                Event(
                    user_id=user_id,
                    event_type=event_type,
                    item_id=candidate.item_id,
                    title=candidate.title,
                    artist=candidate.artist,
                    album=candidate.album,
                    genre=candidate.genre,
                    occurred_at_ms=now_ms - (80 - event_index) * 60_000,
                )
            )
    return candidates, events


if __name__ == "__main__":
    main()
