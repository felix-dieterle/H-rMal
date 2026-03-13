# H-rMal
Kindgerechte Hörtest app um rechts und links ohr in verschiedenen Frequenzbereichen abzutreten. Wie wären kurze Töne in zufällige Frequenzen bei unterschiedlichen Lautstärken in leicht variierenden Abständen sodass nach und nach die Grenzen im Frequenzbereich und Lautstärke Bereich abzutesten. Ein großer Button um hören zu bestätigen.

## CI / Auto-Update einrichten

Die App prüft beim Start, ob eine neuere Version auf GitHub verfügbar ist, und bietet den Download direkt in der App an.

Damit das Update-System korrekt funktioniert, **muss** jede Release-APK mit demselben Zertifikat signiert sein. Android verweigert die Installation einer neuen Version, wenn sich die Signatur gegenüber der installierten Version unterscheidet.

### 1. Einmalig: Keystore erstellen

```bash
keytool -genkey -v \
  -keystore hormal-release.jks \
  -alias hormal-key \
  -keyalg RSA -keysize 2048 -validity 10000 \
  -storepass <STORE_PASSWORD> \
  -keypass <KEY_PASSWORD> \
  -dname "CN=H-rMal, O=Felix Dieterle, C=DE"
```

### 2. Keystore als Repository-Secret hinterlegen

| Secret | Wert |
|--------|------|
| `SIGNING_KEY_BASE64` | `base64 -w 0 hormal-release.jks` |
| `SIGNING_STORE_PASSWORD` | das beim Erstellen gewählte Store-Passwort |
| `SIGNING_KEY_ALIAS` | `hormal-key` (oder der gewählte Alias) |
| `SIGNING_KEY_PASSWORD` | das beim Erstellen gewählte Key-Passwort |

Ohne diese Secrets schlägt der CI-Build mit einer klaren Fehlermeldung fehl, da ohne persistenten Keystore jeder Build eine andere Signatur hätte und das Update-System nicht funktionieren würde.
