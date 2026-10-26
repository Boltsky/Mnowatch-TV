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
```powershell
& "C:\Program Files\BlueStacks_nxt\HD-Adb.exe" -s emulator-5554 shell screencap -p /sdcard/screen.png
& "C:\Program Files\BlueStacks_nxt\HD-Adb.exe" -s emulator-5554 pull /sdcard/screen.png local_screen.png
```
This guarantees an uncorrupted binary image every time.

## 2. Media Streaming & Server Failover Invariants (CRITICAL STABILITY RULE)

1. **NO DEAD PUBLIC PROXIES IN VIDEO STREAMING PIPELINES**:
   - Never route ExoPlayer or media client HTTP traffic through public/free proxy pools. Free proxies introduce multi-second connection stalls, socket drops, and complete playback failure for 1080p/720p streams.
   - All media streams must connect directly or through dedicated, operational backend edge nodes.

2. **HETEROGENEOUS CDN ORIGIN FAILOVER**:
   - Server failovers must route across distinct CDN providers (e.g., Twolekede/Movviez ➔ Munowatch/BunnyCDN `b-cdn.net`).
   - Do NOT construct failover chains that repeatedly query the same rate-limited host (`movviez.com`) under different labels (Server 1, Server 2, Server 3). If `movviez.com` returns HTTP 429, immediately switch to the independent BunnyCDN stream or display a clear user notification.

3. **NO SYNTHETIC IP SPOOFING HEADERS ON CLOUDFLARE ENDPOINTS**:
   - Never inject arbitrary `X-Forwarded-For`, `Client-IP`, or `True-Client-IP` headers into requests directed to Cloudflare-protected CDNs (`movviez.com`). Cloudflare WAF flags header spoofing as bot traffic and imposes aggressive HTTP 429 blocks.
