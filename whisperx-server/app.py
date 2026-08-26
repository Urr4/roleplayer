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
