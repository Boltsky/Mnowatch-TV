# Antigravity Workspace Rules

## 1. ADB Screencap / Screenshots on Windows (CRITICAL SAFETY RULE)
NEVER use stdout redirection ('>') or 'exec-out' for capturing screenshots on Windows:
- DO NOT RUN: adb exec-out screencap -p > file.png
- DO NOT RUN: cmd /c "adb ... > file.png"

WHY:
1. When the ADB daemon starts or restarts, it outputs plain text ('* daemon not running. starting it now on port 5037 *') to stdout.
2. Windows cmd.exe and powershell perform CRLF (\r\n) translation on redirected binary stdout.
3. This corrupts the PNG binary header. When view_file is subsequently called on that file, the Gemini API immediately crashes with 'INVALID_ARGUMENT (code 400)', permanently corrupting the conversation history.

MANDATORY WORKFLOW FOR SCREENSHOTS:
Always save directly to the device filesystem, then use 'adb pull':
`powershell
& "C:\Program Files\BlueStacks_nxt\HD-Adb.exe" -s emulator-5554 shell screencap -p /sdcard/screen.png
& "C:\Program Files\BlueStacks_nxt\HD-Adb.exe" -s emulator-5554 pull /sdcard/screen.png local_screen.png
`
This guarantees an uncorrupted binary image every time.
