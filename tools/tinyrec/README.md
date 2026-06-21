# PremiumDeck TinyRec Trainer

This folder contains the reproducible training/export path for `PremiumDeck TinyRec v1`.

The Android app expects a TensorFlow Lite file named:

```text
premiumdeck_tinyrec_v1.tflite
```

The model has two inputs and one output:

```text
codebook_ids: int32[1, 4]
user_vector: float32[1, 64]
score: float32[1, 1]
```

## Data Contract

Training uses JSONL files so exported app events can be transformed without coupling the trainer to Room internals.

Candidate rows:

```json
{"item_id":"track-1","title":"Track","artist":"Artist","album":"Album","genre":"Soul","source":"PremiumDeck","quality_score":0.92,"codebook_ids":[12,44,98,201]}
```

Behavior event rows:

```json
{"user_id":"local-user","event_type":"full_listen","item_id":"track-1","title":"Track","artist":"Artist","album":"Album","genre":"Soul","occurred_at_ms":1780140000000}
```

Supported event aliases match the app weights:

```text
full_listen, track_completed, like_favorite, playlist_add, repeat_play,
search_result_click, album_or_artist_open, skip_under_30_seconds,
skip_30_to_60_seconds, dislike_hide, track_removed_from_playlist
```

## Train And Export

Install dependencies into a local environment:

```powershell
python -m venv .venv-tinyrec
.\.venv-tinyrec\Scripts\python.exe -m pip install -r tools\tinyrec\requirements.txt
```

Train from real exported events:

```powershell
.\.venv-tinyrec\Scripts\python.exe tools\tinyrec\train_tinyrec.py `
  --events data\premiumdeck_events.jsonl `
  --candidates data\premiumdeck_candidates.jsonl `
  --out app\src\main\assets\premiumdeck_tinyrec_v1.tflite
```

For pipeline validation only, create a clearly marked bootstrap testing model:

```powershell
.\.venv-tinyrec\Scripts\python.exe tools\tinyrec\train_tinyrec.py `
  --bootstrap-sample `
  --out app\src\main\assets\premiumdeck_tinyrec_v1.tflite
```

Do not treat a bootstrap model as production quality. It proves that the Android LiteRT path works; useful recommendations require real PremiumDeck/local listening data.
