# Verified integration runtime

Python 3.11; PyTorch 2.6.0+cpu; torchvision 0.21.0; Ultralytics 8.4.21 (matches
checkpoint metadata). Locked checkpoint SHA is unchanged. Class order verified:
D00, D10, D20, D40, Repair. CPU smoke and container HTTP inference passed on a
synthetic gray image. No CUDA device available; GPU NOT_RUN. No accuracy/dataset
or cloud benchmark claimed. See backend/requirements-ai.txt,
scripts/qa/verify_ai.py and docs/integration/evidence/ai-cpu.txt.
