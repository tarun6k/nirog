# Seed data — READ BEFORE ADDING ROWS

These files are intentionally headers-only. **Never invent a dose, PHI, ETL or
registration row.** Every `label_claims.csv` row must be transcribed from a
CIB&RC gazette notification and carry its reference in `sourceNotificationRef`
(the parser rejects rows without one). Start with wheat and cotton only.

Format: comma-separated, no quoting (no commas inside cells), `;` separates
values inside a list cell (tradeNames), empty cell = null, dates are ISO
`yyyy-MM-dd`. `doseUnit` is one of `ML_PER_L`, `G_PER_L`, `ML_PER_HA`, `G_PER_HA`.
`pricePerUnitInr` is ₹ per ml (liquids) or per g (powders) of formulated product.
`scopeCropId` empty = banned for all crops.
