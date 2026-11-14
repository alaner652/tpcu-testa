# Testa 考試平台

設計文件：`testa-system-design.html`。前端在 `testa/`（Vue 3），後端在 `server/`（Spring Boot 4.1、Java 21、PostgreSQL）。

## 開發

需要 Docker、JDK 21 以上、Node 22。

```bash
docker compose up -d                                              # PostgreSQL（localhost:5432）
cd server && ./mvnw spring-boot:run -Dspring-boot.run.profiles=dev   # 後端 :8080，自動建表與示範帳號
cd testa && npm install && npm run dev                            # 前端 :5173，/api 轉給後端
```

dev profile 的示範帳號（登入頁有一鍵登入）：

| 身分 | 帳號 | 密碼 |
| --- | --- | --- |
| 系統管理員 | admin@testa.tw | testa-admin |
| 承辦人（負責人＋監考） | officer@testa.tw | testa-officer |
| 教師（出題＋評分） | teacher@testa.tw、teacher2@、teacher3@ | testa-teacher |
| 考生（示範考試） | 准考證號 A115-0001 | 登入碼 482915 |

測試：`cd server && ./mvnw test`（用 Testcontainers 開真的 PostgreSQL，需要 Docker）。前端：`cd testa && npm test`。

正式環境不要開 dev profile；第一位系統管理員用環境變數 `TESTA_ADMIN_EMAIL`、`TESTA_ADMIN_PASSWORD` 建立，HTTPS 下設 `TESTA_COOKIE_SECURE=true`。

## 進度（對應設計文件第 9 節的開發順序）

1. 後端骨架與登入：完成。教職員登入、Session、CSRF、單位與角色、權限檢查、操作紀錄、後台管理頁。
2. 題庫：完成。題庫編輯器接上 API，自動儲存，版本衝突偵測。
3. 考試與名單：完成。建立、發布（凍結題目快照）、結束考試；名單匯入（.xlsx、.csv，含 Big5）、登入碼、登入單列印、監考頁。
4. 考生作答：完成。准考證號＋登入碼登入、單一連線、試卷不含答案、伺服器計時、自動儲存、斷線續考、交卷、逾時自動收卷、自動批改。
5. 評分：完成（規則為暫定值，待確認）。每場考試指派 1 到多位評分者；匿名、互相看不到分數；全部送出後取平均（小數一位）；差距超過配分三成會標記（`testa.grading.flag-ratio`）；承辦人可填理由重開評分。
6. 成績與監考：完成。成績表、公布成績、匯出 Excel 與 CSV、監考頁（狀態、鎖定、離開畫面次數）。成績公布後，考生用准考證號＋登入碼登入可以看到自己的總分與「選擇填空／申論」兩個小計（看不到答案、評語、各題得分；是否開放查詢仍待確認）。
7. 部署與試考：正式環境的設定（`docker-compose.prod.yml`、`server/Dockerfile`、`testa/Dockerfile`、`deploy/`）已在開發機上實際建置並驗證過（2026-11-14），還沒上學校的主機。

已在開發機測過：

- 部署：映像檔建置、Nginx（靜態檔、SPA 路由、安全標頭、限流）、每日備份、清空資料庫後從備份還原。
- 模擬考：`deploy/smoke-test.py` 透過 Nginx 走完出題到考生查成績。
- 負載：300 位考生從同一個 IP 同時登入、作答、交卷（8400 個請求，零失敗，p99 12 ms）。
- 異常情境：考到一半後端重啟、資料庫重啟、同一考生兩處登入、登入碼連錯鎖定與重設、承辦人中途結束考試、時間到自動收卷（含完全沒再連線的考生）。
- HTTPS：前面加一台自簽憑證的反向代理，`TESTA_COOKIE_SECURE=true` 下整場模擬考通過；`set_real_ip_from` 設定後限流與操作紀錄都記到真正的來源 IP，偽造的 `X-Forwarded-For` 不會被採信。
- 瀏覽器（Chromium）：考生端（登入、四種題型作答、重新整理續考、斷線自動重送、被踢、交卷、時間到、查成績，含 390 與 320 寬度）、教職員端（換密碼、出題、建立考試、Big5 名單匯入、登入單、監考、閱卷、公布成績、匯出 CSV）、系統管理員後台。

還沒測、要到現場才能確認的：

- 學校主機與電腦教室的實際網路；學校真正的 HTTPS 反向代理。
- Chromium 以外的瀏覽器（Safari、Firefox、Edge）與 Windows。
- 登入單實際用印表機印出來的樣子（PDF 輸出已確認中文正常）。

## 還沒實作的功能（2026-11-14 盤點）

設計文件「第一版要做」的七項都已經做完。以下是沒做或做法比設計文件簡化的部分。

設計文件有寫、目前沒有：

- 考生個資與答卷到期刪除：沒有保存期限，也沒有刪除功能（期限待確認）。
- 後台的「系統設定」：設定頁只是示範。備份由 `backup` 容器每天執行，後台的「備份」分頁只能看清單與最近一次的時間，不能下載、手動觸發或還原。
- 評分差異的後續處理：差距過大只會標記給承辦人看，沒有裁定、重評或加派評分者的流程（待確認）。
- 出題者不得評自己題目的迴避規則、系統管理員的緊急檢視權限：都沒做（待確認）。

只有畫面、沒接後端（正式建置不顯示）：AI 匯入題目、申論題的 AI 輔助評分開關、後台系統設定。

設計文件寫明第一版不做，也確實沒做：班級學生長期帳號、Email／簡訊寄登入單、視訊監考與鎖定瀏覽器、隨機抽題與多份試卷、校務系統串接與單一登入。

其他缺口：

- 教職員忘記密碼只能請系統管理員重設，沒有自助流程。
- 前端只有純邏輯的單元測試（批改、題目檢查、公式轉 HTML、API 錯誤處理），沒有元件與瀏覽器的自動化測試；瀏覽器流程是用腳本手動跑過的。
- CI 設定檔（`.github/workflows/ci.yml`）已寫好，但這個專案還沒推到 GitHub，沒有實際跑過。

## 部署

```bash
cp deploy/env.example .env      # 填資料庫密碼與第一位系統管理員
docker compose -f docker-compose.prod.yml up -d --build
```

對外只開 Nginx（預設 80 埠）。HTTPS 由學校的反向代理負責；區網純 HTTP 時要把 `.env` 的 `TESTA_COOKIE_SECURE` 設成 `false`，否則登入後會立刻被登出。登入狀態存在資料庫（`spring_session`），後端重啟後不用重新登入。

**前面有反向代理時。** Nginx 的限流與操作紀錄裡的 IP 都以來源 IP 計算。如果前面還有一台代理，所有人看起來都來自那台代理，會共用同一份限流額度；照 `deploy/nginx.conf` 開頭的說明設定 `set_real_ip_from`。

**改了 `deploy/` 裡的設定之後**要重啟 web 容器：`docker compose -f docker-compose.prod.yml restart web`。只做 `nginx -s reload` 不夠——多數編輯器與 `git pull` 存檔時會把檔案整個換掉，容器裡掛載的還是舊的那一份，reload 會報找不到設定檔。

**備份與還原。** 系統管理員可以在後台的「備份」分頁確認每天的備份有在跑（超過 26 小時沒有新檔會提醒）。資料庫每天備份到 `TESTA_BACKUP_DIR`（預設 `./backups`），保留 `BACKUP_KEEP_DAYS` 天。要立刻多備份一次：`docker compose -f docker-compose.prod.yml restart backup`。還原會把資料庫整個換成備份當時的內容，先停後端：

```bash
docker compose -f docker-compose.prod.yml stop server
gunzip -c backups/testa-YYYYmmdd-HHMMSS.sql.gz | docker compose -f docker-compose.prod.yml exec -T db psql -U testa -d testa -v ON_ERROR_STOP=1 --single-transaction
docker compose -f docker-compose.prod.yml start server
```

**部署後檢查。** `python3 deploy/smoke-test.py http://主機:埠 管理員帳號 管理員密碼` 會用真的 HTTP 走完一場模擬考（建帳號、題庫、考試、作答、評分、公布、考生查成績）。它會在資料庫留下「冒煙測試」開頭的資料，而且會把管理員的臨時密碼換掉，**只對測試環境執行**。

## 與設計文件 v0.2 不同的地方

- 評分者是 1 到多位，不是固定三位（2026-11-14 確認）。
- 登入碼存原文而非雜湊，登入單才能重印；只有該單位的負責人與監考讀得到（待確認）。
- 後端用 Spring Boot 4.1 而非 3（3.x 已停止開源維護）；編譯目標仍是 Java 21。
- 公布成績＝開放考生查詢自己的分數（設計文件待確認事項第 7 項，暫定這樣做）。
- 中文字型用系統內建黑體，英數字型隨前端打包，不連外部 CDN。

## 只在開發模式出現的示範功能

AI 匯入題目（假資料）、申論題的「AI 輔助評分」開關、後台「系統設定」分頁都還沒接後端，設計文件也寫明第一版不做 AI。這三樣由 `testa/src/lib/flags.ts` 的 `DEMO_FEATURES` 控制，`npm run dev` 看得到，正式建置（`npm run build`）不顯示。

## 後端慣例

- 權限檢查只走 `AccessGuard`（`@PreAuthorize("@access...")` 或 `access.require*`）。系統管理員不會自動通過單位角色檢查。
- 資料表變更一律新增 Flyway migration（`server/src/main/resources/db/migration`），不改舊檔。
- 錯誤回 problem+json，`detail` 是可以直接顯示給使用者的中文，`code` 給程式判斷。
- Session 裡的身分（`StaffPrincipal`、`CandidatePrincipal`）要能序列化；整合測試關掉了資料庫 Session，改動登入流程後要跑 `deploy/smoke-test.py`。
- 送到考生端的資料只走 `AttemptDtos`，不能包含答案、解析、評分規準（`AttemptTest` 有檢查）。
- 計時、收卷、批改只在後端做（`AttemptService`）；前端的倒數只是顯示。
- 不可逆或敏感的動作寫入 `AuditService`；`audit_log` 在資料庫層禁止 update／delete。
