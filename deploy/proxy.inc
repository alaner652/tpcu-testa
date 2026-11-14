proxy_pass http://server:8080;
proxy_http_version 1.1;
proxy_set_header Host $http_host;
proxy_set_header X-Real-IP $remote_addr;
proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
# An outer HTTPS reverse proxy passes X-Forwarded-Proto; without one, use the current scheme
proxy_set_header X-Forwarded-Proto $forwarded_proto;
proxy_read_timeout 60s;
