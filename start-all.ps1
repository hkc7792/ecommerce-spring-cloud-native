$job1 = Start-Process -FilePath "java" -ArgumentList "-jar", "-Dserver.port=8081", "inventory-service/target/inventory-service-0.0.1-SNAPSHOT.jar" -PassThru -WindowStyle Hidden
$job2 = Start-Process -FilePath "java" -ArgumentList "-jar", "-Dserver.port=8082", "order-service/target/order-service-0.0.1-SNAPSHOT.jar" -PassThru -WindowStyle Hidden
$job3 = Start-Process -FilePath "java" -ArgumentList "-jar", "-Dserver.port=8083", "payment-service/target/payment-service-0.0.1-SNAPSHOT.jar" -PassThru -WindowStyle Hidden
$job4 = Start-Process -FilePath "java" -ArgumentList "-jar", "-Dserver.port=8084", "user-service/target/user-service-0.0.1-SNAPSHOT.jar" -PassThru -WindowStyle Hidden

Start-Sleep -Seconds 15

Start-Process "http://localhost:8081/swagger-ui.html"
Start-Process "http://localhost:8082/swagger-ui.html"
Start-Process "http://localhost:8083/swagger-ui.html"
Start-Process "http://localhost:8084/swagger-ui.html"
