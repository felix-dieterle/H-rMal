# H-rMal
Kindgerechte Hörtest app um rechts und links ohr in verschiedenen Frequenzbereichen abzutreten. Wie wären kurze Töne in zufällige Frequenzen bei unterschiedlichen Lautstärken in leicht variierenden Abständen sodass nach und nach die Grenzen im Frequenzbereich und Lautstärke Bereich abzutesten. Ein großer Button um hören zu bestätigen.

## CI / Auto-Update

Die App prüft beim Start, ob eine neuere Version auf GitHub verfügbar ist, und bietet den Download direkt in der App an.

Der CI-Build verwendet einen festen Debug-Keystore (`app/debug.keystore`), der im Repository enthalten ist. Damit sind alle Releases automatisch mit demselben Zertifikat signiert — keine Repository-Secrets nötig. Bei jedem Push auf `main` wird ein neues Release erstellt.
