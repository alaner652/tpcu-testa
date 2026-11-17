# 畫面截圖

[English](screenshots.md) | **繁體中文**

以下畫面依一場招生考試的實際流程排列，使用含示範資料的正式版介面拍攝。桌機尺寸為 1440 × 900；手機尺寸為 390 × 844（2 倍像素密度）。

介面有可見變更時，請依文末的[重新拍攝](#重新拍攝)說明更新圖片。

## 登入

| 桌機 | 手機 |
| --- | --- |
| ![登入頁桌機版](images/login.png) | ![登入頁手機版](images/login-phone.png) |

考生輸入登入單上的准考證號與登入碼；教職員切換到另一個分頁登入。

## 教師：題庫

![題庫清單](images/banks.png)

![題庫編輯器](images/editor.png)

編輯器會自動儲存。數學式以 `$...$` 輸入，再由應用程式內附的 KaTeX 顯示。

![模擬作答](images/editor-preview.png)

「模擬作答」會以考生實際看到的樣子顯示題庫。

## 承辦人：排考與監考

![承辦人首頁](images/officer-home.png)

首頁會列出每場考試接下來要做的事。

![考試清單](images/exams.png)

![新增考試](images/exam-new.png)

| 桌機 | 手機 |
| --- | --- |
| ![監考桌機版](images/exam-proctor.png) | ![監考手機版](images/exam-proctor-phone.png) |

監考狀態每 10 秒更新。點選數量可只顯示該狀態的考生，例如「需注意」會列出登入碼輸錯遭鎖定，或曾離開考試畫面的考生。

![考生登入單](images/exam-slips.png)

每位考生一張登入單，由瀏覽器直接列印。

## 考生

![考生首頁](images/candidate-home.png)

![身分確認與作答規則](images/candidate-rules.png)

| 桌機 | 手機 |
| --- | --- |
| ![考生作答桌機版](images/candidate-exam.png) | ![考生作答手機版](images/candidate-exam-phone.png) |

畫面倒數只供顯示，計時與自動交卷由伺服器控制；答案會在變更時自動儲存。

## 閱卷與成績

![教師首頁](images/teacher-home.png)

| 桌機 | 手機 |
| --- | --- |
| ![匿名閱卷桌機版](images/grading.png) | ![匿名閱卷手機版](images/grading-phone.png) |

評分者一次只看到一份匿名答案，送出前必須輸入分數，0 分也要明確填寫。

![評分者與閱卷進度](images/exam-grading.png)

![成績表](images/exam-results.png)

考試結束且所有申論題完成評分後，才能公布成績。

## 系統管理員

![帳號管理](images/admin-users.png)

![操作紀錄](images/admin-audit.png)

操作紀錄只能新增，不能修改或刪除，並可依操作類型篩選。

![備份](images/admin-backups.png)

可在這裡立即備份，以及下載、刪除、還原備份。刪除與還原放在「…」選單，都要再次確認；還原還要輸入「還原」兩個字，而且有考生正在作答時會被拒絕。

![系統設定](images/admin-settings.png)

各校可能需要調整的項目都在這裡，以白話說明：AI 匯入、評分差距提醒、資料保存、備份保留天數與登入鎖定。

## 重新拍攝

`tools/capture-screenshots.mjs` 會透過 API 建立示範資料，並拍下本頁所有圖片。它直接以 DevTools 協定操作 Chrome，不需要額外套件。

1. 以 `dev` profile 啟動後端，並指定備份目錄，讓備份分頁有內容：`TESTA_BACKUP_PATH=/tmp/testa-backups TESTA_BACKUP_CONTROL_PATH=/tmp/testa-control ./mvnw spring-boot:run -Dspring-boot.run.profiles=dev`（在第一個目錄放幾個 `testa-YYYYmmdd-HHMMSS.sql.gz` 檔案）。
2. 啟動正式版前端，才不會拍到開發模式的示範登入按鈕：`cd testa && npm run build && npx vite preview`。
3. 在專案根目錄執行 `node tools/capture-screenshots.mjs`（Chrome 不在 `/Applications` 時設定 `CHROMIUM`；預覽網址不是 `http://localhost:4173/` 時設定 `TESTA_APP`）。

請沿用既有檔名，避免 README 的連結失效；示範資料不可包含真實人名與校務資料。


## 操作手冊影片

`tools/record-videos.mjs` 會錄下系統內操作手冊用的無聲短片，存到 `testa/public/manual/`（1280 x 800 的 MP4，畫面上有游標，每次點擊會出現光圈）。
準備步驟同上，另外需要 `ffmpeg`。請用一個全新的資料庫錄影（例如 `createdb testa_manual`，再設定 `TESTA_DB_URL=jdbc:postgresql://localhost:5432/testa_manual`），清單裡才只會出現示範資料。

```bash
node tools/record-videos.mjs                 # 錄全部
node tools/record-videos.mjs exam-proctor    # 只錄指定的片段
```

每段影片會自己建立需要的資料，介面改了之後可以只重錄那一段。按鈕或欄位改名時，請一併修改腳本裡的名稱。
操作手冊頁（`/help`）把影片和 `testa/src/manual/*.md`（每章一個 Markdown 檔，`![說明](影片名)` 嵌入影片）裡的步驟說明放在一起，改其中一邊時請一併更新另一邊。
