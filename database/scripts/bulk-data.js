// mongosh script: generates 10,000 medicines and 10,000+ batches (NFR-13 data volume).
// Records are flagged demo: true and have the code prefix BULK- so they can be removed again with drop-bulk-data.js.
//   mongosh smartmed --file database/scripts/bulk-data.js
const N = 10000;
const DAY = 86400000;
const crits = ["LOW", "MEDIUM", "HIGH", "CRITICAL"];
const now = new Date();
const startOfDay = new Date(now.getFullYear(), now.getMonth(), now.getDate());
const supplier = db.suppliers.findOne({});
const meds = [];
for (let i = 0; i < N; i++) {
  const reorder = 50 + (i % 20) * 25;
  meds.push({
    code: "BULK-" + String(i).padStart(5, "0"), name: "Bulk Medicine " + i, genericName: "Generic " + (i % 500),
    form: "Tablet", strength: ((i % 10) + 1) * 50 + "mg", unit: "tablets", reorderLevel: reorder, criticalLevel: Math.floor(reorder / 3),
    criticality: crits[i % 4], unitCost: 1 + (i % 50) / 10, totalQuantity: 0, avgDailyConsumption: 0, active: true, demo: true,
    appliedEventIds: [], createdAt: now, updatedAt: now, createdBy: "bulk", version: NumberLong(0), _class: "com.smartmed.medicine.Medicine",
  });
}
const r = db.medicines.insertMany(meds, { ordered: false });
const ids = Object.values(r.insertedIds);
const batches = [];
const totals = {};
ids.forEach((id, i) => {
  const nb = 1 + (i % 2);                                    // 1 or 2 batches per medicine -> 15,000 batches
  for (let b = 0; b < nb; b++) {
    const qty = 20 + ((i * 7 + b * 13) % 600);
    totals[id] = (totals[id] || 0) + qty;
    batches.push({
      medicineId: id.toHexString(), batchNumber: "BULK-" + i + "-" + b, supplierId: supplier ? supplier._id.toHexString() : null,
      quantity: NumberLong(qty), initialQuantity: NumberLong(qty), writtenOffQuantity: NumberLong(0),
      expiryDate: new Date(startOfDay.getTime() + (20 + ((i + b * 90) % 400)) * DAY), receivedDate: startOfDay, demo: true,
      appliedEventIds: [], createdAt: now, updatedAt: now, version: NumberLong(0), _class: "com.smartmed.batch.StockBatch",
    });
  }
});
db.stock_batches.insertMany(batches, { ordered: false });
const ops = ids.map((id) => ({ updateOne: { filter: { _id: id }, update: { $set: { totalQuantity: NumberLong(totals[id]) } } } }));
db.medicines.bulkWrite(ops, { ordered: false });
print("inserted " + ids.length + " medicines and " + batches.length + " batches");
