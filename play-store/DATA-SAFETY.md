# Data Safety and declarations — publisher worksheet

This worksheet describes the shipped code. Complete the current Play Console form accurately after reviewing the actual provider terms and the final build. It is not an automatically submitted declaration.

## Local personal data

Positions, quantities, costs, nicknames, notes, FIFO lots, dividend records, IPO applications, thresholds and imported disclosures stay in Android app-private storage. No developer-operated server, advertising SDK, analytics SDK, crash reporter, account or broker integration. Local storage alone is not off-device collection. No advertising identifier or persistent device identifier is read.

## Off-device requests

The app sends selected stock symbols/public issue identifiers/date ranges and disclosure URLs over HTTPS to the selected data providers. The network naturally exposes IP address and user agent to those providers. The app does not append quantities, costs, account nicknames, PAN or notes. A symbol request can reveal an investment interest; do not blindly mark the final Data Safety form “no data collected” without classifying these requests and the providers' handling under the current definitions. Provider-side logging and retention are outside this app's control.

Review the “App activity”/search or interaction categories and any applicable financial-information treatment for these requests. Evaluate whether each destination qualifies as a service provider or a third party under Play's definitions. No advertising, profiling or cross-app tracking purpose is implemented. Requests support app functionality and optional alerts. Third-party ephemeral handling should only be claimed if verified; no such assumption is built into this worksheet.

Imports read only a selected document. Exports are explicitly initiated and saved to a selected destination, which can be a user's cloud document provider. Browser links and email composition are separate user actions. No email is sent automatically.

## Permissions in the final merged manifest

Expected: INTERNET, ACCESS_NETWORK_STATE, POST_NOTIFICATIONS, RECEIVE_BOOT_COMPLETED, WAKE_LOCK and AndroidX's signature-level receiver permission. No accessibility/VPN, contacts, location, microphone, camera, SMS, broad storage, QUERY_ALL_PACKAGES or AD_ID permission. No foreground service is used. Verify the merged manifest on every release.

Notifications are optional. Background requests are disabled initially, clearly described in Settings and require the user to enable them. Android may defer work. Backups/device-data transfer are disabled through manifest and extraction rules. Data export is unencrypted and explicitly labelled.

## Financial features

Complete Play's Financial features declaration. “Stock trading and portfolio management” includes the portfolio-management functionality even though no trades are executed. Describe it as informational research and manual portfolio records. Do not certify that no financial features exist. No lending, banking, payments, cryptocurrency or personalized-advice service is implemented.

## Account and deletion

No accounts can be created. The account-deletion workflow for apps that create accounts does not apply to this build. Users can delete individual records, clear Studio data in Settings, clear Android storage or uninstall. These actions do not delete independently exported files or third-party server logs.

## Sources

- https://support.google.com/googleplay/android-developer/answer/10787469
- https://support.google.com/googleplay/android-developer/answer/10144311
- https://support.google.com/googleplay/android-developer/answer/13849271
