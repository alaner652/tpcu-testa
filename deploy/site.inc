# The site itself: static files and the API behind rate limits. Included by nginx.conf (HTTP) and https.conf (HTTPS).
root /usr/share/nginx/html;
index index.html;

client_max_body_size 6m;          # roster files up to 5 MB
server_tokens off;

add_header X-Content-Type-Options nosniff always;
add_header X-Frame-Options DENY always;
add_header Referrer-Policy same-origin always;

gzip on;
gzip_types text/css application/javascript application/json image/svg+xml;

location ~ ^/api/auth/(login|setup)$ {
    limit_req zone=login burst=20 nodelay;
    limit_req_status 429;
    include /etc/nginx/proxy.inc;
}

location = /api/candidate/login {
    limit_req zone=candidate_login burst=400 nodelay;
    limit_req_status 429;
    include /etc/nginx/proxy.inc;
}

location /api/ {
    limit_req zone=api burst=400 nodelay;
    limit_req_status 429;
    include /etc/nginx/proxy.inc;
}

# Hashed static files can be cached for long; index.html is not cached so new releases apply at once.
# Any add_header in a location stops inheriting the outer ones, so the security headers are repeated here.
location /assets/ {
    add_header Cache-Control "public, max-age=31536000, immutable";
    add_header X-Content-Type-Options nosniff always;
}
location = /index.html {
    add_header Cache-Control "no-store";
    add_header X-Content-Type-Options nosniff always;
    add_header X-Frame-Options DENY always;
    add_header Referrer-Policy same-origin always;
}
location / {
    try_files $uri $uri/ /index.html;
}

# Container healthcheck
location = /nginx-health {
    access_log off;
    return 204;
}
