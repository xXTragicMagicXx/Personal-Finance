# Personal Finance

A simple app for tracking what you have, month by month. Add headings (bank accounts, 401k, crypto, loans…), type in each balance once a month, and see how you're doing compared with last month, 3 months, 6 months, a year ago and all time, in dollars and percent. Gains show green and losses red.

- **Overview**: net worth, change over each period, a net-worth chart, a chart of each month's change, and a breakdown by heading.
- **Update**: one field per account. Fields accept sums (`1200 + 340.50`). Shows last month's amount next to each field, and a running total.
- **History**: a comparison table for every heading, and each month with its change. Tap a month to see its breakdown or edit it.
- **Settings**: monthly reminder, theme, import from Excel, export to CSV, backup and restore.
- **Reminders**: the Android app sends a notification on the 1st of each month, plus a follow-up (on the 5th by default) only if you still haven't entered the month. Both are adjustable in Settings.
- **Crypto auto-pricing**: add coins under a heading ("+ Add coin"), enter how many you hold, and the dollar value comes from the current price (CoinGecko, no account needed). Past months keep the price they were saved with.
- **Notes**: add a note to any month (bonus, bought a car…). Months with notes are ringed on the chart and show in History.
- **Goals & milestones**: set amounts to reach for net worth or any heading, or a debt to pay down. Progress bars show an estimate of when you'll get there at your recent pace, and you get confetti when you pass one (or hit a new all-time high).
- **Home-screen widget** (Android): net worth, change since last month, your closest goal, and a nudge when the month is due. Long-press the home screen → Widgets → Finance.

Your data stays on your device (browser storage, or the app's storage on Android). It never goes into this repository. Use **Settings → Save backup** now and then.

## Setup (one time)

### 1. Turn on the website (GitHub Pages)
Repository **Settings → Pages** → Source: **Deploy from a branch**, Branch: **main**, folder **/ (root)** → Save.
The app will be at `https://xxtragicmagicxx.github.io/Personal-Finance/`.

### 2. Android app (APK)
The APK is built by `.github/workflows/android.yml` the same way as the timesheet app: it's a thin shell around the website, so changes to the website reach the installed app without a new APK.

Add two repository secrets (**Settings → Secrets and variables → Actions**):

- `ANDROID_KEYSTORE`: the keystore, base64-encoded
- `ANDROID_KEYSTORE_PASSWORD`: its password

You can reuse the same values you set in the Time-To-Sheet-My-Pants repository. The workflow uses whichever key the keystore holds. Then run **Actions → Android app → Run workflow** (it also runs on its own when `android/` changes on `main`). The APK appears under **Releases** as `Finance.apk`. Open it on your phone to install. Later versions install over it and keep your data, as long as they're signed with the same key.

On first launch the app asks for permission to send notifications. Allow it to get the reminder on the 1st.

## Importing from Excel
Settings → **Import from Excel / CSV**. Copy your table in Excel and paste it, or save it as CSV and pick the file. Months can go across the top or down the side. Rows or columns named *Total* or *Net worth* are skipped. Write `Heading: Account` to group several accounts under one heading, and add `(debt)` to anything you owe.
