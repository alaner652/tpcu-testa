# Estimated server configuration

What to request from the IT office for one admission exam site with a few hundred candidates. The reasons and the set-up steps are in [Deployment](deployment.md).

| Item | Estimate | Notes |
| --- | --- | --- |
| Machine | 1 Linux VM (on Proxmox VE: a VM, not an LXC container) | Everything runs on it with Docker Compose |
| OS | Debian or Ubuntu LTS, with Docker Engine | |
| CPU | 4 vCPU, type `host` or `x86-64-v3` | The image build crashes on the default QEMU CPU type |
| Memory | 8 GB | 300 concurrent candidates measured p99 12 ms on a laptop |
| Disk | 64 GB or more | Mostly for backups; the database is small |
| IP address | 1 fixed internal IP | No public IP needed for exams |
| Host name | 1 internal DNS record for the exam site, pointing to that IP | Must resolve with the Internet cut off |
| Inbound | TCP 443 and 80 from every lab subnet; SSH from the administrator's network only | Nothing inbound from the Internet |
| Outbound | Internet access for installation and upgrades only | Docker Hub, Maven Central, npm; can be closed on exam days |
| Certificate | A TLS certificate for the host name | A public CA or the school's wildcard certificate |
| Time | NTP from a source inside the school | The server clock decides exam timing |
| Backup | VM-level backup (vzdump or Proxmox Backup Server) | In addition to the built-in daily database dump |
| AI question import (optional, not in the first release) | A second VM, about 4 vCPU and 8 GB, no GPU required | Runs a small local model; switched off during exams |
