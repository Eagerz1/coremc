# CoreMC Panel — Pterodactyl / Pelican-based

STATUS: Source folder not present in production environment.
This collaborating agent needs a full panel source (app/, routes/, config/, 
database/, resources/views, package.json, composer.json, webpack, tailwind) 
to edit / deploy the CoreMC hosting layer.

What IS available (from production):
- SFTP endpoint: 185.206.149.99.prismclients.com (port 2053 SFTP / 8443 HTTPS)
- User account: bueb0aa3.4df9ecc6 (credential stored externally)
- Panel payload: staff applications embed (see config/panel-payload.json)
- Plugin jar deployed remotely: /plugins/Omnitools-2.0.0.jar

Expected folder layout when source is provided:
  panel/app/        -- controllers, middleware
  panel/config/     -- .env.example (NO live values), panel config
  panel/routes/     -- web + API routes
  panel/database/   -- migrations / seeds
  panel/resources/  -- views + lang
  panel/package.json
  panel/composer.json
  panel/webpack.config.js
  panel/tailwind.config.js
  panel/public/     -- assets

DO NOT add live credentials here (see .gitignore + .env.example rules).
