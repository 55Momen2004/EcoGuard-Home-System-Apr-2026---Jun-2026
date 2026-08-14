# EcoGuard Home Bluetooth Protocol

Baud rate: 9600 bps  
Bluetooth module: HC-05 using Classic Bluetooth SPP  
Controller: Arduino Mega 2560

## App to Arduino Commands

| Command | Meaning |
|---|---|
| A | Enable smart automation mode |
| H | Disable smart automation mode |
| L / P | Relay 1 ON / OFF |
| R / T | Relay 2 ON / OFF and servo action |
| Q / W | Relay 3 ON / OFF |
| G / F | Relay 4 ON / OFF |
| D / V | Relay 5 ON / OFF |
| E / N | Relay 6 ON / OFF |
| C / M | Relay 7 ON / OFF |
| I / O | Relay 8 ON / OFF |
| Y | Turn all loads OFF |
| X | Mute alarm |
| S | Request immediate status frame |

## Arduino to App Frames

READY,ECOGUARD_HOME

STATUS,FIRE=0,GAS=0,GAS_ADC=120,FLAME_ADC=800,SMART=0,ALARM=0,R1=0,R2=0,R3=0,R4=0,R5=0,R6=0,R7=0,R8=0

ALERT,FIRE=1,GAS=0,GAS_ADC=120,FLAME_ADC=200,SMART=0,ALARM=1,R1=0,R2=0,R3=0,R4=0,R5=0,R6=0,R7=0,R8=0

SAFE,FIRE=0,GAS=0
