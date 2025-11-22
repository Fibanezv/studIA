OCR Manual Test Instructions

Purpose
- Verify PDF OCR extraction flow (PdfRenderer + Play Services Vision) in the app.
- Validate that the app extracts text from both searchable PDFs and scanned PDFs.

Prerequisites
- Android Studio or adb + an emulator/device with API >= 24.
- The app built from the workspace (assembleDebug) and installed on the device/emulator.
- Two sample PDFs in a place accessible to the emulator or device (e.g., push to Downloads):
  - `sample_text.pdf` — a PDF generated from text (contains selectable text).
  - `sample_scanned.pdf` — a scanned PDF (image-only pages).

Steps
1. Build and install the debug APK:

```powershell
cd c:\Users\Fran\Desktop\EstudiaApp
.\gradlew.bat assembleDebug -x lint
adb install -r app\build\outputs\apk\debug\app-debug.apk
```

2. Push sample PDFs to emulator/device `Downloads` (optional). Example using adb:

```powershell
adb push C:\path\to\sample_text.pdf /sdcard/Download/sample_text.pdf
adb push C:\path\to\sample_scanned.pdf /sdcard/Download/sample_scanned.pdf
```

3. Launch the app and open the `Upload` flow.
4. Tap the file picker and select `sample_text.pdf` (use "Files" -> Downloads on emulator).
5. Observe the filename shown in the UI; press "Get Study Plan".
6. Watch the processing overlay. If you have logcat open, filter logs for `DocumentExtractor`:

```powershell
adb logcat --buffer main -v time | Select-String "DocumentExtractor|StudyPlanWorker|UploadActivity"
```

7. Expectation for `sample_text.pdf`:
- OCR flow will run; because the PDF contains text it may extract (if the text layer is absent, OCR will still recognize).
- The worker should proceed to call OpenAI (if API key present) and upload the plan; otherwise you will get an error message shown by the app (the worker attaches error text to the output data).

8. Test `sample_scanned.pdf`:
- Repeat steps with `sample_scanned.pdf`.
- Expect OCR to extract text from images; it may take longer depending on page count and device.
- If OCR fails due to memory/time, the worker will return an error message; capture the logs for `PdfRenderer/OCR fallback failed` or `OOM` entries.

Notes and troubleshooting
- The extractor limits processing to 50 pages to avoid extremely long runs; large PDFs may be truncated.
- If you see OOM warnings in logcat, lower `HIGH_MAX` and `THUMB_MAX` in `DocumentExtractor.java` or test on a device with more memory.
- If worker fails with "OpenAI API key not available", ensure Remote Config has been fetched and the key persisted into `OpenAiKeyStore` or test with a local key via Remote Config.
- Check permission issues: when picking files with SAF, ensure the app retains read permission; re-select the file if permission lost.

Collecting artifacts
- On failure, collect logcat output and the PDF used. Save logcat to a file:

```powershell
adb logcat -d > logcat_output.txt
```

If you want, I can also add a small instrumentation/test harness that drives the UI automatically using Espresso or UI Automator; tell me if you want that next.