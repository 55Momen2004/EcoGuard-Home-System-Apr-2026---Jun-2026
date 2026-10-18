# EcoGuard Home Android App

هذا المجلد يحتوي على مشروع تطبيق Android كامل للتحكم في مشروع EcoGuard Home عبر Bluetooth HC-05، مع ملف Arduino firmware محدث لإرسال تنبيهات الحريق والغاز إلى التطبيق بدون تغيير الهاردوير.

## هل يمكن تنفيذ المطلوب؟
نعم، يمكن تنفيذ التحكم والتنبيهات بنفس الهاردوير الحالي، بشرط رفع ملف firmware المرفق على Arduino Mega 2560. السبب أن التطبيق لا يستطيع معرفة حالة الحريق أو الغاز من نفسه؛ يجب أن يرسل Arduino رسائل عبر HC-05 عند حدوث خطر.

## الملفات المهمة
- `app/` مشروع Android Studio كامل.
- `firmware/EcoGuard_Home_Bluetooth_Notification_Firmware.ino` كود Arduino Mega المحدث.

## اسم التطبيق
EcoGuard Home

## خصائص التطبيق
- اتصال Bluetooth Classic مع HC-05.
- اختيار جهاز HC-05 من الأجهزة المقترنة.
- تحكم في 8 قنوات Relay.
- تشغيل وإيقاف Smart Mode.
- أمر All Loads OFF لإطفاء جميع الأحمال.
- زر Mute Alarm لإيقاف صوت البزر من التطبيق.
- عرض مباشر لحالة Fire / Gas / Alarm / Smart Mode.
- عرض قراءات Gas ADC و Flame ADC.
- إشعارات على الهاتف عند وصول ALERT من Arduino.
- اهتزاز الهاتف عند تنبيه الحريق أو الغاز.

## طريقة إنشاء APK
1. افتح مجلد `EcoGuardHome_Android_Project` باستخدام Android Studio.
2. انتظر Gradle Sync حتى ينتهي.
3. من القائمة اختر: Build > Build APK(s).
4. بعد انتهاء البناء ستجد ملف APK في:
   `app/build/outputs/apk/debug/app-debug.apk`
5. انسخ APK إلى الهاتف وثبته.

## طريقة الاستخدام على الهاتف
1. شغل Arduino Mega والنظام.
2. من إعدادات Bluetooth في الهاتف قم بعمل Pairing مع HC-05.
3. إذا طلب PIN استخدم 1234 أو 0000.
4. افتح تطبيق EcoGuard Home.
5. امنح صلاحيات Bluetooth والإشعارات.
6. اضغط Refresh Devices.
7. اختر HC-05 ثم Connect.
8. استخدم أزرار التحكم، وستظهر التنبيهات تلقائيا عند وصول رسائل ALERT.

## ملاحظة مهمة عن HC-05 و Arduino Mega
الكود يستخدم افتراضيا:
`#define BT_SERIAL Serial1`

أي أن HC-05 متصل على Serial1 في Arduino Mega:
- Mega TX1 = pin 18 إلى RX في HC-05.
- Mega RX1 = pin 19 إلى TX في HC-05.
- GND مشترك.

إذا كان HC-05 عندك موصولا فعليا على Serial0 بدلا من Serial1، غيّر السطر إلى:
`#define BT_SERIAL Serial`

لا تغيّر الهاردوير إلا إذا كان التوصيل الحالي مختلفا عن الكود.

## بروتوكول أوامر التطبيق إلى Arduino
| الأمر | الوظيفة |
|---|---|
| A | Smart Mode ON |
| H | Smart Mode OFF |
| L / P | Relay 1 ON / OFF |
| R / T | Relay 2 ON / OFF + Servo action |
| Q / W | Relay 3 ON / OFF |
| G / F | Relay 4 ON / OFF |
| D / V | Relay 5 ON / OFF |
| E / N | Relay 6 ON / OFF |
| C / M | Relay 7 ON / OFF |
| I / O | Relay 8 ON / OFF |
| Y | All Loads OFF |
| X | Mute Alarm |
| S | Request Status |

## رسائل Arduino إلى التطبيق
| الرسالة | مثال | الاستخدام |
|---|---|---|
| READY | READY,ECOGUARD_HOME | تأكيد أن المتحكم جاهز |
| STATUS | STATUS,FIRE=0,GAS=0,GAS_ADC=120,FLAME_ADC=800,SMART=0,ALARM=0,R1=0... | تحديث واجهة التطبيق |
| ALERT | ALERT,FIRE=1,GAS=0,... | إشعار حريق أو غاز |
| SAFE | SAFE,FIRE=0,GAS=0 | عودة الحالة إلى الوضع الآمن |

## حدود مهمة
- التطبيق يعمل على Android لأن HC-05 يستخدم Bluetooth Classic SPP.
- بدون رفع firmware المرفق سيعمل التحكم فقط إذا كان كود Arduino الحالي يستقبل نفس الأوامر، لكن إشعارات الحريق والغاز لن تعمل لأن الكود القديم لا يرسل حالة الخطر إلى الهاتف.

