# Ripoti ya Mradi: Fire Detection System (Android)

**Jina la mradi:** Firedetectionstystem1  
**Package:** `com.example.firedetectionstystem1`  
**Lugha:** Java  
**Mfumo wa ujenzi:** Android (Gradle, Kotlin DSL)  
**Tarehe ya ripoti:** Aprili 2026  

---

## 1. Muhtasari

Mradi huu ni programu ya **Android** inayolenga **mfumo wa kugundua moto** na kuonyesha hali ya tahadhari kwenye simu ya mtumiaji. Programu ina skrini za kuingia mfumo (login), kujisajili, kuweka upya nenosiri, dashibodi ya hali ya moto/moshi, na skrini ya kuanzia (splash). Data ya tahadhari inaweza kusomwa **kwa muda halisi** kutoka **Google Cloud Firestore** baada ya kuunganishwa na vifaa vya nje (Arduino + ESP8266/ESP32) vilivyowekewa mifano ya code kwenye folda `hardware/`.

---

## 2. Malengo ya mfumo

- Kutoa kiolesura cha kisasa kwa mtumiaji wa mfumo wa kugundua moto.
- Kuhifadhi na kuthibitisha utambulisho wa mtumiaji (Firebase Authentication na/au hifadhi ya ndani ya kifaa).
- Kuonyesha hali ya mfumo (hakuna moto / tahadhari / moto) na vitendo vya dharura (k.m. kupiga simu ya dharura).
- Kuunga mkono **lugha nyingi** (English, Kiswahili, Kichina, Kihispania, Kiarabu).
- Kuunganishwa na **Firestore** kwa data ya sensa zinazotumwa na hardware.

---

## 3. Teknolojia na vifaa vya maendeleo

| Kipengele | Maelezo |
|-----------|---------|
| IDE | Android Studio |
| compileSdk / targetSdk | 34 |
| minSdk | 24 |
| Lugha ya programu | Java 8 |
| UI | XML layouts, AppCompat |
| Backend / wingu | Firebase Authentication, Cloud Firestore |
| Faili ya usanidi | `app/google-services.json` (lazima iwe kwenye moduli ya `app`) |

---

## 4. Muundo wa programu (Activities)

| Activity | Wajibu |
|----------|--------|
| `MainActivity` | Skrini ya kuanzia (splash), kisha kuelekeza kwenye `LoginActivity`. |
| `LoginActivity` | Kuingia: barua pepe, nenosiri; chaguo la lugha; uunganisho na Firebase au hifadhi ya ndani. |
| `RegisterActivity` | Kujisajili; kuunda akaunti Firebase na/au kuhifadhi ndani ya `SharedPreferences`. |
| `ForgetPassword` | Kuweka upya nenosiri (barua pepe ya Firebase au upya wa ndani ikiwa mtandao/Firebase haupatikani). |
| `DashboardActivity` | Kuonyesha hali ya moto/moshi; vitufe vya tahadhari za muda halisi, ugunduzi wa moshi, simu ya dharura; kusoma Firestore `fire_status/current`. |
| `OTPActivity` | Ipo kwenye code lakini **haijasajiliwa** kwenye `AndroidManifest.xml` (haitumiki kwenye flow ya sasa). |

**Kumbuka:** Faili `AppCompatActivity.java` kwenye package hilo ni class tupu; programu hutumia `androidx.appcompat.app.AppCompatActivity` kutoka maktaba.

---

## 5. Hifadhi ya data na usalama

### 5.1 SharedPreferences (`UserPrefs`)

- Inahifadhi `email` na `password` baada ya usajili (na wakati mwingine baada ya kuingia).
- Hutumiwa kama **njia ya ziada** ya kuingia au kuweka upya nenosiri wakati Firebase haifanyi kazi au haipatikani.

### 5.2 Firebase Authentication

- Kuingia na kusajili kwa **barua pepe na nenosiri**.
- Ili kuepuka makosa ya usanidi, programu inajaribu **login ya ndani** pindi uingiaji wa mtandao unashindwa.

### 5.3 Cloud Firestore

- Wasifu wa mtumiaji: `users/{uid}` baada ya usajili (wakati Firebase inafanya kazi).
- Hali ya sensa (kwa dashboard): `fire_status/current`  
  - Vifaa vinavyotarajiwa: `status`, `smokeLevel`, `flameDetected`, `temperature`, `updatedAt`.

### 5.4 Kanuni za usalama (Firestore)

Faili `firestore.rules` inapendekeza: mtumiaji aliyeingia anaweza kusoma/kuandika tu hati yake kwenye `users/{userId}`. **Lazima** deploy rules hizi kwenye Firebase Console au kupitia Firebase CLI.

---

## 6. Lugha (i18n)

- Faili kuu: `res/values/strings.xml`
- Tafsiri: `values-sw`, `values-zh`, `values-es`, `values-ar`
- Chaguo la lugha kwenye `LoginActivity` kupitia `LocaleHelper` na `SharedPreferences` (`AppPrefs`).

---

## 7. Rasilimali za kiolesura (UI)

- Mitindo ya rangi na pembe za duara: `drawable/header_bg.xml`, `input_bg.xml`, `login_btn_bg.xml`, nk.
- Skrini kuu za kuingia na dashibodi zimetengenezwa kwa muonekano wa kitaalamu (rangi za moto/tahadhari).

---

## 8. Hardware na uunganisho na wingu

Kwenye folda **`hardware/`** kuna:

- `arduino_uno_sensor_node.ino` — kusoma MQ-2, flame, DHT; buzzer, relay, LED.
- `esp32_firestore_sender.ino` — mfano wa kutuma data kwenye Firestore (ESP32; inaweza kubadilishwa kwa ESP8266 kwa marekebisho ya UART/WiFi).
- `README.md` — maelekezo ya uunganisho na maktaba.

Muunganiko wa mfumo: sensa → Arduino → (serial) → moduli ya WiFi → Firestore → App ya Android.

---

## 9. Ufunguzi na ujenzi

```text
./gradlew assembleDebug
```

Mahitaji:

- Android SDK imewekwa sawa
- `app/google-services.json` ipo na `applicationId` inalingana na Firebase

---

## 10. Mapungufu na mapendekezo ya baadaye

1. **OTPActivity** haijawekwa manifest; ama iongezwe au code ifutwe ikiwa haitahitajika.
2. **Kuhifadhi nenosiri kwa maandishi wazi** kwenye `SharedPreferences` **si salama** kwa production; tumia Firebase tu au Keystore/EncryptedSharedPreferences.
3. **Firestore rules** lazima ziwe deployed na kujaribiwa.
4. **SIM800L / Arduino** — ripoti ya muunganisho wa pini iko kwenye mazungumzo ya mradi na `hardware/README.md`; jaribio la uwanjani linapendekezwa kabla ya matumizi ya uzalishaji.
5. Ondoa sifa ya `package` kwenye `AndroidManifest.xml` ikiwa unatumia `namespace` kwenye Gradle (onyo la AGP).

---

## 11. Hitimisho

Mradi huu ni programu kamili ya Android inayounganisha kiolesura cha mfumo wa kugundua moto, uthibitisho wa mtumiaji kupitia Firebase, na uwezekano wa kusoma data ya sensa kwa muda halisi kutoka Firestore. Kuna nyongeza za hardware zilizoandikwa kwa ajili ya uunganisho na vifaa halisi vya uwanjani.

---

*Ripoti hii imeandikwa kulingana na muundo wa faili na mipangilio ya mradi kwa muda wa kuandika.*
