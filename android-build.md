# Android build unlock (a-b / a-d)

Windows и рабочий стол часто держат файлы в `app\build` и в корневом `build`. Gradle тогда падает с `Unable to delete directory`.

Репозиторий — одно приложение Tasks. Gradle-корень здесь, модуль называется `app`. Папок `android\` и `calculator` нет.

Выполнить этот файл целиком:

- человек написал **a-b** или **a-d** (или просит подготовить build / sync);
- агент менял исходники приложения и человеку снова нужна Sync Project или сборка APK.

После шагов сказать, что можно собирать.

Не убивать Android Studio (`studio64`). Можно снять её `fsnotifier`. Не трогать исходники и `design/`. Не использовать `robocopy /mir` — его блокирует автопроверка.

## 1. Снять демоны Gradle / Kotlin и fsnotifier

Остановить Java, у которых в командной строке есть `GradleDaemon`, `kotlin-daemon` или `kotlin-compiler`. Затем `fsnotifier` / `fsnotifier64`.

```powershell
Get-CimInstance Win32_Process -Filter "Name='java.exe'" | Where-Object {
  $_.CommandLine -match 'GradleDaemon|kotlin-daemon|kotlin-compiler'
} | ForEach-Object { Stop-Process -Id $_.ProcessId -Force -ErrorAction SilentlyContinue }
Get-Process -Name fsnotifier,fsnotifier64 -ErrorAction SilentlyContinue | Stop-Process -Force
```

Если в корне есть `gradlew.bat` — ещё `.\gradlew.bat --stop` из корня репозитория. Если обёртки ещё нет, этот шаг пропустить.

Проверка: таких Java-процессов больше нет.

## 2. Удалить папки, которые Gradle не может перезаписать

Корень — корень репозитория. Если `Remove-Item` не смог — переименовать в `*.old`.

Частые ошибки: `merged_res_blame_folder`, `packaged_res`, `incremental\packageDebug\tmp`.

```powershell
$paths = @(
  "app\build\intermediates\incremental\packageDebug\tmp",
  "app\build\intermediates\incremental\packageDebug",
  "app\build\intermediates\incremental",
  "app\build\intermediates\merged_res_blame_folder",
  "app\build\intermediates\packaged_res",
  "app\build\intermediates\project_dex_archive",
  "app\build\intermediates\desugar_graph",
  "app\build\intermediates\dex",
  "app\build\intermediates",
  "build"
)
foreach ($p in $paths) {
  if (-not (Test-Path $p)) { continue }
  Remove-Item -LiteralPath $p -Recurse -Force -ErrorAction SilentlyContinue
  if (Test-Path $p) {
    $old = "$p.old"
    if (Test-Path $old) { Remove-Item -LiteralPath $old -Recurse -Force -ErrorAction SilentlyContinue }
    Rename-Item -LiteralPath $p -NewName (Split-Path $old -Leaf) -ErrorAction SilentlyContinue
    Remove-Item -LiteralPath $old -Recurse -Force -ErrorAction SilentlyContinue
  }
}
```

Корневой `build` — это каталог сборки Gradle, не папка `design` и не исходники.

## 3. Что сказать человеку

- Если папок из списка не было или они исчезли (или остались только `*.old`) — **можно собирать**.
- Если `gradlew.bat` ещё нет, так и сказать: снимать нечего, проект Android Studio ещё не создан.
- Если конкретную папку так и не удалось снять — назвать её. При повторной ошибке снова **a-b** / **a-d**.
