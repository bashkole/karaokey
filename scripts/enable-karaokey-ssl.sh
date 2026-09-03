#!/usr/bin/env bash
set -euo pipefail

DOMAIN="karaokey.ikomex.nl"
WEBROOT="/var/www/letsencrypt"
NGINX_CONF="/etc/nginx/sites-available/karaokey.ikomex.nl.conf"

echo "Checking public DNS for ${DOMAIN}..."
if ! dig +short "A" "${DOMAIN}" @8.8.8.8 | grep -q .; then
  echo "ERROR: ${DOMAIN} does not resolve publicly yet."
  echo "Add this DNS record at IONOS (ui-dns) for ikomex.nl:"
  echo "  Type: A"
  echo "  Host: karaokey"
  echo "  Value: 217.154.113.94"
  exit 1
fi

echo "Requesting Let's Encrypt certificate..."
certbot certonly --webroot -w "${WEBROOT}" -d "${DOMAIN}" \
  --non-interactive --agree-tos --register-unsafely-without-email

echo "Installing HTTPS nginx config..."
cat > "${NGINX_CONF}" <<'EOF'
# HTTP - ACME challenge and redirect to HTTPS
server {
    listen 80;
    listen [::]:80;
    server_name karaokey.ikomex.nl;

    location /.well-known/acme-challenge/ {
        root /var/www/letsencrypt;
        allow all;
    }

    location / {
        return 301 https://$host$request_uri;
    }
}

# HTTPS - static site + APK download
server {
    listen 443 ssl http2;
    listen [::]:443 ssl http2;
    server_name karaokey.ikomex.nl;

    ssl_certificate     /etc/letsencrypt/live/karaokey.ikomex.nl/fullchain.pem;
    ssl_certificate_key /etc/letsencrypt/live/karaokey.ikomex.nl/privkey.pem;
    include /etc/letsencrypt/options-ssl-nginx.conf;
    ssl_dhparam /etc/letsencrypt/ssl-dhparams.pem;

    add_header Strict-Transport-Security "max-age=31536000; includeSubDomains" always;
    add_header X-Frame-Options "SAMEORIGIN" always;
    add_header X-Content-Type-Options "nosniff" always;

    root /var/www/vhosts/karaokey/httpdocs;
    index index.html;

    location = /karaokey.apk {
        default_type application/vnd.android.package-archive;
        add_header Content-Disposition 'attachment; filename="karaokey.apk"';
    }

    location /callback {
        try_files $uri $uri/ /callback/index.html;
    }

    location / {
        try_files $uri $uri/ =404;
    }

    access_log /var/log/nginx/karaokey.ikomex.nl.access.log;
    error_log  /var/log/nginx/karaokey.ikomex.nl.error.log;
}
EOF

nginx -t
systemctl reload nginx

echo "SSL enabled for https://${DOMAIN}/"
echo "APK download: https://${DOMAIN}/karaokey.apk"
echo "Spotify callback: https://${DOMAIN}/callback"
