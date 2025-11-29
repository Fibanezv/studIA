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

````markdown
OCR Manual (DEPRECATED)

Nota: La extracción de texto (OCR / PdfRenderer) fue eliminada del módulo. Cualquier prueba o flujo de validación de OCR debe realizarse fuera de este repositorio o en una rama anterior donde esa funcionalidad existía.

Si necesitas que integremos una nueva implementación de extracción (por ejemplo, un servicio nativo diferente, una función en backend o una librería de ML actualizada), dime cuál prefieres y preparo el parche.

````