#include <ArduinoJson.h>

#include <ESP8266WiFi.h>
#include <WiFiClient.h>
#include <ESP8266WebServer.h>
#include <ESP8266mDNS.h>

#ifndef STASSID

#define STASSID "Vodafone-30330037"
#define STAPSK  "50n08u8uL0r54cch10770"
#endif

#define LED LED_BUILTIN
#define SERIAL_TIMEOUT  2000ul

const char* ssid = STASSID;
const char* password = STAPSK;

ESP8266WebServer server(80);

StaticJsonDocument<256> jsonDoc;

void setup(void) {
  pinMode(LED, OUTPUT);
  digitalWrite(LED, 0);
  Serial.begin(115200);
  Serial.setTimeout(SERIAL_TIMEOUT);
  Serial.println();
  Serial.print(F("// Connecting to "));
  Serial.print(ssid);
  Serial.println(F(" ..."));
  
  WiFi.mode(WIFI_STA);
  WiFi.begin(ssid, password);
  waitForConnection();
  Serial.println(F("// Connected"));
  Serial.print(F("// IP address: "));
  Serial.println(WiFi.localIP());

  if (MDNS.begin("esp8266")) {
    Serial.println(F("// MDNS responder started"));
  }

  server.on("/api/v1/wheelly/direction", handleDirection);
  server.on("/api/v1/wheelly/scan", handleScan);
  server.on("/api/v1/wheelly/status", handleStatus);
  server.on("/api/v1/wheelly/clock", handleClock);
  server.onNotFound(handleNotFound);

  server.begin();
  Serial.println(F("// HTTP server started"));
}

/*
 * 
 */
void loop(void) {
  server.handleClient();
  MDNS.update();
}

/*
 * 
 */
void waitForConnection() {
  // Wait for connection
  while (WiFi.status() != WL_CONNECTED) {
    digitalWrite(LED, LOW);
    delay(50);
    digitalWrite(LED, HIGH);
    delay(350);
  }  
}

/**
 * 
 */
void handleDirection() {
  digitalWrite(LED, LOW);
  if (server.method() != HTTP_POST) {
    sendInvalidMethod();
    return;
  }
  String reqBody = server.arg(F("plain"));
  DeserializationError err = deserializeJson(jsonDoc, reqBody);
  if (err) {
    sendInvalidContent(err.c_str());
    return;
  }
  if (!jsonDoc.containsKey(F("left"))
   || !jsonDoc.containsKey(F("right"))
   || !jsonDoc.containsKey(F("validTo"))) {
    sendInvalidContent("Missing parameters");
    return;
  }
  int left = jsonDoc[F("left")];
  int right = jsonDoc[F("right")];
  unsigned long validTo = jsonDoc[F("validTo")];

  serialCleanup();
  Serial.print(F("mt "));
  Serial.print(validTo);
  Serial.print(F(" "));
  Serial.print(left);
  Serial.print(F(" "));
  Serial.print(right);
  Serial.println();
  Serial.flush();
  String resBody = waitForStatus();
  if (resBody == "") {
    sendTimeoutError();
    return;
  }
  server.send(200, F("application/json"), resBody);
  digitalWrite(LED, HIGH);
}

/**
 * 
 */
void handleStatus() {
  digitalWrite(LED, LOW);
  if (server.method() != HTTP_GET) {
    sendInvalidMethod();
    return;
  }
  serialCleanup();
  Serial.println(F("qs"));
  Serial.flush();
  String body = waitForStatus();
  if (body == "") {
    sendTimeoutError();
    return;
  }
  server.send(200, F("application/json"), body);
  digitalWrite(LED, HIGH);
}

/**
 * 
 */
void handleClock() {
  digitalWrite(LED, LOW);
  if (server.method() != HTTP_GET) {
    sendInvalidMethod();
    return;
  }
  String ck = server.arg(F("ck"));
  serialCleanup();
  Serial.print(F("ck "));
  Serial.println(ck);
  Serial.flush();
  String result = Serial.readStringUntil('\r');
  if (result == "") {
    sendTimeoutError();
    return;
  }
  jsonDoc.clear();
  jsonDoc[F("clock")] = result;
  String message;
  serializeJson(jsonDoc, message);
  server.send(200, F("application/json"), message);
  digitalWrite(LED, HIGH);
}

/**
 * 
 */
void handleScan() {
  digitalWrite(LED, LOW);
  if (server.method() != HTTP_POST) {
    sendInvalidMethod();
    return;
  }
  serialCleanup();
  Serial.println(F("sc"));
  Serial.flush();
  String body = waitForStatus();
  if (body == "") {
    sendTimeoutError();
    return;
  }
  server.send(200, F("application/json"), body);
  digitalWrite(LED, HIGH);
}

/*
 * 
 */
void handleNotFound() {
  digitalWrite(LED, LOW);
  jsonDoc.clear();
  jsonDoc[F("message")] = F("Not Found");
  jsonDoc[F("uri")] = server.uri();
  jsonDoc[F("method")] = methodName();
  String body;
  serializeJson(jsonDoc, body);
  server.send(404, F("application/json"), body);
  digitalWrite(LED, HIGH);
}

/*
 * 
 */
void serialCleanup() {
  while (Serial.available() > 0) {
    Serial.read();
  }  
}

/*
 * 
 */
String waitForStatus() {
    String result = Serial.readStringUntil('\r');
    if (result != "") {
      jsonDoc.clear();
      jsonDoc[F("status")] = result;
      String body;
      serializeJson(jsonDoc, body);
      return body;
    } else {
      return result;
    }
}

/*
 * 
 */
String methodName() {
  switch (server.method()){
    case HTTP_GET: return "GET";
    case HTTP_POST: return "POST";
    case HTTP_DELETE: return "DELETE";
    default: return "UNKNOWN";
  }
}

/*
 * 
 */
void sendInvalidMethod() {
  jsonDoc.clear();
  jsonDoc[F("message")] = F("Method Not Allowed");
  jsonDoc[F("method")] = methodName();
  String body;
  serializeJson(jsonDoc, body);
  server.send(405, F("application/json"), body);
  digitalWrite(LED, HIGH);
}

/*
 * 
 */
void sendInvalidContent(const char *msg) {
  jsonDoc.clear();
  jsonDoc[F("message")] = msg;
  String body;
  serializeJson(jsonDoc, body);
  server.send(400, F("application/json"), body);
  digitalWrite(LED, HIGH);
}

/*
 * 
 */
void sendTimeoutError() {
  jsonDoc.clear();
  jsonDoc[F("message")] = F("Timeout");
  String body;
  serializeJson(jsonDoc, body);
  server.send(504, F("application/json"), body);
  digitalWrite(LED, HIGH);
}
