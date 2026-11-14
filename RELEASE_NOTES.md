# Password Login, Temporary Passwords & Account Security

Version: 26.1.2 (build from `8f0c0a984`)

---

## English

### Highlights

- **Password Login** — when the whitelist is disabled, clients must prove a password before any real server data is sent to them.
  - 1.21.6+ clients: native login dialog during the configuration phase.
  - 1.20.2 – 1.21.5 clients: a blank "login lobby" world where they type `/login <password>`.
  - 1.20.2 – 1.20.4 can be enabled explicitly (plaintext, opt-in via `allowUnencrypted`).
  - Only the SHA-256 hash is stored; verification is constant-time.
- **Name Protection** — with the whitelist off, clients must connect using the proxy account's username (identity compared by UUID, so offline-server display-name rewriting like `||name` prefixes doesn't bypass it). Default: on.
- **Temporary Passwords** — generate one or more time-limited passwords from the console.
  - `clientConnection tempPassword add <minutes>` / `list` / `remove <id>`.
  - Players logging in with a temporary password are bound to it; when it expires they are force-disconnected (binding removed on logout).
  - Temporary-password players **cannot modify any Zen settings** (denied-command list + write-time interception).
- **Brute-Force Lockout** — per-IP protection when the main password is wrong.
  - After N failures (default 3) the next attempt must wait `baseDelay × multiplier^extra` (default 60s, ×3 per failure), capped at `maxDelay` (default 1 day).
  - A correct main password resets that IP; changing the password resets all IPs; login cancels/timeouts do not count as failures.
  - Tune with `clientConnection passwordLogin lockout ...` (`threshold`, `baseDelay`, `multiplier`, `maxDelay`, `on/off`, `status`, `clear`).
- **Unified config write guard** — all handlers now modify config through `ConfigManager.modify()`, which intercepts temporary-password-account writes at write time (no memory change, no success message, localized notice).
- **Kick-on-tightening** — tightening auth settings from the console (whitelist `on`, password change, name protection `on`) disconnects affected connected players, but only when the setting actually transitions and is actually the active auth layer.

### Command / Behavior changes

- `whitelist enable on/off` → **`whitelist on/off`** (terminal only). `unsupported whitelist` is gone.
- `clientConnection passwordLogin` and `clientConnection nameProtection` are terminal-only (cannot be changed in-game).
- New commands: `clientConnection tempPassword ...`, `clientConnection passwordLogin lockout ...`.
- Password login / name protection only take effect while the whitelist is **off** (whitelist is the auth layer when enabled).
- Disconnect messages are localized (zh-TW / zh-CN / EN); for clients below 1.20.2 (no configuration phase) a multi-language message is used since their locale is never received.

### Notes

- Defaults: password login `on` (fail-closed: with no password set, all clients are rejected until `passwordLogin set <password>`), name protection `on`, lockout `on`.
- Offline-mode README section updated accordingly.

---

## 繁體中文

### 重點

- **密碼登入** — 白名單關閉時，客戶端必須先通過密碼驗證，伺服器真實資料才會開始傳送。
  - 1.21.6+ 客戶端：在 configuration 階段顯示原生登入對話框。
  - 1.20.2 – 1.21.5 客戶端：進入空白「登入大廳」，輸入 `/login <password>`。
  - 1.20.2 – 1.20.4 可明確開啟（明文傳遞，需自行承擔風險，`allowUnencrypted`）。
  - 只儲存 SHA-256 雜湊；比對為常數時間。
- **名稱保護** — 白名單關閉後，客戶端必須使用與 bot 相同名稱連線（以 UUID 比對身份，故離線服改造顯示名稱如 `||name` 前綴無法繞過）。預設開啟。
- **臨時密碼** — 可從控制台產生一組或多組限時密碼。
  - `clientConnection tempPassword add <分鐘數>` / `list` / `remove <id>`。
  - 以臨時密碼登入的玩家會綁定該密碼；密碼到期時強制斷線（下線即解除綁定）。
  - 臨時密碼玩家**不得修改任何 Zen 設定**（禁用清單 + 寫入當下攔截）。
- **防暴力破解** — 主密碼錯誤時的 IP 級保護。
  - 錯誤超過 N 次（預設 3）後，下一次須等待 `baseDelay × multiplier^次數`（預設 60 秒、每次 ×3），上限 `maxDelay`（預設 1 天）。
  - 主密碼正確 → 該 IP 歸零；變更密碼 → 全部 IP 歸零；於登入流程中退出不計錯誤。
  - 可用 `clientConnection passwordLogin lockout ...` 調整（`threshold`/`baseDelay`/`multiplier`/`maxDelay`/`on/off`/`status`/`clear`）。
- **統一設定寫入防護** — 所有指令 handler 改經 `ConfigManager.modify()` 修改設定；臨時密碼帳號的寫入在「寫入當下」被攔截（記憶體不動、不顯示成功訊息、顯示本地化提示）。
- **收緊即踢出** — 在控制台把驗證設定往更嚴格調整（白名單 `on`、變更密碼、名稱保護 `on`）時，會將受影響的在線玩家斷線；僅在設定「真的變更」且「當前確實在作用」時觸發。

### 指令／行為變更

- `whitelist enable on/off` → **`whitelist on/off`**（僅控制台）。`unsupported whitelist` 已移除。
- `clientConnection passwordLogin` 與 `clientConnection nameProtection` 僅限控制台（遊戲內不可改）。
- 新指令：`clientConnection tempPassword ...`、`clientConnection passwordLogin lockout ...`。
- 白名單**開啟**時，密碼登入／名稱保護不作用（白名單本身即驗證層）。
- 斷線訊息按語言顯示（繁中／簡中／英文）；1.20.2 以下（無 configuration 階段、拿不到語言設定）使用多語言並列。

### 注意

- 預設值：密碼登入 `on`（fail-closed：未設定密碼前所有客戶端被拒絕，直到 `passwordLogin set <password>`）、名稱保護 `on`、防暴力破解 `on`。
- README 離線模式章節已同步更新。