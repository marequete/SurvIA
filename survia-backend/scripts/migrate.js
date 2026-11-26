/**
 * Migration PostgreSQL — Table `licenses`
 * Exécuter une seule fois : npm run db:migrate
 */
require('dotenv').config();
const { Pool } = require('pg');

const pool = new Pool({ connectionString: process.env.DATABASE_URL, ssl: { rejectUnauthorized: false } });

async function migrate() {
  await pool.query(`
    CREATE TABLE IF NOT EXISTS licenses (
      id               SERIAL PRIMARY KEY,
      token            VARCHAR(80) UNIQUE NOT NULL,
      stripe_session_id VARCHAR(120) UNIQUE NOT NULL,
      status           VARCHAR(30) NOT NULL DEFAULT 'PENDING_ACTIVATION',
      bound_hardware_id TEXT,
      issued_at        TIMESTAMP WITH TIME ZONE,
      activated_at     TIMESTAMP WITH TIME ZONE
    );
  `);
  console.log('✅ Table licenses created (or already exists).');
  await pool.end();
}

migrate().catch(err => { console.error(err); process.exit(1); });
