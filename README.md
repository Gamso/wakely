# Wakely

[![CI](https://github.com/Gamso/wakely/actions/workflows/ci.yml/badge.svg)](https://github.com/Gamso/wakely/actions/workflows/ci.yml)

Le réveil Android qui s'adapte à votre agenda : il ne sonne pas (ou sonne plus tard) les jours fériés
et les jours de congé, et peut vous réveiller un certain temps avant vos rendez-vous.

## Fonctionnalités

- **Réveils classiques** : heure, jours de répétition, libellé, sonnerie, snooze réglable ou désactivé.
- **Jours non travaillés** : pour chaque réveil, « ne pas sonner », « sonner à une autre heure » ou « ignorer l'agenda ».
- **Réveils avant un événement** : « X minutes avant » le premier événement de la journée, les événements
  correspondant à des mots-clés, ou un événement précis.
- **Agendas par catégorie** (jours fériés, congés et vacances, événements) : tous les agendas synchronisés
  sur le téléphone (Google, Outlook, Samsung…), avec un filtre par mots-clés.
- **Notification de la veille** : un résumé, chaque soir, des réveils du lendemain.
- **30 sonneries Android (AOSP)**, les sonneries du téléphone ou vos propres fichiers audio.
- Hors ligne : aucune permission Internet, l'agenda est relu au moment de sonner.

## Architecture

Kotlin, Jetpack Compose, MVVM et Clean Architecture, injection de dépendances manuelle (`di/AppContainer`).

```
app/src/main/java/com/gamso/wakely/
├── domain/        Kotlin pur : modèles, interfaces de dépôts, cas d'usage (testés unitairement)
├── data/          Agendas Android (CalendarContract), stockage, AlarmManager, sonneries
├── framework/     Composants Android : réception des alarmes, service de sonnerie, notifications, jobs
├── presentation/  ViewModels (StateFlow) et écrans Compose
└── di/            Graphe de dépendances
```

## Développement

Prérequis : JDK 17+ et le SDK Android (plateforme 37).

```bash
./gradlew assembleDebug          # APK de debug
./gradlew :app:testDebugUnitTest # tests unitaires
./gradlew ktlintCheck detekt     # style et analyse statique (ktlintFormat corrige le style)
./gradlew :app:lintDebug         # Android Lint
```

## Publier une version

Le workflow [Release](.github/workflows/release.yml) construit l'APK et l'AAB signés et crée une release GitHub
pour chaque tag `v*` :

```bash
git tag v1.0.0
git push origin v1.0.0
```

Le numéro de version vient du tag (`versionCode = majeur * 10000 + mineur * 100 + correctif`).

La signature utilise quatre secrets du dépôt (Settings → Secrets and variables → Actions) :

| Secret | Contenu |
|---|---|
| `SIGNING_KEYSTORE_BASE64` | Le fichier keystore (`.jks`) encodé en base64 |
| `SIGNING_KEYSTORE_PASSWORD` | Mot de passe du keystore |
| `SIGNING_KEY_ALIAS` | Alias de la clé |
| `SIGNING_KEY_PASSWORD` | Mot de passe de la clé |

Sans ces secrets, la release est produite non signée (`wakely-<version>-unsigned.apk`).

## Licence

[MIT](LICENSE) © 2026 gamso. Les composants tiers (sonneries AOSP, icônes Material, AndroidX, Kotlin) sont sous
licence Apache 2.0 : voir [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md).
