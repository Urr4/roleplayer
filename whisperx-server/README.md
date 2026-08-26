# WhisperX HTTP wrapper — run this on Stefans-PC (the GPU machine)

This folder is not built/used on the Raspberry Pi. Copy it to Stefans-PC and
run it there so `roleplayer`'s `ASR_URL` has an HTTP endpoint to call.
See `../docs/whisperx-setup.md` for full background.

## 1) Copy this folder to Stefans-PC

```bash
scp -r whisperx-server stefan@Stefans-PC:~/whisperx-server
```

(Adjust user/hostname as needed. Any transfer method works — USB stick, git, etc.)

## 2) Get a Hugging Face token (needed for pyannote diarization)

1. Create a free account at https://huggingface.co
2. Accept the gated model terms on BOTH pages (click "Agree and access
   repository" while logged in):
   - https://huggingface.co/pyannote/speaker-diarization-3.1
   - https://huggingface.co/pyannote/segmentation-3.0
3. Create a read-scoped token: https://huggingface.co/settings/tokens

## 3) Run it (Docker + NVIDIA GPU — recommended)

On Stefans-PC, requires NVIDIA drivers + NVIDIA Container Toolkit already installed:

```bash
cd whisperx-server
docker build -t whisperx-http .
docker run --gpus all -d --name whisperx-http --restart unless-stopped \
  -p 9090:9090 \
  -e HF_TOKEN=hf_xxx \
  -e WHISPERX_DEVICE=cuda \
  -e WHISPERX_COMPUTE_TYPE=float16 \
  whisperx-http
```

`--restart unless-stopped` keeps it running across reboots/crashes since this
is a long-lived service, not a one-off job.

## 4) Fallback: plain Python venv (no Docker/GPU passthrough)

```bash
cd whisperx-server
python -m venv .venv
source .venv/bin/activate   # Windows: .venv\Scripts\activate
pip install -r requirements.txt
export HF_TOKEN=hf_xxx
uvicorn app:app --host 0.0.0.0 --port 9090
```

## 5) Verify it's reachable

From Stefans-PC itself:
```bash
curl -s http://localhost:9090/docs -o /dev/null -w "%{http_code}\n"   # expect 200
```

From the Raspberry Pi (the Swarm manager that will call ASR_URL):
```bash
curl -s http://Stefans-PC:9090/docs -o /dev/null -w "%{http_code}\n"
```

If the hostname `Stefans-PC` doesn't resolve from the Pi, either:
- add a static entry to `/etc/hosts` on the Pi (`<PC-IP> Stefans-PC`), or
- use the PC's IP address directly as `ASR_URL` when deploying roleplayer,
  e.g. `ASR_URL=http://192.168.178.XX:9090 ./setup.sh` (or in `redeploy.sh`).

## 6) Point roleplayer at it

When running `setup.sh`/`redeploy.sh` on the Pi:

```bash
ASR_URL=http://Stefans-PC:9090 DISCORD_BOT_TOKEN=... ./setup.sh
```

Default model is `large-v3` (~10 GB VRAM). If VRAM is tight, override with
`ASR_MODEL=medium` or `ASR_MODEL=large-v3-turbo`.
