# Solve It Bro

Snap a photo of any homework question and get a step-by-step explanation in English or Hindi. Android app (Kotlin, Jetpack Compose) for school students.

## Layout

| Path | What it is |
|---|---|
| `app/` | Android app: Kotlin, Jetpack Compose, CameraX, ML Kit OCR, Room |
| `backend/` | Small Node proxy that calls Claude, so the AI API key never ships in the APK |

## Core flow

1. Camera or gallery capture, cropped to the question
2. On-device text extraction with ML Kit (Latin and Devanagari)
3. The backend asks Claude (image + OCR hint) for a step-by-step solution
4. Result screen with "explain simpler" and follow-up chat
5. History of solved questions with bookmarks

Status: capture, crop, OCR, solving and the result screen with "explain simpler" work end to end. Follow-up chat and history come next.

## Monetization

Free daily solves (5 by default) with ads, plus a subscription for unlimited solves. The backend enforces the daily limit per device; the app shows the count. Ads and Play Billing are stubbed behind `Entitlements` interfaces for now.

## Running the app

Open the repo in Android Studio and run the `app` configuration. Debug builds talk to `http://10.0.2.2:8787`, which is your computer's localhost as seen from the emulator.

On a real phone, the phone must reach the backend over Wi-Fi. Add your computer's LAN address to `local.properties` (not committed), then rebuild:

```
solveitbro.apiBaseUrl=http://192.168.1.20:8787
```

Find the address with `ipconfig` (Windows) or `ifconfig` (Mac/Linux), keep phone and computer on the same Wi-Fi, and allow Node through the firewall if Windows asks.

## Running the backend

```
cd backend
cp .env.example .env   # then set ANTHROPIC_API_KEY
npm install
npm run dev
```

`POST /v1/solve` with header `X-Device-Id` and a JSON body:

```json
{ "image": "<base64>", "mediaType": "image/jpeg", "language": "hi", "mode": "standard", "grade": 8, "ocrText": "optional" }
```

It returns `{ status, subject, question, steps: [{ title, explanation }], finalAnswer, tip }`. Use `"mode": "simpler"` for the "explain simpler" button. `npm test` runs the backend tests.
