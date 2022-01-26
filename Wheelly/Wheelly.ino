#include <Servo.h>
#include "IRremote.h"
#include "AsyncTimer.h"
#include "Multiplexer.h"

#define SONAR_INACTIVITY 50
#define SONAR_TIMEOUT (SONAR_INACTIVITY * 1000ul) // micros

#define RED1_MASK 8
#define RED2_MASK 4
#define RED3_MASK 2
#define RED4_MASK 1
#define LEFT_BACK_MASK 0x10
#define LEFT_FORW_MASK 0x20
#define RIGHT_BACK_MASK 0x80
#define RIGHT_FORW_MASK 0x40

#define LATCH_PIN 8
#define CLOCK_PIN 7
#define DATA_PIN 12

int servoPin = 3;
int triggerPin = 5;
int echoPin = 4;
int receiver = 11; // Signal Pin of IR receiver to Arduino Digital Pin 11


/*-----( Declare objects )-----*/
IRrecv irrecv(receiver);     // create instance of 'irrecv'
decode_results results;      // create instance of 'decode_results'

// Sonar timeout micros
int tDelay = 300;
int pos = 0;    // variable to store the servo position

Servo myservo;  // create servo object to control a servo
// twelve servo objects can be created on most boards
AsyncTimer timer;
AsyncTimer timer1;
Multiplexer multiplexer(LATCH_PIN, CLOCK_PIN, DATA_PIN);

long counter;
unsigned long started;

void setup() {
  Serial.begin(9600);
  multiplexer.begin();

  pinMode(echoPin, INPUT);
  pinMode(triggerPin, OUTPUT);
  pinMode(LED_BUILTIN, OUTPUT);

  myservo.attach(servoPin);  // attaches the servo on pin 9 to the servo object
  myservo.write(90);              // tell servo to go to position in variable 'pos'
  irrecv.enableIRIn(); // Start the receiver
  
  digitalWrite(LED_BUILTIN, LOW);
  multiplexer.reset().flush();
  delay(100);

  digitalWrite(LED_BUILTIN, HIGH);
  multiplexer.values(0xff).flush();
  delay(100);
  
  digitalWrite(LED_BUILTIN, LOW);
  multiplexer.reset().flush();
  delay(100);

  digitalWrite(LED_BUILTIN, HIGH);
  multiplexer.values(0xff).flush();
  delay(100);
  
  digitalWrite(LED_BUILTIN, LOW);
  multiplexer.reset().flush();
  
  Serial.println(F("Hello! I'm Wheelly"));

  unsigned long times[] = {50, 250};
  timer
    .onNext(&handleNext)
    .intervals(2, times)
    .continuous(true)
    .start();
  timer1
    .onNext(&handleStats)
    .interval(10000)
    .continuous(true)
    .start();

  started = millis();
}

void loop() {
  counter++;
  timer.polling();
  timer1.polling();
  multiplexer.flush();
} 

void handleNext(int i, long j) {
  if (i == 0){
    digitalWrite(LED_BUILTIN, HIGH);
    multiplexer.set(j%8);
  } else {
    digitalWrite(LED_BUILTIN, LOW);
    multiplexer.reset();
  }
}

void handleStats(int i, long j) {
  Serial.print("Stats: ");
  Serial.println (counter * 1000 / (millis() - started));
  started = millis();
  counter = 0;
}

void loopIR()   /*----( LOOP: RUNS CONSTANTLY )----*/
{
  if (irrecv.decode(&results)) // have we received an IR signal?

  {
    translateIR(); 
    irrecv.resume(); // receive the next value
  }  
}/* --(end main loop )-- */


void loopSR04() {
    long _duration = 0;
    digitalWrite(triggerPin, LOW);
    delayMicroseconds(2);
    digitalWrite(triggerPin, HIGH);
    delayMicroseconds(10);
    digitalWrite(triggerPin, LOW);
    delayMicroseconds(2);
    _duration = pulseIn(echoPin, HIGH, SONAR_TIMEOUT);
    // Convert microsec to cm
    long distance = (_duration * 100) / 5882;
    Serial.print("Distance ");
    Serial.println(distance);
    if (distance <= 40) {
      digitalWrite(LED_BUILTIN, HIGH);   // turn the LED on (HIGH is the voltage level)
    } else {
      digitalWrite(LED_BUILTIN, LOW);   // turn the LED on (HIGH is the voltage level)
    }
    delay(500);
}

void loopServo() {
  for (pos = 0; pos <= 180; pos += 1) { // goes from 0 degrees to 180 degrees
    // in steps of 1 degree
    myservo.write(pos);              // tell servo to go to position in variable 'pos'
    delay(15);                       // waits 15ms for the servo to reach the position
  }
  for (pos = 180; pos >= 0; pos -= 1) { // goes from 180 degrees to 0 degrees
    myservo.write(pos);              // tell servo to go to position in variable 'pos'
    delay(15);                       // waits 15ms for the servo to reach the position
  }

  //myservo.write(90);              // tell servo to go to position in variable 'pos'
}

/*-----( Function )-----*/
// takes action based on IR code received{
void translateIR() {
  // describing Remote IR codes 
  switch(results.value){
  case 0xFFA25D: Serial.println("POWER"); break;
  case 0xFFE21D: Serial.println("FUNC/STOP"); break;
  case 0xFF629D: Serial.println("VOL+"); break;
  case 0xFF22DD: Serial.println("FAST BACK");    break;
  case 0xFF02FD: Serial.println("PAUSE");    break;
  case 0xFFC23D: Serial.println("FAST FORWARD");   break;
  case 0xFFE01F: Serial.println("DOWN");    break;
  case 0xFFA857: Serial.println("VOL-");    break;
  case 0xFF906F: Serial.println("UP");    break;
  case 0xFF9867: Serial.println("EQ");    break;
  case 0xFFB04F: Serial.println("ST/REPT");    break;
  case 0xFF6897: Serial.println("0");    break;
  case 0xFF30CF: Serial.println("1");    break;
  case 0xFF18E7: Serial.println("2");    break;
  case 0xFF7A85: Serial.println("3");    break;
  case 0xFF10EF: Serial.println("4");    break;
  case 0xFF38C7: Serial.println("5");    break;
  case 0xFF5AA5: Serial.println("6");    break;
  case 0xFF42BD: Serial.println("7");    break;
  case 0xFF4AB5: Serial.println("8");    break;
  case 0xFF52AD: Serial.println("9");    break;
  case 0xFFFFFFFF: Serial.println(" REPEAT");break;  

  default: 
    Serial.println(" other button   ");

  }// End Case

  delay(500); // Do not get immediate repeat
} //END translateIR
