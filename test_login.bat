@echo off
curl -X POST http://localhost:8090/api/auth/login -H "Content-Type: application/json" -d "{\"username\":\"admin\",\"password\":\"changeme\"}"
pause