# Bounce

Reverse Proxy Written in Kotlin

Example of configuration file:
```
{
  "routes": [
    {
      "path": "/test",
      "method": "GET",
      "host": "localhost",
      "port": 5000
    },
    {
      "path": "/test",
      "method": "POST",
      "host": "localhost",
      "port": 5000,
      "connectTimeout": 5000
    }
  ],
  "http.port": 8080
}
```

Compile using maven `mvn clean package`

Run `java -jar target/bounce-3.6.2-fat.jar -conf target/classes/conf/config.json`