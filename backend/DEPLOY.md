# Deploying the backend (Firebase)

The app ships with a **placeholder** `app/google-services.json` (`family-finance-placeholder`), so out
of the box it runs in **demo mode** — fully functional on one device, with no multi-device sync.

To turn on the server features (family sync, server-computed dashboard aggregate, budget alerts,
Chat push via FCM), point the app and backend at a real Firebase project:

## 1. Create the Firebase project
1. <https://console.firebase.google.com> → **Add project**.
2. In the project, add an **Android app** with package name `com.familyfinance.app`.
3. Also add `com.familyfinance.app.debug` if you want the debug build to use the project too.
4. Download the generated **`google-services.json`** and replace `app/google-services.json`.
5. Enable **Cloud Firestore** (production mode), **Storage**, **Authentication** (Email/Password +
   Google) and **Cloud Messaging**.

## 2. Point the CLI at the project
```bash
cd family-finance-app/backend
# Set the real project id:
firebase use --add        # choose the project, alias it "default"
# or edit .firebaserc:  { "projects": { "default": "<your-project-id>" } }
```

## 3. Deploy rules, indexes and functions
```bash
firebase login                       # once, interactively
firebase deploy --only firestore:rules,storage,firestore:indexes
firebase deploy --only functions
```

The rules and the Firestore trigger are already written:
- `firestore.rules`, `storage.rules`
- `firestore.indexes.json`
- `functions/index.js` — the aggregate job + `onChatMessage` push.

## 4. Verify
```bash
cd backend/rules-tests && npm ci && npm test   # 45 security-rule assertions
firebase functions:log                          # after posting a chat message
```

## What works in demo mode vs with a real project

| Feature | Demo (placeholder json) | Real Firebase project |
|---|---|---|
| Personal ledger, budgets, goals, bills | ✅ | ✅ |
| Export / encrypted backup / SQLCipher | ✅ | ✅ |
| Reminders, OCR, auto-categorize, insights | ✅ | ✅ |
| Split & settle | ✅ (single device) | ✅ (shared across members) |
| Family chat (text, photos, reactions) | ✅ (single device) | ✅ (realtime across members) |
| Chat push notification | — | ✅ (needs `functions` deploy) |
| Server-computed family dashboard | local computation | server aggregate |

> Without step 1–3 the app still works, but everything stays on **one device**.
