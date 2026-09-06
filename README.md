# CoreMC Panel

The CoreMC game server control panel. This is a branded, deployable build of the
[Pterodactyl](https://pterodactyl.io) panel — the free, open-source game server
management platform built with PHP (Laravel), React, and Go.

CoreMC runs game servers in isolated Docker containers while exposing a clean,
intuitive web UI to admins and players.

## What's in this repository

| Path | Contents |
| --- | --- |
| `/` (panel root) | The Pterodactyl panel source, rebranded as **CoreMC** |
| `config/app.php` | Default app name set to `CoreMC` |
| `tailwind.config.js` | CoreMC emerald brand palette (`blue` / `primary`) |
| `public/assets/svgs/coremc.svg` | CoreMC logo / wordmark |
| `.env.example` | Environment template (`APP_NAME` / mail defaults set to CoreMC) |

> Upstream Pterodactyl panel source. See `LICENSE.md` (MIT) and the upstream
> repository for full attribution.

## CoreMC branding changes

- App name: `CoreMC` (from `config/app.php` and `.env` `APP_NAME`)
- Brand color: emerald green (`tailwind.config.js` `brand` palette mapped to
  `blue` / `primary`)
- Login logo: `public/assets/svgs/coremc.svg`
- Login + page footer: links to `https://coremc.xyz`

## Running the panel

See the official [Pterodactyl panel install docs](https://pterodactyl.io/panel/1.0/getting_started.html)
for full instructions. In short, on a host with PHP 8.2+, Composer, MySQL/MariaDB,
Redis, and a web server:

```bash
cp .env.example .env
composer install --no-dev --optimize-autoloader
php artisan key:generate --force
php artisan migrate --seed --force
php artisan view:cache
# then point your web server (nginx) at /public
```

There is also a `docker-compose.example.yml` for local/containerised development.

The React client assets are built with webpack (see `package.json` scripts). The
panel's Wings daemon (Go) is a separate component not bundled here.

## Security

Do **not** commit real secrets. Keep `.env` (with real `APP_KEY`, database
credentials, and panel passwords) out of version control; only `.env.example`
(with placeholders) is tracked.

## License

The panel is licensed under the MIT license — see `LICENSE.md`.
