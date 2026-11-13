proxy_pass http://server:8080;
proxy_http_version 1.1;
proxy_set_header Host $http_host;
proxy_set_header X-Real-IP $remote_addr;
proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
# 最外層如果還有一台負責 HTTPS 的反向代理，會把 X-Forwarded-Proto 傳進來；沒有就用目前的協定
proxy_set_header X-Forwarded-Proto $forwarded_proto;
proxy_read_timeout 60s;
