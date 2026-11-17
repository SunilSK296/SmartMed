# Demonstration data

Everything here is **generated demonstration data** and is flagged `demo: true` in MongoDB.
It is created by `DemoDataSeeder` (backend) - these JSON files are a human-readable reference of its definitions.

| File | Content |
|---|---|
| `users.json` | 3 demo accounts - **development credentials only** |
| `categories.json` | 10 medicine categories |
| `suppliers.json` | 4 suppliers (lead time is informational; it is *not* part of the priority score) |
| `medicines.json` | 24 medicines. `batches[].daysToExpiry` is relative to the day of seeding (negative = already expired) |

## Generating it

```powershell
# first start, or to regenerate everything from scratch
$env:SEED_ENABLED = "true"; $env:SEED_RESET = "true"
cd smartmed-backend
.\mvnw.cmd spring-boot:run
```

The seeder also creates 30 days of consumption history over 7 departments (deterministic random generator, so every run
produces the same numbers), runs the real inventory monitor (alerts + restock tasks with different priority scores) and
orders one approved restock request so *Stock receipt* has an order to receive against.

The medicines were chosen so that each situation occurs: well stocked (Aspirin, Amlodipine…), **low** stock (Metformin,
Pantoprazole…), **critical** (Insulin, Cefixime, Omeprazole), **out of stock** (Salbutamol), **expiring within 30 days**
(Amoxicillin batch, ORS, Vitamin C) and an **expired batch** (Cetirizine).
