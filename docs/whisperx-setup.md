# WhisperX setup on Stefans-PC

This project expects a separate HTTP speech-to-text service reachable via `ASR_URL` (default `http://localhost:9090` locally, typically `http://Stefans-PC:9090` from Docker Swarm). The pattern mirrors `OLLAMA_URL` from the sibling `mealplaner` project: the app stays configurable, while the GPU-heavy AI service runs on Stefans-PC.

## 1) Prerequisites

Primary option: **Docker with NVIDIA GPU support**

- NVIDIA GPU drivers installed
- Docker installed
- NVIDIA Container Toolkit / CUDA-enabled Docker runtime installed
- A free Hugging Face account and access token for pyannote diarization

Fallback option: **plain Python virtualenv**

- Python 3.10+ (matching WhisperX support)
- FFmpeg installed
- CUDA/PyTorch environment working on the PC

Docker is preferred because it keeps the setup reproducible and isolated.

## 2) Minimal HTTP wrapper for WhisperX

WhisperX is a Python library/CLI, not an HTTP server by itself. Run it behind a tiny FastAPI wrapper that exposes `POST /transcribe` and returns exactly the JSON consumed by `WhisperXClient.java`.

### `requirements.txt`

```txt
whisperx
fastapi
uvicorn
python-multipart
```

### `app.py`

```python
import os
from pathlib import Path

from fastapi import FastAPI, File, Form, HTTPException, UploadFile
import whisperx

app = FastAPI()

DEVICE = os.getenv("WHISPERX_DEVICE", "cuda")
COMPUTE_TYPE = os.getenv("WHISPERX_COMPUTE_TYPE", "float16")
HF_TOKEN = os.getenv("HF_TOKEN")

if not HF_TOKEN:
    raise RuntimeError("HF_TOKEN is required for pyannote diarization.")

_transcribe_models = {}
_align_models = {}
_diarization_pipeline = None


def get_transcribe_model(model_name: str):
    if model_name not in _transcribe_models:
        _transcribe_models[model_name] = whisperx.load_model(
            model_name,
            DEVICE,
            compute_type=COMPUTE_TYPE,
            language="de",
        )
    return _transcribe_models[model_name]


def get_align_model(language_code: str):
    if language_code not in _align_models:
        model_a, metadata = whisperx.load_align_model(
            language_code=language_code,
            device=DEVICE,
        )
        _align_models[language_code] = (model_a, metadata)
    return _align_models[language_code]


def get_diarization_pipeline():
    global _diarization_pipeline
    if _diarization_pipeline is None:
        try:
            _diarization_pipeline = whisperx.diarize.DiarizationPipeline(
                use_auth_token=HF_TOKEN,
                device=DEVICE,
            )
        except Exception as exc:
            # A 401/403 here almost always means the HF account hasn't accepted
            # the gated model terms yet (or accepted only one of the two models).
            raise RuntimeError(
                "Failed to load the pyannote diarization pipeline. This is usually "
                "caused by not having accepted the gated model terms on Hugging Face. "
                "Log in at huggingface.co, then visit BOTH of these pages and click "
                "'Agree and access repository': "
                "https://huggingface.co/pyannote/speaker-diarization-3.1 and "
                "https://huggingface.co/pyannote/segmentation-3.0 "
                "(the second one is a hidden dependency of the first and is the most "
                "common thing people miss). Also double-check HF_TOKEN is a valid, "
                f"non-expired token. Original error: {exc}"
            ) from exc
    return _diarization_pipeline


@app.post("/transcribe")
async def transcribe(
    file: UploadFile = File(...),
    language: str = Form("de"),
    diarize: bool = Form(True),
    model: str = Form("large-v3"),
):
    suffix = Path(file.filename or "audio.bin").suffix or ".bin"
    temp_path = Path("incoming-audio") / f"upload{suffix}"
    temp_path.parent.mkdir(parents=True, exist_ok=True)

    try:
        temp_path.write_bytes(await file.read())
        audio = whisperx.load_audio(str(temp_path))

        transcribe_model = get_transcribe_model(model)
        result = transcribe_model.transcribe(audio, language=language)

        align_model, align_metadata = get_align_model(language)
        aligned = whisperx.align(
            result["segments"],
            align_model,
            align_metadata,
            audio,
            DEVICE,
        )

        segments = aligned["segments"]

        if diarize:
            diarization = get_diarization_pipeline()(audio)
            segments = whisperx.assign_word_speakers(diarization, aligned)["segments"]

        response_segments = []
        for index, segment in enumerate(segments):
            response_segments.append(
                {
                    "speaker": segment.get("speaker", f"SPEAKER_{index:02d}"),
                    "start": float(segment.get("start", 0.0)),
                    "end": float(segment.get("end", 0.0)),
                    "text": str(segment.get("text", "")).strip(),
                }
            )

        return {"segments": response_segments}
    except Exception as exc:
        raise HTTPException(status_code=500, detail=str(exc)) from exc
    finally:
        if temp_path.exists():
            temp_path.unlink()
```

## 3) Run it with Docker (recommended)

Example `Dockerfile`:

```dockerfile
FROM python:3.11-slim

WORKDIR /app

RUN apt-get update && apt-get install -y ffmpeg && rm -rf /var/lib/apt/lists/*

COPY requirements.txt app.py ./
RUN pip install --no-cache-dir -r requirements.txt

EXPOSE 9090

CMD ["uvicorn", "app:app", "--host", "0.0.0.0", "--port", "9090"]
```

Example start command:

```bash
docker run --gpus all --rm -p 9090:9090 \
  -e HF_TOKEN=hf_xxx \
  -e WHISPERX_DEVICE=cuda \
  -e WHISPERX_COMPUTE_TYPE=float16 \
  whisperx-http
```

If Docker GPU support is not available, run the same `app.py` inside a Python venv instead:

```bash
python -m venv .venv
source .venv/bin/activate
pip install -r requirements.txt
uvicorn app:app --host 0.0.0.0 --port 9090
```

## 4) Hugging Face / pyannote diarization

Speaker diarization uses **gated** pyannote models. Gated models don't show up via the normal search box the way public models do — you must open their model pages directly and accept the license terms while logged in. There are **two** separate gated models to accept (the second is a hidden dependency of the first, and forgetting it is the most common cause of a 401/403 error even after accepting the first one):

1. Create a free Hugging Face account and log in.
2. Open **https://huggingface.co/pyannote/speaker-diarization-3.1** and click **"Agree and access repository"** (instant, automated — not a manual review, but the button/form can be easy to miss on the page).
3. Also open **https://huggingface.co/pyannote/segmentation-3.0** and accept its terms the same way.
4. Generate a free access token at **https://huggingface.co/settings/tokens** → "New token" (a **read**-scoped token is enough).
5. Authenticate with `huggingface-cli login` **or** pass the token via `HF_TOKEN`.

Both acceptances are tied to your HF *account*, not the token, so accept the model terms first, then generate/use the token. If diarization still fails after this, `app.py`'s `get_diarization_pipeline()` now raises a descriptive error pointing back to these two URLs — check the wrapper service logs / HTTP 500 response body for the exact message.

There is **no monetary cost** for the Hugging Face account, token, WhisperX, or pyannote usage in this setup.

## 5) Connect roleplayer to Stefans-PC

`roleplayer/docker-compose.yml` now follows the same pattern as mealplaner’s `OLLAMA_URL`:

```yaml
ASR_URL: ${ASR_URL:-http://Stefans-PC:9090}
```

Set `ASR_URL` to whatever address/port the Raspberry Pi Swarm can actually reach. Port `9090` is the suggested default.

## 6) Model sizing notes

- Default model: `large-v3`
- Default language: `de`
- `large-v3` typically needs roughly **~10 GB VRAM**
- If VRAM is tight, use `medium` or `large-v3-turbo` instead; both are still reasonable choices for German audio

If you change the model on Stefans-PC, you can also override it in roleplayer with `ASR_MODEL`.
