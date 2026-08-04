@echo off
echo Starting Inventory Service on port 8081...
start /b java -Dserver.port=8081 -jar inventory-service\target\inventory-service-0.0.1-SNAPSHOT.jar > inventory.log 2>&1

echo Starting Order Service on port 8082...
start /b java -Dserver.port=8082 -jar order-service\target\order-service-0.0.1-SNAPSHOT.jar > order.log 2>&1

echo Starting Payment Service on port 8083...
start /b java -Dserver.port=8083 -jar payment-service\target\payment-service-0.0.1-SNAPSHOT.jar > payment.log 2>&1

echo Starting User Service on port 8084...
start /b java -Dserver.port=8084 -jar user-service\target\user-service-0.0.1-SNAPSHOT.jar > user.log 2>&1

echo Waiting 15 seconds for services to initialize...
timeout /t 15 /nobreak > nul

echo Opening Swagger UI in browser...
start http://localhost:8081/swagger-ui.html
start http://localhost:8082/swagger-ui.html
start http://localhost:8083/swagger-ui.html
start http://localhost:8084/swagger-ui.html

echo All services started! Check the .log files for console output.
