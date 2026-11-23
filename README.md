# Ti Kafe

Ti Kafe is a native Kotlin Android app for ordering coffee and bakery items ahead for pickup. The app uses **Part D Option B**. Accounts, bakery stock and orders are stored in SQLite on one device, with a Rose Hill staff view for updates.

## Open in Android Studio

1. Extract the ZIP and open the **TiKafe** folder (the one containing `settings.gradle.kts`) with **File → Open**.
2. Use the bundled Gradle JDK and install **Android SDK Platform 35** if Studio prompts you. Let Gradle sync.
3. Replace `0000000` with your student ID in the project’s `gradle.properties`. This sets the installed application ID. To also match the source namespace to your ID, use Android Studio’s **Refactor → Rename** on the package segment `s0000000` and update `namespace` in `app/build.gradle.kts`.
4. Run the **app** configuration on an Android **API 34+ Google Play** emulator. Sign in using one of the accounts below.

| Account | Email | Password |
| --- | --- | --- |
| Demo customer | `demo@tikafe.mu` | `Demo2026` |
| Rose Hill staff | `staff.rh@tikafe.mu` | `Staff2026` |
| New customer | Use Create account | Choose a password with 8 or more characters, a letter and a digit |

Signing in as staff opens a different screen. Use the same emulator for both roles to share the local database.

## Demonstrate the feature flows

- **Registration.** Try a mobile number that does not start with 5 and mismatched passwords before registering. Sign out from Settings and press Back.
- **Menu and stock.** Set Rose Hill croissants to 3 and pain au chocolat to 0 as staff. Switch to the customer and open Bakery with Rose Hill selected. Add a Medium Oat Café latte, rotate while customising another, and add an identical one to see the quantities merge.
- **Loyalty and checkout.** The demo account begins with 8 stamps. Place a Rose Hill order for two drinks, advance it through Preparing, Ready and Collected as staff, then switch to the demo customer. The next drink basket offers a discount. The customer pays at the counter. Pickup times use Mauritius local time and are available up to seven calendar days ahead. Bakery inventory is reserved for the pickup date.
- **Notifications.** Enable notifications in Settings or on an active order. On the same emulator, switch to staff and move the order to Ready. Expand the notification to see the pickup action. Open the notification from staff or while signed in as the order owner to view the order. A staff button sends a local offer when a Rose Hill customer has offers enabled.
- **Branches and arrival.** Try refusing location permission first, then grant approximate location to see distances. For a geofence test, grant precise location and Allow all the time via Enable pickup arrival alerts. Place an order. Use **Extended controls → Location** to move the emulator from outside the radius to a point near the pickup branch. Google Play services determines geofence delivery timing.
- **Accessibility.** Try TalkBack and 200% font on Menu, Customise drink and Basket and bill. Verify the screen reading order and record Accessibility Scanner results from your own emulator. Use Settings to switch between English, French and Kreol Morisien.

## Show the SQLite upgrade

Use a fresh emulator or uninstall the existing app before starting this sequence. Keep the student ID the same for both builds, and preserve app data when installing the update.

```sh
./gradlew :app:installDebug -PlegacyDb=true
# Open the app and place an order. Note the order number.
./gradlew :app:installDebug
# Reopen My orders and confirm that the order number is still listed.
```

On Windows use `gradlew.bat` instead of `./gradlew`. The legacy build uses version code 1 and database version 1. The default build uses version code 2. Its migration adds the stamps column and gives the seeded demo account its starting balance. This upgrade works in place with the same application ID and signing key.

## Source and evidence

- `Account.kt` and the two layouts contain the account screens. `Catalog.kt` defines the Appendix A menu, branches, pricing and opening-hour logic.
- `ShopDb.kt` defines the SQLite schema. A user owns orders, an order owns order lines, and a line owns option rows. Products are referenced by order lines and daily branch stock. The v2 migration changes the users table.
- `ShopActivity.kt` and `Shopping.kt` hold the retained screen state and Compose customer and staff screens. `BranchMap.kt` integrates osmdroid and the Android Geocoder. `Notices.kt` handles notifications and Google Play services geofencing.
- Menu images were generated with OpenAI ImageGen for this project. Map data and tiles are credited to OpenStreetMap contributors. App UI uses AndroidX, Jetpack Compose and Material 3.
- OpenAI Codex helped implement and verify this project. Record that assistance and the image generation in your report. The assignment also asks for a signed, personal report, authentic accessibility audit screenshots, and a 4–6 minute demo in your own voice.
