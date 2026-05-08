# CentOS7 Deployment

## Requirements

- JDK 17
- MySQL 8
- Nginx
- systemd

## Backend environment

```bash
export CHAT2API_DB_URL='jdbc:mysql://127.0.0.1:3306/chat2api?useUnicode=true&characterEncoding=utf8&useSSL=false&serverTimezone=Asia/Shanghai'
export CHAT2API_DB_USERNAME='chat2api'
export CHAT2API_DB_PASSWORD='change-me'
export CHAT2API_ENCRYPTION_KEY='change-this-32-byte-minimum-secret'
export CHAT2API_ADMIN_USERNAME='admin'
export CHAT2API_ADMIN_PASSWORD='change-me'
```

## Build

```bash
cd backend-java
mvn clean package -DskipTests
```

## systemd service

```ini
[Unit]
Description=Chat2API Backend
After=network.target mysqld.service

[Service]
User=chat2api
WorkingDirectory=/opt/chat2api
EnvironmentFile=/opt/chat2api/chat2api.env
ExecStart=/usr/bin/java -jar /opt/chat2api/backend-java.jar
Restart=always
RestartSec=5

[Install]
WantedBy=multi-user.target
```
