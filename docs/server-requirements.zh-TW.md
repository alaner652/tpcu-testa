# 主機預估配置

[English](server-requirements.md) | **繁體中文**

一場招生考試（幾百位考生）要向資訊組申請的項目。原因和安裝步驟寫在[部署文件](deployment.md)。

| 項目 | 預估 | 說明 |
| --- | --- | --- |
| 主機 | 1 台 Linux VM（Proxmox VE 上請開 VM，不要用 LXC 容器） | 所有服務都用 Docker Compose 跑在這一台 |
| 作業系統 | Debian 或 Ubuntu LTS，安裝 Docker Engine | |
| CPU | 4 vCPU，CPU 類型選 `host` 或 `x86-64-v3` | 用 QEMU 預設的 CPU 類型，建置映像檔時會當掉 |
| 記憶體 | 8 GB | 在筆電上實測 300 人同時作答，p99 為 12 ms |
| 硬碟 | 64 GB 以上 | 主要放備份，資料庫本身很小 |
| IP 位址 | 1 個固定的內部 IP | 考試不需要對外 IP |
| 主機名稱 | 1 筆校內 DNS 紀錄，指向上面的 IP | 外網切斷時也要解析得到 |
| 連入 | 各電腦教室的網段連到 TCP 443 和 80；SSH 只開給管理者的網段 | 不需要從網際網路連入 |
| 連出 | 只有安裝和升級時需要網際網路 | Docker Hub、Maven Central、npm；考試當天可以關閉 |
| 憑證 | 這個主機名稱的 TLS 憑證 | 公開 CA 簽發的，或學校的萬用憑證 |
| 時間 | 校內的 NTP 來源 | 考試計時以伺服器的時間為準 |
| 備份 | VM 層級的備份（vzdump 或 Proxmox Backup Server） | 系統內建的每日資料庫備份之外，再加這一層 |
| AI 匯入題目（選用，第一版不含） | 第二台 VM，約 4 vCPU、8 GB，不需要 GPU | 跑小型的本地模型；考試期間關機 |
