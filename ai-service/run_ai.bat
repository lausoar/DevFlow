@echo off
cd /d I:\DevFlow\ai-service
set PYTHONPATH=.
I:\DevFlow\ai-service\.venv\Scripts\python.exe -m uvicorn app.main:app --port 8000
